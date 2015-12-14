/*

Jappix - An open social platform
These are the microblog JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 18/06/11

*/

// Completes arrays of an entry's attached files
function attachedMicroblog(selector, tFName, tFURL, tFThumb, tFSource, tFType, tFLength, tFEComments, tFNComments) {
	if($(selector).attr('title'))
		tFName.push($(selector).attr('title'));
	else
		tFName.push('');
	
	if($(selector).attr('href'))
		tFURL.push($(selector).attr('href'));
	else
		tFURL.push('');
	
	if($(selector).find('link[rel=self][title=thumb]:first').attr('href'))
		tFThumb.push($(selector).find('link[rel=self][title=thumb]:first').attr('href'));
	else
		tFThumb.push('');
	
	if($(selector).attr('source'))
		tFSource.push($(selector).attr('source'));
	else
		tFSource.push('');
	
	if($(selector).attr('type'))
		tFType.push($(selector).attr('type'));
	else
		tFType.push('');
	
	if($(selector).attr('length'))
		tFLength.push($(selector).attr('length'));
	else
		tFLength.push('');
	
	// Comments?
	var comments_href_c = $(selector).find('link[rel=replies][title=comments_file]:first').attr('href');
	
	if(comments_href_c && comments_href_c.match(/^xmpp:(.+)\?;node=(.+)/)) {
		tFEComments.push(RegExp.$1);
		tFNComments.push(decodeURIComponent(RegExp.$2));
	}
	
	else {
		tFEComments.push('');
		tFNComments.push('');
	}
}

