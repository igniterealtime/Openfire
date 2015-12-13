/*

Jappix - An open social platform
These are the storage JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 16/05/11

*/

// Gets the storage items of the user
function getStorage(type) {
	/* REF: http://xmpp.org/extensions/xep-0049.html */
	
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	var iqQuery = iq.setQuery(NS_PRIVATE);
	iqQuery.appendChild(iq.buildNode('storage', {'xmlns': type}));
	
	con.send(iq, handleStorage);
}

// Handles the storage items
function handleStorage(iq) {
	var handleXML = iq.getQuery();
	var handleFrom = fullXID(getStanzaFrom(iq));
	
	// Define some vars
	var options = $(handleXML).find('storage[xmlns=' + NS_OPTIONS + ']');
	var inbox = $(handleXML).find('storage[xmlns=' + NS_INBOX + ']');
	var bookmarks = $(handleXML).find('storage[xmlns=' + NS_BOOKMARKS + ']');
	var rosternotes = $(handleXML).find('storage[xmlns=' + NS_ROSTERNOTES + ']');
	
	// No options and node not yet configured
	if(options.size() && !options.find('option').size() && (iq.getType() != 'error'))
		openWelcome();
	
	// Parse the options xml
	options.find('option').each(function() {
		// We retrieve the informations
		var type = $(this).attr('type');
		var value = $(this).text();
		
		// We display the storage
		setDB('options', type, value);
		
		// If this is the buddy list show status
		if((type == 'roster-showall') && (value == '1'))
			showAllBuddies('storage');
	});
	
	// Parse the inbox xml
	inbox.find('message').each(function() {
		storeInboxMessage(
				  $(this).attr('from'),
				  $(this).attr('subject'),
				  $(this).text(),
				  $(this).attr('status'),
				  $(this).attr('id'),
				  $(this).attr('date'),
				  [
				   $(this).attr('file_title'),
				   $(this).attr('file_href'),
				   $(this).attr('file_type'),
				   $(this).attr('file_length')
				  ]
				 );
	});
	
	// Parse the bookmarks xml
	bookmarks.find('conference').each(function() {
		// We retrieve the informations
		var xid = $(this).attr('jid');
		var name = $(this).attr('name');
		var autojoin = $(this).attr('autojoin');
		var password = $(this).find('password').text();
		var nick = $(this).find('nick').text();
		
		// We display the storage
		displayFavorites(xid, name, nick, autojoin, password);
		
		// Join the chat if autojoin is enabled
		if(autojoin == '1')
			checkChatCreate(xid, 'groupchat', nick, password, name);
	});
	
	// Parse the roster notes xml
	rosternotes.find('note').each(function() {
		setDB('rosternotes', $(this).attr('jid'), $(this).text());
	});
	
	// Options received
	if(options.size()) {
		logThis('Options received.');
		
		// Now, get the inbox
		getStorage(NS_INBOX);
		
		$('.options-hidable').show();
	}
	
	// Inbox received
	else if(inbox.size()) {
		logThis('Inbox received.');
		
		// Send the first presence!
		firstPresence(getDB('checksum', 1));
		
		// Check we have new messages (play a sound if any unread messages)
		if(checkInboxMessages())
			soundPlay(2);
		
		$('.inbox-hidable').show();
	}
	
	// Bookmarks received (for logger)
	else if(bookmarks.size())
		logThis('Bookmarks received.');
	
	// Roster notes received (for logger)
	else if(rosternotes.size())
		logThis('Roster notes received.');
}
