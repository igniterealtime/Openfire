/*
* Query all languages supported by translatewiki by API
* Use: node tw_api.js > languages.json
*/

/*
* Copyright 2012
* Iván Eixarch <ivan@sinanimodelucro.org>
* https://github.com/joker-x/languages4translatewiki
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
* MA 02110-1301, USA.
*/

/* CONFIGURATION */
var target = 'http://translatewiki.net/w/api.php?action=query&meta=siteinfo&siprop=languages&format=json';

/* PROGRAM */
var http = require ('http');

var json = '{'+"\n"+'"attribute":{"nativeName":0},'+"\n"+'"rtl":["ar","dv","fa","ha","he","ks","ku","ps","ur","yi"],'+"\n"+'"lang":{'+"\n"

// build json with translatewiki web API
var request = http.request (target,
  function (res) {
    var twLangs = ''
      , num_langs = 0;
    res.setEncoding ('utf8');
    res.on ('data', function (chunk) { twLangs += chunk; });
    res.on ('end', function () {
      // twLangs = [{code: 'en', '*': 'English'}...]
      twLangs = JSON.parse(twLangs)['query']['languages'];

      for (var l = 0; l < twLangs.length; l++) {
        var code = twLangs[l]['code']
          , nativeName = twLangs[l]['*'];
        json += '"'+code+'":["'+nativeName+'"]';
        if (l < (twLangs.length-1)) json += ",\n";
      }

      json += '}'+"\n"+
      '}';
      console.log(json);
    });
  }).on ('error', function(e) {
    console.error('While query translatewiki API: '+e.message);
  }).end();
