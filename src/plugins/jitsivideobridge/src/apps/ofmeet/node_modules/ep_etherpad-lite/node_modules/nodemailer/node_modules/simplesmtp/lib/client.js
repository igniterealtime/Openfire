"use strict";

var Stream = require("stream").Stream,
    utillib = require("util"),
    net = require("net"),
    tls = require("tls"),
    oslib = require("os"),
    xoauth2 = require("xoauth2"),
    crypto = require("crypto"),
    fs = require('fs');

// expose to the world
module.exports = function(port, host, options){
    var connection = new SMTPClient(port, host, options);

    if(typeof setImmediate == "function"){
        setImmediate(connection.connect.bind(connection));
    }else{
        process.nextTick(connection.connect.bind(connection));
    }

    return connection;
};

/**
 * <p>Generates a SMTP connection object</p>
 *
 * <p>Optional options object takes the following possible properties:</p>
 * <ul>
 *     <li><b>secureConnection</b> - use SSL</li>
 *     <li><b>name</b> - the name of the client server</li>
 *     <li><b>auth</b> - authentication object <code>{user:"...", pass:"..."}</code>
 *     <li><b>ignoreTLS</b> - ignore server support for STARTTLS</li>
 *     <li><b>tls</b> - options for createCredentials</li>
 *     <li><b>debug</b> - output client and server messages to console</li>
 *     <li><b>logFile</b> - output client and server messages to file</li>
 *     <li><b>instanceId</b> - unique instance id for debugging</li>
 *     <li><b>localAddress</b> - outbound address to bind to (see: http://nodejs.org/api/net.html#net_net_connect_options_connectionlistener)</li>
 * </ul>
 *
 * @constructor
 * @namespace SMTP Client module
 * @param {Number} [port=25] Port number to connect to
 * @param {String} [host="localhost"] Hostname to connect to
 * @param {Object} [options] Option properties
 */
function SMTPClient(port, host, options){
    Stream.call(this);
    this.writable = true;
    this.readable = true;

    this.stage = "init";

    this.options = options || {};

    this.port = port || (this.options.secureConnection ? 465 : 25);
    this.host = host || "localhost";

    this.options.secureConnection = !!this.options.secureConnection;
    this.options.auth = this.options.auth || false;
    this.options.maxConnections = this.options.maxConnections || 5;
    this.options.enableDotEscaping = this.options.enableDotEscaping || false;


    if(!this.options.name){
        // defaul hostname is machine hostname or [IP]
        var defaultHostname = (oslib.hostname && oslib.hostname()) || "";

        if(defaultHostname.indexOf('.')<0){
            defaultHostname = "[127.0.0.1]";
        }
        if(defaultHostname.match(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/)){
            defaultHostname = "["+defaultHostname+"]";
        }

        this.options.name = defaultHostname;
    }

    this._init();
}
utillib.inherits(SMTPClient, Stream);

/**
 * <p>Initializes instance variables</p>
 */
SMTPClient.prototype._init = function(){
    /**
     * Defines if the current connection is secure or not. If not,
     * STARTTLS can be used if available
     * @private
     */
    this._secureMode = false;

    /**
     * Ignore incoming data on TLS negotiation
     * @private
     */
    this._ignoreData = false;

    /**
     * Store incomplete messages coming from the server
     * @private
     */
    this._remainder = "";

    /**
     * If set to true, then this object is no longer active
     * @private
     */
    this.destroyed = false;

    /**
     * The socket connecting to the server
     * @publick
     */
    this.socket = false;

    /**
     * Lists supported auth mechanisms
     * @private
     */
    this._supportedAuth = [];

    /**
     * Currently in data transfer state
     * @private
     */
    this._dataMode = false;

    /**
     * Keep track if the client sends a leading \r\n in data mode
     * @private
     */
    this._lastDataBytes = new Buffer(2);
	this._lastDataBytes[0] = 0x0D;
	this._lastDataBytes[1] = 0x0A;
	

    /**
     * Function to run if a data chunk comes from the server
     * @private
     */
    this._currentAction = false;

    /**
     * Timeout variable for waiting the greeting
     * @private
     */
    this._greetingTimeout = false;

    /**
     * Timeout variable for waiting the connection to start
     * @private
     */
    this._connectionTimeout = false;

    if(this.options.ignoreTLS || this.options.secureConnection){
        this._secureMode = true;
    }

    /**
     * XOAuth2 token generator if XOAUTH2 auth is used
     * @private
     */
    this._xoauth2 = false;

    if(typeof this.options.auth.XOAuth2 == "object" && typeof this.options.auth.XOAuth2.getToken == "function"){
        this._xoauth2 = this.options.auth.XOAuth2;
    }else if(typeof this.options.auth.XOAuth2 == "object"){
        if(!this.options.auth.XOAuth2.user && this.options.auth.user){
            this.options.auth.XOAuth2.user = this.options.auth.user;
        }
        this._xoauth2 = xoauth2.createXOAuth2Generator(this.options.auth.XOAuth2);
    }
};

