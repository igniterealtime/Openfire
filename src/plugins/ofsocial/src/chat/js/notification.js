/*

Jappix - An open social platform
These are the notification JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 20/05/11

*/

// Resets the notifications alert if no one remaining
function closeEmptyNotifications() {
	if(!$('.one-notification').size())
		closeBubbles();
}

// Checks if there are pending notifications
function checkNotifications() {
	// Define the selectors
	var notif = '#top-content .notifications';
	var nothing = '.notifications-content .nothing';
	var empty = '.notifications-content .empty';
	
	// Get the notifications number
	var number = $('.one-notification').size();
	
	// Remove the red notify bubble
	$(notif + ' .notify').remove();
	
	// Any notification?
	if(number) {
		$(notif).prepend('<div class="notify one-counter" data-counter="' + number + '">' + number + '</div>');
		$(nothing).hide();
		$(empty).show();
	}
	
	// No notification!
	else {
		$(empty).hide();
		$(nothing).show();
		
		// Purge the social inbox node
		purgeNotifications();
	}
	
	// Update the page title
	updateTitle();
}

// Creates a new notification
function newNotification(type, from, data, body, id, inverse) {
	if(!type || !from)
		return;
	
	// Generate an ID hash
	if(!id)
		var id = hex_md5(type + from);
	
	// Generate the text to be displayed
	var text, action, code;
	
	// User things
	from = bareXID(from);
	var hash = hex_md5(from);
	
	switch(type) {
		case 'subscribe':
			// Get the name to display
			var display_name = data[1];
			
			if(!display_name)
				display_name = data[0];
			
			text = '<b>' + display_name.htmlEnc() + '</b> ' + _e("would like to add you as a friend.") + ' ' + _e("Do you accept?");
			
			break;
		
		case 'invite_room':
			text = '<b>' + getBuddyName(from).htmlEnc() + '</b> ' + _e("would like you to join this chatroom:") + ' <em>' + data[0] + '</em> ' + _e("Do you accept?");
			
			break;
		
		case 'request':
			text = '<b>' + from.htmlEnc() + '</b> ' + _e("would like to get authorization.") + ' ' + _e("Do you accept?");
			
			break;
		
		case 'rosterx':
			text = printf(_e("Do you want to see the friends %s suggests you?"), '<b>' + getBuddyName(from).htmlEnc() + '</b>');
			
			break;
		
		case 'comment':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("commented an item you follow: “%s”."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		case 'like':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("liked your post: “%s”."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		case 'quote':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("quoted you somewhere: “%s”."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		case 'wall':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("published on your wall: “%s”."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		case 'photo':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("tagged you in a photo (%s)."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		case 'video':
			text = '<b>' + data[0].htmlEnc() + '</b> ' + printf(_e("tagged you in a video (%s)."), '<em>' + truncate(body, 25) + '</em>');
			
			break;
		
		default:
			break;
	}
	
	// No text?
	if(!text)
		return;
	
	// Action links?
	if((type == 'comment') || (type == 'like') || (type == 'quote') || (type == 'wall') || (type == 'photo') || (type == 'video')) {
		action = '<a href="#" class="no">' + _e("Hide") + '</a>';
		
		// Any parent link?
		if(data[2] && (type == 'comment'))
			action = '<a href="#" class="yes">' + _e("Show") + '</a>' + action;
	}
	
	else	
		action = '<a href="#" class="yes">' + _e("Yes") + '</a><a href="#" class="no">' + _e("No") + '</a>';
	
	if(text) {
		// We display the notification
		if(!exists('.notifications-content .' + id)) {
			// We create the html markup depending of the notification type
			code = '<div class="one-notification ' + id + ' ' + hash + '" title="' + encodeQuotes(body) + '" data-type="' + encodeQuotes(type) + '">' + 
					'<div class="avatar-container">' + 
						'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
					'</div>' + 
					
					'<p class="notification-text">' + text + '</p>' + 
					'<p class="notification-actions">' + 
						'<span class="talk-images" />' + 
						action + 
					'</p>' + 
			       '</div>';
			
			// Add the HTML code
			if(inverse)
				$('.notifications-content .nothing').before(code);
			else
				$('.notifications-content .empty').after(code);
			
			// Play a sound to alert the user
			soundPlay(2);
			
			// The yes click function
			$('.' + id + ' a.yes').click(function() {
				return actionNotification(type, data, 'yes', id);
			});
			
			// The no click function
			$('.' + id + ' a.no').click(function() {
				return actionNotification(type, data, 'no', id);
			});
			
			// Get the user avatar
			getAvatar(from, 'cache', 'true', 'forget');
		}
	}
	
	// We tell the user he has a new pending notification
	checkNotifications();
	
	logThis('New notification: ' + from, 3);
}

// Performs an action on a given notification
function actionNotification(type, data, value, id) {
	// We launch a function depending of the type
	if((type == 'subscribe') && (value == 'yes'))
		acceptSubscribe(data[0], data[1]);
	
	else if((type == 'subscribe') && (value == 'no'))
		sendSubscribe(data[0], 'unsubscribed');
	
	else if((type == 'invite_room') && (value == 'yes'))
		checkChatCreate(data[0], 'groupchat');
	
	else if(type == 'request')
		requestReply(value, data[0]);
	
	else if((type == 'rosterx') && (value == 'yes'))
		openRosterX(data[0]);
	
	else if((type == 'comment') || (type == 'like') || (type == 'quote') || (type == 'wall') || (type == 'photo') || (type == 'video')) {
		if(value == 'yes') {
			// Get the microblog item
			fromInfosMicroblog(data[2]);
			
			// Append the marker
			$('#channel .top.individual').append('<input type="hidden" name="comments" value="' + encodeQuotes(data[1]) + '" />');
		}
		
		removeNotification(data[3]);
	}
	
	// We remove the notification
	$('.notifications-content .' + id).remove();
	
	// We check if there's any other pending notification
	closeEmptyNotifications();
	checkNotifications();
	
	return false;
}

// Clear the social notifications
function clearNotifications() {
	// Remove notifications
	$('.one-notification').remove();
	
	// Refresh
	closeEmptyNotifications();
	checkNotifications();
	
	return false;
}

// Gets the pending social notifications
function getNotifications() {
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	pubsub.appendChild(iq.buildNode('items', {'node': NS_URN_INBOX, 'xmlns': NS_PUBSUB}));
	
	con.send(iq, handleNotifications);
	
	logThis('Getting social notifications...');
}

// Handles the social notifications
function handleNotifications(iq) {
	// Any error?
	if((iq.getType() == 'error') && $(iq.getNode()).find('item-not-found').size()) {
		// The node may not exist, create it!
		setupMicroblog('', NS_URN_INBOX, '1', '1000000', 'whitelist', 'open', true);
		
		logThis('Error while getting social notifications, trying to reconfigure the Pubsub node!', 2);
	}
	
	// Selector
	var items = $(iq.getNode()).find('item');
	
	// Should we inverse?
	var inverse = true;
	
	if(items.size() == 1)
		inverse = false;
	
	// Parse notifications
	items.each(function() {
		// Parse the current item
		var current_item = $(this).attr('id');
		var current_type = $(this).find('link[rel=via]:first').attr('title');
		var current_href = $(this).find('link[rel=via]:first').attr('href');
		var current_parent_href = $(this).find('link[rel=related]:first').attr('href');
		var current_xid = explodeThis(':', $(this).find('source author uri').text(), 1);
		var current_name = $(this).find('source author name').text();
		var current_text = $(this).find('content[type=text]:first').text();
		var current_bname = getBuddyName(current_xid);
		var current_id = hex_md5(current_type + current_xid + current_href + current_text);
		
		// Choose the good name!
		if(!current_name || (current_bname != getXIDNick(current_xid)))
			current_name = current_bname;
		
		// Create it!
		newNotification(current_type, current_xid, [current_name, current_href, current_parent_href, current_item], current_text, current_id, inverse);
	});
	
	logThis(items.size() + ' social notification(s) got!', 3);
}

// Sends a social notification
function sendNotification(xid, type, href, text, parent) {
	// Notification ID
	var id = hex_md5(xid + text + getTimeStamp());
	
	// IQ
	var iq = new JSJaCIQ();
	iq.setType('set');
	iq.setTo(xid);
	
	// ATOM content
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_URN_INBOX, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'id': id, 'xmlns': NS_PUBSUB}));
	var entry = item.appendChild(iq.buildNode('entry', {'xmlns': NS_ATOM}));
	
	// Notification author (us)
	var Source = entry.appendChild(iq.buildNode('source', {'xmlns': NS_ATOM}));
	var author = Source.appendChild(iq.buildNode('author', {'xmlns': NS_ATOM}));
	author.appendChild(iq.buildNode('name', {'xmlns': NS_ATOM}, getName()));
	author.appendChild(iq.buildNode('uri', {'xmlns': NS_ATOM}, 'xmpp:' + getXID()));
	
	// Notification content
	entry.appendChild(iq.buildNode('published', {'xmlns': NS_ATOM}, getXMPPTime('utc')));
	entry.appendChild(iq.buildNode('content', {'type': 'text', 'xmlns': NS_ATOM}, text));
	entry.appendChild(iq.buildNode('link', {'rel': 'via', 'title': type, 'href': href, 'xmlns': NS_ATOM}));
	
	// Any parent item?
	if(parent && parent[0] && parent[1] && parent[2]) {
		// Generate the parent XMPP URI
		var parent_href = 'xmpp:' + parent[0] + '?;node=' + encodeURIComponent(parent[1]) + ';item=' + encodeURIComponent(parent[2]);
		
		entry.appendChild(iq.buildNode('link', {'rel': 'related', 'href': parent_href, 'xmlns': NS_ATOM}));
	}
	
	con.send(iq);
	
	logThis('Sending a social notification to ' + xid + ' (type: ' + type + ')...');
}

// Removes a social notification
function removeNotification(id) {
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var retract = pubsub.appendChild(iq.buildNode('retract', {'node': NS_URN_INBOX, 'xmlns': NS_PUBSUB}));
	retract.appendChild(iq.buildNode('item', {'id': id, 'xmlns': NS_PUBSUB}));
	
	con.send(iq);
}

// Purge the social notifications
function purgeNotifications() {
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB_OWNER});
	pubsub.appendChild(iq.buildNode('purge', {'node': NS_URN_INBOX, 'xmlns': NS_PUBSUB_OWNER}));
	
	con.send(iq);
	
	return false;
}

// Adapt the notifications bubble max-height
function adaptNotifications() {
	// Process the new height
	var max_height = $('#right-content').height() - 22;
	
	// New height too small
	if(max_height < 250)
		max_height = 250;
	
	// Apply the new height
	$('.notifications-content .tools-content-subitem').css('max-height', max_height);
}

// Plugin launcher
function launchNotifications() {
	// Adapt the notifications height
	adaptNotifications();
}

// Window resize event handler
$(window).resize(adaptNotifications);
