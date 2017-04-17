/*

Jappix - An open social platform
These are the directory JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 03/03/11

*/

// Opens the directory popup
function openDirectory() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("User directory") + '</div>' + 
	
	'<div class="content">' + 
		'<div class="directory-head">' + 
			'<div class="directory-server-text">' + _e("Server to query") + '</div>' + 
			
			'<input name="directory-server-input" class="directory-server-input" value="' + encodeQuotes(HOST_VJUD) + '" />' + 
		'</div>' + 
		
		'<div class="results directory-results"></div>' + 
	'</div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish">' + _e("Close") + '</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('directory', html);
	
	// Associate the events
	launchDirectory();
	
	// Start a search!
	startDirectory();
	
	return false;
}

// Quits the directory popup
function closeDirectory() {
	// Destroy the popup
	destroyPopup('directory');
	
	return false;
}

// Launches a directory search
function startDirectory() {
	// Get the server to query
	var server = $('#directory .directory-server-input').val();
	
	// Launch the search!
	dataForm($('#directory .directory-server-input').val(), 'search', '', '', 'directory');
	
	logThis('Directory search launched: ' + server);
	
	return false;
}

// Plugin launcher
function launchDirectory() {
	// Click event
	$('#directory .bottom .finish').click(closeDirectory);
	
	// Keyboard event
	$('#directory .directory-server-input').keyup(function(e) {
		if(e.keyCode == 13) {
			// No value?
			if(!$(this).val())
				$(this).val(HOST_VJUD);
			
			// Start the directory search
			startDirectory();
			
			return false;
		}
	});
}
