<?php

class WP_Users {
	var $db;

	function WP_Users( &$db ) {
		$this->__construct( $db );
//		register_shutdown_function( array(&$this, '__destruct') );
	}

	function __construct( &$db ) {
		$this->db =& $db;
	}

//	function __destruct() {
//	}

	function _put_user( $args = null ) {
		$defaults = array(
			'ID' => false,
			'user_login' => '',
			'user_nicename' => '',
			'user_email' => '',
			'user_url' => '',
			'user_pass' => false,
			'user_registered' => time(),
			'display_name' => '',
			'user_status' => 0,
			'strict_user_login' => true
		);

		$fields = array_keys( wp_parse_args( $args ) );
		$args = wp_parse_args( $args, $defaults );
		unset($defaults['strict_user_login']);

		if ( isset($args['ID']) && $args['ID'] ) {
			unset($defaults['ID']);
			$fields = array_intersect( $fields, array_keys( $defaults ) );
		} else {
			$fields = array_keys( $defaults );
		}

		extract( $args, EXTR_SKIP );

		$ID = (int) $ID;

		if ( !$ID || in_array( 'user_login', $fields ) ) {
			$user_login = $this->sanitize_user( $user_login, $strict_user_login );

			if ( !$user_login )
				return new WP_Error( 'user_login', __('Invalid login name') );
			if ( !$ID && $this->get_user( $user_login, array( 'by' => 'login' ) ) )
				return new WP_Error( 'user_login', __('Name already exists') );
		}

		if ( !$ID || in_array( 'user_nicename', $fields ) ) {
			if ( !$user_nicename = $this->sanitize_nicename( $user_nicename ? $user_nicename : $user_login ) )
				return new WP_Error( 'user_nicename', __('Invalid nicename') );
			if ( !$ID && $this->get_user( $user_nicename, array( 'by' => 'nicename' ) ) )
				return new WP_Error( 'user_nicename', __('Nicename already exists') );
		}

		if ( !$ID || in_array( 'user_email', $fields ) ) {
			if ( !$this->is_email( $user_email ) )
				return new WP_Error( 'user_email', __('Invalid email address') );

			if ( $already_email = $this->get_user( $user_email, array( 'by' => 'email' ) ) ) {
				// if new user, or if multiple users with that email, or if only one user with that email, but it's not the user being updated
				if ( !$ID || is_wp_error( $already_email ) || $already_email->ID != $ID )
					return new WP_Error( 'user_email', __('Email already exists') );
			}
		}

		if ( !$ID || in_array( 'user_url', $fields ) ) {
			$user_url = esc_url( $user_url );
		}

		if ( !$ID || in_array( 'user_pass', $fields ) ) {
			if ( !$user_pass )
				$user_pass = WP_Pass::generate_password();
			$plain_pass = $user_pass;
			$user_pass  = WP_Pass::hash_password( $user_pass );
		}

		if ( !$ID || in_array( 'user_registered', $fields ) ) {
			if ( !is_numeric($user_registered) )
				$user_registered = backpress_gmt_strtotime( $user_registered );

			if ( !$user_registered || $user_registered < 0 )
				return new WP_Error( 'user_registered', __('Invalid registration time') );

			if ( !$user_registered = @gmdate('Y-m-d H:i:s', $user_registered) )
				return new WP_Error( 'user_registered', __('Invalid registration timestamp') );
		}

		if ( !$ID || in_array( 'user_display', $fields ) ) {
			if ( !$display_name )
				$display_name = $user_login;
		}

		$db_return = NULL;
		if ( $ID ) {
			$db_return = $this->db->update( $this->db->users, compact( $fields ), compact('ID') );
		} else {
			$db_return = $this->db->insert( $this->db->users, compact( $fields ) );
			$ID = $this->db->insert_id;
		}

		if ( !$db_return )
			return new WP_Error( 'WP_Users::_put_user', __('Query failed') );

		// Cache the result
		if ( $ID ) {
			wp_cache_delete( $ID, 'users' );
			$this->get_user( $ID, array( 'from_cache' => false ) );
		}

		$args = compact( array_keys($args) );
		$args['plain_pass'] = $plain_pass;

		do_action( __CLASS__ . '::' . __FUNCTION__, $args );

		return $args;
	}

