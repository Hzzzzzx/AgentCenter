import { describe, expect, test } from "bun:test"
import { Effect } from "effect"
import type { Agent } from "@/agent/agent"
import { RuntimeFlags } from "@/effect/runtime-flags"
import type { Plugin } from "@/plugin"
import { ProviderID } from "@/provider/schema"
import { LLMRequestPrep } from "@/session/llm/request"
import { MessageV2 } from "@/session/message-v2"
import { MessageID, SessionID } from "@/session/schema"
import { SessionUserIdentityContext } from "@/session/user-identity-context"
import { ProviderTest } from "../fake/provider"

describe("LLMRequestPrep.prepare", () => {
  test("passes request user identity to chat hooks", async () => {
    const seen: Array<{ name: "chat.params" | "chat.headers"; userId?: string }> = []
    const trigger: Plugin.Interface["trigger"] = (name, input, output) =>
      Effect.sync(() => {
        if (name === "chat.params" || name === "chat.headers") {
          seen.push({ name, userId: (input as { userId?: string }).userId })
        }
        return output
      })
    const plugin = {
      trigger,
      list: () => Effect.succeed([]),
      init: () => Effect.void,
    } satisfies Plugin.Interface
    const model = ProviderTest.model({ providerID: ProviderID.make("openai") })
    const sessionID = SessionID.make("session-user-context")
    const agent = {
      name: "primary",
      mode: "primary",
      options: {},
      permission: [{ permission: "*", pattern: "*", action: "allow" }],
    } satisfies Agent.Info

    await Effect.runPromise(
      Effect.gen(function* () {
        const flags = yield* RuntimeFlags.Service
        yield* LLMRequestPrep.prepare({
          user: {
            id: MessageID.make("msg_user-context"),
            sessionID,
            role: "user",
            time: { created: 0 },
            agent: agent.name,
            model: { providerID: model.providerID, modelID: model.id },
          } satisfies MessageV2.User,
          sessionID,
          model,
          agent,
          system: [],
          messages: [{ role: "user", content: "hello" }],
          small: false,
          tools: {},
          provider: ProviderTest.info({}, model),
          auth: undefined,
          plugin,
          flags,
          isWorkflow: false,
        })
      }).pipe(
        Effect.provide(RuntimeFlags.layer()),
        Effect.provideService(
          SessionUserIdentityContext.Service,
          SessionUserIdentityContext.Service.of({ userId: "alice" }),
        ),
      ),
    )

    expect(seen).toEqual([
      { name: "chat.params", userId: "alice" },
      { name: "chat.headers", userId: "alice" },
    ])
  })
})