// Displays a given microblog item
function displayMicroblog(packet, from, hash, mode, way) {
	// Get some values
	var iParse = $(packet.getNode()).find('items item');
	
	iParse.each(function() {
		// Initialize
		var tTitle, tFiltered, tTime, tDate, tStamp, tBody, tName, tID, tHash, tIndividual, tFEClick;
		
		// Arrays
		var tFName = [];
		var tFURL = [];
		var tFThumb = [];
		var tFSource = [];
		var tFType = [];
		var tFLength = [];
		var tFEComments = [];
		var tFNComments = [];
		var aFURL = [];
		var aFCat = [];
		
		// Get the values
		tDate = $(this).find('published').text();
		tBody = $(this).find('body').text();
		tID = $(this).attr('id');
		tName = getBuddyName(from);
		tHash = 'update-' + hex_md5(tName + tDate + tID);
		
		// Read attached files with a thumb (place them at first)
		$(this).find('link[rel=enclosure]:has(link[rel=self][title=thumb])').each(function() {
			attachedMicroblog(this, tFName, tFURL, tFThumb, tFSource, tFType, tFLength, tFEComments, tFNComments);
		});
		
		// Read attached files without any thumb
		$(this).find('link[rel=enclosure]:not(:has(link[rel=self][title=thumb]))').each(function() {
			attachedMicroblog(this, tFName, tFURL, tFThumb, tFSource, tFType, tFLength, tFEComments, tFNComments);
		});
		
		// Get the repeat value
		var uRepeat = [$(this).find('source author name').text(), explodeThis(':', $(this).find('source author uri').text(), 1)];
		var uRepeated = false;
		
		if(!uRepeat[0])
			uRepeat = [getBuddyName(from), uRepeat[1]];
		if(!uRepeat[1])
			uRepeat = [uRepeat[0], from];
		
		// Repeated?
		if(uRepeat[1] != from)
			uRepeated = true;
		
		// Get the comments node
		var entityComments, nodeComments;
		
		// Get the comments
		var comments_href = $(this).find('link[title=comments]:first').attr('href');
		
		if(comments_href && comments_href.match(/^xmpp:(.+)\?;node=(.+)/)) {
			entityComments = RegExp.$1;
			nodeComments = decodeURIComponent(RegExp.$2);
		}
		
		// No comments node?
		if(!entityComments || !nodeComments) {
			entityComments = '';
			nodeComments = '';
		}
		
		// Get the stamp & time
		if(tDate) {
			tStamp = extractStamp(Date.jab2date(tDate));
			tTime = parseDate(tDate);
		}
		
		else {
			tStamp = getTimeStamp();
			tTime = '';
		}
		
		// Get the item geoloc
		var tGeoloc = '';
		var sGeoloc = $(this).find('geoloc[xmlns=' + NS_GEOLOC + ']:first');
		var gLat = sGeoloc.find('lat').text();
		var gLon = sGeoloc.find('lon').text();
		
		if(gLat && gLon) {
			tGeoloc += '<a class="geoloc talk-images" href="http://www.openstreetmap.org/?mlat=' + encodeQuotes(gLat) + '&amp;mlon=' + encodeQuotes(gLon) + '&amp;zoom=14" target="_blank">';
			
			// Human-readable name?
			var gHuman = humanPosition(
		                           sGeoloc.find('locality').text(),
		                           sGeoloc.find('region').text(),
		                           sGeoloc.find('country').text()
		                          );
			
			if(gHuman)
				tGeoloc += gHuman.htmlEnc();
			else
				tGeoloc += gLat.htmlEnc() + '; ' + gLon.htmlEnc();
			
			tGeoloc += '</a>';
		}
		
		// Retrieve the message body
		tTitle = $(this).find('content[type=text]').text();
		
		if(!tTitle) {
			// Legacy?
			tTitle = $(this).find('title:not(source > title)').text();
			
			// Last chance?
			if(!tTitle)
				tTitle = tBody;
		}
		
		// Trim the content
		tTitle = trim(tTitle);
		
		// Any content?
		if(tTitle) {
			// Apply links to message body
			tFiltered = filterThisMessage(tTitle, tName.htmlEnc(), true);
			
			// Display the received message
			var html = '<div class="one-update update_' + hash + ' ' + tHash + '" data-stamp="' + encodeQuotes(tStamp) + '" data-id="' + encodeQuotes(tID) + '" data-xid="' + encodeQuotes(from) + '">' + 
					'<div class="' + hash + '">' + 
						'<div class="avatar-container">' + 
							'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
						'</div>' + 
					'</div>' + 
					
					'<div class="body">' + 
						'<p>';
			
			// Is it a repeat?
			if(uRepeated)
				html += '<a href="#" class="repeat talk-images" title="' + encodeQuotes(printf(_e("This is a repeat from %s"), uRepeat[0] + ' (' + uRepeat[1] + ')')) + '" onclick="return checkChatCreate(\'' + encodeOnclick(uRepeat[1]) + '\', \'chat\');"></a>';
			
			html += '<b title="' + from + '" class="name">' + tName.htmlEnc() + '</b> <span>' + tFiltered + '</span></p>' + 
				'<p class="infos">' + tTime + tGeoloc + '</p>';
			
			// Any file to display?
			if(tFURL.length)
				html += '<p class="file">';
			
			// Generate an array of the files URL
			for(var a = 0; a < tFURL.length; a++) {
				// Not enough data?
				if(!tFURL[a])
					continue;
				
				// Push the current URL! (YouTube or file)
				if(tFURL[a].match(/(\w{3,5})(:)(\S+)((\.youtube\.com\/watch(\?v|\?\S+v|\#\!v|\#\!\S+v)\=)|(youtu\.be\/))([^& ]+)((&amp;\S)|(&\S)|\s|$)/gim)) {
					aFURL.push(trim(RegExp.$8));
					aFCat.push('youtube');
				}
				
				else if(canIntegrateBox(explodeThis('/', tFType[a], 1))) {
					aFURL.push(tFURL[a]);
					aFCat.push(fileCategory(explodeThis('/', tFType[a], 1)));
				}
			}
			
			// Add each file code
			for(var f = 0; f < tFURL.length; f++) {
				// Not enough data?
				if(!tFURL[f])
					continue;
				
				// Get the file type
				var tFExt = explodeThis('/', tFType[f], 1);
				var tFCat = fileCategory(tFExt);
				var tFLink = tFURL[f];
				
				// Youtube video?
				if(tFLink.match(/(\w{3,5})(:)(\S+)((\.youtube\.com\/watch(\?v|\?\S+v|\#\!v|\#\!\S+v)\=)|(youtu\.be\/))([^& ]+)((&amp;\S)|(&\S)|\s|$)/gim)) {
					tFLink = trim(RegExp.$8);
					tFCat = 'youtube';
				}
				
				// Supported image/video/sound
				if(canIntegrateBox(tFExt) || (tFCat == 'youtube'))
					tFEClick = 'onclick="return applyIntegrateBox(\'' + encodeOnclick(tFLink) + '\', \'' + encodeOnclick(tFCat) + '\', \'' + encodeOnclick(aFURL) + '\', \'' + encodeOnclick(aFCat) + '\', \'' + encodeOnclick(tFEComments) + '\', \'' + encodeOnclick(tFNComments) + '\', \'large\');" ';
				else
					tFEClick = '';
				
				// Any thumbnail?
				if(tFThumb[f])
					html += '<a class="thumb" ' + tFEClick + 'href="' + encodeQuotes(tFURL[f]) + '" target="_blank" title="' + encodeQuotes(tFName[f]) + '" data-node="' + encodeQuotes(tFNComments) + '"><img src="' + encodeQuotes(tFThumb[f]) + '" alt="" /></a>';
				else
					html += '<a class="' + encodeQuotes(tFCat) + ' link talk-images" ' + tFEClick + 'href="' + encodeQuotes(tFURL[f]) + '" target="_blank" data-node="' + encodeQuotes(tFNComments) + '">' + tFName[f].htmlEnc() + '</a>';
			}
			
			if(tFURL.length)
				html += '</p>';
			
			// It's my own notice, we can remove it!
			if(from == getXID())
				html += '<a href="#" onclick="return removeMicroblog(\'' + encodeOnclick(tID) + '\', \'' + encodeOnclick(tHash) + '\');" title="' + _e("Remove this notice") + '" class="mbtool remove talk-images"></a>';
			
			// Notice from another user
			else {
				// User profile
				html += '<a href="#" title="' + _e("View profile") + '" class="mbtool profile talk-images" onclick="return openUserInfos(\'' + encodeOnclick(from) + '\');"></a>';
				
				// If PEP is enabled
				if(enabledPEP())
					html += '<a href="#" title="' + _e("Repeat this notice") + '" class="mbtool repost talk-images"></a>';
			}
			
			html += '</div><div class="comments-container" data-node="' + encodeQuotes(nodeComments) + '"></div></div>';
			
			// Mixed mode
			if((mode == 'mixed') && !exists('.mixed .' + tHash)) {
				// Remove the old element
				if(way == 'push')
					$('#channel .content.mixed .one-update.update_' + hash).remove();
				
				// Get the nearest element
				var nearest = sortElementByStamp(tStamp, '#channel .mixed .one-update');
				
				// Append the content at the right position (date relative)
				if(nearest == 0)
					$('#channel .content.mixed').append(html);
				else
					$('#channel .one-update[data-stamp=' + nearest + ']:first').before(html);
				
				// Show the new item
				if(way == 'push')
					$('#channel .content.mixed .one-update.' + tHash).fadeIn('fast');
				else
					$('#channel .content.mixed .one-update.' + tHash).show();
				
				// Remove the old notices to make the DOM lighter
				var oneUpdate = '#channel .content.mixed .one-update';
				
				if($(oneUpdate).size() > 80)
					$(oneUpdate + ':last').remove();
				
				// Click event on avatar/name
				$('.mixed .' + tHash + ' .avatar-container, .mixed .' + tHash + ' .body b').click(function() {
					getMicroblog(from, hash);
				});
			}
			
			// Individual mode
			tIndividual = '#channel .content.individual.microblog-' + hash;
			
			// Can append individual content?
			var can_individual = true;
			
			if($('#channel .top.individual input[name=comments]').val() && exists(tIndividual + ' .one-update'))
				can_individual = false;
			
			if(can_individual && exists(tIndividual) && !exists('.individual .' + tHash)) {
				if(mode == 'mixed')
					$(tIndividual).prepend(html);
				else
					$(tIndividual + ' a.more').before(html);
				
				// Show the new item
				if(way == 'push')
					$('#channel .content.individual .one-update.' + tHash).fadeIn('fast');
				else
					$('#channel .content.individual .one-update.' + tHash).show();
				
				// Make 'more' link visible
				$(tIndividual + ' a.more').css('visibility', 'visible');
				
				// Click event on name (if not me!)
				if(from != getXID())
					$('.individual .' + tHash + ' .avatar-container, .individual .' + tHash + ' .body b').click(function() {
						checkChatCreate(from, 'chat');
					});
			}
			
			// Apply the click event
			$('.' + tHash + ' a.repost:not([data-event=true])').click(function() {
				return publishMicroblog(tTitle, tFName, tFURL, tFType, tFLength, tFThumb, uRepeat, entityComments, nodeComments, tFEComments, tFNComments);
			})
			
			.attr('data-event', 'true');
			
			// Apply the hover event
			if(nodeComments)
				$('.' + mode + ' .' + tHash).hover(function() {
					showCommentsMicroblog($(this), entityComments, nodeComments, tHash);
				}, function() {
					if($(this).find('div.comments a.one-comment.loading').size())
						$(this).find('div.comments').remove();
				});
		}
	});
	
	// Display the avatar of this buddy
	getAvatar(from, 'cache', 'true', 'forget');
}

// Removes a given microblog item
function removeMicroblog(id, hash) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#publisher-delete */
	
	// Initialize
	var selector = $('.' + hash);
	var get_last = false;
	
	// Get the latest item for the mixed mode
	if(exists('#channel .content.mixed .' + hash))
		get_last = true;
	
	// Remove the item from our DOM
	selector.fadeOut('fast', function() {
		$(this).remove();
	});
	
	// Send the IQ to remove the item (and get eventual error callback)
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var retract = pubsub.appendChild(iq.buildNode('retract', {'node': NS_URN_MBLOG, 'xmlns': NS_PUBSUB}));
	retract.appendChild(iq.buildNode('item', {'id': id, 'xmlns': NS_PUBSUB}));
	
	if(get_last)
		con.send(iq, handleRemoveMicroblog);
	else
		con.send(iq, handleErrorReply);
	
	return false;
}

// Handles the microblog item removal
function handleRemoveMicroblog(iq) {
	// Handle the error reply
	handleErrorReply(iq);
	
	// Get the latest item
	requestMicroblog(getXID(), '1', false, handleUpdateRemoveMicroblog);
}

// Handles the microblog update
function handleUpdateRemoveMicroblog(iq) {
	// Error?
	if(iq.getType() == 'error')
		return;
	
	// Initialize
	var xid = bareXID(getStanzaFrom(iq));
	var hash = hex_md5(xid);
	
	// Display the item!
	displayMicroblog(iq, xid, hash, 'mixed', 'push');
}

// Gets a given microblog comments node
function getCommentsMicroblog(server, node, id) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#subscriber-retrieve-requestall */
	
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setID('get_' + genID() + '-' + id);
	iq.setTo(server);
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	pubsub.appendChild(iq.buildNode('items', {'node': node, 'xmlns': NS_PUBSUB}));
	
	con.send(iq, handleCommentsMicroblog);
	
	return false;
}

// Handles a microblog comments node items
function handleCommentsMicroblog(iq) {
	// Path
	var id = explodeThis('-', iq.getID(), 1);
	var path = 'div.comments[data-id=' + id + '] div.comments-content';
	
	// Does not exist?
	if(!exists(path))
		return false;
	
	// Any error?
	if(handleErrorReply(iq)) {
		$(path).html('<div class="one-comment loading">' + _e("Could not get the comments!") + '</div>');
		
		return false;
	}
	
	// Initialize
	var data = iq.getNode();
	var server = bareXID(getStanzaFrom(iq));
	var node = $(data).find('items:first').attr('node');
	var users_xid = [];
	var code = '';
	
	// No node?
	if(!node)
		node = $(data).find('publish:first').attr('node');
	
	// Get the parent microblog item
	var parent_select = $('#channel .one-update:has(*[data-node=' + node + '])');
	var parent_data = [parent_select.attr('data-xid'), NS_URN_MBLOG, parent_select.attr('data-id')];
	
	// Get the owner XID
	var owner_xid = parent_select.attr('data-xid');
	
	// Must we create the complete DOM?
	var complete = true;
	
	if($(path).find('.one-comment.compose').size())
		complete = false;
	
	// Add the comment tool
	if(complete)
		code += '<div class="one-comment compose">' + 
				'<span class="icon talk-images"></span><input type="text" placeholder="' + _e("Type your comment here...") + '" />' + 
			'</div>';
	
	// Append the comments
	$(data).find('item').each(function() {
		// Get comment
		var current_id = $(this).attr('id');
		var current_xid = explodeThis(':', $(this).find('source author uri').text(), 1);
		var current_name = $(this).find('source author name').text();
		var current_date = $(this).find('published').text();
		var current_body = $(this).find('content[type=text]').text();
		var current_bname = getBuddyName(current_xid);
		
		// Legacy?
		if(!current_body)
			current_body = $(this).find('title:not(source > title)').text();
		
		// Yet displayed? (continue the loop)
		if($(path).find('.one-comment[data-id=' + current_id + ']').size())
			return;
		
		// No XID?
		if(!current_xid) {
			current_xid = '';
			
			if(!current_name)
				current_name = _e("unknown");
		}
		
		else if(!current_name || (current_bname != getXIDNick(current_xid)))
			current_name = current_bname;
		
		// Any date?
		if(current_date)
			current_date = relativeDate(current_date);
		else
			current_date = getCompleteTime();
		
		// Click event
		var onclick = 'false';
		
		if(current_xid != getXID())
			onclick = 'checkChatCreate(\'' + encodeOnclick(current_xid) + '\', \'chat\')';
		
		// If this is my comment, add a marker
		var type = 'him';
		var marker = '';
		var remove = '';
		
		if(current_xid == getXID()) {
			type = 'me';
			marker = '<div class="marker"></div>';
			remove = '<a href="#" class="remove" onclick="return removeCommentMicroblog(\'' + encodeOnclick(server) + '\', \'' + encodeOnclick(node) + '\', \'' + encodeOnclick(current_id) + '\');">' + _e("Remove") + '</a>';
		}
		
		// New comment?
		var new_class = '';
		
		if(!complete)
			new_class = ' new';
		
		// Add the comment
		if(current_body) {
			// Add the XID
			if(!existArrayValue(users_xid, current_xid))
				users_xid.push(current_xid);
			
			// Add the HTML code
			code += '<div class="one-comment ' + hex_md5(current_xid) + ' ' + type + new_class + '" data-id="' + encodeQuotes(current_id) + '">' + 
					marker + 
					
					'<div class="avatar-container" onclick="return ' + onclick + ';">' + 
						'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
					'</div>' + 
					
					'<div class="comment-container">' + 
						'<a href="#" onclick="return ' + onclick + ';" title="' + encodeQuotes(current_xid) + '" class="name">' + current_name.htmlEnc() + '</a>' + 
						'<span class="date">' + current_date.htmlEnc() + '</span>' + 
						remove + 
					
						'<p class="body">' + filterThisMessage(current_body, current_name, true) + '</p>' + 
					'</div>' + 
					
					'<div class="clear"></div>' + 
				'</div>';
		}
	});
	
	// Add the HTML
	if(complete) {
		$(path).html(code);
		
		// Focus on the compose input
		$(document).oneTime(10, function() {
			$(path).find('.one-comment.compose input').focus();
		});
	}
	
	else {
		$(path).find('.one-comment.compose').after(code);
		
		// Beautiful effect
		$(path).find('.one-comment.new').slideDown('fast', function() {
			adaptCommentMicroblog(id);
		}).removeClass('new');
	}
	
	// Set the good widths
	adaptCommentMicroblog(id);
	
	// Get the avatars
	for(a in users_xid)
		getAvatar(users_xid[a], 'cache', 'true', 'forget');
	
	// Add the owner XID
	if(owner_xid && owner_xid.match('@') && !existArrayValue(users_xid, owner_xid))
		users_xid.push(owner_xid);
	
	// Remove my own XID
	removeArrayValue(users_xid, getXID());
	
	// DOM events
	if(complete) {
		// Update timer
		$(path).everyTime('60s', function() {
			getCommentsMicroblog(server, node, id);
			
			logThis('Updating comments node: ' + node + ' on ' + server + '...');
		});
		
		// Input key event
		$(path).find('.one-comment.compose input').placeholder()
			             .keyup(function(e) {
			             		if((e.keyCode == 13) && $(this).val()) {
			             			// Send the comment!
			             			sendCommentMicroblog($(this).val(), server, node, id, users_xid, parent_data);
			             			
			             			// Reset the input value
			             			$(this).val('');
			             			
			             			return false;
			             		}
			             });
	}
}

// Shows the microblog comments box
function showCommentsMicroblog(path, entityComments, nodeComments, tHash) {
	// Do not display it twice!
	if(path.find('div.comments').size())
		return;
	
	// Generate an unique ID
	var idComments = genID();
	
	// Create comments container
	path.find('div.comments-container').append(
		'<div class="comments" data-id="' + encodeQuotes(idComments) + '">' + 
			'<div class="arrow talk-images"></div>' + 
			'<div class="comments-content">' + 
				'<a href="#" class="one-comment loading"><span class="icon talk-images"></span>' + _e("Show comments") + '</a>' + 
			'</div>' + 
		'</div>'
	);
	
	// Click event
	path.find('div.comments a.one-comment').click(function() {
		// Set loading info
		$(this).parent().html('<div class="one-comment loading"><span class="icon talk-images"></span>' + _e("Loading comments...") + '</div>');
		
		// Request comments
		getCommentsMicroblog(entityComments, nodeComments, idComments);
		
		// Remove the comments from the DOM if click away
		if(tHash) {
			$('#channel').die('click');
			
			$('#channel').live('click', function(evt) {
				if(!$(evt.target).parents('.' + tHash).size()) {
					$('#channel').die('click');
					$('#channel .one-update div.comments-content').stopTime();
					$('#channel .one-update div.comments').remove();
				}
			});
		}
		
		return false;
	});
}

// Sends a comment on a given microblog comments node
function sendCommentMicroblog(value, server, node, id, notifiy_arr, parent_data) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#publisher-publish */
	
	// Not enough data?
	if(!value || !server || !node)
		return false;
	
	// Get some values
	var date = getXMPPTime('utc');
	var hash = hex_md5(value + date);
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('set');
	iq.setTo(server);
	iq.setID('set_' + genID() + '-' + id);
	
	// PubSub main elements
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': node, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'id': hash, 'xmlns': NS_PUBSUB}));
	var entry = item.appendChild(iq.buildNode('entry', {'xmlns': NS_ATOM}));
	
	// Author infos
	var Source = entry.appendChild(iq.buildNode('source', {'xmlns': NS_ATOM}));
	var author = Source.appendChild(iq.buildNode('author', {'xmlns': NS_ATOM}));
	author.appendChild(iq.buildNode('name', {'xmlns': NS_ATOM}, getName()));
	author.appendChild(iq.buildNode('uri', {'xmlns': NS_ATOM}, 'xmpp:' + getXID()));
	
	// Create the comment
	entry.appendChild(iq.buildNode('content', {'type': 'text', 'xmlns': NS_ATOM}, value));
	entry.appendChild(iq.buildNode('published', {'xmlns': NS_ATOM}, date));
	
	con.send(iq);
	
	// Handle this comment!
	iq.setFrom(server);
	handleCommentsMicroblog(iq);
	
	// Notify users
	if(notifiy_arr && notifiy_arr.length) {
		// XMPP link to the item
		var href = 'xmpp:' + server + '?;node=' + encodeURIComponent(node) + ';item=' + encodeURIComponent(hash);
		
		// Loop!
		for(n in notifiy_arr)
			sendNotification(notifiy_arr[n], 'comment', href, value, parent_data);
	}
	
	return false;
}

// Removes a given microblog comment item
function removeCommentMicroblog(server, node, id) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#publisher-delete */
	
	// Remove the item from our DOM
	$('.one-comment[data-id=' + id + ']').slideUp('fast', function() {
		// Get the parent ID
		var parent_id = $(this).parents('div.comments').attr('data-id');
		
		// Remove it!
		$(this).remove();
		
		// Adapt the width
		adaptCommentMicroblog(parent_id);
	});
	
	// Send the IQ to remove the item (and get eventual error callback)
	var iq = new JSJaCIQ();
	iq.setType('set');
	iq.setTo(server);
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var retract = pubsub.appendChild(iq.buildNode('retract', {'node': node, 'xmlns': NS_PUBSUB}));
	retract.appendChild(iq.buildNode('item', {'id': id, 'xmlns': NS_PUBSUB}));
	
	con.send(iq);
	
	return false;
}

// Adapts the comment elements width
function adaptCommentMicroblog(id) {
	var selector = $('div.comments[data-id=' + id + '] div.comments-content');
	var selector_width = selector.width();
	
	// Change widths
	selector.find('.one-comment.compose input').css('width', selector_width - 60);
	selector.find('.one-comment .comment-container').css('width', selector_width - 55);
}

// Handles the microblog of an user
function handleMicroblog(iq) {
	// Get the from attribute of this IQ
	var from = bareXID(getStanzaFrom(iq));
	
	// Define the selector path
	var selector = '#channel .top.individual input[name=';
	
	// Is this request still alive?
	if(from == $(selector + 'jid]').val()) {
		var hash = hex_md5(from);
		
		// Update the items counter
		var old_count = parseInt($(selector + 'counter]').val());
		$(selector + 'counter]').val(old_count + 20);
		
		// Display the microblog
		displayMicroblog(iq, from, hash, 'individual', 'request');
		
		// Hide the waiting icon
		if(enabledPEP())
			waitMicroblog('sync');
		else
			waitMicroblog('unsync');
		
		// Hide the 'more items' link?
		if($(iq.getNode()).find('item').size() < old_count)
			$('#channel .individual a.more').remove();
		
		// Get the comments?
		var comments_node = $('#channel .top.individual input[name=comments]').val();
		
		if(comments_node && comments_node.match(/^xmpp:(.+)\?;node=(.+);item=(.+)/)) {
			// Get the values
			var comments_entity = RegExp.$1;
			comments_node = decodeURIComponent(RegExp.$2);
			
			// Selectors
			var file_link = $('#channel .individual .one-update p.file a[data-node=' + comments_node + ']');
			var entry_link = $('#channel .individual .one-update:has(*[data-node=' + comments_node + '])');
			
			// Is it a file?
			if(file_link.size())
				file_link.click();
			
			// Is it a microblog entry?
			else if(entry_link.size()) {
				showCommentsMicroblog(entry_link, comments_entity, comments_node);
				entry_link.find('a.one-comment').click();
			}
		}
	}
	
	logThis('Microblog got: ' + from, 3);
}

// Resets the microblog elements
function resetMicroblog() {
	// Reset everything
	$('#channel .individual .one-update div.comments-content').stopTime();
	$('#channel .individual').remove();
	$('#channel .mixed').show();
	
	// Hide the waiting icon
	if(enabledPEP())
		waitMicroblog('sync');
	else
		waitMicroblog('unsync');
	
	return false;
}

// Gets the user's microblog to check it exists
function getInitMicroblog() {
	getMicroblog(getXID(), hex_md5(getXID()), true);
}

// Handles the user's microblog to create it in case of error
function handleInitMicroblog(iq) {
	// Any error?
	if((iq.getType() == 'error') && $(iq.getNode()).find('item-not-found').size()) {
		// The node may not exist, create it!
		setupMicroblog('', NS_URN_MBLOG, '1', '1000000', '', '', true);
		
		logThis('Error while getting microblog, trying to reconfigure the Pubsub node!', 2);
	}
}

// Requests an user's microblog
function requestMicroblog(xid, items, get_item, handler) {
	// Ask the server the user's microblog 
	var iq = new JSJaCIQ();
	iq.setType('get');
	iq.setTo(xid);
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var ps_items = pubsub.appendChild(iq.buildNode('items', {'node': NS_URN_MBLOG, 'xmlns': NS_PUBSUB}));
	
	// Request a particular item?
	if(get_item)
		ps_items.appendChild(iq.buildNode('item', {'id': get_item, 'xmlns': NS_PUBSUB}));
	else
		ps_items.setAttribute('max_items', items);
	
	if(handler)
		con.send(iq, handler);
	else
		con.send(iq, handleMicroblog);
	
	return false;
}

// Gets the microblog of an user
function getMicroblog(xid, hash, check) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#subscriber-retrieve */
	
	logThis('Get the microblog: ' + xid, 3);
	
	// Fire the wait event
	waitMicroblog('fetch');
	
	// XMPP URI?
	var get_item = '';
	
	if(xid.match(/^xmpp:(.+)\?;node=(.+);item=(.+)/)) {
		xid = RegExp.$1;
		get_item = decodeURIComponent(RegExp.$3);
	}
	
	// No hash?
	if(!hash)
		hash = hex_md5(xid);
	
	// Can display the individual channel?
	if(!check && !exists('#channel .individual')) {
		// Hide the mixed channel
		$('#channel .mixed').hide();
		
		// Get the channel title depending on the XID
		var cTitle;
		var cShortcuts = '';
		
		if(xid == getXID())
			cTitle = _e("Your channel");
		else {
			cTitle = _e("Channel of") + ' ' + getBuddyName(xid).htmlEnc();
			cShortcuts = '<div class="shortcuts">' + 
						'<a href="#" class="message talk-images" title="' + _e("Send him/her a message") + '" onclick="return composeInboxMessage(\'' + encodeOnclick(xid) + '\');"></a>' + 
						'<a href="#" class="chat talk-images" title="' + _e("Start a chat with him/her") + '" onclick="return checkChatCreate(\'' + encodeOnclick(xid) + '\', \'chat\');"></a>' + 
						'<a href="#" class="command talk-images" title="' + _e("Command") + '" onclick="return retrieveAdHoc(\'' + encodeOnclick(xid) + '\');"></a>' + 
						'<a href="#" class="profile talk-images" title="' + _e("Show user profile") + '" onclick="return openUserInfos(\'' + encodeOnclick(xid) + '\');"></a>' + 
			             '</div>';
		}
		
		// Create a new individual channel
		$('#channel .content.mixed').after(
				'<div class="content individual microblog-' + hash + '">' + 
					'<a href="#" class="more home-images" onclick="return getMicroblog(\'' + encodeOnclick(xid) + '\', \'' + encodeOnclick(hash) + '\');">' + _e("More notices...") + '</a>' + 
				'</div>'
						 )
					   
					   .before(
				'<div class="top individual ' + hash + '">' + 
					'<div class="avatar-container">' + 
						'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
					'</div>' + 
					
					'<div class="update">' + 
						'<h2>' + cTitle + '</h2>' + 
						'<a href="#" onclick="return resetMicroblog();">« ' + _e("Previous") + '</a>' + 
					'</div>' + 
					
					cShortcuts + 
					
					'<input type="hidden" name="jid" value="' + encodeQuotes(xid) + '" />' + 
					'<input type="hidden" name="counter" value="20" />' + 
				'</div>'
						 );
		
		// Display the user avatar
		getAvatar(xid, 'cache', 'true', 'forget');
	}
	
	// Get the number of items to retrieve
	var items = '0';
	
	if(!check)
		items = $('#channel .top.individual input[name=counter]').val();
	
	// Request
	if(check)
		requestMicroblog(xid, items, get_item, handleInitMicroblog);
	else
		requestMicroblog(xid, items, get_item, handleMicroblog);
}

// Show a given microblog waiting status
function waitMicroblog(type) {
	// First hide all the infos elements
	$('#channel .footer div').hide();
	
	// Display the good one
	$('#channel .footer div.' + type).show();
	
	// Depending on the type, disable/enable certain tools
	var selector = $('#channel .top input[name=microblog_body]');
	
	if(type == 'unsync')
		selector.attr('disabled', true);
	else if(type == 'sync')
		$(document).oneTime(10, function() {
			selector.removeAttr('disabled').focus();
		});
}

// Setups a new microblog
function setupMicroblog(entity, node, persist, maximum, access, publish, create) {
	/* REF: http://xmpp.org/extensions/xep-0060.html#owner-create-and-configure */
	
	// Create the PubSub node
	var iq = new JSJaCIQ();
	iq.setType('set');
	
	// Any external entity?
	if(entity)
		iq.setTo(entity);
	
	// Create it?
	if(create) {
		var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
		pubsub.appendChild(iq.buildNode('create', {'xmlns': NS_PUBSUB, 'node': node}));
	}
	
	else
		var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB_OWNER});
	
	// Configure it!
	var configure = pubsub.appendChild(iq.buildNode('configure', {'node': node, 'xmlns': NS_PUBSUB}));
	var x = configure.appendChild(iq.buildNode('x', {'xmlns': NS_XDATA, 'type': 'submit'}));
	
	var field1 = x.appendChild(iq.buildNode('field', {'var': 'FORM_TYPE', 'type': 'hidden', 'xmlns': NS_XDATA}));
	field1.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, NS_PUBSUB_NC));
	
	// Persist items?
	if(persist) {
		var field2 = x.appendChild(iq.buildNode('field', {'var': 'pubsub#persist_items', 'xmlns': NS_XDATA}));
		field2.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, persist));
	}
	
	// Maximum items?
	if(maximum) {
		var field3 = x.appendChild(iq.buildNode('field', {'var': 'pubsub#max_items', 'xmlns': NS_XDATA}));
		field3.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, maximum));
	}
	
	// Access rights?
	if(access) {
		var field4 = x.appendChild(iq.buildNode('field', {'var': 'pubsub#access_model', 'xmlns': NS_XDATA}));
		field4.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, access));
	}
	
	// Publish rights?
	if(publish) {
		var field5 = x.appendChild(iq.buildNode('field', {'var': 'pubsub#publish_model', 'xmlns': NS_XDATA}));
		field5.appendChild(iq.buildNode('value', {'xmlns': NS_XDATA}, publish));
	}
	
	con.send(iq);
}

