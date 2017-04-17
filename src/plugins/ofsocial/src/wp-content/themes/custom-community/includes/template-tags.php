<?php

/* Here you can find all custom template tags for this theme. */  

/**
 * Load modified Simple HTML DOM class (similar to jQuery / querySelectorAll)
 * @see http://simplehtmldom.sourceforge.net/
 */
if( !class_exists('simple_html_dom') ) {
	include_once( get_template_directory() . '/includes/resources/simple_html_dom/simple_html_dom.wp.php' );
}


/**
 * Add the Header Image, if set
 * 
 * @hook cc_header_image, header.php
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 *
 */

add_action( 'cc_header_image', 'cc_add_header_image' );

function cc_add_header_image() {

	global $post;
	$header_image = get_header_image();
	$custom_header_image = get_custom_header();
	
	/*echo '<!-- debug: header_image ' . print_r( array('header_image' => $header_image, 'custom_header_image' => $custom_header_image ), true ). ' -->';*/

	if ( ! empty( $header_image ) ) { ?>
	<div class="cc-header-image">	
		<a class="cc-header-image-link" href="<?php echo esc_url( home_url( '/' ) ); ?>" title="<?php echo esc_attr( get_bloginfo( 'name', 'display' ) ); ?>" rel="home">
			<img src="<?php echo $header_image; ?>" width="<?php echo $custom_header_image->width; ?>" height="<?php echo $custom_header_image->height; ?>" alt="">
		</a>
	</div>	
	<?php 
	}
	
}


/**
 * Add the Top Navigation, if set
 * 
 * @hook cc_before_header, header.php
 * 
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 *
 */

add_action( 'cc_before_header', 'add_top_nav', 10 ); 
 
function add_top_nav() {


	// if top location doesn't have a menu -> do nothing
	if( !has_nav_menu('top') ) {
		return;
	}

    $advanced_settings = get_option( 'cc2_advanced_settings', array() );

    // maximum number of menu levels
	$max_depth = get_theme_mod('top_nav_max_depth', 0 );
	

	// fetch position settings
	$nav_position = get_theme_mod('top_nav_position', 'left' );
	$menu_class = 'nav navbar-nav';
	
	if( $nav_position == 'right' ) {
		$menu_class .= ' pull-right';
	} elseif( $nav_position == 'center' ) {
		$menu_class .= ' center-block'; // actually doesnt work, because else it'd pretty much chop up the bootstrap grid
	}
	
	// prepare params for the menu
	$menu_settings = array(
		'theme_location' => 'top',
		'container_class' => 'navbar-collapse collapse navbar-responsive-collapse',
		'menu_class' => $menu_class,
		'fallback_cb' => '',
		'menu_id' => 'top-menu',
	);
	
	if( !empty( $max_depth ) && is_int( $max_depth ) ) {
		$menu_settings['depth'] = $max_depth;
	}
	
	//$menu_settings['walker'] = new wp_bootstrap_navwalker( array('use_hrefs' => true) );

    $use_hrefs = isset($advanced_settings['load_hover_dropdown_css']) && $advanced_settings['load_hover_dropdown_css'] == true ? true : false;

	$menu_settings['walker'] = new wp_bootstrap_navwalker( array('use_hrefs' => $use_hrefs) );
	$menu_settings['fallback_cb'] = array( 'wp_bootstrap_navwalker', 'fallback' );

		
		/*<nav id="access" class="site-navigation-top navbar navbar-static-top <?php do_action( 'top_nav_class' ); ?>">*/
	?>
		<nav id="access" class="<?php echo apply_filters( 'top_nav_class', 'site-navigation-top navbar navbar-static-top' ); ?>">
		
			<div class="container">
				<div class="row">
					<div class="<?php if( $nav_position != 'center' ) { ?>md-col-12<?php } ?>">
					    <!-- The toggle button for collapsed navbar content -->
					    <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-responsive-collapse">
					      <span class="icon-bar"></span>
					      <span class="icon-bar"></span>
					      <span class="icon-bar"></span>
					    </button>

					    <!-- Nav Brand goes here, if enabled -->
					    <?php do_action( 'add_top_nav_brand' ); ?>

					    <!-- The WordPress Menu -->
						<?php wp_nav_menu( $menu_settings ); ?>
					</div>
				</div>
			</div>
		</nav><!-- .site-navigation-top --><?php
		
}
 
 
/**
 * Add the Default Navigation to Header Bottom
 * 
 * @hook cc_after_header, header.php
 * 
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 *
 */

add_action( 'cc_after_header', 'add_secondary_nav', 20 );
 
function add_secondary_nav() {


	// if no menu is set, do nothing.
	if( true !== has_nav_menu( 'secondary' ) )
		return; 
					
	// fetch position settings
	$nav_position = get_theme_mod('secondary_nav_position', 'left' );
	$menu_class = 'nav navbar-nav';
	
	if( $nav_position == 'right' ) {
		$menu_class .= ' pull-right';
	} elseif( $nav_position == 'center' ) {
		$menu_class .= ' center-block'; // actually does NOT work, because else it'd pretty much chop up the bootstrap grid
	}
	
	// else: add the menu ?>		
	
		<nav id="access-secondary" class="site-navigation">
			
			<div class="container">
				<div class="row">
					<div class="site-navigation-inner<?php if( $nav_position != 'center' ) { ?> md-col-12<?php } ?>">
						<div class="<?php echo apply_filters( 'bottom_nav_class', 'navbar' ); ?>">
						    <!-- .navbar-toggle is used as the toggle for collapsed navbar content -->
						    <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-responsive-collapse">
						      <span class="icon-bar"></span>
						      <span class="icon-bar"></span>
						      <span class="icon-bar"></span>
						    </button>
						
						    <!-- Your site title as branding in the menu, if set -->
						    <?php do_action( 'add_bottom_nav_brand' ); ?>
						    
						    <!-- The WordPress Menu goes here -->
						       <?php wp_nav_menu(
					                array(
					                    'theme_location' => 'secondary',
					                    'container_class' => 'navbar-collapse collapse',
					                    'menu_class' => $menu_class,
					                    'fallback_cb' => '',
					                    'menu_id' => 'main-menu',
					                    'walker' => new wp_bootstrap_navwalker()
					                )
					            ); ?>
						
						</div><!-- .navbar -->
					</div>
				</div>
			</div><!-- .container -->
		</nav><!-- .site-navigation --><?php

}

 
/**
 * Add a Branding to Top Navigation, if set
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 * @uses add_nav_brand()
 */
 
add_action( 'add_top_nav_brand', 'add_top_nav_brand' );

function add_top_nav_brand() {

	/*
	if( true === get_theme_mod( 'top_nav_brand' ) ) {
	
		$content = get_bloginfo('name');
		
		if( get_theme_mod('top_nav_brand_image', false ) != false ) {
			$content = '<img src="' . get_theme_mod('top_nav_brand_image' ) . '" alt="' . $content . '" />';
		}
		
		add_nav_brand( $content );
	}*/
	if( get_theme_mod( 'top_nav_brand', false ) != false ) {
		add_nav_brand();
	}
}


/**
 * Add a Branding to Bottom Navigation, if set
 * 
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 * @uses add_nav_brand()
 */
 
add_action( 'add_bottom_nav_brand', 'add_bottom_nav_brand' );

