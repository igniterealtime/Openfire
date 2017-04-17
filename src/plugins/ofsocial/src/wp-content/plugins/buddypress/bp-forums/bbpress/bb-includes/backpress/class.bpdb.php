<?php
//  backPress DB Class

//  ORIGINAL CODE FROM:
//  Justin Vincent (justin@visunet.ie)
//	http://php.justinvincent.com

define( 'EZSQL_VERSION', 'BP1.25' );
define( 'OBJECT', 'OBJECT', true );
define( 'OBJECT_K', 'OBJECT_K', false );
define( 'ARRAY_A', 'ARRAY_A', false );
define( 'ARRAY_K', 'ARRAY_K', false );
define( 'ARRAY_N', 'ARRAY_N', false );

if ( !defined( 'SAVEQUERIES' ) ) {
	define( 'SAVEQUERIES', false );
}

if ( !defined( 'BPDB__ERROR_STRING' ) ) {
	define( 'BPDB__ERROR_STRING', 'DB Error: %s, %s: %s' );
}
if ( !defined( 'BPDB__ERROR_HTML' ) ) {
	define( 'BPDB__ERROR_HTML', '<div class="error"><p><strong>DB Error in %3$s:</strong> %1$s</p><pre>%2$s</pre></div>' );
}
if ( !defined( 'BPDB__CONNECT_ERROR_MESSAGE' ) ) {
	define( 'BPDB__CONNECT_ERROR_MESSAGE', 'DB Error: cannot connect' );
}
if ( !defined( 'BPDB__SELECT_ERROR_MESSAGE' ) ) {
	define( 'BPDB__SELECT_ERROR_MESSAGE', 'DB Error: cannot select' );
}
if ( !defined( 'BPDB__DB_VERSION_ERROR' ) ) {
	define( 'BPDB__DB_VERSION_ERROR', 'DB Requires MySQL version 4.0 or higher' );
}
if ( !defined( 'BPDB__PHP_EXTENSION_MISSING' ) ) {
	define( 'BPDB__PHP_EXTENSION_MISSING', 'DB Requires The MySQL PHP extension' );
}

class BPDB
{
	/**
	 * Whether to show SQL/DB errors
	 *
	 * @since 1.0
	 * @access private
	 * @var bool
	 */
	var $show_errors = false;

	/**
	 * Whether to suppress errors during the DB bootstrapping.
	 *
	 * @access private
	 * @since 1.0
	 * @var bool
	 */
	var $suppress_errors = false;

	/**
	 * The last error during query.
	 *
	 * @since 1.0
	 * @var string
	 */
	var $last_error = '';

	/**
	 * Amount of queries made
	 *
	 * @since 1.0
	 * @access private
	 * @var int
	 */
	var $num_queries = 0;

	/**
	 * The last query made
	 *
	 * @since 1.0
	 * @access private
	 * @var string
	 */
	var $last_query = null;

	/**
	 * Saved info on the table column
	 *
	 * @since 1.0
	 * @access private
	 * @var array
	 */
	var $col_info = array();
	
	/**
	 * Saved queries that were executed
	 *
	 * @since 1.0
	 * @access private
	 * @var array
	 */
	var $queries = array();

	/**
	 * Whether to use the query log
	 *
	 * @since 1.0
	 * @access private
	 * @var bool
	 */
	var $save_queries = false;

	/**
	 * Table prefix
	 *
	 * You can set this to have multiple installations
	 * in a single database. The second reason is for possible
	 * security precautions.
	 *
	 * @since 1.0
	 * @access private
	 * @var string
	 */
	var $prefix = '';
	
	/**
	 * Whether the database queries are ready to start executing.
	 *
	 * @since 1.0
	 * @access private
	 * @var bool
	 */
	var $ready = false;

	/**
	 * The currently connected MySQL connection resource.
	 *
	 * @since 1.0
	 * @access private
	 * @var bool|resource
	 */
	var $dbh = false;

	/**
	 * List of tables
	 *
	 * @since 1.0
	 * @access private
	 * @var array
	 */
	var $tables = array();

	/**
	 * Whether to use mysql_real_escape_string
	 *
	 * @since 1.0
	 * @access public
	 * @var bool
	 */
	var $real_escape = false;

