/**
 * Base class for ColibriFocus and JingleSession.
 * @param connection Strophe connection object
 * @param sid my session identifier(resource)
 * @constructor
 */
function SessionBase(connection, sid) {
    this.connection = connection;
    this.sid = sid;

    /**
     * The indicator which determines whether the (local) video has been muted
     * in response to a user command in contrast to an automatic decision made
     * by the application logic.
     */
    this.videoMuteByUser = false;
}


SessionBase.prototype.modifySources = function (successCallback) {
    var self = this;
    if(this.peerconnection)
        this.peerconnection.modifySources(function(){
            $(document).trigger('setLocalDescription.jingle', [self.sid]);
            if(successCallback) {
                successCallback();
            }
        });
};

SessionBase.prototype.addSource = function (elem, fromJid) {

    var self = this;
    // FIXME: dirty waiting
    if (!this.peerconnection.localDescription)
    {
        console.warn("addSource - localDescription not ready yet")
        setTimeout(function()
            {
                self.addSource(elem, fromJid);
            },
            200
        );
        return;
    }

    this.peerconnection.addSource(elem);

    this.modifySources();
};

SessionBase.prototype.removeSource = function (elem, fromJid) {

    var self = this;
    // FIXME: dirty waiting
    if (!this.peerconnection.localDescription)
    {
        console.warn("removeSource - localDescription not ready yet")
        setTimeout(function()
            {
                self.removeSource(elem, fromJid);
            },
            200
        );
        return;
    }

    this.peerconnection.removeSource(elem);

    this.modifySources();
};

/**
 * Switches video streams.
 * @param new_stream new stream that will be used as video of this session.
 * @param oldStream old video stream of this session.
 * @param success_callback callback executed after successful stream switch.
 */
SessionBase.prototype.switchStreams = function (new_stream, oldStream, success_callback) {

    var self = this;

    // Stop the stream to trigger onended event for old stream
    oldStream.stop();

    // Remember SDP to figure out added/removed SSRCs
    var oldSdp = null;
    if(self.peerconnection) {
        if(self.peerconnection.localDescription) {
            oldSdp = new SDP(self.peerconnection.localDescription.sdp);
        }
        self.peerconnection.removeStream(oldStream, true);
        self.peerconnection.addStream(new_stream);
    }

    self.connection.jingle.localVideo = new_stream;

    self.connection.jingle.localStreams = [];

    //in firefox we have only one stream object
    if(self.connection.jingle.localAudio != self.connection.jingle.localVideo)
        self.connection.jingle.localStreams.push(self.connection.jingle.localAudio);
    self.connection.jingle.localStreams.push(self.connection.jingle.localVideo);

    // Conference is not active
    if(!oldSdp || !self.peerconnection) {
        success_callback();
        return;
    }

    self.peerconnection.switchstreams = true;
    self.modifySources(function() {
        console.log('modify sources done');

        success_callback();

        var newSdp = new SDP(self.peerconnection.localDescription.sdp);
        console.log("SDPs", oldSdp, newSdp);
        self.notifyMySSRCUpdate(oldSdp, newSdp);
    });
};

/**
 * Figures out added/removed ssrcs and send update IQs.
 * @param old_sdp SDP object for old description.
 * @param new_sdp SDP object for new description.
 */
SessionBase.prototype.notifyMySSRCUpdate = function (old_sdp, new_sdp) {

    var old_media = old_sdp.getMediaSsrcMap();
    var new_media = new_sdp.getMediaSsrcMap();
    //console.log("old/new medias: ", old_media, new_media);

    var toAdd = old_sdp.getNewMedia(new_sdp);
    var toRemove = new_sdp.getNewMedia(old_sdp);
    //console.log("to add", toAdd);
    //console.log("to remove", toRemove);
    if(Object.keys(toRemove).length > 0){
        this.sendSSRCUpdate(toRemove, null, false);
    }
    if(Object.keys(toAdd).length > 0){
        this.sendSSRCUpdate(toAdd, null, true);
    }
};

/**
 * Empty method that does nothing by default. It should send SSRC update IQs to session participants.
 * @param sdpMediaSsrcs array of
 * @param fromJid
 * @param isAdd
 */
SessionBase.prototype.sendSSRCUpdate = function(sdpMediaSsrcs, fromJid, isAdd) {
    //FIXME: put default implementation here(maybe from JingleSession?)
}

/**
 * Sends SSRC update IQ.
 * @param sdpMediaSsrcs SSRCs map obtained from SDP.getNewMedia. Cntains SSRCs to add/remove.
 * @param sid session identifier that will be put into the IQ.
 * @param initiator initiator identifier.
 * @param toJid destination Jid
 * @param isAdd indicates if this is remove or add operation.
 */
