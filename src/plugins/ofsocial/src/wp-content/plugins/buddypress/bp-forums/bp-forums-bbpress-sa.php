<?php
/**
 * BuddyPress bbPress 1.x integration.
 *
 * @package BuddyPress
 * @subpackage ForumsbbPress
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Bootstrap bbPress 1.x, and manipulate globals to integrate with BuddyPress.
 *
 * @return bool|null Returns false on failure.
 */
function bp_forums_load_bbpress() {
	global $wpdb, $wp_roles, $current_user, $wp_users_object;
	global $bb, $bbdb, $bb_table_prefix, $bb_current_user;
	global $bb_roles, $wp_taxonomy_object, $bb_queries;

	// Return if we've already run this function.
	if ( is_object( $bbdb ) )
		return;

	if ( !bp_forums_is_installed_correctly() )
		return false;

	$bp = buddypress();

	define( 'BB_PATH',        $bp->plugin_dir . '/bp-forums/bbpress/' );
	define( 'BACKPRESS_PATH', $bp->plugin_dir . '/bp-forums/bbpress/bb-includes/backpress/' );
	define( 'BB_URL',         $bp->plugin_url . 'bp-forums/bbpress/' );
	define( 'BB_INC', 'bb-includes/' );

	require( BB_PATH . BB_INC . 'class.bb-query.php' );
	require( BB_PATH . BB_INC . 'class.bb-walker.php' );

	require( BB_PATH . BB_INC . 'functions.bb-core.php' );
	require( BB_PATH . BB_INC . 'functions.bb-forums.php' );
	require( BB_PATH . BB_INC . 'functions.bb-topics.php' );
	require( BB_PATH . BB_INC . 'functions.bb-posts.php' );
	require( BB_PATH . BB_INC . 'functions.bb-topic-tags.php' );
	require( BB_PATH . BB_INC . 'functions.bb-capabilities.php' );
	require( BB_PATH . BB_INC . 'functions.bb-meta.php' );
	require( BB_PATH . BB_INC . 'functions.bb-pluggable.php' );
	require( BB_PATH . BB_INC . 'functions.bb-formatting.php' );
	require( BB_PATH . BB_INC . 'functions.bb-template.php' );

	require( BACKPRESS_PATH . 'class.wp-taxonomy.php' );
	require( BB_PATH . BB_INC . 'class.bb-taxonomy.php' );

	require( BB_PATH . 'bb-admin/includes/functions.bb-admin.php' );

	$bb = new stdClass();
	require( bp_get_option(	'bb-config-location' ) );

	// Setup the global database connection.
	$bbdb = new BPDB ( BBDB_USER, BBDB_PASSWORD, BBDB_NAME, BBDB_HOST );

	// Set the table names.
	$bbdb->forums             = $bb_table_prefix . 'forums';
	$bbdb->meta               = $bb_table_prefix . 'meta';
	$bbdb->posts              = $bb_table_prefix . 'posts';
	$bbdb->terms              = $bb_table_prefix . 'terms';
	$bbdb->term_relationships = $bb_table_prefix . 'term_relationships';
	$bbdb->term_taxonomy      = $bb_table_prefix . 'term_taxonomy';
	$bbdb->topics             = $bb_table_prefix . 'topics';

	if ( isset( $bb->custom_user_table ) )
		$bbdb->users = $bb->custom_user_table;
	else
		$bbdb->users = $wpdb->users;

	if ( isset( $bb->custom_user_meta_table ) )
		$bbdb->usermeta = $bb->custom_user_meta_table;
	else
		$bbdb->usermeta = $wpdb->usermeta;

	$bbdb->prefix = $bb_table_prefix;

	define( 'BB_INSTALLING', false );

	if ( is_object( $wp_roles ) ) {
		$bb_roles = $wp_roles;
		bb_init_roles( $bb_roles );
	}

	/**
	 * Fires during the bootstrap setup for bbPress 1.x.
	 *
	 * @since 1.1.0
	 */
	do_action( 'bb_got_roles' );

	/**
	 * Fires during the bootstrap setup for bbPress 1.x.
	 *
	 * @since 1.1.0
	 */
	do_action( 'bb_init'      );

	/**
	 * Fires during the bootstrap setup for bbPress 1.x.
	 *
	 * @since 1.1.0
	 */
	do_action( 'init_roles'   );

	$bb_current_user = $current_user;
	$wp_users_object = new BP_Forums_BB_Auth;

	if ( !isset( $wp_taxonomy_object ) )
		$wp_taxonomy_object = new BB_Taxonomy( $bbdb );

	$wp_taxonomy_object->register_taxonomy( 'bb_topic_tag', 'bb_topic' );

	// Set a site id if there isn't one already.
	if ( !isset( $bb->site_id ) )
		$bb->site_id = bp_get_root_blog_id();

	// Check if the tables are installed, if not, install them.
	if ( !$tables_installed = (boolean) $bbdb->get_results( 'DESCRIBE `' . $bbdb->forums . '`;', ARRAY_A ) ) {
		require( BB_PATH . 'bb-admin/includes/defaults.bb-schema.php' );

		// Backticks and "IF NOT EXISTS" break the dbDelta function.
		bp_bb_dbDelta( str_replace( ' IF NOT EXISTS', '', str_replace( '`', '', $bb_queries ) ) );

		require( BB_PATH . 'bb-admin/includes/functions.bb-upgrade.php' );
		bb_update_db_version();

		// Set the site admins as the keymasters.
		$site_admins = get_site_option( 'site_admins', array('admin') );
		foreach ( (array) $site_admins as $site_admin )
			bp_update_user_meta( bp_core_get_userid( $site_admin ), $bb_table_prefix . 'capabilities', array( 'keymaster' => true ) );

		// Create the first forum.
		bb_new_forum( array( 'forum_name' => 'Default Forum' ) );

		// Set the site URI.
		bb_update_option( 'uri', BB_URL );
	}

	/**
	 * Fires inside an anonymous function that is run on bbPress shutdown.
	 *
	 * @since 1.1.0
	 */
	register_shutdown_function( create_function( '', 'do_action("bb_shutdown");' ) );
}
add_action( 'bbpress_init', 'bp_forums_load_bbpress' );

