<?php

// some initial variables, some of them being very important for updates etc.
define('CC2_THEME', '2.0.28' );

// required for most stuff to work properly
if( file_exists( get_stylesheet_directory() . '/includes/theme-config.php' ) && !defined('CC2_THEME_CONFIG') ) {
	include_once( get_stylesheet_directory() . '/includes/theme-config.php' );
}


/**
 * NOTE: Uncomment the following line if something fails to work - enables all built-in theme debugging functions! ;-)
 */
//define('CC2_THEME_DEBUG', true );

// First, include debugging helper class, if the helper plugin isnt activated
if( !class_exists('__debug') && file_exists( get_template_directory() . '/includes/debug.class.php' ) ) :
	include_once( get_template_directory() . '/includes/debug.class.php' );
endif;

// backup settings + more 
require_once( get_template_directory() . '/includes/extras.php' );

// alternative theme settings handler (alternative to get_theme_mod / set_theme_mod, including comparision functions)
//require( get_template_directory() . '/includes/theme-mods.php' );

// generic validator / sanitization class
require( get_template_directory() . '/includes/pasteur.class.php' );
require( get_template_directory() . '/includes/pasteur.php' );

/**
 * Next: Add theme activation / deactivation hooks - mostly used for future enhancements and updates
 */

require_once( get_template_directory() . '/includes/admin/welcome.class.php' );


// initial activation

// add the calls for the welcome screen
/*
add_action('admin_menu', 'cc2_add_welcome_screen');

function cc2_add_welcome_screen() {
	add_dashboard_page(
		__('Welcome To Custom Community', 'cc2'),
		__('Welcome To Custom Community', 'cc2'),
		'read',
		'cc2-welcome',
		'cc2_welcome_screen'
	);
}

// add the actual welcome screen function
function cc2_welcome_screen() {
	$import_old_settings = array(
		'settings' => __('Theme Settings', 'cc2' ),
		'widgets' => __('Widgets', 'cc2' ),
		'slideshow' => __('Slideshow', 'cc2' ),
	);
	* 
	
	include( get_template_directory() . '/includes/admin/templates/welcome.php' );
}*/

// add to the prospective hook
add_action('after_switch_theme', 'cc2_theme_initial_setup', 10, 2 );
function cc2_theme_initial_setup( $old_name, $old_theme = false) {
	update_option( 'cc2_theme_version', CC2_THEME );
	update_option( 'cc2_theme_status', 'enabled');
	
	set_transient( 'cc2_theme_active', true );
	

	//require_once( get_template_directory() . '/includes/extras.php' );
	 
	// check theme settings + restore if possible
	// option_name = theme_mods_{$theme_name}
	
	$theme_mods_backup = cc2_Helper::get_settings_backup('theme_mods' );
	
	//if( empty( $theme_mods_backup ) ) { // fetch default settings
		include( get_template_directory()  . '/includes/default-settings.php' );
		
		if( defined('CC2_DEFAULT_SETTINGS' ) ) {
			$default_theme_settings = maybe_unserialize( CC2_DEFAULT_SETTINGS );
		
			$theme_mods_backup = $default_theme_settings['theme_mods'];
		}
	//}
	
	if( !empty( $theme_mods_backup ) ) {
		update_option( 'theme_mods_cc2', $theme_mods_backup );
	}
	
	/*
	$theme_mods_backup = get_option( 'cc2_theme_mods_backup', false );
	
	if( empty( $theme_mods_backup ) ) { // fetch default settings
		include( trailingslashit( get_template_directory() ) . 'includes/default-settings.php' );
		if( defined('CC2_DEFAULT_SETTINGS' ) ) {
			$default_theme_settings = maybe_unserialize( CC2_DEFAULT_SETTINGS );
		
			$theme_mods_backup = $default_theme_settings['theme_mods'];
		}
	}
	
	if( !empty( $theme_mods_backup ) ) {
		update_option( 'theme_mods_cc2', $theme_mods_backup );
	}
	*/
	/**
	 * NOTE: the update script should hook into this
	 */
	
	// set default scheme for initial theme set up
	if( get_theme_mod('color_scheme', false ) == false ) {
		set_theme_mod('color_scheme', 'default' );
	}
	
	// first activation
	
//    if ( is_admin() && isset( $_GET['activated'] ) && 'themes.php' == $GLOBALS['pagenow'] ) {
//
//		/**
//		 * TODO: Add return referrer url
//		 */
//
//		//add_query_arg( array( 'page' => 'cc2-welcome', 'return' => admin_url( 'themes.php')
//
//		wp_safe_redirect( add_query_arg( array( 'page' => 'cc2-welcome', 'return' => admin_url( 'themes.php') ), admin_url( apply_filters( 'cc2_welcome_screen_url', 'themes.php' ) ) ) );
//
//        //wp_redirect(admin_url('customize.php'));
//        exit;
//    }
	
}