/**
 * <p>Creates a connection to a SMTP server and sets up connection
 * listener</p>
 */
SMTPClient.prototype.connect = function(){
    var opts = {};
    if(this.options.secureConnection){
        if (this.options.tls) {
            Object.keys(this.options.tls).forEach((function(key) {
                opts[key] = this.options.tls[key];
            }).bind(this));
        }

        if(!("rejectUnauthorized" in opts)){
            opts.rejectUnauthorized = !!this.options.rejectUnauthorized;
        }

        if(this.options.localAddress){
            opts.localAddress = this.options.localAddress;
        }

        this.socket = tls.connect(this.port, this.host, opts, this._onConnect.bind(this));
    }else{
        opts = {
            port: this.port,
            host: this.host
        };
        if(this.options.localAddress){
            opts.localAddress = this.options.localAddress;
        }
        this.socket = net.connect(opts, this._onConnect.bind(this));
    }

    if(this.options.connectionTimeout){
        this._connectionTimeout = setTimeout((function(){
            var error = new Error("Connection timeout");
            error.code = "ETIMEDOUT";
            error.errno = "ETIMEDOUT";
            error.stage = this.stage;
            this.emit("error", error);
            this.close();
        }).bind(this), this.options.connectionTimeout);
    }

    this.socket.on("drain", this._onDrain.bind(this));

    this.socket.on("error", this._onError.bind(this));
};

/**
 * <p>Upgrades the connection to TLS</p>
 *
 * @param {Function} callback Callback function to run when the connection
 *        has been secured
 */
SMTPClient.prototype._upgradeConnection = function(callback){
    this._ignoreData = true;
    this.socket.removeAllListeners("data");
    this.socket.removeAllListeners("error");

    var opts = {
        socket: this.socket,
        host: this.host,
        rejectUnauthorized: !!this.options.rejectUnauthorized
    };

    Object.keys(this.options.tls || {}).forEach((function(key){
        opts[key] = this.options.tls[key];
    }).bind(this));

    this.socket = tls.connect(opts, (function(){
        this._ignoreData = false;
        this._secureMode = true;
        this.socket.on("data", this._onData.bind(this));

        return callback(null, true);
    }).bind(this));
    this.socket.on("error", this._onError.bind(this));
};

/**
 * <p>Connection listener that is run when the connection to
 * the server is opened</p>
 *
 * @event
 */
SMTPClient.prototype._onConnect = function(){
    this.stage = "connect";

    clearTimeout(this._connectionTimeout);

    if("setKeepAlive" in this.socket){
        this.socket.setKeepAlive(true);
    }

    if("setNoDelay" in this.socket){
        this.socket.setNoDelay(true);
    }

    this.socket.on("data", this._onData.bind(this));
    this.socket.on("close", this._onClose.bind(this));
    this.socket.on("end", this._onEnd.bind(this));

    this.socket.setTimeout(3 * 3600 * 1000); // 1 hours
    this.socket.on("timeout", this._onTimeout.bind(this));

    this._greetingTimeout = setTimeout((function(){
        // if strill waiting for greeting, give up
        if(this.socket && !this._destroyed && this._currentAction == this._actionGreeting){
            var error = new Error("Greeting never received");
            error.code = "ETIMEDOUT";
            error.errno = "ETIMEDOUT";
            error.stage = this.stage;
            this.emit("error", error);
            this.close();
        }
    }).bind(this), this.options.greetingTimeout || 10000);

    this._currentAction = this._actionGreeting;
};

