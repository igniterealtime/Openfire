/*

Jappix - An open social platform
These are the dataform JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Gets the defined dataform elements
function dataForm(host, type, node, action, target) {
	// Clean the current session
	cleanDataForm(target);
	
	// We tell the user that a search has been launched
	$('#' + target + ' .wait').show();
	
	// If we have enough data
	if(host && type) {
		// Generate a session ID
		var sessionID = Math.round(100000.5 + (((900000.49999) - (100000.5)) * Math.random()));
		var id = target + '-' + sessionID + '-' + genID();
		$('.' + target + '-results').attr('data-session', target + '-' + sessionID);
		
		// We request the service item
		var iq = new JSJaCIQ();
		iq.setID(id);
		iq.setTo(host);
		iq.setType('get');
		
		// MUC admin query
		if(type == 'muc') {
			iq.setQuery(NS_MUC_OWNER);
			con.send(iq, handleDataFormMuc);
		}
		
		// Browse query
		else if(type == 'browse') {
			var iqQuery = iq.setQuery(NS_DISCO_ITEMS);
			
			if(node)
				iqQuery.setAttribute('node', node);
			
			con.send(iq, handleDataFormBrowse);
		}
		
		// Command
		else if(type == 'command') {
			var items;
			
			if(node)
				items = iq.appendNode('command', {'node': node, 'xmlns': NS_COMMANDS});
			
			else {
				items = iq.setQuery(NS_DISCO_ITEMS);
				items.setAttribute('node', NS_COMMANDS);
			}
			
			if(action && node) {
				iq.setType('set');
				items.setAttribute('action', action);
			}
			
			con.send(iq, handleDataFormCommand);
		}
		
		// Search query
		else if(type == 'search') {
			iq.setQuery(NS_SEARCH);
			con.send(iq, handleDataFormSearch);
		}
		
		// Subscribe query
		else if(type == 'subscribe') {
			iq.setQuery(NS_REGISTER);
			con.send(iq, handleDataFormSubscribe);
		}
		
		// Join
		else if(type == 'join') {
			if(target == 'discovery')
				closeDiscovery();
			
			checkChatCreate(host, 'groupchat');
		}
	}
	
	return false;
}

// Sends a given dataform
function sendDataForm(type, action, x_type, id, xid, node, sessionid, target) {
	// Path
	var pathID = '#' + target + ' .results[data-session=' + id + ']';
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setTo(xid);
	iq.setType('set');
	
	// Set the correct query
	var query;
	
	if(type == 'subscribe')
		iqQuery = iq.setQuery(NS_REGISTER);
	else if(type == 'search')
		iqQuery = iq.setQuery(NS_SEARCH);
	else if(type == 'command')
		iqQuery = iq.appendNode('command', {'xmlns': NS_COMMANDS, 'node': node, 'sessionid': sessionid, 'action': action});
	else if(type == 'x')
		iqQuery = iq.setQuery(NS_MUC_OWNER);
	
	// Build the XML document
	if(action != 'cancel') {
		// No X node
		if(exists('input.register-special') && (type == 'subscribe')) {
			$('input.register-special').each(function() {
				var iName = $(this).attr('name');
				var iValue = $(this).val();
				
				iqQuery.appendChild(iq.buildNode(iName, {'xmlns': NS_REGISTER}, iValue));
			});
		}
		
		// Can create the X node
		else {
			var iqX = iqQuery.appendChild(iq.buildNode('x', {'xmlns': NS_XDATA, 'type': x_type}));
			
			// Each input
			$(pathID + ' .oneresult input, ' + pathID + ' .oneresult textarea, ' + pathID + ' .oneresult select').each(function() {
				// Get the current input value
				var iVar = $(this).attr('name');
				var iType = $(this).attr('data-type');
				var iValue = $(this).val();
				
				// Build a new field node
				var field = iqX.appendChild(iq.buildNode('field', {'var': iVar, 'type': iType, 'xmlns': NS_XDATA}));
				
				// Boolean input?
				if(iType == 'boolean') {
					if($(this).filter(':checked').size())
						iValue = '1';
					else
						iValue = '0';
				}
				
				// JID-multi input?
				if(iType == 'jid-multi') {
					// Values array
					var xid_arr = [iValue];
					
					// Try to split it
					if(iValue.indexOf(',') != -1)
						xid_arr = iValue.split(',');
					
					// Append each value to the XML document
					for(i in xid_arr) {
						// Get the current value
						xid_current = xid_arr[i];
						
						// No current value?
						if(!xid_current || xid_current.match(/^(\s+)$/))
							continue;
						
						// Filter the current value
						xid_current = xid_current.replace(/ /g, '');
						
						// Add the current value
						field.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, xid_current));
					}
				}
				
				// List-multi selector?
				else if(iType == 'list-multi') {
					// Any value?
					if(iValue && iValue.length) {
						for(i in iValue)
							field.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, iValue[i]));
					}
				}
				
				// Other inputs?
				else
					field.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, iValue));
			});
		}
	}
	
	// Clean the current session
	cleanDataForm(target);
	
	// Show the waiting item
	$('#' + target + ' .wait').show();
	
	// Change the ID of the current discovered item
	var iqID = target + '-' + genID();
	$('#' + target + ' .' + target + '-results').attr('data-session', iqID);
	iq.setID(iqID);
	
	// Send the IQ
	if(type == 'subscribe')
		con.send(iq, handleDataFormSubscribe);
	else if(type == 'search')
		con.send(iq, handleDataFormSearch);
	else if(type == 'command')
		con.send(iq, handleDataFormCommand);
	else
		con.send(iq);
	
	return false;
}

// Displays the good dataform buttons
function buttonsDataForm(type, action, id, xid, node, sessionid, target, pathID) {
	// No need to use buttons?
	if(type == 'muc')
		return;
	
	// Override the "undefined" output
	if(!id)
		id = '';
	if(!xid)
		xid = '';
	if(!node)
		node = '';
	if(!sessionid)
		sessionid = '';
	
	// We generate the buttons code
	var buttonsCode = '<div class="oneresult ' + target + '-oneresult ' + target + '-formtools">';
	
	if(action == 'submit') {
		if((target == 'adhoc') && (type == 'command')) {
			buttonsCode += '<a href="#" class="submit" onclick="return sendDataForm(\'' + encodeOnclick(type) + '\', \'execute\', \'submit\', \'' + encodeOnclick(id) + '\', \'' + encodeOnclick(xid) + '\', \'' + encodeOnclick(node) + '\', \'' + encodeOnclick(sessionid) + '\', \'' + encodeOnclick(target) + '\');">' + _e("Submit") + '</a>';
			
			// When keyup on one text input
			$(pathID + ' input').keyup(function(e) {
				if(e.keyCode == 13) {
					sendDataForm(type, 'execute', 'submit', id, xid, node, sessionid, target);
					
					return false;
				}
			});
		}
		
		else {
			buttonsCode += '<a href="#" class="submit" onclick="return sendDataForm(\'' + encodeOnclick(type) + '\', \'submit\', \'submit\', \'' + encodeOnclick(id) + '\', \'' + encodeOnclick(xid) + '\', \'' + encodeOnclick(node) + '\', \'' + encodeOnclick(sessionid) + '\', \'' + encodeOnclick(target) + '\');">' + _e("Submit") + '</a>';
			
			// When keyup on one text input
			$(pathID + ' input').keyup(function(e) {
				if(e.keyCode == 13) {
					sendDataForm(type, 'submit', 'submit', id, xid, node, sessionid, target);
					
					return false;
				}
			});
		}
	}
	
	if((action == 'submit') && (type != 'subscribe') && (type != 'search'))
		buttonsCode += '<a href="#" class="submit" onclick="return sendDataForm(\'' + encodeOnclick(type) + '\', \'cancel\', \'cancel\', \'' + encodeOnclick(id) + '\', \'' + encodeOnclick(xid) + '\', \'' + encodeOnclick(node) + '\', \'' + encodeOnclick(sessionid) + '\', \'' + encodeOnclick(target) + '\');">' + _e("Cancel") + '</a>';
	
	if(((action == 'back') || (type == 'subscribe') || (type == 'search')) && (target == 'discovery'))
		buttonsCode += '<a href="#" class="back" onclick="return startDiscovery();">' + _e("Close") + '</a>';
	
	if((action == 'back') && ((target == 'welcome') || (target == 'directory')))
		buttonsCode += '<a href="#" class="back" onclick="return dataForm(HOST_VJUD, \'search\', \'\', \'\', \'' + target + '\');">' + _e("Previous") + '</a>';
	
	if((action == 'back') && (target == 'adhoc'))
		buttonsCode += '<a href="#" class="back" onclick="return dataForm(\'' + encodeOnclick(xid) + '\', \'command\', \'\', \'\', \'adhoc\');">' + _e("Previous") + '</a>';
	
	buttonsCode += '</div>';
	
	// We display the buttons code
	$(pathID).append(buttonsCode);
}

// Handles the MUC dataform
function handleDataFormMuc(iq) {
	handleErrorReply(iq);
	handleDataFormContent(iq, 'muc');
}

// Handles the browse dataform
function handleDataFormBrowse(iq) {
	handleErrorReply(iq);
	handleDataFormContent(iq, 'browse');
}

// Handles the command dataform
function handleDataFormCommand(iq) {
	handleErrorReply(iq);
	handleDataFormContent(iq, 'command');
}

// Handles the subscribe dataform
function handleDataFormSubscribe(iq) {
	handleErrorReply(iq);
	handleDataFormContent(iq, 'subscribe');
}

// Handles the search dataform
function handleDataFormSearch(iq) {
	handleErrorReply(iq);
	handleDataFormContent(iq, 'search');
}

// Handles the dataform content
function handleDataFormContent(iq, type) {
	// Get the ID
	var sID = iq.getID();
	
	// Get the target
	var splitted = sID.split('-');
	var target = splitted[0];
	var sessionID = target + '-' + splitted[1];
	var from = fullXID(getStanzaFrom(iq));
	var pathID = '#' + target + ' .results[data-session=' + sessionID + ']';
	
	// If an error occured
	if(!iq || (iq.getType() != 'result'))
		noResultDataForm(pathID);
	
	// If we got something okay
	else {
		var handleXML = iq.getNode();
		
		if(type == 'browse') {
			if($(handleXML).find('item').attr('jid')) {
				// Get the query node
				var queryNode = $(handleXML).find('query').attr('node');
				
				$(handleXML).find('item').each(function() {
					// We parse the received xml
					var itemHost = $(this).attr('jid');
					var itemNode = $(this).attr('node');
					var itemName = $(this).attr('name');
					var itemHash = hex_md5(itemHost);
					
					// Node
					if(itemNode)
						$(pathID).append(
							'<div class="oneresult ' + target + '-oneresult" onclick="return dataForm(\'' + encodeOnclick(itemHost) + '\', \'browse\', \'' + encodeOnclick(itemNode) + '\', \'\', \'' + encodeOnclick(target) + '\');">' + 
								'<div class="one-name">' + itemNode.htmlEnc() + '</div>' + 
							'</div>'
						);
					
					// Item
					else if(queryNode && itemName)
						$(pathID).append(
							'<div class="oneresult ' + target + '-oneresult">' + 
								'<div class="one-name">' + itemName.htmlEnc() + '</div>' + 
							'</div>'
						);
					
					// Item with children
					else {
						// We display the waiting element
						$(pathID + ' .disco-wait .disco-category-title').after(
							'<div class="oneresult ' + target + '-oneresult ' + itemHash + '">' + 
								'<div class="one-icon loading talk-images"></div>' + 
								'<div class="one-host">' + itemHost + '</div>' + 
								'<div class="one-type">' + _e("Requesting this service...") + '</div>' + 
							'</div>'
						);
						
						// We display the category
						$('#' + target + ' .disco-wait').show();
						
						// We ask the server what's the service type
						getDataFormType(itemHost, itemNode, sessionID);
					}
				});
			}
			
			// Else, there are no items for this query
			else
				noResultDataForm(pathID);
		}
		
		else if((type == 'muc') || (type == 'search') || (type == 'subscribe') || ((type == 'command') && $(handleXML).find('command').attr('xmlns'))) {
			// Get some values
			var xCommand = $(handleXML).find('command');
			var bNode = xCommand.attr('node');
			var bSession = xCommand.attr('sessionid');
			var bStatus = xCommand.attr('status');
			var xRegister = $(handleXML).find('query[xmlns=' + NS_REGISTER + ']').text();
			var xElement = $(handleXML).find('x');
			
			// Search done
			if((xElement.attr('type') == 'result') && (type == 'search')) {
				var bPath = pathID;
				
				// Display the result
				$(handleXML).find('item').each(function() {
					var bXID = $(this).find('field[var=jid] value:first').text();
					var bName = $(this).find('field[var=fn] value:first').text();
					var bCountry = $(this).find('field[var=ctry] value:first').text();
					var dName = bName;
					
					// Override "undefined" value
					if(!bXID)
						bXID = '';
					if(!bName)
						bName = _e("Unknown name");
					if(!bCountry)
						bCountry = _e("Unknown country");
					
					// User hash
					var bHash = hex_md5(bXID);
					
					// HTML code
					var bHTML = '<div class="oneresult ' + target + '-oneresult ' + bHash + '">' + 
							'<div class="avatar-container">' + 
								'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
							'</div>' + 
							'<div class="one-fn">' + bName + '</div>' + 
							'<div class="one-ctry">' + bCountry + '</div>' + 
							'<div class="one-jid">' + bXID + '</div>' + 
							'<div class="buttons-container">';
					
					// The buddy is not in our buddy list?
					if(!exists('#buddy-list .buddy[data-xid=' + escape(bXID) + ']'))
						bHTML += '<a href="#" class="one-add one-vjud one-button talk-images">' + _e("Add") + '</a>';
					
					// Chat button, if not in welcome/directory mode
					if(target == 'discovery')
						bHTML += '<a href="#" class="one-chat one-vjud one-button talk-images">' + _e("Chat") + '</a>';
					
					// Close the HTML element
					bHTML += '</div></div>';
					
					$(bPath).append(bHTML);
					
					// Click events
					$(bPath + ' .' + bHash + ' a').click(function() {
						// Buddy add
						if($(this).is('.one-add')) {
							$(this).hide();
							
							addThisContact(bXID, dName);
						}
						
						// Buddy chat
						if($(this).is('.one-chat')) {
							if(target == 'discovery')
								closeDiscovery();
							
							checkChatCreate(bXID , 'chat', '', '', dName);
						}
						
						return false;
					});
					
					// Get the user's avatar
					if(bXID)
						getAvatar(bXID, 'cache', 'true', 'forget');
				});
				
				// No result?
				if(!$(handleXML).find('item').size())
					noResultDataForm(pathID);
				
				// Previous button
				buttonsDataForm(type, 'back', sessionID, from, bNode, bSession, target, pathID);
			}
			
			// Command to complete
			else if(xElement.attr('xmlns') || ((type == 'subscribe') && xRegister)) {
				// We display the elements
				fillDataForm(handleXML, sessionID);
				
				// We display the buttons
				if(bStatus != 'completed')
					buttonsDataForm(type, 'submit', sessionID, from, bNode, bSession, target, pathID);
				else
					buttonsDataForm(type, 'back', sessionID, from, bNode, bSession, target, pathID);
			}
			
			// Command completed or subscription done
			else if(((bStatus == 'completed') && (type == 'command')) || (!xRegister && (type == 'subscribe'))) {
				// Display the good text
				var cNote = $(xCommand).find('note');
				
				// Any note?
				if(cNote.size()) {
					cNote.each(function() {
						$(pathID).append(
							'<div class="onetitle ' + target + '-oneresult">' + $(this).text().htmlEnc() + '</div>'
						);
					});
				}
				
				// Default text
				else
					$(pathID).append('<div class="oneinstructions ' + target + '-oneresult">' + _e("Your form has been sent.") + '</div>');
				
				// Display the back button
				buttonsDataForm(type, 'back', sessionID, from, '', '', target, pathID);
				
				// Add the gateway to our roster if subscribed
				if(type == 'subscribe')
					addThisContact(from);
			}
			
			// Command canceled
			else if((bStatus == 'canceled') && (type == 'command')) {
				if(target == 'discovery')
					startDiscovery();
				else if(target == 'adhoc')
					dataForm(from, 'command', '', '', 'adhoc');
			}
			
			// No items for this query
			else
				noResultDataForm(pathID);
		}
		
		else if(type == 'command') {
			if($(handleXML).find('item').attr('jid')) {
				// We display the elements
				$(handleXML).find('item').each(function() {
					// We parse the received xml
					var itemHost = $(this).attr('jid');
					var itemNode = $(this).attr('node');
					var itemName = $(this).attr('name');
					var itemHash = hex_md5(itemHost);
					
					// We display the waiting element
					$(pathID).prepend(
						'<div class="oneresult ' + target + '-oneresult ' + itemHash + '" onclick="return dataForm(\'' + encodeOnclick(itemHost) + '\', \'command\', \'' + encodeOnclick(itemNode) + '\', \'execute\', \'' + encodeOnclick(target) + '\');">' + 
							'<div class="one-name">' + itemName + '</div>' + 
							'<div class="one-next">»</div>' + 
						'</div>'
					);
				});
			}
			
			// Else, there are no items for this query
			else
				noResultDataForm(pathID);
		}
	}
	
	// Focus on the first input
	$(document).oneTime(10, function() {
		$(pathID + ' input:visible:first').focus();
	});
	
	// Hide the wait icon
	$('#' + target + ' .wait').hide();
}

// Fills the dataform elements
function fillDataForm(xml, id) {
	/* REF: http://xmpp.org/extensions/xep-0004.html */
	
	// Initialize new vars
	var target = id.split('-')[0];
	var pathID = '#' + target + ' .results[data-session=' + id + ']';
	var selector, is_dataform;
	
	// Is it a dataform?
	if($(xml).find('x[xmlns=' + NS_XDATA + ']').size())
		is_dataform = true;
	else
		is_dataform = false;
	
	// Determines the good selector to use
	if(is_dataform)
		selector = $(xml).find('x[xmlns=' + NS_XDATA + ']');
	else
		selector = $(xml);
	
	// Form title
	selector.find('title').each(function() {
		$(pathID).append(
			'<div class="onetitle ' + target + '-oneresult">' + $(this).text().htmlEnc() + '</div>'
		);
	});
	
	// Form instructions
	selector.find('instructions').each(function() {
		$(pathID).append(
			'<div class="oneinstructions ' + target + '-oneresult">' + $(this).text().htmlEnc() + '</div>'
		);
	});
	
	// Register?
	if(!is_dataform) {
		// Items to detect
		var reg_names = [_e("Nickname"), _e("Name"), _e("Password"), _e("E-mail")];
		var reg_ids = ['username', 'name', 'password', 'email'];
		
		// Append these inputs
		for(a in reg_names) {
			selector.find(reg_ids[a]).each(function() {
				$(pathID).append(
					'<div class="oneresult ' + target + '-oneresult">' + 
						'<label>' + reg_names[a] + '</label>' + 
						'<input name="' + reg_ids[a] + '" type="text" class="register-special dataform-i" />' + 
					'</div>'
				);
			});
		}
		
		return false;
	}
	
	// Dataform?
	selector.find('field').each(function() {
		// We parse the received xml
		var type = $(this).attr('type');
		var label = $(this).attr('label');
		var field = $(this).attr('var');
		var value = $(this).find('value:first').text();
		var required = '';
		
		// No value?
		if(!field)
			return;
		
		// Required input?
		if($(this).find('required').size())
			required = ' required=""';
		
		// Compatibility fix
		if(!label)
			label = field;
		
		if(!type)
			type = '';
		
		// Generate some values
		var input;
		var hideThis = '';
		
		// Fixed field
		if(type == 'fixed')
			$(pathID).append('<div class="oneinstructions">' + value.htmlEnc() + '</div>');
		
		else {
			// Hidden field
			if(type == 'hidden') {
				hideThis = ' style="display: none;"';
				input = '<input name="' + encodeQuotes(field) + '" data-type="' + encodeQuotes(type) + '" type="hidden" class="dataform-i" value="' + encodeQuotes(value) + '" ' + required + ' />';
			}

			// Boolean field
			else if(type == 'boolean') {
				var checked;
				
				if(value == '1')
					checked = 'checked';
				else
					checked = '';
				
				input = '<input name="' + encodeQuotes(field) + '" type="checkbox" data-type="' + encodeQuotes(type) + '" class="dataform-i df-checkbox" ' + checked + required + ' />';
			}
			
			// List-single/list-multi field
			else if((type == 'list-single') || (type == 'list-multi')) {
				var multiple = '';
				
				// Multiple options?
				if(type == 'list-multi')
					multiple = ' multiple=""';
				
				// Append the select field
				input = '<select name="' + encodeQuotes(field) + '" data-type="' + encodeQuotes(type) + '" class="dataform-i"' + required + multiple + '>';
				var selected;
				
				// Append the available options
				$(this).find('option').each(function() {
					var nLabel = $(this).attr('label');
					var nValue = $(this).find('value').text();
					
					// No label?
					if(!nLabel)
						nLabel = nValue;
					
					// If this is the selected value
					if(nValue == value)
						selected = 'selected';
					else
						selected = '';
					
					input += '<option ' + selected + ' value="' + encodeQuotes(nValue) + '">' + nLabel.htmlEnc() + '</option>';
				});
				
				input += '</select>';
			}
			
			// Text-multi field
			else if(type == 'text-multi')
				input = '<textarea rows="8" cols="60" data-type="' + encodeQuotes(type) + '" name="' + encodeQuotes(field) + '" class="dataform-i"' + required + '>' + value.htmlEnc() + '</textarea>';
			
			// JID-multi field
			else if(type == 'jid-multi') {
				// Put the XID into an array
				var xid_arr = [];
				
				$(this).find('value').each(function() {
					var cValue = $(this).text();
					
					if(!existArrayValue(xid_arr, cValue))
						xid_arr.push(cValue);
				});
				
				// Sort the array
				xid_arr.sort();
				
				// Create the array content
				if(xid_arr.length) {
					var xid_value = '';
					
					for(i in xid_arr) {
						// Any pre-value
						if(xid_value)
							xid_value += ', ';
						
						// Add the current XID
						xid_value += xid_arr[i];
					}
					
					input = '<input name="' + encodeQuotes(field) + '" data-type="' + encodeQuotes(type) + '" type="email" multiple="" class="dataform-i" value="' + encodeQuotes(xid_value) + '"' + required + ' />';
				}
			}
			
			// Other stuffs that are similar
			else {
				// Text-single field
				var iType = 'text';
				
				// Text-private field
				if(type == 'text-private')
					iType = 'password';
				
				// JID-single field
				else if(type == 'jid-single')
					iType = 'email';
				
				input = '<input name="' + encodeQuotes(field) + '" data-type="' + encodeQuotes(type) + '" type="' + iType + '" class="dataform-i" value="' + encodeQuotes(value) + '"' + required + ' />';
			}
			
			// Append the HTML markup for this field
			$(pathID).append(
				'<div class="oneresult ' + target + '-oneresult"' + hideThis + '>' + 
					'<label>' + label.htmlEnc() + '</label>' + 
					input + 
				'</div>'
			);
		}
	});
	
	return false;
}

