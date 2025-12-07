"""
Wrapper Python per integrare Vimm's Lair come sorgente Tottodrillo
Implementa l'interfaccia SourceExecutor
"""
import json
import re
import sys
import os
from typing import Dict, Any, List, Optional
import requests
from bs4 import BeautifulSoup
import urllib3

# Disabilita warning SSL per test (in produzione dovresti usare certificati validi)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Cache per il mapping delle piattaforme (caricato da platform_mapping.json)
_platform_mapping_cache: Optional[Dict[str, Any]] = None
_source_dir: Optional[str] = None

def load_platform_mapping(source_dir: str) -> Dict[str, Any]:
    """Carica platform_mapping.json dalla directory della source"""
    global _platform_mapping_cache, _source_dir
    
    # Se già caricato e stessa directory, ritorna la cache
    if _platform_mapping_cache is not None and _source_dir == source_dir:
        return _platform_mapping_cache
    
    _source_dir = source_dir
    mapping_file = os.path.join(source_dir, 'platform_mapping.json')
    
    if not os.path.exists(mapping_file):
        raise FileNotFoundError(f"platform_mapping.json non trovato in {source_dir}")
    
    with open(mapping_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
        _platform_mapping_cache = data.get('mapping', {})
    
    return _platform_mapping_cache

def map_vimm_code_to_mother_code(vimm_code: str, source_dir: str) -> str:
    """
    Mappa un codice Vimm's Lair (case-insensitive) a un mother_code Tottodrillo
    Usa platform_mapping.json dalla source directory
    """
    if not vimm_code:
        return 'unknown'
    
    # Carica il mapping
    mapping = load_platform_mapping(source_dir)
    
    # Normalizza il codice Vimm's Lair (case-insensitive)
    vimm_code_lower = vimm_code.lower().strip()
    
    # Cerca il mother_code corrispondente
    # Il mapping è: mother_code -> codice/i Vimm's Lair
    for mother_code, vimm_codes in mapping.items():
        if isinstance(vimm_codes, list):
            # Se è una lista, controlla tutti i codici
            for code in vimm_codes:
                if code.lower() == vimm_code_lower:
                    return mother_code
        else:
            # Se è una stringa singola
            if vimm_codes.lower() == vimm_code_lower:
                return mother_code
    
    # Se non trovato, ritorna il codice normalizzato
    return vimm_code_lower

def map_mother_code_to_vimm_uri(mother_code: str, source_dir: str) -> Optional[str]:
    """
    Mappa un mother_code Tottodrillo al codice URI da usare nell'URL di ricerca Vimm's Lair
    Ritorna il primo codice disponibile (case-sensitive per l'URL)
    """
    if not mother_code:
        return None
    
    # Normalizza a minuscolo per il matching (case-insensitive)
    mother_code_lower = mother_code.lower()
    
    # Carica il mapping
    mapping = load_platform_mapping(source_dir)
    
    # Cerca il mother_code (case-insensitive)
    vimm_codes = mapping.get(mother_code_lower)
    if not vimm_codes:
        return None
    
    # Se è una lista, prendi il primo
    if isinstance(vimm_codes, list):
        return vimm_codes[0] if vimm_codes else None
    
    # Se è una stringa singola
    return vimm_codes

# Mapping URI Vimm's Lair -> nome sistema per URL (per compatibilità con codice esistente)
# Questo viene usato per convertire i nomi estratti dalla pagina in codici URI
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
    'Gamecube': 'GameCube',  # Vimm's Lair usa "GameCube" nell'URL
    'GameCube': 'GameCube',  # Alias
    'PS3': 'PS3',
    'Wii': 'Wii',
    'WiiWare': 'Wii',  # Mappato a Wii per URI
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
                # Estrai il dominio dal form se disponibile, altrimenti usa dl2 come default
                # Il dominio può essere dl2 o dl3 a seconda della ROM
                download_domain = "dl2.vimm.net"  # Default
                try:
                    headers = {'User-Agent': get_random_ua()}
                    page = requests.get('https://vimm.net/' + page_url, headers=headers, timeout=10, verify=False)
                    soup = BeautifulSoup(page.content, 'html.parser')
                    form = soup.find('form', id='dl_form')
                    if form:
                        action = form.get('action', '')
                        if action.startswith('//'):
                            match = re.search(r'//(dl[23]\.vimm\.net)', action)
                            if match:
                                download_domain = match.group(1)
                except:
                    pass  # Usa default se errore
                
                return f'https://{download_domain}/?mediaId={media_id}'
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

def get_boxart_urls_from_uri(uri: str) -> list:
    """Costruisce la lista di URL delle immagini (box art e screen) dall'URI della ROM"""
    # L'URI è formato come /vault/48075, estraiamo l'ID
    match = re.search(r'/vault/(\d+)', uri)
    if match:
        rom_id = match.group(1)
        boxart_url = f'https://dl.vimm.net/image.php?type=box&id={rom_id}'
        screen_url = f'https://dl.vimm.net/image.php?type=screen&id={rom_id}'
        return [boxart_url, screen_url]
    return []


def get_uri_from_slug(slug: str) -> Optional[str]:
    """Tenta di ricostruire l'URI dallo slug"""
    # Lo slug è formato come: sistema-nome-rom
    # Possiamo provare a ricostruire l'URI, ma è meglio salvare l'URI originale
    # Per ora, restituiamo None - l'URI deve essere salvato nella ricerca
    return None


def map_system_to_mother_code(system: str, source_dir: str) -> str:
    """
    Mappa un sistema Vimm's Lair a un mother_code Tottodrillo (case-insensitive)
    Usa platform_mapping.json dalla source directory
    """
    # Normalizza a minuscolo per il matching
    return map_vimm_code_to_mother_code(system.lower(), source_dir)


def get_system_search_roms(search_key: str, system: str, page_num: int = 1, source_dir: str = None) -> List[Dict[str, Any]]:
    """
    Cerca ROM per sistema specifico con paginazione
    Vimm's Lair restituisce massimo 200 righe per pagina
    system: codice URI di Vimm's Lair (es. "N64", "SNES", "Gamecube")
    """
    roms = []
    try:
        # system è già il codice URI di Vimm's Lair (es. "N64"), non serve mapparlo
        system_uri = system
        # Usa il formato avanzato con mode=adv
        import urllib.parse
        query_params = {
            'mode': 'adv',
            'p': 'list',
            'system': system_uri,
            'q': search_key,
            'players': '>=',
            'playersValue': '1',
            'simultaneous': '',
            'publisher': '',
            'year': '=',
            'yearValue': '',
            'rating': '>=',
            'ratingValue': '',
            'region': 'All',
            'sort': 'Title',
            'sortOrder': 'ASC'
        }
        # Aggiungi numero pagina se > 1
        if page_num > 1:
            query_params['page'] = str(page_num)
        
        url = 'https://vimm.net/vault/?' + urllib.parse.urlencode(query_params)
        
        headers = {'User-Agent': get_random_ua()}
        page = requests.get(url, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        # La tabella può avere anche la classe 'striped'
        result = soup.find('table', class_=lambda x: x and 'rounded' in x and 'centered' in x and 'cellpadding1' in x and 'hovertable' in x)
        
        if not result:
            return roms
        
        # Estrai header per identificare le colonne
        header_row = result.find('tr')
        headers_list = []
        if header_row:
            ths = header_row.find_all(['th', 'td'])
            for th in ths:
                headers_list.append(th.get_text(strip=True))
        
        # Trova indici colonne
        title_idx = headers_list.index('Title') if 'Title' in headers_list else 0
        region_idx = headers_list.index('Region') if 'Region' in headers_list else -1
        
        # Le righe sono direttamente <tr> con <td> che contengono i link
        rows = result.find_all('tr')
        for row in rows:
            # Salta l'header se presente
            if row.find('th'):
                continue
            
            cells = row.find_all('td')
            if len(cells) <= title_idx:
                continue
            
            # Il <td> con indice title_idx contiene il link alla ROM
            title_cell = cells[title_idx]
            link = title_cell.find('a', href=True)
            if link:
                name = link.get_text(strip=True)
                uri_original = link['href']
                uri = uri_original
                # Assicurati che l'URI sia completo
                if not uri.startswith('/'):
                    uri = '/' + uri
                if not uri.startswith('/vault/'):
                    uri = '/vault/' + uri.lstrip('/')
                
                # Debug: log dell'URI originale per capire il formato
                if not re.search(r'/vault/(\d+)', uri):
                    print(f"⚠️ [get_system_search_roms] URI non numerico: {uri} (href originale: {uri_original})", file=sys.stderr)
                
                slug = get_rom_slug_from_uri(uri)
                
                # Estrai regione dall'immagine flag se disponibile
                regions = []
                if region_idx >= 0 and len(cells) > region_idx:
                    region_cell = cells[region_idx]
                    # Cerca immagine flag con attributo title
                    flag_img = region_cell.find('img', class_='flag')
                    if flag_img:
                        region = flag_img.get('title', '').strip()
                        if region:
                            regions = [region]
                    else:
                        # Fallback: testo della cella
                        region = region_cell.get_text(strip=True)
                        if region:
                            regions = [region]
                
                # Costruisci l'URL dell'immagine box art dall'URI
                # L'app proverà a caricarlo, e se fallisce userà il placeholder
                boxart_url = None
                rom_id = None
                match = re.search(r'/vault/(\d+)', uri)
                if match:
                    rom_id = match.group(1)
                    boxart_url = f'https://dl.vimm.net/image.php?type=box&id={rom_id}'
                
                rom = {
                    'slug': slug,
                    'rom_id': uri,  # Salviamo l'URI come rom_id per poterlo recuperare
                    'title': name,
                    'platform': map_system_to_mother_code(system, source_dir) if source_dir else 'unknown',
                    'boxart_url': boxart_url,  # Mantieni per compatibilità (deprecato)
                    'boxart_urls': [boxart_url] if boxart_url else [],  # Mantieni per compatibilità (deprecato)
                    'box_image': boxart_url,  # Box art (costruita dall'ID, se fallisce l'app userà placeholder)
                    'screen_image': None,  # Screen non disponibile nella ricerca (solo in getEntry)
                    'regions': regions,
                    'links': []
                }
                roms.append(rom)
    except Exception as e:
        print(f"Errore nella ricerca sistema: {e}", file=sys.stderr)
    
    return roms


def get_general_search_roms(search_key: str, page_num: int = 1, source_dir: str = None) -> List[Dict[str, Any]]:
    """
    Cerca ROM in generale su tutto il sito con paginazione
    Vimm's Lair restituisce massimo 200 righe per pagina
    """
    roms = []
    try:
        import urllib.parse
        query_params = {
            'mode': 'adv',
            'p': 'list',
            'q': search_key,
            'players': '>=',
            'playersValue': '1',
            'simultaneous': '',
            'publisher': '',
            'year': '=',
            'yearValue': '',
            'rating': '>=',
            'ratingValue': '',
            'region': 'All',
            'sort': 'Title',
            'sortOrder': 'ASC'
        }
        # Aggiungi numero pagina se > 1
        if page_num > 1:
            query_params['page'] = str(page_num)
        
        url = 'https://vimm.net/vault/?' + urllib.parse.urlencode(query_params)
        
        headers = {'User-Agent': get_random_ua()}
        page = requests.get(url, headers=headers, timeout=10, verify=False)
        soup = BeautifulSoup(page.content, 'html.parser')
        # La tabella può avere anche la classe 'striped'
        result = soup.find('table', class_=lambda x: x and 'rounded' in x and 'centered' in x and 'cellpadding1' in x and 'hovertable' in x)
        
        if not result:
            return roms
        
        # Estrai header per identificare le colonne
        header_row = result.find('tr')
        headers_list = []
        if header_row:
            ths = header_row.find_all(['th', 'td'])
            for th in ths:
                headers_list.append(th.get_text(strip=True))
        
        # Trova indici colonne (ricerca generale: System, Title, Region, Version, Languages)
        system_idx = headers_list.index('System') if 'System' in headers_list else -1
        title_idx = headers_list.index('Title') if 'Title' in headers_list else 1
        region_idx = headers_list.index('Region') if 'Region' in headers_list else -1
        
        # Le righe sono direttamente <tr> con <td> che contengono i link
        rows = result.find_all('tr')
        for row in rows:
            # Salta l'header se presente
            if row.find('th'):
                continue
            
            cells = row.find_all('td')
            if len(cells) <= title_idx:
                continue
            
            # Estrai sistema se disponibile
            system = None
            if system_idx >= 0 and len(cells) > system_idx:
                system = cells[system_idx].get_text(strip=True)
            
            # Estrai titolo e link
            title_cell = cells[title_idx]
            link = title_cell.find('a', href=True)
            if link:
                name = link.get_text(strip=True)
                uri_original = link['href']
                uri = uri_original
                # Assicurati che l'URI sia completo
                if not uri.startswith('/'):
                    uri = '/' + uri
                if not uri.startswith('/vault/'):
                    uri = '/vault/' + uri.lstrip('/')
                
                # Debug: log dell'URI originale per capire il formato
                if not re.search(r'/vault/(\d+)', uri):
                    print(f"⚠️ [get_general_search_roms] URI non numerico: {uri} (href originale: {uri_original})", file=sys.stderr)
                
                slug = get_rom_slug_from_uri(uri)
                
                # Estrai regione dall'immagine flag se disponibile
                regions = []
                if region_idx >= 0 and len(cells) > region_idx:
                    region_cell = cells[region_idx]
                    # Cerca immagine flag con attributo title
                    flag_img = region_cell.find('img', class_='flag')
                    if flag_img:
                        region = flag_img.get('title', '').strip()
                        if region:
                            regions = [region]
                    else:
                        # Fallback: testo della cella
                        region = region_cell.get_text(strip=True)
                        if region:
                            regions = [region]
                
                # Mappa il sistema al mother_code
                platform = 'unknown'
                if system and source_dir:
                    platform = map_system_to_mother_code(system, source_dir)
                
                # Costruisci l'URL dell'immagine box art dall'URI
                # L'app proverà a caricarlo, e se fallisce userà il placeholder
                boxart_url = None
                rom_id = None
                match = re.search(r'/vault/(\d+)', uri)
                if match:
                    rom_id = match.group(1)
                    boxart_url = f'https://dl.vimm.net/image.php?type=box&id={rom_id}'
                
                rom = {
                    'slug': slug,
                    'rom_id': uri,  # Salviamo l'URI come rom_id per poterlo recuperare
                    'title': name,
                    'platform': platform,
                    'boxart_url': boxart_url,  # Mantieni per compatibilità (deprecato)
                    'boxart_urls': [boxart_url] if boxart_url else [],  # Mantieni per compatibilità (deprecato)
                    'box_image': boxart_url,  # Box art (costruita dall'ID, se fallisce l'app userà placeholder)
                    'screen_image': None,  # Screen non disponibile nella ricerca (solo in getEntry)
                    'regions': regions,
                    'links': []
                }
                roms.append(rom)
    except Exception as e:
        print(f"Errore nella ricerca generale: {e}", file=sys.stderr)
    
    return roms


def get_rom_entry_by_uri(uri: str, source_dir: str, include_download_links: bool = True) -> Optional[Dict[str, Any]]:
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
            # Rimuovi il prefisso "The Vault:" se presente
            if title.startswith("The Vault:"):
                title = title.replace("The Vault:", "").strip()
        
        # Cerca il sistema
        system = None
        
        # Carica il mapping per ottenere tutti i codici Vimm's Lair possibili
        mapping = load_platform_mapping(source_dir)
        all_vimm_codes = set()
        for vimm_codes in mapping.values():
            if isinstance(vimm_codes, list):
                all_vimm_codes.update(vimm_codes)
            else:
                all_vimm_codes.add(vimm_codes)
        
        # Prova prima a estrarre dal titolo (es. "New Super Mario Bros. Wii (Wii)" -> "Wii")
        if title and '(' in title:
            title_lower = title.lower()
            for vimm_code in all_vimm_codes:
                if vimm_code.lower() in title_lower:
                    system = vimm_code
                    break
        
        # Se non trovato nel titolo, cerca nella pagina
        if not system:
            system_elem = soup.find(text=re.compile('System|Platform'))
            if system_elem:
                parent = system_elem.parent
                if parent:
                    system_text = parent.get_text()
                    for vimm_code in all_vimm_codes:
                        if vimm_code in system_text:
                            system = vimm_code
                            break
        
        # Se ancora non trovato, cerca in tutti i testi della pagina (case-insensitive)
        if not system:
            page_text = soup.get_text().lower()
            for vimm_code in all_vimm_codes:
                if vimm_code.lower() in page_text:
                    system = vimm_code
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
                # Verifica che non sia il logo di Vimm's Lair
                if 'vault.png' in boxart_url or 'logo' in boxart_url.lower():
                    boxart_url = None
        
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
        
        # Se ancora non trovata, prova prima con type=cart (spesso più affidabile)
        if not boxart_url and rom_id:
            # Prova prima con cart, poi con box come fallback
            cart_img = soup.find('img', src=lambda x: x and f'type=cart&id={rom_id}' in x)
            if cart_img:
                cart_src = cart_img.get('src', '')
                if cart_src.startswith('//'):
                    boxart_url = 'https:' + cart_src
                elif cart_src.startswith('/'):
                    boxart_url = 'https://vimm.net' + cart_src
                elif cart_src.startswith('http'):
                    boxart_url = cart_src
                else:
                    boxart_url = 'https://vimm.net/' + cart_src
        
        # NON costruiamo l'URL direttamente se non trovato nella pagina
        # Se non trovato, useremo il placeholder quando cover_urls è vuoto
        if not boxart_url:
            print(f"⚠️ [get_rom_entry_by_uri] boxart_url non trovato nella pagina per ROM {title} (rom_id: {rom_id})", file=sys.stderr)
        
        # Costruisci l'URL dell'immagine screen solo se trovata nella pagina
        # Cerca l'immagine screen nella pagina
        screen_url = None
        screen_img = soup.find('img', alt='Screen') or soup.find('img', src=lambda x: x and 'type=screen' in (x or ''))
        if screen_img:
            screen_src = screen_img.get('src', '')
            if screen_src:
                if screen_src.startswith('//'):
                    screen_url = 'https:' + screen_src
                elif screen_src.startswith('/'):
                    screen_url = 'https://vimm.net' + screen_src
                elif screen_src.startswith('http'):
                    screen_url = screen_src
        
        if not screen_url and rom_id:
            print(f"⚠️ [get_rom_entry_by_uri] screen_url non trovato nella pagina per ROM {title} (rom_id: {rom_id})", file=sys.stderr)
        
        # Verifica se lo screen è un placeholder di errore
        # Vimm's Lair restituisce sempre un'immagine screen anche quando non esiste
        # (con scritto "Error: image not found"). Dobbiamo verificare se l'immagine è valida.
        valid_screen_url = None
        if screen_url:
            # Verifica se l'immagine screen esiste realmente e non è un placeholder di errore
            try:
                headers = {'User-Agent': get_random_ua()}
                # Facciamo una richiesta GET per verificare il contenuto dell'immagine
                response = requests.get(screen_url, headers=headers, timeout=5, verify=False, allow_redirects=True)
                content_type = response.headers.get('Content-Type', '').lower()
                
                # Se è un'immagine valida (non un placeholder di errore)
                if response.status_code == 200 and 'image' in content_type:
                    # Verifica le dimensioni dell'immagine: le immagini di errore sono spesso 400x100 o simili
                    # Le immagini screen reali sono generalmente più grandi (almeno 200x200)
                    try:
                        from PIL import Image
                        from io import BytesIO
                        img = Image.open(BytesIO(response.content))
                        width, height = img.size
                        
                        # Le immagini di errore sono spesso piccole e rettangolari (es. 400x100)
                        # Le immagini screen reali sono generalmente più grandi e quadrate/rettangolari grandi
                        if width < 200 or height < 200:
                            print(f"⚠️ [get_rom_entry_by_uri] Screen troppo piccolo ({width}x{height}), probabilmente placeholder di errore: {screen_url}", file=sys.stderr)
                        elif width == 400 and height == 100:
                            # Dimensione tipica per immagini di errore Vimm's Lair
                            print(f"⚠️ [get_rom_entry_by_uri] Screen ha dimensioni tipiche di errore ({width}x{height}): {screen_url}", file=sys.stderr)
                        else:
                            valid_screen_url = screen_url
                            print(f"✅ [get_rom_entry_by_uri] Screen valido trovato: {screen_url} (size: {width}x{height})", file=sys.stderr)
                    except ImportError:
                        # PIL non disponibile, usa controllo dimensione file come fallback
                        size = len(response.content)
                        # Le immagini di errore sono spesso tra 5KB e 15KB
                        # Le immagini screen reali sono generalmente più grandi (> 20KB)
                        if 5000 < size < 20000:
                            print(f"⚠️ [get_rom_entry_by_uri] Screen ha dimensione sospetta ({size} bytes), potrebbe essere placeholder di errore: {screen_url}", file=sys.stderr)
                        elif size > 20000:
                            valid_screen_url = screen_url
                            print(f"✅ [get_rom_entry_by_uri] Screen valido trovato: {screen_url} (size: {size} bytes)", file=sys.stderr)
                        else:
                            print(f"⚠️ [get_rom_entry_by_uri] Screen troppo piccolo ({size} bytes): {screen_url}", file=sys.stderr)
                    except Exception as img_error:
                        # Errore nel processare l'immagine, usa dimensione file
                        size = len(response.content)
                        if size > 20000:
                            valid_screen_url = screen_url
                            print(f"✅ [get_rom_entry_by_uri] Screen trovato (size: {size} bytes, errore analisi immagine): {screen_url}", file=sys.stderr)
                        else:
                            print(f"⚠️ [get_rom_entry_by_uri] Screen sospetto (size: {size} bytes, errore analisi): {screen_url}", file=sys.stderr)
                else:
                    print(f"⚠️ [get_rom_entry_by_uri] Screen non valido (status: {response.status_code}, type: {content_type}): {screen_url}", file=sys.stderr)
            except Exception as e:
                # In caso di errore, non includiamo lo screen
                print(f"⚠️ [get_rom_entry_by_uri] Errore verifica screen: {e}", file=sys.stderr)
        
        # box_image è obbligatoria (se non presente, l'app userà il placeholder)
        # screen_image è facoltativa (solo se valida)
        print(f"📊 [get_rom_entry_by_uri] Box art: {boxart_url}, Screen: {valid_screen_url}", file=sys.stderr)
        
        # Estrai il dominio di download dalla tabella dl-row (può essere dl2 o dl3)
        # Ogni ROM può usare un dominio diverso, quindi lo estraiamo dalla tabella
        download_domain = "dl2.vimm.net"  # Default
        dl_row = soup.find('tr', id='dl-row')
        if dl_row:
            # Cerca il form dentro la tabella dl-row
            download_form = dl_row.find('form', id='dl_form')
            if download_form:
                action = download_form.get('action', '')
                if action.startswith('//'):
                    # Estrai il dominio da //dl2.vimm.net/ o //dl3.vimm.net/
                    match = re.search(r'//(dl[23]\.vimm\.net)', action)
                    if match:
                        download_domain = match.group(1)
        else:
            # Fallback: cerca il form direttamente
            download_form = soup.find('form', id='dl_form')
            if download_form:
                action = download_form.get('action', '')
                if action.startswith('//'):
                    match = re.search(r'//(dl[23]\.vimm\.net)', action)
                    if match:
                        download_domain = match.group(1)
        
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
        
        # Genera link per ogni combinazione di version (media) e format (solo se richiesto)
        links = []
        if include_download_links:
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
                    # Usa il dominio estratto dal form (può essere dl2 o dl3)
                    # Il formato viene passato come parametro 'alt' (0, 1, o 2)
                    download_url = f'https://{download_domain}/?mediaId={media_id}'
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
            if not links and include_download_links:
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
        
        # Estrai le regioni dalla tabella della pagina ROM
        # La struttura è: <tr><td>Region</td><td></td><td><img class="flag" title="USA">...</td></tr>
        regions = []
        # Cerca tutte le righe della tabella
        table_rows = soup.find_all('tr')
        for row in table_rows:
            cells = row.find_all('td')
            if len(cells) >= 3:
                # Il primo <td> dovrebbe contenere "Region"
                first_cell_text = cells[0].get_text(strip=True)
                if first_cell_text.lower() == 'region':
                    # Il terzo <td> contiene le immagini flag
                    region_cell = cells[2] if len(cells) > 2 else None
                    if region_cell:
                        # Trova tutte le immagini flag con attributo title
                        flag_imgs = region_cell.find_all('img', class_='flag')
                        for flag_img in flag_imgs:
                            region = flag_img.get('title', '').strip()
                            if region and region not in regions:
                                regions.append(region)
                    break  # Trovata la riga Region, esci dal loop
        
        # Se non trovate regioni nella tabella, prova a cercare in modo alternativo
        if not regions:
            # Cerca direttamente tutte le immagini flag nella pagina
            all_flag_imgs = soup.find_all('img', class_='flag')
            for flag_img in all_flag_imgs:
                region = flag_img.get('title', '').strip()
                if region and region not in regions:
                    regions.append(region)
        
        print(f"🌍 [get_rom_entry_by_uri] Regioni trovate: {regions}", file=sys.stderr)
        
        # Estrai la versione dai link se disponibile (prendi la prima versione trovata)
        version_string = None
        if links:
            # Cerca "Version X.X" nel nome del primo link
            first_link_name = links[0].get('name', '')
            version_match = re.search(r'Version\s+([\d.]+)', first_link_name)
            if version_match:
                version_string = version_match.group(1)
        
        # Aggiungi la versione al titolo se presente e non già inclusa
        final_title = title
        if version_string and version_string not in title:
            final_title = f"{title} (v{version_string})"
        
        entry = {
            'slug': slug,
            'rom_id': uri,
            'title': final_title,
            'platform': map_system_to_mother_code(system, source_dir) if system else 'unknown',
            'boxart_url': boxart_url,  # Mantieni per compatibilità (deprecato)
            'boxart_urls': [boxart_url] if boxart_url else [],  # Mantieni per compatibilità (deprecato)
            'box_image': boxart_url,  # Box art (obbligatoria, null se non presente)
            'screen_image': valid_screen_url,  # Screen (facoltativa, null se non presente)
            'regions': regions,  # Regioni estratte dalla pagina ROM
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
        source_dir = params.get("source_dir")
        
        if not source_dir:
            return json.dumps({"error": "source_dir non fornito"})
        
        if method == "searchRoms":
            return search_roms(params, source_dir)
        elif method == "getEntry":
            return get_entry(params, source_dir)
        elif method == "getPlatforms":
            return get_platforms(source_dir)
        elif method == "getRegions":
            return get_regions()
        else:
            return json.dumps({"error": f"Metodo sconosciuto: {method}"})
    except Exception as e:
        return json.dumps({"error": str(e)})


def search_roms(params: Dict[str, Any], source_dir: str) -> str:
    """
    Cerca ROM nella sorgente
    Vimm's Lair restituisce massimo 200 righe per pagina
    """
    search_key = params.get("search_key") or ""
    if search_key:
        search_key = search_key.strip()
    platforms = params.get("platforms", [])
    regions = params.get("regions", [])  # Filtro regioni
    max_results = params.get("max_results", 50)
    page = params.get("page", 1)
    
    # Normalizza i codici regione per il confronto (uppercase)
    if regions:
        regions = [r.upper().strip() for r in regions if r]
    
    # Vimm's Lair ha un limite di 200 righe per pagina
    VIMMS_PAGE_SIZE = 200
    
    all_roms = []
    
    # Se ci sono piattaforme specificate, cerca per ogni piattaforma
    if platforms:
        for platform in platforms:
            # Mappa il mother_code al codice URI Vimm's Lair usando platform_mapping.json
            system_uri = map_mother_code_to_vimm_uri(platform, source_dir)
            
            if not system_uri:
                # Se non troviamo il mapping, prova a usare il codice direttamente (case-insensitive)
                # Questo può succedere se il mapping non è completo
                system_uri = platform.upper()  # Prova con uppercase
            
            if system_uri:
                # Vimm's Lair permette query vuota per ottenere tutte le ROM del sistema
                query = search_key if search_key else ''
                
                # Calcola quale pagina di Vimm's Lair serve per questa richiesta
                # Se max_results=50 e page=1, prendiamo la pagina 1 di Vimm's Lair (righe 0-199)
                # Se max_results=50 e page=2, prendiamo ancora la pagina 1 (righe 50-99)
                # Se max_results=50 e page=5, prendiamo la pagina 2 di Vimm's Lair (righe 200-399)
                vimms_page = ((page - 1) * max_results) // VIMMS_PAGE_SIZE + 1
                offset_in_page = ((page - 1) * max_results) % VIMMS_PAGE_SIZE
                
                # Carica la pagina di Vimm's Lair
                roms = get_system_search_roms(query, system_uri, vimms_page, source_dir)
                
                # Applica l'offset e limita i risultati
                start_idx = offset_in_page
                end_idx = start_idx + max_results
                paginated_roms = roms[start_idx:end_idx]
                all_roms.extend(paginated_roms)
    else:
        # Ricerca generale - richiede una query
        if not search_key:
            return json.dumps({
                "results": [],
                "total_results": 0,
                "current_page": page,
                "total_pages": 1
            })
        
        # Calcola quale pagina di Vimm's Lair serve
        vimms_page = ((page - 1) * max_results) // VIMMS_PAGE_SIZE + 1
        offset_in_page = ((page - 1) * max_results) % VIMMS_PAGE_SIZE
        
        # Carica la pagina di Vimm's Lair
        roms = get_general_search_roms(search_key, vimms_page, source_dir)
        
        # Applica l'offset e limita i risultati
        start_idx = offset_in_page
        end_idx = start_idx + max_results
        all_roms = roms[start_idx:end_idx]
    
    # Filtra per regioni se specificate
    if regions:
        print(f"🔍 [search_roms] Applicando filtro regioni: {regions} (pagina {page})", file=sys.stderr)
        print(f"🔍 [search_roms] ROM prima del filtro: {len(all_roms)}", file=sys.stderr)
        filtered_roms = []
        # Mapping completo: codici -> nomi possibili
        region_mapping = {
            'EU': ['EU', 'EUROPE', 'E', 'EUROPEAN'],
            'US': ['US', 'USA', 'U', 'UNITED STATES', 'AMERICA', 'NORTH AMERICA'],
            'JP': ['JP', 'JAPAN', 'J', 'JAPANESE'],
            'WW': ['WW', 'WORLDWIDE', 'W', 'WORLD'],
            'KR': ['KR', 'KOREA', 'SOUTH KOREA'],
            'CN': ['CN', 'CHINA', 'CHINESE'],
            'AU': ['AU', 'AUSTRALIA'],
            'BR': ['BR', 'BRAZIL'],
            'UK': ['UK', 'UNITED KINGDOM', 'BRITAIN', 'BRITISH'],
            'FR': ['FR', 'FRANCE', 'FRENCH'],
            'DE': ['DE', 'GERMANY', 'GERMAN'],
            'IT': ['IT', 'ITALY', 'ITALIAN'],
            'ES': ['ES', 'SPAIN', 'SPANISH'],
            'NL': ['NL', 'NETHERLANDS', 'HOLLAND', 'DUTCH'],
            'SE': ['SE', 'SWEDEN', 'SWEDISH'],
            'NO': ['NO', 'NORWAY', 'NORWEGIAN'],
            'DK': ['DK', 'DENMARK', 'DANISH'],
            'FI': ['FI', 'FINLAND', 'FINNISH']
        }
        
        for rom in all_roms:
            rom_regions = rom.get('regions', [])
            if not rom_regions:
                # Se la ROM non ha regioni, la escludiamo quando c'è un filtro regioni attivo
                continue
            
            # Normalizza le regioni della ROM per il confronto
            rom_regions_normalized = [r.upper().strip() if isinstance(r, str) else str(r).upper().strip() for r in rom_regions]
            
            # Verifica se almeno una regione della ROM corrisponde a una regione richiesta
            matches = False
            for requested_region_code in regions:
                requested_region_code = requested_region_code.upper().strip()
                
                # Verifica corrispondenza diretta
                if requested_region_code in rom_regions_normalized:
                    matches = True
                    break
                
                # Verifica tramite mapping
                if requested_region_code in region_mapping:
                    # Ottieni tutti i possibili nomi per questo codice
                    possible_names = region_mapping[requested_region_code]
                    # Verifica se almeno uno dei nomi possibili è presente nelle regioni della ROM
                    if any(name in rom_regions_normalized for name in possible_names):
                        matches = True
                        break
                else:
                    # Se il codice non è nel mapping, verifica corrispondenza diretta con qualsiasi alias
                    for key, aliases in region_mapping.items():
                        if requested_region_code in aliases:
                            # Verifica se la ROM ha questo codice o uno dei suoi alias
                            if key in rom_regions_normalized or any(alias in rom_regions_normalized for alias in aliases):
                                matches = True
                                break
                    if matches:
                        break
            
            if matches:
                filtered_roms.append(rom)
        
        print(f"🔍 [search_roms] ROM dopo il filtro: {len(filtered_roms)}", file=sys.stderr)
        all_roms = filtered_roms
    
    # Per il totale, dobbiamo stimare basandoci sui risultati ottenuti
    # Se abbiamo ottenuto meno di max_results, siamo all'ultima pagina
    # Altrimenti, potrebbe esserci di più
    total_results = len(all_roms)
    if len(all_roms) == max_results:
        # Potrebbe esserci di più, ma non possiamo saperlo senza fare altre richieste
        # Usiamo una stima conservativa
        total_results = page * max_results + 1  # Indica che c'è almeno un'altra pagina
    else:
        # Siamo all'ultima pagina
        total_results = (page - 1) * max_results + len(all_roms)
    
    # Debug: verifica quante ROM hanno boxart_url
    roms_with_images = [r for r in all_roms if r.get('boxart_url')]
    # Log solo per ROM senza immagini (debug placeholder)
    if len(all_roms) > 0 and not roms_with_images:
        first_rom = all_roms[0]
        print(f"⚠️ [search_roms] ROM senza immagini: {first_rom.get('title')}, box_image: {first_rom.get('box_image')}", file=sys.stderr)
    
    response = {
        "results": all_roms,
        "total_results": total_results,
        "current_results": len(all_roms),
        "current_page": page,
        "total_pages": (total_results + max_results - 1) // max_results if total_results > 0 else 1
    }
    
    return json.dumps(response)


def get_entry(params: Dict[str, Any], source_dir: str) -> str:
    """Ottiene una entry specifica per slug"""
    slug = params.get("slug")
    include_download_links = params.get("include_download_links", True)  # Default True per retrocompatibilità
    
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
        entry = get_rom_entry_by_uri(uri, source_dir, include_download_links)
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
        roms = get_general_search_roms(search_name, 1, source_dir)
        
        # Cerca la ROM con slug corrispondente
        for rom in roms:
            if rom['slug'] == slug and rom.get('rom_id'):
                # Trovata! Ora ottieni i dettagli completi
                uri = rom['rom_id']
                entry = get_rom_entry_by_uri(uri, source_dir, include_download_links)
                if entry:
                    return json.dumps({"entry": entry})
    
    # Se non trovata, restituisci entry null per coerenza con l'API
    return json.dumps({
        "entry": None
    })


def get_platforms(source_dir: str) -> str:
    """Ottiene le piattaforme disponibili usando platform_mapping.json"""
    # Carica il mapping dalla source directory
    mapping = load_platform_mapping(source_dir)
    
    # Crea un dizionario con i mother_code come chiavi
    # Ogni valore è un dizionario con "name" che contiene il primo codice Vimm's Lair
    platforms = {}
    
    for mother_code, vimm_codes in mapping.items():
        # Prendi il primo codice Vimm's Lair come nome
        if isinstance(vimm_codes, list):
            name = vimm_codes[0] if vimm_codes else mother_code
        else:
            name = vimm_codes
        
        platforms[mother_code] = {
            "name": name
        }
    
    # Restituisci nel formato atteso: {"platforms": {...}}
    response = {
        "platforms": platforms
    }
    
    return json.dumps(response)


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

