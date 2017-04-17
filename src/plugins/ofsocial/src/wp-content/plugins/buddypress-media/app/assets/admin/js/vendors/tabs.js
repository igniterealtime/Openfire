// Written by S@G@R

/* Utility : Object.create dosen't work all browsers. */
if ( typeof Object.create !== 'function' ) {
	Object.create = function ( obj ) {
		function F() {
		}
		;
		F.prototype = obj;
		return new F();
	};
}

( function ( $, window, document, undefined ) {

	var Tab = {
		init: function ( options, elem ) {
			var self = this;
			self.elem = elem;
			self.$elem = $( elem );

			/* Extend Options */
			self.options = $.extend( { }, $.fn.rtTab.options, options );

			self.rtTabs();
		},
		rtTabs: function () {
			var self = this,
					showTab = self.options.activeTab;

			/* Tab Active */
			self.$elem.find( 'li:nth-child(' + showTab + ')' ).addClass( 'active' );
			self.rtTabContent( activeTabContent = 'yes' );
			self.rtClick();

			// Datahash Variable
			var datahash = ( self.$elem.attr( 'data-hash' ) === 'false' ) ? false : true;

			/* This will keep on same tab as in hashtag */
			if ( datahash === true ) {
				var hashTag = window.location.hash;

				if ( hashTag ) {
					self.$elem.find( 'li' ).find( 'a[href=' + hashTag + ']' ).trigger( 'click' );
				}

				// Detect change in hash value of URL
				$( window ).on( 'hashchange', function () {
					var hashTag = window.location.hash;
					// Iterate over all nav links, setting the "selected" class as-appropriate.
					self.$elem.find( 'li' ).find( 'a[href=' + hashTag + ']' ).trigger( 'click' );
				} );
			}

		},
		rtClick: function () {
			var self = this,
					eachTab = self.$elem.find( 'li' ),
					tabLink = eachTab.find( 'a' );

			tabLink.on( 'click', function ( e ) {
				/* Prevent */
				e.preventDefault();

				/* Remove Active Class From All Tabs */
				eachTab.removeClass( 'active' );

				/* Hide All Tab Contents */
				self.rtTabContent();

				/* Add Active Class to Current Tab */
				$( this ).parent().addClass( 'active' );

				/* Show Active Tab Content */
				var activeTab = $( this ).attr( 'href' );
				$( activeTab ).removeClass( 'hide' );

				// Datahash Variable
				var datahash = ( self.$elem.attr( 'data-hash' ) === 'false' ) ? false : true;

				/* Hash tag in URL */
				if ( datahash === true ) {
					var pos = $( window ).scrollTop();
					location.hash = $( this ).attr( 'href' );
					$( window ).scrollTop( pos );
				}

				/* On complete function */
				if ( typeof self.options.onComplete === 'function' ) {
					self.options.onComplete.apply( self.elem, arguments );
				}

			} );
		},
		rtTabContent: function ( activeTabContent ) {
			var self = this,
					eachTab = self.$elem.find( 'li' ),
					tabLink = eachTab.find( 'a' );

			tabLink.each( function () {
				var link = $( this ),
						tabContent = link.attr( 'href' );
				if ( activeTabContent === 'yes' ) {
					if ( ! link.parent().hasClass( 'active' ) ) {
						$( tabContent ).addClass( 'hide' );
					}
				} else {
					$( tabContent ).addClass( 'hide' );
				}
			} );
		}
	};

	$.fn.rtTab = function ( options ) {
		return this.each( function () {
			var tab = Object.create( Tab );
			tab.init( options, this );

			/* Store Data */
			$.data( this, 'rtTab', tab );
		} );
	};

	$.fn.rtTab.options = {
		activeTab: 1,
		onComplete: null
	};

} )( jQuery, window, document );