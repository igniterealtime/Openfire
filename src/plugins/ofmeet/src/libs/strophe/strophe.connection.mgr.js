Strophe.addConnectionPlugin('connectionmanager', {
    /*
     This plugin implements a Connection Manager that monitors
     the Strophe connection and keeps track of unsent stanzas.
     */
    conn: null,
    firstTime: true,
    conn_state: null,
    element_queue: [],
    enabled: true,  // enabled by default

    _status_lookup: {},

    _receiveTimer: null,
    _reconnectInterval: null,

    // default config
    config: {
        // try to reconnet continously, even after a graceful disconnect
        // unless the disconnect reason is "logout"
        autoReconnect: true,

        // if true (default), will automatically empty queue on successfull reconnect
        // (+/- 20 stanzas per packet, 2 seconds apart)
        autoResend: true,

        receiveTimeout: 20, // in seconds
        pingTimeout: 10, // in seconds
        reconnectInterval: 10, // in seconds - interval at which to attempt reconnection
        onEnqueueElement: null,
        onDequeueElement: null,
        onReceiveTimeout: null,
        onPingTimeout: null,
        onPingOK: null
    },

    // API

    configure: function(config){
        config = config || {};

        for(var c in config){
            if(config.hasOwnProperty(c)){
                this.config[c] = config[c];
            }
        }
    },

    sendQueuedElements: function(max){
        max = max || this.element_queue.length;

        this.conn.pause(); // facilitate bulk sending
        for(var i = 0; i < this.element_queue.length && i < max; i++){
            this.conn.send(this.element_queue[i]);
        }
        this.conn.resume();
    },

    resendAll: function(sent_callback){
        var sendInt = setInterval(function(){
            if(this.element_queue.length === 0){
                clearInterval(sendInt);

                if(sent_callback){ sent_callback(); }
                return;
            }

            this.sendQueuedElements(20);
        }.bind(this), 2000);
    },

    enable: function(){
        this.disable(); // prevent double-enabling

        var that = this;
        var conn = this.conn;

        // insert a tap into the builtin _queueData function
        var _queueData = conn._queueData;
        
        if (_queueData)
        {
		conn._queueData = function (element) {
		    if(that._enqueueElement(element)){
			_queueData.call(this, element);
		    } else {
			console.log("NOT passing through queue call to Strophe Connection!");
		    }
		};
	}
        
        var _onRequestStateChange = conn._onRequestStateChange;
        
        if (_onRequestStateChange)
        {        
		conn._onRequestStateChange = function(func, req){
		    _onRequestStateChange.call(this, function(){
			that._requestReceived(req);
			func(req);
		    }, req);
		};
	}
        
        // build a reverse lookup of Strophe.Status states
        for(var s in Strophe.Status){
            if(Strophe.Status.hasOwnProperty(s)){
                this._status_lookup[Strophe.Status[s]] = s;
            }
        }

        this.enabled = true;
    },

    disable: function(){
        this.conn._queueData = Strophe.Connection.prototype._queueData;
        this.conn._onRequestStateChange = Strophe.Connection.prototype._onRequestStateChange;

        clearTimeout(this._receiveTimer);
        clearInterval(this._reconnectInterval);

        this.enabled = false;
    },

    reconnect: function(){
	console.log("reconnect start");
		
      	var xhr = new XMLHttpRequest();
      	xhr.open("GET", "/ofmeet/config", true);
      	
	xhr.onload = (function() {
		console.log("reconnect onload");
	}).bind(this);
	
	xhr.send(null);      	
      
        this.conn.disconnect();
        this.conn._onDisconnectTimeout(); // clears requests
        this.conn.connect(this.conn.jid, this.conn.pass,
                          this.conn.connect_callback,
                          this.conn.wait, this.conn.hold);
    },

    //--------------------------

    init: function(conn) {
        this.conn = conn;

        this.enable();

        console.log("strophe plugin: connectionmanager enabled");         
    },

    /** Called automatically by Strophe when the connection status changes */
    statusChanged: function(status, condition){
        if(!this.enabled){
            return;
        }

        this.conn_state = status;
        //console.log("Strophe connection status: " + this._status_lookup[status] || status, condition);

        // start timer when connected and reset it when not connected
        clearTimeout(this._receiveTimer);

        if(status == Strophe.Status.CONNECTED){
            this.conn.send($pres()); 

            if (!this.firstTime) setTimeout(function() { window.location.reload(true);}, 5000);
            
            this.firstTime = false; 
            
            this._restartReceiveTimer();
            clearInterval(this._reconnectInterval);
//             clearInterval(this.__countInterval);
//             this.__countInterval = null;
      
            if(this.config.autoResend){
                this.resendAll();
            }
                    }
        else if(status == Strophe.Status.DISCONNECTING){

        }
        else if(status == Strophe.Status.DISCONNECTED){
            this.conn.reconnecting = true;

            if(condition == "logout"){
                // disable auto reconnect
                this.config.autoReconnect = false;
            }
            
            clearInterval(this._reconnectInterval);
                
            if(this.config.autoReconnect){           
                this._reconnectInterval = setInterval(
                    function(){
                        if(!this.conn.connected){
                            this.reconnect();
                        }
                    }.bind(this),
                    this.config.reconnectInterval * 1000);

//                 // start the counting interval (for testing purposes)
//                 if(this.__countInterval == null){
//                     var i = 0;
//                     this.__countInterval = setInterval(function(){
//                         this.conn.send($msg({to: "all@conference." + this.conn.domain,
//                                              type: "groupchat"}).c("body").t(i++));
//                     }.bind(this), 1000);
//                 }
            }
        }
    },

    _enqueueElement: function(el){
         //console.log("req sent: " + el);

        if(!el){
            return;
        }
        

        // only enqueue elements that are not used for authentication
        // or session establishment.
        // also don't buffer presence stanzas
        if( (!el.getAttribute("id") 
            || (Strophe.isTagEqual(el, "iq") && el.getAttribute("id").indexOf("_auth_") != -1))
            || el.getAttribute("xmlns") == "urn:ietf:params:xml:ns:xmpp-sasl"
            || Strophe.isTagEqual(el, "presence")
          ){
            // this is an auth/session stanza
            return true;
        }

        // only push non-empty requests
        if(this.element_queue.indexOf(el) == -1){
            this.element_queue.push(el);

            if(this.config.onEnqueueElement){
                this.config.onEnqueueElement(el);
            }
        }

         //console.log("sentQueue: " + this.element_queue.length + " " + this.element_queue);
         //console.log("_requests: " + this.conn._requests.length + " " + this.conn._requests);

        // if strophe is in the AUTHENTICATING (auth/session establishment) stage,
        // don't allow "regular requests" to be pushed through
//         if(this.conn_state == Strophe.Status.AUTHENTICATING ||
//            this.conn_state == Strophe.Status.DISCONNECTED){
        if(this.conn_state != Strophe.Status.CONNECTED){
            return false;
        }

        return true;
    },

    _requestReceived: function(req){
         //console.log("req received: " + req);

        // only dequeue in connected state
        if(this.conn_state == Strophe.Status.CONNECTED){
            // clear the queue
            var els = req.xmlData.childNodes;
            if(els && els.length > 0){
                for(var i = 0; i < this.element_queue.length; i++){
                    for(var j = 0; j < els.length; j++){
                        //if(this.element_queue[i] == els[j]){
                        if(this.element_queue[i] && els[j] && (
                            this.element_queue[i].getAttribute("id") 
                                == els[j].getAttribute("id"))){  // use request id comparisons instead
                            //console.log("removing from sent queue:" + this.element_queue[i]);
                            this.element_queue.splice(i--, 1);

                            if(this.config.onDequeueElement){
                                this.config.onDequeueElement(els[j]);
                            }
                        }
                    }
                }
            }

             //console.log("sentQueue:" + this.element_queue.length + " " + this.element_queue);
             //console.log("_requests:" + this.conn._requests.length + " " + this.conn._requests);
        }

        this._restartReceiveTimer();
    },

    _restartReceiveTimer: function(){
        clearTimeout(this._receiveTimer);
        this._receiveTimer = setTimeout(this._onReceiveTimeout.bind(this),
                                        this.config.receiveTimeout * 1000);
    },

    _onReceiveTimeout: function(){
        // receive timeout reached, do a ping
        Strophe.info("CM: receive timeout");

        if(this.config.onReceiveTimeout){
            this.config.onReceiveTimeout();
        }

        this.conn.send($iq(
            {
                id: "ping",
                type: "get",
                to: this.conn.domain
            }).c("ping", {xmlns: 'urn:xmpp:ping'}));

        var pingTimeout = setTimeout(this._onPingTimeout.bind(this),
                                     this.config.pingTimeout * 1000);

        this.conn.addHandler(function(resp){
            clearTimeout(pingTimeout);

            if(this.config.onPingOK){
                this.config.onPingOK();
            }
        }.bind(this), null, null, null, "ping");
    },

    _onPingTimeout: function(){
        Strophe.warn("CM: ping timed out, disconnecting!");

        if(this.config.onPingTimeout){
            this.config.onPingTimeout();
        }

        // disconnect connection
        this.conn.disconnect();
    }

});