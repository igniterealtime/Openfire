require('../common');

var db = require('Dirty')('undef-key.dirty');

db.set('now', Date.now());

db.set('gobble', 'adfasdf');

db.set('now', undefined, function() {
	// callback?
	// impossible yes?!
	console.log('blamo!')
});

db.set(undefined);

console.log(db.get('now'));

// db = require('Dirty')('undef-key.dirty');