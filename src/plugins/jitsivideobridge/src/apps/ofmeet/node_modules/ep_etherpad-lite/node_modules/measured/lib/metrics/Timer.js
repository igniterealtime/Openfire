var Histogram = require('./Histogram');
var Meter     = require('./Meter');
var Stopwatch = require('../util/Stopwatch');

module.exports = Timer;
function Timer(properties) {
  properties = properties || {};

  this._meter     = properties.meter || new Meter;
  this._histogram = properties.histogram || new Histogram;
}

Timer.prototype.start = function() {
  var self  = this;
  var watch = new Stopwatch();

  watch.once('end', function(elapsed) {
    self.update(elapsed);
  });

  return watch;
};

Timer.prototype.update = function(value) {
  this._meter.mark();
  this._histogram.update(value);
};


Timer.prototype.reset = function() {
  this._meter.reset();
  this._histogram.reset();
};

Timer.prototype.end = function() {
  this._meter.end();
};

Timer.prototype.toJSON = function() {
  var self   = this;
  var result = {};

  ['meter', 'histogram'].forEach(function(metric) {
    var json       = self['_' + metric].toJSON();
    result[metric] = {};

    for (var key in json) {
      result[metric][key] = json[key];
    }
  });

  return result;
};
