/*
Copyright (c) 2013 ESTOS GmbH

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
/* jshint -W117 */
// static offer taken from chrome M31
var staticoffer = 'v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 1 RTP/SAVPF 111 103 104 0 8 106 105 13 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=sendrecv\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:103 ISAC/16000\r\na=rtpmap:104 ISAC/32000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:106 CN/32000\r\na=rtpmap:105 CN/16000\r\na=rtpmap:13 CN/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\nm=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=sendrecv\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n';

function Colibri(connection, bridgejid) {
    this.connection = connection;
    this.bridgejid = bridgejid;
    this.peers = [];
    this.confid = null;

    this.peerconnection = null;

    this.sid = Math.random().toString(36).substr(2, 12);
    connection.jingle.sessions[this.sid] = this;
    this.mychannel = [];
    this.channels = [];
    this.remotessrc = {};

    // ssrc lines to be added on next update
    this.addssrc = [];
    // ssrc lines to be removed on next update
    this.removessrc = [];
    this.wait = true;
}

// creates a conferences with an initial set of peers
// FIXME: is the initial set required?
Colibri.prototype.makeConference = function (peers) {
    var ob = this;
    if (this.confid !== null) {
        // makeConference called twice...
        console.error('somegoing bad is going to happen');
        // FIXME: just invite peers?
        return;
    }
    this.confid = 0; // !null
    this.peers = [];
    peers.forEach(function(peer) { 
        ob.peers.push(peer);
        ob.channels.push([]);
    });

    this.peerconnection = new RTC.peerconnection(this.connection.jingle.ice_config, this.connection.jingle.pc_constraints);
    this.peerconnection.addStream(this.connection.jingle.localStream);
    this.peerconnection.oniceconnectionstatechange = function (event) {
        console.warn('ice connection state changed to', ob.peerconnection.iceConnectionState);
        /*
        if (ob.peerconnection.signalingState == 'stable' && ob.peerconnection.iceConnectionState == 'connected') {
            console.log('adding new remote SSRCs from iceconnectionstatechange');
            window.setTimeout(function() { ob.modifySources(); }, 1000);
        }
        */
    }
    this.peerconnection.onsignalingstatechange = function (event) {
        console.warn(ob.peerconnection.signalingState);
        /*
        if (ob.peerconnection.signalingState == 'stable' && ob.peerconnection.iceConnectionState == 'connected') {
            console.log('adding new remote SSRCs from signalingstatechange');
            window.setTimeout(function() { ob.modifySources(); }, 1000);
        }
        */
    }
    this.peerconnection.onaddstream = function (event) {
        ob.remoteStream = event.stream;
        $(document).trigger('remotestreamadded.jingle', [event, ob.sid]);
    };
    this.peerconnection.onicecandidate = function (event) {
        ob.sendIceCandidate(event.candidate);
    };
    this.peerconnection.createOffer(
        function (offer) {
            ob.peerconnection.setLocalDescription(offer,
                function() {
                    // success
                },
                function(error) {
                    console.log('setLocalDescription failed', error);
                }
            );
        },
        function (error) {
            console.warn(error);
        }
    );
    this.peerconnection.onicecandidate = function (event) {
        console.log('candidate', event.candidate);
        if (!event.candidate) {
            console.log('end of candidates');
            ob._makeConference();
            return;
        }
    }
}
Colibri.prototype._makeConference = function () {
    var ob = this;
    var elem = $iq({to: this.bridgejid, type: 'get'});
    elem.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri'});

    var localSDP = new SDP(this.peerconnection.localDescription.sdp);
    var contents = SDPUtil.find_lines(localSDP.raw, 'a=mid:').map(SDPUtil.parse_mid);
    localSDP.media.forEach(function(media, channel) {
        var name = SDPUtil.parse_mid(SDPUtil.find_line(media, 'a=mid:'));
        elem.c('content', {creator: 'initiator', name: name});
        elem.c('channel', {initiator: 'false'});

        var mline = SDPUtil.parse_mline(media.split('\r\n')[0]);
        for (var j = 0; j < mline.fmt.length; j++) {
            var rtpmap = SDPUtil.find_line(media, 'a=rtpmap:' + mline.fmt[j]);
            elem.c('payload-type', SDPUtil.parse_rtpmap(rtpmap));
            elem.up();
        }

        elem.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});
        var tmp = SDPUtil.iceparams(media, localSDP.session);
        if (tmp) {
            elem.attrs(tmp);
            var fingerprints = SDPUtil.find_lines(media, 'a=fingerprint:', localSDP.session);
            fingerprints.forEach(function (line) {
                tmp = SDPUtil.parse_fingerprint(line);
                //tmp.xmlns = 'urn:xmpp:tmp:jingle:apps:dtls:0';
                tmp.xmlns = 'urn:xmpp:jingle:apps:dtls:0';
                elem.c('fingerprint').t(tmp.fingerprint);
                delete tmp.fingerprint;
                line = SDPUtil.find_line(media, 'a=setup:', ob.session);
                if (line) {
                    tmp.setup = line.substr(8);
                }
                elem.attrs(tmp);
                elem.up();
            });
            // XEP-0176
            if (SDPUtil.find_line(media, 'a=candidate:', localSDP.session)) { // add any a=candidate lines
                lines = SDPUtil.find_lines(media, 'a=candidate:', localSDP.session);
                for (j = 0; j < lines.length; j++) {
                    tmp = SDPUtil.candidateToJingle(lines[j]);
                    elem.c('candidate', tmp).up();
                }
            }
            elem.up(); // end of transport
        }
        elem.up(); // end of channel
        for (var j = 0; j < ob.peers.length; j++) {
            elem.c('channel', {initiator: 'true' }).up();
        }
        elem.up(); // end of content
    });

    this.connection.sendIQ(elem,
        function(result) {
            ob.createdConference(result);
        },
        function (error) {
            console.warn(error);
        }
    );
};

