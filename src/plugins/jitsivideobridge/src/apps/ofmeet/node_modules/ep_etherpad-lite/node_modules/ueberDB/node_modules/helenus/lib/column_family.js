var util = require('util'),
    Marshal = require('./marshal'),
    Column = require('./column'),
    CounterColumn = require('./counter_column'),
    Row = require('./row'),
    ttype = require('./cassandra/cassandra_types');

/**
 * NO-Operation for deault callbacks
 * @private
 * @memberOf ColumnFamily
 */
var NOOP = function(){};

/**
 * Default read consistency level
 * @private
 * @constant
 * @memberOf ColumnFamily
 */
var DEFAULT_READ_CONSISTENCY = ttype.ConsistencyLevel.QUORUM;

/**
 * Default write consistency level
 * @private
 * @constant
 * @memberOf ColumnFamily
 */
var DEFAULT_WRITE_CONSISTENCY = ttype.ConsistencyLevel.QUORUM;

/**
 * Returns a column parent
 * When calculating the column parent of a standard column family,
 * the parent is simply the column family name.  When dealing with
 * super columns on the other hand, an optional name parameter may
 * be provided.
 *
 * @param {Object} cf A reference to the ColumnFamily
 * @param {Object} name The name of the column (optional)
 * @private
 * @memberOf ColumnFamily
 * @returns {Object} a Thrift ColumnParent object
 */
function columnParent(cf, column) {
  var args = {column_family: cf.name};
  if(cf.isSuper && column) {
    args.super_column = cf.columnMarshaller.serialize(column);
  }
  return new ttype.ColumnParent(args);
}

/**
 * Returns a column path
 * As with the ColumnParent, the value of the ColumnPath depends on whether
 * this is a standard or super column family.  Both must specify the column
 * family name.  A standard column family may provide an optional column name
 * parameter.  In addition to the column name, a super column family may also
 * use a subcolumn parameter.
 *
 * @param {Object} cf A reference to the ColumnFamily
 * @param {Object} column The name of the column (optional)
 * @param {Object} subcolumn The name of the subcolumn (optional)
 * @private
 * @memberOf ColumnFamily a Thrift ColumnPath object
 */
function columnPath(cf, column, subcolumn) {
  if(column === undefined){column = null;}
  if(subcolumn === undefined){subcolumn = null;}

  var args = {column_family: cf.name};

  if(cf.isSuper) {
    if(column){args.column = cf.columnMarshaller.serialize(column);}
    if(subcolumn){args.subcolumn = cf.subcolumnMarshaller.serialize(subcolumn);}
  }
  else {
    if(column){args.column = cf.columnMarshaller.serialize(column);}
  }
  return new ttype.ColumnPath(args);
}


/**
 * A convenience method to normalize the standard parameters used by
 * a thrift operation. The parameter list must contain a `key` parameter
 * as it's first item.  The `column`, `subcolumn`, `options`, and
 * `callback` parameters are optional.
 * @param {Array} list The list of parameters
 * @private
 * @memberOf ColumnFamily
 * @returns {Object} a normalized version of the provided parameter values
 */
function normalizeParameters(list) {
  var args = { }, i = 0, type;
  args.key = list.shift();
  for(; i < list.length; i += 1) {
    type = typeof list[i];
    if (type === 'function') {
      args.callback = list[i];
    } else if (type === 'object' && !Array.isArray(list[i])) {
      args.options = list[i];
    } else {
      if(i === 0){args.column = list[i];}
      if(i === 1){args.subcolumn = list[i];}
    }
  }
  return args;
}

/**
 * Gets an array of columns from an object
 * @param {Object} columns
 * @param {Object} options
 * @private
 * @memberOf ColumnFamily
 * @returns {Array} and array of columns
 */
function getColumns(columns, options){
  var keys = Object.keys(columns), len = keys.length, i = 0, key, value, arr = [],
      ts = new Date();

  for(; i < len; i += 1){
    key = keys[i];
    value = columns[key];

    if(value === null || value === undefined){
      value = '';
    }

    arr.push(new Column(key, value, ts, options.ttl));
  }
  return arr;
}

