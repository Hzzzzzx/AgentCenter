import { get, post } from './client'
import type { RuntimeSkillRefreshResponse } from './types'

export const runtimeResourceApi = {
  listSkills: () => get<RuntimeSkillRefreshResponse>('/runtime/skills'),
  refreshSkills: () => post<RuntimeSkillRefreshResponse>('/runtime/skills/refresh'),
}
