package com.stockflip

/** Enda platsen som känner till att GitHub-taggar har ett "v"-prefix (t.ex. "v1.2.94"). */
internal fun stripVersionPrefix(tagOrVersion: String): String = tagOrVersion.removePrefix("v")

/**
 * Jämför två versionsnamn på formen "1.2.93" komponentvis. Icke-numeriska komponenter
 * tolkas som 0 i stället för att kasta, och saknade komponenter (olika antal segment)
 * behandlas också som 0 -- kastar aldrig på oväntad indata från ett externt API.
 */
fun isNewerVersion(currentVersionName: String, candidateVersionName: String): Boolean {
    val currentParts = currentVersionName.split(".")
    val candidateParts = candidateVersionName.split(".")
    val length = maxOf(currentParts.size, candidateParts.size)
    for (i in 0 until length) {
        val current = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        val candidate = candidateParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (candidate > current) return true
        if (candidate < current) return false
    }
    return false
}
