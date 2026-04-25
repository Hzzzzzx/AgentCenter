// slide-05.js - External System Interaction
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'content',
  index: 5,
  title: '外部系统对接'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: theme.bg };

  // Page title
  slide.addText("外部系统对接", {
    x: 0.5, y: 0.3, w: 9, h: 0.5,
    fontSize: 32, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  // Decorative line
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 0.75, w: 1.2, h: 0.03,
    fill: { color: theme.accent }
  });

  // Three interaction modes
  const modes = [
    {
      title: "感知模式",
      subtitle: "查询外部状态",
      icon: "?",
      color: "E3F2FD",
      borderColor: "1976D2",
      desc: "Agent 查询外部系统状态",
      example: "用户问：PROJ-123 现在在哪个阶段？\nAgent 查询 Jira → 返回状态信息"
    },
    {
      title: "触发模式",
      subtitle: "驱动外部流程",
      icon: "▶",
      color: "E8F5E9",
      borderColor: "4CAF50",
      desc: "Agent 驱动外部系统执行操作",
      example: "用户说：帮我把这个需求转成设计稿\nAgent 创建文档 → 发送通知"
    },
    {
      title: "合规检查模式",
      subtitle: "确保合规执行",
      icon: "✓",
      color: "FFF3E0",
      borderColor: "FF9800",
      desc: "Agent 确保操作符合外部规则",
      example: "用户说：帮我部署到生产\nAgent 检查变更窗口 + 审批状态"
    }
  ];

  modes.forEach((mode, idx) => {
    const xPos = 0.5 + idx * 3.1;

    // Card background
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: 1.0, w: 2.9, h: 4.2,
      fill: { color: "FFFFFF" },
      line: { color: mode.borderColor, width: 1.5 },
      rectRadius: 0.1
    });

    // Icon circle
    slide.addShape(pres.shapes.OVAL, {
      x: xPos + 1.05, y: 1.2, w: 0.8, h: 0.8,
      fill: { color: mode.color }
    });
    slide.addText(mode.icon, {
      x: xPos + 1.05, y: 1.2, w: 0.8, h: 0.8,
      fontSize: 24, fontFace: "Arial",
      color: mode.borderColor, bold: true,
      align: "center", valign: "middle"
    });

    // Title
    slide.addText(mode.title, {
      x: xPos + 0.15, y: 2.1, w: 2.6, h: 0.4,
      fontSize: 16, fontFace: "Microsoft YaHei",
      color: theme.primary, bold: true,
      align: "center"
    });

    // Subtitle
    slide.addText(mode.subtitle, {
      x: xPos + 0.15, y: 2.45, w: 2.6, h: 0.3,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: mode.borderColor,
      align: "center"
    });

    // Divider
    slide.addShape(pres.shapes.RECTANGLE, {
      x: xPos + 0.4, y: 2.85, w: 2.1, h: 0.02,
      fill: { color: mode.color }
    });

    // Description
    slide.addText(mode.desc, {
      x: xPos + 0.15, y: 2.95, w: 2.6, h: 0.5,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: theme.secondary,
      align: "center"
    });

    // Example box
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos + 0.15, y: 3.5, w: 2.6, h: 1.5,
      fill: { color: mode.color, transparency: 50 },
      rectRadius: 0.05
    });

    slide.addText(mode.example, {
      x: xPos + 0.25, y: 3.6, w: 2.4, h: 1.3,
      fontSize: 9, fontFace: "Microsoft YaHei",
      color: theme.primary,
      valign: "top"
    });
  });

  // Page number badge
  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.4, h: 0.4,
    fill: { color: theme.accent }
  });
  slide.addText("5", {
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
  pres.writeFile({ fileName: "slide-05-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