SessionBase.prototype.sendSSRCUpdateIq = function(sdpMediaSsrcs, sid, initiator, toJid, isAdd) {

    var self = this;
    var modify = $iq({to: toJid, type: 'set'})
        .c('jingle', {
            xmlns: 'urn:xmpp:jingle:1',
            action: isAdd ? 'source-add' : 'source-remove',
            initiator: initiator,
            sid: sid
        }
    );
    // FIXME: only announce video ssrcs since we mix audio and dont need
    //      the audio ssrcs therefore
    var modified = false;
    Object.keys(sdpMediaSsrcs).forEach(function(channelNum){
        modified = true;
        var channel = sdpMediaSsrcs[channelNum];
        modify.c('content', {name: channel.mediaType});

        modify.c('description', {xmlns:'urn:xmpp:jingle:apps:rtp:1', media: channel.mediaType});
        // FIXME: not completly sure this operates on blocks and / or handles different ssrcs correctly
        // generate sources from lines
        Object.keys(channel.ssrcs).forEach(function(ssrcNum) {
            var mediaSsrc = channel.ssrcs[ssrcNum];
            modify.c('source', { xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });
            modify.attrs({ssrc: mediaSsrc.ssrc});
            // iterate over ssrc lines
            mediaSsrc.lines.forEach(function (line) {
                var idx = line.indexOf(' ');
                var kv = line.substr(idx + 1);
                modify.c('parameter');
                if (kv.indexOf(':') == -1) {
                    modify.attrs({ name: kv });
                } else {
                    modify.attrs({ name: kv.split(':', 2)[0] });
                    modify.attrs({ value: kv.split(':', 2)[1] });
                }
                modify.up(); // end of parameter
            });
            modify.up(); // end of source
        });

        // generate source groups from lines
        channel.ssrcGroups.forEach(function(ssrcGroup) {
            if (ssrcGroup.ssrcs.length != 0) {

                modify.c('ssrc-group', {
                    semantics: ssrcGroup.semantics,
                    xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0'
                });

                ssrcGroup.ssrcs.forEach(function (ssrc) {
                    modify.c('source', { ssrc: ssrc })
                        .up(); // end of source
                });
                modify.up(); // end of ssrc-group
            }
        });

        modify.up(); // end of description
        modify.up(); // end of content
    });
    if (modified) {
        self.connection.sendIQ(modify,
            function (res) {
                console.info('got modify result', res);
            },
            function (err) {
                console.error('got modify error', err);
            }
        );
    } else {
        console.log('modification not necessary');
    }
};

/**
 * Determines whether the (local) video is mute i.e. all video tracks are
 * disabled.
 *
 * @return <tt>true</tt> if the (local) video is mute i.e. all video tracks are
 * disabled; otherwise, <tt>false</tt>
 */
SessionBase.prototype.isVideoMute = function () {
    var tracks = connection.jingle.localVideo.getVideoTracks();
    var mute = true;

    for (var i = 0; i < tracks.length; ++i) {
        if (tracks[i].enabled) {
            mute = false;
            break;
        }
    }
    return mute;
};

/**
 * Mutes/unmutes the (local) video i.e. enables/disables all video tracks.
 *
 * @param mute <tt>true</tt> to mute the (local) video i.e. to disable all video
 * tracks; otherwise, <tt>false</tt>
 * @param callback a function to be invoked with <tt>mute</tt> after all video
 * tracks have been enabled/disabled. The function may, optionally, return
 * another function which is to be invoked after the whole mute/unmute operation
 * has completed successfully.
 * @param options an object which specifies optional arguments such as the
 * <tt>boolean</tt> key <tt>byUser</tt> with default value <tt>true</tt> which
 * specifies whether the method was initiated in response to a user command (in
 * contrast to an automatic decision made by the application logic)
 */
SessionBase.prototype.setVideoMute = function (mute, callback, options) {
    var byUser;

    if (options) {
        byUser = options.byUser;
        if (typeof byUser === 'undefined') {
            byUser = true;
        } 
    } else {
        byUser = true;
    }
    // The user's command to mute the (local) video takes precedence over any
    // automatic decision made by the application logic.
    if (byUser) {
        this.videoMuteByUser = mute;
    } else if (this.videoMuteByUser) {
        return;
    }
    if (mute == this.isVideoMute())
    {
        // Even if no change occurs, the specified callback is to be executed.
        // The specified callback may, optionally, return a successCallback
        // which is to be executed as well.
        var successCallback = callback(mute);

        if (successCallback) {
            successCallback();
        }
    } else {
        var tracks = connection.jingle.localVideo.getVideoTracks();

        for (var i = 0; i < tracks.length; ++i) {
            tracks[i].enabled = !mute;
        }

        if (this.peerconnection) {
            this.peerconnection.hardMuteVideo(mute);
        }

        this.modifySources(callback(mute));
    }
};

// SDP-based mute by going recvonly/sendrecv
// FIXME: should probably black out the screen as well
SessionBase.prototype.toggleVideoMute = function (callback) {
    setVideoMute(isVideoMute(), callback);
};
