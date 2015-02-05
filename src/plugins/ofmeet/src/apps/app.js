var hash = null;
var url = urlParam("url");
var frameWindow = null;

function start()
{
	frameWindow = document.getElementById('iframe1');
	
	if (window.location.hash) hash = window.location.hash.substring(1);
	console.log("app.js start", hash, url);
		
	frameWindow.onload = function()
	{
		console.log("app.js onload", frameWindow);
	}	
    	
	if (url) frameWindow.src = url;	
	window.parent.connection.ofmuc.appReady();	
}

function stop()
{
	console.log("app.js stop");	
}


function urlParam(name)
{
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	if (!results) { return undefined; }
	return decodeURIComponent(results[1]) || undefined;
}

window.onhashchange = function()
{
	hash = window.location.hash.substring(1);
}
