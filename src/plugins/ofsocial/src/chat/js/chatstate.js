/*

Jappix - An open social platform
These are the chatstate JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 13/05/11

*/

// Sends a given chatstate to a given entity
function chatStateSend(state, xid, hash) {
	var user_type = $('#' + hash).attr('data-type');
	
	// If the friend client supports chatstates and is online
	if((user_type == 'groupchat') || ((user_type == 'chat') && $('#' + hash + ' .message-area').attr('data-chatstates') && !exists('#page-switch .' + hash + ' .unavailable'))) {
		// New message stanza
		var aMsg = new JSJaCMessage();
		aMsg.setTo(xid);
		aMsg.setType(user_type);
		
		// Append the chatstate node
		aMsg.appendNode(state, {'xmlns': NS_CHATSTATES});
		
		// Send this!
		con.send(aMsg);
	}
}

// Displays a given chatstate in a given chat
function displayChatState(state, hash, type) {
	// Groupchat?
	if(type == 'groupchat') {
		resetChatState(hash, type);
		
		// "gone" state not allowed
		if(state != 'gone')
			$('#page-engine .page-engine-chan .user.' + hash).addClass(state);
	}
	
	// Chat
	else {
		// We change the buddy name color in the page-switch
		resetChatState(hash, type);
		$('#page-switch .' + hash + ' .name').addClass(state);
		
		// We generate the chatstate text
		var text = '';
		
		switch(state) {
			// Active
			case 'active':
				text = _e("Your friend is paying attention to the conversation.");
				
				break;
			
			// Composing
			case 'composing':
				text = _e("Your friend is writing a message...");
				
				break;
			
			// Paused
			case 'paused':
				text = _e("Your friend stopped writing a message.");
				
				break;
			
			// Inactive
			case 'inactive':
				text = _e("Your friend is doing something else.");
				
				break;
			
			// Gone
			case 'gone':
				text = _e("Your friend closed the chat.");
				
				break;
		}
		
		// We reset the previous state
		$('#' + hash + ' .chatstate').remove();
		
		// We create the chatstate
		$('#' + hash + ' .content').after('<div class="' + state + ' chatstate">' + text + '</div>');
	}
}

// Resets the chatstate switcher marker
function resetChatState(hash, type) {
	// Define the selector
	var selector;
	
	if(type == 'groupchat')
		selector = $('#page-engine .page-engine-chan .user.' + hash);
	else
		selector = $('#page-switch .' + hash + ' .name');
	
	// Reset!
	selector.removeClass('active')
	selector.removeClass('composing')
	selector.removeClass('paused')
	selector.removeClass('inactive')
	selector.removeClass('gone');
}

// Adds the chatstate events
function eventsChatState(target, xid, hash) {
	target.keyup(function(e) {
		if(e.keyCode != 13) {
			// Composing a message
			if($(this).val() && (getDB('chatstate', xid) != 'on')) {
				// We change the state detect input
				setDB('chatstate', xid, 'on');
				
				// We send the friend a "composing" chatstate
				chatStateSend('composing', xid, hash);
			}
			
			// Stopped composing a message
			else if(!$(this).val() && (getDB('chatstate', xid) == 'on')) {
				// We change the state detect input
				setDB('chatstate', xid, 'off');
				
				// We send the friend an "active" chatstate
				chatStateSend('active', xid, hash);
			}
		}
	});
	
	target.change(function() {
		// Reset the composing database entry
		setDB('chatstate', xid, 'off');
	});
	
	target.focus(function() {
		// Nothing in the input, user is active
		if(!$(this).val())
			chatStateSend('active', xid, hash);
		
		// Something was written, user started writing again
		else
			chatStateSend('composing', xid, hash);
	});
	
	target.blur(function() {
		// Nothing in the input, user is inactive
		if(!$(this).val())
			chatStateSend('inactive', xid, hash);
		
		// Something was written, user paused
		else
			chatStateSend('paused', xid, hash);
	});
}
