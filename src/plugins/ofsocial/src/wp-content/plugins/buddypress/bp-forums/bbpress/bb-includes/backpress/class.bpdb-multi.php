<?php
//  backPress Multi DB Class

//  ORIGINAL CODE FROM:
//  Justin Vincent (justin@visunet.ie)
//	http://php.justinvincent.com

require( BACKPRESS_PATH . 'class.bpdb.php' );

class BPDB_Multi extends BPDB {
	/**
	 * Associative array (dbhname => dbh) for established mysql connections
	 * @var array
	 */
        var $dbhs = array();

	var $_force_dbhname = false;
	var $last_table = '';
	var $db_tables = array();
	var $db_servers = array();

	// function BPDB_Multi() {} // Not used - rely on PHP4 constructor from BPDB to call BPDB_Multi::__construct

	function __construct() {
		$args = func_get_args();
		$args = call_user_func_array( array(&$this, '_init'), $args );

		if ( $args['host'] ) {
			$this->db_servers['dbh_global'] = $args;
			$this->db_connect( '/* */' );
		}
	}

	/**
	 * Figure out which database server should handle the query, and connect to it.
	 * @param string query
	 * @return resource mysql database connection
	 */
	function &db_connect( $query = '' ) {
		$false = false;
		if ( empty( $query ) )
			return $false;
		
		$this->last_table = $table = $this->get_table_from_query( $query );
		
		// We can attempt to force the connection identifier in use
		if ( $this->_force_dbhname && isset($this->db_servers[$this->_force_dbhname]) )
			$dbhname = $this->_force_dbhname;
		
		if ( !isset($dbhname) ) {
			if ( isset( $this->db_tables[$table] ) )
				$dbhname = $this->db_tables[$table];
			else
				$dbhname = 'dbh_global';
		}

		if ( !isset($this->db_servers[$dbhname]) )
			return $false;

		if ( isset($this->dbhs[$dbhname]) && is_resource($this->dbhs[$dbhname]) ) // We're already connected!
			return $this->dbhs[$dbhname];
		
		$success = $this->db_connect_host( $this->db_servers[$dbhname] );

		if ( $success && is_resource($this->dbh) ) {
			$this->dbhs[$dbhname] =& $this->dbh;
		} else {
			unset($this->dbhs[$dbhname]);
			unset($this->dbh);
			return $false;
		}

		return $this->dbhs[$dbhname];
	}

	/**
	 * Sets the prefix of the database tables
	 * @param string prefix
	 * @param false|array tables (optional: false)
	 *	table identifiers are array keys
	 *	array values
	 *		empty: set prefix: array( 'posts' => false, 'users' => false, ... )
	 *		string: set to that array value: array( 'posts' => 'my_posts', 'users' => 'my_users' )
	 *		array: array[0] is DB identifier, array[1] is table name: array( 'posts' => array( 'global', 'my_posts' ), 'users' => array( 'users', 'my_users' ) )
	 *	OR array values (with numeric keys): array( 'posts', 'users', ... )
	 *
	 * @return string the previous prefix (mostly only meaningful if all $table parameter was false)
	 */
	function set_prefix( $prefix, $tables = false ) {
		$old_prefix = parent::set_prefix( $prefix, $tables );
		if ( !$old_prefix || is_wp_error($old_prefix) ) {
			return $old_prefix;
		}

		if ( $tables && is_array($tables) ) {
			$_tables = $tables;
		} else {
			$_tables = $this->tables;
		}
		
		foreach ( $_tables as $key => $value ) {
			// array( 'posts' => array( 'global', 'my_posts' ), 'users' => array( 'users', 'my_users' ) )
			if ( is_array($value) && isset($this->db_servers['dbh_' . $value[0]]) ) {
				$this->add_db_table( $value[0], $value[1] );
				$this->$key = $value[1];
			}
		}

		return $old_prefix;
	}

