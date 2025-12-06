#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ESEMPI DI UTILIZZO - RomsFun Addon per Tottodrillo
Esempi pratici per testare e usare l'addon
"""

# ============================================================
# ESEMPIO 1: Setup iniziale
# ============================================================
def esempio_setup():
    """Come configurare l'addon"""
    import sys
    
    # Aggiungi il path della source
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    sys.path.insert(0, SOURCE_DIR)
    
    # Importa le funzioni
    from romsfun_source import (
        search_roms,
        get_rom_entry_by_url,
        get_platforms,
        get_regions
    )
    
    print("✅ Setup completato")
    return SOURCE_DIR

# ============================================================
# ESEMPIO 2: Ottenere lista piattaforme
# ============================================================
def esempio_get_platforms():
    """Come ottenere la lista delle piattaforme disponibili"""
    import json
    from romsfun_source import get_platforms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Ottieni le piattaforme
    platforms_json = get_platforms(SOURCE_DIR)
    data = json.loads(platforms_json)
    
    platforms = data['platforms']
    
    print(f"Piattaforme disponibili: {len(platforms)}")
    
    # Mostra alcune piattaforme
    for code, info in list(platforms.items())[:10]:
        print(f"  {code}: {info['name']}")
    
    # Cerca una piattaforma specifica
    if 'snes' in platforms:
        print(f"\nSNES trovato: {platforms['snes']['name']}")

# ============================================================
# ESEMPIO 3: Ricerca ROM semplice
# ============================================================
def esempio_ricerca_semplice():
    """Ricerca base per titolo"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Parametri di ricerca
    params = {
        "search_key": "mario",
        "platforms": [],  # Tutte le piattaforme
        "regions": [],
        "max_results": 20,
        "page": 1
    }
    
    # Esegui ricerca
    result = search_roms(params, SOURCE_DIR)
    data = json.loads(result)
    
    # Elabora risultati
    if 'error' in data:
        print(f"Errore: {data['error']}")
        return
    
    print(f"Trovati {data['total_results']} risultati")
    
    for rom in data['roms']:
        print(f"\n📦 {rom['title']}")
        print(f"   Platform: {rom['platform']}")
        print(f"   URL: {rom['rom_id']}")

# ============================================================
# ESEMPIO 4: Ricerca per piattaforma specifica
# ============================================================
def esempio_ricerca_piattaforma():
    """Ricerca ROM per una piattaforma specifica"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Cerca "zelda" solo su Nintendo 64
    params = {
        "search_key": "zelda",
        "platforms": ["n64"],  # Solo N64
        "regions": [],
        "max_results": 10,
        "page": 1
    }
    
    result = search_roms(params, SOURCE_DIR)
    data = json.loads(result)
    
    if 'roms' in data:
        print(f"Zelda games per N64: {len(data['roms'])}")
        for rom in data['roms']:
            print(f"  - {rom['title']}")

# ============================================================
# ESEMPIO 5: Browse per piattaforma (senza query)
# ============================================================
def esempio_browse_piattaforma():
    """Naviga tutte le ROM di una piattaforma"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Ottieni tutti i giochi SNES (prima pagina)
    params = {
        "search_key": "",  # Nessuna ricerca
        "platforms": ["snes"],
        "regions": [],
        "max_results": 50,
        "page": 1
    }
    
    result = search_roms(params, SOURCE_DIR)
    data = json.loads(result)
    
    if 'roms' in data:
        print(f"ROM SNES (pagina 1): {len(data['roms'])}")
        print(f"Totale disponibili: {data.get('total_results', '?')}")

# ============================================================
# ESEMPIO 6: Paginazione
# ============================================================
def esempio_paginazione():
    """Come gestire la paginazione dei risultati"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Funzione per ottenere una pagina
    def get_page(query, platform, page_num):
        params = {
            "search_key": query,
            "platforms": [platform],
            "regions": [],
            "max_results": 50,
            "page": page_num
        }
        
        result = search_roms(params, SOURCE_DIR)
        return json.loads(result)
    
    # Ottieni le prime 3 pagine
    for page in range(1, 4):
        data = get_page("mario", "snes", page)
        
        if 'roms' in data:
            print(f"\nPagina {page}: {len(data['roms'])} ROM")
            if data['roms']:
                print(f"  Prima: {data['roms'][0]['title']}")
                print(f"  Ultima: {data['roms'][-1]['title']}")

