// tipsy.hovercard, twitter style hovercards for tipsy
// version 0.1.1
// (c) 2010 René Föhring rf@bamaru.de
// Modified by Mike Martel mike@trenvo.nl
// released under the MIT license

(function($) {
  var timeoutEl, timeoutId;

  $.fn.tipsyHoverCard = function(options) {
    if ( ! window.bphc_cache )
	window.bphc_cache = new Array();

    var opts = $.extend({}, $.fn.tipsyHoverCard.defaults, options, $.fn.tipsyHoverCard.forcedOptions);
    this.tipsy(opts);

    function clearHideTimeout(ele) {
      if( ele.data('timeoutId') ) clearTimeout(ele.data('timeoutId'));
      if ( ele == timeoutEl ) timeoutEl = null;
      ele.data('timeoutId', null);
    }
    function setHideTimeout(ele) {
      clearHideTimeout(ele);
      //var options = ele.tipsy(true).options;
      timeoutId = setTimeout(function() { $(ele).tipsy('hide'); ele.data('visible', false); }, 600);
      timeoutEl = ele;
      ele.data('timeoutId', timeoutId);
    }

    function show(ele) {
      clearHideTimeout(ele);

      if ( ele != timeoutEl && typeof timeoutEl !== "undefined" && timeoutEl ) {
        jQuery('.tipsy').fadeOut();
        timeoutEl.data('visible', false);
      }

      ele.tipsy(opts).tipsy('show');

      var tip = ele.tipsy('tip');
      tip.addClass('tipsy-hovercard');
      tip.data('tipsyAnchor', ele);
      tip.hover(tipEnter, tipLeave);
      ele.data('visible', true);
    }
    function hide(ele) {
      setHideTimeout(ele);
    }

    function enter() {
      var ele = $(this);
      if ( ele.data('visible') ) return;

      var url = ajaxurl;
      var user_id = ele.attr('class').split('-')[1];
      if( url && ! ele.data('ajax-success') && ! ele.data('hovercardPending') ) {
        if ( window.bphc_cache[user_id] ) {
            ele.data('ajax-success', true);
            ele.attr('title', window.bphc_cache[user_id]);
            ele.data('tipsyAnchor');
            if ( ! ele.data('visible') )
                show(ele);
        } else {
            var data = {
                action: 'buddypress_hovercard',
                userid: user_id
            };

            ele.data('hovercardPending', true);
            jQuery.post(url, data, function(response) {
                ele.data('hovercardPending', false);
                ele.data('ajax-success', true);
                ele.attr('title', response);
                ele.data('tipsyAnchor');
                window.bphc_cache[user_id] = response;

                //if ( ! a.data('timeoutId') )
                if ( ele != timeoutEl && ! ele.data('timeoutId') ){
                    show(ele);
                }
            });
        }
      } else
        show(ele);
    }
    function leave() {
      hide($(this));
    }

    function tipEnter() {
      var a = $(this).data('tipsyAnchor');
      clearHideTimeout(a);
    }
    function tipLeave() {
      var a = $(this).data('tipsyAnchor');
      setHideTimeout(a);
    }

    if( $.fn.hoverIntent && opts.hoverIntent ) {
      // 'out' is called with a latency, even if 'timeout' is set to 0
      // therefore, we're using good ol' mouseleave for out-handling
      var config = $.extend({over: enter, out: function(){}}, opts.hoverIntentConfig);
      this.hoverIntent(config).mouseleave(leave);
    } else {
      this.live("mouseenter", enter)
      this.live("mouseleave", leave)
    }
    return this;
  }
  $.fn.tipsyHoverCard.forcedOptions = {live: false, trigger: 'manual'};
  $.fn.tipsyHoverCard.defaults = {
    gravity: $.fn.tipsy.autoBounds(350,'nw'),
    fade: true,
    fallback: '',
    html: true,
    hideDelay: 600,
    opacity: 1,
    hoverIntent: false
  };

    $(document).ready(function() {
        // Add hovercards to our avatars
        $.add_bp_hovercards();

        // Make our hovercards hoverable (so you can add links inside them)
        $('.tipsy').live('mouseover',function(e){
            clearTimeout(timeoutId);
        });
        $('.tipsy').live('mouseleave',function(e){
            $(this).fadeOut();
            timeoutEl.data('visible', false);
        });
    // Reload the hovercards after an AJAX call is made
    }).ajaxComplete( function(e, xhr, settings) {
        // OK, to make sure it always works: reload on every AJAX response with data - except for Live Notifications
            if ( typeof settings.data !== "undefined" && settings.data.indexOf("action=bpln_check_notification") == -1 )
            // To make (kindof) sure everything is loaded, set a timeout before reloading the hovercards after an ajax call
            setTimeout ( 'jQuery.add_bp_hovercards()', 1000 );
    });

    // Parents filter
    $.expr[':'].parents = function(a,i,m) {
        return jQuery(a).parents(m[3]).length < 1;
    };
    // Add hovercards function. Attached to jq, so we can access it with the setTimeout above
    $.add_bp_hovercards = function() {
        var avatar_filter = 'img[class^="avatar user"]';

        if ( bphc.parent_filter )
            avatar_filter += ':parents(' + bphc.parent_filter +')';

        if ( bphc.element_filter )
            avatar_filter += ':not(' + bphc.element_filter + ')';

        var avatars = jQuery(avatar_filter);
        avatars.tipsyHoverCard();
    }

})(jQuery);