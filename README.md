# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Tottodrillo** è un'app Android moderna e minimale per esplorare, cercare e scaricare ROM da [CrocDB](https://crocdb.net), il database pubblico di giochi retro.

## ✨ Caratteristiche Principali

### 🔍 Esplorazione e Ricerca
- **Home Screen** con ROM in evidenza, piattaforme popolari, preferiti e ROM recenti
- **Esplorazione Piattaforme** organizzate per brand (Nintendo, PlayStation, Sega, Xbox, ecc.) con sezioni collassabili/espandibili
- **Ricerca Avanzata** con debounce automatico (500ms) per ottimizzare le query
- **Filtri Multipli** per piattaforme e regioni con chip interattivi
- **Paginazione Infinita** con lazy loading automatico
- **Visualizzazione ROM** con cover art centrate e proporzionate

### 📥 Download e Installazione
- **Download in Background** con WorkManager per affidabilità
- **Progress Tracking Real-time** con percentuale, bytes scaricati e velocità
- **Notifiche Interattive** con azioni "Interrompi download" e "Interrompi installazione"
- **Path Personalizzato** per salvare i file in qualsiasi cartella (incluso SD card esterna)
- **Installazione Automatica/Manuale**:
  - Supporto per archivi ZIP (estrazione)
  - Supporto per file non-archivio (copia/spostamento)
  - Picker cartelle per destinazione personalizzata
- **Compatibilità ES-DE**:
  - Installazione automatica nella struttura cartelle di ES-DE
  - Selezione cartella ROMs ES-DE
  - Organizzazione automatica per `mother_code` (es. `fds/`, `nes/`, ecc.)
- **Gestione File**:
  - Sovrascrittura file esistenti (non elimina altri file nella cartella)
  - Eliminazione opzionale del file originale dopo installazione
  - Gestione storico download ed estrazioni
- **Opzioni Avanzate**:
  - Download solo WiFi per risparmiare dati mobili
  - Verifica spazio disponibile prima del download
  - Notifiche configurabili

### 💾 Gestione ROM
- **Preferiti** con persistenza su file
- **ROM Recenti** (ultime 25 aperte) con persistenza su file
- **Stato Download/Installazione** per ogni link con aggiornamento automatico
- **Icone di Stato**:
  - Download in corso con progresso
  - Installazione in corso con percentuale
  - Installazione completata (icona verde)
  - Installazione fallita (icona rossa, cliccabile per riprovare)
- **Apertura Cartelle** di installazione direttamente dall'app

### 🎨 Design e UI
- **Material Design 3** con tema dark/light automatico
- **Interfaccia Minimal** e moderna
- **Animazioni Fluide** con Jetpack Compose
- **Cover Art** con lazy loading (Coil) e centratura automatica
- **Logo Piattaforme** SVG caricati da assets con fallback
- **Badge Regioni** con emoji flags
- **Card ROM** con larghezza massima uniforme (180dp)

### ⚙️ Impostazioni
- **Configurazione Download**:
  - Selezione cartella download personalizzata
  - Visualizzazione spazio disponibile
  - Download solo WiFi
  - Notifiche on/off
- **Configurazione Installazione**:
  - Eliminazione file originale dopo installazione
  - Compatibilità ES-DE con selezione cartella
- **Gestione Storico**:
  - Cancellazione storico download ed estrazioni (con conferma)
- **Informazioni App**:
  - Versione app
  - Link GitHub
  - Sezione supporto

## 📱 Screenshots

*Coming soon*

## 🏗️ Architettura

L'app segue **Clean Architecture** con separazione in layer:

