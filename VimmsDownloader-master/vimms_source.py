"""
Wrapper Python per integrare Vimm's Lair come sorgente Tottodrillo
Implementa l'interfaccia SourceExecutor
"""
import json
import re
import sys
from typing import Dict, Any, List, Optional
import requests
from bs4 import BeautifulSoup
import urllib3

# Disabilita warning SSL per test (in produzione dovresti usare certificati validi)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Mapping sistemi Vimm's Lair -> mother_code Tottodrillo
SYSTEM_MAPPING = {
    'NES': 'nes',
    'Genesis': 'genesis',
    'SNES': 'snes',
    'Saturn': 'saturn',
    'PS1': 'psx',
    'Playstation': 'psx',
    'N64': 'n64',
    'Dreamcast': 'dreamcast',
    'PS2': 'ps2',
    'Playstation-2': 'ps2',
    'Xbox': 'xbox',
    'Gamecube': 'gamecube',
    'PS3': 'ps3',
    'Playstation-3': 'ps3',
    'Wii': 'wii',
    'WiiWare': 'wiiware',
    'GB': 'gb',
    'Game-Boy': 'gb',
    'GBC': 'gbc',
    'Game-Boy-Color': 'gbc',
    'GBA': 'gba',
    'Game-Boy-Advanced': 'gba',
    'DS': 'nds',
    'Nintendo-DS': 'nds',
    'PSP': 'psp',
}

# Mapping URI Vimm's Lair -> nome sistema
URI_TO_SYSTEM = {
    'NES': 'NES',
    'Genesis': 'Genesis',
    'SNES': 'SNES',
    'Saturn': 'Saturn',
    'PS1': 'PS1',
    'N64': 'N64',
    'Dreamcast': 'Dreamcast',
    'PS2': 'PS2',
    'Xbox': 'Xbox',
    'GameCube': 'Gamecube',
    'PS3': 'PS3',
    'Wii': 'Wii',
    'WiiWare': 'WiiWare',
    'GB': 'Game-Boy',
    'GBC': 'Game-Boy-Color',
    'GBA': 'Game-Boy-Advanced',
    'DS': 'Nintendo-DS',
    'PSP': 'PSP',
}

USER_AGENTS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36',
]


def get_random_ua() -> str:
    """Restituisce un user agent casuale"""
    import random
    return random.choice(USER_AGENTS)


