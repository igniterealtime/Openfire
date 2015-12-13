/*

Jappix - An open social platform
These are the Jappix Mini JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 24/06/11

*/

// Jappix Mini vars
var DEVELOPER = "on";

var MINI_DISCONNECT	= false;
var MINI_AUTOCONNECT	= false;
var MINI_SHOWPANE	= false;
var MINI_INITIALIZED	= false;
var MINI_ANONYMOUS	= false;
var MINI_ANIMATE	= false;
var MINI_NICKNAME	= null;
var MINI_TITLE		= null;
var MINI_DOMAIN		= null;
var MINI_USER		= null;
var MINI_PASSWORD	= null;
var MINI_RECONNECT	= 0;
var MINI_GROUPCHATS	= [];
var MINI_WORKGROUPS	= [];
var MINI_PASSWORDS	= [];
var MINI_RESOURCE	= JAPPIX_RESOURCE + ' Mini';
var MINI_VISITOR	= null;

// Setups connection handlers
function setupConMini(con) {
	con.registerHandler('message', handleMessageMini);
	con.registerHandler('presence', handlePresenceMini);
	con.registerHandler('iq', handleIQMini);
	con.registerHandler('onerror', handleErrorMini);
	con.registerHandler('onconnect', connectedMini);	
}

function handleReady(peer)
{
	logThis("handleReady");
}

function handleMuteSignal(peer, type, muc, audioMuted, videoMuted, videoRequested)
{
	logThis("handleMuteSignal " + peer.farParty + " " + audioMuted + " " + videoMuted + " " + type + " " + muc, 3);

	var name = peer.farParty.split("@")[0];
	var xid = peer.farParty.split("/")[0];
	var hash = hex_md5(xid);
	var nick = name;

	if (muc)
	{
		name = peer.farParty.split("/")[1];
		nick = unescapeNode(name);	
		
		if (type == "room")
		{
			xid = unescapeNode(peer.farParty);
		}
		
	} else {
		nick = jQuery('#jappix_mini a#friend-' + hash).text().revertHtmlEnc();
	}
	
	
	if (videoRequested) 
	{
		var message = (!audioMuted ? "Started" : "Stopped") + " sending video";
	} else {
		var message = (!audioMuted ? "Started" : "Stopped") + " sending audio";	
	}
	
	var time = getCompleteTime();
	var stamp = extractStamp(new Date());
	var target = '#jappix_mini #chat-' + hash;
	
	if(!exists(target) && (!muc))
		chatMini(type, xid, nick, hash);
				
	displayMessageMini(muc ? "groupchat" : "chat", message, xid, nick, hash, time, stamp, 'user-message');
	
	if (audioMuted || (videoMuted && videoRequested && audioMuted))
		clearAudioVideo(hash);	
	else
		notifyAudioVideo(hash, xid, muc, videoRequested);

}
	
// Connects the user with the given logins
function connectMini(domain, user, password) {

	try {
		// We define the http binding parameters
		oArgs = new Object();
		
		if(HOST_BOSH_MINI)
			oArgs.httpbase = HOST_BOSH_MINI;
		else
			oArgs.httpbase = HOST_BOSH;
		
		// We create the new http-binding connection
		con = new JSJaCOpenfireWSConnection(oArgs);
		//con = new JSJaCHttpBindingConnection(oArgs);
		
		// And we handle everything that happen
		setupConMini(con);
		
		// We retrieve what the user typed in the login inputs
		oArgs = new Object();
		oArgs.secure = true;
		oArgs.xmllang = XML_LANG;
		oArgs.resource = user;
		oArgs.domain = domain;
		
		// Store the resource (for reconnection)
		setDB('jappix-mini', 'resource', user);
		
		// Anonymous login?
		if(MINI_ANONYMOUS) {
			// Anonymous mode disabled?
			if(!allowedAnonymous()) {
				logThis('Not allowed to use anonymous mode.', 2);
				
				// Notify this error
				notifyErrorMini();
				
				return false;
			}
			
			// Bad domain?
			else if(lockHost() && (domain != HOST_ANONYMOUS)) {
				logThis('Not allowed to connect to this anonymous domain: ' + domain, 2);
				
				// Notify this error
				notifyErrorMini();
				
				return false;
			}
			
			oArgs.authtype = 'saslanon';
		}
		
		// Normal login
		else {
			// Bad domain?
			if(lockHost() && (domain != HOST_MAIN)) {
				logThis('Not allowed to connect to this main domain: ' + domain, 2);
				
				// Notify this error
				notifyErrorMini();
				
				return false;
			}
			
			// No nickname?
			if(!MINI_NICKNAME)
				MINI_NICKNAME = user;
			
			oArgs.username = user;
			oArgs.pass = password;
		}
		
		// We connect !
		con.connect(oArgs);
		
		logThis('Jappix Mini is connecting...', 3);
	}
	
	catch(e) {
		// Logs errors
		logThis('Error while logging in: ' + e, 1);
		
		// Reset Jappix Mini
		disconnectedMini();
	}
	
	finally {
		return false;
	}
}

// When the user is connected
function connectedMini() {
	// Update the roster
	jQuery('#jappix_mini a.jm_pane.jm_button span.jm_counter').text('0');
	
	// Do not get the roster if anonymous
	if(MINI_ANONYMOUS)
		initializeMini();
	else
		getRosterMini();
	
	// For logger
	if(MINI_RECONNECT)
		logThis('Jappix Mini is now reconnected.', 3);
	else
		logThis('Jappix Mini is now connected.', 3);
	
	// Reset reconnect var
	MINI_RECONNECT = 0;
	
	initializeWorkgroups();	
}

// When the user disconnects
function saveSessionMini() {
	// Not connected?
	if(!isConnected())
		return;
	
	// Save the actual Jappix Mini DOM
	setDB('jappix-mini', 'dom', jQuery('#jappix_mini').html());
	setDB('jappix-mini', 'nickname', MINI_NICKNAME);
	
	// Save the scrollbar position
	var scroll_position = '';
	var scroll_hash = jQuery('#jappix_mini div.jm_conversation:has(a.jm_pane.jm_clicked)').attr('data-hash');
	
	if(scroll_hash)
		scroll_position = document.getElementById('received-' + scroll_hash).scrollTop + '';
	
	setDB('jappix-mini', 'scroll', scroll_position);
	
	// Save the session stamp
	setDB('jappix-mini', 'stamp', getTimeStamp());
	
	// Suspend connection
	con.suspend(false);
	
	logThis('Jappix Mini session save tool launched.', 3);
}

// Disconnects the connected user
function disconnectMini() {
	// No connection?
	if(!isConnected())
		return false;
	
	// Change markers
	MINI_DISCONNECT = true;
	MINI_INITIALIZED = false;
	
	// Add disconnection handler
	con.registerHandler('ondisconnect', disconnectedMini);
	
	// Disconnect the user
	con.disconnect();		
	
	logThis('Jappix Mini is disconnecting...', 3);
	
	return false;
}

// When the user is disconnected
function disconnectedMini() {
	// Remove the stored items
	removeDB('jappix-mini', 'dom');
	removeDB('jappix-mini', 'nickname');
	removeDB('jappix-mini', 'scroll');
	removeDB('jappix-mini', 'stamp');
	
	// Connection error?
	if(!MINI_DISCONNECT || MINI_INITIALIZED) {
		// Browser error?
		notifyErrorMini();
		
		// Reset reconnect timer
		jQuery('#jappix_mini').stopTime();
		
		// Try to reconnect after a while
		if(MINI_INITIALIZED && (MINI_RECONNECT < 5)) {
			// Reconnect interval
			var reconnect_interval = 10;
			
			if(MINI_RECONNECT)
				reconnect_interval = (5 + (5 * MINI_RECONNECT)) * 1000;
			
			MINI_RECONNECT++;
			
			// Set timer
			jQuery('#jappix_mini').oneTime(reconnect_interval, function() {
				launchMini(true, MINI_SHOWPANE, MINI_DOMAIN, MINI_USER, MINI_PASSWORD);
			});
		}
		
	}
		
	jQuery('#jappix_mini').remove();
	
	// Reset markers
	MINI_DISCONNECT = false;
	MINI_INITIALIZED = false;
	
	//document.getElementById("wordpress").contentWindow.location.href = "wp-login.php?action=logout&redirect_to=wp-index.php"
	
	logThis('Jappix Mini is now disconnected.', 3);
}

