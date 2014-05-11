/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var Helenus = require('helenus');

/**
 * Cassandra DB constructor.
 *
 * @param  {Object}     settings                    The required settings object to create a Cassandra pool.
 * @param  {String[]}   settings.hosts              An array of '<ip>:<port>' strings that are running the Cassandra database.
 * @param  {String}     settings.keyspace           The keyspace that should be used, it's assumed that the keyspace already exists.
 * @param  {String}     settings.cfName             The prefix for the column families that should be used to store data. The column families will be created if they don't exist.
 * @param  {String}     [settings.user]             A username that should be used to authenticate with Cassandra (optional.)
 * @param  {String}     [settings.pass]             A password that should be used to authenticate with Cassandra (optional.)
 * @param  {Number}     [settings.timeout]          The time (defined in ms) when a query has been considered to time-out (Optional, default 3000.)
 * @param  {Number}     [settings.replication]      The replication factor to use. (Optional, default 1.)
 * @param  {String}     [settings.strategyClass]    The strategyClass to use (Optional, default 'SimpleStrategy'.)
 */
exports.database = function(settings) {
  var self = this;

  if (!settings.hosts || settings.hosts.length === 0) {
    throw new Error('The Cassandra hosts should be defined.');
  }
  if (!settings.keyspace) {
    throw new Error('The Cassandra keyspace should be defined.');
  }
  if (!settings.cfName) {
    throw new Error('The Cassandra column family should be defined.');
  }

  self.settings = {};
  self.settings.hosts = settings.hosts;
  self.settings.keyspace = settings.keyspace;
  self.settings.cfName = settings.cfName;
  if (settings.user) {
    self.settings.user = settings.user;
  }
  if (settings.pass) {
    self.settings.pass = settings.pass;
  }
  self.settings.timeout = parseInt(settings.timeout, 10) || 3000;
  self.settings.replication = parseInt(settings.replication, 10) || 1;
  self.settings.strategyClass = settings.strategyClass || 'SimpleStrategy';
  self.settings.cqlVersion = '2.0.0';
};

/**
 * Initializes the Cassandra pool, connects to cassandra and creates the CF if it didn't exist already.
 *
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    An error object (if any.)
 */
exports.database.prototype.init = function(callback) {
  var self = this;

  // Create pool
  self.pool = new Helenus.ConnectionPool(self.settings);
  self.pool.on('error', function(err) {
    // We can't use the callback method here, as this is a generic error handler.
    console.error(err);
  });

  // Connect to it.
  self.pool.connect(function(err) {
    if (err) {
      return callback(err);
    }

    // Get a description of the keyspace so we can determine whether or not the CF exist.
    self.pool.getConnection()._client.describe_keyspace(self.settings.keyspace, function(err, definition) {
      if (err && err.name) {
        // If the keyspace doesn't exist, it will throw here.
        return callback(err);
      }

      // Iterate over all the column families and check if the desired one exists.
      var exists = false;
      for (var i = 0; i < definition.cf_defs.length;i++) {
        if (definition.cf_defs[i].name === self.settings.cfName) {
          exists = true;
          break;
        }
      }

      if (exists) {
        // The CF exists, we're done here.
        callback(null);
      } else {
        // Create the CF
        var cql = 'CREATE COLUMNFAMILY ? (key text PRIMARY KEY, data text);';
        self.pool.cql(cql, [ self.settings.cfName ], callback);
      }
    });
  });
};

/**
 * Gets a value from Cassandra.
 *
 * @param  {String}     key             The key for which the value should be retrieved.
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    An error object (if any.)
 * @param  {String}     callback.value  The value for the given key (if any.)
 */
exports.database.prototype.get = function (key, callback) {
  var self = this;
  var cql = 'SELECT ? FROM ? WHERE ? = ?';
  self.pool.cql(cql, [ 'data', self.settings.cfName, 'key', key ], function (err, rows) {
    if (err) {
      return callback(err);
    }

    if (rows.length === 0 || rows[0].count === 0 || !rows[0].get('data')) {
      return callback(null, null);
    }

    callback(null, rows[0].get('data').value);
  });
};

/**
 * Cassandra has no native `findKeys` method. This function implements a naive filter by retrieving *all* the keys and filtering those.
 * This should obviously be used with the utmost care and is probably not something you want to run in production.
 *
 * @param  {String}     key             The filter for keys that should match.
 * @param  {String}     [notKey]        The filter for keys that shouldn't match.
 * @param  {Function}   callback        Standard callback method
 * @param  {Error}      callback.err    Error object in case something goes wrong.
 * @param  {String[]}   callback.keys   An array of keys that match the specified filters.
 */