def get_rom_download_url(page_url: str) -> Optional[str]:
    """Ottiene l'URL di download per una ROM dalla pagina ROM"""
    try:
        headers = {'User-Agent': get_random_ua()}
        page = requests.get('https://vimm.net/' + page_url, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        # Il form ha ID 'dl_form'
        result = soup.find(id='dl_form')
        if not result:
            # Prova a cercare qualsiasi form con mediaId
            forms = soup.find_all('form')
            for form in forms:
                media_id_elem = form.find(attrs={'name': 'mediaId'})
                if media_id_elem:
                    result = form
                    break
        
        if result:
            media_id_elem = result.find(attrs={'name': 'mediaId'})
            if media_id_elem and media_id_elem.get('value'):
                media_id = media_id_elem['value']
                # Il form usa POST a //dl2.vimm.net/, ma possiamo usare GET con mediaId come query param
                # Costruisce l'URL di download diretto (usa dl2.vimm.net come nel form)
                return f'https://dl2.vimm.net/?mediaId={media_id}'
    except Exception as e:
        print(f"Errore nel recupero URL download: {e}", file=sys.stderr)
    return None


def get_rom_slug_from_uri(uri: str) -> str:
    """Converte l'URI di Vimm's Lair in uno slug per Tottodrillo"""
    # Rimuove il prefisso /vault/ se presente
    slug = uri.replace('/vault/', '').strip('/')
    # Sostituisce / con - per creare uno slug
    slug = slug.replace('/', '-')
    # Rimuove caratteri speciali problematici ma mantiene alcuni
    slug = re.sub(r'[^a-zA-Z0-9\-_]', '', slug)
    return slug.lower()


def get_boxart_url_from_uri(uri: str) -> Optional[str]:
    """Costruisce l'URL dell'immagine box art dall'URI della ROM"""
    # L'URI è formato come /vault/48075, estraiamo l'ID
    match = re.search(r'/vault/(\d+)', uri)
    if match:
        rom_id = match.group(1)
        return f'https://dl.vimm.net/image.php?type=box&id={rom_id}'
    return None


def get_uri_from_slug(slug: str) -> Optional[str]:
    """Tenta di ricostruire l'URI dallo slug"""
    # Lo slug è formato come: sistema-nome-rom
    # Possiamo provare a ricostruire l'URI, ma è meglio salvare l'URI originale
    # Per ora, restituiamo None - l'URI deve essere salvato nella ricerca
    return None


def map_system_to_mother_code(system: str) -> str:
    """Mappa il sistema Vimm's Lair al mother_code di Tottodrillo"""
    return SYSTEM_MAPPING.get(system, system.lower())


def get_system_search_roms(search_key: str, system: str) -> List[Dict[str, Any]]:
    """Cerca ROM per sistema specifico"""
    roms = []
    try:
        # Mappa il sistema al codice URI di Vimm's Lair
        system_uri = URI_TO_SYSTEM.get(system, system)
        url = f'https://vimm.net/vault/?p=list&system={system_uri}&q={search_key}'
        
        headers = {'User-Agent': get_random_ua()}
        page = requests.get(url, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        # La tabella può avere anche la classe 'striped'
        result = soup.find('table', class_=lambda x: x and 'rounded' in x and 'centered' in x and 'cellpadding1' in x and 'hovertable' in x)
        
        if not result:
            return roms
        
        # Le righe sono direttamente <tr> con <td> che contengono i link
        rows = result.find_all('tr')
        for row in rows:
            # Salta l'header se presente
            if row.find('th'):
                continue
            
            # Il primo <td> contiene il link alla ROM
            first_td = row.find('td')
            if first_td:
                link = first_td.find('a', href=True)
                if link:
                    name = link.get_text(strip=True)
                    uri = link['href']
                    # Assicurati che l'URI sia completo
                    if not uri.startswith('/'):
                        uri = '/' + uri
                    if not uri.startswith('/vault/'):
                        uri = '/vault/' + uri.lstrip('/')
                    slug = get_rom_slug_from_uri(uri)
                    boxart_url = get_boxart_url_from_uri(uri)
                    
                    rom = {
                        'slug': slug,
                        'rom_id': uri,  # Salviamo l'URI come rom_id per poterlo recuperare
                        'title': name,
                        'platform': map_system_to_mother_code(system),
                        'boxart_url': boxart_url,
                        'regions': [],
                        'links': []
                    }
                    roms.append(rom)
    except Exception as e:
        print(f"Errore nella ricerca sistema: {e}", file=sys.stderr)
    
    return roms


def get_general_search_roms(search_key: str) -> List[Dict[str, Any]]:
    """Cerca ROM in generale su tutto il sito"""
    roms = []
    try:
        url = f'https://vimm.net/vault/?p=list&q={search_key}'
        
        headers = {'User-Agent': get_random_ua()}
        page = requests.get(url, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        # La tabella può avere anche la classe 'striped'
        result = soup.find('table', class_=lambda x: x and 'rounded' in x and 'centered' in x and 'cellpadding1' in x and 'hovertable' in x)
        
        if not result:
            return roms
        
        # Le righe sono direttamente <tr> con <td> che contengono i link
        rows = result.find_all('tr')
        for row in rows:
            # Salta l'header se presente
            if row.find('th'):
                continue
            
            # Per ricerca generale, la prima colonna è il sistema, la seconda il nome
            tds = row.find_all('td')
            if len(tds) >= 2:
                system = tds[0].get_text(strip=True)
                link = tds[1].find('a', href=True)
                
                if link:
                    name = link.get_text(strip=True)
                    uri = link['href']
                    # Assicurati che l'URI sia completo
                    if not uri.startswith('/'):
                        uri = '/' + uri
                    if not uri.startswith('/vault/'):
                        uri = '/vault/' + uri.lstrip('/')
                    slug = get_rom_slug_from_uri(uri)
                    boxart_url = get_boxart_url_from_uri(uri)
                    
                    rom = {
                        'slug': slug,
                        'rom_id': uri,  # Salviamo l'URI come rom_id per poterlo recuperare
                        'title': name,
                        'platform': map_system_to_mother_code(system),
                        'boxart_url': boxart_url,
                        'regions': [],
                        'links': []
                    }
                    roms.append(rom)
    except Exception as e:
        print(f"Errore nella ricerca generale: {e}", file=sys.stderr)
    
    return roms


def get_rom_entry_by_uri(uri: str) -> Optional[Dict[str, Any]]:
    """Ottiene i dettagli completi di una ROM dall'URI"""
    try:
        # Estrai informazioni dalla pagina ROM per ottenere nome e sistema
        headers = {'User-Agent': get_random_ua()}
        page = requests.get('https://vimm.net/' + uri, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        
        # Cerca il titolo della ROM
        title = "ROM"
        title_elem = soup.find('h1') or soup.find('title')
        if title_elem:
            title = title_elem.get_text().strip()
        
        # Cerca il sistema
        system = None
        system_elem = soup.find(text=re.compile('System|Platform'))
        if system_elem:
            parent = system_elem.parent
            if parent:
                system_text = parent.get_text()
                for vimm_system in SYSTEM_MAPPING.keys():
                    if vimm_system in system_text:
                        system = vimm_system
                        break
        
        # Estrai l'ID della ROM dall'URI per costruire gli URL delle immagini
        rom_id = None
        match = re.search(r'/vault/(\d+)', uri)
        if match:
            rom_id = match.group(1)
        
        # Cerca le immagini (box art e screen)
        boxart_url = None
        screen_url = None
        
        # Cerca l'immagine della box art
        boxart_img = soup.find('img', alt='Box')
        if boxart_img:
            src = boxart_img.get('src', '')
            if src:
                # Normalizza l'URL (rimuovi // iniziale e aggiungi https://)
                if src.startswith('//'):
                    boxart_url = 'https:' + src
                elif src.startswith('/'):
                    boxart_url = 'https://vimm.net' + src
                elif src.startswith('http'):
                    boxart_url = src
                else:
                    boxart_url = 'https://vimm.net/' + src
        
        # Se non trovata, cerca per pattern comune
        if not boxart_url:
            all_imgs = soup.find_all('img')
            for img in all_imgs:
                src = img.get('src', '')
                if src and 'image.php?type=box' in src:
                    if src.startswith('//'):
                        boxart_url = 'https:' + src
                    elif src.startswith('/'):
                        boxart_url = 'https://vimm.net' + src
                    elif src.startswith('http'):
                        boxart_url = src
                    break
        
        # Se ancora non trovata, costruisci dall'URI
        if not boxart_url and rom_id:
            boxart_url = f'https://dl.vimm.net/image.php?type=box&id={rom_id}'
        
        # Costruisci l'URL dell'immagine screen (se abbiamo l'ID)
        if rom_id:
            screen_url = f'https://dl.vimm.net/image.php?type=screen&id={rom_id}'
        
        # Crea lista di immagini per il carosello (box art prima, poi screen)
        cover_urls = []
        if boxart_url:
            cover_urls.append(boxart_url)
        if screen_url:
            cover_urls.append(screen_url)
        
        # Estrai array media dal JavaScript per ottenere tutte le versioni
        media_array = []
        scripts = soup.find_all('script')
        for script in scripts:
            if script.string and 'const media=' in script.string:
                match = re.search(r'const media=(\[.*?\]);', script.string, re.DOTALL)
                if match:
                    try:
                        media_array = json.loads(match.group(1))
                        break
                    except:
                        pass
        
        # Estrai opzioni di format dal select (può essere fuori dal form)
        format_options = []
        format_select = soup.find(id='dl_format')
        if format_select:
            for option in format_select.find_all('option'):
                format_value = option.get('value', '')
                format_text = option.get_text(strip=True)
                # Usa il title se disponibile, altrimenti il testo
                format_title = option.get('title', '')
                if format_title:
                    # Estrai l'estensione dal title (es. ".wbfs files work..." -> ".wbfs")
                    match = re.search(r'\.(\w+)', format_title)
                    if match:
                        ext = match.group(1)
                        format_text = f".{ext}"
                format_options.append({
                    'value': format_value,
                    'text': format_text
                })
        
        # Se non ci sono opzioni di format, usa default (0 = Zipped, 1 = AltZipped, 2 = AltZipped2)
        if not format_options:
            format_options = [
                {'value': '0', 'text': 'Default'},
                {'value': '1', 'text': 'Alt'},
                {'value': '2', 'text': 'Alt2'}
            ]
        
        # Genera link per ogni combinazione di version (media) e format
        links = []
        for media_item in media_array:
            media_id = media_item.get('ID')
            version = media_item.get('Version', '')
            version_string = media_item.get('VersionString', version)
            
            # Per ogni formato disponibile
            for format_option in format_options:
                format_value = format_option.get('value', '0')
                format_text = format_option.get('text', 'Default')
                
                # Determina la dimensione in base al formato
                size_str = None
                size_bytes = 0
                if format_value == '0':
                    size_str = media_item.get('ZippedText', '')
                    size_bytes = int(media_item.get('Zipped', '0') or '0')
                elif format_value == '1':
                    size_str = media_item.get('AltZippedText', '')
                    size_bytes = int(media_item.get('AltZipped', '0') or '0')
                elif format_value == '2':
                    size_str = media_item.get('AltZipped2Text', '')
                    size_bytes = int(media_item.get('AltZipped2', '0') or '0')
                
                # Salta i formati non disponibili (dimensione 0)
                if size_bytes == 0 or size_str in ['0 KB', '0 MB', '0 GB']:
                    continue
                
                # Costruisci l'URL di download
                # Il formato viene passato come parametro 'alt' (0, 1, o 2)
                download_url = f'https://dl2.vimm.net/?mediaId={media_id}'
                if format_value != '0':
                    download_url += f'&alt={format_value}'
                
                # Determina il tipo di formato dal nome
                format_type = "zip"  # Default
                format_display = format_text
                if format_text:
                    format_lower = format_text.lower()
                    if '.wbfs' in format_lower or 'wbfs' in format_lower:
                        format_type = "wbfs"
                        format_display = ".wbfs"
                    elif '.rvz' in format_lower or 'rvz' in format_lower:
                        format_type = "rvz"
                        format_display = ".rvz"
                    elif '.7z' in format_lower or '7z' in format_lower:
                        format_type = "7z"
                        format_display = ".7z"
                    elif '.iso' in format_lower or 'iso' in format_lower:
                        format_type = "iso"
                        format_display = ".iso"
                    elif '.zip' in format_lower or 'zip' in format_lower:
                        format_type = "zip"
                        format_display = ".zip"
                    else:
                        # Se non riconosciuto, usa il testo originale
                        format_display = format_text
                else:
                    # Se non c'è testo, determina dal value
                    if format_value == '1':
                        format_display = "Alt Format"
                    elif format_value == '2':
                        format_display = "Alt2 Format"
                
                # Nome del link: Version - Format (es. "1.1 - .wbfs")
                link_name = f"Version {version_string}"
                if format_display and format_display not in ['Default', 'Alt Format', 'Alt2 Format']:
                    link_name += f" - {format_display}"
                elif format_display:
                    link_name += f" - {format_display}"
                
                if size_str:
                    link_name += f" ({size_str})"
                
                links.append({
                    'name': link_name,
                    'type': 'direct',
                    'format': format_type,
                    'url': download_url,
                    'size_str': size_str
                })
        
        # Se non ci sono link generati (nessun media array), usa il metodo vecchio
        if not links:
            download_url = get_rom_download_url(uri)
            if download_url:
                format_type = "zip"  # Default
                links.append({
                    'name': 'Download',
                    'type': 'direct',
                    'format': format_type,
                    'url': download_url,
                    'size_str': None
                })
        
        slug = get_rom_slug_from_uri(uri)
        
        entry = {
            'slug': slug,
            'rom_id': uri,
            'title': title,
            'platform': map_system_to_mother_code(system) if system else 'unknown',
            'boxart_url': boxart_url,  # Mantieni per compatibilità
            'boxart_urls': cover_urls,  # Lista di tutte le immagini per il carosello
            'regions': [],
            'links': links
        }
        
        return entry
    except Exception as e:
        print(f"Errore nel recupero entry: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return None


def execute(params_json: str) -> str:
    """
    Funzione principale chiamata da Tottodrillo
    Accetta JSON come stringa e ritorna JSON come stringa
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
    """Cerca ROM nella sorgente"""
    search_key = params.get("search_key", "").strip()
    platforms = params.get("platforms", [])
    max_results = params.get("max_results", 50)
    page = params.get("page", 1)
    
    if not search_key:
        return json.dumps({
            "results": [],
            "total_results": 0,
            "current_page": page,
            "total_pages": 1
        })
    
    all_roms = []
    
    # Se ci sono piattaforme specificate, cerca per ogni piattaforma
    if platforms:
        for platform in platforms:
            # Mappa il mother_code alla piattaforma Vimm's Lair
            # Cerca il sistema corrispondente
            system = None
            for vimm_system, mother_code in SYSTEM_MAPPING.items():
                if mother_code == platform:
                    system = vimm_system
                    break
            
            if system:
                roms = get_system_search_roms(search_key, system)
                all_roms.extend(roms)
    else:
        # Ricerca generale
        all_roms = get_general_search_roms(search_key)
    
    # Limita i risultati
    total_results = len(all_roms)
    start_idx = (page - 1) * max_results
    end_idx = start_idx + max_results
    paginated_roms = all_roms[start_idx:end_idx]
    
    response = {
        "results": paginated_roms,
        "total_results": total_results,
        "current_results": len(paginated_roms),
        "current_page": page,
        "total_pages": (total_results + max_results - 1) // max_results if total_results > 0 else 1
    }
    
    return json.dumps(response)


def get_entry(params: Dict[str, Any]) -> str:
    """Ottiene una entry specifica per slug"""
    slug = params.get("slug")
    
    if not slug:
        return json.dumps({"error": "Slug non fornito"})
    
    # Lo slug può essere l'ID numerico della ROM (es. "48075")
    # oppure un URI convertito (es. "vault-48075")
    # Proviamo prima a vedere se lo slug è un numero (ID diretto)
    uri = None
    
    if slug.isdigit():
        # Lo slug è un ID numerico, costruiamo l'URI
        uri = f"/vault/{slug}"
    elif slug.startswith("/vault/"):
        # Lo slug è già un URI
        uri = slug
    elif slug.startswith("vault-"):
        # Lo slug è formato come "vault-48075", estraiamo l'ID
        id_part = slug.replace("vault-", "")
        if id_part.isdigit():
            uri = f"/vault/{id_part}"
    
    # Se abbiamo un URI, usiamolo direttamente
    if uri:
        entry = get_rom_entry_by_uri(uri)
        if entry:
            # Assicuriamoci che lo slug corrisponda
            entry['slug'] = slug
            return json.dumps({"entry": entry})
    
    # Se non abbiamo un URI diretto, proviamo a cercare
    # Estrai il nome dalla slug (rimuovi il prefisso sistema-)
    name_parts = slug.split('-')
    if len(name_parts) > 1:
        # Prova a cercare usando le ultime parti come nome
        search_name = ' '.join(name_parts[-3:])  # Ultime 3 parti
        roms = get_general_search_roms(search_name)
        
        # Cerca la ROM con slug corrispondente
        for rom in roms:
            if rom['slug'] == slug and rom.get('rom_id'):
                # Trovata! Ora ottieni i dettagli completi
                uri = rom['rom_id']
                entry = get_rom_entry_by_uri(uri)
                if entry:
                    return json.dumps({"entry": entry})
    
    # Se non trovata, restituisci entry null per coerenza con l'API
    return json.dumps({
        "entry": None
    })


def get_platforms() -> str:
    """Ottiene le piattaforme disponibili"""
    platforms = {}
    
    # Mappa i sistemi Vimm's Lair alle piattaforme Tottodrillo
    for vimm_system, mother_code in SYSTEM_MAPPING.items():
        platforms[mother_code] = {
            "name": vimm_system
        }
    
    return json.dumps(platforms)


def get_regions() -> str:
    """Ottiene le regioni disponibili"""
    # Vimm's Lair non ha informazioni sulle regioni nelle ricerche
    # Restituiamo regioni comuni
    regions = {
        "US": "United States",
        "EU": "Europe",
        "JP": "Japan",
        "WW": "Worldwide"
    }
    
    response = {
        "regions": regions
    }
    
    return json.dumps(response)

