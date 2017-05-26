<?php

/* Options/Meta */

/* Internal */

function bb_sanitize_meta_key( $key )
{
	return preg_replace( '|[^a-z0-9_]|i', '', $key );
}

/**
 * Adds and updates meta data in the database
 *
 * @internal
 */
function bb_update_meta( $object_id = 0, $meta_key, $meta_value, $type, $global = false )
{
	global $bbdb;
	if ( !is_numeric( $object_id ) || empty( $object_id ) && !$global ) {
		return false;
	}
	$cache_object_id = $object_id = (int) $object_id;
	switch ( $type ) {
		case 'option':
			$object_type = 'bb_option';
			break;
		case 'user' :
			global $wp_users_object;
			$id = $object_id;
			$return = $wp_users_object->update_meta( compact( 'id', 'meta_key', 'meta_value' ) );
			if ( is_wp_error( $return ) ) {
				return false;
			}
			return $return;
			break;
		case 'forum' :
			$object_type = 'bb_forum';
			break;
		case 'topic' :
			$object_type = 'bb_topic';
			break;
		case 'post' :
			$object_type = 'bb_post';
			break;
		default :
			$object_type = $type;
			break;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );

	$meta_tuple = compact( 'object_type', 'object_id', 'meta_key', 'meta_value', 'type' );
	$meta_tuple = apply_filters( 'bb_update_meta', $meta_tuple );
	extract( $meta_tuple, EXTR_OVERWRITE );

	$meta_value = $_meta_value = maybe_serialize( $meta_value );
	$meta_value = maybe_unserialize( $meta_value );

	$cur = $bbdb->get_row( $bbdb->prepare( "SELECT * FROM `$bbdb->meta` WHERE `object_type` = %s AND `object_id` = %d AND `meta_key` = %s", $object_type, $object_id, $meta_key ) );
	if ( !$cur ) {
		$bbdb->query( $bbdb->prepare(
			"INSERT INTO `$bbdb->meta` ( `object_type`, `object_id`, `meta_key`, `meta_value` ) VALUES( %s, %d, %s, %s )
			ON DUPLICATE KEY UPDATE `meta_value` = VALUES( `meta_value` )",
			$object_type, $object_id, $meta_key, $_meta_value
		) );
	} elseif ( $cur->meta_value != $meta_value ) {
		$bbdb->update( $bbdb->meta, array( 'meta_value' => $_meta_value), array( 'object_type' => $object_type, 'object_id' => $object_id, 'meta_key' => $meta_key ) );
	}

	if ( $object_type === 'bb_option' ) {
		$cache_object_id = $meta_key;
		wp_cache_delete( $cache_object_id, 'bb_option_not_set' );
	}
	wp_cache_delete( $cache_object_id, $object_type );

	if ( !$cur ) {
		return true;
	}
}

/**
 * Deletes meta data from the database
 *
 * @internal
 */
function bb_delete_meta( $object_id = 0, $meta_key, $meta_value, $type, $global = false )
{
	global $bbdb;
	if ( !is_numeric( $object_id ) || empty( $object_id ) && !$global ) {
		return false;
	}
	$cache_object_id = $object_id = (int) $object_id;
	switch ( $type ) {
		case 'option':
			$object_type = 'bb_option';
			break;
		case 'user':
			global $wp_users_object;
			$id = $object_id;
			return $wp_users_object->delete_meta( compact( 'id', 'meta_key', 'meta_value' ) );
			break;
		case 'forum':
			$object_type = 'bb_forum';
			break;
		case 'topic':
			$object_type = 'bb_topic';
			break;
		case 'post':
			$object_type = 'bb_post';
			break;
		default:
			$object_type = $type;
			break;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );

	$meta_tuple = compact( 'object_type', 'object_id', 'meta_key', 'meta_value', 'type' );
	$meta_tuple = apply_filters( 'bb_delete_meta', $meta_tuple );
	extract( $meta_tuple, EXTR_OVERWRITE );

	$meta_value = maybe_serialize( $meta_value );

	if ( empty( $meta_value ) ) {
		$meta_sql = $bbdb->prepare( "SELECT `meta_id` FROM `$bbdb->meta` WHERE `object_type` = %s AND `object_id` = %d AND `meta_key` = %s", $object_type, $object_id, $meta_key );
	} else {
		$meta_sql = $bbdb->prepare( "SELECT `meta_id` FROM `$bbdb->meta` WHERE `object_type` = %s AND `object_id` = %d AND `meta_key` = %s AND `meta_value` = %s", $object_type, $object_id, $meta_key, $meta_value );
	}

	if ( !$meta_id = $bbdb->get_var( $meta_sql ) ) {
		return false;
	}

	$bbdb->query( $bbdb->prepare( "DELETE FROM `$bbdb->meta` WHERE `meta_id` = %d", $meta_id ) );

	if ( $object_type == 'bb_option' ) {
		$cache_object_id = $meta_key;
		wp_cache_delete( $cache_object_id, 'bb_option_not_set' );
	}
	wp_cache_delete( $cache_object_id, $object_type );
	return true;
}