	/**
	 * PHP4 style constructor
	 *
	 * @since 1.0
	 *
	 * @return unknown Returns the result of bpdb::__construct()
	 */
	function BPDB()
	{
		$args = func_get_args();
		register_shutdown_function( array( &$this, '__destruct' ) );
		return call_user_func_array( array( &$this, '__construct' ), $args );
	}

	/**
	 * PHP5 style constructor
	 *
	 * Grabs the arguments, calls bpdb::_init() and then connects to the database
	 *
	 * @since 1.0
	 *
	 * @return void
	 */
	function __construct()
	{
		$args = func_get_args();
		$args = call_user_func_array( array( &$this, '_init' ), $args );

		$this->db_connect_host( $args );
	}

	/**
	 * Initialises the class variables based on provided arguments
	 *
	 * @since 1.0
	 *
	 * @param array $args The provided connection settings
	 * @return array The current connection settings after processing by init
	 */
	function _init( $args )
	{
		if ( !extension_loaded( 'mysql' ) ) {
			$this->show_errors();
			$this->bail( BPDB__PHP_EXTENSION_MISSING );
			return;
		}

		if ( 4 == func_num_args() ) {
			$args = array(
				'user' => $args,
				'password' => func_get_arg( 1 ),
				'name' => func_get_arg( 2 ),
				'host' => func_get_arg( 3 )
			);
		}

		$defaults = array(
			'user' => false,
			'password' => false,
			'name' => false,
			'host' => 'localhost',
			'charset' => false,
			'collate' => false,
			'errors' => false
		);

		$args = wp_parse_args( $args, $defaults );

		switch ( $args['errors'] ) {
			case 'show' :
				$this->show_errors( true );
				break;
			case 'suppress' :
				$this->suppress_errors( true );
				break;
		}

		return $args;
	}

	/**
	 * PHP5 style destructor, registered as shutdown function in PHP4
	 *
	 * @since 1.0
	 *
	 * @return bool Always returns true
	 */
	function __destruct()
	{
		return true;
	}

	/**
	 * Figure out which database server should handle the query, and connect to it.
	 *
	 * @since 1.0
	 *
	 * @param string query
	 * @return resource mysql database connection
	 */
	function &db_connect( $query = '' )
	{
		$false = false;
		if ( empty( $query ) ) {
			return $false;
		}
		return $this->dbh;
	}

	/**
	 * Connects to the database server and selects a database
	 *
	 * @since 1.0
	 *
	 * @param array args
	 *	name => string DB name (required)
	 *	user => string DB user (optional: false)
	 *	password => string DB user password (optional: false)
	 *	host => string DB hostname (optional: 'localhost')
	 *	charset => string DB default charset.  Used in a SET NAMES query. (optional)
	 *	collate => string DB default collation.  If charset supplied, optionally added to the SET NAMES query (optional)
	 * @return void|bool void if cannot connect, false if cannot select, true if success
	 */
	function db_connect_host( $args )
	{
		extract( $args, EXTR_SKIP );

		unset( $this->dbh ); // De-reference before re-assigning
		$this->dbh = @mysql_connect( $host, $user, $password, true );

		if ( !$this->dbh ) {
			if ( !$this->suppress_errors ) {
				$this->show_errors();
			}
			$this->bail( BPDB__CONNECT_ERROR_MESSAGE );
			return;
		}

		$this->ready = true;

		if ( $this->has_cap( 'collation' ) ) {
			if ( !empty( $charset ) ) {
				if ( function_exists( 'mysql_set_charset' ) ) {
					mysql_set_charset( $charset, $this->dbh );
					$this->real_escape = true;
				} else {
					$collation_query = "SET NAMES '{$charset}'";
					if ( !empty( $collate ) ) {
						$collation_query .= " COLLATE '{$collate}'";
					}
					$this->query( $collation_query, true );
				}
			}
		}

		return $this->select( $name, $this->dbh );
	}

