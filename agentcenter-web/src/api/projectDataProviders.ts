import { get, post, put } from './client'
import type {
  ProjectDataProviderSettingsDto,
  ProjectDataSnapshotDto,
  ProjectDataSyncHistoryDto,
  UpdateProjectDataProviderRequest,
} from './types'

export const projectDataProviderApi = {
  settings: () => get<ProjectDataProviderSettingsDto>('/project-data-providers'),
  setActive: (data: UpdateProjectDataProviderRequest) =>
    put<ProjectDataProviderSettingsDto>('/project-data-providers/active', data),
  snapshot: () => get<ProjectDataSnapshotDto>('/project-data-providers/snapshot'),
  sync: () => post<ProjectDataSnapshotDto>('/project-data-providers/sync'),
  syncHistory: (providerId?: string, limit = 20) => {
    const params = new URLSearchParams()
    if (providerId) params.set('providerId', providerId)
    params.set('limit', String(limit))
    return get<ProjectDataSyncHistoryDto[]>(`/project-data-providers/sync-history?${params.toString()}`)
  },
}
