# Analisi Architettura: Separazione Sorgenti e App Principale

## Obiettivo
Rendere l'app principale **agnostica dalle sorgenti** e predisporre tutte le funzionalità avanzate (come bypass Cloudflare, gestione cookie, SSL, WebView) come **servizi** che le sorgenti possono richiedere tramite un'interfaccia standard.

---

## 📊 Stato Attuale

### 1. **Logica Hardcoded nell'App Principale**

#### **TottodrilloApp.kt**
- ✅ **CookieJar personalizzato** per immagini (righe 65-118)
  - Logica specifica per visitare pagine ROM per ottenere cookie
  - Gestione cookie di sessione per immagini
  
- ✅ **SSL Trust All** (righe 122-131)
  - Configurazione SSL per accettare certificati self-signed
  - Necessario per alcune sorgenti ma hardcoded
  
- ✅ **Referer Interceptor** (righe 133-186)
  - Aggiunge header Referer alle immagini basandosi su `imageRefererPattern` nel metadata
  - Logica generica ma comunque nell'app principale

#### **DownloadWorker.kt**
- ✅ **CookieJar personalizzato** (righe 60-70)
  - Gestione cookie per download
  
- ✅ **SSL Trust All** (righe 74-86)
  - Configurazione SSL duplicata
  
- ✅ **Gestione Cookie da WebView** (righe 224-263)
  - Logica hardcoded per estrarre e usare cookie da WebView
  - Gestione Referer headers specifica per NSWpedia

#### **WebViewBackgroundDownloader.kt**
- ✅ **Gestione Cookie Cloudflare** (righe 117-147)
  - Logica hardcoded per estrarre cookie Cloudflare
  - JavaScript hardcoded per estrarre URL (righe 50-77)
  - Logica specifica per NSWpedia (righe 60-61)

#### **WebViewDownloadDialog.kt**
- ✅ **Gestione Cookie Cloudflare** (righe 314, 432, 463)
  - Logica duplicata per estrarre cookie
  - Verifica cookie `cf_clearance` hardcoded

### 2. **Dipendenze delle Sorgenti**

#### **SourceExecutor.kt**
- ❌ Riceve `OkHttpClient` generico senza funzionalità avanzate
- ❌ Non ha accesso a:
  - Bypass Cloudflare
  - Gestione cookie personalizzata
  - WebView helper
  - Delay/challenge handling

#### **SourceApiClient.kt**
- ❌ Usa `OkHttpClient` passato senza modifiche
- ❌ Non può richiedere funzionalità avanzate

#### **JavaSourceExecutor.kt**
- ❌ Non ha accesso a funzionalità dell'app
- ❌ Le classi Java caricate dinamicamente non possono usare servizi dell'app

#### **PythonSourceExecutor.kt**
- ❌ Non ha accesso a funzionalità dell'app
- ❌ Gli script Python non possono usare servizi dell'app

### 3. **Problemi Identificati**

1. **Duplicazione di Logica**
   - CookieJar duplicato in `TottodrilloApp`, `DownloadWorker`
   - SSL Trust All duplicato in `TottodrilloApp`, `DownloadWorker`
   - Gestione cookie Cloudflare duplicata in `WebViewBackgroundDownloader`, `WebViewDownloadDialog`

2. **Logica Hardcoded per Sorgenti Specifiche**
   - NSWpedia menzionato esplicitamente in `DownloadWorker` (riga 245)
   - Pattern hardcoded per `.nsp`, `.xci` in `WebViewBackgroundDownloader` (riga 68)

3. **Mancanza di Interfaccia Standard**
   - Le sorgenti non possono richiedere funzionalità avanzate
   - L'app non espone servizi riutilizzabili

4. **Accoppiamento Forte**
   - L'app conosce dettagli specifici delle sorgenti
   - Modifiche alle sorgenti richiedono modifiche all'app

---

## 🎯 Architettura Proposta

### 1. **Creare Interfaccia di Servizi per Sorgenti**

Creare un'interfaccia `SourceServices` che espone tutte le funzionalità avanzate:

