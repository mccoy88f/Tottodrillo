#!/usr/bin/env python3
"""
Script di test per verificare il download della ROM 91176
"""
import sys
import json
import os
import requests
import urllib3
from bs4 import BeautifulSoup

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'VimmsDownloader-master'))

from vimms_source import execute, get_rom_entry_by_uri

def test_rom_91176():
    """Testa la ROM 91176"""
    rom_uri = "/vault/91176"
    rom_slug = "91176"
    
    print("=" * 80)
    print("TEST ROM 91176")
    print("=" * 80)
    print()
    
    # Step 1: Ottieni informazioni ROM
    print("📋 Step 1: Ottieni informazioni ROM")
    print("-" * 80)
    
    params = {"method": "getEntry", "slug": rom_slug}
    result_json = execute(json.dumps(params))
    result = json.loads(result_json)
    
    entry = result.get("entry")
    if not entry:
        print("❌ ROM non trovata")
        return
    
    print(f"✅ ROM trovata: {entry.get('title', 'N/A')}")
    print(f"   Platform: {entry.get('platform', 'N/A')}")
    print(f"   Slug: {entry.get('slug', 'N/A')}")
    print()
    
    links = entry.get('links', [])
    if not links:
        print("❌ Nessun link di download disponibile")
        return
    
    print(f"📥 Link di download disponibili: {len(links)}")
    for i, link in enumerate(links, 1):
        print(f"   {i}. {link.get('name', 'N/A')}")
        print(f"      URL: {link.get('url', 'N/A')}")
        print(f"      Format: {link.get('format', 'N/A')}")
        print(f"      Size: {link.get('size_str', 'N/A')}")
    print()
    
    # Step 2: Test download con sessione
    print("=" * 80)
    print("📥 Step 2: Test download con sessione")
    print("=" * 80)
    print()
    
    # Crea una sessione
    session = requests.Session()
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
    
    # Visita la pagina ROM
    rom_page_url = f"https://vimm.net/vault/{rom_slug}"
    print(f"📄 Visita pagina ROM: {rom_page_url}")
    
    try:
        page_response = session.get(rom_page_url, headers=headers, verify=False, timeout=30)
        print(f"   Status Code: {page_response.status_code}")
        print(f"   Cookie ricevuti: {len(session.cookies)} cookie(s)")
        for cookie in session.cookies:
            print(f"      • {cookie.name}: {cookie.value[:30]}...")
        print()
        
        if page_response.status_code != 200:
            print(f"❌ Errore nel visitare pagina ROM")
            return
        
        print("✅ Pagina ROM visitata con successo")
        print()
        
    except Exception as e:
        print(f"❌ Errore: {e}")
        return
    
    # Testa il primo link di download
    first_link = links[0]
    download_url = first_link.get('url')
    
    if not download_url:
        print("❌ URL di download non disponibile")
        return
    
    print(f"📥 Test download:")
    print(f"   Nome: {first_link.get('name', 'N/A')}")
    print(f"   URL: {download_url}")
    print()
    
    # Estrai mediaId dall'URL o dalla pagina HTML
    media_id = None
    alt = None
    
    if "mediaId=" in download_url:
        # Estrai dall'URL
        media_id = download_url.split("mediaId=")[1].split("&")[0]
        if "alt=" in download_url:
            alt = download_url.split("alt=")[1].split("&")[0]
    else:
        # Prova a estrarre dalla pagina HTML
        soup = BeautifulSoup(page_response.content, 'html.parser')
        form = soup.find('form', id='dl_form')
        if form:
            media_id_elem = form.find(attrs={'name': 'mediaId'})
            media_id = media_id_elem.get('value') if media_id_elem else None
            alt_elem = form.find(attrs={'name': 'alt'})
            alt = alt_elem.get('value') if alt_elem else None
    
    if not media_id:
        print("❌ Impossibile trovare mediaId")
        return
    
    # Costruisci URL corretto se necessario
    if not download_url.startswith("http"):
        download_url = f"https://dl3.vimm.net/?mediaId={media_id}"
        if alt and alt != "0":
            download_url += f"&alt={alt}"
    
    print(f"   URL corretto: {download_url}")
    print()
    
    # Fai il download
    download_headers = {
        'User-Agent': headers['User-Agent'],
        'Referer': rom_page_url,
        'Accept': headers['Accept']
    }
    
    try:
        download_response = session.get(
            download_url,
            headers=download_headers,
            stream=True,
            verify=False,
            allow_redirects=True,
            timeout=60
        )
        
        print(f"📊 Risposta download:")
        print(f"   Status Code: {download_response.status_code}")
        print(f"   Content-Type: {download_response.headers.get('Content-Type', 'N/A')}")
        content_length = download_response.headers.get('Content-Length')
        if content_length:
            size_mb = int(content_length) / (1024 * 1024)
            print(f"   Content-Length: {content_length} bytes ({size_mb:.2f} MB)")
        print()
        
        if download_response.status_code == 200:
            content_type = download_response.headers.get('Content-Type', '')
            if 'application' in content_type or 'octet-stream' in content_type or 'zip' in content_type or '7z' in content_type:
                print("✅ Download avviato con successo!")
                
                # Leggi i primi byte
                first_chunk = None
                for chunk in download_response.iter_content(chunk_size=1024):
                    first_chunk = chunk
                    break
                
                if first_chunk:
                    print(f"✅ Primi 1024 bytes ricevuti")
                    print(f"   Magic bytes (hex): {first_chunk[:16].hex()}")
                    
                    if first_chunk.startswith(b'PK'):
                        print(f"   Tipo file: ZIP/WBFS")
                    elif first_chunk.startswith(b'7z'):
                        print(f"   Tipo file: 7Z")
                    elif first_chunk.startswith(b'Rar!'):
                        print(f"   Tipo file: RAR")
                    else:
                        print(f"   Tipo file: Sconosciuto")
                    
                    print()
                    print("✅ TEST COMPLETATO CON SUCCESSO!")
                    return True
                else:
                    print("⚠️  Nessun dato ricevuto")
            else:
                print(f"⚠️  Content-Type inaspettato: {content_type}")
                print(f"   Response: {download_response.text[:500]}")
        else:
            print(f"❌ Errore: Status Code {download_response.status_code}")
            print(f"   Response: {download_response.text[:500]}")
            
    except Exception as e:
        print(f"❌ Errore durante il download: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    test_rom_91176()

