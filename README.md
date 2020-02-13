# sbt-typescript 

[ ![Download](https://api.bintray.com/packages/platypii/sbt-plugins/sbt-typescript/images/download.svg) ](https://bintray.com/platypii/sbt-plugins/sbt-typescript/_latestVersion)
[![Build Status](https://travis-ci.org/joost-de-vries/sbt-typescript.png?branch=master)](https://travis-ci.org/joost-de-vries/sbt-typescript)
[![Maven Artifact](https://img.shields.io/maven-central/v/com.github.platypii/sbt-typescript.svg)](https://search.maven.org/search?q=g:com.github.platypii%20a:sbt-typescript)

This sbt plugin compiles the TypeScript code in your Play application to javascript fit for consumption by your average browser and device.
Leverages the functionality of com.typesafe.sbt:js-engine to run the typescript compiler.

### Configuration

Create a `tsconfig.json` file in the root of your project with the required [compiler options](http://www.typescriptlang.org/docs/handbook/compiler-options.html).

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

Add the following line to your `project\plugins.sbt`:

    addSbtPlugin("com.github.platypii" % "sbt-typescript" % "3.7.5")

If your project is not a Play application you will have to enable `sbt-web` in `build.sbt`:

    lazy val root = (project in file(".")).enablePlugins(SbtWeb)
    
There are several Javascript engines you can use for the build. The fastest is NodeJs. So make sure you have a recent NodeJs installed and add to `build.sbt`

    JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

NPM libraries are used as standard sbt dependencies (jar files). Add your typescript libraries as dependencies as follows. If the library doesn't include typescript definitions add them too.  
```scala
resolvers += Resolver.bintrayRepo("webjars","maven")
libraryDependencies ++= Seq(
    "org.webjars.npm" % "react" % "15.4.0",
    "org.webjars.npm" % "types__react" % "15.0.34"
)
 ```
These NPM dependencies are resolved through [Webjars](http://webjars.org). Check whether the versions of the NPM packages you need are available there. If not you can add them yourself. Since we added the webjars resolver they'll be available immediately. Otherwise you'd have to wait a while before being able to use them. NB NPM package names like `@angular/code` and `@types/react` are a bit different in webjars: `angular__react` and `types__react`. 
Add the following to `build.sbt` to resolve against those npms.

    resolveFromWebjarsNodeModulesDir := true

To lint your TypeScript code add [`sbt-tslint`](https://github.com/joost-de-vries/sbt-tslint) to your project and create a `tslint.json` file with the linting rules.

To test your TypeScript code add an sbt plugin for a JS testframework. For instance [sbt-jasmine](https://github.com/joost-de-vries/sbt-jasmine) or [sbt-mocha](https://github.com/sbt/sbt-mocha). You can override `tsc` configurations for your test code. To do that create a file `tsconfig.test.json` and add to `build.sbt`

    (projectTestFile in typescript) := Some("tsconfig.test.json")

Any settings in that file will override those in `tsconfig.json` for the compilation of test code.

#### Configuring an IDE
The typescript version of your project can be found in `project/target/node-modules/webjars/typescript` Configure your IDE to use that and point it to the `tsconfig.json`.

#### Compiling directly through tsc
Sometimes it can be helpful to compile your project directly through the TypeScript compiler without `sbt-typescript` in between to check whether a problem is an `sbt-typescript` problem. To do that you can run

    project/target/node-modules/webjars/typescript/bin/tsc -p . -w
Make sure to set the executable bit if necessary.
For this kind of compilation to work you have to fill in the settings in `tsconfig.json` that `sbt-typescript` normally manages. See the [Angular2 demo project](https://github.com/joost-de-vries/play-angular-typescript.g8/blob/master/src/main/g8/tsconfig.json) for an example.
    
#### Compiling to a single js file
You can develop using individual javascript files when running `sbt ~run` in Play and have your whole typescript application concatenated into a single javascript output file for your stage environment without changes to your sources. To do that you have to add a `-DtsCompileMode=stage` parameter to the sbt task in your CI that creates the stage app. So for Play that will often be `sbt stage -DtsCompileMode=stage`.  
    

#### import modules without type information
If you are importing modules for which you don't have the typings you can ignore the TS2307 `can not find module` error:

    tsCodesToIgnore := List(canNotFindModule)
    

## release notes

#### v3.7.5
- upgrade typescript to v3.7.5

#### v3.7.2
- upgrade typescript to v3.7.2
- sbt-typescript project adopted by platypii

#### v2.6.2
- typescript version upgrade
- upgrade to sbt-web 1.4.3
- webjars-locator 0.35

#### v2.6.1
- upgrade typescript to v2.6.1

#### v2.5.2-1
- cross build to sbt 0.13.6 and 1.0.1. alpha release

#### v2.5.2
- upgrade to ts 2.5.2
- fixes issue with test tsconfig overrides

#### v2.4.1-2
- upgrade to sbt-js-engine 1.2.1 and sbt-web 1.4.1
- add correct typeRoots values to tsconfig.json for resolution of @types type def dependencies

#### v2.4.1-1
- allow for overrides of the tsconfig.json for test code

#### v2.4.1
- upgrade to ts npm 2.4.1

#### v2.3.2
- upgrade to ts npm 2.3.2

#### v2.3.1
- upgrade to ts 2.3 final: npm 2.3.1
- sbt-typescript follows the typescript version

#### v0.3.0-beta.11
- upgrade to ts 2.3.0
- fixes readme. Tx [camilosampedro](https://github.com/camilosampedro)

#### v0.3.0-beta.10
- upgrade to ts 2.2.1
- [fixes running on Trireme](https://github.com/joost-de-vries/sbt-typescript/issues/19) Tx [VeryBueno](https://github.com/VeryBueno)!

#### v0.4.0-alfa.1
- supports multi project builds. Uses sbt-web webModules for js deps resolution instead of nodeModules. 
Hence the alfa moniker to see whether f.i. @types resolution still works.

#### v0.3.0-beta.9
- fixes compilation of test assets

#### v0.3.0-beta.8-1
- allows configuring outfile with a path
- makes compile errors 1 based instead of 0 based

#### v0.3.0-beta.8
- upgrades to typescript 2.1 (npm 2.1.4)

#### v0.3.0-beta.7
- upgrades to typescript 2.1 RC (npm 2.1.1)
- resolves webjar @types type definitions

#### v0.3.0-beta.6
- upgrades to typescript 2.0.6

#### v0.3.0-beta.5
- solves an issue (#9) where RxJs would cause a nullpointer. 
- uses typescript 2.0.3

#### v0.3.0-beta.4
- solves an issue (#9) where RxJs would be extracted to the wrong directory. 

#### v0.3.0-beta.3
- uses typescript 2.0 RC (npm 2.0.2)

#### v0.3.0-beta.2
- uses typescript 2.0 beta (npm 2.0.0)

#### v0.3.0SNAPSHOT 
- uses standard typescript functionality to resolve against webjars. Instead of the previous custom rolled module resolution extension.
- uses a snapshot of the upcoming typescript 2.0
- add output assertion options

#### v0.2.7
- adds convenience task for setting up tsc compilation

#### v0.2.6
- fixes jstaskfailure error

#### v0.2.5
- allows for developing using individual javascript files and using a single javascript file in production

#### v0.2.4
- upgrades to typescript 1.8.10

#### v0.2.3
- upgrades to typescript 1.8.7
- adds support for tests in typescript

#### v0.2.2 
- upgrades to typescript 1.8.2
- improves output of single outfile
- fixes a nasty bug in module resolution. This is essential for angular2 applications.
- gives feedback on faulty compiler options.

## History

This project was started by Brandon Arp: https://github.com/ArpNetworking/sbt-typescript

With further contributions by Joost de Vries: https://github.com/joost-de-vries/sbt-typescript

Adopted by platypii: https://github.com/platypii/sbt-typescript
