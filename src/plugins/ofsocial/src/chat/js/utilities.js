/*

Jappix - An open social platform
These are the utilities JS script for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Olivier Migeot
Last revision: 24/06/11

*/

// Checks if a function exists
function functionExists(func) {
	if(typeof func == 'function')
		return true;
	
	return false;
}

// Returns whether using HTTPS or not
function isHTTPS() {
	if(window.location.href && (window.location.href).match(/^https/i))
		return true;
	
	return false;
}

// Generates the good storage URL
function generateURL(url) {
	// HTTPS not allowed
	if((HTTPS_STORAGE != 'on') && url.match(/^https(.+)/))
		url = 'http' + RegExp.$1;
	
	return url;
}

// Disables an input if needed
function disableInput(value, condition) {
	if(value == condition)
		return ' disabled=""';
	
	return '';
}

// Cuts a string
function cut(string, limit) {
	return string.substr(0, limit);
}

// Truncates a string
function truncate(string, limit) {
	// Must truncate the string
	if(string.length > limit)
		string = string.substr(0, limit) + '...';
	
	return string;
}

// Removes the new lines
function noLines(string) {
	return string.replace(/\n/g, ' ');
}

// Encodes a string for onclick attribute
function encodeOnclick(str) {
	return (encodeQuotes(str)).replace(/'/g, '\\$&');
}

// Checks if we are in the anonymous mode
function isAnonymous() {
	if(allowedAnonymous() && LINK_VARS['r'])
		return true;
	
	return false;
}

// Checks if this is a private chat user
function isPrivate(xid) {
	if(exists('[data-xid=' + escape(xid) + '][data-type=groupchat]'))
		return true;
	
	return false;
}

// Checks if the user browser is obsolete
function isObsolete() {
	// Get browser name & version
	var browser_name = BrowserDetect.browser;
	var browser_version = BrowserDetect.version;
	
	// No DOM storage
	if(!hasDB() || !hasPersistent())
		return true;
	
	// Obsolete IE
	if((browser_name == 'Explorer') && (browser_version < 8))
		return true;
	
	// Obsolete Chrome
	if((browser_name == 'Chrome') && (browser_version < 7))
		return true;
	
	// Obsolete Safari
	if((browser_name == 'Safari') && (browser_version < 4))
		return true;
	
	// Obsolete Firefox
	if((browser_name == 'Firefox') && (browser_version < 3.5))
		return true;
	
	// Obsolete Opera
	if((browser_name == 'Opera') && (browser_version < 9))
		return true;
	
	return false;
}

// Gets a MUC user XID
function getMUCUserXID(room, nick) {
	return $('div.chat[data-xid=' + escape(room) + '] div[data-nick=' + escape(nick) + ']').attr('data-xid');
}

// Gets a MUC user read XID
function getMUCUserRealXID(room, nick) {
	return $('div.chat[data-xid=' + escape(room) + '] div[data-nick=' + escape(nick) + ']').attr('data-realxid');
}

// Gets the server of the user
function getServer() {
	// Return the domain of the user
	return con.domain;
}

// Gets the password of the user
function getPassword() {
	// Return the password of the user
	return con.pass;
}

// Quotes the nick of an user
function quoteMyNick(hash, nick) {
	$(document).oneTime(10, function() {
		$('#page-engine #' + hash + ' .message-area').val(nick + ', ').focus();
	});
}

// Escapes a string for a regex usage
function escapeRegex(query) {
	return query.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
}

// Converts a XML document to a string
function xmlToString(xmlData) {
	try {
		// For Mozilla, Firefox, Opera, etc.
		if(window.XMLSerializer)
			return (new XMLSerializer()).serializeToString(xmlData);
		
		// For Internet Explorer
		if(window.ActiveXObject)
			return xmlData.xml;
		
		return null;
	}
	
	catch(e) {
		return null;
	}
}

// Converts a string to a XML document
function XMLFromString(sXML) {
	try {
		// No data?
		if(!sXML)
			return '';
		
		// Add the XML tag
		if(!sXML.match(/^<\?xml/i))
			sXML = '<?xml version="1.0"?>' + sXML;
		
		// Parse it!
		if(window.DOMParser)
			return (new DOMParser()).parseFromString(sXML, 'text/xml');
		
		if(window.ActiveXObject) {
			var oXML = new ActiveXObject('Microsoft.XMLDOM');
			oXML.loadXML(sXML);
			
	 		return oXML;
		}
	}
	
	catch(e) {
		return '';
	}
}

// Return the file category
function fileCategory(ext) {
	var cat;
	
	switch(ext) {
		// Images
		case 'jpg':
		case 'jpeg':
		case 'png':
		case 'bmp':
		case 'gif':
		case 'tif':
		case 'svg':
		case 'psp':
		case 'xcf':
			cat = 'image';
			
			break;
		
		// Videos
		case 'ogv':
		case 'ogg':
		case 'mkv':
		case 'avi':
		case 'mov':
		case 'mp4':
		case 'm4v':
		case 'wmv':
		case 'asf':
		case 'mpg':
		case 'mpeg':
		case 'ogm':
		case 'rmvb':
		case 'rmv':
		case 'qt':
		case 'flv':
		case 'ram':
		case '3gp':
		case 'avc':
			cat = 'video';
			
			break;
		
		// Sounds
		case 'oga':
		case 'mka':
		case 'flac':
		case 'mp3':
		case 'wav':
		case 'm4a':
		case 'wma':
		case 'rmab':
		case 'rma':
		case 'bwf':
		case 'aiff':
		case 'caf':
		case 'cda':
		case 'atrac':
		case 'vqf':
		case 'au':
		case 'aac':
		case 'm3u':
		case 'mid':
		case 'mp2':
		case 'snd':
		case 'voc':
			cat = 'audio';
			
			break;
		
		// Documents
		case 'pdf':
		case 'odt':
		case 'ott':
		case 'sxw':
		case 'stw':
		case 'ots':
		case 'sxc':
		case 'stc':
		case 'sxi':
		case 'sti':
		case 'pot':
		case 'odp':
		case 'ods':
		case 'doc':
		case 'docx':
		case 'docm':
		case 'xls':
		case 'xlsx':
		case 'xlsm':
		case 'xlt':
		case 'ppt':
		case 'pptx':
		case 'pptm':
		case 'pps':
		case 'odg':
		case 'otp':
		case 'sxd':
		case 'std':
		case 'std':
		case 'rtf':
		case 'txt':
		case 'htm':
		case 'html':
		case 'shtml':
		case 'dhtml':
		case 'mshtml':
			cat = 'document';
			
			break;
		
		// Packages
		case 'tgz':
		case 'gz':
		case 'tar':
		case 'ar':
		case 'cbz':
		case 'jar':
		case 'tar.7z':
		case 'tar.bz2':
		case 'tar.gz':
		case 'tar.lzma':
		case 'tar.xz':
		case 'zip':
		case 'xz':
		case 'rar':
		case 'bz':
		case 'deb':
		case 'rpm':
		case '7z':
		case 'ace':
		case 'cab':
		case 'arj':
		case 'msi':
			cat = 'package';
			
			break;
		
		// Others
		default:
			cat = 'other';
			
			break;
	}
	
	return cat;
}

// Registers Jappix as the default XMPP links handler
function xmppLinksHandler() {
	try {
		navigator.registerProtocolHandler('xmpp', JAPPIX_LOCATION + '?x=%s', SERVICE_NAME);
		
		return true;
	}
	
	catch(e) {
		return false;
	}
}

// Checks if a value exists in an array
function existArrayValue(array, value) {
	try {
		// Loop in the array
		for(i in array) {
			if(array[i] == value)
				return true;
		}
		
		return false;
	}
	
	catch(e) {
		return false;
	}
}

// Removes a value from an array
function removeArrayValue(array, value) {
	for(i in array) {
		// It matches, remove it!
		if(array[i] == value) {
			array.splice(i, 1);
			
			return true;
		}
	}
	
	return false;
}

// Converts a string to an array
function stringToArray(string) {
	var array = [];
	
	// Any string to convert?
	if(string) {
		// More than one item
		if(string.match(/,/gi)) {
			var string_split = string.split(',');
			
			for(i in string_split) {
				if(string_split[i])
					array.push(string_split[i]);
				else
					array.push('');
			}
		}
		
		// Only one item
		else
			array.push(string);
	}
	
	return array;
}

// Get the index of an array value
function indexArrayValue(array, value) {
	// Nothing?
	if(!array || !array.length)
		return 0;
	
	// Read the index of the value
	var index = 0;
	
	for(var i = 0; i < array.length; i++) {
		if(array[i] == value) {
			index = i;
			
			break;
		}
	}
	
	return index;
}