/** WP to bbPress wrapper functions ******************************************/

/**
 * Get the current bbPress user.
 *
 * @return object $current_user Current user object from WordPress.
 */
function bb_get_current_user() { global $current_user; return $current_user; }

/**
 * Get userdata for a bbPress user.
 *
 * @param int $user_id User ID.
 * @return object User data from WordPress.
 */
function bb_get_user( $user_id ) { return get_userdata( $user_id ); }

/**
 * Cache users.
 *
 * Noop.
 *
 * @param array $users Array of users.
 */
function bb_cache_users( $users ) {}

/**
 * The bbPress plugin needs this class for its usermeta manipulation.
 */
class BP_Forums_BB_Auth {
	function update_meta( $args = '' ) {
		$defaults = array( 'id' => 0, 'meta_key' => null, 'meta_value' => null, 'meta_table' => 'usermeta', 'meta_field' => 'user_id', 'cache_group' => 'users' );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		return bp_update_user_meta( $id, $meta_key, $meta_value );
	}
}

/**
 * The bbPress plugin needs the DB class to be BPDB, but we want to use WPDB, so we can extend it and use this.
 *
 * The class is pluggable, so that plugins that swap out WPDB with a custom
 * database class (such as HyperDB and ShareDB) can provide their own versions
 * of BPDB which extend the appropriate base class.
 */
