<?php

function bb_get_plugins_callback( $type = 'normal', $path, $filename )
{
	if ( '.php' != substr( $filename, -4 ) ) {
		return false;
	}

	$data = array( 'autoload' => 0 );

	if ( $has_underscore = '_' === substr( $filename, 0, 1 ) ) {
		switch ( $type ) {
			case 'all':
			case 'autoload':
				$data['autoload'] = 1;
				break;
			case 'normal':
			default:
				return false;
				break;
		}
	} elseif ( 'autoload' === $type ) {
		return false;
	}

	if ( $_data = bb_get_plugin_data( $path ) ) {
		return array_merge( $_data , $data );
	}

	return false;
}

function bb_get_plugins( $location = 'all', $type = 'normal', $status = 'all' )
{
	static $plugin_cache = array();

	if ( !in_array( $type, array( 'all', 'autoload', 'normal' ) ) ) {
		$type = 'normal';
	}

	if ( 'autoload' === $type || !in_array( $status, array( 'all', 'active', 'inactive' ) ) ) {
		$status = 'all';
	}

	if ( isset( $plugin_cache[$location][$type][$status] ) ) {
		return $plugin_cache[$location][$type][$status];
	}

	global $bb;
	$directories = array();
	if ( 'all' === $location ) {
		foreach ( $bb->plugin_locations as $_data ) {
			$directories[] = $_data['dir'];
		}
	} elseif ( isset( $bb->plugin_locations[$location]['dir'] ) ) {
		$directories[] = $bb->plugin_locations[$location]['dir'];
	}

	require_once( BB_PATH . BB_INC . 'class.bb-dir-map.php' );

	$plugin_arrays = array();
	foreach ( $directories as $directory ) {
		$dir_map = new BB_Dir_Map(
			$directory,
			array(
				'callback' => 'bb_get_plugins_callback',
				'callback_args' => array( $type ),
				'recurse' => 1
			)
		);
		$dir_plugins = $dir_map->get_results();
		$dir_plugins = is_wp_error( $dir_plugins ) ? array() : $dir_plugins;
		$plugin_arrays[] = $dir_plugins;
		unset($dir_map, $dir_plugins);
	}
	
	$plugins = array();
	foreach ($plugin_arrays as $plugin_array) {
		$plugins = array_merge($plugins, $plugin_array);
	}

	$active_plugins = (array) bb_get_option( 'active_plugins' );
	
	$adjusted_plugins = array();
	foreach ($plugins as $plugin => $plugin_data) {
		$_id = $plugin_data['location'] . '#' . $plugin;
		$plugin_data['active'] = 0;
		if ( 'autoload' === $type || in_array( $_id, $active_plugins ) ) {
			$plugin_data['active'] = 1;
		}
		if (
			'active' === $status && $plugin_data['active'] ||
			'inactive' === $status && !$plugin_data['active'] ||
			'all' === $status
		) {
			$adjusted_plugins[$_id] = $plugin_data;
		}
	}

	uasort( $adjusted_plugins, 'bb_plugins_sort' );

	$plugin_cache[$location][$type][$status] = $adjusted_plugins;

	return $adjusted_plugins;
}

function bb_plugins_sort( $a, $b )
{
	return strnatcasecmp( $a['name'], $b['name'] );
}

function bb_get_plugin_counts()
{
	$all_plugins = bb_get_plugins( 'all', 'all' );
	$active_plugins = (array) bb_get_option( 'active_plugins' );
	$counts = array(
		'plugin_count_all' => count( $all_plugins ),
		'plugin_count_active' => count( $active_plugins ),
		'plugin_count_inactive' => 0,
		'plugin_count_autoload' => 0
	);
	foreach ( $all_plugins as $id => $all_plugin ) {
		if ( $all_plugin['autoload'] ) {
			$counts['plugin_count_autoload']++;
		} elseif ( !in_array( $id, $active_plugins ) ) {
			$counts['plugin_count_inactive']++;
		}
	}
	return $counts;
}

