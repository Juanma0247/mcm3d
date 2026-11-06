package cc.juanma.mcm3d.core.zone

import org.xmlpull.v1.XmlPullParser

class OverpassException(message: String) : Exception(message)

/** Streams an Overpass XML answer (`out geom`) into ways; it never holds the whole document. */
object OverpassParser {
  fun parse(parser: XmlPullParser): List<OsmWay> {
    val ways = ArrayList<OsmWay>()
    var inWay = false
    var wayClass: WayClass? = null
    var lats = DoubleArray(64)
    var lons = DoubleArray(64)
    var n = 0
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        when (parser.name) {
          "way" -> {
            inWay = true
            wayClass = null
            n = 0
          }
          "nd" -> if (inWay) {
            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
            if (lat != null && lon != null) {
              if (n == lats.size) {
                lats = lats.copyOf(n * 2)
                lons = lons.copyOf(n * 2)
              }
              lats[n] = lat
              lons[n] = lon
              n++
            }
          }
          "tag" -> if (inWay && parser.getAttributeValue(null, "k") == "highway") {
            wayClass = classifyHighway(parser.getAttributeValue(null, "v").orEmpty())
          }
          "remark" -> {
            val text = parser.nextText()
            if (text.contains("error", ignoreCase = true)) throw OverpassException(text.trim())
          }
        }
      } else if (event == XmlPullParser.END_TAG && parser.name == "way") {
        val cls = wayClass
        if (inWay && cls != null && n >= 2) ways.add(OsmWay(cls, lats.copyOf(n), lons.copyOf(n)))
        inWay = false
      }
      event = parser.next()
    }
    return ways
  }
}
