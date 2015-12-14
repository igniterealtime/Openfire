/**
 *  jQuery.observeHashChange (Version: 1.0)
 *
 *  http://finnlabs.github.com/jquery.observehashchange/
 *
 *  Copyright (c) 2009, Gregor Schmidt, Finn GmbH
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *  DEALINGS IN THE SOFTWARE.
 **/
(function($) {
  $.fn.hashchange = function(fn) {
    $(window).bind("jQuery.hashchange", fn);
    return this;
  };

  $.observeHashChange = function(options) {
    var opts = $.extend({}, $.observeHashChange.defaults, options);
    if (isHashChangeEventSupported()) {
      nativeVersion();
    }
    else {
      setIntervalVersion(opts);
    }
  };

  var locationHash = null;
  var functionStore = null;
  var interval = 0;

  $.observeHashChange.defaults = {
    interval : 500
  };

  function isHashChangeEventSupported() {
    return "onhashchange" in window;
  }

  function nativeVersion() {
    locationHash = document.location.hash;
    window.onhashchange = onhashchangeHandler;
  }

  function onhashchangeHandler(e, data) {
    var oldHash = locationHash;
    locationHash = document.location.hash;
    $(window).trigger("jQuery.hashchange", {before: oldHash, after: locationHash});
  }

  function setIntervalVersion(opts) {
    if (locationHash == null) {
      locationHash = document.location.hash;
    }
    if (functionStore != null) {
      clearInterval(functionStore);
    }
    if (interval != opts.interval) {
      functionStore = setInterval(checkLocationHash, opts.interval); 
      interval = opts.interval;
    }
  }

  function checkLocationHash() {
    if (locationHash != document.location.hash) {
      var oldHash = locationHash;
      locationHash = document.location.hash;
      $(window).trigger("jQuery.hashchange", {before: oldHash, after: locationHash});
    }
  }

  $.observeHashChange();
})(jQuery);