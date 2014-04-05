config.desktopSharing = 'ext';	 				// Desktop sharing method. Can be set to 'ext', 'webrtc' or false to disable.
config.chromeExtensionId = 'diibjkoicjeejcmhdnailmkgecihlobk'; 	// Id of desktop streamer Chrome extension
config.minChromeExtVersion = '0.1'; 				// Required version of Chrome extension
    
config.getroomnode = function (path) 
{ 
	console.log('getroomnode', path);
	var name = "r";
	var roomnode = null;

	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);

	if (!results)
		roomnode = null; 
	else 	roomnode = results[1] || undefined;	

	if (!roomnode) {
		roomnode = Math.random().toString(36).substr(2, 20);
		window.history.pushState('VideoChat', 'Room: ' + roomnode, path + "?r=" + roomnode);
	}
	return roomnode;    
};  
