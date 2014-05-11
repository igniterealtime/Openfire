var util = require('util'),
    ttypes = require('./cassandra/cassandra_types');

/**
 * Cassandra Column object representation
 * @param {Object} name The name of the column, can be any type, for composites use Array
 * @param {Object} value The value of the column
 * @param {Date} timestamp The timestamp of the value
 * @param {Number} ttl The ttl for the column
 * @constructor
 */
var Column = function(name, value, timestamp, ttl){
  /**
   * The name of the column, can be any type, for composites use Array
   */
  this.name = name;

  /**
   * The value of the column
   */
  this.value = value;

  /**
   * The timestamp of the value
   * @default {Date} new Date();
   */
  this.timestamp = timestamp || new Date();

  /**
   * The ttl for the column
   */
  this.ttl = ttl;
};

/**
 * Marshals the column to a thrift column using the marshallers for name and value
 * @param {Marshal} nameMarshaller The marshaller for the column name
 * @param {Marshal} valueMarshaller The marshaller for the column value
 * @returns {Column} The thrift column with correctly marshalled name and value
 */
Column.prototype.toThrift = function(nameMarshaller, valueMarshaller){
  return new ttypes.Column({
    name: nameMarshaller.serialize(this.name),
    value: valueMarshaller.serialize(this.value),
    timestamp: this.timestamp.getTime() * 1000,
    ttl:this.ttl
  });
};

module.exports = Column;
