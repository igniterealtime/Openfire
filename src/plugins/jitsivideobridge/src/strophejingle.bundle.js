var Base64=(function(){var keyStr="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";var obj={encode:function(input){var output="";var chr1,chr2,chr3;var enc1,enc2,enc3,enc4;var i=0;do{chr1=input.charCodeAt(i++);chr2=input.charCodeAt(i++);chr3=input.charCodeAt(i++);enc1=chr1>>2;enc2=((chr1&3)<<4)|(chr2>>4);enc3=((chr2&15)<<2)|(chr3>>6);enc4=chr3&63;if(isNaN(chr2)){enc3=enc4=64}else{if(isNaN(chr3)){enc4=64}}output=output+keyStr.charAt(enc1)+keyStr.charAt(enc2)+keyStr.charAt(enc3)+keyStr.charAt(enc4)}while(i<input.length);return output},decode:function(input){var output="";var chr1,chr2,chr3;var enc1,enc2,enc3,enc4;var i=0;input=input.replace(/[^A-Za-z0-9\+\/\=]/g,"");do{enc1=keyStr.indexOf(input.charAt(i++));enc2=keyStr.indexOf(input.charAt(i++));enc3=keyStr.indexOf(input.charAt(i++));enc4=keyStr.indexOf(input.charAt(i++));chr1=(enc1<<2)|(enc2>>4);chr2=((enc2&15)<<4)|(enc3>>2);chr3=((enc3&3)<<6)|enc4;output=output+String.fromCharCode(chr1);if(enc3!=64){output=output+String.fromCharCode(chr2)}if(enc4!=64){output=output+String.fromCharCode(chr3)}}while(i<input.length);return output}};return obj})();var MD5=(function(){var hexcase=0;var b64pad="";var chrsz=8;var safe_add=function(x,y){var lsw=(x&65535)+(y&65535);var msw=(x>>16)+(y>>16)+(lsw>>16);return(msw<<16)|(lsw&65535)};var bit_rol=function(num,cnt){return(num<<cnt)|(num>>>(32-cnt))};var str2binl=function(str){var bin=[];var mask=(1<<chrsz)-1;for(var i=0;i<str.length*chrsz;i+=chrsz){bin[i>>5]|=(str.charCodeAt(i/chrsz)&mask)<<(i%32)}return bin};var binl2str=function(bin){var str="";var mask=(1<<chrsz)-1;for(var i=0;i<bin.length*32;i+=chrsz){str+=String.fromCharCode((bin[i>>5]>>>(i%32))&mask)}return str};var binl2hex=function(binarray){var hex_tab=hexcase?"0123456789ABCDEF":"0123456789abcdef";var str="";for(var i=0;i<binarray.length*4;i++){str+=hex_tab.charAt((binarray[i>>2]>>((i%4)*8+4))&15)+hex_tab.charAt((binarray[i>>2]>>((i%4)*8))&15)}return str};var binl2b64=function(binarray){var tab="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";var str="";var triplet,j;for(var i=0;i<binarray.length*4;i+=3){triplet=(((binarray[i>>2]>>8*(i%4))&255)<<16)|(((binarray[i+1>>2]>>8*((i+1)%4))&255)<<8)|((binarray[i+2>>2]>>8*((i+2)%4))&255);for(j=0;j<4;j++){if(i*8+j*6>binarray.length*32){str+=b64pad}else{str+=tab.charAt((triplet>>6*(3-j))&63)}}}return str};var md5_cmn=function(q,a,b,x,s,t){return safe_add(bit_rol(safe_add(safe_add(a,q),safe_add(x,t)),s),b)};var md5_ff=function(a,b,c,d,x,s,t){return md5_cmn((b&c)|((~b)&d),a,b,x,s,t)};var md5_gg=function(a,b,c,d,x,s,t){return md5_cmn((b&d)|(c&(~d)),a,b,x,s,t)};var md5_hh=function(a,b,c,d,x,s,t){return md5_cmn(b^c^d,a,b,x,s,t)};var md5_ii=function(a,b,c,d,x,s,t){return md5_cmn(c^(b|(~d)),a,b,x,s,t)};var core_md5=function(x,len){x[len>>5]|=128<<((len)%32);x[(((len+64)>>>9)<<4)+14]=len;var a=1732584193;var b=-271733879;var c=-1732584194;var d=271733878;var olda,oldb,oldc,oldd;for(var i=0;i<x.length;i+=16){olda=a;oldb=b;oldc=c;oldd=d;a=md5_ff(a,b,c,d,x[i+0],7,-680876936);d=md5_ff(d,a,b,c,x[i+1],12,-389564586);c=md5_ff(c,d,a,b,x[i+2],17,606105819);b=md5_ff(b,c,d,a,x[i+3],22,-1044525330);a=md5_ff(a,b,c,d,x[i+4],7,-176418897);d=md5_ff(d,a,b,c,x[i+5],12,1200080426);c=md5_ff(c,d,a,b,x[i+6],17,-1473231341);b=md5_ff(b,c,d,a,x[i+7],22,-45705983);a=md5_ff(a,b,c,d,x[i+8],7,1770035416);d=md5_ff(d,a,b,c,x[i+9],12,-1958414417);c=md5_ff(c,d,a,b,x[i+10],17,-42063);b=md5_ff(b,c,d,a,x[i+11],22,-1990404162);a=md5_ff(a,b,c,d,x[i+12],7,1804603682);d=md5_ff(d,a,b,c,x[i+13],12,-40341101);c=md5_ff(c,d,a,b,x[i+14],17,-1502002290);b=md5_ff(b,c,d,a,x[i+15],22,1236535329);a=md5_gg(a,b,c,d,x[i+1],5,-165796510);d=md5_gg(d,a,b,c,x[i+6],9,-1069501632);c=md5_gg(c,d,a,b,x[i+11],14,643717713);b=md5_gg(b,c,d,a,x[i+0],20,-373897302);a=md5_gg(a,b,c,d,x[i+5],5,-701558691);d=md5_gg(d,a,b,c,x[i+10],9,38016083);c=md5_gg(c,d,a,b,x[i+15],14,-660478335);b=md5_gg(b,c,d,a,x[i+4],20,-405537848);a=md5_gg(a,b,c,d,x[i+9],5,568446438);d=md5_gg(d,a,b,c,x[i+14],9,-1019803690);c=md5_gg(c,d,a,b,x[i+3],14,-187363961);b=md5_gg(b,c,d,a,x[i+8],20,1163531501);a=md5_gg(a,b,c,d,x[i+13],5,-1444681467);d=md5_gg(d,a,b,c,x[i+2],9,-51403784);c=md5_gg(c,d,a,b,x[i+7],14,1735328473);b=md5_gg(b,c,d,a,x[i+12],20,-1926607734);a=md5_hh(a,b,c,d,x[i+5],4,-378558);d=md5_hh(d,a,b,c,x[i+8],11,-2022574463);c=md5_hh(c,d,a,b,x[i+11],16,1839030562);b=md5_hh(b,c,d,a,x[i+14],23,-35309556);a=md5_hh(a,b,c,d,x[i+1],4,-1530992060);d=md5_hh(d,a,b,c,x[i+4],11,1272893353);c=md5_hh(c,d,a,b,x[i+7],16,-155497632);b=md5_hh(b,c,d,a,x[i+10],23,-1094730640);a=md5_hh(a,b,c,d,x[i+13],4,681279174);d=md5_hh(d,a,b,c,x[i+0],11,-358537222);c=md5_hh(c,d,a,b,x[i+3],16,-722521979);b=md5_hh(b,c,d,a,x[i+6],23,76029189);a=md5_hh(a,b,c,d,x[i+9],4,-640364487);d=md5_hh(d,a,b,c,x[i+12],11,-421815835);c=md5_hh(c,d,a,b,x[i+15],16,530742520);b=md5_hh(b,c,d,a,x[i+2],23,-995338651);a=md5_ii(a,b,c,d,x[i+0],6,-198630844);d=md5_ii(d,a,b,c,x[i+7],10,1126891415);c=md5_ii(c,d,a,b,x[i+14],15,-1416354905);b=md5_ii(b,c,d,a,x[i+5],21,-57434055);a=md5_ii(a,b,c,d,x[i+12],6,1700485571);d=md5_ii(d,a,b,c,x[i+3],10,-1894986606);c=md5_ii(c,d,a,b,x[i+10],15,-1051523);b=md5_ii(b,c,d,a,x[i+1],21,-2054922799);a=md5_ii(a,b,c,d,x[i+8],6,1873313359);d=md5_ii(d,a,b,c,x[i+15],10,-30611744);c=md5_ii(c,d,a,b,x[i+6],15,-1560198380);b=md5_ii(b,c,d,a,x[i+13],21,1309151649);a=md5_ii(a,b,c,d,x[i+4],6,-145523070);d=md5_ii(d,a,b,c,x[i+11],10,-1120210379);c=md5_ii(c,d,a,b,x[i+2],15,718787259);b=md5_ii(b,c,d,a,x[i+9],21,-343485551);a=safe_add(a,olda);b=safe_add(b,oldb);c=safe_add(c,oldc);d=safe_add(d,oldd)}return[a,b,c,d]};var core_hmac_md5=function(key,data){var bkey=str2binl(key);if(bkey.length>16){bkey=core_md5(bkey,key.length*chrsz)}var ipad=new Array(16),opad=new Array(16);for(var i=0;i<16;i++){ipad[i]=bkey[i]^909522486;opad[i]=bkey[i]^1549556828}var hash=core_md5(ipad.concat(str2binl(data)),512+data.length*chrsz);return core_md5(opad.concat(hash),512+128)};var obj={hexdigest:function(s){return binl2hex(core_md5(str2binl(s),s.length*chrsz))},b64digest:function(s){return binl2b64(core_md5(str2binl(s),s.length*chrsz))},hash:function(s){return binl2str(core_md5(str2binl(s),s.length*chrsz))},hmac_hexdigest:function(key,data){return binl2hex(core_hmac_md5(key,data))},hmac_b64digest:function(key,data){return binl2b64(core_hmac_md5(key,data))},hmac_hash:function(key,data){return binl2str(core_hmac_md5(key,data))},test:function(){return MD5.hexdigest("abc")==="900150983cd24fb0d6963f7d28e17f72"}};return obj})();if(!Function.prototype.bind){Function.prototype.bind=function(obj){var func=this;var _slice=Array.prototype.slice;var _concat=Array.prototype.concat;var _args=_slice.call(arguments,1);return function(){return func.apply(obj?obj:this,_concat.call(_args,_slice.call(arguments,0)))}}}if(!Array.prototype.indexOf){Array.prototype.indexOf=function(elt){var len=this.length;var from=Number(arguments[1])||0;from=(from<0)?Math.ceil(from):Math.floor(from);if(from<0){from+=len}for(;from<len;from++){if(from in this&&this[from]===elt){return from}}return -1}}(function(callback){var Strophe;function $build(name,attrs){return new Strophe.Builder(name,attrs)}function $msg(attrs){return new Strophe.Builder("message",attrs)}function $iq(attrs){return new Strophe.Builder("iq",attrs)}function $pres(attrs){return new Strophe.Builder("presence",attrs)}Strophe={VERSION:"1.0.2",NS:{HTTPBIND:"http://jabber.org/protocol/httpbind",BOSH:"urn:xmpp:xbosh",CLIENT:"jabber:client",AUTH:"jabber:iq:auth",ROSTER:"jabber:iq:roster",PROFILE:"jabber:iq:profile",DISCO_INFO:"http://jabber.org/protocol/disco#info",DISCO_ITEMS:"http://jabber.org/protocol/disco#items",MUC:"http://jabber.org/protocol/muc",SASL:"urn:ietf:params:xml:ns:xmpp-sasl",STREAM:"http://etherx.jabber.org/streams",BIND:"urn:ietf:params:xml:ns:xmpp-bind",SESSION:"urn:ietf:params:xml:ns:xmpp-session",VERSION:"jabber:iq:version",STANZAS:"urn:ietf:params:xml:ns:xmpp-stanzas"},addNamespace:function(name,value){Strophe.NS[name]=value},Status:{ERROR:0,CONNECTING:1,CONNFAIL:2,AUTHENTICATING:3,AUTHFAIL:4,CONNECTED:5,DISCONNECTED:6,DISCONNECTING:7,ATTACHED:8},LogLevel:{DEBUG:0,INFO:1,WARN:2,ERROR:3,FATAL:4},ElementType:{NORMAL:1,TEXT:3,CDATA:4},TIMEOUT:1.1,SECONDARY_TIMEOUT:0.1,forEachChild:function(elem,elemName,func){var i,childNode;for(i=0;i<elem.childNodes.length;i++){childNode=elem.childNodes[i];if(childNode.nodeType==Strophe.ElementType.NORMAL&&(!elemName||this.isTagEqual(childNode,elemName))){func(childNode)}}},isTagEqual:function(el,name){return el.tagName.toLowerCase()==name.toLowerCase()},_xmlGenerator:null,_makeGenerator:function(){var doc;if(document.implementation.createDocument===undefined){doc=this._getIEXmlDom();doc.appendChild(doc.createElement("strophe"))}else{doc=document.implementation.createDocument("jabber:client","strophe",null)}return doc},xmlGenerator:function(){if(!Strophe._xmlGenerator){Strophe._xmlGenerator=Strophe._makeGenerator()}return Strophe._xmlGenerator},_getIEXmlDom:function(){var doc=null;var docStrings=["Msxml2.DOMDocument.6.0","Msxml2.DOMDocument.5.0","Msxml2.DOMDocument.4.0","MSXML2.DOMDocument.3.0","MSXML2.DOMDocument","MSXML.DOMDocument","Microsoft.XMLDOM"];for(var d=0;d<docStrings.length;d++){if(doc===null){try{doc=new ActiveXObject(docStrings[d])}catch(e){doc=null}}else{break}}return doc},xmlElement:function(name){if(!name){return null}var node=Strophe.xmlGenerator().createElement(name);var a,i,k;for(a=1;a<arguments.length;a++){if(!arguments[a]){continue}if(typeof(arguments[a])=="string"||typeof(arguments[a])=="number"){node.appendChild(Strophe.xmlTextNode(arguments[a]))}else{if(typeof(arguments[a])=="object"&&typeof(arguments[a].sort)=="function"){for(i=0;i<arguments[a].length;i++){if(typeof(arguments[a][i])=="object"&&typeof(arguments[a][i].sort)=="function"){node.setAttribute(arguments[a][i][0],arguments[a][i][1])}}}else{if(typeof(arguments[a])=="object"){for(k in arguments[a]){if(arguments[a].hasOwnProperty(k)){node.setAttribute(k,arguments[a][k])}}}}}}return node},xmlescape:function(text){text=text.replace(/\&/g,"&amp;");text=text.replace(/</g,"&lt;");text=text.replace(/>/g,"&gt;");text=text.replace(/'/g,"&apos;");text=text.replace(/"/g,"&quot;");return text},xmlTextNode:function(text){text=Strophe.xmlescape(text);return Strophe.xmlGenerator().createTextNode(text)},getText:function(elem){if(!elem){return null}var str="";if(elem.childNodes.length===0&&elem.nodeType==Strophe.ElementType.TEXT){str+=elem.nodeValue}for(var i=0;i<elem.childNodes.length;i++){if(elem.childNodes[i].nodeType==Strophe.ElementType.TEXT){str+=elem.childNodes[i].nodeValue}}return str},copyElement:function(elem){var i,el;if(elem.nodeType==Strophe.ElementType.NORMAL){el=Strophe.xmlElement(elem.tagName);for(i=0;i<elem.attributes.length;i++){el.setAttribute(elem.attributes[i].nodeName.toLowerCase(),elem.attributes[i].value)}for(i=0;i<elem.childNodes.length;i++){el.appendChild(Strophe.copyElement(elem.childNodes[i]))}}else{if(elem.nodeType==Strophe.ElementType.TEXT){el=Strophe.xmlGenerator().createTextNode(elem.nodeValue)}}return el},escapeNode:function(node){return node.replace(/^\s+|\s+$/g,"").replace(/\\/g,"\\5c").replace(/ /g,"\\20").replace(/\"/g,"\\22").replace(/\&/g,"\\26").replace(/\'/g,"\\27").replace(/\//g,"\\2f").replace(/:/g,"\\3a").replace(/</g,"\\3c").replace(/>/g,"\\3e").replace(/@/g,"\\40")},unescapeNode:function(node){return node.replace(/\\20/g," ").replace(/\\22/g,'"').replace(/\\26/g,"&").replace(/\\27/g,"'").replace(/\\2f/g,"/").replace(/\\3a/g,":").replace(/\\3c/g,"<").replace(/\\3e/g,">").replace(/\\40/g,"@").replace(/\\5c/g,"\\")},getNodeFromJid:function(jid){if(jid.indexOf("@")<0){return null}return jid.split("@")[0]},getDomainFromJid:function(jid){var bare=Strophe.getBareJidFromJid(jid);if(bare.indexOf("@")<0){return bare}else{var parts=bare.split("@");parts.splice(0,1);return parts.join("@")}},getResourceFromJid:function(jid){var s=jid.split("/");if(s.length<2){return null}s.splice(0,1);return s.join("/")},getBareJidFromJid:function(jid){return jid?jid.split("/")[0]:null},log:function(level,msg){return},debug:function(msg){this.log(this.LogLevel.DEBUG,msg)},info:function(msg){this.log(this.LogLevel.INFO,msg)},warn:function(msg){this.log(this.LogLevel.WARN,msg)},error:function(msg){this.log(this.LogLevel.ERROR,msg)},fatal:function(msg){this.log(this.LogLevel.FATAL,msg)},serialize:function(elem){var result;if(!elem){return null}if(typeof(elem.tree)==="function"){elem=elem.tree()}var nodeName=elem.nodeName;var i,child;if(elem.getAttribute("_realname")){nodeName=elem.getAttribute("_realname")}result="<"+nodeName;for(i=0;i<elem.attributes.length;i++){if(elem.attributes[i].nodeName!="_realname"){result+=" "+elem.attributes[i].nodeName.toLowerCase()+"='"+elem.attributes[i].value.replace(/&/g,"&amp;").replace(/\'/g,"&apos;").replace(/</g,"&lt;")+"'"}}if(elem.childNodes.length>0){result+=">";for(i=0;i<elem.childNodes.length;i++){child=elem.childNodes[i];switch(child.nodeType){case Strophe.ElementType.NORMAL:result+=Strophe.serialize(child);break;case Strophe.ElementType.TEXT:result+=Strophe.xmlescape(child.nodeValue);break;case Strophe.ElementType.CDATA:result+="<![CDATA["+child.nodeValue+"]]>"}}result+="</"+nodeName+">"}else{result+="/>"}return result},_requestId:0,_connectionPlugins:{},addConnectionPlugin:function(name,ptype){Strophe._connectionPlugins[name]=ptype}};Strophe.Builder=function(name,attrs){if(name=="presence"||name=="message"||name=="iq"){if(attrs&&!attrs.xmlns){attrs.xmlns=Strophe.NS.CLIENT}else{if(!attrs){attrs={xmlns:Strophe.NS.CLIENT}}}}this.nodeTree=Strophe.xmlElement(name,attrs);this.node=this.nodeTree};Strophe.Builder.prototype={tree:function(){return this.nodeTree},toString:function(){return Strophe.serialize(this.nodeTree)},up:function(){this.node=this.node.parentNode;return this},attrs:function(moreattrs){for(var k in moreattrs){if(moreattrs.hasOwnProperty(k)){this.node.setAttribute(k,moreattrs[k])}}return this},c:function(name,attrs,text){var child=Strophe.xmlElement(name,attrs,text);this.node.appendChild(child);if(!text){this.node=child}return this},cnode:function(elem){var xmlGen=Strophe.xmlGenerator();try{var impNode=(xmlGen.importNode!==undefined)}catch(e){var impNode=false}var newElem=impNode?xmlGen.importNode(elem,true):Strophe.copyElement(elem);this.node.appendChild(newElem);this.node=newElem;return this},t:function(text){var child=Strophe.xmlTextNode(text);this.node.appendChild(child);return this}};Strophe.Handler=function(handler,ns,name,type,id,from,options){this.handler=handler;this.ns=ns;this.name=name;this.type=type;this.id=id;this.options=options||{matchbare:false};if(!this.options.matchBare){this.options.matchBare=false}if(this.options.matchBare){this.from=from?Strophe.getBareJidFromJid(from):null}else{this.from=from}this.user=true};Strophe.Handler.prototype={isMatch:function(elem){var nsMatch;var from=null;if(this.options.matchBare){from=Strophe.getBareJidFromJid(elem.getAttribute("from"))}else{from=elem.getAttribute("from")}nsMatch=false;if(!this.ns){nsMatch=true}else{var that=this;Strophe.forEachChild(elem,null,function(elem){if(elem.getAttribute("xmlns")==that.ns){nsMatch=true}});nsMatch=nsMatch||elem.getAttribute("xmlns")==this.ns}if(nsMatch&&(!this.name||Strophe.isTagEqual(elem,this.name))&&(!this.type||elem.getAttribute("type")==this.type)&&(!this.id||elem.getAttribute("id")==this.id)&&(!this.from||from==this.from)){return true}return false},run:function(elem){var result=null;try{result=this.handler(elem)}catch(e){if(e.sourceURL){Strophe.fatal("error: "+this.handler+" "+e.sourceURL+":"+e.line+" - "+e.name+": "+e.message)}else{if(e.fileName){if(typeof(console)!="undefined"){console.trace();console.error(this.handler," - error - ",e,e.message)}Strophe.fatal("error: "+this.handler+" "+e.fileName+":"+e.lineNumber+" - "+e.name+": "+e.message)}else{Strophe.fatal("error: "+this.handler)}}throw e}return result},toString:function(){return"{Handler: "+this.handler+"("+this.name+","+this.id+","+this.ns+")}"}};Strophe.TimedHandler=function(period,handler){this.period=period;this.handler=handler;this.lastCalled=new Date().getTime();this.user=true};Strophe.TimedHandler.prototype={run:function(){this.lastCalled=new Date().getTime();return this.handler()},reset:function(){this.lastCalled=new Date().getTime()},toString:function(){return"{TimedHandler: "+this.handler+"("+this.period+")}"}};Strophe.Request=function(elem,func,rid,sends){this.id=++Strophe._requestId;this.xmlData=elem;this.data=Strophe.serialize(elem);this.origFunc=func;this.func=func;this.rid=rid;this.date=NaN;this.sends=sends||0;this.abort=false;this.dead=null;this.age=function(){if(!this.date){return 0}var now=new Date();return(now-this.date)/1000};this.timeDead=function(){if(!this.dead){return 0}var now=new Date();return(now-this.dead)/1000};this.xhr=this._newXHR()};Strophe.Request.prototype={getResponse:function(){var node=null;if(this.xhr.responseXML&&this.xhr.responseXML.documentElement){node=this.xhr.responseXML.documentElement;if(node.tagName=="parsererror"){Strophe.error("invalid response received");Strophe.error("responseText: "+this.xhr.responseText);Strophe.error("responseXML: "+Strophe.serialize(this.xhr.responseXML));throw"parsererror"}}else{if(this.xhr.responseText){Strophe.error("invalid response received");Strophe.error("responseText: "+this.xhr.responseText);Strophe.error("responseXML: "+Strophe.serialize(this.xhr.responseXML))}}return node},_newXHR:function(){var xhr=null;if(window.XMLHttpRequest){xhr=new XMLHttpRequest();if(xhr.overrideMimeType){xhr.overrideMimeType("text/xml")}}else{if(window.ActiveXObject){xhr=new ActiveXObject("Microsoft.XMLHTTP")}}xhr.onreadystatechange=this.func.bind(null,this);return xhr}};Strophe.Connection=function(service){this.service=service;this.jid="";this.rid=Math.floor(Math.random()*4294967295);this.sid=null;this.streamId=null;this.features=null;this.do_session=false;this.do_bind=false;this.timedHandlers=[];this.handlers=[];this.removeTimeds=[];this.removeHandlers=[];this.addTimeds=[];this.addHandlers=[];this._idleTimeout=null;this._disconnectTimeout=null;this.authenticated=false;this.disconnecting=false;this.connected=false;this.errors=0;this.paused=false;this.hold=1;this.wait=60;this.window=5;this._data=[];this._requests=[];this._uniqueId=Math.round(Math.random()*10000);this._sasl_success_handler=null;this._sasl_failure_handler=null;this._sasl_challenge_handler=null;this._idleTimeout=setTimeout(this._onIdle.bind(this),100);for(var k in Strophe._connectionPlugins){if(Strophe._connectionPlugins.hasOwnProperty(k)){var ptype=Strophe._connectionPlugins[k];var F=function(){};F.prototype=ptype;this[k]=new F();this[k].init(this)}}};Strophe.Connection.prototype={reset:function(){this.rid=Math.floor(Math.random()*4294967295);this.sid=null;this.streamId=null;this.do_session=false;this.do_bind=false;this.timedHandlers=[];this.handlers=[];this.removeTimeds=[];this.removeHandlers=[];this.addTimeds=[];this.addHandlers=[];this.authenticated=false;this.disconnecting=false;this.connected=false;this.errors=0;this._requests=[];this._uniqueId=Math.round(Math.random()*10000)},pause:function(){this.paused=true},resume:function(){this.paused=false},getUniqueId:function(suffix){if(typeof(suffix)=="string"||typeof(suffix)=="number"){return ++this._uniqueId+":"+suffix}else{return ++this._uniqueId+""}},connect:function(jid,pass,callback,wait,hold){this.jid=jid;this.pass=pass;this.connect_callback=callback;this.disconnecting=false;this.connected=false;this.authenticated=false;this.errors=0;this.wait=wait||this.wait;this.hold=hold||this.hold;this.domain=Strophe.getDomainFromJid(this.jid);var body=this._buildBody().attrs({to:this.domain,"xml:lang":"en",wait:this.wait,hold:this.hold,content:"text/xml; charset=utf-8",ver:"1.6","xmpp:version":"1.0","xmlns:xmpp":Strophe.NS.BOSH});this._changeConnectStatus(Strophe.Status.CONNECTING,null);this._requests.push(new Strophe.Request(body.tree(),this._onRequestStateChange.bind(this,this._connect_cb.bind(this)),body.tree().getAttribute("rid")));this._throttledRequestHandler()},attach:function(jid,sid,rid,callback,wait,hold,wind){this.jid=jid;this.sid=sid;this.rid=rid;this.connect_callback=callback;this.domain=Strophe.getDomainFromJid(this.jid);this.authenticated=true;this.connected=true;this.wait=wait||this.wait;this.hold=hold||this.hold;this.window=wind||this.window;this._changeConnectStatus(Strophe.Status.ATTACHED,null)},xmlInput:function(elem){return},xmlOutput:function(elem){return},rawInput:function(data){return},rawOutput:function(data){return},send:function(elem){if(elem===null){return}if(typeof(elem.sort)==="function"){for(var i=0;i<elem.length;i++){this._queueData(elem[i])}}else{if(typeof(elem.tree)==="function"){this._queueData(elem.tree())}else{this._queueData(elem)}}this._throttledRequestHandler();clearTimeout(this._idleTimeout);this._idleTimeout=setTimeout(this._onIdle.bind(this),100)},flush:function(){clearTimeout(this._idleTimeout);this._onIdle()},sendIQ:function(elem,callback,errback,timeout){var timeoutHandler=null;var that=this;if(typeof(elem.tree)==="function"){elem=elem.tree()}var id=elem.getAttribute("id");if(!id){id=this.getUniqueId("sendIQ");elem.setAttribute("id",id)}var handler=this.addHandler(function(stanza){if(timeoutHandler){that.deleteTimedHandler(timeoutHandler)}var iqtype=stanza.getAttribute("type");if(iqtype=="result"){if(callback){callback(stanza)}}else{if(iqtype=="error"){if(errback){errback(stanza)}}else{throw {name:"StropheError",message:"Got bad IQ type of "+iqtype}}}},null,"iq",null,id);if(timeout){timeoutHandler=this.addTimedHandler(timeout,function(){that.deleteHandler(handler);if(errback){errback(null)}return false})}this.send(elem);return id},_queueData:function(element){if(element===null||!element.tagName||!element.childNodes){throw {name:"StropheError",message:"Cannot queue non-DOMElement."}}this._data.push(element)},_sendRestart:function(){this._data.push("restart");this._throttledRequestHandler();clearTimeout(this._idleTimeout);this._idleTimeout=setTimeout(this._onIdle.bind(this),100)},addTimedHandler:function(period,handler){var thand=new Strophe.TimedHandler(period,handler);this.addTimeds.push(thand);return thand},deleteTimedHandler:function(handRef){this.removeTimeds.push(handRef)},addHandler:function(handler,ns,name,type,id,from,options){var hand=new Strophe.Handler(handler,ns,name,type,id,from,options);this.addHandlers.push(hand);return hand},deleteHandler:function(handRef){this.removeHandlers.push(handRef)},disconnect:function(reason){this._changeConnectStatus(Strophe.Status.DISCONNECTING,reason);Strophe.info("Disconnect was called because: "+reason);if(this.connected){this._disconnectTimeout=this._addSysTimedHandler(3000,this._onDisconnectTimeout.bind(this));this._sendTerminate()}},_changeConnectStatus:function(status,condition){for(var k in Strophe._connectionPlugins){if(Strophe._connectionPlugins.hasOwnProperty(k)){var plugin=this[k];if(plugin.statusChanged){try{plugin.statusChanged(status,condition)}catch(err){Strophe.error(""+k+" plugin caused an exception changing status: "+err)}}}}if(this.connect_callback){try{this.connect_callback(status,condition)}catch(e){Strophe.error("User connection callback caused an exception: "+e)}}},_buildBody:function(){var bodyWrap=$build("body",{rid:this.rid++,xmlns:Strophe.NS.HTTPBIND});if(this.sid!==null){bodyWrap.attrs({sid:this.sid})}return bodyWrap},_removeRequest:function(req){Strophe.debug("removing request");var i;for(i=this._requests.length-1;i>=0;i--){if(req==this._requests[i]){this._requests.splice(i,1)}}req.xhr.onreadystatechange=function(){};this._throttledRequestHandler()},_restartRequest:function(i){var req=this._requests[i];if(req.dead===null){req.dead=new Date()}this._processRequest(i)},_processRequest:function(i){var req=this._requests[i];var reqStatus=-1;try{if(req.xhr.readyState==4){reqStatus=req.xhr.status}}catch(e){Strophe.error("caught an error in _requests["+i+"], reqStatus: "+reqStatus)}if(typeof(reqStatus)=="undefined"){reqStatus=-1}if(req.sends>5){this._onDisconnectTimeout();return}var time_elapsed=req.age();var primaryTimeout=(!isNaN(time_elapsed)&&time_elapsed>Math.floor(Strophe.TIMEOUT*this.wait));var secondaryTimeout=(req.dead!==null&&req.timeDead()>Math.floor(Strophe.SECONDARY_TIMEOUT*this.wait));var requestCompletedWithServerError=(req.xhr.readyState==4&&(reqStatus<1||reqStatus>=500));if(primaryTimeout||secondaryTimeout||requestCompletedWithServerError){if(secondaryTimeout){Strophe.error("Request "+this._requests[i].id+" timed out (secondary), restarting")}req.abort=true;req.xhr.abort();req.xhr.onreadystatechange=function(){};this._requests[i]=new Strophe.Request(req.xmlData,req.origFunc,req.rid,req.sends);req=this._requests[i]}if(req.xhr.readyState===0){Strophe.debug("request id "+req.id+"."+req.sends+" posting");try{req.xhr.open("POST",this.service,true)}catch(e2){Strophe.error("XHR open failed.");if(!this.connected){this._changeConnectStatus(Strophe.Status.CONNFAIL,"bad-service")}this.disconnect();return}var sendFunc=function(){req.date=new Date();req.xhr.send(req.data)};if(req.sends>1){var backoff=Math.min(Math.floor(Strophe.TIMEOUT*this.wait),Math.pow(req.sends,3))*1000;setTimeout(sendFunc,backoff)}else{sendFunc()}req.sends++;if(this.xmlOutput!==Strophe.Connection.prototype.xmlOutput){this.xmlOutput(req.xmlData)}if(this.rawOutput!==Strophe.Connection.prototype.rawOutput){this.rawOutput(req.data)}}else{Strophe.debug("_processRequest: "+(i===0?"first":"second")+" request has readyState of "+req.xhr.readyState)}},_throttledRequestHandler:function(){if(!this._requests){Strophe.debug("_throttledRequestHandler called with undefined requests")}else{Strophe.debug("_throttledRequestHandler called with "+this._requests.length+" requests")}if(!this._requests||this._requests.length===0){return}if(this._requests.length>0){this._processRequest(0)}if(this._requests.length>1&&Math.abs(this._requests[0].rid-this._requests[1].rid)<this.window){this._processRequest(1)}},_onRequestStateChange:function(func,req){Strophe.debug("request id "+req.id+"."+req.sends+" state changed to "+req.xhr.readyState);if(req.abort){req.abort=false;return}var reqStatus;if(req.xhr.readyState==4){reqStatus=0;try{reqStatus=req.xhr.status}catch(e){}if(typeof(reqStatus)=="undefined"){reqStatus=0}if(this.disconnecting){if(reqStatus>=400){this._hitError(reqStatus);return}}var reqIs0=(this._requests[0]==req);var reqIs1=(this._requests[1]==req);if((reqStatus>0&&reqStatus<500)||req.sends>5){this._removeRequest(req);Strophe.debug("request id "+req.id+" should now be removed")}if(reqStatus==200){if(reqIs1||(reqIs0&&this._requests.length>0&&this._requests[0].age()>Math.floor(Strophe.SECONDARY_TIMEOUT*this.wait))){this._restartRequest(0)}Strophe.debug("request id "+req.id+"."+req.sends+" got 200");func(req);this.errors=0}else{Strophe.error("request id "+req.id+"."+req.sends+" error "+reqStatus+" happened");if(reqStatus===0||(reqStatus>=400&&reqStatus<600)||reqStatus>=12000){this._hitError(reqStatus);if(reqStatus>=400&&reqStatus<500){this._changeConnectStatus(Strophe.Status.DISCONNECTING,null);this._doDisconnect()}}}if(!((reqStatus>0&&reqStatus<500)||req.sends>5)){this._throttledRequestHandler()}}},_hitError:function(reqStatus){this.errors++;Strophe.warn("request errored, status: "+reqStatus+", number of errors: "+this.errors);if(this.errors>4){this._onDisconnectTimeout()}},_doDisconnect:function(){Strophe.info("_doDisconnect was called");this.authenticated=false;this.disconnecting=false;this.sid=null;this.streamId=null;this.rid=Math.floor(Math.random()*4294967295);if(this.connected){this._changeConnectStatus(Strophe.Status.DISCONNECTED,null);this.connected=false}this.handlers=[];this.timedHandlers=[];this.removeTimeds=[];this.removeHandlers=[];this.addTimeds=[];this.addHandlers=[]},_dataRecv:function(req){try{var elem=req.getResponse()}catch(e){if(e!="parsererror"){throw e}this.disconnect("strophe-parsererror")}if(elem===null){return}if(this.xmlInput!==Strophe.Connection.prototype.xmlInput){this.xmlInput(elem)}if(this.rawInput!==Strophe.Connection.prototype.rawInput){this.rawInput(Strophe.serialize(elem))}var i,hand;while(this.removeHandlers.length>0){hand=this.removeHandlers.pop();i=this.handlers.indexOf(hand);if(i>=0){this.handlers.splice(i,1)}}while(this.addHandlers.length>0){this.handlers.push(this.addHandlers.pop())}if(this.disconnecting&&this._requests.length===0){this.deleteTimedHandler(this._disconnectTimeout);this._disconnectTimeout=null;this._doDisconnect();return}var typ=elem.getAttribute("type");var cond,conflict;if(typ!==null&&typ=="terminate"){if(this.disconnecting){return}cond=elem.getAttribute("condition");conflict=elem.getElementsByTagName("conflict");if(cond!==null){if(cond=="remote-stream-error"&&conflict.length>0){cond="conflict"}this._changeConnectStatus(Strophe.Status.CONNFAIL,cond)}else{this._changeConnectStatus(Strophe.Status.CONNFAIL,"unknown")}this.disconnect();return}var that=this;Strophe.forEachChild(elem,null,function(child){var i,newList;newList=that.handlers;that.handlers=[];for(i=0;i<newList.length;i++){var hand=newList[i];try{if(hand.isMatch(child)&&(that.authenticated||!hand.user)){if(hand.run(child)){that.handlers.push(hand)}}else{that.handlers.push(hand)}}catch(e){}}})},_sendTerminate:function(){Strophe.info("_sendTerminate was called");var body=this._buildBody().attrs({type:"terminate"});if(this.authenticated){body.c("presence",{xmlns:Strophe.NS.CLIENT,type:"unavailable"})}this.disconnecting=true;var req=new Strophe.Request(body.tree(),this._onRequestStateChange.bind(this,this._dataRecv.bind(this)),body.tree().getAttribute("rid"));this._requests.push(req);this._throttledRequestHandler()},_connect_cb:function(req){Strophe.info("_connect_cb was called");this.connected=true;var bodyWrap=req.getResponse();if(!bodyWrap){return}if(this.xmlInput!==Strophe.Connection.prototype.xmlInput){this.xmlInput(bodyWrap)}if(this.rawInput!==Strophe.Connection.prototype.rawInput){this.rawInput(Strophe.serialize(bodyWrap))}var typ=bodyWrap.getAttribute("type");var cond,conflict;if(typ!==null&&typ=="terminate"){cond=bodyWrap.getAttribute("condition");conflict=bodyWrap.getElementsByTagName("conflict");if(cond!==null){if(cond=="remote-stream-error"&&conflict.length>0){cond="conflict"}this._changeConnectStatus(Strophe.Status.CONNFAIL,cond)}else{this._changeConnectStatus(Strophe.Status.CONNFAIL,"unknown")}return}if(!this.sid){this.sid=bodyWrap.getAttribute("sid")}if(!this.stream_id){this.stream_id=bodyWrap.getAttribute("authid")}var wind=bodyWrap.getAttribute("requests");if(wind){this.window=parseInt(wind,10)}var hold=bodyWrap.getAttribute("hold");if(hold){this.hold=parseInt(hold,10)}var wait=bodyWrap.getAttribute("wait");if(wait){this.wait=parseInt(wait,10)}var do_sasl_plain=false;var do_sasl_digest_md5=false;var do_sasl_anonymous=false;var mechanisms=bodyWrap.getElementsByTagName("mechanism");var i,mech,auth_str,hashed_auth_str;if(mechanisms.length>0){for(i=0;i<mechanisms.length;i++){mech=Strophe.getText(mechanisms[i]);if(mech=="DIGEST-MD5"){do_sasl_digest_md5=true}else{if(mech=="PLAIN"){do_sasl_plain=true}else{if(mech=="ANONYMOUS"){do_sasl_anonymous=true}}}}}else{var body=this._buildBody();this._requests.push(new Strophe.Request(body.tree(),this._onRequestStateChange.bind(this,this._connect_cb.bind(this)),body.tree().getAttribute("rid")));this._throttledRequestHandler();return}if(Strophe.getNodeFromJid(this.jid)===null&&do_sasl_anonymous){this._changeConnectStatus(Strophe.Status.AUTHENTICATING,null);this._sasl_success_handler=this._addSysHandler(this._sasl_success_cb.bind(this),null,"success",null,null);this._sasl_failure_handler=this._addSysHandler(this._sasl_failure_cb.bind(this),null,"failure",null,null);this.send($build("auth",{xmlns:Strophe.NS.SASL,mechanism:"ANONYMOUS"}).tree())}else{if(Strophe.getNodeFromJid(this.jid)===null){this._changeConnectStatus(Strophe.Status.CONNFAIL,"x-strophe-bad-non-anon-jid");this.disconnect()}else{if(do_sasl_digest_md5){this._changeConnectStatus(Strophe.Status.AUTHENTICATING,null);this._sasl_challenge_handler=this._addSysHandler(this._sasl_challenge1_cb.bind(this),null,"challenge",null,null);this._sasl_failure_handler=this._addSysHandler(this._sasl_failure_cb.bind(this),null,"failure",null,null);this.send($build("auth",{xmlns:Strophe.NS.SASL,mechanism:"DIGEST-MD5"}).tree())}else{if(do_sasl_plain){auth_str=Strophe.getBareJidFromJid(this.jid);auth_str=auth_str+"\u0000";auth_str=auth_str+Strophe.getNodeFromJid(this.jid);auth_str=auth_str+"\u0000";auth_str=auth_str+this.pass;this._changeConnectStatus(Strophe.Status.AUTHENTICATING,null);this._sasl_success_handler=this._addSysHandler(this._sasl_success_cb.bind(this),null,"success",null,null);this._sasl_failure_handler=this._addSysHandler(this._sasl_failure_cb.bind(this),null,"failure",null,null);hashed_auth_str=Base64.encode(auth_str);this.send($build("auth",{xmlns:Strophe.NS.SASL,mechanism:"PLAIN"}).t(hashed_auth_str).tree())}else{this._changeConnectStatus(Strophe.Status.AUTHENTICATING,null);this._addSysHandler(this._auth1_cb.bind(this),null,null,null,"_auth_1");this.send($iq({type:"get",to:this.domain,id:"_auth_1"}).c("query",{xmlns:Strophe.NS.AUTH}).c("username",{}).t(Strophe.getNodeFromJid(this.jid)).tree())}}}}},_sasl_challenge1_cb:function(elem){var attribMatch=/([a-z]+)=("[^"]+"|[^,"]+)(?:,|$)/;var challenge=Base64.decode(Strophe.getText(elem));var cnonce=MD5.hexdigest(""+(Math.random()*1234567890));var realm="";var host=null;var nonce="";var qop="";var matches;this.deleteHandler(this._sasl_failure_handler);while(challenge.match(attribMatch)){matches=challenge.match(attribMatch);challenge=challenge.replace(matches[0],"");matches[2]=matches[2].replace(/^"(.+)"$/,"$1");switch(matches[1]){case"realm":realm=matches[2];break;case"nonce":nonce=matches[2];break;case"qop":qop=matches[2];break;case"host":host=matches[2];break}}var digest_uri="xmpp/"+this.domain;if(host!==null){digest_uri=digest_uri+"/"+host}var A1=MD5.hash(Strophe.getNodeFromJid(this.jid)+":"+realm+":"+this.pass)+":"+nonce+":"+cnonce;var A2="AUTHENTICATE:"+digest_uri;var responseText="";responseText+="username="+this._quote(Strophe.getNodeFromJid(this.jid))+",";responseText+="realm="+this._quote(realm)+",";responseText+="nonce="+this._quote(nonce)+",";responseText+="cnonce="+this._quote(cnonce)+",";responseText+='nc="00000001",';responseText+='qop="auth",';responseText+="digest-uri="+this._quote(digest_uri)+",";responseText+="response="+this._quote(MD5.hexdigest(MD5.hexdigest(A1)+":"+nonce+":00000001:"+cnonce+":auth:"+MD5.hexdigest(A2)))+",";responseText+='charset="utf-8"';this._sasl_challenge_handler=this._addSysHandler(this._sasl_challenge2_cb.bind(this),null,"challenge",null,null);this._sasl_success_handler=this._addSysHandler(this._sasl_success_cb.bind(this),null,"success",null,null);this._sasl_failure_handler=this._addSysHandler(this._sasl_failure_cb.bind(this),null,"failure",null,null);this.send($build("response",{xmlns:Strophe.NS.SASL}).t(Base64.encode(responseText)).tree());return false},_quote:function(str){return'"'+str.replace(/\\/g,"\\\\").replace(/"/g,'\\"')+'"'},_sasl_challenge2_cb:function(elem){this.deleteHandler(this._sasl_success_handler);this.deleteHandler(this._sasl_failure_handler);this._sasl_success_handler=this._addSysHandler(this._sasl_success_cb.bind(this),null,"success",null,null);this._sasl_failure_handler=this._addSysHandler(this._sasl_failure_cb.bind(this),null,"failure",null,null);this.send($build("response",{xmlns:Strophe.NS.SASL}).tree());return false},_auth1_cb:function(elem){var iq=$iq({type:"set",id:"_auth_2"}).c("query",{xmlns:Strophe.NS.AUTH}).c("username",{}).t(Strophe.getNodeFromJid(this.jid)).up().c("password").t(this.pass);if(!Strophe.getResourceFromJid(this.jid)){this.jid=Strophe.getBareJidFromJid(this.jid)+"/strophe"}iq.up().c("resource",{}).t(Strophe.getResourceFromJid(this.jid));this._addSysHandler(this._auth2_cb.bind(this),null,null,null,"_auth_2");this.send(iq.tree());return false},_sasl_success_cb:function(elem){Strophe.info("SASL authentication succeeded.");this.deleteHandler(this._sasl_failure_handler);this._sasl_failure_handler=null;if(this._sasl_challenge_handler){this.deleteHandler(this._sasl_challenge_handler);this._sasl_challenge_handler=null}this._addSysHandler(this._sasl_auth1_cb.bind(this),null,"stream:features",null,null);this._sendRestart();return false},_sasl_auth1_cb:function(elem){this.features=elem;var i,child;for(i=0;i<elem.childNodes.length;i++){child=elem.childNodes[i];if(child.nodeName=="bind"){this.do_bind=true}if(child.nodeName=="session"){this.do_session=true}}if(!this.do_bind){this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);return false}else{this._addSysHandler(this._sasl_bind_cb.bind(this),null,null,null,"_bind_auth_2");var resource=Strophe.getResourceFromJid(this.jid);if(resource){this.send($iq({type:"set",id:"_bind_auth_2"}).c("bind",{xmlns:Strophe.NS.BIND}).c("resource",{}).t(resource).tree())}else{this.send($iq({type:"set",id:"_bind_auth_2"}).c("bind",{xmlns:Strophe.NS.BIND}).tree())}}return false},_sasl_bind_cb:function(elem){if(elem.getAttribute("type")=="error"){Strophe.info("SASL binding failed.");this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);return false}var bind=elem.getElementsByTagName("bind");var jidNode;if(bind.length>0){jidNode=bind[0].getElementsByTagName("jid");if(jidNode.length>0){this.jid=Strophe.getText(jidNode[0]);if(this.do_session){this._addSysHandler(this._sasl_session_cb.bind(this),null,null,null,"_session_auth_2");this.send($iq({type:"set",id:"_session_auth_2"}).c("session",{xmlns:Strophe.NS.SESSION}).tree())}else{this.authenticated=true;this._changeConnectStatus(Strophe.Status.CONNECTED,null)}}}else{Strophe.info("SASL binding failed.");this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);return false}},_sasl_session_cb:function(elem){if(elem.getAttribute("type")=="result"){this.authenticated=true;this._changeConnectStatus(Strophe.Status.CONNECTED,null)}else{if(elem.getAttribute("type")=="error"){Strophe.info("Session creation failed.");this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);return false}}return false},_sasl_failure_cb:function(elem){if(this._sasl_success_handler){this.deleteHandler(this._sasl_success_handler);this._sasl_success_handler=null}if(this._sasl_challenge_handler){this.deleteHandler(this._sasl_challenge_handler);this._sasl_challenge_handler=null}this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);return false},_auth2_cb:function(elem){if(elem.getAttribute("type")=="result"){this.authenticated=true;this._changeConnectStatus(Strophe.Status.CONNECTED,null)}else{if(elem.getAttribute("type")=="error"){this._changeConnectStatus(Strophe.Status.AUTHFAIL,null);this.disconnect()}}return false},_addSysTimedHandler:function(period,handler){var thand=new Strophe.TimedHandler(period,handler);thand.user=false;this.addTimeds.push(thand);return thand},_addSysHandler:function(handler,ns,name,type,id){var hand=new Strophe.Handler(handler,ns,name,type,id);hand.user=false;this.addHandlers.push(hand);return hand},_onDisconnectTimeout:function(){Strophe.info("_onDisconnectTimeout was called");var req;while(this._requests.length>0){req=this._requests.pop();req.abort=true;req.xhr.abort();req.xhr.onreadystatechange=function(){}}this._doDisconnect();return false},_onIdle:function(){var i,thand,since,newList;while(this.addTimeds.length>0){this.timedHandlers.push(this.addTimeds.pop())}while(this.removeTimeds.length>0){thand=this.removeTimeds.pop();i=this.timedHandlers.indexOf(thand);if(i>=0){this.timedHandlers.splice(i,1)}}var now=new Date().getTime();newList=[];for(i=0;i<this.timedHandlers.length;i++){thand=this.timedHandlers[i];if(this.authenticated||!thand.user){since=thand.lastCalled+thand.period;if(since-now<=0){if(thand.run()){newList.push(thand)}}else{newList.push(thand)}}}this.timedHandlers=newList;var body,time_elapsed;if(this.authenticated&&this._requests.length===0&&this._data.length===0&&!this.disconnecting){Strophe.info("no requests during idle cycle, sending blank request");this._data.push(null)}if(this._requests.length<2&&this._data.length>0&&!this.paused){body=this._buildBody();for(i=0;i<this._data.length;i++){if(this._data[i]!==null){if(this._data[i]==="restart"){body.attrs({to:this.domain,"xml:lang":"en","xmpp:restart":"true","xmlns:xmpp":Strophe.NS.BOSH})}else{body.cnode(this._data[i]).up()}}}delete this._data;this._data=[];this._requests.push(new Strophe.Request(body.tree(),this._onRequestStateChange.bind(this,this._dataRecv.bind(this)),body.tree().getAttribute("rid")));this._processRequest(this._requests.length-1)}if(this._requests.length>0){time_elapsed=this._requests[0].age();if(this._requests[0].dead!==null){if(this._requests[0].timeDead()>Math.floor(Strophe.SECONDARY_TIMEOUT*this.wait)){this._throttledRequestHandler()}}if(time_elapsed>Math.floor(Strophe.TIMEOUT*this.wait)){Strophe.warn("Request "+this._requests[0].id+" timed out, over "+Math.floor(Strophe.TIMEOUT*this.wait)+" seconds since last activity");this._throttledRequestHandler()}}clearTimeout(this._idleTimeout);if(this.connected){this._idleTimeout=setTimeout(this._onIdle.bind(this),100)}}};if(callback){callback(Strophe,$build,$msg,$iq,$pres)}})(function(){window.Strophe=arguments[0];window.$build=arguments[1];window.$msg=arguments[2];window.$iq=arguments[3];window.$pres=arguments[4]});Strophe.addConnectionPlugin("disco",{_connection:null,_identities:[],_features:[],_items:[],init:function(a){this._connection=a;this._identities=[];this._features=[];this._items=[];a.addHandler(this._onDiscoInfo.bind(this),Strophe.NS.DISCO_INFO,"iq","get",null,null);a.addHandler(this._onDiscoItems.bind(this),Strophe.NS.DISCO_ITEMS,"iq","get",null,null)},addIdentity:function(d,c,a,e){for(var b=0;b<this._identities.length;b++){if(this._identities[b].category==d&&this._identities[b].type==c&&this._identities[b].name==a&&this._identities[b].lang==e){return false}}this._identities.push({category:d,type:c,name:a,lang:e});return true},addFeature:function(b){for(var a=0;a<this._features.length;a++){if(this._features[a]==b){return false}}this._features.push(b);return true},removeFeature:function(b){for(var a=0;a<this._features.length;a++){if(this._features[a]===b){this._features.splice(a,1);return true}}return false},addItem:function(b,a,c,d){if(c&&!d){return false}this._items.push({jid:b,name:a,node:c,call_back:d});return true},info:function(c,d,g,b,e){var a={xmlns:Strophe.NS.DISCO_INFO};if(d){a.node=d}var f=$iq({from:this._connection.jid,to:c,type:"get"}).c("query",a);this._connection.sendIQ(f,g,b,e)},items:function(d,e,g,c,f){var b={xmlns:Strophe.NS.DISCO_ITEMS};if(e){b.node=e}var a=$iq({from:this._connection.jid,to:d,type:"get"}).c("query",b);this._connection.sendIQ(a,g,c,f)},_buildIQResult:function(c,b){var e=c.getAttribute("id");var d=c.getAttribute("from");var a=$iq({type:"result",id:e});if(d!==null){a.attrs({to:d})}return a.c("query",b)},_onDiscoInfo:function(e){var d=e.getElementsByTagName("query")[0].getAttribute("node");var a={xmlns:Strophe.NS.DISCO_INFO};if(d){a.node=d}var c=this._buildIQResult(e,a);for(var b=0;b<this._identities.length;b++){var a={category:this._identities[b].category,type:this._identities[b].type};if(this._identities[b].name){a.name=this._identities[b].name}if(this._identities[b].lang){a["xml:lang"]=this._identities[b].lang}c.c("identity",a).up()}for(var b=0;b<this._features.length;b++){c.c("feature",{"var":this._features[b]}).up()}this._connection.send(c.tree());return true},_onDiscoItems:function(g){var f={xmlns:Strophe.NS.DISCO_ITEMS};var e=g.getElementsByTagName("query")[0].getAttribute("node");if(e){f.node=e;var a=[];for(var c=0;c<this._items.length;c++){if(this._items[c].node==e){a=this._items[c].call_back(g);break}}}else{var a=this._items}var d=this._buildIQResult(g,f);for(var c=0;c<a.length;c++){var b={jid:a[c].jid};if(a[c].name){b.name=a[c].name}if(a[c].node){b.node=a[c].node}d.c("item",b).up()}this._connection.send(d.tree());return true}});/* jshint -W117 */
// mozilla chrome compat layer -- very similar to adapter.js
function setupRTC() {
    var RTC = null;
    if (navigator.mozGetUserMedia) {
        console.log('This appears to be Firefox');
        var version = parseInt(navigator.userAgent.match(/Firefox\/([0-9]+)\./)[1], 10);
        if (version >= 22) {
            RTC = {
                peerconnection: mozRTCPeerConnection,
                browser: 'firefox',
                getUserMedia: navigator.mozGetUserMedia.bind(navigator),
                attachMediaStream: function (element, stream) {
                    element[0].mozSrcObject = stream;
                    element[0].play();
                },
                pc_constraints: {}
            };
            if (!MediaStream.prototype.getVideoTracks)
                MediaStream.prototype.getVideoTracks = function () { return []; };
            if (!MediaStream.prototype.getAudioTracks)
                MediaStream.prototype.getAudioTracks = function () { return []; };
            RTCSessionDescription = mozRTCSessionDescription;
            RTCIceCandidate = mozRTCIceCandidate;
        }
    } else if (navigator.webkitGetUserMedia) {
        console.log('This appears to be Chrome');
        RTC = {
            peerconnection: webkitRTCPeerConnection,
            browser: 'chrome',
            getUserMedia: navigator.webkitGetUserMedia.bind(navigator),
            attachMediaStream: function (element, stream) {
                element.attr('src', webkitURL.createObjectURL(stream));
            },
//            pc_constraints: {} // FIVE-182
            pc_constraints: {'optional': [{'DtlsSrtpKeyAgreement': 'true'}]} // enable dtls support in canary
        };
        if (navigator.userAgent.indexOf('Android') != -1) {
            RTC.pc_constraints = {}; // disable DTLS on Android
        }
        if (!webkitMediaStream.prototype.getVideoTracks) {
            webkitMediaStream.prototype.getVideoTracks = function () {
                return this.videoTracks;
            };
        }
        if (!webkitMediaStream.prototype.getAudioTracks) {
            webkitMediaStream.prototype.getAudioTracks = function () {
                return this.audioTracks;
            };
        }
    }
    if (RTC === null) {
        try { console.log('Browser does not appear to be WebRTC-capable'); } catch (e) { }
    }
    return RTC;
}

