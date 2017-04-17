<?php
/**
 * BuddyPress Friends Template Functions.
 *
 * @package BuddyPress
 * @subpackage FriendsTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the friends component slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_friends_slug()
 */
function bp_friends_slug() {
	echo bp_get_friends_slug();
}
	/**
	 * Return the friends component slug.
	 *
	 * @since 1.5.0
	 */
	function bp_get_friends_slug() {

		/**
		 * Filters the friends component slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Friends component slug.
		 */
		return apply_filters( 'bp_get_friends_slug', buddypress()->friends->slug );
	}

/**
 * Output the friends component root slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_friends_root_slug()
 */
function bp_friends_root_slug() {
	echo bp_get_friends_root_slug();
}
	/**
	 * Return the friends component root slug.
	 *
	 * @since 1.5.0
	 */
	function bp_get_friends_root_slug() {

		/**
		 * Filters the friends component root slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Friends component root slug.
		 */
		return apply_filters( 'bp_get_friends_root_slug', buddypress()->friends->root_slug );
	}

/**
 * Output a block of random friends.
 *
 * No longer used in BuddyPress.
 *
 * @todo Deprecate
 */
function bp_friends_random_friends() {

	if ( !$friend_ids = wp_cache_get( 'friends_friend_ids_' . bp_displayed_user_id(), 'bp' ) ) {
		$friend_ids = BP_Friends_Friendship::get_random_friends( bp_displayed_user_id() );
		wp_cache_set( 'friends_friend_ids_' . bp_displayed_user_id(), $friend_ids, 'bp' );
	} ?>

	<div class="info-group">
		<h4><?php bp_word_or_name( __( "My Friends", 'buddypress' ), __( "%s's Friends", 'buddypress' ) ) ?>  (<?php echo BP_Friends_Friendship::total_friend_count( bp_displayed_user_id() ) ?>) <span><a href="<?php echo trailingslashit( bp_displayed_user_domain() . bp_get_friends_slug() ) ?>"><?php _e('See All', 'buddypress') ?></a></span></h4>

		<?php if ( $friend_ids ) { ?>

			<ul class="horiz-gallery">

			<?php for ( $i = 0, $count = count( $friend_ids ); $i < $count; ++$i ) { ?>

				<li>
					<a href="<?php echo bp_core_get_user_domain( $friend_ids[$i] ) ?>"><?php echo bp_core_fetch_avatar( array( 'item_id' => $friend_ids[$i], 'type' => 'thumb' ) ) ?></a>
					<h5><?php echo bp_core_get_userlink($friend_ids[$i]) ?></h5>
				</li>

			<?php } ?>

			</ul>

		<?php } else { ?>

			<div id="message" class="info">
				<p><?php bp_word_or_name( __( "You haven't added any friend connections yet.", 'buddypress' ), __( "%s hasn't created any friend connections yet.", 'buddypress' ) ) ?></p>
			</div>

		<?php } ?>

		<div class="clear"></div>
	</div>

<?php
}

/**
 * Pull up a group of random members, and display some profile data about them.
 *
 * This function is no longer used by BuddyPress core.
 *
 * @todo Deprecate
 *
 * @param int $total_members The number of members to retrieve.
 */
function bp_friends_random_members( $total_members = 5 ) {

	if ( !$user_ids = wp_cache_get( 'friends_random_users', 'bp' ) ) {
		$user_ids = BP_Core_User::get_users( 'random', $total_members );
		wp_cache_set( 'friends_random_users', $user_ids, 'bp' );
	}

	?>

	<?php if ( $user_ids['users'] ) { ?>

		<ul class="item-list" id="random-members-list">

		<?php for ( $i = 0, $count = count( $user_ids['users'] ); $i < $count; ++$i ) { ?>

			<li>
				<a href="<?php echo bp_core_get_user_domain( $user_ids['users'][$i]->id ) ?>"><?php echo bp_core_fetch_avatar( array( 'item_id' => $user_ids['users'][$i]->id, 'type' => 'thumb' ) ) ?></a>
				<h5><?php echo bp_core_get_userlink( $user_ids['users'][$i]->id ) ?></h5>

				<?php if ( bp_is_active( 'xprofile' ) ) { ?>

					<?php $random_data = xprofile_get_random_profile_data( $user_ids['users'][$i]->id, true ); ?>

					<div class="profile-data">
						<p class="field-name"><?php echo $random_data[0]->name ?></p>

						<?php echo $random_data[0]->value ?>

					</div>

				<?php } ?>

				<div class="action">

					<?php if ( bp_is_active( 'friends' ) ) { ?>

						<?php bp_add_friend_button( $user_ids['users'][$i]->id ) ?>

					<?php } ?>

				</div>
			</li>

		<?php } ?>

		</ul>

	<?php } else { ?>

		<div id="message" class="info">
			<p><?php _e( "There aren't enough site members to show a random sample just yet.", 'buddypress' ) ?></p>
		</div>

	<?php } ?>
<?php
}

