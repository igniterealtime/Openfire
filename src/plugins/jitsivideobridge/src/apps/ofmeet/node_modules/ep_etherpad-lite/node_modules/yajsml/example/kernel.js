var require = (function () {
/*!

  require-kernel

  Created by Chad Weider on 01/04/11.
  Released to the Public Domain on 17/01/12.

*/

  /* Storage */
  var main = null; // Reference to main module in `modules`.
  var modules = {}; // Repository of module objects build from `definitions`.
  var definitions = {}; // Functions that construct `modules`.
  var loadingModules = {}; // Locks for detecting circular dependencies.
  var definitionWaiters = {}; // Locks for clearing duplicate requires.
  var fetchRequests = []; // Queue of pending requests.
  var currentRequests = 0; // Synchronization for parallel requests.
  var maximumRequests = 2; // The maximum number of parallel requests.
  var deferred = []; // A list of callbacks that can be evaluated eventually.
  var deferredScheduled = false; // If deferred functions will be executed.

  var syncLock = undefined;
  var globalKeyPath = undefined;

  var rootURI = undefined;
  var libraryURI = undefined;

  var JSONP_TIMEOUT = 60 * 1000;

  function CircularDependencyError(message) {
    this.name = "CircularDependencyError";
    this.message = message;
  };
  CircularDependencyError.prototype = Error.prototype;
  function ArgumentError(message) {
    this.name = "ArgumentError";
    this.message = message;
  };
  ArgumentError.prototype = Error.prototype;

  /* Utility */
  function hasOwnProperty(object, key) {
    // Object-independent because an object may define `hasOwnProperty`.
    return Object.prototype.hasOwnProperty.call(object, key);
  }

  /* Deferral */
  function defer(f_1, f_2, f_n) {
    deferred.push.apply(deferred, arguments);
  }

  function _flushDefer() {
    // Let exceptions happen, but don't allow them to break notification.
    try {
      while (deferred.length) {
        var continuation = deferred.shift();
        continuation();
      }
      deferredScheduled = false;
    } finally {
      deferredScheduled = deferred.length > 0;
      deferred.length && setTimeout(_flushDefer, 0);
    }
  }

  function flushDefer() {
    if (!deferredScheduled && deferred.length > 0) {
      if (syncLock) {
        // Only asynchronous operations will wait on this condition so schedule
        // and don't interfere with the synchronous operation in progress.
        deferredScheduled = true;
        setTimeout(_flushDefer, 0);
      } else {
        _flushDefer();
      }
    }
  }

  function flushDeferAfter(f) {
    try {
      deferredScheduled = true;
      f();
      deferredScheduled = false;
      flushDefer();
    } finally {
      deferredScheduled = false;
      deferred.length && setTimeout(flushDefer, 0);
    }
  }

  // See RFC 2396 Appendix B
  var URI_EXPRESSION =
      /^(([^:\/?#]+):)?(\/\/([^\/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?/;
  function parseURI(uri) {
    var match = uri.match(URI_EXPRESSION);
    var location = match && {
      scheme: match[2],
      host: match[4],
      path: match[5],
      query: match[7],
      fragment: match[9]
    };
    return location;
  }

  function joinURI(location) {
    var uri = "";
    if (location.scheme)
      uri += location.scheme + ':';
    if (location.host)
      uri += "//" + location.host
    if (location.host && location.path && location.path.charAt(0) != '/')
      url += "/"
    if (location.path)
      uri += location.path
    if (location.query)
      uri += "?" + location.query
    if (uri.fragment)
      uri += "#" + location.fragment

    return uri;
  }

  function isSameDomain(uri) {
    var host_uri =
      (typeof location == "undefined") ? {} : parseURI(location.toString());
    var uri = parseURI(uri);

    return (!uri.scheme && !uri.host)
        || (uri.scheme === host_uri.scheme) && (uri.host === host_uri.host);
  }

  function mirroredURIForURI(uri) {
    var host_uri =
      (typeof location == "undefined") ? {} : parseURI(location.toString());
    var uri = parseURI(uri);

    uri.scheme = host_uri.scheme;
    uri.host = host_uri.host;
    return joinURI(uri);
  }

  function normalizePath(path) {
    var pathComponents1 = path.split('/');
    var pathComponents2 = [];

    var component;
    for (var i = 0, ii = pathComponents1.length; i < ii; i++) {
      component = pathComponents1[i];
      switch (component) {
        case '':
          if (i == 0 || i == ii - 1) {
            // This indicates a leading or trailing slash.
            pathComponents2.push(component);
          }
          break;
        case '.':
          // Always skip.
          break;
        case '..':
          if (pathComponents2.length > 1
            || (pathComponents2.length == 1
              && pathComponents2[0] != ''
              && pathComponents2[0] != '.')) {
            pathComponents2.pop();
            break;
          }
        default:
          pathComponents2.push(component);
      }
    }

    return pathComponents2.join('/');
  }

  function fullyQualifyPath(path, basePath) {
    var fullyQualifiedPath = path;
    if (path.charAt(0) == '.'
      && (path.charAt(1) == '/'
        || (path.charAt(1) == '.' && path.charAt(2) == '/'))) {
      if (!basePath) {
        basePath = '';
      } else if (basePath.charAt(basePath.length-1) != '/') {
        basePath += '/';
      }
      fullyQualifiedPath = basePath + path;
    }
    return fullyQualifiedPath;
  }

  function setRootURI(URI) {
    if (!URI) {
      throw new ArgumentError("Invalid root URI.");
    }
    rootURI = (URI.charAt(URI.length-1) == '/' ? URI.slice(0,-1) : URI);
  }

  function setLibraryURI(URI) {
    libraryURI = (URI.charAt(URI.length-1) == '/' ? URI : URI + '/');
  }

  function URIForModulePath(path) {
    var components = path.split('/');
    for (var i = 0, ii = components.length; i < ii; i++) {
      components[i] = encodeURIComponent(components[i]);
    }
    path = components.join('/')

    if (path.charAt(0) == '/') {
      if (!rootURI) {
        throw new Error("Attempt to retrieve the root module "
          + "\""+ path + "\" but no root URI is defined.");
      }
      return rootURI + path;
    } else {
      if (!libraryURI) {
        throw new Error("Attempt to retrieve the library module "
          + "\""+ path + "\" but no libary URI is defined.");
      }
      return libraryURI + path;
    }
  }

  function _compileFunction(code, filename) {
    return new Function(code);
  }

  function compileFunction(code, filename) {
    var compileFunction = rootRequire._compileFunction || _compileFunction;
    return compileFunction.apply(this, arguments);
  }

  /* Remote */
  function setRequestMaximum (value) {
    value == parseInt(value);
    if (value > 0) {
      maximumRequests = value;
      checkScheduledfetchDefines();
    } else {
      throw new ArgumentError("Value must be a positive integer.")
    }
  }

  function setGlobalKeyPath (value) {
    globalKeyPath = value;
  }

  var XMLHttpFactories = [
    function () {return new XMLHttpRequest()},
    function () {return new ActiveXObject("Msxml2.XMLHTTP")},
    function () {return new ActiveXObject("Msxml3.XMLHTTP")},
    function () {return new ActiveXObject("Microsoft.XMLHTTP")}
  ];

  function createXMLHTTPObject() {
    var xmlhttp = false;
    for (var i = 0, ii = XMLHttpFactories.length; i < ii; i++) {
      try {
        xmlhttp = XMLHttpFactories[i]();
      } catch (error) {
        continue;
      }
      break;
    }
    return xmlhttp;
  }

  function getXHR(uri, async, callback, request) {
    var request = request || createXMLHTTPObject();
    if (!request) {
      throw new Error("Error making remote request.")
    }

    function onComplete(request) {
      // Build module constructor.
      if (request.status == 200) {
        callback(undefined, request.responseText);
      } else {
        callback(true, undefined);
      }
    }

    request.open('GET', uri, !!(async));
    if (async) {
      request.onreadystatechange = function (event) {
        if (request.readyState == 4) {
          onComplete(request);
        }
      };
      request.send(null);
    } else {
      request.send(null);
      onComplete(request);
    }
  }

  function getXDR(uri, callback) {
    var xdr = new XDomainRequest();
    xdr.open('GET', uri);
    xdr.error(function () {
      callback(true, undefined);
    });
    xdr.onload(function () {
      callback(undefined, request.responseText);
    });
    xdr.send();
  }

  function fetchDefineXHR(path, async) {
    // If cross domain and request doesn't support such requests, go straight
    // to mirroring.

    var _globalKeyPath = globalKeyPath;

    var callback = function (error, text) {
      if (error) {
        define(path, null);
      } else {
        if (_globalKeyPath) {
          compileFunction(text, path)();
        } else {
          var definition = compileFunction(
              'return (function (require, exports, module) {'
            + text + '\n'
            + '})', path)();
          define(path, definition);
        }
      }
    }

    var uri = URIForModulePath(path);
    if (_globalKeyPath) {
      uri += '?callback=' + encodeURIComponent(globalKeyPath + '.define');
    }
    if (isSameDomain(uri)) {
      getXHR(uri, async, callback);
    } else {
      var request = createXMLHTTPObject();
      if (request && request.withCredentials !== undefined) {
        getXHR(uri, async, callback, request);
      } else if (async && (typeof XDomainRequest != "undefined")) {
        getXDR(uri, callback);
      } else {
        getXHR(mirroredURIForURI(uri), async, callback);
      }
    }
  }

  function fetchDefineJSONP(path) {
    var head = document.head
      || document.getElementsByTagName('head')[0]
      || document.documentElement;
    var script = document.createElement('script');
    if (script.async !== undefined) {
      script.async = "true";
    } else {
      script.defer = "true";
    }
    script.type = "application/javascript";
    script.src = URIForModulePath(path)
      + '?callback=' + encodeURIComponent(globalKeyPath + '.define');

    // Handle failure of JSONP request.
    if (JSONP_TIMEOUT < Infinity) {
      var timeoutId = setTimeout(function () {
        timeoutId = undefined;
        define(path, null);
      }, JSONP_TIMEOUT);
      definitionWaiters[path].unshift(function () {
        timeoutId === undefined && clearTimeout(timeoutId);
      });
    }

    head.insertBefore(script, head.firstChild);
  }

  /* Modules */
  function fetchModule(path, continuation) {
    if (hasOwnProperty(definitionWaiters, path)) {
      definitionWaiters[path].push(continuation);
    } else {
      definitionWaiters[path] = [continuation];
      schedulefetchDefine(path);
    }
  }

  function schedulefetchDefine(path) {
    fetchRequests.push(path);
    checkScheduledfetchDefines();
  }

  function checkScheduledfetchDefines() {
    if (fetchRequests.length > 0 && currentRequests < maximumRequests) {
      var fetchRequest = fetchRequests.pop();
      currentRequests++;
      definitionWaiters[fetchRequest].unshift(function () {
        currentRequests--;
        checkScheduledfetchDefines();
      });
      if (globalKeyPath
        && typeof document !== 'undefined'
          && document.readyState
            && /^loaded|complete$/.test(document.readyState)) {
        fetchDefineJSONP(fetchRequest);
      } else {
        fetchDefineXHR(fetchRequest, true);
      }
    }
  }

  function fetchModuleSync(path, continuation) {
    fetchDefineXHR(path, false);
    continuation();
  }

  function moduleIsLoaded(path) {
    return hasOwnProperty(modules, path);
  }

  function loadModule(path, continuation) {
    // If it's a function then it hasn't been exported yet. Run function and
    //  then replace with exports result.
    if (!moduleIsLoaded(path)) {
      if (hasOwnProperty(loadingModules, path)) {
        throw new CircularDependencyError("Encountered circular dependency.");
      } else if (!moduleIsDefined(path)) {
        throw new Error("Attempt to load undefined module.");
      } else if (definitions[path] === null) {
        continuation(null);
      } else {
        var definition = definitions[path];
        var _module = {id: path, exports: {}};
        var _require = requireRelativeTo(path);
        if (!main) {
          main = _module;
        }
        try {
          loadingModules[path] = true;
          definition(_require, _module.exports, _module);
          modules[path] = _module;
          delete loadingModules[path];
          continuation(_module);
        } finally {
          delete loadingModules[path];
        }
      }
    } else {
      var module = modules[path];
      continuation(module);
    }
  }

  function _moduleAtPath(path, fetchFunc, continuation) {
    var suffixes = ['', '.js', '/index.js'];
    if (path.charAt(path.length - 1) == '/') {
      suffixes = ['index.js'];
    }

    var i = 0, ii = suffixes.length;
    var _find = function (i) {
      if (i < ii) {
        var path_ = path + suffixes[i];
        var after = function () {
          loadModule(path_, function (module) {
            if (module === null) {
              _find(i + 1);
            } else {
              continuation(module);
            }
          });
        }

        if (!moduleIsDefined(path_)) {
          fetchFunc(path_, after);
        } else {
          after();
        }

      } else {
        continuation(null);
      }
    };
    _find(0);
  }

  function moduleAtPath(path, continuation) {
    defer(function () {
      _moduleAtPath(path, fetchModule, continuation);
    });
  }

  function moduleAtPathSync(path) {
    var module;
    var oldSyncLock = syncLock;
    syncLock = true;
    try {
      _moduleAtPath(path, fetchModuleSync, function (_module) {
        module = _module;
      });
    } finally {
      syncLock = oldSyncLock;
    }
    return module;
  }

  /* Definition */
  function moduleIsDefined(path) {
    return hasOwnProperty(definitions, path);
  }

  function defineModule(path, module) {
    if (typeof path != 'string'
      || !((typeof module == 'function') || module === null)) {
      throw new ArgumentError(
          "Definition must be a (string, function) pair.");
    }

    if (moduleIsDefined(path)) {
      // Drop import silently
    } else {
      definitions[path] = module;
    }
  }

  function defineModules(moduleMap) {
    if (typeof moduleMap != 'object') {
      throw new ArgumentError("Mapping must be an object.");
    }
    for (var path in moduleMap) {
      if (hasOwnProperty(moduleMap, path)) {
        defineModule(path, moduleMap[path]);
      }
    }
  }

  function define(fullyQualifiedPathOrModuleMap, module) {
    var moduleMap;
    if (arguments.length == 1) {
      moduleMap = fullyQualifiedPathOrModuleMap;
      defineModules(moduleMap);
    } else if (arguments.length == 2) {
      var path = fullyQualifiedPathOrModuleMap;
      defineModule(fullyQualifiedPathOrModuleMap, module);
      moduleMap = {};
      moduleMap[path] = module;
    } else {
      throw new ArgumentError("Expected 1 or 2 arguments, but got "
          + arguments.length + ".");
    }

    // With all modules installed satisfy those conditions for all waiters.
    for (var path in moduleMap) {
      if (hasOwnProperty(moduleMap, path)
        && hasOwnProperty(definitionWaiters, path)) {
        defer.apply(this, definitionWaiters[path]);
        delete definitionWaiters[path];
      }
    }

    flushDefer();
  }

  /* Require */
  function _designatedRequire(path, continuation) {
    if (continuation === undefined) {
      var module = moduleAtPathSync(path);
      if (!module) {
        throw new Error("The module at \"" + path + "\" does not exist.");
      }
      return module.exports;
    } else {
      if (!(typeof continuation == 'function')) {
        throw new ArgumentError("Continuation must be a function.");
      }

      flushDeferAfter(function () {
        moduleAtPath(path, function (module) {
          continuation(module && module.exports);
        });
      });
    }
  }

  function designatedRequire(path, continuation) {
    var designatedRequire =
        rootRequire._designatedRequire || _designatedRequire;
    return designatedRequire.apply(this, arguments);
  }

  function requireRelative(basePath, qualifiedPath, continuation) {
    qualifiedPath = qualifiedPath.toString();
    var path = normalizePath(fullyQualifyPath(qualifiedPath, basePath));
    return designatedRequire(path, continuation);
  }

  function requireRelativeN(basePath, qualifiedPaths, continuation) {
    if (!(typeof continuation == 'function')) {
      throw new ArgumentError("Final argument must be a continuation.");
    } else {
      // Copy and validate parameters
      var _qualifiedPaths = [];
      for (var i = 0, ii = qualifiedPaths.length; i < ii; i++) {
        _qualifiedPaths[i] = qualifiedPaths[i].toString();
      }
      var results = [];
      function _require(result) {
        results.push(result);
        if (qualifiedPaths.length > 0) {
          requireRelative(basePath, qualifiedPaths.shift(), _require);
        } else {
          continuation.apply(this, results);
        }
      }
      for (var i = 0, ii = qualifiedPaths.length; i < ii; i++) {
        requireRelative(basePath, _qualifiedPaths[i], _require);
      }
    }
  }

  var requireRelativeTo = function (basePath) {
    basePath = basePath.replace(/[^\/]+$/, '');
    function require(qualifiedPath, continuation) {
      if (arguments.length > 2) {
        var qualifiedPaths = Array.prototype.slice.call(arguments, 0, -1);
        var continuation = arguments[arguments.length-1];
        return requireRelativeN(basePath, qualifiedPaths, continuation);
      } else {
        return requireRelative(basePath, qualifiedPath, continuation);
      }
    }
    require.main = main;

    return require;
  }

  var rootRequire = requireRelativeTo('/');

  /* Private internals */
  rootRequire._modules = modules;
  rootRequire._definitions = definitions;
  rootRequire._designatedRequire = _designatedRequire;
  rootRequire._compileFunction = _compileFunction;

  /* Public interface */
  rootRequire.define = define;
  rootRequire.setRequestMaximum = setRequestMaximum;
  rootRequire.setGlobalKeyPath = setGlobalKeyPath;
  rootRequire.setRootURI = setRootURI;
  rootRequire.setLibraryURI = setLibraryURI;

  return rootRequire;
}())