/**
 * Adds an objects meta data to the object
 *
 * This is the only function that should add to user / topic - NOT bbdb::prepared
 *
 * @internal
 */
function bb_append_meta( $object, $type )
{
	global $bbdb;
	switch ( $type ) {
		case 'user':
			global $wp_users_object;
			return $wp_users_object->append_meta( $object );
			break;
		case 'forum':
			$object_id_column = 'forum_id';
			$object_type = 'bb_forum';
			$slug = 'forum_slug';
			break;
		case 'topic':
			$object_id_column = 'topic_id';
			$object_type = 'bb_topic';
			$slug = 'topic_slug';
			break;
		case 'post':
			$object_id_column = 'post_id';
			$object_type = 'bb_post';
			$slug = false;
			break;
	}

	if ( is_array( $object ) && count( $object ) ) {
		$trans = array();
		foreach ( array_keys( $object ) as $i ) {
			$trans[$object[$i]->$object_id_column] =& $object[$i];
		}
		$ids = array_map( 'intval', array_keys( $trans ) );
		$query_ids = array();
		$cached_objects = array();
		$position = 0;
		foreach ( $ids as $_id ) {
			if ( false !== $_cached_object = wp_cache_get( $_id, $object_type ) ) {
				$cached_objects[$position] = $_cached_object;
			} else {
				$query_ids[$position] = $_id;
			}
			$position++;
		}
		if ( !count( $query_ids ) ) {
			return $cached_objects;
		}

		$_query_ids = $query_ids;
		sort( $_query_ids );
		$_query_ids = join( ',', $_query_ids );

		if ( $metas = $bbdb->get_results( "SELECT `object_id`, `meta_key`, `meta_value` FROM `$bbdb->meta` WHERE `object_type` = '$object_type' AND `object_id` IN ($_query_ids) /* bb_append_meta */" ) ) {
			usort( $metas, '_bb_append_meta_sort' );
			foreach ( $metas as $meta ) {
				$trans[$meta->object_id]->{$meta->meta_key} = maybe_unserialize( $meta->meta_value );
				if ( strpos($meta->meta_key, $bbdb->prefix) === 0 ) {
					$trans[$meta->object_id]->{substr($meta->meta_key, strlen($bbdb->prefix))} = maybe_unserialize( $meta->meta_value );
				}
			}
		}
		foreach ( $query_ids as $position => $i ) {
			$cached_objects[$position] = $trans[$i];
			wp_cache_add( $i, $trans[$i], $object_type );
			if ( $slug ) {
				wp_cache_add( $trans[$i]->$slug, $i, 'bb_' . $slug );
			}
		}
		if ( !count( $cached_objects ) ) {
			return $object;
		}
		ksort( $cached_objects );

		return $cached_objects;
	} elseif ( $object ) {
		if ( false !== $cached_object = wp_cache_get( $object->$object_id_column, $object_type ) ) {
			return $cached_object;
		}
		if ( $metas = $bbdb->get_results( $bbdb->prepare( "SELECT `object_id`, `meta_key`, `meta_value` FROM `$bbdb->meta` WHERE `object_type` = '$object_type' AND `object_id` = %d /* bb_append_meta */", $object->$object_id_column ) ) ) {
			usort( $metas, '_bb_append_meta_sort' );
			foreach ( $metas as $meta ) {
				$object->{$meta->meta_key} = maybe_unserialize( $meta->meta_value );
				if ( strpos( $meta->meta_key, $bbdb->prefix ) === 0 ) {
					$object->{substr( $meta->meta_key, strlen( $bbdb->prefix ) )} = $object->{$meta->meta_key};
				}
			}
		}
		if ( $object->$object_id_column ) {
			wp_cache_add( $object->$object_id_column, $object, $object_type );
			if ( $slug ) {
				wp_cache_add( $object->$slug, $object->$object_id_column, 'bb_' . $slug );
			}
		}
		return $object;
	}
}

