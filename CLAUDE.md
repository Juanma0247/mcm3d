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
2. Pantalla de diagnóstico: precisión del GPS, orientación, frecuencia de sensores, calibración de la brújula.
3. Cámara en vivo con un marcador fijo hacia un punto conocido (validar que orientación y proyección cuadran).
4. Obtención de rutas en línea y almacenamiento para uso sin conexión.
5. Dibujo del camino como línea 3D proyectada sobre la cámara.
6. Optimización de energía, memoria y rendimiento.

### Ideas

- Modo grabación y reproducción de sensores: grabar una caminata (GPS + IMU) y reproducirla en el PC para ajustar los algoritmos sin salir a caminar cada vez.
- Capa de depuración en pantalla (precisión, rumbo, FPS, consumo) activable en builds de desarrollo.
- Compilar el APK en GitHub Actions para instalarlo desde el celular sin estar en el PC.

### Temas para la discusión (en este orden)

1. Anclaje del camino al mundo real: GPS, sensores y ARCore. En curso, ver "Discusión de la idea".
2. Mapas y rutas offline: fuente de datos (OpenStreetMap), motor de rutas, formato de almacenamiento.
3. Interfaz y experiencia de uso.
4. Stack técnico (incluye el motor de render: OpenGL ES, Vulkan, Filament/SceneView o Canvas 2D).
5. Optimización de CPU, memoria y batería (cámara + GPS + render).

## Discusión de la idea

### 1. Anclaje del camino al mundo real (2026-10-27, en curso)

Conclusiones provisionales:

- El error de posición del GPS domina cerca (el tramo a los pies se ve corrido) y el error de rumbo domina lejos (todo el camino gira alrededor del usuario). Con 3 m de error lateral, un punto a 5 m se desvía unos 31°; con 10° de error de rumbo, un punto a 100 m queda corrido unos 17 m.
- Solo con GPS + sensores se logra una guía de dirección (rumbo con ±10° o más, varios metros de desfase), no un camino pintado en la acera. Mitigaciones: ajustar al usuario sobre la ruta (map matching) para que el camino siempre salga de sus pies; corregir el sesgo de la brújula con el rumbo de avance del GPS y la dirección de la calle; usar Game Rotation Vector para la orientación de corto plazo; descartar lecturas magnéticas anómalas comparándolas con `GeomagneticField` (modelo WMM, offline); sincronizar la orientación con el instante de cada cuadro de la cámara; gesto de ajuste manual del rumbo.
- ARCore (seguimiento visual-inercial) funciona sin internet, ya está instalado en el S23 Ultra, da una pose estable sincronizada con la cámara y detecta el suelo. No da posición ni rumbo absolutos: hay que alinearlo con el mundo ajustando su trayectoria a la del GPS mientras se camina, lo que estima el rumbo mucho mejor que la brújula.
- Geospatial API de ARCore (VPS con Street View) da posición y rumbo precisos al instante, pero requiere internet y clave de Google Cloud: solo como mejora opcional en línea.
- Construir un seguimiento visual propio (VIO) no vale la pena: es trabajo de investigación de años y ARCore lo resuelve.
- La representación visual debe tolerar el error: cinta ancha y semitransparente, marcadores altos en los giros, camino visible a través de edificios ("radiografía", sin necesidad de oclusión ni profundidad), más difuso cuando la incertidumbre es alta.
- No es viable: precisión de centímetros sin conexión, navegación en interiores sin infraestructura, rumbo exacto desde el primer segundo estando quieto, ni un camino exacto sobre la acera si el mapa solo tiene el eje de la vía.
- Arquitectura propuesta en capas: (1) fusión propia GPS + sensores + map matching, siempre activa; (2) ARCore cuando la cámara está abierta y el seguimiento es bueno; (3) Geospatial opcional con internet.
- Primer hito propuesto: pantalla de diagnóstico que mida los errores reales en el celular y grabe sesiones, para decidir con datos.

Preguntas abiertas:

- Escenario principal de uso: ciudad a pie, senderos y montaña, bicicleta o vehículo.
- ¿Se acepta depender de ARCore (Google Play Services for AR)?
- ¿"Radiografía" significa ver el camino a través de edificios y obstáculos?
- Precisión visual esperada: ¿camino pintado en la acera o guía de dirección y giros?
- ¿"Camino previamente trazado" es una ruta calculada de A a B, un recorrido grabado o dibujado por el usuario (GPX), o ambos?

## Decisiones tomadas

- 2026-10-27: entorno sin Android Studio (opción B). Claude instala Temurin JDK 17 con `winget` y el Android SDK por línea de comandos; Android Studio se puede agregar después para perfilar.
- 2026-10-27: dispositivo de pruebas único: Samsung Galaxy S23 Ultra (Android 16). Sin emulador.