/**
 * <p>Destroys the client - removes listeners etc.</p>
 */
SMTPClient.prototype._destroy = function(){
    if(this._destroyed)return;
    this._destroyed = true;
    this._ignoreData = true;
    this.emit("end");
    this.removeAllListeners();
};

/**
 * <p>'data' listener for data coming from the server</p>
 *
 * @event
 * @param {Buffer} chunk Data chunk coming from the server
 */
SMTPClient.prototype._onData = function(chunk){
    var str;

    if(this._ignoreData || !chunk || !chunk.length){
        return;
    }

    // Wait until end of line
    if(chunk.readUInt8(chunk.length-1) != 0x0A){
        this._remainder += chunk.toString();
        return;
    }else{
        str = (this._remainder + chunk.toString()).trim();
        this._remainder = "";
    }

    // if this is a multi line reply, wait until the ending
    if(str.match(/(?:^|\n)\d{3}-.+$/)){
        this._remainder = str + "\r\n";
        return;
    }

    if(this.options.debug){
        console.log("SERVER"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+str.replace(/\r?\n/g,"\n   "));
    }
    if(this.options.logFile){
        this.log("SERVER"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+str.replace(/\r?\n/g,"\n   ")
        );
    }

    if(typeof this._currentAction == "function"){
        this._currentAction.call(this, str);
    }
};

/**
 * <p>'error' listener for the socket</p>
 *
 * @event
 * @param {Error} err Error object
 * @param {String} type Error name
 */
SMTPClient.prototype._onError = function(err, type, data){
    if(type && type != "Error"){
        err.name = type;
    }
    if(data){
        err.data = data;
    }
    err.stage = this.stage;
    this.emit("error", err);
    this.close();
};

/**
 * <p>'drain' listener for the socket</p>
 *
 * @event
 */
SMTPClient.prototype._onDrain = function(){
    this.emit("drain");
};


/**
 * <p>'close' listener for the socket</p>
 *
 * @event
 */
SMTPClient.prototype._onClose = function(){
    if([this._actionGreeting, this._actionIdle, this.close].indexOf(this._currentAction) < 0){
        return this._onError(new Error("Connection closed unexpectedly"));
    }

    this.stage = "close";

    this._destroy();
};

/**
 * <p>'end' listener for the socket</p>
 *
 * @event
 */
SMTPClient.prototype._onEnd = function(){
    this.stage = "end";

    this._destroy();
};

/**
 * <p>'timeout' listener for the socket</p>
 *
 * @event
 */
SMTPClient.prototype._onTimeout = function(){
    this.close();
};

/**
 * <p>Passes data stream to socket if in data mode</p>
 *
 * @param {Buffer} chunk Chunk of data to be sent to the server
 */
SMTPClient.prototype.write = function(chunk){
    // works only in data mode
    if(!this._dataMode || this._destroyed){
        // this line should never be reached but if it does, then
        // say act like everything's normal.
        return true;
    }

    if(typeof chunk == "string"){
        chunk = new Buffer(chunk, "utf-8");
    }

    if (!this.options.enableDotEscaping) {
        if (chunk.length>=2) {
            this._lastDataBytes[0] = chunk[chunk.length-2];
            this._lastDataBytes[1] = chunk[chunk.length-1];
        } else if (chunk.length==1) {
            this._lastDataBytes[0] = this._lastDataBytes[1]
            this._lastDataBytes[1] = chunk[0];
        }
    } else {
        chunk = this._escapeDot(chunk);
    }

    if(this.options.debug){
        console.log("CLIENT (DATA)"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+chunk.toString().trim().replace(/\n/g,"\n   "));
    }
    if(this.options.logFile){
        this.log("CLIENT (DATA)"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+chunk.toString().trim().replace(/\n/g,"\n   "));
    }

    // pass the chunk to the socket
    return this.socket.write(chunk);
};

