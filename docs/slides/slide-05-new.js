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

  const scale = 10 / 1200;

  const laneAreaLeft = 25 * scale;
  const laneAreaTop = 58 * scale;
  const labelW = 85 * scale;
  const contentStartX = laneAreaLeft + labelW;
  const laneHeight = 76 * scale;
  const nodeW = 48 * scale;
  const nodeH = 18 * scale;

  const lanes = [
    { name: "需求系统", nodes: [{l:10,t:8},{l:65,t:28},{l:120,t:50},{l:175,t:18}], color: "E3F2FD" },
    { name: "开发环境", nodes: [{l:420,t:8},{l:475,t:50},{l:530,t:8},{l:585,t:50}], color: "E8F5E9" },
    { name: "代码仓库", nodes: [{l:140,t:8},{l:195,t:48},{l:250,t:48},{l:305,t:8}], color: "E3F2FD" },
    { name: "CI/CD", nodes: [{l:350,t:50},{l:405,t:8},{l:460,t:50},{l:515,t:8}], color: "E8F5E9" },
    { name: "部署环境", nodes: [{l:20,t:8},{l:75,t:52},{l:130,t:52},{l:185,t:8}], color: "E3F2FD" },
    { name: "运维管理", nodes: [{l:320,t:52},{l:375,t:8},{l:430,t:8},{l:485,t:52}], color: "E8F5E9" },
    { name: "监控告警", nodes: [{l:480,t:12},{l:535,t:48},{l:590,t:20},{l:645,t:40}], color: "E3F2FD" },
    { name: "通知协作", nodes: [{l:200,t:12},{l:255,t:32},{l:310,t:52},{l:365,t:16}], color: "E8F5E9" }
  ];

  const nodeNames = [
    ["提出需求","需求评审","需求分配","需求确认"],
    ["技术方案","代码编写","代码评审","方案确认"],
    ["提交代码","发起MR","代码审查","合并代码"],
    ["触发构建","执行测试","生成报告","镜像构建"],
    ["部署测试","预发布","发布生产","健康检查"],
    ["故障检测","问题定位","自动修复","运维操作"],
    ["指标采集","阈值检测","触发告警","告警确认"],
    ["推送通知","任务协作","进度同步","流程完成"]
  ];

  lanes.forEach((lane, li) => {
    const laneY = laneAreaTop + li * laneHeight;

    if (li > 0) {
      slide.addShape(pres.shapes.LINE, {
        x: laneAreaLeft, y: laneY, w: 780 * scale, h: 0,
        line: { color: "B0BEC5", width: 0.5, dashType: "dash" }
      });
    }

    slide.addShape(pres.shapes.RECTANGLE, {
      x: laneAreaLeft, y: laneY, w: labelW, h: laneHeight,
      fill: { color: lane.color },
      line: { color: "90A4AE", width: 0.5 }
    });

    slide.addShape(pres.shapes.RECTANGLE, {
      x: laneAreaLeft, y: laneY, w: 0.025, h: laneHeight,
      fill: { color: "1976D2" }
    });

    slide.addText(lane.name, {
      x: laneAreaLeft, y: laneY, w: labelW, h: laneHeight,
      fontSize: 10, fontFace: "Microsoft YaHei",
      color: "37474F", bold: true,
      align: "center", valign: "middle"
    });

    lane.nodes.forEach((nd, ni) => {
      const nx = contentStartX + nd.l * scale;
      const ny = laneY + nd.t * scale;

      slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
        x: nx, y: ny, w: nodeW, h: nodeH,
        fill: { color: "FFFFFF" },
        line: { color: "1976D2", width: 1 },
        rectRadius: 0.02
      });

      slide.addText(nodeNames[li][ni], {
        x: nx, y: ny, w: nodeW, h: nodeH,
        fontSize: 7, fontFace: "Microsoft YaHei",
        color: "37474F",
        align: "center", valign: "middle"
      });
    });
  });

  const connectors = [
    [["34,26","34,37","65,37"],["89,46","89,59","120,59"],["144,68","144,27","175,27"]],
    [["444,26","444,50","475,50"],["499,59","499,26","530,26"],["554,26","554,50","585,50"]],
    [["164,26","164,57","195,57"],["219,66","219,57"],["274,48","274,17","305,17"]],
    [["374,68","374,17","405,17"],["429,26","429,59","460,59"],["484,68","484,17","515,17"]],
    [["44,26","44,61","75,61"],["99,61","130,61"],["154,61","154,17","185,17"]],
    [["344,61","344,17","375,17"],["399,17","430,17"],["454,17","454,61","485,61"]],
    [["504,30","504,66","535,66"],["559,66","559,29","590,29"],["614,38","614,58","645,58"]],
    [["224,30","224,41","255,41"],["279,50","279,70","310,70"],["334,70","334,25","365,25"]]
  ];

  lanes.forEach((lane, li) => {
    const laneY = laneAreaTop + li * laneHeight;
    connectors[li].forEach((conn) => {
      const points = conn.map(p => {
        const [x,y] = p.split(",").map(Number);
        return { x: contentStartX + x * scale, y: laneY + y * scale };
      });

      const n0c = { x: points[0].x - nodeW/2, y: points[0].y + nodeH/2 };

      if (points.length === 3) {
        const p1 = points[1], p2 = points[2];
        slide.addShape(pres.shapes.LINE, {
          x: n0c.x, y: n0c.y, w: 0, h: p1.y - n0c.y,
          line: { color: "607D8B", width: 1 }
        });
        slide.addShape(pres.shapes.LINE, {
          x: points[0].x, y: p1.y, w: p2.x - points[0].x, h: 0,
          line: { color: "607D8B", width: 1, endArrowType: "triangle" }
        });
      } else if (points.length === 2) {
        slide.addShape(pres.shapes.LINE, {
          x: n0c.x, y: n0c.y, w: points[1].x - n0c.x, h: 0,
          line: { color: "607D8B", width: 1, endArrowType: "triangle" }
        });
      }
    });
  });

  const agentX = 6.8, agentY = 0.58, agentW = 2.9, agentH = 4.52;
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: agentX, y: agentY, w: agentW, h: agentH,
    fill: { color: "ECEFF1" },
    line: { color: "607D8B", width: 1, dashType: "dash" },
    rectRadius: 0.08
  });

  slide.addText("AgentCenter", {
    x: agentX, y: agentY, w: agentW, h: 0.32,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: theme.primary, bold: true,
    align: "center", valign: "middle"
  });

  const agentNodes = ["信息感知","智能分析","任务调度","执行反馈"];
  agentNodes.forEach((name, idx) => {
    const nodeY = agentY + 0.35 + idx * 0.6;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: agentX + 0.35, y: nodeY, w: 2.2, h: 0.38,
      fill: { color: theme.accent },
      line: { color: theme.primary, width: 1.5 },
      rectRadius: 0.06
    });
    slide.addText(name, {
      x: agentX + 0.35, y: nodeY, w: 2.2, h: 0.38,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: "FFFFFF", bold: true,
      align: "center", valign: "middle"
    });
    if (idx < agentNodes.length - 1) {
      slide.addShape(pres.shapes.LINE, {
        x: agentX + agentW/2, y: nodeY + 0.38, w: 0, h: 0.22,
        line: { color: "78909C", width: 1.5, endArrowType: "triangle" }
      });
    }
  });

  const lineLabels = ["调度指令","配置变更","任务分配","状态数据","告警信息","质量指标"];
  const lineYsHTML = [100, 170, 240, 350, 420, 490];
  const arrowLeft = [true, true, true, false, false, false];

  lineLabels.forEach((label, idx) => {
    const ly = (laneAreaTop + lineYsHTML[idx] * scale);
    slide.addShape(pres.shapes.LINE, {
      x: laneAreaLeft + 780 * scale, y: ly, w: 0.5, h: 0,
      line: { color: "78909C", width: 2, beginArrowType: arrowLeft[idx] ? "triangle" : undefined, endArrowType: !arrowLeft[idx] ? "triangle" : undefined }
    });
    slide.addText(label, {
      x: agentX - 0.15, y: ly - 0.14, w: 0.8, h: 0.18,
      fontSize: 8, fontFace: "Microsoft YaHei",
      color: "78909C", bold: true, align: "center"
    });
  });

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
  const theme = { primary: "0a0a0a", secondary: "525252", accent: "0070F3", light: "D4AF37", bg: "f5f5f5" };
  createSlide(pres, theme);
  pres.writeFile({ fileName: "slide-05-preview.pptx" });
}

module.exports = { createSlide, slideConfig };
