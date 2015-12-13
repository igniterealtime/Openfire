<?php
/**
 * Parses SQL schema statements for comparison to real table structures
 *
 * @package BackPress
 **/
class BP_SQL_Schema_Parser
{
	/**
	 * Builds a column definition as used in CREATE TABLE statements from
	 * an array such as those returned by DESCRIBE `foo` statements
	 */
	function get_column_definition( $column_data )
	{
		if ( !is_array( $column_data ) ) {
			return $column_data;
		}

		$null = '';
		if ( $column_data['Null'] != 'YES' ) {
			$null = 'NOT NULL';
		}

		$default = '';

		// Defaults aren't allowed at all on certain column types
		if ( !in_array(
			strtolower( $column_data['Type'] ),
			array( 'tinytext', 'text', 'mediumtext', 'longtext', 'blob', 'mediumblob', 'longblob' )
		) ) {
			if ( $column_data['Null'] == 'YES' && $column_data['Default'] === null ) {
				$default = 'default NULL';
			} elseif ( preg_match( '@^\d+$@', $column_data['Default'] ) ) {
				$default = 'default ' . $column_data['Default'];
			} elseif ( is_string( $column_data['Default'] ) || is_float( $column_data['Default'] ) ) {
				$default = 'default \'' . $column_data['Default'] . '\'';
			}
		}

		$column_definition = '`' . $column_data['Field'] . '` ' . $column_data['Type'] . ' ' . $null . ' ' . $column_data['Extra'] . ' ' . $default;
		return preg_replace( '@\s+@', ' ', trim( $column_definition ) );
	}

	/**
	 * Builds an index definition as used in CREATE TABLE statements from
	 * an array similar to those returned by SHOW INDEX FROM `foo` statements
	 */
	function get_index_definition( $index_data )
	{
		if ( !is_array( $index_data ) ) {
			return $index_data;
		}

		if ( !count( $index_data ) ) {
			return $index_data;
		}

		$_name = '`' . $index_data[0]['Key_name'] . '`';

		if ( $index_data[0]['Index_type'] == 'BTREE' && $index_data[0]['Key_name'] == 'PRIMARY' ) {
			$_type = 'PRIMARY KEY';
			$_name = '';
		} elseif ( $index_data[0]['Index_type'] == 'BTREE' && !$index_data[0]['Non_unique'] ) {
			$_type = 'UNIQUE KEY';
		} elseif ( $index_data[0]['Index_type'] == 'FULLTEXT' ) {
			$_type = 'FULLTEXT KEY';
		} else {
			$_type = 'KEY';
		}

		$_columns = array();
		foreach ( $index_data as $_index ) {
			if ( $_index['Sub_part'] ) {
				$_columns[] = '`' . $_index['Column_name'] . '`(' . $_index['Sub_part'] . ')';
			} else {
				$_columns[] = '`' . $_index['Column_name'] . '`';
			}
		}
		$_columns = join( ', ', $_columns );

		$index_definition = $_type . ' ' . $_name . ' (' . $_columns . ')';
		return preg_replace( '@\s+@', ' ', $index_definition );
	}

