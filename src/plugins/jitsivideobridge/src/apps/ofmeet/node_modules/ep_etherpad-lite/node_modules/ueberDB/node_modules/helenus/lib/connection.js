
var util = require('util'),
    thrift = require('helenus-thrift'),
    Cassandra = require('./cassandra/Cassandra'),
    ttype = require('./cassandra/cassandra_types'),
    Row = require('./row'),
    zlib = require('zlib'),
    Keyspace = require('./keyspace'),
    errors = require('./errors');

/**
 * A No-Operation function for default callbacks
 * @private
 * @constant
 * @memberOf Connection
 */
var NOOP = function(){};

/**
 * Default port for cassandra
 * @private
 * @constant
 * @memberOf Connection
 */
var DEFAULT_PORT = 9160;

/**
 * Default host
 * @private
 * @constant
 * @memberOf Connection
 */
var DEFAULT_HOST = 'localhost';

/**
 * Default Timeout
 * @private
 * @constant
 * @memberOf Connection
 */
var DEFAULT_TIMEOUT = 4000;

/**
 * Default Consistency Level
 * @private
 * @constant
 * @memberOf Connection
 * @see ttypes.ConsistencyLevel types
 */
var DEFAULT_CONSISTENCYLEVEL = 1;

/**
 * Formats CQL properly, paradigm borrowed from node-mysql:
 * https://github.com/felixge/node-mysql/blob/master/lib/client.js#L145-199
 *
 * @param {String} str
 * @private
 * @memberOf Connection
 * @returns {String} The formatted CQL statement
 */
function formatCQL(cql, params){
  //replace a %% with a % to maintain backward compatibility with util.format
  cql = cql.replace(/%%/, '%');

  //remove existing quotes around parameters in case the user has already wrapped them
  cql = cql.replace(/'(\?|%[sjd])'/g, '$1');

  //escape the params and format the CQL string
  cql = cql.replace(/\?|%[sjd]/g, function() {
    if (params.length === 0) {
      throw errors.create(new Error('Too Few Parameters Given'));
    }

    return escapeCQL(params.shift());
  });

  if (params.length) {
    throw errors.create(new Error('Too Many Parameters Given'));
  }

  return cql;
}

/**
 * Escapes CQL, adapted from node-mysql
 * @param {String} val The value to be escaped
 * @private
 * @memberOf Connection
 * @returns {String} The sanitized string
 */
function escapeCQL(val) {
  if (val === undefined || val === null) {
    return 'NULL';
  }

  if(val instanceof Buffer){
    return val.toString('hex');
  }

  if(typeof val === 'number'){
    return val.toString();
  }

  if (Array.isArray(val)) {
    var sanitized = val.map( function( v ) { return escapeCQL( v ); } );
    return sanitized.join(',');
  }

  if (typeof val === 'object' || typeof val === 'boolean') {
    val = (typeof val.toISOString === 'function') ? val.toISOString() : val.toString();
  }

  val = val.replace(/\'/img, '\'\'');

  return "'"+val+"'";
}

/**
 * The Cassandra Connection
 *
 * @param {Object} options The options for the connection defaults to:
     {
       port: 9160,
       host: 'localhost'
       user: null,
       password: null,
       keyspace: null,
       timeout: 1000
     }
 * @constructor
 * @exec
 */
var Connection = function(options){
  if(!options.port && options.host && options.host.indexOf(':') > -1){
    var split = options.host.split(':');
    options.host = split[0];
    options.port = split[1];
  }

  /**
   * The port to connect to
   * @default 9160
   */
  this.port = options.port || DEFAULT_PORT;

  /**
   * The host to connect to
   * @default localhost
   */
  this.host = options.host || DEFAULT_HOST;

  /**
   * The timeout for the connection
   * @default 3000
   */
  this.timeout = options.timeout || DEFAULT_TIMEOUT;

  /**
  * The username to authenticate with
  */
  this.user = options.user;

  /**
   * The password to connect with
   */
  this.password = options.password;

  /**
   * The keyspace to authenticate to
   */
  this.keyspace = options.keyspace;

  /**
   * The CQL version.
  *
   * - Cassandra 1.0 supports CQL 2.0.0
   * - Cassandra 1.1 supports CQL 2.0.0 and 3.0.0 (with 2.0.0 the default)
   * - Cassandra 1.2 will have CQL 3.0.0 as the default
   *
   * Cassandra will support choosing the CQL version for a while,
   * @see https://issues.apache.org/jira/browse/CASSANDRA-3990
   */
  this.cqlVersion = options.cqlVersion;

  /**
   * Consistency level for cql queries
   */
  this.consistencylevel = options.consistencylevel || DEFAULT_CONSISTENCYLEVEL;

  /**
   * Ready state of the client
   */
  this.ready = false;
};
util.inherits(Connection, process.EventEmitter);

