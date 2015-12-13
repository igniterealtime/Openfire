/*

Jappix - An open social platform
These are the smileys JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 21/03/11

*/

// Generates the correct HTML code for an emoticon insertion tool
function emoteLink(smiley, image, hash) {
	return '<a href="#" class="emoticon emoticon-' + image + ' smileys-images" data-smiley="' + smiley + '"></a>';
}

// Emoticon links arrays
function smileyLinks(hash) {
	var links = '';
	
	var sArray = new Array(
		':-D',
		']:->',
		'8-)',
		':-P',
		':-)',
		';-)',
		':-$',
		':-|',
		':-/',
		'=-O',
		':-(',
		':\'-(',
		':-@',
		':-!',
		'({)',
		'(})',
		':3',
		'(@)',
		':-[',
		':-{}',
		'<3',
		'</3',
		'@}->--',
		'(W)',
		'(Y)',
		'(N)',
		'(I)',
		'(C)',
		'(D)',
		'(B)',
		'(Z)',
		'(X)',
		'(P)',
		'(T)',
		'(8)',
		'(%)',
		'(E)',
		'(R)',
		'(*)',
		'(S)'
	);
	
	var cArray = new Array(
		'biggrin',
		'devil',
		'coolglasses',
		'tongue',
		'smile',
		'wink',
		'blush',
		'stare',
		'frowning',
		'oh',
		'unhappy',
		'cry',
		'angry',
		'puke',
		'hugright',
		'hugleft',
		'lion',
		'pussy',
		'bat',
		'kiss',
		'heart',
		'brheart',
		'flower',
		'brflower',
		'thumbup',
		'thumbdown',
		'lamp',
		'coffee',
		'drink',
		'beer',
		'boy',
		'girl',
		'photo',
		'phone',
		'music',
		'cuffs',
		'mail',
		'rainbow',
		'star',
		'moon'
	);
	
	for(i in sArray)
		links += emoteLink(sArray[i], cArray[i], hash);
	
	return links;
}
