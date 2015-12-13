/*

Jappix - An open social platform
These are the tooltip JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 23/06/11

*/

// Creates a tooltip code
function createTooltip(xid, hash, type) {
	// Path to the element
	var path = '#' + hash;
	var path_tooltip = path + ' .chat-tools-' + type;
	
	// Yet exists?
	if(exists(path_tooltip + ' .bubble-' + type))
		return false;
	
	// Generates special tooltip HTML code
	var title = '';
	var content = '';
	
	switch(type) {
		// Smileys
		case 'smileys':
			title = _e("Smiley insertion");
			content = smileyLinks(hash);
			
			break;
		
		// Style
		case 'style':
			title = _e("Change style");
			content = 
				'<label class="bold"><input type="checkbox" class="bold" />' + _e("Text in bold") + '</label>' + 
				'<label class="italic"><input type="checkbox" class="italic" />' + _e("Text in italic") + '</label>' + 
				'<label class="underline"><input type="checkbox" class="underline" />' + _e("Underlined text") + '</label>' + 
				'<a href="#" class="color" style="background-color: #b10808; clear: both;" data-color="b10808"></a>' + 
				'<a href="#" class="color" style="background-color: #e5860c;" data-color="e5860c"></a>' + 
				'<a href="#" class="color" style="background-color: #f0f30e;" data-color="f0f30e"></a>' + 
				'<a href="#" class="color" style="background-color: #009a04;" data-color="009a04"></a>' + 
				'<a href="#" class="color" style="background-color: #0ba9a0;" data-color="0ba9a0"></a>' + 
				'<a href="#" class="color" style="background-color: #04228f;" data-color="04228f"></a>' + 
				'<a href="#" class="color" style="background-color: #9d0ab7;" data-color="9d0ab7"></a>' + 
				'<a href="#" class="color" style="background-color: #8a8a8a;" data-color="8a8a8a"></a>';
			
			break;
		
		// Chat log
		case 'save':
			title = _e("Save chat");
			content = '<p style="margin-bottom: 8px;">' + _e("Click on the following link to get the chat log, and wait. Then click again to get the file.") + '</p>';
			
			// Possible to generate any log?
			if($(path + ' .one-line').size())
				content += '<a href="#" class="tooltip-actionlog">' + _e("Generate file!") + '</a>';
			else
				content += '<span class="tooltip-nolog">' + _e("This chat is empty!") + '</span>';
			
			break;
	}
	
	// Generates general tooltip HTML code
	var html = 
		'<div class="tooltip bubble-' + type + '">' + 
			'<div class="tooltip-subitem">' + 
				'<p class="tooltip-top">' + title + '</p>' + 
				content + 
			'</div>' + 
			
			'<div class="tooltip-subarrow talk-images"></div>' + 
		'</div>';
	
	// Append the HTML code
	$(path_tooltip).append(html);
	
	// Special events
	switch(type) {
		// Smileys
		case 'smileys':
			// Apply click event on smiley links
			$(path_tooltip + ' a.emoticon').click(function() {
				return insertSmiley($(this).attr('data-smiley'), hash);
			});
			
			break;
		
		// Style
		case 'style':
			// Paths to items
			var message_area = path + ' .message-area';
			var bubble_style = path_tooltip + ' .bubble-style ';
			var style = bubble_style + 'input:checkbox';
			var colors = bubble_style + 'a.color';
			
			// Click event on color picker
			$(colors).click(function() {
				// The clicked color is yet selected
				if($(this).hasClass('selected')) {
					$(message_area).removeAttr('data-color');
					$(this).removeClass('selected');
				}
				
				else {
					$(message_area).attr('data-color', $(this).attr('data-color'));
					$(colors).removeClass('selected');
					$(this).addClass('selected');
				}
				
				return false;
			});
			
			// Change event on text style checkboxes
			$(style).change(function() {
				// Get current type
				var style_data = 'data-' + $(this).attr('class');
				
				// Checked checkbox?
				if($(this).filter(':checked').size())
					$(message_area).attr(style_data, true);
				else
					$(message_area).removeAttr(style_data);
			});
			
			// Update the textarea style when it is changed
			$(style + ', ' + colors).click(function() {
				var style = generateStyle(hash);
				
				// Any style to apply?
				if(style)
					$(message_area).attr('style', style);
				else
					$(message_area).removeAttr('style');
				
				// Focus again on the message textarea
				$(document).oneTime(10, function() {
					$(message_area).focus();
				});
			});
			
			// Load current style
			loadStyleSelector(hash);
			
			break;
		
		// Chat log
		case 'save':
			// Chat log generation click event
			$(path_tooltip + ' .tooltip-actionlog').click(function() {
				// Replace it with a waiting notice
				$(this).replaceWith('<span class="tooltip-waitlog">' + _e("Please wait...") + '</span>');
				
				generateChatLog(xid, hash);
				
				return false;
			});
			
			break;
	}
	
	return true;
}

// Destroys a tooltip code
function destroyTooltip(hash, type) {
	$('#' + hash + ' .chat-tools-content .bubble-' + type).remove();
}

// Applies the page-engine tooltips hover event
function hoverTooltip(xid, hash, type) {
	$('#' + hash + ' .chat-tools-' + type).hover(function() {
		createTooltip(xid, hash, type);
	}, function() {
		destroyTooltip(hash, type)
	});
}

// Applies the hoverTooltip function to the needed things
function tooltipIcons(xid, hash) {
	// Hover events
	hoverTooltip(xid, hash, 'smileys');
	hoverTooltip(xid, hash, 'style');
	hoverTooltip(xid, hash, 'save');
	
	// Click events
	$('#' + hash + ' a.chat-tools-content, #' + hash + ' .chat-tools-content a').click(function() {
		return false;
	});
}

// Loads the style selector options
function loadStyleSelector(hash) {
	// Define the vars
	var path = '#' + hash;
	var message_area = $(path + ' .message-area');
	var bubble_style = path + ' .bubble-style';
	
	// Apply the options to the style selector
	$(bubble_style + ' input[type=checkbox]').each(function() {
		// Current input enabled?
		if(message_area.attr('data-' + $(this).attr('class')))
			$(this).attr('checked', true);
	});
	
	// Apply message color
	$(bubble_style + ' a.color[data-color=' + message_area.attr('data-color') + ']').addClass('selected');
}
