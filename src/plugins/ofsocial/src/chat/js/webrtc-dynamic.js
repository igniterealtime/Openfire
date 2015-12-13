/*===========================================================================*
*                       		                                     *
*                                                                            *
*      Class methods     					 	     *
*                                                                            *
*                                                                            *
*============================================================================*/


WebRtc = function(room) 
{
	this.room = room;
	this.pc = null;	
	this.farParty = null;
	this.inviter = false;
	this.closed = true;
	this.candidates = new Array();	
	this.mediaHints = WebRtc.mediaHints;
	this.localStream = WebRtc.localStream;
	this.localVideoPreview = "localVideoPreview";
	this.remoteVideo = "remoteVideo";
	this.remoteRoomMuteType = "room";
	this.answerCreated = false;
	this.offerCreated = false;
	this.newMediaHints = null;	
}

WebRtc.localStream = null;
WebRtc.mediaHints = {audio:true, video:false};

WebRtc.peers = {}; 
WebRtc.rooms = {}; 

WebRtc.log = function (msg) {console.log(msg)}; 


WebRtc.init =  function(connection, peerConfig)
{
	WebRtc.log("WebRtc.init");

	if (!window.webkitRTCPeerConnection) 
	{
		var msg = "webkitRTCPeerConnection not supported by this browser";			
		alert(msg);
		throw Error(msg);
	}

	WebRtc.peerConfig = peerConfig;
	WebRtc.connection = connection;	
};

WebRtc.close = function()
{
	WebRtc.log("WebRtc.close");
	
	var peers = Object.getOwnPropertyNames(WebRtc.peers)
	
	for (var i=0; i< peers.length; i++)
	{
		var peer = WebRtc.peers[peers[i]];

		if (peer && peer.pc)
		{		
			peer.close();
			WebRtc.peers[peers[i]] = null;
			peer = null;
		}
	}
	
	WebRtc.peers = {}; 
	WebRtc.rooms = {}; 	
};


WebRtc.setPeer = function (uniqueKey, room)
{
	WebRtc.log("WebRtc.setPeer " + uniqueKey + " " + room);
		
	if (WebRtc.peers[uniqueKey] == null)
	{
		WebRtc.peers[uniqueKey] = new WebRtc(room);
	} else {
		
		WebRtc.peers[uniqueKey].room = room;
		WebRtc.peers[uniqueKey].open();			
	}	
};

WebRtc.getPeer = function (jid)
{
	WebRtc.log("WebRtc.getPeer " + jid);
	
	return WebRtc.peers[WebRtc.escape(jid)];

};


WebRtc.handleMessage = function(message, jid, room)
{
	var uniqueKey = WebRtc.escape(jid)

	WebRtc.log("WebRtc.handleMessage " + jid + " " + room + " " + uniqueKey);
	console.log(message)
	
	if (message.getAttribute("type") == "error" || message.getElementsByTagName("action").length == 0)
	{
		return;
	}
		
	var action = message.getElementsByTagName("action")[0].firstChild.data;
	
	
	if (action == "answer")
	{
		var peer = WebRtc.peers[uniqueKey]

		if (peer && peer.pc)
		{
			peer.handleAnswer(message);

		}
		return;
	}
	
	if (action == "candidate")
	{
		var peer = WebRtc.peers[uniqueKey]

		if (peer && peer.pc)
		{
			peer.handleCandidate(message);

		}
		return;	
	}

	if (action == "mute")
	{
		var peer = WebRtc.peers[uniqueKey]

		if (peer && peer.pc)
		{
			peer.handleMute(message);

		}
		return;
	}
	
	if (action == "offer")
	{
		WebRtc.setPeer(uniqueKey, room);
		WebRtc.peers[uniqueKey].handleOffer(message, jid);
		
		return;
	}

	
	var channels = message.getElementsByTagName("channel");

	if (channels.length > 0)
	{
		var peer = WebRtc.peers[uniqueKey]

		if (peer && peer.pc)
		{
			peer.handleChannel(channels[0]);

		}
		return;	
	}
};