/**
 * Connects to the cassandra cluster
 */
Connection.prototype.connect = function(callback){
  var self = this, timer;

  //set callback to a noop to prevent explosion
  callback = callback || NOOP;

  /**
   * Thrift Connection
   */
  this._connection = thrift.createConnection(this.host, this.port);

  self._connection.on('error', function(err){
    clearTimeout(timer);
    callback(err);
  });

  //if we close we don't want ot be ready anymore, and emit it as an error
  this._connection.on('close', function(){
    clearTimeout(timer);
    self.ready = false;
    self.emit('close');
  });

  /**
   * Thrift Client
   */
  this._client = thrift.createClient(Cassandra, this._connection);

  /**
   * Handles what happens when we set the CQL version for the current
   * connection
   *
   * @private
   * @param {Error} err A cassandra error if cql version couldn't be set.
   */
  function onCqlVersionSelected() {
    //set the state to ready
    //@TODO shouldn't this better be done after self.use()? @ctavan
    self.ready = true;

    // if keyspace is specified, use that ks
    self._client.describe_version(function(err, version) {
      if(err){
        return callback(err);
      }

      self.version = version.split('.');

      if (self.keyspace !== undefined){
        return self.use(self.keyspace, callback);
      } else {
        return callback();
      }
    });
  }

  /**
   * Handles what happens when we connect to the cassandra cluster
   *
   * @private
   * @param {Error} err A connection error with cassandra
   */
  function onAuthentication(err) {
    clearTimeout(timer);

    if (err){
      self._connection.connection.destroy();
      callback(errors.create(err));
      return;
    }

    if (self.cqlVersion) {
      self._client.set_cql_version(self.cqlVersion, function(err) {
        if (err) {
          self._connection.connection.destroy();
          callback(errors.create(err));
          return;
        }
        onCqlVersionSelected();
      });
      return;
    }
    onCqlVersionSelected();
  }

  //after we connect, we authenticate
  this._connection.on('connect', function(err){
    if(err){
      callback(err);
    } else {
      //bubble up all errors
      self._connection.removeAllListeners('error');
      self._connection.on('error', function(err){
        self.emit('error', err);
      });

      self.authenticate(onAuthentication);
    }
  });

  timer = setTimeout(function(){
    callback(errors.create({ name: 'TimeoutException', why: 'Connection Timed Out'}));
    self._connection.connection.destroy();
  }, this.timeout);
};

/**
 * Sets the current keyspace
 *
 * @param {String} keyspace The keyspace to use
 */
Connection.prototype.use = function(keyspace, callback){
  var self = this;
  callback = callback || NOOP;

  function onDescribe(err, definition){
    if (err) {
      callback(errors.create(err));
      return;
    }

    self._client.set_keyspace(keyspace, function(err){
      if(err){
        callback(errors.create(err));
      } else {
        callback(null, new Keyspace(self, definition));
      }
    });
  }

  this._client.describe_keyspace(keyspace, onDescribe);
};

/**
 * Authenticates the user
 */
Connection.prototype.authenticate = function(callback){
  callback = callback || NOOP;
  var self = this;

  if(this.user || this.password){
    var credentials = {username: this.user, password: this.password},
        authRequest = new ttype.AuthenticationRequest({ credentials: credentials });

    self._client.login(authRequest, function(err){
      if (err){
        callback(errors.create(err));
      } else {
        callback(null);
      }
    });
  } else {
    callback();
  }
};

/**
 * Executes a command via the thrift connection
 * @param {String} command The command to execute
 * additional params are supplied to the command to be executed
 */
Connection.prototype.execute = function(){
  var args = Array.prototype.slice.apply(arguments),
      command = args.shift(),
      callback = args.pop();

  if(typeof callback !== 'function'){
    args.push(callback);
    callback = NOOP;
  }

  /**
   * Processes the return results of the query
   * @private
   */
  function onReturn(err, results){
    if(err){
      callback(errors.create(err));
    } else {
      callback(null, results);
    }
  }

  args.push(onReturn);

  this._client[command].apply(this._client, args);
};

