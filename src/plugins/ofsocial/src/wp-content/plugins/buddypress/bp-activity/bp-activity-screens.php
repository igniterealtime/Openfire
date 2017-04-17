<?php
/**
 * BuddyPress Activity Screens.
 *
 * The functions in this file detect, with each page load, whether an Activity
 * component page is being requested. If so, it parses any necessary data from
 * the URL, and tells BuddyPress to load the appropriate template.
 *
 * @package BuddyPress
 * @subpackage ActivityScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Load the Activity directory.
 *
 * @since 1.5.0
 *
 * @uses bp_displayed_user_id()
 * @uses bp_is_activity_component()
 * @uses bp_current_action()
 * @uses bp_update_is_directory()
 * @uses do_action() To call the 'bp_activity_screen_index' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_screen_index' hook.
 */
function bp_activity_screen_index() {
	if ( bp_is_activity_directory() ) {
		bp_update_is_directory( true, 'activity' );

		/**
		 * Fires right before the loading of the Activity directory screen template file.
		 *
		 * @since 1.5.0
		 */
		do_action( 'bp_activity_screen_index' );

		/**
		 * Filters the template to load for the Activity directory screen.
		 *
		 * @since 1.5.0
		 *
		 * @param string $template Path to the activity template to load.
		 */
		bp_core_load_template( apply_filters( 'bp_activity_screen_index', 'activity/index' ) );
	}
}
add_action( 'bp_screens', 'bp_activity_screen_index' );

/**
 * Load the 'My Activity' page.
 *
 * @since 1.0.0
 *
 * @uses do_action() To call the 'bp_activity_screen_my_activity' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_my_activity' hook.
 */
