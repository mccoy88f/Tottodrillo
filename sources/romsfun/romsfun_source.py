"""
Wrapper Python per integrare ROMsFun.com come sorgente Tottodrillo
Implementa l'interfaccia SourceExecutor
"""
import json
import re
import sys
import os
import urllib.parse
from typing import Dict, Any, List, Optional
import requests
from bs4 import BeautifulSoup

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

def map_romsfun_slug_to_mother_code(romsfun_slug: str, source_dir: str) -> str:
    """
    Mappa un slug ROMsFun (es. "game-boy") a un mother_code Tottodrillo
    Usa platform_mapping.json dalla source directory
    """
    if not romsfun_slug:
        return 'unknown'
    
    # Carica il mapping
    mapping = load_platform_mapping(source_dir)
    
    # Normalizza lo slug (case-insensitive)
    romsfun_slug_lower = romsfun_slug.lower().strip()
    
    # Cerca il mother_code corrispondente
    # Il mapping è: mother_code -> slug ROMsFun
    for mother_code, romsfun_slugs in mapping.items():
        if isinstance(romsfun_slugs, list):
            # Se è una lista, controlla tutti gli slug
            for slug in romsfun_slugs:
                if slug.lower() == romsfun_slug_lower:
                    return mother_code
        else:
            # Se è una stringa singola
            if romsfun_slugs.lower() == romsfun_slug_lower:
                return mother_code
    
    # Se non trovato, ritorna lo slug normalizzato
    return romsfun_slug_lower.replace('-', '')

def map_mother_code_to_romsfun_slug(mother_code: str, source_dir: str) -> Optional[str]:
    """
    Mappa un mother_code Tottodrillo allo slug ROMsFun da usare nell'URL
    Ritorna il primo slug disponibile
    
    Gestisce sia mother_code ('psx') che slug ROMsFun diretti ('playstation')
    Tutti i confronti sono case-insensitive come in Tottodrillo
    """
    if not mother_code:
        return None
    
    # Normalizza a minuscolo per il matching (case-insensitive)
    mother_code_lower = mother_code.lower().strip()
    
    # Carica il mapping
    mapping = load_platform_mapping(source_dir)
    
    # PRIMA: Cerca il mother_code come chiave nel mapping (case-insensitive)
    # Normalizza anche le chiavi del mapping per il confronto
    for key, value in mapping.items():
        if key.lower() == mother_code_lower:
            # Trovato come chiave (mother_code)
            if isinstance(value, list):
                return value[0].lower().strip() if value else None
            return value.lower().strip()
    
    # SECONDA: Se non trovato, prova a cercare se il mother_code è già uno slug ROMsFun
    # Cerca nei valori del mapping (slug ROMsFun) - questo gestisce il caso in cui
    # l'app passa 'playstation' invece di 'psx' o 'nintendo-wii' invece di 'wii'
    for mapped_mother_code, mapped_slugs in mapping.items():
        if isinstance(mapped_slugs, list):
            # Cerca nella lista di slug (case-insensitive)
            for slug in mapped_slugs:
                if slug.lower().strip() == mother_code_lower:
                    return mother_code_lower  # È già uno slug ROMsFun valido
        else:
            # Confronta con lo slug singolo (case-insensitive)
            if mapped_slugs.lower().strip() == mother_code_lower:
                return mother_code_lower  # È già uno slug ROMsFun valido
    
    # TERZA: Se ancora non trovato, potrebbe essere già uno slug ROMsFun non mappato
    # In questo caso, restituiscilo così com'è (potrebbe funzionare)
    return mother_code_lower

def get_random_ua() -> str:
    """Genera un User-Agent casuale"""
    import random
    uas = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15'
    ]
    return random.choice(uas)

def get_browser_headers(referer: Optional[str] = None) -> Dict[str, str]:
    """
    Genera header HTTP per simulare un browser reale
    Include User-Agent, Accept, Accept-Language, Referer, ecc.
    """
    headers = {
        'User-Agent': get_random_ua(),
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept-Encoding': 'gzip, deflate',  # Rimuoviamo 'br' (Brotli) perché requests non lo decomprime automaticamente
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none' if not referer else 'same-origin',
        'Cache-Control': 'max-age=0'
    }
    
    # Aggiungi Referer se fornito (simula navigazione da un'altra pagina del sito)
    if referer:
        headers['Referer'] = referer
        headers['Sec-Fetch-Site'] = 'same-origin'
    
    return headers

