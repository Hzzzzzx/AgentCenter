# SDD 体系文档

> TianYuan 的 Specification-Driven Development（SDD）能力设计文档入口。

## 当前文档

| 文档 | 说明 |
|------|------|
| [Blueprint Graph 架构基调](blueprint-graph-architecture.md) | SDD Blueprint Graph 的定位、两阶段路线、v1.2 frontend contract graph、生成式图数据、可视化方向与设计决策基线 |
| [Blueprint Graph 工具边界与改进方向](blueprint-graph-tool-boundaries.md) | 当前 Blueprint Graph extractor 的支持范围、不可验证语义、target 拆分规则、Agent Core P0 暴露的问题与后续工具改进路线 |
| [SDD v1.1 图验证开发规范](sdd-v1-1-graph-verified-development.md) | SDD v1.1 Graph-Verified Development 规范：Blueprint Graph 作为 SDD gate 的受管辖证据层，适用性规则、计划模板、F1 增补、code-review-graph 分阶段吸收 |

## 定位

`docs/sdd/` 保存长期 SDD 设计与治理文档，回答 **WHAT / WHY**；`.sisyphus/` 保存执行生命周期、计划、任务卡、证据、生成图与报告，回答 **HOW / WHEN / EVIDENCE**。