function add_bottom_nav_brand() {

	if( !has_nav_menu('top') && has_nav_menu('default') && get_theme_mod('bottom_nav_brand', false) != false ) {

	
	/*if( get_theme_mod( 'bottom_nav_brand', false ) != false ) {
		
		$content = get_bloginfo('name');
		
		if( get_theme_mod('bottom_nav_brand_image', false ) != false ) {
			$content = get_theme_mod('bottom_nav_brand_image' );
		}
		
		add_nav_brand( $content );
	}*/
	
		add_nav_brand( 'bottom' );
	}

}




/**
 * Echo the Branding (title or image) for Navigations
 * Used by add_top_nav_brand() and add_bottom_nav_brand()
 * 
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0.1
 * 
 */
function add_nav_brand( $position = 'top' ) {
	$strSiteName = get_bloginfo( 'name', 'display' );
	
	$content = get_theme_mod( $position . '_nav_brand_image', $strSiteName );
	
	// default content = title
	$content = '<img src="' . $content . '" alt="'.$content.'" />';

	// just echo the brand here.	
	$return = '<a class="navbar-brand hidden-xs" href="' . esc_url( home_url( '/' ) ) . '" title="'. esc_attr( $strSiteName ) .'" rel="home">' . $content . '</a>';

	echo $return;
}

/**
 * Rewrite of bottom nav class and top nav class AS A BLOODY FILTER.
 * 
 * @author Fabian Wolf
 * @since 2.0-rc1
 * @package cc2
 */
 



/**
 * Bottom Nav -> Use dark colors if set
 */
 
if( !function_exists( 'cc2_bottom_nav_class' ) ) :
	add_filter ( 'bottom_nav_class', 'cc2_bottom_nav_class' );

	function cc2_bottom_nav_class( $class = '' ) {
		$return = $class;
		
		// Bottom Nav in dark colors?
		$strAddClass = 'navbar-default'; // default: nope
		
		if( get_theme_mod( 'color_scheme_bottom_nav', false ) == 'dark' ) { // yep
			$strAddClass = 'navbar-inverse ';
		} 
		
		$return = cc2_htmlClass::addClass( $return, $strAddClass );
		
		return $return;		
	}
endif;

/**
 * Top Nav -> Use dark colors if set
 */


if( !function_exists( 'cc2_top_nav_class' ) ) :
	

	function cc2_top_nav_class( $class = '' ) {
		$return = $class;
		
		// Top Nav in dark colors?
		$strAddClass = 'navbar-default'; // default: nope
		
		if( get_theme_mod( 'color_scheme_top_nav', false ) == 'dark' ) { // yep
			$strAddClass = 'navbar-inverse';
		}
		$return = cc2_htmlClass::addClass( $return, $strAddClass );
		
		// Top Nav fixed to top? 
		$strAddClass = 'navbar-static-top'; // default: nope
		
		if( get_theme_mod( 'fixed_top_nav', false ) != false ) { // yep
			$strAddClass = 'navbar-fixed-top';
		}
		
		$return = cc2_htmlClass::addClass( $return, $strAddClass );
		
		//$return .= ' filtered-by-' . __FUNCTION__;
		return $return;
	}
	
	add_filter( 'top_nav_class', 'cc2_top_nav_class' );
endif;

/**
 * Add default branding to the footer
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0.11
 */

if( !function_exists( 'cc2_default_footer_branding' ) ) :
	function cc2_default_footer_branding() {
		$nofollow = '';
		
		if( !is_home() && !is_front_page() ) {
			$nofollow = ' rel="nofollow"';
		}

?>

		<p class="alignright">
			<small><a href="http://wordpress.org/" title="A Semantic Personal Publishing Platform">Proudly powered by WordPress</a>

			<span class="sep"> | </span>
			
			<a href="http://themekraft.com/store/custom-community-2-free-responsive-wordpress-bootstrap-theme/"<?php echo $nofollow; ?> title="<?php _e('WordPress Theme Custom Community 2', 'cc2'); ?>"><?php _e('WordPress Theme Custom Community 2', 'cc2'); ?></a> <?php _e('developed by ThemeKraft', 'cc2'); ?></small>
			
		</p>
		<!-- footer branding -->

<?php

	}
	
	if( get_theme_mod('footer_branding_show_credits', true ) != false ) :
		add_action('_tk_credits', 'cc2_default_footer_branding', 10 );
	endif;
endif;


/**
 * Check if both sidebars should be displayed in current view
 *
 * @author Konrad Sroka
 * @package cc2
 * @since 2.0
 *
 */

function cc2_display_both_sidebars() {
	$return = false;
	if( cc2_display_sidebar( 'left' ) && cc2_display_sidebar( 'right' ) ) {
		$return = true;
	}

	return $return;
}


/**
 * Test if a given sidebar may be _displayed_ in the current VIEW. Thou it doesn't test if this sidebar is actually active.
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0.12
 */

function cc2_has_sidebar( $location = 'right' ) {
	$current_view = '';
	$return = false;
	
	$default_layout = get_theme_mod('default_layout');
	
	if( is_page() ) {
		$current_view = 'page';
	}
	
	if( is_post() ) {
		$current_view = 'post';
	}
	
	if( is_archive() ) {
		$current_view = 'archive';
	}
	
	/**
	 * NOTE: Does this account for is_front_page as well?
	 */
	if( is_home() ) {
		$current_view = 'home';
	}
	
	if( is_front_page() ) {
		$current_view = 'front-page';
	}
	
	
	
	switch( $current_view ) {
		default:
			$layout_part = '';
		
			break;
		case 'post':
		case 'page':
		case 'archive':
			$layout_part = $current_view . '_';
			break;
		case 'home':
			$layout_part = 'archive_';
		
			break;
		case 'front-page':
			$layout_part = 'page_';
		
			break;
		case 'single':
			$layout_part = 'post_';
			break;
	}
	
	$current_layout = get_theme_mod( 'default_' . $layout_part . 'layout', false);
	
	if( $location == $current_layout || 'left-right' == $current_layout ) {
		$return = true;
	}
	
	return $return;
}


/**
 * Wrapper for _cc2_display_sidebar / cc2_has_sidebar
 */

function cc2_display_sidebar( $sidebar_location = 'right' ) {
	return _cc2_display_sidebar( $sidebar_location );
}


/**
 * Check if to display a sidebar in current view
 *
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 *
 */

function _cc2_display_sidebar( $side = 'right' ){

    if( is_page() ) {

        $page_layout = get_theme_mod( 'default_page_layout' );

        if( 'default' === $page_layout )
            $page_layout = get_theme_mod( 'default_layout' );

        if( $side === $page_layout || 'left-right' === $page_layout )
            return true;

        return false;

    }

    if( is_single() ) {

        $single_layout = get_theme_mod( 'default_post_layout' );

		if( class_exists( 'WooCommerce' ) ) {
			$single_layout = get_theme_mod( 'wc_layout_single_product', 'default' );
		}
		

        if( 'default' === $single_layout )
            $single_layout = get_theme_mod( 'default_layout' );

        //echo '<pre>'.get_theme_mod( 'default_post_layout' ).'</pre>';

        if( $side === $single_layout || 'left-right' === $single_layout )
            return true;

        return false;

    }

    if( is_archive() || is_home() ) {

        $archive_layout = get_theme_mod( 'default_archive_layout' );

		/**
		 * WooCommerce support
		 * NOTE: The products list is also an "archive"
		 */
		
		if( class_exists( 'WooCommerce' ) ) {
			$archive_layout = get_theme_mod( 'wc_layout_archive', 'default' );
		}

        if( 'default' === $archive_layout ) {
            $archive_layout = get_theme_mod( 'default_layout' );
		}

		if( $side === $archive_layout || 'left-right' === $archive_layout ) {
			return true;
		}

        return false;

    }

    $default_layout = get_theme_mod( 'default_layout' );

    if( $side === $default_layout || 'left-right' === $default_layout )
        return true;

    return false;

}

