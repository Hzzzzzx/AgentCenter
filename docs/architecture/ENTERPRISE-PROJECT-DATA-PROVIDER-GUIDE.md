# Enterprise Project Data Provider Guide

> Purpose: guide an enterprise-internal AI or engineer to implement AgentCenter project, space, iteration, and work item synchronization without changing Web code or AgentCenter core schema.

## Integration Boundary

Enterprise code should implement one Spring bean:

```java
@Component
public class EnterpriseCloudeReqProjectDataProvider implements ProjectDataProvider {
    @Override
    public String id() {
        return "enterprise-cloudereq";
    }

    @Override
    public String name() {
        return "企业 CloudeReq";
    }

    @Override
    public String description() {
        return "企业内部项目、空间、迭代和事项同步。";
    }

    @Override
    public ProjectDataSnapshotDto snapshot() {
        // Call enterprise APIs here and map them into ProjectDataSnapshotDto.
    }
}
```

AgentCenter discovers the bean through `ProjectDataProviderRegistry`. Runtime Settings can then switch the global provider to `enterprise-cloudereq`.

## Required Enterprise API Capabilities

The provider implementation can call any internal API shape, but it must be able to obtain these logical datasets:

| Capability | Required fields |
|------------|-----------------|
| Projects | Stable project id, display name, optional CloudeReq project id/name |
| Spaces | Stable space id, display name, owning project id |
| Iterations | Stable iteration id, display name, owning project/space id, status, start/end time if available |
| Work items | Stable work item id, display code, type, title, description, status, priority, project id, space id, iteration id, assignee |

Recommended internal endpoints if the enterprise side can expose REST:

| Method | Example path | Purpose |
|--------|--------------|---------|
| `GET` | `/projects` | List projects visible to the integration identity |
| `GET` | `/projects/{projectId}/spaces` | List spaces under a project |
| `GET` | `/projects/{projectId}/spaces/{spaceId}/iterations` | List iterations under a space |
| `GET` | `/work-items?projectId=&spaceId=&iterationId=&updatedAfter=` | List or incrementally sync FE/US/TASK/WORK/BUG/VULN |

The actual enterprise API can differ; only the provider-to-AgentCenter DTO contract must stay stable.

## DTO Mapping Contract

Map enterprise project selections into `ProjectContextDto`:

| AgentCenter field | Enterprise source |
|-------------------|-------------------|
| `id` | Provider-local context id, for example `ctx-{projectId}-{spaceId}-{iterationId}` |
| `externalProjectId` | Enterprise stable project id |
| `project` | Display project name shown in the title bar |
| `externalCloudeReqProjectId` | CloudeReq project id if different from project id |
| `cloudeReqProject` | CloudeReq project display name |
| `externalSpaceId` | Enterprise stable space id |
| `space` | Space display name |
| `externalIterationId` | Enterprise stable iteration id |
| `iteration` | Iteration display name |
| `iterationStatus` | Optional iteration status |
| `iterationStartAt` / `iterationEndAt` | Optional ISO-8601 timestamps or date strings |
| `active` | Provider default active context |
| `extraJson` | Provider-specific metadata |

Map enterprise work items into `ProjectProviderWorkItemDto`:

| AgentCenter field | Enterprise source |
|-------------------|-------------------|
| `externalId` | Enterprise stable work item id. Required for robust upsert. |
| `code` | Human-readable work item code. Must be unique in current AgentCenter DB. |
| `type` | `FE`, `US`, `TASK`, `WORK`, `BUG`, or `VULN` |
| `title` | Work item title |
| `description` | Work item description |
| `status` | Map to AgentCenter `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `DONE` |
| `priority` | Map to AgentCenter `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `project` / `space` / `iteration` | Display names used by current scoped UI filters |
| `projectContextId` | Optional provider context id matching `ProjectContextDto.id` |
| `externalProjectId` / `externalSpaceId` / `externalIterationId` | Stable enterprise ids |
| `assigneeUserId` | AgentCenter user id if mapped, otherwise null |
| `extraJson` | Provider-specific metadata |

## Persistence Behavior

`POST /api/project-data-providers/sync` persists a provider snapshot into:

- `project_context`
- `project_space`
- `project_iteration`
- `project_provider_setting`
- `work_item`

Project, space, and iteration rows are upserted from both `contexts` and `workItems`. This matters when a provider returns work items for an iteration that is not the currently active iteration.

Work items are upserted by `(provider_id, external_work_item_id)`. Legacy rows can still be matched by `code` when they have no `provider_id`.

## Extension Fields

Use `extraJson` for fields that are useful to preserve but not needed for common filters:

```json
{
  "departmentId": "dept-101",
  "businessLine": "payments",
  "sourceUrl": "https://internal.example/work-items/FE-123",
  "securityLevel": "internal"
}
```

Promote a field from `extraJson` to a real column only when one of these becomes true:

- The UI filters or sorts by it.
- The workflow engine branches on it.
- It participates in permissions.
- It is needed for joins or integrity checks.

## Validation Checklist

- Provider id is stable and unique.
- Every project, space, iteration, and work item has a stable external id.
- Every work item maps to exactly one project, one space, and one iteration.
- Work item `code` does not collide with another provider's existing code.
- Status and priority mapping is total and has a safe fallback.
- `snapshot()` handles internal API failure with a clear exception.
- Sync is idempotent: running it twice should update existing rows, not duplicate them.
- Switching provider in Runtime Settings changes available contexts and scoped FE/US/TASK/WORK data without Web code changes.

## Minimal Smoke Test

After adding the enterprise provider bean:

1. Start Bridge.
2. Open Runtime Settings and select the enterprise provider.
3. Call `POST /api/project-data-providers/sync`.
4. Verify `GET /api/project-data-providers` returns the enterprise provider as active.
5. Verify project rows exist in `project_context`, `project_space`, and `project_iteration`.
6. Verify `GET /api/work-items?projectId={projectName}&spaceId={spaceName}&iterationId={iterationName}` returns only that scope's FE/US/TASK/WORK data.