```kotlin
interface SourceServices {
    // HTTP Client con funzionalità avanzate
    fun createHttpClient(
        sourceId: String,
        config: HttpClientConfig
    ): OkHttpClient
    
    // Bypass Cloudflare
    suspend fun bypassCloudflare(
        url: String,
        sourceId: String,
        config: CloudflareBypassConfig
    ): CloudflareBypassResult
    
    // Gestione Cookie
    fun createCookieManager(sourceId: String): CookieManager
    
    // WebView Helper
    suspend fun extractUrlFromWebView(
        url: String,
        sourceId: String,
        config: WebViewConfig
    ): WebViewExtractionResult
    
    // SSL Configuration
    fun createSslContext(config: SslConfig): SSLContext
}
```

### 2. **Configurazione nelle Sorgenti**

Aggiungere al `SourceMetadata` campi per richiedere funzionalità:

```kotlin
data class SourceMetadata(
    // ... campi esistenti ...
    
    // Richieste funzionalità avanzate
    val requiresCloudflareBypass: Boolean = false,
    val cloudflareBypassConfig: CloudflareBypassConfig? = null,
    val requiresCustomCookieManager: Boolean = false,
    val requiresSslTrustAll: Boolean = false,
    val webViewConfig: WebViewConfig? = null,
    val httpClientConfig: HttpClientConfig? = null
)
```

### 3. **Implementazione Servizi nell'App**

Creare `SourceServicesImpl` che implementa tutte le funzionalità:

```kotlin
@Singleton
class SourceServicesImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager
) : SourceServices {
    
    // Implementa tutte le funzionalità
    // - Bypass Cloudflare
    // - Gestione Cookie
    // - WebView Helper
    // - SSL Configuration
}
```

### 4. **Modifiche ai SourceExecutor**

Modificare `SourceExecutor.create()` per accettare `SourceServices`:

```kotlin
fun create(
    metadata: SourceMetadata,
    sourceDir: File,
    okHttpClient: OkHttpClient? = null,
    gson: Gson? = null,
    sourceServices: SourceServices? = null  // NUOVO
): SourceExecutor
```

### 5. **Passare SourceServices alle Sorgenti**

Modificare `RomRepositoryImpl` per passare `SourceServices`:

```kotlin
val executor = SourceExecutor.create(
    metadata,
    sourceDir,
    okHttpClient,
    gson,
    sourceServices  // NUOVO
)
```

---

## 📝 Modifiche Dettagliate

### **File da Creare**

1. **`domain/service/SourceServices.kt`**
   - Interfaccia che espone tutti i servizi

2. **`data/service/SourceServicesImpl.kt`**
   - Implementazione dei servizi

3. **`domain/model/SourceServiceConfig.kt`**
   - Modelli di configurazione per i servizi

### **File da Modificare**

1. **`domain/model/SourceModels.kt`**
   - Aggiungere campi per richieste servizi in `SourceMetadata`

2. **`data/remote/SourceExecutor.kt`**
   - Aggiungere parametro `SourceServices` al metodo `create()`

3. **`data/remote/SourceApiAdapter.kt`**
   - Usare `SourceServices` per creare `OkHttpClient` personalizzato se richiesto

4. **`data/remote/JavaSourceExecutor.kt`**
   - Passare `SourceServices` alle classi Java caricate dinamicamente

5. **`data/remote/PythonSourceExecutor.kt`**
   - Passare `SourceServices` agli script Python

6. **`data/repository/RomRepositoryImpl.kt`**
   - Iniettare e passare `SourceServices` ai `SourceExecutor`

7. **`TottodrilloApp.kt`**
   - Rimuovere logica hardcoded, usare `SourceServices` invece

8. **`data/worker/DownloadWorker.kt`**
   - Usare `SourceServices` invece di logica hardcoded

9. **`presentation/components/WebViewBackgroundDownloader.kt`**
   - Usare `SourceServices` per gestione WebView

10. **`presentation/components/WebViewDownloadDialog.kt`**
    - Usare `SourceServices` per gestione WebView

