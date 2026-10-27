import io
import json
import math
import os
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

pt = print

GPX_PATH = sys.argv[1]
CACHE_DIR = sys.argv[2]
os.makedirs(CACHE_DIR, exist_ok=True)
NS = {"g": "http://www.topografix.com/GPX/1/1"}
EARTH_R = 6371000.0
USER_AGENT = "MCM3D-feasibility-check/0.1"
ZOOM = 13

root = ET.parse(GPX_PATH).getroot()
raw = [(float(p.get("lat")), float(p.get("lon")), float(p.find("g:ele", NS).text))
       for p in root.iterfind(".//g:trkpt", NS)]
lat = np.array([p[0] for p in raw])
lon = np.array([p[1] for p in raw])
ele = np.array([p[2] for p in raw])

lat0, lon0 = lat.mean(), lon.mean()
kx = math.radians(1) * EARTH_R * math.cos(math.radians(lat0))
ky = math.radians(1) * EARTH_R


def to_xy(la, lo):
  return (np.asarray(lo) - lon0) * kx, (np.asarray(la) - lat0) * ky


def fetch(url, cache_name, data=None):
  path = os.path.join(CACHE_DIR, cache_name)
  if not os.path.exists(path):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp, open(path, "wb") as out:
      out.write(resp.read())
  with open(path, "rb") as f:
    return f.read()


x, y = to_xy(lat, lon)
seg_len = np.hypot(np.diff(x), np.diff(y))
cum = np.concatenate([[0], np.cumsum(seg_len)])
step = 5.0
s = np.arange(0, cum[-1], step)
sx, sy = np.interp(s, cum, x), np.interp(s, cum, y)
slat, slon = sy / ky + lat0, sx / kx + lon0

pad = 0.003
bbox = (lat.min() - pad, lon.min() - pad, lat.max() + pad, lon.max() + pad)
query = (f'[out:json][timeout:90];way["highway"]({bbox[0]:.6f},{bbox[1]:.6f},{bbox[2]:.6f},{bbox[3]:.6f});'
         "out geom tags;")
osm = json.loads(fetch("https://overpass-api.de/api/interpreter", "overpass.json",
                       urllib.parse.urlencode({"data": query}).encode()))
ax, ay, bx, by, kinds = [], [], [], [], []
for way in osm["elements"]:
  geom = way.get("geometry") or []
  if len(geom) < 2:
    continue
  wx, wy = to_xy([g["lat"] for g in geom], [g["lon"] for g in geom])
  ax.extend(wx[:-1]); ay.extend(wy[:-1]); bx.extend(wx[1:]); by.extend(wy[1:])
  kinds.extend([way["tags"].get("highway", "?")] * (len(geom) - 1))
ax, ay, bx, by = map(np.array, (ax, ay, bx, by))
kinds = np.array(kinds)
dx, dy = bx - ax, by - ay
len2 = np.maximum(dx * dx + dy * dy, 1e-9)

nearest_dist = np.empty(len(s))
nearest_kind = np.empty(len(s), dtype=object)
for i in range(len(s)):
  t = np.clip(((sx[i] - ax) * dx + (sy[i] - ay) * dy) / len2, 0, 1)
  d = np.hypot(ax + t * dx - sx[i], ay + t * dy - sy[i])
  j = int(np.argmin(d))
  nearest_dist[i], nearest_kind[i] = d[j], kinds[j]

pt(f"OSM highway ways in bbox: {sum(1 for w in osm['elements'] if w.get('geometry'))}, segments: {len(ax)}")
pt(f"Route length: {cum[-1] / 1000:.2f} km, samples every {step:.0f} m: {len(s)}")
for limit in (3, 5, 10, 20):
  pt(f"  within {limit:>2} m of an OSM way: {100 * np.mean(nearest_dist <= limit):5.1f} %")
pt(f"  distance median {np.median(nearest_dist):.1f} m, p95 {np.percentile(nearest_dist, 95):.1f} m, "
   f"max {nearest_dist.max():.1f} m")
off = nearest_dist > 20
runs, start = [], None
for i, flag in enumerate(off):
  if flag and start is None:
    start = i
  if (not flag or i == len(off) - 1) and start is not None:
    runs.append((start, i))
    start = None
