<p align="center">
  <img src="data/img/BJelly-favicon.png" width="120" alt="BibliotecaJelly">
</p>

<h1 align="center">BibliotecaJelly</h1>

<p align="center">
  <strong>Catálogo web offline para Jellyfin</strong><br>
  Películas, series, música, libros y videoclips, disponibles aunque Jellyfin esté apagado.
</p>

<p align="center">
  <a href="https://github.com/zynerio/BibliotecaJelly">GitHub</a>
  ·
  <a href="README.md">Documentación técnica</a>
</p>

<p align="center">
  <img alt="Versión 1.1" src="https://img.shields.io/badge/versi%C3%B3n-1.1-2dd4a7">
  <img alt="PHP 7.4+" src="https://img.shields.io/badge/PHP-7.4%2B-777BB4?logo=php&logoColor=white">
  <img alt="Jellyfin 10.8+ / 12.x" src="https://img.shields.io/badge/Jellyfin-10.8%2B%20%7C%2012.x-00A4DC?logo=jellyfin&logoColor=white">
  <img alt="Almacenamiento JSON" src="https://img.shields.io/badge/almacenamiento-JSON-4ae3b5">
</p>

---

**BibliotecaJelly** es un **port web de la app Android del mismo nombre**. La aplicación Android fue el proyecto original; esta versión lleva su enfoque de catálogo offline a PHP + JavaScript, sin base de datos, sin Composer y sin proceso de compilación.

> Esta app es un **catálogo de consulta**. No reproduce películas ni sustituye a Jellyfin: importa los metadatos y las portadas para poder navegar por la biblioteca cuando el servidor no está disponible.

---

## Funcionalidades

### Catálogo

- **Películas** con portada, título, géneros, calidad, resolución, formato, duración, tamaño y datos técnicos.
- **Series** con temporadas, número de episodios y calidad/resolución/formato por temporada.
- **Música** organizada por **álbumes**, no por pistas sueltas:
  - artista, año, número de pistas, duración, formatos y bitrate;
  - listado completo de pistas dentro de la ficha del álbum.
- **Libros** con soporte para:
  - libros;
  - audiolibros;
  - cómics, detectados por género, etiqueta o extensión.
- **Videoclips** con artistas, álbum, calidad, resolución, formato y duración.
- **Bibliotecas reales** de Jellyfin: nombre, tipo, ruta, composición y recuento de contenido.
- Biblioteca mixta: muestra su composición y reparte su contenido en las pestañas correspondientes.

### Búsqueda, filtros y ordenación

- Búsqueda por título, artista o autor.
- Búsqueda insensible a acentos: `senor` encuentra `Señor`.
- Filtros combinables por:
  - género;
  - calidad;
  - resolución;
  - formato;
  - tipo de libro.
- Ordenación:
  - A → Z;
  - Z → A;
  - últimos añadidos;
  - más antiguos.
- Paginación de 50, 100 o 200 elementos.
- Chips de filtros activos, con eliminación individual.
- Vista de **cuadrícula** o **lista**.

### Fichas de detalle

- Modal de detalle en escritorio.
- En móvil, la tarjeta abre directamente la **ficha completa**.
- Datos técnicos del archivo, audios, subtítulos, ruta y fechas.
- Lista de pistas en álbumes.
- Botones de refresco unitario:
  - **Recargar portada**: actualiza solo la portada de ese elemento.
  - **Recargar detalles**: vuelve a leer de Jellyfin solo ese elemento.
- Los refrescos unitarios no tocan el resto del catálogo.

### Duplicados

- Agrupación de **películas y series** por nombre normalizado.
- Elimina año, acentos y separadores para comparar.
- Muestra las copias con calidad, resolución, tamaño y ruta.
- Pulsar una copia abre su ficha.

### Bibliotecas

- Lectura de las **vistas reales** de Jellyfin.
- Tipo de biblioteca, ruta y número total de elementos.
- Desglose por categoría: películas, series, álbumes, pistas, libros, audiolibros, videoclips, etc.
- Aviso de **biblioteca mixta** cuando contiene varios tipos de contenido.
- Fecha del último análisis.

### Sincronización

