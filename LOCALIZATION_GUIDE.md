# Guida alla Localizzazione - Tottodrillo 🌍

Questa guida spiega come implementare il supporto multilingua (italiano e inglese) nell'app Tottodrillo.

## 📁 Struttura delle Risorse Android

Android gestisce la localizzazione attraverso cartelle `values-{locale}`:

```
app/src/main/res/
├── values/              # Default (italiano)
│   └── strings.xml
├── values-en/           # Inglese
│   └── strings.xml
└── values-it/           # Italiano (opzionale, se vuoi essere esplicito)
    └── strings.xml
```

## 🔧 Passi per Implementare la Localizzazione

### 1. Creare la cartella per l'inglese

Crea la cartella `app/src/main/res/values-en/` e il file `strings.xml` con tutte le traduzioni.

### 2. Usare `stringResource()` in Compose

In Jetpack Compose, invece di usare stringhe hardcoded:

```kotlin
// ❌ SBAGLIATO
Text("Dettaglio ROM")

// ✅ CORRETTO
Text(stringResource(R.string.rom_detail_title))
```

### 3. Aggiungere tutte le stringhe mancanti

Devi:
1. Identificare tutte le stringhe hardcoded nel codice
2. Aggiungerle a `values/strings.xml` (italiano)
3. Aggiungerle a `values-en/strings.xml` (inglese)

### 4. Gestire parametri dinamici

Per stringhe con parametri:

```xml
<!-- strings.xml -->
<string name="search_no_results">Nessun risultato per "%1$s"</string>
```

```kotlin
// Uso in Compose
Text(stringResource(R.string.search_no_results, query))
```

### 5. (Opzionale) Selettore di Lingua

Aggiungi un selettore nelle impostazioni per permettere all'utente di cambiare lingua manualmente.

## 📝 Esempio Pratico

### Prima (hardcoded):
```kotlin
TopAppBar(
    title = { Text("Dettaglio ROM") },
    // ...
)
```

### Dopo (localizzato):
```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.rom_detail_title)) },
    // ...
)
```

E nei file XML:

**values/strings.xml:**
```xml
<string name="rom_detail_title">Dettaglio ROM</string>
```

**values-en/strings.xml:**
```xml
<string name="rom_detail_title">ROM Details</string>
```

## 🎯 Checklist Implementazione

- [x] Creare `values-en/strings.xml` con tutte le traduzioni ✅
- [ ] Sostituire tutte le stringhe hardcoded con `stringResource()`
- [ ] Testare l'app in entrambe le lingue
- [ ] Verificare che tutte le stringhe siano tradotte
- [ ] (Opzionale) Aggiungere selettore lingua nelle impostazioni

## 🌐 Selettore di Lingua (Opzionale)

Se vuoi permettere all'utente di cambiare lingua manualmente, puoi aggiungere un selettore nelle impostazioni.

### 1. Aggiungi le stringhe

**values/strings.xml:**
```xml
<string name="settings_language">Lingua</string>
<string name="settings_language_desc">Seleziona la lingua dell\'app</string>
<string name="language_italian">Italiano</string>
<string name="language_english">English</string>
```

**values-en/strings.xml:**
```xml
<string name="settings_language">Language</string>
<string name="settings_language_desc">Select the app language</string>
<string name="language_italian">Italiano</string>
<string name="language_english">English</string>
```

### 2. Gestisci la lingua in DataStore

Aggiungi una preferenza per la lingua in `DownloadConfigRepository.kt`:

```kotlin
val LANGUAGE = stringPreferencesKey("app_language")

suspend fun setLanguage(locale: String) {
    context.dataStore.edit { preferences ->
        preferences[LANGUAGE] = locale
    }
}

val language: Flow<String> = context.dataStore.data.map { preferences ->
    preferences[LANGUAGE] ?: "it" // Default italiano
}
```

### 3. Applica la lingua in MainActivity

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var configRepository: DownloadConfigRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Applica la lingua all'avvio
        lifecycleScope.launch {
            val language = configRepository.language.first()
            setAppLocale(language)
        }
        
        // ... resto del codice
    }
    
    private fun setAppLocale(locale: String) {
        val config = resources.configuration
        val localeObj = Locale(locale)
        Locale.setDefault(localeObj)
        config.setLocale(localeObj)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
```

### 4. Aggiungi UI nelle Impostazioni

```kotlin
// In DownloadSettingsScreen.kt
SettingItem(
    title = stringResource(R.string.settings_language),
    description = stringResource(R.string.settings_language_desc),
    onClick = {
        // Mostra dialog con opzioni lingua
    }
)
```

## 📚 Risorse Utili

- [Android Localization Guide](https://developer.android.com/guide/topics/resources/localization)
- [Jetpack Compose String Resources](https://developer.android.com/jetpack/compose/resources#string-resources)