/**
 * Sorts meta keys by length to ensure $appended_object->{$bbdb->prefix} key overwrites $appended_object->key as desired
 *
 * @internal
 */
function _bb_append_meta_sort( $a, $b )
{
	return strlen( $a->meta_key ) - strlen( $b->meta_key );
}



/* Options */

/**
 * Echoes the requested bbPress option by calling bb_get_option()
 *
 * @param string The option to be echoed
 * @return void
 */
function bb_option( $option )
{
	echo apply_filters( 'bb_option_' . $option, bb_get_option( $option ) );
}

/**
 * Returns the requested bbPress option from the meta table or the $bb object
 *
 * @param string The option to be echoed
 * @return mixed The value of the option
 */
function bb_get_option( $option )
{
	// Allow plugins to short-circuit options.
	if ( false !== $r = apply_filters( 'bb_pre_get_option_' . $option, false, $option ) ) {
		return $r;
	}

	global $bb;

	switch ( $option ) {
		case 'site_id':
			if ( isset( $bb->site_id ) && is_numeric( $bb->site_id ) ) {
				$r = (int) $bb->site_id;
			} else {
				$r = 1;
			}
			break;
		case 'language':
			$r = str_replace( '_', '-', bb_get_locale() );
			break;
		case 'text_direction':
			global $bb_locale;
			$r = $bb_locale->text_direction;
			break;
		case 'version':
			return '1.2'; // Don't filter
			break;
		case 'bb_db_version' :
			return '2471'; // Don't filter
			break;
		case 'html_type':
			$r = 'text/html';
			break;
		case 'charset':
			$r = 'UTF-8';
			break;
		case 'bb_table_prefix':
		case 'table_prefix':
			global $bbdb;
			return $bbdb->prefix; // Don't filter;
			break;
		case 'url':
			$option = 'uri';
		default:
			if ( isset( $bb->$option ) ) {
				$r = $bb->$option;
				if ( $option === 'mod_rewrite' ) {
					if ( is_bool( $r ) ) {
						$r = (int) $r;
					}
				}
				break;
			}

			$r = bb_get_option_from_db( $option );

			if ( !$r ) {
				switch ( $option ) {
					case 'name':
						$r = __( 'Please give me a name!' );
						break;
					case 'wp_table_prefix' :
						global $wp_table_prefix; // This global is deprecated
						return $wp_table_prefix; // Don't filter;
						break;
					case 'mod_rewrite':
						$r = 0;
						break;
					case 'page_topics':
						$r = 30;
						break;
					case 'edit_lock':
						$r = 60;
						break;
					case 'gmt_offset':
						$r = 0;
						break;
					case 'uri_ssl':
						$r = preg_replace( '|^http://|i', 'https://', bb_get_option( 'uri' ) );
						break;
					case 'throttle_time':
						$r = 30;
						break;
					case 'email_login':
						$r = false;
						break;
				}
			}
			break;
	}

	return apply_filters( 'bb_get_option_' . $option, $r, $option );
}

/**
 * Retrieves and returns the requested bbPress option from the meta table
 *
 * @param string The option to be echoed
 * @return void
 */