function getUserMediaWithConstraints(um, resolution, bandwidth, fps) {
    var constraints = {audio: false, video: false};

    if (um.indexOf('video') >= 0) {
        constraints.video = {mandatory: {}};// same behaviour as true
    }
    if (um.indexOf('audio') >= 0) {
        constraints.audio = {};// same behaviour as true
    }
    if (um.indexOf('screen') >= 0) {
        constraints.video = {
            "mandatory": {
                "chromeMediaSource": "screen"
            }
        };
    }

    if (resolution && !constraints.video) {
        constraints.video = {mandatory: {}};// same behaviour as true
    }
    // see https://code.google.com/p/chromium/issues/detail?id=143631#c9 for list of supported resolutions
    switch (resolution) {
    // 16:9 first
    case '1080':
    case 'fullhd':
        constraints.video.mandatory.minWidth = 1920;
        constraints.video.mandatory.minHeight = 1080;
        constraints.video.mandatory.minAspectRatio = 1.77;
        break;
    case '720':
    case 'hd':
        constraints.video.mandatory.minWidth = 1280;
        constraints.video.mandatory.minHeight = 720;
        constraints.video.mandatory.minAspectRatio = 1.77;
        break;
    case '360':
        constraints.video.mandatory.minWidth = 640;
        constraints.video.mandatory.minHeight = 360;
        constraints.video.mandatory.minAspectRatio = 1.77;
        break;
    case '180':
        constraints.video.mandatory.minWidth = 320;
        constraints.video.mandatory.minHeight = 180;
        constraints.video.mandatory.minAspectRatio = 1.77;
        break;
        // 4:3
    case '960':
        constraints.video.mandatory.minWidth = 960;
        constraints.video.mandatory.minHeight = 720;
        break;
    case '640':
    case 'vga':
        constraints.video.mandatory.minWidth = 640;
        constraints.video.mandatory.minHeight = 480;
        break;
    case '320':
        constraints.video.mandatory.minWidth = 320;
        constraints.video.mandatory.minHeight = 240;
        break;
    default:
        if (navigator.userAgent.indexOf('Android') != -1) {
            constraints.video.mandatory.minWidth = 320;
            constraints.video.mandatory.minHeight = 240;
            constraints.video.mandatory.maxFrameRate = 15;
        }
        break;
    }

    if (bandwidth) { // doesn't work currently, see webrtc issue 1846
        if (!constraints.video) constraints.video = {mandatory: {}};//same behaviour as true
        constraints.video.optional = [{bandwidth: bandwidth}];
    }
    if (fps) { // for some cameras it might be necessary to request 30fps
        // so they choose 30fps mjpg over 10fps yuy2
        if (!constraints.video) constraints.video = {mandatory: {}};// same behaviour as tru;
        constraints.video.mandatory.minFrameRate = fps;
    }
 
    try {
        RTC.getUserMedia(constraints,
                function (stream) {
                    console.log('onUserMediaSuccess');
                    $(document).trigger('mediaready.jingle', [stream]);
                },
                function (error) {
                    console.warn('Failed to get access to local media. Error ', error);
                    $(document).trigger('mediafailure.jingle');
                });
    } catch (e) {
        console.error('GUM failed: ', e);
        $(document).trigger('mediafailure.jingle');
    }
}
/* jshint -W117 */
Strophe.addConnectionPlugin('jingle', {
    connection: null,
    sessions: {},
    jid2session: {},
    ice_config: {iceServers: []},
    pc_constraints: {},
    media_constraints: {
        mandatory: {
            'OfferToReceiveAudio': true,
            'OfferToReceiveVideo': true
        }
        // MozDontOfferDataChannel: true when this is firefox
    },
    localStream: null,

    init: function (conn) {
        this.connection = conn;
        if (this.connection.disco) {
            // http://xmpp.org/extensions/xep-0167.html#support
            // http://xmpp.org/extensions/xep-0176.html#support
            this.connection.disco.addFeature('urn:xmpp:jingle:1');
            this.connection.disco.addFeature('urn:xmpp:jingle:apps:rtp:1');
            this.connection.disco.addFeature('urn:xmpp:jingle:transports:ice-udp:1');
            this.connection.disco.addFeature('urn:xmpp:jingle:apps:rtp:audio');
            this.connection.disco.addFeature('urn:xmpp:jingle:apps:rtp:video');


            // this is dealt with by SDP O/A so we don't need to annouce this
            //this.connection.disco.addFeature('urn:xmpp:jingle:apps:rtp:rtcp-fb:0'); // XEP-0293
            //this.connection.disco.addFeature('urn:xmpp:jingle:apps:rtp:rtp-hdrext:0'); // XEP-0294
            this.connection.disco.addFeature('urn:ietf:rfc:5761'); // rtcp-mux
            //this.connection.disco.addFeature('urn:ietf:rfc:5888'); // a=group, e.g. bundle
            //this.connection.disco.addFeature('urn:ietf:rfc:5576'); // a=ssrc
        }
        this.connection.addHandler(this.onJingle.bind(this), 'urn:xmpp:jingle:1', 'iq', 'set', null, null);
    },
    onJingle: function (iq) {
        var sid = $(iq).find('jingle').attr('sid');
        var action = $(iq).find('jingle').attr('action');
        // send ack first
        var ack = $iq({type: 'result',
              to: iq.getAttribute('from'),
              id: iq.getAttribute('id')
        });
        console.log('on jingle ' + action);
        var sess = this.sessions[sid];
        if ('session-initiate' != action) {
            if (sess === null) {
                ack.type = 'error';
                ack.c('error', {type: 'cancel'})
                   .c('item-not-found', {xmlns: 'urn:ietf:params:xml:ns:xmpp-stanzas'}).up()
                   .c('unknown-session', {xmlns: 'urn:xmpp:jingle:errors:1'});
                this.connection.send(ack);
                return true;
            }
            // compare from to sess.peerjid (bare jid comparison for later compat with message-mode)
            // local jid is not checked
            if (Strophe.getBareJidFromJid(iq.getAttribute('from')) != Strophe.getBareJidFromJid(sess.peerjid)) {
                console.warn('jid mismatch for session id', sid, iq.getAttribute('from'), sess.peerjid);
                ack.type = 'error';
                ack.c('error', {type: 'cancel'})
                   .c('item-not-found', {xmlns: 'urn:ietf:params:xml:ns:xmpp-stanzas'}).up()
                   .c('unknown-session', {xmlns: 'urn:xmpp:jingle:errors:1'});
                this.connection.send(ack);
                return true;
            }
        } else if (sess !== undefined) {
            // existing session with same session id
            // this might be out-of-order if the sess.peerjid is the same as from
            ack.type = 'error';
            ack.c('error', {type: 'cancel'})
               .c('service-unavailable', {xmlns: 'urn:ietf:params:xml:ns:xmpp-stanzas'}).up();
            console.warn('duplicate session id', sid);
            return true;
        }
        // FIXME: check for a defined action
        this.connection.send(ack);
        // see http://xmpp.org/extensions/xep-0166.html#concepts-session
        switch (action) {
        case 'session-initiate':
            sess = new JingleSession($(iq).attr('to'), $(iq).find('jingle').attr('sid'), this.connection);
            // configure session
            if (this.localStream) {
                sess.localStreams.push(this.localStream);
            }
            sess.media_constraints = this.media_constraints;
            sess.pc_constraints = this.pc_constraints;
            sess.ice_config = this.ice_config;

            sess.initiate($(iq).attr('from'), false);
            // FIXME: setRemoteDescription should only be done when this call is to be accepted
            sess.setRemoteDescription($(iq).find('>jingle'), 'offer');

            this.sessions[sess.sid] = sess;
            this.jid2session[sess.peerjid] = sess;

            // the callback should either 
            // .sendAnswer and .accept
            // or .sendTerminate -- not necessarily synchronus
            $(document).trigger('callincoming.jingle', [sess.sid]);
            break;
        case 'session-accept':
            sess.setRemoteDescription($(iq).find('>jingle'), 'answer');
            sess.accept();
            break;
        case 'session-terminate':
            console.log('terminating...');
            sess.terminate();
            this.terminate(sess.sid);
            if ($(iq).find('>jingle>reason').length) {
                $(document).trigger('callterminated.jingle', [
                    sess.sid,
                    $(iq).find('>jingle>reason>:first')[0].tagName,
                    $(iq).find('>jingle>reason>text').text()
                ]);
            } else {
                $(document).trigger('callterminated.jingle', [sess.sid]);
            }
            break;
        case 'transport-info':
            sess.addIceCandidate($(iq).find('>jingle>content'));
            break;
        case 'session-info':
            var affected;
            if ($(iq).find('>jingle>ringing[xmlns="urn:xmpp:jingle:apps:rtp:info:1"]').length) {
                $(document).trigger('ringing.jingle', [sess.sid]);
            } else if ($(iq).find('>jingle>mute[xmlns="urn:xmpp:jingle:apps:rtp:info:1"]').length) {
                affected = $(iq).find('>jingle>mute[xmlns="urn:xmpp:jingle:apps:rtp:info:1"]').attr('name');
                $(document).trigger('mute.jingle', [sess.sid, affected]);
            } else if ($(iq).find('>jingle>unmute[xmlns="urn:xmpp:jingle:apps:rtp:info:1"]').length) {
                affected = $(iq).find('>jingle>unmute[xmlns="urn:xmpp:jingle:apps:rtp:info:1"]').attr('name');
                $(document).trigger('unmute.jingle', [sess.sid, affected]);
            }
            break;
        case 'addsource': // FIXME: proprietary
            sess.addSource($(iq).find('>jingle>content'));
            break;
        case 'removesource': // FIXME: proprietary
            sess.removeSource($(iq).find('>jingle>content'));
            break;
        default:
            console.warn('jingle action not implemented', action);
            break;
        }
        return true;
    },
    initiate: function (peerjid, myjid) { // initiate a new jinglesession to peerjid
        var sess = new JingleSession(myjid,
                                     Math.random().toString(36).substr(2, 12), // random string
                                     this.connection);
        // configure session
        if (this.localStream) {
            sess.localStreams.push(this.localStream);
        }
        sess.media_constraints = this.media_constraints;
        sess.pc_constraints = this.pc_constraints;
        sess.ice_config = this.ice_config;

        sess.initiate(peerjid, true);
        this.sessions[sess.sid] = sess;
        this.jid2session[sess.peerjid] = sess;
        sess.sendOffer();
        return sess;
    },
    terminate: function (sid, reason, text) { // terminate by sessionid (or all sessions)
        if (sid === null || sid === undefined) {
            for (sid in this.sessions) {
                if (this.sessions[sid].state != 'ended') {
                    this.sessions[sid].sendTerminate(reason || (!this.sessions[sid].active()) ? 'cancel' : null, text);
                    this.sessions[sid].terminate();
                }
                delete this.jid2session[this.sessions[sid].peerjid];
                delete this.sessions[sid];
            }
        } else if (this.sessions.hasOwnProperty(sid)) {
            if (this.sessions[sid].state != 'ended') {
                this.sessions[sid].sendTerminate(reason || (!this.sessions[sid].active()) ? 'cancel' : null, text);
                this.sessions[sid].terminate();
            }
            delete this.jid2session[this.sessions[sid].peerjid];
            delete this.sessions[sid];
        }
    },
    terminateByJid: function (jid) {
        if (this.jid2session.hasOwnProperty(jid)) {
            var sess = this.jid2session[jid];
            if (sess) {
                sess.terminate();
                console.log('peer went away silently', jid);
                delete this.sessions[sess.sid];
                delete this.jid2session[jid];
                $(document).trigger('callterminated.jingle', [sess.sid, 'gone']);
            }
        }
    },
    getStunAndTurnCredentials: function () {
        // get stun and turn configuration from server via xep-0215
        // uses time-limited credentials as described in
        // http://tools.ietf.org/html/draft-uberti-behave-turn-rest-00
        //
        // see https://code.google.com/p/prosody-modules/source/browse/mod_turncredentials/mod_turncredentials.lua
        // for a prosody module which implements this
        //
        // currently, this doesn't work with updateIce and therefore credentials with a long
        // validity have to be fetched before creating the peerconnection
        // TODO: implement refresh via updateIce as described in
        //      https://code.google.com/p/webrtc/issues/detail?id=1650
        this.connection.sendIQ(
            $iq({type: 'get', to: this.connection.domain})
                .c('services', {xmlns: 'urn:xmpp:extdisco:1'}).c('service', {host: 'turn.' + this.connection.domain}),
            function (res) {
                var iceservers = [];
                $(res).find('>services>service').each(function (idx, el) {
                    el = $(el);
                    var dict = {};
                    switch (el.attr('type')) {
                    case 'stun':
                        dict.url = 'stun:' + el.attr('host');
                        if (el.attr('port')) {
                            dict.url += ':' + el.attr('port');
                        }
                        iceservers.push(dict);
                        break;
                    case 'turn':
                        dict.url = 'turn:';
                        if (el.attr('username')) { // https://code.google.com/p/webrtc/issues/detail?id=1508
                            if (navigator.userAgent.match(/Chrom(e|ium)\/([0-9]+)\./) && parseInt(navigator.userAgent.match(/Chrom(e|ium)\/([0-9]+)\./)[2], 10) < 28) {
                                dict.url += el.attr('username') + '@';
                            } else {
                                dict.username = el.attr('username'); // only works in M28
                            }
                        }
                        dict.url += el.attr('host');
                        if (el.attr('port') && el.attr('port') != '3478') {
                            dict.url += ':' + el.attr('port');
                        }
                        if (el.attr('transport') && el.attr('transport') != 'udp') {
                            dict.url += '?transport=' + el.attr('transport');
                        }
                        if (el.attr('password')) {
                            dict.credential = el.attr('password');
                        }
                        iceservers.push(dict);
                        break;
                    }
                });
                this.ice_config.iceServers = iceservers;
            },
            function (err) {
                console.warn('getting turn credentials failed', err);
                console.warn('is mod_turncredentials or similar installed?');
            }
        );
        // implement push?
    }
});
/* jshint -W117 */
// SDP STUFF
function SDP(sdp) {
    this.media = sdp.split('\r\nm=');
    for (var i = 1; i < this.media.length; i++) {
        this.media[i] = 'm=' + this.media[i];
        if (i != this.media.length - 1) {
            this.media[i] += '\r\n';
        }
    }
    this.session = this.media.shift() + '\r\n';
    this.raw = this.session + this.media.join('');
}