add_action('switch_theme', 'cc2_theme_deactivation', 10, 2 );
function cc2_theme_deactivation($new_name, $new_theme) {
	// save current theme settings
	//require_once( get_template_directory() . '/includes/extras.php' );
	
	cc2_Helper::update_settings_backup( 'theme_mods', get_theme_mods() );
	
	// disable theme
	
	update_option( 'cc2_theme_status', 'disabled' );
	set_transient( 'cc2_theme_active', false );
	
	//new __debug( cc2_Helper::get_settings_backup( 'theme_mods' ), 'backup of theme_mods' );

}

/**
 * FIXME: Might be the wrong action to hook into - for the "why", just see above code.
 * NOTE: Moved into @function cc2_theme_initial_setup.
 */


add_action( 'after_setup_theme', 'cc2_theme_activation' );
function cc2_theme_activation() {
	//require_once( get_template_directory() . '/includes/extras.php' );
	 
	// check theme settings + restore if possible
	// option_name = theme_mods_{$theme_name}
	
	$theme_mods_backup = cc2_Helper::get_settings_backup('theme_mods' );
	if( !empty( $theme_mods_backup ) ) {
		update_option( 'theme_mods_cc2', $theme_mods_backup );
	}
	
	// set default scheme for initial theme set up
	if( get_theme_mod('color_scheme', false ) == false ) {
		set_theme_mod('color_scheme', 'default' );
	}
	
	
	
//     if ( is_admin() && isset( $_GET['activated'] ) && 'themes.php' == $GLOBALS['pagenow'] ) {
//        //wp_redirect(admin_url('customize.php'));
//
//        wp_safe_redirect( add_query_arg( array( 'page' => 'cc2-welcome', 'return' => admin_url( 'themes.php') ), admin_url( apply_filters( 'cc2_welcome_screen_url', 'themes.php' ) ) ) );
//
//        exit;
//    }
}

// much more helping buddy
if( !function_exists( 'is_customizer_preview' ) ) :

	function is_customizer_preview() {
		$return = false;

		if( !is_admin() && isset($_POST['wp_customize']) && $_POST['wp_customize'] == 'on' && is_user_logged_in() ) {
			$return = true;
		}


		return $return;
	}

endif;

// a lil helper
if( !function_exists( '_notempty' ) ) :
	function _notempty( $value = null ) {
		if( !empty( $value ) ) {
			echo $value;
		}
	}

endif;

// nother lil helper
if( !function_exists( '_switchdefault' ) ) :
	/**
	 * Switch to default if $value is empty. Works identical to get_option()
	 * 
	 * @param mixed $value		Value to test against.
	 * @param mixed $default	Default value if $value is empty. Defaults to FALSE.
	 * @return mixed $return	Returns $default if $value is empty, or $value, if not.
	 */

	function _switchdefault( $value = null, $default = false ) {
		$return = $default;
		
		if( $default !== $value && !empty( $value ) ) {
			$return = $value;
		}
		
		return $return;
	}

endif;



// woocomerce support
add_theme_support( 'woocommerce' );

// woocommerce includes
if( class_exists('WooCommerce' ) ) :
	include_once( get_template_directory() . '/includes/wc-support.php' );
endif;


// Adding Google Fonts and TK Google Fonts Support
add_action('wp_enqueue_scripts', 'cc2_add_google_fonts', 0 );
function cc2_add_google_fonts() {

    $x2_fonts_options = get_option('tk_google_fonts_options');

    if(!is_array($x2_fonts_options) || is_array($x2_fonts_options) && !in_array('Ubuntu+Condensed', $x2_fonts_options)){
        wp_register_style( 'cc2-default-google-fonts-ubuntu-condensed', 'http://fonts.googleapis.com/css?family=Ubuntu+Condensed' );
        wp_enqueue_style( 'cc2-default-google-fonts-ubuntu-condensed' );
    }
    if(!is_array($x2_fonts_options) || is_array($x2_fonts_options) && !in_array('Pacifico', $x2_fonts_options)){
        wp_register_style( 'cc2-default-google-fonts-pacifico', 'http://fonts.googleapis.com/css?family=Pacifico' );
        wp_enqueue_style( 'cc2-default-google-fonts-pacifico' );
    }
    if(!is_array($x2_fonts_options) || is_array($x2_fonts_options) && !in_array('Lato', $x2_fonts_options)){
        wp_register_style( 'cc2-default-google-fonts-lato', 'http://fonts.googleapis.com/css?family=Lato:300' );
        wp_enqueue_style( 'cc2-default-google-fonts-lato' );
    }
}

