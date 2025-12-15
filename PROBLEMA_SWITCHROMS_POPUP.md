# Problema SwitchRoms: Popup HTML Scaricato Come File

## Problema Identificato

### Scenario:
1. SwitchRoms ha link esterni (es. MediaFire)
2. Utente clicca download → si apre popup (MediaFire)
3. Nel popup c'è un annuncio pubblicitario (pagina HTML)
4. Il `setDownloadListener` del popup viene chiamato anche per pagine HTML
5. **NON c'è verifica del mimetype** nel popup
6. L'app pensa che sia un download e chiude il WebView
7. **Risultato**: L'app scarica la pagina HTML del popup come file invece del vero download

### Codice Attuale (Problema)

Nel `setDownloadListener` del popup (riga 198):
```kotlin
newWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
    // ❌ NON verifica se è HTML o file reale
    // Chiama sempre onDownloadUrlExtracted
    onDownloadUrlExtracted(url, updatedLink, popupCookies)
}
```

Nel `setDownloadListener` principale (riga 403):
```kotlin
setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
    // ✅ Verifica mimetype (riga 509-510)
    val matchesMimeType = mimetype?.contains("application/octet-stream") == true ||
        mimetype?.contains("application/x-") == true
    
    // ⚠️ Ma chiama comunque onDownloadUrlExtracted anche se non corrisponde (riga 516-517)
    if (matchesPattern || matchesMimeType) {
        onDownloadUrlExtracted(url, updatedLink, cookies)
    } else {
        // ⚠️ PROBLEMA: Chiama comunque anche se è HTML!
        onDownloadUrlExtracted(url, updatedLink, cookies)
    }
}
```

### Blocco Popup JavaScript

✅ **Il blocco popup JavaScript è attivo** sia nella pagina principale che nel popup:
- Pagina principale: riga 361-373 (`window.adLink = null`)
- Popup: riga 181-193 (`window.adLink = null`)

**Ma non basta** perché:
- Il popup si apre comunque (gestito da `onCreateWindow`)
- Il blocco JavaScript previene solo alcuni popup, non tutti
- Se il popup si apre, il `setDownloadListener` viene chiamato anche per HTML

---

## Soluzione

### 1. Verificare Mimetype nel Popup

Nel `setDownloadListener` del popup, verificare se è HTML e ignorarlo:

```kotlin
newWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
    // Verifica se è una pagina HTML (non un file)
    val isHtmlPage = mimetype?.contains("text/html") == true || 
                     mimetype?.contains("text/plain") == true ||
                     (mimetype == null && !url.contains(".") && !url.contains("?"))
    
    if (isHtmlPage) {
        // È una pagina HTML (probabilmente un annuncio), chiudi il popup e ignora
        android.util.Log.d("WebViewDownloadDialog", "🚫 Popup HTML rilevato, chiudo popup: $url")
        try {
            (newWebView.parent as? android.view.ViewGroup)?.removeView(newWebView)
        } catch (e: Exception) {
            // Popup già chiuso
        }
        popupOpen = false
        return@setDownloadListener // Ignora, non è un download
    }
    
    // Verifica anche se corrisponde ai pattern di download
    val matchesPattern = allPatterns.any { pattern ->
        url.contains(pattern) || url.endsWith(pattern)
    }
    val matchesMimeType = mimetype?.contains("application/octet-stream") == true ||
        mimetype?.contains("application/x-") == true ||
        mimetype?.contains("application/zip") == true ||
        mimetype?.contains("application/x-zip") == true
    
    if (!matchesPattern && !matchesMimeType) {
        // Non corrisponde ai pattern e non è un file, probabilmente è HTML
        android.util.Log.d("WebViewDownloadDialog", "🚫 URL non corrisponde ai pattern, chiudo popup: $url")
        try {
            (newWebView.parent as? android.view.ViewGroup)?.removeView(newWebView)
        } catch (e: Exception) {
            // Popup già chiuso
        }
        popupOpen = false
        return@setDownloadListener // Ignora
    }
    
    // È un vero download, procedi
    // ... resto del codice ...
}
```

### 2. Migliorare Verifica nel DownloadListener Principale

Anche nel `setDownloadListener` principale, non chiamare `onDownloadUrlExtracted` se è HTML:

```kotlin
setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
    // Verifica se è HTML
    val isHtmlPage = mimetype?.contains("text/html") == true || 
                     mimetype?.contains("text/plain") == true
    
    if (isHtmlPage) {
        android.util.Log.d("WebViewDownloadDialog", "🚫 Download HTML ignorato: $url")
        return@setDownloadListener // Ignora pagine HTML
    }
    
    // ... resto del codice ...
}
```

### 3. Chiudere Popup Automaticamente se è HTML

Nel `onPageFinished` del popup, verificare se l'URL corrisponde ai pattern:
- Se NON corrisponde → è probabilmente un annuncio, chiudere dopo 1 secondo
- Se corrisponde → è probabilmente un download, attendere che parta

---

## Verifica Blocco Popup

✅ **Il blocco popup JavaScript è attivo su SwitchRoms**:
- Sia nella pagina principale che nel popup
- Usa `window.adLink = null` per disabilitare alcuni popup
- Ma non previene tutti i popup (alcuni si aprono comunque)

---

## File da Modificare

**WebViewDownloadDialog.kt**:
1. Aggiungere verifica mimetype nel `setDownloadListener` del popup
2. Aggiungere verifica mimetype nel `setDownloadListener` principale
3. Migliorare logica di chiusura popup in `onPageFinished`

