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
public class FixtureBetaProjectDataProvider implements ProjectDataProvider {

    @Override
    public String id() {
        return "fixture-beta";
    }

    @Override
    public String name() {
        return "测试源 B";
    }

    @Override
    public String description() {
        return "本地 fixture：企业内网项目池，覆盖不同项目、空间和迭代。";
    }

    @Override
    public ProjectDataSnapshotDto snapshot() {
        return new ProjectDataSnapshotDto(
                id(),
                contexts(),
                new ProjectContextOptionsDto(
                        List.of("CloudeReq 企业项目", "CloudeReq 安全项目"),
                        List.of("企业中台", "安全治理"),
                        List.of("Sprint 21", "Sprint 22", "长期治理")
                ),
                workItems(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<ProjectContextDto> contexts() {
        return List.of(
                new ProjectContextDto(
                        "ctx-beta-enterprise",
                        "企业中台",
                        "CloudeReq 企业项目",
                        "企业中台",
                        "Sprint 21",
                        true
                ),
                new ProjectContextDto(
                        "ctx-beta-security",
                        "安全治理",
                        "CloudeReq 安全项目",
                        "安全治理",
                        "长期治理",
                        false
                )
        );
    }

    private List<ProjectProviderWorkItemDto> workItems() {
        return List.of(
                item("B-FE201", WorkItemType.FE, "企业项目首页过滤", "企业中台 Sprint 21 下的 FE 数据，用来验证 provider 切换。", WorkItemStatus.TODO, Priority.HIGH, "企业中台", "企业中台", "Sprint 21"),
                item("B-US201", WorkItemType.US, "企业项目同步审计", "作为企业管理员，我可以看到本次同步来自哪个实现位。", WorkItemStatus.IN_PROGRESS, Priority.URGENT, "企业中台", "企业中台", "Sprint 21"),
                item("B-TASK201", WorkItemType.TASK, "企业内网字段映射", "把外部项目、空间、迭代映射为 AgentCenter 稳定上下文。", WorkItemStatus.BACKLOG, Priority.MEDIUM, "企业中台", "企业中台", "Sprint 21"),
                item("B-WORK201", WorkItemType.WORK, "企业发布窗口校验", "企业中台 Sprint 22 的运维工作，不应出现在 Sprint 21。", WorkItemStatus.TODO, Priority.MEDIUM, "企业中台", "企业中台", "Sprint 22"),
                item("B-BUG301", WorkItemType.BUG, "安全项目漏洞流转", "安全治理长期迭代里的缺陷数据。", WorkItemStatus.TODO, Priority.HIGH, "安全治理", "安全治理", "长期治理"),
                item("B-VULN301", WorkItemType.VULN, "权限绕过风险确认", "安全治理 provider 专属漏洞项。", WorkItemStatus.BACKLOG, Priority.URGENT, "安全治理", "安全治理", "长期治理")
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
