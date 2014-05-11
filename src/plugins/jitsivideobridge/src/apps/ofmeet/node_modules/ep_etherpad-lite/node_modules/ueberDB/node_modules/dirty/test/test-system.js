var config = require('./config'),
  fs = require('fs'),
  assert = require('assert'),
  dirty = require(config.LIB_DIRTY);

describe('test-flush', function() {
  var file = config.TMP_PATH + '/flush.dirty';

  afterEach(function() {
    fs.unlinkSync(file);
  });

  it ('should fire drain event on write', function(done) {
    var db = dirty(file);
    db.set('foo', 'bar');
    db.on('drain', function() {
      done();
    });
  });

  it ('should write to disk appropriately', function(done) {
    var db = dirty(file);
    db.set('foo1', 'bar1');
    db.on('drain', function() {
      var contents = fs.readFileSync(file, 'utf-8');

      assert.strictEqual(
        contents,
        JSON.stringify({key: 'foo1', val: 'bar1'})+'\n'
      );

      done();
    });
  });

});

describe('test-for-each', function() {
  var db = dirty();

  db.set(1, {test: 'foo'});
  db.set(2, {test: 'bar'});
  db.set(3, {test: 'foobar'});

  it('should return each doc key and contents', function() {
    var i = 0;
    db.forEach(function(key, doc) {
      i++;
      assert.equal(key, i);
      assert.strictEqual(doc, db.get(key));
    });
    assert.equal(i, 3);
  });
});

describe('test-load', function() {
  var file = config.TMP_PATH +'/load.dirty',
    db = dirty(file);

  afterEach(function() {
    fs.unlinkSync(file);
  });

  it('should load after write to disk', function(done) {
    db.set(1, 'A');
    db.set(2, 'B');
    db.set(3, 'C');
    db.rm(3);

    db.on('drain', function() {
      var db2 = dirty(file);

      db2.on('load', function(length) {
        assert.equal(length, 2);

        assert.strictEqual(db2.get(1), 'A');
        assert.strictEqual(db2.get(2), 'B');
        assert.strictEqual(db2.get(3), undefined);
        assert.strictEqual(db2._keys.length, 2);
        assert.ok(!('3' in db2._docs));
        done();
      });
    });
    
  });
});


describe('test-size', function() {
  var db = dirty();

  db.set(1, {test: 'foo'});
  db.set(2, {test: 'bar'});
  db.set(3, {test: 'foobar'});

  it('should be equal to number of keys set', function() {
    assert.equal(db.size(), 3);
  });
});

describe('test-chaining-of-constructor', function() {
  var file = config.TMP_PATH + '/chain.dirty';
  fs.existsSync(file) && fs.unlinkSync(file);

  it('should allow .on load to chain to constructor', function() {
    var db = dirty(file);
    db.on('load', function() {
      db.set("x", "y");
      db.set("p", "q");
      db.close();

      db = dirty(file).on('load', function(size) {
        assert.strictEqual(db.size(), 2);  
        assert.strictEqual(size, 2);  
      });
    });
  });
});
