#!/usr/bin/env python3
"""
Script di test per verificare il mapping delle piattaforme
tra Vimm's Lair e i mother_code di Tottodrillo
"""

import json
import sys
from pathlib import Path

# Aggiungi il percorso del modulo vimms_source
sys.path.insert(0, str(Path(__file__).parent / "VimmsDownloader-master"))

from vimms_source import SYSTEM_MAPPING, URI_TO_SYSTEM

# Carica platforms_main.json
PLATFORMS_MAIN_PATH = Path(__file__).parent / "app/src/main/assets/platforms_main.json"

def load_platforms_main():
    """Carica platforms_main.json"""
    with open(PLATFORMS_MAIN_PATH, 'r', encoding='utf-8') as f:
        return json.load(f)

def get_all_mother_codes(platforms_data):
    """Estrae tutti i mother_code da platforms_main.json"""
    mother_codes = set()
    for platform in platforms_data.get('platforms', []):
        mother_code = platform.get('mother_code')
        if mother_code:
            mother_codes.add(mother_code)
    return mother_codes

def get_vimm_mapped_mother_codes():
    """Estrae tutti i mother_code mappati da Vimm's Lair"""
    mapped_codes = set()
    for vimm_system, mother_code in SYSTEM_MAPPING.items():
        mapped_codes.add(mother_code)
    return mapped_codes

def get_vimm_systems():
    """Estrae tutti i sistemi Vimm's Lair"""
    return set(SYSTEM_MAPPING.keys())

def test_platform_mapping():
    """Testa il mapping delle piattaforme"""
    print("="*60)
    print("TEST MAPPING PIATTAFORME Vimm's Lair -> Tottodrillo")
    print("="*60)
    
    # Carica platforms_main.json
    try:
        platforms_data = load_platforms_main()
    except Exception as e:
        print(f"❌ Errore nel caricamento platforms_main.json: {e}")
        return
    
    # Estrai dati
    all_mother_codes = get_all_mother_codes(platforms_data)
    vimm_mapped_codes = get_vimm_mapped_mother_codes()
    vimm_systems = get_vimm_systems()
    
    print(f"\n📊 Statistiche:")
    print(f"  - Mother codes totali in Tottodrillo: {len(all_mother_codes)}")
    print(f"  - Sistemi Vimm's Lair mappati: {len(vimm_systems)}")
    print(f"  - Mother codes mappati da Vimm's Lair: {len(vimm_mapped_codes)}")
    
    # Verifica mapping completo
    print(f"\n🔍 Verifica mapping Vimm's Lair -> Mother Code:")
    print("-" * 60)
    
    unmapped_systems = []
    for vimm_system in sorted(vimm_systems):
        mother_code = SYSTEM_MAPPING.get(vimm_system)
        if mother_code:
            # Verifica che il mother_code esista in platforms_main.json
            if mother_code in all_mother_codes:
                print(f"  ✅ {vimm_system:20} -> {mother_code:15} (esiste)")
            else:
                print(f"  ⚠️  {vimm_system:20} -> {mother_code:15} (NON ESISTE in platforms_main.json)")
                unmapped_systems.append((vimm_system, mother_code))
        else:
            print(f"  ❌ {vimm_system:20} -> (NESSUN MAPPING)")
            unmapped_systems.append((vimm_system, None))
    
    # Verifica mother_code non mappati
    print(f"\n🔍 Mother codes in Tottodrillo NON mappati da Vimm's Lair:")
    print("-" * 60)
    unmapped_mother_codes = all_mother_codes - vimm_mapped_codes
    if unmapped_mother_codes:
        for code in sorted(unmapped_mother_codes):
            print(f"  ⚠️  {code}")
    else:
        print("  ✅ Tutti i mother_code sono mappati (o non necessari per Vimm's Lair)")
    
    # Verifica mapping inverso (URI_TO_SYSTEM)
    print(f"\n🔍 Verifica URI_TO_SYSTEM mapping:")
    print("-" * 60)
    uri_systems = set(URI_TO_SYSTEM.values())
    for uri_system in sorted(uri_systems):
        if uri_system in SYSTEM_MAPPING:
            mother_code = SYSTEM_MAPPING[uri_system]
            print(f"  ✅ {uri_system:20} -> {mother_code:15}")
        else:
            print(f"  ❌ {uri_system:20} -> (NESSUN MAPPING in SYSTEM_MAPPING)")
    
    # Riepilogo
    print(f"\n" + "="*60)
    print("📊 RIEPILOGO")
    print("="*60)
    
    if unmapped_systems:
        print(f"⚠️  {len(unmapped_systems)} sistemi Vimm's Lair con problemi di mapping")
    else:
        print("✅ Tutti i sistemi Vimm's Lair sono mappati correttamente")
    
    if unmapped_mother_codes:
        print(f"ℹ️  {len(unmapped_mother_codes)} mother_code non mappati (normale se Vimm's Lair non li supporta)")
    else:
        print("✅ Tutti i mother_code sono mappati")
    
    # Test pratico: verifica che getPlatforms() restituisca solo piattaforme mappate
    print(f"\n🔍 Test getPlatforms():")
    print("-" * 60)
    try:
        from vimms_source import execute
        params = {
            "method": "getPlatforms"
        }
        result_json = execute(json.dumps(params))
        result = json.loads(result_json)
        
        if isinstance(result, dict):
            platforms_returned = set(result.keys())
            print(f"  Piattaforme restituite da getPlatforms(): {len(platforms_returned)}")
            
            # Verifica che tutte siano mother_code validi
            invalid_platforms = platforms_returned - all_mother_codes
            if invalid_platforms:
                print(f"  ⚠️  Piattaforme non valide: {sorted(invalid_platforms)}")
            else:
                print(f"  ✅ Tutte le piattaforme restituite sono mother_code validi")
            
            # Verifica che siano tutte mappate
            unmapped_returned = platforms_returned - vimm_mapped_codes
            if unmapped_returned:
                print(f"  ⚠️  Piattaforme restituite non mappate: {sorted(unmapped_returned)}")
            else:
                print(f"  ✅ Tutte le piattaforme restituite sono correttamente mappate")
        else:
            print(f"  ❌ Risultato non valido: {result}")
    except Exception as e:
        print(f"  ❌ Errore nel test getPlatforms(): {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_platform_mapping()