def get_final_download_url(sub_link_url: str, session: requests.Session, referer: str) -> Optional[str]:
    """
    Segue un link di download intermedio per ottenere l'URL finale del file
    Es: /download/slug-12345/1 -> URL finale del file con token
    
    Nota: La pagina ha un countdown di ~15 secondi prima di mostrare il pulsante.
    Il pulsante potrebbe essere nascosto inizialmente o generato via JavaScript.
    Proviamo a cercare anche elementi nascosti o script che contengono l'URL.
    """
    try:
        headers = get_browser_headers(referer=referer)
        # Aumenta il timeout per dare tempo al countdown
        final_page = session.get(sub_link_url, headers=headers, timeout=20)
        final_page.raise_for_status()
        final_soup = BeautifulSoup(final_page.content, 'html.parser')
        
        # 1. Cerca il link con id="download" (link principale con token)
        # Potrebbe essere nascosto inizialmente (style="display:none" o class nascosta)
        download_link = final_soup.find('a', id='download')
        if download_link:
            file_url = download_link.get('href', '')
            if file_url:
                # L'URL è già completo con token (es: https://sto.romsfast.com/...?token=...)
                if file_url.startswith('http'):
                    return file_url
                else:
                    # Se relativo, aggiungi il dominio
                    return 'https://romsfun.com' + file_url
        
        # 2. Cerca anche link nascosti (potrebbero essere generati prima del countdown)
        download_link_hidden = final_soup.find('a', id='download', style=lambda x: x and 'display' in x.lower())
        if download_link_hidden:
            file_url = download_link_hidden.get('href', '')
            if file_url and file_url.startswith('http'):
                return file_url
        
        # 3. Cerca negli script JavaScript per URL con token
        # A volte l'URL è già presente nello script anche se il pulsante non è visibile
        scripts = final_soup.find_all('script')
        for script in scripts:
            script_text = script.string or ''
            if not script_text:
                continue
            
            # Pattern multipli per trovare URL con token
            url_patterns = [
                r'https://sto\.romsfast\.com/[^"\s\'>]+\?token=[^"\s\'>]+',
                r'["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'href\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'url\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'location\.href\s*=\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'window\.location\s*=\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'downloadUrl\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
            ]
            
            for pattern in url_patterns:
                url_match = re.search(pattern, script_text)
                if url_match:
                    extracted_url = url_match.group(1) if url_match.lastindex else url_match.group(0)
                    if extracted_url.startswith('http'):
                        print(f"✅ [get_final_download_url] URL finale trovato in script (prima richiesta): {extracted_url[:80]}...", file=sys.stderr)
                        return extracted_url
        
        # 4. Aspetta ~16 secondi per il countdown (il pulsante appare dopo ~15 secondi)
        # Il pulsante potrebbe essere generato via JavaScript dopo il countdown
        import time
        print(f"⏳ [get_final_download_url] Attesa countdown di 16 secondi per {sub_link_url}", file=sys.stderr)
        time.sleep(16)  # Aspetta 16 secondi per il countdown
        
        # Seconda richiesta: dopo l'attesa, il pulsante dovrebbe essere presente
        print(f"🔄 [get_final_download_url] Seconda richiesta dopo countdown per {sub_link_url}", file=sys.stderr)
        final_page2 = session.get(sub_link_url, headers=headers, timeout=20)
        final_page2.raise_for_status()
        final_soup2 = BeautifulSoup(final_page2.content, 'html.parser')
        
        download_link2 = final_soup2.find('a', id='download')
        if download_link2:
            file_url = download_link2.get('href', '')
            if file_url:
                if file_url.startswith('http'):
                    print(f"✅ [get_final_download_url] URL finale trovato dopo countdown: {file_url[:80]}...", file=sys.stderr)
                    return file_url
                else:
                    file_url = 'https://romsfun.com' + file_url
                    print(f"✅ [get_final_download_url] URL finale trovato dopo countdown: {file_url[:80]}...", file=sys.stderr)
                    return file_url
        
        # Cerca anche link nascosti dopo il countdown
        all_download_links2 = final_soup2.find_all('a', id='download')
        for link in all_download_links2:
            file_url = link.get('href', '')
            if file_url and file_url.startswith('http'):
                print(f"✅ [get_final_download_url] URL finale trovato (nascosto) dopo countdown: {file_url[:80]}...", file=sys.stderr)
                return file_url
        
        # Cerca negli script JavaScript dopo il countdown
        scripts2 = final_soup2.find_all('script')
        for script in scripts2:
            script_text = script.string or ''
            url_patterns = [
                r'https://sto\.romsfast\.com/[^"\s\']+\?token=[^"\s\']+',
                r'href\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']',
                r'["\'](https://sto\.romsfast\.com/[^"\']+\?token=[^"\']+)["\']'
            ]
            for pattern in url_patterns:
                url_match = re.search(pattern, script_text)
                if url_match:
                    extracted_url = url_match.group(1) if url_match.lastindex else url_match.group(0)
                    if extracted_url.startswith('http'):
                        print(f"✅ [get_final_download_url] URL finale trovato in script dopo countdown: {extracted_url[:80]}...", file=sys.stderr)
                        return extracted_url
        
        # 5. Fallback: cerca link diretto a file (es. .zip, .7z, .rvz, .wbfs, .iso)
        file_link = final_soup2.find('a', href=re.compile(r'\.(zip|7z|rvz|wbfs|iso|rar)', re.IGNORECASE))
        if file_link:
            file_url = file_link.get('href', '')
            if file_url:
                if file_url.startswith('http'):
                    return file_url
                elif not file_url.startswith('http'):
                    file_url = 'https://romsfun.com' + file_url
                    return file_url
        
        # 6. Cerca form con action che punta al file
        form = final_soup2.find('form')
        if form:
            form_action = form.get('action', '')
            if form_action and (form_action.endswith(('.zip', '.7z', '.rvz', '.wbfs', '.iso', '.rar')) or 
                               'download' in form_action.lower() or 'token' in form_action.lower()):
                if form_action.startswith('http'):
                    return form_action
                elif not form_action.startswith('http'):
                    form_action = 'https://romsfun.com' + form_action
                    return form_action
        
        # 7. Cerca meta refresh o redirect
        meta_refresh = final_soup2.find('meta', attrs={'http-equiv': re.compile('refresh', re.I)})
        if meta_refresh:
            content = meta_refresh.get('content', '')
            url_match = re.search(r'url=([^\s;]+)', content, re.I)
            if url_match:
                redirect_url = url_match.group(1)
                if redirect_url.startswith('http'):
                    return redirect_url
                elif not redirect_url.startswith('http'):
                    redirect_url = 'https://romsfun.com' + redirect_url
                    return redirect_url
        
        # 8. Cerca in tutti gli script (anche quelli inline) per pattern più ampi
        all_scripts = final_soup2.find_all('script')
        for script in all_scripts:
            script_text = script.string or ''
            if not script_text:
                continue
            
            # Pattern più ampi per trovare URL con token
            patterns = [
                r'https://sto\.romsfast\.com/[^\s"\'>]+',
                r'["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'href\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'url\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'location\.href\s*=\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'window\.location\s*=\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'downloadUrl\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'fileUrl\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
                r'link\s*[:=]\s*["\'](https://sto\.romsfast\.com/[^"\']+)["\']',
            ]
            for pattern in patterns:
                matches = re.findall(pattern, script_text)
                for match in matches:
                    url = match if isinstance(match, str) else match.group(1) if hasattr(match, 'lastindex') and match.lastindex else match.group(0)
                    if url and url.startswith('http') and ('sto.romsfast.com' in url):
                        print(f"✅ [get_final_download_url] URL finale trovato in script (pattern avanzato): {url[:80]}...", file=sys.stderr)
                        return url
        
        # 9. Cerca anche nel body HTML completo (potrebbe essere in attributi data-*)
        body = final_soup2.find('body')
        if body:
            # Cerca attributi data-* che potrebbero contenere l'URL
            for element in body.find_all(attrs=lambda x: x and any(k.startswith('data-') for k in x.keys())):
                for attr_name, attr_value in element.attrs.items():
                    if isinstance(attr_value, str) and 'sto.romsfast.com' in attr_value and 'token=' in attr_value:
                        url_match = re.search(r'https://sto\.romsfast\.com/[^\s"\'>]+', attr_value)
                        if url_match:
                            print(f"✅ [get_final_download_url] URL finale trovato in attributo {attr_name}: {url_match.group(0)[:80]}...", file=sys.stderr)
                            return url_match.group(0)
        
        # 10. Se non trovato, restituisci l'URL della pagina intermedia
        # NOTA: Questo URL richiede un WebView per gestire il countdown e il JavaScript
        # L'app userà requires_webview=true per aprire un WebView headless
        print(f"⚠️ [get_final_download_url] URL finale non trovato dopo tutti i tentativi", file=sys.stderr)
        print(f"   Restituisco URL intermedio: {sub_link_url}", file=sys.stderr)
        print(f"   L'app dovrà aprire un WebView headless per gestire il countdown", file=sys.stderr)
        return sub_link_url
        
    except Exception as e:
        print(f"⚠️ [get_final_download_url] Errore: {e}", file=sys.stderr)
        # In caso di errore, restituisci l'URL intermedio
        return sub_link_url

