type LogLevel = "debug" | "info" | "warn" | "error"

interface SbtTypescriptOptions {
  logLevel: LogLevel,
  tsconfig: any,
  tsconfigDir: string,
  assetsDirs: string[],
  tsCodesToIgnore: number[],
  extraFiles: string[],
  nodeModulesDirs: string[],
  resolveFromNodeModulesDir: boolean,
  assertCompilation: boolean
}
