# CHM Reader
<!-- impeccable:product-schema 1 -->
## Platform
android
## Stack
Kotlin, Android Views, Material 3, Gradle, CHMLib through JNI, WebView. Android 8/API 26 minimum; target Android 17/API 37.
## Users
The user reads varied technical documentation. They previously found CHM Sharp on iOS suitable.
## Product Purpose
Open local CHM manuals, navigate their actual nested contents, and read the original technical HTML including images and wide tables, entirely offline.
## Capabilities and Constraints
SAF file selection, large files, Cyrillic and legacy encodings, HTML and binary TOC, persistent expansion and reading position, title and full-text search, cancelable indexing, phone drawer and resizable tablet split view, light/dark app themes. Swipe through actual HTML pages in TOC order, preserving table panning, pinch and vertical scroll. No advertising, subscriptions, analytics or paid SDKs. The user authorized public GitHub hosting and manual GitHub Releases updates; INTERNET is used only for user-requested update checks/downloads, and REQUEST_INSTALL_PACKAGES delegates APK installation to Android. No automatic network checks. The WebView remains isolated from the network.
## Evidence on Hand
Local compatibility samples supplied by the user: maker.chm and CombiLdr.chm. These are private test inputs and must not be distributed.
## Product Principles
Preserve the document. Keep the real hierarchy. Make navigation predictable. Keep imported content isolated from the device and network.
