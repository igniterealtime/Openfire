 var Openfire = {};


/** Class: Openfire.Connection
 *  WebSockets Connection Manager for Openfire
 *
 *  Thie class manages an WebSockets connection
 *  to an Openfire XMPP server through the WebSockets plugin and dispatches events to the user callbacks as
 *  data arrives.  It uses the server side Openfire authentication
 *
 *  After creating a Openfire object, the user will typically
 *  call connect() with a user supplied callback to handle connection level
 *  events like authentication failure, disconnection, or connection
 *  complete.
 *
 *  To send data to the connection, use send(doc) or sendRaw(text)
 *
 *  Use xmlInput(doc) and RawInput(text) overrideable function to receive XML data coming into the
 *  connection.
 *
 *  The user will also have several event handlers defined by using
 *  addHandler() and addTimedHandler().  These will allow the user code to
 *  respond to interesting stanzas or do something periodically with the
 *  connection.  These handlers will be active once authentication is
 *  finished.
 *
 *  Create and initialize a Openfire object.
 *
 *
 *  Returns:
 *    A new Openfire object.
 */
 
Openfire.Connection = function(url) 
{
    if (!window.WebSocket) 
    {
    window.WebSocket=window.MozWebSocket;

    if (!window.WebSocket)
    {
        var msg = "WebSocket not supported by this browser";			
        alert(msg);
        throw Error(msg);
    }
    }
    
    this.host = url.indexOf("/") < 0 ? url : url.split("/")[2];   
    this.protocol = url.indexOf("/") < 0 ? "wss:" : (url.split("/")[0] == "http:") ? "ws:" : "wss:";   
    this.jid = "";
    this.resource = "ofchat";
    this.streamId = null;

    // handler lists
    
    this.timedHandlers = [];
    this.handlers = [];
    this.removeTimeds = [];
    this.removeHandlers = [];
    this.addTimeds = [];
    this.addHandlers = [];
    this._idleTimeout = null;
    
    this.authenticated = false;
    this.disconnecting = false;
    this.connected = false;

    this.errors = 0;

    this._uniqueId = Math.round(Math.random() * 10000);

    // setup onIdle callback every 1/10th of a second
    this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
    
    // initialize plugins
    
    for (var k in Strophe._connectionPlugins) 
    {   
        if (Strophe._connectionPlugins.hasOwnProperty(k)) {
        var ptype = Strophe._connectionPlugins[k];
            // jslint complaints about the below line, but this is fine
            var F = function () {};
            F.prototype = ptype;
            this[k] = new F();
        this[k].init(this);	    
        }
    }	   
}

