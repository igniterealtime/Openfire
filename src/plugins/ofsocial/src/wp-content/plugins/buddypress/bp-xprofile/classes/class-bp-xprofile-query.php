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
 * Class for generating SQL clauses to filter a user query by xprofile data.
 *
 * @since 2.2.0
 */
class BP_XProfile_Query {

	/**
	 * Array of xprofile queries.
	 *
	 * See {@see WP_XProfile_Query::__construct()} for information on parameters.
	 *
	 * @since 2.2.0
	 * @var    array
	 */
	public $queries = array();

	/**
	 * Database table that where the metadata's objects are stored (eg $wpdb->users).
	 *
	 * @since 2.2.0
	 * @var    string
	 */
	public $primary_table;

	/**
	 * Column in primary_table that represents the ID of the object.
	 *
	 * @since 2.2.0
	 * @var    string
	 */
	public $primary_id_column;

	/**
	 * A flat list of table aliases used in JOIN clauses.
	 *
	 * @since 2.2.0
	 * @var    array
	 */
	protected $table_aliases = array();

	/**
	 * Constructor.
	 *
	 * @since 2.2.0
	 *
	 * @param array $xprofile_query {
	 *     Array of xprofile query clauses.
	 *
	 *     @type string $relation Optional. The MySQL keyword used to join the clauses of the query.
	 *                            Accepts 'AND', or 'OR'. Default 'AND'.
	 *     @type array {
	 *         Optional. An array of first-order clause parameters, or another fully-formed xprofile query.
	 *
	 *         @type string|int $field   XProfile field to filter by. Accepts a field name or ID.
	 *         @type string     $value   XProfile value to filter by.
	 *         @type string     $compare MySQL operator used for comparing the $value. Accepts '=', '!=', '>',
	 *                                   '>=', '<', '<=', 'LIKE', 'NOT LIKE', 'IN', 'NOT IN', 'BETWEEN',
	 *                                   'NOT BETWEEN', 'REGEXP', 'NOT REGEXP', or 'RLIKE'. Default is 'IN'
	 *                                   when `$value` is an array, '=' otherwise.
	 *         @type string     $type    MySQL data type that the `value` column will be CAST to for comparisons.
	 *                                   Accepts 'NUMERIC', 'BINARY', 'CHAR', 'DATE', 'DATETIME', 'DECIMAL',
	 *                                   'SIGNED', 'TIME', or 'UNSIGNED'. Default is 'CHAR'.
	 *     }
	 * }
	 */
	public function __construct( $xprofile_query ) {
		if ( empty( $xprofile_query ) ) {
			return;
		}

		$this->queries = $this->sanitize_query( $xprofile_query );
	}

	/**
	 * Ensure the `xprofile_query` argument passed to the class constructor is well-formed.
	 *
	 * Eliminates empty items and ensures that a 'relation' is set.
	 *
	 * @since 2.2.0
	 *
	 * @param array $queries Array of query clauses.
	 *
	 * @return array Sanitized array of query clauses.
	 */
	public function sanitize_query( $queries ) {
		$clean_queries = array();

		if ( ! is_array( $queries ) ) {
			return $clean_queries;
		}

		foreach ( $queries as $key => $query ) {
			if ( 'relation' === $key ) {
				$relation = $query;

			} elseif ( ! is_array( $query ) ) {
				continue;

			// First-order clause.
			} elseif ( $this->is_first_order_clause( $query ) ) {
				if ( isset( $query['value'] ) && array() === $query['value'] ) {
					unset( $query['value'] );
				}

				$clean_queries[] = $query;

			// Otherwise, it's a nested query, so we recurse.
			} else {
				$cleaned_query = $this->sanitize_query( $query );

				if ( ! empty( $cleaned_query ) ) {
					$clean_queries[] = $cleaned_query;
				}
			}
		}

		if ( empty( $clean_queries ) ) {
			return $clean_queries;
		}

		// Sanitize the 'relation' key provided in the query.
		if ( isset( $relation ) && 'OR' === strtoupper( $relation ) ) {
			$clean_queries['relation'] = 'OR';

		/*
		 * If there is only a single clause, call the relation 'OR'.
		 * This value will not actually be used to join clauses, but it
		 * simplifies the logic around combining key-only queries.
		 */
		} elseif ( 1 === count( $clean_queries ) ) {
			$clean_queries['relation'] = 'OR';

		// Default to AND.
		} else {
			$clean_queries['relation'] = 'AND';
		}

		return $clean_queries;
	}

