/*

Jappix - An open social platform
These are the privacy JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Opens the privacy popup
function openPrivacy() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Privacy") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="privacy-head">' + 
			'<div class="list-left">' + 
				'<span>' + _e("Choose") + '</span>' + 
				'<select disabled=""></select>' + 
				'<a href="#" class="list-remove one-button talk-images" title="' + _e("Remove") + '"></a>' + 
			'</div>' + 
			
			'<div class="list-center"></div>' + 
			
			'<div class="list-right">' + 
				'<span>' + _e("Add") + '</span>' + 
				'<input type="text" placeholder="' + _e("List name") + '" />' + 
			'</div>' + 
		'</div>' + 
		
		'<div class="privacy-item">' + 
			'<span>' + _e("Item") + '</span>' + 
			'<select disabled=""></select>' + 
			'<a href="#" class="item-add one-button talk-images" title="' + _e("Add") + '"></a>' + 
			'<a href="#" class="item-remove one-button talk-images" title="' + _e("Remove") + '"></a>' + 
			'<a href="#" class="item-save one-button talk-images">' + _e("Save") + '</a>' + 
			
			'<div class="clear"></div>' + 
		'</div>' + 
		
		'<div class="privacy-form">' + 
			'<div class="privacy-first">' + 
				'<label><input type="radio" name="action" value="allow" disabled="" />' + _e("Allow") + '</label>' + 
				'<label><input type="radio" name="action" value="deny" disabled="" />' + _e("Deny") + '</label>' + 
			'</div>' + 
			
			'<div class="privacy-second">' + 
				'<label><input type="radio" name="type" value="jid" disabled="" />' + _e("Address") + '</label>' + 
				'<input type="text" name="jid" disabled="" />' + 
				
				'<label><input type="radio" name="type" value="group" disabled="" />' + _e("Group") + '</label>' + 
				'<select name="group" disabled="">' + groupsToHtmlPrivacy() + '</select>' + 
				
				'<label><input type="radio" name="type" value="subscription" disabled="" />' + _e("Subscription") + '</label>' + 
				'<select name="subscription" disabled="">' + 
					'<option value="none">' + _e("None") + '</option>' + 
					'<option value="both">' + _e("Both") + '</option>' + 
					'<option value="from">' + _e("From") + '</option>' + 
					'<option value="to">' + _e("To") + '</option>' + 
				'</select>' + 
				
				'<label><input type="radio" name="type" value="everybody" disabled="" />' + _e("Everybody") + '</label>' + 
			'</div>' + 
			
			'<div class="privacy-third">' + 
				'<label><input type="checkbox" name="send-messages" disabled="" />' + _e("Send messages") + '</label>' + 
				'<label><input type="checkbox" name="send-queries" disabled="" />' + _e("Send queries") + '</label>' + 
				'<label><input type="checkbox" name="see-status" disabled="" />' + _e("See my status") + '</label>' + 
				'<label><input type="checkbox" name="send-status" disabled="" />' + _e("Send his/her status") + '</label>' + 
				'<label><input type="checkbox" name="everything" disabled="" />' + _e("Everything") + '</label>' + 
			'</div>' + 
			
			'<div class="clear"></div>' + 
		'</div>' + 
		
		'<div class="privacy-active">' + 
			'<label>' + _e("Order") + '<input type="text" name="order" value="1" disabled="" /></label>' + 
			
			'<div class="privacy-active-elements">' + 
				'<label><input type="checkbox" name="active" disabled="" />' + _e("Active for this session") + '</label>' + 
				'<label><input type="checkbox" name="default" disabled="" />' + _e("Always active") + '</label>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('privacy', html);
	
	// Associate the events
	launchPrivacy();
	
	// Display the available privacy lists
	displayListsPrivacy();
	
	// Get the first list items
	displayItemsPrivacy();
	
	return false;
}

// Quits the privacy popup
function closePrivacy() {
	// Destroy the popup
	destroyPopup('privacy');
	
	return false;
}

// Sets the received state for privacy block list
function receivedPrivacy() {
	// Store marker
	setDB('privacy-marker', 'available', 'true');
	
	// Show privacy elements
	$('.privacy-hidable').show();
}

// Gets available privacy lists
function listPrivacy() {
	// Build query
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	iq.setQuery(NS_PRIVACY);
	
	con.send(iq, handleListPrivacy);
	
	logThis('Getting available privacy list(s)...');
}

// Handles available privacy lists
function handleListPrivacy(iq) {
	// Error?
	if(iq.getType() == 'error')
		return logThis('Privacy lists not supported!', 2);
	
	// Get IQ query content
	var iqQuery = iq.getQuery();
	
	// Save the content
	setDB('privacy-lists', 'available', xmlToString(iqQuery));
	
	// Any block list?
	if($(iqQuery).find('list[name=block]').size()) {
		// Not the default one?
		if(!$(iqQuery).find('default[name=block]').size())
			changePrivacy('block', 'default');
		else
			setDB('privacy-marker', 'default', 'block');
		
		// Not the active one?
		if(!$(iqQuery).find('active[name=block]').size())
			changePrivacy('block', 'active');
		else
			setDB('privacy-marker', 'active', 'block');
		
		// Get the block list rules
		getPrivacy('block');
	}
	
	// Apply the received marker here
	else
		receivedPrivacy();
	
	logThis('Got available privacy list(s).', 3);
}

// Gets privacy lists
function getPrivacy(list) {
	// Build query
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	// Privacy query
	var iqQuery = iq.setQuery(NS_PRIVACY);
	iqQuery.appendChild(iq.buildNode('list', {'xmlns': NS_PRIVACY, 'name': list}));
	
	con.send(iq, handleGetPrivacy);
	
	// Must show the wait item?
	if(exists('#privacy'))
		$('#privacy .wait').show();
	
	logThis('Getting privacy list(s): ' + list);
}

// Handles privacy lists
function handleGetPrivacy(iq) {
	// Apply a "received" marker
	receivedPrivacy();
	
	// Store the data for each list
	$(iq.getQuery()).find('list').each(function() {
		// Read list name
		var list_name = $(this).attr('name');
		
		// Store list content
		setDB('privacy', list_name, xmlToString(this));
		
		// Is this a block list?
		if(list_name == 'block') {
			// Reset buddies
			$('#buddy-list .buddy').removeClass('blocked');
			
			// XID types
			$(this).find('item[action=deny][type=jid]').each(function() {
				$('#buddy-list .buddy[data-xid=' + escape($(this).attr('value')) + ']').addClass('blocked');
			});
			
			// Group types
			$(this).find('item[action=deny][type=group]').each(function() {
				$('#buddy-list .group' + hex_md5($(this).attr('value')) + ' .buddy').addClass('blocked');
			});
		}
	});
	
	// Must display it to the popup?
	if(exists('#privacy')) {
		displayItemsPrivacy();
		
		$('#privacy .wait').hide();
	}
	
	logThis('Got privacy list(s).', 3);
}

// Sets a privacy list
function setPrivacy(list, types, values, actions, orders, presence_in, presence_out, msg, iq_p) {
	// Build query
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// Privacy query
	var iqQuery = iq.setQuery(NS_PRIVACY);
	var iqList = iqQuery.appendChild(iq.buildNode('list', {'xmlns': NS_PRIVACY, 'name': list}));
	
	// Build the item elements
	if(types && types.length) {
		for(var i = 0; i < types.length; i++) {
			// Item element
			var iqItem = iqList.appendChild(iq.buildNode('item', {'xmlns': NS_PRIVACY}));
			
			// Item attributes
			if(types[i])
				iqItem.setAttribute('type', types[i]);
			if(values[i])
				iqItem.setAttribute('value', values[i]);
			if(actions[i])
				iqItem.setAttribute('action', actions[i]);
			if(orders[i])
				iqItem.setAttribute('order', orders[i]);
			
			// Child elements
			if(presence_in[i])
				iqItem.appendChild(iq.buildNode('presence-in', {'xmlns': NS_PRIVACY}));
			if(presence_out[i])
				iqItem.appendChild(iq.buildNode('presence-out', {'xmlns': NS_PRIVACY}));
			if(msg[i])
				iqItem.appendChild(iq.buildNode('message', {'xmlns': NS_PRIVACY}));
			if(iq_p[i])
				iqItem.appendChild(iq.buildNode('iq', {'xmlns': NS_PRIVACY}));
		}
	}
	
	con.send(iq);
	
	logThis('Sending privacy list: ' + list);
}

// Push a privacy list item to a list
function pushPrivacy(list, type, value, action, order, presence_in, presence_out, msg, iq_p, hash, special_action) {
	// Read the stored elements (to add them)
	var stored = XMLFromString(getDB('privacy', list));
	
	// Read the first value
	var first_val = value[0];
	
	// Must remove the given value?
	if(special_action == 'remove') {
		type = [];
		value = [];
		action = [];
		order = [];
		presence_in = [];
		presence_out = [];
		iq_p = [];
	}
	
	// Serialize them to an array
	$(stored).find('item').each(function() {
		// Attributes
		var c_type = $(this).attr('type');
		var c_value = $(this).attr('value');
		var c_action = $(this).attr('action');
		var c_order = $(this).attr('order');
		
		// Generate hash
		var c_hash = hex_md5(c_type + c_value + c_action + c_order);
		
		// Do not push it twice!
		if(((c_hash != hash) && (special_action != 'roster')) || ((first_val != c_value) && (special_action == 'roster'))) {
			if(!c_type)
				c_type = '';
			if(!c_value)
				c_value = '';
			if(!c_action)
				c_action = '';
			if(!c_order)
				c_order = '';
			
			type.push(c_type);
			value.push(c_value);
			action.push(c_action);
			order.push(c_order);
			
			// Child elements
			if($(this).find('presence-in').size())
				presence_in.push(true);
			else
				presence_in.push(false);
			
			if($(this).find('presence-out').size())
				presence_out.push(true);
			else
				presence_out.push(false);
			
			if($(this).find('message').size())
				msg.push(true);
			else
				msg.push(false);
			
			if($(this).find('iq').size())
				iq_p.push(true);
			else
				iq_p.push(false);
		}
	});
	
	// Send it!
	setPrivacy(list, type, value, action, order, presence_in, presence_out, msg, iq_p);
}

// Change a privacy list status
function changePrivacy(list, status) {
	// Yet sent?
	if(getDB('privacy-marker', status) == list)
		return;
	
	// Write a marker
	setDB('privacy-marker', status, list);
	
	// Build query
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// Privacy query
	var iqQuery = iq.setQuery(NS_PRIVACY);
	var iqStatus = iqQuery.appendChild(iq.buildNode(status, {'xmlns': NS_PRIVACY}));
	
	// Can add a "name" attribute?
	if(list)
		iqStatus.setAttribute('name', list);
	
	con.send(iq);
	
	logThis('Changing privacy list status: ' + list + ' to: ' + status);
}

// Checks the privacy status (action) of a value
function statusPrivacy(list, value) {
	return $(XMLFromString(getDB('privacy', list))).find('item[value=' + value + ']').attr('action');
}

// Converts the groups array into a <option /> string
function groupsToHtmlPrivacy() {
	var groups = getAllGroups();
	var html = '';
	
	// Generate HTML
	for(i in groups) {
		html += '<option value="' + encodeQuotes(groups[i]) +'">' + groups[i].htmlEnc() + '</option>';
	}
	
	return html;
}

// Displays the privacy lists
function displayListsPrivacy() {
	// Initialize
	var code = '';
	var select = $('#privacy .privacy-head .list-left select');
	var data = XMLFromString(getDB('privacy-lists', 'available'));
	
	// Parse the XML data!
	$(data).find('list').each(function() {
		var list_name = $(this).attr('name');
		
		if(list_name)
			code += '<option value="' + encodeQuotes(list_name) + '">' + list_name.htmlEnc() + '</option>';
	});
	
	// Apply HTML code
	select.html(code);
	
	// Not empty?
	if(code)
		select.removeAttr('disabled');
	else
		select.attr('disabled', true);
	
	return true;
}

// Displays the privacy items for a list
function displayItemsPrivacy() {
	// Reset the form
	clearFormPrivacy();
	disableFormPrivacy();
	
	// Initialize
	var code = '';
	var select = $('#privacy .privacy-item select');
	var list = $('#privacy .privacy-head .list-left select').val();
	
	// Reset the item select
	select.html('');
	
	// No list?
	if(!list)
		return false;
	
	// Reset the list status
	$('#privacy .privacy-active input[type=checkbox]').removeAttr('checked');
	
	// Display the list status
	var status = ['active', 'default'];
	
	for(s in status) {
		if(getDB('privacy-marker', status[s]) == list)
			$('#privacy .privacy-active input[name=' + status[s] + ']').attr('checked', true);
	}
	
	// Try to read the stored items
	var items = XMLFromString(getDB('privacy', list));
	
	// Must retrieve the data?
	if(!items) {
		select.attr('disabled', true);
		
		return getPrivacy(list);
	}
	
	else
		select.removeAttr('disabled');
	
	// Parse the XML data!
	$(items).find('item').each(function() {
		// Read attributes
		var item_type = $(this).attr('type');
		var item_value = $(this).attr('value');
		var item_action = $(this).attr('action');
		var item_order = $(this).attr('order');
		
		// Generate hash
		var item_hash = hex_md5(item_type + item_value + item_action + item_order);
		
		// Read sub-elements
		var item_presencein = $(this).find('presence-in').size();
		var item_presenceout = $(this).find('presence-out').size();
		var item_message = $(this).find('message').size();
		var item_iq = $(this).find('iq').size();
		
		// Apply default values (if missing)
		if(!item_type)
			item_type = '';
		if(!item_value)
			item_value = '';
		if(!item_action)
			item_action = 'allow';
		if(!item_order)
			item_order = '1';
		
		// Apply sub-elements values
		if(item_presencein)
			item_presencein = 'true';
		else
			item_presencein = 'false';
		
		if(item_presenceout)
			item_presenceout = 'true';
		else
			item_presenceout = 'false';
		
		if(item_message)
			item_message = 'true';
		else
			item_message = 'false';
		
		if(item_iq)
			item_iq = 'true';
		else
			item_iq = 'false';
		
		// Generate item description
		var desc = '';
		var desc_arr = [item_type, item_value, item_action, item_order];
		
		for(d in desc_arr) {
			// Nothing to display?
			if(!desc_arr[d])
				continue;
			
			if(desc)
				desc += ' - ';
			
			desc += desc_arr[d];
		}
		
		// Add the select option
		code += '<option data-type="' + encodeQuotes(item_type) + '" data-value="' + encodeQuotes(item_value) + '" data-action="' + encodeQuotes(item_action) + '" data-order="' + encodeQuotes(item_order) + '" data-presence_in="' + encodeQuotes(item_presencein) + '" data-presence_out="' + encodeQuotes(item_presenceout) + '" data-message="' + encodeQuotes(item_message) + '" data-iq="' + encodeQuotes(item_iq) + '" data-hash="' + encodeQuotes(item_hash) + '">' + 
				desc + 
			'</option>';
	});
	
	// Append the code
	select.append(code);
	
	// Display the first item form
	var first_item = select.find('option:first');
	displayFormPrivacy(
			   first_item.attr('data-type'),
			   first_item.attr('data-value'),
			   first_item.attr('data-action'),
			   first_item.attr('data-order'),
			   first_item.attr('data-presence_in'),
			   first_item.attr('data-presence_out'),
			   first_item.attr('data-message'),
			   first_item.attr('data-iq')
			  );
	
	return true;
}

// Displays the privacy form for an item
function displayFormPrivacy(type, value, action, order, presence_in, presence_out, message, iq) {
	// Reset the form
	clearFormPrivacy();
	
	// Apply the action
	$('#privacy .privacy-first input[name=action][value=' + action + ']').attr('checked', true);
	
	// Apply the type & value
	var privacy_second = '#privacy .privacy-second';
	var privacy_type = privacy_second + ' input[name=type]';
	var type_check, value_input;
	
	switch(type) {
		case 'jid':
			type_check = privacy_type + '[value=jid]';
			value_input = privacy_second + ' input[type=text][name=jid]';
			
			break;
		
		case 'group':
			type_check = privacy_type + '[value=group]';
			value_input = privacy_second + ' select[name=group]';
			
			break;
		
		case 'subscription':
			type_check = privacy_type + '[value=subscription]';
			value_input = privacy_second + ' select[name=subscription]';
			
			break;
		
		default:
			type_check = privacy_type + '[value=everybody]';
			
			break;
	}
	
	// Check the target
	$(type_check).attr('checked', true);
	
	// Can apply a value?
	if(value_input)
		$(value_input).val(value);
	
	// Apply the things to do
	var privacy_do = '#privacy .privacy-third input[type=checkbox]';
	
	if(presence_in == 'true')
		$(privacy_do + '[name=send-status]').attr('checked', true);
	if(presence_out == 'true')
		$(privacy_do + '[name=see-status]').attr('checked', true);
	if(message == 'true')
		$(privacy_do + '[name=send-messages]').attr('checked', true);
	if(iq == 'true')
		$(privacy_do + '[name=send-queries]').attr('checked', true);
	
	if(!$(privacy_do).filter(':checked').size())
		$(privacy_do + '[name=everything]').attr('checked', true);
	
	// Apply the order
	$('#privacy .privacy-active input[name=order]').val(order);
	
	// Enable the forms
	$('#privacy .privacy-form input, #privacy .privacy-form select, #privacy .privacy-active input').removeAttr('disabled');
}

// Clears the privacy list form
function clearFormPrivacy() {
	// Uncheck checkboxes & radio inputs
	$('#privacy .privacy-form input[type=checkbox], #privacy .privacy-form input[type=radio]').removeAttr('checked');
	
	// Reset select
	$('#privacy .privacy-form select option').removeAttr('selected');
	$('#privacy .privacy-form select option:first').attr('selected', true);
	
	// Reset text input
	$('#privacy .privacy-form input[type=text]').val('');
	
	// Reset order input
	$('#privacy .privacy-active input[name=order]').val('1');
}

// Disables the privacy list form
function disableFormPrivacy() {
	$('#privacy .privacy-form input, #privacy .privacy-form select, #privacy .privacy-active input').attr('disabled', true);
}

// Enables the privacy list form
function enableFormPrivacy(rank) {
	$('#privacy .privacy-' + rank + ' input, #privacy .privacy-' + rank + ' select').removeAttr('disabled');
}

// Plugin launcher
function launchPrivacy() {
	// Click events
	$('#privacy .bottom .finish').click(closePrivacy);
	
	// Placeholder events
	$('#privacy input[placeholder]').placeholder();
	
	// Form events
	$('#privacy .privacy-head a.list-remove').click(function() {
		// Get list name
		var list = $('#privacy .privacy-head .list-left select').val();
		
		// No value?
		if(!list)
			return false;
		
		// Remove it from popup
		$('#privacy .privacy-head .list-left select option[value=' + list + ']').remove();
		
		// Nothing remaining?
		if(!exists('#privacy .privacy-head .list-left select option'))
			$('#privacy .privacy-head .list-left select option').attr('disabled', true);
		
		// Empty the item select
		$('#privacy .privacy-item select').attr('disabled', true).html('');
		
		// Disable this list before removing it
		var status = ['active', 'default'];
		
		for(s in status) {
			if(getDB('privacy-marker', status[s]) == list)
				changePrivacy('', status[s]);
		}
		
		// Remove from server
		setPrivacy(list);
		
		// Reset the form
		clearFormPrivacy();
		disableFormPrivacy();
		
		return false;
	});
	
	$('#privacy .privacy-head .list-right input').keyup(function(e) {
		// Not enter?
		if(e.keyCode != 13)
			return;
		
		// Get list name
		var list = $('#privacy .privacy-head .list-right input').val();
		var select = '#privacy .privacy-head .list-left select';
		var existed = true;
		
		// Create the new element
		if(!exists(select + ' option[value=' + list + ']')) {
			// Marker
			existed = false;
			
			// Create a new option
			$(select).append('<option value="' + encodeQuotes(list) + '">' + list.htmlEnc() + '</option>');
			
			// Reset the item select
			$('#privacy .privacy-item select').attr('disabled', true).html('');
		}
		
		// Change the select value & enable it
		$(select).val(list).removeAttr('disabled');
		
		// Reset its value
		$(this).val('');
		
		// Reset the form
		clearFormPrivacy();
		disableFormPrivacy();
		
		// Must reload the list items?
		if(existed) {
			displayItemsPrivacy();
			$('#privacy .privacy-item select').removeAttr('disabled');
		}
	});
	
	$('#privacy .privacy-head .list-left select').change(displayItemsPrivacy);
	
	$('#privacy .privacy-item select').change(function() {
		// Get the selected item
		var item = $(this).find('option:selected');
		
		// Display the data!
		displayFormPrivacy(
			   item.attr('data-type'),
			   item.attr('data-value'),
			   item.attr('data-action'),
			   item.attr('data-order'),
			   item.attr('data-presence_in'),
			   item.attr('data-presence_out'),
			   item.attr('data-message'),
			   item.attr('data-iq')
			  );
	});
	
	$('#privacy .privacy-item a.item-add').click(function() {
		// Cannot add anything?
		if(!exists('#privacy .privacy-head .list-left select option:selected'))
			return false;
		
		// Disable item select
		$('#privacy .privacy-item select').attr('disabled', true);
		
		// Reset the form
		clearFormPrivacy();
		disableFormPrivacy();
		
		// Enable first form item
		enableFormPrivacy('first');
		enableFormPrivacy('active');
		
		return false;
	});
	
	$('#privacy .privacy-item a.item-remove').click(function() {
		// Cannot add anything?
		if(!exists('#privacy .privacy-head .list-left select option:selected'))
			return false;
		
		// Get values
		var list = $('#privacy .privacy-head .list-left select').val();
		var selected = $('#privacy .privacy-item select option:selected');
		var item = selected.attr('data-value');
		var hash = selected.attr('data-hash');
		
		// Remove it from popup
		$('#privacy .privacy-item select option:selected').remove();
		
		// No more items in this list?
		if(!exists('#privacy .privacy-item select option')) {
			// Disable this select
			$('#privacy .privacy-item select').attr('disabled', true);
			
			// Remove the privacy list select item
			$('#privacy .privacy-head .list-left select option[value=' + list + ']').remove();
			
			// No more privacy lists?
			if(!exists('#privacy .privacy-head .list-left select option'))
				$('#privacy .privacy-head .list-left select').attr('disabled', true);
			
			// Disable this list before removing it
			var status = ['active', 'default'];
			
			for(s in status) {
				if(getDB('privacy-marker', status[s]) == list)
					changePrivacy('', status[s]);
			}
		}
		
		// Synchronize it with server
		pushPrivacy(list, [], [item], [], [], [], [], [], [], hash, 'remove');
		
		// Reset the form
		clearFormPrivacy();
		disableFormPrivacy();
		
		return false;
	});
	
	$('#privacy .privacy-item a.item-save').click(function() {
		// Canot push item?
		if(exists('#privacy .privacy-form input:disabled'))
			return false;
		
		// Get the hash
		var item_hash = '';
		
		if(!$('#privacy .privacy-item select').is(':disabled'))
			item_hash = $('#privacy .privacy-item select option:selected').attr('data-hash');
		
		// Read the form
		var privacy_second = '#privacy .privacy-second';
		var item_list = $('#privacy .privacy-head .list-left select').val();
		var item_action = $('#privacy .privacy-first input[name=action]').filter(':checked').val();
		var item_type = $(privacy_second + ' input[name=type]').filter(':checked').val();
		var item_order = $('#privacy .privacy-active input[name=order]').val();
		var item_value = '';
		
		// Switch the type to get the value
		switch(item_type) {
			case 'jid':
				item_value = $(privacy_second + ' input[type=text][name=jid]').val();
				
				break;
			
			case 'group':
				item_value = $(privacy_second + ' select[name=group]').val();
				
				break;
			
			case 'subscription':
				item_value = $(privacy_second + ' select[name=subscription]').val();
				
				break;
			
			default:
				item_type = '';
				
				break;
		}
		
		// Get the selected things to do
		var privacy_third_cb = '#privacy .privacy-third input[type=checkbox]';
		var item_prin = false;
		var item_prout = false;
		var item_msg = false;
		var item_iq = false;
		
		// Individual select?
		if(!$(privacy_third_cb + '[name=everything]').filter(':checked').size()) {
			if($(privacy_third_cb + '[name=send-messages]').filter(':checked').size())
				item_msg = true;
			if($(privacy_third_cb + '[name=send-queries]').filter(':checked').size())
				item_iq = true;
			if($(privacy_third_cb + '[name=send-queries]').filter(':checked').size())
				item_iq = true;
			if($(privacy_third_cb + '[name=see-status]').filter(':checked').size())
				item_prout = true;
			if($(privacy_third_cb + '[name=send-status]').filter(':checked').size())
				item_prin = true;
		}
		
		// Push item to the server!
		pushPrivacy(
			    item_list,
			    [item_type],
			    [item_value],
			    [item_action],
			    [item_order],
			    [item_prin],
			    [item_prout],
			    [item_msg],
			    [item_iq],
			    item_hash
			   );
		
		return false;
	});
	
	$('#privacy .privacy-first input').change(function() {
		enableFormPrivacy('second');
	});
	
	$('#privacy .privacy-second input').change(function() {
		enableFormPrivacy('third');
	});
	
	$('#privacy .privacy-third input[type=checkbox]').change(function() {
		// Target
		var target = '#privacy .privacy-third input[type=checkbox]';
		
		// Must tick "everything" checkbox?
		if(!$(target).filter(':checked').size())
			$(target + '[name=everything]').attr('checked', true);
		
		// Must untick the other checkboxes?
		else if($(this).is('[name=everything]'))
			$(target + ':not([name=everything])').removeAttr('checked');
		
		// Must untick "everything" checkbox?
		else
			$(target + '[name=everything]').removeAttr('checked');
	});
	
	$('#privacy .privacy-active input[name=order]').keyup(function() {
		// Get the value
		var value = $(this).val();
		
		// No value?
		if(!value)
			return;
		
		// Not a number?
		if(isNaN(value))
			value = 1;
		else
			value = parseInt(value);
		
		// Negative?		
		if(value < 0)
			value = value * -1;
		
		// Apply the filtered value
		$(this).val(value);
	})
	
	.blur(function() {
		// No value?
		if(!$(this).val())
			$(this).val('1');
	});
	
	$('#privacy .privacy-active .privacy-active-elements input').change(function() {
		// Get the values
		var list_name = $('#privacy .privacy-head .list-left select').val();
		var state_name = $(this).attr('name');
		
		// Cannot continue?
		if(!list_name || !state_name)
			return;
		
		// Change the current list status
		if($(this).filter(':checked').size())
			changePrivacy(list_name, state_name);
		else
			changePrivacy('', state_name);
	});
}
