<?php
/**
 * Custom Community Default Theme Settings
 * Is being used during initial / first theme setup, as well as a worst-case fallback for the theme customizer.
 * 
 * @author Fabian Wolf
 * @packacke cc2
 * @since 2.0r2
 */
 
define( 'CC2_DEFAULT_SETTINGS', serialize( 
	array(
		'theme_mods' => array(
			/**
			 * Color Scheme fun
			 */
	
			'color_scheme' => 'default',
			
			'font_color' => '2826a8',
			'link_color' => '3ac471',
			'hover_color' => '81d742',
			'font_family' => 'Ubuntuu Condensed',
			
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
			'tagline_text_color' => '#a9a9a9',
			 
			/* Header Section*/
			/**
			 * Static frontpage is set, ie. home != blog
			 * 
			 * @see http://codex.wordpress.org/Function_Reference/is_home#Blog_Posts_Index_vs._Site_Front_Page
			 */
			'display_header_home' => false,
			'display_header_static_frontpage' => true,
			'display_header_posts' => true,
			'display_header_pages' => true,
			'display_header_archive' => true,
			'display_header_search' => true,
			'display_header_404' => true,

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

			'top_nav_background_color' => '#2f2f2f',
			'top_nav_text_color' => '#a9a9a9',
			'top_nav_hover_text_color' => '#ffffff',

			/**
			 * Secondary nav color settings (that's usually after the header)
			 */
			'secondary_nav_background_color' => '#2f2f2f',
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
			 * Display Header(s)
			 */
			 
			'display_page_title_heading' => false, /** possibly not required / wrongly named */
			'display_page_title[home]' => true,
			'display_page_title[posts]' => true,
			'display_page_title[pages]' => true,
			'display_page_title[archive]' => true,
			'display_page_title[search]' => true,
			'display_page_title[error]' => true,
			 
			 /**
			  * Center titles
			  */
			'center_title[global]' => false,
			'center_title[home]' => false,
			'center_title[posts]' => false,
			'center_title[pages]' => false,
			'center_title[archive]' => false,
			'center_title[search]' => false,
			'center_title[error]' => false,

			/**
			 * Sidebars Section
			 */

			'default_layout' => 'left',
			'default_page_layout' => 'default',
			'default_post_layout' => 'default',
			'default_archive_layout' => 'default',
			
			'hide_left_sidebar_on_phones' => true,
			'hide_right_sidebar_on_phones' => false,
			
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
			 * Blog section
			 */
			
			'cc_list_post_style' => 'blog-style',
			
			'show_date' => true,
			'show_category' => true,
			'show_author' => true,
			'show_author_image[archive]' => true,
			
			/**
			 * Blog section: Single view
			 */
			'single_show_date' => true,
			'single_show_category' => true,
			'single_show_author' => true,
			'show_author_image[single_post]' => true,
		
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
			
			'caption_title_bg_color' => 'f2694b', /** runs through maybe_hex .. shouldnt do any harm leaving out the hash char */
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
			
			'cc_sliding_time' => '5000',
			'cc_slider_width' => 'auto',
			'cc_slider_height' => 'none',
			
			/**
			 * Advanced bootstrap settings:
			 * - container sizes (small, medium, large)
			 * - sidebar / content col grid customization
			 */
			 
			'bootstrap_container_width[small]' => '750px',
			'bootstrap_container_width[medium]' => '970px',
			'bootstrap_container_width[large]' => '1170px',
			
			/**
			 * Sidebars: Custom Columns
			 */
			'bootstrap_custom_sidebar_cols[left]' => '4',
			'bootstrap_custom_sidebar_cols[right]' => '4',
			
			'cc2_comment_form_orientation' => 'vertical',
		),
	) 
) );