	/**
	 * Determine whether a query clause is first-order.
	 *
	 * A first-order query clause is one that has either a 'key' or a 'value' array key.
	 *
	 * @since 2.2.0
	 *
	 * @param  array $query XProfile query arguments.
	 *
	 * @return bool  Whether the query clause is a first-order clause.
	 */
	protected function is_first_order_clause( $query ) {
		return isset( $query['field'] ) || isset( $query['value'] );
	}

	/**
	 * Return the appropriate alias for the given field type if applicable.
	 *
	 * @since 2.2.0
	 *
	 * @param string $type MySQL type to cast `value`.
	 *
	 * @return string MySQL type.
	 */
	public function get_cast_for_type( $type = '' ) {
		if ( empty( $type ) ) {
			return 'CHAR';
		}

		$meta_type = strtoupper( $type );

		if ( ! preg_match( '/^(?:BINARY|CHAR|DATE|DATETIME|SIGNED|UNSIGNED|TIME|NUMERIC(?:\(\d+(?:,\s?\d+)?\))?|DECIMAL(?:\(\d+(?:,\s?\d+)?\))?)$/', $meta_type ) ) {
			return 'CHAR';
		}

		if ( 'NUMERIC' === $meta_type ) {
			$meta_type = 'SIGNED';
		}

		return $meta_type;
	}

	/**
	 * Generate SQL clauses to be appended to a main query.
	 *
	 * Called by the public {@see BP_XProfile_Query::get_sql()}, this method is abstracted out to maintain parity
	 * with WP's Query classes.
	 *
	 * @since 2.2.0
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to the main query.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	protected function get_sql_clauses() {
		/*
		 * $queries are passed by reference to get_sql_for_query() for recursion.
		 * To keep $this->queries unaltered, pass a copy.
		 */
		$queries = $this->queries;
		$sql = $this->get_sql_for_query( $queries );

		if ( ! empty( $sql['where'] ) ) {
			$sql['where'] = ' AND ' . $sql['where'];
		}

