// StockFlip Android screens — data + components for the 4 mock views.

// ─── Shared primitives ─────────────────────────────────────────────────────
const Pill = ({ kind = "off", children }) => {
  const styles = {
    ok: { bg: "var(--np-primary-container)", fg: "var(--np-on-primary-container)" },
    tr: { bg: "var(--np-tertiary)", fg: "var(--np-on-tertiary)" },
    off:{ bg: "var(--np-surface-alt)", fg: "var(--np-text-tertiary)" },
    ghost:{ bg: "var(--np-chip)", fg: "var(--np-text-secondary)" },
  }[kind];
  return <span style={{
    fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 600,
    letterSpacing: 0.5, textTransform: "uppercase",
    padding: "4px 8px", borderRadius: 999, background: styles.bg, color: styles.fg,
    whiteSpace: "nowrap",
  }}>{children}</span>;
};

const Chip = ({ on, children }) => (
  <span style={{
    display: "inline-flex", alignItems: "center", gap: 4,
    padding: "7px 12px", borderRadius: 999, fontSize: 12, fontWeight: 500,
    fontFamily: "var(--np-font-sans)",
    background: on ? "var(--np-primary-container)" : "var(--np-chip)",
    color: on ? "var(--np-on-primary-container)" : "var(--np-text-secondary)",
    whiteSpace: "nowrap",
  }}>{children}</span>
);

const Num = ({ children, size = 15, color }) => (
  <span style={{
    fontFamily: "var(--np-font-mono)", fontWeight: 600, fontSize: size,
    letterSpacing: -0.15, fontVariantNumeric: "tabular-nums", color,
  }}>{children}</span>
);

// ─── OverviewSummaryCard ───────────────────────────────────────────────────
// New component: gradient header card with Nära/Utlösta/Aktiva metrics
const OverviewSummaryCard = ({ near, triggered, active }) => (
  <div style={{
    background: "linear-gradient(135deg, var(--np-primary-container), rgba(234,241,246,0.92), rgba(255,243,221,0.24))",
    border: "1px solid var(--np-card-border)", borderRadius: 10, padding: "14px 16px", marginBottom: 8,
  }}>
    <div style={{ fontFamily: "var(--np-font-sans)", fontSize: 14, fontWeight: 600, color: "var(--np-on-primary-container)", marginBottom: 10 }}>
      Marknadsläge
    </div>
    <div style={{
      background: "rgba(255,255,255,0.42)", border: "1px solid rgba(214,224,232,0.46)",
      borderRadius: 14, padding: "9px 12px", display: "flex", gap: 10, alignItems: "center",
    }}>
      {[["Nära", near], ["Utlösta", triggered], ["Aktiva", active]].map(([label, val], i, arr) => (
        <React.Fragment key={label}>
          <div style={{ flex: 1, display: "flex", gap: 6, alignItems: "baseline" }}>
            <span style={{ fontFamily: "var(--np-font-mono)", fontWeight: 600, fontSize: 15, letterSpacing: -0.15, fontVariantNumeric: "tabular-nums", color: "var(--np-text-primary)" }}>{val}</span>
            <span style={{ fontFamily: "var(--np-font-sans)", fontWeight: 500, fontSize: 11, letterSpacing: 0.6, color: "var(--np-text-secondary)" }}>{label}</span>
          </div>
          {i < arr.length - 1 && <div style={{ width: 1, height: 18, background: "rgba(214,224,232,0.72)" }}/>}
        </React.Fragment>
      ))}
    </div>
  </div>
);

// ─── Stocks list — now uses MultipleWatchesCard pattern ────────────────────
const STOCKS = [
  { sym: "ERIC-B.ST", flag: "🇸🇪", name: "Ericsson B", px: "138,44", d: "+2,15", dp: "+1,58 %", up: true,  cur: "SEK", watches: 3, triggered: 2 },
  { sym: "VOLV-B.ST", flag: "🇸🇪", name: "Volvo B",    px: "287,30", d: "−0,80", dp: "−0,28 %", up: false, cur: "SEK", watches: 1, triggered: 0 },
  { sym: "AAPL",      flag: "🇺🇸", name: "Apple Inc.", px: "187,23", d: "−0,91", dp: "−0,48 %", up: false, cur: "USD", watches: 2, triggered: 1 },
  { sym: "MSFT",      flag: "🇺🇸", name: "Microsoft",  px: "412,05", d: "+3,27", dp: "+0,80 %", up: true,  cur: "USD", watches: 1, triggered: 0 },
  { sym: "NVDA",      flag: "🇺🇸", name: "NVIDIA",     px: "932,14", d: "+18,40",dp: "+2,01 %", up: true,  cur: "USD", watches: 2, triggered: 0 },
  { sym: "BTC-USD",   flag: "🟧",  name: "Bitcoin",    px: "67.842", d: "−412",  dp: "−0,60 %", up: false, cur: "USD", watches: 1, triggered: 0 },
  { sym: "ETH-USD",   flag: "🟧",  name: "Ethereum",   px: "3.482,55",d: "+42,10",dp: "+1,22 %",up: true,  cur: "USD", watches: 1, triggered: 0 },
];

