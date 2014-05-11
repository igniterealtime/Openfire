var Collection = exports.Collection = require('./lib/Collection');

var metrics = require('./lib/metrics');
for (var name in metrics) {
  exports[name] = metrics[name];
}

var util = require('./lib/util');
for (var name in util) {
  exports[name] = util[name];
}

exports.createCollection = function(name) {
  return new Collection(name);
};