/**
 * Parse the plugin contents to retrieve plugin's metadata.
 *
 * The metadata of the plugin's data searches for the following in the plugin's
 * header. All plugin data must be on its own line. For plugin description, it
 * must not have any newlines or only parts of the description will be displayed
 * and the same goes for the plugin data. The below is formatted for printing.
 *
 * <code>
 * /*
 * Plugin Name: Name of Plugin
 * Plugin URI: Link to plugin information
 * Description: Plugin Description
 * Author: Plugin author's name
 * Author URI: Link to the author's web site
 * Version: Must be set
 * Requires at least: Optional.  Minimum bbPress version this plugin requires
 * Tested up to: Optional. Maximum bbPress version this plugin has been tested with
 * Text Domain: Optional. Unique identifier, should be same as the one used in
 *		bb_load_plugin_textdomain()
 * Domain Path: Optional. Only useful if the translations are located in a
 *		folder above the plugin's base path. For example, if .mo files are
 *		located in the locale folder then Domain Path will be "/locale/" and
 *		must have the first slash. Defaults to the base folder the plugin is
 *		located in.
 *  * / # You must remove the space to close comment (the space is here only for documentation purposes).
 * </code>
 *
 * Plugin data returned array contains the following:
 *		'location' - Location of plugin file
 *		'name' - Name of the plugin, must be unique.
 *		'uri' - Plugin's web site.
 *		'plugin_link' - Title of plugin linked to plugin's web site.
 *		'description' - Description of what the plugin does and/or notes
 *		from the author.
 *		'author' - The author's name
 *		'author_uri' - The author's web site address.
 *		'author_link' - The author's name linked to the author's web site.
 *		'version' - The plugin version number.
 *		'requires' - Minimum bbPress version plugin requires
 *		'tested' - Maximum bbPress version plugin has been tested with
 *		'text_domain' - Plugin's text domain for localization.
 *		'domain_path' - Plugin's relative directory path to .mo files.
 *
 * Some users have issues with opening large files and manipulating the contents
 * for want is usually the first 1kiB or 2kiB. This function stops pulling in
 * the plugin contents when it has all of the required plugin data.
 *
 * The first 8kiB of the file will be pulled in and if the plugin data is not
 * within that first 8kiB, then the plugin author should correct their plugin
 * and move the plugin data headers to the top.
 *
 * The plugin file is assumed to have permissions to allow for scripts to read
 * the file. This is not checked however and the file is only opened for
 * reading.
 *
 * @link http://trac.wordpress.org/ticket/5651 Previous Optimizations.
 * @link http://trac.wordpress.org/ticket/7372 Further and better Optimizations.
 * @since 1.5.0
 *
 * @param string $plugin_file Path to the plugin file
 * @param bool $markup If the returned data should have HTML markup applied
 * @param bool $translate If the returned data should be translated
 * @return array See above for description.
 */
