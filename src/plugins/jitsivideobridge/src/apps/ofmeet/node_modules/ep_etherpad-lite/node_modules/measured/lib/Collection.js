var metrics = require('./metrics');

module.exports = Collection;
function Collection(name) {
  this.name     = name;
  this._metrics = {};
}

Collection.prototype.register = function(name, metric) {
  this._metrics[name] = metric;
};

Collection.prototype.toJSON = function() {
  var json = {};

  for (var metric in this._metrics) {
    json[metric] = this._metrics[metric].toJSON();
  }

  if (!this.name) return json;

  var wrapper = {};
  wrapper[this.name] = json;

  return wrapper;
};

Collection.prototype.end = function end() {
  var metrics = this._metrics;
  Object.keys(metrics).forEach(function(name) {
    var metric = metrics[name];
    if (metric.end) {
      metric.end();
    }
  });
};

Object
  .keys(metrics)
  .forEach(function(name) {
    var MetricConstructor = metrics[name];
    var method = name.substr(0, 1).toLowerCase() + name.substr(1);

    Collection.prototype[method] = function(name, properties) {
      if (!name) throw new Error('Collection.NoMetricName');

      if (this._metrics[name]) return this._metrics[name];

      var metric = new MetricConstructor(properties);
      this.register(name, metric);
      return metric;
    };
  });
