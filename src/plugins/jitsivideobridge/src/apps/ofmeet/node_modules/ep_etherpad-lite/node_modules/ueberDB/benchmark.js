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

//settings
var maxValues = 100000;
var writes = 210;
var reads = 100;
var updates = 20;
var deletes = 10;
var initialWrites = writes-deletes;
var valueLength = 1000;
var randomized = true;

var db;

//counts how man keys are in the db
var counter = 0;
var timeSum = null;
var randomStrings = [];
var valueMap = {};

//the default settings for benchmarking
var bench_settings = require("./defaultTestSettings.js");

if(process.argv.length == 3)
{
  var settings = bench_settings[process.argv[2]];
  db = new ueberDB.database(process.argv[2], settings);

  benchmark(function(err, time)
  {
    if(err) console.error(err.stack ? err.stack : err);
    console.log("finished");
    console.log("time: " +  time + "s");
    process.exit(0);
  })  
}

//the benchmark function
function benchmark(callback)
{
  var startTime = new Date().getTime();

  console.log("generating random data, to fill database...");
  for(var i=0;i<1000;i++)
  {
    randomStrings.push(randomString(valueLength));
  }
  console.log("done");

  async.series([
    //init db
    function(callback)
    {
      console.log("initalize database...");
      db.init(callback);
    },
    //fill the database with the inital values
    function(callback)
    {
      console.log("done");
    
      console.log("do the first writings to fill the database...");
      doOperations(generateOperations(counter, counter + initialWrites, initialWrites, "write"), false, callback);
      
      //increment counter
      counter+=initialWrites;
    },
    function(callback)
    { 
      console.log("done");
      
      console.error("values;read_time;write_time;update_time;delete_time;memory_mb");
      
      var round = 1;
      
      //async while loop
      async.whilst(
        function () { return counter < maxValues; },
        function (callback) 
        {        
          if(round%50 == 0)
          {
            console.log("persistence test");
            persistenceTest(callback);
            round++;
            return;
          }
          else
          {
            if(timeSum != null)
            {        
              var readTime = Math.round(timeSum["read"]/reads);
              var writeTime = Math.round(timeSum["write"]/writes);
              var updateTime = Math.round(timeSum["update"]/updates);
              var deleteTime = Math.round(timeSum["delete"]/deletes);
              var memoryUsage = Math.round(process.memoryUsage().heapUsed/1024/1024);
                
              var csvLine = counter + ";" + readTime + ";" + writeTime + ";" + updateTime + ";" + deleteTime + ";" + memoryUsage;
              
              console.error(csvLine);        
              console.log("rows: " + counter); 
            
              //increment counter
              counter+=writes-deletes; 
            }
          
            timeSum = {"read":0, "write":0,"update":0,"delete":0};
          
            var writeOps = generateOperations(counter, counter + writes, writes, "write");
            var readOps = generateOperations(0, counter, reads, "read");
            var updateOps = generateOperations(0, counter, updates, "update");
            var deleteOps = generateOperations(0, counter, deletes, "delete");
            
            if(randomized)
            {
              //put all operations together and shuffle them
              var operations = writeOps.concat(readOps,updateOps,deleteOps).sort(function() {return 0.5 - Math.random()});
              
              //execute the operations
              doOperations(operations, true, callback);
            }
            else
            {
              async.waterfall([
                function(callback)
                {
                  console.error("write");
                  doOperations(writeOps, true, callback);
                },
                function(callback)
                {
                  console.error("read");
                  doOperations(readOps, true, callback);
                },
                function(callback)
                {
                  console.error("update");
                  doOperations(updateOps, true, callback);
                },
                function(callback)
                {
                  console.error("delete");
                  doOperations(deleteOps, true, callback); 
                },
              ],callback);
            }
          }
          
          round++;
        },callback);
    }
  ], function(err)
  {
    db.close(function()
    {
      var time = Math.round((new Date().getTime()-startTime)/1000);
      callback(err, time);
    });
  });
}

function generateOperations(startNum, endNum, size, type)
{
  var operations = [];

  var keyNum = startNum;

  for(var i=0;i<size;i++)
  {
    var key;  
    if(type == "write")
    {
      key = "key" + keyNum;
      keyNum++;
    }
    else
    {
      key = "key" + (startNum + uniqueRandomNum(endNum-startNum))
    }
    
    var operation = {type:type, key:key};
    
    if(type == "write" || type == "update")
    {
      var StringNum = Math.floor(Math.random() * 1000); 
      operation.value = randomStrings[StringNum];
      valueMap[key] = StringNum;
    }
    
    if(type == "delete")
    {
      valueMap[key] = null;
    }
    
    operations.push(operation);
  }
  
  return operations;
}

function doOperations(operations, measure, callback)
{
  async.forEach(operations, function(item, _callback)
  {    
    //slow down the callback, this ensures the db gets time write the values
    var callback = function(err)
    {
      setTimeout(_callback, 1, err);
    };
  
    //if write or update, call set
    if(item.type == "write" || item.type == "update")
    {
      if(measure)
      {
         measureTime(function(callback)
         {
           db.set(item.key, item.value, null, callback);
         },item.type, callback);
      }
      else
      {
        db.set(item.key, item.value, null, callback);
      }  
    }
    else if(item.type == "read")
    {
      if(measure)
      {
        measureTime(function(callback){
          db.get(item.key, callback);
        },item.type, callback); 
      }
      else
      {
        db.get(item.key, callback);
      }
    }
    else if(item.type == "delete")
    {
      if(measure)
      {
        measureTime(function(callback){
          db.remove(item.key, null, callback);
        },item.type, callback); 
      }
      else
      {
        db.remove(item.key, null, callback);
      }
    }
    else
    {
      callback("Unkown Operation!");
    }    
  },function(err)
  {
    callback(err);
  });
}

function persistenceTest(callback)
{  
  //get all keys in a array
  var keys = [];
  for(var i in valueMap)
  {
    keys.push(i);
  }
  
  //test for inconsistence
  async.forEach(keys, function(item, callback)
  {
    db.get(item, function(err, value)
    {
      if(!err)
      {
        if(value != randomStrings[valueMap[item]])
        {
          err = "Inconsistent key : '" + item + "'\n";
          err+= "is               : '" + value + "'\n";
          err+= "should be        : '" + randomStrings[valueMap[item]] + "'";
        }
      }
      
      callback(err);
    });
  }, callback);
}

function measureTime(func, type, callback){
  var startTime = new Date().getTime();  
  
  func(function(err){
    var time = new Date().getTime()-startTime;
    timeSum[type]+=time;
    
    callback(err);
  });
}

var lastRands = [];

// This Function ensures that the generated random values for the 
// deletes/updates are never the same in one round
function uniqueRandomNum(maxNum)
{
  var rand;
  var isUnique = false;
  
  while(!isUnique)
  {
    rand = Math.floor(Math.random() * maxNum);
    
    isUnique = true;
    
    //try to proof that its not unique
    for(var i in lastRands)
    {
      if(lastRands[i] == rand)
      {
        isUnique = false;
        break;
      }
    }
  }
  
  lastRands.push(rand);
  
  //remove the first element if lastRands is too long
  if(lastRands.length > (reads+updates+deletes)*2)
  {
    lastRands.shift();
  }
  
  return rand;
}

function randomString(string_length) 
{
	var chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
	var randomstring = '';
	
	for (var i=0; i<string_length; i++) 
	{
		var rnum = Math.floor(Math.random() * chars.length);
		randomstring += chars.substring(rnum,rnum+1);
	}
	
	return randomstring;
}
