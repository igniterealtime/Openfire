/**
 * 2011 Peter 'Pita' Martischka 
 * 2013 Kenan Sulayman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * LevelDB port of UeberDB
 * See http://code.google.com/p/leveldb/ for information about LevelDB
 * 
 * LevelDB must be installed in order to use this database.
 * Install it using npm install uberlevel or npm install level-lmdb
 * 
 * Options:
 *   directory: The LevelDB directory, defaults to "lmdb.db"
 *   create_if_missing: Create the LevelDB directory (but not parent directories)
 *                      if it doesn't exist yet. Defaults to true.
 *   write_buffer_size: The size of the LevelDB internal write buffer. Defaults to 4 Mibibytes.
 *   block_size: The LevelDB blocksize. Defaults to 4 kibibytes
 *   compression: Whether to compress the LevelDB using Snappy. Defaults to true.
 */

try {
  var lmdb = require("uberlevel") || require("level-lmdb");
} catch(e) {
  console.error("FATAL: The lmdb dependency could not be found. Please install using npm install uberlevel.");
  process.exit(1);
}

var async = require("async");

exports.database = function(settings) {
  this.db=null;
  
  if(!settings || !settings.directory) {
    settings = {directory:"leveldb-store",create_if_missing: true};
  }
  
  this.settings = settings;
}

exports.database.prototype.init = function(callback) {
  var _this = this;
  async.waterfall([
    function(callback) {
      leveldb.open(_this.settings.directory, { create_if_missing: true },
         function(err, db) {
           _this.db = db;
           callback(err);
	 });
      }
  ],callback);
}

exports.database.prototype.get = function (key, callback) {
  this.db.get(key, function(err, value) {
    callback(err, value ? value : null);
  });
}

exports.database.prototype.set = function (key, value, callback) {
  this.db.put(key, value, callback);
}

exports.database.prototype.remove = function (key, callback) {
  this.db.del(key, callback);
}

exports.database.prototype.doBulk = function (bulk, callback) {
  var batch = this.db.batch();
  bulk.forEach(function (entity) {
    if(entity.type == "set") {
      batch.put(entity.key, entity.value);
    }
    else if(entity.type == "remove") {
      batch.del(entity.key);
    }
  })
  this.db.write(batch, callback);
}

exports.database.prototype.close = function(callback) {
  delete this.db;
  callback(null)
}
