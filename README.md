# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Tottodrillo** è un'app Android moderna e minimale per esplorare, cercare e scaricare ROM da [CrocDB](https://crocdb.net), il database pubblico di giochi retro.

## ✨ Features

### 🔍 Esplorazione e Ricerca
- **Home** con ROM in evidenza e piattaforme popolari
- **Esplorazione** per categorie (Nintendo, PlayStation, Sega, Xbox)
- **Ricerca avanzata** con debounce automatico (500ms)
- **Filtri** per piattaforme e regioni
- **Paginazione infinita** con lazy loading

### 📥 Download Manager
- **Download in background** con WorkManager
- **Progress tracking** real-time (bytes, percentuale)
- **Notifiche** durante e dopo il download
- **Path personalizzato** per salvare i file
- **Estrazione manuale** di archivi ZIP/RAR/7z con picker cartelle
- **Eliminazione automatica** archivi dopo estrazione (opzionale)
- **Solo WiFi** mode per risparmiare dati mobili
- **Gestione spazio** con verifica disponibilità

### 🎨 Design
- **Material Design 3** con tema dark/light
- **Interfaccia minimal** e moderna
- **Animazioni fluide** con Jetpack Compose
- **Cover art** con lazy loading (Coil)
- **Badge regioni** con emoji flags

## 📱 Screenshots

*Coming soon*

## 🏗️ Architettura

L'app segue **Clean Architecture** con separazione in layer:

```
app/
├── data/
│   ├── mapper/          # Conversione API → Domain
│   ├── model/           # Data models (API)
│   ├── remote/          # Retrofit, API service
│   ├── repository/      # Repository implementations
│   └── worker/          # WorkManager workers
├── domain/
│   ├── manager/         # Business logic managers
│   ├── model/           # Domain models (UI)
│   └── repository/      # Repository interfaces
└── presentation/
    ├── components/      # Componenti UI riutilizzabili
    ├── downloads/       # Schermata downloads
    ├── explore/         # Schermata esplorazione
    ├── home/            # Schermata home
    ├── navigation/      # Navigation graph
    ├── search/          # Schermata ricerca
    ├── settings/        # Schermata impostazioni
    └── theme/           # Theme system
```

## 🛠️ Stack Tecnologico

### Core
- **Kotlin** - Linguaggio principale
- **Jetpack Compose** - UI toolkit moderno
- **Material 3** - Design system

### Architettura
- **MVVM** - Pattern architetturale
- **Hilt** - Dependency Injection
- **Coroutines & Flow** - Concorrenza e reattività

### Networking
- **Retrofit** - HTTP client
- **OkHttp** - Network layer
- **Gson** - JSON parsing
- **Coil** - Image loading

### Storage & Persistence
- **DataStore** - Preferences persistenti
- **WorkManager** - Background tasks
- **Room** *(planned)* - Database locale

### Navigation
- **Navigation Compose** - Routing tra schermate

## 🚀 Setup

### Prerequisiti
- Android Studio Hedgehog (2023.1.1) o superiore
- JDK 17
- Android SDK API 34
- Gradle 8.2+

### Installazione

1. **Clone del repository**
```bash
git clone https://github.com/mccoy88f/Tottodrillo.git
cd Tottodrillo
```

2. **Apri in Android Studio**
   - File → Open → Seleziona la cartella del progetto

3. **Sync Gradle**
   - Android Studio sincronizzerà automaticamente le dipendenze

4. **Build & Run**
   - Seleziona un dispositivo/emulatore
   - Run → Run 'app'

### Configurazione

Non è necessaria alcuna API key. L'app utilizza le API pubbliche di CrocDB:
- Base URL: `https://api.crocdb.net/`
- Documentazione: [CrocDB API Docs](https://github.com/cavv-dev/crocdb-api)

## 📦 Build

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

L'APK verrà generato in: `app/build/outputs/apk/`

## 🎯 Roadmap

- [ ] Database Room per cache locale
- [ ] Supporto preferiti offline
- [ ] Download queue con priorità
- [ ] Supporto RAR/7z extraction
- [ ] Filtri avanzati (anno, genere)
- [ ] Modalità grid/list per risultati
- [ ] Statistiche download
- [ ] Backup/restore settings
- [ ] Widget home screen
- [ ] Dark/Light theme selector in-app

## 🤝 Contribuire

Contribuzioni sono benvenute! Per favore:

1. Fork il progetto
2. Crea un branch per la tua feature (`git checkout -b feature/AmazingFeature`)
3. Commit le modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Push al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

### Linee guida
- Segui le convenzioni Kotlin
- Usa Jetpack Compose per la UI
- Scrivi test quando possibile
- Documenta le API pubbliche

## 📄 Licenza

Questo progetto è rilasciato sotto licenza MIT. Vedi il file [LICENSE](LICENSE) per dettagli.

## 🙏 Ringraziamenti

- [CrocDB](https://crocdb.net) per le API pubbliche
- [cavv-dev](https://github.com/cavv-dev) per il database ROM
- Community retro gaming

## 📞 Contatti

**Autore**: mccoy88f

**Project Link**: [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

## ☕ Supportami

Se ti piace questo progetto e vuoi supportarmi, puoi offrirmi una birra! 🍺

<a href="https://www.buymeacoffee.com/mccoy88f"><img src="https://img.buymeacoffee.com/button-api/?text=Offrimi una birra&emoji=🍺&slug=mccoy88f&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" /></a>

[Puoi anche offrirmi una birra con PayPal 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Note**: Questa app è creata per scopi educativi. L'utilizzo di ROM richiede il possesso legale del gioco originale. Rispetta sempre le leggi sul copyright.
