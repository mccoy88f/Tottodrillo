# Guida allo Sviluppo di Sorgenti per Tottodrillo

Questa guida spiega come creare sorgenti personalizzate per Tottodrillo.

## Panoramica

Le sorgenti sono pacchetti ZIP che contengono:
- **source.json**: Metadata della sorgente (obbligatorio)
- **platform_mapping.json**: Mapping delle piattaforme (obbligatorio per tutte le sorgenti)
- **api_config.json**: Configurazione degli endpoint API (solo per sorgenti API)
- **README.md**: Documentazione (opzionale)

Tottodrillo supporta tre tipi di sorgenti:
1. **API**: Chiamate HTTP API (come CrocDB)
2. **Java/Kotlin**: Codice Java/Kotlin eseguito localmente
3. **Python**: Script Python eseguito localmente (richiede Chaquopy)

## Struttura del Pacchetto

### Sorgente API
```
my-source.zip
├── source.json              # Obbligatorio: Metadata
├── platform_mapping.json    # Obbligatorio: Mapping piattaforme
├── api_config.json          # Obbligatorio: Configurazione API
└── README.md                # Opzionale: Documentazione
```

### Sorgente Java/Kotlin
```
my-source.zip
├── source.json              # Obbligatorio: Metadata
├── platform_mapping.json    # Obbligatorio: Mapping piattaforme
├── libs/                    # Opzionale: Cartella per JAR dependencies
│   └── dependency.jar
├── classes.jar              # Opzionale: JAR con le classi (se non in libs/)
└── README.md                # Opzionale: Documentazione
```

### Sorgente Python
```
my-source.zip
├── source.json              # Obbligatorio: Metadata
├── platform_mapping.json    # Obbligatorio: Mapping piattaforme
├── main.py                  # Script Python principale
├── requirements.txt         # Opzionale: Dipendenze Python
└── README.md                # Opzionale: Documentazione
```

## 1. source.json

File di metadata che descrive la sorgente.

### Campi Obbligatori

- `id`: Identificatore univoco (es. "mysource", "crocdb")
- `name`: Nome visualizzato nell'app
- `version`: Versione sorgente (es. "1.0.0")
- `type`: Tipo sorgente: `"api"`, `"java"`, o `"python"` (default: `"api"`)

### Campi Specifici per Tipo

**Per sorgenti API:**
- `baseUrl`: URL base dell'API (es. "https://api.example.com") - obbligatorio
- `apiPackage`: Package Java/Kotlin (opzionale, riservato per futuro)

**Per sorgenti Java/Kotlin:**
- `mainClass`: Classe principale completa (es. "com.example.MySource") - obbligatorio
- `dependencies`: Lista di JAR files da includere (opzionale)

**Per sorgenti Python:**
- `pythonScript`: Nome dello script Python principale (es. "main.py") - obbligatorio
- `dependencies`: Lista di file requirements.txt o moduli Python (opzionale)

### Campi Opzionali (tutti i tipi)

- `description`: Descrizione della sorgente
- `author`: Nome dell'autore
- `minAppVersion`: Versione minima app richiesta (es. "1.1.0")
- `baseUrl`: URL base (solo per sorgenti API, opzionale per altri tipi)

### Esempi

**Sorgente API:**
```json
{
  "id": "mysource",
  "name": "My ROM Source",
  "version": "1.0.0",
  "type": "api",
  "description": "Una sorgente personalizzata per ROM",
  "author": "Il Tuo Nome",
  "baseUrl": "https://api.example.com",
  "minAppVersion": "1.1.0",
  "apiPackage": "com.tottodrillo.sources.mysource"
}
```

**Sorgente Java/Kotlin:**
```json
{
  "id": "javasource",
  "name": "Java ROM Source",
  "version": "1.0.0",
  "type": "java",
  "description": "Sorgente che esegue codice Java localmente",
  "author": "Il Tuo Nome",
  "mainClass": "com.example.MyJavaSource",
  "dependencies": ["libs/gson.jar", "libs/okhttp.jar"]
}
```

