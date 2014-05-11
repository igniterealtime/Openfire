# Example

```js
/*
  Imagine we have an airport with 2 gateways. Every gateway can only be used by one plane at the same time. 
  The planes should land in the order they registered their landing at the airport
*/

var channels = require("channels");

function doLanding(landing, callback)
{
  setTimeout(function()
  {
    console.log(new Date().toString() + " " +landing.planeName + " landed on " + landing.gateway);
    callback();
  },1000);
}

var airport = new channels.channels(doLanding);

airport.emit("gateway1", {planeName: "superjet1", gateway: "gateway1"});
airport.emit("gateway2", {planeName: "superjet2", gateway: "gateway2"});

airport.emit("gateway1", {planeName: "superjet3", gateway: "gateway1"});
airport.emit("gateway2", {planeName: "superjet4", gateway: "gateway2"});

airport.emit("gateway1", {planeName: "superjet5", gateway: "gateway1"});
airport.emit("gateway2", {planeName: "superjet6", gateway: "gateway2"});
```
## Output

<pre>
Thu Jul 28 2011 18:44:52 GMT+0100 (BST) superjet1 landed on gateway1
Thu Jul 28 2011 18:44:52 GMT+0100 (BST) superjet2 landed on gateway2
Thu Jul 28 2011 18:44:53 GMT+0100 (BST) superjet3 landed on gateway1
Thu Jul 28 2011 18:44:53 GMT+0100 (BST) superjet4 landed on gateway2
Thu Jul 28 2011 18:44:54 GMT+0100 (BST) superjet5 landed on gateway1
Thu Jul 28 2011 18:44:54 GMT+0100 (BST) superjet6 landed on gateway2
</pre>