/**
 * <p>Indicates that a data stream for the socket is ended. Works only
 * in data mode.</p>
 *
 * @param {Buffer} [chunk] Chunk of data to be sent to the server
 */
SMTPClient.prototype.end = function(chunk){
    // works only in data mode
    if(!this._dataMode || this._destroyed){
        // this line should never be reached but if it does, then
        // say act like everything's normal.
        return true;
    }

    if(chunk && chunk.length){
        this.write(chunk);
    }

    // redirect output from the server to _actionStream
    this._currentAction = this._actionStream;

    // indicate that the stream has ended by sending a single dot on its own line
    // if the client already closed the data with \r\n no need to do it again
    if(this._lastDataBytes[0] == 0x0D && this._lastDataBytes[1] == 0x0A){
        this.socket.write(new Buffer(".\r\n", "utf-8"));
    }else if(this._lastDataBytes[1] == 0x0D){
        this.socket.write(new Buffer("\n.\r\n"));
    }else{
        this.socket.write(new Buffer("\r\n.\r\n"));
    }
	this._lastDataBytes[0] = 0x0D;
	this._lastDataBytes[1] = 0x0A;


    // end data mode
    this._dataMode = false;
};

/**
 * <p>Send a command to the server, append \r\n</p>
 *
 * @param {String} str String to be sent to the server
 */
SMTPClient.prototype.sendCommand = function(str){
    if(this._destroyed){
        // Connection already closed, can't send any more data
        return;
    }
    if(this.options.debug){
        console.log("CLIENT"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+(str || "").toString().trim().replace(/\n/g,"\n   "));
    }
    if(this.options.logFile){
        this.log("CLIENT"+(this.options.instanceId?" "+
            this.options.instanceId:"")+":\n└──"+(str || "").toString().trim().replace(/\n/g,"\n   "));
    }
    this.socket.write(new Buffer(str+"\r\n", "utf-8"));
};

/**
 * <p>Sends QUIT</p>
 */
SMTPClient.prototype.quit = function(){
    this.sendCommand("QUIT");
    this._currentAction = this.close;
};

/**
 * <p>Closes the connection to the server</p>
 */
SMTPClient.prototype.close = function(){
    if(this.options.debug){
        console.log("Closing connection to the server");
    }
    if(this.options.logFile){
        this.log("Closing connection to the server");
    }
    var closeMethod = "end";

    // Clear current job
    this._currentAction = this._actionIdle;

    if (this.stage === "init") {
        // Clear connection timeout timer if other than timeout error occurred
        clearTimeout(this._connectionTimeout);
        // Close the socket immediately when connection timed out
        closeMethod = "destroy";
    }
    if(this.socket && this.socket.socket && this.socket.socket[closeMethod] && !this.socket.socket.destroyed){
        this.socket.socket[closeMethod]();
    }
    if(this.socket && this.socket[closeMethod] && !this.socket.destroyed){
        this.socket[closeMethod]();
    }
    this._destroy();
};

/**
 * <p>Initiates a new message by submitting envelope data, starting with
 * <code>MAIL FROM:</code> command</p>
 *
 * @param {Object} envelope Envelope object in the form of
 *        <code>{from:"...", to:["..."]}</code>
 */
SMTPClient.prototype.useEnvelope = function(envelope){
    this._envelope = envelope || {};
    this._envelope.from = this._envelope.from || ("anonymous@"+this.options.name);

    // clone the recipients array for latter manipulation
    this._envelope.rcptQueue = JSON.parse(JSON.stringify(this._envelope.to || []));
    this._envelope.rcptFailed = [];

    this._currentAction = this._actionMAIL;
    this.sendCommand("MAIL FROM:<"+(this._envelope.from)+">");
};

/**
 * <p>If needed starts the authentication, if not emits 'idle' to
 * indicate that this client is ready to take in an outgoing mail</p>
 */