	/**
	 * Find the first table name referenced in a query
	 * @param string query
	 * @return string table
	 */
	function get_table_from_query ( $q ) {
		// Remove characters that can legally trail the table name
		rtrim($q, ';/-#');

		// Quickly match most common queries
		if ( preg_match('/^\s*(?:'
				. 'SELECT.*?\s+FROM'
				. '|INSERT(?:\s+IGNORE)?(?:\s+INTO)?'
				. '|REPLACE(?:\s+INTO)?'
				. '|UPDATE(?:\s+IGNORE)?'
				. '|DELETE(?:\s+IGNORE)?(?:\s+FROM)?'
				. ')\s+`?(\w+)`?/is', $q, $maybe) )
			return $maybe[1];

		// Refer to the previous query
		if ( preg_match('/^\s*SELECT.*?\s+FOUND_ROWS\(\)/is', $q) )
			return $this->last_table;

		// Big pattern for the rest of the table-related queries in MySQL 5.0
		if ( preg_match('/^\s*(?:'
				. '(?:EXPLAIN\s+(?:EXTENDED\s+)?)?SELECT.*?\s+FROM'
				. '|INSERT(?:\s+LOW_PRIORITY|\s+DELAYED|\s+HIGH_PRIORITY)?(?:\s+IGNORE)?(?:\s+INTO)?'
				. '|REPLACE(?:\s+LOW_PRIORITY|\s+DELAYED)?(?:\s+INTO)?'
				. '|UPDATE(?:\s+LOW_PRIORITY)?(?:\s+IGNORE)?'
				. '|DELETE(?:\s+LOW_PRIORITY|\s+QUICK|\s+IGNORE)*(?:\s+FROM)?'
				. '|DESCRIBE|DESC|EXPLAIN|HANDLER'
				. '|(?:LOCK|UNLOCK)\s+TABLE(?:S)?'
				. '|(?:RENAME|OPTIMIZE|BACKUP|RESTORE|CHECK|CHECKSUM|ANALYZE|OPTIMIZE|REPAIR).*\s+TABLE'
				. '|TRUNCATE(?:\s+TABLE)?'
				. '|CREATE(?:\s+TEMPORARY)?\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?'
				. '|ALTER(?:\s+IGNORE)?'
				. '|DROP\s+TABLE(?:\s+IF\s+EXISTS)?'
				. '|CREATE(?:\s+\w+)?\s+INDEX.*\s+ON'
				. '|DROP\s+INDEX.*\s+ON'
				. '|LOAD\s+DATA.*INFILE.*INTO\s+TABLE'
				. '|(?:GRANT|REVOKE).*ON\s+TABLE'
				. '|SHOW\s+(?:.*FROM|.*TABLE)'
				. ')\s+`?(\w+)`?/is', $q, $maybe) )
			return $maybe[1];

		// All unmatched queries automatically fall to the global master
		return '';
	}

	/**
	 * Add a database server's information.  Does not automatically connect.
	 * @param string $ds Dataset: the name of the dataset.
	 * @param array $args
	 *	name => string DB name (required)
	 *	user => string DB user (optional: false)
	 *	password => string DB user password (optional: false)
	 *	host => string DB hostname (optional: 'localhost')
	 *	charset => string DB default charset.  Used in a SET NAMES query. (optional)
	 *	collate => string DB default collation.  If charset supplied, optionally added to the SET NAMES query (optional)
	 */
	function add_db_server( $ds, $args = null ) {
		$defaults = array(
			'user' => false,
			'password' => false,
			'name' => false,
			'host' => 'localhost',
			'charset' => false,
			'collate' => false
		);

		$args = wp_parse_args( $args, $defaults );
		$args['ds'] = 'dbh_' . $ds;

		$this->db_servers['dbh_' . $ds] = $args;
	}

	/**
	 * Maps a table to a dataset.
	 * @param string $ds Dataset: the name of the dataset.
	 * @param string $table
	 */
	function add_db_table( $ds, $table ) {
		$this->db_tables[$table] = 'dbh_' . $ds;
	}
}
