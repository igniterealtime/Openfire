
$(document).ready(function () 
{
	var conferenceList = '<datalist id="conference-list">'
		
	for (var i=0; i<config.conferences.length; i++)
	{	
		conferenceList = conferenceList + '<option value="' + Strophe.getNodeFromJid(config.conferences[i].jid) + '"/>'	
	}
	
	conferenceList = conferenceList + '</datalist>'
	
	$("body").append(conferenceList);
	$("#enter_room_field").attr("list", "conference-list");	
});


Strophe.addConnectionPlugin('ofmuc', {
    connection: null,
    roomJid: null,
    members: {},
    sharePDF: null,
    pdfPage: "1",
    recordingToken: null,
    isRecording: false,
    urls: [],
    bookmarks: [],
    
    init: function (conn) {
        this.connection = conn;
        this.connection.addHandler(this.onMessage.bind(this), null, 'message'); 
        this.connection.addHandler(this.onPresence.bind(this), null, 'presence');        
        this.connection.addHandler(this.onPresenceUnavailable.bind(this), null, 'presence', 'unavailable'); 
        this.connection.addHandler(this.onRayo.bind(this), 'urn:xmpp:rayo:1');       
        
        var that = this;
        
	$(window).resize(function () {
	   that.resize();
	}); 
	
    },
        
    statusChanged: function(status, condition)
    {
        var that = this;
            
	if(status == Strophe.Status.CONNECTED)
	{
		this.connection.sendIQ($iq({type: "get"}).c("query", {xmlns: "jabber:iq:private"}).c("storage", {xmlns: "storage:bookmarks"}).tree(), function(resp)
		{
			console.log("get bookmarks", resp)
						
			$(resp).find('conference').each(function() 
			{
				that.bookmarks.push({name: $(this).attr("name"), jid: $(this).attr("jid")});	
			})
			
			$(resp).find('url').each(function() 
			{
				that.urls.push({name: $(this).attr("name"), url: $(this).attr("url")});
			});
		});
	}
    },
    
            
    rayoAccept: function (confId, roomName)
    {
	    var self = this;
	    var req = $iq(
		{
		    type: 'set',
		    to: config.hosts.call_control
		}
	    );
	    req.c('accept',
		{
		    xmlns: 'urn:xmpp:rayo:1'
		});
	    req.c('header', {name: 'JvbRoomId', value: confId}).up();
	    req.c('header', {name: 'JvbRoomName', value: roomName}).up();
	    
	    this.connection.sendIQ(req,
	    
		function (result)
		{
		    //console.info('rayoAccept result ', result);
		},
		function (error)
		{
		    console.info('rayoAccept error ', error);
		}
	    );
	},
        
    resize: function() {
	if ($('#presentation>iframe')) {
	    $('#presentation>iframe').width(this.getPresentationWidth());
	    $('#presentation>iframe').height(this.getPresentationHeight());
	}    
    },
    
    getPresentationWidth: function() {
        var availableWidth = Util.getAvailableVideoWidth();
        var availableHeight = this.getPresentationHeight();

        var aspectRatio = 16.0 / 9.0;
        if (availableHeight < availableWidth / aspectRatio) {
            availableWidth = Math.floor(availableHeight * aspectRatio);
        }
        return availableWidth;
    },
    
    getPresentationHeight: function () {
        var remoteVideos = $('#remoteVideos');
        return window.innerHeight - remoteVideos.outerHeight();
    },    
        
    onPresence: function (pres) {
    	//console.log('ofmuc onPresence', $(pres))  
    	    	
        var from = pres.getAttribute('from');
        var type = pres.getAttribute('type');
        
        if (type != null) {
           return true;
        }
        
    	if (!this.roomJid || Strophe.getBareJidFromJid(from) != this.roomJid) return true;        

        var member = {};
        member.show = $(pres).find('>show').text();
        member.status = $(pres).find('>status').text();
        var tmp = $(pres).find('>x[xmlns="http://jabber.org/protocol/muc#user"]>item');
        member.affiliation = tmp.attr('affiliation');
        member.role = tmp.attr('role');

        if (from == this.roomJid) {
        
        } else if (this.members[from] === undefined) {
            this.members[from] = member;
    
            if (config.userAvatar && config.userAvatar != "null")
            {
            	this.avatarShare(config.userAvatar);
            }
            
	    if (this.sharePDF)
	    {					
		this.pdfShare("create", this.sharePDF + "&control=false#" + this.pdfPage);
	    }            
            
        } else {

        }
        return true;
    },

    onPresenceUnavailable: function (pres) {
    	//console.log('onPresenceUnavailable', $(pres));
    	
        var from = pres.getAttribute('from');
   	if (!this.roomJid) return true;  
        
        delete this.members[from];
        return true;
    },   
    
    onMessage: function (msg) {
    	//console.log('onMessage', $(msg))
    	var that = this;
    	var from = msg.getAttribute('from');
        
	$(msg).find('appshare').each(function() 
	{
		var action = $(this).attr('action');
		var url = $(this).attr('url');
		
		that.handleAppShare(action, url);	
	});
	
	$(msg).find('pdfshare').each(function() 
	{
		var action = $(this).attr('action');
		var url = $(this).attr('url');
		
		that.roomJid = Strophe.getBareJidFromJid(from)
		that.handlePdfShare(action, url);	
	});	
	
	$(msg).find('avatarshare').each(function() 
	{
		that.members[from].avatar = $(this).text();	
		Avatar.setUserAvatar(from);
	});
	
        return true;
    },
    
    onRayo: function (packet) 
    {
	//console.log("onRayo", packet);
	var from = $(packet).attr('from');
	
	var jid = null;
	var videoSpanId = null;
	var node = null;
	var button = $("#sipCallButton > a");

	$(packet).find('header').each(function() 
	{		
		var name = $(this).attr('name');
		var value = $(this).attr('value');
		
		//console.log("onRayo header", name, value);
		
		if (name == "caller_id")
		{	
			if (value.indexOf("@") > -1)
			{
				var callerId = value.substring(4); // remove sip:
				
				node = Strophe.getNodeFromJid(callerId);
				jid = callerId + "/" + node;
			} else {
				node = value;
				jid = node + "@" + config.hosts.domain + "/" + node
			}
			videoSpanId = 'participant_' + node;
		}

	});	
		
	$(packet).find('answered').each(function() 
	{	
		var callId = Strophe.getNodeFromJid(from); 
		
		//console.log("onRayo callid", callId, jid);
		
		if (jid)
		{
			VideoLayout.ensurePeerContainerExists(jid);	
			var container = document.getElementById(videoSpanId);
			
			if (container) 
			{	
			    	$(container).show();			
				$(container).attr("title", Strophe.getBareJidFromJid(jid));
			}
		}
		
		button.addClass("glow");
	});
	
	$(packet).find('hangup').each(function() 
	{	
		var callId = Strophe.getNodeFromJid(from); 
		
		//console.log("onRayo callid", callId, jid);	
		
		if (jid) 
		{
			var container = document.getElementById(videoSpanId);
			
			if (container) 
			{
			    VideoLayout.removeConnectionIndicator(jid);
			    // hide here, wait for video to close before removing
			    $(container).hide();
			    VideoLayout.resizeThumbnails();
			}
		}
		button.removeClass("glow");		
	});	
	
	return true;
    },

    handleAppShare: function (action, url)
    {
	//console.log("handleAppShare", url, action);
    },
    
    handlePdfShare: function (action, url)
    {
	//console.log("handlePdfShare", url, action);
	
	if (this.sharePDF == null)
	{
		if (this.appFrame == null) 
		{
			if (action == "create") this.pdfStart(url);		
		
		} else {

			if (action == "destroy") this.pdfStop(url);	
			if (action == "goto") this.appFrame.contentWindow.location.href = "/ofmeet/pdf/index.html?pdf=" + url;
		}
	}	
    },

    pdfReady: function() {
	this.setPresentationVisible(true); 
        VideoLayout.resizeLargeVideoContainer();
        VideoLayout.positionLarge();
        VideoLayout.resizeThumbnails();  
        this.resize();
        $.prompt.close();
    },
    
    appShare: function(action, url) {
    	//console.log("ofmuc.appShare", url, action)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('appshare', {xmlns: 'http://igniterealtime.org/protocol/appshare', action: action, url: url}).up();
        this.connection.send(msg);        
    },    
    pdfShare: function(action, url) {
    	//console.log("ofmuc.pdfShare", url, action)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('pdfshare', {xmlns: 'http://igniterealtime.org/protocol/pdfshare', action: action, url: url}).up();
        this.connection.send(msg);        
    },
    
    avatarShare: function(avatar) {
    	//console.log("ofmuc.avatarShare", avatar)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('avatarshare', {xmlns: 'http://igniterealtime.org/protocol/avatarshare'}).t(avatar).up();
        this.connection.send(msg);        
    },    

    pdfStart: function(url) {
	//console.log("pdfStart", url);
	
	$('#presentation').html('<iframe id="appViewer"></iframe>');
	
	this.appFrame = document.getElementById("appViewer");
	this.appFrame.contentWindow.location.href = "/ofmeet/pdf/index.html?pdf=" + url + "&room=" + Strophe.getNodeFromJid(this.roomJid);
	
        $.prompt("Please wait....",
            {
                title: "PDF Loader",
                persistent: false
            }
        );	
    },

    pdfStop: function(url) {    
	//console.log("pdfStop", url);	

	this.setPresentationVisible(false);
	
	if (this.appFrame)
	{
		this.appFrame.contentWindow.location.href = "about:blank";
		this.appFrame = null;
		
		$('#presentation').html('');		
	}
    },
    
    pfdGoto: function(page) {
	//console.log("pfdGoto", page);
	
	this.pdfPage = page;
	
	if (this.sharePDF != null)
	{
		this.pdfShare("goto", this.sharePDF + "#" + page);
	}
    },
    
    openPDFDialog: function() {
	//console.log("openPDFDialog");    	
    	    var that = this;
    	    
    	    //this.roomJid = connection.emuc.roomjid;
    	
	    if (that.sharePDF) 
	    {
	    
	        if (this.isPresentationVisible() == false)
	        {
	        	this.setPresentationVisible(true);
	        
	        } else {	
	        
			$.prompt("Are you sure you would like to remove your Presentation?",
				{
				title: "Remove PDF Presentation",
				buttons: { "Remove": true, "Cancel": false},
				defaultButton: 1,
				submit: function(e,v,m,f)
				{
					if(v)
					{
						that.pdfShare("destroy", that.sharePDF);
						that.pdfStop(that.sharePDF);
						that.sharePDF = null;	
					}
				}
			});
		}
	    }
	    else if (this.appFrame != null) {
	    
	        if (this.isPresentationVisible() == false)
	        {
	        	this.setPresentationVisible(true);
	        
	        } else {	    
			$.prompt("Another participant is already sharing an application, presentation or document. This conference allows only one presentation or document at a time.",
				 {
				 f: "Share a PDF Presentation",
				 buttons: { "Ok": true},
				 defaultButton: 0,
				 submit: function(e,v,m,f)
				 {
				    $.prompt.close();
				 }
			});
		}
	    }
	    else {
	    
	    	urlsList = '<datalist id="urls-list">'
	    	
	    	for (var i=0; i<that.urls.length; i++)
	    	{
	    		urlsList = urlsList + '<option value="' + that.urls[i].url + '"/>'
	    	}
	    	urlsList = urlsList + '</datalist>'
	    	
		$.prompt('<h2>Share a Presentation</h2><input id="pdfiUrl" type="text" list="urls-list" autofocus >' + urlsList,
		{
			title: "Share a PDF Presentation",
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
					that.sharePDF = document.getElementById('pdfiUrl').value;

					if (that.sharePDF)
					{
						setTimeout(function()
						{
							that.pdfStart(that.sharePDF  + "&control=true");
							that.pdfShare("create", that.sharePDF  + "&control=false");
						}, 500);
					}
				}					 
			}
		});    
	    }
    },

    setPresentationVisible: function(visible) {    
        if (visible) {
            // Trigger the video.selected event to indicate a change in the
            // large video.
            $(document).trigger("video.selected", [true]);

            $('#largeVideo').fadeOut(300, function () {
                VideoLayout.setLargeVideoVisible(false);
                $('#presentation>iframe').fadeIn(300, function() {
                    $('#presentation>iframe').css({opacity:'1'});
                    ToolbarToggler.dockToolbar(true);
                });
            });
        }
        else {
            if ($('#presentation>iframe').css('opacity') == '1') {
                $('#presentation>iframe').fadeOut(300, function () {
                    $('#presentation>iframe').css({opacity:'0'});
                    $('#reloadPresentation').css({display:'none'});
                    $('#largeVideo').fadeIn(300, function() {
                        VideoLayout.setLargeVideoVisible(true);
                        ToolbarToggler.dockToolbar(false);
                    });
                });
            }
        }
    },
    
    isPresentationVisible: function () {
        return ($('#presentation>iframe') != null && $('#presentation>iframe').css('opacity') == 1);
    },
    
    toggleRecording: function () 
    {
    	var that = this;
    	
	if (!this.recordingToken)
	{		
		$.prompt('<h2>Enter recording token</h2><input id="recordingToken" type="text" placeholder="token" autofocus>',
		{
			title: "Meeting Recording",
			buttons: { "Record": true, "Cancel": false},
			defaultButton: 1,
			loaded: function(event) {
				document.getElementById('recordingToken').focus();
			},			
			submit: function(e,v,m,f)
			{
				if(v)
				{
				    var token = document.getElementById('recordingToken');

				    if (token.value) {
					that.recordingToken = Util.escapeHtml(token.value);
					that.toggleRecording();
				    }	
				}
			}
		});		

		return;
	}

	var req = $iq({type: 'set', to: config.hosts.call_control});
	
	req.c('record',	{xmlns: 'urn:xmpp:rayo:record:1'});
	req.c('hint', 	{name: 'JvbToken', value: this.recordingToken}).up();
	req.c('hint', 	{name: 'JvbState', value: this.isRecording ? "false" : "true"}).up();
	req.c('hint', 	{name: 'JvbRoomName', value: this.roomJid}).up();
	    
	this.connection.sendIQ(req,

		function (result)
		{
		    console.info('toggleRecording result ', result);
		    that.isRecording = !that.isRecording;
		    Toolbar.setRecordingButtonState(that.isRecording);		    
		},
		function (error)
		{
		    console.info('toggleRecording error ', error);
		    Toolbar.setRecordingButtonState(false);
		    that.isRecording = false;		    
		}
	);	    
    }    
    
});

