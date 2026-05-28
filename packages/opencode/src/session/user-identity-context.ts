import { Context, Effect, Layer, Option } from "effect"

export interface Identity {
  readonly userId?: string
}

export class Service extends Context.Service<Service, Identity>()("@opencode/SessionUserIdentity") {}

export const defaultLayer = Layer.succeed(Service, Service.of({}))

export const getUserId = Effect.gen(function* () {
  const identity = Option.getOrUndefined(yield* Effect.serviceOption(Service))
  return identity?.userId
})

export * as SessionUserIdentityContext from "./user-identity-context"
