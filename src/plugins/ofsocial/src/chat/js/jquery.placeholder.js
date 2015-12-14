(function($) {
	$.extend({
		placeholder : {
			settings : {
				focusClass: 'placeholderFocus',
				activeClass: 'placeholder',
				overrideSupport: false,
				preventRefreshIssues: true
			},
			debug : false,
			log : function(msg){
				if(!$.placeholder.debug) return;
				msg = "[Placeholder] " + msg;
				$.placeholder.hasFirebug ?
				console.log(msg) :
				$.placeholder.hasConsoleLog ?
					window.console.log(msg) :
					alert(msg);
			},
			hasFirebug : "console" in window && "firebug" in window.console,
			hasConsoleLog: "console" in window && "log" in window.console
		}

	});

    // check browser support for placeholder
    $.support.placeholder = 'placeholder' in document.createElement('input');

	// Replace the val function to never return placeholders
	$.fn.plVal = $.fn.val;
	$.fn.val = function(value) {
		$.placeholder.log('in val');
		if(this[0]) {
			$.placeholder.log('have found an element');
			var el = $(this[0]);
			if(value != undefined)
			{
				$.placeholder.log('in setter');
				var currentValue = el.plVal();
				var returnValue = $(this).plVal(value);
				if(el.hasClass($.placeholder.settings.activeClass) && currentValue == el.attr('placeholder')){
					el.removeClass($.placeholder.settings.activeClass);
				}
				return returnValue;
			}

			if(el.hasClass($.placeholder.settings.activeClass) && el.plVal() == el.attr('placeholder')) {
				$.placeholder.log('returning empty because its a placeholder');
				return '';
			} else {
				$.placeholder.log('returning original val');
				return el.plVal();
			}
		}
		$.placeholder.log('returning undefined');
		return undefined;
	};

	// Clear placeholder values upon page reload
	$(window).bind('beforeunload.placeholder', function() {
		var els = $('input.placeholderActive' );
		if(els.length > 0)
			els.val('').attr('autocomplete','off');
	});


    // plugin code
	$.fn.placeholder = function(opts) {
		opts = $.extend({},$.placeholder.settings, opts);

		// we don't have to do anything if the browser supports placeholder
		if(!opts.overrideSupport && $.support.placeholder)
		    return this;
			
        return this.each(function() {
            var $el = $(this);

            // skip if we do not have the placeholder attribute
            if(!$el.is('[placeholder]'))
                return;

            // we cannot do password fields, but supported browsers can
            if($el.is(':password'))
                return;
			
			// Prevent values from being reapplied on refresh
			if(opts.preventRefreshIssues)
				$el.attr('autocomplete','off');

            $el.bind('focus.placeholder', function(){
                var $el = $(this);
                if(this.value == $el.attr('placeholder') && $el.hasClass(opts.activeClass))
                    $el.val('')
                       .removeClass(opts.activeClass)
                       .addClass(opts.focusClass);
            });
            $el.bind('blur.placeholder', function(){
                var $el = $(this);
				
				$el.removeClass(opts.focusClass);

                if(this.value == '')
                  $el.val($el.attr('placeholder'))
                     .addClass(opts.activeClass);
            });

            $el.triggerHandler('blur');
			
			// Prevent incorrect form values being posted
			$el.parents('form').submit(function(){
				$el.triggerHandler('focus.placeholder');
			});

        });
    };
})(jQuery);
