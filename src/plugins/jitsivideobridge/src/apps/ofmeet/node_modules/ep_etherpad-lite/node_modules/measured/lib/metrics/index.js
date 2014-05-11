require('fs')
  .readdirSync(__dirname)
  .forEach(function(name) {
    var match = name.match(/(.+)\.js$/);
    if (!match || name === 'index.js') return;

    exports[match[1]] = require('./' + match[1]);
  });
