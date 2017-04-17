/**
 * Independet customizer helper
 * NOTE: We are operating INSIDE the customizer UI (NOT the preview frame) - that nice sidebar to your left .. ;)
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-rc1
 */
//( function( exports, $ ) {

jQuery(function() {
	var api = top.wp;
	
	/**
	 * Custom Color Control
	 * 
	 * @see http://shibashake.com/wordpress-theme/wordpress-theme-customizer-javascript-interface
	 */

	jQuery( document ).on('change', '#customize-control-color_scheme_bottom_nav select', function( e ) {
		console.log( 'bottom_nav_scheme', this, this.value );
		//e.preventDefault();	
	
		
		if( this.value == 'dark' ) {
			//console.log( customizer_data.navbar.dark.bottom_nav );
			jQuery.each( customizer_data.navbar.dark.bottom_nav, function( strSetting, strValue ) {
				//console.log( strSetting, strValue );
				mySetColor( strSetting, strValue )

			});
		} else if( this.value == 'light' ) {
			//console.log( customizer_data.navbar.light.bottom_nav );
			jQuery.each( customizer_data.navbar.light.bottom_nav, function( strSetting, strValue ) {
				//console.log( strSetting, strValue );
				//if( strValue != '' ) {
					mySetColor( strSetting, strValue )
				//}

			});
		}
	});
	
	jQuery( document ).on('change', '#customize-control-color_scheme_top_nav select', function( e ) {
		console.log( 'top_nav_scheme', this, this.value );
		//e.preventDefault();	
	
		
		if( this.value == 'dark' ) {
			//console.log( customizer_data.navbar.dark.bottom_nav );
			jQuery.each( customizer_data.navbar.dark.top_nav, function( strSetting, strValue ) {
				//console.log( strSetting, strValue );
				mySetColor( strSetting, strValue )
				

			});
		} else if( this.value == 'light' ) {
			//console.log( customizer_data.navbar.light.bottom_nav );
			jQuery.each( customizer_data.navbar.light.top_nav, function( strSetting, strValue ) {
				//console.log( strSetting, strValue );
				//if( strValue != '' ) {
					mySetColor( strSetting, strValue )
				//}

			});
		}
		
		
	});
	
	/**
	 * Color Schemes helper
	 * 
	 * Features:
	 * - change colors if scheme is switched (preview)
	 * - reset colors if scheme is switched back to default 
	 * - preview colors
	 */
	 

	/*
	jQuery( document).on('click','#customize-control-dark_top_nav',  function(event) {
		event.preventDefault();
		event.stopPropagation();
		//console.log( api )
		console.log( this, 'fires');
		
		// action: user tried to activate this setting
		if( is_checked('#customize-control-dark_top_nav input[type=checkbox]') == false ) { 
			//console.log( 'check box');
			
			//console.log( event, is_checked('#customize-control-dark_top_nav input[type=checkbox]') );
			
			// tear down the building .. and then construct it from ground up, again m(
			// thanks WP UI "developers" for successfully preventing getting any proper work done .. gnah.
			
			// first, reset colors if true
			
			jQuery.each( customizer_data.navbar.dark.top_nav, function( strSetting, value ) {
				mySetColor( strSetting, value )

			}) 
			jQuery('#customize-control-dark_top_nav input[type=checkbox]').prop('checked', true );
			
			
			
			// second, let regular event run through (more or less)
			//console.log( event, this );
			
		} else {
			//console.log( 'uncheck box')
			
			jQuery.each( customizer_data.navbar.light.top_nav, function( strSetting, value ) {
				mySetColor( strSetting, value )

			}) 
			
			jQuery('#customize-control-dark_top_nav input[type=checkbox]').prop('checked', false );

		}
		
	})*/

	/*Query(document).on('click', '#customize-control-dark_top_nav', function(event) {
		//console.log( 'clicked dark_top_nav', 'status:', this.id,  is_checked('#' + this.id + '' )  );
		
		if( typeof event.handled == 'undefined' || event.handled != true ) {
			event.handled = true;
			console.log( event, is_checked('#customize-control-dark_top_nav input[type=checkbox]') )
		}
		
		//console.log( '#customize-control-dark_top_nav input[type=checkbox] checked?', jQuery('#customize-control-dark_top_nav input')[0].checked  );
		/*if( jQuery('#customize-control-dark_top_nav input')[0].checked != false ) {
			console.info('refreshing values!', customizer_data );
		}*/
		
		
		// fire update .. or not
		
		
		//mySetColor( myControlId, 'transparent' );
		//var parentContainer = jQuery(this).parents('.cc2-customize-control-content');
		//console.log ( 'parentContainer parent parent', parentContainer.parent().parent(), parentContainer.parents('li.customize-control') );
		
		
	//});
	

	function getColorPickerColor( cname ) {
		var api = top.wp.customize;
		
		var control = api.control.instance(cname);
			picker = control.container.find('.color-picker-hex');
			 
		picker.val( control.setting() ); return;
	}

	function mySetColor(cname, newColor) {
		var api = top.wp.customize;
		
		var control = api.control.instance(cname);
			picker = control.container.find('.color-picker-hex');
			 
		control.setting.set(newColor);
		picker.val( control.setting() ); 
		
		jQuery('#customize-control-' + cname + ' .wp-color-result').css('background-color', '#'+newColor);
		
		return;
	}

} );

//} )( top.wp, jQuery );

// helper library
	/**
	 * Tears down the complete building and constructs it back from the ground ..
	 * Total raging madness, but WP effectually overrules all other attempts of properly using JS ..
	 */
	 
	

	/*function rearchitect( selector, trigger, action ) {
		jQuery( document ).on( trigger, selector, function(e) {
			e.preventDefault();
			
			
		})
		
	}*/


	// :checked selector in jQuery is flawed, thus we try going for a native approach
	function is_checked( selector ) {
		$return = false;

		//jQuery('input[name="load_smartmenus_js"]').attr('checked')
		if( typeof document.querySelector != 'undefined') { // yay for modern browsers!		
			$return = ( document.querySelector( selector ).checked );
		} else { // ugh .. prolly nasty internet exploiter and his ugly pals
			$return = jQuery( selector ).attr('checked');
		}
		
		return $return;
	}
	