// Gets the microblog configuration
function getConfigMicroblog() {
	// Lock the microblog options
	$('#persistent, #maxnotices').attr('disabled', true);
	
	// Get the microblog configuration
	var iq = new JSJaCIQ();
	iq.setType('get');
	
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB_OWNER});
	pubsub.appendChild(iq.buildNode('configure', {'node': NS_URN_MBLOG, 'xmlns': NS_PUBSUB_OWNER}));
	
	con.send(iq, handleGetConfigMicroblog);
}

// Handles the microblog configuration
function handleGetConfigMicroblog(iq) {
	// Reset the options stuffs
	waitOptions('microblog');
	
	// Unlock the microblog options
	$('#persistent, #maxnotices').removeAttr('disabled');
	
	// End if not a result
	if(!iq || (iq.getType() != 'result'))
		return;
	
	// Initialize the values
	var selector = $(iq.getNode());
	var persistent = '0';
	var maxnotices = '1000000';
	
	// Get the values
	var xPersistent = selector.find('field[var=pubsub#persist_items] value:first').text();
	var xMaxnotices = selector.find('field[var=pubsub#max_items] value:first').text();
	
	// Any value?
	if(xPersistent)
		persistent = xPersistent;
	
	if(xMaxnotices)
		maxnotices = xMaxnotices;
	
	// Change the maxnotices value
	switch(maxnotices) {
		case '1':
		case '100':
		case '1000':
		case '10000':
		case '100000':
		case '1000000':
			break;
		
		default:
			maxnotices = '1000000';
			break;
	}
	
	// Apply persistent value
	if(persistent == '0')
		$('#persistent').attr('checked', false);
	else
		$('#persistent').attr('checked', true);
	
	// Apply maxnotices value
	$('#maxnotices').val(maxnotices);
}