/**
 * Small helper for submit buttons - uses the PROPER element button, NOT input! tsk ..
 * Mostly an improved copy of @see get_submit_button()
 *
 * 
 * @author Fabian Wolf
 * @since 2.0-b3
 * @package cc2
 */
if( !function_exists( 'proper_submit_button' ) ) :
	function proper_submit_button( $text = '', $type = 'primary large', $name = 'submit', $wrap = true, $other_attributes = array(), $echo = true ) {
		if ( ! is_array( $type ) ) {
			$type = explode( ' ', $type );
		}

		$button_shorthand = array( 'primary', 'small', 'large' );
		$classes = array( 'button' );
		foreach ( $type as $t ) {
			if ( 'secondary' === $t || 'button-secondary' === $t )
				continue;
			$classes[] = in_array( $t, $button_shorthand ) ? 'button-' . $t : $t;
		}
		$class = implode( ' ', array_unique( $classes ) );

		if ( 'delete' === $type ) {
			$class = 'button-secondary delete';
		}

		$text = $text ? $text : __( 'Save Changes' );

		// Default the id attribute to $name unless an id was specifically provided in $other_attributes
		$id = $name;
		if ( is_array( $other_attributes ) && isset( $other_attributes['id'] ) ) {
			$id = $other_attributes['id'];
			unset( $other_attributes['id'] );
		}
		
		// Default button type
		$button_type = 'submit';
		if( is_array( $other_attributes ) && isset( $other_attributes['type'] ) ) {
			$button_type = $other_attributes['type'];
			unset( $other_attributes['type'] );
		}
		
		$button_icon = '';
		$attributes = '';
		if ( is_array( $other_attributes ) ) {
			foreach ( $other_attributes as $attribute => $value ) {
				$attributes .= $attribute . '="';
				
				if( $attribute == 'href' ) {
					$attributes .= esc_url( $value );
				} elseif( $attribute == 'icon' && !empty( $value ) ) {
					$button_icon = '<i class="' . esc_attr( $value ) . '"></i> ';
					
				} else {
				
					 $attributes .= esc_attr( $value );
					
				}
				
				$attributes .= '" '; // Trailing space is important
			}
		} else if ( !empty( $other_attributes ) ) { // Attributes provided as a string
			$attributes = $other_attributes;
		}



		if( in_array( 'link', $type ) != false ) {
			$button = '<a id="' . esc_attr( $id ) . '" class="' . esc_attr( $class ) . '" ' . trim($attributes) . '>' . $button_icon . esc_attr( $text ) . '</a>';
		} else {
			$button = '<button type="' . esc_attr( $button_type ) . '" name="' . esc_attr( $name ) . '" id="' . esc_attr( $id ) . '" class="' . esc_attr( $class ) . '" '
					. trim($attributes) . '>' . $button_icon . esc_attr( $text ) 
					. '</button>';

		}
		
		/*$button = '<input type="submit" name="' . esc_attr( $name ) . '" id="' . esc_attr( $id ) . '" class="' . esc_attr( $class );
		$button	.= '" value="' . esc_attr( $text ) . '" ' . $attributes . ' />';*/

		if ( $wrap ) {
			$button = '<p class="submit">' . $button . '</p>';
		}

		if( $echo != false ) {
			echo $button;
			return;
		}

		return $button;
	}


endif;




/**
 * Adding some extra Bootstrap CSS classes to the sidebars - IF they should hide on small devices
 *
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 *
 */

// one for the left...
if( true === get_theme_mod( 'hide_left_sidebar_on_phones' ) ) :
    //add_action( 'cc_sidebar_left_class', 'cc2_hide_on_small_devices' );
	add_filter( 'cc_sidebar_left_class', 'cc2_hide_on_small_devices' );
endif;

// and one for the right sidebar...
if( true === get_theme_mod( 'hide_right_sidebar_on_phones' ) ) :
	//add_action( 'cc_sidebar_right_class', 'cc2_hide_on_small_devices' );
	add_filter( 'cc_sidebar_right_class', 'cc2_hide_on_small_devices' );
endif;

if ( ! function_exists( 'cc2_hide_on_small_devices' ) ) :

	function cc2_hide_on_small_devices( $class = '' ) {
		$return = $class;
		
		if( !empty( $return ) ) {
			$return .= ' ';
		}
		
		$return .= 'hidden-xs hidden-sm ';
		
		return $return;
	}
	
endif;


/**
 * Adding the needed Bootstrap CSS classes to the sidebars.
 * Filter hooks are used for more flexibility; also, adding a simple little helper to avoid huge ternary comparision constructs.
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 *
 */

if( !function_exists( 'maybe_switch_value' ) ) :

	/**
	 * Little helper to avoid large ternary comparison constructs
	 * Also @see http://davidwalsh.name/php-shorthand-if-else-ternary-operators
	 */
	
	function maybe_switch_value( $current_value, $new_value = 0 ) {
		$return = $current_value;
		
		//new __debug( array( $current_value, 'type' => gettype( $current_value ), $new_value, 'other_type' => gettype( $new_value )), 'values' );
		// fix accidential stringification
		if( !is_int($current_value ) && is_numeric( $current_value) && ( '' +  intval( $current_value) == $current_value) ) {
			$current_value = intval( $current_value );
		}
		
		if( !is_int($new_value ) && is_numeric( $new_value) && ( '' +  intval( $new_value) == $new_value) ) {
			$new_value = intval( $new_value );
		}
		
		
	
		
		if( is_int( $current_value ) && is_int( $new_value ) && $return != $new_value )  {
			
			$return = $new_value;
		}
			
		return $return;
	}
	

endif;


