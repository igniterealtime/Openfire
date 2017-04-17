<?php
/**
 * bbPress Cron Implementation for hosts, which do not offer CRON or for which
 * the user has not setup a CRON job pointing to this file.
 *
 * The HTTP request to this file will not slow down the visitor who happens to
 * visit when the cron job is needed to run.
 *
 * @package bbPress
 */

ignore_user_abort( true );

if ( !empty( $_POST ) || defined( 'DOING_AJAX' ) || defined( 'DOING_CRON' ) ) {
	die();
}

/**
 * Tell bbPress we are doing the CRON task.
 *
 * @var bool
 */
define( 'DOING_CRON', true );

/** Setup bbPress environment */
require_once( './bb-load.php' );

if ( false === $crons = _get_cron_array() ) {
	die();
}

$keys = array_keys( $crons );
$local_time = time();

if ( !is_array( $crons ) || ( isset($keys[0]) && $keys[0] > $local_time ) ) {
	die();
}

foreach ( $crons as $timestamp => $cronhooks ) {
	if ( $timestamp > $local_time ) {
		break;
	}
	foreach ( $cronhooks as $hook => $keys ) {
		foreach ( $keys as $key => $args ) {
			$schedule = $args['schedule'];
			if ( $schedule != false ) {
				$new_args = array( $timestamp, $schedule, $hook, $args['args'] );
				call_user_func_array( 'wp_reschedule_event' , $new_args );
			}
			wp_unschedule_event( $timestamp, $hook, $args['args'] );
 			do_action_ref_array( $hook, $args['args'] );
		}
	}
}
