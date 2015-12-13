<?php

class BB_Walker {
	var $tree_type;
	var $db_fields;

	//abstract callbacks
	function start_lvl($output) { return $output; }
	function end_lvl($output)   { return $output; }
	function start_el($output)  { return $output; }
	function end_el($output)    { return $output; }

	function _init() {
		$this->parents = array();
		$this->depth = 1;
		$this->previous_element = '';
	}		

	function walk($elements, $to_depth) {
		$args = array_slice(func_get_args(), 2);
		$output = '';

		// padding at the end
		$last_element->{$this->db_fields['parent']} = 0;
		$last_element->{$this->db_fields['id']} = 0;
		$elements[] = $last_element;

		$flat = (-1 == $to_depth) ? true : false;
		foreach ( $elements as $element )
			$output .= call_user_func_array( array(&$this, 'step'), array_merge( array($element, $to_depth), $args ) );

		return $output;
	}

	function step( $element, $to_depth ) {
		if ( !isset($this->depth) )
			$this->_init();

		$args = array_slice(func_get_args(), 2);
		$id_field = $this->db_fields['id'];
		$parent_field = $this->db_fields['parent'];

		$flat = (-1 == $to_depth) ? true : false;

		$output = '';

		// If flat, start and end the element and skip the level checks.
		if ( $flat ) {
			// Start the element.
			if ( isset($element->$id_field) && $element->$id_field != 0 ) {
				$cb_args = array_merge( array(&$output, $element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'start_el'), $cb_args);
			}

			// End the element.
			if ( isset($element->$id_field) && $element->$id_field != 0 ) {
				$cb_args = array_merge( array(&$output, $element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'end_el'), $cb_args);
			}

			return;
		}

		// Walk the tree.
		if ( !empty($element) && !empty($this->previous_element) && $element->$parent_field == $this->previous_element->$id_field ) {
			// Previous element is my parent. Descend a level.
			array_unshift($this->parents, $this->previous_element);
			if ( !$to_depth || ($this->depth < $to_depth) ) { //only descend if we're below $to_depth
				$cb_args = array_merge( array(&$output, $this->depth), $args);
				call_user_func_array(array(&$this, 'start_lvl'), $cb_args);
			} else if ( $to_depth && $this->depth == $to_depth  ) {  // If we've reached depth, end the previous element.
				$cb_args = array_merge( array(&$output, $this->previous_element, $this->depth), $args);
				call_user_func_array(array(&$this, 'end_el'), $cb_args);
			}
			$this->depth++; //always do this so when we start the element further down, we know where we are
		} else if ( !empty($element) && !empty($this->previous_element) && $element->$parent_field == $this->previous_element->$parent_field) {
			// On the same level as previous element.
			if ( !$to_depth || ($this->depth <= $to_depth) ) {
				$cb_args = array_merge( array(&$output, $this->previous_element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'end_el'), $cb_args);
			}
		} else if ( $this->depth > 1 ) {
			// Ascend one or more levels.
			if ( !$to_depth || ($this->depth <= $to_depth) ) {
				$cb_args = array_merge( array(&$output, $this->previous_element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'end_el'), $cb_args);
			}

			while ( $parent = array_shift($this->parents) ) {
				$this->depth--;
				if ( !$to_depth || ($this->depth < $to_depth) ) {
					$cb_args = array_merge( array(&$output, $this->depth), $args);
					call_user_func_array(array(&$this, 'end_lvl'), $cb_args);
					$cb_args = array_merge( array(&$output, $parent, $this->depth - 1), $args);
					call_user_func_array(array(&$this, 'end_el'), $cb_args);
				}
				if ( !empty($element) && isset($this->parents[0]) && $element->$parent_field == $this->parents[0]->$id_field ) {
					break;
				}
			}
		} else if ( !empty($this->previous_element) ) {
			// Close off previous element.
			if ( !$to_depth || ($this->depth <= $to_depth) ) {
				$cb_args = array_merge( array(&$output, $this->previous_element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'end_el'), $cb_args);
			}
		}

		// Start the element.
		if ( !$to_depth || ($this->depth <= $to_depth) ) {
			if ( !empty($element) && $element->$id_field != 0 ) {
				$cb_args = array_merge( array(&$output, $element, $this->depth - 1), $args);
				call_user_func_array(array(&$this, 'start_el'), $cb_args);
			}
		}

		$this->previous_element = $element;
		return $output;
	}
}

