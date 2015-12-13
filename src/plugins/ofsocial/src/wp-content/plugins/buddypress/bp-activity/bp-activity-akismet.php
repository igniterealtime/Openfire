<?php
/**
 * Akismet support for BuddyPress' Activity Stream.
 *
 * @package BuddyPress
 * @subpackage ActivityAkismet
 * @since 1.6.0
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Akismet support for the Activity component.
 *
 * @since 1.6.0
 * @since 2.3.0 We only support Akismet 3+.
 */
class BP_Akismet {

	/**
	 * The activity last marked as spam.
	 *
	 * @since 1.6.0
	 *
	 * @var BP_Activity_Activity
	 */
	protected $last_activity = null;

	/**
	 * Constructor.
	 *
	 * @since 1.6.0
	 */
	public function __construct() {
		$this->setup_actions();
	}

	/**
	 * Hook Akismet into the activity stream.
	 *
	 * @since 1.6.0
	 */
	protected function setup_actions() {
		// Add nonces to activity stream lists.
		add_action( 'bp_after_activity_post_form', array( $this, 'add_activity_stream_nonce' ) );
		add_action( 'bp_activity_entry_comments',  array( $this, 'add_activity_stream_nonce' ) );

		// Add a "mark as spam" button to individual activity items.
		add_action( 'bp_activity_entry_meta',      array( $this, 'add_activity_spam_button' ) );
		add_action( 'bp_activity_comment_options', array( $this, 'add_activity_comment_spam_button' ) );

		// Check activity for spam.
		add_action( 'bp_activity_before_save',     array( $this, 'check_activity' ), 4, 1 );

		// Tidy up member's latest (activity) update.
		add_action( 'bp_activity_posted_update',   array( $this, 'check_member_activity_update' ), 1, 3 );

		// Hooks to extend Activity core spam/ham functions for Akismet.
		add_action( 'bp_activity_mark_as_spam',    array( $this, 'mark_as_spam' ), 10, 2 );
		add_action( 'bp_activity_mark_as_ham',     array( $this, 'mark_as_ham' ),  10, 2 );

		// Hook into the Activity wp-admin screen.
		add_action( 'bp_activity_admin_comment_row_actions', array( $this, 'comment_row_action' ), 10, 2 );
		add_action( 'bp_activity_admin_load',                array( $this, 'add_history_metabox' ) );
	}

	/**
	 * Add a history item to the hover links in an activity's row.
	 *
	 * This function lifted with love from the Akismet WordPress plugin's
	 * akismet_comment_row_action() function. Thanks!
	 *
	 * @since 1.6.0
	 *
	 * @param array $actions  The hover links.
	 * @param array $activity The activity for the current row being processed.
	 * @return array The hover links.
	 */
	function comment_row_action( $actions, $activity ) {
		$akismet_result = bp_activity_get_meta( $activity['id'], '_bp_akismet_result' );
		$user_result    = bp_activity_get_meta( $activity['id'], '_bp_akismet_user_result' );
		$desc           = '';

		if ( !$user_result || $user_result == $akismet_result ) {
			// Show the original Akismet result if the user hasn't overridden it, or if their decision was the same.
			if ( 'true' == $akismet_result && $activity['is_spam'] )
				$desc = __( 'Flagged as spam by Akismet', 'buddypress' );

			elseif ( 'false' == $akismet_result && !$activity['is_spam'] )
				$desc = __( 'Cleared by Akismet', 'buddypress' );

		} else {
			$who = bp_activity_get_meta( $activity['id'], '_bp_akismet_user' );

			if ( 'true' == $user_result )
				$desc = sprintf( __( 'Flagged as spam by %s', 'buddypress' ), $who );
			else
				$desc = sprintf( __( 'Un-spammed by %s', 'buddypress' ), $who );
		}

		// Add a History item to the hover links, just after Edit.
		if ( $akismet_result ) {
			$b = array();
			foreach ( $actions as $k => $item ) {
				$b[ $k ] = $item;
				if ( $k == 'edit' )
					$b['history'] = '<a href="' . esc_url( bp_get_admin_url( 'admin.php?page=bp-activity&amp;action=edit&aid=' . $activity['id'] ) ) . '#bp_activity_history"> '. __( 'History', 'buddypress' ) . '</a>';
			}

			$actions = $b;
		}

		if ( $desc )
			echo '<span class="akismet-status"><a href="' . esc_url( bp_get_admin_url( 'admin.php?page=bp-activity&amp;action=edit&aid=' . $activity['id'] ) ) . '#bp_activity_history">' . htmlspecialchars( $desc ) . '</a></span>';

		/**
		 * Filters the list of actions for the current activity's row.
		 *
		 * @since 1.6.0
		 *
		 * @param array $actions Array of available actions for the current activity item's row.
		 */
		return apply_filters( 'bp_akismet_comment_row_action', $actions );
	}