```
app/
├── data/
│   ├── mapper/              # Conversione API → Domain
│   ├── model/               # Data models (API, Platform)
│   ├── remote/               # Retrofit, API service
│   ├── repository/           # Repository implementations
│   ├── receiver/             # BroadcastReceiver per notifiche
│   └── worker/               # WorkManager workers (Download, Extraction)
├── domain/
│   ├── manager/              # Business logic managers (Download, Platform)
│   ├── model/                # Domain models (UI)
│   └── repository/           # Repository interfaces
└── presentation/
    ├── components/            # Componenti UI riutilizzabili
    ├── common/                # UI State classes
    ├── detail/                # Schermata dettaglio ROM
    ├── downloads/             # Schermata downloads
    ├── explore/               # Schermata esplorazione piattaforme
    ├── home/                  # Schermata home
    ├── navigation/            # Navigation graph
    ├── platform/              # Schermata ROM per piattaforma
    ├── search/                # Schermata ricerca
    ├── settings/              # Schermata impostazioni
    └── theme/                 # Theme system
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
- **StateFlow** - Gestione stato reattivo

### Networking
- **Retrofit** - HTTP client
- **OkHttp** - Network layer
- **Gson** - JSON parsing
- **Coil** - Image loading con supporto SVG

### Storage & Persistence
- **DataStore** - Preferences persistenti
- **WorkManager** - Background tasks affidabili
- **File I/O** - Gestione file `.status` per tracking download/installazione

### Navigation
- **Navigation Compose** - Routing tra schermate
- **Safe Navigation** - Gestione back stack per evitare schermate vuote

### Background Tasks
- **DownloadWorker** - Download file in background con foreground service
- **ExtractionWorker** - Estrazione/copia file in background
- **Foreground Notifications** - Notifiche interattive con azioni

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

## 🎯 Funzionalità Dettagliate

### Download Manager
- Download multipli simultanei
- Tracking progresso per ogni download
- Cancellazione download in corso
- Gestione errori con retry automatico
- Verifica spazio disponibile
- Supporto SD card esterna

### Installazione
- Estrazione archivi ZIP
- Copia/spostamento file non-archivio
- Progress tracking durante installazione
- Gestione errori con icona rossa cliccabile per retry
- Aggiornamento automatico UI dopo installazione
- Apertura cartella installazione

### Compatibilità ES-DE
- Abilitazione/disabilitazione compatibilità
- Selezione cartella ROMs ES-DE
- Installazione automatica nella struttura corretta
- Mapping automatico `mother_code` → cartella

### Gestione Storico
- File `.status` per tracking download/installazione
- Formato multi-riga per supportare download multipli dello stesso file
- Cancellazione storico con conferma utente

## 🎯 Roadmap / To Do

Funzionalità pianificate per le prossime versioni:

- [ ] **Implementazione della struttura multisource**
  - Supporto per multiple sorgenti ROM oltre a CrocDB
  - Configurazione e selezione sorgenti nelle impostazioni
  - Unificazione dei risultati da diverse sorgenti

- [ ] **Aggiunta lingua inglese per l'app**
  - Localizzazione completa in inglese
  - Selezione lingua nelle impostazioni
  - Supporto per altre lingue future

- [ ] **Supporto ScreenScraper.fr**
  - Integrazione con API ScreenScraper per arricchire i dati ROM
  - Miglioramento nomi, descrizioni e immagini tramite account privato dell'utente
  - Configurazione credenziali ScreenScraper nelle impostazioni
  - Fallback automatico se account non configurato

- [ ] **Liste ROM personali e download collettivo**
  - Creazione liste personali di ROM
  - Salvataggio e gestione liste multiple
  - Download collettivo di tutte le ROM in una lista
  - Gestione priorità e queue per download multipli

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
- Mantieni il codice pulito e leggibile

## 📄 Licenza

Questo progetto è rilasciato sotto licenza MIT. Vedi il file [LICENSE](LICENSE) per dettagli.

## 🙏 Ringraziamenti

### API e Database
- [CrocDB](https://crocdb.net) per le API pubbliche e il database ROM
- [cavv-dev](https://github.com/cavv-dev) per il database ROM e l'API

### Loghi Piattaforme
I loghi SVG delle piattaforme sono forniti da:
- [alekfull-nx-es-de](https://github.com/anthonycaccese/alekfull-nx-es-de) - Repository di loghi per ES-DE

### Community
- Community retro gaming per il supporto e i feedback
- Tutti i contributori e tester dell'app

## ⚠️ Disclaimer

**IMPORTANTE**: Questa app è creata per scopi educativi e di ricerca. 

- L'utilizzo di ROM richiede il **possesso legale** del gioco originale
- Rispetta sempre le **leggi sul copyright** del tuo paese
- L'app non fornisce ROM, ma si limita a facilitare l'accesso a database pubblici
- L'autore non si assume alcuna responsabilità per l'uso improprio dell'applicazione

## 📞 Contatti

**Autore**: mccoy88f

**Repository**: [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

**Issues**: Se trovi bug o hai suggerimenti, apri una [Issue](https://github.com/mccoy88f/Tottodrillo/issues)

## ☕ Supportami

Se ti piace questo progetto e vuoi supportarmi, puoi offrirmi una birra! 🍺

Il tuo supporto mi aiuta a continuare lo sviluppo e migliorare l'app.

<a href="https://www.buymeacoffee.com/mccoy88f"><img src="https://img.buymeacoffee.com/button-api/?text=Offrimi%20una%20birra&emoji=%F0%9F%8D%BA&slug=mccoy88f&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" /></a>

[Puoi anche offrirmi una birra con PayPal 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Made with ❤️ for the retro gaming community**
