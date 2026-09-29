package com.stockflip.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing-skala på 8 dp-raster (med 4 dp som halvsteg). */
object Space {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp

    /** Horisontell sidmarginal för skärmar och rader. */
    val screenH = 24.dp
    /** Minsta träffyta (tillgänglighet). */
    val touch   = 48.dp
}

/** Gamla tokens — behålls tills korten ersatts (fas 3–4). */
object NP {
    val cardOuterH = 12.dp
    val cardOuterV = 3.dp
}
