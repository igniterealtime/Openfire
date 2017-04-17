<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="X-UA-Compatible" content="chrome=1">
<link rel="stylesheet" type="text/css" href="chat/css/mini.css" />
<link rel="stylesheet" type="text/css" href="chat/css/window.css" />
<script type="text/javascript" src="chat/js/jquery.js"></script>
<script type="text/javascript" src="chat/js/interfaceUI.js"></script>
<script type="text/javascript" src="chat/php/get.php?l=en&t=js&g=mini.xml"></script>


<script language="JavaScript">
	var username, videoXid = {};

	function setGroups(username, groups)
	{
		if(!MINI_INITIALIZED)
		{
			if (username != "")
			{
				console.log("logging into openfire with userid " + username)

				disconnectMini();

				MINI_GROUPCHATS = groups;
				MINI_ANIMATE = true;
				launchMini(true, false, window.location.hostname, username, "dummy");
			}

		} else {

			if (username == "")
			{
				console.log("logging off openfire")

				disconnectMini();
			}
		}

		if(MINI_INITIALIZED && (MINI_GROUPCHATS.length != groups.length || MINI_GROUPCHATS.length == 0))
		{
			for(var i = 0; i < MINI_GROUPCHATS.length; i++)
			{
				if(!MINI_GROUPCHATS[i])
					continue;

				console.log("setGroups removing group " + MINI_GROUPCHATS[i])

				try {
					var chat_room = bareXID(generateXID(MINI_GROUPCHATS[i], 'groupchat'));
					var hash = hex_md5(chat_room);
					var current = '#jappix_mini #chat-' + hash;

					jQuery(current).remove();
					presenceMini('unavailable', '', '', '', chat_room + '/' + unescape(jQuery(current).attr('data-nick')));
				}

				catch(e) {}
			}

			MINI_GROUPCHATS = groups;

			for(var i = 0; i < MINI_GROUPCHATS.length; i++)
			{
				if(!MINI_GROUPCHATS[i])
					continue;

				console.log("setGroups adding group " + MINI_GROUPCHATS[i])

				try {
					var chat_room = bareXID(generateXID(MINI_GROUPCHATS[i], 'groupchat'));
					chatMini('groupchat', chat_room, getXIDNick(chat_room), hex_md5(chat_room), MINI_PASSWORDS[i], MINI_SHOWPANE);
				}

				catch(e) {}
			}
		}

		console.log("logged into openfire with userid " + username)
	}


	function focusWindow(room, xid, type, url)
	{
		if (videoXid[room])
		{
			try {
				videoXid[room].close();
			} catch (e) {}
		}

		videoXid[room] = window.open(url, "_blank");

		var aMsg = new JSJaCMessage();

		aMsg.setTo(xid);
		aMsg.setType(type);
		aMsg.setBody(url);

		con.send(aMsg);
	}


	function setupWindow()
	{
		jQuery(window).resize(function ()
		{
			resiziFrame();
		});

		resiziFrame();
	}

	function resiziFrame()
	{
		var myWidth;
		var myHeight;

		if ( typeof( window.innerWidth ) == 'number' )
		{
			myWidth = window.innerWidth;
			myHeight = window.innerHeight;

		} else if ( document.documentElement && ( document.documentElement.clientWidth || document.documentElement.clientHeight ) ) {

			myWidth = document.documentElement.clientWidth;
			myHeight = document.documentElement.clientHeight;

		} else if( document.body && ( document.body.clientWidth || document.body.clientHeight ) ) {

			myWidth = document.body.clientWidth;
			myHeight = document.body.clientHeight;
		}

		jQuery('#wordpress').css('height', myHeight + 'px');
		jQuery('#wordpress').css('width',  myWidth +  'px');
	}

	function startTone(name)
	{
		if (!ringtone)
		{
			ringtone = new Audio();
			ringtone.loop = true;
			ringtone.src = "chat/ringtones/" + name + ".mp3";
			ringtone.play();
		}
	}

	function stopTone()
	{
		if (ringtone)
		{
			ringtone.pause();
			ringtone = null;
		}
	}

	function ringback()
	{
		startTone("ringback-uk");
	}

	function ringing()
	{
		startTone("diggztone_vibe");
	}

</script>
</head>
<body topmargin="0" leftmargin="0" onload="setupWindow()" onunload="disconnectMini()" style="border-width:0px; overflow: hidden;margin-left: 0px; margin-top: 0px; margin-right: 0px; margin-bottom: 0px">
<iframe id="wordpress" frameborder="0" src="<?php echo $_GET['goto'] ? $_GET['goto'] : "index.php"; ?>" style="border:0px; border-width:0px; margin-left: 0px; margin-top: 0px; margin-right: 0px; margin-bottom: 0px; width:100%;height:100%;"></iframe>
</body>
</html>