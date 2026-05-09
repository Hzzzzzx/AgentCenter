export interface ProjectContextSelection {
  id: string
  project: string
  cloudeReqProject: string
  space: string
  iteration: string
}

export interface ProjectContextOptions {
  cloudeReqProjects: string[]
  spaces: string[]
  iterations: string[]
}