	/**
	 * Generate nonces for activity forms.
	 *
	 * These nonces appear in the member profile status form, as well as in
	 * the reply form of each activity item. The nonces are, in turn, used
	 * by Akismet to help detect spam activity.
	 *
	 * @since 1.6.0
	 *
	 * @see https://plugins.trac.wordpress.org/ticket/1232
	 */
	public function add_activity_stream_nonce() {
		$form_id = '_bp_as_nonce';
		$value   = '_bp_as_nonce_' . bp_loggedin_user_id();

		// If we're in the activity stream loop, we can use the current item's ID to make the nonce unique.
		if ( 'bp_activity_entry_comments' == current_filter() ) {
			$form_id .= '_' . bp_get_activity_id();
			$value   .= '_' . bp_get_activity_id();
		}

		wp_nonce_field( $value, $form_id, false );
	}

	/**
	 * Clean up the bp_latest_update usermeta in case of spamming.
	 *
	 * Run just after an update is posted, this method check to see whether
	 * the newly created update has been marked as spam by Akismet. If so,
	 * the cached update is cleared from the user's 'bp_latest_update'
	 * usermeta, ensuring that it won't appear in the member header and
	 * elsewhere in the theme.
	 *
	 * This can't be done in BP_Akismet::check_activity() due to the
	 * default AJAX implementation; see bp_dtheme_post_update().
	 *
	 * @since 1.6.0
	 *
	 * @see bp_dtheme_post_update()
	 *
	 * @param string $content     Activity update text.
	 * @param int    $user_id     User ID.
	 * @param int    $activity_id Activity ID.
	 */
	public function check_member_activity_update( $content, $user_id, $activity_id ) {
		// By default, only handle activity updates and activity comments.
		if ( empty( $this->last_activity ) || !in_array( $this->last_activity->type, BP_Akismet::get_activity_types() ) )
			return;

		// Was this $activity_id just marked as spam? If not, bail out.
		if ( !$this->last_activity->id || $activity_id != $this->last_activity->id || 'false' == $this->last_activity->akismet_submission['bp_as_result'] )
			return;

		// It was, so delete the member's latest activity update.
		bp_delete_user_meta( $user_id, 'bp_latest_update' );
	}

	/**
	 * Adds a "mark as spam" button to each activity item for site admins.
	 *
	 * This function is intended to be used inside the activity stream loop.
	 *
	 * @since 1.6.0
	 */
	public function add_activity_spam_button() {
		if ( !bp_activity_user_can_mark_spam() )
			return;

		// By default, only handle activity updates and activity comments.
		if ( !in_array( bp_get_activity_type(), BP_Akismet::get_activity_types() ) )
			return;

		bp_button(
			array(
				'block_self' => false,
				'component'  => 'activity',
				'id'         => 'activity_make_spam_' . bp_get_activity_id(),
				'link_class' => 'bp-secondary-action spam-activity confirm button item-button',
				'link_href'  => wp_nonce_url( bp_get_root_domain() . '/' . bp_get_activity_slug() . '/spam/' . bp_get_activity_id() . '/', 'bp_activity_akismet_spam_' . bp_get_activity_id() ),
				'link_text'  => __( 'Spam', 'buddypress' ),
				'wrapper'    => false,
			)
		);
	}

	/**
	 * Adds a "mark as spam" button to each activity COMMENT item for site admins.
	 *
	 * This function is intended to be used inside the activity stream loop.
	 *
	 * @since 1.6.0
	 */
	public function add_activity_comment_spam_button() {
		if ( !bp_activity_user_can_mark_spam() )
			return;

		// By default, only handle activity updates and activity comments.
		$current_comment = bp_activity_current_comment();
		if ( empty( $current_comment ) || !in_array( $current_comment->type, BP_Akismet::get_activity_types() ) )
			return;

		bp_button(
			array(
				'block_self' => false,
				'component'  => 'activity',
				'id'         => 'activity_make_spam_' . bp_get_activity_comment_id(),
				'link_class' => 'bp-secondary-action spam-activity-comment confirm',
				'link_href'  => wp_nonce_url( bp_get_root_domain() . '/' . bp_get_activity_slug() . '/spam/' . bp_get_activity_comment_id() . '/?cid=' . bp_get_activity_comment_id(), 'bp_activity_akismet_spam_' . bp_get_activity_comment_id() ),
				'link_text'  => __( 'Spam', 'buddypress' ),
				'wrapper'    => false,
			)
		);
	}

