#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test rapido per SwitchRoms addon
"""
import json
import sys

# Imposta il path della source directory
SOURCE_DIR = "."  # Directory corrente

def test_search():
    """Test ricerca ROM"""
    print("=" * 60)
    print("TEST RICERCA ROM")
    print("=" * 60)
    
    sys.path.insert(0, SOURCE_DIR)
    from switchroms_source import search_roms
    
    params = {
        "search_key": "mario",
        "platforms": ["switch"],
        "regions": [],
        "max_results": 10,
        "page": 1,
        "source_dir": SOURCE_DIR
    }
    
    print(f"\n🔍 Cerco 'mario' su Switch...")
    
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
            if rom.get('box_image'):
                print(f"      Immagine: {rom['box_image'][:60]}...")
        
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
    from switchroms_source import get_entry
    
    # Usa un URL di esempio (Mario & Luigi: Brothership)
    test_slug = "mario-luigi-brothership-1"
    
    print(f"\n📄 Recupero: {test_slug}...")
    
    try:
        params = {
            "slug": test_slug,
            "source_dir": SOURCE_DIR
        }
        result = get_entry(params, SOURCE_DIR)
        data = json.loads(result)
        
        if 'error' in data:
            print(f"   ❌ Errore: {data['error']}")
            return False
        
        entry = data.get('entry')
        if not entry:
            print("   ❌ Nessun risultato")
            return False
        
        print(f"   ✅ Titolo: {entry.get('title', 'N/A')}")
        print(f"   ✅ Platform: {entry.get('platform', 'N/A')}")
        print(f"   ✅ Regioni: {', '.join(entry.get('regions', [])) or 'N/A'}")
        print(f"   ✅ Download links: {len(entry.get('links', []))}")
        
        if entry.get('box_image'):
            print(f"   ✅ Box art: {entry['box_image'][:60]}...")
        
        # Mostra alcuni link
        links = entry.get('links', [])
        if links:
            print(f"\n   📥 Link download (primi 3):")
            for i, link in enumerate(links[:3], 1):
                print(f"      {i}. {link.get('name', 'N/A')}")
                print(f"         URL: {link.get('url', 'N/A')[:60]}...")
                print(f"         Format: {link.get('format', 'N/A')}")
                print(f"         Size: {link.get('size_str', 'N/A')}")
                print(f"         WebView: {link.get('requires_webview', False)}")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_platforms():
    """Test piattaforme"""
    print("\n" + "=" * 60)
    print("TEST PIATTAFORME")
    print("=" * 60)
    
    sys.path.insert(0, SOURCE_DIR)
    from switchroms_source import get_platforms
    
    try:
        result = get_platforms(SOURCE_DIR)
        data = json.loads(result)
        platforms = data.get('platforms', {})
        
        print(f"\n   ✅ Trovate {len(platforms)} piattaforme")
        
        for code, info in platforms.items():
            print(f"      {code}: {info.get('name', 'N/A')} ({info.get('brand', 'N/A')})")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Errore: {e}")
        return False

def main():
    """Esegue i test"""
    print("\n🧪 TEST SWITCHROMS ADDON\n")
    print(f"📂 Source directory: {SOURCE_DIR}\n")
    
    results = {}
    
    print("▶️  Eseguo test...")
    results['Piattaforme'] = test_platforms()
    results['Ricerca'] = test_search()
    results['Dettagli ROM'] = test_rom_details()
    
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

