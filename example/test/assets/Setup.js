// setup require.js
global.requirejs = require("requirejs")
requirejs.config({
    nodeRequire: require,
    baseUrl: __dirname
})

// setup amd-loader to work in node
require("./amd-loader")

// global test modules
global.assert = require("assert")
