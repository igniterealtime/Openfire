var units = require('../util/units');
var EWMA  = require('../util/ExponentiallyMovingWeightedAverage');

module.exports = Meter;
function Meter(properties) {
  properties = properties || {};

  this._rateUnit     = properties.rateUnit || Meter.RATE_UNIT;
  this._tickInterval = properties.tickInterval || Meter.TICK_INTERVAL;

  this._m1Rate     = new EWMA(1 * units.MINUTES, this._tickInterval);
  this._m5Rate     = new EWMA(5 * units.MINUTES, this._tickInterval);
  this._m15Rate    = new EWMA(15 * units.MINUTES, this._tickInterval);
  this._count      = 0;
  this._currentSum = 0;
  this._lastToJSON = null
  this._interval   = null;
  this._startTime  = null;
}

Meter.RATE_UNIT     = units.SECONDS;
Meter.TICK_INTERVAL = 5 * units.SECONDS;

Meter.prototype.mark = function(n) {
  if (!this._interval) this.start();

  n = n || 1;

  this._count += n;
  this._currentSum += n;
  this._m1Rate.update(n);
  this._m5Rate.update(n);
  this._m15Rate.update(n);
};

Meter.prototype.start = function() {
  this._interval   = setInterval(this._tick.bind(this), Meter.TICK_INTERVAL);
  this._startTime  = Date.now();
  this._lastToJSON = Date.now();
};

Meter.prototype.end = function() {
  clearInterval(this._interval);
};

Meter.prototype._tick = function() {
  this._m1Rate.tick();
  this._m5Rate.tick();
  this._m15Rate.tick();
};

Meter.prototype.reset = function() {
  this.end();
  this.constructor.call(this);
};

Meter.prototype.meanRate = function() {
  if (this._count === 0) return 0;

  var elapsed = Date.now() - this._startTime;
  return this._count / elapsed * this._rateUnit;
};

Meter.prototype.currentRate = function() {
  var currentSum  = this._currentSum;
  var duration    = Date.now() - this._lastToJSON;
  var currentRate = currentSum / duration * this._rateUnit;

  this._currentSum = 0;
  this._lastToJSON = Date.now();

  // currentRate could be NaN if duration was 0, so fix that
  return currentRate || 0;
};

Meter.prototype.toJSON = function() {
  return {
    'mean'         : this.meanRate(),
    'count'        : this._count,
    'currentRate'  : this.currentRate(),
    '1MinuteRate'  : this._m1Rate.rate(this._rateUnit),
    '5MinuteRate'  : this._m5Rate.rate(this._rateUnit),
    '15MinuteRate' : this._m15Rate.rate(this._rateUnit),
  };
};
