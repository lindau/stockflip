// VARIANT B — "Ledger"
// Bloomberg/terminal density crossed with Apple's discipline. Dark graphite, all-mono,
// instrument-panel rows. Hierarchy by alignment and color, not size. Phosphor-teal up,
// soft red down. Numbers are the protagonist.

const B = (() => {
  const STOCKS = [
    { sym: "ERIC-B.ST", name: "Ericsson B",   px: "138.44",  bid: "138.42", ask: "138.46", d: "+2.15", dp: "+1.58", up: true,  vol: "8.42M", spark: [42,44,40,46,43,48,45,50,52,54,58,56,62,60,64,62,66] },
    { sym: "VOLV-B.ST", name: "Volvo B",      px: "287.30",  bid: "287.20", ask: "287.40", d: "−0.80", dp: "−0.28", up: false, vol: "3.10M", spark: [60,58,62,56,59,54,56,52,50,48,52,46,44,48,46,44,42] },
    { sym: "AAPL",      name: "Apple Inc",    px: "187.23",  bid: "187.20", ask: "187.25", d: "−0.91", dp: "−0.48", up: false, vol: "52.1M", spark: [52,54,50,52,48,50,46,48,44,46,42,44,40,42,40,38,36] },
    { sym: "MSFT",      name: "Microsoft",    px: "412.05",  bid: "412.00", ask: "412.10", d: "+3.27", dp: "+0.80", up: true,  vol: "21.8M", spark: [40,42,44,46,44,48,46,50,52,56,54,58,62,60,64,66,68] },
    { sym: "NVDA",      name: "NVIDIA",       px: "932.14",  bid: "931.98", ask: "932.30", d: "+18.40",dp: "+2.01", up: true,  vol: "44.2M", spark: [30,34,38,42,46,42,48,52,56,54,60,64,68,66,70,74,78] },
    { sym: "BTC-USD",   name: "Bitcoin",      px: "67842",   bid: "67830",  ask: "67854",  d: "−412",  dp: "−0.60", up: false, vol: "1.2B",  spark: [55,58,54,52,56,50,54,48,52,46,50,44,48,46,44,42,40] },
    { sym: "ETH-USD",   name: "Ethereum",     px: "3482.55", bid: "3481.20",ask: "3483.90",d: "+42.10",dp: "+1.22", up: true,  vol: "640M",  spark: [44,46,48,46,50,48,52,54,52,56,58,56,60,58,62,64,66] },
    { sym: "HM-B.ST",   name: "H&M B",        px: "164.85",  bid: "164.80", ask: "164.90", d: "+0.55", dp: "+0.33", up: true,  vol: "1.8M",  spark: [50,48,52,50,54,52,56,54,58,56,60,58,62,60,64,62,66] },
  ];

  const sparkPath = (pts, w=44, h=14) => {
    const min = Math.min(...pts), max = Math.max(...pts), r = max - min || 1;
    return pts.map((v,i) => `${(i/(pts.length-1))*w},${h - ((v-min)/r)*h}`).join(" L");
  };

  // Header bar — used inside the screen content (not the device chrome).
  const TopBar = ({ title, sub }) => (
    <div style={{
      padding: "14px 18px 12px",
      borderBottom: "1px solid var(--l-line)",
      display: "flex", alignItems: "center", justifyContent: "space-between",
      background: "var(--l-bg)",
    }}>
      <div>
        <div style={{ fontSize: 17, fontWeight: 600, letterSpacing: -0.3, color: "var(--l-ink)", fontFamily: "var(--va-mono)" }}>{title}</div>
        {sub && <div style={{ fontSize: 10, color: "var(--l-ink-2)", marginTop: 2, letterSpacing: 1, textTransform: "uppercase" }}>{sub}</div>}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <span style={{ width: 6, height: 6, borderRadius: 3, background: "var(--l-up)", boxShadow: "0 0 6px var(--l-up)" }}/>
        <span style={{ fontSize: 10, color: "var(--l-ink-2)", letterSpacing: 1.2, textTransform: "uppercase" }}>LIVE 14:32</span>
      </div>
    </div>
  );

  // Tab strip
  const Tabs = ({ items, active }) => (
    <div style={{
      display: "flex", padding: "10px 18px 0", gap: 4,
      borderBottom: "1px solid var(--l-line)", background: "var(--l-bg)",
    }}>
      {items.map((t, i) => (
        <div key={t} style={{
          padding: "8px 12px 10px", fontSize: 11, fontWeight: 600, letterSpacing: 1,
          textTransform: "uppercase", fontFamily: "var(--va-mono)",
          color: i === active ? "var(--l-tint)" : "var(--l-ink-2)",
          borderBottom: i === active ? "2px solid var(--l-tint)" : "2px solid transparent",
          marginBottom: -1,
        }}>{t}</div>
      ))}
    </div>
  );

  // Stocks — terminal table. 4 columns per row.
  const Stocks = () => (
    <div style={{ background: "var(--l-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <TopBar title="WATCHLIST" sub="8 SYMBOLS · MIXED MARKETS"/>
      <Tabs items={["ALL","SE","US","CRYPTO"]} active={0}/>
      {/* column header */}
      <div style={{
        display: "grid", gridTemplateColumns: "1.4fr 0.9fr 0.5fr 0.6fr",
        padding: "10px 18px 8px", fontSize: 9.5, color: "var(--l-ink-3)",
        letterSpacing: 1.2, textTransform: "uppercase", borderBottom: "1px solid var(--l-line)",
      }}>
        <span>SYM / NAME</span>
        <span style={{ textAlign: "right" }}>LAST</span>
        <span style={{ textAlign: "right" }}>CHG%</span>
        <span style={{ textAlign: "right" }}>TREND</span>
      </div>
      <div style={{ overflow: "auto", flex: 1 }}>
        {STOCKS.map((s, i) => (
          <div key={s.sym} style={{
            display: "grid", gridTemplateColumns: "1.4fr 0.9fr 0.5fr 0.6fr",
            alignItems: "center", padding: "12px 18px",
            borderBottom: "1px solid var(--l-line)",
            background: i % 2 === 0 ? "var(--l-bg)" : "var(--l-grid)",
          }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--l-ink)", letterSpacing: 0.2, fontFamily: "var(--va-mono)" }}>{s.sym}</div>
              <div style={{ fontSize: 10.5, color: "var(--l-ink-2)", marginTop: 1, fontFamily: "var(--va-sf)" }}>{s.name}</div>
            </div>
            <div style={{ textAlign: "right", fontSize: 13.5, fontWeight: 600, color: "var(--l-ink)", letterSpacing: -0.2, fontFamily: "var(--va-mono)", fontVariantNumeric: "tabular-nums" }}>{s.px}</div>
            <div style={{ textAlign: "right", fontSize: 12, fontWeight: 600, fontFamily: "var(--va-mono)", fontVariantNumeric: "tabular-nums",
              color: s.up ? "var(--l-up)" : "var(--l-dn)" }}>{s.dp}</div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <svg width="44" height="14" viewBox="0 0 44 14"><path d={"M" + sparkPath(s.spark)} fill="none" stroke={s.up ? "var(--l-up)" : "var(--l-dn)"} strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  // Pairs — table-style with bid/ask spread bar
  const PAIRS = [
    { a: "ERIC-B.ST", b: "NOKIA.HE",  sp: "3.4720", d: "+1.20", up: true,  zscore: "+1.42" },
    { a: "VOLV-B.ST", b: "SAND.ST",   sp: "1.4820", d: "−1.19", up: false, zscore: "−2.18" },
    { a: "AAPL",      b: "MSFT",      sp: "0.4544", d: "−1.77", up: false, zscore: "−0.94" },
    { a: "BTC-USD",   b: "ETH-USD",   sp: "19.480", d: "+1.72", up: true,  zscore: "+0.66" },
    { a: "HM-B.ST",   b: "INDU-C.ST", sp: "0.6640", d: "+1.21", up: true,  zscore: "+0.31" },
  ];

  const Pairs = () => (
    <div style={{ background: "var(--l-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <TopBar title="PAIRS" sub="5 SPREADS · MEAN-REVERSION"/>
      <Tabs items={["ALL","DIVERGING","CONVERGING"]} active={0}/>
      <div style={{
        display: "grid", gridTemplateColumns: "1.5fr 0.8fr 0.5fr 0.5fr",
        padding: "10px 18px 8px", fontSize: 9.5, color: "var(--l-ink-3)",
        letterSpacing: 1.2, textTransform: "uppercase", borderBottom: "1px solid var(--l-line)",
      }}>
        <span>A / B</span>
        <span style={{ textAlign: "right" }}>RATIO</span>
        <span style={{ textAlign: "right" }}>CHG%</span>
        <span style={{ textAlign: "right" }}>Z-SCORE</span>
      </div>
      <div style={{ overflow: "auto", flex: 1 }}>
        {PAIRS.map((p, i) => {
          const z = parseFloat(p.zscore);
          const zPct = Math.min(Math.abs(z) / 3, 1) * 50;
          return (
            <div key={p.a+p.b} style={{
              padding: "14px 18px", borderBottom: "1px solid var(--l-line)",
              background: i % 2 === 0 ? "var(--l-bg)" : "var(--l-grid)",
            }}>
              <div style={{
                display: "grid", gridTemplateColumns: "1.5fr 0.8fr 0.5fr 0.5fr",
                alignItems: "center",
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: "var(--l-ink)", fontFamily: "var(--va-mono)", letterSpacing: 0.2 }}>{p.a}</div>
                  <div style={{ fontSize: 11, color: "var(--l-ink-2)", fontFamily: "var(--va-mono)", letterSpacing: 0.2 }}>÷ {p.b}</div>
                </div>
                <div style={{ textAlign: "right", fontSize: 14, fontWeight: 600, color: "var(--l-ink)", letterSpacing: -0.2, fontFamily: "var(--va-mono)", fontVariantNumeric: "tabular-nums" }}>{p.sp}</div>
                <div style={{ textAlign: "right", fontSize: 12, fontWeight: 600, fontFamily: "var(--va-mono)", fontVariantNumeric: "tabular-nums",
                  color: p.up ? "var(--l-up)" : "var(--l-dn)" }}>{p.d}</div>
                <div style={{ textAlign: "right", fontSize: 12, fontFamily: "var(--va-mono)", color: "var(--l-ink-2)", fontVariantNumeric: "tabular-nums" }}>{p.zscore}</div>
              </div>
              {/* Z-score bar */}
              <div style={{ position: "relative", height: 3, marginTop: 10, background: "var(--l-line)", borderRadius: 1 }}>
                <div style={{ position: "absolute", left: "50%", top: -2, width: 1, height: 7, background: "var(--l-ink-3)" }}/>
                <div style={{
                  position: "absolute", top: 0, height: 3,
                  left: z >= 0 ? "50%" : `${50 - zPct}%`,
                  width: `${zPct}%`,
                  background: p.up ? "var(--l-up)" : "var(--l-dn)",
                  opacity: 0.7,
                }}/>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  // Alerts — log/console style
  const ALERTS = [
    { sym: "ERIC-B.ST", t: "PRICE > 145.00", s: "ONE-SHOT · ABOVE", state: "tr", when: "14:32:18" },
    { sym: "ERIC-B.ST", t: "DAILY MOVE > ±3%", s: "RECURRING · +3.18%", state: "tr", when: "14:18:44" },
    { sym: "ERIC-B.ST", t: "52W DRAWDOWN > 20%", s: "WATCHING FROM 152.60", state: "ok", when: "ARMED" },
    { sym: "AAPL",      t: "P/E < 22.0", s: "FUNDAMENTAL · DAILY", state: "ok", when: "ARMED" },
    { sym: "AAPL",      t: "PRICE IN [180, 195]", s: "RECURRING · BOTH SIDES", state: "ok", when: "ARMED" },
    { sym: "BTC-USD",   t: "PRICE > 70000", s: "ONE-SHOT · ABOVE", state: "off", when: "DISARMED" },
    { sym: "VOLV-B.ST", t: "YIELD > 4.0%", s: "FUNDAMENTAL · MONTHLY", state: "ok", when: "ARMED" },
    { sym: "NVDA",      t: "DAILY MOVE > ±5%", s: "RECURRING · ALERT", state: "ok", when: "ARMED" },
  ];

  const Alerts = () => (
    <div style={{ background: "var(--l-bg)", height: "100%", display: "flex", flexDirection: "column" }}>
      <TopBar title="ALERTS" sub="2 TRIGGERED · 5 ARMED · 1 DISARMED"/>
      <Tabs items={["ALL","TRIGGERED","ARMED","OFF"]} active={0}/>
      {/* summary row */}
      <div style={{
        display: "grid", gridTemplateColumns: "1fr 1fr 1fr",
        borderBottom: "1px solid var(--l-line)",
      }}>
        {[
          { k: "TRIGGERED", v: "2", c: "var(--l-accent)" },
          { k: "ARMED",     v: "5", c: "var(--l-up)" },
          { k: "DISARMED",  v: "1", c: "var(--l-ink-3)" },
        ].map((c, i) => (
          <div key={c.k} style={{
            padding: "12px 14px", borderRight: i < 2 ? "1px solid var(--l-line)" : "none",
          }}>
            <div style={{ fontSize: 9, color: "var(--l-ink-3)", letterSpacing: 1.4, fontWeight: 600 }}>{c.k}</div>
            <div style={{ fontSize: 22, fontWeight: 600, fontFamily: "var(--va-mono)", color: c.c, letterSpacing: -0.6, marginTop: 2 }}>{c.v}</div>
          </div>
        ))}
      </div>
      <div style={{ overflow: "auto", flex: 1 }}>
        {ALERTS.map((a, i) => {
          const triggered = a.state === "tr";
          const off = a.state === "off";
          return (
            <div key={i} style={{
              padding: "12px 18px", borderBottom: "1px solid var(--l-line)",
              background: triggered ? "rgba(244,194,95,0.06)" : (i % 2 === 0 ? "var(--l-bg)" : "var(--l-grid)"),
              opacity: off ? 0.55 : 1,
              borderLeft: triggered ? "2px solid var(--l-accent)" : "2px solid transparent",
              paddingLeft: triggered ? 16 : 18,
              display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10,
            }}>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ display: "flex", gap: 10, alignItems: "baseline" }}>
                  <span style={{ fontSize: 11, color: "var(--l-tint)", fontFamily: "var(--va-mono)", letterSpacing: 0.6 }}>{a.sym}</span>
                  <span style={{ fontSize: 9, color: "var(--l-ink-3)", letterSpacing: 1 }}>·</span>
                  <span style={{ fontSize: 9, color: "var(--l-ink-3)", letterSpacing: 1.2, fontFamily: "var(--va-mono)" }}>{a.when}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "var(--l-ink)", marginTop: 4, fontFamily: "var(--va-mono)", letterSpacing: -0.1 }}>{a.t}</div>
                <div style={{ fontSize: 10.5, color: "var(--l-ink-2)", marginTop: 2, letterSpacing: 0.4, fontFamily: "var(--va-mono)" }}>{a.s}</div>
              </div>
              <div style={{
                fontSize: 9, fontWeight: 700, padding: "4px 8px", letterSpacing: 1.2,
                fontFamily: "var(--va-mono)",
                background: triggered ? "var(--l-accent)" : (off ? "var(--l-line)" : "transparent"),
                color: triggered ? "#0B0D10" : (off ? "var(--l-ink-3)" : "var(--l-up)"),
                border: triggered || off ? "none" : "1px solid var(--l-up)",
              }}>{triggered ? "FIRED" : (off ? "OFF" : "ARMED")}</div>
            </div>
          );
        })}
      </div>
    </div>
  );

  // Detail
  const Detail = () => (
    <div style={{ background: "var(--l-bg)", height: "100%", display: "flex", flexDirection: "column", overflow: "auto" }}>
      <TopBar title="ERIC-B.ST" sub="ERICSSON B · NASDAQ STOCKHOLM · SEK"/>
      {/* Big price */}
      <div style={{ padding: "20px 18px 14px" }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
          <span style={{ fontSize: 46, fontWeight: 600, fontFamily: "var(--va-mono)", color: "var(--l-ink)", letterSpacing: -1.4, fontVariantNumeric: "tabular-nums" }}>138.44</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "var(--l-up)", fontFamily: "var(--va-mono)", letterSpacing: -0.1 }}>+2.15 / +1.58%</span>
        </div>
        <div style={{ fontSize: 10, color: "var(--l-ink-3)", marginTop: 6, letterSpacing: 1.2, fontFamily: "var(--va-mono)" }}>
          BID 138.42 · ASK 138.46 · SPREAD 0.04
        </div>
      </div>

      {/* Chart */}
      <div style={{ padding: "0 18px 12px" }}>
        <svg width="100%" height="120" viewBox="0 0 320 120" style={{ display: "block" }}>
          {/* Grid */}
          {[20,40,60,80,100].map(y => <line key={y} x1="0" y1={y} x2="320" y2={y} stroke="var(--l-line)" strokeWidth="0.5"/>)}
          {[60,120,180,240].map(x => <line key={x} x1={x} y1="0" x2={x} y2="120" stroke="var(--l-line)" strokeWidth="0.5" strokeDasharray="2,2"/>)}
          {/* Path */}
          <path d="M0 80 L20 76 L40 84 L60 70 L80 78 L100 64 L120 72 L140 56 L160 60 L180 44 L200 48 L220 36 L240 40 L260 26 L280 30 L300 16 L320 22" fill="none" stroke="var(--l-up)" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
          {/* Last marker */}
          <circle cx="320" cy="22" r="3" fill="var(--l-up)"/>
          {/* Axis labels */}
          <text x="2" y="10" fontSize="8" fill="var(--l-ink-3)" fontFamily="JetBrains Mono">140.00</text>
          <text x="2" y="118" fontSize="8" fill="var(--l-ink-3)" fontFamily="JetBrains Mono">128.00</text>
        </svg>
        <div style={{ display: "flex", gap: 4, marginTop: 6 }}>
          {["1D","1W","1M","3M","1Y","5Y","ALL"].map((r, i) => (
            <div key={r} style={{
              fontSize: 10, padding: "5px 10px", letterSpacing: 1, fontWeight: 600,
              color: i === 2 ? "var(--l-tint)" : "var(--l-ink-3)",
              borderBottom: i === 2 ? "1px solid var(--l-tint)" : "1px solid transparent",
              fontFamily: "var(--va-mono)",
            }}>{r}</div>
          ))}
        </div>
      </div>

      {/* Stat grid */}
      <div style={{
        display: "grid", gridTemplateColumns: "1fr 1fr 1fr",
        borderTop: "1px solid var(--l-line)", borderBottom: "1px solid var(--l-line)",
      }}>
        {[
          ["P/E", "14.22"],
          ["P/S", "1.83"],
          ["YIELD", "2.64%"],
          ["52W HIGH", "152.60"],
          ["52W LOW", "98.22"],
          ["VOLUME", "8.42M"],
        ].map(([l, v], i) => (
          <div key={l} style={{
            padding: "11px 14px",
            borderRight: (i % 3 < 2) ? "1px solid var(--l-line)" : "none",
            borderTop: i >= 3 ? "1px solid var(--l-line)" : "none",
          }}>
            <div style={{ fontSize: 9, color: "var(--l-ink-3)", letterSpacing: 1.2, fontWeight: 600, fontFamily: "var(--va-mono)" }}>{l}</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--l-ink)", marginTop: 4, fontFamily: "var(--va-mono)", letterSpacing: -0.2, fontVariantNumeric: "tabular-nums" }}>{v}</div>
          </div>
        ))}
      </div>

      {/* Active alerts log */}
      <div style={{ padding: "14px 18px 8px", fontSize: 9, color: "var(--l-ink-3)", letterSpacing: 1.4, fontWeight: 600, fontFamily: "var(--va-mono)" }}>
        ALERTS · 3 ACTIVE
      </div>
      {[
        { t: "PRICE > 145.00", s: "ONE-SHOT · ABOVE", state: "tr", when: "14:32:18" },
        { t: "DAILY MOVE > ±3%", s: "RECURRING · +3.18%", state: "tr", when: "14:18:44" },
        { t: "52W DRAWDOWN > 20%", s: "WATCHING FROM 152.60", state: "ok", when: "ARMED" },
      ].map((a, i) => {
        const triggered = a.state === "tr";
        return (
          <div key={i} style={{
            padding: "11px 18px", borderTop: "1px solid var(--l-line)",
            background: triggered ? "rgba(244,194,95,0.06)" : "var(--l-bg)",
            borderLeft: triggered ? "2px solid var(--l-accent)" : "2px solid transparent",
            paddingLeft: triggered ? 16 : 18,
            display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10,
          }}>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: "var(--l-ink)", fontFamily: "var(--va-mono)", letterSpacing: -0.1 }}>{a.t}</div>
              <div style={{ fontSize: 10, color: "var(--l-ink-2)", marginTop: 2, fontFamily: "var(--va-mono)", letterSpacing: 0.4 }}>{a.s} · {a.when}</div>
            </div>
            <div style={{
              fontSize: 9, fontWeight: 700, padding: "3px 7px", letterSpacing: 1.2,
              fontFamily: "var(--va-mono)",
              background: triggered ? "var(--l-accent)" : "transparent",
              color: triggered ? "#0B0D10" : "var(--l-up)",
              border: triggered ? "none" : "1px solid var(--l-up)",
            }}>{triggered ? "FIRED" : "ARMED"}</div>
          </div>
        );
      })}
    </div>
  );

  return { Stocks, Pairs, Alerts, Detail };
})();

window.VariantB = B;