// Setting for large screens (from 1200+ pixel).
// The rest is handled automatically by Bootstrap.
if ( ! isset( $content_width ) ) {
	$content_width = 750; /* pixels */
}

/**
 * The next function sets up the Custom Community defaults and registers support for various WordPress features.
 *
 * Note that this function is hooked into the after_setup_theme hook, which runs
 * before the init hook. The init hook would be too late for some features, such as indicating
 * support post thumbnails.
 * 
 * @package cc2
 * @since 2.0
 */
if ( ! function_exists( 'cc_setup' ) ) :
 
add_action( 'after_setup_theme', 'cc_setup' );
 
function cc_setup() {
    global $cap, $content_width;

    // This theme styles the visual editor with editor-style.css to match the theme style.
    add_editor_style();

    if ( function_exists( 'add_theme_support' ) ) {

		/** 
		 * Enable standardized title tag output and filtering
		 * @since WP 4.1
		 */
		add_theme_support( 'title-tag' );

		// Add default posts and comments RSS feed links to head
		add_theme_support( 'automatic-feed-links' );
		
		// Enable support for Post Thumbnails on posts and pages
		// @link http://codex.wordpress.org/Function_Reference/add_theme_support#Post_Thumbnails
		add_theme_support( 'post-thumbnails' );
		
		// Enable support for Post Formats
		add_theme_support( 'post-formats', array( 'aside', 'image', 'video', 'quote', 'link' ) );
		
		// Setup the WordPress core custom background feature.
		add_theme_support( 'custom-background', apply_filters( '_tk_custom_background_args', array(
			'default-color' => '',
			'default-image' => '',
		) ) );
	
    }

	/**
	 * Make theme available for translation
	 * Translations can be filed in the /languages/ directory
	*/
	load_theme_textdomain( 'cc2', get_template_directory() . '/languages' );

	// This theme uses wp_nav_menu() in two locations.
	register_nav_menus( array(
        'top'  		    => __( 'Header Top Nav',        'cc2' ),
        'secondary'     => __( 'Header Secondary Nav',  'cc2' ),
    ) );

}
endif; // end of cc_setup



/**
 * Register widgetized area and update sidebar with default widgets 
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 */
add_action( 'widgets_init', 'cc_widgets_init' );
  
function cc_widgets_init() {
	
	// first sidebar => thus gets also filled with the initial widgets ;)
	register_sidebar( array(
		'name'          => __( 'Sidebar Right', 'cc2' ),
		'id'            => 'sidebar-right',
		'description'   => 'That\'s the primary sidebar. If no other sidebar is called, this one will be used (if there should be a sidebar displayed at all, setup in Customizer, Sidebar options).',
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h3 class="widget-title">',
		'after_title'   => '</h3>',
	) ); 
	
	register_sidebar( array(
		'name'          => __( 'Sidebar Left', 'cc2' ),
		'id'            => 'sidebar-left',
		'description'   => 'This is the left sidebar. It usually doesn\'t show up, except you setup your theme options for "Sidebars" to "left" or "left and right" (see in the Customizer).',
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h3 class="widget-title">',
		'after_title'   => '</h3>',
	) ); 

	// header widgetarea
	register_sidebar( array(
		'name'          => __( 'Sidebar Header', 'cc2' ),
		'id'            => 'sidebar-header',
		'description'   => 'The header widgetarea will pop up in your frontend once you add a widget here..',
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h3 class="widget-title">',
		'after_title'   => '</h3>',
	) ); 


	// footer widgetareas

	register_sidebar( array(
        'name'          => __( 'Footer Fullwidth', 'cc2' ),
        'id'            => 'footer-fullwidth',
        'description'   => 'The fullwidth footer widgetarea will pop up in your frontend once you add a widget here.',
        'before_widget' => '<div id="%1$s" class="footer-fullwidth-widget %2$s">',
        'after_widget'  => '</div><div class="clear"></div>',
        'before_title'  => '<h3 class="widgettitle">',
        'after_title'   => '</h3>'
    ) );
	
	register_sidebar( array(
        'name'          => __( 'Footer Column 1', 'cc2' ),
        'id'            => 'footer-col-1',
        'description'   => 'The footer columns widgetarea will pop up in your frontend once you add a widget in of these footer columns.',
        'before_widget' => '<div id="%1$s" class="footer-column-widget %2$s">',
        'after_widget'  => '</div><div class="clear"></div>',
        'before_title'  => '<h3 class="widgettitle">',
        'after_title'   => '</h3>'
    ) );    

	register_sidebar( array(
        'name'          => __( 'Footer Column 2', 'cc2' ),
        'id'            => 'footer-col-2',
        'description'   => 'The footer columns widgetarea will pop up in your frontend once you add a widget in of these footer columns.',
        'before_widget' => '<div id="%1$s" class="footer-column-widget %2$s">',
        'after_widget'  => '</div><div class="clear"></div>',
        'before_title'  => '<h3 class="widgettitle">',
        'after_title'   => '</h3>'
    ) );    

	register_sidebar( array(
        'name'          => __( 'Footer Column 3', 'cc2' ),
        'id'            => 'footer-col-3',
        'description'   => 'The footer columns widgetarea will pop up in your frontend once you add a widget in of these footer columns.',
        'before_widget' => '<div id="%1$s" class="footer-column-widget %2$s">',
        'after_widget'  => '</div><div class="clear"></div>',
        'before_title'  => '<h3 class="widgettitle">',
        'after_title'   => '</h3>'
    ) );    



}

