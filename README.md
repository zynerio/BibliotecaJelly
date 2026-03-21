# Biblioteca Jelly (Cliente offline para Jellyfin)

Aplicación Android nativa desarrollada en Kotlin/Jetpack Compose que actúa como biblioteca de consulta offline 
para un servidor Jellyfin. 
Importa los metadatos de películas y series, los almacena en una base de datos local y permite consultarlos sin conexión.

![cap2](https://github.com/user-attachments/assets/0a20e7e9-a469-408f-a381-f2f4a27c7b4d)

## Changelog interno

### v2.2 (release pública)

- Ajuste de navegación con tecla atrás en móvil para evitar cierres accidentales de la app.
- En biblioteca:
  - primero cierra diálogos abiertos,
  - vuelve a "Todas las bibliotecas" cuando estás dentro de una biblioteca,
  - y solo sale de la app con doble pulsación atrás cuando no hay más navegación posible.
- En configuración:
  - primero cierra diálogos abiertos (borrar datos, conexión e historial),
  - vuelve a biblioteca si venías desde ahí,
  - y aplica doble pulsación atrás para salir cuando estás en la raíz.
- Versión de aplicación actualizada a `2.2` (`versionCode = 9`).

### v2.1 (release pública)

- Consolidación funcional de la rama 2.x en una única salida pública.
- Pestaña **Otros** completa en modo clásico:
  - agrupación ligera por tipo (Videos / Imágenes),
  - filtro por biblioteca dentro de la pestaña,
  - modal con búsqueda local y orden (`A-Z` / `Últimos añadidos`).
- Sincronización de **Otros** con botones dedicados:
  - Normal,
  - Rápida,
  - Últimos añadidos,
  - además de inclusión en sincronización `Todo`.
- Corrección de UX en novedades:
  - "Marcar vistas" ahora limpia correctamente en modo clásico incluso con desfases de hora servidor/dispositivo.
- Reconciliación de datos en `other_media`:
  - limpieza de elementos eliminados en servidor durante sincronización completa,
  - sin borrados agresivos en incremental por fecha.
- Versión de aplicación actualizada a `2.1` (`versionCode = 8`).

### v2.0 (interna, no publicada de forma independiente)

- Nueva visualización de biblioteca en dos modos:
  - **Modo clásico** (pestañas de Películas, Series y Otros),
  - **Modo avanzado** por bibliotecas con filtrado visual.
- Nueva pestaña **Otros** para contenido ligero (`Video` / `Photo`):
  - tarjetas por tipo (Videos e Imágenes),
  - modal con listado por nombre,
  - búsqueda local y orden (`A-Z` / `Últimos añadidos`) dentro del modal,
  - filtro por biblioteca dentro de la propia pestaña.
- Enriquecimiento de catálogo con metadatos de biblioteca:
  - asociación de películas y series a su biblioteca de origen,
  - persistencia local para navegación y filtros más precisos.
- Mejoras de experiencia previas consolidadas:
  - historial de sincronización gestionable,
  - diálogo con scroll en historiales largos,
  - año de producción visible en detalles,
  - opción para mostrar/ocultar ruta de archivo.
- Localización ampliada y consistente (ES/EN) en interfaz y mensajes de estado.
- Versión de aplicación actualizada a `2.0` (`versionCode = 7`).
- Nota: esta versión se usó como base interna y se consolidó directamente en la salida pública `2.1`.
### v1.6

- Configuración de servidor más robusta:
  - el botón pasa a **Guardar**,
  - la configuración se guarda siempre aunque no haya conexión.
- Validación de conexión no bloqueante:
  - comprobación en segundo plano con timeout,
  - evita cuelgues de la app cuando el servidor no está disponible.
- Corrección de autenticación inicial intermitente:
  - se evita enviar token en `AuthenticateByName`,
  - normalización de usuario/API key para reducir falsos errores de credenciales.
- Versión de aplicación actualizada a `1.6` (`versionCode = 6`).

### v1.5

- Nueva sincronización específica de **últimos añadidos**:
  - `Solo películas (últimos añadidos)`
  - `Solo series (últimos añadidos)`
- Esta opción evita refrescar todo el catálogo para traer únicamente lo nuevo.
- Aviso automático de nueva versión:
  - popup al detectar release más reciente en GitHub,
  - botón directo para descargar la actualización,
  - opción “Más tarde” para ocultar ese aviso hasta la siguiente release.
- Se mantiene el resto de modos existentes (Normal, Rápida y Solo detalles).
- Versión de aplicación actualizada a `1.5` (`versionCode = 5`).

### v1.4

- Sincronización diferenciada por modo:
  - **Normal** (catálogo + detalles),
  - **Rápida** (solo catálogo),
  - **Solo detalles** (sin refrescar catálogo).
- Historial de sincronizaciones:
  - acceso desde opciones,
  - acceso rápido pulsando “Última actualización”.
- Configuración reorganizada por pestañas (`Servidor`, `Sincronización`, `Datos`).
- Nuevo botón **Probar conexión** con popup de resultado.
- Filtros mejorados:
  - combinación de múltiples géneros,
  - combinación de múltiples detalles técnicos,
  - texto actualizado de acción a **“Limpiar detalles”**.
- Corrección de orden **“Últimos añadidos”** en series usando `DateCreated`.
- Versión de aplicación actualizada a `1.4` (`versionCode = 4`).

### v1.3

- Sincronización de películas reforzada para evitar saltos de fase y conservar metadatos clave.
- Corrección de mapeo técnico en películas:
  - formato normalizado correctamente,
  - calidad coherente con resoluciones reales (incluyendo casos límite).
- Nuevo orden de biblioteca por pestaña con persistencia local:
  - `A-Z`,
  - `Últimos añadidos`.
- Mejoras visuales del selector de orden:
  - estilo chips compacto,
  - aviso discreto cuando faltan fechas (`DateCreated`).
- Favoritos locales (películas y series):
  - botón estrella en tarjetas,
  - botón estrella en diálogos de detalle,
  - filtro rápido “Favoritos”.
- Novedades desde última visita:
  - contador por pestaña (`Películas (N)`, `Series (N)`),
  - acción “Marcar vistas”,
  - límite visual `99+` en contadores.
- Ajustes responsive y de renderizado de pósters fallback para evitar solapes en tarjetas.
- Versión de aplicación actualizada a `1.3` (`versionCode = 3`).

## Estado

- Versión interna base: **2.0**
- Versión pública actual: **2.2**
- Android mínimo: **7.0 (API 24)**
- Stack principal: **Kotlin + Jetpack Compose + Material 3**

## Funcionalidades destacadas (v2.1)

### Novedades de la versión

- Sincronización de “últimos añadidos” para películas y series sin refresco completo.
- Aviso in-app de actualización al detectar una release más reciente en GitHub.
- Modos de sincronización separados (normal / rápida / detalles).
- Historial de actualizaciones visible desde opciones y desde la fecha de última actualización.
- Botón “Probar conexión” en configuración.
- Filtros combinables por géneros y detalles técnicos.
- Modo avanzado de bibliotecas para explorar por colección, manteniendo modo clásico.
- Pestaña **Otros** con navegación ligera para vídeos e imágenes personales.

## Roadmap consolidado (2.0 -> 2.1)

- Integración completa de bibliotecas en modo avanzado.
- Portadas de biblioteca automáticas + selección manual.
- Soporte de contenido **Otros** (Video/Photo) en modelo local, repositorio, ViewModel y UI.
- Botonera de sincronización de **Otros** (normal / rápida / últimos añadidos).
- Corrección de "Marcar vistas" en modo clásico.
- Reconciliación de eliminados para `other_media` en sincronización completa.

## Checklist QA final (v2.1)

1. Configuración y conexión
  - Guardar configuración con servidor válido y comprobar estado activo.
  - Probar conexión con credenciales válidas e inválidas.
2. Sincronización por alcance
  - Ejecutar `Todo`, `Películas`, `Series` y `Otros` en modo normal.
  - Ejecutar `Todo`, `Películas`, `Series` y `Otros` en modo rápida.
  - Ejecutar `Películas`, `Series` y `Otros` en `Últimos añadidos`.
3. Contenido Otros
  - Ver pestaña `Otros` con tarjetas por tipo (Videos/Imágenes).
  - Abrir modal de tipo, buscar por título y alternar orden `A-Z` / `Últimos añadidos`.
  - Aplicar filtro por biblioteca dentro de `Otros` y validar conteos.
4. Novedades
  - Ver contador de novedades por pestaña.
  - Pulsar `Marcar vistas` y validar limpieza inmediata del contador.
5. Modo avanzado
  - Abrir bibliotecas, cambiar portadas manual/automático y comprobar persistencia.
  - Verificar cambio correcto entre tabs de contenido dentro de una biblioteca.
6. Integridad local
  - Borrar datos sincronizados por alcance y validar limpieza.
  - Ejecutar sync completa de `Otros` y validar reconciliación de eliminados.
7. Regresión visual
  - Revisar layout en móvil (portrait/landscape) y en tablet si disponible.
  - Confirmar textos ES/EN sin claves faltantes.
    
<img width="878" height="543" alt="cap6" src="https://github.com/user-attachments/assets/a4fbd26b-a6c4-46ba-a38a-d833a33a3016" />


## Funcionalidades destacadas (v1.6)

### Novedades de la versión

- Sincronización de “últimos añadidos” para películas y series sin refresco completo.
- Aviso in-app de actualización al detectar una release más reciente en GitHub.
- Modos de sincronización separados (normal / rápida / detalles).
- Historial de actualizaciones visible desde opciones y desde la fecha de última actualización.
- Botón “Probar conexión” en configuración.
- Filtros combinables por géneros y detalles técnicos.

![cap3](https://github.com/user-attachments/assets/3636ad6f-7bc3-4441-b197-cca6753da99f)


### Base consolidada (v1.3)

### Biblioteca y orden

- Pestañas separadas de **Películas** y **Series**.
- Orden por pestaña con persistencia local:
  - `A-Z`
  - `Últimos añadidos`

### Filtros

- Búsqueda por título.
- Filtro por género.
- Filtro técnico:
  - calidad,
  - formato,
  - resolución.
- Limpieza rápida de filtros activos.

### Favoritos

- Estrella en tarjetas de películas/series.
- Estrella en diálogos de detalle.
- Filtro “Favoritos” por pestaña.

### Novedades

- Contador desde última visita:
  - en pestañas (`Películas (N)`, `Series (N)`),
  - en bloque informativo dentro de la vista.
- Acción “Marcar vistas”.
- Formato visual `99+` para contadores altos.

<img width="1354" height="838" alt="img2ptablet" src="https://github.com/user-attachments/assets/db84513e-d450-4ea6-9f47-86d38791fdb7" />

## Arquitectura

- **Arquitectura**: MVVM
- **UI**: Jetpack Compose + Material 3
- **Persistencia local**: Room
- **Red**: Retrofit + OkHttp
- **Sincronización en segundo plano**: WorkManager
- **Cifrado de credenciales**: EncryptedSharedPreferences (androidx.security.crypto)
- **Imágenes**: Glide con caché local

### Capas principales

- **data/**
  - `LocalModels.kt`: entidades Room (`MovieEntity`, `SeriesEntity`, `SeasonEntity`, `EpisodeEntity`), DAOs y `BibliotecaDatabase`.
  - `RemoteModels.kt`: modelos de la API de Jellyfin (`AuthenticationResult`, `BaseItemDto`, `ItemsResponse`, etc.) e interfaz `JellyfinApi`.
  - `Repository.kt`:
    - `CredentialsStore`: lectura/escritura segura de configuración y credenciales con `EncryptedSharedPreferences`.
    - `JellyfinRepository` / `DefaultJellyfinRepository`: lógica de negocio, autenticación, sincronización incremental y acceso a Room.
    - `ServiceLocator`: creación de `Retrofit`, `OkHttpClient`, base de datos y repositorio.
    - `enqueueManualSync(...)`: utilitario para lanzar sincronizaciones con WorkManager.
  - `data/sync/JellyfinSyncWorker.kt`: `CoroutineWorker` que ejecuta la sincronización en segundo plano.

- **ui/**
  - `MainViewModel`: coordina configuración, estado de sincronización y búsquedas; expone `movies` y `series` como `StateFlow`.
  - `MainActivity`: host de Compose + `MainViewModel` y navegación simple entre configuración y biblioteca.
  - Composables principales en `MainActivity.kt`:
    - `ConfigScreen`: pantalla de configuración del servidor.
    - `LibraryScreen`: tabs “Películas” y “Series”.
    - `MoviesGrid`, `SeriesList`: listados con grid y lista.
    - `MovieDetailDialog`, `SeriesDetailDialog`: detalles con metadatos.

## Modelo de datos

### Películas (`MovieEntity`)

Tabla `movies`:

- `id` (PK, `String`): identificador de Jellyfin.
- `title` (`String`): título.
- `createdUtcMillis` (`Long?`): fecha de creación del ítem (UTC millis).
- `posterUrl` (`String?`): URL de portada.
- `format` (`String?`): contenedor (MKV, MP4, AVI, etc.).
- `quality` (`String?`): 480p, 720p, 1080p, 4K (derivado de la resolución).
- `resolution` (`String?`): por ejemplo `1920x1080`.
- `bitrateMbps` (`Double?`): tasa de bits en Mbps (aproximada).
- `fps` (`Double?`): no se calcula actualmente, reservado.
- `durationMinutes` (`Int?`): duración en minutos.
- `sizeGb` (`Double?`): tamaño estimado en GB.
- `audioLanguages` (`List<String>`): idiomas de audio.
- `subtitleLanguages` (`List<String>`): idiomas de subtítulos.
- `isFavorite` (`Boolean`): favorito local.

Índices:

- Índice por `title` para búsqueda eficiente.

### Series, temporadas y episodios

#### `SeriesEntity` (tabla `series`)

- `id` (PK, `String`)
- `title` (`String`)
- `createdUtcMillis` (`Long?`)
- `posterUrl` (`String?`)
- `totalSeasons` (`Int`)
- `totalEpisodes` (`Int`)
- `isFavorite` (`Boolean`)

Índice por `title`.

#### `SeasonEntity` (tabla `seasons`)

- `id` (PK, `String`)
- `seriesId` (`String`): FK a `SeriesEntity`.
- `seasonNumber` (`Int`)
- `format` (`String?`)
- `quality` (`String?`)
- `resolution` (`String?`)
- `bitrateMbps` (`Double?`)
- `fps` (`Double?`)
- `totalDurationMinutes` (`Int?`)
- `totalSizeGb` (`Double?`)
- `audioLanguages` (`List<String>`)
- `subtitleLanguages` (`List<String>`)
- `episodeCount` (`Int`)

Relación uno-a-muchos con `SeriesEntity` mediante `seriesId`.

#### `EpisodeEntity` (tabla `episodes`)

- `id` (PK, `String`)
- `seriesId` (`String`): FK a `SeriesEntity`.
- `seasonId` (`String`): FK a `SeasonEntity`.
- `seasonNumber` (`Int`)
- `episodeNumber` (`Int`)
- `title` (`String`)
- `durationMinutes` (`Int?`)
- `sizeGb` (`Double?`)

Relaciones:

- `SeriesWithSeasonsAndEpisodes`: modelo que agrupa `SeriesEntity` → `SeasonWithEpisodes` → `EpisodeEntity`.

### Conversores de tipos

- `RoomConverters` convierte `List<String>` ↔ `String` para almacenar listas de idiomas.

## Sincronización

### Modos de sincronización

- **Al iniciar la app**: `MainViewModel` detecta configuración previa y dispara `triggerStartupSync()`.
- **Al cerrarla**: `MainActivity.onStop` llama a `enqueueManualSync(...)`, que encola un `JellyfinSyncWorker`.
- **Manual**:
  - Botón “Sincronizar” en la `TopAppBar`.
  - `Swipe-to-refresh` en la vista principal (usando `accompanist-swiperefresh`).

### Lógica incremental

En `DefaultJellyfinRepository.syncIncremental`:

- Obtiene `lastSyncEpochMillis` del `CredentialsStore`.
- Lo convierte a ISO-8601 y lo pasa como `MinDateLastSaved` a:
  - `getItems(userId, IncludeItemTypes = "Movie", ...)`
  - `getItems(userId, IncludeItemTypes = "Series,Season,Episode", ...)`
- Solo se procesan los elementos cambiados desde la última sincronización.
- En modo “solo nuevos”, si todavía no existe una sincronización previa (`lastSyncEpochMillis` vacío), se realiza una carga completa inicial del catálogo para establecer la base incremental.
- Al finalizar, actualiza el timestamp de última sincronización.

### Progreso y logs

- El repositorio reporta progreso con `(processed, total)`:
  - La UI muestra barra de progreso y texto `Sincronizando X / Y`.
  - El `JellyfinSyncWorker` publica progreso en `WorkManager`.
- Logs con `Log.d`/`Log.e` con la etiqueta `JellyfinSync` para:
  - Inicio/fin de sincronización.
  - Volumen de datos recibidos.
  - Errores de red o HTTP durante la sincronización.

## Conexión y credenciales

### Pantalla de configuración

`ConfigScreen` incluye:

- Dirección del servidor (IP o dominio).
- Puerto.
- Usuario.
- Contraseña.
- API key (opcional).

### Almacenamiento seguro

- `CredentialsStore` usa `EncryptedSharedPreferences` para guardar:
  - `baseUrl` (servidor + puerto).
  - `username`, `password`.
  - `apiKey` (si se usa).
  - `accessToken` y `userId` obtenidos por autenticación.
  - Timestamp de última sincronización.

### Validación de conexión

`JellyfinRepository.authenticateAndValidateConnection()`:

- Si hay API key, la considera válida directamente.
- Si no, llama a `POST /Users/AuthenticateByName` con `{ "Username": "...", "Pw": "..." }`.
- Manejo de errores:
  - 401: `Usuario o contraseña incorrectos`.
  - `SocketTimeoutException`: `Tiempo de conexión agotado`.
  - `UnknownHostException`: `Servidor no disponible`.
  - Otros: `Error HTTP xxx` o `Error desconocido`.

La UI muestra estos mensajes en la pantalla de configuración.

## Uso de la API de Jellyfin

### Autenticación

- Endpoint: `POST /Users/AuthenticateByName`
- Cuerpo esperado:

```json
{
  "Username": "usuario",
  "Pw": "contraseña"
}
```

- Respuesta simplificada:

```json
{
  "AccessToken": "token",
  "User": {
    "Id": "user-id",
    "Name": "usuario"
  }
}
```

- El `AccessToken` se envía en siguientes peticiones vía cabecera:

```http
Authorization: MediaBrowser Token="TOKEN"
```

También se admite usar directamente una API key en esa cabecera.

### Listado de elementos

- Endpoint: `GET /Users/{userId}/Items`
- Parámetros relevantes usados:
  - `IncludeItemTypes=Movie` para películas.
  - `IncludeItemTypes=Series,Season,Episode` para series.
  - `Recursive=true`.
  - `Fields=MediaStreams,MediaSources,Path,PremiereDate,DateCreated,Width,Height,Overview`.
  - `MinDateLastSaved=...` para sincronización incremental.

Respuesta simplificada:

```json
{
  "Items": [
    {
      "Id": "item-id",
      "Name": "Título",
      "Type": "Movie",
      "Container": "mkv",
      "RunTimeTicks": 6000000000,
      "ImageTags": { "Primary": "tag123" },
      "MediaStreams": [
        {
          "Type": "Video",
          "Language": "es",
          "Width": 1920,
          "Height": 1080,
          "Bitrate": 8000000
        }
      ],
      "MediaSources": [
        {
          "Container": "mkv",
          "RunTimeTicks": 6000000000,
          "Size": 2147483648,
          "Bitrate": 8000000
        }
      ]
    }
  ]
}
```

Esta estructura se mapea a `BaseItemDto` y luego a las entidades de Room.

## Interfaz de usuario

### Tabs y listas

- Tabs principales:
  - **Películas**: grid de portadas + título (`LazyVerticalGrid`).
  - **Series**: lista con poster, título, número de temporadas y episodios (`LazyColumn`).
- Búsqueda:
  - Campo de texto que filtra por título en ambas secciones.

### Vista de detalle

- **Películas**:
  - Diálogo con:
    - Formato, calidad, resolución.
    - Bitrate, duración, tamaño.
    - Listado de idiomas de audio y subtítulos.
    - Acción de favorito (estrella).
- **Series**:
  - Diálogo con número total de temporadas y episodios.
  - La base de datos incluye estadísticas agregadas por temporada (`SeasonEntity`) para extender fácilmente la vista de detalle.
  - Acción de favorito (estrella).

### Sincronización en UI

- `Swipe-to-refresh` para lanzar sincronización manual.
- Botón “Sincronizar” en la barra superior.
- Barra de progreso lineal mostrando elementos procesados/total.
- Texto con timestamp de última sincronización.

### Gestión de imágenes

- `PosterImage` usa `AndroidView` + `Glide`:
  - Carga las portadas/pósters desde la URL generada a partir de `baseUrl` + `/Items/{id}/Images/Primary`.
  - Usa `ic_launcher_foreground` como placeholder mientras se carga.
  - Glide gestiona caché en disco y memoria; se puede ajustar el tamaño de las imágenes para optimizar espacio.

## Testing

### Pruebas unitarias

- `JellyfinParsingTest`:
  - Valida el parsing de la respuesta de autenticación (`AuthenticationResult`).
  - Valida el parsing de `ItemsResponse` con un ejemplo simplificado de película.

Se pueden añadir más pruebas que instancien `DefaultJellyfinRepository` con dobles de `JellyfinApi` y Room in-memory para cubrir más lógica de sincronización.

### Pruebas de integración (sugeridas)

- Usar `MockWebServer` para simular un servidor Jellyfin:
  - Responder a `AuthenticateByName` y `Items` con JSON de prueba.
  - Verificar flujo completo: configuración → autenticación → sincronización → datos en Room.
- Añadir pruebas de UI con `compose-ui-test` para validar búsqueda, tabs y diálogos de detalle.

## Instalación y ejecución

1. Clonar el repositorio y abrirlo en **Android Studio Iguana/Koala o superior**.
2. Sincronizar Gradle y esperar a que se descarguen las dependencias.
3. Ejecutar en un emulador o dispositivo físico con **Android 7.0 (API 24)** o superior.
4. En la primera ejecución:
   - Introducir dirección IP o dominio del servidor Jellyfin.
   - Introducir puerto (por defecto suele ser `8096` para HTTP).
   - Rellenar usuario/contraseña o API key.
  - Pulsar “Guardar” para guardar la configuración.
  - La app valida la conexión en segundo plano (sin bloquear); si no hay red, la configuración queda guardada y podrás reintentar desde “Probar conexión” o al sincronizar.

## Generar APK firmado

1. En Android Studio, ir a **Build > Generate Signed Bundle / APK...**.
2. Seleccionar **APK**.
3. Crear o elegir un **keystore** existente.
4. Seleccionar el módulo `app`.
5. Elegir tipo de build (`release`) y finalizar el asistente.
6. El APK firmado quedará disponible en `app/build/outputs/apk/release/`.

Este APK puede instalarse directamente en dispositivos Android compatibles.


