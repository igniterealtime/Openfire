<?php

/* Our Main function to set all private */
function logged_out_redirect() {
	global $bp;

	// BuddyPress components to lock
	if ( bp_is_activity_component() || bp_is_groups_component() || bp_is_group_forum() /*|| bbp_is_single_forum() || bbp_is_single_topic()*/|| bp_is_forums_component() || bp_is_blogs_component() || bp_is_page( BP_MEMBERS_SLUG ) || bp_is_profile_component() ) {

		// Check if user is logged out	
		if(!is_user_logged_in()) {
			// Check if a page was selected for redirection
			if(of_get_option('redirect')){
				$redirect_page = get_permalink(of_get_option('redirect'));
			// If not redirect to login page
			}else{
				$redirect_page = site_url('/wp-login.php');
			}
		wp_redirect($redirect_page);
		exit;
		} 
	}
}
add_filter('get_header','logged_out_redirect',1);

/* Locking Even RSS Feeds */
function logged_out_rss_feed() {
	remove_action( 'bp_actions', 'bp_activity_action_sitewide_feed', 3 );
	remove_action( 'bp_actions', 'bp_activity_action_personal_feed', 3 );
	remove_action( 'bp_actions', 'bp_activity_action_friends_feed', 3 );
	remove_action( 'bp_actions', 'bp_activity_action_my_groups_feed', 3 );
	remove_action( 'bp_actions', 'bp_activity_action_mentions_feed', 3 );
	remove_action( 'bp_actions', 'bp_activity_action_favorites_feed', 3 );
	remove_action( 'groups_action_group_feed', 'groups_action_group_feed', 3 );
}
add_action('init', 'logged_out_rss_feed'); 

?>