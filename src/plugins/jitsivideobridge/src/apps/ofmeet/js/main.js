var connection = null;
var roomjid;
var nickname = null;
var roomUrl = null;
var sharedKey = '';
var screenShare = false;
var screenToVideo = false;
var pdfShare = null;
var pdfFrame = null;
var pdfPage = "1";
var altView = false;
var sipUri = null;
var notificationInterval = false;
var unreadMessages = 0;
var toolbarTimeout = null;
var getVideoSize = null;
var currentVideoWidth = null;
var currentVideoHeight = null;


$(document).ready(function () 
{
    var storedDisplayName = window.localStorage.displayname;
    
    getVideoSize = getVideoSizeCover;
        
    if (storedDisplayName) {
        nickname = unescape(storedDisplayName);
        $("#localVideo").attr("title", nickname);        
    }
    
    window.RTC = setupRTC();

    if (window.RTC === null) {
	window.location.href = 'webrtcrequired.html';
	return;

    } else if (window.RTC.browser != 'chrome') {
	window.location.href = 'chromeonly.html';
	return;
    }
    
    $('#nickinput').keydown(function(event) 
    {
        if (event.keyCode == 13) {
            event.preventDefault();
            var val = this.value;
            this.value = '';
            
            if (!nickname) {
                nickname = val;
                window.localStorage.displayname = nickname;                
                $("#localVideo").attr("title", nickname);
                
                $('#nickname').css({visibility:"hidden"});
                $('#ofmeet').css({visibility:'visible'});
                $('#usermsg').css({visibility:'visible'});
                $('#usermsg').focus();
                
                if (connection && roomjid) connection.emuc.changeNick(roomjid + "/" + nickname);
                return;
            }
        }
    });

    $('#usermsg').keydown(function(event) 
    {
        if (event.keyCode == 13) 
        {
		event.preventDefault();
		var message = this.value;
		$('#usermsg').val('').trigger('autosize.resize');
		this.focus();
		connection.emuc.sendMessage(message, nickname);
            
		unreadMessages = 0;
		setVisualNotification(false);            
        }
    });

    $('#usermsg').autosize();
    
    resizeLarge();
    
    $(window).resize(function () {
        resizeLarge();
        positionLarge();        
    });

    document.getElementById('largeVideo').addEventListener('loadedmetadata', function(e)
    {
	currentVideoWidth = this.videoWidth;
	currentVideoHeight = this.videoHeight;
	positionLarge(currentVideoWidth, currentVideoHeight);
    });

    $(document).on('webkitfullscreenchange mozfullscreenchange fullscreenchange',
	function() {
	    resizeLarge();
	    positionLarge();
    });        

    RTCPeerconnection = RTC.peerconnection;

    if (config.useWebsockets)
    	connection = new Openfire.Connection(config.bosh);    
    else
    	connection = new Strophe.Connection(config.bosh);
    	
    connection.resource = Math.random().toString(36).substr(2, 20);
    connection.rawInput = function (data) { console.log('RECV: ' + data); };
    connection.rawOutput = function (data) { console.log('SEND: ' + data); };
    
    var jid = config.hosts.domain;

    connection.connect(jid, null, function (status) 
    {
	if (status == Strophe.Status.CONNECTED) 
	{
	    console.log('connected');
	    connection.send($pres()); 
	    doJoin(); 
	    
	    if (urlParam("screen"))
	    {
	    	getConstraints(['screen']);
	    	$("#screen").addClass("fa-border");
	    	
	    } else {
		getConstraints(['audio', 'video'], config.resolution);	
	    	$("#screen").removeClass("fa-border");
	    }
	    showToolbar();
	    updateRoomUrl(window.location.href);		
	    getUserMedia();
	    
	} else {
	    console.log('status', status);
	}
    });
});

$(window).bind('beforeunload', function () 
{
    console.log("beforeunload");
    
    if (pdfShare != null)
    {
	connection.emuc.pdfShare("destroy", pdfShare);    
    }
    
    if (connection && connection.connected) {
    	unregisterRayoEvents();	
    }
    
    if (window.RTC.rayo.localStream)
    {
    	window.RTC.rayo.localStream.stop();
    	window.RTC.rayo.localStream = null;
    }
    
    if (window.RTC.rayo.pc)
    {
	var pcs = Object.getOwnPropertyNames(window.RTC.rayo.pc) 
	
	for (var i=0; i< pcs.length; i++)
	{
		console.log("closing peer connection", pcs[i]);
		
		var pc = window.RTC.rayo.pc[pcs[i]];
		
		if (pc)
		{
			pc.close();
			pc = null;
		}
	}
    }
    
    connection.disconnect();    
});

$(window).bind('entered.muc', function (event, from, member) 
{
	console.log('entered.muc', from, member);
	
	if (pdfShare)
	{					
		connection.emuc.pdfShare("create", pdfShare + "&control=false#" + pdfPage);
	}	
});

$(window).bind('left.muc', function (event, from) 
{
	console.log('left.muc', from);
});

$(document).bind('mediaready.rayo', function(event, stream) 
{
	window.RTC.rayo.localStream = stream;

	if (stream.getVideoTracks().length > 0)
	{
		window.RTC.attachMediaStream($('#localVideo'), stream);
		document.getElementById('localVideo').muted = true;
		document.getElementById('localVideo').autoplay = true;
		document.getElementById('localVideo').volume = 0;

		document.getElementById('largeVideo').volume = 0;
		document.getElementById('largeVideo').src = document.getElementById('localVideo').src;
	} else {
		window.RTC.attachMediaStream($('#localAudio'), stream);	
	}
    
	console.log("mediaready.rayo");   
	
	registerRayoEvents();	
});

$(document).bind('mediafailure.rayo', function(error) {
	console.error('mediafailure.rayo ' + error);	
	window.location.href = 'webrtcrequired.html';	
}); 


$(document).bind('remoteaudiostreamremoved.rayo', function(event, data) 
{
	console.log('remoteaudiostreamremoved.rayo ', data);

	var id = 'remoteVideo_' + data.stream.id;	
	$('#' + id).remove();	
	resizeThumbnails();	
});
	
$(document).bind('remoteaudiostreamadded.rayo', function(event, data, nick) 
{
	console.log('remoteaudiostreamadded.rayo ', data, nick);
	
	var pos = nick.indexOf("(");
	if (pos > -1) nick = nick.substring(0, pos);
	
	var id = 'remoteVideo_' + data.stream.id;

	if (!document.getElementById(id))
	{
		var vid = document.createElement('video');
		vid.id = id;
		vid.autoplay = true;
		vid.oncontextmenu = function() { return false; };
		var remotes = document.getElementById('remoteVideos');
		remotes.appendChild(vid);
	}
	var sel = $('#' + id);
	sel.attr("title", unescape(nick) + " (Audio only)")
	sel.show();
	resizeThumbnails();	
});

$(document).bind('remotestreamadded.rayo', function(event, data, nick) 
{
	console.log('remotestreamadded.rayo ', nick);
	
	var pos = nick.indexOf("(");
	if (pos > -1) nick = nick.substring(0, pos);
	
	var id = 'remoteVideo_' + data.stream.id;

	if (!document.getElementById(id))
	{
		var vid = document.createElement('video');
		vid.id = id;
		vid.autoplay = true;
		vid.oncontextmenu = function() { return false; };
		var remotes = document.getElementById('remoteVideos');
		remotes.appendChild(vid);
	}
	var sel = $('#' + id);
	sel.hide();

	window.RTC.attachMediaStream(sel, data.stream);	    

	if (sel.attr('id') && sel.attr('id').indexOf('mixedmslabel') == -1) {
	    // ignore mixedmslabela0 and non room members
	    sel.attr("title", unescape(nick))
	    sel.attr("class", "remotevideo")	    
	    sel.show();

	    document.getElementById('largeVideo').volume = 1;
	    $('#largeVideo').attr('src', sel.attr('src'));
	}
	
	resizeThumbnails();	

	data.stream.onended = function() {
	    console.log('stream ended', this.id);
	    var src = $('#' + id).attr('src');
	    $('#' + id).remove();
	    if (src === $('#largeVideo').attr('src')) {
		// this is currently displayed as large
		// pick the last visible video in the row
		// ... well, if nobody else is left, this picks the local video
		var pick = $('#remoteVideos :visible:last').get(0);
		// mute if localvideo
		document.getElementById('largeVideo').volume = pick.volume;
		document.getElementById('largeVideo').src = pick.src;
	    }
	    resizeThumbnails();
	}
	// FIXME: hover is bad, this causes flicker. How about moving this?
	// remember that moving this in the DOM requires to play() again
	sel.click(
	    function() {
		console.log('hover in', $(this).attr('src'));
		$("#largeVideo").css("visibility", "visible");
		var newSrc = $(this).attr('src');
		
		if ($('#largeVideo').attr('src') != newSrc) {
		    document.getElementById('largeVideo').volume = 1;
		    $('#largeVideo').fadeOut(300, function(){
			$(this).attr('src', newSrc);
			$(this).fadeIn(300);
		    });
		}
		
		if (pdfFrame != null)
		{			
			$("#largeVideo").css("display", "none");
		}
	    }
	);
});


function setupRTC() 
{
    var RTC = null;
    if (navigator.mozGetUserMedia) {
	console.log('This appears to be Firefox');
	var version = parseInt(navigator.userAgent.match(/Firefox\/([0-9]+)\./)[1], 10);
	if (version >= 22) {
	    RTC = {
		rayo: {
			channels: {},
			confid: {},
			pc: {},
			addssrc: {},
			localStream: null,
			constraints: {audio: false, video: false}
		},
		peerconnection: mozRTCPeerConnection,
		browser: 'firefox',
		getUserMedia: navigator.mozGetUserMedia.bind(navigator),
		attachMediaStream: function (element, stream) {
		    element[0].mozSrcObject = stream;
		    element[0].play();
		},
		pc_constraints: {}
	    };
	    if (!MediaStream.prototype.getVideoTracks)
		MediaStream.prototype.getVideoTracks = function () { return []; };
	    if (!MediaStream.prototype.getAudioTracks)
		MediaStream.prototype.getAudioTracks = function () { return []; };
	    RTCSessionDescription = mozRTCSessionDescription;
	    RTCIceCandidate = mozRTCIceCandidate;
	}
    } else if (navigator.webkitGetUserMedia) {
	console.log('This appears to be Chrome');
	RTC = {
		rayo: {
			channels: {},
			confid: {},
			pc: {},
			addssrc: {},
			localStream: null,
			constraints: {audio: false, video: false}	    		
		},	
	    peerconnection: webkitRTCPeerConnection,
	    browser: 'chrome',
	    getUserMedia: navigator.webkitGetUserMedia.bind(navigator),
	    attachMediaStream: function (element, stream) {
		element.attr('src', webkitURL.createObjectURL(stream));
	    },
	    pc_constraints: {'optional': [{'DtlsSrtpKeyAgreement': 'true'}]} // enable dtls support in canary
	};
	if (navigator.userAgent.indexOf('Android') != -1) {
	    RTC.pc_constraints = {}; // disable DTLS on Android
	}
	if (!webkitMediaStream.prototype.getVideoTracks) {
	    webkitMediaStream.prototype.getVideoTracks = function () {
		return this.videoTracks;
	    };
	}
	if (!webkitMediaStream.prototype.getAudioTracks) {
	    webkitMediaStream.prototype.getAudioTracks = function () {
		return this.audioTracks;
	    };
	}
    }
    if (RTC === null) {
	try { console.log('Browser does not appear to be WebRTC-capable'); } catch (e) { }
    }
    return RTC;
}
    
