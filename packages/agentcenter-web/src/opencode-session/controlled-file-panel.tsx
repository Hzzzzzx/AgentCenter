import type { FileContent, SnapshotFileDiff } from "@opencode-ai/sdk/v2"
import { File } from "@opencode-ai/ui/file"
import { FileIcon } from "@opencode-ai/ui/file-icon"
import { Icon } from "@opencode-ai/ui/icon"
import { previewSelectedLines } from "@opencode-ai/ui/pierre/selection-bridge"
import { normalize as normalizeDiff } from "@opencode-ai/ui/session-diff"
import {
  SessionReview,
  type SessionReviewComment,
  type SessionReviewCommentDelete,
  type SessionReviewCommentUpdate,
  type SessionReviewDiffStyle,
  type SessionReviewLineComment,
} from "@opencode-ai/ui/session-review"
import { createEffect, createMemo, For, Match, on, onCleanup, onMount, Show, Switch } from "solid-js"
import { createStore } from "solid-js/store"
import { selectionFromLines, type SelectedLineRange } from "../../../app/src/context/file/types"
import {
  addWorkspaceFileContext,
  removeWorkspaceFileContextComment,
  updateWorkspaceFileContextComment,
} from "./native-prompt-composer"

type Toast = { tone: "info" | "error" | "success"; text: string }

export type OpenWorkspaceFileDetail = {
  sessionId: string
  path: string
  mode?: "diff" | "text"
  selection?: SelectedLineRange
}

type ControlledFileNode = {
  name: string
  path: string
  type: "file" | "directory"
  ignored: boolean
}

type DirectoryState = {
  expanded: boolean
  loaded?: boolean
  loading?: boolean
  error?: string
  nodes?: ControlledFileNode[]
}

type FileState = {
  loading?: boolean
  loaded?: boolean
  error?: string
  content?: FileContent
}

type State = {
  dirs: Record<string, DirectoryState>
  files: Record<string, FileState>
  activePath?: string
  activeMode: "diff" | "text"
  activeSelection?: SelectedLineRange | null
  reviewDiffStyle: SessionReviewDiffStyle
  reviewOpen: string[]
  reviewComments: SessionReviewComment[]
  reviewFocusedComment: { file: string; id: string } | null
  query: string
  searching: boolean
  searchResults: string[]
}

const [state, setState] = createStore<State>({
  dirs: { "": { expanded: true } },
  files: {},
  activeMode: "diff",
  activeSelection: null,
  reviewDiffStyle: "unified",
  reviewOpen: [],
  reviewComments: [],
  reviewFocusedComment: null,
  query: "",
  searching: false,
  searchResults: [],
})

const openWorkspaceFileEvent = "agentcenter:open-workspace-file"

export function openWorkspaceFile(detail: OpenWorkspaceFileDetail) {
  window.dispatchEvent(new CustomEvent(openWorkspaceFileEvent, { detail }))
}

