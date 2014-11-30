/*
 * candy-timeago-plugin
 * @version 0.1 (2011-07-15)
 * @author David Devlin (dave.devlin@gmail.com)
 *
 * Integrates the jQuery Timeago plugin (http://timeago.yarp.com/) with Candy.
 */

var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.Timeago = (function(self, Candy, $) {

	self.init = function() {

		Candy.View.Template.Chat['adminMessage'] = '<li><small><abbr title="{{time}}">{{time}}</abbr></small><div class="adminmessage"><span class="label">{{sender}}</span><span class="spacer">▸</span>{{subject}} {{message}}</div></li>';
		Candy.View.Template.Chat['infoMessage'] = '<li><small><abbr title="{{time}}">{{time}}</abbr></small><div class="infomessage"><span class="spacer">•</span>{{subject}} {{message}}</div></li>';
		Candy.View.Template.Room['subject'] = '<li><small><abbr title="{{time}}">{{time}}</abbr></small><div class="subject"><span class="label">{{roomName}}</span><span class="spacer">▸</span>{{_roomSubject}} {{subject}}</div></li>';
		Candy.View.Template.Message['item'] = '<li><small><abbr title="{{time}}">{{time}}</abbr></small><div><a class="label" href="#" class="name">{{displayName}}</a><span class="spacer">▸</span>{{{message}}}</div></li>';

		Candy.Util.localizedTime = function(dateTime) {
			if (dateTime === undefined) {
				return undefined;
			}

			var date = Candy.Util.iso8601toDate(dateTime);
			return date.format($.i18n._('isoDateTime'));
		};

    var applyTimeago = function(e, args) {
      var $elem = args.element ? $('abbr', args.element) : $('abbr');
      $elem.timeago();
    };

    $(Candy).on('candy:view.message.after-show', applyTimeago);
    $(Candy).on('candy:view.room.after-subject-change', applyTimeago);
    // the following handlers run timeago() on all <abbr> tags
    $(Candy).on('candy:core.presence.room', applyTimeago);
    $(Candy).on('candy:view.chat.admin-message', applyTimeago);
	};

	return self;
}(CandyShop.Timeago || {}, Candy, jQuery));

/*
 * timeago: a jQuery plugin, version: 0.9.3 (2011-01-21)
 * @requires jQuery v1.2.3 or later
 *
 * Timeago is a jQuery plugin that makes it easy to support automatically
 * updating fuzzy timestamps (e.g. "4 minutes ago" or "about 1 day ago").
 *
 * For usage and examples, visit:
 * http://timeago.yarp.com/
 *
 * Licensed under the MIT:
 * http://www.opensource.org/licenses/mit-license.php
 *
 * Copyright (c) 2008-2011, Ryan McGeary (ryanonjavascript -[at]- mcgeary [*dot*] org)
 */
(function($) {
  $.timeago = function(timestamp) {
    if (timestamp instanceof Date) {
      return inWords(timestamp);
    } else if (typeof timestamp === "string") {
      return inWords($.timeago.parse(timestamp));
    } else {
      return inWords($.timeago.datetime(timestamp));
    }
  };
  var $t = $.timeago;

  $.extend($.timeago, {
    settings: {
      refreshMillis: 60000,
      allowFuture: false,
      strings: {
        prefixAgo: null,
        prefixFromNow: null,
        suffixAgo: "ago",
        suffixFromNow: "from now",
        seconds: "less than a minute",
        minute: "about a minute",
        minutes: "%d minutes",
        hour: "about an hour",
        hours: "about %d hours",
        day: "a day",
        days: "%d days",
        month: "about a month",
        months: "%d months",
        year: "about a year",
        years: "%d years",
        numbers: []
      }
    },
    inWords: function(distanceMillis) {
      var $l = this.settings.strings;
      var prefix = $l.prefixAgo;
      var suffix = $l.suffixAgo;
      if (this.settings.allowFuture) {
        if (distanceMillis < 0) {
          prefix = $l.prefixFromNow;
          suffix = $l.suffixFromNow;
        }
        distanceMillis = Math.abs(distanceMillis);
      }

      var seconds = distanceMillis / 1000;
      var minutes = seconds / 60;
      var hours = minutes / 60;
      var days = hours / 24;
      var years = days / 365;

      function substitute(stringOrFunction, number) {
        var string = $.isFunction(stringOrFunction) ? stringOrFunction(number, distanceMillis) : stringOrFunction;
        var value = ($l.numbers && $l.numbers[number]) || number;
        return string.replace(/%d/i, value);
      }

      var words = seconds < 45 && substitute($l.seconds, Math.round(seconds)) ||
        seconds < 90 && substitute($l.minute, 1) ||
        minutes < 45 && substitute($l.minutes, Math.round(minutes)) ||
        minutes < 90 && substitute($l.hour, 1) ||
        hours < 24 && substitute($l.hours, Math.round(hours)) ||
        hours < 48 && substitute($l.day, 1) ||
        days < 30 && substitute($l.days, Math.floor(days)) ||
        days < 60 && substitute($l.month, 1) ||
        days < 365 && substitute($l.months, Math.floor(days / 30)) ||
        years < 2 && substitute($l.year, 1) ||
        substitute($l.years, Math.floor(years));

      return $.trim([prefix, words, suffix].join(" "));
    },
    parse: function(iso8601) {
      var s = $.trim(iso8601);
      s = s.replace(/\.\d\d\d+/,""); // remove milliseconds
      s = s.replace(/-/,"/").replace(/-/,"/");
      s = s.replace(/T/," ").replace(/Z/," UTC");
      s = s.replace(/([\+\-]\d\d)\:?(\d\d)/," $1$2"); // -04:00 -> -0400
      return new Date(s);
    },
    datetime: function(elem) {
      // jQuery's `is()` doesn't play well with HTML5 in IE
      var isTime = $(elem).get(0).tagName.toLowerCase() === "time"; // $(elem).is("time");
      var iso8601 = isTime ? $(elem).attr("datetime") : $(elem).attr("title");
      return $t.parse(iso8601);
    }
  });

  $.fn.timeago = function() {
    var self = this;
    self.each(refresh);

    var $s = $t.settings;
    if ($s.refreshMillis > 0) {
      setInterval(function() { self.each(refresh); }, $s.refreshMillis);
    }
    return self;
  };

  function refresh() {
    var data = prepareData(this);
    if (!isNaN(data.datetime)) {
      $(this).text(inWords(data.datetime));
    }
    return this;
  }

  function prepareData(element) {
    element = $(element);
    if (!element.data("timeago")) {
      element.data("timeago", { datetime: $t.datetime(element) });
      var text = $.trim(element.text());
      if (text.length > 0) {
        element.attr("title", text);
      }
    }
    return element.data("timeago");
  }

  function inWords(date) {
    return $t.inWords(distance(date));
  }

  function distance(date) {
    return (new Date().getTime() - date.getTime());
  }

  // fix for IE6 suckage
  document.createElement("abbr");
  document.createElement("time");
}(jQuery));