SMTPClient.prototype._authenticateUser = function(){
    this.stage = "auth";

    if(!this.options.auth){
        // no need to authenticate, at least no data given
        this._currentAction = this._actionIdle;
        this.emit("idle"); // ready to take orders
        return;
    }

    var auth;
    if(this.options.auth.XOAuthToken && this._supportedAuth.indexOf("XOAUTH")>=0){
        auth = "XOAUTH";
    }else if(this._xoauth2 && this._supportedAuth.indexOf("XOAUTH2")>=0){
        auth = "XOAUTH2";
    }else if(this.options.authMethod) {
        auth = this.options.authMethod.toUpperCase().trim();
    }else{
        // use first supported
        auth = (this._supportedAuth[0] || "PLAIN").toUpperCase().trim();
    }

    switch(auth){
        case "XOAUTH":
            this._currentAction = this._actionAUTHComplete;

            if(typeof this.options.auth.XOAuthToken == "object" &&
              typeof this.options.auth.XOAuthToken.generate == "function"){
                this.options.auth.XOAuthToken.generate((function(err, XOAuthToken){
                    if(this._destroyed){
                        // Nothing to do here anymore, connection already closed
                        return;
                    }
                    if(err){
                        return this._onError(err, "XOAuthTokenError");
                    }
                    this.sendCommand("AUTH XOAUTH " + XOAuthToken);
                }).bind(this));
            }else{
                this.sendCommand("AUTH XOAUTH " + this.options.auth.XOAuthToken.toString());
            }
            return;
        case "XOAUTH2":
            this._currentAction = this._actionAUTHComplete;
            this._xoauth2.getToken((function(err, token){
                if(this._destroyed){
                    // Nothing to do here anymore, connection already closed
                    return;
                }
                if(err){
                    this._onError(err, "XOAUTH2Error");
                    return;
                }
                this.sendCommand("AUTH XOAUTH2 " + token);
            }).bind(this));
            return;
        case "LOGIN":
            this._currentAction = this._actionAUTH_LOGIN_USER;
            this.sendCommand("AUTH LOGIN");
            return;
        case "PLAIN":
            this._currentAction = this._actionAUTHComplete;
            this.sendCommand("AUTH PLAIN "+new Buffer(
                    //this.options.auth.user+"\u0000"+
                    "\u0000"+ // skip authorization identity as it causes problems with some servers
                    this.options.auth.user+"\u0000"+
                    this.options.auth.pass,"utf-8").toString("base64"));
            return;
        case "CRAM-MD5":
            this._currentAction = this._actionAUTH_CRAM_MD5;
            this.sendCommand("AUTH CRAM-MD5");
            return;
    }

    this._onError(new Error("Unknown authentication method - "+auth), "UnknowAuthError");
};

/** ACTIONS **/

