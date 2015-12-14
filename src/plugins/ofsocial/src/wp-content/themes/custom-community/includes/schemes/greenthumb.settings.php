<?php
/**
 * Custom Community Default Theme Settings
 * Is being used during initial / first theme setup, as well as a worst-case fallback for the theme customizer.
 * 
 * @author Fabian Wolf
 * @packacke cc2
 * @since 2.0r2
 */
 
$settings = array(
	/**
	 * Color Scheme fun .. is stored in the actual scheme data
	 */

	
	/**
	 * static_front_page aka Home Page
	 * NOTE: Built-in section
	 */
	
	'hide_front_page_content' => false,

	/**
	 * Site Title & Tagline
	 */
	'site_title_font_family' => 'Pacifico',
	'site_title_position' => 'left',
	'tagline_font_family' => 'inherit',
	'tagline_text_color' => '#456B08',
	 
	/* Header Section*/
	
	'header_textcolor' => '#aed132',
	
	'header_height' => 'auto',
	'header_height_blog' => 'auto',
	'header_height_home' => 'auto',

	'header_background_color' => 'transparent', /* default: #ffffff */
	'header_background_image' => '',
	
	 
	/* Adding to Navigation Section (Nav) */
	'fixed_top_nav' => true,
	'top_nav_position' => 'left',

	'secondary_nav_position' => 'left',

	'color_scheme_top_nav' => 'light',
	'color_scheme_bottom_nav' => 'light',
	 
	/**
	 * Top nav color settings
	 */

	'top_nav_background_color' => '#2F4A2F',
	'top_nav_text_color' => '#a9a9a9',
	'top_nav_hover_text_color' => '#ffffff',

	/**
	 * Secondary nav color settings (that's usually after the header)
	 */
	'secondary_nav_background_color' => '#2F362F',
	'secondary_nav_text_color' => '#a9a9a9',
	'secondary_nav_hover_text_color' => '#ffffff',
	 
	 
	/**
	 * Header Top Nav - Add Branding
	 */
	  
	'top_nav_brand' => false,
	'top_nav_brand_text_color' => '#a9a9a9',
	'top_nav_brand_image' => '',
	 
	/**
	 * Branding: Header Bottom Nav
	 */
	 
	'bottom_nav_brand' => true,
	'bottom_nav_brand_text_color' => '#a9a9a9',
	'bottom_nav_brand_image' => '',
	 

	/**
	 * Widget section
	 */
	 
	'widget_title_text_color' => '',
	'widget_title_background_color' => '',
	'widget_title_font_size' => '',
	'widget_background_color' => '',
	'widget_link_color' => '',
	'widget_link_text_hover_color' => '',
	
	/**
	 * Typography Section
	 */
	
	'title_font_family' => 'Ubuntu Condensed',
	'title_font_weight' => false, /* bold => true|false */
	'title_font_style' => false, /* italic => true|false */
	'title_font_color' => '',
	
	'h1_font_size' => '48px',
	'h2_font_size' => '32px',
	'h3_font_size' => '28px',
	'h4_font_size' => '24px',
	'h5_font_size' => '22px',
	'h6_font_size' => '20px',
	

	/**
	 * Footer Section
	 * 
	 * NOTE: background_image says 'logo', but should actually be empty or sth. else.
	 */
	 
	'footer_fullwidth_background_image' => '',
	'footer_fullwidth_background_color' => '#eeeeee',
	'footer_fullwidth_border_top_color' => '#dddddd',
	'footer_fullwidth_border_bottom_color' => '#333333',

	/**
	 * Slider section
	 */

	'cc_slideshow_template' => 'none',
	'cc2_slideshow_style' => 'slides-only',
	'cc_slider_display' => 'home',
	'cc_slider_position' => 'cc_after_header',
	
	'slider_effect_title' => 'bounceInLeft',
	'slider_effect_excerpt' => 'bounceInRight',
	'cc_slider_text_align' => 'center',
	
	'caption_title_bg_color' => '#84F24B', /** runs through maybe_hex .. shouldnt do any harm leaving out the hash char */
	'caption_title_font_color' => 'ffffff',
	'caption_title_font_family' => 'Ubuntu Condensed',
	'caption_title_font_weight' => false, /* bold => true|false */
	'caption_title_font_style' => true, /* italic => true|false */
	'caption_title_shadow' => true, /* text-shadow => true|false */
	'caption_title_opacity' => '0.9',
	
	'caption_text_bg_color' => 'fbfbfb',
	'caption_text_font_color' => '333333',
	'caption_text_font_family' => '',
	'caption_text_font_weight' => false, /* bold => true|false */
	'caption_text_font_style' => false, /* italic => true|false */
	'caption_text_shadow' => false, /* text-shadow => true|false */
	'caption_text_opacity' => '0.8',

);