**Sorgente Python:**
```json
{
  "id": "pythonsource",
  "name": "Python ROM Source",
  "version": "1.0.0",
  "type": "python",
  "description": "Sorgente che esegue script Python localmente",
  "author": "Il Tuo Nome",
  "pythonScript": "main.py",
  "dependencies": ["requirements.txt"]
}
```

## 2. api_config.json

Configurazione degli endpoint API della sorgente.

### Struttura Base

```json
{
  "base_url": "https://api.example.com",
  "endpoints": {
    "endpoint_name": {
      "method": "GET|POST|PUT|PATCH|DELETE",
      "path": "/path/to/endpoint",
      "query_params": [...],
      "body_model": "ModelName",
      "response_model": "ResponseModelName"
    }
  },
  "request_models": {
    "ModelName": {
      "fields": {...}
    }
  },
  "response_models": {
    "ResponseModelName": {
      "wrapper": "ApiResponse",
      "data_path": "data",
      "fields": {...}
    }
  }
}
```

### Endpoint Config

Ogni endpoint deve avere:

- `method`: Metodo HTTP (GET, POST, etc.)
- `path`: Percorso relativo all'URL base
- `query_params`: Lista parametri query (opzionale, solo per GET)
- `body_model`: Nome del modello request (opzionale, solo per POST/PUT/PATCH)
- `response_model`: Nome del modello response (obbligatorio)

### Query Parameters

```json
{
  "name": "param_name",
  "type": "String|Int|Long|List<String>",
  "required": false,
  "default": "default_value"
}
```

### Request/Response Models

I modelli descrivono la struttura dei dati:

```json
{
  "fields": {
    "field_name": {
      "type": "String|Int|Long|List<String>|Map<String,String>",
      "serialized_name": "field_name_in_json",
      "nullable": false
    }
  }
}
```

### Esempio Completo

```json
{
  "base_url": "https://api.example.com",
  "endpoints": {
    "search": {
      "method": "POST",
      "path": "search",
      "body_model": "SearchRequest",
      "response_model": "SearchResults"
    },
    "get_entry": {
      "method": "GET",
      "path": "entry/{id}",
      "query_params": [
        {
          "name": "id",
          "type": "String",
          "required": true
        }
      ],
      "response_model": "EntryResponse"
    }
  },
  "request_models": {
    "SearchRequest": {
      "fields": {
        "query": {
          "type": "String",
          "nullable": false
        },
        "platform": {
          "type": "String",
          "nullable": true
        },
        "page": {
          "type": "Int",
          "nullable": false
        }
      }
    }
  },
  "response_models": {
    "SearchResults": {
      "wrapper": "ApiResponse",
      "data_path": "data",
      "fields": {
        "results": {
          "type": "List<RomEntry>",
          "nullable": false
        },
        "total": {
          "type": "Int",
          "nullable": false
        }
      }
    },
    "EntryResponse": {
      "fields": {
        "entry": {
          "type": "RomEntry",
          "nullable": false
        }
      }
    }
  }
}
```

## 3. platform_mapping.json

**Obbligatorio per tutte le sorgenti.** Questo file mappa i `mother_code` (codici standard di Tottodrillo) ai codici specifici della tua sorgente.

### Struttura

```json
{
  "mapping": {
    "mother_code_1": "codice_sorgente_1",
    "mother_code_2": ["codice_sorgente_2a", "codice_sorgente_2b"],
    "mother_code_3": "codice_sorgente_3"
  }
}
```

### Regole

- **Chiave**: `mother_code` da `platforms_main.json` dell'app (es. `"nes"`, `"snes"`, `"psx"`)
- **Valore**: Può essere:
  - Una stringa singola: `"nes"` → `"Nintendo-Entertainment-System"`
  - Un array di stringhe: `"nds"` → `["DS", "Nintendo-DS"]` (per piattaforme con più varianti)

### Esempio Completo

