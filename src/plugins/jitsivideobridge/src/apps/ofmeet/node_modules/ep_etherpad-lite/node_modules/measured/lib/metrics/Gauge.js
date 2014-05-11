module.exports = Gauge;
function Gauge(readFn) {
  this._readFn = readFn;
}

// This is sync for now, but maybe async gauges would be useful as well?
Gauge.prototype.toJSON = function() {
  return this._readFn();
};
