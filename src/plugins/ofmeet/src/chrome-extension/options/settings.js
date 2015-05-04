window.addEvent("domready", function () {

    new FancySettings.initWithManifest(function (settings) 
    {
        var background = chrome.extension.getBackgroundPage();
        
        settings.manifest.disconnect.addEvent("action", function () 
        {
            console.log("Logged out!");
        });        

        settings.manifest.connect.addEvent("action", function () 
        {
            reloadTL()
        });

        settings.manifest.savesipSpeakerAddress.addEvent("action", function () 
        {
            reloadTL()
        }); 


        function reloadTL(){
            background.ChromeApi.reloadTL();
        }

    });
    

});
