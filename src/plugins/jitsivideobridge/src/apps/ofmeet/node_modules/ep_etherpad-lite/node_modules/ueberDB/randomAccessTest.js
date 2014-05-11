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
 
var opsPerSecond = 100;
var keysLength = 10000;
var seconds = 60;

var async = require("async");
var ueberDB = require("./CloneAndAtomicLayer");
var log4js = require('log4js');

var db;

var counter = 1;
var secondsCounter = 0;
var localDB = {};

//the default settings for benchmarking
var bench_settings = require("./defaultTestSettings.js");

if(process.argv.length == 3)
{
  var settings = bench_settings[process.argv[2]];
  db = new ueberDB.database(process.argv[2], settings, null, log4js.getLogger("ueberDB"));

  db.init(function(err)
  {
    if(err) throw err;
    doOperations ()
  });
}
else
{
  console.error("wrong parameters");
}

var operationTypes = ["get", "set", "getsub", "setsub"];

function doOperations()
{
  secondsCounter++;

  //run trough all operations, fire them randomly
  for(var i=1;i<=opsPerSecond;i++)
  {  
    setTimeout(doBatch, Math.floor(Math.random()*1000));
  }
}

var progressLogger = log4js.getLogger("progress");

function doBatch()
{
  counter++;
  
  //create the operation
  var key = "key" + Math.floor(Math.random()*keysLength);
  var type = localDB[key] == null ? "set" : operationTypes[Math.floor(Math.random()*4)];

  //print progress
  if(counter % 100 == 0)
    progressLogger.info(counter + "/" + opsPerSecond*seconds);

  //test if there is a new operation to do
  if(counter % opsPerSecond == 0)
  {
    if(secondsCounter < seconds)
    {
      doOperations();
    }
    else
    {
      progressLogger.info("finished");
      process.exit(0);
    }
  }
    

  //get the value and test if its the expected value
  if(type == "get")
  {
    var shouldBeValue = JSON.stringify(localDB[key])
    db.get(key, function(err, value)
    {
      if(err) throw err;

      if(JSON.stringify(value) != shouldBeValue)
      {
        console.error("Incorrect value of " + key + ", should be: " + shouldBeValue + ", is " + JSON.stringify(value));
        process.exit(1);
      }
    });
  }
  //set the value
  else if(type == "set")
  {
    var value = {"str": "str" + counter, sub: {num: counter}};
    localDB[key] = value;
    db.set(key, value);
  }
  //get the subvalue and test if its the expected value
  else if(type == "getsub")
  {
    var shouldBeValue = JSON.stringify(localDB[key]["sub"]["num"]);
    
    db.getSub(key, ["sub", "num"], function(err, value)
    {
      if(err) throw err;          

      if(JSON.stringify(value) != shouldBeValue)
      {
        console.error("Incorrect subvalue of " + key + ", should be: " + shouldBeValue + ", is " + JSON.stringify(value));
        process.exit(1);
      }
    });
  }
  //set the subvalue
  else if(type == "setsub")
  {
    var value = {num:counter};
    localDB[key]["sub"]["num"] = counter;
    db.setSub(key, ["sub", "num"], counter);
  }
}
