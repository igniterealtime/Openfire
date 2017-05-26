/*

Jappix - An open social platform
These are the user-infos JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/03/11

*/

// Opens the user-infos popup
function openUserInfos(xid) {
	// Can show shortcuts?
	var shortcuts = '';
	
	if(xid != getXID()) {
		shortcuts = '<div class="shortcuts">' + 
					'<a href="#" class="message talk-images" title="' + _e("Send him/her a message") + '" onclick="closeUserInfos(); return composeInboxMessage(\'' + encodeOnclick(xid) + '\');"></a>' + 
					'<a href="#" class="chat talk-images" title="' + _e("Start a chat with him/her") + '" onclick="closeUserInfos(); return checkChatCreate(\'' + encodeOnclick(xid) + '\', \'chat\');"></a>' + 
					'<a href="#" class="command talk-images" title="' + _e("Command") + '" onclick="closeUserInfos(); return retrieveAdHoc(\'' + encodeOnclick(xid) + '\');"></a>' + 
		             '</div>';
	}
	
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("User profile") + '</div>' + 
	
	'<div class="tab">' + 
		'<a href="#" class="tab-active" data-key="1">' + _e("General") + '</a>' + 
		'<a href="#" data-key="2">' + _e("Advanced") + '</a>' + 
		'<a href="#" data-key="3">' + _e("Comments") + '</a>' + 
	'</div>' + 
	
	'<div class="content">' + 
		'<div class="lap-active one-lap info1">' + 
			'<div class="main-infos">' + 
				'<div class="avatar-container">' + 
					'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
				'</div>' + 
				
				'<h1 id="BUDDY-FN" class="reset-info">' + _e("unknown") + '</h1>' + 
				'<h2 class="buddy-xid" class="reset-info">' + _e("unknown") + '</h2>' + 
				'<h3 class="buddy-last" class="reset-info">' + _e("unknown") + '</h3>' + 
				
				shortcuts + 
			'</div>' + 
			
			'<div class="block-infos">' + 
				'<div class="one-line"><b>' + _e("Date of birth") + '</b><span id="BUDDY-BDAY" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("E-mail") + '</b><span id="BUDDY-EMAIL-USERID" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("Phone") + '</b><span id="BUDDY-TEL-NUMBER" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("Website") + '</b><span id="BUDDY-URL" class="reset-info">' + _e("unknown") + '</span></div>' + 
			'</div>' + 
			
			'<div class="block-infos">' + 
				'<div class="one-line"><b>' + _e("Client") + '</b><span id="BUDDY-CLIENT" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("System") + '</b><span id="BUDDY-SYSTEM" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("Local time") + '</b><span id="BUDDY-TIME" class="reset-info">' + _e("unknown") + '</span></div>' + 
			'</div>' + 
		'</div>' + 
		
		'<div class="one-lap info2">' + 
			'<div class="block-infos">' + 
				'<div class="one-line"><b>' + _e("Street") + '</b><span id="BUDDY-ADR-STREET" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("City") + '</b><span id="BUDDY-ADR-LOCALITY" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("Postal code") + '</b><span id="BUDDY-ADR-PCODE" class="reset-info">' + _e("unknown") + '</span></div>' + 
				
				'<div class="one-line"><b>' + _e("Country") + '</b><span id="BUDDY-ADR-CTRY" class="reset-info">' + _e("unknown") + '</span></div>' + 
			'</div>' + 
			
			'<div class="block-infos">' + 
				'<div class="one-line"><b>' + _e("Biography") + '</b><span id="BUDDY-DESC" class="reset-info">' + _e("unknown") + '</span></div>' + 
			'</div>' + 
		'</div>' + 
		
		'<div class="one-lap info3">' + 
			'<textarea class="rosternotes" rows="8" cols="60"></textarea>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('userinfos', html);
	
	// Associate the events
	launchUserInfos();
	
	// We retrieve the user's vcard
	retrieveUserInfos(xid);
	
	return false;
}

// Closes the user-infos popup
function closeUserInfos() {
	// Send the buddy comments
	sendBuddyComments();
	
	// Destroy the popup
	destroyPopup('userinfos');
	
	return false;
}

