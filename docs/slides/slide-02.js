// slide-02.js - Table of Contents
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'toc',
  index: 2,
  title: '目录'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: theme.bg };

  // Page title
  slide.addText("目录", {
    x: 0.5, y: 0.4, w: 9, h: 0.7,
    fontSize: 36, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  // Decorative line under title
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 1.5, h: 0.04,
    fill: { color: theme.accent }
  });

  // TOC items
  const tocItems = [
    { num: "01", title: "核心定位", desc: "AgentCenter 的角色与价值" },
    { num: "02", title: "整体架构", desc: "分层架构与核心组件" },
    { num: "03", title: "外部系统对接", desc: "与 DevOps 系统的交互模式" },
    { num: "04", title: "总结", desc: "关键要点与后续规划" }
  ];

  tocItems.forEach((item, idx) => {
    const yPos = 1.5 + idx * 0.95;

    // Number circle
    slide.addShape(pres.shapes.OVAL, {
      x: 0.5, y: yPos, w: 0.5, h: 0.5,
      fill: { color: theme.accent }
    });
    slide.addText(item.num, {
      x: 0.5, y: yPos, w: 0.5, h: 0.5,
      fontSize: 14, fontFace: "Arial",
      color: "FFFFFF", bold: true,
      align: "center", valign: "middle"
    });

    // Title
    slide.addText(item.title, {
      x: 1.2, y: yPos, w: 4, h: 0.35,
      fontSize: 20, fontFace: "Microsoft YaHei",
      color: theme.primary, bold: true
    });

    // Description
    slide.addText(item.desc, {
      x: 1.2, y: yPos + 0.35, w: 6, h: 0.3,
      fontSize: 14, fontFace: "Microsoft YaHei",
      color: theme.secondary
    });
  });

  // Right decorative shape
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 8.5, y: 1.5, w: 1.2, h: 3.5,
    fill: { color: theme.light, transparency: 30 }
  });

  // Page number badge
  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.4, h: 0.4,
    fill: { color: theme.accent }
  });
  slide.addText("2", {
    x: 9.3, y: 5.1, w: 0.4, h: 0.4,
    fontSize: 12, fontFace: "Arial",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  return slide;
}

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
  pres.writeFile({ fileName: "slide-02-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