- Sincronización por pasos, dirigida por el navegador:
  - no usa cron;
  - no usa `exec`;
  - no necesita procesos en segundo plano;
  - se pausa al cerrar la pestaña y se reanuda al volver.
- Progreso en vivo, mensajes de estado, errores detallados y cancelación.
- Sincronización de **todas** las categorías o solo una.
- **Últimos añadidos** de todas las categorías.
- **Actualizar detalles** sin añadir elementos nuevos.
- **Recargar portadas** para forzar la descarga de todas las carátulas.
- **Refresco unitario** de portada o detalles desde la ficha de un elemento.
- **Limpiar sincronización** con opción de borrar también las portadas.
- Historial del último resultado dentro de la app.

### Portadas

- Descarga opcional de portadas para uso offline.
- Límite configurable por categoría; `0` significa descargar todas.
- Recarga forzada con nombre de archivo nuevo para evitar la caché del navegador.
- Imagen por defecto `data/img/sin_imagen.jpg` cuando no hay portada local ni remota.
- Si Jellyfin elimina una portada, la recarga unitaria o global borra la copia antigua.

### Interfaz

- Tema oscuro inspirado en Jellyfin.
- Diseño responsive.
- Logo y favicon propios.
- Sin fuentes externas, sin frameworks de UI y sin librerías JavaScript.
- Notificaciones tipo toast para las acciones.
- Estado del servidor visible en la cabecera.

---

## Modos de sincronización

| Modo | Qué hace |
|---|---|
| **Todo** | Importa todas las categorías y analiza las bibliotecas reales. Sustituye el catálogo y elimina lo que ya no exista en Jellyfin. |
| **Películas** | Sincroniza solo películas. |
| **Series** | Sincroniza solo series, temporadas y episodios. |
| **Música** | Sincroniza álbumes, pistas, formatos y carátulas. |
| **Libros** | Sincroniza libros, audiolibros y cómics. |
| **Videoclips** | Sincroniza videoclips y sus datos técnicos. |
| **Últimos añadidos** | Importa hasta 64 elementos recientes de cada categoría sin eliminar lo existente. |
| **Actualizar detalles** | Refresca metadatos de todo el catálogo sin añadir elementos nuevos. |
| **Recargar portadas** | Vuelve a descargar todas las portadas, incluidas las ya guardadas. No modifica el catálogo. |

La sincronización completa se puede pausar cerrando la pestaña y reanudar al volver a abrir la app.

---

## Cómo funciona

La app se divide en dos partes:

1. **Frontend** (`index.php`, `assets/app.js`, `assets/app.css`):
   - HTML, CSS y JavaScript vanilla.
   - Sin build, sin `node_modules`, sin frameworks.
2. **Backend PHP** (`api/*.php`):
   - Habla con la API de Jellyfin.
   - Guarda el catálogo en archivos JSON.
   - Evita problemas de CORS y no expone las credenciales al navegador.

No hay base de datos. Los datos persistentes son archivos JSON y las imágenes descargadas.

### Almacenamiento

```text
data/
├── config.json          # conexión y preferencias
├── movies.json          # películas
├── series.json          # series y temporadas
├── music.json           # álbumes y pistas
├── books.json           # libros, audiolibros y cómics
├── musicvideos.json     # videoclips
├── libraries.json       # bibliotecas reales
├── sync-status.json     # último estado de sincronización
├── sync-plan.json       # plan temporal de sincronización
├── posters/             # portadas descargadas
├── img/                 # logo, favicon, GitHub e imagen por defecto
└── logs/                # errores del backend
```

Las escrituras usan bloqueo de archivo para evitar lecturas a medio escribir.

---

## Compatibilidad con Jellyfin

- **Jellyfin 10.8+** y **12.x**.
- Probado en vivo contra **Jellyfin 12.2.0**.
- Autenticación por **API Key** o **usuario/contraseña**.
- El cliente envía las cabeceras modernas y antiguas para cubrir ambos esquemas:
  - `Authorization` en Jellyfin 12.x;
  - `X-Emby-Authorization` y `X-Emby-Token` en 10.x.
