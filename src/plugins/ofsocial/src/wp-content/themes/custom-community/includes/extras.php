<?php
/**
 * Custom functions that act independently of the theme templates
 *
 * Eventually, some of the functionality here could be replaced by core features
 */
 

/**
 * @package cc2
 * @since 2.0
 * @author Fabian Wolf
 */

if( !class_exists('cc2_Helper') ) :

	class cc2_Helper {
		static $strOptionPrefix = 'cc2_backup_';
		
		/**
		 * Replacement for is_page, is_admin and is_plugin_page. Does not (yet) count in the AJAX situation. 
		 * NOTE: A check like admin.page?page=my-page&some=parame would also be valid if it was admin.page?page=my-page&some-param&other=param
		 * 
		 * @param [optional]string $path	Could be a single file, a query or a more or less full path, eg. admin.php?page=my-page
		 * @return boolean $return			Returns true if this is indeed is_admin() (w/o the optional $path parameter), or false if not (or if we are not on that specific url / path)
		 */
		
		public static function is_admin_page( $path = false ) {
			global $plugin_page;
			$return = false;
			
			// default
			$return = is_admin();
			
			if( !empty( $path ) && $return != false ) { // ONLY check path if its INDEED in_admin
				// page could either be: admin.php, a query: ?page=some-page or a more or less full path: options.php?page=cc2-dashboard
				
				$strSearchedFile = basename( $_SERVER['PHP_SELF'] );
				
				// check if this is a query or maybe even a full path with query - and if the currently called file IS that path or got that query
				
				if( strpos( $path, '?' ) !== false ) { // yep, we got a query
					$x = explode('?', trim($path) );
					
					if( empty( $x[0] ) ) { // only a query
						$arrSearchedQuery = parse_str( $path );
					} else { // query with file
						$strSearchedFile = $x[0];
						$arrSearchedQuery = parse_str( $x[1] );
					}
					
				} else { // nope, not a query
					$strSearchedFile = $path;
				}
				
				// check file
				
				if(  basename( $_SERVER['PHP_SELF'] ) == $strSearchedFile ) {
					$return = true;
				}
				
				// check query
				
				if( !empty( $arrSearchedQuery ) && !empty( $_GET) ) {
					natcasesort( $arrSearchedQuery ); // sort it for later comparison
					
					// copy GET
					$arrCurrentQuery = $_GET;
					natcasesort( $arrCurrentQuery );
					
					// prepare comparison
					$strCurrentQuery = implode('&', $arrCurrentQuery );
					$strSearchedQuery = implode('&', $arrSearchedQuery );
					
					if( $strCurrentQuery == $strSearchedQuery ) {
						$return = true;
					}
					
				}
			}
			
			
			return $return;
		}
		
		
			
		/**
		 * Adapted wp_parse_args for options, settings, etc.
		 */

		public static function parse_args( $defaults = false, $new_params = array() ) {
			$return = $defaults;
			
			if( !empty( $defaults ) && !empty( $new_params ) ) {
				// first, take a look if there are array keys NOT in $defaults
				$arrNewKeys = array_keys( $new_params );
				$arrDefaultKeys = array_keys( $defaults );
				
				$arrOtherKeys = array_diff( $arrNewKeys, $arrDefaultKeys );
				
				// cycle through the intersect
				foreach( $arrDefaultKeys as $strKey ) {
					if( $return[ $strKey ] != $new_params[ $strKey ] ) {
						$return[ $strKey ] = $new_params[ $strKey ];
					}
				}
				
				// then cycle through the new keys
				
				if( !empty( $arrOtherKeys ) ) {
					foreach( $arrOtherKeys as $strOtherKey ) {
						$return[ $strOtherKey ] = $new_params[ $strOtherKey ];
					}
				}
			}
			
			
			return $return;
		}
		
		
		public static function update_settings_backup( $strOptionSuffix = 'settings', $data = false ) {
			$return = false;
			
			if( !empty( $data ) ) {
				$return = self::backup_settings( $data, $strOptionSuffix );
			}
			
			return $return;
		}
		
		public static function backup_settings( $data, $strOptionSuffix = 'settings' ) {
			$return = false;
			
			if( !empty( $data ) && self::is_assoc( $data ) != false ) {
				if( self::has_settings_backup( $strOptionSuffix ) != false ) {
					$created = self::get_settings_backup( $strOptionSuffix, '_created' );
					
					if( !empty($created) && is_int( $created ) ) {
						$data['_created'] = $created;
					}
				}
				
				$data['_modified'] = time();
				
				$return = update_option( self::$strOptionPrefix . $strOptionSuffix, $data );
			}
			
			return $return;
		}
		
		public static function get_settings_backup( $strOptionSuffix = 'settings', $strFieldName = false ) {
			$return = false;
		
			
			if( !empty( $strOptionSuffix ) ) {
				$settings = get_option( self::$strOptionPrefix . $strOptionSuffix, false );
			}
			
			if( !empty( $settings ) && self::is_assoc( $settings) != false ) {
				
				if( !empty( $strFieldName ) ) {
					$return = ( isset( $settings[ $strFieldName ] ) ? $settings[ $strFieldName ] : false );
				} else {
					$return = $settings;
				}
			}
			
			return $return;
		}
		
		public static function has_settings_backup( $strOptionSuffix = 'settings' ) {
			$return = false;
			
			$settings = get_option( self::$strOptionPrefix . $strOptionSuffix, false );
			if( !empty( $settings ) && self::is_assoc( $settings ) != false ) {
				$return = true;
			}
			
			return $return;
		}
		
		
		
		public static function is_assoc( $array = false ) {
			$return = false;
			
			if( !empty( $array ) && is_array( $array) ) {
				foreach($array as $key => $value) {
					if ($key !== (int) $key) {
						$return = true;
					}
				}
			}
			
			return $return;
		}
		
		
	}