def get_rom_slug_from_url(url: str) -> str:
    """
    Estrae lo slug della ROM dall'URL
    Es: https://romsfun.com/roms/game-boy/super-mario-land/ -> super-mario-land
    """
    match = re.search(r'/roms/[^/]+/([^/]+)/?$', url)
    if match:
        return match.group(1)
    return url.split('/')[-1].replace('.html', '')

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
        import traceback
        error_msg = f"{str(e)}\n{traceback.format_exc()}"
        print(f"❌ [execute] Errore: {error_msg}", file=sys.stderr)
        return json.dumps({"error": error_msg})

def search_roms(params: Dict[str, Any], source_dir: str) -> str:
    """
    Cerca ROM nella sorgente ROMsFun
    Supporta ricerca per piattaforma e query testuale
    """
    search_key = params.get("search_key") or ""
    if search_key:
        search_key = search_key.strip()
    platforms = params.get("platforms", [])
    regions = params.get("regions", [])
    max_results = params.get("max_results", 50)
    page = params.get("page", 1)
    
    all_roms = []
    
    # Se ci sono piattaforme specificate, cerca per ogni piattaforma
    if platforms:
        for platform in platforms:
            # Mappa il mother_code allo slug ROMsFun
            # La funzione gestisce automaticamente sia mother_code che slug ROMsFun diretti
            platform_slug = map_mother_code_to_romsfun_slug(platform, source_dir)
            
            if not platform_slug:
                # Questo non dovrebbe mai accadere, ma per sicurezza saltiamo
                print(f"⚠️ [search_roms] Impossibile determinare slug per piattaforma '{platform}', saltata", file=sys.stderr)
                continue
            
            # Costruisci l'URL della pagina della piattaforma
            # ROMsFun usa URL tipo: /roms/nintendo-ds?keywords=query&orderby=popular&order=desc
            # Il formato cambia in base alla piattaforma, ma lo slug è sempre quello dal mapping
            # Assicurati che lo slug sia in minuscolo per la costruzione dell'URL
            platform_slug_lower = platform_slug.lower().strip()
            if search_key:
                # Ricerca per piattaforma con query: usa keywords invece di s
                # Codifica la query per l'URL (sostituisce spazi con +)
                keywords = urllib.parse.quote_plus(search_key)
                search_url = f'https://romsfun.com/roms/{platform_slug_lower}?keywords={keywords}&orderby=popular&order=desc'
            else:
                # Solo elenco piattaforma senza query
                # URL formato: https://romsfun.com/roms/{platform_slug}/
                search_url = f'https://romsfun.com/roms/{platform_slug_lower}/'
            
            print(f"🔗 [search_roms] URL costruito per piattaforma '{platform}' (slug: '{platform_slug}'): {search_url}", file=sys.stderr)
            
            # Aggiungi paginazione se page > 1
            # ROMsFun usa 'page' come parametro di paginazione
            if page > 1:
                if '?' in search_url:
                    search_url += f'&page={page}'
                else:
                    search_url += f'?page={page}'
            
            print(f"🔍 [search_roms] Cercando ROM su {search_url}", file=sys.stderr)
            
            # Estrai ROM dalla pagina
            roms = get_roms_from_platform_page(search_url, platform_slug, source_dir, regions)
            print(f"✅ [search_roms] Trovate {len(roms)} ROM per piattaforma {platform}", file=sys.stderr)
            all_roms.extend(roms)
            
            # Se abbiamo raggiunto max_results, fermiamoci
            if len(all_roms) >= max_results:
                all_roms = all_roms[:max_results]
                break
    else:
        # Ricerca generale senza piattaforma specifica
        # ROMsFun usa: https://romsfun.com/?s=query
        if search_key:
            # Usa la pagina di ricerca generale
            # Codifica la query per l'URL (sostituisce spazi con +)
            query_encoded = urllib.parse.quote_plus(search_key)
            search_url = f'https://romsfun.com/?s={query_encoded}'
            
            # Aggiungi paginazione se page > 1
            if page > 1:
                search_url += f'&page={page}'
            
            print(f"🔍 [search_roms] Ricerca generale su {search_url}", file=sys.stderr)
            
            # Per la ricerca generale, dobbiamo estrarre ROM da tutte le piattaforme
            # Usiamo get_roms_from_platform_page con un URL di ricerca generale
            roms = get_roms_from_platform_page(search_url, None, source_dir, regions)
            all_roms.extend(roms)
        else:
            # Senza query e senza piattaforma, non possiamo fare una ricerca significativa
            print(f"⚠️ [search_roms] Nessuna piattaforma o query specificata", file=sys.stderr)
            return json.dumps({
                "results": [],
                "total_results": 0,
                "current_results": 0,
                "current_page": page,
                "total_pages": 1
            })
    
    # Nota: Il filtro per regioni non viene applicato qui perché le regioni
    # non sono disponibili nella pagina di elenco. Il repository applicherà
    # il filtro dopo aver ottenuto i dettagli delle ROM tramite get_entry()
    
    # Limita i risultati
    all_roms = all_roms[:max_results]
    
    print(f"✅ [search_roms] Totale ROM trovate: {len(all_roms)}", file=sys.stderr)
    
    # Stima totale risultati (non possiamo saperlo esattamente senza fare altre richieste)
    total_results = len(all_roms)
    if len(all_roms) == max_results:
        # Potrebbe esserci di più
        total_results = page * max_results + 1
    
    response = {
        "results": all_roms,
        "total_results": total_results,
        "current_results": len(all_roms),
        "current_page": page,
        "total_pages": (total_results + max_results - 1) // max_results if total_results > 0 else 1
    }
    
    return json.dumps(response)


