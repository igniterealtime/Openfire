`follow-redirects` extends http and https with the ability to follow
HTTP redirects painlessly. It does not modify the native modules but
instead offers its own http/https modules which inherit from the native
modules. If you want to automatically follow redirects, all you need to
do is replace: 

```javascript
var http = require('http');
```

by

```javascript
var http = require('follow-redirects').http;
```

# Install

    npm install follow-redirects

# Usage

```javascript

var http = require('follow-redirects').http;
var https = require('follow-redirects').https;

/* 
 * http and https are just like Node.js' http and https modules except 
 * that they follow redirects seamlessly. 
 */

http.get('http://bit.ly/900913', function (res) {
  res.on('data', function (chunk) {
    console.log(chunk);
  });
}).on('error', function (err) {
  console.error(err);
});

/*
 * You can optionnally pass the maxRedirect option which defaults to 5
 */

https.request({
  host: 'bitly.com',
  path: '/UHfDGO',
  maxRedirects: 3
}, function (res) {
  res.on('data', function (chunk) {
    console.log(chunk);
  });
}).on('error', function (err) {
  console.error(err);
});

```
