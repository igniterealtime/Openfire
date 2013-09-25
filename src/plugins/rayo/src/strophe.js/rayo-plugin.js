/**
 * RAYO XMPP extensions
 *
 *
 */


Strophe.addConnectionPlugin('rayo', {
    _connection: null,
    
    init: function(conn) {
        this._connection = conn;
        
        /* extend name space 
         *  NS.RAYO - XMPP -0327
         *
         */
         
        Strophe.addNamespace('RAYO_CORE', "urn:xmpp:rayo:1");
        Strophe.addNamespace('RAYO_CALL', "urn:xmpp:rayo:call:1");    
        Strophe.addNamespace('RAYO_MIXER', "urn:xmpp:rayo:mixer:1");     
        Strophe.addNamespace('RAYO_EXT', "urn:xmpp:rayo:ext:1");     
        Strophe.addNamespace('RAYO_EXT_COMPLETE', "urn:xmpp:rayo:ext:complete:1");     
        Strophe.addNamespace('RAYO_INPUT', "urn:xmpp:rayo:input:1");     
        Strophe.addNamespace('RAYO_INPUT_COMPLETE', "urn:xmpp:rayo:input:complete:1");    
        Strophe.addNamespace('RAYO_OUTPUT', "urn:xmpp:rayo:output:1");     
        Strophe.addNamespace('RAYO_OUTPUT_COMPLETE', "urn:xmpp:rayo:output:complete:1"); 
        Strophe.addNamespace('RAYO_PROMPT', "urn:xmpp:rayo:promprt:1");          
        Strophe.addNamespace('RAYO_RECORD', "urn:xmpp:rayo:record:1");     
        Strophe.addNamespace('RAYO_RECORD_COMPLETE', "urn:xmpp:rayo:record:complete:1");    
        Strophe.addNamespace('RAYO_SAY', "urn:xmpp:tropo:say:1");     
        Strophe.addNamespace('RAYO_SAY_COMPLETE', "urn:xmpp:tropo:say:complete:1");    
        Strophe.addNamespace('RAYO_HANDSET', "urn:xmpp:rayo:handset:1");     
        Strophe.addNamespace('RAYO_HANDSET_COMPLETE', "urn:xmpp:rayo:handset:complete:1");     
        
	this._connection.addHandler(this.handlePresence.bind(this), null,"presence", null, null, null);         
    },
        
    handset: function(config) 
    {
    	this.config = config;
    	var self = this;
    	
        var iq = $iq({to: server, from: this._connection.jid, type: "get"})
            .c("handset", {xmlns: Strophe.NS.RAYO_HANDSET, cryptoSuite: config.cryptoSuite, localCrypto: config.localCrypto, remoteCrypto: config.remoteCrypto, mixer: config.mixer, codec: config.codec, stereo: config.stereo});  
            
        this._connection.sendIQ(iq, function(response)
        {
		$('ref', response).each(function() 
		{
			var ref = {host: $(this).attr('host'), localport: $(this).attr('localport'), remoteport: $(this).attr('remoteport'), id: $(this).attr('id'), uri: $(this).attr('uri')} 
			self.config.onStart(ref);
		});        
        });   
    },
    
    handlePresence: function(presence) 
    {

    }
});