11. **`di/NetworkModule.kt`**
    - Fornire `SourceServices` come singleton

---

## 🔄 Flusso Proposto

### **1. Inizializzazione Sorgente**

```
RomRepositoryImpl.searchRoms()
  ↓
SourceManager.getEnabledSources()
  ↓
SourceExecutor.create(metadata, sourceDir, okHttpClient, gson, sourceServices)
  ↓
SourceServices.createHttpClient() se richiesto da metadata
  ↓
SourceExecutor pronto con funzionalità avanzate
```

### **2. Richiesta Bypass Cloudflare**

```
SourceExecutor.getEntry()
  ↓
SourceApiClient.call() o Python/Java executor
  ↓
Se metadata.requiresCloudflareBypass == true
  ↓
SourceServices.bypassCloudflare()
  ↓
Ritorna URL e cookie
```

### **3. Download con WebView**

```
RomDetailViewModel.downloadRom()
  ↓
Se link.requiresWebView == true
  ↓
SourceServices.extractUrlFromWebView()
  ↓
WebView gestisce Cloudflare automaticamente
  ↓
Ritorna URL finale e cookie
```

---

## ✅ Vantaggi

1. **Separazione delle Responsabilità**
   - App principale: fornisce servizi generici
   - Sorgenti: richiedono servizi tramite configurazione

2. **Riusabilità**
   - Logica di bypass Cloudflare centralizzata
   - Gestione cookie centralizzata
   - WebView helper centralizzato

3. **Estensibilità**
   - Nuove funzionalità aggiunte come servizi
   - Sorgenti possono richiedere nuove funzionalità senza modificare codice

4. **Testabilità**
   - Servizi testabili indipendentemente
   - Mock di servizi per test delle sorgenti

5. **Manutenibilità**
   - Logica duplicata rimossa
   - Modifiche in un solo posto

---

## ⚠️ Considerazioni

1. **Retrocompatibilità**
   - Le sorgenti esistenti devono continuare a funzionare
   - I campi nuovi in `SourceMetadata` devono essere opzionali

2. **Performance**
   - `SourceServices` deve essere efficiente
   - Cache dove possibile

3. **Sicurezza**
   - SSL Trust All deve essere configurabile per sorgente
   - Validazione input dalle sorgenti

4. **Documentazione**
   - Documentare come le sorgenti possono richiedere servizi
   - Esempi di configurazione

---

## 🚀 Piano di Implementazione

### **Fase 1: Creare Interfaccia e Implementazione Base**
- [ ] Creare `SourceServices` interface
- [ ] Creare `SourceServicesImpl` con implementazione base
- [ ] Creare modelli di configurazione

### **Fase 2: Integrare con SourceExecutor**
- [ ] Modificare `SourceExecutor.create()` per accettare `SourceServices`
- [ ] Modificare `SourceApiAdapter` per usare servizi
- [ ] Modificare `JavaSourceExecutor` per passare servizi
- [ ] Modificare `PythonSourceExecutor` per passare servizi

### **Fase 3: Refactoring App Principale**
- [ ] Spostare logica da `TottodrilloApp` a `SourceServicesImpl`
- [ ] Spostare logica da `DownloadWorker` a `SourceServicesImpl`
- [ ] Spostare logica da `WebViewBackgroundDownloader` a `SourceServicesImpl`
- [ ] Spostare logica da `WebViewDownloadDialog` a `SourceServicesImpl`

### **Fase 4: Aggiornare Metadata**
- [ ] Aggiungere campi opzionali a `SourceMetadata`
- [ ] Aggiornare sorgenti esistenti con nuova configurazione

### **Fase 5: Testing e Documentazione**
- [ ] Test con sorgenti esistenti
- [ ] Documentazione per sviluppatori sorgenti
- [ ] Esempi di configurazione

---

## 📌 Note Finali

Questa architettura rende l'app **completamente agnostica** dalle sorgenti, fornendo servizi generici che le sorgenti possono richiedere tramite configurazione. Le sorgenti diventano **plugin** veri e propri che si integrano con l'app tramite un'interfaccia standard.

