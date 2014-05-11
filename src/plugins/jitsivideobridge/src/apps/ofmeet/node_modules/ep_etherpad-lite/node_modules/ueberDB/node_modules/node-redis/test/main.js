var c      = require('../').createClient(null, null, 'test'),
    c2     = require('../').createClient(),
    assert = require('assert');

var buffer = new Buffer(new Array(1025).join('x'));

module.exports = {
  "test basic commands": function (done) {
    c.set('1', 'test');
    c.get('1', function (error, value) {
      assert.ok(!error);
      assert.equal(value, 'test');
    });

    c.del('1', function (error) {
      assert.ok(!error);
    });

    c.get('1', function (error, value) {
      assert.ok(!error);
      assert.isNull(value);
      done();
    });

  },
  "test stress": function (done) {
    var n = 0,
        o = 0;

    for (var i = 0; i < 10000; i++) {
      c.set('2' + i, buffer, function (error) {
        assert.ok(!error);
        ++n;
      });
    }

    for (i = 0; i < 10000; i++) {
      c.del('2' + i, function (error) {
        assert.ok(!error);
        ++o;
      });
    }

    c.ping(function (error) {
      assert.ok(!error)
      done()
    })

    process.on('exit', function () {
      assert.equal(10000, n);
      assert.equal(10000, o);
    });
  },
  "test pubsub": function (done) {
    c.subscribe('test');
    c.on('subscribe:test', function (count) {
      assert.equal(1, count);
      c2.publish('test', '123', function (error) {
        assert.ok(!error);
      });
    });
    c.on('message:test', function (data) {
      assert.equal('123', data.toString());
      c.unsubscribe('test');
    });
    c.on('unsubscribe:test', function (count) {
      assert.equal(0, count);
      assert.equal(false, c.blocking);
      c.ping(function (error) {
        assert.ok(!error);
        done();
      });
    });
  },
  "test monitor": function (done) {
    c.monitor();
    c.once('data', function (data) {
      assert.ok(/MONITOR/.test(data));
      c.once('data', function (data) {
        assert.ok(/SET/.test(data));
        c.once('data', function (data) {
          assert.ok(/DEL/.test(data));
          done();
        });
      });
    });
    c2.set('test', 123);
    c2.del('test');
  },
  after: function () {
    c.quit();
    c2.quit();
  }
};
