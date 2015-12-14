<?php
// global $wp_registered_sidebars;
#########################################
function onetone_widgets_init() {
		register_sidebar(array(
			'name' => __('Displayed Everywhere', 'onetone'),
			'id'   => 'displayed-everywhere',
			'before_widget' => '<div id="%1$s" class="widget %2$s">', 
			'after_widget' => '<span class="seperator extralight-border"></span></div>', 
			'before_title' => '<h3 class="widgettitle">', 
			'after_title' => '</h3>', 
			));
		
	register_sidebar(array(
			'name' => __('Footer Area One', 'onetone'),
			'id'   => 'footer_widget_1',
			'before_widget' => '<div id="%1$s" class="widget widget-box %2$s">', 
			'after_widget' => '<span class="seperator extralight-border"></span></div>', 
			'before_title' => '<h3 class="widget-title">', 
			'after_title' => '</h3>' 
			));
	register_sidebar(array(
			'name' => __('Footer Area Two', 'onetone'),
			'id'   => 'footer_widget_2',
			'before_widget' => '<div id="%1$s" class="widget widget-box %2$s">', 
			'after_widget' => '<span class="seperator extralight-border"></span></div>', 
			'before_title' => '<h3 class="widget-title">', 
			'after_title' => '</h3>' 
			));
	register_sidebar(array(
			'name' => __('Footer Area Three', 'onetone'),
			'id'   => 'footer_widget_3',
			'before_widget' => '<div id="%1$s" class="widget widget-box %2$s">', 
			'after_widget' => '<span class="seperator extralight-border"></span></div>', 
			'before_title' => '<h3 class="widget-title">', 
			'after_title' => '</h3>' 
			));
}
add_action( 'widgets_init', 'onetone_widgets_init' );