if ( ! function_exists( 'cc2_add_sidebar_class' ) ) :


	// one for the left...
	//add_action( 'cc_sidebar_left_class', 'cc2_add_sidebar_class' );
	add_filter( 'cc_sidebar_left_class', 'cc2_add_sidebar_class', 1 );

	// and one for the right sidebar...
	//add_action( 'cc_sidebar_right_class', 'cc2_add_sidebar_class' );
	add_filter( 'cc_sidebar_right_class','cc2_add_sidebar_class', 2 );

	

	function cc2_add_sidebar_class( $class = '') {
		global $post;
		$cols = ' col-md-4 col-lg-4 ';
		
		$default_sidebar_cols = array(
			'left' => 4,
			'right' => 4,
			'left-right' => 3,
		);

		// get customized col numbers
		$custom_sidebar_cols = get_theme_mod( 'bootstrap_custom_sidebar_cols' );


		//new __debug( $custom_sidebar_cols, 'sidebar cols settings' );
		
		// first check if its a page or post or archive
		if( is_page() ) {
			$layout_mode = get_theme_mod( 'default_page_layout' );

			if( 'default' === $layout_mode ) {
				$layout_mode = get_theme_mod( 'default_layout' );
			}
			

			switch ( $layout_mode ) {
				case 'left':
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left'] );
					break;
				case 'right':
					//$sidebar_cols = 4;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right'] );
					break;
				case 'left-right':
					//$cols = ' col-md-3 col-lg-3 ';
					//$sidebar_cols = 3;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left-right'], $custom_sidebar_cols['left-right'] );
					
					break;
			}

		} elseif( is_single() ) {

			$layout_mode = get_theme_mod( 'default_post_layout' );

			if( 'default' === $layout_mode ) {
				$layout_mode = get_theme_mod( 'default_layout' );
			}
			
			switch ( $layout_mode ) {
				case 'left':
						$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left'] );
					break;
				case 'right':
					//$sidebar_cols = 4;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right'] );
					break;
				case 'left-right':
					//$cols = ' col-md-3 col-lg-3 ';
					//$sidebar_cols = 3;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left-right'], $custom_sidebar_cols['left-right'] );
					
					break;
			}

		} elseif( is_archive() || is_home() ) {

			$layout_mode = get_theme_mod( 'default_archive_layout' );

			if( 'default' === $layout_mode ) {
				$layout_mode = get_theme_mod( 'default_layout' );
			}

			switch ( $layout_mode ) {
				case 'left':
						$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left'] );
					break;
				case 'right':
					//$sidebar_cols = 4;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right'] );
					break;
				case 'left-right':
					//$cols = ' col-md-3 col-lg-3 ';
					//$sidebar_cols = 3;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left-right'], $custom_sidebar_cols['left-right'] );
					
					break;
			}

		// if nothing of the above, check the default layout:
		} else {

			switch ( get_theme_mod('default_layout') ) {
				case 'left':
						$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left'] );
					break;
				case 'right':
					//$sidebar_cols = 4;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right'] );
					break;
				case 'left-right':
					//$cols = ' col-md-3 col-lg-3 ';
					//$sidebar_cols = 3;
					$sidebar_cols = maybe_switch_value( $default_sidebar_cols['left-right'], $custom_sidebar_cols['left-right'] );
					break;
				case 'fullwidth':
					//$cols = '';
					$sidebar_cols = 0; // maybe FALSE might be better? ie. !sidebar_cols etc.
					break;
			}

		}
		
		/**
		 * Customize the sidebar column number (with SANE numbers!)
		 */
		
		if( $sidebar_cols > 0 && $sidebar_cols < 13 ) {
			$cols = ' col-md-' . $sidebar_cols . ' col-lg-' . $sidebar_cols;
		} elseif( $sidebar_cols == 0 ) {
			$cols = '';
		}
		
		// compile it all and return the result
		$return = $class . $cols;

		return $return;
	}

endif;



/**
 * Automatically adjust the Bootstrap CSS classes for the content container
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 * 
 */
 
add_filter( 'cc2_content_class', 'cc2_add_content_class' );

if ( ! function_exists( 'cc2_add_content_class' ) ) :

function cc2_add_content_class( $class = '' ) {
	global $post;
    $cols = ' col-md-8 col-lg-8 ';
    $content_cols = 8;
    
    
    
	$default_sidebar_cols = array(
		'left' => 4,
		'right' => 4,
		'left-right' => 3,
	);
	
	//$default_content_cols = array(
		//'left' => 8,
		//'right' => 8,
		//'left-right' => 6,
	//);

	// get customized col numbers
	$custom_sidebar_cols = get_theme_mod( 'bootstrap_custom_sidebar_cols' );




    // first check if its a page or post or archive
	if( is_page() ) {

        $page_layout = get_theme_mod( 'default_page_layout' );

        if( 'default' === $page_layout )
            $page_layout = get_theme_mod( 'default_layout' );

        switch ( $page_layout ) {
            case 'left':
				$content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
                
                break;
            case 'right':
                $content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
				break;
			case 'left-right':
				$content_cols = 6;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']) + maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 6 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
				break;
			case 'fullwidth':
				$content_cols = 12;
				break;
        }

	} elseif( is_single() ) {

        $single_layout = get_theme_mod( 'default_post_layout' );

		if( class_exists( 'WooCommerce' ) ) {
			$single_layout = get_theme_mod( 'wc_layout_single_product', 'default' );
		}
		
        if( 'default' === $single_layout )
            $single_layout = get_theme_mod( 'default_layout' );

        switch ( $single_layout ) {
             case 'left':
				$content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
                
                break;
            case 'right':
                $content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
				break;
			case 'left-right':
				$content_cols = 6;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']) + maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 6 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
			
				break;
			case 'fullwidth':
				$content_cols = 12;
				break;
        }

	} elseif( is_archive() || is_home() ) {

        $archive_layout = get_theme_mod( 'default_archive_layout' );

		if( class_exists( 'WooCommerce' ) ) {
			$archive_layout = get_theme_mod( 'wc_layout_archive', 'default' );
		}
		

        if( 'default' === $archive_layout )
            $archive_layout = get_theme_mod( 'default_layout' );

        switch ( $archive_layout ) {
            case 'left':
				$content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
                
                break;
            case 'right':
                $content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
				break;
			case 'left-right':
				$content_cols = 6;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']) + maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 6 ) {
					$content_cols = 12 - $current_sidebar_cols;
				}
				break;
			case 'fullwidth':
				$content_cols = 12;
				break;
        }

	// if nothing of the above, check the default layout:
	} else {

        switch ( get_theme_mod('default_layout') ) {
             case 'left':
				$content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $content_cols;
				}
                
                break;
            case 'right':
                $content_cols = 8;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 4 ) {
					$content_cols = 12 - $content_cols;
				}
				break;
			case 'left-right':
				$content_cols = 6;
            
				$current_sidebar_cols = maybe_switch_value( $default_sidebar_cols['left'], $custom_sidebar_cols['left']) + maybe_switch_value( $default_sidebar_cols['right'], $custom_sidebar_cols['right']);
				if( $current_sidebar_cols != 6 ) {
					$content_cols = 12 - $content_cols;
				}
				break;
			case 'fullwidth':
				$content_cols = 12;
				break;
        }

    }

	// compile class
	$arrReturn = ( is_array($class) ? $class : explode(' ', $class ) );
	$arrReturn[] = 'col-md-' . $content_cols;
	$arrReturn[] = 'col-lg-' . $content_cols;

	// return it to the wolf it was torn ..
	$return = implode(' ', $arrReturn );	

    echo $return;

}

endif; // cc2_add_content_class() exists


/**
 * Add optional display switch to the comment form
 * NOTE: Although the Bootstrap documentation indicated, that nothing but the form tag may use the .form-(something) classes, it actually works with a simple div WRAPPING the form itself as well ;)
 * Tested with Firebug in the official docs: @see http://getbootstrap.com/css/#forms-horizontal
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 * 
 */

if( !function_exists( 'cc2_bootstrap_comment_form_before' ) ) :
	function cc2_bootstrap_comment_form_before() {
		if( get_theme_mod( 'cc2_comment_form_orientation', 'horizontal' ) == 'horizontal' ) {
			echo '<div class="form-horizontal">';
		}
	}

	add_action( 'comment_form_before', 'cc2_bootstrap_comment_form_before' );
	//add_action( 'comment_form_before_fields', 'cc2_bootstrap_comment_form_before' );

endif;

