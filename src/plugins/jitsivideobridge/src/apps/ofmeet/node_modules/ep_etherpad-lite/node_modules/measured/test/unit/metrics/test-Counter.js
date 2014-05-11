var common  = require('../../common');
var test    = require('utest');
var assert  = require('assert');
var Counter = common.measured.Counter;

var counter;
test('Counter', {
  before: function() {
    counter = new Counter();
  },

  'has initial value of 0': function() {
    var json = counter.toJSON();
    assert.deepEqual(json, 0);
  },

  'can be initialized with a given count': function() {
    var counter = new Counter({count: 5});
    assert.equal(counter.toJSON(), 5);
  },

  '#inc works incrementally': function() {
    counter.inc(5);
    assert.equal(counter.toJSON(), 5);

    counter.inc(3);
    assert.equal(counter.toJSON(), 8);
  },

  '#inc defaults to 1': function() {
    counter.inc();
    assert.equal(counter.toJSON(), 1);

    counter.inc();
    assert.equal(counter.toJSON(), 2);
  },

  '#dec works incrementally': function() {
    counter.dec(3);
    assert.equal(counter.toJSON(), -3);

    counter.dec(2);
    assert.equal(counter.toJSON(), -5);
  },

  '#dec defaults to 1': function() {
    counter.dec();
    assert.equal(counter.toJSON(), -1);

    counter.dec();
    assert.equal(counter.toJSON(), -2);
  },

  '#reset works': function() {
    counter.inc(23);
    assert.equal(counter.toJSON(), 23);

    counter.reset();
    assert.equal(counter.toJSON(), 0);

    counter.reset(50);
    assert.equal(counter.toJSON(), 50);
  }
});
