/* global process, require */
/// <reference path="logger.ts" />
/// <reference path="internal.ts" />
/// <reference path="sbt-web.d.ts" />
/// <reference path="files.ts" />

import {
  CompilerOptions,
  Diagnostic,
  DiagnosticCategory,
  EmitResult,
  Program,
  SourceFile,
  convertCompilerOptionsFromJson,
  createProgram,
  createCompilerHost,
  getPreEmitDiagnostics,
  flattenDiagnosticMessageText,
  sys
} from "typescript"
import { Logger } from "./logger"
import * as fs from "./files"

const args: Args = parseArgs(process.argv)
const sbtTypescriptOpts: SbtTypescriptOptions = args.options

const logger = new Logger(sbtTypescriptOpts.logLevel)

const sourceMappings = new SourceMappings(args.sourceFileMappings)

logger.debug("starting compilation of ", sourceMappings.mappings.map((sm) => sm.relativePath))
logger.debug("from ", sbtTypescriptOpts.assetsDirs)
logger.debug("to ", args.target)
logger.debug("args " + JSON.stringify(args, null, 2))

const compileResult = compile(sourceMappings, sbtTypescriptOpts, args.target)

compileDone(compileResult)

function compileDone(compileResult: CompilationResult) {
  // datalink escape character https://en.wikipedia.org/wiki/C0_and_C1_control_codes#DLE
  // used to signal result of compilation see https://github.com/sbt/sbt-js-engine/blob/master/src/main/scala/com/typesafe/sbt/jse/SbtJsTask.scala
  console.log("\u0010" + JSON.stringify(compileResult))
}

