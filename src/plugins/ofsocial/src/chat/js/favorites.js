/*

Jappix - An open social platform
These are the favorites JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Opens the favorites popup
function openFavorites() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Manage favorite rooms") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="switch-fav">' + 
			'<div class="room-switcher room-list">' + 
				'<div class="icon list-icon talk-images"></div>' + 
				
				_e("Change favorites") + 
			'</div>' + 
			
			'<div class="room-switcher room-search">' + 
				'<div class="icon search-icon talk-images"></div>' + 
				
				_e("Search a room") + 
			'</div>' + 
		'</div>' + 
		
		'<div class="static-fav">' + 
			'<div class="favorites-edit favorites-content">' + 
				'<div class="head fedit-head static-fav-head">' + 
					'<div class="head-text fedit-head-text">' + _e("Select a favorite") + '</div>' + 
					
					'<select name="fedit-head-select" class="head-select fedit-head-select"></select>' + 
				'</div>' + 
				
				'<div class="results fedit-results static-fav-results">' + 
					'<div class="fedit-line">' + 
						'<label>' + _e("Name") + '</label>' + 
						
						'<input class="fedit-title" type="text" required="" />' + 
					'</div>' + 
					
					'<div class="fedit-line">' + 
						'<label>' + _e("Nickname") + '</label>' + 
						
						'<input class="fedit-nick" type="text" value="' + getNick() + '" required="" />' + 
					'</div>' + 
					
					'<div class="fedit-line">' + 
						'<label>' + _e("Room") + '</label>' + 
						
						'<input class="fedit-chan" type="text" required="" />' + 
					'</div>' + 
					
					'<div class="fedit-line">' + 
						'<label>' + _e("Server") + '</label>' + 
						
						'<input class="fedit-server" type="text" value="' + HOST_MUC + '" required="" />' + 
					'</div>' + 
					
					'<div class="fedit-line">' + 
						'<label>' + _e("Password") + '</label>' + 
						
						'<input class="fedit-password" type="password" />' + 
					'</div>' + 
					
					'<div class="fedit-line">' + 
						'<label>' + _e("Automatic") + '</label>' + 
						
						'<input type="checkbox" class="fedit-autojoin" />' + 
					'</div>' + 
					
					'<div class="fedit-actions">' + 
						'<a href="#" class="fedit-terminate fedit-add add one-button talk-images">' + _e("Add") + '</a>' + 
						'<a href="#" class="fedit-terminate fedit-edit one-button talk-images">' + _e("Edit") + '</a>' + 
						'<a href="#" class="fedit-terminate fedit-remove remove one-button talk-images">' + _e("Remove") + '</a>' + 
					'</div>' + 
				'</div>' + 
			'</div>' + 
			
			'<div class="favorites-search favorites-content">' + 
				'<div class="head fsearch-head static-fav-head">' + 
					'<div class="head-text fsearch-head-text">' + _e("Search a room on") + '</div>' + 
					
					'<input type="text" class="head-input fsearch-head-server" value="' + HOST_MUC + '" />' + 
				'</div>' + 
				
				'<div class="results fsearch-results static-fav-results">' + 
					'<p class="fsearch-noresults">' + _e("No room found on this server.") + '</p>' + 
				'</div>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('favorites', html);
	
	// Load the favorites
	loadFavorites();
	
	// Associate the events
	launchFavorites();
}

// Resets the favorites elements
function resetFavorites() {
	var path = '#favorites ';
	
	$(path + '.wait, ' + path + '.fedit-terminate').hide();
	$(path + '.fedit-add').show();
	$(path + '.fsearch-oneresult').remove();
	$(path + 'input').val('');
	$(path + '.please-complete').removeClass('please-complete');
	$(path + '.fedit-nick').val(getNick());
	$(path + '.fsearch-head-server, ' + path + '.fedit-server').val(HOST_MUC);
	$(path + '.fedit-autojoin').attr('checked', false);
}

// Quits the favorites popup
function quitFavorites() {
	// Destroy the popup
	destroyPopup('favorites');
	
	return false;
}

// Adds a room to the favorites
function addThisFavorite(roomXID, roomName) {
	// Button path
	var button = '#favorites .fsearch-results div[data-xid=' + escape(roomXID) + '] a.one-button';
	
	// Add a remove button instead of the add one
	$(button + '.add').replaceWith('<a href="#" class="one-button remove talk-images">' + _e("Remove") + '</a>');
	
	// Click event
	$(button + '.remove').click(function() {
		return removeThisFavorite(roomXID, roomName);
	});
	
	// Hide the add button in the (opened?) groupchat
	$('#' + hex_md5(roomXID) + ' .tools-add').hide();
	
	// Add the database entry
	displayFavorites(roomXID, explodeThis(' (', roomName, 0), getNick(), '0', '');
	
	// Publish the favorites
	favoritePublish();
	
	return false;
}

// Removes a room from the favorites
function removeThisFavorite(roomXID, roomName) {
	// Button path
	var button = '#favorites .fsearch-results div[data-xid=' + escape(roomXID) + '] a.one-button';
	
	// Add a remove button instead of the add one
	$(button + '.remove').replaceWith('<a href="#" class="one-button add talk-images">' + _e("Add") + '</a>');
	
	// Click event
	$(button + '.add').click(function() {
		return addThisFavorite(roomXID, roomName);
	});
	
	// Show the add button in the (opened?) groupchat
	$('#' + hex_md5(roomXID) + ' .tools-add').show();
	
	// Remove the favorite
	removeFavorite(roomXID, true);
	
	// Publish the favorites
	favoritePublish();
	
	return false;
}

// Edits a favorite
function editFavorite() {
	// Path to favorites
	var favorites = '#favorites .';
	
	// Reset the favorites
	resetFavorites();
	
	// Show the edit/remove button, hide the others
	$(favorites + 'fedit-terminate').hide();
	$(favorites + 'fedit-edit').show();
	$(favorites + 'fedit-remove').show();
	
	// We retrieve the values
	var xid = $(favorites + 'fedit-head-select').val();
	var data = XMLFromString(getDB('favorites', xid));
	
	// If this is not the default room
	if(xid != 'none') {
		// We apply the values
		$(favorites + 'fedit-title').val($(data).find('name').text());
		$(favorites + 'fedit-nick').val($(data).find('nick').text());
		$(favorites + 'fedit-chan').val(getXIDNick(xid));
		$(favorites + 'fedit-server').val(getXIDHost(xid));
		$(favorites + 'fedit-password').val($(data).find('password').text());
		
		if($(data).find('autojoin').text() == '1')
			$(favorites + 'fedit-autojoin').attr('checked', true);
	}
	
	else
		resetFavorites();
}

// Adds a favorite
function addFavorite() {
	// Path to favorites
	var favorites = '#favorites .';
	
	// We reset the inputs
	$(favorites + 'fedit-title, ' + favorites + 'fedit-nick, ' + favorites + 'fedit-chan, ' + favorites + 'fedit-server, ' + favorites + 'fedit-password').val('');
	
	// Show the add button, hide the others
	$(favorites + 'fedit-terminate').hide();
	$(favorites + 'fedit-add').show();
}

// Terminate a favorite editing
function terminateThisFavorite(type) {
	// Path to favorites
	var favorites = '#favorites ';
	
	// We get the values of the current edited groupchat
	var old_xid = $(favorites + '.fedit-head-select').val();
	
	var title = $(favorites + '.fedit-title').val();
	var nick = $(favorites + '.fedit-nick').val();
	var room = $(favorites + '.fedit-chan').val();
	var server = $(favorites + '.fedit-server').val();
	var xid = room + '@' + server;
	var password = $(favorites + '.fedit-password').val();
	var autojoin = '0';
	
	if($(favorites + '.fedit-autojoin').filter(':checked').size())
		autojoin = '1';
	
	// We check the missing values and send this if okay
	if((type == 'add') || (type == 'edit')) {
		if(title && nick && room && server) {
			// Remove the edited room
			if(type == 'edit')
				removeFavorite(old_xid, true);
			
			// Display the favorites
			displayFavorites(xid, title, nick, autojoin, password);
			
			// Reset the inputs
			resetFavorites();
		}
		
		else {
			$(favorites + 'input[required]').each(function() {
				var select = $(this);
				
				if(!select.val())
					$(document).oneTime(10, function() {
						select.addClass('please-complete').focus();
					});
				else
					select.removeClass('please-complete');	
			});
		}
	}
	
	// Must remove a favorite?
	else if(type == 'remove') {
		removeFavorite(old_xid, true);
		
		// Reset the inputs
		resetFavorites();
	}
	
	// Publish the new favorites
	favoritePublish();
	
	logThis('Action on this bookmark: ' + room + '@' + server + ' / ' + type, 3);
	
	return false;
}

// Removes a favorite
function removeFavorite(xid, database) {
	// We remove the target favorite everywhere needed
	$('.buddy-conf-groupchat-select option[value=' + xid + ']').remove();
	$('.fedit-head-select option[value=' + xid + ']').remove();
	
	// Must remove it from database?
	if(database)
		removeDB('favorites', xid);
}

// Sends a favorite to the XMPP server
function favoritePublish() {
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var query = iq.setQuery(NS_PRIVATE);
	var storage = query.appendChild(iq.buildNode('storage', {'xmlns': NS_BOOKMARKS}));
	
	// We generate the XML
	for(var i = 0; i < sessionStorage.length; i++) {
		// Get the pointer values
		var current = sessionStorage.key(i);
		
		// If the pointer is on a stored favorite
		if(explodeThis('_', current, 0) == 'favorites') {
			var data = XMLFromString(sessionStorage.getItem(current));
			var xid = $(data).find('xid').text();
			var rName = $(data).find('name').text();
			var nick = $(data).find('nick').text();
			var password = $(data).find('password').text();
			var autojoin = $(data).find('autojoin').text();
			
			// We create the node for this groupchat
			var item = storage.appendChild(iq.buildNode('conference', {'name': rName, 'jid': xid, 'autojoin': autojoin, xmlns: NS_BOOKMARKS}));
			item.appendChild(iq.buildNode('nick', {xmlns: NS_BOOKMARKS}, nick));
			
			if(password)
				item.appendChild(iq.buildNode('password', {xmlns: NS_BOOKMARKS}, password));
			
			logThis('Bookmark sent: ' + xid, 3);
		}
	}
	
	con.send(iq);
}

// Gets a list of the MUC items on a given server
function getGCList() {
	var path = '#favorites .';
	var gcServer = $('.fsearch-head-server').val();
	
	// We reset some things
	$(path + 'fsearch-oneresult').remove();
	$(path + 'fsearch-noresults').hide();
	$(path + 'wait').show();
	
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setTo(gcServer);
	
	iq.setQuery(NS_DISCO_ITEMS);
	
	con.send(iq, handleGCList);
}

// Handles the MUC items list
function handleGCList(iq) {
	var path = '#favorites .';
	var from = fullXID(getStanzaFrom(iq));
	
	if (!iq || (iq.getType() != 'result')) {
		openThisError(3);
		
		$(path + 'wait').hide();
		
		logThis('Error while retrieving the rooms: ' + from, 1);
	}
	
	else {
		var handleXML = iq.getQuery();
		
		if($(handleXML).find('item').size()) {
			// Initialize the HTML code
			var html = '';
			
			$(handleXML).find('item').each(function() {
				var roomXID = $(this).attr('jid');
				var roomName = $(this).attr('name');
				
				if(roomXID && roomName) {
					// Escaped values
					var escaped_xid = encodeOnclick(roomXID);
					var escaped_name = encodeOnclick(roomName);
					
					// Initialize the room HTML
					html += '<div class="oneresult fsearch-oneresult" data-xid="' + escape(roomXID) + '">' + 
							'<div class="room-name">' + roomName.htmlEnc() + '</div>' + 
							'<a href="#" class="one-button join talk-images" onclick="return joinFavorite(\'' + escaped_xid + '\');">' + _e("Join") + '</a>';
					
					// This room is yet a favorite
					if(existDB('favorites', roomXID))
						html += '<a href="#" class="one-button remove talk-images" onclick="return removeThisFavorite(\'' + escaped_xid + '\', \'' + escaped_name + '\');">' + _e("Remove") + '</a>';
					else
						html += '<a href="#" class="one-button add talk-images" onclick="return addThisFavorite(\'' + escaped_xid + '\', \'' + escaped_name + '\');">' + _e("Add") + '</a>';
					
					// Close the room HTML
					html += '</div>';
				}
			});
			
			// Append this code to the popup
			$(path + 'fsearch-results').append(html);
		}
		
		else
			$(path + 'fsearch-noresults').show();
		
		logThis('Rooms retrieved: ' + from, 3);
	}
	
	$(path + 'wait').hide();
}

// Joins a groupchat from favorites
function joinFavorite(room) {
	quitFavorites();
	checkChatCreate(room, 'groupchat', '', '', getXIDNick(room));
	
	return false;
}

// Displays a given favorite
function displayFavorites(xid, name, nick, autojoin, password) {
	// Generate the HTML code
	var html = '<option value="' + encodeQuotes(xid) + '">' + name.htmlEnc() + '</option>';
	
	// Remove the existing favorite
	removeFavorite(xid, false);
	
	// We complete the select forms
	$('#buddy-list .gc-join-first-option, #favorites .fedit-head-select-first-option').after(html);
	
	// We store the informations
	var value = '<groupchat><xid>' + xid.htmlEnc() + '</xid><name>' + name.htmlEnc() + '</name><nick>' + nick.htmlEnc() + '</nick><autojoin>' + autojoin.htmlEnc() + '</autojoin><password>' + password.htmlEnc() + '</password></groupchat>';
	setDB('favorites', xid, value);
}

// Loads the favorites for the popup
function loadFavorites() {
	// Initialize the HTML code
	var html = '';
	
	// Read the database
	for(var i = 0; i < sessionStorage.length; i++) {
		// Get the pointer values
		var current = sessionStorage.key(i);
		
		// If the pointer is on a stored favorite
		if(explodeThis('_', current, 0) == 'favorites') {
			var data = XMLFromString(sessionStorage.getItem(current));
			
			// Add the current favorite to the HTML code
			html += '<option value="' + encodeQuotes($(data).find('xid').text()) + '">' + $(data).find('name').text().htmlEnc() + '</option>';
		}
	}
	
	// Generate specific HTML code
	var favorites_bubble = '<option value="none" class="gc-join-first-option" selected="">' + _e("Select a favorite") +  '</option>' + html;
	var favorites_popup = '<option value="none" class="fedit-head-select-first-option" selected="">' + _e("Select a favorite") + '</option>' + html;
	
	// Append the HTML code
	$('#buddy-list .buddy-conf-groupchat-select').html(favorites_bubble);
	$('#favorites .fedit-head-select').html(favorites_popup);
}

// Plugin launcher
function launchFavorites() {
	var path = '#favorites .';
	
	// Keyboard events
	$(path + 'fsearch-head-server').keyup(function(e) {
		if(e.keyCode == 13) {
			// No value?
			if(!$(this).val())
				$(this).val(HOST_MUC);
			
			// Get the list
			getGCList();
		}
	});
	
	$(path + 'fedit-line input').keyup(function(e) {
		if(e.keyCode == 13) {
			// Edit a favorite
			if($(path + 'fedit-edit').is(':visible'))
				terminateThisFavorite('edit');
			
			// Add a favorite
			else
				terminateThisFavorite('add');
		}
	});
	
	// Change events
	$('.fedit-head-select').change(editFavorite);
	
	// Click events
	$(path + 'room-switcher').click(function() {
		$(path + 'favorites-content').hide();
		resetFavorites();
	});
	
	$(path + 'room-list').click(function() {
		$(path + 'favorites-edit').show();
	});
	
	$(path + 'room-search').click(function() {
		$(path + 'favorites-search').show();
		getGCList();
	});
	
	$(path + 'fedit-add').click(function() {
		return terminateThisFavorite('add');
	});
	
	$(path + 'fedit-edit').click(function() {
		return terminateThisFavorite('edit');
	});
	
	$(path + 'fedit-remove').click(function() {
		return terminateThisFavorite('remove');
	});
	
	$(path + 'bottom .finish').click(function() {
		return quitFavorites();
	});
}
