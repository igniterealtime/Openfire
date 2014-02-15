/**
 * Jitsi video bridge plugin for Candy
 *
 */

var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.Videobridge = (function(self, Candy, $) {

	var connection = null;
	var nickname = null;
	var roomJid = null;
	var previousRoomJid = null;	
	var room = null;
	var previousRoom = null;
	var videoOn = false;
	var screenShare = false;	

	self.init = function() 
	{
		console.log("Videobridge.init");

		window.RTC = setupRTC();

		if (window.RTC == null) {
		     alert('Sorry, your browser is not WebRTC enabled!');
		    return;

		} else if (window.RTC.browser != 'chrome') {
		    alert('Sorry, only Chrome supported for now!');
		    return;
		}
						
		getConstraints(['audio','video'], '360');		
		getUsermedia();	
			
		Candy.View.Event.Room.onAdd = handleOnAdd;		
		Candy.View.Event.Room.onShow = handleOnShow;
		Candy.View.Event.Room.onHide = handleOnHide;
		Candy.View.Event.Room.onClose = handleOnClose;	
		Candy.View.Event.Room.onPresenceChange = handleOnPresenceChange;		
		Candy.View.Event.Roster.onUpdate = handleRoster;
		
		Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.CHAT, chatObserver);		

		resizeLarge();

		$(window).resize(function() {
		    resizeLarge();
		});		
	}


	var videoControl = function() {
		
		console.log("videoControl ");
		
		var html = "";
		html += '<li id="videobridge-control" data-tooltip="add or remove video"></li>';
		html += '<li id="webcam-control" data-tooltip="mute/toggle video mute"></li>';
		html += '<li id="mic-control" data-tooltip="toggle audio mute"></li>';	
		html += '<li id="screen-control" data-tooltip="toggle desktop screen share"></li>';			
		
		$('#chat-toolbar').prepend(html);
		
		$('#videobridge-control').click(function() {
			$(this).toggleClass('active');
			
			if (videoOn)	
				sendExpireCommand(roomJid);
			else
				sendOfferCommand(roomJid);		    
		});
		
		$('#webcam-control').click(function() {
			
			$(this).toggleClass('muted');
			
			if (window.RTC.rayo.localStream)
			{
				for (var idx = 0; idx < window.RTC.rayo.localStream.getVideoTracks().length; idx++) {
				    window.RTC.rayo.localStream.getVideoTracks()[idx].enabled = !window.RTC.rayo.localStream.getVideoTracks()[idx].enabled;
				}
			}
		});
		
		$('#mic-control').click(function() {
			$(this).toggleClass('muted');	
			
			if (window.RTC.rayo.localStream)
			{
				for (var idx = 0; idx < window.RTC.rayo.localStream.getAudioTracks().length; idx++) {
				    window.RTC.rayo.localStream.getAudioTracks()[idx].enabled = !window.RTC.rayo.localStream.getAudioTracks()[idx].enabled;
				}

			}			
		});
		
		$('#screen-control').click(function() {			
			
			var videobridge = Strophe.getNodeFromJid(roomJid);

			if (screenShare)
			{	
				var screenDIV = document.getElementById("screenshare");
				screenDIV.parentElement.removeChild(screenDIV);
				$(this).removeClass('active');

			} else {
				//var url = "../../publish.html?r=" + videobridge + "&screen=true";
				var url = "/jitsi/apps/ofmeet/publish.html?r=" + videobridge + "&screen=true";

				$("body").append("<div id='screenshare'><iframe  style='display:none' src='" + url + "'></iframe></div>");
				$("#screen").addClass("fa-border");
				$(this).addClass('active');	
			}
			
			screenShare = !screenShare;
		});				

	};
	
	var handleOnPresenceChange = function(arg) {
		
		console.log("handleOnPresenceChange " + arg.roomJid);

	};
	
	var handleOnClose = function(arg) {
		
		console.log("handleOnClose " + arg.roomJid);
		sendExpireCommand(arg.roomJid);
	};
	
	var handleOnAdd = function(arg) {		
		console.log("handleOnAdd ", arg);
	};

	var handleOnHide = function(arg) {
	
		console.log("handleOnHide", previousRoom, room);
	};	

	var handleOnShow = function(arg) 
	{
		console.log("handleOnShow ", arg);
		
		previousRoom = room;	
		previousRoomJid = roomJid;
		
		roomJid = arg.roomJid;
		room = Strophe.getNodeFromJid(arg.roomJid);
		
		if (videoOn && previousRoomJid && previousRoomJid != roomJid) 
			sendExpireCommand(previousRoomJid);		
	}	
	
	var handleRoster = function(args) {
	
		console.log("handleRoster " + args.action + " " + args.user.getEscapedJid() + " " + args.roomJid);
	};

	var sendOfferCommand = function(roomJid) 
	{
		console.log("sendOfferCommand ", roomJid);
		
		if (window.RTC.rayo.localStream.getVideoTracks().length > 0)
		{
			$('.message-pane-wrapper').css("bottom", "110px");
			$('#localVideo').css("visibility", "visible");		
		} else {
			$('#remoteVideos').css("visibility", "hidden");		
		}
			    
		var offer = $iq({to: connection.domain, type: 'set'});
		offer.c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'offer', muc: roomJid});	
		
		connection.sendIQ(offer,
			function (res) {
			    console.log('rayo colibri offer sent ok');
			    videoOn = true;
			},

			function (err) {
			    console.log('rayo colibri offer got error ', err);
			    $('#localVideo').css("visibility", "hidden");
			    $('.message-pane-wrapper').css("bottom", "0px");			    
			}
		);		
	}
	

	var sendExpireCommand = function(roomJID)
	{
		console.log("sendExpireCommand ", roomJID);

		$('#localVideo').css("visibility", "hidden");		
				
		connection.sendIQ($iq({to: connection.domain, type: 'set'}).c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', action: 'expire', muc: roomJID}),
			function (res) {
			    console.log('rayo colibri expire set ok');	
			    videoOn = false;
			    $('.message-pane-wrapper').css("bottom", "0px");
			    $('#videobridge-control').removeClass('active');
			    
			    setTimeout(function()
			    {
				if (window.RTC.rayo.pc)
				{
			    		console.log("sendExpireCommand close peer conection");
					window.RTC.rayo.pc.close();
					window.RTC.rayo.pc = null;
				}

			    }, 2000);			    
			},

			function (err) {
			    console.log('rayo colibri expire got error', err);
			    $('.message-pane-wrapper').css("bottom", "0px");
			    $('#videobridge-control').removeClass('active');			    
			}
		);				
	}
	
	
	var chatObserver = 
	{
		update: function(obj, args) 
		{
			if(args.type === 'connection')
			{
				switch(args.status) 
				{
					case Strophe.Status.CONNECTING:				
						connection = Candy.Core.getConnection();															
						break;

					case Strophe.Status.CONNECTED:	
						nickname = Strophe.escapeNode(Candy.Core.getUser().getNick());	
						Candy.Core.addHandler(rayoCallback, 'urn:xmpp:rayo:colibri:1');							
						break;
				}
			}
		}
	};    

	var rayoCallback = function(presence) {
		
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

		console.log("rayoCallback exit", presence);		
		return true;
	};

	var handleOffer = function (from, offer) 
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

		window.RTC.rayo.pc = new window.RTC.peerconnection(null, {'optional': [{'DtlsSrtpKeyAgreement': 'true'}]});

		window.RTC.rayo.pc.onicecandidate = function(event)
		{
			console.log('candidate', event.candidate);

			if (!event.candidate) 
			{
				sendAnswer(from, videobridge, confid, channelId);
			}

		}

		window.RTC.rayo.pc.onaddstream = function(e)
		{
			console.log("onstream", e, window.RTC.rayo.addssrc);

			if (window.RTC.rayo.pc.signalingState == "have-remote-offer")
				$(document).trigger('remotestreamadded.rayo', [e, nick]);

			window.RTC.rayo.pc.createAnswer(function(desc)
			{
				if (!window.RTC.rayo.addssrc)				
					window.RTC.rayo.pc.setLocalDescription(desc);
			});				
		};			

		window.RTC.rayo.pc.addStream(window.RTC.rayo.localStream);
		window.RTC.rayo.pc.setRemoteDescription(new RTCSessionDescription({type: "offer", sdp : bridgeSDP.raw}));
	};

	var sendAnswer = function(from, videobridge, confid, channelId) 
	{
		console.log("sendAnswer");

		var remoteSDP = new SDP(window.RTC.rayo.pc.localDescription.sdp);	

		//console.log("remoteSDP ", window.RTC.rayo.pc.localDescription.sdp);

		var change = $iq({to: from, type: 'set'});
		change.c('colibri', {xmlns: 'urn:xmpp:rayo:colibri:1', videobridge: videobridge});					
		change.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: confid});

		for (channel = 0; channel < 2; channel++) 
		{
		    if (channelId[channel])
		    {
			change.c('content', {name: channel === 0 ? 'audio' : 'video'});
			change.c('channel', {id: channelId[channel]});

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
			    console.log('rayo colibri answer set ok', window.RTC.rayo.pc.signalingState);			
			},

			function (err) {
			    console.log('rayo colibri answer got error ' + err);
			}
		);														

	};

	var removeSSRC = function(from, removesource) {
		
		var videobridge = $(removesource).attr('videobridge');	
		var active = $(removesource).attr('active');	
		var sdp = new SDP(window.RTC.rayo.pc.remoteDescription.sdp);

		console.log("removeSSRC unmodified SDP", videobridge);
				
		$(removesource).find('content').each(function() 
		{		
			var name = $(this).attr('name');
			var ssrc = null;

			$(this).find('source').each(function() 
			{
			    ssrc = $(this).attr('ssrc');			    
			});

			if (ssrc != null)
			{
				var idx = (name == "audio" ? 0 : 1);
				sdp.removeMediaLines(idx, 'a=ssrc:' + ssrc);
			}
		});

		sdp.raw = sdp.session + sdp.media.join('');
					
		//console.log("removeSSRC modified SDP", sdp.raw);
		
		window.RTC.rayo.pc.setRemoteDescription(new RTCSessionDescription({type: 'offer', sdp: sdp.raw}		

			), function() {
			    console.log('modify ok');	

			}, function(error) {
			    console.log('handleSSRC modify failed');
		});
		
		console.log("removeSSRC exit ", removesource);
		
	};

	var handleAddSSRC = function(from, addsource) {
				
		var videobridge = $(addsource).attr('videobridge');					
		var sdp = new SDP(window.RTC.rayo.pc.remoteDescription.sdp);

		console.log("handleAddSSRC unmodified SDP", videobridge);
						
		$(addsource).find('content').each(function() 
		{		
			var name = $(this).attr('name');
			
			var lines = '';

			$(this).find('source').each(function() 
			{
			    var ssrc = $(this).attr('ssrc');

			    $(this).find('>parameter').each(function () {
				lines += 'a=ssrc:' + ssrc + ' ' + $(this).attr('name');

				if ($(this).attr('value') && $(this).attr('value').length)
				    lines += ':' + $(this).attr('value');

				lines += '\r\n';
			    });				    
			});

			var idx = (name == "audio" ? 0 : 1);
			sdp.media[idx] += lines;	
		});

		sdp.raw = sdp.session + sdp.media.join('');
			
		window.RTC.rayo.addssrc = true;
						
		window.RTC.rayo.pc.setRemoteDescription(new RTCSessionDescription({type: 'offer', sdp: sdp.raw}		

			), function() {
			    console.log('modify ok', window.RTC.rayo.pc.signalingState);			    

			}, function(error) {
			    console.log('handleSSRC modify failed');
		});
		
		console.log("handleSSRC exit ", addsource);		
	};

	
	var handleAnswer = function(from, answer) {

		console.log("handleAnswer", answer);

		var bridgeSDP = new SDP('v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 1 RTP/SAVPF 111 103 104 0 8 106 105 13 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=sendrecv\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:103 ISAC/16000\r\na=rtpmap:104 ISAC/32000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:106 CN/32000\r\na=rtpmap:105 CN/16000\r\na=rtpmap:13 CN/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\nm=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=sendrecv\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n');		

		var muc = $(answer).attr('muc');
		var nick = $(answer).attr('nickname');
		var participant = $(answer).attr('participant');
		var videobridge = $(answer).attr('videobridge');
		var confid = null;
		var channelId = [];

		$(answer).find('conference').each(function() 
		{		
			confid = $(this).attr('id');

			$(this).find('content').each(function() 
			{		
				var name = $(this).attr('name');
				var channel = name == "audio" ? 0 : 1;				

				$(this).find('channel').each(function() 
				{
					channelId[channel] = $(this).attr('id');

					$(this).find('source').each(function() 
					{	
						var ssrc = $(this).attr('ssrc');
;							
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

		window.RTC.rayo.pc.setRemoteDescription
		(
			new RTCSessionDescription({type: "answer", sdp: bridgeSDP.raw}),
			
			function () {
			    console.log('setRemoteDescription success');
			},
			function (error) {
			    console.log('setRemoteDescription failed', error);
			}		
		);
		
		//console.log("bridgeSDP.raw", bridgeSDP.raw);			
	};

	$(window).bind('beforeunload', function () 
	{
	    if (connection && connection.connected) {
		
		window.RTC.rayo.localStream.stop();
		window.RTC.rayo.pc.close();
		connection.disconnect();
	    }
	});
	
	    
	$(document).bind('mediaready.rayo', function(event, stream) {
		window.RTC.rayo.localStream = stream;

		window.RTC.attachMediaStream($('#localVideo'), stream);
		document.getElementById('localVideo').muted = true;
		document.getElementById('localVideo').autoplay = true;
		document.getElementById('localVideo').volume = 0;

		document.getElementById('largeVideo').volume = 0;
		document.getElementById('largeVideo').src = document.getElementById('localVideo').src;

		console.log("mediaready.rayo"); 
		
		videoControl();
	});
    
	$(document).bind('mediafailure.rayo', function(error) {
		console.error('mediafailure.rayo ' + error);
	});    

    $(document).bind('remotestreamadded.rayo', function(event, data, sid) {
	console.log("remotestreamadded.rayo", sid);
	
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

	if (sel.attr('id').indexOf('mixedmslabel') == -1) {
	    // ignore mixedmslabela0 and non room members
	    sel.show();
	    resizeThumbnails();

	    document.getElementById('largeVideo').volume = 1;
	    $('#largeVideo').attr('src', sel.attr('src'));
	}

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
                
		if ($("#largeVideo").css("visibility") == "hidden")
		{                                
			$("#largeVideo").css("visibility", "visible");
			var newSrc = $(this).attr('src');
			if ($('#largeVideo').attr('src') != newSrc) {
			    document.getElementById('largeVideo').volume = 1;
			    $('#largeVideo').fadeOut(300, function(){
				$(this).attr('src', newSrc);
				$(this).fadeIn(300);
			    });
			}
		} else {
		
		 $("#largeVideo").css("visibility", "hidden");
		}
            }
        );
    });

    $(document).bind('callterminated.rayo', function(event, sid, reason) {
        // FIXME
    });


    var resizeLarge = function() {
        var availableHeight = window.innerHeight;
        var numvids = $('#remoteVideos>video:visible').length;
        if (numvids < 5)
            availableHeight -= 100; // min thumbnail height for up to 4 videos
        else
            availableHeight -= 50; // min thumbnail height for more than 5 videos

        availableHeight -= 79; // padding + link ontop
        var availableWidth = window.innerWidth;
        var aspectRatio = 16.0 / 9.0;
        if (availableHeight < availableWidth / aspectRatio) {
            availableWidth = Math.floor(availableHeight * aspectRatio);
        }
        if (availableWidth < 0 || availableHeight < 0) return;
        $('#largeVideo').width(availableWidth);
        $('#largeVideo').height(availableWidth/aspectRatio);
        $('#chatspace').width(availableWidth);
        resizeThumbnails() ;
    }
    
    var resizeThumbnails = function() {
        // Calculate the available height, which is the inner window height minus 39px for the header
        // minus 4px for the delimiter lines on the top and bottom of the large video,
        // minus the 36px space inside the remoteVideos container used for highlighting shadow.
        var availableHeight = window.innerHeight - $('#largeVideo').height() - 79;
        var numvids = $('#remoteVideos>video:visible').length;
        // Remove the 1px borders arround videos.
        var availableWinWidth = $('#remoteVideos').width() - numvids*2;
        var availableWidth = availableWinWidth / numvids;
        var aspectRatio = 16.0 / 9.0;
        var maxHeight = Math.min(160, availableHeight);
        var availableHeight = Math.min(maxHeight, availableWidth / aspectRatio);
        if (availableHeight < availableWidth / aspectRatio) {
            availableWidth = Math.floor(availableHeight * aspectRatio);
        }
        // size videos so that while keeping AR and max height, we have a nice fit
        $('#remoteVideos').height(availableHeight + 36); // add the 2*18px border used for highlighting shadow.
        $('#remoteVideos>video:visible').width(availableWidth);
        $('#remoteVideos>video:visible').height(availableHeight);
    }

    var setupRTC = function() {
	    var RTC = null;
	    if (navigator.mozGetUserMedia) {
		console.log('This appears to be Firefox');
		var version = parseInt(navigator.userAgent.match(/Firefox\/([0-9]+)\./)[1], 10);
		if (version >= 22) {
		    RTC = {
		    	rayo: {
		    		pc: null,		    	
				channels: {},	
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
		    		pc: null,
				channels: {},
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
	//            pc_constraints: {} // FIVE-182
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
    
    var getUsermedia = function()
    {
    	console.log("getUsermedia", window.RTC.rayo.constraints);
    	
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

    var getConstraints = function(um, resolution, bandwidth, fps) 
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
			"chromeMediaSource": "screen"
		    }
		};
	    }

	    if (resolution && !window.RTC.rayo.constraints.video) {
		window.RTC.rayo.constraints.video = {mandatory: {}};// same behaviour as true
	    }
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
    return self;
}(CandyShop.Videobridge || {}, Candy, jQuery));    