/**
 * Hook into tool bar and add some useful stuff - but ONLY on the frontend!
 * NOTE: Pluggable function
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-rc1
 */

if( !function_exists( 'cc2_add_to_frontend_toolbar' ) ) :
	function cc2_add_to_frontend_toolbar( $wp_admin_bar ) {
		$arrParams = array(
			'id'    => 'cc2_settings',
			'title' => 'CC Settings',
			'parent' => 'site-name',
			'href'  => admin_url('/admin.php?page=cc2-settings'),
			'meta'  => array( 'class' => 'cc2-settings' ),
		);
		$wp_admin_bar->add_node( $arrParams );
	}

	if( !is_admin() ) :
		add_action( 'admin_bar_menu', 'cc2_add_to_frontend_toolbar', 100 );
	endif;

endif;

/**
 * Scroll-to-top button - Bootstrap style
 * Inspired by @link http://stackoverflow.com/questions/22413203/bootstrap-affix-back-to-top-link
 * Static class for better readability and comprehension 
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 */

if( !class_exists( 'cc2_BootstrapButton') ) {
	
	class cc2_BootstrapButton {
		public static function init() {
			// fetch settings
			$advanced_settings = get_option('cc2_advanced_settings', false );
			
			
			// only load and display the button if the option is enabled
			if( empty($advanced_settings['load_scroll_top']) ) {
				return;
			}
				
			// set up actions and filters
		
			add_action('wp_footer', array('cc2_BootstrapButton', 'add_bootstrap_button' ) );
			
			// check if navigation is set to 'fixed', and if so, add a filter to replace the top link and set up an additional action to insert the custom link
			if( get_theme_mod('fixed_top_nav', false) != false ) {
				
				add_filter('cc2_scroll_top_button_link', array( 'cc2_BootstrapButton', 'replace_top_link' ) ); // replace top link
				add_action('cc_before_header', array('cc2_BootstrapButton', 'add_bootstrap_top_id'), 1);
			}
			
			// load assets
			add_action( 'wp_enqueue_scripts', array('cc2_BootstrapButton', 'load_assets' ) );
		}
	
		public static function add_bootstrap_button() {
		
			// filter hook for optionally replacing the whole template
			$strScrollTopTemplate = apply_filters( 'cc2_scroll_top_button_template', '<span id="top-link-block" class="hidden top-link-container">' .
				'<a href="%s" class="well well-sm" id="top-link-button-link">' .
				'<i class="%s"></i><span class="top-link-button-text">%s</span>' .
				'</a>' .
				'</span><!-- /#top-link-block -->' 
			);
			
			
			// filter hook for optionally replacing the top link
			$strTopLink = apply_filters( 'cc2_scroll_top_button_link', '#masthead' );
			
			// filter hook for optionally replacing the icon
			$strTopLinkIcon = apply_filters( 'cc2_scroll_top_button_icon_class', 'glyphicon glyphicon-chevron-up' );
			
			// filter hook for the text
			$strTopLinkText = apply_filters( 'cc2_scroll_top_button_text', __('Back to Top', 'cc2' ) );
			
			// insert all data into the template
			$strFormattedTemplate = sprintf( $strScrollTopTemplate, $strTopLink, $strTopLinkIcon, $strTopLinkText );
			
			// roll you own? ^_^
			$return = apply_filters('cc2_scroll_top_button_html', $strFormattedTemplate );
		
			echo $return;
			
		}
		
		public static function add_bootstrap_top_id() {
			$strTopLinkTemplate = '<div id="top"></div>';

			echo apply_filters('cc2_bootstrap_button_top_id_template', $strTopLinkTemplate );
		}
		
		public static function replace_top_link( $link ) {
			$return = $link;
			
			$return = '#top';
			
			return $return;
		}
		
		public static function load_assets() {
			wp_enqueue_script( 'cc2-scroll-top' );
		}
	}
	
	
	add_action('after_setup_theme', array('cc2_BootstrapButton', 'init' ) );
}

