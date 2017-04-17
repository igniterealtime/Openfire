/*

Jappix - An open social platform
These are the error functions for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 02/04/11

*/

// Shows the given error output
function showError(condition, reason, type) {
	// Enough data to output the error
	if(condition || reason) {
		// Initialize the error text
		var eText = '';
		
		// Any error condition
		if(condition)
			eText += condition;
		
		// Any error type
		if(type && eText)
			eText += ' (' + type + ')';
		
		// Any error reason
		if(reason) {
			if(eText)
				eText += ' - ';
			
			eText += reason;
		}
		
		// We reveal the error
		openThisError(1);
		
		// Create the error text
		$('#board .one-board.error[data-id=1] span').text(eText);
	}
	
	// Not enough data to output the error: output a generic board
	else
		openThisError(2);
}

// Handles the error from a packet and return true if any error
function handleError(packet) {
	/* REF: http://xmpp.org/extensions/xep-0086.html */
	
	// Initialize
	var type, code, reason, condition;
	var node = $(packet);
	
	// First level error (connection error)
	if(node.is('error')) {
		// Get the value
		code = node.attr('code');
		
		// Specific error reason
		switch(code) {
			case '401':
				reason = _e("Authorization failed");
				break;
			
			case '409':
				reason = _e("Registration failed, please choose a different username");
				break;
			
			case '503':
				reason = _e("Service unavailable");
				break;
			
			case '500':
				reason = _e("Internal server error, try later");
				break;
			
			default:
				reason = node.find('text').text();
				break;
		}
		
		// Remove the general wait item (security)
		removeGeneralWait();
		
		// Show reconnect pane
		if(CURRENT_SESSION && CONNECTED) {
			// Anonymous?
			if(isAnonymous())
				createReconnect('anonymous');
			else
				createReconnect('normal');
		}
		
		// Show the homepage (security)
		else if(!CURRENT_SESSION || !CONNECTED) {
			$('#home').show();
			pageTitle('home');
		}
		
		// Still connected? (security)
		if(isConnected())
			con.disconnect();
		
		logThis('First level error received.', 1);
	}
	
	// Second level error (another error)
	else if(node.find('error').size()) {
		type = node.find('error').attr('type');
		reason = node.find('error text').text();
		condition = packet.getElementsByTagName('error').item(0).childNodes.item(0).nodeName.replace(/-/g, ' ');
		
		logThis('Second level error received.', 1);
	}
	
	// No error
	else
		return false;
	
	// Show the error board
	showError(condition, reason, type);
	
	// Return there's an error
	return true;
}

// Handles the error reply of a packet
function handleErrorReply(packet) {
	return handleError(packet.getNode());
}

// Handles the error reply for a message
function handleMessageError(packet) {
	if(!handleErrorReply(packet))
		handleMessage(packet);
}
