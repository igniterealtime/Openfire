/**
 * 2011 Peter 'Pita' Martischka
 *
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

var async = require("async");
var redis = require("redis");

/* settings:
    {
      host: 
      port:
      socket:
      database:
      password:
      client_options
    }

*/

exports.database = function(settings) {
    this.client = null;
    this.settings = settings || {};
}

exports.database.prototype.auth = function(callback){
  if (this.settings.password)
    this.client.auth(this.settings.password,callback);
  callback();
}

exports.database.prototype.select = function(callback){
  if (this.settings.database)
    return this.client.select(this.settings.database,callback);
  callback();
}

exports.database.prototype.init = function(callback) {
  if (this.settings.socket) {
    this.client = redis.createClient(this.settings.socket, 
    this.settings.client_options);
  } else {
    this.client = redis.createClient(this.settings.port,
    this.settings.host, this.settings.client_options);
  }
  
  this.client.database = this.settings.database;
	async.waterfall([this.auth.bind(this), this.select.bind(this)],callback);
}

exports.database.prototype.get = function (key, callback) {
    this.client.get(key, callback);
}

exports.database.prototype.findKeys = function (key, notKey, callback) {
    // As redis provides only limited support for getting a list of all
    // available keys we have to limit key and notKey here.
    // See http://redis.io/commands/keys
    if(notKey==null || notKey==undefined) {
        this.client.KEYS(key, callback);
    } else if(notKey=="*:*:*") {
        // restrict key to format "text:*"
        var matches = /^([^:]+):\*$/.exec(key);
        if(matches) {
            this.client.SMEMBERS("ueberDB:keys:" + matches[1], callback);
        } else {
            callback(new customError("redis db only supports key patterns like pad:* when notKey is set to *:*:*","apierror"), null);
        }
    } else {
        callback(new customError("redis db currently only supports *:*:* as notKey","apierror"), null);
    }
}

exports.database.prototype.set = function (key, value, callback) {
    var matches = /^([^:]+):([^:]+)$/.exec(key);
    if(matches) {
        this.client.sadd(new Array("ueberDB:keys:" + matches[1], matches[0]));
    }
    this.client.set(key,value,callback);
}

exports.database.prototype.remove = function (key, callback) {
    var matches = /^([^:]+):([^:]+)$/.exec(key);
    if(matches) {
        this.client.srem(new Array("ueberDB:keys:" + matches[1], matches[0]));
    }
    this.client.del(key,callback);
}

exports.database.prototype.doBulk = function (bulk, callback) {
    var multi = this.client.multi();

    for(var i in bulk) {
        var matches = /^([^:]+):([^:]+)$/.exec(bulk[i].key);
        if(bulk[i].type == "set") {
            if(matches) {
                multi.sadd(new Array("ueberDB:keys:" + matches[1], matches[0]));
            }
            multi.set(bulk[i].key, bulk[i].value);
        } else if(bulk[i].type == "remove") {
            if(matches) {
                multi.srem(new Array("ueberDB:keys:" + matches[1], matches[0]));
            }
            multi.del(bulk[i].key);
        }
    }

    multi.exec(callback);
}

exports.database.prototype.close = function(callback) {
    this.client.end();
    callback()
}