// callback when a conference was created
Colibri.prototype.createdConference = function(result) {
    console.log('created a conference on the bridge');
    var tmp;

    this.confid = $(result).find('>conference').attr('id');
    var remotecontents = $(result).find('>conference>content').get();
    for (var i = 0; i < remotecontents.length; i++) {
        tmp = $(remotecontents[i]).find('>channel').get();
        this.mychannel.push($(tmp.shift()));
        for (j = 0; j < tmp.length; j++) {
            if (this.channels[j] === undefined) {
                this.channels[j] = [];
            }
            this.channels[j].push(tmp[j]);
        }
    }
    console.log('remote channels', this.channels);

    // establish our channel with the bridge
    // static answer taken from chrome, should be replaced by a dynamic one
    // that is based on our offer
    var bridgeSDP = new SDP('v=0\r\no=- 5151055458874951233 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 1 RTP/SAVPF 111 103 104 0 8 106 105 13 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=sendrecv\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10\r\na=rtpmap:103 ISAC/16000\r\na=rtpmap:104 ISAC/32000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:106 CN/32000\r\na=rtpmap:105 CN/16000\r\na=rtpmap:13 CN/8000\r\na=rtpmap:126 telephone-event/8000\r\na=maxptime:60\r\nm=video 1 RTP/SAVPF 100 116 117\r\nc=IN IP4 0.0.0.0\r\na=rtcp:1 IN IP4 0.0.0.0\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=sendrecv\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 goog-remb\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\n');
    // get the mixed ssrc
    for (var channel = 0; channel < remotecontents.length; channel++) {
        tmp = $(this.mychannel[channel]).find('>source[xmlns="urn:xmpp:jingle:apps:rtp:ssma:0"]');
        if (tmp.length) {
            bridgeSDP.media[channel] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'cname:mixed' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'label:mixedlabela0' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'msid:mixedmslabela0 mixedlabela0' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'mslabel:mixedmslabela0' +'\r\n';
        } else {
            // make chrome happy... '3735928559' == 0xDEADBEEF
            bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'cname:mixed' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'label:mixedlabelv0' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'msid:mixedmslabelv0 mixedlabelv0' +'\r\n';
            bridgeSDP.media[channel] += 'a=ssrc:' + '3735928559' + ' ' + 'mslabel:mixedmslabelv0' +'\r\n';
        }

        tmp = $(this.mychannel[channel]).find('>transport[xmlns="urn:xmpp:jingle:transports:ice-udp:1"]');
        if (tmp.length) {
            bridgeSDP.media[channel] += 'a=ice-ufrag:' + tmp.attr('ufrag') + '\r\n';
            bridgeSDP.media[channel] += 'a=ice-pwd:' + tmp.attr('pwd') + '\r\n';
            tmp.find('>candidate').each(function() {
                bridgeSDP.media[channel] += SDPUtil.candidateFromJingle(this);
            });
            tmp = tmp.find('>fingerprint');
            if (tmp.length) {
                bridgeSDP.media[channel] += 'a=fingerprint:' + tmp.attr('hash') + ' ' + tmp.text() + '\r\n';
                if (tmp.attr('direction')) {
                    bridgeSDP.media[channel]+= 'a=setup:' + tmp.attr('direction') + '\r\n';
                }
            }
        }
    }
    bridgeSDP.raw = bridgeSDP.session + bridgeSDP.media.join('');
    var ob = this;
    this.peerconnection.setRemoteDescription(new RTCSessionDescription({type:'answer', sdp:bridgeSDP.raw}),
        function () {
            console.log('setRemoteDescription success');
            // remote channels == remotecontents length - 1!
            for (var i = 0; i < remotecontents.length - 1; i++) { 
                ob.initiate(ob.peers[i], true);
            }
        },
        function(error) {
            console.log('setRemoteDescription failed');
        }
    );

};

