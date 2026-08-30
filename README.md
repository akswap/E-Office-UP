# E-Office-UP Secure Browser

A focused Android browser for **UP e-Office / district e-Office / Parichay-style government portal workflows**, with VPN-awareness, integrated downloads, browser diagnostics, and isolated multi-account sessions.

> **Status:** Current UI testing confirms successful UP e-Office portal loading over a supported VPN, integrated PDF download management, offline access to downloaded documents, and multi-profile browser sessions.

> **VPN requirement:** In the current tested setup, e-Office access through this app is intended to work with **AnyConnect VPN** or **MotionPro Global VPN**. Other VPN clients are not part of the supported/tested configuration for this project.

---

## ✅ Current Tested Proof

Recent on-device tests show the following working inside the app:

| Feature | Current proof |
|---|---|
| UP district e-Office portal | ✅ Successfully loaded inside the app |
| Supported VPN path | ✅ AnyConnect VPN / MotionPro Global VPN |
| VPN awareness | ✅ `VPN ACTIVE` shown in the browser UI |
| eFile / KMS / Mail / Tasks portal UI | ✅ Portal dashboard rendered |
| PDF downloads | ✅ Completed PDF shown in built-in Downloads panel |
| Offline document access | ✅ Downloaded files tracked and openable from the app |
| Browser tools | ✅ Settings / Downloads / HOST / Logs / About |
| Multi-account sessions | ✅ Multiple isolated browser profiles supported; not limited to two accounts |
| Current About screen | ✅ Displays `Version: 1.0 (e-Office UP)` |

### Visual proof

A privacy-redacted screenshot collage has been prepared from the current test captures. Personal names, designations, team-member names, document identifiers and other sensitive portal data should remain blurred before any screenshot is published publicly.

---

## 🌟 Main Features

### 🔐 VPN-Aware e-Office Access

The browser exposes live connectivity state in the UI so the user can see when the secure VPN path is active before continuing with the e-Office workflow.

Current tested UI shows:

```text
WIFI | VPN ACTIVE
```

The UP district e-Office dashboard successfully loads in the same browser session.

### ✅ Supported VPN Clients

The current tested deployment is designed around these VPN clients:

```text
Cisco AnyConnect VPN
MotionPro Global VPN
```

For this project, these are the supported/tested VPN paths for UP e-Office access. A generic or unrelated VPN connection should not be assumed to work simply because Android reports that a VPN is active.

The browser's `VPN ACTIVE` indicator confirms that Android has an active VPN path; successful e-Office access still depends on using the required departmental/authorized VPN service, valid credentials and normal server-side access controls.

### 👥 Multi-Account / Multi-Profile Session Isolation

This is **not limited to two accounts**.

The browser can create many isolated browser profiles so separate e-Office accounts can remain signed in without intentionally sharing their normal cookie/session state.

```text
Profile 1 → Account/session A
Profile 2 → Account/session B
Profile 3 → Account/session C
...
```

The practical number of simultaneous profiles depends on Android/WebView resources, storage and available RAM rather than an artificial two-account limit.

### 📁 Integrated Download Manager

The built-in **Browser Settings & Tools → Downloads** panel tracks documents downloaded from the e-Office/intranet workflow.

Current testing confirms a PDF reaching:

```text
Status : COMPLETED
Size   : 310 KB (captured test file)
```

The interface provides controls to open and remove downloaded items, and downloaded PDF/DOC/Word files can be tracked for later/offline access.

### 🧰 Browser Settings & Tools

The current interface exposes an integrated tool panel with:

```text
Settings
Downloads
HOST
Logs
About
```

This keeps common troubleshooting and document-management tools inside the browser instead of requiring a separate utility.

### 🔑 Government SSO / Portal Compatibility

The application is designed around UP e-Office and related government SSO workflows, including popup/multi-window flows, DOM/Web storage, cookies and portal redirects required by normal authenticated browser sessions.

### ⚡ Connection Pre-Warming

The project includes connection/DNS pre-warming logic for commonly used government portal hosts to reduce avoidable first-navigation delay on supported VPN-connected networks.

---

## 🖥️ Current UI Evidence Summary

### 1. e-Office portal loaded with VPN active

The current test capture shows:

- `VPN ACTIVE` in the browser header
- the district e-Office portal loaded inside the application
- eFile, KMS, Mail, Tasks, Notes and other portal areas rendered
- operation through the project's supported VPN setup (AnyConnect VPN / MotionPro Global VPN)

### 2. Download manager proof

The current Downloads panel shows a PDF download as **COMPLETED**, with the file retained in the app's download list and an open control available.

### 3. About / application identity

The current About screen shows:

```text
Version: 1.0 (e-Office UP)
```

### 4. Multi-account capability

The browser's session model is documented as **multi-profile / multi-account**, not “dual-session only.” Each profile is intended to maintain an isolated login context so many accounts can be used on the same device, subject to device resources.

---

## 🛠️ Technical / Historical Build Notes

Earlier repository release notes referenced:

```text
Package Name : com.aistudio.hostsbrowser.xkqpwy
Version Code : 5
Version Name : 0.0.0.5
Minimum SDK  : Android 7.0 (API 24)
UI           : Jetpack Compose + Material Design 3
```

The **current tested UI** now identifies itself as **Version 1.0 (e-Office UP)**. Keep release assets/version metadata synchronized with the APK when publishing the next release.

---

## 📦 Document Support

Designed for common office-document workflows including:

- PDF
- DOC / Word documents
- government circulars and office orders
- downloaded e-Office attachments

Actual file handling can depend on Android version, installed document viewers and portal response headers.

---

## 🔒 Privacy When Publishing Screenshots

Government portal screenshots can contain personal and official information. Before adding screenshots to this public repository, blur or crop items such as:

- employee names and designations
- team/member names
- user IDs, email addresses and mobile numbers
- draft/file/document numbers
- OTPs, tokens, QR codes or session information
- sensitive correspondence/document content

The current proof collage was intentionally prepared with these areas obscured.

---

## ⚠️ Scope

This project is a browser/client utility. It does not replace the official e-Office service, **AnyConnect VPN / MotionPro Global VPN**, SSO identity provider or departmental access controls. Users still need valid authorization and normal credentials for the systems they access.

A VPN being shown as active is not, by itself, a guarantee that e-Office will be reachable. The VPN must provide the authorized network path required by the e-Office deployment.

---

## 📄 Disclaimer

This repository documents the application and its tested behavior. Portal behavior can change when government services, Android WebView, VPN configuration, SSO flows or server-side policies are updated.

Do not publish confidential departmental information, credentials, session tokens or unredacted official documents in bug reports or screenshots.
