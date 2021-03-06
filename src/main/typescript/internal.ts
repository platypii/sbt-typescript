///<reference path="sbt-ts.d.ts"/>

/** this file contains code that has no dependencies on external modules */
const path = require("path")
require("es6-shim")

class SourceMapping {
  public absolutePath: string
  public relativePath: string

  constructor(a: string[]) {
    this.absolutePath = a[0]
    this.relativePath = a[1]
  }

  normalizedAbsolutePath(): string {
    return path.normalize(this.absolutePath)
  }

  toOutputPath(targetDir: string, extension: string) {
    return path.join(targetDir,
      replaceFileExtension(path.normalize(this.relativePath), extension)
    )
  }
}

class SourceMappings {
  public mappings: SourceMapping[]
  private absolutePaths: string[]
  private relativePaths: string[]

  constructor(sourceFileMappings: string[][]) {
    this.mappings = sourceFileMappings.map((a) => new SourceMapping(a))
  }

  asAbsolutePaths(): string[] {
    if (!this.absolutePaths) {
      this.absolutePaths = this.mappings.map((sm) => sm.normalizedAbsolutePath())
    }
    return this.absolutePaths
  }

  asRelativePaths(): string[] {
    if (!this.relativePaths) {
      this.relativePaths = this.mappings.map((sm) => sm.relativePath)
    }
    return this.relativePaths
  }
  find(sourceFileName: string): SourceMapping | undefined {
    const absPath = path.normalize(sourceFileName)
    const index = this.asAbsolutePaths().indexOf(absPath)
    if (index !== -1) {
      return this.mappings[index]
    } else {
      // logger.error("did not find '" + absPath + "'")
      return undefined
    }
  }
}

/** interfacing with sbt */
// from jstranspiler
function parseArgs(args: string[]): Args {

  const SOURCE_FILE_MAPPINGS_ARG = 2
  const TARGET_ARG = 3
  const OPTIONS_ARG = 4

  const cwd = process.cwd()

  let sourceFileMappings: string[][]
  try {
    sourceFileMappings = JSON.parse(args[SOURCE_FILE_MAPPINGS_ARG])
  } catch (e) {
    sourceFileMappings = [[
      path.join(cwd, args[SOURCE_FILE_MAPPINGS_ARG]),
      args[SOURCE_FILE_MAPPINGS_ARG]
    ]]
  }

  let target = (args.length > TARGET_ARG ? args[TARGET_ARG] : path.join(cwd, "lib"))

  let options: SbtTypescriptOptions
  if (target.length > 0 && target.charAt(0) === "{") {
    options = JSON.parse(target)
    target = path.join(cwd, "lib")
  } else {
    options = (args.length > OPTIONS_ARG ? JSON.parse(args[OPTIONS_ARG]) : {})
  }

  return {
    sourceFileMappings,
    target,
    options
  }
}

interface Args {
  sourceFileMappings: string[][]
  target: string
  options: SbtTypescriptOptions
}

function replaceFileExtension(file: string, ext: string) {
  const oldExt = path.extname(file)
  return file.substring(0, file.length - oldExt.length) + ext
}
