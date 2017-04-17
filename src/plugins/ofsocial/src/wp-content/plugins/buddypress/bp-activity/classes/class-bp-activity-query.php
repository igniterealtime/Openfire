<?php
/**
 * BuddyPress Activity Classes
 *
 * @package BuddyPress
 * @subpackage ActivityQuery
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Class for generating the WHERE SQL clause for advanced activity fetching.
 *
 * This is notably used in {@link BP_Activity_Activity::get()} with the
 * 'filter_query' parameter.
 *
 * @since 2.2.0
 */
class BP_Activity_Query extends BP_Recursive_Query {
	/**
	 * Array of activity queries.
	 *
	 * See {@see BP_Activity_Query::__construct()} for information on query arguments.
	 *
	 * @since 2.2.0
	 * @var array
	 */
	public $queries = array();

	/**
	 * Table alias.
	 *
	 * @since 2.2.0
	 * @var string
	 */
	public $table_alias = '';

	/**
	 * Supported DB columns.
	 *
	 * See the 'wp_bp_activity' DB table schema.
	 *
	 * @since 2.2.0
	 * @var array
	 */
	public $db_columns = array(
		'id', 'user_id', 'component', 'type', 'action', 'content',
		'item_id', 'secondary_item_id', 'hide_sitewide', 'is_spam',
	);

	/**
	 * Constructor.
	 *
	 * @since 2.2.0
	 *
	 * @param array $query {
	 *     Array of query clauses.
	 *     @type array {
	 *         @type string $column   Required. The column to query against. Basically, any DB column in the main
	 *                                'wp_bp_activity' table.
	 *         @type string $value    Required. Value to filter by.
	 *         @type string $compare  Optional. The comparison operator. Default '='.
	 *                                Accepts '=', '!=', '>', '>=', '<', '<=', 'IN', 'NOT IN', 'LIKE',
	 *                                'NOT LIKE', BETWEEN', 'NOT BETWEEN', 'REGEXP', 'NOT REGEXP', 'RLIKE'.
	 *         @type string $relation Optional. The boolean relationship between the activity queries.
	 *                                Accepts 'OR', 'AND'. Default 'AND'.
	 *         @type array {
	 *             Optional. Another fully-formed activity query. See parameters above.
	 *         }
	 *     }
	 * }
	 */
	public function __construct( $query = array() ) {
		if ( ! is_array( $query ) ) {
			return;
		}

		$this->queries = $this->sanitize_query( $query );
	}

	/**
	 * Generates WHERE SQL clause to be appended to a main query.
	 *
	 * @since 2.2.0
	 *
	 * @param string $alias An existing table alias that is compatible with the current query clause.
	 *                      Default: 'a'. BP_Activity_Activity::get() uses 'a', so we default to that.
	 * @return string SQL fragment to append to the main WHERE clause.
	 */
	public function get_sql( $alias = 'a' ) {
		if ( ! empty( $alias ) ) {
			$this->table_alias = sanitize_title( $alias );
		}

		$sql = $this->get_sql_clauses();

		// We only need the 'where' clause.
		//
		// Also trim trailing "AND" clause from parent BP_Recursive_Query class
		// since it's not necessary for our needs.
		return preg_replace( '/^\sAND/', '', $sql['where'] );
	}

