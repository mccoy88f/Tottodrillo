#!/usr/bin/env python3
"""
Script per analizzare il form di download della ROM 91176
"""
import requests
from bs4 import BeautifulSoup
import urllib3
import re

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def analyze_form_91176():
    """Analizza il form della ROM 91176"""
    url = "https://vimm.net/vault/91176"
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    
    session = requests.Session()
    response = session.get(url, headers=headers, verify=False, timeout=10)
    soup = BeautifulSoup(response.content, 'html.parser')
    
    print("=" * 80)
    print("ANALISI FORM ROM 91176")
    print("=" * 80)
    print()
    
    # Cerca il form
    form = soup.find('form', id='dl_form')
    if not form:
        forms = soup.find_all('form')
        print(f"📋 Trovati {len(forms)} form:")
        for i, f in enumerate(forms, 1):
            print(f"   {i}. ID: {f.get('id', 'N/A')}, Action: {f.get('action', 'N/A')}")
            if 'mediaId' in str(f):
                form = f
                print(f"      ✅ Questo form contiene mediaId!")
        print()
    
    if form:
        print("📋 Form trovato:")
        print(f"   ID: {form.get('id', 'N/A')}")
        print(f"   Action: {form.get('action', 'N/A')}")
        print(f"   Method: {form.get('method', 'GET')}")
        print()
        
        # Estrai tutti i campi
        print("📋 Campi del form:")
        form_data = {}
        for inp in form.find_all(['input', 'select']):
            name = inp.get('name', '')
            value = inp.get('value', '')
            input_type = inp.get('type', inp.name)
            if name:
                print(f"   • {name}: {value} (type: {input_type})")
                form_data[name] = value
        
        print()
        
        # Cerca mediaId
        media_id = form_data.get('mediaId')
        alt = form_data.get('alt', '0')
        
        if media_id:
            print(f"✅ MediaId: {media_id}")
            print(f"✅ Alt: {alt}")
            print()
            
            # Costruisci URL come nel form
            action = form.get('action', '')
            if action.startswith('//'):
                download_base = 'https:' + action
            elif action.startswith('/'):
                download_base = 'https://vimm.net' + action
            elif action.startswith('http'):
                download_base = action
            else:
                download_base = 'https://dl3.vimm.net/'
            
            print(f"📥 URL base dal form: {download_base}")
            print()
            
            # Prova il download con POST (come fa il form)
            print("📥 Test download con POST (come fa il form):")
            post_data = {'mediaId': media_id, 'alt': alt}
            print(f"   URL: {download_base}")
            print(f"   Data: {post_data}")
            print()
            
            download_response = session.post(
                download_base,
                data=post_data,
                headers={
                    'User-Agent': headers['User-Agent'],
                    'Referer': url,
                    'Origin': 'https://vimm.net'
                },
                stream=True,
                verify=False,
                allow_redirects=True,
                timeout=30
            )
            
            print(f"📊 Risposta:")
            print(f"   Status Code: {download_response.status_code}")
            print(f"   Content-Type: {download_response.headers.get('Content-Type', 'N/A')}")
            content_length = download_response.headers.get('Content-Length')
            if content_length:
                size_mb = int(content_length) / (1024 * 1024)
                print(f"   Content-Length: {content_length} bytes ({size_mb:.2f} MB)")
            
            if download_response.status_code == 200:
                print("✅ Download avviato con successo!")
                first_chunk = None
                for chunk in download_response.iter_content(chunk_size=1024):
                    first_chunk = chunk
                    break
                if first_chunk:
                    print(f"   Magic bytes: {first_chunk[:16].hex()}")
            else:
                print(f"❌ Errore: {download_response.status_code}")
                print(f"   Response: {download_response.text[:300]}")
        else:
            print("❌ MediaId non trovato")
    else:
        print("❌ Form non trovato")

if __name__ == "__main__":
    analyze_form_91176()

