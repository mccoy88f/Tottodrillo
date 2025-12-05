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
                # Costruisce l'URL di download diretto (usa dl3.vimm.net come nel form)
                return f'https://dl3.vimm.net/download/?mediaId={media_id}'
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
        # Ottieni l'URL di download
        download_url = get_rom_download_url(uri)
        
        if not download_url:
            return None
        
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
        
        # Cerca l'immagine della box art
        boxart_url = None
        # L'immagine ha alt="Box" e src che inizia con //dl.vimm.net/image.php?type=box&id=
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
        
        # Determina il formato dal nome del file o dall'URL
        format_type = "zip"  # Default
        if download_url:
            if '.7z' in download_url or '7z' in download_url:
                format_type = "7z"
            elif '.zip' in download_url or 'zip' in download_url:
                format_type = "zip"
        
        slug = get_rom_slug_from_uri(uri)
        
        entry = {
            'slug': slug,
            'rom_id': uri,
            'title': title,
            'platform': map_system_to_mother_code(system) if system else 'unknown',
            'boxart_url': boxart_url,
            'regions': [],
            'links': [{
                'name': 'Download',
                'type': 'direct',
                'format': format_type,
                'url': download_url,
                'size_str': None
            }]
        }
        
        return entry
    except Exception as e:
        print(f"Errore nel recupero entry: {e}", file=sys.stderr)
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
    
    # Se non trovata, restituisci errore
    return json.dumps({
        "error": f"ROM con slug '{slug}' non trovata. Esegui prima una ricerca."
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