if( !function_exists( 'cc2_bootstrap_comment_form_after' ) ) :
	function cc2_bootstrap_comment_form_after() {
		if( get_theme_mod( 'cc2_comment_form_orientation', 'horizontal' ) == 'horizontal' ) {
			echo '</div>';
		}
	}
	
	add_action( 'comment_form_after', 'cc2_bootstrap_comment_form_after' );
	//add_action( 'comment_form_after_fields', 'cc2_bootstrap_comment_form_after' );
endif;

/**
 * Add Bootstrap support to the comment form fields
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 */

// helper class
if( !class_exists( 'cc2_htmlClass' ) ) :
	class cc2_htmlClass {
		public static function addClass( $class, $add_class = null, $return_type = 'string' ) {
			$return = $class;
			
			if( !empty( $class ) && !empty( $add_class ) ) {
			
				$arrClass = self::_prepare_array( $class );
				$arrAddClass = self::_prepare_array( $add_class );
				
				$arrClass = array_unique( array_merge( $arrClass, $arrAddClass ) );
				
				if( $return_type == 'string' ) {
					$return = trim( implode(' ', $arrClass ) );
				} else {
					$return = $arrClass;
				}
			}
		
			return $return;
		}
		
		public static function removeClass( $class, $remove_class = null, $return_type = 'string' ) {
			$return = $class;
			
			
			// cant remove something that isnt there
			if( !empty( $class ) && !empty( $remove_class) ) { 
				
				$arrClass = self::_prepare_array( $class );
				$arrRemoveClass = self::_prepare_array( $remove_class );
				$strHaystack = implode(' || ', $arrClass ); $strHaystackBefore = $strHaystack;
				
				foreach( $arrRemoveClass as $strRemoveClassPart ) {
					//if( in_array( $strRemoveClassPart, $arrClass ) ) {
					if( strpos( $strHaystack, $strRemoveClassPart ) !== false ) {
						unset( $arrClass[ array_search( $strRemoveClassPart, $arrClass ) ] );
						
						// update string counterpart
						$strHaystack = str_replace( array($strRemoveClassPart, ' ||  || ' ), array('', ' || ' ), $strHaystack );
					}
				}
				
			
				if( $return_type == 'string' ) {
					$return = trim( implode(' ', $arrClass ) );
				} else {
					$return = $arrClass;
				}
			}
			
			return $return;
		}
		
		/**
		 * Replace single or multiple classes within the given class string
		 * 
		 * @param string $haystack		HTML class string to parse.
		 * @param mixed $search			String or array containing the value(s) to search for.
		 * @param mixed $replace		String or array which replace(s) the above given value(s).
		 * @param string $return_type	Return parsed data either as array or string. Defaults to 'string'.
		 * 
		 * @return mixed $result		
		 */
		public static function replaceClass( $class, $search = null, $replace = null, $case_sensitive = true, $return_type = 'string' ) {
			$return = $class;
			
			// if you want to REMOVE classes, use the bloody proper method call!
			if( !empty( $class ) && !empty( $search ) && !empty( $replace ) ) {
				$arrClass = self::_prepare_array( $class );
				$arrSearch = self::_prepare_array( $search );
				$arrReplace = self::_prepare_array( $replace );
				
				/**
				 * We're using a trick: @see http://php.net/str_replace#100871
				 */
				
				$strHaystack = implode(' || ', $arrClass ); $strHaystackBefore = trim($strHaystack);
				
				foreach( $arrSearch as $strNeedle ) {
					/**
					 * TODO: Add support for multiple needles!
					 */
					if( $case_sensitive != false ) { // case sensitive => HOME-page != home-page
						if( strpos( $strHaystack, $strNeedle ) !== false ) {
							//self::_search_replace_array( $arrClass, 
							$strHaystack = str_replace( $strNeedle, $arrReplace, $strHaystack );
						}
					} else { // ignore case, ie. HOME-page == home-page
						if( stripos( $strHaystack, $strNeedle ) !== false ) {
							$strHaystack = str_ireplace( $strNeedle, $arrReplace, $strHaystack );
						}
					}
				}
				
				// compare haystack ...
				if( $strHaystackBefore != trim( $strHaystack ) ) {
					// .. and convert it back to array if it has changed
					
					$arrClass = explode(' || ', $strHaystack );
				}
				
				
				if( $return_type == 'string' ) {
					$return = trim( implode(' ', $arrClass ) );
				} else {
					$return = $arrClass;
				}
				
			}
			
			return $return;
		}
		
		public static function _search_replace_array( $haystack, $search, $replace ) {
			$return = $haystack;
			
			return $return;
		}
		
		public static function _prepare_array( $class ) {
			$return = $class;
			
			if( is_string( $class ) && strpos( $class, ' ') !== false  ) {
				$return = explode(' ', $class );
			} elseif( is_array( $class ) ) {
				$return = $class;
			} elseif( empty( $class ) != false ) {
				$return = array();
			} else {
				$return = array( $class );
			}
			
			return $return;
		}
	}

endif;


// main function

if( !function_exists( 'cc2_bootstrap_comment_form_fields' ) ):
	function cc2_bootstrap_comment_form_fields( $arrFields = array() ) {
		$return = $arrFields;
		$is_horizontal = ( get_theme_mod( 'cc2_comment_form_orientation', 'horizontal' ) == 'horizontal' ? true : false );
		
		//new __debug( $arrFields, 'arrFields' );
		// mostly for reference
		$aria_req = ' aria-required="true" ';
		$commenter = array('comment_author' => '', 'comment_author_email' => '', 'comment_author_url' => '' );
		
		// mostly for reference
		$arrDefaultFields = array(
			'author' =>
				'<p class="comment-form-author">' .
				'<label for="author">' . __( 'Name', 'domainreference' ) . '</label> ' .
				( !empty($req) ? '<span class="required">*</span>' : '' ) .
				'<input id="author" name="author" type="text" value="' . esc_attr( $commenter['comment_author'] ) .
				'" size="30"' . $aria_req . ' /></p>',

			'email' =>
				'<p class="comment-form-email"><label for="email">' . __( 'Email', 'domainreference' ) . '</label> ' .
				( !empty($req) ? '<span class="required">*</span>' : '' ) .
				'<input id="email" name="email" type="text" value="' . esc_attr(  $commenter['comment_author_email'] ) .
				'" size="30"' . $aria_req . ' /></p>',

			'url' =>
				'<p class="comment-form-url"><label for="url">' .
				__( 'Website', 'domainreference' ) . '</label>' .
				'<input id="url" name="url" type="text" value="' . esc_attr( $commenter['comment_author_url'] ) .
				'" size="30" /></p>'
		);
		
		if( class_exists('simple_html_dom' ) ) { // avoid nasty errors if out of some unknown reason the simple_html_dom class failed to be included
		
			$dom = new simple_html_dom();
			
			foreach( $arrDefaultFields as $strFieldName => $strHTML ) { // use the default fields ONLY as reference, NOT for actual parsing!
				
				if( isset( $return[ $strFieldName ] ) != false ) {
					// reset variables
					$strEditedHTML = '';
					
					// load snippets
					$dom->load( $return[ $strFieldName ] );
					
					// find input tag
					$elem = $dom->find('input#' . $strFieldName, 0);
					
					// add class if not already set
					$elem->class = cc2_htmlClass::addClass( $elem->class, 'form-control' );
					
					/**
					 * Wrap element if horizontal form is enabled
					 * Also see @http://simplehtmldom.sourceforge.net/manual.htm > How to access the HTML element's attributes? > Tips
					 */
					if( $is_horizontal != false )  {
						
						// find label ...
						$label_elem = $dom->find('label', 0 );
						
						// .. and add class
						$label_elem->class = cc2_htmlClass::addClass( $label_elem->class, array('col-md-2', 'col-lg-2', 'control-label') );
						
						// wrap parent element
						$elem->parent()->outertext = '<div class="col-md-10 col-lg-10">' . $elem->parent()->outertext . '</div>';
						
					}
					
				
					
					
					// return edited data
					$strEditedHTML = $dom->save();
					
					
					// Optionally group field 			
					if( $is_horizontal != false ) {
						$strEditedHTML = sprintf( '<div class="form-group">%s</div>', $strEditedHTML );
					
					}
					
					if( !empty( $strEditedHTML) ) {
						$return[ $strFieldName ] = $strEditedHTML;
					}
					
					// uncomment the following line for testing purposes (ie. to see whether this was ACTUALLY passed throught the filter or not)
					//$return[ $strFieldName ] .= '<!-- parsed field: ' . $strFieldName . ' -->';
				}
			}
		}
	

		return $return;
	}

	add_filter('comment_form_default_fields', 'cc2_bootstrap_comment_form_fields' );