/**
 * <p>Will be run after the connection is created and the server sends
 * a greeting. If the incoming message starts with 220 initiate
 * SMTP session by sending EHLO command</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionGreeting = function(str){
    this.stage = "greeting";

    clearTimeout(this._greetingTimeout);

    if(str.substr(0,3) != "220"){
        this._onError(new Error("Invalid greeting from server - "+str), false, str);
        return;
    }

    this._currentAction = this._actionEHLO;
    this.sendCommand("EHLO "+this.options.name);
};

/**
 * <p>Handles server response for EHLO command. If it yielded in
 * error, try HELO instead, otherwise initiate TLS negotiation
 * if STARTTLS is supported by the server or move into the
 * authentication phase.</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionEHLO = function(str){
    this.stage = "ehlo";

    if(str.substr(0, 3) == "421") {
        this._onError(new Error("Server terminates connection - "+str), false, str);
        return;
    }

    if(str.charAt(0) != "2"){
        // Try HELO instead
        this._currentAction = this._actionHELO;
        this.sendCommand("HELO "+this.options.name);
        return;
    }

    // Detect if the server supports STARTTLS
    if(!this._secureMode && str.match(/[ \-]STARTTLS\r?$/mi)){
        this.sendCommand("STARTTLS");
        this._currentAction = this._actionSTARTTLS;
        return;
    }

    // Detect if the server supports PLAIN auth
    if(str.match(/AUTH(?:\s+[^\n]*\s+|\s+)PLAIN/i)){
        this._supportedAuth.push("PLAIN");
    }

    // Detect if the server supports LOGIN auth
    if(str.match(/AUTH(?:\s+[^\n]*\s+|\s+)LOGIN/i)){
        this._supportedAuth.push("LOGIN");
    }

    // Detect if the server supports CRAM-MD5 auth
    if(str.match(/AUTH(?:\s+[^\n]*\s+|\s+)CRAM-MD5/i)){
        this._supportedAuth.push("CRAM-MD5");
    }

    // Detect if the server supports XOAUTH auth
    if(str.match(/AUTH(?:\s+[^\n]*\s+|\s+)XOAUTH/i)){
        this._supportedAuth.push("XOAUTH");
    }

    // Detect if the server supports XOAUTH2 auth
    if(str.match(/AUTH(?:\s+[^\n]*\s+|\s+)XOAUTH2/i)){
        this._supportedAuth.push("XOAUTH2");
    }

    this._authenticateUser.call(this);
};

/**
 * <p>Handles server response for HELO command. If it yielded in
 * error, emit 'error', otherwise move into the authentication phase.</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionHELO = function(str){
    this.stage = "helo";

    if(str.charAt(0) != "2"){
        this._onError(new Error("Invalid response for EHLO/HELO - "+str), false, str);
        return;
    }
    this._authenticateUser.call(this);
};

/**
 * <p>Handles server response for STARTTLS command. If there's an error
 * try HELO instead, otherwise initiate TLS upgrade. If the upgrade
 * succeedes restart the EHLO</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionSTARTTLS = function(str){
    this.stage = "starttls";

    if(str.charAt(0) != "2"){
        // Try HELO instead
        this._currentAction = this._actionHELO;
        this.sendCommand("HELO "+this.options.name);
        return;
    }

    this._upgradeConnection((function(err, secured){
        if(err){
            this._onError(new Error("Error initiating TLS - "+(err.message || err)), "TLSError");
            return;
        }
        if(this.options.debug){
            console.log("Connection secured");
        }
        if(this.options.logFile){
            this.log("Connection secured");
        }

        if(secured){
            // restart session
            this._currentAction = this._actionEHLO;
            this.sendCommand("EHLO "+this.options.name);
        }else{
            this._authenticateUser.call(this);
        }
    }).bind(this));
};

/**
 * <p>Handle the response for AUTH LOGIN command. We are expecting
 * '334 VXNlcm5hbWU6' (base64 for 'Username:'). Data to be sent as
 * response needs to be base64 encoded username.</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionAUTH_LOGIN_USER = function(str){
    if(str != "334 VXNlcm5hbWU6"){
        this._onError(new Error("Invalid login sequence while waiting for '334 VXNlcm5hbWU6' - "+str), false, str);
        return;
    }
    this._currentAction = this._actionAUTH_LOGIN_PASS;
    this.sendCommand(new Buffer(
            this.options.auth.user, "utf-8").toString("base64"));
};

/**
 * <p>Handle the response for AUTH CRAM-MD5 command. We are expecting
 * '334 <challenge string>'. Data to be sent as response needs to be
 * base64 decoded challenge string, MD5 hashed using the password as
 * a HMAC key, prefixed by the username and a space, and finally all
 * base64 encoded again.</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionAUTH_CRAM_MD5 = function(str) {
    var challengeMatch = str.match(/^334\s+(.+)$/),
        challengeString = "";

    if (!challengeMatch) {
        this._onError(new Error("Invalid login sequence while waiting for server challenge string - "+str), false, str);
        return;
    } else {
        challengeString = challengeMatch[1];
    }

    // Decode from base64
    var base64decoded = new Buffer(challengeString, 'base64').toString('ascii'),
        hmac_md5 = crypto.createHmac('md5', this.options.auth.pass);
    hmac_md5.update(base64decoded);
    var hex_hmac = hmac_md5.digest('hex'),
        prepended = this.options.auth.user + " " + hex_hmac;

    this._currentAction = this._actionAUTH_CRAM_MD5_PASS;

    this.sendCommand(new Buffer(prepended).toString("base64"));
};

/**
 * <p>Handles the response to CRAM-MD5 authentication, if there's no error,
 * the user can be considered logged in. Emit 'idle' and start
 * waiting for a message to send</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionAUTH_CRAM_MD5_PASS = function(str) {
    if (!str.match(/^235\s+/)) {
        this._onError(new Error("Invalid login sequence while waiting for '235 go ahead' - "+str), false, str);
        return;
    }
    this._currentAction = this._actionIdle;
    this.emit("idle"); // ready to take orders
};

/**
 * <p>Handle the response for AUTH LOGIN command. We are expecting
 * '334 UGFzc3dvcmQ6' (base64 for 'Password:'). Data to be sent as
 * response needs to be base64 encoded password.</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionAUTH_LOGIN_PASS = function(str){
    if(str != "334 UGFzc3dvcmQ6"){
        this._onError(new Error("Invalid login sequence while waiting for '334 UGFzc3dvcmQ6' - "+str), false, str);
        return;
    }
    this._currentAction = this._actionAUTHComplete;
    this.sendCommand(new Buffer(this.options.auth.pass, "utf-8").toString("base64"));
};

/**
 * <p>Handles the response for authentication, if there's no error,
 * the user can be considered logged in. Emit 'idle' and start
 * waiting for a message to send</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionAUTHComplete = function(str){
    var response;

    if(this._xoauth2 && str.substr(0, 3) == "334"){
        try{
            response = str.split(" ");
            response.shift();
            response = JSON.parse(new Buffer(response.join(" "), "base64").toString("utf-8"));

            if((!this._xoauth2.reconnectCount || this._xoauth2.reconnectCount < 200) && ['400','401'].indexOf(response.status)>=0){
                this._xoauth2.reconnectCount = (this._xoauth2.reconnectCount || 0) + 1;
                this._currentAction = this._actionXOAUTHRetry;
            }else{
                this._xoauth2.reconnectCount = 0;
                this._currentAction = this._actionAUTHComplete;
            }
            this.sendCommand(new Buffer(0));
            return;

        }catch(E){}
    }

    this._xoauth2.reconnectCount = 0;

    if(str.charAt(0) != "2"){
        this._onError(new Error("Invalid login - "+str), "AuthError", str);
        return;
    }

    this._currentAction = this._actionIdle;
    this.emit("idle"); // ready to take orders
};

/**
 * If XOAUTH2 authentication failed, try again by generating
 * new access token
 */
