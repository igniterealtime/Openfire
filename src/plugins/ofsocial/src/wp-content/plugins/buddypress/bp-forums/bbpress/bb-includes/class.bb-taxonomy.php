<?php
/**
 * Taxonomy API
 *
 * @package bbPress
 * @subpackage Taxonomy
 * @since 1.0
 * @todo cache
 */
class BB_Taxonomy extends WP_Taxonomy
{
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
	 * @package bbPress
	 * @subpackage Taxonomy
	 * @since 1.0
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

		$defaults = array('order' => 'ASC', 'field' => 'term_id', 'user_id' => 0);
		$args = wp_parse_args( $args, $defaults );
		extract($args, EXTR_SKIP);

		if ( 'tt_id' == $field )
			$field = 'tt.term_taxonomy_id';
		else
			$field = 'tt.term_id';

		$order = ( 'desc' == strtolower($order) ) ? 'DESC' : 'ASC';
		$user_id = (int) $user_id;

		$terms = array_map('intval', $terms);

		$taxonomies = "'" . implode("', '", $taxonomies) . "'";
		$terms = "'" . implode("', '", $terms) . "'";

		$sql = "SELECT tr.object_id FROM {$this->db->term_relationships} AS tr INNER JOIN {$this->db->term_taxonomy} AS tt ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tt.taxonomy IN ($taxonomies) AND $field IN ($terms)";
		if ( $user_id )
			$sql .= " AND tr.user_id = '$user_id'";
		$sql .= " ORDER BY tr.object_id $order";

		$object_ids = $this->db->get_col( $sql );

		if ( ! $object_ids )
			return array();

