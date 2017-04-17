/*

Jappix - An open social platform
These are the talkpage JS scripts for Jappix

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 06/05/11

*/

// Creates the talkpage events
function eventsTalkPage() {
	// Launch all associated plugins
	launchMicroblog();
	launchRoster();
	launchPresence();
	launchPEP();
	launchNotifications();
	launchMusic();
}

// Creates the talkpage code
function createTalkPage() {
	// Talkpage exists?
	if(exists('#talk'))
		return false;
	
	// Anonymous detector
	var anonymous = isAnonymous();
	
	// Generate the HTML code
	var html = 
	'<div id="talk" class="removable">' + 
		'<div id="top-content">' + 
			'<div class="tools tools-logo talk-images"></div>' + 
			
			'<div class="tools tools-all">';
				
				if(!anonymous) html += 
				'<a href="#" onclick="return openInbox();" class="inbox-hidable">' + _e("Messages") +  '</a>' + 
				'<a href="#" onclick="return openVCard();">' + _e("Profile") +  '</a>' + 
				'<a href="#" onclick="return optionsOpen();" class="options-hidable">' + _e("Options") +  '</a>' + 
				'<a href="#" onclick="return normalQuit();">' + _e("Disconnect") +  '</a>';
				
				else html +=
				'<a href="./">' + _e("Disconnect") +  '</a>';
			
			html +=
			'</div>';
			
			if(!anonymous && document.createElement('audio').canPlayType) html += 
			'<div class="tools-all ibubble">' + 
				'<div class="tools music talk-images" onclick="return openMusic();"></div>' + 
				
				'<div class="music-content tools-content bubble hidable">' + 
					'<div class="tools-content-subarrow talk-images"></div>' + 
					
					'<div class="tools-content-subitem">' + 
						'<div class="player">' + 
							'<a href="#" class="stop talk-images" onclick="return actionMusic(\'stop\');"></a>' + 
						'</div>' + 
						
						'<div class="list">' + 
							'<p class="no-results">' + _e("No result!") +  '</p>' + 
						'</div>' + 
						
						'<div class="search">' + 
							'<input type="text" />' + 
						'</div>' + 
					'</div>' + 
				'</div>' + 
			'</div>';
			
			if(!anonymous) html += 
			'<div class="tools-all ibubble">' + 
				'<div class="tools notifications talk-images" onclick="return showBubble(\'.notifications-content\');"></div>' + 
				
				'<div class="notifications-content tools-content bubble hidable">' + 
					'<div class="tools-content-subarrow talk-images"></div>' + 
					
					'<div class="tools-content-subitem">' + 
						'<a class="empty" href="#" onclick="return clearNotifications();">' + _e("Empty") +  '</a>' + 
						'<p class="nothing">' + _e("No notifications.") +  '</p>' + 
					'</div>' + 
				'</div>' + 
			'</div>';
		
		html +=
		'</div>' + 
		
		'<div id="main-content">' + 
			'<div id="left-content">';
				if(!anonymous) html += 
				'<div id="buddy-list">' + 
					'<div class="content"></div>' + 
					
					'<div class="filter">' + 
						'<input type="text" placeholder="' + _e("Filter") +  '" />' + 
						'<a href="#">x</a>' + 
					'</div>' + 
					
					'<div class="foot ibubble">' + 
						'<div class="buddy-list-add buddy-list-icon">' + 
							'<a href="#" class="add talk-images" title="' + _e("Add a friend") +  '"></a>' + 
						'</div>' + 
						
						'<div class="buddy-list-join buddy-list-icon">' + 
							'<a href="#" class="join talk-images" title="' + _e("Join a chat") +  '"></a>' + 
						'</div>' + 
						
						'<div class="buddy-list-groupchat buddy-list-icon">' + 
							'<a href="#" class="groupchat talk-images" title="' + _e("Your groupchats") +  '"></a>' + 
						'</div>' + 
						
						'<div class="buddy-list-more buddy-list-icon">' + 
							'<a href="#" class="more talk-images" title="' + _e("More stuff") +  '"></a>' + 
						'</div>' + 
						
						'<div style="clear: both;"></div>' + 
					'</div>' + 
				'</div>';
				
				html +=
				'<div id="my-infos">' + 
					'<div class="content">' + 
						'<div class="element f-presence ibubble">' + 
							'<a href="#" class="icon picker disabled" data-value="available">' + 
								'<span class="talk-images"></span>' + 
							'</a>' + 
							
							'<input id="presence-status" type="text" placeholder="' + _e("Status") + '" disabled="" />' + 
						'</div>';
						
						if(!anonymous) html += 
						'<div class="element f-mood pep-hidable ibubble">' + 
							'<a href="#" class="icon picker" data-value="happy">' + 
								'<span class="talk-images"></span>' + 
							'</a>' + 
							
							'<input id="mood-text" type="text" placeholder="' + _e("Mood") + '" />' + 
						'</div>' + 
						
						'<div class="element f-activity pep-hidable ibubble">' + 
							'<a href="#" class="icon picker" data-value="exercising">' + 
								'<span class="talk-images activity-exercising"></span>' + 
							'</a>' + 
							
							'<input id="activity-text" type="text" placeholder="' + _e("Activity") + '" />' + 
						'</div>';
					
					html +=
					'</div>' + 
				'</div>' + 
			'</div>' + 
			
			'<div id="right-content">' + 
				'<div id="page-switch">' + 
					'<div class="chans">';
						if(!anonymous) html += 
						'<div class="channel switcher activechan" onclick="return switchChan(\'channel\');">' + 
							'<div class="icon talk-images"></div>' + 
						
							'<div class="name">' + _e("Channel") +  '</div>' + 
						'</div>';
					
					html +=
					'</div>' + 
					
					'<div class="more ibubble">' + 
						'<div class="more-button talk-images" onclick="return loadChatSwitch();" title="' + _e("All tabs") +  '"></div>' + 
					'</div>' + 
				'</div>' + 
				
				'<div id="page-engine">';
					if(!anonymous) html += 
					'<div id="channel" class="page-engine-chan" style="display: block;">' + 
						'<div class="top mixed ' + hex_md5(getXID()) + '">' + 
							'<div class="avatar-container">' + 
								'<img class="avatar" src="' + './img/others/default-avatar.png' + '" alt="" />' + 
							'</div>' + 
							
							'<div class="update">' + 
								'<p>' + _e("What\'s up with you?") +  '</p>' + 
								
								'<div class="microblog-body">' + 
									'<input class="focusable" type="text" name="microblog_body" placeholder="' + _e("Type something you want to share with your friends...") + '" disabled="" />' + 
								'</div>' + 
								
								'<div class="one-microblog-icon ibubble">' + 
									'<a href="#" onclick="return showBubble(\'#attach\');" title="' + _e("Attach a file") +  '" class="postit attach talk-images"></a>' + 
									
									'<form id="attach" class="bubble hidable" action="./php/file-share.php" method="post" enctype="multipart/form-data">' + 
										'<div class="attach-subarrow talk-images"></div>' + 
										
										'<div class="attach-subitem">' + 
											'<p class="attach-p">' + _e("Attach a file") +  '</p>' + 
											generateFileShare() + 
										'</div>' + 
									'</form>' + 
								'</div>' + 
							'</div>' + 
						'</div>' + 
						
						'<div class="content mixed"></div>' + 
						
						'<div class="footer">' + 
							'<div class="sync talk-images">' + _e("You are synchronized with your network.") +  '</div>' + 
							
							'<div class="unsync talk-images">' + _e("Cannot send anything: you can only receive notices!") +  '</div>' + 
							
							'<div class="fetch wait-small">' + _e("Fetching the social channel...") +  '</div>' + 
						'</div>' + 
					'</div>';
				
				html +=
				'</div>' + 
			'</div>' + 
		'</div>' + 
	'</div>';
	
	// Create the HTML code
	$('body').prepend(html);
	
	// Adapt the buddy-list size
	adaptRoster();
	
	// Create JS events
	eventsTalkPage();
	
	// Start the auto idle functions
	liveIdle();
	
	return true;
}

// Destroys the talkpage code
function destroyTalkPage() {
	// Reset our database
	resetDB();
	
	// Reset some vars
	STANZA_ID = 1;
	BLIST_ALL = false;
	FIRST_PRESENCE_SENT = false;
	SEARCH_FILTERED = false;
	AVATAR_PENDING = [];
	
	// Kill all timers, exept the board ones
	$('*:not(#board .one-board)').stopTime();
	
	// Kill the auto idle functions
	dieIdle();
	
	// We renitalise the html markup as its initiale look
	$('.removable').remove();
	pageTitle('home');
	
	// Finally we show the homepage
	$('#home').show();
}
