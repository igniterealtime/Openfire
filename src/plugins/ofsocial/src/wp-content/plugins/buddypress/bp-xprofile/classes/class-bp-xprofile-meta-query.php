<?php
/**
 * BuddyPress XProfile Classes.
 *
 * @package BuddyPress
 * @subpackage XProfileClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Class for generating SQL clauses that filter a primary query according to
 * XProfile metadata keys and values.
 *
 * `BP_XProfile_Meta_Query` is a helper that allows primary query classes, such
 * as {@see WP_Query} and {@see WP_User_Query}, to filter their results by object
 * metadata, by generating `JOIN` and `WHERE` subclauses to be attached
 * to the primary SQL query string.
 *
 * @since 2.3.0
 */
class BP_XProfile_Meta_Query extends WP_Meta_Query {

	/**
	 * Determine whether a query clause is first-order.
	 *
	 * A first-order meta query clause is one that has either a 'key', 'value',
	 * or 'object' array key.
	 *
	 * @since 2.3.0
	 *
	 * @param array $query Meta query arguments.
	 *
	 * @return bool Whether the query clause is a first-order clause.
	 */
	protected function is_first_order_clause( $query ) {
		return isset( $query['key'] ) || isset( $query['value'] ) || isset( $query['object'] );
	}

	/**
	 * Constructs a meta query based on 'meta_*' query vars
	 *
	 * @since 2.3.0
	 *
	 * @param array $qv The query variables.
	 */
	public function parse_query_vars( $qv ) {
		$meta_query = array();

		/*
		 * For orderby=meta_value to work correctly, simple query needs to be
		 * first (so that its table join is against an unaliased meta table) and
		 * needs to be its own clause (so it doesn't interfere with the logic of
		 * the rest of the meta_query).
		 */
		$primary_meta_query = array();
		foreach ( array( 'key', 'compare', 'type' ) as $key ) {
			if ( ! empty( $qv[ "meta_$key" ] ) ) {
				$primary_meta_query[ $key ] = $qv[ "meta_$key" ];
			}
		}

		// WP_Query sets 'meta_value' = '' by default.
		if ( isset( $qv['meta_value'] ) && ( '' !== $qv['meta_value'] ) && ( ! is_array( $qv['meta_value'] ) || $qv['meta_value'] ) ) {
			$primary_meta_query['value'] = $qv['meta_value'];
		}

		// BP_XProfile_Query sets 'object_type' = '' by default.
		if ( isset( $qv[ 'object_type' ] ) && ( '' !== $qv[ 'object_type' ] ) && ( ! is_array( $qv[ 'object_type' ] ) || $qv[ 'object_type' ] ) ) {
			$meta_query[0]['object'] = $qv[ 'object_type' ];
		}

		$existing_meta_query = isset( $qv['meta_query'] ) && is_array( $qv['meta_query'] ) ? $qv['meta_query'] : array();

		if ( ! empty( $primary_meta_query ) && ! empty( $existing_meta_query ) ) {
			$meta_query = array(
				'relation' => 'AND',
				$primary_meta_query,
				$existing_meta_query,
			);
		} elseif ( ! empty( $primary_meta_query ) ) {
			$meta_query = array(
				$primary_meta_query,
			);
		} elseif ( ! empty( $existing_meta_query ) ) {
			$meta_query = $existing_meta_query;
		}

		$this->__construct( $meta_query );
	}

	/**
	 * Generates SQL clauses to be appended to a main query.
	 *
	 * @since 2.3.0
	 *
	 * @param string $type              Type of meta, eg 'user', 'post'.
	 * @param string $primary_table     Database table where the object being filtered is stored (eg wp_users).
	 * @param string $primary_id_column ID column for the filtered object in $primary_table.
	 * @param object $context           Optional. The main query object.
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to the main query.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	public function get_sql( $type, $primary_table, $primary_id_column, $context = null ) {
		if ( ! $meta_table = _get_meta_table( $type ) ) {
			return false;
		}

		$this->meta_table     = $meta_table;
		$this->meta_id_column = 'object_id';

		$this->primary_table     = $primary_table;
		$this->primary_id_column = $primary_id_column;

		$sql = $this->get_sql_clauses();

		/*
		 * If any JOINs are LEFT JOINs (as in the case of NOT EXISTS), then all JOINs should
		 * be LEFT. Otherwise posts with no metadata will be excluded from results.
		 */
		if ( false !== strpos( $sql['join'], 'LEFT JOIN' ) ) {
			$sql['join'] = str_replace( 'INNER JOIN', 'LEFT JOIN', $sql['join'] );
		}