// Gets the user-infos
function retrieveUserInfos(xid) {
	// We setup the waiting indicator
	markers = 'vcard last';
	
	// We put the user's XID
	$('#userinfos .buddy-xid').text(xid);
	
	// We get the vCard
	getVCard(xid, 'buddy');
	
	// Get the highest resource for this XID
	var cXID = getHighestResource(xid);
	var pXID = xid;
	
	// If the user is logged in
	if(cXID) {
		// Change the XID
		pXID = cXID;
		
		// We request the user's system infos
		queryUserInfos(cXID, 'version')
		
		// We request the user's local time
		queryUserInfos(cXID, 'time')
		
		// Add these to the markers
		markers += ' version time';
	}
	
	// We request the user's last activity
	queryUserInfos(pXID, 'last');
	
	// Add the markers
	$('#userinfos .content').addClass(markers);
	
	// We request all the user's comments
	displayBuddyComments(xid);
}

// Builds the asked user-infos query
function queryUserInfos(xid, mode) {
	// Generate a session ID
	var id = genID();
	$('#userinfos').attr('data-' + mode, id);
	
	// New IQ
	var iq = new JSJaCIQ();
	
	iq.setID(id);
	iq.setType('get');
	iq.setTo(xid);
	
	// Last activity query
	if(mode == 'last') {
		iq.setQuery(NS_LAST);
		con.send(iq, lastActivityUserInfos);
	}
	
	// Time query
	else if(mode == 'time') {
		iq.appendNode('time', {'xmlns': NS_URN_TIME});
		con.send(iq, localTimeUserInfos);
	}
	
	// Version query
	else if(mode == 'version') {
		iq.setQuery(NS_VERSION);
		con.send(iq, versionUserInfos);
	}
}

// Checks if the waiting item can be hidden
function vCardBuddyInfos() {
	$('#userinfos .content').removeClass('vcard');
	wUserInfos();
}

// Displays the buddy comments
function displayBuddyComments(xid) {
	// We get the value in the database
	var value = getDB('rosternotes', xid);
	
	// Display the value
	if(value)
		$('#userinfos .rosternotes').val(value);
}

// Displays the user's last activity result
function lastActivityUserInfos(iq) {
	// Extract the request ID
	var id = iq.getID();
	var path = '#userinfos[data-last=' + id + ']';
	
	// End if session does not exist
	if(!exists(path))
		return;
	
	if(iq && (iq.getType() == 'result')) {
		// Get the values
		var from = fullXID(getStanzaFrom(iq));
		var seconds = $(iq.getNode()).find('query').attr('seconds');
		
		// Any seconds?
		if(seconds != undefined) {
			// Initialize the parsing
			var last;
			seconds = parseInt(seconds);
			
			// Active user
			if(seconds <= 60)
				last = _e("User currently active");
			
			// Inactive user
			else {
				// Parse the date
				var date_now = new Date();
				var time_now = date_now.getTime();
				var date_last = new Date(date_now - (seconds * 1000));
				var date = date_last.toLocaleString();
				
				// Offline user
				if(from.indexOf('/') == -1)
					last = printf(_e("Last seen: %s"), date);
				
				// Online user
				else
					last = printf(_e("Inactive since: %s"), date);
			}
			
			// Append this text
			$('#userinfos .buddy-last').text(last);
		}
		
		logThis('Last activity received: ' + from);
	}
	
	$('#userinfos .content').removeClass('last');
	wUserInfos();
}

// Displays the user's software version result
function versionUserInfos(iq) {
	// Extract the request ID
	var id = iq.getID();
	var path = '#userinfos[data-version=' + id + ']';
	
	// End if session does not exist
	if(!exists(path))
		return;
	
	// Extract the reply data
	if(iq && (iq.getType() == 'result')) {
		// Get the values
		var xml = iq.getQuery();
		var name = $(xml).find('name').text();
		var version = $(xml).find('version').text();
		var os = $(xml).find('os').text();
		
		// Put the values together
		if(name && version)
			name = name + ' ' + version;
		
		// Display the values
		if(name)
			$(path + ' #BUDDY-CLIENT').text(name);
		if(os)
			$(path + ' #BUDDY-SYSTEM').text(os);
		
		logThis('Software version received: ' + fullXID(getStanzaFrom(iq)));
	}
	
	$('#userinfos .content').removeClass('version');
	wUserInfos();
}

