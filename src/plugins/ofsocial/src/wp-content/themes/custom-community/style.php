<?php
/**
 * Additional Dynamic CSS Class
 * 
 * A part of the CSS will be created dynamically and then loaded in the header.
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 * 
 */

class cc2_CustomStyle {
	/**
	 * Also see @link https://developer.mozilla.org/en-US/docs/Web/CSS/length
	 */
	var $allowed_css_units = array(
		'em', 'ex', 'rem', 'pt', 'px', '%', 'cm', 'mm', 'in', 'pc',
		'ch', 
		'vh', 'vw',
		'vmin', 'vmax',
	);
/**
 * 
 * Additional Dynamic CSS
 * 
 * A part of the CSS will be created dynamically
 * and then loaded in the header.
 * 
 * @author Konrad Sroka 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 * 
 */

	function cc_additional_dynamic_css() {
		// initial values
		$advanced_settings = get_option( 'cc2_advanced_settings', array() );
		$has_static_frontpage = ( get_option( 'show_on_front') == 'page' ? true : false );
		
 	
 	?>
 	<style type="text/css">	
		
	<?php /*
	.js.loading-stage body {
		display: none;
	}*/
	?>

	/** Le Container De Bootstrap **/

	<?php 
	// get theme mods
	$arrKnownContainerSizes = array(
		'small' => '750', 
		'medium' => '970', 
		'large' => '1170' 
	);
	$arrContainerWidth = get_theme_mod( 'bootstrap_container_width', false );
	
	
	foreach( $arrKnownContainerSizes as $strContainerSize => $iContainerDefaultWidth ) {
		// avoid nasty fuck-ups
		if( empty($arrContainerWidth[ $strContainerSize ] ) 
			|| trim($arrContainerWidth[ $strContainerSize ]) == $iContainerDefaultWidth . 'px'
			|| trim($arrContainerWidth[ $strContainerSize ]) == $iContainerDefaultWidth . ' px'
		) {
			$bootstrap_container_width[ $strContainerSize ] = false;
		} else {
			$bootstrap_container_width[ $strContainerSize ] = trim( $arrContainerWidth[ $strContainerSize ] );
		}
		
	}
	
	//echo '/*' . print_r( array( $arrContainerWidth, 'vs', $bootstrap_container_width ), true ) . ' */';
	
	
	/*$bootstrap_container_width['small'] = get_theme_mod( 'bootstrap_container_width[small]', false);
	$bootstrap_container_width['medium'] = get_theme_mod( 'bootstrap_container_width[medium]', false);
	$bootstrap_container_width['large'] = get_theme_mod( 'bootstrap_container_width[large]', false);*/
	
	
	
	
	// settings for: small / mobile screen => default: 750px
	if( $bootstrap_container_width['small'] !== false ) {  ?>
		
	@media (min-width: 768px) { 
		.container { width: <?php echo $bootstrap_container_width['small']; ?>  }
	}
	
	<?php } ?>

	<?php 
	// settings for: medium desktop => default: 992px
	if( $bootstrap_container_width['medium'] !== false ) { ?>

	@media (min-width: 992px) { 
		.container { width: <?php echo $bootstrap_container_width['medium']; ?> }
	}
	
	<?php } ?>
	
	<?php 
	// settings for: large desktop => default: 1170px
	
	if( $bootstrap_container_width['large'] !== false ) { ?>
	@media (min-width: 1200px) {
		.container { width:  <?php echo $bootstrap_container_width['large']; ?> }
	}
	
	<?php } 
	
	/**
	 * Quick fix for the dropdown hover menu (the JS version basically does the same, but cannot open sub-sub-menus)
	 * 
	 * NOTE: This option is intentionally located nearby the custom bootstrap container width .. in the case we actually want to make it more customizable (ie. integrate both with each other)
	 * 
	 * Also: .no-mobile option to avoid accidential misniks on tablet or phablet systems
	 * 
	 */
	if( !empty( $advanced_settings['load_hover_dropdown_css'] ) && $advanced_settings['load_hover_dropdown_css'] > 0 && is_int( $advanced_settings['load_hover_dropdown_css'] ) ) {
		$load_hover_dropdown_css_min_width = 768;
		if( $advanced_settings['load_hover_dropdown_css'] > 300 ) {
			$load_hover_dropdown_css_min_width = $advanced_settings['load_hover_dropdown_css'];
		}
	?>
	
	@media (min-width: <?php echo $load_hover_dropdown_css_min_width; ?>px) {
		html.no-mobile .dropdown:active > .dropdown-menu,
		html.no-mobile .dropdown:hover > .dropdown-menu,
		.dropdown-submenu:active > .dropdown-menu, 
		.dropdown-submenu:hover > .dropdown-menu {
			display: block;
		}
	}
	
	<?php
	}
	 

	
	?>
	

    /** Header **/

	<?php

	/** 
	 * hide site title if header image is set (cause it fucks up the layout!)
	 * NOTE: get_header_image() is ALSO null = empty if the header image is "hidden" in the customizer
	 */

	// formerly: get_header_image() != ''
	$show_header_image = false;
	$header_image = get_header_image();
	
	if( !empty( $header_image ) ) {
		$show_header_image = true;
	}

	?>
	
	.site-header .cc-header-image {
	<?php
	if( !empty( $show_header_image) ) { ?>
		display: block;
	<?php } else { ?>
		display: none;
	<?php } ?>
	}
	
	
	<?php


	// site title!
	//$show_site_title = ( get_theme_mod( 'display_headertext');
	$show_site_title = true;
	
	?>
	
	
	.site-branding {
		
	<?php if( !empty( $show_site_title ) ) { ?>
		display: block;
	<?php } else { ?>
		display: none;	
	<?php } ?>

	}

<?php
	
	/**
	 * Header base settings
	 */
    
    
    // Header Background Color.
    
    /*
    echo '/*debug: Header Background Color: '
		. print_r( get_theme_mod( 'header_background_color' ), true )
		. '*' . '/';*/
    
	
	$arrHeaderBaseStyle = array();
    
    if( get_theme_mod( 'header_background_color', false ) != false ) {
		$arrHeaderBaseStyle[] = 'background-color: #' . get_theme_mod( 'header_background_color' );
	}
	
	// Header Background Image
    if( get_theme_mod( 'header_background_image', false ) != false ) {
		$arrHeaderBaseStyle[] = 'background-image: url("' . get_theme_mod( 'header_background_image' ) . '")';
	}
	
	// Header Height
	if( get_theme_mod( 'header_height', false ) != false ) {
		$arrHeaderBaseStyle[] = 'height: ' . get_theme_mod('header_height' );
	}
	

    if( !empty( $arrHeaderBaseStyle ) ) {
		?>
		.site-header {
			<?php echo implode(";\n", $arrHeaderBaseStyle ); ?>;
		}
	<?php
	}
     
    
	/**
	 * Header display per page type
	 */
    
    // Show header on home (only if no static frontpage is set!)
    if( $has_static_frontpage != false ) { // blog != home

		// blog
		if( ! get_theme_mod( 'display_header_home', true) ) : ?>
		body.blog #masthead .cc-header-image {
			display: none; 
		}
		<?php
		else : // display header home = blog

			if( get_theme_mod('header_height_blog', false ) !== false ) : ?>
		
		body.blog #masthead {
			height: <?php echo get_theme_mod('header_height_blog' ); ?>;
		}
		<?php endif;
		endif; // display header home.end
		?>
		
		
		
