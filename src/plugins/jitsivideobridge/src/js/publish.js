var connection = null;
var roomjid;
var nickname = null;
var roomUrl = null;
var sharedKey = '';
var screenShare = false;

$(document).ready(function () 
{
    window.RTC = setupRTC();

    if (window.RTC === null) {
	return;

    } else if (window.RTC.browser != 'chrome') {
	return;
    }
   
    

    RTCPeerconnection = RTC.peerconnection;

    if (config.useWebsockets)
    	connection = new Openfire.Connection(config.bosh);    
    else
    	connection = new Strophe.Connection(config.bosh);
    	
    connection.resource = Math.random().toString(36).substr(2, 20);
    /*
    connection.rawInput = function (data) { console.log('RECV: ' + data); };
    connection.rawOutput = function (data) { console.log('SEND: ' + data); };
    */
    var jid = config.hosts.domain;

    connection.connect(jid, null, function (status) 
    {
	if (status == Strophe.Status.CONNECTED) 
	{
	    console.log('connected');
	    connection.send($pres()); 
	    doJoin(); 

	    getConstraints(['screen']);
	    getUserMedia();
	    
	} else {
	    console.log('status', status);
	}
    });
});

$(window).bind('beforeunload', function () 
{
    if (connection && connection.connected) {
    	unregisterRayoEvents();
	connection.disconnect();
    }
});

$(document).bind('mediaready.rayo', function(event, stream) 
{
	console.log("mediaready.rayo");   

	window.RTC.rayo.localStream = stream;	
	registerRayoEvents();	
});

$(document).bind('mediafailure.rayo', function(error) {
	console.error('mediafailure.rayo ' + error);
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
		"maxWidth": "1280",
		"maxHeight": "1280",
		"maxFrameRate": "30"		
	    }
	};
    }

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

function urlParam(name)
{
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	if (!results) { return undefined; }
	return results[1] || undefined;
}

function doJoin() {
    var roomnode = urlParam("r");

    console.log("doJoin", roomnode);
	
    if (!roomnode) {
    	roomnode = Math.random().toString(36).substr(2, 20);
    	window.history.pushState('VideoChat', 'Room: ' + roomnode, window.location.pathname + "?r=" + roomnode);
    }

    roomjid = roomnode + '@' + config.hosts.muc;
    var myroomjid = roomjid;
    myroomjid += '/' + Strophe.getNodeFromJid(connection.jid) + "(Desktop)";
            
    connection.addHandler(rayoCallback, 'urn:xmpp:rayo:colibri:1');
    connection.emuc.doJoin(myroomjid);    
}
 
function rayoCallback(presence) 
{
	console.log("rayoCallback", presence);
	
	var from = $(presence).attr('from');		

	$(presence).find('offer').each(function() 
	{
		handleOffer(from, this);
	});	

	return true;
};
	
function handleOffer (from, offer) 
{
	console.log("handleOffer", offer);

	var bridgeSDP = new SDP('v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 1 RTP/SAVPF 111 0 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=sendrecv\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\nm=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=sendrecv\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n');		
	var muc = $(offer).attr('muc');
	var nick = $(offer).attr('nickname');
	var participant = $(offer).attr('participant');
	var videobridge = $(offer).attr('videobridge');
	var confid = null;
	var channelId = [];

	window.RTC.rayo.channels = {}
	window.RTC.rayo.addssrc = false;

	$(offer).find('conference').each(function() 
	{		
		confid = $(this).attr('id');
		window.RTC.rayo.channels.id = confid;

		$(this).find('content').each(function() 
		{		
			var name = $(this).attr('name');
			var channel = name == "audio" ? 0 : 1;				
			
			if ((window.RTC.rayo.localStream.getVideoTracks().length > 0 && name == "video") || (window.RTC.rayo.localStream.getAudioTracks().length > 0 && name == "audio"))
			{
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
				
			} else {
				bridgeSDP.media[channel] = null;
			}
		});				
	});

	bridgeSDP.raw = bridgeSDP.session + bridgeSDP.media.join('');
	window.RTC.rayo.channels.sdp = bridgeSDP.raw;

	//console.log("bridgeSDP.raw", bridgeSDP.raw);	

   	window.RTC.rayo.pc[videobridge] = new window.RTC.peerconnection(null, {'optional': [{'DtlsSrtpKeyAgreement': 'true'}]}); 

   
	window.RTC.rayo.pc[videobridge].onicecandidate = function(event)
	{
		//console.log('candidate', event.candidate);

		if (!event.candidate) 
		{
			sendAnswer(from, videobridge, confid, channelId);
		}

	}

	window.RTC.rayo.pc[videobridge].onaddstream = function(e)
	{
		console.log("onstream", e, window.RTC.rayo.addssrc);

		if (window.RTC.rayo.pc[videobridge].signalingState == "have-remote-offer")
			$(document).trigger('remotestreamadded.rayo', [e, nick]);

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

function unregisterRayoEvents()
{	
	window.RTC.rayo.localStream.stop();
	
	connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'expire', muc: roomjid}),
		function (res) {
		    console.log('rayo colibri unregister set ok');
		},

		function (err) {
		    console.log('rayo colibri unregister got error', err);
		}
	);	
}
