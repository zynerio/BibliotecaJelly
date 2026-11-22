# BibliotecaJelly

<p align="center"><img width="256" height="256" alt="BJelly-favicon" src="https://github.com/user-attachments/assets/87dafa6e-df0c-499f-96c9-520a5558ba3c" /></p>


Catálogo offline para Jellyfin. Este repositorio es el **hub público** de descargas y actualizaciones; el código fuente no se publica aquí.

## Productos

| Producto | Versión | Repositorio de releases | Descarga |
|---|---|---|---|
| Android | 2.4.0 | [BibliotecaJelly-android](https://github.com/zynerio/BibliotecaJelly-android) | [Última release](https://github.com/zynerio/BibliotecaJelly-android/releases/latest) |
| Escritorio | 0.1.0 | [BibliotecaJelly-desktop](https://github.com/zynerio/BibliotecaJelly-desktop) | [Última release](https://github.com/zynerio/BibliotecaJelly-desktop/releases/latest) |
| Servidor web | 1.4.0 | [BibliotecaJelly-server](https://github.com/zynerio/BibliotecaJelly-server) | [Última release](https://github.com/zynerio/BibliotecaJelly-server/releases/latest) |

## Contenido del hub

- [`manifiesto/`](manifiesto/) — versiones y enlaces de descarga.
- [`android/`](android/) — enlace a las releases Android.
- [`desktop/`](desktop/) — enlace a las releases de escritorio.
- [`server/`](server/) — enlace a las releases del servidor.
- [`shared/`](shared/) — recursos de marca compartidos.

## Actualizaciones

La app de escritorio consulta:

```text
manifiesto/desktop.json
```

El servidor consulta:

```text
manifiesto/server.json
```

Android tiene su propio manifiesto en `manifiesto/android.json` para integraciones futuras.

## Código y licencias

El código fuente se mantiene fuera de los repositorios públicos. Las releases contienen únicamente los artefactos de distribución.

> **Servidor:** el ZIP de instalación incluye el código PHP necesario para ejecutar la aplicación; al descargarlo, ese código es visible.