# ============================================================
# ESEMPIO 7: Dettagli ROM completi
# ============================================================
def esempio_dettagli_rom():
    """Come ottenere tutti i dettagli di una ROM"""
    import json
    from romsfun_source import get_rom_entry_by_url
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # URL di una ROM specifica
    url = "https://romsfun.com/roms/super-nintendo/super-mario-world-usa.html"
    
    # Ottieni dettagli
    rom = get_rom_entry_by_url(url, SOURCE_DIR)
    
    if rom:
        print(f"Titolo: {rom['title']}")
        print(f"Platform: {rom['platform']}")
        print(f"Regioni: {', '.join(rom['regions'])}")
        print(f"\nBox Art: {rom['box_image']}")
        
        print(f"\nDownload Links: {len(rom['links'])}")
        for i, link in enumerate(rom['links'], 1):
            print(f"  {i}. {link['label']}")
            print(f"     URL: {link['url']}")
            print(f"     Direct: {link['direct']}")

# ============================================================
# ESEMPIO 8: Ricerca multi-piattaforma
# ============================================================
def esempio_multi_platform():
    """Cerca su più piattaforme contemporaneamente"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # Cerca "sonic" su più piattaforme SEGA
    params = {
        "search_key": "sonic",
        "platforms": ["megadrive", "gamegear", "saturn", "dreamcast"],
        "regions": [],
        "max_results": 20,
        "page": 1
    }
    
    result = search_roms(params, SOURCE_DIR)
    data = json.loads(result)
    
    if 'roms' in data:
        # Raggruppa per piattaforma
        by_platform = {}
        for rom in data['roms']:
            platform = rom['platform']
            if platform not in by_platform:
                by_platform[platform] = []
            by_platform[platform].append(rom['title'])
        
        # Mostra risultati
        for platform, titles in by_platform.items():
            print(f"\n{platform.upper()}: {len(titles)} games")
            for title in titles[:3]:  # Prime 3
                print(f"  - {title}")

# ============================================================
# ESEMPIO 9: Verifica disponibilità ROM
# ============================================================
def esempio_check_availability():
    """Verifica se una ROM è disponibile"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    def check_rom(title, platform):
        """Verifica se una ROM esiste"""
        params = {
            "search_key": title,
            "platforms": [platform],
            "regions": [],
            "max_results": 10,
            "page": 1
        }
        
        result = search_roms(params, SOURCE_DIR)
        data = json.loads(result)
        
        if 'roms' in data and data['roms']:
            # Cerca match esatto o simile
            for rom in data['roms']:
                if title.lower() in rom['title'].lower():
                    return True, rom['title']
        
        return False, None
    
    # Test
    games = [
        ("Final Fantasy VII", "psx"),
        ("Ocarina of Time", "n64"),
        ("Non Existing Game", "snes")
    ]
    
    for title, platform in games:
        found, exact_title = check_rom(title, platform)
        if found:
            print(f"✅ {title} ({platform}): {exact_title}")
        else:
            print(f"❌ {title} ({platform}): Non trovato")

