package com.agentcenter.bridge.application.projectcontext;

import com.agentcenter.bridge.api.dto.ProjectContextDto;
import com.agentcenter.bridge.api.dto.ProjectContextOptionsDto;
import com.agentcenter.bridge.api.dto.ProjectDataSnapshotDto;
import com.agentcenter.bridge.api.dto.ProjectProviderWorkItemDto;
import com.agentcenter.bridge.domain.workitem.Priority;
import com.agentcenter.bridge.domain.workitem.WorkItemStatus;
import com.agentcenter.bridge.domain.workitem.WorkItemType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class FixtureAlphaProjectDataProvider implements ProjectDataProvider {

    @Override
    public String id() {
        return "fixture-alpha";
    }

    @Override
    public String name() {
        return "测试源 A";
    }

    @Override
    public String description() {
        return "本地 fixture：AgentCenter 和平台接入项目，覆盖 Sprint 14/15。";
    }

    @Override
    public ProjectDataSnapshotDto snapshot() {
        return new ProjectDataSnapshotDto(
                id(),
                contexts(),
                new ProjectContextOptionsDto(
                        List.of("CloudeReq 研发项目", "CloudeReq 交付空间"),
                        List.of("研发中台", "平台工程"),
                        List.of("Sprint 14", "Sprint 15")
                ),
                workItems(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<ProjectContextDto> contexts() {
        return List.of(
                new ProjectContextDto(
                        "ctx-alpha-agentcenter",
                        "AgentCenter",
                        "CloudeReq 研发项目",
                        "研发中台",
                        "Sprint 14",
                        true
                ),
                new ProjectContextDto(
                        "ctx-alpha-platform",
                        "平台接入",
                        "CloudeReq 交付空间",
                        "平台工程",
                        "Sprint 15",
                        false
                )
        );
    }

    private List<ProjectProviderWorkItemDto> workItems() {
        return List.of(
                item("A-FE001", WorkItemType.FE, "项目上下文标题栏串联", "标题栏切换当前项目后刷新首页与看板数据。", WorkItemStatus.TODO, Priority.HIGH, "AgentCenter", "研发中台", "Sprint 14"),
                item("A-FE002", WorkItemType.FE, "项目管理配置持久化", "把项目配置从前端本地状态迁移到 Bridge API。", WorkItemStatus.IN_PROGRESS, Priority.URGENT, "AgentCenter", "研发中台", "Sprint 14"),
                item("A-US001", WorkItemType.US, "同步源切换验证", "作为平台负责人，我可以切换测试同步源并看到不同迭代数据。", WorkItemStatus.TODO, Priority.HIGH, "AgentCenter", "研发中台", "Sprint 14"),
                item("A-TASK001", WorkItemType.TASK, "补齐上下文过滤测试", "验证 work-items overview 和列表使用同一上下文过滤条件。", WorkItemStatus.BACKLOG, Priority.MEDIUM, "AgentCenter", "研发中台", "Sprint 14"),
                item("A-WORK001", WorkItemType.WORK, "平台工程同步演练", "验证另一个项目空间的工作项不会混入 AgentCenter。", WorkItemStatus.TODO, Priority.MEDIUM, "平台接入", "平台工程", "Sprint 15"),
                item("A-FE101", WorkItemType.FE, "平台接入运行资源页", "平台接入项目专属的 Runtime Skill/MCP 管理入口。", WorkItemStatus.BACKLOG, Priority.LOW, "平台接入", "平台工程", "Sprint 15")
        );
    }

    private ProjectProviderWorkItemDto item(String code,
                                            WorkItemType type,
                                            String title,
                                            String description,
                                            WorkItemStatus status,
                                            Priority priority,
                                            String project,
                                            String space,
                                            String iteration) {
        return new ProjectProviderWorkItemDto(
                code,
                type,
                title,
                description,
                status,
                priority,
                project,
                space,
                iteration,
                "01DEFAULTUSER00000000000000001"
        );
    }
}
