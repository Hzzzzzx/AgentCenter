export interface ProjectContextSelection {
  id: string
  externalProjectId?: string | null
  project: string
  externalCloudeReqProjectId?: string | null
  cloudeReqProject: string
  externalSpaceId?: string | null
  space: string
  externalIterationId?: string | null
  iteration: string
  iterationStatus?: string | null
  iterationStartAt?: string | null
  iterationEndAt?: string | null
  active?: boolean
  extraJson?: string | null
}

export interface ProjectContextOptions {
  cloudeReqProjects: string[]
  spaces: string[]
  iterations: string[]
}
