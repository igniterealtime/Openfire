/*

Jappix - An open social platform
These are the integratebox JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 08/05/11

*/

// Opens the integratebox popup
function openIntegrateBox() {
	// Popup HTML content
	var html = 
	'<div class="top">' + _e("Media viewer") + '</div>' + 
	
	'<div class="content"></div>' + 
	
	'<div class="bottom">' + 
		'<div class="wait wait-medium"></div>' + 
		
		'<a href="#" class="finish close">' + _e("Close") + '</a>' + 
		'<a href="#" class="finish next disabled" title="' + _e("Next") + '">&gt;</a>' + 
		'<a href="#" class="finish previous disabled" title="' + _e("Previous") + '">&lt;</a>' + 
	'</div>';
	
	// Create the popup
	createPopup('integratebox', html);
	
	// Associate the events
	launchIntegratebox();
}

// Closes the integratebox popup
function closeIntegrateBox() {
	// Destroy the popup
	destroyPopup('integratebox');
	
	return false;
}

// Generates the integratebox HTML code
function codeIntegrateBox(serv, url) {
	var code = '';
	
	// Protocol to use
	var protocol = 'http';
	
	if(isHTTPS())
		protocol = 'https';
	
	// Legacy browser
	var legacy = false;
	
	if((BrowserDetect.browser == 'Explorer') && (BrowserDetect.version < 9))
		legacy = true;
	
	// Switch to get the good DOM code
	switch(serv) {
		case 'youtube':
			if(legacy)
				code = '<object width="640" height="385"><param name="movie" value="http://www.youtube.com/v/' + url + '&amp;autoplay=1"></param><embed src="http://www.youtube.com/v/' + encodeQuotes(url) + '&amp;autoplay=1" type="application/x-shockwave-flash" width="640" height="385"></embed></object>';
			else
				code = '<object width="640" height="385" data="' + encodeQuotes(protocol) + '://www.youtube.com/embed/' + encodeQuotes(url) + '?autoplay=1" type="text/html"><a href="http://www.youtube.com/watch?v=' + encodeQuotes(url) + '" target="_blank">http://www.youtube.com/watch?v=' + encodeQuotes(url) + '</a></object>';
			
			break;
		
		case 'dailymotion':
			code = '<object width="640" height="385"><param name="movie" value="http://www.dailymotion.com/swf/video/' + url + '&amp;autoplay=1"></param><param name="allowFullScreen" value="false"></param><embed type="application/x-shockwave-flash" src="http://www.dailymotion.com/swf/video/' + encodeQuotes(url) + '&amp;autoplay=1" width="640" height="385" allowfullscreen="true" allowscriptaccess="always"></embed></object>';
			
			break;
		
		case 'vimeo':
			code = '<object width="640" height="385"><param name="allowfullscreen" value="true" /><param name="movie" value="http://vimeo.com/moogaloop.swf?clip_id=' + encodeQuotes(url) + '&amp;server=vimeo.com&amp;show_title=1&amp;show_byline=1&amp;show_portrait=0&amp;color=&amp;fullscreen=1&amp;autoplay=1" /><embed src="http://vimeo.com/moogaloop.swf?clip_id=' + encodeQuotes(url) + '&amp;server=vimeo.com&amp;show_title=1&amp;show_byline=1&amp;show_portrait=0&amp;color=&amp;fullscreen=1&amp;autoplay=1" type="application/x-shockwave-flash" allowfullscreen="true" allowscriptaccess="always" width="640" height="385"></embed></object>';
			
			break;
		
		case 'theora':
		case 'video':
			code = '<video width="640" height="385" src="' + encodeQuotes(url) + '" controls autoplay><a href="' + encodeQuotes(url) + '" target="_blank">' + encodeQuotes(url) + '</a></video>';
			
			break;
		
		case 'vorbis':
		case 'audio':
			code = '<audio src="' + encodeQuotes(url) + '" controls autoplay><a href="' + encodeQuotes(url) + '" target="_blank">' + encodeQuotes(url) + '</a></audio>';
			
			break;
		
		case 'image':
			code = '<a href="' + encodeQuotes(url) + '" target="_blank"><img alt="" src="' + encodeQuotes(url) + '" /></a>';
		
			break;
	}
	
	return code;
}