	<?php 
	
		// home = static frontpage
		?>
		
		
			
	<?php if( get_theme_mod( 'display_header_static_frontpage', false ) != true ) { ?>
		body.home #masthead .cc-header-image {
			display: none;
		}
	<?php 
		} else {
			if( get_theme_mod('header_height_home', false ) !== false ) { ?>
		body.home #masthead {
			height: <?php echo get_theme_mod('header_height_home' ); ?>;
		}
		
		<?php }
		} // display static frontpage header.end 
		?>
		
		
	<?php 
	} else { // blog = home
	?>
	
		
	<?php
		if( !get_theme_mod( 'display_header_home', true ) ) { ?>
    
			body.home #masthead .cc-header-image {
				display: none;
			}
    <?php ?>
    
    <?php
		} else { // show header home
			if( get_theme_mod('header_height_home', false ) !== false ) { ?>
		
		body.home #masthead {
			height: <?php echo get_theme_mod('header_height_home' ); ?>;
		}
		<?php } ?>
		
		<?php 
		} /* show header home.end */ ?>
		
	}
		
	<?php
	}
	
	
    // Show header on posts?
    if( !get_theme_mod( 'display_header_posts', true ) ) { ?>
        body.single #masthead .cc-header-image { 
			display: none; 
		}
    <?php }

    // Show header on pages?
    if( ! get_theme_mod( 'display_header_pages', true ) ) { ?>
        body.page #masthead .cc-header-image { 
			display: none; 
		}
    <?php }

    // Show header on archive?
    if( ! get_theme_mod( 'display_header_archive', true ) ) { ?>
        body.archive #masthead .cc-header-image,
        body.blog.paged #masthead .cc-header-image { 
			display: none; 
		}
    <?php }

    // Show header on search?
    if( ! get_theme_mod( 'display_header_search', true ) ) { ?>
        body.search #masthead .cc-header-image { display: none; }
    <?php }

    // Show header on 404?
    if( ! get_theme_mod( 'display_header_404', true ) ) { ?>
        body.error404 #masthead .cc-header-image { display: none; }
    <?php }	
   
	/**
	 * WooCommerce Support
	 * 
	 * @since 2.0.25
	 * @package cc2
	 * @author Fabian Wolf
	 */
	if( class_exists( 'WooCommerce' ) ) :
		if( ! get_theme_mod( 'wc_display_header_products', true ) ) : ?>
		body.archive.woocommerce  #masthead .cc-header-image, 
		body.post-type-archive-product.woocommerce #masthead .cc-header-image {
			display: none;
		}	
	<?php
		
		endif;
		
		if( ! get_theme_mod( 'wc_display_header_single_product', true ) ) : ?>
		
		body.single-product  #masthead .cc-header-image,
		body.single.woocommerce #masthead .cc-header-image,
		body.single.woocommerce-page #masthead .cc-header-image {
			display: none;
		}
		
		
	<?php	
		endif;
	endif;
	?>


    /** Navigation **/

	<?php
	/**
	 * NOTE: Quick bugfix for the mobile menu jumping bug; its cause is a completely ignored CSS priority of button:hover over .navbar-toggle:hover
	 */
	?>
	nav button.navbar-toggle {
		border-radius: 4px 4px 4px 4px;
		float: right;
		margin-bottom: 15px;
		margin-right: 15px;
		margin-top: 15px;
		padding: 9px 10px;
		position: relative;
	}



    <?php // Nav Branding Font Family according to Site Title Font Family
	// prepare data
		if( get_theme_mod( 'site_title_font_family', '' ) !== '' ) {
			$strNavbarBrandFontTitle = get_theme_mod( 'site_title_font_family' );
		}
	// use title font family

	// text color aka "font color"
	if( get_theme_mod('top_nav_brand_text_color', false ) !== false ) {
		$arrTopNavBrandAttributes['color'] = trim( get_theme_mod('top_nav_brand_text_color') );
		
		if( substr( $arrTopNavBrandAttributes['color'], 0, 1) != '#' ) { // add rgba-support later on
			$arrTopNavBrandAttributes['color'] = '#' . $arrTopNavBrandAttributes['color'];
		}
	}
	/*
	if( get_theme_mod('top_nav_brand_image', false ) !== false ) {
		$arrTopNavBrandAttributes['']
		
	}*/
    
    // display if given
    if( '' !== get_theme_mod( 'site_title_font_family' ) ) { ?>
		.navbar-brand {
			font-family: <?php echo get_theme_mod( 'site_title_font_family' ); ?>;
        }
    <?php } ?>
    

	<?php  // top nav actions
	
	if( isset( $arrTopNavBrandAttributes ) ) { ?>
		

	<?php } ?>



    <?php if( has_nav_menu('top') && !has_nav_menu('secondary') ) { ?>
        .site-navigation {
            clip: 1px, 1px, 1px, 1px;
            position: absolute;
        }
        
        
    <?php } ?>

    <?php // Remove margin from top nav if set
    if( true === has_nav_menu( 'top' ) ) { ?>
        .site-navigation-top.navbar {
            margin-bottom: 0;
        }
    <?php }

    // correcting navbar fixed top if admin bar is displaying
     //if( is_admin_bar_showing() ) :
		$header_height = get_theme_mod('header_height', 'auto' );
		if( $header_height == 'auto' || empty( $header_height) ) {
			$strHeaderHeight = '50px';
		} elseif( is_int( $header_height ) != false ) {
			$strHeaderHeight = $header_height . 'px';
		} elseif( strlen( str_replace( $this->allowed_css_units, '', $header_height ) ) < strlen( $header_height) != false )  {
			$strHeaderHeight = $header_height;
		}
		
		if( get_theme_mod( 'fixed_top_nav', false ) !== false ) : ?>
     
		body.admin-bar nav.site-navigation-top.navbar-fixed-top {
			top: 32px !important;
		}
   
		
		body.admin-bar .site-header {
			
			margin-top: <?php echo $strHeaderHeight; ?>;
		}
		 
		@media screen and (max-width: 782px) { <?php // that's the breakpoint where the admin bar gets bigger.. ?>
			body.admin-bar nav.site-navigation-top.navbar-fixed-top {
				top: 46px !important;
			}
        }
        
        
        
        <?php else : 
        
        /*
         * NOTE: Seems to be obsolete - or broken from the begin
         * 
        body.logged-in.admin-bar .site-navigation-top {
			margin-top: 32px !important;
		}
		*/
		?>
		 @media screen and (max-width: 782px) { <?php // that's the breakpoint where the admin bar gets bigger.. ?>
            body.logged-in.admin-bar .site-navigation-top {
                margin-top: 46px !important;
            }
        }

        
        <?php endif; ?>
        
        
    <?php 
    //endif;

    // Correct the <body> padding if the menu is fixed to top
    if( true === get_theme_mod( 'fixed_top_nav' ) && true === has_nav_menu( 'top' ) ) { ?>
        body {
            margin-top: 64px;
        }
    <?php } ?>

	<?php
	/** Basic coloring of the top navbar */
	
	if( ( $top_nav_background_color = get_theme_mod('top_nav_background_color', false) ) != false ) {
		?>
		.site-navigation-top {
			background: <?php echo ( $top_nav_background_color != 'transparent' ? '#' : '' ) . $top_nav_background_color; ?>;
		}
	<?php
	}
	
	if( ( $top_nav_text_color = get_theme_mod('top_nav_text_color', false) ) != false ) {
		?>
		.site-navigation-top .navbar-nav a,
		.site-navigation-top .navbar-nav a:link,
		.site-navigation-top .navbar-nav a:visited
		{
			color: <?php echo ( $top_nav_text_color != 'transparent' ? '#' : '' ) . $top_nav_text_color; ?>;
		}
	<?php
	}
	
	if( ( $top_nav_hover_text_color = get_theme_mod('top_nav_hover_text_color', false) ) != false ) {
		?>
	
		
		.site-navigation-top .navbar-nav > li > a:hover,
		.site-navigation-top .navbar-nav > li > a:active,
		.site-navigation-top .navbar-nav > li > a:focus
		{
			color: <?php echo ( $top_nav_hover_text_color != 'transparent' ? '#' : '' ) . $top_nav_hover_text_color; ?>;
		}
	<?php
	}
	
	/** 
	 * Basic coloring of the secondary navbar (below the slideshow)
	 */
	
	if( ( $secondary_nav_background_color = get_theme_mod('secondary_nav_background_color', false) ) != false ) {
		?>
		.site-navigation .navbar {
			background: <?php echo ( $secondary_nav_background_color != 'transparent' ? '#' : '' ) . $secondary_nav_background_color; ?>;
		}
	<?php
	}
	
	if( ( $secondary_nav_text_color = get_theme_mod('secondary_nav_text_color', false) ) != false ) {
		?>
		.site-navigation .navbar-nav a,
		.site-navigation .navbar-nav a:link,
		.site-navigation .navbar-nav a:visited
		{
			color: <?php echo ( $secondary_nav_text_color != 'transparent' ? '#' : '' ) . $secondary_nav_text_color; ?>;
		}
	<?php
	}
	
	if( ( $secondary_nav_hover_text_color = get_theme_mod('secondary_nav_hover_text_color', false) ) != false ) {
		?>

		.site-navigation .navbar-nav > li > a:hover,
		.site-navigation .navbar-nav > li > a:active,
		.site-navigation .navbar-nav > li > a:focus
		{
			color: <?php echo ( $secondary_nav_hover_text_color != 'transparent' ? '#' : '' ) . $secondary_nav_hover_text_color; ?>;
		}
	<?php
	}
	
	// navigation positions
	//$top_nav_position = get_theme_mod('top_nav_position', 'left' );

	?>


		


    /** Typography **/

    <?php // Titles h1-h6 ?>
    h1, h2, h3, h4, h5, h6 {
        margin-top: 30px;
        <?php if( get_theme_mod( 'title_font_family' ) ) ?>
            font-family: <?php echo get_theme_mod( 'title_font_family' ).';'; ?>

        <?php if( get_theme_mod( 'title_font_weight' ) ): ?>
            font-weight: bold;
        <?php else: ?>
            font-weight: normal;
        <?php endif; ?>

        <?php if( get_theme_mod( 'title_font_style' ) ): ?>
            font-style: italic;
        <?php else: ?>
            font-style: normal;
        <?php endif; ?>

        <?php if( '' !== get_theme_mod('title_font_color') ) ?>
            color: #<?php echo get_theme_mod('title_font_color') ?>;

        <?php if( '' !== get_theme_mod('title_font_family') || 'Helvetica Neue' !== get_theme_mod('title_font_family') ) // Helvetica Neue is Bootstrap's Default Font anyway ?>
            font-family: <?php echo get_theme_mod('title_font_family') ?>;
    }

    <?php // Change Title Font Sizes - only for displays above 767px ?>
    @media screen and (min-width: 768px) {
        <?php // Change H1 Font Size if set
        if( get_theme_mod('h1_font_size') != '' ) { ?>
            h1 {
                font-size: <?php echo get_theme_mod('h1_font_size') ?>;
            }
        <?php } ?>

        <?php // Change H2 Font Size if set
        if( get_theme_mod('h2_font_size') != '' ) { ?>
            h2 {
                font-size: <?php echo get_theme_mod('h2_font_size') ?>;
            }
        <?php } ?>

        <?php // Change H3 Font Size if set
         if( get_theme_mod('h3_font_size') != '' ) { ?>
            h3 {
                font-size: <?php echo get_theme_mod('h3_font_size') ?>;
            }
        <?php } ?>

        <?php // Change H4 Font Size if set
        if( get_theme_mod('h4_font_size') != '' ) { ?>
            h4 {
                font-size: <?php echo get_theme_mod('h4_font_size') ?>;
            }
        <?php } ?>

        <?php // Change H5 Font Size if set
        if( get_theme_mod('h5_font_size') != '' ) { ?>
            h5 {
                font-size: <?php echo get_theme_mod('h5_font_size') ?>;
            }
        <?php } ?>

        <?php // Change H6 Font Size if set
        if( get_theme_mod('h6_font_size') != '' ) { ?>
            h6 {
                font-size: <?php echo get_theme_mod('h6_font_size') ?>;
            }
        <?php } ?>
    }

	<?php // Underline Links.
	switch( get_theme_mod('link_underline') ) {

		case 'never': ?>
			a, a:hover { text-decoration: none; } <?php 
			break;
		
		case 'always': ?>
			a, a:hover { text-decoration: underline; } <?php 
			break;
		
		case 'just when normal': ?>		
			a { text-decoration: underline; } 
			a:hover { text-decoration: none; } <?php 
			break;
		
		// case 'just for mouse over' doesn't need any extra style ;-) 
		
	} ?>

	/** Content: Center titles */
	
	<?php $center_title = get_theme_mod( 'center_title', array() ); 
	if( !empty( $center_title ) ) {
		if( $center_title['global'] != 1 ) { // individual settings
			if( $center_title['home'] == 1) { ?>
		
			body.home .page-title {
				text-align: center;
			}
			
			<?php
			}
			
			if( $center_title['pages'] == 1) { ?>
		
			body.page .page-title {
				text-align: center;
			}
			
			<?php
			}
			
			if( $center_title['posts'] == 1) { ?>
		
			body.single .page-title {
				text-align: center;
			}
			
			<?php
			}
			
			
			if( $center_title['archive'] == 1) { ?>
		
			body.archive .page-title {
				text-align: center;
			}
			
			<?php
			}
			
			if( $center_title['search'] == 1) { ?>
		
			body.search .page-title {
				text-align: center;
			}
			
			<?php
			}
			
			if( $center_title['error'] == 1) { ?>
		
			body.error404 .page-title {
				text-align: center;
			}
			
			<?php
			}
		
		} else { // global setting!
?>
			.page-title  {
				text-align: center;
			}
<?php
			
		}
		
		
		
	} ?>
	

	/** Widgets */
	
	<?php
	/**
	 * widget_title_text_color, widget_title_background_color, widget_title_font_size,
	 */
	$widget_title_props = '';
	
	// prepare property variable for widget title
	if( get_theme_mod('widget_title_text_color', false) != false ) {
		$widget_title_props .= 'color: #' . get_theme_mod( 'widget_title_text_color' ) . '; ';
	}
	
	if( get_theme_mod('widget_title_background_color', false) != false ) {
		$widget_title_props .= 'background: #' . get_theme_mod( 'widget_title_background_color' ) . '; ';
	}
	
	if( get_theme_mod('widget_title_font_size', false) != false ) {
		$widget_title_props .= 'font-size: ' . get_theme_mod( 'widget_title_font_size' ) . '; ';
	}
	
	// output css
	if( !empty( $widget_title_props ) ) {
	?>
		.widgettitle,
		.widget-title
		{
			<?php echo apply_filters( 'cc2_style_widget_title', $widget_title_props ); ?>
		}
	<?php
	}
	
	/**
	 * widget_background_color, widget_link_color, widget_link_text_hover_color
	 */
	 
	// widget background color
	if( get_theme_mod( 'widget_background_color', false ) != false ) {
	?>
		.widgetarea,
		.widget {	
			background: #<?php echo get_theme_mod('widget_background_color'); ?>;
		}
	<?php
	}
	
	
	// widget link color (LoVe)
	if( get_theme_mod( 'widget_link_color', false ) != false ) {
	?>
		.widgetarea a:link,
		.widgetarea a:visited,
		.widget a:link,
		.widget a:visited {	
			color: #<?php echo get_theme_mod('widget_link_color'); ?>;
		}
	<?php
	}
	
	// widget link hover color (HAteS)
	if( get_theme_mod( 'widget_link_text_hover_color', false ) != false ) {
	?>
		
		.widgetarea a:hover,
		.widgetarea a:active,
		.widgetarea a:focus,
		.widget a:hover,
		.widget a:active,
		.widget a:focus {	
			color: #<?php echo get_theme_mod('widget_link_text_hover_color'); ?>;
		}
	<?php
	}

	
	
	/*.sidebar .widget_meta , .sidebar .widget_recent_entries ul, .sidebar .widget_archive ul, .sidebar .widget_categories ul, .sidebar .widget_nav_menu ul, .sidebar .widget_pages ul*/
	

	?>

	/** The CC Slider - Dynamic CSS Additions **/

    <?php // the slider width  ?>
    .cc-slider .cc-slider-wrap {
        width: <?php echo get_theme_mod( 'cc_slider_width' ); ?>;
    }
    <?php // the slider max height ?>
    .cc-slider .carousel,
    .cc-slider .carousel-inner > .item {
        max-height: <?php echo get_theme_mod( 'cc_slider_height' ); ?>;
    }

    <?php // slider nav backgrounds ?>
	.cc-slider .cc-slider-secret-wrap:hover .carousel-control {
		background: <?php echo '#'.get_theme_mod('link_color'); ?>;
	}
    .cc-slider .carousel-control:hover {
        background: <?php echo '#'.get_theme_mod('hover_color'); ?>;
    }
    <?php // caption, title, excerpt ?>
	.cc-slider .carousel-caption h1 {
		margin-bottom: 12px;
		font-size: 18px;
		clear: both;
		color: #<?php echo get_theme_mod('caption_title_font_color'); ?>;
		text-align: <?php echo get_theme_mod( 'cc_slider_text_align' ); ?>;
		<?php // check if text shadow is set 
		if( false === get_theme_mod('caption_text_shadow') ) { ?>
			text-shadow: none;
		<?php } ?>
		<?php // check if a special font family is selected  
		if( get_theme_mod('caption_title_font_family') != '' ) { ?>
			font-family: <?php echo get_theme_mod('caption_title_font_family'); ?>;
		<?php } ?>
		<?php // check if font weight should be bold 
		if( true === get_theme_mod('caption_title_font_weight') ) { ?>
			font-weight: bold;
		<?php } ?>
		<?php // check if font style should be italic
		if( true === get_theme_mod('caption_title_font_style') ) { ?>
			font-style: italic;
		<?php } ?>
		/**
		Slider effect: <?php echo get_theme_mod('slider_effect_title'); ?>
		*/
		
		<?php if( 'no-effect' !== get_theme_mod( 'slider_effect_title' ) ) { ?>
			display: none;
		<?php } ?>
	}
	.cc-slider-excerpt {
		<?php if( 'hide' === get_theme_mod( 'slider_effect_excerpt' ) ) { ?>
			display: none;
		<?php } else { ?>				
			display: block;
		<?php } ?>
		width: 100%;
		min-height: 40px;
	}
	.col-12 .cc-slider-excerpt,
	.md-col-12 .cc-slider-excerpt {
		min-height: 40px;
	}
	.cc-slider .carousel-caption p {
		font-size: 11px;
		margin: 0;
		color: #<?php echo get_theme_mod('caption_text_font_color'); ?>;
		text-align: <?php echo get_theme_mod( 'cc_slider_text_align' ); ?>;
		<?php // check if text shadow is set 
		if( false === get_theme_mod('caption_text_shadow') ) { ?>
			text-shadow: none;
		<?php } ?>
		<?php // check if a special font family is selected  
		if( get_theme_mod('caption_text_font_family') != '' ) { ?>
			font-family: <?php echo get_theme_mod('caption_text_font_family'); ?>;
		<?php } ?>
		<?php // check if font weight should be bold 
		if( true === get_theme_mod('caption_text_font_weight') ) { ?>
			font-weight: bold;
		<?php } ?>
		<?php // check if font style should be italic
		if( true === get_theme_mod('caption_text_font_style') ) { ?>
			font-style: italic;
		<?php } ?>		
		<?php if( 'no-effect' !== get_theme_mod( 'slider_effect_excerpt' ) ) { ?>
			display: none;
		<?php } ?>
	}
	.cc-slider .textwrap {
		padding: 7px 10px; 
	}
	.cc-slider h1 .textwrap {
		<?php // check opacity
		if( '' != get_theme_mod('caption_title_opacity') ) { ?>
			opacity: <?php echo get_theme_mod('caption_title_opacity'); ?>;
		<?php } ?>	
		<?php if( get_theme_mod('caption_title_bg_color') != '' ) { ?>
			background: #<?php echo get_theme_mod('caption_title_bg_color'); ?>;
		<?php } ?>
	}
	.cc-slider p .textwrap {
		<?php // check opacity
		if( '' != get_theme_mod('caption_text_opacity') ) { ?>
			opacity: <?php echo get_theme_mod('caption_text_opacity'); ?>;
		<?php } ?>	
		<?php if( get_theme_mod('caption_text_bg_color') != '' ) { ?>
			background: #<?php echo get_theme_mod('caption_text_bg_color'); ?>;
		<?php } ?>
	}	
	@media screen and (min-width: 480px) { 
		.cc-slider .carousel-caption h1, 
		.cc-slider .col-12 .carousel-caption h1 {
			font-size: 22px;
		}
		.cc-slider .carousel-caption p, 
		.cc-slider .col-12 .carousel-caption p {
			font-size: 14px;
		}
	}
	@media screen and (min-width: 768px) { 
		.cc-slider .carousel-caption h1 {
			font-size: 32px;
		}
		.cc-slider-excerpt {
			min-height: 55px;
		}
		.cc-slider .carousel-caption p {
			font-size: 17px;
		}
		.cc-slider .carousel-indicators {
			bottom: 15px;
		}
	}

	/* Rudimentary support for Threaded Comments */
	
	.comment ul.children {
		list-style-type: none;
	}

	/* Quick styling for avatar image (if enabled) */
	
	.post .entry-meta-author {
		margin-right: 15px;
		margin-top: -10px;
		margin-left: 5px;
	}
	
	<?php /** NOTE: Fixes issues with the post header size (overlaps with the bottom border etc.); a gravatar image usually is 60x60. */ ?>	
	.has-author-avatar .page-header,
	.has-author-avatar .page-header .entry-meta {
		min-height: 60px; 
	}
	
	.has-author-avatar .page-title {
		margin-bottom: 20px;
	}
	

	/* Footer Fullwidth */
	
	<?php
	// compile background color or image
	
	
	$footer_fullwidth_background = get_theme_mod( 'footer_fullwidth_background_color', 'eee' ); // defaults to #eee
	
	if( $footer_fullwidth_background != 'transparent' ) {
		$footer_fullwidth_background = '#' . $footer_fullwidth_background;
	}
	
	if( $footer_fullwidth_background != 'transparent' && get_theme_mod( 'footer_fullwidth_background_image', false ) != false ) { // replace background color ..
		$footer_fullwidth_background = 'url(' . get_theme_mod('footer_fullwidth_background_image') . ')';
	}
	
	
	// compile border color
	$footer_fullwidth_border_top = '1px solid #'. get_theme_mod( 'footer_fullwidth_border_top_color', 'ddd' );
	?>
	
	#footer-fullwidth-wrap {
		padding: 0 0 20px 0;
		background: <?php echo $footer_fullwidth_background; ?>;		
		border-top: <?php echo apply_filters('cc2_footer_fullwidth_border_top', $footer_fullwidth_border_top ); ?>;
	}

	/* Footer Columns */
	
	#footer-columns-wrap {
		padding: 20px 0;
		background: #2a2c2f;	
	}
	
		/* Footer Columns Fonts */
	
		.footer-columns, 
		.footer-columns p {
			color: #8c8c8c;
		}
		.footer-columns a {
			color: #c3c3c3;
		}
		.footer-columns a:hover {
			color: #8c8c8c;
		}
	
	
