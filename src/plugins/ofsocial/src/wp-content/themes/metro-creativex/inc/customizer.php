<?php
/**
 * Theme Customizer
 *
 * @package metro-creativex
 */

/**
 * Add postMessage support for site title and description for the Theme Customizer.
 *
 * @param WP_Customize_Manager $wp_customize Theme Customizer object.
 */
function metro_creativex_customize_register( $wp_customize ) {

	class metro_creativex_Theme_Support extends WP_Customize_Control
	{
		public function render_content()
		{

		}

	}

	$wp_customize->get_setting( 'blogname' )->transport         = 'postMessage';
	$wp_customize->get_setting( 'blogdescription' )->transport  = 'postMessage';
	$wp_customize->get_setting( 'header_textcolor' )->transport = 'postMessage';

	$wp_customize->remove_section( 'background_image' );
	$wp_customize->remove_control('header_textcolor');

	/* theme notes */
	$wp_customize->add_section( 'metro_creativex_theme_notes' , array(
		'title'      => __('ThemeIsle theme notes','metro-creativex'),
		'description' => sprintf( __( "Thank you for being part of this! We've spent almost 6 months building ThemeIsle without really knowing if anyone will ever use a theme or not, so we're very grateful that you've decided to work with us. Wanna <a href='http://themeisle.com/contact/' target='_blank'>say hi</a>?
		<br/><br/><a href='http://themeisle.com/demo/?theme=MetroX' target='_blank' />View Theme Demo</a> | <a href='https://themeisle.com/forums/forum/metrox/' target='_blank'>Get theme support</a>","metro-creativex")),
		'priority'   => 30,
	));
	$wp_customize->add_setting(
        'metro_creativex_theme_notes', array('sanitize_callback' => 'metro_creativex_sanitize_notes')
	);

	$wp_customize->add_control( new metro_creativex_Theme_Support( $wp_customize, 'metro_creativex_theme_notes',
	    array(
	        'section' => 'metro_creativex_theme_notes',
	   )
	));

	$wp_customize->add_section( 'metro_creativex_logo_logo_section' , array(
    	'title'       => __( 'Logo', 'metro-creativex' ),
    	'priority'    => 31,
    	'description' => __('Upload a logo to replace the default site name and description in the header','metro-creativex'),
	) );

	$wp_customize->add_setting( 'metro-creativex_logo',
        array('sanitize_callback' => 'esc_url_raw') );
	$wp_customize->add_control( new WP_Customize_Image_Control( $wp_customize, 'metro-creativex_logo', array(
	    'label'    => __( 'Logo', 'metro-creativex' ),
	    'section'  => 'metro_creativex_logo_logo_section',
	    'settings' => 'metro-creativex_logo',
	) ) );


	/* Socials */
	$wp_customize->add_section( 'metro_creativex_socials_section' , array(
    	'title'       => __( 'Socials', 'metro-creativex' ),
    	'priority'    => 32,
	) );

	$wp_customize->add_setting( 'metro-creativex_social_link_fb', array('sanitize_callback' => 'esc_url_raw') );
	$wp_customize->add_control( 'metro-creativex_social_link_fb', array(
	    'label'    => __( 'Facebook link', 'metro-creativex' ),
	    'section'  => 'metro_creativex_socials_section',
	    'settings' => 'metro-creativex_social_link_fb',

		'priority'    => 5,
	) );
	$wp_customize->add_setting( 'metro-creativex_social_link_tw', array('sanitize_callback' => 'esc_url_raw') );
	$wp_customize->add_control( 'metro-creativex_social_link_tw', array(
	    'label'    => __( 'Twitter link', 'metro-creativex' ),
	    'section'  => 'metro_creativex_socials_section',
	    'settings' => 'metro-creativex_social_link_tw',
		'priority'    => 10,
	) );
	
	/* colors */
	$wp_customize->add_setting( 'metro-creativex_text_color', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_text_color', 
			array(
				'label'      => __( 'Texts color', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_text_color',
				'priority'   => 10
			) ) 
	);
	
	$wp_customize->add_setting( 'metro-creativex_link_color', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_link_color', 
			array(
				'label'      => __( 'Links color', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_link_color',
				'priority'   => 11
			) ) 
	);
	
	$wp_customize->add_setting( 'metro-creativex_link_color_hover', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_link_color_hover', 
			array(
				'label'      => __( 'Links color - hover', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_link_color_hover',
				'priority'   => 12
			) ) 
	);
	
	$wp_customize->add_setting( 'metro-creativex_nav_color', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_nav_color', 
			array(
				'label'      => __( 'Navigation menu color', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_nav_color',
				'priority'   => 13
			) ) 
	);
	
	$wp_customize->add_setting( 'metro-creativex_nav_color_hover', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_nav_color_hover', 
			array(
				'label'      => __( 'Navigation menu color - hover', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_nav_color_hover',
				'priority'   => 14
			) ) 
	);
	
	$wp_customize->add_setting( 'metro-creativex_sidebar_title_color', array('sanitize_callback' => 'metro_creativex_sanitize_notes') );
	$wp_customize->add_control(
	    new WP_Customize_Color_Control( 
			$wp_customize, 
			'metro-creativex_sidebar_title_color', 
			array(
				'label'      => __( 'Sidebar title color', 'metro-creativex' ),
				'section'    => 'colors',
				'settings'   => 'metro-creativex_sidebar_title_color',
				'priority'   => 15
			) ) 
	);
	
}
add_action( 'customize_register', 'metro_creativex_customize_register' );

function metro_creativex_sanitize_notes( $input ) {

    return $input;

}


/**
 * Bind JS handlers to make Theme Customizer preview reload changes asynchronously.
 */
function metro_creativex_customize_preview_js() {
	wp_enqueue_script( 'customizerJS', get_template_directory_uri() . '/js/customizer.js', array( 'jquery' ), '20131205', true );
}
add_action( 'customize_preview_init', 'metro_creativex_customize_preview_js' );