var showNotice, adminMenu, columns;
(function($){
// sidebar admin menu
adminMenu = {

	init : function() {
		$('ul#bbAdminMenu li.bb-menu div.bb-menu-toggle').each( function() {
			if ( $(this).siblings('div.bb-menu-sub-wrap').length )
				$(this).click(function(){ adminMenu.toggle( $(this).siblings('div.bb-menu-sub-wrap') ); });
			else
				$(this).hide();
		});

		$('#bbAdminMenu li.bb-menu.bb-menu-separator a').click(function(){
			if ( $('body').hasClass('bb-menu-folded') ) {
				adminMenu.fold(1);
				deleteUserSetting( 'fm' );
			} else {
				adminMenu.fold();
				setUserSetting( 'fm', 'f' );
			}
			return false;
		});

		if ( $('body').hasClass('bb-menu-folded') ) {
			this.fold();
		}
		this.restoreMenuState();
	},

	restoreMenuState : function() {
		$('ul#bbAdminMenu li.bb-menu.bb-menu-has-submenu').each(function(i, e) {
			var v = getUserSetting( 'm'+i );
			if ( $(e).hasClass('bb-menu-current') ) return true; // leave the current parent open

			if ( 'o' == v ) $(e).addClass('bb-menu-open');
			else if ( 'c' == v ) $(e).removeClass('bb-menu-open');
		});
	},

	toggle : function(el) {
		el['slideToggle'](150, function(){el.css('display','');}).parent().toggleClass( 'bb-menu-open' );

		$('ul#bbAdminMenu li.bb-menu.bb-menu-has-submenu').each(function(i, e) {
			var v = $(e).hasClass('bb-menu-open') ? 'o' : 'c';
			setUserSetting( 'm'+i, v );
		});

		return false;
	},

	fold : function(off) {
		if (off) {
			$('body').removeClass('bb-menu-folded');
			$('#bbAdminMenu li.bb-menu.bb-menu-has-submenu').unbind();
		} else {
			$('body').addClass('bb-menu-folded');
			$('#bbAdminMenu li.bb-menu.bb-menu-has-submenu').hoverIntent({
				over: function(e){
					var m, b, h, o, f;
					m = $(this).find('div.bb-menu-sub-wrap');
					b = m.parent().offset().top + m.height() + 1; // Bottom offset of the menu
					h = $('#bbWrap').height(); // Height of the entire page
					o = 60 + b - h;
					f = $(window).height() + $('body').scrollTop() - 15; // The fold
					if (f < (b - o)) {
						o = b - f;
					}
					if (o > 1) {
						m.css({'marginTop':'-'+o+'px'});
					} else if ( m.css('marginTop') ) {
						m.css({'marginTop':''});
					}
					m.addClass('bb-menu-sub-open');
				},
				out: function(){ $(this).find('div.bb-menu-sub-wrap').removeClass('bb-menu-sub-open').css({'marginTop':''}); },
				timeout: 220,
				sensitivity: 8,
				interval: 100
			});
		}
	}
};

$(document).ready(function(){
	adminMenu.init();
	$('thead .check-column :checkbox, tfoot .check-column :checkbox').click( function() {
		$(this).parents( 'table' ).find( '.check-column :checkbox' ).attr( 'checked', $(this).is( ':checked' ) ? 'checked' : false );
	} );
});


})(jQuery);
