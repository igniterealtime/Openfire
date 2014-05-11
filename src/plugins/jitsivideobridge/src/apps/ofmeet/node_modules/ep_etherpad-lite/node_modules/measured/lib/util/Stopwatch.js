var util         = require('util');
var EventEmitter = require('events').EventEmitter;

module.exports = Stopwatch;
util.inherits(Stopwatch, EventEmitter);
function Stopwatch() {
  EventEmitter.call(this);

  this._start = Date.now();
  this._ended = false;
}

Stopwatch.prototype.end = function() {
  if (this._ended) return;

  this._ended = true;
  var elapsed   = Date.now() - this._start;

  this.emit('end', elapsed);
  return elapsed;
};