// send a session-initiate to a new participant
Colibri.prototype.initiate = function (peer, isInitiator) {
    var participant = this.peers.indexOf(peer);
    console.log('tell', peer, participant);
    var sdp;
    if (this.peerconnection != null && this.peerconnection.signalingState == 'stable') {
        sdp = new SDP(this.peerconnection.remoteDescription.sdp);
        var localSDP = new SDP(this.peerconnection.localDescription.sdp);
        // throw away stuff we don't want
        // not needed with static offer
        var line;
        sdp.removeSessionLines('a=group:');
        sdp.removeSessionLines('a=msid-semantic:');
        for (var j = 0; j < sdp.media.length; j++) {
            sdp.removeMediaLines(j, 'a=rtcp-mux');
            sdp.removeMediaLines(j, 'a=ssrc:');
            sdp.removeMediaLines(j, 'a=crypto:');
            sdp.removeMediaLines(j, 'a=candidate:');
            sdp.removeMediaLines(j, 'a=ice-options:google-ice');
            sdp.removeMediaLines(j, 'a=ice-ufrag:');
            sdp.removeMediaLines(j, 'a=ice-pwd:');
            sdp.removeMediaLines(j, 'a=fingerprint:');
            sdp.removeMediaLines(j, 'a=setup:');

            // re-add all remote a=ssrcs
            for (var jid in this.remotessrc) {
                if (jid == peer) continue;
                sdp.media[j] += this.remotessrc[jid][j];
            }
            // and local a=ssrc lines
            sdp.media[j] += SDPUtil.find_lines(localSDP.media[j], 'a=ssrc').join('\r\n') + '\r\n';
        }
        sdp.raw = sdp.session + sdp.media.join('');
    } else {
        sdp = new SDP(staticoffer);
        console.warn('staticoffer, should no longer happen here');
    }

    // make a new colibri session and configure it
    var sess = new ColibriSession(this.connection.jid,
                                  Math.random().toString(36).substr(2, 12), // random string
                                  this.connection);
    sess.initiate(peer); 
    sess.colibri = this;
    sess.localStream = this.connection.jingle.localStream;
    sess.media_constraints = this.connection.jingle.media_constraints;
    sess.pc_constraints = this.connection.jingle.pc_constraints;
    sess.ice_config = this.connection.ice_config;

    connection.jingle.sessions[sess.sid] = sess;
    connection.jingle.jid2session[sess.peerjid] = sess;


    var init = $iq({to: sess.peerjid,
                   type: 'set'})
            .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
               action: 'session-initiate',
               initiator: sess.me,
               sid: sess.sid });

    // add stuff we got from the bridge
    for (var j = 0; j < sdp.media.length; j++) {
        var chan = $(this.channels[participant][j]);
        console.log('channel id', chan.attr('id'));

        tmp = chan.find('>source[xmlns="urn:xmpp:jingle:apps:rtp:ssma:0"]');
        if (tmp.length) {
            sdp.media[j] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'cname:mixed' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'label:mixedlabela0' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'msid:mixedmslabela0 mixedlabela0' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + tmp.attr('ssrc') + ' ' + 'mslabel:mixedmslabela0' +'\r\n';
        } else {
            // make chrome happy... '3735928559' == 0xDEADBEEF
            sdp.media[j] += 'a=ssrc:' + '3735928559' + ' ' + 'cname:mixed' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + '3735928559' + ' ' + 'label:mixedlabelv0' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + '3735928559' + ' ' + 'msid:mixedmslabelv0 mixedlabelv0' +'\r\n';
            sdp.media[j] += 'a=ssrc:' + '3735928559' + ' ' + 'mslabel:mixedmslabelv0' +'\r\n';
        }

        tmp = chan.find('>transport[xmlns="urn:xmpp:jingle:transports:ice-udp:1"]');
        if (tmp.length) {
            if (tmp.attr('ufrag'))
                sdp.media[j] += 'a=ice-ufrag:' + tmp.attr('ufrag') + '\r\n';
            if (tmp.attr('pwd'))
                sdp.media[j] += 'a=ice-pwd:' + tmp.attr('pwd') + '\r\n';
            // and the candidates...
            tmp.find('>candidate').each(function () {
                sdp.media[j] += SDPUtil.candidateFromJingle(this);
            });
            tmp = tmp.find('>fingerprint');
            if (tmp.length) {
                sdp.media[j] += 'a=fingerprint:' + tmp.attr('hash') + ' ' + tmp.text() + '\r\n';
                /*
                if (tmp.attr('direction')) {
                    sdp.media[j] += 'a=setup:' + tmp.attr('direction') + '\r\n';
                }
                */
                sdp.media[j] += 'a=setup:actpass\r\n';
            }
        }
    }
    sdp.toJingle(init, 'initiator');
    this.connection.sendIQ(init,
        function (res) {
            console.log('got result');
        },
        function (err) {
            console.log('got error');
        }
    );
}

