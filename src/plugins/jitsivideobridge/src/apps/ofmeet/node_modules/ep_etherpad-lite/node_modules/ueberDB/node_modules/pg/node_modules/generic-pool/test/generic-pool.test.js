var assert     = require('assert');
var poolModule = require('..');

module.exports = {

    'expands to max limit' : function (beforeExit) {
        var createCount  = 0;
        var destroyCount = 0;
        var borrowCount  = 0;
        
        var factory = {
            name     : 'test1',
            create   : function(callback) {
                callback(null, { count: ++createCount });
            },
            destroy  : function(client) { destroyCount++; },
            max : 2,
            idleTimeoutMillis : 100
        };

        var pool = poolModule.Pool(factory);

        for (var i = 0; i < 10; i++) {
            var full = !pool.acquire(function(err, obj) {
                return function(err, obj) {
                    assert.equal(typeof obj.count, 'number');
                    setTimeout(function() {
                        borrowCount++;
                        pool.release(obj);
                    }, 100);
                };
            }());
            assert.ok((i < 1) ^ full);
        }

        beforeExit(function() {
            assert.equal(0, factory.min);
            assert.equal(2, createCount);
            assert.equal(2, destroyCount);
            assert.equal(10, borrowCount);
        });
    },
    
    'respects min limit' : function (beforeExit) {
        var createCount  = 0;
        var destroyCount = 0;
        var borrowCount  = 0;

        var pool = poolModule.Pool({
            name     : 'test-min',
            create   : function(callback) {
                callback(null, { count: ++createCount });
            },
            destroy  : function(client) { destroyCount++; },
            min : 1,
            max : 2,
            idleTimeoutMillis : 100
        });
        pool.drain();

        beforeExit(function() {
            assert.equal(0, pool.availableObjectsCount());
            assert.equal(1, createCount);
            assert.equal(1, destroyCount);
        });
    },
    
    'min and max limit defaults' : function (beforeExit) {
      var factory = {
        name    : "test-limit-defaults",
        create  : function(callback) { callback(null, {}); },
        destroy : function(client) { },
        idleTimeoutMillis: 100
      };
      var pool = poolModule.Pool(factory);
      
      beforeExit(function() {
        assert.equal(1, factory.max);
        assert.equal(0, factory.min);
      });
    },
    
    'malformed min and max limits are ignored' : function (beforeExit) {
      var factory = {
        name    : "test-limit-defaults2",
        create  : function(callback) { callback(null, {}); },
        destroy : function(client) { },
        idleTimeoutMillis: 100,
        min : "asf",
        max : [ ]
      };
      var pool = poolModule.Pool(factory);
      
      beforeExit(function() {
        assert.equal(1, factory.max);
        assert.equal(0, factory.min);
      });
    },
    
    'min greater than max sets to max minus one' : function (beforeExit) {
      var factory = {
        name    : "test-limit-defaults3",
        create  : function(callback) { callback(null, {}); },
        destroy : function(client) { },
        idleTimeoutMillis: 100,
        min : 5,
        max : 3
      };
      var pool = poolModule.Pool(factory);
      pool.drain();
      
      beforeExit(function() {
        assert.equal(3, factory.max);
        assert.equal(2, factory.min);
      });
    },

    'supports priority on borrow' : function(beforeExit) {
        var borrowTimeLow  = 0;
        var borrowTimeHigh = 0;
        var borrowCount = 0;
        var i;

        var pool = poolModule.Pool({
            name     : 'test2',
            create   : function(callback) { callback(); },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 100,
            priorityRange : 2
        });

        for (i = 0; i < 10; i++) {
            pool.acquire(function(err, obj) {
                return function() {
                    setTimeout(function() {
                        var t = new Date().getTime();
                        if (t > borrowTimeLow) { borrowTimeLow = t; }
                        borrowCount++;
                        pool.release(obj);
                    }, 50);
                };
            }(), 1);
        }

        for (i = 0; i < 10; i++) {
            pool.acquire(function(obj) {
                return function() {
                    setTimeout(function() {
                        var t = new Date().getTime();
                        if (t > borrowTimeHigh) { borrowTimeHigh = t; }
                        borrowCount++;
                        pool.release(obj);
                    }, 50);
                };
            }(), 0);
        }

        beforeExit(function() {
            assert.equal(20, borrowCount);
            assert.equal(true, borrowTimeLow > borrowTimeHigh);
        });
    },

    'removes correct object on reap' : function (beforeExit) {
        var destroyed = [];
        var clientCount = 0;

        var pool = poolModule.Pool({
            name     : 'test3',
            create   : function(callback) { callback(null, { id : ++clientCount }); },
            destroy  : function(client) { destroyed.push(client.id); },
            max : 2,
            idleTimeoutMillis : 100
        });

        pool.acquire(function(err, client) {
            assert.equal(typeof client.id, 'number');
            // should be removed second
            setTimeout(function() { pool.release(client); }, 5);
        });
        pool.acquire(function(err, client) {
            assert.equal(typeof client.id, 'number');
            // should be removed first
            pool.release(client);
        });

        setTimeout(function() { }, 102);

        beforeExit(function() {
            assert.equal(2, destroyed[0]);
            assert.equal(1, destroyed[1]);
        });
    },

    'tests drain' : function (beforeExit) {
        var created = 0;
        var destroyed = 0;
        var count = 5;
        var acquired = 0;

        var pool = poolModule.Pool({
            name    : 'test4',
            create  : function(callback) { callback(null, {id: ++created}); },
            destroy : function(client) { destroyed += 1; },
            max : 2,
            idletimeoutMillis : 300000
        });

        for (var i = 0; i < count; i++) {
            pool.acquire(function(err, client) {
                acquired += 1;
                assert.equal(typeof client.id, 'number');
                setTimeout(function() { pool.release(client); }, 250);
            });
        }

        assert.notEqual(count, acquired);
        pool.drain(function() {
            assert.equal(count, acquired);
            // short circuit the absurdly long timeouts above.
            pool.destroyAllNow();
            beforeExit(function() {});
        });

        // subsequent calls to acquire should return an error.
        assert.throws(function() {
            pool.acquire(function(client) {});
        }, Error);
    },

    'handle creation errors' : function (beforeExit) {
        var created = 0;
        var pool = poolModule.Pool({
            name     : 'test6',
            create   : function(callback) {
                if (created < 5) {
                    callback(new Error('Error occurred.'));
                } else {
                    callback({ id : created });
                }
                created++;
            },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 1000
        });
        // ensure that creation errors do not populate the pool.
        for (var i = 0; i < 5; i++) {
            pool.acquire(function(err, client) {
                assert.ok(err instanceof Error);
                assert.ok(client === null);
            });
        }

        var called = false;
        pool.acquire(function(err, client) {
            assert.ok(err === null);
            assert.equal(typeof client.id, 'number');
            called = true;
        });
        beforeExit(function() {
            assert.ok(called);
            assert.equal(pool.waitingClientsCount(), 0);
        });
    },

    'handle creation errors for delayed creates' : function (beforeExit) {
        var created = 0;
        var pool = poolModule.Pool({
            name     : 'test6',
            create   : function(callback) {
                if (created < 5) {
                    setTimeout(function() {
                        callback(new Error('Error occurred.'));
                    }, 0);
                } else {
                    setTimeout(function() {
                        callback({ id : created });
                    }, 0);
                }
                created++;
            },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 1000
        });
        // ensure that creation errors do not populate the pool.
        for (var i = 0; i < 5; i++) {
            pool.acquire(function(err, client) {
                assert.ok(err instanceof Error);
                assert.ok(client === null);
            });
        }
        var called = false;
        pool.acquire(function(err, client) {
            assert.ok(err === null);
            assert.equal(typeof client.id, 'number');
            called = true;
        });
        beforeExit(function() {
            assert.ok(called);
            assert.equal(pool.waitingClientsCount(), 0);
        });
    },

    'pooled decorator should acquire and release' : function (beforeExit) {
        var assertion_count = 0;
        var destroyed_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback({id: Math.floor(Math.random()*1000)}); },
            destroy  : function(client) { destroyed_count += 1; },
            max : 1,
            idleTimeoutMillis : 100
        });

        var pooledFn = pool.pooled(function(client, cb) {
          assert.equal(typeof client.id, 'number');
          assert.equal(pool.getPoolSize(), 1);
          assertion_count += 2;
          cb();
        });

        assert.equal(pool.getPoolSize(), 0);
        assertion_count += 1;

        pooledFn(function(err) {
          if (err) { throw err; }
          assert.ok(true);
          assertion_count += 1;
        });

        beforeExit(function() {
          assert.equal(assertion_count, 4);
          assert.equal(destroyed_count, 1); 
        });
    },
    
    'pooled decorator should pass arguments and return values' : function(beforeExit) {
        var assertion_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback({id: Math.floor(Math.random()*1000)}); },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 100
        });

        var pooledFn = pool.pooled(function(client, arg1, arg2, cb) {
          assert.equal(arg1, "First argument");
          assert.equal(arg2, "Second argument");
          assertion_count += 2;
          cb(null, "First return", "Second return");
        });

        pooledFn("First argument", "Second argument", function(err, retVal1, retVal2) {
          if(err) { throw err; }
          assert.equal(retVal1, "First return");
          assert.equal(retVal2, "Second return");
          assertion_count += 2;
        });

        beforeExit(function() {
          assert.equal(assertion_count, 4);
        });
    },

    'pooled decorator should allow undefined callback' : function(beforeExit) {
        var assertion_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback({id: Math.floor(Math.random()*1000)}); },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 100
        });

        var pooledFn = pool.pooled(function(client, arg, cb) {
          assert.equal(arg, "Arg!");
          assertion_count += 1;
          cb();
        });

        pooledFn("Arg!");

        beforeExit(function() {
          assert.equal(pool.getPoolSize(), 0);
          assert.equal(assertion_count, 1);
        });

    },

    'pooled decorator should forward pool errors' : function(beforeExit) {
        var assertion_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback(new Error('Pool error')); },
            destroy  : function(client) { },
            max : 1,
            idleTimeoutMillis : 100
        });

        var pooledFn = pool.pooled(function(cb) {
          assert.ok(false, "Pooled function shouldn't be called due to a pool error");
        });

        pooledFn(function(err, obj) {
          assert.equal(err.message, 'Pool error');
          assertion_count += 1;
        });

        beforeExit(function() {
          assert.equal(assertion_count, 1);
        });
    },

    'getPoolSize' : function (beforeExit) {
        var assertion_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback({id: Math.floor(Math.random()*1000)}); },
            destroy  : function(client) { },
            max : 2,
            idleTimeoutMillis : 100
        });

        assert.equal(pool.getPoolSize(), 0);
        assertion_count += 1;
        pool.acquire(function(err, obj1) {
            if (err) { throw err; }
            assert.equal(pool.getPoolSize(), 1);
            assertion_count += 1;
            pool.acquire(function(err, obj2) {
                if (err) { throw err; }
                assert.equal(pool.getPoolSize(), 2);
                assertion_count += 1;

                pool.release(obj1);
                pool.release(obj2);

                pool.acquire(function(err, obj3) {
                    if (err) { throw err; }
                    // should still be 2
                    assert.equal(pool.getPoolSize(), 2);
                    assertion_count += 1;
                    pool.release(obj3);
                });
            });
        });

        beforeExit(function() {
            assert.equal(assertion_count, 4);
        });
    },

    'availableObjectsCount' : function (beforeExit) {
        var assertion_count = 0;
        var pool = poolModule.Pool({
            name     : 'test1',
            create   : function(callback) { callback({id: Math.floor(Math.random()*1000)}); },
            destroy  : function(client) { },
            max : 2,
            idleTimeoutMillis : 100
        });

        assert.equal(pool.availableObjectsCount(), 0);
        assertion_count += 1;
        pool.acquire(function(err, obj1) {
            if (err) { throw err; }
            assert.equal(pool.availableObjectsCount(), 0);
            assertion_count += 1;

            pool.acquire(function(err, obj2) {
                if (err) { throw err; }
                assert.equal(pool.availableObjectsCount(), 0);
                assertion_count += 1;

                pool.release(obj1);
                assert.equal(pool.availableObjectsCount(), 1);
                assertion_count += 1;

                pool.release(obj2);
                assert.equal(pool.availableObjectsCount(), 2);
                assertion_count += 1;

                pool.acquire(function(err, obj3) {
                    if (err) { throw err; }
                    assert.equal(pool.availableObjectsCount(), 1);
                    assertion_count += 1;
                    pool.release(obj3);

                    assert.equal(pool.availableObjectsCount(), 2);
                    assertion_count += 1;
                });
            });
        });

        beforeExit(function() {
            assert.equal(assertion_count, 7);
        });
    },

    'logPassesLogLevel': function(beforeExit){
        var loglevels = {'verbose':0, 'info':1, 'warn':2, 'error':3};
        var logmessages = {verbose:[], info:[], warn:[], error:[]};
        var factory = {
            name     : 'test1',
            create   : function(callback) {callback(null, {id:Math.floor(Math.random()*1000)}); },
            destroy  : function(client) {},
            max      : 2,
            idleTimeoutMillis: 100,
            log      : function(msg, level) {testlog(msg, level);}
        };
        var testlog = function(msg, level){
            assert.ok(level in loglevels);
            logmessages[level].push(msg);
        };
        var pool = poolModule.Pool(factory);

        var pool2 = poolModule.Pool({
            name     : 'testNoLog',
            create   : function(callback) {callback(null, {id:Math.floor(Math.random()*1000)}); },
            destroy  : function(client) {},
            max      : 2,
            idleTimeoutMillis: 100
        });
        assert.equal(pool2.getName(), 'testNoLog');

        pool.acquire(function(err, obj){
          if (err) {throw err;}
          assert.equal(logmessages.verbose[0], 'createResource() - creating obj - count=1 min=0 max=2');
          assert.equal(logmessages.info[0], 'dispense() clients=1 available=0');
          logmessages.info = [];
          logmessages.verbose = [];
          pool2.borrow(function(err, obj){
            assert.equal(logmessages.info.length, 0);
            assert.equal(logmessages.verbose.length, 0);
            assert.equal(logmessages.warn.length, 0);
          });
        });
    },
    
    'removes from available objects on destroy': function(beforeExit){
        var destroyCalled = false;
        var factory = {
            name: 'test',
            create: function(callback) {callback(null, {}); },
            destroy: function(client) {destroyCalled = true; },
            max: 2,
            idleTimeoutMillis: 100
        };

        var pool = poolModule.Pool(factory);
        pool.acquire(function(err, obj){
            pool.destroy(obj);            
        });
        assert.equal(destroyCalled, true);
        assert.equal(pool.availableObjectsCount(), 0);        
    },

    'removes from available objects on validation failure': function(beforeExit){
        var destroyCalled = false,
            validateCalled = false,
            count = 0;
        var factory = {
            name: 'test',
            create: function(callback) {callback(null, {count: count++}); },
            destroy: function(client) {destroyCalled = client.count; },
            validate: function(client) {validateCalled = true; return client.count != 0;},
            max: 2,
            idleTimeoutMillis: 100
        };

        var pool = poolModule.Pool(factory);
        pool.acquire(function(err, obj){
            pool.release(obj);
            assert.equal(obj.count, 0);

            pool.acquire(function(err, obj){
                pool.release(obj);
                assert.equal(obj.count, 1);
            });
        });
        assert.equal(validateCalled, true);
        assert.equal(destroyCalled, 0);
        assert.equal(pool.availableObjectsCount(), 1);
    },

    'do schedule again if error occured when creating new Objects async': function(beforeExit){
        var factory = {
            name: 'test',
            create: function(callback) {
              process.nextTick(function(){
                var err = new Error('Create Error');
                callback(err); 
              })
            },
            destroy: function(client) {},
            max: 1,
            idleTimeoutMillis: 100
        };

        var getFlag = 0;
        var pool = poolModule.Pool(factory);
        pool.acquire(function(){});
        pool.acquire(function(err, obj){
           getFlag = 1;
           assert(err);
           assert.equal(pool.availableObjectsCount(), 0);        
       });

       beforeExit(function() {
         assert.equal(getFlag, 1);   
       });
    }


};
