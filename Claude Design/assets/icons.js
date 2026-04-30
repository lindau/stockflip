// StockFlip icon set — converted from app/src/main/res/drawable vector XMLs.
// Each icon is a 24x24 SVG path. Use <Icon name="foo" /> or rawPaths[name] for custom rendering.
window.SFIcons = {
  stock:            "M4,18h16v-2H6.41L11,11.41l3,3L20.59,8 19.17,6.59 14,11.76l-3,-3L4,15.76V18z",
  compare_arrows:   null, // special: drawn as 2 circles + line below
  notifications:    "M12,22a2,2 0 0,0 2,-2h-4a2,2 0 0,0 2,2zM18,16v-4.5c0,-2.76 -1.57,-5.26 -4.07,-6.32C13.61,4.49 12.84,4 12,4s-1.61,0.49 -1.93,1.18C7.57,6.24 6,8.74 6,11.5L6,16l-2,2v1h16v-1l-2,-2z",
  notifications_off:"M20,18.69L7.84,6.14 5.27,3.49 4,4.76l2.8,2.8v0.01c-0.52,0.99 -0.8,2.16 -0.8,3.42v5l-2,2v1h13.73l2,2L21,19.72l-1,-1.03zM12,22c1.11,0 2,-0.89 2,-2h-4c0,1.11 0.89,2 2,2zM18,14.68L18,11c0,-3.08 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68c-0.15,0.03 -0.29,0.08 -0.42,0.12 -0.1,0.03 -0.2,0.07 -0.3,0.11h-0.01c-0.01,0 -0.01,0 -0.02,0.01 -0.23,0.09 -0.46,0.2 -0.68,0.31 0,0 -0.01,0 -0.01,0.01L18,14.68z",
  add:              "M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z",
  fab_add:          "M19,14H14V19H10V14H5V10H10V5H14V10H19Z",
  arrow_back:       "M20,11H7.83L13.41,5.41L12,4L4,12L12,20L13.41,18.59L7.83,13H20V11Z",
  arrow_upward:     "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z",
  arrow_downward:   "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z",
  crypto:           "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z",
  delete:           "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
  search:           "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3 5.91,3 3,5.91 3,9.5 3,13.09 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79L20,21.49 21.49,20 15.5,14zM9.5,14C7.01,14 5,11.99 5,9.5 5,7.01 7.01,5 9.5,5 11.99,5 14,7.01 14,9.5 14,11.99 11.99,14 9.5,14z",
  paid:             "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12.88,7.76v1.12h2.12v1.12h-2.12v1.12h2.12v1.12h-2.12v3.36h-1.76v-3.36H9.12v-1.12h2.12v-1.12H9.12v-1.12h2.12V7.76h1.76z",
  expand_more:      "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z",
  expand_less:      "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z",
};

window.Icon = function Icon({ name, size = 20, color = "currentColor", style = {} }) {
  const p = window.SFIcons[name];
  if (name === "compare_arrows") {
    return React.createElement("svg", { width: size, height: size, viewBox: "0 0 24 24", fill: "none", stroke: color, strokeWidth: 2, strokeLinecap: "round", style },
      React.createElement("circle", { cx: 10, cy: 7, r: 3 }),
      React.createElement("circle", { cx: 14, cy: 17, r: 3 }),
      React.createElement("line", { x1: 11.3, y1: 9.6, x2: 12.7, y2: 14.4 })
    );
  }
  return React.createElement("svg", { width: size, height: size, viewBox: "0 0 24 24", fill: color, style },
    React.createElement("path", { d: p })
  );
};