WebRtc.handleRoster = function(myJid, jid, room, action, mediaHints)
{
	WebRtc.log("WebRtc.handleRoster " + myJid + " " + jid + " " + room + " " + action);

	var uniqueKey = WebRtc.escape(jid)
	
	if (!mediaHints) mediaHints = WebRtc.mediaHints;	// use global default;	
	
	if (action == "chat")
	{
		WebRtc.log("WebRtc.handleRoster opening chat with " + room);					
		WebRtc.rooms[room] = {ready: true, active: false, muted: false};		
		WebRtc.setPeer(uniqueKey, room);
		WebRtc.peers[uniqueKey].muc = false;
		WebRtc.peers[uniqueKey].inviter = true;	
		WebRtc.peers[uniqueKey].newMediaHints = mediaHints;
		WebRtc.peers[uniqueKey].farParty = jid;
		WebRtc.peers[uniqueKey].initiate();
	}
	
	if (action == "join")
	{
		if (myJid == jid)
		{
			WebRtc.log("WebRtc.handleRoster opening room " + room);					
			WebRtc.rooms[room] = {ready: true, active: false, muted: false};
		}

		if (WebRtc.rooms[room] == null && myJid != jid)
		{
			WebRtc.setPeer(uniqueKey, room);
			WebRtc.peers[uniqueKey].muc = true;
			WebRtc.peers[uniqueKey].inviter = true;
			WebRtc.peers[uniqueKey].newMediaHints = mediaHints;			
			WebRtc.peers[uniqueKey].farParty = jid;
		}			
	}	

	if (action == "leave")
	{
		if (myJid == jid)	// I have left, close all peerconnections
		{
			WebRtc.log("WebRtc.handleRoster closing room " + room);					
			WebRtc.rooms[room] = null;
			
			var peers = Object.getOwnPropertyNames(WebRtc.peers)

			for (var i=0; i< peers.length; i++)
			{
				var peer = WebRtc.peers[peers[i]];
				
				if (peer.room == room)
				{
					peer.close();	
				}
			}
			
		} else {				// someone has left, close their peerconnection
			var peer = WebRtc.peers[uniqueKey]

			if (peer != null)
			{		
				peer.close();		
			}		
		}
	}

};

WebRtc.muteRoom = function(mute, room)
{
	WebRtc.log("WebRtc.muteRoom " + room + " " + mute);
	
	var peers = Object.getOwnPropertyNames(WebRtc.peers)
	
	for (var i=0; i< peers.length; i++)
	{
		WebRtc.log("Found participant " + peers[i]);		
	
		var peer = WebRtc.peers[peers[i]];

		if (peer)
		{		
			if (peer.room == room)
			{
				if (peer.pc)		
				{			
					WebRtc.log("muting to participant " + peer.farParty + " in room " + room + " " + mute);
					peer.pc.localStreams[0].audioTracks[0].enabled = !mute;		
					peer.sendMuteSignal(mute, true, "room", false);						
					
				} else {
					WebRtc.log("initiating media with participant " + peer.farParty + " in room " + room + " " + mute);
					
					peer.muc = true;
					peer.remoteRoomMuteType = "room";
					peer.initiate();					
				}			
			}
		}
	}
	
	if (WebRtc.rooms[room]) WebRtc.rooms[room].active = !mute;
};

WebRtc.muteRemoteRoom = function(mute, room)
{
	var peers = Object.getOwnPropertyNames(WebRtc.peers)
	
	for (var i=0; i< peers.length; i++)
	{
		var peer = WebRtc.peers[peers[i]];

		if (peer && !peer.closed && peer.pc && peer.pc.remoteStreams.length > 0)
		{							
			if (peer.room == room)
			{
				WebRtc.log("muting from participant " + peer.farParty + " in room " + room + " " + mute);

				peer.pc.remoteStreams[0].audioTracks[0].enabled = !mute;					
			}
		}
	}
	
	if (WebRtc.rooms[room]) WebRtc.rooms[room].muted = mute;
};

WebRtc.isRoomMuted = function(room)
{
	WebRtc.log("WebRtc.isRoomMuted " + room);
	
	var mute = true;
	if (WebRtc.rooms[room]) mute = !WebRtc.rooms[room].active;
	
	return mute;
};

WebRtc.isRemoteRoomMuted = function(room)
{
	WebRtc.log("WebRtc.isRemoteRoomMuted " + room);
	console.log(WebRtc.rooms[room]);
	
	var mute = true;
	if (WebRtc.rooms[room]) mute = WebRtc.rooms[room].muted;
	
	return mute;
};


WebRtc.toggleRoomMute = function(room)
{
	WebRtc.log("WebRtc.toggleRoomMute " + room);

	WebRtc.muteRoom(!WebRtc.isRoomMuted(room), room);
};




