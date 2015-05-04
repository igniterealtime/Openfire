/**
 * Strophe.inum connection plugin.
 */

 Strophe.addConnectionPlugin('inum', 
 {
       connection: null,
       audioChannels: {},
       localStream: null,

	init: function (conn) 
	{
		this.connection = conn; 

		console.log("strophe plugin inum enabled");                              
	},

	hangup: function (callId)
	{
	    var self = this;

	    var req = $iq(
		{
		    type: 'set',
		    to: callId
		}
	    );
	    req.c('hangup',
		{
		    xmlns: "urn:xmpp:rayo:1"
		});

	    this.connection.sendIQ(req,

		function (result)
		{
		    console.info('Hangup result ', result);
		    $(document).trigger('inum.cleared', [callId]);
		},
		function (error)
		{
		    console.info('Hangup error ', error);
		}
	    );
	},

	dial: function (confId, to)
	{
	    var self = this;
	    var req = $iq(
		{
		    type: 'set',
		    to: config.hosts.call_control
		}
	    );
	    req.c('dial',
		{
		    xmlns: "urn:xmpp:rayo:1",
		    to: to,
		    from: connection.jid
		});
	    req.c('header',
		{
		    name: 'JvbRoomId',
		    value: confId
		});

	    this.connection.sendIQ(req,

		function (result)
		{
		    console.info('Dial result ', result);

		    var resource = $(result).find('ref').attr('uri');
		    self.call_resource = resource.substr('xmpp:'.length);

		    console.info("Received call resource: " + self.call_resource);

		    $(document).trigger('inum.dialled', [confId, to, self.call_resource]);			    
		},

		function (error)
		{
		    console.error('inum plugin dial error ', error);
		}
	    );
	},

	createWebrtcDevice: function()
	{
		console.log("createWebrtcDevice request");

		var that = this;                   

		navigator.webkitGetUserMedia({audio: true, video: false}, function(stream) 
		{
			that.localStream = stream;

			var iq = $iq({to: config.hosts.bridge, type: 'get'});

			iq.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri'});                       
			iq.c('content', {name: 'audio'}).c('channel', {initiator: 'true', expire: '15', "rtp-level-relay-type": "mixer", endpoint: that.connection.jid});

			that.connection.sendIQ(iq, function (offer) 
			{
				that.handleOffer(offer);

			}, function (err) {

				console.error('createWebrtcDevice', err);                                                
			});                                                           

		}, function(error) {

			console.error("No audio device found");
		});
	},

	handleOffer: function(offer) 
	{
		var offerSDP = new SDP('v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 1 RTP/SAVPF 111 0 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=sendrecv\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\nm=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=sendrecv\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n');                   
		offerSDP.media[1] = null;

		console.log("handleOffer", offer, this.localStream, offerSDP);

		var that = this;
		var confId = null;
		var audioId = null;

		$(offer).find('conference').each(function() 
		{
			confId = $(this).attr('id');
			that.audioChannels[confId] = {confId: confId};

			$(this).find('content').each(function() 
			{                              
				var name = $(this).attr('name');                                

				if (name == "audio")
				{
					$(this).find('channel').each(function() 
					{
						audioId = $(this).attr('id');
						that.audioChannels[confId].audioId = audioId;

						//console.log("createWebrtcDevice audio track " + that.audioChannels[confId].audioId);                                                                                                                                                                                    

						$(this).find('source').each(function() 
						{              
							var ssrc = $(this).attr('ssrc');

							offerSDP.media[0] += 'a=ssrc:' + ssrc + ' ' + 'cname:mixed' + '\r\n';
							offerSDP.media[0] += 'a=ssrc:' + ssrc + ' ' + 'label:mixedlabela0' + '\r\n';
							offerSDP.media[0] += 'a=ssrc:' + ssrc + ' ' + 'msid:mixedmslabela0 mixedlabela0' + '\r\n';
							offerSDP.media[0] += 'a=ssrc:' + ssrc + ' ' + 'mslabel:mixedmslabela0' + '\r\n';                                                                                                            
						});

						$(this).find('transport').each(function() 
						{              
							var pwd = $(this).attr('pwd');
							var ufrag = $(this).attr('ufrag');

							if (ufrag) offerSDP.media[0] += 'a=ice-ufrag:' + ufrag + '\r\n';
							if (pwd) offerSDP.media[0] += 'a=ice-pwd:' + pwd + '\r\n';

							$(this).find('candidate').each(function() 
							{              
								offerSDP.media[0] += SDPUtil.candidateFromJingle(this);                                                                                          
							});

							$(this).find('fingerprint').each(function() 
							{              
								var hash = $(this).attr('hash');
								var setup  = $(this).attr('setup');
								var fingerprint = $(this).text();

								if (hash && fingerprint) offerSDP.media[0] += 'a=fingerprint:' + hash + ' ' + fingerprint + '\r\n';
								if (setup) offerSDP.media[0] += 'a=setup:' + setup + '\r\n';    

							});                                                                                                           
						});                                                                                           
					});
				}
			});                                                           
		});

		offerSDP.raw = offerSDP.session + offerSDP.media.join('');
		that.audioChannels[confId].sdp = offerSDP.raw;

		//console.log("createWebrtcDevice offerSDP.raw", offerSDP.raw);         

		that.audioChannels[confId].peerconnection = new webkitRTCPeerConnection(null, {'optional': [{'DtlsSrtpKeyAgreement': 'true'}]}); 

		that.audioChannels[confId].peerconnection.onicecandidate = function(event)
		{
			//console.log('createWebrtcDevice candidate', event.candidate);

			if (!event.candidate) 
			{
				that.sendAnswer(confId);
			}

		}

		that.audioChannels[confId].peerconnection.onaddstream = function(data)
		{
			console.log("createWebrtcDevice onstream", data);

			if (that.audioChannels[confId].peerconnection.signalingState == "have-remote-offer")
			{
				this.audio = new Audio();
				this.audio.autoplay = true;
				this.audio.volume = 1;
				this.audio.src = URL.createObjectURL(data.stream);                                                       
			}

			that.audioChannels[confId].peerconnection.createAnswer(function(desc)
			{
				that.audioChannels[confId].peerconnection.setLocalDescription(desc);
				$(document).trigger('inum.connected', [confId, audioId]);					
			});                                                           
		};                                             

		that.audioChannels[confId].peerconnection.addStream(that.localStream);
		that.audioChannels[confId].peerconnection.setRemoteDescription(new RTCSessionDescription({type: "offer", sdp : offerSDP.raw}));

		$(document).trigger('inum.offered', [confId, audioId]);
	},

	sendAnswer: function (confId) 
	{
		var audioId = this.audioChannels[confId].audioId;

		//console.log("sendAnswer", confId, audioId, this.audioChannels[confId].peerconnection.localDescription.sdp);

		var that = this;

		var remoteSDP = new SDP(this.audioChannels[confId].peerconnection.localDescription.sdp);   

		//console.log("remoteSDP ", remoteSDP);

		var iq = $iq({to: config.hosts.bridge, type: 'set'});

		iq.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: confId});                  
		iq.c('content', {name: 'audio'}).c('channel', {id: audioId});

		var tmp = SDPUtil.find_lines(remoteSDP.media[0], 'a=ssrc:');

		iq.c('source', { xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });

		tmp.forEach(function (line) 
		{
			var idx = line.indexOf(' ');
			var linessrc = line.substr(0, idx).substr(7);
			iq.attrs({ssrc: linessrc});

			var kv = line.substr(idx + 1);
			iq.c('parameter');

			if (kv.indexOf(':') == -1) {
			    iq.attrs({ name: kv });
			} else {
			    iq.attrs({ name: kv.split(':', 2)[0] });
			    iq.attrs({ value: kv.split(':', 2)[1] });
			}
			iq.up();
		});

		iq.up(); // end of source

		var rtpmap = SDPUtil.find_lines(remoteSDP.media[0], 'a=rtpmap:');

		rtpmap.forEach(function (val) 
		{
			var rtpmap = SDPUtil.parse_rtpmap(val);
			iq.c('payload-type', rtpmap);
			iq.up();
		});


		iq.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});                                                                                              
		var fingerprints = SDPUtil.find_lines(remoteSDP.media[0], 'a=fingerprint:', remoteSDP.session);

		fingerprints.forEach(function (line) 
		{
		    var tmp = SDPUtil.parse_fingerprint(line);
		    tmp.xmlns = 'urn:xmpp:jingle:apps:dtls:0';
		    iq.c('fingerprint').t(tmp.fingerprint);
		    delete tmp.fingerprint;
		    var line = SDPUtil.find_line(remoteSDP.media[0], 'a=setup:', remoteSDP.session);

		    if (line) {
				tmp.setup = line.substr(8);
		    }
		    iq.attrs(tmp);
		    iq.up();
		});

		var candidates = SDPUtil.find_lines(remoteSDP.media[0], 'a=candidate:', remoteSDP.session);

		candidates.forEach(function (line) {
			var tmp = SDPUtil.candidateToJingle(line);
			iq.c('candidate', tmp).up();
		});

		tmp = SDPUtil.iceparams(remoteSDP.media[0], remoteSDP.session);

		if (tmp) {
			iq.attrs(tmp);

		}

		this.connection.sendIQ(iq,

			function (res) {
			    console.log('sendAnswer ok', res);  
			    $(document).trigger('inum.answered', [confId, audioId]);				    
			},

			function (err) {
			    console.error('sendAnswer error', err);                                 
			}
		);                                                                                                                                                                                                                             

	},

	expireWebrtcDevice: function(confId, audioId)
	{
		console.log("expireWebrtcDevice " + confId);

		if (this.audioChannels[confId] && this.audioChannels[confId].peerconnection)
		{
			this.audioChannels[confId].peerconnection.close();
		}

		if (this.localStream)
		{
			this.localStream.stop();
		}

		var iq = $iq({to: config.hosts.bridge, type: 'get'});

		iq.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: confId});                  
		iq.c('content', {name: 'audio'}).c('channel', {id: audioId, expire: '0'});

		var that = this;

		this.connection.sendIQ(iq, 

			function (res) {
			    console.log('expireWebrtcDevice response', res);
			    $(document).trigger('inum.expired', [confId, audioId]);					    
			},

			function (err) {
			    console.error('expireWebrtcDevice error', err);                                                 
			}
		); 

		this.audioChannels[confId].peerconnection = null;
		this.localStream = null;
	}                              
}); 