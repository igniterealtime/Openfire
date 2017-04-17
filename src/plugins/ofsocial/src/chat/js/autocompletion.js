/*

Jappix - An open social platform
These are the autocompletion tools JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 12/11/10

*/

// Sort an array with insensitivity to the case
function caseInsensitiveSort(a, b) { 
	// Put the two strings into lower case
	a = a.toLowerCase();
	b = b.toLowerCase();
	
	// Process the sort
	if(a > b)
		return 1;
	if(a < b)
		return -1;
}

// Creates an array with the autocompletion results
function processAutocompletion(query, id) {
	// Replace forbidden characters in regex
	query = escapeRegex(query);
	
	// Create an empty array
	var results = new Array();
	
	// Search in the roster
	$('#' + id + ' .user').each(function() {
		var nick = $(this).find('.name').text();
		var regex = new RegExp('(^)' + query, 'gi');
		
		if(nick.match(regex))
			results.push(nick);
	});
	
	// Sort the array
	results = results.sort(caseInsensitiveSort);
	
	// Return the results array
	return results;
}

// Resets the autocompletion tools
function resetAutocompletion(hash) {
	$('#' + hash + ' .message-area').removeAttr('data-autocompletion-pointer').removeAttr('data-autocompletion-query');
}

// Autocompletes the chat input nick
function createAutocompletion(hash) {
	// Initialize
	var vSelector = $('#' + hash + ' .message-area');
	var value = vSelector.val();
	if(!value)
		resetAutocompletion(hash);
	var query = vSelector.attr('data-autocompletion-query');
	
	// The autocompletion has not been yet launched
	if(query == undefined) {
		query = value;
		vSelector.attr('data-autocompletion-query', query);
	}
	
	// Get the pointer
	var pointer = vSelector.attr('data-autocompletion-pointer');
	var i = 0;
	
	if(pointer)
		i = parseInt(pointer);
	
	// We get the nickname
	var nick = processAutocompletion(query, hash)[i];
	
	// Shit, this is my nick!
	if((nick != undefined) && (nick.toLowerCase() == getMUCNick(hash).toLowerCase())) {
		// Increment
		i++;
		
		// Get the next nick
		nick = processAutocompletion(query, hash)[i];
	}
	
	// We quote the nick
	if(nick != undefined) {
		// Increment
		i++;
		quoteMyNick(hash, nick);
		
		// Put a pointer
		vSelector.attr('data-autocompletion-pointer', i);
	}
}