// Applies a given integratebox element
function applyIntegrateBox(url, service, url_list, services_list, comments_e_list, comments_n_list, width_style) {
	// Close the integratebox
	closeIntegrateBox();
	
	// Media integration not wanted?
	if(getDB('options', 'integratemedias') == '0')
		return true;
	
	// Apply the HTML code
	var dom_code = codeIntegrateBox(service, url);
	
	// Any code: apply it!
	if(dom_code) {
		// We show the integratebox
		openIntegrateBox();
		
		// We add the code to the DOM
		$('#integratebox .content').prepend('<div class="one-media">' + dom_code + '</div>');
		
		// Image waiting icon
		if(service == 'image') {
			var waitItem = $('#integratebox .wait');
			
			// Show it while it is loading
			waitItem.show();
			
			// Hide it when it is loaded
			$('#integratebox img').load(function() {
				waitItem.hide();
				
				// Center the image vertically
				$(this).css('margin-top', (($('#integratebox .content').height() - $(this).height()) / 2));
			});
		}
		
		// Large style?
		var comments_id = genID();
		
		if(width_style == 'large') {
			// Make the popup large
			$('#integratebox .popup').addClass('large');
			
			// Add the right content
			$('#integratebox .content').after(
				'<div class="comments" data-id="' + encodeQuotes(comments_id) + '">' + 
					'<div class="comments-content">' + 
						'<div class="one-comment loading"><span class="icon talk-images"></span>' + _e("Loading comments...") + '</div>' + 
					'</div>' + 
				'</div>'
			);
		}
		
		// Previous and next items?
		var url_array = stringToArray(url_list);
		var services_array = stringToArray(services_list);
		var comments_e_array = stringToArray(comments_e_list);
		var comments_n_array = stringToArray(comments_n_list);
		var index = indexArrayValue(url_array, url);
		
		// Any comments?
		if(exists('#integratebox .comments')) {
			if(comments_e_array[index] && comments_n_array[index])
				getCommentsMicroblog(comments_e_array[index], comments_n_array[index], comments_id);
			else
				$('#integratebox .comments .comments-content').html('<div class="one-comment loading"><span class="icon talk-images"></span>' + _e("Comments locked!") + '</div>');
		}
			
		// Get the previous values
		var previous_url = url_array[index - 1];
		var previous_services = services_array[index - 1];
		
		// Get the next values
		var next_url = url_array[index + 1];
		var next_services = services_array[index + 1];
		
		// Enable/disable buttons
		if(previous_url && previous_services)
			$('#integratebox .bottom .finish.previous').removeClass('disabled');
		else
			$('#integratebox .bottom .finish.previous').addClass('disabled');
		
		if(next_url && next_services)
			$('#integratebox .bottom .finish.next').removeClass('disabled');
		else
			$('#integratebox .bottom .finish.next').addClass('disabled');
		
		// Click events
		$('#integratebox .bottom .finish.previous, #integratebox .bottom .finish.next').click(function() {
			// Not acceptable?
			if($(this).is('.disabled'))
				return false;
			
			// Apply the event!
			if($(this).is('.previous'))
				applyIntegrateBox(previous_url, previous_services, url_list, services_list, comments_e_list, comments_n_list, width_style);
			else
				applyIntegrateBox(next_url, next_services, url_list, services_list, comments_e_list, comments_n_list, width_style);
			
			return false;
		});
		
		if(width_style == 'large')
			$('#integratebox .content a:has(img)').click(function() {
				if(next_url && next_services)
					applyIntegrateBox(next_url, next_services, url_list, services_list, comments_e_list, comments_n_list, width_style);
				
				return false;
			});
		
		return false;
	}
	
	// Nothing: return true to be able to open the URL in a new tab
	return true;
}