const StocksScreen = () => (
  <div style={{ padding: "8px 12px 12px", height: "100%", overflow: "auto" }}>
    {/* OverviewSummaryCard — new component */}
    <OverviewSummaryCard near={3} triggered={3} active={11} />
    <div style={{ display: "flex", gap: 6, padding: "0 0 10px", overflowX: "auto" }}>
      <Chip on>Sortering: Bokstavsordning</Chip>
      <Chip>Marknad: Alla</Chip>
    </div>
    {/* MultipleWatchesCard pattern */}
    <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {STOCKS.map((s, i) => {
        const first = i === 0, last = i === STOCKS.length - 1;
        const isTriggered = s.triggered > 0;
        return (
          <div key={s.sym}>
          <div style={{
            background: isTriggered ? "var(--np-tertiary-container)" : "var(--np-surface)",
            border: `1px solid ${isTriggered ? "var(--np-tertiary)" : "var(--np-card-border)"}`,
            borderTopLeftRadius: first ? 14 : 4, borderTopRightRadius: first ? 14 : 4,
            borderBottomLeftRadius: last ? 14 : 4, borderBottomRightRadius: last ? 14 : 4,
            padding: "12px 14px", display: "flex", alignItems: "center", gap: 10,
          }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{
                fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 500,
                letterSpacing: 0.6, textTransform: "uppercase",
                color: "var(--np-text-tertiary)",
              }}>{s.flag} {s.sym} · {s.cur}</div>
              <div style={{
                fontFamily: "var(--np-font-sans)", fontSize: 15, fontWeight: 600,
                color: "var(--np-text-primary)", marginTop: 3, letterSpacing: -0.1,
              }}>{s.name}</div>
            </div>
            <div style={{ textAlign: "right" }}>
              <Num>{s.px}</Num>
              <div style={{
                fontFamily: "var(--np-font-mono)", fontVariantNumeric: "tabular-nums",
                fontSize: 12, marginTop: 2,
                color: s.up ? "var(--np-price-up)" : "var(--np-price-down)",
              }}>{s.d} ({s.dp})</div>
            </div>
          </div>
          {/* MultipleWatchesCard footer — watch count + triggered badge */}
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 4 }}>
            <span style={{ fontFamily: "var(--np-font-sans)", fontSize: 11, color: "var(--np-text-secondary)" }}>
              {s.watches} bevakningar{s.triggered > 0 && <span style={{ color: "var(--np-tertiary)" }}> · {s.triggered} nådd</span>}
            </span>
          </div>
          </div>
        );
      })}
    </div>
  </div>
);

// ─── Pairs list ────────────────────────────────────────────────────────────
const PAIRS = [
  { a: "ERIC-B.ST", b: "NOKIA.HE",  name: "Ericsson ÷ Nokia",   sp: "3,472", d: "+0,041", dp: "+1,20 %", up: true },
  { a: "VOLV-B.ST",b: "SAND.ST",   name: "Volvo ÷ Sandvik",    sp: "1,482", d: "−0,018", dp: "−1,19 %", up: false },
  { a: "AAPL",      b: "MSFT",      name: "Apple ÷ Microsoft", sp: "0,4544", d: "−0,0082", dp: "−1,77 %", up: false },
  { a: "BTC-USD",   b: "ETH-USD",   name: "BTC ÷ ETH",         sp: "19,48",  d: "+0,33",  dp: "+1,72 %", up: true },
  { a: "HM-B.ST",   b: "INDU-C.ST", name: "H&M ÷ Industrivärden", sp: "0,664", d: "+0,008", dp: "+1,21 %", up: true },
];