	/**
	 * Get a filterable list of activity types that Akismet should automatically check for spam.
	 *
	 * @since 1.6.0
	 *
	 * @static
	 *
	 * @return array $value List of activity types.
	 */
	public static function get_activity_types() {

		/**
		 * Filters the list of activity types that Akismet should automatically check for spam.
		 *
		 * @since 1.6.0
		 *
		 * @param array Array of default activity types for Akismet to check.
		 */
		return apply_filters( 'bp_akismet_get_activity_types', array( 'activity_comment', 'activity_update' ) );
	}

	/**
	 * Mark activity item as spam.
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity Activity item being spammed.
	 * @param string               $source   Either "by_a_person" (e.g. a person has
	 *                                       manually marked the activity as spam) or
	 *                                       "by_akismet" (automatically spammed).
	 */
	public function mark_as_spam( $activity, $source ) {
		// Record this item so we can do some tidyup in BP_Akismet::check_member_activity_update().
		$this->last_activity = $activity;

		/**
		 * Fires after marking an activity item has been marked as spam.
		 *
		 * @since 1.6.0
		 *
		 * @param BP_Activity_Activity $activity Activity object being marked as spam.
		 * @param string               $source   Source of the whom marked as spam.
		 *                                       Either "by_a_person" (e.g. a person has
		 *                                       manually marked the activity as spam)
		 *                                       or "by_akismet".
		 */
		do_action( 'bp_activity_akismet_mark_as_spam', $activity, $source );
	}

	/**
	 * Mark activity item as ham.
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity Activity item being hammed.
	 * @param string               $source   Either "by_a_person" (e.g. a person has
	 *                                       manually marked the activity as ham) or
	 *                                       "by_akismet" (automatically hammed).
	 */
	public function mark_as_ham( $activity, $source ) {
		// If the activity was, originally, automatically marked as spam by Akismet, run the @mentions filter as it would have been skipped.
		if ( 'true' == bp_activity_get_meta( $activity->id, '_bp_akismet_result' ) && !bp_activity_get_meta( $activity->id, '_bp_akismet_user_result' ) )
			$activity->content = bp_activity_at_name_filter( $activity->content, $activity->id );

		/**
		 * Fires after marking an activity item has been marked as ham.
		 *
		 * @since 1.6.0
		 *
		 * @param BP_Activity_Activity $activity Activity object being marked as ham.
		 * @param string               $source   Source of the whom marked as ham.
		 *                                       Either "by_a_person" (e.g. a person has
		 *                                       manually marked the activity as ham) or
		 *                                       "by_akismet" (automatically hammed).
		 */
		do_action( 'bp_activity_akismet_mark_as_ham', $activity, $source );
	}

	/**
	 * Build a data package for the Akismet service to inspect.
	 *
	 * @since 1.6.0
	 *
	 * @see http://akismet.com/development/api/#comment-check
	 * @static
	 *
	 * @param BP_Activity_Activity $activity Activity item data.
	 * @return array $activity_data
	 */
	public static function build_akismet_data_package( $activity ) {
		$userdata = get_userdata( $activity->user_id );

		$activity_data                          = array();
		$activity_data['akismet_comment_nonce'] = 'inactive';
		$activity_data['comment_author']        = $userdata->display_name;
		$activity_data['comment_author_email']  = $userdata->user_email;
		$activity_data['comment_author_url']    = bp_core_get_userlink( $userdata->ID, false, true);
		$activity_data['comment_content']       = $activity->content;
		$activity_data['comment_type']          = $activity->type;
		$activity_data['permalink']             = bp_activity_get_permalink( $activity->id, $activity );
		$activity_data['user_ID']               = $userdata->ID;
		$activity_data['user_role']             = Akismet::get_user_roles( $userdata->ID );

		/**
		 * Get the nonce if the new activity was submitted through the "what's up, Paul?" form.
		 * This helps Akismet ensure that the update was a valid form submission.
		 */
		if ( !empty( $_POST['_bp_as_nonce'] ) )
			$activity_data['akismet_comment_nonce'] = wp_verify_nonce( $_POST['_bp_as_nonce'], "_bp_as_nonce_{$userdata->ID}" ) ? 'passed' : 'failed';

		/**
		 * If the new activity was a reply to an existing item, check the nonce with the activity parent ID.
		 * This helps Akismet ensure that the update was a valid form submission.
		 */
		elseif ( !empty( $activity->secondary_item_id ) && !empty( $_POST['_bp_as_nonce_' . $activity->secondary_item_id] ) )
			$activity_data['akismet_comment_nonce'] = wp_verify_nonce( $_POST["_bp_as_nonce_{$activity->secondary_item_id}"], "_bp_as_nonce_{$userdata->ID}_{$activity->secondary_item_id}" ) ? 'passed' : 'failed';

		/**
		 * Filters activity data before being sent to Akismet to inspect.
		 *
		 * @since 1.6.0
		 *
		 * @param array                $activity_data Array of activity data for Akismet to inspect.
		 * @param BP_Activity_Activity $activity      Activity item data.
		 */
		return apply_filters( 'bp_akismet_build_akismet_data_package', $activity_data, $activity );
	}

