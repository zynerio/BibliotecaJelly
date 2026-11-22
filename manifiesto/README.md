# Manifiestos de actualización

Cada producto tiene su propio repositorio público de releases y su propia versión:

| Producto | Repositorio | Tag | Manifiesto |
|---|---|---|---|
| Escritorio | `BibliotecaJelly-desktop` | `v0.1.0` | `desktop.json` |
| Servidor web | `BibliotecaJelly-server` | `v1.4.0` | `server.json` |
| Android | `BibliotecaJelly-android` | `v2.4.0` | `android.json` |

La app de escritorio consulta:

```text
https://raw.githubusercontent.com/zynerio/BibliotecaJelly/main/manifiesto/desktop.json
```

El servidor consulta el mismo hub desde `api/update-check.php`.

## Al publicar una versión

1. Compila el producto localmente.
2. Crea el tag en su repositorio de releases, por ejemplo `v0.2.0`.
3. Adjunta el instalador, APK o ZIP a esa release.
4. Actualiza el manifiesto correspondiente:
   - `version`
   - `tag`
   - `publishedAt`
   - `notesUrl`
   - URL y SHA-256 de `downloads`.
5. Haz commit al hub.

No se usa `/releases/latest` en el hub: cada producto resuelve su propia release desde su repositorio.

## Código

Los repositorios públicos contienen únicamente READMEs, manifiestos y artefactos de release. El código fuente se mantiene fuera del hub.
