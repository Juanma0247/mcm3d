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
| `tools/gpx_check.py` | Analiza un GPX: cobertura de OpenStreetMap a lo largo de la ruta (Overpass API) y alturas frente a Terrain Tiles (Terrarium). Uso: `python -I tools/gpx_check.py <ruta.gpx> cache/<nombre>`. Las descargas quedan en `cache/` (ignorada por git, igual que los `.gpx`, `.kml` y `.kmz`). |
| `app/` | Módulo Android (`cc.juanma.mcm3d`): `sensing/` (monitores de GNSS, orientación, ambiente y energía en un hilo propio), `diagnostics/` (ViewModel y pantalla de diagnóstico), `ui/` (tema y componentes). Código Kotlin en `src/main/java`. |
| `core/` | Módulo Kotlin puro, sin Android: ángulos, orientación de la cámara desde la matriz de rotación, altitud barométrica, bandas GNSS, medidores de frecuencia y promedios, cálculo de consumo. Pruebas JUnit en `src/test`. |
| `gradle/`, `gradlew`, `gradlew.bat` | Wrapper de Gradle (checksum fijado) y catálogo de versiones `gradle/libs.versions.toml`. |
| `tools/deploy.ps1` | Compila, instala en el celular y abre la app (`-Logcat` para ver logs, `-NoLaunch` para solo instalar). |

## Proyecto Android

- applicationId y namespace: `cc.juanma.mcm3d`. Versión actual: 0.1.0 (versionCode 1).
- Gradle 9.8.2 (wrapper), AGP 9.4.1 con Kotlin integrado (sin plugin `kotlin-android`), Kotlin 2.4.21, Compose BOM 2026.09.00, Material 3. `:core` usa el plugin `kotlin-jvm`.
- minSdk 26, compileSdk 37 (las librerías AndroidX actuales lo exigen; AGP descargó la plataforma 37), targetSdk 36 (Android 16, el del S23 Ultra), Java/JVM 17. Release con R8 y reducción de recursos.
- Compilar y probar sin celular: `.\gradlew.bat :core:test :app:assembleDebug` (requiere `JAVA_HOME` al JDK 17; en terminales viejas, tomarlo de la variable de sistema).
- Textos de la interfaz en español en `res/values/strings.xml` (idioma por defecto); el inglés irá en `values-en` más adelante.
- Tema: solo oscuro por ahora, paleta en `ui/theme/Color.kt` (acento ámbar `#FF9A2E`, rojo solo para alertas).
- Ícono adaptativo vectorial con capa monocromática (`res/drawable/ic_launcher_*.xml`).
- Respaldo: `allowBackup` y `fullBackupContent` desactivados, y `res/xml/data_extraction_rules.xml` excluye todo de la nube y de la transferencia entre dispositivos (los datos tendrán ubicaciones reales). Revisar cuando existan rutas guardadas que el usuario quiera migrar.
- Antes de publicar: subir targetSdk a 37 (lint `OldTargetApi`, única advertencia actual) y probar en Android 17.
- Al actualizar versiones, consultar los metadatos de Maven (Google y Central) y la tabla de compatibilidad de AGP.

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

## Flujo de desarrollo

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
- La depuración inalámbrica se apaga sola después de un rato (visto el 2026-10-27); al reactivarla en el celular, `adb` se reconecta solo por mDNS.

## Estado

### Hecho

- 2026-10-27: creación de `CLAUDE.md`, `.gitignore` y `docs/flujo-de-trabajo.md`; repositorio conectado a GitHub.
- 2026-10-27: entorno de compilación listo: JDK 17, Android SDK (API 36) y adb, sin Android Studio.
- 2026-10-27: depuración USB e inalámbrica activadas en el S23 Ultra (Bloqueador automático desactivado).
- 2026-10-27: S23 Ultra vinculado y conectado por depuración inalámbrica; perfil de hardware leído con adb.
- 2026-10-27: discusión de la idea completa (5 temas cerrados, ver "Discusión de la idea"). Ruta de prueba validada contra OSM y elevación.
- 2026-10-27: v0.1.0 (hito 1): proyecto Android con `:app` y `:core`, pantalla de diagnóstico en vivo (GNSS con altitud sobre el nivel del mar, satélites, segunda frecuencia y constelaciones; rumbo verdadero de la cámara con declinación, precisión, brújula frente a giroscopio y frente a rumbo de avance, anomalía magnética; barómetro con altura relativa; luz; energía con corriente, %/h, temperatura y estado térmico; inventario y frecuencia de sensores) y `tools/deploy.ps1`. Compila en debug y release, 22 pruebas de `:core` pasan, lint sin errores. APK release: 0,99 MB. No se probó en el celular por decisión del usuario.

