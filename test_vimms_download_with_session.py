#!/usr/bin/env python3
"""
Script di test per verificare il download da Vimm's Lair con sessione
Simula il comportamento del DownloadWorker Android
"""
import requests
import urllib3
import os
import tempfile

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def test_download_with_session(rom_slug, media_id, alt=None, output_dir="/tmp"):
    """Testa il download visitando prima la pagina ROM per ottenere i cookie"""
    
    # Crea una sessione per mantenere i cookie
    session = requests.Session()
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
    
    print("=" * 80)
    print("TEST DOWNLOAD VIMM'S LAIR CON SESSIONE")
    print("=" * 80)
    print()
    
    # Step 1: Visita la pagina ROM per ottenere i cookie di sessione
    rom_page_url = f"https://vimm.net/vault/{rom_slug}"
    print(f"📄 Step 1: Visita pagina ROM per ottenere cookie")
    print(f"   URL: {rom_page_url}")
    print()
    
    try:
        page_response = session.get(rom_page_url, headers=headers, verify=False, timeout=30)
        print(f"📊 Risposta pagina ROM:")
        print(f"   Status Code: {page_response.status_code}")
        print(f"   Cookie ricevuti:")
        for cookie in session.cookies:
            print(f"      • {cookie.name}: {cookie.value[:50]}...")
        print()
        
        if page_response.status_code != 200:
            print(f"❌ Errore nel visitare pagina ROM: {page_response.status_code}")
            return False
        
        print("✅ Pagina ROM visitata con successo, cookie ottenuti")
        print()
        
    except Exception as e:
        print(f"❌ Errore nel visitare pagina ROM: {e}")
        import traceback
        traceback.print_exc()
        return False
    
    # Step 2: Costruisci l'URL di download
    # Il form usa dl3.vimm.net secondo l'analisi della pagina
    # Prova prima con dl3, poi con dl2 come fallback
    download_url = f"https://dl3.vimm.net/?mediaId={media_id}"
    if alt is not None:
        download_url += f"&alt={alt}"
    
    print(f"📥 Step 2: Download file")
    print(f"   URL: {download_url}")
    print(f"   MediaId: {media_id}")
    if alt is not None:
        print(f"   Alt: {alt}")
    print()
    
    # Step 3: Fai il download usando la stessa sessione (con i cookie)
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
        print(f"   Content-Length: {download_response.headers.get('Content-Length', 'N/A')} bytes")
        content_length = download_response.headers.get('Content-Length')
        if content_length:
            size_mb = int(content_length) / (1024 * 1024)
            print(f"   Dimensione: {size_mb:.2f} MB")
        print()
        
        if download_response.status_code == 200:
            # Verifica il Content-Type
            content_type = download_response.headers.get('Content-Type', '')
            
            if 'application' in content_type or 'octet-stream' in content_type or 'zip' in content_type or 'download' in content_type.lower():
                print("✅ Download avviato con successo!")
                
                # Leggi i primi byte per verificare (senza seek, usa iter_content)
                first_chunk = None
                for chunk in download_response.iter_content(chunk_size=1024):
                    first_chunk = chunk
                    break
                
                if first_chunk:
                    print(f"✅ Primi 1024 bytes ricevuti correttamente")
                    print(f"   Magic bytes (hex): {first_chunk[:16].hex()}")
                    
                    # Verifica il tipo di file
                    if first_chunk.startswith(b'PK'):
                        print(f"   Tipo file: ZIP/WBFS (inizia con PK)")
                    elif first_chunk.startswith(b'Rar!'):
                        print(f"   Tipo file: RAR")
                    elif first_chunk.startswith(b'7z'):
                        print(f"   Tipo file: 7Z")
                    elif first_chunk.startswith(b'WII'):
                        print(f"   Tipo file: WBFS (header WII)")
                    else:
                        print(f"   Tipo file: Sconosciuto (potrebbe essere valido)")
                    
                    # Prova a scaricare un piccolo file di test (primi 1MB)
                    # Riavvia il download per il file completo
                    download_response2 = session.get(
                        download_url,
                        headers=download_headers,
                        stream=True,
                        verify=False,
                        allow_redirects=True,
                        timeout=60
                    )
                    
                    test_file = os.path.join(output_dir, f"test_download_{media_id}.bin")
                    print()
                    print(f"💾 Test download parziale (primi 1MB) in: {test_file}")
                    
                    downloaded = 0
                    max_bytes = 1024 * 1024  # 1MB
                    
                    with open(test_file, 'wb') as f:
                        for chunk in download_response2.iter_content(chunk_size=8192):
                            if chunk:
                                f.write(chunk)
                                downloaded += len(chunk)
                                if downloaded >= max_bytes:
                                    break
                    
                    file_size = os.path.getsize(test_file)
                    print(f"✅ File di test scaricato: {file_size} bytes ({file_size / 1024:.2f} KB)")
                    print(f"   File salvato in: {test_file}")
                    
                    # Pulisci il file di test
                    os.remove(test_file)
                    print(f"   File di test rimosso")
                    
                    return True
                else:
                    print("⚠️  Nessun dato ricevuto")
                    return False
            else:
                print(f"⚠️  Content-Type inaspettato: {content_type}")
                print(f"   Response preview: {download_response.text[:500]}")
                return False
        else:
            print(f"❌ Errore: Status Code {download_response.status_code}")
            print(f"   Response: {download_response.text[:500]}")
            return False
            
    except Exception as e:
        print(f"❌ Errore durante il download: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    # Test con i dati della ROM 17874
    test_cases = [
        ("17874", "58792", None, "Version 1.1 - .wbfs"),
        ("17874", "58792", "1", "Version 1.1 - .rvz"),
        ("17874", "9409", None, "Version 1.2 - .wbfs"),
        ("17874", "9409", "1", "Version 1.2 - .rvz"),
    ]
    
    success_count = 0
    for rom_slug, media_id, alt, description in test_cases:
        print(f"🔍 Test: {description}")
        print("-" * 80)
        success = test_download_with_session(rom_slug, media_id, alt)
        if success:
            success_count += 1
        print()
        print("=" * 80)
        print()
    
    print(f"📊 RIEPILOGO: {success_count}/{len(test_cases)} download riusciti")
    print("=" * 80)

