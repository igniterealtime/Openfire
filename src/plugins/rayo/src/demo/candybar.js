/* global */
(function (window) {
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
        buf.push('\n<div id="callStatus"><img');
        buf.push(attrs({
            src: locals.picUrl,
            "class": "callerAvatar"
        }, {
            src: true
        }));
        buf.push('/>\n  <h1 class="caller"><span class="callerName">');
        var __val__ = locals.caller;
        buf.push(escape(null == __val__ ? "" : __val__));
        buf.push('</span><span class="callerNumber">');
        var __val__ = locals.caller;
        buf.push(escape(null == __val__ ? "" : __val__));
        buf.push('</span></h1>\n  <h2 class="callTime">0:00:00</h2>\n  <div class="callActions"></div>\n</div>');
    }
    return buf.join("");
}

  var phoney = window.ATT && window.ATT.phoneNumber || window.phoney;

  var CandyBar = function (options) {
    var spec = options || {};
    this.states = {
      incoming: {
        buttons: [
          {
            cls: 'answer',
            label: 'Answer'
          },
          {
            cls: 'ignore',
            label: 'Ignore'
          }
        ]
      },
      calling: {
        buttons: [{
          cls: 'cancel',
          label: 'Cancel'
        }]
      },
      active: {
        buttons: [{
          cls: 'end',
          label: 'End Call'
        },
        {
          cls: 'hold',
          label: 'Hold Call'   
        },  
        {
          cls: 'mute',
          label: 'Mute Call'  
        },  
        {
          cls: 'redirect',
          label: 'Redirect Call'  
        },   
        {
          cls: 'record',
          label: 'Record Voice'  
        }, 
        {
          cls: 'say',
          label: 'Say Message'  
        },         
        {
          cls: 'pause',
          label: 'Pause say'  
        },        
        {
          cls: 'resume',
          label: 'Resume say'        
        }],
        timer: true
      },
      busy: {
        buttons: [{
          cls: 'join',
          label: 'Join Call'
        }],
      },   
      muted: {
        buttons: [{
          cls: 'unmute',
          label: 'Unmute Call'
        }],
      },      
      held: {
        buttons: [{
          cls: 'join',
          label: 'Unhold Call'
        }],
      },       
      conferenced: {
        buttons: [{
          cls: 'leave',
          label: 'Leave Call'
        }],
        timer: true        
      },      
      inactive: {
        buttons: [],
        clearUser: true,
        hidden: true
      },
      ending: {
        buttons: []
      },
      waiting: {
        buttons: []
      }
    };

    this.config = {
      defaultName: '',
      defaultNumber: 'Unknown Number'
    };

    this.registerPhoneHandlers();

  };
  
  CandyBar.prototype.render = function () {
    if (!this.dom) {
      this.dom = this.domify(template(this));
      this.addButtonHandlers();
      document.body.insertBefore(this.dom, document.body.firstChild);
    } else {
      this.dom.innerHTML = this.domify(template(this)).innerHTML;
    }
    this.setState('inactive');
    return this.dom;
  };
  
  CandyBar.prototype.addButtonHandlers = function () {
    var self = this;
    this.dom.addEventListener('click', function (e) {
      var target = e.target;     
      if (target.tagName === 'BUTTON') {
        if (self[target.className]) {       
          self[target.className]();
        }
        return false;
      }
    }, true);
  };
  
  CandyBar.prototype.getStates = function () {
    return Object.keys(this.states);
  };
  
  CandyBar.prototype.getState = function () {
    return this.state;
  };  

  CandyBar.prototype.setState = function (state) {
    this.state = state;
    
    if (!this.dom) return this;    
    var buttons = this.dom.querySelectorAll('button'),
      callActionsEl = this.dom.querySelector('.callActions'),
      self = this,
      stateDef = this.states[state],
      forEach = Array.prototype.forEach;
    if (stateDef) {
      // set proper class on bar itself
      this.getStates().forEach(function (cls) {
        self.dom.classList.remove(cls);
      });
      self.dom.classList.add(state);
      
      // set/remove 'hidden' class on bar itself
      if (stateDef.hidden) {
        self.dom.classList.remove('visible');
        document.body.classList.remove('candybarVisible');
      } else {
        self.dom.classList.add('visible');
        document.body.classList.add('candybarVisible');
      }

      // remove all the buttons
      forEach.call(buttons, function (button) {
        button.parentElement.removeChild(button);
      });
      // add buttons
      stateDef.buttons.forEach(function (button) {     
        callActionsEl.appendChild(self.domify('<button class="' + button.cls + '">' + button.label + '</button>'));         
      });

      // start/stop timer
      if (stateDef.timer) {
        if (this.timerStopped) {
          this.startTimer();
        }
      } else {
        this.resetTimer();
      }

      // reset user if relevant
      if (stateDef.clearUser) {
        this.clearUser();
      }

    } else {
      throw new Error('Invalid value for CandyBar state. Valid values are: ' + this.getStates().join(', '));
    }
    return this;
  };

  CandyBar.prototype.endGently = function (delay) {
    var self = this;
    this.setState('ending');
    setTimeout(function () {
      self.dom.classList.remove('visible');
      setTimeout(function () {
        self.setState('inactive');
        self.clearUser();
      }, 1000);
    }, 1000);
    return this;
  };

  CandyBar.prototype.setImageUrl = function (url) {
    this.attachImageDom(!!url);
    this.imageDom.src = url;
    this.dom.classList[!!url ? 'add' : 'remove']('havatar');
  };

  CandyBar.prototype.attachImageDom = function (bool) {
    if (!this.imageDom) {
      this.imageDom = this.dom.querySelector('.callerAvatar');
    }
    if (bool && !this.imageDom.parentElement) {
      this.dom.insertBefore(this.imageDom, this.dom.firstChild);
    } else if (this.imageDom.parentElement) {
      this.imageDom.parentElement.removeChild(this.imageDom);
    }
    return this.imageDom;
  };

  CandyBar.prototype.registerPhoneHandlers = function () {
    var self = this;
       
    self.end = function () {   
      if (self.call) {         
        self.call.hangup && self.call.hangup();
        self.call && self.call.ended && self.call.ended();
        delete self.call;
      }
    };
    self.answer = function () {
      if (self.call) {
        self.call.answer();
      }
    };
    self.ignore = function () {
      if (self.call) {
        self.call.hangup();
      }
    };    
    self.join = function () {
      if (self.call) {
        self.call.join();
      }
    };  
    self.leave = function () {
      if (self.call) {
        self.call.leave();
      }
    };  
    self.hold = function () {
      if (self.call) {
        self.call.hold();
      }
    }; 
    self.redirect = function () {
      if (self.call) {
        self.call.redirect(prompt("Please enter new destination:","sip:xxxx@domain.com"));
      }
    };   
    self.say = function () {
      if (self.call) {
        self.call.say(prompt("Please enter message:","tts:hello world, i love you"));
      }
    };  
    self.record = function () {
      if (self.call) {
        self.call.record(prompt("Please enter name:","greeting"));
      }
    };     
    self.pause = function () {
      if (self.call.saying) {
        self.call.saying.pause();
      }
    };    
    self.resume = function () {
      if (self.call.saying) {
        self.call.saying.resume();
      }
    };     
    self.mute = function () {
      if (self.call) {
        self.call.mute(true);
      }
    };  
    self.private = function () {
      if (self.call) {
        self.call.private();
      }
    };         
    self.unmute = function () {
      if (self.call) {
        self.call.mute(false);
      }
    };     
    self.cancel = function () {
      if (self.call) {
        self.call.hangup();
      }
    };
  };

  CandyBar.prototype.getUser = function () {
    var user = this.user || {},
      self = this;
    return {
      picUrl: user.picUrl,
      name: (user.name && user.name) || this.config.defaultName,
      number: function () {
        if (user.number && user.number !== self.config.defaultNumber) {
          if (phoney) {
            return phoney.stringify(user.number);
          } else {
            return escape(user.number);
          }
        } else {
          return self.config.defaultNumber;
        }
      }()
    };
  };

  CandyBar.prototype.setUser = function (details) {
    this.user = details;
    if (!this.dom) return;
    var user = this.getUser();
    this.dom.querySelector('.callerNumber').innerHTML = user.number;
    this.dom.querySelector('.callerName').innerHTML = user.name;
    this.setImageUrl(user.picUrl);
    return this;
  };

  CandyBar.prototype.clearUser = function () {
    this.setUser({
      picUrl: '',
      name: '',
      number: ''
    });
    return this;
  };

  CandyBar.prototype.domify = function (str) {
    var div = document.createElement('div');
    div.innerHTML = str;
    return div.firstElementChild;
  };

  CandyBar.prototype.startTimer = function () {
    this.timerStartTime = Date.now();
    this.timerStopped = false;
    this.updateTimer();
    return this;
  };

  CandyBar.prototype.stopTimer = function () {
    this.timerStopped = true;
    return this;
  };

  CandyBar.prototype.resetTimer = function () {
    this.timerStopped = true;
    this.setTimeInDom('0:00:00');
    return this;
  };

  CandyBar.prototype.updateTimer = function () {
    if (this.timerStopped) return;
    
    var diff = Date.now() - this.timerStartTime,
        s = Math.floor(diff / 1000) % 60,
        min = Math.floor((diff / 1000) / 60) % 60,
        hr = Math.floor(((diff / 1000) / 60) / 60) % 60,
        time = [hr, this.zeroPad(min), this.zeroPad(s)].join(':');
    
    if (this.time !== time) {
        this.time = time;
        this.setTimeInDom(time);
    }

    setTimeout(this.updateTimer.bind(this), 100);
  };

  CandyBar.prototype.setTimeInDom = function (timeString) {
    if (!this.dom) return;
    this.dom.querySelector('.callTime').innerHTML = timeString;
  };

  CandyBar.prototype.zeroPad = function (num) {
    return ((num + '').length === 1) ? '0' + num : num;
  };

  window.CandyBar = CandyBar;
})(window);
