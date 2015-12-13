<?php
/**
 * iBuddy functions and definitions
 *
 * @package iBuddy
 */

/**
 * Set the content width based on the theme's design and stylesheet.
 */
if ( ! isset( $content_width ) )
	$content_width = 640; /* pixels */

if ( ! function_exists( 'ibuddy_setup' ) ) :
/**
 * Sets up theme defaults and registers support for various WordPress features.
 *
 * Note that this function is hooked into the after_setup_theme hook, which runs
 * before the init hook. The init hook is too late for some features, such as indicating
 * support post thumbnails.
 */
function ibuddy_setup() {

	/**
	 * Make theme available for translation
	 * Translations can be filed in the /languages/ directory
	 */
	load_theme_textdomain( 'ibuddy', get_template_directory() . '/languages' );

	/**
	 * Add default posts and comments RSS feed links to head
	 */
	add_theme_support( 'automatic-feed-links' );

	/**
	 * Enable support for Post Thumbnails on posts and pages
	 */
	add_theme_support( 'post-thumbnails' );

	/**
 	 * Add additional Image size for posts
 	 */
	add_image_size( 'Post', 810, 210, true); // Post 810x410

	/**
	 * This theme uses wp_nav_menu() in one location.
	 */
	register_nav_menus( array(
		'primary' => __( 'Primary Menu', 'ibuddy' ),
	) );

}
endif; // ibuddy_setup
add_action( 'after_setup_theme', 'ibuddy_setup' );

/**
 * Register widgetized area and update sidebar with default widgets
 */
function ibuddy_widgets_init() {
	register_sidebar( array(
		'name'          => __( 'BuddyPress Sidebar', 'ibuddy' ),
		'description'   => __( 'Add widgets to show in all BuddyPress components pages', 'ibuddy' ),
		'id'            => 'sidebar-1',
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h1 class="widget-title">',
		'after_title'   => '</h1>',
	) );
	
}
add_action( 'widgets_init', 'ibuddy_widgets_init' );

/**
 * Enqueue scripts and styles
 */
function ibuddy_scripts() {
	wp_enqueue_style( 'iBuddy-style', get_stylesheet_uri() );
	/* Google Fonts */
	wp_enqueue_style( 'iBuddy-google-fonts' ,'http://fonts.googleapis.com/css?family=Oswald' );
	
	wp_enqueue_script( 'iBuddy-navigation', get_template_directory_uri() . '/js/navigation.js', array(), '20120206', true );

	wp_enqueue_script( 'iBuddy-skip-link-focus-fix', get_template_directory_uri() . '/js/skip-link-focus-fix.js', array(), '20130115', true );

	if ( is_singular() && comments_open() && get_option( 'thread_comments' ) ) {
		wp_enqueue_script( 'comment-reply' );
	}

	if ( is_singular() && wp_attachment_is_image() ) {
		wp_enqueue_script( 'iBuddy-keyboard-image-navigation', get_template_directory_uri() . '/js/keyboard-image-navigation.js', array( 'jquery' ), '20120202' );
	}
	
}
add_action( 'wp_enqueue_scripts', 'ibuddy_scripts' );



/**
 * Custom Function to stay on the same page on failed login 
 */
function login_failed( $username ) {
   $referrer = $_SERVER['HTTP_REFERER'];
   // if there's a valid referrer page, and it's not the default log-in screen
   if ( !empty($referrer) && !strstr($referrer,'wp-login') && !strstr($referrer,'wp-admin') ) {
      wp_redirect( $referrer . '?login=failed' );
      exit;
   }
}
add_action( 'wp_login_failed', 'login_failed' );



/**
 * Custom template tags for this theme.
 */
require get_template_directory() . '/inc/template-tags.php';


/**
 * Custom functions that act independently of the theme templates.
 */
require get_template_directory() . '/inc/extras.php';

/**
 * Custom Sidebars.
 */
require get_template_directory() . '/inc/custom-sidebars.php';

/**
 * Custom Widgets.
 */
require get_template_directory() . '/inc/custom-widgets.php';
	
/**
 * Custom Function to add Read More link to the excerpt 
 */
function new_excerpt_more( $more ) {
	return ' <a class="read-more" href="'. get_permalink( get_the_ID() ) . '">'. __('Read More','ibuddy') . '</a>';
}
add_filter( 'excerpt_more', 'new_excerpt_more' );

?>