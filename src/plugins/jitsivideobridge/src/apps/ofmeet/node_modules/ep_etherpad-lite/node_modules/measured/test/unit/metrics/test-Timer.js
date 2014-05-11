var common    = require('../../common');
var test      = require('utest');
var assert    = require('assert');
var sinon     = require('sinon');
var Timer     = common.measured.Timer;
var Histogram = common.measured.Histogram;
var Meter     = common.measured.Meter;

var timer;
var meter;
var histogram;
test('Timer', {
  before: function() {
    meter     = sinon.stub(new Meter);
    histogram = sinon.stub(new Histogram);

    timer = new Timer({
      meter     : meter,
      histogram : histogram,
    });
  },

  'can be initialized without options': function() {
    timer = new Timer();
  },

  '#update() marks the meter': function() {
    timer.update(5);

    assert.ok(meter.mark.calledOnce);
  },

  '#update() updates the histogram': function() {
    timer.update(5);

    assert.ok(histogram.update.calledWith(5));
  },

  '#toJSON() contains meter info': function() {
    meter.toJSON.returns({a: 1, b: 2});
    var json = timer.toJSON();

    assert.deepEqual(json['meter'], {a: 1, b: 2});
  },

  '#toJSON() contains histogram info': function() {
    histogram.toJSON.returns({c: 3, d: 4});
    var json = timer.toJSON();

    assert.deepEqual(json['histogram'], {c: 3, d: 4});
  },

  '#start returns a Stopwatch which updates the timer': function() {
    var clock = sinon.useFakeTimers();
    clock.tick(10);

    var watch = timer.start();
    clock.tick(50);
    watch.end();

    assert.ok(meter.mark.calledOnce);
    assert.equal(histogram.update.args[0][0], 50);

    clock.restore();
  },

  '#reset is delegated to histogram and meter': function() {
    timer.reset();

    assert.ok(meter.reset.calledOnce);
    assert.ok(histogram.reset.calledOnce);
  },
});
