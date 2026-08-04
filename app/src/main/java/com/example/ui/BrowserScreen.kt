package com.example.ui

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Bookmark
import com.example.data.DownloadItem
import com.example.data.HostMapping
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isDesktopMode by viewModel.isDesktopMode.collectAsState()
    val bypassSsl by viewModel.bypassSsl.collectAsState()
    val isProxyActive by viewModel.isProxyActive.collectAsState()
    val isVpnConnected by viewModel.isVpnConnected.collectAsState()
    val onlyVpnForSecured by viewModel.onlyVpnForSecured.collectAsState()
    val internetType by viewModel.internetType.collectAsState()
    val networkSignalLevel by viewModel.networkSignalLevel.collectAsState()

    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    var showTabsDialog by remember { mutableStateOf(false) }

    val hostMappings by viewModel.hostMappings.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var addressText by remember { mutableStateOf(currentUrl) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Settings, 1: Downloads, 2: HOST, 3: Logs, 4: About

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            currentTime = sdf.format(java.util.Date()).uppercase()
            kotlinx.coroutines.delay(1000L)
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

    var isGoAnimating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scaleFactor by animateFloatAsState(
        targetValue = if (isGoAnimating) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "go_scale"
    )
    val rotationAngle by animateFloatAsState(
        targetValue = if (isGoAnimating) 360f else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "go_rotation"
    )

    // Sync input address when current URL changes
    LaunchedEffect(currentUrl) {
        addressText = currentUrl
    }

    // Support back-key navigation through WebView history automatically
    val canGoBackState = remember(currentUrl, isLoading) {
        webViewRef?.canGoBack() ?: false
    }
    androidx.activity.compose.BackHandler(enabled = canGoBackState) {
        webViewRef?.goBack()
    }

    val isCurrentBookmarked = remember(bookmarks, currentUrl) {
        bookmarks.any { it.url.equals(currentUrl, ignoreCase = true) }
    }

    val infiniteRefreshTransition = rememberInfiniteTransition(label = "refresh_spin")
    val refreshRotationDegrees by if (isLoading) {
        infiniteRefreshTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refresh_rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val activeTabObj = tabs.find { it.id == activeTabId }
    val isCurrentTabPrivate = activeTabObj?.isPrivate == true

    // Dynamic styling colors based on incognito vs. standard mode
    val toolbarBgColor = if (isCurrentTabPrivate) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface
    val toolbarTextColor = if (isCurrentTabPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    val toolbarSubTextColor = if (isCurrentTabPrivate) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
    val addressBarBgColor = if (isCurrentTabPrivate) Color(0xFF334155) else MaterialTheme.colorScheme.surfaceVariant
    val addressBarTextColor = if (isCurrentTabPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    val addressBarPlaceholderColor = if (isCurrentTabPrivate) Color(0xFF94A3B8).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val iconTintColor = if (isCurrentTabPrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(toolbarBgColor)
                    .statusBarsPadding()
            ) {
                // Simulated Status Bar Overlay to match Sleek Interface template style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(toolbarBgColor)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTime.ifEmpty { "12:00 PM" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = toolbarSubTextColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Internet connection type and signal indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // High fidelity cellular/wifi signal bars
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                modifier = Modifier.height(10.dp)
                            ) {
                                for (i in 1..4) {
                                    val barHeight = i * 2.5
                                    val isFilled = i <= networkSignalLevel
                                    val barColor = if (internetType == "None") {
                                        if (isCurrentTabPrivate) Color(0xFF475569) else Color(0xFFCBD5E1)
                                    } else {
                                        if (isFilled) {
                                            if (isCurrentTabPrivate) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                                        } else {
                                            if (isCurrentTabPrivate) Color(0xFF334155) else Color(0xFFE2E8F0)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(barHeight.dp)
                                            .background(barColor, shape = RoundedCornerShape(0.5.dp))
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(1.dp))

                            Text(
                                text = internetType.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (internetType == "None") {
                                    if (isCurrentTabPrivate) Color(0xFF475569) else Color(0xFF94A3B8)
                                } else {
                                    if (isCurrentTabPrivate) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                                },
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Elegant separator line between Network signal indicator and VPN connection
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(12.dp)
                                .background(if (isCurrentTabPrivate) Color(0xFF475569) else Color(0xFFE2E8F0))
                        )

                        // Live VPN status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isVpnConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                            Text(
                                text = if (isVpnConnected) "VPN ACTIVE" else "VPN DISCONNECTED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isVpnConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Divider(color = if (isCurrentTabPrivate) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                // Main Top Operations Bar
                val showGoButton = (addressText.trim() != currentUrl.trim() && addressText.isNotEmpty()) || isGoAnimating

                val showEOfficeButton = isVpnConnected && 
                    !currentUrl.lowercase().contains("districts.upeoffice.gov.in") && 
                    !currentUrl.lowercase().contains("parichay.nic.in")

                if (showEOfficeButton) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.loadUrl("https://districts.upeoffice.gov.in/")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E3A8A), // deep royal blue
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "E-Office Home Logo",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OPEN E-OFFICE UP Portal",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Settings trigger so user can still access settings when e-office button is active
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isProxyActive) Color(0xFF1D4ED8) else Color(0xFF475569)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 3-dots for Tab Management
                        IconButton(
                            onClick = { showTabsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options (Tabs)",
                                tint = Color(0xFF475569)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(toolbarBgColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    if (canGoBackState) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go Back",
                                tint = iconTintColor
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Address input field with search & actions (Sleek pill style with BasicTextField to avoid line layout clipping)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(addressBarBgColor)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (addressText.isEmpty()) {
                                Text(
                                    text = "Enter URL or address...",
                                    fontSize = 13.sp,
                                    color = addressBarPlaceholderColor
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = addressText,
                                onValueChange = { addressText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = addressBarTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (!isGoAnimating) {
                                            scope.launch {
                                                isGoAnimating = true
                                                kotlinx.coroutines.delay(500)
                                                focusManager.clearFocus()
                                                viewModel.loadUrl(addressText)
                                                isGoAnimating = false
                                            }
                                        }
                                    }
                                )
                            )
                        }
                        if (addressText.isNotEmpty()) {
                            IconButton(
                                onClick = { addressText = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(14.dp),
                                    tint = iconTintColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AnimatedContent(
                        targetState = showGoButton,
                        transitionSpec = {
                            (slideInHorizontally { width -> width / 2 } + fadeIn() togetherWith
                             slideOutHorizontally { width -> width / 2 } + fadeOut())
                                .using(SizeTransform(clip = false))
                        },
                        label = "ops_and_go"
                    ) { targetShowGo ->
                        if (targetShowGo) {
                            // Animated "Go" button
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scaleFactor
                                        scaleY = scaleFactor
                                    }
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (isCurrentTabPrivate) Color(0xFF0F172A) else Color(0xFF1D4ED8))
                                    .clickable {
                                        if (!isGoAnimating) {
                                            scope.launch {
                                                isGoAnimating = true
                                                kotlinx.coroutines.delay(500)
                                                focusManager.clearFocus()
                                                viewModel.loadUrl(addressText)
                                                isGoAnimating = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isGoAnimating) "GOING" else "GO",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.graphicsLayer {
                                            if (isGoAnimating) {
                                                rotationY = rotationAngle
                                            }
                                        }
                                    )
                                    Icon(
                                        imageVector = if (isGoAnimating) Icons.Default.Refresh else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Go Actions",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer {
                                                if (isGoAnimating) {
                                                    rotationZ = rotationAngle
                                                }
                                            }
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Refresh Button
                                IconButton(
                                    onClick = { viewModel.reload() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = iconTintColor,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = refreshRotationDegrees
                                        }
                                    )
                                }

                                // Desktop Mode toggle button which visually models standard computer screens
                                IconButton(
                                    onClick = { viewModel.toggleDesktopMode() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(13.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isDesktopMode) MaterialTheme.colorScheme.primary else iconTintColor,
                                                    shape = RoundedCornerShape(2.2.dp)
                                                )
                                                .background(
                                                    if (isDesktopMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(2.2.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDesktopMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(3.dp)
                                                .background(if (isDesktopMode) MaterialTheme.colorScheme.primary else iconTintColor)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(10.dp)
                                                .height(1.5.dp)
                                                .background(
                                                    if (isDesktopMode) MaterialTheme.colorScheme.primary else iconTintColor,
                                                    shape = RoundedCornerShape(0.5.dp)
                                                )
                                        )
                                    }
                                }

                                // Bottom settings triggering gear
                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (isProxyActive) MaterialTheme.colorScheme.primary else iconTintColor
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // 3-dots for Tab Management
                                IconButton(
                                    onClick = { showTabsDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options (Tabs)",
                                        tint = iconTintColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

                // High precision page loading progress indicator bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF1D4ED8), // Elegant brand blue
                        trackColor = Color.Transparent
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F9)) // Matching background #F3F4F9
                .padding(innerPadding)
        ) {
            // Main Central WebView Viewport
            WebViewContainer(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onWebViewCreated = { webView ->
                    webViewRef = webView
                }
            )

            // Sleek, real-time bottom loading notification pill
            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "star_effects")
                val rotationDegrees by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "star_rot"
                )
                val scalePulse by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "star_scale"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(20.dp)),
                        color = Color(0xFFEFF6FF).copy(alpha = 0.95f), // Translucent light blue
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Loading Star",
                                tint = Color(0xFFEAB308), // Golden star!
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = rotationDegrees
                                        scaleX = scalePulse
                                        scaleY = scalePulse
                                    }
                                    .size(18.dp)
                            )
                            Text(
                                text = "Please Wait..     by A.K.S.",
                                color = Color(0xFF1E40AF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }


            // 3-Dots Action / Tabs Manager Overlay Dialog
            if (showTabsDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showTabsDialog = false }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.75f)
                            .padding(12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tabs Manager",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showTabsDialog = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tabs Manager"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tab Actions: Only Private Tab
                            Button(
                                onClick = {
                                    viewModel.addNewTab(url = "about:incognito", isPrivate = true)
                                    showTabsDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Private Tab",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Private Tab+",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFF6366F1), // Royal Indigo Background
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Open Tabs (${tabs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF64748B)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Scrollable list of tabs
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tabs.size) { index ->
                                    val tab = tabs[index]
                                    val isActive = tab.id == activeTabId

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectTab(tab.id)
                                                showTabsDialog = false
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isActive) {
                                                if (tab.isPrivate) Color(0xFFF1F5F9) else Color(0xFFEFF6FF)
                                            } else {
                                                Color(0xFFF8FAFC)
                                            }
                                        ),
                                        border = if (isActive) {
                                            androidx.compose.foundation.BorderStroke(2.dp, if (tab.isPrivate) Color(0xFF0F172A) else Color(0xFF3B82F6))
                                        } else {
                                            null
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (tab.isPrivate) Icons.Default.Lock else Icons.Default.Info,
                                                contentDescription = if (tab.isPrivate) "Private Mode" else "Web Page",
                                                tint = if (tab.isPrivate) Color(0xFF475569) else Color(0xFF3B82F6),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (tab.title.length > 30) tab.title.take(30) + "..." else tab.title,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.sp,
                                                        color = if (tab.isPrivate) Color(0xFF0F172A) else Color(0xFF1E293B)
                                                    )
                                                    if (tab.isPrivate) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        androidx.compose.material3.Surface(
                                                            color = Color(0xFF334155),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Private",
                                                                fontSize = 9.sp,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = tab.url,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.closeTab(tab.id)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close tab",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Settings Sheet sliding up using customized system drawer overlay
            AnimatedVisibility(
                visible = showSettingsSheet,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showSettingsSheet = false }
                ) {
                    // Prevent dismissal clicks on the container Card itself
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.75f)
                            .clickable(enabled = false, onClick = {}),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(top = 12.dp)
                        ) {
                            // Drag pill bar
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                    .align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Browser Settings & Tools",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { showSettingsSheet = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Custom Tabs Navigation row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val tabs = listOf("Settings", "Downloads", "HOST", "Logs", "About")
                                tabs.forEachIndexed { index, title ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (activeTab == index) MaterialTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                            .clickable { activeTab = index }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeTab == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                            // Tabs Content area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                val downloads by viewModel.downloads.collectAsState()

                                when (activeTab) {
                                    0 -> SettingsTab(viewModel, isDesktopMode, bypassSsl, isProxyActive, webViewRef)
                                    1 -> DownloadsTab(viewModel, downloads)
                                    2 -> MappingsTab(viewModel, hostMappings)
                                    3 -> LogsTab(viewModel, logs)
                                    4 -> AboutTab()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 0: DNS HOST OVERRIDE MAPPINGS
// ----------------------------------------------------
@Composable
fun MappingsTab(
    viewModel: BrowserViewModel,
    mappings: List<HostMapping>
) {
    var newHost by remember { mutableStateOf("") }
    var newIp by remember { mutableStateOf("") }
    var hostError by remember { mutableStateOf(false) }
    var ipError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Pre-configured for AnyConnect eOffice logins.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
                lineHeight = 16.sp
            )
        }

        // Add Host form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Add Host Mapping Rule",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newHost,
                            onValueChange = {
                                newHost = it
                                hostError = false
                            },
                            label = { Text("Hostname API") },
                            placeholder = { Text("districts.upeoffice.gov.in") },
                            isError = hostError,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.3f)
                                .padding(end = 6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        TextField(
                            value = newIp,
                            onValueChange = {
                                newIp = it
                                ipError = false
                            },
                            label = { Text("Target IP") },
                            placeholder = { Text("192.168.x.x") },
                            isError = ipError,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Button(
                            onClick = {
                                if (newHost.isBlank()) {
                                    hostError = true
                                    return@Button
                                }
                                if (newIp.isBlank()) {
                                    ipError = true
                                    return@Button
                                }
                                viewModel.insertMapping(newHost.trim(), newIp.trim())
                                newHost = ""
                                newIp = ""
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // List of mappings
        item {
            Text(
                text = "Active Redirection Rules (${mappings.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (mappings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No HOST mappings stored.", fontSize = 13.sp, color = Color.Gray)
                }
            }
        } else {
            items(mappings, key = { it.id }) { mapping ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (mapping.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mapping.hostname,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mapping.isEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "➔ Directs to IP: ${mapping.ipAddress}",
                            fontSize = 11.sp,
                            color = if (mapping.isEnabled) MaterialTheme.colorScheme.primary
                                    else Color.Gray
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Active toggle switch
                        Switch(
                            checked = mapping.isEnabled,
                            onCheckedChange = { viewModel.toggleMappingEnabled(mapping) },
                            modifier = Modifier.scale(0.8f)
                        )

                        // Delete button
                        IconButton(
                            onClick = { viewModel.deleteMapping(mapping) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: BOOKMARKS LIST
// ----------------------------------------------------
// ----------------------------------------------------
// TAB 2: ABOUT APPLICATION
// ----------------------------------------------------
@Composable
fun AboutTab() {
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "Exit Application?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to exit and fully close the application?\n\nक्या आप वाकई ऐप बंद करना चाहते हैं?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? android.app.Activity)?.finishAffinity()
                    }
                ) {
                    Text(
                        text = "Yes, Exit (हाँ)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false }
                ) {
                    Text(
                        text = "No (नहीं)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Version: 1.0 (e-Office UP)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEFF6FF)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "*",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.align(Alignment.Top)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Created By",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "AKHILESH KUMAR SHUKLA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E3A8A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Senior Assistant",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showExitDialog = true },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit App",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exit Application (ऐप बंद करें)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UpIrrigationLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White, shape = CircleShape)
            .border(2.dp, Color(0xFFBAE6FD), CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Red Lightning waves at the top
            val lPath = Path().apply {
                moveTo(w * 0.15f, h * 0.22f)
                lineTo(w * 0.55f, h * 0.10f)
                lineTo(w * 0.45f, h * 0.18f)
                lineTo(w * 0.85f, h * 0.10f)
                lineTo(w * 0.45f, h * 0.26f)
                lineTo(w * 0.52f, h * 0.20f)
                close()
            }
            drawPath(lPath, color = Color(0xFFEF4444), style = Fill)

            // 2. Center Droplet
            val dropPath = Path().apply {
                moveTo(w * 0.5f, h * 0.22f)
                cubicTo(w * 0.45f, h * 0.32f, w * 0.30f, h * 0.42f, w * 0.30f, h * 0.52f)
                cubicTo(w * 0.30f, h * 0.65f, w * 0.70f, h * 0.65f, w * 0.70f, h * 0.52f)
                cubicTo(w * 0.70f, h * 0.42f, w * 0.55f, h * 0.32f, w * 0.5f, h * 0.22f)
                close()
            }
            drawPath(dropPath, color = Color(0xFF0284C7), style = Fill)

            // 3. Right & Left leaves (Wreath)
            val leafColor = Color(0xFF16A34A)
            
            // Draw left side leaves
            for (i in 0..4) {
                val p = i / 4f
                val angle = Math.PI - (p * Math.PI / 2.3)
                val cx = (w * 0.5 + Math.cos(angle) * (w * 0.26)).toFloat()
                val cy = (h * 0.50 + Math.sin(angle) * (h * 0.22)).toFloat()
                
                drawOval(
                    color = leafColor,
                    topLeft = Offset(cx - w * 0.04f, cy - h * 0.02f),
                    size = Size(w * 0.08f, h * 0.04f)
                )
            }
            
            // Draw right side leaves
            for (i in 0..4) {
                val p = i / 4f
                val angle = p * Math.PI / 2.3
                val cx = (w * 0.5 + Math.cos(angle) * (w * 0.26)).toFloat()
                val cy = (h * 0.50 + Math.sin(angle) * (h * 0.22)).toFloat()
                
                drawOval(
                    color = leafColor,
                    topLeft = Offset(cx - w * 0.04f, cy - h * 0.02f),
                    size = Size(w * 0.08f, h * 0.04f)
                )
            }

            // 4. White circle inside the droplet
            drawCircle(
                color = Color.White,
                radius = w * 0.12f,
                center = Offset(w * 0.5f, h * 0.51f),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Center partition inside the circle
            drawLine(
                color = Color.White,
                start = Offset(w * 0.5f, h * 0.39f),
                end = Offset(w * 0.5f, h * 0.63f),
                strokeWidth = 1.5.dp.toPx()
            )

            // 5. Three waves at the bottom representing water
            for (i in 0..2) {
                val waveY = h * 0.72f + i * (h * 0.045f)
                val wavePath = Path().apply {
                    moveTo(w * 0.15f, waveY)
                    quadraticTo(w * 0.35f, waveY - h * 0.02f, w * 0.5f, waveY)
                    quadraticTo(w * 0.65f, waveY + h * 0.02f, w * 0.85f, waveY)
                }
                drawPath(
                    path = wavePath,
                    color = Color(0xFF0284C7),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Overlay Texts: "उ प्र" & "सि वि" inside the droplet white circle
        Text(
            text = "उ प्र",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-7).dp)
        )
        Text(
            text = "सि वि",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 7.dp)
        )
    }
}

// ----------------------------------------------------
// TAB 2: LIVE PROXY CONNECTION LOGS
// ----------------------------------------------------
@Composable
fun LogsTab(
    viewModel: BrowserViewModel,
    logs: List<String>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Secure Network DNS Connection Console",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { viewModel.clearLogs() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            ) {
                Text("Clear console", fontSize = 10.sp)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp)),
            color = Color(0xFF1E293B) // Dark console slate
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Idle. Logging incoming browser requests...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (log.contains("Mapped")) Color(0xFF10B981) // Green for custom mappings
                                    else if (log.contains("Error") || log.contains("FAIL")) Color(0xFFEF4444) // Red for errors
                                    else Color(0xFFE2E8F0), // Light gray default
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: GENERAL SETTINGS CONTROLS
// ----------------------------------------------------
@Composable
fun SettingsTab(
    viewModel: BrowserViewModel,
    isDesktopMode: Boolean,
    bypassSsl: Boolean,
    isProxyActive: Boolean,
    webView: WebView?
) {
    val onlyVpnForSecured by viewModel.onlyVpnForSecured.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Configure core browser mechanics for intranet authentication.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Clear Storage Button
        item {
            Button(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Clear Cookies, Cache & History", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
            }
        }

        // Desktop mode Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Desktop Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Simulates standard computer display layouts. Helpful for eOffice logins that restrict mobile logins.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isDesktopMode,
                        onCheckedChange = { viewModel.toggleDesktopMode() }
                    )
                }
            }
        }

        // SSL Bypass Toggle Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bypass SSL Warnings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ignores untrusted security warnings. Required to connect over government private VPN servers.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = bypassSsl,
                        onCheckedChange = { viewModel.toggleBypassSsl() }
                    )
                }
            }
        }

        // Proxy Toggle Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Domain Redirection Engine",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toggles DNS hosts override system. Turn OFF to route ordinary web domains natively.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isProxyActive,
                        onCheckedChange = { viewModel.setProxyState(it) }
                    )
                }
            }
        }

        // Print Web Page / Save as PDF Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = "Print Webpage (Save as PDF)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Convert the current webpage into standard PDF layout or send to any physical printer.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.triggerPrint() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Print", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        var selectedCleanupOption by remember { mutableStateOf(0) } // 0: eOffice Only, 1: Clear All
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Storage & History Cleanup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Please select the cleanup mode to delete session cookies, cached files, and history database records:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // eOffice Only option card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCleanupOption = 0 },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCleanupOption == 0) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            }
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selectedCleanupOption == 0) 2.dp else 1.dp,
                            color = if (selectedCleanupOption == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedCleanupOption == 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "eOffice option",
                                    tint = if (selectedCleanupOption == 0) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "eOffice Only (सिर्फ eOffice)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = if (selectedCleanupOption == 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Clears cookies, caches, and storage ONLY for districts.upeoffice.gov.in and parichay.nic.in portals.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            RadioButton(
                                selected = (selectedCleanupOption == 0),
                                onClick = { selectedCleanupOption = 0 }
                            )
                        }
                    }

                    // Clear All option card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCleanupOption = 1 },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCleanupOption == 1) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            }
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selectedCleanupOption == 1) 2.dp else 1.dp,
                            color = if (selectedCleanupOption == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedCleanupOption == 1) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear all option",
                                    tint = if (selectedCleanupOption == 1) {
                                        MaterialTheme.colorScheme.onError
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear All Data (सब कुछ साफ करें)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = if (selectedCleanupOption == 1) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Clears all cookies, complete WebView web caches, storage logs, and absolute history across all sites.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            RadioButton(
                                selected = (selectedCleanupOption == 1),
                                onClick = { selectedCleanupOption = 1 },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (selectedCleanupOption == 0) {
                            viewModel.clearSelectiveEOfficeBrowsingData(webView)
                            android.widget.Toast.makeText(
                                context, 
                                "eOffice Data Cleared Successfully!\n(सिर्फ eOffice डेटा सफलतापूर्वक साफ़ किया गया)", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            try {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                webView?.clearCache(true)
                                webView?.clearHistory()
                                viewModel.clearBrowsingHistory()
                                viewModel.addLog("🧹 Cleared browser cookies, cache files, and database history successfully.")
                                android.widget.Toast.makeText(
                                    context, 
                                    "All Data Cleared Successfully!\n(सभी डेटा सफलतापूर्वक साफ़ किया गया)", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                viewModel.addLog("❌ Error cleaning storage: ${e.localizedMessage}")
                                android.widget.Toast.makeText(
                                    context, 
                                    "Error: ${e.localizedMessage}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = if (selectedCleanupOption == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        )
    }
}

// ----------------------------------------------------
// TAB 2: DOWNLOADS HISTORY & ACCESS
// ----------------------------------------------------
@Composable
fun DownloadsTab(
    viewModel: BrowserViewModel,
    downloads: List<com.example.data.DownloadItem>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Check permission helper for older Android APIs
    val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT <= 32) {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    } else {
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    var permissionGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT <= 32) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // Scoped Storage covers everything on modern Android 13+ without write storage permission
            }
        )
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (android.os.Build.VERSION.SDK_INT <= 32) {
            permissionGranted = results[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Explanatory Rationale Permission Box
        if (android.os.Build.VERSION.SDK_INT <= 32 && !permissionGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Storage Permission Needed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant storage access to store downloaded files in public folders reliably.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                    Button(
                        onClick = { launcher.launch(permissionsToRequest) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Allow", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        Text(
            text = "Downloaded PDF, DOC & Word files from intranets are tracked and openable offline below:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
            lineHeight = 16.sp
        )

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No Downloads",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files downloaded yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Your downloaded copies will render here.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(downloads) { download ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconColor = when (download.downloadStatus) {
                                "COMPLETED" -> Color(0xFF10B981)
                                "DOWNLOADING" -> Color(0xFF2563EB)
                                else -> Color(0xFFEF4444)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                when (download.downloadStatus) {
                                    "COMPLETED" -> Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    "DOWNLOADING" -> CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = iconColor
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Failed",
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = download.fileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(iconColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = download.downloadStatus,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = iconColor
                                        )
                                    }

                                    val sizeText = if (download.fileSize > 0) {
                                        val kb = download.fileSize / 1024
                                        if (kb > 1024) {
                                            String.format("%.1f MB", kb / 1024.0)
                                        } else {
                                            "$kb KB"
                                        }
                                    } else {
                                        "Unknown size"
                                    }
                                    Text(
                                        text = sizeText,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (download.downloadStatus == "COMPLETED") {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val file = java.io.File(download.filePath)
                                                if (file.exists()) {
                                                    val authority = "${context.packageName}.fileprovider"
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        authority,
                                                        file
                                                    )
                                                    
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, download.mimeType.ifEmpty { "application/octet-stream" })
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } else {
                                                    android.widget.Toast.makeText(context, "File absolute path doesn't exist anymore.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Error opening file: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Open file",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.deleteDownload(download) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete download",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