endif;


if( ! function_exists( 'cc2_pagination' ) ) :
/**
 * Display navigation to next/previous pages when applicable
 * 
 * NOTE: Rewrite of resurrected original function from 1.99 / early alpha releases
 */

function cc2_pagination( $arrParams = array() ) {
	global $wp_query;
	static $tk_index_count = 1;

	$return = '';
	$echo = true;
	
	$strAttrID = 'cc2-pagination' . $tk_index_count;
	$tk_index_count++;
	
	

    if(function_exists('wp_pagenavi')) {

        ob_start();
        wp_pagenavi( array( 'query' => $wp_query ) );
        $cc_tmp_wp_pagenavi = ob_get_clean();
        $cc_tmp_wp_pagenavi = str_replace('class=\'wp-pagenavi\'', 'id="' . $strAttrID . '" class="wp-pagenavi navigation"', $cc_tmp_wp_pagenavi);
        $return = $cc_tmp_wp_pagenavi;

    } else {
		// prepare parameters
		
		$arrParams = wp_parse_args( array(
			'use_pagination' => true,
			'echo' => true,
			'center_pagination' => true,
			'center_pager' => false,
		), $arrParams );
		
		extract( $arrParams );
		
		// get current page
		
		$current_page = ( get_query_var( 'paged' ) ) ? get_query_var( 'paged' ) : 1;
		if( is_front_page() ) {
			$current_page = ( get_query_var( 'page' ) ) ? get_query_var( 'page' ) : 1;
		}

		// prepare links
		
		$arrPostsLink['next'] = get_next_posts_link( __('<span class="arrow-left">&laquo;</span><span class="nav-next-text"> Older Entries</span>', 'cc2'), $wp_query->max_num_pages );
		
		$arrPostsLink['prev'] = get_previous_posts_link( __('<span class="nav-prev-text">Newer Entries </span><span class="arrow-right">&raquo;</span>', 'cc2') );

		// prepare templates

		$arrTemplate['open'] = apply_filters('cc2_pagination_template_wrapper_open', '<div id="%s" class="%s"><ul class="%s">' );
		$arrTemplate['next_prev'] = apply_filters('cc2_pagination_template_prev_next', '<li class="%s">%s</li>' );
		$arrTemplate['paging'] = apply_filters('cc2_pagination_template_paging', '<li class="%s">%s</li>' );
		
		$arrTemplate['close'] = apply_filters('cc2_paginatation_template_wrapper_close', '</ul></div><!-- End paging navigation -->' );


		if( $wp_query->max_num_pages > 1 ) {
			
			$arrPages = paginate_links( array('total' => $wp_query->max_num_pages, 'current' => $current_page, 'prev_next' => false, 'type' => 'array' ) );
			
			
			if( !empty( $arrPages ) ) {
				foreach( $arrPages as $strPageLink ) {
					$strPageLinkClass = 'pagination-nav';
					$strCurrentPageLink = $strPageLink;
					
					if( stripos( $strPageLink, 'page-numbers current' ) !== false ) {
						$strPageLinkClass = 'active';
						$strCurrentPageLink = str_replace( array(' class=\'page-numbers current\'', ' class="page-numbers current"' ), '', $strPageLink );
					}
					
					$arrPaginationList[] = sprintf( $arrTemplate['paging'], $strPageLinkClass, $strCurrentPageLink );
				}

			}

		}
			
		// wrapper.open
			
		$strPagingContainerClass = 'navigation';
		$strPagingClass = 'pager';
		
		if( !empty( $arrPaginationList ) && !empty( $use_pagination ) ) {
			$strPagingClass = 'pagination';
		}
		
		if( !empty( $center_pagination ) ) {
			$strPagingContainerClass .= ' aligncenter';
		}
		
		$return = sprintf( $arrTemplate['open'], $strAttrID, $strPagingContainerClass, $strPagingClass );

		// add next
		if( !empty( $arrPostsLink['next'] ) ) {
			$strPostsLinkClassNext = 'nav-link-next';
			if( empty($center_pager) && $strPagingClass != 'pagination' ) {
				$strPostsLinkClassNext .= ' alignleft';
			}
			
			$return .= sprintf( $arrTemplate['next_prev'], $strPostsLinkClassNext, $arrPostsLink['next'] );
		}
		
		// add pagination
		if( $strPagingClass == 'pagination' ) { // easy, eh? ;-)
			$return .= implode("\n", $arrPaginationList );
		}
		
		// add prev
		if( !empty( $arrPostsLink['prev'] ) ) {
			$strPostsLinkClassPrev = 'nav-link-prev';
			if( empty($center_pager) && $strPagingClass != 'pagination' ) {
				$strPostsLinkClassPrev .= ' alignright';
			}
			
			$return .= sprintf( $arrTemplate['next_prev'], $strPostsLinkClassPrev, $arrPostsLink['prev'] );
		}
		
		// wrapper.close
		$return .= $arrTemplate['close'];
		

    }

	//new __debug( $return, 'return' );

	if( !empty( $echo ) ) {
		echo $return;
		return;
	}
	return $return;
}

add_action('cc2_have_posts_after_loop_front_page', 'cc2_pagination');
add_action('cc2_have_posts_after_loop_archive', 'cc2_pagination');
add_action('cc2_have_posts_after_loop', 'cc2_pagination');


if ( ! function_exists( '_tk_index_nav' ) ) :
/**
 * Wrapper function for proper backwars compatiblity
 */
function _tk_index_nav() {
	if( !function_exists('cc2_pagination' ) ) {
		return;
	}
	
	cc2_pagination();
}
 
 
endif;


