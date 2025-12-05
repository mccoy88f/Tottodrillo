#!/usr/bin/env python3
"""
Script di test per verificare le piattaforme disponibili su Vimm's Lair
"""
import sys
import json
import os

# Aggiungi il percorso del progetto VimmsDownloader al path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'VimmsDownloader-master'))

try:
    from vimms_source import get_platforms, SYSTEM_MAPPING, URI_TO_SYSTEM
    
    print("=" * 60)
    print("TEST PIATTAFORME VIMM'S LAIR")
    print("=" * 60)
    print()
    
    # Mostra il mapping SYSTEM_MAPPING
    print("📋 MAPPING SISTEMI VIMM'S LAIR -> MOTHER_CODE TOTTODRILLO:")
    print("-" * 60)
    for vimm_system, mother_code in sorted(SYSTEM_MAPPING.items()):
        print(f"  {vimm_system:25} -> {mother_code}")
    print()
    
    # Mostra il mapping URI_TO_SYSTEM
    print("📋 MAPPING URI -> NOME SISTEMA:")
    print("-" * 60)
    for uri, system in sorted(URI_TO_SYSTEM.items()):
        print(f"  {uri:25} -> {system}")
    print()
    
    # Testa la funzione get_platforms()
    print("🔍 TEST FUNZIONE get_platforms():")
    print("-" * 60)
    
    # Simula la chiamata come fa Tottodrillo
    params_json = json.dumps({"method": "getPlatforms"})
    result_json = get_platforms()
    
    print("Risultato JSON:")
    print(result_json)
    print()
    
    # Parse del risultato
    try:
        result = json.loads(result_json)
        if "platforms" in result:
            platforms = result["platforms"]
            print(f"✅ Trovate {len(platforms)} piattaforme:")
            print("-" * 60)
            for platform in platforms:
                if isinstance(platform, dict):
                    name = platform.get("name", "N/A")
                    code = platform.get("code", "N/A")
                    print(f"  • {name:30} (codice: {code})")
                else:
                    print(f"  • {platform}")
        else:
            print("⚠️  Il risultato non contiene 'platforms'")
            print(f"   Chiavi disponibili: {list(result.keys())}")
    except json.JSONDecodeError as e:
        print(f"❌ Errore nel parsing JSON: {e}")
        print(f"   Risultato grezzo: {result_json[:200]}...")
    except Exception as e:
        print(f"❌ Errore: {e}")
        import traceback
        traceback.print_exc()
    
    print()
    print("=" * 60)
    print("RIEPILOGO:")
    print("=" * 60)
    print(f"  • Sistemi mappati in SYSTEM_MAPPING: {len(SYSTEM_MAPPING)}")
    print(f"  • URI mappati in URI_TO_SYSTEM: {len(URI_TO_SYSTEM)}")
    print()
    
except ImportError as e:
    print(f"❌ Errore nell'importazione: {e}")
    print("   Assicurati che il file vimms_source.py sia nella cartella VimmsDownloader-master/")
    sys.exit(1)
except Exception as e:
    print(f"❌ Errore: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

