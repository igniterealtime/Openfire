# Helenus

  NodeJS Bindings for Cassandra

  Currently the driver has full CQL support and a growing support for thrift (non-cql) commands.
  If you would like to contribute, please contact Russ Bradberry &lt;rbradberry@simplereach.com&gt;

  If you have any questions regarding the driver, please visit our [google group](https://groups.google.com/forum/?fromgroups#!forum/helenus)


### Build Status

  [![Build Status](https://secure.travis-ci.org/simplereach/helenus.png)](http://travis-ci.org/simplereach/helenus)

## Installation

    npm install helenus

## Running Tests

  Ensure cassandra is running on localhost:9160.

    make test

  For coverage

    make test-cov

## Usage

## CQL

```javascript
  var helenus = require('helenus'),
      pool = new helenus.ConnectionPool({
        hosts      : ['localhost:9160'],
        keyspace   : 'helenus_test',
        user       : 'test',
        password   : 'test1233',
        timeout    : 3000
        //cqlVersion : '3.0.0' // specify this if you're using Cassandra 1.1 and want to use CQL 3
      });

  //optionally you can supply the 'getHost' parameter to the connection pool options which will
  // allow you to override the default random host decision

  //if you don't listen for error, it will bubble up to `process.uncaughtException`
  //pools act just like connection objects, so you don't have to worry about api
  //differences when using either the pool or the connection
  pool.on('error', function(err){
    console.error(err.name, err.message);
  });

  //makes a connection to the pool, this will return once there is at least one
  //valid connection, other connections may still be pending
  pool.connect(function(err, keyspace){
    if(err){
      throw(err);
    } else {
      //to use cql, access the pool object once connected
      //the first argument is the CQL string, the second is an `Array` of items
      //to interpolate into the format string, the last is the callback
      //for formatting specific see `http://nodejs.org/docs/latest/api/util.html#util.format`
      //results is an array of row objects

      pool.cql("SELECT col FROM cf_one WHERE key = ?", ['key123'], function(err, results){
        console.log(err, results);
      });

      //NOTE:
      //- You can always skip quotes around placeholders, they are added automatically.
      //- In CQL 3 you cannot use placeholders for ColumnFamily names or Column names.
    }
  });
```

## Thrift

If you do not want to use CQL, you can make calls using the thrift driver

```javascript
  pool.connect(function(err, keyspace){
    if(err){
      throw(err);
    }

    //first retreive the column family from the server
    //helenus will cache column families it has already seen
    keyspace.get('my_cf', function(err, cf){
      if(err){
        throw(err);
      }

      //insert something into the column family
      cf.insert('foo', {'bar':'baz'}, function(err){
        if(err){
          throw(err);
        }

        //get what we just put in
        //the driver will return a helenus.Row object just like CQL
        cf.get('foo', {consistency:helenus.ConsistencyLevel.ONE} function(err, row){
          if(err){
            throw(err);
          }

          row.get('bar').value // => baz
        });
      });
    });

  });
```

### Thrift Support

Currently Helenus supports the following command for the thrift side of the driver:

  * connection.createKeyspace
  * connection.dropKeyspace
  * keyspace.createColumnFamily
  * keyspace.dropColumnFamily
  * columnFamily.insert
  * columnFamily.get
  * columnFamily.getIndexed
  * columnFamily.remove
  * columnFamily.truncate

The following support is going to be added in later releases:

  * columnFamily.rowCount
  * columnFamily.columnCount
  * columnfamily.increment
  * SuperColumns
  * CounterColumns
  * Better composite support

## Row

The Helenus Row object acts like an array but contains some helper methods to
make your life a bit easier when dealing with dynamic columns in Cassandra

### row.count

Returns the number of columns in the row

### row[N]

This will return the column at index N

    results.forEach(function(row){
      //gets the 5th column of each row
      console.log(row[5]);
    });

### row.get(name)

This will return the column with a specific name

    results.forEach(function(row){
      //gets the column with the name 'foo' of each row
      console.log(row.get('foo'));
    });

### row.forEach()

This is wrapper function of Array.forEach which return name,value,ts,ttl of column from row as callback params.

    results.forEach(function(row){
      //all row of result
      row.forEach(function(name,value,ts,ttl){
        //all column of row
        console.log(name,value,ts,ttl);
      });

    });

### row.slice(start, finish)

Slices columns in the row based on their numeric index, this allows you to get
columns x through y, it returns a Helenus row object of columns that match the slice.

    results.forEach(function(row){
      //gets the first 5 columns of each row
      console.log(row.slice(0,5));
    });

### row.nameSlice(start, finish)

Slices the columns based on part of their column name. returns a Helenus row of columns
that match the slice

    results.forEach(function(row){
      //gets all columns that start with a, b, c, or d
      console.log(row.nameSlice('a','e'));
    });

## Column

Columns are returned as objects with the following structure:

```javascript
  {
    name: 'Foo',       //The column name
    value: 'bar',      //The column value
    timestamp: Date(), //The date object of the timestamp for the column
    ttl: 123456        //The ttl (in milliseconds) for the columns
  }
```

## ConsistencyLevel

Helenus supports using a custom consistency level. By default, when using the thrift client reads and writes will both use `QUORUM`. When using the thrift driver, you simply pass a custom level in the options:

```javascript
cf.insert(key, values, {consistency : helenus.ConsistencyLevel.ANY}, callback);
```


## Contributors

* Russell Bradberry - @devdazed
* Matthias Eder - @matthiase
* Christoph Tavan - @ctavan

## License

(The MIT License)

Copyright (c) 2011 SimpleReach &lt;rbradberry@simplereach.com&gt;

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
'Software'), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
