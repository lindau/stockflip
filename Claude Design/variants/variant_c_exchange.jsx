// VARIANT C — "Exchange"
// Editorial newspaper aesthetic. Warm newsprint, DM Serif Display headlines,
// chart-forward, hard corners, hairline rules. Quiet but confident.

const C = (() => {
  const STOCKS = [
    { sym: "ERIC B",  loc: "STO", name: "Ericsson B",   px: "138,44", d: "+1,58 %", up: true,  spark: [42,44,40,46,43,48,45,50,52,54,58,56,62] },
    { sym: "VOLV B",  loc: "STO", name: "Volvo B",      px: "287,30", d: "−0,28 %", up: false, spark: [60,58,62,56,59,54,56,52,50,48,52,46,44] },
    { sym: "AAPL",    loc: "NDQ", name: "Apple Inc.",   px: "187,23", d: "−0,48 %", up: false, spark: [52,54,50,52,48,50,46,48,44,46,42,44,40] },
    { sym: "MSFT",    loc: "NDQ", name: "Microsoft",    px: "412,05", d: "+0,80 %", up: true,  spark: [40,42,44,46,44,48,46,50,52,56,54,58,62] },
    { sym: "NVDA",    loc: "NDQ", name: "Nvidia",       px: "932,14", d: "+2,01 %", up: true,  spark: [30,34,38,42,46,42,48,52,56,54,60,64,68] },
    { sym: "BTC",     loc: "CRY", name: "Bitcoin",      px: "67 842", d: "−0,60 %", up: false, spark: [55,58,54,52,56,50,54,48,52,46,50,44,48] },
  ];

  const sparkPath = (pts, w=64, h=20) => {
    const min = Math.min(...pts), max = Math.max(...pts), r = max - min || 1;
    return pts.map((v,i) => `${(i/(pts.length-1))*w},${h - ((v-min)/r)*h}`).join(" L");
  };

  // Newspaper-style masthead
  const Masthead = ({ title, sub, day }) => (
    <div style={{
      padding: "16px 22px 12px",
      borderBottom: "1px solid var(--e-hairline)",
      background: "var(--e-bg)",
    }}>
      <div style={{
        display: "flex", justifyContent: "space-between", alignItems: "baseline",
        fontSize: 9.5, color: "var(--e-ink-2)", letterSpacing: 1.6,
        textTransform: "uppercase", fontFamily: "var(--va-sf)", fontWeight: 600,
      }}>
        <span>StockFlip · {sub}</span>
        <span>{day}</span>
      </div>
      <div style={{
        fontFamily: "var(--va-serif)", fontSize: 38, fontWeight: 400,
        letterSpacing: -0.6, lineHeight: 1.05, marginTop: 6, color: "var(--e-ink)",
      }}>{title}</div>
    </div>
  );

  // Tab strip — pill row, hard corners
  const Tabs = ({ items, active }) => (
    <div style={{
      display: "flex", gap: 0, padding: "10px 22px 12px",
      background: "var(--e-bg)", borderBottom: "1px solid var(--e-line)",
    }}>
      {items.map((t, i) => (
        <div key={t} style={{
          padding: "5px 12px", fontSize: 11, fontWeight: 600,
          letterSpacing: 0.6, fontFamily: "var(--va-sf)",
          color: i === active ? "#FBF8F1" : "var(--e-ink-2)",
          background: i === active ? "var(--e-chip-on)" : "transparent",
          marginRight: 4,
        }}>{t}</div>
      ))}
    </div>
  );

  // Stocks list — newsprint roster
  const Stocks = () => (
    <div style={{ background: "var(--e-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <Masthead title="Marknader" sub="Bevakning" day="Tor 14 nov"/>
      <Tabs items={["Allt","Sverige","USA","Krypto"]} active={0}/>
      <div style={{ overflow: "auto", flex: 1 }}>
        {STOCKS.map((s, i) => (
          <div key={s.sym} style={{
            display: "grid", gridTemplateColumns: "1.6fr 0.8fr 0.5fr 0.7fr",
            alignItems: "center", padding: "16px 22px",
            borderBottom: "1px solid var(--e-line)",
            background: "var(--e-bg)",
          }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
                <span style={{ fontFamily: "var(--va-serif)", fontSize: 19, color: "var(--e-ink)", letterSpacing: -0.2 }}>{s.sym}</span>
                <span style={{ fontSize: 9, color: "var(--e-ink-3)", letterSpacing: 1.2, fontWeight: 600 }}>{s.loc}</span>
              </div>
              <div style={{ fontSize: 11.5, color: "var(--e-ink-2)", marginTop: 2, fontStyle: "italic", fontFamily: "var(--va-serif)" }}>{s.name}</div>
            </div>
            <div style={{
              textAlign: "right", fontFamily: "var(--va-serif)", fontSize: 22,
              color: "var(--e-ink)", letterSpacing: -0.4, fontVariantNumeric: "tabular-nums",
            }}>{s.px}</div>
            <div style={{
              textAlign: "right", fontSize: 12, fontWeight: 600,
              fontFamily: "var(--va-sf)", fontVariantNumeric: "tabular-nums",
              color: s.up ? "var(--e-up)" : "var(--e-dn)",
            }}>{s.d}</div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <svg width="64" height="20" viewBox="0 0 64 20"><path d={"M" + sparkPath(s.spark)} fill="none" stroke={s.up ? "var(--e-up)" : "var(--e-dn)"} strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/></svg>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  // Pairs — editorial spread cards
  const PAIRS = [
    { a: "ERIC B", b: "Nokia", sp: "3,4720", d: "+1,20 %", up: true,  caption: "Telekom-divergens" },
    { a: "Volvo B", b: "Sandvik", sp: "1,4820", d: "−1,19 %", up: false, caption: "Industri-konvergens" },
    { a: "AAPL", b: "MSFT", sp: "0,4544", d: "−1,77 %", up: false, caption: "Big tech-rotation" },
    { a: "BTC", b: "ETH", sp: "19,48", d: "+1,72 %", up: true,  caption: "Krypto-spread" },
  ];

  const Pairs = () => (
    <div style={{ background: "var(--e-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <Masthead title="Relationer" sub="Par" day="Tor 14 nov"/>
      <Tabs items={["Alla","Divergerande","Konvergerande"]} active={0}/>
      <div style={{ overflow: "auto", flex: 1 }}>
        {PAIRS.map((p, i) => (
          <div key={p.a+p.b} style={{
            padding: "20px 22px", borderBottom: "1px solid var(--e-line)",
          }}>
            <div style={{ fontSize: 9.5, color: "var(--e-ink-3)", letterSpacing: 1.6, textTransform: "uppercase", fontWeight: 600, marginBottom: 8 }}>
              № {String(i+1).padStart(2,"0")} · {p.caption}
            </div>
            <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12 }}>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ fontFamily: "var(--va-serif)", fontSize: 22, lineHeight: 1.1, color: "var(--e-ink)", letterSpacing: -0.3 }}>
                  {p.a} <span style={{ color: "var(--e-ink-3)", fontStyle: "italic", fontSize: 16 }}>mot</span> {p.b}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div style={{ fontFamily: "var(--va-serif)", fontSize: 24, color: "var(--e-ink)", letterSpacing: -0.4, fontVariantNumeric: "tabular-nums" }}>{p.sp}</div>
                <div style={{ fontSize: 11.5, fontWeight: 600, color: p.up ? "var(--e-up)" : "var(--e-dn)", marginTop: 2, fontVariantNumeric: "tabular-nums" }}>{p.d}</div>
              </div>
            </div>
            {/* Mini chart strip */}
            <svg width="100%" height="34" viewBox="0 0 320 34" style={{ marginTop: 12, display: "block" }}>
              <line x1="0" y1="17" x2="320" y2="17" stroke="var(--e-line)" strokeWidth="0.6" strokeDasharray="2,3"/>
              <path d={p.up
                ? "M0 24 L20 22 L40 25 L60 20 L80 22 L100 18 L120 20 L140 16 L160 17 L180 14 L200 15 L220 11 L240 12 L260 9 L280 11 L300 7 L320 8"
                : "M0 8 L20 11 L40 9 L60 13 L80 11 L100 14 L120 12 L140 16 L160 14 L180 18 L200 17 L220 21 L240 19 L260 23 L280 22 L300 25 L320 24"
              } fill="none" stroke={p.up ? "var(--e-up)" : "var(--e-dn)"} strokeWidth="1.4"/>
            </svg>
          </div>
        ))}
      </div>
    </div>
  );

  // Alerts — editorial bulletin
  const ALERTS = [
    { sym: "ERIC B",  t: "Pris över 145,00",  s: "Engångs · över · vid 14:32",   tr: true,  amt: "146,02" },
    { sym: "ERIC B",  t: "Daglig rörelse > ±3 %", s: "Återkommande · +3,18 %",   tr: true,  amt: "+3,18 %" },
    { sym: "ERIC B",  t: "Drawdown från 52v-topp > 20 %", s: "Bevakar från 152,60", tr: false, amt: "−9,2 %" },
    { sym: "AAPL",    t: "P/E under 22,0", s: "Fundamentalt · dagligt", tr: false, amt: "P/E 28,4" },
    { sym: "AAPL",    t: "Pris i 180–195", s: "Återkommande · båda hållen", tr: false, amt: "187,23" },
    { sym: "BTC",     t: "Pris över 70 000", s: "Engångs · pausad", tr: false, off: true, amt: "67 842" },
  ];

  const Alerts = () => (
    <div style={{ background: "var(--e-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <Masthead title="Bulletinen" sub="Bevakningar" day="Tor 14 nov"/>
      <Tabs items={["Alla","Triggade","Bevakar","Av"]} active={0}/>
      {/* Editorial summary line */}
      <div style={{
        padding: "12px 22px", fontFamily: "var(--va-serif)", fontStyle: "italic",
        fontSize: 13, color: "var(--e-ink-2)", borderBottom: "1px solid var(--e-line)",
        lineHeight: 1.4,
      }}>
        Två av åtta larm utlöstes idag. Båda gäller <span style={{ color: "var(--e-ink)" }}>Ericsson B</span>, som passerade 145-strecket strax efter öppning.
      </div>
      <div style={{ overflow: "auto", flex: 1 }}>
        {ALERTS.map((a, i) => (
          <div key={i} style={{
            padding: "16px 22px", borderBottom: "1px solid var(--e-line)",
            opacity: a.off ? 0.5 : 1,
            background: a.tr ? "rgba(164,96,27,0.06)" : "transparent",
            borderLeft: a.tr ? "3px solid var(--e-accent)" : "3px solid transparent",
            paddingLeft: a.tr ? 19 : 22,
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 4 }}>
              <span style={{ fontSize: 9.5, letterSpacing: 1.4, color: "var(--e-ink-3)", textTransform: "uppercase", fontWeight: 600 }}>
                {a.sym} {a.tr && <span style={{ color: "var(--e-accent)", marginLeft: 8 }}>· UTLÖST</span>}
              </span>
              <span style={{ fontFamily: "var(--va-serif)", fontSize: 14, color: "var(--e-ink)", fontVariantNumeric: "tabular-nums" }}>{a.amt}</span>
            </div>
            <div style={{ fontFamily: "var(--va-serif)", fontSize: 18, color: "var(--e-ink)", letterSpacing: -0.2, lineHeight: 1.2 }}>{a.t}</div>
            <div style={{ fontSize: 11.5, color: "var(--e-ink-2)", marginTop: 3, fontStyle: "italic", fontFamily: "var(--va-serif)" }}>{a.s}</div>
          </div>
        ))}
      </div>
    </div>
  );

  // Detail — long-form editorial
  const Detail = () => (
    <div style={{ background: "var(--e-bg)", height: "100%", display: "flex", flexDirection: "column", overflow: "auto" }}>
      <div style={{ padding: "16px 22px 8px", borderBottom: "1px solid var(--e-hairline)" }}>
        <div style={{ fontSize: 9.5, letterSpacing: 1.6, color: "var(--e-ink-2)", textTransform: "uppercase", fontWeight: 600 }}>
          Sektion · Sverige · Telekom
        </div>
        <div style={{ fontFamily: "var(--va-serif)", fontSize: 36, lineHeight: 1.0, marginTop: 8, letterSpacing: -0.6, color: "var(--e-ink)" }}>
          Ericsson B
        </div>
        <div style={{ fontFamily: "var(--va-serif)", fontStyle: "italic", fontSize: 13, color: "var(--e-ink-2)", marginTop: 4 }}>
          Nasdaq Stockholm · Stora Bolag · ERIC B
        </div>
      </div>

      {/* Lede with price */}
      <div style={{ padding: "18px 22px 10px", display: "flex", alignItems: "baseline", gap: 14 }}>
        <span style={{ fontFamily: "var(--va-serif)", fontSize: 56, color: "var(--e-ink)", letterSpacing: -1.6, lineHeight: 1, fontVariantNumeric: "tabular-nums" }}>138,44</span>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: "var(--e-up)", fontVariantNumeric: "tabular-nums" }}>+2,15 sek</div>
          <div style={{ fontSize: 13, fontWeight: 600, color: "var(--e-up)", fontVariantNumeric: "tabular-nums", marginTop: 1 }}>+1,58 %</div>
        </div>
      </div>

      {/* Editorial chart */}
      <div style={{ padding: "0 22px 14px" }}>
        <svg width="100%" height="130" viewBox="0 0 320 130" style={{ display: "block" }}>
          <line x1="0" y1="65" x2="320" y2="65" stroke="var(--e-line)" strokeWidth="0.6" strokeDasharray="2,3"/>
          <path d="M0 90 L24 86 L48 92 L72 78 L96 84 L120 70 L144 76 L168 60 L192 64 L216 48 L240 52 L264 38 L288 42 L312 26 L320 24"
            fill="none" stroke="var(--e-up)" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
          <circle cx="320" cy="24" r="2.5" fill="var(--e-up)"/>
          <text x="0" y="10" fontSize="9" fill="var(--e-ink-3)" fontFamily="DM Serif Display" fontStyle="italic">152,60 · 52v-topp</text>
          <text x="0" y="125" fontSize="9" fill="var(--e-ink-3)" fontFamily="DM Serif Display" fontStyle="italic">98,22 · 52v-botten</text>
        </svg>
        <div style={{ display: "flex", gap: 0, marginTop: 8, borderTop: "1px solid var(--e-line)", paddingTop: 10 }}>
          {["1d","1v","1m","3m","1å","5å","Allt"].map((r, i) => (
            <div key={r} style={{
              fontSize: 11, padding: "3px 12px", letterSpacing: 0.4, fontWeight: 600,
              color: i === 2 ? "#FBF8F1" : "var(--e-ink-2)",
              background: i === 2 ? "var(--e-chip-on)" : "transparent",
              marginRight: 2,
            }}>{r}</div>
          ))}
        </div>
      </div>

      {/* Stat grid — 2 col, hairline */}
      <div style={{ borderTop: "1px solid var(--e-hairline)", padding: "14px 22px 4px" }}>
        <div style={{ fontSize: 9.5, letterSpacing: 1.6, color: "var(--e-ink-3)", textTransform: "uppercase", fontWeight: 600, marginBottom: 10 }}>
          Nyckeltal
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", rowGap: 14, columnGap: 18 }}>
          {[
            ["P/E", "14,22"], ["Direktavkastning", "2,64 %"],
            ["P/S", "1,83"], ["Volym", "8,42 mn"],
            ["52v-topp", "152,60"], ["52v-botten", "98,22"],
          ].map(([l, v]) => (
            <div key={l} style={{ borderBottom: "1px solid var(--e-line)", paddingBottom: 8, display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
              <span style={{ fontSize: 12, color: "var(--e-ink-2)", fontFamily: "var(--va-serif)", fontStyle: "italic" }}>{l}</span>
              <span style={{ fontFamily: "var(--va-serif)", fontSize: 18, color: "var(--e-ink)", letterSpacing: -0.2, fontVariantNumeric: "tabular-nums" }}>{v}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Alerts column */}
      <div style={{ padding: "14px 22px 18px" }}>
        <div style={{ fontSize: 9.5, letterSpacing: 1.6, color: "var(--e-ink-3)", textTransform: "uppercase", fontWeight: 600, marginBottom: 10 }}>
          Bevakningar · 3 aktiva
        </div>
        {[
          { t: "Pris över 145,00", s: "Engångs · utlöst 14:32", tr: true },
          { t: "Daglig rörelse > ±3 %", s: "Återkommande · +3,18 %", tr: true },
          { t: "Drawdown från topp > 20 %", s: "Bevakar från 152,60", tr: false },
        ].map((a, i) => (
          <div key={i} style={{
            padding: "10px 0", borderTop: i === 0 ? "1px solid var(--e-line)" : "none",
            borderBottom: "1px solid var(--e-line)",
            display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 10,
          }}>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontFamily: "var(--va-serif)", fontSize: 16, color: "var(--e-ink)", letterSpacing: -0.1 }}>{a.t}</div>
              <div style={{ fontSize: 11.5, color: "var(--e-ink-2)", marginTop: 2, fontStyle: "italic", fontFamily: "var(--va-serif)" }}>{a.s}</div>
            </div>
            <span style={{
              fontSize: 9.5, letterSpacing: 1.4, fontWeight: 600,
              color: a.tr ? "var(--e-accent)" : "var(--e-up)", whiteSpace: "nowrap",
            }}>{a.tr ? "UTLÖST" : "BEVAKAR"}</span>
          </div>
        ))}
      </div>
    </div>
  );

  return { Stocks, Pairs, Alerts, Detail };
})();

window.VariantC = C;
