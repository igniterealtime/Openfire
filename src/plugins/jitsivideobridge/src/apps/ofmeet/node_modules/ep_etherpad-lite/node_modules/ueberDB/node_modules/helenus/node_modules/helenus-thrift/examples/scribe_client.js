/* HOW TO prepare gen-nodejs
 $ thrift -I ~/tmp/scribe -I ~/tmp/thrift-0.6.0/contrib --gen js:node ~/tmp/scribe/if/scribe.thrift 
 $ thrift -I ~/tmp/scribe -I ~/tmp/thrift-0.6.0/contrib --gen js:node ~/tmp/scribe/if/bucketupdater.thrift 
 $ thrift -I ~/tmp/scribe -I ~/tmp/thrift-0.6.0/contrib --gen js:node ~/tmp/thrift-0.6.0/contrib/fb303/if/fb303.thrift
 */
var thrift = require('thrift'),
    scribe = require('./gen-nodejs/scribe'),
    ttypes = require('./gen-nodejs/scribe_types');

var connection = thrift.createConnection('localhost', 1463),
    client = thrift.createClient(scribe, connection);

var counter = 0;

var str_times = function(str,times) {
  var r = '';
  for (var i = 0; i < times; i++) {
    r += str;
  }
  return r;
};

var push_lines = function() {
  var num = Math.floor(Math.random() * 100);
  var now = '[' + (new Date()).toString() + ']';
  var logheader = '192.168.0.1 - - ' + now + ' ';
  var lines = [];
  for (var i = 0; i < num; i++) {
    lines.push(logheader + (counter + i) + ' ' + str_times('x', Math.floor(Math.random() * 100)) + "\n");
  }
  var logs = lines.map(function(x){ return new ttypes.LogEntry({category: 'thrifttest', message: x}); });
  console.log("transferring...", logs.length);
  var result = client.Log(logs, function(err, success){ if (! err){ counter += num; }});
};

var loop_id = setInterval(push_lines, 1000);

setTimeout(function() {
  clearInterval(loop_id);
  connection.end();
  console.log("transferred:", counter);
}, 100 * 1000);
