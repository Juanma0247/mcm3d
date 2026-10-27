# MCM3D

Workspace de **MCM3D** (nombre provisional): aplicativo Android de navegación con realidad aumentada que dibuja sobre la imagen de la cámara el camino a seguir desde la posición actual. Usa los sensores del celular y datos de rutas descargados previamente, y está pensado para funcionar sobre todo sin conexión.

## Repositorio

- Es un repositorio git. Remoto: `origin` → https://github.com/Juanma0247/mcm3d.git (rama `main`).
- El repositorio es **público**: nunca subir claves de API, keystores ni grabaciones reales de sensores o GPS (revelan ubicaciones del usuario). Las claves van en `local.properties`, que está ignorado por git.
- Subproyectos con repositorio propio: ninguno.
- Regla: después de cada cambio de archivos se hace commit y push a `origin/main`.

## Estructura

| Carpeta / archivo | Qué contiene |
|---|---|
| `CLAUDE.md` | Este archivo: idea general, estado, decisiones, ideas y backlog. |
| `docs/flujo-de-trabajo.md` | Guía para preparar el PC, conectar el celular (USB o Wi-Fi) y el ciclo editar, compilar, probar. |
| `app/` | (Pendiente) módulo Android en Kotlin. |

## Idea general

El celular funciona como una "radiografía" o ventana: al apuntarlo hacia donde va la ruta, la pantalla muestra la imagen de la cámara con el camino dibujado encima (una línea o cinta 3D pegada al suelo). Al girar el celular, el dibujo se desplaza de forma coherente, como si el camino existiera en el mundo real.

Fuentes de información:

- **GPS (GNSS)**: posición en el mundo.
- **Giroscopio, acelerómetro y magnetómetro**: orientación del celular (hacia dónde apunta la cámara) y detección de movimiento.
- **Cámara**: imagen de fondo sobre la que se dibuja.
- **Datos y algoritmos propios**: fusión de sensores, proyección del camino en pantalla, ajuste del camino a la vía.
- **Bases de datos en línea**: mapas y cálculo de rutas, descargados antes de salir para usarlos sin conexión.

Prioridades: funcionar offline y lograr la máxima eficiencia en velocidad, procesamiento, memoria y energía.

## Flujo de desarrollo (propuesto, pendiente de confirmar)

- Claude escribe el código en este workspace, compila con Gradle desde la terminal (`gradlew`) e instala en el celular físico con `adb` (USB o depuración inalámbrica). Android Studio es opcional (debugger y profilers).
- No se usa emulador: no tiene cámara ni sensores reales, que son el núcleo de la app.
- Claude puede abrir la app, leer `logcat`, tomar capturas de pantalla del celular y descargar registros; el usuario mueve el celular y cuenta lo que ve.
- Detalle completo en `docs/flujo-de-trabajo.md`.

## Entorno de este PC (revisado el 2026-10-27)

- git y GitHub CLI (`gh`) instalados, sesión iniciada como `Juanma0247`.
- **No** hay Android Studio ni Android SDK instalados.
- Java disponible: solo Java 8 (32 bits). El Android Gradle Plugin actual necesita JDK 17 o superior.
- Pendiente: decidir entre instalar Android Studio (trae SDK y JDK) o solo JDK 17 más las herramientas de línea de comandos del SDK.
- Celular de pruebas: pendiente (modelo y versión de Android).

## Estado

### Hecho

- 2026-10-27: creación de `CLAUDE.md`, `.gitignore` y `docs/flujo-de-trabajo.md`; repositorio conectado a GitHub.

### En curso

- Preparar el entorno de compilación y la conexión con el celular.
- Siguiente paso: discusión de la idea (dificultades, alcance, interfaz, stack, optimización).

### Por implementar (borrador, se ajusta tras la discusión)

1. Esqueleto del proyecto (Kotlin, Gradle, script de despliegue al celular).
2. Pantalla de diagnóstico: precisión del GPS, orientación, frecuencia de sensores, calibración de la brújula.
3. Cámara en vivo con un marcador fijo hacia un punto conocido (validar que orientación y proyección cuadran).
4. Obtención de rutas en línea y almacenamiento para uso sin conexión.
5. Dibujo del camino como línea 3D proyectada sobre la cámara.
6. Optimización de energía, memoria y rendimiento.

### Ideas

- Modo grabación y reproducción de sensores: grabar una caminata (GPS + IMU) y reproducirla en el PC para ajustar los algoritmos sin salir a caminar cada vez.
- Capa de depuración en pantalla (precisión, rumbo, FPS, consumo) activable en builds de desarrollo.
- Compilar el APK en GitHub Actions para instalarlo desde el celular sin estar en el PC.

### Temas para la discusión

- Mapas y rutas offline: fuente de datos (OpenStreetMap), motor de rutas, formato de almacenamiento.
- Precisión del GPS (varios metros) frente a un camino que debe verse "pegado" al suelo.
- Orientación: fusión de sensores, interferencias magnéticas, calibración, deriva.
- ARCore (seguimiento visual, Geospatial API) frente a una solución propia solo con sensores.
- Motor de render: OpenGL ES, Vulkan, Filament/SceneView o Canvas 2D.
- Interfaz y experiencia de uso.
- Consumo de batería de cámara + GPS + render.

## Decisiones tomadas

- (Ninguna todavía.)
