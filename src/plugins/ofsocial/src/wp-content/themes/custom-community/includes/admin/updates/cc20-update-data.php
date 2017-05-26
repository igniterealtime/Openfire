<?php 
/**
 * Update script for cc1 => cc2 2.0r1
 * 
 * @author Fabian Wolf
 * @package cc2
 */
	

$arrOptions['customizer']['title'] = 'Customizer Settings';
$arrOptions['customizer']['old_option_name'] = 'custom_community_theme_options';
$arrOptions['customizer']['old_setting_prefix'] = 'cap_';
$arrOptions['customizer']['new_option_name'] = 'cc2_theme_mods';
$arrOptions['customizer']['import_data'] = array(

	// General Tab
	'style_css' 			=>	'color_scheme',
	'bg_body_color'			=>	'background_color',
	'bg_body_img'			=>	'background_image',
	'bg_body_img_repeat'	=>	'background_repeat',
	'bg_body_img_pos'		=>	'background_position_x',		/*	select => radio		same value names!*/
	'bg_body_img_fixed'	=>	array(
		'new_setting_name' => 'background_attachment',
		
		'value_conversion' => array(
			'enabled' => 'fixed', // old => new
			'disabled' => 'scroll',
		),
	), 		//	select => radio 	Enabled => Fixed		Disabled => Scroll
	'bg_container_color'	=>	'container_color',				
	
	'title_font_style' => array(
		'new_setting_name' => 'title_font_family',
		'conversion_callback' => 'convert_font_family',
	),			// font-family array has changed! -> do it once for all
	'title_color'			=>	'title_font_color',
	'title_weight' => array(
		'new_setting_name' => 'title_font_weight',
		'value_conversion' => array(
			'bold' => true,
			'normal' => false,
		),
		
	),			// select => checkbox	bold => checked			normal	=> unchecked
	'font_style' =>	array(
		'new_setting_name' => 'font_family',
		'callback' => 'font_conversion',
	), 					// * font-family array has changed! 
	'font_color'			=>	'font_color',
	'link_color'			=>	'link_color',
	'link_color_hover'		=>	'hover_color',
	'link_underline'		=>	'link_underline',
	
	// Sidebars
	
	'sidebar_position' => array(
		'new_setting_name' => 'default_layout',
		'value_conversion' => array(
			'left' => 'left',
			'right' => 'right',
			'left and right' => 'left-right',
			'fullwidth' => 'fullwidth',
		),
	),
	
	'archive_template' => array(
		'new_setting_name' => 'default_archive_layout', 
		'value_conversion' => array(
			'left' => 'left',
			'right' => 'right',
			'left and rightt' => 'left-right',
			'left and right' => 'left-right',
			'fullwidth' => 'fullwidth',
		),
	),


	// Header Tab					
	'header_text_color'	=> 'header_textcolor',
	
	'header_text' => array(
		'new_setting_name' => 'display_header_text',
		'value_conversion' => array(
			'on' => true,
			'off' => false,
		),
	),			//	select => checkbox	on => checked			off => unchecked 


	// Slideshow
	// is handled seperately
	
	
	// Blog section
	//'default_homepage_hide_avatar' => '',
	
		/*
		[cap_posts_lists_style_taxonomy] => blog
    [cap_archive_template] => right
    [cap_magazine_style_taxonomy] => img-mouse-over
    [cap_posts_lists_style_dates] => blog
    [cap_magazine_style_dates] => img-mouse-over
    [cap_posts_lists_style_author] => blog
    [cap_magazine_style_author] => img-mouse-over
    [cap_posts_lists_hide_avatar] => show
    [cap_posts_lists_style</span>] => bubbles
    [cap_posts_lists_hide_date] => show
    [cap_posts_lists_category_order] => DESC
    [cap_posts_lists_tag_order] => DESC
    [cap_posts_lists_date_order] => DESC
	*/
	
	
		// Archive View - Display Post Meta
		'posts_lists_hide_date' => array(
			'new_setting_name' => 'show_date',
			'value_conversion' => array(
				'show' => true,
				'hide' => false,
			),
		),
		
		/* Show / hide avatars in blog display

		Show or hide the avatars in the post listing.
		This option is for categories, tags and archives pages, showing your articles.
		*/
		
		'posts_lists_hide_avatar' => array(
			'new_setting_name' => 'show_author_image[archive]',
			'value_conversion' => array(
				'show' => true,
				'hide' => false,
			),
		),
		
	// Branding
	'logo' => 'top_nav_brand_image',
	
	
	// Widgets
	/*
	    [cap_bg_widgettitle_style] => angled
    [cap_widgettitle_font_style] => Helvetica Neue, Helvetica, Arial, sans-serif
    [cap_widgettitle_font_size] => 
    [cap_widgettitle_font_color] => 
    [cap_bg_widgettitle_color] => 
    [cap_bg_widgettitle_img] => 
    [cap_bg_widgettitle_img_repeat] => no repeat
    [cap_capitalize_widgets_li] => no
    [cap_capitalize_widgettitles] => no
    */
    'widgettitle_font_size' => 'widget_title_font_size',
    'widgettitle_font_color' => 'widget_title_text_color',
    'bg_widgettitle_color' => 'widget_title_background_color',
    
    // Footer
    /*
     *  [cap_footer_width] => default
        [cap_footerall_height] => 
    [cap_bg_footerall_color] => 
    [cap_bg_footerall_img] => 
    [cap_bg_footerall_img_repeat] => no repeat
    [cap_footer_height] => 
    [cap_bg_footer_color] => 
    [cap_bg_footer_img] => 
    [cap_bg_footer_img_repeat] => no repeat
    */
    'bg_footerall_color' => 'footer_fullwidth_background_color',
    'bg_footerall_img' => 'footer_fullwidth_background_image',	
);

