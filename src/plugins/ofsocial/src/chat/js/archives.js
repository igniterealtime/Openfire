/*

Jappix - An open social platform
These are the archives functions for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 03/03/11

*/

// Opens the archive tools
function openArchives() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Message archives") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="filter">' + 
			'<select class="friend" multiple=""></select>' + 
			
			'<div class="date"></div>' + 
		'</div>' + 
		
		'<div class="current">' + 
			'<span class="name"></span>' + 
			'<span class="time">' + _e("Please select a friend to view the chat history.") + '</span>' + 
		'</div>' + 
		
		'<div class="logs" id="chat-content-archives"></div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('archives', html);
	
	// Associate the events
	launchArchives();
	
	// Get all the buddies in our roster
	var buddies = getAllBuddies();
	var options = '';
	
	for(i in buddies) {
		var current = buddies[i];
		
		// Add the current buddy
		options += '<option value="' + encodeQuotes(current) + '">' + getBuddyName(current).htmlEnc() + '</option>';
	}
	
	// Can append the buddy HTML code?
	if(options)
		$('#archives .filter .friend').append(options);
	
	return false;
}

// Closes the archive tools
function closeArchives() {
	// Destroy the popup
	destroyPopup('archives');
	
	return false;
}

// Gets the archives list for a buddy
function getListArchives(xid) {
	// Reset the archives viewer
	$('#archives .logs').empty();
	
	// Show the waiting icon
	$('#archives .wait').show();
	
	// Apply the ID
	var id = genID();
	$('#archives').attr('data-session', id);
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setID(id);
	
	var list = iq.appendNode('list', {'xmlns': NS_URN_ARCHIVE, 'with': xid});
	var set = list.appendChild(iq.buildNode('set', {'xmlns': NS_RSM}));
	set.appendChild(iq.buildNode('max', {'xmlns': NS_RSM}, '0'));
	
	con.send(iq, handleListArchives);
	
	logThis('Getting archives list for: ' + xid + '...');
}

// Handles the archives list for a buddy
function handleListArchives(iq) {
	// Hide the waiting icon
	$('#archives .wait').hide();
	
	// Any error?
	if(handleErrorReply(iq) || !exists('#archives[data-session=' + iq.getID() + ']'))
		return;
	
	// Get the last archive date
	var last = $(iq.getNode()).find('list set changed').text();
	
	// Any last archive?
	if(last) {
		// Read the date
		var date = Date.jab2date(last);
		
		// Change the datepicker value
		$('#archives .filter .date').DatePickerSetDate(date, true);
		
		// Retrieve the archives
		checkChangeArchives();
	}
	
	logThis('Got archives list.', 2);
}

// Gets the archives for a day
function getDayArchives(xid, date) {
	// Reset the archives viewer
	$('#archives .logs').empty();
	
	// Show the waiting icon
	$('#archives .wait').show();
	
	// Apply the ID
	var id = genID();
	$('#archives').attr('data-session', id);
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setID(id);
	
	iq.appendNode('list', {'xmlns': NS_URN_ARCHIVE, 'with': xid, 'start': date + 'T00:00:00Z', 'end': date + 'T23:59:59Z'});
	
	con.send(iq, handleDayArchives);
	
	logThis('Getting day archives (' + date + ') for: ' + xid + '...');
}

// Handles the archives for a day
function handleDayArchives(iq) {
	// Hide the waiting icon
	$('#archives .wait').hide();
	
	// Any error?
	if(handleErrorReply(iq) || !exists('#archives[data-session=' + iq.getID() + ']'))
		return;
	
	// Get each archive thread
	$(iq.getNode()).find('chat').each(function() {
		// Current values
		var xid = $(this).attr('with');
		var start = $(this).attr('start');
		
		if(xid && start)
			$('#archives .logs').append('<input class="archives-pending" type="hidden" data-with="' + encodeQuotes(xid) + '" data-start="' + encodeQuotes(start) + '" />');
	});
	
	// Display the day
	var date = parseDay($('#archives .filter .date').DatePickerGetDate(true) + 'T00:00:00Z' + getDateTZO());
	
	// Try to get the first thread
	var pending = '#archives input.archives-pending:first';
	
	if(!exists(pending))
		date = printf(_e("Nothing found for: %s"), date);
	
	else {
		retrieveArchives($(pending).attr('data-with'), $(pending).attr('data-start'));
		$(pending).remove();
	}
	
	$('#archives .current .time').text(date);
	
	logThis('Got day archives.', 2);
}

// Retrieves a specified archive collection
function retrieveArchives(xid, start) {
	// Show the waiting icon
	$('#archives .wait').show();
	
	// Apply the ID
	var id = genID();
	$('#archives').attr('data-session', id);
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setID(id);
	
	var list = iq.appendNode('retrieve', {'xmlns': NS_URN_ARCHIVE, 'with': xid, 'start': start});
	
	con.send(iq, handleRetrieveArchives);
	
	logThis('Retrieving archives (start: ' + start + ') for: ' + xid + '...');
}

