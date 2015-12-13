<?php
/**
 * BuddyPress Forums Screen Functions.
 *
 * @package BuddyPress
 * @subpackage ForumsScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Load the Forums directory.
 */
function bp_forums_directory_forums_setup() {

	// Get BuddyPress once.
	$bp = buddypress();

	if ( bp_is_forums_component() && ( !bp_current_action() || ( 'tag' == bp_current_action() && bp_action_variables() ) ) && !bp_current_item() ) {
		if ( !bp_forums_has_directory() )
			return false;

		if ( !bp_forums_is_installed_correctly() ) {
			bp_core_add_message( __( 'The forums component has not been set up yet.', 'buddypress' ), 'error' );
			bp_core_redirect( bp_get_root_domain() );
		}

		bp_update_is_directory( true, 'forums' );

		/**
		 * Fires early in the initialization of bbPress-based areas of BuddyPress.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bbpress_init' );

		// Check to see if the user has posted a new topic from the forums page.
		if ( isset( $_POST['submit_topic'] ) && bp_is_active( 'forums' ) ) {
			check_admin_referer( 'bp_forums_new_topic' );

			$bp->groups->current_group = groups_get_group( array( 'group_id' => $_POST['topic_group_id'] ) );
			if ( !empty( $bp->groups->current_group->id ) ) {
				// Auto join this user if they are not yet a member of this group.
				if ( !bp_current_user_can( 'bp_moderate' ) && 'public' == $bp->groups->current_group->status && !groups_is_user_member( bp_loggedin_user_id(), $bp->groups->current_group->id ) )
					groups_join_group( $bp->groups->current_group->id );

				$error_message = '';

				$forum_id = groups_get_groupmeta( $bp->groups->current_group->id, 'forum_id' );
				if ( !empty( $forum_id ) ) {
					if ( empty( $_POST['topic_title'] ) )
						$error_message = __( 'Please provide a title for your forum topic.', 'buddypress' );
					else if ( empty( $_POST['topic_text'] ) )
						$error_message = __( 'Forum posts cannot be empty. Please enter some text.', 'buddypress' );

					if ( $error_message ) {
						bp_core_add_message( $error_message, 'error' );
						$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum';
					} else {
						if ( !$topic = groups_new_group_forum_topic( $_POST['topic_title'], $_POST['topic_text'], $_POST['topic_tags'], $forum_id ) ) {
							bp_core_add_message( __( 'There was an error when creating the topic', 'buddypress'), 'error' );
							$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum';
						} else {
							bp_core_add_message( __( 'The topic was created successfully', 'buddypress') );
							$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum/topic/' . $topic->topic_slug . '/';
						}
					}

					bp_core_redirect( $redirect );

				} else {
					bp_core_add_message( __( 'Please pick the group forum where you would like to post this topic.', 'buddypress' ), 'error' );
					bp_core_redirect( add_query_arg( 'new', '', bp_get_forums_directory_permalink() ) );
				}

			} else {
				bp_core_add_message( __( 'Please pick the group forum where you would like to post this topic.', 'buddypress' ), 'error' );
				bp_core_redirect( add_query_arg( 'new', '', bp_get_forums_directory_permalink() ) );
			}
		}

		/**
		 * Fires right before the loading of the forums directory screen template file.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_forums_directory_forums_setup' );

		/**
		 * Filters the template to load for the forums directory screen.
		 *
		 * @since 1.1.0
		 *
		 * @param string $template Path to the forums template to load.
		 */
		bp_core_load_template( apply_filters( 'bp_forums_template_directory_forums_setup', 'forums/index' ) );
	}
}
add_action( 'bp_screens', 'bp_forums_directory_forums_setup', 2 );

/**
 * Load the Topics Started screen.
 */
