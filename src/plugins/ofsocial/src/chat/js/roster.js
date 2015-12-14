/*

Jappix - An open social platform
These are the roster JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 19/05/11

*/

// Gets the roster items
function getRoster() {
	var iq = new JSJaCIQ();
	
	iq.setType('get');
	iq.setQuery(NS_ROSTER);
	
	con.send(iq, handleRoster);
}

// Handles the roster items
function handleRoster(iq) {
	// Define some variables
	var handleXML = iq.getQuery();
	var current, xid, dName, subscription, group, xidHash, getNick, nick;
	
	// Parse the vcard xml
	$(handleXML).find('item').each(function() {
		parseRoster($(this), 'load');
	});
	
	// Update our avatar (if changed), and send our presence
	getAvatar(getXID(), 'force', 'true', 'forget');
	
	logThis('Roster received.');
}

// Parses the group XML and display the roster
function parseRoster(current, mode) {
	// Get the values
	xid = current.attr('jid');
	dName = current.attr('name');
	subscription = current.attr('subscription');
	xidHash = hex_md5(xid);
	
	// Create an array containing the groups
	var groups = new Array();
	
	current.find('group').each(function() {
		var group_text = $(this).text();
		
		if(group_text)
			groups.push(group_text);
	});
	
	// No group?
	if(!groups.length)
		groups.push(_e("Unclassified"));
	
	// If no name is defined, we get the default nick of the buddy
	if(!dName)
		dName = getXIDNick(xid);
	
	displayRoster(xid, xidHash, dName, subscription, groups, mode);
}

// Updates the roster groups
function updateGroups() {
	$('#buddy-list .one-group').each(function() {
		// Current values
		var check = $(this).find('.buddy').size();
		var hidden = $(this).find('.buddy:not(.hidden-buddy:hidden)').size();
		
		// Special case: the filtering tool
		if(SEARCH_FILTERED)
			hidden = $(this).find('.buddy:visible').size();
		
		// If the group is empty
		if(!check)
			$(this).remove();
		
		// If the group contains no online buddy (and is not just hidden)
		if(!hidden && $(this).find('a.group').hasClass('minus'))
			$(this).hide();
		else
			$(this).show();
	});
}

