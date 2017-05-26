/*

Jappix - An open social platform
These are the discovery JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 03/03/11

*/

// Opens the discovery popup
function openDiscovery() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Service discovery") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="discovery-head">' + 
			'<div class="disco-server-text">' + _e("Server to query") + '</div>' + 
			
			'<input name="disco-server-input" class="disco-server-input" value="' + encodeQuotes(HOST_MAIN) + '" />' + 
		'</div>' + 
		
		'<div class="results discovery-results">' + 
			'<div class="disco-category disco-account">' + 
				'<p class="disco-category-title">' + _e("Accounts") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-auth">' + 
				'<p class="disco-category-title">' + _e("Authentications") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-automation">' + 
				'<p class="disco-category-title">' + _e("Automation") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-client">' + 
				'<p class="disco-category-title">' + _e("Clients") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-collaboration">' + 
				'<p class="disco-category-title">' + _e("Collaboration") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-component">' + 
				'<p class="disco-category-title">' + _e("Components") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-conference">' + 
				'<p class="disco-category-title">' + _e("Rooms") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-directory">' + 
				'<p class="disco-category-title">' + _e("Directories") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-gateway">' + 
				'<p class="disco-category-title">' + _e("Gateways") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-headline">' + 
				'<p class="disco-category-title">' + _e("News") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-hierarchy">' + 
				'<p class="disco-category-title">' + _e("Hierarchy") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-proxy">' + 
				'<p class="disco-category-title">' + _e("Proxies") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-pubsub">' + 
				'<p class="disco-category-title">' + _e("Publication/Subscription") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-server">' + 
				'<p class="disco-category-title">' + _e("Server") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-store">' + 
				'<p class="disco-category-title">' + _e("Storage") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-others">' + 
				'<p class="disco-category-title">' + _e("Others") + '</p>' + 
			'</div>' + 
			
			'<div class="disco-category disco-wait">' + 
				'<p class="disco-category-title">' + _e("Loading") + '</p>' + 
			'</div>' + 
		'</div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('discovery', html);
	
	// Associate the events
	launchDiscovery();
	
	// We request a disco to the default server
	startDiscovery();
	
	return false;
}

// Quits the discovery popup
function closeDiscovery() {
	// Destroy the popup
	destroyPopup('discovery');
	
	return false;
}

// Launches a discovery
function startDiscovery() {
	/* REF: http://xmpp.org/extensions/xep-0030.html */
	
	// We get the server to query
	var discoServer = $('#discovery .disco-server-input').val();
	
	// We launch the items query
	dataForm(discoServer, 'browse', '', '', 'discovery');
	
	logThis('Service discovery launched: ' + discoServer);
	
	return false;
}

// Cleans the discovery results
function cleanDiscovery() {
	// We remove the results
	$('#discovery .discovery-oneresult, #discovery .oneinstructions, #discovery .onetitle, #discovery .no-results').remove();
	
	// We clean the user info
	$('#discovery .disco-server-info').text('');
	
	// We hide the wait icon, the no result alert and the results
	$('#discovery .wait, #discovery .disco-category').hide();
}

// Plugin launcher
function launchDiscovery() {
	// Click event
	$('#discovery .bottom .finish').click(closeDiscovery);
	
	// Keyboard event
	$('#discovery .disco-server-input').keyup(function(e) {
		if(e.keyCode == 13) {
			// No value?
			if(!$(this).val())
				$(this).val(HOST_MAIN);
			
			// Start the discovery
			startDiscovery();
			
			return false;
		}
	});
}