pt(f"Stretches farther than 20 m from OSM: {len(runs)}, total {sum(e - b for b, e in runs) * step:.0f} m")
for b, e in sorted(runs, key=lambda r: r[0] - r[1])[:5]:
  pt(f"  km {s[b] / 1000:.2f}-{s[e] / 1000:.2f} ({(e - b) * step:.0f} m), max {nearest_dist[b:e + 1].max():.0f} m")
pt("Route length by nearest OSM highway type (samples within 10 m):")
on = nearest_dist <= 10
for kind in sorted(set(nearest_kind[on]), key=lambda k: -np.sum(nearest_kind[on] == k)):
  pt(f"  {kind:<14} {np.sum(nearest_kind[on] == kind) * step / 1000:6.2f} km")


def tile_xy(la, lo, z):
  n = 2 ** z
  tx = (lo + 180) / 360 * n
  ty = (1 - math.asinh(math.tan(math.radians(la))) / math.pi) / 2 * n
  return tx, ty


tx0, ty0 = tile_xy(bbox[2], bbox[1], ZOOM)
tx1, ty1 = tile_xy(bbox[0], bbox[3], ZOOM)
tiles_x = range(int(tx0), int(tx1) + 1)
tiles_y = range(int(ty0), int(ty1) + 1)
mosaic = np.zeros((256 * len(tiles_y), 256 * len(tiles_x)))
for iy, ty in enumerate(tiles_y):
  for ix, tx in enumerate(tiles_x):
    png = fetch(f"https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{ZOOM}/{tx}/{ty}.png",
                f"terrarium_{ZOOM}_{tx}_{ty}.png")
    rgb = np.asarray(Image.open(io.BytesIO(png)).convert("RGB")).astype(np.float64)
    mosaic[iy * 256:(iy + 1) * 256, ix * 256:(ix + 1) * 256] = rgb[..., 0] * 256 + rgb[..., 1] + rgb[..., 2] / 256 - 32768
pt(f"Terrarium z{ZOOM}: {len(tiles_x) * len(tiles_y)} tiles, pixel ~{40075016.686 * math.cos(math.radians(lat0)) / 2 ** ZOOM / 256:.1f} m")


def dem(la, lo):
  fx, fy = tile_xy(la, lo, ZOOM)
  px = (fx - tiles_x.start) * 256 - 0.5
  py = (fy - tiles_y.start) * 256 - 0.5
  x0, y0 = int(math.floor(px)), int(math.floor(py))
  ux, uy = px - x0, py - y0
  q = mosaic[y0:y0 + 2, x0:x0 + 2]
  return (q[0, 0] * (1 - ux) * (1 - uy) + q[0, 1] * ux * (1 - uy)
          + q[1, 0] * (1 - ux) * uy + q[1, 1] * ux * uy)


dem_vertices = np.array([dem(a, o) for a, o in zip(lat, lon)])
diff = ele - dem_vertices
pt(f"Komoot ele - DEM at vertices: mean {diff.mean():+.1f} m, std {diff.std():.1f} m, "
   f"p95 |d| {np.percentile(np.abs(diff), 95):.1f} m, max |d| {np.abs(diff).max():.1f} m")
pt(f"Start/end: Komoot {ele[0]:.1f} / {ele[-1]:.1f} m, DEM {dem_vertices[0]:.1f} / {dem_vertices[-1]:.1f} m")
dem_samples = np.array([dem(a, o) for a, o in zip(slat, slon)])
kernel = np.ones(9) / 9
smooth = np.convolve(dem_samples, kernel, mode="valid")
d = np.diff(smooth)
pt(f"Gain/loss Komoot: +{np.sum(np.clip(np.diff(ele), 0, None)):.0f} / -{np.sum(np.clip(-np.diff(ele), 0, None)):.0f} m")
pt(f"Gain/loss DEM (5 m samples, 45 m smoothing): +{np.sum(np.clip(d, 0, None)):.0f} / -{np.sum(np.clip(-d, 0, None)):.0f} m")
grade = np.diff(dem_samples[::4]) / (step * 4)
pt(f"Grade along route (20 m windows): p50 {100 * np.median(np.abs(grade)):.0f} %, "
   f"p90 {100 * np.percentile(np.abs(grade), 90):.0f} %, max {100 * np.abs(grade).max():.0f} %")