if ( ! class_exists( 'BPDB' ) ) :
	class BPDB extends WPDB {
		var $db_servers = array();

		/**
		 * Constructor.
		 *
		 * @see WPDB::__construct() for description of parameters.
		 */
		function __construct( $dbuser, $dbpassword, $dbname, $dbhost ) {
			parent::__construct( $dbuser, $dbpassword, $dbname, $dbhost );

			$args = func_get_args();
			$args = call_user_func_array( array( &$this, 'init' ), $args );

			if ( $args['host'] )
				$this->db_servers['dbh_global'] = $args;
		}

		/**
		 * Determine if a database supports a particular feature.
		 *
		 * Overridden here to work around differences between bbPress's
		 * and WordPress's implementations. In particular, when
		 * BuddyPress tries to run bbPress' SQL installation script,
		 * the collation check always failed. The capability is long
		 * supported by WordPress' minimum required MySQL version, so
		 * this is safe.
		 *
		 * @see WPDB::has_cap() for a description of parameters and
		 *      return values.
		 *
		 * @param string $db_cap See {@link WPDB::has_cap()}.
		 * @param string $_table_name See {@link WPDB::has_cap()}.
		 * @return bool See {@link WPDB::has_cap()}.
		 */
		function has_cap( $db_cap, $_table_name='' ) {
			if ( 'collation' == $db_cap )
				return true;

			return parent::has_cap( $db_cap );
		}

		/**
		 * Initialize the class variables based on provided arguments.
		 *
		 * Based on, and taken from, the BackPress class in turn taken
		 * from the 1.0 branch of bbPress.
		 *
		 * @see BBDB::__construct() for a description of params.
		 *
		 * @param array $args Array of args to parse.
		 * @return array $args.
		 */
		function init( $args ) {
			if ( 4 == func_num_args() ) {
				$args = array(
						'user'     => $args,
						'password' => func_get_arg( 1 ),
						'name'     => func_get_arg( 2 ),
						'host'     => func_get_arg( 3 ),
						'charset'  => defined( 'BBDB_CHARSET' ) ? BBDB_CHARSET : false,
						'collate'  => defined( 'BBDB_COLLATE' ) ? BBDB_COLLATE : false,
					     );
			}

			$defaults = array(
					'user'     => false,
					'password' => false,
					'name'     => false,
					'host'     => 'localhost',
					'charset'  => false,
					'collate'  => false,
					'errors'   => false
					);

			return wp_parse_args( $args, $defaults );
		}

		/**
		 * Stub for escape_deep() compatibility.
		 *
		 * @see WPDB::escape_deep() for description of parameters and
		 *      return values.
		 *
		 * @param mixed $data See {@link WPDB::escape_deep()}.
		 * @return mixed $data See {@link WPDB::escape_deep()}.
		 */
		function escape_deep( $data ) {
			return $this->escape( $data );
		}
	}
endif; // End class_exists( 'BPDB' ).

/**
 * Convert object to given output format.
 *
 * The bbPress plugin needs this to convert vars.
 *
 * @param object $object Object to convert.
 * @param string $output Type of object to return. OBJECT, ARRAY_A, or ARRAY_N.
 */
function backpress_convert_object( &$object, $output ) {
	if ( is_array( $object ) ) {
		foreach ( array_keys( $object ) as $key )
			backpress_convert_object( $object[$key], $output );
	} else {
		switch ( $output ) {
			case OBJECT  : break;
			case ARRAY_A : $object = get_object_vars($object); break;
			case ARRAY_N : $object = array_values(get_object_vars($object)); break;
		}
	}
}

/**
 * Parse and execute queries for updating a set of database tables.
 *
 * Copied from wp-admin/includes/upgrade.php, this will take care of creating
 * the bbPress stand-alone tables without loading a conflicting WP Admin.
 *
 * @see dbDelta() for a description of parameters and return value.
 *
 * @param array $queries See {@link dbDelta()}.
 * @param bool  $execute See {@link dbDelta()}.
 * @return array See {@link dbDelta()}.
 */
