<?php
// Last sync [WP11537] - Refactored into a class based on wp-includes/taxonomy.php

/**
 * Taxonomy API
 *
 * @package WordPress
 * @subpackage Taxonomy
 * @since 2.3.0
 */

/**
 * WordPress Taxonomy based off of WordPress revision 8782.
 *
 * @since 2.3.0
 */
class WP_Taxonomy {
	/**
	 * Stores the database.
	 *
	 * @var unknown_type
	 */
	var $db;
	var $taxonomies = array();

	function WP_Taxonomy( &$db ) {
		$this->__construct( $db );
		register_shutdown_function( array(&$this, '__destruct') );
	}

	/**
	 * PHP5 constructor - Assigns the database to an attribute of the class.
	 *
	 * @param unknown_type $db
	 */
	function __construct( &$db ) {
		$this->db =& $db;
	}

	/**
	 * Does nothing.
	 *
	 * @package BackPress
	 * @subpackage Taxonomy
	 */
	function __destruct() {
	}

	/**
	 * Return all of the taxonomy names that are of $object_type.
	 *
	 * It appears that this function can be used to find all of the names inside of
	 * $this->taxonomies variable.
	 *
	 * <code><?php $taxonomies = $this->get_object_taxonomies('post'); ?></code> Should
	 * result in <code>Array('category', 'post_tag')</code>
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->taxonomies
	 *
	 * @param array|string|object $object_type Name of the type of taxonomy object, or an object (row from posts)
	 * @return array The names of all taxonomy of $object_type.
	 */
	function get_object_taxonomies($object_type) {
		$object_type = (array) $object_type;

		// WP DIFF
		$taxonomies = array();
		foreach ( (array) $this->taxonomies as $taxonomy ) {
			if ( array_intersect($object_type, (array) $taxonomy->object_type) )
				$taxonomies[] = $taxonomy->name;
		}

		return $taxonomies;
	}

	/**
	 * Retrieves the taxonomy object of $taxonomy.
	 *
	 * The get_taxonomy function will first check that the parameter string given
	 * is a taxonomy object and if it is, it will return it.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->taxonomies
	 * @uses $this->is_taxonomy() Checks whether taxonomy exists
	 *
	 * @param string $taxonomy Name of taxonomy object to return
	 * @return object|bool The Taxonomy Object or false if $taxonomy doesn't exist
	 */
	function get_taxonomy( $taxonomy ) {
		if ( !$this->is_taxonomy($taxonomy) )
			return false;

		return $this->taxonomies[$taxonomy];
	}

	/**
	 * Checks that the taxonomy name exists.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 * 
	 * @uses $this->taxonomies
	 *
	 * @param string $taxonomy Name of taxonomy object
	 * @return bool Whether the taxonomy exists or not.
	 */
	function is_taxonomy( $taxonomy ) {
		return isset($this->taxonomies[$taxonomy]);
	}

	/**
	 * Whether the taxonomy object is hierarchical.
	 *
	 * Checks to make sure that the taxonomy is an object first. Then Gets the
	 * object, and finally returns the hierarchical value in the object.
	 *
	 * A false return value might also mean that the taxonomy does not exist.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->is_taxonomy() Checks whether taxonomy exists
	 * @uses $this->get_taxonomy() Used to get the taxonomy object
	 *
	 * @param string $taxonomy Name of taxonomy object
	 * @return bool Whether the taxonomy is hierarchical
	 */
	function is_taxonomy_hierarchical($taxonomy) {
		if ( !$this->is_taxonomy($taxonomy) )
			return false;

		$taxonomy = $this->get_taxonomy($taxonomy);
		return $taxonomy->hierarchical;
	}

	/**
	 * Create or modify a taxonomy object. Do not use before init.
	 *
	 * A simple function for creating or modifying a taxonomy object based on the
	 * parameters given. The function will accept an array (third optional
	 * parameter), along with strings for the taxonomy name and another string for
	 * the object type.
	 *
	 * The function keeps a default set, allowing for the $args to be optional but
	 * allow the other functions to still work. It is possible to overwrite the
	 * default set, which contains two keys: hierarchical and update_count_callback.
	 *
	 * Nothing is returned, so expect error maybe or use is_taxonomy() to check
	 * whether taxonomy exists.
	 *
	 * Optional $args contents:
	 *
	 * hierarachical - has some defined purpose at other parts of the API and is a
	 * boolean value.
	 *
	 * update_count_callback - works much like a hook, in that it will be called
	 * when the count is updated.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 * @uses $this->taxonomies Inserts new taxonomy object into the list
	 * 
	 * @param string $taxonomy Name of taxonomy object
	 * @param string $object_type Name of the object type for the taxonomy object.
	 * @param array|string $args See above description for the two keys values.
	 */
	function register_taxonomy( $taxonomy, $object_type, $args = array() ) {
		$defaults = array('hierarchical' => false, 'update_count_callback' => '');
		$args = wp_parse_args($args, $defaults);

		$args['name'] = $taxonomy;
		$args['object_type'] = $object_type;
		$this->taxonomies[$taxonomy] = (object) $args;
	}

	//
	// Term API
	//

