<?php
// Content width
if ( ! isset( $content_width ) )
	$content_width = 828;
	
function simpleportal_widgets_init() {

register_sidebar( array(

'name'=>'Primary Sidebar',

'id'   => 'primary_sidebar',

'description'   => 'Default Left Side Bar',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
register_sidebar( array(

'name'=>'Footer Area1',

'id'   => 'footer_area1',

'description'   => 'Footer widget1',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
register_sidebar( array(

'name'=>'Footer Area2',

'id'   => 'footer_area2',

'description'   => 'Footer widget2',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
register_sidebar( array(

'name'=>'Footer Area3',

'id'   => 'footer_area3',

'description'   => 'Footer widget3',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
register_sidebar( array(

'name'=>'Footer Area Home 1',

'id'   => 'footer_area_home1',

'description'   => 'Home Footer widget1',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
register_sidebar( array(

'name'=>'Footer Area Home 2',

'id'   => 'footer_area_home2',

'description'   => 'Home Footer widget2',

'before_widget' => '<div class="well">',

'after_widget'  => '</div>',

'before_title'  => '<h4>',

'after_title'   => '</h4>'

));
}
add_action( 'widgets_init', 'simpleportal_widgets_init' );

/**
* Sets up theme defaults and registers support for various WordPress features.
*
* Declare textdomain for this child theme.
* Translations can be filed in the /languages/ directory.
*/
function simpleportal_setup() {
load_child_theme_textdomain( 'simpleportal', get_stylesheet_directory() . '/languages' );

// custom header
$args = array(
'flex-width'    => true,
'width'         => 1170,
'flex-height'    => true,
);
add_theme_support( 'custom-header', $args );


// add featured image sizes
if ( function_exists( 'add_theme_support' ) ) {
	add_theme_support( 'post-thumbnails' );
        set_post_thumbnail_size( 150, 150 ); // default Post Thumbnail dimensions   
}
if ( function_exists( 'add_image_size' ) ) { 
	add_image_size( '4-column-gallery-thumb', 240, 108, true ); //(cropped)
}

// Custom background image
add_theme_support( 'custom-background' );

// Register Custom Navigation Walker
require_once('wp_bootstrap_navwalker.php');

// This theme uses wp_nav_menu() in 7 locations.
register_nav_menus( array(
'top-right' => __( 'Top Right Navigation', 'simpleportal' ),
'primary' => __( 'Primary Navigation', 'simpleportal' ),
'admin' => __( 'Admin Navigation', 'simpleportal' ),
'contributor' => __( 'Contributors Navigation', 'simpleportal' ),
'subscriber' => __( 'Subscribers Navigation', 'simpleportal' ),
'author' => __( 'Authors Navigation', 'simpleportal' ),
'vertical' => __( 'Vertical Navigation', 'simpleportal' )
) );
}
add_action( 'after_setup_theme', 'simpleportal_setup' );

// Unregister some of the Default Theme Sidebars
function simpleportal_remove_some_widgets(){
unregister_sidebar( 'sidebar-1' );
unregister_sidebar( 'sidebar-2' );
}
add_action( 'widgets_init', 'simpleportal_remove_some_widgets', 11 );

// list subpages
if(!function_exists('simpleportal_get_post_top_ancestor_id')){
/**
* Gets the id of the topmost ancestor of the current page. Returns the current
* page's id if there is no parent.
*
* @uses object $post
* @return int
*/
function simpleportal_get_post_top_ancestor_id(){
global $post;

if($post->post_parent){
$ancestors = array_reverse(get_post_ancestors($post->ID));
return $ancestors[0];
}

return $post->ID;
}}

// Stylesheets
function simpleportal_styles() {

// Register stylesheets
wp_enqueue_style( 'bootstrap', get_stylesheet_directory_uri() . '/css/bootstrap.min.css', array(), '3.1.1', 'all' );
wp_enqueue_style( 'simpleportal-main', get_stylesheet_directory_uri() . '/style.css', array(), '1.5', 'all' );
wp_enqueue_style( 'bbpress-print', get_stylesheet_directory_uri() . '/css/bbpress-print.css', array(), '1.0', 'print' );
}
add_action( 'wp_enqueue_scripts', 'simpleportal_styles' );


function simpleportal_scripts_styles() {
// Loads Bootstrap JavaScript file.
wp_enqueue_script( 'simpleportal-bootstrap', get_stylesheet_directory_uri() . '/js/bootstrap.min.js', array( 'jquery' ), '3.1.1', true );
if ( preg_match( '/MSIE [6-8]/', $_SERVER['HTTP_USER_AGENT'] ) ) {
wp_enqueue_script( 'simpleportal-html5shiv', get_stylesheet_directory_uri() . '/js/html5shiv.js', array( 'jquery' ), '3.1.1', true );
wp_enqueue_script( 'simpleportal-respond', get_stylesheet_directory_uri() . '/js/respond.min.js', array( 'jquery' ), '3.1.1', true );
}
}
add_action( 'wp_enqueue_scripts', 'simpleportal_scripts_styles' );

// Start BuddyPress Specific Functions to this theme

if (function_exists('bp_is_active')) :

// BuddyPress show message notifications in theme
function simpleportal_current_user_notification_count() {
$notifications = bp_notifications_get_notifications_for_user(bp_loggedin_user_id(), 'object');
$count = !empty($notifications) ? count($notifications) : 0;

echo $count;
}

else : endif;

?>