function bb_get_option_from_db( $option )
{
	global $bbdb;
	$option = bb_sanitize_meta_key( $option );

	if ( wp_cache_get( $option, 'bb_option_not_set' ) ) {
		$r = null;
	} elseif ( false !== $_r = wp_cache_get( $option, 'bb_option' ) ) {
		$r = $_r;
	} else {
		if ( BB_INSTALLING ) {
			$bbdb->suppress_errors();
		}
		$row = $bbdb->get_row( $bbdb->prepare( "SELECT `meta_value` FROM `$bbdb->meta` WHERE `object_type` = 'bb_option' AND `meta_key` = %s", $option ) );
		if ( BB_INSTALLING ) {
			$bbdb->suppress_errors( false );
		}

		if ( is_object( $row ) ) {
			$r = maybe_unserialize( $row->meta_value );
		} else {
			$r = null;
		}
	}

	if ( $r === null ) {
		wp_cache_set( $option, true, 'bb_option_not_set' );
	} else {
		wp_cache_set( $option, $r, 'bb_option' );
	}

	return apply_filters( 'bb_get_option_from_db_' . $option, $r, $option );
}

function bb_form_option( $option )
{
	echo bb_get_form_option( $option );
}

function bb_get_form_option( $option )
{
	return esc_attr( bb_get_option( $option ) );
}

// Don't use the return value; use the API. Only returns options stored in DB.
function bb_cache_all_options()
{
	$base_options = array(
		'site_id',
		'bb_db_version',
		'name',
		'description',
		'uri_ssl',
		'from_email',
		'bb_auth_salt',
		'bb_secure_auth_salt',
		'bb_logged_in_salt',
		'bb_nonce_salt',
		'page_topics',
		'edit_lock',
		'bb_active_theme',
		'active_plugins',
		'mod_rewrite',
		'datetime_format',
		'date_format',
		'avatars_show',
		'avatars_default',
		'avatars_rating',
		'wp_table_prefix',
		'user_bbdb_name',
		'user_bbdb_user',
		'user_bbdb_password',
		'user_bbdb_host',
		'user_bbdb_charset',
		'user_bbdb_collate',
		'custom_user_table',
		'custom_user_meta_table',
		'wp_siteurl',
		'wp_home',
		'cookiedomain',
		'usercookie',
		'passcookie',
		'authcookie',
		'cookiepath',
		'sitecookiepath',
		'secure_auth_cookie',
		'logged_in_cookie',
		'admin_cookie_path',
		'core_plugins_cookie_path',
		'user_plugins_cookie_path',
		'wp_admin_cookie_path',
		'wp_plugins_cookie_path',
		'wordpress_mu_primary_blog_id',
		'enable_loginless',
		'enable_subscriptions',
		'enable_xmlrpc',
		'enable_pingback',
		'throttle_time',
		'bb_xmlrpc_allow_user_switching',
		'bp_bbpress_cron',
		'email_login',
		'static_title',
		'plugin_cookie_paths',
		'wp_roles_map',
		'gmt_offset',
		'timezone_string',
		'name_link_profile',
		'bp_bbpress_cron_check',
	);

	// Check that these aren't already in the cache
	$query_options = array();
	foreach ( $base_options as $base_option ) {
		if ( isset( $bb->$base_option ) ) {
			continue;
		}
		if ( wp_cache_get( $base_option, 'bb_option_not_set' ) ) {
			continue;
		}
		if ( false === wp_cache_get( $base_option, 'bb_option' ) ) {
			$query_options[] = $base_option;
			wp_cache_set( $base_option, true, 'bb_option_not_set' );
		}
	}
	if ( !count( $query_options ) ) {
		// It's all in cache
		return true;
	}

	$query_keys = "('" . join( "','", $query_options ) . "')";

	global $bbdb;
	$results = $bbdb->get_results( "SELECT `meta_key`, `meta_value` FROM `$bbdb->meta` WHERE `object_type` = 'bb_option' AND `meta_key` IN $query_keys;" );

	if ( count( $base_options ) === count( $query_options ) && ( !$results || !is_array( $results ) || !count( $results ) ) ) {
		// Let's assume that the options haven't been populated from the old topicmeta table
		if ( !BB_INSTALLING && ( !defined( 'BB_DO_NOT_UPGRADE_TOPICMETA' ) || !BB_DO_NOT_UPGRADE_TOPICMETA ) ) {
			$topicmeta_exists = $bbdb->query( "SELECT * FROM $bbdb->topicmeta LIMIT 1" );
			if ($topicmeta_exists) {
				require_once( BB_PATH . 'bb-admin/includes/defaults.bb-schema.php' );
				// Create the meta table
				$bbdb->query( $bb_queries['meta'] );
				// Copy options
				$bbdb->query( "INSERT INTO `$bbdb->meta` (`meta_key`, `meta_value`) SELECT `meta_key`, `meta_value` FROM `$bbdb->topicmeta` WHERE `topic_id` = 0;" );
				// Copy topic meta
				$bbdb->query( "INSERT INTO `$bbdb->meta` (`object_id`, `meta_key`, `meta_value`) SELECT `topic_id`, `meta_key`, `meta_value` FROM `$bbdb->topicmeta` WHERE `topic_id` != 0;" );
				// Entries with an object_id are topic meta at this stage
				$bbdb->query( "UPDATE `$bbdb->meta` SET `object_type` = 'bb_topic' WHERE `object_id` != 0" );
			}
			unset( $topicmeta_exists );

			return bb_cache_all_options();
		}

		return false;
	} else {
		foreach ( $results as $options ) {
			wp_cache_delete( $options->meta_key, 'bb_option_not_set' );
			wp_cache_set( $options->meta_key, maybe_unserialize( $options->meta_value ), 'bb_option' );
		}
	}

	return true;
}