// Displays a defined roster item
function displayRoster(dXID, dXIDHash, dName, dSubscription, dGroup, dMode) {
	// First remove the buddy
	$('#buddy-list .' + dXIDHash).remove();
	
	// Define some things around the groups
	var is_gateway = isGateway(dXID);
	var gateway = '';
	
	if(is_gateway) {
		gateway = ' gateway';
		dGroup = new Array(_e("Gateways"));
	}
	
	// Remove request (empty his social channel)
	if(dSubscription == 'remove')
		$('#channel .mixed .one-update.update_' + dXIDHash).remove();
	
	// Other request
	else {
		// Is this buddy blocked?
		var privacy_class = '';
		var privacy_state = statusPrivacy('block', dXID);
		
		if(privacy_state == 'deny')
			privacy_class = ' blocked';
		
		// For each group this buddy has
		for(i in dGroup) {
			var cGroup = dGroup[i];
			
			if(cGroup) {
				// Process some vars
				var groupHash = 'group' + hex_md5(cGroup);
				var groupContent = '#buddy-list .' + groupHash;
				var groupBuddies = groupContent + ' .group-buddies';
				
				// Is this group blocked?
				if((statusPrivacy('block', cGroup) == 'deny') && (privacy_state != 'allow'))
					privacy_class = ' blocked';
				
				// Group not yet displayed
				if(!exists(groupContent)) {
					// Define some things
					var groupCont = '#buddy-list .content';
					var groupToggle = groupCont + ' .' + groupHash + ' a.group';
					
					// Create the HTML markup of the group
					$(groupCont).prepend(
						'<div class="' + groupHash + ' one-group" data-group="' + escape(cGroup) + '">' + 
							'<a href="#" class="group talk-images minus">' + cGroup.htmlEnc() + '</a>' + 
							'<div class="group-buddies"></div>' + 
						'</div>'
					);
					
					// Create the click event which will hide and show the content
					$(groupToggle).click(function() {
						var group = $(groupBuddies);
						var group_toggle = $(groupContent + ' a.group');
						
						// We must hide the buddies
						if(group_toggle.hasClass('minus')) {
							group.hide();
							group_toggle.removeClass('minus').addClass('plus');
							
							// Remove the group opened buddy-info
							closeBubbles();
						}
						
						// We must show the buddies
						else {
							group_toggle.removeClass('plus').addClass('minus');
							group.show();
						}
						
						return false;
					});
				}
				
				// Initialize the HTML code
				var name_code = '<p class="buddy-name">' + dName.htmlEnc() + '</p>';
				var presence_code = '<p class="buddy-presence talk-images unavailable">' + _e("Unavailable") + '</p>';
				
				var html = '<div class="hidden-buddy buddy ibubble ' + dXIDHash + gateway + privacy_class + '" data-xid="' + escape(dXID) + '">' + 
						'<div class="buddy-click">';
				
				// Display avatar if not gateway
				if(!is_gateway)
					html += '<div class="avatar-container">' + 
							'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
						'</div>';
				
				html += '<div class="name">';
				
				// Special gateway code
				if(is_gateway)
					html += presence_code +
						name_code;
				
				else
					html += name_code + 
						presence_code;
				
				html += '</div></div></div>';
				
				// Create the DOM element for this buddy
				$(groupBuddies).append(html);
				
				// Apply the hover event
				applyBuddyHover(dXID, dXIDHash, dName, dSubscription, dGroup, groupHash);
			}
		}
		
		// Click event on this buddy
		$('#buddy-list .' + dXIDHash + ' .buddy-click').click(function() {
			return checkChatCreate(dXID, 'chat');
		});
		
		// We get the user presence if necessary
		if(dMode == 'presence')
			presenceFunnel(dXID, dXIDHash);
		
		// If the buddy must be shown
		if(BLIST_ALL)
			$('#buddy-list .' + dXIDHash).show();
	}
	
	// We update our groups
	if(!SEARCH_FILTERED)
		updateGroups();
	else
		funnelFilterBuddySearch();
}

// Applies the buddy editing input events
function applyBuddyInput(xid) {
	// Initialize
	var path = '#buddy-list .buddy[data-xid=' + escape(xid) + ']';
	var rename = path + ' .bm-rename input';
	var group = path + ' .bm-group input';
	var manage_infos = path + ' .manage-infos';
	var bm_choose = manage_infos + ' div.bm-choose';
	
	// Keyup events
	$(rename).keyup(function(e) {
		if(e.keyCode == 13) {
			// Send the item
			sendRoster(xid, '', trim($(rename).val()), thisBuddyGroups(xid));
			
			// Remove the buddy editor
			closeBubbles();
			
			return false;
		}
	});
	
	$(group).keyup(function(e) {
		if(e.keyCode == 13) {
			// Empty input?
			if(!trim($(this).val())) {
				// Send the item
				sendRoster(xid, '', trim($(rename).val()), thisBuddyGroups(xid));
				
				// Remove the buddy editor
				closeBubbles();
				
				return false;
			}
			
			// Get the values
			var this_value = trim($(this).val());
			var escaped_value = escape(this_value);
			
			// Check if the group yet exists
			var group_exists = false;
			
			$(bm_choose + ' label span').each(function() {
				if($(this).text() == this_value)
					group_exists = true;
			});
			
			// Create a new checked checkbox
			if(!group_exists)
				$(bm_choose).prepend('<label><input type="checkbox" data-group="' + escaped_value + '" /><span>' + this_value.htmlEnc() + '</span></label>');
			
			// Check the checkbox
			$(bm_choose + ' input[data-group=' + escaped_value + ']').attr('checked', true);
			
			// Reset the value of this input
			$(this).val('');
			
			return false;
		}
	});
	
	// Click events
	$(manage_infos + ' p.bm-authorize a.to').click(function() {
		closeBubbles();
		sendSubscribe(xid, 'subscribed');
		
		return false;
	});
	
	$(manage_infos + ' p.bm-authorize a.from').click(function() {
		closeBubbles();
		sendSubscribe(xid, 'subscribe');
		
		return false;
	});
	
	$(manage_infos + ' p.bm-authorize a.unblock').click(function() {
		closeBubbles();
		
		// Update privacy settings
		pushPrivacy('block', ['jid'], [xid], ['allow'], ['1'], [false], [true], [true], [true], '', 'roster');
		$(path).removeClass('blocked');
		
		// Enable the "block" list
		changePrivacy('block', 'active');
		changePrivacy('block', 'default');
		
		// Send an available presence
		sendPresence(xid, 'available', getUserShow(), getUserStatus());
		
		return false;
	});
	
	$(manage_infos + ' p.bm-remove a.remove').click(function() {
		closeBubbles();
		sendRoster(xid, 'remove');
		
		return false;
	});
	
	$(manage_infos + ' p.bm-remove a.prohibit').click(function() {
		closeBubbles();
		sendSubscribe(xid, 'unsubscribed');
		
		return false;
	});
	
	$(manage_infos + ' p.bm-remove a.block').click(function() {
		closeBubbles();
		
		// Update privacy settings
		pushPrivacy('block', ['jid'], [xid], ['deny'], ['1'], [false], [true], [true], [true], '', 'roster');
		$(path).addClass('blocked');
		
		// Enable the "block" list
		changePrivacy('block', 'active');
		changePrivacy('block', 'default');
		
		// Send an unavailable presence
		sendPresence(xid, 'unavailable');
		
		// Remove the user presence
		for(var i = 0; i < sessionStorage.length; i++) {
			// Get the pointer values
			var current = sessionStorage.key(i);
			
			// If the pointer is on a stored presence
			if((explodeThis('_', current, 0) == 'presence') && (bareXID(explodeThis('_', current, 1)) == xid))
				sessionStorage.removeItem(current);
		}
		
		// Manage his new presence
		presenceFunnel(xid, hex_md5(xid));
		
		return false;
	});
	
	$(manage_infos + ' a.save').click(function() {
		// Send the item
		sendRoster(xid, '', trim($(rename).val()), thisBuddyGroups(xid));
		
		// Remove the buddy editor
		closeBubbles();
		
		return false;
	});
}

