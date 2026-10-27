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
| `tools/connect-phone.ps1` | Conecta `adb` al celular por Wi-Fi sin pedir el puerto: prueba mDNS, el último puerto usado (`tools/.last-phone`, ignorado por git) y, si falla, escanea los puertos del celular. Es un respaldo para cuando el celular está en otra subred. Sin probar: el sistema de permisos bloqueó su ejecución. |
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

Escenario objetivo: senderismo en los alrededores de Manizales, donde no suele haber cobertura celular pero sí cielo abierto. El camino queda fijo en el mundo y la pantalla lo revela al mover el celular (estilo Pokémon GO). Muestra qué sendero tomar en cada bifurcación, el recorrido hecho (de dónde venimos) y el que falta (hacia dónde vamos). Las rutas se importan de otras apps (Relive, Komoot, Wikiloc) o se graban con la app.

## Flujo de desarrollo (propuesto, pendiente de confirmar)

- Claude escribe el código en este workspace, compila con Gradle desde la terminal (`gradlew`) e instala en el celular físico con `adb` (USB o depuración inalámbrica). Android Studio es opcional (debugger y profilers).
- No se usa emulador: no tiene cámara ni sensores reales, que son el núcleo de la app.
- Claude puede abrir la app, leer `logcat`, tomar capturas de pantalla del celular y descargar registros; el usuario mueve el celular y cuenta lo que ve.
- Detalle completo en `docs/flujo-de-trabajo.md`.

## Entorno de este PC (revisado el 2026-10-27)

- git y GitHub CLI (`gh`) instalados, sesión iniciada como `Juanma0247`.
- No hay Android Studio (decisión: trabajar sin IDE, ver "Decisiones tomadas").
- Temurin JDK 17.0.20 instalado con `winget` en `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`; el instalador fijó `JAVA_HOME` de sistema a esa ruta. El Java 8 de Oracle sigue en el PATH para otros usos.
- Android SDK en `%LOCALAPPDATA%\Android\Sdk` (variable de usuario `ANDROID_HOME`): cmdline-tools 23.0, platform-tools 37.0.1 (adb), `platforms/android-36` y `build-tools/36.1.0`, con licencias aceptadas. `platform-tools` y `cmdline-tools\latest\bin` están en el PATH de usuario.
- `sdkmanager` está deprecado desde cmdline-tools 23: el reemplazo es `android sdk` (binario `android.exe` en la misma carpeta). Además lista e instala los paquetes con `/` (`platforms/android-36`) en lugar de `;`.
- Las terminales abiertas antes de la instalación (incluido VS Code) no ven las variables nuevas hasta reiniciarlas; mientras tanto Claude usa rutas absolutas.
- Las terminales abiertas antes de la instalación tampoco ven `adb` en el PATH; Claude lo llama por su ruta completa.

## Celular de pruebas (leído con adb el 2026-10-27)

- Samsung Galaxy S23 Ultra (SM-S918B), Android 16 (API 36), One UI 8.5, Snapdragon 8 Gen 2 (SM8550).
- El "Bloqueador automático" de Samsung impide la depuración USB e inalámbrica: debe estar desactivado mientras se desarrolla.
- Sensores físicos: acelerómetro y giroscopio LSM6DSO (STMicro), magnetómetro AK09918 (AKM), barómetro LPS22HH (STMicro).
- Sensores fusionados (QTI): Rotation Vector, Game Rotation Vector, gravity, linear acceleration; además detector y contador de pasos (Samsung) y versiones sin calibrar de acelerómetro, giroscopio y magnetómetro.
- GNSS: Qualcomm GNSS Gen9, doble frecuencia L1 + L5 (usa L5 en el fix). Capacidades: MEASUREMENTS (mediciones crudas), SATELLITE_PVT, MEASUREMENT_CORRECTIONS (LOS_SATS, EXCESS_PATH_LENGTH, REFLECTING_PLANE), ACCUMULATED_DELTA_RANGE (estado desconocido).
- ARCore (Google Play Services for AR) instalado, versión 1.57.
- Conexión adb: vinculado por depuración inalámbrica (`adb-R5CX60MG4SM`). PC y celular están en la misma red (DESIGNIO2, `192.168.0.x`), así que `adb` descubre el celular por mDNS y se conecta solo (serial `adb-R5CX60MG4SM-YcULU6._adb-tls-connect._tcp`), sin dictar puertos.
- Si el celular queda en otra subred, mDNS deja de funcionar: hay que dictar el puerto de conexión o usar `tools/connect-phone.ps1`.
- Si queda una sesión vieja `offline` que provoca "more than one device", se limpia con `adb kill-server` seguido de `adb start-server`.