// Can store anything but NULL.
function bb_update_option( $option, $value )
{
	return bb_update_meta( 0, $option, $value, 'option', true );
}

function bb_delete_option( $option, $value = '' )
{
	return bb_delete_meta( 0, $option, $value, 'option', true );
}

/**
 * Delete a transient
 *
 * @since 1.0
 * @package bbPress
 * @subpackage Transient
 *
 * @param string $transient Transient name. Expected to not be SQL-escaped
 * @return bool true if successful, false otherwise
 */
function bb_delete_transient( $transient )
{
	global $_bb_using_ext_object_cache, $bbdb;

	if ( $_bb_using_ext_object_cache ) {
		return wp_cache_delete( $transient, 'transient' );
	} else {
		$transient = '_transient_' . $bbdb->escape( $transient );
		return bb_delete_option( $transient );
	}
}

/**
 * Get the value of a transient
 *
 * If the transient does not exist or does not have a value, then the return value
 * will be false.
 * 
 * @since 1.0
 * @package bbPress
 * @subpackage Transient
 *
 * @param string $transient Transient name. Expected to not be SQL-escaped
 * @return mixed Value of transient
 */
function bb_get_transient( $transient )
{
	global $_bb_using_ext_object_cache, $bbdb;

	if ( $_bb_using_ext_object_cache ) {
		$value = wp_cache_get( $transient, 'transient' );
	} else {
		$transient_option = '_transient_' . $bbdb->escape( $transient );
		$transient_timeout = '_transient_timeout_' . $bbdb->escape( $transient );
		$timeout = bb_get_option( $transient_timeout );
		if ( $timeout && $timeout < time() ) {
			bb_delete_option( $transient_option );
			bb_delete_option( $transient_timeout );
			return false;
		}

		$value = bb_get_option( $transient_option );
	}

	return apply_filters( 'transient_' . $transient, $value );
}

/**
 * Set/update the value of a transient
 *
 * You do not need to serialize values, if the value needs to be serialize, then
 * it will be serialized before it is set.
 *
 * @since 1.0
 * @package bbPress
 * @subpackage Transient
 *
 * @param string $transient Transient name. Expected to not be SQL-escaped
 * @param mixed $value Transient value.
 * @param int $expiration Time until expiration in seconds, default 0
 * @return bool False if value was not set and true if value was set.
 */
function bb_set_transient( $transient, $value, $expiration = 0 )
{
	global $_bb_using_ext_object_cache, $bbdb;

	if ( $_bb_using_ext_object_cache ) {
		return wp_cache_set( $transient, $value, 'transient', $expiration );
	} else {
		$transient_timeout = '_transient_timeout_' . $bbdb->escape( $transient );
		$transient = '_transient_' . $bbdb->escape( $transient );
		if ( 0 != $expiration ) {
			bb_update_option( $transient_timeout, time() + $expiration );
		}
		return bb_update_option( $transient, $value );
	}
}