const PairsScreen = () => (
  <div style={{ padding: "8px 12px 12px", height: "100%", overflow: "auto" }}>
    <div style={{ display: "flex", gap: 6, padding: "6px 0 10px" }}>
      <Chip on>Sortering: Bokstav</Chip>
      <Chip>Riktning: Alla</Chip>
    </div>
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {PAIRS.map((p) => (
        <div key={p.name} style={{
          background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
          borderRadius: 14, padding: 14,
        }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10 }}>
            <div style={{
              fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 500,
              letterSpacing: 0.6, textTransform: "uppercase",
              color: "var(--np-text-tertiary)",
            }}>{p.a} ÷ {p.b}</div>
            <Pill kind="ghost">Kvot</Pill>
          </div>
          <div style={{
            fontFamily: "var(--np-font-sans)", fontSize: 15, fontWeight: 600,
            color: "var(--np-text-primary)", marginTop: 4,
          }}>{p.name}</div>
          <div style={{
            marginTop: 10, display: "flex", alignItems: "baseline",
            justifyContent: "space-between",
          }}>
            <Num size={22}>{p.sp}</Num>
            <div style={{
              fontFamily: "var(--np-font-mono)", fontVariantNumeric: "tabular-nums",
              fontSize: 13, fontWeight: 600,
              color: p.up ? "var(--np-price-up)" : "var(--np-price-down)",
            }}>{p.d} ({p.dp})</div>
          </div>
        </div>
      ))}
    </div>
  </div>
);

// ─── Alerts list ───────────────────────────────────────────────────────────
const ALERTS = [
  { tk: "ERIC-B.ST", title: "Prismål 145,00 SEK", sub: "Engångslarm · över tröskel", state: "tr", when: "Utlöst 14:32" },
  { tk: "ERIC-B.ST", title: "Dagsrörelse ±3 %",   sub: "Återkommande · +3,18 % idag", state: "tr", when: "Utlöst 14:18" },
  { tk: "ERIC-B.ST", title: "52v-nedgång 20 %",   sub: "Engångslarm · bevakar från 152,60", state: "ok", when: "Aktiv" },
  { tk: "AAPL",      title: "P/E under 22",       sub: "Nyckeltal · uppdateras dagligen", state: "ok", when: "Aktiv" },
  { tk: "AAPL",      title: "Prisintervall 180–195", sub: "Återkommande · båda sidor", state: "ok", when: "Aktiv" },
  { tk: "BTC-USD",   title: "Prismål 70 000 USD", sub: "Engångslarm · över tröskel", state: "off", when: "Inaktiverad" },
  { tk: "VOLV-B.ST", title: "Direktavk. över 4 %", sub: "Nyckeltal · månadsvis", state: "ok", when: "Aktiv" },
];