function compile(sourceMaps: SourceMappings, sbtOptions: SbtTypescriptOptions, target: string): CompilationResult {
  const problems: Problem[] = []
  let results: CompilationFileResult[] = []

  const {options: compilerOptions, errors} = toCompilerOptions(sbtOptions)

  if (errors.length > 0) {
    problems.push(...toProblems(errors, sbtOptions.tsCodesToIgnore))
  } else {
    compilerOptions.outDir = target

    let nodeModulesPaths: string[] = []
    if (sbtOptions.resolveFromNodeModulesDir) {
      nodeModulesPaths = nodeModulesPaths.concat(sbtOptions.nodeModulesDirs.map(p => p + "/*"))
      nodeModulesPaths = nodeModulesPaths.concat(sbtOptions.nodeModulesDirs.map(p => p + "/@types/*"))
      compilerOptions.typeRoots = sbtOptions.nodeModulesDirs.map(p => p + "/@types")
    }

    const assetPaths = sbtOptions.assetsDirs.map(p => p + "/*")
    // see https://github.com/Microsoft/TypeScript-Handbook/blob/release-2.0/pages/Module%20Resolution.md#path-mapping
    compilerOptions.baseUrl = "."
    compilerOptions.paths = {
      "*": ["*"].concat(nodeModulesPaths) // .concat(assetPaths)
    }
    logger.debug("using tsc options ", compilerOptions)
    const compilerHost = createCompilerHost(compilerOptions)

    let filesToCompile = sourceMaps.asAbsolutePaths()
    if (sbtOptions.extraFiles) filesToCompile = filesToCompile.concat(sbtOptions.extraFiles)

    logger.debug("files to compile ", filesToCompile)
    const program: Program = createProgram(filesToCompile, compilerOptions, compilerHost)
    logger.debug("created program")
    problems.push(...findPreemitProblems(program, sbtOptions.tsCodesToIgnore))

    const emitOutput = program.emit()

    const moveTestPromise = sbtOptions.assetsDirs.length === 2 ? moveEmittedTestAssets(sbtOptions) : Promise.resolve({})

    moveTestPromise
      .then(() => {
        if (sbtOptions.assertCompilation) {
          logAndAssertEmitted(results, emitOutput)
        }
      }, () => {})
    problems.push(...toProblems(emitOutput.diagnostics, sbtOptions.tsCodesToIgnore))

    if (logger.isDebug) {
      const declarationFiles = program.getSourceFiles().filter(isDeclarationFile)
      logger.debug("referring to " + declarationFiles.length + " declaration files and " + (program.getSourceFiles().length - declarationFiles.length) + " code files.")
    }

    if (!emitOutput.emitSkipped) {
      results = flatten(program.getSourceFiles().filter(isCodeFile).map(toCompilationResult(sourceMaps, compilerOptions)))
    } else {
      results = []
    }

  }

  return {
    results,
    problems
  }

  function logAndAssertEmitted(declaredResults: CompilationFileResult[], emitOutput: EmitResult): void {
    const ffw = flatFilesWritten(declaredResults)
    const emitted = emitOutput.emitSkipped ? [] : emitOutput.emittedFiles || []
    logger.debug("files written", ffw)
    logger.debug("files emitted", emitted)

    const emittedButNotDeclared = minus(emitted, ffw)
    const declaredButNotEmitted = minus(ffw, emitted)

    fs.notExistingFiles(ffw)
      .then(nef => {
        if (nef.length > 0) {
          logger.error(`files declared that have not been generated ${nef}`)
        } else {
          logger.debug(`all declared files exist`)
        }

      })
      .catch(err => logger.error("unexpected error", err))

    if (emittedButNotDeclared.length > 0 || declaredButNotEmitted.length > 0) {
      const errorMessage = `
emitted and declared files are not equal
emitted but not declared ${emittedButNotDeclared}
declared but not emitted ${declaredButNotEmitted}
`
      if (!emitOutput.emitSkipped) logger.error(errorMessage) // throw new Error(errorMessage)
    }

    function minus(arr1: string[], arr2: string[]): string[] {
      const r: string[] = []
      for (const s of arr1) {
        if (arr2.indexOf(s) === -1) {
          r.push(s)
        }
      }
      return r
    }
  }

  /**
   * When compiling test assets, unfortunately because we have two rootdirs the paths are not being relativized to outDir
   * see https://github.com/Microsoft/TypeScript/issues/7837
   * so we get
   * ...<outdir>/main/assets/<code> and
   * ...<outdir>/test/assets/<code> because they have ./src in common
   * we need to find out what their relative paths are wrt the path they have in common
   * and move the desired emitted test files up to the target path
   */
  function moveEmittedTestAssets(sbtOpts: SbtTypescriptOptions): Promise<any> {
    const common = commonPath(sbtOpts.assetsDirs[0], sbtOpts.assetsDirs[1])
    const relPathAssets = sbtOpts.assetsDirs[0].substring(common.length)
    const relPathTestAssets = sbtOpts.assetsDirs[1].substring(common.length)

    const sourcePath = path.join(target, relPathTestAssets)
    // logger.debug(`removing ${target}/${relPathAssets}`)
    const futureRemove = fs.remove(path.join(target, relPathAssets))
    futureRemove.then(() => logger.debug(`removed ${target}/${relPathAssets}`))
    // logger.debug(`moving ${sourcePath} to ${target}`)
    const futureMove = fs.move(sourcePath, target)
    futureMove.then(() => logger.debug(`moved ${sourcePath} to ${target}`))
    return Promise.all([futureRemove, futureMove])
  }

  function commonPath(path1: string, path2: string) {
    let commonPath = ""
    for (let i = 0; i < path1.length; i++) {
      if (path1.charAt(i) === path2.charAt(i)) {
        commonPath += path1.charAt(i)
      } else {
        return commonPath
      }
    }
    return commonPath
  }

  function toCompilerOptions(sbtOptions: SbtTypescriptOptions): {options: CompilerOptions, errors: Diagnostic[]} {
    const unparsedCompilerOptions: any = sbtOptions.tsconfig["compilerOptions"]
    // logger.debug("compilerOptions ", unparsedCompilerOptions)
    if (unparsedCompilerOptions.outFile) {
      const outFile = path.join(target, unparsedCompilerOptions.outFile)
      logger.debug("single outFile ", outFile)
      unparsedCompilerOptions.outFile = outFile
    }
    if (sbtOptions.assetsDirs.length === 2) {
      unparsedCompilerOptions.rootDirs = sbtOptions.assetsDirs

    } else if (sbtOptions.assetsDirs.length === 1) {
      // ??! one root dir creates the correct output files, two rootdirs throws away shared directories
      unparsedCompilerOptions.rootDir = sbtOptions.assetsDirs[0]
    } else {
      throw new Error("nr of asset dirs should always be 1 or 2")
    }
    unparsedCompilerOptions.listEmittedFiles = true
    return convertCompilerOptionsFromJson(unparsedCompilerOptions, sbtOptions.tsconfigDir, "tsconfig.json")
  }

  function flatFilesWritten(results: CompilationFileResult[]): string[] {
    const files: string[] = []
    results.forEach(cfr => cfr.result.filesWritten.forEach(fw => files.push(fw)))
    return files
  }

  function isCodeFile(f: SourceFile) {
    return !(isDeclarationFile(f))
  }

  function isDeclarationFile(f: SourceFile) {
    const fileName = f.fileName
    return ".d.ts" === fileName.substring(fileName.length - 5)
  }

  function flatten<T>(xs: Array<T | undefined>): T[] {
    const result: T[] = []
    xs.forEach(x => {
      if (x !== undefined) {
        result.push(x)
      }
    })
    return result
  }
}

