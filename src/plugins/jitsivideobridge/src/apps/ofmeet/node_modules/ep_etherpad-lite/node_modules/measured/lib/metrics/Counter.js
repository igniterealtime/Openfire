module.exports = Counter;
function Counter(properties) {
  properties = properties || {};

  this._count = properties.count || 0;
}

Counter.prototype.toJSON = function() {
  return this._count;
};

Counter.prototype.inc = function(n) {
  this._count += (n || 1);
};

Counter.prototype.dec = function(n) {
  this._count -= (n || 1);
};

Counter.prototype.reset = function(count) {
  this._count = count || 0;
};
