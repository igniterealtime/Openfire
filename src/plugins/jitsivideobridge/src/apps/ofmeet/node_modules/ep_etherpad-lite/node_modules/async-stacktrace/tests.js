var vows = require('vows');
var assert = require('assert');
var ERR = require("./ERR");

var emptyFunc = function(){};

vows.describe('ERR function').addBatch({
  'when its loaded' : {
    topic: function() {
      return require("./ERR");
    },
    
    'it returns the ERR function': function(topic){
      assert.equal(typeof topic, "function");
    },
    
    'its not global': function(topic){
      assert.isUndefined(global.ERR);
    }
  }
}).addBatch({
  'when you call it without an error' : {
    'it returns false' : function() {
      assert.isFalse(ERR());
    }
  },
  'when you call it with an error' : {
    'it returns true' : function() {
      assert.isTrue(ERR(new Error(), emptyFunc));
    }
  },
  'when you give it a callback and an error': {
    'it calls the callback with the error' : function() {
      ERR(new Error(), function(err) {
        assert.isNotNull(err);
      });
    }
  },
  'when you give it no callback and an error': {
    'it throws the error' : function() {
      var wasThrown = false;
      
      try
      {
        ERR(new Error());
      }
      catch(e)
      {
        wasThrown = true;
      }
      
      assert.isTrue(wasThrown);
    }
  },
  'when you call it with a string as an error' : {
    'it uses the string as an error message' : function(){
      var errStr = "testerror";
      var err;
      ERR(errStr, function(_err){
        err = _err;
      });
      
      assert.equal(err.message, errStr);
    }
  },
  'when you give it a non-async stacktrace': {
    'it turns it into an async stacktrace' : function(){
      var errorBefore = new Error();
      var stackLinesBefore = errorBefore.stack.split("\n").length;
      var errorAfter;
      
      ERR(errorBefore, function(err){
        errorAfter = err;
      });
      
      var stackLinesAfter = errorAfter.stack.split("\n").length;
      
      assert.equal(stackLinesBefore+3, stackLinesAfter);
    }
  },
  'when you give it a async stacktrace': {
    'it adds a new stack line' : function(){
      var err = new Error();
      
      ERR(err, function(_err){
        err = _err;
      });
      
      var stackLinesBefore = err.stack.split("\n").length;
      
      ERR(err, function(_err){
        err = _err;
      });
      
      var stackLinesAfter = err.stack.split("\n").length;
      
      assert.equal(stackLinesBefore+1, stackLinesAfter);
    }
  }
}).run();    