/**
 * Load the aid against JS errors (@see https://github.com/andyet/ConsoleDummy.js)
 * Try to load as early as possible
 */
function cc2_js_aid() { 
	wp_register_script( 'consoledummy', get_template_directory_uri() . '/includes/js/SlimConsoleDummy.min.js' ); // out of convenience
	wp_enqueue_script( 'consoledummy' );
}
add_action('get_header', 'cc2_js_aid' ); // frontend
add_action('admin_enqueue_scripts', 'cc2_js_aid', 1 ); // admin


/**
 * Color Scheme library includes (classes + helper functions)
 * TODO: Test if these could be tacked into the rest of the includes at the end of this file, or if there's a need to load them all BEFORE the rest.
 */

//require_once ( get_template_directory() . '/includes/extras.php' );
require( get_template_directory() . '/includes/schemes/libs/color-schemes.class.php' );


/*
if( !class_exists( 'cc2_ColorSchemes_ThemeHandler' ) ) {
	require( get_template_directory() . '/includes/schemes/libs/theme_handler.class.php' );	
} 
*/

include( get_template_directory() . '/includes/schemes/libs/functions.php' );


/**
 * Helps with initialization, esp. if you're using a child theme or want to extend or override the class with your own.
 * 
 * NOTE: Some plugins plus several modules for PHP (including eAccellerator) break the Closure support or do not support them at all - DESPITE the correct PHP version 5.3+ - thus the original anonymous function call was replaced with this outdated regular init function.
*/
	
if( !function_exists( '__cc2_init_color_schemes_call' ) ) :
	//__debug::log('outdated private function call fires', 'cc2_init_color_schemes');

	function __cc2_init_color_schemes_call() {
		global $cc2_color_schemes;
		
		if( !isset( $GLOBALS['cc2_color_schemes'] ) && !isset($cc2_color_schemes) ) {
			do_action('cc2_init_color_schemes');
		}
	}
	
	add_action('init', '__cc2_init_color_schemes_call', 10 );
endif;


//endif;


/**
 * Register scripts and styles 
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0 
 */

add_action( 'wp_enqueue_scripts', 'cc2_register_scripts', 10 );
 
