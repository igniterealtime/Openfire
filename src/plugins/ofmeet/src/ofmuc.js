
$(document).ready(function () 
{
	var conferenceList = '<datalist id="conference-list">'
		
	for (var i=0; i<config.conferences.length; i++)
	{	
		conferenceList = conferenceList + '<option value="' + Strophe.getNodeFromJid(config.conferences[i].jid) + '">' + config.conferences[i].name + '</option>'	
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
    shareApp: null,
    pdfPage: "1",
    recordingToken: null,
    isRecording: false,
    urls: [],
    bookmarks: [],
    appRunning: false,
    enableCursor: true,
    
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
	
	window.addEventListener('message', function (event) 
	{ 
		//console.log("addListener message ofmuc", event);
		
		if (!event.data) return;  
		if (event.data.type == 'ofmeetLoaded')  that.appReady();
		if (event.data.type == 'ofmeetSendMessage')  that.appMessage(event.data.msg);                           
	});	
	
    },
        
    statusChanged: function(status, condition)
    {
        var that = this;
            
	if(status == Strophe.Status.CONNECTED)
	{
		this.connection.sendIQ($iq({type: "get"}).c("query", {xmlns: "jabber:iq:private"}).c("storage", {xmlns: "storage:bookmarks"}).tree(), function(resp)
		{
			//console.log("get bookmarks", resp)
						
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

	    if (this.shareApp)	
	    {	
	    	// tell new participant my active application available to share	    	
		this.appShare("create", this.shareApp); 		
	    } 
	    
	    if (this.sharePDF)
	    {					
		this.pdfShare("create", this.sharePDF + "&control=false#" + this.pdfPage);
	    }            
            
        } else {

	    if (this.shareApp)
	    {					

	    } 
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
	var farparty = SettingsMenu.getDisplayName();
	
	if (!farparty) farparty = Strophe.getResourceFromJid(from); 	
	if (!that.roomJid) that.roomJid = Strophe.getBareJidFromJid(from);        
	
	$(msg).find('appshare').each(function() 
	{
		var action = $(this).attr('action');
		var url = $(this).attr('url');

		if (Strophe.getResourceFromJid(from) != Strophe.getResourceFromJid(that.connection.jid))
		{		
			that.handleAppShare(action, url, farparty);
		}
	});
	
	$(msg).find('pdfshare').each(function() 
	{
		var action = $(this).attr('action');
		var url = $(this).attr('url');		
		
		if (Strophe.getResourceFromJid(from) != Strophe.getResourceFromJid(that.connection.jid))
		{				
			that.handlePdfShare(action, url, farparty);	
		}
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
	});	
	
	return true;
    },
    
    appSave: function(callback) {
    	//console.log("ofmuc.appSave");
    	
    	var canSave = false;
    	
    	try {
    		canSave = this.appFrame && this.appFrame.contentWindow.OpenfireMeetings && this.appFrame.contentWindow.OpenfireMeetings.getContent;
    	} catch (e) { if (callback) callback()}
    	
	if (canSave)
	{        	
		var content = this.appFrame.contentWindow.OpenfireMeetings.getContent();
    		
    		if (content != null)
    		{
			var compressed = LZString.compressToBase64(content);   
			
			//console.log("ofmuc.appSave", this.shareApp, content, compressed);

			var ns = this.shareApp + "/" + this.roomJid;
			var iq = $iq({to: config.hosts.domain, type: 'set'});
			iq.c('query', {xmlns: "jabber:iq:private"}).c('ofmeet-application', {xmlns: ns}).t(compressed);

			this.connection.sendIQ(iq,

				function (resp) {
					if (callback) callback()
				},

				function (err) {			
					$.prompt("Application save...", {title: err, persistent: false});			
				}
			);
			
		} else if (callback) callback();
	
	} else if (callback) callback()      
    },   
    
    appPrint: function() {
    	//console.log("ofmuc.appPrint");

    	var canPrint = false;
    	
    	try {
    		canPrint = this.appFrame && this.appFrame.contentWindow.OpenfireMeetings && this.appFrame.contentWindow.OpenfireMeetings.getPrintContent;
    	} catch (e) {}
    	
	if (canPrint)
	{        	
		var content = this.appFrame.contentWindow.OpenfireMeetings.getPrintContent();   
		var printWin = window.open();
		printWin.document.write(content);
		printWin.print();
   		printWin.close();
	}
    },  
    
    appEnableCursor: function(flag) {
    	//console.log("ofmuc.appEnableCursor", flag)   
    	this.enableCursor = flag;
    },
    

    appReady: function() {
    	//console.log("ofmuc.appReady")   
    	
    	if (this.appRunning) return;
    	
        $.prompt.close();    	
        
	this.setPresentationVisible(true); 
        VideoLayout.resizeLargeVideoContainer();
        VideoLayout.positionLarge();
        VideoLayout.resizeThumbnails();  
        this.resize();
        
        // request for initial content
        
	if (this.shareApp)     // owner, get from server
	{
		var that = this;
		var ns = this.shareApp + "/" + this.roomJid;
        	var iq = $iq({to: config.hosts.domain, type: 'get'});
        	iq.c('query', {xmlns: "jabber:iq:private"}).c('ofmeet-application', {xmlns: ns});
        	
		this.connection.sendIQ(iq,

			function (resp) {			
				var response = "";

				$(resp).find('ofmeet-application').each(function() 
				{
					try {
						if (that.appFrame && that.appFrame.contentWindow.OpenfireMeetings && that.appFrame.contentWindow.OpenfireMeetings.setContent)
						{ 	
							var content = LZString.decompressFromBase64($(this).text());
							//console.log("ofmuc.appReady", that.shareApp, content);						
							that.appFrame.contentWindow.OpenfireMeetings.setContent(content);
						}
					} catch (e) {}
				});
			},

			function (err) {			
				$.prompt("Application data retrieve...", {title: err, persistent: false});			
			}
		); 
		
		this.appShare("create", this.shareApp);			
		
	} else { 		// request from peers	
		var msg = $msg({to: this.roomJid, type: 'groupchat'});
		msg.c('appshare', {xmlns: 'http://igniterealtime.org/protocol/appshare', action: 'message', url: '{"type": "joined"}'}).up();
		this.connection.send(msg);
	}
	
	this.appRunning = true;
    	this.appFrame.contentWindow.postMessage({ type: 'ofmeetEnableCursor', flag: this.enableCursor}, '*');	
    },
    
    appShare: function(action, url) {
    	//console.log("ofmuc.appShare", url, action)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('appshare', {xmlns: 'http://igniterealtime.org/protocol/appshare', action: action, url: url}).up();
        this.connection.send(msg);        
    },  

    appStart: function(url, owner) {
	//console.log("ofmuc.appStart", url, owner);
	
	this.enableCursor = true;
		
	$('#presentation').html('<iframe id="appViewer" src="' + url + "?room=" + Strophe.getNodeFromJid(this.roomJid) + "&user=" + SettingsMenu.getDisplayName() + '"></iframe>');
	this.appFrame = document.getElementById("appViewer");
		
	$.prompt("Please wait....",
	    {
		title: "Application Loader",
		persistent: false
	    }
	);	
    },

   appStop: function(url) {    
	//console.log("ofmuc.appStop", url);	

	this.setPresentationVisible(false);
	
	if (this.appFrame)
	{
		this.appFrame.contentWindow.location.href = "about:blank";
		this.appFrame = null;
		this.appRunning = false;
		
		$('#presentation').html('');		
	}
    },
    
    appMessage: function(msg) {

	//console.log("ofmuc.appMessage", msg);
	
	if (this.appFrame)
	{
		this.appShare("message", JSON.stringify(msg));
	}        
    },    

    handleAppShare: function (action, url, from)
    {
	//console.log("ofmuc.handleAppShare", url, action);
	
	if (this.shareApp == null)
	{
		if (this.appFrame == null) 
		{
			if (action == "create") this.appStart(url, false);		
		
		} else {
			
			if (action == "destroy") this.appStop(url);	
		}
	}
	
	if (this.appFrame && this.appFrame.contentWindow)
	{
		if (this.enableCursor) this.appFrame.contentWindow.postMessage({ type: 'ofmeetSetMessage', json: url, from: from}, '*');
		
		try {
			if (this.appFrame.contentWindow.OpenfireMeetings && this.appFrame.contentWindow.OpenfireMeetings.handleAppMessage && action == "message")
			{
				this.appFrame.contentWindow.OpenfireMeetings.handleAppMessage(url, from);
			}
		} catch (e) { }		
	}
    },

    openAppsDialog: function() {
	//console.log("ofmuc.openAppsDialog"); 
	var that = this;
	var canPrint = false;
	var canSave = false;
	
	try {
		canPrint = this.appFrame && this.appFrame.contentWindow.OpenfireMeetings && this.appFrame.contentWindow.OpenfireMeetings.getPrintContent;
		canSave = this.appFrame && this.appFrame.contentWindow.OpenfireMeetings && this.appFrame.contentWindow.OpenfireMeetings.setContent;
	} catch (e) {}
	
	var removeButtons = { "Remove": 1};
	var printButtons = { "Ok": 1};
	
	if (canPrint)
	{
		removeButtons["Print"] = 2;
		printButtons["Print"] = 2;		
	}
	
	if (canSave)
	{
		removeButtons["Save"] = 3;	
	}	
	
	
	if (this.shareApp) 
	{
	        if (this.isPresentationVisible() == false)
	        {
	        	this.setPresentationVisible(true);
	        
	        } else {
	        
			$.prompt("Are you sure you would like to remove your shared application?",
				{
				title: "Remove application sharing",
				buttons: removeButtons,
				defaultButton: 1,
				submit: function(e,v,m,f)
				{
					if(v==1)
					{
						that.appSave(function()
						{
							that.appShare("destroy", that.shareApp);
							that.appStop(that.shareApp);
							that.shareApp = null;
						});						
						
					} 
					else if(v==3)
					{
						that.appSave();
					}
					else if(v==2)
					{
						that.appPrint();
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
			$.prompt("Another participant is already sharing an application, presentation or document. This conference allows only one application, presentation or document at a time.",
				 {
				 f: "Share an application",
				 buttons: printButtons,
				 defaultButton: 0,
				 submit: function(e,v,m,f)
				 {
					if(v==1)
					{
				    		//$.prompt.close();
					} 
					else if(v==2)
					{
						that.appPrint();
					}				    
				 }
			});
		}
	}
	else {
	    	var appsList = '<select id="appName"><option value="/ofmeet/apps/woot">Collaborative Editing</option><option value="/ofmeet/apps/drawing">Collaborative Drawing</option><option value="/ofmeet/apps/scrumblr">Post-It Scrum Board</option>'
	    	
	    	for (var i=0; i<that.urls.length; i++)
	    	{
	    		if (that.urls[i].url.indexOf(".pdf") == -1 && that.urls[i].url.indexOf("mrtp:") == -1 )
	    		{
	    			appsList = appsList + '<option value="' + that.urls[i].url + '">' + that.urls[i].name + '</option>'
	    		}
	    	}
	    	appsList = appsList + '</select>'
	    	
		$.prompt('<h2>Are you sure you would like to share an application?</h2>' + appsList,
		{
			title: "Share an application",
			persistent: false,
			buttons: { "Share": true , "Cancel": false},
			defaultButton: 1,     
			loaded: function(event) {
				//document.getElementById('appName').select();
			},			
			submit: function(e,v,m,f) 
			{
				if(v)
				{
					that.shareApp = document.getElementById('appName').value;

					if (that.shareApp)
					{											
						setTimeout(function()
						{					
							that.appStart(that.shareApp, true);						
						}, 500);													
					}
				}					 
			}
		});    
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
    
    pdfShare: function(action, url) {
    	//console.log("ofmuc.pdfShare", url, action)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('pdfshare', {xmlns: 'http://igniterealtime.org/protocol/pdfshare', action: action, url: url}).up();
        this.connection.send(msg);        
    },
    
    pdfStart: function(url) {
	//console.log("ofmuc.pdfStart", url);
	
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
	//console.log("ofmuc.pdfStop", url);	

	this.setPresentationVisible(false);
	
	if (this.appFrame)
	{
		this.appFrame.contentWindow.location.href = "about:blank";
		this.appFrame = null;
		
		$('#presentation').html('');		
	}
    },
    
    pfdGoto: function(page) {
	//console.log("ofmuc.pfdGoto", page);
	
	this.pdfPage = page;
	
	if (this.sharePDF != null)
	{
		this.pdfShare("goto", this.sharePDF + "#" + page);
	}
    },
    
    pfdMessage: function(msg) {

	//console.log("pfdMessage", msg);
	
	if (this.appFrame)
	{
		this.pdfShare("message", JSON.stringify(msg));
	}        
    },

    handlePdfShare: function (action, url, from)
    {
	//console.log("local handlePdfShare", url, action, from);
	
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
	
	if (this.appFrame && this.appFrame.contentWindow.handlePdfShare && action == "message")
	{
		this.appFrame.contentWindow.handlePdfShare(url, from);
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
				    //$.prompt.close();
				 }
			});
		}
	    }
	    else {
	    
	    	var urlsList = '<datalist id="urls-list">'
	    	
	    	for (var i=0; i<that.urls.length; i++)
	    	{
	    		if (that.urls[i].url.indexOf(".pdf") > -1 ) urlsList = urlsList + '<option value="' + that.urls[i].url + '">' + that.urls[i].name + '</option>'
	    	}
	    	urlsList = urlsList + '</datalist>'
	    	
		$.prompt('<h2>Share a Presentation</h2> <br> Full URL to a public PDF:<input id="pdfiUrl" type="text" list="urls-list" autofocus >' + urlsList,
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
                    ToolbarToggler.dockToolbar(false);
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
                        ToolbarToggler.dockToolbar(true);
                    });
                });
            }
        }
    },
    
    isPresentationVisible: function () {
        return ($('#presentation>iframe') != null && $('#presentation>iframe').css('opacity') == 1);
    },

    avatarShare: function(avatar) {
    	//console.log("ofmuc.avatarShare", avatar)
        var msg = $msg({to: this.roomJid, type: 'groupchat'});
        msg.c('avatarshare', {xmlns: 'http://igniterealtime.org/protocol/avatarshare'}).t(avatar).up();
        this.connection.send(msg);        
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