export function ControlledFilePanel(props: {
  sessionID?: string
  diffs: SnapshotFileDiff[]
  onToast?: (toast: Toast) => void
}) {
  const changed = createMemo(() =>
    props.diffs.filter((diff): diff is SnapshotFileDiff & { file: string } => typeof diff.file === "string"),
  )
  const activeDiff = createMemo(() => changed().find((diff) => diff.file === state.activePath))
  const activeFile = createMemo(() => (state.activePath ? state.files[state.activePath] : undefined))

  createEffect(
    on(
      () => props.sessionID,
      () => {
        setState({
          dirs: { "": { expanded: true } },
          files: {},
          activePath: undefined,
          activeMode: "diff",
          activeSelection: null,
          reviewOpen: [],
          reviewComments: [],
          reviewFocusedComment: null,
          query: "",
          searching: false,
          searchResults: [],
        })
        if (props.sessionID) void loadDirectory("")
      },
      { defer: false },
    ),
  )

  createEffect(() => {
    const files = changed().map((diff) => diff.file)
    if (files.length === 0) {
      if (state.reviewOpen.length > 0) setState("reviewOpen", [])
      return
    }

    const current = state.reviewOpen.filter((file) => files.includes(file))
    if (current.length === 0) {
      setState("reviewOpen", [files[0]!])
      return
    }

    if (current.length !== state.reviewOpen.length) setState("reviewOpen", current)
  })

  const openEventHandler = (event: Event) => {
    if (!(event instanceof CustomEvent)) return
    if (!isOpenWorkspaceFileDetail(event.detail)) return
    if (!props.sessionID || event.detail.sessionId !== props.sessionID) return

    void openFile(event.detail.path, event.detail.mode ?? "text").then(() => {
      if (event.detail.selection) setState("activeSelection", cloneRange(event.detail.selection))
    })
  }

  onMount(() => window.addEventListener(openWorkspaceFileEvent, openEventHandler))
  onCleanup(() => window.removeEventListener(openWorkspaceFileEvent, openEventHandler))

  return (
    <section class="controlled-file-panel">
      <div class="file-panel-heading">
        <span>Files</span>
        <strong>{changed().length ? "OpenCode changes" : "Workspace files"}</strong>
      </div>

      <Show when={props.sessionID} fallback={<p class="file-preview-empty">打开受控会话后，文件能力会绑定到 workspace-control 的 allowedRoot。</p>}>
        <div class="file-panel-search">
          <input
            type="search"
            placeholder="搜索文件"
            value={state.query}
            onInput={(event) => setState("query", event.currentTarget.value)}
            onKeyDown={(event) => {
              if (event.key !== "Enter") return
              event.preventDefault()
              void searchFiles()
            }}
          />
          <button type="button" disabled={state.searching} onClick={() => void searchFiles()}>
            {state.searching ? "..." : "Go"}
          </button>
        </div>

        <Show when={state.searchResults.length > 0}>
          <div class="file-panel-list" data-kind="search">
            <For each={state.searchResults}>
              {(path) => (
                <FileRow
                  path={path}
                  type="file"
                  active={state.activePath === path}
                  onClick={() => void openFile(path, "text")}
                />
              )}
            </For>
          </div>
        </Show>

        <Show when={changed().length > 0}>
          <div class="file-panel-subheading">
            <span>Changed</span>
            <em>{changed().length}</em>
          </div>
          <div class="file-panel-review">
            <SessionReview
              title="OpenCode Review"
              empty={<p class="file-preview-empty">No OpenCode changes.</p>}
              diffs={changed()}
              diffStyle={state.reviewDiffStyle}
              onDiffStyleChange={(style) => setState("reviewDiffStyle", style)}
              open={state.reviewOpen}
              onOpenChange={(open) => setState("reviewOpen", open)}
              onViewFile={(path) => void openFile(path, "text")}
              readFile={readWorkspaceFile}
              onLineComment={addReviewComment}
              onLineCommentUpdate={updateReviewComment}
              onLineCommentDelete={removeReviewComment}
              lineCommentActions={{
                moreLabel: "More",
                editLabel: "Edit",
                deleteLabel: "Delete",
                saveLabel: "Save",
              }}
              comments={state.reviewComments}
              focusedComment={state.reviewFocusedComment}
              onFocusedCommentChange={(focus) => setState("reviewFocusedComment", focus)}
              focusedFile={state.activePath}
              classes={{
                root: "agentcenter-session-review-scroll",
                header: "agentcenter-session-review-header",
                container: "agentcenter-session-review-container",
              }}
            />
          </div>
        </Show>

        <div class="file-panel-subheading">
          <span>Workspace</span>
          <button type="button" onClick={() => void loadDirectory("", true)}>
            Refresh
          </button>
        </div>
        <div class="file-tree">
          <TreeBranch path="" level={0} />
        </div>

        <Show when={state.activePath}>
          {(path) => (
            <div class="file-panel-viewer">
              <div class="file-panel-viewer-head">
                <span>{path()}</span>
                <div class="file-panel-mode">
                  <button type="button" onClick={() => addFileContext(path())}>
                    {state.activeSelection ? "Add selection" : "Add context"}
                  </button>
                  <Show when={activeDiff()}>
                    <button
                      type="button"
                      class={state.activeMode === "diff" ? "active" : ""}
                      onClick={() => setState("activeMode", "diff")}
                    >
                      Diff
                    </button>
                    <button
                      type="button"
                      class={state.activeMode === "text" ? "active" : ""}
                      onClick={() => {
                        setState("activeMode", "text")
                        void loadFile(path())
                      }}
                    >
                      File
                    </button>
                  </Show>
                </div>
              </div>
              <Switch>
                <Match when={state.activeMode === "diff" && activeDiff()}>
                  {(diff) => {
                    const view = normalizeDiff(diff())
                    return (
                      <div class="file-panel-file" data-scrollable>
                        <File mode="diff" fileDiff={view.fileDiff} />
                      </div>
                    )
                  }}
                </Match>
                <Match when={activeFile()?.loading}>
                  <p class="file-preview-empty">Loading...</p>
                </Match>
                <Match when={activeFile()?.error}>
                  {(error) => <p class="file-preview-empty">{error()}</p>}
                </Match>
                <Match when={activeFile()?.content}>
                  {(content) => (
                    <FileContentView
                      path={path()}
                      content={content()}
                      selection={state.activeSelection}
                      onSelection={(range) => setState("activeSelection", range)}
                    />
                  )}
                </Match>
                <Match when={!activeFile()?.loaded}>
                  <p class="file-preview-empty">选择文件后会在这里预览内容。</p>
                </Match>
              </Switch>
            </div>
          )}
        </Show>
      </Show>
    </section>
  )

  async function searchFiles() {
    if (!props.sessionID) return
    const query = state.query.trim()
    if (!query) {
      setState("searchResults", [])
      return
    }
    setState("searching", true)
    try {
      const results = await request<string[]>(
        `/agentcenter/session/${encodeURIComponent(props.sessionID)}/find/file?query=${encodeURIComponent(query)}&dirs=false&limit=20`,
      )
      setState("searchResults", results)
    } catch (error) {
      props.onToast?.({ tone: "error", text: `搜索文件失败：${errorText(error)}` })
    } finally {
      setState("searching", false)
    }
  }

  async function openFile(path: string, mode: "diff" | "text") {
    setState("activePath", path)
    setState("activeMode", activeDiff() && mode === "diff" ? "diff" : "text")
    setState("activeSelection", null)
    if (mode === "text" || !activeDiff()) await loadFile(path)
  }

  async function loadDirectory(input: string, force = false) {
    if (!props.sessionID) return
    const dir = normalizeDir(input)
    const current = state.dirs[dir]
    if (!force && current?.loaded) return
    setState("dirs", dir, { ...(current ?? { expanded: dir === "" }), loading: true, error: undefined })
    try {
      const nodes = await request<ControlledFileNode[]>(
        `/agentcenter/session/${encodeURIComponent(props.sessionID)}/file?path=${encodeURIComponent(dir)}`,
      )
      setState("dirs", dir, {
        expanded: current?.expanded ?? dir === "",
        loaded: true,
        loading: false,
        nodes,
      })
    } catch (error) {
      setState("dirs", dir, {
        ...(current ?? { expanded: dir === "" }),
        loading: false,
        error: errorText(error),
      })
      props.onToast?.({ tone: "error", text: `加载文件树失败：${errorText(error)}` })
    }
  }

  async function loadFile(path: string) {
    if (!props.sessionID || !path) return
    const current = state.files[path]
    if (current?.loaded || current?.loading) return
    setState("files", path, { loading: true, error: undefined })
    try {
      const content = await request<FileContent>(
        `/agentcenter/session/${encodeURIComponent(props.sessionID)}/file/content?path=${encodeURIComponent(path)}`,
      )
      setState("files", path, { loading: false, loaded: true, content })
    } catch (error) {
      setState("files", path, { loading: false, loaded: false, error: errorText(error) })
      props.onToast?.({ tone: "error", text: `读取文件失败：${errorText(error)}` })
    }
  }

  async function readWorkspaceFile(path: string) {
    if (!props.sessionID) return undefined
    const current = state.files[path]
    if (current?.content) return current.content

    try {
      const content = await request<FileContent>(
        `/agentcenter/session/${encodeURIComponent(props.sessionID)}/file/content?path=${encodeURIComponent(path)}`,
      )
      setState("files", path, { loading: false, loaded: true, content })
      return content
    } catch (error) {
      setState("files", path, { loading: false, loaded: false, error: errorText(error) })
      props.onToast?.({ tone: "error", text: `读取文件失败：${errorText(error)}` })
      return undefined
    }
  }

  function addFileContext(path: string) {
    if (!props.sessionID) return
    const selection = state.activeSelection ? selectionFromLines(state.activeSelection) : undefined
    addWorkspaceFileContext({
      sessionId: props.sessionID,
      path,
      selection,
      preview: fileSelectionPreview(path, state.activeSelection),
    })
    props.onToast?.({ tone: "success", text: `已加入 OpenCode 上下文：${contextLabel(path, state.activeSelection)}` })
  }

  function addReviewComment(input: SessionReviewLineComment) {
    if (!props.sessionID) return
    const comment = input.comment.trim()
    if (!comment) return

    const saved: SessionReviewComment = {
      id: reviewCommentId(),
      file: input.file,
      selection: cloneRange(input.selection),
      comment,
    }
    setState("reviewComments", (items) => [...items, saved])
    addWorkspaceFileContext({
      sessionId: props.sessionID,
      path: input.file,
      selection: selectionFromLines(input.selection),
      comment,
      commentID: saved.id,
      commentOrigin: "review",
      preview: input.preview,
    })
    props.onToast?.({ tone: "success", text: `已加入 Review 上下文：${contextLabel(input.file, input.selection)}` })
  }

  function updateReviewComment(input: SessionReviewCommentUpdate) {
    if (!props.sessionID) return
    const comment = input.comment.trim()
    if (!comment) return

    setState("reviewComments", (items) =>
      items.map((item) =>
        item.id === input.id && item.file === input.file
          ? { ...item, selection: cloneRange(input.selection), comment }
          : item,
      ),
    )
    updateWorkspaceFileContextComment({
      sessionId: props.sessionID,
      path: input.file,
      commentID: input.id,
      comment,
      preview: input.preview,
    })
  }

  function removeReviewComment(input: SessionReviewCommentDelete) {
    if (!props.sessionID) return
    setState("reviewComments", (items) => items.filter((item) => item.id !== input.id || item.file !== input.file))
    removeWorkspaceFileContextComment({
      sessionId: props.sessionID,
      path: input.file,
      commentID: input.id,
    })
  }

  function toggleDirectory(path: string) {
    const dir = normalizeDir(path)
    const next = !(state.dirs[dir]?.expanded ?? false)
    setState("dirs", dir, { ...(state.dirs[dir] ?? { loaded: false }), expanded: next })
    if (next) void loadDirectory(dir)
  }

  function TreeBranch(branch: { path: string; level: number }) {
    const nodes = createMemo(() => state.dirs[normalizeDir(branch.path)]?.nodes ?? [])
    const dir = createMemo(() => state.dirs[normalizeDir(branch.path)])
    return (
      <div class="file-tree-branch">
        <Show when={dir()?.loading}>
          <p class="file-preview-empty">Loading...</p>
        </Show>
        <Show when={dir()?.error}>
          {(error) => <p class="file-preview-empty">{error()}</p>}
        </Show>
        <For each={nodes()}>
          {(node) => {
            const expanded = createMemo(() => !!state.dirs[normalizeDir(node.path)]?.expanded)
            return (
              <div>
                <button
                  type="button"
                  class={`file-tree-row ${state.activePath === node.path ? "file-tree-row--active" : ""}`}
                  style={{ "--level": String(branch.level) }}
                  onClick={() => {
                    if (node.type === "directory") {
                      toggleDirectory(node.path)
                      return
                    }
                    void openFile(node.path, "text")
                  }}
                >
                  <Show when={node.type === "directory"} fallback={<span class="file-tree-spacer" />}>
                    <Icon name={expanded() ? "chevron-down" : "chevron-right"} size="small" />
                  </Show>
                  <FileIcon node={node} expanded={expanded()} mono={node.ignored} class="file-tree-icon" />
                  <span>{node.name}</span>
                </button>
                <Show when={node.type === "directory" && expanded()}>
                  <TreeBranch path={node.path} level={branch.level + 1} />
                </Show>
              </div>
            )
          }}
        </For>
      </div>
    )
  }
}

