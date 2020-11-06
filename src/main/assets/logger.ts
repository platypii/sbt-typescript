
type LogLevel = "debug" | "info" | "warn" | "error"

// tslint:disable:no-console
export class Logger {
  public readonly logLevel: LogLevel
  public readonly isDebug: boolean

  constructor(logLevel: LogLevel) {
    this.logLevel = logLevel
    this.isDebug = logLevel === "debug"
  }

  debug(message: string, object?: any) {
    if (this.logLevel === "debug") {
      if (object) {
        console.log(message, object)
      } else {
        console.log(message)
      }
    }
  }

  info(message: string) {
    if (this.logLevel === "debug" || this.logLevel === "info") {
      console.log(message)
    }
  }

  warn(message: string) {
    if (this.logLevel === "debug" || this.logLevel === "info" || this.logLevel === "warn") {
      console.log(message)
    }
  }

  error(message: string, error?: any) {
    if (this.logLevel === "debug" || this.logLevel === "info" || this.logLevel === "warn" || this.logLevel === "error") {
      if (error !== undefined) {
        let errorMessage = error.message
        if (error.fileName !== undefined) {
          errorMessage = errorMessage + " in " + error.fileName
        }
        if (error.lineNumber !== undefined) {
          errorMessage = errorMessage + " at line " + error.lineNumber
        }
        console.log(message + " " + errorMessage)
      } else {
        console.log(message)
      }
    }
  }
}