function getUserMedia()
{
    console.log("getUserMedia", window.RTC.rayo.constraints);
    	
    try {
	window.RTC.getUserMedia(window.RTC.rayo.constraints,
		function (stream) {
		    console.log('onUserMediaSuccess');
		    $(document).trigger('mediaready.rayo', [stream]);
		},
		function (error) {
		    console.warn('Failed to get access to local media. Error ', error);
		    $(document).trigger('mediafailure.rayo');
		});
    } catch (e) {
	console.error('GUM failed: ', e);
	$(document).trigger('mediafailure.rayo');
    }    
};

function getConstraints(um, resolution, bandwidth, fps) 
{
    console.log("getConstraints", um, resolution, bandwidth, fps);
    	
    window.RTC.rayo.constraints = {audio: false, video: false};

    if (um.indexOf('video') >= 0) {
	window.RTC.rayo.constraints.video = {mandatory: {}};// same behaviour as true
    }
    if (um.indexOf('audio') >= 0) {
	window.RTC.rayo.constraints.audio = {};// same behaviour as true
    }
    
    if (um.indexOf('screen') >= 0) {
	window.RTC.rayo.constraints.video = {
	    "mandatory": {
		"chromeMediaSource": "screen",
		"maxWidth": window.screen.width,
		"maxHeight": window.screen.height,
		"maxFrameRate": "3"		
	    }
	};
    
    } else

    if (resolution && window.RTC.rayo.constraints.video) 
    {
	window.RTC.rayo.constraints.video = {mandatory: {}};// same behaviour as true
	// see https://code.google.com/p/chromium/issues/detail?id=143631#c9 for list of supported resolutions
	switch (resolution) {
	// 16:9 first
	case '1080':
	case 'fullhd':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 1920;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 1080;
		window.RTC.rayo.constraints.video.mandatory.minAspectRatio = 1.77;
		break;
	case '720':
	case 'hd':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 1280;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 720;
		window.RTC.rayo.constraints.video.mandatory.minAspectRatio = 1.77;
		break;
	case '360':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 640;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 360;
		window.RTC.rayo.constraints.video.mandatory.minAspectRatio = 1.77;
		break;
	case '180':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 320;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 180;
		window.RTC.rayo.constraints.video.mandatory.minAspectRatio = 1.77;
		break;
	// 4:3
	case '960':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 960;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 720;
		break;
	case '640':
	case 'vga':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 640;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 480;
		break;
	case '320':
		window.RTC.rayo.constraints.video.mandatory.minWidth = 320;
		window.RTC.rayo.constraints.video.mandatory.minHeight = 240;
		break;
	default:
		if (navigator.userAgent.indexOf('Android') != -1) {
		    window.RTC.rayo.constraints.video.mandatory.minWidth = 320;
		    window.RTC.rayo.constraints.video.mandatory.minHeight = 240;
		    window.RTC.rayo.constraints.video.mandatory.maxFrameRate = 15;
		}
		break;
	}
    }

    if (bandwidth) { // doesn't work currently, see webrtc issue 1846
	if (!window.RTC.rayo.constraints.video) window.RTC.rayo.constraints.video = {mandatory: {}};//same behaviour as true
	window.RTC.rayo.constraints.video.optional = [{bandwidth: bandwidth}];
    }
    if (fps) { // for some cameras it might be necessary to request 30fps
	// so they choose 30fps mjpg over 10fps yuy2
	if (!window.RTC.rayo.constraints.video) window.RTC.rayo.constraints.video = {mandatory: {}};// same behaviour as tru;
	window.RTC.rayo.constraints.video.mandatory.minFrameRate = fps;
    }
}  

function positionLarge (videoWidth, videoHeight) 
{
    var videoSpaceWidth = $('#videospace').width();
    var videoSpaceHeight = window.innerHeight;

    var videoSize = getVideoSize(videoWidth, videoHeight, videoSpaceWidth, videoSpaceHeight);

    var largeVideoWidth = videoSize[0];
    var largeVideoHeight = videoSize[1];

    var videoPosition = getVideoPosition(largeVideoWidth, largeVideoHeight, videoSpaceWidth, videoSpaceHeight);

    var horizontalIndent = videoPosition[0];
    var verticalIndent = videoPosition[1];

    positionVideo(  $('#largeVideo'),largeVideoWidth, largeVideoHeight, horizontalIndent, verticalIndent);
    
    if (pdfFrame)
    {
    	$('#pdfViewer').height(largeVideoHeight);
	$('#pdfViewer').width(largeVideoWidth);
    }
};

function positionVideo( video, width, height, horizontalIndent, verticalIndent) 
{
    video.width(width);
    video.height(height);
    
    video.css({  top: verticalIndent + 'px',
                 bottom: verticalIndent + 'px',
                 left: horizontalIndent + 'px',
                 right: horizontalIndent + 'px'});
}

function getVideoPosition (   videoWidth, videoHeight, videoSpaceWidth, videoSpaceHeight) 
{
    // Parent height isn't completely calculated when we position the video in
    // full screen mode and this is why we use the screen height in this case.
    // Need to think it further at some point and implement it properly.
    
    var isFullScreen = document.fullScreen || document.mozFullScreen || document.webkitIsFullScreen; 
    
    if (isFullScreen)
        videoSpaceHeight = window.innerHeight;

    var horizontalIndent = (videoSpaceWidth - videoWidth)/2;
    var verticalIndent = (videoSpaceHeight - videoHeight)/2;

    return [horizontalIndent, verticalIndent];
};

function resizeLarge() 
{
    var availableHeight = window.innerHeight;
    var availableWidth = getAvailableVideoWidth();

    if (availableWidth < 0 || availableHeight < 0) return;

    $('#videospace').width(availableWidth);
    $('#videospace').height(availableHeight);
    $('#largeVideoContainer').width(availableWidth);
    $('#largeVideoContainer').height(availableHeight);

    resizeThumbnails();
}

function getAvailableVideoWidth() 
{
        var chatspaceWidth = $('#chatspace').css("opacity") == 1 ? $('#chatspace').width() : 0;
        return window.innerWidth - chatspaceWidth;
};
  
  
function resizeThumbnails() 
{
    // Calculate the available height, which is the inner window height minus
    // 39px for the header minus 2px for the delimiter lines on the top and
    // bottom of the large video, minus the 36px space inside the remoteVideos
    // container used for highlighting shadow.
    var availableHeight = 100;

    var numvids = $('#remoteVideos>video:visible').length;

    // Remove the 1px borders arround videos and the chat width.
    var availableWinWidth = $('#remoteVideos').width() - 2 * numvids - 50;
    var availableWidth = availableWinWidth / numvids;
    var aspectRatio = 16.0 / 9.0;
    var maxHeight = Math.min(160, availableHeight);
    availableHeight = Math.min(maxHeight, availableWidth / aspectRatio);
    if (availableHeight < availableWidth / aspectRatio) {
        availableWidth = Math.floor(availableHeight * aspectRatio);
    }

    // size videos so that while keeping AR and max height, we have a nice fit
    $('#remoteVideos').height(availableHeight);
    $('#remoteVideos>video').width(availableWidth);
    $('#remoteVideos>video').height(availableHeight);
}

function getVideoSizeCover(videoWidth, videoHeight, videoSpaceWidth, videoSpaceHeight) 
{
    if (!videoWidth)
        videoWidth = currentVideoWidth;
        
    if (!videoHeight)
        videoHeight = currentVideoHeight;

    var aspectRatio = videoWidth / videoHeight;

    var availableWidth = Math.max(videoWidth, videoSpaceWidth);
    var availableHeight = Math.max(videoHeight, videoSpaceHeight);

    if (availableWidth / aspectRatio < videoSpaceHeight) {
        availableHeight = videoSpaceHeight;
        availableWidth = availableHeight*aspectRatio;
    }

    if (availableHeight*aspectRatio < videoSpaceWidth) {
        availableWidth = videoSpaceWidth;
        availableHeight = availableWidth / aspectRatio;
    }

    return [availableWidth, availableHeight];
}

function urlParam(name)
{
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	if (!results) { return undefined; }
	return results[1] || undefined;
}

function doJoin() {
    var roomnode = urlParam("r");

    if (urlParam("n"))
    {
	    nickname = unescape(urlParam("n"));
	    $("#localVideo").attr("title", nickname);
	    window.localStorage.displayname = nickname;    
    }

    console.log("doJoin", roomnode, nickname);
	
    if (!roomnode) {
    	roomnode = Math.random().toString(36).substr(2, 20);
    	window.history.pushState('VideoChat', 'Room: ' + roomnode, window.location.pathname + "?r=" + roomnode);
    }

    roomjid = roomnode + '@' + config.hosts.muc;
    var myroomjid = roomjid;

    if (config.useNicks) {
        var nick = window.prompt('Your nickname (optional)');
        if (nick) {
            myroomjid += '/' + nick;
            nickname = nick;  
            $("#localVideo").attr("title", nickname);
        } else {
            myroomjid += '/' + Strophe.getNodeFromJid(connection.jid);
        }
        
    } else {
    
    	if (nickname)
        	myroomjid += '/' + escape(nickname) + "(" + Math.random().toString(36).substr(2, 5) + ")";
    	else
        	myroomjid += '/' + Math.random().toString(36).substr(2, 20);
    }
            
    connection.addHandler(rayoCallback, 'urn:xmpp:rayo:colibri:1');
    connection.emuc.doJoin(myroomjid);  
    
    if (nickname)
    {
    	var question = urlParam("q");    	
    	
    	if (question) updateChatConversation(nickname, unescape(question));
    	
	$('#nickname').css({visibility:"hidden"});
	$('#ofmeet').css({visibility:'visible'});
	$('#usermsg').css({visibility:'visible'});    
    }
}

 
function rayoCallback(presence) 
{
	console.log("rayoCallback start", presence);
	
	var from = $(presence).attr('from');		

	$(presence).find('offer').each(function() 
	{
		handleOffer(from, this);
	});	

	$(presence).find('removesource').each(function() 
	{
		removeSSRC(from, this);	
	});

	$(presence).find('addsource').each(function() 
	{
		handleAddSSRC(from, this);	
	});
	
	$(presence).find('inviteaccepted').each(function() 
	{
		var callid = $(this).attr("callid");
		var streamId = callid.replace(/[^A-Za-z0-9\+\/\=]/g, "");
		
		$(document).trigger('remoteaudiostreamadded.rayo', [{stream:{id:streamId}}, callid]);
		
		$("#invite").removeClass("fa-spin");
	});
	
	$(presence).find('invitecompleted').each(function() 
	{
		var callid = $(this).attr("callid");
		var streamId = $(this).attr("callid");	
		var streamId = callid.replace(/[^A-Za-z0-9\+\/\=]/g, "");		
		$(document).trigger('remoteaudiostreamremoved.rayo', [{stream:{id:streamId}}]);
		
		$("#invite").removeClass("fa-border");	
		$("#invite").removeClass("fa-spin");		
	});	

	console.log("rayoCallback end", presence);
	
	return true;
};


