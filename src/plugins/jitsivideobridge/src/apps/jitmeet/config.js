var config = {
    hosts: {
        domain: 'btg199251',
        muc: 'conference.btg199251', // FIXME: use XEP-0030
        bridge: 'jitsi-videobridge.btg199251' // FIXME: use XEP-0030
    },
   
//  useStunTurn: true, // use XEP-0215 to fetch STUN and TURN server
//  useIPv6: true, // ipv6 support. use at your own risk
    useNicks: false,
    bosh: 'https://btg199251:7443/http-bind/', // FIXME: use xep-0156 for that
    
    getroomnode: function (path) 
    { 
    	console.log('getroomnode', path);
	var name = "r";
	var roomnode = null;
	
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	
	if (!results)
		roomnode = null; 
	else 	roomnode = results[1] || undefined;	

	if (!roomnode) {
		roomnode = Math.random().toString(36).substr(2, 20);
		window.history.pushState('VideoChat', 'Room: ' + roomnode, path + "?r=" + roomnode);
	}
	return roomnode;    
    }    
};