### En curso

- Probar la v0.1.0 en el S23 Ultra con `.\tools\deploy.ps1` cuando el usuario lo indique, y anotar los errores reales medidos (GPS, brújula, barómetro, consumo).
- Siguiente hito: grabación de sesiones (GNSS + sensores) a archivo para reproducirlas en el PC y ajustar los algoritmos.
- Pendiente: leer las capacidades de vibración del S23 Ultra (`adb shell dumpsys vibrator_manager`: control de amplitud y primitivas hápticas). No se pudo porque la depuración inalámbrica se había apagado.

### Por implementar (borrador, se ajusta tras la discusión)

1. Esqueleto del proyecto (Kotlin, Gradle, script de despliegue al celular). Hecho en v0.1.0.
2. Pantalla de diagnóstico y grabación de sesiones: precisión del GPS, brújula frente a dirección de avance, deriva, barómetro y consumo de corriente. Decide si hace falta ARCore. Diagnóstico en vivo hecho en v0.1.0; falta la grabación de sesiones.
3. Cámara en vivo con un marcador fijo hacia un punto conocido (validar que orientación y proyección cuadran).
4. Grabación de rutas en segundo plano e importación de GPX/KML/KMZ.
5. Paquete offline de zona: senderos de OpenStreetMap y modelo de elevación (elegir entre Terrarium y Copernicus comparándolos en la ruta de prueba).
6. Vista AR: ruta por delante y recorrido hecho, fijos en el mundo, con ajuste a la ruta y aviso en bifurcaciones.
7. Plan B: ARCore, solo si el diagnóstico lo justifica.
8. Optimización de energía, memoria y rendimiento.

### Ideas

- Modo grabación y reproducción de sensores: grabar una caminata (GPS + IMU) y reproducirla en el PC para ajustar los algoritmos sin salir a caminar cada vez.
- Capa de depuración en pantalla (precisión, rumbo, FPS, consumo) activable en builds de desarrollo.
- Compilar el APK en GitHub Actions para instalarlo desde el celular sin estar en el PC.
- "Volver por donde vine": guiar de regreso por el recorrido grabado, útil sin cobertura.
- Avisos hápticos en un reloj inteligente (Wear OS), más fáciles de notar que en el bolsillo al caminar.

### Temas para la discusión (en este orden)

1. Anclaje del camino al mundo real: GPS, sensores y ARCore. Cerrado, ver "Discusión de la idea".
2. Mapas, rutas y datos offline: fuentes, importación, grabación, elevación, paquete de zona. Cerrado.
3. Interfaz y experiencia de uso. Cerrado.
4. Stack técnico. Cerrado.
5. Optimización de CPU, memoria y batería (cámara + GPS + render). Cerrado.

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

### 2. Mapas, rutas y datos offline (cerrado el 2026-10-27)

Respuestas del usuario:

- No hace falta calcular rutas nuevas: basta con seguir rutas importadas o grabadas. El cálculo de rutas queda como mejora futura, cuando haya datos y experiencia en ciertas zonas.
- Las rutas las publican y usan los caminantes de Manizales (Relive, Komoot, Wikiloc); el usuario no usa esas apps, se guía por quienes conocen las rutas.
- Zona: cualquiera; la principal es Manizales.
- Ruta de prueba: `Downloads\Puente_Gallinazo_No__3___Recorrido_circular_desde_Aeropuerto_La_Nubia.gpx` (Komoot). No se sube al repositorio.

Validación con la ruta de prueba (`tools/gpx_check.py`, 2026-10-27):