/**
 * Adds a few wrapper functions for less serious (aka lazy) developers
 * 
 * @author Fabian Wolf
 */

	if( !function_exists('cc2_parse_args' ) ) :
		function cc2_parse_args( $defaults = false, $new_params = array() ) {
			return cc2_Helper::parse_args( $defaults, $new_params );
		}
	endif;

	if( !function_exists('is_admin_page') ) :
	
		function is_admin_page( $path = false ) {
			return cc2_Helper::is_admin_page( $path );
		}

	endif;

endif;
 
 /*
 * @package _tk
 */

/**
 * Get our wp_nav_menu() fallback, wp_page_menu(), to show a home link.
 */
if( !function_exists( '_tk_page_menu_args' ) ):
function _tk_page_menu_args( $args ) {
	$args['show_home'] = true;
	return $args;
}
add_filter( 'wp_page_menu_args', '_tk_page_menu_args' );
endif;

/**
 * Adds custom classes to the array of body classes.
 */
if( !function_exists( '_tk_body_classes' ) ) :
function _tk_body_classes( $classes ) {
	// Adds a class of group-blog to blogs with more than 1 published author
	if ( is_multi_author() ) {
		$classes[] = 'group-blog';
	}

	return $classes;
}
add_filter( 'body_class', '_tk_body_classes' );
endif;

/**
 * Filter in a link to a content ID attribute for the next/previous image links on image attachment pages
 */
if( !function_exists( '_tk_enhanced_image_navigation' ) ) :
function _tk_enhanced_image_navigation( $url, $id ) {
	if ( ! is_attachment() && ! wp_attachment_is_image( $id ) )
		return $url;

	$image = get_post( $id );
	if ( ! empty( $image->post_parent ) && $image->post_parent != $id )
		$url .= '#main';

	return $url;
}
add_filter( 'attachment_link', '_tk_enhanced_image_navigation', 10, 2 );
endif;

/**
 * Filters wp_title to print a neat <title> tag based on what is being viewed.
 */
/*if( !function_exists( '_tk_wp_title' ) ) :
function _tk_wp_title( $title, $sep ) {
	global $page, $paged;

	if ( is_feed() )
		return $title;

	// Add the blog name
	$title .= get_bloginfo( 'name' );

	// Add the blog description for the home/front page.
	$site_description = get_bloginfo( 'description', 'display' );
	if ( $site_description && ( is_home() || is_front_page() ) )
		$title .= " $sep $site_description";

	// Add a page number if necessary:
	if ( $paged >= 2 || $page >= 2 )
		$title .= " $sep " . sprintf( __( 'Page %s', 'cc2' ), max( $paged, $page ) );

	return $title;
}
add_filter( 'wp_title', '_tk_wp_title', 10, 2 );
endif;
*/