def get_roms_from_platform_page(page_url: str, platform_slug: Optional[str], source_dir: str, regions_filter: List[str] = None) -> List[Dict[str, Any]]:
    """
    Estrae le ROM dalla pagina di elenco di una piattaforma o dalla pagina di ricerca
    platform_slug può essere None per ricerca generale
    """
    roms = []
    
    try:
        # Usa session per mantenere i cookie tra le richieste
        session = requests.Session()
        
        # Simula navigazione browser reale per bypassare Cloudflare:
        # 1. Prima visita la homepage per ottenere cookie Cloudflare iniziali
        import time
        try:
            home_headers = get_browser_headers()
            print(f"🌐 [get_roms_from_platform_page] Visita homepage per ottenere cookie Cloudflare...", file=sys.stderr)
            home_response = session.get('https://romsfun.com/', headers=home_headers, timeout=15)
            
            # Se riceviamo 403 anche sulla homepage, aspetta e riprova
            if home_response.status_code == 403:
                print(f"⚠️ [get_roms_from_platform_page] 403 sulla homepage, attendo 3 secondi...", file=sys.stderr)
                time.sleep(3)
                home_headers = get_browser_headers()  # Nuovo User-Agent
                home_response = session.get('https://romsfun.com/', headers=home_headers, timeout=15)
            
            home_response.raise_for_status()
            print(f"✅ [get_roms_from_platform_page] Cookie Cloudflare ottenuti dalla homepage", file=sys.stderr)
            # Aspetta un po' per simulare comportamento umano
            time.sleep(1)
        except Exception as e:
            print(f"⚠️ [get_roms_from_platform_page] Errore visita homepage: {e}, continuo comunque...", file=sys.stderr)
            # Continua comunque, ma aspetta un po'
            time.sleep(1)
        
        # 2. Poi visita la pagina della piattaforma con Referer dalla homepage
        # Questo simula un click da homepage alla pagina della piattaforma
        headers = get_browser_headers(referer='https://romsfun.com/')
        
        # Aggiungi un piccolo delay per simulare comportamento umano
        time.sleep(0.5)
        
        print(f"🔗 [get_roms_from_platform_page] Richiesta pagina: {page_url}", file=sys.stderr)
        page = session.get(page_url, headers=headers, timeout=20)
        
        # Se riceviamo 403, prova a riprovare con un delay più lungo
        if page.status_code == 403:
            print(f"⚠️ [get_roms_from_platform_page] Ricevuto 403, attendo 3 secondi e riprovo...", file=sys.stderr)
            time.sleep(3)
            # Riprova con un nuovo User-Agent e stesso Referer
            headers = get_browser_headers(referer='https://romsfun.com/')
            page = session.get(page_url, headers=headers, timeout=20)
        
        page.raise_for_status()
        
        # Debug: verifica status e dimensione risposta
        print(f"📊 [get_roms_from_platform_page] Status code: {page.status_code}, Dimensione: {len(page.content)} bytes", file=sys.stderr)
        print(f"📊 [get_roms_from_platform_page] Content-Type: {page.headers.get('Content-Type', 'N/A')}", file=sys.stderr)
        
        # Debug: verifica encoding e compressione
        print(f"📊 [get_roms_from_platform_page] Encoding: {page.encoding}, Apparent encoding: {page.apparent_encoding}", file=sys.stderr)
        print(f"📊 [get_roms_from_platform_page] Content-Encoding header: {page.headers.get('Content-Encoding', 'N/A')}", file=sys.stderr)
        
        # Debug: primi caratteri della risposta (usa text per vedere il contenuto decompresso)
        try:
            # requests dovrebbe già aver decompresso automaticamente
            content_preview = page.text[:500] if hasattr(page, 'text') and page.text else str(page.content[:500])
            # Rimuovi caratteri non stampabili per il debug
            content_preview_clean = ''.join(c for c in content_preview if c.isprintable() or c.isspace())[:200]
            print(f"📄 [get_roms_from_platform_page] Anteprima risposta (primi 200 caratteri stampabili): {content_preview_clean}", file=sys.stderr)
        except Exception as e:
            print(f"⚠️ [get_roms_from_platform_page] Errore lettura contenuto: {e}", file=sys.stderr)
        
        # Usa page.text se disponibile (già decompresso e decodificato), altrimenti page.content
        if hasattr(page, 'text') and page.text:
            soup = BeautifulSoup(page.text, 'html.parser')
        else:
            soup = BeautifulSoup(page.content, 'html.parser', from_encoding=page.encoding or 'utf-8')
        
        # Debug: verifica se la pagina è stata caricata correttamente
        page_title = soup.find('title')
        if page_title:
            title_text = page_title.get_text(strip=True)
            print(f"📄 [get_roms_from_platform_page] Titolo pagina: {title_text[:100]}", file=sys.stderr)
        
        # Verifica se è una pagina Cloudflare
        page_text_lower = soup.get_text().lower()
        if 'just a moment' in page_text_lower or 'cloudflare' in page_text_lower or 'checking your browser' in page_text_lower:
            print(f"⚠️ [get_roms_from_platform_page] Possibile blocco Cloudflare per {page_url}", file=sys.stderr)
            print(f"   Primi 500 caratteri della pagina: {page_text_lower[:500]}", file=sys.stderr)
        
        # Debug: verifica struttura HTML
        all_divs = soup.find_all('div')
        print(f"🔍 [get_roms_from_platform_page] Totale div trovati: {len(all_divs)}", file=sys.stderr)
        
        # Cerca classi che contengono "archive" o "rom"
        archive_divs = soup.find_all('div', class_=lambda x: x and ('archive' in str(x).lower() or 'rom' in str(x).lower()))
        print(f"🔍 [get_roms_from_platform_page] Div con 'archive' o 'rom' nella classe: {len(archive_divs)}", file=sys.stderr)
        
        # Cerca le card delle ROM
        # Le card sono in <div class="archive-left">
        rom_cards = soup.find_all('div', class_='archive-left')
        print(f"🔍 [get_roms_from_platform_page] Trovate {len(rom_cards)} card con classe 'archive-left'", file=sys.stderr)
        
        if not rom_cards:
            # Fallback: cerca qualsiasi div con classe che contiene "archive"
            rom_cards = soup.find_all('div', class_=lambda x: x and 'archive' in x.lower())
            print(f"🔍 [get_roms_from_platform_page] Trovate {len(rom_cards)} card con 'archive' nel nome classe", file=sys.stderr)
        
        # Se ancora non trova, cerca link diretti a /roms/
        if not rom_cards:
            all_links = soup.find_all('a', href=re.compile(r'/roms/[^/]+/[^/]+\.html'))
            print(f"🔍 [get_roms_from_platform_page] Trovati {len(all_links)} link diretti a /roms/", file=sys.stderr)
        
        for card in rom_cards:
            try:
                # Estrai il link alla ROM (dentro <a href="...">)
                link = card.find('a', href=re.compile(r'/roms/[^/]+/[^/]+\.html'))
                if not link:
                    continue
                
                rom_url = link.get('href', '')
                if not rom_url:
                    continue
                
                # Normalizza URL
                if not rom_url.startswith('http'):
                    rom_url = 'https://romsfun.com' + rom_url
                
                # Estrai il titolo da <h3 class="h6 font-weight-semibold text-truncate text-body mt-3">
                title_elem = card.find('h3', class_=lambda x: x and 'h6' in x and 'font-weight-semibold' in x and 'text-truncate' in x)
                if not title_elem:
                    # Fallback: cerca qualsiasi h3
                    title_elem = card.find('h3')
                if not title_elem:
                    # Ultimo fallback: usa il testo del link
                    title_elem = link
                
                title = title_elem.get_text(strip=True)
                if not title:
                    continue
                
                # Estrai l'immagine da <img> dentro <div class="archive-cover-container">
                # L'immagine ha classe "attachment-thumbnail size-thumbnail wp-post-image"
                cover_container = card.find('div', class_=lambda x: x and 'archive-cover-container' in x)
                img = None
                if cover_container:
                    img = cover_container.find('img', class_=lambda x: x and 'attachment-thumbnail' in x)
                if not img:
                    # Fallback: cerca qualsiasi img nella card
                    img = card.find('img')
                box_image = None
                if img:
                    srcset = img.get('srcset', '')
                    src = img.get('src', '')
                    
                    if srcset:
                        urls = re.findall(r'(\S+)\s+\d+w', srcset)
                        if urls:
                            box_image = urls[-1]
                        else:
                            box_image = src
                    else:
                        box_image = src
                    
                    # Normalizza URL
                    if box_image:
                        if box_image.startswith('//'):
                            box_image = 'https:' + box_image
                        elif box_image.startswith('/'):
                            box_image = 'https://romsfun.com' + box_image
                        elif not box_image.startswith('http'):
                            box_image = 'https://romsfun.com/' + box_image
                
                # Estrai lo slug dall'URL
                slug_match = re.search(r'/roms/[^/]+/([^/]+)\.html', rom_url)
                if slug_match:
                    rom_slug = slug_match.group(1)
                else:
                    # Fallback: normalizza il titolo
                    rom_slug = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-')
                
                # Mappa la piattaforma al mother_code
                if platform_slug:
                    # Usa la piattaforma fornita
                    platform_mother_code = map_romsfun_slug_to_mother_code(platform_slug, source_dir)
                else:
                    # Estrai la piattaforma dall'URL (es. /roms/nintendo-wii/title.html -> nintendo-wii)
                    platform_match = re.search(r'/roms/([^/]+)/', rom_url)
                    if platform_match:
                        extracted_platform_slug = platform_match.group(1)
                        platform_mother_code = map_romsfun_slug_to_mother_code(extracted_platform_slug, source_dir)
                    else:
                        platform_mother_code = 'unknown'
                
                # Le regioni non sono disponibili nella lista, le otterremo dal dettaglio se necessario
                # Per ora, lasciamo vuoto
                regions = []
                
                rom = {
                    'slug': rom_slug,
                    'rom_id': rom_url,  # Usa l'URL come ID
                    'title': title,
                    'platform': platform_mother_code,
                    'boxart_url': box_image,  # Deprecato
                    'boxart_urls': [box_image] if box_image else [],  # Deprecato
                    'box_image': box_image,  # Nuovo formato
                    'screen_image': None,  # Non disponibile nella lista
                    'regions': regions,
                    'links': []  # I link di download si ottengono dal dettaglio
                }
                
                roms.append(rom)
                
            except Exception as e:
                print(f"⚠️ [get_roms_from_platform_page] Errore estrazione ROM da card: {e}", file=sys.stderr)
                continue
        
    except requests.exceptions.RequestException as e:
        print(f"❌ [get_roms_from_platform_page] Errore richiesta: {e}", file=sys.stderr)
    except Exception as e:
        import traceback
        print(f"❌ [get_roms_from_platform_page] Errore: {e}\n{traceback.format_exc()}", file=sys.stderr)
    
    return roms

