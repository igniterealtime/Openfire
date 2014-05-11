var config = require('../../test/config');
var COUNT = 1e6,
    dirty = require(config.LIB_DIRTY)(config.TMP_PATH + '/benchmark-set.dirty'),
    util = require('util');

var start = Date.now();
for (var i = 0; i < COUNT; i++) {
  dirty.set(i, i);
}

var ms = Date.now() - start,
    mhz = ((COUNT / (ms / 1000)) / 1e6).toFixed(2),
    million = COUNT / 1e6;

// Can't use console.log() since since I also test this in ancient node versions
util.log(mhz+' Mhz ('+million+' million in '+ms+' ms)');
