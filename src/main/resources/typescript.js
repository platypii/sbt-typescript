var st;
(function (st) {
    "use strict";
    var Logger = (function () {
        function Logger(logLevel) {
            this.logLevel = logLevel;
        }
        Logger.prototype.debug = function (message) { if (this.logLevel === 'debug')
            console.log(message); };
        Logger.prototype.info = function (message) { if (this.logLevel === 'debug' || this.logLevel === 'info')
            console.log(message); };
        Logger.prototype.warn = function (message) { if (this.logLevel === 'debug' || this.logLevel === 'info' || this.logLevel === 'warn')
            console.log(message); };
        Logger.prototype.error = function (message, error) {
            if (this.logLevel === 'debug' || this.logLevel === 'info' || this.logLevel === 'warn' || this.logLevel === 'error') {
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
    })();
    var fs = require('fs');
    var typescript = require("typescript");
    var path = require("path");
    var jst = require("jstranspiler");
    var args = jst.args(process.argv);
    var sbtTypescriptOpts = args.options;
    var logger = new Logger(sbtTypescriptOpts.logLevel);
    logger.debug("starting compile");
    logger.debug("args=" + JSON.stringify(args));
    logger.debug("target: " + args.target);
    var compileResult = compile(args.sourceFileMappings, sbtTypescriptOpts);
    compileDone(compileResult);
    function compile(sourceMaps, options) {
        var rootDir = calculateRootDir(sourceMaps);
        var problems = [];
        var _a = toInputOutputFiles(sourceMaps), inputFiles = _a[0], outputFiles = _a[1];
        logger.debug("starting compilation of " + sourceMaps);
        var opt = args.options;
        opt.rootDir = rootDir;
        opt.outDir = args.target;
        var confResult = typescript.parseConfigFileTextToJson(options.tsconfigFilename, JSON.stringify(options.tsconfig));
        if (confResult.error)
            problems.push(parseDiagnostic(confResult.error));
        var results = [];
        if (confResult.config) {
            logger.debug("options = " + JSON.stringify(confResult.config));
            var compilerOptions = confResult.config.compilerOptions;
            var compilerHost = typescript.createCompilerHost(compilerOptions);
            var program = typescript.createProgram(inputFiles, compilerOptions, compilerHost);
            problems.push.apply(problems, findGlobalProblems(program));
            var emitOutput = program.emit();
            problems.push.apply(problems, toProblems(emitOutput.diagnostics));
            var sourceFiles = program.getSourceFiles();
            logger.debug("got some source files " + JSON.stringify(sourceFiles.map(function (sf) { return sf.fileName; })));
            results = flatten(sourceFiles.map(toCompilationResult(inputFiles, outputFiles, compilerOptions)));
        }
        var output = {
            results: results,
            problems: problems
        };
        return output;
    }
    function compileDone(compileResult) {
        console.log("\u0010" + JSON.stringify(compileResult));
    }
    function determineOutFile(outFile, options) {
        if (options.outFile) {
            logger.debug("single outFile");
            return options.outFile;
        }
        else {
            return outFile;
        }
    }
    function toCompilationResult(inputFiles, outputFiles, compilerOptions) {
        return function (sourceFile) {
            var index = inputFiles.indexOf(path.normalize(sourceFile.fileName));
            if (index === -1) {
                logger.debug("did not find source file " + sourceFile.fileName + " in list compile list, assuming library or dependency and skipping output");
                return {};
            }
            var deps = [sourceFile.fileName].concat(sourceFile.referencedFiles.map(function (f) { return f.fileName; }));
            var outputFile = determineOutFile(outputFiles[index], compilerOptions);
            var filesWritten = [outputFile];
            if (compilerOptions.declaration) {
                var outputFileDeclaration = replaceFileExtension(outputFile, ".d.ts");
                filesWritten.push(outputFileDeclaration);
            }
            if (compilerOptions.sourceMap) {
                var outputFileMap = outputFile + ".map";
                fixSourceMapFile(outputFileMap);
                filesWritten.push(outputFileMap);
            }
            var result = {
                source: sourceFile.fileName,
                result: {
                    filesRead: deps,
                    filesWritten: filesWritten
                }
            };
            return {
                value: result
            };
        };
    }
    function flatten(xs) {
        var result = [];
        xs.forEach(function (x) {
            if (x.value)
                result.push(x.value);
        });
        return result;
    }
    function toInputOutputFiles(sourceMaps) {
        var inputFiles = [];
        var outputFiles = [];
        sourceMaps.forEach(function (sourceMap) {
            var absolutFilePath = sourceMap[0];
            var relativeFilePath = sourceMap[1];
            inputFiles.push(path.normalize(absolutFilePath));
            outputFiles.push(path.join(args.target, replaceFileExtension(path.normalize(relativeFilePath), ".js")));
        });
        return [inputFiles, outputFiles];
    }
    function replaceFileExtension(file, ext) {
        var oldExt = path.extname(file);
        return file.substring(0, file.length - oldExt.length) + ext;
    }
    function fixSourceMapFile(file) {
        var sourceMap = JSON.parse(fs.readFileSync(file, 'utf-8'));
        sourceMap.sources = sourceMap.sources.map(function (source) { return path.basename(source); });
        fs.writeFileSync(file, JSON.stringify(sourceMap), 'utf-8');
    }
    function calculateRootDir(sourceMaps) {
        if (sourceMaps.length) {
            var inputFile = path.normalize(sourceMaps[0][0]);
            var outputFile = path.normalize(sourceMaps[0][1]);
            return inputFile.substring(0, inputFile.length - outputFile.length);
        }
        else {
            return "";
        }
    }
    function findGlobalProblems(program) {
        var syntacticDiagnostics = program.getSyntacticDiagnostics();
        if (syntacticDiagnostics.length === 0) {
            var globalDiagnostics = program.getGlobalDiagnostics();
            if (globalDiagnostics.length === 0) {
                var semanticDiagnostics = program.getSemanticDiagnostics();
                return toProblems(semanticDiagnostics);
            }
            else {
                return toProblems(globalDiagnostics);
            }
        }
        else {
            return toProblems(syntacticDiagnostics);
        }
    }
    function toProblems(diagnostics) {
        return diagnostics.map(parseDiagnostic);
    }
    function parseDiagnostic(d) {
        var lineCol = { line: 0, character: 0 };
        var fileName = "Global";
        var lineText = "";
        if (d.file) {
            lineCol = d.file.getLineAndCharacterOfPosition(d.start);
            var lineStart = d.file.getLineStarts()[lineCol.line];
            var lineEnd = d.file.getLineStarts()[lineCol.line + 1];
            lineText = d.file.text.substring(lineStart, lineEnd);
            fileName = d.file.fileName;
        }
        var problem = {
            lineNumber: lineCol.line,
            characterOffset: lineCol.character,
            message: typescript.flattenDiagnosticMessageText(d.messageText, typescript.sys.newLine),
            source: fileName,
            severity: toSeverity(d.category),
            lineContent: lineText
        };
        return problem;
    }
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
})(st || (st = {}));