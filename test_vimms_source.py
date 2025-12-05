#!/usr/bin/env python3
"""
Script di test per vimms_source.py
Testa tutte le funzionalità: ricerca, dettagli, download immagine e file
"""

import json
import os
import sys
import requests
from pathlib import Path

# Aggiungi il percorso del modulo vimms_source
sys.path.insert(0, str(Path(__file__).parent / "VimmsDownloader-master"))

from vimms_source import (
    execute,
    get_system_search_roms,
    get_rom_entry_by_uri,
    SYSTEM_MAPPING
)

# Crea cartella temp
TEMP_DIR = Path("temp_test")
TEMP_DIR.mkdir(exist_ok=True)
print(f"📁 Cartella temp creata: {TEMP_DIR.absolute()}")

def test_search_all_snes():
    """Test 1: Cerca tutti i giochi SNES"""
    print("\n" + "="*60)
    print("TEST 1: Ricerca tutti i giochi SNES")
    print("="*60)
    
    # Trova il sistema SNES in SYSTEM_MAPPING
    snes_system = None
    for system, mother_code in SYSTEM_MAPPING.items():
        if "snes" in system.lower() or "super nintendo" in system.lower():
            snes_system = system
            break
    
    if not snes_system:
        print("❌ SNES non trovato in SYSTEM_MAPPING")
        print("Sistemi disponibili:", list(SYSTEM_MAPPING.keys()))
        return None
    
    print(f"🔍 Sistema trovato: {snes_system}")
    print("🔍 Cercando tutti i giochi SNES (senza filtro di ricerca)...")
    
    # Cerca senza filtro per ottenere tutti i giochi
    params = {
        "method": "searchRoms",
        "search_key": "",  # Stringa vuota per ottenere tutti i giochi
        "platforms": ["snes"],  # Mother code per SNES
        "max_results": 20,  # Limita a 20 per il test
        "page": 1
    }
    
    result_json = execute(json.dumps(params))
    result = json.loads(result_json)
    
    if "results" in result:
        roms = result["results"]
        print(f"✅ Trovati {len(roms)} giochi SNES")
        if roms:
            print(f"\n📋 Primi 5 giochi trovati:")
            for i, rom in enumerate(roms[:5], 1):
                print(f"  {i}. {rom.get('title', 'N/A')} (slug: {rom.get('slug', 'N/A')})")
            return roms[0] if roms else None
    else:
        print(f"❌ Errore nella ricerca: {result}")
        return None

def test_search_mario_nes():
    """Test 2: Cerca 'mario' su NES"""
    print("\n" + "="*60)
    print("TEST 2: Ricerca 'mario' su NES")
    print("="*60)
    
    params = {
        "method": "searchRoms",
        "search_key": "mario",
        "platforms": ["nes"],  # Mother code per NES
        "max_results": 10,
        "page": 1
    }
    
    result_json = execute(json.dumps(params))
    result = json.loads(result_json)
    
    if "results" in result:
        roms = result["results"]
        print(f"✅ Trovati {len(roms)} giochi con 'mario' su NES")
        if roms:
            print(f"\n📋 Giochi trovati:")
            for i, rom in enumerate(roms, 1):
                print(f"  {i}. {rom.get('title', 'N/A')} (slug: {rom.get('slug', 'N/A')})")
            return roms[0] if roms else None
    else:
        print(f"❌ Errore nella ricerca: {result}")
        return None

def test_get_entry_details(rom):
    """Test 3: Ottieni dettagli completi di una ROM"""
    print("\n" + "="*60)
    print("TEST 3: Dettagli completi ROM")
    print("="*60)
    
    if not rom:
        print("❌ Nessuna ROM disponibile per il test")
        return None
    
    slug = rom.get('slug')
    print(f"🔍 Recuperando dettagli per slug: {slug}")
    
    params = {
        "method": "getEntry",
        "slug": slug
    }
    
    result_json = execute(json.dumps(params))
    result = json.loads(result_json)
    
    if "entry" in result and result["entry"]:
        entry = result["entry"]
        print(f"✅ Dettagli recuperati:")
        print(f"  📝 Titolo: {entry.get('title', 'N/A')}")
        print(f"  🎮 Piattaforma: {entry.get('platform', 'N/A')}")
        print(f"  🖼️  Box Art URL: {entry.get('boxart_url', 'N/A')}")
        print(f"  🔗 Link download: {len(entry.get('links', []))} link disponibili")
        for link in entry.get('links', []):
            print(f"     - {link.get('name', 'N/A')}: {link.get('url', 'N/A')}")
        return entry
    else:
        print(f"❌ Errore nel recupero dettagli: {result}")
        return None

