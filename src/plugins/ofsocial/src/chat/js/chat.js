/*

Jappix - An open social platform
These are the chat JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 19/05/11

*/

// Correctly opens a new chat
function checkChatCreate(xid, type, nickname, password, title) {
	// No XID?
	if(!xid)
		return false;
	
	// We generate some stuffs
	var hash = hex_md5(xid);
	var name;
	
	// Gets the name of the user/title of the room
	if(title)
		name = title;
	
	else {
		// Private groupchat chat
		if(type == 'private')
			name = thisResource(xid).htmlEnc();
		
		// XMPP-ID
		else if(xid.indexOf('@') != -1)
			name = getBuddyName(xid);
		
		// Gateway
		else
			name = xid;
	}
	
	// If the target div does not exist
	if(!exists('#' + hash)) {
		// We check the type of the chat to open
		if((type == 'chat') || (type == 'private'))
			chatCreate(hash, xid, name, type);
		
		else if(type == 'groupchat') {
			// Try to read the room stored configuration
			if(!isAnonymous() && (!nickname || !password || !title)) {
				// Catch the room data
				var fData = $(XMLFromString(getDB('favorites', xid)));
				var fNick = fData.find('nick').text();
				var fPwd = fData.find('password').text();
				var fName = fData.find('name').text();
				
				// Apply the room data
				if(!nickname && fNick)
					nickname = fNick;
				if(!password && fPwd)
					password = fPwd;
				if(!title && fName)
					name = fName;
			}
			
			groupchatCreate(hash, xid, name, nickname, password);
		}
	}
	
	// Switch to the newly-created chat
	switchChan(hash);
	
	return false;
}

// Generates the chat DOM elements
function generateChat(type, id, xid, nick) {
	// Generate some stuffs
	var path = '#' + id + ' .';
	var escaped_xid = escape(xid);
	
	// Special code
	var specialAttributes, specialAvatar, specialName, specialCode, specialLink, specialDisabled, specialStyle;
	
	// Groupchat special code
	if(type == 'groupchat') {
		specialAttributes = ' data-type="groupchat"';
		specialAvatar = '';
		specialName = '<p class="bc-infos"><b>' + _e("Subject") + '</b> <span class="muc-topic">' + _e("no subject defined for this room.") + '</span></p>';
		specialCode = '<div class="content groupchat-content" id="chat-content-' + id + '"></div><div class="list"><div class="moderator role"><p class="title">' + _e("Moderators") + '</p></div><div class="participant role"><p class="title">' + _e("Participants") + '</p></div><div class="visitor role"><p class="title">' + _e("Visitors") + '</p></div><div class="none role"><p class="title">' + _e("Others") + '</p></div></div>';
		specialLink = '<a href="#" class="tools-mucadmin tools-tooltip talk-images chat-tools-content" title="' + _e("Administration panel for this room") + '"></a>';
		specialStyle = '';
		
		// Is this a gateway?
		if(xid.match(/%/))
			specialDisabled = '';
		else
			specialDisabled = ' disabled=""';
	}
	
	// Chat (or other things?!) special code
	else {
		specialAttributes = ' data-type="chat"';
		specialAvatar = '<div class="avatar-container"><img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" /></div>';
		specialName = '<div class="bc-pep"></div><p class="bc-infos"><span class="unavailable show talk-images"></span></p>';
		specialCode = '<div class="content" id="chat-content-' + id + '"></div>';
		specialLink = '<a href="#" class="tools-archives tools-tooltip talk-images chat-tools-content" title="' + _e("View chat history") + '"></a><a href="#" class="tools-infos tools-tooltip talk-images chat-tools-content" title="' + _e("Show user profile") + '"></a>';
		specialStyle = ' style="display: none;"';
		specialDisabled = '';
	}
	
	// Not a groupchat private chat, we can use the buddy add icon
	if((type == 'chat') || (type == 'groupchat')) {
		var addTitle;
		
		if(type == 'chat')
			addTitle = _e("Add this contact to your friends");
		else
			addTitle = _e("Add this groupchat to your favorites");
		
		specialLink += '<a href="#" class="tools-add tools-tooltip talk-images chat-tools-content" title="' + addTitle + '"></a>';
	}
	
	// IE DOM parsing bug fix
	var specialStylePicker = '<div class="chat-tools-content chat-tools-style"' + specialStyle + '>' + 
					'<a href="#" class="tools-style tools-tooltip talk-images"></a>' + 
				 '</div>';
	
	if((BrowserDetect.browser == 'Explorer') && (BrowserDetect.version < 9))
		specialStylePicker = '';
	
	// Append the chat HTML code
	$('#page-engine').append(
		'<div id="' + id + '" class="page-engine-chan chat one-counter"' + specialAttributes + ' data-xid="' + escaped_xid + '">' + 
			'<div class="top ' + id + '">' + 
				specialAvatar + 
				
				'<div class="name">' + 
					'<p class="bc-name bc-name-nick">' + nick.htmlEnc() + '</p>' + 
					specialName + 
				'</div>' + 
			'</div>' + 
			
			specialCode + 
			
			'<div class="text">' + 
				'<div class="footer">' + 
					'<div class="chat-tools-content chat-tools-smileys">' + 
						'<a href="#" class="tools-smileys tools-tooltip talk-images"></a>' + 
					'</div>' + 
					
					specialStylePicker + 
					
					'<div class="chat-tools-content chat-tools-save">' + 
						'<a href="#" class="tools-save tools-tooltip talk-images"></a>' + 
					'</div>' + 
					
					'<a href="#" class="tools-clear tools-tooltip talk-images chat-tools-content" title="' + _e("Clean current chat") + '"></a>' + 
					
					specialLink + 
				'</div>' + 
				
				'<div class="compose">' + 
					'<textarea class="message-area focusable" ' + specialDisabled + ' data-to="' + escaped_xid + '" /></textarea>' + 
				'</div>' + 
			'</div>' + 
		'</div>'
	);
	
	// Click event: chat cleaner
	$(path + 'tools-clear').click(function() {
		cleanChat(id);
	});
	
	// Click event: user-infos
	$(path + 'tools-infos').click(function() {
		openUserInfos(xid);
	});
}