/**
 * Display navigation to next/previous pages when applicable
 * 
 * FIXME: Seems to be simply copy + pasted work. Undefined variable $the_lp_query is being used. Commited by the former main authors of this theme? Added a quick global + isset() to the function. (@author Fabian Wolf)
 * 
 
function _tk_index_nav() {
	global $the_lp_query;
    $cc_tmp = '';

	if( isset( $the_lp_query ) ) {

		if(function_exists('wp_pagenavi') ) {

			ob_start();
			wp_pagenavi( array( 'query' => $the_lp_query ) );
			$cc_tmp_wp_pagenavi = ob_get_clean();
			$cc_tmp_wp_pagenavi = str_replace('class=\'wp-pagenavi\'', 'id="_index" class="wp-pagenavi navigation"', $cc_tmp_wp_pagenavi);
			$cc_tmp .= $cc_tmp_wp_pagenavi;

		} else {

			$cc_tmp .='<div id="_index" class="navigation">';
			$cc_tmp .='<div class="alignleft">'. get_next_posts_link('&laquo; Older Entries', $the_lp_query->max_num_pages ) .'</div>';
			$cc_tmp .='<div class="alignright">' . get_previous_posts_link('Newer Entries &raquo;') .'</div>';
			$cc_tmp .='</div><!-- End navigation -->';

		}
	}

	echo $cc_tmp;
}*/


endif; // _tk_index_nav

if ( ! function_exists( '_tk_content_nav' ) ) :
/**
 * Display navigation to next/previous pages when applicable
 */
function _tk_content_nav( $nav_id ) {
	global $wp_query, $post;

	// Don't print empty markup on single pages if there's nowhere to navigate.
	if ( is_single() ) {
		$previous = ( is_attachment() ) ? get_post( $post->post_parent ) : get_adjacent_post( false, '', true );
		$next = get_adjacent_post( false, '', false );

		if ( ! $next && ! $previous )
			return;
	}

	// Don't print empty markup in archives if there's only one page.
	if ( $wp_query->max_num_pages < 2 && ( is_home() || is_archive() || is_search() ) )
		return;

	$nav_class = ( is_single() ) ? 'post-navigation' : 'paging-navigation';

	?>
	<nav role="navigation" id="<?php echo esc_attr( $nav_id ); ?>" class="<?php echo $nav_class; ?>">
		<h1 class="screen-reader-text"><?php _e( 'Post navigation', '_tk' ); ?></h1>

	<?php if ( is_single() ) : // navigation links for single posts ?>

		<?php previous_post_link( '<div class="nav-previous">%link</div>', '<span class="meta-nav">' . _x( '&larr;', 'Previous post link', '_tk' ) . '</span> %title' ); ?>
		<?php next_post_link( '<div class="nav-next">%link</div>', '%title <span class="meta-nav">' . _x( '&rarr;', 'Next post link', '_tk' ) . '</span>' ); ?>

	<?php elseif ( $wp_query->max_num_pages > 1 && ( is_home() || is_archive() || is_search() ) ) : // navigation links for home, archive, and search pages ?>
		<h1>Yay for pagination!</h1>

		<?php if ( get_next_posts_link() ) : ?>
		<div class="nav-previous"><?php next_posts_link( __( '<span class="meta-nav">&larr;</span> Older posts', '_tk' ) ); ?></div>
		<?php endif; ?>

		<?php if ( get_previous_posts_link() ) : ?>
		<div class="nav-next"><?php previous_posts_link( __( 'Newer posts <span class="meta-nav">&rarr;</span>', '_tk' ) ); ?></div>
		<?php endif; ?>

	<?php endif; ?>

	</nav><!-- #<?php echo esc_html( $nav_id ); ?> -->
	<?php
}
endif; // _tk_content_nav() exists

if ( ! function_exists( '_tk_comment' ) ) :
/**
 * Template for comments and pingbacks.
 *
 * Used as a callback by wp_list_comments() for displaying the comments.
 */
function _tk_comment( $comment, $args, $depth ) {
	$GLOBALS['comment'] = $comment;

	if ( 'pingback' == $comment->comment_type || 'trackback' == $comment->comment_type ) : ?>

	<li id="comment-<?php comment_ID(); ?>" <?php comment_class( 'media' ); ?>>
		<div class="comment-body">
			<?php _e( 'Pingback:', '_tk' ); ?> <?php comment_author_link(); ?> <?php edit_comment_link( __( 'Edit', '_tk' ), '<span class="edit-link">', '</span>' ); ?>
		</div>

	<?php else : ?>

	<li id="comment-<?php comment_ID(); ?>" <?php comment_class( empty( $args['has_children'] ) ? '' : 'parent' ); ?>>
		<article id="div-comment-<?php comment_ID(); ?>" class="comment-body media">
			<a class="pull-left" href="#">
				<?php if ( 0 != $args['avatar_size'] ) echo get_avatar( $comment, $args['avatar_size'] ); ?>
			</a>
			
			<div class="media-body">
				<div class="media-body-wrap panel">
					
					<h5 class="media-heading"><?php printf( __( '%s <span class="says">says:</span>', '_tk' ), sprintf( '<cite class="fn">%s</cite>', get_comment_author_link() ) ); ?></h5>
					<p class="comment-meta">
						<a href="<?php echo esc_url( get_comment_link( $comment->comment_ID ) ); ?>">
							<time datetime="<?php comment_time( 'c' ); ?>">
								<?php printf( _x( '%1$s at %2$s', '1: date, 2: time', '_tk' ), get_comment_date(), get_comment_time() ); ?>
							</time>
						</a> 
						<?php edit_comment_link( __( '<span style="margin-left: 5px;" class="glyphicon glyphicon-edit"></span> Edit', '_tk' ), '<span class="edit-link">', '</span>' ); ?>
					</p>
	
					<?php if ( '0' == $comment->comment_approved ) : ?>
						<p class="comment-awaiting-moderation"><?php _e( 'Your comment is awaiting moderation.', '_tk' ); ?></p>
					<?php endif; ?>
					
					<div class="comment-content">
						<?php comment_text(); ?>
					</div><!-- .comment-content -->
										
					<footer class="reply comment-reply panel-footer">
						<?php comment_reply_link( array_merge( $args, array( 'add_below' => 'div-comment', 'depth' => $depth, 'max_depth' => $args['max_depth'] ) ) ); ?>
					</footer><!-- .reply -->

				</div>
			</div><!-- .media-body -->

		</article><!-- .comment-body -->

	<?php
	endif;
}
endif; // ends check for _tk_comment()

if ( ! function_exists( '_tk_the_attached_image' ) ) :
/**
 * Prints the attached image with a link to the next attached image.
 */
function _tk_the_attached_image() {
	$post                = get_post();
	$attachment_size     = apply_filters( '_tk_attachment_size', array( 1200, 1200 ) );
	$next_attachment_url = wp_get_attachment_url();

	/**
	 * Grab the IDs of all the image attachments in a gallery so we can get the
	 * URL of the next adjacent image in a gallery, or the first image (if
	 * we're looking at the last image in a gallery), or, in a gallery of one,
	 * just the link to that image file.
	 */
	$attachment_ids = get_posts( array(
		'post_parent'    => $post->post_parent,
		'fields'         => 'ids',
		'numberposts'    => -1,
		'post_status'    => 'inherit',
		'post_type'      => 'attachment',
		'post_mime_type' => 'image',
		'order'          => 'ASC',
		'orderby'        => 'menu_order ID'
	) );

	// If there is more than 1 attachment in a gallery...
	if ( count( $attachment_ids ) > 1 ) {
		foreach ( $attachment_ids as $attachment_id ) {
			if ( $attachment_id == $post->ID ) {
				$next_id = current( $attachment_ids );
				break;
			}
		}

		// get the URL of the next image attachment...
		if ( $next_id )
			$next_attachment_url = get_attachment_link( $next_id );

		// or get the URL of the first image attachment.
		else
			$next_attachment_url = get_attachment_link( array_shift( $attachment_ids ) );
	}

	printf( '<a href="%1$s" title="%2$s" rel="attachment">%3$s</a>',
		esc_url( $next_attachment_url ),
		the_title_attribute( array( 'echo' => false ) ),
		wp_get_attachment_image( $post->ID, $attachment_size )
	);
}
endif;

