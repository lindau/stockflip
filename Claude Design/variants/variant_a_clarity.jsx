// VARIANT A — "Clarity"
// Apple Health / Fitness-inspired. Soft white surfaces, rounded numerics, airy cards, large
// breathing headers, subtle pastel tints. Hierarchy via type size + surface layering, not borders.

const A = (() => {
  const STOCKS = [
    { sym: "ERIC-B", flag: "🇸🇪", name: "Ericsson B",   px: "138,44", d: "+1,58 %", up: true,  spark: [42,44,40,46,43,48,45,50,52,54,58,56,62] },
    { sym: "VOLV-B", flag: "🇸🇪", name: "Volvo B",      px: "287,30", d: "−0,28 %", up: false, spark: [60,58,62,56,59,54,56,52,50,48,52,46,44] },
    { sym: "AAPL",   flag: "🇺🇸", name: "Apple",        px: "187,23", d: "−0,48 %", up: false, spark: [52,54,50,52,48,50,46,48,44,46,42,44,40] },
    { sym: "MSFT",   flag: "🇺🇸", name: "Microsoft",    px: "412,05", d: "+0,80 %", up: true,  spark: [40,42,44,46,44,48,46,50,52,56,54,58,62] },
    { sym: "NVDA",   flag: "🇺🇸", name: "NVIDIA",       px: "932,14", d: "+2,01 %", up: true,  spark: [30,34,38,42,46,42,48,52,56,54,60,64,68] },
    { sym: "BTC",    flag: "🟧", name: "Bitcoin",      px: "67.842", d: "−0,60 %", up: false, spark: [55,58,54,52,56,50,54,48,52,46,50,44,48] },
  ];

  const sparkPath = (pts, w=72, h=22) => {
    const min = Math.min(...pts), max = Math.max(...pts), r = max - min || 1;
    return pts.map((v,i) => `${(i/(pts.length-1))*w},${h - ((v-min)/r)*h}`).join(" L");
  };

  const Chip = ({ on, children }) => (
    <span style={{
      display: "inline-flex", alignItems: "center", gap: 4,
      padding: "7px 13px", borderRadius: 999, fontSize: 13, fontWeight: 500,
      fontFamily: "var(--va-sf)", letterSpacing: -0.1,
      background: on ? "var(--a-chip-on)" : "var(--a-chip)",
      color: on ? "var(--a-tint-ink)" : "var(--a-ink-2)",
      whiteSpace: "nowrap",
    }}>{children}</span>
  );

  const Header = ({ title, sub, action }) => (
    <div style={{ padding: "6px 20px 14px" }}>
      <div style={{
        fontFamily: "var(--va-sf)", fontSize: 34, fontWeight: 700, letterSpacing: -0.6,
        color: "var(--a-ink)", lineHeight: 1.1,
      }}>{title}</div>
      {sub && <div style={{ fontSize: 14, color: "var(--a-ink-2)", marginTop: 4, fontFamily: "var(--va-sf)" }}>{sub}</div>}
    </div>
  );

  // ── Stocks
  const Stocks = () => (
    <div style={{ background: "var(--a-bg)", height: "100%", overflow: "auto", padding: "8px 0 16px" }}>
      <Header title="Aktier" sub="Uppdaterad 09:30 · Stockholm öppen" />
      <div style={{ padding: "0 20px 12px", display: "flex", gap: 6, overflowX: "auto" }}>
        <Chip on>Mina</Chip><Chip>Sverige</Chip><Chip>USA</Chip><Chip>Krypto</Chip>
      </div>
      <div style={{ margin: "0 16px", background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", overflow: "hidden" }}>
        {STOCKS.map((s, i) => (
          <div key={s.sym} style={{
            display: "flex", alignItems: "center", gap: 14,
            padding: "14px 18px",
            borderBottom: i < STOCKS.length - 1 ? "1px solid var(--a-line)" : "none",
          }}>
            <div style={{
              width: 40, height: 40, borderRadius: 12,
              background: "var(--a-surface-2)", display: "grid", placeItems: "center",
              fontSize: 18,
            }}>{s.flag}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: "var(--a-ink)", letterSpacing: -0.2 }}>{s.name}</div>
              <div style={{ fontSize: 12, color: "var(--a-ink-3)", fontFamily: "var(--va-mono)", marginTop: 2, letterSpacing: -0.2 }}>{s.sym}</div>
            </div>
            <svg width="56" height="22" viewBox="0 0 72 22">
              <path d={"M" + sparkPath(s.spark)} fill="none"
                stroke={s.up ? "var(--a-up)" : "var(--a-dn)"} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <div style={{ textAlign: "right", minWidth: 72 }}>
              <div style={{ fontFamily: "var(--va-sf)", fontSize: 16, fontWeight: 600, fontVariantNumeric: "tabular-nums", letterSpacing: -0.3, color: "var(--a-ink)" }}>{s.px}</div>
              <div style={{
                display: "inline-block", marginTop: 2, padding: "2px 7px", borderRadius: 6,
                fontSize: 11, fontWeight: 600, fontFamily: "var(--va-sf)",
                background: s.up ? "rgba(48,179,158,0.12)" : "rgba(240,123,123,0.12)",
                color: s.up ? "var(--a-up)" : "var(--a-dn)",
              }}>{s.d}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  // ── Pairs
  const PAIRS = [
    { a: "ERIC-B", b: "NOKIA", name: "Ericsson ÷ Nokia", sp: "3,472", d: "+1,20 %", up: true, trend: [38,42,40,44,46,50,54] },
    { a: "AAPL",   b: "MSFT",  name: "Apple ÷ Microsoft", sp: "0,4544", d: "−1,77 %", up: false, trend: [54,52,48,46,44,42,40] },
    { a: "BTC",    b: "ETH",   name: "BTC ÷ ETH",        sp: "19,48",  d: "+1,72 %", up: true, trend: [42,44,42,48,50,52,56] },
    { a: "VOLV-B", b: "SAND",  name: "Volvo ÷ Sandvik",  sp: "1,482",  d: "−1,19 %", up: false, trend: [48,50,48,46,44,42,40] },
  ];
  const Pairs = () => (
    <div style={{ background: "var(--a-bg)", height: "100%", overflow: "auto", padding: "8px 0 16px" }}>
      <Header title="Par" sub="4 bevakade kvoter" />
      <div style={{ padding: "0 16px", display: "flex", flexDirection: "column", gap: 12 }}>
        {PAIRS.map(p => (
          <div key={p.name} style={{ background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", padding: 18 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: 0.6, textTransform: "uppercase", color: "var(--a-ink-3)", fontFamily: "var(--va-mono)" }}>{p.a} ÷ {p.b}</div>
                <div style={{ fontSize: 17, fontWeight: 600, color: "var(--a-ink)", marginTop: 4, letterSpacing: -0.3 }}>{p.name}</div>
              </div>
              <div style={{
                padding: "3px 8px", borderRadius: 6, fontSize: 11, fontWeight: 600, fontFamily: "var(--va-sf)",
                background: p.up ? "rgba(48,179,158,0.12)" : "rgba(240,123,123,0.12)",
                color: p.up ? "var(--a-up)" : "var(--a-dn)",
              }}>{p.d}</div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 14, marginTop: 14 }}>
              <div style={{ fontFamily: "var(--va-sf)", fontSize: 32, fontWeight: 700, letterSpacing: -0.8, fontVariantNumeric: "tabular-nums", color: "var(--a-ink)" }}>{p.sp}</div>
              <svg width="90" height="32" viewBox="0 0 90 32" style={{ flex: 1 }}>
                <path d={"M" + p.trend.map((v,i) => `${(i/6)*90},${32 - ((v-38)/20)*32}`).join(" L")}
                  fill="none" stroke={p.up ? "var(--a-up)" : "var(--a-dn)"} strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  // ── Alerts
  const ALERTS = [
    { tk: "ERIC-B · Ericsson B", flag: "🇸🇪", title: "Prismål 145,00 SEK", sub: "Engångslarm · över tröskel", state: "tr", when: "Utlöst 14:32" },
    { tk: "ERIC-B · Ericsson B", flag: "🇸🇪", title: "Dagsrörelse ±3 %",   sub: "Återkommande · +3,18 % idag", state: "tr", when: "Utlöst 14:18" },
    { tk: "AAPL · Apple",        flag: "🇺🇸", title: "P/E under 22",       sub: "Nyckeltal · dagligen",        state: "ok", when: "Aktiv" },
    { tk: "AAPL · Apple",        flag: "🇺🇸", title: "Prisintervall 180–195", sub: "Återkommande", state: "ok", when: "Aktiv" },
    { tk: "BTC · Bitcoin",       flag: "🟧", title: "Prismål 70 000 USD", sub: "Engångslarm",  state: "off", when: "Pausad" },
  ];
  const Alerts = () => (
    <div style={{ background: "var(--a-bg)", height: "100%", overflow: "auto", padding: "8px 0 16px" }}>
      <Header title="Bevakningar" sub="2 utlösta idag · 3 aktiva" />
      <div style={{ padding: "0 20px 10px", display: "flex", gap: 6, overflowX: "auto" }}>
        <Chip on>Alla</Chip><Chip>Aktiva</Chip><Chip>Utlösta</Chip><Chip>Pausade</Chip>
      </div>

      {/* Summary insight card — Apple Health style */}
      <div style={{ margin: "0 16px 12px", background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", padding: 18,
        display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{
          width: 46, height: 46, borderRadius: 23, background: "var(--a-accent-bg)",
          display: "grid", placeItems: "center",
        }}>
          <div style={{ fontSize: 22 }}>🔔</div>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, color: "var(--a-ink-2)" }}>Nya aviseringar</div>
          <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.5, color: "var(--a-ink)" }}>2 utlösta larm</div>
        </div>
        <div style={{ color: "var(--a-ink-3)" }}>
          <svg width="10" height="18" viewBox="0 0 10 18" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 1l7 8-7 8"/></svg>
        </div>
      </div>

      <div style={{ margin: "0 16px", background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", overflow: "hidden" }}>
        {ALERTS.map((a, i) => {
          const triggered = a.state === "tr";
          return (
            <div key={a.title} style={{
              display: "flex", gap: 14, padding: "14px 18px",
              borderBottom: i < ALERTS.length - 1 ? "1px solid var(--a-line)" : "none",
              background: triggered ? "rgba(244,183,64,0.08)" : "transparent",
            }}>
              <div style={{
                width: 10, alignSelf: "stretch", borderRadius: 5, marginTop: 2, marginBottom: 2,
                background: triggered ? "var(--a-accent)" : a.state === "ok" ? "var(--a-tint)" : "var(--a-line)",
              }}/>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: 0.4, color: "var(--a-ink-3)", fontFamily: "var(--va-mono)", textTransform: "uppercase" }}>{a.flag} {a.tk}</div>
                <div style={{ fontSize: 15, fontWeight: 600, color: "var(--a-ink)", marginTop: 3, letterSpacing: -0.2 }}>{a.title}</div>
                <div style={{ fontSize: 12, color: "var(--a-ink-2)", marginTop: 3 }}>{a.sub}</div>
              </div>
              <div style={{
                alignSelf: "flex-start",
                fontSize: 11, fontWeight: 600, padding: "3px 8px", borderRadius: 6,
                background: triggered ? "var(--a-accent)" : a.state === "ok" ? "var(--a-chip-on)" : "var(--a-line)",
                color: triggered ? "#0E1113" : a.state === "ok" ? "var(--a-tint-ink)" : "var(--a-ink-2)",
                fontFamily: "var(--va-sf)", whiteSpace: "nowrap",
              }}>{a.when}</div>
            </div>
          );
        })}
      </div>
    </div>
  );

  // ── Detail — Ericsson B
  const Detail = () => {
    const spark = [40,42,41,46,44,48,46,50,52,56,54,58,62,60,64];
    return (
      <div style={{ background: "var(--a-bg)", height: "100%", overflow: "auto", padding: "4px 0 16px" }}>
        <div style={{ padding: "6px 20px 10px", display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ color: "var(--a-tint)", fontSize: 17, fontWeight: 500, fontFamily: "var(--va-sf)" }}>‹ Aktier</div>
        </div>

        {/* Hero */}
        <div style={{ margin: "0 16px", background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", padding: 22 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ fontSize: 22 }}>🇸🇪</div>
            <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: 0.5, textTransform: "uppercase", color: "var(--a-ink-3)", fontFamily: "var(--va-mono)" }}>ERIC-B · STOCKHOLM · SEK</div>
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: "var(--a-ink)", marginTop: 6, letterSpacing: -0.5 }}>Ericsson B</div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginTop: 18 }}>
            <div style={{ fontFamily: "var(--va-sf)", fontSize: 46, fontWeight: 700, letterSpacing: -1.2, color: "var(--a-ink)", fontVariantNumeric: "tabular-nums" }}>138,44</div>
            <div style={{
              padding: "4px 10px", borderRadius: 8, fontSize: 13, fontWeight: 600,
              background: "rgba(48,179,158,0.14)", color: "var(--a-up)", fontFamily: "var(--va-sf)",
            }}>+2,15  +1,58 %</div>
          </div>

          {/* Chart */}
          <svg width="100%" height="96" viewBox="0 0 320 96" style={{ marginTop: 16 }}>
            <defs>
              <linearGradient id="aup" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--a-up)" stopOpacity="0.28"/>
                <stop offset="100%" stopColor="var(--a-up)" stopOpacity="0"/>
              </linearGradient>
            </defs>
            {(() => {
              const w=320, h=96, min=Math.min(...spark), max=Math.max(...spark), r=max-min||1;
              const pts = spark.map((v,i) => [(i/(spark.length-1))*w, h - ((v-min)/r)*h*0.85 - 4]);
              const path = pts.map((p,i)=>`${i?'L':'M'}${p[0]},${p[1]}`).join(" ");
              const area = path + ` L${w},${h} L0,${h} Z`;
              return <g>
                <path d={area} fill="url(#aup)"/>
                <path d={path} fill="none" stroke="var(--a-up)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
                <circle cx={pts[pts.length-1][0]} cy={pts[pts.length-1][1]} r="4" fill="var(--a-up)"/>
              </g>;
            })()}
          </svg>
          <div style={{ marginTop: 10, display: "flex", gap: 6 }}>
            {["1D","1V","1M","3M","1Å","Alla"].map((r,i)=>(
              <div key={r} style={{
                flex: 1, textAlign: "center", padding: "7px 0",
                fontSize: 12, fontWeight: 600, borderRadius: 8,
                background: i===2 ? "var(--a-ink)" : "transparent",
                color: i===2 ? "#FFF" : "var(--a-ink-2)",
              }}>{r}</div>
            ))}
          </div>
        </div>

        {/* Stat grid */}
        <div style={{ margin: "12px 16px", display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
          {[["P/E","14,22"],["P/S","1,83"],["Direktavk.","2,64 %"]].map(([l,v])=>(
            <div key={l} style={{ background: "var(--a-surface)", borderRadius: "var(--a-r-md)", padding: "14px 12px" }}>
              <div style={{ fontSize: 11, fontWeight: 500, letterSpacing: 0.3, textTransform: "uppercase", color: "var(--a-ink-3)", fontFamily: "var(--va-sf)" }}>{l}</div>
              <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: -0.4, color: "var(--a-ink)", marginTop: 6, fontFamily: "var(--va-sf)", fontVariantNumeric: "tabular-nums" }}>{v}</div>
            </div>
          ))}
        </div>

        {/* Range */}
        <div style={{ margin: "0 16px 12px", background: "var(--a-surface)", borderRadius: "var(--a-r-md)", padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 500, letterSpacing: 0.3, textTransform: "uppercase", color: "var(--a-ink-3)", fontFamily: "var(--va-sf)" }}>52 v intervall</div>
          <div style={{ position: "relative", height: 8, background: "var(--a-surface-2)", borderRadius: 4, marginTop: 12 }}>
            <div style={{
              position: "absolute", left: "74%", top: -3, width: 14, height: 14, borderRadius: 7,
              background: "var(--a-up)", border: "3px solid var(--a-surface)",
              boxShadow: "0 1px 3px rgba(0,0,0,0.15)",
            }}/>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10, fontFamily: "var(--va-sf)", fontVariantNumeric: "tabular-nums", fontSize: 12 }}>
            <span style={{ color: "var(--a-ink-3)" }}>98,22</span>
            <span style={{ color: "var(--a-ink)", fontWeight: 600 }}>138,44</span>
            <span style={{ color: "var(--a-ink-3)" }}>152,60</span>
          </div>
        </div>

        {/* Watches */}
        <div style={{ padding: "4px 20px 8px", fontSize: 15, fontWeight: 700, color: "var(--a-ink)", letterSpacing: -0.3 }}>Bevakningar</div>
        <div style={{ margin: "0 16px", background: "var(--a-surface)", borderRadius: "var(--a-r-lg)", overflow: "hidden" }}>
          {[
            { t:"Prismål 145,00 SEK", s:"Engångslarm", w:"Utlöst 14:32", tr:true },
            { t:"Dagsrörelse ±3 %",   s:"Återkommande", w:"Utlöst 14:18", tr:true },
            { t:"52v-nedgång 20 %",   s:"Engångslarm",  w:"Aktiv", tr:false },
          ].map((a,i,arr)=>(
            <div key={a.t} style={{
              display: "flex", gap: 14, padding: "14px 18px",
              borderBottom: i < arr.length-1 ? "1px solid var(--a-line)" : "none",
              background: a.tr ? "rgba(244,183,64,0.08)" : "transparent",
            }}>
              <div style={{ width: 10, alignSelf: "stretch", borderRadius: 5,
                background: a.tr ? "var(--a-accent)" : "var(--a-tint)" }}/>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--a-ink)", letterSpacing: -0.2 }}>{a.t}</div>
                <div style={{ fontSize: 12, color: "var(--a-ink-2)", marginTop: 2 }}>{a.s}</div>
              </div>
              <div style={{
                alignSelf: "flex-start", fontSize: 11, fontWeight: 600, padding: "3px 8px", borderRadius: 6,
                background: a.tr ? "var(--a-accent)" : "var(--a-chip-on)",
                color: a.tr ? "#0E1113" : "var(--a-tint-ink)", fontFamily: "var(--va-sf)",
              }}>{a.w}</div>
            </div>
          ))}
        </div>
      </div>
    );
  };

  return { Stocks, Pairs, Alerts, Detail };
})();

window.VariantA = A;