function removeSSRC(from, removesource) 
{		
	console.log("removeSSRC input ssrc ", removesource);

	var channelId = null;	
	var foundSSRC = false;
	var videobridge = $(removesource).attr('videobridge');							
	var sdp = new SDP(window.RTC.rayo.pc[videobridge].remoteDescription.sdp);

	console.log("removeSSRC unmodified SDP", videobridge);

	$(removesource).find('content').each(function() 
	{		
		var name = $(this).attr('name');		
		var ssrc = null;

		$(this).find('channel').each(function() 
		{
			channelId = $(this).attr('id');
		});
		
		$(this).find('source').each(function() 
		{		
		    ssrc = $(this).attr('ssrc');			    
		});

		if (ssrc != null)
		{	
			var idx = (name == "audio" ? 0 : 1);			
			if (!screenToVideo) sdp.removeMediaLines(idx, 'a=ssrc:' + ssrc);
			foundSSRC = true;
		}
		
	});

	$(document).trigger('remoteaudiostreamremoved.rayo', [{stream:{id:channelId}}]); 
		
	if (foundSSRC)	
	{	
		sdp.raw = sdp.session + sdp.media.join('');

		//console.log("removeSSRC modified SDP", sdp.raw);

		window.RTC.rayo.pc[videobridge].setRemoteDescription(new RTCSessionDescription({type: 'offer', sdp: sdp.raw}		

			), function() {		
			    console.log('removeSSRC modify ok');		    

			}, function(error) {

			    console.log('removeSSRC modify failed');
		});
	}
};


function handleAddSSRC(from, addsource) 
{
	console.log("handleSSRC input ssrc ", addsource);

	var foundSSRC = false;
	var channelId = null;
	var videobridge = $(addsource).attr('videobridge');
	window.RTC.rayo.nickname = $(addsource).attr('nickname');
	
	var sdp = new SDP(window.RTC.rayo.pc[videobridge].remoteDescription.sdp);

	$(addsource).find('content').each(function() 
	{		
		var name = $(this).attr('name');
		
		if (name == "audio") return;

		var lines = '';
		
		$(this).find('channel').each(function() 
		{
			channelId = $(this).attr('id');
		});

		$(this).find('source').each(function() 
		{
		    var ssrc = $(this).attr('ssrc');

		    $(this).find('>parameter').each(function () {
			lines += 'a=ssrc:' + ssrc + ' ' + $(this).attr('name');

			if ($(this).attr('value') && $(this).attr('value').length)
			    lines += ':' + $(this).attr('value');

			lines += '\r\n';
			foundSSRC = true;
		    });				    
		});

		var idx = (name == "audio" ? 0 : 1);
		sdp.media[idx] += lines;	
	});	
	
	if (foundSSRC == false)	// NO SSRC, audio participant, show dummy face
	{
		$(document).trigger('remoteaudiostreamadded.rayo', [{stream:{id:channelId}}, window.RTC.rayo.nickname]);	
	
	} else {
		sdp.raw = sdp.session + sdp.media.join('');
		window.RTC.rayo.addssrc = true;

		window.RTC.rayo.pc[videobridge].setRemoteDescription(new RTCSessionDescription({type: 'offer', sdp: sdp.raw}		

			), function() {
			    console.log('handleAddSSRC modify ok', window.RTC.rayo.pc[videobridge].signalingState);			    

			}, function(error) {
			    console.log('handleSSRC modify failed');
		});	
	}	
};

	
function handleOffer (from, offer) 
{
	console.log("handleOffer", offer);
	
	var audioDirection = "sendrecv";
	var videoDirection = "sendrecv";	
	
	if (window.RTC.rayo.localStream.getVideoTracks().length == 0 )
	{
		videoDirection = "sendonly";
	}
	
	if (window.RTC.rayo.localStream.getAudioTracks().length == 0)
	{
		audioDirection = "sendonly";
	}
	
	var SDPHeader = "v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n";
	var SDPVideo = "m=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=" + videoDirection + "\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\n";
	var SDPAudio = "m=audio 1 RTP/SAVPF 111 0 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=" + audioDirection + "\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\n";


	if (!config.recordVideo)
	{
		SDPVideo +="a=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n"
	}

	var bridgeSDP = new SDP(SDPHeader + SDPAudio + SDPVideo);
	
	var muc = $(offer).attr('muc');
	var nick = $(offer).attr('nickname');
	var participant = $(offer).attr('participant');
	var videobridge = $(offer).attr('videobridge');
	var confid = null;
	var channelId = [];

	window.RTC.rayo.addssrc = false;
	window.RTC.rayo.nickname = "Unknown";

	$(offer).find('conference').each(function() 
	{		
		confid = $(this).attr('id');

		$(this).find('content').each(function() 
		{		
			var name = $(this).attr('name');
			var channel = name == "audio" ? 0 : 1;				
			
			console.log("handleOffer track", name);						

			$(this).find('channel').each(function() 
			{
				channelId[channel] = $(this).attr('id');

				$(this).find('source').each(function() 
				{	
					var ssrc = $(this).attr('ssrc');

					if (ssrc) 
					{
					    bridgeSDP.media[channel] += 'a=ssrc:' + ssrc + ' ' + 'cname:mixed' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + ssrc + ' ' + 'label:mixedlabela0' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + ssrc + ' ' + 'msid:mixedmslabela0 mixedlabela0' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + ssrc + ' ' + 'mslabel:mixedmslabela0' + '\r\n';

					} else {
					    // make chrome happy... '3735928559' == 0xDEADBEEF
					    bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'cname:mixed' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'label:mixedlabelv0' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'msid:mixedmslabelv0 mixedlabelv0' + '\r\n';
					    bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'mslabel:mixedmslabelv0' + '\r\n';
					}							
				});

				$(this).find('transport').each(function() 
				{	
					var pwd = $(this).attr('pwd');
					var ufrag = $(this).attr('ufrag');

					if (ufrag) bridgeSDP.media[channel] += 'a=ice-ufrag:' + ufrag + '\r\n';
					if (pwd) bridgeSDP.media[channel] += 'a=ice-pwd:' + pwd + '\r\n';

					$(this).find('candidate').each(function() 
					{	
						bridgeSDP.media[channel] += SDPUtil.candidateFromJingle(this);						
					});

					$(this).find('fingerprint').each(function() 
					{	
						var hash = $(this).attr('hash');
						var setup  = $(this).attr('setup');
						var fingerprint = $(this).text();

						if (hash && fingerprint) bridgeSDP.media[channel] += 'a=fingerprint:' + hash + ' ' + fingerprint + '\r\n';
						if (setup) bridgeSDP.media[channel] += 'a=setup:' + setup + '\r\n';	

					});							
				});						
			});
				
		});				
	});

	bridgeSDP.raw = bridgeSDP.session + bridgeSDP.media.join('');

	//console.log("bridgeSDP.raw", bridgeSDP.raw);	

   	window.RTC.rayo.pc[videobridge] = new window.RTC.peerconnection(config.iceServers, {'optional': [{'DtlsSrtpKeyAgreement': 'true'}, {googIPv6: config.useIPv6}]}); 

   
	window.RTC.rayo.pc[videobridge].onicecandidate = function(event)
	{
		console.log('candidate', event.candidate);

		if (!event.candidate) 
		{
			sendAnswer(from, videobridge, confid, channelId);
		}

	}

	window.RTC.rayo.pc[videobridge].onaddstream = function(e)
	{
		console.log("onstream", e, window.RTC.rayo.addssrc);

		if (window.RTC.rayo.pc[videobridge].signalingState == "have-remote-offer")
			$(document).trigger('remotestreamadded.rayo', [e, window.RTC.rayo.nickname]);

		window.RTC.rayo.pc[videobridge].createAnswer(function(desc)
		{
			if (!window.RTC.rayo.addssrc)				
				window.RTC.rayo.pc[videobridge].setLocalDescription(desc);
		});				
	};			

	window.RTC.rayo.pc[videobridge].addStream(window.RTC.rayo.localStream);
	window.RTC.rayo.pc[videobridge].setRemoteDescription(new RTCSessionDescription({type: "offer", sdp : bridgeSDP.raw}));
};
	
	
function sendAnswer(from, videobridge, confid, channelId) 
{
	console.log("sendAnswer");

	var remoteSDP = new SDP(window.RTC.rayo.pc[videobridge].localDescription.sdp);	

	//console.log("remoteSDP ", window.RTC.rayo.pc[videobridge].localDescription.sdp);

	var change = $iq({to: from, type: 'set'});
	change.c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', videobridge: videobridge});					
	change.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: confid});

	for (channel = 0; channel < 2; channel++) 
	{
	    if (remoteSDP.media[channel])
	    {
		change.c('content', {name: remoteSDP.media[channel].indexOf('m=audio') > -1 ? 'audio' : 'video'});
		change.c('channel', {id: remoteSDP.media[channel].indexOf('m=audio') > -1 ? channelId[0] : channelId[1]});

		tmp = SDPUtil.find_lines(remoteSDP.media[channel], 'a=ssrc:');			
		change.c('source', { xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });

		tmp.forEach(function (line) {
			var idx = line.indexOf(' ');
			var linessrc = line.substr(0, idx).substr(7);
			change.attrs({ssrc: linessrc});

			var kv = line.substr(idx + 1);
			change.c('parameter');

			if (kv.indexOf(':') == -1) {
			    change.attrs({ name: kv });
			} else {
			    change.attrs({ name: kv.split(':', 2)[0] });
			    change.attrs({ value: kv.split(':', 2)[1] });
			}
			change.up();
		});

		change.up(); // end of source

		var rtpmap = SDPUtil.find_lines(remoteSDP.media[channel], 'a=rtpmap:');

		rtpmap.forEach(function (val) {
			var rtpmap = SDPUtil.parse_rtpmap(val);
			change.c('payload-type', rtpmap);
			change.up();
		});


		change.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});						
		var fingerprints = SDPUtil.find_lines(remoteSDP.media[channel], 'a=fingerprint:', remoteSDP.session);

		fingerprints.forEach(function (line) 
		{
		    var tmp = SDPUtil.parse_fingerprint(line);
		    tmp.xmlns = 'urn:xmpp:jingle:apps:dtls:0';
		    change.c('fingerprint').t(tmp.fingerprint);
		    delete tmp.fingerprint;
		    var line = SDPUtil.find_line(remoteSDP.media[channel], 'a=setup:', remoteSDP.session);

		    if (line) {
			tmp.setup = line.substr(8);
		    }
		    change.attrs(tmp);
		    change.up();
		});

		var candidates = SDPUtil.find_lines(remoteSDP.media[channel], 'a=candidate:', remoteSDP.session);

		candidates.forEach(function (line) {
			var tmp = SDPUtil.candidateToJingle(line);
			change.c('candidate', tmp).up();
		});

		tmp = SDPUtil.iceparams(remoteSDP.media[channel], remoteSDP.session);

		if (tmp) {
			change.attrs(tmp);

		}

		change.up(); // end of transport
		change.up(); // end of channel
		change.up(); // end of content
	   }
	}

	connection.sendIQ(change,
		function (res) {
		    console.log('rayo colibri answer set ok', window.RTC.rayo.pc[videobridge].signalingState);			
		},

		function (err) {
		    console.log('rayo colibri answer got error ' + err);
		}
	);														
	
};

