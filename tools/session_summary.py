"""Summarize an MCM3D session CSV: duration, distance, GNSS quality, still periods and battery."""
import math
import statistics
import sys
from collections import Counter


def pt(*args, **kwargs):
  print(*args, **kwargs)


def distance_m(lat1, lon1, lat2, lon2):
  r = 6371008.8
  d_lat = math.radians(lat2 - lat1)
  d_lon = math.radians(lon2 - lon1)
  a = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2) ** 2
  return 2 * r * math.asin(math.sqrt(min(1.0, a)))


def num(value):
  return float(value) if value not in ("", None) else None


def percentile(values, p):
  if not values:
    return None
  ordered = sorted(values)
  return ordered[min(len(ordered) - 1, int(round(p / 100 * (len(ordered) - 1))))]


def fmt(value, decimals=1, unit=""):
  return "n/d" if value is None else f"{value:.{decimals}f}{unit}"


def main(path):
  counts = Counter()
  fixes, power, modes, marks, baro = [], [], [], [], []
  header = []
  with open(path, encoding="utf-8") as source:
    for raw in source:
      raw = raw.rstrip("\n")
      if raw.startswith("#"):
        header.append(raw)
        continue
      fields = raw.split(",")
      kind = fields[0]
      counts[kind] += 1
      t = int(fields[1]) / 1000
      if kind == "FIX":
        fixes.append((t, float(fields[2]), float(fields[3]), num(fields[4]), num(fields[8]), int(fields[14]), num(fields[15])))
      elif kind == "PWR":
        power.append((t, num(fields[2]), num(fields[3]), num(fields[5])))
      elif kind == "MODE":
        modes.append((t, fields[2], fields[3]))
      elif kind == "MARK":
        marks.append(t)
      elif kind == "BARO":
        baro.append((t, float(fields[2])))

  pt("\n".join(line for line in header[:3]))
  last_t = max((t for t, *_ in fixes + power), default=0)
  pt(f"Duración: {last_t / 3600:.2f} h. Registros: {dict(counts)}")

  distance = 0.0
  for previous, current in zip(fixes, fixes[1:]):
    distance += distance_m(previous[1], previous[2], current[1], current[2])
  accuracies = [f[3] for f in fixes if f[3] is not None]
  pt(f"Posiciones: {len(fixes)}. Distancia: {distance / 1000:.2f} km")
  pt(f"Precisión horizontal: mediana {fmt(percentile(accuracies, 50))} m, p90 {fmt(percentile(accuracies, 90))} m, máx {fmt(max(accuracies, default=None))} m")
  second = [f[5] for f in fixes]
  pt(f"Señales L5/E5a en uso: media {fmt(statistics.mean(second) if second else None)}")

  still_s = 0.0
  for (t0, mode, _), nxt in zip(modes, modes[1:] + [(last_t, "", "")]):
    if mode == "still":
      still_s += nxt[0] - t0
  pt(f"Cambios de modo: {len(modes)}. Tiempo en pausa: {still_s / 60:.1f} min")
  for t, mode, reason in modes:
    pt(f"  {t / 60:7.1f} min  {mode:6s} {reason}")

  if len(power) >= 2:
    t0, level0, *_ = power[0]
    t1, level1, *_ = power[-1]
    hours = (t1 - t0) / 3600
    currents = [abs(p[2]) for p in power if p[2] is not None]
    pt(f"Batería: {fmt(level0, 0)} % a {fmt(level1, 0)} % en {hours:.2f} h ({fmt((level0 - level1) / hours if hours > 0 else None)} %/h)")
    pt(f"Corriente muestreada: mediana {fmt(percentile(currents, 50), 0)} mA. Temperatura máx: {fmt(max((p[3] for p in power if p[3] is not None), default=None))} °C")
  if baro:
    pressures = [p for _, p in baro]
    pt(f"Presión: {min(pressures):.2f} a {max(pressures):.2f} hPa (unos {(max(pressures) - min(pressures)) * 8.9:.0f} m de desnivel)")
  pt(f"Puntos marcados: {len(marks)}")


if __name__ == "__main__":
  if len(sys.argv) != 2:
    pt("Uso: python -I tools/session_summary.py recordings/session_YYYYMMDD_HHMMSS.csv")
    sys.exit(1)
  main(sys.argv[1])
