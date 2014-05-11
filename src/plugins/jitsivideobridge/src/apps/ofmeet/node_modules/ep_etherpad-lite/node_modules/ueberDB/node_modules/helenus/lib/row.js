var util = require('util'),
    Column = require('./column'),
    Marshal = require('./marshal');

/**
 * Represents the columns in a row
 * @constructor
 * @param {Object} data The data returned from the cql request
 * @param {Object} schema The schema returned from the cql request
 */
var Row = function(data, schema){

  var i = 0, len = data.columns.length, item, name,
      deserializeName, deserializeValue,
      deserializeNameDefault = new Marshal(schema.default_name_type || 'BytesType').deserialize,
      deserializeValueDefault = new Marshal(schema.default_value_type || 'BytesType').deserialize;

  this.key = data.key;
  this._map = {};
  this._schema = schema;

  for(; i < len; i += 1){
    item = data.columns[i];

    // Default name deserializer
    deserializeName = deserializeNameDefault;

    // Individual columns can be set with a different name deserializer (in
    // case of CompositeColumns)
    if(schema.name_types && schema.name_types[item.name]){
      deserializeName = new Marshal(schema.name_types[item.name]).deserialize;
    }
    name = deserializeName(item.name);

    //default value decoder
    deserializeValue = deserializeValueDefault;

    //individual columns can be set with a different value deserializer
    if(schema.value_types && schema.value_types[item.name]){
      deserializeValue = new Marshal(schema.value_types[item.name]).deserialize;
    }

    //when doing select * you get a column called KEY, it's not good eats.
    if(item.name !== 'KEY'){

      // Only deserialize if specified
      if(schema.noDeserialize) {
        this.push(new Column(name,item.value, new Date(item.timestamp / 1000), item.ttl));
      } else {
        this.push(new Column(name,deserializeValue(item.value), new Date(item.timestamp / 1000), item.ttl));
      }

      this._map[name] = this.length - 1;
    }
  }

  /**
   * Return the columns count, synonymous to length
   */
  this.__defineGetter__('count', function(){
    return this.length;
  });
};
util.inherits(Row, Array);

/**
 * Create a row object using data returned from a thrift request
 * @param {String} key The key of the row
 * @param {Array} columns The response from the thrift request
 * @param {ColumnFamily} cf The column family creating the row
 */
Row.fromThrift = function(key, columns, cf){
  var data = { columns: [], key:key }, col,
      schema = {}, i = 0, len = columns.length;

  //TODO: Implement super columns
  for(; i < len; i += 1){
    col = cf.isCounter ? columns[i].counter_column : columns[i].column;
    data.columns.push( col );
  }

  schema.value_types = {};
  schema.default_value_type = cf.definition.default_validation_class;
  schema.default_name_type = cf.definition.comparator_type;

  if(cf.definition.column_metadata && Array.isArray(cf.definition.column_metadata)){
    i = 0; len = cf.definition.column_metadata.length;
    var item;

    for(; i < len; i += 1){
      item = cf.definition.column_metadata[i];
      schema.value_types[item.name] = item.validation_class;
    }
  }

  // Counters already returned deserialized
  if(cf.isCounter) {
    schema.noDeserialize = true;
  }

  return new Row(data, schema);
};

/**
 * Overrides the Array.forEach to callback with (name, value, timestamp, ttl)
 * @param {Function} callback The callback to invoke once for each column in the Row
 */
Row.prototype.forEach = function(callback){
  var i = 0, len = this.length, item;
  for(; i < len; i += 1){
    item = this[i];
    callback(item.name, item.value, item.timestamp, item.ttl);
  }
};

/**
 * Adds the ability to get a column by its name rather than by its array index
 * @param {String} name The name of the column to get
 * @returns {Object} a tuple of the column name, timestamp, ttl and value
 */
Row.prototype.get = function(name){
  return this[this._map[name]];
};

/**
 * Inspect method for columns
 */
Row.prototype.inspect = function(){
  var i = 0, names = Object.keys(this._map), len = names.length, cols = [], col;
  for(; i < len; i += 1){
    col = names[i];
    if(Array.isArray(col)){
      col = col.join(':');
    }

    cols.push(col);
  }

  var key = this.key;
  if(Array.isArray(key)){
    key = key.join(':');
  }

  return util.format("<Row: Key: '%s', ColumnCount: %s, Columns: [ '%s' ]>", key, this.length, cols);
};

/**
 * Slices out columns based on their name
 * @param {String} start The starting string
 * @param {String} end The ending string
 * @returns {Row} Row with the sliced out columns
 */
Row.prototype.nameSlice = function(start, end){
  start = start || ' ';
  end = end || '~';

  var names = Object.keys(this._map),
      i = 0, len = names.length, matches = [], key;

  for(; i < len; i += 1){
    key = names[i];
    if(key >= start && key < end){
      matches.push(this.get(key));
    }
  }

  return new Row({ key:this.key, columns:matches }, this._schema);
};

/**
 * Slices out columns based ont their index
 * @param {Number} start Required. An integer that specifies where to start the selection (The first columns has an index of 0). You can also use negative numbers to select from the end of the row
 * @param {Number} end Optional. An integer that specifies where to end the selection. If omitted, slice() selects all elements from the start position and to the end of the row
 * @returns {Row} Row with the sliced out columns
 */
Row.prototype.slice = function(start, end){
  start = start || 0;

  var matches = Array.prototype.slice.call(this, start, end);
  return new Row({ key:this.key, columns:matches }, this._schema);
};

/**
 * ToString method for columns
 * @see Row#inspect
 */
Row.prototype.toString = function(){
  return this.inspect();
};

module.exports = Row;
