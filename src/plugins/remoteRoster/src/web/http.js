var http = function() {
	this.callback = function(a) {};

	this.obj = window.XMLHttpRequest ? new XMLHttpRequest()
			: new ActiveXObject("Microsoft.XMLHTTP");

	this.load = function(url) {
		this.obj.open('get', url);
		this.obj.callback = this.callback;
		this.obj.onreadystatechange = this.handle;
		this.obj.send(null);
	};

	this.handle = function() {
		if (this.readyState == 4)
			this.callback(this);
	};
};