	/**
	 * Sets the table prefix for the WordPress tables.
	 *
	 * @since 1.0
	 *
	 * @param string prefix
	 * @param false|array tables (optional: false)
	 *	table identifiers are array keys
	 *	array values
	 *		empty: set prefix: array( 'posts' => false, 'users' => false, ... )
	 *		string: set to that array value: array( 'posts' => 'my_posts', 'users' => 'my_users' )
	 *	OR array values (with numeric keys): array( 'posts', 'users', ... )
	 *
	 * @return string the previous prefix (mostly only meaningful if all $table parameter was false)
	 */
	function set_prefix( $prefix, $tables = false )
	{
		if ( !$prefix ) {
			return false;
		}
		if ( preg_match( '|[^a-z0-9_]|i', $prefix ) ) {
			return new WP_Error( 'invalid_db_prefix', 'Invalid database prefix' ); // No gettext here
		}

		$old_prefix = $this->prefix;

		if ( $tables && is_array( $tables ) ) {
			$_tables =& $tables;
		} else {
			$_tables =& $this->tables;
			$this->prefix = $prefix;
		}

		foreach ( $_tables as $key => $value ) {
			if ( is_numeric( $key ) ) { // array( 'posts', 'users', ... )
				$this->$value = $prefix . $value;
			} elseif ( !$value ) {
				$this->$key = $prefix . $key; // array( 'posts' => false, 'users' => false, ... )
			} elseif ( is_string( $value ) ) { // array( 'posts' => 'my_posts', 'users' => 'my_users' )
				$this->$key = $value;
			}
		}

		return $old_prefix;
	}

	/**
	 * Selects a database using the current database connection.
	 *
	 * The database name will be changed based on the current database
	 * connection. On failure, the execution will bail and display an DB error.
	 *
	 * @since 1.0
	 *
	 * @param string $db MySQL database name
	 * @return bool True on success, false on failure.
	 */
	function select( $db, &$dbh )
	{
		if ( !@mysql_select_db( $db, $dbh ) ) {
			$this->ready = false;
			$this->show_errors();
			$this->bail( BPDB__SELECT_ERROR_MESSAGE );
			return false;
		}
		return true;
	}

	function _weak_escape( $string )
	{
		return addslashes( $string );
	}

	function _real_escape( $string )
	{
		if ( $this->dbh && $this->real_escape ) {
			return mysql_real_escape_string( $string, $this->dbh );
		} else {
			return addslashes( $string );
		}
	}

	function _escape( $data )
	{
		if ( is_array( $data ) ) {
			foreach ( (array) $data as $k => $v ) {
				if ( is_array( $v ) ) {
					$data[$k] = $this->_escape( $v );
				} else {
					$data[$k] = $this->_real_escape( $v );
				}
			}
		} else {
			$data = $this->_real_escape( $data );
		}

		return $data;
	}

	/**
	 * Escapes content for insertion into the database using addslashes(), for security
	 *
	 * @since 1.0
	 *
	 * @param string|array $data
	 * @return string query safe string
	 */
	function escape( $data )
	{
		if ( is_array( $data ) ) {
			foreach ( (array) $data as $k => $v ) {
				if ( is_array( $v ) ) {
					$data[$k] = $this->escape( $v );
				} else {
					$data[$k] = $this->_weak_escape( $v );
				}
			}
		} else {
			$data = $this->_weak_escape( $data );
		}

		return $data;
	}

	/**
	 * Escapes content by reference for insertion into the database, for security
	 *
	 * @since 1.0
	 *
	 * @param string $s
	 */
	function escape_by_ref( &$string )
	{
		$string = $this->_real_escape( $string );
	}

	/**
	 * Escapes array recursively for insertion into the database, for security
	 * @param array $array
	 */
	function escape_deep( $array )
	{
		return $this->_escape( $array );
	}

	/**
	 * Prepares a SQL query for safe execution.  Uses sprintf()-like syntax.
	 *
	 * This function only supports a small subset of the sprintf syntax; it only supports %d (decimal number), %s (string).
	 * Does not support sign, padding, alignment, width or precision specifiers.
	 * Does not support argument numbering/swapping.
	 *
	 * May be called like {@link http://php.net/sprintf sprintf()} or like {@link http://php.net/vsprintf vsprintf()}.
	 *
	 * Both %d and %s should be left unquoted in the query string.
	 *
	 * <code>
	 * wpdb::prepare( "SELECT * FROM `table` WHERE `column` = %s AND `field` = %d", "foo", 1337 )
	 * </code>
	 *
	 * @link http://php.net/sprintf Description of syntax.
	 * @since 1.0
	 *
	 * @param string $query Query statement with sprintf()-like placeholders
	 * @param array|mixed $args The array of variables to substitute into the query's placeholders if being called like {@link http://php.net/vsprintf vsprintf()}, or the first variable to substitute into the query's placeholders if being called like {@link http://php.net/sprintf sprintf()}.
	 * @param mixed $args,... further variables to substitute into the query's placeholders if being called like {@link http://php.net/sprintf sprintf()}.
	 * @return null|string Sanitized query string
	 */
	function prepare( $query = null ) // ( $query, *$args )
	{
		if ( is_null( $query ) ) {
			return;
		}
		$args = func_get_args();
		array_shift( $args );
		// If args were passed as an array (as in vsprintf), move them up
		if ( isset( $args[0] ) && is_array( $args[0] ) ) {
			$args = $args[0];
		}
		$query = str_replace( "'%s'", '%s', $query ); // in case someone mistakenly already singlequoted it
		$query = str_replace( '"%s"', '%s', $query ); // doublequote unquoting
		$query = str_replace( '%s', "'%s'", $query ); // quote the strings
		array_walk( $args, array( &$this, 'escape_by_ref' ) );
		return @vsprintf( $query, $args );
	}

