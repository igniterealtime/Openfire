var NodeUUID = require('node-uuid'),
    util = require('util');

/**
 * UUID
 * @param {String} id The UUID to craete a UUID object from
 * @constructor
 */
var UUID = function(id){
  if(id){
    this.hex = id;
  } else {
    this.hex = NodeUUID.v4();
  }
};

/**
 * Returns a buffer with the bytes for the UUID
 */
UUID.prototype.toBuffer = function(){
  return NodeUUID.parse(this.hex, new Buffer(16));
};

/**
 * Returns a binary string representation of the UUID
 */
UUID.prototype.toBinary = function(){
  return this.toBuffer().toString('binary');
};

/**
 * Inspect
 */
UUID.prototype.inspect = function(){
  return this.hex;
};

/**
 * toString
 */
UUID.prototype.toString = function(){
  return this.inspect();
};

/**
 * Creates a UUID from a buffer
 * @pram {Buffer} buf The buffer to create the UUID from
 */
UUID.fromBuffer = function(buf){
  return new UUID(NodeUUID.unparse(buf));
};

/**
 * Creates a UUID from a binary string
 * @pram {String} bin The binary string to create the UUID from
 */
UUID.fromBinary = function(bin){
  return UUID.fromBuffer(new Buffer(bin, 'binary'));
};

/**
 * TimeUUID
 * @constructor
 * @param {String} id The TimeUUID to craete a TimeUUID object from
 */
var TimeUUID = function(id){
  if(id){
    this.hex = id;
  } else {
    this.hex = NodeUUID.v1();
  }
};
util.inherits(TimeUUID, UUID);

/**
 * Creates a TimeUUID from a Timestamp
 * @param {Date} ts The Timestamp to create the TimeUUID from
 */
TimeUUID.fromTimestamp = function(ts){
  return new TimeUUID( NodeUUID.v1({ msecs: ts.getTime() }) );
};

/**
 * Creates a TimeUUID from a buffer
 * @pram {Buffer} buf The buffer to create the UUID from
 */
TimeUUID.fromBuffer = function(buf){
  return new TimeUUID(NodeUUID.unparse(buf));
};

/**
 * Creates a TimeUUID from a binary string
 * @pram {String} bin The binary string to create the UUID from
 */
TimeUUID.fromBinary = function(bin){
  return TimeUUID.fromBuffer(new Buffer(bin, 'binary'));
};

exports.UUID = UUID;
exports.TimeUUID = TimeUUID;