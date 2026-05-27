import { Agent } from "@/agent/agent"
import { Command } from "@/command"
import { Config } from "@/config/config"
import { Format } from "@/format"
import { LSP } from "@/lsp/lsp"
import { MCP } from "@/mcp"
import { Permission } from "@/permission"
import { PermissionID } from "@/permission/schema"
import { File as OpenCodeFile } from "@/file"
import { Ripgrep } from "@/file/ripgrep"
import { ModelID, ProviderID } from "@/provider/schema"
import { Project } from "@/project/project"
import { Provider } from "@/provider/provider"
import { Question } from "@/question"
import { QuestionID } from "@/question/schema"
import { Session } from "@/session/session"
import { MessageV2 } from "@/session/message-v2"
import { SessionPrompt } from "@/session/prompt"
import { SessionRevert } from "@/session/revert"
import { MessageID, PartID, SessionID } from "@/session/schema"
import { SessionStatus } from "@/session/status"
import { Todo } from "@/session/todo"
import { Snapshot } from "@/snapshot"
import { Vcs } from "@/project/vcs"
import { Schema, Struct } from "effect"
import { HttpApi, HttpApiEndpoint, HttpApiError, HttpApiGroup, HttpApiSchema, OpenApi } from "effect/unstable/httpapi"
import { Authorization } from "../middleware/authorization"
import { ApiNotFoundError, PermissionNotFoundError, QuestionNotFoundError } from "../errors"
import { described } from "./metadata"

const ProductId = Schema.String

export const AgentCenterMe = Schema.Struct({
  tenantId: Schema.String,
  userId: Schema.String,
})

export const AgentCenterWorkItem = Schema.Struct({
  id: Schema.String,
  title: Schema.String,
  status: Schema.Literals(["open", "running", "blocked", "done", "archived"]),
})

export const AgentCenterProject = Schema.Struct({
  tenantId: Schema.String,
  projectId: Schema.String,
  name: Schema.String,
  workItems: Schema.Array(AgentCenterWorkItem),
})

export const AgentCenterOpenPayload = Schema.Struct({
  title: Schema.optional(Schema.String),
})

const AgentCenterNativePromptFields = Struct.omit(SessionPrompt.PromptInput.fields, ["sessionID", "parts"])
export const AgentCenterPromptPayload = Schema.Struct({
  ...AgentCenterNativePromptFields,
  text: Schema.optional(Schema.String),
  parts: Schema.optional(SessionPrompt.PromptInput.fields.parts),
})
export const AgentCenterCommandPayload = Schema.Struct(Struct.omit(SessionPrompt.CommandInput.fields, ["sessionID"]))
export const AgentCenterShellPayload = Schema.Struct(Struct.omit(SessionPrompt.ShellInput.fields, ["sessionID"]))

export const AgentCenterQuestionReplyPayload = Schema.Struct({
  answers: Schema.Array(Question.Answer),
})

export const AgentCenterPermissionReplyPayload = Schema.Struct({
  reply: Permission.Reply,
  message: Schema.optional(Schema.String),
})

export const AgentCenterRevertPayload = Schema.Struct(Struct.omit(SessionRevert.RevertInput.fields, ["sessionID"]))

export const AgentCenterForkPayload = Schema.Struct({
  messageID: Schema.optional(MessageID),
})

export const AgentCenterCompactPayload = Schema.Struct({
  model: Schema.optional(
    Schema.Struct({
      providerID: ProviderID,
      modelID: ModelID,
    }),
  ),
  auto: Schema.optional(Schema.Boolean),
})

export const AgentCenterSessionUpdatePayload = Schema.Struct({
  title: Schema.optional(Schema.String),
  permission: Schema.optional(Permission.Ruleset),
  time: Schema.optional(
    Schema.Struct({
      archived: Schema.optional(Session.ArchivedTimestamp),
    }),
  ),
})

export const AgentCenterFileNode = Schema.Struct({
  name: Schema.String,
  path: Schema.String,
  type: Schema.Literals(["file", "directory"]),
  ignored: Schema.Boolean,
})

export const AgentCenterFileQuery = Schema.Struct({
  path: Schema.optional(Schema.String),
})