	/**
	 * Returns a table structure from a raw sql query of the form "CREATE TABLE foo" etc.
	 * The resulting array contains the original query, the columns as would be returned by DESCRIBE `foo`
	 * and the indices as would be returned by SHOW INDEX FROM `foo` on a real table
	 */
	function describe_table( $query )
	{
		// Retrieve the table structure from the query
		if ( !preg_match( '@^CREATE\s+TABLE(\s+IF\s+NOT\s+EXISTS)?\s+`?([^\s|`]+)`?\s+\((.*)\)\s*([^\)|;]*)\s*;?@ims', $query, $_matches ) ) {
			return $query;
		}

		$_if_not_exists = $_matches[1];

		// Tidy up the table name
		$_table_name = trim( $_matches[2] );

		// Tidy up the table columns/indices
		$_columns_indices = trim( $_matches[3], " \t\n\r\0\x0B," );
		// Split by commas not followed by a closing parenthesis ")", using fancy lookaheads
		$_columns_indices = preg_split( '@,(?!(?:[^\(]+\)))@ms', $_columns_indices );
		$_columns_indices = array_map( 'trim', $_columns_indices );

		// Tidy the table attributes
		$_attributes = preg_replace( '@\s+@', ' ', trim( $_matches[4] ) );
		unset( $_matches );

		// Initialise some temporary arrays
		$_columns = array();
		$_indices = array();

		// Loop over the columns/indices
		foreach ( $_columns_indices as $_column_index ) {
			if ( preg_match( '@^(PRIMARY\s+KEY|UNIQUE\s+(?:KEY|INDEX)|FULLTEXT\s+(?:KEY|INDEX)|KEY|INDEX)\s+(?:`?(\w+)`?\s+)*\((.+?)\)$@im', $_column_index, $_matches ) ) {
				// It's an index

				// Tidy the type
				$_index_type = strtoupper( preg_replace( '@\s+@', ' ', trim( $_matches[1] ) ) );
				$_index_type = str_replace( 'INDEX', 'KEY', $_index_type );
				// Set the index name
				$_index_name = ( 'PRIMARY KEY' == $_matches[1] ) ? 'PRIMARY' : $_matches[2];
				// Split into columns
				$_index_columns = array_map( 'trim', explode( ',', $_matches[3] ) );

				foreach ( $_index_columns as $_index_columns_index => $_index_column ) {
					preg_match( '@`?(\w+)`?(?:\s*\(\s*(\d+)\s*\))?@i', $_index_column, $_matches_column );

					$_indices[$_index_name][] = array(
						'Table'        => $_table_name,
						'Non_unique'   => ( 'UNIQUE KEY' == $_index_type || 'PRIMARY' == $_index_name ) ? '0' : '1',
						'Key_name'     => $_index_name,
						'Seq_in_index' => (string) ( $_index_columns_index + 1 ),
						'Column_name'  => $_matches_column[1],
						'Sub_part'     => ( isset( $_matches_column[2] ) && $_matches_column[2] ) ? $_matches_column[2] : null,
						'Index_type'   => ( 'FULLTEXT KEY' == $_index_type ) ? 'FULLTEXT' : 'BTREE'
					);
				}
				unset( $_index_type, $_index_name, $_index_columns, $_index_columns_index, $_index_column, $_matches_column );

			} elseif ( preg_match( "@^`?(\w+)`?\s+(?:(\w+)(?:\s*\(\s*(\d+)\s*\))?(?:\s+(unsigned)){0,1})(?:\s+(NOT\s+NULL))?(?:\s+(auto_increment))?(?:\s+(default)\s+(?:(NULL|'[^']*'|\d+)))?@im", $_column_index, $_matches ) ) {
				// It's a column

				// Tidy the NOT NULL
				$_matches[5] = isset( $_matches[5] ) ? strtoupper( preg_replace( '@\s+@', ' ', trim( $_matches[5] ) ) ) : '';

				$_columns[$_matches[1]] = array(
					'Field'   => $_matches[1],
					'Type'    => ( is_numeric( $_matches[3] ) ) ? $_matches[2] . '(' . $_matches[3] . ')' . ( ( isset( $_matches[4] ) && strtolower( $_matches[4] ) == 'unsigned' ) ? ' unsigned' : '' ) : $_matches[2],
					'Null'    => ( 'NOT NULL' == strtoupper( $_matches[5] ) ) ? 'NO' : 'YES',
					'Default' => ( isset( $_matches[7] ) && 'default' == strtolower( $_matches[7] ) && 'NULL' !== strtoupper( $_matches[8] ) ) ? trim( $_matches[8], "'" ) : null,
					'Extra'   => ( isset( $_matches[6] ) && 'auto_increment' == strtolower( $_matches[6] ) ) ? 'auto_increment' : ''
				);
			}
		}
		unset( $_matches, $_columns_indices, $_column_index );

		// Tidy up the original query
		$_tidy_query = 'CREATE TABLE';
		if ( $_if_not_exists ) {
			$_tidy_query .= ' IF NOT EXISTS';
		}
		$_tidy_query .= ' `' . $_table_name . '` (' . "\n";
		foreach ( $_columns as $_column ) {
			$_tidy_query .= "\t" . BP_SQL_Schema_Parser::get_column_definition( $_column ) . ",\n";
		}
		unset( $_column );
		foreach ( $_indices as $_index ) {
			$_tidy_query .= "\t" . BP_SQL_Schema_Parser::get_index_definition( $_index ) . ",\n";
		}
		$_tidy_query = substr( $_tidy_query, 0, -2 ) . "\n" . ') ' . $_attributes . ';';

		// Add to the query array using the table name as the index
		$description = array(
			'query_original' => $query,
			'query_tidy' => $_tidy_query,
			'columns' => $_columns,
			'indices' => $_indices
		);
		unset( $_table_name, $_columns, $_indices, $_tidy_query );

		return $description;
	}