- El GPX de Komoot es una ruta planificada: 1 track, 716 puntos, 2 waypoints (parqueadero y puente), 21,3 km, circular. Separación entre puntos: mediana 21 m, máximo 265 m.
- Las horas son sintéticas (hora de descarga más la duración estimada de 6 h 28 min): no son una grabación real.
- Las alturas de Komoot son inconsistentes: inicio y fin son el mismo punto, pero difieren 20 m (2033,6 y 2013,8 m).
- OpenStreetMap cubre el 100 % de la ruta: distancia mediana 0,1 m, máxima 1,5 m. Komoot traza sobre OSM, así que sus rutas planificadas coinciden con OSM; las grabadas (Wikiloc, Relive) traerán el error del GPS de quien las grabó.
- Tipo de vía: 15,5 km `unclassified` (vías rurales menores), 1,4 km `footway`, 1,2 km `cycleway`, 1,1 km `trunk`, 0,6 km `track`, 0,35 km `path` y tramos menores.
- Elevación Terrarium z13 (píxel de 19 m a esta latitud): consistente consigo misma (inicio y fin 2031,7 m). Diferencia con Komoot: media −3 m, desviación 8 m, máximo 29 m. Desnivel: Komoot +679/−699 m; DEM +1123/−1124 m (el ruido del DEM infla el desnivel y Komoot suaviza mucho; el real debe estar entre ambos).
- Pendientes en ventanas de 20 m: mediana 10 %, p90 24 %. Dibujar el camino plano no sirve: con 24 % de pendiente, 50 m adelante el camino está 12 m más arriba.
- Tamaños para esta zona (unos 6 × 5 km): vías de OSM en JSON 836 KB (en binario compacto, un orden de magnitud menos); 4 teselas de elevación, 168 KB.

Conclusiones:

- Importación por archivo GPX (y KML/KMZ) con "Compartir", "Abrir con" y selector de archivos; Relive, Komoot y Wikiloc no tienen API pública abierta.
- Al importar: ignorar las horas de rutas planificadas, recalcular todas las alturas con el modelo de elevación propio, densificar la ruta (un punto cada pocos metros) y suavizar el perfil de altura para evitar saltos y pendientes falsas.
- Rutas grabadas por otros: ajustarlas a OSM donde coinciden y conservar la línea grabada donde OSM no tiene el sendero.
- Los waypoints del GPX se muestran como marcadores AR (por ejemplo, el puente, visible desde lejos).
- Grabación propia con servicio en primer plano, puntos solo al moverse unos metros o cambiar de dirección, altura del barómetro y exportación a GPX.
- Elevación: Terrain Tiles de AWS (Terrarium), validadas con la ruta de prueba; Copernicus DEM como alternativa si hace falta más calidad. En la vista AR se usa la altura relativa al usuario (mismo modelo) y el barómetro para los cambios reales.
- Paquete de zona: descarga automática de la zona alrededor de cada ruta importada (con margen) o alrededor de la ubicación actual antes de salir a grabar. Se procesa una vez y se guarda compacto; en el sendero solo se lee.
- Cálculo de rutas A→B (A* con penalización por pendiente sobre el grafo de senderos): mejora futura.
- Mejora futura: aprender la altura real de una ruta con el barómetro al recorrerla y guardarla para la siguiente vez.

Aprobado por el usuario: descarga automática de zona alrededor de cada ruta y estrategia de alturas. Principio: los datos deben ser lo más precisos posible en las tres dimensiones. Implicaciones:

- Comparar Terrarium contra Copernicus DEM en la ruta de prueba y quedarse con el más preciso.
- Grabar la altura del barómetro desde la primera versión de la grabación, y preferir la altura grabada sobre la del modelo cuando exista.
- Ajustar a OSM las rutas grabadas por otros para corregir su error horizontal.

### 3. Interfaz y experiencia de uso (cerrado el 2026-10-27)

Propuesta inicial:

- Condicionantes: sol directo (alto contraste, textos grandes, colores que resalten sobre vegetación, tierra y cielo: magenta, naranja o cian, nunca verde), una mano y a veces pantalla mojada (botones grandes, pocos toques), batería para 4 a 8 h (AR a ratos, el resto en el bolsillo), sin cobertura.
- Concepto de "salida": siempre graba el recorrido; puede seguir una ruta o no seguir ninguna (grabar una nueva). Grabar y navegar son lo mismo.
- Pantallas: Mis rutas (lista, importar, estado del paquete offline), Detalle de ruta (perfil de altura, vista 2D, puntos de referencia, iniciar), Navegación, Resumen al terminar (guardar, exportar GPX), Ajustes y diagnóstico.
- Navegación: celular levantado = vista AR; celular plano = mapa 2D desde arriba. Cambio automático por la orientación; la cámara solo se enciende en AR.
- Vista AR: cinta de la ruta por delante (color fuerte, flechas de sentido), cinta del recorrido hecho (tenue), bifurcaciones con marcador alto y ramas incorrectas grises y tachadas, puntos de referencia como pines con nombre y distancia, franja de rumbo arriba, flecha en el borde cuando la ruta queda fuera de cuadro, indicador de precisión (la cinta se ensancha si hay duda), resumen de lo que falta abajo.
- Mapa 2D sin mapa base de calles: ruta, senderos de la zona y relieve calculado del modelo de elevación ya descargado.
- Modo bolsillo: pantalla apagada grabando; avisos por vibración y sonido opcional al acercarse a una bifurcación, al salirse de la ruta y al llegar a un punto de referencia; notificación con la siguiente indicación; estimación de si alcanza la batería.
- Correcciones: arrastre horizontal en AR para girar el camino, arrastre vertical para subirlo o bajarlo, doble toque para restablecer; aviso de calibración de brújula (movimiento en 8).
- Seguridad: "volver por donde vine" y coordenadas y altura visibles para emergencias.

