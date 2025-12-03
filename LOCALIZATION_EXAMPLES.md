# Esempi Pratici di Localizzazione

Questo documento mostra esempi concreti di come sostituire le stringhe hardcoded con `stringResource()`.

## 📝 Import Necessario

In tutti i file Compose, aggiungi:

```kotlin
import androidx.compose.ui.res.stringResource
import com.crocdb.friends.R
```

## 🔄 Esempi di Conversione

### 1. HomeScreen.kt

**Prima:**
```kotlin
Text("Esplora il mondo")
Text("del retro gaming")
SectionHeader(title = "Piattaforme Popolari")
SectionHeader(title = "In Evidenza")
SectionHeader(title = "Recenti")
SectionHeader(title = "Preferiti")
```

**Dopo:**
```kotlin
Text(stringResource(R.string.home_welcome))
Text(stringResource(R.string.home_subtitle))
SectionHeader(title = stringResource(R.string.home_popular_platforms))
SectionHeader(title = stringResource(R.string.home_featured))
SectionHeader(title = stringResource(R.string.home_recent))
SectionHeader(title = stringResource(R.string.home_favorites))
```

### 2. RomDetailScreen.kt

**Prima:**
```kotlin
TopAppBar(
    title = { Text("Dettaglio ROM") },
    // ...
)
Text("Scaricato")
Text("Riprova")
Text("Download")
Text("${downloadStatus.progress}%")
Text("${extractionStatus.progress}%")
```

**Dopo:**
```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.rom_detail_title)) },
    // ...
)
Text(stringResource(R.string.rom_detail_downloaded))
Text(stringResource(R.string.retry))
Text(stringResource(R.string.rom_detail_download))
Text(stringResource(R.string.rom_detail_download_progress, downloadStatus.progress))
Text(stringResource(R.string.rom_detail_extraction_progress, extractionStatus.progress))
```

### 3. DownloadSettingsScreen.kt

**Prima:**
```kotlin
TopAppBar(
    title = { Text("Impostazioni") },
    // ...
)
Text("Cancella storico download ed estrazioni")
Text("Questa azione eliminerà tutti i file .status...")
Text("Cancella")
Text("Annulla")
```

**Dopo:**
```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.settings_title)) },
    // ...
)
Text(stringResource(R.string.settings_clear_history_dialog_title))
Text(stringResource(R.string.settings_clear_history_dialog_message))
Text(stringResource(R.string.settings_clear_history_dialog_confirm))
Text(stringResource(R.string.settings_clear_history_dialog_cancel))
```

### 4. Stringhe con Parametri

**Prima:**
```kotlin
Text("Nessun risultato per \"$query\"")
```

**Dopo:**
```kotlin
Text(stringResource(R.string.search_no_results, query))
```

Nel file XML:
```xml
<string name="search_no_results">Nessun risultato per "%1$s"</string>
```

### 5. ContentDescription per Accessibilità

**Prima:**
```kotlin
Icon(
    imageVector = Icons.Default.Refresh,
    contentDescription = "Ricarica"
)
```

**Dopo:**
```kotlin
Icon(
    imageVector = Icons.Default.Refresh,
    contentDescription = stringResource(R.string.refresh)
)
```

## 🎯 Checklist per File

Per ogni file Compose:

1. ✅ Aggiungi `import androidx.compose.ui.res.stringResource`
2. ✅ Aggiungi `import com.crocdb.friends.R`
3. ✅ Identifica tutte le stringhe hardcoded
4. ✅ Aggiungi le stringhe a `values/strings.xml` (italiano)
5. ✅ Aggiungi le stringhe a `values-en/strings.xml` (inglese)
6. ✅ Sostituisci le stringhe hardcoded con `stringResource()`
7. ✅ Testa l'app in entrambe le lingue

## 📋 File da Modificare

Elenco dei file principali che contengono stringhe hardcoded:

- [ ] `HomeScreen.kt`
- [ ] `RomDetailScreen.kt`
- [ ] `SearchScreen.kt`
- [ ] `ExploreScreen.kt`
- [ ] `DownloadSettingsScreen.kt`
- [ ] `DownloadsScreen.kt`
- [ ] Altri file Compose con testo

## 🔍 Come Trovare Stringhe Hardcoded

Usa questa ricerca regex in Android Studio:
```
Text\(".*"\)
```

Oppure cerca manualmente:
- `Text("...")`
- `title = "..."`
- `contentDescription = "..."`
- Stringhe tra virgolette doppie

## ⚠️ Note Importanti

1. **Non tradurre nomi propri**: "Tottodrillo", "CrocDB", "ES-DE" rimangono invariati
2. **Parametri**: Usa `%1$s` per stringhe, `%1$d` per numeri
3. **Plurali**: Android supporta plurali con `plurals` (vedi documentazione Android)
4. **Formattazione**: Mantieni la formattazione HTML se presente (`<b>`, `<i>`, ecc.)