		return $sql;
	}

	/**
	 * Generate SQL clauses for a single query array.
	 *
	 * If nested subqueries are found, this method recurses the tree to produce the properly nested SQL.
	 *
	 * @since 2.2.0
	 *
	 * @param  array $query Query to parse.
	 * @param  int   $depth Optional. Number of tree levels deep we currently are. Used to calculate indentation.
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to a single query array.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	protected function get_sql_for_query( &$query, $depth = 0 ) {
		$sql_chunks = array(
			'join'  => array(),
			'where' => array(),
		);

		$sql = array(
			'join'  => '',
			'where' => '',
		);

		$indent = '';
		for ( $i = 0; $i < $depth; $i++ ) {
			$indent .= "  ";
		}

		foreach ( $query as $key => &$clause ) {
			if ( 'relation' === $key ) {
				$relation = $query['relation'];
			} elseif ( is_array( $clause ) ) {

				// This is a first-order clause.
				if ( $this->is_first_order_clause( $clause ) ) {
					$clause_sql = $this->get_sql_for_clause( $clause, $query );

					$where_count = count( $clause_sql['where'] );
					if ( ! $where_count ) {
						$sql_chunks['where'][] = '';
					} elseif ( 1 === $where_count ) {
						$sql_chunks['where'][] = $clause_sql['where'][0];
					} else {
						$sql_chunks['where'][] = '( ' . implode( ' AND ', $clause_sql['where'] ) . ' )';
					}

					$sql_chunks['join'] = array_merge( $sql_chunks['join'], $clause_sql['join'] );
				// This is a subquery, so we recurse.
				} else {
					$clause_sql = $this->get_sql_for_query( $clause, $depth + 1 );

					$sql_chunks['where'][] = $clause_sql['where'];
					$sql_chunks['join'][]  = $clause_sql['join'];
				}
			}
		}

		// Filter to remove empties.
		$sql_chunks['join']  = array_filter( $sql_chunks['join'] );
		$sql_chunks['where'] = array_filter( $sql_chunks['where'] );

		if ( empty( $relation ) ) {
			$relation = 'AND';
		}

		// Filter duplicate JOIN clauses and combine into a single string.
		if ( ! empty( $sql_chunks['join'] ) ) {
			$sql['join'] = implode( ' ', array_unique( $sql_chunks['join'] ) );
		}

		// Generate a single WHERE clause with proper brackets and indentation.
		if ( ! empty( $sql_chunks['where'] ) ) {
			$sql['where'] = '( ' . "\n  " . $indent . implode( ' ' . "\n  " . $indent . $relation . ' ' . "\n  " . $indent, $sql_chunks['where'] ) . "\n" . $indent . ')';
		}

		return $sql;
	}

	/**
	 * Generates SQL clauses to be appended to a main query.
	 *
	 * @since 2.2.0
	 *
	 * @param string $primary_table     Database table where the object being filtered is stored (eg wp_users).
	 * @param string $primary_id_column ID column for the filtered object in $primary_table.
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to the main query.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	public function get_sql( $primary_table, $primary_id_column ) {

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

		return $sql;
	}

	/**
	 * Generate SQL JOIN and WHERE clauses for a first-order query clause.
	 *
	 * "First-order" means that it's an array with a 'field' or 'value'.
	 *
	 * @since 2.2.0
	 *
	 * @param array $clause       Query clause.
	 * @param array $parent_query Parent query array.
	 *
	 * @return array {
	 *     Array containing JOIN and WHERE SQL clauses to append to a first-order query.
	 *
	 *     @type string $join  SQL fragment to append to the main JOIN clause.
	 *     @type string $where SQL fragment to append to the main WHERE clause.
	 * }
	 */
	public function get_sql_for_clause( &$clause, $parent_query ) {
		global $wpdb;

		$sql_chunks = array(
			'where' => array(),
			'join' => array(),
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

		$field_compare = $clause['compare'];

		// First build the JOIN clause, if one is required.
		$join = '';

		$data_table = buddypress()->profile->table_name_data;

		// We prefer to avoid joins if possible. Look for an existing join compatible with this clause.
		$alias = $this->find_compatible_table_alias( $clause, $parent_query );
		if ( false === $alias ) {
			$i = count( $this->table_aliases );
			$alias = $i ? 'xpq' . $i : $data_table;

			// JOIN clauses for NOT EXISTS have their own syntax.
			if ( 'NOT EXISTS' === $field_compare ) {
				$join .= " LEFT JOIN $data_table";
				$join .= $i ? " AS $alias" : '';
				$join .= $wpdb->prepare( " ON ($this->primary_table.$this->primary_id_column = $alias.user_id AND $alias.field_id = %d )", $clause['field'] );

			// All other JOIN clauses.
			} else {
				$join .= " INNER JOIN $data_table";
				$join .= $i ? " AS $alias" : '';
				$join .= " ON ( $this->primary_table.$this->primary_id_column = $alias.user_id )";
			}

			$this->table_aliases[] = $alias;
			$sql_chunks['join'][] = $join;
		}

		// Save the alias to this clause, for future siblings to find.
		$clause['alias'] = $alias;

		// Next, build the WHERE clause.
		$where = '';

		// Field_id.
		if ( array_key_exists( 'field', $clause ) ) {
			// Convert field name to ID if necessary.
			if ( ! is_numeric( $clause['field'] ) ) {
				$clause['field'] = xprofile_get_field_id_from_name( $clause['field'] );
			}

			// NOT EXISTS has its own syntax.
			if ( 'NOT EXISTS' === $field_compare ) {
				$sql_chunks['where'][] = $alias . '.user_id IS NULL';
			} else {
				$sql_chunks['where'][] = $wpdb->prepare( "$alias.field_id = %d", $clause['field'] );
			}
		}

		// Value.
		if ( array_key_exists( 'value', $clause ) ) {
			$field_value = $clause['value'];
			$field_type = $this->get_cast_for_type( isset( $clause['type'] ) ? $clause['type'] : '' );

			if ( in_array( $field_compare, array( 'IN', 'NOT IN', 'BETWEEN', 'NOT BETWEEN' ) ) ) {
				if ( ! is_array( $field_value ) ) {
					$field_value = preg_split( '/[,\s]+/', $field_value );
				}
			} else {
				$field_value = trim( $field_value );
			}

			switch ( $field_compare ) {
				case 'IN' :
				case 'NOT IN' :
					$field_compare_string = '(' . substr( str_repeat( ',%s', count( $field_value ) ), 1 ) . ')';
					$where = $wpdb->prepare( $field_compare_string, $field_value );
					break;

				case 'BETWEEN' :
				case 'NOT BETWEEN' :
					$field_value = array_slice( $field_value, 0, 2 );
					$where = $wpdb->prepare( '%s AND %s', $field_value );
					break;

				case 'LIKE' :
				case 'NOT LIKE' :
					$field_value = '%' . bp_esc_like( $field_value ) . '%';
					$where = $wpdb->prepare( '%s', $field_value );
					break;

				default :
					$where = $wpdb->prepare( '%s', $field_value );
					break;

			}

			if ( $where ) {
				$sql_chunks['where'][] = "CAST($alias.value AS {$field_type}) {$field_compare} {$where}";
			}
		}

		/*
		 * Multiple WHERE clauses (`field` and `value` pairs) should be joined in parentheses.
		 */
		if ( 1 < count( $sql_chunks['where'] ) ) {
			$sql_chunks['where'] = array( '( ' . implode( ' AND ', $sql_chunks['where'] ) . ' )' );
		}

		return $sql_chunks;
	}

	/**
	 * Identify an existing table alias that is compatible with the current query clause.
	 *
	 * We avoid unnecessary table joins by allowing each clause to look for an existing table alias that is
	 * compatible with the query that it needs to perform. An existing alias is compatible if (a) it is a
	 * sibling of $clause (ie, it's under the scope of the same relation), and (b) the combination of
	 * operator and relation between the clauses allows for a shared table join. In the case of BP_XProfile_Query,
	 * this * only applies to IN clauses that are connected by the relation OR.
	 *
	 * @since 2.2.0
	 *
	 * @param array $clause       Query clause.
	 * @param array $parent_query Parent query of $clause.
	 *
	 * @return string|bool Table alias if found, otherwise false.
	 */
	protected function find_compatible_table_alias( $clause, $parent_query ) {
		$alias = false;

		foreach ( $parent_query as $sibling ) {
			// If the sibling has no alias yet, there's nothing to check.
			if ( empty( $sibling['alias'] ) ) {
				continue;
			}

			// We're only interested in siblings that are first-order clauses.
			if ( ! is_array( $sibling ) || ! $this->is_first_order_clause( $sibling ) ) {
				continue;
			}

			$compatible_compares = array();

			// Clauses connected by OR can share joins as long as they have "positive" operators.
			if ( 'OR' === $parent_query['relation'] ) {
				$compatible_compares = array( '=', 'IN', 'BETWEEN', 'LIKE', 'REGEXP', 'RLIKE', '>', '>=', '<', '<=' );

			// Clauses joined by AND with "negative" operators share a join only if they also share a key.
			} elseif ( isset( $sibling['field_id'] ) && isset( $clause['field_id'] ) && $sibling['field_id'] === $clause['field_id'] ) {
				$compatible_compares = array( '!=', 'NOT IN', 'NOT LIKE' );
			}

			$clause_compare  = strtoupper( $clause['compare'] );
			$sibling_compare = strtoupper( $sibling['compare'] );
			if ( in_array( $clause_compare, $compatible_compares ) && in_array( $sibling_compare, $compatible_compares ) ) {
				$alias = $sibling['alias'];
				break;
			}
		}

		return $alias;
	}
}
