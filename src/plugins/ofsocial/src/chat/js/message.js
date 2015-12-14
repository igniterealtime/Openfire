/*

Jappix - An open social platform
These are the messages JS scripts for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Maranda
Last revision: 24/06/11

*/

// Handles the incoming message packets
function handleMessage(message) {
	// Error packet? Stop!
	if(handleErrorReply(message))
		return;
	
	// We get the message items
	var from = fullXID(getStanzaFrom(message));
	var id = message.getID();
	var type = message.getType();
	var body = trim(message.getBody());
	var node = message.getNode();
	var subject = trim(message.getSubject());
	
	// We generate some values
	var xid = bareXID(from);
	var resource = thisResource(from);
	var hash = hex_md5(xid);
	var xHTML = $(node).find('html body').size();
	var GCUser = false;
	
	// This message comes from a groupchat user
	if(isPrivate(xid) && ((type == 'chat') || !type) && resource) {
		GCUser = true;
		xid = from;
		hash = hex_md5(xid);
	}
	
	// Get message date
	var time, stamp, d_stamp;
	
	// Read the delay
	var delay = readMessageDelay(node);
	
	// Any delay?
	if(delay) {
		time = relativeDate(delay);
		d_stamp = Date.jab2date(delay);
	}
	
	// No delay: get actual time
	else {
		time = getCompleteTime();
		d_stamp = new Date();
	}
	
	// Get the date stamp
	stamp = extractStamp(d_stamp);
	
	// Received message
	if(hasReceived(message))
		return messageReceived(hash, id);
	
	// Chatstate message
	if(node && !delay && ((((type == 'chat') || !type) && !exists('#page-switch .' + hash + ' .unavailable')) || (type == 'groupchat'))) {
		/* REF: http://xmpp.org/extensions/xep-0085.html */
		
		// Re-process the hash
		var chatstate_hash = hash;
		
		if(type == 'groupchat')
			chatstate_hash = hex_md5(from);
		
		// Do something depending of the received state
		if($(node).find('active').size()) {
			displayChatState('active', chatstate_hash, type);
			
			// Tell Jappix the entity supports chatstates
			$('#' + chatstate_hash + ' .message-area').attr('data-chatstates', 'true');
			
			logThis('Active chatstate received from: ' + from);
		}
		
		else if($(node).find('composing').size())
			displayChatState('composing', chatstate_hash, type);
		
		else if($(node).find('paused').size())
			displayChatState('paused', chatstate_hash, type);
		
		else if($(node).find('inactive').size())
			displayChatState('inactive', chatstate_hash, type);
		
		else if($(node).find('gone').size())
			displayChatState('gone', chatstate_hash, type);
	}
	
	// Invite message
	if($(node).find('x[xmlns=' + NS_MUC_USER + '] invite').size()) {
		// We get the needed values
		var iFrom = $(node).find('x[xmlns=' + NS_MUC_USER + '] invite').attr('from');
		var iRoom = $(node).find('x[xmlns=' + NS_XCONFERENCE + ']').attr('jid');
		
		// Old invite method?
		if(!iRoom)
			iRoom = from;
		
		// We display the notification
		newNotification('invite_room', iFrom, [iRoom], body);
		
		logThis('Invite Request from: ' + iFrom + ' to join: ' + iRoom);
		
		return false;
	}
	
	// Request message
	if(message.getChild('confirm', NS_HTTP_AUTH)) {
		// Open a new notification
		newNotification('request', xid, [message], body);
		
		logThis('HTTP Request from: ' + xid);
		
		return false;
	}
	
	// Roster Item Exchange message
	if(message.getChild('x', NS_ROSTERX)) {
		// Open a new notification
		newNotification('rosterx', xid, [message], body);
		
		logThis('Roster Item Exchange from: ' + xid);
		
		return false;
	}
	
	// Normal message
	if((type == 'normal') && body) {
		// Message date
		var messageDate = delay;
		
		// No message date?
		if(!messageDate)
			messageDate = getXMPPTime('utc');
		
		// Message ID
		var messageID = hex_md5(xid + subject + messageDate);
		
		// We store the received message
		storeInboxMessage(xid, subject, body, 'unread', messageID, messageDate);
		
		// Display the inbox message
		if(exists('#inbox'))
			displayInboxMessage(xid, subject, body, 'unread', messageID, messageDate);
		
		// Check we have new messages (play a sound if any unread messages)
		if(checkInboxMessages())
			soundPlay(2);
		
		// Send it to the server
		storeInbox();
		
		return false;
	}
	
	// PubSub event
	if($(node).find('event').attr('xmlns') == NS_PUBSUB_EVENT) {
		// We get the needed values
		var iParse = $(node).find('event items');
		var iNode = iParse.attr('node');
		
		// Turn around the different result cases
		if(iNode) {
			switch(iNode) {
				// Mood
				case NS_MOOD:
					// Retrieve the values
					var iMood = iParse.find('mood');
					var fValue = '';
					var tText = '';
					
					// There's something
					if(iMood.children().size()) {
						// Read the value
						fValue = node.getElementsByTagName('mood').item(0).childNodes.item(0).nodeName;
						
						// Read the text
						tText = iMood.find('text').text();
						
						// Avoid errors
						if(!fValue)
							fValue = '';
					}
					
					// Store the PEP event (and display it)
					storePEP(xid, 'mood', fValue, tText);
					
					break;
				
				// Activity
				case NS_ACTIVITY:
					// Retrieve the values
					var iActivity = iParse.find('activity');
					var sValue = '';
					var tText = '';
					
					// There's something
					if(iActivity.children().size()) {
						// Read the value
						fValue = node.getElementsByTagName('activity').item(0).childNodes.item(0).nodeName;
						
						// Read the text
						tText = iActivity.find('text').text();
						
						// Avoid errors
						if(!fValue)
							fValue = '';
					}
					
					// Store the PEP event (and display it)
					storePEP(xid, 'activity', fValue, tText);
					
					break;
				
				// Tune
				case NS_TUNE:
					// Retrieve the values
					var iTune = iParse.find('tune');
					var tArtist = iTune.find('artist').text();
					var tSource = iTune.find('source').text();
					var tTitle = iTune.find('title').text();
					var tURI = iTune.find('uri').text();
					
					// Store the PEP event (and display it)
					storePEP(xid, 'tune', tArtist, tTitle, tSource, tURI);
					
					break;
				
				// Geolocation
				case NS_GEOLOC:
					// Retrieve the values
					var iGeoloc = iParse.find('geoloc');
					var tLat = iGeoloc.find('lat').text();
					var tLon = iGeoloc.find('lon').text();
					
					// Any extra-values?
					var tLocality = iGeoloc.find('locality').text();
					var tRegion = iGeoloc.find('region').text();
					var tCountry = iGeoloc.find('country').text();
					var tHuman = humanPosition(tLocality, tRegion, tCountry);
					
					// Store the PEP event (and display it)
					storePEP(xid, 'geoloc', tLat, tLon, tHuman);
					
					break;
				
				// Microblog
				case NS_URN_MBLOG:
					displayMicroblog(message, xid, hash, 'mixed', 'push');
					
					break;
				
				// Inbox
				case NS_URN_INBOX:
					// Do not handle friend's notifications
					if(xid == getXID())
						handleNotifications(message);
					
					break;
			}
		}
		
		return false;
	}
	
	// If this is a room topic message
	if(subject && (type == 'groupchat')) {
		// Filter the vars
		var filter_subject = subject.replace(/\n/g, ' ');
		var filteredSubject = filterThisMessage(filter_subject, resource, true);
		var filteredName = resource.htmlEnc();
		
		// Display the new subject at the top
		$('#' + hash + ' .top .name .bc-infos .muc-topic').replaceWith('<span class="muc-topic" title="' + subject + '">' + filteredSubject + '</span>');
		
		// Display the new subject as a system message
		if(resource) {
			var topic_body = filteredName + ' ' + _e("changed the subject to:") + ' ' + filteredSubject;
			displayMessage(type, from, hash, filteredName, topic_body, time, stamp, 'system-message', false);
		}
	}
	
	// If the message has a content
	if(xHTML || body) {
		var filteredMessage;
		var notXHTML = true;
		
		// IE bug fix
		if((BrowserDetect.browser == 'Explorer') && (BrowserDetect.version < 9))
			xHTML = 0;
		
		//If this is a xHTML message
		if(xHTML) {
			notXHTML = false;
			
			// Filter the xHTML message
			body = filterThisXHTML(node);
		}
		
		// Groupchat message
		if(type == 'groupchat') {
			/* REF: http://xmpp.org/extensions/xep-0045.html */
			
			// We generate the message type and time
			var message_type = 'user-message';
			
			// This is an old message
			if(delay && resource)
				message_type = 'old-message';
			
			// This is a system message
			else if(!resource)
				message_type = 'system-message';
			
			var nickQuote = '';
			
			// If this is not an old message
			if(message_type == 'user-message') {
				var myNick = getMUCNick(hash);
				
				// If an user quoted our nick (with some checks)
				var regex = new RegExp('((^)|( )|(@))' + escapeRegex(myNick) + '(($)|(:)|(,)|( ))', 'gi');
				
				if(body.match(regex) && (myNick != resource) && (message_type == 'user-message'))
					nickQuote = ' my-nick';
				
				// We notify the user if there's a new personnal message
				if(nickQuote) {
					messageNotify(hash, 'personnal');
					soundPlay(1);
				}
				
				// We notify the user there's a new unread muc message
				else
					messageNotify(hash, 'unread');
			}
			
			// Display the received message
			displayMessage(type, from, hash, resource.htmlEnc(), body, time, stamp, message_type, notXHTML, nickQuote);
		}
		
		// Chat message
		else if((type == 'chat') || !type) {
			// Gets the nickname of the user
			var fromName = resource;
			var chatType = 'chat';
			
			// Must send a receipt notification?
			if(hasReceipt(message) && (id != null))
				sendReceived(type, from, id);
			
			// It does not come from a groupchat user, get the full name
			if(!GCUser)
				fromName = getBuddyName(xid);
			else
				chatType = 'private';
			
			// If the chat isn't yet opened, open it !
			if(!exists('#' + hash)) {
				// We create a new chat
				chatCreate(hash, xid, fromName, chatType);
				
				// We tell the user that a new chat has started
				soundPlay(0);
			}
			
			else
				soundPlay(1);
			
			// Display the received message
			displayMessage(type, xid, hash, fromName.htmlEnc(), body, time, stamp, 'user-message', notXHTML, '', 'him');
			
			// We notify the user
			messageNotify(hash, 'personnal');
		}
		
		return false;
	}
	
	return false;
}

