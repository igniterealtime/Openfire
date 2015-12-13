/*

Jappix - An open social platform
These are the homepage JS scripts for Jappix

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Emmanuel Gil Peyrot
Last revision: 23/06/11

*/

// Allows the user to switch the difference home page elements
function switchHome(div) {
	// Path to
	var home = '#home .';
	var right = home + 'right ';
	var current = right + '.homediv.' + div;
	
	// We switch the div
	$(right + '.homediv, ' + right + '.top').hide();
	$(right + '.' + div).show();
	
	// We reset the homedivs
	$(home + 'homediv:not(.default), ' + home + 'top:not(.default)').remove();
	
	// Get the HTML code to display
	var disable_form = '';
	var lock_host = '';
	var code = '';
	
	// Apply the previous link
	switch(div) {
		case 'loginer':
		case 'anonymouser':
		case 'registerer':
			if(!exists(right + '.top.sub')) {
				// Append the HTML code for previous link
				$(right + '.top.default').after('<h1 class="top sub loginer anonymouser registerer">&laquo; <a href="#" class="previous">' + _e("Previous") + '</a></h1>');
				
				// Click event on previous link
				$(home + 'top.sub a.previous').click(function() {
					return switchHome('default');
				});
			}
		
		break;
	}
	
	// Apply the form
	switch(div) {
		// Login tool
		case 'loginer':
			lock_host = disableInput(LOCK_HOST, 'on');
			code = '<p>' + printf(_e("Login to your existing XMPP account. You can also use the %s to join a groupchat."), '<a href="#" class="to-anonymous">' + _e("anonymous mode") + '</a>') + '</p>' + 
				
				'<form action="#" method="post">' + 
					'<fieldset>' + 
						'<legend>' + _e("Required") + '</legend>' + 
						
						'<label for="lnick">' + _e("Address") + '</label>' + 
						'<input type="text" class="nick" id="lnick" pattern="[^@/]+" required="" /><span class="jid">@</span><input type="text" class="server" id="lserver" value="' + HOST_MAIN + '" ' + lock_host + ' pattern="[^@/]+" required="" />' + 
						'<label for="lpassword">' + _e("Password") + '</label>' + 
						'<input type="password" class="password" id="lpassword" required="" />' + 
						'<label for="lremember">' + _e("Remember me") + '</label>' + 
						'<input type="checkbox" class="remember" id="lremember" />' + 
					'</fieldset>' + 
					
					'<a href="#" class="advanced home-images">' + _e("Advanced") + '</a>' + 
					
					'<fieldset class="advanced">' + 
						'<legend>' + _e("Advanced") + '</legend>' + 
						
						'<label for="lresource">' + _e("Resource") + '</label>' + 
						'<input type="text" class="resource" id="lresource" value="' + JAPPIX_RESOURCE + '" />' + 
						'<label for="lpriority">' + _e("Priority") + '</label>' + 
						'<select class="priority" id="lpriority">' + 
							'<option value="1">' + _e("Low") + '</option>' + 
							'<option value="10" selected="">' + _e("Medium") + '</option>' + 
							'<option value="100">' + _e("High") + '</option>' + 
						'</select>' + 
					'</fieldset>' + 
					
					'<input type="submit" value="' + _e("Here we go!") + '" />' + 
				'</form>';
			
			break;
		
		// Anonymous login tool
		case 'anonymouser':
			disable_form = disableInput(ANONYMOUS, 'off');
			code = '<p>' + printf(_e("Enter the groupchat you want to join and the nick you want to have. You can also go back to the %s."), '<a href="#" class="to-home">' + _e("login page") + '</a>') + '</p>' + 
				
				'<form action="#" method="post">' + 
					'<fieldset>' + 
						'<legend>' + _e("Required") + '</legend>' + 
						
						'<label>' + _e("Room") + '</label>' + 
						'<input type="text" class="room"' + disable_form + ' pattern="[^/]+" required="" />' + 
						
						'<label>' + _e("Nickname") + '</label>' + 
						'<input type="text" class="nick"' + disable_form + ' required="" />' + 
					'</fieldset>' + 
					
					'<input type="submit" value="' + _e("Here we go!") + '"' + disable_form + ' />' + 
				'</form>' + 
				
				'<div class="info report">' + 
					_e("Share this link with your friends:") + ' <span></span>' + 
				'</div>';
			
			break;
		
		// Register tool
		case 'registerer':
			disable_form = disableInput(REGISTRATION, 'off');
			
			if(!disable_form)
				lock_host = disableInput(LOCK_HOST, 'on');
			
			code = '<p>' + _e("Register a new XMPP account to join your friends on your own social cloud. That's simple!") + '</p>' + 
				
				'<form action="#" method="post">' + 
					'<fieldset>' + 
						'<legend>' + _e("Required") + '</legend>' + 
						
						'<label for="rnick">' + _e("Address") + '</label>' + 
						'<input type="text" class="nick" id="rnick" ' + disable_form + ' pattern="[^@/]+" required="" /><span class="jid">@</span><input type="text" class="server" id="rserver" value="' + HOST_MAIN + '" ' + disable_form + lock_host + ' pattern="[^@/]+" required="" />' + 
						'<label for="rpassword">' + _e("Password") + '</label>' + 
						'<input type="password" class="password" id="rpassword" ' + disable_form + ' required="" />' + 
						'<label for="spassword">' + _e("Confirm") + '</label><input type="password" class="spassword" id="spassword" ' + disable_form + ' required="" />' + 
					'</fieldset>' + 
					
					'<input type="submit" value="' + _e("Here we go!") + '" ' + disable_form + '/>' + 
				'</form>';
			
			break;
	}
	
	// Form disabled?
	if(disable_form)
		code += '<div class="info fail">' + 
				_e("This tool has been disabled, you cannot use it!") + 
			'</div>';
	
	// Create this HTML code
	if(code && !exists(current)) {
		// Append it!
		$(right + '.homediv.default').after('<div class="' + div + ' homediv">' + code + '</div>');
		
		// Create the attached events
		switch(div) {
			// Login tool
			case 'loginer':
				$(current + ' a.to-anonymous').click(function() {
					return switchHome('anonymouser');
				});
				
				$(current + ' a.advanced').click(showAdvanced);
				
				$(current + ' form').submit(loginForm);
				
				break;
			
			// Anonymous login tool
			case 'anonymouser':
				$(current + ' a.to-home').click(function() {
					return switchHome('loginer');
				});
				
				$(current + ' form').submit(doAnonymous);
				
				// Keyup event on anonymous join's room input
				$(current + ' input.room').keyup(function() {
					var value = $(this).val();
					var report = current + ' .report';
					var span = report + ' span';
					
					if(!value) {
						$(report).hide();
						$(span).text('');
					}
					
					else {
						$(report).show();
						$(span).text(JAPPIX_LOCATION + '?r=' + value);
					}
				});
				
				break;
			
			// Register tool
			case 'registerer':
				$(current + ' form').submit(registerForm);
				
				break;
		}
	}
	
	// We focus on the first input
	$(document).oneTime(10, function() {
		$(right + 'input:visible:first').focus();
	});
	
	return false;
}

