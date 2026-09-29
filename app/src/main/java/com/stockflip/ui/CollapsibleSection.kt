package com.stockflip.ui

import android.content.Context
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/**
 * Binder som gör en sektion på aktiedetaljsidan kollapsbar.
 *
 * Kollapsen styr enbart [body]:s synlighet, så befintlig visibility-logik på korten inuti
 * (t.ex. "dölj insidersektionen om det saknas data") påverkas inte. Läget sparas globalt
 * per sektion i SharedPreferences (inte per aktie).
 */
class CollapsibleSection(
    context: Context,
    private val key: String,
    private val header: View,
    private val titleView: TextView,
    private val summaryView: TextView,
    private val arrowView: ImageView,
    private val body: View,
    defaultExpanded: Boolean
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isExpanded: Boolean = runCatching { prefs.getBoolean(prefKey(), defaultExpanded) }
        .getOrDefault(defaultExpanded)
        private set

    private var summary: String? = null

    init {
        header.isClickable = true
        header.isFocusable = true
        header.setOnClickListener { setExpanded(!isExpanded, animate = true) }
        apply(animate = false)
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    /** Sammanfattningen visas bara när sektionen är kollapsad. */
    fun setSummary(text: String?) {
        summary = text?.takeIf { it.isNotBlank() }
        updateSummary()
    }

    fun expand() = setExpanded(true, animate = false)

    private fun setExpanded(expanded: Boolean, animate: Boolean) {
        if (expanded == isExpanded && !animate) return
        isExpanded = expanded
        runCatching { prefs.edit().putBoolean(prefKey(), expanded).apply() }
        apply(animate)
    }

    private fun apply(animate: Boolean) {
        if (animate) {
            (body.parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
        }
        body.visibility = if (isExpanded) View.VISIBLE else View.GONE
        val rotation = if (isExpanded) 180f else 0f
        if (animate) arrowView.animate().rotation(rotation).setDuration(150).start()
        else arrowView.rotation = rotation
        header.contentDescription = titleView.text
        header.isSelected = isExpanded
        updateSummary()
    }

    private fun updateSummary() {
        val text = summary
        val show = !isExpanded && text != null
        summaryView.text = text.orEmpty()
        summaryView.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun prefKey() = "expanded_$key"

    companion object {
        private const val PREFS_NAME = "stock_detail_sections"
    }
}
