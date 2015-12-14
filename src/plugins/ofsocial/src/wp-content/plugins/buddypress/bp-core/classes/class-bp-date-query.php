<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

if ( class_exists( 'WP_Date_Query' ) ) :
/**
 * BuddyPress date query class.
 *
 * Extends the {@link WP_Date_Query} class for use with BuddyPress.
 *
 * @since 2.1.0
 *
 * @param array $date_query {
 *     Date query arguments.  See first parameter of {@link WP_Date_Query::__construct()}.
 * }
 * @param string $column The DB column to query against.
 */
class BP_Date_Query extends WP_Date_Query {
	/**
	 * The column to query against. Can be changed via the query arguments.
	 *
	 * @var string
	 */
	public $column;

	/**
	 * Constructor.
	 *
	 * @param array  $date_query
	 * @param string $column
	 *
	 * @see WP_Date_Query::__construct()
	 */
	public function __construct( $date_query, $column = '' ) {
		if ( ! empty( $column ) ) {
			$this->column = $column;
			add_filter( 'date_query_valid_columns', array( $this, 'register_date_column' ) );
		}

		parent::__construct( $date_query, $column );
	}

	/**
	 * Destructor.
	 */
	public function __destruct() {
		remove_filter( 'date_query_valid_columns', array( $this, 'register_date_column' ) );
	}

	/**
	 * Registers our date column with WP Date Query to pass validation.
	 *
	 * @param array $retval Current DB columns.
	 *
	 * @return array
	 */
	public function register_date_column( $retval = array() ) {
		$retval[] = $this->column;
		return $retval;
	}
}
endif;