/**
 * Gets a slcie predicate based on some options
 * @private
 * @memberOf ColumnFamily
 * @returns {SlicePredicate}
 */
function getSlicePredicate(options, serializer){
  var predicate = new ttype.SlicePredicate(),
      start = '', end = '';

  if(Array.isArray(options.columns)){
    var cols = [], i = 0, len = options.columns.length;
    for(; i < len; i += 1){
      cols.push( serializer.serialize(options.columns[i]) );
    }
    predicate.column_names = cols;
  } else {
    if(options.start){
      start = serializer.serialize(options.start, !options.reversed);
    }

    if(options.end){
      end = serializer.serialize(options.end, !!options.reversed);
    }

    predicate.slice_range = new ttype.SliceRange({
      start:start,
      finish:end,
      reversed:options.reversed,
      count:options.max
    });
  }

  return predicate;
}

/**
 * Representation of a Column Family
 *
 * @param {Object} definition The Column Family definition
 * @constructor
 */
var ColumnFamily = function(keyspace, definition){
//  ttype.CfDef.call(this, definition);
  this.isSuper = definition.column_type === 'Super';
  this.isCounter = definition.default_validation_class === 'org.apache.cassandra.db.marshal.CounterColumnType';
  this.keyspace = keyspace;
  this.connection = keyspace.connection;
  this.definition = definition;
  this.name = definition.name;
  this.columnMarshaller = new Marshal(definition.comparator_type);
  this.subcolumnMarshaller = this.isSuper ? new Marshal(definition.subcomparator_type) : null;
  this.valueMarshaller = new Marshal(definition.default_validation_class);
  this.keyMarshaller = new Marshal(definition.key_validation_class);
  this.columnValidators = {};

  if(definition.column_metadata && Array.isArray(definition.column_metadata)){
    var i = 0, len = definition.column_metadata.length, col;
    for(; i < len; i += 1){
      col = definition.column_metadata[i];
      col.name = this.columnMarshaller.deserialize(col.name);
      this.setColumnValidator(col.name, col.validation_class);
    }
  }
};

/**
 * Sets the marshaller for a given column name
 * @param name {Object} The name of column to set
 * @param type {String} The validation Class to use for the specified column
 */
ColumnFamily.prototype.setColumnValidator = function(name, type){
  var binName = this.columnMarshaller.serialize(name).toString('binary');
  this.columnValidators[binName] = new Marshal(type);
};

/**
 * Gets the column validator (marshaller) for a column
 * @param name
 * @return {Marshal}
 */
ColumnFamily.prototype.getColumnValidator = function(name){
  var marshalledName = this.columnMarshaller.serialize(name),
      binName = marshalledName.toString('binary');

  return this.columnValidators[binName];
};
/**
 * Performs a set command to the cluster
 *
 * @param {String} key The key for the row
 * @param {Object} columns The value for the columns as represented by JSON or an array of Column objects
 * @param {Object} options The options for the insert
 * @param {Function} callback The callback to call once complete
 */
ColumnFamily.prototype.insert = function(key, columns, options, callback){
  if (typeof options === 'function'){
    callback = options;
    options = {};
  }

  if(!Array.isArray(columns)){
    columns = getColumns(columns, options);
  }

  var len = columns.length, i = 0, valueMarshaller, col,
      mutations = [], batch = {},
      consistency = options.consistency || options.consistencyLevel || DEFAULT_WRITE_CONSISTENCY;

  for(; i < len; i += 1){
    col = columns[i];
    valueMarshaller = this.getColumnValidator(col.name) || this.valueMarshaller;

    mutations.push(new ttype.Mutation({
      column_or_supercolumn: new ttype.ColumnOrSuperColumn({
        column: col.toThrift(this.columnMarshaller, valueMarshaller)
      })
    }));
  }

  var marshalledKey = this.keyMarshaller.serialize(key).toString('binary');

  batch[marshalledKey] = {};
  batch[marshalledKey][this.definition.name] = mutations;

  this.connection.execute('batch_mutate', batch, consistency, callback);
};

