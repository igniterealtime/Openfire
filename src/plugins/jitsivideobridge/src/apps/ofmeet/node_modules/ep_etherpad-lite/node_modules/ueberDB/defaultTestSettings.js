exports["mysql"] = {"user":"etherpadlite", host: "localhost", "password":"etherpadlite", database: "etherpadlite"};
exports["postgres"] = {"user":"etherpadlite", host: "localhost", "password":"etherpadlite", database: "etherpadlite"};
exports["sqlite"] = {filename:"var/sqlite3.db"};
exports["dirty"] = {filename:"var/dirty.db"};
exports["redis"] = {};
exports["couch"] = {port: 5984, host: 'localhost', database: "etherpadlite", maxListeners: 0};
exports["mongodb"] = {port: 27017, host: "localhost", dbname: "etherpadlite"};
exports["cassandra"] = {hosts: ["127.0.0.1:9160"], keyspace: "etherpadlite", cfName: "etherpadlite"};
