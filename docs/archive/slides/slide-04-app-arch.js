const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'content',
  index: 4,
  title: '应用架构图'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: "FAFAFA" };

  slide.addText("AgentCenter 应用架构图", {
    x: 0.25, y: 0.06, w: 9.5, h: 0.28,
    fontSize: 20, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.25, y: 0.32, w: 1.0, h: 0.02,
    fill: { color: theme.accent }
  });

  const layerGap = 0.06;
  const labelW = 0.85;
  const contentStartX = 1.15;
  const contentEndX = 6.85;
  const contentW = contentEndX - contentStartX;
  const rowH = 0.28;
  const rowGap = 0.04;
  const itemW = contentW / 4;

  const layers = [
    {
      name: "用户交互层",
      bgColor: "FFF3E0",
      rows: [
        ["对话界面", "流式渲染", "气泡通知", "主动推送"],
        ["管理控制台", "状态总览", "仪表盘", "数据可视化"]
      ]
    },
    {
      name: "接入层",
      bgColor: "E8F5E9",
      rows: [
        ["API网关", "REST API", "WebSocket", "SSE"],
        ["JWT认证", "OAuth2", "限流熔断", "负载均衡"]
      ]
    },
    {
      name: "核心引擎层",
      bgColor: "FFE0B2",
      rows: [
        ["多Agent协作", "工作流引擎", "意图识别", "上下文管理"],
        ["编排调度", "任务分发", "结果聚合", "会话管理"]
      ]
    },
    {
      name: "工具能力层",
      bgColor: "E1BEE7",
      rows: [
        ["MCP协议", "Skill技能", "消息队列", "事件总线"],
        ["定时任务", "Webhook", "Agent配置", "主动建议"]
      ]
    },
    {
      name: "数据访问层",
      bgColor: "E3F2FD",
      rows: [
        ["知识库", "向量检索", "对话存储", "Redis缓存"],
        ["审计日志", "追溯查询", "数据备份", "安全加密"]
      ]
    }
  ];

  let currentY = 0.4;

  layers.forEach((layer, li) => {
    const totalH = 2 * rowH + rowGap + 0.16;

    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.25, y: currentY, w: 9.5, h: totalH,
      fill: { color: layer.bgColor },
      line: { color: "B0BEC5", width: 0.5, dashType: "dash" }
    });

    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.25, y: currentY, w: 0.05, h: totalH,
      fill: { color: "1976D2" }
    });

    slide.addText(layer.name, {
      x: 0.32, y: currentY, w: labelW, h: totalH,
      fontSize: 9, fontFace: "Microsoft YaHei",
      color: "37474F", bold: true,
      align: "center", valign: "middle"
    });

    layer.rows.forEach((row, ri) => {
      const rowY = currentY + 0.1 + ri * (rowH + rowGap);

      row.forEach((item, idx) => {
        const itemX = contentStartX + idx * itemW;

        slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
          x: itemX, y: rowY, w: itemW - 0.06, h: rowH,
          fill: { color: "FFFFFF" },
          line: { color: "1976D2", width: 0.8 },
          rectRadius: 0.02
        });

        slide.addText(item, {
          x: itemX, y: rowY, w: itemW - 0.06, h: rowH,
          fontSize: 7, fontFace: "Microsoft YaHei",
          color: "37474F",
          align: "center", valign: "middle"
        });
      });
    });

    currentY += totalH + layerGap;
  });

  const devopsX = 6.95;
  const devopsItems = [
    { name: "需求管理系统", color: "BBDEFB" },
    { name: "代码仓库", color: "B2EBF2" },
    { name: "CI/CD", color: "C8E6C9" },
    { name: "部署环境", color: "FFE0B2" },
    { name: "监控告警", color: "E1BEE7" },
    { name: "通知协作", color: "CFD8DC" }
  ];

  const devopsH = currentY - 0.4 - 0.06;
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: devopsX, y: 0.4, w: 2.8, h: devopsH,
    fill: { color: "ECEFF1" },
    line: { color: "607D8B", width: 1, dashType: "dash" },
    rectRadius: 0.08
  });

  slide.addText("DevOps 系统", {
    x: devopsX, y: 0.45, w: 2.8, h: 0.3,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true,
    align: "center", valign: "middle"
  });

  const devopsItemH = (devopsH - 0.5) / 6;
  devopsItems.forEach((item, idx) => {
    const iy = 0.8 + idx * devopsItemH;

    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: devopsX + 0.15, y: iy, w: 2.5, h: devopsItemH - 0.05,
      fill: { color: item.color },
      line: { color: "1976D2", width: 0.6 },
      rectRadius: 0.04
    });

    slide.addText(item.name, {
      x: devopsX + 0.15, y: iy, w: 2.5, h: devopsItemH - 0.05,
      fontSize: 9, fontFace: "Microsoft YaHei",
      color: "37474F", bold: true,
      align: "center", valign: "middle"
    });
  });

  const arrowX = 6.85;
  slide.addShape(pres.shapes.LINE, {
    x: arrowX, y: 0.8, w: 0, h: devopsH - 0.4,
    line: { color: "78909C", width: 1.5, dashType: "dash" }
  });
  slide.addText("集成", {
    x: arrowX - 0.3, y: 0.4 + devopsH / 2 - 0.15, w: 0.6, h: 0.3,
    fontSize: 8, fontFace: "Microsoft YaHei",
    color: "78909C",
    align: "center", valign: "middle"
  });

  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.35, h: 0.35,
    fill: { color: theme.accent }
  });
  slide.addText("4", {
    x: 9.3, y: 5.1, w: 0.35, h: 0.35,
    fontSize: 11, fontFace: "Arial",
    color: "FFFFFF", bold: true,
    align: "center", valign: "middle"
  });

  return slide;
}

if (require.main === module) {
  const pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9';
  const theme = { primary: "0a0a0a", secondary: "525252", accent: "0070F3", light: "D4AF37", bg: "f5f5f5" };
  createSlide(pres, theme);
  pres.writeFile({ fileName: "slide-04-app-arch.pptx" });
}

module.exports = { createSlide, slideConfig };
