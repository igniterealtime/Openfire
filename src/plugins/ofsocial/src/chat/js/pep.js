/*

Jappix - An open social platform
These are the PEP JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 13/05/11

*/

// Stores the PEP items
function storePEP(xid, type, value1, value2, value3, value4) {
	// Handle the correct values
	if(!value1)
		value1 = '';
	if(!value2)
		value2 = '';
	if(!value3)
		value3 = '';
	if(!value4)
		value4 = '';
	
	// If one value
	if(value1 || value2 || value3 || value4) {
		// Define the XML variable
		var xml = '<pep type="' + type + '">';
		
		// Generate the correct XML
		if(type == 'tune')
			xml += '<artist>' + value1.htmlEnc() + '</artist><title>' + value2.htmlEnc() + '</title><album>' + value3.htmlEnc() + '</album><uri>' + value4.htmlEnc() + '</uri>';
		else if(type == 'geoloc')
			xml += '<lat>' + value1.htmlEnc() + '</lat><lon>' + value2.htmlEnc() + '</lon><human>' + value3.htmlEnc() + '</human>';
		else
			xml += '<value>' + value1.htmlEnc() + '</value><text>' + value2.htmlEnc() + '</text>';
		
		// End the XML node
		xml += '</pep>';
		
		// Update the input with the new value
		setDB('pep-' + type, xid, xml);
	}
	
	else
		removeDB('pep-' + type, xid);
	
	// Display the PEP event
	displayPEP(xid, type);
}