	/**
	 * Check if the activity item is spam or ham.
	 *
	 * @since 1.6.0
	 *
	 * @see http://akismet.com/development/api/
	 * @todo Spam counter?
	 * @todo Auto-delete old spam?
	 *
	 * @param BP_Activity_Activity $activity The activity item to check.
	 */
	public function check_activity( $activity ) {
		// By default, only handle activity updates and activity comments.
		if ( !in_array( $activity->type, BP_Akismet::get_activity_types() ) )
			return;

		// Make sure last_activity is clear to avoid any confusion.
		$this->last_activity = null;

		// Build data package for Akismet.
		$activity_data = BP_Akismet::build_akismet_data_package( $activity );

		// Check with Akismet to see if this is spam.
		$activity_data = $this->send_akismet_request( $activity_data, 'check', 'spam' );

		// Record this item.
		$this->last_activity = $activity;

		// Store a copy of the data that was submitted to Akismet.
		$this->last_activity->akismet_submission = $activity_data;

		// Spam.
		if ( 'true' == $activity_data['bp_as_result'] ) {
			/**
			 * Fires after an activity item has been proven to be spam, but before officially being marked as spam.
			 *
			 * @since 1.6.0
			 *
			 * @param BP_Activity_Activity $activity      The activity item proven to be spam.
			 * @param array                $activity_data Array of activity data for item including
			 *                                            Akismet check results data.
			 */
			do_action_ref_array( 'bp_activity_akismet_spam_caught', array( &$activity, $activity_data ) );

			// Mark as spam.
			bp_activity_mark_as_spam( $activity, 'by_akismet' );
		}

		// Update activity meta after a spam check.
		add_action( 'bp_activity_after_save', array( $this, 'update_activity_akismet_meta' ), 1, 1 );
	}

	/**
	 * Update activity meta after a manual spam change (user-initiated).
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity The activity to check.
	 */
	public function update_activity_spam_meta( $activity ) {
		// By default, only handle activity updates and activity comments.
		if ( !in_array( $activity->type, BP_Akismet::get_activity_types() ) )
			return;

		$this->update_activity_history( $activity->id, sprintf( __( '%s reported this activity as spam', 'buddypress' ), bp_get_loggedin_user_username() ), 'report-spam' );
		bp_activity_update_meta( $activity->id, '_bp_akismet_user_result', 'true' );
		bp_activity_update_meta( $activity->id, '_bp_akismet_user', bp_get_loggedin_user_username() );
	}

	/**
	 * Update activity meta after a manual ham change (user-initiated).
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity The activity to check.
	 */
	public function update_activity_ham_meta( $activity ) {
		// By default, only handle activity updates and activity comments.
		if ( !in_array( $activity->type, BP_Akismet::get_activity_types() ) )
			return;

		$this->update_activity_history( $activity->id, sprintf( __( '%s reported this activity as not spam', 'buddypress' ), bp_get_loggedin_user_username() ), 'report-ham' );
		bp_activity_update_meta( $activity->id, '_bp_akismet_user_result', 'false' );
		bp_activity_update_meta( $activity->id, '_bp_akismet_user', bp_get_loggedin_user_username() );
	}