// pull in a new participant into the conference
Colibri.prototype.addNewParticipant = function(peer) {
    var ob = this;
    if (this.confid === 0) {
        // bad state
        console.log('confid does not exist yet, postponing', peer);
        window.setTimeout(function() {
            ob.addNewParticipant(peer);
        }, 250);
        return;
    }
    var index = this.channels.length;
    this.channels.push([])
    this.peers.push(peer);

    var elem = $iq({to: this.bridgejid, type: 'get'});
    elem.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: this.confid});
    var localSDP = new SDP(this.peerconnection.localDescription.sdp);
    var contents = SDPUtil.find_lines(localSDP.raw, 'a=mid:').map(SDPUtil.parse_mid);
    contents.forEach(function(name) {
        elem.c('content', {creator: 'initiator', name: name});
        elem.c('channel', {initiator: 'true'});
        elem.up(); // end of channel
        elem.up(); // end of content
    });

    this.connection.sendIQ(elem,
        function(result) {
            var contents = $(result).find('>conference>content').get();
            for (var i = 0; i < contents.length; i++) {
                tmp = $(contents[i]).find('>channel').get();
                ob.channels[index][i] = tmp[0];
            }
            ob.initiate(peer, true);
        },
        function (error) {
            console.warn(error);
        }
    );
}