function cc2_register_scripts() {
	$advanced_settings = get_option('cc2_advanced_settings', array() );

    // load bootstrap css
    //wp_enqueue_style( 'cc-bootstrap', get_template_directory_uri() . '/includes/resources/bootstrap/dist/css/bootstrap.min.css' );

    // load bootstrap css => fires earlier
    wp_enqueue_style( 'style', apply_filters('cc2_style_css', get_template_directory_uri() . '/style.css') );



	// load bootstrap js
	//wp_register_script( 'bootstrap-min', get_template_directory_uri() . '/includes/resources/bootstrap/dist/js/'
	
    wp_register_script( 'cc-bootstrap-tooltip',      get_template_directory_uri() . '/includes/resources/bootstrap/js/tooltip.js',       array('jquery') );

    wp_register_script( 'cc-bootstrap-affix',        get_template_directory_uri() . '/includes/resources/bootstrap/js/affix.js',         array('jquery') );
    wp_register_script( 'cc-bootstrap-alert',        get_template_directory_uri() . '/includes/resources/bootstrap/js/alert.js',         array('jquery') );
    wp_register_script( 'cc-bootstrap-button',       get_template_directory_uri() . '/includes/resources/bootstrap/js/button.js',        array('jquery') );
    
     wp_register_script( 'cc-bootstrap-transition',   get_template_directory_uri() . '/includes/resources/bootstrap/js/transition.js',    array('jquery') );
     
    wp_register_script( 'cc-bootstrap-carousel',     get_template_directory_uri() . '/includes/resources/bootstrap/js/carousel.js',      array('jquery', 'cc-bootstrap-transition') );
    wp_register_script( 'cc-bootstrap-collapse',     get_template_directory_uri() . '/includes/resources/bootstrap/js/collapse.js',      array('jquery', 'cc-bootstrap-transition') );
    wp_register_script( 'cc-bootstrap-dropdown',     get_template_directory_uri() . '/includes/resources/bootstrap/js/dropdown.js',      array('jquery') );
    wp_register_script( 'cc-bootstrap-modal',        get_template_directory_uri() . '/includes/resources/bootstrap/js/modal.js',         array('jquery') );
    wp_register_script( 'cc-bootstrap-popover',      get_template_directory_uri() . '/includes/resources/bootstrap/js/popover.js',       array('jquery', 'cc-bootstrap-tooltip') );
    wp_register_script( 'cc-bootstrap-scrollspy',    get_template_directory_uri() . '/includes/resources/bootstrap/js/scrollspy.js',     array('jquery') );
    wp_register_script( 'cc-bootstrap-tab',          get_template_directory_uri() . '/includes/resources/bootstrap/js/tab.js',           array('jquery') );

   


	// load the glyphicons
	wp_register_style( 'glyphicons', get_template_directory_uri() . '/includes/resources/glyphicons/css/bootstrap-glyphicons.css' );
	
	// register font awesome
	wp_register_style ('font-awesome', get_template_directory_uri() . '/includes/resources/fontawesome/css/font-awesome.min.css' );
	
	//wp_register_style( 'cc-style', get_stylesheet_uri() );

	// load bootstrap wp js
	/**
	 * TODO: Check if THIS is the reason for the random "customizer preview window is empty" bug
	 */
	wp_register_script( 'cc-bootstrapwp', get_template_directory_uri() . '/includes/js/bootstrap-wp.js', array('jquery') );

	// load animate.css
	wp_register_style( 'cc-animate-css', get_template_directory_uri() . '/includes/resources/animatecss/animate.min.css' );

	// load skip link focus fix
	wp_register_script( 'cc-skip-link-focus-fix', get_template_directory_uri() . '/includes/js/skip-link-focus-fix.js', array(), '20130115', true );
	
	
	wp_register_script( 'cc-keyboard-image-navigation', get_template_directory_uri() . '/includes/js/keyboard-image-navigation.js', array( 'jquery' ), '20120202' );
	
	// register scroll-to-top script
	wp_register_script( 'cc2-scroll-top', get_template_directory_uri() . '/includes/js/bootstrap-scroll-to-top.js', array( 'cc-bootstrap-scrollspy', 'cc-bootstrap-affix' ), '2.0', true ); // run in footer - else the document isnt rendered fully, and thus, the window hieght check will fail


	// register smartmenus.js
	wp_register_script( 'cc2-smartmenus-js', get_template_directory_uri() . '/includes/resources/smartmenus/jquery.smartmenus.js', array('jquery'), '0.9.6' );
	wp_register_script( 'cc2-smartmenus-js-bootstrap', get_template_directory_uri() . '/includes/resources/smartmenus/addons/bootstrap/jquery.smartmenus.bootstrap.js', array('cc2-smartmenus-js') );
	
	wp_register_style( 'cc2-smartmenus-js-bootstrap', get_template_directory_uri() . '/includes/resources/smartmenus/addons/bootstrap/jquery.smartmenus.bootstrap.css', array('style')  );

	// register test.js
	wp_register_script('cc2-test-js', get_template_directory_uri() . '/includes/js/test.js', false, false, true );

	// register headjs
	wp_register_script( 'cc2-headjs-redux', get_template_directory_uri() . '/includes/js/head.core.css3.min.js' );

	// get settings
	// load headjs (+ support for WP plugins)
	
	//$headjs_settings = wp_parse_args( array( 'headjs_type' => $advanced_settings['headjs_type'], 'headjs_url' => $advanced_settings['headjs_url'] ) , array('headjs_type' => 'redux', 'headjs_url' => '' ) );
		
	$headjs_url = get_template_directory_uri() . '/includes/js/head.min.js';
	if( !empty( $advanced_settings['headjs_url'] ) ) {	
		$headjs_url = str_replace(
			array('%template_directory_uri%', '%stylesheet_directory_uri%'), 
			array( get_template_directory_uri(), get_stylesheet_directory_uri() ),
			$advanced_settings['headjs_url']
		);
	}
	
	if( !empty( $headjs_url ) ) {
		wp_register_script( 'cc2-headjs-full', $headjs_url );
	}
}
 
/**
 * Enqueue scripts and styles 
 * 
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0 
 */

add_action( 'wp_enqueue_scripts', 'cc2_load_assets', 11 );
 