function bb_get_plugin_data( $plugin_file, $markup = true, $translate = true ) {
	global $bb;

	if ( preg_match( '/^([a-z0-9_-]+)#((?:[a-z0-9\/\\_-]+.)+)(php)$/i', $plugin_file, $_matches ) ) {
		$plugin_file = $bb->plugin_locations[$_matches[1]]['dir'] . $_matches[2] . $_matches[3];
		
		$_directory = $bb->plugin_locations[$_matches[1]]['dir'];
		$_plugin = $_matches[2] . $_matches[3];

		if ( !$_plugin ) {
			// Not likely
			return false;
		}

		if ( validate_file( $_plugin ) ) {
			// $plugin has .., :, etc.
			return false;
		}

		$plugin_file = $_directory . $_plugin;
		unset( $_matches, $_directory, $_plugin );
	}

	if ( !file_exists( $plugin_file ) ) {
		// The plugin isn't there
		return false;
	}

	// We don't need to write to the file, so just open for reading.
	$fp = fopen($plugin_file, 'r');

	// Pull only the first 8kiB of the file in.
	$plugin_code = fread( $fp, 8192 );

	// PHP will close file handle, but we are good citizens.
	fclose($fp);

	// Grab just the first commented area from the file
	if ( !preg_match( '|/\*(.*?Plugin Name:.*?)\*/|ims', $plugin_code, $plugin_block ) )
		return false;
        $plugin_data = trim( $plugin_block[1] );

	preg_match( '|Plugin Name:(.*)$|mi', $plugin_data, $name );
	preg_match( '|Plugin URI:(.*)$|mi', $plugin_data, $uri );
	preg_match( '|Version:(.*)|i', $plugin_data, $version );
	preg_match( '|Description:(.*)$|mi', $plugin_data, $description );
	preg_match( '|Author:(.*)$|mi', $plugin_data, $author );
	preg_match( '|Author URI:(.*)$|mi', $plugin_data, $author_uri );
	preg_match( '|Text Domain:(.*)$|mi', $plugin_data, $text_domain );
	preg_match( '|Domain Path:(.*)$|mi', $plugin_data, $domain_path );
	preg_match( '|Requires at least:(.*)$|mi', $plugin_data, $requires );
	preg_match( '|Tested up to:(.*)$|mi', $plugin_data, $tested );

	// Normalise the path to the plugin
	$plugin_file = str_replace( '\\', '/', $plugin_file );

	foreach ( $bb->plugin_locations as $_name => $_data ) {
		$_directory = str_replace( '\\', '/', $_data['dir'] );
		if ( 0 === strpos( $plugin_file, $_directory ) ) {
			$location = array( 1 => $_name );
			break;
		}
	}

	$plugins_allowedtags = array('a' => array('href' => array(),'title' => array()),'abbr' => array('title' => array()),'acronym' => array('title' => array()),'code' => array(),'em' => array(),'strong' => array());

	$fields = array(
		'location' => '',
		'name' => 'html',
		'uri' => 'url',
		'version' => 'text',
		'description' => 'html',
		'author' => 'html',
		'author_uri' => 'url',
		'text_domain' => '',
		'domain_path' => '',
		'requires' => 'text',
		'tested' => 'text',
	);
	foreach ( $fields as $field => $san ) {
		if ( !empty( ${$field} ) ) {
			${$field} = trim(${$field}[1]);
			switch ( $san ) {
			case 'html' :
				${$field} = bb_filter_kses( ${$field} );
				break;
			case 'text' :
				${$field} = esc_html(  ${$field} );
				break;
			case 'url' :
				${$field} = esc_url( ${$field} );
				break;
			}
		} else {
			${$field} = '';
		}
	}

	$plugin_data = compact( array_keys( $fields ) );

	if ( $translate )
		$plugin_data = _bb_get_plugin_data_translate( $plugin_data, $plugin_file );

	if ( $markup )
		$plugin_data['description'] = bb_autop( preg_replace( '/[\r\n]+/', "\n", trim( $plugin_data['description'] ) ) );

	$plugin_data['plugin_link'] = ( $plugin_data['uri'] ) ?
		"<a href='{$plugin_data['uri']}' title='" . esc_attr__( 'Visit plugin site' ) . "'>{$plugin_data['name']}</a>" :
		$plugin_data['name'];
	$plugin_data['author_link'] = ( $plugin_data['author'] && $plugin_data['author_uri'] ) ?
		"<a href='{$plugin_data['author_uri']}' title='" . esc_attr__( 'Visit author homepage' ) . "'>{$plugin_data['author']}</a>" :
		$plugin_data['author'];

	return $plugin_data;
}

function _bb_get_plugin_data_translate( $plugin_data, $plugin_file ) {
	//Translate fields
	if( !empty($plugin_data['text_domain']) ) {
		if( ! empty( $plugin_data['domain_path'] ) )
			bb_load_plugin_textdomain($plugin_data['text_domain'], dirname($plugin_file). $plugin_data['domain_path']);
		else
			bb_load_plugin_textdomain($plugin_data['text_domain'], dirname($plugin_file));

		foreach ( array('name', 'plugin_url', 'description', 'author', 'author_uri', 'version') as $field )
			$plugin_data[$field] = translate($plugin_data[$field], $plugin_data['text_domain']);
	}

	return $plugin_data;
}

