/*

Jappix - An open social platform
These are the IQ JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 21/03/11

*/

// Handles an incoming IQ packet
function handleIQ(iq) {
	// Gets the IQ content
	var iqNode = iq.getNode();
	var iqFrom = fullXID(getStanzaFrom(iq));
	var iqID = iq.getID();
	var iqQueryXMLNS = iq.getQueryXMLNS();
	var iqQuery = iq.getQuery();
	var iqType = iq.getType();
	
	// Build the response
	var iqResponse = new JSJaCIQ();
	
	iqResponse.setID(iqID);
	iqResponse.setTo(iqFrom);
	iqResponse.setType('result');
	
	// Software version query
	if((iqQueryXMLNS == NS_VERSION) && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0092.html */
		
		var iqQuery = iqResponse.setQuery(NS_VERSION);
		
		iqQuery.appendChild(iq.buildNode('name', {'xmlns': NS_VERSION}, 'Jappix'));
		iqQuery.appendChild(iq.buildNode('version', {'xmlns': NS_VERSION}, JAPPIX_VERSION));
		iqQuery.appendChild(iq.buildNode('os', {'xmlns': NS_VERSION}, BrowserDetect.OS));
		
		con.send(iqResponse);
		
		logThis('Received software version query: ' + iqFrom);
	}
	
	// Last activity query
	else if((iqQueryXMLNS == NS_LAST) && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0012.html */
		
		var iqQuery = iqResponse.setQuery(NS_LAST);
		iqQuery.setAttribute('seconds', getLastActivity());
		
		con.send(iqResponse);
		
		logThis('Received last activity query: ' + iqFrom);
	}
	
	// Privacy lists push
	else if((iqQueryXMLNS == NS_PRIVACY) && (iqType == 'set')) {
		// REF : http://xmpp.org/extensions/xep-0016.html
		
		// Roster push
		con.send(iqResponse);
		
		// Get the lists
		$(iqQuery).find('list').each(function() {
			getPrivacy($(this).attr('name'));
		});
		
		logThis('Received privacy lists push: ' + iqFrom);
	}
	
	// Roster push
	else if((iqQueryXMLNS == NS_ROSTER) && (iqType == 'set')) {
		// REF : http://xmpp.org/extensions/xep-0092.html
		
		// Roster push
		con.send(iqResponse);
		
		// Get the values
		$(iqQuery).find('item').each(function() {
			parseRoster($(this), 'presence');
		});
		
		logThis('Received roster push: ' + iqFrom);
	}
	
	// Roster Item Exchange query
	else if($(iqNode).find('x[xmlns=' + NS_ROSTERX + ']').size()) {
		// Open a new notification
		newNotification('rosterx', iqFrom, [iqNode], '');
		
		logThis('Roster Item Exchange from: ' + iqFrom);
	}
	
	// Disco info query
	else if((iqQueryXMLNS == NS_DISCO_INFO) && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0030.html */
		
		var iqQuery = iqResponse.setQuery(NS_DISCO_INFO);
		
		// We set the name of the client
		iqQuery.appendChild(iq.buildNode('identity', {
			'category': 'client',
			'type': 'web',
			'name': 'Jappix',
			'xmlns': NS_DISCO_INFO
		}));
		
		// We set all the supported features
		var fArray = myDiscoInfos();
		
		for(i in fArray)
			iqQuery.appendChild(iq.buildNode('feature', {'var': fArray[i], 'xmlns': NS_DISCO_INFO}));
		
		con.send(iqResponse);
		
		logThis('Received disco#infos query: ' + iqFrom);
	}
	
	// User time query
	else if($(iqNode).find('time').size() && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0202.html */
		
		var iqTime = iqResponse.appendNode('time', {'xmlns': NS_URN_TIME});
		iqTime.appendChild(iq.buildNode('tzo', {'xmlns': NS_URN_TIME}, getDateTZO()));
		iqTime.appendChild(iq.buildNode('utc', {'xmlns': NS_URN_TIME}, getXMPPTime('utc')));
		
		con.send(iqResponse);
		
		logThis('Received local time query: ' + iqFrom);
	}
	
	// Ping
	else if($(iqNode).find('ping').size() && (iqType == 'get')) {
		/* REF: http://xmpp.org/extensions/xep-0199.html */
		
		con.send(iqResponse);
		
		logThis('Received a ping: ' + iqFrom);
	}
	
	// Not implemented
	else if(!$(iqNode).find('error').size() && ((iqType == 'get') || (iqType == 'set'))) {
		// Append stanza content
		for(var i = 0; i < iqNode.childNodes.length; i++)
			iqResponse.getNode().appendChild(iqNode.childNodes.item(i).cloneNode(true));
		
		// Append error content
		var iqError = iqResponse.appendNode('error', {'xmlns': NS_CLIENT, 'code': '501', 'type': 'cancel'});
		iqError.appendChild(iq.buildNode('feature-not-implemented', {'xmlns': NS_STANZAS}));
		iqError.appendChild(iq.buildNode('text', {'xmlns': NS_STANZAS}, _e("The feature requested is not implemented by the recipient or server and therefore cannot be processed.")));
		
		con.send(iqResponse);
		
		logThis('Received an unsupported IQ query from: ' + iqFrom);
	}
}