function cc2_load_assets() {
	
	
	
	// load settings
	$advanced_settings = get_option('cc2_advanced_settings', array() );
	
	// load headjs (+ support for WP plugins)

	$headjs_settings = array(
		'headjs_type' => ( !isset( $advanced_settings['headjs_type'] ) ? 'redux' : $advanced_settings['headjs_type'] ),
		
		'headjs_url' => ( !isset( $advanced_settings['headjs_url'] ) ? '' : $advanced_settings['headjs_url'] ),
	);
	
	/**
	 * because PHP is too dumb .. and some other folks might be as well, we NOT gonna use the WP way *eyerolls*
	 * @author Fabian Wolf
	 */
	/*
	$headjs_settings = wp_parse_args(
		array( 
			'headjs_type' => $advanced_settings['headjs_type'], 
			'headjs_url' => $advanced_settings['headjs_url'] 
		),
		array(
			'headjs_type' => 'redux', 'headjs_url' => '' 
		) 
	);
	*/
	
	/**
	 *  Disable headjs for known plugins: 
	 * - Head.WP (only executes load component; @link https://github.com/kylereicks/head.js.wp), 
	 * - Asynchronous Javascript (only executes load component; @link https://github.com/parisholley/wordpress-asynchronous-javascript/),
	 * - CleanerPress (uses the full component, but is outdated (0.96, current is: 1.0.3); @link http://wordpress.org/plugins/cleanerpress/)
	 */
	
	
	
    // load bootstrap css
    //wp_enqueue_style( 'cc-bootstrap', get_template_directory_uri() . '/includes/resources/bootstrap/dist/css/bootstrap.min.css' );

	// load bootstrap css => fires too late
    //wp_enqueue_style( 'style', apply_filters('cc2_style_css', get_template_directory_uri() . '/style.css') );

	// aid against JS troubles => https://github.com/andyet/ConsoleDummy.js
	//wp_enqueue_script( 'consoledummy' );




	/**
	 * load bootstrap js
	 * NOTE: Improves adjustability, while trying to nail down the bloody random appearing "customizer preview = blank page" bug
	 * Descriptions taken directly from @link http://getbootstrap.com/javascript/
	 */
	
	$arrKnownBootstrapScripts = array(
		'cc-bootstrap-tooltip' => array(
			'description' => 'Tooltip.js; Inspired by the excellent jQuery.tipsy plugin written by Jason Frame; Tooltips is an updated version, which doesn\'t rely on images, uses CSS3 for animations, and data-attributes for local title storage.',
		),
		'cc-bootstrap-transition' => array(
			'description' => 'Transition.js is a basic helper for transitionEnd events as well as a CSS transition emulator. It\'s used by the other plugins to check for CSS transition support and to catch hanging transitions.',
		),
		
		'cc-bootstrap-affix' => array(
			'description' => 'Affix; required eg. for fixed navbars.',
		),
		'cc-bootstrap-alert' => array(
			'description' => 'Add dismiss functionality to all alert messages with this plugin.',
		),
		'cc-bootstrap-button' => array(
			'description' => 'Do more with buttons. Control button states or create groups of buttons for more components like toolbars.',
		),
		
		'cc-bootstrap-carousel' => array(
			'description' => 'Simple slideshow function; required if you want to use the default Custom Community slideshow script',
		),
		'cc-bootstrap-collapse' => array(
			'description' => 'Get base styles and flexible support for collapsible components like accordions and navigation. Requires the transitions plugin to be included in your version of Bootstrap.',
			'deps' => 'cc-bootstrap-transition',
		),
		'cc-bootstrap-dropdown' => array(
			'description' => 'Add dropdown menus to nearly anything with this simple plugin, including the navbar, tabs, and pills. Required by the navbar dropdowns.'
		),
		'cc-bootstrap-modal' => array( 
			'description' => 'Modals are streamlined, but flexible, dialog prompts with the minimum required functionality and smart defaults.',
		),
		'cc-bootstrap-popover' => array(
			'description' => 'Add small overlays of content, like those on the iPad, to any element for housing secondary information. Popovers require the tooltip plugin to be included.',
			'deps' => 'cc-bootstrap-tooltip',
		),
		'cc-bootstrap-scrollspy' => array(
			'description' => 'The ScrollSpy plugin is for automatically updating nav targets based on scroll position. Scroll the area below the navbar and watch the active class change. The dropdown sub items will be highlighted as well.',
		),
		'cc-bootstrap-tab' => array(
			'description' => 'Add quick, dynamic tab functionality to transition through panes of local content, even via dropdown menus.',
		),
	);
	
	
	// sorry for the complex structure - but it helps explaining ;-)
	
	$arrLoadBootstrapScripts = apply_filters( 'cc2_bootstrap_scripts_handlers', array_keys( $arrKnownBootstrapScripts ) );
	
	if( !empty( $arrLoadBootstrapScripts ) ) {
		foreach( $arrLoadBootstrapScripts as $strScriptHandler ) {
			wp_enqueue_script( $strScriptHandler );
		}
	}
	
	/*
    wp_enqueue_script( 'cc-bootstrap-tooltip');

    wp_enqueue_script( 'cc-bootstrap-affix' );
    wp_enqueue_script( 'cc-bootstrap-alert' );
    wp_enqueue_script( 'cc-bootstrap-button' );
    

    wp_enqueue_script( 'cc-bootstrap-carousel' );
    wp_enqueue_script( 'cc-bootstrap-collapse' );
    wp_enqueue_script( 'cc-bootstrap-dropdown' );
    wp_enqueue_script( 'cc-bootstrap-modal' );
    wp_enqueue_script( 'cc-bootstrap-popover' );
    wp_enqueue_script( 'cc-bootstrap-scrollspy' );
    wp_enqueue_script( 'cc-bootstrap-tab' );
    wp_enqueue_script( 'cc-bootstrap-transition');
	*/


	// load the glyphicons
	wp_enqueue_style( 'glyphicons' );
		
	//wp_enqueue_style( 'cc-style', get_stylesheet_uri() );

	// load bootstrap wp js
	if( ! defined( 'CC2_LESSPHP' ) ) {
		wp_enqueue_script( 'cc-bootstrapwp' );
	}

	// load animate.css
	wp_enqueue_style( 'cc-animate-css');

	// load skip link focus fix
	wp_enqueue_script( 'cc-skip-link-focus-fix');

	/**
	 * NOTE: Postponed till 2.1 - requires more changes to the CSS compartment and to the Menu Walker
	 * But basically, its there - if you want to use it, go ahead and help yourself! ;)
	 * 
	 * @author Fabian Wolf
	 */
	// optional: load smartmenus.js
	/*if( $load_smartmenus_js != false ) {
		wp_enqueue_style('cc2-smartmenus-js-bootstrap');
		wp_enqueue_script('cc2-smartmenus-js-bootstrap');
	}*/

	// load comment replies 
	if ( is_singular() && comments_open() && get_option( 'thread_comments' ) ) {
		wp_enqueue_script( 'comment-reply' );
	}
	
	// load keyboard image navigation
	if ( is_singular() && wp_attachment_is_image() ) {
		wp_enqueue_script( 'cc-keyboard-image-navigation' );
	}
		
	// load headjs (+ support for WP plugins)
	switch( $headjs_settings['headjs_type'] ) {
		case 'none':
		case 'disabled':
			// exactly that .. whyever you might want to disable html5 + css3 support + modernizr + js-based media queries, but still, thats ALSO a legitimate setting ;)
			break;
		case 'full':
			wp_enqueue_script('cc2-headjs-full');
			break;
		case 'redux':
		default:
			wp_enqueue_script('cc2-headjs-redux' );
			break;
	}
	
	
}

