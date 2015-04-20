#Candy Timeago plugin

This plugin replaces the exact time/date with 'fuzzy timestamps' (e.g. 'less than a minute ago', '2 minutes ago', 'about an hour ago'). The timestamps update dynamically. All the heavy lifting is done by Ryan McGeary's excellent jQuery Timeago plugin (http://timeago.yarp.com/).

##Usage

To enable Timeago include it's JavaScript code and CSS file (after the main Candy script and CSS):

```html
<script type="text/javascript" src="candyshop/timeago/candy.js"></script>
<link rel="stylesheet" type="text/css" href="candyshop/timeago/candy.css" />
```

Then call its init() method after Candy has been initialized:

```html
Candy.init('/http-bind/');
CandyShop.Timeago.init();
Candy.Core.connect();
```