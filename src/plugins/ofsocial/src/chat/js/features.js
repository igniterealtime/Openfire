/*

Jappix - An open social platform
This is the server features JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 01/06/11

*/

// Gets the features of a server
function getFeatures() {
	/* REF: http://xmpp.org/extensions/xep-0030.html */
	
	// Get the main values
	var to = getServer();
	var caps = con.server_caps;
	var xml = null;
	
	// Try to get the stored data
	if(caps)
		xml = XMLFromString(getPersistent('caps', caps));
	
	// Any stored data?
	if(xml) {
		handleFeatures(xml);
		
		logThis('Read server CAPS from cache.');
	}
	
	// Not stored (or no CAPS)!
	else {
		var iq = new JSJaCIQ();
		
		iq.setTo(to);
		iq.setType('get');
		iq.setQuery(NS_DISCO_INFO);
		
		con.send(iq, handleDiscoInfos);
		
		logThis('Read server CAPS from network.');
	}
}

// Handles the features of a server
function handleFeatures(xml) {
	// Selector
	var selector = $(xml);
	
	// Markers
	var pep = false;
	var pubsub = false;
	var archive = false;
	var archive_auto = false;
	var archive_manual = false;
	var archive_manage = false;
	var archive_pref = false;
	var commands = false;
	
	// Scan the features
	if(selector.find('identity[category=pubsub][type=pep]').size())
		pep = true;
	if(selector.find('feature[var=' + NS_PUBSUB + ']').size())
		pubsub = true;
	if(selector.find('feature[var=' + NS_URN_ARCHIVE + ']').size())
		archive = true;
	if(selector.find('feature[var=' + NS_URN_AR_AUTO + ']').size())
		archive_auto = true;
	if(selector.find('feature[var=' + NS_URN_AR_MANUAL + ']').size())
		archive_manual = true;
	if(selector.find('feature[var=' + NS_URN_AR_MANAGE + ']').size())
		archive_manage = true;
	if(selector.find('feature[var=' + NS_URN_AR_PREF + ']').size())
		archive_pref = true;
	if(selector.find('feature[var=' + NS_COMMANDS + ']').size())
		commands = true;
	
	// Enable the pep elements if available
	if(pep) {
		// Update our database
		enableFeature('pep');
		
		// Get the microblog
		getInitMicroblog();
		
		// Get the notifications
		getNotifications();
		
		// Geolocate the user
		geolocate();
		
		// Enable microblogging send tools
		waitMicroblog('sync');
		$('.postit.attach').css('display', 'block');
		
		logThis('XMPP server supports PEP.', 3);
	}
	
	// Disable microblogging send tools (no PEP!)
	else {
		waitMicroblog('unsync');
		
		logThis('XMPP server does not support PEP.', 2);
	}
	
	// Enable the pubsub features if available
	if(pubsub)
		enableFeature(NS_PUBSUB);
	
	// Enable the archiving features if available
	if(archive)
		enableFeature(NS_URN_ARCHIVE);
	
	// Enable the archiving sub-features if available
	if(archive_pref)
		enableFeature(NS_URN_AR_PREF);
	if(archive_auto)
		enableFeature(NS_URN_AR_AUTO);
	if(archive_manual)
		enableFeature(NS_URN_AR_MANUAL);
	if(archive_manage)
		enableFeature(NS_URN_AR_MANAGE);
	
	// Enable the commands features if available
	if(commands)
		enableFeature(NS_COMMANDS);
	
	// Hide the private life fieldset if nothing to show
	if(!pep && !archive_pref)
		$('#options fieldset.privacy').hide();
	
	// Apply the features
	applyFeatures('talk');
	
	// Process the buddy-list height
	if(pep)
		adaptRoster();
	
	return false;
}

// The function to apply the features to an element
function applyFeatures(id) {
	// Path to the elements
	var path = '#' + id + ' .';
	
	// PEP features
	if(enabledPEP())
		$(path + 'pep-hidable').show();
	
	// PubSub features
	if(enabledPubSub())
		$(path + 'pubsub-hidable').show();
	
	// Archives features
	if(enabledArchives() || enabledArchives('auto') || enabledArchives('manual') || enabledArchives('manage')) {
		$(path + 'archives-hidable:not(.pref)').show();
		
		// Sub-feature: archives preferences
		if(enabledArchives('pref'))
			$(path + 'archives-hidable.pref').show();
	}
	
	// Commands features
	if(enabledCommands())
		$(path + 'commands-hidable').show();
	
	// XMPP links (browser feature)
	if(navigator.registerProtocolHandler)
		$(path + 'xmpplinks-hidable').show();
}

// Enables a feature
function enableFeature(feature) {
	setDB('feature', feature, 'true');
}

// Checks if a feature is enabled
function enabledFeature(feature) {
	if(getDB('feature', feature) == 'true')
		return true;
	else
		return false;
}

// Returns the XMPP server PEP support
function enabledPEP() {
	return enabledFeature('pep');
}

// Returns the XMPP server PubSub support
function enabledPubSub() {
	return enabledFeature(NS_PUBSUB);
}

// Returns the XMPP server archives support
function enabledArchives(sub) {
	var xmlns = NS_URN_ARCHIVE;
	
	// Any sub element sent?
	if(sub)
		xmlns += ':' + sub;
	
	return enabledFeature(xmlns);
}

// Returns the XMPP server commands support
function enabledCommands() {
	return enabledFeature(NS_COMMANDS);
}
