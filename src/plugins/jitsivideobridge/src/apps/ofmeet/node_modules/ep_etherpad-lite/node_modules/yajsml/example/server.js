#! /usr/bin/node
/*!

  Copyright (c) 2011 Chad Weider

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

*/

var fs = require('fs');
var connect = require('connect');
var cors = require('connect-cors');
var request = require('request');

// This needs to be a package.
var UglifyMiddleware = require('./uglify-middleware');
var compressor = new UglifyMiddleware();
compressor._console = console;

var Yajsml = require('yajsml');
var Server = Yajsml.Server;
var associators = Yajsml.associators;

var configuration;
for (var i = 1, ii = process.argv.length; i < ii; i++) {
  if (process.argv[i] == '--configuration') {
    var configPath = process.argv[i+1];
    if (!configPath) {
      throw new Error("Configuration option specified, but no path given.");
    } else {
      configuration = JSON.parse(fs.readFileSync(configPath));
    }
  }
}

if (!configuration) {
  throw new Error("No configuration option given.");
}

var assetServer = connect.createServer()
  .use(cors({
      origins: ['*']
    , methods: ['HEAD', 'GET']
    , headers: [
        'content-type'
      , 'accept'
      , 'date'
      , 'if-modified-since'
      , 'last-modified'
      , 'expires'
      , 'etag'
      , 'cache-control'
      ]
    }))
  .use(connect.cookieParser())
  ;

if (configuration['minify']) {
  assetServer.use(compressor);
}

function interpolatePath(path, values) {
  return path && path.replace(/(\/)?:(\w+)/, function (_, slash, key) {
    return (slash ? '/' : '') + encodeURIComponent(String((values || {})[key]));
  });
}

function interpolateURL(url, values) {
  var parsed = require('url').parse(url);
  if (parsed) {
    parsed.pathname = interpolatePath(parsed.pathname, values);
  }
  return require('url').format(parsed);
}

function handle(req, res, next) {
  var instanceConfiguration = {
    rootPath: configuration['rootPath'] && interpolatePath(configuration['rootPath'], req.params)
  , rootURI: configuration['rootURI'] && interpolateURL(configuration['rootURI'], req.params)
  , libraryPath: configuration['libraryPath'] && interpolatePath(configuration['libraryPath'], req.params)
  , libraryURI: configuration['libraryURI'] && interpolateURL(configuration['libraryURI'], req.params)
  };
  var instance = new (Yajsml.Server)(instanceConfiguration);

  if (configuration['manifest']) {
    request({
        url: interpolateURL(configuration['manifest'], req.params)
      , method: 'GET'
      , encoding: 'utf8'
      , timeout: 2000
      }
    , function (error, res, content) {
      if (error || res.statusCode != '200') {
        // Silently use default associator
        instance.setAssociator(new (associators.SimpleAssociator)());
      } else {
        try {
          var manifest = JSON.parse(content);
          var associations =
              associators.associationsForSimpleMapping(manifest);
          var associator = new (associators.StaticAssociator)(associations);
          instance.setAssociator(associator);
        } catch (e) {
          instance.setAssociator(new (associators.SimpleAssociator)());
        }
      }
      respond();
    });
  } else {
    instance.setAssociator(new (associators.SimpleAssociator)());
    respond();
  }

  function respond() {
    instance.handle(req, res, next);
  }
}
assetServer.use(connect.router(function (app) {
  configuration['rootPath'] && app.all(configuration['rootPath'] + '/*', handle);
  configuration['libraryPath'] && app.all(configuration['libraryPath'] + '/*', handle);
}));

assetServer.listen(configuration['port'] || 8450);
