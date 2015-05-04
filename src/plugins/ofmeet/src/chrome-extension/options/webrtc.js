window.addEventListener("load", function()
{
 	navigator.webkitGetUserMedia({ audio: true, video: true },
                function (stream) {
               
                    setTimeout(function() {stream.stop();}, 1000);

                },
                function (error) {
                    alert("To experience the full functionality of Openfire Meetings, please connect audio and video devices.");
                    console.error("Error trying to get the stream:: " + error.message);
                }
        );
});