// Displays a PEP item
function displayPEP(xid, type) {
	// Read the target input for values
	var value = $(XMLFromString(getDB('pep-' + type, xid)));
	var dText;
	var aLink = ''
	
	// If the PEP element exists
	if(type) {
		// Get the user hash
		var hash = hex_md5(xid);
		
		// Initialize
		var fText, fValue;
		var dText = '';
		
		// Parse the XML for mood and activity
		if((type == 'mood') || (type == 'activity')) {
			if(value) {
				var pepValue = value.find('value').text();
				var pepText = value.find('text').text();
				
				// No value?
				if(!pepValue)
					pepValue = 'none';
				
				// Apply the good values
				if(type == 'mood')
					fValue = moodIcon(pepValue);
				else if(type == 'activity')
					fValue = activityIcon(pepValue);
				if(!pepText)
					fText = _e("unknown");
				else
					fText = pepText;
			}
			
			else {
				if(type == 'mood')
					fValue = moodIcon('undefined');
				else if(type == 'activity')
					fValue = activityIcon('exercising');
				
				fText = _e("unknown");
			}
			
			dText = fText;
			fText = fText.htmlEnc();
		}
		
		else if(type == 'tune') {
			fValue = 'tune-note';
			
			if(value) {
				// Parse the tune XML
				var tArtist = value.find('artist').text();
				var tTitle = value.find('title').text();
				var tAlbum = value.find('album').text();
				var tURI = value.find('uri').text();
				var fArtist, fTitle, fAlbum, fURI;
				
				// Apply the good values
				if(!tArtist && !tAlbum && !tTitle) {
					fText = _e("unknown");
					dText = fText;
				}
				
				else {
					// URI element
					if(!tURI)
						fURI = 'http://grooveshark.com/search?q=' + encodeURIComponent(tArtist + ' ' + tTitle + ' ' + tAlbum);
					else
						fURI = tURI;
					
					// Artist element
					if(!tArtist)
						fArtist = _e("unknown");
					else
						fArtist = tArtist;
					
					// Title element
					if(!tTitle)
						fTitle = _e("unknown");
					else
						fTitle = tTitle;
					
					// Album element
					if(!tAlbum)
						fAlbum = _e("unknown");
					else
						fAlbum = tAlbum;
					
					// Generate the link to the title
					aLink = ' href="' + fURI + '" target="_blank"';
					
					// Generate the text to be displayed
					dText = fArtist + ' - ' + fTitle + ' (' + fAlbum + ')';
					fText =  '<a' + aLink + '>' + dText + '</a>';
				}
			}
			
			else {
				fText = _e("unknown");
				dText = fText;
			}
		}
		
		else if(type == 'geoloc') {
			fValue = 'location-world';
			
			if(value) {
				// Parse the geoloc XML
				var tLat = value.find('lat').text();
				var tLon = value.find('lon').text();
				var tHuman = value.find('human').text();
				var tReal = tHuman;
				
				// No human location?
				if(!tHuman)
					tHuman = _e("See his/her position on the globe");
				
				// Generate the text to be displayed
				if(tLat && tLon) {
					aLink = ' href="http://www.openstreetmap.org/?mlat=' + encodeQuotes(tLat) + '&amp;mlon=' + encodeQuotes(tLon) + '&amp;zoom=14" target="_blank"';
					fText = '<a' + aLink + '>' + tHuman.htmlEnc() + '</a>';
					
					if(tReal)
						dText = tReal;
					else
						dText = tLat + '; ' + tLon;
				}
				
				else {
					fText = _e("unknown");
					dText = fText;
				}
			}
			
			else {
				fText = _e("unknown");
				dText = fText;
			}
		}
		
		// Apply the text to the buddy infos
		var this_buddy = '#buddy-list .buddy[data-xid=' + escape(xid) + ']';
		
		if(exists(this_buddy))
			$(this_buddy + ' .bi-' + type).replaceWith('<p class="bi-' + type + ' talk-images ' + fValue + '" title="' + encodeQuotes(dText) + '">' + fText + '</p>');
		
		// Apply the text to the buddy chat
		if(exists('#' + hash)) {
			// Selector
			var bc_pep = $('#' + hash + ' .bc-pep');
			
			// We remove the old PEP item
			bc_pep.find('a.bi-' + type).remove();
			
			// If the new PEP item is not null, create a new one
			if(fText != _e("unknown"))
				bc_pep.prepend(
					'<a' + aLink + ' class="bi-' + type + ' talk-images ' + fValue + '" title="' + encodeQuotes(dText) + '"></a>'
				);
			
			// Process the new status position
			adaptChatPresence(hash);
		}
		
		// If this is the PEP values of the logged in user
		if(xid == getXID()) {
			// Change the icon/value of the target element
			if((type == 'mood') || (type == 'activity')) {
				// Change the input value
				var dVal = '';
				var dAttr = pepValue;
				
				// Must apply default values?
				if(pepValue == 'none') {
					if(type == 'mood')
						dAttr = 'happy';
					else
						dAttr = 'exercising';
				}
				
				// No text?
				if(dText != _e("unknown"))
					dVal = dText;
				
				// Store this user event in our database
				setDB(type + '-value', 1, dAttr);
				setDB(type + '-text', 1, dVal);
				
				// Apply this PEP event
				$('#my-infos .f-' + type + ' a.picker').attr('data-value', dAttr);
				$('#my-infos .f-' + type + ' input').val(dVal);
				$('#my-infos .f-' + type + ' input').placeholder();
			}
			
			else if((type == 'tune') || (type == 'geoloc')) {
				// Reset the values
				$('#my-infos .f-others a.' + type).remove();
				
				// Not empty?
				if(dText != _e("unknown")) {
					// Specific stuffs
					var href, title, icon_class;
					
					if(type == 'tune') {
						href = fURI;
						title = dText;
						icon_class = 'tune-note';
					}
					
					else {
						href = 'http://www.openstreetmap.org/?mlat=' + tLat + '&amp;mlon=' + tLon + '&amp;zoom=14';
						title = _e("Where are you?") + ' (' + dText + ')';
						icon_class = 'location-world';
					}
					
					// Must create the container?
					if(!exists('#my-infos .f-others'))
						$('#my-infos .content').append('<div class="element f-others"></div>');
					
					// Create the element
					$('#my-infos .f-others').prepend(
						'<a class="icon ' + type + '" href="' + encodeQuotes(href) + '" target="_blank" title="' + encodeQuotes(title) +  '">' + 
							'<span class="talk-images ' + icon_class + '"></span>' + 
						'</a>'
					);
				}
				
				// Empty?
				else if(!exists('#my-infos .f-others a.icon'))
					$('#my-infos .f-others').remove();
				
				// Process the buddy-list height again
				adaptRoster();
			}
		}
	}
}

