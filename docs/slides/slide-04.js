// slide-04.js - Architecture Use Case Diagram
const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'content',
  index: 4,
  title: '架构用例图'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: "FAFAFA" };

  slide.addText("AgentCenter 应用架构用例图", {
    x: 0.25, y: 0.15, w: 9.5, h: 0.35,
    fontSize: 24, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.25, y: 0.48, w: 1.0, h: 0.02,
    fill: { color: theme.accent }
  });

  const actors = [
    { name: "开发者", x: 0.5 },
    { name: "运维", x: 1.9 },
    { name: "业务", x: 3.3 },
    { name: "PM", x: 4.7 },
    { name: "Agent", x: 6.1 }
  ];

  actors.forEach((actor, idx) => {
    const color = idx === 4 ? "4CAF50" : "2196F3";
    slide.addShape(pres.shapes.OVAL, {
      x: actor.x + 0.2, y: 0.58, w: 0.18, h: 0.18,
      fill: { color: color }
    });
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: actor.x + 0.16, y: 0.78, w: 0.26, h: 0.28,
      fill: { color: color },
      rectRadius: 0.06
    });
    slide.addText(actor.name, {
      x: actor.x, y: 1.08, w: 0.6, h: 0.25,
      fontSize: 9, fontFace: "Microsoft YaHei",
      color: theme.primary,
      align: "center"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.35, y: 1.4, w: 7.0, h: 3.85,
    fill: { color: "FFFFFF" },
    line: { color: theme.primary, width: 2.5 },
    rectRadius: 0.15
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.35, y: 1.28, w: 1.5, h: 0.3,
    fill: { color: theme.primary },
    rectRadius: 0.06
  });
  slide.addText("AgentCenter", {
    x: 0.35, y: 1.28, w: 1.5, h: 0.3,
    fontSize: 10, fontFace: "Arial",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.55, y: 1.7, w: 3.0, h: 0.85,
    fill: { color: "E3F2FD" },
    line: { color: "1976D2", width: 1.5 },
    rectRadius: 0.1
  });
  slide.addText("💬 用户交互层", {
    x: 0.65, y: 1.75, w: 2.8, h: 0.25,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "1976D2", bold: true
  });

  const uiComponents = [
    { name: "对话界面", desc: "自然语言" },
    { name: "气泡通知", desc: "主动推送" },
    { name: "管理控制台", desc: "状态总览" },
    { name: "仪表盘", desc: "数据可视" }
  ];
  uiComponents.forEach((comp, idx) => {
    const row = Math.floor(idx / 2);
    const col = idx % 2;
    const xPos = 0.65 + col * 1.4;
    const yPos = 2.03 + row * 0.24;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: yPos, w: 1.3, h: 0.22,
      fill: { color: "FFFFFF" },
      line: { color: "1976D2", width: 0.5 },
      rectRadius: 0.04
    });
    slide.addText(comp.name, {
      x: xPos + 0.03, y: yPos, w: 0.7, h: 0.22,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "1976D2", bold: true,
      valign: "middle"
    });
    slide.addText(comp.desc, {
      x: xPos + 0.7, y: yPos, w: 0.58, h: 0.22,
      fontSize: 6, fontFace: "Microsoft YaHei",
      color: "607D8B",
      valign: "middle"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 3.7, y: 1.7, w: 3.5, h: 0.85,
    fill: { color: "BBDEFB" },
    line: { color: "1976D2", width: 1.5 },
    rectRadius: 0.1
  });
  slide.addText("🚪 接入层", {
    x: 3.8, y: 1.75, w: 3.3, h: 0.25,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "1976D2", bold: true
  });

  const accessComponents = [
    { name: "API路由", desc: "REST" },
    { name: "鉴权认证", desc: "OAuth/JWT" },
    { name: "限流熔断", desc: "保护" },
    { name: "流式对话", desc: "SSE" }
  ];
  accessComponents.forEach((comp, idx) => {
    const row = Math.floor(idx / 2);
    const col = idx % 2;
    const xPos = 3.8 + col * 1.65;
    const yPos = 2.03 + row * 0.24;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: yPos, w: 1.55, h: 0.22,
      fill: { color: "FFFFFF" },
      line: { color: "1976D2", width: 0.5 },
      rectRadius: 0.04
    });
    slide.addText(comp.name, {
      x: xPos + 0.03, y: yPos, w: 0.8, h: 0.22,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "1976D2", bold: true,
      valign: "middle"
    });
    slide.addText(comp.desc, {
      x: xPos + 0.8, y: yPos, w: 0.72, h: 0.22,
      fontSize: 6, fontFace: "Microsoft YaHei",
      color: "607D8B",
      valign: "middle"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.55, y: 2.7, w: 6.65, h: 0.85,
    fill: { color: "FFF3E0" },
    line: { color: "FF9800", width: 1.5 },
    rectRadius: 0.1
  });
  slide.addText("⚙️ 核心引擎层", {
    x: 0.65, y: 2.75, w: 6.4, h: 0.25,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "FF9800", bold: true
  });

  const engineComponents = [
    { name: "多Agent协作", desc: "协作" },
    { name: "工作流引擎", desc: "编排" },
    { name: "Agent注册", desc: "发现" },
    { name: "意图识别", desc: "解析" },
    { name: "上下文管理", desc: "记忆" }
  ];
  engineComponents.forEach((comp, idx) => {
    const xPos = 0.65 + idx * 1.28;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: 3.03, w: 1.18, h: 0.45,
      fill: { color: "FFFFFF" },
      line: { color: "FF9800", width: 0.5 },
      rectRadius: 0.04
    });
    slide.addText(comp.name, {
      x: xPos, y: 3.05, w: 1.18, h: 0.22,
      fontSize: 8, fontFace: "Microsoft YaHei",
      color: "FF9800", bold: true,
      align: "center", valign: "middle"
    });
    slide.addText(comp.desc, {
      x: xPos, y: 3.25, w: 1.18, h: 0.18,
      fontSize: 6, fontFace: "Microsoft YaHei",
      color: "607D8B",
      align: "center", valign: "middle"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.55, y: 3.7, w: 3.15, h: 0.85,
    fill: { color: "F3E5F5" },
    line: { color: "9C27B0", width: 1.5 },
    rectRadius: 0.1
  });
  slide.addText("🔧 工具能力层", {
    x: 0.65, y: 3.75, w: 2.95, h: 0.25,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "9C27B0", bold: true
  });

  const toolComponents = [
    { name: "MCP", desc: "协议" },
    { name: "Skill", desc: "技能" },
    { name: "消息队列", desc: "异步" },
    { name: "事件总线", desc: "驱动" },
    { name: "定时任务", desc: "调度" },
    { name: "插件", desc: "扩展" }
  ];
  toolComponents.forEach((comp, idx) => {
    const row = Math.floor(idx / 3);
    const col = idx % 3;
    const xPos = 0.6 + col * 1.0;
    const yPos = 4.03 + row * 0.24;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: yPos, w: 0.95, h: 0.22,
      fill: { color: "FFFFFF" },
      line: { color: "9C27B0", width: 0.5 },
      rectRadius: 0.04
    });
    slide.addText(comp.name + " " + comp.desc, {
      x: xPos + 0.02, y: yPos, w: 0.91, h: 0.22,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "9C27B0",
      valign: "middle",
      align: "center"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 3.85, y: 3.7, w: 3.35, h: 0.85,
    fill: { color: "E8F5E9" },
    line: { color: "4CAF50", width: 1.5 },
    rectRadius: 0.1
  });
  slide.addText("📊 数据访问层", {
    x: 3.95, y: 3.75, w: 3.15, h: 0.25,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "4CAF50", bold: true
  });

  const dataComponents = [
    { name: "知识库", desc: "向量检索" },
    { name: "对话存储", desc: "历史" },
    { name: "Redis缓存", desc: "加速" },
    { name: "审计日志", desc: "追溯" }
  ];
  dataComponents.forEach((comp, idx) => {
    const row = Math.floor(idx / 2);
    const col = idx % 2;
    const xPos = 3.95 + col * 1.6;
    const yPos = 4.03 + row * 0.24;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: yPos, w: 1.5, h: 0.22,
      fill: { color: "FFFFFF" },
      line: { color: "4CAF50", width: 0.5 },
      rectRadius: 0.04
    });
    slide.addText(comp.name, {
      x: xPos + 0.03, y: yPos, w: 0.8, h: 0.22,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "4CAF50", bold: true,
      valign: "middle"
    });
    slide.addText(comp.desc, {
      x: xPos + 0.8, y: yPos, w: 0.67, h: 0.22,
      fontSize: 6, fontFace: "Microsoft YaHei",
      color: "607D8B",
      valign: "middle"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.55, y: 4.7, w: 1.4, h: 0.4,
    fill: { color: "FFFDE7" },
    line: { color: "FFC107", width: 1 },
    rectRadius: 0.08
  });
  slide.addText("🛡️ 安全合规", {
    x: 0.55, y: 4.7, w: 1.4, h: 0.4,
    fontSize: 9, fontFace: "Microsoft YaHei",
    color: "F57C00", bold: true,
    align: "center", valign: "middle"
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 2.1, y: 4.7, w: 5.1, h: 0.4,
    fill: { color: "00BCD4" },
    line: { color: "00838F", width: 1 },
    rectRadius: 0.08
  });
  slide.addText("🧠 记忆能力", {
    x: 2.15, y: 4.7, w: 1.1, h: 0.4,
    fontSize: 9, fontFace: "Microsoft YaHei",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  const memoryComponents = [
    { name: "用户", x: 3.35 },
    { name: "项目", x: 4.1 },
    { name: "系统", x: 4.85 },
    { name: "时间线", x: 5.6 }
  ];
  memoryComponents.forEach((comp) => {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: comp.x, y: 4.75, w: 0.7, h: 0.3,
      fill: { color: "FFFFFF" },
      line: { color: "00838F", width: 1 },
      rectRadius: 0.04
    });
    slide.addText(comp.name, {
      x: comp.x, y: 4.75, w: 0.7, h: 0.3,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "006064",
      align: "center", valign: "middle"
    });
  });

  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 7.5, y: 1.4, w: 2.1, h: 3.85,
    fill: { color: "ECEFF1" },
    line: { color: "607D8B", width: 1.5, dashType: "dash" },
    rectRadius: 0.1
  });

  slide.addText("DevOps 系统", {
    x: 7.55, y: 1.5, w: 2.0, h: 0.28,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: "607D8B", bold: true,
    align: "center"
  });

  const extItems = [
    { name: "项目跟踪系统", cat: "需求/任务" },
    { name: "代码仓库系统", cat: "Git仓库" },
    { name: "持续集成系统", cat: "构建/测试" },
    { name: "容器编排系统", cat: "K8s/部署" },
    { name: "监控告警系统", cat: "指标/日志" },
    { name: "通知协作系统", cat: "消息/邮件" }
  ];

  extItems.forEach((item, idx) => {
    const yPos = 1.85 + idx * 0.55;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: 7.6, y: yPos, w: 1.9, h: 0.48,
      fill: { color: "FFFFFF" },
      line: { color: "90A4AE", width: 1 },
      rectRadius: 0.06
    });
    slide.addText(item.name, {
      x: 7.6, y: yPos + 0.04, w: 1.9, h: 0.22,
      fontSize: 8, fontFace: "Microsoft YaHei",
      color: theme.primary, bold: true,
      align: "center"
    });
    slide.addText(item.cat, {
      x: 7.6, y: yPos + 0.26, w: 1.9, h: 0.18,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "607D8B",
      align: "center"
    });
  });

  slide.addText("图例", {
    x: 0.55, y: 5.35, w: 0.5, h: 0.2,
    fontSize: 8, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  const legends = [
    { color: "1976D2", desc: "交互" },
    { color: "FF9800", desc: "引擎" },
    { color: "9C27B0", desc: "工具" },
    { color: "4CAF50", desc: "数据" }
  ];

  legends.forEach((leg, idx) => {
    const xPos = 1.1 + idx * 0.85;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: xPos, y: 5.37, w: 0.18, h: 0.18,
      fill: { color: leg.color },
      rectRadius: 0.03
    });
    slide.addText(leg.desc, {
      x: xPos + 0.22, y: 5.35, w: 0.55, h: 0.2,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: theme.secondary
    });
  });

  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.3, w: 0.35, h: 0.35,
    fill: { color: theme.accent }
  });
  slide.addText("4", {
    x: 9.3, y: 5.3, w: 0.35, h: 0.35,
    fontSize: 11, fontFace: "Arial",
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
  pres.writeFile({ fileName: "slide-04-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