# ============================================================
# ESEMPIO 10: Statistiche piattaforma
# ============================================================
def esempio_platform_stats():
    """Ottieni statistiche su una piattaforma"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    def get_platform_stats(platform_code):
        """Conta ROM disponibili per piattaforma"""
        params = {
            "search_key": "",
            "platforms": [platform_code],
            "regions": [],
            "max_results": 1,  # Solo per il conteggio
            "page": 1
        }
        
        result = search_roms(params, SOURCE_DIR)
        data = json.loads(result)
        
        return data.get('total_results', 0)
    
    # Test su varie piattaforme
    platforms = ['snes', 'psx', 'n64', 'gba', 'nds']
    
    print("📊 Statistiche ROM disponibili:\n")
    for platform in platforms:
        count = get_platform_stats(platform)
        print(f"{platform.upper():8s}: {count:5d} ROM")

# ============================================================
# ESEMPIO 11: Export risultati JSON
# ============================================================
def esempio_export_json():
    """Esporta risultati in un file JSON"""
    import json
    from romsfun_source import search_roms
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    params = {
        "search_key": "mario",
        "platforms": ["snes"],
        "regions": [],
        "max_results": 50,
        "page": 1
    }
    
    result = search_roms(params, SOURCE_DIR)
    data = json.loads(result)
    
    # Salva in file
    output_file = "/storage/emulated/0/Download/mario_snes.json"
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print(f"✅ Risultati salvati in: {output_file}")

# ============================================================
# ESEMPIO 12: Integrazione con Tottodrillo
# ============================================================
def esempio_tottodrillo_integration():
    """Esempio di come Tottodrillo chiamerebbe l'addon"""
    import json
    from romsfun_source import search_roms, get_rom_entry_by_url
    
    SOURCE_DIR = "/storage/emulated/0/Tottodrillo/sources/romsfun"
    
    # 1. L'utente cerca "pokemon"
    search_params = {
        "search_key": "pokemon",
        "platforms": [],  # L'app potrebbe filtrare dopo
        "regions": [],
        "max_results": 50,
        "page": 1
    }
    
    search_result = search_roms(search_params, SOURCE_DIR)
    search_data = json.loads(search_result)
    
    # 2. L'utente seleziona una ROM
    if search_data.get('roms'):
        selected_rom_url = search_data['roms'][0]['rom_id']
        
        # 3. L'app recupera i dettagli completi
        rom_details = get_rom_entry_by_url(selected_rom_url, SOURCE_DIR)
        
        # 4. L'app mostra i link di download
        if rom_details:
            print(f"ROM selezionata: {rom_details['title']}")
            print(f"Link disponibili: {len(rom_details['links'])}")
            
            # L'utente può ora scaricare usando uno dei link
            for link in rom_details['links']:
                print(f"  - {link['label']}: {link['url']}")

# ============================================================
# MAIN - Esegui esempi
# ============================================================
if __name__ == "__main__":
    print("🎮 ESEMPI DI UTILIZZO - RomsFun Addon\n")
    print("=" * 60)
    
    # Modifica questo per eseguire l'esempio che vuoi
    esempio_da_eseguire = 3  # Cambia questo numero
    
    esempi = {
        1: ("Setup iniziale", esempio_setup),
        2: ("Ottenere piattaforme", esempio_get_platforms),
        3: ("Ricerca semplice", esempio_ricerca_semplice),
        4: ("Ricerca per piattaforma", esempio_ricerca_piattaforma),
        5: ("Browse piattaforma", esempio_browse_piattaforma),
        6: ("Paginazione", esempio_paginazione),
        7: ("Dettagli ROM", esempio_dettagli_rom),
        8: ("Multi-piattaforma", esempio_multi_platform),
        9: ("Check disponibilità", esempio_check_availability),
        10: ("Statistiche", esempio_platform_stats),
        11: ("Export JSON", esempio_export_json),
        12: ("Integrazione Tottodrillo", esempio_tottodrillo_integration)
    }
    
    if esempio_da_eseguire in esempi:
        nome, func = esempi[esempio_da_eseguire]
        print(f"\n▶️  Eseguo: {nome}\n")
        print("-" * 60 + "\n")
        
        try:
            func()
        except Exception as e:
            print(f"\n❌ Errore: {e}")
            import traceback
            traceback.print_exc()
    else:
        print("❌ Esempio non valido")
        print("\nEsempi disponibili:")
        for num, (nome, _) in esempi.items():
            print(f"  {num:2d}. {nome}")
