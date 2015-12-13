

/*
Copyright 2007 Adobe Systems Incorporated

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.


THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/

/*
 * The Bridge class, responsible for navigating AS instances
 */
function FABridge(target,bridgeName)
{
    this.target = target;
    this.remoteTypeCache = {};
    this.remoteInstanceCache = {};
    this.remoteFunctionCache = {};
    this.localFunctionCache = {};
    this.bridgeID = FABridge.nextBridgeID++;
    this.name = bridgeName;
    this.nextLocalFuncID = 0;
    FABridge.instances[this.name] = this;
    FABridge.idMap[this.bridgeID] = this;

    return this;
}

// type codes for packed values
FABridge.TYPE_ASINSTANCE =  1;
FABridge.TYPE_ASFUNCTION =  2;

FABridge.TYPE_JSFUNCTION =  3;
FABridge.TYPE_ANONYMOUS =   4;

FABridge.initCallbacks = {};
FABridge.userTypes = {};

FABridge.addToUserTypes = function()
{
	for (var i = 0; i < arguments.length; i++)
	{
		FABridge.userTypes[arguments[i]] = {
			'typeName': arguments[i], 
			'enriched': false
		};
	}
}

FABridge.argsToArray = function(args)
{
    var result = [];
    for (var i = 0; i < args.length; i++)
    {
        result[i] = args[i];
    }
    return result;
}

function instanceFactory(objID)
{
    this.fb_instance_id = objID;
    return this;
}

function FABridge__invokeJSFunction(args)
{  
    var funcID = args[0];
    var throughArgs = args.concat();//FABridge.argsToArray(arguments);
    throughArgs.shift();
   
    var bridge = FABridge.extractBridgeFromID(funcID);
    return bridge.invokeLocalFunction(funcID, throughArgs);
}

FABridge.addInitializationCallback = function(bridgeName, callback)
{
    var inst = FABridge.instances[bridgeName];
    if (inst != undefined)
    {
        callback.call(inst);
        return;
    }

    var callbackList = FABridge.initCallbacks[bridgeName];
    if(callbackList == null)
    {
        FABridge.initCallbacks[bridgeName] = callbackList = [];
    }

    callbackList.push(callback);
}

function FABridge__bridgeInitialized(bridgeName)
{
    var searchStr = "bridgeName="+ bridgeName;

    if (/Explorer/.test(navigator.appName) || /Opera/.test(navigator.appName) || /Netscape/.test(navigator.appName) || /Konqueror|Safari|KHTML/.test(navigator.appVersion))
    {

        var flashInstances = document.getElementsByTagName("object");
        if (flashInstances.length == 1)
        {
            FABridge.attachBridge(flashInstances[0], bridgeName);
        }
        else
        {
            for(var i = 0; i < flashInstances.length; i++)
            {
                var inst = flashInstances[i];
                var params = inst.childNodes;
                var flash_found = false;

                for (var j = 0; j < params.length; j++)
                {
                    var param = params[j];
                    if (param.nodeType == 1 && param.tagName.toLowerCase() == "param")
                    {
                        if (param["name"].toLowerCase() == "flashvars" && param["value"].indexOf(searchStr) >= 0)
                        {
                            FABridge.attachBridge(inst, bridgeName);
                            flash_found = true;
                            break;
                        }
                    }
                }

                if (flash_found) {
                    break;
                }
            }
        }
    }
    else
    {
        var flashInstances = document.getElementsByTagName("embed");
        if (flashInstances.length == 1)
        {
            FABridge.attachBridge(flashInstances[0], bridgeName);
        }
        else
        {
            for(var i = 0; i < flashInstances.length; i++)
            {
                var inst = flashInstances[i];
                var flashVars = inst.attributes.getNamedItem("flashVars").nodeValue;
                if (flashVars.indexOf(searchStr) >= 0)
                {
                    FABridge.attachBridge(inst, bridgeName);
                }

            }
        }
    }
    return true;
}

// used to track multiple bridge instances, since callbacks from AS are global across the page.

FABridge.nextBridgeID = 0;
FABridge.instances = {};
FABridge.idMap = {};
FABridge.refCount = 0;

FABridge.extractBridgeFromID = function(id)
{
    var bridgeID = (id >> 16);
    return FABridge.idMap[bridgeID];
}

FABridge.attachBridge = function(instance, bridgeName)
{
    var newBridgeInstance = new FABridge(instance, bridgeName);

    FABridge[bridgeName] = newBridgeInstance;

/*  FABridge[bridgeName] = function() {
        return newBridgeInstance.root();
    }
*/

    var callbacks = FABridge.initCallbacks[bridgeName];
    if (callbacks == null)
    {
        return;
    }
    for (var i = 0; i < callbacks.length; i++)
    {
        callbacks[i].call(newBridgeInstance);
    }
    delete FABridge.initCallbacks[bridgeName]
}

// some methods can't be proxied.  You can use the explicit get,set, and call methods if necessary.

FABridge.blockedMethods =
{
    toString: true,
    get: true,
    set: true,
    call: true
};

FABridge.prototype =
{


// bootstrapping

    root: function()
    {
        return this.deserialize(this.target.getRoot());
    },

    releaseASObjects: function()
    {
        return this.target.releaseASObjects();
    },

    releaseNamedASObject: function(value)
    {
        if(typeof(value) != "object")
        {
            return false;
        }
        else
        {
            var ret =  this.target.releaseNamedASObject(value.fb_instance_id);
            return ret;
        }
    },

    create: function(className)
    {
        return this.deserialize(this.target.create(className));
    },


    // utilities

    makeID: function(token)
    {
        return (this.bridgeID << 16) + token;
    },


    // low level access to the flash object

    getPropertyFromAS: function(objRef, propName)
    {
        if (FABridge.refCount > 0)
        {
            throw new Error("You are trying to call recursively into the Flash Player which is not allowed. In most cases the JavaScript setTimeout function, can be used as a workaround.");
        }
        else
        {
            FABridge.refCount++;
            retVal = this.target.getPropFromAS(objRef, propName);
            retVal = this.handleError(retVal);
            FABridge.refCount--;
            return retVal;
        }
    },

    setPropertyInAS: function(objRef,propName, value)
    {
        if (FABridge.refCount > 0)
        {
            throw new Error("You are trying to call recursively into the Flash Player which is not allowed. In most cases the JavaScript setTimeout function, can be used as a workaround.");
        }
        else
        {
            FABridge.refCount++;
            retVal = this.target.setPropInAS(objRef,propName, this.serialize(value));
            retVal = this.handleError(retVal);
            FABridge.refCount--;
            return retVal;
        }
    },

    callASFunction: function(funcID, args)
    {
        if (FABridge.refCount > 0)
        {
            throw new Error("You are trying to call recursively into the Flash Player which is not allowed. In most cases the JavaScript setTimeout function, can be used as a workaround.");
        }
        else
        {
            FABridge.refCount++;
            retVal = this.target.invokeASFunction(funcID, this.serialize(args));
            retVal = this.handleError(retVal);
            FABridge.refCount--;
            return retVal;
        }
    },

    callASMethod: function(objID, funcName, args)
    {
        if (FABridge.refCount > 0)
        {
            throw new Error("You are trying to call recursively into the Flash Player which is not allowed. In most cases the JavaScript setTimeout function, can be used as a workaround.");
        }
        else
        {
            FABridge.refCount++;
            args = this.serialize(args);
            retVal = this.target.invokeASMethod(objID, funcName, args);
            retVal = this.handleError(retVal);
            FABridge.refCount--;
            return retVal;
        }
    },

    // responders to remote calls from flash

    invokeLocalFunction: function(funcID, args)
    {
        var result;
        var func = this.localFunctionCache[funcID];

        if(func != undefined)
        {
            result = this.serialize(func.apply(null, this.deserialize(args)));
        }

        return result;
    },

    // Object Types and Proxies
	getUserTypeDescriptor: function(objTypeName)
	{
		var simpleType = objTypeName.replace(/^([^:]*)\:\:([^:]*)$/, "$2");
    	var isUserProto = ((typeof window[simpleType] == "function") && (typeof FABridge.userTypes[simpleType] != "undefined"));

    	var protoEnriched = false;
    	
    	if (isUserProto) {
	    	protoEnriched = FABridge.userTypes[simpleType].enriched;
    	}
    	var toret = {
    		'simpleType': simpleType, 
    		'isUserProto': isUserProto, 
    		'protoEnriched': protoEnriched
    	};
    	return toret;
	}, 
	
    // accepts an object reference, returns a type object matching the obj reference.
    getTypeFromName: function(objTypeName)
    {
    	var ut = this.getUserTypeDescriptor(objTypeName);
    	var toret = this.remoteTypeCache[objTypeName];
    	if (ut.isUserProto)
		{
    		//enrich both of the prototypes: the FABridge one, as well as the class in the page. 
	    	if (!ut.protoEnriched)
			{

		    	for (i in window[ut.simpleType].prototype)
				{
		    		toret[i] = window[ut.simpleType].prototype[i];
		    	}
				
				window[ut.simpleType].prototype = toret;
				this.remoteTypeCache[objTypeName] = toret;
				FABridge.userTypes[ut.simpleType].enriched = true;
	    	}
    	}
        return toret;
    },

    createProxy: function(objID, typeName)
    {
    	//get user created type, if it exists
    	var ut = this.getUserTypeDescriptor(typeName);

        var objType = this.getTypeFromName(typeName);

		if (ut.isUserProto)
		{
			var instFactory = window[ut.simpleType];
			var instance = new instFactory(this.name, objID);
			instance.fb_instance_id = objID;
		}
		else
		{
	        instanceFactory.prototype = objType;
	        var instance = new instanceFactory(objID);
		}

        this.remoteInstanceCache[objID] = instance;
        return instance;
    },

    getProxy: function(objID)
    {
        return this.remoteInstanceCache[objID];
    },

    // accepts a type structure, returns a constructed type
    addTypeDataToCache: function(typeData)
    {
        newType = new ASProxy(this, typeData.name);
        var accessors = typeData.accessors;
        for (var i = 0; i < accessors.length; i++)
        {
            this.addPropertyToType(newType, accessors[i]);
        }

        var methods = typeData.methods;
        for (var i = 0; i < methods.length; i++)
        {
            if (FABridge.blockedMethods[methods[i]] == undefined)
            {
                this.addMethodToType(newType, methods[i]);
            }
        }


        this.remoteTypeCache[newType.typeName] = newType;
        return newType;
    },

    addPropertyToType: function(ty, propName)
    {
        var c = propName.charAt(0);
        var setterName;
        var getterName;
        if(c >= "a" && c <= "z")
        {
            getterName = "get" + c.toUpperCase() + propName.substr(1);
            setterName = "set" + c.toUpperCase() + propName.substr(1);
        }
        else
        {
            getterName = "get" + propName;
            setterName = "set" + propName;
        }
        ty[setterName] = function(val)
        {
            this.bridge.setPropertyInAS(this.fb_instance_id, propName, val);
        }
        ty[getterName] = function()
        {
            return this.bridge.deserialize(this.bridge.getPropertyFromAS(this.fb_instance_id, propName));
        }
    },

    addMethodToType: function(ty, methodName)
    {
        ty[methodName] = function()
        {
            return this.bridge.deserialize(this.bridge.callASMethod(this.fb_instance_id, methodName, FABridge.argsToArray(arguments)));
        }
    },

    // Function Proxies

    getFunctionProxy: function(funcID)
    {
        var bridge = this;
        if (this.remoteFunctionCache[funcID] == null)
        {
            this.remoteFunctionCache[funcID] = function()
            {
                bridge.callASFunction(funcID, FABridge.argsToArray(arguments));
            }
        }
        return this.remoteFunctionCache[funcID];
    },

    getFunctionID: function(func)
    {
        if (func.__bridge_id__ == undefined)
        {
            func.__bridge_id__ = this.makeID(this.nextLocalFuncID++);
            this.localFunctionCache[func.__bridge_id__] = func;
        }
        return func.__bridge_id__;
    },

    // serialization / deserialization

    serialize: function(value)
    {
        var result = {};

        var t = typeof(value);
        if (t == "number" || t == "string" || t == "boolean" || t == null || t == undefined)
        {
            result = value;
        }
        else if (value instanceof Array)
        {
            result = [];
            for (var i = 0; i < value.length; i++)
            {
                result[i] = this.serialize(value[i]);
            }
        }
        else if (t == "function")
        {
            result.type = FABridge.TYPE_JSFUNCTION;
            result.value = this.getFunctionID(value);
        }
        else if (value instanceof ASProxy)
        {
            result.type = FABridge.TYPE_ASINSTANCE;
            result.value = value.fb_instance_id;
        }
        else
        {
            result.type = FABridge.TYPE_ANONYMOUS;
            result.value = value;
        }

        return result;
    },

    deserialize: function(packedValue)
    {

        var result;

        var t = typeof(packedValue);
        if (t == "number" || t == "string" || t == "boolean" || packedValue == null || packedValue == undefined)
        {
            result = this.handleError(packedValue);
            //if (typeof(retVal)=="string" && retVal.indexOf("__FLASHERROR")==0)
            //{
            //    throw new Error(retVal);
            //}
        }
        else if (packedValue instanceof Array)
        {
            result = [];
            for (var i = 0; i < packedValue.length; i++)
            {
                result[i] = this.deserialize(packedValue[i]);
            }
        }
        else if (t == "object")
        {
            for(var i = 0; i < packedValue.newTypes.length; i++)
            {
                this.addTypeDataToCache(packedValue.newTypes[i]);
            }
            for (var aRefID in packedValue.newRefs)
            {
                this.createProxy(aRefID, packedValue.newRefs[aRefID]);
            }
            if (packedValue.type == FABridge.TYPE_PRIMITIVE)
            {
                result = packedValue.value;
            }
            else if (packedValue.type == FABridge.TYPE_ASFUNCTION)
            {
                result = this.getFunctionProxy(packedValue.value);
            }
            else if (packedValue.type == FABridge.TYPE_ASINSTANCE)
            {
                result = this.getProxy(packedValue.value);
            }
            else if (packedValue.type == FABridge.TYPE_ANONYMOUS)
            {
                result = packedValue.value;
            }
        }
        return result;
    },

    addRef: function(obj)
    {
        this.target.incRef(obj.fb_instance_id);
    },

    release:function(obj)
    {
        this.target.releaseRef(obj.fb_instance_id);
    },

    handleError: function(value)
    {
        if (typeof(value)=="string" && value.indexOf("__FLASHERROR")==0)
        {
            var myErrorMessage = value.split("||");
            if(FABridge.refCount > 0 )
            {
                FABridge.refCount--;
            }
            throw new Error(myErrorMessage[1]);
            return value;
        }
        else
        {
            return value;
        }   
    }
};

// The root ASProxy class that facades a flash object

ASProxy = function(bridge, typeName)
{
    this.bridge = bridge;
    this.typeName = typeName;
    return this;
};

ASProxy.prototype =
{
    get: function(propName)
    {
        return this.bridge.deserialize(this.bridge.getPropertyFromAS(this.fb_instance_id, propName));
    },

    set: function(propName, value)
    {
        this.bridge.setPropertyInAS(this.fb_instance_id, propName, value);
    },

    call: function(funcName, args)
    {
        this.bridge.callASMethod(this.fb_instance_id, funcName, args);
    }, 
    
    addRef: function() {
        this.bridge.addRef(this);
    }, 
    
    release: function() {
        this.bridge.release(this);
    }
};


// FIXME: Needed by flXHR
var flensed={base_path:"//s.phono.com/deps/flensed/1.0/"};

;function Phono(config) {

   // Define defualt config and merge from constructor
   this.config = Phono.util.extend({
      gateway: window.location.hostname,
      connectionUrl: window.location.protocol + "//" + window.location.host + "/http-bind/"
   }, config);

   // Bind 'on' handlers
   Phono.events.bind(this, config);
   
   // Initialize Fields
   this.sessionId = null;
   Phono.log.debug("ConnectionUrl: " + this.config.connectionUrl);
   this.connection = new Strophe.Connection(this.config.connectionUrl);
   if(navigator.appName.indexOf('Internet Explorer')>0){
    xmlSerializer = {};
    xmlSerializer.serializeToString = function(body) {return body.xml;};
   } else {
    xmlSerializer = new XMLSerializer();
   }
   this.connection.xmlInput = function (body) {
       Phono.log.debug("[WIRE] (i) " + xmlSerializer.serializeToString(body));
   };

   this.connection.xmlOutput = function (body) {
       Phono.log.debug("[WIRE] (o) " + xmlSerializer.serializeToString(body));
   };

   // Wrap ourselves with logging
   Phono.util.loggify("Phono", this);
   
   this.connect();
   
};