	function new_user( $args = null ) {
		$args = wp_parse_args( $args );
		$args['ID'] = false;

		$r = $this->_put_user( $args );

		if ( is_wp_error($r) )
			return $r;

		do_action( __CLASS__ . '::' . __FUNCTION__, $r, $args );

		return $r;
	}

	function update_user( $ID, $args = null ) {
		$args = wp_parse_args( $args );

		$args['output'] = OBJECT;
		$user = $this->get_user( $ID, $args );
		if ( !$user || is_wp_error( $user ) )
			return $user;

		$args['ID'] = $user->ID;

		$r = $this->_put_user( $args );

		if ( is_wp_error($r) )
			return $r;

		do_action( __CLASS__ . '::' . __FUNCTION__, $r, $args );

		return $r;
	}

	/**
	 * set_password() - Updates the user's password with a new encrypted one
	 *
	 * For integration with other applications, this function can be
	 * overwritten to instead use the other package password checking
	 * algorithm.
	 *
	 * @since 2.5
	 * @uses WP_Pass::hash_password() Used to encrypt the user's password before passing to the database
	 *
	 * @param string $password The plaintext new user password
	 * @param int $user_id User ID
	 */
	function set_password( $password, $user_id ) {
		$user = $this->get_user( $user_id );
		if ( !$user || is_wp_error( $user ) )
			return $user;

		$user_id = $user->ID;
		$hash = WP_Pass::hash_password($password);
		$this->update_user( $user->ID, array( 'user_pass' => $password ) );
	}

