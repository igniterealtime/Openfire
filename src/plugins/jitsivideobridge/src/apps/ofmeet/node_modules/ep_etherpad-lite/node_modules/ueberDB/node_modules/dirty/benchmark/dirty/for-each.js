var config = require('../../test/config');
var COUNT = 1e6,
    dirty = require(config.LIB_DIRTY)(),
    util = require('util');

for (var i = 0; i < COUNT; i++) {
  dirty.set(i, i);
}

var start = Date.now(), i = 0;
dirty.forEach(function(key, doc) {
  if (!key && key !== 0) {
    throw new Error('implementation fail');
  }
});

var ms = Date.now() - start,
    mhz = ((COUNT / (ms / 1000)) / 1e6).toFixed(2),
    million = COUNT / 1e6;

// Can't use console.log() since since I also test this in ancient node versions
util.log(mhz+' Mhz ('+million+' million in '+ms+' ms)');
