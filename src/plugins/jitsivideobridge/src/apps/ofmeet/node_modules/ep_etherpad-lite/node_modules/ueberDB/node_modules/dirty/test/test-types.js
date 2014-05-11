var config = require('./config'),
  fs = require('fs'),
  dirty = require(config.LIB_DIRTY),
  assert = require('assert');

describe.skip('test-types', function() {
  var db = dirty(config.TMP_PATH + '/test-types.dirty');

  describe('keys', function() {
    it('should prevent storage of an undefined key', function() {
      db.set(undefined, 'value');
    });

    it('should not return an undefined key', function() {
      assert(!db.get(undefined));
    });
  });

});