// Sends a given message
function sendMessage(hash, type) {
	// Get the values
	var message_area = $('#' + hash + ' .message-area');
	var body = trim(message_area.val());
	var xid = unescape(message_area.attr('data-to'));
	
	// If the user didn't entered any message, stop
	if(!body || !xid)
		return false;
	
	try {
		// We send the message through the XMPP network
		var aMsg = new JSJaCMessage();
		aMsg.setTo(xid);
		
		// Set an ID
		var id = genID();
		aMsg.setID(id);
		
		// /clear shortcut
		if(body.match(/^\/clear/))
			cleanChat(hex_md5(xid));
		
		// /join shortcut
		else if(body.match(/^\/join (\S+)\s*(.*)/)) {
			// Join
			var room = generateXID(RegExp.$1, 'groupchat');
			var pass = RegExp.$2;
			
			checkChatCreate(room, 'groupchat');
		}
		
		// /part shortcut
		else if(body.match(/^\/part\s*(.*)/) && (!isAnonymous() || (isAnonymous() && (xid != generateXID(ANONYMOUS_ROOM, 'groupchat')))))
			quitThisChat(xid, hex_md5(xid), type);
		
		// /whois shortcut
		else if(body.match(/^\/whois(( (\S+))|($))/)) {
			var whois_xid = RegExp.$3;
			
			// Groupchat WHOIS
			if(type == 'groupchat') {
				var nXID = getMUCUserXID(xid, whois_xid);
				
				if(!nXID)
					openThisInfo(6);
				else
					openUserInfos(nXID);
			}
			
			// Chat or private WHOIS
			else {
				if(!whois_xid)
					openUserInfos(xid);
				else
					openUserInfos(whois_xid);
			}
		}
		
		// Chat message type
		else if(type == 'chat') {
			aMsg.setType('chat');
			
			// Generates the correct message depending of the choosen style
			var notXHTML = true;
			var genMsg = generateMessage(aMsg, body, hash);
			
			if(genMsg == 'XHTML')
				notXHTML = false;
			
			// Receipt request
			var receipt_request = receiptRequest(hash);
			
			if(receipt_request)
				aMsg.appendNode('request', {'xmlns': NS_URN_RECEIPTS});
			
			// Chatstate
			aMsg.appendNode('active', {'xmlns': NS_CHATSTATES});
			
			// Send it!
			con.send(aMsg, handleErrorReply);
			
			// Filter the xHTML message (for us!)
			if(!notXHTML)
				body = filterThisXHTML(aMsg.getNode());
			
			// Finally we display the message we just sent
			var my_xid = getXID();
			
			displayMessage('chat', my_xid, hash, getBuddyName(my_xid).htmlEnc(), body, getCompleteTime(), getTimeStamp(), 'user-message', notXHTML, '', 'me', id);
			
			// Receipt timer
			if(receipt_request)
				checkReceived(hash, id);
		}
		
		// Groupchat message type
		else if(type == 'groupchat') {
			// /say shortcut
			if(body.match(/^\/say (.+)/)) {
				body = body.replace(/^\/say (.+)/, '$1');
				
				aMsg.setType('groupchat');
				generateMessage(aMsg, body, hash);
				
				con.send(aMsg, handleErrorReply);
			}
			
			// /nick shortcut
			else if(body.match(/^\/nick (.+)/)) {
				var nick = body.replace(/^\/nick (.+)/, '$1');
				
				// Does not exist yet?
				if(!getMUCUserXID(xid, nick)) {
					// Send a new presence
					sendPresence(xid + '/' + nick, '', getUserShow(), getUserStatus(), '', false, false, handleErrorReply);
					
					// Change the stored nickname
					$('#' + hex_md5(xid)).attr('data-nick', escape(nick));
				}
			}
			
			// /msg shortcut
			else if(body.match(/^\/msg (\S+)\s+(.+)/)) {
				var nick = RegExp.$1;
				var body = RegExp.$2;
				var nXID = getMUCUserXID(xid, nick);
				
				// We check if the user exists
				if(!nXID)
					openThisInfo(6);
				
				// If the private message is not empty
				else if(body) {
					aMsg.setType('chat');
					aMsg.setTo(nXID);
					generateMessage(aMsg, body, hash);
					
					con.send(aMsg, handleErrorReply);
				}
			}
			
			// /topic shortcut
			else if(body.match(/^\/topic (.+)/)) {
				var topic = body.replace(/^\/topic (.+)/, '$1');
				
				aMsg.setType('groupchat');
				aMsg.setSubject(topic);
				
				con.send(aMsg, handleMessageError);
			}
			
			// /ban shortcut
			else if(body.match(/^\/ban (\S+)\s*(.*)/)) {
				var nick = RegExp.$1;
				var reason = RegExp.$2;
				var nXID = getMUCUserRealXID(xid, nick);
				
				// We check if the user exists
				if(!nXID)
					openThisInfo(6);
				
				else {
					// We generate the ban IQ
					var iq = new JSJaCIQ();
					iq.setTo(xid);
					iq.setType('set');
					
					var iqQuery = iq.setQuery(NS_MUC_ADMIN);
					var item = iqQuery.appendChild(iq.buildNode('item', {'affiliation': 'outcast', 'jid': nXID, 'xmlns': NS_MUC_ADMIN}));
					
					if(reason)
						item.appendChild(iq.buildNode('reason', {'xmlns': NS_MUC_ADMIN}, reason));
					
					con.send(iq, handleErrorReply);
				}
			}
			
			// /kick shortcut
			else if(body.match(/^\/kick (\S+)\s*(.*)/)) {
				var nick = RegExp.$1;
				var reason = RegExp.$2;
				var nXID = getMUCUserXID(xid, nick);
				
				// We check if the user exists
				if(!nXID)
					openThisInfo(6);
				
				else {
					// We generate the kick IQ
					var iq = new JSJaCIQ();
					iq.setTo(xid);
					iq.setType('set');
					
					var iqQuery = iq.setQuery(NS_MUC_ADMIN);
					var item = iqQuery.appendChild(iq.buildNode('item', {'nick': nick, 'role': 'none', 'xmlns': NS_MUC_ADMIN}));
					
					if(reason)
						item.appendChild(iq.buildNode('reason', {'xmlns': NS_MUC_ADMIN}, reason));
					
					con.send(iq, handleErrorReply);
				}
			}
			
			// /invite shortcut
			else if(body.match(/^\/invite (\S+)\s*(.*)/)) {
				var xid = RegExp.$1;
				var reason = RegExp.$2;
				
				var x = aMsg.appendNode('x', {'xmlns': NS_MUC_USER});
				var aNode = x.appendChild(aMsg.buildNode('invite', {'to': xid, 'xmlns': NS_MUC_USER}));
				
				if(reason)
					aNode.appendChild(aMsg.buildNode('reason', {'xmlns': NS_MUC_USER}, reason));
				
				con.send(aMsg, handleErrorReply);
			}
			
			// No shortcut, this is a message
			else {
				aMsg.setType('groupchat');
				
				// Chatstate
				aMsg.appendNode('active', {'xmlns': NS_CHATSTATES});
				
				generateMessage(aMsg, body, hash);
				
				con.send(aMsg, handleMessageError);
			}
		}
		
		// We reset the message input
		$('#' + hash + ' .message-area').val('');
	}
	
	finally {
		logThis('Message sent to: ' + xid + ' / ' + type, 3);
		
		return false;
	}
}

