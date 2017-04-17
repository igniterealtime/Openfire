<?php
/**
 * A few helper functions and function wrappers for the Color Scheme implementation
 * @author Fabian Wolf
 * @since 2.0r2
 * @package cc2
 */

if( !function_exists( 'cc2_init_scheme_helper' ) ) {
	function cc2_init_scheme_helper() {
		global $cc2_color_schemes;
		
		//$cc2_color_schemes = $GLOBALS['cc2_color_schemes'];
		if( empty( $cc2_color_schemes ) || !is_object( $cc2_color_schemes ) ) {
			//new __debug( array( 'cc2_color_schemes' => $cc2_color_schemes ), 'init_scheme_helper starts' );
			
			
			//new __debug( defined( 'CC2_COLOR_SCHEMES_CLASS' ) ? 'color_scheme_class is defined' : 'something went HAGGARDLY wrong!' , 'init_scheme_helper starts' );
			
			do_action('cc2_init_color_schemes'); // should be unset-table / replaceable via plugin / filter hooks
			
				
			if( class_exists( 'cc2_ColorSchemes' ) ) {
			
				$cc2_color_schemes = cc2_ColorSchemes::init();
			}

		}
		
		//new __debug( $cc2_color_schemes, 'init_scheme_helper ends' );
		
		return $cc2_color_schemes;
	}
	
	cc2_init_scheme_helper();
	
}

/**
 * Simple function to set the default scheme if none is present. Mostly used during theme and plugin setup.
 * @author Fabian Wolf
 * @since 2.0r2
 * @package cc2
 */

if( !function_exists( 'cc2_set_default_scheme' ) ) {
	function cc2_set_default_scheme() {
		$current_scheme = get_theme_mod('color_scheme', false );
		
		if( empty( $current_scheme) ) {
			$return = set_theme_mod( 'color_scheme', 'default');
		}
		
		return $return;
	}
	
}


if( !function_exists('cc2_get_current_color_scheme') ) {
	function cc2_get_current_color_scheme() {
		$return = false;
		global $cc2_color_schemes;
		
		//cc2_init_scheme_helper();
		
		/*
		if( !isset( $cc2_color_schemes ) ) {
			
			do_action('cc2_init_color_schemes'); // should be unset-table / replaceable via plugin / filter hooks
			
			if( !isset( $cc2_color_schemes ) && isset( $cc2_color_scheme_class) ) {
				$cc2_color_scheme_class::init();
			}
			
		}*/
		
		
		$return = apply_filters('cc2_get_current_color_scheme', $cc2_color_schemes->get_current_color_scheme() );
				
		return $return;
	}
	
}

if( !function_exists('cc2_get_color_schemes') ) {
	function cc2_get_color_schemes( $include_settings = false ) {
		global $cc2_color_schemes;
		$return = false;
		
		//$cc2_color_schemes = $GLOBALS['cc2_color_schemes'];
		
		//new __debug( $cc2_color_schemes, __METHOD__ . ': cc2_color_schemes' );
		
		cc2_init_scheme_helper();
		
		$return = apply_filters('cc2_get_all_color_schemes', $cc2_color_schemes->get_color_schemes( $include_settings ) );
		
		
		
		
		//new __debug( $return, __METHOD__ . ': available color schemes' );
		
		return $return;
	}
}

if( !function_exists('cc2_get_color_scheme_by_slug') ) {
	function cc2_get_color_scheme_by_slug( $scheme_slug = false ) {
		$return = false;
		global $cc2_color_schemes;
	
		//if( !empty( $scheme_slug ) && function_exists( 'cc2_get_color_schemes') ) {
		
		if( !empty( $scheme_slug ) && isset( $cc2_color_schemes ) && method_exists( $cc2_color_schemes, 'get_single_color_scheme' ) ) {
			$return = $cc2_color_schemes->get_single_color_scheme( $scheme_slug );
			
			$return = apply_filters('cc2_add_missing_scheme_variables', $return );
		/*
			$arrSchemes = cc2_get_color_schemes();
			
			//new __debug( array('scheme_slug' => $scheme_slug, 'schemes' => $arrSchemes ), __METHOD__ );
			
			if( isset( $arrSchemes[ $scheme_slug ] ) != false ) {
				
				// check for missing settings
				$return = $arrSchemes[ $scheme_slug ];
				
				
				if( empty( $return['slug'] ) != false ) {
					$return['slug'] = $scheme_slug;
				}
			
				//$return = $cc2_color_schemes->add_missing_scheme_variables( $return );
				$return = apply_filters('cc2_add_missing_scheme_variables', $return );
			}
		*/	
		}

		return $return;
	}
	
}