/**
 * Attempts activation of plugin in a "sandbox" and redirects on success.
 *
 * A plugin that is already activated will not attempt to be activated again.
 *
 * The way it works is by setting the redirection to the error before trying to
 * include the plugin file. If the plugin fails, then the redirection will not
 * be overwritten with the success message. Also, the options will not be
 * updated and the activation hook will not be called on plugin error.
 *
 * It should be noted that in no way the below code will actually prevent errors
 * within the file. The code should not be used elsewhere to replicate the
 * "sandbox", which uses redirection to work.
 *
 * If any errors are found or text is outputted, then it will be captured to
 * ensure that the success redirection will update the error redirection.
 *
 * @since 1.0
 *
 * @param string $plugin Plugin path to main plugin file with plugin data.
 * @param string $redirect Optional. URL to redirect to.
 * @return WP_Error|null WP_Error on invalid file or null on success.
 */
function bb_activate_plugin( $plugin, $redirect = '' ) {
	$active_plugins = (array) bb_get_option( 'active_plugins' );
	$plugin = bb_plugin_basename( trim( $plugin ) );

	$valid_path = bb_validate_plugin( $plugin );
	if ( is_wp_error( $valid_path ) )
		return $valid_path;

	if ( in_array( $plugin, $active_plugins ) ) {
		return false;
	}

	if ( !empty( $redirect ) ) {
		// We'll override this later if the plugin can be included without fatal error
		wp_redirect( add_query_arg( '_scrape_nonce', bb_create_nonce( 'scrape-plugin_' . $plugin ), $redirect ) ); 
	}

	ob_start();
	@include( $valid_path );
	// Add to the active plugins array
	$active_plugins[] = $plugin;
	ksort( $active_plugins );
	bb_update_option( 'active_plugins', $active_plugins );
	do_action( 'bb_activate_plugin_' . $plugin );
	ob_end_clean();

	return $valid_path;
}

/**
 * Deactivate a single plugin or multiple plugins.
 *
 * The deactivation hook is disabled by the plugin upgrader by using the $silent
 * parameter.
 *
 * @since unknown
 *
 * @param string|array $plugins Single plugin or list of plugins to deactivate.
 * @param bool $silent Optional, default is false. Prevent calling deactivate hook.
 */
function bb_deactivate_plugins( $plugins, $silent = false ) {
	$active_plugins = (array) bb_get_option( 'active_plugins' );

	if ( !is_array( $plugins ) ) {
		$plugins = array( $plugins );
	}

	foreach ( $plugins as $plugin ) {
		$plugin = bb_plugin_basename( trim( $plugin ) );
		if ( !in_array( $plugin, $active_plugins ) ) {
			continue;
		}
		// Remove the deactivated plugin
		array_splice( $active_plugins, array_search( $plugin, $active_plugins ), 1 );
		if ( !$silent ) {
			do_action( 'bb_deactivate_plugin_' . $plugin );
		}
	}

	bb_update_option( 'active_plugins', $active_plugins );
}

/**
 * Validate the plugin path.
 *
 * Checks that the file exists and is valid file.
 *
 * @since 1.0
 * @uses validate_file() to check the passed plugin identifier isn't malformed
 * @uses bb_get_plugin_path() to get the full path of the plugin
 * @uses bb_get_plugins() to get the plugins that actually exist
 *
 * @param string $plugin Plugin Path
 * @param string $location The location of plugin, one of 'user', 'core' or 'all'
 * @param string $type The type of plugin, one of 'all', 'autoload' or 'normal'
 * @return WP_Error|int 0 on success, WP_Error on failure.
 */
function bb_validate_plugin( $plugin, $location = 'all', $type = 'all' ) {
	if ( validate_file( trim( $plugin ) ) ) {
		return new WP_Error( 'plugin_invalid', __( 'Invalid plugin path.' ) );
	}
	$path = bb_get_plugin_path( trim( $plugin ) );
	if ( !file_exists( $path ) ) {
		return new WP_Error( 'plugin_not_found', __( 'Plugin file does not exist.' ) );
	}
	if ( !in_array( trim( $plugin ), array_keys( bb_get_plugins( $location, $type ) ) ) ) {
		return new WP_Error( 'plugin_not_available', __( 'That type of plugin is not available in the specified location.' ) );
	}

	return $path;
}
