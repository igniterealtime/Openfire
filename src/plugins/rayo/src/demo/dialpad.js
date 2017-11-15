/* global */
(function () {
    /*
  WildEmitter.js is a slim little event emitter largely based on @visionmedia's Emitter from UI Kit.
  
  I wanted it standalone.
  
  I also wanted support for wildcard emitters. Like:
  
  emitter.on('*', function (eventName, other, event, payloads) {
      
  });
  
  emitter.on('somenamespace*', function (eventName, payloads) {
      
  });
  
  Functions triggered by wildcard registered events also get the event name as the first argument.
  
  */
  function WildEmitter() {
      this.callbacks = {};
  }
  
  // Listen on the given `event` with `fn`. Store a group name if present.
  WildEmitter.prototype.on = function (event, groupName, fn) {
      var hasGroup = (arguments.length === 3),
          group = hasGroup ? arguments[1] : undefined, 
          func = hasGroup ? arguments[2] : arguments[1];
      func._groupName = group;
      (this.callbacks[event] = this.callbacks[event] || []).push(func);
      return this;
  };
  
  // Adds an `event` listener that will be invoked a single
  // time then automatically removed.
  WildEmitter.prototype.once = function (event, fn) {
      var self = this;
      function on() {
          self.off(event, on);
          fn.apply(this, arguments);
      }
      this.on(event, on);
      return this;
  };
  
  // Unbinds an entire group
  WildEmitter.prototype.releaseGroup = function (groupName) {
      var item, i, len, handlers;
      for (item in this.callbacks) {
          handlers = this.callbacks[item];
          for (i = 0, len = handlers.length; i < len; i++) {
              if (handlers[i]._groupName === groupName) {
                  //console.log('removing');
                  // remove it and shorten the array we're looping through
                  handlers.splice(i, 1);
                  i--;
                  len--;
              }
          }
      }
      return this;
  };
  
  // Remove the given callback for `event` or all
  // registered callbacks.
  WildEmitter.prototype.off = function (event, fn) {
      var callbacks = this.callbacks[event],
          i;
      
      if (!callbacks) return this;
  
      // remove all handlers
      if (arguments.length === 1) {
          delete this.callbacks[event];
          return this;
      }
  
      // remove specific handler
      i = callbacks.indexOf(fn);
      callbacks.splice(i, 1);
      return this;
  };
  
  // Emit `event` with the given args.
  // also calls any `*` handlers
  WildEmitter.prototype.emit = function (event) {
      var args = [].slice.call(arguments, 1),
          callbacks = this.callbacks[event],
          specialCallbacks = this.getWildcardCallbacks(event),
          i,
          len,
          item;
  
      if (callbacks) {
          for (i = 0, len = callbacks.length; i < len; ++i) {
              callbacks[i].apply(this, args);
          }
      }
  
      if (specialCallbacks) {
          for (i = 0, len = specialCallbacks.length; i < len; ++i) {
              specialCallbacks[i].apply(this, [event].concat(args));
          }
      }
  
      return this;
  };
  
  // Helper for for finding special wildcard event handlers that match the event
  WildEmitter.prototype.getWildcardCallbacks = function (eventName) {
      var item,
          split,
          result = [];
  
      for (item in this.callbacks) {
          split = item.split('*');
          if (item === '*' || (split.length === 2 && eventName.slice(0, split[1].length) === split[1])) {
              result = result.concat(this.callbacks[item]);
          }
      }
      return result;
  };
  

  var jade=function(exports){Array.isArray||(Array.isArray=function(arr){return"[object Array]"==Object.prototype.toString.call(arr)}),Object.keys||(Object.keys=function(obj){var arr=[];for(var key in obj)obj.hasOwnProperty(key)&&arr.push(key);return arr}),exports.merge=function merge(a,b){var ac=a["class"],bc=b["class"];if(ac||bc)ac=ac||[],bc=bc||[],Array.isArray(ac)||(ac=[ac]),Array.isArray(bc)||(bc=[bc]),ac=ac.filter(nulls),bc=bc.filter(nulls),a["class"]=ac.concat(bc).join(" ");for(var key in b)key!="class"&&(a[key]=b[key]);return a};function nulls(val){return val!=null}return exports.attrs=function attrs(obj,escaped){var buf=[],terse=obj.terse;delete obj.terse;var keys=Object.keys(obj),len=keys.length;if(len){buf.push("");for(var i=0;i<len;++i){var key=keys[i],val=obj[key];"boolean"==typeof val||null==val?val&&(terse?buf.push(key):buf.push(key+'="'+key+'"')):0==key.indexOf("data")&&"string"!=typeof val?buf.push(key+"='"+JSON.stringify(val)+"'"):"class"==key&&Array.isArray(val)?buf.push(key+'="'+exports.escape(val.join(" "))+'"'):escaped&&escaped[key]?buf.push(key+'="'+exports.escape(val)+'"'):buf.push(key+'="'+val+'"')}}return buf.join(" ")},exports.escape=function escape(html){return String(html).replace(/&(?!(\w+|\#\d+);)/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;")},exports.rethrow=function rethrow(err,filename,lineno){if(!filename)throw err;var context=3,str=require("fs").readFileSync(filename,"utf8"),lines=str.split("\n"),start=Math.max(lineno-context,0),end=Math.min(lines.length,lineno+context),context=lines.slice(start,end).map(function(line,i){var curr=i+start+1;return(curr==lineno?"  > ":"    ")+curr+"| "+line}).join("\n");throw err.path=filename,err.message=(filename||"Jade")+":"+lineno+"\n"+context+"\n\n"+err.message,err},exports}({});

  var template = function anonymous(locals, attrs, escape, rethrow, merge) {
    attrs = attrs || jade.attrs;
    escape = escape || jade.escape;
    rethrow = rethrow || jade.rethrow;
    merge = merge || jade.merge;
    var buf = [];
    with (locals || {}) {
        var interp;
        var __indent = [];
        buf.push('\n<div id="screen">\n  <div class="dialerwrapper">\n    <div class="numberEntry"></div>\n    <ul id="dialpad">\n      <li>\n        <button data-value="1">\n          <p>1</p>\n          <div>&nbsp;</div>\n        </button>\n        <button data-value="2">\n          <p>2</p>\n          <div>abc</div>\n        </button>\n        <button data-value="3">\n          <p>3</p>\n          <div>def</div>\n        </button>\n      </li>\n      <li>\n        <button data-value="4">\n          <p>4</p>\n          <div>ghi</div>\n        </button>\n        <button data-value="5">\n          <p>5</p>\n          <div>jki</div>\n        </button>\n        <button data-value="6">\n          <p>6</p>\n          <div>mno</div>\n        </button>\n      </li>\n      <li>\n        <button data-value="7">\n          <p>7</p>\n          <div>pqrs</div>\n        </button>\n        <button data-value="8">\n          <p>8</p>\n          <div>tuv</div>\n        </button>\n        <button data-value="9">\n          <p>9</p>\n          <div>wxyz</div>\n        </button>\n      </li>\n      <li>\n        <button data-value="#">\n          <p>#</p>\n          <div>&nbsp;</div>\n        </button>\n        <button data-value="0">\n          <p>0</p>\n          <div>abc</div>\n        </button>\n        <button data-value="*">\n          <p>*</p>\n          <div></div>\n        </button>\n      </li>\n    </ul>\n  </div>');
        if (locals.footer) {
            buf.push('\n  <footer>\n    <nav id="actions"><a id="call_button" class="call">Call</a></nav>\n  </footer>');
        }
        buf.push("\n</div>");
    }
    return buf.join("");
}

  var phoney = window.ATT && window.ATT.phoneNumber || window.phoney;

  var Dialpad = function (spec) {
    var config = spec || {},
      availableCallbacks = {
        'onHide': 'hide',
        'onPress': 'press',
        'onCallableNumber': 'callableNumber',
        'onCall': 'call'
      };

    // inherit wildemitter properties
    WildEmitter.call(this);
    this.number = '';
    this.footer = true;

    // register handlers passed in on init
    for (var item in availableCallbacks) {
      if (config[item]) this.on(availableCallbacks[item], config[item]);
    }
  };

  Dialpad.prototype = new WildEmitter();
  
  Dialpad.prototype.render = function (container) {
    this.dom = this.domify(template(this));
    this.addClickHandlers();
    this.numberField = this.dom.querySelector('.numberEntry');
    this.clear();
    this.addDocListener();
    return this.dom;
  };

  Dialpad.prototype.hide = function () {
    this.removeDocListener();
    this.dom.parentElement.removeChild(this.dom);
    this.emit('hide');
  };
  
  Dialpad.prototype.addDocListener = function () {
    var self = this;
    this.boundKeyHandler = function () {
      self.handleKeyDown.apply(self, arguments);
    };
    document.addEventListener('keydown', this.boundKeyHandler, true);
  };

  Dialpad.prototype.removeDocListener = function () {
    document.removeEventListener('keydown', this.boundKeyHandler, true);
  };

  Dialpad.prototype.addClickHandlers = function () {
    var self = this,
      buttons = this.dom.querySelectorAll('button'),
      callButton = this.dom.querySelector('.call');

    // for button handlers
    Array.prototype.forEach.call(buttons, function (button) {
      button.addEventListener('click', function (e) {
        var data = this.attributes['data-value'],
          value = data && data.nodeValue;
        if (value == 'del') {
          self.removeLastNumber();
        } else {
          self.addNumber(value);
        }
        return false;
      }, true);
    });

    if (callButton) {
      callButton.addEventListener('click', function () {
        self.handleCallClick.apply(self, arguments);
      }, false);
    }
  };
  
  Dialpad.prototype.handleKeyDown = function (e) {
    var number,
      keyCode = e.which;
    // only handle if Dialpad is showing
    if (keyCode >= 48 && keyCode <= 57) {
      number = keyCode - 48;
      this.addNumber(number + '');
    }

    if (keyCode === 8) {
      this.removeLastNumber();
      e.preventDefault();
    }

    if (keyCode === 13) {
      this.handleCallClick(e);
    }
  };

  Dialpad.prototype.getNumber = function () {  
    return this.number;
  };

  Dialpad.prototype.setCallLabel = function (label) {
    var callButton = this.dom.querySelector('.call');
    if (callButton) {
      callButton.innerHTML = label;
    }
  };

  Dialpad.prototype.getCallLabel = function () {
    var callButton = this.dom.querySelector('.call');
    return callButton.innerHTML;
  };

  Dialpad.prototype.setNumber = function (number) {
    var newNumber = phoney.parse(number),
      oldNumber = this.number,
      callable = phoney.getCallable(newNumber);
    this.number = newNumber;
    this.numberField.innerHTML = phoney.stringify(this.number);
    if (callable) {
      this.emit('callableNumber', callable);
    }
  };

  Dialpad.prototype.addNumber = function (number) {
    var newNumber = (this.getNumber() + '') + number;
    this.emit('press', number);
    this.setNumber(newNumber);
  };

  Dialpad.prototype.removeLastNumber = function () {
    this.setNumber(this.getNumber().slice(0, -1));
    this.emit('press', 'del');
  };

  Dialpad.prototype.clear = function () {
    this.setNumber('');
  };

  Dialpad.prototype.domify = function (str) {
    var div = document.createElement('div');
    div.innerHTML = str;
    return div.firstElementChild;
  };

  Dialpad.prototype.handleCallClick = function (e) {
    e.preventDefault();
    this.emit('call', this.number, !!phoney.getCallable(this.number));
    return false;
  };

  // attach to window or export with commonJS
  if (typeof module !== "undefined") {
      module.exports = Dialpad;
  } else if (typeof define === "function" && define.amd) {
      define(Dialpad);
  } else {
      window.Dialpad = Dialpad;
  }
})(window);
