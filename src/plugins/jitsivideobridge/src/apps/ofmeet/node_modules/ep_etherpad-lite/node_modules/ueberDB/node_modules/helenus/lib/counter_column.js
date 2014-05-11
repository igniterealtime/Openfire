var ttypes = require('./cassandra/cassandra_types');

/**
 * Cassandra Counter Column object representation
 * @param {Object} name The name of the column, can be any type, for composites use Array
 * @param {Number} value The value to add to the counter, must be an integer.
 * @constructor
 */
var CounterColumn = function(name, value){
  /**
   * The name of the column, can be any type, for composites use Array
   */
  this.name = name;

  /**
   * The value to add to the counter
   */
  this.value = value;

};

/**
 * Marshals the counter column to a thrift counter column using the marshallers
 * for name and value. Values are always numbers.
 * @param {Marshal} nameMarshaller The marshaller for the column name
 * @returns {CounterColumn} The thrift column with correctly marshalled name and value
 */
CounterColumn.prototype.toThrift = function(nameMarshaller){
  return new ttypes.CounterColumn({
    name: nameMarshaller.serialize(this.name),
    value: this.value
  });
};

module.exports = CounterColumn;