// Changes the mood icon
function moodIcon(value) {
	// The main var
	var icon;
	
	// Switch the values
	switch(value) {
		case 'angry':
		case 'cranky':
		case 'hot':
		case 'invincible':
		case 'mean':
		case 'restless':
		case 'serious':
		case 'strong':
			icon = 'mood-one';
			break;
		
		case 'contemplative':
		case 'happy':
		case 'playful':
			icon = 'mood-two';
			break;
		
		case 'aroused':
		case 'envious':
		case 'excited':
		case 'interested':
		case 'lucky':
		case 'proud':
		case 'relieved':
		case 'satisfied':
		case 'shy':
			icon = 'mood-three';
			break;
		
		case 'calm':
		case 'cautious':
		case 'contented':
		case 'creative':
		case 'humbled':
		case 'lonely':
		case 'undefined':
		case 'none':
			icon = 'mood-four';
			break;
		
		case 'afraid':
		case 'amazed':
		case 'confused':
		case 'dismayed':
		case 'hungry':
		case 'in_awe':
		case 'indignant':
		case 'jealous':
		case 'lost':
		case 'offended':
		case 'outraged':
		case 'shocked':
		case 'surprised':
		case 'embarrassed':
		case 'impressed':
			icon = 'mood-five';
			break;
		
		case 'crazy':
		case 'distracted':
		case 'neutral':
		case 'relaxed':
		case 'thirsty':
			icon = 'mood-six';
			break;
		
		case 'amorous':
		case 'curious':
		case 'in_love':
		case 'nervous':
		case 'sarcastic':
			icon = 'mood-eight';
			break;
		
		case 'brave':
		case 'confident':
		case 'hopeful':
		case 'grateful':
		case 'spontaneous':
		case 'thankful':
			icon = 'mood-nine';
			break;
		
		default:
			icon = 'mood-seven';
			break;
	}
	
	// Return the good icon name
	return icon;
}

// Changes the activity icon
function activityIcon(value) {
	// The main var
	var icon;
	
	// Switch the values
	switch(value) {
		case 'doing_chores':
			icon = 'activity-doing_chores';
			break;
		
		case 'drinking':
			icon = 'activity-drinking';
			break;
		
		case 'eating':
			icon = 'activity-eating';
			break;
		
		case 'grooming':
			icon = 'activity-grooming';
			break;
		
		case 'having_appointment':
			icon = 'activity-having_appointment';
			break;
		
		case 'inactive':
			icon = 'activity-inactive';
			break;
		
		case 'relaxing':
			icon = 'activity-relaxing';
			break;
		
		case 'talking':
			icon = 'activity-talking';
			break;
		
		case 'traveling':
			icon = 'activity-traveling';
			break;
		
		case 'working':
			icon = 'activity-working';
			break;
		default:
			icon = 'activity-exercising';
			break;
	}
	
	// Return the good icon name
	return icon;
}

// Sends the user's mood
function sendMood(value, text) {
	/* REF: http://xmpp.org/extensions/xep-0107.html */
	
	// We propagate the mood on the xmpp network
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// We create the XML document
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_MOOD, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'xmlns': NS_PUBSUB}));
	var mood = item.appendChild(iq.buildNode('mood', {'xmlns': NS_MOOD}));
	
	if(value != 'none') {
		mood.appendChild(iq.buildNode(value, {'xmlns': NS_MOOD}));
		mood.appendChild(iq.buildNode('text', {'xmlns': NS_MOOD}, text));
	}
	
	// And finally we send the mood that is set
	con.send(iq);
	
	logThis('New mood sent: ' + value + ' (' + text + ')', 3);
}

// Sends the user's activity
function sendActivity(main, sub, text) {
	// We propagate the mood on the xmpp network
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// We create the XML document
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_ACTIVITY, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'xmlns': NS_PUBSUB}));
	var activity = item.appendChild(iq.buildNode('activity', {'xmlns': NS_ACTIVITY}));
	
	if(main != 'none') {
		var mainType = activity.appendChild(iq.buildNode(main, {'xmlns': NS_ACTIVITY}));
		
		// Child nodes
		if(sub)
			mainType.appendChild(iq.buildNode(sub, {'xmlns': NS_ACTIVITY}));
		if(text)
			activity.appendChild(iq.buildNode('text', {'xmlns': NS_ACTIVITY}, text));
	}
	
	// And finally we send the mood that is set
	con.send(iq);
	
	logThis('New activity sent: ' + main + ' (' + text + ')', 3);
}

// Sends the user's geographic position
function sendPosition(vLat, vLon, vAlt, vCountry, vCountrycode, vRegion, vPostalcode, vLocality, vStreet, vBuilding, vText, vURI) {
	/* REF: http://xmpp.org/extensions/xep-0080.html */
	
	// We propagate the position on pubsub
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// We create the XML document
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_GEOLOC, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'xmlns': NS_PUBSUB}));
	var geoloc = item.appendChild(iq.buildNode('geoloc', {'xmlns': NS_GEOLOC}));
	
	// Create two position arrays
	var pos_names  = ['lat', 'lon', 'alt', 'country', 'countrycode', 'region', 'postalcode', 'locality', 'street', 'building', 'text', 'uri', 'timestamp'];
	var pos_values = [ vLat,  vLon,  vAlt,  vCountry,  vCountrycode,  vRegion,  vPostalcode,  vLocality,  vStreet,  vBuilding,  vText,  vURI,  getXMPPTime('utc')];
	
	for(var i = 0; i < pos_names.length; i++) {
		if(pos_names[i] && pos_values[i])
			geoloc.appendChild(iq.buildNode(pos_names[i], {'xmlns': NS_GEOLOC}, pos_values[i]));
	}
	
	// And finally we send the XML
	con.send(iq);
	
	// For logger
	if(vLat && vLon)
		logThis('Geolocated.', 3);
	else
		logThis('Not geolocated.', 2);
}