	/**
	 * Generate WHERE clauses for a first-order clause.
	 *
	 * @since 2.2.0
	 *
	 * @param  array $clause       Array of arguments belonging to the clause.
	 * @param  array $parent_query Parent query to which the clause belongs.
	 * @return array {
	 *     @type array $where Array of subclauses for the WHERE statement.
	 *     @type array $join  Empty array. Not used.
	 * }
	 */
	protected function get_sql_for_clause( $clause, $parent_query ) {
		global $wpdb;

		$sql_chunks = array(
			'where' => array(),
			'join' => array(),
		);

		$column = isset( $clause['column'] ) ? $this->validate_column( $clause['column'] ) : '';
		$value  = isset( $clause['value'] )  ? $clause['value'] : '';
		if ( empty( $column ) || ! isset( $clause['value'] ) ) {
			return $sql_chunks;
		}

		if ( isset( $clause['compare'] ) ) {
			$clause['compare'] = strtoupper( $clause['compare'] );
		} else {
			$clause['compare'] = isset( $clause['value'] ) && is_array( $clause['value'] ) ? 'IN' : '=';
		}

		// Default 'compare' to '=' if no valid operator is found.
		if ( ! in_array( $clause['compare'], array(
			'=', '!=', '>', '>=', '<', '<=',
			'LIKE', 'NOT LIKE',
			'IN', 'NOT IN',
			'BETWEEN', 'NOT BETWEEN',
			'REGEXP', 'NOT REGEXP', 'RLIKE'
		) ) ) {
			$clause['compare'] = '=';
		}

		$compare = $clause['compare'];

		$alias = ! empty( $this->table_alias ) ? "{$this->table_alias}." : '';

		// Next, Build the WHERE clause.
		$where = '';

		// Value.
		if ( isset( $clause['value'] ) ) {
			if ( in_array( $compare, array( 'IN', 'NOT IN', 'BETWEEN', 'NOT BETWEEN' ) ) ) {
				if ( ! is_array( $value ) ) {
					$value = preg_split( '/[,\s]+/', $value );
				}
			}

			// Tinyint.
			if ( ! empty( $column ) && true === in_array( $column, array( 'hide_sitewide', 'is_spam' ) ) ) {
				$sql_chunks['where'][] = $wpdb->prepare( "{$alias}{$column} = %d", $value );

			} else {
				switch ( $compare ) {
					// IN uses different syntax.
					case 'IN' :
					case 'NOT IN' :
						$in_sql = BP_Activity_Activity::get_in_operator_sql( "{$alias}{$column}", $value );

						// 'NOT IN' operator is as easy as a string replace!
						if ( 'NOT IN' === $compare ) {
							$in_sql = str_replace( 'IN', 'NOT IN', $in_sql );
						}

						$sql_chunks['where'][] = $in_sql;
						break;

					case 'BETWEEN' :
					case 'NOT BETWEEN' :
						$value = array_slice( $value, 0, 2 );
						$where = $wpdb->prepare( '%s AND %s', $value );
						break;

					case 'LIKE' :
					case 'NOT LIKE' :
						$value = '%' . bp_esc_like( $value ) . '%';
						$where = $wpdb->prepare( '%s', $value );
						break;

					default :
						$where = $wpdb->prepare( '%s', $value );
						break;

				}
			}

			if ( $where ) {
				$sql_chunks['where'][] = "{$alias}{$column} {$compare} {$where}";
			}
		}

		/*
		 * Multiple WHERE clauses should be joined in parentheses.
		 */
		if ( 1 < count( $sql_chunks['where'] ) ) {
			$sql_chunks['where'] = array( '( ' . implode( ' AND ', $sql_chunks['where'] ) . ' )' );
		}

		return $sql_chunks;
	}

	/**
	 * Determine whether a clause is first-order.
	 *
	 * @since 2.2.0
	 *
	 * @param array $query Clause to check.
	 * @return bool
	 */
	protected function is_first_order_clause( $query ) {
		return isset( $query['column'] ) || isset( $query['value'] );
	}

	/**
	 * Validates a column name parameter.
	 *
	 * Column names are checked against a whitelist of known tables.
	 * See {@link BP_Activity_Query::db_tables}.
	 *
	 * @since 2.2.0
	 *
	 * @param string $column The user-supplied column name.
	 * @return string A validated column name value.
	 */
	public function validate_column( $column = '' ) {
		if ( in_array( $column, $this->db_columns ) ) {
			return $column;
		} else {
			return '';
		}
	}
}