// Applies the buddy editing hover events
function applyBuddyHover(xid, hash, nick, subscription, groups, group_hash) {
	// Generate the values
	var bPath = '#buddy-list .' + group_hash + ' .buddy[data-xid=' + escape(xid) + ']';
	var iPath = bPath + ' .buddy-infos';
	
	// Apply the hover event
	$(bPath).hover(function() {
		// Another bubble exist
		if(exists('#buddy-list .buddy-infos'))
			return false;
		
		$(bPath).oneTime(200, function() {
			// Another bubble exist
			if(exists('#buddy-list .buddy-infos'))
				return false;
			
			// Add this bubble!
			showBubble(iPath);
			
			// Create the buddy infos DOM element
			$(bPath).append(
				'<div class="buddy-infos bubble removable">' + 
					'<div class="buddy-infos-subarrow talk-images"></div>' + 
					'<div class="buddy-infos-subitem">' + 
						'<div class="pep-infos">' + 
							'<p class="bi-status talk-images unavailable">' + _e("unknown") + '</p>' + 
							'<p class="bi-mood talk-images mood-four">' + _e("unknown") + '</p>' + 
							'<p class="bi-activity talk-images activity-exercising">' + _e("unknown") + '</p>' + 
							'<p class="bi-tune talk-images tune-note">' + _e("unknown") + '</p>' + 
							'<p class="bi-geoloc talk-images location-world">' + _e("unknown") + '</p>' + 
							'<p class="bi-view talk-images view-individual"><a href="#" class="profile">' + _e("Profile") + '</a> / <a href="#" class="channel">' + _e("Channel") + '</a> / <a href="#" class="commands">' + _e("Commands") + '</a></p>' + 
							'<p class="bi-edit talk-images edit-buddy"><a href="#">' + _e("Edit") + '</a></p>' + 
						'</div>' + 
					'</div>' + 
				'</div>'
			);
			
			// Sets the good position
			buddyInfosPosition(xid, group_hash);
			
			// Get the presence
			presenceFunnel(xid, hash);
			
			// Get the PEP infos
			displayAllPEP(xid);
			
			// Click events
			$(bPath + ' .bi-view a').click(function() {
				// Renitialize the buddy infos
				closeBubbles();
				
				// Profile
				if($(this).is('.profile'))
					openUserInfos(xid);
				
				// Channel
				else if($(this).is('.channel'))
					fromInfosMicroblog(xid, hash);
				
				// Command
				else if($(this).is('.commands'))
					retrieveAdHoc(xid);
				
				return false;
			});
			
			$(bPath + ' .bi-edit a').click(function() {
				buddyEdit(xid, nick, subscription, groups);
				
				return false;
			});
		});
	}, function() {
		if(!exists(iPath + ' .manage-infos'))
			closeBubbles();
		
		$(bPath).stopTime();
	});
}

