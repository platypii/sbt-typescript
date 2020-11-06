// This file mostly promisifies file operations

import * as fs from "fs-extra"

/**
 * Delete a file or directory
 */
export function remove(dir: string): Promise<void> {
  return new Promise((resolve, reject) => {
    fs.remove(dir, (e: any) => {
      if (e) {
        reject(e)
      } else {
        resolve()
      }
    })
  })
}

/**
 * Move file from sourcePath to target
 */
export function move(sourcePath: string, target: string): Promise<void> {
  return new Promise((resolve, reject) => {
    fs.copy(sourcePath, target, (e: any) => {
      if (e) {
        reject(e)
      } else {
        fs.remove(sourcePath, (e: any) => {
          if (e) {
            reject(e)
          } else {
            resolve()
          }
        })
      }
    })
  })
}

/**
 * From a list of declared files, return a list of missing ones.
 */
export function notExistingFiles(filesDeclared: string[]): Promise<string[]> {
  return Promise.all(filesDeclared.map(exists))
    .then((e: [string, boolean][]) => {
      return e.filter(a => {
        const [s, exist] = a
        return !exist
      })
      .map(a => {
        const [s, b] = a
        return s
      })
    })
}

function exists(file: string): Promise<[string, boolean]> {
  return new Promise<[string, boolean]>((resolve, reject) => {
    fs.access(file, (errAccess: any) => {
      if (errAccess) {
        resolve([file, false])
      } else {
        fs.stat(file, (err: any, stats: any) => {
          if (err) {
            reject(err)
          } else {
            resolve([file, stats.isFile()])
          }
        })
      }
    })
  })
}
