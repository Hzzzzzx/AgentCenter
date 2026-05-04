// slide-03.js - Core Positioning
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'content',
  index: 3,
  title: '核心定位'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: theme.bg };

  // Page title
  slide.addText("核心定位", {
    x: 0.5, y: 0.4, w: 9, h: 0.6,
    fontSize: 36, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  // Decorative line
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 0.95, w: 1.5, h: 0.04,
    fill: { color: theme.accent }
  });

  // Main quote box
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 1.3, w: 9, h: 1.2,
    fill: { color: theme.accent },
    rectRadius: 0.1
  });

  slide.addText("AgentCenter = AI 调度中心，不是另一个 DevOps 系统", {
    x: 0.7, y: 1.5, w: 8.6, h: 0.8,
    fontSize: 24, fontFace: "Microsoft YaHei",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  // Two column layout
  // Left column - What AgentCenter does
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 2.7, w: 4.3, h: 2.5,
    fill: { color: "FFFFFF" },
    rectRadius: 0.08
  });

  slide.addText("AgentCenter 负责", {
    x: 0.7, y: 2.85, w: 3.9, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: theme.accent, bold: true
  });

  const leftItems = [
    "感知外部系统状态",
    "触发外部系统流程",
    "提供上下文记忆",
    "编排多Agent协作",
    "主动推送通知"
  ];

  leftItems.forEach((item, idx) => {
    slide.addShape(pres.shapes.OVAL, {
      x: 0.8, y: 3.35 + idx * 0.38, w: 0.15, h: 0.15,
      fill: { color: theme.light }
    });
    slide.addText(item, {
      x: 1.1, y: 3.25 + idx * 0.38, w: 3.5, h: 0.35,
      fontSize: 14, fontFace: "Microsoft YaHei",
      color: theme.primary
    });
  });

  // Right column - What External systems do
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 5.2, y: 2.7, w: 4.3, h: 2.5,
    fill: { color: "FFFFFF" },
    rectRadius: 0.08
  });

  slide.addText("外部系统自带", {
    x: 5.4, y: 2.85, w: 3.9, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: theme.secondary, bold: true
  });

  const rightItems = [
    "合规机制",
    "审批流程",
    "审计日志",
    "权限控制",
    "具体执行"
  ];

  rightItems.forEach((item, idx) => {
    slide.addShape(pres.shapes.OVAL, {
      x: 5.5, y: 3.35 + idx * 0.38, w: 0.15, h: 0.15,
      fill: { color: theme.secondary }
    });
    slide.addText(item, {
      x: 5.8, y: 3.25 + idx * 0.38, w: 3.5, h: 0.35,
      fontSize: 14, fontFace: "Microsoft YaHei",
      color: theme.primary
    });
  });

  // Page number badge
  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.4, h: 0.4,
    fill: { color: theme.accent }
  });
  slide.addText("3", {
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
  pres.writeFile({ fileName: "slide-03-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
