/**
 * cc2 Customizer Helper library
 * NOTE: We are operating INSIDE the customizer UI, NOT the preview frame. First one being that nice sidebar with those accordeon thingies to your LEFT ;)
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */
 
function maybe_hex( value ) {
	var $return = value;
	
	if( value.substr(0,1) != '#' ) {
		$return = '#' + value;
	}
	
	return $return;
}

( function( exports, $ ) {
	var api = top.wp;
	

	/**
	 * Color Scheme changes
	 */
	 
	jQuery(document).on('click', '#customize-control-color_scheme input[type="radio"]', function(ev) {
		//console.log( 'checked:', jQuery('#customize-control-color_scheme input[type="radio"]:checked').val() );
		//console.log( 'checked / v2:', jQuery(this).val() );
		var strPreviewScheme = this.value;
		insert_scheme_settings( strPreviewScheme );
		
		//console.log( 'switch to ' + strPreviewScheme + ':', customizer_data.color_schemes[strPreviewScheme] );
		
	});
	
	function _maybe_hex( value ) {
		var $return = value;
		
		//if( 
		
		return $return;
	}
	
	/**
	 * Parse the classes of each ID (= setting) and do a switch case to decide which control has to be used
	 */
	
	function insert_scheme_settings( scheme_slug ) {
		console.log( scheme_slug + ' was selected' );
		
		var customizer_current_settings = top.wp.customize.settings.settings,
			customizer_current_controls = top.wp.customize.settings.controls;
		
		//var control = api.control.instance(cname);
		//console.log( customizer_data.color_schemes[scheme_slug] );
		
		if( customizer_data.color_schemes[scheme_slug].default_settings == false || typeof customizer_data.color_schemes[scheme_slug].settings == 'undefined' ) {
			console.error( scheme_slug + ' has no default settings' );
			return;
		}
		
		console.log( customizer_data.color_schemes[scheme_slug].default_settings );
		
		jQuery.each( customizer_data.color_schemes[scheme_slug].default_settings, function( settingName, settingValue ) {
			console.log( customizer_current_settings[settingName], customizer_current_controls[settingName] );
			
			if( typeof customizer_current_controls[settingName] != 'undefined' ) {
				switch( customizer_current_controls[settingName].type ) {
					
					case 'image':
						// nothing to do .. yet
						break;
					case 'select':
						console.log( 'selecting ' + settingName + ' to option ' + settingValue );
						setSelectedOption( settingName, settingValue );
						
						break;
					case 'radio':
						console.log( 'checking radiobox ' + settingName + ' to ' + settingValue );
						setRadio( settingName, settingValue );
					
						break;
					case 'checkbox':
						console.log( 'checking checkbox ' + settingName + ' to ' + settingValue );
					
						setCheckbox( settingName, settingValue );
					
						break;
						
					case 'text':
						console.log( 'setting text field ' + settingName + ' to ' + settingValue );
					
						setTextField( settingName, settingValue );
					
						break;
					case 'color':
						console.log( 'changing color of ' + settingName + ' to ' + settingValue );
					
						mySetColor( settingName, maybe_hex( settingValue ) );
					
						break;
					
					case 'description':
					case 'label':
						// nothing to do	
					default:
						break;
				}
			}
		} );
	
	}
	
	
	/**
	 * Custom Color Control
	 * 
	 * @see http://shibashake.com/wordpress-theme/wordpress-theme-customizer-javascript-interface
	 */

	jQuery(document).on('click', '.color-picker-set-transparent', function(elem) {
		//console.log(  );
		var myId = jQuery(this).parents('li.customize-control').attr('id');
		var myControlId = myId.replace('customize-control-', '');
		
		console.log( 'myControlId', myControlId );
		
		//console.log( top.wp.customize.control.instance(  );
		
		mySetColor( myControlId, 'transparent' );
		/*var parentContainer = jQuery(this).parents('.cc2-customize-control-content');
		console.log ( 'parentContainer parent parent', parentContainer.parent().parent(), parentContainer.parents('li.customize-control') );
		*/
		
	});

	/**
	 * Fixing the background color / image refresh
	 */

	/*
	jQuery(document).on('click', '#customize-control-background_color .color-picker-hex', function( elem ) {
		window.console.log( 'background_color changed', jQuery(this).val() );
	} );*/

	/**
	 * Hide slideshow options if post slideshow + display notice about different post slider types
	 */
	 
	jQuery(document).on('change', '#customize-control-cc_slideshow_template select', function(e ) {
		var currentOption = jQuery(this).prop('options')[ jQuery(this).prop('selectedIndex')];
		var currentOptionText = jQuery( currentOption ).text();
		
			
		
		//window.console.info( 'optionsText', jQuery( currentOption).text(), 'vs', currentOptionText );
		//jQuery( jQuery('#customize-control-cc_slideshow_template select').prop('options')[ jQuery('#customize-control-cc_slideshow_template select').prop('selectedIndex') ] ).val()
		
		
	});

	/*jQuery(document).on('click', '#customize-control-background_color', function( elem ) {
		var myControlId = this.attr('id').replace('customize-control-', '' );
		
		
	});*/
	//function 
	
	function getInputField( slug, add_to_selector ) {
		var selector = 'input';
		if( typeof add_to_selector != 'undefined' && add_to_selector != '' ) {
			selector += add_to_selector;
		}
		
		return top.wp.customize.control.instance( slug ).container.find( selector );
	}
	
	
	function getSelectedOption( slug ) {
		return top.wp.customize.control.instance( slug ).container.find( 'option:selected' );
	}
	
	function setSelectedOption( slug, option_value ) {
		var availOptions = top.wp.customize.control.instance( slug ).container.find('option');
		
		availOptions.each( function( i, elem ) {
			if( elem.value === option_value ) {
				elem.selected = 'selected';
				// alternative:
				elem.selectedIndex = i;
			}
		});
	}
	
	function getCheckbox( slug ) {
		return getInputField( slug, '[type="checkbox"]:checked' );
	}
	
	function setCheckbox( slug, check_value, input_type ) {
		if( typeof input_type == 'undefined' ) { // defaults to type="checkbox"
			input_type = 'checkbox';
		}
		
		var checkboxes = top.wp.customize.control.instance( slug ).container.find('input[type="'+input_type+'"]');
		checkboxes.each( function( i, elem ) {
			if( elem.value === check_value ) {
				elem.checked = 'checked';
			} else {
				elem.checked = '';
			}
		});
		
		
	}
	
	function getRadio( slug ) {
		return getInputField( slug, '[type="radio"]:checked' );
	}
	
	function setRadio( slug, check_value ) {
		setCheckbox( slug, check_value, 'radio');
	}
	
	function setTextField( slug, value ) {
		top.wp.customize.control.instance( slug ).container.find('input[type="text"]').val( value );
		return;
	}
	
	function getTextField( slug ) {
		var $return = getInputField( slug, '[type="text"]' );
		
		return $return.val();
		
		//return top.wp.customize.control.instance( slug ).container.find('input[type="text"]').val();
	}
	
	function getColorPickerColor( cname ) {
		var api = top.wp.customize;
		
		var control = api.control.instance(cname);
			picker = control.container.find('.color-picker-hex');
			 
		//picker.val( control.setting() ); return;
		return picker.val();
	}

	function mySetColor(cname, newColor) {
		var api = top.wp.customize;
		
		var control = api.control.instance(cname);
		var picker = control.container.find('.color-picker-hex')[0];
		
		
		
		
		//console.log( control.setting() );
		
		control.setting.set(newColor);
		
		// set preview color
		jQuery( control.container.find('.wp-color-result')[0] ).css('background-color', newColor );
		//control.container.find('.wp-color-result')
		
		//console.log( control.setting() );
		
		picker.value = control.setting();
		return;
		
		//picker.val( control.setting() ); return;
	}
} )( top.wp, jQuery );
