package cc.juanma.mcm3d.core.route

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Detects GPX, KML or KMZ from the content itself: file names and MIME types from other apps are unreliable. */
object RouteFileReader {
  const val MAX_BYTES = 32 * 1024 * 1024
  private const val ENTITY_SCAN_BYTES = 64 * 1024

  fun read(bytes: ByteArray, newParser: () -> XmlPullParser): ParsedRoute {
    if (bytes.size > MAX_BYTES) throw RouteParseException(RouteParseException.Reason.TOO_LARGE)
    if (isZip(bytes)) {
      val kml = extractKml(bytes) ?: throw RouteParseException(RouteParseException.Reason.NOT_A_ROUTE)
      return parseXml(kml, newParser, zipped = true)
    }
    return parseXml(bytes, newParser, zipped = false)
  }

  private fun parseXml(bytes: ByteArray, newParser: () -> XmlPullParser, zipped: Boolean): ParsedRoute {
    // Entity declarations have no use in route files and allow expansion bombs.
    if (containsAscii(bytes, "<!ENTITY", ENTITY_SCAN_BYTES)) {
      throw RouteParseException(RouteParseException.Reason.MALFORMED)
    }
    val route = try {
      val parser = newParser()
      parser.setInput(ByteArrayInputStream(bytes), null)
      var event = parser.eventType
      while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) event = parser.next()
      if (event != XmlPullParser.START_TAG) throw RouteParseException(RouteParseException.Reason.NOT_A_ROUTE)
      when (localName(parser.name).lowercase()) {
        "gpx" -> GpxParser.parse(parser)
        "kml" -> KmlParser.parse(parser, if (zipped) RouteFormat.KMZ else RouteFormat.KML)
        else -> throw RouteParseException(RouteParseException.Reason.NOT_A_ROUTE)
      }
    } catch (error: XmlPullParserException) {
      throw RouteParseException(RouteParseException.Reason.MALFORMED, error)
    } catch (error: IOException) {
      throw RouteParseException(RouteParseException.Reason.MALFORMED, error)
    }
    if (route.points.size < 2) throw RouteParseException(RouteParseException.Reason.NO_TRACK)
    return route
  }

  private fun isZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
      bytes[2].toInt() == 3 && bytes[3].toInt() == 4

  private fun extractKml(bytes: ByteArray): ByteArray? {
    var fallback: ByteArray? = null
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          val name = entry.name.lowercase()
          if (entry.isDirectory || !name.endsWith(".kml")) continue
          val content = readLimited(zip)
          if (name.substringAfterLast('/') == "doc.kml") return content
          if (fallback == null) fallback = content
        }
      }
    } catch (error: IOException) {
      throw RouteParseException(RouteParseException.Reason.MALFORMED, error)
    }
    return fallback
  }

  private fun readLimited(zip: ZipInputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
      val n = zip.read(buffer)
      if (n < 0) break
      out.write(buffer, 0, n)
      if (out.size() > MAX_BYTES) throw RouteParseException(RouteParseException.Reason.TOO_LARGE)
    }
    return out.toByteArray()
  }

  private fun containsAscii(bytes: ByteArray, text: String, limit: Int): Boolean {
    val end = minOf(bytes.size, limit) - text.length
    outer@ for (i in 0..end) {
      for (j in text.indices) if (bytes[i + j] != text[j].code.toByte()) continue@outer
      return true
    }
    return false
  }
}
