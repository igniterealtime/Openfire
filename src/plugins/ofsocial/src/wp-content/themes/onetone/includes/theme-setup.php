<?php


function onetone_setup(){
	global $content_width;
	$lang = get_template_directory(). '/languages';
	load_theme_textdomain('onetone', $lang);
	add_theme_support( 'post-thumbnails' ); 
	$args = array();
	$header_args = array( 
	    'default-image'          => '',
		 'default-repeat' => 'repeat',
        'default-text-color'     => 'CC9966',
        'width'                  => 1120,
        'height'                 => 80,
        'flex-height'            => true
     );
	add_theme_support( 'custom-background', $args );
	add_theme_support( 'custom-header', $header_args );
	add_theme_support( 'automatic-feed-links' );
	add_theme_support('nav_menus');
	add_theme_support( "title-tag" );
	register_nav_menus( array('primary' => __( 'Primary Menu', 'onetone' )));
	register_nav_menus( array('home_menu' => __( 'Home Page Header Menu', 'onetone' )));
	add_editor_style("editor-style.css");
	if ( ! isset( $content_width ) ) $content_width = 1120;
	
}

add_action( 'after_setup_theme', 'onetone_setup' );


 function onetone_custom_scripts(){
	 
    $theme_info = wp_get_theme();
    wp_enqueue_style('onetone-font-awesome',  get_template_directory_uri() .'/css/font-awesome.min.css', false, '4.0.3', false);
	wp_enqueue_style('onetone-owl-carousel',  get_template_directory_uri() .'/css/owl.carousel.css', false, '1.3.3', false);
	wp_enqueue_style('onetone-owl-theme',  get_template_directory_uri() .'/css/owl.theme.css', false, '1.3.3', false);
	wp_enqueue_style( 'onetone-main', get_stylesheet_uri(), array(), '1.4.3' );
	wp_enqueue_style('Yanone-Kaffeesatz', esc_url('//fonts.googleapis.com/css?family=Yanone+Kaffeesatz|Lustria|Raleway|Open+Sans:400,300'), false, '', false );
	
   $background_array  = onetone_options_array("page_background");
   $background        = onetone_get_background($background_array);
   $header_image      = get_header_image();
   $onetone_custom_css = "";
	if (isset($header_image) && ! empty( $header_image )) {
	$onetone_custom_css .= ".home-header{background:url(".$header_image. ") repeat;}\n";
	}
    if ( 'blank' != get_header_textcolor() && '' != get_header_textcolor() ){
     $header_color        =  ' color:#' . get_header_textcolor() . ';';
	 $onetone_custom_css .=  '.home-header,.site-name,.site-description{'.$header_color.'}';
		}
	$custom_css           =  onetone_options_array("custom_css");
	$onetone_custom_css  .=  '.site{'.$background.'}';
	
	$links_color = onetone_options_array( 'links_color');
	if($links_color == "" || $links_color == null)
	$links_color = "#963";
	
	$onetone_custom_css  .=  'a,.site-logo a:hover,.site-navigation a:hover,.widget a:hover,.entry-title a:hover,.entry-meta a:hover,.loop-pagination a:hover,.page_navi a:hover,.site-footer a:hover,.home-navigation > ul > li.current > a > span,.home-navigation li a:hover,.home-navigation li.current a,.home-footer a:hover,#back-to-top,#back-to-top span{color:'.$links_color.';}#back-to-top {border:1px solid '.$links_color.';}mark,ins,.widget #wp-calendar #today{background:'.$links_color.'; }::selection{background:'.$links_color.' !important;}::-moz-selection{background:'.$links_color.' !important;}';


	$top_menu_font_color = onetone_options_array( 'font_color');
	if($top_menu_font_color !="" && $top_menu_font_color!=null){
		$onetone_custom_css  .=  'header #menu-main > li > a span,header .top-nav > ul > li > a span{color:'.$top_menu_font_color.'}';
		}
	
	$onetone_custom_css  .=  $custom_css;
	
	wp_add_inline_style( 'onetone-main', $onetone_custom_css );
	if(is_home()){
	wp_enqueue_script( 'onetone-bigvideo', get_template_directory_uri().'/js/jquery.tubular.1.0.js', array( 'jquery' ), '1.0', true );
	}
	wp_enqueue_script( 'onetone-modernizr', get_template_directory_uri().'/js/modernizr.custom.js', array( 'jquery' ), '2.8.2 ', false );
	wp_enqueue_script( 'onetone-respond', get_template_directory_uri().'/js/respond.min.js', array( 'jquery' ), '1.4.2 ', false );
	wp_enqueue_script( 'onetone-scrollTo', get_template_directory_uri().'/js/jquery.scrollTo.js', array( 'jquery' ), '1.4.14 ', false );
	wp_enqueue_script( 'onetone-carousel', get_template_directory_uri().'/js/owl.carousel.js', array( 'jquery' ), '1.3.3 ', true );
	wp_enqueue_script( 'onetone-parallax', get_template_directory_uri().'/js/jquery.parallax-1.1.3.js', array( 'jquery' ), '1.1.3 ', true );
	wp_enqueue_script( 'onetone-default', get_template_directory_uri().'/js/onetone.js', array( 'jquery' ),$theme_info->get( 'Version' ), true );
	
	if ( is_singular() && comments_open() && get_option( 'thread_comments' ) ){wp_enqueue_script( 'comment-reply' );}
	
	$slide_time = onetone_options_array("slide_time");
	$slide_time = is_numeric($slide_time)?$slide_time:"5000";
	
	wp_localize_script( 'onetone-default', 'onetone_params', array(
			'ajaxurl'        => admin_url('admin-ajax.php'),
			'themeurl' => get_template_directory_uri(),
			'slideSpeed'  => $slide_time
		)  );
	
	}
	
	function onetone_admin_scripts(){
		$theme_info = wp_get_theme();
		wp_enqueue_script( 'onetone-modernizr', get_template_directory_uri().'/js/admin.js', array( 'jquery' ), $theme_info->get( 'Version' ), false );
		
		}
	

  add_action( 'wp_enqueue_scripts', 'onetone_custom_scripts' );
  add_action( 'admin_enqueue_scripts', 'onetone_admin_scripts' );



