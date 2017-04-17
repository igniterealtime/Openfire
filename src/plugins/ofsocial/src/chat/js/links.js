/*

Jappix - An open social platform
These are the links JS script for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Maranda
Last revision: 24/06/11

*/

// Apply links in a string
function applyLinks(string, mode, style) {
	// Special stuffs
	var style, target;
	var prompt = "click to open screen share";	

	console.log("applyLinks");
	console.log(string);
	
	// Links style
	if(!style)
		style = '';
	else
		style = ' style="' + style + '"';
	
	// Open in new tabs
	if(mode != 'xhtml-im')
		target = ' target="_blank"';
	else
		target = '';


	if (string.indexOf("/ofmeet/?r=") > -1) {
		
		string = string.replace(/(\s|<br \/>|^|\()((https?|ftp|file|xmpp|irc|mailto|vnc|webcal|ssh|ldap|smb|magnet|spotify)(:)([^<>'"\s\)]+))/gim, '$1<a href="$2"' + target + style + '><b style="color:red;">Click here to join conference</b></a>');
	
	} else if (string.indexOf("onclick=") == -1) {
		// XMPP address
		string = string.replace(/(\s|<br \/>|^)(([a-zA-Z0-9\._-]+)@([a-zA-Z0-9\.\/_-]+))(\s|$)/gi, '$1<a href="xmpp:$2" target="_blank"' + style + '>$2</a>$5');

		// Simple link
		string = string.replace(/(\s|<br \/>|^|\()((https?|ftp|file|xmpp|irc|mailto|vnc|webcal|ssh|ldap|smb|magnet|spotify)(:)([^<>'"\s\)]+))/gim, '$1<a href="$2"' + target + style + '>$2</a>');
	}
	
	return string;
}