/**
 * Remove a single row or column
 * This function uses a variable-length paramter list.  Which parameters
 * are passed depends on which column path should be used for the
 * removal and whether this column family is a super column or not.
 *
 * @param {String} key The key for this row (required)
 * @param {Object} column The column name (optional)
 * @param {Object} subcolumn The subcolumn name (optional)
 * @param {Object} options The thrift options for this operation (optional)
 * @param {Function} callback The callback to call once complete (optional)
 */
ColumnFamily.prototype.remove = function() {
  var args = normalizeParameters(Array.prototype.slice.apply(arguments));
  args.callback = args.callback || NOOP;
  args.options = args.options || { };

  var self = this;
  var marshalledKey = this.keyMarshaller.serialize(args.key).toString('binary'),
      path = columnPath(self, args.column, args.subcolumn),
      timestamp = args.options.timestamp || new Date(),
      consistency = args.options.consistency || args.options.consistencyLevel || DEFAULT_WRITE_CONSISTENCY;

  this.connection.execute('remove', marshalledKey, path, timestamp * 1000, consistency, args.callback);
};

/**
 * Counts the number of columns in a row by it's key
 * @param {String} key The key to get
 * @param {Object} options Options for the get, can have start, end, max, consistencyLevel
 *   <ul>
 *     <li>
 *       start: the from part of the column name, for composites pass an array. By default the
 *       composite queries are inclusive, to make them exclusive pass an array of arrays where the
 *       inner array is [ value, false ].
 *     </li>
 *       start: the end part of the column name, for composites pass an array. By default the
 *       composite queries are inclusive, to make them exclusive pass an array of arrays where the
 *       inner array is [ value, false ].
 *     <li>reversed: {Boolean} to whether the range is reversed or not</li>
 *     <li>max: the max amount of columns to return</li>
 *     <li>columns: an {Array} of column names to get</li>
 *     <li>consistencyLevel: the read consistency level</li>
 *   </ul>
 * @param {Function} callback The callback to invoke once the response has been received
 */
ColumnFamily.prototype.count = function(key, options, callback){
  options.count = true;
  this.get(key, options, callback);
};

/**
 * Get a row by its key
 * @param {String} key The key to get
 * @param {Object} options Options for the get, can have start, end, max, consistencyLevel
 *   <ul>
 *     <li>
 *       start: the from part of the column name, for composites pass an array. By default the
 *       composite queries are inclusive, to make them exclusive pass an array of arrays where the
 *       inner array is [ value, false ].
 *     </li>
 *       start: the end part of the column name, for composites pass an array. By default the
 *       composite queries are inclusive, to make them exclusive pass an array of arrays where the
 *       inner array is [ value, false ].
 *     <li>reversed: {Boolean} to whether the range is reversed or not</li>
 *     <li>max: the max amount of columns to return</li>
 *     <li>columns: an {Array} of column names to get</li>
 *     <li>consistencyLevel: the read consistency level</li>
 *   </ul>
 * @param {Function} callback The callback to invoke once the response has been received
 */
ColumnFamily.prototype.get = function(key, options, callback){
  if (typeof options === 'function'){
    callback = options;
    options = {};
  }

  callback = callback || NOOP;

  options.start = options.start || '';
  options.end = options.end || '';

  var self = this,
      consistency = options.consistency || options.consistencyLevel || DEFAULT_READ_CONSISTENCY,
      marshalledKey = this.keyMarshaller.serialize(key).toString('binary'),
      predicate = getSlicePredicate(options, this.columnMarshaller);

  function onComplete(err, val){
    if(err){
      callback(err);
      return;
    }

    if(options.count === true){
      callback(null, val);
    } else {
      callback(null, Row.fromThrift(key, val, self));
    }
  }

  var command = options.count === true ? 'get_count' : 'get_slice';
  this.connection.execute(command, marshalledKey, columnParent(self), predicate, consistency, onComplete);
};


/**
 * Truncates a ColumnFamily
 * @param {Function} callback The callback to invoke once the ColumnFamily has been truncated
 */
