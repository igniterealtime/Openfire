/*
Created By: Chris Campbell
Website: http://particletree.com
Date: 2/1/2006

Inspired by the lightbox implementation found at http://www.huddletogether.com/projects/lightbox/
*/

/*-------------------------------GLOBAL VARIABLES------------------------------------*/

var detect = navigator.userAgent.toLowerCase();
var OS,browser,version,total,thestring, place;

/*-----------------------------------------------------------------------------------------------*/

//Browser detect script originally created by Peter Paul Koch at http://www.quirksmode.org/

function getBrowserInfo() {
    if (checkIt('konqueror')) {
        browser = "Konqueror";
        OS = "Linux";
    }
    else if (checkIt('safari')) browser 	= "Safari"
    else if (checkIt('omniweb')) browser 	= "OmniWeb"
    else if (checkIt('opera')) browser 		= "Opera"
    else if (checkIt('webtv')) browser 		= "WebTV";
    else if (checkIt('icab')) browser 		= "iCab"
    else if (checkIt('msie')) browser 		= "Internet Explorer"
    else if (!checkIt('compatible')) {
        browser = "Netscape Navigator"
        version = detect.charAt(8);
    }
    else browser = "An unknown browser";

    if (!version) version = detect.charAt(place + thestring.length);

    if (!OS) {
        if (checkIt('linux')) OS 		= "Linux";
        else if (checkIt('x11')) OS 	= "Unix";
        else if (checkIt('mac')) OS 	= "Mac"
        else if (checkIt('win')) OS 	= "Windows"
        else OS 								= "an unknown operating system";
    }
}

function checkIt(string) {
    place = detect.indexOf(string) + 1;
    thestring = string;
    return place;
}

/*-----------------------------------------------------------------------------------------------*/

Event.observe(window, 'load', initialize, false);
Event.observe(window, 'load', getBrowserInfo, false);
Event.observe(window, 'unload', Event.unloadCache, false);

var lightbox = Class.create();

lightbox.prototype = {

    yPos : 0,
    xPos : 0,

    initialize: function(ctrl) {
        this.content = ctrl.href;
        Event.observe(ctrl, 'click', this.activate.bindAsEventListener(this), false);
        ctrl.onclick = function(){return false;};
    },

    // Turn everything on - mainly the IE fixes
    activate: function(){
        if (browser === 'Internet Explorer'){
            this.getScroll();
            this.prepareIE('100%', 'hidden');
            this.setScroll(0,0);
            this.hideSelects('hidden');
        }
        this.displayLightbox("block");
    },

    // Ie requires height to 100% and overflow hidden or else you can scroll down past the lightbox
    prepareIE: function(height, overflow){
        let bod = document.getElementsByTagName('body')[0];
        bod.style.height = height;
        bod.style.overflow = overflow;

        let htm = document.getElementsByTagName('html')[0];
        htm.style.height = height;
        htm.style.overflow = overflow;
    },

    // In IE, select elements hover on top of the lightbox
    hideSelects: function(visibility){
        let selects = document.getElementsByTagName('select');
        for(const element of selects) {
            element.style.visibility = visibility;
        }
    },

    // Taken from lightbox implementation found at http://www.huddletogether.com/projects/lightbox/
    getScroll: function(){
        if (self.pageYOffset) {
            this.yPos = self.pageYOffset;
        } else if (document.documentElement && document.documentElement.scrollTop){
            this.yPos = document.documentElement.scrollTop;
        } else if (document.body) {
            this.yPos = document.body.scrollTop;
        }
    },

    setScroll: function(x, y){
        window.scrollTo(x, y);
    },

    displayLightbox: function(display){
        $('overlay').style.display = display;
        $('lightbox').style.display = display;
        if(display !== 'none') this.loadInfo();
    },

    // Begin Ajax request based off of the href of the clicked linked
    loadInfo: function() {
        var myAjax = new Ajax.Request(
        this.content,
        {method: 'post', parameters: "", onComplete: this.processInfo.bindAsEventListener(this)}
        );

    },

    // Display Ajax response
    processInfo: function(response){
        let info = "<div id='lbContent'>" + response.responseText + "</div>";
        new Insertion.Before($('lbLoadMessage'), info)
        $('lightbox').className = "done";
        this.actions();
    },

    // Search through new links within the lightbox, and attach click event
    actions: function(){
        let lbActions = document.getElementsByClassName('lbAction');

        for(const action of lbActions) {
            Event.observe(action, 'click', this[action.rel].bindAsEventListener(this), false);
            action.onclick = function(){return false;};
        }

    },

    // Example of creating your own functionality once lightbox is initiated
    insert: function(e){
       let link = Event.element(e).parentNode;
       Element.remove($('lbContent'));

       var myAjax = new Ajax.Request(
              link.href,
              {method: 'post', parameters: "", onComplete: this.processInfo.bindAsEventListener(this)}
       );

    },

    // Example of creating your own functionality once lightbox is initiated
    deactivate: function(){
        Element.remove($('lbContent'));

        if (browser === "Internet Explorer"){
            this.setScroll(0,this.yPos);
            this.prepareIE("auto", "auto");
            this.hideSelects("visible");
        }

        this.displayLightbox("none");
    }
}

/*-----------------------------------------------------------------------------------------------*/

// Onload, make all links that need to trigger a lightbox active
function initialize(){
    addLightboxMarkup();
    let lbox = document.getElementsByClassName('lbOn');

    for (const element in lbox) {
        let valid = new lightbox(element);
    }
}

// Add in markup necessary to make this work. Basically two divs:
// Overlay holds the shadow
// Lightbox is the centered square that the content is put into.
function addLightboxMarkup() {
    let bod 				= document.getElementsByTagName('body')[0];
    let overlay 			= document.createElement('div');
    overlay.id		= 'overlay';
    let lb					= document.createElement('div');
    lb.id				= 'lightbox';
    lb.className 	= 'loading';
    lb.innerHTML	= '<div id="lbLoadMessage">' +
                          '<p>Loading</p>' +
                          '</div>';
    bod.appendChild(overlay);
    bod.appendChild(lb);
}