export const AgentCenterFindTextQuery = Schema.Struct({
  pattern: Schema.String,
})

export const AgentCenterFindFileQuery = Schema.Struct({
  query: Schema.String,
  dirs: Schema.optional(Schema.Literals(["true", "false"])),
  type: Schema.optional(Schema.Literals(["file", "directory"])),
  limit: Schema.optional(
    Schema.NumberFromString.check(Schema.isInt(), Schema.isGreaterThanOrEqualTo(1), Schema.isLessThanOrEqualTo(200)),
  ),
})

export const AgentCenterSessionView = Schema.Struct({
  id: Session.Info.fields.id,
  title: Session.Info.fields.title,
  directoryLabel: Schema.String,
})

export const AgentCenterOpenResponse = Schema.Struct({
  tenantId: Schema.String,
  projectId: Schema.String,
  workItemId: Schema.String,
  userId: Schema.String,
  sessionId: Schema.String,
  workspaceId: Schema.String,
  resourceSnapshotId: Schema.optional(Schema.String),
  allowedRootLabel: Schema.String,
  session: AgentCenterSessionView,
  permission: Permission.Ruleset,
})

export const AgentCenterScopeResponse = Schema.Struct({
  tenantId: Schema.String,
  projectId: Schema.String,
  workItemId: Schema.String,
  userId: Schema.String,
  sessionId: Schema.String,
  workspaceId: Schema.String,
  allowedRootLabel: Schema.String,
})

export const AgentCenterSessionBootstrap = Schema.Struct({
  session: Session.Info,
  messages: Schema.Array(MessageV2.WithParts),
  status: SessionStatus.Info,
  diff: Schema.Array(Snapshot.FileDiff),
  todos: Schema.Array(Todo.Info),
  questions: Schema.Array(Question.Request),
  permissions: Schema.Array(Permission.Request),
})

export const AgentCenterNativePathInfo = Schema.Struct({
  home: Schema.String,
  state: Schema.String,
  config: Schema.String,
  worktree: Schema.String,
  directory: Schema.String,
}).annotate({ identifier: "AgentCenterNativePathInfo" })

export const ProjectParams = Schema.Struct({
  projectID: ProductId,
})

export const OpenWorkItemParams = Schema.Struct({
  projectID: ProductId,
  workItemID: ProductId,
})

export const SessionParams = Schema.Struct({
  sessionID: SessionID,
})

export const MessageParams = Schema.Struct({
  sessionID: SessionID,
  messageID: MessageID,
})

export const PartParams = Schema.Struct({
  sessionID: SessionID,
  messageID: MessageID,
  partID: PartID,
})

export const QuestionParams = Schema.Struct({
  sessionID: SessionID,
  requestID: QuestionID,
})

export const PermissionParams = Schema.Struct({
  sessionID: SessionID,
  requestID: PermissionID,
})