Respuestas del usuario:

- Cambio automático AR / mapa 2D según la inclinación: sí.
- Nombres de lugares, veredas y puntos de referencia de la zona: sí. Ríos: no hacen falta.
- La app se publicará en Play Store: alto nivel de acabado, diseño, usabilidad, adaptabilidad y eficiencia en otros dispositivos. Se parte del S23 Ultra y luego se adapta a gamas más bajas; el diseño UX/UI se pule desde ahora.
- Estilo sobrio, técnico y profesional; tema oscuro por defecto para ahorrar batería, con alto contraste para pleno sol y sin confusiones.
- Avisos por vibración: sí, con patrones según el tipo de aviso (una desviación se siente como alerta; una bifurcación a 50 m apenas se nota).

Nombres de lugares en OpenStreetMap (zona 4.95..5.08 N, 75.55..75.38 O, consulta del 2026-10-27): 334 elementos con nombre. Veredas como `place=village/hamlet/locality` (Gallinazo, Alto Tablazo, Bajo Tablazo, La Aurora, El Arenillo...), 16 cerros (`natural=peak`: Alto Bonito, Cerro Sancancio, Cerro Tesorito...), miradores, atractivos, una cascada, un camping y una cueva. También barrios y comunas urbanas (se filtran fuera de la ciudad).

Ajustes tras las respuestas:

- Nombres: se descargan con el paquete de zona; en el mapa 2D como etiquetas y en AR como etiquetas discretas de cerros y veredas, pocas y priorizadas para no saturar.
- Estilo: un solo color de acento fuerte para la ruta; rojo reservado para alertas; borde oscuro (halo) alrededor de todo trazo y texto claro en AR para que se lea sobre cielo brillante y sobre bosque oscuro; forma más color (sólido o punteado, iconos) para no depender solo del color (daltonismo); cifras con números tabulares; "modo sol" automático con el sensor de luz (trazos más gruesos, textos más grandes, contraste máximo).
- Vibración: el ritmo indica el tipo de aviso y la intensidad indica la urgencia (en el bolsillo se distingue mejor el ritmo que la intensidad). Propuesta: bifurcación a 50 m, un toque muy suave; bifurcación inmediata, dos toques suaves; punto de referencia, dos toques medios espaciados; regreso a la ruta, un toque medio; desviación, tres pulsos fuertes repetidos cada ~10 s hasta volver o descartar; GPS perdido o batería baja, pulso largo más uno corto. Intensidad ajustable. Pendiente: leer en el S23 Ultra si soporta control de amplitud y primitivas hápticas; en celulares sin control de amplitud se usa solo el ritmo.
- Play Store: detectar los sensores disponibles y degradar con elegancia (muchos gama media y baja no tienen giroscopio ni barómetro); grabar con servicio en primer plano iniciado por el usuario para no pedir permiso de ubicación en segundo plano; ARCore marcado como opcional; atribuciones obligatorias (© OpenStreetMap contributors, fuente de elevación); textos en recursos desde el inicio (español primero, inglés después); accesibilidad (escala de fuente, contraste, TalkBack fuera de la vista AR); la instancia pública de Overpass tiene límites de uso justo, así que a escala habrá que evaluar paquetes de zona preprocesados propios.

### 4. Stack técnico (cerrado el 2026-10-27)

Propuesta inicial:

- Kotlin, sin NDK al inicio. Módulos: `:core` (Kotlin puro: geometría, fusión de sensores, ajuste a la ruta, formatos) probado en el PC con sesiones grabadas, y `:app` (Android).
- UI: Jetpack Compose + Material 3 con tema propio, Navigation Compose, ViewModel + StateFlow, corrutinas. Inyección de dependencias manual al inicio (compila más rápido que Hilt).
- Render: un solo renderizador OpenGL ES 3.0 propio para AR y mapa 2D (misma escena, dos cámaras virtuales, transición animada al inclinar). La cámara entra como textura externa, sincronizada por marca de tiempo con la orientación. Descartados: Filament/SceneView (pesados, PBR innecesario), Vulkan (complejidad sin beneficio para esta escena), Canvas 2D (no sincroniza con los cuadros de la cámara), SDK de mapas como MapLibre o Google Maps (peso, dependencia, segundo renderizador).
- Cámara: CameraX con Surface propia hacia OpenGL, estabilización desactivada, lente principal, 720p o 1080p a 30 fps.
- Sensores: `SensorManager` directo (Game Rotation Vector, magnetómetro, barómetro, pasos) con batching por hardware en modo bolsillo.
- Ubicación: `LocationManager` (GPS_PROVIDER) + `GnssStatus`: control total y sin depender de Google Play Services.
- Datos: Room para rutas, salidas y metadatos; geometrías y paquetes de zona en formato binario propio con memoria mapeada (carga instantánea, poca RAM); DataStore para ajustes.
- Descarga y procesamiento de zonas: OkHttp + WorkManager; lectura en streaming con `JsonReader` y `XmlPullParser` del sistema; teselas de elevación con `BitmapFactory`.
- Build: Gradle Kotlin DSL + catálogo de versiones; minSdk 26, targetSdk 36; R8 y reducción de recursos; Baseline Profiles; publicación como AAB.
- Plan B: ARCore usa el mismo renderizador OpenGL; opcional en el manifiesto.
- Pruebas: JUnit en `:core` con sesiones grabadas (fuera del repo) y datos sintéticos (en el repo).

Respuestas del usuario:

- Mapa 2D propio en OpenGL: sí. La propuesta de stack queda aprobada.
- Dominio del usuario: `juanma.cc`; nombre de la app: `mcm3d`. applicationId confirmado: `cc.juanma.mcm3d` (dominio invertido, convención de Android; no se puede cambiar después de publicar).
- Reporte de fallos: Android vitals de Play Console más registro local de fallos en el celular; Crashlytics se evalúa más adelante.

### 5. Optimización de CPU, memoria y batería (cerrado el 2026-10-27)

Propuesta inicial:

- Presupuesto: la pantalla (sobre todo con brillo alto al sol) y la cámara son lo que más gasta; luego GPU, GNSS y CPU. Los sensores corren en el procesador de sensores de bajo consumo.
- Objetivos iniciales a medir en el S23 Ultra: modo bolsillo ≤ 4 %/h; mapa 2D en pantalla ≤ 10 %/h; vista AR ≤ 20 %/h; una salida típica termina con ≥ 30 % de batería (ver presupuesto abajo). Render ≤ 10 ms por cuadro, memoria < 150 MB en AR, descarga < 15 MB, arranque en frío < 1 s.
- GNSS: 1 Hz cerca de bifurcaciones o fuera de ruta; en tramos largos sin decisiones, intervalo mayor con el modo de bajo consumo del chip (capacidad LOW_POWER_MODE). Mediciones crudas solo en diagnóstico.
- Sensores: en bolsillo, pasos y barómetro con batching por hardware (el procesador principal duerme); en AR, Game Rotation Vector a ~100 Hz y magnetómetro a 10 a 20 Hz.
- CPU: todo por eventos, sin bucles de sondeo ni temporizadores; sin crear objetos en los caminos calientes (sensores, render) para no activar el recolector de basura; índice espacial en el paquete de zona para el ajuste a la ruta; todo lo costoso se precalcula al importar o descargar (ruta densificada, alturas, distancias acumuladas, bifurcaciones y ángulos, prioridades de etiquetas, textura de relieve).
- Render: 30 fps sincronizado con la cámara y `Surface.setFrameRate(30)` para que la pantalla baje su frecuencia; en el mapa 2D sin movimiento, render solo cuando algo cambia; mallas de cinta regeneradas solo cada pocos metros de avance y solo para el tramo visible; shaders simples, sin postprocesado; etiquetas en un atlas de texturas generado una vez.
- Cámara: solo en AR, con histéresis al inclinar para no encender y apagar; se mantiene abierta unos segundos al bajar el celular para que volver a AR sea instantáneo; vista previa a 720p; imagen de cámara ligeramente atenuada (en AMOLED ahorra energía y mejora el contraste de la cinta).
- Pantalla: tema oscuro en mapa y listas; pantalla encendida forzada solo mientras se mira la navegación; el modo sol sube el contraste, no el brillo.
- Escritura: puntos de la salida guardados por lotes (cada ~30 s), no en cada lectura.
- Temperatura: vigilar el estado térmico del sistema y degradar (fps, resolución de cámara) antes de que el celular se caliente al sol.
- Medición: corriente instantánea de la batería (`BATTERY_PROPERTY_CURRENT_NOW`) en la pantalla de diagnóstico, `dumpsys batterystats` y trazas de Perfetto por adb.
- Gamas bajas (después): menos fps y resolución, menos etiquetas, cinta más simple, según memoria y clase de rendimiento del dispositivo.