function onetone_of_get_options($default = false) {
	
	//$optionsframework_settings = get_option(ONETONE_OPTIONS_PREFIXED.'optionsframework');
	
	// Gets the unique option id
	//$option_name = $optionsframework_settings['id'];
	
	$option_name  = optionsframework_option_name();
	
	
	if ( get_option($option_name) ) {
		$options = get_option($option_name);
	}
	else{
		
		 $location = apply_filters( 'options_framework_location', array('includes/admin-options.php') );

	        if ( $optionsfile = locate_template( $location ) ) {
				
	            $maybe_options = require_once $optionsfile;
	            if ( is_array( $maybe_options ) ) {
					$options = $maybe_options;
	            } else if ( function_exists( 'optionsframework_options' ) ) {
					$options = optionsframework_options();
				}
	        }
	    $options = apply_filters( 'of_options', $options );
		$config  =  $options;
		foreach ( (array) $config as $option ) {
			if ( ! isset( $option['id'] ) ) {
				continue;
			}
			if ( ! isset( $option['std'] ) ) {
				continue;
			}
			if ( ! isset( $option['type'] ) ) {
				continue;
			}
				$output[$option['id']] = apply_filters( 'of_sanitize_' . $option['type'], $option['std'], $option );
		}
		$options = $output;
		
		
		}
		
	if ( isset($options) ) {
		return $options;
	} else {
		return $default;
	}
}


global $onetone_options;
$onetone_options = onetone_of_get_options();

function onetone_options_array($name){
	global $onetone_options;
	if(isset($onetone_options[$name]))
	return $onetone_options[$name];
	else
	return "";
}

/* 
 * This is an example of how to add custom scripts to the options panel.
 * This one shows/hides the an option when a checkbox is clicked.
 */

add_action('optionsframework_custom_scripts', 'onetone_optionsframework_custom_scripts');

function onetone_optionsframework_custom_scripts() { 

}

add_filter('options_framework_location','onetone_options_framework_location_override');

function onetone_options_framework_location_override() {
	return array('includes/admin-options.php');
}

function onetone_optionscheck_options_menu_params( $menu ) {
	
	$menu['page_title'] = __( 'Onetone Options', 'onetone');
	$menu['menu_title'] = __( 'Onetone Options', 'onetone');
	$menu['menu_slug'] = 'onetone-options';
	return $menu;
}

add_filter( 'optionsframework_menu', 'onetone_optionscheck_options_menu_params' );

function onetone_wp_title( $title, $sep ) {
	global $paged, $page;
	if ( is_feed() )
		return $title;

	// Add the site name.
	$title .= get_bloginfo( 'name' );

	// Add the site description for the home/front page.
	$site_description = get_bloginfo( 'description', 'display' );
	if ( $site_description && ( is_home() || is_front_page() ) )
		$title = "$title $sep $site_description";

	// Add a page number if necessary.
	if ( $paged >= 2 || $page >= 2 )
		$title = "$title $sep " . sprintf( __( ' Page %s ', 'onetone' ), max( $paged, $page ) );

	return $title;
}
add_filter( 'wp_title', 'onetone_wp_title', 10, 2 );


function onetone_title( $title ) {
if ( $title == '' ) {
  return 'Untitled';
  } else {
  return $title;
  }
}
add_filter( 'the_title', 'onetone_title' );