// Sets the good buddy-infos position
function buddyInfosPosition(xid, group_hash) {
	// Paths
	var group = '#buddy-list .' + group_hash;
	var buddy = group + ' .buddy[data-xid=' + escape(xid) + ']';
	var buddy_infos = buddy + ' .buddy-infos';
	
	// Get the offset to define
	var offset = 3;
	
	if(isGateway(xid))
		offset = -8;
	
	// Process the position
	var top = $(buddy).position().top + offset;
	var left = $(buddy).width() - 10;
	
	// Apply the top position
	$(buddy_infos).css('top', top)
	              .css('left', left);
}

// Generates an array of the current groups of a buddy
function thisBuddyGroups(xid) {
	var path = '#buddy-list .buddy[data-xid=' + escape(xid) + '] ';
	var array = new Array();
	
	// Each checked checkboxes
	$(path + 'div.bm-choose input[type=checkbox]').filter(':checked').each(function() {
		array.push(unescape($(this).attr('data-group')));
	});
	
	// Entered input value (and not yet in the array)
	var value = trim($(path + 'p.bm-group input').val());
	
	if(value && !existArrayValue(array, value))
		array.push(value);
	
	return array;
}

// Adds a given contact to our roster
function addThisContact(xid, name) {
	logThis('Add this contact: ' + xid + ', as ' + name, 3);
	
	// Cut the resource of this XID
	xid = bareXID(xid);
	
	// If the form is complete
	if(xid) {
		// We send the subscription
		sendSubscribe(xid, 'subscribe');
		sendRoster(xid, '', name);
		
		// We hide the bubble
		closeBubbles();
	}
}

// Gets an array of all the groups in the roster
function getAllGroups() {
	var groups = new Array();
	
	$('#buddy-list .one-group').each(function() {
		var current = unescape($(this).attr('data-group'));
		
		if((current != _e("Unclassified")) && (current != _e("Gateways")))
			groups.push(current);
	});
	
	return groups.sort();
}

