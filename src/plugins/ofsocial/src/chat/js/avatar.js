/*

Jappix - An open social platform
These are the avatar JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 01/03/11

*/

// Requests the avatar of a given user
var AVATAR_PENDING = [];

function getAvatar(xid, mode, enabled, photo) {
	/* REF: http://xmpp.org/extensions/xep-0153.html */
	
	// No need to get the avatar, another process is yet running
	if(existArrayValue(AVATAR_PENDING, xid))
		return false;
	
	// Initialize: XML data is in one SQL entry, because some browser are sloooow with SQL requests
	var xml = XMLFromString(getPersistent('avatar', xid));
	var forced = false;
	
	// Retrieving forced?
	if($(xml).find('forced').text() == 'true')
		forced = true;
	
	// No avatar in presence
	if(!photo && !forced && (enabled == 'true')) {
		// Pending marker
		AVATAR_PENDING.push(xid);
		
		// Reset the avatar
		resetAvatar(xid, hex_md5(xid));
		
		logThis('No avatar for: ' + xid, 2);
	}
	
	// Try to catch the avatar
	else {
		// Define some stuffs
		var type = $(xml).find('type').text();
		var binval = $(xml).find('binval').text();
		var checksum = $(xml).find('checksum').text();
		var updated = false;
		
		// Process the checksum of the avatar
		if((checksum == photo) || (photo == 'forget') || forced)
			updated = true;
		
		// If the avatar is yet stored and a new retrieving is not needed
		if((mode == 'cache') && type && binval && checksum && updated) {
			// Pending marker
			AVATAR_PENDING.push(xid);
			
			// Display the cache avatar
			displayAvatar(xid, hex_md5(xid), type, binval);
			
			logThis('Read avatar from cache: ' + xid, 3);
		}
		
		// Else if the request has not yet been fired, we get it
		else if((!updated || (mode == 'cache' && !updated) || (mode == 'force') || (photo = 'forget')) && (enabled != 'false')) {
			// Pending marker
			AVATAR_PENDING.push(xid);
			
			// Get the latest avatar
			var iq = new JSJaCIQ();
			iq.setType('get');
			iq.setTo(xid);
			
			iq.appendNode('vCard', {'xmlns': NS_VCARD});
			
			con.send(iq, handleAvatar);
			
			logThis('Get avatar from server: ' + xid, 3);
		}
	}
	
	return true;
}

// Handles the avatar
function handleAvatar(iq) {
	// Extract the XML values
	var handleXML = iq.getNode();
	var handleFrom = fullXID(getStanzaFrom(iq));
	
	// Is this me? Remove the resource!
	if(bareXID(handleFrom) == getXID())
		handleFrom = bareXID(handleFrom);
	
	// Get some other values
	var hash = hex_md5(handleFrom);
	var find = $(handleXML).find('vCard');
	var aChecksum = 'none';
	var oChecksum = null;
	
	// Read our own checksum
	if(handleFrom == getXID()) {
		oChecksum = getDB('checksum', 1);
		
		// Avoid the "null" value
		if(!oChecksum)
			oChecksum = '';
	}
	
	// vCard not empty?
	if(find.size()) {
		// We get our profile details
		if(handleFrom == getXID()) {
			// Get the names
			var names = generateBuddyName(iq);
			
			// Write the values to the database
			setDB('profile', 'name', names[0]);
			setDB('profile', 'nick', names[1]);
		}
		
		// We get the avatar
		var aType = find.find('TYPE:first').text();
		var aBinval = find.find('BINVAL:first').text();
		
		// No binval?
		if(!aBinval) {
			aType = 'none';
			aBinval = 'none';
		}
		
		// Enough data
		else {
			// No type?
			if(!aType)
				aType = 'image/png';
			
			// Process the checksum
			else
				aChecksum = hex_sha1(Base64.decode(aBinval));
		}
		
		// We display the user avatar
		displayAvatar(handleFrom, hash, aType, aBinval);
		
		// Store the avatar
		setPersistent('avatar', handleFrom, '<avatar><type>' + aType + '</type><binval>' + aBinval + '</binval><checksum>' + aChecksum + '</checksum><forced>false</forced></avatar>');
		
		logThis('Avatar retrieved from server: ' + handleFrom, 3);
	}
	
	// vCard is empty
	else
		resetAvatar(handleFrom);
	
	// We got a new checksum for us?
	if(((oChecksum != null) && (oChecksum != aChecksum)) || !FIRST_PRESENCE_SENT) {
		// Define a proper checksum
		var pChecksum = aChecksum;
		
		if(pChecksum == 'none')
			pChecksum = '';
		
		// Update our temp. checksum
		setDB('checksum', 1, pChecksum);
		
		// Send the stanza
		if(FIRST_PRESENCE_SENT)
			presenceSend(pChecksum);
		else
			getStorage(NS_OPTIONS);
	}
}

// Reset the avatar of an user
function resetAvatar(xid, hash) {
	// Store the empty avatar
	setPersistent('avatar', xid, '<avatar><type>none</type><binval>none</binval><checksum>none</checksum><forced>false</forced></avatar>');
	
	// Display the empty avatar
	displayAvatar(xid, hash, 'none', 'none');
}

// Displays the avatar of an user
function displayAvatar(xid, hash, type, binval) {
	// Initialize the vars
	var container = hash + ' .avatar-container';
	var code = '<img class="avatar" src="';
	
	// If the avatar exists
	if((type != 'none') && (binval != 'none'))
		code += 'data:' + type + ';base64,' + binval;
	else
		code += './img/others/default-avatar.png';
	
	code += '" alt="" />';
	
	// Replace with the new avatar (in the roster and in the chat)
	$('.' + container).html(code);
	
	// We can remove the pending marker
	removeArrayValue(AVATAR_PENDING, xid);
}