		return $object_ids;
	}

	/**
	 * Will unlink the term from the taxonomy.
	 *
	 * Will remove the term's relationship to the taxonomy, not the term or taxonomy
	 * itself. The term and taxonomy will still exist. Will require the term's
	 * object ID to perform the operation.
	 *
	 * @package bbPress
	 * @subpackage Taxonomy
	 * @since 1.0
	 *
	 * @param int $object_id The term Object Id that refers to the term
	 * @param string|array $taxonomy List of Taxonomy Names or single Taxonomy name.
	 * @param int $user_id The ID of the user who created the relationship.
	 */
	function delete_object_term_relationships( $object_id, $taxonomies, $user_id = 0 ) {
		$object_id = (int) $object_id;
		$user_id = (int) $user_id;

		if ( !is_array($taxonomies) )
			$taxonomies = array($taxonomies);

		foreach ( (array) $taxonomies as $taxonomy ) {
			$terms = $this->get_object_terms($object_id, $taxonomy, array('fields' => 'tt_ids', 'user_id' => $user_id));
			$in_terms = "'" . implode("', '", $terms) . "'";
			$sql = "DELETE FROM {$this->db->term_relationships} WHERE object_id = %d AND term_taxonomy_id IN ($in_terms)";
			if ( $user_id )
				$sql .= " AND user_id = %d";
			$this->db->query( $this->db->prepare( $sql, $object_id, $user_id ) );
			$this->update_term_count($terms, $taxonomy);
		}
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
	 * @package bbPress
	 * @subpackage Taxonomy
	 * @since 1.0
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

		$defaults = array('orderby' => 'name', 'order' => 'ASC', 'fields' => 'all', 'user_id' => 0);
		$args = wp_parse_args( $args, $defaults );
		$args['user_id'] = (int) $args['user_id'];

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
		$user_id = (int) $user_id;

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
			$select_this = 't.*, tt.*, tr.user_id';
		else if ( 'ids' == $fields )
			$select_this = 't.term_id';
		else if ( 'names' == $fields )
			$select_this = 't.name';
		else if ( 'all_with_object_id' == $fields )
			$select_this = 't.*, tt.*, tr.user_id, tr.object_id';

		$query = "SELECT $select_this FROM {$this->db->terms} AS t INNER JOIN {$this->db->term_taxonomy} AS tt ON tt.term_id = t.term_id INNER JOIN {$this->db->term_relationships} AS tr ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tt.taxonomy IN ($taxonomies) AND tr.object_id IN ($object_ids)";
		if ( $user_id )
			$query .= " AND user_id = '$user_id'";
		$query .= " $orderby $order";

		if ( 'all' == $fields || 'all_with_object_id' == $fields ) {
			$terms = array_merge($terms, $this->db->get_results($query));
			$this->update_term_cache($terms);
		} else if ( 'ids' == $fields || 'names' == $fields ) {
			$terms = array_merge($terms, $this->db->get_col($query));
		} else if ( 'tt_ids' == $fields ) {
			$query = "SELECT tr.term_taxonomy_id FROM {$this->db->term_relationships} AS tr INNER JOIN {$this->db->term_taxonomy} AS tt ON tr.term_taxonomy_id = tt.term_taxonomy_id WHERE tr.object_id IN ($object_ids) AND tt.taxonomy IN ($taxonomies)";
			if ( $user_id )
				$query .= " AND tr.user_id = '$user_id'";
			$query .= " $orderby $order";
			$terms = $this->db->get_col( $query );
		}

		if ( ! $terms )
			$terms = array();

		return apply_filters('wp_get_object_terms', $terms, $object_ids, $taxonomies, $args);
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
	 * @package bbPress
	 * @subpackage Taxonomy
	 * @since 1.0
	 *
	 * @param int $object_id The object to relate to.
	 * @param array|int|string $term The slug or id of the term, will replace all existing
	 * related terms in this taxonomy.
	 * @param array|string $taxonomy The context in which to relate the term to the object.
	 * @param bool $append If false will delete difference of terms.
	 * @return array|WP_Error Affected Term IDs
	 */
	function set_object_terms($object_id, $terms, $taxonomy, $args = null) {
		$object_id = (int) $object_id;

		$defaults = array( 'append' => false, 'user_id' => 0 );
		if ( is_scalar( $args ) )
			$args = array( 'append' => (bool) $args );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );
		if ( !$user_id = (int) $user_id )
			return new WP_Error('invalid_user_id', __('Invalid User ID'));

		if ( !$this->is_taxonomy($taxonomy) )
			return new WP_Error('invalid_taxonomy', __('Invalid Taxonomy'));

		if ( !is_array($terms) )
			$terms = array($terms);

		if ( ! $append )
			$old_tt_ids = $this->get_object_terms($object_id, $taxonomy, array('user_id' => $user_id, 'fields' => 'tt_ids', 'orderby' => 'none'));

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

			if ( $this->db->get_var( $this->db->prepare( "SELECT term_taxonomy_id FROM {$this->db->term_relationships} WHERE object_id = %d AND term_taxonomy_id = %d AND user_id = %d", $object_id, $id, $user_id ) ) )
				continue;
			$this->db->insert( $this->db->term_relationships, array( 'object_id' => $object_id, 'term_taxonomy_id' => $id, 'user_id' => $user_id ) );
		}

		$this->update_term_count($tt_ids, $taxonomy);

		if ( ! $append ) {
			$delete_terms = array_diff($old_tt_ids, $tt_ids);
			if ( $delete_terms ) {
				$in_delete_terms = "'" . implode("', '", $delete_terms) . "'";
				$this->db->query( $this->db->prepare("DELETE FROM {$this->db->term_relationships} WHERE object_id = %d AND user_id = %d AND term_taxonomy_id IN ($in_delete_terms)", $object_id, $user_id) );
				$this->update_term_count($delete_terms, $taxonomy);
			}
		}

		$t = $this->get_taxonomy($taxonomy);
		if ( ! $append && isset($t->sort) && $t->sort ) {
			$values = array();
			$term_order = 0;
			$final_tt_ids = $this->get_object_terms($object_id, $taxonomy, array( 'user_id' => $user_id, 'fields' => 'tt_ids' ));
			foreach ( $tt_ids as $tt_id )
				if ( in_array($tt_id, $final_tt_ids) )
					$values[] = $this->db->prepare( "(%d, %d, %d, %d)", $object_id, $tt_id, $user_id, ++$term_order);
			if ( $values )
				$this->db->query("INSERT INTO {$this->db->term_relationships} (object_id, term_taxonomy_id, user_id, term_order) VALUES " . join(',', $values) . " ON DUPLICATE KEY UPDATE term_order = VALUES(term_order)");
		}

		do_action('set_object_terms', $object_id, $terms, $tt_ids, $taxonomy, $append);
		return $tt_ids;
	}
} // END class BB_Taxonomy extends WP_Taxonomy
