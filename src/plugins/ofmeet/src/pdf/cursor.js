
  var FOREGROUND_COLORS = ["#111", "#eee"];
  var CURSOR_HEIGHT = 50;
  var CURSOR_ANGLE = (35 / 180) * Math.PI;
  var CURSOR_WIDTH = Math.ceil(Math.sin(CURSOR_ANGLE) * CURSOR_HEIGHT);
  var CLICK_TRANSITION_TIME = 3000;

  var peers = {};
  
  var session = {
  
  	send: function(msg)
  	{
  		//console.log('session.send', msg);
  		window.parent.connection.ofmuc.pfdMessage(msg);
  	}
  };

  var handlePdfShare = function(url, from)
  {
  	try {
  		var obj = JSON.parse(url);
  		//window.parent.console.log("remote handlePdfShare", obj, url, from);
  		
		var p = peers[from];
		
		if (! p) {        
    			p = Cursor.getClient(from);
    			p.updatePeer({id: from, name: from, status: "live"}); 
    			peers[from] = p;
		} 
    
    		if (obj.type == "cursor-update") p.updatePosition(obj);	
  	
  	} catch (e) { window.parent.console.error(e)}
  };
  
  var util = {};
  
  util.Class = function (superClass, prototype) {
    var a;
    if (prototype === undefined) {
      prototype = superClass;
    } else {
      if (superClass.prototype) {
        superClass = superClass.prototype;
      }
      var newPrototype = Object.create(superClass);
      for (a in prototype) {
        if (prototype.hasOwnProperty(a)) {
          newPrototype[a] = prototype[a];
        }
      }
      prototype = newPrototype;
    }
    var ClassObject = function () {
      var obj = Object.create(prototype);
      obj.constructor.apply(obj, arguments);
      obj.constructor = ClassObject;
      return obj;
    };
    ClassObject.prototype = prototype;
    if (prototype.constructor.name) {
      ClassObject.className = prototype.constructor.name;
      ClassObject.toString = function () {
        return '[Class ' + this.className + ']';
      };
    }
    if (prototype.classMethods) {
      for (a in prototype.classMethods) {
        if (prototype.classMethods.hasOwnProperty(a)) {
          ClassObject[a] = prototype.classMethods[a];
        }
      }
    }
    return ClassObject;
  };

  var elementFinder = {};
    
  elementFinder.ignoreElement = function ignoreElement(el) 
  {
    if (el instanceof $) {
      el = el[0];
    }
    while (el) {
      if ($(el).hasClass("togetherjs")) {
        return true;
      }
      el = el.parentNode;
    }
    return false;
  };

  elementFinder.elementLocation = function elementLocation(el) {
    if (el instanceof $) {
      // a jQuery element
      el = el[0];
    }
    if (el[0] && el.attr && el[0].nodeType == 1) {
      // Or a jQuery element not made by us
      el = el[0];
    }
    if (el.id) {
      return "#" + el.id;
    }
    if (el.tagName == "BODY") {
      return "body";
    }
    if (el.tagName == "HEAD") {
      return "head";
    }
    if (el === document) {
      return "document";
    }
    var parent = el.parentNode;
    if ((! parent) || parent == el) {
      console.warn("elementLocation(", el, ") has null parent");
      throw new Error("No locatable parent found");
    }
    var parentLocation = elementLocation(parent);
    var children = parent.childNodes;
    var _len = children.length;
    var index = 0;
    for (var i=0; i<_len; i++) {
      if (children[i] == el) {
        break;
      }
      if (children[i].nodeType == document.ELEMENT_NODE) {
        if (children[i].className.indexOf("togetherjs") != -1) {
          // Don't count our UI
          continue;
        }
        // Don't count text or comments
        index++;
      }
    }
    return parentLocation + ":nth-child(" + (index+1) + ")";
  };

  elementFinder.CannotFind = {
    constructor: function CannotFind(location, reason, context) {
      this.prefix = "";
      this.location = location;
      this.reason = reason;
      this.context = context;
    },
    toString: function () {
      var loc;
      try {
        loc = elementFinder.elementLocation(this.context);
      } catch (e) {
        loc = this.context;
      }
      return (
        "[CannotFind " + this.prefix +
          "(" + this.location + "): " +
          this.reason + " in " +
          loc + "]");
    }
  };

  elementFinder.findElement = function findElement(loc, container) {
    // FIXME: should this all just be done with document.querySelector()?
    // But no!  We can't ignore togetherjs elements with querySelector.
    // But maybe!  We *could* make togetherjs elements less obtrusive?
    container = container || document;
    var el, rest;
    if (loc === "body") {
      return document.body;
    } else if (loc === "head") {
      return document.head;
    } else if (loc === "document") {
      return document;
    } else if (loc.indexOf("body") === 0) {
      el = document.body;
      try {
        return findElement(loc.substr(("body").length), el);
      } catch (e) {
        if (e instanceof elementFinder.CannotFind) {
          e.prefix = "body" + e.prefix;
        }
        throw e;
      }
    } else if (loc.indexOf("head") === 0) {
      el = document.head;
      try {
        return findElement(loc.substr(("head").length), el);
      } catch (e) {
        if (e instanceof elementFinder.CannotFind) {
          e.prefix = "head" + e.prefix;
        }
        throw e;
      }
    } else if (loc.indexOf("#") === 0) {
      var id;
      loc = loc.substr(1);
      if (loc.indexOf(":") === -1) {
        id = loc;
        rest = "";
      } else {
        id = loc.substr(0, loc.indexOf(":"));
        rest = loc.substr(loc.indexOf(":"));
      }
      el = document.getElementById(id);
      if (! el) {
        throw elementFinder.CannotFind("#" + id, "No element by that id", container);
      }
      if (rest) {
        try {
          return findElement(rest, el);
        } catch (e) {
          if (e instanceof elementFinder.CannotFind) {
            e.prefix = "#" + id + e.prefix;
          }
          throw e;
        }
      } else {
        return el;
      }
    } else if (loc.indexOf(":nth-child(") === 0) {
      loc = loc.substr((":nth-child(").length);
      if (loc.indexOf(")") == -1) {
        throw "Invalid location, missing ): " + loc;
      }
      var num = loc.substr(0, loc.indexOf(")"));
      num = parseInt(num, 10);
      var count = num;
      loc = loc.substr(loc.indexOf(")") + 1);
      var children = container.childNodes;
      el = null;
      for (var i=0; i<children.length; i++) {
        var child = children[i];
        if (child.nodeType == document.ELEMENT_NODE) {
          if (child.className.indexOf("togetherjs") != -1) {
            continue;
          }
          count--;
          if (count === 0) {
            // this is the element
            el = child;
            break;
          }
        }
      }
      if (! el) {
        throw elementFinder.CannotFind(":nth-child(" + num + ")", "container only has " + (num - count) + " elements", container);
      }
      if (loc) {
        try {
          return elementFinder.findElement(loc, el);
        } catch (e) {
          if (e instanceof elementFinder.CannotFind) {
            e.prefix = ":nth-child(" + num + ")" + e.prefix;
          }
          throw e;
        }
      } else {
        return el;
      }
    } else {
      throw elementFinder.CannotFind(loc, "Malformed location", container);
    }
  };

  elementFinder.elementByPixel = function (height) {
    /* Returns {location: "...", offset: pixels}

       To get the pixel position back, you'd do:
         $(location).offset().top + offset
     */
    function search(start, height) {
      var last = null;
      var children = start.children();
      children.each(function () {
        var el = $(this);
        if (el.hasClass("togetherjs") || el.css("position") == "fixed" || ! el.is(":visible")) {
          return;
        }
        if (el.offset().top > height) {
          return false;
        }
        last = el;
      });
      if ((! children.length) || (! last)) {
        // There are no children, or only inapplicable children
        return {
          location: elementFinder.elementLocation(start[0]),
          offset: height - start.offset().top,
          absoluteTop: height,
          documentHeight: $(document).height()
        };
      }
      return search(last, height);
    }
    return search($(document.body), height);
  };

  elementFinder.pixelForPosition = function (position) {
    /* Inverse of elementFinder.elementByPixel */
    if (position.location == "body") {
      return position.offset;
    }
    var el;
    try {
      el = elementFinder.findElement(position.location);
    } catch (e) {
      if (e instanceof elementFinder.CannotFind && position.absoluteTop) {
        // We don't trust absoluteTop to be quite right locally, so we adjust
        // for the total document height differences:
        var percent = position.absoluteTop / position.documentHeight;
        return $(document).height() * percent;
      }
      throw e;
    }
    var top = $(el).offset().top;
    // FIXME: maybe here we should test for sanity, like if an element is
    // hidden.  We can use position.absoluteTop to get a sense of where the
    // element roughly should be.  If the sanity check failed we'd use
    // absoluteTop
    return top + position.offset;
  };

  // Number of milliseconds after page load in which a scroll-update
  // related hello-back message will be processed:
  
  var SCROLL_UPDATE_CUTOFF = 2000;

  // FIXME: should check for a peer leaving and remove the cursor object
  var Cursor = util.Class({
    constructor: function (clientId) {
      this.clientId = clientId;
      this.element = $('<div id="togetherjs-template-cursor" class="togetherjs-cursor togetherjs"> <!-- Note: images/cursor.svg is a copy of this (for editing): --> <!-- crossbrowser svg dropshadow http://demosthenes.info/blog/600/Creating-a-True-CrossBrowser-Drop-Shadow- --> <svg version="1.1" id="Layer_1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" width="15px" height="22.838px" viewBox="96.344 146.692 15 22.838" enable-background="new 96.344 146.692 15 22.838" xml:space="preserve"> <path fill="#231F20" d="M98.984,146.692c2.167,1.322,1.624,6.067,3.773,7.298c-0.072-0.488,2.512-0.931,3.097,0 c0.503,0.337,1.104-0.846,2.653,0.443c0.555,0.593,3.258,2.179,1.001,8.851c-0.446,1.316,2.854,0.135,1.169,2.619 c-3.748,5.521-9.455,2.787-9.062,1.746c1.06-2.809-6.889-4.885-4.97-9.896c0.834-2.559,2.898,0.653,2.923,0.29 c-0.434-1.07-2.608-5.541-2.923-6.985C96.587,150.793,95.342,147.033,98.984,146.692z"/> </svg> <!-- <img class="togetherjs-cursor-img" src="http://localhost:8080/togetherjs/images/cursor.svg"> --> <span class="togetherjs-cursor-container"> <span class="togetherjs-cursor-name">Dummy</span> <span style="display:none" class="togetherjs-cursor-typing" id="togetherjs-cursor-typebox"> <span class="togetherjs-typing-ellipse-one">&#9679;</span><span class="togetherjs-typing-ellipse-two">&#9679;</span><span class="togetherjs-typing-ellipse-three">&#9679;</span> </span> <!-- Displayed when the cursor is below the screen: --> <span class="togetherjs-cursor-down"> </span> <!-- Displayed when the cursor is above the screen: --> <span class="togetherjs-cursor-up"> </span> </span> </div>');
      this.elementClass = "togetherjs-scrolled-normal";
      this.element.addClass(this.elementClass);
      //this.updatePeer(peers.getPeer(clientId));
      this.lastTop = this.lastLeft = null;
      $(document.body).append(this.element);
      //this.element.animateCursorEntry();
      this.keydownTimeout = null;
      this.clearKeydown = this.clearKeydown.bind(this);
      this.atOtherUrl = false;   
      this.color = Math.floor(Math.random() * 0xffffff).toString(16);
      while (this.color.length < 6) {
	this.color = "0" + this.color;
      }
      this.color = "#" + this.color;       
    },

    // How long after receiving a setKeydown call that we should show the
    // user typing.  This should be more than MIN_KEYDOWN_TIME:
    KEYDOWN_WAIT_TIME: 2000,

    updatePeer: function (peer) {
      // FIXME: can I use peer.setElement()?
      this.element.css({color: this.color});
      var img = this.element.find("img.togetherjs-cursor-img");
      img.attr("src", makeCursor(this.color));
      var name = this.element.find(".togetherjs-cursor-name");
      var nameContainer = this.element.find(".togetherjs-cursor-container");
      name.text(peer.name);
      nameContainer.css({
        backgroundColor: this.color,
        color: tinycolor.mostReadable(this.color, FOREGROUND_COLORS)
      });
      
      var path = this.element.find("svg path");
      
      path.attr("fill", this.color);
      
      // FIXME: should I just remove the element?
      if (peer.status != "live") {
        this.element.hide();
        this.element.find("svg").animate({
          opacity: 0
        }, 350);
        this.element.find(".togetherjs-cursor-container").animate({
                width: 34,
                height: 20,
                padding: 12,
                margin: 0
            }, 200).animate({
                width: 0,
                height: 0,
                padding: 0,
                opacity: 0
                }, 200);
      } else {
        this.element.show();
        this.element.animate({
          opacity:0.3
        }).animate({
          opacity:1
        });
      }
    },

    setClass: function (name) {
      if (name != this.elementClass) {
        this.element.removeClass(this.elementClass).addClass(name);
        this.elementClass = name;
      }
    },

    updatePosition: function (pos) {
      var top, left;
      if (this.atOtherUrl) {
        this.element.show();
        this.atOtherUrl = false;
      }
      if (pos.element) {
        var target = $(elementFinder.findElement(pos.element));
        var offset = target.offset();
        top = offset.top + pos.offsetY;
        left = offset.left + pos.offsetX;
      } else {
        // No anchor, just an absolute position
        top = pos.top;
        left = pos.left;
      }
      // These are saved for use by .refresh():
      this.lastTop = top;
      this.lastLeft = left;
      this.setPosition(top, left);
    },

    hideOtherUrl: function () {
      if (this.atOtherUrl) {
        return;
      }
      this.atOtherUrl = true;
      // FIXME: should show away status better:
      this.element.hide();
    },

    // place Cursor rotate function down here FIXME: this doesnt do anything anymore.  This is in the CSS as an animation
    rotateCursorDown: function(){
      var e = $(this.element).find('svg');
        e.animate({borderSpacing: -150, opacity: 1}, {
        step: function(now, fx) {
          if (fx.prop == "borderSpacing") {
            e.css('-webkit-transform', 'rotate('+now+'deg)')
              .css('-moz-transform', 'rotate('+now+'deg)')
              .css('-ms-transform', 'rotate('+now+'deg)')
              .css('-o-transform', 'rotate('+now+'deg)')
              .css('transform', 'rotate('+now+'deg)');
          } else {
            e.css(fx.prop, now);
          }
        },
        duration: 500
      }, 'linear').promise().then(function () {
        e.css('-webkit-transform', '')
          .css('-moz-transform', '')
          .css('-ms-transform', '')
          .css('-o-transform', '')
          .css('transform', '')
          .css("opacity", "");
      });
    },

    setPosition: function (top, left) {
      var wTop = $(window).scrollTop();
      var height = $(window).height();

      if (top < wTop) {
        // FIXME: this is a totally arbitrary number, but is meant to be big enough
        // to keep the cursor name from being off the top of the screen.
        top = 25;
        this.setClass("togetherjs-scrolled-above");
      } else if (top > wTop + height - CURSOR_HEIGHT) {
        top = height - CURSOR_HEIGHT - 5;
        this.setClass("togetherjs-scrolled-below");
      } else {
        this.setClass("togetherjs-scrolled-normal");
      }
      this.element.css({
        top: top,
        left: left
      });
    },

    refresh: function () {
      if (this.lastTop !== null) {
        this.setPosition(this.lastTop, this.lastLeft);
      }
    },

    setKeydown: function () {
      if (this.keydownTimeout) {
        clearTimeout(this.keydownTimeout);
      } else {
        this.element.find(".togetherjs-cursor-typing").show().animateKeyboard();
      }
      this.keydownTimeout = setTimeout(this.clearKeydown, this.KEYDOWN_WAIT_TIME);
    },

    clearKeydown: function () {
      this.keydownTimeout = null;
      this.element.find(".togetherjs-cursor-typing").hide().stopKeyboardAnimation();
    },

    _destroy: function () {
      this.element.remove();
      this.element = null;
    }
  });

  Cursor._cursors = {};

  Cursor.getClient = function (clientId) {
    var c = Cursor._cursors[clientId];
    if (! c) {
      c = Cursor._cursors[clientId] = Cursor(clientId);
    }
    return c;
  };

  Cursor.forEach = function (callback, context) {
    context = context || null;
    for (var a in Cursor._cursors) {
      if (Cursor._cursors.hasOwnProperty(a)) {
        callback.call(context, Cursor._cursors[a], a);
      }
    }
  };

  Cursor.destroy = function (clientId) {
    Cursor._cursors[clientId]._destroy();
    delete Cursor._cursors[clientId];
  };

