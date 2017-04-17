/*

Jappix - An open social platform
These are the CAPS JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 05/05/11

*/

// Returns an array of the Jappix disco#infos
function myDiscoInfos() {
	var fArray = new Array(
		NS_MUC,
		NS_MUC_USER,
		NS_MUC_ADMIN,
		NS_MUC_OWNER,
		NS_MUC_CONFIG,
		NS_DISCO_INFO,
		NS_DISCO_ITEMS,
		NS_PUBSUB_RI,
		NS_BOSH,
		NS_CAPS,
		NS_MOOD,
		NS_ACTIVITY,
		NS_TUNE,
		NS_GEOLOC,
		NS_NICK,
		NS_URN_ADATA,
		NS_URN_AMETA,
		NS_URN_MBLOG,
		NS_URN_INBOX,
		NS_MOOD + NS_NOTIFY,
		NS_ACTIVITY + NS_NOTIFY,
		NS_TUNE + NS_NOTIFY,
		NS_GEOLOC + NS_NOTIFY,
		NS_URN_MBLOG + NS_NOTIFY,
		NS_URN_INBOX + NS_NOTIFY,
		NS_URN_DELAY,
		NS_ROSTER,
		NS_ROSTERX,
		NS_HTTP_AUTH,
		NS_CHATSTATES,
		NS_XHTML_IM,
		NS_IPV6,
		NS_LAST,
		NS_PRIVATE,
		NS_REGISTER,
		NS_SEARCH,
		NS_COMMANDS,
		NS_VERSION,
		NS_XDATA,
		NS_VCARD,
		NS_URN_TIME,
		NS_URN_PING,
		NS_URN_ARCHIVE,
		NS_URN_AR_PREF,
		NS_URN_RECEIPTS,
		NS_PRIVACY
	);
	
	return fArray;
}

// Gets the disco#infos of an entity
function getDiscoInfos(to, caps) {
	// No CAPS
	if(!caps) {
		logThis('No CAPS: ' + to, 2);
		
		displayDiscoInfos(to, '');
		
		return false;
	}
	
	// Get the stored disco infos
	var xml = XMLFromString(getPersistent('caps', caps));
	
	// Yet stored
	if(xml) {
		logThis('CAPS from cache: ' + to, 3);
		
		displayDiscoInfos(to, xml);
		
		return true;
	}
	
	logThis('CAPS from the network: ' + to, 3);
	
	// Not stored: get the disco#infos
	var iq = new JSJaCIQ();
	
	iq.setTo(to);
	iq.setType('get');
	iq.setQuery(NS_DISCO_INFO);
	
	con.send(iq, handleDiscoInfos);
	
	return true;
}

