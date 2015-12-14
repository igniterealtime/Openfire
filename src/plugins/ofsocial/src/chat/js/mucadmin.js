/*

Jappix - An open social platform
These are the mucadmin JS scripts for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Marco Cirillo
Last revision: 03/03/11

*/

// Opens the MUC admin popup
function openMucAdmin(xid, aff) {
	// Popup HTML content
	var html_full = 
	'<div class="top">' + _e("MUC administration") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="head mucadmin-head">' + 
			'<div class="head-text mucadmin-head-text">' + _e("You administrate this room") + '</div>' + 
			
			'<div class="mucadmin-head-jid">' + xid + '</div>' + 
		'</div>' + 
		
		'<div class="mucadmin-forms">' + 
			'<div class="mucadmin-topic">' + 
				'<fieldset>' + 
					'<legend>' + _e("Subject") + '</legend>' + 
					
					'<label for="topic-text">' + _e("Enter new subject") + '</label>' + 
					'<textarea id="topic-text" name="room-topic" rows="8" cols="60" ></textarea>' + 
				'</fieldset>' + 
			'</div>' + 
			
			'<div class="mucadmin-conf">' + 
				'<fieldset>' + 
					'<legend>' + _e("Configuration") + '</legend>' + 
					
					'<div class="results mucadmin-results"></div>' + 
				'</fieldset>' + 
			'</div>' + 
			
			'<div class="mucadmin-aut">' + 
				'<fieldset>' + 
					'<legend>' + _e("Authorizations") + '</legend>' + 
					
					'<label>' + _e("Member list") + '</label>' + 
					'<div class="aut-member aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'member\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
					
					'<label>' + _e("Owner list") + '</label>' + 
					'<div class="aut-owner aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'owner\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
					
					'<label>' + _e("Administrator list") + '</label>' + 
					'<div class="aut-admin aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'admin\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
					
					'<label>' + _e("Outcast list") + '</label>' + 
					'<div class="aut-outcast aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'outcast\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
				'</fieldset>' + 
			'</div>' + 
			
			'<div class="mucadmin-others">' + 
				'<fieldset>' + 
					'<legend>' + _e("Others") + '</legend>' + 
					
					'<label>' + _e("Destroy this MUC") + '</label>' + 
					'<a href="#" onclick="return destroyMucAdmin();">' + _e("Yes, let's do it!") + '</a>' + 
				'</fieldset>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish save">' + _e("Save") + '</a>' + 
		'<a href="#" class="finish cancel">' + _e("Cancel") + '</a>' + 
	'</div>';
	
	var html_partial = 
	'<div class="top">' + _e("MUC administration") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="head mucadmin-head">' + 
			'<div class="head-text mucadmin-head-text">' + _e("You administrate this room") + '</div>' + 
			
			'<div class="mucadmin-head-jid">' + xid + '</div>' + 
		'</div>' + 
		
		'<div class="mucadmin-forms">' + 
			'<div class="mucadmin-aut">' + 
				'<fieldset>' + 
					'<legend>' + _e("Authorizations") + '</legend>' + 
					
					'<label>' + _e("Member list") + '</label>' + 
					'<div class="aut-member aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'member\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
					
					'<label>' + _e("Outcast list") + '</label>' + 
					'<div class="aut-outcast aut-group">' + 
						'<a href="#" class="aut-add" onclick="return addInputMucAdmin(\'\', \'outcast\');">' + _e("Add an input") + '</a>' + 
					'</div>' + 
				'</fieldset>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish save">' + _e("Save") + '</a>' + 
		'<a href="#" class="finish cancel">' + _e("Cancel") + '</a>' + 
	'</div>';	
	
	// Create the popup
	if(aff == 'owner')
		createPopup('mucadmin', html_full);
	if(aff == 'admin')
		createPopup('mucadmin', html_partial);
	
	// Associate the events
	launchMucAdmin();
		
	// We get the affiliated user's privileges
	if(aff == 'owner') {
		queryMucAdmin(xid, 'member');
		queryMucAdmin(xid, 'owner');
		queryMucAdmin(xid, 'admin');
		queryMucAdmin(xid, 'outcast');
		// We query the room to edit
		dataForm(xid, 'muc', '', '', 'mucadmin');
	} else if(aff == 'admin') {
		queryMucAdmin(xid, 'member');
		queryMucAdmin(xid, 'outcast');
	}
}

// Closes the MUC admin popup
function closeMucAdmin() {
	// Destroy the popup
	destroyPopup('mucadmin');
	
	return false;
}

// Removes a MUC admin input
function removeInputMucAdmin(element) {
	var path = $(element).parent();
	
	// We first hide the container of the input
	path.hide();
	
	// Then, we add a special class to the input
	path.find('input').addClass('aut-dustbin');
	
	return false;
}

// Adds a MUC admin input
function addInputMucAdmin(xid, affiliation) {
	var hash = hex_md5(xid + affiliation);
	
	// Add the HTML code
	$('#mucadmin .aut-' + affiliation + ' .aut-add').after(
		'<div class="one-aut ' + hash + '">' + 
			'<input id="aut-' + affiliation + '" name="' + affiliation + '" type="text" class="mucadmin-i" value="' + xid + '" />' + 
			'<a href="#" class="aut-remove">[-]</a>' + 
		'</div>'
	);
	
	// Click event
	$('#mucadmin .' + hash + ' .aut-remove').click(function() {
		return removeInputMucAdmin(this);
	});
	
	// Focus on the input we added
	if(!xid)
		$(document).oneTime(10, function() {
			$('#mucadmin .' + hash + ' input').focus();
		});
	
	return false;
}