```json
{
  "mapping": {
    "nes": "NES",
    "snes": "SNES",
    "n64": "N64",
    "gc": "Gamecube",
    "wii": ["Wii", "WiiWare"],
    "gb": ["GB", "Game-Boy"],
    "gbc": ["GBC", "Game-Boy-Color"],
    "gba": ["GBA", "Game-Boy-Advanced"],
    "nds": ["DS", "Nintendo-DS"],
    "genesis": "Genesis",
    "saturn": "Saturn",
    "dreamcast": "Dreamcast",
    "psx": ["PS1", "Playstation"],
    "ps2": ["PS2", "Playstation-2"],
    "ps3": ["PS3", "Playstation-3"],
    "psp": "PSP",
    "xbox": "Xbox"
  }
}
```

### Come Trovare i mother_code

I `mother_code` sono definiti nel file `platforms_main.json` dell'app Tottodrillo. Puoi consultare questo file per vedere tutti i codici disponibili. Ogni `mother_code` rappresenta una piattaforma standardizzata che Tottodrillo riconosce.

**Nota**: Il file `platforms_main.json` rimane nelle assets dell'app e contiene i dati comuni delle piattaforme (nome, brand, immagine, descrizione). Il file `platform_mapping.json` nel tuo ZIP contiene solo il mapping tra `mother_code` e i codici specifici della tua sorgente.

## Validazione

L'app valida automaticamente in base al tipo di sorgente:

**Per tutte le sorgenti:**
- ✅ Presenza di `source.json` e `platform_mapping.json`
- ✅ Validità JSON di entrambi i file
- ✅ `platform_mapping.json` contiene un campo `mapping` di tipo oggetto

**Per sorgenti API:**
- ✅ Presenza di `api_config.json`
- ✅ Campi obbligatori in `source.json` (`id`, `name`, `version`, `type`, `baseUrl`)
- ✅ Formato URL valido
- ✅ Struttura endpoint corretta in `api_config.json`

**Per sorgenti Java/Kotlin:**
- ✅ Campi obbligatori in `source.json` (`id`, `name`, `version`, `type`, `mainClass`)
- ✅ Verifica che la classe principale possa essere caricata (al runtime)

**Per sorgenti Python:**
- ✅ Campi obbligatori in `source.json` (`id`, `name`, `version`, `type`, `pythonScript`)
- ✅ Presenza dello script Python specificato

## Endpoint Richiesti

Una sorgente deve implementare almeno questi endpoint:

1. **search**: Cerca ROM
2. **get_entry**: Ottiene dettagli di una ROM

Endpoint opzionali ma consigliati:

- `get_platforms`: Lista piattaforme supportate
- `get_regions`: Lista regioni supportate

## Formato Dati ROM

Le ROM devono essere mappate al formato standard di Tottodrillo:

```json
{
  "slug": "unique-identifier",
  "rom_id": "optional-id",
  "title": "ROM Title",
  "platform": "platform-code",
  "box_image": "https://...",           // Box art (obbligatoria, null se non disponibile)
  "screen_image": "https://...",        // Screen shot (facoltativa, null se non disponibile)
  "boxart_url": "https://...",          // DEPRECATO: Usa box_image invece
  "boxart_urls": ["https://..."],       // DEPRECATO: Usa box_image e screen_image invece
  "regions": ["US", "EU"],
  "links": [
    {
      "name": "Download Name",
      "type": "direct|torrent",
      "format": "zip|7z|bin",
      "url": "https://...",
      "size_str": "100 MB"
    }
  ]
}
```

### Gestione Immagini

**Nuovo formato (consigliato):**
- `box_image`: URL dell'immagine box art (obbligatoria). Se `null`, l'app userà automaticamente il placeholder della sorgente.
- `screen_image`: URL dell'immagine screen shot (facoltativa). Se presente, verrà mostrata dopo la box art nel carosello.

**Vecchio formato (deprecato, ma ancora supportato per compatibilità):**
- `boxart_url`: URL singolo dell'immagine box art
- `boxart_urls`: Lista di URL immagini (box + screen)

