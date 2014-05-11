var poolModule = require('./lib/generic-pool.js');
var mysql = require('mysql');

var pool = poolModule.Pool({
    name: 'mysql',
    create: function (callback) {

        console.log('create');

        var connection = mysql.createConnection({
            host: 'localhost',
            user: 'test',
            password: 'test',
            database: 'test'
        });

        connection.connect(function () {
            return callback(null, connection);
        });

    },
    destroy: function (client) { // this is never called.  ever

        console.log('destroy');
        client.destroy();
    },
    max: 3,
    min: 2,

    idleTimeoutMillis: 3000, //changed this  to 3000
    log: true
});


pool.query = function (query, data, callback) {

    try {

        pool.acquire(function (err, client) {

          //called 3 times.  never called again.

            client.query(query, data, function (err, results, fields) {

                try {

                    pool.release(client); // I've also tried pool.destroy(client);          
                    return callback(err, results);

                } catch (err) {
                    console.log(err)
                }

            });
        });
    } catch (err) {
        console.log(err);
    }   
};

console.log("STARTED");

var i, count = 0;
for (i = 0; i < 10; i++) {
    pool.query("select 1", [], function(err) {
        count += 1;
        console.log("finished " + count + " queries");
        if (count === 10) {
            console.log("STOPPING");
            pool.drain();
        }
    });
}