/* User meta */

function bb_get_usermeta( $user_id, $meta_key )
{
	if ( !$user = bb_get_user( $user_id ) ) {
		return;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );
	if ( !isset( $user->$meta_key ) ) {
		return;
	}
	return $user->$meta_key;
}

function bb_update_usermeta( $user_id, $meta_key, $meta_value )
{
	return bb_update_meta( $user_id, $meta_key, $meta_value, 'user' );
}

// $meta_value defaults to null to conform to BackPress' WP_User::delete_meta()
function bb_delete_usermeta( $user_id, $meta_key, $meta_value = null )
{
	return bb_delete_meta( $user_id, $meta_key, $meta_value, 'user' );
}

/**
 * Saves and restores user interface settings stored in a cookie.
 *
 * Checks if the current user-settings cookie is updated and stores it. When no
 * cookie exists (different browser used), adds the last saved cookie restoring
 * the settings.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 */
function bb_user_settings()
{
	if ( !defined( 'BB_IS_ADMIN' ) || !BB_IS_ADMIN ) {
		return;
	}

	if ( defined( 'DOING_AJAX' ) ) {
		return;
	}

	if ( !$user = bb_get_current_user() ) {
		return;
	}

	$settings = bb_get_usermeta( $user->ID, 'bb_user_settings' );

	if ( isset( $_COOKIE['bb-user-settings-' . $user->ID] ) ) {
		$cookie = preg_replace( '/[^A-Za-z0-9=&_]/', '', $_COOKIE['bb-user-settings-' . $user->ID] );

		if ( !empty( $cookie ) && strpos( $cookie, '=' ) ) {
			if ( $cookie == $settings ) {
				return;
			}

			$last_time = (int) bb_get_usermeta( $user->ID, 'bb_user_settings_time' );
			$saved = isset( $_COOKIE['bb-user-settings-time-' . $user->ID] ) ? preg_replace( '/[^0-9]/', '', $_COOKIE['bb-user-settings-time-' . $user->ID] ) : 0;

			if ( $saved > $last_time ) {
				bb_update_usermeta( $user->ID, 'bb_user_settings', $cookie );
				bb_update_usermeta( $user->ID, 'bb_user_settings_time', time() - 5 );
				return;
			}
		}
	}

	setcookie( 'bb-user-settings-' . $user->ID, $settings, time() + 31536000, $bb->cookiepath );
	setcookie( 'bb-user-settings-time-' . $user->ID, time(), time() + 31536000, $bb->cookiepath );
}

/**
 * Retrieve user interface setting value based on setting name.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 *
 * @param string $name The name of the setting.
 * @param string $default Optional default value to return when $name is not set.
 * @return mixed the last saved user setting or the default value/false if it doesn't exist.
 */
function bb_get_user_setting( $name, $default = false )
{
	$arr = bb_get_all_user_settings();

	return isset( $arr[$name] ) ? $arr[$name] : $default;
}

/**
 * Adds or updates a user interface setting value based on setting name.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 *
 * @param string $name The name of the setting.
 * @param string $value The value of the setting.
 */
function bb_update_user_setting( $name, $value )
{
	if ( is_null( $value ) || $value === '' ) {
		return;
	}

	if ( !$user = bb_get_current_user() ) {
		return;
	}

	$name = (string) $name;
	$name = preg_replace( '/[^A-Za-z0-9_]/', '', $name );

	$value = (string) $value;
	$value = preg_replace( '/[^A-Za-z0-9_]/', '', $value );

	$arr = bb_get_all_user_settings();
	$arr[$name] = $value;

	foreach ( $arr as $k => $v ) {
		$settings .= $k . '=' . $v . '&';
	}
	$settings = rtrim( $settings, '&' );

	bb_update_usermeta( $user->ID, 'bb_user_settings', $settings );
	setcookie( 'bb-user-settings-' . $user->ID, $settings, time() + 31536000, $bb->cookiepath );
}