// remove iSAC and CN from SDP
SDP.prototype.mangle = function () {
    var i, j, mline, lines, rtpmap, newdesc;
    for (i = 0; i < this.media.length; i++) {
        lines = this.media[i].split('\r\n');
        lines.pop(); // remove empty last element
        mline = SDPUtil.parse_mline(lines.shift());
        if (mline.media != 'audio')
            continue;
        newdesc = '';
        mline.fmt.length = 0;
        for (j = 0; j < lines.length; j++) {
            if (lines[j].substr(0, 9) == 'a=rtpmap:') {
                rtpmap = SDPUtil.parse_rtpmap(lines[j]);
                if (rtpmap.name == 'CN' || rtpmap.name == 'ISAC')
                    continue;
                mline.fmt.push(rtpmap.id);
                newdesc += lines[j] + '\r\n';
            } else {
                newdesc += lines[j] + '\r\n';
            }
        }
        this.media[i] = SDPUtil.build_mline(mline) + '\r\n';
        this.media[i] += newdesc;
    }
    this.raw = this.session + this.media.join('');
};

// add content's to a jingle element
SDP.prototype.toJingle = function (elem, thecreator) {
    var i, j, k, mline, ssrc, rtpmap, tmp, line, lines;
    var ob = this;
    // new bundle plan
    if (SDPUtil.find_line(this.session, 'a=group:')) {
        lines = SDPUtil.find_lines(this.session, 'a=group:');
        for (i = 0; i < lines.length; i++) {
            tmp = lines[i].split(' ');
            var semantics = tmp.shift().substr(8);
            // new plan
            elem.c('group', {xmlns: 'urn:xmpp:jingle:apps:grouping:0', type: semantics, semantics:semantics});
            for (j = 0; j < tmp.length; j++) {
                elem.c('content', {name: tmp[j]}).up();
            }
            elem.up();

            // temporary plan, to be removed
            elem.c('group', {xmlns: 'urn:ietf:rfc:5888', type: semantics});
            for (j = 0; j < tmp.length; j++) {
                elem.c('content', {name: tmp[j]}).up();
            }
            elem.up();
        }
    }
    // old bundle plan, to be removed
    var bundle = [];
    if (SDPUtil.find_line(this.session, 'a=group:BUNDLE')) {
        bundle = SDPUtil.find_line(this.session, 'a=group:BUNDLE ').split(' ');
        bundle.shift();
    }
    for (i = 0; i < this.media.length; i++) {
        mline = SDPUtil.parse_mline(this.media[i].split('\r\n')[0]);
        if (!(mline.media == 'audio' || mline.media == 'video')) {
            continue;
        }
        if (SDPUtil.find_line(this.media[i], 'a=ssrc:')) {
            ssrc = SDPUtil.find_line(this.media[i], 'a=ssrc:').substring(7).split(' ')[0]; // take the first
        } else {
            ssrc = false;
        }

        elem.c('content', {creator: thecreator, name: mline.media});
        if (SDPUtil.find_line(this.media[i], 'a=mid:')) {
            // prefer identifier from a=mid if present
            var mid = SDPUtil.parse_mid(SDPUtil.find_line(this.media[i], 'a=mid:'));
            elem.attrs({ name: mid });

            // old BUNDLE plan, to be removed
            if (bundle.indexOf(mid) != -1) {
                elem.c('bundle', {xmlns: 'http://estos.de/ns/bundle'}).up();
                bundle.splice(bundle.indexOf(mid), 1);
            }
        }
        if (SDPUtil.find_line(this.media[i], 'a=rtpmap:').length) {
            elem.c('description',
                 {xmlns: 'urn:xmpp:jingle:apps:rtp:1',
                  media: mline.media });
            if (ssrc) {
                elem.attrs({ssrc: ssrc});
            }
            for (j = 0; j < mline.fmt.length; j++) {
                rtpmap = SDPUtil.find_line(this.media[i], 'a=rtpmap:' + mline.fmt[j]);
                elem.c('payload-type', SDPUtil.parse_rtpmap(rtpmap));
                // put any 'a=fmtp:' + mline.fmt[j] lines into <param name=foo value=bar/>
                if (SDPUtil.find_line(this.media[i], 'a=fmtp:' + mline.fmt[j])) {
                    tmp = SDPUtil.parse_fmtp(SDPUtil.find_line(this.media[i], 'a=fmtp:' + mline.fmt[j]));
                    for (k = 0; k < tmp.length; k++) {
                        elem.c('parameter', tmp[k]).up();
                    }
                }
                this.RtcpFbToJingle(this.media[i], elem, mline.fmt[j]); // XEP-0293 -- map a=rtcp-fb

                elem.up();
            }
            if (SDPUtil.find_line(this.media[i], 'a=crypto:', this.session)) {
                elem.c('encryption', {required: 1});
                var crypto = SDPUtil.find_lines(this.media[i], 'a=crypto:', this.session);
                crypto.forEach(function(line) {
                    elem.c('crypto', SDPUtil.parse_crypto(line)).up();
                });
                elem.up(); // end of encryption
            }

            if (ssrc) {
                // new style mapping
                elem.c('source', { ssrc: ssrc, xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });
                // FIXME: group by ssrc and support multiple different ssrcs
                var ssrclines = SDPUtil.find_lines(this.media[i], 'a=ssrc:');
                ssrclines.forEach(function(line) {
                    idx = line.indexOf(' ');
                    var linessrc = line.substr(0, idx).substr(7);
                    if (linessrc != ssrc) {
                        elem.up();
                        ssrc = linessrc;
                        elem.c('source', { ssrc: ssrc, xmlns: 'urn:xmpp:jingle:apps:rtp:ssma:0' });
                    }
                    var kv = line.substr(idx + 1);
                    elem.c('parameter');
                    if (kv.indexOf(':') == -1) {
                        elem.attrs({ name: kv });
                    } else {
                        elem.attrs({ name: kv.split(':', 2)[0] });
                        elem.attrs({ value: kv.split(':', 2)[1] });
                    }
                    elem.up();
                });
                elem.up();

                // old proprietary mapping, to be removed at some point
                tmp = SDPUtil.parse_ssrc(this.media[i]);
                tmp.xmlns = 'http://estos.de/ns/ssrc';
                tmp.ssrc = ssrc;
                elem.c('ssrc', tmp).up(); // ssrc is part of description
            }

            if (SDPUtil.find_line(this.media[i], 'a=rtcp-mux')) {
                elem.c('rtcp-mux').up();
            }

            // XEP-0293 -- map a=rtcp-fb:*
            this.RtcpFbToJingle(this.media[i], elem, '*');

            // XEP-0294
            if (SDPUtil.find_line(this.media[i], 'a=extmap:')) {
                lines = SDPUtil.find_lines(this.media[i], 'a=extmap:');
                for (j = 0; j < lines.length; j++) {
                    tmp = SDPUtil.parse_extmap(lines[j]);
                    elem.c('rtp-hdrext', { xmlns: 'urn:xmpp:jingle:apps:rtp:rtp-hdrext:0',
                                    uri: tmp.uri,
                                    id: tmp.value });
                    if (tmp.hasOwnProperty('direction')) {
                        switch (tmp.direction) {
                        case 'sendonly':
                            elem.attrs({senders: 'responder'});
                            break;
                        case 'recvonly':
                            elem.attrs({senders: 'initiator'});
                            break;
                        case 'sendrecv':
                            elem.attrs({senders: 'both'});
                            break;
                        case 'inactive':
                            elem.attrs({senders: 'none'});
                            break;
                        }
                    }
                    // TODO: handle params
                    elem.up();
                }
            }
            elem.up(); // end of description
        }

        elem.c('transport', {xmlns: 'urn:xmpp:jingle:transports:ice-udp:1'});
        // XEP-0320
        var fingerprints = SDPUtil.find_lines(this.media[i], 'a=fingerprint:', this.session);
        fingerprints.forEach(function(line) {
            tmp = SDPUtil.parse_fingerprint(line);
            tmp.xmlns = 'urn:xmpp:tmp:jingle:apps:dtls:0';
            // tmp.xmlns = 'urn:xmpp:jingle:apps:dtls:0'; -- FIXME: update receivers first
            elem.c('fingerprint').t(tmp.fingerprint);
            delete tmp.fingerprint;
            line = SDPUtil.find_line(ob.media[i], 'a=setup:', ob.session);
            if (line) {
                tmp.setup = line.substr(8);
            }
            elem.attrs(tmp);
            elem.up();
        });
        tmp = SDPUtil.iceparams(this.media[i], this.session);
        if (tmp) {
            elem.attrs(tmp);
            // XEP-0176
            if (SDPUtil.find_line(this.media[i], 'a=candidate:', this.session)) { // add any a=candidate lines
                lines = SDPUtil.find_lines(this.media[i], 'a=candidate:', this.session);
                for (j = 0; j < lines.length; j++) {
                    tmp = SDPUtil.candidateToJingle(lines[j]);
                    elem.c('candidate', tmp).up();
                }
            }
            elem.up(); // end of transport
        }

        if (SDPUtil.find_line(this.media[i], 'a=sendrecv', this.session)) {
            elem.attrs({senders: 'both'});
        } else if (SDPUtil.find_line(this.media[i], 'a=sendonly', this.session)) {
            elem.attrs({senders: 'initiator'});
        } else if (SDPUtil.find_line(this.media[i], 'a=recvonly', this.session)) {
            elem.attrs({senders: 'responder'});
        } else if (SDPUtil.find_line(this.media[i], 'a=inactive', this.session)) {
            elem.attrs({senders: 'none'});
        }
        if (mline.port == '0') {
            // estos hack to reject an m-line
            elem.attrs({senders: 'rejected'});
        }
        elem.up(); // end of content
    }
    elem.up();
    return elem;
};

