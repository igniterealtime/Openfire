var https = require('../').https,
  http = require('../').http,
  nativeHttps = require('https'),
  nativeHttp = require('http');

require('colors');

var urls = [
  'http://bit.ly/900913'
  /*,
  {
    type: 'https',
    host: 'bitly.com',
    path: '/UHfDGO',
    maxRedirects: 10
  }
 */
];

require('../').maxRedirects = 6;


var libs = {
  http: {
    //native: nativeHttp,
    follow: http
  },
  https: {
    //native: nativeHttps,
    follow: https
  }
};

urls.forEach(function (url) {

  var proto = 'http';
  if (typeof url === 'string' && url.substr(0, 5) === 'https') {
      proto = 'https';
  }
  else if (url.type === 'https') {
      proto = 'https';
  }
  for (var key in libs[proto]) {
    var lib = libs[proto][key];
    /**
     * Test .get
     */
    console.log((proto + '.' + 'get(' + url + ')').blue);
    lib.get(url, function(res) {
      //console.log('statusCode: ', res.statusCode);
      //console.log('headers: ', res.headers);

      res.on('data', function(d) {
        console.log(('Data received ').red);
        console.log(d.toString());
      });

    }).on('error', function(e) {
      console.error(e);
    });

    /**
     * Test .request
     */
    console.log((proto + '.' + 'request(' + url + ')').blue);
    var request = http.request;
    var req = request(url, function(res) {
      //console.log('STATUS: ' + res.statusCode);
      //console.log('HEADERS: ' + JSON.stringify(res.headers));
      res.setEncoding('utf8');
      res.on('data', function (chunk) {
        console.log('BODY: ' + chunk);
      });
    });

    req.on('error', function(e) {
      console.log('problem with request: ' + e.message);
    });

    req.end();
  };
});
