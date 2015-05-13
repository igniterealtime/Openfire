
var dialer = null;
var offHook = false;
var conn = null;
var audioChannelId = null;
var mixerId = null;
var cleared = false;
var ringtone = null;
var domain = urlParam("domain");
var username = urlParam("username");
var password = urlParam("password");

window.addEventListener("unload", function () 
{
	if (conn && mixerId && audioChannelId) conn.inum.expireWebrtcDevice(mixerId, audioChannelId);
	if (conn) conn.disconnect();
});
                
window.addEventListener("load", function()
{
	dialer = document.querySelector("inum-telephone");

	dialer.addEventListener('Telephone.Dialer.Button', function (event)
	{
		//console.log("Telephone.Dialer.Button", event, dialer);

		if (event.detail.label == "Call")
			telephoneAction({action: "dial", destination: event.detail.number});

		else if (dialer.call) {
			telephoneAction({action: "hangup", call_id: dialer.call.id});
		}
	});

	dialer.addEventListener('Telephone.Dialer.Number', function (event)
	{
		console.log("Telephone.Dialer.Number", event);
		//telephoneAction({action: "dial", call_id: event.detail.number});						
	});

	dialer.addEventListener('Telephone.Dialer.Press', function (event)
	{
		//console.log("Telephone.Dialer.Press", event);	
	});

	dialer.addEventListener('Telephone.Dialer.Action', function (event)
	{
		console.log("Telephone.Dialer.Action", event);

		if (event.detail.action == 'end') 	telephoneAction({action: "hangup", call_id: event.detail.call.id});
		if (event.detail.action == 'hold') 	telephoneAction({action: "hold", call_id: event.detail.call.id});
		if (event.detail.action == 'unhold') 	telephoneAction({action: "join", mixer: event.detail.call.mixer});
				
		if ("#*0123456789".indexOf(event.detail.action) > -1)
		{
			telephoneAction({action: "dtmf", tone: event.detail.action});
		}		
	});
	
	$(document).bind('ofmeet.connected', function (event, connection)
	{
		console.log("ofmeet connected", connection);
		
		ofmeet.visible(false);		
		connection.inum.createWebrtcDevice();
		conn = connection;
	});
	
	$(document).bind('inum.offered', function (event, confId, audioId)
	{
		console.log("inum.offered", confId, audioId);
		audioChannelId = audioId;
		mixerId = confId;
		
		$("inum-telephone").css("display", "block");
		$('#pleasewait').css("display", "none");
	});

	$(document).bind('inum.streamadded', function (event, confId, audioId, data)
	{
		console.log("inum.streamadded", confId, audioId, data);
	});

	$(document).bind('inum.delivered', function (event, confId, audioId)
	{
		console.log("inum.delivered", confId, audioId);
	});
	
	$(document).bind('inum.cleared', function(event, callId)
	{
		console.log("inum.cleared", callId);
		dialer.setLabel("Call");
		dialer.setState("inactive");				
		dialer.call = null
		stopTone();		
	});	

	$(document).bind('inum.dialled', function (event, confId, to, callId)
	{
		console.log("inum.dialled", confId, to, callId);
		
		dialer.call = {id: callId, mixer: confId, to: to}		
		dialer.setLabel("Hangup");
	});


	$(document).bind('inum.answered', function(event, callId, callerId, calledId)
	{
		console.log("inum.answered", callId, callerId, calledId);
		dialer.setState("active");
		stopTone();
	});
	
	
	$(document).bind('inum.hangup', function(event, callId, callerId, calledId)
	{
		console.log("inum.hangup", callId, callerId, calledId);
		
		dialer.setLabel("Call");
		dialer.setState("inactive");				
		dialer.call = null
		stopTone();		
	});


	$(document).bind('ofmeet.ready', function ()
	{
		console.log("ofmeet.ready");
		ofmeet.connect();
	});	
		
	ofmeet.ready(username, password);	
})


function urlParam(name)
{
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	if (!results) { return undefined; }
	return unescape(results[1] || undefined);
};

function telephoneAction(request)
{
	console.log("telephoneAction", request);
	
	if (request.action == "dial")
	{
		conn.inum.dial(mixerId, domain ? "sip:" + request.destination + "@" + domain : request.destination);
		startTone("ringback-uk");
	}
	
	if (request.action == "hangup")
	{
		conn.inum.hangup(request.call_id);	
		stopTone();
	}
	
	if (request.action == "dtmf") conn.inum.sendTones(request.tone);		
}

function startTone(name)
{
	if (!ringtone)
	{
		ringtone = new Audio();
		ringtone.loop = true;
		ringtone.src = "ringtones/" + name + ".mp3";
		ringtone.play();
	}
}

function stopTone()
{
	if (ringtone)
	{
		ringtone.pause();
		ringtone = null;
	}
}