		/**
		 * Filter the meta query's generated SQL.
		 *
		 * @since 2.3.0
		 *
		 * @param array $args {
		 *     An array of meta query SQL arguments.
		 *
		 *     @type array  $clauses           Array containing the query's JOIN and WHERE clauses.
		 *     @type array  $queries           Array of meta queries.
		 *     @type string $type              Type of meta.
		 *     @type string $primary_table     Primary table.
		 *     @type string $primary_id_column Primary column ID.
		 *     @type object $context           The main query object.
		 * }
		 */
		return apply_filters_ref_array( 'bp_xprofile_get_meta_sql', array( $sql, $this->queries, $type, $primary_table, $primary_id_column, $context ) );
	}

	/**
	 * Generate SQL JOIN and WHERE clauses for a first-order query clause.
	 *
	 * "First-order" means that it's an array with a 'key' or 'value'.
	 *
	 * @since 2.3.0
	 *
	 * @param array  $clause       Query clause, passed by reference.
	 * @param array  $parent_query Parent query array.
	 * @param string $clause_key   Optional. The array key used to name the clause in the original `$meta_query`
	 *                             parameters. If not provided, a key will be generated automatically.
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to a first-order query.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	public function get_sql_for_clause( &$clause, $parent_query, $clause_key = '' ) {
		global $wpdb;

		$sql_chunks = array(
			'where' => array(),
			'join'  => array(),
		);

		if ( isset( $clause['compare'] ) ) {
			$clause['compare'] = strtoupper( $clause['compare'] );
		} else {
			$clause['compare'] = isset( $clause['value'] ) && is_array( $clause['value'] ) ? 'IN' : '=';
		}

		if ( ! in_array( $clause['compare'], array(
			'=', '!=', '>', '>=', '<', '<=',
			'LIKE', 'NOT LIKE',
			'IN', 'NOT IN',
			'BETWEEN', 'NOT BETWEEN',
			'EXISTS', 'NOT EXISTS',
			'REGEXP', 'NOT REGEXP', 'RLIKE'
		) ) ) {
			$clause['compare'] = '=';
		}

		$meta_compare = $clause['compare'];

		// First build the JOIN clause, if one is required.
		$join = '';

		// We prefer to avoid joins if possible. Look for an existing join compatible with this clause.
		$alias = $this->find_compatible_table_alias( $clause, $parent_query );
		if ( false === $alias ) {
			$i = count( $this->table_aliases );
			$alias = $i ? 'mt' . $i : $this->meta_table;

			// JOIN clauses for NOT EXISTS have their own syntax.
			if ( 'NOT EXISTS' === $meta_compare ) {
				$join .= " LEFT JOIN $this->meta_table";
				$join .= $i ? " AS $alias" : '';
				$join .= $wpdb->prepare( " ON ($this->primary_table.$this->primary_id_column = $alias.$this->meta_id_column AND $alias.meta_key = %s )", $clause['key'] );

			// All other JOIN clauses.
			} else {
				$join .= " INNER JOIN $this->meta_table";
				$join .= $i ? " AS $alias" : '';
				$join .= " ON ( $this->primary_table.$this->primary_id_column = $alias.$this->meta_id_column )";
			}

			$this->table_aliases[] = $alias;
			$sql_chunks['join'][]  = $join;
		}

		// Save the alias to this clause, for future siblings to find.
		$clause['alias'] = $alias;

		// Determine the data type.
		$_meta_type     = isset( $clause['type'] ) ? $clause['type'] : '';
		$meta_type      = $this->get_cast_for_type( $_meta_type );
		$clause['cast'] = $meta_type;

		// Fallback for clause keys is the table alias.
		if ( ! $clause_key ) {
			$clause_key = $clause['alias'];
		}

		// Ensure unique clause keys, so none are overwritten.
		$iterator = 1;
		$clause_key_base = $clause_key;
		while ( isset( $this->clauses[ $clause_key ] ) ) {
			$clause_key = $clause_key_base . '-' . $iterator;
			$iterator++;
		}

		// Store the clause in our flat array.
		$this->clauses[ $clause_key ] =& $clause;

		// Next, build the WHERE clause.
		// Meta_key.
		if ( array_key_exists( 'key', $clause ) ) {
			if ( 'NOT EXISTS' === $meta_compare ) {
				$sql_chunks['where'][] = $alias . '.' . $this->meta_id_column . ' IS NULL';
			} else {
				$sql_chunks['where'][] = $wpdb->prepare( "$alias.meta_key = %s", trim( $clause['key'] ) );
			}
		}

		// Meta_value.
		if ( array_key_exists( 'value', $clause ) ) {
			$meta_value = $clause['value'];

			if ( in_array( $meta_compare, array( 'IN', 'NOT IN', 'BETWEEN', 'NOT BETWEEN' ) ) ) {
				if ( ! is_array( $meta_value ) ) {
					$meta_value = preg_split( '/[,\s]+/', $meta_value );
				}
			} else {
				$meta_value = trim( $meta_value );
			}

			switch ( $meta_compare ) {
				case 'IN' :
				case 'NOT IN' :
					$meta_compare_string = '(' . substr( str_repeat( ',%s', count( $meta_value ) ), 1 ) . ')';
					$where = $wpdb->prepare( $meta_compare_string, $meta_value );
					break;

				case 'BETWEEN' :
				case 'NOT BETWEEN' :
					$meta_value = array_slice( $meta_value, 0, 2 );
					$where = $wpdb->prepare( '%s AND %s', $meta_value );
					break;

				case 'LIKE' :
				case 'NOT LIKE' :
					$meta_value = '%' . $wpdb->esc_like( $meta_value ) . '%';
					$where = $wpdb->prepare( '%s', $meta_value );
					break;

				// EXISTS with a value is interpreted as '='.
				case 'EXISTS' :
					$meta_compare = '=';
					$where = $wpdb->prepare( '%s', $meta_value );
					break;

				// 'value' is ignored for NOT EXISTS.
				case 'NOT EXISTS' :
					$where = '';
					break;

				default :
					$where = $wpdb->prepare( '%s', $meta_value );
					break;

			}

			if ( $where ) {
				$sql_chunks['where'][] = "CAST($alias.meta_value AS {$meta_type}) {$meta_compare} {$where}";
			}
		}

		// Object_type.
		if ( array_key_exists( 'object', $clause ) ) {
			$object_type = $clause['object'];

			if ( in_array( $meta_compare, array( 'IN', 'NOT IN' ) ) ) {
				if ( ! is_array( $object_type ) ) {
					$object_type = preg_split( '/[,\s]+/', $object_type );
				}
			} else {
				$object_type = trim( $object_type );
			}

			switch ( $meta_compare ) {
				case 'IN' :
				case 'NOT IN' :
					$meta_compare_string = '(' . substr( str_repeat( ',%s', count( $object_type ) ), 1 ) . ')';
					$object_where        = $wpdb->prepare( $meta_compare_string, $object_type );
					break;

				case 'LIKE' :
				case 'NOT LIKE' :
					$object_type  = '%' . $wpdb->esc_like( $object_type ) . '%';
					$object_where = $wpdb->prepare( '%s', $object_type );
					break;

				// EXISTS with a value is interpreted as '='.
				case 'EXISTS' :
					$meta_compare = '=';
					$object_where = $wpdb->prepare( '%s', $object_type );
					break;

				// 'value' is ignored for NOT EXISTS.
				case 'NOT EXISTS' :
					$object_where = '';
					break;

				default :
					$object_where = $wpdb->prepare( '%s', $object_type );
					break;
			}

			if ( ! empty( $object_where ) ) {
				$sql_chunks['where'][] = "{$alias}.object_type {$meta_compare} {$object_where}";
			}
		}

		/*
		 * Multiple WHERE clauses (for meta_key and meta_value) should
		 * be joined in parentheses.
		 */
		if ( 1 < count( $sql_chunks['where'] ) ) {
			$sql_chunks['where'] = array( '( ' . implode( ' AND ', $sql_chunks['where'] ) . ' )' );
		}

		return $sql_chunks;
	}
}
