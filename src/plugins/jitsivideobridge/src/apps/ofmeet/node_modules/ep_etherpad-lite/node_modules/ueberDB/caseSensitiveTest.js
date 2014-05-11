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
var ueberDB = require("./CloneAndAtomicLayer");
var log4js = require('log4js');
var assert = require('assert');
var util = require("util");

//the default settings for benchmarking
var test_settings = require("./defaultTestSettings.js");
var db;

if(process.argv.length == 3)
{
  var settings = test_settings[process.argv[2]];
  test();
}
else
{
  console.error("wrong parameters");
}

function test ()
{
  async.series([
    //init first db
    function(callback)
    {
      db = new ueberDB.database(process.argv[2], settings, null, log4js.getLogger("ueberDB"));
      db.init(callback);
    },
    //write first value
    function(callback)
    {
      db.db.wrappedDB.set("test", "test", callback);
    },
    //write second value
    function(callback)
    {
      db.db.wrappedDB.set("TEST", "TEST", callback);
    },
    //get first value back
    function(callback)
    {
      db.db.wrappedDB.get("test", function(err, value){
        if(err)
        {
          callback(err);
          return;
        }
        
        try{
          assert.equal(value, "test", "lowercase value got overwritten " + value);
        }
        catch(e)
        {
          console.error(e.message);
        }
        
        callback();
      });
    },
    //get second value back
    function(callback)
    {
      db.db.wrappedDB.get("TEST", function(err, value){
        if(err)
        {
          callback(err);
          return;
        }
        
        try{
          assert.equal(value, "TEST", "uppercase value got overwritten " + value);
        }
        catch(e)
        {
          console.error(e.message);
        }
        
        callback();
      });
    }
  ], function(err){
    if(err) throw err;
    
    process.exit();
  });
}



