# 🔧 Troubleshooting Guide - Crocdb Friends

## ⚠️ Problema: Gradle Configuration Failed

### Errore
```
Failed to notify project evaluation listener.
'org.gradle.api.file.FileCollection org.gradle.api.artifacts.Configuration.fileCollection'
```

### ✅ Soluzione
Ho già applicato i fix necessari nel progetto. Verifica di avere:

1. **Android Studio Hedgehog (2023.1.1) o superiore**
2. **JDK 17** configurato
3. **Gradle 8.2** (già configurato nel progetto)

### Passi per risolvere:

1. **Sync Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

2. **Invalidate Caches** (se serve)
   ```
   File → Invalidate Caches / Restart → Invalidate and Restart
   ```

3. **Verifica JDK**
   ```
   File → Project Structure → SDK Location
   Assicurati che JDK sia 17
   ```

---

## 🐛 Altri Problemi Comuni

### 1. Plugin KSP non trovato

**Errore**: `Plugin [id: 'com.google.devtools.ksp'] was not found`

**Soluzione**:
```kotlin
// build.gradle.kts (root)
plugins {
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
```

### 2. Compose Compiler Version

**Errore**: Compose compiler version mismatch

**Soluzione**: Rimuovere `composeOptions` dal build.gradle.kts (già fatto)

### 3. Network Permission Denied

**Errore**: App crasha su download

**Soluzione**: Verifica permessi in AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 4. FileProvider non configurato

**Errore**: Crash su download completato

**Soluzione**: Verifica che `res/xml/file_paths.xml` esista

---

## ✅ Checklist Pre-Build

- [ ] Android Studio Hedgehog+
- [ ] JDK 17
- [ ] Gradle sync completato
- [ ] No errori in build.gradle.kts
- [ ] AndroidManifest.xml valido
- [ ] res/xml/file_paths.xml presente

---

## 📝 Versioni Corrette

```groovy
Android Gradle Plugin: 8.2.2
Kotlin: 1.9.22
Hilt: 2.50
KSP: 1.9.22-1.0.17
Gradle: 8.2
Compose BOM: 2023.10.01
```

---

## 🚀 Build Commands

### Clean Build
```bash
./gradlew clean
./gradlew assembleDebug
```

### Install su dispositivo
```bash
./gradlew installDebug
```

### Build Release
```bash
./gradlew assembleRelease
```

---

## 📞 Supporto

Se i problemi persistono:

1. Controlla versione Android Studio
2. Verifica JDK 17
3. Fai clean + rebuild
4. Invalida cache
5. Riavvia Android Studio

---

**Ultimo aggiornamento**: Dicembre 2024  
**Versione progetto**: 1.0.0
