//
// Copyright (c) 2011 Frank Kohlhepp
// https://github.com/frankkohlhepp/fancy-settings
// License: LGPL v2.1
//
(function () {
    var lang = navigator.language.split("-")[0];
    if (this.i18n === undefined) { this.i18n = {}; }
    this.i18n.get = function (value) {
        if (value === "lang") {
            return lang;
        }
        
        if (this.hasOwnProperty(value)) {
            value = this[value];
            if (value.hasOwnProperty(lang)) {
                return value[lang];
            } else if (value.hasOwnProperty("en")) {
                return value["en"];
            } else {
                return Object.values(value)[0];
            }
        } else {
            return value;
        }
    };
}());
