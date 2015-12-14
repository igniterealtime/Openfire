/*

Jappix - An open social platform
These are the receipts JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 26/12/10

*/

// Checks if we can send a receipt request
function receiptRequest(hash) {
	// Entity have support for receipt?
	if($('#' + hash + ' .message-area').attr('data-receipts') == 'true')
		return true;
	
	return false;
}

// Checks if there is a receipt request
function hasReceipt(packet) {
	// Any receipt request?
	if(packet.getChild('request', NS_URN_RECEIPTS))
		return true;
	
	return false;
}

// Checks if there is a received reply
function hasReceived(packet) {
	// Any received reply?
	if(packet.getChild('received', NS_URN_RECEIPTS))
		return true;
	
	return false;
}

// Sends a received notification
function sendReceived(type, to, id) {
	var aMsg = new JSJaCMessage();
	aMsg.setTo(to);
	aMsg.setID(id);
	
	// Any type?
	if(type)
		aMsg.setType(type);
	
	// Append the received node
	aMsg.appendNode('received', {'xmlns': NS_URN_RECEIPTS, 'id': id});
	
	con.send(aMsg);
	
	logThis('Sent received to: ' + to);
}

// Tells the message has been received
function messageReceived(hash, id) {
	// Line selector
	var path = $('#' + hash + ' .one-line[data-id=' + id + ']');
	
	// Add a received marker
	path.attr('data-received', 'true')
	    .removeAttr('data-lost');
	
	// Group selector
	var group = path.parent();
	
	// Remove the group marker
	if(!group.find('.one-line[data-lost]').size()) {
		group.find('b.name').removeClass('talk-images')
				    .removeAttr('title');
	}
	
	return false;
}

// Checks if the message has been received
function checkReceived(hash, id) {
	// Fire a check 10 seconds later
	$('#' + hash + ' .one-line[data-id=' + id + ']').oneTime('10s', function() {
		// Not received?
		if($(this).attr('data-received') != 'true') {
			// Add a "lost" marker
			$(this).attr('data-lost', 'true');
			
			// Add a warn on the buddy-name
			$(this).parent().find('b.name').addClass('talk-images')
						       .attr('title', _e("Your friend seems not to have received your message(s)!"));
		}
	});
}
