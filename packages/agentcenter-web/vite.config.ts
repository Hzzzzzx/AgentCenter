import tailwindcss from "@tailwindcss/vite"
import solidPlugin from "vite-plugin-solid"

const target = process.env.AGENTCENTER_OPENCODE_URL ?? "http://127.0.0.1:4096"
const rawDirectoryRoutes = new Set([
  "/agent",
  "/command",
  "/config",
  "/experimental",
  "/formatter",
  "/lsp",
  "/mcp",
  "/path",
  "/project",
  "/provider",
  "/question",
  "/vcs",
])
const opencodeProxy = {
  target,
  changeOrigin: true,
}

export default {
  plugins: [workspaceControlProxyGuard(), tailwindcss(), solidPlugin()],
  resolve: {
    alias: {
      "@tanstack/solid-query": new URL("../app/node_modules/@tanstack/solid-query/build/index.js", import.meta.url)
        .pathname,
      "@": new URL("../app/src", import.meta.url).pathname,
    },
  },
  worker: {
    format: "es",
  },
  server: {
    port: 5174,
    strictPort: false,
    proxy: {
      "/agentcenter": opencodeProxy,
      "/api": opencodeProxy,
      "/app": opencodeProxy,
      "/auth": opencodeProxy,
      "/command": opencodeProxy,
      "/config": opencodeProxy,
      "/experimental": opencodeProxy,
      "/formatter": opencodeProxy,
      "/global": opencodeProxy,
      "/lsp": opencodeProxy,
      "/mcp": opencodeProxy,
      "/path": opencodeProxy,
      "/project": opencodeProxy,
      "/provider": opencodeProxy,
    },
  },
}

function workspaceControlProxyGuard() {
  return {
    name: "agentcenter-workspace-control-proxy-guard",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url) return next()
        const url = new URL(req.url, "http://agentcenter.local")
        if (url.pathname === "/global/event") {
          res.statusCode = 403
          res.setHeader("content-type", "application/json")
          res.end(JSON.stringify({ message: "Raw OpenCode events must go through workspace-control" }))
          return
        }
        if (!rawDirectoryRoutes.has(url.pathname)) return next()
        if (!url.searchParams.has("directory") && !req.headers["x-opencode-directory"]) return next()

        res.statusCode = 403
        res.setHeader("content-type", "application/json")
        res.end(JSON.stringify({ message: "Raw OpenCode directory routes must go through workspace-control" }))
      })
    },
  }
}