// Displays the user's local time result
function localTimeUserInfos(iq) {
	// Extract the request ID
	var id = iq.getID();
	var path = '#userinfos[data-time=' + id + ']';
	
	// End if session does not exist
	if(!exists(path))
		return;
	
	if(iq && (iq.getType() == 'result')) {
		// Get the values
		var xml = iq.getNode();
		var tzo = $(xml).find('tzo').text();
		var utc = $(xml).find('utc').text();
		
		// Any UTC?
		if(utc) {
			// Add the TZO if there's no one
			if(tzo && utc.match(/^(.+)Z$/))
				utc = RegExp.$1 + tzo;
			
			// Get the local date string
			var local_string = Date.hrTime(utc);
			
			// Then display it
			$(path + ' #BUDDY-TIME').text(local_string);
		}
		
		logThis('Local time received: ' + fullXID(getStanzaFrom(iq)));
	}
	
	$('#userinfos .content').removeClass('time');
	wUserInfos();
}

// Hides the waiting image if needed
function wUserInfos() {
	var selector = $('#userinfos .content');
	
	if(!selector.hasClass('vcard') && !selector.hasClass('last') && !selector.hasClass('version') && !selector.hasClass('time'))
		$('#userinfos .wait').hide();
}

// Sends the buddy comments
function sendBuddyComments() {
	// Update the current value
	var value = $('#userinfos .rosternotes').val();
	var xid = $('#userinfos .buddy-xid').text();
	
	// Necessary to update?
	var old_value = getDB('rosternotes', xid);
	
	if((old_value == value) || (!old_value && !value))
		return false;
	
	// Update the database
	setDB('rosternotes', xid, value);
	
	// Send the new buddy storage values
	var iq = new JSJaCIQ();
	iq.setType('set');
	var query = iq.setQuery(NS_PRIVATE);
	var storage = query.appendChild(iq.buildNode('storage', {'xmlns': NS_ROSTERNOTES}));
	
	// We regenerate the XML
	for(var i = 0; i < sessionStorage.length; i++) {
		// Get the pointer values
		var current = sessionStorage.key(i);
		
		// If the pointer is on a stored rosternote
		if(explodeThis('_', current, 0) == 'rosternotes') {
			var xid = explodeThis('_', current, 1);
			var value = sessionStorage.getItem(current);
			
			if(xid && value)
				storage.appendChild(iq.buildNode('note', {'jid': xid, 'xmlns': NS_ROSTERNOTES}, value));
		}
	}
	
	con.send(iq);
	
	return false;
}

// Switches the user-infos tabs
function switchUInfos(id) {
	$('#userinfos .content .one-lap').hide();
	$('#userinfos .content .info' + id).show();
	$('#userinfos .tab a').removeClass('tab-active');
	$('#userinfos .tab a[data-key=' + id + ']').addClass('tab-active');
	
	return false;
}

// Gets the user's informations when creating a new chat
function getUserInfos(hash, xid, nick, type) {
	// This is a normal chat
	if(type != 'private') {
		// Display the buddy name
		if(nick) {
			$('#' + hash + ' .top .name .bc-name').text(nick);
			$('#page-switch .' + hash + ' .name').text(nick);
		}
		
		// Get the buddy PEP informations
		displayAllPEP(xid);
	}
	
	// Display the buddy presence
	presenceFunnel(xid, hash);
}

// Plugin launcher
function launchUserInfos() {
	// Click events
	$('#userinfos .tab a').click(function() {
		// Yet active?
		if($(this).hasClass('tab-active'))
			return false;
		
		// Switch to the good tab
		var key = parseInt($(this).attr('data-key'));
		
		return switchUInfos(key);
	});
	
	$('#userinfos .bottom .finish').click(function() {
		return closeUserInfos();
	});
}