- El badge `Online` solo comprueba `/System/Info/Public`; para validar credenciales usa **Opciones → Probar conexión**.

---

## Requisitos

| Requisito | Detalle |
|---|---|
| **PHP 7.4 o superior** | Probado con PHP 8.3 y 8.5. |
| **Extensión JSON** | Incluida por defecto. |
| **Transporte HTTP** | cURL o `allow_url_fopen`. |
| **OpenSSL** | Solo si Jellyfin usa HTTPS. |
| **Escritura en `data/`** | El usuario del servidor web debe poder escribir. |
| **Servidor web** | Apache con `.htaccess` o nginx con la regla equivalente. |

No se necesita Composer, Node, Bun ni ninguna librería externa.

---

## Instalación

1. Copia el proyecto en el directorio web.
2. Asegura que `data/` existe y es escribible:

```bash
chmod -R u+rwX data
```

3. Abre la app en el navegador:

```text
http://TU_SERVIDOR/BibliotecaJelly/
```

4. Entra en **Opciones**:
   - URL del servidor Jellyfin;
   - API Key o usuario/contraseña;
   - prueba la conexión.
5. Pulsa **Sincronizar** y elige el modo deseado.

### Protección de datos

`data/` contiene credenciales y el catálogo. No debe ser accesible desde la web:

- Apache: el `.htaccess` incluido ya lo bloquea.
- nginx: añade una regla para devolver 404 en `/data/`.
- Las imágenes de `data/img` se sirven únicamente a través de `api/image.php`.

La app **no tiene autenticación propia**. Si se expone fuera de la red local, colócala detrás de un proxy con autenticación, una VPN o Basic Auth.

---

## Estructura del proyecto

```text
BibliotecaJelly/
├── index.php                  # interfaz y sprite de iconos
├── assets/
│   ├── app.css                # tema oscuro y responsive
│   └── app.js                 # lógica del cliente
├── api/
│   ├── _bootstrap.php         # configuración y utilidades
│   ├── _store.php             # almacenamiento JSON
│   ├── _jellyfin.php          # cliente HTTP de Jellyfin
│   ├── _sync.php              # motor de sincronización
│   ├── _sync_extra.php        # música, libros, videoclips y bibliotecas
│   ├── _query.php             # filtros y paginación
│   ├── state.php              # estado global
│   ├── config.php             # configuración
│   ├── connection-test.php    # prueba de conexión
│   ├── movies.php             # listado de películas
│   ├── series.php             # listado de series
│   ├── music.php              # listado de álbumes
│   ├── books.php              # listado de libros
│   ├── musicvideos.php        # listado de videoclips
│   ├── libraries.php          # bibliotecas reales
│   ├── facets.php             # filtros disponibles
│   ├── duplicates.php         # duplicados
│   ├── item.php               # elemento concreto
│   ├── image.php              # logo, favicon e imágenes de data/img
│   ├── refresh-item.php       # refresco unitario
│   ├── sync.php               # start / step / status / cancel
│   ├── clear.php              # limpiar catálogo
│   └── poster.php             # portadas locales o remotas
├── data/
│   ├── img/
│   │   ├── BJelly.png
│   │   ├── BJelly-favicon.png
│   │   ├── github.png
│   │   └── sin_imagen.jpg
│   └── ...                    # catálogo y portadas (no se versionan)
└── README.md
```

---

## Alcance de esta versión

Esta versión web es un port centrado en la **consulta y sincronización del catálogo**. No incluye:

- reproducción de medios;
- favoritos locales;
- historial completo de sincronizaciones;
- avisos de nuevas releases;
- pestaña **Otros** de vídeos/imágenes de la app Android;
- filtros avanzados de duplicados por nivel de coincidencia.

La versión Android sigue siendo la referencia para esas funciones nativas.

---

## Créditos

Proyecto personal de **Zynerio**, port web de la app Android **Biblioteca Jelly**.

- Repositorio: [github.com/zynerio/BibliotecaJelly](https://github.com/zynerio/BibliotecaJelly)
- Documentación técnica: [README.md](README.md)
- Jellyfin es una marca de sus respectivos autores. Este proyecto no está afiliado al equipo de Jellyfin.
