## Why classic stacktraces are not very helpful when dealing with async functions

Look at this example. `one` calls `two`, `two` calls `three`, and `three` calls `four`. All functions call the given callback asynchronous. `four` calls the callback with an error. `three` and `two` passes the error to their callback function and stop executing with `return`. `one` finally throws it 

```js
function one()
{
   two(function(err){
     if(err){
       throw err;
     }
   
     console.log("two finished");
   });
}

function two(callback)
{
  setTimeout(function () { 
    three(function(err)
    {
      if(err) {
        callback(err);
        return;
      }
      
      console.log("three finished");
      callback();
    });
  }, 0);
}

function three(callback)
{
  setTimeout(function () { 
    four(function(err)
    {
      if(err) {
        callback(err);
        return;
      } 
      
      console.log("four finished");
      callback();
    });
  }, 0);
}

function four(callback)
{
  setTimeout(function(){
    callback(new Error());
  }, 0);
}

one();
```

### When you execute it, you will get this:

```
$ node example_without.js 

/home/pita/Code/async-stacktrace/example_without.js:5
       throw err;
       ^
Error
    at Timer.callback (/home/pita/Code/async-stacktrace/example_without.js:47:14)
```

### The problems here are:

* You can see that the error happend in `four`, but you can't see from where `four` was called. The context gets lost
* You write the same 4 lines over and over again, just to handle errors

## The solution

### Lets replace this code in `two` and `three` 

```js
if(err) {
  callback(err);
  return;
}
```

### with

```js
if(ERR(err, callback)) return;
```

### and replace this code in `one`

```js
if(err){
  throw err;
}
```

### with

```js
ERR(err);
```

### This is how it looks like now: 

```js
var ERR = require("async-stacktrace");

function one()
{
   two(function(err){
     ERR(err);
   
     console.log("two finished");
   });
}

function two(callback)
{
  setTimeout(function () { 
    three(function(err)
    {
      if(ERR(err, callback)) return;
      
      console.log("three finished");
      callback();
    });
  }, 0);
}

function three(callback)
{
  setTimeout(function () { 
    four(function(err)
    {
      if(ERR(err, callback)) return;
      
      console.log("four finished");
      callback();
    });
  }, 0);
}

function four(callback)
{
  setTimeout(function(){
    callback(new Error());
  }, 0);
}

one();
```

### When you execute it, you will get this:

```
$ node example.js 

/home/pita/Code/async-stacktrace/ERR.js:57
      throw err;
      ^
Async Stacktrace:
    at /home/pita/Code/async-stacktrace/example.js:6:6
    at /home/pita/Code/async-stacktrace/example.js:17:10
    at /home/pita/Code/async-stacktrace/example.js:30:10

Error
    at Timer.callback (/home/pita/Code/async-stacktrace/example.js:41:14)
```

### What is new?

The "Async Stacktrace" shows you where this error was caught and passed to the next callback. This allows you to see from where `four` was called. You also have less code to write

## npm
```
npm install async-stacktrace
```

## Usage

This is how you require the ERR function

```js
var ERR = require("async-stacktrace");
```

The parameters of `ERR()` are: 

1. `err` The error object (can be a string that describes the error too)
2. `callback` (optional) If the callback is set and an error is passed, it will call the callback with the modified stacktrace. Else it will throw the error

The return value is true if there is an error. Else its false
