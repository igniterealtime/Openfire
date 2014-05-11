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

try
{
  var sqlite3 = require("sqlite3");  
}
catch(e)
{
  console.error("FATAL: The sqlite dependency could not be loaded. We removed it from the dependencies since it caused problems on several Platforms to compile it. If you still want to use sqlite, do a 'npm install sqlite3' in your etherpad-lite root folder");
  process.exit(1);
}

var async = require("async");

exports.database = function(settings)
{
  this.db=null; 
  
  if(!settings || !settings.filename)
  {
    settings = {filename:":memory:"};
  }
  
  this.settings = settings;
  
  //set settings for the dbWrapper
  if(settings.filename == ":memory:")
  {
    this.settings.cache = 0;
    this.settings.writeInterval = 0;
    this.settings.json = true;
  }
  else
  {
    this.settings.cache = 1000;
    this.settings.writeInterval = 100;
    this.settings.json = true;
  }
}

exports.database.prototype.init = function(callback)
{
  var _this = this;

  async.waterfall([
    function(callback)
    {
      _this.db = new sqlite3.cached.Database(_this.settings.filename, callback);
    },
    function(callback)
    {
      var sql = "CREATE TABLE IF NOT EXISTS store (key TEXT PRIMARY KEY,value TEXT)";
      _this.db.run(sql,callback);
    } 
  ],callback);
}

exports.database.prototype.get = function (key, callback)
{
  this.db.get("SELECT value FROM store WHERE key = ?", key, function(err,row)
  {
    callback(err,row ? row.value : null);
  });
}

exports.database.prototype.findKeys = function (key, notKey, callback)
{
  var query="SELECT key FROM store WHERE key LIKE ?"
    , params=[]
  ;
  //desired keys are %key:%, e.g. pad:%
  key=key.replace(/\*/g,'%');
  params.push(key);
  
  if(notKey!=null && notKey != undefined){
    //not desired keys are notKey:%, e.g. %:%:%
    notKey=notKey.replace(/\*/g,'%');
    query+=" AND key NOT LIKE ?"
    params.push(notKey);
  }
  
  this.db.all(query, params, function(err,results)
  {
    var value = [];
    
    if(!err && Object.keys(results).length > 0)
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
  this.db.run("REPLACE INTO store VALUES (?,?)", key, value, callback);
}

exports.database.prototype.remove = function (key, callback)
{
  this.db.run("DELETE FROM store WHERE key = ?", key, callback);
}

exports.database.prototype.doBulk = function (bulk, callback)
{ 
  var sql = "BEGIN TRANSACTION;\n";
  for(var i in bulk)
  {
    if(bulk[i].type == "set")
    {
      sql+="REPLACE INTO store VALUES (" + escape(bulk[i].key) + ", " + escape(bulk[i].value) + ");\n";
    }
    else if(bulk[i].type == "remove")
    {
      sql+="DELETE FROM store WHERE key = " + escape(bulk[i].key) + ";\n";
    }
  }
  sql += "END TRANSACTION;";
  
  this.db.exec(sql, function(err){
    if(err)
    {
      console.error("ERROR WITH SQL: ");
      console.error(sql);
    }
    
    callback(err);
  });
}

exports.database.prototype.close = function(callback)
{
  this.db.close();
  callback(null)
}

function escape (val) 
{
  return "'"+val.replace(/'/g, "''")+"'";
};