	/**
	 * Update activity meta after an automatic spam check (not user-initiated).
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity The activity to check.
	 */
	public function update_activity_akismet_meta( $activity ) {
		// Check we're dealing with what was last updated by Akismet.
		if ( empty( $this->last_activity ) || !empty( $this->last_activity ) && $activity->id != $this->last_activity->id )
			return;

		// By default, only handle activity updates and activity comments.
		if ( !in_array( $this->last_activity->type, BP_Akismet::get_activity_types() ) )
			return;

		// Spam.
		if ( 'true' == $this->last_activity->akismet_submission['bp_as_result'] ) {
			bp_activity_update_meta( $activity->id, '_bp_akismet_result', 'true' );
			$this->update_activity_history( $activity->id, __( 'Akismet caught this item as spam', 'buddypress' ), 'check-spam' );

		// Not spam.
		} elseif ( 'false' == $this->last_activity->akismet_submission['bp_as_result'] ) {
			bp_activity_update_meta( $activity->id, '_bp_akismet_result', 'false' );
			$this->update_activity_history( $activity->id, __( 'Akismet cleared this item', 'buddypress' ), 'check-ham' );

		// Uh oh, something's gone horribly wrong. Unexpected result.
		} else {
			bp_activity_update_meta( $activity->id, '_bp_akismet_error', bp_core_current_time() );
			$this->update_activity_history( $activity->id, sprintf( __( 'Akismet was unable to check this item (response: %s), will automatically retry again later.', 'buddypress' ), $this->last_activity->akismet_submission['bp_as_result'] ), 'check-error' );
		}

		// Record the original data which was submitted to Akismet for checking.
		bp_activity_update_meta( $activity->id, '_bp_akismet_submission', $this->last_activity->akismet_submission );
	}

	/**
	 * Contact Akismet to check if this is spam or ham.
	 *
	 * Props to WordPress core Akismet plugin for a lot of this.
	 *
	 * @since 1.6.0
	 *
	 * @param array  $activity_data Packet of information to submit to Akismet.
	 * @param string $check         "check" or "submit".
	 * @param string $spam          "spam" or "ham".
	 * @return array $activity_data Activity data, with Akismet data added.
	 */
	public function send_akismet_request( $activity_data, $check = 'check', $spam = 'spam' ) {
		$query_string = $path = '';

		$activity_data['blog']         = bp_get_option( 'home' );
		$activity_data['blog_charset'] = bp_get_option( 'blog_charset' );
		$activity_data['blog_lang']    = get_locale();
		$activity_data['referrer']     = $_SERVER['HTTP_REFERER'];
		$activity_data['user_agent']   = bp_core_current_user_ua();
		$activity_data['user_ip']      = bp_core_current_user_ip();

		if ( Akismet::is_test_mode() )
			$activity_data['is_test'] = 'true';

		// Loop through _POST args and rekey strings.
		foreach ( $_POST as $key => $value )
			if ( is_string( $value ) && 'cookie' != $key )
				$activity_data['POST_' . $key] = $value;

		// Keys to ignore.
		$ignore = array( 'HTTP_COOKIE', 'HTTP_COOKIE2', 'PHP_AUTH_PW' );

		// Loop through _SERVER args and remove whitelisted keys.
		foreach ( $_SERVER as $key => $value ) {

			// Key should not be ignored.
			if ( !in_array( $key, $ignore ) && is_string( $value ) ) {
				$activity_data[$key] = $value;

			// Key should be ignored.
			} else {
				$activity_data[$key] = '';
			}
		}

		foreach ( $activity_data as $key => $data )
			$query_string .= $key . '=' . urlencode( stripslashes( $data ) ) . '&';

		if ( 'check' == $check )
			$path = 'comment-check';
		elseif ( 'submit' == $check )
			$path = 'submit-' . $spam;

		// Send to Akismet.
		add_filter( 'akismet_ua', array( $this, 'buddypress_ua' ) );
		$response = Akismet::http_post( $query_string, $path );
		remove_filter( 'akismet_ua', array( $this, 'buddypress_ua' ) );

		// Get the response.
		if ( ! empty( $response[1] ) && ! is_wp_error( $response[1] ) )
			$activity_data['bp_as_result'] = $response[1];
		else
			$activity_data['bp_as_result'] = false;

		// Perform a daily tidy up.
		if ( ! wp_next_scheduled( 'bp_activity_akismet_delete_old_metadata' ) )
			wp_schedule_event( time(), 'daily', 'bp_activity_akismet_delete_old_metadata' );

		return $activity_data;
	}

