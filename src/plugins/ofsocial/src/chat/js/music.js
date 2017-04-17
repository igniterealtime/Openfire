/*

Jappix - An open social platform
These are the music JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 25/04/11

*/

// Opens the music bubble
function openMusic() {
	var path = '.music-content';
	
	// Show the music bubble
	showBubble(path);
	
	$(document).oneTime(10, function() {
		$(path + ' input').focus();
	});
	
	return false;
}

// Parses the music search XML
function parseMusic(xml, type) {
	var path = '.music-content ';
	var content = path + '.list';
	var path_type = content + ' .' + type;
	
	// Create the result container
	if(!exists(path_type)) {
		var code = '<div class="' + type + '"></div>';
		
		if(type == 'local')
			$(content).prepend(code);
		else
			$(content).append(code);
	}
	
	// Fill the results
	$(xml).find('track').each(function() {
		// Parse the XML
		var id = $(this).find('id').text();
		var title = $(this).find('name').text();
		var artist = $(this).find('artist').text();
		var source = $(this).find('source').text();
		var duration = $(this).find('duration').text();
		var uri = $(this).find('url').text();
		var mime = $(this).find('type').text();
		
		// No ID?
		if(!id)
			id = hex_md5(uri);
		
		// No MIME?
		if(!mime)
			mime = 'audio/ogg';
		
		// Local URL?
		if(type == 'local')
			uri = generateURL(uri);
		
		// Append the HTML code
		$(path_type).append('<a href="#" class="song" data-id="' + id + '">' + title + '</a>');
		
		// Current playing song?
		var current_song = $(path_type + ' a[data-id=' + id + ']');
		
		if(exists('.music-audio[data-id=' + id + ']'))
			current_song.addClass('playing');
		
		// Click event
		current_song.click(function() {
			return addMusic(id,  title, artist, source, duration, uri, mime, type);
		});
	});
	
	// The search is finished
	if(exists(content + ' .jamendo') && exists(content + ' .local')) {
		// Get the result values
		var jamendo = $(content + ' .jamendo').text();
		var local = $(content + ' .local').text();
		
		// Enable the input
		$(path + 'input').val('').removeAttr('disabled');
		
		// No result
		if(!jamendo && !local)
			$(path + '.no-results').show();
		
		// We must put a separator between the categories
		if(jamendo && local)
			$(content + ' .local').addClass('special');
	}
}

// Sends the music search requests
function searchMusic() {
	var path = '.music-content ';
	
	// We get the input string
	var string = $(path + 'input').val();
	
	// We lock the search input
	$(path + 'input').attr('disabled', true);
	
	// We reset the results
	$(path + '.list div').remove();
	$(path + '.no-results').hide();
	
	// Get the Jamendo results
	$.get('./php/music-search.php', {searchquery: string, location: 'jamendo'}, function(data) {
		parseMusic(data, 'jamendo');
	});
	
	// Get the local results
	$.get('./php/music-search.php', {searchquery: string, location: JAPPIX_LOCATION}, function(data) {
		parseMusic(data, 'local');
	});
}

// Performs an action on the music player
function actionMusic(action) {
	try {
		// Initialize
		var playThis = document.getElementById('top-content').getElementsByTagName('audio')[0];
		
		// Nothing to play, exit
		if(!playThis)
			return false;
		
		var stopButton = $('#top-content a.stop');
		
		// User play a song
		if(action == 'play') {
			stopButton.show();
			playThis.load();
			playThis.play();
			playThis.addEventListener('ended', function() {
				actionMusic('stop');
			}, true);  
			
			logThis('Music is now playing.');
		}
		
		// User stop the song or the song came to its end
		else if(action == 'stop') {
			stopButton.hide();
			playThis.pause();
			$('#top-content .music').removeClass('actived');
			$('.music-content .list a').removeClass('playing');
			$('.music-audio').remove();
			publishMusic();
			
			logThis('Music is now stopped.');
		}
	}
	
	catch(e) {}
	
	finally {
		return false;
	}
}

// Publishes the current title over PEP
function publishMusic(title, artist, source, duration, uri) {
	// We share the tune on PEP if enabled
	if(enabledPEP()) {
		/* REF: http://xmpp.org/extensions/xep-0118.html */
		
		var iq = new JSJaCIQ();
		iq.setType('set');
		
		// Create the main PubSub nodes
		var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
		var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_TUNE, 'xmlns': NS_PUBSUB}));
		var item = publish.appendChild(iq.buildNode('item', {'xmlns': NS_PUBSUB}));
		var tune = item.appendChild(iq.buildNode('tune', {'xmlns': NS_TUNE}));
		
		// Enough data?
		if(title || artist || source || uri) {
			// Data array
			var nodes = new Array(
					'title',
					'artist',
					'source',
					'length',
					'uri'
				    );
			
			var values = new Array(
					title,
					artist,
					source,
					length,
					uri
				     );
			
			// Create the children nodes
			for(i in nodes) {
				if(values[i])
					tune.appendChild(iq.buildNode(nodes[i], {'xmlns': NS_TUNE}, values[i]));
			}
		}
		
		con.send(iq);
		
		logThis('New tune sent: ' + title, 3);
	}
}

// Adds a music title to the results
function addMusic(id, title, artist, source, duration, uri, mime, type) {
	var path = '.music-content ';
	
	// We remove & create a new audio tag
	$('.music-audio').remove();
	$(path + '.player').prepend('<audio class="music-audio" type="' + mime + '" data-id="' + id + '" />');
	
	// We apply the new source to the player
	if(type == 'jamendo')
		$('.music-audio').attr('src', 'http://api.jamendo.com/get2/stream/track/redirect/?id=' + id + '&streamencoding=ogg2');
	else
		$('.music-audio').attr('src', uri);
	
	// We play the target sound
	actionMusic('play');
	
	// We set the actived class
	$('#top-content .music').addClass('actived');
	
	// We set a current played track indicator
	$(path + '.list a').removeClass('playing');
	$(path + 'a[data-id=' + id + ']').addClass('playing');
	
	// We publish what we listen
	publishMusic(title, artist, source, duration, uri);
	
	return false;
}

// Plugin launcher
function launchMusic() {
	// When music search string submitted
	$('.music-content input').keyup(function(e) {
		// Enter : send
		if(e.keyCode == 13 && $(this).val())
			searchMusic();
		
		// Escape : quit
		if(e.keyCode == 27)
			closeBubbles();
	});
}
