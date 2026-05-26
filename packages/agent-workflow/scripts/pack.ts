import { mkdir } from "node:fs/promises"
import path from "node:path"
import { spawn } from "node:child_process"
import { fileURLToPath } from "node:url"

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..")
const outDir = path.join(repoRoot, "dist")
const archive = path.join(outDir, "agent-workflow-runtime.tgz")

await mkdir(outDir, { recursive: true })
await run("tar", ["-czf", archive, "-C", repoRoot, "packages/agent-workflow"])

console.log(`Packed Agent Workflow runtime: ${archive}`)

function run(command: string, args: string[]) {
  return new Promise<void>((resolve, reject) => {
    const child = spawn(command, args, { stdio: "inherit" })
    child.on("error", reject)
    child.on("close", (code) => {
      if (code === 0) {
        resolve()
        return
      }
      reject(new Error(`${command} exited with ${code}`))
    })
  })
}
