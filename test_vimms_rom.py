#!/usr/bin/env python3
"""
Script di test per verificare l'estrazione delle informazioni di una ROM da Vimm's Lair
"""
import sys
import json
import os

# Aggiungi il percorso del progetto VimmsDownloader al path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'VimmsDownloader-master'))

try:
    from vimms_source import get_rom_entry_by_uri, execute, get_boxart_url_from_uri
    
    print("=" * 80)
    print("TEST ESTRAZIONE ROM DA VIMM'S LAIR")
    print("=" * 80)
    print()
    
    # URI della ROM da testare
    rom_uri = "/vault/17874"
    rom_url = "https://vimm.net/vault/17874"
    
    print(f"🔍 Test ROM: {rom_url}")
    print(f"   URI: {rom_uri}")
    print()
    
    # Test 1: Estrazione diretta con get_rom_entry_by_uri
    print("📋 TEST 1: Estrazione diretta con get_rom_entry_by_uri")
    print("-" * 80)
    try:
        rom_data = get_rom_entry_by_uri(rom_uri)
        if rom_data:
            print("✅ ROM trovata!")
            print()
            print("Dettagli ROM:")
            print(f"  • Slug: {rom_data.get('slug', 'N/A')}")
            print(f"  • Title: {rom_data.get('title', 'N/A')}")
            print(f"  • Platform: {rom_data.get('platform', 'N/A')}")
            print(f"  • ROM ID: {rom_data.get('rom_id', 'N/A')}")
            print()
            print("Immagini:")
            boxart_url = rom_data.get('boxart_url')
            boxart_urls = rom_data.get('boxart_urls', [])
            cover_urls = rom_data.get('cover_urls', [])
            # Usa boxart_urls se disponibile, altrimenti cover_urls, altrimenti boxart_url
            all_images = boxart_urls if boxart_urls else (cover_urls if cover_urls else ([boxart_url] if boxart_url else []))
            if all_images:
                print(f"  • Trovate {len(all_images)} immagini:")
                for i, url in enumerate(all_images, 1):
                    print(f"    {i}. {url}")
            elif boxart_url:
                print(f"  • Box Art URL: {boxart_url}")
            else:
                print("  ⚠️  Nessuna immagine trovata")
            print()
            print("Download Links:")
            links = rom_data.get('links', [])
            if links:
                print(f"  • Trovati {len(links)} link:")
                for i, link in enumerate(links, 1):
                    print(f"    {i}. {link.get('name', 'N/A')}")
                    print(f"       URL: {link.get('url', 'N/A')}")
                    print(f"       Format: {link.get('format', 'N/A')}")
                    print(f"       Size: {link.get('size', 'N/A')}")
            else:
                print("  ⚠️  Nessun link di download trovato")
            print()
            print("Regioni:")
            regions = rom_data.get('regions', [])
            if regions:
                for region in regions:
                    print(f"  • {region}")
            else:
                print("  ⚠️  Nessuna regione trovata")
        else:
            print("❌ ROM non trovata")
    except Exception as e:
        print(f"❌ Errore: {e}")
        import traceback
        traceback.print_exc()
    
    print()
    print("=" * 80)
    print()
    
    # Test 2: Estrazione tramite execute (come fa Tottodrillo)
    print("📋 TEST 2: Estrazione tramite execute (come fa Tottodrillo)")
    print("-" * 80)
    try:
        params = {
            "method": "getEntry",
            "slug": "17874"  # Lo slug per Vimm's Lair è l'ID numerico
        }
        params_json = json.dumps(params)
        result_json = execute(params_json)
        
        print("Risultato JSON:")
        print(result_json)
        print()
        
        # Parse del risultato
        result = json.loads(result_json)
        if "entry" in result:
            entry = result["entry"]
            if entry:
                print("✅ Entry trovata!")
                print()
                print("Dettagli Entry:")
                print(f"  • Slug: {entry.get('slug', 'N/A')}")
                print(f"  • Title: {entry.get('title', 'N/A')}")
                print(f"  • Platform: {entry.get('platform', 'N/A')}")
                print(f"  • ROM ID: {entry.get('rom_id', 'N/A')}")
                print()
                print("Immagini:")
                boxart_url = entry.get('boxart_url')
                boxart_urls = entry.get('boxart_urls', [])
                cover_urls = entry.get('cover_urls', [])
                # Usa boxart_urls se disponibile, altrimenti cover_urls, altrimenti boxart_url
                all_images = boxart_urls if boxart_urls else (cover_urls if cover_urls else ([boxart_url] if boxart_url else []))
                if all_images:
                    print(f"  • Trovate {len(all_images)} immagini:")
                    for i, url in enumerate(all_images, 1):
                        print(f"    {i}. {url}")
                elif boxart_url:
                    print(f"  • Box Art URL: {boxart_url}")
                else:
                    print("  ⚠️  Nessuna immagine trovata")
                print()
                print("Download Links:")
                links = entry.get('links', [])
                if links:
                    print(f"  • Trovati {len(links)} link:")
                    for i, link in enumerate(links, 1):
                        print(f"    {i}. {link.get('name', 'N/A')}")
                        print(f"       URL: {link.get('url', 'N/A')}")
                        print(f"       Format: {link.get('format', 'N/A')}")
                        print(f"       Size: {link.get('size', 'N/A')}")
                else:
                    print("  ⚠️  Nessun link di download trovato")
            else:
                print("⚠️  Entry è null")
        else:
            print("⚠️  Il risultato non contiene 'entry'")
            print(f"   Chiavi disponibili: {list(result.keys())}")
    except Exception as e:
        print(f"❌ Errore: {e}")
        import traceback
        traceback.print_exc()
    
    print()
    print("=" * 80)
    print()
    
    # Test 3: Verifica URL immagine
    print("📋 TEST 3: Verifica URL immagine")
    print("-" * 80)
    try:
        boxart_url = get_boxart_url_from_uri(rom_uri)
        if boxart_url:
            print(f"✅ Box Art URL: {boxart_url}")
            print(f"   URL completo: https:{boxart_url if boxart_url.startswith('//') else boxart_url}")
        else:
            print("❌ Box Art URL non trovato")
    except Exception as e:
        print(f"❌ Errore: {e}")
        import traceback
        traceback.print_exc()
    
    print()
    print("=" * 80)
    print("TEST COMPLETATO")
    print("=" * 80)
    
except ImportError as e:
    print(f"❌ Errore nell'importazione: {e}")
    print("   Assicurati che il file vimms_source.py sia nella cartella VimmsDownloader-master/")
    sys.exit(1)
except Exception as e:
    print(f"❌ Errore: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