// update the channel description (payload-types + dtls fp) for a participant
Colibri.prototype.updateChannel = function (remoteSDP, participant) {
    console.log('change allocation for', this.confid);
    var change = $iq({to: this.bridgejid, type: 'set'});
    change.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: this.confid});
    for (channel = 0; channel < this.channels[participant].length; channel++) {
        change.c('content', {name: channel == 0 ? 'audio' : 'video'});
        change.c('channel', {id: $(this.channels[participant][channel]).attr('id'), initiator: 'true'});
        console.log('channel id', $(this.channels[participant][channel]).attr('id'));

        var rtpmap = SDPUtil.find_lines(remoteSDP.media[channel], 'a=rtpmap:');
        rtpmap.forEach(function (val) {
            // TODO: too much copy-paste
            var rtpmap = SDPUtil.parse_rtpmap(val);
            change.c('payload-type', rtpmap);
            // 
            // put any 'a=fmtp:' + mline.fmt[j] lines into <param name=foo value=bar/>
            /*
            if (SDPUtil.find_line(remoteSDP.media[channel], 'a=fmtp:' + rtpmap.id)) {
                tmp = SDPUtil.parse_fmtp(SDPUtil.find_line(remoteSDP.media[channel], 'a=fmtp:' + rtpmap.id));
                for (var k = 0; k < tmp.length; k++) {
                    change.c('parameter', tmp[k]).up();
                }
            }
            */
            change.up();
        });

        // now add transport
        change.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});
        var fingerprints = SDPUtil.find_lines(remoteSDP.media[channel], 'a=fingerprint:', remoteSDP.session);
        fingerprints.forEach(function (line) {
            tmp = SDPUtil.parse_fingerprint(line);
            tmp.xmlns = 'urn:xmpp:jingle:apps:dtls:0';
            change.c('fingerprint').t(tmp.fingerprint);
            delete tmp.fingerprint;
            line = SDPUtil.find_line(remoteSDP.media[channel], 'a=setup:', remoteSDP.session);
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
    this.connection.sendIQ(change,
        function (res) {
            console.log('got result');
        },
        function (err) {
            console.log('got error');
        }
    );
};

// tell everyone about a new participants a=ssrc lines (isadd is true)
// or a leaving participants a=ssrc lines
// FIXME: should not take an SDP, but rather the a=ssrc lines and probably a=mid
Colibri.prototype.sendSSRCUpdate = function(sdp, exclude, isadd) {
    var ob = this;
    // FIXME: this should only send to participants that are stable, i.e. who have sent a session-accept
    // possibly, this.remoteSSRC[session.peerjid] does not exist yet
    this.peers.forEach(function(peerjid) {
        if (peerjid == exclude) return;
        console.warn('tell', peerjid, 'about ' + (isadd ? 'new' : 'removed') + ' ssrcs from', exclude);
        console.warn('remoteSSRC', ob.remotessrc[peerjid]);
        if (!ob.remotessrc[peerjid]) {
            // don't tell them about updates yet?
            console.warn('do we really want to bother', peerjid, 'with updates yet?')
        }
        var channel;
        var peersess = ob.connection.jingle.jid2session[peerjid];
        var modify = $iq({to: peerjid, type: 'set'})
            .c('jingle', {
                xmlns: 'urn:xmpp:jingle:1',
                action: isadd ? 'addsource' : 'removesource',
                initiator: peersess.initiator,
                sid: peersess.sid
            }
        );
        for (channel = 0; channel < sdp.media.length; channel++) {
            tmp = SDPUtil.find_lines(sdp.media[channel], 'a=ssrc:');
            modify.c('content', {name: SDPUtil.parse_mid(SDPUtil.find_line(sdp.media[channel], 'a=mid:'))});
            modify.c('source', { xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });
            // FIXME: not completly sure this operates on blocks and / or handles different ssrcs correctly
            tmp.forEach(function(line) {
                var idx = line.indexOf(' ');
                var linessrc = line.substr(0, idx).substr(7);
                modify.attrs({ssrc:linessrc});

                var kv = line.substr(idx + 1);
                modify.c('parameter');
                if (kv.indexOf(':') == -1) {
                    modify.attrs({ name: kv });
                } else {
                    modify.attrs({ name: kv.split(':', 2)[0] });
                    modify.attrs({ value: kv.split(':', 2)[1] });
                }
                modify.up();
            });
            modify.up(); // end of source
            modify.up(); // end of content
        }
        ob.connection.sendIQ(modify,
            function (res) {
                console.warn('got modify result');
            },
            function (err) {
                console.warn('got modify error');
            }
        );
    });
};

Colibri.prototype.setRemoteDescription = function(session, elem, desctype) {
    var participant = this.peers.indexOf(session.peerjid);
    console.log('Colibri.setRemoteDescription from', session.peerjid, participant);
    var ob = this;
    var remoteSDP = new SDP('');
    var tmp;
    var channel;
    remoteSDP.fromJingle(elem);

    // ACT 1: change allocation on bridge
    this.updateChannel(remoteSDP, participant);

    // ACT 2: tell anyone else about the new SSRCs
    this.sendSSRCUpdate(remoteSDP, session.peerjid, true);

    // ACT 3: note the SSRCs
    this.remotessrc[session.peerjid] = [];
    for (channel = 0; channel < this.channels[participant].length; channel++) {
        this.remotessrc[session.peerjid][channel] = SDPUtil.find_lines(remoteSDP.media[channel], 'a=ssrc:').join('\r\n') + '\r\n';
    }

    // ACT 4: add new a=ssrc lines to local remotedescription
    for (channel = 0; channel < this.channels[participant].length; channel++) {
        if (!this.addssrc[channel]) this.addssrc[channel] = '';
        this.addssrc[channel] += SDPUtil.find_lines(remoteSDP.media[channel], 'a=ssrc:').join('\r\n') + '\r\n';
    }
    this.modifySources();
};

// relay ice candidates to bridge using trickle
Colibri.prototype.addIceCandidate = function (session, elem) {
    var ob = this;
    var participant = this.peers.indexOf(session.peerjid);
    console.log('change transport allocation for', this.confid, session.peerjid, participant);
    var change = $iq({to: this.bridgejid, type: 'set'});
    change.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: this.confid});
    $(elem).each(function() {
        var name = $(this).attr('name');
        var channel = name == 'audio' ? 0 :  1;

        change.c('content', {name: name});
        change.c('channel', {id: $(ob.channels[participant][channel]).attr('id'), initiator: 'true'});
        $(this).find('>transport').each(function() {
            change.c('transport', {
                ufrag: $(this).attr('ufrag'),
                pwd: $(this).attr('pwd'),
                xmlns: $(this).attr('xmlns')
            });

            $(this).find('>candidate').each(function () {
                /* not yet
                if (this.getAttribute('protocol') == 'tcp' && this.getAttribute('port') == 0) {
                    // chrome generates TCP candidates with port 0
                    return;
                }
                */
                var line = SDPUtil.candidateFromJingle(this);
                change.c('candidate', SDPUtil.candidateToJingle(line)).up();
            });
            change.up(); // end of transport
        });
        change.up(); // end of channel
        change.up(); // end of content
    });
    // FIXME: need to check if there is at least one candidate when filtering TCP ones
    this.connection.sendIQ(change,
        function (res) {
            console.log('got result');
        },
        function (err) {
            console.warn('got error');
        }
    );
};