function toCompilationResult(sourceMappings: SourceMappings, compilerOptions: CompilerOptions): (sf: SourceFile) => CompilationFileResult | undefined {
  return sourceFile => {
    const sm = sourceMappings.find(sourceFile.fileName)
    if (sm !== undefined) {
      // logger.debug("source file is ", sourceFile.fileName)
      const deps = [sourceFile.fileName].concat(sourceFile.referencedFiles.map(f => f.fileName))
      const outputFile = determineOutFile(sm.toOutputPath(compilerOptions.outDir!, ".js"), compilerOptions)
      const filesWritten = [outputFile]

      if (compilerOptions.declaration) {
        const outputFileDeclaration = sm.toOutputPath(compilerOptions.outDir!, ".d.ts")
        filesWritten.push(outputFileDeclaration)
      }

      if (compilerOptions.sourceMap && !compilerOptions.inlineSourceMap) {
        const outputFileMap = outputFile + ".map"
        filesWritten.push(outputFileMap)
      }

      return {
        source: sourceFile.fileName,
        result: {
          filesRead: deps,
          filesWritten
        }
      }
    } else {
      return undefined
    }
  }
}

function determineOutFile(outFile: string, options: CompilerOptions): string {
  if (options.outFile) {
    logger.debug("single outFile ", options.outFile)
    return options.outFile
  } else {
    return outFile
  }
}

function findPreemitProblems(program: Program, tsIgnoreList?: number[]): Problem[] {
  const diagnostics = getPreEmitDiagnostics(program)

  if (tsIgnoreList) return diagnostics.filter(ignoreDiagnostic(tsIgnoreList)).map(parseDiagnostic)
  else return diagnostics.map(parseDiagnostic)
}

function toProblems(diagnostics: readonly Diagnostic[], tsIgnoreList?: number[]): Problem[] {
  if (tsIgnoreList) return diagnostics.filter(ignoreDiagnostic(tsIgnoreList)).map(parseDiagnostic)
  else return diagnostics.map(parseDiagnostic)
}

function ignoreDiagnostic(tsIgnoreList: number[]): (d: Diagnostic) => boolean {
  return (d: Diagnostic) => tsIgnoreList.indexOf(d.code) === -1
}

function parseDiagnostic(d: Diagnostic): Problem {
  let lineCol = {line: 0, character: 0}
  let fileName = "tsconfig.json"
  let lineText = ""
  if (d.file && d.start) {
    lineCol = d.file.getLineAndCharacterOfPosition(d.start)

    const lineStart = d.file.getLineStarts()[lineCol.line]
    const lineEnd = d.file.getLineStarts()[lineCol.line + 1]
    lineText = d.file.text.substring(lineStart, lineEnd)
    fileName = d.file.fileName
  }

  return {
    lineNumber: lineCol.line + 1,
    characterOffset: lineCol.character,
    message: "TS" + d.code + " " + flattenDiagnosticMessageText(d.messageText, sys.newLine),
    source: fileName,
    severity: toSeverity(d.category),
    lineContent: lineText
  }

  function toSeverity(i: DiagnosticCategory): string {
    if (i === 0) {
      return "warn"
    } else if (i === 1) {
      return "error"
    } else if (i === 2) {
      return "info"
    } else {
      return "error"
    }
  }
}
