<?php
/**
* Changes by Jeff Mould (@link http://www.jeffmould.com/2014/02/10/responsive-multi-level-drop-menu-bootstrap-3-updated/):
* Adapted from Edward McIntyre's wp_bootstrap_navwalker class. 
* Removed support for glyphicon and added support for Font Awesome
* @link https://github.com/jeffmould/multi-level-bootstrap-menu
* 
* Further changes by Fabian Wolf (@link http://usability-idealist.de/) for proper integration with CC2:
* - adjustments for re-enabling the parent item link
* - slight code improvements (mostly readability and avoiding class collisions)
* 
*/
/**
 * Class Name: wp_bootstrap_navwalker
 * GitHub URI: https://github.com/twittem/wp-bootstrap-navwalker
 * Description: A custom WordPress nav walker class to implement the Bootstrap 3 navigation style in a custom theme using the WordPress built in menu manager.
 * Version: 2.0.4
 * Author: Edward McIntyre - @twittem
 * License: GPL-2.0+
 * License URI: http://www.gnu.org/licenses/gpl-2.0.txt
 */
//exit if accessed directly
if(!defined('ABSPATH')) exit;

if( !class_exists( 'wp_bootstrap_navwalker' ) ) :

class wp_bootstrap_navwalker extends Walker_Nav_Menu {
	
	/**
	 * Enable parent item links?
	 */
	
	protected $use_hrefs = false;

	function __construct( $params = false ) {
		if( !empty( $params ) && is_array( $params ) ) {
			foreach( array_keys( $params ) as $strParam ) {
				switch( $strParam ) {
					case 'use_hrefs':
					case 'link_parent_items':
						
						$this->use_hrefs = !empty( $params[ $strParam ] );
						break;
				}
			}
		
		
			
		}
		
		
	}

	/**
	 * @see Walker::start_lvl()
	 * @since 3.0.0
	 *
	 * @param string $output Passed by reference. Used to append additional content.
	 * @param int $depth Depth of page. Used for padding.
	 */
	public function start_lvl( &$output, $depth = 0, $args = array() ) {
		$indent = str_repeat( "\t", $depth );
		
		/**
		 * NOTE: Shouldn't this be WITHOUT an prefixing space?!
		 */
		
		$output .= "\n$indent<ul role=\"menu\" class=\"dropdown-menu\">\n";
		
	}

	/**
	 * @see Walker::start_el()
	 * @since 3.0.0
	 *
	 * @param string $output Passed by reference. Used to append additional content.
	 * @param object $item Menu item data object.
	 * @param int $depth Depth of menu item. Used for padding.
	 * @param int $current_page Menu item ID.
	 * @param object $args
	 */
	public function start_el( &$output, $item, $depth = 0, $args = array(), $id = 0 ) {
		$indent = ( $depth ) ? str_repeat( "\t", $depth ) : '';

		/**
		 * Dividers, Headers or Disabled
		 * =============================
		 * Determine whether the item is a Divider, Header, Disabled or regular
		 * menu item. To prevent errors we use the strcasecmp() function to so a
		 * comparison that is not case sensitive. The strcasecmp() function returns
		 * a 0 if the strings are equal.
		 */
		if ( strcasecmp( $item->attr_title, 'divider' ) == 0 && $depth === 1 ) {
			$output .= $indent . '<li role="presentation" class="divider">';
		} else if ( strcasecmp( $item->title, 'divider') == 0 && $depth === 1 ) {
			$output .= $indent . '<li role="presentation" class="divider">';
		} else if ( strcasecmp( $item->attr_title, 'dropdown-header') == 0 && $depth === 1 ) {
			$output .= $indent . '<li role="presentation" class="dropdown-header">' . esc_attr( $item->title );
		} else if ( strcasecmp($item->attr_title, 'disabled' ) == 0 ) {
			$output .= $indent . '<li role="presentation" class="disabled"><a href="#">' . esc_attr( $item->title ) . '</a>';
		} else {

			$class_names = $value = '';

			$classes = empty( $item->classes ) ? array() : (array) $item->classes;
			$classes[] = 'menu-item-' . $item->ID;

			$class_names = join( ' ', apply_filters( 'nav_menu_css_class', array_filter( $classes ), $item, $args ) );

			/*
			if ( $args->has_children )
				$class_names .= ' dropdown';
			*/

			if($args->has_children && $depth === 0) { $class_names .= ' dropdown'; } elseif($args->has_children && $depth > 0) { $class_names .= ' dropdown-submenu'; }

			if ( in_array( 'current-menu-item', $classes ) )
				$class_names .= ' active';

			// remove Font Awesome icon from classes array and save the icon
			// we will add the icon back in via a <span> below so it aligns with
			// the menu item
			if ( in_array('fa', $classes)) {
				$key = array_search('fa', $classes);
				$icon = $classes[$key + 1];
				$class_names = str_replace($classes[$key+1], '', $class_names);
				$class_names = str_replace($classes[$key], '', $class_names);

			}
			
			$class_names = $class_names ? ' class="' . esc_attr( $class_names ) . '"' : '';

			$id = apply_filters( 'nav_menu_item_id', 'menu-item-'. $item->ID, $item, $args );
			$id = $id ? ' id="' . esc_attr( $id ) . '"' : '';

			$output .= $indent . '<li' . $id . $value . $class_names .'>';

			$atts = array();
			$atts['title']  = ! empty( $item->title )	? $item->title	: '';
			$atts['target'] = ! empty( $item->target )	? $item->target	: '';
			$atts['rel']    = ! empty( $item->xfn )		? $item->xfn	: '';

			// If item has_children add atts to a.
			// if ( $args->has_children && $depth === 0 ) {
			$url = ! empty( $item->url ) ? $item->url : '';
			
			//if ( $args->has_children ) {
			if ( $args->has_children && $depth === 0 ) {
				$atts['href']   		= ( empty( $url ) ? '#' : $url ); // make parent item clickable again
				$atts['data-toggle']	= 'dropdown';
				//$atts['data-target']	= $atts['href'];
				
				if( $this->use_hrefs != false ) {
					$atts['data-target'] = $atts['href'];
				} else {
					$atts['data-target']	= '#'; // avoids issues with the 
				}
				
				$atts['class']			= 'dropdown-toggle';
				
				// => data-toggle="dropdown" data-target="'. $url . '" class="dropdown-toggle"'
				
				$atts['aria-haspopup'] = 'true';
				
			} else {
				$atts['href'] = $url;
			}

			$atts = apply_filters( 'nav_menu_link_attributes', $atts, $item, $args );

			$attributes = '';
			foreach ( $atts as $attr => $value ) {
				if ( ! empty( $value ) ) {
					$value = ( 'href' === $attr ) ? esc_url( $value ) : esc_attr( $value );
					$attributes .= ' ' . $attr . '="' . $value . '"';
				}
			}

			$item_output = $args->before;

			// Font Awesome icons
			if ( ! empty( $icon ) ) {
				$item_output .= '<a'. $attributes .'><span class="fa ' . esc_attr( $icon ) . '"></span>&nbsp;';
			} else {
				$item_output .= '<a'. $attributes .'>';	
			}

			$item_output .= $args->link_before . apply_filters( 'the_title', $item->title, $item->ID ) . $args->link_after;
			$item_output .= ( $args->has_children && 0 === $depth ) ? ' <span class="caret"></span></a>' : '</a>';
			$item_output .= $args->after;

			$output .= apply_filters( 'walker_nav_menu_start_el', $item_output, $item, $depth, $args );
		}
	}

