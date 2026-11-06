package cc.juanma.mcm3d.core.route

import org.xmlpull.v1.XmlPullParser

internal fun localName(qualified: String): String = qualified.substringAfterLast(':')

internal fun XmlPullParser.doubleAttribute(name: String): Double {
  for (i in 0 until attributeCount) {
    if (localName(getAttributeName(i)) == name) return getAttributeValue(i).trim().toDoubleOrNull() ?: Double.NaN
  }
  return Double.NaN
}

internal fun XmlPullParser.textOrNull(): String? = nextText().trim().ifEmpty { null }

private val TAG = Regex("<[^>]*>")
private val SPACES = Regex("\\s+")

internal fun plainText(raw: String?, maxLength: Int = 500): String? {
  if (raw == null) return null
  val text = raw.replace(TAG, " ").replace("&nbsp;", " ").replace(SPACES, " ").trim()
  return text.take(maxLength).ifEmpty { null }
}