// Edits buddy informations
function buddyEdit(xid, nick, subscription, groups) {
	logThis('Buddy edit: ' + xid, 3);
	
	// Initialize
	var path = '#buddy-list .buddy[data-xid=' + escape(xid) + '] .';
	var html = '<div class="manage-infos">';
	
	// Get the privacy state
	var privacy_state = statusPrivacy('block', xid);
	var privacy_active = getDB('privacy-marker', 'available');
	
	// Get the group privacy state
	for(g in groups) {
		if((statusPrivacy('block', groups[g]) == 'deny') && (privacy_state != 'allow'))
			privacy_state = 'deny';
	}
	
	// The subscription with this buddy is not full
	if((subscription != 'both') || ((privacy_state == 'deny') && privacy_active)) {
		var authorize_links = '';
		html += '<p class="bm-authorize talk-images">';
		
		// Link to allow to see our status
		if((subscription == 'to') || (subscription == 'none'))
			authorize_links += '<a href="#" class="to">' + _e("Authorize") + '</a>';
		
		// Link to ask to see his/her status
		if((subscription == 'from') || (subscription == 'none')) {
			if(authorize_links)
				authorize_links += ' / ';
			
			authorize_links += '<a href="#" class="from">' + _e("Ask for authorization") + '</a>';
		}
		
		// Link to unblock this buddy
		if((privacy_state == 'deny') && privacy_active) {
			if(authorize_links)
				authorize_links += ' / ';
			
			html += '<a href="#" class="unblock">' + _e("Unblock") + '</a>';
		}
		
		html += authorize_links + '</p>';
	}
	
	// Complete the HTML code
	var remove_links = '';
	html += '<p class="bm-remove talk-images">';
	remove_links = '<a href="#" class="remove">' + _e("Remove") + '</a>';
	
	// This buddy is allowed to see our presence, we can show a "prohibit" link
	if((subscription == 'both') || (subscription == 'from'))
		remove_links += ' / <a href="#" class="prohibit">' + _e("Prohibit") + '</a>';
	
	// Complete the HTML code
	if((privacy_state != 'deny') && privacy_active) {
		if(remove_links)
			remove_links += ' / ';
		
		remove_links += '<a href="#" class="block">' + _e("Block") + '</a>';
	}
	
	// Complete the HTML code
	html += remove_links + 
		'</p>' + 
		'<p class="bm-rename talk-images"><label>' + _e("Rename") + '</label> <input type="text" value="' + encodeQuotes(nick) + '" /></p>';
	
	// Only show group tool if not a gateway
	if(!isGateway(xid))
		html += '<p class="bm-group talk-images"><label>' + _e("Groups") + '</label> <input type="text" /></p>' + 
			'<div class="bm-choose">' + 
				'<div></div>' + 
			'</div>';
	
	// Close the DOM element
	html += '<a href="#" class="save">' + _e("Save") + '</a>' + 
		'</div>';
	
	// We update the DOM elements
	$(path + 'pep-infos').replaceWith(html);
	
	// Gets all the existing groups
	var all_groups = getAllGroups();
	var all_groups_dom = '';
	
	for(a in all_groups) {
		// Current group
		var all_groups_current = all_groups[a];
		
		// Is the current group checked?
		var checked = '';
		
		if(existArrayValue(groups, all_groups_current))
			checked = ' checked="true"';
		
		// Add the current group HTML
		all_groups_dom += '<label><input type="checkbox" data-group="' + escape(all_groups_current) + '"' + checked + ' /><span>' + all_groups_current.htmlEnc() + '</span></label>';
	}
	
	// Prepend this in the DOM
	var bm_choose = path + 'manage-infos div.bm-choose';
	
	$(bm_choose).prepend(all_groups_dom);
	
	// Apply the editing input events
	applyBuddyInput(xid);
}

// Updates the roster items
function sendRoster(xid, subscription, name, group) {
	// We send the new buddy name
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var iqQuery = iq.setQuery(NS_ROSTER);
	var item = iqQuery.appendChild(iq.buildNode('item', {'xmlns': NS_ROSTER, 'jid': xid}));
	
	// Any subscription?
	if(subscription)
		item.setAttribute('subscription', subscription);
	
	// Any name?
	if(name)
		item.setAttribute('name', name);
	
	// Any group?
	if(group && group.length) {
		for(i in group)
			item.appendChild(iq.buildNode('group', {'xmlns': NS_ROSTER}, group[i]));
	}
	
	con.send(iq);
	
	logThis('Roster item sent: ' + xid, 3);
}

// Adapts the roster height, depending of the window size
function adaptRoster() {
	// Process the new height
	var new_height = $('#left-content').height() - $('#my-infos').height() - 97;
	
	// New height too small
	if(new_height < 211)
		new_height = 211;
	
	// Apply the new height
	$('#buddy-list .content').css('height', new_height);
}

// Gets all the buddies in our roster
function getAllBuddies() {
	var buddies = new Array();
	
	$('#buddy-list .buddy').each(function() {
		var xid = unescape($(this).attr('data-xid'));
		
		if(xid)
			buddies.push(xid);
	});
	
	return buddies;
}

// Gets the user gateways
function getGateways() {
	// New array
	var gateways = new Array();
	var buddies = getAllBuddies();
	
	// Get the gateways
	for(c in buddies) {
		if(isGateway(buddies[c]))
			gateways.push(buddies[c]);
	}
	
	return gateways;
}

// Define a global var for buddy list all buddies displayed
var BLIST_ALL = false;