@media (min-width: 992px) { 
	.footer-columns .widgetarea {
		
		min-height: 300px;
		overflow: auto;
		
		/* this will be packed into some options.. ;) 
			background: #2a2a2a;
			padding: 10px 20px;
			margin-top: 10px;
			margin-bottom: 10px;
		*/
	}
	
}
	.footer-columns .table tr > th, 
	.footer-columns .table tr > td {
		border-top: 1px solid #3f3f3f; 
	}
	.footer-columns .table tr {
		border-bottom: none; 
	}
	.footer-columns .table-striped tr:nth-child(odd) > td {
		background-color: #262626;
	}

	/* Footer Branding */
	
	#branding {
		padding: 20px 0;
		border-top: 1px solid #333;
		background: #040c14;
		color: #6a6a6a;
	}
	#branding p {
		line-height: 100%; 
		margin: 0;
		color: #6a6a6a;
	}
	#branding a {
		color: #9a9a9a;
	} 
	#branding a:hover, 
	#branding a:focus {
		color: #6a6a6a;
		text-decoration: none;
	}

	/* Scroll-to-top button Styling */
	<?php
	$top_link_container_height = apply_filters('cc2_scroll_top_link_container_height', 18 ); // px
	$top_link_container_offset = apply_filters('cc2_scroll_top_link_container_offset', $top_link_container_height );
	
	if( true === get_theme_mod( 'fixed_top_nav' ) && true === has_nav_menu( 'top' ) ) {
		//$top_link_container_offset += 64; // negative of the offset - height of link element
		$top_link_container_offset += 64;
		
	}
	
	// check if wp_admin_bar is visible
	if( is_admin_bar_showing() != false ) {
		$top_link_container_offset += 32; // wp_admin_bar / toolbar has a height of 32px
	}
	
	
	$top_link_container_offset = $top_link_container_offset * -1;
	
	// could also be left ..
	$top_link_container_position = apply_filters( 'cc2_scroll_top_link_container_position', 'right: 10px;' );
	
	?>
	
	.top-link-container.affix-top {
		position: absolute; /* allows it to "slide" up into view */
		
		bottom: <?php echo $top_link_container_offset; ?>px; /* negative of the offset - height of link element */
		
		/* padding from the left side of the window */
		<?php echo $top_link_container_position; ?>
		
	}
	
	.top-link-container.affix {
		position: fixed; /* keeps it on the bottom once in view */
		bottom: <?php echo $top_link_container_height; ?>px; /* height of link element */
		<?php echo $top_link_container_position; ?>
	}
	
	
	.top-link-container .top-link-button-text {
		margin-left: 5px;
	}
	
	/* Hide link text on small devices */
	@media max-width 767px{
		.top-link-container .top-link-button-text {
			display: none;
		}
	}


