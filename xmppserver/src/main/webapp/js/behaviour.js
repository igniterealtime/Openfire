/*
   Behaviour v1.1 by Ben Nolan, June 2005. Based largely on the work
   of Simon Willison (see comments by Simon below).

   Description:
    
    Uses css selectors to apply javascript behaviours to enable
    unobtrusive javascript in html documents.
    
   Usage:   
   
    var myrules = {
        'b.someclass' : function(element){
            element.onclick = function(){
                alert(this.innerHTML);
            }
        },
        '#someid u' : function(element){
            element.onmouseover = function(){
                this.innerHTML = "BLAH!";
            }
        }
    };
    
    Behaviour.register(myrules);
    
    // Call Behaviour.apply() to re-apply the rules (if you
    // update the dom, etc).

   License:
   
    This file is entirely BSD licensed.
    
   More information:
    
    http://ripcord.co.nz/behaviour/
   
*/   

var Behaviour = {
    list : [],
    
    register : function(sheet){
        Behaviour.list.push(sheet);
    },
    
    start : function(){
        Behaviour.addLoadEvent(function(){
            Behaviour.apply();
        });
    },
    
    apply : function(){
        for (const sheet of Behaviour.list){
            for (let selector in sheet){
                let list = document.getElementsBySelector(selector);
                
                if (!list){
                    continue;
                }

                for (const element of list){
                    sheet[selector](element);
                }
            }
        }
    },
    
    addLoadEvent : function(func){
        let oldonload = window.onload;
        
        if (typeof window.onload != 'function') {
            window.onload = func;
        } else {
            window.onload = function() {
                oldonload();
                func();
            }
        }
    }
}

Behaviour.start();

/*
   The following code is Copyright (C) Simon Willison 2004.

   document.getElementsBySelector(selector)
   - returns an array of element objects from the current document
     matching the CSS selector. Selectors can contain element names, 
     class names and ids and can be nested. For example:
     
       elements = document.getElementsBySelect('div#main p a.external')
     
     Will return an array of all 'a' elements with 'external' in their 
     class attribute that are contained inside 'p' elements that are 
     contained inside the 'div' element which has id="main"

   New in version 0.4: Support for CSS2 and CSS3 attribute selectors:
   See http://www.w3.org/TR/css3-selectors/#attribute-selectors

   Version 0.4 - Simon Willison, March 25th 2003
   -- Works in Phoenix 0.5, Mozilla 1.3, Opera 7, Internet Explorer 6, Internet Explorer 5 on Windows
   -- Opera 7 fails 
*/

function getAllChildren(e) {
  // Returns all children of element. Workaround required for IE5/Windows. Ugh.
  return e.all ? e.all : e.getElementsByTagName('*');
}

document.getElementsBySelector = function(selector) {
  // Attempt to fail gracefully in lesser browsers
  if (!document.getElementsByTagName) {
    return [];
  }
  // Split selector in to tokens
  const tokens = selector.split(' ');
  let currentContext = new Array(document);
  for (const retrievedToken of tokens) {
    let token = retrievedToken.replace(/^\s+/,'').replace(/\s+$/,'');

    let bits;
    let tagName;
    let found;
    let foundCount;

    if (token.indexOf('#') > -1) {
      // Token is an ID selector
      bits = token.split('#');
      tagName = bits[0];
      const id = bits[1];
      let element = document.getElementById(id);
      if (tagName && element.nodeName.toLowerCase() !== tagName) {
        // tag with that ID not found, return false
        return [];
      }
      // Set currentContext to contain just this element
      currentContext = new Array(element);
      continue; // Skip to next token
    }
    if (token.indexOf('.') > -1) {
      // Token contains a class selector
      bits = token.split('.');
      tagName = bits[0];
      const className = bits[1];
      if (!tagName) {
        tagName = '*';
      }
      // Get elements matching tag, filter them for class selector
      found = [];
      foundCount = 0;
      for (const contextElement of currentContext) {
        let elements;
        if (tagName === '*') {
            elements = getAllChildren(contextElement);
        } else {
            elements = contextElement.getElementsByTagName(tagName);
        }
        for (const element of elements) {
          found[foundCount++] = element;
        }
      }
      currentContext = [];
      let currentContextIndex = 0;
      for (const element of found) {
        if (element.className && element.className.match(new RegExp('\\b'+className+'\\b'))) {
          currentContext[currentContextIndex++] = element;
        }
      }
      continue; // Skip to next token
    }
    // Code to deal with attribute selectors
    if (token.match(/^(\w*)\[(\w+)([=~\|\^\$\*]?)=?"?([^\]"]*)"?\]$/)) {
      tagName = RegExp.$1;
      const attrName = RegExp.$2;
      const attrOperator = RegExp.$3;
      const attrValue = RegExp.$4;
      if (!tagName) {
        tagName = '*';
      }
      // Grab all the tagName elements within current context
      found = [];
      foundCount = 0;
      for (const contextElement of currentContext) {
        let elements;
        if (tagName === '*') {
            elements = getAllChildren(currentContext[h]);
        } else {
            elements = contextElement.getElementsByTagName(tagName);
        }
        for (const element of elements) {
          found[foundCount++] = element;
        }
      }
      let checkFunction; // This function will be used to filter the elements
      switch (attrOperator) {
        case '=': // Equality
          checkFunction = function(e) { return (e.getAttribute(attrName) === attrValue); };
          break;
        case '~': // Match one of space seperated words 
          checkFunction = function(e) { return (e.getAttribute(attrName).match(new RegExp('\\b'+attrValue+'\\b'))); };
          break;
        case '|': // Match start with value followed by optional hyphen
          checkFunction = function(e) { return (e.getAttribute(attrName).match(new RegExp('^'+attrValue+'-?'))); };
          break;
        case '^': // Match starts with value
          checkFunction = function(e) { return (e.getAttribute(attrName).indexOf(attrValue) === 0); };
          break;
        case '$': // Match ends with value - fails with "Warning" in Opera 7
          checkFunction = function(e) { return (e.getAttribute(attrName).lastIndexOf(attrValue) === e.getAttribute(attrName).length - attrValue.length); };
          break;
        case '*': // Match ends with value
          checkFunction = function(e) { return (e.getAttribute(attrName).indexOf(attrValue) > -1); };
          break;
        default :
          // Just test for existence of attribute
          checkFunction = function(e) { return e.getAttribute(attrName); };
      }
      currentContext = [];
      let currentContextIndex = 0;
      for (const item of found) {
        if (checkFunction(item)) {
          currentContext[currentContextIndex++] = item;
        }
      }
      continue; // Skip to next token
    }
    
    if (!currentContext[0]){
        return;
    }
    
    // If we get here, token is JUST an element (not a class or ID selector)
    tagName = token;
    found = [];
    foundCount = 0;
    for (const contextElement of currentContext) {
      let elements = contextElement.getElementsByTagName(tagName);
      for (const element of elements) {
        found[foundCount++] = element;
      }
    }
    currentContext = found;
  }
  return currentContext;
}

/* That revolting regular expression explained 
/^(\w+)\[(\w+)([=~\|\^\$\*]?)=?"?([^\]"]*)"?\]$/
  \---/  \---/\-------------/    \-------/
    |      |         |               |
    |      |         |           The value
    |      |    ~,|,^,$,* or =
    |   Attribute 
   Tag
*/