	/**
	 * Get SQL/DB error
	 *
	 * @since 1.0
	 *
	 * @param string $str Error string
	 */
	function get_error( $str = '' )
	{
		if ( empty( $str ) ) {
			if ( $this->last_error ) {
				$str = $this->last_error;
			} else {
				return false;
			}
		}

		$caller = $this->get_caller();
		$error_str = sprintf( BPDB__ERROR_STRING, $str, $this->last_query, $caller );

		if ( class_exists( 'WP_Error' ) ) {
			return new WP_Error( 'db_query', $error_str, array( 'query' => $this->last_query, 'error' => $str, 'caller' => $caller ) );
		} else {
			return array( 'query' => $this->last_query, 'error' => $str, 'caller' => $caller, 'error_str' => $error_str );
		}
	}

	/**
	 * Print SQL/DB error.
	 *
	 * @since 1.0
	 *
	 * @param string $str The error to display
	 * @return bool False if the showing of errors is disabled.
	 */
	function print_error( $str = '' )
	{
		if ( $this->suppress_errors ) {
			return false;
		}

		$error = $this->get_error( $str );
		if ( is_object( $error ) && is_a( $error, 'WP_Error' ) ) {
			$err = $error->get_error_data();
			$err['error_str'] = $error->get_error_message();
		} else {
			$err =& $error;
		}

		$log_file = ini_get( 'error_log' );
		if ( !empty( $log_file ) && ( 'syslog' != $log_file ) && is_writable( $log_file ) && function_exists( 'error_log' ) ) {
			error_log($err['error_str'], 0);
		}

		// Is error output turned on or not
		if ( !$this->show_errors ) {
			return false;
		}

		$str = htmlspecialchars( $err['error'], ENT_QUOTES );
		$query = htmlspecialchars( $err['query'], ENT_QUOTES );
		$caller = htmlspecialchars( $err['caller'], ENT_QUOTES );

		// If there is an error then take note of it

		printf( BPDB__ERROR_HTML, $str, $query, $caller );
	}

	/**
	 * Enables showing of database errors.
	 *
	 * This function should be used only to enable showing of errors.
	 * bpdb::hide_errors() should be used instead for hiding of errors. However,
	 * this function can be used to enable and disable showing of database
	 * errors.
	 *
	 * @since 1.0
	 *
	 * @param bool $show Whether to show or hide errors
	 * @return bool Old value for showing errors.
	 */
	function show_errors( $show = true )
	{
		$errors = $this->show_errors;
		$this->show_errors = $show;
		return $errors;
	}

	/**
	 * Disables showing of database errors.
	 *
	 * @since 1.0
	 *
	 * @return bool Whether showing of errors was active or not
	 */
	function hide_errors()
	{
		return $this->show_errors( false );
	}

	/**
	 * Whether to suppress database errors.
	 *
	 * @since 1.0
	 *
	 * @param bool $suppress
	 * @return bool previous setting
	 */
	function suppress_errors( $suppress = true )
	{
		$errors = $this->suppress_errors;
		$this->suppress_errors = $suppress;
		return $errors;
	}

	/**
	 * Kill cached query results.
	 *
	 * @since 1.0
	 */
	function flush()
	{
		$this->last_result = array();
		$this->col_info = array();
		$this->last_query = null;
		$this->last_error = '';
		$this->num_rows = 0;
	}

