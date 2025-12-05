#!/usr/bin/env python3
"""
Script per scaricare il file completo della ROM 91176
"""
import requests
import urllib3
import os
import sys

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def download_full_file(rom_slug, media_id, alt=None, output_dir="/tmp/test_downloads"):
    """Scarica il file completo"""
    
    # Crea la cartella di output
    os.makedirs(output_dir, exist_ok=True)
    
    # Crea una sessione
    session = requests.Session()
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
    
    print("=" * 80)
    print("DOWNLOAD COMPLETO ROM 91176")
    print("=" * 80)
    print()
    
    # Step 1: Visita la pagina ROM
    rom_page_url = f"https://vimm.net/vault/{rom_slug}"
    print(f"📄 Step 1: Visita pagina ROM")
    print(f"   URL: {rom_page_url}")
    
    try:
        page_response = session.get(rom_page_url, headers=headers, verify=False, timeout=30)
        print(f"   Status Code: {page_response.status_code}")
        print(f"   Cookie ricevuti: {len(session.cookies)}")
        print()
        
        if page_response.status_code != 200:
            print(f"❌ Errore nel visitare pagina ROM")
            return False
        
        print("✅ Pagina ROM visitata con successo")
        print()
        
    except Exception as e:
        print(f"❌ Errore: {e}")
        return False
    
    # Step 2: Costruisci URL download
    download_url = f"https://dl2.vimm.net/?mediaId={media_id}"
    if alt is not None and alt != "0":
        download_url += f"&alt={alt}"
    
    print(f"📥 Step 2: Download file completo")
    print(f"   URL: {download_url}")
    print(f"   MediaId: {media_id}")
    if alt:
        print(f"   Alt: {alt}")
    print()
    
    # Step 3: Download
    download_headers = {
        'User-Agent': headers['User-Agent'],
        'Referer': rom_page_url,
        'Accept': headers['Accept']
    }
    
    # Determina il nome del file
    filename = f"rom_{rom_slug}_mediaId_{media_id}"
    if alt:
        filename += f"_alt_{alt}"
    filename += ".bin"  # Estensione generica, verrà determinata dal Content-Type
    
    output_file = os.path.join(output_dir, filename)
    
    print(f"💾 File di output: {output_file}")
    print()
    
    try:
        download_response = session.get(
            download_url,
            headers=download_headers,
            stream=True,
            verify=False,
            allow_redirects=True,
            timeout=300  # 5 minuti per file grandi
        )
        
        print(f"📊 Risposta download:")
        print(f"   Status Code: {download_response.status_code}")
        print(f"   Content-Type: {download_response.headers.get('Content-Type', 'N/A')}")
        content_length = download_response.headers.get('Content-Length')
        if content_length:
            size_mb = int(content_length) / (1024 * 1024)
            print(f"   Content-Length: {content_length} bytes ({size_mb:.2f} MB)")
        print()
        
        if download_response.status_code != 200:
            print(f"❌ Errore: Status Code {download_response.status_code}")
            print(f"   Response: {download_response.text[:500]}")
            return False
        
        # Determina estensione dal Content-Type
        content_type = download_response.headers.get('Content-Type', '')
        if 'zip' in content_type:
            output_file = output_file.replace('.bin', '.zip')
        elif '7z' in content_type or 'x-7z' in content_type:
            output_file = output_file.replace('.bin', '.7z')
        elif 'wbfs' in content_type:
            output_file = output_file.replace('.bin', '.wbfs')
        elif 'rvz' in content_type:
            output_file = output_file.replace('.bin', '.rvz')
        
        print(f"📥 Download in corso...")
        print(f"   File: {output_file}")
        print()
        
        downloaded = 0
        total_size = int(content_length) if content_length else 0
        
        with open(output_file, 'wb') as f:
            for chunk in download_response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    
                    # Mostra progresso ogni MB
                    if total_size > 0 and downloaded % (1024 * 1024) < 8192:
                        progress = (downloaded / total_size) * 100
                        print(f"   Progresso: {downloaded / (1024*1024):.2f} MB / {total_size / (1024*1024):.2f} MB ({progress:.1f}%)", end='\r')
        
        print()  # Nuova riga dopo il progresso
        print()
        
        # Verifica il file
        file_size = os.path.getsize(output_file)
        print(f"✅ Download completato!")
        print(f"   File: {output_file}")
        print(f"   Dimensione: {file_size} bytes ({file_size / 1024:.2f} KB)")
        
        if total_size > 0:
            if file_size == total_size:
                print(f"   ✅ Dimensione corretta (atteso: {total_size} bytes)")
            else:
                print(f"   ⚠️  Dimensione diversa (atteso: {total_size} bytes, scaricato: {file_size} bytes)")
        
        # Verifica magic bytes
        with open(output_file, 'rb') as f:
            first_bytes = f.read(16)
            print(f"   Magic bytes: {first_bytes.hex()}")
            if first_bytes.startswith(b'PK'):
                print(f"   Tipo file: ZIP")
            elif first_bytes.startswith(b'7z'):
                print(f"   Tipo file: 7Z")
            elif first_bytes.startswith(b'Rar!'):
                print(f"   Tipo file: RAR")
            else:
                print(f"   Tipo file: Sconosciuto")
        
        print()
        print(f"✅ File scaricato con successo in: {output_file}")
        return True
        
    except Exception as e:
        print(f"❌ Errore durante il download: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    # ROM 91176 - Space Invaders
    success = download_full_file("91176", "87402", None)
    
    if success:
        print("=" * 80)
        print("✅ TEST COMPLETATO CON SUCCESSO!")
        print("=" * 80)
    else:
        print("=" * 80)
        print("❌ TEST FALLITO")
        print("=" * 80)
        sys.exit(1)

