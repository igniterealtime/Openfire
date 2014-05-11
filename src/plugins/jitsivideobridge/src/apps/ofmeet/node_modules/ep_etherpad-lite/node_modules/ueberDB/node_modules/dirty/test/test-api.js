var config = require('./config');
  path = require('path'),
  fs = require('fs'),
  dirty = require(config.LIB_DIRTY),
  events = require('events'),
  assert = require('assert');

// exists moved from path to fs in node v0.7.1
// https://raw.github.com/joyent/node/v0.7.1/ChangeLog
var exists = (fs.exists) ? fs.exists : path.exists;

function dirtyAPITests(file) {
  var mode = (file) ? 'persistent' : 'transient';

  describe('dirty api (' + mode + ' mode)', function() {
    function cleanup(done) {
      exists(file, function(doesExist) {
        if (doesExist) {
          fs.unlinkSync(file);
        }

        done();
      });
    }

    before(cleanup);

    describe('dirty constructor', function() {
      var db = dirty(file);

      after(cleanup);

      it('is an event emitter', function() {
        assert.ok(db instanceof events.EventEmitter);
      });

      it('is a dirty', function() {
        assert.ok(db instanceof dirty);
      });

    });

    describe('events', function() {

      afterEach(cleanup);

      it('should fire load', function(done) {
        var db = dirty(file);
        db.on('load', function(length) {
          assert.strictEqual(length, 0);
          done();
        });
      });

      it('should fire drain after write', function(done) {
        var db = dirty(file);
        db.on('load', function(length) {
          assert.strictEqual(length, 0);

          db.set('key', 'value');
          db.on('drain', function() {
            done();
          });

        });
      });
    });

    describe('accessors', function(done) {
      after(cleanup);
      var db;

      it('.set should trigger callback', function(done) {
        db = dirty(file);
        db.set('key', 'value', function(err) {
          assert.ok(!err);
          done();
        });
      });

      it('.get should return value', function() {
        assert.strictEqual(db.get('key'), 'value');
      });

      it('.path is valid', function() {
        assert.strictEqual(db.path, file);
      });

      it('.forEach runs for all', function() {
        var total = 2, count = 0;
        db.set('key1', 'value1');
        db.set('delete', 'me');

        db.rm('delete');

        var keys = ['key', 'key1'];
        var vals = ['value', 'value1'];

        db.forEach(function(key, val) {
          assert.strictEqual(key, keys[count]);
          assert.strictEqual(val, vals[count]);

          count ++;
        });

        assert.strictEqual(count, total);
      });

      it('.rm removes key/value pair', function() {
        db.set('test', 'test');
        assert.strictEqual(db.get('test'), 'test');
        db.rm('test');
        assert.strictEqual(db.get('test'), undefined);
      });

      it('will reload file from disk', function(done) {
        if (!file) {
          console.log('N/A in transient mode');
          return done();
        }

        db = dirty(file);
        db.on('load', function(length) {
          assert.strictEqual(length, 2);
          assert.strictEqual(db.get('key'), 'value');
          assert.strictEqual(db.get('key1'), 'value1');
          done();
        });
      });
    });
    
    describe('db file close', function(done) {
      after(cleanup);
      
      it('close', function(done) {
        if (!file) {
          console.log('N/A in transient mode');
          return done();
        }
        var db = dirty(file);
        db.on('load', function(length) {
          db.set('close', 'close');
          db.on('drain', function() {
            db.close();
          });
        });

        db.on('write_close',function() {
          done();
        });
      });
    });

  });
}

dirtyAPITests('');
dirtyAPITests(config.TMP_PATH + '/apitest.dirty');
