/*
* Scrap all languages supported by translatewiki
* Depends of jsdom
* Use: node scrap.js > languages.json
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
var target = 'http://translatewiki.net/wiki/Special:SupportedLanguages';
var debug = false;

/* PROGRAM */
var jsdom = require('jsdom');

jsdom.env(target, ['//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min.js'],
function(errors, window) {

  var decodeHTML = function(text) {
    return $('<div />').html($.trim(text)).text();
  }

  var json = '{'+"\n"+'"attribute":{"name":0,"nativeName":1},'+"\n"+'"rtl":["ar","dv","fa","ha","he","ks","ku","ps","ur","yi"],'+"\n"+'"lang":{'+"\n"
    , $ = window.$
    , num_langs = 0;

  $('h2').each(function() {
    var langcode = $(this).attr('id')
      , texto = $(this).text().split(']')[1].split('-')
      , name = decodeHTML(texto[0])
      , nativeName = decodeHTML(texto[1]);
    json += '"'+langcode+'":["'+name+'","'+nativeName+'"],'+"\n";
    num_langs++;
  });
  json += '}'+"\n"+
    '}';
  console.log(json);
  if (debug) console.log(num_langs);
});
