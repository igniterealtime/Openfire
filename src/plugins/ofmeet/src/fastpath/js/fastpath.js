var connection = null;
var userForm = null;

$(document).ready(function () 
{
    connection = new Openfire.Connection(window.location.protocol + "//" + window.location.host + '/http-bind/');        	
    connection.resource = Math.random().toString(36).substr(2, 20);
    connection.rawInput = function (data) { console.log('RECV: ' + data); };
    connection.rawOutput = function (data) { console.log('SEND: ' + data); };

    connection.connect(window.location.hostname, null, function (status) 
    {
	if (status == Strophe.Status.CONNECTED) 
	{
	    console.log('connected');
	    connection.send($pres()); 
            connection.addHandler(onMessage, 'http://jabber.org/protocol/workgroup', 'message');
	    
	    $('#fastpath').html('Online');
	    
	} else {
	    console.log('status', status);
	}
    });
});

function joinWorkgroup(workgroup, form)
{
	userForm = form;
	
	if (connection != null && connection.connected)
	{
		var iq = $iq({to: workgroup + "@workgroup." + connection.domain, type: 'set'}).c('join-queue', {xmlns: 'http://jabber.org/protocol/workgroup'});	
		iq.c('queue-notifications').up();
		iq.c('x', {xmlns: 'jabber:x:data', type: 'submit'});

		var items = Object.getOwnPropertyNames(form)

		for (var i=0; i< items.length; i++)
		{
			iq.c('field', {var: items[i]}).c('value').t(form[items[i]]).up().up();
		}
		
		iq.up();
		
		connection.sendIQ(iq,
			function (res) {
			    console.log('join workgroup ok', res);			
			},

			function (err) {
			    console.log('join workgroup error', err);
			}
		);
	}
}

function onMessage(message)
{
	console.log('onMessage', message);
	
	$(message).find('x').each(function() 
	{	
		var xmlns = $(this).attr("xmlns");
		
		if (xmlns == "jabber:x:conference")
		{
			var roomJid = $(this).attr("jid");	
			window.location.href = "/ofmeet/?r=" + Strophe.getNodeFromJid(roomJid) + "&n=" + userForm.username + "&q=" + userForm.question;
		}
	});
	return true;
}

