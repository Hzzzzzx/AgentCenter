// slide-01.js - Cover Page
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'cover',
  index: 1,
  title: 'AgentCenter 应用架构图'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: theme.bg };

  // Left accent bar
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 0.15, h: 5.625,
    fill: { color: theme.accent }
  });

  // Top decorative line
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.5, w: 3, h: 0.02,
    fill: { color: theme.light }
  });

  // Main title
  slide.addText("AgentCenter", {
    x: 0.5, y: 1.7, w: 9, h: 1.2,
    fontSize: 60, fontFace: "Arial",
    color: theme.primary, bold: true
  });

  // Subtitle
  slide.addText("企业智能中枢 · 应用架构图", {
    x: 0.5, y: 2.9, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei",
    color: theme.secondary
  });

  // Bottom decorative line
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.8, w: 5, h: 0.02,
    fill: { color: theme.light }
  });

  // Date
  slide.addText("2026-04-06", {
    x: 0.5, y: 4.2, w: 3, h: 0.4,
    fontSize: 14, fontFace: "Arial",
    color: theme.secondary
  });

  // Right side decorative shape
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 7.5, y: 0, w: 2.5, h: 5.625,
    fill: { color: theme.accent, transparency: 10 }
  });

  // AI icon placeholder (circle with gradient effect)
  slide.addShape(pres.shapes.OVAL, {
    x: 8.0, y: 1.8, w: 1.5, h: 1.5,
    fill: { color: theme.accent }
  });
  slide.addText("AI", {
    x: 8.0, y: 1.8, w: 1.5, h: 1.5,
    fontSize: 36, fontFace: "Arial",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  return slide;
}

// Standalone preview
if (require.main === module) {
  const pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9';
  const theme = {
    primary: "0a0a0a",
    secondary: "525252",
    accent: "0070F3",
    light: "D4AF37",
    bg: "f5f5f5"
  };
  createSlide(pres, theme);
  pres.writeFile({ fileName: "slide-01-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
