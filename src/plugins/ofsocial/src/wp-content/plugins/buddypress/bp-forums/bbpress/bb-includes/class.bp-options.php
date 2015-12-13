<?php
/**
 * BP_Options allows storage of options for BackPress
 * in the bbPress database
 *
 * @see BP_Options_Interface
 * @package bbPress
 */
class BP_Options
{
	function prefix()
	{
		return 'bp_bbpress_';
	}
	
	function get( $option )
	{
		switch ( $option ) {
			case 'application_id':
				return bb_get_option( 'site_id' );
				break;
			case 'application_uri':
				return bb_get_uri( null, null, BB_URI_CONTEXT_NONE );
				break;
			case 'cron_uri':
				return bb_get_uri( 'bb-cron.php', array( 'check' => BP_Options::get( 'cron_check' ) ), BB_URI_CONTEXT_WP_HTTP_REQUEST );
				break;
			case 'wp_http_version':
				return 'bbPress/' . bb_get_option( 'version' );
				break;
			case 'hash_function_name':
				return 'bb_hash';
				break;
			case 'language_locale':
				return bb_get_locale();
				break;
			case 'language_directory':
				return BB_LANG_DIR;
				break;
			case 'charset':
			case 'gmt_offset':
			case 'timezone_string':
				return bb_get_option( $option );
				break;
			default:
				return bb_get_option( BP_Options::prefix() . $option );
				break;
		}
	}
	
	function add( $option, $value )
	{
		return BP_Options::update( $option, $value );
	}
	
	function update( $option, $value )
	{
		return bb_update_option( BP_Options::prefix() . $option, $value );
	}
	
	function delete( $option )
	{
		return bb_delete_option( BP_Options::prefix() . $option );
	}
} // END class BP_Options

/**
 * Allows storage of transients for BackPress
 *
 * @see BP_Transients_Interface
 * @package bbPress
 */
class BP_Transients
{
	function prefix()
	{
		return 'bp_bbpress_';
	}
	
	function get( $transient )
	{
		return bb_get_transient( BP_Transients::prefix() . $transient );
	}
	
	function set( $transient, $value, $expiration = 0 )
	{
		return bb_set_transient( BP_Transients::prefix() . $transient, $value, $expiration );
	}
	
	function delete( $transient )
	{
		return bb_delete_transient( BP_Transients::prefix() . $transient );
	}
} // END class BP_Transients
