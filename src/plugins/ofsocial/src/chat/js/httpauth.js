/*

Jappix - An open social platform
These are the http-auth JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 16/11/10

*/

// Replies to a HTTP request
function requestReply(value, xml) {
	// We parse the xml content
	var from = fullXID(getStanzaFrom(xml));
	var confirm = $(xml.getNode()).find('confirm');
	var xmlns = confirm.attr('xmlns');
	var id = confirm.attr('id');
	var method = confirm.attr('method');
	var url = confirm.attr('url');
	
	// We generate the reply message
	var aMsg = new JSJaCMessage();
	aMsg.setTo(from);
	
	// If "no"
	if(value == 'no') {
		aMsg.setType('error');
		aMsg.appendNode('error', {'code': '401', 'type': 'auth'});
	}
	
	// We set the confirm node
	aMsg.appendNode('confirm', {'xmlns': xmlns, 'url': url, 'id': id, 'method': method});
	
	// We send the message
	con.send(aMsg, handleErrorReply);
	
	logThis('Replying HTTP auth request: ' + from, 3);
}