/**
 * Display a Friends search form.
 *
 * No longer used in BuddyPress.
 *
 * @todo Deprecate
 */
function bp_friend_search_form() {

	$action = bp_displayed_user_domain() . bp_get_friends_slug() . '/my-friends/search/';
	$label  = __( 'Filter Friends', 'buddypress' ); ?>

		<form action="<?php echo $action ?>" id="friend-search-form" method="post">

			<label for="friend-search-box" id="friend-search-label"><?php echo $label ?></label>
			<input type="search" name="friend-search-box" id="friend-search-box" value="<?php echo $value ?>"<?php echo $disabled ?> />

			<?php wp_nonce_field( 'friends_search', '_wpnonce_friend_search' ) ?>

			<input type="hidden" name="initiator" id="initiator" value="<?php echo esc_attr( bp_displayed_user_id() ) ?>" />

		</form>

	<?php
}

/**
 * Output the "Add Friend" button in the member loop.
 *
 * @since 1.2.6
 */
function bp_member_add_friend_button() {
	bp_add_friend_button( bp_get_member_user_id() );
}
add_action( 'bp_directory_members_actions', 'bp_member_add_friend_button' );

/**
 * Output the friend count for the current member in the loop.
 */
function bp_member_total_friend_count() {
	echo bp_get_member_total_friend_count();
}
	/**
	 * Return the friend count for the current member in the loop.
	 *
	 * Return value is a string of the form "x friends".
	 *
	 * @return string A string of the form "x friends".
	 */
	function bp_get_member_total_friend_count() {
		global $members_template;

		if ( 1 == (int) $members_template->member->total_friend_count ) {

			/**
			 * Filters text used to denote total friend count.
			 *
			 * @since 1.2.0
			 *
			 * @param string $value String of the form "x friends".
			 * @param int    $value Total friend count for current member in the loop.
			 */
			return apply_filters( 'bp_get_member_total_friend_count', sprintf( __( '%d friend', 'buddypress' ), (int) $members_template->member->total_friend_count ) );
		} else {

			/** This filter is documented in bp-friends/bp-friends-template.php */
			return apply_filters( 'bp_get_member_total_friend_count', sprintf( __( '%d friends', 'buddypress' ), (int) $members_template->member->total_friend_count ) );
		}
	}

/**
 * Output the ID of the current user in the friend request loop.
 *
 * @see bp_get_potential_friend_id() for a description of arguments.
 *
 * @param int $user_id See {@link bp_get_potential_friend_id()}.
 */
function bp_potential_friend_id( $user_id = 0 ) {
	echo bp_get_potential_friend_id( $user_id );
}
	/**
	 * Return the ID of current user in the friend request loop.
	 *
	 * @global object $friends_template
	 *
	 * @param int $user_id Optional. If provided, the function will simply
	 *                     return this value.
	 * @return int ID of potential friend.
	 */
	function bp_get_potential_friend_id( $user_id = 0 ) {
		global $friends_template;

		if ( empty( $user_id ) && isset( $friends_template->friendship->friend ) )
			$user_id = $friends_template->friendship->friend->id;
		elseif ( empty( $user_id ) && !isset( $friends_template->friendship->friend ) )
			$user_id = bp_displayed_user_id();

		/**
		 * Filters the ID of current user in the friend request loop.
		 *
		 * @since 1.2.10
		 *
		 * @param int $user_id ID of current user in the friend request loop.
		 */
		return apply_filters( 'bp_get_potential_friend_id', (int) $user_id );
	}

/**
 * Check whether a given user is a friend of the logged-in user.
 *
 * Returns - 'is_friend', 'not_friends', 'pending'.
 *
 * @param int $user_id ID of the potential friend. Default: the value of
 *                     {@link bp_get_potential_friend_id()}.
 * @return string 'is_friend', 'not_friends', or 'pending'.
 */