SDP.prototype.RtcpFbToJingle = function (sdp, elem, payloadtype) { // XEP-0293
    var lines = SDPUtil.find_lines(sdp, 'a=rtcp-fb:' + payloadtype);
    for (var i = 0; i < lines.length; i++) {
        var tmp = SDPUtil.parse_rtcpfb(lines[i]);
        if (tmp.type == 'trr-int') {
            elem.c('rtcp-fb-trr-int', {xmlns: 'urn:xmpp:jingle:apps:rtp:rtcp-fb:0', value: tmp.params[0]});
            elem.up();
        } else {
            elem.c('rtcp-fb', {xmlns: 'urn:xmpp:jingle:apps:rtp:rtcp-fb:0', type: tmp.type});
            if (tmp.params.length > 0) {
                elem.attrs({'subtype': tmp.params[0]});
            }
            elem.up();
        }
    }
};

SDP.prototype.RtcpFbFromJingle = function (elem, payloadtype) { // XEP-0293
    var media = '';
    var tmp = elem.find('>rtcp-fb-trr-int[xmlns="urn:xmpp:jingle:apps:rtp:rtcp-fb:0"]');
    if (tmp.length) {
        media += 'a=rtcp-fb:' + '*' + ' ' + 'trr-int' + ' ';
        if (tmp.attr('value')) {
            media += tmp.attr('value');
        } else {
            media += '0';
        }
        media += '\r\n';
    }
    tmp = elem.find('>rtcp-fb[xmlns="urn:xmpp:jingle:apps:rtp:rtcp-fb:0"]');
    tmp.each(function () {
        media += 'a=rtcp-fb:' + payloadtype + ' ' + $(this).attr('type');
        if ($(this).attr('subtype')) {
            media += ' ' + $(this).attr('subtype');
        }
        media += '\r\n';
    });
    return media;
};

