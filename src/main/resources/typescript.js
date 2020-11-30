"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var path = require("path");
require("es6-shim");
var Logger = (function () {
    function Logger(logLevel) {
        this.logLevel = logLevel;
        this.isDebug = logLevel === "debug";
    }
    Logger.prototype.debug = function (message, object) {
        if (this.logLevel === "debug") {
            if (object) {
                console.log(message, object);
            }
            else {
                console.log(message);
            }
        }
    };
    Logger.prototype.info = function (message) {
        if (this.logLevel === "debug" || this.logLevel === "info") {
            console.log(message);
        }
    };
    Logger.prototype.warn = function (message) {
        if (this.logLevel === "debug" || this.logLevel === "info" || this.logLevel === "warn") {
            console.log(message);
        }
    };
    Logger.prototype.error = function (message, error) {
        if (this.logLevel === "debug" || this.logLevel === "info" || this.logLevel === "warn" || this.logLevel === "error") {
            if (error !== undefined) {
                var errorMessage = error.message;
                if (error.fileName !== undefined) {
                    errorMessage = errorMessage + " in " + error.fileName;
                }
                if (error.lineNumber !== undefined) {
                    errorMessage = errorMessage + " at line " + error.lineNumber;
                }
                console.log(message + " " + errorMessage);
            }
            else {
                console.log(message);
            }
        }
    };
    return Logger;
}());
var SourceMapping = (function () {
    function SourceMapping(a) {
        this.absolutePath = a[0];
        this.relativePath = a[1];
    }
    SourceMapping.prototype.normalizedAbsolutePath = function () {
        return path.normalize(this.absolutePath);
    };
    SourceMapping.prototype.toOutputPath = function (targetDir, extension) {
        return path.join(targetDir, replaceFileExtension(path.normalize(this.relativePath), extension));
    };
    return SourceMapping;
}());
var SourceMappings = (function () {
    function SourceMappings(sourceFileMappings) {
        this.mappings = sourceFileMappings.map(function (a) { return new SourceMapping(a); });
    }
    SourceMappings.prototype.asAbsolutePaths = function () {
        if (!this.absolutePaths) {
            this.absolutePaths = this.mappings.map(function (sm) { return sm.normalizedAbsolutePath(); });
        }
        return this.absolutePaths;
    };
    SourceMappings.prototype.asRelativePaths = function () {
        if (!this.relativePaths) {
            this.relativePaths = this.mappings.map(function (sm) { return sm.relativePath; });
        }
        return this.relativePaths;
    };
    SourceMappings.prototype.find = function (sourceFileName) {
        var absPath = path.normalize(sourceFileName);
        var index = this.asAbsolutePaths().indexOf(absPath);
        if (index !== -1) {
            return this.mappings[index];
        }
        else {
            return undefined;
        }
    };
    return SourceMappings;
}());
function parseArgs(args) {
    var SOURCE_FILE_MAPPINGS_ARG = 2;
    var TARGET_ARG = 3;
    var OPTIONS_ARG = 4;
    var cwd = process.cwd();
    var sourceFileMappings;
    try {
        sourceFileMappings = JSON.parse(args[SOURCE_FILE_MAPPINGS_ARG]);
    }
    catch (e) {
        sourceFileMappings = [[
            path.join(cwd, args[SOURCE_FILE_MAPPINGS_ARG]),
            args[SOURCE_FILE_MAPPINGS_ARG]
        ]];
    }
    var target = (args.length > TARGET_ARG ? args[TARGET_ARG] : path.join(cwd, "lib"));
    var options;
    if (target.length > 0 && target.charAt(0) === "{") {
        options = JSON.parse(target);
        target = path.join(cwd, "lib");
    }
    else {
        options = (args.length > OPTIONS_ARG ? JSON.parse(args[OPTIONS_ARG]) : {});
    }
    return {
        sourceFileMappings: sourceFileMappings,
        target: target,
        options: options
    };
}
function replaceFileExtension(file, ext) {
    var oldExt = path.extname(file);
    return file.substring(0, file.length - oldExt.length) + ext;
}
var typescript_1 = require("typescript");
var args = parseArgs(process.argv);
var sbtTypescriptOpts = args.options;
var logger = new Logger(sbtTypescriptOpts.logLevel);
var sourceMappings = new SourceMappings(args.sourceFileMappings);
logger.debug("starting compilation of ", sourceMappings.mappings.map(function (sm) { return sm.relativePath; }));
logger.debug("from ", sbtTypescriptOpts.assetsDirs);
logger.debug("to ", args.target);
logger.debug("args " + JSON.stringify(args, null, 2));
var compileResult = compile(sourceMappings, sbtTypescriptOpts, args.target);
compileDone(compileResult);
function compileDone(compileResult) {
    console.log("\u0010" + JSON.stringify(compileResult));
}
function compile(sourceMaps, sbtOptions, target) {
    var problems = [];
    var results = [];
    var _a = toCompilerOptions(sbtOptions), compilerOptions = _a.options, errors = _a.errors;
    if (errors.length > 0) {
        problems.push.apply(problems, toProblems(errors, sbtOptions.tsCodesToIgnore));
    }
    else {
        compilerOptions.outDir = target;
        var nodeModulesPaths = [];
        if (sbtOptions.resolveFromNodeModulesDir) {
            nodeModulesPaths = nodeModulesPaths.concat(sbtOptions.nodeModulesDirs.map(function (p) { return p + "/*"; }));
            nodeModulesPaths = nodeModulesPaths.concat(sbtOptions.nodeModulesDirs.map(function (p) { return p + "/@types/*"; }));
            compilerOptions.typeRoots = sbtOptions.nodeModulesDirs.map(function (p) { return p + "/@types"; });
        }
        var assetPaths = sbtOptions.assetsDirs.map(function (p) { return p + "/*"; });
        compilerOptions.baseUrl = ".";
        compilerOptions.paths = {
            "*": ["*"].concat(nodeModulesPaths)
        };
        logger.debug("using tsc options ", compilerOptions);
        var compilerHost = typescript_1.createCompilerHost(compilerOptions);
        var filesToCompile = sourceMaps.asAbsolutePaths();
        if (sbtOptions.extraFiles)
            filesToCompile = filesToCompile.concat(sbtOptions.extraFiles);
        logger.debug("files to compile ", filesToCompile);
        var program = typescript_1.createProgram(filesToCompile, compilerOptions, compilerHost);
        logger.debug("created program");
        problems.push.apply(problems, findPreemitProblems(program, sbtOptions.tsCodesToIgnore));
        var emitOutput_1 = program.emit();
        var moveTestPromise = sbtOptions.assetsDirs.length === 2 ? moveEmittedTestAssets(sbtOptions) : Promise.resolve({});
        moveTestPromise
            .then(function () {
                if (sbtOptions.assertCompilation) {
                    logAndAssertEmitted(results, emitOutput_1);
                }
            }, function () { });
        problems.push.apply(problems, toProblems(emitOutput_1.diagnostics, sbtOptions.tsCodesToIgnore));
        if (logger.isDebug) {
            var declarationFiles = program.getSourceFiles().filter(isDeclarationFile);
            logger.debug("referring to " + declarationFiles.length + " declaration files and " + (program.getSourceFiles().length - declarationFiles.length) + " code files.");
        }
        if (!emitOutput_1.emitSkipped) {
            results = flatten(program.getSourceFiles().filter(isCodeFile).map(toCompilationResult(sourceMaps, compilerOptions)));
        }
        else {
            results = [];
        }
    }
    return {
        results: results,
        problems: problems
    };
    function logAndAssertEmitted(declaredResults, emitOutput) {
        var ffw = flatFilesWritten(declaredResults);
        var emitted = emitOutput.emitSkipped ? [] : emitOutput.emittedFiles || [];
        logger.debug("files written", ffw);
        logger.debug("files emitted", emitted);
        var emittedButNotDeclared = minus(emitted, ffw);
        var declaredButNotEmitted = minus(ffw, emitted);
        notExistingFiles(ffw)
            .then(function (nef) {
                if (nef.length > 0) {
                    logger.error("files declared that have not been generated " + nef);
                }
                else {
                    logger.debug("all declared files exist");
                }
            })
            .catch(function (err) { return logger.error("unexpected error", err); });
        if (emittedButNotDeclared.length > 0 || declaredButNotEmitted.length > 0) {
            var errorMessage = "\nemitted and declared files are not equal\nemitted but not declared " + emittedButNotDeclared + "\ndeclared but not emitted " + declaredButNotEmitted + "\n";
            if (!emitOutput.emitSkipped)
                logger.error(errorMessage);
        }
        function minus(arr1, arr2) {
            var r = [];
            for (var _i = 0, arr1_1 = arr1; _i < arr1_1.length; _i++) {
                var s = arr1_1[_i];
                if (arr2.indexOf(s) === -1) {
                    r.push(s);
                }
            }
            return r;
        }
    }
    function moveEmittedTestAssets(sbtOpts) {
        var common = commonPath(sbtOpts.assetsDirs[0], sbtOpts.assetsDirs[1]);
        var relPathAssets = sbtOpts.assetsDirs[0].substring(common.length);
        var relPathTestAssets = sbtOpts.assetsDirs[1].substring(common.length);
        var sourcePath = path.join(target, relPathTestAssets);
        var futureRemove = remove(path.join(target, relPathAssets));
        futureRemove.then(function () { return logger.debug("removed " + target + "/" + relPathAssets); });
        var futureMove = move(sourcePath, target);
        futureMove.then(function () { return logger.debug("moved " + sourcePath + " to " + target); });
        return Promise.all([futureRemove, futureMove]);
    }
    function commonPath(path1, path2) {
        var commonPath = "";
        for (var i = 0; i < path1.length; i++) {
            if (path1.charAt(i) === path2.charAt(i)) {
                commonPath += path1.charAt(i);
            }
            else {
                return commonPath;
            }
        }
        return commonPath;
    }
    function toCompilerOptions(sbtOptions) {
        var unparsedCompilerOptions = sbtOptions.tsconfig["compilerOptions"];
        if (unparsedCompilerOptions.outFile) {
            var outFile = path.join(target, unparsedCompilerOptions.outFile);
            logger.debug("single outFile ", outFile);
            unparsedCompilerOptions.outFile = outFile;
        }
        if (sbtOptions.assetsDirs.length === 2) {
            unparsedCompilerOptions.rootDirs = sbtOptions.assetsDirs;
        }
        else if (sbtOptions.assetsDirs.length === 1) {
            unparsedCompilerOptions.rootDir = sbtOptions.assetsDirs[0];
        }
        else {
            throw new Error("nr of asset dirs should always be 1 or 2");
        }
        unparsedCompilerOptions.listEmittedFiles = true;
        return typescript_1.convertCompilerOptionsFromJson(unparsedCompilerOptions, sbtOptions.tsconfigDir, "tsconfig.json");
    }
    function flatFilesWritten(results) {
        var files = [];
        results.forEach(function (cfr) { return cfr.result.filesWritten.forEach(function (fw) { return files.push(fw); }); });
        return files;
    }
    function isCodeFile(f) {
        return !(isDeclarationFile(f));
    }
    function isDeclarationFile(f) {
        var fileName = f.fileName;
        return ".d.ts" === fileName.substring(fileName.length - 5);
    }
    function flatten(xs) {
        var result = [];
        xs.forEach(function (x) {
            if (x !== undefined) {
                result.push(x);
            }
        });
        return result;
    }
}
function toCompilationResult(sourceMappings, compilerOptions) {
    return function (sourceFile) {
        var sm = sourceMappings.find(sourceFile.fileName);
        if (sm !== undefined) {
            var deps = [sourceFile.fileName].concat(sourceFile.referencedFiles.map(function (f) { return f.fileName; }));
            var outputFile = determineOutFile(sm.toOutputPath(compilerOptions.outDir, ".js"), compilerOptions);
            var filesWritten = [outputFile];
            if (compilerOptions.declaration) {
                var outputFileDeclaration = sm.toOutputPath(compilerOptions.outDir, ".d.ts");
                filesWritten.push(outputFileDeclaration);
            }
            if (compilerOptions.sourceMap && !compilerOptions.inlineSourceMap) {
                var outputFileMap = outputFile + ".map";
                filesWritten.push(outputFileMap);
            }
            return {
                source: sourceFile.fileName,
                result: {
                    filesRead: deps,
                    filesWritten: filesWritten
                }
            };
        }
        else {
            return undefined;
        }
    };
}
function determineOutFile(outFile, options) {
    if (options.outFile) {
        logger.debug("single outFile ", options.outFile);
        return options.outFile;
    }
    else {
        return outFile;
    }
}
function findPreemitProblems(program, tsIgnoreList) {
    var diagnostics = typescript_1.getPreEmitDiagnostics(program);
    if (tsIgnoreList)
        return diagnostics.filter(ignoreDiagnostic(tsIgnoreList)).map(parseDiagnostic);
    else
        return diagnostics.map(parseDiagnostic);
}
function toProblems(diagnostics, tsIgnoreList) {
    if (tsIgnoreList)
        return diagnostics.filter(ignoreDiagnostic(tsIgnoreList)).map(parseDiagnostic);
    else
        return diagnostics.map(parseDiagnostic);
}
function ignoreDiagnostic(tsIgnoreList) {
    return function (d) { return tsIgnoreList.indexOf(d.code) === -1; };
}
function parseDiagnostic(d) {
    var lineCol = { line: 0, character: 0 };
    var fileName = "tsconfig.json";
    var lineText = "";
    if (d.file && d.start) {
        lineCol = d.file.getLineAndCharacterOfPosition(d.start);
        var lineStart = d.file.getLineStarts()[lineCol.line];
        var lineEnd = d.file.getLineStarts()[lineCol.line + 1];
        lineText = d.file.text.substring(lineStart, lineEnd);
        fileName = d.file.fileName;
    }
    return {
        lineNumber: lineCol.line + 1,
        characterOffset: lineCol.character,
        message: "TS" + d.code + " " + typescript_1.flattenDiagnosticMessageText(d.messageText, typescript_1.sys.newLine),
        source: fileName,
        severity: toSeverity(d.category),
        lineContent: lineText
    };
    function toSeverity(i) {
        if (i === 0) {
            return "warn";
        }
        else if (i === 1) {
            return "error";
        }
        else if (i === 2) {
            return "info";
        }
        else {
            return "error";
        }
    }
}
var fs = require("fs-extra");
function remove(dir) {
    return new Promise(function (resolve, reject) {
        fs.remove(dir, function (e) {
            if (e) {
                reject(e);
            }
            else {
                resolve();
            }
        });
    });
}
function move(sourcePath, target) {
    return new Promise(function (resolve, reject) {
        fs.copy(sourcePath, target, function (e) {
            if (e) {
                reject(e);
            }
            else {
                fs.remove(sourcePath, function (e) {
                    if (e) {
                        reject(e);
                    }
                    else {
                        resolve();
                    }
                });
            }
        });
    });
}
function notExistingFiles(filesDeclared) {
    return Promise.all(filesDeclared.map(exists))
        .then(function (e) {
            return e.filter(function (a) {
                var s = a[0], exist = a[1];
                return !exist;
            })
                .map(function (a) {
                    var s = a[0], b = a[1];
                    return s;
                });
        });
}
function exists(file) {
    return new Promise(function (resolve, reject) {
        fs.access(file, function (errAccess) {
            if (errAccess) {
                resolve([file, false]);
            } else {
                fs.stat(file, function (err, stats) {
                    if (err) {
                        reject(err);
                    } else {
                        resolve([file, stats.isFile()]);
                    }
                });
            }
        });
    });
}
