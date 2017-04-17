<?php
/**
 * iBuddy custom sidebars for home and blog pages
 *
 * @package iBuddy
 */
?>
<?php

/*
 * Home Sidebar
 */

// Creating the Side bar
if ( ! function_exists('home_sidebar') ) {

// Register Sidebar
function home_sidebar()  {
	$args = array(
		'id'            => 'home_sidebar',
		'name'          => __( 'Home Sidebar', 'ibuddy' ),
		'description'   => __( 'Add widgets to show in home page footer area', 'ibuddy' ),
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h1 class="widget-title">',
		'after_title'   => '</h1>',
	);

	register_sidebar( $args );
}

// Hook into the 'widgets_init' action
add_action( 'widgets_init', 'home_sidebar' );

}

// Side bar template output
function get_home_sidebar(){

                echo '<div id="secondary" class="widget-area" role="complementary">';
		do_action( 'before_sidebar' );
		if ( !function_exists('dynamic_sidebar') || !dynamic_sidebar('home_sidebar') ) :
	        endif;
                echo '</div><!-- #secondary -->';
}

/*
 * Blog Sidebar
 */
if ( ! function_exists('blog_sidebar') ) {

// Register Sidebar
function blog_sidebar()  {
	$args = array(
		'id'            => 'blog_sidebar',
		'name'          => __( 'Blog Sidebar', 'ibuddy' ),
		'description'   => __( 'Add widgets to show in blog page', 'ibuddy' ),
		'before_widget' => '<aside id="%1$s" class="widget %2$s">',
		'after_widget'  => '</aside>',
		'before_title'  => '<h1 class="widget-title">',
		'after_title'   => '</h1>',
	);

	register_sidebar( $args );
}

// Hook into the 'widgets_init' action
add_action( 'widgets_init', 'blog_sidebar' );

}

function get_blog_sidebar(){

                echo '<div id="secondary" class="widget-area" role="complementary">';
		do_action( 'before_sidebar' );
		if ( !function_exists('dynamic_sidebar') || !dynamic_sidebar('blog_sidebar') ) :
	        endif;
                echo '</div><!-- #secondary -->';
}

?>