**Nota importante:**
- Se `box_image` è `null`, l'app aggiungerà automaticamente il placeholder definito in `source.json` (`defaultImage`)
- Lo `screen_image` è facoltativo e viene mostrato solo se presente e valido
- L'ordine nel carosello è sempre: box art (o placeholder) → screen shot

## Testing

1. Crea il tuo pacchetto ZIP
2. Usa `SourceInstaller.validateZip()` per testare
3. Installa nell'app e verifica il funzionamento

## Best Practices

1. **Versioning**: Usa semantic versioning (1.0.0, 1.1.0, etc.)
2. **Error Handling**: Fornisci messaggi di errore chiari
3. **Documentation**: Includi sempre un README.md
4. **Testing**: Testa la sorgente prima di distribuirla
5. **Updates**: Mantieni la compatibilità con versioni precedenti

## Esempi

Vedi `sources/crocdb/` per un esempio completo di sorgente API funzionante.

## Sorgenti Java/Kotlin

Le sorgenti Java/Kotlin permettono di eseguire codice personalizzato direttamente nell'app. Questo è utile per:
- Scraping di siti web
- Elaborazione di file locali
- Integrazione con database locali
- Parsing di file HTML/XML
- Qualsiasi logica personalizzata che non richiede API esterne

### Requisiti

1. **Classe principale**: Deve avere i metodi pubblici richiesti (vedi interfaccia `SourceExecutor`)
2. **Costruttore**: Può avere:
   - Costruttore senza parametri
   - Costruttore che accetta `(SourceMetadata, File)` - riceve i metadata e la directory della sorgente
3. **Dipendenze**: I JAR dependencies devono essere inclusi nella cartella `libs/` o nella root del ZIP
4. **Package**: La classe deve essere nel package specificato in `mainClass`

### Struttura del JAR

Il JAR deve contenere:
- La classe principale e tutte le classi necessarie
- Le classi devono essere compilate per Java 17 (compatibile con Android)
- Le dipendenze esterne devono essere incluse come JAR separati nella cartella `libs/`

### Metodi Richiesti

La classe principale deve implementare questi metodi pubblici:

```java
// Cerca ROM nella sorgente
public SearchResults searchRoms(
    String searchKey,        // Termine di ricerca (può essere null)
    List<String> platforms,  // Lista codici piattaforma (può essere vuota)
    List<String> regions,    // Lista codici regione (può essere vuota)
    int maxResults,          // Numero massimo risultati
    int page                 // Numero pagina (1-based)
)

// Ottiene dettagli di una ROM
public EntryResponse getEntry(String slug)

// Ottiene le piattaforme supportate
public Map<String, Object> getPlatforms()

// Ottiene le regioni supportate
public RegionsResponse getRegions()
```

### Esempio Classe Java Completo

