<?php
/**
 * BuddyPress Blogs Actions.
 *
 * @package BuddyPress
 * @subpackage BlogsActions
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * Redirect to a random blog in the multisite network.
 *
 * @since 1.0.0
 */
function bp_blogs_redirect_to_random_blog() {

	// Bail if not looking for a random blog
	if ( ! bp_is_blogs_component() || ! isset( $_GET['random-blog'] ) )
		return;

	// Multisite is active so find a random blog
	if ( is_multisite() ) {
		$blog = bp_blogs_get_random_blogs( 1, 1 );
		bp_core_redirect( get_home_url( $blog['blogs'][0]->blog_id ) );

	// No multisite and still called, always redirect to root
	} else {
		bp_core_redirect( bp_core_get_root_domain() );
	}
}
add_action( 'bp_actions', 'bp_blogs_redirect_to_random_blog' );