function bp_bb_dbDelta($queries, $execute = true) {
	global $wpdb;

	// Separate individual queries into an array.
	if ( !is_array($queries) ) {
		$queries = explode( ';', $queries );
		if ('' == $queries[count($queries) - 1]) array_pop($queries);
	}

	$cqueries = array(); // Creation Queries.
	$iqueries = array(); // Insertion Queries.
	$for_update = array();

	// Create a tablename index for an array ($cqueries) of queries.
	foreach($queries as $qry) {
		if (preg_match("|CREATE TABLE ([^ ]*)|", $qry, $matches)) {
			$cqueries[trim( strtolower($matches[1]), '`' )] = $qry;
			$for_update[$matches[1]] = 'Created table '.$matches[1];
		} else if (preg_match("|CREATE DATABASE ([^ ]*)|", $qry, $matches)) {
			array_unshift($cqueries, $qry);
		} else if (preg_match("|INSERT INTO ([^ ]*)|", $qry, $matches)) {
			$iqueries[] = $qry;
		} else if (preg_match("|UPDATE ([^ ]*)|", $qry, $matches)) {
			$iqueries[] = $qry;
		} else {
			// Unrecognized query type.
		}
	}

	// Check to see which tables and fields exist.
	if ($tables = $wpdb->get_col('SHOW TABLES;')) {
		// For every table in the database.
		foreach ($tables as $table) {
			// Upgrade global tables only for the main site. Don't upgrade at all if DO_NOT_UPGRADE_GLOBAL_TABLES is defined.
			if ( in_array($table, $wpdb->tables('global')) && ( !is_main_site() || defined('DO_NOT_UPGRADE_GLOBAL_TABLES') ) )
				continue;

			// If a table query exists for the database table...
			if ( array_key_exists(strtolower($table), $cqueries) ) {
				// Clear the field and index arrays.
				$cfields = $indices = array();
				// Get all of the field names in the query from between the parents.
				preg_match("|\((.*)\)|ms", $cqueries[strtolower($table)], $match2);
				$qryline = trim($match2[1]);

				// Separate field lines into an array.
				$flds = explode("\n", $qryline);

				//echo "<hr/><pre>\n".print_r(strtolower($table), true).":\n".print_r($cqueries, true)."</pre><hr/>";

				// For every field line specified in the query.
				foreach ($flds as $fld) {
					// Extract the field name.
					preg_match("|^([^ ]*)|", trim($fld), $fvals);
					$fieldname = trim( $fvals[1], '`' );

					// Verify the found field name.
					$validfield = true;
					switch (strtolower($fieldname)) {
					case '':
					case 'primary':
					case 'index':
					case 'fulltext':
					case 'unique':
					case 'key':
						$validfield = false;
						$indices[] = trim(trim($fld), ", \n");
						break;
					}
					$fld = trim($fld);

					// If it's a valid field, add it to the field array.
					if ($validfield) {
						$cfields[strtolower($fieldname)] = trim($fld, ", \n");
					}
				}

				// Fetch the table column structure from the database.
				$tablefields = $wpdb->get_results("DESCRIBE {$table};");

				// For every field in the table.
				foreach ($tablefields as $tablefield) {
					// If the table field exists in the field array...
					if (array_key_exists(strtolower($tablefield->Field), $cfields)) {
						// Get the field type from the query.
						preg_match("|".$tablefield->Field." ([^ ]*( unsigned)?)|i", $cfields[strtolower($tablefield->Field)], $matches);
						$fieldtype = $matches[1];

						// Is actual field type different from the field type in query?
						if ($tablefield->Type != $fieldtype) {
							// Add a query to change the column type.
							$cqueries[] = "ALTER TABLE {$table} CHANGE COLUMN {$tablefield->Field} " . $cfields[strtolower($tablefield->Field)];
							$for_update[$table.'.'.$tablefield->Field] = "Changed type of {$table}.{$tablefield->Field} from {$tablefield->Type} to {$fieldtype}";
						}

						// Get the default value from the array.
							//echo "{$cfields[strtolower($tablefield->Field)]}<br>";
						if (preg_match("| DEFAULT '(.*)'|i", $cfields[strtolower($tablefield->Field)], $matches)) {
							$default_value = $matches[1];
							if ($tablefield->Default != $default_value) {
								// Add a query to change the column's default value.
								$cqueries[] = "ALTER TABLE {$table} ALTER COLUMN {$tablefield->Field} SET DEFAULT '{$default_value}'";
								$for_update[$table.'.'.$tablefield->Field] = "Changed default value of {$table}.{$tablefield->Field} from {$tablefield->Default} to {$default_value}";
							}
						}

						// Remove the field from the array (so it's not added).
						unset($cfields[strtolower($tablefield->Field)]);
					} else {
						// This field exists in the table, but not in the creation queries?
					}
				}

				// For every remaining field specified for the table.
				foreach ($cfields as $fieldname => $fielddef) {
					// Push a query line into $cqueries that adds the field to that table.
					$cqueries[] = "ALTER TABLE {$table} ADD COLUMN $fielddef";
					$for_update[$table.'.'.$fieldname] = 'Added column '.$table.'.'.$fieldname;
				}

				// Index stuff goes here
				// Fetch the table index structure from the database.
				$tableindices = $wpdb->get_results("SHOW INDEX FROM {$table};");

				if ($tableindices) {
					// Clear the index array.
					unset($index_ary);

					// For every index in the table.
					foreach ($tableindices as $tableindex) {
						// Add the index to the index data array.
						$keyname = $tableindex->Key_name;
						$index_ary[$keyname]['columns'][] = array('fieldname' => $tableindex->Column_name, 'subpart' => $tableindex->Sub_part);
						$index_ary[$keyname]['unique'] = ($tableindex->Non_unique == 0)?true:false;
					}

					// For each actual index in the index array.
					foreach ($index_ary as $index_name => $index_data) {
						// Build a create string to compare to the query.
						$index_string = '';
						if ($index_name == 'PRIMARY') {
							$index_string .= 'PRIMARY ';
						} else if($index_data['unique']) {
							$index_string .= 'UNIQUE ';
						}
						$index_string .= 'KEY ';
						if ($index_name != 'PRIMARY') {
							$index_string .= $index_name;
						}
						$index_columns = '';
						// For each column in the index.
						foreach ($index_data['columns'] as $column_data) {
							if ($index_columns != '') $index_columns .= ',';
							// Add the field to the column list string.
							$index_columns .= $column_data['fieldname'];
							if ($column_data['subpart'] != '') {
								$index_columns .= '('.$column_data['subpart'].')';
							}
						}
						// Add the column list to the index create string.
						$index_string .= ' ('.$index_columns.')';
						if (!(($aindex = array_search($index_string, $indices)) === false)) {
							unset($indices[$aindex]);
							//echo "<pre style=\"border:1px solid #ccc;margin-top:5px;\">{$table}:<br />Found index:".$index_string."</pre>\n";
						}
						//else echo "<pre style=\"border:1px solid #ccc;margin-top:5px;\">{$table}:<br /><b>Did not find index:</b>".$index_string."<br />".print_r($indices, true)."</pre>\n";
					}
				}

				// For every remaining index specified for the table.
				foreach ( (array) $indices as $index ) {
					// Push a query line into $cqueries that adds the index to that table.
					$cqueries[] = "ALTER TABLE {$table} ADD $index";
					$for_update[$table.'.'.$fieldname] = 'Added index '.$table.' '.$index;
				}

				// Remove the original table creation query from processing.
				unset($cqueries[strtolower($table)]);
				unset($for_update[strtolower($table)]);
			} else {
				// This table exists in the database, but not in the creation queries?
			}
		}
	}

	$allqueries = array_merge($cqueries, $iqueries);
	if ($execute) {
		foreach ($allqueries as $query) {
			//echo "<pre style=\"border:1px solid #ccc;margin-top:5px;\">".print_r($query, true)."</pre>\n";
			$wpdb->query($query);
		}
	}

	return $for_update;
}
