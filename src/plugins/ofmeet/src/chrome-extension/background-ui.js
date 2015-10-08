
var ChromeUi = (function(self) { 

	var rootWindow = null;
	var windows = {}
	var windowClosed = true;
	var callbacks = {}
	var videoWin = null;
	var telephoneWin = null;
	var optionUrl = chrome.extension.getURL('options/index.html')
	var ports = {};	
	var speakerConnected = false;
	
	self.createRootWindow = function()
	{
		chrome.windows.create({url: chrome.extension.getURL('root.html'),
			    focused: true,
			    type: "panel"
		}, function (win) {
		
			rootWindow = win
			chrome.windows.update(win.id, {width: 320, height: 900});
			console.log("createRootWindow", rootWindow)
			windowClosed = false;
		});
	}

	self.createNodeWindow = function(id, create, update)
	{
		console.log('createNodeWindow', id);	
		
		var win = windows[id]

		if (win)
		{
			chrome.windows.update(win.window.id, update);
		
		} else {
		
			chrome.windows.create(create, function (win) 
			{
				chrome.windows.update(win.id, update);
				
				var newWin = {window: win, id: id};
				windows[id] = newWin
				windows[win.id] = newWin;
			});
		}
	}
	
	self.getRootWindow = function()
	{
		return rootWindow;
	}

	self.getNodeWindow = function(id)
	{
		return windows[id];
	}
	
	self.destroyNodeWindow = function(id)
	{	
		console.log('destroyNodeWindow', id);	
		try {
			var win = windows[id]
			
			if (win)
			{
				chrome.windows.remove(win.window.id);
				this.removeNodeWindow(win.window.id);
			}
	
		} catch (e) {}	
	}
	
	self.removeNodeWindow = function(id)
	{	
		console.log('removeNodeWindow', id);	
		try {
			var win = windows[id]
			console.log('removeNodeWindow', win);	
			
			if (win)
			{
				windows[win.window.id] = null;
				windows[win.id] = null;					
			}
	
		} catch (e) {}	
	}	
	
	self.drawAttention = function(win)
	{
		if (win)
		{ 
			chrome.windows.update(win.id, {drawAttention: true});
		}
	}

	self.destroyRootWindow = function()
	{		
		try {
			console.log('destroyRootWindow');		
			
			if (!windowClosed) chrome.windows.remove(rootWindow.id);
			
			var items = Object.getOwnPropertyNames(windows)

			for (var i=0; i< items.length; i++)
			{
				try {			
					self.destroyNodeWindow(items[i]);
				} catch (e) {}					
			}
			
		} catch (e) {}
		
		windowClosed = true;		
	}	

	/**
	 *	buttons [{title: "accept", iconUrl: "accept.png"}]
	 * 	items [{ title: "Item1", message: "This is item 1."}]
	 *	progress 0 - 100
	 */
	 
	self.notifyText = function(message, context, iconUrl, buttons, callback)
	{			
		var opt = {
		  type: "basic",
		  title: "TraderLynk",		  
		  iconUrl: iconUrl ? iconUrl : "tl_logo2.png",
		  
		  message: message,
		  buttons: buttons,		  
		  contextMessage: context		  
		}
		var id = Math.random().toString(36).substr(2,9); 
				
		chrome.notifications.create(id, opt, function(notificationId)
		{
			if (callback) callbacks[notificationId] = callback;
		});
	};
	
	self.notifyImage = function(message, context, imageUrl, buttons, callback)
	{			
		var opt = {
		  type: "image",
		  title: "TraderLynk",		  
		  iconUrl: "tl_logo2.png",
		  
		  message: message,	
		  buttons: buttons,		  
		  contextMessage: context,
		  imageUrl: imageUrl
		}
		var id = Math.random().toString(36).substr(2,9); 
		
		chrome.notifications.create(id, opt, function(notificationId)
		{
			if (callback) callbacks[notificationId] = callback;
		});
	};		
	
	self.notifyProgress = function(message, context, progress, buttons, callback)
	{			
		var opt = {
		  type: "progress",
		  title: "TraderLynk",		  
		  iconUrl: "tl_logo2.png",
		  
		  message: message,	
		  buttons: buttons,		  
		  contextMessage: context,
		  progress: progress
		}
		var id = Math.random().toString(36).substr(2,9); 
		
		chrome.notifications.create(id, opt, function(notificationId)
		{
			if (callback) callbacks[notificationId] = callback;
		});
	};	
	
	
	self.notifyList = function(message, context, items, buttons, callback)
	{			
		var opt = {
		  type: "list",
		  title: "TraderLynk",		  
		  iconUrl: "tl_logo2.png",
		  
		  message: message,	
		  buttons: buttons,		  
		  contextMessage: context,
		  items: items
		}
		var id = Math.random().toString(36).substr(2,9); 
		
		chrome.notifications.create(id, opt, function(notificationId)
		{
			if (callback) callbacks[notificationId] = callback;
		});
	};

	self.closeVideoWindow = function()
	{
		if (videoWin != null)
		{
			chrome.windows.remove(videoWin.id);		
		}
	}
	
	self.openVideoWindow = function(roomId, jid, name, callback)
	{
		if (videoWin == null)
		{
			chrome.windows.create({url: chrome.extension.getURL('conversation.video.html?id='+roomId+'&jid='+jid+'&name='+name), focused: true, type: "popup", width: 800, height: 640}, function (win) 
			{
				videoWin = win;
				if (callback) callback(videoWin);
			});
			
		} else {
			chrome.windows.update(videoWin.id, {focused: true, width: 800, height: 640});		
		}
	};

	self.openTelephoneWindow = function(roomId, jid, name)
	{
		if (telephoneWin == null)
		{
			chrome.windows.create({url: chrome.extension.getURL('conversation.telephone.html?id='+roomId+'&jid='+jid+'&name='+name), focused: true, type: "panel", width: 320, height: 900}, function (win) 
			{
				telephoneWin = win;
			});
			
		} else {
			chrome.windows.update(telephoneWin.id, {focused: true, width: 320, height: 900});		
		}		
	};
	

	self.closeTelephoneWindow = function()
	{
		if (telephoneWin != null)
		{
			chrome.windows.remove(telephoneWin.id);		
		}
	}
	
	
	self.speakerConnect = function()
	{

	};


	self.speakerDisconnect = function()
	{
	
	};
	
	self.speakerOn = function(roomName)
	{
	
	};


	self.speakerOff = function(roomName)
	{
	
	};	

	self.speakerTalk = function(roomName)
	{
	
	};


	self.speakerUntalk = function(roomName)
	{
	
	};
	
	self.getOption = function(name)
	{
		var value = null;
		
		try {
			var key = window.localStorage[name];		
			if (key) value = JSON.parse(key);
		} catch (e) {
			console.error("getStoreSetting", e);
		}
		return value;
	};
	
	self.doOptions = function()
	{
		chrome.tabs.getAllInWindow(null, function(tabs)
		{
		    var option_tab = tabs.filter(function(t) { return t.url === optionUrl; });

		    if (option_tab.length)
		    {
			chrome.tabs.update(option_tab[0].id, {highlighted: true, active: true});

		    }else{

			chrome.tabs.create({url: optionUrl, active: true});
		    }
		});	
	}
	
	self.publishDesktop = function(room, callback, errorback)
	{
		chrome.desktopCapture.chooseDesktopMedia(["screen", "window"], function(streamId) 
		{					

		});
	}
	
	
	window.addEventListener("load", function() 
	{
		console.log('background-ui.js load event');
		
		//chrome.systemIndicator.enable();
		
		chrome.browserAction.onClicked.addListener(function()
		{
			ChromeUi.doOptions();	
		});		
		
		chrome.windows.onRemoved.addListener(function(win) 
		{
			console.log("closing window ", win);
			
			if (rootWindow && win == rootWindow.id)
			{				
				windowClosed = true;
				rootWindow = null;
				
				var items = Object.getOwnPropertyNames(windows)

				for (var i=0; i< items.length; i++)
				{
					try {			
						self.destroyNodeWindow(items[i]);
					} catch (e) {}					
				}
				
			} else if (videoWin && win == videoWin.id) {	
				videoWin = null;
				
			} else if (telephoneWin && win == telephoneWin.id) {	
				telephoneWin = null;				
			
			} else {
				self.removeNodeWindow(win);
			}
		});	
		
		chrome.notifications.onButtonClicked.addListener(function(notificationId, buttonIndex)
		{
			var callback = callbacks[notificationId];	

			if (callback)
			{
				callback(notificationId, buttonIndex);

				chrome.notifications.clear(notificationId, function(wasCleared)
				{
					callbacks[notificationId] = null;
					delete callbacks[notificationId];
				});
			}
		})	

		chrome.runtime.onMessageExternal.addListener(function(request, sender, sendResponse) 
		{
		
		});

		chrome.runtime.onConnectExternal.addListener(function(port) 
		{
			ports[port.name] = port;

			port.onMessage.addListener(function(msg) 
			{
			
			});

			port.onDisconnect.addListener(function()
			{
				delete ports[port.name];
			});	  	
		});	

		chrome.runtime.onMessage.addListener(function(request, sender) 
		{	
		
		});	
		
		ChromeUi.createRootWindow();		
		
	});

	window.addEventListener("beforeunload", function () 
	{
		console.log('background-ui.js beforeunload event');
		ChromeUi.destroyRootWindow();
	});
			
	return self;
	
}(ChromeUi || {}));	