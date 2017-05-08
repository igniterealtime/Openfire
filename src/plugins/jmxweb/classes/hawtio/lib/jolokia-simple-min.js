
(function(){var builder=function($,Jolokia){function getAttribute(mbean,attribute,path,opts){if(arguments.length===3&&$.isPlainObject(path)){opts=path;path=null;}else if(arguments.length==2&&$.isPlainObject(attribute)){opts=attribute;attribute=null;path=null;}
var req={type:"read",mbean:mbean,attribute:attribute};addPath(req,path);return extractValue(this.request(req,prepareSucessCallback(opts)),opts);}
function setAttribute(mbean,attribute,value,path,opts){if(arguments.length===4&&$.isPlainObject(path)){opts=path;path=null;}
var req={type:"write",mbean:mbean,attribute:attribute,value:value};addPath(req,path);return extractValue(this.request(req,prepareSucessCallback(opts)),opts);}
function execute(mbean,operation){var req={type:"exec",mbean:mbean,operation:operation};var opts,end=arguments.length;if(arguments.length>2&&$.isPlainObject(arguments[arguments.length-1])){opts=arguments[arguments.length-1];end=arguments.length-1;}
if(end>2){var args=[];for(var i=2;i<end;i++){args[i-2]=arguments[i];}
req.arguments=args;}
return extractValue(this.request(req,prepareSucessCallback(opts)),opts);}
function search(mbeanPattern,opts){var req={type:"search",mbean:mbeanPattern};return extractValue(this.request(req,prepareSucessCallback(opts)),opts);}
function version(opts){return extractValue(this.request({type:"version"},prepareSucessCallback(opts)),opts);}
function list(path,opts){if(arguments.length==1&&!$.isArray(path)&&$.isPlainObject(path)){opts=path;path=null;}
var req={type:"list"};addPath(req,path);return extractValue(this.request(req,prepareSucessCallback(opts)),opts);}
function addPath(req,path){if(path!=null){if($.isArray(path)){req.path=$.map(path,Jolokia.escape).join("/");}else{req.path=path;}}}
function extractValue(response,opts){if(response==null){return null;}
if(response.status==200){return response.value;}
if(opts&&opts.error){return opts.error(response);}else{throw new Error("Jolokia-Error: "+JSON.stringify(response));}}
function prepareSucessCallback(opts){if(opts&&opts.success){var parm=$.extend({},opts);parm.success=function(resp){opts.success(resp.value);};return parm;}else{return opts;}}
$.extend(Jolokia.prototype,{"getAttribute":getAttribute,"setAttribute":setAttribute,"execute":execute,"search":search,"version":version,"list":list});return Jolokia;};(function(root,factory){if(typeof define==='function'&&define.amd){define(["jquery","jolokia"],factory);}else{if(root.Jolokia){builder(jQuery,root.Jolokia);}else{console.error("No Jolokia definition found. Please include jolokia.js before jolokia-simple.js");}}}(this,function(jQuery,Jolokia){return builder(jQuery,Jolokia);}));})();