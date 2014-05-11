
/**
 * Creates a JS Error from a Cassndra Error
 * @param {Object} err The cassandra error eg: { name:'Exception', why:'Some Reason' }
 */
exports.create = function createError(err){
  //sometimes the message comes back as the field why and sometimes as the field message
  var error = new Error(err.why || err.message);
  error.name = 'Helenus' + err.name;
  Error.captureStackTrace(error, createError);
  return error;
};