	/**
	 * Helper function to flatten arrays
	 */
	function _flatten_array( $array, $cut_branch = 0, $keep_child_array_keys = true )
	{
		if ( !is_array( $array ) ) {
			return $array;
		}

		if ( empty( $array ) ) {
			return null;
		}

		$temp = array();
		foreach ( $array as $k => $v ) {
			if ( $cut_branch && $k == $cut_branch )
				continue;
			if ( is_array( $v ) ) {
				if ( $keep_child_array_keys ) {
					$temp[$k] = true;
				}
				$temp += BP_SQL_Schema_Parser::_flatten_array( $v, $cut_branch, $keep_child_array_keys );
			} else {
				$temp[$k] = $v;
			}
		}
		return $temp;
	}

	/**
	 * Splits grouped SQL statements into queries within a highly structured array
	 * Only supports CREATE TABLE, INSERT and UPDATE
	 */
	function parse( $sql )
	{
		// Only accept strings or arrays
		if ( is_string( $sql ) ) {
			// Just pop strings into an array to start with
			$queries = array( $sql );
		} elseif ( is_array( $sql ) ) {
			// Flatten the array
			$queries = BP_SQL_Schema_Parser::_flatten_array( $sql, 0, false );
			// Remove empty nodes
			$queries = array_filter( $queries );
		} else {
			return false;
		}

		// Clean up the queries
		$_clean_queries = array();
		foreach ( $queries as $_query ) {
			// Trim space and semi-colons
			$_query = trim( $_query, "; \t\n\r\0\x0B" );
			// If it exists and isn't a number
			if ( $_query && !is_numeric( $_query ) ) {
				// Is it more than one query?
				if ( strpos( ';', $_query ) !== false ) {
					// Explode by semi-colon
					foreach ( explode( ';', $_query ) as $_part ) {
						$_part = trim( $_part );
						if ( $_part && !is_numeric( $_part ) ) {
							// Pull out any commented code
							// Can't properly deal with /*!4321 FOO `bar` */ version specific inclusion, just includes it regardless of version
							$_part = preg_replace( '@/\*![0-9]*([^\*]*)\*/@', '$1', $_part );
							$_part = preg_replace( '@/\*[^\*]*\*/@', '', $_part );
							$_part = preg_replace( '@[\-\-|#].*$@m', '', $_part );
							$_clean_queries[] = trim( $_part ) . ';';
						}
					}
					unset( $_part );
				} else {
					$_clean_queries[] = $_query . ';';
				}
			}
		}
		unset( $_query );
		if ( !count( $_clean_queries ) ) {
			return false;
		}
		$queries = $_clean_queries;
		unset( $_clean_queries );

		$_queries = array();
		foreach ( $queries as $_query ) {
			// Only process table creation, inserts and updates, capture the table/database name while we are at it
			if ( !preg_match( '@^(CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?|INSERT\s+INTO|UPDATE)\s+`?([^\s|`]+)`?@im', $_query, $_matches ) ) {
				continue;
			}

			// Tidy up the type so we can switch it
			$_type = strtoupper( preg_replace( '@\s+@', ' ', trim( $_matches[1] ) ) );
			$_table_name = trim( $_matches[2] );
			unset( $_matches );

			switch ( $_type ) {
				case 'CREATE TABLE':
				case 'CREATE TABLE IF NOT EXISTS':
					$_description = BP_SQL_Schema_Parser::describe_table( $_query );
					if ( is_array( $_description ) ) {
						$_queries['tables'][$_table_name] = $_description;
					}
					break;

				case 'INSERT INTO':
					// Just add the query as is for now
					$_queries['insert'][$_table_name][] = $_query;
					break;

				case 'UPDATE':
					// Just add the query as is for now
					$_queries['update'][$_table_name][] = $_query;
					break;
			}
			unset( $_type, $_table_name );
		}
		unset( $_query );

		if ( !count( $_queries ) ) {
			return false;
		}
		return $_queries;
	}

