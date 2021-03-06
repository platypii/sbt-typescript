
interface Problem {
  lineNumber: number
  characterOffset: number
  message: string
  source: string
  severity: string
  lineContent: string
}

interface CompilationResult {
  results: CompilationFileResult[]
  problems: Problem[]
}

interface CompilationFileResult {
  source: string
  result: {
    filesRead: string[]
    filesWritten: string[]
  }
}
