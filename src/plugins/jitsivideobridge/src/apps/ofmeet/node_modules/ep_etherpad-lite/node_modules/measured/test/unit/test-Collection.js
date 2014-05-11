var common = require('../common');
var test   = require('utest');
var assert = require('assert');

var collection;
test('Collection', {
  before: function() {
    collection = common.measured.createCollection();
  },

  'with two counters': function() {
    var collection = new common.measured.Collection('counters');
    var a = collection.counter('a');
    var b = collection.counter('b');

    a.inc(3);
    b.inc(5);

    assert.deepEqual(collection.toJSON(), {
      'counters': {
        'a': 3,
        'b': 5,
      }
    });
  },

  'returns same metric object when given the same name': function() {
    var a1 = collection.counter('a');
    var a2 = collection.counter('a');

    assert.strictEqual(a1, a2);
  },

  'throws exception when creating a metric without name': function() {
    assert.throws(function() {
      collection.counter();
    }, /Collection.NoMetricName/);
  },
});
