var Flash = {
	installed: false,
	callbacks: new Array(),
	install: function(callbackName) {
		dojo.event.connect(dojo.flash, "loaded", callbackName);
		dojo.flash.setSwf({flash6: "/crossdomain_version6.swf",
						   flash8: "/crossdomain_version8.swf",
						   visible: false});
	},
	request: function() {
		var self = this;
    	var _method, _url = null;
    	var _contentType = "application/x-www-form-urlencoded";
    	var _headers = new Array();
    
    	// responseXML 
    	// status 
    
    	this.open = function(method, url, async, user, password) { 
        	_method = method;
        	_url = url;
    	}
    
    	this.send = function(body) {               
        	function callback(response) {
            	self.responseText = response;
				self.responseXML = XmlDocument.create();
				self.responseXML.loadXML(response);
            
            	if (self.onload) {
             	   self.onload(self);
            	}
        	}
        	dojo.flash.comm.XmlHttp(
			_url, Flash.registerCallback(callback), _method, body, _contentType);
    	}
    
    	this.setRequestHeader = function(header, value) {
        	if (header.toLowerCase() == "Content-Type".toLowerCase()) {
            	_contentType = value;
            	return;
        	}
    	}
    
    	this.getRequestHeader = function() { alert("not supported"); }
    	this.getResponseHeader = function(a) { alert("not supported"); }
    	this.getAllResponseHeaders = function() { alert("not supported"); }
    	this.abort = function() { alert("not supported"); }
    	this.addEventListener = function(a, b, c) { alert("not supported"); }
    	this.dispatchEvent = function(e) { alert("not supported"); }
    	this.openRequest = function(a, b, c, d, e) { this.open(a, b, c, d, e); }
    	this.overrideMimeType = function(e) { alert("not supported"); }
    	this.removeEventListener = function(a, b, c) { alert("not supported"); }
	},
	registerCallback: function(callback) {
    	// todo: could be improved (look for the first available spot in the callbacks table,
		// if necessary, expand it)
    	var length = this.callbacks.push(selfDeleteCallback);
    	var callbackID = length - 1;
    
    	return "Flash.callbacks[" + callbackID + "]";
    
    	function selfDeleteCallback(obj) {
        	delete Flash.callbacks[callbackID];
        	setTimeout(function() { callback(obj); }, 0);
        	return;
    	} 
	}
}