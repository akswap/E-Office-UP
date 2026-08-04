# Release v0.0.0.5 - E-Office-UP Secure Browser

🚀 **E-Office-UP Secure Browser (v0.0.0.5 / Build 5)**

This release introduces major performance optimizations, specialized fixes for government SSO portals (Parichay / JanParichay / UP e-Office), dual-session multi-profile isolation, and smart VPN connectivity pre-warming.

---

## 🌟 What's New & Key Highlights

### ⚡ 1. Parichay & UP e-Office Acceleration
* **Pre-warming Engine**: Instant DNS resolution and connection pre-warming for `districts.upeoffice.gov.in`, `parichay.nic.in`, `services.parichay.nic.in`, `janparichay.nic.in`, and `sso.nic.in`.
* **Zero Latency Navigation**: Reduces initial SSL handshake and gateway latency when accessing district portals over VPN networks.

### 🔐 2. Government Portal SSL & SSO Compatibility
* **SSL Certificate Resilience**: Handled legacy/internal government SSL certificate errors seamlessly to prevent blank screen failures.
* **Popup & Multi-Window SSO Flow**: Enabled multi-window popup support (`setSupportMultipleWindows(true)`) for Parichay single-sign-on redirects and OTP/token verification windows.
* **DOM Storage & Session Support**: Full support for local HTML5 storage, SessionStorage, IndexedDB, and third-party cookies required by Parichay authentication servers.

### 🛡️ 3. Smart VPN Status Monitor
* **Live VPN Sensor**: Real-time status indicator showing `🟢 VPN Connected` vs `🔴 Connect VPN first` before attempting log in.
* **Auto Pre-warm Trigger**: Automatically initiates connection warm-up as soon as a secure VPN tunnel is detected on the device.

### 🕵️ 4. Multi-Profile Tab Isolation (Private Mode)
* **Isolated Browser Contexts**: Normal and Private tabs run on separate cookie and WebStorage profiles, enabling officers to log into multiple e-Office user accounts simultaneously without session overlap.

### 📁 5. Document & Attachment Support
* **File Upload & Download Engine**: Native file picker and Android `DownloadManager` support for downloading government circulars, office orders, and signed PDFs effortlessly.

---

## 🛠️ Technical Details & Build Information
* **Package Name**: `com.aistudio.hostsbrowser.xkqpwy`
* **Version Code**: `5`
* **Version Name**: `0.0.0.5`
* **Target SDK**: Android 14 / 15 (API Level 34-36)
* **Minimum SDK**: Android 7.0 (API Level 24)
* **UI Framework**: Jetpack Compose + Material Design 3

---

## 📦 Download Assets
* `app-release.apk` - Official Signed APK for Manual Installation.
* `app-release.aab` - Android App Bundle for Google Play Console Submission.
