/*

Jappix - An open social platform
These are the filtering JS script for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Maranda
Last revision: 24/06/11

*/

// Generates a given emoticon HTML code
function emoteImage(image, text, after) {
	return ' <img class="emoticon emoticon-' + image + ' smileys-images" alt="' + encodeQuotes(text) + '" src="' + './img/others/blank.gif' + '" /> ' + after;
}

// Filters a given message
function filterThisMessage(neutralMessage, nick, html_encode) {
	var filteredMessage = neutralMessage;
	
	// We encode the HTML special chars
	if(html_encode)
		filteredMessage = filteredMessage.htmlEnc();
	
	// /me command
	filteredMessage = filteredMessage.replace(/((^)|((.+)(>)))(\/me )([^<]+)/, nick + ' $7')
	
	// We replace the smilies text into images
	.replace(/(:-@)($|\s|<)/gi, emoteImage('angry', '$1', '$2'))
	.replace(/(:-\[)($|\s|<)/gi, emoteImage('bat', '$1', '$2'))
	.replace(/(\(B\))($|\s|<)/g, emoteImage('beer', '$1', '$2'))
	.replace(/((:-?D)|(XD))($|\s|<)/gi, emoteImage('biggrin', '$1', '$4'))
	.replace(/(:-\$)($|\s|<)/gi, emoteImage('blush', '$1', '$2'))
	.replace(/(\(Z\))($|\s|<)/g, emoteImage('boy', '$1', '$2'))
	.replace(/(\(W\))($|\s|<)/g, emoteImage('brflower', '$1', '$2'))			
	.replace(/((&lt;\/3)|(\(U\)))($|\s|<)/g, emoteImage('brheart', '$1', '$4'))			
	.replace(/(\(C\))($|\s|<)/g, emoteImage('coffee', '$1', '$2'))			
	.replace(/((8-\))|(\(H\)))($|\s|<)/g, emoteImage('coolglasses', '$1', '$4'))
	.replace(/(:'-\()($|\s|<)/gi, emoteImage('cry', '$1', '$2'))
	.replace(/(\(%\))($|\s|<)/g, emoteImage('cuffs', '$1', '$2'))
	.replace(/(\]:-&gt;)($|\s|<)/gi, emoteImage('devil', '$1', '$2'))			
	.replace(/(\(D\))($|\s|<)/g, emoteImage('drink', '$1', '$2'))
	.replace(/(@}-&gt;--)($|\s|<)/gi, emoteImage('flower', '$1', '$2'))
	.replace(/((:-\/)|(:S))($|\s|<)/gi, emoteImage('frowning', '$1', '$4'))
	.replace(/(\(X\))($|\s|<)/g, emoteImage('girl', '$1', '$2'))
	.replace(/((&lt;3)|(\(L\)))($|\s|<)/g, emoteImage('heart', '$1', '$4'))
	.replace(/(\(}\))($|\s|<)/g, emoteImage('hugleft', '$1', '$2'))			
	.replace(/(\({\))($|\s|<)/g, emoteImage('hugright', '$1', '$2'))
	.replace(/(:-{})($|\s|<)/gi, emoteImage('kiss', '$1', '$2'))
	.replace(/(\(I\))($|\s|<)/g, emoteImage('lamp', '$1', '$2'))
	.replace(/(:3)($|\s|<)/gi, emoteImage('lion', '$1', '$2'))
	.replace(/(\(E\))($|\s|<)/g, emoteImage('mail', '$1', '$2'))
	.replace(/(\(S\))($|\s|<)/g, emoteImage('moon', '$1', '$2'))
	.replace(/(\(8\))($|\s|<)/g, emoteImage('music', '$1', '$2'))
	.replace(/((=-?O)|(:-?O))($|\s|<)/gi, emoteImage('oh', '$1', '$4'))
	.replace(/(\(T\))($|\s|<)/g, emoteImage('phone', '$1', '$2'))
	.replace(/(\(P\))($|\s|<)/g, emoteImage('photo', '$1', '$2'))
	.replace(/(:-!)($|\s|<)/gi, emoteImage('puke', '$1', '$2'))
	.replace(/(\(@\))($|\s|<)/g, emoteImage('pussy', '$1', '$2'))
	.replace(/(\(R\))($|\s|<)/g, emoteImage('rainbow', '$1', '$2'))
	.replace(/(:-?\))($|\s|<)/gi, emoteImage('smile', '$1', '$2'))
	.replace(/(\(\*\))($|\s|<)/g, emoteImage('star', '$1', '$2'))
	.replace(/(:-?\|)($|\s|<)/gi, emoteImage('stare', '$1', '$2'))
	.replace(/(\(N\))($|\s|<)/g, emoteImage('thumbdown', '$1', '$2'))
	.replace(/(\(Y\))($|\s|<)/g, emoteImage('thumbup', '$1', '$2'))
	.replace(/(:-?P)($|\s|<)/gi, emoteImage('tongue', '$1', '$2'))
	.replace(/(:-?\()($|\s|<)/gi, emoteImage('unhappy', '$1', '$2'))
	.replace(/(;-?\))($|\s|<)/gi, emoteImage('wink', '$1', '$2'))
	
	// Text in bold
	.replace(/(^|\s|>)((\*)([^<>'"]+)(\*))($|\s|<)/gi, '$1<b>$2</b>$6')
	
	// Italic text
	.replace(/(^|\s|>)((\/)([^<>'"]+)(\/))($|\s|<)/gi, '$1<i>$2</i>$6')
	
	// Underlined text
	.replace(/(^|\s|>)((_)([^<>'"]+)(_))($|\s|<)/gi, '$1<span style="text-decoration: underline;">$2</span>$6');
	
	// Add the links
	if(html_encode)
		filteredMessage = applyLinks(filteredMessage, 'desktop');
	
	// Filter integratebox links
	filteredMessage = filterIntegrateBox(filteredMessage);
	
	return filteredMessage;
}

// Filters a xHTML message to be displayed in Jappix
function filterThisXHTML(code) {
	// Allowed elements array
	var elements = new Array(
				 'a',
				 'abbr',
			         'acronym',
			         'address',
			         'blockquote',
				 'body',
				 'br',
				 'cite',
			         'code',
			         'dd',
			         'dfn',
			         'div',
			         'dt',
			         'em',
			         'h1',
			         'h2',
			         'h3',
			         'h4',
			         'h5',
			         'h6',
			         'head',
			         'html',
			         'img',
			         'kbd',
			         'li',
			         'ol',
			         'p',
			         'pre',
			         'q',
			         'samp',
			         'span',
			         'strong',
			         'title',
			         'ul',
			         'var'
			        );
	
	// Allowed attributes array
	var attributes = new Array(
				   'accesskey',
				   'alt',
				   'charset',
				   'cite',
				   'class',
				   'height',
				   'href',
				   'hreflang',
				   'id',
				   'longdesc',
				   'profile',
				   'rel',
				   'rev',
				   'src',
				   'style',
				   'tabindex',
				   'title',
				   'type',
				   'uri',
				   'version',
				   'width',
				   'xml:lang',
				   'xmlns'
				  );
	
	// Remove forbidden elements
	$(code).find('html body *').each(function() {
		// This element is not authorized
		if(!existArrayValue(elements, (this).nodeName.toLowerCase()))
			$(this).remove();
	});
	
	// Remove forbidden attributes
	$(code).find('html body *').each(function() {
		// Put a pointer on this element (jQuery way & normal way)
		var cSelector = $(this);
		var cElement = (this);
		
		// Loop the attributes of the current element
		$(cElement.attributes).each(function(index) {
			// Read the current attribute
			var cAttr = cElement.attributes[index];
			var cName = cAttr.name;
			var cVal = cAttr.value;
			
			// This attribute is not authorized, or contains JS code
			if(!existArrayValue(attributes, cName.toLowerCase()) || ((cVal.toLowerCase()).match(/(^|"|')javascript:/)))
				cSelector.removeAttr(cName);
		});
	});
	
	// Filter some other elements
	$(code).find('a').attr('target', '_blank');
	
	return $(code).find('html body').html();
}
