/*

Jappix - An open social platform
These are the popup JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 26/04/11

*/

// Creates a popup code
function createPopup(id, content) {
	// Popup exists?
	if(exists('#' + id))
		return false;
	
	// Append the popup code
	$('body').append(
		'<div id="' + id + '" class="lock removable">' + 
			'<div class="popup">' + 
				content + 
			'</div>' + 
		'</div>'
	);
	
	return true;
}

// Destroys a popup code
function destroyPopup(id) {
	// Stop the popup timers
	$('#' + id + ' *').stopTime();
	
	// Remove the popup
	$('#' + id).remove();
	
	// Manage input focus
	inputFocus();
}