Respuestas del usuario:

- Los objetivos sirven para empezar; se optimiza sobre la marcha del desarrollo.
- Salida típica: 8 h, con hasta 20 % del tiempo en la vista AR y otro 20 % en las demás funciones en pantalla (mapa 2D y resto).

Presupuesto de batería de una salida típica (peor caso, con los objetivos por modo):

| Modo | Tiempo | Consumo |
|---|---|---|
| Vista AR (20 %) | 1,6 h × 20 %/h | 32 % |
| Mapa 2D y resto en pantalla (20 %) | 1,6 h × 10 %/h | 16 % |
| Bolsillo (60 %) | 4,8 h × 4 %/h | 19 % |
| Total | 8 h | 67 % (queda ~33 %) |

Para terminar con ≥ 40 % habría que bajar la vista AR a ~15 %/h (total ~59 %). La vista AR es el modo a optimizar primero.

## Decisiones tomadas

- 2026-10-27: entorno sin Android Studio (opción B). Claude instala Temurin JDK 17 con `winget` y el Android SDK por línea de comandos; Android Studio se puede agregar después para perfilar.
- 2026-10-27: dispositivo de pruebas único: Samsung Galaxy S23 Ultra (Android 16). Sin emulador.
- 2026-10-27: escenario objetivo: senderismo alrededor de Manizales, sin cobertura y a campo abierto.
- 2026-10-27: anclaje en dos capas: base propia con GPS y sensores, y ARCore solo como plan B si la base resulta imprecisa. Geospatial API fuera del objetivo.
- 2026-10-27: el camino queda fijo en el mundo y se revela al mover el celular, visible a través de obstáculos, sin oclusión.
- 2026-10-27: precisión objetivo: guía de dirección, giros y bifurcaciones, mostrando el recorrido hecho y el que falta.
- 2026-10-27: rutas importadas de otras apps (Relive, Komoot, Wikiloc) y grabadas con la app.
- 2026-10-27: la primera versión solo sigue rutas importadas o grabadas; el cálculo de rutas nuevas queda como mejora futura.
- 2026-10-27: la app funciona en cualquier zona; Manizales es la zona principal de pruebas.
- 2026-10-27: al importar una ruta se descarga automáticamente su zona (senderos y elevación); antes de grabar una ruta nueva, los alrededores de la ubicación actual.
- 2026-10-27: alturas recalculadas con modelo de elevación propio, suavizadas y, cuando existan, reemplazadas por las grabadas con barómetro. Principio: máxima precisión en las tres dimensiones.
- 2026-10-27: cambio automático entre vista AR y mapa 2D según la inclinación del celular.
- 2026-10-27: nombres de lugares, veredas y puntos de referencia de OSM en el mapa y en AR; sin ríos.
- 2026-10-27: objetivo de publicación en Play Store; UX/UI pulida desde ahora; adaptación a gamas bajas después de validar en el S23 Ultra.
- 2026-10-27: estilo sobrio, técnico y profesional; tema oscuro por defecto con alto contraste para pleno sol.
- 2026-10-27: avisos hápticos diferenciados por tipo (ritmo) y urgencia (intensidad).
- 2026-10-27: stack aprobado (ver "Discusión de la idea", punto 4), con mapa 2D propio en OpenGL y sin SDK de mapas.
- 2026-10-27: reporte de fallos con Android vitals y registro local; Crashlytics más adelante.
- 2026-10-27: applicationId `cc.juanma.mcm3d`.
- 2026-10-27: objetivos iniciales de consumo y rendimiento aprobados (punto 5); salida típica de 8 h con hasta 20 % en AR y 20 % en otras vistas en pantalla; se optimiza sobre la marcha.