	/**
	 * Traverse elements to create list from elements.
	 *
	 * Display one element if the element doesn't have any children otherwise,
	 * display the element and its children. Will only traverse up to the max
	 * depth and no ignore elements under that depth.
	 *
	 * This method shouldn't be called directly, use the walk() method instead.
	 *
	 * @see Walker::start_el()
	 * @since 2.5.0
	 *
	 * @param object $element Data object
	 * @param array $children_elements List of elements to continue traversing.
	 * @param int $max_depth Max depth to traverse.
	 * @param int $depth Depth of current element.
	 * @param array $args
	 * @param string $output Passed by reference. Used to append additional content.
	 * @return null Null on failure with no changes to parameters.
	 */
	public function display_element( $element, &$children_elements, $max_depth, $depth, $args, &$output ) {
        if ( ! $element )
            return;

        $id_field = $this->db_fields['id'];

        // Display this element.
        if ( is_object( $args[0] ) )
           $args[0]->has_children = ! empty( $children_elements[ $element->$id_field ] );

        parent::display_element( $element, $children_elements, $max_depth, $depth, $args, $output );
    }

	/**
	 * Menu Fallback
	 * =============
	 * If this function is assigned to the wp_nav_menu's fallback_cb variable
	 * and a manu has not been assigned to the theme location in the WordPress
	 * menu manager the function with display nothing to a non-logged in user,
	 * and will add a link to the WordPress menu manager if logged in as an admin.
	 *
	 * @param array $args passed from the wp_nav_menu function.
	 *
	 */
	public static function fallback( $args ) {
		if ( current_user_can( 'manage_options' ) ) {

			extract( $args );

			$fb_output = null;

			if ( $container ) {
				$fb_output = '<' . $container;

				if ( $container_id )
					$fb_output .= ' id="' . $container_id . '"';

				if ( $container_class )
					$fb_output .= ' class="' . $container_class . '"';

				$fb_output .= '>';
			}

			$fb_output .= '<ul';

			if ( $menu_id )
				$fb_output .= ' id="' . $menu_id . '"';

			if ( $menu_class )
				$fb_output .= ' class="' . $menu_class . '"';

			$fb_output .= '>';
			$fb_output .= '<li><a href="' . admin_url( 'nav-menus.php' ) . '">Add a menu</a></li>';
			$fb_output .= '</ul>';

			if ( $container )
				$fb_output .= '</' . $container . '>';

			echo $fb_output;
		}
	}
}

endif; // !class_exists
