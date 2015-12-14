<?php
/**
 * Handles all of the stylesheet (style.css) filtering / replacement, switch between dynamic and static mode, etc. pp.
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-r2
 * @require cc2_ColorSchemes
 */


if( class_exists( 'cc2_ColorSchemes_ThemeHandler' ) ) {
	return;
}

class cc2_ColorSchemes_ThemeHandler {
	
	var $arrKnownLocations = array(),
		$arrColorSchemes = array();
	
	/**
	 * @static
	 * @var    \wp_less Reusable object instance.
	 */
	protected static $instance = null;


	/**
	 * Creates a new instance. Called on 'after_setup_theme'.
	 * May be used to access class methods from outside.
	 *
	 * @see    __construct()
	 * @static
	 * @return \wp_less
	 */
	public static function init() {
		global $cc2_color_schemes;
		
		null === self::$instance AND self::$instance = new self;
		
		//$cc2_color_schemes = self::$instance;
		
		return self::$instance;
	}
	
	function __construct() {
		// init variables
		
		/*$this->init_schemes();
		
		$upload_dir = $upload_dir = wp_upload_dir();
		
		$arrLocations = array(
			$upload_dir['path'] . '/cc2/schemes/' => $upload_dir['url'] . '/cc2/schemes/',
			get_template_directory() . '/includes/schemes/' => get_template_directory_uri() . '/includes/schemes/',
		);
		
		$this->set_known_locations( $arrLocations );
		*/
		
		$this->maybe_init_color_schemes();
		
		// add filters
		
		add_filter( 'cc2_style_css', array( $this, 'switch_color_scheme') );
	}


	/*public function set_known_locations( $arrLocations = array() ) {
		$return = false;
		
		if( !empty( $arrLocations ) && is_array( $arrLocations ) ) {
			$this->arrKnownLocations = apply_filters('cc2_color_schemes_set_locations', $arrLocations );
			
			$return = true;
		}
	
		return $return;
	}*/
	
	public function maybe_init_color_schemes() {
		global $cc2_color_schemes;
		
		if( !isset( $cc2_color_schemes ) || !is_object( $cc2_color_schemes ) && class_exists('cc2pro_ColorSchemes') ) {
			$cc2_color_schemes = new cc2pro_ColorSchemes();
		}
	}
	
	
		
	public function switch_color_scheme( $url ) {
		global $cc2_color_schemes;
		$return = $url;
		
		if( isset( $cc2_color_schemes ) ) {
		
			$current_scheme = apply_filters('cc2_get_current_color_scheme', $cc2_color_schemes->get_current_color_scheme() );
			
			//new __debug( array('current_scheme' => $current_scheme ), 'switch_color_scheme' );
			
			if( !empty( $current_scheme ) && isset( $current_scheme['slug'] ) && $current_scheme['slug'] != 'default' ) {
				//new __debug( array('current_scheme' => $current_scheme ), 'switch_color_scheme = true' );
				
				$return = apply_filters('cc2_set_style_url', $current_scheme['style_url'] );
			}
		}
		
		return $return;
	}
		

}
	
add_action('cc2_init_color_schemes', array('cc2_ColorSchemes_ThemeHandler', 'init'), 11 );