WebRtc.muteUser = function(mute, jid, video)
{	
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer.pc)
	{		
		WebRtc.log("muting local user " + peer.farParty + " " + mute);

		peer.pc.localStreams[0].audioTracks[0].enabled = !mute;				
				
		if (peer.pc.localStreams[0].videoTracks.length > 0 && video) 
		{
			peer.pc.localStreams[0].videoTracks[0].enabled = !mute;	
			
			peer.sendMuteSignal(mute, mute, "private", video);			
		} else {

			peer.sendMuteSignal(mute, true, "private", false);		
		}		
	}
};

WebRtc.toggleUserMute = function(jid, video)
{
	WebRtc.muteUser(!WebRtc.isUserMuted(jid), jid, video);
};


WebRtc.isUserMuted = function(jid)
{
	var mute = true;
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer != null && peer.pc && peer.pc.localStreams.length > 0)
	{		
		mute = !peer.pc.localStreams[0].audioTracks[0].enabled;		
	}	
	return mute;
};

WebRtc.isRemoteUserMuted = function(jid)
{
	var mute = true;
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer != null && peer.pc && peer.pc.remoteStreams.length > 0)
	{		
		mute = !peer.pc.remoteStreams[0].audioTracks[0].enabled;		
	}	
	return mute;
};

WebRtc.hasRemoteUserVideo = function(jid)
{
	var hasVideo = false;
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer != null && peer.pc && peer.pc.remoteStreams.length > 0)
	{		
		hasVideo = true;		
	}	
	return hasVideo;
};

WebRtc.isRemoteUserVideoMuted = function(jid)
{
	var mute = true;
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer != null && peer.pc && peer.pc.remoteStreams.length > 0)
	{		
		mute = !peer.pc.remoteStreams[0].videoTracks[0].enabled;		
	}	
	return mute;
};


WebRtc.muteRemoteUser = function(mute, jid)
{
	var uniqueKey = WebRtc.escape(jid)

	var peer = WebRtc.peers[uniqueKey]

	if (peer != null && peer.pc && peer.pc.remoteStreams.length > 0)
	{		
		WebRtc.log("muting remote user " + peer.farParty + " " + mute);

		peer.pc.remoteStreams[0].audioTracks[0].enabled = !mute;
		
		if (peer.pc.remoteStreams[0].videoTracks.length > 0) 
		{
			peer.pc.remoteStreams[0].videoTracks[0].enabled = !mute;									
		}
	}
};

WebRtc.textToXML = function(text)
{
	var doc = null;

	if (window['DOMParser']) {
	    var parser = new DOMParser();
	    doc = parser.parseFromString(text, 'text/xml');

	} else if (window['ActiveXObject']) {
	    var doc = new ActiveXObject("MSXML2.DOMDocument");
	    doc.async = false;
	    doc.loadXML(text);

	} else {
	    throw Error('No DOMParser object found.');
	}

	return doc.firstChild;
};