	/**
	 * Perform a MySQL database query, using current database connection.
	 *
	 * More information can be found on the codex page.
	 *
	 * @since 1.0
	 *
	 * @param string $query
	 * @return int|false Number of rows affected/selected or false on error
	 */
	function query( $query, $use_current = false )
	{
		if ( !$this->ready ) {
			return false;
		}

		// filter the query, if filters are available
		// NOTE: some queries are made before the plugins have been loaded, and thus cannot be filtered with this method
		if ( function_exists( 'apply_filters' ) ) {
			$query = apply_filters( 'query', $query );
		}

		// initialise return
		$return_val = 0;
		$this->flush();

		// Log how the function was called
		$this->func_call = "\$db->query(\"$query\")";

		// Keep track of the last query for debug..
		$this->last_query = $query;

		// Perform the query via std mysql_query function..
		if ( SAVEQUERIES ) {
			$this->timer_start();
		}

		if ( $use_current ) {
			$dbh =& $this->dbh;
		} else {
			$dbh = $this->db_connect( $query );
		}

		$this->result = @mysql_query( $query, $dbh );
		++$this->num_queries;

		if ( SAVEQUERIES ) {
			$this->queries[] = array( $query, $this->timer_stop(), $this->get_caller() );
		}

		// If there is an error then take note of it..
		if ( $this->last_error = mysql_error( $dbh ) ) {
			return $this->print_error( $this->last_error );
		}

		if ( preg_match( "/^\\s*(insert|delete|update|replace|alter) /i", $query ) ) {
			$this->rows_affected = mysql_affected_rows( $dbh );
			// Take note of the insert_id
			if ( preg_match( "/^\\s*(insert|replace) /i", $query ) ) {
				$this->insert_id = mysql_insert_id( $dbh );
			}
			// Return number of rows affected
			$return_val = $this->rows_affected;
		} else {
			$i = 0;
			while ( $i < @mysql_num_fields( $this->result ) ) {
				$this->col_info[$i] = @mysql_fetch_field( $this->result );
				$i++;
			}
			$num_rows = 0;
			while ( $row = @mysql_fetch_object( $this->result ) ) {
				$this->last_result[$num_rows] = $row;
				$num_rows++;
			}

			@mysql_free_result( $this->result );

			// Log number of rows the query returned
			$this->num_rows = $num_rows;

			// Return number of rows selected
			$return_val = $this->num_rows;
		}

		return $return_val;
	}

	/**
	 * Insert a row into a table.
	 *
	 * <code>
	 * wpdb::insert( 'table', array( 'column' => 'foo', 'field' => 1337 ), array( '%s', '%d' ) )
	 * </code>
	 *
	 * @since 1.0
	 * @see bpdb::prepare()
	 *
	 * @param string $table table name
	 * @param array $data Data to insert (in column => value pairs).  Both $data columns and $data values should be "raw" (neither should be SQL escaped).
	 * @param array|string $format (optional) An array of formats to be mapped to each of the value in $data.  If string, that format will be used for all of the values in $data.  A format is one of '%d', '%s' (decimal number, string).  If omitted, all values in $data will be treated as strings.
	 * @return int|false The number of rows inserted, or false on error.
	 */
	function insert( $table, $data, $format = null )
	{
		$formats = $format = (array) $format;
		$fields = array_keys( $data );
		$formatted_fields = array();
		foreach ( $fields as $field ) {
			if ( !empty( $format ) ) {
				$form = ( $form = array_shift( $formats ) ) ? $form : $format[0];
			} elseif ( isset( $this->field_types[$field] ) ) {
				$form = $this->field_types[$field];
			} elseif ( is_null( $data[$field] ) ) {
				$form = 'NULL';
				unset( $data[$field] );
			} else {
				$form = '%s';
			}
			$formatted_fields[] = $form;
		}
		$sql = "INSERT INTO `$table` (`" . implode( '`,`', $fields ) . "`) VALUES (" . implode( ",", $formatted_fields ) . ")";
		return $this->query( $this->prepare( $sql, $data ) );
	}

