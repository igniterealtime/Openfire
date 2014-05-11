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

var mysql = require("mysql");
var async = require("async");

exports.database = function(settings)
{
  this.db = new mysql.Client();
  
  this.settings = settings;
  
  if(this.settings.host != null)
    this.db.host = this.settings.host;
    
  if(this.settings.port != null)
    this.db.port = this.settings.port;
    
  if(this.settings.user != null)
    this.db.user = this.settings.user;
  
  if(this.settings.password != null)
    this.db.password = this.settings.password;
    
  if(this.settings.database != null)
    this.db.database = this.settings.database;
  
  this.settings.cache = 1000;
  this.settings.writeInterval = 100;
  this.settings.json = true;
}

exports.database.prototype.init = function(callback)
{
  var sqlCreate = "CREATE TABLE IF NOT EXISTS `store` ( " +
                  "`key` VARCHAR( 100 ) NOT NULL COLLATE utf8_general_ci, " + 
                  "`value` LONGTEXT NOT NULL , " + 
                  "PRIMARY KEY (  `key` ) " +
                  ") ENGINE = INNODB;"; 
                  
  var sqlAlter  = "ALTER TABLE store MODIFY `key` VARCHAR(100) COLLATE utf8_general_ci;";

  var db = this.db;
  var self = this;

  db.query(sqlCreate,[],function(err){
    //call the main callback
    callback(err);
    
    //check migration level, alter if not migrated
    self.get("MYSQL_MIGRATION_LEVEL", function(err, level){
      if(err){
        throw err;
      }
      
      if(level !== "1"){
        db.query(sqlAlter,[],function(err)
        {
          if(err){
            throw err;
          }
          
          self.set("MYSQL_MIGRATION_LEVEL","1", function(err){
            if(err){
              throw err;
            }
          });
        });
      }
    })
  });
}

exports.database.prototype.get = function (key, callback)
{
  this.db.query("SELECT `value` FROM `store` WHERE  `key` = ?", [key], function(err,results)
  {
    var value = null;
    
    if(!err && results.length == 1)
    {
      value = results[0].value;
    }
  
    callback(err,value);
  });
}

exports.database.prototype.findKeys = function (key, notKey, callback)
{
  var query="SELECT `key` FROM `store` WHERE  `key` LIKE ?"
    , params=[]
  ;
  
  //desired keys are key, e.g. pad:%
  key=key.replace(/\*/g,'%');
  params.push(key);
  
  if(notKey!=null && notKey != undefined){
    //not desired keys are notKey, e.g. %:%:%
    notKey=notKey.replace(/\*/g,'%');
    query+=" AND `key` NOT LIKE ?"
    params.push(notKey);
  }
  this.db.query(query, params, function(err,results)
  {
    var value = [];
    
    if(!err && results.length > 0)
    {
      results.forEach(function(val){
        value.push(val.key);
      });
    }
  
    callback(err,value);
  });
}

exports.database.prototype.set = function (key, value, callback)
{
  if(key.length > 100)
  {
    callback("Your Key can only be 100 chars");
  }
  else
  {
    this.db.query("REPLACE INTO `store` VALUES (?,?)", [key, value], function(err, info){
      callback(err);
    });
  }
}

exports.database.prototype.remove = function (key, callback)
{
  this.db.query("DELETE FROM `store` WHERE `key` = ?", [key], callback);
}

exports.database.prototype.doBulk = function (bulk, callback)
{ 
  var _this = this;
  
  var replaceSQL = "REPLACE INTO `store` VALUES ";
  var removeSQL = "DELETE FROM `store` WHERE `key` IN ("
  
  var firstReplace = true;
  var firstRemove = true;
  
  for(var i in bulk)
  {  
    if(bulk[i].type == "set")
    {
      if(!firstReplace)
        replaceSQL+=",";
      firstReplace = false;
    
      replaceSQL+="(" + _this.db.escape(bulk[i].key) + ", " + _this.db.escape(bulk[i].value) + ")";
    }
    else if(bulk[i].type == "remove")
    {
      if(!firstRemove)
        removeSQL+=",";
      firstRemove = false;
    
      removeSQL+=_this.db.escape(bulk[i].key);
    }
  }
  
  replaceSQL+=";";
  removeSQL+=");";
  
  async.parallel([
    function(callback)
    {
      if(!firstReplace)
        _this.db.query(replaceSQL, callback);
      else
        callback();
    },
    function(callback)
    {
      if(!firstRemove)
        _this.db.query(removeSQL, callback);
      else
        callback();
    }
  ], callback);
  
}

exports.database.prototype.close = function(callback)
{
  this.db.end(callback);
}