// Generates the correct message area style
function generateStyle(hash) {
	// Initialize the vars
	var styles = '#' + hash + ' div.bubble-style';
	var checkbox = styles + ' input[type=checkbox]';
	var color = styles + ' a.color.selected';
	var style = '';
	
	// Loop the input values
	$(checkbox).filter(':checked').each(function() {
		// If there is a previous element
		if(style)
			style += ' ';
		
		// Get the current style
		switch($(this).attr('class')) {
			case 'bold':
				style += 'font-weight: bold;';
				break;
			
			case 'italic':
				style += 'font-style: italic;';
				break;
			
			case 'underline':
				style += 'text-decoration: underline;';
				break;
		}
	});
	
	// Get the color value
	$(color).each(function() {
		style += 'color: #' + $(this).attr('data-color');
	});
	
	return style;
}

// Generates the correct message code
function generateMessage(aMsg, body, hash) {
	// Create the classical body
	aMsg.setBody(body);
	
	// Get the style
	var style = $('#' + hash + ' .message-area').attr('style');
	
	// A message style is choosen
	if(style) {
		// Explode the message body new lines (to create one <p /> element by line)
		var new_lines = new Array(body);
		
		if(body.match(/\n/))
			new_lines = body.split('\n');
		
		// Create the XML elements
		var aHtml = aMsg.appendNode('html', {'xmlns': NS_XHTML_IM});
		var aBody = aHtml.appendChild(aMsg.buildNode('body', {'xmlns': NS_XHTML}));
		
		// Use the exploded body array to create one element per entry
		for(i in new_lines) {
			// Current line
			var cLine = new_lines[i];
			
			// Blank line, we put a <br />
			if(cLine.match(/(^)(\s+)($)/) || !cLine)
				aBody.appendChild(aMsg.buildNode('br', {'xmlns': NS_XHTML}));
			
			// Line with content, we put a <p />
			else {
				// HTML encode the line
				cLine = cLine.htmlEnc();
				
				// Filter the links
				cLine = applyLinks(cLine, 'xhtml-im', style);
				
				// Append the filtered line
				$(aBody).append($('<p style="' + style + '">' + cLine + '</p>'));
			}
		}
		
		return 'XHTML';
	}
	
	return 'PLAIN';
}

