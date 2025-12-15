# Confronto Main (3.0.0) vs Beta (3.1.0) - SwitchRoms WebView

## Analisi Completata

Ho verificato come funzionava SwitchRoms nella versione 3.0.0 (main) e confrontato con la versione 3.1.0 (beta).

---

## Risultato: **IDENTICO**

### Comportamento nel Main (3.0.0)

1. **Gestione Popup** (righe 148-267):
   - Quando si apre un popup, viene creato un nuovo WebView invisibile
   - Il popup ha un `setDownloadListener` per intercettare i download (riga 198)
   - **Il popup viene chiuso automaticamente dopo 2 secondi** (riga 252-263)
   - Se il download parte prima dei 2 secondi, il popup viene chiuso dal `setDownloadListener` (riga 200-206)

2. **Pattern di Intercettazione**:
   - Carica `downloadInterceptPatterns` dalla source metadata (righe 44-66)
   - Pattern per SwitchRoms: `["sto.romsfast.com", "?token="]`
   - Combina con pattern di default: `[".nsp", ".xci", ".zip", ".7z"]`

3. **Problema Identificato**:
   - **NON c'è logica per distinguere popup annuncio vs popup download PRIMA che il download parta**
   - Il sistema aspetta passivamente che il download parta
   - Se il download parte entro 2 secondi → funziona (popup chiuso dal `setDownloadListener`)
   - Se il download NON parte entro 2 secondi → popup chiuso automaticamente (potrebbe essere un annuncio o un download lento)

### Comportamento nel Beta (3.1.0)

**IDENTICO al main** - Nessuna differenza nella logica di gestione popup.

---

## Conclusione

### Il Problema Esisteva Già nel Main

Il problema che hai descritto (distinguere popup annuncio vs popup download) **esisteva già nella versione 3.0.0** (main). Non è stato introdotto nel beta.

### Come Funziona Attualmente

1. **Popup si apre** → WebView invisibile creata
2. **Download parte entro 2 secondi** → `setDownloadListener` intercetta e chiude popup ✅
3. **Download NON parte entro 2 secondi** → Popup chiuso automaticamente dopo 2 secondi ⚠️
   - Se era un annuncio → OK
   - Se era un download lento → PROBLEMA (download perso)

### Cosa Manca

**Logica per verificare se l'URL del popup corrisponde ai pattern PRIMA di chiuderlo:**

```kotlin
// Nel onCreateWindow, dopo aver creato il popup:
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    
    // Verifica se l'URL del popup corrisponde ai pattern di download
    val isDownloadPopup = url?.let { popupUrl ->
        allPatterns.any { pattern ->
            popupUrl.contains(pattern) || popupUrl.endsWith(pattern)
        }
    } ?: false
    
    if (isDownloadPopup) {
        // È un popup di download, NON chiudere automaticamente
        // Attendere che il download parta (il setDownloadListener lo gestirà)
    } else {
        // È un popup annuncio, chiudere dopo 2 secondi (comportamento attuale)
        android.os.Handler(...).postDelayed({
            // Chiudi popup
        }, 2000)
    }
}
```

---

## Raccomandazione

### Il Problema Non È Stato Introdotto nel Beta

Il sistema agnostico non ha rotto SwitchRoms. Il problema esisteva già nel main.

### Soluzione Proposta

Aggiungere logica per verificare se l'URL del popup corrisponde ai pattern PRIMA di chiuderlo:
- Se corrisponde → NON chiudere, attendere download
- Se non corrisponde → Chiudere dopo 2 secondi (annuncio)

Questo migliorerebbe il comportamento sia per main che per beta.

---

## File da Modificare

1. **WebViewDownloadDialog.kt** (sia main che beta):
   - Aggiungere verifica pattern nell'`onPageFinished` del popup
   - Chiudere popup solo se NON corrisponde ai pattern

2. **SourceServicesImpl.kt** (solo beta):
   - Se si vuole centralizzare la logica, aggiungere supporto per gestire popup in `extractUrlFromWebView`

