package com.stockflip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.PodcastSignalDto
import com.stockflip.ui.theme.Space
import java.util.Locale

enum class SignalTone { Positive, Negative, Neutral }

data class PodcastFlagLabel(val text: String, val tone: SignalTone)

/** Flagga från podcast-analysis → svensk etikett. Okända flaggor ger null och ignoreras. */
fun podcastFlagLabel(flag: String): PodcastFlagLabel? = when (flag) {
    "konsensus_pos" -> PodcastFlagLabel("Konsensus positiv", SignalTone.Positive)
    "konsensus_neg" -> PodcastFlagLabel("Konsensus negativ", SignalTone.Negative)
    "splittrad" -> PodcastFlagLabel("Splittrade åsikter", SignalTone.Neutral)
    "ökar" -> PodcastFlagLabel("Diskuteras mer", SignalTone.Neutral)
    "ny" -> PodcastFlagLabel("Ny i poddarna", SignalTone.Neutral)
    "vänt_pos" -> PodcastFlagLabel("Sentiment vänt positivt", SignalTone.Positive)
    "vänt_neg" -> PodcastFlagLabel("Sentiment vänt negativt", SignalTone.Negative)
    "poddägare_köper" -> PodcastFlagLabel("Poddvärd köper", SignalTone.Positive)
    "poddägare_säljer" -> PodcastFlagLabel("Poddvärd säljer", SignalTone.Negative)
    "sent_ute" -> PodcastFlagLabel("Sent ute (redan upp ≥15 %)", SignalTone.Neutral)
    "insyn_köper" -> PodcastFlagLabel("Insyn köper", SignalTone.Positive)
    "insyn_säljer" -> PodcastFlagLabel("Insyn säljer", SignalTone.Negative)
    "rapport_snart" -> PodcastFlagLabel("Rapport snart", SignalTone.Neutral)
    "blankat" -> PodcastFlagLabel("Blankad", SignalTone.Negative)
    "småbolag" -> PodcastFlagLabel("Småbolag", SignalTone.Neutral)
    else -> null
}

fun PodcastSignalDto.flagLabels(): List<PodcastFlagLabel> = flags.mapNotNull(::podcastFlagLabel)

/** "3 poddar · 5 avsnitt · positivt" */
fun PodcastSignalDto.summaryText(): String {
    val sentiment = when {
        net == null -> null
        net >= 0.5 -> "positivt"
        net <= -0.5 -> "negativt"
        else -> "blandat"
    }
    val pods = if (pods == 1) "1 podd" else "$pods poddar"
    val eps = if (episodes == 1) "1 avsnitt" else "$episodes avsnitt"
    return listOfNotNull(pods, eps, sentiment).joinToString(" · ")
}

fun PodcastSignalDto.sinceFirstText(): String? {
    val pct = price?.pct ?: return null
    val date = price.first_date?.let { " ($it)" }.orEmpty()
    return String.format(Locale("sv", "SE"), "%+.1f %%", pct) + " sedan första omnämnandet" + date
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PodcastFlagChips(signal: PodcastSignalDto, modifier: Modifier = Modifier, max: Int = Int.MAX_VALUE) {
    val labels = signal.flagLabels().take(max)
    if (labels.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { l ->
            AssistChip(onClick = {}, label = { Text(l.text, style = MaterialTheme.typography.labelMedium) })
        }
    }
}

/** Signalkort överst i poddsektionen på aktiesidan. */
@Composable
fun PodcastSignalCard(signal: PodcastSignalDto) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(signal.summaryText() + " senaste 14 dagarna", style = MaterialTheme.typography.bodyMedium)
        signal.sinceFirstText()?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PodcastFlagChips(signal)
    }
}

/** En markör i kursgrafen: [timestamp] i epoch-sekunder (som grafens egna), [tone] +1 positiv, -1 negativ, 0 neutral/blandad. */
data class MentionMarker(val timestamp: Long, val tone: Int, val notes: List<String> = emptyList())

internal fun stanceTone(stance: String?): Int {
    val s = stance?.lowercase(Locale.ROOT) ?: return 0
    return when {
        "neg" in s -> -1
        "pos" in s -> 1
        else -> 0
    }
}

/** "Kort & Lång · Positiv · Köp": podd, ton och rekommendation (tomma delar utelämnas). */
internal fun mentionNote(o: com.stockflip.PodcastObservationEntity): String =
    listOfNotNull(o.podcast, o.stance, o.recommendation).filter { it.isNotBlank() }.joinToString(" · ")

private const val DAY_SECONDS = 86_400L

/** Slår ihop omnämnanden per dag (UTC); om tonen skiljer sig inom en dag blir markören neutral (grå). */
fun mentionMarkersFrom(observations: List<com.stockflip.PodcastObservationEntity>): List<MentionMarker> =
    observations.mapNotNull { o -> o.publishedAtMillis?.let { Triple(it / 1000L, stanceTone(o.stance), mentionNote(o)) } }
        .groupBy { (ts, _, _) -> ts / DAY_SECONDS }
        .map { (day, items) ->
            val tones = items.map { it.second }.toSet()
            MentionMarker(day * DAY_SECONDS + DAY_SECONDS / 2, if (tones.size == 1) tones.first() else 0, items.map { it.third }.distinct())
        }
        .sortedBy { it.timestamp }

/** Index i [timestamps] (sorterad) närmast [ts], eller null om [ts] ligger utanför grafens intervall. */
internal fun chartIndexFor(timestamps: List<Long>, ts: Long): Int? {
    if (timestamps.size < 2) return null
    if (ts < timestamps.first() - DAY_SECONDS || ts > timestamps.last() + DAY_SECONDS) return null
    var lo = 0
    var hi = timestamps.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (timestamps[mid] < ts) lo = mid + 1 else hi = mid
    }
    val prev = (lo - 1).coerceAtLeast(0)
    return if (kotlin.math.abs(timestamps[prev] - ts) <= kotlin.math.abs(timestamps[lo] - ts)) prev else lo
}
