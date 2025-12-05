#!/usr/bin/env python3
"""
Script per analizzare il form di download nella pagina ROM
"""
import requests
from bs4 import BeautifulSoup
import urllib3
import re

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def analyze_download_form(rom_uri="/vault/17874"):
    """Analizza il form di download nella pagina ROM"""
    url = f"https://vimm.net{rom_uri}"
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    
    print(f"🔍 Analisi form download per: {url}")
    print("=" * 80)
    print()
    
    session = requests.Session()
    response = session.get(url, headers=headers, verify=False, timeout=10)
    soup = BeautifulSoup(response.content, 'html.parser')
    
    # Cerca il form di download
    download_form = soup.find('form', id='dl_form')
    if not download_form:
        # Prova a cercare qualsiasi form
        forms = soup.find_all('form')
        print(f"📋 Trovati {len(forms)} form nella pagina:")
        for i, form in enumerate(forms, 1):
            print(f"   {i}. ID: {form.get('id', 'N/A')}, Action: {form.get('action', 'N/A')}, Method: {form.get('method', 'GET')}")
            if 'mediaId' in str(form):
                download_form = form
                print(f"      ✅ Questo form contiene mediaId!")
        print()
    
    if download_form:
        print("📋 Form di download trovato:")
        print(f"   ID: {download_form.get('id', 'N/A')}")
        print(f"   Action: {download_form.get('action', 'N/A')}")
        print(f"   Method: {download_form.get('method', 'GET')}")
        print()
        
        # Estrai tutti i campi del form
        print("📋 Campi del form:")
        inputs = download_form.find_all(['input', 'select', 'button'])
        form_data = {}
        for inp in inputs:
            name = inp.get('name', '')
            value = inp.get('value', '')
            input_type = inp.get('type', inp.name)
            if name:
                print(f"   • {name}: {value} (type: {input_type})")
                form_data[name] = value
        
        print()
        
        # Cerca mediaId
        media_id_input = download_form.find(attrs={'name': 'mediaId'})
        if media_id_input:
            media_id = media_id_input.get('value', '')
            print(f"✅ MediaId trovato: {media_id}")
        else:
            print("❌ MediaId non trovato nel form")
        
        print()
        
        # Cerca token CSRF o altri token
        csrf_token = soup.find(attrs={'name': re.compile('csrf|token|_token', re.I)})
        if csrf_token:
            print(f"🔑 Token CSRF trovato: {csrf_token.get('name')} = {csrf_token.get('value', '')}")
        
        # Verifica i cookie
        print("🍪 Cookie della sessione:")
        for cookie in session.cookies:
            print(f"   • {cookie.name}: {cookie.value[:50]}...")
        
        print()
        
        # Prova il download con la sessione
        if media_id_input:
            media_id = media_id_input.get('value', '')
            action = download_form.get('action', '')
            
            # Costruisci l'URL completo
            if action.startswith('//'):
                download_url = 'https:' + action
            elif action.startswith('/'):
                download_url = 'https://vimm.net' + action
            elif action.startswith('http'):
                download_url = action
            else:
                download_url = 'https://dl2.vimm.net/'
            
            print(f"📥 Test download con sessione:")
            print(f"   URL: {download_url}")
            print(f"   MediaId: {media_id}")
            print()
            
            # Prepara i dati del form
            post_data = {}
            for inp in download_form.find_all(['input', 'select']):
                name = inp.get('name')
                if name and name != 'submit':
                    value = inp.get('value', '')
                    if inp.name == 'select':
                        # Prendi il primo option selezionato
                        selected = inp.find('option', selected=True)
                        if selected:
                            value = selected.get('value', '')
                    post_data[name] = value
            
            print(f"   Dati POST: {post_data}")
            print()
            
            # Fai la richiesta POST
            try:
                download_response = session.post(
                    download_url,
                    data=post_data,
                    headers={
                        'User-Agent': headers['User-Agent'],
                        'Referer': url,
                        'Origin': 'https://vimm.net'
                    },
                    stream=True,
                    verify=False,
                    timeout=30
                )
                
                print(f"📊 Risposta download:")
                print(f"   Status Code: {download_response.status_code}")
                print(f"   Content-Type: {download_response.headers.get('Content-Type', 'N/A')}")
                print(f"   Content-Length: {download_response.headers.get('Content-Length', 'N/A')}")
                
                if download_response.status_code == 200:
                    content_type = download_response.headers.get('Content-Type', '')
                    if 'application' in content_type or 'octet-stream' in content_type or 'zip' in content_type:
                        print(f"   ✅ Download avviato con successo!")
                        # Leggi i primi byte
                        first_bytes = download_response.raw.read(1024)
                        if first_bytes:
                            print(f"   ✅ Primi bytes ricevuti: {len(first_bytes)} bytes")
                            print(f"   Magic bytes: {first_bytes[:16].hex()}")
                    else:
                        print(f"   ⚠️  Content-Type inaspettato: {content_type}")
                        print(f"   Response preview: {download_response.text[:200]}")
                else:
                    print(f"   ❌ Errore: {download_response.status_code}")
                    print(f"   Response: {download_response.text[:500]}")
                    
            except Exception as e:
                print(f"   ❌ Errore: {e}")
                import traceback
                traceback.print_exc()
    else:
        print("❌ Form di download non trovato")

if __name__ == "__main__":
    analyze_download_form()