def get_entry(params: Dict[str, Any], source_dir: str) -> str:
    """
    Ottiene una entry specifica per slug
    Lo slug può essere:
    - URL completo: https://romsfun.com/roms/nintendo-wii/new-super-mario-bros-58600.html
    - Slug parziale: new-super-mario-bros-58600
    - Slug con estensione: new-super-mario-bros-58600.html
    """
    slug = params.get("slug")
    
    if not slug:
        return json.dumps({"error": "Slug non fornito"})
    
    # Normalizza lo slug per costruire l'URL (tutto in minuscolo)
    slug_normalized = slug.strip().lower()
    
    # Costruisci l'URL della pagina ROM (tutti gli slug negli URL devono essere in minuscolo)
    if slug_normalized.startswith('http'):
        # URL completo, normalizza la parte path
        page_url = re.sub(r'(https?://romsfun\.com/roms/)([^/?#]+)', 
                         lambda m: m.group(1) + m.group(2).lower(), 
                         slug_normalized)
    elif slug_normalized.startswith('/'):
        # URL relativo, normalizza tutto in minuscolo
        page_url = 'https://romsfun.com' + slug_normalized.lower()
    else:
        # Slug parziale - potrebbe essere solo il nome o l'URL completo senza protocollo
        if '.html' in slug_normalized:
            # Ha già l'estensione
            if slug_normalized.startswith('roms/'):
                page_url = 'https://romsfun.com/' + slug_normalized.lower()
            else:
                # Assumiamo che sia nella forma: piattaforma/nome-rom.html
                # Normalizza tutto in minuscolo
                page_url = f'https://romsfun.com/roms/{slug_normalized.lower()}'
        else:
            # Solo il nome, normalizza in minuscolo
            page_url = f'https://romsfun.com/roms/{slug_normalized.lower()}.html'
    
    try:
        entry = get_rom_entry_by_url(page_url, source_dir)
        if entry:
            return json.dumps({"entry": entry})
        else:
            return json.dumps({"entry": None})
    except Exception as e:
        import traceback
        error_msg = f"{str(e)}\n{traceback.format_exc()}"
        print(f"❌ [get_entry] Errore: {error_msg}", file=sys.stderr)
        return json.dumps({"error": error_msg})


