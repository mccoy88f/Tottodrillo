# Analisi SwitchRoms WebView: Distinzione Popup vs Download

## Problema Identificato

SwitchRoms ha una funzionalità specifica:
- I link di download non sono diretti ma esterni
- Quando si clicca il download, possono aprirsi **popup**
- I popup possono essere:
  1. **Annunci pubblicitari** (da ignorare)
  2. **Vero download** (da intercettare)

La source deve distinguere tra i due.

---

## Come Funziona Attualmente (Beta)

### 1. WebViewDownloadDialog
- Usa `setDownloadListener` per intercettare download
- `setDownloadListener` viene chiamato **SOLO quando parte un download reale** (con `contentDisposition` o `mimetype` appropriato)
- Usa `interceptPatterns` per verificare se l'URL corrisponde a un pattern di download

### 2. Gestione Popup
- I popup vengono gestiti con `onCreateWindow` (WebChromeClient)
- Quando si apre un popup:
  - Crea una nuova WebView per il popup
  - Carica l'URL nel popup
  - **Problema**: Non distingue tra popup annuncio e popup download

### 3. SourceServicesImpl.extractUrlFromWebView
- Usa JavaScript per estrarre URL dalla pagina
- Cerca link che corrispondono ai pattern (`interceptPatterns`)
- **Problema**: Non gestisce popup, solo estrazione URL dalla pagina principale

---

## Problema Potenziale

### Scenario SwitchRoms:
1. Utente clicca su link download → si apre popup
2. Popup può essere:
   - **Annuncio**: URL tipo `ads.example.com` → **da ignorare**
   - **Download**: URL tipo `sto.romsfast.com?token=...` → **da intercettare**

### Comportamento Attuale:
- `setDownloadListener` viene chiamato solo quando parte un download reale
- Se il popup è un annuncio, non viene intercettato (OK)
- Se il popup contiene un link che porta a un download, il download parte ma potrebbe non essere intercettato correttamente

### Cosa Manca:
1. **Logica per distinguere popup annuncio vs popup download**:
   - Dovrebbe verificare se l'URL del popup corrisponde ai pattern
   - Se corrisponde → è un download, intercettalo
   - Se non corrisponde → è un annuncio, chiudilo

2. **Intercettazione download nel popup**:
   - Il popup dovrebbe avere un `setDownloadListener` per intercettare download
   - Attualmente il popup ha solo `shouldOverrideUrlLoading` e `onPageFinished`

---

## Soluzione Proposta

### 1. Migliorare Gestione Popup in WebViewDownloadDialog

Nel metodo `onCreateWindow`, dopo aver creato il popup WebView:

```kotlin
// Verifica se l'URL del popup corrisponde ai pattern di download
val popupUrl = resultMsg?.data?.getString("url")
val isDownloadPopup = popupUrl?.let { url ->
    allPatterns.any { pattern ->
        url.contains(pattern) || url.endsWith(pattern)
    }
} ?: false

if (isDownloadPopup) {
    // È un popup di download, aggiungi download listener
    newWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        // Intercetta il download dal popup
        // ... logica di intercettazione
    }
} else {
    // È un popup annuncio, chiudilo dopo 2 secondi (già implementato)
}
```

### 2. Migliorare SourceServicesImpl.extractUrlFromWebView

Aggiungere supporto per gestire popup:
- Quando viene rilevato un popup, verificare se corrisponde ai pattern
- Se corrisponde, attendere che il download parta nel popup
- Intercettare il download dal popup

---

## Verifica Necessaria

### Domande da Rispondere:
1. **Nel main, come veniva gestito questo caso?**
   - C'era logica hardcoded per SwitchRoms?
   - Come distingueva popup annuncio vs popup download?

2. **I pattern `downloadInterceptPatterns` sono sufficienti?**
   - `["sto.romsfast.com", "?token="]` sono abbastanza specifici?
   - Dovrebbero essere più specifici?

3. **Il download parte sempre dal popup o può partire dalla pagina principale?**
   - Se parte dal popup → serve `setDownloadListener` nel popup
   - Se parte dalla pagina principale → serve logica diversa

---

## Raccomandazioni

### Soluzione Immediata:
1. Aggiungere `setDownloadListener` al popup WebView
2. Verificare se l'URL del popup corrisponde ai pattern prima di chiuderlo
3. Se corrisponde, attendere che il download parta invece di chiudere il popup

### Soluzione a Lungo Termine:
1. Centralizzare la logica di distinzione popup/download in `SourceServices`
2. Permettere alle source di configurare pattern più specifici
3. Aggiungere timeout configurabile per attendere download nei popup

---

## File da Modificare

1. **WebViewDownloadDialog.kt**:
   - Aggiungere logica per distinguere popup annuncio vs download
   - Aggiungere `setDownloadListener` al popup se è un download

2. **SourceServicesImpl.kt** (opzionale):
   - Migliorare `extractUrlFromWebView` per gestire popup
   - Aggiungere metodo helper per verificare se URL corrisponde ai pattern

3. **switchroms/source.json** (opzionale):
   - Verificare se i pattern sono corretti
   - Aggiungere pattern più specifici se necessario