function bp_activity_screen_my_activity() {

	/**
	 * Fires right before the loading of the "My Activity" screen template file.
	 *
	 * @since 1.0.0
	 */
	do_action( 'bp_activity_screen_my_activity' );

	/**
	 * Filters the template to load for the "My Activity" screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_my_activity', 'members/single/home' ) );
}

/**
 * Load the 'My Friends' activity page.
 *
 * @since 1.0.0
 *
 * @uses bp_is_active()
 * @uses bp_update_is_item_admin()
 * @uses bp_current_user_can()
 * @uses do_action() To call the 'bp_activity_screen_friends' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_friends_activity' hook.
 */
function bp_activity_screen_friends() {
	if ( !bp_is_active( 'friends' ) )
		return false;

	bp_update_is_item_admin( bp_current_user_can( 'bp_moderate' ), 'activity' );

	/**
	 * Fires right before the loading of the "My Friends" screen template file.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_activity_screen_friends' );

	/**
	 * Filters the template to load for the "My Friends" screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_friends_activity', 'members/single/home' ) );
}

/**
 * Load the 'My Groups' activity page.
 *
 * @since 1.2.0
 *
 * @uses bp_is_active()
 * @uses bp_update_is_item_admin()
 * @uses bp_current_user_can()
 * @uses do_action() To call the 'bp_activity_screen_groups' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_groups_activity' hook.
 */
function bp_activity_screen_groups() {
	if ( !bp_is_active( 'groups' ) )
		return false;

	bp_update_is_item_admin( bp_current_user_can( 'bp_moderate' ), 'activity' );

	/**
	 * Fires right before the loading of the "My Groups" screen template file.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_activity_screen_groups' );

	/**
	 * Filters the template to load for the "My Groups" screen.
	 *
	 * @since 1.2.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_groups_activity', 'members/single/home' ) );
}

/**
 * Load the 'Favorites' activity page.
 *
 * @since 1.2.0
 *
 * @uses bp_update_is_item_admin()
 * @uses bp_current_user_can()
 * @uses do_action() To call the 'bp_activity_screen_favorites' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_favorite_activity' hook.
 */
function bp_activity_screen_favorites() {
	bp_update_is_item_admin( bp_current_user_can( 'bp_moderate' ), 'activity' );

	/**
	 * Fires right before the loading of the "Favorites" screen template file.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_activity_screen_favorites' );

	/**
	 * Filters the template to load for the "Favorites" screen.
	 *
	 * @since 1.2.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_favorite_activity', 'members/single/home' ) );
}

/**
 * Load the 'Mentions' activity page.
 *
 * @since 1.2.0
 *
 * @uses bp_update_is_item_admin()
 * @uses bp_current_user_can()
 * @uses do_action() To call the 'bp_activity_screen_mentions' hook.
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_mention_activity' hook.
 */
function bp_activity_screen_mentions() {
	bp_update_is_item_admin( bp_current_user_can( 'bp_moderate' ), 'activity' );

	/**
	 * Fires right before the loading of the "Mentions" screen template file.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_activity_screen_mentions' );

	/**
	 * Filters the template to load for the "Mentions" screen.
	 *
	 * @since 1.2.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_mention_activity', 'members/single/home' ) );
}

/**
 * Reset the logged-in user's new mentions data when he visits his mentions screen.
 *
 * @since 1.5.0
 *
 * @uses bp_is_my_profile()
 * @uses bp_activity_clear_new_mentions()
 * @uses bp_loggedin_user_id()
 */
function bp_activity_reset_my_new_mentions() {
	if ( bp_is_my_profile() )
		bp_activity_clear_new_mentions( bp_loggedin_user_id() );
}
add_action( 'bp_activity_screen_mentions', 'bp_activity_reset_my_new_mentions' );

/**
 * Load the page for a single activity item.
 *
 * @since 1.2.0
 *
 * @uses bp_is_activity_component()
 * @uses bp_activity_get_specific()
 * @uses bp_current_action()
 * @uses bp_action_variables()
 * @uses bp_do_404()
 * @uses bp_is_active()
 * @uses groups_get_group()
 * @uses groups_is_user_member()
 * @uses apply_filters_ref_array() To call the 'bp_activity_permalink_access' hook.
 * @uses do_action() To call the 'bp_activity_screen_single_activity_permalink' hook.
 * @uses bp_core_add_message()
 * @uses is_user_logged_in()
 * @uses bp_core_redirect()
 * @uses site_url()
 * @uses esc_url()
 * @uses bp_get_root_domain()
 * @uses bp_get_activity_root_slug()
 * @uses bp_core_load_template()
 * @uses apply_filters() To call the 'bp_activity_template_profile_activity_permalink' hook.
 */
function bp_activity_screen_single_activity_permalink() {
	$bp = buddypress();

	// No displayed user or not viewing activity component.
	if ( !bp_is_activity_component() )
		return false;

	if ( ! bp_current_action() || !is_numeric( bp_current_action() ) )
		return false;

	// Get the activity details.
	$activity = bp_activity_get_specific( array( 'activity_ids' => bp_current_action(), 'show_hidden' => true, 'spam' => 'ham_only', ) );

	// 404 if activity does not exist
	if ( empty( $activity['activities'][0] ) || bp_action_variables() ) {
		bp_do_404();
		return;

	} else {
		$activity = $activity['activities'][0];
	}

	// Default access is true.
	$has_access = true;

	// If activity is from a group, do an extra cap check.
	if ( isset( $bp->groups->id ) && $activity->component == $bp->groups->id ) {

		// Activity is from a group, but groups is currently disabled.
		if ( !bp_is_active( 'groups') ) {
			bp_do_404();
			return;
		}

		// Check to see if the group is not public, if so, check the
		// user has access to see this activity.
		if ( $group = groups_get_group( array( 'group_id' => $activity->item_id ) ) ) {

			// Group is not public.
			if ( 'public' != $group->status ) {

				// User is not a member of group.
				if ( !groups_is_user_member( bp_loggedin_user_id(), $group->id ) ) {
					$has_access = false;
				}
			}
		}
	}

	/**
	 * Filters the access permission for a single activity view.
	 *
	 * @since 1.2.0
	 *
	 * @param array $access Array holding the current $has_access value and current activity item instance.
	 */
	$has_access = apply_filters_ref_array( 'bp_activity_permalink_access', array( $has_access, &$activity ) );

	/**
	 * Fires before the loading of a single activity template file.
	 *
	 * @since 1.2.0
	 *
	 * @param BP_Activity_Activity $activity   Object representing the current activity item being displayed.
	 * @param bool                 $has_access Whether or not the current user has access to view activity.
	 */
	do_action( 'bp_activity_screen_single_activity_permalink', $activity, $has_access );

	// Access is specifically disallowed.
	if ( false === $has_access ) {

		// User feedback.
		bp_core_add_message( __( 'You do not have access to this activity.', 'buddypress' ), 'error' );

		// Redirect based on logged in status.
		if ( is_user_logged_in() ) {
			$url = bp_loggedin_user_domain();

		} else {
			$url = sprintf(
				site_url( 'wp-login.php?redirect_to=%s' ),
				urlencode( esc_url_raw( bp_activity_get_permalink( (int) bp_current_action() ) ) )
			);
		}

		bp_core_redirect( $url );
	}

	/**
	 * Filters the template to load for a single activity screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the activity template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_activity_template_profile_activity_permalink', 'members/single/activity/permalink' ) );
}
add_action( 'bp_screens', 'bp_activity_screen_single_activity_permalink' );

/**
 * Add activity notifications settings to the notifications settings page.
 *
 * @since 1.2.0
 *
 * @uses bp_get_user_meta()
 * @uses bp_core_get_username()
 * @uses do_action() To call the 'bp_activity_screen_notification_settings' hook.
 */
function bp_activity_screen_notification_settings() {

	if ( bp_activity_do_mentions() ) {
		if ( ! $mention = bp_get_user_meta( bp_displayed_user_id(), 'notification_activity_new_mention', true ) ) {
			$mention = 'yes';
		}
	}

	if ( ! $reply = bp_get_user_meta( bp_displayed_user_id(), 'notification_activity_new_reply', true ) ) {
		$reply = 'yes';
	}

	?>

	<table class="notification-settings" id="activity-notification-settings">
		<thead>
			<tr>
				<th class="icon">&nbsp;</th>
				<th class="title"><?php _e( 'Activity', 'buddypress' ) ?></th>
				<th class="yes"><?php _e( 'Yes', 'buddypress' ) ?></th>
				<th class="no"><?php _e( 'No', 'buddypress' )?></th>
			</tr>
		</thead>

		<tbody>
			<?php if ( bp_activity_do_mentions() ) : ?>
				<tr id="activity-notification-settings-mentions">
					<td>&nbsp;</td>
					<td><?php printf( __( 'A member mentions you in an update using "@%s"', 'buddypress' ), bp_core_get_username( bp_displayed_user_id() ) ) ?></td>
					<td class="yes"><input type="radio" name="notifications[notification_activity_new_mention]" id="notification-activity-new-mention-yes" value="yes" <?php checked( $mention, 'yes', true ) ?>/><label for="notification-activity-new-mention-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
					<td class="no"><input type="radio" name="notifications[notification_activity_new_mention]" id="notification-activity-new-mention-no" value="no" <?php checked( $mention, 'no', true ) ?>/><label for="notification-activity-new-mention-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
				</tr>
			<?php endif; ?>

			<tr id="activity-notification-settings-replies">
				<td>&nbsp;</td>
				<td><?php _e( "A member replies to an update or comment you've posted", 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_activity_new_reply]" id="notification-activity-new-reply-yes" value="yes" <?php checked( $reply, 'yes', true ) ?>/><label for="notification-activity-new-reply-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_activity_new_reply]" id="notification-activity-new-reply-no" value="no" <?php checked( $reply, 'no', true ) ?>/><label for="notification-activity-new-reply-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>

			<?php

			/**
			 * Fires inside the closing </tbody> tag for activity screen notification settings.
			 *
			 * @since 1.2.0
			 */
			do_action( 'bp_activity_screen_notification_settings' ) ?>
		</tbody>
	</table>

<?php
}
add_action( 'bp_notification_settings', 'bp_activity_screen_notification_settings', 1 );

/** Theme Compatibility *******************************************************/

/**
 * The main theme compat class for BuddyPress Activity.
 *
 * This class sets up the necessary theme compatibility actions to safely output
 * activity template parts to the_title and the_content areas of a theme.
 *
 * @since 1.7.0
 */
class BP_Activity_Theme_Compat {

	/**
	 * Set up the activity component theme compatibility.
	 *
	 * @since 1.7.0
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_activity' ) );
	}

	/**
	 * Set up the theme compatibility hooks, if we're looking at an activity page.
	 *
	 * @since 1.7.0
	 */
	public function is_activity() {

		// Bail if not looking at a group.
		if ( ! bp_is_activity_component() )
			return;

		// Activity Directory.
		if ( ! bp_displayed_user_id() && ! bp_current_action() ) {
			bp_update_is_directory( true, 'activity' );

			/** This action is documented in bp-activity/bp-activity-screens.php */
			do_action( 'bp_activity_screen_index' );

			add_filter( 'bp_get_buddypress_template',                array( $this, 'directory_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'directory_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'directory_content'    ) );

		// Single activity.
		} elseif ( bp_is_single_activity() ) {
			add_filter( 'bp_get_buddypress_template',                array( $this, 'single_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'single_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'single_dummy_content'    ) );
		}
	}

	/** Directory *************************************************************/

	/**
	 * Add template hierarchy to theme compat for the activity directory page.
	 *
	 * This is to mirror how WordPress has {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function directory_template_hierarchy( $templates ) {

		/**
		 * Filters the template hierarchy for the activity directory page.
		 *
		 * @since 1.8.0
		 *
		 * @param array $index-directory Array holding template names to be merged into template list.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_activity_directory', array(
			'activity/index-directory.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates().
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with directory data.
	 *
	 * @since 1.7.0
	 */
	public function directory_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => bp_get_directory_title( 'activity' ),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the groups index template part.
	 *
	 * @since 1.7.0
	 */
	public function directory_content() {
		return bp_buffer_template_part( 'activity/index', null, false );
	}

	/** Single ****************************************************************/

	/**
	 * Add custom template hierarchy to theme compat for activity permalink pages.
	 *
	 * This is to mirror how WordPress has {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function single_template_hierarchy( $templates ) {

		/**
		 * Filters the template hierarchy for the activity permalink pages.
		 *
		 * @since 1.8.0
		 *
		 * @param array $index Array holding template names to be merged into template list.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_activity_single_item', array(
			'activity/single/index.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates().
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with the displayed user's data.
	 *
	 * @since 1.7.0
	 */
	public function single_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => __( 'Activity', 'buddypress' ),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the members' activity permalink template part.
	 *
	 * @since 1.7.0
	 */
	public function single_dummy_content() {
		return bp_buffer_template_part( 'activity/single/home', null, false );
	}
}
new BP_Activity_Theme_Compat();
