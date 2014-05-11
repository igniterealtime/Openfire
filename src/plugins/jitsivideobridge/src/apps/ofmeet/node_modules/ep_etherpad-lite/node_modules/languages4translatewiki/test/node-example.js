// From node the module is accesible with a simple require
var languages = require ('../languages.min.js');
var num_languages = 0;

// languages.getAllLanguageCode() return an array of all ISO 639-1 language code supported
var langscodes = languages.getAllLanguageCode();
// iterate this array
for (num_languages=0; num_languages<langscodes.length; num_languages++) {
	// show a string representation of the object return by languages.getLanguageInfo(langcode)
	console.log(langscodes[num_languages]);
	console.log("   "+JSON.stringify(languages.getLanguageInfo(langscodes[num_languages])));
}
// show the number of languages supported
console.log("Languages supported: "+num_languages);
// test languages.isValid(langcode) function
console.log("¿isValid 'kaka' language code? "+languages.isValid('kaka'));
console.log("¿isValid 'es' language code? "+languages.isValid('es'));
