<?php
/**
 * Welcome Page Class
 * 
 * Shows a feature overview for the new version (major). Planned for the future: Recommended plugins, etc.
 *
 * Strongly inspired by the WooCommerce Welcome Page Class
 *
 * @author 		Fabian Wolf
 * @package		cc2
 * @version     2.0.25
*/

if ( ! defined( 'ABSPATH' ) ) {
	exit; // Exit if accessed directly
}

if( !class_exists( 'cc2_Admin_Welcome' ) ) :

class cc2_Admin_Welcome {
	function __construct() {
		add_action( 'admin_menu', array( $this, 'add_welcome_screen' ) );
		add_action( 'admin_head', array( $this, 'admin_head' ) );
		//add_action( 'admin_init', array( $this, 'add_welcome_redirect'    ) );
	}
	
	/*
	function add_welcome_redirect() {
		
	}*/
	
	function add_welcome_screen() {
		$welcome_page_name = __('About Custom Community', 'cc2');
		$welcome_page_title = __('Welcome To Custom Community', 'cc2');
		
		//$page = add_dashboard_page( $welcome_page_title, $welcome_page_name, 'manage_options', 'wc-about', array( $this, 'about_screen' ) );
		$page = add_theme_page(
			$welcome_page_name,
			$welcome_page_title,
			'edit_theme_options',
			'cc2-welcome',
			array($this, 'admin_page_welcome' )
		);
		
		add_action( 'admin_print_styles-'. $page, array( $this, 'init_admin_css' ) );
		
		
	}
	
	public function init_admin_css() {
		$current_theme = wp_get_theme();
		$version = ( is_object( $current_theme ) ? $current_theme->Version : '2.0' );
		
		wp_register_style ('font-awesome', get_template_directory_uri() . '/includes/resources/fontawesome/css/font-awesome.min.css' );
		
		wp_enqueue_style( 'cc2-welcome', get_template_directory_uri() . '/includes/admin/css/welcome.css', array('font-awesome'), $version );
		
	}
	
	function admin_head() {
		remove_submenu_page( 'themes.php', 'cc2-welcome' );
		
	}
	
	function admin_page_welcome() {
		// prepare variables
		$import_old_settings = array(
			'settings' => __('Theme Settings', 'cc2' ),
			'widgets' => __('Widgets', 'cc2' ),
			'slideshow' => __('Slideshow', 'cc2' ),
		);
		
		$theme_settings_url = admin_url( apply_filters('cc2_admin_settings_url', 'themes.php') . '?page=cc2-settings' );
		$theme_customize_url = apply_filters('cc2_admin_customizer_url', admin_url( 'customize.php') );
		$theme_rtfm_url = apply_filters( 'cc2_rtfm_url','https://github.com/Themekraft/Custom-Community/wiki' );
		
		
		$theme_return_url = apply_filters( 'cc2_return_url', get_query_var( 'return', admin_url('index.php') ) );
		
		$arrPopulateVars = array( 'theme_settings_url' => $theme_settings_url, 'theme_customize_url' => $theme_customize_url, 'theme_rtfm_url' => $theme_rtfm_url, 'theme_return_url' => $theme_return_url );
		
		// fetch required templates
		$this->get_welcome_header( $arrPopulateVars );
		
		include( get_template_directory() . '/includes/admin/templates/welcome.php' );
		
		$this->get_welcome_footer();
	}
	
	function get_welcome_header( $env_vars = false ) {

		// Flush after upgrades
		/*if ( ! empty( $_GET['wc-updated'] ) || ! empty( $_GET['wc-installed'] ) )
			flush_rewrite_rules();
		*/

		// Drop minor version if 0
		//$major_version = substr( WC()->version, 0, 3 );
		
		
		
		$current_theme = wp_get_theme();
		$version = ( is_object( $current_theme ) ? $current_theme->Version : '2.0' );
		$major_version = substr( $version, 0, 3 );
		
		if( !empty( $env_vars ) && is_array( $env_vars ) != false ) {
			extract( $env_vars, EXTR_SKIP );
		}
		
		include( get_template_directory() . '/includes/admin/templates/welcome-header.php' );
	}
	
	
	function get_welcome_footer( $env_vars = false ) {
		if( !empty( $env_vars ) && is_array( $env_vars ) != false ) {
			extract( $env_vars, EXTR_SKIP );
		}
		
		
		include( get_template_directory() . '/includes/admin/templates/welcome-footer.php' );
	}
}

new cc2_Admin_Welcome();

endif; // endif !class_exists