// Handles the user's microblog
function handleMyMicroblog(packet) {
	// Reset the entire form
	$('#channel .top input[name=microblog_body]').removeAttr('disabled').val('');
	$('#channel .top input[name=microblog_body]').placeholder();
	unattachMicroblog();
	
	// Check for errors
	handleErrorReply(packet);
}

// Performs the microblog sender checks
function sendMicroblog() {
	logThis('Send a new microblog item', 3);
	
	// Avoid nasty errors
	try {
		// Get the values
		var selector = $('#channel .top input[name=microblog_body]');
		var body = trim(selector.val());
		
		// Sufficient parameters
		if(body) {
			// Disable & blur our input
			selector.attr('disabled', true).blur();
			
			// Files array
			var fName = [];
			var fType = [];
			var fLength = [];
			var fURL = [];
			var fThumb = [];
			
			// Read the files
			$('#attach .one-file').each(function() {
				// Push the values!
				fName.push($(this).find('a.link').text());
				fType.push($(this).attr('data-type'));
				fLength.push($(this).attr('data-length'));
				fURL.push($(this).find('a.link').attr('href'));
				fThumb.push($(this).attr('data-thumb'));
			});
			
			// Containing YouTube videos?
			var yt_matches = body.match(/(\w{3,5})(:)(\S+)((\.youtube\.com\/watch(\?v|\?\S+v|\#\!v|\#\!\S+v)\=)|(youtu\.be\/))([^& ]+)((&amp;\S)|(&\S)|\s|$)/gim);
			
			for(y in yt_matches) {
				fName.push('');
				fType.push('text/html');
				fLength.push('');
				fURL.push(trim(yt_matches[y]));
				fThumb.push('https://img.youtube.com/vi/' + trim(yt_matches[y].replace(/(\w{3,5})(:)(\S+)((\.youtube\.com\/watch(\?v|\?\S+v|\#\!v|\#\!\S+v)\=)|(youtu\.be\/))([^& ]+)((&amp;\S)|(&\S)|\s|$)/gim, '$8')) + '/0.jpg');
			}
			
			// Send the message on the XMPP network
			publishMicroblog(body, fName, fURL, fType, fLength, fThumb);
		}
	}
	
	// Return false (security)
	finally {
		return false;
	}
}

// Publishes a given microblog item
function publishMicroblog(body, attachedname, attachedurl, attachedtype, attachedlength, attachedthumb, repeat, comments_entity, comments_node, comments_entity_file, comments_node_file) {
	/* REF: http://xmpp.org/extensions/xep-0277.html */
	
	// Generate some values
	var time = getXMPPTime('utc');
	var id = hex_md5(body + time);
	var nick = getName();
	var xid = getXID();
	
	// Define repeat options
	var author_nick = nick;
	var author_xid = xid;
	
	if(repeat && repeat.length) {
		author_nick = repeat[0];
		author_xid = repeat[1];
	}
	
	// Define comments options
	var node_create = false;
	
	if(!comments_entity || !comments_node) {
		node_create = true;
		comments_entity = HOST_PUBSUB;
		comments_node = NS_URN_MBLOG + ':comments/' + id;
	}
	
	if(!comments_entity_file)
		comments_entity_file = [];
	if(!comments_node_file)
		comments_node_file = [];
	
	// New IQ
	var iq = new JSJaCIQ();
	iq.setType('set');
	iq.setTo(xid);
	
	// Create the main XML nodes/childs
	var pubsub = iq.appendNode('pubsub', {'xmlns': NS_PUBSUB});
	var publish = pubsub.appendChild(iq.buildNode('publish', {'node': NS_URN_MBLOG, 'xmlns': NS_PUBSUB}));
	var item = publish.appendChild(iq.buildNode('item', {'id': id, 'xmlns': NS_PUBSUB}));
	var entry = item.appendChild(iq.buildNode('entry', {'xmlns': NS_ATOM}));
	
	// Create the XML source childs
	var Source = entry.appendChild(iq.buildNode('source', {'xmlns': NS_ATOM}));
	var author = Source.appendChild(iq.buildNode('author', {'xmlns': NS_ATOM}));
	author.appendChild(iq.buildNode('name', {'xmlns': NS_ATOM}, author_nick));
	author.appendChild(iq.buildNode('uri', {'xmlns': NS_ATOM}, 'xmpp:' + author_xid));
	
	// Create the XML entry childs
	entry.appendChild(iq.buildNode('content', {'type': 'text', 'xmlns': NS_ATOM}, body));
	entry.appendChild(iq.buildNode('published', {'xmlns': NS_ATOM}, time));
	entry.appendChild(iq.buildNode('updated', {'xmlns': NS_ATOM}, time));
	entry.appendChild(iq.buildNode('link', {
			'rel': 'alternate',
			'href': 'xmpp:' + xid + '?;node=' + encodeURIComponent(NS_URN_MBLOG) + ';item=' + encodeURIComponent(id),
			'xmlns': NS_ATOM
	}));
	
	// Create the attached files nodes
	for(var i = 0; i < attachedurl.length; i++) {
		// Not enough data?
		if(!attachedurl[i])
			continue;
		
		// Append a new file element
		var file = entry.appendChild(iq.buildNode('link', {'xmlns': NS_ATOM, 'rel': 'enclosure', 'href': attachedurl[i]}));
		
		// Add attributes
		if(attachedname[i])
			file.setAttribute('title', attachedname[i]);
		if(attachedtype[i])
			file.setAttribute('type', attachedtype[i]);
		if(attachedlength[i])
			file.setAttribute('length', attachedlength[i]);
		
		// Any thumbnail?
		if(attachedthumb[i])
			file.appendChild(iq.buildNode('link', {'xmlns': NS_URN_MBLOG, 'rel': 'self', 'title': 'thumb', 'type': attachedtype[i], 'href': attachedthumb[i]}));
		
		// Any comments node?
		if(!comments_entity_file[i] || !comments_node_file[i]) {
			// Generate values
			comments_entity_file[i] = HOST_PUBSUB;
			comments_node_file[i] = NS_URN_MBLOG + ':comments/' + hex_md5(attachedurl[i] + attachedname[i] + attachedtype[i] + attachedlength[i] + time);
			
			// Create the node
			setupMicroblog(comments_entity_file[i], comments_node_file[i], '1', '1000000', 'open', 'open', true);
		}
		
		file.appendChild(iq.buildNode('link', {'xmlns': NS_URN_MBLOG, 'rel': 'replies', 'title': 'comments_file', 'href': 'xmpp:' + comments_entity_file[i] + '?;node=' + encodeURIComponent(comments_node_file[i])}));
	}
	
	// Create the comments child
	entry.appendChild(iq.buildNode('link', {'xmlns': NS_ATOM, 'rel': 'replies', 'title': 'comments', 'href': 'xmpp:' + comments_entity + '?;node=' + encodeURIComponent(comments_node)}));
	
	// Create the geoloc child
	var geoloc_xml = getDB('geolocation', 'now');
	
	if(geoloc_xml) {
		// Create two position arrays
		var geo_names  = ['lat', 'lon', 'country', 'countrycode', 'region', 'postalcode', 'locality', 'street', 'building', 'text', 'uri', 'timestamp'];
		var geo_values = parsePosition(XMLFromString(geoloc_xml));
		
		// New geoloc child
		var geoloc = entry.appendChild(iq.buildNode('geoloc', {'xmlns': NS_GEOLOC}));
		
		// Append the geoloc content
		for(var g = 0; g < geo_names.length; g++) {
			if(geo_names[g] && geo_values[g])
				geoloc.appendChild(iq.buildNode(geo_names[g], {'xmlns': NS_GEOLOC}, geo_values[g]));
		}
	}
	
	// Send the IQ
	con.send(iq, handleMyMicroblog);
	
	// Create the XML comments PubSub nodes
	if(node_create)
		setupMicroblog(comments_entity, comments_node, '1', '1000000', 'open', 'open', true);
	
	return false;
}

// Attaches a file to a microblog post
function attachMicroblog() {
	// File upload vars
	var attach_options = {
		dataType:	'xml',
		beforeSubmit:	waitMicroblogAttach,
		success:	handleMicroblogAttach
	};
	
	// Upload form submit event
	$('#attach').submit(function() {
		if(!exists('#attach .wait') && $('#attach input[type=file]').val())
			$(this).ajaxSubmit(attach_options);
		
		return false;
	});
	
	// Upload input change event
	$('#attach input[type=file]').change(function() {
		if(!exists('#attach .wait') && $(this).val())
			$('#attach').ajaxSubmit(attach_options);
		
		return false;
	});
}

// Unattaches a microblog file
function unattachMicroblog(id) {
	// Individual removal?
	if(id)
		$('#attach .one-file[data-id=' + id + ']').remove();
	else
		$('#attach .one-file').remove();
	
	// Must enable the popup again?
	if(!exists('#attach .one-file')) {
		// Restore the bubble class
		$('#attach').addClass('bubble');
		
		// Enable the bubble click events
		if(id) {
			$('#attach').hide();
			showBubble('#attach');
		}
		
		else
			closeBubbles();
	}
	
	return false;
}

// Wait event for file attaching
function waitMicroblogAttach() {
	// Append the wait icon
	$('#attach input[type=submit]').after('<div class="wait wait-medium"></div>');
	
	// Lock the bubble
	$('#attach').removeClass('bubble');
}

// Success event for file attaching
function handleMicroblogAttach(responseXML) {
	// Data selector
	var dData = $(responseXML).find('jappix');
	
	// Process the returned data
	if(dData.find('error').size()) {
		openThisError(4);
		
		// Unlock the bubble?
		if(!exists('#attach .one-file')) {
			$('#attach').addClass('bubble').hide();
			
			// Show the bubble again!
			showBubble('#attach');
		}
		
		logThis('Error while attaching the file: ' + dData.find('error').text(), 1);
	}
	
	else {
		// Do not allow this bubble to be hidden
		$('#attach').removeClass('bubble');
		
		// Get the file values
		var fName = dData.find('title').text();
		var fType = dData.find('type').text();
		var fLength = dData.find('length').text();
		var fURL = dData.find('href').text();
		var fThumb = dData.find('thumb').text();
		
		// Generate a file ID
		var fID = hex_md5(fURL);
		
		// Add this file
		$('#attach .attach-subitem').append(
			'<div class="one-file" data-type="' + encodeQuotes(fType) + '" data-length="' + encodeQuotes(fLength) + '" data-thumb="' + encodeQuotes(fThumb) + '" data-id="' + fID + '">' + 
				'<a class="remove talk-images" href="#" title="' + encodeQuotes(_e("Unattach the file")) + '"></a>' + 
				'<a class="link" href="' + encodeQuotes(fURL) + '" target="_blank">' + fName.htmlEnc() + '</a>' + 
			'</div>'
		);
		
		// Click event
		$('#attach .one-file[data-id=' + fID + '] a.remove').click(function() {
			return unattachMicroblog(fID);
		});
		
		logThis('File attached.', 3);
	}
	
	// Reset the attach bubble
	$('#attach input[type=file]').val('');
	$('#attach .wait').remove();
	
	// Focus on the text input
	$(document).oneTime(10, function() {
		$('#channel .top input[name=microblog_body]').focus();
	});
}

// Shows the microblog of an user from his infos
function fromInfosMicroblog(xid, hash) {
	// Renitialize the channel
	resetMicroblog();
	
	// Switch to the channel
	switchChan('channel');
	
	// Get the microblog
	getMicroblog(xid, hash);
}

// Plugin launcher
function launchMicroblog() {
	// Keyboard event
	$('#channel .top input[name=microblog_body]').keyup(function(e) {
		// Enter pressed: send the microblog notice
		if((e.keyCode == 13) && !exists('#attach .wait'))
			return sendMicroblog();
	})
	
	// Placeholder
	.placeholder();
	
	// Microblog file attacher
	attachMicroblog();
}