	/**
	 * Filters user agent when sending to Akismet to add BuddyPress info.
	 *
	 * @since 1.6.0
	 *
	 * @param string $user_agent User agent string, as generated by Akismet.
	 * @return string $user_agent Modified user agent string.
	 */
	public function buddypress_ua( $user_agent ) {
		$user_agent = 'BuddyPress/' . bp_get_version() . ' | Akismet/'. constant( 'AKISMET_VERSION' );
		return $user_agent;
	}

	/**
	 * Adds a "History" meta box to the activity edit screen.
	 *
	 * @since 1.6.0
	 *
	 * @param string $screen_action The type of screen that has been requested.
	 */
	function add_history_metabox( $screen_action ) {
		// Only proceed if we're on the edit screen.
		if ( 'edit' != $screen_action )
			return;

		// Display meta box with a low priority (low position on screen by default).
		add_meta_box( 'bp_activity_history',  __( 'Activity History', 'buddypress' ), array( $this, 'history_metabox' ), get_current_screen()->id, 'normal', 'low' );
	}

	/**
	 * History meta box for the Activity admin edit screen.
	 *
	 * @since 1.6.0
	 *
	 * @see https://buddypress.trac.wordpress.org/ticket/3907
	 * @todo Update activity meta to allow >1 record with the same key (iterate through $history).
	 *
	 * @param object $item Activity item.
	 */
	function history_metabox( $item ) {
		$history = BP_Akismet::get_activity_history( $item->id );

		if ( empty( $history ) )
			return;

		echo '<div class="akismet-history"><div>';
		printf( _x( '%1$s &mdash; %2$s', 'x hours ago - akismet cleared this item', 'buddypress' ), '<span>' . bp_core_time_since( $history[2] ) . '</span>', esc_html( $history[1] ) );
		echo '</div></div>';
	}

	/**
	 * Update an activity item's Akismet history.
	 *
	 * @since 1.6.0
	 *
	 * @param int    $activity_id Activity item ID.
	 * @param string $message     Human-readable description of what's changed.
	 * @param string $event       The type of check we were carrying out.
	 */
	public function update_activity_history( $activity_id = 0, $message = '', $event = '' ) {
		$event = array(
			'event'   => $event,
			'message' => $message,
			'time'    => Akismet::_get_microtime(),
			'user'    => bp_loggedin_user_id(),
		);

		// Save the history data.
		bp_activity_update_meta( $activity_id, '_bp_akismet_history', $event );
	}

	/**
	 * Get an activity item's Akismet history.
	 *
	 * @since 1.6.0
	 *
	 * @param int $activity_id Activity item ID.
	 * @return array The activity item's Akismet history.
	 */
	public function get_activity_history( $activity_id = 0 ) {
		$history = bp_activity_get_meta( $activity_id, '_bp_akismet_history' );
		if ( $history === false )
			$history = array();

		// Sort it by the time recorded.
		usort( $history, 'akismet_cmp_time' );

		return $history;
	}
}

/**
 * Delete old spam activity meta data.
 *
 * This is done as a clean-up mechanism, as _bp_akismet_submission meta can
 * grow to be quite large.
 *
 * @since 1.6.0
 *
 * @global wpdb $wpdb WordPress database object.
 */
function bp_activity_akismet_delete_old_metadata() {
	global $wpdb;

	$bp = buddypress();

	/**
	 * Filters the threshold for how many days old Akismet metadata needs to be before being automatically deleted.
	 *
	 * @since 1.6.0
	 *
	 * @param integer 15 How many days old metadata needs to be.
	 */
	$interval = apply_filters( 'bp_activity_akismet_delete_meta_interval', 15 );

	// Enforce a minimum of 1 day.
	$interval = max( 1, absint( $interval ) );

	// _bp_akismet_submission meta values are large, so expire them after $interval days regardless of the activity status
	$sql          = $wpdb->prepare( "SELECT a.id FROM {$bp->activity->table_name} a LEFT JOIN {$bp->activity->table_name_meta} m ON a.id = m.activity_id WHERE m.meta_key = %s AND DATE_SUB(%s, INTERVAL {$interval} DAY) > a.date_recorded LIMIT 10000", '_bp_akismet_submission', current_time( 'mysql', 1 ) );
	$activity_ids = $wpdb->get_col( $sql );

	if ( ! empty( $activity_ids ) ) {
		foreach ( $activity_ids as $activity_id )
			bp_activity_delete_meta( $activity_id, '_bp_akismet_submission' );
	}
}
add_action( 'bp_activity_akismet_delete_old_metadata', 'bp_activity_akismet_delete_old_metadata' );