def test_download_image(entry):
    """Test 4: Scarica l'immagine della box art"""
    print("\n" + "="*60)
    print("TEST 4: Download immagine box art")
    print("="*60)
    
    if not entry:
        print("❌ Nessuna entry disponibile per il test")
        return None
    
    boxart_url = entry.get('boxart_url')
    if not boxart_url:
        print("❌ Nessun URL box art disponibile")
        return None
    
    print(f"📥 Scaricando immagine da: {boxart_url}")
    
    try:
        response = requests.get(boxart_url, timeout=30, verify=False)
        response.raise_for_status()
        
        # Determina estensione file
        ext = "jpg"
        if ".png" in boxart_url.lower():
            ext = "png"
        elif ".gif" in boxart_url.lower():
            ext = "gif"
        
        image_path = TEMP_DIR / f"boxart_{entry.get('slug', 'unknown')}.{ext}"
        with open(image_path, 'wb') as f:
            f.write(response.content)
        
        print(f"✅ Immagine scaricata: {image_path} ({len(response.content)} bytes)")
        return image_path
    except Exception as e:
        print(f"❌ Errore nel download immagine: {e}")
        return None

def test_download_file(entry):
    """Test 5: Scarica il file ROM"""
    print("\n" + "="*60)
    print("TEST 5: Download file ROM")
    print("="*60)
    
    if not entry:
        print("❌ Nessuna entry disponibile per il test")
        return None
    
    links = entry.get('links', [])
    if not links:
        print("❌ Nessun link download disponibile")
        return None
    
    download_link = links[0]  # Prendi il primo link
    download_url = download_link.get('url')
    
    if not download_url:
        print("❌ Nessun URL download disponibile")
        return None
    
    print(f"📥 Scaricando ROM da: {download_url}")
    print("⚠️  Nota: Il download potrebbe richiedere tempo...")
    
    try:
        # Header necessari per il download da Vimm's Lair
        # Il Referer deve puntare alla pagina della ROM
        rom_uri = entry.get('rom_id', '')
        if not rom_uri.startswith('/'):
            rom_uri = '/' + rom_uri
        
        headers = {
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Referer': f'https://vimm.net{rom_uri}'  # IMPORTANTE: Vimm's Lair richiede il Referer
        }
        
        # Prova prima con GET
        response = requests.get(download_url, headers=headers, timeout=300, stream=True, verify=False, allow_redirects=True)
        
        # Se fallisce, prova con POST (come nel form)
        if response.status_code != 200:
            print(f"⚠️  GET fallito ({response.status_code}), provo con POST...")
            # Estrai mediaId dall'URL
            import urllib.parse
            parsed = urllib.parse.urlparse(download_url)
            params = urllib.parse.parse_qs(parsed.query)
            media_id = params.get('mediaId', [None])[0]
            if media_id:
                # Usa POST come nel form originale
                post_url = download_url.split('?')[0] if '?' in download_url else download_url
                response = requests.post(post_url, data={'mediaId': media_id}, headers=headers, timeout=300, stream=True, verify=False, allow_redirects=True)
        response.raise_for_status()
        
        # Determina nome file dall'URL o dal link
        filename = download_link.get('name', 'download')
        if not filename or filename == 'Download':
            # Prova a estrarre il nome dall'URL
            filename = download_url.split('/')[-1].split('?')[0]
            if not filename or '.' not in filename:
                filename = f"rom_{entry.get('slug', 'unknown')}"
        
        # Aggiungi estensione se mancante
        if '.' not in filename:
            format_type = download_link.get('format', 'zip')
            filename = f"{filename}.{format_type}"
        
        file_path = TEMP_DIR / filename
        
        # Download con progress
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        percent = (downloaded / total_size) * 100
                        print(f"\r  ⏳ Progresso: {percent:.1f}% ({downloaded}/{total_size} bytes)", end='', flush=True)
        
        print(f"\n✅ File scaricato: {file_path} ({downloaded} bytes)")
        return file_path
    except Exception as e:
        print(f"\n❌ Errore nel download file: {e}")
        import traceback
        traceback.print_exc()
        return None

def main():
    """Esegue tutti i test"""
    print("🚀 Avvio test completo di vimms_source.py")
    print(f"📁 Directory di lavoro: {Path.cwd()}")
    
    # Test 1: Ricerca tutti i giochi SNES
    snes_rom = test_search_all_snes()
    
    # Test 2: Ricerca 'mario' su NES
    mario_rom = test_search_mario_nes()
    
    # Test 3: Dettagli ROM (usa mario_rom se disponibile, altrimenti snes_rom)
    test_rom = mario_rom or snes_rom
    entry = test_get_entry_details(test_rom)
    
    # Test 4: Download immagine
    image_path = test_download_image(entry)
    
    # Test 5: Download file
    file_path = test_download_file(entry)
    
    # Riepilogo
    print("\n" + "="*60)
    print("📊 RIEPILOGO TEST")
    print("="*60)
    print(f"✅ Ricerca SNES: {'OK' if snes_rom else 'FAIL'}")
    print(f"✅ Ricerca Mario NES: {'OK' if mario_rom else 'FAIL'}")
    print(f"✅ Dettagli ROM: {'OK' if entry else 'FAIL'}")
    print(f"✅ Download immagine: {'OK' if image_path else 'FAIL'}")
    print(f"✅ Download file: {'OK' if file_path else 'FAIL'}")
    print(f"\n📁 File salvati in: {TEMP_DIR.absolute()}")
    
    if image_path:
        print(f"   🖼️  Immagine: {image_path}")
    if file_path:
        print(f"   📦 ROM: {file_path}")

if __name__ == "__main__":
    main()

