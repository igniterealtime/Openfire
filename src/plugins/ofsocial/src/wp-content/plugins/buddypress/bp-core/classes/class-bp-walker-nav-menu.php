<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Create HTML list of BP nav items.
 *
 * @since 1.7.0
 */
class BP_Walker_Nav_Menu extends Walker_Nav_Menu {

	/**
	 * Description of fields indexes for building markup.
	 *
	 * @since 1.7.0
	 * @var array
	 */
	var $db_fields = array( 'id' => 'css_id', 'parent' => 'parent' );

	/**
	 * Tree type.
	 *
	 * @since 1.7.0
	 * @var string
	 */
	var $tree_type = array();

	/**
	 * Display array of elements hierarchically.
	 *
	 * This method is almost identical to the version in {@link Walker::walk()}.
	 * The only change is on one line which has been commented. An IF was
	 * comparing 0 to a non-empty string which was preventing child elements
	 * being grouped under their parent menu element.
	 *
	 * This caused a problem for BuddyPress because our primary/secondary
	 * navigations don't have a unique numerical ID that describes a
	 * hierarchy (we use a slug). Obviously, WordPress Menus use Posts, and
	 * those have ID/post_parent.
	 *
	 * @since 1.7.0
	 *
	 * @see Walker::walk()
	 *
	 * @param array $elements  See {@link Walker::walk()}.
	 * @param int   $max_depth See {@link Walker::walk()}.
	 *
	 * @return string See {@link Walker::walk()}.
	 */
	public function walk( $elements, $max_depth ) {
		$func_args = func_get_args();

		$args   = array_slice( $func_args, 2 );
		$output = '';

		if ( $max_depth < -1 ) // invalid parameter
			return $output;

		if ( empty( $elements ) ) // nothing to walk
			return $output;

		$parent_field = $this->db_fields['parent'];

		// flat display
		if ( -1 == $max_depth ) {

			$empty_array = array();
			foreach ( $elements as $e )
				$this->display_element( $e, $empty_array, 1, 0, $args, $output );

			return $output;
		}

		/*
		 * Need to display in hierarchical order
		 * separate elements into two buckets: top level and children elements
		 * children_elements is two dimensional array, eg.
		 * children_elements[10][] contains all sub-elements whose parent is 10.
		 */
		$top_level_elements = array();
		$children_elements  = array();

		foreach ( $elements as $e ) {
			// BuddyPress: changed '==' to '==='. This is the only change from version in Walker::walk().
			if ( 0 === $e->$parent_field )
				$top_level_elements[] = $e;
			else
				$children_elements[$e->$parent_field][] = $e;
		}

		/*
		 * When none of the elements is top level
		 * assume the first one must be root of the sub elements.
		 */
		if ( empty( $top_level_elements ) ) {

			$first              = array_slice( $elements, 0, 1 );
			$root               = $first[0];
			$top_level_elements = array();
			$children_elements  = array();

			foreach ( $elements as $e ) {
				if ( $root->$parent_field == $e->$parent_field )
					$top_level_elements[] = $e;
				else
					$children_elements[$e->$parent_field][] = $e;
			}
		}

		foreach ( $top_level_elements as $e )
			$this->display_element( $e, $children_elements, $max_depth, 0, $args, $output );

		/*
		 * if we are displaying all levels, and remaining children_elements is not empty,
		 * then we got orphans, which should be displayed regardless
		 */
		if ( ( $max_depth == 0 ) && count( $children_elements ) > 0 ) {
			$empty_array = array();

			foreach ( $children_elements as $orphans )
				foreach ( $orphans as $op )
					$this->display_element( $op, $empty_array, 1, 0, $args, $output );
		 }

		 return $output;
	}

	/**
	 * Display the current <li> that we are on.
	 *
	 * @see Walker::start_el() for complete description of parameters.
	 *
	 * @since 1.7.0
	 *
	 * @param string $output Passed by reference. Used to append
	 *                       additional content.
	 * @param object $item   Menu item data object.
	 * @param int    $depth  Depth of menu item. Used for padding. Optional,
	 *                       defaults to 0.
	 * @param array  $args   Optional. See {@link Walker::start_el()}.
	 * @param int    $id     Menu item ID. Optional.
	 */
	public function start_el( &$output, $item, $depth = 0, $args = array(), $id = 0 ) {
		// If we're someway down the tree, indent the HTML with the appropriate number of tabs
		$indent = $depth ? str_repeat( "\t", $depth ) : '';

		/**
		 * Filters the classes to be added to the nav menu markup.
		 *
		 * @since 1.7.0
		 *
		 * @param array  $value Array of classes to be added.
		 * @param object $item  Menu item data object.
		 * @param array  $args  Array of arguments for the item.
		 */
		$class_names = join( ' ', apply_filters( 'bp_nav_menu_css_class', array_filter( $item->class ), $item, $args ) );
		$class_names = ! empty( $class_names ) ? ' class="' . esc_attr( $class_names ) . '"' : '';

		// Add HTML ID
		$id = sanitize_html_class( $item->css_id . '-personal-li' );  // Backpat with BP pre-1.7

		/**
		 * Filters the value to be used for the nav menu ID attribute.
		 *
		 * @since 1.7.0
		 *
		 * @param string $id   ID attribute to be added to the menu item.
		 * @param object $item Menu item data object.
		 * @param array  $args Array of arguments for the item.
		 */
		$id = apply_filters( 'bp_nav_menu_item_id', $id, $item, $args );
		$id = ! empty( $id ) ? ' id="' . esc_attr( $id ) . '"' : '';

		// Opening tag; closing tag is handled in Walker_Nav_Menu::end_el().
		$output .= $indent . '<li' . $id . $class_names . '>';

		// Add href attribute
		$attributes = ! empty( $item->link ) ? ' href="' . esc_url( $item->link ) . '"' : '';

		// Construct the link
		$item_output = $args->before;
		$item_output .= '<a' . $attributes . '>';

		/**
		 * Filters the link text to be added to the item output.
		 *
		 * @since 1.7.0
		 *
		 * @param string $name  Item text to be applied.
		 * @param int    $value Post ID the title is for.
		 */
		$item_output .= $args->link_before . apply_filters( 'the_title', $item->name, 0 ) . $args->link_after;
		$item_output .= '</a>';
		$item_output .= $args->after;

		/**
		 * Filters the final result for the menu item.
		 *
		 * @since 1.7.0
		 *
		 * @param string $item_output Constructed output for the menu item to append to output.
		 * @param object $item        Menu item data object.
		 * @param int    $depth       Depth of menu item. Used for padding.
		 * @param array  $args        Array of arguments for the item.
		 */
		$output .= apply_filters( 'bp_walker_nav_menu_start_el', $item_output, $item, $depth, $args );
	}
}