Openfire.Connection.prototype = {

    /** Function: reset
     *  Reset the connection.
     *
     *  This function should be called after a connection is disconnected
     *  before that connection is reused.
     */
     
    reset: function ()
    {
        this.streamId = null;

        this.timedHandlers = [];
        this.handlers = [];
        this.removeTimeds = [];
        this.removeHandlers = [];
        this.addTimeds = [];
        this.addHandlers = [];

        this.authenticated = false;
        this.disconnecting = false;
        this.connected = false;

        this.errors = 0;
    },


    /** Function: pause
     *  UNUSED with websockets
     */
     
    pause: function ()
    {
        return;
    },

    /** Function: resume
     *  UNUSED with websockets
     */
     
    resume: function ()
    {
        return;
    },

    /** Function: getUniqueId
     *  Generate a unique ID for use in <iq/> elements.
     *
     *  All <iq/> stanzas are required to have unique id attributes.  This
     *  function makes creating these easy.  Each connection instance has
     *  a counter which starts from zero, and the value of this counter
     *  plus a colon followed by the suffix becomes the unique id. If no
     *  suffix is supplied, the counter is used as the unique id.
     *
     *  Suffixes are used to make debugging easier when reading the stream
     *  data, and their use is recommended.  The counter resets to 0 for
     *  every new connection for the same reason.  For connections to the
     *  same server that authenticate the same way, all the ids should be
     *  the same, which makes it easy to see changes.  This is useful for
     *  automated testing as well.
     *
     *  Parameters:
     *    (String) suffix - A optional suffix to append to the id.
     *
     *  Returns:
     *    A unique string to be used for the id attribute.
     */
     
    getUniqueId: function (suffix)
    {
        if (typeof(suffix) == "string" || typeof(suffix) == "number") {
            return ++this._uniqueId + ":" + suffix;
        } else {
            return ++this._uniqueId + "";
        }
    },
    
    /** Function: connect
     *  Starts the connection process.
     *
     *
     *  Parameters:
     *    (String) username - The Openfire username.  
     *    (String) pass - The user's password.
     *    (String) resource - The user resource for this connection.     
     *    (Function) callback The connect callback function.
     */
     
    connect: function (jid, pass, callback, wait, hold, route)
    { 	    
        this.jid = jid.indexOf("/") > -1 ? jid : jid + '/' + this.resource;        
        this.username = jid.indexOf("@") < 0 ? null : jid.split("@")[0];
        this.pass = pass == "" ? null : pass;        
        this.connect_callback = callback;  
        this.disconnecting = false;
        this.connected = false;
        this.authenticated = false;
        this.errors = 0;
        
    this._changeConnectStatus(Strophe.Status.CONNECTING, null);   
    this.url = this.protocol + "//" + this.host + "/ws/server?username=" + this.username + "&password=" + this.pass + "&resource=" + this.resource;
    this._ws = new WebSocket(this.url, "xmpp");  
    
    this._ws.onopen = this._onopen.bind(this);
    this._ws.onmessage = this._onmessage.bind(this);
    this._ws.onclose = this._onclose.bind(this);
    
    window.openfireWebSocket = this;
    
        this.jid = this.jid.indexOf("@") < 0 ? this.resource + "@" + this.jid  : this.jid;
    
    this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);

    },


    /** 
     * Private Function: _onopen websocket event handler
     *
     */    
 
    _onopen: function() 
    {       
        this.connected = true;
        this.authenticated = true;
        this.resource = Strophe.getResourceFromJid(this.jid);
        this.domain = Strophe.getDomainFromJid(this.jid);
    
    try {
       
       this._changeConnectStatus(Strophe.Status.CONNECTED, null);	

    } catch (e) {

        throw Error("User connection callback caused an exception: " + e);
    } 
    
    this.interval = setInterval (function() {window.openfireWebSocket.sendRaw(" ")}, 10000 );	
    },
    
    /** Function: attach
     *  UNUSED, use connect again
     */
     
    attach: function()
    {
        return 
    },
    
    /** Function: xmlInput
     *  User overrideable function that receives XML data coming into the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Openfire.xmlInput = function (elem) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (XMLElement) elem - The XML data received by the connection.
     */
     
    xmlInput: function (elem)
    {
        return;
    },

    /** Function: xmlOutput
     *  User overrideable function that receives XML data sent to the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Openfire.xmlOutput = function (elem) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (XMLElement) elem - The XMLdata sent by the connection.
     */
     
    xmlOutput: function (elem)
    {
        return;
    },

    /** Function: rawInput
     *  User overrideable function that receives raw data coming into the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Openfire.rawInput = function (data) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (String) data - The data received by the connection.
     */
     
    rawInput: function (data)
    {
        return;
    },

    /** Function: rawOutput
     *  User overrideable function that receives raw data sent to the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Openfire.rawOutput = function (data) {
     *  >   (user code)
     *  > };
     *
     *  Parameters:
     *    (String) data - The data sent by the connection.
     */
     
    rawOutput: function (data)
    {
        return;
    },




    /** Function: sendRaw
     *  Send a stanza in raw XML text.
     *
     *  This function is called to push data onto the send queue to
     *  go out over the wire.  Whenever a request is sent to the BOSH
     *  server, all pending data is sent and the queue is flushed.
     *
     *  Parameters:
     *    xml - The stanza text XML to send.
     */
     
    sendRaw: function(xml) {

        if(!this.connected || this._ws == null) {
            throw Error("Not connected, cannot send packets.");
        }

    if (xml != " ")
    {
        this.xmlOutput(this._textToXML(xml));
        this.rawOutput(xml);
    }
    
        this._ws.send(xml);
    },
    

    /** Function: send
     *  Send a stanza.
     *
     *  This function is called to push data onto the send queue to
     *  go out over the wire.  Whenever a request is sent to the BOSH
     *  server, all pending data is sent and the queue is flushed.
     *
     *  Parameters:
     *    (XMLElement |
     *     [XMLElement] |
     *     Strophe.Builder) elem - The stanza to send.
     */
     
    send: function(elem) 
    {    
        if(!this.connected || this._ws == null) {
            throw Error("Not connected, cannot send packets.");
        }
   
        var toSend = "";
        
        if (elem === null) { return ; }
        
        if (typeof(elem.sort) === "function") 
        {
            for (var i = 0; i < elem.length; i++) 
            {
                toSend += Strophe.serialize(elem[i]);
                this.xmlOutput(elem[i]);
            }
            
        } else if (typeof(elem.tree) === "function") {
        
            toSend = Strophe.serialize(elem.tree());
            this.xmlOutput(elem.tree());
             
        } else {
        
            toSend = Strophe.serialize(elem);
            this.xmlOutput(elem);
        }
        
    this.rawOutput(toSend);
    this._ws.send(toSend);
    },    

    /** Function: flush
     *  UNUSED
     */
     
    flush: function ()
    {
        return
    },
    
   
    /** Function: sendIQ
     *  Helper function to send IQ stanzas.
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza to send.
     *    (Function) callback - The callback function for a successful request.
     *    (Function) errback - The callback function for a failed or timed
     *      out request.  On timeout, the stanza will be null.
     *    (Integer) timeout - The time specified in milliseconds for a
     *      timeout to occur.
     *
     *  Returns:
     *    The id used to send the IQ.
     */
    
    sendIQ: function(elem, callback, errback, timeout) {
        var timeoutHandler = null;
        var that = this;

        if (typeof(elem.tree) === "function") {
            elem = elem.tree();
        }
    var id = elem.getAttribute('id');

    // inject id if not found
    
    if (!id) {
        id = this.getUniqueId("sendIQ");
        elem.setAttribute("id", id);
    }

    var handler = this.addHandler(function (stanza) {
        // remove timeout handler if there is one
        
            if (timeoutHandler) {
                that.deleteTimedHandler(timeoutHandler);
            }

            var iqtype = stanza.getAttribute('type');
            
        if (iqtype == 'result') 
        {
        if (callback) {
                    callback(stanza);
                }
        } else if (iqtype == 'error') {
        if (errback) {
                    errback(stanza);
                }
        } else {
                throw {
                    name: "StropheError",
                    message: "Got bad IQ type of " + iqtype
                };
            }
    }, null, 'iq', null, id);

    // if timeout specified, setup timeout handler.
    
    if (timeout) 
    {
        timeoutHandler = this.addTimedHandler(timeout, function () {
                // get rid of normal handler
                that.deleteHandler(handler);

            // call errback on timeout with null stanza
                if (errback) {
            errback(null);
                }
        return false;
        });
    }

    this.send(elem);

    return id;
    },

    /** Function: addTimedHandler
     *  Add a timed handler to the connection.
     *
     *  This function adds a timed handler.  The provided handler will
     *  be called every period milliseconds until it returns false,
     *  the connection is terminated, or the handler is removed.  Handlers
     *  that wish to continue being invoked should return true.
     *
     *  Because of method binding it is necessary to save the result of
     *  this function if you wish to remove a handler with
     *  deleteTimedHandler().
     *
     *  Note that user handlers are not active until authentication is
     *  successful.
     *
     *  Parameters:
     *    (Integer) period - The period of the handler.
     *    (Function) handler - The callback function.
     *
     *  Returns:
     *    A reference to the handler that can be used to remove it.
     */
     
    addTimedHandler: function (period, handler)
    {
        var thand = new Strophe.TimedHandler(period, handler);
        this.addTimeds.push(thand);
        return thand;
    },


    /** Function: deleteTimedHandler
     *  Delete a timed handler for a connection.
     *
     *  This function removes a timed handler from the connection.  The
     *  handRef parameter is *not* the function passed to addTimedHandler(),
     *  but is the reference returned from addTimedHandler().
     *
     *  Parameters:
     *    (Strophe.TimedHandler) handRef - The handler reference.
     */
     
    deleteTimedHandler: function (handRef)
    {
        // this must be done in the Idle loop so that we don't change
        // the handlers during iteration
        
        this.removeTimeds.push(handRef);
    },

    /** Function: addHandler
     *  Add a stanza handler for the connection.
     *
     *  This function adds a stanza handler to the connection.  The
     *  handler callback will be called for any stanza that matches
     *  the parameters.  Note that if multiple parameters are supplied,
     *  they must all match for the handler to be invoked.
     *
     *  The handler will receive the stanza that triggered it as its argument.
     *  The handler should return true if it is to be invoked again;
     *  returning false will remove the handler after it returns.
     *
     *  As a convenience, the ns parameters applies to the top level element
     *  and also any of its immediate children.  This is primarily to make
     *  matching /iq/query elements easy.
     *
     *  The options argument contains handler matching flags that affect how
     *  matches are determined. Currently the only flag is matchBare (a
     *  boolean). When matchBare is true, the from parameter and the from
     *  attribute on the stanza will be matched as bare JIDs instead of
     *  full JIDs. To use this, pass {matchBare: true} as the value of
     *  options. The default value for matchBare is false. 
     *
     *  The return value should be saved if you wish to remove the handler
     *  with deleteHandler().
     *
     *  Parameters:
     *    (Function) handler - The user callback.
     *    (String) ns - The namespace to match.
     *    (String) name - The stanza name to match.
     *    (String) type - The stanza type attribute to match.
     *    (String) id - The stanza id attribute to match.
     *    (String) from - The stanza from attribute to match.
     *    (String) options - The handler options
     *
     *  Returns:
     *    A reference to the handler that can be used to remove it.
     */
     
    addHandler: function (handler, ns, name, type, id, from, options)
    {
        var hand = new Strophe.Handler(handler, ns, name, type, id, from, options);
        this.addHandlers.push(hand);
        return hand;
    },

    /** Function: deleteHandler
     *  Delete a stanza handler for a connection.
     *
     *  This function removes a stanza handler from the connection.  The
     *  handRef parameter is *not* the function passed to addHandler(),
     *  but is the reference returned from addHandler().
     *
     *  Parameters:
     *    (Strophe.Handler) handRef - The handler reference.
     */
     
    deleteHandler: function (handRef)
    {
        // this must be done in the Idle loop so that we don't change
        // the handlers during iteration
        
        this.removeHandlers.push(handRef);
    },

    /** Function: disconnect
     *  Start the graceful disconnection process.
     *
     *  This function starts the disconnection process.  This process starts
     *  by sending unavailable presence and sending BOSH body of type
     *  terminate.  A timeout handler makes sure that disconnection happens
     *  even if the BOSH server does not respond.
     *
     *  The user supplied connection callback will be notified of the
     *  progress as this process happens.
     *
     *  Parameters:
     *    (String) reason - The reason the disconnect is occuring.
     */    
    
    disconnect: function(reason) {
    
        if(!this.connected || this._ws == null) {
            return;
        }

        this._changeConnectStatus(Strophe.Status.DISCONNECTING, reason);  
        Strophe.info("Disconnect was called because: " + reason);
        
        this._ws.close();
        
    },

    /** PrivateFunction: _onDisconnectTimeout
     *  _Private_ timeout handler for handling non-graceful disconnection.
     *
     *  If the graceful disconnect process does not complete within the
     *  time allotted, this handler finishes the disconnect anyway.
     *
     *  Returns:
     *    false to remove the handler.
     */
     
    _onDisconnectTimeout: function ()
    {
        Strophe.info("_onDisconnectTimeout was called");
        
        this._doDisconnect();

        return false;
    },

    /** PrivateFunction: _doDisconnect
     *  _Private_ function to disconnect.
     *
     *  This is the last piece of the disconnection logic.  This resets the
     *  connection and alerts the user's connection callback.
     */
     
    _doDisconnect: function ()
    {
        Strophe.info("_doDisconnect was called");
    this._onclose();
    },
    
    /** PrivateFunction: _changeConnectStatus
     *  _Private_ helper function that makes sure plugins and the user's
     *  callback are notified of connection status changes.
     *
     *  Parameters:
     *    (Integer) status - the new connection status, one of the values
     *      in Strophe.Status
     *    (String) condition - the error condition or null
     */

    _changeConnectStatus: function (status, condition)
    {
        // notify all plugins listening for status changes
        
        for (var k in Strophe._connectionPlugins) 
        {
            if (Strophe._connectionPlugins.hasOwnProperty(k)) 
            {
                var plugin = this[k];
                
                if (plugin.statusChanged) 
                {
                    try {
                        plugin.statusChanged(status, condition);
                    } catch (err) {
                        Strophe.error("" + k + " plugin caused an exception changing status: " + err);
                    }
                }
            }
        }

        // notify the user's callback
        
    if (typeof this.connect_callback == 'function')
        {
            try {
                this.connect_callback(status, condition);
            } catch (e) {
                Strophe.error("User connection callback caused an exception: " + e);
           }
        }
    },



    /** 
     * Private Function: _onclose websocket event handler
     *
     */    

    _onclose: function() 
    {
        Strophe.info("websocket closed");
    //console.log('_onclose - disconnected');
    clearInterval(this.interval);
    
        this.authenticated = false;
        this.disconnecting = false;
        this.streamId = null;

        // tell the parent we disconnected
        
        this._changeConnectStatus(Strophe.Status.DISCONNECTED, null);
        this.connected = false;

        // delete handlers
        
        this.handlers = [];
        this.timedHandlers = [];
        this.removeTimeds = [];
        this.removeHandlers = [];
        this.addTimeds = [];
        this.addHandlers = [];
        
        if(this._ws.readyState != this._ws.CLOSED)
        {
          this._ws.close();
        }
    },

    /** 
     * Private Function: _onmessage websocket event handler
     *
     */    
    
    _onmessage: function(packet) 
    {
        var elem;
        
        try {
            elem = this._textToXML(packet.data);
            
        } catch (e) {
        
            if (e != "parsererror") { throw e; }
            this.disconnect("strophe-parsererror");
        }
        
        if (elem === null) { return; }
        
        this.xmlInput(elem);
        this.rawInput(packet.data);
        
        // remove handlers scheduled for deletion
        
        var i, hand;
        
        while (this.removeHandlers.length > 0) 
        {
            hand = this.removeHandlers.pop();
            i = this.handlers.indexOf(hand);
            
            if (i >= 0) {
                this.handlers.splice(i, 1);
            }
        }

        // add handlers scheduled for addition
        
        while (this.addHandlers.length > 0) 
        {
            this.handlers.push(this.addHandlers.pop());
        }  
        
        // send each incoming stanza through the handler chain
        
        var i, newList;
        newList = this.handlers;
        this.handlers = [];
        
        for (i = 0; i < newList.length; i++) 
        {          
            var hand = newList[i];
            
            if (hand.isMatch(elem) && (this.authenticated || !hand.user)) 
            {
                if (hand.run(elem)) 
                {
                    this.handlers.push(hand);
                }
                
            } else {
                this.handlers.push(hand);
            }
        }        
    },
    
    /** 
     * Private Function: _textToXML convert text to DOM Document object
     *
     */    

    _textToXML: function (text) {
            
        var doc = null;
        
        if (window['DOMParser']) {
            var parser = new DOMParser();
            doc = parser.parseFromString(text, 'text/xml');

        } else if (window['ActiveXObject']) {
            var doc = new ActiveXObject("MSXML2.DOMDocument");
            doc.async = false;
            doc.loadXML(text);
            
        } else {
            throw Error('No DOMParser object found.');
        }

        return doc.firstChild;
    },     

    /** PrivateFunction: _onIdle
     *  _Private_ handler to process events during idle cycle.
     *
     *  This handler is called every 100ms to fire timed handlers that
     *  are ready and keep poll requests going.
     */
     
    _onIdle: function ()
    {
        var i, thand, since, newList;

        // remove timed handlers that have been scheduled for deletion
        
        while (this.removeTimeds.length > 0) 
        {
            thand = this.removeTimeds.pop();
            i = this.timedHandlers.indexOf(thand);
            if (i >= 0) {
                this.timedHandlers.splice(i, 1);
            }
        }

        // add timed handlers scheduled for addition
        
        while (this.addTimeds.length > 0) 
        {
            this.timedHandlers.push(this.addTimeds.pop());
        }

        // call ready timed handlers
        var now = new Date().getTime();
        newList = [];
        
        for (i = 0; i < this.timedHandlers.length; i++) 
        {
            thand = this.timedHandlers[i];
            
            if (this.authenticated || !thand.user) {
                since = thand.lastCalled + thand.period;
                
                if (since - now <= 0) {
                    if (thand.run()) {
                        newList.push(thand);
                    }
                } else {
                    newList.push(thand);
                }
            }
        }
        
        this.timedHandlers = newList;
        
        // reactivate the timer
        
        clearTimeout(this._idleTimeout);
        this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
    }        
}