exports.database.prototype.findKeys = function (key, notKey, callback) {
  var self = this;
  if (!notKey) {
    // Get all the keys.
    var cql = 'SELECT ? FROM ?';
    self.pool.cql(cql, [ 'key', self.settings.cfName ], function (err, rows) {
      if (err) {
        return callback(err);
      }

      var keys = [];
      rows.forEach(function(row) {
        keys.push(row.get('key').value);
      });

      callback(null, keys);
    });

  } else if (notKey === '*:*:*') {
    // restrict key to format 'text:*'
    var matches = /^([^:]+):\*$/.exec(key);
    if (matches) {
      // Get the 'text' bit out of the key and get all those keys from a special column.
      // We can retrieve them from this column as we're duplicating them on .set/.remove
      var cql = 'SELECT * from ? WHERE ? = ?';
      self.pool.cql(cql, [ self.settings.cfName, 'key', 'ueberdb:keys:' + matches[1] ], function (err, rows) {
        if (err) {
          return callback(err);
        }

        // If the key could not be found, the column count will still be one as the `key` column always returns.
        if (rows.length === 0 || rows[0].count <= 1 || !rows[0].get('data')) {
          return callback(null, []);
        }

        var keys = [];
        rows[0].forEach(function(name, value) {
          keys.push(name);
        });
        callback(null, keys);
      });
    } else {
      callback(new customError('Cassandra db only supports key patterns like pad:* when notKey is set to *:*:*', 'apierror'), null);
    }
  } else {
    callback(new customError('Cassandra db currently only supports *:*:* as notKey', 'apierror'), null);
  }
};

/**
 * Sets a value for a key.
 *
 * @param  {String}     key         The key to set.
 * @param  {String}     value           The value associated to this key.
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    Error object in case something goes wrong.
 */
exports.database.prototype.set = function (key, value, callback) {
  this.doBulk([{'type': 'set', 'key': key, 'value': value}], callback);
};

/**
 * Removes a key and it's value from the column family.
 *
 * @param  {String}     key             The key to remove.
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    Error object in case something goes wrong.
 */
exports.database.prototype.remove = function (key, callback) {
  this.doBulk([{'type': 'remove', 'key': key}], callback);
};

/**
 * Performs multiple operations in one action. Note that these are *NOT* atomic and any order is not guaranteed.
 *
 * @param  {Object[]}   bulk            The set of operations that should be performed.
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    Error object in case something goes wrong.
 */
exports.database.prototype.doBulk = function (bulk, callback) {
  var self = this;
  var query = 'BEGIN BATCH USING CONSISTENCY ONE \n';
  var parameters = [];
  bulk.forEach(function(operation) {
    var matches = /^([^:]+):([^:]+)$/.exec(operation.key);
    if (operation.type === 'set') {
      query += 'UPDATE ? SET ? = ? WHERE ? = ?; \n';
      parameters.push(self.settings.cfName);
      parameters.push('data');
      parameters.push(operation.value);
      parameters.push('key');
      parameters.push(operation.key);

      if (matches) {
        query += 'UPDATE ? SET ? = 1 WHERE ? = ?; \n';
        parameters.push(self.settings.cfName);
        parameters.push(matches[0]);
        parameters.push('key');
        parameters.push('ueberdb:keys:' + matches[1]);
      }

    } else if (operation.type === 'remove') {
      query += 'DELETE FROM ? WHERE ? = ?; \n';
      parameters.push(self.settings.cfName);
      parameters.push('key');
      parameters.push(operation.key);

      if (matches) {
        query += 'DELETE ? FROM ? WHERE ? = ?; \n';
        parameters.push(matches[0]);
        parameters.push(self.settings.cfName);
        parameters.push('key');
        parameters.push('ueberdb:keys:' + matches[1]);
      }
    }
  });
  query += 'APPLY BATCH;';
  self.pool.cql(query, parameters, callback);
};

/**
 * Closes the Cassandra connection.
 *
 * @param  {Function}   callback        Standard callback method.
 * @param  {Error}      callback.err    Error object in case something goes wrong.
 */
exports.database.prototype.close = function(callback) {
  var self = this;
  self.pool.on('close', callback);
  self.pool.close();
};