// Generates the chat switch elements
function generateSwitch(type, id, xid, nick) {
	// Path to the element
	var chat_switch = '#page-switch .';
	
	// Special code
	var specialClass = ' unavailable';
	var show_close = true;
	
	// Groupchat
	if(type == 'groupchat') {
		specialClass = ' groupchat-default';
		
		if(isAnonymous() && (xid == generateXID(ANONYMOUS_ROOM, 'groupchat')))
			show_close = false;
	}
	
	// Generate the HTML code
	var html = '<div class="' + id + ' switcher chan" onclick="return switchChan(\'' + encodeOnclick(id) + '\')">' + 
			'<div class="icon talk-images' + specialClass + '"></div>' + 
			
			'<div class="name">' + nick.htmlEnc() + '</div>';
	
	// Show the close button if not MUC and not anonymous
	if(show_close)
		html += '<div class="exit" title="' + _e("Close this tab") + '" onclick="return quitThisChat(\'' + encodeOnclick(xid) + '\', \'' + encodeOnclick(id) + '\', \'' + encodeOnclick(type) + '\');">x</div>';
	
	// Close the HTML
	html += '</div>';
	
	// Append the HTML code
	$(chat_switch + 'chans, ' + chat_switch + 'more-content').append(html);
}

// Cleans given the chat lines
function cleanChat(chat) {
	$('#page-engine #' + chat + ' .content .one-group').remove();
	
	$(document).oneTime(10, function() {
		$('#page-engine #' + chat + ' .text .message-area').focus();
	});
}

// Creates a new chat
function chatCreate(hash, xid, nick, type) {
	logThis('New chat: ' + xid, 3);
	
	// Create the chat content
	generateChat(type, hash, xid, nick);
	
	// Create the chat switcher
	generateSwitch(type, hash, xid, nick);
	
	// If the user is not in our buddy-list
	if(type == 'chat') {
		// Add button
		if(!exists('#buddy-list .buddy[data-xid=' + escape(xid) + ']'))
			$('#' + hash + ' .tools-add').click(function() {
				// Hide the icon (to tell the user all is okay)
				$(this).hide();
				
				// Send the subscribe request
				addThisContact(xid, nick);
			}).show();
		
		// Archives button
		else if(enabledArchives() || enabledArchives('auto') || enabledArchives('manual') || enabledArchives('manage'))
			$('#' + hash + ' .tools-archives').click(function() {
				// Open the archives popup
				openArchives();
				
				// Get the archives for this user
				$('#archives .filter .friend').val(xid);
				updateArchives();
			}).show();
	}
	
	// We catch the user's informations (like this avatar, vcard, and so on...)
	getUserInfos(hash, xid, nick, type);
	
	// The icons-hover functions
	tooltipIcons(xid, hash);
	
	// The event handlers
	var inputDetect = $('#page-engine #' + hash + ' .message-area');
	
	inputDetect.focus(function() {
		chanCleanNotify(hash);
	})
	
	inputDetect.keypress(function(e) {
		// Enter key
		if(e.keyCode == 13) {
			// Send the message
			sendMessage(hash, 'chat');
			
			// Reset the composing database entry
			setDB('chatstate', xid, 'off');
			
			return false;
		}
	});
	
	// Chatstate events
	eventsChatState(inputDetect, xid, hash);
}
