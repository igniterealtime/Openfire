/*

Jappix - An open social platform
These are the Roster Item Exchange JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Opens the welcome tools
function openRosterX(data) {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Suggested friends") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="rosterx-head">' + 
			'<a href="#" class="uncheck">' + _e("Uncheck all") + '</a>' + 
			'<a href="#" class="check">' + _e("Check all") + '</a>' + 
		'</div>' + 
		
		'<div class="results"></div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<a href="#" class="finish save">' + _e("Save") + '</a>' + 
		'<a href="#" class="finish cancel">' + _e("Cancel") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('rosterx', html);
	
	// Associate the events
	launchRosterX();
	
	// Parse the data
	parseRosterX(data);
	
	logThis('Roster Item Exchange popup opened.');
}

// Closes the welcome tools
function closeRosterX() {
	// Destroy the popup
	destroyPopup('rosterx');
	
	return false;
}

// Parses a rosterx query
function parseRosterX(data) {
	// Main selector
	var x = $(data).find('x[xmlns=' + NS_ROSTERX + ']:first');
	
	// Parse data
	x.find('item').each(function() {
		// Generate group XML
		var group = '';
		
		$(this).find('group').each(function() {
			group += '<group>' + $(this).text().htmlEnc() + '</group>';
		});
		
		if(group)
			group = '<groups>' + group + '</groups>';
		
		// Display it!
		displayRosterX($(this).attr('jid'), $(this).attr('name'), group, $(this).attr('action'));
	});
	
	// Click to check/uncheck
	$('#rosterx .oneresult').click(function(evt) {
		// No need to apply when click on input
		if($(evt.target).is('input[type=checkbox]'))
			return;
		
		// Input selector
		var checkbox = $(this).find('input[type=checkbox]');
		
		// Check or uncheck?
		if(checkbox.filter(':checked').size())
			checkbox.removeAttr('checked');
		else
			checkbox.attr('checked', true);
	});
}

// Displays a rosterx item
function displayRosterX(xid, nick, group, action) {
	// End if no XID
	if(!xid)
		return false;
	
	// Set up a default action if no one
	if(!action || (action != 'modify') || (action != 'delete'))
		action = 'add';
	
	// Override "undefined" for nickname
	if(!nick)
		nick = '';
	
	// Display it
	$('#rosterx .results').append(
		'<div class="oneresult">' + 
			'<input type="checkbox" checked="" data-name="' + encodeQuotes(nick) + '" data-xid="' + encodeQuotes(xid) + '" data-action="' + encodeQuotes(action) + '" data-group="' + encodeQuotes(group) + '" />' + 
			'<span class="name">' + nick.htmlEnc() + '</span>' + 
			'<span class="xid">' + xid.htmlEnc() + '</span>' + 
			'<span class="action ' + action + ' talk-images"></span>' + 
		'</div>'
	);
}

// Saves the rosterx settings
function saveRosterX() {
	// Send the requests
	$('#rosterx .results input[type=checkbox]').filter(':checked').each(function() {
		// Read the attributes
		var nick = $(this).attr('data-name');
		var xid = $(this).attr('data-xid');
		var action = $(this).attr('data-action');
		var group = $(this).attr('data-group');
		
		// Parse groups XML
		if(group) {
			var group_arr = []
			
			$(group).find('group').each(function() {
				group_arr.push($(this).text().revertHtmlEnc());
			});
		}
		
		// Process the asked action
		var roster_item = $('#buddy-list .' + hex_md5(xid));
		
		switch(action) {
			// Buddy add
			case 'add':
				if(!exists(roster_item)) {
					sendSubscribe(xid, 'subscribe');
					sendRoster(xid, '', nick, group_arr);
				}
				
				break;
			
			// Buddy edit
			case 'modify':
				if(exists(roster_item))
					sendRoster(xid, '', nick, group_arr);
				
				break;
			
			// Buddy delete
			case 'delete':
				if(exists(roster_item))
					sendRoster(xid, 'remove');
				
				break;
		}
	});
	
	// Close the popup
	closeRosterX();
}

// Plugin launcher
function launchRosterX() {
	// Click events
	$('#rosterx .bottom .finish').click(function() {
		if($(this).is('.save'))
			return saveRosterX();
		if($(this).is('.cancel'))
			return closeRosterX();
	});
	
	$('#rosterx .rosterx-head a').click(function() {
		if($(this).is('.check'))
			$('#rosterx .results input[type=checkbox]').attr('checked', true);
		else if($(this).is('.uncheck'))
			$('#rosterx .results input[type=checkbox]').removeAttr('checked');
		
		return false;
	});
}