function FileRow(props: { path: string; type: "file" | "directory"; active?: boolean; onClick: () => void }) {
  return (
    <button type="button" class={`file-tree-row ${props.active ? "file-tree-row--active" : ""}`} onClick={props.onClick}>
      <span class="file-tree-spacer" />
      <FileIcon node={{ path: props.path, type: props.type }} class="file-tree-icon" />
      <span>{props.path}</span>
    </button>
  )
}

function FileContentView(props: {
  path: string
  content: FileContent
  selection?: SelectedLineRange | null
  onSelection: (range: SelectedLineRange | null) => void
}) {
  return (
    <div class="file-panel-file" data-scrollable>
      <Show
        when={props.content.type === "text"}
        fallback={<p class="file-preview-empty">Binary file cannot be previewed.</p>}
      >
        <File
          mode="text"
          file={{
            name: props.path,
            contents: props.content.content,
            cacheKey: `${props.path}:${props.content.content.length}:${props.content.diff ?? ""}`,
          }}
          enableLineSelection
          selectedLines={props.selection}
          onLineSelectionEnd={props.onSelection}
          media={{ mode: "auto", path: props.path, current: props.content }}
          class="select-text"
        />
      </Show>
    </div>
  )
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path, { headers: { "content-type": "application/json" } })
  if (!response.ok) throw new Error((await response.text().catch(() => "")) || `${response.status} ${response.statusText}`)
  return (await response.json()) as T
}