// Handles the disco#infos of an entity
function handleDiscoInfos(iq) {
	if(!iq || (iq.getType() == 'error'))
		return;
	
	// IQ received, get some values
	var from = fullXID(getStanzaFrom(iq));
	var query = iq.getQuery();
	
	// Generate the CAPS-processing values
	var identities = new Array();
	var features = new Array();
	var data_forms = new Array();
	
	// Identity values
	$(query).find('identity').each(function() {
		var pCategory = $(this).attr('category');
		var pType = $(this).attr('type');
		var pLang = $(this).attr('xml:lang');
		var pName = $(this).attr('name');
		
		if(!pCategory)
			pCategory = '';
		if(!pType)
			pType = '';
		if(!pLang)
			pLang = '';
		if(!pName)
			pName = '';
		
		identities.push(pCategory + '/' + pType + '/' + pLang + '/' + pName);
	});
	
	// Feature values
	$(query).find('feature').each(function() {
		var pVar = $(this).attr('var');
		
		// Add the current value to the array
		if(pVar)
			features.push(pVar);
	});
	
	// Data-form values
	$(query).find('x[xmlns=' + NS_XDATA + ']').each(function() {
		// Initialize some stuffs
		var pString = '';
		var sortVar = new Array();
		
		// Add the form type field
		$(this).find('field[var=FORM_TYPE] value').each(function() {
			var cText = $(this).text();
			
			if(cText)
				pString += cText + '<';
		});
		
		// Add the var attributes into an array
		$(this).find('field:not([var=FORM_TYPE])').each(function() {
			var cVar = $(this).attr('var');
			
			if(cVar)
				sortVar.push(cVar);
		});
		
		// Sort the var attributes
		sortVar = sortVar.sort();
		
		// Loop this sorted var attributes
		for(i in sortVar) {
			// Initialize the value sorting
			var sortVal = new Array();
			
			// Append it to the string
			pString += sortVar[i] + '<';
			
			// Add each value to the array
			$(this).find('field[var=' + sortVar[i] + '] value').each(function() {
				sortVal.push($(this).text());
			});
			
			// Sort the values
			sortVal = sortVal.sort();
			
			// Append the values to the string
			for(j in sortVal)
				pString += sortVal[j] + '<';
		}
		
		// Any string?
		if(pString) {
			// Remove the undesired double '<' from the string
			if(pString.match(/(.+)(<)+$/))
				pString = pString.substring(0, pString.length - 1);
			
			// Add the current string to the array
			data_forms.push(pString);
		}
	});
	
	// Process the CAPS
	var caps = processCaps(identities, features, data_forms);
	
	// Get the XML string
	var xml = xmlToString(query);
	
	// Store the disco infos
	setPersistent('caps', caps, xml);
	
	// This is our server
	if(from == getServer()) {
		// Handle the features
		handleFeatures(xml);
		
		logThis('Got our server CAPS', 3);
	}
	
	else {
		// Display the disco infos
		displayDiscoInfos(from, xml);
		
		logThis('Got CAPS: ' + from, 3);
	}
}

// Displays the disco#infos everywhere needed for an entity
function displayDiscoInfos(from, xml) {
	// Generate the chat path
	var xid = bareXID(from);
	
	// This comes from a private groupchat chat?
	if(isPrivate(xid))
		xid = from;
	
	hash = hex_md5(xid);
	
	// xHTML-IM indicator
	var xhtml_im = false;
	var receipts = false;
	
	// Display the supported features
	$(xml).find('feature').each(function() {
		var current = $(this).attr('var');
		
		// xHTML-IM
		if(current == NS_XHTML_IM)
			xhtml_im = true;
		
		// Receipts
		else if(current == NS_URN_RECEIPTS)
			receipts = true;
	});
	
	// Paths
	var path = $('#' + hash);
	var message_area = path.find('.message-area');
	var style = path.find('.chat-tools-style');
	
	// Apply xHTML-IM
	if(xhtml_im)
		style.show();
	
	else {
		// Remove the tooltip elements
		style.hide();
		style.find('.bubble-style').remove();
		
		// Reset the markers
		message_area.removeAttr('style')
			    .removeAttr('data-color')
			    .removeAttr('data-bold')
			    .removeAttr('data-italic')
			    .removeAttr('data-underline');
	}
	
	// Apply receipts
	if(receipts)
		message_area.attr('data-receipts', 'true');
	else
		message_area.removeAttr('data-receipts');
}

// Generates the CAPS hash
function processCaps(cIdentities, cFeatures, cDataForms) {
	// Initialize
	var cString = '';
	
	// Sort the arrays
	cIdentities = cIdentities.sort();
	cFeatures = cFeatures.sort();
	cDataForms = cDataForms.sort();
	
	// Process the sorted identity string
	for(a in cIdentities)
		cString += cIdentities[a] + '<';
	
	// Process the sorted feature string
	for(b in cFeatures)
		cString += cFeatures[b] + '<';
	
	// Process the sorted data-form string
	for(c in cDataForms)
		cString += cDataForms[c] + '<';
	
	// Process the SHA-1 hash
	var cHash = b64_sha1(cString);
	
	return cHash;
}

// Generates the Jappix CAPS hash
function myCaps() {
	return processCaps(new Array('client/web//Jappix'), myDiscoInfos(), new Array());
}
