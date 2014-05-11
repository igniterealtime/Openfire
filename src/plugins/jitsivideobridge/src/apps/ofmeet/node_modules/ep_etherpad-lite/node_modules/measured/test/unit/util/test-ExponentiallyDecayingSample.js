var common = require('../../common');
var test   = require('utest');
var assert = require('assert');
var EDS    = common.measured.ExponentiallyDecayingSample;
var units  = common.measured.units;

var sample;
test('ExponentiallyDecayingSample#toSortedArray', {
  before: function() {
    sample = new EDS({
      size: 3,
      random: function() {
        return 1;
      }
    });
  },

  'returns an empty array by default': function() {
    assert.deepEqual(sample.toSortedArray(), []);
  },

  'is always sorted by priority': function() {
    sample.update('a', Date.now() + 3000);
    sample.update('b', Date.now() + 2000);
    sample.update('c', Date.now() + 0);

    assert.deepEqual(sample.toSortedArray(), ['c', 'b', 'a']);
  },
});

var sample;
test('ExponentiallyDecayingSample#toArray', {
  before: function() {
    sample = new EDS({
      size: 3,
      random: function() {
        return 1;
      }
    });
  },

  'returns an empty array by default': function() {
    assert.deepEqual(sample.toArray(), []);
  },

  'may return an unsorted array': function() {
    sample.update('a', Date.now() + 3000);
    sample.update('b', Date.now() + 2000);
    sample.update('c', Date.now() + 0);

    assert.deepEqual(sample.toArray(), ['c', 'a', 'b']);
  },
});

var sample;
test('ExponentiallyDecayingSample#update', {
  before: function() {
    sample = new EDS({
      size: 2,
      random: function() {
        return 1;
      }
    });
  },

  'can add one item': function() {
    sample.update('a');

    assert.deepEqual(sample.toSortedArray(), ['a']);
  },

  'sorts items according to priority ascending': function() {
    sample.update('a', Date.now() + 0);
    sample.update('b', Date.now() + 1000);

    assert.deepEqual(sample.toSortedArray(), ['a', 'b']);
  },

  'pops items with lowest priority': function() {
    sample.update('a', Date.now() + 0);
    sample.update('b', Date.now() + 1000);
    sample.update('c', Date.now() + 2000);

    assert.deepEqual(sample.toSortedArray(), ['b', 'c']);
  },

  'items with too low of a priority do not make it in': function() {
    sample.update('a', Date.now() + 1000);
    sample.update('b', Date.now() + 2000);
    sample.update('c', Date.now() + 0);

    assert.deepEqual(sample.toSortedArray(), ['a', 'b']);
  },
});

var sample;
test('ExponentiallyDecayingSample#_rescale', {
  before: function() {
    sample = new EDS({
      size: 2,
      random: function() {
        return 1;
      }
    });
  },

  'works as expected': function() {
    sample.update('a', Date.now() + 50 * units.MINUTES);
    sample.update('b', Date.now() + 55 * units.MINUTES);

    var elements = sample._elements.toSortedArray();
    assert.ok(elements[0].priority > 1000);
    assert.ok(elements[1].priority > 1000);

    sample._rescale(Date.now() + 60 * units.MINUTES);

    elements = sample._elements.toSortedArray();
    assert.ok(elements[0].priority < 1);
    assert.ok(elements[0].priority > 0);
    assert.ok(elements[1].priority < 1);
    assert.ok(elements[1].priority > 0);
  },
});
