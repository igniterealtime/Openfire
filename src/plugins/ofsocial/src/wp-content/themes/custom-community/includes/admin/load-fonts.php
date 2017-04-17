<?php
/**
 * A try to improve the font loading situation
 */

if( !function_exists('cc2_customizer_load_fonts') ) :
	function cc2_customizer_load_fonts( $fonts = array() ) {
		global $cc2_font_family;
		
		if( empty( $cc2_font_family ) ) {
			if( !defined('THEME_CONFIG') ) {
				require_once( get_template_directory() . '/includes/admin/theme-config.php' );
			}
			
			$theme_config = maybe_unserialize( THEME_CONFIG );
			$cc2_font_family = $theme_config['fonts'];
		}
		
		// If TK Google Fonts is activated get the loaded Google fonts!
		$tk_google_fonts_options = get_option('tk_google_fonts_options', false);

		// Merge Google fonts with the font family array, if there are any
		if( !empty( $tk_google_fonts_options) && isset($tk_google_fonts_options['selected_fonts']) ) {

			foreach ($tk_google_fonts_options['selected_fonts'] as $key => $selected_font) {
				$selected_font = str_replace('+', ' ', $selected_font);
				$cc2_font_family[$selected_font] = $selected_font;
			}

		}

		return $cc2_font_family;
	}

endif;