function toggleVideo() 
{
        if (!connection && !window.RTC.rayo.localStream) return;
        
        for (var idx = 0; idx < window.RTC.rayo.localStream.getVideoTracks().length; idx++) {
            window.RTC.rayo.localStream.getVideoTracks()[idx].enabled = !window.RTC.rayo.localStream.getVideoTracks()[idx].enabled;
        }
}

function toggleAudio() 
{
        if (!connection && !window.RTC.rayo.localStream) return;
        
        for (var idx = 0; idx < window.RTC.rayo.localStream.getAudioTracks().length; idx++) {
            window.RTC.rayo.localStream.getAudioTracks()[idx].enabled = !window.RTC.rayo.localStream.getAudioTracks()[idx].enabled;
        }
}

function registerRayoEvents()
{	
	connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'offer', muc: roomjid}),
		function (res) {
		    console.log('rayo colibri register set ok');			    
		},

		function (err) {
		    console.log('rayo colibri register got error', err);
		}
	);
}

function unregisterRayoEvents(constraints, resolution)
{			
	connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'expire', muc: roomjid}),
		function (res) {
		    console.log('rayo colibri unregister set ok');
		    window.RTC.rayo.localStream.stop();		    
		    
		    if (constraints)
		    {
		    	setTimeout(function()
		    	{
		    		screenToVideo = false;
				getConstraints(constraints, resolution);		
				getUserMedia();	
			}, 1000);
		    }
		},

		function (err) {
		    console.log('rayo colibri unregister got error', err);
		}
	);	
}

function toggleScreenShare()
{
	console.log('toggleScreenShare', window.RTC.rayo.constraints);
		
	var videobridge = Strophe.getNodeFromJid(roomjid);

	if (screenShare)
	{	
		screenToVideo = true;
		unregisterRayoEvents(['audio', 'video'], config.resolution);		
		$("#screen").removeClass("fa-border");

	} else {
		unregisterRayoEvents(['screen']);
		$("#screen").addClass("fa-border");		
	}	
	
	screenShare = !screenShare;
}

function updateChatConversation(nick, message)
{
	var timestamp = new Date();

	//console.log("updateChatConversation", nick, message, timestamp);

	if (!nick) nick = "System";

	divClassName = "Out";

	if (nickname == nick) 
		divClassName = "In";    
	else {
                unreadMessages++;
                setVisualNotification(true);                
	}

	var content = '<div class="message message' + divClassName + '">' 
		+'<span class="msgText">' + setEmoticons(message) + '</span>'
		+'<span class="msgPerson">' + nick + '<span class="msgTime">&nbsp;-&nbsp;' + new Date().format("m-d-Y H:i:s") + '</span></span>'
	    +'</div>'; 

	$('#ofmeet-log').append(content);
	$('#ofmeet-log').animate({ scrollTop: $('#ofmeet-log')[0].scrollHeight}, 1000);	         
}

function buttonClick(id, classname) {
    console.log("buttonClick", id, classname);
    
    $(id).toggleClass(classname); // add the class to the clicked element
}

function openLockDialog() {
    if (sharedKey)
        $.prompt("Are you sure you would like to remove your secret key?",
        {
            title: "Remove secrect key",
            persistent: false,
            buttons: { "Remove": true, "Cancel": false},
            defaultButton: 1,
            submit: function(e,v,m,f){
                if(v)
                {
                    sharedKey = '';
                    lockRoom();
                }
            }
            });
    else
        $.prompt('<h2>Set a secrect key to lock your room</h2>' +
                 '<input id="lockKey" type="text" placeholder="your shared key" autofocus>',
                {
                    persistent: false,
                    buttons: { "Save": true , "Cancel": false},
                    defaultButton: 1,
                    loaded: function(event) {
                        document.getElementById('lockKey').focus();
                    },
                    submit: function(e,v,m,f){
                    if(v)
                    {
                        var lockKey = document.getElementById('lockKey');

                    if (lockKey.value != null)
                    {
                        sharedKey = lockKey.value;
                        lockRoom(true);
                    }
                }
            }
        });
}

function openLinkDialog() {
    $.prompt('<input id="inviteLinkRef" type="text" value="' + roomUrl + '" onclick="this.select();">',
    {
	title: "Share this link with everyone you want to invite",
	persistent: false,
	buttons: { "Share": false},
	loaded: function(event) {
		document.getElementById('inviteLinkRef').select();
	},
	submit: function(e,v,m,f) {
		connection.emuc.sendMessage(roomUrl, nickname);
	}
    });
}

function lockRoom(lock) {
    connection.emuc.lockRoom(sharedKey);    
    buttonClick("#lockIcon", "fa fa-unlock fa-lg fa fa-lock fa-lg");
}

function openPDFDialog() 
{
    if (pdfShare) 
    {
        $.prompt("Are you sure you would like to remove your Presentation?",
                {
                title: "Remove PDF Presentation",
                buttons: { "Remove": true, "Cancel": false},
                defaultButton: 1,
                submit: function(e,v,m,f)
                {
			if(v)
			{
				connection.emuc.pdfShare("destroy", pdfShare);
				pdfStop(pdfShare);
				pdfShare = null;
				
				$("#pdf").removeClass("fa-spin fa-border");	
			}
            	}
        });
    }
    else if (pdfFrame != null) {
        $.prompt("Another participant is already sharing a Presentation. This conference allows only one Presentation at a time.",
                 {
                 f: "Share a PDF Prsentation",
                 buttons: { "Ok": true},
                 defaultButton: 0,
                 submit: function(e,v,m,f)
                 {
                    $.prompt.close();
                 }
        });
    }
    else {
	$.prompt('<h2>Share a Presentation</h2><input id="pdfiUrl" type="text" placeholder="e.g. http://www.ge.com/battery/resources/pdf/CraigIrwin.pdf" autofocus >',
	{
                title: "Share a PDF Prsentation",
            	persistent: false,
            	buttons: { "Share": true , "Cancel": false},
            	defaultButton: 1,
		loaded: function(event) {
			document.getElementById('pdfiUrl').select();
		},
		submit: function(e,v,m,f) 
		{
			if(v)
			{
				pdfShare = document.getElementById('pdfiUrl').value;
		
				if (pdfShare)
				{	
					pdfStart(pdfShare  + "&control=true");
					connection.emuc.pdfShare("create", pdfShare  + "&control=false");
				}
			}					 
		}
	});    
    }
}

function pfdReady()
{
	if (pdfFrame != null)
	{
		console.log("pdfReady");
		$("#pdfViewer").css("display", "block");
		$('#pdfViewer').height($('#largeVideo').height());
		$('#pdfViewer').width($('#largeVideo').width());
		$("#largeVideo").css("display", "none");
		$("#pdf").removeClass("fa-spin");
		if (pdfShare) $("#pdf").addClass("fa-border");
	}
}


function pdfStart(url)
{
	console.log("pdfStart", url);	
	pdfFrame = document.getElementById("pdfViewer");
	pdfFrame.contentWindow.location.href = "/jitsi/apps/ofmeet/pdf/index.html?pdf=" + url;
	$("#pdf").addClass("fa-spin");
}

function pdfStop(url)
{
	console.log("pdfStop", url);	
	$("#pdf").removeClass("fa-border fa-spin");		
	$("#largeVideo").css("display", "block");
	$("#pdfViewer").css("display", "none");	
	pdfFrame = null;
}

function pfdGoto(page)
{
	console.log("pfdGoto", page);
	
	pdfPage = page;
	
	if (pdfShare != null)
	{
		connection.emuc.pdfShare("goto", pdfShare + "#" + page);
	}
}

function handlePdfShare(action, url)
{
	console.log("handlePdfShare", url, action);
	
	if (pdfShare == null)
	{
		if (pdfFrame == null) 
		{
			if (action == "create") pdfStart(url);		
		
		} else {

			if (action == "destroy") pdfStop(url);	
			if (action == "goto") pdfFrame.contentWindow.location.href = "/jitsi/apps/ofmeet/pdf/index.html?pdf=" + url;
		}
	}
}


function openChat() {
    var chatspace = $('#chatspace');
    var videospace = $('#videospace');
    var chatspaceWidth = chatspace.width();

    $('#usermsg').focus(function(){
    	resetVisualNotification();
    });

    if (chatspace.css("opacity") == 1) {
        chatspace.animate({opacity: 0}, "fast");
        chatspace.animate({width: 0}, "slow");
        videospace.animate({right: 0, width:"100%"}, "slow");
    }
    else {
        chatspace.animate({width: "20%"}, "slow");
        chatspace.animate({opacity: 1}, "slow");
        videospace.animate({right:chatspaceWidth, width:"80%"}, "slow");
    }
    
    resizeLarge();
    positionLarge();    
    
    // Request the focus in the nickname field or the chat input field.
    if ($('#nickinput').is(':visible'))
        $('#nickinput').focus();
    else
        $('#usermsg').focus();
}

function hideToolbar() 
{
    var isToolbarHover = false;
    $('#header').find('*').each(function(){
        var id = $(this).attr('id');
        if ($("#" + id + ":hover").length > 0) {
            isToolbarHover = true;
        }
    });

    clearTimeout(toolbarTimeout);
    toolbarTimeout = null;

    if (!isToolbarHover) {
        $('#header').hide("slide", { direction: "up", duration: 300});
    }
    else {
        toolbarTimeout = setTimeout(hideToolbar, 5000);
    }
};


function showToolbar() 
{
    if (!$('#header').is(':visible')) {
        $('#header').show("slide", { direction: "up", duration: 300});

        if (toolbarTimeout) {
            clearTimeout(toolbarTimeout);
            toolbarTimeout = null;
        }
        toolbarTimeout = setTimeout(hideToolbar, 5000);
    }
}


function dockToolbar(isDock) {
    if (isDock) {
        // First make sure the toolbar is shown.
        if (!$('#header').is(':visible')) {
            showToolbar();
        }
        // Then clear the time out, to dock the toolbar.
        clearTimeout(toolbarTimeout);
        toolbarTimeout = null;
    }
    else {
        if (!$('#header').is(':visible')) {
            showToolbar();
        }
        else {
            toolbarTimeout = setTimeout(hideToolbar, 5000);
        }
    }
}

function updateRoomUrl(newRoomUrl) {
    roomUrl = newRoomUrl;
}