function bp_is_friend( $user_id = 0 ) {

	if ( !is_user_logged_in() )
		return false;

	if ( empty( $user_id ) )
		$user_id = bp_get_potential_friend_id( $user_id );

	if ( bp_loggedin_user_id() == $user_id )
		return false;

	/**
	 * Filters the status of friendship between logged in user and given user.
	 *
	 * @since 1.2.10
	 *
	 * @param string $value String status of friendship. Possible values are 'is_friend', 'not_friends', 'pending'.
	 */
	return apply_filters( 'bp_is_friend', friends_check_friendship_status( bp_loggedin_user_id(), $user_id ), $user_id );
}

/**
 * Output the Add Friend button.
 *
 * @see bp_get_add_friend_button() for information on arguments.
 *
 * @param int      $potential_friend_id See {@link bp_get_add_friend_button()}.
 * @param int|bool $friend_status       See {@link bp_get_add_friend_button()}.
 */
function bp_add_friend_button( $potential_friend_id = 0, $friend_status = false ) {
	echo bp_get_add_friend_button( $potential_friend_id, $friend_status );
}
	/**
	 * Create the Add Friend button.
	 *
	 * @param int  $potential_friend_id ID of the user to whom the button
	 *                                  applies. Default: value of {@link bp_get_potential_friend_id()}.
	 * @param bool $friend_status       Not currently used.
	 * @return string HTML for the Add Friend button.
	 */
	function bp_get_add_friend_button( $potential_friend_id = 0, $friend_status = false ) {

		if ( empty( $potential_friend_id ) )
			$potential_friend_id = bp_get_potential_friend_id( $potential_friend_id );

		$is_friend = bp_is_friend( $potential_friend_id );

		if ( empty( $is_friend ) )
			return false;

		switch ( $is_friend ) {
			case 'pending' :
				$button = array(
					'id'                => 'pending',
					'component'         => 'friends',
					'must_be_logged_in' => true,
					'block_self'        => true,
					'wrapper_class'     => 'friendship-button pending_friend',
					'wrapper_id'        => 'friendship-button-' . $potential_friend_id,
					'link_href'         => wp_nonce_url( bp_loggedin_user_domain() . bp_get_friends_slug() . '/requests/cancel/' . $potential_friend_id . '/', 'friends_withdraw_friendship' ),
					'link_text'         => __( 'Cancel Friendship Request', 'buddypress' ),
					'link_title'        => __( 'Cancel Friendship Requested', 'buddypress' ),
					'link_id'           => 'friend-' . $potential_friend_id,
					'link_rel'          => 'remove',
					'link_class'        => 'friendship-button pending_friend requested'
				);
				break;

			case 'awaiting_response' :
				$button = array(
					'id'                => 'awaiting_response',
					'component'         => 'friends',
					'must_be_logged_in' => true,
					'block_self'        => true,
					'wrapper_class'     => 'friendship-button awaiting_response_friend',
					'wrapper_id'        => 'friendship-button-' . $potential_friend_id,
					'link_href'         => bp_loggedin_user_domain() . bp_get_friends_slug() . '/requests/',
					'link_text'         => __( 'Friendship Requested', 'buddypress' ),
					'link_title'        => __( 'Friendship Requested', 'buddypress' ),
					'link_id'           => 'friend-' . $potential_friend_id,
					'link_rel'          => 'remove',
					'link_class'        => 'friendship-button awaiting_response_friend requested'
				);
				break;

			case 'is_friend' :
				$button = array(
					'id'                => 'is_friend',
					'component'         => 'friends',
					'must_be_logged_in' => true,
					'block_self'        => false,
					'wrapper_class'     => 'friendship-button is_friend',
					'wrapper_id'        => 'friendship-button-' . $potential_friend_id,
					'link_href'         => wp_nonce_url( bp_loggedin_user_domain() . bp_get_friends_slug() . '/remove-friend/' . $potential_friend_id . '/', 'friends_remove_friend' ),
					'link_text'         => __( 'Cancel Friendship', 'buddypress' ),
					'link_title'        => __( 'Cancel Friendship', 'buddypress' ),
					'link_id'           => 'friend-' . $potential_friend_id,
					'link_rel'          => 'remove',
					'link_class'        => 'friendship-button is_friend remove'
				);
				break;

			default:
				$button = array(
					'id'                => 'not_friends',
					'component'         => 'friends',
					'must_be_logged_in' => true,
					'block_self'        => true,
					'wrapper_class'     => 'friendship-button not_friends',
					'wrapper_id'        => 'friendship-button-' . $potential_friend_id,
					'link_href'         => wp_nonce_url( bp_loggedin_user_domain() . bp_get_friends_slug() . '/add-friend/' . $potential_friend_id . '/', 'friends_add_friend' ),
					'link_text'         => __( 'Add Friend', 'buddypress' ),
					'link_title'        => __( 'Add Friend', 'buddypress' ),
					'link_id'           => 'friend-' . $potential_friend_id,
					'link_rel'          => 'add',
					'link_class'        => 'friendship-button not_friends add'
				);
				break;
		}

		/**
		 * Filters the HTML for the add friend button.
		 *
		 * @since 1.1.0
		 *
		 * @param string $button HTML markup for add friend button.
		 */
		return bp_get_button( apply_filters( 'bp_get_add_friend_button', $button ) );
	}