WebRtc.escape = function(s)
{
        return s.replace(/^\s+|\s+$/g, '')
            .replace(/\\/g,  "")
            .replace(/ /g,   "")
            .replace(/\"/g,  "")
            .replace(/\&/g,  "")
            .replace(/\'/g,  "")
            .replace(/\//g,  "")
            .replace(/:/g,   "")
            .replace(/</g,   "")
            .replace(/>/g,   "")
            .replace(/\./g,  "")            
            .replace(/@/g,   "");

};

/*===========================================================================*
*                       		                                     *
*                                                                            *
*                Object Methods						     *
*                                                                            *
*                                                                            *
*============================================================================*/



WebRtc.prototype.initiate = function()
{
	WebRtc.log("initiate " + this.farParty);

	var mediaHints = this.newMediaHints	
	var _webrtc = this;
	
	this.answerCreated = false;
	this.offerCreated = false;
				
	this.createPeerConnection(function() {

		WebRtc.log("initiate createPeerConnection callback");
	
		if (this.pc != null)
		{
			this.pc.createOffer( function(desc) 
			{
				_webrtc.pc.setLocalDescription(desc);
				_webrtc.sendSDP(desc.sdp, mediaHints); 	
				_webrtc.offerCreated = true;

			}, null, {'mandatory': {offerToRecieveAudio: mediaHints.audio, offerToRecieveVideo: mediaHints.video}});		
		}	
	}, mediaHints);
}

WebRtc.prototype.open = function ()
{
	WebRtc.log("open");

	this.closed = false;		

}

WebRtc.prototype.close = function ()
{
	WebRtc.log("close");

	if (this.pc)
	{
		this.pc.close();
		this.pc = null;
	}

	this.closed = true;	
	this.answerCreated = false;
	this.offerCreated = false;
			
	
}
WebRtc.prototype.handleAnswer = function(elem)
{
	WebRtc.log("handleAnswer");

	var sdp = elem.getElementsByTagName("sdp")[0].firstChild.data;	
	var audio = elem.getElementsByTagName("audio")[0].firstChild.data;
	var video = elem.getElementsByTagName("video")[0].firstChild.data;
	
	var mediaHints = {audio: audio == "true", video: video == "true"};

	this.inviter= true;
	this.pc.setRemoteDescription(new RTCSessionDescription({type: "answer", sdp : sdp}));
	
	this.addJingleNodesCandidates();	

}

WebRtc.prototype.handleOffer = function(elem, jid)
{
	WebRtc.log("handleOffer");

	var sdp = elem.getElementsByTagName("sdp")[0].firstChild.data;	
	var audio = elem.getElementsByTagName("audio")[0].firstChild.data;
	var video = elem.getElementsByTagName("video")[0].firstChild.data;
	
	this.muc = elem.getElementsByTagName("muc")[0].firstChild.data == "true";
	
	var mediaHints = {audio: audio == "true", video: video == "true"};

	this.answerCreated = false;
	this.offerCreated = false;
	
	var _webrtc = this;
	
	this.createPeerConnection(function() {

		WebRtc.log("handleOffer createPeerConnection callback");
		
		_webrtc.inviter= false;	
		_webrtc.farParty = jid;
		_webrtc.pc.setRemoteDescription(new RTCSessionDescription({type: "offer", sdp : sdp}));	
		
		if (_webrtc.inviter == false)
		{
		    _webrtc.pc.createAnswer( function (desc)
		    {
			_webrtc.pc.setLocalDescription(desc);			
			_webrtc.sendSDP(desc.sdp, {audio: mediaHints.audio, video: mediaHints.video}); 
			_webrtc.answerCreated = true;

		    }, null, {'mandatory': {offerToRecieveAudio: mediaHints.audio, offerToRecieveVideo: mediaHints.video}});			
		}		
		
	}, mediaHints);

}


WebRtc.prototype.handleMute = function(elem)
{
	WebRtc.log("handleMute");

	var audio = elem.getElementsByTagName("audio")[0].firstChild.data;
	var video = elem.getElementsByTagName("video")[0].firstChild.data;
	var videoReq = elem.getElementsByTagName("videoreq")[0].firstChild.data;	
	var muc = elem.getElementsByTagName("muc")[0].firstChild.data;
		
	this.remoteRoomMuteType = elem.getElementsByTagName("type")[0].firstChild.data
	
	if (WebRtc.callback)
	{
		WebRtc.callback.onMute(this, this.remoteRoomMuteType, muc == "true", audio == "true", video == "true", videoReq == "true");			
	}
}

WebRtc.prototype.handleCandidate = function(elem)
{
	WebRtc.log("handleCandidate");
	
	var label = elem.getElementsByTagName("label")[0].firstChild.data;
	var candidate = elem.getElementsByTagName("candidate")[0].firstChild.data;
	
	var ice = {sdpMLineIndex: label, candidate: candidate};
	var iceCandidate = new RTCIceCandidate(ice);
	
	if ((this.inviter && this.offerCreated == false) || (this.inviter == false && this.answerCreated == false))	
	{
		this.candidates.push(iceCandidate);
	} else {
		this.pc.addIceCandidate(iceCandidate);
	}	
}

WebRtc.prototype.handleChannel = function(channel)
{
	WebRtc.log("handleChannel");

	var relayHost = channel.getAttribute("host");
	var relayLocalPort = channel.getAttribute("localport");
	var relayRemotePort = channel.getAttribute("remoteport");

	WebRtc.log("add JingleNodes candidate: " + relayHost + " " + relayLocalPort + " " + relayRemotePort); 

	this.sendTransportInfo("0", "a=candidate:3707591233 1 udp 2113937151 " + relayHost + " " + relayRemotePort + " typ host generation 0");				

	var candidate = new RTCIceCandidate({sdpMLineIndex: "0", candidate: "a=candidate:3707591233 1 udp 2113937151 " + relayHost + " " + relayLocalPort + " typ host generation 0"});				
	this.pc.addIceCandidate(candidate);				
}
	

WebRtc.prototype.createPeerConnection = function(callback, mediaHints)
{
	WebRtc.log("createPeerConnection");

	if (!mediaHints) mediaHints = WebRtc.mediaHints;	// use global default;

	this.candidates = new Array();
	this.createCallback = callback;
	this.pc = new window.webkitRTCPeerConnection(WebRtc.peerConfig);

	this.pc.onicecandidate = this.onIceCandidate.bind(this);		
	this.pc.onstatechange = this.onStateChanged.bind(this);
	this.pc.onopen = this.onSessionOpened.bind(this);
	this.pc.onaddstream = this.onRemoteStreamAdded.bind(this);
	this.pc.onremovestream = this.onRemoteStreamRemoved.bind(this);
	
	if (this.mediaHints.audio != mediaHints.audio || this.mediaHints.video != mediaHints.video)
	{
		this.localStream = null;
		this.mediaHints = mediaHints;
	}
	
	if (this.localStream == null)
		navigator.webkitGetUserMedia({audio:this.mediaHints.audio, video:this.mediaHints.video}, this.onUserMediaSuccess.bind(this), this.onUserMediaError.bind(this));
	else {
		this.onUserMediaSuccess(this.localStream);
	}

	this.closed = false;	
}

WebRtc.prototype.onUserMediaSuccess = function(stream)
{
	WebRtc.log("onUserMediaSuccess");
	this.pc.addStream(stream);
	this.localStream = stream;
	
	if (WebRtc.localStream == null) WebRtc.localStream = stream;
	
	this.createCallback();	
	
	if (document.getElementById(this.localVideoPreview) && stream.videoTracks.length > 0)
	{
		document.getElementById(this.localVideoPreview).src = webkitURL.createObjectURL(stream);
		document.getElementById(this.localVideoPreview).play();	
	}
}

WebRtc.prototype.onUserMediaError = function (error)
{
	WebRtc.log("onUserMediaError " + error.code);
}

WebRtc.prototype.onIceCandidate = function (event)
{
	WebRtc.log("onIceCandidate");

	while (this.candidates.length > 0)
	{
		var candidate = this.candidates.pop();

		console.log("Retrieving candidate " + candidate.candidate);		    

		this.pc.addIceCandidate(candidate);
	}
	
	if (event.candidate && this.closed == false)
	{		
		this.sendTransportInfo(event.candidate.sdpMLineIndex, event.candidate.candidate);
	}	
		
}


WebRtc.prototype.onSessionOpened = function (event)
{
	WebRtc.log("onSessionOpened");
	WebRtc.log(event);
}

WebRtc.prototype.onRemoteStreamAdded = function (event)
{
	var url = webkitURL.createObjectURL(event.stream);
	WebRtc.log("onRemoteStreamAdded " + url);
	WebRtc.log(event);
	
	if (WebRtc.callback)
	{
		WebRtc.callback.onReady(this);
		
		var video = this.pc.remoteStreams[0].videoTracks.length == 0;
		
		if (!this.inviter && this.muc)
			WebRtc.callback.onMute(this, this.remoteRoomMuteType, this.muc, false, video, video);			
	}
			
	if (!this.inviter)
	{		
		this.pc.localStreams[0].audioTracks[0].enabled = false;
	}

	if (document.getElementById(this.remoteVideo))
	{	
		document.getElementById(this.remoteVideo).src = url;
		document.getElementById(this.remoteVideo).play();
		
	} else {
	
		var uniqueKey = "webrtc_" + WebRtc.escape(this.farParty);

		if (!document.getElementById(uniqueKey))
		{
			var ifrm = document.createElement("video"); 
			ifrm.setAttribute("id", uniqueKey); 			
			ifrm.setAttribute("autoplay", "autoplay"); 	   
			ifrm.style.display = "none"; 
			document.body.appendChild(ifrm); 	
		}

		document.getElementById(uniqueKey).src = url; 		
	}
}

WebRtc.prototype.onRemoteStreamRemoved = function (event)
{
	//var url = webkitURL.createObjectURL(event.stream);
	WebRtc.log("onRemoteStreamRemoved " + url);
	WebRtc.log(event);
}

WebRtc.prototype.onStateChanged = function (event)
{
	WebRtc.log("onStateChanged");
	WebRtc.log(event);
}



WebRtc.prototype.sendSDP = function(sdp, mediaHints)
{
	WebRtc.log("sendSDP " + mediaHints.audio + " " + mediaHints.video);
	WebRtc.log(sdp);	
	
	var msg = "";
	msg += "<message  type='chat' to='" + this.farParty + "'>";	
	msg += "<webrtc xmlns='http://webrtc.org/xmpp'>";
	
	if (this.inviter)
	{
		msg += "<action>offer</action>";	
	} else {
		msg += "<action>answer</action>";	
	}

	msg += "<sdp>" + sdp+ "</sdp>";
	msg += "<audio>" + mediaHints.audio + "</audio>";	
	msg += "<video>" + mediaHints.video + "</video>";	
	msg += "<muc>" + this.muc + "</muc>";	
	
	msg += "</webrtc>";	
	msg += "</message>";	
	
	this.sendPacket(msg);
}

	
WebRtc.prototype.sendMuteSignal = function (audio, video, type, videoReq)
{
	WebRtc.log("sendMuteSignal " + audio + " " + video + " " + type);
		
	var msg = "";
	msg += "<message type='chat' to='" + this.farParty + "'>";	
	msg += "<webrtc xmlns='http://webrtc.org/xmpp'>";
	msg += "<action>mute</action>";	
	msg += "<audio>" + audio + "</audio>";	
	msg += "<video>" + video + "</video>";	
	msg += "<videoreq>" + videoReq + "</videoreq>";	
	msg += "<muc>" + this.muc + "</muc>";	
	msg += "<type>" + type + "</type>";	
	msg += "</webrtc>";
	msg += "</message>";	
	
	this.sendPacket(msg);
}

WebRtc.prototype.sendTransportInfo = function (sdpMLineIndex, candidate)
{
	WebRtc.log("sendTransportInfo");
	
	var msg = "";
	msg += "<message type='chat' to='" + this.farParty + "'>";	
	msg += "<webrtc xmlns='http://webrtc.org/xmpp'>";
	msg += "<action>candidate</action>";		
	msg += "<label>" + sdpMLineIndex + "</label>";
	msg += "<candidate>" + candidate + "</candidate>";	
	msg += "<muc>" + this.muc + "</muc>";	
	msg += "</webrtc>";
	msg += "</message>";	
	
	this.sendPacket(msg);	
}


WebRtc.prototype.addJingleNodesCandidates = function() 
{
	WebRtc.log("addJingleNodesCandidates");
	
	var iq = "";
	var id = this.farParty;
		
	iq += "<iq type='get' to='" +  "relay." + window.location.hostname + "' id='" + id + "'>";
	iq += "<channel xmlns='http://jabber.org/protocol/jinglenodes#channel' protocol='udp' />";
	iq += "</iq>";	

	this.sendPacket(iq);	
}


WebRtc.prototype.sendPacket = function(packet) {

	try {	
		if (WebRtc.connection instanceof Strophe.Connection || WebRtc.connection instanceof Openfire.Connection) 
		{	
			var xml = WebRtc.textToXML(packet);

			WebRtc.log("sendPacket with Strophe.Connection");
			WebRtc.log(xml);		

			WebRtc.connection.send(xml);		

		} else {

			WebRtc.log("sendPacket as String");
			WebRtc.log(packet);

			WebRtc.connection.sendXML(packet);
		}
	
	} catch (e) {

		WebRtc.log("sendPacket as String");
		WebRtc.log(packet);

		WebRtc.connection.sendXML(packet);	
	}
};

WebRtc.prototype.muteUser = function(mute, video)
{
	this.pc.localStreams[0].audioTracks[0].enabled = !mute;				

	if (this.pc.localStreams[0].videoTracks.length > 0 && video) 
	{
		this.pc.localStreams[0].videoTracks[0].enabled = !mute;	

		this.sendMuteSignal(mute, mute, "private", video);			
	} else {

		this.sendMuteSignal(mute, true, "private", false);		
	}
};

WebRtc.prototype.muteRemoteUser = function(mute)
{
	this.pc.remoteStreams[0].audioTracks[0].enabled = !mute;

	if (this.pc.remoteStreams[0].videoTracks.length > 0) 
	{
		this.pc.remoteStreams[0].videoTracks[0].enabled = !mute;									
	}
};

