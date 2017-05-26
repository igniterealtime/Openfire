/*

Jappix - An open social platform
These are the groupchat JS scripts for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Marco Cirillo
Last revision: 11/05/11

*/

// Displays the MUC admin elements
function displayMucAdmin(affiliation, id, xid, statuscode) {
	// We must be in the "login" mode
	if(isAnonymous())
		return;
	
	// We check if the user is a room owner or administrator to give him privileges
	if(affiliation == 'owner' || affiliation == 'admin')
		$('#' + id + ' .tools-mucadmin').show();
	
	// We check if the room hasn't been yet created
	if(statuscode == 201)
		openThisInfo(4);
	
	// We add the click event
	$('#' + id + ' .tools-mucadmin').click(function() {
		openMucAdmin(xid, affiliation);
	});
}

// Initializes a connection with a MUC groupchat
function getMUC(room, nickname, password) {
	// Room hash
	var hash = hex_md5(room);
	
	// Reset the elements
	$('#' + hash + ' .muc-ask').remove();
	$('#' + hash + ' .compose').show();
	
	// No nickname?
	if(!nickname) {
		// Get some values
		if(!isAnonymous())
			nickname = getNick();
		else
			nickname = ANONYMOUS_NICK;
		
		// If the nickname could not be retrieved, ask it
		if(!nickname)
			generateMUCAsk('nickname', room, hash, nickname, password);
	}
	
	// Got our nickname?
	if(nickname) {
		// Get our general presence
		var show = getDB('presence-show', 1);
		var status = getDB('options', 'presence-status');
	
		// Set my nick
		$('#' + hash).attr('data-nick', escape(nickname));
	
		// Send the appropriate presence
		sendPresence(room + '/' + nickname, '', show, status, '', true, password, handleMUC);
	}
	
	return false;
}

// Handles the MUC main elements
function handleMUC(presence) {
	// We get the xml content
	var xml = presence.getNode();
	var from = fullXID(getStanzaFrom(presence));
	var room = bareXID(from);
	var nickname = thisResource(from);
	var hash = hex_md5(room);
	
	// No ID: must fix M-Link bug
	if(presence.getID() == null)
		presence.setID(1);
	
	logThis('First MUC presence: ' + from, 3);
	
	// Catch the errors
	if(!handleError(xml)) {
		// Define some stuffs
		var muc_user = $(xml).find('x[xmlns=' + NS_MUC_USER + ']');
		var affiliation = muc_user.find('item').attr('affiliation');
                var statuscode = parseInt(muc_user.find('status').attr('code'));
		
		// Handle my presence
		handlePresence(presence);
		
		// Check if I am a room owner
		displayMucAdmin(affiliation, hash, room, statuscode);
		
		// Tell the MUC we can notify the incoming presences
		$(document).oneTime('15s', function() {
			$('#' + hash).attr('data-initial', 'true');
		});
		
		// Enable the chatting input
		$(document).oneTime(10, function() {
			$('#' + hash + ' .message-area').removeAttr('disabled').focus();
		});
	}
	
	// A password is required
	else if($(xml).find('error[type=auth] not-authorized').size())
		generateMUCAsk('password', room, hash, nickname);
	
	// There's a nickname conflict
	else if($(xml).find('error[type=cancel] conflict').size())
		generateMUCAsk('nickname', room, hash);
}

// Generates a correct MUC asker
function generateMUCAsk(type, room, hash, nickname, password) {
	// Generate the path to the elements
	var path_to = '#' + hash + ' .muc-ask';
	
	// Define the label text
	var label_text;
	
	switch(type) {
		case 'nickname':
			label_text = _e("Nickname");
			break;
		
		case 'password':
			label_text = _e("Password");
			break;
	}
	
	// Create the HTML markup
	$('#' + hash + ' .compose').hide();
	
	$('#' + hash).append(
		'<div class="muc-ask text">' + 
			'<label>' + label_text + '</label>' + 
			'<input class="focusable" type="text" />' + 
		'</div>'
	);
	
	// When a key is pressed in the input
	$(path_to + ' input').keyup(function(e) {
		var value_input = $(this).val();
		
		// Enter key pressed
		if((e.keyCode == 13) && value_input) {
			if(type == 'nickname')
				nickname = value_input;
			else if(type == 'password')
				password = value_input;
			
			return getMUC(room, nickname, password);
		}
	});
	
	// Focus on the input
	$(document).oneTime(10, function() {
		$(path_to + ' input').focus();
	});
}

// Creates a new groupchat
function groupchatCreate(hash, room, chan, nickname, password) {
	/* REF: http://xmpp.org/extensions/xep-0045.html */
	
	logThis('New groupchat: ' + room, 3);
	
	// Create the chat content
	generateChat('groupchat', hash, room, chan);
	
	// Create the chat switcher
	generateSwitch('groupchat', hash, room, chan);
	
	// The icons-hover functions
	tooltipIcons(room, hash);
	
	// Click event on the add tool
	$('#' + hash + ' .tools-add').click(function() {
		// Hide the icon (to tell the user all is okay)
		$(this).hide();
		
		// Add the groupchat to the user favorites
		addThisFavorite(room, chan);
	});
	
	// Must show the add button?
	if(!existDB('favorites', room))
		$('#' + hash + ' .tools-add').show();
	
	// The event handlers
	var inputDetect = $('#' + hash + ' .message-area');
	
	// Focus event
	inputDetect.focus(function() {
		chanCleanNotify(hash);
	})
	
	// Blur event
	inputDetect.blur(function() {
		resetAutocompletion(hash);
	})
	
	// Lock to the input
	inputDetect.keypress(function(e) {
		// Enter key
		if(e.keyCode == 13) {
			sendMessage(hash, 'groupchat');
			
			// Reset the composing database entry
			setDB('chatstate', room, 'off');
			
			return false;
		}
		
		// Tabulation key
		else if(e.keyCode == 9) {
			createAutocompletion(hash);
			
			return false;
		}
		
		// Reset the autocompleter
		else
			resetAutocompletion(hash);
	});
	
	// Chatstate events
	eventsChatState(inputDetect, room, hash);
	
	// Get the current muc informations and content
	getMUC(room, nickname, password);
}
