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
 * Base class for creating query classes that generate SQL fragments for filtering results based on recursive query params.
 *
 * @since 2.2.0
 */
abstract class BP_Recursive_Query {

	/**
	 * Query arguments passed to the constructor.
	 *
	 * @since 2.2.0
	 * @var array
	 */
	public $queries = array();

	/**
	 * Generate SQL clauses to be appended to a main query.
	 *
	 * Extending classes should call this method from within a publicly
	 * accessible get_sql() method, and manipulate the SQL as necessary.
	 * For example, {@link BP_XProfile_Query::get_sql()} is merely a wrapper for
	 * get_sql_clauses(), while {@link BP_Activity_Query::get_sql()} discards
	 * the empty 'join' clause, and only passes the 'where' clause.
	 *
	 * @since 2.2.0
	 *
	 * @return array
	 */
	protected function get_sql_clauses() {
		$sql = $this->get_sql_for_query( $this->queries );

		if ( ! empty( $sql['where'] ) ) {
			$sql['where'] = ' AND ' . "\n" . $sql['where'] . "\n";
		}

		return $sql;
	}

	/**
	 * Generate SQL clauses for a single query array.
	 *
	 * If nested subqueries are found, this method recurses the tree to
	 * produce the properly nested SQL.
	 *
	 * Subclasses generally do not need to call this method. It is invoked
	 * automatically from get_sql_clauses().
	 *
	 * @since 2.2.0
	 *
	 * @param  array $query Query to parse.
	 * @param  int   $depth Optional. Number of tree levels deep we
	 *                      currently are. Used to calculate indentation.
	 * @return array
	 */
	protected function get_sql_for_query( $query, $depth = 0 ) {
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
			$indent .= "\t";
		}

		foreach ( $query as $key => $clause ) {
			if ( 'relation' === $key ) {
				$relation = $query['relation'];
			} elseif ( is_array( $clause ) ) {
				// This is a first-order clause
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
				// This is a subquery
				} else {
					$clause_sql = $this->get_sql_for_query( $clause, $depth + 1 );

					$sql_chunks['where'][] = $clause_sql['where'];
					$sql_chunks['join'][]  = $clause_sql['join'];
				}
			}
		}

		// Filter empties
		$sql_chunks['join']  = array_filter( $sql_chunks['join'] );
		$sql_chunks['where'] = array_filter( $sql_chunks['where'] );

		if ( empty( $relation ) ) {
			$relation = 'AND';
		}

		if ( ! empty( $sql_chunks['join'] ) ) {
			$sql['join'] = implode( ' ', array_unique( $sql_chunks['join'] ) );
		}

		if ( ! empty( $sql_chunks['where'] ) ) {
			$sql['where'] = '( ' . "\n\t" . $indent . implode( ' ' . "\n\t" . $indent . $relation . ' ' . "\n\t" . $indent, $sql_chunks['where'] ) . "\n" . $indent . ')' . "\n";
		}

		return $sql;
	}

	/**
	 * Recursive-friendly query sanitizer.
	 *
	 * Ensures that each query-level clause has a 'relation' key, and that
	 * each first-order clause contains all the necessary keys from
	 * $defaults.
	 *
	 * Extend this method if your class uses different sanitizing logic.
	 *
	 * @since 2.2.0
	 *
	 * @param array $queries Array of query clauses.
	 *
	 * @return array Sanitized array of query clauses.
	 */
	protected function sanitize_query( $queries ) {
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
	 * Generate JOIN and WHERE clauses for a first-order clause.
	 *
	 * Must be overridden in a subclass.
	 *
	 * @since 2.2.0
	 *
	 * @param array $clause       Array of arguments belonging to the clause.
	 * @param array $parent_query Parent query to which the clause belongs.
	 *
	 * @return array {
	 *     @type array $join  Array of subclauses for the JOIN statement.
	 *     @type array $where Array of subclauses for the WHERE statement.
	 * }
	 */
	abstract protected function get_sql_for_clause( $clause, $parent_query );

	/**
	 * Determine whether a clause is first-order.
	 *
	 * Must be overridden in a subclass.
	 *
	 * @since 2.2.0
	 *
	 * @param array $query Clause to check.
	 *
	 * @return bool
	 */
	abstract protected function is_first_order_clause( $query );
}
