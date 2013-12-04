Strophe.addConnectionPlugin('emuc', {
    connection: null,
    roomjid: null,
    myroomjid: null,
    list_members: [],
    isOwner: false,
    mucDomain: config.hosts.muc,
    
    init: function (conn) {
        this.connection = conn;
    },
    setDomain: function(domain) {
        this.mucDomain = domain;
    },
    doJoin: function () {
        var roomnode = urlParam("r");			// BAO
	
        if (!roomnode) {
            roomnode = Math.random().toString(36).substr(2, 20);
            window.history.pushState('VideoChat', 'Room: ' + roomnode, window.location.pathname + "?r=" + roomnode);
        }
        
        if (this.roomjid == null) {
            this.roomjid = roomnode + '@' + this.mucDomain;
        }

        // muc stuff
        this.connection.addHandler(this.onPresence.bind(this), null, 'presence', null, null, this.roomjid, {matchBare: true});
        this.connection.addHandler(this.onPresenceUnavailable.bind(this), null, 'presence', 'unavailable', null, this.roomjid, {matchBare: true});
        this.connection.addHandler(this.onPresenceError.bind(this), null, 'presence', 'error', null, this.roomjid, {matchBare: true});

        this.connection.addHandler(this.onMessage.bind(this), null, 'message', null, null, this.roomjid, {matchBare: true});

        if (config.useNicks) {
            var nick = window.prompt('Your nickname (optional)');
            if (nick) {
                this.myroomjid = this.roomjid + '/' + nick;
            } else {
                this.myroomjid = this.roomjid + '/' + Strophe.getNodeFromJid(this.connection.jid);
            }
            this.connection.send($pres({to: this.myroomjid }).c('x', {xmlns: 'http://jabber.org/protocol/muc'}));
        } else {
            this.myroomjid = this.roomjid + '/' + Strophe.getNodeFromJid(this.connection.jid);
            console.log('joining', this.roomjid);
            this.connection.send($pres({to: this.myroomjid }).c('x', {xmlns: 'http://jabber.org/protocol/muc'}));
        }
    },
    onPresence: function (pres) {
        var from = pres.getAttribute('from'),
            type = pres.getAttribute('type');
        if (type != null) {
            return true;
        }
        if ($(pres).find('>x[xmlns="http://jabber.org/protocol/muc#user"]>status[code="201"]').length) {
            // http://xmpp.org/extensions/xep-0045.html#createroom-instant
            this.isOwner = true;
            var create = $iq({type: 'set', to: this.roomjid})
                    .c('query', {xmlns: 'http://jabber.org/protocol/muc#owner'})
                    .c('x', {xmlns: 'jabber:x:data', type: 'submit'});
            this.connection.send(create); // fire away
        }
        if (from == this.myroomjid) {
            this.onJoinComplete();
        } else if (this.list_members.indexOf(from) == -1) {
            // new participant
            this.list_members.push(from);
            // FIXME: belongs into an event so we can separate emuc and colibri
            if (master !== null) {
                // FIXME: this should prepare the video
                if (master.confid === null) {
                    console.log('make new conference with', from);
                    master.makeConference(this.list_members);
                } else {
                    console.log('invite', from, 'into conference');
                    master.addNewParticipant(from);
                }
            }
        } else {
            console.log('presence change from', from);
        }
        return true;
    },
    onPresenceUnavailable: function (pres) {
        // FIXME: first part doesn't belong into EMUC
        // FIXME: this should actually hide the video already for a nicer UX
        this.connection.jingle.terminateByJid($(pres).attr('from'));
        /*
        if (Object.keys(this.connection.jingle.sessions).length == 0) {
            console.log('everyone left');
        }
        */

        for (var i = 0; i < this.list_members.length; i++) {
            if (this.list_members[i] == $(pres).attr('from')) {
                this.list_members.splice(i, 1);
                break;
            }
        }
        if (this.list_members.length == 0) {
            console.log('everyone left');
            if (master !== null) {
                if (master.peerconnection !== null) master.peerconnection.close();
                master = new Colibri(connection, config.hosts.bridge);
            }
        }
        return true;
    },
    onPresenceError: function(pres) {
        var ob = this;
        if ($(pres).find('>error[type="auth"]>not-authorized[xmlns="urn:ietf:params:xml:ns:xmpp-stanzas"]').length) {
            window.setTimeout(function() {
                var given = window.prompt('Password required');
                if (given != null) {
                    ob.connection.send($pres({to: ob.myroomjid }).c('x', {xmlns: 'http://jabber.org/protocol/muc'}).c('password').t(given));
                } else {
                    // user aborted
                }
            }, 50);
        } else {
            console.warn('onPresError ', pres);
        }
        return true;
    },
    onJoinComplete: function() {
        console.log('onJoinComplete');
        $('#roomurl').text(window.location.href);
        $('#header').css('visibility', 'visible');
        if (this.list_members.length < 1) {
            // FIXME: belongs into an event so we can separate emuc and colibri
            master = new Colibri(connection, config.hosts.bridge);
            return;
        }
    },
    sendMessage: function(body) {
        msg = $msg({to: this.roomjid, type: 'groupchat'});
        msg.c('body', body);
        this.connection.send(msg);
    },
    onMessage: function (msg) {
        var txt = $(msg).find('>body').text();
        // TODO: <subject/>
        if (txt) {
            //console.log('chat', Strophe.getResourceFromJid($(msg).attr('from')), txt);
        }
        return true;
    },
    lockRoom: function(key) {
        //http://xmpp.org/extensions/xep-0045.html#roomconfig
        var ob = this;
        this.connection.sendIQ($iq({to:this.roomjid, type:'get'}).c('query', {xmlns:'http://jabber.org/protocol/muc#owner'}),
            function(res) {
                if ($(res).find('>query>x[xmlns="jabber:x:data"]>field[var="muc#roomconfig_roomsecret"]').length) {
                    var formsubmit = $iq({to:ob.roomjid, type:'set'}).c('query', {xmlns:'http://jabber.org/protocol/muc#owner'});
                    formsubmit.c('x', {xmlns: 'jabber:x:data', type: 'submit'});
                    formsubmit.c('field', {'var': 'FORM_TYPE'}).c('value').t('http://jabber.org/protocol/muc#roomconfig').up().up();
                    formsubmit.c('field', {'var': 'muc#roomconfig_roomsecret'}).c('value').t(key).up().up();
                    // FIXME: is muc#roomconfig_passwordprotectedroom required?
                    this.connection.sendIQ(formsubmit,
                        function(res) {
                            console.log('set room password');
                        },
                        function(err) {
                            console.warn('setting password failed', err);
                        }
                    );
                } else {
                    console.warn('room passwords not supported');
                }
            },
            function(err) {
                console.warn('setting password failed', err);
            }
        );
    }
});

$(window).bind('beforeunload', function() {
    if (connection && connection.connected) {
	connection.disconnect();
    }
})