// Plugin launcher
function launchRoster() {
	// Filtering tool
	var iFilter = $('#buddy-list .filter input');
	var aFilter = $('#buddy-list .filter a');
	
	iFilter.placeholder()
	
	.blur(function() {
		// Nothing is entered, put the placeholder instead
		if(!trim($(this).val()))
			aFilter.hide();
		else
			aFilter.show();
	})
	
	.keyup(function(e) {
		funnelFilterBuddySearch(e.keyCode);
	});
	
	aFilter.click(function() {
		// Reset the input
		$(this).hide();
		iFilter.val('');
		iFilter.placeholder();
		
		// Security: show all the groups, empty or not
		$('#buddy-list .one-group').show();
		
		// Reset the filtering tool
		resetFilterBuddySearch();
		
		return false;
	});
	
	// When the user click on the add button, show the contact adding tool
	$('#buddy-list .foot .add').click(function() {
		// Yet displayed?
		if(exists('#buddy-conf-add'))
			return closeBubbles();
		
		// Add the bubble
		showBubble('#buddy-conf-add');
		
		// Append the content
		$('#buddy-list .buddy-list-add').append(
			'<div id="buddy-conf-add" class="buddy-conf-item bubble removable">' + 
				'<div class="buddy-conf-subarrow talk-images"></div>' + 
				
				'<div class="buddy-conf-subitem">' + 
					'<p class="buddy-conf-p">' + _e("Add a friend") +  '</p>' + 
					
					'<label><span>' + _e("Address") +  '</span><input type="text" class="buddy-conf-input add-contact-jid" required="" /></label>' + 
					'<label><span>' + _e("Name") +  '</span><input type="text" class="buddy-conf-input add-contact-name" /></label>' +  
					'<label>' + 
						'<span>' + _e("Gateway") +  '</span>' + 
						'<select class="buddy-conf-select add-contact-gateway">' + 
							'<option value="none" selected="">' + _e("None") +  '</option>' + 
						'</select>' + 
					'</label>' +  
					'<span class="add-contact-name-get">' + _e("Getting the name...") + '</span>' + 
					
					'<p class="buddy-conf-text">' + 
						'<a href="#" class="buddy-conf-add-search">' + _e("Search a friend") +  '</a>' + 
					'</p>' + 
				'</div>' + 
			'</div>'
		);
		
		// Add the gateways
		var gateways = getGateways();
		
		// Any gateway?
		if(gateways.length) {
			// Append the gateways
			for(i in gateways)
				$('.add-contact-gateway').append('<option value="' + escape(gateways[i]) + '">' + gateways[i].htmlEnc() +  '</option>');
			
			// Show the gateway selector
			$('.add-contact-gateway').parent().show();
		}
		
		// No gateway?
		else
			$('.add-contact-gateway').parent().hide();
		
		// Blur event on the add contact input
		$('.add-contact-jid').blur(function() {
			// Read the value
			var value = trim($(this).val());
			
			// Try to catch the buddy name
			if(value && !trim($('.add-contact-name').val()) && ($('.add-contact-gateway').val() == 'none')) {
				// User XID
				var xid = generateXID(value, 'chat');
				
				// Notice for the user
				$('.add-contact-name-get').attr('data-for', escape(xid)).show();
				
				// Request the user vCard
				getAddUserName(xid);
			}
		});
		
		// When a key is pressed...
		$('#buddy-conf-add input, #buddy-conf-add select').keyup(function(e) {
			// Enter : continue
			if(e.keyCode == 13) {
				// Get the values
				var xid = trim($('.add-contact-jid').val());
				var name = trim($('.add-contact-name').val());
				var gateway = unescape($('.add-contact-gateway').val());
				
				// Generate the XID to add
				if((gateway != 'none') && xid)
					xid = xid.replace(/@/g, '%') + '@' + gateway;
				else
					xid = generateXID(xid, 'chat');
				
				// Submit the form
				if(xid && (xid != getXID()))
					addThisContact(xid, name);
				else
					$(document).oneTime(10, function() {
						$('.add-contact-jid').addClass('please-complete').focus();
					});
				
				return false;
			}
			
			// Escape : quit
			if(e.keyCode == 27)
				closeBubbles();
		});
		
		// Click event on search link
		$('.buddy-conf-add-search').click(function() {
			closeBubbles();
			return openDirectory();
		});
		
		// Focus on the input
		$(document).oneTime(10, function() {
			$('.add-contact-jid').focus();
		});
		
		return false;
	});
	
	// When the user click on the join button, show the chat joining tool
	$('#buddy-list .foot .join').click(function() {
		// Yet displayed?
		if(exists('#buddy-conf-join'))
			return closeBubbles();
		
		// Add the bubble
		showBubble('#buddy-conf-join');
		
		// Append the content
		$('#buddy-list .buddy-list-join').append(
			'<div id="buddy-conf-join" class="buddy-conf-item bubble removable">' + 
				'<div class="buddy-conf-subarrow talk-images"></div>' + 
				
				'<div class="buddy-conf-subitem search">' + 
					'<p class="buddy-conf-p" style="margin-bottom: 0;">' + _e("Join a chat") +  '</p>' + 
					
					'<input type="text" class="buddy-conf-input join-jid" required="" />' + 
					'<select class="buddy-conf-select buddy-conf-join-select join-type">' + 
						'<option value="chat" selected="">' + _e("Chat") +  '</option>' + 
						'<option value="groupchat">' + _e("Groupchat") +  '</option>' + 
					'</select>' + 
				'</div>' + 
			'</div>'
		);
		
		// Input vars
		var destination = '#buddy-conf-join .search';
		var dHovered = destination + ' ul li.hovered:first';
		
		// When a key is pressed...
		$('#buddy-conf-join input, #buddy-conf-join select').keyup(function(e) {
			// Enter: continue
			if(e.keyCode == 13) {
				// Select something from the search
				if(exists(dHovered))
					addBuddySearch(destination, $(dHovered).attr('data-xid'));
				
				// Join something
				else {
					var xid = trim($('.join-jid').val());
					var type = $('.buddy-conf-join-select').val();
					
					if(xid && type) {
						// Generate a correct XID
						xid = generateXID(xid, type);
						
						// Not me
						if(xid != getXID()) {
							// Update some things
							$('.join-jid').removeClass('please-complete');
							closeBubbles();
							
							// Create a new chat
							checkChatCreate(xid, type);
						}
						
						else
							$('.join-jid').addClass('please-complete');
					}
					
					else
						$('.join-jid').addClass('please-complete');
				}
				
				return false;
			}
			
			// Escape: quit
			else if(e.keyCode == 27)
				closeBubbles();
			
			// Buddy search?
			else if($('.buddy-conf-join-select').val() == 'chat') {
				// New buddy search
				if((e.keyCode != 40) && (e.keyCode != 38))
					createBuddySearch(destination);
				
				// Navigating with keyboard in the results
				arrowsBuddySearch(e, destination);
			}
		});
		
		// Buddy search lost focus
		$('#buddy-conf-join input').blur(function() {
			if(!$(destination + ' ul').attr('mouse-hover'))
				resetBuddySearch(destination);
		});
		
		// Re-focus on the text input
		$('#buddy-conf-join select').change(function() {
			$(document).oneTime(10, function() {
				$('#buddy-conf-join input').focus();
			});
		});
		
		// We focus on the input
		$(document).oneTime(10, function() {
			$('#buddy-conf-join .join-jid').focus();
		});
		
		return false;
	});
	
	// When the user click on the groupchat button, show the groupchat menu
	$('#buddy-list .foot .groupchat').click(function() {
		// Yet displayed?
		if(exists('#buddy-conf-groupchat'))
			return closeBubbles();
		
		// Add the bubble
		showBubble('#buddy-conf-groupchat');
		
		// Append the content
		$('#buddy-list .buddy-list-groupchat').append(
			'<div id="buddy-conf-groupchat" class="buddy-conf-item bubble removable">' + 
				'<div class="buddy-conf-subarrow talk-images"></div>' + 
				
				'<div class="buddy-conf-subitem">' + 
					'<p class="buddy-conf-p">' + _e("Your groupchats") +  '</p>' + 
					
					'<select name="groupchat-join" class="buddy-conf-select buddy-conf-groupchat-select"></select>' + 
					
					'<p class="buddy-conf-text">' + 
						'- <a href="#" class="buddy-conf-groupchat-edit">' + _e("Manage your favorite groupchats") +  '</a>' + 
					'</p>' + 
				'</div>' + 
			'</div>'
		);
		
		// When the user wants to edit his groupchat favorites
		$('.buddy-conf-groupchat-edit').click(function() {
			openFavorites();
			closeBubbles();
			
			return false;
		});
		
		// Change event
		$('.buddy-conf-groupchat-select').change(function() {
			var groupchat = trim($(this).val());
			
			if(groupchat != 'none') {
				// We hide the bubble
				closeBubbles();
				
				// Create the chat
				checkChatCreate(groupchat, 'groupchat');
				
				// We reset the select value
				$(this).val('none');
			}
		});
		
		// Load the favorites
		loadFavorites();
		
		return false;
	});
	
	// When the user click on the more button, show the more menu
	$('#buddy-list .foot .more').click(function() {
		// Yet displayed?
		if(exists('#buddy-conf-more'))
			return closeBubbles();
		
		// Add the bubble
		showBubble('#buddy-conf-more');
		
		// Append the content
		$('#buddy-list .buddy-list-more').append(
			'<div id="buddy-conf-more" class="buddy-conf-item bubble removable">' + 
				'<div class="buddy-conf-subarrow talk-images"></div>' + 
				
				'<div class="buddy-conf-subitem">' + 
					'<p class="buddy-conf-p">' + _e("More stuff") +  '</p>' + 
					
					'<p class="buddy-conf-text">' + 
						'- <a href="#" class="buddy-conf-more-display-unavailable">' + _e("Show all friends") +  '</a>' + 
						'<a href="#" class="buddy-conf-more-display-available">' + _e("Only show connected friends") +  '</a>' + 
					'</p>' + 
					
					'<p class="buddy-conf-text archives-hidable">' + 
						'- <a href="#" class="buddy-conf-more-archives">' + _e("Message archives") +  '</a>' + 
					'</p>' + 
					
					'<p class="buddy-conf-text privacy-hidable">' + 
						'- <a href="#" class="buddy-conf-more-privacy">' + _e("Privacy") +  '</a>' + 
					'</p>' + 
					
					'<p class="buddy-conf-text">' + 
						'- <a href="#" class="buddy-conf-more-service-disco">' + _e("Service discovery") +  '</a>' + 
					'</p>' + 
					
					'<p class="buddy-conf-text commands-hidable"">' + 
						'- <a href="#" class="buddy-conf-more-commands">' + _e("Commands") +  '</a>' + 
					'</p>' + 
				'</div>' + 
			'</div>'
		);
		
		// Close bubble when link clicked
		$('#buddy-conf-more a').click(function() {
			closeBubbles();
		});
		
		// When the user wants to display all his buddies
		$('.buddy-conf-more-display-unavailable').click(function() {
			showAllBuddies('roster');
			
			return false;
		});
		
		// When the user wants to display only online buddies
		$('.buddy-conf-more-display-available').click(function() {
			showOnlineBuddies('roster');
			
			return false;
		});
		
		// When the user click on the archives link
		$('.buddy-conf-more-archives').click(openArchives);
		
		// When the user click on the privacy link
		$('.buddy-conf-more-privacy').click(openPrivacy);
		
		// When the user click on the service discovery link
		$('.buddy-conf-more-service-disco').click(openDiscovery);
		
		// When the user click on the command link
		$('.buddy-conf-more-commands').click(function() {
			serverAdHoc(con.domain);
			
			return false;
		});
		
		// Manage the displayed links
		if(BLIST_ALL) {
			$('.buddy-conf-more-display-unavailable').hide();
			$('.buddy-conf-more-display-available').show();
		}
		
		if(enabledArchives() || enabledArchives('auto') || enabledArchives('manual') || enabledArchives('manage'))
			$('.buddy-conf-more-archives').parent().show();
		
		if(enabledCommands())
			$('.buddy-conf-more-commands').parent().show();
		
		if(getDB('privacy-marker', 'available'))
			$('.buddy-conf-more-privacy').parent().show();
		
		return false;
	});
	
	// When the user scrolls the buddy list
	$('#buddy-list .content').scroll(function() {
		// Close the opened buddy infos bubble
		closeBubbles();
	});
}

// Window resize event handler
$(window).resize(adaptRoster);
