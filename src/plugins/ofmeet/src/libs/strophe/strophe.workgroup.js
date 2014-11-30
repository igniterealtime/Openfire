(function() {
Strophe.addConnectionPlugin('workgroup',
{
	_connection: null,

	/** Function: init
	* Plugin init
	*
	* Parameters:
	*   (Strophe.Connection) conn - Strophe connection
	*/
	init: function(conn)
	{
		this._connection = conn;

		this._connection.addHandler(this._handlePresence.bind(this), null,"presence", null, null, null);  
		this._connection.addHandler(this._handleMessage.bind(this), null,"message", null, null, null); 		
		this._connection.addHandler(this._handleWorkgroups.bind(this), "http://jabber.org/protocol/workgroup", 'iq');	

		console.log("strophe plugin: workgroup enabled");        
	},
	
	statusChanged: function(status, condition)
	{
		console.log("Strophe connection workgroup status: ", status, condition);

		if (status == Strophe.Status.CONNECTED)
		{
		
	    	} else if (status == Strophe.Status.DISCONNECTING) {

		} else if(status == Strophe.Status.DISCONNECTED){

	    	}
	},	

	fetchWorkgroups: function(service, callback, errorback) 
	{
	    var iq = $iq({type: 'get', to: "workgroup." + this._connection.domain}).c('workgroups', {jid: this._connection.jid, xmlns: 'http://jabber.org/protocol/workgroup'});
	    var that = this;
	    
	    if (!service) service = "conference"; 
	    
	    this._connection.sendIQ(iq, 
	    
	    	function(response)  
	    	{
		    $(response).find('workgroup').each(function() 
		    {
			var current = $(this);
			var jid = current.attr('jid');	
			var name = Strophe.getNodeFromJid(jid);
			var chatRoom = 'workgroup-' + name + "@" + service + "." + that._connection.domain;
						
			if (callback) callback({name: name, chatRoom: chatRoom, jid: jid});	
			
		    });
		    
	    	}, function (error) {
		    
		    	if (errorback) errorback(that.translateError(error));
		}
	    );
	},
	
	subscribe: function(workgroup) 
	{  
		this._connection.send($pres({type: 'subscribe', to: workgroup + "@workgroup." + this._connection.domain })); 
	},
	
	unsubscribe: function(workgroup) 
	{  
		this._connection.send($pres({type: 'unsubscribe', to: workgroup + "@workgroup." + this._connection.domain })); 
	},

	joinWorkgroup: function(workgroup, maxChats, callback, errorback) 
	{
		var jid = workgroup + "@workgroup." + this._connection.domain;	
		this._connection.send($pres({to: jid}).c('agent-status', {xmlns: 'http://jabber.org/protocol/workgroup'}));		
		this._connection.send($pres({to: jid}).c('status').t("Online"));		
		var iq = $iq({type: 'get', to: jid}).c('agent-status-request', {xmlns: 'http://jabber.org/protocol/workgroup'});
		var that = this;
		
		this._connection.sendIQ(iq,
			function (res) {
				if (callback) callback(res);			
			},

			function (err) {
				if (errorback) errorback(that.translateError(err))			    
			}
		);		
	},
	
	translateError: function (err) {
	    var error = {};

	    $(err).find('error').each(function() 
	    {
		error.code = $(this).attr("code");
	    });	

	    $(err).find('text').each(function() 
	    {
		error.description = $(this).text();
	    });				    

	   return error;				    
	},	
	
	leaveWorkgroup: function(workgroup) 
	{
		var jid = workgroup + "@workgroup." + this._connection.domain;	
		this._connection.send($pres({to: jid, type: "unavailable"}).c('agent-status', {xmlns: 'http://jabber.org/protocol/workgroup'}));		
		this._connection.send($pres({to: jid, type: "unavailable"}).c('status').t("Online"));				
	},	
	
	joinQueue: function(workgroup, form, callback, errorback) 
	{
		var iq = $iq({to: workgroup + "@workgroup." + this._connection.domain, type: 'set'}).c('join-queue', {xmlns: 'http://jabber.org/protocol/workgroup'});	
		iq.c('queue-notifications').up();
		iq.c('x', {xmlns: 'jabber:x:data', type: 'submit'});

		var items = Object.getOwnPropertyNames(form)

		for (var i=0; i< items.length; i++)
		{
			iq.c('field', {var: items[i]}).c('value').t(form[items[i]]).up().up();
		}

		iq.up();
		var that = this;

		this._connection.sendIQ(iq,
			function (res) {
				if (callback) callback(res);			
			},

			function (err) {
				if (errorback) errorback(that.translateError(err));				    
			}
		);     	       	
	},   
	
	leaveQueue: function(workgroup, callback, errorback) 
	{
		var iq = $iq({to: workgroup + "@workgroup." + this._connection.domain, type: 'set'}).c('depart-queue', {xmlns: 'http://jabber.org/protocol/workgroup'});	
		var that = this;
		
		this._connection.sendIQ(iq,
			function (res) {
				if (callback) callback(res);			
			},

			function (err) {
				if (errorback) errorback(that.translateError(err));				    
			}
		);     	       	
	}, 
	
	acceptOffer: function(workgroup, jid, id, callback, errorback) 
	{
		var iq = $iq({to: workgroup + "@workgroup." + this._connection.domain, type: 'set'}).c('offer-accept', {xmlns: 'http://jabber.org/protocol/workgroup', jid: jid, id: id});	
		var that = this;
		
		this._connection.sendIQ(iq,
			function (res) {
				if (callback) callback(res);			
			},

			function (err) {
				if (errorback) errorback(that.translateError(err));			    
			}
		);      	       	
	},	
   	
	rejectOffer: function(workgroup, jid, id, callback, errorback) 
	{
		var iq = $iq({to: workgroup + "@workgroup." + this._connection.domain, type: 'set'}).c('offer-reject', {xmlns: 'http://jabber.org/protocol/workgroup', jid: jid, id: id});	
		var that = this;
		
		this._connection.sendIQ(iq,
			function (res) {
				if (callback) callback(res);			
			},

			function (err) {
				if (errorback) errorback(that.translateError(err));			    
			}
		);      	       	
	},   	
	
	_handleMessage: function(message) 
	{  	
		$(message).find('queue-status').each(function ()  	
		{
			var position = 0;
			var time = 0;

			$(this).find('position').each(function ()  	
			{
				position = $(this).text();
			});

			$(this).find('time').each(function ()  	
			{
				time = $(this).text();
			});

			$(document).trigger("workgroup.queue.status",
			{
				from: $(message).attr("from"),
				position: position,
				time: time
			});		
		});
		
		return true;	
	},
	
	_handlePresence: function(presence) 
	{  	
		var to = $(presence).attr('to');
		var from = $(presence).attr('from');	

		var xquery = presence.getElementsByTagName("x");
		var agentStatus = presence.getElementsByTagName("agent-status");	
		var notifyQueue = presence.getElementsByTagName("notify-queue");	
		var notifyQueueDetails = presence.getElementsByTagName("notify-queue-details");		
		var inviteAccepted = presence.getElementsByTagName("inviteaccepted");
		var inviteCompleted = presence.getElementsByTagName("invitecompleted");	
		var workgroupPresence = presence.getElementsByTagName("workgroup");

		if (agentStatus.length > 0 || notifyQueueDetails.length > 0 || notifyQueue.length > 0) {

			this._handleAgentPresence($(presence));	

		} else if (workgroupPresence.length > 0) {
			var type = $(presence).attr('type');  
			var open = type != "unavailable" && type != "unsubscribe" && type != "subscribe";		
			var workgroup = Strophe.getNodeFromJid(from);
			
			$(document).trigger("workgroup.status", {workgroup: workgroup, open: open});			
		}	
		return true;	
	},
	
	_handleWorkgroups: function(iq) {

		var _myself = this; 
		var iq = $(iq);
		
		var workgroupJid = iq.attr('from');
		var workgroupName = Strophe.getNodeFromJid(workgroupJid);

		if (iq.attr('type') != "result" && iq.attr('type') != "error") this._connection.send($iq({type: 'result', to: iq.attr('from'), id: iq.attr('id')}));		

		iq.find('offer').each(function() 
		{
			var id = $(this).attr('id');
			var jid = $(this).attr('jid').toLowerCase();	
			var properties = {id: id, jid: jid};

			iq.find('value').each(function() 
			{	
				var name = $(this).attr('name');		
				var value = $(this).text();
				properties[name] = value;
			});
			
			$(document).trigger("workgroup.offer", {workgroupName: workgroupName, workgroupJid: workgroupJid, properties: properties});			
		});

		iq.find('offer-revoke').each(function() 
		{
			id = $(this).attr('id');
			jid = $(this).attr('jid').toLowerCase();
			var properties = {id: id, jid: jid};
			var reason = "offer timed out";

			$(this).find('reason').each(function() 
			{			
				reason = $(this).text();
			});

			$(document).trigger("workgroup.revoke", {workgroupName: workgroupName, workgroupJid: workgroupJid, properties: properties, reason: reason});	
		});
		return true;
	},

	_handleAgentPresence:  function(presence) 
	{
		var from = presence.attr('from');
		var status = presence.attr('type') || "available";
		var maxChats = 0, currentChats = 0, workgroup;				

		presence.find('agent-status').each(function() 
		{
			workgroup = $(this).attr("jid");
			
			presence.find('max-chats').each(function() 
			{
				maxChats = $(this).text();	
			});	

			presence.find('current-chats').each(function() 
			{
				currentChats = $(this).text();				
			});
			
			$(document).trigger("workgroup.agent.status",
			{
				   from: from,
				   status: status,
				   workgroup: workgroup,
				   maxChats: maxChats
			});			
		});

		presence.find('notify-queue-details').each(function() 
		{
			presence.find('user').each(function() 
			{
				var jid = $(this).attr('jid');
				var position, time, joinTime

				$(this).find('position').each(function() 
				{
					position = $(this).text() == "0" ? "first": jQuery(this).text();				
				});

				$(this).find('time').each(function() 
				{
					time = $(this).text();				
				});

				$(this).find('join-time').each(function() 
				{
					joinTime = $(this).text();				
				});

				$(document).trigger("workgroup.queue.details",
				{
				   from: from,
				   workgroup: from,
				   position: position,
				   time: time,
				   joinTime: joinTime
				});										
			});

		});

		presence.find('notify-queue').each(function() 
		{
			var free = true;
			var count, oldest, waitTime, status

			presence.find('count').each(function() 
			{
				count = jQuery(this).text();				
			});

			presence.find('oldest').each(function() 
			{
				oldest = jQuery(this).text();				
			});

			presence.find('time').each(function() 
			{
				waitTime = jQuery(this).text();				
			});

			presence.find('status').each(function() 
			{
				status = jQuery(this).text();				
			});

			if (count && oldest && waitTime && status)
			{
				free = false;		
			}

			$(document).trigger("workgroup.queue.summary",
			{
			   workgroup: from,
			   free: free,
			   count: count,
			   oldest: oldest,
			   waitTime: waitTime,
			   status: status
			});			
		});	

		return true;     
	}	
});
}).call(this);