function toggleFullScreen() {
    var fsElement = document.documentElement;

    if (!document.mozFullScreen && !document.webkitIsFullScreen){

        //Enter Full Screen
        if (fsElement.mozRequestFullScreen) {
            fsElement.mozRequestFullScreen();
        }
        else {
            fsElement.webkitRequestFullScreen(Element.ALLOW_KEYBOARD_INPUT);
        }
    } else {
        //Exit Full Screen
        if (document.mozCancelFullScreen) {
            document.mozCancelFullScreen();
        } else {
            document.webkitCancelFullScreen();
        }
    }
}

function inviteParticipant()
{
    if (sipUri == null)
    {
	$.prompt('<h2>Enter SIP address or Telephone number to invite a person by phone</h2><input id="sipUri" type="text" placeholder="sip:name@domain or tel:nnnnnnnn" autofocus >',
	{
                title: "Invite Participant by Phone",
            	persistent: false,
            	buttons: { "Invite": true , "Cancel": false},
            	defaultButton: 1,
		loaded: function(event) {
			document.getElementById('sipUri').select();
		},
		submit: function(e,v,m,f) 
		{
			if(v)
			{
				sipUri = document.getElementById('sipUri').value;
				$("#invite").addClass("fa-border");
				
				connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'invite', muc: roomjid, from: "sip:" + roomjid, to: sipUri}),
					function (res) {
					    console.log('rayo colibri invite ok');
					},

					function (err) {
					    console.log('rayo colibri invite error', err);
					}
				);
			}					 
		}
	}); 
	
    } else {
    
        $.prompt("Are you sure you would like to remove the Phone Participant?",
                {
                title: "Remove Participant by Phone",
                buttons: { "Remove": true, "Cancel": false},
                defaultButton: 1,
                submit: function(e,v,m,f)
                {
			if(v)
			{				
				connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'uninvite', muc: roomjid, callId: sipUri}),
					function (res) {
					    console.log('rayo colibri uninvite ok');
					    sipUri = null;
					},

					function (err) {
					    console.log('rayo colibri uninvite error', err);
					    sipUri = null;					    
					}
				);	
			}
            	}
        });  
    
    }
}

