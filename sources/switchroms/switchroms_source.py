"""
Wrapper Python per integrare SwitchRoms.io come sorgente Tottodrillo
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

def get_random_ua() -> str:
    """Genera un User-Agent casuale"""
    user_agents = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15"
    ]
    import random
    return random.choice(user_agents)

def get_browser_headers(referer: Optional[str] = None) -> Dict[str, str]:
    """Genera header browser-like per le richieste"""
    headers = {
        "User-Agent": get_random_ua(),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9,it;q=0.8",
        "Accept-Encoding": "gzip, deflate",
        "Connection": "keep-alive",
        "Upgrade-Insecure-Requests": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none" if not referer else "same-origin",
        "Cache-Control": "max-age=0"
    }
    if referer:
        headers["Referer"] = referer
    return headers

def search_roms(params: Dict[str, Any], source_dir: str) -> str:
    """
    Cerca ROM su SwitchRoms.io
    - Se search_key è vuoto: usa la pagina categoria https://switchroms.io/nintendo-switch-games/
    - Se search_key è presente: usa la ricerca https://switchroms.io/?s=query
    """
    try:
        search_key = params.get("search_key", "").strip()
        max_results = params.get("max_results", 50)
        page = params.get("page", 1)
        
        # Costruisci URL
        if not search_key:
            # Nessuna query: usa la pagina categoria
            if page == 1:
                search_url = "https://switchroms.io/nintendo-switch-games/"
            else:
                search_url = f"https://switchroms.io/nintendo-switch-games/page/{page}/"
            print(f"🔍 [search_roms] Caricamento pagina categoria (pagina {page}): {search_url}", file=sys.stderr)
        else:
            # Query presente: usa la ricerca
            if page == 1:
                search_url = f"https://switchroms.io/?s={urllib.parse.quote(search_key)}"
            else:
                search_url = f"https://switchroms.io/page/{page}/?s={urllib.parse.quote(search_key)}"
            print(f"🔍 [search_roms] Cercando: {search_key} su {search_url}", file=sys.stderr)
        
        # Fai la richiesta
        session = requests.Session()
        headers = get_browser_headers()
        response = session.get(search_url, headers=headers, timeout=15)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Trova tutti i blocchi ROM
        rom_blocks = soup.find_all('a', class_=lambda x: x and 'wrapper-item-title' in x and 'title-recommended' in x)
        
        roms = []
        for block in rom_blocks[:max_results]:
            try:
                # URL della pagina ROM
                rom_url = block.get('href', '')
                if not rom_url:
                    continue
                
                # Titolo
                title_elem = block.find('h3', class_='title-post')
                title = title_elem.get_text(strip=True) if title_elem else ""
                
                # Immagine
                img_elem = block.find('img', class_='bg-img')
                box_image = img_elem.get('src', '') if img_elem else None
                
                # Versione e dimensione (es: "1.0.1 + 9.8 GB")
                version_elem = block.find('span', class_='text-cat version')
                version_info = version_elem.get_text(strip=True) if version_elem else ""
                
                # Publisher e genere (es: "Nintendo + Adventure")
                publisher_elem = block.find_all('span', class_='text-cat version')
                publisher_info = ""
                if len(publisher_elem) > 1:
                    publisher_info = publisher_elem[1].get_text(strip=True) if publisher_elem[1] else ""
                
                # Estrai versione e dimensione dalla stringa
                version = None
                size_str = None
                if version_info:
                    # Pattern: "1.0.1 + 9.8 GB" o "V1.0.1 | 130 MB"
                    version_match = re.search(r'[Vv]?(\d+\.\d+(?:\.\d+)?)', version_info)
                    if version_match:
                        version = version_match.group(1)
                    
                    size_match = re.search(r'(\d+\.?\d*\s*(?:MB|GB|KB))', version_info, re.IGNORECASE)
                    if size_match:
                        size_str = size_match.group(1)
                
                # Estrai publisher e genere
                publisher = None
                genre = None
                if publisher_info:
                    parts = publisher_info.split('+')
                    if len(parts) >= 1:
                        publisher = parts[0].strip()
                    if len(parts) >= 2:
                        genre = parts[1].strip()
                
                # Slug dall'URL (es: "mario-luigi-brothership-1" da "https://switchroms.io/mario-luigi-brothership-1/")
                slug_match = re.search(r'/([^/]+)/?$', rom_url)
                slug = slug_match.group(1) if slug_match else rom_url.split('/')[-1]
                
                roms.append({
                    "slug": slug,
                    "rom_id": rom_url,  # Usiamo l'URL completo come rom_id
                    "title": title,
                    "platform": "switch",  # Sempre Switch
                    "box_image": box_image,
                    # SwitchRoms non ha screenshot, non includere screen_image
                    "regions": [],  # Non disponibili nella lista
                    "links": []  # Verranno recuperati in get_entry
                })
            except Exception as e:
                print(f"⚠️ [search_roms] Errore parsing ROM: {e}", file=sys.stderr)
                continue
        
        print(f"✅ [search_roms] Trovate {len(roms)} ROM", file=sys.stderr)
        
        # Estrai informazioni sulla paginazione
        total_pages = 1
        try:
            # Cerca il blocco di paginazione
            nav_links = soup.find('div', class_='nav-links')
            if nav_links:
                # Trova tutti i link di pagina
                page_links = nav_links.find_all('a', class_='page-numbers')
                page_numbers = []
                for link in page_links:
                    href = link.get('href', '')
                    # Estrai il numero di pagina dall'URL (es: /page/24/ o /nintendo-switch-games/page/24/)
                    match = re.search(r'/page/(\d+)/', href)
                    if match:
                        page_numbers.append(int(match.group(1)))
                
                # Trova anche il numero nella pagina corrente
                current_page_elem = nav_links.find('span', class_='page-numbers current')
                if current_page_elem:
                    current_text = current_page_elem.get_text(strip=True)
                    try:
                        current_num = int(current_text)
                        page_numbers.append(current_num)
                    except:
                        pass
                
                if page_numbers:
                    total_pages = max(page_numbers)
                    print(f"📄 [search_roms] Paginazione: pagina {page} di {total_pages}", file=sys.stderr)
                else:
                    # Se non ci sono link di pagina, probabilmente c'è solo una pagina
                    total_pages = 1
        except Exception as e:
            print(f"⚠️ [search_roms] Errore estrazione paginazione: {e}", file=sys.stderr)
        
        return json.dumps({
            "roms": roms,
            "total_results": len(roms) * total_pages if total_pages > 1 else len(roms),  # Stima
            "current_results": len(roms),
            "current_page": page,
            "total_pages": total_pages
        })
        
    except Exception as e:
        import traceback
        error_msg = f"{str(e)}\n{traceback.format_exc()}"
        print(f"❌ [search_roms] Errore: {error_msg}", file=sys.stderr)
        return json.dumps({"error": error_msg})

def get_entry(params: Dict[str, Any], source_dir: str) -> str:
    """
    Ottiene i dettagli completi di una ROM
    """
    try:
        slug = params.get("slug", "")
        include_download_links = params.get("include_download_links", True)  # Default True per retrocompatibilità
        if not slug:
            return json.dumps({"entry": None})
        
        # Costruisci URL (lo slug può essere un URL completo o solo lo slug)
        if slug.startswith("http"):
            page_url = slug
        else:
            page_url = f"https://switchroms.io/{slug}/"
        
        print(f"🔗 [get_entry] Recupero dettagli: {page_url}", file=sys.stderr)
        
        # Verifica che lo slug sia valido per SwitchRoms (non dovrebbe contenere riferimenti ad altre piattaforme)
        # SwitchRoms ha solo ROM per Nintendo Switch, quindi se lo slug contiene riferimenti ad altre piattaforme,
        # probabilmente è un errore e dovremmo restituire None
        if not slug.startswith("http") and ("n3ds" in slug.lower() or "wii" in slug.lower() or "ds" in slug.lower() or "nes" in slug.lower()):
            print(f"⚠️ [get_entry] Slug non valido per SwitchRoms (contiene riferimenti ad altre piattaforme): {slug}", file=sys.stderr)
            return json.dumps({"entry": None})
        
        # Fai la richiesta alla pagina ROM
        session = requests.Session()
        headers = get_browser_headers()
        try:
            response = session.get(page_url, headers=headers, timeout=15)
            
            # Se la pagina non esiste (404), probabilmente lo slug non è valido per SwitchRoms
            if response.status_code == 404:
                print(f"⚠️ [get_entry] Pagina non trovata (404) per: {page_url}", file=sys.stderr)
                return json.dumps({"entry": None})
            
            response.raise_for_status()
        except requests.exceptions.HTTPError as e:
            if e.response is not None and e.response.status_code == 404:
                print(f"⚠️ [get_entry] Pagina non trovata (404) per: {page_url}", file=sys.stderr)
                return json.dumps({"entry": None})
            raise
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Estrai titolo
        title = None
        # Cerca prima in h1 con classe h1-title (titolo principale del gioco)
        h1 = soup.find('h1', class_='h1-title')
        if h1:
            title = h1.get_text(strip=True)
            # Rimuovi "NSP, XCI Switch Rom V..." dal titolo
            title = re.sub(r'\s+NSP.*$', '', title, flags=re.IGNORECASE)
            title = re.sub(r'\s+XCI.*$', '', title, flags=re.IGNORECASE)
            title = re.sub(r'\s+Switch\s+Rom.*$', '', title, flags=re.IGNORECASE)
            title = re.sub(r'\s+V\d+\.\d+.*$', '', title, flags=re.IGNORECASE)
            title = re.sub(r'\s+Free\s+Download.*$', '', title, flags=re.IGNORECASE)
        
        # Fallback: cerca nel title della pagina
        if not title or title.lower() in ['switch rom', 'switchrom']:
            title_tag = soup.find('title')
            if title_tag:
                title = title_tag.get_text(strip=True)
                # Rimuovi "Switch Rom" o simili dal titolo
                title = re.sub(r'\s*-\s*Switch\s*Rom.*$', '', title, flags=re.IGNORECASE)
                title = re.sub(r'\s*\|\s*Switch\s*Rom.*$', '', title, flags=re.IGNORECASE)
                title = re.sub(r'\s+NSP.*$', '', title, flags=re.IGNORECASE)
                title = re.sub(r'\s+XCI.*$', '', title, flags=re.IGNORECASE)
        
        # Pulisci il titolo
        if title:
            title = title.strip()
        
        # Estrai immagine box art
        box_image = None
        
        # Prima cerca l'immagine che ha l'alt text corrispondente al titolo
        if title:
            # Crea una versione semplificata del titolo per il matching
            title_words = re.sub(r'[^\w\s]', '', title.lower()).split()
            if title_words:
                # Cerca immagini nell'articolo principale
                article = soup.find('article') or soup.find('main')
                if article:
                    for img in article.find_all('img', src=re.compile(r'\.(jpg|jpeg|png|webp)', re.I)):
                        alt_text = img.get('alt', '').lower()
                        # Verifica se almeno 2 parole del titolo sono nell'alt text
                        matches = sum(1 for word in title_words[:3] if word in alt_text)
                        if matches >= 2:
                            box_image = img.get('src', '')
                            print(f"✅ [get_entry] Immagine trovata per matching alt text: {box_image[:80]}...", file=sys.stderr)
                            break
        
        # Fallback: cerca l'immagine principale nell'articolo (prima immagine che non è bg-img)
        if not box_image:
            article = soup.find('article') or soup.find('main')
            if article:
                for img in article.find_all('img', src=re.compile(r'\.(jpg|jpeg|png|webp)', re.I)):
                    # Salta immagini con classe bg-img (sono quelle dei giochi correlati)
                    if 'bg-img' not in (img.get('class', []) or []):
                        box_image = img.get('src', '')
                        if box_image:
                            print(f"✅ [get_entry] Immagine trovata nell'articolo: {box_image[:80]}...", file=sys.stderr)
                            break
        
        # Ultimo fallback: prima immagine valida
        if not box_image:
            img_elem = soup.find('img', src=re.compile(r'\.(jpg|jpeg|png|webp)', re.I))
            if img_elem:
                box_image = img_elem.get('src', '')
        
        # Trova il pulsante Download
        download_button = soup.find('a', href=re.compile(r'/\?download$'))
        download_url = None
        if download_button:
            download_url = download_button.get('href', '')
            if not download_url.startswith('http'):
                download_url = f"https://switchroms.io{download_url}"
        
        download_links = []
        
        # Estrai download links solo se richiesto (per performance in home screen e ricerca)
        if download_url and include_download_links:
            print(f"✅ [get_entry] Pulsante Download trovato: {download_url}", file=sys.stderr)
            
            # Visita la pagina di download
            download_response = session.get(download_url, headers=get_browser_headers(referer=page_url), timeout=15)
            download_response.raise_for_status()
            download_soup = BeautifulSoup(download_response.content, 'html.parser')
            
            # Trova tutti i link nella tabella download-list
            download_list = download_soup.find('div', class_='download-list')
            if download_list:
                link_buttons = download_list.find_all('a', class_='a-link-button')
                
                for link_button in link_buttons:
                    try:
                        link_url = link_button.get('href', '')
                        if not link_url.startswith('http'):
                            link_url = f"https://switchroms.io{link_url}"
                        
                        # Estrai informazioni dal testo del link
                        link_title_elem = link_button.find('span', class_='link-title')
                        link_text = link_title_elem.get_text(strip=True) if link_title_elem else ""
                        
                        # Parse: "NSP ROM | 9.8 GB | Buzzheavier" o "[UPDATE] NSP ROM V1.0.1 | 130 MB | Buzzheavier"
                        format_type = None
                        size_str = None
                        host = None
                        
                        # Estrai formato (NSP, XCI, UPDATE)
                        if 'NSP' in link_text.upper():
                            format_type = 'NSP'
                        elif 'XCI' in link_text.upper():
                            format_type = 'XCI'
                        
                        # Estrai dimensione
                        size_match = re.search(r'(\d+\.?\d*\s*(?:MB|GB|KB))', link_text, re.IGNORECASE)
                        if size_match:
                            size_str = size_match.group(1)
                        
                        # Estrai host (ultima parte dopo |)
                        parts = link_text.split('|')
                        if len(parts) >= 3:
                            host = parts[-1].strip()
                        
                        # Nome del link
                        link_name = link_text if link_text else f"{format_type or 'ROM'} Download"
                        
                        # Estrai SEMPRE l'URL finale "click here" dalla pagina di download
                        # Il WebView aprirà direttamente questo URL invece della pagina intermedia
                        final_url = None
                        try:
                            print(f"🔍 [get_entry] Estrazione URL finale da: {link_url}", file=sys.stderr)
                            link_response = session.get(link_url, headers=get_browser_headers(referer=download_url), timeout=10, allow_redirects=True)
                            link_response.raise_for_status()
                            link_soup = BeautifulSoup(link_response.content, 'html.parser')
                            
                            # Cerca il link "click here" nella pagina (pattern: <a href="..." rel="noopener nofollow" target="_blank">)
                            # Cerca prima per rel="noopener" o "noopener nofollow"
                            click_here_link = link_soup.find('a', href=re.compile(r'https?://'), rel=lambda x: x and 'noopener' in x.lower())
                            if not click_here_link:
                                print(f"⚠️ [get_entry] Link con rel='noopener' non trovato, provo fallback...", file=sys.stderr)
                                # Fallback: cerca qualsiasi link esterno nella sezione aligncenter
                                align_center = link_soup.find('p', class_='aligncenter')
                                if align_center:
                                    click_here_link = align_center.find('a', href=re.compile(r'https?://'))
                            
                            if click_here_link:
                                final_url = click_here_link.get('href', '')
                                if final_url and final_url.startswith('http'):
                                    print(f"✅ [get_entry] URL finale estratto: {final_url[:100]}...", file=sys.stderr)
                                else:
                                    print(f"⚠️ [get_entry] URL estratto non valido: {final_url}", file=sys.stderr)
                                    final_url = None
                            else:
                                print(f"⚠️ [get_entry] Link 'click here' non trovato nella pagina", file=sys.stderr)
                                # Debug: stampa alcuni link trovati nella pagina
                                all_links = link_soup.find_all('a', href=re.compile(r'https?://'))
                                print(f"🔍 [get_entry] Trovati {len(all_links)} link esterni nella pagina", file=sys.stderr)
                                if all_links:
                                    for i, link in enumerate(all_links[:3]):  # Primi 3 per debug
                                        href = link.get('href', '')
                                        rel = link.get('rel', [])
                                        print(f"  Link {i+1}: {href[:80]}... (rel: {rel})", file=sys.stderr)
                        except Exception as e:
                            print(f"❌ [get_entry] Errore estrazione URL finale per {link_url}: {e}", file=sys.stderr)
                            import traceback
                            print(f"   Traceback: {traceback.format_exc()}", file=sys.stderr)
                        
                        # Per SwitchRoms, apriamo il WebView direttamente sul link "click here" se disponibile
                        # Se non disponibile, usiamo la pagina intermedia come fallback
                        # Il WebView intercetterà il download quando parte
                        download_url_to_use = final_url if final_url else link_url
                        if not final_url:
                            print(f"⚠️ [get_entry] Usando URL intermedio come fallback: {link_url}", file=sys.stderr)
                        
                        download_links.append({
                            "name": link_name,
                            "type": "ROM",
                            "format": format_type or "unknown",
                            "url": download_url_to_use,  # Usa URL finale se disponibile, altrimenti intermedio
                            "size": None,
                            "size_str": size_str,
                            "requires_webview": True  # Sempre true per SwitchRoms: apri WebView per intercettare download
                        })
                    except Exception as e:
                        print(f"⚠️ [get_entry] Errore parsing link download: {e}", file=sys.stderr)
                        continue
                
                print(f"✅ [get_entry] Trovati {len(download_links)} link download", file=sys.stderr)
        
        # Estrai regioni dalla tabella Language
        regions = []
        try:
            # Cerca tutte le righe tr e trova quella con Language
            language_row = None
            for tr in soup.find_all('tr'):
                th = tr.find('th')
                if th and 'Language' in th.get_text():
                    language_row = tr
                    break
            
            if language_row:
                language_td = language_row.find('td', class_='text-muted')
                if language_td:
                    languages_text = language_td.get_text(strip=True)
                    # Parse le lingue separate da virgola
                    languages = [lang.strip() for lang in languages_text.split(',')]
                    print(f"🌐 [get_entry] Lingue trovate: {', '.join(languages)}", file=sys.stderr)
                    
                    # Mappa le lingue ai codici regione di Tottodrillo
                    language_to_regions = {
                        'english': ['US', 'UK'],
                        'french': ['FR', 'EU'],
                        'german': ['DE', 'EU'],
                        'italian': ['IT', 'EU'],
                        'japanese': ['JP'],
                        'dutch': ['NL', 'EU'],
                        'korean': ['KR'],
                        'portuguese': ['BR', 'EU'],
                        'russian': ['RU'],  # RU potrebbe non essere supportato, ma lo includiamo
                        'spanish': ['ES', 'EU'],
                        'chinese': ['CN'],
                        'simplified chinese': ['CN'],
                        'traditional chinese': ['CN', 'TW'],  # Traditional Chinese -> Taiwan
                    }
                    
                    # Converti le lingue in regioni
                    region_codes = set()
                    for lang in languages:
                        lang_lower = lang.lower().strip()
                        matched = False
                        # Cerca match esatto o parziale
                        for lang_key, region_codes_list in language_to_regions.items():
                            if lang_key == lang_lower or lang_key in lang_lower or lang_lower in lang_key:
                                region_codes.update(region_codes_list)
                                matched = True
                                break
                        # Se non trovato, prova match parziale più flessibile
                        if not matched:
                            if 'english' in lang_lower:
                                region_codes.update(['US', 'UK'])
                            elif 'chinese' in lang_lower:
                                region_codes.add('CN')
                                if 'traditional' in lang_lower:
                                    region_codes.add('TW')
                    
                    # Converti in lista e ordina
                    regions = sorted(list(region_codes))
                    print(f"✅ [get_entry] Regioni estratte: {', '.join(regions)}", file=sys.stderr)
        except Exception as e:
            print(f"⚠️ [get_entry] Errore estrazione regioni: {e}", file=sys.stderr)
        
        # Estrai publisher e genere dalla pagina (se disponibili)
        publisher = None
        genre = None
        
        entry = {
            "slug": slug.split('/')[-1].rstrip('/'),
            "rom_id": page_url,
            "title": title or "Unknown",
            "platform": "switch",
            "box_image": box_image,
            # SwitchRoms non ha screenshot, non includere screen_image
            "regions": regions,
            "links": download_links
        }
        
        return json.dumps({"entry": entry})
        
    except Exception as e:
        import traceback
        error_msg = f"{str(e)}\n{traceback.format_exc()}"
        print(f"❌ [get_entry] Errore: {error_msg}", file=sys.stderr)
        return json.dumps({"error": error_msg})

def get_platforms(source_dir: str) -> str:
    """Ritorna le piattaforme supportate (solo Switch)"""
    platforms = {
        "switch": {
            "name": "Nintendo Switch",
            "brand": "Nintendo"
        }
    }
    return json.dumps({"platforms": platforms})

def get_regions() -> str:
    """Ritorna le regioni supportate (non disponibili su SwitchRoms)"""
    regions = {}
    return json.dumps({"regions": regions})

def execute(params_json: str) -> str:
    """
    Entry point principale per l'esecuzione dello script
    """
    try:
        params = json.loads(params_json)
        method = params.get("method", "")
        source_dir = params.get("source_dir", os.path.dirname(__file__))
        
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

if __name__ == "__main__":
    # Test locale
    if len(sys.argv) > 1:
        params_json = sys.argv[1]
        result = execute(params_json)
        print(result)
    else:
        print("Usage: python switchroms_source.py <params_json>")

