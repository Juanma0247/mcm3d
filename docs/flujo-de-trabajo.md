# Flujo de trabajo: desarrollar y probar MCM3D en el celular

## 1. Cómo se trabaja

1. Claude edita el código en este workspace.
2. Claude compila e instala la app en tu celular con un solo comando y la abre.
3. Tú pruebas moviendo el celular. Mientras tanto Claude puede leer los logs (`adb logcat`), tomar capturas de la pantalla del celular y descargar archivos de registro para analizarlos.
4. Cada cambio queda en git y se sube a GitHub.

### ¿Hace falta Android Studio?

No para el día a día. Un proyecto Android es código Kotlin más archivos Gradle, y se compila igual desde la terminal. Android Studio aporta:

- Debugger con breakpoints, Layout Inspector y previews de Compose.
- Profilers de CPU, memoria y energía (útiles en la fase de optimización).
- Live Edit / Apply Changes (recarga parcial sin reinstalar).

Para esta app, Live Edit aporta poco: casi todo lo importante (cámara, sensores, render) exige reiniciar la app de todas formas. Android Studio puede abrir este mismo proyecto cuando quieras, sin cambiar nada.

### ¿Por qué no un emulador?

El emulador no tiene cámara real ni GPS, giroscopio o magnetómetro reales (se simulan con controles manuales). Como la app depende por completo de eso, las pruebas reales se hacen en el celular físico.

### ¿Qué tan rápido es el ciclo?

- Primera compilación: varios minutos (descarga de Gradle y dependencias).
- Cambios pequeños después: compilación incremental e instalación, normalmente entre 10 y 40 segundos según el PC.
- No hay recarga instantánea tipo web: cada cambio es "compilar, instalar, abrir".

## 2. Preparar el PC

Se eligió la opción mínima, sin IDE, y quedó instalada el 2026-10-27:

- Temurin JDK 17 (con `winget`), con `JAVA_HOME` de sistema.
- Android SDK en `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME`): command-line tools, `platform-tools` (adb), plataforma de Android 16 (API 36) y build-tools.
- `adb` y `sdkmanager` en el PATH de usuario. Hay que reiniciar las terminales abiertas (o VS Code) para que los reconozcan.

La otra opción era instalar Android Studio, que trae su propio SDK, JDK y los profilers. Se puede agregar más adelante y usará este mismo SDK.

Opcional: [scrcpy](https://github.com/Genymobile/scrcpy) para ver y controlar la pantalla del celular desde el PC.

## 3. Preparar el celular (una sola vez)

1. Ajustes → Acerca del teléfono (en Samsung: Información de software) → tocar 7 veces "Número de compilación". Se activan las "Opciones para desarrolladores".
2. Opciones para desarrolladores → activar "Depuración USB".
3. En Android 11 o superior, activar también "Depuración inalámbrica".
4. Casos especiales:
   - Xiaomi, Redmi, POCO: activar "Instalar vía USB" y "Depuración USB (ajustes de seguridad)" (piden cuenta Xiaomi y SIM).
   - Samsung con One UI 6 o superior (incluye el S23 Ultra): si la depuración aparece como "bloqueada por Bloqueador automático", ir a Ajustes → Seguridad y privacidad → Bloqueador automático y desactivarlo (o buscar "Bloqueador automático" en el buscador de Ajustes). Debe quedar desactivado mientras se desarrolla; al reactivarlo se vuelve a bloquear la depuración.

## 4. Conectar por Wi-Fi (recomendado)

Requisitos: Android 11 o superior; PC y celular en la misma red Wi-Fi.

**Vincular (una sola vez por PC)**

1. En el celular: Opciones para desarrolladores → Depuración inalámbrica → "Vincular dispositivo con código de vinculación". Aparece una IP con puerto y un código de 6 dígitos.
2. En el PC:

   ```powershell
   adb pair 192.168.1.50:37123
   ```

   Usar la IP y el puerto de vinculación que muestra el celular, y escribir el código cuando lo pida. También se puede pasar el código directamente (`adb pair IP:puerto 123456`), así que basta con dictarle a Claude la IP, el puerto y el código sin cerrar el diálogo del celular.

**Conectar (cada vez que se reactive la depuración inalámbrica)**

1. En la pantalla principal de "Depuración inalámbrica" aparece "Dirección IP y puerto". Este puerto es distinto al de vinculación y cambia cada vez que se activa.
2. En el PC:

   ```powershell
   adb connect 192.168.1.50:41234
   adb devices
   ```

   `adb devices` debe listar el celular con estado `device`.

`adb mdns services` muestra los celulares visibles en la red, para no tener que leer el puerto en pantalla.

**Problemas comunes**

- Redes de universidad, de empresa o algunos routers aíslan los dispositivos entre sí y la vinculación falla. Solución: activar la "Zona con cobertura inalámbrica móvil" de Windows y conectar el celular a esa red, o usar USB.
- Al cambiar de red, la depuración inalámbrica se apaga: reactivarla y repetir `adb connect`.

## 5. Conectar por USB

1. Usar un cable de datos (no solo de carga).
2. Aceptar en el celular el diálogo "¿Permitir depuración USB?" y marcar "Permitir siempre desde esta computadora".
3. Ejecutar `adb devices`. Si el celular no aparece, puede faltar el driver USB del fabricante en Windows.

## 6. Ciclo de cada cambio

Claude ejecuta lo siguiente (se automatizará en un script `tools/deploy.ps1` cuando exista el proyecto):

```powershell
.\gradlew installDebug
adb shell am start -n <paquete>/.MainActivity
adb logcat
```

Captura de la pantalla del celular, que Claude puede ver directamente:

```powershell
adb shell screencap -p /sdcard/mcm3d_shot.png
adb pull /sdcard/mcm3d_shot.png
```

En PowerShell no se usa `adb exec-out screencap -p > archivo.png`, porque la redirección corrompe el archivo binario.

## 7. Pruebas en campo (lejos del PC)

Al salir a caminar el celular pierde la conexión con el PC. Para no perder información:

- La app guarda en el celular un registro de la sesión (logs y, si se activa, la grabación de sensores).
- Al volver, Claude lo descarga con `adb pull` y lo analiza.
- Las grabaciones contienen ubicaciones reales: se guardan en carpetas ignoradas por git y nunca se suben al repositorio, que es público.

## 8. Opcional: APK compilado en GitHub

Se puede configurar GitHub Actions para compilar el APK en cada push y descargarlo desde el celular. Sirve para instalar una versión nueva sin estar en el PC.
