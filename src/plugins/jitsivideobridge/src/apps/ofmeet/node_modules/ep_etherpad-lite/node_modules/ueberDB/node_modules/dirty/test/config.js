var path = require('path'),
  fs = require('fs'),
  rimraf = require('rimraf');

var TMP_PATH = path.join(__dirname, 'tmp'),
  LIB_DIRTY = path.join(__dirname, '../lib/dirty');

rimraf.sync(TMP_PATH);
fs.mkdirSync(TMP_PATH);

module.exports = {
  TMP_PATH: TMP_PATH,
  LIB_DIRTY: LIB_DIRTY
};