// construct an SDP from a jingle stanza
SDP.prototype.fromJingle = function (jingle) {
    var obj = this;
    this.raw = 'v=0\r\n' +
        'o=- ' + '1923518516' + ' 2 IN IP4 0.0.0.0\r\n' +// FIXME
        's=-\r\n' +
        't=0 0\r\n';
    // http://tools.ietf.org/html/draft-ietf-mmusic-sdp-bundle-negotiation-04#section-8
    if ($(jingle).find('>group[xmlns="urn:xmpp:jingle:apps:grouping:0"]').length) {
        try {
        $(jingle).find('>group[xmlns="urn:xmpp:jingle:apps:grouping:0"]').each(function (idx, group) {
            var contents = $(group).find('>content').map(function (idx, content) {
                return $(content).attr('name');
            }).get();
            if (contents.length > 0) {
                obj.raw += 'a=group:' + ($(group).attr('semantics') || $(group).attr('type')) + ' ' + contents.join(' ') + '\r\n';
            }
        });
        } catch (e) { console.error(e.toString()); }
    } else if ($(jingle).find('>group[xmlns="urn:ietf:rfc:5888"]').length) {
        // temporary namespace, not to be used. to be removed soon.
        $(jingle).find('>group[xmlns="urn:ietf:rfc:5888"]').each(function (idx, group) {
            var contents = $(group).find('>content').map(function (idx, content) {
                return $(content).attr('name');
            }).get();
            if ($(group).attr('type') !== null && contents.length > 0) {
                obj.raw += 'a=group:' + $(group).attr('type') + ' ' + contents.join(' ') + '\r\n';
            }
        });
    } else {
        // for backward compability, to be removed soon
        // assume all contents are in the same bundle group, can be improved upon later
        var bundle = $(jingle).find('>content').filter(function (idx, content) {
            //elem.c('bundle', {xmlns:'http://estos.de/ns/bundle'});
            return $(content).find('>bundle').length > 0;
        }).map(function (idx, content) {
            return $(content).attr('name');
        }).get();
        if (bundle.length) {
            this.raw += 'a=group:BUNDLE ' + bundle.join(' ') + '\r\n';
        }
    }

    this.session = this.raw;
    jingle.find('>content').each(function () {
        var m = obj.jingle2media($(this));
        obj.media.push(m);
    });

    // reconstruct msid-semantic -- apparently not necessary
    /*
    var msid = SDPUtil.parse_ssrc(this.raw);
    if (msid.hasOwnProperty('mslabel')) {
        this.session += "a=msid-semantic: WMS " + msid.mslabel + "\r\n";
    }
    */

    this.raw = this.session + this.media.join('');
};

