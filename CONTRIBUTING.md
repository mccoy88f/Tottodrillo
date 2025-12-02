# Contributing to Crocdb Friends

Grazie per il tuo interesse nel contribuire a Crocdb Friends! 🎉

## Come Contribuire

### Segnalare Bug

Se trovi un bug, apri una [issue](https://github.com/tuousername/crocdb-friends/issues) includendo:
- Descrizione del problema
- Steps per riprodurlo
- Comportamento atteso vs attuale
- Screenshot (se applicabile)
- Versione Android e dispositivo

### Proporre Feature

Per proporre nuove feature:
1. Controlla che non sia già stata proposta nelle [issues](https://github.com/tuousername/crocdb-friends/issues)
2. Apri una nuova issue con tag "enhancement"
3. Descrivi la feature e il suo valore aggiunto

### Pull Request

1. Fork il progetto
2. Crea un branch (`git checkout -b feature/AmazingFeature`)
3. Commit le modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Push al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

## Linee Guida di Sviluppo

### Code Style

- Segui le [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Usa 4 spazi per indentazione
- Max line length: 120 caratteri
- Organizza imports alfabeticamente

### Architettura

- Mantieni separazione tra layer (data/domain/presentation)
- Usa dependency injection con Hilt
- ViewModel per business logic
- Repository pattern per data access

### Testing

- Scrivi unit test per business logic
- Aggiungi UI test per flussi critici
- Assicurati che tutti i test passino prima del PR

### Commit Messages

Usa il formato [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add download queue feature
fix: resolve crash on search
docs: update README
style: format code
refactor: simplify download manager
test: add unit tests for repository
chore: update dependencies
```

## Setup Ambiente di Sviluppo

1. Installa Android Studio Hedgehog o superiore
2. SDK Android API 34
3. JDK 17
4. Clone e sincronizza Gradle

## Domande?

Apri una [discussion](https://github.com/tuousername/crocdb-friends/discussions) per domande generali.

Grazie per contribuire! 🙏
