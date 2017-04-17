/*

Jappix - An open social platform
These are the temporary/persistent data store functions

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Temporary: returns whether it is available or not
function hasDB() {
	if(window.sessionStorage)
		return true;
	
	return false;
}

// Temporary: used to read a database entry
function getDB(type, id) {
	try {
		return sessionStorage.getItem(type + '_' + id);
	}
	
	catch(e) {
		logThis('Error while getting a temporary database entry (' + type + ' -> ' + id + '): ' + e, 1);
		
		return null;
	}
}

// Temporary: used to update a database entry
function setDB(type, id, value) {
	try {
		sessionStorage.setItem(type + '_' + id, value);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while writing a temporary database entry (' + type + ' -> ' + id + '): ' + e, 1);
		
		return false;
	}
}

// Temporary: used to remove a database entry
function removeDB(type, id) {
	try {
		sessionStorage.removeItem(type + '_' + id);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while removing a temporary database entry (' + type + ' -> ' + id + '): ' + e, 1);
		
		return false;
	}
}

// Temporary: used to check a database entry exists
function existDB(type, id) {
	var read = getDB(type, id);
	
	if(read != null)
		return true;
	
	return false;
}

// Temporary: used to clear all the database
function resetDB() {
	try {
		sessionStorage.clear();
		
		logThis('Temporary database cleared.', 3);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while clearing temporary database: ' + e, 1);
		
		return false;
	}
}

// Persistent: returns whether it is available or not
function hasPersistent() {
	if(window.localStorage)
		return true;
	
	return false;
}

// Persistent: used to read a database entry
function getPersistent(type, id) {
	try {
		return localStorage.getItem(type + '_' + id);
	}
	
	catch(e) {
		logThis('Error while getting a persistent database entry (' + type + ' -> ' + id + '): ' + e, 1);
		
		return null;
	}
}

// Persistent: used to update a database entry
function setPersistent(type, id, value) {
	try {
		localStorage.setItem(type + '_' + id, value);
		
		return true;
	}
	
	// Database might be full
	catch(e) {
		logThis('Retrying: could not write a persistent database entry (' + type + ' -> ' + id + '): ' + e, 2);
		
		// Flush it!
		flushPersistent();
		
		// Set the item again
		try {
			localStorage.setItem(type + '_' + id, value);
			
			return true;
		}
		
		// New error!
		catch(e) {
			logThis('Aborted: error while writing a persistent database entry (' + type + ' -> ' + id + '): ' + e, 1);
			
			return false;
		}
	}
}

// Persistent: used to remove a database entry
function removePersistent(type, id) {
	try {
		localStorage.removeItem(type + '_' + id);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while removing a persistent database entry (' + type + ' -> ' + id + '): ' + e, 1);
		
		return false;
	}
}

// Persistent: used to check a database entry exists
function existPersistent(type, id) {
	var read = getPersistent(type, id);
	
	if(read != null)
		return true;
	
	return false;
}

// Persistent: used to clear all the database
function resetPersistent() {
	try {
		localStorage.clear();
		
		logThis('Persistent database cleared.', 3);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while clearing persistent database: ' + e, 1);
		
		return false;
	}
}

// Persistent: used to flush the database
function flushPersistent() {
	try {
		// Get the stored session entry
		var session = getPersistent('session', 1);
		
		// Clear the persistent database
		localStorage.clear();
		
		// Restaure the stored session entry
		if(session)
			setPersistent('session', 1, session);
		
		logThis('Persistent database flushed.', 3);
		
		return true;
	}
	
	catch(e) {
		logThis('Error while flushing persistent database: ' + e, 1);
		
		return false;
	}
}