// translate a jingle content element into an an SDP media part
SDP.prototype.jingle2media = function (content) {
    var media = '',
        desc = content.find('description'),
        ssrc = desc.attr('ssrc'),
        self = this,
        tmp;

    tmp = { media: desc.attr('media') };
    tmp.port = '1';
    if (content.attr('senders') == 'rejected') {
        // estos hack to reject an m-line.
        tmp.port = '0';
    }
    if (content.find('>transport>fingerprint').length || desc.find('encryption').length) {
        tmp.proto = 'RTP/SAVPF';
    } else {
        tmp.proto = 'RTP/AVPF';
    }
    tmp.fmt = desc.find('payload-type').map(function () { return $(this).attr('id'); }).get();
    media += SDPUtil.build_mline(tmp) + '\r\n';
    media += 'c=IN IP4 0.0.0.0\r\n';
    media += 'a=rtcp:1 IN IP4 0.0.0.0\r\n';
    tmp = content.find('>transport[xmlns="urn:xmpp:jingle:transports:ice-udp:1"]');
    if (tmp.length) {
        if (tmp.attr('ufrag')) {
            media += SDPUtil.build_iceufrag(tmp.attr('ufrag')) + '\r\n';
        }
        if (tmp.attr('pwd')) {
            media += SDPUtil.build_icepwd(tmp.attr('pwd')) + '\r\n';
        }
        tmp.find('>fingerprint').each(function () {
            // FIXME: check namespace at some point
            media += 'a=fingerprint:' + $(this).attr('hash');
            media += ' ' + $(this).text();
            media += '\r\n';
            if ($(this).attr('setup')) {
                media += 'a=setup:' + $(this).attr('setup') + '\r\n';
            }
        });
    }
    switch (content.attr('senders')) {
    case 'initiator':
        media += 'a=sendonly\r\n';
        break;
    case 'responder':
        media += 'a=recvonly\r\n';
        break;
    case 'none':
        media += 'a=inactive\r\n';
        break;
    case 'both':
        media += 'a=sendrecv\r\n';
        break;
    }
    media += 'a=mid:' + content.attr('name') + '\r\n';

    // <description><rtcp-mux/></description>
    // see http://code.google.com/p/libjingle/issues/detail?id=309 -- no spec though
    // and http://mail.jabber.org/pipermail/jingle/2011-December/001761.html
    if (desc.find('rtcp-mux').length) {
        media += 'a=rtcp-mux\r\n';
    }

    if (desc.find('encryption').length) {
        desc.find('encryption>crypto').each(function () {
            media += 'a=crypto:' + $(this).attr('tag');
            media += ' ' + $(this).attr('crypto-suite');
            media += ' ' + $(this).attr('key-params');
            if ($(this).attr('session-params')) {
                media += ' ' + $(this).attr('session-params');
            }
            media += '\r\n';
        });
    }
    desc.find('payload-type').each(function () {
        media += SDPUtil.build_rtpmap(this) + '\r\n';
        if ($(this).find('>parameter').length) {
            media += 'a=fmtp:' + $(this).attr('id') + ' ';
            media += $(this).find('parameter').map(function () { return ($(this).attr('name') ? ($(this).attr('name') + '=') : '') + $(this).attr('value'); }).get().join(';');
            media += '\r\n';
        }
        // xep-0293
        media += self.RtcpFbFromJingle($(this), $(this).attr('id'));
    });

    // xep-0293
    media += self.RtcpFbFromJingle(desc, '*');

    // xep-0294
    tmp = desc.find('>rtp-hdrext[xmlns="urn:xmpp:jingle:apps:rtp:rtp-hdrext:0"]');
    tmp.each(function () {
        media += 'a=extmap:' + $(this).attr('id') + ' ' + $(this).attr('uri') + '\r\n';
    });

    content.find('>transport[xmlns="urn:xmpp:jingle:transports:ice-udp:1"]>candidate').each(function () {
        media += SDPUtil.candidateFromJingle(this);
    });

    tmp = content.find('description>source[xmlns="urn:xmpp:jingle:apps:rtp:ssma:0"]');
    tmp.each(function () {
        var ssrc = $(this).attr('ssrc');
        $(this).find('>parameter').each(function () {
            media += 'a=ssrc:' + ssrc + ' ' + $(this).attr('name');
            if ($(this).attr('value') && $(this).attr('value').length)
                media += ':' + $(this).attr('value');
            media += '\r\n';
        });
    });

    if (tmp.length === 0) {
        // fallback to proprietary mapping of a=ssrc lines
        tmp = content.find('description>ssrc[xmlns="http://estos.de/ns/ssrc"]');
        if (tmp.length) {
            media += 'a=ssrc:' + ssrc + ' cname:' + tmp.attr('cname') + '\r\n';
            media += 'a=ssrc:' + ssrc + ' msid:' + tmp.attr('msid') + '\r\n';
            media += 'a=ssrc:' + ssrc + ' mslabel:' + tmp.attr('mslabel') + '\r\n';
            media += 'a=ssrc:' + ssrc + ' label:' + tmp.attr('label') + '\r\n';
        }
    }
    return media;
};

SDPUtil = {
    iceparams: function (mediadesc, sessiondesc) {
        var data = null;
        if (SDPUtil.find_line(mediadesc, 'a=ice-ufrag:', sessiondesc) &&
            SDPUtil.find_line(mediadesc, 'a=ice-pwd:', sessiondesc)) {
            data = {
                ufrag: SDPUtil.parse_iceufrag(SDPUtil.find_line(mediadesc, 'a=ice-ufrag:', sessiondesc)),
                pwd: SDPUtil.parse_icepwd(SDPUtil.find_line(mediadesc, 'a=ice-pwd:', sessiondesc))
            };
        }
        return data;
    },
    parse_iceufrag: function (line) {
        return line.substring(12);
    },
    build_iceufrag: function (frag) {
        return 'a=ice-ufrag:' + frag;
    },
    parse_icepwd: function (line) {
        return line.substring(10);
    },
    build_icepwd: function (pwd) {
        return 'a=ice-pwd:' + pwd;
    },
    parse_mid: function (line) {
        return line.substring(6);
    },
    parse_mline: function (line) {
        var parts = line.substring(2).split(' '),
        data = {};
        data.media = parts.shift();
        data.port = parts.shift();
        data.proto = parts.shift();
        if (parts[parts.length - 1] === '') { // trailing whitespace
            parts.pop();
        }
        data.fmt = parts;
        return data;
    },
    build_mline: function (mline) {
        return 'm=' + mline.media + ' ' + mline.port + ' ' + mline.proto + ' ' + mline.fmt.join(' ');
    },
    parse_rtpmap: function (line) {
        var parts = line.substring(9).split(' '),
            data = {};
        data.id = parts.shift();
        parts = parts[0].split('/');
        data.name = parts.shift();
        data.clockrate = parts.shift();
        data.channels = parts.length ? parts.shift() : '1';
        return data;
    },
    build_rtpmap: function (el) {
        var line = 'a=rtpmap:' + el.getAttribute('id') + ' ' + el.getAttribute('name') + '/' + el.getAttribute('clockrate');
        if (el.getAttribute('channels') && el.getAttribute('channels') != '1') {
            line += '/' + el.getAttribute('channels');
        }
        return line;
    },
    parse_crypto: function (line) {
        var parts = line.substring(9).split(' '),
        data = {};
        data.tag = parts.shift();
        data['crypto-suite'] = parts.shift();
        data['key-params'] = parts.shift();
        if (parts.length) {
            data['session-params'] = parts.join(' ');
        }
        return data;
    },
    parse_fingerprint: function (line) { // RFC 4572
        var parts = line.substring(14).split(' '),
        data = {};
        data.hash = parts.shift();
        data.fingerprint = parts.shift();
        // TODO assert that fingerprint satisfies 2UHEX *(":" 2UHEX) ?
        return data;
    },
    parse_fmtp: function (line) {
        var parts = line.split(' '),
            i, key, value,
            data = [];
        parts.shift();
        parts = parts.join(' ').split(';');
        for (i = 0; i < parts.length; i++) {
            key = parts[i].split('=')[0];
            while (key.length && key[0] == ' ') {
                key = key.substring(1);
            }
            value = parts[i].split('=')[1];
            if (key && value) {
                data.push({name: key, value: value});
            } else if (key) {
                // rfc 4733 (DTMF) style stuff
                data.push({name: '', value: key});
            }
        }
        return data;
    },
    parse_icecandidate: function (line) {
        var candidate = {},
            elems = line.split(' ');
        candidate.foundation = elems[0].substring(12);
        candidate.component = elems[1];
        candidate.protocol = elems[2].toLowerCase();
        candidate.priority = elems[3];
        candidate.ip = elems[4];
        candidate.port = elems[5];
        // elems[6] => "typ"
        candidate.type = elems[7];
        for (var i = 8; i < elems.length; i += 2) {
            switch (elems[i]) {
            case 'raddr':
                candidate['rel-addr'] = elems[i + 1];
                break;
            case 'rport':
                candidate['rel-port'] = elems[i + 1];
                break;
            case 'generation':
                candidate.generation = elems[i + 1];
                break;
            default: // TODO
                console.log('parse_icecandidate not translating "' + elems[i] + '" = "' + elems[i + 1] + '"');
            }
        }
        candidate.network = '1';
        candidate.id = Math.random().toString(36).substr(2, 10); // not applicable to SDP -- FIXME: should be unique, not just random
        return candidate;
    },
    build_icecandidate: function (cand) {
        var line = ['a=candidate:' + cand.foundation, cand.component, cand.protocol, cand.priority, cand.ip, cand.port, 'typ', cand.type].join(' ');
        line += ' ';
        switch (cand.type) {
        case 'srflx':
        case 'prflx':
        case 'relay':
            if (cand.hasOwnAttribute('rel-addr') && cand.hasOwnAttribute('rel-port')) {
                line += 'raddr';
                line += ' ';
                line += cand['rel-addr'];
                line += ' ';
                line += 'rport';
                line += ' ';
                line += cand['rel-port'];
                line += ' ';
            }
            break;
        }
        line += 'generation';
        line += ' ';
        line += cand.hasOwnAttribute('generation') ? cand.generation : '0';
        return line;
    },
    parse_ssrc: function (desc) {
        // proprietary mapping of a=ssrc lines
        // TODO: see "Jingle RTP Source Description" by Juberti and P. Thatcher on google docs
        // and parse according to that
        var lines = desc.split('\r\n'),
            data = {};
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].substring(0, 7) == 'a=ssrc:') {
                var idx = lines[i].indexOf(' ');
                data[lines[i].substr(idx + 1).split(':', 2)[0]] = lines[i].substr(idx + 1).split(':', 2)[1];
            }
        }
        return data;
    },
    parse_rtcpfb: function (line) {
        var parts = line.substr(10).split(' ');
        var data = {};
        data.pt = parts.shift();
        data.type = parts.shift();
        data.params = parts;
        return data;
    },
    parse_extmap: function (line) {
        var parts = line.substr(9).split(' ');
        var data = {};
        data.value = parts.shift();
        if (data.value.indexOf('/') != -1) {
            data.direction = data.value.substr(data.value.indexOf('/') + 1);
            data.value = data.value.substr(0, data.value.indexOf('/'));
        } else {
            data.direction = 'both';
        }
        data.uri = parts.shift();
        data.params = parts;
        return data;
    },
    find_line: function (haystack, needle, sessionpart) {
        var lines = haystack.split('\r\n');
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].substring(0, needle.length) == needle) {
                return lines[i];
            }
        }
        if (!sessionpart) {
            return false;
        }
        // search session part
        lines = sessionpart.split('\r\n');
        for (var j = 0; j < lines.length; j++) {
            if (lines[j].substring(0, needle.length) == needle) {
                return lines[j];
            }
        }
        return false;
    },
    find_lines: function (haystack, needle, sessionpart) {
        var lines = haystack.split('\r\n'),
            needles = [];
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].substring(0, needle.length) == needle)
                needles.push(lines[i]);
        }
        if (needles.length || !sessionpart) {
            return needles;
        }
        // search session part
        lines = sessionpart.split('\r\n');
        for (var j = 0; j < lines.length; j++) {
            if (lines[j].substring(0, needle.length) == needle) {
                needles.push(lines[j]);
            }
        }
        return needles;
    },
    candidateToJingle: function (line) {
        // a=candidate:2979166662 1 udp 2113937151 192.168.2.100 57698 typ host generation 0
        //      <candidate component=... foundation=... generation=... id=... ip=... network=... port=... priority=... protocol=... type=.../>
        if (line.substring(0, 12) != 'a=candidate:') {
            console.log('parseCandidate called with a line that is not a candidate line');
            console.log(line);
            return null;
        }
        if (line.substring(line.length - 2) == '\r\n') // chomp it
            line = line.substring(0, line.length - 2);
        var candidate = {},
            elems = line.split(' '),
            i;
        if (elems[6] != 'typ') {
            console.log('did not find typ in the right place');
            console.log(line);
            return null;
        }
        candidate.foundation = elems[0].substring(12);
        candidate.component = elems[1];
        candidate.protocol = elems[2].toLowerCase();
        candidate.priority = elems[3];
        candidate.ip = elems[4];
        candidate.port = elems[5];
        // elems[6] => "typ"
        candidate.type = elems[7];
        for (i = 8; i < elems.length; i += 2) {
            switch (elems[i]) {
            case 'raddr':
                candidate['rel-addr'] = elems[i + 1];
                break;
            case 'rport':
                candidate['rel-port'] = elems[i + 1];
                break;
            case 'generation':
                candidate.generation = elems[i + 1];
                break;
            default: // TODO
                console.log('not translating "' + elems[i] + '" = "' + elems[i + 1] + '"');
            }
        }
        candidate.network = '1';
        candidate.id = Math.random().toString(36).substr(2, 10); // not applicable to SDP -- FIXME: should be unique, not just random
        return candidate;
    },
    candidateFromJingle: function (cand) {
        var line = 'a=candidate:';
        line += cand.getAttribute('foundation');
        line += ' ';
        line += cand.getAttribute('component');
        line += ' ';
        line += cand.getAttribute('protocol'); //.toUpperCase(); // chrome M23 doesn't like this
        line += ' ';
        line += cand.getAttribute('priority');
        line += ' ';
        line += cand.getAttribute('ip');
        line += ' ';
        line += cand.getAttribute('port');
        line += ' ';
        line += 'typ';
        line += ' ' + cand.getAttribute('type');
        line += ' ';
        switch (cand.getAttribute('type')) {
        case 'srflx':
        case 'prflx':
        case 'relay':
            if (cand.getAttribute('rel-addr') && cand.getAttribute('rel-port')) {
                line += 'raddr';
                line += ' ';
                line += cand.getAttribute('rel-addr');
                line += ' ';
                line += 'rport';
                line += ' ';
                line += cand.getAttribute('rel-port');
                line += ' ';
            }
            break;
        }
        line += 'generation';
        line += ' ';
        line += cand.getAttribute('generation') || '0';
        return line + '\r\n';
    }
};
/* jshint -W117 */
// Jingle stuff
function JingleSession(me, sid, connection) {
    this.me = me;
    this.sid = sid;
    this.connection = connection;
    this.initiator = null;
    this.responder = null;
    this.isInitiator = null;
    this.peerjid = null;
    this.state = null;
    this.peerconnection = null;
    this.remoteStream = null;
    this.localSDP = null;
    this.remoteSDP = null;
    this.localStreams = [];
    this.relayedStreams = [];
    this.remoteStreams = [];
    this.startTime = null;
    this.stopTime = null;
    this.media_constraints = null;
    this.pc_constraints = null;
    this.ice_config = {},
    this.drip_container = [];

    this.usetrickle = true;
    this.usepranswer = false; // early transport warmup -- mind you, this might fail. depends on webrtc issue 1718
    this.usedrip = false; // dripping is sending trickle candidates not one-by-one

    this.hadstuncandidate = false;
    this.hadturncandidate = false;
    this.lasticecandidate = false;

    this.statsinterval = null;

    this.reason = null;

    this.addssrc = [];
    this.removessrc = [];

    this.wait = true;
}

