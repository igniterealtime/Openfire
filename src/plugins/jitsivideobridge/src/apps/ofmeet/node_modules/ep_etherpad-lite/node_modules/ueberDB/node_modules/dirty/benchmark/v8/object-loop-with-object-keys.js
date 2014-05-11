var COUNT = 1e6,
    util = require('util'),
    o = {};

for (var i = 0; i < COUNT; i++) {
  o[i] = i;
}

var start = +new Date, keys = Object.keys(o), length = keys.length;
for (var i = 0; i < keys.length; i++) {
  if (o[keys[i]] != i) {
    throw new Error('implementation fail');
  }
}

var ms = +new Date - start,
    mhz = ((COUNT / (ms / 1000)) / 1e6).toFixed(2),
    million = COUNT / 1e6;

// Can't use console.log() since since I also test this in ancient node versions
util.log(mhz+' Mhz ('+million+' million in '+ms+' ms)');
