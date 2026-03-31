package com.stockflip

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class HelpFragment : Fragment() {

    private var webView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val wv = WebView(requireContext())
        webView = wv
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val markdown = requireContext().assets.open("manual.md").bufferedReader().use { it.readText() }
        wv.settings.javaScriptEnabled = false
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                // Allow local file navigation (fragment anchors), block everything else
                return scheme != "file" && scheme != "about"
            }
        }
        wv.loadDataWithBaseURL(
            "file:///android_asset/",
            markdownToHtml(markdown, isDark),
            "text/html",
            "UTF-8",
            null
        )
        return wv
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}

// ---------------------------------------------------------------------------
// Markdown → HTML converter (handles the subset used in manual.md)
// ---------------------------------------------------------------------------

private fun markdownToHtml(markdown: String, darkMode: Boolean): String {
    val themeClass = if (darkMode) "dark" else "light"
    return """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>${buildManualCss()}</style>
</head>
<body class="$themeClass">
${buildManualBody(markdown)}
</body>
</html>"""
}

private fun buildManualCss(): String = """
:root {
  --bg:#FFFBFE;--text:#1C1B1F;--heading:#1C1B1F;
  --surface:#F4EFF4;--border:#CAC4D0;--code-bg:#F4EFF4;
  --link:#6750A4;--th-bg:#E8DEF8;--bq-border:#7965AF;--bq-bg:#F6F0FF;
}
.dark {
  --bg:#1C1B1F;--text:#E6E1E5;--heading:#E6E1E5;
  --surface:#2B2930;--border:#49454F;--code-bg:#2B2930;
  --link:#D0BCFF;--th-bg:#4A4458;--bq-border:#9A82DB;--bq-bg:#21192E;
}
*{box-sizing:border-box}
body{font-family:Roboto,sans-serif;font-size:15px;line-height:1.65;
  color:var(--text);background:var(--bg);padding:16px;margin:0}
h1{font-size:1.5em;font-weight:700;margin:.4em 0 .3em;color:var(--heading)}
h2{font-size:1.2em;font-weight:600;margin:1.6em 0 .3em;color:var(--heading);
  border-bottom:1px solid var(--border);padding-bottom:4px}
h3{font-size:1.0em;font-weight:600;margin:1.2em 0 .25em;color:var(--heading)}
p{margin:.45em 0}
ul,ol{padding-left:1.5em;margin:.35em 0}
li{margin:.2em 0}
li.sub{list-style:circle;margin-left:1em}
a{color:var(--link);text-decoration:none}
hr{border:none;border-top:1px solid var(--border);margin:1.4em 0}
table{border-collapse:collapse;width:100%;margin:.7em 0;font-size:.875em}
th{background:var(--th-bg);padding:7px 10px;text-align:left;
  font-weight:600;border:1px solid var(--border)}
td{padding:6px 10px;border:1px solid var(--border)}
tr:nth-child(even) td{background:var(--surface)}
code{font-family:monospace;font-size:.9em;background:var(--code-bg);
  padding:1px 4px;border-radius:3px}
pre{background:var(--code-bg);padding:12px;border-radius:6px;
  overflow-x:auto;margin:.6em 0}
pre code{padding:0;background:none}
blockquote{border-left:3px solid var(--bq-border);margin:.7em 0;
  padding:8px 12px;background:var(--bq-bg);border-radius:0 4px 4px 0}
blockquote p{margin:0}
strong{font-weight:700}
em{font-style:italic}
""".trimIndent()

private fun buildManualBody(markdown: String): String {
    val sb = StringBuilder()
    val lines = markdown.lines()
    var i = 0
    var currentListTag: String? = null
    var inCodeBlock = false
    val codeLines = StringBuilder()

    fun closeList() {
        currentListTag?.let { sb.append("</$it>\n"); currentListTag = null }
    }

    while (i < lines.size) {
        val line = lines[i]

        // Code block fence
        if (line.trimStart().startsWith("```")) {
            if (!inCodeBlock) {
                closeList()
                inCodeBlock = true
                codeLines.setLength(0)
            } else {
                inCodeBlock = false
                sb.append("<pre><code>${esc(codeLines.toString().trimEnd('\n'))}</code></pre>\n")
            }
            i++; continue
        }
        if (inCodeBlock) { codeLines.append(line).append('\n'); i++; continue }

        // Table (collect all consecutive | lines)
        if (line.startsWith("|")) {
            closeList()
            val tableRows = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith("|")) { tableRows.add(lines[i]); i++ }
            sb.append(buildManualTable(tableRows))
            continue
        }

        // List items
        val isSubItem = line.startsWith("  - ")
        val isUnordered = line.startsWith("- ") && !line.startsWith("---")
        val isOrdered = line.matches(Regex("^\\d+\\. .*"))

        when {
            isOrdered -> {
                if (currentListTag != "ol") { closeList(); sb.append("<ol>\n"); currentListTag = "ol" }
                sb.append("<li>${fmt(line.replace(Regex("^\\d+\\. "), ""))}</li>\n")
            }
            isSubItem -> {
                if (currentListTag != "ul") { closeList(); sb.append("<ul>\n"); currentListTag = "ul" }
                sb.append("<li class=\"sub\">${fmt(line.drop(4))}</li>\n")
            }
            isUnordered -> {
                if (currentListTag != "ul") { closeList(); sb.append("<ul>\n"); currentListTag = "ul" }
                sb.append("<li>${fmt(line.drop(2))}</li>\n")
            }
            else -> {
                closeList()
                when {
                    line.startsWith("# ")   -> sb.append("<h1 id=\"${toId(line.drop(2))}\">${fmt(line.drop(2))}</h1>\n")
                    line.startsWith("## ")  -> sb.append("<h2 id=\"${toId(line.drop(3))}\">${fmt(line.drop(3))}</h2>\n")
                    line.startsWith("### ") -> sb.append("<h3 id=\"${toId(line.drop(4))}\">${fmt(line.drop(4))}</h3>\n")
                    line.startsWith("---")  -> sb.append("<hr>\n")
                    line.startsWith("> ")   -> sb.append("<blockquote><p>${fmt(line.drop(2))}</p></blockquote>\n")
                    line.isBlank()          -> { /* spacing handled by CSS margins */ }
                    else                    -> sb.append("<p>${fmt(line)}</p>\n")
                }
            }
        }
        i++
    }
    closeList()
    return sb.toString()
}

private fun buildManualTable(rows: List<String>): String {
    if (rows.size < 2) return ""
    val sb = StringBuilder("<table>\n<thead><tr>")
    splitRow(rows[0]).forEach { sb.append("<th>${fmt(it)}</th>") }
    sb.append("</tr></thead>\n<tbody>\n")
    for (j in 2 until rows.size) {
        sb.append("<tr>")
        splitRow(rows[j]).forEach { sb.append("<td>${fmt(it)}</td>") }
        sb.append("</tr>\n")
    }
    return sb.append("</tbody></table>\n").toString()
}

private fun splitRow(line: String) = line.trim('|').split("|").map { it.trim() }

private fun fmt(text: String): String {
    var s = esc(text)
    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
    s = s.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
    s = s.replace(Regex("`(.+?)`"), "<code>$1</code>")
    s = s.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
    return s
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun toId(text: String) = text.lowercase()
    .replace('å', 'a').replace('ä', 'a').replace('ö', 'o')
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .replace(Regex("\\s+"), "-")
    .trim('-')