JingleSession.prototype.initiate = function (peerjid, isInitiator) {
    var obj = this;
    if (this.state !== null) {
        console.error('attempt to initiate on session ' + this.sid +
                  'in state ' + this.state);
        return;
    }
    this.isInitiator = isInitiator;
    this.state = 'pending';
    this.initiator = isInitiator ? this.me : peerjid;
    this.responder = !isInitiator ? this.me : peerjid;
    this.peerjid = peerjid;
    console.log('create PeerConnection ' + JSON.stringify(this.ice_config));
    try {
        this.peerconnection = new RTCPeerconnection(this.ice_config,
                                                     this.pc_constraints);
        console.log('Created RTCPeerConnnection');
    } catch (e) {
        console.error('Failed to create PeerConnection, exception: ',
                      e.message);
        console.error(e);
        return;
    }
    this.hadstuncandidate = false;
    this.hadturncandidate = false;
    this.lasticecandidate = false;
    this.peerconnection.onicecandidate = function (event) {
        obj.sendIceCandidate(event.candidate);
    };
    this.peerconnection.onaddstream = function (event) {
        obj.remoteStream = event.stream;
        obj.remoteStreams.push(event.stream);
        $(document).trigger('remotestreamadded.jingle', [event, obj.sid]);
    };
    this.peerconnection.onremovestream = function (event) {
        obj.remoteStream = null;
        // FIXME: remove from this.remoteStreams
        $(document).trigger('remotestreamremoved.jingle', [event, obj.sid]);
    };
    this.peerconnection.onsignalingstatechange = function (event) {
        if (!(obj && obj.peerconnection)) return;
        console.log('signallingstate ', obj.peerconnection.signalingState, event);
    };
    this.peerconnection.oniceconnectionstatechange = function (event) {
        if (!(obj && obj.peerconnection)) return;
        console.log('iceconnectionstatechange', obj.peerconnection.iceConnectionState, event);
        switch (obj.peerconnection.iceConnectionState) {
        case 'connected':
            this.startTime = new Date();
            break;
        case 'disconnected':
            this.stopTime = new Date();
            break;
        }
        $(document).trigger('iceconnectionstatechange.jingle', [obj.sid, obj]);
    };
    // add any local and relayed stream
    this.localStreams.forEach(function(stream) {
        obj.peerconnection.addStream(stream);
    });
    this.relayedStreams.forEach(function(stream) {
        obj.peerconnection.addStream(stream);
    });
};

JingleSession.prototype.accept = function () {
    var ob = this;
    this.state = 'active';

    var pranswer = this.peerconnection.localDescription;
    if (!pranswer || pranswer.type != 'pranswer') {
        return;
    }
    console.log('going from pranswer to answer');
    if (this.usetrickle) {
        // remove candidates already sent from session-accept
        var lines = SDPUtil.find_lines(pranswer.sdp, 'a=candidate:');
        for (var i = 0; i < lines.length; i++) {
            pranswer.sdp = pranswer.sdp.replace(lines[i] + '\r\n', '');
        }
    }
    while (SDPUtil.find_line(pranswer.sdp, 'a=inactive')) {
        // FIXME: change any inactive to sendrecv or whatever they were originally
        pranswer.sdp = pranswer.sdp.replace('a=inactive', 'a=sendrecv');
    }
    var prsdp = new SDP(pranswer.sdp);
    var accept = $iq({to: this.peerjid,
             type: 'set'})
        .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
           action: 'session-accept',
           initiator: this.initiator,
           responder: this.responder,
           sid: this.sid });
    prsdp.toJingle(accept, this.initiator == this.me ? 'initiator' : 'responder');
    this.connection.sendIQ(accept,
        function () {
            var ack = {};
            ack.source = 'answer';
            $(document).trigger('ack.jingle', [ob.sid, ack]);
        },
        function (stanza) {
            var error = ($(stanza).find('error').length) ? {
                code: $(stanza).find('error').attr('code'),
                reason: $(stanza).find('error :first')[0].tagName,
            }:{};
            error.source = 'answer';
            $(document).trigger('error.jingle', [ob.sid, error]);
        },
    10000);

    var sdp = this.peerconnection.localDescription.sdp;
    while (SDPUtil.find_line(sdp, 'a=inactive')) {
        // FIXME: change any inactive to sendrecv or whatever they were originally
        sdp = sdp.replace('a=inactive', 'a=sendrecv');
    }
    this.peerconnection.setLocalDescription(new RTCSessionDescription({type: 'answer', sdp: sdp}),
        function () {
            console.log('setLocalDescription success');
        },
        function (e) {
            console.error('setLocalDescription failed', e);
        }
    );
};

JingleSession.prototype.terminate = function (reason) {
    this.state = 'ended';
    this.reason = reason;
    this.peerconnection.close();
    if (this.statsinterval !== null) {
        window.clearInterval(this.statsinterval);
        this.statsinterval = null;
    }
};

JingleSession.prototype.active = function () {
    return this.state == 'active';
};

JingleSession.prototype.sendIceCandidate = function (candidate) {
    var ob = this;
    if (candidate && !this.lasticecandidate) {
        var ice = SDPUtil.iceparams(this.localSDP.media[candidate.sdpMLineIndex], this.localSDP.session),
            jcand = SDPUtil.candidateToJingle(candidate.candidate);
        if (!(ice && jcand)) {
            console.error('failed to get ice && jcand');
            return;
        }
        ice.xmlns = 'urn:xmpp:jingle:transports:ice-udp:1';

        if (jcand.type === 'srflx') {
            this.hadstuncandidate = true;
        } else if (jcand.type === 'relay') {
            this.hadturncandidate = true;
        }
        console.log(event.candidate, jcand);

        if (this.usetrickle) {
            if (this.usedrip) {
                if (this.drip_container.length === 0) {
                    // start 10ms callout
                    window.setTimeout(function () {
                        if (ob.drip_container.length === 0) return;
                        var allcands = ob.drip_container;
                        ob.drip_container = [];
                        var cand = $iq({to: ob.peerjid, type: 'set'})
                            .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
                               action: 'transport-info',
                               initiator: ob.initiator,
                               sid: ob.sid});
                        for (var mid = 0; mid < ob.localSDP.media.length; mid++) {
                            var cands = allcands.filter(function (el) { return el.sdpMLineIndex == mid; });
                            if (cands.length > 0) {
                                var ice = SDPUtil.iceparams(ob.localSDP.media[mid], ob.localSDP.session);
                                ice.xmlns = 'urn:xmpp:jingle:transports:ice-udp:1';
                                cand.c('content', {creator: ob.initiator == ob.me ? 'initiator' : 'responder',
                                       name: cands[0].sdpMid
                                }).c('transport', ice);
                                for (var i = 0; i < cands.length; i++) {
                                    cand.c('candidate', SDPUtil.candidateToJingle(cands[i].candidate)).up();
                                }
                                // add fingerprint
                                if (SDPUtil.find_line(ob.localSDP.media[mid], 'a=fingerprint:', ob.localSDP.session)) {
                                    var tmp = SDPUtil.parse_fingerprint(SDPUtil.find_line(ob.localSDP.media[mid], 'a=fingerprint:', ob.localSDP.session));
                                    tmp.required = true;
                                    cand.c('fingerprint').t(tmp.fingerprint);
                                    delete tmp.fingerprint;
                                    cand.attrs(tmp);
                                    cand.up();
                                }
                                cand.up(); // transport
                                cand.up(); // content
                            }
                        }
                        // might merge last-candidate notification into this, but it is called alot later. See webrtc issue #2340
                        //console.log('was this the last candidate', ob.lasticecandidate);
                        ob.connection.sendIQ(cand,
                            function () {
                                var ack = {};
                                ack.source = 'transportinfo';
                                $(document).trigger('ack.jingle', [ob.sid, ack]);
                            },
                            function (stanza) {
                                var error = ($(stanza).find('error').length) ? {
                                    code: $(stanza).find('error').attr('code'),
                                    reason: $(stanza).find('error :first')[0].tagName,
                                }:{};
                                error.source = 'transportinfo';
                                $(document).trigger('error.jingle', [ob.sid, error]);
                            },
                        10000);
                    }, 10);
                }
                this.drip_container.push(event.candidate);
                return;
            }
            // map to transport-info
            var cand = $iq({to: this.peerjid, type: 'set'})
                .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
                        action: 'transport-info',
                        initiator: this.initiator,
                        sid: this.sid})
                .c('content', {creator: this.initiator == this.me ? 'initiator' : 'responder',
                        name: candidate.sdpMid
                        })
                .c('transport', ice)
                .c('candidate', jcand);
            cand.up();
            // add fingerprint
            if (SDPUtil.find_line(this.localSDP.media[candidate.sdpMLineIndex], 'a=fingerprint:', this.localSDP.session)) {
                var tmp = SDPUtil.parse_fingerprint(SDPUtil.find_line(this.localSDP.media[candidate.sdpMLineIndex], 'a=fingerprint:', this.localSDP.session));
                tmp.required = true;
                cand.c('fingerprint').t(tmp.fingerprint);
                delete tmp.fingerprint;
                cand.attrs(tmp);
                cand.up();
            }
            this.connection.sendIQ(cand,
                function () {
                    var ack = {};
                    ack.source = 'transportinfo';
                    $(document).trigger('ack.jingle', [ob.sid, ack]);
                },
                function (stanza) {
                    console.error('transport info error');
                    var error = ($(stanza).find('error').length) ? {
                        code: $(stanza).find('error').attr('code'),
                        reason: $(stanza).find('error :first')[0].tagName,
                    }:{};
                    error.source = 'transportinfo';
                    $(document).trigger('error.jingle', [ob.sid, error]);
                },
            10000);
        }
    } else {
        console.log('sendIceCandidate: last candidate.');
        if (!this.usetrickle) {
            console.log('should send full offer now...');
            var init = $iq({to: this.peerjid,
                       type: 'set'})
                .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
                   action: this.peerconnection.localDescription.type == 'offer' ? 'session-initiate' : 'session-accept',
                   initiator: this.initiator,
                   sid: this.sid});
            this.localSDP = new SDP(this.peerconnection.localDescription.sdp);
            this.localSDP.toJingle(init, this.initiator == this.me ? 'initiator' : 'responder');
            this.connection.sendIQ(init,
                function () {
                    console.log('session initiate ack');
                    var ack = {};
                    ack.source = 'offer';
                    $(document).trigger('ack.jingle', [ob.sid, ack]);
                },
                function (stanza) {
                    ob.state = 'error';
                    ob.peerconnection.close();
                    var error = ($(stanza).find('error').length) ? {
                        code: $(stanza).find('error').attr('code'),
                        reason: $(stanza).find('error :first')[0].tagName,
                    }:{};
                    error.source = 'offer';
                    $(document).trigger('error.jingle', [ob.sid, error]);
                },
            10000);
        }
        this.lasticecandidate = true;
        console.log('Have we encountered any srflx candidates? ' + this.hadstuncandidate);
        console.log('Have we encountered any relay candidates? ' + this.hadturncandidate);

        if (!(this.hadstuncandidate || this.hadturncandidate) && this.peerconnection.signalingState != 'closed') {
            $(document).trigger('nostuncandidates.jingle', [this.sid]);
        }
    }
};

JingleSession.prototype.sendOffer = function () {
    console.log('sendOffer...');
    var ob = this;
    this.peerconnection.createOffer(function (sdp) {
            ob.createdOffer(sdp);
        },
        function (e) {
            console.error('createOffer failed', e);
        },
        this.media_constraints
    );
};

JingleSession.prototype.createdOffer = function (sdp) {
    console.log('createdOffer', sdp);
    var ob = this;
    this.localSDP = new SDP(sdp.sdp);
    //this.localSDP.mangle();
    if (this.usetrickle) {
        var init = $iq({to: this.peerjid,
                   type: 'set'})
            .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
               action: 'session-initiate',
               initiator: this.initiator,
               sid: this.sid});
        this.localSDP.toJingle(init, this.initiator == this.me ? 'initiator' : 'responder');
        this.connection.sendIQ(init,
            function () {
                var ack = {};
                ack.source = 'offer';
                $(document).trigger('ack.jingle', [ob.sid, ack]);
            },
            function (stanza) {
                ob.state = 'error';
                ob.peerconnection.close();
                var error = ($(stanza).find('error').length) ? {
                    code: $(stanza).find('error').attr('code'),
                    reason: $(stanza).find('error :first')[0].tagName,
                }:{};
                error.source = 'offer';
                $(document).trigger('error.jingle', [ob.sid, error]);
            },
        10000);
    }
    sdp.sdp = this.localSDP.raw;
    this.peerconnection.setLocalDescription(sdp, function () {
            console.log('setLocalDescription success');
        },
        function (e) {
            console.error('setLocalDescription failed', e);
        }
    );
    var cands = SDPUtil.find_lines(this.localSDP.raw, 'a=candidate:');
    for (var i = 0; i < cands.length; i++) {
        var cand = SDPUtil.parse_icecandidate(cands[i]);
        if (cand.type == 'srflx') {
            this.hadstuncandidate = true;
        } else if (cand.type == 'relay') {
            this.hadturncandidate = true;
        }
    }
};

JingleSession.prototype.setRemoteDescription = function (elem, desctype) {
    console.log('setting remote description... ', desctype);
    this.remoteSDP = new SDP('');
    this.remoteSDP.fromJingle(elem);
    if (this.peerconnection.remoteDescription !== null) {
        console.log('setRemoteDescription when remote description is not null, should be pranswer', this.peerconnection.remoteDescription);
        if (this.peerconnection.remoteDescription.type == 'pranswer') {
            var pranswer = new SDP(this.peerconnection.remoteDescription.sdp);
            for (var i = 0; i < pranswer.media.length; i++) {
                // make sure we have ice ufrag and pwd
                if (!SDPUtil.find_line(this.remoteSDP.media[i], 'a=ice-ufrag:', this.remoteSDP.session)) {
                    if (SDPUtil.find_line(pranswer.media[i], 'a=ice-ufrag:', pranswer.session)) {
                        this.remoteSDP.media[i] += SDPUtil.find_line(pranswer.media[i], 'a=ice-ufrag:', pranswer.session) + '\r\n';
                    } else {
                        console.warn('no ice ufrag?');
                    }
                    if (SDPUtil.find_line(pranswer.media[i], 'a=ice-pwd:', pranswer.session)) {
                        this.remoteSDP.media[i] += SDPUtil.find_line(pranswer.media[i], 'a=ice-pwd:', pranswer.session) + '\r\n';
                    } else {
                        console.warn('no ice pwd?');
                    }
                }
                // copy over candidates
                var lines = SDPUtil.find_lines(pranswer.media[i], 'a=candidate:');
                for (var j = 0; j < lines.length; j++) {
                    this.remoteSDP.media[i] += lines[j] + '\r\n';
                }
            }
            this.remoteSDP.raw = this.remoteSDP.session + this.remoteSDP.media.join('');
        }
    }
    var remotedesc = new RTCSessionDescription({type: desctype, sdp: this.remoteSDP.raw});
    
    this.peerconnection.setRemoteDescription(remotedesc,
        function () {
            console.log('setRemoteDescription success');
        },
        function (e) {
            console.error('setRemoteDescription error', e);
        }
    );
};