## Estado

### Hecho

- 2026-10-27: creación de `CLAUDE.md`, `.gitignore` y `docs/flujo-de-trabajo.md`; repositorio conectado a GitHub.
- 2026-10-27: entorno de compilación listo: JDK 17, Android SDK (API 36) y adb, sin Android Studio.
- 2026-10-27: depuración USB e inalámbrica activadas en el S23 Ultra (Bloqueador automático desactivado).
- 2026-10-27: S23 Ultra vinculado y conectado por depuración inalámbrica; perfil de hardware leído con adb.

### En curso

- Siguiente paso: discusión de la idea (dificultades, alcance, interfaz, stack, optimización).

### Por implementar (borrador, se ajusta tras la discusión)

1. Esqueleto del proyecto (Kotlin, Gradle, script de despliegue al celular).
2. Pantalla de diagnóstico y grabación de sesiones: precisión del GPS, brújula frente a dirección de avance, deriva, barómetro. Decide si hace falta ARCore.
3. Cámara en vivo con un marcador fijo hacia un punto conocido (validar que orientación y proyección cuadran).
4. Grabación de rutas en segundo plano e importación de GPX/KML/KMZ.
5. Paquete offline de zona: senderos de OpenStreetMap y modelo de elevación.
6. Vista AR: ruta por delante y recorrido hecho, fijos en el mundo, con ajuste a la ruta y aviso en bifurcaciones.
7. Plan B: ARCore, solo si el diagnóstico lo justifica.
8. Optimización de energía, memoria y rendimiento.

### Ideas

- Modo grabación y reproducción de sensores: grabar una caminata (GPS + IMU) y reproducirla en el PC para ajustar los algoritmos sin salir a caminar cada vez.
- Capa de depuración en pantalla (precisión, rumbo, FPS, consumo) activable en builds de desarrollo.
- Compilar el APK en GitHub Actions para instalarlo desde el celular sin estar en el PC.
- "Volver por donde vine": guiar de regreso por el recorrido grabado, útil sin cobertura.

### Temas para la discusión (en este orden)

1. Anclaje del camino al mundo real: GPS, sensores y ARCore. Cerrado, ver "Discusión de la idea".
2. Mapas, rutas y datos offline: fuentes, importación, grabación, elevación, paquete de zona. En curso.
3. Interfaz y experiencia de uso.
4. Stack técnico (incluye el motor de render: OpenGL ES, Vulkan, Filament/SceneView o Canvas 2D).
5. Optimización de CPU, memoria y batería (cámara + GPS + render).

## Discusión de la idea

### 1. Anclaje del camino al mundo real (cerrado el 2026-10-27)

Respuestas del usuario:

- Escenario: senderos de los alrededores de Manizales, sin cobertura celular pero a campo abierto. La mayoría de senderos está en los mapas, aunque algunos con poca precisión, sobre todo en altura.
- ARCore: solo como segunda opción si la base con GPS y sensores resulta muy imprecisa.
- "Radiografía": el camino queda fijo en el mundo y la pantalla lo revela al moverla (estilo Pokémon GO), visible a través de obstáculos, sin calcular oclusión.
- Precisión esperada: guía de dirección y giros (qué sendero tomar en cada bifurcación), con la proyección del recorrido hecho y del que falta.
- Rutas: importar las de otras apps (Relive, Komoot, Wikiloc) y grabar y guardar rutas propias.

Conclusiones:

- El error de posición del GPS domina cerca y el de rumbo domina lejos: 3 m de error lateral desvían unos 31° un punto a 5 m; 10° de error de rumbo corren unos 17 m un punto a 100 m.
- Capa 1, base siempre activa: fusión propia GPS + sensores + ajuste a la ruta (map matching) para que el camino salga de los pies del usuario; Game Rotation Vector para la orientación inmediata; brújula corregida con la dirección de avance y la del sendero, descartando lecturas anómalas con `GeomagneticField` (modelo WMM, offline); orientación sincronizada con cada cuadro de la cámara; estabilización de video desactivada; gesto de ajuste manual del rumbo.
- Capa 2, plan B: ARCore (offline), alineado con el GPS ajustando trayectorias mientras se camina. Solo si el diagnóstico muestra que la capa 1 no alcanza.
- Descartado: Geospatial API (requiere internet, fuera del objetivo), seguimiento visual propio (VIO), oclusión y profundidad.
- El escenario favorece la capa 1: a cielo abierto el GPS de doble frecuencia suele dar 2 a 5 m, y sin carros ni estructuras metálicas la brújula es más confiable. Riesgos: bosque cerrado (el GPS empeora), subidas lentas (la dirección de avance del GPS es menos fiable), zigzags cerrados (el ajuste a la ruta puede confundir tramos).
- Bifurcaciones: se resuelven bien si las ramas se separan más de unos 20 a 30°. Para ramas casi paralelas: distancia a la bifurcación, rama incorrecta atenuada y detección rápida de salida de ruta.
- Representación: cinta ancha y semitransparente, marcadores altos en giros y bifurcaciones, ruta por delante y recorrido hecho con colores distintos, más difusa cuando hay más incertidumbre.
- No viable: precisión de centímetros sin conexión, interiores, rumbo exacto desde el primer segundo estando quieto.

### 2. Mapas, rutas y datos offline (en curso desde el 2026-10-27)

Propuesta inicial:

- Datos que deben estar en el celular antes de salir: la ruta a seguir, la red de senderos de la zona (OpenStreetMap) y un modelo de elevación (OSM no guarda alturas).
- Importación por archivo GPX (y KML/KMZ) mediante "Compartir" o "Abrir con" y un selector de archivos. Relive, Komoot y Wikiloc no tienen API pública abierta para sincronizar.
- Al importar, ajustar la ruta a los senderos de OSM donde coinciden y conservar la línea grabada donde OSM no tiene el sendero.
- Grabación con servicio en primer plano, guardando puntos solo al moverse unos metros o cambiar de dirección, con altura del barómetro; exportación a GPX.
- Elevación: Terrain Tiles de AWS (formato Terrarium, PNG que Android decodifica de forma nativa) o Copernicus DEM (GeoTIFF, mejor calidad, más complejo). Usar altura relativa al usuario, leída del mismo modelo, más el barómetro para seguir los cambios reales de altura.
- Paquete de zona: con internet, el usuario elige la zona y la app descarga los senderos (Overpass API) y las teselas de elevación, los procesa una vez y los guarda en formato compacto. En el sendero solo se leen.
- Rutas nuevas A→B: algoritmo propio (A* con penalización por pendiente) sobre el grafo de senderos de la zona, como fase posterior.

Preguntas abiertas:

- ¿Hace falta calcular rutas nuevas sobre los senderos o basta con seguir rutas importadas o grabadas?
- ¿"Relieve" es Relive? ¿Cuál de las tres apps se usa más?
- Conseguir un GPX real de un sendero conocido cerca de Manizales para pruebas.
- ¿Zona fija (alrededores de Manizales) o descarga de cualquier zona elegida?

## Decisiones tomadas

- 2026-10-27: entorno sin Android Studio (opción B). Claude instala Temurin JDK 17 con `winget` y el Android SDK por línea de comandos; Android Studio se puede agregar después para perfilar.
- 2026-10-27: dispositivo de pruebas único: Samsung Galaxy S23 Ultra (Android 16). Sin emulador.
- 2026-10-27: escenario objetivo: senderismo alrededor de Manizales, sin cobertura y a campo abierto.
- 2026-10-27: anclaje en dos capas: base propia con GPS y sensores, y ARCore solo como plan B si la base resulta imprecisa. Geospatial API fuera del objetivo.
- 2026-10-27: el camino queda fijo en el mundo y se revela al mover el celular, visible a través de obstáculos, sin oclusión.
- 2026-10-27: precisión objetivo: guía de dirección, giros y bifurcaciones, mostrando el recorrido hecho y el que falta.
- 2026-10-27: rutas importadas de otras apps (Relive, Komoot, Wikiloc) y grabadas con la app.
