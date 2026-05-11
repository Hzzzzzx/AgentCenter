export interface UploadCandidate {
  id: string
  filename: string
  sizeBytes: number
}

export function splitUploadCandidates(items: UploadCandidate[]): UploadCandidate[][] {
  const small: UploadCandidate[] = []
  const large: UploadCandidate[] = []

  for (const item of items) {
    if (item.sizeBytes > 10 * 1024 * 1024) large.push(item)
    else small.push(item)
  }

  return [small, large]
}