const AlertsScreen = () => (
  <div style={{ padding: "8px 12px 12px", height: "100%", overflow: "auto" }}>
    <div style={{ display: "flex", gap: 6, padding: "6px 0 10px", overflowX: "auto" }}>
      <Chip on>Status: Alla</Chip>
      <Chip>Typ: Alla</Chip>
      <Chip>Ticker: Alla</Chip>
    </div>

    {/* Group — ERIC-B.ST */}
    <div style={{
      fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 600, letterSpacing: 0.7,
      textTransform: "uppercase", color: "var(--np-text-tertiary)",
      padding: "10px 4px 6px",
    }}>🇸🇪 ERIC-B.ST · Ericsson B</div>
    <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {ALERTS.slice(0, 3).map((a, i) => {
        const first = i === 0, last = i === 2;
        const triggered = a.state === "tr";
        return (
          <div key={a.title} style={{
            background: triggered ? "var(--np-tertiary-container)" : "var(--np-surface)",
            border: `1px solid ${triggered ? "var(--np-tertiary)" : "var(--np-card-border)"}`,
            borderTopLeftRadius: first ? 14 : 4, borderTopRightRadius: first ? 14 : 4,
            borderBottomLeftRadius: last ? 14 : 4, borderBottomRightRadius: last ? 14 : 4,
            padding: "12px 14px 12px 18px", position: "relative", overflow: "hidden",
          }}>
            <span style={{
              position: "absolute", left: 0, top: 0, bottom: 0, width: 5,
              background: triggered ? "var(--np-tertiary)" : "transparent",
            }}/>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontFamily: "var(--np-font-sans)", fontSize: 15, fontWeight: 600,
                  color: "var(--np-text-primary)", letterSpacing: -0.1,
                }}>{a.title}</div>
                <div style={{
                  fontFamily: "var(--np-font-mono)", fontVariantNumeric: "tabular-nums",
                  fontSize: 12, color: "var(--np-text-secondary)", marginTop: 4,
                }}>{a.sub}</div>
              </div>
              <Pill kind={a.state}>{a.when}</Pill>
            </div>
          </div>
        );
      })}
    </div>

    <div style={{
      fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 600, letterSpacing: 0.7,
      textTransform: "uppercase", color: "var(--np-text-tertiary)",
      padding: "14px 4px 6px",
    }}>🇺🇸 AAPL · Apple Inc.</div>
    <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {ALERTS.slice(3, 5).map((a, i) => {
        const first = i === 0, last = i === 1;
        return (
          <div key={a.title} style={{
            background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
            borderTopLeftRadius: first ? 14 : 4, borderTopRightRadius: first ? 14 : 4,
            borderBottomLeftRadius: last ? 14 : 4, borderBottomRightRadius: last ? 14 : 4,
            padding: "12px 14px 12px 18px", position: "relative",
          }}>
            <span style={{ position:"absolute",left:0,top:0,bottom:0,width:5,background:"transparent" }}/>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: "var(--np-font-sans)", fontSize: 15, fontWeight: 600, color: "var(--np-text-primary)" }}>{a.title}</div>
                <div style={{ fontFamily: "var(--np-font-mono)", fontSize: 12, color: "var(--np-text-secondary)", marginTop: 4, fontVariantNumeric: "tabular-nums" }}>{a.sub}</div>
              </div>
              <Pill kind={a.state}>{a.when}</Pill>
            </div>
          </div>
        );
      })}
    </div>

    <div style={{
      fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 600, letterSpacing: 0.7,
      textTransform: "uppercase", color: "var(--np-text-tertiary)",
      padding: "14px 4px 6px",
    }}>🟧 BTC-USD · Bitcoin</div>
    <div style={{
      background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
      borderRadius: 14, padding: "12px 14px 12px 18px", position: "relative", opacity: 0.7,
    }}>
      <span style={{ position:"absolute",left:0,top:0,bottom:0,width:5,background:"transparent" }}/>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: "var(--np-font-sans)", fontSize: 15, fontWeight: 600, color: "var(--np-text-primary)" }}>{ALERTS[5].title}</div>
          <div style={{ fontFamily: "var(--np-font-mono)", fontSize: 12, color: "var(--np-text-secondary)", marginTop: 4, fontVariantNumeric: "tabular-nums" }}>{ALERTS[5].sub}</div>
        </div>
        <Pill kind="off">{ALERTS[5].when}</Pill>
      </div>
    </div>
  </div>
);