	/**
	 * Retrieve object_ids of valid taxonomy and term.
	 *
	 * The strings of $taxonomies must exist before this function will continue. On
	 * failure of finding a valid taxonomy, it will return an WP_Error class, kind
	 * of like Exceptions in PHP 5, except you can't catch them. Even so, you can
	 * still test for the WP_Error class and get the error message.
	 *
	 * The $terms aren't checked the same as $taxonomies, but still need to exist
	 * for $object_ids to be returned.
	 *
	 * It is possible to change the order that object_ids is returned by either
	 * using PHP sort family functions or using the database by using $args with
	 * either ASC or DESC array. The value should be in the key named 'order'.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses wp_parse_args() Creates an array from string $args.
	 *
	 * @param string|array $terms String of term or array of string values of terms that will be used
	 * @param string|array $taxonomies String of taxonomy name or Array of string values of taxonomy names
	 * @param array|string $args Change the order of the object_ids, either ASC or DESC
	 * @return WP_Error|array If the taxonomy does not exist, then WP_Error will be returned. On success
	 *	the array can be empty meaning that there are no $object_ids found or it will return the $object_ids found.
	 */
	function get_objects_in_term( $terms, $taxonomies, $args = null ) {
		if ( !is_array($terms) )
			$terms = array($terms);

		if ( !is_array($taxonomies) )
			$taxonomies = array($taxonomies);

		foreach ( (array) $taxonomies as $taxonomy ) {
			if ( !$this->is_taxonomy($taxonomy) )
				return new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));
		}

		$defaults = array('order' => 'ASC', 'field' => 'term_id');
		$args = wp_parse_args( $args, $defaults );
		extract($args, EXTR_SKIP);

		if ( 'tt_id' == $field )
			$field = 'tt.term_taxonomy_id';
		else
			$field = 'tt.term_id';

		$order = ( 'desc' == strtolower($order) ) ? 'DESC' : 'ASC';

		$terms = array_map('intval', $terms);

		$taxonomies = "'" . implode("', '", $taxonomies) . "'";
		$terms = "'" . implode("', '", $terms) . "'";

		$object_ids = $this->db->get_col("SELECT tr.object_id FROM {$this->db->term_relationships} AS tr INNER JOIN {$this->db->term_taxonomy} AS tt ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tt.taxonomy IN ($taxonomies) AND $field IN ($terms) ORDER BY tr.object_id $order");

		if ( ! $object_ids )
			return array();

		return $object_ids;
	}

	/**
	 * Get all Term data from database by Term ID.
	 *
	 * The usage of the get_term function is to apply filters to a term object. It
	 * is possible to get a term object from the database before applying the
	 * filters.
	 *
	 * $term ID must be part of $taxonomy, to get from the database. Failure, might
	 * be able to be captured by the hooks. Failure would be the same value as $this->db
	 * returns for the get_row method.
	 *
	 * There are two hooks, one is specifically for each term, named 'get_term', and
	 * the second is for the taxonomy name, 'term_$taxonomy'. Both hooks gets the
	 * term object, and the taxonomy name as parameters. Both hooks are expected to
	 * return a Term object.
	 *
	 * 'get_term' hook - Takes two parameters the term Object and the taxonomy name.
	 * Must return term object. Used in get_term() as a catch-all filter for every
	 * $term.
	 *
	 * 'get_$taxonomy' hook - Takes two parameters the term Object and the taxonomy
	 * name. Must return term object. $taxonomy will be the taxonomy name, so for
	 * example, if 'category', it would be 'get_category' as the filter name. Useful
	 * for custom taxonomies or plugging into default taxonomies.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->sanitize_term() Cleanses the term based on $filter context before returning.
	 * @see $this->sanitize_term_field() The $context param lists the available values for get_term_by() $filter param.
	 *
	 * @param int|object $term If integer, will get from database. If object will apply filters and return $term.
	 * @param string $taxonomy Taxonomy name that $term is part of.
	 * @param string $output Constant OBJECT, ARRAY_A, or ARRAY_N
	 * @param string $filter Optional, default is raw or no WordPress defined filter will applied.
	 * @return mixed|null|WP_Error Term Row from database. Will return null if $term is empty. If taxonomy does not
	 * exist then WP_Error will be returned.
	 */
	function &get_term($term, $taxonomy, $output = OBJECT, $filter = 'raw') {
		if ( empty($term) ) {
			$error = new WP_Error('invalid_term', __('Empty Term'));
			return $error;
		}

		if ( !$this->is_taxonomy($taxonomy) ) {
			$error = new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));
			return $error;
		}

		if ( is_object($term) ) {
			wp_cache_add($term->term_id, $term, $taxonomy);
			wp_cache_add($term->term_taxonomy_id, $term->term_id, "$taxonomy:tt_id" );
			$_term = $term;
		} else {
			$term = (int) $term;
			if ( ! $_term = wp_cache_get($term, $taxonomy) ) {
				$_term = $this->db->get_row( $this->db->prepare( "SELECT t.*, tt.* FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} AS tt ON t.term_id = tt.term_id WHERE tt.taxonomy = %s AND t.term_id = %s LIMIT 1", $taxonomy, $term) );
				wp_cache_add($term, $_term, $taxonomy);
				wp_cache_add($_term->term_taxonomy_id, $_term->term_id, "$taxonomy:tt_id" );
			}
		}

		$_term = apply_filters('get_term', $_term, $taxonomy);
		$_term = apply_filters("get_$taxonomy", $_term, $taxonomy);
		$_term = $this->sanitize_term($_term, $taxonomy, $filter);

		backpress_convert_object( $_term, $output );

		return $_term;
	}

	/**
	 * Get all Term data from database by Term field and data.
	 *
	 * Warning: $value is not escaped for 'name' $field. You must do it yourself, if
	 * required.
	 *
	 * The default $field is 'id', therefore it is possible to also use null for
	 * field, but not recommended that you do so.
	 *
	 * If $value does not exist, the return value will be false. If $taxonomy exists
	 * and $field and $value combinations exist, the Term will be returned.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->sanitize_term() Cleanses the term based on $filter context before returning.
	 * @see $this->sanitize_term_field() The $context param lists the available values for get_term_by() $filter param.
	 *
	 * @param string $field Either 'slug', 'name', 'id', or 'tt_id'
	 * @param string|int $value Search for this term value
	 * @param string $taxonomy Taxonomy Name
	 * @param string $output Constant OBJECT, ARRAY_A, or ARRAY_N
	 * @param string $filter Optional, default is raw or no WordPress defined filter will applied.
	 * @return mixed Term Row from database. Will return false if $taxonomy does not exist or $term was not found.
	 */
	function get_term_by($field, $value, $taxonomy, $output = OBJECT, $filter = 'raw') {
		if ( !$this->is_taxonomy($taxonomy) )
			return false;

		if ( 'slug' == $field ) {
			$field = 't.slug';
			$value = $this->sanitize_term_slug($value, $taxonomy);
			if ( empty($value) )
				return false;
		} else if ( 'name' == $field ) {
			// Assume already escaped
			$field = 't.name';
		} else if ( 'tt_id' == $field ) {
			$field = 'tt.term_taxonomy_id';
			$value = (int) $value;
			if ( $_term_id = wp_cache_get( $value, "$taxonomy:tt_id" ) )
				return $this->get_term( $_term_id, $taxonomy, $output, $filter );
		} else {
			$field = 't.term_id';
			$value = (int) $value;
		}

		$term = $this->db->get_row( $this->db->prepare( "SELECT t.*, tt.* FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} AS tt ON t.term_id = tt.term_id WHERE tt.taxonomy = %s AND $field = %s LIMIT 1", $taxonomy, $value) );
		if ( !$term )
			return false;

		wp_cache_add($term->term_id, $term, $taxonomy);
		wp_cache_add($term->term_taxonomy_id, $term->term_id, "$taxonomy:tt_id" );

		$term = $this->sanitize_term($term, $taxonomy, $filter);

		backpress_convert_object( $term, $output );

		return $term;
	}

	/**
	 * Merge all term children into a single array of their IDs.
	 *
	 * This recursive function will merge all of the children of $term into the same
	 * array of term IDs. Only useful for taxonomies which are hierarchical.
	 *
	 * Will return an empty array if $term does not exist in $taxonomy.
	 * 
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->_get_term_hierarchy()
	 * @uses $this->get_term_children() Used to get the children of both $taxonomy and the parent $term
	 *
	 * @param string $term ID of Term to get children
	 * @param string $taxonomy Taxonomy Name
	 * @return array|WP_Error List of Term Objects. WP_Error returned if $taxonomy does not exist
	 */
	function get_term_children( $term_id, $taxonomy ) {
		if ( !$this->is_taxonomy($taxonomy) )
			return new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));

		$term_id = intval( $term_id );

		$terms = $this->_get_term_hierarchy($taxonomy);

		if ( ! isset($terms[$term_id]) )
			return array();

		$children = $terms[$term_id];

		foreach ( (array) $terms[$term_id] as $child ) {
			if ( isset($terms[$child]) )
				$children = array_merge($children, $this->get_term_children($child, $taxonomy));
		}

		return $children;
	}

	/**
	 * Get sanitized Term field.
	 *
	 * Does checks for $term, based on the $taxonomy. The function is for contextual
	 * reasons and for simplicity of usage. See sanitize_term_field() for more
	 * information.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->sanitize_term_field() Passes the return value in sanitize_term_field on success.
	 *
	 * @param string $field Term field to fetch
	 * @param int $term Term ID
	 * @param string $taxonomy Taxonomy Name
	 * @param string $context Optional, default is display. Look at sanitize_term_field() for available options.
	 * @return mixed Will return an empty string if $term is not an object or if $field is not set in $term.
	 */
	function get_term_field( $field, $term, $taxonomy, $context = 'display' ) {
		$term = (int) $term;
		$term = $this->get_term( $term, $taxonomy );
		if ( is_wp_error($term) )
			return $term;

		if ( !is_object($term) )
			return '';

		if ( !isset($term->$field) )
			return '';

		return $this->sanitize_term_field($field, $term->$field, $term->term_id, $taxonomy, $context);
	}

	/**
	 * Sanitizes Term for editing.
	 *
	 * Return value is sanitize_term() and usage is for sanitizing the term for
	 * editing. Function is for contextual and simplicity.
	 * 
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->sanitize_term() Passes the return value on success
	 *
	 * @param int|object $id Term ID or Object
	 * @param string $taxonomy Taxonomy Name
	 * @return mixed|null|WP_Error Will return empty string if $term is not an object.
	 */
	function get_term_to_edit( $id, $taxonomy ) {
		$term = $this->get_term( $id, $taxonomy );

		if ( is_wp_error($term) )
			return $term;

		if ( !is_object($term) )
			return '';

		return $this->sanitize_term($term, $taxonomy, 'edit');
	}

	/**
	 * Retrieve the terms in a given taxonomy or list of taxonomies.
	 *
	 * You can fully inject any customizations to the query before it is sent, as
	 * well as control the output with a filter.
	 *
	 * The 'get_terms' filter will be called when the cache has the term and will
	 * pass the found term along with the array of $taxonomies and array of $args.
	 * This filter is also called before the array of terms is passed and will pass
	 * the array of terms, along with the $taxonomies and $args.
	 *
	 * The 'list_terms_exclusions' filter passes the compiled exclusions along with
	 * the $args.
	 *
	 * The 'get_terms_orderby' filter passes the ORDER BY clause for the query
	 * along with the $args array.
	 *
	 * The 'get_terms_fields' filter passes the fields for the SELECT query
	 * along with the $args array.
	 *
	 * The list of arguments that $args can contain, which will overwrite the defaults:
	 *
	 * orderby - Default is 'name'. Can be name, count, term_group, slug or nothing
	 * (will use term_id), Passing a custom value other than these will cause it to
	 * order based on the custom value.
	 *
	 * order - Default is ASC. Can use DESC.
	 *
	 * hide_empty - Default is true. Will not return empty terms, which means
	 * terms whose count is 0 according to the given taxonomy.
	 *
	 * exclude - Default is an empty string.  A comma- or space-delimited string
	 * of term ids to exclude from the return array.  If 'include' is non-empty,
	 * 'exclude' is ignored.
	 *
	 * include - Default is an empty string.  A comma- or space-delimited string
	 * of term ids to include in the return array.
	 *
	 * number - The maximum number of terms to return.  Default is empty.
	 *
	 * offset - The number by which to offset the terms query.
	 *
	 * fields - Default is 'all', which returns an array of term objects.
	 * If 'fields' is 'ids' or 'names', returns an array of
	 * integers or strings, respectively.
	 *
	 * slug - Returns terms whose "slug" matches this value. Default is empty string.
	 *
	 * hierarchical - Whether to include terms that have non-empty descendants
	 * (even if 'hide_empty' is set to true).
	 *
	 * search - Returned terms' names will contain the value of 'search',
	 * case-insensitive.  Default is an empty string.
	 *
	 * name__like - Returned terms' names will begin with the value of 'name__like',
	 * case-insensitive. Default is empty string.
	 *
	 * The argument 'pad_counts', if set to true will include the quantity of a term's
	 * children in the quantity of each term's "count" object variable.
	 *
	 * The 'get' argument, if set to 'all' instead of its default empty string,
	 * returns terms regardless of ancestry or whether the terms are empty.
	 *
	 * The 'child_of' argument, when used, should be set to the integer of a term ID.  Its default
	 * is 0.  If set to a non-zero value, all returned terms will be descendants
	 * of that term according to the given taxonomy.  Hence 'child_of' is set to 0
	 * if more than one taxonomy is passed in $taxonomies, because multiple taxonomies
	 * make term ancestry ambiguous.
	 *
	 * The 'parent' argument, when used, should be set to the integer of a term ID.  Its default is
	 * the empty string '', which has a different meaning from the integer 0.
	 * If set to an integer value, all returned terms will have as an immediate
	 * ancestor the term whose ID is specified by that integer according to the given taxonomy.
	 * The 'parent' argument is different from 'child_of' in that a term X is considered a 'parent'
	 * of term Y only if term X is the father of term Y, not its grandfather or great-grandfather, etc.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses wp_parse_args() Merges the defaults with those defined by $args and allows for strings.
	 *
	 * @param string|array Taxonomy name or list of Taxonomy names
	 * @param string|array $args The values of what to search for when returning terms
	 * @return array|WP_Error List of Term Objects and their children. Will return WP_Error, if any of $taxonomies do not exist.
	 */
	function &get_terms($taxonomies, $args = '') {
		$empty_array = array();

		$single_taxonomy = false;
		if ( !is_array($taxonomies) ) {
			$single_taxonomy = true;
			$taxonomies = array($taxonomies);
		}

		foreach ( (array) $taxonomies as $taxonomy ) {
			if ( ! $this->is_taxonomy($taxonomy) ) {
				$error = & new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));
				return $error;
			}
		}

		$in_taxonomies = "'" . implode("', '", $taxonomies) . "'";

		$defaults = array('orderby' => 'name', 'order' => 'ASC',
			'hide_empty' => true, 'exclude' => '', 'exclude_tree' => '', 'include' => '',
			'number' => '', 'fields' => 'all', 'slug' => '', 'parent' => '',
			'hierarchical' => true, 'child_of' => 0, 'get' => '', 'name__like' => '',
			'pad_counts' => false, 'offset' => '', 'search' => '');
		$args = wp_parse_args( $args, $defaults );
		$args['number'] = absint( $args['number'] );
		$args['offset'] = absint( $args['offset'] );
		if ( !$single_taxonomy || !$this->is_taxonomy_hierarchical($taxonomies[0]) ||
			'' !== $args['parent'] ) {
			$args['child_of'] = 0;
			$args['hierarchical'] = false;
			$args['pad_counts'] = false;
		}

		if ( 'all' == $args['get'] ) {
			$args['child_of'] = 0;
			$args['hide_empty'] = 0;
			$args['hierarchical'] = false;
			$args['pad_counts'] = false;
		}
		extract($args, EXTR_SKIP);

		if ( $child_of ) {
			$hierarchy = $this->_get_term_hierarchy($taxonomies[0]);
			if ( !isset($hierarchy[$child_of]) )
				return $empty_array;
		}

		if ( $parent ) {
			$hierarchy = $this->_get_term_hierarchy($taxonomies[0]);
			if ( !isset($hierarchy[$parent]) )
				return $empty_array;
		}

		// $args can be whatever, only use the args defined in defaults to compute the key
		$filter_key = ( has_filter('list_terms_exclusions') ) ? serialize($GLOBALS['wp_filter']['list_terms_exclusions']) : '';
		$key = md5( serialize( compact(array_keys($defaults)) ) . serialize( $taxonomies ) . $filter_key );
		$last_changed = wp_cache_get('last_changed', 'terms');
		if ( !$last_changed ) {
			$last_changed = time();
			wp_cache_set('last_changed', $last_changed, 'terms');
		}
		$cache_key = "get_terms:$key:$last_changed";
		$cache = wp_cache_get( $cache_key, 'terms' );
		if ( false !== $cache ) {
			$cache = apply_filters('get_terms', $cache, $taxonomies, $args);
			return $cache;
		}

		$_orderby = strtolower($orderby);
		if ( 'count' == $_orderby )
			$orderby = 'tt.count';
		else if ( 'name' == $_orderby )
			$orderby = 't.name';
		else if ( 'slug' == $_orderby )
			$orderby = 't.slug';
		else if ( 'term_group' == $_orderby )
			$orderby = 't.term_group';
		elseif ( empty($_orderby) || 'id' == $_orderby )
			$orderby = 't.term_id';

		$orderby = apply_filters( 'get_terms_orderby', $orderby, $args );

		$where = '';
		$inclusions = '';
		if ( !empty($include) ) {
			$exclude = '';
			$exclude_tree = '';
			$interms = preg_split('/[\s,]+/',$include);
			if ( count($interms) ) {
				foreach ( (array) $interms as $interm ) {
					if (empty($inclusions))
						$inclusions = ' AND ( t.term_id = ' . intval($interm) . ' ';
					else
						$inclusions .= ' OR t.term_id = ' . intval($interm) . ' ';
				}
			}
		}

		if ( !empty($inclusions) )
			$inclusions .= ')';
		$where .= $inclusions;

		$exclusions = '';
		if ( ! empty( $exclude_tree ) ) {
			$excluded_trunks = preg_split('/[\s,]+/',$exclude_tree);
			foreach( (array) $excluded_trunks as $extrunk ) {
				$excluded_children = (array) $this->get_terms($taxonomies[0], array('child_of' => intval($extrunk), 'fields' => 'ids'));
				$excluded_children[] = $extrunk;
				foreach( (array) $excluded_children as $exterm ) {
					if ( empty($exclusions) )
						$exclusions = ' AND ( t.term_id <> ' . intval($exterm) . ' ';
					else
						$exclusions .= ' AND t.term_id <> ' . intval($exterm) . ' ';

				}
			}
		}
		if ( !empty($exclude) ) {
			$exterms = preg_split('/[\s,]+/',$exclude);
			if ( count($exterms) ) {
				foreach ( (array) $exterms as $exterm ) {
					if ( empty($exclusions) )
						$exclusions = ' AND ( t.term_id <> ' . intval($exterm) . ' ';
					else
						$exclusions .= ' AND t.term_id <> ' . intval($exterm) . ' ';
				}
			}
		}

		if ( !empty($exclusions) )
			$exclusions .= ')';
		$exclusions = apply_filters('list_terms_exclusions', $exclusions, $args );
		$where .= $exclusions;

		if ( !empty($slug) ) {
			$slug =  $this->sanitize_term_slug($slug);
			$where .= " AND t.slug = '$slug'";
		}

		if ( !empty($name__like) )
			$where .= " AND t.name LIKE '{$name__like}%'";

		if ( '' !== $parent ) {
			$parent = (int) $parent;
			$where .= " AND tt.parent = '$parent'";
		}

		if ( $hide_empty && !$hierarchical )
			$where .= ' AND tt.count > 0';

		// don't limit the query results when we have to descend the family tree
		if ( ! empty($number) && ! $hierarchical && empty( $child_of ) && '' === $parent ) {
			if( $offset )
				$limit = 'LIMIT ' . $offset . ',' . $number;
			else
				$limit = 'LIMIT ' . $number;

		} else
			$limit = '';

		if ( !empty($search) ) {
			$search = like_escape($search);
			$where .= " AND (t.name LIKE '%$search%')";
		}

		if ( !in_array( $fields, array( 'all', 'ids', 'names', 'tt_ids' ) ) )
			$fields = 'all';

		$selects = array();
		if ( 'all' == $fields )
			$selects = array('t.*', 'tt.*');
		else if ( 'ids' == $fields )
			$selects = array('t.term_id', 'tt.parent', 'tt.count');
		else if ( 'names' == $fields )
			$selects = array('t.term_id', 'tt.parent', 'tt.count', 't.name');
	        $select_this = implode(', ', apply_filters( 'get_terms_fields', $selects, $args ));

		$query = "SELECT $select_this FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} AS tt ON t.term_id = tt.term_id WHERE tt.taxonomy IN ($in_taxonomies) $where ORDER BY $orderby $order $limit";

		$terms = $this->db->get_results($query);
		if ( 'all' == $fields ) {
			$this->update_term_cache($terms);
		}

		if ( empty($terms) ) {
			wp_cache_add( $cache_key, array(), 'terms' );
			$terms = apply_filters('get_terms', array(), $taxonomies, $args);
			return $terms;
		}

		if ( $child_of || $hierarchical ) {
			$children = $this->_get_term_hierarchy($taxonomies[0]);
			if ( ! empty($children) )
				$terms = & $this->_get_term_children($child_of, $terms, $taxonomies[0]);
		}

		// Update term counts to include children.
		if ( $pad_counts )
			$this->_pad_term_counts($terms, $taxonomies[0]);

		// Make sure we show empty categories that have children.
		if ( $hierarchical && $hide_empty && is_array($terms) ) {
			foreach ( $terms as $k => $term ) {
				if ( ! $term->count ) {
					$children = $this->_get_term_children($term->term_id, $terms, $taxonomies[0]);
					if( is_array($children) )
						foreach ( $children as $child )
							if ( $child->count )
								continue 2;

					// It really is empty
					unset($terms[$k]);
				}
			}
		}
		reset ( $terms );

		$_terms = array();
		if ( 'ids' == $fields ) {
			while ( $term = array_shift($terms) )
				$_terms[] = $term->term_id;
			$terms = $_terms;
		} elseif ( 'names' == $fields ) {
			while ( $term = array_shift($terms) )
				$_terms[] = $term->name;
			$terms = $_terms;
		}

		if ( 0 < $number && intval(@count($terms)) > $number ) {
			$terms = array_slice($terms, $offset, $number);
		}

		wp_cache_add( $cache_key, $terms, 'terms' );

		$terms = apply_filters('get_terms', $terms, $taxonomies, $args);
		return $terms;
	}

	/**
	 * Check if Term exists.
	 *
	 * Returns the index of a defined term, or 0 (false) if the term doesn't exist.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param int|string $term The term to check
	 * @param string $taxonomy The taxonomy name to use
	 * @param int $parent ID of parent term under which to confine the exists search.
	 * @return mixed Get the term id or Term Object, if exists.
	 */
	function is_term($term, $taxonomy = '', $parent = 0) {
		$select = "SELECT term_id FROM {$this->db->terms} as t WHERE ";
		$tax_select = "SELECT tt.term_id, tt.term_taxonomy_id FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} as tt ON tt.term_id = t.term_id WHERE ";
		
		if ( is_int($term) ) {
			if ( 0 == $term )
				return 0;
			$where = 't.term_id = %d';
			if ( !empty($taxonomy) )
				return $this->db->get_row( $this->db->prepare( $tax_select . $where . " AND tt.taxonomy = %s", $term, $taxonomy ), ARRAY_A );
			else
				return $this->db->get_var( $this->db->prepare( $select . $where, $term ) );
		}

		$term = trim( stripslashes( $term ) );

		if ( '' === $slug = $this->sanitize_term_slug($term) )
			return 0;

		$where = 't.slug = %s';
		$else_where = 't.name = %s';
		$where_fields = array($slug);
		$else_where_fields = array($term);
		if ( !empty($taxonomy) ) {
			$parent = (int) $parent;
			if ( $parent > 0 ) {
				$where_fields[] = $parent;
				$else_where_fields[] = $parent;
				$where .= ' AND tt.parent = %d';
				$else_where .= ' AND tt.parent = %d';
			}

			$where_fields[] = $taxonomy;
			$else_where_fields[] = $taxonomy;

			if ( $result = $this->db->get_row( $this->db->prepare("SELECT tt.term_id, tt.term_taxonomy_id FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} as tt ON tt.term_id = t.term_id WHERE $where AND tt.taxonomy = %s", $where_fields), ARRAY_A) )
				return $result;

			return $this->db->get_row( $this->db->prepare("SELECT tt.term_id, tt.term_taxonomy_id FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} as tt ON tt.term_id = t.term_id WHERE $else_where AND tt.taxonomy = %s", $else_where_fields), ARRAY_A);
		}

		if ( $result = $this->db->get_var( $this->db->prepare("SELECT term_id FROM {$this->db->terms} as t WHERE $where", $where_fields) ) )
			return $result;

		return $this->db->get_var( $this->db->prepare("SELECT term_id FROM {$this->db->terms} as t WHERE $else_where", $else_where_fields) );
	}

	function sanitize_term_slug( $title, $taxonomy = '', $term_id = 0 ) {
		return apply_filters( 'pre_term_slug', $title, $taxonomy, $term_id );
	}

	function format_to_edit( $text ) {
		return format_to_edit( $text );
	}

	/**
	 * Sanitize Term all fields
	 *
	 * Relies on sanitize_term_field() to sanitize the term. The difference
	 * is that this function will sanitize <strong>all</strong> fields. The
	 * context is based on sanitize_term_field().
	 *
	 * The $term is expected to be either an array or an object.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses $this->sanitize_term_field Used to sanitize all fields in a term
	 *
	 * @param array|object $term The term to check
	 * @param string $taxonomy The taxonomy name to use
	 * @param string $context Default is 'display'.
	 * @return array|object Term with all fields sanitized
	 */
	function sanitize_term($term, $taxonomy, $context = 'display') {

		if ( 'raw' == $context )
			return $term;

		$fields = array('term_id', 'name', 'description', 'slug', 'count', 'parent', 'term_group');

		$do_object = false;
		if ( is_object($term) )
			$do_object = true;

		$term_id = $do_object ? $term->term_id : (isset($term['term_id']) ? $term['term_id'] : 0);

		foreach ( (array) $fields as $field ) {
			if ( $do_object ) {
				if ( isset($term->$field) )
					$term->$field = $this->sanitize_term_field($field, $term->$field, $term_id, $taxonomy, $context);
			} else {
				if ( isset($term[$field]) )
					$term[$field] = $this->sanitize_term_field($field, $term[$field], $term_id, $taxonomy, $context);
			}
		}

		if ( $do_object )
			$term->filter = $context;
		else
			$term['filter'] = $context;

		return $term;
	}

	/**
	 * Cleanse the field value in the term based on the context.
	 *
	 * Passing a term field value through the function should be assumed to have
	 * cleansed the value for whatever context the term field is going to be used.
	 *
	 * If no context or an unsupported context is given, then default filters will
	 * be applied.
	 *
	 * There are enough filters for each context to support a custom filtering
	 * without creating your own filter function. Simply create a function that
	 * hooks into the filter you need.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param string $field Term field to sanitize
	 * @param string $value Search for this term value
	 * @param int $term_id Term ID
	 * @param string $taxonomy Taxonomy Name
	 * @param string $context Either edit, db, display, attribute, or js.
	 * @return mixed sanitized field
	 */
	function sanitize_term_field($field, $value, $term_id, $taxonomy, $context) {
		if ( 'parent' == $field  || 'term_id' == $field || 'count' == $field || 'term_group' == $field ) {
			$value = (int) $value;
			if ( $value < 0 )
				$value = 0;
		}

		if ( 'raw' == $context )
			return $value;

		if ( 'edit' == $context ) {
			$value = apply_filters("edit_term_$field", $value, $term_id, $taxonomy);
			$value = apply_filters("edit_${taxonomy}_$field", $value, $term_id);
			if ( 'description' == $field )
				$value = $this->format_to_edit($value);
			else
				$value = esc_attr($value);
		} else if ( 'db' == $context ) {
			$value = apply_filters("pre_term_$field", $value, $taxonomy);
			$value = apply_filters("pre_${taxonomy}_$field", $value);
			// WP DIFF
		} else if ( 'rss' == $context ) {
			$value = apply_filters("term_${field}_rss", $value, $taxonomy);
			$value = apply_filters("${taxonomy}_${field}_rss", $value);
		} else {
			// Use display filters by default.
			$value = apply_filters("term_$field", $value, $term_id, $taxonomy, $context);
			$value = apply_filters("${taxonomy}_$field", $value, $term_id, $context);
		}

		if ( 'attribute' == $context )
			$value = esc_attr($value);
		else if ( 'js' == $context )
			$value = esc_js($value);

		return $value;
	}

	/**
	 * Count how many terms are in Taxonomy.
	 *
	 * Default $args is 'ignore_empty' which can be <code>'ignore_empty=true'</code>
	 * or <code>array('ignore_empty' => true);</code>.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses wp_parse_args() Turns strings into arrays and merges defaults into an array.
	 *
	 * @param string $taxonomy Taxonomy name
	 * @param array|string $args Overwrite defaults
	 * @return int How many terms are in $taxonomy
	 */
	function count_terms( $taxonomy, $args = array() ) {
		$defaults = array('ignore_empty' => false);
		$args = wp_parse_args($args, $defaults);
		extract($args, EXTR_SKIP);

		$where = '';
		if ( $ignore_empty )
			$where = 'AND count > 0';

		return $this->db->get_var( $this->db->prepare( "SELECT COUNT(*) FROM {$this->db->term_taxonomy} WHERE taxonomy = %s $where", $taxonomy ) );
	}

	/**
	 * Will unlink the term from the taxonomy.
	 *
	 * Will remove the term's relationship to the taxonomy, not the term or taxonomy
	 * itself. The term and taxonomy will still exist. Will require the term's
	 * object ID to perform the operation.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param int $object_id The term Object Id that refers to the term
	 * @param string|array $taxonomy List of Taxonomy Names or single Taxonomy name.
	 */
	function delete_object_term_relationships( $object_id, $taxonomies ) {
		$object_id = (int) $object_id;

		if ( !is_array($taxonomies) )
			$taxonomies = array($taxonomies);

		foreach ( (array) $taxonomies as $taxonomy ) {
			$terms = $this->get_object_terms($object_id, $taxonomy, array('fields' => 'tt_ids'));
			$in_terms = "'" . implode("', '", $terms) . "'";
			$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->term_relationships} WHERE object_id = %d AND term_taxonomy_id IN ($in_terms)", $object_id ) );
			$this->update_term_count($terms, $taxonomy);
		}
	}

	/**
	 * Removes a term from the database.
	 *
	 * If the term is a parent of other terms, then the children will be updated to
	 * that term's parent.
	 *
	 * The $args 'default' will only override the terms found, if there is only one
	 * term found. Any other and the found terms are used.
	 *
	 * The $args 'force_default' will force the term supplied as default to be
	 * assigned even if the object was not going to be termless
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses do_action() Calls both 'delete_term' and 'delete_$taxonomy' action
	 *  hooks, passing term object, term id. 'delete_term' gets an additional
	 *  parameter with the $taxonomy parameter.
	 *
	 * @param int $term Term ID
	 * @param string $taxonomy Taxonomy Name
	 * @param array|string $args Optional. Change 'default' term id and override found term ids.
	 * @return bool|WP_Error Returns false if not term; true if completes delete action.
	 */
	function delete_term( $term, $taxonomy, $args = array() ) {
		$term = (int) $term;

		if ( ! $ids = $this->is_term($term, $taxonomy) )
			return false;
		if ( is_wp_error( $ids ) )
			return $ids;

		$tt_id = $ids['term_taxonomy_id'];

		$defaults = array();
		$args = wp_parse_args($args, $defaults);
		extract($args, EXTR_SKIP);

		if ( isset($default) ) {
			$default = (int) $default;
			if ( !$this->is_term($default, $taxonomy) )
				unset($default);
		}

		// Update children to point to new parent
		if ( $this->is_taxonomy_hierarchical($taxonomy) ) {
			$term_obj = $this->get_term($term, $taxonomy);
			if ( is_wp_error( $term_obj ) )
				return $term_obj;
			$parent = $term_obj->parent;

			$this->db->update( $this->db->term_taxonomy, compact( 'parent' ), array( 'parent' => $term_obj->term_id ) + compact( 'taxonomy' ) );
		}

		$objects = $this->db->get_col( $this->db->prepare( "SELECT object_id FROM {$this->db->term_relationships} WHERE term_taxonomy_id = %d", $tt_id ) );

		foreach ( (array) $objects as $object ) {
			$terms = $this->get_object_terms($object, $taxonomy, array('fields' => 'ids', 'orderby' => 'none'));
			if ( 1 == count($terms) && isset($default) ) {
				$terms = array($default);
			} else {
				$terms = array_diff($terms, array($term));
				if (isset($default) && isset($force_default) && $force_default)
					$terms = array_merge($terms, array($default));
			}
			$terms = array_map('intval', $terms);
			$this->set_object_terms($object, $terms, $taxonomy);
		}

		$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->term_taxonomy} WHERE term_taxonomy_id = %d", $tt_id ) );

		// Delete the term if no taxonomies use it.
		if ( !$this->db->get_var( $this->db->prepare( "SELECT COUNT(*) FROM {$this->db->term_taxonomy} WHERE term_id = %d", $term) ) )
			$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->terms} WHERE term_id = %d", $term) );

		$this->clean_term_cache($term, $taxonomy);

		do_action('delete_term', $term, $tt_id, $taxonomy);
		do_action("delete_$taxonomy", $term, $tt_id);

		return true;
	}

	/**
	 * Retrieves the terms associated with the given object(s), in the supplied taxonomies.
	 *
	 * The following information has to do the $args parameter and for what can be
	 * contained in the string or array of that parameter, if it exists.
	 *
	 * The first argument is called, 'orderby' and has the default value of 'name'.
	 * The other value that is supported is 'count'.
	 *
	 * The second argument is called, 'order' and has the default value of 'ASC'.
	 * The only other value that will be acceptable is 'DESC'.
	 *
	 * The final argument supported is called, 'fields' and has the default value of
	 * 'all'. There are multiple other options that can be used instead. Supported
	 * values are as follows: 'all', 'ids', 'names', and finally
	 * 'all_with_object_id'.
	 *
	 * The fields argument also decides what will be returned. If 'all' or
	 * 'all_with_object_id' is choosen or the default kept intact, then all matching
	 * terms objects will be returned. If either 'ids' or 'names' is used, then an
	 * array of all matching term ids or term names will be returned respectively.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param int|array $object_id The id of the object(s) to retrieve.
	 * @param string|array $taxonomies The taxonomies to retrieve terms from.
	 * @param array|string $args Change what is returned
	 * @return array|WP_Error The requested term data or empty array if no terms found. WP_Error if $taxonomy does not exist.
	 */
	function get_object_terms($object_ids, $taxonomies, $args = array()) {
		if ( !is_array($taxonomies) )
			$taxonomies = array($taxonomies);

		foreach ( (array) $taxonomies as $taxonomy ) {
			if ( !$this->is_taxonomy($taxonomy) )
				return new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));
		}

		if ( !is_array($object_ids) )
			$object_ids = array($object_ids);
		$object_ids = array_map('intval', $object_ids);

		$defaults = array('orderby' => 'name', 'order' => 'ASC', 'fields' => 'all');
		$args = wp_parse_args( $args, $defaults );

		$terms = array();
		if ( count($taxonomies) > 1 ) {
			foreach ( $taxonomies as $index => $taxonomy ) {
				$t = $this->get_taxonomy($taxonomy);
				if ( isset($t->args) && is_array($t->args) && $args != array_merge($args, $t->args) ) {
					unset($taxonomies[$index]);
					$terms = array_merge($terms, $this->get_object_terms($object_ids, $taxonomy, array_merge($args, $t->args)));
				}
			}
		} else {
			$t = $this->get_taxonomy($taxonomies[0]);
			if ( isset($t->args) && is_array($t->args) )
				$args = array_merge($args, $t->args);
		}

		extract($args, EXTR_SKIP);

		if ( 'count' == $orderby )
			$orderby = 'tt.count';
		else if ( 'name' == $orderby )
			$orderby = 't.name';
		else if ( 'slug' == $orderby )
			$orderby = 't.slug';
		else if ( 'term_group' == $orderby )
			$orderby = 't.term_group';
		else if ( 'term_order' == $orderby )
			$orderby = 'tr.term_order';
		else if ( 'none' == $orderby ) {
			$orderby = '';
			$order = '';
		} else {
			$orderby = 't.term_id';
		}

		// tt_ids queries can only be none or tr.term_taxonomy_id
		if ( ('tt_ids' == $fields) && !empty($orderby) )
			$orderby = 'tr.term_taxonomy_id';

		if ( !empty($orderby) )
			$orderby = "ORDER BY $orderby";

		$taxonomies = "'" . implode("', '", $taxonomies) . "'";
		$object_ids = implode(', ', $object_ids);

		$select_this = '';
		if ( 'all' == $fields )
			$select_this = 't.*, tt.*';
		else if ( 'ids' == $fields )
			$select_this = 't.term_id';
		else if ( 'names' == $fields )
			$select_this = 't.name';
		else if ( 'all_with_object_id' == $fields )
			$select_this = 't.*, tt.*, tr.object_id';

		$query = "SELECT $select_this FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} AS tt ON tt.term_id = t.term_id INNER JOIN {$this->db->term_relationships} AS tr ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tt.taxonomy IN ($taxonomies) AND tr.object_id IN ($object_ids) $orderby $order";

		if ( 'all' == $fields || 'all_with_object_id' == $fields ) {
			$terms = array_merge($terms, $this->db->get_results($query));
			$this->update_term_cache($terms);
		} else if ( 'ids' == $fields || 'names' == $fields ) {
			$terms = array_merge($terms, $this->db->get_col($query));
		} else if ( 'tt_ids' == $fields ) {
			$terms = $this->db->get_col("SELECT tr.term_taxonomy_id FROM {$this->db->term_relationships} AS tr INNER JOIN {$this->db->term_taxonomy} AS tt ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tr.object_id IN ($object_ids) AND tt.taxonomy IN ($taxonomies) $orderby $order");
		}

		if ( ! $terms )
			$terms = array();

		return apply_filters('wp_get_object_terms', $terms, $object_ids, $taxonomies, $args);
	}

	/**
	 * Adds a new term to the database. Optionally marks it as an alias of an existing term.
	 *
	 * Error handling is assigned for the nonexistance of the $taxonomy and $term
	 * parameters before inserting. If both the term id and taxonomy exist
	 * previously, then an array will be returned that contains the term id and the
	 * contents of what is returned. The keys of the array are 'term_id' and
	 * 'term_taxonomy_id' containing numeric values.
	 *
	 * It is assumed that the term does not yet exist or the above will apply. The
	 * term will be first added to the term table and then related to the taxonomy
	 * if everything is well. If everything is correct, then several actions will be
	 * run prior to a filter and then several actions will be run after the filter
	 * is run.
	 *
	 * The arguments decide how the term is handled based on the $args parameter.
	 * The following is a list of the available overrides and the defaults.
	 *
	 * 'alias_of'. There is no default, but if added, expected is the slug that the
	 * term will be an alias of. Expected to be a string.
	 *
	 * 'description'. There is no default. If exists, will be added to the database
	 * along with the term. Expected to be a string.
	 *
	 * 'parent'. Expected to be numeric and default is 0 (zero). Will assign value
	 * of 'parent' to the term.
	 *
	 * 'slug'. Expected to be a string. There is no default.
	 *
	 * If 'slug' argument exists then the slug will be checked to see if it is not
	 * a valid term. If that check succeeds (it is not a valid term), then it is
	 * added and the term id is given. If it fails, then a check is made to whether
	 * the taxonomy is hierarchical and the parent argument is not empty. If the
	 * second check succeeds, the term will be inserted and the term id will be
	 * given.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses do_action() Calls 'create_term' hook with the term id and taxonomy id as parameters.
	 * @uses do_action() Calls 'create_$taxonomy' hook with term id and taxonomy id as parameters.
	 * @uses apply_filters() Calls 'term_id_filter' hook with term id and taxonomy id as parameters.
	 * @uses do_action() Calls 'created_term' hook with the term id and taxonomy id as parameters.
	 * @uses do_action() Calls 'created_$taxonomy' hook with term id and taxonomy id as parameters.
	 *
	 * @param int|string $term The term to add or update.
	 * @param string $taxonomy The taxonomy to which to add the term
	 * @param array|string $args Change the values of the inserted term
	 * @return array|WP_Error The Term ID and Term Taxonomy ID
	 */
	function insert_term( $term, $taxonomy, $args = array() ) {
		if ( !$this->is_taxonomy($taxonomy) )
			return new WP_Error('invalid_taxonomy', __('Invalid taxonomy'));

		if ( is_int($term) && 0 == $term )
			return new WP_Error('invalid_term_id', __('Invalid term ID'));

		if ( '' == trim($term) )
			return new WP_Error('empty_term_name', __('A name is required for this term'));

		$defaults = array( 'alias_of' => '', 'description' => '', 'parent' => 0, 'slug' => '');
		$args = wp_parse_args($args, $defaults);
		$args['name'] = $term;
		$args['taxonomy'] = $taxonomy;
		$args = $this->sanitize_term($args, $taxonomy, 'db');
		extract($args, EXTR_SKIP);

		// expected_slashed ($name)
		$name = stripslashes($name);
		$description = stripslashes($description);

		if ( empty($slug) )
			$slug = $this->sanitize_term_slug($name, $taxonomy);

		$term_group = 0;
		if ( $alias_of ) {
			$alias = $this->db->get_row( $this->db->prepare( "SELECT term_id, term_group FROM {$this->db->terms} WHERE slug = %s", $alias_of) );
			if ( $alias->term_group ) {
				// The alias we want is already in a group, so let's use that one.
				$term_group = $alias->term_group;
			} else {
				// The alias isn't in a group, so let's create a new one and firstly add the alias term to it.
				$term_group = $this->db->get_var("SELECT MAX(term_group) FROM {$this->db->terms}") + 1;
				$this->db->query( $this->db->prepare( "UPDATE {$this->db->terms} SET term_group = %d WHERE term_id = %d", $term_group, $alias->term_id ) );
			}
		}

		if ( ! $term_id = $this->is_term($slug) ) {
			if ( false === $this->db->insert( $this->db->terms, compact( 'name', 'slug', 'term_group' ) ) )
				return new WP_Error('db_insert_error', __('Could not insert term into the database'), $this->db->last_error);
			$term_id = (int) $this->db->insert_id;
		} else if ( $this->is_taxonomy_hierarchical($taxonomy) && !empty($parent) ) {
			// If the taxonomy supports hierarchy and the term has a parent, make the slug unique
			// by incorporating parent slugs.
			$slug = $this->unique_term_slug($slug, (object) $args);
			if ( false === $this->db->insert( $this->db->terms, compact( 'name', 'slug', 'term_group' ) ) )
				return new WP_Error('db_insert_error', __('Could not insert term into the database'), $this->db->last_error);
			$term_id = (int) $this->db->insert_id;
		}

		if ( empty($slug) ) {
			$slug = $this->sanitize_term_slug($slug, $taxonomy, $term_id);
			$this->db->update( $this->db->terms, compact( 'slug' ), compact( 'term_id' ) );
		}

		$tt_id = $this->db->get_var( $this->db->prepare( "SELECT tt.term_taxonomy_id FROM {$this->db->term_taxonomy} AS tt INNER JOIN {$this->db->terms} AS t ON tt.term_id = t.term_id WHERE tt.taxonomy = %s AND t.term_id = %d", $taxonomy, $term_id ) );

		if ( !empty($tt_id) )
			return array('term_id' => $term_id, 'term_taxonomy_id' => $tt_id);

		$this->db->insert( $this->db->term_taxonomy, compact( 'term_id', 'taxonomy', 'description', 'parent') + array( 'count' => 0 ) );
		$tt_id = (int) $this->db->insert_id;

		do_action("create_term", $term_id, $tt_id);
		do_action("create_$taxonomy", $term_id, $tt_id);

		$term_id = apply_filters('term_id_filter', $term_id, $tt_id);

		$this->clean_term_cache($term_id, $taxonomy);

		do_action("created_term", $term_id, $tt_id);
		do_action("created_$taxonomy", $term_id, $tt_id);

		return array('term_id' => $term_id, 'term_taxonomy_id' => $tt_id);
	}

	/**
	 * Create Term and Taxonomy Relationships.
	 *
	 * Relates an object (post, link etc) to a term and taxonomy type. Creates the
	 * term and taxonomy relationship if it doesn't already exist. Creates a term if
	 * it doesn't exist (using the slug).
	 *
	 * A relationship means that the term is grouped in or belongs to the taxonomy.
	 * A term has no meaning until it is given context by defining which taxonomy it
	 * exists under.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param int $object_id The object to relate to.
	 * @param array|int|string $term The slug or id of the term, will replace all existing
	 * related terms in this taxonomy.
	 * @param array|string $taxonomy The context in which to relate the term to the object.
	 * @param bool $append If false will delete difference of terms.
	 * @return array|WP_Error Affected Term IDs
	 */
	function set_object_terms($object_id, $terms, $taxonomy, $append = false) {
		$object_id = (int) $object_id;

		if ( !$this->is_taxonomy($taxonomy) )
			return new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));

		if ( !is_array($terms) )
			$terms = array($terms);

		if ( ! $append )
			$old_tt_ids = $this->get_object_terms($object_id, $taxonomy, array('fields' => 'tt_ids', 'orderby' => 'none'));

		$tt_ids = array();
		$term_ids = array();

		foreach ( (array) $terms as $term ) {
			if ( !strlen(trim($term)) )
				continue;

			if ( !$id = $this->is_term($term, $taxonomy) )
				$id = $this->insert_term($term, $taxonomy);
			if ( is_wp_error($id) )
				return $id;
			$term_ids[] = $id['term_id'];
			$id = $id['term_taxonomy_id'];
			$tt_ids[] = $id;

			if ( $this->db->get_var( $this->db->prepare( "SELECT term_taxonomy_id FROM {$this->db->term_relationships} WHERE object_id = %d AND term_taxonomy_id = %d", $object_id, $id ) ) )
				continue;
			$this->db->insert( $this->db->term_relationships, array( 'object_id' => $object_id, 'term_taxonomy_id' => $id ) );
		}

		$this->update_term_count($tt_ids, $taxonomy);

		if ( ! $append ) {
			$delete_terms = array_diff($old_tt_ids, $tt_ids);
			if ( $delete_terms ) {
				$in_delete_terms = "'" . implode("', '", $delete_terms) . "'";
				$this->db->query( $this->db->prepare("DELETE FROM {$this->db->term_relationships} WHERE object_id = %d AND term_taxonomy_id IN ($in_delete_terms)", $object_id) );
				$this->update_term_count($delete_terms, $taxonomy);
			}
		}

		$t = $this->get_taxonomy($taxonomy);
		if ( ! $append && isset($t->sort) && $t->sort ) {
			$values = array();
			$term_order = 0;
			$final_tt_ids = $this->get_object_terms($object_id, $taxonomy, 'fields=tt_ids');
			foreach ( $tt_ids as $tt_id )
				if ( in_array($tt_id, $final_tt_ids) )
					$values[] = $this->db->prepare( "(%d, %d, %d)", $object_id, $tt_id, ++$term_order);
			if ( $values )
				$this->db->query("INSERT INTO {$this->db->term_relationships} (object_id, term_taxonomy_id, term_order) VALUES " . join(',', $values) . " ON DUPLICATE KEY UPDATE term_order = VALUES(term_order)");
		}

		do_action('set_object_terms', $object_id, $terms, $tt_ids, $taxonomy, $append);
		return $tt_ids;
	}

	/**
	 * Will make slug unique, if it isn't already.
	 *
	 * The $slug has to be unique global to every taxonomy, meaning that one
	 * taxonomy term can't have a matching slug with another taxonomy term. Each
	 * slug has to be globally unique for every taxonomy.
	 *
	 * The way this works is that if the taxonomy that the term belongs to is
	 * heirarchical and has a parent, it will append that parent to the $slug.
	 *
	 * If that still doesn't return an unique slug, then it try to append a number
	 * until it finds a number that is truely unique.
	 *
	 * The only purpose for $term is for appending a parent, if one exists.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param string $slug The string that will be tried for a unique slug
	 * @param object $term The term object that the $slug will belong too
	 * @return string Will return a true unique slug.
	 */
	function unique_term_slug($slug, $term) {
		// If the taxonomy supports hierarchy and the term has a parent, make the slug unique
		// by incorporating parent slugs.
		if ( $this->is_taxonomy_hierarchical($term->taxonomy) && !empty($term->parent) ) {
			$the_parent = $term->parent;
			while ( ! empty($the_parent) ) {
				$parent_term = $this->get_term($the_parent, $term->taxonomy);
				if ( is_wp_error($parent_term) || empty($parent_term) )
					break;
					$slug .= '-' . $parent_term->slug;
				if ( empty($parent_term->parent) )
					break;
				$the_parent = $parent_term->parent;
			}
		}

		// If we didn't get a unique slug, try appending a number to make it unique.
		if ( !empty($args['term_id']) )
			$query = $this->db->prepare( "SELECT slug FROM {$this->db->terms} WHERE slug = %s AND term_id != %d", $slug, $args['term_id'] );
		else
			$query = $this->db->prepare( "SELECT slug FROM {$this->db->terms} WHERE slug = %s", $slug );

		if ( $this->db->get_var( $query ) ) {
			$num = 2;
			do {
				$alt_slug = $slug . "-$num";
				$num++;
				$slug_check = $this->db->get_var( $this->db->prepare( "SELECT slug FROM {$this->db->terms} WHERE slug = %s", $alt_slug ) );
			} while ( $slug_check );
			$slug = $alt_slug;
		}

		return $slug;
	}

	/**
	 * Update term based on arguments provided.
	 *
	 * The $args will indiscriminately override all values with the same field name.
	 * Care must be taken to not override important information need to update or
	 * update will fail (or perhaps create a new term, neither would be acceptable).
	 *
	 * Defaults will set 'alias_of', 'description', 'parent', and 'slug' if not
	 * defined in $args already.
	 *
	 * 'alias_of' will create a term group, if it doesn't already exist, and update
	 * it for the $term.
	 *
	 * If the 'slug' argument in $args is missing, then the 'name' in $args will be
	 * used. It should also be noted that if you set 'slug' and it isn't unique then
	 * a WP_Error will be passed back. If you don't pass any slug, then a unique one
	 * will be created for you.
	 *
	 * For what can be overrode in $args, check the term scheme can contain and stay
	 * away from the term keys.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses do_action() Will call both 'edit_term' and 'edit_$taxonomy' twice.
	 * @uses apply_filters() Will call the 'term_id_filter' filter and pass the term
	 *  id and taxonomy id.
	 *
	 * @param int $term_id The ID of the term
	 * @param string $taxonomy The context in which to relate the term to the object.
	 * @param array|string $args Overwrite term field values
	 * @return array|WP_Error Returns Term ID and Taxonomy Term ID
	 */
	function update_term( $term_id, $taxonomy, $args = array() ) {
		if ( !$this->is_taxonomy($taxonomy) )
			return new WP_Error('invalid_taxonomy', __('Invalid taxonomy'));

		$term_id = (int) $term_id;

		// First, get all of the original args
		$term = $this->get_term($term_id, $taxonomy, ARRAY_A);

		if ( is_wp_error( $term ) )
			return $term;

		// Merge old and new args with new args overwriting old ones.
		$args = array_merge($term, $args);

		$defaults = array( 'alias_of' => '', 'description' => '', 'parent' => 0, 'slug' => '');
		$args = wp_parse_args($args, $defaults);
		$args = $this->sanitize_term($args, $taxonomy, 'db');
		extract($args, EXTR_SKIP);

		// expected_slashed ($name)
		$name = stripslashes($name);
		$description = stripslashes($description);

		if ( '' == trim($name) )
			return new WP_Error('empty_term_name', __('A name is required for this term'));

		$empty_slug = false;
		if ( empty($slug) ) {
			$empty_slug = true;
			$slug = $this->sanitize_term_slug($name, $taxonomy, $term_id);
		}

		if ( $alias_of ) {
			$alias = $this->db->get_row( $this->db->prepare( "SELECT term_id, term_group FROM {$this->db->terms} WHERE slug = %s", $alias_of) );
			if ( $alias->term_group ) {
				// The alias we want is already in a group, so let's use that one.
				$term_group = $alias->term_group;
			} else {
				// The alias isn't in a group, so let's create a new one and firstly add the alias term to it.
				$term_group = $this->db->get_var("SELECT MAX(term_group) FROM {$this->db->terms}") + 1;
				$this->db->update( $this->db->terms, compact('term_group'), array( 'term_id' => $alias->term_id ) );
			}
		}

		// Check for duplicate slug
		$id = $this->db->get_var( $this->db->prepare( "SELECT term_id FROM {$this->db->terms} WHERE slug = %s", $slug ) );
		if ( $id && ($id != $term_id) ) {
			// If an empty slug was passed or the parent changed, reset the slug to something unique.
			// Otherwise, bail.
			if ( $empty_slug || ( $parent != $term->parent) )
				$slug = $this->unique_term_slug($slug, (object) $args);
			else
				return new WP_Error('duplicate_term_slug', sprintf(__('The slug &#8220;%s&#8221; is already in use by another term'), $slug));
		}

		$this->db->update($this->db->terms, compact( 'name', 'slug', 'term_group' ), compact( 'term_id' ) );

		if ( empty($slug) ) {
			$slug = $this->sanitize_term_slug($name, $taxonomy, $term_id);
			$this->db->update( $this->db->terms, compact( 'slug' ), compact( 'term_id' ) );
		}

		$tt_id = $this->db->get_var( $this->db->prepare( "SELECT tt.term_taxonomy_id FROM {$this->db->term_taxonomy} AS tt INNER JOIN {$this->db->terms} AS t ON tt.term_id = t.term_id WHERE tt.taxonomy = %s AND t.term_id = %d", $taxonomy, $term_id) );

		$this->db->update( $this->db->term_taxonomy, compact( 'term_id', 'taxonomy', 'description', 'parent' ), array( 'term_taxonomy_id' => $tt_id ) );

		do_action("edit_term", $term_id, $tt_id);
		do_action("edit_$taxonomy", $term_id, $tt_id);

		$term_id = apply_filters('term_id_filter', $term_id, $tt_id);

		$this->clean_term_cache($term_id, $taxonomy);

		do_action("edited_term", $term_id, $tt_id);
		do_action("edited_$taxonomy", $term_id, $tt_id);

		return array('term_id' => $term_id, 'term_taxonomy_id' => $tt_id);
	}

	/**
	 * Enable or disable term counting.
	 *
	 * @since 2.5.0
	 *
	 * @param bool $defer Optional. Enable if true, disable if false.
	 * @return bool Whether term counting is enabled or disabled.
	 */
	function defer_term_counting($defer=NULL) {
		static $_defer = false;

		if ( is_bool($defer) ) {
			$_defer = $defer;
			// flush any deferred counts
			if ( !$defer )
				$this->update_term_count( NULL, NULL, true );
		}

		return $_defer;
	}

	/**
	 * Updates the amount of terms in taxonomy.
	 *
	 * If there is a taxonomy callback applied, then it will be called for updating
	 * the count.
	 *
	 * The default action is to count what the amount of terms have the relationship
	 * of term ID. Once that is done, then update the database.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 * @uses $this->db
	 *
	 * @param int|array $terms The ID of the terms
	 * @param string $taxonomy The context of the term.
	 * @return bool If no terms will return false, and if successful will return true.
	 */
	function update_term_count( $terms, $taxonomy, $do_deferred=false ) {
		static $_deferred = array();

		if ( $do_deferred ) {
			foreach ( (array) array_keys($_deferred) as $tax ) {
				$this->update_term_count_now( $_deferred[$tax], $tax );
				unset( $_deferred[$tax] );
			}
		}

		if ( empty($terms) )
			return false;

		if ( !is_array($terms) )
			$terms = array($terms);

		if ( $this->defer_term_counting() ) {
			if ( !isset($_deferred[$taxonomy]) )
				$_deferred[$taxonomy] = array();
			$_deferred[$taxonomy] = array_unique( array_merge($_deferred[$taxonomy], $terms) );
			return true;
		}

		return $this->update_term_count_now( $terms, $taxonomy );
	}

	/**
	 * Perform term count update immediately.
	 *
	 * @since 2.5.0
	 *
	 * @param array $terms The term_taxonomy_id of terms to update.
	 * @param string $taxonomy The context of the term.
	 * @return bool Always true when complete.
	 */
	function update_term_count_now( $terms, $taxonomy ) {
		$terms = array_map('intval', $terms);

		$taxonomy = $this->get_taxonomy($taxonomy);
		if ( !empty($taxonomy->update_count_callback) ) {
			call_user_func($taxonomy->update_count_callback, $terms);
		} else {
			// Default count updater
			foreach ( (array) $terms as $term ) {
				$count = $this->db->get_var( $this->db->prepare( "SELECT COUNT(*) FROM {$this->db->term_relationships} WHERE term_taxonomy_id = %d", $term) );
				$this->db->update( $this->db->term_taxonomy, compact( 'count' ), array( 'term_taxonomy_id' => $term ) );
			}

		}

		$this->clean_term_cache($terms);

		return true;
	}

	//
	// Cache
	//

	/**
	 * Removes the taxonomy relationship to terms from the cache.
	 *
	 * Will remove the entire taxonomy relationship containing term $object_id. The
	 * term IDs have to exist within the taxonomy $object_type for the deletion to
	 * take place.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3
	 *
	 * @see $this->get_object_taxonomies() for more on $object_type
	 * @uses do_action() Will call action hook named, 'clean_object_term_cache' after completion.
	 *	Passes, function params in same order.
	 *
	 * @param int|array $object_ids Single or list of term object ID(s)
	 * @param string $object_type The taxonomy object type
	 */
	function clean_object_term_cache($object_ids, $object_type) {
		if ( !is_array($object_ids) )
			$object_ids = array($object_ids);

		foreach ( $object_ids as $id )
			foreach ( $this->get_object_taxonomies($object_type) as $taxonomy )
				wp_cache_delete($id, "{$taxonomy}_relationships");

		do_action('clean_object_term_cache', $object_ids, $object_type);
	}

	/**
	 * Will remove all of the term ids from the cache.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param int|array $ids Single or list of Term IDs
	 * @param string $taxonomy Can be empty and will assume tt_ids, else will use for context.
	 */
	function clean_term_cache($ids, $taxonomy = '') {
		static $cleaned = array();

		if ( !is_array($ids) )
			$ids = array($ids);

		$taxonomies = array();
		// If no taxonomy, assume tt_ids.
		if ( empty($taxonomy) ) {
			$tt_ids = implode( ',', array_map( 'intval', $ids ) );
			$terms = $this->db->get_results("SELECT term_id, term_taxonomy_id, taxonomy FROM {$this->db->term_taxonomy} WHERE term_taxonomy_id IN ($tt_ids)");
			foreach ( (array) $terms as $term ) {
				$taxonomies[] = $term->taxonomy;
				wp_cache_delete($term->term_id, $term->taxonomy);
				wp_cache_delete($term->term_taxonomy_id, "{$term->taxonomy}:tt_id");
			}
			$taxonomies = array_unique($taxonomies);
		} else {
			$tt_ids = implode( ',', array_map( 'intval', $ids ) );
			$terms = $this->db->get_results("SELECT term_id, term_taxonomy_id FROM {$this->db->term_taxonomy} WHERE term_id IN ($tt_ids)");
			foreach ( (array) $terms as $term ) {
				wp_cache_delete($term->term_id, $taxonomy);
				wp_cache_delete($term->term_taxonomy_id, "$taxonomy:tt_id");
			}
			$taxonomies = array($taxonomy);
		}

		foreach ( $taxonomies as $taxonomy ) {
			if ( isset($cleaned[$taxonomy]) )
				continue;
			$cleaned[$taxonomy] = true;
			wp_cache_delete('all_ids', $taxonomy);
			wp_cache_delete('get', $taxonomy);
			$this->delete_children_cache($taxonomy);
		}

		wp_cache_delete('get_terms', 'terms');

		do_action('clean_term_cache', $ids, $taxonomy);
	}

	/**
	 * Retrieves the taxonomy relationship to the term object id.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @uses wp_cache_get() Retrieves taxonomy relationship from cache
	 *
	 * @param int|array $id Term object ID
	 * @param string $taxonomy Taxonomy Name
	 * @return bool|array Empty array if $terms found, but not $taxonomy. False if nothing is in cache for $taxonomy and $id.
	 */
	function &get_object_term_cache($id, $taxonomy) {
		$cache = wp_cache_get($id, "{$taxonomy}_relationships");
		return $cache;
	}

	/**
	 * Updates the cache for Term ID(s).
	 *
	 * Will only update the cache for terms not already cached.
	 *
	 * The $object_ids expects that the ids be separated by commas, if it is a
	 * string.
	 *
	 * It should be noted that update_object_term_cache() is very time extensive. It
	 * is advised that the function is not called very often or at least not for a
	 * lot of terms that exist in a lot of taxonomies. The amount of time increases
	 * for each term and it also increases for each taxonomy the term belongs to.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 * @uses $this->get_object_terms() Used to get terms from the database to update
	 *
	 * @param string|array $object_ids Single or list of term object ID(s)
	 * @param string $object_type The taxonomy object type
	 * @return null|bool Null value is given with empty $object_ids. False if 
	 */
	function update_object_term_cache($object_ids, $object_type) {
		if ( empty($object_ids) )
			return;

		if ( !is_array($object_ids) )
			$object_ids = explode(',', $object_ids);

		$object_ids = array_map('intval', $object_ids);

		$taxonomies = $this->get_object_taxonomies($object_type);

		$ids = array();
		foreach ( (array) $object_ids as $id ) {
			foreach ( $taxonomies as $taxonomy ) {
				if ( false === wp_cache_get($id, "{$taxonomy}_relationships") ) {
					$ids[] = $id;
					break;
				}
			}
		}

		if ( empty( $ids ) )
			return false;

		$terms = $this->get_object_terms($ids, $taxonomies, 'fields=all_with_object_id');

		$object_terms = array();
		foreach ( (array) $terms as $term )
			$object_terms[$term->object_id][$term->taxonomy][$term->term_id] = $term;

		foreach ( $ids as $id ) {
			foreach ( $taxonomies  as $taxonomy ) {
				if ( ! isset($object_terms[$id][$taxonomy]) ) {
					if ( !isset($object_terms[$id]) )
						$object_terms[$id] = array();
					$object_terms[$id][$taxonomy] = array();
				}
			}
		}

		foreach ( $object_terms as $id => $value ) {
			foreach ( $value as $taxonomy => $terms ) {
				wp_cache_set($id, $terms, "{$taxonomy}_relationships");
			}
		}
	}

	/**
	 * Updates Terms to Taxonomy in cache.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @since 2.3.0
	 *
	 * @param array $terms List of Term objects to change
	 * @param string $taxonomy Optional. Update Term to this taxonomy in cache
	 */
	function update_term_cache($terms, $taxonomy = '') {
		foreach ( (array) $terms as $term ) {
			$term_taxonomy = $taxonomy;
			if ( empty($term_taxonomy) )
				$term_taxonomy = $term->taxonomy;

			wp_cache_add($term->term_id, $term, $term_taxonomy);
			wp_cache_add($term->term_taxonomy_id, $term->term_id, "$term_taxonomy:tt_id");
		}
	}

	//
	// Private
	//

	/**
	 * Retrieves children of taxonomy as Term IDs.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @access private
	 * @since 2.3.0
	 *
	 * @uses backpress_update_option() Stores all of the children in "$taxonomy_children"
	 *  option. That is the name of the taxonomy, immediately followed by '_children'.
	 *
	 * @param string $taxonomy Taxonomy Name
	 * @return array Empty if $taxonomy isn't hierarachical or returns children as Term IDs.
	 */
	function _get_term_hierarchy($taxonomy) {
		if ( !$this->is_taxonomy_hierarchical($taxonomy) )
			return array();
		$children = $this->get_children_cache($taxonomy);
		if ( is_array($children) )
			return $children;

		$children = array();
		$terms = $this->get_terms($taxonomy, 'get=all');
		foreach ( $terms as $term ) {
			if ( $term->parent > 0 )
				$children[$term->parent][] = $term->term_id;
		}
		$this->set_children_cache($taxonomy, $children);

		return $children;
	}

	/**
	 * Get the subset of $terms that are descendants of $term_id.
	 *
	 * If $terms is an array of objects, then _get_term_children returns an array of objects.
	 * If $terms is an array of IDs, then _get_term_children returns an array of IDs.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @access private
	 * @since 2.3.0
	 *
	 * @param int $term_id The ancestor term: all returned terms should be descendants of $term_id.
	 * @param array $terms The set of terms---either an array of term objects or term IDs---from which those that are descendants of $term_id will be chosen.
	 * @param string $taxonomy The taxonomy which determines the hierarchy of the terms.
	 * @return array The subset of $terms that are descendants of $term_id.
	 */
	function &_get_term_children($term_id, $terms, $taxonomy) {
		$empty_array = array();
		if ( empty($terms) )
			return $empty_array;

		$term_list = array();
		$has_children = $this->_get_term_hierarchy($taxonomy);

		if  ( ( 0 != $term_id ) && ! isset($has_children[$term_id]) )
			return $empty_array;

		foreach ( (array) $terms as $term ) {
			$use_id = false;
			if ( !is_object($term) ) {
				$term = $this->get_term($term, $taxonomy);
				if ( is_wp_error( $term ) )
					return $term;
				$use_id = true;
			}

			if ( $term->term_id == $term_id )
				continue;

			if ( $term->parent == $term_id ) {
				if ( $use_id )
					$term_list[] = $term->term_id;
				else
					$term_list[] = $term;

				if ( !isset($has_children[$term->term_id]) )
					continue;

				if ( $children = $this->_get_term_children($term->term_id, $terms, $taxonomy) )
					$term_list = array_merge($term_list, $children);
			}
		}

		return $term_list;
	}

	/**
	 * Add count of children to parent count.
	 *
	 * Recalculates term counts by including items from child terms. Assumes all
	 * relevant children are already in the $terms argument.
	 *
	 * @package WordPress
	 * @subpackage Taxonomy
	 * @access private
	 * @since 2.3
	 *
	 * @param array $terms List of Term IDs
	 * @param string $taxonomy Term Context
	 * @return null Will break from function if conditions are not met.
	 */
	function _pad_term_counts(&$terms, $taxonomy) {
		return;
	}

	/**
	 * Determine if the given object is associated with any of the given terms.
	 *
	 * The given terms are checked against the object's terms' term_ids, names and slugs.
	 * Terms given as integers will only be checked against the object's terms' term_ids.
	 * If no terms are given, determines if object is associated with any terms in the given taxonomy.
	 *
	 * @since 2.7.0
	 * @uses WP_Taxonomy::get_object_term_cache()
	 * @uses WP_Taxonomy::get_object_terms()
	 *
	 * @param int $object_id.  ID of the object (post ID, link ID, ...)
	 * @param string $taxonomy.  Single taxonomy name
	 * @param int|string|array $terms Optional.  Term term_id, name, slug or array of said
	 * @return bool|WP_Error. WP_Error on input error.
	 */
	function is_object_in_term( $object_id, $taxonomy, $terms = null ) {
		if ( !$object_id = (int) $object_id )
			return new WP_Error( 'invalid_object', __( 'Invalid object ID' ) );

		$object_terms = $this->get_object_term_cache( $object_id, $taxonomy );
		if ( empty( $object_terms ) )
			 $object_terms = $this->get_object_terms( $object_id, $taxonomy );

		if ( is_wp_error( $object_terms ) )
			return $object_terms;
		if ( empty( $object_terms ) )
			return false;
		if ( empty( $terms ) )
			return ( !empty( $object_terms ) );

		$terms = (array) $terms;

		if ( $ints = array_filter( $terms, 'is_int' ) )
			$strs = array_diff( $terms, $ints );
		else
			$strs =& $terms;

		foreach ( $object_terms as $object_term ) {
			if ( $ints && in_array( $object_term->term_id, $ints ) ) return true; // If int, check against term_id
			if ( $strs ) {
				if ( in_array( $object_term->term_id, $strs ) ) return true;
				if ( in_array( $object_term->name, $strs ) )    return true;
				if ( in_array( $object_term->slug, $strs ) )    return true;
			}
		}

		return false;
	}

	function get_children_cache( $taxonomy ) { return false; }
	function set_children_cache( $taxonomy, $children ) {}
	function delete_children_cache( $taxonomy ) {}
}
