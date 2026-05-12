import { get, post, put } from './client'
import type {
  ProjectDataProviderSettingsDto,
  ProjectDataSnapshotDto,
  UpdateProjectDataProviderRequest,
} from './types'

export const projectDataProviderApi = {
  settings: () => get<ProjectDataProviderSettingsDto>('/project-data-providers'),
  setActive: (data: UpdateProjectDataProviderRequest) =>
    put<ProjectDataProviderSettingsDto>('/project-data-providers/active', data),
  snapshot: () => get<ProjectDataSnapshotDto>('/project-data-providers/snapshot'),
  sync: () => post<ProjectDataSnapshotDto>('/project-data-providers/sync'),
}
