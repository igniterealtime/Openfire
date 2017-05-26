/*

Jappix - An open social platform
These are the options JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Opens the options popup
function optionsOpen() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Edit options") + '</div>' + 
	
	'<div class="tab">' + 
		'<a href="#" class="tab-active" data-key="1">' + _e("General") + '</a>' + 
		'<a href="#" class="pubsub-hidable" data-key="2">' + _e("Channel") + '</a>' + 
		'<a href="#" data-key="3">' + _e("Account") + '</a>' + 
	'</div>' + 
	
	'<div class="content">' + 
		'<div id="conf1" class="lap-active one-lap forms">' + 
			'<fieldset class="privacy">' + 
				'<legend>' + _e("Privacy") + '</legend>' + 
				
				'<label for="geolocation" class="pep-hidable">' + _e("Geolocation") + '</label>' + 
				'<input id="geolocation" type="checkbox" class="pep-hidable" />' + 
				
				'<label for="archiving" class="archives-hidable pref">' + _e("Message archiving") + '</label>' + 
				'<input id="archiving" type="checkbox" class="archives-hidable pref" />' + 
			'</fieldset>' + 
			
			'<fieldset>' + 
				'<legend>' + _e("Application") + '</legend>' + 
				
				'<label for="sounds">' + _e("Sounds") + '</label>' + 
				'<input id="sounds" type="checkbox" />' + 
				
				'<label for="showall">' + _e("Show all friends") + '</label>' + 
				'<input id="showall" type="checkbox" />' + 
				
				'<label for="integratemedias">' + _e("Media integration") + '</label>' + 
				'<input id="integratemedias" type="checkbox" />' + 
				
				'<label class="xmpplinks-hidable">' + _e("XMPP links") + '</label>' + 
				'<a href="#" class="linked xmpp-links xmpplinks-hidable">' + _e("Open XMPP links with Jappix") + '</a>' + 
			'</fieldset>' + 
		'</div>' + 
		
		'<div id="conf2" class="one-lap forms">' + 
			'<fieldset>' + 
				'<legend>' + _e("Channel") + '</legend>' + 
				
				'<label>' + _e("Empty") + '</label>' + 
				'<a href="#" class="linked empty-channel">' + _e("Empty channel") + '</a>' + 
				
				'<label>' + _e("Persistent") + '</label>' + 
				'<input id="persistent" type="checkbox" />' + 
				
				'<label>' + _e("Maximum notices") + '</label>' + 
				'<select id="maxnotices">' + 
					'<option value="1">1</option>' + 
					'<option value="100">100</option>' + 
					'<option value="1000">1000</option>' + 
					'<option value="10000">10000</option>' + 
					'<option value="100000">100000</option>' + 
					'<option value="1000000">1000000</option>' + 
				'</select>' + 
			'</fieldset>' + 
			
			'<div class="sub-ask sub-ask-empty sub-ask-element">' + 
				'<div class="sub-ask-top">' + 
					'<div class="sub-ask-title">' + _e("Empty channel") + '</div>' + 
					'<a href="#" class="sub-ask-close">X</a>' + 
				'</div>' + 
				
				'<div class="sub-ask-content">' + 
					'<label>' + _e("Password") + '</label>' + 
					'<input type="password" class="purge-microblog check-empty" required="" />' + 
				'</div>' + 
				
				'<a href="#" class="sub-ask-bottom">' + _e("Empty") + ' &raquo;</a>' + 
			'</div>' + 
		'</div>' + 
		
		'<div id="conf3" class="one-lap forms">' + 
			'<fieldset>' + 
				'<legend>' + _e("Account") + '</legend>' + 
				
				'<label>' + _e("Password") + '</label>' + 
				'<a href="#" class="linked change-password">' + _e("Change password") + '</a>' + 
				
				'<label>' + _e("Delete") + '</label>' + 
				'<a href="#" class="linked delete-account">' + _e("Delete account") + '</a>' + 
			'</fieldset>' + 
				
			'<div class="sub-ask sub-ask-pass sub-ask-element">' + 
				'<div class="sub-ask-top">' + 
					'<div class="sub-ask-title">' + _e("Change password") + '</div>' + 
					'<a href="#" class="sub-ask-close">X</a>' + 
				'</div>' + 
				
				'<div class="sub-ask-content">' + 
					'<label>' + _e("Old") + '</label>' + 
					'<input type="password" class="password-change old" required="" />' + 
					
					'<label>' + _e("New (2 times)") + '</label>' + 
					'<input type="password" class="password-change new1" required="" />' + 
					'<input type="password" class="password-change new2" required="" />' + 
				'</div>' + 
				
				'<a href="#" class="sub-ask-bottom">' + _e("Continue") + ' &raquo;</a>' + 
			'</div>' + 
			
			'<div class="sub-ask sub-ask-delete sub-ask-element">' + 
				'<div class="sub-ask-top">' + 
					'<div class="sub-ask-title">' + _e("Delete account") + '</div>' + 
					'<a href="#" class="sub-ask-close">X</a>' + 
				'</div>' + 
				
				'<div class="sub-ask-content">' + 
					'<label>' + _e("Password") + '</label>' + 
					'<input type="password" class="delete-account check-password" required="" />' + 
				'</div>' + 
				
				'<a href="#" class="sub-ask-bottom">' + _e("Delete") + ' &raquo;</a>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish save">' + _e("Save") + '</a>' + 
		'<a href="#" class="finish cancel">' + _e("Cancel") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('options', html);
	
	// Apply the features
	applyFeatures('options');
	
	// Associate the events
	launchOptions();
	
	return false;
}

// Closes the options popup
function closeOptions() {
	// Destroy the popup
	destroyPopup('options');
	
	return false;
}

// Checks whether the options are loaded or not
function loadedOptions() {
	if($('.options-hidable').is(':visible'))
		return true;
	
	return false;
}

// Switches between the options tabs
function switchOptions(id) {
	$('#options .one-lap').hide();
	$('#options #conf' + id).show();
	$('#options .tab a').removeClass('tab-active');
	$('#options .tab a[data-key=' + id + ']').addClass('tab-active');
	
	return false;
}

// Manages the options wait item
function waitOptions(id) {
	var sOptions = $('#options .content');
	
	// Remove the current item class
	sOptions.removeClass(id);
	
	// Hide the waiting items if all was received
	if(!sOptions.hasClass('microblog') && !sOptions.hasClass('archives')) {
		$('#options .wait').hide();
		$('#options .finish:first').removeClass('disabled');
	}
}

// Sends the options to the XMPP server
function storeOptions() {
	// Get the values
	var sounds = getDB('options', 'sounds');
	var geolocation = getDB('options', 'geolocation');
	var showall = getDB('options', 'roster-showall');
	var integratemedias = getDB('options', 'integratemedias');
	var status = getDB('options', 'presence-status');
	
	// Create an array to be looped
	var oType = new Array('sounds', 'geolocation', 'roster-showall', 'integratemedias', 'presence-status');
	var oContent = new Array(sounds, geolocation, showall, integratemedias, status);
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var query = iq.setQuery(NS_PRIVATE);
	var storage = query.appendChild(iq.buildNode('storage', {'xmlns': NS_OPTIONS}));
	
	// Loop the array
	for(i in oType)
		storage.appendChild(iq.buildNode('option', {'type': oType[i], 'xmlns': NS_OPTIONS}, oContent[i]));
	
	con.send(iq, handleStoreOptions);
	
	logThis('Storing options...', 3);
}

// Handles the option storing
function handleStoreOptions(iq) {
	if(!iq || (iq.getType() != 'result'))
		logThis('Options not stored.', 2);
	else
		logThis('Options stored.', 3);
}

// Saves the user options
function saveOptions() {
	// We apply the sounds
	var sounds = '0';
	
	if($('#sounds').filter(':checked').size())
		sounds = '1';
	
	setDB('options', 'sounds', sounds);
	
	// We apply the geolocation
	if($('#geolocation').filter(':checked').size()) {
		setDB('options', 'geolocation', '1');
		
		// We geolocate the user on the go
		geolocate();
	}
	
	else {
		setDB('options', 'geolocation', '0');
		
		// We delete the geolocation informations
		sendPosition();
		removeDB('geolocation', 'now');
	}
	
	// We apply the roster show all
	if($('#showall').filter(':checked').size()) {
		setDB('options', 'roster-showall', '1');
		showAllBuddies('options');
	}
	
	else {
		setDB('options', 'roster-showall', '0');
		showOnlineBuddies('options');
	}
	
	// We apply the media integration
	var integratemedias = '0';
	
	if($('#integratemedias').filter(':checked').size())
		integratemedias = '1';
	
	setDB('options', 'integratemedias', integratemedias);
	
	// We apply the message archiving
	if(enabledArchives('pref')) {
		var aEnabled = false;
		
		if($('#archiving').filter(':checked').size())
			aEnabled = true;
		
		configArchives(aEnabled);
	}
	
	// We apply the microblog configuration
	var persist = '0';
	var maximum = $('#maxnotices').val();
	
	if($('#persistent').filter(':checked').size())
		persist = '1';
	
	if(enabledPEP() && enabledPubSub())
		setupMicroblog('', NS_URN_MBLOG, persist, maximum, '', '', false);
	
	// We send the options to the database
	storeOptions();
	
	// Close the options
	closeOptions();
	
	return false;
}

// Handles the password changing
function handlePwdChange(iq) {
	// Remove the general wait item
	removeGeneralWait();
	
	// If no errors
	if(!handleErrorReply(iq)) {
		clearLastSession();
		quit();
		openThisInfo(1);
		
		logThis('Password changed.', 3);
	}
	
	else
		logThis('Password not changed.', 2);
}

// Sends the new account password
function sendNewPassword() {
	/* REF: http://xmpp.org/extensions/xep-0077.html#usecases-changepw */
	
	var password0 = $('#options .old').val();
	var password1 = $('#options .new1').val();
	var password2 = $('#options .new2').val();
	
	if ((password1 == password2) && (password0 == getPassword())) {
		// We show the waiting image
		showGeneralWait();
		
		// We send the IQ
		var iq = new JSJaCIQ();
		
		iq.setTo(getServer());
		iq.setType('set');
		
		var iqQuery = iq.setQuery(NS_REGISTER);
		
		iqQuery.appendChild(iq.buildNode('username', {'xmlns': NS_REGISTER}, con.username));
		iqQuery.appendChild(iq.buildNode('password', {'xmlns': NS_REGISTER}, password1));
		
		con.send(iq, handlePwdChange);
		
		logThis('Password change sent.', 3);
	}
	
	else {
		$('.sub-ask-pass input').each(function() {
			var select = $(this);
			
			if(!select.val())
				$(document).oneTime(10, function() {
					select.addClass('please-complete').focus();
				});
			else
				select.removeClass('please-complete');	
		});
		
		if(password0 != getPassword())
			$(document).oneTime(10, function() {
				$('#options .old').addClass('please-complete').focus();
			});
		if(password1 != password2)
			$(document).oneTime(10, function() {
				$('#options .new1, #options .new2').addClass('please-complete').focus();
			});
	}
	
	return false;
}