function setEmoticons(body) 
{
	if (body)
	{	
		body = body.replace(/:\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZSAPrkL1pSI/jhMFlTTPPYNPvnLZCEJnttUuvIOfniMIF7du7OOJ6RJsKgN/vmLYRtLoRsL4FoL/niL/HTNu/cKvHUNYRtL/nhMPnjL/rlLoNrL8WnNYFmMPbcMvffMdixO969OI+DJvHUNu/PN+7MOO/QN+zLOaudJ3xvUWdeI4J8dntsUoBmMIJ3JYVwLu3MOL+aOPznLPLVNYF8dvDRN926Of3oLHxwUerGOtq1OsKiN/jgMJ2QJ4+LhPTaM9TDKXRqJe/POPvlLmdeJD44IdPBK+LQKuDOK9LAK5yPKHZzdO3aLExFIoJ9dlhSTPfeMY+KhPzoLPHx8dbV1f3pK////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABSACwAAAAAEAAQAAAH1YBSgoIKKywcBzODi1JQER84LyQIORpQjE4wJgRPAhcePiMNA4NQMEFPGQVRUTFCOyI6l1IRJk8OISlUVCkGBQI0D1IKHwQZUQEBu8lUDh01Kis4TwVUDAa7BidUUQkLKCwvAlG75eU2ABMQHCQX5ObnABUWBwgeMfDlURIlNzM5PpC0oGCOQosiT0AokaJhxI4kTBj8MHKCCA8MMlwIgtJAhIAlPIAMCXEEA4ENPQYN0EGjQwIAACQ8kbGB1CIoD2osmFChBAgXKRkJUoECgoUbTRgFAgA7' border='0'>");
		body = body.replace(/:-\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZSAPrkL1pSI/jhMFlTTPPYNPvnLZCEJnttUuvIOfniMIF7du7OOJ6RJsKgN/vmLYRtLoRsL4FoL/niL/HTNu/cKvHUNYRtL/nhMPnjL/rlLoNrL8WnNYFmMPbcMvffMdixO969OI+DJvHUNu/PN+7MOO/QN+zLOaudJ3xvUWdeI4J8dntsUoBmMIJ3JYVwLu3MOL+aOPznLPLVNYF8dvDRN926Of3oLHxwUerGOtq1OsKiN/jgMJ2QJ4+LhPTaM9TDKXRqJe/POPvlLmdeJD44IdPBK+LQKuDOK9LAK5yPKHZzdO3aLExFIoJ9dlhSTPfeMY+KhPzoLPHx8dbV1f3pK////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABSACwAAAAAEAAQAAAH1YBSgoIKKywcBzODi1JQER84LyQIORpQjE4wJgRPAhcePiMNA4NQMEFPGQVRUTFCOyI6l1IRJk8OISlUVCkGBQI0D1IKHwQZUQEBu8lUDh01Kis4TwVUDAa7BidUUQkLKCwvAlG75eU2ABMQHCQX5ObnABUWBwgeMfDlURIlNzM5PpC0oGCOQosiT0AokaJhxI4kTBj8MHKCCA8MMlwIgtJAhIAlPIAMCXEEA4ENPQYN0EGjQwIAACQ8kbGB1CIoD2osmFChBAgXKRkJUoECgoUbTRgFAgA7' border='0'>");
		body = body.replace(/:\(/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZiANDn8cji74aKjsXg7qrR6kdpgLLV63p8gFBSVnqDibjY7FhdYbDU64eKjpDD5Z7L6M7m8M/m8E1QVIrB5JfH5rHV67TW65bG5rzb7cXg75jH5qvS6nh7gGaava/T6pLE5b7c7a3T6kVof26evmiq1LXX65jI5onA5Hix1szk78Pe7lqWu6fP6Xh8gIe/5KLN6FhreU1rgW+t1YiKjl1uej1lfjxlfo7D5Y3C5U9RVFlseUlpgMDd7sXa5Mbg77rZ7LfY7E5RVIK942SavH2z18He7qu+yF5uekBmf8ni70NER3+74nZzdG93e4+cosbb5J2wu6q+yMHd7p/L6FFTVsfh74/D5YSPlXqJkzk4On+84o+co6m9yL7b7Whyep6xu4WQlnmDiPHx8c3l8NbV1dHn8f///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABiACwAAAAAEAAQAAAH6IBigoIcMDY1Oi2Di2ICSCRaJxNCMiICjAgrLg8bFQYhLzdDCIMCK1YEP0UDGSoYHhodDYJILgRdUAtjYwtfUgwfO2IcJA8/VRJBAABBEkkgLCgHMEsbRSlXCWVlCU5jAwY4NDYnFQNjANvqEAFAFwU1EwYZ6OrbEQEKFAU6QiEqKXqAyZGjSY8xPiw4ONJCxgsMXJSEeVJmSxYjPAgQYSJGxA0PWKKMgRBhjBEvJUzEECRgiAYGIAYECOCDR4kpI2YMQtDhAwsDQBRYIGBiBBVGDXagwHGBggMiMXQyEnSARoECRw4wCgQAOw==' border='0'>");
		body = body.replace(/:-\(/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZiANDn8cji74aKjsXg7qrR6kdpgLLV63p8gFBSVnqDibjY7FhdYbDU64eKjpDD5Z7L6M7m8M/m8E1QVIrB5JfH5rHV67TW65bG5rzb7cXg75jH5qvS6nh7gGaava/T6pLE5b7c7a3T6kVof26evmiq1LXX65jI5onA5Hix1szk78Pe7lqWu6fP6Xh8gIe/5KLN6FhreU1rgW+t1YiKjl1uej1lfjxlfo7D5Y3C5U9RVFlseUlpgMDd7sXa5Mbg77rZ7LfY7E5RVIK942SavH2z18He7qu+yF5uekBmf8ni70NER3+74nZzdG93e4+cosbb5J2wu6q+yMHd7p/L6FFTVsfh74/D5YSPlXqJkzk4On+84o+co6m9yL7b7Whyep6xu4WQlnmDiPHx8c3l8NbV1dHn8f///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABiACwAAAAAEAAQAAAH6IBigoIcMDY1Oi2Di2ICSCRaJxNCMiICjAgrLg8bFQYhLzdDCIMCK1YEP0UDGSoYHhodDYJILgRdUAtjYwtfUgwfO2IcJA8/VRJBAABBEkkgLCgHMEsbRSlXCWVlCU5jAwY4NDYnFQNjANvqEAFAFwU1EwYZ6OrbEQEKFAU6QiEqKXqAyZGjSY8xPiw4ONJCxgsMXJSEeVJmSxYjPAgQYSJGxA0PWKKMgRBhjBEvJUzEECRgiAYGIAYECOCDR4kpI2YMQtDhAwsDQBRYIGBiBBVGDXagwHGBggMiMXQyEnSARoECRw4wCgQAOw==' border='0'>");
		body = body.replace(/:D/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZVAMjHx2deI/PYNPvnLVlTTPjhMIFmMI6AKIJ8doBmMIRsL4RtL+/PN4RtLu7OOIF8dsKiN7+aOPnhMCMfIIFoL/3oLPvmLXxvUfrkL8KgN/DRN/HUNe/QN+zWLp+dnd69OPLVNa2rq4VwLsWnNe7MOPHUNlpXWHRpJY+LhOvIOe3MONixO9q1Orq5ud26OXxwUfHTNuzLOfbcMurGOoNrL3ttUvrlLntsUvTaM4F7dqudJ+rUL3ZzdFlRJI+DJvznLPjgMPvlLtC8LdG9LUxEI5uLKVlQJPffMe/POFhSTEtEI+vUL5qKKvniMIJ9dp6RJvfeMfzoLI+KhFpSI5CEJvHx8dbV1f3pK////////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABVACwAAAAAEAAQAAAH2oBVgoI5NwkGNQ+Di1VSFCszKiQpLDRSjEkRMQJQBRJHOAwZBINSEUhQNgNRUT9BQCUQl1UUMVAWPgFXVwFUAwUaDVU5KwI2UVNTu8lXFjIuCDczUANXT1S7VDpXUU0OFwkqBVG75eUVGDAKBiQS5ObnGBsLNSlME8lU+lQBUxNFHF48YIHDSAgsABICwBKiB5QPPKrQYLBDiQcsGLF4INIBhAhBUjKUWHLCRIsWJk50EDACxSACEDTIEHLgwBAoIEaQWiSlgQsHMDZw+CDCJSNBCC4oWPDCCaNAADs=' border='0'>");
		body = body.replace(/:x/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAPcAAKyrq/Hx8dbV1YNyMP3oN2ZiWunNPiMfIPXeOu7UPe/cNuzSPT44I396asjHx395anRqKXBoUHpvRvrkOGdeJ+zRPvbfOufLP4J9du7VPe3TPbq5ufPbO9PCM9m+PfLaO+iMbfPcO9TDM3hrOndpOvbeOpuHNOiNbvnjOJ+NM+DNNdzIN7SdOO7aNuiDY+DGPN/MNfDYPNyAY7GZOGNMMPXdO5CELGFVKrioMPHZPMa2McuxPHVqKWBUNtxWO/zmOPDXPGRdTuh8XO/VPC8pIjArItvAPcNrUX11XtyFaKqcL+7bNtzBPXBpUNrAPeXJP6udLvzoN/nkOOh5WJ2KM9i8PoteK/rlOOrVOFlRJoY4K+rPPvvlOPjjOXBcKvjhOe/VPfHYPIFvMeiObuiLbJ5fS8SzMnBNRcOxMod4L29mUIJ2Kox+LWZZWefROXFrXJ5eSpmKL+XIP5yJNHBlKu3YNvTdO+HPNG5MOHVrKXs8NPznN7agN+hcP/jiOcKxM892W3ZmLuiOb9zCPZ2QLZJdTGxgOOjNPm9nUPbgOnRpKXlOOrObOH9yLOLQNP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAAAAACwAAAAAEAAQAAAI7gABCBz4gIQYEg8GKhSoZoYcA1sMPDHxZiGAHlUqhAlhJ0SOBR4iDGwTZEcaL1hQTJDypUYGDwUA6Omz6MYRH2s6HOiAo4gbDSYAaBGSpBCZKVagHHik48CfDxcwnHFxQtAJQDYcUXikIEsdBBUkAIADYgwIGgoeqX1E4EeiBAMAECkjA48IEWvZtliRIW6gFGxg3Nm6NoqKRi/EIrnwwY8SCHnSEliiiA4fDAIZJSgxYQ8hBjwgMIgDZs5ARE6AlOhyhYsZNBZiMIk50JCRBBwQWEDAYcigJhYjsDi0QMMCA1RoWwTQYMSAEQ0WBgQAOw==' border='0'>");
		body = body.replace(/;\\/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZaAOvQPvLaO4J9dvbfOvrkOHxzUu3TPXtxUvjiOYNyMPvlOOrOPvznN/TcO1lUTPfhOZ6RLfvmOPTdO4FvMWdeJ+7UPe3SPde7PtzBPfnjOfXeO+jMP/XdO+vRPoF9dsCoOoFwMP3oN/HZPFpSJufLP/znOOzRPoR0MIJxMNi9PbmqMIBvMfnkOIJyMPrlOL6kOtrAPcCnOsKrOffgOT44I5uMLnNpKdK/NFlRJu/WPO/bNtPCM+7aNnVrKa+cNPDXPIp8Lsy4NuXOOkxFJJWEMN3KNu/cNvnjOODNNXZzdDEsIe7bNunNPlhTTPPbO8e2Me/WPSMfILehN+LQNHRqKc+8NfPcO9TDM56QLcy5NvHx8dbV1Y+LhPzoN/3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABaACwAAAAAEAAQAAAH54BagoIeBysTBwKDi1pcIBckCwsbKShcjE0vUkRCDVlAPgAxDoNcLwABVTYsNxk1QQYfl1ogTAEPLjxdXUtIRQ0mCVoeF1ADLlQUXipKXREPIjACByQBCAw4I14QUUYMBBIABSsLDSxdKhBeU8pdCgMWLRMLEgRdXvj5Ie8GCQcbTjIwwHIFH4QnXQhw6FBAQIofMyLsGDKCRg8dJRAEwJBECwoAVhBEYNAlRJcSRzRUOCGISwwDDR4QUKCAAAINOWTM0uLggwkREgYM4BCgggxSi7gkgAHAgoEOGE7sZCSgQIsEDRkFAgA7' border='0'>");
		body = body.replace(/B-\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZdAOvQPvLaO4J9dvbfOvTcO+3TPXxzUntxUoNyMFlRJurOPllUTPzoN/rkOPTdO+/cNvznN/njOYJyMOvRPujMP8KrOdrAPfXdO+7UPTEsIfPbO+3SPdzBPenNPoJxMKudLufLP8CoOvjiOYBvMYF9doFvMZuNLsCnOuzRPr6kOte7PvPcO4R0MOHPNPXeO/DXPHZzdD44I4FwMNi9Pe/bNo6BLFpRJse3MdC9NKmbL6maL/rlOFlQJu/WPXNpKYJ2KoJ3KvfhOUtEJJ6XazEtLqqjesq1N+/WPOXOOlhTTPHZPJ2QLWZcKGdfNMiyN1hPJvvmOMe2MdTCM93JNurVOM+8NfvlOPnkOOfROZqMLrmqMIN+affgOfHx8dbV1Y+LhCMfIP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABdACwAAAAAEAAQAAAH4YBdgoIkByMlBwKDi11fMiogCgoUMx5fjEkpHT0BBA4aLwAnC4NfKQABAyJXDRFcKwUhl10yHQFBOxAMDBBQIgQoCF0kKk5FPAkJNkNNyU9GFgIHIEgwYNcxW0TXYFgABiMKBDrXGR9aGdc5AxsSJQoODTRRD2FhDzctVgMFCAcUGiJAsEcwDIMGFyYYEDDjRZUfNAo+AIIjAAcYXTwAWJFFyBIpLT7EMOECAwtBX04UIEDFhA8mNaa4OFJhVpcFIVAocTBgwIUAGCqQWvQFgQUAGwpM4MDCJiMBBiQgWMgoEAA7' border='0'>");
		body = body.replace(/8-\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZdAOvQPvLaO4J9dvbfOvTcO+3TPXxzUntxUoNyMFlRJurOPllUTPzoN/rkOPTdO+/cNvznN/njOYJyMOvRPujMP8KrOdrAPfXdO+7UPTEsIfPbO+3SPdzBPenNPoJxMKudLufLP8CoOvjiOYBvMYF9doFvMZuNLsCnOuzRPr6kOte7PvPcO4R0MOHPNPXeO/DXPHZzdD44I4FwMNi9Pe/bNo6BLFpRJse3MdC9NKmbL6maL/rlOFlQJu/WPXNpKYJ2KoJ3KvfhOUtEJJ6XazEtLqqjesq1N+/WPOXOOlhTTPHZPJ2QLWZcKGdfNMiyN1hPJvvmOMe2MdTCM93JNurVOM+8NfvlOPnkOOfROZqMLrmqMIN+affgOfHx8dbV1Y+LhCMfIP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABdACwAAAAAEAAQAAAH4YBdgoIkByMlBwKDi11fMiogCgoUMx5fjEkpHT0BBA4aLwAnC4NfKQABAyJXDRFcKwUhl10yHQFBOxAMDBBQIgQoCF0kKk5FPAkJNkNNyU9GFgIHIEgwYNcxW0TXYFgABiMKBDrXGR9aGdc5AxsSJQoODTRRD2FhDzctVgMFCAcUGiJAsEcwDIMGFyYYEDDjRZUfNAo+AIIjAAcYXTwAWJFFyBIpLT7EMOECAwtBX04UIEDFhA8mNaa4OFJhVpcFIVAocTBgwIUAGCqQWvQFgQUAGwpM4MDCJiMBBiQgWMgoEAA7' border='0'>");
		body = body.replace(/:p/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZZAPzoN4J9dvLaO+vQPvTcO3xzUurOPu3TPfjiOYNyMPbfOntxUllUTPrkOPTdO/fhOZCELMKrOYJyMNi9PenNPtzBPfPbO9rAPfvlOPPcO+jMP+7UPde7PvHZPO3SPYF9dvDXPMCoOvznN+ViRsCnOvnkOOzRPr5UP4BvMfXeO2VUKYFvMb6kOmdeJ4R0MFpRJllRJuvRPu/WPVpSJpdHN+fLP4JxMIFwMFhTTD44I+/WPOvWN+vXN/XdO0s9JezYN35qLfnjOY+CLNPBM/vmOMa2Md7KNeXOOoF2K+fROf3oN2ddKHVrKYtgMXZzdMa1MX5rLaudLp6QLenUOPfgOdhdRPrlOPJmSY6CLPHx8dbV1Y+LhP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABZACwAAAAAEAAQAAAH34BZgoIfCygrCwGDi1lbNxw1BgYaEzZbjDgsFDICBA4WIAMkDINbLAMCCgglDUFUGQchl1k3FAIPWC0AAC1CCAQmCVkfHDIKVjAvXFwvMEQPHRcBCzUCCCJSEMsQUSINDgMFKAYEJQDL6FwAGAoeEisGDg3n6VxK7AcJCxoWPE/1XIr86BGjQIAJIKYgyTGDCYQZPpbsEFDBSRYbAzIgGELjxIkqNIyk2OBC0BYSBwg8UDHiyggVKXREmJWFQQgTHZJAaQLkyIYIpBZtSXBhgIcDMSq4oMkoQAEJCQwyCgQAOw==' border='0'>");
		body = body.replace(/X-\(/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZdAO64qOu2pXJNOm5IM9qehI2GhOixn92iilZGQuSsmOOqley2plZPTYhrZNKSdNqehdKTdeGnkaZtUHJOO3NdVOCmj42GhdGSdOWtmY2Hhe24qLd2UsuJaNibgcyKanRQPsyLauiyoMiFYnFMON2iidGRc6JoSNufhteZfeewnc2MbL19Xc6Nbbp5VtWWenNgVuWumuq0o+q1pHNgV6lyVtyhiIlsZYlsZHJdU8+PcM+Ob96ji7+AYd6kjN+kjaVsTsmHZeKplG9KNeCmkH95dnZzdM6NbnleV8SYiXlfWIhsZKJ/dbmQhOy3p6F+de23p4drY1ZOTOixnohrY8SXiLmQg+u1pOmzouaum9WXeu65qH94duavnPHx8dbV1e65qf///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABdACwAAAAAEAAQAAAH5oBdgoJbOAMDFFuDi10FQhsiHiBALSMFjFEmHC4PBwcnKCw/DIMFJkYEERhcXDBBNQ4SFoJCHAQKUjEBATIhCSQ5E11bGy4RR1VPAAAaTEkKHSuFIg8YMU4IX18IS1ZcOyovAx4HXAEN2ds3CwYVJQIDIOUBSjbaNusGQxcCFEAnMGRo0KYNQIAUPXTM2NICRZAQCwBo+QKgyZUEBHgU6TKCRY0EVKA0aDAFCRYfED4IKvDDAQkFXAwYSJHARxYaGQYxkJCjw44KQ3oQgECD1CILE1aoKHFBB48PORkReiFAwAwijAIBADs=' border='0'>");
		body = body.replace(/:\^O/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZTAMe3KPjhMFlTTPPYNPzoLIJ8doBmMOvIOe/QN3tsUvrkL+zLOY6AKPnjL3ttUu7OOOrGOoF8doRsL+HPKuDOK4FmMHxvUfLVNYRtLsWnNd69OINrL9ixO+/PN/TaM3xwUfHUNe3MOIRtLzEsIfDRN0xJSvffMdq1OnRqJe7MOIVwLr+aOMKgN/bcMt26OfjgMPnhMIFoL/HUNsKiN4F7dtTDKY+LhPHTNoJ9dj46L/niMLakK8W1KnZzdFlRJJ2QJ+/POO3YLeDMLIJ2JvvnLbWkK4F2JuDNLO3ZLVhSTPniL/rlLllQJD45L/vmLcW1KffeMVpXWI+KhPHx8cjHx9bV1ZCEJv3pKyMfIP///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABTACwAAAAAEAAQAAAH0IBTgoI0CQYVDhGDi1NSMRwQISkHJxtSjEkrCwNQATAmHh0sAoNSK0BQQTwEBE9ILzIzl1MxC1BHI1gAAFgjFAEkGFM0HANLEzUAV1cANRNOLS4FCRBQRMvY2AQ6DxYGIQE/VuPk5Ao3EhUpME1U7u/uOQogIg4HJiglUfv8JShKCD5EOOFBCJaDCA9SgKKhx5QNHV4YSXhwSIMLKgRJYSEjAJOEPhoMyGBjkIAZJFoUYcBgB5QLGUgtkoLBxYMbIBBoUFGSkaACFiSI+ICDUSAAOw==' border='0'>");
		body = body.replace(/:\^0/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZTAMe3KPjhMFlTTPPYNPzoLIJ8doBmMOvIOe/QN3tsUvrkL+zLOY6AKPnjL3ttUu7OOOrGOoF8doRsL+HPKuDOK4FmMHxvUfLVNYRtLsWnNd69OINrL9ixO+/PN/TaM3xwUfHUNe3MOIRtLzEsIfDRN0xJSvffMdq1OnRqJe7MOIVwLr+aOMKgN/bcMt26OfjgMPnhMIFoL/HUNsKiN4F7dtTDKY+LhPHTNoJ9dj46L/niMLakK8W1KnZzdFlRJJ2QJ+/POO3YLeDMLIJ2JvvnLbWkK4F2JuDNLO3ZLVhSTPniL/rlLllQJD45L/vmLcW1KffeMVpXWI+KhPHx8cjHx9bV1ZCEJv3pKyMfIP///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABTACwAAAAAEAAQAAAH0IBTgoI0CQYVDhGDi1NSMRwQISkHJxtSjEkrCwNQATAmHh0sAoNSK0BQQTwEBE9ILzIzl1MxC1BHI1gAAFgjFAEkGFM0HANLEzUAV1cANRNOLS4FCRBQRMvY2AQ6DxYGIQE/VuPk5Ao3EhUpME1U7u/uOQogIg4HJiglUfv8JShKCD5EOOFBCJaDCA9SgKKhx5QNHV4YSXhwSIMLKgRJYSEjAJOEPhoMyGBjkIAZJFoUYcBgB5QLGUgtkoLBxYMbIBBoUFGSkaACFiSI+ICDUSAAOw==' border='0'>");
		body = body.replace(/;\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZTAPvnLVpSI/rkL/jhMPPYNFlTTIJ3JZ6RJnVrJHtsUvznLPniL6udJ4RtL4F7doNrL9q1OsKiN4RtLoFoL7+aOMKgN4VwLuzLOYFmMI+LhPHUNfrlLt26Oe/QN/ffMfbcMvLVNdixO/niMO3MOIRsL+7MOMWnNYJ8dvnhMP3oLOrGOnxvUfnjL+/PN4BmMO7OOHxwUd69OOLQKkxFIpCEJvDRN4F8dvHUNvTaM3ttUvHTNuvIOYF2Ju/cKo+CJ+rUL+HPKsW1Ke/cK8a2KXZzdPvmLcW0Ku3aLFhSTCMfIPvlLmdeI+/POD44IYJ9dp2QJ/jgMPfeMY+KhPHx8fzoLNbV1f3pK////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH5BAEAAFMALAAAAAAQABAAAAfXgFOCgg4JLhg5NoOLU1ITISojJTsQD1KMSBQXBFEDKB44LRUFg1IUTFEbAFRUCkpQNxGXUxMXUT5PVrq6AAM1ElMOIQQbSQFWBwxWMlZFHxwnCSpRAAFLVj0IAQEyVCIvKy4jA1S75lYpAjokGCUo5ee66RoNOTseCvEGVlQLHTA2IOAwcsAcgxkGAESJQWTKgxY/eDShQWMGAiFUWICwIEhKhRsDjgQZAoQKABYETGQYVCBCjQ8iBAhYEAWECVKLpEjg8EKHhg4xLKxkJOjEChINYDhhFAgAOw==' border='0'>");
		body = body.replace(/;-\)/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZTAPvnLVpSI/rkL/jhMPPYNFlTTIJ3JZ6RJnVrJHtsUvznLPniL6udJ4RtL4F7doNrL9q1OsKiN4RtLoFoL7+aOMKgN4VwLuzLOYFmMI+LhPHUNfrlLt26Oe/QN/ffMfbcMvLVNdixO/niMO3MOIRsL+7MOMWnNYJ8dvnhMP3oLOrGOnxvUfnjL+/PN4BmMO7OOHxwUd69OOLQKkxFIpCEJvDRN4F8dvHUNvTaM3ttUvHTNuvIOYF2Ju/cKo+CJ+rUL+HPKsW1Ke/cK8a2KXZzdPvmLcW0Ku3aLFhSTCMfIPvlLmdeI+/POD44IYJ9dp2QJ/jgMPfeMY+KhPHx8fzoLNbV1f3pK////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH5BAEAAFMALAAAAAAQABAAAAfXgFOCgg4JLhg5NoOLU1ITISojJTsQD1KMSBQXBFEDKB44LRUFg1IUTFEbAFRUCkpQNxGXUxMXUT5PVrq6AAM1ElMOIQQbSQFWBwxWMlZFHxwnCSpRAAFLVj0IAQEyVCIvKy4jA1S75lYpAjokGCUo5ee66RoNOTseCvEGVlQLHTA2IOAwcsAcgxkGAESJQWTKgxY/eDShQWMGAiFUWICwIEhKhRsDjgQZAoQKABYETGQYVCBCjQ8iBAhYEAWECVKLpEjg8EKHhg4xLKxkJOjEChINYDhhFAgAOw==' border='0'>");
		body = body.replace(/:8\}/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZcAPXgOoFRK/TbO+7HO+3CO4J5dXtiUPLVO/DNO+/KO/PZO/PYO/TcO/HSO1lQTIJ6deiuOu7IO9ePN/PaO+7JO+7GO755M+myO+amOumzOvHRO+q0OvLWO+itOntlUINZLIFTLOmxOuu4O4JWLOaiOb+ANNaLN+eoOtmaOINcLMGHNI+Hg3xmUIJXLNmWN+jWOLOlMuy/O+y8O4JYLO7FO+enOu3DO+irOuiqOr+BNPLUO/DPO/XhOsGyNOWgOejUONnEOKaZMM25NsGxNLOkMnJoKqeZMWVcKPTeOzEuJHZzdOu5O+jUOZqNL+WfOfLXO4x/Lc26NuisOvTdO1hQTCUiI6aXMOjVOKaZMefTOY+IhPXfOvHx8dbV1fXiOv///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABcACwAAAAAEAAQAAAH4oBcgoIFBgEBBgWDi1wrICY+NSckEiNajFQWGCI2AxE0MjclDoMrFlIEOwcLChwaFRk5l1wgGAQNE1FbAFlABwMQH1wFJiI7E1tVRl5NSQwNMS4PBk42B0g8WENePUFbCxQ4HgE1AwtbXunqAAIIIS0BJxEKAOrr7RczBiQ0HFtXL9S9YKIgQQcWBSTI0MDgRxEYXmAcEaKDAAolXEbcqHCAwRYiUKxMeZJgQwpBWkpkGNBggQABCnQkWKJiFhcHOSDEoIAAQQICG1SQWqTlgwscIS50QJHCJqMHHlrMYPGAUSAAOw==' border='0'>");
		body = body.replace(/:_\|/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZpANDn8YaKjrLV60dpgMji78Xg7np8gKrR6lBSVjg4Oszk70xJSjxlfoiKjp7L6GNpbsni71VbYEVof4rB5G6evsbg77HV67XX65fH5rfY7I3C5afP6ZLE5WaavU1rgc/m8bjY7GSavM7m8Hh7gJjI5onA5Hh8gJbG5klpgJDD5Ye/5JjH5lqWu4eKjnix1nmDiGRqbrDU61hreV1uemiq1G+t1aW1vbq5uXqDicXg71lseT1lfl5uer7c7Y7D5YK9432z10Bmf6LN6LTW65mnr5CvxJi60KjD06/T6n+84py80avS6s7l8LrZ7Mrj74/D5Tg3Opinr8Pf7n+74nyKlIqfrK3T6oierJ/L6J+xu8He7lFTVm93e8Dd7qzF1HZzdMfh75GwxHqDiM/m8MPe7sHd7n2LlKCyu83l8PHx8dbV1SMfIP///wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABpACwAAAAAEAAQAAAH54BpgoIjMgw7OiaDi2kBQTRJJRM/NRIBjAgsKg5FFgJhQj4hCIMBLE9GVxEFORFVSisdLYJBKgdUWQpoaApnZjEcKGkjNA5NYGgfAMtMED0bLgYyU0taCh82MDA2AGgFAhozDCUWBWhiCzc3C1wiBBknAzsTAjlRa/j5RAQgGAM6P6yQSZAvX4IKQ1LwMFFDyBEoD17gwPHiQQIvB4B8SSPBB5IyENCIGIPGiZQLJDwIChBiRYweBQgQqNDlAhYKDQYh6MBhg4AMIIYcIEFhC6MWKFxoOIEhBRAPORkJMjBjwAAeBhgFAgA7' border='0'>");
		body = body.replace(/\?:\|/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZoAIJ9dvTcO3xzUvjiOevQPu3TPYNyMPzoN/rkOHtxUurOPvLaO1lUTPbfOvfgOe/WPeLPNIF9doBvMejMP+fLPyMfIPTdO+3SPfXdO8CoOti9PevRPvXeO4FwMO3ZNmdeJ8KrOezRPoR0MPPcO9e7PoJyMNzBPdTDM4FvMYJxMPvmOPDXPPznN9rAPcCnOtnDON3aOL6kOvvlOKy7RMGvNMWuOGeDcJavSe/WPNzCPefROT44I3ZzdHaPZtnUOvHZPMvFQXCKa8u3NtTCM73KOsa1MfPbO93KNp6xTXyTYnVqKenNPv3oN1hPJ4edXJCELLamMWiEb4aiU77LOu7UPYB0K4mnR/njOVhTTJCDLNrVOniGeM/GPvfhOY+kV5aqUp2QLca2MVlRJvnkOJOlVH+XYbXCP73JOvHx8dbV1Y+LhP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABoACwAAAAAEAAQAAAH4oBogoIRCRIoCQCDi2hqHSQUCgoTGilqjFgxOQ8LATpGKwQuDINqMTUvDQNQCFcOIwUZl2gdS0JNHixgRSwqAwEhBmgRJA80FWFMFU9rX1E9QC0ACRQLRzsQax8nU0FEUkkEAhIKAWMHa+npMAdmNhclKAoWCOjqazBIW2QFBgkTL6p8yKbOi5MZGDYIAKBhhQMPYk6oK3NmwAITPNCkIDBigIosSoYcuKGFAxURgtS4KBCgCwIZMhD44IADxCw0DDKE+GGhQQMMVriAILVIjYEWBC4U2GBCxE1GAASUMLCQUSAAOw==' border='0'>");
		body = body.replace(/:O/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZoAIJ9dvTcO3xzUvjiOevQPu3TPYNyMPzoN/rkOHtxUurOPvLaO1lUTPbfOvfgOe/WPeLPNIF9doBvMejMP+fLPyMfIPTdO+3SPfXdO8CoOti9PevRPvXeO4FwMO3ZNmdeJ8KrOezRPoR0MPPcO9e7PoJyMNzBPdTDM4FvMYJxMPvmOPDXPPznN9rAPcCnOtnDON3aOL6kOvvlOKy7RMGvNMWuOGeDcJavSe/WPNzCPefROT44I3ZzdHaPZtnUOvHZPMvFQXCKa8u3NtTCM73KOsa1MfPbO93KNp6xTXyTYnVqKenNPv3oN1hPJ4edXJCELLamMWiEb4aiU77LOu7UPYB0K4mnR/njOVhTTJCDLNrVOniGeM/GPvfhOY+kV5aqUp2QLca2MVlRJvnkOJOlVH+XYbXCP73JOvHx8dbV1Y+LhP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABoACwAAAAAEAAQAAAH4oBogoIRCRIoCQCDi2hqHSQUCgoTGilqjFgxOQ8LATpGKwQuDINqMTUvDQNQCFcOIwUZl2gdS0JNHixgRSwqAwEhBmgRJA80FWFMFU9rX1E9QC0ACRQLRzsQax8nU0FEUkkEAhIKAWMHa+npMAdmNhclKAoWCOjqazBIW2QFBgkTL6p8yKbOi5MZGDYIAKBhhQMPYk6oK3NmwAITPNCkIDBigIosSoYcuKGFAxURgtS4KBCgCwIZMhD44IADxCw0DDKE+GGhQQMMVriAILVIjYEWBC4U2GBCxE1GAASUMLCQUSAAOw==' border='0'>");
		body = body.replace(/:0/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZaAIJ9dvznN+rOPoNyMHxzUnVrKXtxUvjiOfrkOPTcO+3TPVlUTOvQPvLaO/TdO/vmOO3SPfnkOO7UPefLP+nNPti9Pde7PllRJufROYF9drOiMujMP8KrOYBvMfrlOO/cNvnjOb6kOuvRPmdeJ9rAPZ6QLcCnOoJyMLipMNPBM4R0MNzBPf3oNzArIvXeO+zRPoFwMIJxMFpSJvbfOsCoOvHZPPvlOIFvMd7EPFhTTODHO3FlKvznOPPcO/XdO/PbO/DXPKeXMN7FPHZzdKiYMK6bNLWkMaOTMfnjOKWUMcKxM3FmKq+cNMa2Md3INr6rNLWlMffgOe/WPd3JNr6rNcOyMse2Me/WPCMfIPzoN/Hx8dbV1Y+LhP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABaACwAAAAAEAAQAAAH4oBagoIZBh03BgCDi1pcMBYTAgIbFTFcjDkhFEVUCQ5PTDgmC4NcIUI7RgcRCCBQSzo0l1owFEdKHgFZWQEPVUkvA1oZFlJBHiglXV0lKA9ENSQABhMNBwEXMssyFwEIDgwEHQIJEVkFI8sjBVk2MxAnNwIOCFldH8v4LO4KAwYbP0AEWEawSxYEPkQQAFABSJQHWZoUKGCFBY8DDVYM0RKDQY8DKbCIxJICiQsJKgRxMaEggZMWIltMcXGFwywtC2i8qIFBgwYMDSRwILWIywASDCAoELFCxU1GAAicGLCQUSAAOw==' border='0'>");
		body = body.replace(/:\|/gi, "<img src='data:image/gif;base64,R0lGODlhEAAQAOZJAJCELPjiOevQPoJ9durOPntxUvTcO/TdO/rkOHxzUu3TPYNyMFlUTMSzMvznN/HZPO3SPe/WPdzBPevRPsKrOe7UPf3oN4JxMPXdO1lRJoFvMcCoOvPbO/vmOPXeO/nkOIR0MNe7Pr6kOuzRPufLP1pRJsCnOoBvMfDXPPPcO4JyMIF9dvvlONrAPffgOWdeJ/njOYFwMNi9PejMP+nNPvfhOcSyMs+8NVhTTJ6QLfnjOJCDLJ2PLfrlOI+CLPznOHZzdO/WPJ2QLY6CLNC9NKudLvLaO/bfOvzoN/Hx8dbV1Y+LhP3pN////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACH+JzxDT1BZPkNvcHlyaWdodCBKaXZlIFNvZnR3YXJlIDIwMDItMjAwMwAh+QQBAABJACwAAAAAEAAQAAAH2IBJgoIrBScaBQODi0lLMSEkBAQzMhdLjDgiNBFGBgccKAImDINLIgJGRwEfCDAuKQobl0kxNEY1Qy9ISC8+AQYjC0krIRFHPRklTEwlGR01Dy0DBSRGAQ45AMsARQ4IBwIJJwQGH0jL6ExILEcQKhoEBwjn6UwW7AoLBTMcMA716hBgmJBggAwULjrQW2bhRwAjEoAkuSAgRQAeOwBoFKLDQwUQgpaYUGDgho0GKIl4CEJhVhIGG0Y8OHDkCAYjFSiQWrRkQQsBEBRMkADCJaMBCVQsKMgoEAA7' border='0'>");
	}

	return linkify(body);
};

function linkify(inputText)
{
	var replacedText, replacePattern1, replacePattern2, replacePattern3;

	//URLs starting with http://, https://, or ftp://
	replacePattern1 = /(\b(https?|ftp):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/gim;
	replacedText = inputText.replace(replacePattern1, '<a href="$1" target="_blank">$1</a>');

	//URLs starting with "www." (without // before it, or it'd re-link the ones done above).
	replacePattern2 = /(^|[^\/])(www\.[\S]+(\b|$))/gim;
	replacedText = replacedText.replace(replacePattern2, '$1<a href="http://$2" target="_blank">$2</a>');

	//Change email addresses to mailto:: links.
	replacePattern3 = /(([a-zA-Z0-9\-\_\.])+@[a-zA-Z\_]+?(\.[a-zA-Z]{2,6})+)/gim;
	replacedText = replacedText.replace(replacePattern3, '<a href="mailto:$1">$1</a>');

	return replacedText;
}

function resetVisualNotification()
{
	unreadMessages = 0;
	setVisualNotification(false); 
}

function setVisualNotification(show) 
{
	var unreadMsgElement = document.getElementById('unreadMessages');

	if (unreadMessages) {
	    unreadMsgElement.innerHTML = "&nbsp;" + unreadMessages.toString() + "&nbsp;";

            showToolbar();            
	    var chatButtonElement = document.getElementById('chatButton').parentNode;
	    var leftIndent = (Util.getTextWidth(chatButtonElement) - Util.getTextWidth(unreadMsgElement))/2 - 2;
	    var topIndent = (Util.getTextHeight(chatButtonElement) - Util.getTextHeight(unreadMsgElement))/2 - 3;

	    unreadMsgElement.setAttribute('style', 'top:' + topIndent + '; left:' + leftIndent +';background-color: red');
	}
	else
	    unreadMsgElement.innerHTML = '';

	var glower = $('#chatButton');

	if (show && !notificationInterval) {
	    notificationInterval = window.setInterval(function() {
		glower.toggleClass('active');
	    }, 800);
	}
	else if (!show && notificationInterval) {
	    window.clearInterval(notificationInterval);
	    notificationInterval = false;
	    glower.removeClass('active');
	}
}

Date.prototype.format = function(format) 
{
	var returnStr = '';
	var replace = Date.replaceChars;
	for (var i = 0; i < format.length; i++) {
		var curChar = format.charAt(i);
		if (replace[curChar]) {
			returnStr += replace[curChar].call(this);
		} else {
			returnStr += curChar;
		}
	}
	return returnStr;
};

Date.replaceChars = {
	shortMonths: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
	longMonths: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
	shortDays: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
	longDays: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
	
	// Day
	d: function() { return (this.getDate() < 10 ? '0' : '') + this.getDate(); },
	D: function() { return Date.replaceChars.shortDays[this.getDay()]; },
	j: function() { return this.getDate(); },
	l: function() { return Date.replaceChars.longDays[this.getDay()]; },
	N: function() { return this.getDay() + 1; },
	S: function() { return (this.getDate() % 10 == 1 && this.getDate() != 11 ? 'st' : (this.getDate() % 10 == 2 && this.getDate() != 12 ? 'nd' : (this.getDate() % 10 == 3 && this.getDate() != 13 ? 'rd' : 'th'))); },
	w: function() { return this.getDay(); },
	z: function() { return "Not Yet Supported"; },
	// Week
	W: function() { return "Not Yet Supported"; },
	// Month
	F: function() { return Date.replaceChars.longMonths[this.getMonth()]; },
	m: function() { return (this.getMonth() < 9 ? '0' : '') + (this.getMonth() + 1); },
	M: function() { return Date.replaceChars.shortMonths[this.getMonth()]; },
	n: function() { return this.getMonth() + 1; },
	t: function() { return "Not Yet Supported"; },
	// Year
	L: function() { return "Not Yet Supported"; },
	o: function() { return "Not Supported"; },
	Y: function() { return this.getFullYear(); },
	y: function() { return ('' + this.getFullYear()).substr(2); },
	// Time
	a: function() { return this.getHours() < 12 ? 'am' : 'pm'; },
	A: function() { return this.getHours() < 12 ? 'AM' : 'PM'; },
	B: function() { return "Not Yet Supported"; },
	g: function() { return this.getHours() % 12 || 12; },
	G: function() { return this.getHours(); },
	h: function() { return ((this.getHours() % 12 || 12) < 10 ? '0' : '') + (this.getHours() % 12 || 12); },
	H: function() { return (this.getHours() < 10 ? '0' : '') + this.getHours(); },
	i: function() { return (this.getMinutes() < 10 ? '0' : '') + this.getMinutes(); },
	s: function() { return (this.getSeconds() < 10 ? '0' : '') + this.getSeconds(); },
	// Timezone
	e: function() { return "Not Yet Supported"; },
	I: function() { return "Not Supported"; },
	O: function() { return (-this.getTimezoneOffset() < 0 ? '-' : '+') + (Math.abs(this.getTimezoneOffset() / 60) < 10 ? '0' : '') + (Math.abs(this.getTimezoneOffset() / 60)) + '00'; },
	T: function() { var m = this.getMonth(); this.setMonth(0); var result = this.toTimeString().replace(/^.+ \(?([^\)]+)\)?$/, '$1'); this.setMonth(m); return result;},
	Z: function() { return -this.getTimezoneOffset() * 60; },
	// Full Date/Time
	c: function() { return "Not Yet Supported"; },
	r: function() { return this.toString(); },
	U: function() { return this.getTime() / 1000; }
};