/**
 * Get a comma-separated list of IDs of a user's friends.
 *
 * @param int $user_id Optional. Default: the displayed user's ID, or the
 *                     logged-in user's ID.
 * @return string|bool A comma-separated list of friend IDs if any are found,
 *                     otherwise false.
 */
function bp_get_friend_ids( $user_id = 0 ) {

	if ( empty( $user_id ) )
		$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();

	$friend_ids = friends_get_friend_user_ids( $user_id );

	if ( empty( $friend_ids ) )
		return false;

	return implode( ',', friends_get_friend_user_ids( $user_id ) );
}

/**
 * Get a user's friendship requests.
 *
 * Note that we return a 0 if no pending requests are found. This is necessary
 * because of the structure of the $include parameter in bp_has_members().
 *
 * @param int $user_id ID of the user whose requests are being retrieved.
 *                     Defaults to displayed user.
 * @return array|int An array of user IDs if found, or a 0 if none are found.
 */
function bp_get_friendship_requests( $user_id = 0 ) {
	if ( !$user_id ) {
		$user_id = bp_displayed_user_id();
	}

	if ( !$user_id ) {
		return 0;
	}

	$requests = friends_get_friendship_request_user_ids( $user_id );

	if ( !empty( $requests ) ) {
		$requests = implode( ',', (array) $requests );
	} else {
		$requests = 0;
	}

	/**
	 * Filters the total pending friendship requests for a user.
	 *
	 * @since 1.2.0
	 *
	 * @param array|int An array of user IDs if found, or a 0 if none are found.
	 */
	return apply_filters( 'bp_get_friendship_requests', $requests );
}

/**
 * Output the ID of the friendship between the logged-in user and the current user in the loop.
 */
function bp_friend_friendship_id() {
	echo bp_get_friend_friendship_id();
}
	/**
	 * Return the ID of the friendship between the logged-in user and the current user in the loop.
	 *
	 * @return int ID of the friendship.
	 */
	function bp_get_friend_friendship_id() {
		global $members_template;

		if ( !$friendship_id = wp_cache_get( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id() ) ) {
			$friendship_id = friends_get_friendship_id( $members_template->member->id, bp_loggedin_user_id() );
			wp_cache_set( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id(), $friendship_id, 'bp' );
		}

		/**
		 * Filters the ID of the friendship between the logged in user and the current user in the loop.
		 *
		 * @since 1.2.0
		 *
		 * @param int $friendship_id ID of the friendship.
		 */
		return apply_filters( 'bp_get_friend_friendship_id', $friendship_id );
	}

/**
 * Output the URL for accepting the current friendship request in the loop.
 */
function bp_friend_accept_request_link() {
	echo bp_get_friend_accept_request_link();
}
	/**
	 * Return the URL for accepting the current friendship request in the loop.
	 *
	 * @return string accept-friendship URL.
	 */
	function bp_get_friend_accept_request_link() {
		global $members_template;

		if ( !$friendship_id = wp_cache_get( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id() ) ) {
			$friendship_id = friends_get_friendship_id( $members_template->member->id, bp_loggedin_user_id() );
			wp_cache_set( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id(), $friendship_id, 'bp' );
		}

		/**
		 * Filters the URL for accepting the current friendship request in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Accept-friendship URL.
		 */
		return apply_filters( 'bp_get_friend_accept_request_link', wp_nonce_url( bp_loggedin_user_domain() . bp_get_friends_slug() . '/requests/accept/' . $friendship_id, 'friends_accept_friendship' ) );
	}

/**
 * Output the URL for rejecting the current friendship request in the loop.
 */