// Handles the account deletion request
function handleAccDeletion(iq) {
	// Remove the general wait item
	removeGeneralWait();
	
	// If no errors
	if(!handleErrorReply(iq)) {
		clearLastSession();
		destroyTalkPage();
		openThisInfo(2);
		logout();
		
		logThis('Account deleted.', 3);
	}
	
	else
		logThis('Account not deleted.', 2);
}

// Purge the user's microblog items
function purgeMyMicroblog() {
	/* REF: http://xmpp.org/extensions/xep-0060.html#owner-purge */
	
	var password = $('#options .check-empty').val();
	
	if(password == getPassword()) {
		// Send the IQ to remove the item (and get eventual error callback)
		var iq = new JSJaCIQ();
		iq.setType('set');
		
		var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB_OWNER});
		pubsub.appendChild(iq.buildNode('purge', {'node': NS_URN_MBLOG, 'xmlns': NS_PUBSUB_OWNER}));
		
		con.send(iq, handleMicroblogPurge);
		
		// Hide the tool
		$('#options .sub-ask').hide();
		
		logThis('Microblog purge sent.', 3);
	}
	
	else {
		var selector = $('#options .check-empty');
		
		if(password != getPassword())
			$(document).oneTime(10, function() {
				selector.addClass('please-complete').focus();
			});
		else
			selector.removeClass('please-complete');
	}
	
	return false;
}

