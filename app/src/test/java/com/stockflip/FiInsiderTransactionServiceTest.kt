package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Test

class FiInsiderTransactionServiceTest {
    private val service = FiInsiderTransactionService()

    @Test
    fun `parseCsv parses FI export rows`() {
        val rows = service.parseCsv(sampleCsv)

        assertEquals(2, rows.size)
        assertEquals("NCC AB", rows[0]["Utgivare"])
        assertEquals("Förvärv", rows[0]["Karaktär"])
        assertEquals("200,16", rows[0]["Pris"])
        assertEquals("Silex Microsystems AB (publ)", rows[1]["Utgivare"])
    }

    private val sampleCsv = """
        Publiceringsdatum;Utgivare;LEI-kod;Anmälningsskyldig;Person i ledande ställning;Befattning;Närstående;Korrigering;Beskrivning av korrigering;Är förstagångsrapportering;Är kopplad till aktieprogram;Karaktär;Instrumenttyp;Instrumentnamn;ISIN;Transaktionsdatum;Volym;Volymsenhet;Pris;Valuta;Handelsplats;Status;
        2026-05-09 00:00:00;NCC AB;;Tomas Carlsson;Tomas Carlsson;Verkställande direktör (VD);;;;;;Förvärv;Aktie;NCC B;SE0000117970;2026-05-07 00:00:00;2158;Antal;200,16;SEK;NASDAQ STOCKHOLM;Aktuell;
        2026-05-09 00:00:00;Silex Microsystems AB (publ);;Joakim Pedersen;Joakim Pedersen;Styrelseledamot;Ja;;;;;Avyttring;Aktie;Silex Microsystems AB;SE0028000190;2026-05-08 00:00:00;292;Antal;341,30;SEK;NASDAQ STOCKHOLM;Aktuell;
    """.trimIndent()
}
