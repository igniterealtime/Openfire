"use strict";

var punycode = require("punycode");

module.exports = function(address){
    return address.replace(/((?:https?:\/\/)?.*\@)?([^\/]*)/, function(o, start, domain){
        var domainParts = domain.split(/\./).map(punycode.toASCII.bind(punycode));
        return (start || "") + domainParts.join(".");
    });
};