class BB_Walker_Blank extends BB_Walker { // Used for template functions
	var $tree_type;
	var $db_fields = array( 'id' => '', 'parent' => '' );

	var $start_lvl = '';
	var $end_lvl   = '';

	//abstract callbacks
	function start_lvl( $output, $depth ) { 
		if ( !$this->start_lvl )
			return '';
		$indent = str_repeat("\t", $depth);
		$output .= $indent . "$this->start_lvl\n";
		return $output;
	}

	function end_lvl( $output, $depth )   {
		if ( !$this->end_lvl )
			return '';
		$indent = str_repeat("\t", $depth);
		$output .= $indent . "$this->end_lvl\n";
		return $output;
	}

	function start_el()  { return ''; }
	function end_el()    { return ''; }
}

class BB_Loop {
	var $elements;
	var $walker;
	var $_preserve = array();
	var $_looping = false;

	function &start( $elements, $walker = 'BB_Walker_Blank' ) {
		$null = null;
		$a = new BB_Loop( $elements );
		if ( !$a->elements )
			return $null;
		$a->walker = new $walker;
		return $a;
	}

	function BB_Loop( &$elements ) {
		$this->elements = $elements;
		if ( !is_array($this->elements) || empty($this->elements) )
			return $this->elements = false;
	}

	function step() {
		if ( !is_array($this->elements) || !current($this->elements) || !is_object($this->walker) )
			return false;

		if ( !$this->_looping ) {
			$r = reset($this->elements);
			$this->_looping = true;
		} else {
			$r = next($this->elements);
		}

		if ( !$args = func_get_args() )
			$args = array( 0 );
		echo call_user_func_array( array(&$this->walker, 'step'), array_merge(array(current($this->elements)), $args) );
		return $r;
	}

	function pad( $pad, $offset = 0 ) {
		if ( !is_array($this->elements) || !is_object($this->walker) )
			return false;

		if ( is_numeric($pad) )
			return $pad * ($this->walker->depth - 1) + (int) $offset;

		return str_repeat( $pad, $this->walker->depth - 1 );
	}

	function preserve( $array ) {
		if ( !is_array( $array ) )
			return false;

		foreach ( $array as $key )
			$this->_preserve[$key] = isset( $GLOBALS[$key] ) ? $GLOBALS[$key] : null;
	}

	function reinstate() {
		foreach ( $this->_preserve as $key => $value )
			$GLOBALS[$key] = $value;
	}

	function classes( $output = 'string' ) {
		if ( !is_array($this->elements) || !is_object($this->walker) )
			return false;
		$classes = array();

		$current = current($this->elements);

		if ( $prev = prev($this->elements) )
			next($this->elements);
		else		
			reset($this->elements);

		if ( $next = next($this->elements) )
			prev($this->elements);
		else
			end($this->elements);

		if ( !empty($next) && $next->{$this->walker->db_fields['parent']} == $current->{$this->walker->db_fields['id']} )
			$classes[] = 'bb-parent';
		elseif ( !empty($next) && $next->{$this->walker->db_fields['parent']} == $current->{$this->walker->db_fields['parent']} )
			$classes[] = 'bb-precedes-sibling';
		else
			$classes[] = 'bb-last-child';

		if ( !empty($prev) && $current->{$this->walker->db_fields['parent']} == $prev->{$this->walker->db_fields['id']} )
			$classes[] = 'bb-first-child';
		elseif ( !empty($prev) && $current->{$this->walker->db_fields['parent']} == $prev->{$this->walker->db_fields['parent']} )
			$classes[] = 'bb-follows-sibling';
		elseif ( $prev )
			$classes[] = 'bb-follows-niece';

		if ( $this->walker->depth > 1 )
			$classes[] = 'bb-child';
		else
			$classes[] = 'bb-root';

		if ( $output === 'string' )
			$classes = join(' ', $classes);

		return $classes;
	}

}