def get_rom_entry_by_url(page_url: str, source_dir: str) -> Optional[Dict[str, Any]]:
    """
    Ottiene i dettagli completi di una ROM dall'URL della pagina
    """
    try:
        # Usa session per mantenere i cookie tra le richieste
        session = requests.Session()
        
        # Simula navigazione browser reale per bypassare Cloudflare:
        # 1. Prima visita la homepage per ottenere cookie Cloudflare iniziali
        import time
        try:
            home_headers = get_browser_headers()
            print(f"🌐 [get_rom_entry_by_url] Visita homepage per ottenere cookie Cloudflare...", file=sys.stderr)
            home_response = session.get('https://romsfun.com/', headers=home_headers, timeout=15)
            
            # Se riceviamo 403 anche sulla homepage, aspetta e riprova
            if home_response.status_code == 403:
                print(f"⚠️ [get_rom_entry_by_url] 403 sulla homepage, attendo 3 secondi...", file=sys.stderr)
                time.sleep(3)
                home_headers = get_browser_headers()  # Nuovo User-Agent
                home_response = session.get('https://romsfun.com/', headers=home_headers, timeout=15)
            
            home_response.raise_for_status()
            print(f"✅ [get_rom_entry_by_url] Cookie Cloudflare ottenuti dalla homepage", file=sys.stderr)
            # Aspetta un po' per simulare comportamento umano
            time.sleep(1)
        except Exception as e:
            print(f"⚠️ [get_rom_entry_by_url] Errore visita homepage: {e}, continuo comunque...", file=sys.stderr)
            # Continua comunque, ma aspetta un po'
            time.sleep(1)
        
        # 2. Estrai la piattaforma dall'URL per usarla come Referer
        # Es: /roms/nintendo-wii/... -> https://romsfun.com/roms/nintendo-wii/
        referer_url = None
        match = re.search(r'(https://romsfun\.com/roms/[^/]+/)', page_url)
        if match:
            referer_url = match.group(1)
        else:
            referer_url = 'https://romsfun.com/roms/'
        
        # 3. Visita la pagina ROM con Referer (simula click da lista)
        # Aggiungi un piccolo delay per simulare comportamento umano
        time.sleep(0.5)
        
        print(f"🔗 [get_rom_entry_by_url] Richiesta pagina: {page_url}", file=sys.stderr)
        headers = get_browser_headers(referer=referer_url)
        page = session.get(page_url, headers=headers, timeout=20)
        
        # Se riceviamo 403, prova a riprovare con un delay più lungo e nuovo User-Agent
        if page.status_code == 403:
            print(f"⚠️ [get_rom_entry_by_url] Ricevuto 403 per {page_url}, attendo 3 secondi e riprovo...", file=sys.stderr)
            time.sleep(3)
            # Riprova con un nuovo User-Agent
            headers = get_browser_headers(referer=referer_url)
            page = session.get(page_url, headers=headers, timeout=20)
        
        page.raise_for_status()
        soup = BeautifulSoup(page.content, 'html.parser')
        
        # 1. Estrai il titolo
        title = None
        h1 = soup.find('h1', class_=lambda x: x and 'h3' in x and 'font-weight-bold' in x and 'text-primary' in x)
        if h1:
            title = h1.get_text(strip=True)
        
        if not title:
            # Fallback: cerca nel title tag
            title_tag = soup.find('title')
            if title_tag:
                title = title_tag.get_text(strip=True)
                # Rimuovi suffissi comuni come "ROM - Nintendo Wii Game"
                title = re.sub(r'\s*ROM\s*-\s*.*$', '', title, flags=re.IGNORECASE)
        
        if not title:
            return None
        
        # 2. Estrai la piattaforma dalla tabella
        platform = None
        platform_slug = None
        table = soup.find('table', class_=lambda x: x and 'table' in x and 'table-striped' in x)
        if table:
            rows = table.find_all('tr')
            for row in rows:
                th = row.find('th')
                td = row.find('td')
                if not th or not td:
                    continue
                
                label = th.get_text(strip=True)
                if 'Console' in label or 'Platform' in label:
                    link = td.find('a')
                    if link:
                        platform = link.get_text(strip=True)
                        # Estrai lo slug dalla piattaforma dall'URL
                        platform_href = link.get('href', '')
                        if platform_href:
                            match = re.search(r'/roms/([^/?#]+)', platform_href)
                            if match:
                                platform_slug = match.group(1)
                        break
        
        # Mappa la piattaforma al mother_code
        if platform_slug:
            platform_mother_code = map_romsfun_slug_to_mother_code(platform_slug, source_dir)
        else:
            platform_mother_code = 'unknown'
        
        # 3. Estrai box art
        box_image = None
        box_img = soup.find('img', class_=lambda x: x and 'attachment-post-thumbnail' in x)
        if box_img:
            srcset = box_img.get('srcset', '')
            src = box_img.get('src', '')
            
            # Prendi l'URL più grande da srcset
            if srcset:
                urls = re.findall(r'(\S+)\s+\d+w', srcset)
                if urls:
                    box_image = urls[-1]  # Ultimo = più grande
                else:
                    box_image = src
            else:
                box_image = src
            
            # Normalizza URL
            if box_image:
                if box_image.startswith('//'):
                    box_image = 'https:' + box_image
                elif box_image.startswith('/'):
                    box_image = 'https://romsfun.com' + box_image
                elif not box_image.startswith('http'):
                    box_image = 'https://romsfun.com/' + box_image
        
        # 4. Estrai regioni dalla tabella
        regions = []
        # 5. Estrai file size dalla tabella (prima di creare i link)
        file_size = None
        
        if table:
            rows = table.find_all('tr')
            for row in rows:
                th = row.find('th')
                td = row.find('td')
                if not th or not td:
                    continue
                
                label = th.get_text(strip=True)
                if 'Region' in label:
                    links = td.find_all('a')
                    for link in links:
                        region_text = link.get_text(strip=True)
                        if region_text:
                            regions.append(region_text)
                elif 'File size' in label or 'Size' in label:
                    file_size = td.get_text(strip=True)
        
        # 6. Estrai link di download
        # IMPORTANTE: Il pulsante "Download now" ha un href reale che DEVE essere letto, non calcolato
        # Es: href="/download/pokemon-moon-black-2-52778" (il numero post_id è specifico e non calcolabile)
        download_links = []
        
        # STEP 1: Cerca il pulsante "Download now" e leggi il suo href reale
        download_button = soup.find('a', href=re.compile(r'/download/'), class_=lambda x: x and 'btn' in x and 'btn-primary' in x)
        if not download_button:
            # Prova pattern alternativi per il pulsante
            download_button = soup.find('a', class_=lambda x: x and 'btn' in x and ('primary' in x.lower() or 'download' in x.lower()), href=re.compile(r'/download/'))
        
        if download_button:
            download_page_url = download_button.get('href', '')
            if download_page_url:
                if not download_page_url.startswith('http'):
                    download_page_url = 'https://romsfun.com' + download_page_url
                
                print(f"✅ [get_rom_entry_by_url] Pulsante Download now trovato: {download_page_url}", file=sys.stderr)
                
                # STEP 2: Segui il link alla pagina intermedia che contiene la tabella
                try:
                    # Usa la stessa session per mantenere i cookie
                    download_page = session.get(download_page_url, headers=get_browser_headers(referer=page_url), timeout=10)
                    download_page.raise_for_status()
                    download_soup = BeautifulSoup(download_page.content, 'html.parser')
                    
                    # STEP 3: Cerca la tabella nella pagina intermedia
                    # La tabella contiene le righe con i link numerati
                    download_table = download_soup.find('table')
                    
                    if download_table:
                        print(f"✅ [get_rom_entry_by_url] Tabella download trovata nella pagina intermedia", file=sys.stderr)
                        # Estrai i link dalla tabella
                        rows = download_table.find_all('tr')
                        for row in rows:
                            link_elem = row.find('a', href=re.compile(r'/download/[^/]+/\d+'))
                            if link_elem:
                                sub_link_url = link_elem.get('href', '')
                                if not sub_link_url.startswith('http'):
                                    sub_link_url = 'https://romsfun.com' + sub_link_url
                                
                                # Estrai informazioni dal testo della riga
                                row_text = row.get_text(strip=True)
                                
                                # Prova a estrarre formato e dimensione dal testo
                                format_type = None
                                if 'RVZ' in row_text or 'rvz' in sub_link_url.lower():
                                    format_type = 'RVZ'
                                elif 'WBFS' in row_text or 'wbfs' in sub_link_url.lower():
                                    format_type = 'WBFS'
                                elif 'ISO' in row_text or 'iso' in sub_link_url.lower():
                                    format_type = 'ISO'
                                elif 'NDS' in row_text or 'nds' in sub_link_url.lower():
                                    format_type = 'NDS'
                                
                                # Estrai dimensione se presente nel testo della riga
                                size_match = re.search(r'(\d+\.?\d*\s*(?:MB|GB|KB|M|G))', row_text, re.IGNORECASE)
                                link_size_str = size_match.group(0) if size_match else None
                                
                                print(f"🔗 [get_rom_entry_by_url] Trovato link download: {sub_link_url}", file=sys.stderr)
                                
                                # Segui il link per ottenere l'URL finale del file (con token)
                                # Nota: questa pagina ha un countdown di ~15 secondi prima di mostrare il pulsante
                                final_download_url = get_final_download_url(sub_link_url, session, download_page_url)
                                
                                if final_download_url:
                                    # Se abbiamo ottenuto l'URL finale, estrai anche la dimensione dalla pagina finale
                                    if not link_size_str:
                                        try:
                                            final_page = session.get(sub_link_url, headers=get_browser_headers(referer=download_page_url), timeout=20)
                                            final_soup = BeautifulSoup(final_page.content, 'html.parser')
                                            download_btn = final_soup.find('a', id='download')
                                            if download_btn:
                                                btn_text = download_btn.get_text(strip=True)
                                                size_in_btn = re.search(r'\((\d+\.?\d*\s*(?:MB|GB|KB|M|G))\)', btn_text, re.IGNORECASE)
                                                if size_in_btn:
                                                    link_size_str = size_in_btn.group(1)
                                        except:
                                            pass  # Non critico
                                    
                                    # Determina se serve WebView (se l'URL finale non è stato trovato)
                                    # L'URL finale dovrebbe essere https://sto.romsfast.com/...?token=...
                                    requires_webview = not (final_download_url.startswith('http') and 'sto.romsfast.com' in final_download_url)
                                    
                                    download_links.append({
                                        'name': link_elem.get_text(strip=True) or f'Download {format_type or "ROM"}',
                                        'type': 'ROM',
                                        'format': format_type or 'unknown',
                                        'url': final_download_url,  # URL completo con token (es: https://sto.romsfast.com/...?token=...) oppure URL intermedio
                                        'size': None,
                                        'size_str': link_size_str or file_size if file_size else None,
                                        'requires_webview': requires_webview  # True se l'URL richiede WebView per countdown
                                    })
                    
                    # Se non abbiamo trovato link nella tabella, prova fallback
                    if not download_links:
                        print(f"⚠️ [get_rom_entry_by_url] Tabella non trovata, provo fallback", file=sys.stderr)
                        # Cerca link diretti nella pagina intermedia
                        direct_links = download_soup.find_all('a', href=re.compile(r'/download/[^/]+/\d+'))
                        for link_elem in direct_links:
                            sub_link_url = link_elem.get('href', '')
                            if not sub_link_url.startswith('http'):
                                sub_link_url = 'https://romsfun.com' + sub_link_url
                            
                            final_download_url = get_final_download_url(sub_link_url, session, download_page_url)
                            if final_download_url:
                                # Determina se serve WebView
                                requires_webview = not (final_download_url.startswith('http') and 'sto.romsfast.com' in final_download_url)
                                
                                download_links.append({
                                    'name': link_elem.get_text(strip=True) or 'Download',
                                    'type': 'ROM',
                                    'format': 'unknown',
                                    'url': final_download_url,
                                    'size': None,
                                    'size_str': file_size if file_size else None,
                                    'requires_webview': requires_webview
                                })
                                break  # Prendi solo il primo
                        
                except Exception as e:
                    print(f"⚠️ [get_rom_entry_by_url] Errore estrazione link download: {e}", file=sys.stderr)
                    import traceback
                    print(f"   Traceback: {traceback.format_exc()}", file=sys.stderr)
                    # In caso di errore, usa almeno il link originale (richiede WebView)
                    download_links.append({
                        'name': download_button.get_text(strip=True) or 'Download',
                        'type': 'ROM',
                        'format': 'unknown',
                        'url': download_page_url,
                        'size': None,
                        'size_str': file_size if file_size else None,
                        'requires_webview': True  # Richiede WebView per gestire il processo
                    })
        else:
            print(f"⚠️ [get_rom_entry_by_url] Pulsante Download now non trovato", file=sys.stderr)
        
        # 7. Costruisci lo slug dalla URL
        slug_match = re.search(r'/roms/[^/]+/([^/]+)\.html', page_url)
        if slug_match:
            rom_slug = slug_match.group(1)
        else:
            # Fallback: usa il titolo normalizzato
            rom_slug = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-')
        
        # 8. Costruisci la risposta nel formato RomEntry
        entry = {
            'slug': rom_slug,
            'rom_id': page_url,  # Usa l'URL come ID
            'title': title,
            'platform': platform_mother_code,
            'boxart_url': box_image,  # Deprecato, mantenuto per compatibilità
            'boxart_urls': [box_image] if box_image else [],  # Deprecato
            'box_image': box_image,  # Nuovo formato
            'screen_image': None,  # Non disponibile nella pagina di dettaglio
            'regions': regions,
            'links': download_links
        }
        
        return entry
        
    except requests.exceptions.RequestException as e:
        print(f"❌ [get_rom_entry_by_url] Errore richiesta: {e}", file=sys.stderr)
        return None
    except Exception as e:
        import traceback
        print(f"❌ [get_rom_entry_by_url] Errore: {e}\n{traceback.format_exc()}", file=sys.stderr)
        return None