SMTPClient.prototype._actionXOAUTHRetry = function(){

    // ensure that something is listening unexpected responses
    this._currentAction = this._actionIdle;

    this._xoauth2.generateToken((function(err, token){
        if(this._destroyed){
            // Nothing to do here anymore, connection already closed
            return;
        }
        if(err){
            this._onError(err, "XOAUTH2Error");
            return;
        }
        this._currentAction = this._actionAUTHComplete;
        this.sendCommand("AUTH XOAUTH2 " + token);
    }).bind(this));
};

/**
 * <p>This function is not expected to run. If it does then there's probably
 * an error (timeout etc.)</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionIdle = function(str){
    this.stage = "idle";

    if(Number(str.charAt(0)) > 3){
        this._onError(new Error(str), false, str);
        return;
    }

    // this line should never get called
};

/**
 * <p>Handle response for a <code>MAIL FROM:</code> command</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionMAIL = function(str){
    this.stage = "mail";

    if(Number(str.charAt(0)) != "2"){
        this._onError(new Error("Mail from command failed - " + str), "SenderError", str);
        return;
    }

    if(!this._envelope.rcptQueue.length){
        this._onError(new Error("Can't send mail - no recipients defined"), "RecipientError", str);
    }else{
        this._envelope.curRecipient = this._envelope.rcptQueue.shift();
        this._currentAction = this._actionRCPT;
        this.sendCommand("RCPT TO:<"+this._envelope.curRecipient+">"+this._getDSN());
    }
};

/**
 * <p>SetsUp DSN</p>
 */
