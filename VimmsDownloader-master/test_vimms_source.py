#!/usr/bin/env python3
"""
Script di test per verificare che vimms_source.py funzioni correttamente
Esegui: python3 test_vimms_source.py
"""
import json
import sys
import os

# Aggiungi il percorso corrente al path per importare vimms_source
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    from vimms_source import execute
    
    print("=" * 60)
    print("Test vimms_source.py")
    print("=" * 60)
    
    # Test 1: searchRoms - ricerca generale
    print("\n[Test 1] Ricerca generale: 'mario'")
    params1 = {
        "method": "searchRoms",
        "search_key": "mario",
        "platforms": [],
        "regions": [],
        "max_results": 5,
        "page": 1
    }
    result1 = execute(json.dumps(params1))
    print(f"Risultato: {result1[:200]}...")  # Mostra solo i primi 200 caratteri
    
    # Test 2: searchRoms - ricerca per piattaforma
    print("\n[Test 2] Ricerca per piattaforma NES: 'zelda'")
    params2 = {
        "method": "searchRoms",
        "search_key": "zelda",
        "platforms": ["nes"],
        "regions": [],
        "max_results": 5,
        "page": 1
    }
    result2 = execute(json.dumps(params2))
    print(f"Risultato: {result2[:200]}...")
    
    # Test 3: getPlatforms
    print("\n[Test 3] Ottieni piattaforme")
    params3 = {
        "method": "getPlatforms"
    }
    result3 = execute(json.dumps(params3))
    print(f"Risultato: {result3[:200]}...")
    
    # Test 4: getRegions
    print("\n[Test 4] Ottieni regioni")
    params4 = {
        "method": "getRegions"
    }
    result4 = execute(json.dumps(params4))
    print(f"Risultato: {result4}")
    
    # Test 5: getEntry (richiede una ricerca preliminare)
    print("\n[Test 5] Ottieni entry (richiede ricerca preliminare)")
    # Prima facciamo una ricerca per ottenere uno slug
    search_result = json.loads(result1)
    if search_result.get("results") and len(search_result["results"]) > 0:
        first_rom = search_result["results"][0]
        slug = first_rom.get("slug")
        print(f"Usando slug dalla ricerca: {slug}")
        
        params5 = {
            "method": "getEntry",
            "slug": slug
        }
        result5 = execute(json.dumps(params5))
        print(f"Risultato: {result5[:300]}...")
    else:
        print("Nessun risultato dalla ricerca, skip test getEntry")
    
    print("\n" + "=" * 60)
    print("Test completati!")
    print("=" * 60)
    
except ImportError as e:
    print(f"Errore importazione: {e}")
    print("Assicurati di avere installato le dipendenze:")
    print("  pip3 install requests beautifulsoup4")
    sys.exit(1)
except Exception as e:
    print(f"Errore durante i test: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

