/**
 * Mariano Julio Vicario aka Ranu - TW: @el_ranu
 * http://www.ranu.com.ar
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


exports.database = function (settings) {
    this.db = null;
    this.mongo = null;

    if (!settings || typeof settings.dbname != 'string' || typeof settings.port != 'number') {
        throw "some settings are incorrect or not  complete";
    }

    this.settings = settings;
    this.settings.cache = 1000;
    this.settings.writeInterval = 100;
    this.settings.json = true;
    
}

exports.database.prototype.init = function(callback) {
    this.mongo = new MongoKeyValue(this.settings.dbname, this.settings.host, this.settings.port, this.settings.user, this.settings.password, function (err) {
        callback(err);
    }, this.settings.collectionName);
}

exports.database.prototype.get = function (key, callback) {
    this.mongo.findOne(key, callback);
}

exports.database.prototype.findKeys = function (key, notKey, callback) {
    var findKey=this.createFindRegex(key,notKey);
    this.mongo.find(findKey, callback);
}

exports.database.prototype.set = function (key, value, callback) {
    this.mongo.set(key, value, callback);
}

exports.database.prototype.remove = function (key, callback) {
    this.mongo.remove(key, callback);
}

exports.database.prototype.doBulk = function (bulk, callback) {
    this.mongo.bulk(bulk, callback);
}

exports.database.prototype.close = function (callback) {
    this.mongo.close(callback);
    this.mongo = null;
}

function escape (val) 
{
  return "'"+val.replace(/'/g, "''")+"'";
};


function MongoKeyValue(dbName, dbHost, dbPort, dbUser, dbPass, fncallback, collectionname) {
    fncallback = typeof (fncallback) == 'function' ? fncallback : null ;
    var mongodb = require('mongodb').Db;
    var Server = require('mongodb').Server;
    var Connection = require('mongodb').Connection; 
    this.collectionName = typeof collectionname === 'string' ? collectionname : "store";
    
    this.db = new mongodb(dbName, new Server(dbHost, dbPort, { auto_reconnect: true }), {});
    this.collection = null;
    var me = this;
    var callback = fncallback;
    this.db.open(function (err, colle) {
        me.collection = colle;
        if (dbUser && dbPass) {
            me.db.authenticate(dbUser, dbPass, function (err) {
                ensureIndex();
                callback(err);
            });
        }
        else {
            ensureIndex();
            callback(err);
        }

    });

    function ensureIndex() {
        me.db.collection(me.collectionName, function (err, collection) {
            collection.ensureIndex({key :1}, function(err, name){
                var name;
            });
        }); 
    }
    
    this.find = function(key, callback) {
      me.db.collection(me.collectionName, function (err, collection) {
            if (err) callback(err);
            var p = collection.find({ key: key }, { _id:0, key: 1 }).toArray(function (err, ret) {
                if (ret){
                    var keys=[];
                    ret.forEach(function(val){
                        keys.push(val.key);
                    });
                    callback(err, keys);
                }else{
                    callback(err, ret);
                }
            });
        });
    }

    this.findOne = function (key, callback) {
        me.db.collection(me.collectionName, function (err, collection) {
            if (err) callback(err);
            var p = collection.findOne({ key: key }, function (err, ret) {
                if (ret)
                    callback(err, ret.val);
                else
                    callback(err, ret);
            })
        });
    }

    this.set = function (key, value, callback) {
        me.db.collection(me.collectionName, function (err, collection) {
            collection.update({key: key}, { key: key, val: value }, { safe: true, upsert: true }, function (err, docs) {
                callback(err);
            });
        });
    }

    this.bulk = function (bulk, callback) {
        var co = 1;
        me.db.collection(me.collectionName, function (err, collection) {
            for (var i in bulk) {
                if (bulk[i].type == "set") {
                    collection.update({ key: bulk[i].key }, { key: bulk[i].key, val: bulk[i].value }, { safe: true, upsert: true }, function (err, docs) {
                        if (err) console.log(err);
                        if (co === bulk.length) {
                            callback(err);
                            co= 1;
                        }
                        co++;
                    });
                }
                else if (bulk[i].type == "remove") {
                    collection.remove({ key: bulk[i].key }, { safe: true }, function (err) {
                        if (err) console.log(err);
                        if ( co === bulk.length) {
                            callback(err);
                            co = 1;
                        }
                        co++;
                    });
                }
            }
        });
    }

    this.remove = function (key, callback) {
        me.db.collection(me.collectionName, function (err, collection) {
            collection.remove({ key: key }, { safe: true }, function (err) {
                callback(err);
            });
        });
    }

    this.close = function (callback) {
        me.db.close(function (err) {
            callback(err);
        });
    }
}