/**
 * Custom options
 * 
 * syntax: 'option_name' => array( 'old_name' => 'new_name' [=> 'optional_callback|type|variation' => array('data') ]
 */

$arrOptions['custom_options'] = array(
	'title' => 'Various Theme Settings',
	'old_option_name' => 'custom_community_theme_options',
	'old_setting_prefix' => 'cap_',
	'new_option_name' =>'cc2_advanded_settings',
	'allow_empty_values' => false,
	'import_data' => array(
		'overwrite_css' => 'custom_css',
	),
);


/**
 * From the options:
 * Array
(
    [cap_style_css] => white
    [cap_website_width] => 1000
    [cap_website_width_unit] => px
    [cap_add_custom_background] => 0
    [cap_bg_body_color] => 
    [cap_bg_body_img] => 
    [cap_bg_body_img_fixed] => 0
    [cap_bg_body_img_pos] => center
    [cap_bg_body_img_repeat] => no repeat
    [cap_cc_responsive_enable] => 0
    [cap_bg_container_color] => 
    [cap_bg_container_nolines] => show
    [cap_v_line_color] => 
    [cap_bg_container_img] => 
    [cap_bg_container_img_repeat] => no repeat
    [cap_container_corner_radius] => rounded
    [cap_sidebar_position] => right
    [cap_title_font_style] => Helvetica Neue, Helvetica, Arial, sans-serif
    [cap_title_size] => 
    [cap_title_weight] => bold
    [cap_title_color] => 
    [cap_subtitle_font_style] => Helvetica Neue, Helvetica, Arial, sans-serif
    [cap_subtitle_weight] => bold
    [cap_subtitle_color] => 
    [cap_excerpt_on] => content
    [cap_excerpt_length] => 
    [cap_font_style] => Helvetica Neue, Helvetica, Arial, sans-serif
    [cap_font_size] => 
    [cap_font_color] => 
    [cap_link_color] => 
    [cap_link_color_hover] => 
    [cap_link_color_subnav_adapt] => just the link colour
    [cap_link_underline] => never
    [cap_link_bg_color] => 
    [cap_link_bg_color_hover] => 
    [cap_link_styling_title_adapt] => just the hover effect
    [cap_favicon] => 
    [cap_default_homepage_hide_avatar] => show
    [cap_posts_lists_style_home] => blog
    [cap_magazine_style_home] => img-mouse-over
    [cap_default_homepage_last_posts] => show
    [cap_default_homepage_style] => bubbles
    [cap_default_homepage_hide_date] => show
    [cap_posts_lists_style_taxonomy] => blog
    [cap_archive_template] => right
    [cap_magazine_style_taxonomy] => img-mouse-over
    [cap_posts_lists_style_dates] => blog
    [cap_magazine_style_dates] => img-mouse-over
    [cap_posts_lists_style_author] => blog
    [cap_magazine_style_author] => img-mouse-over
    [cap_posts_lists_hide_avatar] => show
    [cap_posts_lists_style</span>] => bubbles
    [cap_posts_lists_hide_date] => show
    [cap_posts_lists_category_order] => DESC
    [cap_posts_lists_tag_order] => DESC
    [cap_posts_lists_date_order] => DESC
    [cap_bg_loginpage_img] => 
    [cap_login_logo_height] => 
    [cap_bg_loginpage_body_color] => 
    [cap_bg_loginpage_backtoblog_fade_1] => 
    [cap_bg_loginpage_backtoblog_fade_2] => 
    [cap_add_to_head] => 
    [cap_add_to_footer] => 
    [cap_avatars_only_in_comments] => no
    [cap_add_custom_image_header] => 0
    [cap_header_text] => on
    [cap_header_text_color] => 
    [cap_logo] => 
    [cap_header_height] => 200
    [cap_header_width] => default
    [cap_header_img] => 
    [cap_header_img_repeat] => no repeat
    [cap_header_img_x] => left
    [cap_header_img_y] => 
    [cap_menue_disable_home] => 1
    [cap_menue_enable_community] => 1
    [cap_menu_x] => left
    [cap_bg_menu_style] => tab style
    [cap_menu_underline] => 
    [cap_menue_link_color] => 
    [cap_menue_link_color_current] => 
    [cap_bg_menue_link_color] => 
    [cap_bg_menu_img] => 
    [cap_bg_menu_img_repeat] => no repeat
    [cap_bg_menue_link_color_current] => 
    [cap_bg_menu_img_current] => 
    [cap_bg_menu_img_current_repeat] => no repeat
    [cap_bg_menue_link_color_hover] => 
    [cap_bg_menue_link_color_dd_hover] => 
    [cap_menu_corner_radius] => all rounded
    [cap_leftsidebar_width] => 225
    [cap_bg_leftsidebar_color] => 
    [cap_bg_leftsidebar_img] => 
    [cap_bg_leftsidebar_img_repeat] => no repeat
    [cap_bg_leftsidebar_default_nav] => yes
    [cap_rightsidebar_width] => 225
    [cap_bg_rightsidebar_color] => 
    [cap_bg_rightsidebar_img] => 
    [cap_bg_rightsidebar_img_repeat] => no repeat
    [cap_bg_widgettitle_style] => angled
    [cap_widgettitle_font_style] => Helvetica Neue, Helvetica, Arial, sans-serif
    [cap_widgettitle_font_size] => 
    [cap_widgettitle_font_color] => 
    [cap_bg_widgettitle_color] => 
    [cap_bg_widgettitle_img] => 
    [cap_bg_widgettitle_img_repeat] => no repeat
    [cap_capitalize_widgets_li] => no
    [cap_capitalize_widgettitles] => no
    [cap_footer_width] => default
    [cap_footerall_height] => 
    [cap_bg_footerall_color] => 
    [cap_bg_footerall_img] => 
    [cap_bg_footerall_img_repeat] => no repeat
    [cap_footer_height] => 
    [cap_bg_footer_color] => 
    [cap_bg_footer_img] => 
    [cap_bg_footer_img_repeat] => no repeat
    [cap_bp_avatar] => 
    [cap_bp_login_bar_top] => on
    [cap_bp_default_navigation] => 1
    [cap_bg_content_nav_color] => 
    [cap_menue_enable_search] => 1
    [cap_buddydev_search] => 1
    [cap_searchbar_x] => right
    [cap_searchbar_y] => 
    [cap_login_sidebar] => on
    [cap_bp_login_sidebar_text] => 
    [cap_bp_profile_header] => on
    [cap_bp_profile_sidebars] => default
    [cap_bp_profiles_avatar_size] => 
    [cap_bp_profiles_nav_order] => 
    [cap_bp_groups_header] => on
    [cap_bp_groups_sidebars] => default
    [cap_bp_groups_avatar_size] => 
    [cap_bp_groups_nav_order] => 
    [cap_enable_slideshow_home] => home
    [cap_slideshow_img] => 
    [cap_slideshow_small_img] => 
    [cap_slideshow_cat] => 
    [cap_slideshow_amount] => 
    [cap_slideshow_post_type] => 
    [cap_slideshow_show_page] => 
    [cap_slideshow_time] => 
    [cap_slideshow_orderby] => 
    [cap_slideshow_style] => default
    [cap_slideshow_caption] => on
    [cap_slideshow_shadow] => sharper shadow
    [cap_slideshow_direct_links] => no
    [cap_overwrite_css] => 
    [cap_general_min_role] => Administrator
    [cap_header_min_role] => Administrator
    [cap_menu_min_role] => Administrator
    [cap_sidebars_min_role] => Administrator
    [cap_footer_min_role] => Administrator
    [cap_buddypress_min_role] => Administrator
    [cap_profile_min_role] => Administrator
    [cap_groups_min_role] => Administrator
    [cap_slideshow_min_role] => Administrator
    [cap_overwrite_min_role] => Administrator
    [cap_roles_and_capabilities_min_role] => Administrator
)
*/