<?php 
		/**
		 * Action hook for adding additional CSS _inside_ the style tag
		 * 
		 * @hook cc2_additional_dynamic_css_before_close_tag
		 * @uses custom_header_style() Public static method for styling the header
		 */
		do_action('cc2_additional_dynamic_css_before_close_tag'); ?>

</style>

<?php
		/**
		 * Action hook for adding additional CSS _after_ the style tag
		 * 
		 * @hook cc2_additional_dynamic_css_after_close_tag
		 */
		do_action('cc2_additional_dynamic_css_after_close_tag'); ?>

		
		<?php // interim fix for header text color option // will be reworked
		//cc2_custom_header_style();
		?>

		<?php
		/**
		 * Action hook for adding additional CSS before _the very end_ of this function call
		 * 
		 * @hook cc2_additional_dynamic_css_before_end
		 * @uses custom_styling() 	Advanced Settings: Custom CSS tab - fires at the very end to always override ANYTHING that happened before!
		 */
		do_action('cc2_additional_dynamic_css_before_end');
			
	} // end.function
	
	/**
	 * Replaces _tk_header_custom_style()
	 */
	public static function custom_header_style() {
		$header_text_color = get_header_textcolor();
		
		// Has the text been hidden?
		if ( 'blank' == $header_text_color ) { ?>
		.site-branding,
        .site-title,
		.site-description {
			position: absolute;
			clip: rect(1px, 1px, 1px, 1px);
		}
		<?php
		// If the user has set a custom color for the text use that
		} else {

			
		
		} // 'blank' == $header_text_color
	?>

    <?php 	// Site Title Font Family ?>
			.site-title a {
		<?php if( !empty( $header_text_color ) ) { ?>
				color: #<?php echo $header_text_color; ?>;
		<?php } ?>
				
		<?php if( get_theme_mod( 'site_title_font_family', false ) != false && 'inherit' !== get_theme_mod( 'site_title_font_family' ) ) { ?>
				font-family: <?php echo get_theme_mod( 'site_title_font_family' ); ?>;
		<?php } ?>
				text-shadow: 1px 1px 1px rgba(0,0,0,.3);
			}

	<?php 
		if( $header_text_color != 'blank' ) {
			
			if ( get_theme_mod( 'header_text_hover_color', false ) != false ) { ?>
			
			.site-title a:hover {
				color: #<?php echo get_theme_mod( 'header_text_hover_color' ); ?>;
			}
			
<?php 		}
		} ?>

    <?php // Site Title Position
		if( get_theme_mod( 'site_title_position', false ) != false && 'left' !== get_theme_mod( 'site_title_position' ) ) { ?>
	.site-title, .site-description {
		text-align: <?php echo get_theme_mod( 'site_title_position' ); ?>;
	}
    <?php 
		} 
    
    
	/** Prepare Tagline Properties */
		$tagline_styling_props = '';
	
		if( get_theme_mod('tagline_font_family', false) != false ) {
			$tagline_styling_props .= 'font-family: ' . get_theme_mod('tagline_font_family') . ';';
		}
		
		if( get_theme_mod('tagline_text_color', false) != false ) {
			$tagline_styling_props .= 'color: #' . get_theme_mod('tagline_text_color') . ';';
		}
		
	
		if( !empty( $tagline_styling_props ) ) { ?>
		.site-description {
			<?php echo $tagline_styling_props; ?>
		}

	<?php }	
		
	}
	
	
	/**
	 * Advanced Settings: Custom CSS tab
	 * 
	 * @uses @hook 
	 */
	
	function custom_styling() {
	
		$custom_styling = get_option( 'cc2_advanced_settings', false );
	
		if( !empty( $custom_styling ) && isset ( $custom_styling['custom_css'] ) && !empty( $custom_styling['custom_css']) ) { ?>
			
		<!-- Custom CSS -->
		<style type="text/css">
			<?php echo apply_filters( 'output_advanced_settings_custom_css', $custom_styling['custom_css'] ); ?>
		</style>
		<!-- /Custom CSS -->
	<?php
		}	
		
	}
	/**
	 * Plugin instance.
	 *
	 * @see get_instance()
	 * @type object
	 */
	protected static $instance = NULL;
		
	/**
	 * Implements Factory pattern
	 * Strongly inspired by WP Maintenance Mode of Frank Bueltge ^_^ (@link https://github.com/bueltge/WP-Maintenance-Mode)
	 * 
	 * Access this plugins working instance
	 *
	 * @wp-hook after_setup_theme
	 * @return object of this class
	 */
	public static function get_instance() {

		NULL === self::$instance and self::$instance = new self;

		return self::$instance;
	}
	
	
	/**
	 * Class constructor - put at the end of the class for a better overview of which function calls have to be added to actions etc.
	 */
	
	
	function __construct() {
		
		add_action( 'wp_head', array( $this, 'cc_additional_dynamic_css') );
		
		add_action( 'cc2_additional_dynamic_css_before_close_tag', array( 'cc2_CustomStyle', 'custom_header_style' ) );
		add_action( 'cc2_additional_dynamic_css_before_end', array( $this, 'custom_styling' ) );
	}
	
} // end.class


add_action( 'after_setup_theme', array( 'cc2_CustomStyle', 'get_instance') );

// close tag intentionally left out