/*
  peers.on("new-peer identity-updated status-updated", function (peer) {
    var c = Cursor.getClient(peer.id);
    c.updatePeer(peer);
  });
*/

  var lastTime = 0;
  var MIN_TIME = 100;
  var lastPosX = -1;
  var lastPosY = -1;
  var lastMessage = null;
  
  function mousemove(event) 
  {
    var now = Date.now();
    if (now - lastTime < MIN_TIME) {
      return;
    }
    lastTime = now;
    var pageX = event.pageX;
    var pageY = event.pageY;
    if (Math.abs(lastPosX - pageX) < 3 && Math.abs(lastPosY - pageY) < 3) {
      // Not a substantial enough change
      return;
    }
    lastPosX = pageX;
    lastPosY = pageY;
    var target = event.target;

    if (elementFinder.ignoreElement(target)) {
      target = null;
    }
    if ((! target) || target == document.documentElement || target == document.body) {
      lastMessage = {
        type: "cursor-update",
        top: pageY,
        left: pageX
      };
      session.send(lastMessage);
      return;
    }
    target = $(target);
    var offset = target.offset();
    if (! offset) {
      // FIXME: this really is walkabout.js's problem to fire events on the
      // document instead of a specific element
      console.warn("Could not get offset of element:", target[0]);
      return;
    }
    var offsetX = pageX - offset.left;
    var offsetY = pageY - offset.top;
    lastMessage = {
      type: "cursor-update",
      element: elementFinder.elementLocation(target),
      offsetX: Math.floor(offsetX),
      offsetY: Math.floor(offsetY)
    };
    session.send(lastMessage);
  }



  function makeCursor(color) 
  {
    var canvas = $("<canvas></canvas>");
    canvas.attr("height", CURSOR_HEIGHT);
    canvas.attr("width", CURSOR_WIDTH);
    var context = canvas[0].getContext('2d');
    context.fillStyle = color;
    context.moveTo(0, 0);
    context.beginPath();
    context.lineTo(0, CURSOR_HEIGHT/1.2);
    context.lineTo(Math.sin(CURSOR_ANGLE/2) * CURSOR_HEIGHT / 1.5,
                   Math.cos(CURSOR_ANGLE/2) * CURSOR_HEIGHT / 1.5);
    context.lineTo(Math.sin(CURSOR_ANGLE) * CURSOR_HEIGHT / 1.2,
                   Math.cos(CURSOR_ANGLE) * CURSOR_HEIGHT / 1.2);
    context.lineTo(0, 0);
    context.shadowColor = 'rgba(0,0,0,0.3)';
    context.shadowBlur = 2;
    context.shadowOffsetX = 1;
    context.shadowOffsetY = 2;
	context.strokeStyle = "#ffffff";
	context.stroke();
    context.fill();
    return canvas[0].toDataURL("image/png");
  }

  var scrollTimeout = null;
  var scrollTimeoutSet = 0;
  var SCROLL_DELAY_TIMEOUT = 75;
  var SCROLL_DELAY_LIMIT = 300;

  function scroll() {
    var now = Date.now();
    if (scrollTimeout) {
      if (now - scrollTimeoutSet < SCROLL_DELAY_LIMIT) {
        clearTimeout(scrollTimeout);
      } else {
        // Just let it progress anyway
        return;
      }
    }
    scrollTimeout = setTimeout(_scrollRefresh, SCROLL_DELAY_TIMEOUT);
    if (! scrollTimeoutSet) {
      scrollTimeoutSet = now;
    }
  }

  var lastScrollMessage = null;
  
  function _scrollRefresh() {
    scrollTimeout = null;
    scrollTimeoutSet = 0;
    Cursor.forEach(function (c) {
      c.refresh();
    });
    lastScrollMessage = {
      type: "scroll-update",
      position: elementFinder.elementByPixel($(window).scrollTop())
    };
    session.send(lastScrollMessage);
  }


  function documentClick(event) {
    if (event.togetherjsInternal) {
      // This is an artificial internal event
      return;
    }
    // FIXME: this might just be my imagination, but somehow I just
    // really don't want to do anything at this stage of the event
    // handling (since I'm catching every click), and I'll just do
    // something real soon:
    setTimeout(function () 
    {
      var element = event.target;
      if (element == document.documentElement) {
        // For some reason clicking on <body> gives the <html> element here
        element = document.body;
      }
      if (elementFinder.ignoreElement(element)) {
        return;
      }
      //Prevent click events on video objects to avoid conflicts with
      //togetherjs's own video events
      if (element.nodeName.toLowerCase() === 'video'){
        return;
      }

      var location = elementFinder.elementLocation(element);
      var offset = $(element).offset();
      var offsetX = event.pageX - offset.left;
      var offsetY = event.pageY - offset.top;
      
      session.send({
        type: "cursor-click",
        element: location,
        offsetX: offsetX,
        offsetY: offsetY
      });
      
      displayClick({top: event.pageY, left: event.pageX}, 'red');
    });
  }


  function displayClick(pos, color) {
  console.log("displayClick", pos, color);
    // FIXME: should we hide the local click if no one else is going to see it?
    // That means tracking who might be able to see our screen.
    var element = $('<div class="togetherjs-click togetherjs"></div>');
    $(document.body).append(element);
    
    element.css({
      top: pos.top,
      left: pos.left,
      borderColor: color
    });
    setTimeout(function () {
      element.addClass("togetherjs-clicking");
    }, 100);
    setTimeout(function () {
      element.remove();
    }, CLICK_TRANSITION_TIME);
  }

  var lastKeydown = 0;
  var MIN_KEYDOWN_TIME = 500;

  function documentKeydown(event) {
    setTimeout(function () {
      var now = Date.now();
      if (now - lastKeydown < MIN_KEYDOWN_TIME) {
        return;
      }
      lastKeydown = now;
      // FIXME: is event.target interesting here?  That is, *what* the
      // user is typing into, not just that the user is typing?  Also
      // I'm assuming we don't care if the user it typing into a
      // togetherjs-related field, since chat activity is as interesting
      // as any other activity.
      session.send({type: "keydown"});
    });
  }
  
window.addEventListener("unload", function () 
{
    Cursor.forEach(function (c, clientId) {
      Cursor.destroy(clientId);
    });
    
    $(document).unbind("mousemove", mousemove);
    document.removeEventListener("click", documentClick, true);
    document.removeEventListener("keydown", documentKeydown, true);
    $(window).unbind("scroll", scroll);	   
});
	
window.addEventListener("load", function()
{
    $(document).mousemove(mousemove);
    document.addEventListener("click", documentClick, true);
    document.addEventListener("keydown", documentKeydown, true);
    $(window).scroll(scroll);
    scroll();    
});
