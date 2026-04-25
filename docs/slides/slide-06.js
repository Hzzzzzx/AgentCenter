// slide-06.js - Summary
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'summary',
  index: 6,
  title: '总结'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: theme.bg };

  // Page title
  slide.addText("总结", {
    x: 0.5, y: 0.4, w: 9, h: 0.6,
    fontSize: 36, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  // Decorative line
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 0.95, w: 1.2, h: 0.04,
    fill: { color: theme.accent }
  });

  // Key takeaways section
  slide.addText("关键要点", {
    x: 0.5, y: 1.3, w: 9, h: 0.4,
    fontSize: 18, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  const takeaways = [
    { num: "1", text: "AgentCenter 是 AI 调度层，对接外部系统而非替代" },
    { num: "2", text: "外部 DevOps 系统自带合规/审批/审计机制" },
    { num: "3", text: "Agent 具备感知、触发、合规检查三大能力" },
    { num: "4", text: "记忆能力提供可靠的上下文输入" }
  ];

  takeaways.forEach((item, idx) => {
    const yPos = 1.8 + idx * 0.55;

    // Number circle
    slide.addShape(pres.shapes.OVAL, {
      x: 0.5, y: yPos, w: 0.4, h: 0.4,
      fill: { color: theme.accent }
    });
    slide.addText(item.num, {
      x: 0.5, y: yPos, w: 0.4, h: 0.4,
      fontSize: 14, fontFace: "Arial",
      color: "FFFFFF", bold: true,
      align: "center", valign: "middle"
    });

    // Text
    slide.addText(item.text, {
      x: 1.1, y: yPos, w: 8.4, h: 0.4,
      fontSize: 14, fontFace: "Microsoft YaHei",
      color: theme.primary,
      valign: "middle"
    });
  });

  // Next steps section
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 4.0, w: 9, h: 1.3,
    fill: { color: theme.accent, transparency: 10 },
    rectRadius: 0.1
  });

  slide.addText("后续规划", {
    x: 0.7, y: 4.15, w: 8.6, h: 0.35,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: theme.accent, bold: true
  });

  const nextSteps = [
    "确定详细技术方案",
    "画部署架构图",
    "画数据流图",
    "画 Agent 协作流程图"
  ];

  nextSteps.forEach((step, idx) => {
    const xPos = 0.7 + (idx % 2) * 4.3;
    const yPos = 4.55 + Math.floor(idx / 2) * 0.35;

    slide.addText("○ " + step, {
      x: xPos, y: yPos, w: 4, h: 0.3,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: theme.primary
    });
  });

  // Page number badge
  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.4, h: 0.4,
    fill: { color: theme.accent }
  });
  slide.addText("6", {
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
  pres.writeFile({ fileName: "slide-06-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