JingleSession.prototype.addIceCandidate = function (elem) {
    var obj = this;
    if (this.peerconnection.signalingState == 'closed') {
        return;
    }
    if (!this.peerconnection.remoteDescription && this.peerconnection.signalingState == 'have-local-offer') {
        console.log('trickle ice candidate arriving before session accept...');
        // create a PRANSWER for setRemoteDescription
        if (!this.remoteSDP) {
            var cobbled = 'v=0\r\n' +
                'o=- ' + '1923518516' + ' 2 IN IP4 0.0.0.0\r\n' +// FIXME
                's=-\r\n' +
                't=0 0\r\n';
            // first, take some things from the local description
            for (var i = 0; i < this.localSDP.media.length; i++) {
                cobbled += SDPUtil.find_line(this.localSDP.media[i], 'm=') + '\r\n';
                cobbled += SDPUtil.find_lines(this.localSDP.media[i], 'a=rtpmap:').join('\r\n') + '\r\n';
                if (SDPUtil.find_line(this.localSDP.media[i], 'a=mid:')) {
                    cobbled += SDPUtil.find_line(this.localSDP.media[i], 'a=mid:') + '\r\n';
                }
                cobbled += 'a=inactive\r\n';
            }
            this.remoteSDP = new SDP(cobbled);
        }
        // then add things like ice and dtls from remote candidate
        elem.each(function () {
            for (var i = 0; i < obj.remoteSDP.media.length; i++) {
                if (SDPUtil.find_line(obj.remoteSDP.media[i], 'a=mid:' + $(this).attr('name')) ||
                        obj.remoteSDP.media[i].indexOf('m=' + $(this).attr('name')) === 0) {
                    if (!SDPUtil.find_line(obj.remoteSDP.media[i], 'a=ice-ufrag:')) {
                        var tmp = $(this).find('transport');
                        obj.remoteSDP.media[i] += 'a=ice-ufrag:' + tmp.attr('ufrag') + '\r\n';
                        obj.remoteSDP.media[i] += 'a=ice-pwd:' + tmp.attr('pwd') + '\r\n';
                        tmp = $(this).find('transport>fingerprint');
                        if (tmp.length) {
                            obj.remoteSDP.media[i] += 'a=fingerprint:' + tmp.attr('hash') + ' ' + tmp.text() + '\r\n';
                        } else {
                            console.log('no dtls fingerprint (webrtc issue #1718?)');
                            obj.remoteSDP.media[i] += 'a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:BAADBAADBAADBAADBAADBAADBAADBAADBAADBAAD\r\n';
                        }
                        break;
                    }
                }
            }
        });
        this.remoteSDP.raw = this.remoteSDP.session + this.remoteSDP.media.join('');

        // we need a complete SDP with ice-ufrag/ice-pwd in all parts
        // this makes the assumption that the PRANSWER is constructed such that the ice-ufrag is in all mediaparts
        // but it could be in the session part as well. since the code above constructs this sdp this can't happen however
        var iscomplete = this.remoteSDP.media.filter(function (mediapart) {
            return SDPUtil.find_line(mediapart, 'a=ice-ufrag:');
        }).length == this.remoteSDP.media.length;

        if (iscomplete) {
            console.log('setting pranswer');
            try {
                this.peerconnection.setRemoteDescription(new RTCSessionDescription({type: 'pranswer', sdp: this.remoteSDP.raw }));
            } catch (e) {
                console.error('setting pranswer failed', e);
            }
        } else {
            console.log('not yet setting pranswer');
        }
    }
    // operate on each content element
    elem.each(function () {
        // would love to deactivate this, but firefox still requires it
        var idx = -1;
        var i;
        for (i = 0; i < obj.remoteSDP.media.length; i++) {
            if (SDPUtil.find_line(obj.remoteSDP.media[i], 'a=mid:' + $(this).attr('name')) ||
                obj.remoteSDP.media[i].indexOf('m=' + $(this).attr('name')) === 0) {
                idx = i;
                break;
            }
        }
        if (idx == -1) { // fall back to localdescription
            for (i = 0; i < obj.localSDP.media.length; i++) {
                if (SDPUtil.find_line(obj.localSDP.media[i], 'a=mid:' + $(this).attr('name')) ||
                    obj.localSDP.media[i].indexOf('m=' + $(this).attr('name')) === 0) {
                    idx = i;
                    break;
                }
            }
        }
        var name = $(this).attr('name');
        // TODO: check ice-pwd and ice-ufrag?
        $(this).find('transport>candidate').each(function () {
            var line, candidate;
            line = SDPUtil.candidateFromJingle(this);
            candidate = new RTCIceCandidate({sdpMLineIndex: idx,
                                            sdpMid: name,
                                            candidate: line});
            try {
                obj.peerconnection.addIceCandidate(candidate);
            } catch (e) {
                console.error('addIceCandidate failed', e.toString(), line);
            }
        });
    });
};

JingleSession.prototype.sendAnswer = function (provisional) {
    console.log('createAnswer', provisional);
    var ob = this;
    this.peerconnection.createAnswer(
        function (sdp) {
            ob.createdAnswer(sdp, provisional);
        },
        function (e) {
            console.error('createAnswer failed', e);
        },
        this.media_constraints
    );
};

JingleSession.prototype.createdAnswer = function (sdp, provisional) {
    console.log('createAnswer callback');
    console.log(sdp);
    var ob = this;
    this.localSDP = new SDP(sdp.sdp);
    //this.localSDP.mangle();
    this.usepranswer = provisional === true;
    if (this.usetrickle) {
        if (!this.usepranswer) {
            var accept = $iq({to: this.peerjid,
                     type: 'set'})
                .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
                   action: 'session-accept',
                   initiator: this.initiator,
                   responder: this.responder,
                   sid: this.sid });
            this.localSDP.toJingle(accept, this.initiator == this.me ? 'initiator' : 'responder');
            this.connection.sendIQ(accept,
                function () {
                    var ack = {};
                    ack.source = 'answer';
                    $(document).trigger('ack.jingle', [ob.sid, ack]);
                },
                function (stanza) {
                    var error = ($(stanza).find('error').length) ? {
                        code: $(stanza).find('error').attr('code'),
                        reason: $(stanza).find('error :first')[0].tagName,
                    }:{};
                    error.source = 'answer';
                    $(document).trigger('error.jingle', [ob.sid, error]);
                },
            10000);
        } else {
            sdp.type = 'pranswer';
            for (var i = 0; i < this.localSDP.media.length; i++) {
                this.localSDP.media[i] = this.localSDP.media[i].replace('a=sendrecv\r\n', 'a=inactive\r\n');
            }
            this.localSDP.raw = this.localSDP.session + '\r\n' + this.localSDP.media.join('');
        }
    }
    sdp.sdp = this.localSDP.raw;
    this.peerconnection.setLocalDescription(sdp,
        function () {
            console.log('setLocalDescription success');
        },
        function (e) {
            console.error('setLocalDescription failed', e);
        }
    );
    var cands = SDPUtil.find_lines(this.localSDP.raw, 'a=candidate:');
    for (var j = 0; j < cands.length; j++) {
        var cand = SDPUtil.parse_icecandidate(cands[j]);
        if (cand.type == 'srflx') {
            this.hadstuncandidate = true;
        } else if (cand.type == 'relay') {
            this.hadturncandidate = true;
        }
    }
};

JingleSession.prototype.sendTerminate = function (reason, text) {
    var obj = this,
        term = $iq({to: this.peerjid,
               type: 'set'})
        .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
           action: 'session-terminate',
           initiator: this.initiator,
           sid: this.sid})
        .c('reason')
        .c(reason || 'success');
        
    if (text) {
        term.up().c('text').t(text);
    }
    
    this.connection.sendIQ(term,
        function () {
            obj.peerconnection.close();
            obj.peerconnection = null;
            obj.terminate();
            var ack = {};
            ack.source = 'terminate';
            $(document).trigger('ack.jingle', [obj.sid, ack]);
        },
        function (stanza) {
            var error = ($(stanza).find('error').length) ? {
                code: $(stanza).find('error').attr('code'),
                reason: $(stanza).find('error :first')[0].tagName,
            }:{};
            $(document).trigger('ack.jingle', [obj.sid, error]);
        },
    10000);
    if (this.statsinterval !== null) {
        window.clearInterval(this.statsinterval);
        this.statsinterval = null;
    }
};


JingleSession.prototype.addSource = function (elem) {
    console.log('addssrc', new Date().getTime());
    console.log('ice', this.peerconnection.iceConnectionState);
    var sdp = new SDP(this.peerconnection.remoteDescription.sdp);

    var ob = this;
    $(elem).each(function (idx, content) {
        var name = $(content).attr('name');
        var lines = '';
        tmp = $(content).find('>source[xmlns="urn:xmpp:jingle:apps:rtp:ssma:0"]');
        tmp.each(function () {
            var ssrc = $(this).attr('ssrc');
            $(this).find('>parameter').each(function () {
                lines += 'a=ssrc:' + ssrc + ' ' + $(this).attr('name');
                if ($(this).attr('value') && $(this).attr('value').length)
                    lines += ':' + $(this).attr('value');
                lines += '\r\n';
            });
        });
        console.log(name, lines);
        sdp.media.forEach(function(media, idx) {
            /*
            // remove a=crypto lines
            // FIXME: port this over to modifySources...
            while(SDPUtil.find_line(sdp.media[idx], 'a=crypto:')) {
                sdp.media[idx] = sdp.media[idx].replace(SDPUtil.find_line(sdp.media[idx], 'a=crypto:')+'\r\n', '');
            }
            */
            if (!SDPUtil.find_line(media, 'a=mid:' + name))
                return;
            sdp.media[idx] += lines;
            if (!ob.addssrc[idx]) ob.addssrc[idx] = '';
            ob.addssrc[idx] += lines;
        });
        sdp.raw = sdp.session + sdp.media.join('');
    });
    this.modifySources();
};

JingleSession.prototype.removeSource = function (elem) {
    console.log('removessrc', new Date().getTime());
    console.log('ice', this.peerconnection.iceConnectionState);
    var sdp = new SDP(this.peerconnection.remoteDescription.sdp);

    var ob = this;
    $(elem).each(function (idx, content) {
        var name = $(content).attr('name');
        var lines = '';
        tmp = $(content).find('>source[xmlns="urn:xmpp:jingle:apps:rtp:ssma:0"]');
        tmp.each(function () {
            var ssrc = $(this).attr('ssrc');
            $(this).find('>parameter').each(function () {
                lines += 'a=ssrc:' + ssrc + ' ' + $(this).attr('name');
                if ($(this).attr('value') && $(this).attr('value').length)
                    lines += ':' + $(this).attr('value');
                lines += '\r\n';
            });
        });
        console.log(name, lines);
        sdp.media.forEach(function(media, idx) {
            /*
            // remove a=crypto lines
            // FIXME: port this over to modifySources...
            while(SDPUtil.find_line(sdp.media[idx], 'a=crypto:')) {
                sdp.media[idx] = sdp.media[idx].replace(SDPUtil.find_line(sdp.media[idx], 'a=crypto:')+'\r\n', '');
            }
            */
            if (!SDPUtil.find_line(media, 'a=mid:' + name))
                return;
            sdp.media[idx] += lines;
            if (!ob.addssrc[idx]) ob.removessrc[idx] = '';
            ob.removessrc[idx] += lines;
        });
        sdp.raw = sdp.session + sdp.media.join('');
    });
    console.log(this.removessrc);
    this.modifySources();
};



JingleSession.prototype.modifySources = function() {
    var ob = this;
    if (!(this.addssrc.length || this.removessrc.length)) return;
    if (!(this.peerconnection.signalingState == 'stable' && this.peerconnection.iceConnectionState == 'connected')) {
        console.warn('addNewRemoteSSRC not yet', this.peerconnection.signalingState, this.peerconnection.iceConnectionState);
        this.wait = true;
        window.setTimeout(function() { ob.modifySources(); }, 250);
        return;
    }
    if (this.wait) {
        window.setTimeout(function() { ob.modifySources(); }, 2500);
        this.wait = false;
        return;
    }

    console.log('ice', this.peerconnection.iceConnectionState);
    var sdp = new SDP(this.peerconnection.remoteDescription.sdp);
    // mangle SDP a little... not sure if this is required anymore
    if (SDPUtil.find_line(sdp.session, 'a=msid-semantic:')) {
        sdp.session = sdp.session.replace(SDPUtil.find_line(sdp.session, 'a=msid-semantic:') + '\r\n', '');
    }
    sdp.media.forEach(function(media, idx) {
        sdp.media[idx] = sdp.media[idx].replace('a=recvonly', 'a=sendrecv'); // WTF?
    });

    // add sources
    this.addssrc.forEach(function(lines, idx) {
        sdp.media[idx] += lines;
    });
    this.addssrc = [];

    // remove sources
    this.removessrc.forEach(function(lines, idx) {
        lines = lines.split('\r\n');
        lines.pop(); // remove empty last element;
        lines.forEach(function(line) {
            sdp.media[idx] = sdp.media[idx].replace(line + '\r\n', '');
        });
    });
    this.removessrc = [];

    sdp.raw = sdp.session + sdp.media.join('');
    console.warn(sdp.raw);
    this.peerconnection.setRemoteDescription(new RTCSessionDescription({type: 'offer', sdp: sdp.raw}),
        function() {
            console.log('modify ok');
            ob.peerconnection.createAnswer(
                function(modifiedAnswer) {
                    console.log('modified answer...');
                    ob.peerconnection.setLocalDescription(modifiedAnswer,
                        function() {
                            console.log('modified setLocalDescription ok');
                        },
                        function(error) {
                            console.log('modified setLocalDescription failed');
                        }
                    );
                },
                function(error) {
                    console.log('modified answer failed');
                }
            );
        },
        function(error) {
            console.log('modify failed');
        }
    );
};

JingleSession.prototype.sendMute = function (muted, content) {
    var info = $iq({to: this.peerjid,
             type: 'set'})
        .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
           action: 'session-info',
           initiator: this.initiator,
           sid: this.sid });
    info.c(muted ? 'mute' : 'unmute', {xmlns: 'urn:xmpp:jingle:apps:rtp:info:1'});
    info.attrs({'creator': this.me == this.initiator ? 'creator' : 'responder'});
    if (content) {
        info.attrs({'name': content});
    }
    this.connection.send(info);
};

JingleSession.prototype.sendRinging = function () {
    var info = $iq({to: this.peerjid,
             type: 'set'})
        .c('jingle', {xmlns: 'urn:xmpp:jingle:1',
           action: 'session-info',
           initiator: this.initiator,
           sid: this.sid });
    info.c('ringing', {xmlns: 'urn:xmpp:jingle:apps:rtp:info:1'});
    this.connection.send(info);
};

JingleSession.prototype.getStats = function (interval) {
    var ob = this;
    var recv = {audio: 0, video: 0};
    var lost = {audio: 0, video: 0};
    var lastrecv = {audio: 0, video: 0};
    var lastlost = {audio: 0, video: 0};
    var loss = {audio: 0, video: 0};
    var delta = {audio: 0, video: 0};
    this.statsinterval = window.setInterval(function () {
        if (ob && ob.peerconnection && ob.peerconnection.getStats) {
            ob.peerconnection.getStats(function (stats) {
                var results = stats.result();
                // TODO: there are so much statistics you can get from this..
                for (var i = 0; i < results.length; ++i) {
                    if (results[i].type == 'ssrc') {
                        var packetsrecv = results[i].stat('packetsReceived');
                        var packetslost = results[i].stat('packetsLost');
                        if (packetsrecv && packetslost) {
                            packetsrecv = parseInt(packetsrecv, 10);
                            packetslost = parseInt(packetslost, 10);
                            
                            if (results[i].stat('googFrameRateReceived')) {
                                lastlost.video = lost.video;
                                lastrecv.video = recv.video;
                                recv.video = packetsrecv;
                                lost.video = packetslost;
                            } else {
                                lastlost.audio = lost.audio;
                                lastrecv.audio = recv.audio;
                                recv.audio = packetsrecv;
                                lost.audio = packetslost;
                            }
                        }
                    }
                }
                delta.audio = recv.audio - lastrecv.audio;
                delta.video = recv.video - lastrecv.video;
                loss.audio = (delta.audio > 0) ? Math.ceil(100 * (lost.audio - lastlost.audio) / delta.audio) : 0;
                loss.video = (delta.video > 0) ? Math.ceil(100 * (lost.video - lastlost.video) / delta.video) : 0;
                $(document).trigger('packetloss.jingle', [ob.sid, loss]);
            });
        }
    }, interval || 3000);
    return this.statsinterval;
};
