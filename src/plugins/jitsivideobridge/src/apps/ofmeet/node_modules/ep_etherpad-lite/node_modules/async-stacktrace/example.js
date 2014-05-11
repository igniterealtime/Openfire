var ERR = require("./ERR");

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