	// $user_id can be user ID#, user_login, user_email (by specifying by = email)
	function get_user( $user_id = 0, $args = null ) {
		$defaults = array( 'output' => OBJECT, 'by' => false, 'from_cache' => true, 'append_meta' => true );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		if ( !$user_id ) {
			return false;
		}

		// Let's just deal with arrays
		$user_ids = (array) $user_id;

		if ( !count( $user_ids ) ) {
			return false;
		}

		// Validate passed ids
		$safe_user_ids = array();
		foreach ( $user_ids as $_user_id ) {
			switch ( $by ) {
				case 'login':
					$safe_user_ids[] = $this->sanitize_user( $_user_id, true );
					break;
				case 'email':
					if ( $this->is_email( $_user_id ) ) {
						$safe_user_ids[] = $_user_id;
					}
					break;
				case 'nicename':
					$safe_user_ids[] = $this->sanitize_nicename( $_user_id );
					break;
				default:
					if ( is_numeric( $_user_id ) ) {
						$safe_user_ids[] = (int) $_user_id;
					} else { // If one $_user_id is non-numerical, treat all $user_ids as user_logins
						$safe_user_ids[] = $this->sanitize_user( $_user_id, true );
						$by = 'login';
					}
					break;
			}
		}

		// No soup for you!
		if ( !count( $safe_user_ids ) ) {
			return false;
		}

		// Name the cache storing non-existant ids and the SQL field to query by
		switch ( $by ) {
			case 'login':
				$non_existant_cache = 'userlogins';
				$sql_field = 'user_login';
				break;
			case 'email':
				$non_existant_cache = 'useremail';
				$sql_field = 'user_email';
				break;
			case 'nicename':
				$non_existant_cache = 'usernicename';
				$sql_field = 'user_nicename';
				break;
			default:
				$non_existant_cache = 'users';
				$sql_field = 'ID';
				break;
		}
		
		// Check if the numeric user IDs exist from caches
		$cached_users = array();
		if ( $from_cache ) {
			$existant_user_ids = array();
			$maybe_existant_user_ids = array();
			
			switch ( $by ) {
				case 'login':
				case 'email':
				case 'nicename':
					foreach ( $safe_user_ids as $_safe_user_id ) {
						$ID = wp_cache_get( $_safe_user_id, $non_existant_cache );
						if ( false === $ID ) {
							$maybe_existant_user_ids[] = $_safe_user_id;
						} elseif ( 0 !== $ID ) {
							$existant_user_ids[] = $ID;
						}
					}
					if ( count( $existant_user_ids ) ) {
						// We need to run again using numeric ids
						$args['by'] = false;
						$cached_users = $this->get_user( $existant_user_ids, $args );
					}
					break;
				default:
					foreach ( $safe_user_ids as $_safe_user_id ) {
						$user = wp_cache_get( $_safe_user_id, 'users' );
						if ( false === $user ) {
							$maybe_existant_user_ids[] = $_safe_user_id;
						} elseif ( 0 !== $user ) {
							$cached_users[] = $user;
						}
					}
					break;
			}

			// No maybes? Then it's definite.
			if ( !count( $maybe_existant_user_ids ) ) {
				if ( !count( $cached_users ) ) {
					// Nothing there sorry
					return false;
				}

				// Deal with the case where one record was requested but multiple records are returned
				if ( !is_array( $user_id ) && $user_id ) {
					if ( 1 < count( $cached_users ) ) {
						if ( 'user_email' == $sql_field ) {
							$err = __( 'Multiple email matches.  Log in with your username.' );
						} else {
							$err = sprintf( __( 'Multiple %s matches' ), $sql_field );
						}
						return new WP_Error( $sql_field, $err, $args + array( 'user_id' => $user_id, 'unique' => false ) );
					}

					// If one item was requested, it expects a single user object back
					$cached_users = array_shift( $cached_users );
				}

				backpress_convert_object( $cached_users, $output );
				return $cached_users;
			}

			// If we get this far, there are some maybes so try and grab them
		} else {
			$maybe_existant_user_ids = $safe_user_ids;
		}

		// Escape the ids for the SQL query
		$maybe_existant_user_ids = $this->db->escape_deep( $maybe_existant_user_ids );

		// Sort the ids so the MySQL will more consistently cache the query
		sort( $maybe_existant_user_ids );

		// Get the users from the database
		$sql = "SELECT * FROM `{$this->db->users}` WHERE `$sql_field` in ('" . join( "','", $maybe_existant_user_ids ) . "');";
		$db_users = $this->db->get_results( $sql );

		// Merge in the cached users if available
		if ( count( $cached_users ) ) {
			// Create a convenient array of database fetched user ids
			$db_user_ids = array();
			foreach ( $db_users as $_db_user ) {
				$db_user_ids[] = $_db_user->ID;
			}
			$users = array_merge( $cached_users, $db_users );
		} else {
			$users = $db_users;
		}

		// Deal with the case where one record was requested but multiple records are returned
		if ( !is_array( $user_id ) && $user_id ) {
			if ( 1 < count( $users ) ) {
				if ( 'user_email' == $sql_field ) {
					$err = __( 'Multiple email matches.  Log in with your username.' );
				} else {
					$err = sprintf( __( 'Multiple %s matches' ), $sql_field );
				}
				return new WP_Error( $sql_field, $err, $args + array( 'user_id' => $user_id, 'unique' => false ) );
			}
		}

		// Create a convenient array of final user ids
		$final_user_ids = array();
		foreach ( $users as $_user ) {
			$final_user_ids[] = $_user->$sql_field;
		}

		foreach ( $safe_user_ids as $_safe_user_id ) {
			if ( !in_array( $_safe_user_id, $final_user_ids ) ) {
				wp_cache_add( $_safe_user_id, 0, $non_existant_cache );
			}
		}

		if ( !count( $users ) ) {
			return false;
		}

		// Add display names
		$final_users = array();
		foreach ( $users as $_user ) {
			// Make sure there is a display_name set
			if ( !$_user->display_name ) {
				$_user->display_name = $_user->user_login;
			}

			$final_users[] = $_user;
		}

		// append_meta() does the user object, useremail, userlogins caching
		if ( $append_meta ) {
			if ( count( $cached_users ) ) {
				$db_final_users =array();
				$cached_final_users = array();
				foreach ( $final_users as $final_user ) {
					if ( in_array( $final_user->ID, $db_user_ids ) ) {
						$db_final_users[] = $final_user;
					} else {
						$cached_final_users[] = $final_user;
					}
				}
				$db_final_users = $this->append_meta( $db_final_users );
				$final_users = array_merge( $cached_final_users, $db_final_users );
			} else {
				$final_users = $this->append_meta( $final_users );
			}
		}

		// If one item was requested, it expects a single user object back
		if ( !is_array( $user_id ) && $user_id ) {
			$final_users = array_shift( $final_users );
		}

		backpress_convert_object( $final_users, $output );
		return $final_users;
	}

