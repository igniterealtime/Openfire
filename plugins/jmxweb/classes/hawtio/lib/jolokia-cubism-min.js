
(function(){var builder=function(cubism,Jolokia){var VERSION="1.1.4";var ctx_jolokia=function(url,opts){var source={},context=this,j4p=createAgent(url,opts),step=5e3;try
{context.on("start",function(){j4p.start();});context.on("stop",function(){j4p.stop();});}
catch(err)
{}
source.metric=function(){var values=[];var name;var argsLen=arguments.length;var options={};var lastIdx=arguments.length-1;var lastArg=arguments[lastIdx];if(typeof lastArg=="string"){name=lastArg;argsLen=lastIdx;}
if(typeof lastArg=="object"&&!lastArg.type){options=lastArg;name=options.name;argsLen=lastIdx;}
if(!name&&typeof arguments[0]!="function"){name=arguments[0].mbean;}
var metric=context.metric(mapValuesFunc(values,options.keepDelay,context.width),name);if(options.delta){var prevMetric=metric.shift(-options.delta);metric=metric.subtract(prevMetric);if(name){metric.toString=function(){return name};}}
if(typeof arguments[0]=="function"){var func=arguments[0];var respFunc=function(resp){var isError=false;for(var i=0;i<arguments.length;i++){if(j4p.isError(arguments[i])){isError=true;break;}}
values.unshift({time:Date.now(),value:isError?NaN:func.apply(metric,arguments)});};var args=[respFunc];for(var i=1;i<argsLen;i++){args.push(arguments[i]);}
j4p.register.apply(j4p,args);}else{var request=arguments[0];j4p.register(function(resp){values.unshift({time:Date.now(),value:j4p.isError(resp)?NaN:Number(resp.value)});},request);}
return metric;};source.start=function(newStep){newStep=newStep||step;j4p.start(newStep);};source.stop=function(){j4p.stop()};source.isRunning=function(){return j4p.isRunning()};return source;function createAgent(url,opts){if(url instanceof Jolokia){return url;}else{var args;if(typeof url=="string"){args={url:url};if(opts){for(var key in opts){if(opts.hasOwnProperty(key)){args[key]=opts[key];}}}}else{args=url;}
return new Jolokia(args);}}
function mapValuesFunc(values,keepDelay,width){return function(cStart,cStop,cStep,callback){cStart=+cStart;cStop=+cStop;var retVals=[],cTime=cStop,vLen=values.length,vIdx=0,vStart=vLen>0?values[vLen-1].time:undefined;if(!vLen||cStop<vStart){for(var t=cStart;t<=cStop;t+=cStep){retVals.push(NaN);}
return callback(null,retVals);}
while(cTime>values[0].time+cStep){retVals.unshift(NaN);cTime-=cStep;}
while(cTime>=cStart&&cTime>=vStart){while(values[vIdx].time>cTime){vIdx++;}
retVals.unshift(values[vIdx].value);cTime-=cStep;}
while(cTime>=cStart){retVals.unshift(NaN);cTime-=cStep;}
if(vLen>width){if(!keepDelay){values.length=width;}else{var keepUntil=values[width].time-keepDelay,i=width;while(i<vLen&&values[i].time>keepUntil){i++;}
values.length=i;}}
callback(null,retVals);}}};ctx_jolokia.VERSION=VERSION;cubism.context.prototype.jolokia=ctx_jolokia;return ctx_jolokia;};(function(root){if(typeof define==='function'&&define.amd){define(["cubism","jolokia"],function(cubism,Jolokia){return builder(cubism,Jolokia);});}else{if(root.Jolokia&&root.cubism){builder(root.cubism,root.Jolokia);}else{console.error("No "+(root.cubism?"Cubism":"Jolokia")+" definition found. "+"Please include jolokia.js and cubism.js before jolokia-cubism.js");}}})(this);})();