// Parses the user's geographic position
function parsePosition(data) {
	var result = $(data).find('result:first');
	
	// Get latitude and longitude
	var lat = result.find('geometry:first location:first lat').text();
	var lng = result.find('geometry:first location:first lng').text();
	
	var array = [
	             lat,
	             lng,
	             result.find('address_component:has(type:contains("country")):first long_name').text(),
	             result.find('address_component:has(type:contains("country")):first short_name').text(),
	             result.find('address_component:has(type:contains("administrative_area_level_1")):first long_name').text(),
	             result.find('address_component:has(type:contains("postal_code")):first long_name').text(),
	             result.find('address_component:has(type:contains("locality")):first long_name').text(),
	             result.find('address_component:has(type:contains("route")):first long_name').text(),
	             result.find('address_component:has(type:contains("street_number")):first long_name').text(),
	             result.find('formatted_address:first').text(),
	             'http://www.openstreetmap.org/?mlat=' + lat + '&mlon=' + lng + '&zoom=14'
	            ];
	
	return array;
}

// Converts a position into an human-readable one
function humanPosition(tLocality, tRegion, tCountry) {
	var tHuman = '';
	
	// Any locality?
	if(tLocality) {
		tHuman += tLocality;
		
		if(tRegion)
			tHuman += ', ' + tRegion;
		if(tCountry)
			tHuman += ', ' + tCountry;
	}
	
	// Any region?
	else if(tRegion) {
		tHuman += tRegion;
		
		if(tCountry)
			tHuman += ', ' + tCountry;
	}
	
	// Any country?
	else if(tCountry)
		tHuman += tCountry;
	
	return tHuman;
}

// Gets the user's geographic position
function getPosition(position) {
	// Convert integers to strings
	var vLat = '' + position.coords.latitude;
	var vLon = '' + position.coords.longitude;
	var vAlt = '' + position.coords.altitude;
	
	// Get full position (from Google Maps API)
	$.get('./php/geolocation.php', {latitude: vLat, longitude: vLon, language: XML_LANG}, function(data) {
		// Parse data!
		var results = parsePosition(data);
		
		// Handled!
		sendPosition(
		             vLat,
		             vLon,
		             vAlt,
		             results[2],
		             results[3],
		             results[4],
		             results[5],
		             results[6],
		             results[7],
		             results[8],
		             results[9],
		             results[10]
		            );
		
		// Store data
		setDB('geolocation', 'now', xmlToString(data));
		
		logThis('Position details got from Google Maps API.');
	});
	
	logThis('Position got: latitude > ' + vLat + ' / longitude > ' + vLon + ' / altitude > ' + vAlt);
}

// Geolocates the user
function geolocate() {
	// We wait a bit...
	$('#my-infos').stopTime().oneTime('4s', function() {
		// We publish the user location if allowed (maximum cache age of 1 hour)
		if((getDB('options', 'geolocation') == '1') && enabledPEP() && navigator.geolocation) {
			navigator.geolocation.getCurrentPosition(getPosition);
			
			logThis('Geolocating...', 3);
		}
		
		else if(!navigator.geolocation)
			logThis('Not geolocated: browser does not support it.', 1);
		
		else
			logThis('Not geolocated.', 2);
	});
}

// Displays all the supported PEP events for a given XID
function displayAllPEP(xid) {
	displayPEP(xid, 'mood');
	displayPEP(xid, 'activity');
	displayPEP(xid, 'tune');
	displayPEP(xid, 'geoloc');
}