	/**
	 * Update a row in the table
	 *
	 * <code>
	 * wpdb::update( 'table', array( 'column' => 'foo', 'field' => 1337 ), array( 'ID' => 1 ), array( '%s', '%d' ), array( '%d' ) )
	 * </code>
	 *
	 * @since 1.0
	 * @see bpdb::prepare()
	 *
	 * @param string $table table name
	 * @param array $data Data to update (in column => value pairs).  Both $data columns and $data values should be "raw" (neither should be SQL escaped).
	 * @param array $where A named array of WHERE clauses (in column => value pairs).  Multiple clauses will be joined with ANDs.  Both $where columns and $where values should be "raw".
	 * @param array|string $format (optional) An array of formats to be mapped to each of the values in $data.  If string, that format will be used for all of the values in $data.  A format is one of '%d', '%s' (decimal number, string).  If omitted, all values in $data will be treated as strings.
	 * @param array|string $format_where (optional) An array of formats to be mapped to each of the values in $where.  If string, that format will be used for all of  the items in $where.  A format is one of '%d', '%s' (decimal number, string).  If omitted, all values in $where will be treated as strings.
	 * @return int|false The number of rows updated, or false on error.
	 */
	function update( $table, $data, $where, $format = null, $where_format = null )
	{
		if ( !is_array( $where ) ) {
			return false;
		}

		$formats = $format = (array) $format;
		$bits = $wheres = array();
		foreach ( (array) array_keys( $data ) as $field ) {
			if ( !empty( $format ) ) {
				$form = ( $form = array_shift( $formats ) ) ? $form : $format[0];
			} elseif ( isset( $this->field_types[$field] ) ) {
				$form = $this->field_types[$field];
			} elseif ( is_null( $data[$field] ) ) {
				$form = 'NULL';
				unset( $data[$field] );
			} else {
				$form = '%s';
			}
			$bits[] = "`$field` = {$form}";
		}

		$where_formats = $where_format = (array) $where_format;
		foreach ( (array) array_keys( $where ) as $field ) {
			if ( !empty( $where_format ) ) {
				$form = ( $form = array_shift( $where_formats ) ) ? $form : $where_format[0];
			} elseif ( isset( $this->field_types[$field] ) ) {
				$form = $this->field_types[$field];
			} elseif ( is_null( $where[$field] ) ) {
				unset( $where[$field] );
				$wheres[] = "`$field` IS NULL";
				continue;
			} else {
				$form = '%s';
			}
			$wheres[] = "`$field` = {$form}";
		}

		$sql = "UPDATE `$table` SET " . implode( ', ', $bits ) . ' WHERE ' . implode( ' AND ', $wheres );
		return $this->query( $this->prepare( $sql, array_merge( array_values( $data ), array_values( $where ) ) ) );
	}

	/**
	 * Retrieve one variable from the database.
	 *
	 * Executes a SQL query and returns the value from the SQL result.
	 * If the SQL result contains more than one column and/or more than one row, this function returns the value in the column and row specified.
	 * If $query is null, this function returns the value in the specified column and row from the previous SQL result.
	 *
	 * @since 1.0
	 *
	 * @param string|null $query SQL query.  If null, use the result from the previous query.
	 * @param int $x (optional) Column of value to return.  Indexed from 0.
	 * @param int $y (optional) Row of value to return.  Indexed from 0.
	 * @return string Database query result
	 */
	function get_var( $query=null, $x = 0, $y = 0 )
	{
		$this->func_call = "\$db->get_var(\"$query\",$x,$y)";
		if ( $query ) {
			$this->query( $query );
		}

		// Extract var out of cached results based x,y vals
		if ( !empty( $this->last_result[$y] ) ) {
			$values = array_values( get_object_vars( $this->last_result[$y] ) );
		}

		// If there is a value return it else return null
		return ( isset($values[$x]) && $values[$x]!=='' ) ? $values[$x] : null;
	}

	/**
	 * Retrieve one row from the database.
	 *
	 * Executes a SQL query and returns the row from the SQL result.
	 *
	 * @since 1.0
	 *
	 * @param string|null $query SQL query.
	 * @param string $output (optional) one of ARRAY_A | ARRAY_N | OBJECT constants.  Return an associative array (column => value, ...), a numerically indexed array (0 => value, ...) or an object ( ->column = value ), respectively.
	 * @param int $y (optional) Row to return.  Indexed from 0.
	 * @return mixed Database query result in format specifed by $output
	 */
	function get_row( $query = null, $output = OBJECT, $y = 0 )
	{
		$this->func_call = "\$db->get_row(\"$query\",$output,$y)";
		if ( $query ) {
			$this->query( $query );
		} else {
			return null;
		}

		if ( !isset( $this->last_result[$y] ) ) {
			return null;
		}

		if ( $output == OBJECT ) {
			return $this->last_result[$y] ? $this->last_result[$y] : null;
		} elseif ( $output == ARRAY_A ) {
			return $this->last_result[$y] ? get_object_vars( $this->last_result[$y] ) : null;
		} elseif ( $output == ARRAY_N ) {
			return $this->last_result[$y] ? array_values( get_object_vars( $this->last_result[$y] ) ) : null;
		} else {
			$this->print_error( " \$db->get_row(string query, output type, int offset) -- Output type must be one of: OBJECT, ARRAY_A, ARRAY_N" );
		}
	}

