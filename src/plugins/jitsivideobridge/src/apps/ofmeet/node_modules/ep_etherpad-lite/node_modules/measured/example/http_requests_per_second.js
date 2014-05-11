var metrics    = require('..');
var collection = new metrics.Collection('http');
var http       = require('http');

var rps = collection.meter('requestsPerSecond');
http.createServer(function(req, res) {
  console.error(req.headers['content-length']);
  rps.mark();
  res.end('Thanks');
}).listen(3000);

setInterval(function() {
  console.log(collection.toJSON());
}, 1000);
