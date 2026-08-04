package com.example.network

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalProxyServer(
    private val getHostIp: (String) -> String?,
    private val onLog: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set
    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "ProxyServer-Listen") {
            try {
                // Bind to localhost securely
                val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = s
                port = s.localPort
                Log.d("LocalProxyServer", "Started on 127.0.0.1:$port")
                onLog("Proxy server started on port $port")
                
                while (isRunning) {
                    val clientSocket = s.accept()
                    thread(name = "Proxy-Handler") {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e("LocalProxyServer", "Server exception", e)
                    onLog("Proxy server error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        onLog("Proxy server stopped")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 30000
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            // Read the HTTP request line
            val firstLine = readLine(input) ?: return
            if (firstLine.isEmpty()) return

            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val requestUrl = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS CONNECT Tunneling
                val hostPortParts = requestUrl.split(":")
                val host = hostPortParts[0]
                val targetPort = if (hostPortParts.size > 1) hostPortParts[1].toInt() else 443

                // Resolve hostname with our custom DNS map!
                val mappedIp = getHostIp(host)
                val targetAddress = if (!mappedIp.isNullOrEmpty()) {
                    onLog("⚡ [Proxy CONNECT] $host:$targetPort ➔ Mapped to $mappedIp")
                    InetAddress.getByName(mappedIp)
                } else {
                    onLog("🌐 [Proxy CONNECT] $host:$targetPort ➔ System Default DNS")
                    InetAddress.getByName(host)
                }

                // Connect to target server
                val targetSocket = Socket(targetAddress, targetPort)
                targetSocket.soTimeout = 30000

                // Consume remaining headers of CONNECT request from client
                while (true) {
                    val line = readLine(input)
                    if (line.isNullOrEmpty()) break // empty line (\r\n) means headers done
                }

                // Send success response to client (WebView)
                val response = "HTTP/1.1 200 Connection Established\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()

                // Tunnel bytes asynchronously in both directions
                val clientToTarget = thread {
                    bridgeStream(input, targetSocket.getOutputStream())
                }
                val targetToClient = thread {
                    bridgeStream(targetSocket.getInputStream(), output)
                }

                clientToTarget.join()
                targetToClient.join()
                try { targetSocket.close() } catch (_: Exception) {}
            } else {
                // Plaintext HTTP Proxying
                var host = ""
                var targetPort = 80
                var pathAndQuery = requestUrl

                if (requestUrl.startsWith("http://", ignoreCase = true)) {
                    val uriStr = requestUrl.substring(7)
                    val slashIdx = uriStr.indexOf('/')
                    val hostPortStr = if (slashIdx != -1) uriStr.substring(0, slashIdx) else uriStr
                    pathAndQuery = if (slashIdx != -1) uriStr.substring(slashIdx) else "/"
                    
                    val hostPortParts = hostPortStr.split(":")
                    host = hostPortParts[0]
                    targetPort = if (hostPortParts.size > 1) hostPortParts[1].toInt() else 80
                }

                // Read remaining headers, extracting Host and Content-Length
                val headers = mutableListOf<String>()
                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    headers.add(line)
                    if (line.startsWith("Host:", ignoreCase = true)) {
                        val hostVal = line.substring(5).trim()
                        val hostValParts = hostVal.split(":")
                        if (host.isEmpty()) {
                            host = hostValParts[0]
                            targetPort = if (hostValParts.size > 1) hostValParts[1].toInt() else 80
                        }
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                }

                if (host.isEmpty()) return

                // Resolve hostname with our custom DNS map
                val mappedIp = getHostIp(host)
                val targetAddress = if (!mappedIp.isNullOrEmpty()) {
                    onLog("⚡ [Proxy HTTP] $method $host:$targetPort$pathAndQuery ➔ Mapped to $mappedIp")
                    InetAddress.getByName(mappedIp)
                } else {
                    onLog("🌐 [Proxy HTTP] $method $host:$targetPort$pathAndQuery ➔ System Default DNS")
                    InetAddress.getByName(host)
                }

                val targetSocket = Socket(targetAddress, targetPort)
                targetSocket.soTimeout = 30000

                val targetOutput = targetSocket.getOutputStream()
                val targetInput = targetSocket.getInputStream()

                // Forward request headers & any body
                val newFirstLine = "$method $pathAndQuery HTTP/1.1\r\n"
                targetOutput.write(newFirstLine.toByteArray(Charsets.UTF_8))
                for (header in headers) {
                    targetOutput.write("$header\r\n".toByteArray(Charsets.UTF_8))
                }
                targetOutput.write("\r\n".toByteArray(Charsets.UTF_8))
                targetOutput.flush()

                if (contentLength > 0) {
                    val postBuffer = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = input.read(postBuffer, readTotal, contentLength - readTotal)
                        if (r == -1) break
                        readTotal += r
                    }
                    targetOutput.write(postBuffer, 0, readTotal)
                    targetOutput.flush()
                }

                // Tunnel downstream
                val clientToTarget = thread { bridgeStream(input, targetOutput) }
                val targetToClient = thread { bridgeStream(targetInput, output) }
                clientToTarget.join()
                targetToClient.join()
                try { targetSocket.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            // handle error gracefully
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val bos = java.io.ByteArrayOutputStream()
        var lastByte = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (b == '\n'.code) {
                if (lastByte == '\r'.code) {
                    val bytes = bos.toByteArray()
                    return String(bytes, 0, bytes.size - 1, Charsets.UTF_8)
                }
                return String(bos.toByteArray(), Charsets.UTF_8)
            }
            bos.write(b)
            lastByte = b
        }
        return if (bos.size() > 0) String(bos.toByteArray(), Charsets.UTF_8) else null
    }

    private fun bridgeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (_: Exception) {}
    }
}