ColumnFamily.prototype.truncate = function(callback){
  this.connection.execute('truncate', this.name, callback);
};

/**
 * Gets rows by their indexed fields
 * @param {Object} query Options for the rows part of the get
 *   <ul>
 *     <li>fields: an array of objects that contain { column:column_name, operator: 'EQ', value:value }
 *       <ul>
 *         <li>column: {String} The name of the column with the index</li>
 *         <li>operator: {String} The operator to use, can be EQ, GTE, GT, LTE, ot LT</li>
 *         <li>value: {String} The value to query by</li>
 *       </ul>
 *     </li>
 *     <li>start: the start key to get</li>
 *     <li>max: the total amount of rows to return</li>
 *   </ul>
 * @param {Object} options Options for the get, can have start, end, max, consistencyLevel
 *   <ul>
 *     <li>start: the from part of the column name</li>
 *     <li>end: the to part of the column name</li>
 *     <li>max: the max amount of columns to return</li>
 *     <li>columns: an {Array} of column names to get</li>
 *     <li>consistencyLevel: the read consistency level</li>
 *   </ul>
 * @param {Function} callback The callback to invoke once the response has been received
 */
ColumnFamily.prototype.getIndexed = function(query, options, callback){
  if (typeof options === 'function'){
    callback = options;
    options = {};
  }

  callback = callback || NOOP;

  options.start = options.start || '';
  options.end = options.end || '';

  var self = this, indexClause, indexExpressions = [],
      i = 0, len = query.fields.length, field, valueMarshaller,
      consistency = options.consistency || options.consistencyLevel || DEFAULT_READ_CONSISTENCY,
      predicate = getSlicePredicate(options, this.columnMarshaller);

  for(; i < len; i += 1){
    field = query.fields[i];
    valueMarshaller = this.getColumnValidator(field.column) || this.valueMarshaller;

    indexExpressions.push(new ttype.IndexExpression({
      column_name:this.columnMarshaller.serialize(field.column),
      op:ttype.IndexOperator[field.operator],
      value: valueMarshaller.serialize(field.value)
    }));
  }

  indexClause = new ttype.IndexClause({
    expressions: indexExpressions,
    start_key:query.start || '',
    count:query.max || 100
  });

  function onComplete(err, val){
    if(err){
      callback(err);
      return;
    }

    var results = [], i = 0, len = val.length, row;
    for(; i < len; i += 1){
      row = val[i];
      results.push(Row.fromThrift(self.keyMarshaller.deserialize(row.key), row.columns, self));
    }
    callback(null, results);
  }

  this.connection.execute('get_indexed_slices', columnParent(self), indexClause, predicate, consistency, onComplete);
};


/**
 * Increments a counter column.
 * @param  {Object}   key      Row key
 * @param  {Object}   column   Column name
 * @param  {Number}   value    Integer to increase by, defaults to 1 (optional)
 * @param  {Object}   options  The thrift options (optional)
 * @param  {Function} callback The callback to call once complete
 */
ColumnFamily.prototype.incr = function (key, column, value, options, callback) {

  if (typeof options === 'function') {
    callback = options;
    options = {};
  }

  if (typeof value === 'function') {
    callback = value;
    options = {};
    value = 1;
  }

  if (typeof value === 'object') {
    options = value;
    value = 1;
  }


  var mutations = [], batch = {}, col,
    consistency = options.consistency || options.consistencyLevel || DEFAULT_WRITE_CONSISTENCY;

  col = new CounterColumn(column, value);

  mutations.push(new ttype.Mutation({
    column_or_supercolumn: new ttype.ColumnOrSuperColumn({
      counter_column: col.toThrift(this.columnMarshaller)
    })
  }));

  var marshalledKey = this.keyMarshaller.serialize(key).toString('binary');

  batch[marshalledKey] = {};
  batch[marshalledKey][this.definition.name] = mutations;

  this.connection.execute('batch_mutate', batch, consistency, callback);
};

module.exports = ColumnFamily;