// Allows the user to display the advanced login options
function showAdvanced() {
	// Hide the link
	$('#home a.advanced').hide();
	
	// Show the fieldset
	$('#home fieldset.advanced').show();
	
	return false;
}

// Reads the login form values
function loginForm() {
	// We get the values
	var lPath = '#home .loginer ';
	var lServer = $(lPath + '.server').val();
	var lNick = $(lPath + '.nick').val();
	var lPass = $(lPath + '.password').val();
	var lResource = $(lPath + '.resource').val();
	var lPriority = $(lPath + '.priority').val();
	var lRemember = $(lPath + '.remember').filter(':checked').size();
	
	// Enough values?
	if(lServer && lNick && lPass && lResource && lPriority)
		doLogin(lNick, lServer, lPass, lResource, lPriority, lRemember);
	
	// Something is missing?
	else {
		$(lPath + 'input[type=text], ' + lPath + 'input[type=password]').each(function() {
			var select = $(this);
			
			if(!select.val())
				$(document).oneTime(10, function() {
					select.addClass('please-complete').focus();
				});
			else
				select.removeClass('please-complete');	
		});
	}
	
	return false;
}

// Reads the register form values
function registerForm() {
	var rPath = '#home .registerer ';
	
	// Remove the success info
	$(rPath + '.success').remove();
	
	// Get the values
	var username = $(rPath + '.nick').val();
	var domain = $(rPath + '.server').val();
	var pass = $(rPath + '.password').val();
	var spass = $(rPath + '.spassword').val();
	
	// Enough values?
	if(domain && username && pass && spass && (pass == spass)) {
		// We remove the not completed class to avoid problems
		$('#home .registerer input').removeClass('please-complete');
		
		// Fire the register event!
		doRegister(username, domain, pass);
	}
	
	// Something is missing?
	else {
		$(rPath + 'input[type=text], ' + rPath + 'input[type=password]').each(function() {
			var select = $(this);
			
			if(!select.val() || (select.is('#spassword') && pass && (pass != spass)))
				$(document).oneTime(10, function() {
					select.addClass('please-complete').focus();
				});
			else
				select.removeClass('please-complete');	
		});
	}
	
	return false;
}

