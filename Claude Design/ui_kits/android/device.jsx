// Device frame for StockFlip Android mocks.
// Sized 390×844 (close to Pixel 7 viewport). Status bar + app bar + content + bottom nav, all scoped via --np-* tokens.

const SFDevice = ({ title, children, showFab = false, activeTab = 0, theme = "light" }) => {
  const statusTint = theme === "dark" ? "#F2F5F7" : "#10202E";
  const navBg = theme === "dark" ? "#101A27" : "#FFFFFF";
  const navDivider = theme === "dark" ? "#263648" : "#D5DEE7";

  return (
    <div data-theme={theme} style={{
      width: 390, height: 844, borderRadius: 44, padding: 8,
      background: theme === "dark" ? "#05080D" : "#1A2232",
      boxShadow: "0 30px 60px rgba(16,32,46,0.25), 0 10px 20px rgba(16,32,46,0.12)",
      boxSizing: "border-box",
    }}>
      <div style={{
        width: "100%", height: "100%", borderRadius: 36, overflow: "hidden",
        background: "var(--np-background)", display: "flex", flexDirection: "column",
        position: "relative",
      }}>
        {/* Status bar */}
        <div style={{
          height: 36, display: "flex", alignItems: "center", justifyContent: "space-between",
          padding: "0 22px 0 28px", color: statusTint, fontSize: 13, fontWeight: 600,
          fontFamily: "var(--np-font-sans)", letterSpacing: 0.1,
          background: "var(--np-background)",
        }}>
          <span>09:30</span>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            {/* signal */}
            <svg width="16" height="11" viewBox="0 0 16 11" fill={statusTint}><rect x="0" y="8" width="3" height="3" rx="0.5"/><rect x="4" y="6" width="3" height="5" rx="0.5"/><rect x="8" y="3" width="3" height="8" rx="0.5"/><rect x="12" y="0" width="3" height="11" rx="0.5"/></svg>
            {/* wifi */}
            <svg width="15" height="11" viewBox="0 0 15 11" fill={statusTint}><path d="M7.5 10.5L.8 3.8a9.5 9.5 0 0 1 13.4 0L7.5 10.5z"/></svg>
            {/* battery */}
            <svg width="24" height="11" viewBox="0 0 24 11"><rect x="0.5" y="0.5" width="20" height="10" rx="2" fill="none" stroke={statusTint}/><rect x="21" y="3.5" width="2" height="4" rx="1" fill={statusTint}/><rect x="2" y="2" width="15" height="7" rx="1" fill={statusTint}/></svg>
          </div>
        </div>

        {/* App bar */}
        {title && (
          <div style={{
            height: 56, display: "flex", alignItems: "center", padding: "0 4px",
            background: "var(--np-background)",
          }}>
            <button style={{
              width: 48, height: 48, borderRadius: 24, border: 0, background: "transparent",
              color: "var(--np-text-primary)", display: "grid", placeItems: "center", cursor: "pointer",
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d={window.SFIcons.arrow_back}/></svg>
            </button>
            <span style={{
              fontFamily: "var(--np-font-sans)", fontSize: 17, fontWeight: 600,
              color: "var(--np-text-primary)", letterSpacing: -0.1,
            }}>{title}</span>
            <div style={{ flex: 1 }} />
            <button style={{
              width: 48, height: 48, borderRadius: 24, border: 0, background: "transparent",
              color: "var(--np-text-primary)", display: "grid", placeItems: "center", cursor: "pointer",
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d={window.SFIcons.search}/></svg>
            </button>
          </div>
        )}

        {/* Content */}
        <div style={{ flex: 1, overflow: "hidden", position: "relative" }}>
          {children}
          {showFab && (
            <div style={{
              position: "absolute", right: 16, bottom: 16,
              width: 56, height: 56, borderRadius: 16,
              background: "var(--np-primary)", color: "var(--np-on-primary)",
              display: "grid", placeItems: "center",
              boxShadow: "0 4px 10px rgba(16,32,46,0.18)",
            }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor"><path d={window.SFIcons.fab_add}/></svg>
            </div>
          )}
        </div>

        {/* Bottom nav */}
        <div style={{
          background: navBg, borderTop: `1px solid ${navDivider}`,
          display: "flex", height: 64,
        }}>
          {[
            { label: "Aktier", icon: "stock" },
            { label: "Par", icon: "compare_arrows" },
            { label: "Bevakningar", icon: "notifications" },
          ].map((t, i) => {
            const on = i === activeTab;
            return (
              <div key={t.label} style={{
                flex: 1, display: "flex", flexDirection: "column",
                alignItems: "center", justifyContent: "center", gap: 4,
                color: on ? "var(--np-primary)" : "var(--np-text-tertiary)",
              }}>
                <div style={{
                  width: 56, height: 28, borderRadius: 14,
                  background: on ? "var(--np-primary-container)" : "transparent",
                  display: "grid", placeItems: "center",
                }}>
                  {t.icon === "compare_arrows" ? (
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="10" cy="7" r="3"/><circle cx="14" cy="17" r="3"/><line x1="11.3" y1="9.6" x2="12.7" y2="14.4"/></svg>
                  ) : (
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d={window.SFIcons[t.icon]}/></svg>
                  )}
                </div>
                <span style={{
                  fontFamily: "var(--np-font-sans)", fontSize: 11, fontWeight: 500, letterSpacing: 0.3,
                }}>{t.label}</span>
              </div>
            );
          })}
        </div>

        {/* Nav handle */}
        <div style={{ height: 16, display: "grid", placeItems: "center", background: navBg }}>
          <div style={{ width: 108, height: 4, borderRadius: 2, background: theme === "dark" ? "#F2F5F7" : "#10202E", opacity: 0.35 }} />
        </div>
      </div>
    </div>
  );
};

window.SFDevice = SFDevice;
