const pptxgen = require("pptxgenjs");

const slideConfig = {
  type: 'content',
  index: 5,
  title: '信息流泳道图'
};

function createSlide(pres, theme) {
  const slide = pres.addSlide();
  slide.background = { color: "FAFAFA" };

  slide.addText("AgentCenter 信息流泳道图", {
    x: 0.25, y: 0.12, w: 9.5, h: 0.35,
    fontSize: 24, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.25, y: 0.45, w: 1.0, h: 0.02,
    fill: { color: theme.accent }
  });

  const lanes = [
    {
      name: "需求系统",
      nodes: [
        {n: "提出需求", x: 0, y: 0.06},
        {n: "需求评审", x: 1, y: 0.26},
        {n: "需求分配", x: 2, y: 0.1},
        {n: "需求确认", x: 3, y: 0.3}
      ]
    },
    {
      name: "开发环境",
      nodes: [
        {n: "技术方案", x: 0, y: 0.26},
        {n: "代码编写", x: 1, y: 0.06},
        {n: "代码评审", x: 2, y: 0.3},
        {n: "方案确认", x: 3, y: 0.1}
      ]
    },
    {
      name: "代码仓库",
      nodes: [
        {n: "提交代码", x: 0, y: 0.1},
        {n: "发起MR", x: 1, y: 0.3},
        {n: "代码审查", x: 2, y: 0.06},
        {n: "合并代码", x: 3, y: 0.26}
      ]
    },
    {
      name: "CI/CD",
      nodes: [
        {n: "触发构建", x: 0, y: 0.3},
        {n: "执行测试", x: 1, y: 0.1},
        {n: "生成报告", x: 2, y: 0.26},
        {n: "镜像构建", x: 3, y: 0.06}
      ]
    },
    {
      name: "部署环境",
      nodes: [
        {n: "部署测试", x: 0, y: 0.06},
        {n: "预发布", x: 1, y: 0.3},
        {n: "发布生产", x: 2, y: 0.1},
        {n: "健康检查", x: 3, y: 0.26}
      ]
    },
    {
      name: "运维管理",
      nodes: [
        {n: "故障检测", x: 0, y: 0.26},
        {n: "问题定位", x: 1, y: 0.06},
        {n: "自动修复", x: 2, y: 0.3},
        {n: "运维操作", x: 3, y: 0.1}
      ]
    },
    {
      name: "监控告警",
      nodes: [
        {n: "指标采集", x: 0, y: 0.1},
        {n: "阈值检测", x: 1, y: 0.3},
        {n: "触发告警", x: 2, y: 0.06},
        {n: "告警确认", x: 3, y: 0.26}
      ]
    },
    {
      name: "通知协作",
      nodes: [
        {n: "推送通知", x: 0, y: 0.3},
        {n: "任务协作", x: 1, y: 0.1},
        {n: "进度同步", x: 2, y: 0.26},
        {n: "流程完成", x: 3, y: 0.06}
      ]
    }
  ];

  const laneHeight = 0.52;
  const startY = 0.58;
  const nameWidth = 1.1;
  const nodeWidth = 0.75;
  const nodeSpacing = 0.88;
  const contentStartX = 1.55;

  const laneBgColors = ["E3F2FD", "E8F5E9", "E3F2FD", "E8F5E9", "E3F2FD", "E8F5E9", "E3F2FD", "E8F5E9"];

  lanes.forEach((lane, laneIdx) => {
    const laneY = startY + laneIdx * laneHeight;

    // Draw horizontal dashed line between lanes (except first)
    if (laneIdx > 0) {
      slide.addShape(pres.shapes.LINE, {
        x: 0.3, y: laneY, w: 5.8, h: 0,
        line: { color: "B0BEC5", width: 1, dashType: "dash" }
      });
    }

    // Left label box with left border (竖线包围)
    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.3, y: laneY, w: nameWidth, h: laneHeight,
      fill: { color: laneBgColors[laneIdx] },
      line: { color: "90A4AE", width: 1 }
    });

    // Add left vertical line emphasis
    slide.addShape(pres.shapes.LINE, {
      x: 0.3, y: laneY, w: 0, h: laneHeight,
      line: { color: "1976D2", width: 2 }
    });

    slide.addText(lane.name, {
      x: 0.3, y: laneY, w: nameWidth, h: laneHeight,
      fontSize: 8, fontFace: "Microsoft YaHei",
      color: "37474F", bold: true,
      align: "center", valign: "middle"
    });

    lane.nodes.forEach((node) => {
      const nx = contentStartX + node.x * nodeSpacing;
      const ny = laneY + node.y;

      slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
        x: nx, y: ny, w: nodeWidth, h: 0.4,
        fill: { color: "FFFFFF" },
        line: { color: "1976D2", width: 1 },
        rectRadius: 0.06
      });

      slide.addText(node.n, {
        x: nx, y: ny, w: nodeWidth, h: 0.4,
        fontSize: 7, fontFace: "Microsoft YaHei",
        color: "37474F",
        align: "center", valign: "middle"
      });
    });
  });

  // Cross-lane flow arrows (simplified)
  const flowData = [
    { fromLane: 0, fromNode: 0, toLane: 0, toNode: 1, label: "", color: "4CAF50" },
    { fromLane: 0, toLane: 1, label: "", color: "2196F3" },
    { fromLane: 1, toLane: 2, label: "", color: "2196F3" },
    { fromLane: 2, toLane: 3, label: "", color: "FF9800" },
    { fromLane: 3, toLane: 4, label: "", color: "9C27B0" },
    { fromLane: 4, toLane: 5, label: "", color: "F44336" },
    { fromLane: 5, toLane: 6, label: "", color: "00BCD4" },
    { fromLane: 6, toLane: 7, label: "", color: "607D8B" },
  ];

  // Draw simplified flow arrows on the right side of content area
  const flowX = contentStartX + 4 * nodeSpacing + nodeWidth + 0.15;
  
  flowData.forEach((flow, idx) => {
    const fromY = startY + flow.fromLane * laneHeight + laneHeight / 2;
    const toY = startY + flow.toLane * laneHeight + laneHeight / 2;
    
    slide.addShape(pres.shapes.LINE, {
      x: flowX + idx * 0.08, y: fromY, w: 0, h: toY - fromY,
      line: { color: flow.color, width: 1.5, endArrowType: "triangle" }
    });
  });

  // Right side: AgentCenter panel
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 6.8, y: 0.58, w: 2.9, h: 4.58,
    fill: { color: "ECEFF1" },
    line: { color: "607D8B", width: 1, dashType: "dash" },
    rectRadius: 0.1
  });

  slide.addText("AgentCenter", {
    x: 6.8, y: 0.58, w: 2.9, h: 0.35,
    fontSize: 12, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true,
    align: "center", valign: "middle"
  });

  // AgentCenter nodes
  const agentNodes = [
    {n: "意图解析", y: 1.05},
    {n: "任务编排", y: 1.8},
    {n: "多Agent协作", y: 2.55},
    {n: "结果汇总", y: 3.3}
  ];

  agentNodes.forEach((node, idx) => {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: 7.15, y: node.y, w: 2.2, h: 0.5,
      fill: { color: theme.accent },
      line: { color: theme.primary, width: 2 },
      rectRadius: 0.1
    });

    slide.addText(node.n, {
      x: 7.15, y: node.y, w: 2.2, h: 0.5,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: "FFFFFF", bold: true,
      align: "center", valign: "middle"
    });

    // Connecting lines between agent nodes
    if (idx < agentNodes.length - 1) {
      slide.addShape(pres.shapes.LINE, {
        x: 8.25, y: node.y + 0.5, w: 0, h: 0.25,
        line: { color: "78909C", width: 1.5, endArrowType: "triangle" }
      });
    }
  });

  // Read/Write arrows connecting DevOps lanes to AgentCenter
  // Left side arrows pointing to AgentCenter (Read)
  slide.addShape(pres.shapes.LINE, {
    x: 5.6, y: 1.8, w: 1.2, h: 0,
    line: { color: "78909C", width: 1.5, endArrowType: "triangle" }
  });
  slide.addText("Read", {
    x: 5.7, y: 1.6, w: 0.5, h: 0.2,
    fontSize: 7, fontFace: "Microsoft YaHei",
    color: "78909C",
    align: "center"
  });

  // Right side arrows from AgentCenter (Write/Execute)
  slide.addShape(pres.shapes.LINE, {
    x: 6.8, y: 2.5, w: -0.3, h: 0,
    line: { color: "78909C", width: 1.5, endArrowType: "triangle" },
    flipH: true
  });
  slide.addText("Write", {
    x: 6.2, y: 2.3, w: 0.5, h: 0.2,
    fontSize: 7, fontFace: "Microsoft YaHei",
    color: "78909C",
    align: "center"
  });

  // Legend at bottom
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: 0.3, y: 5.0, w: 6.3, h: 0.55,
    fill: { color: "ECEFF1" },
    line: { color: "B0BEC5", width: 1 },
    rectRadius: 0.06
  });

  slide.addText("图例", {
    x: 0.4, y: 5.05, w: 0.5, h: 0.2,
    fontSize: 8, fontFace: "Microsoft YaHei",
    color: "37474F", bold: true
  });

  // Legend items
  const legendItems = [
    { name: "泳道分隔", dash: true },
    { name: "跨泳道流动", arrow: true, color: "2196F3" },
    { name: "Agent读写", arrow: true, color: "78909C" }
  ];

  legendItems.forEach((item, idx) => {
    const lx = 0.9 + idx * 1.8;
    
    if (item.dash) {
      slide.addShape(pres.shapes.LINE, {
        x: lx, y: 5.27, w: 0.4, h: 0,
        line: { color: "B0BEC5", width: 1, dashType: "dash" }
      });
    } else if (item.arrow) {
      slide.addShape(pres.shapes.LINE, {
        x: lx, y: 5.27, w: 0.4, h: 0,
        line: { color: item.color, width: 2, endArrowType: "triangle" }
      });
    }
    
    slide.addText(item.name, {
      x: lx + 0.5, y: 5.17, w: 1.2, h: 0.2,
      fontSize: 7, fontFace: "Microsoft YaHei",
      color: "37474F"
    });
  });

  // Page number
  slide.addShape(pres.shapes.OVAL, {
    x: 9.3, y: 5.1, w: 0.35, h: 0.35,
    fill: { color: theme.accent }
  });
  slide.addText("5", {
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