// send our own candidate to the bridge
Colibri.prototype.sendIceCandidate = function(candidate) {
    //console.log('candidate', candidate);
    if (!candidate) {
        console.log('end of candidates');
        return;
    }
    var mycands = $iq({to: this.bridgejid, type: 'set'});
    mycands.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri', id: this.confid});
    mycands.c('content', {name: candidate.sdpMid });
    mycands.c('channel', {id: $(this.mychannel[candidate.sdpMLineIndex]).attr('id'), initiator: 'true'});
    mycands.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});
    tmp = SDPUtil.candidateToJingle(candidate.candidate);
    mycands.c('candidate', tmp).up();
    this.connection.sendIQ(mycands,
        function (res) {
            console.log('got result');
        },
        function (err) {
            console.warn('got error');
        }
    );
};

Colibri.prototype.terminate = function (session, reason) {
    console.log('remote session terminated from', session.peerjid);
    var participant = this.peers.indexOf(session.peerjid);
    if (!this.remotessrc[session.peerjid] || participant == -1) {
        return;
    }
    console.log('remote ssrcs:', this.remotessrc[session.peerjid]);
    var ssrcs = this.remotessrc[session.peerjid];
    for (var i = 0; i < ssrcs.length; i++) {
        if (!this.removessrc[i]) this.removessrc[i] = '';
        this.removessrc[i] += ssrcs[i];
    }
    // remove from this.peers
    this.peers.splice(participant, 1);
    // and from channels -- this wil timeout on the bridge
    // FIXME: this suggests keeping parallel arrays is wrong
    this.channels.splice(participant, 1);

    // tell everyone about the ssrcs to be removed
    var sdp = new SDP('');
    var localSDP = new SDP(this.peerconnection.localDescription.sdp);
    var contents = SDPUtil.find_lines(localSDP.raw, 'a=mid:').map(SDPUtil.parse_mid);
    for (var j = 0; j < ssrcs.length; j++) {
        sdp.media[j] = 'a=mid:' + contents[j] + '\r\n';
        sdp.media[j] += ssrcs[j];
        this.removessrc[j] += ssrcs[j];
    }
    this.sendSSRCUpdate(sdp, session.peerjid, false);

    delete this.remotessrc[session.peerjid];
    this.modifySources();
}