// Handles the microblog purge
function handleMicroblogPurge(iq) {
	// If no errors
	if(!handleErrorReply(iq)) {
		// Remove the microblog items
		$('.one-update.update_' + hex_md5(getXID())).remove();
		
		logThis('Microblog purged.', 3);
	}
	
	else
		logThis('Microblog not purged.', 2);
}

// Deletes the user's account
function deleteMyAccount() {
	/* REF: http://xmpp.org/extensions/xep-0077.html#usecases-cancel */
	
	var password = $('#options .check-password').val();
	
	if(password == getPassword()) {
		// We show the waiting image
		showGeneralWait();
		
		// We send the IQ
		var iq = new JSJaCIQ();
		iq.setType('set');
		
		var iqQuery = iq.setQuery(NS_REGISTER);
		iqQuery.appendChild(iq.buildNode('remove', {'xmlns': NS_REGISTER}));
		
		con.send(iq, handleAccDeletion);
		
		logThis('Delete account sent.', 3);
	}
	
	else {
		var selector = $('#options .check-password');
		
		if(password != getPassword())
			$(document).oneTime(10, function() {
				selector.addClass('please-complete').focus();
			});
		else
			selector.removeClass('please-complete');
	}
	
	return false;
}

// Loads the user options
function loadOptions() {
	// Process the good stuffs, depending of the server features
	var enabled_archives_pref = enabledArchives('pref');
	var enabled_pubsub = enabledPubSub();
	var enabled_pep = enabledPEP();
	var sWait = $('#options .content');
	
	// Show the waiting items if necessary
	if(enabled_archives_pref || (enabled_pep && enabled_pubsub)) {
		$('#options .wait').show();
		$('#options .finish:first').addClass('disabled');
	}
	
	// We get the archiving configuration
	if(enabled_archives_pref) {
		sWait.addClass('archives');
		getConfigArchives();
	}
	
	// We get the microblog configuration
	if(enabled_pubsub && enabled_pep) {
		sWait.addClass('microblog');
		getConfigMicroblog();
	}
	
	// We show the "privacy" form if something is visible into it
	if(enabled_archives_pref || enabled_pep)
		$('#options fieldset.privacy').show();
	
	// We get the values of the forms for the sounds
	if(getDB('options', 'sounds') == '0')
		$('#sounds').attr('checked', false);
	else
		$('#sounds').attr('checked', true);
	
	// We get the values of the forms for the geolocation
	if(getDB('options', 'geolocation') == '1')
		$('#geolocation').attr('checked', true);
	else
		$('#geolocation').attr('checked', false);
	
	// We get the values of the forms for the roster show all
	if(getDB('options', 'roster-showall') == '1')
		$('#showall').attr('checked', true);
	else
		$('#showall').attr('checked', false);
	
	// We get the values of the forms for the integratemedias
	if(getDB('options', 'integratemedias') == '0')
		$('#integratemedias').attr('checked', false);
	else
		$('#integratemedias').attr('checked', true);
}