SMTPClient.prototype._getDSN = function(){
    var ret = "", n = [], dsn;

    if (this.currentMessage && this.currentMessage.options && "dsn" in this.currentMessage.options){
        dsn = this.currentMessage.options.dsn;

        if(dsn.success){
            n.push("SUCCESS");
        }

        if(dsn.failure){
            n.push("FAILURE");
        }

        if(dsn.delay){
            n.push("DELAY");
        }

        if(n.length > 0){
            ret = " NOTIFY=" + n.join(",") + " ORCPT=rfc822;" + this.currentMessage._message.from;
        }
    }

    return ret;
};

/**
 * <p>Handle response for a <code>RCPT TO:</code> command</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionRCPT = function(str){
    this.stage = "rcpt";

    if(Number(str.charAt(0)) != "2"){
        // this is a soft error
        this._envelope.rcptFailed.push(this._envelope.curRecipient);
    }

    if(!this._envelope.rcptQueue.length){
        if(this._envelope.rcptFailed.length < this._envelope.to.length){
            if(this._envelope.rcptFailed.length){
                this.emit("rcptFailed", this._envelope.rcptFailed);
            }
            this._currentAction = this._actionDATA;
            this.sendCommand("DATA");
        }else{
            this._onError(new Error("Can't send mail - all recipients were rejected"), "RecipientError", str);
            return;
        }
    }else{
        this._envelope.curRecipient = this._envelope.rcptQueue.shift();
        this._currentAction = this._actionRCPT;
        this.sendCommand("RCPT TO:<"+this._envelope.curRecipient+">");
    }
};

/**
 * <p>Handle response for a <code>DATA</code> command</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionDATA = function(str){
    this.stage = "data";

    // response should be 354 but according to this issue https://github.com/eleith/emailjs/issues/24
    // some servers might use 250 instead, so lets check for 2 or 3 as the first digit
    if([2,3].indexOf(Number(str.charAt(0)))<0){
        this._onError(new Error("Data command failed - " + str), false, str);
        return;
    }

    // Emit that connection is set up for streaming
    this._dataMode = true;
    this._currentAction = this._actionIdle;
    this.emit("message");
};

/**
 * <p>Handle response for a <code>DATA</code> stream</p>
 *
 * @param {String} str Message from the server
 */
SMTPClient.prototype._actionStream = function(str){
    if(Number(str.charAt(0)) != "2"){
        // Message failed
        this.emit("ready", false, str);
    }else{
        // Message sent succesfully
        this.emit("ready", true, str);
    }

    // Waiting for new connections
    this._currentAction = this._actionIdle;

    if(typeof setImmediate == "function"){
        setImmediate(this.emit.bind(this, "idle"));
    }else{
        process.nextTick(this.emit.bind(this, "idle"));
    }
};

/**
 * <p>Log debugs to given file</p>
 *
 * @param {String} str Log message
 */
SMTPClient.prototype.log = function(str) {
    fs.appendFile(this.options.logFile, str + "\n", function(err) {
        if (err) {
            console.log('Log write failed. Data to log: ' + str);
        }
    });
};

/**
 * <p>Inserts an extra dot at the begining of a line if it starts with a dot 
 * See RFC 2821 Section 4.5.2</p>
 *
 * @param {Buffer} chunk The chunk that will be send.
 */
SMTPClient.prototype._escapeDot = function(chunk) {
	var pos, OutBuff, i;
	OutBuff = new Buffer(chunk.length * 2);
	pos = 0;
	
	for (i=0; i<chunk.length; i++) {
		if (this._lastDataBytes[0] == 0x0D && this._lastDataBytes[1] == 0x0A && chunk[i] == 0x2E) {
			OutBuff[pos] = 0x2E;
			pos +=1;
		}
		OutBuff[pos] = chunk[i];
		pos += 1;
		this._lastDataBytes[0] = this._lastDataBytes[1];
		this._lastDataBytes[1] = chunk[i];
	}
	
	return OutBuff.slice(0,pos);
}
