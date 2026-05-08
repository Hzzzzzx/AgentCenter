#!/usr/bin/env node

const workspaceRoot = new URL('..', import.meta.url).pathname.replace(/\/$/, '')

let buffer = ''

process.stdin.setEncoding('utf8')
process.stdin.on('data', (chunk) => {
  buffer += chunk
  let index
  while ((index = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, index).trim()
    buffer = buffer.slice(index + 1)
    if (line) handleLine(line)
  }
})

function handleLine(line) {
  let message
  try {
    message = JSON.parse(line)
  } catch {
    return
  }

  if (!message || typeof message !== 'object' || !message.method) return

  if (message.method === 'initialize') {
    respond(message.id, {
      protocolVersion: '2024-11-05',
      capabilities: { tools: {} },
      serverInfo: { name: 'agentcenter-test-workspace', version: '0.1.0' },
    })
    return
  }

  if (message.method === 'tools/list') {
    respond(message.id, {
      tools: [
        {
          name: 'workspace_marker',
          description: 'Return AgentCenter runtime test workspace marker and root path.',
          inputSchema: { type: 'object', properties: {}, additionalProperties: false },
        },
      ],
    })
    return
  }

  if (message.method === 'tools/call') {
    const name = message.params?.name
    if (name === 'workspace_marker') {
      respond(message.id, {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              marker: 'AGENTCENTER_RUNTIME_TEST_WORKSPACE=runtime-test-workspace',
              workspaceRoot,
            }),
          },
        ],
      })
      return
    }
    respondError(message.id, -32602, `Unknown tool: ${name}`)
    return
  }

  if (message.id !== undefined) {
    respondError(message.id, -32601, `Method not found: ${message.method}`)
  }
}

function respond(id, result) {
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, result }) + '\n')
}

function respondError(id, code, message) {
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, error: { code, message } }) + '\n')
}