	function delete_user( $user_id ) {
		$user = $this->get_user( $user_id );

		if ( !$user || is_wp_error( $user ) )
			return $user;

		do_action( 'pre_' . __CLASS__ . '::' . __FUNCTION__, $user->ID );

		$r = $this->db->query( $this->db->prepare( "DELETE FROM {$this->db->users} WHERE ID = %d", $user->ID ) );
		$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->usermeta} WHERE user_id = %d", $user->ID ) );

		wp_cache_delete( $user->ID, 'users' );
		wp_cache_delete( $user->user_nicename, 'usernicename' );
		wp_cache_delete( $user->user_email, 'useremail' );
		wp_cache_delete( $user->user_login, 'userlogins' );

		do_action( __CLASS__ . '::' . __FUNCTION__, $user->ID );

		return $r;
	}

	// Used for user meta, but can be used for other meta data (such as bbPress' topic meta)
	// Should this be in the class or should it be it's own special function?
	function append_meta( $object, $args = null ) {
		$defaults = array( 'meta_table' => 'usermeta', 'meta_field' => 'user_id', 'id_field' => 'ID', 'cache_group' => 'users' );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		if ( is_array($object) ) {
			$trans = array();
			foreach ( array_keys($object) as $i )
				$trans[$object[$i]->$id_field] =& $object[$i];
			$ids = join(',', array_keys($trans));
			if ( $ids && $metas = $this->db->get_results("SELECT $meta_field, meta_key, meta_value FROM {$this->db->$meta_table} WHERE $meta_field IN ($ids) /* WP_Users::append_meta */") ) {
				usort( $metas, array(&$this, '_append_meta_sort') );
				foreach ( $metas as $meta ) {
					if ( empty( $meta->meta_key ) )
						continue;
					$trans[$meta->$meta_field]->{$meta->meta_key} = maybe_unserialize( $meta->meta_value );
					if ( strpos($meta->meta_key, $this->db->prefix) === 0 )
						$trans[$meta->$meta_field]->{substr($meta->meta_key, strlen($this->db->prefix))} = maybe_unserialize( $meta->meta_value );
				}
			}
			foreach ( array_keys($trans) as $i ) {
				wp_cache_set( $i, $trans[$i], $cache_group );
				if ( 'users' == $cache_group ) {
					wp_cache_set( $trans[$i]->user_login, $i, 'userlogins' );
					wp_cache_set( $trans[$i]->user_email, $i, 'useremail' );
					wp_cache_set( $trans[$i]->user_nicename, $i, 'usernicename' );
				}
			}
			return $object;
		} elseif ( $object ) {
			if ( $metas = $this->db->get_results("SELECT meta_key, meta_value FROM {$this->db->$meta_table} WHERE $meta_field = '{$object->$id_field}' /* WP_Users::append_meta */") ) {
				usort( $metas, array(&$this, '_append_meta_sort') );
				foreach ( $metas as $meta ) {
					if ( empty( $meta->meta_key ) )
						continue;
					$object->{$meta->meta_key} = maybe_unserialize( $meta->meta_value );
					if ( strpos($meta->meta_key, $this->db->prefix) === 0 )
						$object->{substr($meta->meta_key, strlen($this->db->prefix))} = maybe_unserialize( $meta->meta_value );
				}
			}
			wp_cache_set( $object->$id_field, $object, $cache_group );
			if ( 'users' == $cache_group ) {
				wp_cache_set($object->user_login, $object->ID, 'userlogins');
				wp_cache_set($object->user_email, $object->ID, 'useremail');
				wp_cache_set($object->user_nicename, $object->ID, 'usernicename');
			}
			return $object;
		}
	}
	
	/** 
	 * _append_meta_sort() - sorts meta keys by length to ensure $appended_object->{$bbdb->prefix}key overwrites $appended_object->key as desired
	 *
	 * @internal
	 */
	function _append_meta_sort( $a, $b ) {
		return strlen( $a->meta_key ) - strlen( $b->meta_key );
	}

	function update_meta( $args = null ) {
		$defaults = array( 'id' => 0, 'meta_key' => null, 'meta_value' => null, 'meta_table' => 'usermeta', 'meta_field' => 'user_id', 'cache_group' => 'users' );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		$user = $this->get_user( $id );
		if ( !$user || is_wp_error($user) )
			return $user;

		$id = (int) $user->ID;

		if ( is_null($meta_key) || is_null($meta_value) )
			return false;

		$meta_key = preg_replace('|[^a-z0-9_]|i', '', $meta_key);
		if ( 'usermeta' == $meta_table && 'capabilities' == $meta_key )
			$meta_key = $this->db->prefix . 'capabilities';

		$meta_tuple = compact('id', 'meta_key', 'meta_value', 'meta_table');
		$meta_tuple = apply_filters( __CLASS__ . '::' . __FUNCTION__, $meta_tuple );
		extract($meta_tuple, EXTR_OVERWRITE);

		$_meta_value = maybe_serialize( $meta_value );

		$cur = $this->db->get_row( $this->db->prepare( "SELECT * FROM {$this->db->$meta_table} WHERE $meta_field = %d AND meta_key = %s", $id, $meta_key ) );

		if ( !$cur ) {
			$this->db->insert( $this->db->$meta_table, array( $meta_field => $id, 'meta_key' => $meta_key, 'meta_value' => $_meta_value ) );
		} elseif ( $cur->meta_value != $meta_value ) {
			$this->db->update( $this->db->$meta_table, array( 'meta_value' => $_meta_value ), array( $meta_field => $id, 'meta_key' => $meta_key ) );
		}


		wp_cache_delete( $id, $cache_group );

		return true;
	}

	function delete_meta( $args = null ) {
		$defaults = array( 'id' => 0, 'meta_key' => null, 'meta_value' => null, 'meta_table' => 'usermeta', 'meta_field' => 'user_id', 'meta_id_field' => 'umeta_id', 'cache_group' => 'users' );
		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		$user = $this->get_user( $id );
		if ( !$user || is_wp_error($user) )
			return $user;

		$id = (int) $id;

		if ( is_null($meta_key) )
			return false;

		$meta_key = preg_replace('|[^a-z0-9_]|i', '', $meta_key);

		$meta_tuple = compact('id', 'meta_key', 'meta_value', 'meta_table');
		$meta_tuple = apply_filters( __CLASS__ . '::' . __FUNCTION__, $meta_tuple );
		extract($meta_tuple, EXTR_OVERWRITE);

		$_meta_value = is_null($meta_value) ? null : maybe_serialize( $meta_value );

		if ( is_null($_meta_value) )
			$meta_id = $this->db->get_var( $this->db->prepare( "SELECT $meta_id_field FROM {$this->db->$meta_table} WHERE $meta_field = %d AND meta_key = %s", $id, $meta_key ) );
		else
			$meta_id = $this->db->get_var( $this->db->prepare( "SELECT $meta_id_field FROM {$this->db->$meta_table} WHERE $meta_field = %d AND meta_key = %s AND meta_value = %s", $id, $meta_key, $_meta_value ) );

		if ( !$meta_id )
			return false;

		if ( is_null($_meta_value) )
			$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->$meta_table} WHERE $meta_field = %d AND meta_key = %s", $id, $meta_key ) );
		else
			$this->db->query( $this->db->prepare( "DELETE FROM {$this->db->$meta_table} WHERE $meta_id_field = %d", $meta_id ) );

		wp_cache_delete( $id, $cache_group );

		return true;
	}

	function sanitize_user( $user_login, $strict = false ) {
		return sanitize_user( $user_login, $strict );
	}

	function sanitize_nicename( $slug ) {
		return sanitize_title( $slug );
	}

	function is_email( $email ) {
		return is_email( $email );
	}

}
