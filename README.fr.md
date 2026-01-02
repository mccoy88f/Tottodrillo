# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-3.1.0-blue.svg)

**Tottodrillo** est une application Android moderne et minimaliste pour explorer, rechercher et télécharger des ROMs depuis plusieurs sources. L'application prend en charge l'installation dynamique de sources via des packages ZIP, permettant d'ajouter de nouvelles sources ROM sans mettre à jour l'application.

**Dépôt de Sources**: [Tottodrillo-Source](https://github.com/mccoy88f/Tottodrillo-Source) - Contient toutes les définitions de sources et guides de développement.

## 🌍 Autres Langues / Other Languages

Ce README est également disponible dans d'autres langues :

- [🇬🇧 English](README.md)
- [🇮🇹 Italiano](README.it.md)
- [🇪🇸 Español](README.es.md)
- [🇩🇪 Deutsch](README.de.md)
- [🇯🇵 日本語](README.ja.md)
- [🇨🇳 简体中文](README.zh-CN.md)
- [🇵🇹 Português](README.pt.md)

---

## ✨ Caractéristiques Principales

### 🎮 Intégration IGDB (NOUVEAU en v2.7.0)
- **Importation de Métadonnées**: Recherchez et importez des métadonnées riches pour vos ROMs depuis Internet Game Database (IGDB)
- **Informations Complètes sur les Jeux**: Importez titre, couverture, description, scénario, genres, développeur, éditeur, note, captures d'écran et plus encore
- **Configuration Facile**: Configurez votre Client ID et Secret IGDB directement dans les Paramètres
- **Correspondance Intelligente**: Visualisez les plateformes correspondantes et confirmez avant d'importer les métadonnées
- **Détails de ROM Enrichis**: Enrichissez votre collection de ROMs avec des métadonnées professionnelles et des couvertures de haute qualité d'IGDB

### 🔍 Recherche d'Informations ROMs
- **Fournisseurs Multiples**: Choisissez entre Gamefaqs et MobyGames pour la recherche d'informations ROMs
- **Fournisseur Configurable**: Sélectionnez votre fournisseur préféré dans les paramètres
- **Intégration Gamefaqs**: Recherchez des informations ROM directement sur Gamefaqs
- **Intégration MobyGames**: Recherchez des informations ROM sur MobyGames
- **Texte de Bouton Dynamique**: Le texte du bouton de recherche change selon le fournisseur sélectionné

### 🔍 Exploration et Recherche
- **Écran d'Accueil** avec ROMs en vedette, plateformes populaires, favoris et ROMs récentes
- **Exploration des Plateformes** organisées par marque (Nintendo, PlayStation, Sega, Xbox, etc.) avec sections repliables/dépliables
- **Recherche Avancée** avec debounce automatique (500ms) pour optimiser les requêtes
- **Filtres Multiples** pour plateformes et régions avec puces interactives
- **Pagination Infinie** avec chargement différé automatique
- **Affichage des ROMs** avec couvertures centrées et proportionnées

### 📥 Téléchargement et Installation
- **Téléchargements en Arrière-plan** avec WorkManager pour la fiabilité
- **Suivi de Progression en Temps Réel** avec pourcentage, octets téléchargés et vitesse
- **Notifications Interactives** avec actions "Annuler le téléchargement" et "Annuler l'installation"
- **Chemin Personnalisé** pour sauvegarder les fichiers dans n'importe quel dossier (y compris carte SD externe)
- **Installation Automatique/Manuelle** :
  - Support des archives ZIP (extraction)
  - Support des fichiers non-archive (copie/déplacement)
  - Sélecteur de dossier pour destination personnalisée
- **Compatibilité ES-DE** :
  - Installation automatique dans la structure de dossiers ES-DE
  - Sélection du dossier ROMs ES-DE
  - Organisation automatique par `mother_code` (ex. `fds/`, `nes/`, etc.)
- **Gestion des Fichiers** :
  - Écrasement des fichiers existants (ne supprime pas les autres fichiers du dossier)
  - Suppression optionnelle du fichier original après installation
  - Gestion de l'historique des téléchargements et extractions
- **Options Avancées** :
  - Téléchargements WiFi uniquement pour économiser les données mobiles
  - Vérification de l'espace disponible avant le téléchargement
  - Notifications configurables

### 💾 Gestion des ROMs
- **Favoris** avec persistance basée sur fichiers
- **ROMs Récentes** (25 dernières ouvertes) avec persistance basée sur fichiers
- **État de Téléchargement/Installation** pour chaque lien avec mise à jour automatique
- **Icônes d'État** :
  - Téléchargement en cours avec indicateur de progression
  - Installation en cours avec pourcentage
  - Installation terminée (icône verte)
  - Installation échouée (icône rouge, cliquable pour réessayer)
- **Ouverture des Dossiers** d'installation directement depuis l'app

### 🎨 Design et Interface
- **Material Design 3** avec thème sombre/clair automatique
- **Interface Minimaliste** et moderne
- **Animations Fluides** avec Jetpack Compose
- **Pochette d'Art** avec chargement différé (Coil) et centrage automatique
- **Logos de Plateformes** SVG chargés depuis les assets avec fallback
- **Badges de Région** avec drapeaux emoji
- **Cartes ROM** avec largeur maximale uniforme (180dp)

### ⚙️ Paramètres (Redessiné en v2.7.0)
- **Structure en Arbre avec Groupes Dépliables**: Paramètres organisés en 8 catégories repliables pour une meilleure navigation
- **Recherche d'Informations ROMs** :
  - Choisir le fournisseur de recherche (Gamefaqs ou MobyGames)
  - Gamefaqs est le fournisseur par défaut
  - Paramètres d'intégration IGDB (configuration Client ID et Secret)
- **Configuration du Téléchargement** :
  - Sélection du dossier de téléchargement personnalisé
  - Affichage de l'espace disponible
  - Gestion des permissions de stockage (Android 11+)
  - Téléchargements WiFi uniquement
  - Notifications activées/désactivées (pour téléchargements, installations et mises à jour)
- **Configuration de l'Installation** :
  - Suppression du fichier original après installation
  - Compatibilité ES-DE avec sélection de dossier
- **Gestion de l'Historique** :
  - Effacement de l'historique des téléchargements et extractions (avec confirmation)
- **Informations sur l'App** (Toujours visible) :
  - Version de l'app
  - Lien GitHub
  - Section de support

## 📱 Captures d'Écran

![Écran d'Accueil de Tottodrillo](screen.jpg)

## 🏗️ Architecture

L'application suit une **Clean Architecture** avec séparation en couches :

```
app/
├── data/
│   ├── mapper/              # Conversion API → Domain
│   ├── model/               # Modèles de données (API, Platform)
│   ├── remote/               # Retrofit, service API
│   ├── repository/           # Implémentations de repository
│   ├── receiver/             # BroadcastReceiver pour notifications
│   └── worker/               # Workers WorkManager (Download, Extraction)
├── domain/
│   ├── manager/              # Gestionnaires de logique métier (Download, Platform)
│   ├── model/                # Modèles de domaine (UI)
│   └── repository/           # Interfaces de repository
└── presentation/
    ├── components/            # Composants UI réutilisables
    ├── common/                # Classes d'état UI
    ├── detail/                # Écran de détails ROM
    ├── downloads/             # Écran des téléchargements
    ├── explore/               # Écran d'exploration des plateformes
    ├── home/                  # Écran d'accueil
    ├── navigation/            # Graphe de navigation
    ├── platform/              # Écran des ROMs par plateforme
    ├── search/                # Écran de recherche
    ├── settings/              # Écran des paramètres
    └── theme/                 # Système de thème
```

## 🛠️ Stack Technologique

### Core
- **Kotlin** - Langage principal
- **Jetpack Compose** - Toolkit UI moderne
- **Material 3** - Système de design

### Architecture
- **MVVM** - Modèle architectural
- **Hilt** - Injection de dépendances
- **Coroutines & Flow** - Concurrence et réactivité
- **StateFlow** - Gestion d'état réactive

### Réseau
- **Retrofit** - Client HTTP
- **OkHttp** - Couche réseau
- **Gson** - Parsing JSON
- **Coil** - Chargement d'images avec support SVG

### Stockage et Persistance
- **DataStore** - Préférences persistantes
- **WorkManager** - Tâches en arrière-plan fiables
- **File I/O** - Gestion des fichiers `.status` pour suivre les téléchargements/installations

### Navigation
- **Navigation Compose** - Routage entre écrans
- **Safe Navigation** - Gestion de la pile de retour pour éviter les écrans vides

### Tâches en Arrière-plan
- **DownloadWorker** - Téléchargement de fichiers en arrière-plan avec service au premier plan
- **ExtractionWorker** - Extraction/copie de fichiers en arrière-plan
- **Notifications au Premier Plan** - Notifications interactives avec actions

## 🚀 Configuration

### Prérequis
- Android Studio Hedgehog (2023.1.1) ou supérieur
- JDK 17
- Android SDK API 34
- Gradle 8.2+

### Installation

1. **Cloner le dépôt**
```bash
git clone https://github.com/mccoy88f/Tottodrillo.git
cd Tottodrillo
```

2. **Ouvrir dans Android Studio**
   - Fichier → Ouvrir → Sélectionner le dossier du projet

3. **Synchroniser Gradle**
   - Android Studio synchronisera automatiquement les dépendances

4. **Compiler et Exécuter**
   - Sélectionner un appareil/émulateur
   - Exécuter → Exécuter 'app'

### Configuration

Aucune clé API n'est requise. L'application utilise les API publiques de CrocDB :
- URL de base : `https://api.crocdb.net/`
- Documentation : [CrocDB API Docs](https://github.com/cavv-dev/crocdb-api)

## 📦 Compilation

### Compilation Debug
```bash
./gradlew assembleDebug
```

### Compilation Release
```bash
./gradlew assembleRelease
```

L'APK sera généré dans : `app/build/outputs/apk/`

## 🎯 Fonctionnalités Détaillées

### Gestionnaire de Téléchargement
- Téléchargements multiples simultanés
- Suivi de progression pour chaque téléchargement
- Annulation des téléchargements en cours
- Gestion des erreurs avec nouvelle tentative automatique
- Vérification de l'espace disponible
- Support des cartes SD externes

### Installation
- Extraction d'archives ZIP
- Copie/déplacement de fichiers non-archive
- Suivi de progression pendant l'installation
- Gestion des erreurs avec icône rouge cliquable pour réessayer
- Mise à jour automatique de l'UI après installation
- Ouverture du dossier d'installation

### Compatibilité ES-DE
- Activer/désactiver la compatibilité
- Sélection du dossier ROMs ES-DE
- Installation automatique dans la structure correcte
- Mapping automatique `mother_code` → dossier

### Gestion de l'Historique
- Fichiers `.status` pour suivre les téléchargements/installations
- Format multi-lignes pour supporter plusieurs téléchargements du même fichier
- Effacement de l'historique avec confirmation utilisateur

## 🌐 Localisation

L'application prend actuellement en charge 8 langues :
- 🇮🇹 Italien (par défaut)
- 🇬🇧 Anglais
- 🇪🇸 Espagnol
- 🇩🇪 Allemand
- 🇯🇵 Japonais
- 🇫🇷 Français
- 🇨🇳 Chinois simplifié
- 🇵🇹 Portugais

L'application utilise automatiquement la langue de l'appareil. Si la langue n'est pas prise en charge, elle utilise l'italien par défaut.

## 🤝 Contribuer

Les contributions sont les bienvenues ! Veuillez :

1. Forker le projet
2. Créer une branche pour votre fonctionnalité (`git checkout -b feature/AmazingFeature`)
3. Committer vos modifications (`git commit -m 'Add some AmazingFeature'`)
4. Pousser vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

### Lignes directrices
- Suivre les conventions Kotlin
- Utiliser Jetpack Compose pour l'UI
- Écrire des tests lorsque c'est possible
- Documenter les APIs publiques
- Garder le code propre et lisible

## 📄 Licence

Ce projet est publié sous la licence MIT. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

## 🙏 Remerciements

### APIs et Base de Données
- [CrocDB](https://crocdb.net) pour les API publiques et la base de données ROM
- [cavv-dev](https://github.com/cavv-dev) pour la base de données ROM et l'API

### Logos de Plateformes
Les logos SVG des plateformes sont fournis par :
- [alekfull-nx-es-de](https://github.com/anthonycaccese/alekfull-nx-es-de) - Dépôt de logos pour ES-DE

### Communauté
- Communauté de gaming rétro pour le support et les retours
- Tous les contributeurs et testeurs de l'app

## ⚠️ Avertissement

**IMPORTANT** : Cette application est créée à des fins éducatives et de recherche.

- L'utilisation de ROMs nécessite la **propriété légale** du jeu original
- Respectez toujours les **lois sur le droit d'auteur** de votre pays
- L'application ne fournit pas de ROMs, mais facilite uniquement l'accès aux bases de données publiques
- L'auteur n'assume aucune responsabilité pour l'utilisation abusive de l'application

## 📞 Contact

**Auteur** : mccoy88f

**Dépôt** : [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

**Issues** : Si vous trouvez des bugs ou avez des suggestions, ouvrez une [Issue](https://github.com/mccoy88f/Tottodrillo/issues)

## ☕ Me Soutenir

Si vous aimez ce projet et souhaitez me soutenir, vous pouvez m'offrir un café ! 🍺

Votre soutien m'aide à continuer le développement et à améliorer l'application.

<a href="https://www.buymeacoffee.com/mccoy88f">BUY ME A COFFEE!</a>

[Vous pouvez également m'offrir un café avec PayPal 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Made with ❤️ for the retro gaming community**

