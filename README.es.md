# Tottodrillo 🎮

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-2.5.0-blue.svg)

**Tottodrillo** es una aplicación Android moderna y minimalista para explorar, buscar y descargar ROMs de [CrocDB](https://crocdb.net), la base de datos pública de juegos retro.

## 🌍 Otros Idiomas / Other Languages

Este README también está disponible en otros idiomas:

- [🇬🇧 English](README.md)
- [🇮🇹 Italiano](README.it.md)
- [🇩🇪 Deutsch](README.de.md)
- [🇯🇵 日本語](README.ja.md)
- [🇫🇷 Français](README.fr.md)
- [🇨🇳 简体中文](README.zh-CN.md)
- [🇵🇹 Português](README.pt.md)

---

## ✨ Características Principales

### 🔍 Búsqueda de Información de ROMs (NUEVO en v2.5.0)
- **Proveedores Múltiples**: Elige entre Gamefaqs y MobyGames para buscar información de ROMs
- **Proveedor Configurable**: Selecciona tu proveedor preferido en la configuración
- **Integración Gamefaqs**: Busca información de ROMs directamente en Gamefaqs
- **Integración MobyGames**: Busca información de ROMs en MobyGames (característica existente)
- **Texto de Botón Dinámico**: El texto del botón de búsqueda cambia según el proveedor seleccionado

### 🔍 Exploración y Búsqueda
- **Pantalla de Inicio** con ROMs destacadas, plataformas populares, favoritos y ROMs recientes
- **Exploración de Plataformas** organizadas por marca (Nintendo, PlayStation, Sega, Xbox, etc.) con secciones colapsables/expandibles
- **Búsqueda Avanzada** con debounce automático (500ms) para optimizar las consultas
- **Filtros Múltiples** para plataformas y regiones con chips interactivos
- **Paginación Infinita** con carga diferida automática
- **Visualización de ROMs** con portadas centradas y proporcionadas

### 📥 Descarga e Instalación
- **Descargas en Segundo Plano** con WorkManager para confiabilidad
- **Seguimiento de Progreso en Tiempo Real** con porcentaje, bytes descargados y velocidad
- **Notificaciones Interactivas** con acciones "Cancelar descarga" y "Cancelar instalación"
- **Ruta Personalizada** para guardar archivos en cualquier carpeta (incluida tarjeta SD externa)
- **Instalación Automática/Manual**:
  - Soporte para archivos ZIP (extracción)
  - Soporte para archivos no comprimidos (copia/movimiento)
  - Selector de carpetas para destino personalizado
- **Compatibilidad ES-DE**:
  - Instalación automática en la estructura de carpetas de ES-DE
  - Selección de carpeta ROMs ES-DE
  - Organización automática por `mother_code` (ej. `fds/`, `nes/`, etc.)
- **Gestión de Archivos**:
  - Sobrescribir archivos existentes (no elimina otros archivos en la carpeta)
  - Eliminación opcional del archivo original después de la instalación
  - Gestión del historial de descargas y extracciones
- **Opciones Avanzadas**:
  - Descargas solo WiFi para ahorrar datos móviles
  - Verificación de espacio disponible antes de descargar
  - Notificaciones configurables

### 💾 Gestión de ROMs
- **Favoritos** con persistencia en archivos
- **ROMs Recientes** (últimas 25 abiertas) con persistencia en archivos
- **Estado de Descarga/Instalación** para cada enlace con actualización automática
- **Iconos de Estado**:
  - Descarga en progreso con indicador de progreso
  - Instalación en progreso con porcentaje
  - Instalación completada (icono verde)
  - Instalación fallida (icono rojo, clicable para reintentar)
- **Abrir Carpetas** de instalación directamente desde la app

### 🎨 Diseño e Interfaz
- **Material Design 3** con tema oscuro/claro automático
- **Interfaz Minimalista** y moderna
- **Animaciones Suaves** con Jetpack Compose
- **Portadas** con carga diferida (Coil) y centrado automático
- **Logos de Plataformas** SVG cargados desde assets con fallback
- **Insignias de Región** con banderas emoji
- **Tarjetas de ROMs** con ancho máximo uniforme (180dp)

### ⚙️ Configuración
- **Búsqueda de Información de ROMs**:
  - Elige proveedor de búsqueda (Gamefaqs o MobyGames)
  - Gamefaqs es el proveedor predeterminado
- **Configuración de Descarga**:
  - Selección de carpeta de descarga personalizada
  - Visualización de espacio disponible
  - Descargas solo WiFi
  - Notificaciones on/off
- **Configuración de Instalación**:
  - Eliminar archivo original después de la instalación
  - Compatibilidad ES-DE con selección de carpeta
- **Gestión de Historial**:
  - Borrar historial de descargas e instalaciones (con confirmación)
- **Información de la App**:
  - Versión de la app
  - Enlace a GitHub
  - Sección de soporte

## 📱 Capturas de Pantalla

![Pantalla de Inicio de Tottodrillo](screen.jpg)

## 🏗️ Arquitectura

La aplicación sigue **Clean Architecture** con separación por capas:

```
app/
├── data/
│   ├── mapper/              # Conversión API → Domain
│   ├── model/               # Modelos de datos (API, Platform)
│   ├── remote/               # Retrofit, servicio API
│   ├── repository/           # Implementaciones de repositorio
│   ├── receiver/             # BroadcastReceiver para notificaciones
│   └── worker/               # Workers de WorkManager (Download, Extraction)
├── domain/
│   ├── manager/              # Gestores de lógica de negocio (Download, Platform)
│   ├── model/                # Modelos de dominio (UI)
│   └── repository/           # Interfaces de repositorio
└── presentation/
    ├── components/            # Componentes UI reutilizables
    ├── common/                # Clases de estado UI
    ├── detail/                # Pantalla de detalles de ROM
    ├── downloads/             # Pantalla de descargas
    ├── explore/               # Pantalla de exploración de plataformas
    ├── home/                  # Pantalla de inicio
    ├── navigation/            # Grafo de navegación
    ├── platform/              # Pantalla de ROMs por plataforma
    ├── search/                # Pantalla de búsqueda
    ├── settings/              # Pantalla de configuración
    └── theme/                 # Sistema de temas
```

## 🛠️ Stack Tecnológico

### Core
- **Kotlin** - Lenguaje principal
- **Jetpack Compose** - Toolkit de UI moderno
- **Material 3** - Sistema de diseño

### Arquitectura
- **MVVM** - Patrón arquitectónico
- **Hilt** - Inyección de dependencias
- **Coroutines & Flow** - Concurrencia y reactividad
- **StateFlow** - Gestión de estado reactivo

### Redes
- **Retrofit** - Cliente HTTP
- **OkHttp** - Capa de red
- **Gson** - Parsing JSON
- **Coil** - Carga de imágenes con soporte SVG

### Almacenamiento y Persistencia
- **DataStore** - Preferencias persistentes
- **WorkManager** - Tareas en segundo plano confiables
- **File I/O** - Gestión de archivos `.status` para seguimiento de descargas/instalaciones

### Navegación
- **Navigation Compose** - Enrutamiento entre pantallas
- **Safe Navigation** - Gestión de back stack para evitar pantallas vacías

### Tareas en Segundo Plano
- **DownloadWorker** - Descarga de archivos en segundo plano con servicio en primer plano
- **ExtractionWorker** - Extracción/copia de archivos en segundo plano
- **Notificaciones en Primer Plano** - Notificaciones interactivas con acciones

## 🚀 Configuración

### Prerrequisitos
- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK API 34
- Gradle 8.2+

### Instalación

1. **Clonar el repositorio**
```bash
git clone https://github.com/mccoy88f/Tottodrillo.git
cd Tottodrillo
```

2. **Abrir en Android Studio**
   - File → Open → Selecciona la carpeta del proyecto

3. **Sincronizar Gradle**
   - Android Studio sincronizará automáticamente las dependencias

4. **Compilar y Ejecutar**
   - Selecciona un dispositivo/emulador
   - Run → Run 'app'

### Configuración

No se requiere ninguna clave de API. La aplicación utiliza las API públicas de CrocDB:
- URL Base: `https://api.crocdb.net/`
- Documentación: [CrocDB API Docs](https://github.com/cavv-dev/crocdb-api)

## 📦 Compilación

### Compilación Debug
```bash
./gradlew assembleDebug
```

### Compilación Release
```bash
./gradlew assembleRelease
```

El APK se generará en: `app/build/outputs/apk/`

## 🎯 Características Detalladas

### Gestor de Descargas
- Descargas múltiples simultáneas
- Seguimiento de progreso para cada descarga
- Cancelación de descargas en curso
- Manejo de errores con reintento automático
- Verificación de espacio disponible
- Soporte para tarjeta SD externa

### Instalación
- Extracción de archivos ZIP
- Copia/movimiento de archivos no comprimidos
- Seguimiento de progreso durante la instalación
- Manejo de errores con icono rojo clicable para reintentar
- Actualización automática de la UI después de la instalación
- Abrir carpeta de instalación

### Compatibilidad ES-DE
- Habilitar/deshabilitar compatibilidad
- Selección de carpeta ROMs ES-DE
- Instalación automática en la estructura correcta
- Mapeo automático `mother_code` → carpeta

### Gestión de Historial
- Archivos `.status` para seguimiento de descargas/instalaciones
- Formato multi-línea para soportar múltiples descargas del mismo archivo
- Borrar historial con confirmación del usuario

## 🎯 Roadmap / To Do

Funcionalidades planificadas para futuras versiones:

- [ ] **Implementación de estructura multi-fuente**
  - Soporte para múltiples fuentes de ROMs además de CrocDB
  - Configuración y selección de fuentes en la configuración
  - Unificación de resultados de diferentes fuentes

- [ ] **Soporte para ScreenScraper.fr**
  - Integración con API de ScreenScraper para enriquecer datos de ROMs
  - Mejora de nombres, descripciones e imágenes mediante cuenta privada del usuario
  - Configuración de credenciales de ScreenScraper en la configuración
  - Fallback automático si la cuenta no está configurada

- [ ] **Listas personalizadas de ROMs y descarga masiva**
  - Crear listas personalizadas de ROMs
  - Guardar y gestionar múltiples listas
  - Descarga masiva de todas las ROMs en una lista
  - Gestión de prioridades y cola para múltiples descargas

## 🌐 Localización

La aplicación actualmente admite 8 idiomas:
- 🇮🇹 Italiano (por defecto)
- 🇬🇧 Inglés
- 🇪🇸 Español
- 🇩🇪 Alemán
- 🇯🇵 Japonés
- 🇫🇷 Francés
- 🇨🇳 Chino simplificado
- 🇵🇹 Portugués

La aplicación utiliza automáticamente el idioma del dispositivo. Si el idioma no está soportado, usa el italiano por defecto.

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Por favor:

1. Haz fork del proyecto
2. Crea una rama para tu característica (`git checkout -b feature/AmazingFeature`)
3. Confirma tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Envía a la rama (`git push origin feature/AmazingFeature`)
5. Abre una Pull Request

### Pautas
- Sigue las convenciones de Kotlin
- Usa Jetpack Compose para la UI
- Escribe tests cuando sea posible
- Documenta las APIs públicas
- Mantén el código limpio y legible

## 📄 Licencia

Este proyecto está publicado bajo la Licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.

## 🙏 Agradecimientos

### APIs y Base de Datos
- [CrocDB](https://crocdb.net) por las API públicas y la base de datos de ROMs
- [cavv-dev](https://github.com/cavv-dev) por la base de datos de ROMs y la API

### Logos de Plataformas
Los logos SVG de las plataformas son proporcionados por:
- [alekfull-nx-es-de](https://github.com/anthonycaccese/alekfull-nx-es-de) - Repositorio de logos para ES-DE

### Comunidad
- Comunidad de gaming retro por el apoyo y los comentarios
- Todos los contribuidores y probadores de la app

## ⚠️ Descargo de Responsabilidad

**IMPORTANTE**: Esta aplicación está creada con fines educativos y de investigación.

- El uso de ROMs requiere la **posesión legal** del juego original
- Respeta siempre las **leyes de copyright** de tu país
- La aplicación no proporciona ROMs, solo facilita el acceso a bases de datos públicas
- El autor no asume ninguna responsabilidad por el uso indebido de la aplicación

## 📞 Contacto

**Autor**: mccoy88f

**Repositorio**: [https://github.com/mccoy88f/Tottodrillo](https://github.com/mccoy88f/Tottodrillo)

**Issues**: Si encuentras errores o tienes sugerencias, abre un [Issue](https://github.com/mccoy88f/Tottodrillo/issues)

## ☕ Apóyame

Si te gusta este proyecto y quieres apoyarme, ¡puedes invitarme a un café! 🍺

Tu apoyo me ayuda a continuar el desarrollo y mejorar la aplicación.

<a href="https://www.buymeacoffee.com/mccoy88f">BUY ME A COFFEE!</a>

[También puedes invitarme a un café con PayPal 🍻](https://paypal.me/mccoy88f?country.x=IT&locale.x=it_IT)

---

**Made with ❤️ for the retro gaming community**

