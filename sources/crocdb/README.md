# Sorgente CrocDB

Sorgente ufficiale per Tottodrillo che si connette all'API pubblica di CrocDB.

## Installazione

1. Scarica il file `crocdb-source.zip`
2. Apri Tottodrillo
3. Vai su Impostazioni > Sorgenti
4. Tocca "Installa sorgente"
5. Seleziona il file ZIP scaricato
6. La sorgente verrà installata automaticamente

## Struttura del pacchetto

```
crocdb-source.zip
├── source.json          # Metadata della sorgente
├── api_config.json      # Configurazione degli endpoint API
├── platform_mapping.json  # Mapping piattaforme (mother_code -> codici CrocDB)
└── README.md            # Questo file
```

## Formato sorgente

Per creare una sorgente personalizzata, segui questa struttura:

### source.json
```json
{
  "id": "identificatore-univoco",
  "name": "Nome Visualizzato",
  "version": "1.0.0",
  "description": "Descrizione della sorgente",
  "author": "Nome Autore",
  "baseUrl": "https://api.example.com",
  "minAppVersion": "1.1.0",
  "apiPackage": "com.tottodrillo.sources.example"
}
```

### api_config.json
Vedi la documentazione completa nel repository del progetto.

## Supporto

Per problemi o domande, apri una issue su GitHub.

