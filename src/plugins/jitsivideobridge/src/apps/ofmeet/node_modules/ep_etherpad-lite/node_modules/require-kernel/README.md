# require-kernel #

This is an implementation of the [CommonJS module standard](http://wiki.commonjs.org/wiki/Modules/1.1) for a browser environment.

## Usage ##

The kernel is a code fragment that evaluates to an unnamed function.

### Interface ###

Modules can be loaded either synchronously and asynchronously:

* `module = require(path)`
* `require(path1[, path2[, ...]], function (module1[, module2[, ...]]) {})`

The kernel has the following methods:

* `define`: A method for defining modules. It may be invoked one of several ways. In either case the path is expected to be fully qualified and the module a function with the signature `(require, exports, module)`.
  * `require.define(path, module)`
  * `require.define({path1: module1[, path2: module2[, ...]]})`
* `setGlobalKeyPath`: A string (such as `"require"` and `"namespace.req"`) that evaluates to the kernel in the global scope. Asynchronous retrieval of modules using JSONP will happen if and only if this path is defined. Default is `undefined`.
* `setRootURI`: The URI that non-library paths will be requested relative to. Default is `undefined`.
* `setLibraryURI`: The URI that library paths (i.e. paths that do not match `/^\.{0,2}\//`) will be requested relative to. Default is `undefined`.
* `setRequestMaximum`: The maximum number of concurrent requests. Default is `2`.

## Behavior ##

### JSONP ###

If a global key path was set for the kernel and the request is allowed to be asynchronous, a JSONP will be used to request the module. The callback parameter sent in the request is the `define` method of `require` (as specified by the global key path).

### Cross Origin Resources ###

JSONP accomplishes CORS, so if such a request is possible to make, it is made, else, if the user agent is capable of such a request, requests to cross origin resources can be made, if not (IE[6,7]), the kernel will attempt to make a request to a mirrored location on the same origin (`http://static.example.com/javascripts/index.js` becomes `http://www.example.com/javascripts/index.js`).

## License ##

Released to the public domain. In any regions where transfer the public domain is not possible the software is granted under the terms of the MIT License.
