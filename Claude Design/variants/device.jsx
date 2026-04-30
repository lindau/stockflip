// Shared device shell for variant canvases. Scoped via `variantClass` on the outer div
// so each variant's CSS custom properties cascade into the frame.
// Supports an optional bottom-nav and FAB override, and a `frameTone` (light | dark | warm) for bezel look.

const VariantDevice = ({
  variantClass,          // "va-clarity" | "va-ledger" | "va-exchange"
  frameTone = "light",   // bezel color
  statusTint,            // override status bar tint
  title,
  children,
  showFab = false,
  fabColor,
  activeTab = 0,
  navBg,
  navIconOn,
  navIconOff,
  navDivider,
  navLabels = ["Aktier", "Par", "Bevakningar"],
}) => {
  const bezel = {
    light: "#1A2232",
    dark:  "#05080D",
    warm:  "#2A241A",
  }[frameTone];

  return (
    <div className={variantClass} style={{
      width: 390, height: 844, borderRadius: 46, padding: 8,
      background: bezel, boxSizing: "border-box",
      boxShadow: "0 40px 70px rgba(12,18,28,0.32), 0 14px 24px rgba(12,18,28,0.18)",
    }}>
      <div style={{
        width: "100%", height: "100%", borderRadius: 38, overflow: "hidden",
        display: "flex", flexDirection: "column", position: "relative",
      }}>
        {/* Status bar */}
        <div style={{
          height: 40, display: "flex", alignItems: "center", justifyContent: "space-between",
          padding: "0 24px 0 30px", color: statusTint, fontSize: 14, fontWeight: 600,
          fontFamily: "var(--va-sf)", letterSpacing: 0.1,
        }}>
          <span>09:30</span>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <svg width="17" height="11" viewBox="0 0 16 11" fill={statusTint}><rect x="0" y="8" width="3" height="3" rx="0.5"/><rect x="4" y="6" width="3" height="5" rx="0.5"/><rect x="8" y="3" width="3" height="8" rx="0.5"/><rect x="12" y="0" width="3" height="11" rx="0.5"/></svg>
            <svg width="15" height="11" viewBox="0 0 15 11" fill={statusTint}><path d="M7.5 10.5L.8 3.8a9.5 9.5 0 0 1 13.4 0L7.5 10.5z"/></svg>
            <svg width="26" height="12" viewBox="0 0 24 11"><rect x="0.5" y="0.5" width="20" height="10" rx="2.5" fill="none" stroke={statusTint}/><rect x="21" y="3.5" width="2" height="4" rx="1" fill={statusTint}/><rect x="2" y="2" width="15" height="7" rx="1.2" fill={statusTint}/></svg>
          </div>
        </div>

        {/* Content */}
        <div style={{ flex: 1, overflow: "hidden", position: "relative" }}>
          {children}
          {showFab && (
            <div style={{
              position: "absolute", right: 18, bottom: 20,
              width: 56, height: 56, borderRadius: 18,
              background: fabColor, color: "#fff",
              display: "grid", placeItems: "center",
              boxShadow: "0 8px 16px rgba(12,18,28,0.22), 0 2px 4px rgba(12,18,28,0.08)",
            }}>
              <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor"><path d="M19,14H14V19H10V14H5V10H10V5H14V10H19Z"/></svg>
            </div>
          )}
        </div>

        {/* Bottom nav */}
        <div style={{
          background: navBg, borderTop: `1px solid ${navDivider}`,
          display: "flex", height: 64, paddingBottom: 0,
        }}>
          {navLabels.map((label, i) => {
            const on = i === activeTab;
            const icon = ["stock", "compare", "bell"][i];
            return (
              <div key={label} style={{
                flex: 1, display: "flex", flexDirection: "column",
                alignItems: "center", justifyContent: "center", gap: 4,
                color: on ? navIconOn : navIconOff,
              }}>
                {icon === "stock" && <svg width="24" height="24" viewBox="0 0 24 24" fill={on ? navIconOn : navIconOff}><path d="M4,18h16v-2H6.41L11,11.41l3,3L20.59,8 19.17,6.59 14,11.76l-3,-3L4,15.76V18z"/></svg>}
                {icon === "compare" && <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke={on ? navIconOn : navIconOff} strokeWidth="2" strokeLinecap="round"><circle cx="10" cy="7" r="3"/><circle cx="14" cy="17" r="3"/><line x1="11.3" y1="9.6" x2="12.7" y2="14.4"/></svg>}
                {icon === "bell" && <svg width="24" height="24" viewBox="0 0 24 24" fill={on ? navIconOn : navIconOff}><path d="M12,22a2,2 0 0,0 2,-2h-4a2,2 0 0,0 2,2zM18,16v-4.5c0,-2.76 -1.57,-5.26 -4.07,-6.32C13.61,4.49 12.84,4 12,4s-1.61,0.49 -1.93,1.18C7.57,6.24 6,8.74 6,11.5L6,16l-2,2v1h16v-1l-2,-2z"/></svg>}
                <span style={{ fontSize: 10.5, fontWeight: 600, letterSpacing: 0.2, fontFamily: "var(--va-sf)" }}>{label}</span>
              </div>
            );
          })}
        </div>
        <div style={{ height: 18, display: "grid", placeItems: "center", background: navBg }}>
          <div style={{ width: 132, height: 5, borderRadius: 3, background: statusTint, opacity: 0.35 }}/>
        </div>
      </div>
    </div>
  );
};

window.VariantDevice = VariantDevice;
