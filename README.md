# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-2.0.0-blue.svg)

**Tottodrillo** is a modern and minimal Android app to explore, search, and download ROMs from multiple sources. The app supports dynamic source installation via ZIP packages, allowing you to add new ROM sources without updating the app.

**Sources Repository**: [Tottodrillo-Source](https://github.com/mccoy88f/Tottodrillo-Source) - Contains all source definitions and development guides.

## 🌍 Other Languages / Altri Linguaggi

This README is also available in other languages:

- [🇮🇹 Italiano](README.it.md)
- [🇪🇸 Español](README.es.md)
- [🇩🇪 Deutsch](README.de.md)
- [🇯🇵 日本語](README.ja.md)
- [🇫🇷 Français](README.fr.md)
- [🇨🇳 简体中文](README.zh-CN.md)
- [🇵🇹 Português](README.pt.md)

---

## ✨ Key Features

### 🔌 Multi-Source System (NEW in v2.0)
- **Dynamic Source Installation**: Install new ROM sources via ZIP packages without updating the app
- **Multiple Source Types**: Support for API, Java/Kotlin, and Python sources
- **Source Management**: Enable/disable, install, uninstall, and update sources from settings
- **Default Sources**: Automatic installation of CrocDB and Vimm's Lair on first launch
- **Source Filtering**: Filter search results by selected sources
- **Automatic Refresh**: UI updates automatically when sources are enabled/disabled
- **Platform Mapping**: Each source includes its own platform mapping for seamless integration

### 🔍 Exploration & Search
- **Home Screen** with featured ROMs, popular platforms, favorites, and recent ROMs
- **Platform Exploration** organized by brand (Nintendo, PlayStation, Sega, Xbox, etc.) with collapsible/expandable sections
- **Advanced Search** with automatic debounce (500ms) to optimize queries
- **Multiple Filters** for platforms, regions, and sources with interactive chips
- **Infinite Pagination** with automatic lazy loading
- **ROM Display** with centered and proportioned cover art
- **Image Carousel**: Multiple images per ROM (box art, screenshots) with swipeable carousel
- **Lazy Image Loading**: Images load only when visible on screen for better performance

### 📥 Download & Installation
- **Background Downloads** with WorkManager for reliability
- **Real-time Progress Tracking** with percentage, bytes downloaded, and speed
- **Interactive Notifications** with "Cancel download" and "Cancel installation" actions
- **Custom Path** to save files in any folder (including external SD card)
- **Automatic/Manual Installation**:
  - Support for ZIP archives (extraction)
  - Support for non-archive files (copy/move)
  - Folder picker for custom destination
- **ES-DE Compatibility**:
  - Automatic installation in ES-DE folder structure
  - ES-DE ROMs folder selection
  - Automatic organization by `mother_code` (e.g., `fds/`, `nes/`, etc.)
- **File Management**:
  - Overwrite existing files (does not delete other files in the folder)
  - Optional deletion of original file after installation
  - Download and extraction history management
- **Advanced Options**:
  - WiFi-only downloads to save mobile data
  - Available space verification before download
  - Configurable notifications
- **Session Management**: Automatic cookie handling for sources that require it (e.g., Vimm's Lair)

### 💾 ROM Management
- **Favorites** with file-based persistence
- **Recent ROMs** (last 25 opened) with file-based persistence
- **Download/Installation Status** for each link with automatic updates
- **Multiple Download Links**: Support for multiple versions and formats per ROM
- **Source Identification**: Each download link shows its source
- **Status Icons**:
  - Download in progress with progress indicator
  - Installation in progress with percentage
  - Installation completed (green icon)
  - Installation failed (red icon, clickable to retry)
- **Open Installation Folders** directly from the app

### 🎨 Design & UI
- **Material Design 3** with automatic dark/light theme
- **Minimal and Modern** interface
- **Smooth Animations** with Jetpack Compose
- **Cover Art** with lazy loading (Coil) and automatic centering
- **Platform Logos** SVG loaded from assets with fallback
- **Region Badges** with emoji flags (automatically mapped from source region names)
- **ROM Cards** with uniform maximum width (180dp)
- **Image Carousel**: Swipeable carousel for multiple ROM images

### ⚙️ Settings
- **Source Management**:
  - View all installed sources
  - Enable/disable sources
  - Install new sources from ZIP files
  - Update existing sources
  - Uninstall sources
  - Install default sources (CrocDB and Vimm's Lair)
- **Download Configuration**:
  - Custom download folder selection
  - Available space display
  - WiFi-only downloads
  - Notifications on/off
- **Installation Configuration**:
  - Delete original file after installation
  - ES-DE compatibility with folder selection
- **History Management**:
  - Clear download and extraction history (with confirmation)
- **App Information**:
  - App version
  - GitHub link
  - Support section

## 📱 Screenshots

![Tottodrillo Home Screen](screen.jpg)

## 🏗️ Architecture

The app follows **Clean Architecture** with layer separation:

```
app/
├── data/
│   ├── mapper/              # API → Domain conversion
│   ├── model/               # Data models (API, Platform)
│   ├── remote/               # Retrofit, API service, Source executors
│   ├── repository/           # Repository implementations
│   ├── receiver/             # BroadcastReceiver for notifications
│   └── worker/               # WorkManager workers (Download, Extraction)
├── domain/
│   ├── manager/              # Business logic managers (Download, Platform, Source)
│   ├── model/                # Domain models (UI)
│   └── repository/           # Repository interfaces
└── presentation/
    ├── components/            # Reusable UI components
    ├── common/                # UI State classes
    ├── detail/                # ROM detail screen
    ├── downloads/             # Downloads screen
    ├── explore/               # Platform exploration screen
    ├── home/                  # Home screen
    ├── navigation/            # Navigation graph
    ├── platform/              # ROMs by platform screen
    ├── search/                # Search screen
    ├── settings/              # Settings screen
    ├── sources/               # Source management screens
    └── theme/                 # Theme system
```

## 🛠️ Tech Stack

### Core
- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Material 3** - Design system

### Architecture
- **MVVM** - Architectural pattern
- **Hilt** - Dependency Injection
- **Coroutines & Flow** - Concurrency and reactivity
- **StateFlow** - Reactive state management

### Networking
- **Retrofit** - HTTP client
- **OkHttp** - Network layer with cookie management
- **Gson** - JSON parsing
- **Coil** - Image loading with SVG support

### Storage & Persistence
- **DataStore** - Persistent preferences
- **WorkManager** - Reliable background tasks
- **File I/O** - `.status` file management for tracking downloads/installations

### Navigation
- **Navigation Compose** - Screen routing
- **Safe Navigation** - Back stack management to avoid blank screens

### Background Tasks
- **DownloadWorker** - File download in background with foreground service
- **ExtractionWorker** - File extraction/copy in background
- **Foreground Notifications** - Interactive notifications with actions

### Source System
- **SourceExecutor Interface** - Common interface for all source types
- **SourceApiAdapter** - API source executor
- **JavaSourceExecutor** - Java/Kotlin source executor with dynamic class loading
- **PythonSourceExecutor** - Python source executor using Chaquopy
- **Chaquopy** - Python SDK for Android (Python 3.11)

## 🚀 Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or higher
- JDK 17
- Android SDK API 34
- Gradle 8.2+

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/mccoy88f/Tottodrillo.git
cd Tottodrillo
```

2. **Open in Android Studio**
   - File → Open → Select the project folder

3. **Sync Gradle**
   - Android Studio will automatically sync dependencies

4. **Build & Run**
   - Select a device/emulator
   - Run → Run 'app'

### Configuration

No API key is required. The app uses public APIs from installed sources:
- **CrocDB**: Base URL: `https://api.crocdb.net/`
- **Vimm's Lair**: Web scraping via Python (included as default source)

## 📦 Build

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

The APK will be generated in: `app/build/outputs/apk/`

## 🎯 Detailed Features

### Source System
- **Installation**: Install sources from ZIP files via file picker
- **Validation**: Automatic validation of source structure and metadata
- **Version Management**: Update sources with newer versions (preserves enabled state)
- **Enable/Disable**: Toggle sources on/off without uninstalling
- **Uninstallation**: Remove sources completely
- **Default Sources**: Automatic installation of CrocDB and Vimm's Lair on first launch
- **Cache Management**: Automatic cache invalidation when sources change
- **Platform Mapping**: Each source defines its own platform code mapping

### Download Manager
- Multiple simultaneous downloads
- Progress tracking for each download
- Cancel ongoing downloads
- Error handling with automatic retry
- Available space verification
- External SD card support
- Session cookie management for sources that require it

### Installation
- ZIP archive extraction
- Copy/move non-archive files
- Progress tracking during installation
- Error handling with red clickable icon for retry
- Automatic UI update after installation
- Open installation folder
- Multiple download links per ROM (versions, formats)

### ES-DE Compatibility
- Enable/disable compatibility
- ES-DE ROMs folder selection
- Automatic installation in correct structure
- Automatic mapping `mother_code` → folder

### History Management
- `.status` files for tracking downloads/installations
- Multi-line format to support multiple downloads of the same file
- Clear history with user confirmation

## 📚 Source Development

Tottodrillo supports three types of sources:

1. **API Sources**: HTTP REST API endpoints (like CrocDB)
2. **Java/Kotlin Sources**: Local Java/Kotlin code execution
3. **Python Sources**: Local Python script execution (using Chaquopy)

For detailed documentation on creating sources, see the [Tottodrillo-Source repository](https://github.com/mccoy88f/Tottodrillo-Source):
- [🇮🇹 Italian Guide](https://github.com/mccoy88f/Tottodrillo-Source/blob/main/SOURCE_DEVELOPMENT.md)
- [🇬🇧 English Guide](https://github.com/mccoy88f/Tottodrillo-Source/blob/main/SOURCE_DEVELOPMENT_EN.md)

### Quick Start

1. Create a ZIP package with:
   - `source.json` - Source metadata (required)
   - `platform_mapping.json` - Platform code mapping (required)
   - `api_config.json` - API configuration (for API sources)
   - Python script or JAR file (for Python/Java sources)

2. Install the source via Settings → Sources → Install Source

3. Enable the source and start using it!

See the documentation files for complete examples and API details.

## 🎯 Roadmap / To Do

Features planned for future versions:

- [ ] **ScreenScraper.fr support**
  - Integration with ScreenScraper API to enrich ROM data
  - Improve names, descriptions, and images via user's private account
  - ScreenScraper credentials configuration in settings
  - Automatic fallback if account not configured

- [ ] **Custom ROM lists and bulk download**
  - Create custom ROM lists
  - Save and manage multiple lists
  - Bulk download of all ROMs in a list
  - Priority and queue management for multiple downloads

- [ ] **Source marketplace**
  - Browse and install sources from a central repository
  - Source ratings and reviews
  - Automatic source updates

## 🌐 Localization

The app currently supports 8 languages:
- 🇮🇹 Italian (default)
- 🇬🇧 English
- 🇪🇸 Spanish
- 🇩🇪 German
- 🇯🇵 Japanese
- 🇫🇷 French
- 🇨🇳 Simplified Chinese
- 🇵🇹 Portuguese

The app automatically uses the device's language. If the language is not supported, it defaults to Italian.

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the project
2. Create a branch for your feature (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Guidelines
- Follow Kotlin conventions
- Use Jetpack Compose for UI
- Write tests when possible
- Document public APIs
- Keep code clean and readable

## 📄 License

This project is released under the MIT License. See the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

### APIs & Database
- [CrocDB](https://crocdb.net) for public APIs and ROM database
- [cavv-dev](https://github.com/cavv-dev) for the ROM database and API
- [Vimm's Lair](https://vimm.net) for ROM preservation

### Platform Logos
Platform SVG logos are provided by:
- [alekfull-nx-es-de](https://github.com/anthonycaccese/alekfull-nx-es-de) - ES-DE logo repository

### Libraries
- [Chaquopy](https://chaquo.com/chaquopy/) - Python SDK for Android

### Community
- Retro gaming community for support and feedback
- All contributors and app testers

## ⚠️ Disclaimer

**IMPORTANT**: This app is created for educational and research purposes.

- Using ROMs requires **legal ownership** of the original game
- Always respect **copyright laws** in your country
- The app does not provide ROMs, but only facilitates access to public databases
- The author assumes no responsibility for misuse of the application

## 📞 Contact

**Author**: mccoy88f

**Repository**: [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

**Issues**: If you find bugs or have suggestions, open an [Issue](https://github.com/mccoy88f/Tottodrillo/issues)

## ☕ Support Me

If you like this project and want to support me, you can buy me a coffee! 🍺

Your support helps me continue development and improve the app.

<a href="https://www.buymeacoffee.com/mccoy88f">BUY ME A COFFEE!</a>

[You can also buy me a coffee with PayPal 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Made with ❤️ for the retro gaming community**
