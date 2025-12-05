#!/usr/bin/env python3
"""
Script per testare il caricamento di tutte le ROM del Dreamcast con paginazione
"""
import requests
from bs4 import BeautifulSoup
import urllib.parse
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def get_all_dreamcast_roms():
    """Carica tutte le ROM del Dreamcast con paginazione"""
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
    
    query_params = {
        'mode': 'adv',
        'p': 'list',
        'system': 'Dreamcast',
        'q': '',
        'players': '>=',
        'playersValue': '1',
        'simultaneous': '',
        'publisher': '',
        'year': '=',
        'yearValue': '',
        'rating': '>=',
        'ratingValue': '',
        'region': 'All',
        'sort': 'Title',
        'sortOrder': 'ASC'
    }
    
    all_roms = []
    page = 1
    max_pages = 10  # Limite per il test
    
    print('=' * 80)
    print('CARICAMENTO TUTTE LE ROM DEL DREAMCAST')
    print('=' * 80)
    print()
    
    while page <= max_pages:
        # Aggiungi numero pagina
        query_params_page = query_params.copy()
        if page > 1:
            query_params_page['page'] = str(page)
        
        url = 'https://vimm.net/vault/?' + urllib.parse.urlencode(query_params_page)
        print(f'📄 Caricamento pagina {page}...')
        print(f'   URL: {url[:80]}...')
        
        try:
            response = requests.get(url, headers=headers, verify=False, timeout=10)
            soup = BeautifulSoup(response.content, 'html.parser')
            
            # Cerca la tabella
            table = soup.find('table', class_=lambda x: x and 'rounded' in x and 'centered' in x and 'cellpadding1' in x and 'hovertable' in x)
            
            if not table:
                print(f'   ⚠️  Tabella non trovata, fine paginazione')
                break
            
            # Cerca header per identificare le colonne
            header_row = table.find('tr')
            headers_list = []
            if header_row:
                ths = header_row.find_all(['th', 'td'])
                for th in ths:
                    headers_list.append(th.get_text(strip=True))
            
            # Trova indice colonne
            title_idx = headers_list.index('Title') if 'Title' in headers_list else 0
            region_idx = headers_list.index('Region') if 'Region' in headers_list else -1
            
            # Estrai righe dati
            rows = table.find_all('tr')
            data_rows = [r for r in rows if not r.find('th')]
            
            if len(data_rows) == 0:
                print(f'   ⚠️  Nessuna riga trovata, fine paginazione')
                break
            
            print(f'   ✅ Trovate {len(data_rows)} ROM')
            
            # Estrai dati da ogni riga
            for row in data_rows:
                cells = row.find_all('td')
                if len(cells) > title_idx:
                    # Estrai titolo e link
                    title_cell = cells[title_idx]
                    link = title_cell.find('a', href=True)
                    if link:
                        title = link.get_text(strip=True)
                        uri = link['href']
                        if not uri.startswith('/'):
                            uri = '/' + uri
                        if not uri.startswith('/vault/'):
                            uri = '/vault/' + uri.lstrip('/')
                        
                        # Estrai regione
                        region = ''
                        if region_idx >= 0 and len(cells) > region_idx:
                            region = cells[region_idx].get_text(strip=True)
                        
                        all_roms.append({
                            'title': title,
                            'uri': uri,
                            'region': region
                        })
            
            # Verifica se ci sono altre pagine
            pagination = soup.find_all('a', href=lambda x: x and 'page=' in str(x))
            has_next = False
            for link in pagination:
                href = link.get('href', '')
                text = link.get_text(strip=True)
                if 'Next' in text or (text.isdigit() and int(text) > page):
                    has_next = True
                    break
            
            if not has_next:
                print(f'   ✅ Ultima pagina raggiunta')
                break
            
            page += 1
            print()
            
        except Exception as e:
            print(f'   ❌ Errore: {e}')
            break
    
    print()
    print('=' * 80)
    print(f'RIEPILOGO: {len(all_roms)} ROM totali caricate')
    print('=' * 80)
    print()
    
    # Mostra prime 20 ROM con regione
    print('Prime 20 ROM:')
    print('-' * 80)
    for i, rom in enumerate(all_roms[:20], 1):
        region_display = rom['region'] if rom['region'] else '(nessuna)'
        title = rom['title']
        print(f'{i:3d}. {title:50s} | Regione: {region_display}')
    
    if len(all_roms) > 20:
        print(f'\n... e altre {len(all_roms) - 20} ROM')
    
    # Statistiche regioni
    print()
    print('Statistiche regioni:')
    regions_count = {}
    for rom in all_roms:
        region = rom['region'] if rom['region'] else '(nessuna)'
        regions_count[region] = regions_count.get(region, 0) + 1
    
    for region, count in sorted(regions_count.items(), key=lambda x: x[1], reverse=True):
        print(f'  {region}: {count}')
    
    return all_roms

if __name__ == "__main__":
    roms = get_all_dreamcast_roms()