// Gets the dataform type
function getDataFormType(host, node, id) {
	var iq = new JSJaCIQ();
	iq.setID(id + '-' + genID());
	iq.setTo(host);
	iq.setType('get');
	
	var iqQuery = iq.setQuery(NS_DISCO_INFO);
	
	if(node)
		iqQuery.setAttribute('node', node);
	
	con.send(iq, handleThisBrowse);
}

// Handles the browse stanza
function handleThisBrowse(iq) {
	/* REF: http://xmpp.org/registrar/disco-categories.html */
	
	var id = iq.getID();
	var splitted = id.split('-');
	var target = splitted[0];
	var sessionID = target + '-' + splitted[1];
	var from = fullXID(getStanzaFrom(iq));
	var hash = hex_md5(from);
	var handleXML = iq.getQuery();
	var pathID = '#' + target + ' .results[data-session=' + sessionID + ']';
	
	// We first remove the waiting element
	$(pathID + ' .disco-wait .' + hash).remove();
	
	if($(handleXML).find('identity').attr('type')) {
		var category = $(handleXML).find('identity').attr('category');
		var type = $(handleXML).find('identity').attr('type');
		var named = $(handleXML).find('identity').attr('name');
		
		if(named)
			gName = named;
		else
			gName = '';
		
		var one, two, three, four, five;
		
		// Get the features that this entity supports
		var findFeature = $(handleXML).find('feature');
		
		for(i in findFeature) {
			var current = findFeature.eq(i).attr('var');
			
			switch(current) {
				case NS_SEARCH:
					one = 1;
					break;
				
				case NS_MUC:
					two = 1;
					break;
				
				case NS_REGISTER:
					three = 1;
					break;
				
				case NS_COMMANDS:
					four = 1;
					break;
				
				case NS_DISCO_ITEMS:
					five = 1;
					break;
				
				default:
					break;
			}
		}
		
		var buttons = Array(one, two, three, four, five);
		
		// We define the toolbox links depending on the supported features
		var tools = '';
		var aTools = Array('search', 'join', 'subscribe', 'command', 'browse');
		var bTools = Array(_e("Search"), _e("Join"), _e("Subscribe"), _e("Command"), _e("Browse"));
		
		for(i in buttons) {
			if(buttons[i])
				tools += '<a href="#" class="one-button ' + aTools[i] + ' talk-images" onclick="return dataForm(\'' + encodeOnclick(from) + '\', \'' + encodeOnclick(aTools[i]) + '\', \'\', \'\', \'' + encodeOnclick(target) + '\');" title="' + encodeOnclick(bTools[i]) + '"></a>';
		}
		
		// As defined in the ref, we detect the type of each category to put an icon
		switch(category) {
			case 'account':
			case 'auth':
			case 'automation':
			case 'client':
			case 'collaboration':
			case 'component':
			case 'conference':
			case 'directory':
			case 'gateway':
			case 'headline':
			case 'hierarchy':
			case 'proxy':
			case 'pubsub':
			case 'server':
			case 'store':
				break;
			
			default:
				category = 'others';
		}
		
		// We display the item we found
		$(pathID + ' .disco-' + category + ' .disco-category-title').after(
			'<div class="oneresult ' + target + '-oneresult ' + hash + '">' + 
				'<div class="one-icon ' + category + ' talk-images"></div>' + 
				'<div class="one-host">' + from + '</div>' + 
				'<div class="one-type">' + gName + '</div>' + 
				'<div class="one-actions">' + tools + '</div>' + 
			'</div>'
		);
		
		// We display the category
		$(pathID + ' .disco-' + category).show();
	}
	
	else {
		$(pathID + ' .disco-others .disco-category-title').after(
			'<div class="oneresult ' + target + '-oneresult">' + 
				'<div class="one-icon down talk-images"></div>' + 
				'<div class="one-host">' + from + '</div>' + 
				'<div class="one-type">' + _e("Service offline or broken") + '</div>' + 
			'</div>'
		);
		
		// We display the category
		$(pathID + ' .disco-others').show();
	}
	
	// We hide the waiting stuffs if there's no remaining loading items
	if(!$(pathID + ' .disco-wait .' + target + '-oneresult').size())
		$(pathID + ' .disco-wait, #' + target + ' .wait').hide();
}

// Cleans the current data-form popup
function cleanDataForm(target) {
	if(target == 'discovery')
		cleanDiscovery();
	else
		$('#' + target + ' div.results').empty();
}

// Displays the no result indicator
function noResultDataForm(path) {
	$(path).prepend('<p class="no-results">' + _e("Sorry, but the entity didn't return any result!") + '</p>');
}