```java
package com.example;

import com.tottodrillo.data.model.*;
import com.tottodrillo.domain.model.SourceMetadata;
import java.io.File;
import java.util.*;

public class MyJavaSource {
    private SourceMetadata metadata;
    private File sourceDir;
    
    // Costruttore opzionale - riceve metadata e directory sorgente
    public MyJavaSource(SourceMetadata metadata, File sourceDir) {
        this.metadata = metadata;
        this.sourceDir = sourceDir;
        // Inizializza qui se necessario
    }
    
    // Metodo richiesto: cerca ROM
    public SearchResults searchRoms(
        String searchKey,
        List<String> platforms,
        List<String> regions,
        int maxResults,
        int page
    ) {
        SearchResults results = new SearchResults();
        List<RomEntry> roms = new ArrayList<>();
        
        // Esempio: implementa la tua logica di ricerca
        // Puoi fare scraping, query database, parsing file, etc.
        
        // Esempio di ROM entry
        RomEntry entry = new RomEntry();
        entry.slug = "example-rom";
        entry.title = "Example ROM";
        entry.platform = platforms.isEmpty() ? "nes" : platforms.get(0);
        entry.boxImage = "https://example.com/boxart.png";  // Box art (obbligatoria)
        entry.screenImage = "https://example.com/screen.png"; // Screen shot (facoltativa)
        // Per compatibilità, puoi anche usare:
        // entry.boxartUrl = "https://example.com/boxart.png"; // DEPRECATO
        entry.regions = regions.isEmpty() ? Arrays.asList("US") : regions;
        
        // Aggiungi link download
        DownloadLink link = new DownloadLink();
        link.name = "Download";
        link.type = "direct";
        link.format = "zip";
        link.url = "https://example.com/rom.zip";
        link.sizeStr = "10 MB";
        entry.links = Arrays.asList(link);
        
        roms.add(entry);
        
        results.results = roms;
        results.totalResults = roms.size();
        results.currentPage = page;
        
        return results;
    }
    
    // Metodo richiesto: ottiene entry per slug
    public EntryResponse getEntry(String slug) {
        EntryResponse response = new EntryResponse();
        RomEntry entry = new RomEntry();
        
        // Implementa la logica per recuperare i dettagli della ROM
        entry.slug = slug;
        entry.title = "Example ROM";
        entry.platform = "nes";
        entry.boxImage = "https://example.com/boxart.png";  // Box art (obbligatoria)
        entry.screenImage = "https://example.com/screen.png"; // Screen shot (facoltativa)
        // Per compatibilità, puoi anche usare:
        // entry.boxartUrl = "https://example.com/boxart.png"; // DEPRECATO
        entry.regions = Arrays.asList("US", "EU");
        
        // Aggiungi link download
        DownloadLink link = new DownloadLink();
        link.name = "Download";
        link.type = "direct";
        link.format = "zip";
        link.url = "https://example.com/rom.zip";
        link.sizeStr = "10 MB";
        entry.links = Arrays.asList(link);
        
        response.entry = entry;
        return response;
    }
    
    // Metodo richiesto: ottiene piattaforme
    public Map<String, Object> getPlatforms() {
        Map<String, Object> platforms = new HashMap<>();
        
        // Esempio: restituisci le piattaforme supportate
        Map<String, String> nes = new HashMap<>();
        nes.put("name", "Nintendo Entertainment System");
        platforms.put("nes", nes);
        
        return platforms;
    }
    
    // Metodo richiesto: ottiene regioni
    public RegionsResponse getRegions() {
        RegionsResponse response = new RegionsResponse();
        Map<String, String> regions = new HashMap<>();
        
        regions.put("US", "United States");
        regions.put("EU", "Europe");
        regions.put("JP", "Japan");
        
        response.regions = regions;
        return response;
    }
}
```

**Nota**: Le classi `RomEntry`, `SearchResults`, `EntryResponse`, `RegionsResponse`, `DownloadLink` devono essere incluse nel JAR o disponibili come dipendenze. Puoi copiare le definizioni dal progetto Tottodrillo o creare le tue versioni compatibili.

### Compilazione e Packaging

1. **Compila il codice**: Compila il tuo codice Java/Kotlin in un JAR
   ```bash
   javac -d classes com/example/MyJavaSource.java
   jar cvf mysource.jar -C classes .
   ```

2. **Includi dipendenze**: Metti tutti i JAR dependencies nella cartella `libs/`
   ```
   my-source/
   ├── source.json
   ├── libs/
   │   ├── gson.jar
   │   └── jsoup.jar  # Esempio per web scraping
   └── mysource.jar   # Il tuo JAR principale
   ```

3. **Crea source.json**: Crea il file `source.json` con `type: "java"` e `mainClass`
   ```json
   {
     "id": "mysource",
     "name": "My Source",
     "version": "1.0.0",
     "type": "java",
     "mainClass": "com.example.MyJavaSource"
   }
   ```

4. **Crea il pacchetto ZIP**: Comprimi tutto in un file ZIP
   ```bash
   zip -r my-source.zip source.json libs/ mysource.jar
   ```

### Limitazioni e Considerazioni