function bp_member_forums_screen_topics() {

	/**
	 * Fires right before the loading of the forums topics started screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_member_forums_screen_topics' );

	/**
	 * Filters the template to load for the forums topics started screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the forums topics started template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_member_forums_screen_topics', 'members/single/home' ) );
}

/**
 * Load the Replied To screen.
 */
function bp_member_forums_screen_replies() {

	/**
	 * Fires right before the loading of the forums replied to screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_member_forums_screen_replies' );

	/**
	 * Filters the template to load for the forums replied to screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the forums replied to template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_member_forums_screen_replies', 'members/single/home' ) );
}

/**
 * Load the template content for a user's Favorites forum tab.
 *
 * Note that this feature is not fully implemented at the moment.
 */
function bp_member_forums_screen_favorites() {

	/**
	 * Fires right before the loading of the forums favorites screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_member_forums_screen_favorites' );

	/**
	 * Filters the template to load for the forums favorites screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the forums favorites template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_member_forums_screen_favorites', 'members/single/home' ) );
}

/**
 * Load a single forum page.
 */
function bp_forums_screen_single_forum() {

	if ( !bp_is_forums_component() || !bp_is_current_action( 'forum' ) || !bp_action_variable( 0 ) )
		return false;

	/**
	 * Fires right before the loading of the forums single forum screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_forums_screen_single_forum' );

	/**
	 * Filters the template to load for the forums single forum screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the forums single forum template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_forums_screen_single_forum', 'forums/single/forum' ) );
}
add_action( 'bp_screens', 'bp_forums_screen_single_forum' );

/**
 * Load a single forum topic page.
 */
function bp_forums_screen_single_topic() {

	if ( !bp_is_forums_component() || !bp_is_current_action( 'topic' ) || !bp_action_variable( 0 ) )
		return false;

	/**
	 * Fires right before the loading of the forums single topic screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_forums_screen_single_topic' );

	/**
	 * Filters the template to load for the forums single topic screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the forums single topic template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_forums_screen_single_topic', 'forums/single/topic' ) );
}
add_action( 'bp_screens', 'bp_forums_screen_single_topic' );


/** Theme Compatibility *******************************************************/

/**
 * The main theme compat class for legacy BuddyPress forums.
 *
 * This class sets up the necessary theme compatibility actions to safely output
 * old forum template parts to the_title and the_content areas of a theme.
 *
 * @since 1.7.0
 */
class BP_Forum_Legacy_Theme_Compat {

	/**
	 * Set up theme compatibility for the legacy forums component.
	 *
	 * @since 1.7.0
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_legacy_forum' ) );
	}

	/**
	 * Are we looking at something that needs old forum theme compatibility?
	 *
	 * @since 1.7.0
	 */
	public function is_legacy_forum() {

		// Bail if not looking at a group.
		if ( ! bp_is_forums_component() )
			return;

		// Forum Directory.
		if ( ( ! bp_current_action() || ( 'tag' == bp_current_action() && bp_action_variables() ) ) && ! bp_current_item() ) {

			if ( ! bp_forums_has_directory() )
				return false;

			if ( ! bp_forums_is_installed_correctly() ) {
				bp_core_add_message( __( 'The forums component has not been set up yet.', 'buddypress' ), 'error' );
				bp_core_redirect( bp_get_root_domain() );
			}

			bp_update_is_directory( true, 'forums' );

			do_action( 'bp_forums_directory_forums_setup' );

			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'directory_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'directory_content'    ) );

		}

	}

	/** Directory *************************************************************/

	/**
	 * Update the global $post with directory data.
	 *
	 * @since 1.7.0
	 */
	public function directory_dummy_post() {

		// Title based on ability to create groups.
		if ( is_user_logged_in() ) {
			$title = __( 'Forums', 'buddypress' ) . '&nbsp;<a class="button show-hide-new bp-title-button" href="#new-topic" id="new-topic-button">' . __( 'New Topic', 'buddypress' ) . '</a>';
		} else {
			$title = __( 'Forums', 'buddypress' );
		}

		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => $title,
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
	 * Filter the_content with the old forum index template part.
	 *
	 * @since 1.7.0
	 */
	public function directory_content() {
		return bp_buffer_template_part( 'forums/index', null, false );
	}
}
new BP_Forum_Legacy_Theme_Compat();