export const AgentCenterPaths = {
  me: "/agentcenter/me",
  projects: "/agentcenter/project",
  workItems: "/agentcenter/project/:projectID/work-item",
  openWorkItem: "/agentcenter/project/:projectID/work-item/:workItemID/open",
  scope: "/agentcenter/session/:sessionID/scope",
  event: "/agentcenter/session/:sessionID/event",
  bootstrap: "/agentcenter/session/:sessionID/bootstrap",
  session: "/agentcenter/session/:sessionID",
  messages: "/agentcenter/session/:sessionID/message",
  message: "/agentcenter/session/:sessionID/message/:messageID",
  part: "/agentcenter/session/:sessionID/message/:messageID/part/:partID",
  children: "/agentcenter/session/:sessionID/children",
  todo: "/agentcenter/session/:sessionID/todo",
  findText: "/agentcenter/session/:sessionID/find",
  findFile: "/agentcenter/session/:sessionID/find/file",
  fileList: "/agentcenter/session/:sessionID/file",
  fileContent: "/agentcenter/session/:sessionID/file/content",
  fileStatus: "/agentcenter/session/:sessionID/file/status",
  abort: "/agentcenter/session/:sessionID/abort",
  command: "/agentcenter/session/:sessionID/command",
  shell: "/agentcenter/session/:sessionID/shell",
  fork: "/agentcenter/session/:sessionID/fork",
  compact: "/agentcenter/session/:sessionID/compact",
  share: "/agentcenter/session/:sessionID/share",
  revert: "/agentcenter/session/:sessionID/revert",
  unrevert: "/agentcenter/session/:sessionID/unrevert",
  questions: "/agentcenter/session/:sessionID/question",
  questionReply: "/agentcenter/session/:sessionID/question/:requestID/reply",
  questionReject: "/agentcenter/session/:sessionID/question/:requestID/reject",
  permissions: "/agentcenter/session/:sessionID/permission",
  permissionReply: "/agentcenter/session/:sessionID/permission/:requestID/reply",
  nativeAgent: "/agentcenter/session/:sessionID/native/agent",
  nativeCommand: "/agentcenter/session/:sessionID/native/command",
  nativeConfig: "/agentcenter/session/:sessionID/native/config",
  nativeConfigProviders: "/agentcenter/session/:sessionID/native/config/providers",
  nativeFormatter: "/agentcenter/session/:sessionID/native/formatter",
  nativeLsp: "/agentcenter/session/:sessionID/native/lsp",
  nativeMcp: "/agentcenter/session/:sessionID/native/mcp",
  nativePath: "/agentcenter/session/:sessionID/native/path",
  nativePermission: "/agentcenter/session/:sessionID/native/permission",
  nativeProject: "/agentcenter/session/:sessionID/native/project",
  nativeProjectCurrent: "/agentcenter/session/:sessionID/native/project/current",
  nativeProvider: "/agentcenter/session/:sessionID/native/provider",
  nativeQuestion: "/agentcenter/session/:sessionID/native/question",
  nativeVcs: "/agentcenter/session/:sessionID/native/vcs",
} as const