// Plugin launcher
function launchOptions() {
	// Click events
	$('#options .tab a').click(function() {
		// Yet active?
		if($(this).hasClass('tab-active'))
			return false;
		
		// Switch to the good tab
		var key = parseInt($(this).attr('data-key'));
		
		return switchOptions(key);
	});
	
	$('#options .linked').click(function() {
		$('#options .sub-ask').hide();
	});
	
	$('#options .xmpp-links').click(xmppLinksHandler);
	
	$('#options .empty-channel').click(function() {
		var selector = '#options .sub-ask-empty';
		
		$(selector).show();
		
		$(document).oneTime(10, function() {
			$(selector + ' input').focus();
		});
		
		return false;
	});
	
	$('#options .change-password').click(function() {
		var selector = '#options .sub-ask-pass';
		
		$(selector).show();
		
		$(document).oneTime(10, function() {
			$(selector + ' input:first').focus();
		});
		
		return false;
	});
	
	$('#options .delete-account').click(function() {
		var selector = '#options .sub-ask-delete';
		
		$(selector).show();
		
		$(document).oneTime(10, function() {
			$(selector + ' input').focus();
		});
		
		return false;
	});
	
	$('#options .sub-ask-pass .sub-ask-bottom').click(function() {
		return sendNewPassword();
	});
	
	$('#options .sub-ask-empty .sub-ask-bottom').click(function() {
		return purgeMyMicroblog();
	});
	
	$('#options .sub-ask-delete .sub-ask-bottom').click(function() {
		return deleteMyAccount();
	});
	
	$('#options .sub-ask-close').click(function() {
		$('#options .sub-ask').hide();
		
		return false;
	});
	
	$('#options .bottom .finish').click(function() {
		if($(this).is('.save') && !$(this).hasClass('disabled'))
			return saveOptions();
		if($(this).is('.cancel'))
			return closeOptions();
		
		return false;
	});
	
	// The keyup events
	$('#options .sub-ask input').keyup(function(e) {
		if(e.keyCode == 13) {
			// Microblog purge
			if($(this).is('.purge-microblog'))
				return purgeMyMicroblog();
			
			// Password change
			else if($(this).is('.password-change'))
				return sendNewPassword();
			
			// Account deletion
			else if($(this).is('.delete-account'))
				return deleteMyAccount();
		}
	});
	
	// Load the options
	loadOptions();
}
