# Tottodrillo v2.0 - Release Notes

## 🇮🇹 Italiano

### Novità Principali

#### 🖼️ Gestione Immagini Migliorata
- **Separazione Box Art e Screen Shot**: Le sorgenti ora restituiscono `box_image` (obbligatoria) e `screen_image` (facoltativa) separatamente
- **Placeholder Intelligente**: 
  - Se una ROM non ha box art, viene mostrato automaticamente il placeholder della sorgente
  - Se il placeholder della sorgente non è disponibile, viene usato il logo dell'app come ultima spiaggia
  - Il placeholder viene mostrato anche quando il caricamento dell'immagine fallisce
- **Rilevamento Errori Screen**: Le immagini screen di errore (es. "Error: image not found") vengono automaticamente rilevate e ignorate

#### 🔍 Miglioramenti Ricerca e Filtri
- **Filtro Regioni**: Aggiunto filtro per regioni nel pannello filtri
- **Contatore Filtri Attivi**: Mostra il numero totale di filtri attivi (es. "2 filtri attivi") invece di contare singolarmente
- **Indicatore Caricamento**: La ruota di caricamento viene mostrata quando si applica un nuovo filtro
- **Indicatore "ROMs+"**: Se ci sono più ROM disponibili oltre quelle mostrate, viene visualizzato "ROMs+" (es. "50 ROMs+")
- **Ordinamento Alfabetico**: I risultati di ricerca sono ordinati alfabeticamente per nome, indipendentemente dalla sorgente

#### 🎯 Miglioramenti UI/UX
- **Pulsante "Installa Sorgenti Predefinite"**: Disponibile anche dopo il primo avvio se non ci sono sorgenti disponibili
- **Gestione Errori Immagini**: Migliorata la gestione degli errori di caricamento immagini con fallback automatico al placeholder

#### 📦 Sorgente Vimm's Lair
- **Piattaforme Supportate**: Aumentate da 18 a 35 piattaforme
- **Rilevamento Immagini**: Migliorato il rilevamento delle immagini box art e screen shot
- **Gestione Placeholder**: Corretto il comportamento del placeholder per ROM senza immagini

### Miglioramenti Tecnici

- **Modello Dati**: Aggiunto supporto per `box_image` e `screen_image` separati nel modello API
- **Retrocompatibilità**: Mantenuto supporto per il vecchio formato `boxart_url` e `boxart_urls` (deprecato)
- **Documentazione**: Aggiornata la documentazione di sviluppo sorgenti con il nuovo formato immagini

### Note per Sviluppatori

- Le sorgenti devono ora restituire `box_image` (obbligatoria) e `screen_image` (facoltativa) invece di `boxart_urls`
- Il formato vecchio è ancora supportato per retrocompatibilità ma è deprecato
- Vedi `SOURCE_DEVELOPMENT.md` per i dettagli sul nuovo formato

---

## 🇬🇧 English

### Main Features

#### 🖼️ Improved Image Management
- **Box Art and Screen Shot Separation**: Sources now return `box_image` (required) and `screen_image` (optional) separately
- **Smart Placeholder**: 
  - If a ROM doesn't have box art, the source's placeholder is automatically shown
  - If the source's placeholder is not available, the app logo is used as a last resort
  - The placeholder is also shown when image loading fails
- **Error Screen Detection**: Error screen images (e.g. "Error: image not found") are automatically detected and ignored

#### 🔍 Search and Filter Improvements
- **Region Filter**: Added region filter in the filter panel
- **Active Filters Counter**: Shows the total number of active filters (e.g. "2 active filters") instead of counting individually
- **Loading Indicator**: Loading spinner is shown when applying a new filter
- **"ROMs+" Indicator**: If there are more ROMs available beyond those shown, "ROMs+" is displayed (e.g. "50 ROMs+")
- **Alphabetical Sorting**: Search results are sorted alphabetically by name, regardless of source

#### 🎯 UI/UX Improvements
- **"Install Default Sources" Button**: Available even after first launch if no sources are available
- **Image Error Handling**: Improved error handling for image loading with automatic fallback to placeholder

#### 📦 Vimm's Lair Source
- **Supported Platforms**: Increased from 18 to 35 platforms
- **Image Detection**: Improved detection of box art and screen shot images
- **Placeholder Management**: Fixed placeholder behavior for ROMs without images

### Technical Improvements

- **Data Model**: Added support for separate `box_image` and `screen_image` in API model
- **Backward Compatibility**: Maintained support for old format `boxart_url` and `boxart_urls` (deprecated)
- **Documentation**: Updated source development documentation with new image format

### Notes for Developers

- Sources should now return `box_image` (required) and `screen_image` (optional) instead of `boxart_urls`
- Old format is still supported for backward compatibility but is deprecated
- See `SOURCE_DEVELOPMENT.md` for details on the new format