- **Sicurezza**: Il codice Java viene eseguito con i permessi dell'app. Assicurati che il codice sia sicuro.
- **Performance**: Il caricamento dinamico delle classi può essere più lento rispetto alle API.
- **Dipendenze**: Tutte le dipendenze devono essere incluse nel pacchetto ZIP.
- **Compatibilità**: Il codice deve essere compatibile con Java 17 e Android API 26+.

## Sorgenti Python

Le sorgenti Python permettono di eseguire script Python direttamente nell'app usando Chaquopy. Questo è utile per:
- Scraping web con librerie Python (BeautifulSoup, Scrapy, etc.)
- Elaborazione dati con pandas, numpy
- Machine learning e analisi dati
- Qualsiasi logica che beneficia delle librerie Python

### Requisiti

1. **Chaquopy configurato**: L'app deve avere Chaquopy configurato (già incluso in Tottodrillo)
2. **Funzione execute**: Lo script Python deve esporre una funzione `execute(params: str) -> str` che accetta JSON e ritorna JSON
3. **Dipendenze**: Le dipendenze Python possono essere specificate in `requirements.txt` o installate dinamicamente

### Struttura dello Script

Lo script Python deve:
- Essere nella root del pacchetto ZIP
- Avere il nome specificato in `pythonScript` nel `source.json`
- Esporre la funzione `execute(params_json: str) -> str`
- Restituire JSON valido per ogni metodo

### Metodi Supportati

La funzione `execute` riceve un JSON con il campo `method` che indica quale operazione eseguire:

- `"searchRoms"`: Cerca ROM
- `"getEntry"`: Ottiene dettagli di una ROM
- `"getPlatforms"`: Ottiene le piattaforme supportate
- `"getRegions"`: Ottiene le regioni supportate

### Esempio Script Python Completo

```python
import json
from typing import Dict, Any, List, Optional

def execute(params_json: str) -> str:
    """
    Funzione principale chiamata da Tottodrillo
    Accetta JSON come stringa e ritorna JSON come stringa
    
    Args:
        params_json: JSON string con i parametri della richiesta
        
    Returns:
        JSON string con la risposta
    """
    try:
        params = json.loads(params_json)
        method = params.get("method")
        
        if method == "searchRoms":
            return search_roms(params)
        elif method == "getEntry":
            return get_entry(params)
        elif method == "getPlatforms":
            return get_platforms()
        elif method == "getRegions":
            return get_regions()
        else:
            return json.dumps({"error": f"Metodo sconosciuto: {method}"})
    except Exception as e:
        return json.dumps({"error": str(e)})

def search_roms(params: Dict[str, Any]) -> str:
    """
    Cerca ROM nella sorgente
    
    Args:
        params: Dizionario con:
            - search_key: str (opzionale)
            - platforms: List[str]
            - regions: List[str]
            - max_results: int
            - page: int
            
    Returns:
        JSON string con SearchResults
    """
    search_key = params.get("search_key")
    platforms = params.get("platforms", [])
    regions = params.get("regions", [])
    max_results = params.get("max_results", 50)
    page = params.get("page", 1)
    
    # Implementa la tua logica qui
    # Esempio: web scraping, query database, etc.
    
    results = []
    
    # Esempio di ROM entry
    rom_entry = {
        "slug": "example-rom",
        "title": "Example ROM",
        "platform": platforms[0] if platforms else "nes",
        "box_image": "https://example.com/boxart.png",  # Box art (obbligatoria, None se non disponibile)
        "screen_image": "https://example.com/screen.png",  # Screen shot (facoltativa, None se non disponibile)
        # Per compatibilità, puoi anche usare:
        # "boxart_url": "https://example.com/boxart.png",  # DEPRECATO
        "regions": regions if regions else ["US"],
        "links": [
            {
                "name": "Download",
                "type": "direct",
                "format": "zip",
                "url": "https://example.com/rom.zip",
                "size_str": "10 MB"
            }
        ]
    }
    results.append(rom_entry)
    
    response = {
        "results": results,
        "total_results": len(results),
        "current_page": page,
        "total_pages": 1
    }
    
    return json.dumps(response)

def get_entry(params: Dict[str, Any]) -> str:
    """
    Ottiene una entry specifica per slug
    
    Args:
        params: Dizionario con:
            - slug: str
            
    Returns:
        JSON string con EntryResponse
    """
    slug = params.get("slug")
    
    # Implementa la logica per recuperare i dettagli della ROM
    entry = {
        "slug": slug,
        "title": "Example ROM",
        "platform": "nes",
        "box_image": "https://example.com/boxart.png",  # Box art (obbligatoria, None se non disponibile)
        "screen_image": "https://example.com/screen.png",  # Screen shot (facoltativa, None se non disponibile)
        # Per compatibilità, puoi anche usare:
        # "boxart_url": "https://example.com/boxart.png",  # DEPRECATO
        "regions": ["US", "EU"],
        "links": [
            {
                "name": "Download",
                "type": "direct",
                "format": "zip",
                "url": "https://example.com/rom.zip",
                "size_str": "10 MB"
            }
        ]
    }
    
    response = {
        "entry": entry
    }
    
    return json.dumps(response)

def get_platforms() -> str:
    """
    Ottiene le piattaforme disponibili
    
    Returns:
        JSON string con le piattaforme
    """
    platforms = {
        "nes": {
            "name": "Nintendo Entertainment System"
        },
        "snes": {
            "name": "Super Nintendo Entertainment System"
        }
    }
    
    return json.dumps(platforms)

def get_regions() -> str:
    """
    Ottiene le regioni disponibili
    
    Returns:
        JSON string con RegionsResponse
    """
    regions = {
        "US": "United States",
        "EU": "Europe",
        "JP": "Japan"
    }
    
    response = {
        "regions": regions
    }
    
    return json.dumps(response)
```