(function() {
   
    // ======================================================================
   
;Phono.util = {
   guid: function() {
     return MD5.hexdigest(new String((new Date()).getTime())) 
   },
   escapeXmppNode: function(input) {
      var node = input;
		node = node.replace(/\\/g, "\\5c");
		node = node.replace(/ /g, "\\20");
		node = node.replace(/\"/, "\\22");
		node = node.replace(/&/g, "\\26");
		node = node.replace(/\'/, "\\27");
		node = node.replace(/\//g, "\\2f");
		node = node.replace(/:/g, "\\3a");
		node = node.replace(/</g, "\\3c");
		node = node.replace(/>/g, "\\3e");
		node = node.replace(/@/g, "\\40");         
      return node;
   },
   // From jQuery 1.4.2
	each: function( object, callback, args ) {
		var name, i = 0,
			length = object.length,
			isObj = length === undefined || jQuery.isFunction(object);

		if ( args ) {
			if ( isObj ) {
				for ( name in object ) {
					if ( callback.apply( object[ name ], args ) === false ) {
						break;
					}
				}
			} else {
				for ( ; i < length; ) {
					if ( callback.apply( object[ i++ ], args ) === false ) {
						break;
					}
				}
			}

		// A special, fast, case for the most common use of each
		} else {
			if ( isObj ) {
				for ( name in object ) {
					if ( callback.call( object[ name ], name, object[ name ] ) === false ) {
						break;
					}
				}
			} else {
				for ( var value = object[0];
					i < length && callback.call( value, i, value ) !== false; value = object[++i] ) {}
			}
		}

		return object;
	},   
	isFunction: function( obj ) {
		return toString.call(obj) === "[object Function]";
	},

	isArray: function( obj ) {
		return toString.call(obj) === "[object Array]";
	},   
	isPlainObject: function( obj ) {
		if ( !obj || toString.call(obj) !== "[object Object]" || obj.nodeType || obj.setInterval ) {
			return false;
		}
		if ( obj.constructor
			&& !hasOwnProperty.call(obj, "constructor")
			&& !hasOwnProperty.call(obj.constructor.prototype, "isPrototypeOf") ) {
			return false;
		}
		var key;
		for ( key in obj ) {}
		
		return key === undefined || hasOwnProperty.call( obj, key );
	},	
   extend: function() {
   	var target = arguments[0] || {}, i = 1, length = arguments.length, deep = false, options, name, src, copy;
   	if ( typeof target === "boolean" ) {
   		deep = target;
   		target = arguments[1] || {};
   		i = 2;
   	}
   	if ( typeof target !== "object" && !jQuery.isFunction(target) ) {
   		target = {};
   	}
   	if ( length === i ) {
   		target = this;
   		--i;
   	}
   	for ( ; i < length; i++ ) {
   		if ( (options = arguments[ i ]) != null ) {
   			for ( name in options ) {
   				src = target[ name ];
   				copy = options[ name ];
   				if ( target === copy ) {
   					continue;
   				}
   				if ( deep && copy && ( jQuery.isPlainObject(copy) || jQuery.isArray(copy) ) ) {
   					var clone = src && ( jQuery.isPlainObject(src) || jQuery.isArray(src) ) ? src
   						: jQuery.isArray(copy) ? [] : {};
   					target[ name ] = jQuery.extend( deep, clone, copy );
   				} else if ( copy !== undefined ) {
   					target[ name ] = copy;
   				}
   			}
   		}
   	}
   	return target;
   },
   
   
   // Inspired by...
   // written by Dean Edwards, 2005
   // with input from Tino Zijdel, Matthias Miller, Diego Perini   
   eventCounter: 1,
   addEvent: function(target, type, handler) {
		// assign each event handler a unique ID
		if (!handler.$$guid) handler.$$guid = this.eventCounter++;
		// create a hash table of event types for the target
		if (!target.events) target.events = {};
		// create a hash table of event handlers for each target/event pair
		var handlers = target.events[type];
		if (!handlers) {
			handlers = target.events[type] = {};
			// store the existing event handler (if there is one)
			if (target["on" + type]) {
				handlers[0] = target["on" + type];
			}
		}
		// store the event handler in the hash table
		handlers[handler.$$guid] = handler;
		// assign a global event handler to do all the work
		target["on" + type] = handleEvent;
   },
   removeEvent: function(target, type, handler) {
		// delete the event handler from the hash table
		if (target.events && target.events[type]) {
			delete target.events[type][handler.$$guid];
		}
   },
   handleEvent: function(event) {
   	var returnValue = true;
   	// get a reference to the hash table of event handlers
   	var handlers = this.events[event.type];
   	// execute each event handler
   	for (var i in handlers) {
   		this.$$handleEvent = handlers[i];
   		if (this.$$handleEvent(event) === false) {
   			returnValue = false;
   		}
   	}
   	return returnValue;
   },
    /* parseUri JS v0.1, by Steven Levithan (http://badassery.blogspot.com)
       Splits any well-formed URI into the following parts (all are optional):
       ----------------------
       • source (since the exec() method returns backreference 0 [i.e., the entire match] as key 0, we might as well use it)
       • protocol (scheme)
       • authority (includes both the domain and port)
       • domain (part of the authority; can be an IP address)
       • port (part of the authority)
       • path (includes both the directory path and filename)
       • directoryPath (part of the path; supports directories with periods, and without a trailing backslash)
       • fileName (part of the path)
       • query (does not include the leading question mark)
       • anchor (fragment)
    */
    parseUri: function(sourceUri) {
        var uriPartNames = ["source","protocol","authority","domain","port","path","directoryPath","fileName","query","anchor"];
        var uriParts = new RegExp("^(?:([^:/?#.]+):)?(?://)?(([^:/?#]*)(?::(\\d*))?)?((/(?:[^?#](?![^?#/]*\\.[^?#/.]+(?:[\\?#]|$)))*/?)?([^?#/]*))?(?:\\?([^#]*))?(?:#(.*))?").exec(sourceUri);
        var uri = {};
        
        for(var i = 0; i < 10; i++){
        uri[uriPartNames[i]] = (uriParts[i] ? uriParts[i] : "");
        }
        
        // Always end directoryPath with a trailing backslash if a path was present in the source URI
        // Note that a trailing backslash is NOT automatically inserted within or appended to the "path" key
        if(uri.directoryPath.length > 0){
            uri.directoryPath = uri.directoryPath.replace(/\/?$/, "/");
        }
    
        return uri;
    },
    filterWideband: function(offer, wideband) {
        var codecs = new Array();
        Phono.util.each(offer, function() {
            if (!wideband) {
                if (this.name.toUpperCase() != "G722" && this.rate != "16000") {
                    codecs.push(this);
                }
            } else {
                codecs.push(this);
            }
        });
        return codecs;
    },
    isIOS: function() {
        var userAgent = window.navigator.userAgent;
        if (userAgent.match(/iPad/i) || userAgent.match(/iPhone/i)) {
            return true;
        }
        return false;
    },
    isAndroid: function() {
        var userAgent = window.navigator.userAgent;
        if (userAgent.match(/Android/i)) {
            return true;
        }
        return false;
    },
    localUri : function(fullUri) {
        var splitUri = fullUri.split(":");
        return splitUri[0] + ":" + splitUri[1] + ":" + splitUri[2];
    },
    loggify: function(objName, obj) {
        for(prop in obj) {
            if(typeof obj[prop] === 'function') {
                Phono.util.loggyFunction(objName, obj, prop);
            }
        }
        return obj;
    },
    loggyFunction: function(objName, obj, funcName) {
        var original = obj[funcName];
        obj[funcName] = function() {

            // Convert arguments to a real array
            var sep = "";
            var args = "";
            for (var i = 0; i < arguments.length; i++) {
                args+= (sep + arguments[i]);
                sep = ",";
            }
            
            Phono.log.debug("[INVOKE] " + objName + "." + funcName + "(" + args  + ")");
            return original.apply(obj, arguments);
        }
    },
    padWithZeroes: function(num, len) {
        var str = "" + num;
        while (str.length < len) {
            str = "0" + str;
        }
        return str;
    },
    padWithSpaces: function(str, len) {
        while (str.length < len) {
            str += " ";
        }
        return str;
    }
};


;var PhonoLogger = function() {
    var logger = this;
    logger.eventQueue = [];
    logger.initialized = false;
    $(document).ready(function() {
        if (typeof console === "undefined" || typeof console.log === "undefined") {
         console = {};
         console.log = function(mess) {
         // last ditch loging uncomment this 
	//		alert(mess)
		};
        }
        console.log("Phono Logger Initialized")
        logger.initialized = true;
        logger.flushEventQueue();
    });    
};

(function() {

    var newLine = "\r\n";

    // Logging events
    // ====================================================================================

    var PhonoLogEvent = function(timeStamp, level, messages, exception) {
        this.timeStamp = timeStamp;
        this.level = level;
        this.messages = messages;
        this.exception = exception;
    };

    PhonoLogEvent.prototype = {
        getThrowableStrRep: function() {
            return this.exception ? getExceptionStringRep(this.exception) : "";
        },
        getCombinedMessages: function() {
            return (this.messages.length === 1) ? this.messages[0] : this.messages.join(newLine);
        }
    };

    // Log Levels
    // ====================================================================================

    var PhonoLogLevel = function(level, name) {
        this.level = level;
        this.name = name;
    };

    PhonoLogLevel.prototype = {
        toString: function() {
            return this.name;
        },
        equals: function(level) {
            return this.level == level.level;
        },
        isGreaterOrEqual: function(level) {
            return this.level >= level.level;
        }
    };

    PhonoLogLevel.ALL = new PhonoLogLevel(Number.MIN_VALUE, "ALL");
    PhonoLogLevel.TRACE = new PhonoLogLevel(10000, "TRACE");
    PhonoLogLevel.DEBUG = new PhonoLogLevel(20000, "DEBUG");
    PhonoLogLevel.INFO = new PhonoLogLevel(30000, "INFO");
    PhonoLogLevel.WARN = new PhonoLogLevel(40000, "WARN");
    PhonoLogLevel.ERROR = new PhonoLogLevel(50000, "ERROR");
    PhonoLogLevel.FATAL = new PhonoLogLevel(60000, "FATAL");
    PhonoLogLevel.OFF = new PhonoLogLevel(Number.MAX_VALUE, "OFF");

    // Logger
    // ====================================================================================

    PhonoLogger.prototype.log = function(level, params) {

        var exception;
        var finalParamIndex = params.length - 1;
        var lastParam = params[params.length - 1];
        if (params.length > 1 && isError(lastParam)) {
            exception = lastParam;
            finalParamIndex--;
        }

        var messages = [];
        for (var i = 0; i <= finalParamIndex; i++) {
            messages[i] = params[i];
        }

        var loggingEvent = new PhonoLogEvent(new Date(), level , messages, exception);
        this.eventQueue.push(loggingEvent);

        this.flushEventQueue();
        
    };
    
    PhonoLogger.prototype.flushEventQueue = function() {
        if(this.initialized) {
            var logger = this;
            Phono.util.each(this.eventQueue, function(idx, event) {
                Phono.events.trigger(logger, "log", event);
            });
            this.eventQueue = [];
        }
    };

    PhonoLogger.prototype.debug = function() {
        this.log(PhonoLogLevel.DEBUG, arguments);
    };

    PhonoLogger.prototype.info = function() {
        this.log(PhonoLogLevel.INFO, arguments);
    };

    PhonoLogger.prototype.warn = function() {
        this.log(PhonoLogLevel.WARN, arguments);
    };

    PhonoLogger.prototype.error = function() {
        this.log(PhonoLogLevel.ERROR, arguments);
    };

    // Util
    // ====================================================================================

    function getExceptionMessage(ex) {
        if (ex.message) {
            return ex.message;
        } else if (ex.description) {
            return ex.description;
        } else {
            return toStr(ex);
        }
    };

    // Gets the portion of the URL after the last slash
    function getUrlFileName(url) {
        var lastSlashIndex = Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\"));
        return url.substr(lastSlashIndex + 1);
    };

    // Returns a nicely formatted representation of an error
    function getExceptionStringRep(ex) {
        if (ex) {
            var exStr = "Exception: " + getExceptionMessage(ex);
            try {
                if (ex.lineNumber) {
                    exStr += " on line number " + ex.lineNumber;
                }
                if (ex.fileName) {
                    exStr += " in file " + getUrlFileName(ex.fileName);
                }
            } catch (localEx) {
            }
            if (showStackTraces && ex.stack) {
                exStr += newLine + "Stack trace:" + newLine + ex.stack;
            }
            return exStr;
        }
        return null;
    };

    function isError(err) {
        return (err instanceof Error);
    };

    function bool(obj) {
        return Boolean(obj);
    };
    
})();


   
    // ======================================================================

   
   // Global
   Phono.version = "0.4";
   
   Phono.log = new PhonoLogger();
   
   Phono.registerPlugin = function(name, config) {
      if(!Phono.plugins) {
         Phono.plugins = {};
      }
      Phono.plugins[name] = config;
   };

   // ======================================================================

   Phono.prototype.connect = function() {

      // Noop if already connected
      if(this.connection.connected) return;

      var phono = this;

      this.connection.connect(phono.config.jid, phono.config.password, function (status) {
         if (status === Strophe.Status.CONNECTED) {
            phono.connection.send($pres());
            phono.handleConnect();
         } else if (status === Strophe.Status.DISCONNECTED) {
            phono.handleDisconnect();
         } else if (status === Strophe.Status.ERROR 
                 || status === Strophe.Status.CONNFAIL 
                 || status === Strophe.Status.CONNFAIL 
                 || status === Strophe.Status.AUTHFAIL) {
            phono.handleError();
          }
      },50);
   };

   Phono.prototype.disconnect = function() {
      this.connection.disconnect();
   };

   Phono.prototype.connected = function() {
      return this.connection.connected;
   };

   // Fires when the underlying Strophe Connection is estabilshed
   Phono.prototype.handleConnect = function() {
      this.sessionId = Strophe.getBareJidFromJid(this.connection.jid);
    
      new PluginManager(this, this.config, function(plugins) {
         Phono.events.trigger(this, "ready");
      }).init();     
   };

   // Fires when the underlying Strophe Connection errors out
   Phono.prototype.handleError = function() {
      Phono.events.trigger(this, "error", {
         reason: "Error connecting to XMPP server"
      });
   };

   // Fires when the underlying Strophe Connection disconnects
   Phono.prototype.handleDisconnect = function() {
      Phono.events.trigger(this, "unready");
   };

   // ======================================================================

/*	flXHR 1.0.5 <http://flxhr.flensed.com/> | Copyright (c) 2008-2010 Kyle Simpson, Getify Solutions, Inc. | This software is released under the MIT License <http://www.opensource.org/licenses/mit-license.php> */
(function(c){var E=c,h=c.document,z="undefined",a=true,L=false,g="",o="object",k="function",N="string",l="div",e="onunload",H=null,y=null,K=null,q=null,x=0,i=[],m=null,r=null,G="flXHR.js",n="flensed.js",P="flXHR.vbs",j="checkplayer.js",A="flXHR.swf",u=c.parseInt,w=c.setTimeout,f=c.clearTimeout,s=c.setInterval,v=c.clearInterval,O="instanceId",J="readyState",D="onreadystatechange",M="ontimeout",C="onerror",d="binaryResponseBody",F="xmlResponseText",I="loadPolicyURL",b="noCacheHeader",p="sendTimeout",B="appendToId",t="swfIdPrefix";if(typeof c.flensed===z){c.flensed={}}if(typeof c.flensed.flXHR!==z){return}y=c.flensed;w(function(){var Q=L,ab=h.getElementsByTagName("script"),V=ab.length;try{y.base_path.toLowerCase();Q=a}catch(T){y.base_path=g}function Z(ai,ah,aj){for(var ag=0;ag<V;ag++){if(typeof ab[ag].src!==z){if(ab[ag].src.indexOf(ai)>=0){break}}}var af=h.createElement("script");af.setAttribute("src",y.base_path+ai);if(typeof ah!==z){af.setAttribute("type",ah)}if(typeof aj!==z){af.setAttribute("language",aj)}h.getElementsByTagName("head")[0].appendChild(af)}if((typeof ab!==z)&&(ab!==null)){if(!Q){var ac=0;for(var U=0;U<V;U++){if(typeof ab[U].src!==z){if(((ac=ab[U].src.indexOf(n))>=0)||((ac=ab[U].src.indexOf(G))>=0)){y.base_path=ab[U].src.substr(0,ac);break}}}}}try{y.checkplayer.module_ready()}catch(aa){Z(j,"text/javascript")}var ad=null;(function ae(){try{y.ua.pv.join(".")}catch(af){ad=w(arguments.callee,25);return}if(y.ua.win&&y.ua.ie){Z(P,"text/vbscript","vbscript")}y.binaryToString=function(aj,ai){ai=(((y.ua.win&&y.ua.ie)&&typeof ai!==z)?(!(!ai)):!(y.ua.win&&y.ua.ie));if(!ai){try{return flXHR_vb_BinaryToString(aj)}catch(al){}}var am=g,ah=[];try{for(var ak=0;ak<aj.length;ak++){ah[ah.length]=String.fromCharCode(aj[ak])}am=ah.join(g)}catch(ag){}return am};y.bindEvent(E,e,function(){try{c.flensed.unbindEvent(E,e,arguments.callee);for(var ai in r){if(r[ai]!==Object.prototype[ai]){try{r[ai]=null}catch(ah){}}}y.flXHR=null;r=null;y=null;q=null;K=null}catch(ag){}})})();function Y(){f(ad);try{E.detachEvent(e,Y)}catch(af){}}if(ad!==null){try{E.attachEvent(e,Y)}catch(X){}}var S=null;function R(){f(S);try{E.detachEvent(e,R)}catch(af){}}try{E.attachEvent(e,R)}catch(W){}S=w(function(){R();try{y.checkplayer.module_ready()}catch(af){throw new c.Error("flXHR dependencies failed to load.")}},20000)},0);y.flXHR=function(aR){var ab=L;if(aR!==null&&typeof aR===o){if(typeof aR.instancePooling!==z){ab=!(!aR.instancePooling);if(ab){var aG=function(){for(var a0=0;a0<i.length;a0++){var a1=i[a0];if(a1[J]===4){a1.Reset();a1.Configure(aR);return a1}}return null}();if(aG!==null){return aG}}}}var aW=++x,ai=[],af=null,ah=null,X=null,Y=null,aM=-1,aF=0,aa=null,ac=null,ao=null,aE=null,aw=null,aV=null,ak=null,Q=null,aL=null,Z=a,aB=L,aY="flXHR_"+aW,au=a,aC=L,aA=a,aJ=L,S="flXHR_swf",ae="flXHRhideSwf",V=null,aH=-1,T=g,aK=null,aD=null,aO=null;var U=function(){if(typeof aR===o&&aR!==null){if((typeof aR[O]!==z)&&(aR[O]!==null)&&(aR[O]!==g)){aY=aR[O]}if((typeof aR[t]!==z)&&(aR[t]!==null)&&(aR[t]!==g)){S=aR[t]}if((typeof aR[B]!==z)&&(aR[B]!==null)&&(aR[B]!==g)){V=aR[B]}if((typeof aR[I]!==z)&&(aR[I]!==null)&&(aR[I]!==g)){T=aR[I]}if(typeof aR[b]!==z){au=!(!aR[b])}if(typeof aR[d]!==z){aC=!(!aR[d])}if(typeof aR[F]!==z){aA=!(!aR[F])}if(typeof aR.autoUpdatePlayer!==z){aJ=!(!aR.autoUpdatePlayer)}if((typeof aR[p]!==z)&&((H=u(aR[p],10))>0)){aH=H}if((typeof aR[D]!==z)&&(aR[D]!==null)){aK=aR[D]}if((typeof aR[C]!==z)&&(aR[C]!==null)){aD=aR[C]}if((typeof aR[M]!==z)&&(aR[M]!==null)){aO=aR[M]}}Y=S+"_"+aW;function a0(){f(af);try{E.detachEvent(e,a0)}catch(a3){}}try{E.attachEvent(e,a0)}catch(a1){}(function a2(){try{y.bindEvent(E,e,aI)}catch(a3){af=w(arguments.callee,25);return}a0();af=w(aT,1)})()}();function aT(){if(V===null){Q=h.getElementsByTagName("body")[0]}else{Q=y.getObjectById(V)}try{Q.nodeName.toLowerCase();y.checkplayer.module_ready();K=y.checkplayer}catch(a1){af=w(aT,25);return}if((q===null)&&(typeof K._ins===z)){try{q=new K(r.MIN_PLAYER_VERSION,aU,L,aq)}catch(a0){aP(r.DEPENDENCY_ERROR,"flXHR: checkplayer Init Failed","The initialization of the 'checkplayer' library failed to complete.");return}}else{q=K._ins;ag()}}function ag(){if(q===null||!q.checkPassed){af=w(ag,25);return}if(m===null&&V===null){y.createCSS("."+ae,"left:-1px;top:0px;width:1px;height:1px;position:absolute;");m=a}var a4=h.createElement(l);a4.id=Y;a4.className=ae;Q.appendChild(a4);Q=null;var a1={},a5={allowScriptAccess:"always"},a2={id:Y,name:Y,styleclass:ae},a3={swfCB:aS,swfEICheck:"reset"};try{q.DoSWF(y.base_path+A,Y,"1","1",a1,a5,a2,a3)}catch(a0){aP(r.DEPENDENCY_ERROR,"flXHR: checkplayer Call Failed","A call to the 'checkplayer' library failed to complete.");return}}function aS(a0){if(a0.status!==K.SWF_EI_READY){return}R();aV=y.getObjectById(Y);aV.setId(Y);if(T!==g){aV.loadPolicy(T)}aV.autoNoCacheHeader(au);aV.returnBinaryResponseBody(aC);aV.doOnReadyStateChange=al;aV.doOnError=aP;aV.sendProcessed=ap;aV.chunkResponse=ay;aM=0;ax();aX();if(typeof aK===k){try{aK(ak)}catch(a1){aP(r.HANDLER_ERROR,"flXHR::onreadystatechange(): Error","An error occurred in the handler function. ("+a1.message+")");return}}at()}function aI(){try{c.flensed.unbindEvent(E,e,aI)}catch(a3){}try{for(var a4=0;a4<i.length;a4++){if(i[a4]===ak){i[a4]=L}}}catch(bb){}try{for(var a6 in ak){if(ak[a6]!==Object.prototype[a6]){try{ak[a6]=null}catch(ba){}}}}catch(a9){}ak=null;R();if((typeof aV!==z)&&(aV!==null)){try{aV.abort()}catch(a8){}try{aV.doOnReadyStateChange=null;al=null}catch(a7){}try{aV.doOnError=null;doOnError=null}catch(a5){}try{aV.sendProcessed=null;ap=null}catch(a2){}try{aV.chunkResponse=null;ay=null}catch(a1){}aV=null;try{c.swfobject.removeSWF(Y)}catch(a0){}}aQ();aK=null;aD=null;aO=null;ao=null;aa=null;aL=null;Q=null}function ay(){if(aC&&typeof arguments[0]!==z){aL=((aL!==null)?aL:[]);aL=aL.concat(arguments[0])}else{if(typeof arguments[0]===N){aL=((aL!==null)?aL:g);aL+=arguments[0]}}}function al(){if(typeof arguments[0]!==z){aM=arguments[0]}if(aM===4){R();if(aC&&aL!==null){try{ac=y.binaryToString(aL,a);try{aa=flXHR_vb_StringToBinary(ac)}catch(a2){aa=aL}}catch(a1){}}else{ac=aL}aL=null;if(ac!==g){if(aA){try{ao=y.parseXMLString(ac)}catch(a0){ao={}}}}}if(typeof arguments[1]!==z){aE=arguments[1]}if(typeof arguments[2]!==z){aw=arguments[2]}ad(aM)}function ad(a0){aF=a0;ax();aX();ak[J]=Math.max(0,a0);if(typeof aK===k){try{aK(ak)}catch(a1){aP(r.HANDLER_ERROR,"flXHR::onreadystatechange(): Error","An error occurred in the handler function. ("+a1.message+")");return}}}function aP(){R();aQ();aB=a;var a3;try{a3=new y.error(arguments[0],arguments[1],arguments[2],ak)}catch(a4){function a1(){this.number=0;this.name="flXHR Error: Unknown";this.description="Unknown error from 'flXHR' library.";this.message=this.description;this.srcElement=ak;var a8=this.number,a7=this.name,ba=this.description;function a9(){return a8+", "+a7+", "+ba}this.toString=a9}a3=new a1()}var a5=L;try{if(typeof aD===k){aD(a3);a5=a}}catch(a0){var a2=a3.toString();function a6(){this.number=r.HANDLER_ERROR;this.name="flXHR::onerror(): Error";this.description="An error occured in the handler function. ("+a0.message+")\nPrevious:["+a2+"]";this.message=this.description;this.srcElement=ak;var a8=this.number,a7=this.name,ba=this.description;function a9(){return a8+", "+a7+", "+ba}this.toString=a9}a3=new a6()}if(!a5){w(function(){y.throwUnhandledError(a3.toString())},1)}}function W(){am();aB=a;if(typeof aO===k){try{aO(ak)}catch(a0){aP(r.HANDLER_ERROR,"flXHR::ontimeout(): Error","An error occurred in the handler function. ("+a0.message+")");return}}else{aP(r.TIMEOUT_ERROR,"flXHR: Operation Timed out","The requested operation timed out.")}}function R(){f(af);af=null;f(X);X=null;f(ah);ah=null}function aZ(a1,a2,a0){ai[ai.length]={func:a1,funcName:a2,args:a0};Z=L}function aQ(){if(!Z){Z=a;var a1=ai.length;for(var a0=0;a0<a1;a0++){try{ai[a0]=L}catch(a2){}}ai=[]}}function at(){if(aM<0){ah=w(at,25);return}if(!Z){for(var a0=0;a0<ai.length;a0++){try{if(ai[a0]!==L){ai[a0].func.apply(ak,ai[a0].args);ai[a0]=L}}catch(a1){aP(r.HANDLER_ERROR,"flXHR::"+ai[a0].funcName+"(): Error","An error occurred in the "+ai[a0].funcName+"() function.");return}}Z=a}}function aX(){try{ak[O]=aY;ak[J]=aF;ak.status=aE;ak.statusText=aw;ak.responseText=ac;ak.responseXML=ao;ak.responseBody=aa;ak[D]=aK;ak[C]=aD;ak[M]=aO;ak[I]=T;ak[b]=au;ak[d]=aC;ak[F]=aA}catch(a0){}}function ax(){try{aY=ak[O];if(ak.timeout!==null&&(H=u(ak.timeout,10))>0){aH=H}aK=ak[D];aD=ak[C];aO=ak[M];if(ak[I]!==null){if((ak[I]!==T)&&(aM>=0)){aV.loadPolicy(ak[I])}T=ak[I]}if(ak[b]!==null){if((ak[b]!==au)&&(aM>=0)){aV.autoNoCacheHeader(ak[b])}au=ak[b]}if(ak[d]!==null){if((ak[d]!==aC)&&(aM>=0)){aV.returnBinaryResponseBody(ak[d])}aC=ak[d]}if(aA!==null){aA=!(!ak[F])}}catch(a0){}}function aN(){am();try{aV.reset()}catch(a0){}aE=null;aw=null;ac=null;ao=null;aa=null;aL=null;aB=L;aX();T=g;ax()}function aU(a0){if(a0.checkPassed){ag()}else{if(!aJ){aP(r.PLAYER_VERSION_ERROR,"flXHR: Insufficient Flash Player Version","The Flash Player was either not detected, or the detected version ("+a0.playerVersionDetected+") was not at least the minimum version ("+r.MIN_PLAYER_VERSION+") needed by the 'flXHR' library.")}else{q.UpdatePlayer()}}}function aq(a0){if(a0.updateStatus===K.UPDATE_CANCELED){aP(r.PLAYER_VERSION_ERROR,"flXHR: Flash Player Update Canceled","The Flash Player was not updated.")}else{if(a0.updateStatus===K.UPDATE_FAILED){aP(r.PLAYER_VERSION_ERROR,"flXHR: Flash Player Update Failed","The Flash Player was either not detected or could not be updated.")}}}function ap(){if(aH!==null&&aH>0){X=w(W,aH)}}function am(){R();aQ();ax();aM=0;aF=0;try{aV.abort()}catch(a0){aP(r.CALL_ERROR,"flXHR::abort(): Failed","The abort() call failed to complete.")}aX()}function av(){ax();if(typeof arguments[0]===z||typeof arguments[1]===z){aP(r.CALL_ERROR,"flXHR::open(): Failed","The open() call requires 'method' and 'url' parameters.")}else{if(aM>0||aB){aN()}if(aF===0){al(1)}else{aM=1}var a7=arguments[0],a6=arguments[1],a5=(typeof arguments[2]!==z)?arguments[2]:a,ba=(typeof arguments[3]!==z)?arguments[3]:g,a9=(typeof arguments[4]!==z)?arguments[4]:g;try{aV.autoNoCacheHeader(au);aV.open(a7,a6,a5,ba,a9)}catch(a8){aP(r.CALL_ERROR,"flXHR::open(): Failed","The open() call failed to complete.")}}}function az(){ax();if(aM<=1&&!aB){var a1=(typeof arguments[0]!==z)?arguments[0]:g;if(aF===1){al(2)}else{aM=2}try{aV.autoNoCacheHeader(au);aV.send(a1)}catch(a2){aP(r.CALL_ERROR,"flXHR::send(): Failed","The send() call failed to complete.")}}else{aP(r.CALL_ERROR,"flXHR::send(): Failed","The send() call cannot be made at this time.")}}function aj(){ax();if(typeof arguments[0]===z||typeof arguments[1]===z){aP(r.CALL_ERROR,"flXHR::setRequestHeader(): Failed","The setRequestHeader() call requires 'name' and 'value' parameters.")}else{if(!aB){var a3=(typeof arguments[0]!==z)?arguments[0]:g,a2=(typeof arguments[1]!==z)?arguments[1]:g;try{aV.setRequestHeader(a3,a2)}catch(a4){aP(r.CALL_ERROR,"flXHR::setRequestHeader(): Failed","The setRequestHeader() call failed to complete.")}}}}function an(){ax();return g}function ar(){ax();return[]}ak={readyState:aF,responseBody:aa,responseText:ac,responseXML:ao,status:aE,statusText:aw,timeout:aH,open:function(){ax();if(ak[J]===0){ad(1)}if(!Z||aM<0){aZ(av,"open",arguments);return}av.apply({},arguments)},send:function(){ax();if(ak[J]===1){ad(2)}if(!Z||aM<0){aZ(az,"send",arguments);return}az.apply({},arguments)},abort:am,setRequestHeader:function(){ax();if(!Z||aM<0){aZ(aj,"setRequestHeader",arguments);return}aj.apply({},arguments)},getResponseHeader:an,getAllResponseHeaders:ar,onreadystatechange:aK,ontimeout:aO,instanceId:aY,loadPolicyURL:T,noCacheHeader:au,binaryResponseBody:aC,xmlResponseText:aA,onerror:aD,Configure:function(a0){if(typeof a0===o&&a0!==null){if((typeof a0[O]!==z)&&(a0[O]!==null)&&(a0[O]!==g)){aY=a0[O]}if(typeof a0[b]!==z){au=!(!a0[b]);if(aM>=0){aV.autoNoCacheHeader(au)}}if(typeof a0[d]!==z){aC=!(!a0[d]);if(aM>=0){aV.returnBinaryResponseBody(aC)}}if(typeof a0[F]!==z){aA=!(!a0[F])}if((typeof a0[D]!==z)&&(a0[D]!==null)){aK=a0[D]}if((typeof a0[C]!==z)&&(a0[C]!==null)){aD=a0[C]}if((typeof a0[M]!==z)&&(a0[M]!==null)){aO=a0[M]}if((typeof a0[p]!==z)&&((H=u(a0[p],10))>0)){aH=H}if((typeof a0[I]!==z)&&(a0[I]!==null)&&(a0[I]!==g)&&(a0[I]!==T)){T=a0[I];if(aM>=0){aV.loadPolicy(T)}}aX()}},Reset:aN,Destroy:aI};if(ab){i[i.length]=ak}return ak};r=y.flXHR;r.HANDLER_ERROR=10;r.CALL_ERROR=11;r.TIMEOUT_ERROR=12;r.DEPENDENCY_ERROR=13;r.PLAYER_VERSION_ERROR=14;r.SECURITY_ERROR=15;r.COMMUNICATION_ERROR=16;r.MIN_PLAYER_VERSION="9.0.124";r.module_ready=function(){}})(window);
// This code was written by Tyler Akins and has been placed in the
// public domain.  It would be nice if you left this header intact.
// Base64 code from Tyler Akins -- http://rumkin.com

var Base64 = (function () {
    var keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    var obj = {
        /**
         * Encodes a string in base64
         * @param {String} input The string to encode in base64.
         */
        encode: function (input) {
            var output = "";
            var chr1, chr2, chr3;
            var enc1, enc2, enc3, enc4;
            var i = 0;
        
            do {
                chr1 = input.charCodeAt(i++);
                chr2 = input.charCodeAt(i++);
                chr3 = input.charCodeAt(i++);
                
                enc1 = chr1 >> 2;
                enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
                enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
                enc4 = chr3 & 63;

                if (isNaN(chr2)) {
                    enc3 = enc4 = 64;
                } else if (isNaN(chr3)) {
                    enc4 = 64;
                }
                
                output = output + keyStr.charAt(enc1) + keyStr.charAt(enc2) +
                    keyStr.charAt(enc3) + keyStr.charAt(enc4);
            } while (i < input.length);
            
            return output;
        },
        
        /**
         * Decodes a base64 string.
         * @param {String} input The string to decode.
         */
        decode: function (input) {
            var output = "";
            var chr1, chr2, chr3;
            var enc1, enc2, enc3, enc4;
            var i = 0;
            
            // remove all characters that are not A-Z, a-z, 0-9, +, /, or =
            input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");
            
            do {
                enc1 = keyStr.indexOf(input.charAt(i++));
                enc2 = keyStr.indexOf(input.charAt(i++));
                enc3 = keyStr.indexOf(input.charAt(i++));
                enc4 = keyStr.indexOf(input.charAt(i++));
                
                chr1 = (enc1 << 2) | (enc2 >> 4);
                chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
                chr3 = ((enc3 & 3) << 6) | enc4;
                
                output = output + String.fromCharCode(chr1);
                
                if (enc3 != 64) {
                    output = output + String.fromCharCode(chr2);
                }
                if (enc4 != 64) {
                    output = output + String.fromCharCode(chr3);
                }
            } while (i < input.length);
            
            return output;
        }
    };

    return obj;
})();
/*
 * A JavaScript implementation of the RSA Data Security, Inc. MD5 Message
 * Digest Algorithm, as defined in RFC 1321.
 * Version 2.1 Copyright (C) Paul Johnston 1999 - 2002.
 * Other contributors: Greg Holt, Andrew Kepert, Ydnar, Lostinet
 * Distributed under the BSD License
 * See http://pajhome.org.uk/crypt/md5 for more info.
 */

var MD5 = (function () {
    /*
     * Configurable variables. You may need to tweak these to be compatible with
     * the server-side, but the defaults work in most cases.
     */
    var hexcase = 0;  /* hex output format. 0 - lowercase; 1 - uppercase */
    var b64pad  = ""; /* base-64 pad character. "=" for strict RFC compliance */
    var chrsz   = 8;  /* bits per input character. 8 - ASCII; 16 - Unicode */

    /*
     * Add integers, wrapping at 2^32. This uses 16-bit operations internally
     * to work around bugs in some JS interpreters.
     */
    var safe_add = function (x, y) {
        var lsw = (x & 0xFFFF) + (y & 0xFFFF);
        var msw = (x >> 16) + (y >> 16) + (lsw >> 16);
        return (msw << 16) | (lsw & 0xFFFF);
    };

    /*
     * Bitwise rotate a 32-bit number to the left.
     */
    var bit_rol = function (num, cnt) {
        return (num << cnt) | (num >>> (32 - cnt));
    };

    /*
     * Convert a string to an array of little-endian words
     * If chrsz is ASCII, characters >255 have their hi-byte silently ignored.
     */
    var str2binl = function (str) {
        var bin = [];
        var mask = (1 << chrsz) - 1;
        for(var i = 0; i < str.length * chrsz; i += chrsz)
        {
            bin[i>>5] |= (str.charCodeAt(i / chrsz) & mask) << (i%32);
        }
        return bin;
    };

    /*
     * Convert an array of little-endian words to a string
     */
    var binl2str = function (bin) {
        var str = "";
        var mask = (1 << chrsz) - 1;
        for(var i = 0; i < bin.length * 32; i += chrsz)
        {
            str += String.fromCharCode((bin[i>>5] >>> (i % 32)) & mask);
        }
        return str;
    };

    /*
     * Convert an array of little-endian words to a hex string.
     */
    var binl2hex = function (binarray) {
        var hex_tab = hexcase ? "0123456789ABCDEF" : "0123456789abcdef";
        var str = "";
        for(var i = 0; i < binarray.length * 4; i++)
        {
            str += hex_tab.charAt((binarray[i>>2] >> ((i%4)*8+4)) & 0xF) +
                hex_tab.charAt((binarray[i>>2] >> ((i%4)*8  )) & 0xF);
        }
        return str;
    };

    /*
     * Convert an array of little-endian words to a base-64 string
     */
    var binl2b64 = function (binarray) {
        var tab = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        var str = "";
        var triplet, j;
        for(var i = 0; i < binarray.length * 4; i += 3)
        {
            triplet = (((binarray[i   >> 2] >> 8 * ( i   %4)) & 0xFF) << 16) |
                (((binarray[i+1 >> 2] >> 8 * ((i+1)%4)) & 0xFF) << 8 ) |
                ((binarray[i+2 >> 2] >> 8 * ((i+2)%4)) & 0xFF);
            for(j = 0; j < 4; j++)
            {
                if(i * 8 + j * 6 > binarray.length * 32) { str += b64pad; }
                else { str += tab.charAt((triplet >> 6*(3-j)) & 0x3F); }
            }
        }
        return str;
    };

    /*
     * These functions implement the four basic operations the algorithm uses.
     */
    var md5_cmn = function (q, a, b, x, s, t) {
        return safe_add(bit_rol(safe_add(safe_add(a, q),safe_add(x, t)), s),b);
    };

    var md5_ff = function (a, b, c, d, x, s, t) {
        return md5_cmn((b & c) | ((~b) & d), a, b, x, s, t);
    };

    var md5_gg = function (a, b, c, d, x, s, t) {
        return md5_cmn((b & d) | (c & (~d)), a, b, x, s, t);
    };

    var md5_hh = function (a, b, c, d, x, s, t) {
        return md5_cmn(b ^ c ^ d, a, b, x, s, t);
    };

    var md5_ii = function (a, b, c, d, x, s, t) {
        return md5_cmn(c ^ (b | (~d)), a, b, x, s, t);
    };
    
    /*
     * Calculate the MD5 of an array of little-endian words, and a bit length
     */
    var core_md5 = function (x, len) {
        /* append padding */
        x[len >> 5] |= 0x80 << ((len) % 32);
        x[(((len + 64) >>> 9) << 4) + 14] = len;

        var a =  1732584193;
        var b = -271733879;
        var c = -1732584194;
        var d =  271733878;

        var olda, oldb, oldc, oldd;
        for (var i = 0; i < x.length; i += 16)
        {
            olda = a;
            oldb = b;
            oldc = c;
            oldd = d;
            
            a = md5_ff(a, b, c, d, x[i+ 0], 7 , -680876936);
            d = md5_ff(d, a, b, c, x[i+ 1], 12, -389564586);
            c = md5_ff(c, d, a, b, x[i+ 2], 17,  606105819);
            b = md5_ff(b, c, d, a, x[i+ 3], 22, -1044525330);
            a = md5_ff(a, b, c, d, x[i+ 4], 7 , -176418897);
            d = md5_ff(d, a, b, c, x[i+ 5], 12,  1200080426);
            c = md5_ff(c, d, a, b, x[i+ 6], 17, -1473231341);
            b = md5_ff(b, c, d, a, x[i+ 7], 22, -45705983);
            a = md5_ff(a, b, c, d, x[i+ 8], 7 ,  1770035416);
            d = md5_ff(d, a, b, c, x[i+ 9], 12, -1958414417);
            c = md5_ff(c, d, a, b, x[i+10], 17, -42063);
            b = md5_ff(b, c, d, a, x[i+11], 22, -1990404162);
            a = md5_ff(a, b, c, d, x[i+12], 7 ,  1804603682);
            d = md5_ff(d, a, b, c, x[i+13], 12, -40341101);
            c = md5_ff(c, d, a, b, x[i+14], 17, -1502002290);
            b = md5_ff(b, c, d, a, x[i+15], 22,  1236535329);
            
            a = md5_gg(a, b, c, d, x[i+ 1], 5 , -165796510);
            d = md5_gg(d, a, b, c, x[i+ 6], 9 , -1069501632);
            c = md5_gg(c, d, a, b, x[i+11], 14,  643717713);
            b = md5_gg(b, c, d, a, x[i+ 0], 20, -373897302);
            a = md5_gg(a, b, c, d, x[i+ 5], 5 , -701558691);
            d = md5_gg(d, a, b, c, x[i+10], 9 ,  38016083);
            c = md5_gg(c, d, a, b, x[i+15], 14, -660478335);
            b = md5_gg(b, c, d, a, x[i+ 4], 20, -405537848);
            a = md5_gg(a, b, c, d, x[i+ 9], 5 ,  568446438);
            d = md5_gg(d, a, b, c, x[i+14], 9 , -1019803690);
            c = md5_gg(c, d, a, b, x[i+ 3], 14, -187363961);
            b = md5_gg(b, c, d, a, x[i+ 8], 20,  1163531501);
            a = md5_gg(a, b, c, d, x[i+13], 5 , -1444681467);
            d = md5_gg(d, a, b, c, x[i+ 2], 9 , -51403784);
            c = md5_gg(c, d, a, b, x[i+ 7], 14,  1735328473);
            b = md5_gg(b, c, d, a, x[i+12], 20, -1926607734);
            
            a = md5_hh(a, b, c, d, x[i+ 5], 4 , -378558);
            d = md5_hh(d, a, b, c, x[i+ 8], 11, -2022574463);
            c = md5_hh(c, d, a, b, x[i+11], 16,  1839030562);
            b = md5_hh(b, c, d, a, x[i+14], 23, -35309556);
            a = md5_hh(a, b, c, d, x[i+ 1], 4 , -1530992060);
            d = md5_hh(d, a, b, c, x[i+ 4], 11,  1272893353);
            c = md5_hh(c, d, a, b, x[i+ 7], 16, -155497632);
            b = md5_hh(b, c, d, a, x[i+10], 23, -1094730640);
            a = md5_hh(a, b, c, d, x[i+13], 4 ,  681279174);
            d = md5_hh(d, a, b, c, x[i+ 0], 11, -358537222);
            c = md5_hh(c, d, a, b, x[i+ 3], 16, -722521979);
            b = md5_hh(b, c, d, a, x[i+ 6], 23,  76029189);
            a = md5_hh(a, b, c, d, x[i+ 9], 4 , -640364487);
            d = md5_hh(d, a, b, c, x[i+12], 11, -421815835);
            c = md5_hh(c, d, a, b, x[i+15], 16,  530742520);
            b = md5_hh(b, c, d, a, x[i+ 2], 23, -995338651);
            
            a = md5_ii(a, b, c, d, x[i+ 0], 6 , -198630844);
            d = md5_ii(d, a, b, c, x[i+ 7], 10,  1126891415);
            c = md5_ii(c, d, a, b, x[i+14], 15, -1416354905);
            b = md5_ii(b, c, d, a, x[i+ 5], 21, -57434055);
            a = md5_ii(a, b, c, d, x[i+12], 6 ,  1700485571);
            d = md5_ii(d, a, b, c, x[i+ 3], 10, -1894986606);
            c = md5_ii(c, d, a, b, x[i+10], 15, -1051523);
            b = md5_ii(b, c, d, a, x[i+ 1], 21, -2054922799);
            a = md5_ii(a, b, c, d, x[i+ 8], 6 ,  1873313359);
            d = md5_ii(d, a, b, c, x[i+15], 10, -30611744);
            c = md5_ii(c, d, a, b, x[i+ 6], 15, -1560198380);
            b = md5_ii(b, c, d, a, x[i+13], 21,  1309151649);
            a = md5_ii(a, b, c, d, x[i+ 4], 6 , -145523070);
            d = md5_ii(d, a, b, c, x[i+11], 10, -1120210379);
            c = md5_ii(c, d, a, b, x[i+ 2], 15,  718787259);
            b = md5_ii(b, c, d, a, x[i+ 9], 21, -343485551);
            
            a = safe_add(a, olda);
            b = safe_add(b, oldb);
            c = safe_add(c, oldc);
            d = safe_add(d, oldd);
        }
        return [a, b, c, d];
    };


    /*
     * Calculate the HMAC-MD5, of a key and some data
     */
    var core_hmac_md5 = function (key, data) {
        var bkey = str2binl(key);
        if(bkey.length > 16) { bkey = core_md5(bkey, key.length * chrsz); }
        
        var ipad = new Array(16), opad = new Array(16);
        for(var i = 0; i < 16; i++)
        {
            ipad[i] = bkey[i] ^ 0x36363636;
            opad[i] = bkey[i] ^ 0x5C5C5C5C;
        }
        
        var hash = core_md5(ipad.concat(str2binl(data)), 512 + data.length * chrsz);
        return core_md5(opad.concat(hash), 512 + 128);
    };

    var obj = {
        /*
         * These are the functions you'll usually want to call.
         * They take string arguments and return either hex or base-64 encoded
         * strings.
         */
        hexdigest: function (s) {
            return binl2hex(core_md5(str2binl(s), s.length * chrsz));
        },

        b64digest: function (s) {
            return binl2b64(core_md5(str2binl(s), s.length * chrsz));
        },

        hash: function (s) {
            return binl2str(core_md5(str2binl(s), s.length * chrsz));
        },

        hmac_hexdigest: function (key, data) {
            return binl2hex(core_hmac_md5(key, data));
        },

        hmac_b64digest: function (key, data) {
            return binl2b64(core_hmac_md5(key, data));
        },

        hmac_hash: function (key, data) {
            return binl2str(core_hmac_md5(key, data));
        },

        /*
         * Perform a simple self-test to see if the VM is working
         */
        test: function () {
            return MD5.hexdigest("abc") === "900150983cd24fb0d6963f7d28e17f72";
        }
    };

    return obj;
})();

/*
    This program is distributed under the terms of the MIT license.
    Please see the LICENSE file for details.

    Copyright 2006-2008, OGG, LLC
*/

/* jslint configuration: */
/*global document, window, setTimeout, clearTimeout, console,
    XMLHttpRequest, ActiveXObject,
    Base64, MD5,
    Strophe, $build, $msg, $iq, $pres */

/** File: strophe.js
 *  A JavaScript library for XMPP BOSH.
 *
 *  This is the JavaScript version of the Strophe library.  Since JavaScript
 *  has no facilities for persistent TCP connections, this library uses
 *  Bidirectional-streams Over Synchronous HTTP (BOSH) to emulate
 *  a persistent, stateful, two-way connection to an XMPP server.  More
 *  information on BOSH can be found in XEP 124.
 */

/** PrivateFunction: Function.prototype.bind
 *  Bind a function to an instance.
 *
 *  This Function object extension method creates a bound method similar
 *  to those in Python.  This means that the 'this' object will point
 *  to the instance you want.  See
 *  <a href='http://benjamin.smedbergs.us/blog/2007-01-03/bound-functions-and-function-imports-in-javascript/'>Bound Functions and Function Imports in JavaScript</a>
 *  for a complete explanation.
 *
 *  This extension already exists in some browsers (namely, Firefox 3), but
 *  we provide it to support those that don't.
 *
 *  Parameters:
 *    (Object) obj - The object that will become 'this' in the bound function.
 *
 *  Returns:
 *    The bound function.
 */
if (!Function.prototype.bind) {
    Function.prototype.bind = function (obj)
    {
        var func = this;
        return function () { return func.apply(obj, arguments); };
    };
}

/** PrivateFunction: Function.prototype.prependArg
 *  Prepend an argument to a function.
 *
 *  This Function object extension method returns a Function that will
 *  invoke the original function with an argument prepended.  This is useful
 *  when some object has a callback that needs to get that same object as
 *  an argument.  The following fragment illustrates a simple case of this
 *  > var obj = new Foo(this.someMethod);</code></blockquote>
 *
 *  Foo's constructor can now use func.prependArg(this) to ensure the
 *  passed in callback function gets the instance of Foo as an argument.
 *  Doing this without prependArg would mean not setting the callback
 *  from the constructor.
 *
 *  This is used inside Strophe for passing the Strophe.Request object to
 *  the onreadystatechange handler of XMLHttpRequests.
 *
 *  Parameters:
 *    arg - The argument to pass as the first parameter to the function.
 *
 *  Returns:
 *    A new Function which calls the original with the prepended argument.
 */
if (!Function.prototype.prependArg) {
    Function.prototype.prependArg = function (arg)
    {
        var func = this;

        return function () {
            var newargs = [arg];
            for (var i = 0; i < arguments.length; i++) {
                newargs.push(arguments[i]);
            }
            return func.apply(this, newargs);
        };
    };
}

/** PrivateFunction: Array.prototype.indexOf
 *  Return the index of an object in an array.
 *
 *  This function is not supplied by some JavaScript implementations, so
 *  we provide it if it is missing.  This code is from:
 *  http://developer.mozilla.org/En/Core_JavaScript_1.5_Reference:Objects:Array:indexOf
 *
 *  Parameters:
 *    (Object) elt - The object to look for.
 *    (Integer) from - The index from which to start looking. (optional).
 *
 *  Returns:
 *    The index of elt in the array or -1 if not found.
 */
if (!Array.prototype.indexOf)
{
    Array.prototype.indexOf = function(elt /*, from*/)
    {
        var len = this.length;

        var from = Number(arguments[1]) || 0;
        from = (from < 0) ? Math.ceil(from) : Math.floor(from);
        if (from < 0) {
            from += len;
        }

        for (; from < len; from++) {
            if (from in this && this[from] === elt) {
                return from;
            }
        }

        return -1;
    };
}

/* All of the Strophe globals are defined in this special function below so
 * that references to the globals become closures.  This will ensure that
 * on page reload, these references will still be available to callbacks
 * that are still executing.
 */

(function (callback) {
var Strophe;

/** Function: $build
 *  Create a Strophe.Builder.
 *  This is an alias for 'new Strophe.Builder(name, attrs)'.
 *
 *  Parameters:
 *    (String) name - The root element name.
 *    (Object) attrs - The attributes for the root element in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
function $build(name, attrs) { return new Strophe.Builder(name, attrs); }
/** Function: $msg
 *  Create a Strophe.Builder with a <message/> element as the root.
 *
 *  Parmaeters:
 *    (Object) attrs - The <message/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
function $msg(attrs) { return new Strophe.Builder("message", attrs); }
/** Function: $iq
 *  Create a Strophe.Builder with an <iq/> element as the root.
 *
 *  Parameters:
 *    (Object) attrs - The <iq/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
function $iq(attrs) { return new Strophe.Builder("iq", attrs); }
/** Function: $pres
 *  Create a Strophe.Builder with a <presence/> element as the root.
 *
 *  Parameters:
 *    (Object) attrs - The <presence/> element attributes in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder object.
 */
function $pres(attrs) { return new Strophe.Builder("presence", attrs); }

/** Class: Strophe
 *  An object container for all Strophe library functions.
 *
 *  This class is just a container for all the objects and constants
 *  used in the library.  It is not meant to be instantiated, but to
 *  provide a namespace for library objects, constants, and functions.
 */
Strophe = {
    /** Constant: VERSION
     *  The version of the Strophe library. Unreleased builds will have
     *  a version of head-HASH where HASH is a partial revision.
     */
    VERSION: "1.0.1",

    /** Constants: XMPP Namespace Constants
     *  Common namespace constants from the XMPP RFCs and XEPs.
     *
     *  NS.HTTPBIND - HTTP BIND namespace from XEP 124.
     *  NS.BOSH - BOSH namespace from XEP 206.
     *  NS.CLIENT - Main XMPP client namespace.
     *  NS.AUTH - Legacy authentication namespace.
     *  NS.ROSTER - Roster operations namespace.
     *  NS.PROFILE - Profile namespace.
     *  NS.DISCO_INFO - Service discovery info namespace from XEP 30.
     *  NS.DISCO_ITEMS - Service discovery items namespace from XEP 30.
     *  NS.MUC - Multi-User Chat namespace from XEP 45.
     *  NS.SASL - XMPP SASL namespace from RFC 3920.
     *  NS.STREAM - XMPP Streams namespace from RFC 3920.
     *  NS.BIND - XMPP Binding namespace from RFC 3920.
     *  NS.SESSION - XMPP Session namespace from RFC 3920.
     */
    NS: {
        HTTPBIND: "http://jabber.org/protocol/httpbind",
        BOSH: "urn:xmpp:xbosh",
        CLIENT: "jabber:client",
        AUTH: "jabber:iq:auth",
        ROSTER: "jabber:iq:roster",
        PROFILE: "jabber:iq:profile",
        DISCO_INFO: "http://jabber.org/protocol/disco#info",
        DISCO_ITEMS: "http://jabber.org/protocol/disco#items",
        MUC: "http://jabber.org/protocol/muc",
        SASL: "urn:ietf:params:xml:ns:xmpp-sasl",
        STREAM: "http://etherx.jabber.org/streams",
        BIND: "urn:ietf:params:xml:ns:xmpp-bind",
        SESSION: "urn:ietf:params:xml:ns:xmpp-session",
        VERSION: "jabber:iq:version",
        STANZAS: "urn:ietf:params:xml:ns:xmpp-stanzas"
    },

    /** Function: addNamespace 
     *  This function is used to extend the current namespaces in
     *	Strophe.NS.  It takes a key and a value with the key being the
     *	name of the new namespace, with its actual value.
     *	For example:
     *	Strophe.addNamespace('PUBSUB', "http://jabber.org/protocol/pubsub");
     *
     *  Parameters:
     *    (String) name - The name under which the namespace will be
     *      referenced under Strophe.NS
     *    (String) value - The actual namespace.	
     */
    addNamespace: function (name, value)
    {
	Strophe.NS[name] = value;
    },

    /** Constants: Connection Status Constants
     *  Connection status constants for use by the connection handler
     *  callback.
     *
     *  Status.ERROR - An error has occurred
     *  Status.CONNECTING - The connection is currently being made
     *  Status.CONNFAIL - The connection attempt failed
     *  Status.AUTHENTICATING - The connection is authenticating
     *  Status.AUTHFAIL - The authentication attempt failed
     *  Status.CONNECTED - The connection has succeeded
     *  Status.DISCONNECTED - The connection has been terminated
     *  Status.DISCONNECTING - The connection is currently being terminated
     *  Status.ATTACHED - The connection has been attached
     */
    Status: {
        ERROR: 0,
        CONNECTING: 1,
        CONNFAIL: 2,
        AUTHENTICATING: 3,
        AUTHFAIL: 4,
        CONNECTED: 5,
        DISCONNECTED: 6,
        DISCONNECTING: 7,
        ATTACHED: 8
    },

    /** Constants: Log Level Constants
     *  Logging level indicators.
     *
     *  LogLevel.DEBUG - Debug output
     *  LogLevel.INFO - Informational output
     *  LogLevel.WARN - Warnings
     *  LogLevel.ERROR - Errors
     *  LogLevel.FATAL - Fatal errors
     */
    LogLevel: {
        DEBUG: 0,
        INFO: 1,
        WARN: 2,
        ERROR: 3,
        FATAL: 4
    },

    /** PrivateConstants: DOM Element Type Constants
     *  DOM element types.
     *
     *  ElementType.NORMAL - Normal element.
     *  ElementType.TEXT - Text data element.
     */
    ElementType: {
        NORMAL: 1,
        TEXT: 3
    },

    /** PrivateConstants: Timeout Values
     *  Timeout values for error states.  These values are in seconds.
     *  These should not be changed unless you know exactly what you are
     *  doing.
     *
     *  TIMEOUT - Timeout multiplier. A waiting request will be considered
     *      failed after Math.floor(TIMEOUT * wait) seconds have elapsed.
     *      This defaults to 1.1, and with default wait, 66 seconds.
     *  SECONDARY_TIMEOUT - Secondary timeout multiplier. In cases where
     *      Strophe can detect early failure, it will consider the request
     *      failed if it doesn't return after
     *      Math.floor(SECONDARY_TIMEOUT * wait) seconds have elapsed.
     *      This defaults to 0.1, and with default wait, 6 seconds.
     */
    TIMEOUT: 1.1,
    SECONDARY_TIMEOUT: 0.1,

    /** Function: forEachChild
     *  Map a function over some or all child elements of a given element.
     *
     *  This is a small convenience function for mapping a function over
     *  some or all of the children of an element.  If elemName is null, all
     *  children will be passed to the function, otherwise only children
     *  whose tag names match elemName will be passed.
     *
     *  Parameters:
     *    (XMLElement) elem - The element to operate on.
     *    (String) elemName - The child element tag name filter.
     *    (Function) func - The function to apply to each child.  This
     *      function should take a single argument, a DOM element.
     */
    forEachChild: function (elem, elemName, func)
    {
        var i, childNode;

        for (i = 0; i < elem.childNodes.length; i++) {
            childNode = elem.childNodes[i];
            if (childNode.nodeType == Strophe.ElementType.NORMAL &&
                (!elemName || this.isTagEqual(childNode, elemName))) {
                func(childNode);
            }
        }
    },

    /** Function: isTagEqual
     *  Compare an element's tag name with a string.
     *
     *  This function is case insensitive.
     *
     *  Parameters:
     *    (XMLElement) el - A DOM element.
     *    (String) name - The element name.
     *
     *  Returns:
     *    true if the element's tag name matches _el_, and false
     *    otherwise.
     */
    isTagEqual: function (el, name)
    {
        return el.tagName.toLowerCase() == name.toLowerCase();
    },

    /** PrivateVariable: _xmlGenerator
     *  _Private_ variable that caches a DOM document to
     *  generate elements.
     */
    _xmlGenerator: null,

    /** PrivateFunction: _makeGenerator
     *  _Private_ function that creates a dummy XML DOM document to serve as
     *  an element and text node generator.
     */
    _makeGenerator: function () {
        var doc;

        if (window.ActiveXObject) {
            doc = new ActiveXObject("Microsoft.XMLDOM");
            doc.appendChild(doc.createElement('strophe'));
        } else {
            doc = document.implementation
                .createDocument('jabber:client', 'strophe', null);
        }

        return doc;
    },

    /** Function: xmlElement
     *  Create an XML DOM element.
     *
     *  This function creates an XML DOM element correctly across all
     *  implementations. Note that these are not HTML DOM elements, which
     *  aren't appropriate for XMPP stanzas.
     *
     *  Parameters:
     *    (String) name - The name for the element.
     *    (Array|Object) attrs - An optional array or object containing
     *      key/value pairs to use as element attributes. The object should
     *      be in the format {'key': 'value'} or {key: 'value'}. The array
     *      should have the format [['key1', 'value1'], ['key2', 'value2']].
     *    (String) text - The text child data for the element.
     *
     *  Returns:
     *    A new XML DOM element.
     */
    xmlElement: function (name)
    {
        if (!name) { return null; }

        var node = null;
        if (!Strophe._xmlGenerator) {
            Strophe._xmlGenerator = Strophe._makeGenerator();
        }
        node = Strophe._xmlGenerator.createElement(name);

        // FIXME: this should throw errors if args are the wrong type or
        // there are more than two optional args
        var a, i, k;
        for (a = 1; a < arguments.length; a++) {
            if (!arguments[a]) { continue; }
            if (typeof(arguments[a]) == "string" ||
                typeof(arguments[a]) == "number") {
                node.appendChild(Strophe.xmlTextNode(arguments[a]));
            } else if (typeof(arguments[a]) == "object" &&
                       typeof(arguments[a].sort) == "function") {
                for (i = 0; i < arguments[a].length; i++) {
                    if (typeof(arguments[a][i]) == "object" &&
                        typeof(arguments[a][i].sort) == "function") {
                        node.setAttribute(arguments[a][i][0],
                                          arguments[a][i][1]);
                    }
                }
            } else if (typeof(arguments[a]) == "object") {
                for (k in arguments[a]) {
                    if (arguments[a].hasOwnProperty(k)) {
                        node.setAttribute(k, arguments[a][k]);
                    }
                } 
            }
        }

        return node;
    },

    /*  Function: xmlescape
     *  Excapes invalid xml characters.
     *
     *  Parameters:
     *     (String) text - text to escape.
     *
     *	Returns:
     *      Escaped text.
     */
    xmlescape: function(text) 
    {
	text = text.replace(/\&/g, "&amp;");
        text = text.replace(/</g,  "&lt;");
        text = text.replace(/>/g,  "&gt;");
        return text;    
    },

    /** Function: xmlTextNode
     *  Creates an XML DOM text node.
     *
     *  Provides a cross implementation version of document.createTextNode.
     *
     *  Parameters:
     *    (String) text - The content of the text node.
     *
     *  Returns:
     *    A new XML DOM text node.
     */
    xmlTextNode: function (text)
    {
	//ensure text is escaped
	text = Strophe.xmlescape(text);

        if (!Strophe._xmlGenerator) {
            Strophe._xmlGenerator = Strophe._makeGenerator();
        }
        return Strophe._xmlGenerator.createTextNode(text);
    },

    /** Function: getText
     *  Get the concatenation of all text children of an element.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    A String with the concatenated text of all text element children.
     */
    getText: function (elem)
    {
        if (!elem) { return null; }

        var str = "";
        if (elem.childNodes.length === 0 && elem.nodeType ==
            Strophe.ElementType.TEXT) {
            str += elem.nodeValue;
        }

        for (var i = 0; i < elem.childNodes.length; i++) {
            if (elem.childNodes[i].nodeType == Strophe.ElementType.TEXT) {
                str += elem.childNodes[i].nodeValue;
            }
        }

        return str;
    },

    /** Function: copyElement
     *  Copy an XML DOM element.
     *
     *  This function copies a DOM element and all its descendants and returns
     *  the new copy.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    A new, copied DOM element tree.
     */
    copyElement: function (elem)
    {
        var i, el;
        if (elem.nodeType == Strophe.ElementType.NORMAL) {
            el = Strophe.xmlElement(elem.tagName);

            for (i = 0; i < elem.attributes.length; i++) {
                el.setAttribute(elem.attributes[i].nodeName.toLowerCase(),
                                elem.attributes[i].value);
            }

            for (i = 0; i < elem.childNodes.length; i++) {
                el.appendChild(Strophe.copyElement(elem.childNodes[i]));
            }
        } else if (elem.nodeType == Strophe.ElementType.TEXT) {
            el = Strophe.xmlTextNode(elem.nodeValue);
        }

        return el;
    },

    /** Function: escapeNode
     *  Escape the node part (also called local part) of a JID.
     *
     *  Parameters:
     *    (String) node - A node (or local part).
     *
     *  Returns:
     *    An escaped node (or local part).
     */
    escapeNode: function (node)
    {
        return node.replace(/^\s+|\s+$/g, '')
            .replace(/\\/g,  "\\5c")
            .replace(/ /g,   "\\20")
            .replace(/\"/g,  "\\22")
            .replace(/\&/g,  "\\26")
            .replace(/\'/g,  "\\27")
            .replace(/\//g,  "\\2f")
            .replace(/:/g,   "\\3a")
            .replace(/</g,   "\\3c")
            .replace(/>/g,   "\\3e")
            .replace(/@/g,   "\\40");
    },

    /** Function: unescapeNode
     *  Unescape a node part (also called local part) of a JID.
     *
     *  Parameters:
     *    (String) node - A node (or local part).
     *
     *  Returns:
     *    An unescaped node (or local part).
     */
    unescapeNode: function (node)
    {
        return node.replace(/\\20/g, " ")
            .replace(/\\22/g, '"')
            .replace(/\\26/g, "&")
            .replace(/\\27/g, "'")
            .replace(/\\2f/g, "/")
            .replace(/\\3a/g, ":")
            .replace(/\\3c/g, "<")
            .replace(/\\3e/g, ">")
            .replace(/\\40/g, "@")
            .replace(/\\5c/g, "\\");
    },

    /** Function: getNodeFromJid
     *  Get the node portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the node.
     */
    getNodeFromJid: function (jid)
    {
        if (jid.indexOf("@") < 0) { return null; }
        return jid.split("@")[0];
    },

    /** Function: getDomainFromJid
     *  Get the domain portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the domain.
     */
    getDomainFromJid: function (jid)
    {
        var bare = Strophe.getBareJidFromJid(jid);
        if (bare.indexOf("@") < 0) {
            return bare;
        } else {
            var parts = bare.split("@");
            parts.splice(0, 1);
            return parts.join('@');
        }
    },

    /** Function: getResourceFromJid
     *  Get the resource portion of a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the resource.
     */
    getResourceFromJid: function (jid)
    {
        var s = jid.split("/");
        if (s.length < 2) { return null; }
        s.splice(0, 1);
        return s.join('/');
    },

    /** Function: getBareJidFromJid
     *  Get the bare JID from a JID String.
     *
     *  Parameters:
     *    (String) jid - A JID.
     *
     *  Returns:
     *    A String containing the bare JID.
     */
    getBareJidFromJid: function (jid)
    {
        return jid.split("/")[0];
    },

    /** Function: log
     *  User overrideable logging function.
     *
     *  This function is called whenever the Strophe library calls any
     *  of the logging functions.  The default implementation of this
     *  function does nothing.  If client code wishes to handle the logging
     *  messages, it should override this with
     *  > Strophe.log = function (level, msg) {
     *  >   (user code here)
     *  > };
     *
     *  Please note that data sent and received over the wire is logged
     *  via Strophe.Connection.rawInput() and Strophe.Connection.rawOutput().
     *
     *  The different levels and their meanings are
     *
     *    DEBUG - Messages useful for debugging purposes.
     *    INFO - Informational messages.  This is mostly information like
     *      'disconnect was called' or 'SASL auth succeeded'.
     *    WARN - Warnings about potential problems.  This is mostly used
     *      to report transient connection errors like request timeouts.
     *    ERROR - Some error occurred.
     *    FATAL - A non-recoverable fatal error occurred.
     *
     *  Parameters:
     *    (Integer) level - The log level of the log message.  This will
     *      be one of the values in Strophe.LogLevel.
     *    (String) msg - The log message.
     */
    log: function (level, msg)
    {
        return;
    },

    /** Function: debug
     *  Log a message at the Strophe.LogLevel.DEBUG level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
    debug: function(msg)
    {
        this.log(this.LogLevel.DEBUG, msg);
    },

    /** Function: info
     *  Log a message at the Strophe.LogLevel.INFO level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
    info: function (msg)
    {
        this.log(this.LogLevel.INFO, msg);
    },

    /** Function: warn
     *  Log a message at the Strophe.LogLevel.WARN level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
    warn: function (msg)
    {
        this.log(this.LogLevel.WARN, msg);
    },

    /** Function: error
     *  Log a message at the Strophe.LogLevel.ERROR level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
    error: function (msg)
    {
        this.log(this.LogLevel.ERROR, msg);
    },

    /** Function: fatal
     *  Log a message at the Strophe.LogLevel.FATAL level.
     *
     *  Parameters:
     *    (String) msg - The log message.
     */
    fatal: function (msg)
    {
        this.log(this.LogLevel.FATAL, msg);
    },

    /** Function: serialize
     *  Render a DOM element and all descendants to a String.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    The serialized element tree as a String.
     */
    serialize: function (elem)
    {
        var result;

        if (!elem) { return null; }

        if (typeof(elem.tree) === "function") {
            elem = elem.tree();
        }

        var nodeName = elem.nodeName;
        var i, child;

        if (elem.getAttribute("_realname")) {
            nodeName = elem.getAttribute("_realname");
        }

        result = "<" + nodeName;
        for (i = 0; i < elem.attributes.length; i++) {
               if(elem.attributes[i].nodeName != "_realname") {
                 result += " " + elem.attributes[i].nodeName.toLowerCase() +
                "='" + elem.attributes[i].value
                    .replace("&", "&amp;")
                       .replace("'", "&apos;")
                       .replace("<", "&lt;") + "'";
               }
        }

        if (elem.childNodes.length > 0) {
            result += ">";
            for (i = 0; i < elem.childNodes.length; i++) {
                child = elem.childNodes[i];
                if (child.nodeType == Strophe.ElementType.NORMAL) {
                    // normal element, so recurse
                    result += Strophe.serialize(child);
                } else if (child.nodeType == Strophe.ElementType.TEXT) {
                    // text element
                    result += child.nodeValue;
                }
            }
            result += "</" + nodeName + ">";
        } else {
            result += "/>";
        }

        return result;
    },

    /** PrivateVariable: _requestId
     *  _Private_ variable that keeps track of the request ids for
     *  connections.
     */
    _requestId: 0,

    /** PrivateVariable: Strophe.connectionPlugins
     *  _Private_ variable Used to store plugin names that need
     *  initialization on Strophe.Connection construction.
     */
    _connectionPlugins: {},

    /** Function: addConnectionPlugin
     *  Extends the Strophe.Connection object with the given plugin.
     *
     *  Paramaters:
     *    (String) name - The name of the extension.
     *    (Object) ptype - The plugin's prototype.
     */
    addConnectionPlugin: function (name, ptype)
    {
        Strophe._connectionPlugins[name] = ptype;
    }
};

/** Class: Strophe.Builder
 *  XML DOM builder.
 *
 *  This object provides an interface similar to JQuery but for building
 *  DOM element easily and rapidly.  All the functions except for toString()
 *  and tree() return the object, so calls can be chained.  Here's an
 *  example using the $iq() builder helper.
 *  > $iq({to: 'you': from: 'me': type: 'get', id: '1'})
 *  >     .c('query', {xmlns: 'strophe:example'})
 *  >     .c('example')
 *  >     .toString()
 *  The above generates this XML fragment
 *  > <iq to='you' from='me' type='get' id='1'>
 *  >   <query xmlns='strophe:example'>
 *  >     <example/>
 *  >   </query>
 *  > </iq>
 *  The corresponding DOM manipulations to get a similar fragment would be
 *  a lot more tedious and probably involve several helper variables.
 *
 *  Since adding children makes new operations operate on the child, up()
 *  is provided to traverse up the tree.  To add two children, do
 *  > builder.c('child1', ...).up().c('child2', ...)
 *  The next operation on the Builder will be relative to the second child.
 */

/** Constructor: Strophe.Builder
 *  Create a Strophe.Builder object.
 *
 *  The attributes should be passed in object notation.  For example
 *  > var b = new Builder('message', {to: 'you', from: 'me'});
 *  or
 *  > var b = new Builder('messsage', {'xml:lang': 'en'});
 *
 *  Parameters:
 *    (String) name - The name of the root element.
 *    (Object) attrs - The attributes for the root element in object notation.
 *
 *  Returns:
 *    A new Strophe.Builder.
 */
Strophe.Builder = function (name, attrs)
{
    // Set correct namespace for jabber:client elements
    if (name == "presence" || name == "message" || name == "iq") {
        if (attrs && !attrs.xmlns) {
            attrs.xmlns = Strophe.NS.CLIENT;
        } else if (!attrs) {
            attrs = {xmlns: Strophe.NS.CLIENT};
        }
    }

    // Holds the tree being built.
    this.nodeTree = Strophe.xmlElement(name, attrs);

    // Points to the current operation node.
    this.node = this.nodeTree;
};

Strophe.Builder.prototype = {
    /** Function: tree
     *  Return the DOM tree.
     *
     *  This function returns the current DOM tree as an element object.  This
     *  is suitable for passing to functions like Strophe.Connection.send().
     *
     *  Returns:
     *    The DOM tree as a element object.
     */
    tree: function ()
    {
        return this.nodeTree;
    },

    /** Function: toString
     *  Serialize the DOM tree to a String.
     *
     *  This function returns a string serialization of the current DOM
     *  tree.  It is often used internally to pass data to a
     *  Strophe.Request object.
     *
     *  Returns:
     *    The serialized DOM tree in a String.
     */
    toString: function ()
    {
        return Strophe.serialize(this.nodeTree);
    },

    /** Function: up
     *  Make the current parent element the new current element.
     *
     *  This function is often used after c() to traverse back up the tree.
     *  For example, to add two children to the same element
     *  > builder.c('child1', {}).up().c('child2', {});
     *
     *  Returns:
     *    The Stophe.Builder object.
     */
    up: function ()
    {
        this.node = this.node.parentNode;
        return this;
    },

    /** Function: attrs
     *  Add or modify attributes of the current element.
     *
     *  The attributes should be passed in object notation.  This function
     *  does not move the current element pointer.
     *
     *  Parameters:
     *    (Object) moreattrs - The attributes to add/modify in object notation.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
    attrs: function (moreattrs)
    {
        for (var k in moreattrs) {
            if (moreattrs.hasOwnProperty(k)) {
                this.node.setAttribute(k, moreattrs[k]);
            }
        }
        return this;
    },

    /** Function: c
     *  Add a child to the current element and make it the new current
     *  element.
     *
     *  This function moves the current element pointer to the child.  If you
     *  need to add another child, it is necessary to use up() to go back
     *  to the parent in the tree.
     *
     *  Parameters:
     *    (String) name - The name of the child.
     *    (Object) attrs - The attributes of the child in object notation.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
    c: function (name, attrs)
    {
        var child = Strophe.xmlElement(name, attrs);
        this.node.appendChild(child);
        this.node = child;
        return this;
    },

    /** Function: cnode
     *  Add a child to the current element and make it the new current
     *  element.
     *
     *  This function is the same as c() except that instead of using a
     *  name and an attributes object to create the child it uses an
     *  existing DOM element object.
     *
     *  Parameters:
     *    (XMLElement) elem - A DOM element.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
    cnode: function (elem)
    {
        this.node.appendChild(elem);
        this.node = elem;
        return this;
    },

    /** Function: t
     *  Add a child text element.
     *
     *  This *does not* make the child the new current element since there
     *  are no children of text elements.
     *
     *  Parameters:
     *    (String) text - The text data to append to the current element.
     *
     *  Returns:
     *    The Strophe.Builder object.
     */
    t: function (text)
    {
        var child = Strophe.xmlTextNode(text);
        this.node.appendChild(child);
        return this;
    }
};


/** PrivateClass: Strophe.Handler
 *  _Private_ helper class for managing stanza handlers.
 *
 *  A Strophe.Handler encapsulates a user provided callback function to be
 *  executed when matching stanzas are received by the connection.
 *  Handlers can be either one-off or persistant depending on their
 *  return value. Returning true will cause a Handler to remain active, and
 *  returning false will remove the Handler.
 *
 *  Users will not use Strophe.Handler objects directly, but instead they
 *  will use Strophe.Connection.addHandler() and
 *  Strophe.Connection.deleteHandler().
 */

/** PrivateConstructor: Strophe.Handler
 *  Create and initialize a new Strophe.Handler.
 *
 *  Parameters:
 *    (Function) handler - A function to be executed when the handler is run.
 *    (String) ns - The namespace to match.
 *    (String) name - The element name to match.
 *    (String) type - The element type to match.
 *    (String) id - The element id attribute to match.
 *    (String) from - The element from attribute to match.
 *    (Object) options - Handler options
 *
 *  Returns:
 *    A new Strophe.Handler object.
 */
Strophe.Handler = function (handler, ns, name, type, id, from, options)
{
    this.handler = handler;
    this.ns = ns;
    this.name = name;
    this.type = type;
    this.id = id;
    this.options = options || {matchbare: false};
    
    // default matchBare to false if undefined
    if (!this.options.matchBare) {
        this.options.matchBare = false;
    }

    if (this.options.matchBare) {
        this.from = Strophe.getBareJidFromJid(from);
    } else {
        this.from = from;
    }

    // whether the handler is a user handler or a system handler
    this.user = true;
};

Strophe.Handler.prototype = {
    /** PrivateFunction: isMatch
     *  Tests if a stanza matches the Strophe.Handler.
     *
     *  Parameters:
     *    (XMLElement) elem - The XML element to test.
     *
     *  Returns:
     *    true if the stanza matches and false otherwise.
     */
    isMatch: function (elem)
    {
        var nsMatch;
        var from = null;
        
        if (this.options.matchBare) {
            from = Strophe.getBareJidFromJid(elem.getAttribute('from'));
        } else {
            from = elem.getAttribute('from');
        }

        nsMatch = false;
        if (!this.ns) {
            nsMatch = true;
        } else {
            var that = this;
            Strophe.forEachChild(elem, null, function (elem) {
                if (elem.getAttribute("xmlns") == that.ns) {
                    nsMatch = true;
                }
            });

            nsMatch = nsMatch || elem.getAttribute("xmlns") == this.ns;
        }

        if (nsMatch &&
            (!this.name || Strophe.isTagEqual(elem, this.name)) &&
            (!this.type || elem.getAttribute("type") === this.type) &&
            (!this.id || elem.getAttribute("id") === this.id) &&
            (!this.from || from === this.from)) {
                return true;
        }

        return false;
    },

    /** PrivateFunction: run
     *  Run the callback on a matching stanza.
     *
     *  Parameters:
     *    (XMLElement) elem - The DOM element that triggered the
     *      Strophe.Handler.
     *
     *  Returns:
     *    A boolean indicating if the handler should remain active.
     */
    run: function (elem)
    {
        var result = null;
        try {
            result = this.handler(elem);
        } catch (e) {
            if (e.sourceURL) {
                Strophe.fatal("error: " + this.handler +
                              " " + e.sourceURL + ":" +
                              e.line + " - " + e.name + ": " + e.message);
            } else if (e.fileName) {
                if (typeof(console) != "undefined") {
                    console.trace();
                    console.error(this.handler, " - error - ", e, e.message);
                }
                Strophe.fatal("error: " + this.handler + " " +
                              e.fileName + ":" + e.lineNumber + " - " +
                              e.name + ": " + e.message);
            } else {
                Strophe.fatal("error: " + this.handler);
            }

            throw e;
        }

        return result;
    },

    /** PrivateFunction: toString
     *  Get a String representation of the Strophe.Handler object.
     *
     *  Returns:
     *    A String.
     */
    toString: function ()
    {
        return "{Handler: " + this.handler + "(" + this.name + "," +
            this.id + "," + this.ns + ")}";
    }
};

/** PrivateClass: Strophe.TimedHandler
 *  _Private_ helper class for managing timed handlers.
 *
 *  A Strophe.TimedHandler encapsulates a user provided callback that
 *  should be called after a certain period of time or at regular
 *  intervals.  The return value of the callback determines whether the
 *  Strophe.TimedHandler will continue to fire.
 *
 *  Users will not use Strophe.TimedHandler objects directly, but instead
 *  they will use Strophe.Connection.addTimedHandler() and
 *  Strophe.Connection.deleteTimedHandler().
 */

/** PrivateConstructor: Strophe.TimedHandler
 *  Create and initialize a new Strophe.TimedHandler object.
 *
 *  Parameters:
 *    (Integer) period - The number of milliseconds to wait before the
 *      handler is called.
 *    (Function) handler - The callback to run when the handler fires.  This
 *      function should take no arguments.
 *
 *  Returns:
 *    A new Strophe.TimedHandler object.
 */
Strophe.TimedHandler = function (period, handler)
{
    this.period = period;
    this.handler = handler;

    this.lastCalled = new Date().getTime();
    this.user = true;
};

Strophe.TimedHandler.prototype = {
    /** PrivateFunction: run
     *  Run the callback for the Strophe.TimedHandler.
     *
     *  Returns:
     *    true if the Strophe.TimedHandler should be called again, and false
     *      otherwise.
     */
    run: function ()
    {
        this.lastCalled = new Date().getTime();
        return this.handler();
    },

    /** PrivateFunction: reset
     *  Reset the last called time for the Strophe.TimedHandler.
     */
    reset: function ()
    {
        this.lastCalled = new Date().getTime();
    },

    /** PrivateFunction: toString
     *  Get a string representation of the Strophe.TimedHandler object.
     *
     *  Returns:
     *    The string representation.
     */
    toString: function ()
    {
        return "{TimedHandler: " + this.handler + "(" + this.period +")}";
    }
};

/** PrivateClass: Strophe.Request
 *  _Private_ helper class that provides a cross implementation abstraction
 *  for a BOSH related XMLHttpRequest.
 *
 *  The Strophe.Request class is used internally to encapsulate BOSH request
 *  information.  It is not meant to be used from user's code.
 */

/** PrivateConstructor: Strophe.Request
 *  Create and initialize a new Strophe.Request object.
 *
 *  Parameters:
 *    (XMLElement) elem - The XML data to be sent in the request.
 *    (Function) func - The function that will be called when the
 *      XMLHttpRequest readyState changes.
 *    (Integer) rid - The BOSH rid attribute associated with this request.
 *    (Integer) sends - The number of times this same request has been
 *      sent.
 */
Strophe.Request = function (elem, func, rid, sends)
{
    this.id = ++Strophe._requestId;
    this.xmlData = elem;
    this.data = Strophe.serialize(elem);
    // save original function in case we need to make a new request
    // from this one.
    this.origFunc = func;
    this.func = func;
    this.rid = rid;
    this.date = NaN;
    this.sends = sends || 0;
    this.abort = false;
    this.dead = null;
    this.age = function () {
        if (!this.date) { return 0; }
        var now = new Date();
        return (now - this.date) / 1000;
    };
    this.timeDead = function () {
        if (!this.dead) { return 0; }
        var now = new Date();
        return (now - this.dead) / 1000;
    };
    this.xhr = this._newXHR();
};

Strophe.Request.prototype = {
    /** PrivateFunction: getResponse
     *  Get a response from the underlying XMLHttpRequest.
     *
     *  This function attempts to get a response from the request and checks
     *  for errors.
     *
     *  Throws:
     *    "parsererror" - A parser error occured.
     *
     *  Returns:
     *    The DOM element tree of the response.
     */
    getResponse: function ()
    {
        var node = null;
        if (this.xhr.responseXML && this.xhr.responseXML.documentElement) {
            node = this.xhr.responseXML.documentElement;
            if (node.tagName == "parsererror") {
                Strophe.error("invalid response received");
                Strophe.error("responseText: " + this.xhr.responseText);
                Strophe.error("responseXML: " +
                              Strophe.serialize(this.xhr.responseXML));
                throw "parsererror";
            }
        } else if (this.xhr.responseText) {
            Strophe.error("invalid response received");
            Strophe.error("responseText: " + this.xhr.responseText);
            Strophe.error("responseXML: " +
                          Strophe.serialize(this.xhr.responseXML));
        }

        return node;
    },

    /** PrivateFunction: _newXHR
     *  _Private_ helper function to create XMLHttpRequests.
     *
     *  This function creates XMLHttpRequests across all implementations.
     *
     *  Returns:
     *    A new XMLHttpRequest.
     */
    _newXHR: function ()
    {
        var xhr = null;
        if (window.XMLHttpRequest) {
            xhr = new XMLHttpRequest();
            if (xhr.overrideMimeType) {
                xhr.overrideMimeType("text/xml");
            }
        } else if (window.ActiveXObject) {
            xhr = new ActiveXObject("Microsoft.XMLHTTP");
        }

        xhr.onreadystatechange = this.func.prependArg(this);

        return xhr;
    }
};

/** Class: Strophe.Connection
 *  XMPP Connection manager.
 *
 *  Thie class is the main part of Strophe.  It manages a BOSH connection
 *  to an XMPP server and dispatches events to the user callbacks as
 *  data arrives.  It supports SASL PLAIN, SASL DIGEST-MD5, and legacy
 *  authentication.
 *
 *  After creating a Strophe.Connection object, the user will typically
 *  call connect() with a user supplied callback to handle connection level
 *  events like authentication failure, disconnection, or connection
 *  complete.
 *
 *  The user will also have several event handlers defined by using
 *  addHandler() and addTimedHandler().  These will allow the user code to
 *  respond to interesting stanzas or do something periodically with the
 *  connection.  These handlers will be active once authentication is
 *  finished.
 *
 *  To send data to the connection, use send().
 */

/** Constructor: Strophe.Connection
 *  Create and initialize a Strophe.Connection object.
 *
 *  Parameters:
 *    (String) service - The BOSH service URL.
 *
 *  Returns:
 *    A new Strophe.Connection object.
 */
Strophe.Connection = function (service)
{
    /* The path to the httpbind service. */
    this.service = service;
    /* The connected JID. */
    this.jid = "";
    /* request id for body tags */
    this.rid = Math.floor(Math.random() * 4294967295);
    /* The current session ID. */
    this.sid = null;
    this.streamId = null;

    // SASL
    this.do_session = false;
    this.do_bind = false;

    // handler lists
    this.timedHandlers = [];
    this.handlers = [];
    this.removeTimeds = [];
    this.removeHandlers = [];
    this.addTimeds = [];
    this.addHandlers = [];

    this._idleTimeout = null;
    this._disconnectTimeout = null;

    this.authenticated = false;
    this.disconnecting = false;
    this.connected = false;

    this.errors = 0;

    this.paused = false;

    // default BOSH values
    this.hold = 1;
    this.wait = 60;
    this.window = 5;

    this._data = [];
    this._requests = [];
    this._uniqueId = Math.round(Math.random() * 10000);

    this._sasl_success_handler = null;
    this._sasl_failure_handler = null;
    this._sasl_challenge_handler = null;

    // setup onIdle callback every 1/10th of a second
    this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);

    // initialize plugins
    for (var k in Strophe._connectionPlugins) {
        if (Strophe._connectionPlugins.hasOwnProperty(k)) {
	    var ptype = Strophe._connectionPlugins[k];
            // jslint complaints about the below line, but this is fine
            var F = function () {};
            F.prototype = ptype;
            this[k] = new F();
	    this[k].init(this);
        }
    }
};

Strophe.Connection.prototype = {
    /** Function: reset
     *  Reset the connection.
     *
     *  This function should be called after a connection is disconnected
     *  before that connection is reused.
     */
    reset: function ()
    {
        this.rid = Math.floor(Math.random() * 4294967295);

        this.sid = null;
        this.streamId = null;

        // SASL
        this.do_session = false;
        this.do_bind = false;

        // handler lists
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

        this._requests = [];
        this._uniqueId = Math.round(Math.random()*10000);
    },

    /** Function: pause
     *  Pause the request manager.
     *
     *  This will prevent Strophe from sending any more requests to the
     *  server.  This is very useful for temporarily pausing while a lot
     *  of send() calls are happening quickly.  This causes Strophe to
     *  send the data in a single request, saving many request trips.
     */
    pause: function ()
    {
        this.paused = true;
    },

    /** Function: resume
     *  Resume the request manager.
     *
     *  This resumes after pause() has been called.
     */
    resume: function ()
    {
        this.paused = false;
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
     *  As the connection process proceeds, the user supplied callback will
     *  be triggered multiple times with status updates.  The callback
     *  should take two arguments - the status code and the error condition.
     *
     *  The status code will be one of the values in the Strophe.Status
     *  constants.  The error condition will be one of the conditions
     *  defined in RFC 3920 or the condition 'strophe-parsererror'.
     *
     *  Please see XEP 124 for a more detailed explanation of the optional
     *  parameters below.
     *
     *  Parameters:
     *    (String) jid - The user's JID.  This may be a bare JID,
     *      or a full JID.  If a node is not supplied, SASL ANONYMOUS
     *      authentication will be attempted.
     *    (String) pass - The user's password.
     *    (Function) callback The connect callback function.
     *    (Integer) wait - The optional HTTPBIND wait value.  This is the
     *      time the server will wait before returning an empty result for
     *      a request.  The default setting of 60 seconds is recommended.
     *      Other settings will require tweaks to the Strophe.TIMEOUT value.
     *    (Integer) hold - The optional HTTPBIND hold value.  This is the
     *      number of connections the server will hold at one time.  This
     *      should almost always be set to 1 (the default).
     */
    connect: function (jid, pass, callback, wait, hold)
    {
        this.jid = jid;
        this.pass = pass;
        this.connect_callback = callback;
        this.disconnecting = false;
        this.connected = false;
        this.authenticated = false;
        this.errors = 0;

        this.wait = wait || this.wait;
        this.hold = hold || this.hold;

        // parse jid for domain and resource
        this.domain = Strophe.getDomainFromJid(this.jid);

        // build the body tag
        var body = this._buildBody().attrs({
            to: this.domain,
            "xml:lang": "en",
            wait: this.wait,
            hold: this.hold,
            content: "text/xml; charset=utf-8",
            ver: "1.6",
            "xmpp:version": "1.0",
            "xmlns:xmpp": Strophe.NS.BOSH
        });

        this._changeConnectStatus(Strophe.Status.CONNECTING, null);

        this._requests.push(
            new Strophe.Request(body.tree(),
                                this._onRequestStateChange.bind(this)
                                    .prependArg(this._connect_cb.bind(this)),
                                body.tree().getAttribute("rid")));
        this._throttledRequestHandler();
    },

    /** Function: attach
     *  Attach to an already created and authenticated BOSH session.
     *
     *  This function is provided to allow Strophe to attach to BOSH
     *  sessions which have been created externally, perhaps by a Web
     *  application.  This is often used to support auto-login type features
     *  without putting user credentials into the page.
     *
     *  Parameters:
     *    (String) jid - The full JID that is bound by the session.
     *    (String) sid - The SID of the BOSH session.
     *    (String) rid - The current RID of the BOSH session.  This RID
     *      will be used by the next request.
     *    (Function) callback The connect callback function.
     *    (Integer) wait - The optional HTTPBIND wait value.  This is the
     *      time the server will wait before returning an empty result for
     *      a request.  The default setting of 60 seconds is recommended.
     *      Other settings will require tweaks to the Strophe.TIMEOUT value.
     *    (Integer) hold - The optional HTTPBIND hold value.  This is the
     *      number of connections the server will hold at one time.  This
     *      should almost always be set to 1 (the default).
     *    (Integer) wind - The optional HTTBIND window value.  This is the
     *      allowed range of request ids that are valid.  The default is 5.
     */
    attach: function (jid, sid, rid, callback, wait, hold, wind)
    {
        this.jid = jid;
        this.sid = sid;
        this.rid = rid;
        this.connect_callback = callback;

        this.domain = Strophe.getDomainFromJid(this.jid);

        this.authenticated = true;
        this.connected = true;

        this.wait = wait || this.wait;
        this.hold = hold || this.hold;
        this.window = wind || this.window;

        this._changeConnectStatus(Strophe.Status.ATTACHED, null);
    },

    /** Function: xmlInput
     *  User overrideable function that receives XML data coming into the
     *  connection.
     *
     *  The default function does nothing.  User code can override this with
     *  > Strophe.Connection.xmlInput = function (elem) {
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
     *  > Strophe.Connection.xmlOutput = function (elem) {
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
     *  > Strophe.Connection.rawInput = function (data) {
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
     *  > Strophe.Connection.rawOutput = function (data) {
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
    send: function (elem)
    {
        if (elem === null) { return ; }
        if (typeof(elem.sort) === "function") {
            for (var i = 0; i < elem.length; i++) {
                this._queueData(elem[i]);
            }
        } else if (typeof(elem.tree) === "function") {
            this._queueData(elem.tree());
        } else {
            this._queueData(elem);
        }

        this._throttledRequestHandler();
        clearTimeout(this._idleTimeout);
        this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
    },

    /** Function: flush
     *  Immediately send any pending outgoing data.
     *  
     *  Normally send() queues outgoing data until the next idle period
     *  (100ms), which optimizes network use in the common cases when
     *  several send()s are called in succession. flush() can be used to 
     *  immediately send all pending data.
     */
    flush: function ()
    {
        // cancel the pending idle period and run the idle function
        // immediately
        clearTimeout(this._idleTimeout);
        this._onIdle();
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
	    if (iqtype === 'result') {
		if (callback) {
                    callback(stanza);
                }
	    } else if (iqtype === 'error') {
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
	if (timeout) {
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

    /** PrivateFunction: _queueData
     *  Queue outgoing data for later sending.  Also ensures that the data
     *  is a DOMElement.
     */
    _queueData: function (element) {
        if (element === null ||
            !element.tagName ||
            !element.childNodes) {
            throw {
                name: "StropheError",
                message: "Cannot queue non-DOMElement."
            };
        }
        
        this._data.push(element);
    },

    /** PrivateFunction: _sendRestart
     *  Send an xmpp:restart stanza.
     */
    _sendRestart: function ()
    {
        this._data.push("restart");

        this._throttledRequestHandler();
        clearTimeout(this._idleTimeout);
        this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
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
    disconnect: function (reason)
    {
        this._changeConnectStatus(Strophe.Status.DISCONNECTING, reason);

        Strophe.info("Disconnect was called because: " + reason);
        if (this.connected) {
            // setup timeout handler
            this._disconnectTimeout = this._addSysTimedHandler(
                30000, this._onDisconnectTimeout.bind(this));
                
            // remove all of the requests
            if (this._requests.length > 0) {
            	for (var i=0; i<this._requests.length; i++) {
        			this._removeRequest(this._requests[i]);
            	}
        	}
                
            this._sendTerminate();
        }
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
        for (var k in Strophe._connectionPlugins) {
            if (Strophe._connectionPlugins.hasOwnProperty(k)) {
                var plugin = this[k];
                if (plugin.statusChanged) {
                    try {
                        plugin.statusChanged(status, condition);
                    } catch (err) {
                        Strophe.error("" + k + " plugin caused an exception " +
                                      "changing status: " + err);
                    }
                }
            }
        }

        // notify the user's callback
        if (this.connect_callback) {
            try {
                this.connect_callback(status, condition);
            } catch (e) {
                Strophe.error("User connection callback caused an " +
                              "exception: " + e);
            }
        }
    },

    /** PrivateFunction: _buildBody
     *  _Private_ helper function to generate the <body/> wrapper for BOSH.
     *
     *  Returns:
     *    A Strophe.Builder with a <body/> element.
     */
    _buildBody: function ()
    {
        var bodyWrap = $build('body', {
            rid: this.rid++,
            xmlns: Strophe.NS.HTTPBIND
        });

        if (this.sid !== null) {
            bodyWrap.attrs({sid: this.sid});
        }

        return bodyWrap;
    },

    /** PrivateFunction: _removeRequest
     *  _Private_ function to remove a request from the queue.
     *
     *  Parameters:
     *    (Strophe.Request) req - The request to remove.
     */
    _removeRequest: function (req)
    {
        Strophe.debug("removing request");

        var i;
        for (i = this._requests.length - 1; i >= 0; i--) {
            if (req == this._requests[i]) {
                this._requests.splice(i, 1);
            }
        }

        // IE6 fails on setting to null, so set to empty function
        req.xhr.onreadystatechange = function () {};

        this._throttledRequestHandler();
    },

    /** PrivateFunction: _restartRequest
     *  _Private_ function to restart a request that is presumed dead.
     *
     *  Parameters:
     *    (Integer) i - The index of the request in the queue.
     */
    _restartRequest: function (i)
    {
        var req = this._requests[i];
        if (req.dead === null) {
            req.dead = new Date();
        }

        this._processRequest(i);
    },

    /** PrivateFunction: _processRequest
     *  _Private_ function to process a request in the queue.
     *
     *  This function takes requests off the queue and sends them and
     *  restarts dead requests.
     *
     *  Parameters:
     *    (Integer) i - The index of the request in the queue.
     */
    _processRequest: function (i)
    {
        var req = this._requests[i];
        var reqStatus = -1;

        try {
            if (req.xhr.readyState == 4) {
                reqStatus = req.xhr.status;
            }
        } catch (e) {
            Strophe.error("caught an error in _requests[" + i +
                          "], reqStatus: " + reqStatus);
        }

        if (typeof(reqStatus) == "undefined") {
            reqStatus = -1;
        }

        var time_elapsed = req.age();
        var primaryTimeout = (!isNaN(time_elapsed) &&
                              time_elapsed > Math.floor(Strophe.TIMEOUT * this.wait));
        var secondaryTimeout = (req.dead !== null &&
                                req.timeDead() > Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait));
        var requestCompletedWithServerError = (req.xhr.readyState == 4 &&
                                               (reqStatus < 1 ||
                                                reqStatus >= 500));
        if (primaryTimeout || secondaryTimeout ||
            requestCompletedWithServerError) {
            if (secondaryTimeout) {
                Strophe.error("Request " +
                              this._requests[i].id +
                              " timed out (secondary), restarting");
            }
            req.abort = true;
            req.xhr.abort();
            // setting to null fails on IE6, so set to empty function
            req.xhr.onreadystatechange = function () {};
            this._requests[i] = new Strophe.Request(req.xmlData,
                                                    req.origFunc,
                                                    req.rid,
                                                    req.sends);
            req = this._requests[i];
        }

        if (req.xhr.readyState === 0) {
            Strophe.debug("request id " + req.id +
                          "." + req.sends + " posting");

            req.date = new Date();
            try {
                req.xhr.open("POST", this.service, true);
            } catch (e2) {
                Strophe.error("XHR open failed.");
                if (!this.connected) {
                    this._changeConnectStatus(Strophe.Status.CONNFAIL,
                                              "bad-service");
                }
                this.disconnect();
                return;
            }

            // Fires the XHR request -- may be invoked immediately
            // or on a gradually expanding retry window for reconnects
            var sendFunc = function () {
                req.xhr.send(req.data);
            };

            // Implement progressive backoff for reconnects --
            // First retry (send == 1) should also be instantaneous
            if (req.sends > 1) {
                // Using a cube of the retry number creats a nicely
                // expanding retry window
                var backoff = Math.pow(req.sends, 3) * 1000;
                setTimeout(sendFunc, backoff);
            } else {
                sendFunc();
            }

            req.sends++;

            this.xmlOutput(req.xmlData);
            this.rawOutput(req.data);
        } else {
            Strophe.debug("_processRequest: " +
                          (i === 0 ? "first" : "second") +
                          " request has readyState of " +
                          req.xhr.readyState);
        }
    },

    /** PrivateFunction: _throttledRequestHandler
     *  _Private_ function to throttle requests to the connection window.
     *
     *  This function makes sure we don't send requests so fast that the
     *  request ids overflow the connection window in the case that one
     *  request died.
     */
    _throttledRequestHandler: function ()
    {
        if (!this._requests) {
            Strophe.debug("_throttledRequestHandler called with " +
                          "undefined requests");
        } else {
            Strophe.debug("_throttledRequestHandler called with " +
                          this._requests.length + " requests");
        }

        if (!this._requests || this._requests.length === 0) {
            return;
        }

        if (this._requests.length > 0) {
            this._processRequest(0);
        }

        if (this._requests.length > 1 &&
            Math.abs(this._requests[0].rid -
                     this._requests[1].rid) < this.window - 1) {
            this._processRequest(1);
        }
    },

    /** PrivateFunction: _onRequestStateChange
     *  _Private_ handler for Strophe.Request state changes.
     *
     *  This function is called when the XMLHttpRequest readyState changes.
     *  It contains a lot of error handling logic for the many ways that
     *  requests can fail, and calls the request callback when requests
     *  succeed.
     *
     *  Parameters:
     *    (Function) func - The handler for the request.
     *    (Strophe.Request) req - The request that is changing readyState.
     */
    _onRequestStateChange: function (func, req)
    {
        Strophe.debug("request id " + req.id +
                      "." + req.sends + " state changed to " +
                      req.xhr.readyState);

        if (req.abort) {
            req.abort = false;
            return;
        }

        // request complete
        var reqStatus;
        if (req.xhr.readyState == 4) {
            reqStatus = 0;
            try {
                reqStatus = req.xhr.status;
            } catch (e) {
                // ignore errors from undefined status attribute.  works
                // around a browser bug
            }

            if (typeof(reqStatus) == "undefined") {
                reqStatus = 0;
            }

            if (this.disconnecting) {
                if (reqStatus >= 400) {
                    this._hitError(reqStatus);
                    return;
                }
            }

            var reqIs0 = (this._requests[0] == req);
            var reqIs1 = (this._requests[1] == req);

            if ((reqStatus > 0 && reqStatus < 500) || req.sends > 5) {
                // remove from internal queue
                this._removeRequest(req);
                Strophe.debug("request id " +
                              req.id +
                              " should now be removed");
            }

            // request succeeded
            if (reqStatus == 200) {
                // if request 1 finished, or request 0 finished and request
                // 1 is over Strophe.SECONDARY_TIMEOUT seconds old, we need to
                // restart the other - both will be in the first spot, as the
                // completed request has been removed from the queue already
                if (reqIs1 ||
                    (reqIs0 && this._requests.length > 0 &&
                     this._requests[0].age() > Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait))) {
                    this._restartRequest(0);
                }
                // call handler
                Strophe.debug("request id " +
                              req.id + "." +
                              req.sends + " got 200");
                func(req);
                this.errors = 0;
            } else {
                Strophe.error("request id " +
                              req.id + "." +
                              req.sends + " error " + reqStatus +
                              " happened");
                if (reqStatus === 0 ||
                    (reqStatus >= 400 && reqStatus < 600) ||
                    reqStatus >= 12000) {
                    this._hitError(reqStatus);
                    if (reqStatus >= 400 && reqStatus < 500) {
                        this._changeConnectStatus(Strophe.Status.DISCONNECTING,
                                                  null);
                        this._doDisconnect();
                    }
                }
            }

            if (!((reqStatus > 0 && reqStatus < 10000) ||
                  req.sends > 5)) {
                this._throttledRequestHandler();
            }
        }
    },

    /** PrivateFunction: _hitError
     *  _Private_ function to handle the error count.
     *
     *  Requests are resent automatically until their error count reaches
     *  5.  Each time an error is encountered, this function is called to
     *  increment the count and disconnect if the count is too high.
     *
     *  Parameters:
     *    (Integer) reqStatus - The request status.
     */
    _hitError: function (reqStatus)
    {
        this.errors++;
        Strophe.warn("request errored, status: " + reqStatus +
                     ", number of errors: " + this.errors);
        if (this.errors > 4) {
            this._onDisconnectTimeout();
        }
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
        this.authenticated = false;
        this.disconnecting = false;
        this.sid = null;
        this.streamId = null;
        this.rid = Math.floor(Math.random() * 4294967295);

        // tell the parent we disconnected
        if (this.connected) {
            this._changeConnectStatus(Strophe.Status.DISCONNECTED, null);
            this.connected = false;
        }

        // delete handlers
        this.handlers = [];
        this.timedHandlers = [];
        this.removeTimeds = [];
        this.removeHandlers = [];
        this.addTimeds = [];
        this.addHandlers = [];
    },

    /** PrivateFunction: _dataRecv
     *  _Private_ handler to processes incoming data from the the connection.
     *
     *  Except for _connect_cb handling the initial connection request,
     *  this function handles the incoming data for all requests.  This
     *  function also fires stanza handlers that match each incoming
     *  stanza.
     *
     *  Parameters:
     *    (Strophe.Request) req - The request that has data ready.
     */
    _dataRecv: function (req)
    {
        try {
            var elem = req.getResponse();
        } catch (e) {
            if (e != "parsererror") { throw e; }
            this.disconnect("strophe-parsererror");
        }
        if (elem === null) { return; }

        this.xmlInput(elem);
        this.rawInput(Strophe.serialize(elem));

        // remove handlers scheduled for deletion
        var i, hand;
        while (this.removeHandlers.length > 0) {
            hand = this.removeHandlers.pop();
            i = this.handlers.indexOf(hand);
            if (i >= 0) {
                this.handlers.splice(i, 1);
            }
        }

        // add handlers scheduled for addition
        while (this.addHandlers.length > 0) {
            this.handlers.push(this.addHandlers.pop());
        }

        // handle graceful disconnect
        if (this.disconnecting && this._requests.length === 0) {
            this.deleteTimedHandler(this._disconnectTimeout);
            this._disconnectTimeout = null;
            this._doDisconnect();
            return;
        }

        var typ = elem.getAttribute("type");
        var cond, conflict;
        if (typ !== null && typ == "terminate") {
            // an error occurred
            cond = elem.getAttribute("condition");
            conflict = elem.getElementsByTagName("conflict");
            if (cond !== null) {
                if (cond == "remote-stream-error" && conflict.length > 0) {
                    cond = "conflict";
                }
                this._changeConnectStatus(Strophe.Status.CONNFAIL, cond);
            } else {
                this._changeConnectStatus(Strophe.Status.CONNFAIL, "unknown");
            }
            this.disconnect();
            return;
        }

        // send each incoming stanza through the handler chain
        var that = this;
        Strophe.forEachChild(elem, null, function (child) {
            var i, newList;
            // process handlers
            newList = that.handlers;
            that.handlers = [];
            for (i = 0; i < newList.length; i++) {
                var hand = newList[i];
                if (hand.isMatch(child) &&
                    (that.authenticated || !hand.user)) {
                    if (hand.run(child)) {
                        that.handlers.push(hand);
                    }
                } else {
                    that.handlers.push(hand);
                }
            }
        });
    },

    /** PrivateFunction: _sendTerminate
     *  _Private_ function to send initial disconnect sequence.
     *
     *  This is the first step in a graceful disconnect.  It sends
     *  the BOSH server a terminate body and includes an unavailable
     *  presence if authentication has completed.
     */
    _sendTerminate: function ()
    {
        Strophe.info("_sendTerminate was called");
        var body = this._buildBody().attrs({type: "terminate"});

        if (this.authenticated) {
            body.c('presence', {
                xmlns: Strophe.NS.CLIENT,
                type: 'unavailable'
            });
        }

        this.disconnecting = true;

        var req = new Strophe.Request(body.tree(),
                                      this._onRequestStateChange.bind(this)
                                          .prependArg(this._dataRecv.bind(this)),
                                      body.tree().getAttribute("rid"));

        this._requests.push(req);
        this._throttledRequestHandler();
    },

    /** PrivateFunction: _connect_cb
     *  _Private_ handler for initial connection request.
     *
     *  This handler is used to process the initial connection request
     *  response from the BOSH server. It is used to set up authentication
     *  handlers and start the authentication process.
     *
     *  SASL authentication will be attempted if available, otherwise
     *  the code will fall back to legacy authentication.
     *
     *  Parameters:
     *    (Strophe.Request) req - The current request.
     */
    _connect_cb: function (req)
    {
        Strophe.info("_connect_cb was called");

        this.connected = true;
        var bodyWrap = req.getResponse();
        if (!bodyWrap) { return; }

        this.xmlInput(bodyWrap);
        this.rawInput(Strophe.serialize(bodyWrap));

        var typ = bodyWrap.getAttribute("type");
        var cond, conflict;
        if (typ !== null && typ == "terminate") {
            // an error occurred
            cond = bodyWrap.getAttribute("condition");
            conflict = bodyWrap.getElementsByTagName("conflict");
            if (cond !== null) {
                if (cond == "remote-stream-error" && conflict.length > 0) {
                    cond = "conflict";
                }
                this._changeConnectStatus(Strophe.Status.CONNFAIL, cond);
            } else {
                this._changeConnectStatus(Strophe.Status.CONNFAIL, "unknown");
            }
            return;
        }

        // check to make sure we don't overwrite these if _connect_cb is
        // called multiple times in the case of missing stream:features
        if (!this.sid) {
            this.sid = bodyWrap.getAttribute("sid");
        }
        if (!this.stream_id) {
            this.stream_id = bodyWrap.getAttribute("authid");
        }
        var wind = bodyWrap.getAttribute('requests');
        if (wind) { this.window = parseInt(wind, 10); }
        var hold = bodyWrap.getAttribute('hold');
        if (hold) { this.hold = parseInt(hold, 10); }
        var wait = bodyWrap.getAttribute('wait');
        if (wait) { this.wait = parseInt(wait, 10); }
        

        var do_sasl_plain = false;
        var do_sasl_digest_md5 = false;
        var do_sasl_anonymous = false;

        var mechanisms = bodyWrap.getElementsByTagName("mechanism");
        var i, mech, auth_str, hashed_auth_str;
        if (mechanisms.length > 0) {
            for (i = 0; i < mechanisms.length; i++) {
                mech = Strophe.getText(mechanisms[i]);
                if (mech == 'DIGEST-MD5') {
                    do_sasl_digest_md5 = true;
                } else if (mech == 'PLAIN') {
                    do_sasl_plain = true;
                } else if (mech == 'ANONYMOUS') {
                    do_sasl_anonymous = true;
                }
            }
        } else {
            // we didn't get stream:features yet, so we need wait for it
            // by sending a blank poll request
            var body = this._buildBody();
            this._requests.push(
                new Strophe.Request(body.tree(),
                                    this._onRequestStateChange.bind(this)
                                      .prependArg(this._connect_cb.bind(this)),
                                    body.tree().getAttribute("rid")));
            this._throttledRequestHandler();
            return;
        }

        if (Strophe.getNodeFromJid(this.jid) === null &&
            do_sasl_anonymous) {
            this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);
            this._sasl_success_handler = this._addSysHandler(
                this._sasl_success_cb.bind(this), null,
                "success", null, null);
            this._sasl_failure_handler = this._addSysHandler(
                this._sasl_failure_cb.bind(this), null,
                "failure", null, null);

            this.send($build("auth", {
                xmlns: Strophe.NS.SASL,
                mechanism: "ANONYMOUS"
            }).tree());
        } else if (Strophe.getNodeFromJid(this.jid) === null) {
            // we don't have a node, which is required for non-anonymous
            // client connections
            this._changeConnectStatus(Strophe.Status.CONNFAIL,
                                      'x-strophe-bad-non-anon-jid');
            this.disconnect();
        } else if (do_sasl_digest_md5) {
            this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);
            this._sasl_challenge_handler = this._addSysHandler(
                this._sasl_challenge1_cb.bind(this), null,
                "challenge", null, null);
            this._sasl_failure_handler = this._addSysHandler(
                this._sasl_failure_cb.bind(this), null,
                "failure", null, null);

            this.send($build("auth", {
                xmlns: Strophe.NS.SASL,
                mechanism: "DIGEST-MD5"
            }).tree());
        } else if (do_sasl_plain) {
            // Build the plain auth string (barejid null
            // username null password) and base 64 encoded.
            auth_str = Strophe.getBareJidFromJid(this.jid);
            auth_str = auth_str + "\u0000";
            auth_str = auth_str + Strophe.getNodeFromJid(this.jid);
            auth_str = auth_str + "\u0000";
            auth_str = auth_str + this.pass;

            this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);
            this._sasl_success_handler = this._addSysHandler(
                this._sasl_success_cb.bind(this), null,
                "success", null, null);
            this._sasl_failure_handler = this._addSysHandler(
                this._sasl_failure_cb.bind(this), null,
                "failure", null, null);

            hashed_auth_str = Base64.encode(auth_str);
            this.send($build("auth", {
                xmlns: Strophe.NS.SASL,
                mechanism: "PLAIN"
            }).t(hashed_auth_str).tree());
        } else {
            this._changeConnectStatus(Strophe.Status.AUTHENTICATING, null);
            this._addSysHandler(this._auth1_cb.bind(this), null, null,
                                null, "_auth_1");

            this.send($iq({
                type: "get",
                to: this.domain,
                id: "_auth_1"
            }).c("query", {
                xmlns: Strophe.NS.AUTH
            }).c("username", {}).t(Strophe.getNodeFromJid(this.jid)).tree());
        }
    },

    /** PrivateFunction: _sasl_challenge1_cb
     *  _Private_ handler for DIGEST-MD5 SASL authentication.
     *
     *  Parameters:
     *    (XMLElement) elem - The challenge stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_challenge1_cb: function (elem)
    {
        var attribMatch = /([a-z]+)=("[^"]+"|[^,"]+)(?:,|$)/;

        var challenge = Base64.decode(Strophe.getText(elem));
        var cnonce = MD5.hexdigest(Math.random() * 1234567890);
        var realm = "";
        var host = null;
        var nonce = "";
        var qop = "";
        var matches;

        // remove unneeded handlers
        this.deleteHandler(this._sasl_failure_handler);

        while (challenge.match(attribMatch)) {
            matches = challenge.match(attribMatch);
            challenge = challenge.replace(matches[0], "");
            matches[2] = matches[2].replace(/^"(.+)"$/, "$1");
            switch (matches[1]) {
            case "realm":
                realm = matches[2];
                break;
            case "nonce":
                nonce = matches[2];
                break;
            case "qop":
                qop = matches[2];
                break;
            case "host":
                host = matches[2];
                break;
            }
        }

        var digest_uri = "xmpp/" + this.domain;
        if (host !== null) {
            digest_uri = digest_uri + "/" + host;
        }

        var A1 = MD5.hash(Strophe.getNodeFromJid(this.jid) +
                          ":" + realm + ":" + this.pass) +
            ":" + nonce + ":" + cnonce;
        var A2 = 'AUTHENTICATE:' + digest_uri;

        var responseText = "";
        responseText += 'username=' +
            this._quote(Strophe.getNodeFromJid(this.jid)) + ',';
        responseText += 'realm=' + this._quote(realm) + ',';
        responseText += 'nonce=' + this._quote(nonce) + ',';
        responseText += 'cnonce=' + this._quote(cnonce) + ',';
        responseText += 'nc="00000001",';
        responseText += 'qop="auth",';
        responseText += 'digest-uri=' + this._quote(digest_uri) + ',';
        responseText += 'response=' + this._quote(
            MD5.hexdigest(MD5.hexdigest(A1) + ":" +
                          nonce + ":00000001:" +
                          cnonce + ":auth:" +
                          MD5.hexdigest(A2))) + ',';
        responseText += 'charset="utf-8"';

        this._sasl_challenge_handler = this._addSysHandler(
            this._sasl_challenge2_cb.bind(this), null,
            "challenge", null, null);
        this._sasl_success_handler = this._addSysHandler(
            this._sasl_success_cb.bind(this), null,
            "success", null, null);
        this._sasl_failure_handler = this._addSysHandler(
            this._sasl_failure_cb.bind(this), null,
            "failure", null, null);

        this.send($build('response', {
            xmlns: Strophe.NS.SASL
        }).t(Base64.encode(responseText)).tree());

        return false;
    },

    /** PrivateFunction: _quote
     *  _Private_ utility function to backslash escape and quote strings.
     *
     *  Parameters:
     *    (String) str - The string to be quoted.
     *
     *  Returns:
     *    quoted string
     */
    _quote: function (str)
    {
        return '"' + str.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"'; 
        //" end string workaround for emacs
    },


    /** PrivateFunction: _sasl_challenge2_cb
     *  _Private_ handler for second step of DIGEST-MD5 SASL authentication.
     *
     *  Parameters:
     *    (XMLElement) elem - The challenge stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_challenge2_cb: function (elem)
    {
        // remove unneeded handlers
        this.deleteHandler(this._sasl_success_handler);
        this.deleteHandler(this._sasl_failure_handler);

        this._sasl_success_handler = this._addSysHandler(
            this._sasl_success_cb.bind(this), null,
            "success", null, null);
        this._sasl_failure_handler = this._addSysHandler(
            this._sasl_failure_cb.bind(this), null,
            "failure", null, null);
        this.send($build('response', {xmlns: Strophe.NS.SASL}).tree());
        return false;
    },

    /** PrivateFunction: _auth1_cb
     *  _Private_ handler for legacy authentication.
     *
     *  This handler is called in response to the initial <iq type='get'/>
     *  for legacy authentication.  It builds an authentication <iq/> and
     *  sends it, creating a handler (calling back to _auth2_cb()) to
     *  handle the result
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza that triggered the callback.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _auth1_cb: function (elem)
    {
        // build plaintext auth iq
        var iq = $iq({type: "set", id: "_auth_2"})
            .c('query', {xmlns: Strophe.NS.AUTH})
            .c('username', {}).t(Strophe.getNodeFromJid(this.jid))
            .up()
            .c('password').t(this.pass);

        if (!Strophe.getResourceFromJid(this.jid)) {
            // since the user has not supplied a resource, we pick
            // a default one here.  unlike other auth methods, the server
            // cannot do this for us.
            this.jid = Strophe.getBareJidFromJid(this.jid) + '/strophe';
        }
        iq.up().c('resource', {}).t(Strophe.getResourceFromJid(this.jid));

        this._addSysHandler(this._auth2_cb.bind(this), null,
                            null, null, "_auth_2");

        this.send(iq.tree());

        return false;
    },

    /** PrivateFunction: _sasl_success_cb
     *  _Private_ handler for succesful SASL authentication.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_success_cb: function (elem)
    {
        Strophe.info("SASL authentication succeeded.");

        // remove old handlers
        this.deleteHandler(this._sasl_failure_handler);
        this._sasl_failure_handler = null;
        if (this._sasl_challenge_handler) {
            this.deleteHandler(this._sasl_challenge_handler);
            this._sasl_challenge_handler = null;
        }

        this._addSysHandler(this._sasl_auth1_cb.bind(this), null,
                            "stream:features", null, null);

        // we must send an xmpp:restart now
        this._sendRestart();

        return false;
    },

    /** PrivateFunction: _sasl_auth1_cb
     *  _Private_ handler to start stream binding.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_auth1_cb: function (elem)
    {
        var i, child;

        for (i = 0; i < elem.childNodes.length; i++) {
            child = elem.childNodes[i];
            if (child.nodeName == 'bind') {
                this.do_bind = true;
            }

            if (child.nodeName == 'session') {
                this.do_session = true;
            }
        }

        if (!this.do_bind) {
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            return false;
        } else {
            this._addSysHandler(this._sasl_bind_cb.bind(this), null, null,
                                null, "_bind_auth_2");

            var resource = Strophe.getResourceFromJid(this.jid);
            if (resource) {
                this.send($iq({type: "set", id: "_bind_auth_2"})
                          .c('bind', {xmlns: Strophe.NS.BIND})
                          .c('resource', {}).t(resource).tree());
            } else {
                this.send($iq({type: "set", id: "_bind_auth_2"})
                          .c('bind', {xmlns: Strophe.NS.BIND})
                          .tree());
            }
        }

        return false;
    },

    /** PrivateFunction: _sasl_bind_cb
     *  _Private_ handler for binding result and session start.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_bind_cb: function (elem)
    {
        if (elem.getAttribute("type") == "error") {
            Strophe.info("SASL binding failed.");
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            return false;
        }

        // TODO - need to grab errors
        var bind = elem.getElementsByTagName("bind");
        var jidNode;
        if (bind.length > 0) {
            // Grab jid
            jidNode = bind[0].getElementsByTagName("jid");
            if (jidNode.length > 0) {
                this.jid = Strophe.getText(jidNode[0]);

                if (this.do_session) {
                    this._addSysHandler(this._sasl_session_cb.bind(this),
                                        null, null, null, "_session_auth_2");

                    this.send($iq({type: "set", id: "_session_auth_2"})
                                  .c('session', {xmlns: Strophe.NS.SESSION})
                                  .tree());
                } else {
                    this.authenticated = true;
                    this._changeConnectStatus(Strophe.Status.CONNECTED, null);
                }
            }
        } else {
            Strophe.info("SASL binding failed.");
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            return false;
        }
    },

    /** PrivateFunction: _sasl_session_cb
     *  _Private_ handler to finish successful SASL connection.
     *
     *  This sets Connection.authenticated to true on success, which
     *  starts the processing of user handlers.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_session_cb: function (elem)
    {
        if (elem.getAttribute("type") == "result") {
            this.authenticated = true;
            this._changeConnectStatus(Strophe.Status.CONNECTED, null);
        } else if (elem.getAttribute("type") == "error") {
            Strophe.info("Session creation failed.");
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            return false;
        }

        return false;
    },

    /** PrivateFunction: _sasl_failure_cb
     *  _Private_ handler for SASL authentication failure.
     *
     *  Parameters:
     *    (XMLElement) elem - The matching stanza.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _sasl_failure_cb: function (elem)
    {
        // delete unneeded handlers
        if (this._sasl_success_handler) {
            this.deleteHandler(this._sasl_success_handler);
            this._sasl_success_handler = null;
        }
        if (this._sasl_challenge_handler) {
            this.deleteHandler(this._sasl_challenge_handler);
            this._sasl_challenge_handler = null;
        }

        this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
        return false;
    },

    /** PrivateFunction: _auth2_cb
     *  _Private_ handler to finish legacy authentication.
     *
     *  This handler is called when the result from the jabber:iq:auth
     *  <iq/> stanza is returned.
     *
     *  Parameters:
     *    (XMLElement) elem - The stanza that triggered the callback.
     *
     *  Returns:
     *    false to remove the handler.
     */
    _auth2_cb: function (elem)
    {
        if (elem.getAttribute("type") == "result") {
            this.authenticated = true;
            this._changeConnectStatus(Strophe.Status.CONNECTED, null);
        } else if (elem.getAttribute("type") == "error") {
            this._changeConnectStatus(Strophe.Status.AUTHFAIL, null);
            this.disconnect();
        }

        return false;
    },

    /** PrivateFunction: _addSysTimedHandler
     *  _Private_ function to add a system level timed handler.
     *
     *  This function is used to add a Strophe.TimedHandler for the
     *  library code.  System timed handlers are allowed to run before
     *  authentication is complete.
     *
     *  Parameters:
     *    (Integer) period - The period of the handler.
     *    (Function) handler - The callback function.
     */
    _addSysTimedHandler: function (period, handler)
    {
        var thand = new Strophe.TimedHandler(period, handler);
        thand.user = false;
        this.addTimeds.push(thand);
        return thand;
    },

    /** PrivateFunction: _addSysHandler
     *  _Private_ function to add a system level stanza handler.
     *
     *  This function is used to add a Strophe.Handler for the
     *  library code.  System stanza handlers are allowed to run before
     *  authentication is complete.
     *
     *  Parameters:
     *    (Function) handler - The callback function.
     *    (String) ns - The namespace to match.
     *    (String) name - The stanza name to match.
     *    (String) type - The stanza type attribute to match.
     *    (String) id - The stanza id attribute to match.
     */
    _addSysHandler: function (handler, ns, name, type, id)
    {
        var hand = new Strophe.Handler(handler, ns, name, type, id);
        hand.user = false;
        this.addHandlers.push(hand);
        return hand;
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

        // cancel all remaining requests and clear the queue
        var req;
        while (this._requests.length > 0) {
            req = this._requests.pop();
            req.abort = true;
            req.xhr.abort();
            // jslint complains, but this is fine. setting to empty func
            // is necessary for IE6
            req.xhr.onreadystatechange = function () {};
        }

        // actually disconnect
        this._doDisconnect();

        return false;
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
        while (this.removeTimeds.length > 0) {
            thand = this.removeTimeds.pop();
            i = this.timedHandlers.indexOf(thand);
            if (i >= 0) {
                this.timedHandlers.splice(i, 1);
            }
        }

        // add timed handlers scheduled for addition
        while (this.addTimeds.length > 0) {
            this.timedHandlers.push(this.addTimeds.pop());
        }

        // call ready timed handlers
        var now = new Date().getTime();
        newList = [];
        for (i = 0; i < this.timedHandlers.length; i++) {
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

        var body, time_elapsed;

        // if no requests are in progress, poll
        if (this.authenticated && this._requests.length === 0 &&
            this._data.length === 0 && !this.disconnecting) {
            Strophe.info("no requests during idle cycle, sending " +
                         "blank request");
            this._data.push(null);
        }

        if (this._requests.length < 2 && this._data.length > 0 &&
            !this.paused) {
            body = this._buildBody();
            for (i = 0; i < this._data.length; i++) {
                if (this._data[i] !== null) {
                    if (this._data[i] === "restart") {
                        body.attrs({
                            to: this.domain,
                            "xml:lang": "en",
                            "xmpp:restart": "true",
                            "xmlns:xmpp": Strophe.NS.BOSH
                        });
                    } else {
                        body.cnode(this._data[i]).up();
                    }
                }
            }
            delete this._data;
            this._data = [];
            this._requests.push(
                new Strophe.Request(body.tree(),
                                    this._onRequestStateChange.bind(this)
                                    .prependArg(this._dataRecv.bind(this)),
                                    body.tree().getAttribute("rid")));
            this._processRequest(this._requests.length - 1);
        }

        if (this._requests.length > 0) {
            time_elapsed = this._requests[0].age();
            if (this._requests[0].dead !== null) {
                if (this._requests[0].timeDead() >
                    Math.floor(Strophe.SECONDARY_TIMEOUT * this.wait)) {
                    this._throttledRequestHandler();
                }
            }

            if (time_elapsed > Math.floor(Strophe.TIMEOUT * this.wait)) {
                Strophe.warn("Request " +
                             this._requests[0].id +
                             " timed out, over " + Math.floor(Strophe.TIMEOUT * this.wait) +
                             " seconds since last activity");
                this._throttledRequestHandler();
            }
        }

        // reactivate the timer
        clearTimeout(this._idleTimeout);
        this._idleTimeout = setTimeout(this._onIdle.bind(this), 100);
    }
};

if (callback) {
    callback(Strophe, $build, $msg, $iq, $pres);
}

})(function () {
    window.Strophe = arguments[0];
    window.$build = arguments[1];
    window.$msg = arguments[2];
    window.$iq = arguments[3];
    window.$pres = arguments[4];
});

/* CORS plugin
**
** flXHR.js should be loaded before this plugin if flXHR support is required.
*/

Strophe.addConnectionPlugin('cors', {
    init: function () {
        // replace Strophe.Request._newXHR with new CORS version
        if (window.XDomainRequest) {
            // We are in IE with CORS support
            Strophe.debug("CORS with IE");
            Strophe.Request.prototype._newXHR = function () {
                var stateChange = function(xhr, state) {
                    // Fudge the calling of onreadystatechange()
                    xhr.status = state;
                    xhr.readyState = 4;
                    try {
                        xhr.onreadystatechange();
                    }catch(err){}
                    xhr.readyState = 0;
                    try{
                        xhr.onreadystatechange();
                    }catch(err){}
                }
                var xhr = new XDomainRequest();
                xhr.readyState = 0;
                xhr.onreadystatechange = this.func.prependArg(this);
                xhr.onload = function () {
                    // Parse the responseText to XML
                    xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
                    xmlDoc.async = "false";
                    xmlDoc.loadXML(xhr.responseText);
                    xhr.responseXML = xmlDoc;
                    stateChange(xhr, 200);
                }
                xhr.onerror = function () {
                    stateChange(xhr, 500);
                }
                xhr.ontimeout = function () {
                    stateChange(xhr, 500);
                }
                var sendFnc = xhr.send;
                xhr.send = function (value) {
                    xhr.readyState = 2;
                    return sendFnc(value);
                }
                return xhr;
            };
        } else if (new XMLHttpRequest().withCredentials !== undefined) {
            // We are in a sane browser with CROS support - no need to do anything
            Strophe.debug("CORS with Firefox/Safari/Chome");
        } else if (flensed && flensed.flXHR) {
            // We don't have CORS support, so include flXHR
            Strophe.debug("CORS not supported, using flXHR");
            var poolingSetting = true;
            if (navigator.userAgent.indexOf('MSIE') !=-1) {
                // IE 7 has an issue with instance pooling and flash 10.1
                poolingSetting = false;
            }
            Strophe.Request.prototype._newXHR = function () {
                var xhr = new flensed.flXHR({
                    autoUpdatePlayer: true,
                    instancePooling: poolingSetting,
                    noCacheHeader: false});
                xhr.onreadystatechange = this.func.prependArg(this);
                return xhr;
            };
        } else {
            Strophe.error("No CORS and no flXHR. You may experience cross domain turbulence.");
        }
    }
});

// Inspired by addEvent - Dean Edwards, 2005
;Phono.events = {
   handlerCount: 1,
   add: function(target, type, handler) {
      // ignore case
      type = type.toLowerCase();
        // assign each event handler a unique ID
        if (!handler.$$guid) handler.$$guid = this.handlerCount++;
        // create a hash table of event types for the target
        if (!target.events) target.events = {};
        // create a hash table of event handlers for each target/event pair
        var handlers = target.events[type];
        if (!handlers) {
            handlers = target.events[type] = {};
            // store the existing event handler (if there is one)
            if (target["on" + type]) {
                handlers[0] = target["on" + type];
            }
        }
        // store the event handler in the hash table
        handlers[handler.$$guid] = handler;
        // assign a global event handler to do all the work
        target["on" + type] = this.handle;
   },
   bind: function(target, config) {
      var name;
      for(k in config) {
        if(k.match("^on")) {
            this.add(target, k.substr(2).toLowerCase(), config[k]);
        }
      }
   },
   remove: function(target, type, handler) {
      // ignore case
      type = type.toLowerCase();
        // delete the event handler from the hash table
        if (target.events && target.events[type]) {
            delete target.events[type][handler.$$guid];
        }
   },
   trigger: function(target, type, event, data) {
      event = event || {};
      event.type = type;
      var handler = target["on"+type.toLowerCase()]
      if(handler) {
         // Don't log log events ;-)
         if("log" != type.toLowerCase()) {
             Phono.log.info("[EVENT] " + type + "[" + data + "]");
         }
         handler.call(target, event, data); 
      }
   },
   handle: function(event, data) {
    // get a reference to the hash table of event handlers
    var handlers = this.events[event.type.toLowerCase()];
    // set event source
    event.source = this;
    // build arguments
    var args = new Array();
    args.push(event);
    if(data) {
       var i;
       for(i=0; i<data.length; i++) {
          args.push(data[i]);
       }
    }
    var target = this;
    // execute each event handler
    Phono.util.each(handlers, function() {
         this.apply(target,args);
    });
   }
};
/*
 * jQuery Tools 1.2.2 - The missing UI library for the Web
 * 
 * [toolbox.flashembed]
 * 
 * NO COPYRIGHTS OR LICENSES. DO WHAT YOU LIKE.
 * 
 * http://flowplayer.org/tools/
 * 
 * File generated: Tue May 25 08:09:15 GMT 2010
 */
(function(){function f(a,b){if(b)for(key in b)if(b.hasOwnProperty(key))a[key]=b[key];return a}function l(a,b){var c=[];for(var d in a)if(a.hasOwnProperty(d))c[d]=b(a[d]);return c}function m(a,b,c){if(e.isSupported(b.version))a.innerHTML=e.getHTML(b,c);else if(b.expressInstall&&e.isSupported([6,65]))a.innerHTML=e.getHTML(f(b,{src:b.expressInstall}),{MMredirectURL:location.href,MMplayerType:"PlugIn",MMdoctitle:document.title});else{if(!a.innerHTML.replace(/\s/g,"")){a.innerHTML="<h2>Flash version "+
b.version+" or greater is required</h2><h3>"+(g[0]>0?"Your version is "+g:"You have no flash plugin installed")+"</h3>"+(a.tagName=="A"?"<p>Click here to download latest version</p>":"<p>Download latest version from <a href='"+k+"'>here</a></p>");if(a.tagName=="A")a.onclick=function(){location.href=k}}if(b.onFail){var d=b.onFail.call(this);if(typeof d=="string")a.innerHTML=d}}if(h)window[b.id]=document.getElementById(b.id);f(this,{getRoot:function(){return a},getOptions:function(){return b},getConf:function(){return c},
getApi:function(){return a.firstChild}})}var h=document.all,k="http://www.adobe.com/go/getflashplayer",n=typeof jQuery=="function",o=/(\d+)[^\d]+(\d+)[^\d]*(\d*)/,i={width:"100%",height:"100%",id:"_"+(""+Math.random()).slice(9),allowfullscreen:true,allowscriptaccess:"always",quality:"high",version:[3,0],onFail:null,expressInstall:null,w3c:false,cachebusting:false};window.attachEvent&&window.attachEvent("onbeforeunload",function(){__flash_unloadHandler=function(){};__flash_savedUnloadHandler=function(){}});
window.flashembed=function(a,b,c){if(typeof a=="string")a=document.getElementById(a.replace("#",""));if(a){if(typeof b=="string")b={src:b};return new m(a,f(f({},i),b),c)}};var e=f(window.flashembed,{conf:i,getVersion:function(){var a;try{a=navigator.plugins["Shockwave Flash"].description.slice(16)}catch(b){try{var c=new ActiveXObject("ShockwaveFlash.ShockwaveFlash.7");a=c&&c.GetVariable("$version")}catch(d){}}return(a=o.exec(a))?[a[1],a[3]]:[0,0]},asString:function(a){if(a===null||a===undefined)return null;
var b=typeof a;if(b=="object"&&a.push)b="array";switch(b){case "string":a=a.replace(new RegExp('(["\\\\])',"g"),"\\$1");a=a.replace(/^\s?(\d+\.?\d+)%/,"$1pct");return'"'+a+'"';case "array":return"["+l(a,function(d){return e.asString(d)}).join(",")+"]";case "function":return'"function()"';case "object":b=[];for(var c in a)a.hasOwnProperty(c)&&b.push('"'+c+'":'+e.asString(a[c]));return"{"+b.join(",")+"}"}return String(a).replace(/\s/g," ").replace(/\'/g,'"')},getHTML:function(a,b){a=f({},a);var c='<object width="'+
a.width+'" height="'+a.height+'" id="'+a.id+'" name="'+a.id+'"';if(a.cachebusting)a.src+=(a.src.indexOf("?")!=-1?"&":"?")+Math.random();c+=a.w3c||!h?' data="'+a.src+'" type="application/x-shockwave-flash"':' classid="clsid:D27CDB6E-AE6D-11cf-96B8-444553540000"';c+=">";if(a.w3c||h)c+='<param name="movie" value="'+a.src+'" />';a.width=a.height=a.id=a.w3c=a.src=null;a.onFail=a.version=a.expressInstall=null;for(var d in a)if(a[d])c+='<param name="'+d+'" value="'+a[d]+'" />';a="";if(b){for(var j in b)if(b[j]){d=
b[j];a+=j+"="+(/function|object/.test(typeof d)?e.asString(d):d)+"&"}a=a.slice(0,-1);c+='<param name="flashvars" value=\''+a+"' />"}c+="</object>";return c},isSupported:function(a){return g[0]>a[0]||g[0]==a[0]&&g[1]>=a[1]}}),g=e.getVersion();if(n){jQuery.tools=jQuery.tools||{version:"1.2.2"};jQuery.tools.flashembed={conf:i};jQuery.fn.flashembed=function(a,b){return this.each(function(){$(this).data("flashembed",flashembed(this,a,b))})}}})();


;(function() {
function FlashAudio(phono, config, callback) {
    
    // Define defualt config and merge from constructor
    this.config = Phono.util.extend({
        protocol: "rtmfp",
        swf: "//" + window.location.host + "/redfire/phono/plugins/audio/phono.audio.swf",
        cirrus: "rtmfp://" + window.location.hostname,
        direct: true,
        video: false
    }, config);
    
    // Bind Event Listeners
    Phono.events.bind(this, config);
    
    var containerId = this.config.containerId;
    
    // Create flash continer is user did not specify one
    if(!containerId) {
	this.config.containerId = containerId = this.createContainer();
    }
    
    // OMG! Fix position of flash movie to be integer pixel
    Phono.events.bind(this, {
        onPermissionBoxShow: function() {
            var p = $("#"+containerId).position();
            $("#"+containerId).css("left",parseInt(p.left));
            $("#"+containerId).css("top",parseInt(p.top));
        } 
    });		
    
    var plugin = this;
    
    // Flash movie is embedded asynchronously so we need a listener 
    // to fire when the SWF is loaded and ready for action
    FABridge.addInitializationCallback(containerId, function(){
        Phono.log.info("FlashAudio Ready");
        plugin.$flash = this.create("Wrapper").getAudio();
        plugin.$flash.addEventListener(null, function(event) {
            var eventName = (event.getType()+"");
            Phono.events.trigger(plugin, eventName, {
                reason: event.getReason()
            });
            if (eventName == "mediaError") {
                Phono.events.trigger(phono, "error", {
                    reason: event.getReason()
                });
            }
        });
        callback(plugin);
        if (plugin.config.direct) {
            window.setTimeout(10, plugin.$flash.doCirrusConnect(plugin.config.cirrus));
        }
    });

    wmodeSetting = "opaque";
    
    if ((navigator.appVersion.indexOf("X11")!=-1) || (navigator.appVersion.indexOf("Linux")!=-1) || (jQuery.browser.opera)) {
        wmodeSetting = "window";
    }
    
    // Embed flash plugin
    flashembed(containerId, 
               {
                   id:containerId + "id",
                   src:this.config.swf + "?rnd=" + new Date().getTime(),
                   wmode:wmodeSetting
               }, 
               {
                   bridgeName:containerId
               }
              );
};

FlashAudio.count = 0;

// FlashAudio Functions
//
// Most of these will simply pass through to the underlying Flash layer.
// In the old API this was done by 'wrapping' the Flash object. I've chosen a more verbos 
// approach to aid in debugging now that the Flash side has been reduced to a few simple calls.
// =============================================================================================

// Show the Flash Audio permission box
FlashAudio.prototype.showPermissionBox = function() {
    this.$flash.showPermissionBox();
};

// Returns true if the FLash movie has microphone access
FlashAudio.prototype.permission = function() {
    return this.$flash.getHasPermission();
};

// Creates a new Player and will optionally begin playing
FlashAudio.prototype.play = function(transport, autoPlay) {
    url = transport.uri.replace("protocol",this.config.protocol);
    var luri = url;
    var uri = Phono.util.parseUri(url);
    var location = Phono.util.parseUri(document.location);
    
    if (uri.protocol == "rtp") return null;
    if (url.indexOf("//") == 0) {
        luri = location.protocol+":"+url;
    } else if (uri.protocol.length < 2) {
        // We are relative, so use the document.location
        luri = location.protocol+"://"+location.authority+location.directoryPath+url;
    }
    
    var player;
    if (this.config.direct == true && transport.peerID != undefined && this.config.cirrus != undefined) {
        Phono.log.info("Direct media play with peer " + transport.peerID);
        player = this.$flash.play(luri, autoPlay, transport.peerID, this.config.video);
        
    } else {
        Phono.log.info("Red5 media play with url " + luri);    
    	player = this.$flash.play(luri, autoPlay);
    	
    }
    return {
        url: function() {
            return player.getUrl();
        },
        start: function() {
            player.start();
        },
        stop: function() {
            player.stop();
        },
        volume: function(value) {
   	    if(arguments.length === 0) {
   		return player.getVolume();
   	    }
   	    else {
   		player.setVolume(value);
   	    }
        }
    }
};

// Creates a new audio Share and will optionally begin playing
FlashAudio.prototype.share = function(transport, autoPlay, codec) {
    var url = transport.uri.replace("protocol",this.config.protocol);
    var direct = false;
    var peerID = "";
    if (this.config.direct == true && transport.peerID != undefined && this.config.cirrus != undefined) { 
        peerID = transport.peerID;
        Phono.log.info("Direct media share with peer " + peerID);
        
    } else {
        Phono.log.info("Red5 media share with url " + url);    
    }
    
    
    var share = this.$flash.share(url, autoPlay, codec.id, codec.name, codec.rate, true, peerID, this.config.video);
    return {
        // Readonly
        url: function() {
            return share.getUrl();
        },
        codec: function() {
            var codec = share.getCodec();
            return {
                id: codec.getId(),
                name: codec.getName(),
                rate: codec.getRate()
            }
        },
        // Control
        start: function() {
            share.start();
        },
        stop: function() {
            share.stop();
        },
        digit: function(value, duration, audible) {
            share.digit(value, duration, audible);
        },
        // Properties
        gain: function(value) {
   	    if(arguments.length === 0) {
   		return share.getGain();
   	    }
   	    else {
   		share.setGain(value);
   	    }
        },
        mute: function(value) {
   	    if(arguments.length === 0) {
   		return share.getMute();
   	    }
   	    else {
   		share.setMute(value);
   	    }
        },
        suppress: function(value) {
   	    if(arguments.length === 0) {
   		return share.getSuppress();
   	    }
   	    else {
   		share.setSuppress(value);
   	    }
        },
        energy: function(){
            return {
               mic: 0.0 ,
               spk: 0.0
            }
        }
    }
};   

// Returns an object containg JINGLE transport information
FlashAudio.prototype.transport = function() {
    var $flash = this.$flash;
    var config = this.config;
    var cirrus = this.config.cirrus;
    return {
        name: this.$flash.getTransport(),
        description: this.$flash.getDescription(),
        buildTransport: function(direction, j, callback, u, updateCallback, callId) {
            var nearID = "";
            var suffix = callId.length > 16 ? callId.substring(0, 16) : callId;

            if (direction == "offer")
            {
            	var playName = "read" + suffix;
            	var publishName = "write" + suffix;
            
            } else {
            	var playName = "write" + suffix;
            	var publishName = "read" + suffix;        
            }
            
            if (config.direct) {
                nearID = $flash.nearID(cirrus);
            }
            if (nearID != null && nearID != "") {
                j.c('transport',{xmlns:this.name, peerID:nearID}).c('candidate', {rtmpUri:"rtmfp://" + window.location.hostname, playName:playName, publishName:publishName});
            } else {
                j.c('transport',{xmlns:this.name}).c('candidate', {rtmpUri:"rtmp://" + window.location.hostname + "/xmpp", playName:playName, publishName:publishName});
            }
            
            callback();
        },
        processTransport: function(t) {
            var pID = t.attr('peerid');
            var transport;
            
            // If we have a Peer ID, and no other transport, fake one
            if (pID != undefined)
                transport = {input: {uri: "rtmfp://" + window.location.hostname, peerID: pID},  output: {uri: "rtmfp://" + window.location.hostname, peerID: pID}};
                
            t.find('candidate').each(function () {
                transport = { input: {uri: $(this).attr('rtmpuri') + "/" + $(this).attr('playname'),
                                      peerID: pID},   
                              output: {uri: $(this).attr('rtmpuri') + "/" + $(this).attr('publishname'),
                                       peerID: pID} 
                            };
            });
            return transport;
        }
    }
};

// Returns an array of codecs supported by this plugin
FlashAudio.prototype.codecs = function() {
    var result = new Array();
    var codecs = this.$flash.getCodecs();
    Phono.util.each(codecs, function() {
        result.push({
            id: this.getId(),
            name: this.getName(),
            rate: this.getRate()
        });
    });
    return result;
};

// Creates a DIV to hold the Flash movie if one was not specified by the user
FlashAudio.prototype.createContainer = function(phono) {
    
    var flashDiv = $("<div>")
      	.attr("id","_phono-audio-flash" + (FlashAudio.count++))
      	.addClass("phono_FlashHolder")
      	.appendTo("body");
    
    flashDiv.css({
   	"width":"1px",
   	"height":"1px",
   	"position":"absolute",
   	"top":"50%",
   	"left":"50%",
   	"margin-top":"-69px",
   	"margin-left":"-107px",
   	"z-index":"10001",
   	"visibility":"visible"
    });
    
    var containerId = $(flashDiv).attr("id");
    
    Phono.events.bind(this, {
      	onPermissionBoxShow: function() {
	    $("#"+containerId).css({
		"width":"215px",
   		"height":"138px"
	    });
      	},
      	onPermissionBoxHide: function() {
	    $("#"+containerId).css({
		"width":"1px",
   		"height":"1px"
	    });
      	}
    });
    
    return containerId;
    
};      


function JavaAudio(phono, config, callback) {

    if (JavaAudio.exists()){
      // Define defualt config and merge from constructor
      this.config = Phono.util.extend({
          jar: "//" + window.location.host + "/redfire/phono/plugins/audio/phono.audio.jar"
      }  , config);
    
      // Bind Event Listeners
      Phono.events.bind(this, config);
    
      var containerId = this.config.containerId;
    
      // Create applet continer is user did not specify one
      if(!containerId) {
          this.config.containerId = containerId = _createContainer();
      }
    
      var plugin = this;
    
      // Install the applet
      plugin.$applet = _loadApplet(containerId, this.config.jar, callback, plugin);
      window.setInterval(function(){
        var str = "Loading...";
        try { 
         var json = plugin.$applet[0].getJSONStatus();
         if (json){
	   var statusO = eval('(' +json+ ')');
           if (!statusO.userTrust){
             Phono.events.trigger(phono, "error", {
                reason: "Java Applet not trusted by user - cannot continue"
             });
           } else {
             eps = statusO.endpoints;
             if (eps.length >0){
                if ((eps[0].sent > 50) && (eps[0].rcvd == 0)){
                  Phono.events.trigger(phono, "error", {
                    reason: "Java Applet detected firewall."
                  });
                }
                str = "share: "+eps[0].uri ;
                str +=" sent " +eps[0].sent ;
                str +=" rcvd " +eps[0].rcvd ;
                str +=" error " +eps[0].error ;
                Phono.log.debug("[JAVA RTP] "+str);
             }
           } 
         } else {
          Phono.events.trigger(phono, "error", {
            reason: "Java applet did not load."
          });
          Phono.log.debug("[JAVA Load errror] no status returned.");
         }
        } catch (e) {
          Phono.events.trigger(phono, "error", {
            reason: "Can not communicate with Java Applet - perhaps it did not load."
          });
          Phono.log.debug("[JAVA Load error] "+e);
        }
      },25000); 
    } else {
         Phono.events.trigger(phono, "error", {
            reason: "Java not available in this browser."
         });
    }
};

JavaAudio.exists = function() {
    return (navigator.javaEnabled());
}

JavaAudio.count = 0;

// JavahAudio Functions
//
// Most of these will simply pass through to the underlying Java layer.
// =============================================================================================

// Creates a new Player and will optionally begin playing
JavaAudio.prototype.play = function(transport, autoPlay) {
    var url = transport.uri;
    var applet = this.$applet[0];
    var player;
    var luri = url;
    var uri = Phono.util.parseUri(url);
    var location = Phono.util.parseUri(document.location);

    if (uri.protocol == "rtp") return null;
    if (url.indexOf("//") == 0) {
        luri = location.protocol+":"+url;
    } else if (uri.protocol.length < 2) {
        // We are relative, so use the document.location
        luri = location.protocol+"://"+location.authority+location.directoryPath+url;
    }

    if (autoPlay === undefined) autoPlay = false;
    player = applet.play(luri, autoPlay);
    return {
        url: function() {
            return player.getUrl();
        },
        start: function() {
            player.start();
        },
        stop: function() {
            player.stop();
        },
        volume: function() { 
            if(arguments.length === 0) {
   		return player.volume();
   	    }
   	    else {
   		player.volume(value);
   	    }
        }
    }
};

// Creates a new audio Share and will optionally begin playing
JavaAudio.prototype.share = function(transport, autoPlay, codec) {
    var url = transport.uri;
    var applet = this.$applet[0];

    Phono.log.debug("[JAVA share codec ] "+codec.p.pt +" id = "+codec.id);
    var acodec = applet.mkCodec(codec.p, codec.id);
    var share = applet.share(url, acodec, autoPlay);
    return {
        // Readonly
        url: function() {
            return share.getUrl();
        },
        codec: function() {
            var codec = share.getCodec();
            return {
                id: codec.getId(),
                name: codec.getName(),
                rate: codec.getRate()
            }
        },
        // Control
        start: function() {
            share.start();
        },
        stop: function() {
            share.stop();
        },
        digit: function(value, duration, audible) {
            share.digit(value, duration, audible);
        },
        // Properties
        gain: function(value) {
   	    if(arguments.length === 0) {
   		return share.gain();
   	    }
   	    else {
   		share.gain(value);
   	    }
        },
        mute: function(value) {
   	    if(arguments.length === 0) {
   		return share.mute();
   	    }
   	    else {
   		share.mute(value);
   	    }
        },
        suppress: function(value) {
   	    if(arguments.length === 0) {
   		return share.doES();
   	    }
   	    else {
   		share.doES(value);
   	    }
        },
        energy: function(){
            var en = share.energy();
            return {
               mic: Math.floor(Math.max((Math.LOG2E * Math.log(en[0])-4.0),0.0)),
               spk: Math.floor(Math.max((Math.LOG2E * Math.log(en[1])-4.0),0.0))
            }
        }
    }
};   

// We always have java audio permission
JavaAudio.prototype.permission = function() {
    return true;
};

// Returns an object containg JINGLE transport information
JavaAudio.prototype.transport = function() {
    var applet = this.$applet[0];
    var endpoint = applet.allocateEndpoint();
    
    return {
        name: "urn:xmpp:jingle:transports:raw-udp:1",
        description: "urn:xmpp:jingle:apps:rtp:1",
        buildTransport: function(direction, j, callback, u, updateCallback, callId) {
            var uri = Phono.util.parseUri(endpoint);
            j.c('transport',{xmlns:"urn:xmpp:jingle:transports:raw-udp:1"})
                .c('candidate',{ip:uri.domain, port:uri.port, generation:"1"});
            callback();
        },
        processTransport: function(t) {
            var fullUri;
            t.find('candidate').each(function () {
                fullUri = endpoint + ":" + $(this).attr('ip') + ":" + $(this).attr('port');
            });
            return {input:{uri:fullUri}, output:{uri:fullUri}};
        }
    }
};

String.prototype.startsWith = function(str) {
    return (this.match("^"+str)==str)
};

// Returns an array of codecs supported by this plugin
JavaAudio.prototype.codecs = function() {
    var result = new Array();
    var applet = this.$applet[0];
    var codecs = applet.codecs();
    
    for (l=0; l<codecs.length; l++) {
        var name;
        if (codecs[l].name.startsWith("SPEEX")) {name = "SPEEX";}
        else name = codecs[l].name;
        result.push({
            id: codecs[l].pt,
            name: name,
            rate: codecs[l].rate,
            p: codecs[l]
        });
    }
    
    return result;
};

JavaAudio.prototype.audioIn = function(str) {
     var applet = this.$applet[0];
    applet.setAudioIn(str);
}

JavaAudio.prototype.audioInDevices = function(){
    var result = new Array();

    //var applet = this.$applet;
    //var jsonstr = applet.getAudioDeviceList();
    
    //console.log("seeing this.audioDeviceList as "+this.audioDeviceList);
    var devs = eval ('(' +this.audioDeviceList+ ')');
    var mixers = devs.mixers;
    result.push("Let my system choose");
    for (l=0; l<mixers.length; l++) {
        if (mixers[l].targets.length > 0){
            result.push(mixers[l].name );
        }
    }

    return result;
}


// Creates a DIV to hold the capture applet if one was not specified by the user
_createContainer = function() {
    
    var appletDiv = $("<div>")
        .attr("id","_phono-appletHolder" + (JavaAudio.count++))
        .addClass("phono_AppletHolder")
        .appendTo("body");
    
    appletDiv.css({
        "width":"1px",
        "height":"1px",
        "position":"absolute",
        "top":"50%",
        "left":"50%",
   	"margin-top":"-69px",
   	"margin-left":"-107px",
        "z-index":"10001",
        "visibility":"visible"
    });
    
    var containerId = $(appletDiv).attr("id");
    return containerId;
}

_loadApplet = function(containerId, jar, callback, plugin) {
    var id = "_phonoAudio" + (JavaAudio.count++);
    
    var callbackName = id+"Callback";
    
    window[callbackName] = function(devJson) {
            //console.log("Java audio device list json is "+devJson);
            plugin.audioDeviceList = devJson;
            t = window.setTimeout( function () {callback(plugin);},10);
            };
    var applet = $("<applet>")
        .attr("id", id)
        .attr("name",id)
        .attr("code","com.phono.applet.rtp.RTPApplet")
        .attr("archive",jar)
        .attr("width","1px")
        .attr("height","1px")
        .attr("mayscript","true")
        .append($("<param>")
                .attr("name","doEC")
                .attr("value","true")
               )
        .append($("<param>")
                .attr("name","callback")
                .attr("value",callbackName)
               )
        .appendTo("#" + containerId)
    // Firefox 7.0.1 seems to treat the applet object as a function
    // which causes mayhem later on - so we return an array containing it
    // which seems to sheild us from issue.
    return applet; 
};

function PhonegapIOSAudio(phono, config, callback) {
    
    // Bind Event Listeners
    Phono.events.bind(this, config);
    
    var plugin = this;

    this.initState(callback, plugin);
};

PhonegapIOSAudio.exists = function() {
    return ((typeof PhoneGap != "undefined") && Phono.util.isIOS());
}

PhonegapIOSAudio.codecs = new Array();
PhonegapIOSAudio.endpoint = "rtp://0.0.0.0";

PhonegapIOSAudio.prototype.allocateEndpoint = function () {
    PhonegapIOSAudio.endpoint = "rtp://0.0.0.0";
    PhoneGap.exec( 
                  function(result) {console.log("endpoint success: " + result);
                                                    PhonegapIOSAudio.endpoint = result;}, 
                  function(result) {console.log("endpoint fail:" + result);},
                  "Phono","allocateEndpoint",[]);
}

PhonegapIOSAudio.prototype.initState = function(callback, plugin) {

    this.allocateEndpoint();
    PhoneGap.exec( 
                  function(result) {
                      console.log("codec success: " + result);
                      var codecs = jQuery.parseJSON(result);
                      for (l=0; l<codecs.length; l++) {
                          var name;
                          if (codecs[l].name.startsWith("SPEEX")) {name = "SPEEX";}
                          else name = codecs[l].name;
                          PhonegapIOSAudio.codecs.push({
                              id: codecs[l].ptype,
                              name: name,
                              rate: codecs[l].rate,
                              p: codecs[l]
                          });
                      };
                      
                      // We are done with initialisation
                      callback(plugin);
                  }, 
                  function(result) {console.log("codec fail:" + result);},
                  "Phono","codecs",[]
                 );
};

// PhonegapIOSAudio Functions
//
// Most of these will simply pass through to the underlying Phonegap layer.
// =============================================================================================

// Creates a new Player and will optionally begin playing
PhonegapIOSAudio.prototype.play = function(transport, autoPlay) {
    var url = transport.uri;
    var luri = url;
    var uri = Phono.util.parseUri(url);
    var location = Phono.util.parseUri(document.location);

    if (uri.protocol == "rtp") return null;
    if (url.indexOf("//") == 0) {
        luri = location.protocol+":"+url;
    } else if (uri.protocol.length < 2) {
        // We are relative, so use the document.location
        luri = location.protocol+"://"+location.directoryPath.substring(0,location.directoryPath.length)+url;
        luri = encodeURI(luri);
    }

    // Get PhoneGap to create the play
    console.log("play("+luri+","+autoPlay+")");
    PhoneGap.exec( 
                  function(result) {console.log("play success: " + result);},
                  function(result) {console.log("play fail:" + result);},
                  "Phono","play",
                  [{
                      'uri':luri,
                      'autoplay': autoPlay == true ? "YES":"NO"
                  }] );


    return {
        url: function() {
            return luri;
        },
        start: function() {
            console.log("play.start " + luri);
            PhoneGap.exec( 
                          function(result) {console.log("start success: " + result);},
                          function(result) {console.log("start fail:" + result);},
                          "Phono","start",
                          [{
                              'uri':luri
                          }]);
        },
        stop: function() {
            PhoneGap.exec(
                          function(result) {console.log("stop success: " + result);},
                          function(result) {console.log("stop fail:" + result);},
                          "Phono","stop", 
                          [{
                              'uri':luri
                          }]);
        },
        volume: function() { 
            if(arguments.length === 0) {
                
   	    }
   	    else {
   	    }
        }
    }
};

// Creates a new audio Share and will optionally begin playing
PhonegapIOSAudio.prototype.share = function(transport, autoPlay, codec) {
    var url = transport.uri;
    var codecD = ""+codec.name+":"+codec.rate+":"+codec.id;
    // Get PhoneGap to create the share
    PhoneGap.exec( 
                  function(result) {console.log("share success: " + result);},
                  function(result) {console.log("share fail:" + result);},
                  "Phono","share",
                  [{
                      'uri':url,
                      'autoplay': autoPlay == true ? "YES":"NO",
                      'codec':codecD
                  }]);

    var luri = Phono.util.localUri(url);
    var muteStatus = false;
    var gainValue = 50;
    var micEnergy = 0.0;
    var spkEnergy = 0.0;

    // Return a shell of an object
    return {
        // Readonly
        url: function() {
            return url;
        },
        codec: function() {
            var codec;
            return {
                id: codec.getId(),
                name: codec.getName(),
                rate: codec.getRate()
            }
        },
        // Control
        start: function() {
            console.log("share.start " + luri);
            PhoneGap.exec( 
                          function(result) {console.log("start success: " + result);},
                          function(result) {console.log("start fail:" + result);},
                          "Phono","start",
                          [{
                              'uri':luri
                          }]);
        },
        stop: function() {
            PhoneGap.exec(
                          function(result) {console.log("stop success: " + result);},
                          function(result) {console.log("stop fail:" + result);},
                          "Phono","stop", 
                          [{
                              'uri':luri
                          }]);
        },
        digit: function(value, duration, audible) {
            PhoneGap.exec(
                          function(result) {console.log("digit success: " + result);},
                          function(result) {console.log("digit fail:" + result);},
                          "Phono","digit", 
                          [{
                              'uri':luri,
                              'digit':value,
                              'duration':duration,
                              'audible':audible == true ? "YES":"NO"
                          }]
                         );
        },
        // Properties
        gain: function(value) {
   	    if(arguments.length === 0) {
                return gainValue;
   	    }
   	    else {
                PhoneGap.exec( 
                              function(result) {
                                  console.log("gain success: " + result + " " + value);
                                  gainValue = value;
                              },
                              function(result) {console.log("gain fail:" + result);},
                              "Phono","gain",
                              [{
                                  'uri':luri,
                                  'value':value
                              }]
                             );
   	    }
        },
        mute: function(value) {
   	    if(arguments.length === 0) {
                return muteStatus;
   	    }
   	    else {
                PhoneGap.exec( 
                              function(result) {
                                  console.log("mute success: " + result + " " + value);
                                  muteStatus = value;
                              },
                              function(result) {console.log("mute fail:" + result);},
                              "Phono","mute",
                              [{
                                  'uri':luri,
                                  'value':value == true ? "YES":"NO"
                              }]
                             );
   	    }
        },
        suppress: function(value) {
   	    if(arguments.length === 0) {
   	    }
   	    else {
   	    }
        },
        energy: function(){
            PhoneGap.exec(
                        function(result) {
                            console.log("energy success: " + result);
                            var en = jQuery.parseJSON(result);
                            micEnergy = Math.floor(Math.max((Math.LOG2E * Math.log(en[0])-4.0),0.0));
                            spkEnergy = Math.floor(Math.max((Math.LOG2E * Math.log(en[1])-4.0),0.0));
                            },
                        function(result) {console.log("energy fail:" + result);},
                        "Phono","energy",
                        [{'uri':luri}]
            );
            return {
               mic: micEnergy,
               spk: spkEnergy
            }
        }
    }
};   

// We always have phonegap audio permission
PhonegapIOSAudio.prototype.permission = function() {
    return true;
};

// Returns an object containg JINGLE transport information
PhonegapIOSAudio.prototype.transport = function() {
    
    var endpoint = PhonegapIOSAudio.endpoint;
    // We've used this one, get another ready
    this.allocateEndpoint();

    return {
        name: "urn:xmpp:jingle:transports:raw-udp:1",
        description: "urn:xmpp:jingle:apps:rtp:1",
        buildTransport: function(direction, j, callback, u, updateCallback, callId) {
            console.log("buildTransport: " + endpoint);
            var uri = Phono.util.parseUri(endpoint);
            j.c('transport',{xmlns:"urn:xmpp:jingle:transports:raw-udp:1"})
                .c('candidate',{ip:uri.domain, port:uri.port, generation:"1"});
            callback();
        },
        processTransport: function(t) {
            var fullUri;
            t.find('candidate').each(function () {
                fullUri = endpoint + ":" + $(this).attr('ip') + ":" + $(this).attr('port');
            });
            return {input:{uri:fullUri}, output:{uri:fullUri}};
        }
    }
};

String.prototype.startsWith = function(str) {
    return (this.match("^"+str)==str)
};

// Returns an array of codecs supported by this plugin
PhonegapIOSAudio.prototype.codecs = function() {
    return PhonegapIOSAudio.codecs;
};




function PhonegapAndroidAudio(phono, config, callback) {
    
    // Bind Event Listeners
    Phono.events.bind(this, config);

    var plugin = this;

    // Register our Java plugin with Phonegap so that we can call it later
    PhoneGap.exec(null, null, "App", "addService", ['PhonogapAudio', 'com.phono.android.phonegap.Phono']);
    
    // FIXME: Should not have to do this twice!
    this.allocateEndpoint();
    this.initState(callback, plugin);
};

PhonegapAndroidAudio.exists = function() {
    return ((typeof PhoneGap != "undefined") && Phono.util.isAndroid());
}

PhonegapAndroidAudio.codecs = new Array();
PhonegapAndroidAudio.endpoint = "rtp://0.0.0.0";

PhonegapAndroidAudio.prototype.allocateEndpoint = function () {
    
    PhonegapAndroidAudio.endpoint = "rtp://0.0.0.0";

    PhoneGap.exec(function(result) {console.log("endpoint: success");
                                    PhonegapAndroidAudio.endpoint = result.uri;
                                   },
                  function(result) {console.log("endpoint: fail");},
                  "PhonogapAudio",  
                  "allocateEndpoint",              
                  [{}]);      
}

PhonegapAndroidAudio.prototype.initState = function(callback, plugin) {

    this.allocateEndpoint();

    var codecSuccess = function(result) {
        console.log("codec: success");
        var codecs = result.codecs;
        for (l=0; l<codecs.length; l++) {
            var name;
            if (codecs[l].name.startsWith("SPEEX")) {name = "SPEEX";}
            else name = codecs[l].name;
            PhonegapAndroidAudio.codecs.push({
                id: codecs[l].ptype,
                name: name,
                rate: codecs[l].rate,
                p: codecs[l]
            });
        }
        // We are done with initialisation
        callback(plugin);
    }
    
    var codecFail = function(result) {
        console.log("codec:fail");
    }
    
    // Get the codec list
    PhoneGap.exec(codecSuccess,
                  codecFail,
                  "PhonogapAudio",
                  "codecs",
                  [{}]);
};

// PhonegapAndroidAudio Functions
//
// Most of these will simply pass through to the underlying Phonegap layer.
// =============================================================================================

// Creates a new Player and will optionally begin playing
PhonegapAndroidAudio.prototype.play = function(transport, autoPlay) {
    var url = transport.uri;
    var luri = url;
    var uri = Phono.util.parseUri(url);
    var location = Phono.util.parseUri(document.location);

    if (uri.protocol == "rtp") return null;
    if (url.indexOf("//") == 0) {
        luri = location.protocol+":"+url;
    } else if (uri.protocol.length < 2) {
        // We are relative, so use the document.location
        luri = location.protocol+"://"+location.directoryPath.substring(0,location.directoryPath.length)+url;
        luri = encodeURI(luri);
    }

    // Get PhoneGap to create the play
    PhoneGap.exec(function(result) {console.log("play: success");},
                  function(result) {console.log("play: fail");},
                  "PhonogapAudio",  
                  "play",              
                  [{
                      'uri':luri,
                      'autoplay': autoPlay == true ? "YES":"NO"
                  }]);      

    return {
        url: function() {
            return luri;
        },
        start: function() {
            console.log("play.start " + luri);
            PhoneGap.exec(function(result) {console.log("start: success");},
                  function(result) {console.log("start: fail");},
                  "PhonogapAudio",  
                  "start",              
                  [{
                      'uri':luri
                  }]);   
        },
        stop: function() {
            console.log("play.stop " + luri);
            PhoneGap.exec(function(result) {console.log("stop: success");},
                  function(result) {console.log("stop: fail");},
                  "PhonogapAudio",  
                  "stop",              
                  [{
                      'uri':luri
                  }]);   

        },
        volume: function() { 
            if(arguments.length === 0) {
                
   	    }
   	    else {
   	    }
        }
    }
};

// Creates a new audio Share and will optionally begin playing
PhonegapAndroidAudio.prototype.share = function(transport, autoPlay, codec) {
    var url = transport.uri;
    var codecD = ""+codec.name+":"+codec.rate+":"+codec.id;


    // Get PhoneGap to create the share
    PhoneGap.exec(function(result) {console.log("share: success");},
                  function(result) {console.log("share: fail");},
                  "PhonogapAudio",  
                  "share",              
                  [{
                      'uri':url,
                      'autoplay': autoPlay == true ? "YES":"NO",
                      'codec':codecD
                  }]);   

    var luri = Phono.util.localUri(url);
    var muteStatus = false;
    var gainValue = 50;
    var micEnergy = 0.0;
    var spkEnergy = 0.0;


    // Return a shell of an object
    return {
        // Readonly
        url: function() {
            return url;
        },
        codec: function() {
            var codec;
            return {
                id: codec.getId(),
                name: codec.getName(),
                rate: codec.getRate()
            }
        },
        // Control
        start: function() {
            console.log("share.start " + luri);
            PhoneGap.exec(function(result) {console.log("start: success");},
                          function(result) {console.log("start: fail");},
                          "PhonogapAudio",  
                          "start",              
                          [{
                              'uri':luri
                          }]);   
        },
        stop: function() {
            console.log("share.stop " + luri);
            PhoneGap.exec(function(result) {console.log("stop: success");},
                          function(result) {console.log("stop: fail");},
                          "PhonogapAudio",  
                          "stop",              
                          [{
                              'uri':luri
                          }]);   
        },
        digit: function(value, duration, audible) {
            console.log("share.digit " + luri);
            PhoneGap.exec(function(result) {console.log("digit: success");},
                          function(result) {console.log("digit: fail");},
                          "PhonogapAudio",  
                          "digit",              
                          [{
                              'uri':luri,
                              'digit':value,
                              'duration':duration,
                              'audible':audible == true ? "YES":"NO"
                          }]);   
        },
        // Properties
        gain: function(value) {
   	    if(arguments.length === 0) {
                return gainValue;
   	    }
   	    else {
                console.log("share.gain " + luri);
                PhoneGap.exec(function(result) {console.log("gain: success");},
                              function(result) {console.log("gain: fail");},
                              "PhonogapAudio",  
                              "gain",              
                              [{
                                  'uri':luri,
                                  'value':value
                              }]);   
   	    }
        },
        mute: function(value) {
   	    if(arguments.length === 0) {
                return muteStatus;
   	    }
   	    else {
                console.log("share.mute " + luri);
                PhoneGap.exec(function(result) {console.log("mute: success");},
                              function(result) {console.log("mute: fail");},
                              "PhonogapAudio",  
                              "mute",              
                              [{
                                  'uri':luri,
                                  'value':value == true ? "YES":"NO"
                              }]);   
   	    }
        },
        suppress: function(value) {
   	    if(arguments.length === 0) {
   	    }
   	    else {
   	    }
        },
        energy: function(){
            PhoneGap.exec(
                        function(result) {
                            console.log("energy success: " + result);
                            var en = jQuery.parseJSON(result);
                            if(en != null) {
                              micEnergy = Math.floor(Math.max((Math.LOG2E * Math.log(en.mic)-4.0),0.0));
                              spkEnergy = Math.floor(Math.max((Math.LOG2E * Math.log(en.spk)-4.0),0.0)); 
			    }
                          },
                        function(result) {console.log("energy fail:" + result);},
                        "PhonogapAudio","energy",
                        [{'uri':luri}]
            );
            return {
               mic: micEnergy,
               spk: spkEnergy
            }
        }
    }
};   

// We always have phonegap audio permission
PhonegapAndroidAudio.prototype.permission = function() {
    return true;
};

// Returns an object containg JINGLE transport information
PhonegapAndroidAudio.prototype.transport = function() {
    
    var endpoint = PhonegapAndroidAudio.endpoint;
    // We've used this one, get another ready
    this.allocateEndpoint();

    return {
        name: "urn:xmpp:jingle:transports:raw-udp:1",
        description: "urn:xmpp:jingle:apps:rtp:1",
        buildTransport: function(direction, j, callback, u, updateCallback, callId) {
            console.log("buildTransport: " + endpoint);
            var uri = Phono.util.parseUri(endpoint);
            j.c('transport',{xmlns:"urn:xmpp:jingle:transports:raw-udp:1"})
                .c('candidate',{ip:uri.domain, port:uri.port, generation:"1"});
            callback();
        },
        processTransport: function(t) {
            var fullUri;
            t.find('candidate').each(function () {
                fullUri = endpoint + ":" + $(this).attr('ip') + ":" + $(this).attr('port');
            });
            return {input:{uri:fullUri}, output:{uri:fullUri}};
        }
    }
};

String.prototype.startsWith = function(str) {
    return (this.match("^"+str)==str)
};

// Returns an array of codecs supported by this plugin
PhonegapAndroidAudio.prototype.codecs = function() {
    return PhonegapAndroidAudio.codecs;
};


function WebRTCAudio(phono, config, callback) {

    console.log("Initialize WebRTC");

    if (typeof webkitPeerConnection00 == "function") {
        WebRTCAudio.peerConnection = webkitPeerConnection00;
    } else {
        WebRTCAudio.peerConnection = webkitPeerConnection;
    }

    this.config = Phono.util.extend({
        media: {audio:true,video:false}
    }, config);
    
    var plugin = this;
    
    var localContainerId = this.config.localContainerId;
    var remoteContainerId = this.config.remoteContainerId;

    // Create audio continer if user did not specify one
    if(!localContainerId) {
	this.config.localContainerId = this.createContainer();
    }
    if(!remoteContainerId) {
	this.config.remoteContainerId = this.createContainer();
    }

    WebRTCAudio.remoteVideo = document.getElementById(this.config.remoteContainerId);
    WebRTCAudio.localVideo = document.getElementById(this.config.localContainerId);

    try { 
        console.log("Request access to local media, use new syntax");
        navigator.webkitGetUserMedia(this.config.media, 
                                     function(stream) {
                                         WebRTCAudio.localStream = stream;
                                         console.log("We have a stream");
                                         var url = webkitURL.createObjectURL(stream);
                                         WebRTCAudio.localVideo.style.opacity = 1;
                                         WebRTCAudio.localVideo.src = url;
                                         callback(plugin);
                                     },
                                     function(error) {
                                         console.log("Failed to get access to local media. Error code was " + error.code);
                                         alert("Failed to get access to local media. Error code was " + error.code + ".");   
                                     });
    } catch (e) {
        console.log("getUserMedia error, try old syntax");
        navigator.webkitGetUserMedia("audio", 
                                     function(stream) {
                                         WebRTCAudio.localStream = stream;
                                         console.log("We have a stream");
                                         var url = webkitURL.createObjectURL(stream);
                                         WebRTCAudio.localVideo.style.opacity = 1;
                                         WebRTCAudio.localVideo.src = url;
                                         callback(plugin);
                                     },
                                     function(error) {
                                         console.log("Failed to get access to local media. Error code was " + error.code);
                                         alert("Failed to get access to local media. Error code was " + error.code + ".");   
                                     });    
    }
}

WebRTCAudio.exists = function() {
    return (typeof webkitPeerConnection00 == "function")|| (typeof webkitPeerConnection == "function");
}

WebRTCAudio.localStream = null;
WebRTCAudio.localOffer = null;
WebRTCAudio.remoteOffer = null;
WebRTCAudio.pc = null
WebRTCAudio.stun = "STUN stun.l.google.com:19302";
WebRTCAudio.count = 0;
WebRTCAudio.started = false;
WebRTCAudio.direction = "answer";

// WebRTCAudio Functions
//
// =============================================================================================

// Creates a new Player and will optionally begin playing
WebRTCAudio.prototype.play = function(transport, autoPlay) {
    var url = transport.uri;
    var luri = url;
    var audioPlayer = null;
    
    return {
        url: function() {
            return luri;
        },
        start: function() {
            if (audioPlayer != null) {
                $(audioPlayer).remove();
            }
            audioPlayer = $("<audio>")
      	        .attr("id","_phono-audioplayer-webrtc" + (WebRTCAudio.count++))
                .attr("autoplay","autoplay")
                .attr("src",url)
                .attr("loop","loop")
      	        .appendTo("body");
        },
        stop: function() {
            $(audioPlayer).remove();
            audioPlayer = null;
        },
        volume: function() { 
        }
    }
};

// Creates a new audio Share and will optionally begin playing
WebRTCAudio.prototype.share = function(transport, autoPlay, codec) {
    var url = transport.uri;
    var share;
    var localStream;  

    return {
        // Readonly
        url: function() {
            return null;
        },
        codec: function() {
            return null;
        },
        // Control
        start: function() {
            // Start - we already have done...
        },
        stop: function() {
            // Stop
            console.log("Closing PeerConnection");
            if (WebRTCAudio.pc != null) {
                WebRTCAudio.pc.close();
                WebRTCAudio.pc = null;
		WebRTCAudio.localOffer = null;
		WebRTCAudio.remoteOffer = null;
		WebRTCAudio.started = false;
		WebRTCAudio.direction = "answer";		
            } 
            WebRTCAudio.remoteVideo.style.opacity = 0;
        },
        digit: function(value, duration, audible) {
            // No idea how to do this yet
        },
        // Properties
        gain: function(value) {
            return null;
        },
        mute: function(value) {
            return null;
        },
        suppress: function(value) {
            return null;
        },
        energy: function(){        
            return {
               mic: 0.0,
               spk: 0.0
            }
        }
    }
};   

// Do we have WebRTC permission? 
WebRTCAudio.prototype.permission = function() {
    return true;
};

// Returns an object containg JINGLE transport information
WebRTCAudio.prototype.transport = function() {
    
    return {
        name: "http://phono.com/webrtc/transport",
        description: "http://phono.com/webrtc/description",
        buildTransport: function(direction, j, callback, u, updateCallback, callId) 
        {
            WebRTCAudio.direction = direction;
            
            if (direction == "answer") {
                // We are the result of an inbound call, so provide answer
                if (WebRTCAudio.pc != null) {
                    WebRTCAudio.pc.close();
                    WebRTCAudio.pc = null;
                }
                
		WebRTCAudio.started = false;
		
                WebRTCAudio.pc = new WebRTCAudio.peerConnection(WebRTCAudio.stun,
		  function(candidate, bMore) {

			if (bMore) {
			    WebRTCAudio.localOffer.addCandidate(candidate);

			} else {

				if (WebRTCAudio.started == false)	 // incoming call step 3 - send SDP to remote
				{
					j.c('transport',{xmlns:"http://phono.com/webrtc/transport"})
                                         .c('webrtc', WebRTCAudio.localOffer.toSdp());
                                         
					WebRTCAudio.started = true;
					
			    		callback();					
				}
			}
		  }
		 );
                
                WebRTCAudio.pc.onaddstream = function(event) { // incoming call step 2 - set local SDP, wait for ICE to fire
                    console.log("Remote stream added.");
                    console.log("Local stream is: " + WebRTCAudio.localStream);
                    var url = webkitURL.createObjectURL(event.stream);
                    WebRTCAudio.remoteVideo.style.opacity = 1;
                    WebRTCAudio.remoteVideo.src = url;
                    
		    WebRTCAudio.localOffer = WebRTCAudio.pc.createAnswer(WebRTCAudio.remoteOffer.toSdp(), {has_audio: true, has_video: false});
		    WebRTCAudio.pc.setLocalDescription(WebRTCAudio.pc.SDP_ANSWER, WebRTCAudio.localOffer);
		    WebRTCAudio.pc.startIce();
		
                };
                WebRTCAudio.pc.onremovestream = function(event) {
                    conole.log("Remote stream removed.");
                };

                WebRTCAudio.pc.addStream(WebRTCAudio.localStream);   
                
		// incoming call step 1b - set remote SDP, wait for media to be added

		WebRTCAudio.pc.setRemoteDescription(WebRTCAudio.pc.SDP_OFFER, WebRTCAudio.remoteOffer);	                 

		
            } else {
                // We are creating an outbound call
                if (WebRTCAudio.pc != null) {
                    WebRTCAudio.pc.close();
                    WebRTCAudio.pc = null;                    
                }
                
		WebRTCAudio.started = false;

                WebRTCAudio.pc = new WebRTCAudio.peerConnection(WebRTCAudio.stun,
		  function(candidate,bMore) {

			if (bMore) {
			    WebRTCAudio.localOffer.addCandidate(candidate);

			} else {

				if (WebRTCAudio.started == false) // outgoing call step 2 - set SDP to remote
				{
					j.c('transport',{xmlns:"http://phono.com/webrtc/transport"})
                                         .c('webrtc', WebRTCAudio.localOffer.toSdp());
                                         
					WebRTCAudio.started = true;
					
			    		callback();					
				}				
			}								
		  }
		 );		 
                WebRTCAudio.pc.onaddstream = function(event) {
                    console.log("Remote stream added.");
                    console.log("Local stream is: " + WebRTCAudio.localStream);
                    
                    var url = webkitURL.createObjectURL(event.stream);
                    WebRTCAudio.remoteVideo.style.opacity = 1;
                    WebRTCAudio.remoteVideo.src = url;
                };

                
                WebRTCAudio.pc.addStream(WebRTCAudio.localStream);
                
                // outgoing call step 1 - set local SDP, wait for ICE to fire
                
                WebRTCAudio.localOffer = WebRTCAudio.pc.createOffer({has_audio: true, has_video: false});
		WebRTCAudio.pc.setLocalDescription(WebRTCAudio.pc.SDP_OFFER, WebRTCAudio.localOffer);
		WebRTCAudio.pc.startIce();
		
                console.log("Created PeerConnection for new OUTBOUND CALL");
            }
        },
        processTransport: function(t) {
            var webrtc;
            var sdp;
            
            t.find('webrtc').each(function () {
                sdp = this.textContent;
                console.log("S->C SDP: " + sdp);                
            });
            
	    if (WebRTCAudio.direction == "answer") { // incoming call step 1a - set remote SDP

		WebRTCAudio.started = false;
	    	WebRTCAudio.remoteOffer = new SessionDescription(sdp);   	
	    
	    } else {	// outgoing call step 3 - set remote SDP, wait for media to be added

		var answer = new SessionDescription(sdp);
		WebRTCAudio.pc.setRemoteDescription(WebRTCAudio.pc.SDP_ANSWER, answer);	    	    
	    }
            
            return {input:{uri:"webrtc"}, output:{uri:"webrtc"}};
        }
    }
};

// Returns an array of codecs supported by this plugin
// Hack until we get capabilities support
WebRTCAudio.prototype.codecs = function() {
    var result = new Array();
    result.push({
        id: 1,
        name: "webrtc",
        rate: 16000,
        p: 20
    });
    return result;
};

WebRTCAudio.prototype.audioInDevices = function(){
    var result = new Array();
    return result;
}

// Creates a DIV to hold the video element if not specified by the user
WebRTCAudio.prototype.createContainer = function() {
    var webRTC = $("<video>")
      	.attr("id","_phono-audio-webrtc" + (WebRTCAudio.count++))
        .attr("autoplay","autoplay").css('display', 'none')
      	.appendTo("body");

    var containerId = $(webRTC).attr("id");       
    return containerId;
};      


    Phono.registerPlugin("audio", {
        
        create: function(phono, config, callback) {
            config = Phono.util.extend({
                type: "auto"
            }, config);
            
            // What are we going to create? Look at the config...
            if (config.type === "java") {
                return Phono.util.loggify("JavaAudio", new JavaAudio(phono, config, callback));                
                
            } else if (config.type === "phonegap-ios") {
                return Phono.util.loggify("PhonegapIOSAudio", new PhonegapIOSAudio(phono, config, callback));
                
            } else if (config.type === "phonegap-android") {
                return Phono.util.loggify("PhonegapAndroidAudio", new PhonegapAndroidAudio(phono, config, callback));
                
            } else if (config.type === "flash") {
                return Phono.util.loggify("FlashAudio", new FlashAudio(phono, config, callback));

            } else if (config.type === "webrtc") {
                return Phono.util.loggify("WebRTCAudio", new WebRTCAudio(phono, config, callback));
                
            } else if (config.type === "none") {
                window.setTimeout(callback,10);
                return null;
                
            } else if (config.type === "auto") {
                
                console.log("Detecting Audio Plugin");
                
                if (PhonegapIOSAudio.exists())  { 
                    console.log("Detected iOS"); 
                    return Phono.util.loggify("PhonegapIOSAudio", new PhonegapIOSAudio(phono, config, callback));
                    
                } else if (PhonegapAndroidAudio.exists()) { 
                    console.log("Detected Android"); 
                    return Phono.util.loggify("PhonegapAndroidAudio", new PhonegapAndroidAudio(phono, config, callback));
                } else { 
                    console.log("Detected Flash"); 
                    return Phono.util.loggify("FlashAudio", new FlashAudio(phono, config, callback));
                    
                }
            }
        }
    });
      
})();
;(function() {

   function Message(connection) {
      this.from = null;
      this.body = null;
      this.connection = connection;
   };
   
   Message.prototype.reply = function(body) {
      this.connection.send($msg({to:this.from, type:"chat"}).c("body").t(body));
   };

   function StropheMessaging(phono, config, callback) {
      
      this.connection = phono.connection;
      
      this.connection.addHandler(
         this.handleMessage.bind(this), 
         null, "message", "chat"
      );
      
      Phono.events.bind(this, config);
      
      callback(this);
   };
   
   StropheMessaging.prototype.send = function(to, body) {
      this.connection.send($msg({to:to, type:"chat"}).c("body").t(body));
   };

   StropheMessaging.prototype.handleMessage = function(msg) {
      var message = new Message(this.connection);
      message.from = Strophe.getBareJidFromJid($(msg).attr("from"));
      message.body = $(msg).find("body").text();
      Phono.events.trigger(this, "message", {
         message: message
      }, [message]);
      return true;
   };
   
   Phono.registerPlugin("messaging", {
      create: function(phono, config, callback) {
         return new StropheMessaging(phono, config, callback);
      }
   });
      
})();

;(function() {

   Strophe.addNamespace('JINGLE', "urn:xmpp:jingle:1");
   Strophe.addNamespace('JINGLE_SESSION_INFO',"urn:xmpp:jingle:apps:rtp:1:info");

   var CallState = {
       CONNECTED: 0,
       RINGING: 1,
       DISCONNECTED: 2,
       PROGRESS: 3,
       INITIAL: 4
   };

   var Direction = {
       OUTBOUND: 0,
       INBOUND: 1
   };
   
   // Call
   //
   // A Call is the central object in the Phone API. Calls are started
   // using the Phone's dial function or by answering an incoming call.
   // =================================================================
   
   function Call(phone, id, direction, config) {

      var call = this;
      
      // TODO: move out to factory method
      this.phone = phone;
      this.phono = phone.phono;
      this.audioLayer = this.phono.audio;
      this.transport = this.audioLayer.transport();
      this.connection = this.phono.connection;
      
      this.config = Phono.util.extend({
         pushToTalk: false,
         mute: false,
         talking: false,
         hold: false,
         volume: 50,
         gain: 50,
         tones: false,
         codecs: phone.config.codecs
      }, config);
      
      // Apply config
      Phono.util.each(this.config, function(k,v) {
         if(typeof call[k] == "function") {
            call[k](v);
         }
      });
            
      this.id = id;
      this.direction = direction;
      this.state = CallState.INITIAL;  
      this.remoteJid = null;
      this.initiator = null;
      this.codec = null;

      this.headers = [];
      
      if(this.config.headers) {
         this.headers = this.config.headers;
      }
      
      // Bind Event Listeners
      Phono.events.bind(this, config);
      
       this.ringer = this.audioLayer.play({uri:phone.ringTone()}); 
       this.ringback = this.audioLayer.play({uri:phone.ringbackTone()});
      if (this.audioLayer.audioIn){
         this.audioLayer.audioIn(phone.audioInput());
      }
      
   };
   
   Call.prototype.startAudio = function(iq) {
      if(this.input) {
         this.input.start();
      }
      if(this.output) {
         this.output.start();
      }
   };
   
   Call.prototype.stopAudio = function(iq) {
      if(this.input) {
         this.input.stop();
      }
      if(this.output) {
         this.output.stop();
      }
   };
   
   Call.prototype.start = function() {
      
      var call = this;
      
      if (call.state != CallState.INITIAL) return;
       
      var initiateIq = $iq({type:"set", to:call.remoteJid});
      
      var initiate = initiateIq.c('jingle', {
         xmlns: Strophe.NS.JINGLE,
         action: "session-initiate",
         initiator: call.initiator,
         sid: call.id
      });
                     
      $(call.headers).each(function() {
         initiate.c("custom-header", {name:this.name, data:this.value}).up();
      });
             
       var partialInitiate = initiate
           .c('content', {creator:"initiator"})
           .c('description', {xmlns:this.transport.description})
       
       Phono.util.each(this.config.codecs(Phono.util.filterWideband(this.audioLayer.codecs(),this.phone.wideband())), function() {
           partialInitiate = partialInitiate.c('payload-type', {
               id: this.id,
               name: this.name,
               clockrate: this.rate
           }).up();           
       });

       var updateIq = $iq({type:"set", to:call.remoteJid});
       
       var update = updateIq.c('jingle', {
           xmlns: Strophe.NS.JINGLE,
           action: "transport-accept",
           initiator: call.initiator,
           sid: call.id
       });
       
       var partialUpdate = update
           .c('content', {creator:"initiator"})
           .c('description', {xmlns:this.transport.description})
       
       this.transport.buildTransport("offer", partialInitiate.up(), 
                                     function() {
                                         call.connection.sendIQ(initiateIq, function (iq) {
                                             call.state = CallState.PROGRESS;
                                         });
                                     },
                                     partialUpdate.up(),
                                     function() {
                                         call.connection.sendIQ(updateIq, function (iq) {
                                         });   
                                     }, call.id
                                    );

   };
   
   Call.prototype.accept = function() {

      var call = this;

      if (call.state != CallState.PROGRESS) return;
      
      var jingleIq = $iq({
         type: "set", 
         to: call.remoteJid})
         .c('jingle', {
            xmlns: Strophe.NS.JINGLE,
            action: "session-info",
            initiator: call.initiator,
            sid: call.id})
         .c('ringing', {
            xmlns:Strophe.NS.JINGLE_SESSION_INFO}
      );
         
      this.connection.sendIQ(jingleIq, function (iq) {
          call.state = CallState.RINGING;
          Phono.events.trigger(call, "ring");
      });

   };
   
   Call.prototype.answer = function() {
      
      var call = this;
      
      if (call.state != CallState.RINGING 
      && call.state != CallState.PROGRESS) return;

       var acceptIq = $iq({type:"set", to:call.remoteJid});
      
       var accept = acceptIq.c('jingle', {
           xmlns: Strophe.NS.JINGLE,
           action: "session-accept",
           initiator: call.initiator,
           sid: call.id
       });
       
       var partialAccept = accept
           .c('content', {creator:"initiator"})
           .c('description', {xmlns:this.transport.description});
       
       partialAccept = partialAccept.c('payload-type', {
           id: call.codec.id,
           name: call.codec.name,
           clockrate: call.codec.rate
       }).up();           

       $.each((call.audioLayer.codecs()), function() {
           if (this.name == "telephone-event") {
               partialAccept = partialAccept.c('payload-type', {
                   id: this.id,
                   name: this.name,
                   clockrate: this.rate
               }).up();     
           } 
       });
       
       var updateIq = $iq({type:"set", to:call.remoteJid});
      
       var update = updateIq.c('jingle', {
           xmlns: Strophe.NS.JINGLE,
           action: "transport-replace",
           initiator: call.initiator,
           sid: call.id
       });
       
       var partialUpdate = update
           .c('content', {creator:"initiator"})
           .c('description', {xmlns:this.transport.description})

       this.transport.buildTransport("answer", partialAccept.up(), 
                                     function(){
                                         call.connection.sendIQ(acceptIq, function (iq) {
                                             call.state = CallState.CONNECTED;
                                             Phono.events.trigger(call, "answer");
                                             if (call.ringer != null) call.ringer.stop();
                                             call.startAudio();
                                         });
                                     },
                                     partialUpdate.up(),
                                     function() {
                                         call.connection.sendIQ(updateIq, function (iq) {
                                         });   
                                     }, call.id);
   };

   Call.prototype.bindAudio = function(binding) {
      this.input = binding.input;
      this.output = binding.output;
      this.volume(this.volume());
      this.gain(this.gain());
      this.mute(this.mute());
      this.hold(this.hold());
      this.headset(this.headset());
      this.pushToTalkStateChanged();
   };
   
   Call.prototype.hangup = function() {

      var call = this;
      
      if (call.state != CallState.CONNECTED 
       && call.state != CallState.RINGING 
       && call.state != CallState.PROGRESS) return;
      
      var jingleIq = $iq({
         type:"set", 
         to:call.remoteJid})
         .c('jingle', {
            xmlns: Strophe.NS.JINGLE,
            action: "session-terminate",
            initiator: call.initiator,
            sid: call.id}
      );
      
      this.connection.sendIQ(jingleIq, function (iq) {
          call.state = CallState.DISCONNECTED;
          Phono.events.trigger(call, "hangup");
          call.stopAudio();
          if (call.ringer != null) call.ringer.stop();
          if (call.ringback != null) call.ringback.stop();          
      });
      
   };
   
   Call.prototype.digit = function(value, duration) {
      if(!duration) {
         duration = 349;
      }
      this.output.digit(value, duration, this._tones);
   };
   
   Call.prototype.pushToTalk = function(value) {
   	if(arguments.length === 0) {
   	    return this._pushToTalk;
   	}
   	this._pushToTalk = value;
   	this.pushToTalkStateChanged();
   };

   Call.prototype.talking = function(value) {
   	if(arguments.length === 0) {
   	    return this._talking;
   	}
   	this._talking = value;
   	this.pushToTalkStateChanged();
   };

   Call.prototype.mute = function(value) {
   	if(arguments.length === 0) {
   	    return this._mute;
   	}
   	this._mute = value;
   	if(this.output) {
      	this.output.mute(value);
   	}
   };

   // TODO: hold should be implemented in JINGLE
   Call.prototype.hold = function(hold) {
      
   };

   Call.prototype.volume = function(value) {
   	if(arguments.length === 0) {
   	    return this._volume;
   	}
   	this._volume = value;
   	if(this.input) {
   	   this.input.volume(value);
   	}
   };

   Call.prototype.tones = function(value) {
   	if(arguments.length === 0) {
   	    return this._tones;
   	}
	   this._tones = value;
   };

   Call.prototype.gain = function(value) {
   	if(arguments.length === 0) {
   	    return this._gain;
   	}
   	this._gain = value;
   	if(this.output) {
   	   this.output.gain(value);
   	}
   };

   Call.prototype.energy = function() {
   	if(this.output) {
   	   ret = this.output.energy();
   	}
	return ret;
   };

   Call.prototype.headset = function(value) {
   	if(arguments.length === 0) {
   	    return this._headset;
   	}
   	this._headset = value;
   	if(this.output) {
   	   this.output.suppress(!value);
   	}
   };
   
	Call.prototype.pushToTalkStateChanged = function() {
	   if(this.input && this.output) {
   		if (this._pushToTalk) {
   			if (this._talking) {
   				this.input.volume(20);
   				this.output.mute(false);
   			} else {
   				this.input.volume(this._volume);
   				this.output.mute(true);
   			}
   		} else {
   			this.input.volume(this._volume);
   			this.output.mute(false);
   		}
	   }
	};
   
   Call.prototype.negotiate = function(iq) {

      var call = this;

      // Find a matching audio codec
      var description = $(iq).find('description');
      var codec = null;
      description.find('payload-type').each(function () {
         var codecName = $(this).attr('name');
         var codecRate = $(this).attr('clockrate');
          var codecId = $(this).attr('id');
          $.each(call.config.codecs(Phono.util.filterWideband(call.audioLayer.codecs(),call.phone.wideband())), function() {
             if ((this.name == codecName && this.rate == codecRate && this.name != "telephone-event") || (parseInt(this.id) < 90 && this.id == codecId)) {
                 if (codec == null) codec = {id: codecId , name:this.name,  rate: this.rate, p: this.p};
                 return false;
            } 
         });
      });
      
      // No matching codec
      if (!codec) {
          Phono.log.error("No matching jingle codec (not a problem if using WebRTC)");
          // Voodoo up a temporary codec as a placeholder
          codec = {
              id: 1,
              name: "webrtc-ulaw",
              rate: 8000,
              p: 20
          };
      }
       
      // Find a matching media transport
      var foundTransport = false;
      $(iq).find('transport').each(function () {
          if (call.transport.name == $(this).attr('xmlns') && foundTransport == false) {
              var transport = call.transport.processTransport($(this));      
              if (transport != undefined) {
                  call.bindAudio({
                      input: call.audioLayer.play(transport.input, false),
                      output: call.audioLayer.share(transport.output, false, codec)
                  });
                  foundTransport = true;
              } else {
                  Phono.log.error("No valid candidate in transport");
              }
          }
      });

      if (foundTransport == false) {
          Phono.log.error("No matching valid transport");
          return null;
      }
      return codec;
       
   };

   // Phone
   //
   // A Phone is created automatically with each Phono instance. 
   // Basic Phone allows setting  ring tones,  ringback tones, etc.
   // =================================================================

   function Phone(phono, config, callback) {

      var phone = this;
      this.phono = phono;
      this.connection = phono.connection;
      
      // Initialize call hash
      this.calls = {};

      // Initial state
      this._wideband = true;

      // Define defualt config and merge from constructor
      this.config = Phono.util.extend({
         audioInput: "System Default",
         ringTone: "//s.phono.com/ringtones/Diggztone_Marimba.mp3",
         ringbackTone: "//s.phono.com/ringtones/ringback-us.mp3",
         wideband: true,
         headset: false,
         codecs: function(offer) {return offer;}
      }, config);
      
      // Apply config
      Phono.util.each(this.config, function(k,v) {
         if(typeof phone[k] == "function") {
            phone[k](v);
         }
      });
      
      // Bind Event Listeners
      Phono.events.bind(this, config);
      
      // Register Strophe handler for JINGLE messages
      this.connection.addHandler(
         this.doJingle.bind(this), 
         Strophe.NS.JINGLE, "iq", "set"
      );
      
      callback(this);

   };
   
   Phone.prototype.doJingle = function(iq) {
      
      var phone = this;
      var audioLayer = this.phono.audio;
      
      var jingle = $(iq).find('jingle');
      var action = jingle.attr('action') || "";
      var id = jingle.attr('sid') || "";
      var call = this.calls[id] || null;
      
      switch(action) {
         
         // Inbound Call
         case "session-initiate":
         
            call = Phono.util.loggify("Call", new Call(phone, id, Direction.INBOUND));
            call.phone = phone;
            call.remoteJid = $(iq).attr('from');
            call.initiator = jingle.attr('initiator');
            
            // Register Call
            phone.calls[call.id] = call;

            // Negotiate SDP
            call.codec = call.negotiate(iq);
            if(call.codec == null) {
               Phono.log.warn("Failed to negotiate incoming call", iq);
            }
            
            // Get incoming headers
            call.headers = new Array();
            jingle.find("custom-header").each(function() {
               call.headers.push({
                  name:$(this).attr("name"),
                  value:$(this).attr("data")
               });
            });

            // Start ringing
            if (call.ringer != null) call.ringer.start();
            
            // Auto accept the call (i.e. send ringing)
            call.state = CallState.PROGRESS;
            call.accept();

            // Fire imcoming call event
            Phono.events.trigger(this, "incomingCall", {
               call: call
            });
          
            // Get microphone permission if we are going to need it
            if(!audioLayer.permission()) {
                Phono.events.trigger(audioLayer, "permissionBoxShow");
            }
                        
            break;
            
         // Accepted Outbound Call
         case "session-accept":
         
            // Negotiate SDP
            call.codec = call.negotiate(iq);
            if(call.codec == null) {
                Phono.log.warn("Failed to negotiate outbound call", iq);
            }

            call.state = CallState.CONNECTED;

            // Stop ringback
            if (call.ringback != null) call.ringback.stop();

            // Connect audio streams
            call.startAudio();

            // Fire answer event
            Phono.events.trigger(call, "answer")
            
            break;

         // Transport information update
         case "transport-replace":
         case "transport-accept":
            call.transport.processTransport($(iq));
            break;

         // Hangup
         case "session-terminate":
            
            call.state = CallState.DISCONNECTED;
            
            call.stopAudio();
            if (call.ringer != null) call.ringer.stop();
            if (call.ringback != null) call.ringback.stop();
            
            // Fire hangup event
            Phono.events.trigger(call, "hangup")
            
            break;
            
         // Ringing
         case "session-info":
         
            if ($(iq).find('ringing')) {
               call.state = CallState.RINGING;
               if (call.ringback != null) call.ringback.start();
               Phono.events.trigger(call, "ring")
            }
            
            break;
      }

      // Send Reply
      this.connection.send(
         $iq({
            type: "result", 
             id: $(iq).attr('id'),
             to:call.remoteJid
         })
      );
      
      return true;      
   };
   
   Phone.prototype.dial = function(to, config) {
      
      //Generate unique ID
      var id = Phono.util.guid();

      // Configure Call properties inherited from Phone
      config = Phono.util.extend({
         headset: this.headset()
      }, (config || {}));

      // Create and configure Call
      var call = new Phono.util.loggify("Call", new Call(this, id, Direction.OUTBOUND, config));
      call.phone = this;
      call.remoteJid = to;
      call.initiator = this.connection.jid;

      // Give platform a chance to fix up 
      // the destination and add headers
      this.beforeDial(call);

      // Register call
      this.calls[call.id] = call;

      // Kick off JINGLE invite
      call.start();
      
      return call;
   };
   
   Phone.prototype.beforeDial = function(call) {
      var to = call.remoteJid;
      
      if(to.match("^sip:")) {
         call.remoteJid = Phono.util.escapeXmppNode(to.substr(4)) + "@openlink." + window.location.hostname;
      }
      else if(to.match("^xmpp:")) {
         call.remoteJid = to.substr(5); 
      }
      else if(to.match("^app:")) {
         call.remoteJid = Phono.util.escapeXmppNode(to.substr(4)) + "@openlink." + window.location.hostname;
      }
      else if(to.match("^tel:")) {
         call.remoteJid = Phono.util.escapeXmppNode(to.substr(4)) + "@openlink." + window.location.hostname;
      }
      else {
         var number = to.replace(/[\(\)\-\.\ ]/g, '');
         
         if(number.match(/^\+?\d+$/)) {
            call.remoteJid = number + "@openlink." + window.location.hostname;
         }
         else if(to.indexOf("@") > 0) {
             call.remoteJid = Phono.util.escapeXmppNode(to) + "@openlink." + window.location.hostname;
         
         } else call.remoteJid = to + "@openlink." + window.location.hostname;
      }
   };

   Phone.prototype.audioInput = function(value) {
      if(arguments.length == 0) {
         return this._audioInput;
      }
      this._audioInput = value;
   };
   
   Phone.prototype.audioInDevices = function(){
       var audiolayer = this.phono.audio;
       var ret = new Object();
       if (audiolayer.audioInDevices){
           ret = audiolayer.audioInDevices();
       }
       return ret;
   }

   Phone.prototype.ringTone = function(value) {
      if(arguments.length == 0) {
         return this._ringTone;
      }
      this._ringTone = value;
   };

   Phone.prototype.ringbackTone = function(value) {
      if(arguments.length == 0) {
         return this._ringbackTone;
      }
      this._ringbackTone = value;
   };

   Phone.prototype.headset = function(value) {
      if(arguments.length == 0) {
         return this._headset;
      }
      this._headset = value;
      Phono.util.each(this.calls, function() {
        this.headset(value);
      });
   };

   Phone.prototype.wideband = function(value) {
      if(arguments.length == 0) {
         return this._wideband;
      }
      this._wideband = value;
   }

   Phono.registerPlugin("phone", {
      create: function(phono, config, callback) {
         return Phono.util.loggify("Phone", new Phone(phono, config, callback));
      }
   });
      
})();


   // ======================================================================

   Strophe.log = function(level, msg) {
       Phono.log.debug("[STROPHE] " + msg);
   };

   // Register Loggign Callback
   Phono.events.add(Phono.log, "log", function(event) {
      var date = event.timeStamp;
      var formattedDate = 
            Phono.util.padWithZeroes(date.getHours(), 2) + ":" + 
            Phono.util.padWithZeroes(date.getMinutes(), 2) + ":" + 
            Phono.util.padWithZeroes(date.getSeconds(), 2) + "." +
            Phono.util.padWithZeroes(date.getMilliseconds(), 3);
      var formattedMessage = formattedDate + " " + Phono.util.padWithSpaces(event.level.name, 5) + " - " + event.getCombinedMessages();
      var throwableStringRep = event.getThrowableStrRep();
      if (throwableStringRep) {
        formattedMessage += newLine + throwableStringRep;
      }
      console.log(formattedMessage);
   });

   // PluginManager is responsible for initializing plugins an 
   // notifying when all plugins are initialized
   function PluginManager(phono, config, readyHandler) {
      this.index = 0;
      this.readyHandler = readyHandler;
      this.config = config;
      this.phono = phono;
      this.pluginNames = new Array();
      for(pluginName in Phono.plugins) {
         this.pluginNames.push(pluginName);
      }
   };

   PluginManager.prototype.init = function(phono, config, readyHandler) {
      this.chain();
   };

   PluginManager.prototype.chain = function() {
      var manager = this;
      var pluginName = manager.pluginNames[this.index];
      Phono.plugins[pluginName].create(manager.phono, manager.config[pluginName], function(plugin) {
         manager.phono[pluginName] = plugin;
         manager.index++;
         if(manager.index === manager.pluginNames.length) {
            manager.readyHandler.apply(manager.phono);
         }
         else {
            manager.chain();
         }
      });
   };
   
})();


(function($) {
   
   $.phono = function(config) {
      return new Phono(config);
   }
   
})(jQuery);