	/**
	 * Evaluates the difference between a given set of SQL queries and real database structure
	 */
	function delta( $db_object, $queries, $ignore = false, $execute = true )
	{
		if ( !$db_object || !is_object( $db_object ) || !( is_a( $db_object, 'BPDB' ) || is_a( $db_object, 'BPDB_Multi' ) || is_a( $db_object, 'BPDB_Hyper' ) ) ) {
			return __( 'Passed variable is not a BackPress database object.' );
		}

		if ( !$_queries = BP_SQL_Schema_Parser::parse( $queries ) ) {
			return __( 'No schema available.' );
		}

		// Set up default elements to ignore
		$ignore_defaults = array(
			'tables'  => array(), // Just a list of tablenames, including prefix. Does not affect INSERT and UPDATE queries.
			'columns' => array(), // Arrays of column names, keyed with the table names, including prefix.
			'indices' => array()  // Arrays of index names, keyed with the table names, including prefix.
		);

		// Add the elements to ignore that were passed to the function
		if ( !$ignore || !is_array( $ignore ) ) {
			$ignore = $ignore_defaults;
		} else {
			if ( isset( $ignore['tables'] ) && is_array( $ignore['tables'] ) ) {
				$ignore['tables'] = array_merge( $ignore_defaults['tables'], $ignore['tables'] );
			}
			if ( isset( $ignore['columns'] ) && is_array( $ignore['columns'] ) ) {
				$ignore['columns'] = array_merge( $ignore_defaults['columns'], $ignore['columns'] );
			}
			if ( isset( $ignore['indices'] ) && is_array( $ignore['indices'] ) ) {
				$ignore['indices'] = array_merge( $ignore_defaults['indices'], $ignore['indices'] );
			}
		}

		// Build an array of $db_object registered tables and their database identifiers
		$_tables = $db_object->tables;
		$db_object_tables = array();
		foreach ( $_tables as $_table_id => $_table_name ) {
			if ( is_array( $_table_name ) && isset( $db_object->db_servers['dbh_' . $_table_name[0]] ) ) {
				$db_object_tables[$db_object->$_table_id] = 'dbh_' . $_table_name[0];
			} else {
				$db_object_tables[$db_object->$_table_id] = 'dbh_global';
			}
		}
		unset( $_tables, $_table_id, $_table_name );

		$alterations = array();

		// Loop through table queries
		if ( isset( $_queries['tables'] ) ) {
			foreach ( $_queries['tables'] as $_new_table_name => $_new_table_data ) {
				if ( in_array( $_new_table_name, $ignore['tables'] ) ) {
					continue;
				}

				// See if the table is custom and registered in $db_object under a custom database
				if (
					isset( $db_object_tables[$_new_table_name] ) &&
					$db_object_tables[$_new_table_name] != 'dbh_global' &&
					isset( $db_object->db_servers[$db_object_tables[$_new_table_name]]['ds'] )
				) {
					// Force the database connection
					$_dbhname = $db_object->db_servers[$db_object_tables[$_new_table_name]]['ds'];
					$db_object->_force_dbhname = $_dbhname;
				} else {
					$_dbhname = 'dbh_global';
				}

				// Fetch the existing table column structure from the database
				$db_object->suppress_errors();
				if ( !$_existing_table_columns = $db_object->get_results( 'DESCRIBE `' . $_new_table_name . '`;', ARRAY_A ) ) {
					$db_object->suppress_errors( false );
					// The table doesn't exist, add it and then continue to the next table
					$alterations[$_dbhname][$_new_table_name][] = array(
						'action' => 'create_table',
						'message' => __( 'Creating table' ),
						'query' => $_new_table_data['query_tidy']
					);
					continue;
				}
				$db_object->suppress_errors( false );

				// Add an index to the existing columns array
				$__existing_table_columns = array();
				foreach ( $_existing_table_columns as $_existing_table_column ) {
					// Remove 'Key' from returned column structure
					unset( $_existing_table_column['Key'] );
					$__existing_table_columns[$_existing_table_column['Field']] = $_existing_table_column;
				}
				$_existing_table_columns = $__existing_table_columns;
				unset( $__existing_table_columns );

				// Loop over the columns in this table and look for differences
				foreach ( $_new_table_data['columns'] as $_new_column_name => $_new_column_data ) {
					if ( isset( $ignore['columns'][$_new_table_name] ) && in_array( $_new_column_name, $ignore['columns'][$_new_table_name] ) ) {
						continue;
					}

					if ( !in_array( $_new_column_data, $_existing_table_columns ) ) {
						// There is a difference
						if ( !isset( $_existing_table_columns[$_new_column_name] ) ) {
							// The column doesn't exist, so add it
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'add_column',
								'message' => sprintf( __( 'Adding column: %s' ), $_new_column_name ),
								'column' => $_new_column_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` ADD COLUMN ' . BP_SQL_Schema_Parser::get_column_definition( $_new_column_data ) . ';'
							);
							continue;
						}

						// Adjust defaults on columns that allow defaults
						if (
							$_new_column_data['Default'] !== $_existing_table_columns[$_new_column_name]['Default'] &&
							!in_array(
								strtolower( $_new_column_data['Type'] ),
								array( 'tinytext', 'text', 'mediumtext', 'longtext', 'blob', 'mediumblob', 'longblob' )
							)
						) {
							// Change the default value for the column
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'set_default',
								'message' => sprintf( __( 'Setting default on column: %s' ), $_new_column_name ),
								'column' => $_new_column_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` ALTER COLUMN `' . $_new_column_name . '` SET DEFAULT \'' . $_new_column_data['Default'] . '\';'
							);
							// Don't continue, overwrite this if the next conditional is met
						}

						if (
							$_new_column_data['Type'] !== $_existing_table_columns[$_new_column_name]['Type'] ||
							( 'YES' === $_new_column_data['Null'] xor 'YES' === $_existing_table_columns[$_new_column_name]['Null'] ) ||
							$_new_column_data['Extra'] !== $_existing_table_columns[$_new_column_name]['Extra']
						) {
							// Change the structure for the column
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'change_column',
								'message' => sprintf( __( 'Changing column: %s' ), $_new_column_name ),
								'column' => $_new_column_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` CHANGE COLUMN `' . $_new_column_name . '` ' . BP_SQL_Schema_Parser::get_column_definition( $_new_column_data ) . ';'
							);
						}
					}
				}
				unset( $_existing_table_columns, $_new_column_name, $_new_column_data );

				// Fetch the table index structure from the database
				if ( !$_existing_table_indices = $db_object->get_results( 'SHOW INDEX FROM `' . $_new_table_name . '`;', ARRAY_A ) ) {
					continue;
				}

				// Add an index to the existing columns array and organise by index name
				$__existing_table_indices = array();
				foreach ( $_existing_table_indices as $_existing_table_index ) {
					// Remove unused parts from returned index structure
					unset(
						$_existing_table_index['Collation'],
						$_existing_table_index['Cardinality'],
						$_existing_table_index['Packed'],
						$_existing_table_index['Null'],
						$_existing_table_index['Comment']
					);
					$__existing_table_indices[$_existing_table_index['Key_name']][] = $_existing_table_index;
				}
				$_existing_table_indices = $__existing_table_indices;
				unset( $__existing_table_indices );

				// Loop over the indices in this table and look for differences
				foreach ( $_new_table_data['indices'] as $_new_index_name => $_new_index_data ) {
					if ( isset( $ignore['indices'][$_new_table_name] ) && in_array( $_new_index_name, $ignore['indices'][$_new_table_name] ) ) {
						continue;
					}

					if ( !in_array( $_new_index_data, $_existing_table_indices ) ) {
						// There is a difference
						if ( !isset( $_existing_table_indices[$_new_index_name] ) ) {
							// The index doesn't exist, so add it
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'add_index',
								'message' => sprintf( __( 'Adding index: %s' ), $_new_index_name ),
								'index' => $_new_index_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` ADD ' . BP_SQL_Schema_Parser::get_index_definition( $_new_index_data ) . ';'
							);
							continue;
						}

						if ( $_new_index_data !== $_existing_table_indices[$_new_index_name] ) {
							// The index is incorrect, so drop it and add the new one
							if ( $_new_index_name == 'PRIMARY' ) {
								$_drop_index_name = 'PRIMARY KEY';
							} else {
								$_drop_index_name = 'INDEX `' . $_new_index_name . '`';
							}
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'drop_index',
								'message' => sprintf( __( 'Dropping index: %s' ), $_new_index_name ),
								'index' => $_new_index_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` DROP ' . $_drop_index_name . ';'
							);
							unset( $_drop_index_name );
							$alterations[$_dbhname][$_new_table_name][] = array(
								'action' => 'add_index',
								'message' => sprintf( __( 'Adding index: %s' ), $_new_index_name ),
								'index' => $_new_index_name,
								'query' => 'ALTER TABLE `' . $_new_table_name . '` ADD ' . BP_SQL_Schema_Parser::get_index_definition( $_new_index_data ) . ';'
							);
						}
					}
				}
				unset( $_new_index_name, $_new_index_data );

				// Go back to the default database connection
				$db_object->_force_dbhname = false;
			}
			unset( $_new_table_name, $_new_table_data, $_dbhname );
		}

		// Now deal with the sundry INSERT and UPDATE statements (if any)
		if ( isset( $_queries['insert'] ) && is_array( $_queries['insert'] ) && count( $_queries['insert'] ) ) {
			foreach ( $_queries['insert'] as $_table_name => $_inserts ) {
				foreach ( $_inserts as $_insert ) {
					$alterations['dbh_global'][$_table_name][] = array(
						'action' => 'insert',
						'message' => __( 'Inserting data' ),
						'query' => $_insert
					);
				}
				unset( $_insert );
			}
			unset( $_table_name, $_inserts );
		}
		if ( isset( $_queries['update'] ) && is_array( $_queries['update'] ) && count( $_queries['update'] ) ) {
			foreach ( $_queries['update'] as $_table_name => $_updates ) {
				foreach ( $_updates as $_update ) {
					$alterations['dbh_global'][$_table_name][] = array(
						'action' => 'update',
						'message' => __( 'Updating data' ),
						'query' => $_update
					);
				}
				unset( $_update );
			}
			unset( $_table_name, $_updates );
		}

		// Initialise an array to hold the output messages
		$messages = array();
		$errors = array();

		foreach ( $alterations as $_dbhname => $_tables ) {
			// Force the database connection (this was already checked to be valid in the previous loop)
			$db_object->_force_dbhname = $_dbhname;

			// Note the database in the return messages
			$messages[] = '>>> ' . sprintf( __( 'Modifying database: %s (%s)' ), $db_object->db_servers[$_dbhname]['name'], $db_object->db_servers[$_dbhname]['host'] );

			foreach ( $_tables as $_table_name => $_alterations ) {
				// Note the table in the return messages
				$messages[] = '>>>>>> ' . sprintf( __( 'Table: %s' ), $_table_name );

				foreach ( $_alterations as $_alteration ) {
					// If there is no query, then skip
					if ( !$_alteration['query'] ) {
						continue;
					}

					// Note the action in the return messages
					$messages[] = '>>>>>>>>> ' . $_alteration['message'];

					if ( !$execute ) {
						$messages[] = '>>>>>>>>>>>> ' . __( 'Skipped' );
						continue;
					}

					// Run the query
					$_result = $db_object->query( $_alteration['query'] );
					$_result_error = $db_object->get_error();

					if ( $_result_error ) {
						// There was an error
						$_result =& $_result_error;
						unset( $_result_error );
						$messages[] = '>>>>>>>>>>>> ' . __( 'SQL ERROR! See the error log for more detail' );
						$errors[] = __( 'SQL ERROR!' );
						$errors[] = '>>> ' . sprintf( __( 'Database: %s (%s)' ), $db_object->db_servers[$_dbhname]['name'], $db_object->db_servers[$_dbhname]['host'] );
						$errors[] = '>>>>>> ' . $_result->error_data['db_query']['query'];
						$errors[] = '>>>>>> ' . $_result->error_data['db_query']['error'];
					} else {
						$messages[] = '>>>>>>>>>>>> ' . __( 'Done' );
					}
					unset( $_result );
				}
				unset( $_alteration );
			}
			unset( $_table_name, $_alterations );
		}
		unset( $_dbhname, $_tables );

		// Reset the database connection
		$db_object->_force_dbhname = false;

		return array( 'messages' => $messages, 'errors' => $errors );
	}
} // END class BP_SQL_Schema_Parser