// Handles a specified archive collection
function handleRetrieveArchives(iq) {
	// Hide the waiting icon
	$('#archives .wait').hide();
	
	// Any error?
	if(handleErrorReply(iq) || !exists('#archives[data-session=' + iq.getID() + ']'))
		return;
	
	// Get the node
	var chat = $(iq.getNode()).find('chat:first');
	
	// Get the buddy XID
	var xid = bareXID(chat.attr('with'));
	
	// Get the start date & stamp
	var start_date = Date.jab2date(chat.attr('start'));
	var start_stamp = extractStamp(start_date);
	
	// Parse the result chat
	chat.find('to, from').each(function() {
		var node = (this).nodeName;
		var stamp = start_stamp + parseInt($(this).attr('secs'));
		var date = extractTime(new Date(stamp * 1000));
		var body = $(this).find('body').text();
		
		// Is it my message?
		if((node == 'to') && body)
			displayMessage('chat', getXID(), 'archives', getBuddyName(getXID()).htmlEnc(), body, date, start_stamp, 'user-message', true, '', 'me');
		
		// Is it a buddy message?
		else if((node == 'from') && body)
			displayMessage('chat', xid, 'archives', getBuddyName(xid).htmlEnc(), body, date, start_stamp, 'user-message', true, '', 'him');
	});
	
	// Not the latest thread?
	var pending = '#archives input.archives-pending:first';
	
	if(exists(pending)) {
		retrieveArchives($(pending).attr('data-with'), $(pending).attr('data-start'));
		$(pending).remove();
	}
	
	// Everything has been retrieved, get the avatars
	else {
		getAvatar(getXID(), 'cache', 'true', 'forget');
		getAvatar(xid, 'cache', 'true', 'forget');
	}
	
	logThis('Got archives.', 2);
}

// Gets the archiving configuration
function getConfigArchives() {
	// Lock the archiving options
	$('#archiving').attr('checked', false).attr('disabled', true);
	
	// Get the archiving configuration
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	iq.appendNode('pref', {'xmlns': NS_URN_ARCHIVE});
	
	con.send(iq, handleGetConfigArchives);
}

// Handles the archiving configuration
function handleGetConfigArchives(iq) {
	// Reset the options stuffs
	waitOptions('archives');
	
	// Unlock the archiving options
	$('#archiving').removeAttr('disabled');
	
	// End if not a result
	if(!iq || (iq.getType() != 'result'))
		return;
	
	// Extract the preferences from the IQ
	var enabled = $(iq.getNode()).find('pref auto').attr('save');
	
	// Define the input enabling/disabling vars
	var checked = true;
	
	if(enabled != 'true')
		checked = false;
	
	// Apply the values
	$('#archiving').attr('checked', checked);
}

// Configures the archiving on the server
function configArchives(enabled) {
	// Configure the auto element
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	iq.appendNode('auto', {'xmlns': NS_URN_ARCHIVE, 'save': enabled});
	
	con.send(iq, handleConfigArchives);
	
	// Configure the default element
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var pref = iq.appendNode('pref', {'xmlns': NS_URN_ARCHIVE});
	pref.appendChild(iq.appendNode('default', {'xmlns': NS_URN_ARCHIVE, 'otr': 'concede', 'save': 'body'}));
	
	con.send(iq);
	
	// Configure the method element
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var mType = new Array('auto', 'local', 'manual');
	var mUse = new Array('prefer', 'concede', 'concede');
	
	var pref = iq.appendNode('pref', {'xmlns': NS_URN_ARCHIVE});
	
	for(i in mType)
		pref.appendChild(iq.appendNode('method', {'xmlns': NS_URN_ARCHIVE, 'type': mType[i], 'use': mUse[i]}));
	
	con.send(iq);
	
	// Logger
	logThis('Configuring archives...', 3);
}

// Handles the archives configuration
function handleConfigArchives(iq) {
	if(!iq || (iq.getType() != 'result'))
		logThis('Archives not configured.', 2);
	else
		logThis('Archives configured.', 3);
}

// Checks if the datepicker has changed
function checkChangeArchives() {
	var xid = $('#archives .filter .friend').val();
	var date = $('#archives .filter .date').DatePickerGetDate(true);
	
	// No XID?
	if(!xid || !xid.length)
		return;
	
	// Too many value?
	if(xid.length > 1) {
		$('#archives .filter .friend').val(xid[0]);
		
		return;
	}
	
	// Get the first XID
	xid = xid[0];
	
	// Get the archives
	getDayArchives(xid, date);
}

// Update the archives with the selected XID
function updateArchives() {
	// Read the values
	var xid = $('#archives .filter .friend').val();
	var date = $('#archives .filter .date').DatePickerGetDate(true);
	
	// No XID?
	if(!xid || !xid.length)
		return;
	
	// Too many value?
	if(xid.length > 1) {
		$('#archives .filter .friend').val(xid[0]);
		
		return;
	}
	
	// Get the first XID
	xid = xid[0];
	
	// Apply the current marker
	$('#archives .current .name').text(getBuddyName(xid));
	$('#archives .current .time').text(parseDay(date + 'T00:00:00Z' + getDateTZO()));
	
	// Get the archives
	getListArchives(xid, date);
}

// Plugin launcher
function launchArchives() {
	// Current date
	var current_date = explodeThis('T', getXMPPTime(), 0);
	
	// Datepicker
	$('#archives .filter .date').DatePicker({
		flat: true,
		date: current_date,
		current: current_date,
		calendars: 1,
		starts: 1,
		onChange: checkChangeArchives
	});
	
	// Click events
	$('#archives .bottom .finish').click(function() {
		return closeArchives();
	});
	
	// Change event
	$('#archives .filter .friend').change(updateArchives);
}