// ─── Detail — Ericsson B ──────────────────────────────────────────────────
const DetailScreen = () => (
  <div style={{ padding: "8px 12px 24px", height: "100%", overflow: "auto" }}>
    {/* Hero card */}
    <div style={{
      background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
      borderRadius: 20, padding: 18,
    }}>
      <div style={{
        fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 500, letterSpacing: 0.6,
        textTransform: "uppercase", color: "var(--np-text-tertiary)",
      }}>🇸🇪 ERIC-B.ST · Stockholm · SEK</div>
      <div style={{
        fontFamily: "var(--np-font-sans)", fontSize: 22, fontWeight: 600,
        color: "var(--np-text-primary)", marginTop: 4, letterSpacing: -0.2,
      }}>Ericsson B</div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 10, marginTop: 14 }}>
        <Num size={34}>138,44</Num>
        <span style={{
          fontFamily: "var(--np-font-mono)", fontVariantNumeric: "tabular-nums",
          fontSize: 14, fontWeight: 600, color: "var(--np-price-up)",
        }}>+2,15 (+1,58 %)</span>
      </div>
      <div style={{
        fontFamily: "var(--np-font-mono)", fontSize: 11, fontVariantNumeric: "tabular-nums",
        color: "var(--np-text-tertiary)", marginTop: 6,
      }}>Uppdaterad 14:32 · börsen öppen</div>

      {/* Sparkline */}
      <svg width="100%" height="64" viewBox="0 0 320 64" style={{ marginTop: 14 }}>
        <defs>
          <linearGradient id="up" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--np-price-up)" stopOpacity="0.22"/>
            <stop offset="100%" stopColor="var(--np-price-up)" stopOpacity="0"/>
          </linearGradient>
        </defs>
        <path d="M0 44 L20 42 L40 48 L60 40 L80 44 L100 38 L120 42 L140 30 L160 34 L180 24 L200 28 L220 20 L240 22 L260 14 L280 18 L300 10 L320 12 L320 64 L0 64 Z" fill="url(#up)"/>
        <path d="M0 44 L20 42 L40 48 L60 40 L80 44 L100 38 L120 42 L140 30 L160 34 L180 24 L200 28 L220 20 L240 22 L260 14 L280 18 L300 10 L320 12" fill="none" stroke="var(--np-price-up)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
      <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
        {["1D","1V","1M","3M","1Å","5Å"].map((r,i) => <Chip key={r} on={i===2}>{r}</Chip>)}
      </div>
    </div>

    {/* Stat triplet */}
    <div style={{
      display: "grid", gridTemplateColumns: "repeat(3, 1fr)",
      background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
      borderRadius: 14, marginTop: 10, overflow: "hidden",
    }}>
      {[
        ["P/E", "14,22"],
        ["P/S", "1,83"],
        ["Direktavk.", "2,64 %"],
      ].map(([l, v], i) => (
        <div key={l} style={{
          padding: "14px 12px",
          borderRight: i < 2 ? "1px solid var(--np-outline-variant)" : "none",
        }}>
          <div style={{
            fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 500,
            letterSpacing: 0.5, textTransform: "uppercase",
            color: "var(--np-text-tertiary)",
          }}>{l}</div>
          <div style={{
            fontFamily: "var(--np-font-mono)", fontVariantNumeric: "tabular-nums",
            fontSize: 16, fontWeight: 600, letterSpacing: -0.2,
            color: "var(--np-text-primary)", marginTop: 6,
          }}>{v}</div>
        </div>
      ))}
    </div>

    {/* Range card */}
    <div style={{
      background: "var(--np-surface)", border: "1px solid var(--np-card-border)",
      borderRadius: 14, padding: 14, marginTop: 10,
    }}>
      <div style={{
        fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 500,
        letterSpacing: 0.5, textTransform: "uppercase",
        color: "var(--np-text-tertiary)",
      }}>52 veckors intervall</div>
      <div style={{ display: "flex", justifyContent: "space-between", marginTop: 8 }}>
        <Num size={13}>98,22</Num>
        <Num size={13}>152,60</Num>
      </div>
      <div style={{ position: "relative", height: 6, background: "var(--np-surface-alt)", borderRadius: 3, marginTop: 6 }}>
        <div style={{
          position: "absolute", left: "74%", top: -3, width: 2, height: 12,
          background: "var(--np-primary)", borderRadius: 1,
        }}/>
      </div>
      <div style={{
        fontFamily: "var(--np-font-mono)", fontSize: 11, fontVariantNumeric: "tabular-nums",
        color: "var(--np-text-tertiary)", marginTop: 6, textAlign: "center",
      }}>Nuvarande 138,44 · 73,7 % av intervallet</div>
    </div>

    {/* Active watches */}
    <div style={{
      fontFamily: "var(--np-font-sans)", fontSize: 10, fontWeight: 600, letterSpacing: 0.7,
      textTransform: "uppercase", color: "var(--np-text-tertiary)",
      padding: "14px 4px 6px",
    }}>Bevakningar · 3 aktiva</div>
    <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {[
        { t: "Prismål 145,00 SEK", s: "Engångslarm", state: "tr", when: "Utlöst 14:32" },
        { t: "Dagsrörelse ±3 %",   s: "Återkommande", state: "tr", when: "Utlöst 14:18" },
        { t: "52v-nedgång 20 %",   s: "Engångslarm", state: "ok", when: "Aktiv" },
      ].map((a, i, arr) => {
        const first = i === 0, last = i === arr.length - 1;
        const triggered = a.state === "tr";
        return (
          <div key={a.t} style={{
            background: triggered ? "var(--np-tertiary-container)" : "var(--np-surface)",
            border: `1px solid ${triggered ? "var(--np-tertiary)" : "var(--np-card-border)"}`,
            borderTopLeftRadius: first ? 14 : 4, borderTopRightRadius: first ? 14 : 4,
            borderBottomLeftRadius: last ? 14 : 4, borderBottomRightRadius: last ? 14 : 4,
            padding: "12px 14px 12px 18px", position: "relative",
          }}>
            <span style={{ position:"absolute",left:0,top:0,bottom:0,width:5,background: triggered ? "var(--np-tertiary)" : "transparent" }}/>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: "var(--np-font-sans)", fontSize: 14, fontWeight: 600, color: "var(--np-text-primary)" }}>{a.t}</div>
                <div style={{ fontFamily: "var(--np-font-mono)", fontSize: 11, color: "var(--np-text-secondary)", marginTop: 3 }}>{a.s}</div>
              </div>
              <Pill kind={a.state}>{a.when}</Pill>
            </div>
          </div>
        );
      })}
    </div>
  </div>
);

Object.assign(window, { StocksScreen, PairsScreen, AlertsScreen, DetailScreen });
