#!/usr/bin/env python3
"""
Script di test per verificare il download da Vimm's Lair usando POST
"""
import requests
import urllib3

# Disabilita warning SSL
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def test_download_post(media_id, alt=None, filename="test_download"):
    """Testa il download usando POST"""
    url = "https://dl2.vimm.net/"
    
    # Prepara i parametri per la POST
    data = {"mediaId": media_id}
    if alt is not None:
        data["alt"] = str(alt)
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Referer': 'https://vimm.net/',
        'Origin': 'https://vimm.net'
    }
    
    print(f"📥 Test download POST:")
    print(f"   URL: {url}")
    print(f"   Parametri: {data}")
    print()
    
    try:
        # Fai la richiesta POST
        response = requests.post(url, data=data, headers=headers, stream=True, verify=False, timeout=30)
        
        print(f"📊 Risposta:")
        print(f"   Status Code: {response.status_code}")
        print(f"   Headers:")
        for key, value in response.headers.items():
            if key.lower() in ['content-type', 'content-length', 'content-disposition']:
                print(f"      {key}: {value}")
        print()
        
        if response.status_code == 200:
            # Controlla il Content-Type
            content_type = response.headers.get('Content-Type', '')
            content_length = response.headers.get('Content-Length', '0')
            
            print(f"✅ Download avviato con successo!")
            print(f"   Content-Type: {content_type}")
            print(f"   Content-Length: {content_length} bytes ({int(content_length) / (1024*1024):.2f} MB)")
            print()
            
            # Prova a leggere i primi byte per verificare che sia un file valido
            first_chunk = response.raw.read(1024)
            response.raw.seek(0)  # Reset per eventuale download completo
            
            if first_chunk:
                print(f"✅ Primi 1024 bytes ricevuti correttamente")
                print(f"   Magic bytes (hex): {first_chunk[:16].hex()}")
                
                # Verifica il tipo di file dai magic bytes
                if first_chunk.startswith(b'PK'):
                    print(f"   Tipo file: ZIP/WBFS (inizia con PK)")
                elif first_chunk.startswith(b'Rar!'):
                    print(f"   Tipo file: RAR")
                elif first_chunk.startswith(b'7z'):
                    print(f"   Tipo file: 7Z")
                else:
                    print(f"   Tipo file: Sconosciuto (potrebbe essere valido)")
            else:
                print(f"⚠️  Nessun dato ricevuto")
            
            return True
        else:
            print(f"❌ Errore: Status Code {response.status_code}")
            print(f"   Response: {response.text[:500]}")
            return False
            
    except Exception as e:
        print(f"❌ Errore durante il download: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    print("=" * 80)
    print("TEST DOWNLOAD VIMM'S LAIR CON POST")
    print("=" * 80)
    print()
    
    # Test con i mediaId trovati
    test_cases = [
        ("58792", None, "Version 1.1 - .wbfs"),
        ("58792", "1", "Version 1.1 - .rvz"),
        ("9409", None, "Version 1.2 - .wbfs"),
        ("9409", "1", "Version 1.2 - .rvz"),
    ]
    
    for media_id, alt, description in test_cases:
        print(f"🔍 Test: {description}")
        print("-" * 80)
        success = test_download_post(media_id, alt, description.replace(" ", "_"))
        print()
        if not success:
            print("⚠️  Questo download potrebbe non funzionare")
        print("=" * 80)
        print()

