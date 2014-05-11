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
