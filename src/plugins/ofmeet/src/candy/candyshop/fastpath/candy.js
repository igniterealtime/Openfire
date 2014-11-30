/**
 * Fastpath plugin for Candy
 *
 */

var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.Fastpath = (function(self, Candy, $) {

	var connection = null;
	var nickname = null;

	self.init = function() 
	{
		//console.log("Fastpath.init");	
		
		$(Candy).on('candy:core.chat.connection', function(obj, data) 
		{		
			switch(data.status) 
			{
				case Strophe.Status.CONNECTED:	
					connection = Candy.Core.getConnection();
					Candy.Core.addHandler(fastpathIqCallback, "http://jabber.org/protocol/workgroup", 'iq', 'set');						
					Candy.Core.addHandler(fastpathPresCallback, null, 'presence');
					Candy.Core.addHandler(fastpathMsgCallback, null, 'message');				
					nickname = Strophe.escapeNode(Candy.Core.getUser().getNick());	
					GetWorkgroups();					
					break;
			}
		    	return true;
		});			
	}
	self.acceptOffer = function()
	{
		//console.log('accepted', window.properties);	
		
		connection.send($iq({type: 'set', to: window.properties.workgroupJid}).c('offer-accept', {xmlns: "http://jabber.org/protocol/workgroup", jid: window.properties.jid, id: window.properties.id}));			
		Candy.View.Pane.Chat.Modal.hide();		
	}
	
	self.rejectOffer = function()
	{
		//console.log('rejected');
		
		connection.send($iq({type: 'set', to: window.properties.workgroupJid}).c('offer-reject', {xmlns: "http://jabber.org/protocol/workgroup", jid: window.properties.jid, id: window.properties.id}));
		Candy.View.Pane.Chat.Modal.hide();		
	}	
	
	var GetWorkgroups = function() 
	{
		var iq = $iq({type: 'get', to: "workgroup." + connection.domain}).c('workgroups', {jid: connection.jid, xmlns: "http://jabber.org/protocol/workgroup"});

		connection.sendIQ(iq, function(response)
		{
		    $(response).find('workgroup').each(function() 
		    {
			var current = $(this);
			var jid = current.attr('jid');	
			var name = Strophe.getNodeFromJid(jid);
			var chatRoom = 'workgroup-' + name + "@conference." + connection.domain;

			connection.send($pres({to: jid}).c('agent-status', {'xmlns': "http://jabber.org/protocol/workgroup"}));				    	
			Candy.Core.Action.Jabber.Room.Join(chatRoom);	
			
			connection.send($pres({to: jid}).c("status").t("Online").up().c("priority").t("1"));						
			connection.sendIQ($iq({type: 'get', to: jid}).c('agent-status-request', {xmlns: "http://jabber.org/protocol/workgroup"}));

		    });

		});
	};
	
	var fastpathIqCallback = function(iq) 
	{
		//console.log('fastpathIqCallback', iq);

		var iq = $(iq);
		var workgroupJid = iq.attr('from');
		var workgroup = Strophe.getNodeFromJid(workgroupJid);
		
		connection.send($iq({type: 'result', to: iq.attr('from'), id: iq.attr('id')}));			

		iq.find('offer').each(function() 
		{
			var id = $(this).attr('id');
			var jid = $(this).attr('jid').toLowerCase();	
			var properties = {id: id, jid: jid, workgroupJid: workgroupJid};

			iq.find('value').each(function() 
			{	
				var name = $(this).attr('name');		
				var value = $(this).text();
				properties[name] = value;
			});
				
			//console.log("fastpathIqCallback offer", properties, workgroup);
			
			acceptRejectOffer(workgroup, properties);			
		});

		iq.find('offer-revoke').each(function() 
		{
			id = $(this).attr('id');
			jid = $(this).attr('jid').toLowerCase();
			
			//console.log("fastpathIqCallback offer-revoke", workgroup);			
		});

		return true;
	}
	
	var fastpathPresCallback = function(presence) 
	{
		//console.log('fastpathPresCallback', presence);	
		
		var presence = $(presence);
		
		if (presence.find('agent-status').length > 0 || presence.find('notify-queue-details').length > 0 || presence.find('notify-queue').length > 0) 
		{			
			var from = Candy.Util.unescapeJid(presence.attr('from'));
			var nick = Strophe.getNodeFromJid(from);

			var workGroup, maxChats, free = true;				

			presence.find('agent-status').each(function() 
			{
				workGroup = 'workgroup-' + Strophe.getNodeFromJid($(this).attr('jid')) + "@conference." + connection.domain;			

				presence.find('max-chats').each(function() 
				{
					maxChats = $(this).text();	
				});	

				presence.find('chat').each(function() 
				{
					free = false;

					var sessionID = $(this).attr('sessionID');	
					var sessionJid = sessionID + "@conference." + connection.domain;
					var sessionHash = (sessionJid);

					var userID = $(this).attr('userID');	
					var startTime = $(this).attr('startTime');	
					var question = $(this).attr('question');				
					var username = $(this).attr('username');	
					var email = $(this).attr('email');	

					if (workGroup)
					{
						//console.log('agent-status message  to ' + workGroup);			
						var text = "Talking with " + username + " about " + question;
						Candy.View.Pane.Message.show(workGroup, nick, text);						
					}
				});
			});

			presence.find('notify-queue-details').each(function() 
			{
				var workGroup = 'workgroup-' + nick + "@conference." + connection.domain;		
				var free = true;

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

					if (position && time && joinTime)
					{
						free = false;

						//console.log('notify-queue-details message  to ' + workGroup);	

						var text = "A caller has been waiting for " + time + " secconds";
						Candy.View.Pane.Message.show(workGroup, nick, text);							
					}			
				});

			});

			presence.find('notify-queue').each(function() 
			{
				var workGroup = 'workgroup-' + nick + "@conference." + connection.domain;		
				var free = true;
				var count, oldest, waitTime, status
				var room = Candy.View.Pane.Room.getPane(workGroup, '.message-pane')

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
					//console.log('notify-queue message  to ' + workGroup);	

					var text = "There are " + count + " caller(s) waiting for as long as " + waitTime + " seconds";
					if (room) Candy.View.Pane.Message.show(workGroup, nick, text);							
				}

				if (free && room) Candy.View.Pane.Message.show(workGroup, nick, "No waiting conversations");				

			});	
		}
		
		return true;			
	}
	
	var fastpathMsgCallback = function(message) 
	{
		//console.log('fastpathMsgCallback', message);
		
		var msg = $(message);
		
		msg.find('invite').each(function() 
		{	
			var roomJid = msg.attr("from");	
			var workgroupJid = $(this).attr('from');	

			msg.find('offer').each(function() 
			{	
				var contactJid = $(this).attr('jid');
				//console.log("fastpathMsgCallback offer", workgroupJid, contactJid, roomJid);
				
				if (CandyShop.OfMeet)
				{
					CandyShop.OfMeet.showOfMeet(roomJid);
				} else {
					Candy.Core.Action.Jabber.Room.Join(roomJid);
				}
			});

		});				
		return true;		
	}
	
	var acceptRejectOffer = function(workgroup, properties)
	{				
		var form = '<strong>' + workgroup + '</strong>' + '<form class="accept-reject-offer-form">'
			
		var props = Object.getOwnPropertyNames(properties)

		for (var i=0; i< props.length; i++)
		{
			if (props[i] != "id" && props[i] != "jid" && props[i] != "workgroupJid")
				form = form + props[i] + " - " + properties[props[i]] + "<p/>";
		}
		window.properties = properties;
		
		form = form + '<input onclick="CandyShop.Fastpath.acceptOffer()" type="button" value="Accept" />'
			    + '<input onclick="CandyShop.Fastpath.rejectOffer()" type="button" value="Reject" />'
			    + '</form>'	
		
		Candy.View.Pane.Chat.Modal.show(form, true);		    
	}	
		
    return self;
    
}(CandyShop.Fastpath || {}, Candy, jQuery));  	