def get_platform_display_name(romsfun_slug: str) -> str:
    """
    Converte uno slug ROMsFun nel nome esatto come appare nelle pagine ROMsFun
    Mapping completo basato sui dati ufficiali di ROMsFun
    Tutti i confronti sono case-insensitive come in Tottodrillo
    """
    if not romsfun_slug:
        return romsfun_slug
    
    # Normalizza lo slug per il matching (case-insensitive)
    romsfun_slug_lower = romsfun_slug.lower().strip()
    
    # Mapping completo slug -> nome esatto come appare su ROMsFun
    # Basato sui dati ufficiali estratti dal sito
    platform_name_mapping = {
        "3do": "3DO",
        "atari-2600": "Atari 2600",
        "atari-jaguar": "Atari Jaguar",
        "amiga": "Commodore - Amiga",
        "fujitsu-fm-towns-marty": "Fujitsu FM Towns Marty",
        "game-boy": "Game Boy (GB)",
        "game-boy-color": "Game Boy Color",
        "gamecube": "GameCube",
        "gamepark-gp32": "GamePark GP32",
        "game-boy-advance": "GBA",
        "mame": "MAME",
        "microsoft-msx": "Microsoft MSX",
        "microsoft-msx2": "Microsoft MSX2",
        "ms-dos": "MS-DOS",
        "mugen": "MUGEN",
        "nec-pc-9801": "NEC PC-9801",
        "nec-pc-fx": "NEC PC-FX",
        "turbografx-cd": "NEC TurboGrafx CD",
        "turbografx": "NEC TurboGrafx-16",
        "neo-geo": "Neo Geo",
        "neo-geo-aes": "Neo Geo AES",
        "neo-geo-cd": "Neo Geo CD",
        "snk-neo-geo-pocket-color": "Neo Geo Pocket Color",
        "nes": "NES",
        "nintendo-3ds": "Nintendo 3DS",
        "nintendo-64": "Nintendo 64",
        "nintendo-ds": "Nintendo DS",
        "nintendo-pokemon-mini": "Nintendo Pokemon Mini",
        "nintendo-virtual-boy": "Nintendo Virtual Boy",
        "nintendo-wii": "Nintendo Wii",
        "nokia-n-gage": "Nokia N-Gage",
        "openbor": "OpenBOR",
        "other": "Other",
        "ouya": "Ouya",
        "philips-cd-i": "Philips CD-i",
        "playstation": "PlayStation (PS)",
        "ps-vita": "PS Vita",
        "playstation-2": "PS2",
        "ps3": "PS3",
        "playstation-4": "PS4",
        "playstation-portable": "PSP",
        "scummvm": "ScummVM",
        "32x": "SEGA 32X",
        "sega-cd": "Sega CD",
        "dreamcast": "Sega Dreamcast",
        "sega-game-gear": "Sega Game Gear",
        "lindbergh": "Sega Lindbergh",
        "sega-master-system": "Sega Master System",
        "sega-naomi": "Sega Naomi",
        "sega-pico": "Sega Pico",
        "sega-ringedge": "Sega RingEdge",
        "sega-ringedge-2": "Sega RingEdge 2",
        "sega-saturn": "Sega Saturn",
        "sega-sg-1000": "Sega SG-1000",
        "sega-genesis": "Sega Genesis",
        "sharp-x1": "Sharp - X1",
        "sharp-x68000": "Sharp X68000",
        "super-nintendo": "SNES",
        "teknoparrot": "TeknoParrot",
        "wii-u": "Wii U",
        "windows": "Windows",
        "windows-3x": "Windows 3x",
        "wonderswan": "WonderSwan",
        "wonderswan-color": "WonderSwan Color",
        "xbox": "XBOX",
        "xbox-360": "Xbox 360"
    }
    
    # Normalizza lo slug per il matching (case-insensitive)
    slug_lower = romsfun_slug.lower()
    
    # Se c'è un mapping specifico, usalo (case-insensitive)
    for slug_key, name in platform_name_mapping.items():
        if slug_key.lower().strip() == romsfun_slug_lower:
            return name
    
    # Fallback: converte lo slug in un nome leggibile
    # Lista di abbreviazioni che devono rimanere in maiuscolo
    abbreviations = {'fm', 'cd', 'ds', 'gb', 'gbc', 'gba', 'ps', 'ps2', 'ps3', 'ps4', 'psp', 'psvita', 
                    'xbox', 'xbox360', 'msx', 'msx1', 'msx2', 'pc', 'pc98', 'pcfx', 'pcengine', 'pcenginecd',
                    '3do', '3ds', 'n3ds', 'n64', 'nes', 'snes', 'nds', 'wii', 'wiiu', 'gc', 'ngc',
                    '32x', 'cdi', 'cdimono1', 'mame', 'scummvm', 'mugen', 'dos', 'windows', 'windows3x'}
    
    # Rimuovi i trattini e capitalizza ogni parola
    words = romsfun_slug.split('-')
    formatted_words = []
    
    for word in words:
        # Se la parola è un'abbreviazione, mantienila in maiuscolo
        if word.lower() in abbreviations:
            formatted_words.append(word.upper())
        else:
            # Capitalizza la prima lettera
            formatted_words.append(word.capitalize())
    
    name = ' '.join(formatted_words)
    return name

def get_platforms(source_dir: str) -> str:
    """Ottiene le piattaforme disponibili usando platform_mapping.json"""
    # Carica il mapping dalla source directory
    mapping = load_platform_mapping(source_dir)
    
    # Crea un dizionario con i mother_code come chiavi
    platforms = {}
    
    for mother_code, romsfun_slugs in mapping.items():
        # Prendi il primo slug ROMsFun
        if isinstance(romsfun_slugs, list):
            slug = romsfun_slugs[0] if romsfun_slugs else mother_code
        else:
            slug = romsfun_slugs
        
        # Converti lo slug in un nome completo leggibile
        # Es: "fujitsu-fm-towns-marty" -> "Fujitsu FM Towns Marty"
        display_name = get_platform_display_name(slug)
        
        platforms[mother_code] = {
            "name": display_name
        }
    
    # Restituisci nel formato atteso: {"platforms": {...}}
    response = {
        "platforms": platforms
    }
    
    return json.dumps(response)

def get_regions() -> str:
    """Ottiene le regioni disponibili"""
    # TODO: Implementare il recupero delle regioni dal sito
    # Per ora restituiamo regioni comuni
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

