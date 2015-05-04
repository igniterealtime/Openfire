
window.addEventListener("load", function() 
{

});

window.addEventListener("beforeunload", function () 
{

});
			


chrome.runtime.onConnect.addListener(function (channel) 
{
    channel.onMessage.addListener(function (message) {
        switch(message.type) {
        case 'ofmeetGetScreen':
            var pending = chrome.desktopCapture.chooseDesktopMedia(message.options || ['screen', 'window'], 
                                                                   channel.sender.tab, function (streamid) {
                // Communicate this string to the app so it can call getUserMedia with it
                message.type = 'ofmeetGotScreen';
                message.sourceId = streamid;
                channel.postMessage(message);
            });
            // Let the app know that it can cancel the timeout
            message.type = 'ofmeetGetScreenPending';
            message.request = pending;
            channel.postMessage(message);
            break;
        case 'ofmeetCancelGetScreen':
            chrome.desktopCapture.cancelChooseDesktopMedia(message.request);
            message.type = 'ofmeetCanceledGetScreen';
            channel.postMessage(message);
            break;
        }
    });
});