// Displays a given message in a chat tab
function displayMessage(type, xid, hash, name, body, time, stamp, message_type, is_xhtml, nick_quote, mode, id) {
	// Generate some stuffs
	var has_avatar = false;
	var xid_hash = '';
	
	if(!nick_quote)
		nick_quote = '';
	
	if(message_type != 'system-message') {
		has_avatar = true;
		xid_hash = hex_md5(xid);
	}
	
	// Can scroll?
	var cont_scroll = document.getElementById('chat-content-' + hash);
	var can_scroll = false;
	
	if(!cont_scroll.scrollTop || ((cont_scroll.clientHeight + cont_scroll.scrollTop) == cont_scroll.scrollHeight))
		can_scroll = true;
	
	// Any ID?
	var data_id = '';
	
	if(id)
		data_id = ' data-id="' + id + '"';
	
	// Filter the message
	var filteredMessage = filterThisMessage(body, name, is_xhtml);
	
	// Display the received message in the room
	var messageCode = '<div class="one-line ' + message_type + nick_quote + '"' + data_id + '>';
	
	// Name color attribute
	if(type == 'groupchat')
		attribute = ' style="color: ' + generateColor(name) + ';" class="name';
	else {
		attribute = ' class="name';
		
		if(mode)
			attribute += ' ' + mode;
	}
	
	// Close the class attribute
	if(message_type == 'system-message')
		attribute += ' hidden"';
	else
		attribute += '"';
	
	// Filter the previous displayed message
	var last = $('#' + hash + ' .one-group:last');
	var last_name = last.find('b.name').attr('data-xid');
	var last_type = last.attr('data-type');
	var last_stamp = parseInt(last.attr('data-stamp'));
	var grouped = false;
	
	// We can group it with another previous message
	if((last_name == xid) && (message_type == last_type) && ((stamp - last_stamp) <= 1800))
		grouped = true;
	
	// Is it a /me command?
	if(body.match(/(^|>)(\/me )([^<]+)/))
		filteredMessage = '<i>' + filteredMessage + '</i>';
	
	messageCode += filteredMessage + '</div>';
	
	// Must group it?
	var group_path = ' .one-group:last';
	
	if(!grouped) {
		// Generate message headers
		var message_head = '';
		
		// Any avatar to add?
		if(has_avatar)
			message_head += '<div class="avatar-container"><img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" /></div>';
		
		// Add the date & the name
		message_head += '<span class="date">' + time + '</span><b data-xid="' + encodeQuotes(xid) + '" ' + attribute + '>' + name + '</b>';
		
		// Generate message code
		group_path = '';
		messageCode = '<div class="one-group ' + xid_hash + '" data-type="' + message_type + '" data-stamp="' + stamp + '">' + message_head + messageCode + '</div>';
	}
	
	// Archive message
	if(hash == 'archives')
		$('#archives .logs' + group_path).append(messageCode);
	
	// Instant message
	else {
		// Write the code in the DOM
		$('#' + hash + ' .content' + group_path).append(messageCode);
		
		// Must get the avatar?
		if(has_avatar && xid)
			getAvatar(xid, 'cache', 'true', 'forget');
	}
	
	// Scroll to this message
	if(can_scroll)
		autoScroll(hash);
}
