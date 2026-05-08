import { get } from './client'
import type { ArtifactDto } from './types'

export const artifactApi = {
  get: (id: string) => get<ArtifactDto>(`/artifacts/${id}`),
  listByWorkItem: (workItemId: string) => get<ArtifactDto[]>(`/work-items/${workItemId}/artifacts`),
}
