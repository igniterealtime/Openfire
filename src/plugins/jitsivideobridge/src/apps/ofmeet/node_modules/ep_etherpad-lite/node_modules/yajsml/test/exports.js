var http = require('http');
var connect = require('connect');
var Yajsml = require('yajsml');
var urlutil = require('url');

var resourceHandler = function (req, res, next) {
  var resources = resourceHandler.resources || {};
  var url = urlutil.parse(req.url);
  var resource = resources[url.path];
  
  req.writeHead(resource.status, resource.header);
  setTimeout(function () {
    req.end(resource.content);    
  }, 0);
};

var resourceServer = http.createServer(resourceHandler);
resourceServer.listen(9999); // Unlisten somewhere...
resourceServerURL = 'http://localhost:9999/';

describe('yajsml', function(){
  var app = connect();
  var yajsml = new (Yajsml.Server)({
    rootPath: 'src'
  , rootURL: resourceServerURL + 'src'
  , libraryPath: 'lib'
  , libraryURL: resourceServerURL + 'lib'
  });
  app.use(yajsml);

  describe('simple request', function(){
    resourceHandler.resources = {
      {
        status: 200
      , headers: {'content-type': 'text/plain'}
      , content: 'asdf'
      }
    }

    it('should ', function(){
      app.request()
      .get('/src')
      .end(function (res) {
        res.getHeader('content-type').should.equal('application/javascript');
      });
    })
  });
});