// Plugin launcher
function launchPEP() {
	// Apply empty values to the PEP database
	setDB('mood-value', 1, '');
	setDB('mood-text', 1, '');
	setDB('activity-value', 1, '');
	setDB('activity-text', 1, '');
	
	// Click event for user mood
	$('#my-infos .f-mood a.picker').click(function() {
		// Initialize some vars
		var path = '#my-infos .f-mood div.bubble';
		var mood_id = ['crazy', 'excited', 'playful', 'happy', 'shocked', 'hot', 'sad', 'amorous', 'confident'];
		var mood_lang = [_e("Crazy"), _e("Excited"), _e("Playful"), _e("Happy"), _e("Shocked"), _e("Hot"), _e("Sad"), _e("Amorous"), _e("Confident")];
		var mood_val = $('#my-infos .f-mood a.picker').attr('data-value');
		
		// Yet displayed?
		var can_append = true;
		
		if(exists(path))
			can_append = false;
		
		// Add this bubble!
		showBubble(path);
		
		if(!can_append)
			return false;
		
		// Generate the HTML code
		var html = '<div class="bubble removable">';
		
		for(i in mood_id) {
			// Yet in use: no need to display it!
			if(mood_id[i] == mood_val)
				continue;
			
			html += '<a href="#" class="talk-images" data-value="' + mood_id[i] + '" title="' + mood_lang[i] + '"></a>';
		}
		
		html += '</div>';
		
		// Append the HTML code
		$('#my-infos .f-mood').append(html);
		
		// Click event
		$(path + ' a').click(function() {
			// Update the mood marker
			$('#my-infos .f-mood a.picker').attr('data-value', $(this).attr('data-value'));
			
			// Close the bubble
			closeBubbles();
			
			// Focus on the status input
			$(document).oneTime(10, function() {
				$('#mood-text').focus();
			});
			
			return false;
		});
		
		return false;
	});
	
	// Click event for user activity
	$('#my-infos .f-activity a.picker').click(function() {
		// Initialize some vars
		var path = '#my-infos .f-activity div.bubble';
		var activity_id = ['doing_chores', 'drinking', 'eating', 'exercising', 'grooming', 'having_appointment', 'inactive', 'relaxing', 'talking', 'traveling', 'working'];
		var activity_lang = [_e("Chores"), _e("Drinking"), _e("Eating"), _e("Exercising"), _e("Grooming"), _e("Appointment"), _e("Inactive"), _e("Relaxing"), _e("Talking"), _e("Traveling"), _e("Working")];
		var activity_val = $('#my-infos .f-activity a.picker').attr('data-value');
		
		// Yet displayed?
		var can_append = true;
		
		if(exists(path))
			can_append = false;
		
		// Add this bubble!
		showBubble(path);
		
		if(!can_append)
			return false;
		
		// Generate the HTML code
		var html = '<div class="bubble removable">';
		
		for(i in activity_id) {
			// Yet in use: no need to display it!
			if(activity_id[i] == activity_val)
				continue;
			
			html += '<a href="#" class="talk-images" data-value="' + activity_id[i] + '" title="' + activity_lang[i] + '"></a>';
		}
		
		html += '</div>';
		
		// Append the HTML code
		$('#my-infos .f-activity').append(html);
		
		// Click event
		$(path + ' a').click(function() {
			// Update the activity marker
			$('#my-infos .f-activity a.picker').attr('data-value', $(this).attr('data-value'));
			
			// Close the bubble
			closeBubbles();
			
			// Focus on the status input
			$(document).oneTime(10, function() {
				$('#activity-text').focus();
			});
			
			return false;
		});
		
		return false;
	});
	
	// Submit events for PEP inputs
	$('#mood-text, #activity-text').placeholder()
	
	.keyup(function(e) {
		if(e.keyCode == 13) {
			$(this).blur();
			
			return false;
		}
	});
	
	// Input blur handler
	$('#mood-text').blur(function() {
		// Read the parameters
		var value = $('#my-infos .f-mood a.picker').attr('data-value');
		var text = $(this).val();
		
		// Must send the mood?
		if((value != getDB('mood-value', 1)) || (text != getDB('mood-text', 1))) {
			// Update the local stored values
			setDB('mood-value', 1, value);
			setDB('mood-text', 1, text);
			
			// Send it!
			sendMood(value, text);
		}
	})
	
	// Input focus handler
	.focus(function() {
		closeBubbles();
	});
	
	// Input blur handler
	$('#activity-text').blur(function() {
		// Read the parameters
		var value = $('#my-infos .f-activity a.picker').attr('data-value');
		var text = $(this).val();
		
		// Must send the activity?
		if((value != getDB('activity-value', 1)) || (text != getDB('activity-text', 1))) {
			// Update the local stored values
			setDB('activity-value', 1, value);
			setDB('activity-text', 1, text);
			
			// Send it!
			sendActivity(value, '', text);
		}
	})
	
	// Input focus handler
	.focus(function() {
		closeBubbles();
	});
}
