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
    Formato URL: https://switchroms.io/?s=query
    """
    try:
        search_key = params.get("search_key", "").strip()
        max_results = params.get("max_results", 50)
        page = params.get("page", 1)
        
        if not search_key:
            return json.dumps({
                "roms": [],
                "total_results": 0,
                "current_results": 0,
                "current_page": page,
                "total_pages": 1
            })
        
        # Costruisci URL di ricerca
        search_url = f"https://switchroms.io/?s={urllib.parse.quote(search_key)}"
        if page > 1:
            search_url += f"&paged={page}"
        
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
                    "screen_image": None,  # SwitchRoms non ha screenshot
                    "regions": [],  # Non disponibili nella lista
                    "links": []  # Verranno recuperati in get_entry
                })
            except Exception as e:
                print(f"⚠️ [search_roms] Errore parsing ROM: {e}", file=sys.stderr)
                continue
        
        print(f"✅ [search_roms] Trovate {len(roms)} ROM", file=sys.stderr)
        
        return json.dumps({
            "roms": roms,
            "total_results": len(roms),
            "current_results": len(roms),
            "current_page": page,
            "total_pages": 1  # Non possiamo sapere il totale senza fare altre richieste
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
        if not slug:
            return json.dumps({"entry": None})
        
        # Costruisci URL (lo slug può essere un URL completo o solo lo slug)
        if slug.startswith("http"):
            page_url = slug
        else:
            page_url = f"https://switchroms.io/{slug}/"
        
        print(f"🔗 [get_entry] Recupero dettagli: {page_url}", file=sys.stderr)
        
        # Fai la richiesta alla pagina ROM
        session = requests.Session()
        headers = get_browser_headers()
        response = session.get(page_url, headers=headers, timeout=15)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Estrai titolo
        title = None
        h1 = soup.find('h1')
        if h1:
            title = h1.get_text(strip=True)
        else:
            # Fallback: cerca nel title della pagina
            title_tag = soup.find('title')
            if title_tag:
                title = title_tag.get_text(strip=True)
                # Rimuovi "Switch Rom" o simili dal titolo
                title = re.sub(r'\s*-\s*Switch\s*Rom.*$', '', title, flags=re.IGNORECASE)
        
        # Estrai immagine box art
        box_image = None
        img_elem = soup.find('img', class_='bg-img')
        if img_elem:
            box_image = img_elem.get('src', '')
        else:
            # Cerca altre immagini
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
        
        if download_url:
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
                        
                        # Prova a estrarre l'URL finale direttamente dalla pagina di download
                        final_url = None
                        try:
                            link_response = session.get(link_url, headers=get_browser_headers(referer=download_url), timeout=10, allow_redirects=True)
                            link_response.raise_for_status()
                            link_soup = BeautifulSoup(link_response.content, 'html.parser')
                            
                            # Cerca il link "click here" nella pagina (pattern: <a href="..." rel="noopener nofollow" target="_blank">)
                            # Cerca prima per rel="noopener" o "noopener nofollow"
                            click_here_link = link_soup.find('a', href=re.compile(r'https?://'), rel=lambda x: x and 'noopener' in x.lower())
                            if not click_here_link:
                                # Fallback: cerca qualsiasi link esterno nella sezione aligncenter
                                align_center = link_soup.find('p', class_='aligncenter')
                                if align_center:
                                    click_here_link = align_center.find('a', href=re.compile(r'https?://'))
                            
                            if click_here_link:
                                final_url = click_here_link.get('href', '')
                                if final_url and final_url.startswith('http'):
                                    print(f"✅ [get_entry] URL finale estratto: {final_url[:80]}...", file=sys.stderr)
                                else:
                                    final_url = None
                        except Exception as e:
                            print(f"⚠️ [get_entry] Impossibile estrarre URL finale per {link_url}: {e}", file=sys.stderr)
                        
                        download_links.append({
                            "name": link_name,
                            "type": "ROM",
                            "format": format_type or "unknown",
                            "url": final_url if final_url else link_url,  # Usa URL finale se disponibile, altrimenti intermedio
                            "size": None,
                            "size_str": size_str,
                            "requires_webview": not final_url  # WebView solo se non abbiamo l'URL finale
                        })
                    except Exception as e:
                        print(f"⚠️ [get_entry] Errore parsing link download: {e}", file=sys.stderr)
                        continue
                
                print(f"✅ [get_entry] Trovati {len(download_links)} link download", file=sys.stderr)
        
        # Estrai regioni (non disponibili su SwitchRoms, ma possiamo provare a dedurle dal titolo)
        regions = []
        
        # Estrai publisher e genere dalla pagina (se disponibili)
        publisher = None
        genre = None
        
        entry = {
            "slug": slug.split('/')[-1].rstrip('/'),
            "rom_id": page_url,
            "title": title or "Unknown",
            "platform": "switch",
            "box_image": box_image,
            "screen_image": None,
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