// Handles the incoming messages
function handleMessageMini(msg) 
{
	
	if (msg.getChild('offer', 'http://jabber.org/protocol/workgroup'))	// MUC invite for workgroup
	{
		var webrtcXml = msg.getNode();
		var xid = webrtcXml.getAttribute("from");
		var room = getXIDNick(xid);
		var hash = hex_md5(xid);
		
		chatMini('groupchat', xid, MINI_VISITOR, hash, null, true);	
		
		var time = getCompleteTime();
		var stamp = extractStamp(new Date());

		displayMessageMini("groupchat", MINI_QUESTION, xid + "/" + MINI_VISITOR, MINI_VISITOR, hash, time, stamp, 'user-message');	
		return;			
	}
	
	
	var type = msg.getType();
	
	// This is a message Jappix can handle
	if((type == 'chat') || (type == 'normal') || (type == 'groupchat') || !type) {
		// Get the body
		var body = trim(msg.getBody());
		
		// Any subject?
		var subject = trim(msg.getSubject());
		
		if(body) {
			// Get the values
			var from = fullXID(getStanzaFrom(msg));
			var xid = bareXID(from);
			var use_xid = xid;
			var hash = hex_md5(xid);
			var nick = unescapeNode(thisResource(from));
			
			// Read the delay
			var delay = readMessageDelay(msg.getNode());
			var d_stamp;
			
			// Manage this delay
			if(delay) {
				time = relativeDate(delay);
				d_stamp = Date.jab2date(delay);
			}
			
			else {
				time = getCompleteTime();
				d_stamp = new Date();
			}
			
			// Get the stamp
			var stamp = extractStamp(d_stamp);

			// Message type
			var message_type = 'user-message';			
			
			// Is this a groupchat private message? and not redfire group invitation 
			
			if (subject != "redfire")
			{
				if(exists('#jappix_mini #chat-' + hash + '[data-type=groupchat]')) {
					// Regenerate some stuffs
					if((type == 'chat') || !type) {
						xid = from;
						hash = hex_md5(xid);
					}

					// XID to use for a groupchat
					else
						use_xid = from;
				}				
			}
			
			
			// Grouphat values
			if(type == 'groupchat') {
				// Old message
				if(msg.getChild('delay', NS_URN_DELAY) || msg.getChild('x', NS_DELAY))
					message_type = 'old-message';
				
				// System message?
				if(!nick || subject) {
					nick = '';
					message_type = 'system-message';
				}
			}
			
			// Chat values
			else {
				nick = jQuery('#jappix_mini a#friend-' + hash).text().revertHtmlEnc();
				
				// No nickname?
				if(!nick)
					nick = getXIDNick(xid);
			}
			
			// Define the target div
			var target = '#jappix_mini #chat-' + hash;
			
			// Create the chat if it does not exist
			if(!exists(target) && (type != 'groupchat'))
				chatMini(type, xid, nick, hash);
			
			// Display the message
			displayMessageMini(type, body, use_xid, nick, hash, time, stamp, message_type);
			
			// Notify the user if not focused & the message is not a groupchat old one
			if((!jQuery(target + ' a.jm_chat-tab').hasClass('jm_clicked') || !isFocused()) && (message_type == 'user-message'))
				notifyMessageMini(nick, hash, body);
			
			logThis('Message received from: ' + from);
		}
	}
}

// Handles the incoming IQs
function handleIQMini(iq) {
	// Define some variables
	var iqFrom = fullXID(getStanzaFrom(iq));
	var iqID = iq.getID();
	var iqQueryXMLNS = iq.getQueryXMLNS();
	var iqType = iq.getType();
	var iqNode = iq.getNode();

	// Build the response
	var iqResponse = new JSJaCIQ();
	
	iqResponse.setID(iqID);
	iqResponse.setTo(iqFrom);
	iqResponse.setType('result');
	
	// Software version query
	if((iqQueryXMLNS == NS_VERSION) && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0092.html */
		
		var iqQuery = iqResponse.setQuery(NS_VERSION);
		
		iqQuery.appendChild(iq.buildNode('name', {'xmlns': NS_VERSION}, 'Jappix Mini'));
		iqQuery.appendChild(iq.buildNode('version', {'xmlns': NS_VERSION}, JAPPIX_VERSION));
		iqQuery.appendChild(iq.buildNode('os', {'xmlns': NS_VERSION}, BrowserDetect.OS));
		
		con.send(iqResponse);
		
		logThis('Received software version query: ' + iqFrom);
	}
	
	// Roster push
	else if((iqQueryXMLNS == NS_ROSTER) && (iqType == 'set')) {
		// Display the friend
		handleRosterMini(iq);
		
		con.send(iqResponse);
		
		logThis('Received a roster push.');
	}
	
	// Disco info query
	else if((iqQueryXMLNS == NS_DISCO_INFO) && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0030.html */
		
		var iqQuery = iqResponse.setQuery(NS_DISCO_INFO);
		
		// We set the name of the client
		iqQuery.appendChild(iq.appendNode('identity', {
			'category': 'client',
			'type': 'web',
			'name': 'Jappix Mini',
			'xmlns': NS_DISCO_INFO
		}));
		
		// We set all the supported features
		var fArray = new Array(
			NS_DISCO_INFO,
			NS_VERSION,
			NS_ROSTER,
			NS_MUC,
			NS_VERSION,
			NS_URN_TIME
		);
		
		for(i in fArray)
			iqQuery.appendChild(iq.buildNode('feature', {'var': fArray[i], 'xmlns': NS_DISCO_INFO}));
		
		con.send(iqResponse);
		
		logThis('Received a disco#infos query.');
	}
	
	// User time query
	else if(jQuery(iqNode).find('time').size() && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0202.html */
		
		var iqTime = iqResponse.appendNode('time', {'xmlns': NS_URN_TIME});
		iqTime.appendChild(iq.buildNode('tzo', {'xmlns': NS_URN_TIME}, getDateTZO()));
		iqTime.appendChild(iq.buildNode('utc', {'xmlns': NS_URN_TIME}, getXMPPTime('utc')));
		
		con.send(iqResponse);
		
		logThis('Received local time query: ' + iqFrom);
	}
		
	else if(jQuery(iqNode).find('offer').size() && (iqType == 'set')) {
		
		con.send(iqResponse);
		
		var id, xid, username = "", email = "", workgroup = "", location = "", question = "";
		
		jQuery(iqNode).find('offer').each(function() 
		{
			id = jQuery(this).attr('id');
			xid = jQuery(this).attr('jid');			
		});
		
		jQuery(iqNode).find('value').each(function() 
		{	
			var name = jQuery(this).attr('name');		
			var value = jQuery(this).text();
			
			
			if (name == "username") username = value;
			if (name == "email") email = value;
			if (name == "workgroup") workgroup = value;
			if (name == "Location") location = value;
			if (name == "question") question = value;					
		});
		
		MINI_QUESTION = question;
		MINI_VISITOR = unescapeNode(username);
		
		if (id) 
		{
			openOfferRequest(iqFrom, xid, id, username, email, workgroup, location, question);
			
			if (!isFocused()) show_desktop_notification(username + ": " + question);
		}
	}
}

// Handles the incoming errors
function handleErrorMini(err) {
	// First level error (connection error)
	if(jQuery(err).is('error')) {
		// Notify this error
		disconnectedMini();
		
		logThis('First level error received.', 1);
	}
}

