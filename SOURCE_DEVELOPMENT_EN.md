# Tottodrillo Source Development Guide

This guide explains how to create custom sources for Tottodrillo.

## Overview

Sources are ZIP packages that contain:
- **source.json**: Source metadata (required)
- **platform_mapping.json**: Platform mapping (required for all sources)
- **api_config.json**: API endpoint configuration (only for API sources)
- **README.md**: Documentation (optional)

Tottodrillo supports three types of sources:
1. **API**: HTTP API calls (like CrocDB)
2. **Java/Kotlin**: Java/Kotlin code executed locally
3. **Python**: Python scripts executed locally (requires Chaquopy)

## Package Structure

### API Source
```
my-source.zip
├── source.json              # Required: Metadata
├── platform_mapping.json    # Required: Platform mapping
├── api_config.json          # Required: API configuration
└── README.md                # Optional: Documentation
```

### Java/Kotlin Source
```
my-source.zip
├── source.json              # Required: Metadata
├── platform_mapping.json    # Required: Platform mapping
├── libs/                    # Optional: Folder for JAR dependencies
│   └── dependency.jar
├── classes.jar              # Optional: JAR with classes (if not in libs/)
└── README.md                # Optional: Documentation
```

### Python Source
```
my-source.zip
├── source.json              # Required: Metadata
├── platform_mapping.json    # Required: Platform mapping
├── main.py                  # Main Python script
├── requirements.txt         # Optional: Python dependencies
└── README.md                # Optional: Documentation
```

## 1. source.json

Metadata file that describes the source.

### Required Fields

- `id`: Unique identifier (e.g., "mysource", "crocdb")
- `name`: Display name in the app
- `version`: Source version (e.g., "1.0.0")
- `type`: Source type: `"api"`, `"java"`, or `"python"` (default: `"api"`)

### Type-Specific Fields

**For API sources:**
- `baseUrl`: Base API URL (e.g., "https://api.example.com") - required
- `apiPackage`: Java/Kotlin package (optional, reserved for future)

**For Java/Kotlin sources:**
- `mainClass`: Full main class name (e.g., "com.example.MySource") - required
- `dependencies`: List of JAR files to include (optional)

**For Python sources:**
- `pythonScript`: Main Python script name (e.g., "main.py") - required
- `dependencies`: List of requirements.txt files or Python modules (optional)

### Optional Fields (all types)

- `description`: Source description
- `author`: Author name
- `minAppVersion`: Minimum app version required (e.g., "1.1.0")
- `baseUrl`: Base URL (only for API sources, optional for other types)
- `imageRefererPattern`: Pattern for image Referer header (e.g., "https://example.com/vault/{id}")

### Examples

**API Source:**
```json
{
  "id": "mysource",
  "name": "My ROM Source",
  "version": "1.0.0",
  "type": "api",
  "description": "A custom ROM source",
  "author": "Your Name",
  "baseUrl": "https://api.example.com",
  "minAppVersion": "1.1.0",
  "apiPackage": "com.tottodrillo.sources.mysource"
}
```

**Java/Kotlin Source:**
```json
{
  "id": "javasource",
  "name": "Java ROM Source",
  "version": "1.0.0",
  "type": "java",
  "description": "Source that executes Java code locally",
  "author": "Your Name",
  "mainClass": "com.example.MyJavaSource",
  "dependencies": ["libs/gson.jar", "libs/okhttp.jar"]
}
```

**Python Source:**
```json
{
  "id": "pythonsource",
  "name": "Python ROM Source",
  "version": "1.0.0",
  "type": "python",
  "description": "Source that executes Python scripts locally",
  "author": "Your Name",
  "pythonScript": "main.py",
  "dependencies": ["requirements.txt"],
  "imageRefererPattern": "https://example.com/vault/{id}"
}
```

## 2. api_config.json

API endpoint configuration for the source.

### Base Structure

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

Each endpoint must have:

- `method`: HTTP method (GET, POST, etc.)
- `path`: Path relative to base URL
- `query_params`: List of query parameters (optional, only for GET)
- `body_model`: Request model name (optional, only for POST/PUT/PATCH)
- `response_model`: Response model name (required)

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

Models describe the data structure:

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

### Complete Example

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
          "nullable": true
        }
      }
    }
  }
}
```

## 3. platform_mapping.json

**Required for all sources.** This file maps `mother_code` (Tottodrillo's standard codes) to your source-specific codes.

### Structure

```json
{
  "mapping": {
    "mother_code_1": "source_code_1",
    "mother_code_2": ["source_code_2a", "source_code_2b"],
    "mother_code_3": "source_code_3"
  }
}
```

### Rules

- **Key**: `mother_code` from the app's `platforms_main.json` (e.g., `"nes"`, `"snes"`, `"psx"`)
- **Value**: Can be:
  - A single string: `"nes"` → `"Nintendo-Entertainment-System"`
  - An array of strings: `"nds"` → `["DS", "Nintendo-DS"]` (for platforms with multiple variants)

### Complete Example

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

### How to Find mother_code

The `mother_code` values are defined in Tottodrillo's `platforms_main.json` file. You can check this file to see all available codes. Each `mother_code` represents a standardized platform that Tottodrillo recognizes.

**Note**: The `platforms_main.json` file remains in the app's assets and contains common platform data (name, brand, image, description). The `platform_mapping.json` file in your ZIP contains only the mapping between `mother_code` and your source-specific codes.

## Validation

The app automatically validates based on source type:

**For all sources:**
- ✅ Presence of `source.json` and `platform_mapping.json`
- ✅ Valid JSON for both files
- ✅ `platform_mapping.json` contains a `mapping` field of type object

**For API sources:**
- ✅ Presence of `api_config.json`
- ✅ Required fields in `source.json` (`id`, `name`, `version`, `type`, `baseUrl`)
- ✅ Valid URL format
- ✅ Correct endpoint structure in `api_config.json`

**For Java/Kotlin sources:**
- ✅ Required fields in `source.json` (`id`, `name`, `version`, `type`, `mainClass`)
- ✅ Verification that the main class can be loaded (at runtime)

**For Python sources:**
- ✅ Required fields in `source.json` (`id`, `name`, `version`, `type`, `pythonScript`)
- ✅ Presence of the specified Python script

## Required Endpoints

A source must implement at least these endpoints:

1. **search**: Search ROMs
2. **get_entry**: Get ROM details

Optional but recommended endpoints:

- `get_platforms`: List of supported platforms
- `get_regions`: List of supported regions

## ROM Data Format

ROMs must be mapped to Tottodrillo's standard format:

```json
{
  "slug": "unique-identifier",
  "rom_id": "optional-id",
  "title": "ROM Title",
  "platform": "platform-code",
  "box_image": "https://...",           // Box art (required, null if not available)
  "screen_image": "https://...",        // Screen shot (optional, null if not available)
  "boxart_url": "https://...",          // DEPRECATED: Use box_image instead
  "boxart_urls": ["https://..."],       // DEPRECATED: Use box_image and screen_image instead
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

### Image Handling

**New format (recommended):**
- `box_image`: URL of the box art image (required). If `null`, the app will automatically use the source's placeholder.
- `screen_image`: URL of the screen shot image (optional). If present, it will be shown after the box art in the carousel.

**Old format (deprecated, but still supported for backward compatibility):**
- `boxart_url`: Single URL of the box art image
- `boxart_urls`: List of image URLs (box + screen)

**Important notes:**
- If `box_image` is `null`, the app will automatically add the placeholder defined in `source.json` (`defaultImage`)
- `screen_image` is optional and will only be shown if present and valid
- The carousel order is always: box art (or placeholder) → screen shot

## Testing

1. Create your ZIP package
2. Use `SourceInstaller.validateZip()` to test
3. Install in the app and verify functionality

## Best Practices

1. **Versioning**: Use semantic versioning (1.0.0, 1.1.0, etc.)
2. **Error Handling**: Provide clear error messages
3. **Documentation**: Always include a README.md
4. **Testing**: Test the source before distributing
5. **Updates**: Maintain compatibility with previous versions

## Examples

See `sources/crocdb/` for a complete working API source example.
See `sources/vimms/` for a complete working Python source example.

## Java/Kotlin Sources

Java/Kotlin sources allow you to execute custom code directly in the app. This is useful for:
- Web scraping
- Local file processing
- Integration with local databases
- HTML/XML file parsing
- Any custom logic that doesn't require external APIs

### Requirements

1. **Main class**: Must have the required public methods (see `SourceExecutor` interface)
2. **Constructor**: Can have:
   - No-argument constructor
   - Constructor that accepts `(SourceMetadata, File)` - receives metadata and source directory
3. **Dependencies**: JAR dependencies must be included in the `libs/` folder or in the ZIP root
4. **Package**: The class must be in the package specified in `mainClass`

### JAR Structure

The JAR must contain:
- The main class and all necessary classes
- Classes must be compiled for Java 17 (compatible with Android)
- External dependencies must be included as separate JARs in the `libs/` folder

### Required Methods

The main class must implement these public methods:

```java
// Search ROMs in the source
public SearchResults searchRoms(
    String searchKey,        // Search term (can be null)
    List<String> platforms,  // Platform code list (can be empty)
    List<String> regions,    // Region code list (can be empty)
    int maxResults,          // Maximum number of results
    int page                 // Page number (1-based)
)

// Get ROM details
public EntryResponse getEntry(String slug)

// Get supported platforms
public Map<String, Object> getPlatforms()

// Get supported regions
public RegionsResponse getRegions()
```

### Complete Java Class Example

```java
package com.example;

import com.tottodrillo.data.model.*;
import com.tottodrillo.domain.model.SourceMetadata;
import java.io.File;
import java.util.*;

public class MyJavaSource {
    private SourceMetadata metadata;
    private File sourceDir;
    
    // Optional constructor - receives metadata and source directory
    public MyJavaSource(SourceMetadata metadata, File sourceDir) {
        this.metadata = metadata;
        this.sourceDir = sourceDir;
        // Initialize here if necessary
    }
    
    // Required method: search ROMs
    public SearchResults searchRoms(
        String searchKey,
        List<String> platforms,
        List<String> regions,
        int maxResults,
        int page
    ) {
        SearchResults results = new SearchResults();
        List<RomEntry> roms = new ArrayList<>();
        
        // Example: implement your search logic
        // You can do scraping, database queries, file parsing, etc.
        
        // Example ROM entry
        RomEntry entry = new RomEntry();
        entry.slug = "example-rom";
        entry.title = "Example ROM";
        entry.platform = platforms.isEmpty() ? "nes" : platforms.get(0);
        entry.boxImage = "https://example.com/boxart.png";  // Box art (required)
        entry.screenImage = "https://example.com/screen.png"; // Screen shot (optional)
        // For backward compatibility, you can also use:
        // entry.boxartUrl = "https://example.com/boxart.png"; // DEPRECATED
        entry.regions = regions.isEmpty() ? Arrays.asList("US") : regions;
        
        // Add download link
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
    
    // Required method: get entry by slug
    public EntryResponse getEntry(String slug) {
        EntryResponse response = new EntryResponse();
        RomEntry entry = new RomEntry();
        
        // Implement logic to retrieve ROM details
        entry.slug = slug;
        entry.title = "Example ROM";
        entry.platform = "nes";
        entry.boxImage = "https://example.com/boxart.png";  // Box art (required)
        entry.screenImage = "https://example.com/screen.png"; // Screen shot (optional)
        // For backward compatibility, you can also use:
        // entry.boxartUrl = "https://example.com/boxart.png"; // DEPRECATED
        entry.regions = Arrays.asList("US", "EU");
        
        // Add download link
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
    
    // Required method: get platforms
    public Map<String, Object> getPlatforms() {
        Map<String, Object> platforms = new HashMap<>();
        
        // Example: return supported platforms
        Map<String, String> nes = new HashMap<>();
        nes.put("name", "Nintendo Entertainment System");
        platforms.put("nes", nes);
        
        return platforms;
    }
    
    // Required method: get regions
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

**Note**: The classes `RomEntry`, `SearchResults`, `EntryResponse`, `RegionsResponse`, `DownloadLink` must be included in the JAR or available as dependencies. You can copy the definitions from the Tottodrillo project or create your own compatible versions.

### Compilation and Packaging

1. **Compile the code**: Compile your Java/Kotlin code into a JAR
   ```bash
   javac -d classes com/example/MyJavaSource.java
   jar cvf mysource.jar -C classes .
   ```

2. **Include dependencies**: Put all JAR dependencies in the `libs/` folder
   ```
   my-source/
   ├── source.json
   ├── libs/
   │   ├── gson.jar
   │   └── jsoup.jar  # Example for web scraping
   └── mysource.jar   # Your main JAR
   ```

3. **Create source.json**: Create the `source.json` file with `type: "java"` and `mainClass`
   ```json
   {
     "id": "mysource",
     "name": "My Source",
     "version": "1.0.0",
     "type": "java",
     "mainClass": "com.example.MyJavaSource"
   }
   ```

4. **Create the ZIP package**: Compress everything into a ZIP file
   ```bash
   zip -r my-source.zip source.json libs/ mysource.jar
   ```

### Limitations and Considerations

- **Security**: Java code runs with app permissions. Ensure the code is secure.
- **Performance**: Dynamic class loading can be slower than APIs.
- **Dependencies**: All dependencies must be included in the ZIP package.
- **Compatibility**: Code must be compatible with Java 17 and Android API 26+.

## Python Sources

Python sources allow you to execute Python scripts directly in the app using Chaquopy. This is useful for:
- Web scraping with Python libraries (BeautifulSoup, Scrapy, etc.)
- Data processing with pandas, numpy
- Machine learning and data analysis
- Any logic that benefits from Python libraries

### Requirements

1. **Chaquopy configured**: The app must have Chaquopy configured (already included in Tottodrillo)
2. **Execute function**: The Python script must expose an `execute(params: str) -> str` function that accepts JSON and returns JSON
3. **Dependencies**: Python dependencies can be specified in `requirements.txt` or installed dynamically

### Script Structure

The Python script must:
- Be in the ZIP package root
- Have the name specified in `pythonScript` in `source.json`
- Expose the `execute(params_json: str) -> str` function
- Return valid JSON for each method

### Supported Methods

The `execute` function receives a JSON with a `method` field indicating which operation to perform:

- `"searchRoms"`: Search ROMs
- `"getEntry"`: Get ROM details
- `"getPlatforms"`: Get supported platforms
- `"getRegions"`: Get supported regions

### Complete Python Script Example

```python
import json
import sys
import os
from typing import Dict, Any, List, Optional

def execute(params_json: str) -> str:
    """
    Main function called by Tottodrillo
    Accepts JSON as string and returns JSON as string
    
    Args:
        params_json: JSON string with request parameters
        
    Returns:
        JSON string with the response
    """
    try:
        params = json.loads(params_json)
        method = params.get("method")
        source_dir = params.get("source_dir", "")
        
        if method == "searchRoms":
            return search_roms(params, source_dir)
        elif method == "getEntry":
            return get_entry(params, source_dir)
        elif method == "getPlatforms":
            return get_platforms(source_dir)
        elif method == "getRegions":
            return get_regions()
        else:
            return json.dumps({"error": f"Unknown method: {method}"})
    except Exception as e:
        return json.dumps({"error": str(e)})

def search_roms(params: Dict[str, Any], source_dir: str) -> str:
    """
    Search ROMs in the source
    
    Args:
        params: Dictionary with:
            - search_key: str (optional)
            - platforms: List[str]
            - regions: List[str]
            - max_results: int
            - page: int
        source_dir: Path to source directory
            
    Returns:
        JSON string with SearchResults
    """
    search_key = params.get("search_key")
    platforms = params.get("platforms", [])
    regions = params.get("regions", [])
    max_results = params.get("max_results", 50)
    page = params.get("page", 1)
    
    # Implement your logic here
    # Example: web scraping, database queries, etc.
    
    results = []
    
    # Example ROM entry
    rom_entry = {
        "slug": "example-rom",
        "title": "Example ROM",
        "platform": platforms[0] if platforms else "nes",
        "box_image": "https://example.com/boxart.png",  # Box art (required, None if not available)
        "screen_image": "https://example.com/screen.png",  # Screen shot (optional, None if not available)
        # For backward compatibility, you can also use:
        # "boxart_url": "https://example.com/boxart.png",  # DEPRECATED
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

def get_entry(params: Dict[str, Any], source_dir: str) -> str:
    """
    Get a specific entry by slug
    
    Args:
        params: Dictionary with:
            - slug: str
        source_dir: Path to source directory
            
    Returns:
        JSON string with EntryResponse
    """
    slug = params.get("slug")
    
    # Implement logic to retrieve ROM details
    entry = {
        "slug": slug,
        "title": "Example ROM",
        "platform": "nes",
        "box_image": "https://example.com/boxart.png",  # Box art (required, None if not available)
        "screen_image": "https://example.com/screen.png",  # Screen shot (optional, None if not available)
        # For backward compatibility, you can also use:
        # "boxart_url": "https://example.com/boxart.png",  # DEPRECATED
        # "boxart_urls": ["https://example.com/boxart.png", "https://example.com/screen.png"],  # DEPRECATED
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

def get_platforms(source_dir: str) -> str:
    """
    Get available platforms
    
    Args:
        source_dir: Path to source directory
    
    Returns:
        JSON string with platforms
    """
    # Load platform mapping from platform_mapping.json
    import json as json_module
    mapping_path = os.path.join(source_dir, "platform_mapping.json")
    with open(mapping_path, 'r') as f:
        mapping_data = json_module.load(f)
        mapping = mapping_data.get("mapping", {})
    
    # Return platforms as dictionary with mother_code as keys
    platforms = {}
    for mother_code in mapping.keys():
        platforms[mother_code] = {"name": mother_code.upper()}
    
    return json.dumps(platforms)

def get_regions() -> str:
    """
    Get available regions
    
    Returns:
        JSON string with RegionsResponse
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

### Example with Web Scraping

```python
import json
import requests
from bs4 import BeautifulSoup

def execute(params_json: str) -> str:
    params = json.loads(params_json)
    method = params.get("method")
    
    if method == "searchRoms":
        return search_roms(params)
    # ... other methods

def search_roms(params: Dict[str, Any]) -> str:
    search_key = params.get("search_key", "")
    
    # Example web scraping
    url = f"https://example.com/search?q={search_key}"
    response = requests.get(url)
    soup = BeautifulSoup(response.content, 'html.parser')
    
    # HTML parsing and data extraction
    results = []
    for item in soup.find_all('div', class_='rom-item'):
        # Extract box art and screen shot separately
        box_img = item.find('img', class_='boxart')
        screen_img = item.find('img', class_='screen')
        
        rom = {
            "slug": item.get('data-slug'),
            "title": item.find('h2').text,
            "platform": item.get('data-platform'),
            "box_image": box_img.get('src') if box_img else None,  # Box art (required)
            "screen_image": screen_img.get('src') if screen_img else None,  # Screen shot (optional)
            # For backward compatibility, you can also use:
            # "boxart_url": box_img.get('src') if box_img else None,  # DEPRECATED
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

**Note**: To use libraries like `requests` or `beautifulsoup4`, you must include them in `requirements.txt` or install them via Chaquopy.

### Chaquopy Configuration

Chaquopy is already configured in Tottodrillo. The configuration includes:

- **Python Version**: 3.11
- **Supported ABIs**: armeabi-v7a, arm64-v8a, x86, x86_64
- **Dependencies**: Can be installed dynamically from sources

### Python Dependency Management

Python dependencies can be managed in two ways:

1. **In the app's build.gradle.kts** (for common dependencies):
```kotlin
chaquopy {
    defaultConfig {
        python {
            version = "3.11"
        }
        pip {
            install("requests")
            install("beautifulsoup4")
        }
    }
}
```

2. **In sources** (for specific dependencies):
   - Create a `requirements.txt` file in the source
   - Dependencies will be installed automatically when the source is loaded

**Example requirements.txt:**
```
requests>=2.31.0
beautifulsoup4>=4.12.0
lxml>=4.9.0
```

### Packaging a Python Source

1. **Create the Python script**: Create your main script (e.g., `main.py`)
2. **Add dependencies** (optional): Create `requirements.txt` if necessary
3. **Create source.json**:
```json
{
  "id": "pythonsource",
  "name": "Python Source",
  "version": "1.0.0",
  "type": "python",
  "pythonScript": "main.py",
  "dependencies": ["requirements.txt"]
}
```

4. **Create the ZIP package**:
```bash
zip -r python-source.zip source.json main.py requirements.txt platform_mapping.json
```

### Limitations and Considerations

- **APK Size**: Chaquopy significantly increases APK size (~50-100MB)
- **Performance**: Python execution can be slower than Java/Kotlin
- **Dependencies**: Some Python libraries may not be compatible with Android
- **Debugging**: Debugging Python code can be more complex
- **Security**: Python code runs with app permissions

## Summary: Which Source Type to Choose?

### API Source
**Use when:**
- You have access to an HTTP REST API
- You want the simplest and fastest solution
- You don't need complex client-side logic

**Advantages:**
- Simple setup (JSON only)
- Optimal performance
- No code to write

**Disadvantages:**
- Requires an existing API
- Depends on server availability

### Java/Kotlin Source
**Use when:**
- You need to do web scraping
- You need to process local files
- You want to integrate with local databases
- You prefer Java/Kotlin over Python

**Advantages:**
- Native performance
- Full access to Android APIs
- No external dependencies (beyond JARs)

**Disadvantages:**
- Requires JAR compilation
- More complex to develop
- Manual dependency management

### Python Source
**Use when:**
- You need powerful Python libraries (pandas, numpy, etc.)
- You want advanced scraping (BeautifulSoup, Scrapy)
- You already have existing Python code
- You prefer Python for complex logic

**Advantages:**
- Access to a vast ecosystem of libraries
- Rapid development for complex logic
- Ideal for data processing

**Disadvantages:**
- Significantly increases APK size
- Potentially slower performance
- Some libraries may not be compatible

## Checklist for Creating a Source

- [ ] Decide the source type (API, Java, Python)
- [ ] Create the `source.json` file with all required fields
- [ ] For API sources: create `api_config.json`
- [ ] For Java sources: compile code into JAR and include dependencies
- [ ] For Python sources: create Python script and `requirements.txt` (if necessary)
- [ ] Create `platform_mapping.json` with platform mappings
- [ ] Test the source locally
- [ ] Validate the ZIP package with `SourceInstaller.validateZip()`
- [ ] Create a README.md with instructions
- [ ] Create the final ZIP package
- [ ] Test installation in the app

## Support

For questions or issues:
- Open an issue on GitHub
- Consult the API documentation you're integrating
- Check examples in the `sources/` folder
- Check app logs for execution errors