function normalizeDir(path: string) {
  return path.replace(/^\/+|\/+$/g, "")
}

function errorText(error: unknown) {
  if (error instanceof Error) return error.message
  if (typeof error === "string") return error
  return String(error)
}

function fileSelectionPreview(path: string, range?: SelectedLineRange | null) {
  if (!range) return undefined
  const content = state.files[path]?.content
  if (!content || content.type !== "text") return undefined
  return previewSelectedLines(content.content, {
    start: Math.min(range.start, range.end),
    end: Math.max(range.start, range.end),
  })
}

function contextLabel(path: string, range?: SelectedLineRange | null) {
  if (!range) return path
  const start = Math.min(range.start, range.end)
  const end = Math.max(range.start, range.end)
  return start === end ? `${path}:${start}` : `${path}:${start}-${end}`
}

function cloneRange(range: SelectedLineRange): SelectedLineRange {
  return {
    start: range.start,
    end: range.end,
    ...(range.side ? { side: range.side } : {}),
    ...(range.endSide ? { endSide: range.endSide } : {}),
  }
}

function reviewCommentId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID()
  return `review-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

function isOpenWorkspaceFileDetail(input: unknown): input is OpenWorkspaceFileDetail {
  if (typeof input !== "object" || input === null) return false
  const detail = input as Partial<OpenWorkspaceFileDetail>
  return (
    typeof detail.sessionId === "string" &&
    typeof detail.path === "string" &&
    detail.path.length > 0 &&
    (detail.mode === undefined || detail.mode === "diff" || detail.mode === "text") &&
    (detail.selection === undefined || isSelectedLineRange(detail.selection))
  )
}

function isSelectedLineRange(input: unknown): input is SelectedLineRange {
  if (typeof input !== "object" || input === null) return false
  const range = input as Partial<SelectedLineRange>
  return (
    Number.isFinite(range.start) &&
    Number.isFinite(range.end) &&
    (range.side === undefined || range.side === "additions" || range.side === "deletions") &&
    (range.endSide === undefined || range.endSide === "additions" || range.endSide === "deletions")
  )
}