// Checks whether the file ext can use integratebox or not
function canIntegrateBox(ext) {
	// Can use?
	if(ext && ((ext == 'jpg') || (ext == 'jpeg') || (ext == 'png') || (ext == 'gif') || (ext == 'ogg') || (ext == 'oga') || (ext == 'ogv')))
		return true;
	
	return false;
}

// Filters a string to apply the integratebox links
function filterIntegrateBox(data) {
	// Encapsulates the string into two <div /> elements
	var xml = $('<div><div>' + data + '</div></div>').contents();
	
	// Loop the <a /> elements
	$(xml).find('a').each(function() {
		// Initialize this element
		var href = $(this).attr('href');
		var to, url, service, event;
		
		// XMPP ID
		if(href.match(/^xmpp:(.+)/i))
			to = RegExp.$1;
		
		// YouTube video box
		else if(href.match(/(\w{3,5})(:)(\S+)((\.youtube\.com\/watch(\?v|\?\S+v|\#\!v|\#\!\S+v)\=)|(youtu\.be\/))([^& ]+)((&amp;\S)|(&\S)|\s|$)/gim)) {
			url = RegExp.$8;
			service = 'youtube';
		}
		
		// Dailymotion video box
		else if(href.match(/(\w{3,5})(:)(\S+)\.dailymotion\.com\/video\/([\w\-]+)((\#[\w\-]+)|\s|$)/gim)) {
			url = RegExp.$4;
			service = 'dailymotion';
		}
		
		// Vimeo video box
		else if(href.match(/((\w{3,5})(:)(\S+)(vimeo|www\.vimeo)\.com\/([\w\-]+))/gim)) {
			url = RegExp.$1;
			service = 'vimeo';
		}
		
		// Theora video box
		else if(href.match(/((\w{3,5})(:)(\S+)(\.)(ogv|ogg))/gim)) {
			url = RegExp.$1;
			service = 'theora';
		}
		
		// Vorbis audio box
		else if(href.match(/((\w{3,5})(:)(\S+)(\.oga))/gim)) {
			url = RegExp.$1;
			service = 'vorbis';
		}
		
		// Image box
		else if(href.match(/((\w{3,5})(:)(\S+)(\.)(jpg|jpeg|png|gif|tif|bmp))/gim)) {
			url = RegExp.$1;
			service = 'image';
		}
		
		// Define the good event
		if(to)
			event = 'xmppLink(\'' + encodeOnclick(to) + '\')';
		else if(url && service)
			event = 'applyIntegrateBox(\'' + encodeOnclick(url) + '\', \'' + encodeOnclick(service) + '\')';
		
		// Any click event to apply?
		if(event) {
			// Regenerate the link element (for onclick)
			var new_a = '<a';
			var element_a = (this);
			
			// Attributes
			$(element_a.attributes).each(function(index) {
				// Read the current attribute
				var current_attr = element_a.attributes[index];
				
				// Apply the current attribute
				new_a += ' ' + encodeQuotes(current_attr.name) + '="' + encodeQuotes(current_attr.value) + '"';
			});
			
			// Add onclick attribute
			new_a += ' onclick="return ' + event + ';"';
			
			// Value
			new_a += '>' + $(this).text().htmlEnc() + '</a>';
			
			// Replace it!
			$(this).replaceWith(new_a);
		}
	});
	
	// Regenerate the HTML code (include string into a div to be readable)
	var string = $(xml).html();
	
	return string;
}

// Plugin launcher
function launchIntegratebox() {
	// Click event
	$('#integratebox .bottom .finish.close').click(closeIntegrateBox);
}

// Plugin keyup event
$(document).keyup(function(e) {
	// Previous item?
	if((exists('#integratebox .bottom .finish.previous:not(.disabled)')) && (e.keyCode == 37)) {
		$('#integratebox .bottom .finish.previous').click();
		
		return false;
	}
	
	// Next item?
	if((exists('#integratebox .bottom .finish.next:not(.disabled)')) && (e.keyCode == 39)) {
		$('#integratebox .bottom .finish.next').click();
		
		return false;
	}
});
