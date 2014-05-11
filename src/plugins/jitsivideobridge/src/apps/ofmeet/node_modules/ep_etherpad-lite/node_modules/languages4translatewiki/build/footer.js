//return string "ltr" if the language is written from left to right or "rtl" in other case
var getLangDirection = function(langcode) {
  var result = "ltr";
  if (langs.rtl.indexOf(langcode) != -1) result = "rtl";
  return result;
};

/* PUBLIC */

//return boolean value, true if langcode is supported
exports.isValid = function(langcode) {
  return langs.lang.hasOwnProperty(langcode);
};

//return array with all the language codes supported
exports.getAllLanguageCode = function() {
  var result = [];
  for (langcode in langs.lang) {
    result.push(langcode);
  }
  return result;
};

//return object {"nativeName", "direction"}
//if langcode isn't supported return {}
exports.getLanguageInfo = function(langcode) {
  var result = {};
  if (exports.isValid(langcode)) {
    var lang = langs.lang[langcode];
    for (attr in langs['attribute']) {
      result[attr] = lang[langs['attribute'][attr]];
    }
    result['direction']=getLangDirection(langcode);
  }
  return result;
};

//allow executing by nodejs in the server or by javascript in the browser
})(typeof exports === 'undefined'? this['languages']={}: exports);
