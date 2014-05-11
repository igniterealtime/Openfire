var common    = require('../../common');
var test      = require('utest');
var assert    = require('assert');
var sinon     = require('sinon');
var Histogram = common.measured.Histogram;
var EDS       = common.measured.ExponentiallyDecayingSample;

var histogram;
test('Histogram', {
  before: function() {
    histogram = new Histogram;
  },

  'all values are null in the beginning': function() {
    var json = histogram.toJSON();
    assert.strictEqual(json['min'], null);
    assert.strictEqual(json['max'], null);
    assert.strictEqual(json['sum'], 0);
    assert.strictEqual(json['variance'], null);
    assert.strictEqual(json['mean'], 0);
    assert.strictEqual(json['stddev'], null);
    assert.strictEqual(json['count'], 0);
    assert.strictEqual(json['median'], null);
    assert.strictEqual(json['p75'], null);
    assert.strictEqual(json['p95'], null);
    assert.strictEqual(json['p99'], null);
    assert.strictEqual(json['p999'], null);
  },
});

var sample;
test('Histogram#update', {
  before: function() {
    sample    = sinon.stub(new EDS);
    histogram = new Histogram({sample: sample});

    sample.toArray.returns([]);
  },

  'updates underlaying sample': function() {
    histogram.update(5);
    assert.ok(sample.update.calledWith(5));
  },

  'keeps track of min': function() {
    histogram.update(5);
    histogram.update(3);
    histogram.update(6);

    assert.equal(histogram.toJSON().min, 3);
  },

  'keeps track of max': function() {
    histogram.update(5);
    histogram.update(9);
    histogram.update(3);

    assert.equal(histogram.toJSON().max, 9);
  },

  'keeps track of sum': function() {
    histogram.update(5);
    histogram.update(1);
    histogram.update(12);

    assert.equal(histogram.toJSON().sum, 18);
  },

  'keeps track of count': function() {
    histogram.update(5);
    histogram.update(1);
    histogram.update(12);

    assert.equal(histogram.toJSON().count, 3);
  },

  'keeps track of mean': function() {
    histogram.update(5);
    histogram.update(1);
    histogram.update(12);

    assert.equal(histogram.toJSON().mean, 6);
  },

  'keeps track of variance (example without variance)': function() {
    histogram.update(5);
    histogram.update(5);
    histogram.update(5);

    assert.equal(histogram.toJSON().variance, 0);
  },

  'keeps track of variance (example with variance)': function() {
    histogram.update(1);
    histogram.update(2);
    histogram.update(3);
    histogram.update(4);

    assert.equal(histogram.toJSON().variance.toFixed(3), '1.667');
  },

  'keeps track of stddev': function() {
    histogram.update(1);
    histogram.update(2);
    histogram.update(3);
    histogram.update(4);

    assert.equal(histogram.toJSON().stddev.toFixed(3), '1.291');
  },

  'keeps track of percentiles': function() {
    var values = [];
    for (var i = 1; i <= 100; i++) values.push(i);
    sample.toArray.returns(values);

    var json = histogram.toJSON();
    assert.equal(json['median'].toFixed(3), '50.500');
    assert.equal(json['p75'].toFixed(3), '75.750');
    assert.equal(json['p95'].toFixed(3), '95.950');
    assert.equal(json['p99'].toFixed(3), '99.990');
    assert.equal(json['p999'].toFixed(3), '100.000');
  },
});

test('Histogram#percentiles', {
  before: function() {
    sample    = sinon.stub(new EDS);
    histogram = new Histogram({sample: sample});

    var values = [];
    for (var i = 1; i <= 100; i++) {
      values.push(i);
    }

    for (var i = 0; i < 100; i++) {
      var swapWith = Math.floor(Math.random() * 100);
      var value = values[i];

      values[i] = values[swapWith];
      values[swapWith] = value;
    }

    sample.toArray.returns(values);
  },

  'calculates single percentile correctly': function() {
    var percentiles = histogram.percentiles([0.5]);
    assert.equal(percentiles[0.5], 50.5);

    var percentiles = histogram.percentiles([0.99]);
    assert.equal(percentiles[0.99], 99.99);
  },
});

test('Histogram#reset', {
  before: function() {
    histogram = new Histogram({sample: sample});
  },

  'resets all values': function() {
    histogram.update(5);
    histogram.update(2);
    var json = histogram.toJSON();

    for (var key in json) {
      var value = json[key];
      assert.ok(typeof value === 'number');
    }

    histogram.reset();
    var json = histogram.toJSON();

    for (var key in json) {
      var value = json[key];
      assert.ok(value === 0 || value === null);
    }
  },
});