/**
 * Enqueue debugging assets
 */


if( !function_exists('cc2_load_theme_debug_assets' ) ) :
	add_action('wp_enqueue_scripts', 'cc2_load_theme_debug_assets', 9999 ); // load at the latest moment possible - ie. AFTER ALL other scripts

	function cc2_load_theme_debug_assets() {
		if( defined('CC2_THEME_DEBUG') ) {
		
			$cc2_advanced_settings = get_option('cc2_advanced_settings', false );
		
		
			if( !empty( $cc2_advanced_settings ) && !empty($cc2_advanced_settings['load_test_js']) ) {
				wp_enqueue_script( 'cc2-test-js' );
			}
		}
	}
	
endif;




// Load Customizer Options
require get_template_directory() . '/includes/admin/customizer-options.class.php';

// Implement the Custom Header Feature
require get_template_directory() . '/includes/custom-header.php';


// Load the customizer style file for the frontend & customizer preview
require get_template_directory() . '/style.php';

// Load Bootstrap WP Navwalker
//require get_template_directory() . '/includes/bootstrap-wp-navwalker.php';

// Load improved Bootstrap WP Navwalker
require get_template_directory() . '/includes/wp-bootstrap-navwalker.php';

// Custom template tags for this theme
require get_template_directory() . '/includes/template-tags.php';

// Custom functions that act independently of the theme templates
//require get_template_directory() . '/includes/extras.php';

// Load Jetpack compatibility file
require get_template_directory() . '/includes/jetpack.php';




// load Boostrap helpers
require_once( get_template_directory() . '/includes/bootstrap-supplements.php' );

// Load the slider
require get_template_directory() . '/includes/slider/cc-slider.php';


// Load this ONLY for the admin...
if ( is_admin() ): 
	
	// Load base class 
	require_once( get_template_directory() . '/includes/admin/base.php' );

	// Load the admin.php	
	require get_template_directory() . '/includes/admin/cc2-dashboard.php';
	
	// Load the cc slideshow admin settings 	
	require get_template_directory() . '/includes/admin/slider.php';
	
	// load the advanced settings
	require_once( get_template_directory() . '/includes/admin/advanced-settings.php' );
	
	// load the backup page
	require_once( get_template_directory() . '/includes/admin/backup-settings.php' );

	
endif;