/**
 * Delete user interface settings.
 *
 * Deleting settings would reset them to the defaults.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 *
 * @param mixed $names The name or array of names of the setting to be deleted.
 */
function bb_delete_user_setting( $names )
{
	$arr = bb_get_all_user_settings();
	$names = (array) $names;

	if ( !$user = bb_get_current_user() ) {
		return;
	}

	foreach ( $names as $name ) {
		if ( isset( $arr[$name] ) ) {
			unset( $arr[$name] );
			$settings = '';
		}
	}

	if ( isset( $settings ) ) {
		foreach ( $arr as $k => $v ) {
			$settings .= $k . '=' . $v . '&';
		}
		$settings = rtrim( $settings, '&' );

		bb_update_usermeta( $user->ID, 'bb_user_settings', $settings );
		setcookie( 'bb-user-settings-' . $user->ID, $settings, time() + 31536000, $bb->cookiepath );
	}
}

/**
 * Retrieve all user interface settings.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 *
 * @return array the last saved user settings or empty array.
 */
function bb_get_all_user_settings()
{
	if ( ! $user = bb_get_current_user() ) {
		return array();
	}

	$arr = array();
	if ( isset( $_COOKIE['bb-user-settings-' . $user->ID] ) ) {
		$cookie = preg_replace( '/[^A-Za-z0-9=&_]/', '', $_COOKIE['bb-user-settings-' . $user->ID] );

		if ( $cookie && strpos( $cookie, '=' ) ) { // the '=' cannot be 1st char
			parse_str( $cookie, $arr );
		}
	} elseif ( isset( $user->bb_user_settings ) && is_string( $user->bb_user_settings ) ) {
		parse_str( $user->bb_user_settings, $arr );
	}

	return $arr;
}

/**
 * Delete the user settings of the current user.
 *
 * @package bbPress
 * @subpackage Meta
 * @since 1.0
 */
function bb_delete_all_user_settings()
{
	if ( !$user = bb_get_current_user() ) {
		return;
	}

	bb_delete_usermeta( $user->ID, 'bb_user_settings' );
	setcookie( 'bb-user-settings-' . $user->ID, ' ', time() - 31536000, $bb->cookiepath );
}




/* Forum meta */

function bb_get_forummeta( $forum_id, $meta_key )
{
	if ( !$forum = bb_get_forum( $forum_id ) ) {
		return;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );
	if ( !isset( $forum->$meta_key ) ) {
		return;
	}
	return $forum->$meta_key;
}

function bb_update_forummeta( $forum_id, $meta_key, $meta_value )
{
	return bb_update_meta( $forum_id, $meta_key, $meta_value, 'forum' );
}

function bb_delete_forummeta( $forum_id, $meta_key, $meta_value = '' )
{
	return bb_delete_meta( $forum_id, $meta_key, $meta_value, 'forum' );
}



/* Topic meta */

function bb_get_topicmeta( $topic_id, $meta_key )
{
	if ( !$topic = get_topic( $topic_id ) ) {
		return;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );
	if ( !isset($topic->$meta_key) ) {
		return;
	}
	return $topic->$meta_key;
}

function bb_update_topicmeta( $topic_id, $meta_key, $meta_value )
{
	return bb_update_meta( $topic_id, $meta_key, $meta_value, 'topic' );
}

function bb_delete_topicmeta( $topic_id, $meta_key, $meta_value = '' )
{
	return bb_delete_meta( $topic_id, $meta_key, $meta_value, 'topic' );
}



/* Post meta */

function bb_get_postmeta( $post_id, $meta_key )
{
	if ( !$post = bb_get_post( $post_id ) ) {
		return;
	}

	$meta_key = bb_sanitize_meta_key( $meta_key );
	if ( !isset( $post->$meta_key ) ) {
		return;
	}
	return $post->$meta_key;
}

function bb_update_postmeta( $post_id, $meta_key, $meta_value )
{
	return bb_update_meta( $post_id, $meta_key, $meta_value, 'post' );
}

function bb_delete_postmeta( $post_id, $meta_key, $meta_value = '' )
{
	return bb_delete_meta( $post_id, $meta_key, $meta_value, 'post' );
}