function bp_friend_reject_request_link() {
	echo bp_get_friend_reject_request_link();
}
	/**
	 * Return the URL for rejecting the current friendship request in the loop.
	 *
	 * @return string reject-friendship URL.
	 */
	function bp_get_friend_reject_request_link() {
		global $members_template;

		if ( !$friendship_id = wp_cache_get( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id() ) ) {
			$friendship_id = friends_get_friendship_id( $members_template->member->id, bp_loggedin_user_id() );
			wp_cache_set( 'friendship_id_' . $members_template->member->id . '_' . bp_loggedin_user_id(), $friendship_id, 'bp' );
		}

		/**
		 * Filters the URL for rejecting the current friendship request in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Reject-friendship URL.
		 */
		return apply_filters( 'bp_get_friend_reject_request_link', wp_nonce_url( bp_loggedin_user_domain() . bp_get_friends_slug() . '/requests/reject/' . $friendship_id, 'friends_reject_friendship' ) );
	}

/**
 * Output the total friend count for a given user.
 *
 * @param int $user_id See {@link friends_get_total_friend_count()}.
 */
function bp_total_friend_count( $user_id = 0 ) {
	echo bp_get_total_friend_count( $user_id );
}
	/**
	 * Return the total friend count for a given user.
	 *
	 * @param int $user_id See {@link friends_get_total_friend_count()}.
	 * @return int Total friend count.
	 */
	function bp_get_total_friend_count( $user_id = 0 ) {

		/**
		 * Filters the total friend count for a given user.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Total friend count.
		 */
		return apply_filters( 'bp_get_total_friend_count', friends_get_total_friend_count( $user_id ) );
	}
	add_filter( 'bp_get_total_friend_count', 'bp_core_number_format' );

/**
 * Output the total friendship request count for a given user.
 *
 * @see bp_friend_get_total_requests_count() for description of arguments.
 *
 * @param int $user_id See {@link bp_friend_get_total_requests_count().
 */
function bp_friend_total_requests_count( $user_id = 0 ) {
	echo bp_friend_get_total_requests_count( $user_id );
}
	/**
	 * Return the total friendship request count for a given user.
	 *
	 * @param int $user_id ID of the user whose requests are being counted.
	 *                     Default: ID of the logged-in user.
	 * @return int Friend count.
	 */
	function bp_friend_get_total_requests_count( $user_id = 0 ) {
		if ( empty( $user_id ) )
			$user_id = bp_loggedin_user_id();

		/**
		 * Filters the total friendship request count for a given user.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Friendship request count.
		 */
		return apply_filters( 'bp_friend_get_total_requests_count', count( BP_Friends_Friendship::get_friend_user_ids( $user_id, true ) ) );
	}

/** Stats **********************************************************************/

/**
 * Display the number of friends in user's profile.
 *
 * @since 2.0.0
 *
 * @uses bp_friends_get_profile_stats() to get the stats.
 *
 * @param array|string $args before|after|user_id.
 */
function bp_friends_profile_stats( $args = '' ) {
	echo bp_friends_get_profile_stats( $args );
}
add_action( 'bp_members_admin_user_stats', 'bp_friends_profile_stats', 7, 1 );

/**
 * Return the number of friends in user's profile.
 *
 * @since 2.0.0
 *
 * @param array|string $args before|after|user_id.
 * @return string HTML for stats output.
 */
function bp_friends_get_profile_stats( $args = '' ) {

	// Parse the args.
	$r = bp_parse_args( $args, array(
		'before'  => '<li class="bp-friends-profile-stats">',
		'after'   => '</li>',
		'user_id' => bp_displayed_user_id(),
		'friends' => 0,
		'output'  => ''
	), 'friends_get_profile_stats' );

	// Allow completely overloaded output.
	if ( empty( $r['output'] ) ) {

		// Only proceed if a user ID was passed.
		if ( ! empty( $r['user_id'] ) ) {

			// Get the user's friends.
			if ( empty( $r['friends'] ) ) {
				$r['friends'] = absint( friends_get_total_friend_count( $r['user_id'] ) );
			}

			// If friends exist, show some formatted output.
			$r['output'] = $r['before'] . sprintf( _n( '%s friend', '%s friends', $r['friends'], 'buddypress' ), '<strong>' . $r['friends'] . '</strong>' ) . $r['after'];
		}
	}

	/**
	 * Filters the number of friends in user's profile.
	 *
	 * @since 2.0.0
	 *
	 * @param string $value Formatted string displaying total friends count.
	 * @param array  $r     Array of arguments for string formatting and output.
	 */
	return apply_filters( 'bp_friends_get_profile_stats', $r['output'], $r );
}