Colibri.prototype.modifySources = function() {
    var ob = this;
    if (!(this.addssrc.length || this.removessrc.length)) return;
    if (this.peerconnection.signalingState == 'closed') return;

    // FIXME: this is a big hack
    if (!(this.peerconnection.signalingState == 'stable' && this.peerconnection.iceConnectionState == 'connected')) {
        console.warn('modifySources not yet', this.peerconnection.signalingState, this.peerconnection.iceConnectionState);
        window.setTimeout(function() { ob.modifySources(); }, 250);
        this.wait = true;
        return;
    }
    if (this.wait) {
        window.setTimeout(function() { ob.modifySources(); }, 2500);
        this.wait = false;
        return;
    }
    var sdp = new SDP(this.peerconnection.remoteDescription.sdp);

    // mangle SDP a little
    // remove the msid-semantics from the remote description, if any
    sdp.removeSessionLines('a=msid-semantic:');

    // add sources
    this.addssrc.forEach(function(lines, idx) {
        sdp.media[idx] += lines;
    });
    this.addssrc = [];

    // remove sources
    this.removessrc.forEach(function(lines, idx) {
        lines = lines.split('\r\n');
        lines.pop(); // remove empty last element;
        lines.forEach(function(line) {
            sdp.media[idx] = sdp.media[idx].replace(line + '\r\n', '');
        });
    });
    this.removessrc = [];

    sdp.raw = sdp.session + sdp.media.join('');
    this.peerconnection.setRemoteDescription(
        new RTCSessionDescription({type: 'offer', sdp: sdp.raw }),
        function() {
            console.log('setModifiedRemoteDescription ok');
            ob.peerconnection.createAnswer(
                function(modifiedAnswer) {
                    console.log('modifiedAnswer created');
                    // FIXME: pushing down an answer while ice connection state 
                    // is still checking is bad...
                    console.log(ob.peerconnection.iceConnectionState);
                    ob.peerconnection.setLocalDescription(modifiedAnswer,
                        function() {
                            console.log('setModifiedLocalDescription ok');
                        },
                        function(error) {
                            console.log('setModifiedLocalDescription failed');
                        }
                    );
                },
                function(error) {
                    console.log('createModifiedAnswer failed');
                }
            );
        },
        function(error) {
            console.log('setModifiedRemoteDescription failed');
        }
    );
}


// A colibri session is similar to a jingle session, it just implements some things differently
// FIXME: inherit jinglesession, see https://github.com/legastero/Jingle-RTCPeerConnection/blob/master/index.js
function ColibriSession(me, sid, connection) {
    this.me = me;
    this.sid = sid;
    this.connection = connection;
    //this.peerconnection = null;
    //this.mychannel = null;
    //this.channels = null;
    this.peerjid = null;

    this.colibri = null;
};

ColibriSession.prototype.initiate = function (peerjid, isInitiator) {
    console.log('ColibriSession.initiate');
    this.peerjid = peerjid;
};

ColibriSession.prototype.sendOffer = function (offer) {
    console.log('ColibriSession.sendOffer');
};


ColibriSession.prototype.accept = function () {
    console.log('ColibriSession.accept');
};

ColibriSession.prototype.terminate = function (reason) {
    console.log('ColibriSession.terminate');
    this.colibri.terminate(this, reason);
};

ColibriSession.prototype.active = function () {
    console.log('ColibriSession.active');
};

ColibriSession.prototype.setRemoteDescription = function (elem, desctype) {
    console.log('ColibriSession.setRemoteDescription');
    this.colibri.setRemoteDescription(this, elem, desctype);
}

ColibriSession.prototype.addIceCandidate = function (elem) {
    this.colibri.addIceCandidate(this, elem);
}

ColibriSession.prototype.sendAnswer = function (sdp, provisional) {
    console.log('ColibriSession.sendAnswer');
};

ColibriSession.prototype.sendTerminate = function (reason, text) {
    console.log('ColibriSession.sendTerminate');
};