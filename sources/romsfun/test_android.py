#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test rapido per RomsFun addon - Da eseguire su Android
Testa solo le funzionalità base senza richiedere connessione
"""
import json
import sys

# Imposta il path della source directory
# Modifica questo path secondo la tua installazione:
# - Android: "/storage/emulated/0/Tottodrillo/sources/romsfun"
# - Mac/Linux: usa il path dove hai estratto i file
SOURCE_DIR = "."  # Directory corrente (per test rapidi)

def test_offline():
    """Test che non richiedono connessione internet"""
    print("=" * 60)
    print("TEST OFFLINE - Funzionalità locali")
    print("=" * 60)
    
    sys.path.insert(0, SOURCE_DIR)
    from romsfun_source import (
        load_platform_mapping,
        map_mother_code_to_romsfun_slug,
        map_romsfun_slug_to_mother_code,
        get_platforms,
        get_platform_display_name
    )
    
    print("\n1️⃣ Test Platform Mapping...")
    try:
        mapping = load_platform_mapping(SOURCE_DIR)
        print(f"   ✅ Caricato mapping con {len(mapping)} piattaforme")
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        return False
    
    print("\n2️⃣ Test Mother Code -> RomsFun Slug...")
    test_cases = [
        ("snes", "super-nintendo"),
        ("psx", "playstation"),
        ("gba", "game-boy-advance"),
        ("n64", "nintendo-64"),
        ("wii", "nintendo-wii"),
    ]
    
    all_ok = True
    for mother_code, expected in test_cases:
        result = map_mother_code_to_romsfun_slug(mother_code, SOURCE_DIR)
        if result == expected:
            print(f"   ✅ {mother_code} -> {result}")
        else:
            print(f"   ❌ {mother_code} -> {result} (atteso: {expected})")
            all_ok = False
    
    print("\n3️⃣ Test RomsFun Slug -> Mother Code...")
    reverse_cases = [
        ("super-nintendo", "snes"),
        ("playstation", "psx"),
        ("game-boy-advance", "gba"),
    ]
    
    for slug, expected in reverse_cases:
        result = map_romsfun_slug_to_mother_code(slug, SOURCE_DIR)
        if result == expected:
            print(f"   ✅ {slug} -> {result}")
        else:
            print(f"   ⚠️  {slug} -> {result} (atteso: {expected})")
    
    print("\n4️⃣ Test Get Platforms...")
    try:
        platforms_json = get_platforms(SOURCE_DIR)
        data = json.loads(platforms_json)
        platforms = data.get('platforms', {})
        print(f"   ✅ Trovate {len(platforms)} piattaforme")
        
        # Mostra alcune
        print("\n   📋 Alcune piattaforme:")
        for i, (code, info) in enumerate(list(platforms.items())[:5], 1):
            print(f"      {code}: {info['name']}")
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        all_ok = False
    
    print("\n5️⃣ Test Platform Display Names...")
    display_tests = [
        ("super-nintendo", "SNES"),
        ("playstation", "PlayStation (PS)"),
        ("game-boy-advance", "GBA"),
    ]
    
    for slug, expected in display_tests:
        result = get_platform_display_name(slug)
        if result == expected:
            print(f"   ✅ {slug} -> {result}")
        else:
            print(f"   ⚠️  {slug} -> {result} (atteso: {expected})")
    
    return all_ok

def test_connectivity():
    """Test connettività al sito"""
    print("\n" + "=" * 60)
    print("TEST CONNETTIVITÀ")
    print("=" * 60)
    
    import requests
    sys.path.insert(0, SOURCE_DIR)
    from romsfun_source import get_browser_headers
    
    print("\n🌐 Test connessione a romsfun.com...")
    
    try:
        headers = get_browser_headers()
        response = requests.get('https://romsfun.com', headers=headers, timeout=10)
        
        print(f"   ✅ Status: {response.status_code}")
        print(f"   ✅ Content-Type: {response.headers.get('Content-Type', 'N/A')}")
        
        # Verifica Cloudflare
        if 'cloudflare' in response.text.lower() or response.status_code == 403:
            print("   ⚠️  CLOUDFLARE rilevato - potrebbe servire cloudscraper")
            return False
        
        return response.status_code == 200
        
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        return False

def test_search():
    """Test ricerca ROM"""
    print("\n" + "=" * 60)
    print("TEST RICERCA ROM")
    print("=" * 60)
    
    sys.path.insert(0, SOURCE_DIR)
    from romsfun_source import search_roms
    
    params = {
        "search_key": "mario",
        "platforms": ["snes"],
        "regions": [],
        "max_results": 10,
        "page": 1
    }
    
    print(f"\n🔍 Cerco 'mario' su SNES...")
    
    try:
        result = search_roms(params, SOURCE_DIR)
        data = json.loads(result)
        
        if 'error' in data:
            print(f"   ❌ Errore: {data['error']}")
            return False
        
        roms = data.get('roms', [])
        total = data.get('total_results', 0)
        
        print(f"   ✅ Trovati {total} risultati")
        print(f"   ✅ ROM in pagina: {len(roms)}")
        
        if roms:
            rom = roms[0]
            print(f"\n   📦 Primo risultato:")
            print(f"      Titolo: {rom.get('title', 'N/A')}")
            print(f"      Platform: {rom.get('platform', 'N/A')}")
            print(f"      URL: {rom.get('rom_id', 'N/A')[:60]}...")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_rom_details():
    """Test dettagli ROM"""
    print("\n" + "=" * 60)
    print("TEST DETTAGLI ROM")
    print("=" * 60)
    
    sys.path.insert(0, SOURCE_DIR)
    from romsfun_source import get_rom_entry_by_url
    
    test_url = "https://romsfun.com/roms/super-nintendo/super-mario-world.html"
    
    print(f"\n📄 Recupero: Super Mario World (SNES)...")
    
    try:
        result = get_rom_entry_by_url(test_url, SOURCE_DIR)
        
        if not result:
            print("   ❌ Nessun risultato")
            return False
        
        print(f"   ✅ Titolo: {result.get('title', 'N/A')}")
        print(f"   ✅ Platform: {result.get('platform', 'N/A')}")
        print(f"   ✅ Regioni: {', '.join(result.get('regions', []))}")
        print(f"   ✅ Download links: {len(result.get('links', []))}")
        
        if result.get('box_image'):
            print(f"   ✅ Box art: {result['box_image'][:60]}...")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Esegue i test"""
    print("\n🧪 TEST ROMSFUN ADDON - ANDROID\n")
    print(f"📂 Source directory: {SOURCE_DIR}\n")
    
    results = {}
    
    # Test offline (sempre eseguibili)
    print("▶️  Eseguo test offline...")
    results['Offline'] = test_offline()
    
    # Chiedi se eseguire test online
    print("\n" + "-" * 60)
    try:
        response = input("\n❓ Eseguire test online (richiedono internet)? [s/N]: ")
        online = response.lower().strip() in ['s', 'si', 'y', 'yes']
    except:
        online = False
    
    if online:
        print("\n▶️  Eseguo test online...")
        results['Connettività'] = test_connectivity()
        
        if results['Connettività']:
            results['Ricerca'] = test_search()
            results['Dettagli ROM'] = test_rom_details()
        else:
            print("\n⚠️  Saltati test ricerca/dettagli (connessione fallita)")
    
    # Riepilogo
    print("\n" + "=" * 60)
    print("RIEPILOGO")
    print("=" * 60 + "\n")
    
    for name, result in results.items():
        status = "✅" if result else "❌"
        print(f"{status} {name}")
    
    passed = sum(1 for r in results.values() if r)
    total = len(results)
    
    print(f"\n📊 {passed}/{total} test passati\n")
    
    return all(results.values())

if __name__ == "__main__":
    try:
        success = main()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n\n⚠️  Test interrotti dall'utente")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ ERRORE CRITICO: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
