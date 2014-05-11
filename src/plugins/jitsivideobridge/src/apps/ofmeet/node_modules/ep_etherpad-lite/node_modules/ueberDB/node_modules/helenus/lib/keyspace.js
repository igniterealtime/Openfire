var ColumnFamily = require('./column_family'),
    ttypes = require('./cassandra/cassandra_types'),
    Marshal = require('./marshal');


/**
 * A No-Operation default for callbacks
 * @private
 * @memberOf Keyspace
 * @constant
 */
var NOOP = function(){};

/**
 * Creates an instance of a keyspace
 * @constructor
 */
var Keyspace = function(connection, definition){
  /**
   * The connection object
   * @see Connection
   */
  this.connection = connection;

  /**
   * The name of the keyspace
   */
  this.name = definition.name;

  /**
   * The thrift definition of the keyspace
   */
  this.definition = definition;

  /**
   * A cache of column familes to help with performance
   */
  this.columnFamilies = {};
};

/**
 * Gets a column family from the cache or loads it up
 * @param {String} columnFamily The name of the columnFamily to get the definition for
 * @param {Function} callback The callback to invoke once the column family has been retreived
 */
Keyspace.prototype.get = function(columnFamily, callback){
  var self = this;

  if(this.columnFamilies[columnFamily]){
    callback(null, this.columnFamilies[columnFamily]);
  } else {
    this.describe(function(err, columnFamilies){
      if(err){
        callback(err);
      } else {
        if(columnFamilies[columnFamily]){
          self.columnFamilies = columnFamilies;
          callback(null, columnFamilies[columnFamily]);
        } else {
          var e = new Error('ColumnFamily ' + columnFamily + ' Not Found');
          e.name = 'HelenusNotFoundError';
          callback(e);
        }
      }
    });
  }
};

/**
 * Loads gets all the column families and calls back with them
 * @param {Function} callback The callback to invoke once the column families have been retreived
 */
Keyspace.prototype.describe = function(callback){
  var self = this;

  function onComplete(err, definition){
    if (err) {
      callback(err);
      return;
    }

    var i = 0, cf, columnFamilies = {},
        len = definition.cf_defs.length;

    for(; i < len; i += 1) {
      cf = definition.cf_defs[i];
      columnFamilies[cf.name] = new ColumnFamily(self, cf);
    }

    callback(null, columnFamilies);
  }

  this.connection.execute('describe_keyspace', this.name, onComplete);
};

/**
 * Creates a column family with options
 * @param {String} name The name of the column family to create
 * @param {Object} options The options for the columns family, options are:
 *  <ul>
 *    <li>column_type: Can be "Standard" or "Super" Defaults to "Standard" </li>
 *    <li>comparator_type: The default comparator type</li>
 *    <li>subcomparator_type: The default subcomparator type</li>
 *    <li>comment: A comment for the cf</li>
 *    <li>read_repair_chance: </li>
 *    <li>column_metadata: </li>
 *    <li>gc_grace_seconds: </li>
 *    <li>default_validation_class: </li>
 *    <li>min_compaction_threshold: </li>
 *    <li>max_compaction_threshold: </li>
 *    <li>replicate_on_write: </li>
 *    <li>merge_shards_chance: </li>
 *    <li>key_validation_class: </li>
 *    <li>key_alias: </li>
 *    <li>compaction_strategy: </li>
 *    <li>compaction_strategy_options: </li>
 *    <li>compression_options: </li>
 *    <li>bloom_filter_fp_chance: </li>
 *    <li>columns: Columns is an array of column options each element in the array is an object with these options:
 *      <ul>
 *        <li>name: *REQUIRED* The name of the column</li>
 *        <li>validation_class: *REQUIRED* The validation class. Defaults to BytesType</li>
 *        <li>index_type: The type of index</li>
 *        <li>index_name: The name of the index</li>
 *        <li>index_options: The options for the index, </li>
 *      </ul>
 *    </li>
 *  </ul>
 * @param {Function} callback The callback to invoke once the column family has been created
 */
Keyspace.prototype.createColumnFamily = function(name, options, callback){
  if(typeof options === 'function'){
    callback = options;
    options = {};
  }

  callback = callback || NOOP;
  options = options || {};

  var meta,
      comparator = options.comparator_type || 'BytesType';

  if(options.columns && Array.isArray(options.columns)){
    var i = 0, len = options.columns.length, col,
        columnMarshaller = new Marshal(comparator);

    meta = [];

    for(; i < len; i += 1){
      col = options.columns[i];

      meta.push(new ttypes.ColumnDef({
        name: columnMarshaller.serialize(col.name),
        validation_class: col.validation_class,
        index_type: col.index_type,
        index_name: col.index_name,
        index_options: col.index_options
      }));
    }
  }

  var cfdef = new ttypes.CfDef({
    keyspace: this.name,
    name: name,
    column_type: options.column_type || 'Standard',
    comparator_type: comparator,
    subcomparator_type: options.subcomparator_type,
    comment: options.comment,
    read_repair_chance: options.read_repair_chance || 1,
    column_metadata: meta,
    gc_grace_seconds: options.gc_grace_seconds,
    default_validation_class: options.default_validation_class,
    min_compaction_threshold: options.min_compaction_threshold,
    max_compaction_threshold: options.max_compaction_threshold,
    replicate_on_write: options.replicate_on_write,
    merge_shards_chance: options.merge_shards_chance,
    key_validation_class: options.key_validation_class,
    key_alias: options.key_alias,
    compaction_strategy: options.compaction_strategy,
    compaction_strategy_options: options.compaction_strategy_options,
    compression_options: options.compression_options,
    bloom_filter_fp_chance: options.bloom_filter_fp_chance
  });

  this.connection.execute('system_add_column_family', cfdef, callback);
};

/**
 * Drops a column family
 * @param {String} name The name of the column family to drop
 * @param {Function} callback The callback to invoke once the column family has been created
 */
Keyspace.prototype.dropColumnFamily = function(name, callback){
  callback = callback || NOOP;
  this.connection.execute('system_drop_column_family', name, callback);
};

module.exports = Keyspace;
