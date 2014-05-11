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
var urlutil = require('url');
var pathutil = require('path');

var mime = undefined;
try {
  mime = require('mime');
} catch (e) {
  // skip.
}

var fs_client = (new function () {
  var STATUS_MESSAGES = {
    403: '403: Access denied.'
  , 404: '404: File not found.'
  , 405: '405: Only the HEAD or GET methods are allowed.'
  , 502: '502: Error reading file.'
  };

  function request(options, callback) {
    var path = options.path;
    path = decodeURIComponent(path);
    if (path.charAt(0) == '/') { // Account for '/C:\Windows' type of paths.
      path = pathutil.resolve('/', path.slice(1));
    }
    path = pathutil.normalize(path);

    var method = options.method;

    var response = new (require('events').EventEmitter);
    response.setEncoding = function (encoding) {this._encoding = encoding};
    response.statusCode = 504;
    response.headers = {};

    var request = new (require('events').EventEmitter);
    request.end = function () {
      if (options.method != 'HEAD' && options.method != 'GET') {
        response.statusCode = 405;
        response.headers['allow'] = 'HEAD, GET';

        callback(response);
        response.emit('data', STATUS_MESSAGES[response.statusCode])
        response.emit('end');
      } else {
        function head() {
          fs.lstat(path, function (error, stats) {
            if (error) {
              if (error.code == 'ENOENT') {
                response.statusCode = 404;
                var parentTries = 2;
                var statParent = function (path) {
                  var parentPath = pathutil.dirname(path);
                  fs.stat(parentPath, function (error, stats) {
                    if (!error) {
                      var date = new Date();
                      var modifiedLast = new Date(stats.mtime);
                      response.headers['date'] = date.toUTCString();
                      response.headers['last-modified'] =
                          modifiedLast.toUTCString();
                      after_head();
                    } else if (parentTries > 0 || parentPath == '/') {
                      parentTries--;
                      statParent(parentPath);
                    } else if (error.code == 'ENOENT') {
                      response.statusCode = 404;
                    } else {
                      response.statusCode = 502;
                      after_head();
                    }
                  });
                };
                statParent(path);
              } else if (error.code == 'EACCESS') {
                response.statusCode = 403;
                after_head();
              } else {
                response.statusCode = 502;
                after_head();
              }
            } else if (stats.isFile()) {
              var date = new Date();
              var modifiedLast = new Date(stats.mtime);
              var modifiedSince = (options.headers || {})['if-modified-since'];
              modifiedSince = modifiedSince && new Date(modifiedSince);

              response.headers['date'] = date.toUTCString();
              response.headers['last-modified'] = modifiedLast.toUTCString();

              if (modifiedSince && modifiedLast
                  && modifiedSince >= modifiedLast) {
                response.statusCode = 304;
              } else {
                response.statusCode = 200;
              }
              after_head();
            } else if (stats.isSymbolicLink()) {
              var date = new Date();
              var modifiedLast = new Date(stats.mtime);
              response.headers['date'] = date.toUTCString();
              response.headers['last-modified'] = modifiedLast.toUTCString();

              fs.readlink(path, function (error, linkString) {
                if (!error) {
                  response.statusCode = 307;
                  response.headers['location'] = linkString;
                } else {
                  response.statusCode = 502;
                }
                after_head();
              });
            } else {
              response.statusCode = 404;
              after_head();
            }
          });
        }
        function after_head() {
          if (method == 'HEAD') {
            callback(response);
            response.emit('end');
          } else if (response.statusCode != 200) {
            if (STATUS_MESSAGES[response.statusCode]) {
              response.headers['content-type'] = 'text/plain; charset=utf-8';
            }

            callback(response);
            if (STATUS_MESSAGES[response.statusCode]) {
              response.emit('data', STATUS_MESSAGES[response.statusCode]);
            }
            response.emit('end');
          } else {
            get();
          }
        }
        function get() {
          response.statusCode = 200;
          var type, charset;
          if (mime) {
            type = mime.lookup(path);
            charset = mime.charsets.lookup(type);
          } else {
            type = 'application/octet-stream';
          }
          response.headers['content-type'] =
              type + (charset ? '; charset=' + charset : '');

          var stream = fs.createReadStream(path);
          stream.statusCode = response.statusCode;
          stream.headers = response.headers;
          response = stream;

          callback(response);
          stream.resume();
        }
      }

      head();
    };
    return request;
  }
  this.request = request;
}());

/* Retrieve file, http, or https resources. */
/* All of just just because normal Request.js doesn't support `file://`? */
function requestURI(url, method, headers, callback, redirectCount) {
  var parsedURL = urlutil.parse(url);
  var client = undefined;
  if (parsedURL.protocol == 'file:') {
    client = fs_client;
  } else if (parsedURL.protocol == 'http:') {
    client = require('http');
  } else if (parsedURL.protocol == 'https:') {
    client = require('https');
  } else {
    throw new Error(
      "No implementation for this resource's protocol: " + parsedURL.protocol);
  }

  var request = client.request({
    hostname: parsedURL.hostname
  , port: parsedURL.port
  , path: parsedURL.path
  , method: method
  , headers: headers
  }, function (response) {
    var buffer = undefined;
    var ended = false;
    var closed = false;
    if (response.statusCode == 301
        || response.statusCode == 302
        || response.statusCode == 307) {
      response.close();
      redirectCount = redirectCount || 0;
      if (redirectCount > 3) {
        // Is this correct HTTP behavior?
        callback(504, {}, undefined);
      } else {
        try {
          redirectCount
          requestURI(
            response.headers['location']
          , 'GET'
          , headers
          , callback
          , redirectCount+1
          );
        } catch (e) {
          callback(502, {}, undefined);
        }
      }
    } else {
      response.setEncoding('utf8');
      response.on('data', function (chunk) {
        buffer = buffer || '';
        buffer += chunk;
      });
      response.on('close', function () {
        closed = true
        !ended && callback(502, {});
      });
      response.on('end', function () {
        ended = true;
        !closed && callback(response.statusCode, response.headers, buffer);
      });
    }
  });
  request.on('error', function () {
    callback(502, {});
  });
  request.end();
}

function requestURIs(locations, method, headers, callback) {
  var pendingRequests = locations.length;
  var responses = [];

  function respondFor(i) {
    return function (status, headers, content) {
      responses[i] = [status, headers, content];
      if (--pendingRequests == 0) {
        completed();
      }
    };
  }

  for (var i = 0, ii = locations.length; i < ii; i++) {
    requestURI(locations[i], method, headers, respondFor(i));
  }

  function completed() {
    var statuss = responses.map(function (x) {return x[0]});
    var headerss = responses.map(function (x) {return x[1]});
    var contentss = responses.map(function (x) {return x[2]});
    callback(statuss, headerss, contentss);
  };
}

exports.requestURI = requestURI;
exports.requestURIs = requestURIs;