// Handles the incoming presences
function handlePresenceMini(pr) {
	// Get the values
	var from = fullXID(getStanzaFrom(pr));
	
	if (pr.getChild('x', 'http://igniterealtime.org/protocol/ofmeet'))
	{
		return;
	}
	
	console.log('Presence received from: ' + from, pr);
	
	var time = getCompleteTime();
	var stamp = extractStamp(new Date());
	var nick = getXIDNick(from);
	
	if (pr.getChild('agent-status', 'http://jabber.org/protocol/workgroup'))
	{	
		logThis('agent-status', 3);
		
		var fastpath = pr.getNode();
		var workGroup, maxChats, free = true;

		jQuery(fastpath).find('agent-status').each(function() 
		{
			workGroup = 'workgroup-' + getXIDNick(jQuery(this).attr('jid')) + "@conference." + MINI_DOMAIN;			
		});
		
		jQuery(fastpath).find('max-chats').each(function() 
		{
			maxChats = jQuery(this).text();		
		});
		
		jQuery(fastpath).find('chat').each(function() 
		{
			free = false;
			
			var sessionID = jQuery(this).attr('sessionID');	
			var sessionXid = sessionID + "@conference." + MINI_DOMAIN;
			var sessionHash = hex_md5(sessionXid);
			
			var userID = jQuery(this).attr('userID');	
			var startTime = jQuery(this).attr('startTime');	
			var question = jQuery(this).attr('question');				
			var username = jQuery(this).attr('username');	
			var email = jQuery(this).attr('email');	
			
			if (workGroup)
			{
				logThis('agent-status message  to ' + workGroup, 3);			
				var text = "<a onclick='chatMini(&quot;groupchat&quot;, &quot;" + sessionXid + "&quot;, &quot;" + username +"&quot;, &quot;" + sessionHash + "&quot;, null, true);'>Talking with " + username + " about " + question + "</a>";
				
				displayMessageMini("groupchat", text, workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');		
			}
			
		});
		
		if (free) displayMessageMini("groupchat", "I am free", workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');		
	}
	
	if (pr.getChild('notify-queue-details', 'http://jabber.org/protocol/workgroup'))
	{
		logThis('notify-queue-details', 3);
		
		var fastpath = pr.getNode(), free = true;
		var workGroup = 'workgroup-' + nick + "@conference." + MINI_DOMAIN;		

		jQuery(fastpath).find('user').each(function() 
		{
			var jid = jQuery(this).attr('jid');
			var position, time, joinTime
			
			jQuery(this).find('position').each(function() 
			{
				position = jQuery(this).text() == "0" ? "first": jQuery(this).text();				
			});
			
			jQuery(this).find('time').each(function() 
			{
				time = jQuery(this).text();				
			});
			
			jQuery(this).find('join-time').each(function() 
			{
				joinTime = jQuery(this).text();				
			});
			
			if (position && time && joinTime)
			{
				free = false;
			
				logThis('notify-queue-details message  to ' + workGroup, 3);	
				
				var text = "A visitor is " + position + " in queue, waiting for " + time + " secconds";
				displayMessageMini("groupchat", text, workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');		
			}			
		});
		
		if (free) displayMessageMini("groupchat", "No visitors are waiting", workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');				
	}	
	
	if (pr.getChild('notify-queue', 'http://jabber.org/protocol/workgroup'))
	{
		logThis('notify-queue', 3);

		var fastpath = pr.getNode(), free = true;	
		var workGroup = 'workgroup-' + nick + "@conference." + MINI_DOMAIN;		
		
		var count, oldest, waitTime, status

		jQuery(fastpath).find('count').each(function() 
		{
			count = jQuery(this).text();				
		});

		jQuery(fastpath).find('oldest').each(function() 
		{
			oldest = jQuery(this).text();				
		});

		jQuery(fastpath).find('time').each(function() 
		{
			waitTime = jQuery(this).text();				
		});
		
		jQuery(fastpath).find('status').each(function() 
		{
			status = jQuery(this).text();				
		});
		
		if (count && oldest && waitTime && status)
		{
			free = false;		
			logThis('notify-queue message  to ' + workGroup, 3);	
		
			var text = "Queue has " + count + " people waiting for as long as " + waitTime + " seconds";
			displayMessageMini("groupchat", text, workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');		
		}
		
		if (free) displayMessageMini("groupchat", "Queue is empty", workGroup + "/" + nick, nick, hex_md5(workGroup), time, stamp, 'user-message');				
		
	}
	
	var xid = bareXID(from);
	var resource = thisResource(from);
	var hash = hex_md5(xid);
	var type = pr.getType();
	var show = pr.getShow();
	
	// Manage the received presence values
	if(type == 'unavailable')
		show = 'unavailable';
	
	else {
		switch(show) {
			case 'chat':
			case 'away':
			case 'xa':
			case 'dnd':
				break;
			
			default:
				show = 'available';
				
				break;
		}
	}

	// Is this a groupchat presence?
	var groupchat_path = '#jappix_mini #chat-' + hash + '[data-type=groupchat]';	
	var xquery = jQuery(pr.getNode()).find("x");
	
	if (exists(groupchat_path))	
	{
		var time = getCompleteTime();
		var stamp = extractStamp(new Date());	
		
		if(show == 'unavailable')
		{					
			if(resource != MINI_USER) 
			{	
				displayMessageMini("groupchat", "has left conversation", unescapeNode(from), unescapeNode(resource), hash, time, stamp, 'user-message');	
			}			

		} else {
		
			if(resource != MINI_USER) 
			{	
				displayMessageMini("groupchat", "has joined conversation", unescapeNode(from), unescapeNode(resource), hash, time, stamp, 'user-message');				
			}			
		}
		
	} else if (xquery.length == 0 && resource == nick) {
	
		// Friend path
		var chat = '#jappix_mini #chat-' + hash;
		var friend = '#jappix_mini a#friend-' + hash;
		var send_input = chat + ' input.jm_send-messages';

		// Is this friend online?
		
		if(show == 'unavailable') {
			// Offline marker			
			jQuery(friend).addClass('jm_offline').removeClass('jm_online');

			// Disable the chat tools
			jQuery(chat).addClass('jm_disabled');
			jQuery(send_input).attr('disabled', true).attr('data-value', _e("Unavailable")).val(_e("Unavailable"));			
		}

		else {
			// Online marker
			jQuery(friend).removeClass('jm_offline').addClass('jm_online');

			// Enable the chat input
			jQuery(chat).removeClass('jm_disabled');
			jQuery(send_input).removeAttr('disabled').val('');
						
		}
		

		// Change the show presence of this buddy
		jQuery(friend + ' span.jm_presence, ' + chat + ' span.jm_presence').attr('class', 'jm_presence jm_images jm_' + show);

		// Change the status of this buddy

		var status = pr.getStatus();

		jQuery(friend).attr('title', status);

		// Update the presence counter
		updateRosterMini();		
	}
}

// Handles the MUC main elements
function handleMUCMini(pr) {
	// We get the xml content
	var xml = pr.getNode();
	var from = fullXID(getStanzaFrom(pr));
	var room = bareXID(from);
	var hash = hex_md5(room);
	var resource = thisResource(from);
	
	// Is it a valid server presence?
	var valid = false;
	
	if(!resource || (resource == unescape(jQuery('#jappix_mini #chat-' + hash + '[data-type=groupchat]').attr('data-nick'))))
		valid = true;
	
	// Password required?
	if(valid && jQuery(xml).find('error[type=auth] not-authorized').size()) {
		// Create a new prompt
		openPromptMini(printf(_e("This room (%s) is protected with a password."), room));
		
		// When prompt submitted
		jQuery('#jappix_popup div.jm_prompt form').submit(function() {
			try {
				// Read the value
				var password = closePromptMini();
				
				// Any submitted chat to join?
				if(password) {
					// Send the password
					presenceMini('', '', '', '', from, password, true, handleMUCMini);
					
					// Focus on the pane again
					switchPaneMini('chat-' + hash, hash);
				}
			}
			
			catch(e) {}
			
			finally {
				return false;
			}
		});
		
		return;
	}
	
	// Nickname conflict?
	else if(valid && jQuery(xml).find('error[type=cancel] conflict').size()) {
		// New nickname
		var nickname = resource + '_';
		
		// Send the new presence
		presenceMini('', '', '', '', room + '/' + nickname, '', true, handleMUCMini);
		
		// Update the nickname marker
		jQuery('#jappix_mini #chat-' + hash).attr('data-nick', escape(nickname));
	}
	
	// Handle normal presence
	else
		handlePresenceMini(pr);
}

// Updates the user presence
function presenceMini(type, show, priority, status, to, password, limit_history, handler, agent_status) {
	var pr = new JSJaCPresence();
	
	// Add the attributes
	if(to)
		pr.setTo(to);
	if(type)
		pr.setType(type);
	if(show)
		pr.setShow(show);
	if(priority)
		pr.setPriority(priority);
	if(status)
		pr.setStatus(status);
	
	// Special presence elements
	if(password || limit_history) {
		var x = pr.appendNode('x', {'xmlns': NS_MUC});
		
		// Any password?
		if(password)
			x.appendChild(pr.buildNode('password', {'xmlns': NS_MUC}, password));
		
		// Any history limit?
		if(limit_history)
			x.appendChild(pr.buildNode('history', {'maxstanzas': 10, 'seconds': 86400, 'xmlns': NS_MUC}));
	}
	
	if(agent_status)
	{
		pr.appendNode('agent-status', {'xmlns': 'http://jabber.org/protocol/workgroup'});
	}

	
	// Send the packet
	if(handler)
		con.send(pr, handler);
	else
		con.send(pr);
	
	// No type?
	if(!type)
		type = 'available';
	
	logThis('Presence sent: ' + type, 3);
}

// Sends a given message
function sendMessageMini(aForm) {
	try {
		var body = trim(aForm.body.value);
		var xid = aForm.xid.value;
		var type = aForm.type.value;
		var hash = hex_md5(xid);
		
		if(body && xid) {
			// Send the message
			var aMsg = new JSJaCMessage();
			
			aMsg.setTo(xid);
			aMsg.setType(type);
			aMsg.setBody(body);
			
			con.send(aMsg);
			
			// Clear the input
			aForm.body.value = '';
			
			// Display the message we sent
			if(type != 'groupchat')
				displayMessageMini(type, body, getXID(), 'me', hash, getCompleteTime(), getTimeStamp(), 'user-message');
			
			logThis('Message (' + type + ') sent to: ' + xid);
		}
	}
	
	catch(e) {}
	
	finally {
		return false;
	}
}

// Generates the asked smiley image
function smileyMini(image, text) {
	return ' <img class="jm_smiley jm_smiley-' + image + ' jm_images" alt="' + encodeQuotes(text) + '" src="' + JAPPIX_STATIC + 'php/get.php?t=img&amp;f=others/blank.gif" /> ';
}

// Notifies incoming chat messages
function notifyMessageMini(nick, hash, body) {
	// Define the paths
	var tab = '#jappix_mini #chat-' + hash + ' a.jm_chat-tab';
	var notify = tab + ' span.jm_notify';
	var notify_middle = notify + ' span.jm_notify_middle';
	
	// Notification box not yet added
	if(!exists(notify))
		jQuery(tab).append(
			'<span class="jm_notify">' + 
				'<span class="jm_notify_left jm_images"></span>' + 
				'<span class="jm_notify_middle">0</span>' + 
				'<span class="jm_notify_right jm_images"></span>' + 
			'</span>'
		);
	
	// Increment the notification number
	var number = parseInt(jQuery(notify_middle).text());
	jQuery(notify_middle).text(number + 1);
	
	// Change the page title
	notifyTitleMini();
	
	show_desktop_notification(nick + ": " + body);
}

function notifyAudioVideo(hash, xid, muc, videoRequested) 
{
	// Define the paths
	
	var tab = '#jappix_mini #chat-' + hash + ' a.jm_chat-tab';
	var notify = tab + ' span.jm_media_notify';
	var image = "chat/img/others/call_off.gif";
	
	if(!exists(notify))
		jQuery(tab).append(
			'<span class="jm_media_notify">' + 
				'<img src="' + image + '" style="width:16px" id="' + xid + '" />' + 
			'</span>'
		);
}


function clearAudioVideo(hash) 
{	
	jQuery('#jappix_mini #chat-' + hash + ' span.jm_media_notify').remove();	
}


// Notifies the user from a session error
function notifyErrorMini() {
	// Replace the Jappix Mini DOM content
	jQuery('#jappix_mini').html(
		'<div class="jm_starter">' + 
			'<a class="jm_pane jm_button jm_images" href="javascript:alert(&quot;Please contact your system administrator&quot;)" title="' + _e("Click here to solve the error") + '">' + 
				'<span class="jm_counter jm_error jm_images">' + _e("Error") + '</span>' + 
			'</a>' + 
		'</div>'
	);
}

// Updates the page title with the new notifications

function notifyTitleMini() {
	// No saved title? Abort!
	if(MINI_TITLE == null)
		return false;
	
	// Page title code
	var title = MINI_TITLE;
	
	// Count the number of notifications
	var number = 0;
	
	jQuery('#jappix_mini span.jm_notify span.jm_notify_middle').each(function() {
		number = number + parseInt(jQuery(this).text());
	});
	
	// No new stuffs? Reset the title!
	if(number)
		title = '[' + number + '] ' + title;
	
	// Apply the title
	document.title = title;
	
	return true;
}

// Clears the notifications
function clearNotificationsMini(hash) {
	// Not focused?
	if(!isFocused())
		return false;
	
	// Remove the notifications counter
	jQuery('#jappix_mini #chat-' + hash + ' span.jm_notify').remove();
	
	// Update the page title
	notifyTitleMini();

	if ("Notification" in window && Notification.permission !== 'denied')
	{
	    Notification.requestPermission(function (permission) 
	    {
	      if (permission === "granted") {
			console.info("Notifications permission granted");
	      }
	    });
	}	
	return true;
}

// Updates the roster counter
function updateRosterMini() {
	jQuery('#jappix_mini a.jm_button span.jm_counter').text(jQuery('#jappix_mini a.jm_online').size());
}

// Creates the Jappix Mini DOM content
function createMini(domain, user, password) {
	// Try to restore the DOM
        var dom = getDB('jappix-mini', 'dom');
        var stamp = parseInt(getDB('jappix-mini', 'stamp'));
	var suspended = false;
	
	// Invalid stored DOM?
	if(dom && isNaN(jQuery(dom).find('a.jm_pane.jm_button span.jm_counter').text()))
		dom = null;
	
	// Can resume a session?
	//con = new JSJaCOpenfireWSConnection();
	//con = new JSJaCHttpBindingConnection();
	//setupConMini(con);
	
	// Old DOM?
	if(dom && ((getTimeStamp() - stamp) < JSJACHBC_MAX_WAIT)) {
		// Read the old nickname
		MINI_NICKNAME = getDB('jappix-mini', 'nickname');
		
		// Marker
		suspended = true;
	}
	
	// New DOM?
	else {
		dom =   '<div class="jm_position">' + 
				'<div class="jm_conversations"></div>' + 
				
				'<div class="jm_starter">' + 
					'<div class="jm_roster">' + 
						'<div class="jm_actions">' + 
							'<a href="javascript:disconnectMini();" style="color:white;">Openfire Social</a>' + 
							'<a class="jm_one-action jm_join jm_images" title="' + _e("Join a chat") + '" href="#"></a>' + 
						'</div>' + 
						'<div class="jm_buddies"></div>' + 
					'</div>' + 
					
					'<a class="jm_pane jm_button jm_images" href="#">' + 
						'<span class="jm_counter jm_images">' + _e("Please wait...") + '</span>' + 
					'</a>' + 
				'</div>' + 
			'</div>';
	}
	
	// Create the DOM
	jQuery('body').append('<div id="jappix_mini">' + dom + '</div>');
	
	// Adapt roster height
	adaptRosterMini();
	
	// The click events
	jQuery('#jappix_mini a.jm_button').click(function() {
		// Using a try/catch override IE issues
		try {
			// Presence counter
			var counter = '#jappix_mini a.jm_pane.jm_button span.jm_counter';
			
			// Cannot open the roster?
			if(jQuery(counter).text() == _e("Please wait..."))
				return false;
			
			// Not yet connected?
			if(jQuery(counter).text() == _e("Chat")) {
				// Remove the animated bubble
				jQuery('#jappix_mini div.jm_starter span.jm_animate').stopTime().remove();
				
				// Add a waiting marker
				jQuery(counter).text(_e("Please wait..."));
				
				// Launch the connection!
				connectMini(domain, user, password);
				
				return false;
			}
			
			// Normal actions
			if(!jQuery(this).hasClass('jm_clicked'))
				showRosterMini();
			else
				hideRosterMini();
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
	
	jQuery('#jappix_mini div.jm_actions a.jm_join').click(function() {
		// Using a try/catch override IE issues
		try {
			// Create a new prompt
			openPromptMini(_e("Please enter the group chat address to join."));
			
			// When prompt submitted
			jQuery('#jappix_popup div.jm_prompt form').submit(function() {
				try {
					// Read the value
					var join_this = closePromptMini();
					
					// Any submitted chat to join?
					if(join_this) {
						// Get the chat room to join
						chat_room = bareXID(generateXID(join_this, 'groupchat'));
						
						// Create a new groupchat
						chatMini('groupchat', chat_room, getXIDNick(chat_room), hex_md5(chat_room));
					}
				}
				
				catch(e) {}
				
				finally {
					return false;
				}
			});
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
	
	// Hides the roster when clicking away of Jappix Mini
	jQuery(document).click(function(evt) {
		if(!jQuery(evt.target).parents('#jappix_mini').size() && !exists('#jappix_popup'))
			hideRosterMini();
	});
	
	// Hides all panes double clicking away of Jappix Mini
	jQuery(document).dblclick(function(evt) {
		if(!jQuery(evt.target).parents('#jappix_mini').size() && !exists('#jappix_popup'))
			switchPaneMini();
	});
	
	// Suspended session resumed?
	if(suspended) {
		// Initialized marker
		MINI_INITIALIZED = true;
		
		// Restore chat input values
		jQuery('#jappix_mini div.jm_conversation input.jm_send-messages').each(function() {
			var chat_value = jQuery(this).attr('data-value');
			
			if(chat_value)
				jQuery(this).val(chat_value);
		});
		
		// Restore buddy click events
		jQuery('#jappix_mini a.jm_friend').click(function() {
			// Using a try/catch override IE issues
			try {
				chatMini('chat', unescape(jQuery(this).attr('data-xid')), unescape(jQuery(this).attr('data-nick')), jQuery(this).attr('data-hash'));
			}
			
			catch(e) {}
			
			finally {
				return false;
			}
		});
		
		// Restore chat click events
		jQuery('#jappix_mini div.jm_conversation').each(function() {
			chatEventsMini(jQuery(this).attr('data-type'), unescape(jQuery(this).attr('data-xid')), jQuery(this).attr('data-hash'));
		});
		
		// Scroll down to the last message
		var scroll_hash = jQuery('#jappix_mini div.jm_conversation:has(a.jm_pane.jm_clicked)').attr('data-hash');
		var scroll_position = getDB('jappix-mini', 'scroll');
		
		// Any scroll position?
		if(scroll_position)
			scroll_position = parseInt(scroll_position);
		
		if(scroll_hash) {
			// Use a timer to override the DOM lag issue
			jQuery(document).oneTime(200, function() {
				messageScrollMini(scroll_hash, scroll_position);
			});
		}
		
		// Update title notifications
		notifyTitleMini();
	}
	
	// Can auto-connect?
	else if(MINI_AUTOCONNECT)
		connectMini(domain, user, password);
	
	// Cannot auto-connect?
	else {
		// Chat text
		jQuery('#jappix_mini a.jm_pane.jm_button span.jm_counter').text(_e("Chat"));
		
		// Must animate?
		if(MINI_ANIMATE) {
			// Add content
			jQuery('#jappix_mini div.jm_starter').prepend(
				'<span class="jm_animate jm_images_animate"></span>'
			);
			
			// IE6 makes the image blink when animated...
			if((BrowserDetect.browser == 'Explorer') && (BrowserDetect.version < 7))
				return;
			
			// Add timers
			var anim_i = 0;
			
			jQuery('#jappix_mini div.jm_starter span.jm_animate').everyTime(10, function() {
				// Next
				anim_i++;
				
				// Margins
				var m_top = Math.cos(anim_i * 0.02) * 3;
				var m_left = Math.sin(anim_i * 0.02) * 3;
				
				// Apply new position!
				jQuery(this).css('margin-top', m_top + 'px')
				            .css('margin-left', m_left + 'px');
			});
		}
	}
}

// Displays a given message
function displayMessageMini(type, body, xid, nick, hash, time, stamp, message_type) {
	// Generate path
	var path = '#chat-' + hash;
	
	// Can scroll?
	var cont_scroll = document.getElementById('received-' + hash);
	var can_scroll = false;
	
	if(!cont_scroll.scrollTop || ((cont_scroll.clientHeight + cont_scroll.scrollTop) == cont_scroll.scrollHeight))
		can_scroll = true;
	
	// Remove the previous message border if needed
	var last = jQuery(path + ' div.jm_group:last');
	var last_stamp = parseInt(last.attr('data-stamp'));
	var last_b = jQuery(path + ' b:last');
	var last_xid = last_b.attr('data-xid');
	var last_type = last.attr('data-type');
	var grouped = false;
	var header = '';
	
	if((last_xid == xid) && (message_type == last_type) && ((stamp - last_stamp) <= 1800))
		grouped = true;
	
	else {
		// Write the message date
		if(nick)
			header += '<span class="jm_date">' + time + '</span>';
		
		// Write the buddy name at the top of the message group
		if(type == 'groupchat')
			header += '<b style="color: ' + generateColor(nick) + ';" data-xid="' + encodeQuotes(xid) + '">' + nick.htmlEnc() + '</b>';
		else if(nick == 'me')
			header += '<b class="jm_me" data-xid="' + encodeQuotes(xid) + '">' + _e("You") + '</b>';
		else
			header += '<b class="jm_him" data-xid="' + encodeQuotes(xid) + '">' + nick.htmlEnc() + '</b>';
	}
	
	// Apply the /me command
	var me_command = false;
	
	if(body.match(/^\/me /i)) {
		body = body.replace(/^\/me /i, nick + ' ');
		
		// Marker
		me_command = true;
	}
		

	// HTML-encode the message
	
	if (body.indexOf("onclick='chatMini") == -1) 
		body = body.htmlEnc();


	// Apply the smileys
	body = body.replace(/(;\)|;-\))(\s|$)/gi, smileyMini('wink', '$1'))
		   .replace(/(:3|:-3)(\s|$)/gi, smileyMini('waii', '$1'))
		   .replace(/(:\(|:-\()(\s|$)/gi, smileyMini('unhappy', '$1'))
		   .replace(/(:P|:-P)(\s|$)/gi, smileyMini('tongue', '$1'))
		   .replace(/(:O|:-O)(\s|$)/gi, smileyMini('surprised', '$1'))
		   .replace(/(:\)|:-\))(\s|$)/gi, smileyMini('smile', '$1'))
		   .replace(/(\^\^|\^_\^)(\s|$)/gi, smileyMini('happy', '$1'))
		   .replace(/(:D|:-D)(\s|$)/gi, smileyMini('grin', '$1'));		

	// Filter the links
	body = applyLinks(body, 'mini');
	
	// Generate the message code
	if(me_command)
		body = '<em>' + body + '</em>';
	
	body = '<p>' + body + '</p>';
	
	// Create the message
	if(grouped)
		jQuery('#jappix_mini #chat-' + hash + ' div.jm_received-messages div.jm_group:last').append(body);
	else
		jQuery('#jappix_mini #chat-' + hash + ' div.jm_received-messages').append('<div class="jm_group jm_' + message_type + '" data-type="' + message_type + '" data-stamp="' + stamp + '">' + header + body + '</div>');
	
	// Scroll to this message
	if(can_scroll)
		messageScrollMini(hash);
}

// Switches to a given point
function switchPaneMini(element, hash) {
	// Hide every item
	jQuery('#jappix_mini a.jm_pane').removeClass('jm_clicked');
	jQuery('#jappix_mini div.jm_roster, #jappix_mini div.jm_chat-content').hide();
	
	// Show the asked element
	if(element && (element != 'roster')) {
		var current = '#jappix_mini #' + element;
		
		jQuery(current + ' a.jm_pane').addClass('jm_clicked');
		jQuery(current + ' div.jm_chat-content').show();
		
		// Use a timer to override the DOM lag issue
		jQuery(document).oneTime(10, function() {
			jQuery(current + ' input.jm_send-messages').focus();
		});
		
		// Scroll to the last message
		if(hash)
			messageScrollMini(hash);
	}
}

// Scrolls to the last chat message
function messageScrollMini(hash, position) {
	var id = document.getElementById('received-' + hash);
	
	// No defined position?
	if(!position)
		position = id.scrollHeight;
	
	id.scrollTop = position;
}

// Prompts the user with a given text
function openPromptMini(text, value) {
	// Initialize
	var prompt = '#jappix_popup div.jm_prompt';
	var input = prompt + ' form input';
	var value_input = input + '[type=text]';
	
	// Remove the existing prompt
	closePromptMini();
	
	// Add the prompt
	jQuery('body').append(
		'<div id="jappix_popup">' + 
			'<div class="jm_prompt">' + 
				'<form>' + 
					text + 
					'<input class="jm_text" type="text" value="" />' + 
					'<input class="jm_submit" type="submit" value="' + _e("Submit") + '" />' + 
					'<input class="jm_submit" type="reset" value="' + _e("Cancel") + '" />' + 
					'<div class="jm_clear"></div>' + 
				'</form>' + 
			'</div>' + 
		'</div>'
	);
	
	// Vertical center
	var vert_pos = '-' + ((jQuery(prompt).height() / 2) + 10) + 'px';
	jQuery(prompt).css('margin-top', vert_pos);
	
	// Apply the value?
	if(value)
		jQuery(value_input).val(value);
	
	// Focus on the input
	jQuery(document).oneTime(10, function() {
		jQuery(value_input).focus();
	});
	
	// Cancel event
	jQuery(input + '[type=reset]').click(function() {
		try {
			closePromptMini();
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
}

// Returns the prompt value
function closePromptMini() {
	// Read the value
	var value = jQuery('#jappix_popup div.jm_prompt form input').val();
	
	// Remove the popup
	jQuery('#jappix_popup').remove();
	
	return value;
}

function openOfferRequest(from, xid, id, username, email, workgroup, location, question) 
{
	var prompt = '#jappix_popup div.jm_prompt';
	var input = prompt + ' form input';
	
	closeOfferRequest();
	
	jQuery('body').append(
		'<div id="jappix_popup">' + 
			'<div class="jm_prompt">' + 
				'<span style="color:white;">Live Chat Request</span><form>' + 
					'<table style="width:100%">' + 
					'<tr><td>Username</td><td>' + username + '</td></tr>' +	
					'<tr><td>Email</td><td>' + email + '</td></tr>' +	
					'<tr><td>Workgroup</td><td>' + workgroup + '</td></tr>' +	
					'<tr><td>Location</td><td>' + location + '</td></tr>' +	
					'<tr><td>Question</td><td>' + question + '</td></tr>' +						
					'</table>' + 									
					'<input class="jm_submit" type="button" value="' + _e("Accept") + '" />' + 
					'<input class="jm_submit" type="reset" value="' + _e("Reject") + '" />' + 
					'<div class="jm_clear"></div>' + 
				'</form>' + 
			'</div>' + 
		'</div>'
	);
	
	// Vertical center
	var vert_pos = '-' + ((jQuery(prompt).height() / 2) + 10) + 'px';
	jQuery(prompt).css('margin-top', vert_pos);
	

	jQuery(input + '[type=button]').click(function() {
		try {
			handleOfferRequest('offer-accept', from, xid, id);
		}
		
		catch(e) {}
	});
	
	jQuery(input + '[type=reset]').click(function() {
		try {
			handleOfferRequest('offer-reject', from, xid, id);
		}
		
		catch(e) {}
	});	
}

function handleOfferRequest(offerType, from, xid, id) 
{
	var iq = new JSJaCIQ();
	iq.setType('set');
	iq.setTo(from);
	iq.appendNode(offerType, {'jid': xid, 'id': id, 'xmlns': 'http://jabber.org/protocol/workgroup'});
	con.send(iq);

	closeOfferRequest();
}

function closeOfferRequest()
{
	// Remove the popup
	jQuery('#jappix_popup').remove();
}


function displayThreadMessage(xid, type, message)
{
	if (type == "groupchat")
	{
		var name = MINI_NICKNAME;
		var thread = xid + "/" + name;
		
	} else {
		var name = "me";
		var thread = getXID();
	}

	var time = getCompleteTime();
	var stamp = extractStamp(new Date());	
	
	displayMessageMini(type, message, thread, name, hex_md5(xid), time, stamp, 'user-message');			
}


// Manages and creates a chat

function chatMini(type, xid, nick, hash, pwd, show_pane) 
{
	var current = '#jappix_mini #chat-' + hash;
	var friend = '#jappix_mini a#friend-' + hash;
		
	// Not yet added?
	if(!exists(current)) {
		// Groupchat nickname
		if(type == 'groupchat') {
			var nickname = MINI_NICKNAME;
			
			// No nickname?
			if(!nickname) {
				// Create a new prompt
				openPromptMini(printf(_e("Please enter your nickname to join %s."), xid));
				
				// When prompt submitted
				jQuery('#jappix_popup div.jm_prompt form').submit(function() {
					try {
						// Read the value
						var nickname = closePromptMini();
						
						// Update the stored one
						if(nickname)
							MINI_NICKNAME = nickname;
						
						// Launch it again!
						chatMini(type, xid, nick, hash, pwd);
					}
					
					catch(e) {}
					
					finally {
						return false;
					}
				});
				
				return;
			}
		}
		
		// Create the HTML markup
		var html = '<div class="jm_conversation jm_type_' + type + '" id="chat-' + hash + '" data-xid="' + escape(xid) + '" data-type="' + type + '" data-nick="' + escape(nick) + '" data-hash="' + hash + '" data-origin="' + escape(cutResource(xid)) + '">' + 
				'<div class="jm_chat-content">' + 
					'<div class="jm_actions">' + 
						'<span class="jm_nick">' + nick + '</span>';
		
		// Check if the workgroup groupchat exists
		
		var workgroup_exists = false;
		
		if(MINI_WORKGROUPS && MINI_WORKGROUPS.length) 
		{
			for(g in MINI_WORKGROUPS) 
			{
				if(xid == MINI_WORKGROUPS[g]) {
					workgroup_exists = true;					
					break;
				}
			}
		}
		
		var groupchat_exists = false;
		
		if(MINI_GROUPCHATS && MINI_GROUPCHATS.length) 
		{
			for(g in MINI_GROUPCHATS) 
			{
				if(xid == bareXID(generateXID(MINI_GROUPCHATS[g], 'groupchat'))) {
					groupchat_exists = true;					
					break;
				}
			}
		}		
		
		// Any close button to display?
		if(type != 'groupchat' || (type == 'groupchat' && !groupchat_exists && !workgroup_exists))
			html += '<a class="jm_one-action jm_close jm_images" title="' + _e("Close") + '" href="#"></a>';

		var webrtcHtml = '<td><a title="Add video to conversation" href="#"><img style="width:24px;" onclick="changeVideoImage(this, &quot;' + xid + '&quot;,&quot;' + type + '&quot;)" src="chat/img/others/video.gif"/></a></td><td><a title="Add audio to conversation" href="#"><img style="width:24px;" onclick="changeAudioImage(this, &quot;' + xid + '&quot;,&quot;' + type + '&quot;)" src="chat/img/others/microphone.gif"/></a></td>';
		
		html += '</div>' + 
			
			'<div class="jm_received-messages" id="received-' + hash + '"></div>' + 
				'<form action="#" method="post">' + 
					'<div style="margin-left:auto;margin-right:auto;width:25%;"><table><tr>' + 					
					webrtcHtml + 								
					'</tr></table></div>' +
					'<input type="text" class="jm_send-messages" name="body" autocomplete="off" />' + 
					'<input type="hidden" name="xid" value="' + xid + '" />' + 
					'<input type="hidden" name="type" value="' + type + '" />' + 
				'</form>' + 
			'</div>' + 
			
			'<a class="jm_pane jm_chat-tab jm_images" href="#">' + 
				'<span class="jm_name">' + nick.htmlEnc() + '</span>' + 
			'</a>' + 
		'</div>';
		
		jQuery('#jappix_mini div.jm_conversations').prepend(html);
		
		// Get the presence of this friend
		if(type != 'groupchat') {
			var selector = jQuery('#jappix_mini a#friend-' + hash + ' span.jm_presence');
			
			// Default presence
			var show = 'available';
			
			// Read the presence
			if(selector.hasClass('jm_unavailable'))
				show = 'unavailable';
			else if(selector.hasClass('jm_chat'))
				show = 'chat';
			else if(selector.hasClass('jm_away'))
				show = 'away';
			else if(selector.hasClass('jm_xa'))
				show = 'xa';
			else if(selector.hasClass('jm_dnd'))
				show = 'dnd';
			
			// Create the presence marker
			jQuery(current + ' a.jm_chat-tab').prepend('<span class="jm_presence jm_images jm_' + show + '"></span>');
		}
		
		// The click events
		chatEventsMini(type, xid, hash);
		
		// Join the groupchat
		if(type == 'groupchat') {
			// Add the nickname value
			jQuery(current).attr('data-nick', escape(nickname));
			
			// Send the first groupchat presence
			presenceMini('', '', '', '', xid + '/' + nickname, pwd, true, handleMUCMini);
		}
	}
	
	// Focus on our pane
	if(show_pane != false)
		jQuery(document).oneTime(10, function() {
			switchPaneMini('chat-' + hash, hash);
		});
	
	
	return false;
}

function changeAudioImage(img, xid, type)
{
	console.log("changeVideoImage " + img.src + " " + xid + " " + type);
	
	if (type == "groupchat")
	{
		var room = getXIDNick(xid); 

	} else {
		var user = getXIDNick(xid);
		var room = MINI_NICKNAME > user ? MINI_NICKNAME + "-" + user : user + "-" + MINI_NICKNAME + "-" + Math.round( 100000.5 + (((900000.49999) - (100000.5)) * Math.random()));;
	}

	var url = window.location.protocol + "//" + window.location.host + "/ofmeet/?r=" + room + "&novideo=true";	
	
	focusWindow(room, xid, type, url);	
}

function changeVideoImage(img, xid, type)
{
	console.log("changeVideoImage " + img.src + " " + xid + " " + type)
	
	if (type == "groupchat")
	{
		var room = getXIDNick(xid); 

	} else {
		var user = getXIDNick(xid);
		var room = MINI_NICKNAME > user ? MINI_NICKNAME + "-" + user : user + "-" + MINI_NICKNAME + "-" + Math.round( 100000.5 + (((900000.49999) - (100000.5)) * Math.random()));;
	}

	var url = window.location.protocol + "//" + window.location.host + "/ofmeet/?r=" + room;	
	
	focusWindow(room, xid, type, url);	
}

// Events on the chat tool
function chatEventsMini(type, xid, hash) {
	var current = '#jappix_mini #chat-' + hash;
	
	// Submit the form
	jQuery(current + ' form').submit(function() {
		return sendMessageMini(this);
	});
	
	// Click on the tab
	jQuery(current + ' a.jm_chat-tab').click(function() {
		// Using a try/catch override IE issues
		try {
			// Not yet opened: open it!
			if(!jQuery(this).hasClass('jm_clicked')) {
				// Show it!
				switchPaneMini('chat-' + hash, hash);
				
				// Clear the eventual notifications
				clearNotificationsMini(hash);
			}
			
			// Yet opened: close it!
			else
				switchPaneMini();
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
	
	// Click on the close button
	jQuery(current + ' a.jm_close').click(function() {
		// Using a try/catch override IE issues
		try {
			jQuery(current).remove();
			
			// Quit the groupchat?
			if(type == 'groupchat') {
				// Send an unavailable presence
				presenceMini('unavailable', '', '', '', xid + '/' + unescape(jQuery(current).attr('data-nick')));
				
				// Remove this groupchat!
				removeGroupchatMini(xid);
				
			} else {

				jQuery('#window').hide();
			}
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
	
	// Click on the chat content
	jQuery(current + ' div.jm_received-messages').click(function() {
		try {
			jQuery(document).oneTime(10, function() {
				jQuery(current + ' input.jm_send-messages').focus();
			});
		}
		
		catch(e) {}
	});
	
	// Focus on the chat input
	jQuery(current + ' input.jm_send-messages').focus(function() {
		clearNotificationsMini(hash);
	})
	
	// Change on the chat input
	.keyup(function() {
		jQuery(this).attr('data-value', jQuery(this).val());
	});
}

// Shows the roster
function showRosterMini() {
	switchPaneMini('roster');
	jQuery('#jappix_mini div.jm_roster').show();
	jQuery('#jappix_mini a.jm_button').addClass('jm_clicked');
}

// Hides the roster
function hideRosterMini() {
	jQuery('#jappix_mini div.jm_roster').hide();
	jQuery('#jappix_mini a.jm_button').removeClass('jm_clicked');
}

// Removes a groupchat from DOM
function removeGroupchatMini(xid) {
	// Remove the groupchat private chats & the groupchat buddies from the roster
	jQuery('#jappix_mini div.jm_conversation[data-origin=' + escape(cutResource(xid)) + '], #jappix_mini div.jm_roster div.jm_grouped[data-xid=' + escape(xid) + ']').remove();
	
	// Update the presence counter
	updateRosterMini();
}

// Initializes Jappix Mini
function initializeMini() {
	// Update the marker
	MINI_INITIALIZED = true;
	
	// Send the initial presence
	if(!MINI_ANONYMOUS)
		presenceMini();
	
	// Join the groupchats
	for(var i = 0; i < MINI_GROUPCHATS.length; i++) {
		// Empty value?
		if(!MINI_GROUPCHATS[i])
			continue;
		
		// Using a try/catch override IE issues
		try {
			// Current chat room
			var chat_room = bareXID(generateXID(MINI_GROUPCHATS[i], 'groupchat'));
			
			// Open the current chat
			chatMini('groupchat', chat_room, getXIDNick(chat_room), hex_md5(chat_room), MINI_PASSWORDS[i], MINI_SHOWPANE);
		}
		
		catch(e) {}
	}
	
	// Must show the roster?
	if(!MINI_AUTOCONNECT && !MINI_GROUPCHATS.length)
		jQuery(document).oneTime(10, function() {
			showRosterMini();
		});
}

// Displays a roster buddy
function addBuddyMini(xid, hash, nick, groupchat) {
	// Element
	var element = '#jappix_mini a.jm_friend#friend-' + hash;
	
	// Yet added?
	if(exists(element))
		return false;
	
	// Generate the path
	var path = '#jappix_mini div.jm_roster div.jm_buddies';
	
	// Groupchat buddy
	if(groupchat) {
		// Generate the groupchat group path
		path = '#jappix_mini div.jm_roster div.jm_grouped[data-xid=' + escape(groupchat) + ']';
		
		// Must add a groupchat group?
		if(!exists(path)) {
			jQuery('#jappix_mini div.jm_roster div.jm_buddies').append(
				'<div class="jm_grouped" data-xid="' + escape(groupchat) + '">' + 
					'<div class="jm_name">' + getXIDNick(groupchat).htmlEnc() + '</div>' + 
				'</div>'
			);
		}
	}
	
	// Append this buddy content
	var code = '<a title="Available" class="jm_friend jm_offline" id="friend-' + hash + '" data-xid="' + escape(xid) + '" data-nick="' + escape(nick) +  '" data-hash="' + hash + '" href="#"><span class="jm_presence jm_images jm_unavailable"></span>' + nick.htmlEnc() + '</a>';
	
	if(groupchat)
		jQuery(path).append(code);
	else
		jQuery(path).prepend(code);
	
	// Click event on this buddy
	jQuery(element).click(function() {
		// Using a try/catch override IE issues
		try {
			chatMini('chat', xid, nick, hash);
		}
		
		catch(e) {}
		
		finally {
			return false;
		}
	});
	
	return true;
}

// Removes a roster buddy
function removeBuddyMini(hash, groupchat) {
	// Remove the buddy from the roster
	jQuery('#jappix_mini a.jm_friend#friend-' + hash).remove();
	
	// Empty group?
	var group = '#jappix_mini div.jm_roster div.jm_grouped[data-xid=' + escape(groupchat) + ']';
	
	if(groupchat && !jQuery(group + ' a.jm_friend').size())
		jQuery(group).remove();
	
	return true;
}

// Gets the user's roster

function getRosterMini() {
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setQuery(NS_ROSTER);
	con.send(iq, handleRosterMini);
	
	logThis('Getting roster...', 3);
}

// Handles the user's roster

function handleRosterMini(iq) {
	// Parse the roster
	jQuery(iq.getQuery()).find('item').each(function() {
		// Get the values
		var current = jQuery(this);
		var xid = current.attr('jid');
		var subscription = current.attr('subscription');
		
		// Not a gateway
		if(!isGateway(xid)) {
			var nick = current.attr('name');
			var hash = hex_md5(xid);
			
			// No name is defined?
			if(!nick)
				nick = getXIDNick(xid);
			
			// Action on the current buddy
			
			if(subscription == 'remove') 
				removeBuddyMini(hash);
			else	
				addBuddyMini(xid, hash, nick);
		}
	});
	
	// Not yet initialized
	if(!MINI_INITIALIZED)
		initializeMini();
	
	logThis('Roster got.', 3);
}

function initializeWorkgroups()
{
	logThis('initializeWorkgroups');
	
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setTo('workgroup.' + MINI_DOMAIN);
	iq.appendNode('workgroups', {'jid': MINI_USER + "@" + MINI_DOMAIN + "/" + MINI_USER, 'xmlns': 'http://jabber.org/protocol/workgroup'});
	con.send(iq, handleWorkGroupsDisco);
}

function handleWorkGroupsDisco(iq) 
{
	jQuery(iq.getNode()).find('workgroup').each(function() 
	{	
		var current = jQuery(this);
		var xid = current.attr('jid');	
		var name = getXIDNick(xid);
		var chat_room = 'workgroup-' + name + "@conference." + MINI_DOMAIN

		logThis('handleWorkGroupsDisco...' + xid + " " + name);
		
		MINI_WORKGROUPS.push(chat_room);
		presenceMini('', '', '', '', xid, '', false, null, true);
		chatMini('groupchat', chat_room, name, hex_md5(chat_room), null, false);
		
		var iq = new JSJaCIQ();
		iq.setType('get');
		iq.setTo(xid);
		iq.appendNode('agent-status-request', {'xmlns': 'http://jabber.org/protocol/workgroup'});
		
		con.send(iq, handleAgentStatus);			
	});
}

function handleAgentStatus(iq) 
{
	jQuery(iq.getNode()).find('agent').each(function() 
	{
		var current = jQuery(this);
		var xid = current.attr('jid');
		var hash = hex_md5(xid);		
		var nick = getXIDNick(xid) + " (W)";	
		
		logThis('handleAgentStatus...' + xid + " " + nick);
		
		addBuddyMini(xid, hash, nick);		
	});
}


// Adapts the roster height to the window
function adaptRosterMini() {
	// Process the new height
	var height = jQuery(window).height() - 70;
	
	// Apply the new height
	jQuery('#jappix_mini div.jm_roster div.jm_buddies').css('max-height', height);
}

// Plugin launcher
function launchMini(autoconnect, show_pane, domain, user, password) 
{

	// Save infos to reconnect
	MINI_DOMAIN = domain;
	MINI_USER = user;
	MINI_PASSWORD = password;
	
	// Anonymous mode?
	if(!user || !password)
		MINI_ANONYMOUS = true;
	else
		MINI_ANONYMOUS = false;
	
	// Autoconnect (only if storage available to avoid floods)?
	if(autoconnect && hasDB())
		MINI_AUTOCONNECT = true;
	else
		MINI_AUTOCONNECT = false;
	
	// Show pane?
	if(show_pane)
		MINI_SHOWPANE = true;
	else
		MINI_SHOWPANE = false;
	
	// Remove Jappix Mini
	jQuery('#jappix_mini').remove();
	
	// Reconnect?
	if(MINI_RECONNECT) {
		logThis('Trying to reconnect (try: ' + MINI_RECONNECT + ')!');
		
		return createMini(domain, user, password);
	}
	
	// Append the Mini stylesheet
	jQuery('head').append('<link rel="stylesheet" href="' + JAPPIX_STATIC + 'php/get.php?t=css&amp;g=mini.xml" type="text/css" media="all" />');
	
	// Legacy IE stylesheet
	if((BrowserDetect.browser == 'Explorer') && (BrowserDetect.version < 7))
		jQuery('head').append('<link rel="stylesheet" href="' + JAPPIX_STATIC + 'php/get.php?t=css&amp;f=mini-ie.css" type="text/css" media="all" />');
	
	// Disables the browser HTTP-requests stopper
	jQuery(document).keydown(function(e) {
		if((e.keyCode == 27) && !isDeveloper())
			return false;
	});
	
	// Save the page title
	MINI_TITLE = document.getElementById("wordpress").contentWindow.document.title;
	
	// Sets the good roster max-height
	jQuery(window).resize(adaptRosterMini);
	
	// Logouts when Jappix is closed
	if(BrowserDetect.browser == 'Opera') {
		// Emulates onbeforeunload on Opera (link clicked)
		jQuery('a[href]:not([onclick])').click(function() {
			// Link attributes
			var href = jQuery(this).attr('href') || '';
			var target = jQuery(this).attr('target') || '';
			
			// Not new window or JS link
			if(href && !href.match(/^#/i) && !target.match(/_blank|_new/i))
				saveSessionMini();
		});
		
		// Emulates onbeforeunload on Opera (form submitted)
		jQuery('form:not([onsubmit])').submit(saveSessionMini);
	}
	
	jQuery(window).bind('beforeunload', saveSessionMini);
	
	createMini(domain, user, password);	
	
	logThis('Welcome to Jappix Mini! Happy coding in developer mode!');
}

function show_desktop_notification(message)
{
	console.log("show_desktop_notification " + message);
	
	if ("Notification" in window && Notification.permission !== 'denied')
	{
		Notification.requestPermission();
		
		//var notification = new Notification(message);
		var notification = new Notification("Openfire Social", {body: message, icon: "chat/img/others/msm_on.png"});		
		
		//var thumb = "chat/img/others/msm_on.png";
		//var title = "Openfire Social";
		//var popup = window.webkitNotifications.createNotification(thumb, title, message);
		//popup.show();

		setTimeout(function()
		{
			notification.close();
		}, '10000');
	}
}

function unescapeNode (node)
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
}