// Plugin launcher
function launchHome() {
	// Define the vars
	var home = '#home ';
	var button = home + 'button';
	var locale = home + '.locale';
	
	// Removes the <noscript /> elements to lighten the DOM
	$('noscript').remove();
	
	// Allows the user to switch the home page
	$(button).click(function() {
		// Login button
		if($(this).is('.login'))
			return switchHome('loginer');
		
		// Register button
		else
			return switchHome('registerer');
	});
	
	// Allows the user to switch the language
	$(locale).hover(function() {
		// Initialize the HTML code
		var keepget = $(locale).attr('data-keepget');
		var html = '<div class="list">';
		
		// Generate each locale HTML code
		for(i in LOCALES_AVAILABLE_ID)
			html += '<a href="./?l=' + LOCALES_AVAILABLE_ID[i] + keepget + '">' + LOCALES_AVAILABLE_NAMES[i].htmlEnc() + '</a>';
		
		html += '</div>';
		
		// Append the HTML code
		$(locale).append(html);
	}, function() {
		$(locale + ' .list').remove();
	});
	
	// Disables the browser HTTP-requests stopper
	$(document).keydown(function(e) {
		if((e.keyCode == 27) && !isDeveloper())
			return false;
	});
	
	// Warns for an obsolete browser
	if(isObsolete()) {
		// Add the code
		$(locale).after(
			'<div class="obsolete">' + 
				'<p>' + _e("Your browser is out of date!") + '</p>' + 
				
				'<a class="firefox browsers-images" title="' + printf(_e("Last %s version is better!"), 'Mozilla Firefox') + '" href="http://www.mozilla.com/firefox/"></a>' + 
				'<a class="chrome browsers-images" title="' + printf(_e("Last %s version is better!"), 'Google Chrome') + '" href="http://www.google.com/chrome"></a>' + 
				'<a class="safari browsers-images" title="' + printf(_e("Last %s version is better!"), 'Safari') + '" href="http://www.apple.com/safari/"></a>' + 
				'<a class="opera browsers-images" title="' + printf(_e("Last %s version is better!"), 'Opera') + '" href="http://www.opera.com/"></a>' + 
				'<a class="ie browsers-images" title="' + printf(_e("Last %s version is better!"), 'Internet Explorer') + '" href="http://www.microsoft.com/hk/windows/internet-explorer/"></a>' + 
			'</div>'
		);
		
		// Display it later
		$(home + '.obsolete').oneTime('1s', function() {
			$(this).slideDown();
		});
		
		logThis('Jappix does not support this browser!', 2);
	}
	
	logThis('Welcome to Jappix! Happy coding in developer mode!');
}

// Launch this plugin!
$(document).ready(launchHome);