	/**
	 * Retrieve one column from the database.
	 *
	 * Executes a SQL query and returns the column from the SQL result.
	 * If the SQL result contains more than one column, this function returns the column specified.
	 * If $query is null, this function returns the specified column from the previous SQL result.
	 *
	 * @since 1.0
	 *
	 * @param string|null $query SQL query.  If null, use the result from the previous query.
	 * @param int $x Column to return.  Indexed from 0.
	 * @return array Database query result.  Array indexed from 0 by SQL result row number.
	 */
	function get_col( $query = null , $x = 0 )
	{
		if ( $query ) {
			$this->query( $query );
		}

		$new_array = array();
		// Extract the column values
		for ( $i=0; $i < count( $this->last_result ); $i++ ) {
			$new_array[$i] = $this->get_var( null, $x, $i );
		}
		return $new_array;
	}

	/**
	 * Retrieve an entire SQL result set from the database (i.e., many rows)
	 *
	 * Executes a SQL query and returns the entire SQL result.
	 *
	 * @since 1.0
	 *
	 * @param string $query SQL query.
	 * @param string $output (optional) ane of ARRAY_A | ARRAY_N | OBJECT | OBJECT_K | ARRAY_K constants.  With one of the first three, return an array of rows indexed from 0 by SQL result row number.  Each row is an associative array (column => value, ...), a numerically indexed array (0 => value, ...), or an object. ( ->column = value ), respectively.  With OBJECT_K and ARRAY_K, return an associative array of row objects keyed by the value of each row's first column's value.  Duplicate keys are discarded.
	 * @return mixed Database query results
	 */
	function get_results( $query = null, $output = OBJECT ) 
	{
		$this->func_call = "\$db->get_results(\"$query\", $output)";

		if ( $query ) {
			$this->query($query);
		} else {
			return null;
		}

		if ( $output == OBJECT ) {
			// Return an integer-keyed array of row objects
			return $this->last_result;
		} elseif ( $output == OBJECT_K || $output == ARRAY_K ) {
			// Return an array of row objects with keys from column 1
			// (Duplicates are discarded)
			$key = $this->col_info[0]->name;
			foreach ( $this->last_result as $row ) {
				if ( !isset( $new_array[ $row->$key ] ) ) {
					$new_array[ $row->$key ] = $row;
				}
			}
			if ( $output == ARRAY_K ) {
				return array_map( 'get_object_vars', $new_array );
			}
			return $new_array;
		} elseif ( $output == ARRAY_A || $output == ARRAY_N ) {
			// Return an integer-keyed array of...
			if ( $this->last_result ) {
				$i = 0;
				foreach( $this->last_result as $row ) {
					if ( $output == ARRAY_N ) {
						// ...integer-keyed row arrays
						$new_array[$i] = array_values( get_object_vars( $row ) );
					} else {
						// ...column name-keyed row arrays
						$new_array[$i] = get_object_vars( $row );
					}
					++$i;
				}
				return $new_array;
			}
		}
	}

	/**
	 * Retrieve column metadata from the last query.
	 *
	 * @since 1.0
	 *
	 * @param string $info_type one of name, table, def, max_length, not_null, primary_key, multiple_key, unique_key, numeric, blob, type, unsigned, zerofill
	 * @param int $col_offset 0: col name. 1: which table the col's in. 2: col's max length. 3: if the col is numeric. 4: col's type
	 * @return mixed Column Results
	 */
	function get_col_info( $info_type = 'name', $col_offset = -1 )
	{
		if ( $this->col_info ) {
			if ( $col_offset == -1 ) {
				$i = 0;
				foreach( (array) $this->col_info as $col ) {
					$new_array[$i] = $col->{$info_type};
					$i++;
				}
				return $new_array;
			} else {
				return $this->col_info[$col_offset]->{$info_type};
			}
		}
	}

	/**
	 * Starts the timer, for debugging purposes.
	 *
	 * @since 1.0
	 *
	 * @return true
	 */
	function timer_start()
	{
		$mtime = microtime();
		$mtime = explode( ' ', $mtime );
		$this->time_start = $mtime[1] + $mtime[0];
		return true;
	}

