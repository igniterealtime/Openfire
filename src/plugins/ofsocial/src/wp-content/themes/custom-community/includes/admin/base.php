<?php 
/**
 * CC2 Admin UI Base class
 * Mostly used for registering JS and CSS files, so they may be dynamically loaded and not clutter the regular Admin interface
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */


if( file_exists( get_stylesheet_directory() . '/includes/theme-config.php' ) && !defined('CC2_THEME_CONFIG') ) {
	
	include_once( get_stylesheet_directory() . '/includes/theme-config.php' );
}


add_action('admin_init', array( 'cc2_Admin_Base', 'get_instance'), 10 );
 
class cc2_Admin_Base {
	var $themePrefix = 'cc2_',
	$arrSections = array(
		'getting-started' => array(
			'title' => 'Getting Started',
			'settings_slug' => 'cc2_options',
		),
		'slider-options' => array(
			'title' => 'Slideshow',
			'settings_slug' => 'cc2_slider_options',
		),
		'advanced-settings' => array(
			'title' => 'Advanded Settings',
			'settings_slug' => 'cc2_advanced_settings',
		),
	),
	$strPageName = 'cc2-settings';

	/**
	 * Plugin instance.
	 *
	 * @see get_instance()
	 * @type object
	 */
	protected static $instance = NULL;
		
	/**
	 * Implements Factory pattern
	 * Strongly inspired by WP Maintenance Mode of Frank Bueltge ^_^ (@link https://github.com/bueltge/WP-Maintenance-Mode)
	 * 
	 * Access this plugins working instance
	 *
	 * @wp-hook after_setup_theme
	 * @return object of this class
	 */
	public static function get_instance() {

		NULL === self::$instance and self::$instance = new self;

		return self::$instance;
	}
	
	function __construct()  {
		// check for global config (definition)
		if( defined('CC2_THEME_CONFIG' ) ) {
			
			$config = maybe_unserialize( CC2_THEME_CONFIG );
			$this->arrSections = $config['admin_sections'];
		}
		
		add_action('wp_admin_scripts', array( $this, 'init_assets' ) );
	}

	
	public function init_assets() {
		// register base files
		// load the aid against JS errors (@see https://github.com/andyet/ConsoleDummy.js)
		wp_register_script( 'consoledummy', get_template_directory_uri() . '/includes/js/SlimConsoleDummy.min.js' );

		
		wp_register_script('cc-admin-ajaxhooks', get_template_directory_uri() . '/includes/admin/js/ajaxhooks.js', array('jquery') );
		
		wp_register_script('cc-admin-helper', get_template_directory_uri().'/includes/admin/js/admin-helper.js', array('jquery') );
		wp_register_script('cc-admin-js', get_template_directory_uri().'/includes/admin/js/admin.js', array( 'jquery', 'cc-admin-helper' ) );
		
		wp_register_style('cc-admin-css', get_template_directory_uri() . '/includes/admin/css/admin.css' );

		// customizer helper
		wp_register_script('cc2-customizer-helper', get_template_directory_uri() . '/includes/admin/js/customizer-helper.js', array('jquery', 'wp-color-picker') );
		
		


		// avoid rendering blockers / slow downs during debug mode
		if( !defined('CC2_THEME_DEBUG') ) {
		

			wp_register_style('cc_tk_zendesk_css', '//assets.zendesk.com/external/zenbox/v2.6/zenbox.css' );
			wp_register_script('cc_tk_zendesk_js', '//assets.zendesk.com/external/zenbox/v2.6/zenbox.js');	
		}
		
		/**
		 * NOTE: Possible fix for the external script rendering blockers
		 */
		
		wp_register_script('cc-support-helper', get_template_directory_uri() . '/includes/admin/js/support-helper.js', array('jquery', 'cc-admin-ajaxhooks'));
		

		/**
		 * NOTE: Rest of the assets are registered for on-demand loading
		 */

		// load bootstrap css
		wp_register_style( 'cc-bootstrap', get_template_directory_uri() . '/includes/resources/bootstrap/dist/css/bootstrap.min.css' );

		// load bootstrap js
		wp_register_script('cc-bootstrap-js', get_template_directory_uri().'/includes/resources/bootstrap/dist/js/bootstrap.min.js', array('jquery') );

		// load the glyphicons
		wp_register_style( 'cc-glyphicons', get_template_directory_uri() . '/includes/resources/glyphicons/css/bootstrap-glyphicons.css' );

		// load animate.css
		wp_register_style( 'cc-animate-css', get_template_directory_uri() . '/includes/resources/animatecss/animate.min.css' );

	}
}


function is_cc2_admin_page( $current_page_slug, $allowed_suffixes = array() ) {
	$return = false;
	
	$arrKnownSuffixes = apply_filters('is_cc2_admin_page_known_suffixes', $allowed_suffixes );
	
	//new __debug( $current_page_slug, 'load_assets suffix' );
	//new __debug( $arrKnownSuffixes, 'allowed suffixes (after filter)' );
	
	if( !empty( $arrKnownSuffixes ) ) {
		if( !is_array( $arrKnownSuffixes ) ) {
			$arrKnownSuffixes = array( $arrKnownSuffixes );
		}
		//new __debug( $arrKnownSuffixes, 'allowed suffixes (after is_array check)' );
		
		foreach( $arrKnownSuffixes as $strSuffix ) {
			if( strpos( $current_page_slug, $strSuffix ) !== false ) {
				$return = true;
				break;
			}
		}
	}
	
	//new __debug( $return ? 'true' : 'false', 'return' );
	
	return $return;
}