export const AgentCenterApi = HttpApi.make("agentcenter").add(
  HttpApiGroup.make("agentcenter")
    .add(
      HttpApiEndpoint.get("me", AgentCenterPaths.me, {
        success: described(AgentCenterMe, "AgentCenter current identity"),
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.me",
          summary: "Get AgentCenter identity",
          description: "Get the current development tenant and user identity used by workspace-control.",
        }),
      ),
      HttpApiEndpoint.get("projects", AgentCenterPaths.projects, {
        success: described(Schema.Array(AgentCenterProject), "AgentCenter projects"),
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.project.list",
          summary: "List AgentCenter projects",
          description: "List first-stage AgentCenter projects and work items managed by workspace-control.",
        }),
      ),
      HttpApiEndpoint.get("workItems", AgentCenterPaths.workItems, {
        params: ProjectParams,
        success: described(Schema.Array(AgentCenterWorkItem), "AgentCenter work items"),
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.workItem.list",
          summary: "List work items",
          description: "List work items for an AgentCenter project.",
        }),
      ),
      HttpApiEndpoint.post("openWorkItem", AgentCenterPaths.openWorkItem, {
        params: OpenWorkItemParams,
        payload: Schema.optional(AgentCenterOpenPayload),
        success: described(AgentCenterOpenResponse, "AgentCenter session opened"),
        error: HttpApiError.BadRequest,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.workItem.open",
          summary: "Open work item session",
          description: "Open a controlled OpenCode session for the selected project and work item.",
        }),
      ),
      HttpApiEndpoint.get("scope", AgentCenterPaths.scope, {
        params: SessionParams,
        success: described(AgentCenterScopeResponse, "AgentCenter session scope"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.scope",
          summary: "Get session scope",
          description: "Get the product scope for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.get("bootstrap", AgentCenterPaths.bootstrap, {
        params: SessionParams,
        success: described(AgentCenterSessionBootstrap, "AgentCenter session bootstrap"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.bootstrap",
          summary: "Bootstrap session rendering context",
          description:
            "Get messages, status, diff, and provider context for a controlled AgentCenter session without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.get("event", AgentCenterPaths.event, {
        params: SessionParams,
        success: Schema.String.pipe(HttpApiSchema.asText({ contentType: "text/event-stream" })),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.event",
          summary: "Subscribe to controlled session events",
          description:
            "Subscribe to OpenCode events for a controlled AgentCenter session without exposing raw runtime directories.",
        }),
      ),
      HttpApiEndpoint.get("messages", AgentCenterPaths.messages, {
        params: SessionParams,
        success: described(Schema.Array(MessageV2.WithParts), "AgentCenter session messages"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.messages",
          summary: "Get session messages",
            description: "Get OpenCode messages for a controlled AgentCenter session without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.get("message", AgentCenterPaths.message, {
        params: MessageParams,
        success: described(MessageV2.WithParts, "AgentCenter session message"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.message",
          summary: "Get controlled session message",
          description: "Get a single OpenCode message for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.patch("update", AgentCenterPaths.session, {
        params: SessionParams,
        payload: AgentCenterSessionUpdatePayload,
        success: described(Session.Info, "Controlled session updated"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.update",
          summary: "Update controlled session",
          description: "Update native OpenCode session metadata while preserving the workspace-control binding.",
        }),
      ),
      HttpApiEndpoint.post("share", AgentCenterPaths.share, {
        params: SessionParams,
        success: described(Session.Info, "Controlled session shared"),
        error: [ApiNotFoundError, HttpApiError.InternalServerError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.share",
          summary: "Share controlled session",
          description: "Share a controlled OpenCode session without trusting browser-provided directories.",
        }),
      ),
      HttpApiEndpoint.delete("unshare", AgentCenterPaths.share, {
        params: SessionParams,
        success: described(Session.Info, "Controlled session unshared"),
        error: [ApiNotFoundError, HttpApiError.InternalServerError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.unshare",
          summary: "Unshare controlled session",
          description: "Remove the share URL for a controlled OpenCode session.",
        }),
      ),
      HttpApiEndpoint.delete("deleteMessage", AgentCenterPaths.message, {
        params: MessageParams,
        success: described(Schema.Boolean, "Controlled message deleted"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.message.delete",
          summary: "Delete controlled session message",
          description: "Delete a message through the bound workspace-control session.",
        }),
      ),
      HttpApiEndpoint.delete("deletePart", AgentCenterPaths.part, {
        params: PartParams,
        success: described(Schema.Boolean, "Controlled message part deleted"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.part.delete",
          summary: "Delete controlled message part",
          description: "Delete a message part through the bound workspace-control session.",
        }),
      ),
      HttpApiEndpoint.patch("updatePart", AgentCenterPaths.part, {
        params: PartParams,
        payload: MessageV2.Part,
        success: described(MessageV2.Part, "Controlled message part updated"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.part.update",
          summary: "Update controlled message part",
          description: "Update a message part through the bound workspace-control session.",
        }),
      ),
      HttpApiEndpoint.get("children", AgentCenterPaths.children, {
        params: SessionParams,
        success: described(Schema.Array(Session.Info), "Controlled child sessions"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.children",
          summary: "Get controlled session children",
          description: "Get forked child sessions for a controlled AgentCenter session without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.get("todo", AgentCenterPaths.todo, {
        params: SessionParams,
        success: described(Schema.Array(Todo.Info), "Controlled session todos"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.todo",
          summary: "Get controlled session todos",
          description: "Get OpenCode todo state for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.get("findText", AgentCenterPaths.findText, {
        params: SessionParams,
        query: AgentCenterFindTextQuery,
        success: described(Schema.Array(Ripgrep.SearchMatch), "Controlled workspace text matches"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.find.text",
          summary: "Find text in controlled session workspace",
          description: "Search text inside a controlled AgentCenter session workspace without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.get("findFile", AgentCenterPaths.findFile, {
        params: SessionParams,
        query: AgentCenterFindFileQuery,
        success: described(Schema.Array(Schema.String), "Controlled workspace file paths"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.find.file",
          summary: "Find files in controlled session workspace",
          description: "Search files inside a controlled AgentCenter session workspace without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.get("fileList", AgentCenterPaths.fileList, {
        params: SessionParams,
        query: AgentCenterFileQuery,
        success: described(Schema.Array(AgentCenterFileNode), "Controlled workspace file nodes"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.file.list",
          summary: "List controlled session files",
          description: "List files inside a controlled AgentCenter session workspace without exposing absolute paths.",
        }),
      ),
      HttpApiEndpoint.get("fileContent", AgentCenterPaths.fileContent, {
        params: SessionParams,
        query: AgentCenterFileQuery,
        success: described(OpenCodeFile.Content, "Controlled workspace file content"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.file.content",
          summary: "Read controlled session file",
          description: "Read file content inside a controlled AgentCenter session workspace.",
        }),
      ),
      HttpApiEndpoint.get("fileStatus", AgentCenterPaths.fileStatus, {
        params: SessionParams,
        success: described(Schema.Array(OpenCodeFile.Info), "Controlled workspace file status"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.file.status",
          summary: "Get controlled session file status",
          description: "Get git status inside a controlled AgentCenter session workspace.",
        }),
      ),
      HttpApiEndpoint.post("prompt", AgentCenterPaths.messages, {
        params: SessionParams,
        payload: AgentCenterPromptPayload,
        success: described(Schema.Boolean, "Prompt accepted"),
        error: [HttpApiError.BadRequest, ApiNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.prompt",
          summary: "Send session prompt",
          description: "Send a prompt to a controlled AgentCenter session without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.post("command", AgentCenterPaths.command, {
        params: SessionParams,
        payload: AgentCenterCommandPayload,
        success: described(MessageV2.WithParts, "Controlled command message"),
        error: [HttpApiError.BadRequest, ApiNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.command",
          summary: "Send controlled session command",
          description: "Send an OpenCode command through workspace-control without exposing raw directories.",
        }),
      ),
      HttpApiEndpoint.post("shell", AgentCenterPaths.shell, {
        params: SessionParams,
        payload: AgentCenterShellPayload,
        success: described(MessageV2.WithParts, "Controlled shell message"),
        error: [HttpApiError.BadRequest, ApiNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.shell",
          summary: "Run controlled shell command",
          description: "Run an OpenCode shell command in the bound workspace-control allowed root.",
        }),
      ),
      HttpApiEndpoint.post("abort", AgentCenterPaths.abort, {
        params: SessionParams,
        success: described(Schema.Boolean, "Session abort requested"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.abort",
          summary: "Abort controlled session",
          description: "Abort the active OpenCode run for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.post("fork", AgentCenterPaths.fork, {
        params: SessionParams,
        payload: Schema.optional(AgentCenterForkPayload),
        success: described(AgentCenterOpenResponse, "Controlled forked session"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.fork",
          summary: "Fork controlled session",
          description:
            "Fork an OpenCode session and bind the new session id to the same AgentCenter workspace-control scope.",
        }),
      ),
      HttpApiEndpoint.post("compact", AgentCenterPaths.compact, {
        params: SessionParams,
        payload: Schema.optional(AgentCenterCompactPayload),
        success: described(Schema.Boolean, "Controlled session compact requested"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.compact",
          summary: "Compact controlled session",
          description: "Compact a controlled AgentCenter session while preserving the workspace-control binding.",
        }),
      ),
      HttpApiEndpoint.post("revert", AgentCenterPaths.revert, {
        params: SessionParams,
        payload: AgentCenterRevertPayload,
        success: described(Session.Info, "Session reverted"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.revert",
          summary: "Revert controlled session",
          description: "Revert a controlled AgentCenter session to a user message.",
        }),
      ),
      HttpApiEndpoint.post("unrevert", AgentCenterPaths.unrevert, {
        params: SessionParams,
        success: described(Session.Info, "Session unreverted"),
        error: [ApiNotFoundError, HttpApiError.BadRequest],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.unrevert",
          summary: "Unrevert controlled session",
          description: "Restore the latest reverted state for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.get("questions", AgentCenterPaths.questions, {
        params: SessionParams,
        success: described(Schema.Array(Question.Request), "Pending controlled session questions"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.question.list",
          summary: "List controlled session questions",
          description: "List pending question requests for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.post("questionReply", AgentCenterPaths.questionReply, {
        params: QuestionParams,
        payload: AgentCenterQuestionReplyPayload,
        success: described(Schema.Boolean, "Question answered"),
        error: [ApiNotFoundError, QuestionNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.question.reply",
          summary: "Reply to controlled session question",
          description: "Reply to an OpenCode question request through workspace-control.",
        }),
      ),
      HttpApiEndpoint.post("questionReject", AgentCenterPaths.questionReject, {
        params: QuestionParams,
        success: described(Schema.Boolean, "Question rejected"),
        error: [ApiNotFoundError, QuestionNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.question.reject",
          summary: "Reject controlled session question",
          description: "Reject an OpenCode question request through workspace-control.",
        }),
      ),
      HttpApiEndpoint.get("permissions", AgentCenterPaths.permissions, {
        params: SessionParams,
        success: described(Schema.Array(Permission.Request), "Pending controlled session permissions"),
        error: ApiNotFoundError,
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.permission.list",
          summary: "List controlled session permissions",
          description: "List pending permission requests for a controlled AgentCenter session.",
        }),
      ),
      HttpApiEndpoint.post("permissionReply", AgentCenterPaths.permissionReply, {
        params: PermissionParams,
        payload: AgentCenterPermissionReplyPayload,
        success: described(Schema.Boolean, "Permission responded"),
        error: [ApiNotFoundError, PermissionNotFoundError],
      }).annotateMerge(
        OpenApi.annotations({
          identifier: "agentcenter.session.permission.reply",
          summary: "Reply to controlled session permission",
          description: "Respond to an OpenCode permission request through workspace-control.",
        }),
      ),
      HttpApiEndpoint.get("nativeAgent", AgentCenterPaths.nativeAgent, {
        params: SessionParams,
        success: described(Schema.Array(Agent.Info), "Controlled native agent list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeCommand", AgentCenterPaths.nativeCommand, {
        params: SessionParams,
        success: described(Schema.Array(Command.Info), "Controlled native command list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeConfig", AgentCenterPaths.nativeConfig, {
        params: SessionParams,
        success: described(Config.Info, "Controlled native config"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeConfigProviders", AgentCenterPaths.nativeConfigProviders, {
        params: SessionParams,
        success: described(Provider.ConfigProvidersResult, "Controlled native config providers"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeFormatter", AgentCenterPaths.nativeFormatter, {
        params: SessionParams,
        success: described(Schema.Array(Format.Status), "Controlled native formatter status"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeLsp", AgentCenterPaths.nativeLsp, {
        params: SessionParams,
        success: described(Schema.Array(LSP.Status), "Controlled native LSP status"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeMcp", AgentCenterPaths.nativeMcp, {
        params: SessionParams,
        success: described(Schema.Record(Schema.String, MCP.Status), "Controlled native MCP status"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativePath", AgentCenterPaths.nativePath, {
        params: SessionParams,
        success: described(AgentCenterNativePathInfo, "Controlled native path info"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativePermission", AgentCenterPaths.nativePermission, {
        params: SessionParams,
        success: described(Schema.Array(Permission.Request), "Controlled native permission list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeProject", AgentCenterPaths.nativeProject, {
        params: SessionParams,
        success: described(Schema.Array(Project.Info), "Controlled native project list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeProjectCurrent", AgentCenterPaths.nativeProjectCurrent, {
        params: SessionParams,
        success: described(Project.Info, "Controlled native current project"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeProvider", AgentCenterPaths.nativeProvider, {
        params: SessionParams,
        success: described(Provider.ListResult, "Controlled native provider list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeQuestion", AgentCenterPaths.nativeQuestion, {
        params: SessionParams,
        success: described(Schema.Array(Question.Request), "Controlled native question list"),
        error: ApiNotFoundError,
      }),
      HttpApiEndpoint.get("nativeVcs", AgentCenterPaths.nativeVcs, {
        params: SessionParams,
        success: described(Vcs.Info, "Controlled native VCS info"),
        error: ApiNotFoundError,
      }),
    )
    .annotateMerge(OpenApi.annotations({ title: "agentcenter", description: "AgentCenter control-plane routes." }))
    .middleware(Authorization),
)