	/**
	 * Stops the debugging timer.
	 *
	 * @since 1.0
	 *
	 * @return int Total time spent on the query, in milliseconds
	 */
	function timer_stop()
	{
		$mtime = microtime();
		$mtime = explode( ' ', $mtime );
		$time_end = $mtime[1] + $mtime[0];
		$time_total = $time_end - $this->time_start;
		return $time_total;
	}

	/**
	 * Wraps errors in a nice header and footer and dies.
	 *
	 * Will not die if bpdb::$show_errors is true
	 *
	 * @since 1.0
	 *
	 * @param string $message
	 * @return false|void
	 */
	function bail( $message )
	{
		if ( !$this->show_errors ) {
			if ( class_exists( 'WP_Error' ) )
				$this->error = new WP_Error( '500', $message );
			else
				$this->error = $message;
			return false;
		}
		backpress_die( $message );
	}

	/**
	 * Whether or not MySQL database is at least the required minimum version.
	 *
	 * @since 1.0
	 *
	 * @return WP_Error
	 */
	function check_database_version( $dbh_or_table = false )
	{
		// Make sure the server has MySQL 4.0
		if ( version_compare( $this->db_version( $dbh_or_table ), '4.0.0', '<' ) ) {
			return new WP_Error( 'database_version', BPDB__DB_VERSION_ERROR );
		}
	}

	/**
	 * Whether of not the database supports collation.
	 *
	 * Called when BackPress is generating the table scheme.
	 *
	 * @since 1.0
	 *
	 * @return bool True if collation is supported, false if version does not
	 */
	function supports_collation()
	{
		return $this->has_cap( 'collation' );
	}

	/**
	 * Generic function to determine if a database supports a particular feature
	 *
	 * @since 1.0
	 *
	 * @param string $db_cap the feature
	 * @param false|string|resource $dbh_or_table Which database to test.  False = the currently selected database, string = the database containing the specified table, resource = the database corresponding to the specified mysql resource.
	 * @return bool
	 */
	function has_cap( $db_cap, $dbh_or_table = false )
	{
		$version = $this->db_version( $dbh_or_table );

		switch ( strtolower( $db_cap ) ) {
			case 'collation' :
			case 'group_concat' :
			case 'subqueries' :
				return version_compare( $version, '4.1', '>=' );
				break;

			case 'index_hint_for_join' :
				return version_compare( $version, '5.0', '>=' );
				break;

			case 'index_hint_lists' :
			case 'index_hint_for_any' :
				return version_compare( $version, '5.1', '>=' );
				break;
		}

		return false;
	}

	/**
	 * The database version number
	 *
	 * @since 1.0
	 *
	 * @param false|string|resource $dbh_or_table Which database to test. False = the currently selected database, string = the database containing the specified table, resource = the database corresponding to the specified mysql resource.
	 * @return false|string false on failure, version number on success
	 */
	function db_version( $dbh_or_table = false )
	{
		if ( !$dbh_or_table ) {
			$dbh =& $this->dbh;
		} elseif ( is_resource( $dbh_or_table ) ) {
			$dbh =& $dbh_or_table;
		} else {
			$dbh = $this->db_connect( "DESCRIBE $dbh_or_table" );
		}

		if ( $dbh ) {
			return preg_replace( '|[^0-9\.]|', '', mysql_get_server_info( $dbh ) );
		}
		return false;
	}

	/**
	 * Retrieve the name of the function that called bpdb.
	 *
	 * Requires PHP 4.3 and searches up the list of functions until it reaches
	 * the one that would most logically had called this method.
	 *
	 * @since 1.0
	 *
	 * @return string The name of the calling function
	 */
	function get_caller()
	{
		// requires PHP 4.3+
		if ( !is_callable( 'debug_backtrace' ) ) {
			return '';
		}

		$bt = debug_backtrace();
		$caller = array();

		$bt = array_reverse( $bt );
		foreach ( (array) $bt as $call ) {
			if ( @$call['class'] == __CLASS__ ) {
				continue;
			}
			$function = $call['function'];
			if ( isset( $call['class'] ) ) {
				$function = $call['class'] . "->$function";
			}
			$caller[] = $function;
		}
		$caller = join( ', ', $caller );

		return $caller;
	}
}
