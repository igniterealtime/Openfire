var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.OfMeet = (function(self, Candy, $) {
		
	self.init = function() {
		var html = '<li id="ofmeet-control-icon" data-tooltip="Openfire Meetings"><img id="ofmeet-control" src="/ofmeet/candy/candyshop/ofmeet/webcam.png"></span></li>';
		$('#emoticons-icon').after(html);		
		
		$('#ofmeet-control-icon').click(function(event) 
		{
			var roomJid = Candy.View.getCurrent().roomJid;
			Candy.Core.Action.Jabber.Room.Leave(roomJid);
			self.showOfMeet(roomJid);
		});
		
		var html2 = '<div id="video-modal"><a id="video-modal-cancel" class="close" href="#">×</a><span id="video-modal-body"></span></div><div id="video-modal-overlay"></div>';
		$(html2).appendTo("body");
		
		$("#video-modal").css("height", window.innerHeight - 40);
		$("#video-modal").css("width", window.innerWidth - 20);		
		$("#video-modal-overlay").hide();
		
		$(window).resize(function () 
		{
			$("#video-modal").css("height", window.innerHeight - 40);
			$("#video-modal").css("width", window.innerWidth - 20);	       
		});		
	};

	self.showOfMeet = function(roomJid) 
	{
                $("#video-modal-cancel").show().click(function(e) 
                {
			$("#video-modal").fadeOut("fast", function() {
			    $("#video-modal-body").text("");
			    $("#video-modal-overlay").hide();
			});
                    	e.preventDefault();                	
                    	window.location.reload();                 	
                });

		var room = Strophe.getNodeFromJid(roomJid);
		
		$("#video-modal").stop(false, true);
		$("#video-modal").fadeIn("fast");
		$("#video-modal-body").html('<iframe id="ofmeet" src="/ofmeet/index.html?r=' + room + '"></iframe>');                
		$("#video-modal-overlay").show();
            
		return true;
	};

	return self;
}(CandyShop.OfMeet || {}, Candy, jQuery));