/**
 * Executes a CQL Query Against the DB.
 * @param {String} cmd A string representation of the query: 'select %s, %s from MyCf where key=%s'
 * @param {Array} args0...argsN An Array of arguments for the string ['arg0', 'arg1', 'arg2']
 * @param {Object} options An object with options for the query, { gzip:true }
 * @param {Function} callback The callback function for the results
 */
Connection.prototype.cql = function(cmd, args, options, callback){
  //case when only a cmd and callback are supplied
  if(typeof args === 'function'){
    callback = args;
    options = {};
    args = undefined;
  }

  //case when cmd args and callback are supplied
  if (typeof options === 'function' && Array.isArray(args)){
    callback = options;
    options = {};
  }

  //case when cmd options and callback are supplied, but not args
  if(typeof options === 'function' && !Array.isArray(args)){
    callback = options;
    options = args;
    args = undefined;
  }

  if(options === undefined || options === null){
    options = {};
  }

  //in case a callback is not supplied
  if(typeof callback !== 'function'){
    callback = NOOP;
  }

  var cql, escaped = [], self = this;

  if(args){
    cql = new Buffer(formatCQL(cmd, args));
  } else {
    cql = new Buffer(cmd);
  }

  function onReturn(err, res){
    if (err){
      callback(err);
      return;
    }

    if(res.type === ttype.CqlResultType.ROWS){
      var rows = [], i = 0, rowlength = res.rows.length;
      for(; i < rowlength; i += 1){
        rows.push(new Row(res.rows[i], res.schema));
      }
      callback(null, rows);
    } else if(res.type === ttype.CqlResultType.INT){
      callback(null, res.num);
    } else if (res.type === ttype.CqlResultType.VOID) {
      callback(null);
    }
  }

  if (this.cqlVersion === '3.0.0' && this.version[0] === '19' && this.version[1] > '33') {
    if(options.gzip === true){
      zlib.deflate(cql, function(err, cqlz){
        self.execute('execute_cql3_query', cqlz, ttype.Compression.GZIP, self.consistencylevel, onReturn);
      });
    } else {
      self.execute('execute_cql3_query', cql, ttype.Compression.NONE, self.consistencylevel, onReturn);
    }
  }
  // fallback to older version
  else {
    if(options.gzip === true){
      zlib.deflate(cql, function(err, cqlz){
        self.execute('execute_cql_query', cqlz, ttype.Compression.GZIP, onReturn);
      });
    } else {
      self.execute('execute_cql_query', cql, ttype.Compression.NONE, onReturn);
    }
  }
};

/**
 * Creates a keyspace
 *
 * @param {String} keyspace The name of the keyspace to create
 * @param {Object} options Keyspace options
 * @param {Function} callback The callback to invoke once complete
 */
Connection.prototype.createKeyspace = function(keyspace, options, callback){
  if(typeof options === 'function'){
    callback = options;
    options = {};
  }

  callback = callback || NOOP;
  options = options || {};

  if(!keyspace){
    callback(errors.create({name:'InvalidNameError', why:'Keyspace name not specified'}));
    return;
  }

  var args = {
    name:keyspace,
    strategy_class: options.strategyClass || 'SimpleStrategy',
    strategy_options: options.strategyOptions || {},
    replication_factor: options.replication || 1,
    durable_writes: options.durable || true,
    cf_defs: []
  };
  if (args.strategy_class === 'SimpleStrategy' &&
      !args.strategy_options.replication_factor) {
    args.strategy_options.replication_factor = '' + args.replication_factor;
  }
  var ksdef = new ttype.KsDef(args);

  /**
   * Once finished, fix the error if needed
   */
  function onComplete(err, response){
    if(err){
      callback(errors.create(err));
    } else {
      callback(null, response);
    }
  }

  this._client.system_add_keyspace(ksdef, onComplete);
};

/**
 * Drops a keyspace
 ** @param {String} name The keyspace name
 */
Connection.prototype.dropKeyspace = function(keyspace, callback){
  callback = callback || NOOP;

  if (typeof keyspace !== 'string'){
    callback({name:'InvalidNameError', why:'Keyspace name not specified'});
  }

  function onComplete(err, response){
    if(err){
      callback(errors.create(err));
    } else {
      callback(null, response);
    }
  }
  this._client.system_drop_keyspace(keyspace, onComplete);
};


/**
 * Closes the connection to the server
 */
Connection.prototype.close = function(){
  this._connection.end();
};
//export our client
module.exports = Connection;
