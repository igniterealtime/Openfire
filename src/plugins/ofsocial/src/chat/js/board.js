/*

Jappix - An open social platform
These are the notification board JS script for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 12/03/11

*/

// Creates a board panel
function createBoard(type, id) {
	// Text var
	var text = '';
	
	// Info
	if(type == 'info') {
		switch(id) {
			// Password change
			case 1:
				text = _e("Your password has been changed, now you can connect to your account with your new login data.");
				
				break;
			
			// Account deletion
			case 2:
				text = _e("Your XMPP account has been removed, bye!");
				
				break;
			
			// Account logout
			case 3:
				text = _e("You have been logged out of your XMPP account, have a nice day!");
				
				break;
			
			// Groupchat join
			case 4:
				text = _e("The room you joined seems not to exist. You should create it!");
				
				break;
			
			// Groupchat removal
			case 5:
				text = _e("The groupchat has been removed, now someone else will be able to recreate it.");
				
				break;
			
			// Non-existant groupchat user
			case 6:
				text = _e("The user that you want to reach is not present in the room.");
				
				break;
		}
	}
	
	// Error
	else {
		switch(id) {
			// Custom error
			case 1:
				text = '<b>' + _e("Error") + '</b> &raquo; <span></span>';
				
				break;
			
			// Network error
			case 2:
				text = _e("Jappix has been interrupted by a network issue, a bug or bad login (check that you entered the right credentials), sorry for the inconvenience.");
				
				break;
			
			// List retrieving error
			case 3:
				text = _e("The element list on this server could not be obtained!");
				
				break;
			
			// Attaching error
			case 4:
				text = printf(_e("An error occured while uploading your file: maybe it is too big (%s maximum) or forbidden!"), JAPPIX_MAX_UPLOAD);
				
				break;
		}
	}
	
	// No text?
	if(!text)
		return false;
	
	// Append the content
	$('#board').append('<div class="one-board ' + type + '" data-id="' + id + '">' + text + '</div>');
	
	// Events (click and auto-hide)
	$('#board .one-board.' + type + '[data-id=' + id + ']')
	
	.click(function() {
		closeThisBoard(this);
	})
	
	.oneTime('5s', function() {
		closeThisBoard(this);
	})
	
	.slideDown();
	
	return true;
}

// Destroys the existing board notifications
function destroyBoard() {
	$('#board').empty();
}

// Executes a given action on the notification board
function actionBoard(id, type) {
	// In a first, we destroy other boards
	destroyBoard();
	
	// Then we display the board
	createBoard(type, id);
}

// Opens a given error ID
function openThisError(id) {
	actionBoard(id, 'error');
}

// Opens a given info ID
function openThisInfo(id) {
	actionBoard(id, 'info');
}

// Closes a given board
function closeThisBoard(board) {
	$(board).slideUp('normal', function() {
		$(this).remove();
	});
}
