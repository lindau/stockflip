package com.stockflip.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color

/** En https-länk i en text: [start] inklusive, [end] exklusive. */
internal data class TextLink(val start: Int, val end: Int, val url: String)

private val URL_REGEX = Regex("""https://[^\s<>)\]]+""")

/** Tecken som hör till omgivande text eller markdown, inte till själva adressen. */
private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', '*', '_', '\'', '"')

/** Hittar https-adresser i releasetexten. Bara https görs klickbara. */
internal fun findLinks(text: String): List<TextLink> = URL_REGEX.findAll(text).mapNotNull { match ->
    val url = match.value.trimEnd(*TRAILING_PUNCTUATION)
    if (url.length <= "https://".length) null else TextLink(match.range.first, match.range.first + url.length, url)
}.toList()

/** Releasetexten med adresserna som klickbara, understrukna länkar i [linkColor]. */
internal fun linkifyReleaseNotes(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    findLinks(text).forEach { addLink(LinkAnnotation.Url(it.url, style), it.start, it.end) }
}

/** Öppnar länkar i en Chrome Custom Tab över appen; saknas stöd öppnas standardwebbläsaren. */
internal class InAppBrowserUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "https") return
        try {
            CustomTabsIntent.Builder().build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(context, parsed)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // Ingen webbläsare installerad: inget att göra.
            }
        }
    }
}
