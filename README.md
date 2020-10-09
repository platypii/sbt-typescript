# sbt-typescript

[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://travis-ci.org/joost-de-vries/sbt-typescript.png?branch=master)](https://travis-ci.org/joost-de-vries/sbt-typescript)
[![Maven Artifact](https://img.shields.io/maven-central/v/com.github.platypii/sbt-typescript.svg)](https://search.maven.org/search?q=g:com.github.platypii%20a:sbt-typescript)

This sbt plugin compiles the TypeScript code in your Play application to javascript fit for consumption by your average browser and device.
Leverages the functionality of com.typesafe.sbt:js-engine to run the typescript compiler.

## Setup

Add the following line to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.platypii" % "sbt-typescript" % "4.0.3")
```

If your project is not a Play application you will have to enable `sbt-web` in `build.sbt`:

    lazy val root = (project in file(".")).enablePlugins(SbtWeb)

There are several Javascript engines you can use for the build. The fastest is NodeJs. So make sure you have a recent NodeJs installed and add to `build.sbt`

    JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

### Dependencies

NPM libraries are used as standard sbt dependencies (jar files). Add your typescript libraries as dependencies as follows. If the library doesn't include typescript definitions add them too.
```scala
resolvers += Resolver.bintrayRepo("webjars","maven")
libraryDependencies ++= Seq(
    "org.webjars.npm" % "react" % "15.4.0",
    "org.webjars.npm" % "types__react" % "15.0.34"
)
 ```
These NPM dependencies are resolved through [WebJars](https://www.webjars.org/). Check whether the versions of the NPM packages you need are available there. If not you can add them yourself. Since we added the webjars resolver they'll be available immediately. Otherwise you'd have to wait a while before being able to use them. NPM package names like `@angular/code` and `@types/react` are a bit different in webjars: `angular__react` and `types__react`.
Add the following to `build.sbt` to resolve against those npms.

    resolveFromWebjarsNodeModulesDir := true

To lint your TypeScript code add [`sbt-tslint`](https://github.com/joost-de-vries/sbt-tslint) to your project and create a `tslint.json` file with the linting rules.

### Configuration

Create a `tsconfig.json` file in the root of your project with the required [compiler options](https://www.typescriptlang.org/docs/handbook/compiler-options.html).

The following `tsc` compiler options are managed by `sbt-typescript` so setting them in `tsconfig.json` has no effect: `outDir`, `rootDirs`, `paths`, `baseUrl`, `typeRoots`. If you use the `stage` compile mode the `outFile` option is also managed by `sbt-typescript`.

Option                 | Description
-----------------------|------------
outFile                | Concatenate and emit output to a single file.
outDir                 | Destination directory for output files.
typingsFile            | A file that refers to typings that the build needs. Default None, but would normally be "/typings/index.d.ts"

To be able to view the original Typescript code from your browser when developing add the following to `tsconfig.json`
```json
"compilerOptions": {
    "sourceMap": true,
    "mapRoot": "/assets",
    "sourceRoot": "/assets",
```

### Testing

To test your TypeScript code add an sbt plugin for a JS testframework. For instance [sbt-jasmine](https://github.com/joost-de-vries/sbt-jasmine) or [sbt-mocha](https://github.com/sbt/sbt-mocha). You can override `tsc` configurations for your test code. To do that create a file `tsconfig.test.json` and add to `build.sbt`

    (projectTestFile in typescript) := Some("tsconfig.test.json")

Any settings in that file will override those in `tsconfig.json` for the compilation of test code.

#### Configuring an IDE
The typescript version of your project can be found in `project/target/node-modules/webjars/typescript` Configure your IDE to use that and point it to the `tsconfig.json`.

#### Compiling directly through tsc
Sometimes it can be helpful to compile your project directly through the TypeScript compiler without `sbt-typescript` in between to check whether a problem is an `sbt-typescript` problem. To do that you can run

```bash
project/target/node-modules/webjars/typescript/bin/tsc -p . -w
```
Make sure to set the executable bit if necessary.
For this kind of compilation to work you have to fill in the settings in `tsconfig.json` that `sbt-typescript` normally manages. See the [Angular2 demo project](https://github.com/joost-de-vries/play-angular-typescript.g8/blob/master/src/main/g8/tsconfig.json) for an example.

#### Compiling to a single js file
You can develop using individual javascript files when running `sbt ~run` in Play and have your whole typescript application concatenated into a single javascript output file for your stage environment without changes to your sources. To do that you have to add a `-DtsCompileMode=stage` parameter to the sbt task in your CI that creates the stage app. So for Play that will often be `sbt stage -DtsCompileMode=stage`.

#### Import modules without type information
If you are importing modules for which you don't have the typings you can ignore the TS2307 `can not find module` error:

    tsCodesToIgnore := List(canNotFindModule)

## Publishing sbt-typescript

Use sbt-sonatype plugin.

```
sbt publishSigned
sbt sonatypeBundleRelease
```

## History

This project was started by Brandon Arp: https://github.com/ArpNetworking/sbt-typescript

With further contributions by Joost de Vries: https://github.com/joost-de-vries/sbt-typescript

Adopted by platypii: https://github.com/platypii/sbt-typescript
