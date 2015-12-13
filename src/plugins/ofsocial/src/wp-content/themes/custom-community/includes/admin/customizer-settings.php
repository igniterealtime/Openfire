<?php
/**
 * Settings for both the Theme Customizer as well as for validating imported setings (Backup & Reset Settings)
 * 
 * NOTE: Replaced [ with ( because of the utterly DUMB Theme Check plugin *eyerolls*
 * Base settings:
 * - add_setting $slug, array(
 * 		'default' => $default,
 * 		'capability' => 'edit_theme_options',
		'transport'  => 'refresh',
	);
 * - add_control $control_params ) OR add_control( new $control_type, $control_params )
 * 
 * Struct:
 * 'slug' => array(
 * 		'default' => $default_value,
 * 		'sanitize' => $sanitize_callback - if left out = the theme default sanitize call (ie. array('cc2_Pasteur', 'customizer_default' ) ), or else any existing sanitize function
 * )
 * 
 * Known placeholders = identical to the return of cc2_CustomizerLoader::prepare_variables()
 * 
 * 
 * 
 * @author Fabian Wolf
 * @packacke cc2
 * @since 2.0.20
 */
 
$customizer_settings = array( 
	'theme_mods' => array(

		/**
		 * Color Scheme fun .. is stored in the actual scheme data
		 */

		
		/**
		 * static_front_page aka Home Page
		 * NOTE: Built-in section
		 */
		
		'hide_front_page_content' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'section'  		=> 	'static_front_page',
			'control_type' => 'default',
			'control_params' => array(
				'label'    		=> 	__('Hide all content on the front page', 'cc2'),
				'type'     		=> 	'checkbox',
				'priority'		=> 	261,
			)
		),

		/**
		 * Site Title & Tagline
		 */
		'site_title_font_family' => array(
			'default' => 'Pacifico',
			
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'section' 		=> 	'title_tagline',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'   		=> 	__('Site Title Font Family', 'cc2'),
				'priority'		=> 	180,
				'type'    		=> 	'select',
				'choices'    	=> 	'%cc2_font_family%',
			),
		),
			
		'site_title_position' => array(
			'default' => 'left',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section' 		=> 	'title_tagline',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'   		=> 	__('Site Title Position', 'cc2'),
				
				'priority'		=> 	200,
				'type'    		=> 	'select',
				'choices'    	=> 	'%cc2_h_positions%',
			),
		),
		'tagline_font_family' => array(
			'default' => 'inherit',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section' 		=> 	'title_tagline',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'   		=> 	__('Tagline Font Family', 'cc2'),
				'priority'		=> 	200,
				'type'    		=> 	'select',
				'choices'    	=> 	'%cc2_font_family',
			
			),
		),
		'tagline_text_color' => array(
			'default' => '#a9a9a9',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			
			'section' 		=> 	'title_tagline',
			
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Tagline Color', 'cc2'),
				'priority'				=> 201,
			),
		),
		 
		/* Header Section*/
		/**
		 * Static frontpage is set, ie. home != blog
		 * 
		 * @see http://codex.wordpress.org/Function_Reference/is_home#Blog_Posts_Index_vs._Site_Front_Page
		 */

		'header_height' => array(
			'default' => 'auto',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section'    	=> 'header',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'      	=> __('Header Height', 'cc2'),
				
				'priority'   	=> 120,
			),
		),
		'header_height_blog' => array('default' => 'auto',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section'    	=> 'header',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'      	=> __('Header Height on Blog', 'cc2'),
				'priority'   	=> 140,
			),
		),
		'header_height_home' => array('default' => 'auto',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section'    	=> 'header',
			
			'control_type' => 'default',
			'control_params' => array(
				'label'      	=> __('Header Height on Homepage', 'cc2'),
			
				'priority'   	=> 145,
			),
		),

		'header_background_color' => array('default' => 'transparent',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			
			'section'    	=> 'header',
			
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Header Background Color', 'cc2'),
					'priority'				=> 220,
			),
		
		), /* default: #ffffff */
		
		'header_background_image' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			
			'section'    	=> 'header',
			
			'control_type' => 'image',
			'control_params' => array(
					'label'    				=> __('Header Background Image', 'cc2'),
					'priority'				=> 221,
			),
		),
		 
		/* Adding to Navigation Section (Nav) */
		'fixed_top_nav' => array(
			'default' => true,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
				'label'    		=> 	__('Top nav fixed to top?', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'checkbox',
				'priority'		=> 	40,
			),
		),
		
		'top_nav_position' => array('default' => 'left',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				'label'   		=> 	__('Top Nav Position', 'cc2'),
				'section' 		=> 	'nav',
				'priority'		=> 	50,
				'type'    		=> 	'select',
				'choices'    	=> '%cc2_h_positions%'
			),
		),

		'secondary_nav_position' => array('default' => 'left',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				'label'   		=> 	__('Secondary Nav Position', 'cc2'),
				'section' 		=> 	'nav',
				'priority'		=> 	55,
				'type'    		=> 	'select',
				'choices'    	=> '%cc2_h_positions%'
			),
		),

		'color_scheme_top_nav' => array('default' => 'light',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				'label'    		=> 	__('Top nav color scheme', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'select',
				'choices'		=> array(
					'dark' => 'Dark',
					'light' => 'Light (Default)',
					'custom' => 'Custom',
				),
				'priority'		=> 	81,
			),
		),
		'color_scheme_bottom_nav' => array('default' => 'light',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				'label'    		=> 	__('Bottom nav color scheme', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'select',
				'choices'		=> array(
					'dark' => 'Dark',
					'light' => 'Light (Default)',
					'custom' => 'Custom',
				),
				'priority'		=> 	83,
			),
		),
		 
		/**
		 * Top nav color settings
		 */

		'top_nav_background_color' => array('default' => '#2f2f2f',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Top Nav Background Color', 'cc2'),
				'section'  				=> 'nav',
				'priority'				=> '%nav_section_priority%',
			),
		),
		'top_nav_text_color' => array('default' => '#a9a9a9',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Top Nav Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> '%nav_section_priority%',
			),
		),
		'top_nav_hover_text_color' => array('default' => '#ffffff',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Top Nav Hover Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> 'nav_section_priority',
			),
		),

		/**
		 * Secondary nav color settings (that's usually after the header)
		 */
		'secondary_nav_background_color' => array('default' => '#2f2f2f',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Secondary Nav Background Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> '%nav_section_priority%',
			),
		),
		'secondary_nav_text_color' => array('default' => '#a9a9a9',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Secondary Nav Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			),
		),
		'secondary_nav_hover_text_color' => array('default' => '#ffffff',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'color',
			'control_params' => array(
				'label'    				=> __('Secondary Nav Hover Font Color', 'cc2'),
				'section'  				=> 'nav',
				'priority'				=> '%nav_section_priority',
			),
		),
		 
		 
		/**
		 * Header Top Nav - Add Branding
		 */
		  
		'top_nav_brand' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		),
		
		'top_nav_brand_text_color' => array('default' => '#a9a9a9',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'top_nav_brand_image' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		 
		/**
		 * Branding: Header Bottom Nav
		 */
		 
		'bottom_nav_brand' => array(
			'default' => true,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		),
		
		'bottom_nav_brand_text_color' => array('default' => '#a9a9a9',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		
		),
		'bottom_nav_brand_image' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		 

		/**
		 * Widget section
		 */
		 
		'widget_title_text_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'widget_title_background_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'widget_title_font_size' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'widget_background_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'widget_link_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'widget_link_text_hover_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		/**
		 * Typography Section
		 */
		
		'title_font_family' => array('default' => 'Ubuntu Condensed',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'title_font_weight' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* bold => true|false */
		'title_font_style' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* italic => true|false */
		
		'title_font_color' => array('default' => '',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		'h1_font_size' => array('default' => '48px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'h2_font_size' => array('default' => '32px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'h3_font_size' => array('default' => '28px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'h4_font_size' => array('default' => '24px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'h5_font_size' => array('default' => '22px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'h6_font_size' => array('default' => '20px',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		

		/**
		 * Footer Section
		 * 
		 * NOTE: background_image says 'logo',), but should actually be empty or sth. else.
		 */
		 
		'footer_fullwidth_background_image' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'footer_fullwidth_background_color' => array('default' => '#eeeeee',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'footer_fullwidth_border_top_color' => array('default' => '#dddddd',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'footer_fullwidth_border_bottom_color' => array('default' => '#333333',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),

		/**
		 * Slider section
		 */

		'cc_slideshow_template' => array('default' => 'none',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'cc2_slideshow_style' => array('default' => 'slides-only',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'cc_slider_display' => array('default' => 'home',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'cc_slider_position' => array('default' => 'cc_after_header',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		'slider_effect_title' => array('default' => 'bounceInLeft',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'slider_effect_excerpt' => array('default' => 'bounceInRight',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		),
		'cc_slider_text_align' => array('default' => 'center',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		'caption_title_bg_color' => array('default' => 'f2694b',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		), /** runs through maybe_hex .. shouldnt do any harm leaving out the hash char */
		'caption_title_font_color' => array('default' => 'ffffff',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'caption_title_font_family' => array('default' => 'Ubuntu Condensed',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		'caption_title_font_weight' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* bold => true|false */
		'caption_title_font_style' => array(
			'default' => true,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* italic => true|false */
		'caption_title_shadow' => array(
			'default' => true,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* text-shadow => true|false */
		
		'caption_title_opacity' => array('default' => '0.9',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		
		'caption_text_bg_color' => array('default' => 'fbfbfb',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'caption_text_font_color' => array('default' => '333333',
			'sanitize_callback' 	=>  array( 'cc2_Pasteur', 'sanitize_hex'),
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),
		'caption_text_font_family' => array('default' => '',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
				'control_type' => 'default',
				'control_params' => array(
				
			),
		),
		'caption_text_font_weight' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* bold => true|false */
		'caption_text_font_style' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* italic => true|false */
		'caption_text_shadow' => array(
			'default' => false,
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_boolean' ),
			'control_type' => 'default',
			'control_params' => array(
			
			),
		), /* text-shadow => true|false */
		
		'caption_text_opacity' => array('default' => '0.8',
			'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_text' ),
			'control_type' => 'default',
			'control_params' => array(
				
			),
		),

	),
	
	'advanced_settings' => array(
	
	),
	
	
);