### Esempio con Web Scraping

```python
import json
import requests
from bs4 import BeautifulSoup

def execute(params_json: str) -> str:
    params = json.loads(params_json)
    method = params.get("method")
    
    if method == "searchRoms":
        return search_roms(params)
    # ... altri metodi

def search_roms(params: Dict[str, Any]) -> str:
    search_key = params.get("search_key", "")
    
    # Esempio di web scraping
    url = f"https://example.com/search?q={search_key}"
    response = requests.get(url)
    soup = BeautifulSoup(response.content, 'html.parser')
    
    # Parsing HTML e estrazione dati
    results = []
    for item in soup.find_all('div', class_='rom-item'):
        # Estrai box art e screen shot separatamente
        box_img = item.find('img', class_='boxart')
        screen_img = item.find('img', class_='screen')
        
        rom = {
            "slug": item.get('data-slug'),
            "title": item.find('h2').text,
            "platform": item.get('data-platform'),
            "box_image": box_img.get('src') if box_img else None,  # Box art (obbligatoria)
            "screen_image": screen_img.get('src') if screen_img else None,  # Screen shot (facoltativa)
            # Per compatibilità, puoi anche usare:
            # "boxart_url": box_img.get('src') if box_img else None,  # DEPRECATO
            "links": [{
                "name": "Download",
                "type": "direct",
                "url": item.find('a', class_='download').get('href')
            }]
        }
        results.append(rom)
    
    return json.dumps({
        "results": results,
        "total_results": len(results)
    })
```

**Nota**: Per usare librerie come `requests` o `beautifulsoup4`, devi includerle nel `requirements.txt` o installarle tramite Chaquopy.

### Configurazione Chaquopy

Chaquopy è già configurato in Tottodrillo. La configurazione include:

- **Versione Python**: 3.11
- **ABI supportate**: armeabi-v7a, arm64-v8a, x86, x86_64
- **Dipendenze**: Possono essere installate dinamicamente dalle sorgenti

### Gestione Dipendenze Python

Le dipendenze Python possono essere gestite in due modi:

1. **Nel build.gradle.kts dell'app** (per dipendenze comuni):
```kotlin
python {
    pip {
        install("requests")
        install("beautifulsoup4")
    }
}
```

2. **Nelle sorgenti** (per dipendenze specifiche):
   - Crea un file `requirements.txt` nella sorgente
   - Le dipendenze verranno installate automaticamente quando la sorgente viene caricata

**Esempio requirements.txt:**
```
requests>=2.31.0
beautifulsoup4>=4.12.0
lxml>=4.9.0
```

### Packaging di una Sorgente Python

1. **Crea lo script Python**: Crea il tuo script principale (es. `main.py`)
2. **Aggiungi dipendenze** (opzionale): Crea `requirements.txt` se necessario
3. **Crea source.json**:
```json
{
  "id": "pythonsource",
  "name": "Python Source",
  "version": "1.0.0",
  "type": "python",
  "pythonScript": "main.py"
}
```

4. **Crea il pacchetto ZIP**:
```bash
zip -r python-source.zip source.json main.py requirements.txt
```

### Limitazioni e Considerazioni

- **Dimensione APK**: Chaquopy aumenta significativamente la dimensione dell'APK (~50-100MB)
- **Performance**: L'esecuzione Python può essere più lenta rispetto a Java/Kotlin
- **Dipendenze**: Alcune librerie Python potrebbero non essere compatibili con Android
- **Debugging**: Il debugging di codice Python può essere più complesso
- **Sicurezza**: Il codice Python viene eseguito con i permessi dell'app

## Riepilogo: Quale Tipo di Sorgente Scegliere?

### Sorgente API
**Usa quando:**
- Hai accesso a un'API HTTP REST
- Vuoi la soluzione più semplice e veloce
- Non hai bisogno di logica complessa lato client

**Vantaggi:**
- Setup semplice (solo JSON)
- Performance ottimali
- Nessun codice da scrivere

**Svantaggi:**
- Richiede un'API esistente
- Dipende dalla disponibilità del server

### Sorgente Java/Kotlin
**Usa quando:**
- Devi fare scraping web
- Hai bisogno di elaborare file locali
- Vuoi integrare con database locali
- Preferisci Java/Kotlin a Python

**Vantaggi:**
- Performance native
- Accesso completo alle API Android
- Nessuna dipendenza esterna (oltre ai JAR)

**Svantaggi:**
- Richiede compilazione in JAR
- Più complesso da sviluppare
- Gestione manuale delle dipendenze

### Sorgente Python
**Usa quando:**
- Hai bisogno di librerie Python potenti (pandas, numpy, etc.)
- Vuoi fare scraping avanzato (BeautifulSoup, Scrapy)
- Hai già codice Python esistente
- Preferisci Python per la logica complessa

**Vantaggi:**
- Accesso a un vasto ecosistema di librerie
- Sviluppo rapido per logica complessa
- Ideale per data processing

**Svantaggi:**
- Aumenta significativamente la dimensione dell'APK
- Performance potenzialmente più lente
- Alcune librerie potrebbero non essere compatibili

## Checklist per Creare una Sorgente

- [ ] Decidi il tipo di sorgente (API, Java, Python)
- [ ] Crea il file `source.json` con tutti i campi obbligatori
- [ ] Per sorgenti API: crea `api_config.json`
- [ ] Per sorgenti Java: compila il codice in JAR e includi dipendenze
- [ ] Per sorgenti Python: crea lo script Python e `requirements.txt` (se necessario)
- [ ] Testa la sorgente localmente
- [ ] Valida il pacchetto ZIP con `SourceInstaller.validateZip()`
- [ ] Crea un README.md con istruzioni
- [ ] Crea il pacchetto ZIP finale
- [ ] Testa l'installazione nell'app

## Supporto

Per domande o problemi:
- Apri una issue su GitHub
- Consulta la documentazione dell'API che stai integrando
- Verifica gli esempi nella cartella `sources/`
- Controlla i log dell'app per errori di esecuzione