if ( ! function_exists( '_tk_posted_on' ) ) :
/**
 * Prints HTML with meta information for the current post-date/time and author.
 */
function _tk_posted_on() { 
    global $post;
			// first check if to step into the first 3 things at all
    if( !is_single() && ( true == get_theme_mod( 'show_date' ) || true == get_theme_mod( 'show_category' ) || true == get_theme_mod( 'show_author' ) )
        || is_single() && ( true == get_theme_mod( 'single_show_date' ) || true == get_theme_mod( 'single_show_category' ) || true == get_theme_mod( 'single_show_author' ) ) ) :


            echo _e( 'Posted ', 'cc2' );

            // the post date comes here, if theme options are chosen for either single or archive view
            if( !is_single() && true == get_theme_mod( 'show_date' ) || is_single() && true == get_theme_mod( 'single_show_date' ) ) { ?>

                    <span class="post-date-wrap">on
                        <a class="post-date-link" href="<?php the_permalink(); ?>" title="<?php the_title(); ?>">
                            <span class="post-date-icon glyphicon glyphicon-calendar"></span>
                            <time class="post-date" datetime="<?php esc_attr( the_date( 'c' ) ) ?>">
                                <?php esc_html( the_time('F j, Y') ); ?>
                            </time>
                        </a>
                    </span>

            <?php }


            // now the category
            if( !is_single() && true == get_theme_mod( 'show_category' ) || is_single() && true == get_theme_mod( 'single_show_category' ) ) { ?>

                <span class="post-category-wrap">in <?php the_category(', '); ?> </span>

            <?php }


            // then the author stuff
            if( !is_single() && true == get_theme_mod( 'show_author' ) || is_single() && true == get_theme_mod( 'single_show_author' ) ) { ?>

                <span class="author vcard">
                    <?php if (defined('BP_VERSION')) {
                        printf( __('by %s', 'cc2'), bp_core_get_userlink( $post->post_author ) );
                    } else { 	// else if no BP activated, do this
                        echo sprintf( __('by %s', 'cc2'), '<a href="' . get_author_posts_url( get_the_author_meta( 'ID' ) ) . '" title="View all posts by ' . esc_attr( get_the_author() ) . '">'. get_the_author_meta( 'display_name' ) . '</a>' );
                    }

                    ?>
               </span>

            <?php }



        // display comments - if is single post view
        if( is_single() && true == get_theme_mod( 'single_show_comments' ) ) { ?>

            <a class="jump-to-comments" ref="#comments" title="Comments on this post">
                <span class="alignright">
                    <span class="glyphicon glyphicon-comments"></span>
                    <?php comments_number( _e( 'No Comments', 'cc2' ), _e( 'One Comment', 'cc2' ), _e( '$ Comments', 'cc2' ) ); ?>
                </span>
            </a>

        <?php }

    endif;
	
}
endif;

/**
 * Return / display author image with varied results, depending on whether BuddyPress is active or not.
 * 
 * @author Fabian Wolf
 * @since 2.0r1
 * @package cc2
 * 
 * @param int $user_id		User-ID of the author avatar to retrieve. Optional inside the loop, but required if used outside.
 * @return array $return	Returns an associative array with the following keys
 * @return string $image	Complete HTML image source.
 * @return string $linked_image	Complete HTML with image linked to the authors profile (if available; else returns false).
 * @return string $src		URL to the image.
 * @return int $width
 * @return int $height
 */

if( !function_exists( 'cc2_get_author_image' ) ) :

	function cc2_get_author_image( $user_id = 0 ) {
		global $post;
		$return = false;
		

		// author avatar settings
		$author_email = ( empty( $user_id ) ? get_the_author_meta( 'user_email' ) : get_the_author_meta( 'user_email', $user_id ) );;
		$avatar_size = apply_filters( 'cc2_author_avatar_size', '60' );
		$avatar_image = get_avatar( $author_email, $avatar_size );
		
		// prepare return array
		$arrReturn = array(
			'width' => $avatar_size,
			'height' => $avatar_size,
			'image' => $avatar_image,
			'linked_image' => false,
		);
		
		// get image src
		if( class_exists( 'simple_html_dom' ) ) {
			$dom = new simple_html_dom();
			$dom->load( '<html><body>'. $avatar_image .'</body></html>' );
			$arrReturn['src'] = $dom->find('img', 0 )->src;
		} else { // simple, but more failure-prone variation
			$x = explode( " src='", $avatar_image );
			$x2 = explode( "' class='", $x[1] );
			$arrReturn['src'] = trim($x2[0]);
		}
		
		// linked html source
		$author_url = cc2_get_author_posts_url( $user_id );
		
		if( !empty( $author_url ) ) {
			//$author_name = get_author_name();
			$author_name = get_the_author_meta('display_name');
			
			$arrReturn['linked_image'] = '<a href="' . $author_url . '" title="'. sprintf( __('View all posts by %s', 'cc2'), $author_name ) .'">'.$arrReturn['image'].'</a>';
		}
		
	
		
		/**
		 * Hook into this filter if you want to roll your own .. ;)
		 */
		
		$return = apply_filters('cc2_get_author_avatar', $arrReturn );
		
		return $return;
	}

endif;

/**
 * Get author posts link
 */
if( !function_exists( 'cc2_get_author_posts_url' ) ) :
	function cc2_get_author_posts_url( $user_id = 0 ) {
		global $post;
		$return = false;

		// buddypress-specific call
		if (defined('BP_VERSION')) {
			if( empty( $user_id ) ) {
				$post->post_author;
			}
			
			/**
			 * @see http://codex.buddypress.org/developer/function-examples/bp_core_get_userlink/
			 */
			$return = bp_core_get_userlink( $user_id, false, true );
			
		} else { // default wp style
		
			if( empty( $user_id ) ) {
				$user_id = get_the_author_meta( 'ID' );
			}
			
			$return = get_author_posts_url( $user_id );
		}
		return $return;
	}
	
endif;



/**
 * Returns true if a blog has more than 1 category
 */
function _tk_categorized_blog() {
	if ( false === ( $all_the_cool_cats = get_transient( 'all_the_cool_cats' ) ) ) {
		// Create an array of all the categories that are attached to posts
		$all_the_cool_cats = get_categories( array(
			'hide_empty' => 1,
		) );

		// Count the number of categories that are attached to the posts
		$all_the_cool_cats = count( $all_the_cool_cats );

		set_transient( 'all_the_cool_cats', $all_the_cool_cats );
	}

	if ( '1' != $all_the_cool_cats ) {
		// This blog has more than 1 category so _tk_categorized_blog should return true
		return true;
	} else {
		// This blog has only 1 category so _tk_categorized_blog should return false
		return false;
	}
}

/**
 * Flush out the transients used in _tk_categorized_blog
 */
function _tk_category_transient_flusher() {
	// Like, beat it. Dig?
	delete_transient( 'all_the_cool_cats' );
}
add_action( 'edit_category', '_tk_category_transient_flusher' );
add_action( 'save_post',     '_tk_category_transient_flusher' );