// Handles the MUC admin form
function handleMucAdminAuth(iq) {
	// We got the authorizations results
	$(iq.getQuery()).find('item').each(function() {
		// We parse the received xml
		var xid = $(this).attr('jid');
		var affiliation = $(this).attr('affiliation');
		
		// We create one input for one XID
		addInputMucAdmin(xid, affiliation);
	});
	
	// Hide the wait icon
	$('#mucadmin .wait').hide();
	
	logThis('MUC admin items received: ' + fullXID(getStanzaFrom(iq)));
}

// Queries the MUC admin form
function queryMucAdmin(xid, type) {
	// Show the wait icon
	$('#mucadmin .wait').show();
	
	// New IQ
	var iq = new JSJaCIQ();
	
	iq.setTo(xid);
	iq.setType('get');
	
	var iqQuery = iq.setQuery(NS_MUC_ADMIN);
	iqQuery.appendChild(iq.buildNode('item', {'affiliation': type, 'xmlns': NS_MUC_ADMIN}));
	
	con.send(iq, handleMucAdminAuth);
}

// Sends the new chat-room topic
function sendMucAdminTopic(xid) {
	// We get the new topic
	var topic = $('.mucadmin-topic textarea').val();
	
	// We send the new topic if not blank
	if(topic) {
		var m = new JSJaCMessage();
		m.setTo(xid);
		m.setType('groupchat');
		m.setSubject(topic);
		con.send(m);
		
		logThis('MUC admin topic sent: ' + topic, 3);
	}
}

// Sends the MUC admin auth form
function sendMucAdminAuth(xid) {
	// We define the values array
	var types = new Array('member', 'owner', 'admin', 'outcast');

	for(i in types) {
		// We get the current type
		var tType = types[i];
		
		// We loop for all the elements
		$('.mucadmin-aut .aut-' + tType + ' input').each(function() {
			// We set the iq headers
			var iq = new JSJaCIQ();
			iq.setTo(xid);
			iq.setType('set');

			var iqQuery = iq.setQuery(NS_MUC_ADMIN);
	
			// We get the needed values
			var value = $(this).val();
			
			// If there's a value
			if(value)
				var item = iqQuery.appendChild(iq.buildNode('item', {'jid': value, 'xmlns': NS_MUC_ADMIN}));
			
			// It the user had removed the XID
			if($(this).hasClass('aut-dustbin') && value)
				item.setAttribute('affiliation', 'none');
			
			// If the value is not blank and okay
			else if(value)
				item.setAttribute('affiliation', tType);
	
			// We send the iq !
			con.send(iq, handleErrorReply);
		});
	}	
	
	logThis('MUC admin authorizations form sent: ' + xid, 3);
}

// Checks if the MUC room was destroyed
function handleDestroyMucAdminIQ(iq) {
	if(!handleErrorReply(iq)) {
		// We close the groupchat
		var room = fullXID(getStanzaFrom(iq));
		var hash = hex_md5(room);
		quitThisChat(room, hash, 'groupchat');
		
		// We close the muc admin popup
		closeMucAdmin();
		
		// We tell the user that all is okay
		openThisInfo(5);
		
		// We remove the user's favorite
		if(existDB('favorites', room))
			removeThisFavorite(room, explodeThis('@', room, 0));
		
		logThis('MUC admin destroyed: ' + room, 3);
	}
	
	// We hide the wait icon
	$('#mucadmin .wait').hide();
}

// Destroys a MUC room
function destroyMucAdminIQ(xid) {
	// We ask the server to delete the room
	var iq = new JSJaCIQ();
	
	iq.setTo(xid);
	iq.setType('set');
	var iqQuery = iq.setQuery(NS_MUC_OWNER);
	iqQuery.appendChild(iq.buildNode('destroy', {'xmlns': NS_MUC_OWNER}));
	
	con.send(iq, handleDestroyMucAdminIQ);
	
	logThis('MUC admin destroy sent: ' + xid, 3);
	
	return false;
}

// Performs the MUC room destroy functions
function destroyMucAdmin() {
	// We get the XID of the current room
	var xid = $('#mucadmin .mucadmin-head-jid').text();
	
	// We show the wait icon
	$('#mucadmin .wait').show();
	
	// We send the iq
	destroyMucAdminIQ(xid);
}

// Sends all the MUC admin stuffs
function sendMucAdmin() {
	// We get the XID of the current room
	var xid = $('#mucadmin .mucadmin-head-jid').text();
	
	// We change the room topic
	sendMucAdminTopic(xid);
	
	// We send the needed queries
	sendDataForm('x', 'submit', 'submit', $('#mucadmin .mucadmin-results').attr('data-session'), xid, '', '', 'mucadmin');
	sendMucAdminAuth(xid);
}

// Saves the MUC admin elements
function saveMucAdmin() {
	// We send the new options
	sendMucAdmin();
	
	// And we quit the popup
	return closeMucAdmin();
}

// Plugin launcher
function launchMucAdmin() {
	// Click events
	$('#mucadmin .bottom .finish').click(function() {
		if($(this).is('.cancel'))
			return closeMucAdmin();
		if($(this).is('.save'))
			return saveMucAdmin();
	});
}
