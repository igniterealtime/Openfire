var common = require('../common');

var collection = new common.measured.Collection();

collection.timer('a').start();
collection.meter('b').start();
collection.counter('c');

collection.end();
