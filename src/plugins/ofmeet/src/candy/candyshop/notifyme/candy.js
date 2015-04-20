/** File: candy.js
 * Candy - Chats are not dead yet.
 *
 * Authors:
 *   - Troy McCabe <troy.mccabe@geeksquad.com>
 *
 * Copyright:
 *   (c) 2012 Geek Squad. All rights reserved.
 */
var CandyShop = (function(self) { return self; }(CandyShop || {}));

/** Class: CandyShop.NotifyMe
 * Notifies with a sound and highlights the text in the chat when a nick is called out
 */
CandyShop.NotifyMe = (function(self, Candy, $) {
	/** Object: _options
	 * Options for this plugin's operation
	 *
	 * Options:
	 *   (String) nameIdentifier - Prefix to append to a name to look for. '@' now looks for '@NICK', '' looks for 'NICK', etc. Defaults to '@'
	 *   (Boolean) playSound - Whether to play a sound when identified. Defaults to true
	 *   (Boolean) highlightInRoom - Whether to highlight the name in the room. Defaults to true
	 */
	var _options = {
		nameIdentifier: '@',
		playSound: true,
		highlightInRoom: true
	};

	/** Function: init
	 * Initialize the NotifyMe plugin
	 * Bind to beforeShow, play sound and higlight if specified
	 *
	 * Parameters:
	 *   (Object) options - The options to apply to this plugin
	 */
	self.init = function(options) {
		// apply the supplied options to the defaults specified
		$.extend(true, _options, options);
		
		// get the nick from the current user
		var nick = Candy.Core.getUser().getNick();

		// make it what is searched
		// search for <identifier>name in the whole message
		var searchTerm = _options.nameIdentifier + nick;

		// bind to the beforeShow event
		$(Candy).on('candy:view.message.before-show', function(e, args) {
			var searchRegExp = new RegExp('^(.*)(\s?' + searchTerm + ')', 'ig');
			
			// if it's in the message and it's not from me, do stuff
			// I wouldn't want to say 'just do @{MY_NICK} to get my attention' and have it knock...
			if (searchRegExp.test(args.message) && args.name != nick) {
				// play the sound if specified
				if (_options.playSound) {
					Candy.View.Pane.Chat.Toolbar.playSound();
				}
				
				// Save that I'm mentioned in args
				args.forMe = true;
			}
			
			return args.message;
		});
		
		// bind to the beforeShow event
		$(Candy).on('candy:view.message.before-render', function(e, args) {
			var searchRegExp = new RegExp('^(.*)(\s?' + searchTerm + ')', 'ig');
			
			// if it's in the message and it's not from me, do stuff
			// I wouldn't want to say 'just do @{MY_NICK} to get my attention' and have it knock...
			if (searchRegExp.test(args.templateData.message) && args.templateData.name != nick) {
				// highlight if specified
				if (_options.highlightInRoom) {
					args.templateData.message = args.templateData.message.replace(searchRegExp, '$1<span class="candy-notifyme-highlight">' + searchTerm + '</span>');
				}
			}
		});
	};

	return self;
}(CandyShop.NotifyMe || {}, Candy, jQuery));