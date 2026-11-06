# Prueba de campo del 2026-11-03 (v0.2.0)

Análisis de la sesión `session_20261103_085639.csv` (en `recordings/`, fuera del repositorio porque tiene ubicaciones reales). Aquí no se anotan coordenadas.

## Datos generales

- Duración: 8,0 h (08:56 a 16:56). Archivo de 1,25 MB.
- Filas: FIX 727, ORI 4181, SAT 14 686, BARO 362, PWR 150, MODE 48, MARK 1. Las filas SAT son el 73 % del archivo.
- Recorrido: 3,65 km a pie, 4758 pasos, velocidad mediana caminando 1,36 m/s. Solo 3 posiciones a más de 4 m/s: no hubo tramos en vehículo.
- La grabación se mantuvo en un radio de unos 490 m alrededor del inicio y terminó a 4 m del punto de partida.
- Tiempo en movimiento: 2,05 h; en pausa (modo quieto): 5,95 h (74 %).
- Celular en el bolsillo casi todo el tiempo: pantalla encendida en 19 de 150 muestras, luz mediana 0 lx (máximo 21 000 lx). Pose: 64 % vertical (bolsillo), 9 % plano.

## GNSS

| Medida | Caminando | Quieto |
|---|---|---|
| Precisión horizontal (mediana) | 3,8 m | 9,2 m |
| Precisión vertical (mediana) | 2,5 m | 2,9 m |
| Satélites usados (mediana) | 34 | 26 |
| Señales L5/E5a en uso (mediana) | 23 | 12 |
| C/N0 de los 4 mejores (mediana) | 39,5 dB-Hz | 34,4 dB-Hz |
| Precisión del rumbo de avance (mediana) | ±36,5° | n/a |

- Todas las posiciones: 74 % con precisión ≤ 5 m, 98 % ≤ 10 m, máximo 47 m; vertical p90 4,2 m.
- El chip reporta la precisión en escalones: 3,8 m en el 70 % de las posiciones y 9,9 m en el 20 %; la vertical casi siempre 2,5 m. **No sirve como medida fina de calidad**: la fusión necesita su propia estimación (dispersión frente a la trayectoria suavizada, satélites usados, C/N0).
- Satélites usados por época: BeiDou 18, GPS 12,5, Galileo 12,4, GLONASS 4,6. Un tercio de las señales usadas son L5/E5a.
- Rumbo de avance del GPS: ±36,5° caminando y ±19,5° entre 2,2 y 4 m/s. **A paso de caminata el rumbo instantáneo del GPS no sirve** para corregir la brújula: hay que calcular la dirección de avance con el desplazamiento acumulado (20 a 30 m).
- Tiempo hasta la primera posición al reanudar tras una pausa: 0 a 12 s, típico 1 a 3 s (arranque en caliente). Apagar el GNSS en las pausas cuesta poco al volver.

## Orientación

- El Rotation Vector de QTI reporta la precisión del rumbo siempre en −1 (desconocida) y el magnetómetro siempre en precisión 3 (alta): ninguno sirve como indicador de calidad del rumbo.
- Campo magnético: mediana 30,7 µT (esperado unos 30,2 µT); 65 % de las lecturas dentro de ±10 % y 85 % dentro de ±20 %. En zona urbana la interferencia es frecuente: un filtro por magnitud del campo descartaría cerca de un tercio de las lecturas.
- Rumbo de la cámara frente al rumbo de avance del GPS, celular en el bolsillo: el desfase medio se mantuvo entre 304° y 322° en los 8 tramos largos del día (misma posición en el bolsillo), con dispersión de 15 a 28° por tramo (balanceo de la pierna más ruido del rumbo GPS). La brújula no mostró cambios grandes de sesgo a lo largo del día.
- **Defecto de la grabación**: en las filas ORI el Game Rotation Vector no corresponde al mismo instante que el Rotation Vector (con lotes de 1 s puede venir de otro momento del lote). Caminando, ambos difieren en la dirección de la gravedad entre 5 y 34°, algo imposible si fueran del mismo instante; quietos coinciden (0,3°, deriva 0,2°/min). Por eso esta sesión no permite medir la deriva del Game Rotation Vector. Hay que guardar ambos del mismo instante.
- Sin datos en pose AR (celular levantado y apuntando): la comparación con un norte conocido y caminando con el celular en la mano sigue pendiente.

## Barómetro

- Inicio y fin en el mismo punto, pero la presión bajó de 798,42 a 794,93 hPa: 3,5 hPa, unos 37 m de altura falsa (a esta presión, unos 10,5 m por hPa). Es la marea atmosférica diaria del trópico (máximo a media mañana, mínimo a media tarde) sumada al clima.
- La diferencia entre la altitud GNSS y la barométrica pasó de 108 m a 66 m durante el día (5 a 7 m/h). Dentro de cada tramo de 5 a 13 min la dispersión fue de solo 2 a 4,5 m.
- En las pausas largas la presión cambió entre −0,04 y −0,67 hPa (hasta unos 7 m equivalentes en 88 min).
- **Conclusión**: el barómetro es muy bueno para cambios de altura de corto plazo, pero deriva varios metros por hora. La altura de la app debe fusionar barómetro (corto plazo) con la altura GNSS (largo plazo, constante de tiempo de varios minutos) y, en sendero, con el modelo de elevación.

## Pausas (modo quieto)

- 24 tramos en movimiento, de los cuales 15 fueron reanudaciones falsas: el sensor de movimiento significativo despertó la grabación sin pasos ni desplazamiento (se movió el celular sin caminar) y el GNSS quedó encendido 3 min cada vez. Total: 46 min de GNSS sin nada que grabar.
- No se perdió recorrido: el salto máximo entre la última posición antes de una pausa y la primera después fue de 24 m.
- El único punto marcado se registró durante una pausa, sin posición nueva.
- **Mejoras**: tras despertar por movimiento significativo, un periodo de prueba de unos 60 s; si no hay pasos ni desplazamiento, volver a la pausa (ahorra unos dos tercios de ese tiempo). Marcar un punto debe reanudar el GNSS para fijar su posición.

## Energía

- Batería: 71 % a 49 % en 8 h (2,8 %/h), incluido el uso normal del celular. Temperatura máxima 31,6 °C; estado térmico normal todo el día.
- Corriente con GNSS y sensores activos y pantalla apagada: mediana 120 mA (unos 2,4 %/h con la batería de 5000 mAh). Con la pantalla encendida: 380 a 470 mA.
- El objetivo del modo bolsillo (≤ 4 %/h) se cumple con margen, incluso si se camina todo el tiempo.

## Implicaciones para el diseño

1. Estimar la calidad de cada posición con datos propios, no con la precisión reportada por el chip.
2. Calcular la dirección de avance con el desplazamiento acumulado (≥ 20 a 30 m), no con el rumbo instantáneo del GPS.
3. Altura con filtro complementario: barómetro para el corto plazo, GNSS para el largo plazo y modelo de elevación en sendero.
4. Pausas: periodo de prueba al despertar; marcar un punto reanuda el GNSS.
5. Grabación de diagnóstico: Rotation Vector y Game Rotation Vector del mismo instante.
6. Pendiente: prueba en pose AR, a cielo abierto y en sendero.
