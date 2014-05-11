var common    = require('../../common');
var test      = require('utest');
var assert    = require('assert');
var Stopwatch = common.measured.Stopwatch;
var sinon     = require('sinon');

var watch;
var clock;
test('Stopwatch', {
  before: function() {
    clock = sinon.useFakeTimers();
    watch = new Stopwatch();
  },

  after: function() {
    clock.restore();
  },

  'returns time on end': function() {
    clock.tick(10);

    var watch = new Stopwatch();
    clock.tick(100);

    var elapsed = watch.end();
    assert.equal(elapsed, 100);
  },

  'emits time on end': function() {
    var watch = new Stopwatch();
    clock.tick(20);

    var time;
    watch.on('end', function(_time) {
      time = _time;
    });

    watch.end();

    assert.equal(time, 20);
  },

  'becomes useless after being ended once': function() {
    var watch = new Stopwatch();
    clock.tick(20);

    var time;
    watch.on('end', function(_time) {
      time = _time;
    });

    assert.equal(watch.end(), 20);
    assert.equal(time, 20);

    time = null;
    assert.equal(watch.end(), undefined);
    assert.equal(time, null);
  },
});
