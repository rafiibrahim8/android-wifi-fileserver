package me.ibrahimrafi.wififileshare.server

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
private val inlineSvgCache = ConcurrentHashMap<String, String>()
private val safeIconNamePattern = Regex("^[A-Za-z0-9_.-]+\\.svg$")

internal fun buildRows(context: Context, currentPath: String, serverBase: String, entries: List<DirectoryEntry>): String {
    return buildString {
        buildUpRow(context, currentPath, serverBase)?.let { append(it) }

        for (entry in entries) {
            val encoded = percentEncodePath(entry.relativePath)
            val icon = iconForEntry(entry)
            val escapedRelativePath = escapeHtml(entry.relativePath)
            if (entry.isDirectory) {
                val href = "$serverBase/$encoded"
                append("<tr class=\"entry-row entry-selectable\" data-selectable=\"1\" data-kind=\"dir\" data-path=\"")
                append(escapedRelativePath)
                append("\" data-name=\"")
                append(escapeHtml(entry.name))
                append("\">")
                append("<td></td>")
                append("<td class=\"name-cell\"><a class=\"name-link\" href=\"")
                append(escapeHtml(href))
                append("\"><span class=\"name-wrap\">")
                append("<span class=\"select-mark\" aria-hidden=\"true\">✓</span>")
                append(inlineIconHtml(context, icon))
                append("<span class=\"name\">")
                append(escapeHtml(entry.name))
                append("</span></span></a></td>")
                append("<td class=\"center\">&mdash;</td>")
                append("<td class=\"hideable center\">&mdash;</td>")
                append("<td class=\"hideable\"></td>")
                append("</tr>")
            } else {
                val downloadUrl = "$serverBase/files/$encoded"
                val timeIso = isoTime(entry.modified)
                append("<tr class=\"entry-row entry-selectable\" data-selectable=\"1\" data-kind=\"file\" data-path=\"")
                append(escapedRelativePath)
                append("\" data-name=\"")
                append(escapeHtml(entry.name))
                append("\">")
                append("<td></td>")
                append("<td class=\"name-cell\"><a class=\"name-link\" href=\"")
                append(escapeHtml(downloadUrl))
                append("\" download=\"")
                append(escapeHtml(entry.name))
                append("\"><span class=\"name-wrap\">")
                append("<span class=\"select-mark\" aria-hidden=\"true\">✓</span>")
                append(inlineIconHtml(context, icon))
                append("<span class=\"name\">")
                append(escapeHtml(entry.name))
                append("</span></span></a>")
                append("<button class=\"copy-btn\" data-url=\"")
                append(escapeHtml(downloadUrl))
                append("\" title=\"Copy link\" aria-label=\"Copy link\">Copy</button></td>")
                append("<td class=\"center\">")
                append(escapeHtml(formatBytes(entry.size)))
                append("</td>")
                append("<td class=\"hideable center\"><time datetime=\"")
                append(escapeHtml(timeIso))
                append("\">")
                append(escapeHtml(formatTime(entry.modified)))
                append("</time></td>")
                append("<td class=\"hideable\"></td>")
                append("</tr>")
            }
        }
    }
}

private fun buildUpRow(context: Context, currentPath: String, serverBase: String): String? {
    val trimmed = currentPath.trim('/')
    if (trimmed.isBlank()) return null

    val parent = trimmed.substringBeforeLast('/', "")
    val href = if (parent.isBlank()) "$serverBase/" else "$serverBase/${percentEncodePath(parent)}"

    return """
        <tr class="entry-row">
          <td></td>
          <td class="name-cell">
            <a class="name-link" href="${escapeHtml(href)}">
              <span class="name-wrap">
                ${inlineIconHtml(context, "up.svg")}
                <span class="go-up">Up</span>
              </span>
            </a>
          </td>
          <td></td>
          <td class="hideable"></td>
          <td class="hideable"></td>
        </tr>
    """.trimIndent()
}

internal fun buildBreadcrumbTitle(currentPath: String, serverBase: String): String {
    val segments = currentPath.trim('/').split('/').filter { it.isNotBlank() }
    val out = mutableListOf<String>()
    out += "<a href=\"${escapeHtml("$serverBase/")}\">/</a>"

    var accumulated = ""
    for (segment in segments) {
        accumulated = if (accumulated.isBlank()) segment else "$accumulated/$segment"
        val href = "$serverBase/${percentEncodePath(accumulated)}"
        out += "<a href=\"${escapeHtml(href)}\">${escapeHtml(segment)}</a>"
        out += "<span>/</span>"
    }

    return out.joinToString(" ")
}

private fun isoTime(epochMs: Long): String {
    return isoFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

private fun inlineIconHtml(context: Context, iconFileName: String): String {
    val safeName = if (safeIconNamePattern.matches(iconFileName)) iconFileName else "default.svg"
    val primary = inlineSvgCache.getOrPut(safeName) { readSvgAsset(context, safeName).orEmpty() }
    if (primary.isNotBlank()) return primary

    if (safeName != "default.svg") {
        val fallback = inlineSvgCache.getOrPut("default.svg") { readSvgAsset(context, "default.svg").orEmpty() }
        if (fallback.isNotBlank()) return fallback
    }

    return "<span class=\"icon\" aria-hidden=\"true\"></span>"
}

private fun readSvgAsset(context: Context, iconFileName: String): String? {
    return runCatching {
        context.assets.open("icons/$iconFileName").bufferedReader().use { it.readText() }
    }.getOrNull()
}

internal fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

internal fun jsString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
