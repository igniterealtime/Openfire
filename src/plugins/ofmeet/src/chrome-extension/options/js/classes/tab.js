//
// Copyright (c) 2011 Frank Kohlhepp
// https://github.com/frankkohlhepp/fancy-settings
// License: LGPL v2.1
//
(function () {
    var Bundle = new Class({
        "initialize": function (creator) {
            this.creator = creator;
            
            // Create DOM elements
            this.tab = new Element("div", {"class": "tab"});
            this.content = new Element("div", {"class": "tab-content"});
            
            // Create event handlers
            this.tab.addEvent("click", this.activate.bind(this));
        },
        
        "activate": function () {
            if (this.creator.activeBundle && this.creator.activeBundle !== this) {
                this.creator.activeBundle.deactivate();
            }
            this.tab.addClass("active");
            this.content.addClass("show");
            this.creator.activeBundle = this;
        },
        
        "deactivate": function () {
            this.tab.removeClass("active");
            this.content.removeClass("show");
            this.creator.activeBundle = null;
        }
    });
    
    this.Tab = new Class({
        "activeBundle": null,
        
        "initialize": function (tabContainer, tabContentContainer) {
            this.tabContainer = tabContainer;
            this.tabContentContainer = tabContentContainer;
        },
        
        "create": function () {
            var bundle = new Bundle(this);
            bundle.tab.inject(this.tabContainer);
            bundle.content.inject(this.tabContentContainer);
            if (!this.activeBundle) { bundle.activate(); }
            return bundle;
        }
    });
}());
