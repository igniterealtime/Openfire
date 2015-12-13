<?php
/**
 * BuddyPress Activity Template Functions.
 *
 * @package BuddyPress
 * @subpackage ActivityTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the activity component slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_slug()
 */
function bp_activity_slug() {
	echo bp_get_activity_slug();
}
	/**
	 * Return the activity component slug.
	 *
	 * @since 1.5.0
	 *
	 * @uses apply_filters() To call the 'bp_get_activity_slug' hook.
	 *
	 * @return string The activity component slug.
	 */
	function bp_get_activity_slug() {

		/**
		 * Filters the activity component slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $slug Activity component slug.
		 */
		return apply_filters( 'bp_get_activity_slug', buddypress()->activity->slug );
	}

/**
 * Output the activity component root slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_root_slug()
 */
function bp_activity_root_slug() {
	echo bp_get_activity_root_slug();
}
	/**
	 * Return the activity component root slug.
	 *
	 * @since 1.5.0
	 *
	 * @uses apply_filters() To call the 'bp_get_activity_root_slug' hook.
	 *
	 * @return string The activity component root slug.
	 */
	function bp_get_activity_root_slug() {

		/**
		 * Filters the activity component root slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $root_slug Activity component root slug.
		 */
		return apply_filters( 'bp_get_activity_root_slug', buddypress()->activity->root_slug );
	}

/**
 * Output activity directory permalink.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_directory_permalink()
 */
function bp_activity_directory_permalink() {
	echo esc_url( bp_get_activity_directory_permalink() );
}
	/**
	 * Return activity directory permalink.
	 *
	 * @since 1.5.0
	 *
	 * @uses trailingslashit()
	 * @uses bp_get_root_domain()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_directory_permalink' hook.
	 *
	 * @return string Activity directory permalink.
	 */
	function bp_get_activity_directory_permalink() {

		/**
		 * Filters the activity directory permalink.
		 *
		 * @since 1.5.0
		 *
		 * @param string $url Permalink url for the activity directory.
		 */
		return apply_filters( 'bp_get_activity_directory_permalink', trailingslashit( bp_get_root_domain() . '/' . bp_get_activity_root_slug() ) );
	}

/**
 * The main activity template loop class.
 *
 * This is responsible for loading a group of activity items and displaying them.
 *
 * @since 1.0.0
 */
class BP_Activity_Template {

	/**
	 * The loop iterator.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $current_activity = -1;

	/**
	 * The activity count.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $activity_count;

	/**
	 * The total activity count.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $total_activity_count;

	/**
	 * Array of activities located by the query.
	 *
	 * @since 1.5.0
	 * @var array
	 */
	public $activities;

	/**
	 * The activity object currently being iterated on.
	 *
	 * @since 1.5.0
	 * @var object
	 */
	public $activity;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @since 1.5.0
	 * @var bool
	 */
	public $in_the_loop;

	/**
	 * URL parameter key for activity pagination. Default: 'acpage'.
	 *
	 * @since 2.1.0
	 * @var string
	 */
	public $pag_arg;

	/**
	 * The page number being requested.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $pag_page;

	/**
	 * The number of items being requested per page.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $pag_num;

	/**
	 * An HTML string containing pagination links.
	 *
	 * @since 1.5.0
	 * @var string
	 */
	public $pag_links;

	/**
	 * The displayed user's full name.
	 *
	 * @since 1.5.0
	 * @var string
	 */
	public $full_name;

	/**
	 * Constructor method.
	 *
	 * The arguments passed to this class constructor are of the same
	 * format as {@link BP_Activity_Activity::get()}.
	 *
	 * @see BP_Activity_Activity::get() for a description of the argument
	 *      structure, as well as default values.
	 *
	 * @param array $args {
	 *     Array of arguments. Supports all arguments from
	 *     BP_Activity_Activity::get(), as well as 'page_arg' and
	 *     'include'. Default values for 'per_page' and 'display_comments'
	 *     differ from the originating function, and are described below.
	 *     @type string      $page_arg         The string used as a query parameter in
	 *                                         pagination links. Default: 'acpage'.
	 *     @type array|bool  $include          Pass an array of activity IDs to
	 *                                         retrieve only those items, or false to noop the 'include'
	 *                                         parameter. 'include' differs from 'in' in that 'in' forms
	 *                                         an IN clause that works in conjunction with other filters
	 *                                         passed to the function, while 'include' is interpreted as
	 *                                         an exact list of items to retrieve, which skips all other
	 *                                         filter-related parameters. Default: false.
	 *     @type int|bool    $per_page         Default: 20.
	 *     @type string|bool $display_comments Default: 'threaded'.
	 * }
	 */
	public function __construct( $args ) {
		$bp = buddypress();

		// Backward compatibility with old method of passing arguments.
		if ( !is_array( $args ) || func_num_args() > 1 ) {
			_deprecated_argument( __METHOD__, '1.6', sprintf( __( 'Arguments passed to %1$s should be in an associative array. See the inline documentation at %2$s for more details.', 'buddypress' ), __METHOD__, __FILE__ ) );

			$old_args_keys = array(
				0 => 'page',
				1 => 'per_page',
				2 => 'max',
				3 => 'include',
				4 => 'sort',
				5 => 'filter',
				6 => 'search_terms',
				7 => 'display_comments',
				8 => 'show_hidden',
				9 => 'exclude',
				10 => 'in',
				11 => 'spam',
				12 => 'page_arg'
			);

			$func_args = func_get_args();
			$args = bp_core_parse_args_array( $old_args_keys, $func_args );
		}

		$defaults = array(
			'page'              => 1,
			'per_page'          => 20,
			'page_arg'          => 'acpage',
			'max'               => false,
			'fields'            => 'all',
			'count_total'       => false,
			'sort'              => false,
			'include'           => false,
			'exclude'           => false,
			'in'                => false,
			'filter'            => false,
			'scope'             => false,
			'search_terms'      => false,
			'meta_query'        => false,
			'date_query'        => false,
			'filter_query'      => false,
			'display_comments'  => 'threaded',
			'show_hidden'       => false,
			'spam'              => 'ham_only',
			'update_meta_cache' => true,
		);
		$r = wp_parse_args( $args, $defaults );
		extract( $r );

		$this->pag_arg  = sanitize_key( $r['page_arg'] );
		$this->pag_page = bp_sanitize_pagination_arg( $this->pag_arg, $r['page']     );
		$this->pag_num  = bp_sanitize_pagination_arg( 'num',          $r['per_page'] );

		// Check if blog/forum replies are disabled.
		$this->disable_blogforum_replies = (bool) bp_core_get_root_option( 'bp-disable-blogforum-comments' );

		// Get an array of the logged in user's favorite activities.
		$this->my_favs = maybe_unserialize( bp_get_user_meta( bp_loggedin_user_id(), 'bp_favorite_activities', true ) );

		// Fetch specific activity items based on ID's.
		if ( !empty( $include ) ) {
			$this->activities = bp_activity_get_specific( array(
				'activity_ids'      => explode( ',', $include ),
				'max'               => $max,
				'count_total'       => $count_total,
				'page'              => $this->pag_page,
				'per_page'          => $this->pag_num,
				'sort'              => $sort,
				'display_comments'  => $display_comments,
				'show_hidden'       => $show_hidden,
				'spam'              => $spam,
				'update_meta_cache' => $update_meta_cache,
			) );

		// Fetch all activity items.
		} else {
			$this->activities = bp_activity_get( array(
				'display_comments'  => $display_comments,
				'max'               => $max,
				'count_total'       => $count_total,
				'per_page'          => $this->pag_num,
				'page'              => $this->pag_page,
				'sort'              => $sort,
				'search_terms'      => $search_terms,
				'meta_query'        => $meta_query,
				'date_query'        => $date_query,
				'filter_query'      => $filter_query,
				'filter'            => $filter,
				'scope'             => $scope,
				'show_hidden'       => $show_hidden,
				'exclude'           => $exclude,
				'in'                => $in,
				'spam'              => $spam,
				'update_meta_cache' => $update_meta_cache,
			) );
		}

		// The total_activity_count property will be set only if a
		// 'count_total' query has taken place.
		if ( ! is_null( $this->activities['total'] ) ) {
			if ( ! $max || $max >= (int) $this->activities['total'] ) {
				$this->total_activity_count = (int) $this->activities['total'];
			} else {
				$this->total_activity_count = (int) $max;
			}
		}

		$this->has_more_items = $this->activities['has_more_items'];

		$this->activities = $this->activities['activities'];

		if ( $max ) {
			if ( $max >= count($this->activities) ) {
				$this->activity_count = count( $this->activities );
			} else {
				$this->activity_count = (int) $max;
			}
		} else {
			$this->activity_count = count( $this->activities );
		}

		$this->full_name = bp_get_displayed_user_fullname();

		// Fetch parent content for activity comments so we do not have to query in the loop.
		foreach ( (array) $this->activities as $activity ) {
			if ( 'activity_comment' != $activity->type ) {
				continue;
			}

			$parent_ids[] = $activity->item_id;
		}

		if ( !empty( $parent_ids ) ) {
			$activity_parents = bp_activity_get_specific( array( 'activity_ids' => $parent_ids ) );
		}

		if ( !empty( $activity_parents['activities'] ) ) {
			foreach( $activity_parents['activities'] as $parent ) {
				$this->activity_parents[$parent->id] = $parent;
			}

			unset( $activity_parents );
		}

		if ( (int) $this->total_activity_count && (int) $this->pag_num ) {
			$this->pag_links = paginate_links( array(
				'base'      => add_query_arg( $this->pag_arg, '%#%' ),
				'format'    => '',
				'total'     => ceil( (int) $this->total_activity_count / (int) $this->pag_num ),
				'current'   => (int) $this->pag_page,
				'prev_text' => _x( '&larr;', 'Activity pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Activity pagination next text', 'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => array(),
			) );
		}
	}

	/**
	 * Whether there are activity items available in the loop.
	 *
	 * @see bp_has_activities()
	 *
	 * @return bool True if there are items in the loop, otherwise false.
	 */
	function has_activities() {
		if ( $this->activity_count ) {
			return true;
		}

		return false;
	}

	/**
	 * Set up the next activity item and iterate index.
	 *
	 * @return object The next activity item to iterate over.
	 */
	public function next_activity() {
		$this->current_activity++;
		$this->activity = $this->activities[ $this->current_activity ];

		return $this->activity;
	}

	/**
	 * Rewind the posts and reset post index.
	 */
	public function rewind_activities() {
		$this->current_activity = -1;
		if ( $this->activity_count > 0 ) {
			$this->activity = $this->activities[0];
		}
	}

	/**
	 * Whether there are activity items left in the loop to iterate over.
	 *
	 * This method is used by {@link bp_activities()} as part of the while loop
	 * that controls iteration inside the activities loop, eg:
	 *     while ( bp_activities() ) { ...
	 *
	 * @see bp_activities()
	 *
	 * @return bool True if there are more activity items to show,
	 *              otherwise false.
	 */
	public function user_activities() {
		if ( ( $this->current_activity + 1 ) < $this->activity_count ) {
			return true;
		} elseif ( ( $this->current_activity + 1 ) == $this->activity_count ) {

			/**
			 * Fires right before the rewinding of activity posts.
			 *
			 * @since 1.1.0
			 */
			do_action( 'activity_loop_end' );

			// Do some cleaning up after the loop.
			$this->rewind_activities();
		}

		$this->in_the_loop = false;

		return false;
	}

	/**
	 * Set up the current activity item inside the loop.
	 *
	 * Used by {@link bp_the_activity()} to set up the current activity item
	 * data while looping, so that template tags used during that iteration
	 * make reference to the current activity item.
	 *
	 * @see bp_the_activity()
	 */
	public function the_activity() {

		$this->in_the_loop = true;
		$this->activity    = $this->next_activity();

		if ( is_array( $this->activity ) ) {
			$this->activity = (object) $this->activity;
		}

		// Loop has just started.
		if ( $this->current_activity == 0 ) {

			/**
			 * Fires if the current activity item is the first in the activity loop.
			 *
			 * @since 1.1.0
			 */
			do_action('activity_loop_start');
		}
	}
}

/**
 * Initialize the activity loop.
 *
 * Based on the $args passed, bp_has_activities() populates the
 * $activities_template global, enabling the use of BuddyPress templates and
 * template functions to display a list of activity items.
 *
 * @since 1.0.0
 * @since 2.4.0 Introduced the `$fields` parameter.
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses groups_is_user_member()
 * @uses bp_current_action()
 * @uses bp_is_current_action()
 * @uses bp_get_activity_slug()
 * @uses bp_action_variable()
 * @uses wp_parse_args()
 * @uses bp_is_active()
 * @uses friends_get_friend_user_ids()
 * @uses groups_get_user_groups()
 * @uses bp_activity_get_user_favorites()
 * @uses apply_filters() To call the 'bp_has_activities' hook.
 *
 * @param array|string $args {
 *     Arguments for limiting the contents of the activity loop. Most arguments
 *     are in the same format as {@link BP_Activity_Activity::get()}. However,
 *     because the format of the arguments accepted here differs in a number of
 *     ways, and because bp_has_activities() determines some default arguments in
 *     a dynamic fashion, we list all accepted arguments here as well.
 *
 *     Arguments can be passed as an associative array, or as a URL querystring
 *     (eg, 'user_id=4&display_comments=threaded').
 *
 *     @type int               $page             Which page of results to fetch. Using page=1 without per_page will result
 *                                               in no pagination. Default: 1.
 *     @type int|bool          $per_page         Number of results per page. Default: 20.
 *     @type string            $page_arg         String used as a query parameter in pagination links. Default: 'acpage'.
 *     @type int|bool          $max              Maximum number of results to return. Default: false (unlimited).
 *     @type string            $fields           Activity fields to retrieve. 'all' to fetch entire activity objects,
 *                                               'ids' to get only the activity IDs. Default 'all'.
 *     @type string|bool       $count_total      If true, an additional DB query is run to count the total activity items
 *                                               for the query. Default: false.
 *     @type string            $sort             'ASC' or 'DESC'. Default: 'DESC'.
 *     @type array|bool        $exclude          Array of activity IDs to exclude. Default: false.
 *     @type array|bool        $in               Array of IDs to limit query by (IN). 'in' is intended to be used in
 *                                               conjunction with other filter parameters. Default: false.
 *     @type array|bool        $include          Array of exact activity IDs to query. Providing an 'include' array will
 *                                               override all other filters passed in the argument array. When viewing the
 *                                               permalink page for a single activity item, this value defaults to the ID of
 *                                               that item. Otherwise the default is false.
 *     @type array             $meta_query       Limit by activitymeta by passing an array of meta_query conditions. See
 *                                               {@link WP_Meta_Query::queries} for a description of the syntax.
 *     @type array             $date_query       Limit by date by passing an array of date_query conditions. See first
 *                                               parameter of {@link WP_Date_Query::__construct()} for syntax.
 *     @type array             $filter_query     Advanced activity filtering.  See {@link BP_Activity_Query::__construct()}.
 *     @type string            $search_terms     Limit results by a search term. Default: false.
 *     @type string            $scope            Use a BuddyPress pre-built filter.
 *                                                 - 'just-me' retrieves items belonging only to a user; this is equivalent
 *                                                   to passing a 'user_id' argument.
 *                                                 - 'friends' retrieves items belonging to the friends of a user.
 *                                                 - 'groups' retrieves items belonging to groups to which a user belongs to.
 *                                                 - 'favorites' retrieves a user's favorited activity items.
 *                                                 - 'mentions' retrieves items where a user has received an @-mention.
 *                                               The default value of 'scope' is set to one of the above if that value
 *                                               appears in the appropriate place in the URL; eg, 'scope' will be 'groups'
 *                                               when visiting http://example.com/members/joe/activity/groups/. Otherwise
 *                                               defaults to false.
 *     @type int|array|bool    $user_id          The ID(s) of user(s) whose activity should be fetched. Pass a single ID or
 *                                               an array of IDs. When viewing a user profile page (but not that user's
 *                                               activity subpages, ie My Friends, My Groups, etc), 'user_id' defaults to
 *                                               the ID of the displayed user. Otherwise the default is false.
 *     @type string|array|bool $object           Filters by the `component` column in the database, which is generally the
 *                                               component ID in the case of BuddyPress components, or the plugin slug in
 *                                               the case of plugins. For example, 'groups' will limit results to those that
 *                                               are associated with the BP Groups component. Accepts a single component
 *                                               string, or an array of multiple components. Defaults to 'groups' when
 *                                               viewing the page of a single group, the My Groups activity filter, or the
 *                                               Activity > Groups filter of a user profile. Otherwise defaults to false.
 *     @type string|array|bool $action           Filters by the `type` column in the database, which is a string
 *                                               categorizing the activity item (eg, 'new_blog_post', 'created_group').
 *                                               Accepts a comma-delimited string or an array of types. Default: false.
 *     @type int|array|bool    $primary_id       Filters by the `item_id` column in the database. The meaning of
 *                                               'primary_id' differs between components/types; for example, in the case of
 *                                               'created_group', 'primary_id' is the ID of the group. Accepts a single ID,
 *                                               or an array of multiple IDs. When viewing a single group, defaults to the
 *                                               current group ID. When viewing a user's Groups stream page, defaults to the
 *                                               IDs of the user's groups. Otherwise defaults to false.
 *     @type int|array|bool    $secondary_id     Filters by the `secondary_item_id` column in the database. The meaning of
 *                                               'secondary_id' differs between components/types. Accepts a single ID, or an
 *                                               array of multiple IDs. Defaults to false.
 *     @type int               $offset           Return only activity items with an ID greater than or equal to this one.
 *                                               Note that providing an offset will disable pagination. Default: false.
 *     @type string|bool       $display_comments How to handle activity comments. Possible values:
 *                                                 - 'threaded' - comments appear in a threaded tree, under their parent
 *                                                   items.
 *                                                 - 'stream' - the activity stream is presented in a flat manner, with
 *                                                   comments sorted in chronological order alongside other activity items.
 *                                                 - false - don't fetch activity comments at all.
 *                                               Default: 'threaded'.
 *     @type bool              $show_hidden      Whether to show items marked hide_sitewide. Defaults to false, except in
 *                                               the following cases:
 *                                                 - User is viewing his own activity stream.
 *                                                 - User is viewing the activity stream of a non-public group of which he
 *                                                   is a member.
 *     @type string|bool       $spam             Spam status. 'ham_only', 'spam_only', or false to show all activity
 *                                               regardless of spam status. Default: 'ham_only'.
 *     @type bool              $populate_extras  Whether to pre-fetch the activity metadata for the queried items.
 *                                               Default: true.
 * }
 * @return bool Returns true when activities are found, otherwise false.
 */
function bp_has_activities( $args = '' ) {
	global $activities_template;

	// Get BuddyPress.
	$bp = buddypress();

	/*
	 * Smart Defaults.
	 */

	// User filtering.
	$user_id = bp_displayed_user_id()
		? bp_displayed_user_id()
		: false;

	// Group filtering.
	if ( bp_is_group() ) {
		$object      = $bp->groups->id;
		$primary_id  = bp_get_current_group_id();
		$show_hidden = (bool) ( groups_is_user_member( bp_loggedin_user_id(), $primary_id ) || bp_current_user_can( 'bp_moderate' ) );
	} else {
		$object      = false;
		$primary_id  = false;
		$show_hidden = false;
	}

	// The default scope should recognize custom slugs.
	$scope = array_key_exists( bp_current_action(), (array) $bp->loaded_components )
		? $bp->loaded_components[ bp_current_action() ]
		: bp_current_action();

	// Support for permalinks on single item pages: /groups/my-group/activity/124/.
	$include = bp_is_current_action( bp_get_activity_slug() )
		? bp_action_variable( 0 )
		: false;

	$search_terms_default = false;
	$search_query_arg = bp_core_get_component_search_query_arg( 'activity' );
	if ( ! empty( $_REQUEST[ $search_query_arg ] ) ) {
		$search_terms_default = stripslashes( $_REQUEST[ $search_query_arg ] );
	}

	/*
	 * Parse Args.
	 */

	// Note: any params used for filtering can be a single value, or multiple
	// values comma separated.
	$r = bp_parse_args( $args, array(
		'display_comments'  => 'threaded',   // False for none, stream/threaded - show comments in the stream or threaded under items.
		'include'           => $include,     // Pass an activity_id or string of IDs comma-separated.
		'exclude'           => false,        // Pass an activity_id or string of IDs comma-separated.
		'in'                => false,        // Comma-separated list or array of activity IDs among which to search.
		'sort'              => 'DESC',       // Sort DESC or ASC.
		'page'              => 1,            // Which page to load.
		'per_page'          => 20,           // Number of items per page.
		'page_arg'          => 'acpage',     // See https://buddypress.trac.wordpress.org/ticket/3679.
		'max'               => false,        // Max number to return.
		'fields'            => 'all',
		'count_total'       => false,
		'show_hidden'       => $show_hidden, // Show activity items that are hidden site-wide?
		'spam'              => 'ham_only',   // Hide spammed items.

		// Scope - pre-built activity filters for a user (friends/groups/favorites/mentions).
		'scope'             => $scope,

		// Filtering
		'user_id'           => $user_id,     // user_id to filter on.
		'object'            => $object,      // Object to filter on e.g. groups, profile, status, friends.
		'action'            => false,        // Action to filter on e.g. activity_update, new_forum_post, profile_updated.
		'primary_id'        => $primary_id,  // Object ID to filter on e.g. a group_id or forum_id or blog_id etc.
		'secondary_id'      => false,        // Secondary object ID to filter on e.g. a post_id.
		'offset'            => false,        // Return only items >= this ID.
		'since'             => false,        // Return only items recorded since this Y-m-d H:i:s date.

		'meta_query'        => false,        // Filter on activity meta. See WP_Meta_Query for format.
		'date_query'        => false,        // Filter by date. See first parameter of WP_Date_Query for format.
		'filter_query'      => false,        // Advanced filtering.  See BP_Activity_Query for format.

		// Searching.
		'search_terms'      => $search_terms_default,
		'update_meta_cache' => true,
	), 'has_activities' );

	/*
	 * Smart Overrides.
	 */

	// Translate various values for 'display_comments'
	// This allows disabling comments via ?display_comments=0
	// or =none or =false. Final true is a strict type check. See #5029.
	if ( in_array( $r['display_comments'], array( 0, '0', 'none', 'false' ), true ) ) {
		$r['display_comments'] = false;
	}

	// Ignore pagination if an offset is passed.
	if ( ! empty( $r['offset'] ) ) {
		$r['page'] = 0;
	}

	// Search terms.
	if ( ! empty( $_REQUEST['s'] ) && empty( $r['search_terms'] ) ) {
		$r['search_terms'] = $_REQUEST['s'];
	}

	// Do not exceed the maximum per page.
	if ( ! empty( $r['max'] ) && ( (int) $r['per_page'] > (int) $r['max'] ) ) {
		$r['per_page'] = $r['max'];
	}

	/**
	 * Filters whether BuddyPress should enable afilter support.
	 *
	 * Support for basic filters in earlier BP versions is disabled by default.
	 * To enable, put add_filter( 'bp_activity_enable_afilter_support', '__return_true' );
	 * into bp-custom.php or your theme's functions.php.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value True if BuddyPress should enable afilter support.
	 */
	if ( isset( $_GET['afilter'] ) && apply_filters( 'bp_activity_enable_afilter_support', false ) ) {
		$r['filter'] = array(
			'object' => $_GET['afilter']
		);
	} elseif ( ! empty( $r['user_id'] ) || ! empty( $r['object'] ) || ! empty( $r['action'] ) || ! empty( $r['primary_id'] ) || ! empty( $r['secondary_id'] ) || ! empty( $r['offset'] ) || ! empty( $r['since'] ) ) {
		$r['filter'] = array(
			'user_id'      => $r['user_id'],
			'object'       => $r['object'],
			'action'       => $r['action'],
			'primary_id'   => $r['primary_id'],
			'secondary_id' => $r['secondary_id'],
			'offset'       => $r['offset'],
			'since'        => $r['since']
		);
	} else {
		$r['filter'] = false;
	}

	// If specific activity items have been requested, override the $hide_spam
	// argument. This prevents backpat errors with AJAX.
	if ( ! empty( $r['include'] ) && ( 'ham_only' === $r['spam'] ) ) {
		$r['spam'] = 'all';
	}

	/*
	 * Query
	 */

	$activities_template = new BP_Activity_Template( $r );

	/**
	 * Filters whether or not there are activity items to display.
	 *
	 * @since 1.1.0
	 *
	 * @param bool   $value               Whether or not there are activity items to display.
	 * @param string $activities_template Current activities template being used.
	 * @param array  $r                   Array of arguments passed into the BP_Activity_Template class.
	 */
	return apply_filters( 'bp_has_activities', $activities_template->has_activities(), $activities_template, $r );
}

/**
 * Determine if there are still activities left in the loop.
 *
 * @since 1.0.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses BP_Activity_Template::user_activities() {@link BP_Activity_Template::user_activities()}
 *
 * @return bool Returns true when activities are found.
 */
function bp_activities() {
	global $activities_template;
	return $activities_template->user_activities();
}

/**
 * Get the current activity object in the loop.
 *
 * @since 1.0.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses BP_Activity_Template::the_activity() {@link BP_Activity_Template::the_activity()}
 *
 * @return object The current activity within the loop.
 */
function bp_the_activity() {
	global $activities_template;
	return $activities_template->the_activity();
}

/**
 * Output the URL for the Load More link.
 *
 * @since 2.1.0
 */
function bp_activity_load_more_link() {
	echo esc_url( bp_get_activity_load_more_link() );
}
	/**
	 * Get the URL for the Load More link.
	 *
	 * @since 2.1.0
	 *
	 * @return string $link
	 */
	function bp_get_activity_load_more_link() {
		global $activities_template;

		$url  = bp_get_requested_url();
		$link = add_query_arg( $activities_template->pag_arg, $activities_template->pag_page + 1, $url );

		/**
		 * Filters the Load More link URL.
		 *
		 * @since 2.1.0
		 *
		 * @param string $link                The "Load More" link URL with appropriate query args.
		 * @param string $url                 The original URL.
		 * @param object $activities_template The activity template loop global.
		 */
		return apply_filters( 'bp_get_activity_load_more_link', $link, $url, $activities_template );
	}

/**
 * Output the activity pagination count.
 *
 * @since 1.0.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses BP_Activity_Template::the_activity() {@link BP_Activity_Template::the_activity()}
 */
function bp_activity_pagination_count() {
	echo bp_get_activity_pagination_count();
}

	/**
	 * Return the activity pagination count.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_core_number_format()
	 *
	 * @return string The pagination text.
	 */
	function bp_get_activity_pagination_count() {
		global $activities_template;

		$start_num = intval( ( $activities_template->pag_page - 1 ) * $activities_template->pag_num ) + 1;
		$from_num  = bp_core_number_format( $start_num );
		$to_num    = bp_core_number_format( ( $start_num + ( $activities_template->pag_num - 1 ) > $activities_template->total_activity_count ) ? $activities_template->total_activity_count : $start_num + ( $activities_template->pag_num - 1 ) );
		$total     = bp_core_number_format( $activities_template->total_activity_count );

		if ( 1 == $activities_template->total_activity_count ) {
			$message = __( 'Viewing 1 item', 'buddypress' );
		} else {
			$message = sprintf( _n( 'Viewing %1$s - %2$s of %3$s item', 'Viewing %1$s - %2$s of %3$s items', $activities_template->total_activity_count, 'buddypress' ), $from_num, $to_num, $total );
		}

		return $message;
	}

/**
 * Output the activity pagination links.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_pagination_links()
 */
function bp_activity_pagination_links() {
	echo bp_get_activity_pagination_links();
}

	/**
	 * Return the activity pagination links.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_pagination_links' hook.
	 *
	 * @return string The pagination links.
	 */
	function bp_get_activity_pagination_links() {
		global $activities_template;

		/**
		 * Filters the activity pagination link output.
		 *
		 * @since 1.0.0
		 *
		 * @param string $pag_links Output for the activity pagination links.
		 */
		return apply_filters( 'bp_get_activity_pagination_links', $activities_template->pag_links );
	}

/**
 * Return true when there are more activity items to be shown than currently appear.
 *
 * @since 1.5.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses apply_filters() To call the 'bp_activity_has_more_items' hook.
 *
 * @return bool $has_more_items True if more items, false if not.
 */
function bp_activity_has_more_items() {
	global $activities_template;

	if ( ! empty( $activities_template->has_more_items )  ) {
		$has_more_items = true;
	} else {
		$remaining_pages = 0;

		if ( ! empty( $activities_template->pag_page ) ) {
			$remaining_pages = floor( ( $activities_template->total_activity_count - 1 ) / ( $activities_template->pag_num * $activities_template->pag_page ) );
		}

		$has_more_items = (int) $remaining_pages > 0;
	}

	/**
	 * Filters whether there are more activity items to display.
	 *
	 * @since 1.5.0
	 *
	 * @param bool $has_more_items Whether or not there are more activity items to display.
	 */
	return apply_filters( 'bp_activity_has_more_items', $has_more_items );
}

/**
 * Output the activity count.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_count()
 */
function bp_activity_count() {
	echo bp_get_activity_count();
}

	/**
	 * Return the activity count.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_count' hook.
	 *
	 * @return int The activity count.
	 */
	function bp_get_activity_count() {
		global $activities_template;

		/**
		 * Filters the activity count for the activity template.
		 *
		 * @since 1.2.0
		 *
		 * @param int $activity_count The count for total activity.
		 */
		return apply_filters( 'bp_get_activity_count', (int) $activities_template->activity_count );
	}

/**
 * Output the number of activities per page.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_per_page()
 */
function bp_activity_per_page() {
	echo bp_get_activity_per_page();
}

	/**
	 * Return the number of activities per page.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_per_page' hook.
	 *
	 * @return int The activities per page.
	 */
	function bp_get_activity_per_page() {
		global $activities_template;

		/**
		 * Filters the activity posts per page value.
		 *
		 * @since 1.2.0
		 *
		 * @param int $pag_num How many post should be displayed for pagination.
		 */
		return apply_filters( 'bp_get_activity_per_page', (int) $activities_template->pag_num );
	}

/**
 * Output the activities title.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activities_title()
 * @todo Deprecate.
 */
function bp_activities_title() {
	echo bp_get_activities_title();
}

	/**
	 * Return the activities title.
	 *
	 * @since 1.0.0
	 *
	 * @global string $bp_activity_title
	 * @uses apply_filters() To call the 'bp_get_activities_title' hook.
	 * @todo Deprecate.
	 *
	 * @return string The activities title.
	 */
	function bp_get_activities_title() {
		global $bp_activity_title;

		/**
		 * Filters the activities title for the activity template.
		 *
		 * @since 1.0.0
		 *
		 * @param string $bp_activity_title The title to be displayed.
		 */
		return apply_filters( 'bp_get_activities_title', $bp_activity_title );
	}

/**
 * {@internal Missing Description}
 *
 * @since 1.0.0
 *
 * @uses bp_get_activities_no_activity()
 * @todo Deprecate.
 */
function bp_activities_no_activity() {
	echo bp_get_activities_no_activity();
}

	/**
	 * {@internal Missing Description}
	 *
	 * @since 1.0.0
	 *
	 * @global string $bp_activity_no_activity
	 * @uses apply_filters() To call the 'bp_get_activities_no_activity' hook.
	 * @todo Deprecate.
	 *
	 * @return string
	 */
	function bp_get_activities_no_activity() {
		global $bp_activity_no_activity;

		/**
		 * Filters the text used when there is no activity to display.
		 *
		 * @since 1.0.0
		 *
		 * @param string $bp_activity_no_activity Text to display for no activity.
		 */
		return apply_filters( 'bp_get_activities_no_activity', $bp_activity_no_activity );
	}

/**
 * Output the activity ID.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_id()
 */
function bp_activity_id() {
	echo bp_get_activity_id();
}

	/**
	 * Return the activity ID.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_id' hook.
	 *
	 * @return int The activity ID.
	 */
	function bp_get_activity_id() {
		global $activities_template;

		/**
		 * Filters the activity ID being displayed.
		 *
		 * @since 1.2.0
		 *
		 * @param int $id The activity ID.
		 */
		return apply_filters( 'bp_get_activity_id', $activities_template->activity->id );
	}

/**
 * Output the activity item ID.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_item_id()
 */
function bp_activity_item_id() {
	echo bp_get_activity_item_id();
}

	/**
	 * Return the activity item ID.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_item_id' hook.
	 *
	 * @return int The activity item ID.
	 */
	function bp_get_activity_item_id() {
		global $activities_template;

		/**
		 * Filters the activity item ID being displayed.
		 *
		 * @since 1.2.0
		 *
		 * @param int $item_id The activity item ID.
		 */
		return apply_filters( 'bp_get_activity_item_id', $activities_template->activity->item_id );
	}

/**
 * Output the activity secondary item ID.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_secondary_item_id()
 */
function bp_activity_secondary_item_id() {
	echo bp_get_activity_secondary_item_id();
}

	/**
	 * Return the activity secondary item ID.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_secondary_item_id' hook.
	 *
	 * @return int The activity secondary item ID.
	 */
	function bp_get_activity_secondary_item_id() {
		global $activities_template;

		/**
		 * Filters the activity secondary item ID being displayed.
		 *
		 * @since 1.2.0
		 *
		 * @param int $secondary_item_id The activity secondary item ID.
		 */
		return apply_filters( 'bp_get_activity_secondary_item_id', $activities_template->activity->secondary_item_id );
	}

/**
 * Output the date the activity was recorded.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_date_recorded()
 */
function bp_activity_date_recorded() {
	echo bp_get_activity_date_recorded();
}

	/**
	 * Return the date the activity was recorded.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_date_recorded' hook.
	 *
	 * @return string The date the activity was recorded.
	 */
	function bp_get_activity_date_recorded() {
		global $activities_template;

		/**
		 * Filters the date the activity was recorded.
		 *
		 * @since 1.2.0
		 *
		 * @param int $date_recorded The activity's date.
		 */
		return apply_filters( 'bp_get_activity_date_recorded', $activities_template->activity->date_recorded );
	}

/**
 * Output the display name of the member who posted the activity.
 *
 * @since 2.1.0
 *
 * @uses bp_get_activity_member_display_name()
 */
function bp_activity_member_display_name() {
	echo bp_get_activity_member_display_name();
}

	/**
	 * Return the display name of the member who posted the activity.
	 *
	 * @since 2.1.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_member_display_name' hook.
	 *
	 * @return string The date the activity was recorded.
	 */
	function bp_get_activity_member_display_name() {
		global $activities_template;

		$retval = isset( $activities_template->activity->display_name )
			? $activities_template->activity->display_name
			: '';

		/**
		 * Filters the display name of the member who posted the activity.
		 *
		 * @since 2.1.0
		 *
		 * @param int $retval Display name for the member who posted.
		 */
		return apply_filters( 'bp_get_activity_member_display_name', $retval );
	}

/**
 * Output the activity object name.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_object_name()
 */
function bp_activity_object_name() {
	echo bp_get_activity_object_name();
}

	/**
	 * Return the activity object name.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_object_name' hook.
	 *
	 * @return string The activity object name.
	 */
	function bp_get_activity_object_name() {
		global $activities_template;

		/**
		 * Filters the activity object name.
		 *
		 * @since 1.2.0
		 *
		 * @param string $activity_component The activity object name.
		 */
		return apply_filters( 'bp_get_activity_object_name', $activities_template->activity->component );
	}

/**
 * Output the activity type.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_type()
 */
function bp_activity_type() {
	echo bp_get_activity_type();
}

	/**
	 * Return the activity type.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_type' hook.
	 *
	 * @return string The activity type.
	 */
	function bp_get_activity_type() {
		global $activities_template;

		/**
		 * Filters the activity type.
		 *
		 * @since 1.2.0
		 *
		 * @param string $activity_type The activity type.
		 */
		return apply_filters( 'bp_get_activity_type', $activities_template->activity->type );
	}

	/**
	 * Output the activity action name.
	 *
	 * Just a wrapper for bp_activity_type().
	 *
	 * @since 1.2.0
	 * @deprecated 1.5.0
	 *
	 * @todo Properly deprecate in favor of bp_activity_type() and
	 *       remove redundant echo
	 *
	 * @uses bp_activity_type()
	 */
	function bp_activity_action_name() { echo bp_activity_type(); }

	/**
	 * Return the activity type.
	 *
	 * Just a wrapper for bp_get_activity_type().
	 *
	 * @since 1.2.0
	 * @deprecated 1.5.0
	 *
	 * @todo Properly deprecate in favor of bp_get_activity_type().
	 *
	 * @uses bp_get_activity_type()
	 *
	 * @return string The activity type.
	 */
	function bp_get_activity_action_name() { return bp_get_activity_type(); }

/**
 * Output the activity user ID.
 *
 * @since 1.1.0
 *
 * @uses bp_get_activity_user_id()
 */
function bp_activity_user_id() {
	echo bp_get_activity_user_id();
}

	/**
	 * Return the activity user ID.
	 *
	 * @since 1.1.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_user_id' hook.
	 *
	 * @return int The activity user ID.
	 */
	function bp_get_activity_user_id() {
		global $activities_template;

		/**
		 * Filters the activity user ID.
		 *
		 * @since 1.1.0
		 *
		 * @param int $user_id The activity user ID.
		 */
		return apply_filters( 'bp_get_activity_user_id', $activities_template->activity->user_id );
	}

/**
 * Output the activity user link.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_user_link()
 */
function bp_activity_user_link() {
	echo bp_get_activity_user_link();
}

	/**
	 * Return the activity user link.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_core_get_user_domain()
	 * @uses apply_filters() To call the 'bp_get_activity_user_link' hook.
	 *
	 * @return string $link The activity user link.
	 */
	function bp_get_activity_user_link() {
		global $activities_template;

		if ( empty( $activities_template->activity->user_id ) || empty( $activities_template->activity->user_nicename ) || empty( $activities_template->activity->user_login ) ) {
			$link = $activities_template->activity->primary_link;
		} else {
			$link = bp_core_get_user_domain( $activities_template->activity->user_id, $activities_template->activity->user_nicename, $activities_template->activity->user_login );
		}

		/**
		 * Filters the activity user link.
		 *
		 * @since 1.2.0
		 *
		 * @param string $link The activity user link.
		 */
		return apply_filters( 'bp_get_activity_user_link', $link );
	}

/**
 * Output the avatar of the user that performed the action.
 *
 * @since 1.1.0
 *
 * @see bp_get_activity_avatar() for description of arguments.
 * @uses bp_get_activity_avatar()
 *
 * @param array|string $args See {@link bp_get_activity_avatar()} for description.
 */
function bp_activity_avatar( $args = '' ) {
	echo bp_get_activity_avatar( $args );
}
	/**
	 * Return the avatar of the user that performed the action.
	 *
	 * @since 1.1.0
	 *
	 * @see bp_core_fetch_avatar() For a description of the arguments.
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_is_single_activity()
	 * @uses wp_parse_args()
	 * @uses apply_filters() To call the 'bp_get_activity_avatar_object_' . $current_activity_item->component hook.
	 * @uses apply_filters() To call the 'bp_get_activity_avatar_item_id' hook.
	 * @uses bp_core_fetch_avatar()
	 * @uses apply_filters() To call the 'bp_get_activity_avatar' hook.
	 *
	 * @param array|string $args  {
	 *     Arguments are listed here with an explanation of their defaults.
	 *     For more information about the arguments, see
	 *     {@link bp_core_fetch_avatar()}.
	 *     @type string      $alt     Default: 'Profile picture of [user name]' if
	 *                                activity user name is available, otherwise 'Profile picture'.
	 *     @type string      $class   Default: 'avatar'.
	 *     @type string|bool $email   Default: Email of the activity's
	 *                                associated user, if available. Otherwise false.
	 *     @type string      $type    Default: 'full' when viewing a single activity
	 *                                permalink page, otherwise 'thumb'.
	 *     @type int|bool    $user_id Default: ID of the activity's user.
	 * }
	 * @return string User avatar string.
	 */
	function bp_get_activity_avatar( $args = '' ) {
		global $activities_template;

		$bp = buddypress();

		// On activity permalink pages, default to the full-size avatar.
		$type_default = bp_is_single_activity() ? 'full' : 'thumb';

		// Within the activity comment loop, the current activity should be set
		// to current_comment. Otherwise, just use activity.
		$current_activity_item = isset( $activities_template->activity->current_comment ) ? $activities_template->activity->current_comment : $activities_template->activity;

		// Activity user display name.
		$dn_default  = isset( $current_activity_item->display_name ) ? $current_activity_item->display_name : '';

		// Prepend some descriptive text to alt.
		$alt_default = !empty( $dn_default ) ? sprintf( __( 'Profile picture of %s', 'buddypress' ), $dn_default ) : __( 'Profile picture', 'buddypress' );

		$defaults = array(
			'alt'     => $alt_default,
			'class'   => 'avatar',
			'email'   => false,
			'type'    => $type_default,
			'user_id' => false
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		if ( !isset( $height ) && !isset( $width ) ) {

			// Backpat.
			if ( isset( $bp->avatar->full->height ) || isset( $bp->avatar->thumb->height ) ) {
				$height = ( 'full' == $type ) ? $bp->avatar->full->height : $bp->avatar->thumb->height;
			} else {
				$height = 20;
			}

			// Backpat.
			if ( isset( $bp->avatar->full->width ) || isset( $bp->avatar->thumb->width ) ) {
				$width = ( 'full' == $type ) ? $bp->avatar->full->width : $bp->avatar->thumb->width;
			} else {
				$width = 20;
			}
		}

		/**
		 * Filters the activity avatar object based on current activity item component.
		 *
		 * This is a variable filter dependent on the component used.
		 * Possible hooks are bp_get_activity_avatar_object_blog,
		 * bp_get_activity_avatar_object_group, and bp_get_activity_avatar_object_user.
		 *
		 * @since 1.1.0
		 *
		 * @param string $component Component being displayed.
		 */
		$object  = apply_filters( 'bp_get_activity_avatar_object_' . $current_activity_item->component, 'user' );
		$item_id = !empty( $user_id ) ? $user_id : $current_activity_item->user_id;

		/**
		 * Filters the activity avatar item ID.
		 *
		 * @since 1.2.10
		 *
		 * @param int $item_id Item ID for the activity avatar.
		 */
		$item_id = apply_filters( 'bp_get_activity_avatar_item_id', $item_id );

		// If this is a user object pass the users' email address for Gravatar so we don't have to prefetch it.
		if ( 'user' == $object && empty( $user_id ) && empty( $email ) && isset( $current_activity_item->user_email ) ) {
			$email = $current_activity_item->user_email;
		}

		/**
		 * Filters the value returned by bp_core_fetch_avatar.
		 *
		 * @since 1.1.3
		 *
		 * @param array $value Array of arguments calculated for use with bp_core_fetch_avatar.
		 */
		return apply_filters( 'bp_get_activity_avatar', bp_core_fetch_avatar( array(
			'item_id' => $item_id,
			'object'  => $object,
			'type'    => $type,
			'alt'     => $alt,
			'class'   => $class,
			'width'   => $width,
			'height'  => $height,
			'email'   => $email
		) ) );
	}

/**
 * Output the avatar of the object that action was performed on.
 *
 * @since 1.2.0
 *
 * @see bp_get_activity_secondary_avatar() for description of arguments.
 * @uses bp_get_activity_secondary_avatar()
 *
 * @param array|string $args See {@link bp_get_activity_secondary_avatar} for description.
 */
function bp_activity_secondary_avatar( $args = '' ) {
	echo bp_get_activity_secondary_avatar( $args );
}

	/**
	 * Return the avatar of the object that action was performed on.
	 *
	 * @since 1.2.0
	 *
	 * @see bp_core_fetch_avatar() for description of arguments.
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses wp_parse_args()
	 * @uses get_blog_option()
	 * @uses apply_filters() To call the 'bp_get_activity_secondary_avatar_object_' . $activities_template->activity->component hook.
	 * @uses apply_filters() To call the 'bp_get_activity_secondary_avatar_item_id' hook.
	 * @uses bp_core_fetch_avatar()
	 * @uses apply_filters() To call the 'bp_get_activity_secondary_avatar' hook.
	 *
	 * @param array|string $args  {
	 *     For a complete description of arguments, see {@link bp_core_fetch_avatar()}.
	 *     @type string      $alt     Default value varies based on current activity
	 *                                item component.
	 *     @type string      $type    Default: 'full' when viewing a single activity
	 *                                permalink page, otherwise 'thumb'.
	 *     @type string      $class   Default: 'avatar'.
	 *     @type string|bool $email   Default: email of the activity's user.
	 *     @type int|bool    $user_id Default: ID of the activity's user.
	 * }
	 * @return string The secondary avatar.
	 */
	function bp_get_activity_secondary_avatar( $args = '' ) {
		global $activities_template;

		$r = wp_parse_args( $args, array(
			'alt'        => '',
			'type'       => 'thumb',
			'width'      => 20,
			'height'     => 20,
			'class'      => 'avatar',
			'link_class' => '',
			'linked'     => true,
			'email'      => false
		) );
		extract( $r, EXTR_SKIP );

		// Set item_id and object (default to user).
		switch ( $activities_template->activity->component ) {
			case 'groups' :
				if ( bp_disable_group_avatar_uploads() ) {
					return false;
				}

				$object  = 'group';
				$item_id = $activities_template->activity->item_id;
				$link    = '';
				$name    = '';

				// Only if groups is active.
				if ( bp_is_active( 'groups' ) ) {
					$group = groups_get_group( array(
						'group_id'          => $item_id,
						'populate_extras'   => false,
						'update_meta_cache' => false,
					) );
					$link  = bp_get_group_permalink( $group );
					$name  = $group->name;
				}

				if ( empty( $alt ) ) {
					$alt = __( 'Group logo', 'buddypress' );

					if ( ! empty( $name ) ) {
						$alt = sprintf( __( 'Group logo of %s', 'buddypress' ), $name );
					}
				}

				break;
			case 'blogs' :
				$object  = 'blog';
				$item_id = $activities_template->activity->item_id;
				$link    = home_url();

				if ( empty( $alt ) ) {
					$alt = sprintf( __( 'Profile picture of the author of the site %s', 'buddypress' ), get_blog_option( $item_id, 'blogname' ) );
				}

				break;
			case 'friends' :
				$object  = 'user';
				$item_id = $activities_template->activity->secondary_item_id;
				$link    = bp_core_get_userlink( $item_id, false, true );

				if ( empty( $alt ) ) {
					$alt = sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_core_get_user_displayname( $activities_template->activity->secondary_item_id ) );
				}

				break;
			default :
				$object  = 'user';
				$item_id = $activities_template->activity->user_id;
				$email   = $activities_template->activity->user_email;
				$link    = bp_core_get_userlink( $item_id, false, true );

				if ( empty( $alt ) ) {
					$alt = sprintf( __( 'Profile picture of %s', 'buddypress' ), $activities_template->activity->display_name );
				}

				break;
		}

		/**
		 * Filters the activity secondary avatar object based on current activity item component.
		 *
		 * This is a variable filter dependent on the component used. Possible hooks are
		 * bp_get_activity_secondary_avatar_object_blog, bp_get_activity_secondary_avatar_object_group,
		 * and bp_get_activity_secondary_avatar_object_user.
		 *
		 * @since 1.2.10
		 *
		 * @param string $object Component being displayed.
		 */
		$object  = apply_filters( 'bp_get_activity_secondary_avatar_object_' . $activities_template->activity->component, $object );

		/**
		 * Filters the activity secondary avatar item ID.
		 *
		 * @since 1.2.10
		 *
		 * @param int $item_id ID for the secondary avatar item.
		 */
		$item_id = apply_filters( 'bp_get_activity_secondary_avatar_item_id', $item_id );

		// If we have no item_id or object, there is no avatar to display.
		if ( empty( $item_id ) || empty( $object ) ) {
			return false;
		}

		// Get the avatar.
		$avatar = bp_core_fetch_avatar( array(
			'item_id' => $item_id,
			'object'  => $object,
			'type'    => $type,
			'alt'     => $alt,
			'class'   => $class,
			'width'   => $width,
			'height'  => $height,
			'email'   => $email
		) );

		if ( !empty( $linked ) ) {

			/**
			 * Filters the secondary avatar link for current activity.
			 *
			 * @since 1.7.0
			 *
			 * @param string $link      Link to wrap the avatar image in.
			 * @param string $component Activity component being acted on.
			 */
			$link = apply_filters( 'bp_get_activity_secondary_avatar_link', $link, $activities_template->activity->component );

			/**
			 * Filters the determined avatar for the secondary activity item.
			 *
			 * @since 1.2.10
			 *
			 * @param string $avatar Formatted HTML <img> element, or raw avatar URL.
			 */
			$avatar = apply_filters( 'bp_get_activity_secondary_avatar', $avatar );

			return sprintf( '<a href="%s" class="%s">%s</a>',
				$link,
				$link_class,
				$avatar
			);
		}

		/** This filter is documented in bp-activity/bp-activity-template.php */
		return apply_filters( 'bp_get_activity_secondary_avatar', $avatar );
	}

/**
 * Output the activity action.
 *
 * @since 1.2.0
 *
 * @param array $args See bp_get_activity_action().
 * @uses bp_get_activity_action()
 */
function bp_activity_action( $args = array() ) {
	echo bp_get_activity_action( $args );
}

	/**
	 * Return the activity action.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters_ref_array() To call the 'bp_get_activity_action_pre_meta' hook.
	 * @uses bp_insert_activity_meta()
	 * @uses apply_filters_ref_array() To call the 'bp_get_activity_action' hook.
	 *
	 * @param array $args {
	 *     @type bool $no_timestamp Whether to exclude the timestamp.
	 * }
	 *
	 * @return string The activity action.
	 */
	function bp_get_activity_action( $args = array() ) {
		global $activities_template;

		$r = wp_parse_args( $args, array(
			'no_timestamp' => false,
		) );

		/**
		 * Filters the activity action before the action is inserted as meta.
		 *
		 * @since 1.2.10
		 *
		 * @param array $value Array containing the current action, the current activity, and the $args array passed into the function.
		 */
		$action = apply_filters_ref_array( 'bp_get_activity_action_pre_meta', array(
			$activities_template->activity->action,
			&$activities_template->activity,
			$r
		) );

		// Prepend the activity action meta (link, time since, etc...).
		if ( ! empty( $action ) && empty( $r['no_timestamp'] ) ) {
			$action = bp_insert_activity_meta( $action );
		}

		/**
		 * Filters the activity action after the action has been inserted as meta.
		 *
		 * @since 1.2.0
		 *
		 * @param array $value Array containing the current action, the current activity, and the $args array passed into the function.
		 */
		return apply_filters_ref_array( 'bp_get_activity_action', array(
			$action,
			&$activities_template->activity,
			$r
		) );
	}

/**
 * Output the activity content body.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_content_body()
 */
function bp_activity_content_body() {
	echo bp_get_activity_content_body();
}

	/**
	 * Return the activity content body.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_insert_activity_meta()
	 * @uses apply_filters_ref_array() To call the 'bp_get_activity_content_body' hook.
	 *
	 * @return string The activity content body.
	 */
	function bp_get_activity_content_body() {
		global $activities_template;

		// Backwards compatibility if action is not being used.
		if ( empty( $activities_template->activity->action ) && ! empty( $activities_template->activity->content ) ) {
			$activities_template->activity->content = bp_insert_activity_meta( $activities_template->activity->content );
		}

		/**
		 * Filters the activity content body.
		 *
		 * @since 1.2.0
		 *
		 * @param array $value Array containing the current activity content body and the current activity.
		 */
		return apply_filters_ref_array( 'bp_get_activity_content_body', array( $activities_template->activity->content, &$activities_template->activity ) );
	}

/**
 * Does the activity have content?
 *
 * @since 1.2.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 *
 * @return bool True if activity has content, false otherwise.
 */
function bp_activity_has_content() {
	global $activities_template;

	if ( ! empty( $activities_template->activity->content ) ) {
		return true;
	}

	return false;
}

/**
 * Output the activity content.
 *
 * @since 1.0.0
 * @deprecated 1.5.0
 *
 * @todo properly deprecate this function.
 *
 * @uses bp_get_activity_content()
 */
function bp_activity_content() {
	echo bp_get_activity_content();
}

	/**
	 * Return the activity content.
	 *
	 * @since 1.0.0
	 * @deprecated 1.5.0
	 *
	 * @todo properly deprecate this function.
	 *
	 * @uses bp_get_activity_action()
	 * @uses bp_get_activity_content_body()
	 * @uses apply_filters() To call the 'bp_get_activity_content' hook.
	 *
	 * @return string The activity content.
	 */
	function bp_get_activity_content() {

		/**
		 * If you want to filter activity update content, please use
		 * the filter 'bp_get_activity_content_body'.
		 *
		 * This function is mainly for backwards compatibility.
		 */
		$content = bp_get_activity_action() . ' ' . bp_get_activity_content_body();
		return apply_filters( 'bp_get_activity_content', $content );
	}

/**
 * Attach metadata about an activity item to the activity content.
 *
 * This metadata includes the time since the item was posted (which will appear
 * as a link to the item's permalink).
 *
 * @since 1.2.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses bp_core_time_since()
 * @uses apply_filters_ref_array() To call the 'bp_activity_time_since' hook.
 * @uses bp_is_single_activity()
 * @uses bp_activity_get_permalink()
 * @uses esc_attr__()
 * @uses apply_filters_ref_array() To call the 'bp_activity_permalink' hook.
 * @uses apply_filters() To call the 'bp_insert_activity_meta' hook.
 *
 * @param string $content The activity content.
 * @return string The activity content with the metadata string attached.
 */
function bp_insert_activity_meta( $content = '' ) {
	global $activities_template;

	// Strip any legacy time since placeholders from BP 1.0-1.1.
	$new_content = str_replace( '<span class="time-since">%s</span>', '', $content );

	// Get the time since this activity was recorded.
	$date_recorded  = bp_core_time_since( $activities_template->activity->date_recorded );

	/**
	 * Filters the activity item time since markup.
	 *
	 * @since 1.2.0
	 *
	 * @param array $value Array containing the time since markup and the current activity component.
	 */
	$time_since = apply_filters_ref_array( 'bp_activity_time_since', array(
		'<span class="time-since">' . $date_recorded . '</span>',
		&$activities_template->activity
	) );

	// Insert the permalink.
	if ( ! bp_is_single_activity() ) {

		// Setup variables for activity meta.
		$activity_permalink = bp_activity_get_permalink( $activities_template->activity->id, $activities_template->activity );
		$activity_meta      = sprintf( '%1$s <a href="%2$s" class="view activity-time-since" title="%3$s">%4$s</a>',
			$new_content,
			$activity_permalink,
			esc_attr__( 'View Discussion', 'buddypress' ),
			$time_since
		);

		/**
		 * Filters the activity permalink to be added to the activity content.
		 *
		 * @since 1.2.0
		 *
		 * @param array $value Array containing the html markup for the activity permalink, after being parsed by
		 *                     sprintf and current activity component.
		 */
		$new_content = apply_filters_ref_array( 'bp_activity_permalink', array(
			$activity_meta,
			&$activities_template->activity
		) );
	} else {
		$new_content .= str_pad( $time_since, strlen( $time_since ) + 2, ' ', STR_PAD_BOTH );
	}

	/**
	 * Filters the activity content after activity metadata has been attached.
	 *
	 * @since 1.2.0
	 *
	 * @param string $content Activity content with the activity metadata added.
	 */
	return apply_filters( 'bp_insert_activity_meta', $new_content, $content );
}

/**
 * Determine if the current user can delete an activity item.
 *
 * @since 1.2.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses apply_filters() To call the 'bp_activity_user_can_delete' hook.
 *
 * @param object|bool $activity Optional. Falls back on the current item in the loop.
 * @return bool True if can delete, false otherwise.
 */
function bp_activity_user_can_delete( $activity = false ) {
	global $activities_template;

	// Try to use current activity if none was passed.
	if ( empty( $activity ) && ! empty( $activities_template->activity ) ) {
		$activity = $activities_template->activity;
	}

	// If current_comment is set, we'll use that in place of the main activity.
	if ( isset( $activity->current_comment ) ) {
		$activity = $activity->current_comment;
	}

	// Assume the user cannot delete the activity item.
	$can_delete = false;

	// Only logged in users can delete activity.
	if ( is_user_logged_in() ) {

		// Community moderators can always delete activity (at least for now).
		if ( bp_current_user_can( 'bp_moderate' ) ) {
			$can_delete = true;
		}

		// Users are allowed to delete their own activity. This is actually
		// quite powerful, because doing so also deletes all comments to that
		// activity item. We should revisit this eventually.
		if ( isset( $activity->user_id ) && ( (int) $activity->user_id === bp_loggedin_user_id() ) ) {
			$can_delete = true;
		}

		// Viewing a single item, and this user is an admin of that item.
		if ( bp_is_single_item() && bp_is_item_admin() ) {
			$can_delete = true;
		}
	}

	/**
	 * Filters whether the current user can delete an activity item.
	 *
	 * @since 1.5.0
	 *
	 * @param bool   $can_delete Whether the user can delete the item.
	 * @param object $activity   Current activity item object.
	 */
	return (bool) apply_filters( 'bp_activity_user_can_delete', $can_delete, $activity );
}

/**
 * Output the activity parent content.
 *
 * @since 1.2.0
 *
 * @see bp_get_activity_parent_content() for a description of arguments.
 * @uses bp_get_activity_parent_content()
 *
 * @param array|string $args See {@link bp_get_activity_parent_content} for description.
 */
function bp_activity_parent_content( $args = '' ) {
	echo bp_get_activity_parent_content($args);
}

	/**
	 * Return the activity content.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_parent_content' hook.
	 *
	 * @param string $args Unused. Left over from an earlier implementation.
	 * @return mixed False on failure, otherwise the activity parent content.
	 */
	function bp_get_activity_parent_content( $args = '' ) {
		global $activities_template;

		// Bail if no activity on no item ID.
		if ( empty( $activities_template->activity ) || empty( $activities_template->activity->item_id ) ) {
			return false;
		}

		// Get the ID of the parent activity content.
		$parent_id = (int) $activities_template->activity->item_id;

		// Bail if no parent content.
		if ( empty( $activities_template->activity_parents[ $parent_id ] ) ) {
			return false;
		}

		// Bail if no action.
		if ( empty( $activities_template->activity_parents[ $parent_id ]->action ) ) {
			return false;
		}

		// Content always includes action.
		$content = $activities_template->activity_parents[ $parent_id ]->action;

		// Maybe append activity content, if it exists.
		if ( ! empty( $activities_template->activity_parents[ $parent_id ]->content ) ) {
			$content .= ' ' . $activities_template->activity_parents[ $parent_id ]->content;
		}

		// Remove the time since content for backwards compatibility.
		$content = str_replace( '<span class="time-since">%s</span>', '', $content );

		// Remove images.
		$content = preg_replace( '/<img[^>]*>/Ui', '', $content );

		/**
		 * Filters the activity parent content.
		 *
		 * @since 1.2.0
		 *
		 * @param string $content Content set to be displayed as parent content.
		 */
		return apply_filters( 'bp_get_activity_parent_content', $content );
	}

/**
 * Output the parent activity's user ID.
 *
 * @since 1.7.0
 */
function bp_activity_parent_user_id() {
	echo bp_get_activity_parent_user_id();
}

	/**
	 * Return the parent activity's user ID.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Activity_Template $activities_template
	 *
	 * @return bool|int False if parent activity can't be found, otherwise
	 *                  the parent activity's user ID.
	 */
	function bp_get_activity_parent_user_id() {
		global $activities_template;

		// Bail if no activity on no item ID.
		if ( empty( $activities_template->activity ) || empty( $activities_template->activity->item_id ) ) {
			return false;
		}

		// Get the ID of the parent activity content.
		$parent_id = (int) $activities_template->activity->item_id;

		// Bail if no parent item.
		if ( empty( $activities_template->activity_parents[ $parent_id ] ) ) {
			return false;
		}

		// Bail if no parent user ID.
		if ( empty( $activities_template->activity_parents[ $parent_id ]->user_id ) ) {
			return false;
		}

		$retval = $activities_template->activity_parents[ $parent_id ]->user_id;

		/**
		 * Filters the activity parent item's user ID.
		 *
		 * @since 1.7.0
		 *
		 * @param int $retval ID for the activity parent's user.
		 */
		return (int) apply_filters( 'bp_get_activity_parent_user_id', $retval );
	}

/**
 * Output whether or not the current activity is in a current user's favorites.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_is_favorite()
 */
function bp_activity_is_favorite() {
	echo bp_get_activity_is_favorite();
}

	/**
	 * Return whether the current activity is in a current user's favorites.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_is_favorite' hook.
	 *
	 * @return bool True if user favorite, false otherwise.
	 */
	function bp_get_activity_is_favorite() {
		global $activities_template;

		/**
		 * Filters whether the current activity item is in the current user's favorites.
		 *
		 * @since 1.2.0
		 *
		 * @param bool $value Whether or not the current activity item is in the current user's favorites.
		 */
 		return (bool) apply_filters( 'bp_get_activity_is_favorite', in_array( $activities_template->activity->id, (array) $activities_template->my_favs ) );
	}

/**
 * Output the comment markup for an activity item.
 *
 * @since 1.2.0
 *
 * @todo deprecate $args param
 *
 * @param array|string $args See {@link bp_activity_get_comments} for description.
 */
function bp_activity_comments( $args = '' ) {
	echo bp_activity_get_comments( $args );
}

	/**
	 * Get the comment markup for an activity item.
	 *
	 * @since 1.2.0
	 *
	 * @todo deprecate $args param
	 * @todo Given that checks for children already happen in bp_activity_recurse_comments(),
	 *       this function can probably be streamlined or removed.
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_activity_recurse_comments()
	 *
	 * @param string $args Unused. Left over from an earlier implementation.
	 * @return bool
	 */
	function bp_activity_get_comments( $args = '' ) {
		global $activities_template;

		if ( empty( $activities_template->activity->children ) ) {
			return false;
		}

		bp_activity_recurse_comments( $activities_template->activity );
	}

		/**
		 * Loops through a level of activity comments and loads the template for each.
		 *
		 * Note: The recursion itself used to happen entirely in this function. Now it is
		 * split between here and the comment.php template.
		 *
		 * @since 1.2.0
		 *
		 * @global object $activities_template {@link BP_Activity_Template}
		 * @uses locate_template()
		 *
		 * @param object $comment The activity object currently being recursed.
		 * @return bool|string
		 */
		function bp_activity_recurse_comments( $comment ) {
			global $activities_template;

			if ( empty( $comment ) ) {
				return false;
			}

			if ( empty( $comment->children ) ) {
				return false;
			}

			/**
			 * Filters the opening tag for the template that lists activity comments.
			 *
			 * @since 1.6.0
			 *
			 * @param string $value Opening tag for the HTML markup to use.
			 */
			echo apply_filters( 'bp_activity_recurse_comments_start_ul', '<ul>' );
			foreach ( (array) $comment->children as $comment_child ) {

				// Put the comment into the global so it's available to filters.
				$activities_template->activity->current_comment = $comment_child;

				$template = bp_locate_template( 'activity/comment.php', false, false );

				// Backward compatibility. In older versions of BP, the markup was
				// generated in the PHP instead of a template. This ensures that
				// older themes (which are not children of bp-default and won't
				// have the new template) will still work.
				if ( !$template ) {
					$template = buddypress()->plugin_dir . '/bp-themes/bp-default/activity/comment.php';
				}

				load_template( $template, false );

				unset( $activities_template->activity->current_comment );
			}

			/**
			 * Filters the closing tag for the template that list activity comments.
			 *
			 * @since  1.6.0
			 *
			 * @param string $value Closing tag for the HTML markup to use.
			 */
			echo apply_filters( 'bp_activity_recurse_comments_end_ul', '</ul>' );
		}

/**
 * Utility function that returns the comment currently being recursed.
 *
 * @since 1.5.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses apply_filters() To call the 'bp_activity_current_comment' hook.
 *
 * @return object|bool $current_comment The activity comment currently being
 *                                      displayed. False on failure.
 */
function bp_activity_current_comment() {
	global $activities_template;

	$current_comment = !empty( $activities_template->activity->current_comment )
		? $activities_template->activity->current_comment
		: false;

	/**
	 * Filters the current comment being recursed.
	 *
	 * @since 1.5.0
	 *
	 * @param object|bool $current_comment The activity comment currently being displayed. False on failure.
	 */
	return apply_filters( 'bp_activity_current_comment', $current_comment );
}


/**
 * Output the ID of the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_id()
 */
function bp_activity_comment_id() {
	echo bp_get_activity_comment_id();
}

	/**
	 * Return the ID of the activity comment currently being displayed.
	 *
	 * @since 1.5.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_activity_comment_id' hook.
	 *
	 * @return int|bool $comment_id The ID of the activity comment currently
	 *                              being displayed, false if none is found.
	 */
	function bp_get_activity_comment_id() {
		global $activities_template;

		$comment_id = isset( $activities_template->activity->current_comment->id ) ? $activities_template->activity->current_comment->id : false;

		/**
		 * Filters the ID of the activity comment currently being displayed.
		 *
		 * @since 1.5.0
		 *
		 * @param int|bool $comment_id ID for the comment currently being displayed.
		 */
		return apply_filters( 'bp_activity_comment_id', $comment_id );
	}

/**
 * Output the ID of the author of the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_user_id()
 */
function bp_activity_comment_user_id() {
	echo bp_get_activity_comment_user_id();
}

	/**
	 * Return the ID of the author of the activity comment currently being displayed.
	 *
	 * @since 1.5.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_activity_comment_user_id' hook.
	 *
	 * @return int|bool $user_id The user_id of the author of the displayed
	 *                           activity comment. False on failure.
	 */
	function bp_get_activity_comment_user_id() {
		global $activities_template;

		$user_id = isset( $activities_template->activity->current_comment->user_id ) ? $activities_template->activity->current_comment->user_id : false;

		/**
		 * Filters the ID of the author of the activity comment currently being displayed.
		 *
		 * @since 1.5.0
		 *
		 * @param int|bool $user_id ID for the author of the comment currently being displayed.
		 */
		return apply_filters( 'bp_activity_comment_user_id', $user_id );
	}

/**
 * Output the author link for the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_user_link()
 */
function bp_activity_comment_user_link() {
	echo bp_get_activity_comment_user_link();
}

	/**
	 * Return the author link for the activity comment currently being displayed.
	 *
	 * @since 1.5.0
	 *
	 * @uses bp_core_get_user_domain()
	 * @uses bp_get_activity_comment_user_id()
	 * @uses apply_filters() To call the 'bp_activity_comment_user_link' hook.
	 *
	 * @return string $user_link The URL of the activity comment author's profile.
	 */
	function bp_get_activity_comment_user_link() {
		$user_link = bp_core_get_user_domain( bp_get_activity_comment_user_id() );

		/**
		 * Filters the author link for the activity comment currently being displayed.
		 *
		 * @since 1.5.0
		 *
		 * @param string $user_link Link for the author of the activity comment currently being displayed.
		 */
		return apply_filters( 'bp_activity_comment_user_link', $user_link );
	}

/**
 * Output the author name for the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_name()
 */
function bp_activity_comment_name() {
	echo bp_get_activity_comment_name();
}

	/**
	 * Return the author name for the activity comment currently being displayed.
	 *
	 * The use of the 'bp_acomment_name' filter is deprecated. Please use
	 * 'bp_activity_comment_name'.
	 *
	 * @since 1.5.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_acomment_name' hook.
	 * @uses apply_filters() To call the 'bp_activity_comment_name' hook.
	 *
	 * @return string $name The full name of the activity comment author.
	 */
	function bp_get_activity_comment_name() {
		global $activities_template;

		if ( isset( $activities_template->activity->current_comment->user_fullname ) ) {

			$name = apply_filters( 'bp_acomment_name', $activities_template->activity->current_comment->user_fullname, $activities_template->activity->current_comment );  // Backward compatibility.
		} else {
			$name = $activities_template->activity->current_comment->display_name;
		}

		/**
		 * Filters the name of the author for the activity comment.
		 *
		 * @since 1.5.0
		 *
		 * @param string $name Name to be displayed with the activity comment.
		 */
		return apply_filters( 'bp_activity_comment_name', $name );
	}

/**
 * Output the formatted date_recorded of the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_date_recorded()
 */
function bp_activity_comment_date_recorded() {
	echo bp_get_activity_comment_date_recorded();
}

	/**
	 * Return the formatted date_recorded for the activity comment currently being displayed.
	 *
	 * @since 1.5.0
	 *
	 * @uses bp_core_time_since()
	 * @uses apply_filters() To call the 'bp_activity_comment_date_recorded' hook.
	 *
	 * @return string|bool $date_recorded Time since the activity was recorded,
	 *                                    in the form "%s ago". False on failure.
	 */
	function bp_get_activity_comment_date_recorded() {

		/**
		 * Filters the recorded date of the activity comment currently being displayed.
		 *
		 * @since 1.5.0
		 *
		 * @param string|bool Date for the activity comment currently being displayed.
		 */
		return apply_filters( 'bp_activity_comment_date_recorded', bp_core_time_since( bp_get_activity_comment_date_recorded_raw() ) );
	}

/**
 * Output the date_recorded of the activity comment currently being displayed.
 *
 * @since 2.3.0
 *
 * @uses bp_get_activity_comment_date_recorded()
 */
function bp_activity_comment_date_recorded_raw() {
	echo bp_get_activity_comment_date_recorded_raw();
}

	/**
	 * Return the date_recorded for the activity comment currently being displayed.
	 *
	 * @since 2.3.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_core_time_since()
	 * @uses apply_filters() To call the 'bp_activity_comment_date_recorded' hook.
	 *
	 * @return string|bool $date_recorded Time since the activity was recorded,
	 *                                    in the form "%s ago". False on failure.
	 */
	function bp_get_activity_comment_date_recorded_raw() {
		global $activities_template;

		/**
		 * Filters the raw recorded date of the activity comment currently being displayed.
		 *
		 * @since 2.3.0
		 *
		 * @param string|bool Raw date for the activity comment currently being displayed.
		 */
		return apply_filters( 'bp_activity_comment_date_recorded', $activities_template->activity->current_comment->date_recorded );
	}

/**
 * Output the 'delete' URL for the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_delete_link()
 */
function bp_activity_comment_delete_link() {
	echo bp_get_activity_comment_delete_link();
}

	/**
	 * Gets the 'delete' URL for the activity comment currently being displayed.
	 *
	 * @since 1.5.0
	 *
	 * @uses wp_nonce_url()
	 * @uses bp_get_root_domain()
	 * @uses bp_get_activity_slug()
	 * @uses bp_get_activity_comment_id()
	 * @uses apply_filters() To call the 'bp_activity_comment_delete_link' hook.
	 *
	 * @return string $link The nonced URL for deleting the current
	 *                      activity comment.
	 */
	function bp_get_activity_comment_delete_link() {
		$link = wp_nonce_url( bp_get_root_domain() . '/' . bp_get_activity_slug() . '/delete/' . bp_get_activity_comment_id() . '?cid=' . bp_get_activity_comment_id(), 'bp_activity_delete_link' );

		/**
		 * Filters the link used for deleting the activity comment currently being displayed.
		 *
		 * @since 1.5.0
		 *
		 * @param string $link Link to use for deleting the currently displayed activity comment.
		 */
		return apply_filters( 'bp_activity_comment_delete_link', $link );
	}

/**
 * Output the content of the activity comment currently being displayed.
 *
 * @since 1.5.0
 *
 * @uses bp_get_activity_comment_content()
 */
function bp_activity_comment_content() {
	echo bp_get_activity_comment_content();
}

	/**
	 * Return the content of the activity comment currently being displayed.
	 *
	 * The content is run through two filters. 'bp_get_activity_content'
	 * will apply all filters applied to activity items in general. Use
	 * 'bp_activity_comment_content' to modify the content of activity
	 * comments only.
	 *
	 * @since 1.5.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_content' hook.
	 * @uses apply_filters() To call the 'bp_activity_comment_content' hook.
	 *
	 * @return string $content The content of the current activity comment.
	 */
	function bp_get_activity_comment_content() {
		global $activities_template;

		/** This filter is documented in bp-activity/bp-activity-template.php */
		$content = apply_filters( 'bp_get_activity_content', $activities_template->activity->current_comment->content );

		/**
		 * Filters the content of the current activity comment.
		 *
		 * @since 1.2.0
		 *
		 * @param string $content The content of the current activity comment.
		 */
		return apply_filters( 'bp_activity_comment_content', $content );
	}

/**
 * Output the activity comment count.
 *
 * @since 1.2.0
 *
 * @uses bp_activity_get_comment_count()
 */
function bp_activity_comment_count() {
	echo bp_activity_get_comment_count();
}

	/**
	 * Return the comment count of an activity item.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_activity_recurse_comment_count()
	 * @uses apply_filters() To call the 'bp_activity_get_comment_count' hook.
	 *
	 * @param array|null $deprecated Deprecated.
	 * @return int $count The activity comment count.
	 */
	function bp_activity_get_comment_count( $deprecated = null ) {
		global $activities_template;

		// Deprecated notice about $args.
		if ( ! empty( $deprecated ) ) {
			_deprecated_argument( __FUNCTION__, '1.2', sprintf( __( '%1$s no longer accepts arguments. See the inline documentation at %2$s for more details.', 'buddypress' ), __FUNCTION__, __FILE__ ) );
		}

		// Get the count using the purpose-built recursive function.
		$count = ! empty( $activities_template->activity->children )
			? bp_activity_recurse_comment_count( $activities_template->activity )
			: 0;

		/**
		 * Filters the activity comment count.
		 *
		 * @since 1.2.0
		 *
		 * @param int $count The activity comment count.
		 */
		return apply_filters( 'bp_activity_get_comment_count', (int) $count );
	}

		/**
		 * Return the total number of comments to the current comment.
		 *
		 * This function recursively adds the total number of comments each
		 * activity child has, and returns them.
		 *
		 * @since 1.2.0
		 *
		 * @uses bp_activity_recurse_comment_count()
		 * @uses apply_filters() To call the 'bp_activity_recurse_comment_count' hook.
		 *
		 * @param object $comment Activity comment object.
		 * @param int    $count The current iteration count.
		 * @return int $count The activity comment count.
		 */
		function bp_activity_recurse_comment_count( $comment, $count = 0 ) {

			// Copy the count.
			$new_count = $count;

			// Loop through children and recursively count comments.
			if ( ! empty( $comment->children ) ) {
				foreach ( (array) $comment->children as $comment ) {
					$new_count++;
					$new_count = bp_activity_recurse_comment_count( $comment, $new_count );
				}
			}

			/**
			 * Filters the total number of comments for the current comment.
			 *
			 * @since 2.1.0
			 *
			 * @param int    $new_count New total count for the current comment.
			 * @param object $comment   Activity comment object.
			 * @param int    $count     Current iteration count for the current comment.
			 */
			return apply_filters( 'bp_activity_recurse_comment_count', $new_count, $comment, $count );
		}

/**
 * Output the depth of the current activity comment.
 *
 * @since 2.0.0
 */
function bp_activity_comment_depth() {
	echo bp_activity_get_comment_depth();
}
	/**
	 * Return the current activity comment depth.
	 *
	 * @since 2.0.0
	 *
	 * @return int $depth Depth for the current activity comment.
	 */
	function bp_activity_get_comment_depth() {
		global $activities_template;

		/**
		 * Filters the comment depth of the current activity comment.
		 *
		 * @since 2.0.0
		 *
		 * @param int $depth Depth for the current activity comment.
		 */
		return apply_filters( 'bp_activity_get_comment_depth', $activities_template->activity->current_comment->depth );
	}

/**
 * Output the activity comment link.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_comment_link()
 */
function bp_activity_comment_link() {
	echo bp_get_activity_comment_link();
}

	/**
	 * Return the activity comment link.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_comment_link' hook.
	 *
	 * @return string The activity comment link.
	 */
	function bp_get_activity_comment_link() {
		global $activities_template;

		/**
		 * Filters the comment link for the current activity comment.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value Constructed URL parameters with activity IDs.
		 */
		return apply_filters( 'bp_get_activity_comment_link', '?ac=' . $activities_template->activity->id . '/#ac-form-' . $activities_template->activity->id );
	}

/**
 * Output the activity comment form no JavaScript display CSS.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_comment_form_nojs_display()
 */
function bp_activity_comment_form_nojs_display() {
	echo bp_get_activity_comment_form_nojs_display();
}

	/**
	 * Return the activity comment form no JavaScript display CSS.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 *
	 * @return string|bool The activity comment form no JavaScript
	 *                     display CSS. False on failure.
	 */
	function bp_get_activity_comment_form_nojs_display() {
		global $activities_template;

		if ( isset( $_GET['ac'] ) && ( $_GET['ac'] === ( $activities_template->activity->id . '/' ) ) ) {
			return 'style="display: block"';
		}

		return false;
	}

/**
 * Output the activity comment form action.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_comment_form_action()
 */
function bp_activity_comment_form_action() {
	echo bp_get_activity_comment_form_action();
}

	/**
	 * Return the activity comment form action.
	 *
	 * @since 1.2.0
	 *
	 * @uses home_url()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_comment_form_action' hook.
	 *
	 * @return string The activity comment form action.
	 */
	function bp_get_activity_comment_form_action() {

		/**
		 * Filters the activity comment form action URL.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value URL to use in the comment form's action attribute.
		 */
		return apply_filters( 'bp_get_activity_comment_form_action', home_url( bp_get_activity_root_slug() . '/reply/' ) );
	}

/**
 * Output the activity permalink ID.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_permalink_id()
 */
function bp_activity_permalink_id() {
	echo bp_get_activity_permalink_id();
}

	/**
	 * Return the activity permalink ID.
	 *
	 * @since 1.2.0
	 *
	 * @uses apply_filters() To call the 'bp_get_activity_permalink_id' hook.
	 *
	 * @return string The activity permalink ID.
	 */
	function bp_get_activity_permalink_id() {

		/**
		 * Filters the activity action permalink ID.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value Current action for the activity item.
		 */
		return apply_filters( 'bp_get_activity_permalink_id', bp_current_action() );
	}

/**
 * Output the activity thread permalink.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_permalink_id()
 */
function bp_activity_thread_permalink() {
	echo bp_get_activity_thread_permalink();
}

	/**
	 * Return the activity thread permalink.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_activity_get_permalink()
	 * @uses apply_filters() To call the 'bp_get_activity_thread_permalink' hook.
	 *
	 * @return string $link The activity thread permalink.
	 */
	function bp_get_activity_thread_permalink() {
		global $activities_template;

		$link = bp_activity_get_permalink( $activities_template->activity->id, $activities_template->activity );

		/**
		 * Filters the activity thread permalink.
		 *
		 * @since 1.2.0
		 *
		 * @param string $link The activity thread permalink.
		 */
		return apply_filters( 'bp_get_activity_thread_permalink', $link );
	}

/**
 * Output the activity comment permalink.
 *
 * @since 1.8.0
 *
 * @uses bp_get_activity_permalink_id()
 */
function bp_activity_comment_permalink() {
	echo bp_get_activity_comment_permalink();
}
	/**
	 * Return the activity comment permalink.
	 *
	 * @since 1.8.0
	 *
	 * @uses bp_activity_get_permalink()
	 * @uses apply_filters() To call the 'bp_get_activity_comment_permalink' hook.
	 *
	 * @return string $link The activity comment permalink.
	 */
	function bp_get_activity_comment_permalink() {
		global $activities_template;

		// Check that comment exists.
		$comment_id = isset( $activities_template->activity->current_comment->id )
			? $activities_template->activity->current_comment->id
			: 0;

		// Setup the comment link.
		$comment_link = ! empty( $comment_id )
			? '#acomment-' .$comment_id
			: false;

		// Append comment ID to end of activity permalink.
		$link = bp_activity_get_permalink( $activities_template->activity->id, $activities_template->activity ) . $comment_link;

		/**
		 * Filters the activity comment permalink.
		 *
		 * @since 1.8.0
		 *
		 * @param string $link       Activity comment permalink.
		 * @param int    $comment_id ID for the current activity comment.
		 */
		return apply_filters( 'bp_get_activity_comment_permalink', $link, $comment_id );
	}

/**
 * Output the activity favorite link.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_favorite_link()
 */
function bp_activity_favorite_link() {
	echo bp_get_activity_favorite_link();
}

	/**
	 * Return the activity favorite link.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses wp_nonce_url()
	 * @uses home_url()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_favorite_link' hook.
	 *
	 * @return string The activity favorite link.
	 */
	function bp_get_activity_favorite_link() {
		global $activities_template;

		/**
		 * Filters the activity favorite link.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value Constructed link for favoriting the activity comment.
		 */
		return apply_filters( 'bp_get_activity_favorite_link', wp_nonce_url( home_url( bp_get_activity_root_slug() . '/favorite/' . $activities_template->activity->id . '/' ), 'mark_favorite' ) );
	}

/**
 * Output the activity unfavorite link.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_unfavorite_link()
 */
function bp_activity_unfavorite_link() {
	echo bp_get_activity_unfavorite_link();
}

	/**
	 * Return the activity unfavorite link.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses wp_nonce_url()
	 * @uses home_url()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_unfavorite_link' hook.
	 *
	 * @return string The activity unfavorite link.
	 */
	function bp_get_activity_unfavorite_link() {
		global $activities_template;

		/**
		 * Filters the activity unfavorite link.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value Constructed link for unfavoriting the activity comment.
		 */
		return apply_filters( 'bp_get_activity_unfavorite_link', wp_nonce_url( home_url( bp_get_activity_root_slug() . '/unfavorite/' . $activities_template->activity->id . '/' ), 'unmark_favorite' ) );
	}

/**
 * Output the activity CSS class.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_css_class()
 */
function bp_activity_css_class() {
	echo bp_get_activity_css_class();
}

	/**
	 * Return the current activity item's CSS class.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_activity_mini_activity_types' hook.
	 * @uses bp_activity_get_comment_count()
	 * @uses bp_activity_can_comment()
	 * @uses apply_filters() To call the 'bp_get_activity_css_class' hook.
	 *
	 * @return string The activity item's CSS class.
	 */
	function bp_get_activity_css_class() {
		global $activities_template;

		/**
		 * Filters the available mini activity actions available as CSS classes.
		 *
		 * @since 1.2.0
		 *
		 * @param array $value Array of classes used to determine classes applied to HTML element.
		 */
		$mini_activity_actions = apply_filters( 'bp_activity_mini_activity_types', array(
			'friendship_accepted',
			'friendship_created',
			'new_blog',
			'joined_group',
			'created_group',
			'new_member'
		) );

		$class = ' activity-item';

		if ( in_array( $activities_template->activity->type, (array) $mini_activity_actions ) || empty( $activities_template->activity->content ) ) {
			$class .= ' mini';
		}

		if ( bp_activity_get_comment_count() && bp_activity_can_comment() ) {
			$class .= ' has-comments';
		}

		/**
		 * Filters the determined classes to add to the HTML element.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Classes to be added to the HTML element.
		 */
		return apply_filters( 'bp_get_activity_css_class', $activities_template->activity->component . ' ' . $activities_template->activity->type . $class );
	}

/**
 * Output the activity delete link.
 *
 * @since 1.1.0
 *
 * @uses bp_get_activity_delete_link()
 */
function bp_activity_delete_link() {
	echo bp_get_activity_delete_link();
}

	/**
	 * Return the activity delete link.
	 *
	 * @since 1.1.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_get_root_domain()
	 * @uses bp_get_activity_root_slug()
	 * @uses bp_is_activity_component()
	 * @uses bp_current_action()
	 * @uses wp_get_referer()
	 * @uses wp_nonce_url()
	 * @uses apply_filters() To call the 'bp_get_activity_delete_link' hook.
	 *
	 * @return string $link Activity delete link. Contains $redirect_to arg
	 *                      if on single activity page.
	 */
	function bp_get_activity_delete_link() {

		$url   = bp_get_activity_delete_url();
		$class = 'delete-activity';

		// Determine if we're on a single activity page, and customize accordingly.
		if ( bp_is_activity_component() && is_numeric( bp_current_action() ) ) {
			$class = 'delete-activity-single';
		}

		$link = '<a href="' . esc_url( $url ) . '" class="button item-button bp-secondary-action ' . $class . ' confirm" rel="nofollow">' . __( 'Delete', 'buddypress' ) . '</a>';

		/**
		 * Filters the activity delete link.
		 *
		 * @since 1.1.0
		 *
		 * @param string $link Activity delete HTML link.
		 */
		return apply_filters( 'bp_get_activity_delete_link', $link );
	}

/**
 * Output the URL to delete a single activity stream item.
 *
 * @since 2.1.0
 *
 * @uses bp_get_activity_delete_link()
 */
function bp_activity_delete_url() {
	echo esc_url( bp_get_activity_delete_url() );
}
	/**
	 * Return the URL to delete a single activity item.
	 *
	 * @since 2.1.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses bp_get_root_domain()
	 * @uses bp_get_activity_root_slug()
	 * @uses bp_is_activity_component()
	 * @uses bp_current_action()
	 * @uses add_query_arg()
	 * @uses wp_get_referer()
	 * @uses wp_nonce_url()
	 * @uses apply_filters() To call the 'bp_get_activity_delete_link' hook.
	 *
	 * @return string $link Activity delete link. Contains $redirect_to arg
	 *                      if on single activity page.
	 */
	function bp_get_activity_delete_url() {
		global $activities_template;

		$url = trailingslashit( bp_get_root_domain() . '/' . bp_get_activity_root_slug() . '/delete/' . $activities_template->activity->id );

		// Determine if we're on a single activity page, and customize accordingly.
		if ( bp_is_activity_component() && is_numeric( bp_current_action() ) ) {
			$url = add_query_arg( array( 'redirect_to' => wp_get_referer() ), $url );
		}

		$url = wp_nonce_url( $url, 'bp_activity_delete_link' );

		/**
		 * Filters the activity delete URL.
		 *
		 * @since 2.1.0
		 *
		 * @param string $url Activity delete URL.
		 */
		return apply_filters( 'bp_get_activity_delete_url', $url );
	}

/**
 * Output the activity latest update link.
 *
 * @since 1.2.0
 *
 * @see bp_get_activity_latest_update() for description of parameters.
 * @uses bp_get_activity_latest_update()
 *
 * @param int $user_id See {@link bp_get_activity_latest_update()} for description.
 */
function bp_activity_latest_update( $user_id = 0 ) {
	echo bp_get_activity_latest_update( $user_id );
}

	/**
	 * Return the activity latest update link.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_is_user_inactive()
	 * @uses bp_core_is_user_deleted()
	 * @uses bp_get_user_meta()
	 * @uses apply_filters() To call the 'bp_get_activity_latest_update_excerpt' hook.
	 * @uses bp_create_excerpt()
	 * @uses bp_get_root_domain()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_latest_update' hook.
	 *
	 * @param int $user_id If empty, will fall back on displayed user.
	 * @return string|bool $latest_update The activity latest update link.
	 *                                    False on failure.
	 */
	function bp_get_activity_latest_update( $user_id = 0 ) {

		if ( empty( $user_id ) ) {
			$user_id = bp_displayed_user_id();
		}

		if ( bp_is_user_inactive( $user_id ) ) {
			return false;
		}

		if ( !$update = bp_get_user_meta( $user_id, 'bp_latest_update', true ) ) {
			return false;
		}

		/**
		 * Filters the latest update excerpt.
		 *
		 * @since 1.2.10
		 *
		 * @param string $value The excerpt for the latest update.
		 */
		$latest_update = apply_filters( 'bp_get_activity_latest_update_excerpt', trim( strip_tags( bp_create_excerpt( $update['content'], 358 ) ) ) );

		$latest_update = sprintf(
			'%s <a href="%s">%s</a>',
			$latest_update,
			esc_url_raw( bp_activity_get_permalink( $update['id'] ) ),
			esc_attr__( 'View', 'buddypress' )
		);

		/**
		 * Filters the latest update excerpt with view link appended to the end.
		 *
		 * @since 1.2.0
		 *
		 * @param string $latest_update The latest update with "view" link appended to it.
		 */
		return apply_filters( 'bp_get_activity_latest_update', $latest_update );
	}

/**
 * Output the activity filter links.
 *
 * @since 1.1.0
 *
 * @see bp_get_activity_filter_links() for description of parameters.
 * @uses bp_get_activity_filter_links()
 *
 * @param array|bool $args See {@link bp_get_activity_filter_links()} for description.
 */
function bp_activity_filter_links( $args = false ) {
	echo bp_get_activity_filter_links( $args );
}

	/**
	 * Return the activity filter links.
	 *
	 * @since 1.1.0
	 *
	 * @uses wp_parse_args()
	 * @uses BP_Activity_Activity::get_recorded_components() {@link BP_Activity_Activity}
	 * @uses esc_attr()
	 * @uses add_query_arg()
	 * @uses remove_query_arg()
	 * @uses apply_filters() To call the 'bp_get_activity_filter_link_href' hook.
	 * @uses apply_filters() To call the 'bp_get_activity_filter_links' hook.
	 *
	 * @param array|bool $args {
	 *     @type string $style The type of markup to use for the links.
	 *                         'list', 'paragraph', or 'span'. Default: 'list'.
	 * }
	 * @return string|bool $component_links The activity filter links.
	 *         False on failure.
	 */
	function bp_get_activity_filter_links( $args = false ) {

		$r = wp_parse_args( $args, array(
			'style' => 'list'
		) );

		// Define local variable.
		$component_links = array();

		// Fetch the names of components that have activity recorded in the DB.
		$components = BP_Activity_Activity::get_recorded_components();

		if ( empty( $components ) ) {
			return false;
		}

		foreach ( (array) $components as $component ) {

			// Skip the activity comment filter.
			if ( 'activity' == $component ) {
				continue;
			}

			if ( isset( $_GET['afilter'] ) && $component == $_GET['afilter'] ) {
				$selected = ' class="selected"';
			} else {
				$selected = '';
			}

			$component = esc_attr( $component );

			switch ( $r['style'] ) {
				case 'list':
					$tag = 'li';
					$before = '<li id="afilter-' . $component . '"' . $selected . '>';
					$after = '</li>';
				break;
				case 'paragraph':
					$tag = 'p';
					$before = '<p id="afilter-' . $component . '"' . $selected . '>';
					$after = '</p>';
				break;
				case 'span':
					$tag = 'span';
					$before = '<span id="afilter-' . $component . '"' . $selected . '>';
					$after = '</span>';
				break;
			}

			$link = add_query_arg( 'afilter', $component );
			$link = remove_query_arg( 'acpage' , $link );

			/**
			 * Filters the activity filter link URL for the current activity component.
			 *
			 * @since 1.1.0
			 *
			 * @param string $link      The URL for the current component.
			 * @param string $component The current component getting links constructed for.
			 */
			$link = apply_filters( 'bp_get_activity_filter_link_href', $link, $component );

			$component_links[] = $before . '<a href="' . esc_url( $link ) . '">' . ucwords( $component ) . '</a>' . $after;
		}

		$link = remove_query_arg( 'afilter' , $link );

		if ( isset( $_GET['afilter'] ) ) {
			$component_links[] = '<' . $tag . ' id="afilter-clear"><a href="' . esc_url( $link ) . '">' . __( 'Clear Filter', 'buddypress' ) . '</a></' . $tag . '>';
		}

		/**
		 * Filters all of the constructed filter links.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value All of the links to be displayed to the user.
		 */
 		return apply_filters( 'bp_get_activity_filter_links', implode( "\n", $component_links ) );
	}

/**
 * Determine if a comment can be made on an activity item.
 *
 * @since 1.2.0
 *
 * @global object $activities_template {@link BP_Activity_Template}
 * @uses bp_get_activity_action_name()
 * @uses apply_filters() To call the 'bp_activity_can_comment' hook.
 *
 * @return bool $can_comment True if item can receive comments.
 */
function bp_activity_can_comment() {
	global $activities_template;
	$bp = buddypress();

	// Assume activity can be commented on.
	$can_comment = true;

	// Determine ability to comment based on activity action name.
	$activity_action = bp_get_activity_action_name();

	$turn_off = 0;
	if ( ! empty( $activities_template->disable_blogforum_replies ) ) {
		$turn_off = 1;
	}

	$maybe_turn_off = array_fill_keys( array(
		'new_blog_post',
		'new_blog_comment',
		'new_forum_topic',
		'new_forum_post',
	), $turn_off );

	$maybe_turn_off['activity_comment'] = 1;

	// Fetch all the tracked post types once.
	if ( empty( $bp->activity->track ) ) {
		$bp->activity->track = bp_activity_get_post_types_tracking_args();
	}

	foreach ( $bp->activity->track as $action => $tracking_args ) {
		if ( empty( $tracking_args->activity_comment ) ) {
			$maybe_turn_off[ $action ] = $turn_off;
		}
	}

	$can_comment = empty( $maybe_turn_off[ $activity_action ] );

	/**
	 * Filters whether a comment can be made on an activity item.
	 *
	 * @since 1.5.0
	 *
	 * @param bool   $can_comment     Status on if activity can be commented on.
	 * @param string $activity_action Current activity action being checked on.
	 */
	return apply_filters( 'bp_activity_can_comment', $can_comment, $activity_action );
}

/**
 * Determine whether a comment can be made on an activity reply item.
 *
 * @since 1.5.0
 *
 * @param  bool|object $comment     Activity comment.
 * @return bool        $can_comment True if comment can receive comments,
 *                                  otherwise false.
 */
function bp_activity_can_comment_reply( $comment = false ) {

	// Assume activity can be commented on.
	$can_comment = true;

	// Check that comment exists.
	if ( empty( $comment ) ) {
		$comment = bp_activity_current_comment();
	}

	if ( ! empty( $comment ) ) {

		// Fall back on current comment in activity loop.
		$comment_depth = isset( $comment->depth )
			? intval( $comment->depth )
			: bp_activity_get_comment_depth();

		// Threading is turned on, so check the depth.
		if ( get_option( 'thread_comments' ) ) {
			$can_comment = (bool) ( $comment_depth < get_option( 'thread_comments_depth' ) );

		// No threading for comment replies if no threading for comments.
		} else {
			$can_comment = false;
		}
	}

	/**
	 * Filters whether a comment can be made on an activity reply item.
	 *
	 * @since 1.5.0
	 *
	 * @param bool   $can_comment Status on if activity reply can be commented on.
	 * @param string $comment     Current comment being checked on.
	 */
	return (bool) apply_filters( 'bp_activity_can_comment_reply', $can_comment, $comment );
}

/**
 * Determine whether favorites are allowed.
 *
 * Defaults to true, but can be modified by plugins.
 *
 * @since 1.5.0
 *
 * @uses apply_filters() To call the 'bp_activity_can_favorite' hook.
 *
 * @return bool True if comment can receive comments.
 */
function bp_activity_can_favorite() {

	/**
	 * Filters whether or not users can favorite activity items.
	 *
	 * @since 1.5.0
	 *
	 * @param bool $value Whether or not favoriting is enabled.
	 */
	return apply_filters( 'bp_activity_can_favorite', true );
}

/**
 * Output the total favorite count for a specified user.
 *
 * @since 1.2.0
 *
 * @see bp_get_total_favorite_count_for_user() for description of parameters.
 * @uses bp_get_total_favorite_count_for_user()
 *
 * @param int $user_id See {@link bp_get_total_favorite_count_for_user()}.
 */
function bp_total_favorite_count_for_user( $user_id = 0 ) {
	echo bp_get_total_favorite_count_for_user( $user_id );
}

	/**
	 * Return the total favorite count for a specified user.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_activity_total_favorites_for_user()
	 * @uses apply_filters() To call the 'bp_get_total_favorite_count_for_user' hook.
	 *
	 * @param int $user_id ID of user being queried. Default: displayed user ID.
	 * @return int The total favorite count for the specified user.
	 */
	function bp_get_total_favorite_count_for_user( $user_id = 0 ) {
		$retval = false;

		if ( bp_activity_can_favorite() ) {
			// Default to displayed user if none is passed.
			$user_id = empty( $user_id )
				? bp_displayed_user_id()
				: $user_id;

			// Get user meta if user ID exists.
			if ( ! empty( $user_id ) ) {
				$retval = bp_activity_total_favorites_for_user( $user_id );
			}
		}

		/**
		 * Filters the total favorite count for a user.
		 *
		 * @since 1.2.0
		 *
		 * @param int|bool $retval Total favorite count for a user. False on no favorites.
		 */
		return apply_filters( 'bp_get_total_favorite_count_for_user', $retval );
	}


/**
 * Output the total mention count for a specified user.
 *
 * @since 1.2.0
 *
 * @see bp_get_total_mention_count_for_user() for description of parameters.
 * @uses bp_get_total_favorite_count_for_user()
 *
 * @param int $user_id See {@link bp_get_total_mention_count_for_user()}.
 */
function bp_total_mention_count_for_user( $user_id = 0 ) {
	echo bp_get_total_mention_count_for_user( $user_id );
}

	/**
	 * Return the total mention count for a specified user.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_get_user_meta()
	 * @uses apply_filters() To call the 'bp_get_total_mention_count_for_user' hook.
	 *
	 * @param int $user_id ID of user being queried. Default: displayed user ID.
	 * @return int The total mention count for the specified user.
	 */
	function bp_get_total_mention_count_for_user( $user_id = 0 ) {

		// Default to displayed user if none is passed.
		$user_id = empty( $user_id )
			? bp_displayed_user_id()
			: $user_id;

		// Get user meta if user ID exists.
		$retval = ! empty( $user_id )
			? bp_get_user_meta( $user_id, 'bp_new_mention_count', true )
			: false;

		/**
		 * Filters the total mention count for a user.
		 *
		 * @since 1.2.0
		 *
		 * @param int|bool $retval Total mention count for a user. False on no mentions.
		 */
		return apply_filters( 'bp_get_total_mention_count_for_user', $retval );
	}

/**
 * Output the public message link for displayed user.
 *
 * @since 1.2.0
 *
 * @uses bp_get_send_public_message_link()
 */
function bp_send_public_message_link() {
	echo esc_url( bp_get_send_public_message_link() );
}

	/**
	 * Return the public message link for the displayed user.
	 *
	 * @since 1.2.0
	 *
	 * @uses is_user_logged_in()
	 * @uses bp_is_my_profile()
	 * @uses bp_is_user()
	 * @uses wp_nonce_url()
	 * @uses bp_get_activity_directory_permalink()
	 * @uses apply_filters() To call the 'bp_get_send_public_message_link' hook.
	 *
	 * @return string The public message link for the displayed user.
	 */
	function bp_get_send_public_message_link() {

		// No link if not logged in, not looking at someone else's profile.
		if ( ! is_user_logged_in() || ! bp_is_user() || bp_is_my_profile() ) {
			$retval = '';
		} else {
			$args   = array( 'r' => bp_get_displayed_user_mentionname() );
			$url    = add_query_arg( $args, bp_get_activity_directory_permalink() );
			$retval = wp_nonce_url( $url );
		}

		/**
		 * Filters the public message link for the displayed user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $retval The URL for the public message link.
		 */
		return apply_filters( 'bp_get_send_public_message_link', $retval );
	}

/**
 * Recurse through all activity comments and return the activity comment IDs.
 *
 * @since 2.0.0
 *
 * @param array $activity Array of activities generated from {@link bp_activity_get()}.
 * @param array $activity_ids Used for recursion purposes in this function.
 * @return array
 */
function bp_activity_recurse_comments_activity_ids( $activity = array(), $activity_ids = array() ) {
	if ( is_array( $activity ) && ! empty( $activity['activities'] ) ) {
		$activity = $activity['activities'][0];
	}

	if ( ! empty( $activity->children ) ) {
		foreach ($activity->children as $child ) {
			$activity_ids[] = $child->id;

			if( ! empty( $child->children ) ) {
				$activity_ids = bp_activity_recurse_comments_activity_ids( $child, $activity_ids );
			}
		}
	}

	return $activity_ids;
}

/**
 * Output the mentioned user display name.
 *
 * @since 1.2.0
 *
 * @see bp_get_mentioned_user_display_name() for description of parameters.
 * @uses bp_get_mentioned_user_display_name()
 *
 * @param int|string|bool $user_id_or_username See {@link bp_get_mentioned_user_display_name()}.
 */
function bp_mentioned_user_display_name( $user_id_or_username = false ) {
	echo bp_get_mentioned_user_display_name( $user_id_or_username );
}

	/**
	 * Returns the mentioned user display name.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_core_get_user_displayname()
	 * @uses apply_filters() To call the 'bp_get_mentioned_user_display_name' hook.
	 *
	 * @param int|string|bool $user_id_or_username User ID or username.
	 * @return string The mentioned user's display name.
	 */
	function bp_get_mentioned_user_display_name( $user_id_or_username = false ) {

		// Get user display name.
		$name = bp_core_get_user_displayname( $user_id_or_username );

		// If user somehow has no name, return this really lame string.
		if ( empty( $name ) ) {
			$name = __( 'a user', 'buddypress' );
		}

		/**
		 * Filters the mentioned user display name.
		 *
		 * @since 1.2.0
		 *
		 * @param string     $name                Display name for the mentioned user.
		 * @param int|string $user_id_or_username User ID or username use for query.
		 */
		return apply_filters( 'bp_get_mentioned_user_display_name', $name, $user_id_or_username );
	}

/**
 * Output button for sending a public message (an @-mention).
 *
 * @since 1.2.0
 *
 * @see bp_get_send_public_message_button() for description of parameters.
 * @uses bp_get_send_public_message_button()
 *
 * @param array|string $args See {@link bp_get_send_public_message_button()}.
 */
function bp_send_public_message_button( $args = '' ) {
	echo bp_get_send_public_message_button( $args );
}

	/**
	 * Return button for sending a public message (an @-mention).
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_get_send_public_message_link()
	 * @uses wp_parse_args()
	 * @uses bp_get_button()
	 * @uses apply_filters() To call the 'bp_get_send_public_message_button' hook.
	 *
	 * @param array|string $args {
	 *     All arguments are optional. See {@link BP_Button} for complete
	 *     descriptions.
	 *     @type string $id                Default: 'public_message'.
	 *     @type string $component         Default: 'activity'.
	 *     @type bool   $must_be_logged_in Default: true.
	 *     @type bool   $block_self        Default: true.
	 *     @type string $wrapper_id        Default: 'post-mention'.
	 *     @type string $link_href         Default: the public message link for
	 *                                     the current member in the loop.
	 *     @type string $link_title        Default: 'Send a public message on your
	 *                                     activity stream.'.
	 *     @type string $link_text         Default: 'Public Message'.
	 *     @type string $link_class        Default: 'activity-button mention'.
	 * }
	 * @return string The button for sending a public message.
	 */
	function bp_get_send_public_message_button( $args = '' ) {

		$r = bp_parse_args( $args, array(
			'id'                => 'public_message',
			'component'         => 'activity',
			'must_be_logged_in' => true,
			'block_self'        => true,
			'wrapper_id'        => 'post-mention',
			'link_href'         => bp_get_send_public_message_link(),
			'link_title'        => __( 'Send a public message on your activity stream.', 'buddypress' ),
			'link_text'         => __( 'Public Message', 'buddypress' ),
			'link_class'        => 'activity-button mention'
		) );

		/**
		 * Filters the public message button HTML.
		 *
		 * @since 1.2.10
		 *
		 * @param array $r Array of arguments for the public message button HTML.
		 */
		return bp_get_button( apply_filters( 'bp_get_send_public_message_button', $r ) );
	}

/**
 * Output the activity post form action.
 *
 * @since 1.2.0
 *
 * @uses bp_get_activity_post_form_action()
 */
function bp_activity_post_form_action() {
	echo bp_get_activity_post_form_action();
}

	/**
	 * Return the activity post form action.
	 *
	 * @since 1.2.0
	 *
	 * @uses home_url()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_activity_post_form_action' hook.
	 *
	 * @return string The activity post form action.
	 */
	function bp_get_activity_post_form_action() {

		/**
		 * Filters the action url used for the activity post form.
		 *
		 * @since 1.2.0
		 *
		 * @param string $value URL to be used for the activity post form.
		 */
		return apply_filters( 'bp_get_activity_post_form_action', home_url( bp_get_activity_root_slug() . '/post/' ) );
	}

/**
 * Echo a list of linked avatars of users who have commented on the current activity item.
 *
 * Use this function to easily output activity comment authors' avatars.
 *
 * Avatars are wrapped in <li> elements, but you've got to provide your own
 * <ul> or <ol> wrapper markup.
 *
 * @since 1.7.0
 *
 * @see bp_core_fetch_avatar() for a description of arguments.
 *
 * @param array $args See {@link bp_core_fetch_avatar()}.
 */
function bp_activity_comments_user_avatars( $args = array() ) {

	$r = bp_parse_args( $args, array(
		'height' => false,
		'html'   => true,
		'type'   => 'thumb',
		'width'  => false,
	) );

	// Get the user IDs of everyone who has left a comment to the current activity item.
	$user_ids = bp_activity_get_comments_user_ids();
	$output   = array();
	$retval   = '';

	if ( ! empty( $user_ids ) ) {
		foreach ( (array) $user_ids as $user_id ) {

			// Skip an empty user ID.
			if ( empty( $user_id ) ) {
				continue;
			}

			// Get profile link for this user.
			$profile_link = bp_core_get_user_domain( $user_id );

			// Get avatar for this user.
			$image_html   = bp_core_fetch_avatar( array(
				'item_id' => $user_id,
				'height'  => $r['height'],
				'html'    => $r['html'],
				'type'    => $r['type'],
				'width'   => $r['width']
			) );

			// If user has link & avatar, add them to the output array.
			if ( ! empty( $profile_link ) && ! empty( $image_html ) ) {
				$output[] = sprintf( '<a href="%1$s">%2$s</a>', esc_url( $profile_link ), $image_html );
			}
		}

		// If output array is not empty, wrap everything in some list items.
		if ( ! empty( $output ) ) {
			$retval = '<li>' . implode( '</li><li>', $output ) . '</li>';
		}
	}

	/**
	 * Filters the list of linked avatars for users who have commented on the current activity item.
	 *
	 * @since 1.7.0
	 *
	 * @param string $retval HTML markup for the list of avatars.
	 * @param array  $r      Array of arguments used for each avatar.
	 * @param array  $output Array of each avatar found, before imploded into single string.
	 */
	echo apply_filters( 'bp_activity_comments_user_avatars', $retval, $r, $output );
}

/**
 * Return the IDs of every user who's left a comment on the current activity item.
 *
 * @since 1.7.0
 *
 * @return bool|array An array of IDs, or false if none are found.
 */
function bp_activity_get_comments_user_ids() {
	global $activities_template;

	$user_ids = ! empty( $activities_template->activity->children )
		? (array) bp_activity_recurse_comments_user_ids( $activities_template->activity->children )
		: array();

	/**
	 * Filters the list of user IDs for the current activity item.
	 *
	 * @since 1.7.0
	 *
	 * @param array $value Array of unique user IDs for the current activity item.
	 */
	return apply_filters( 'bp_activity_get_comments_user_ids', array_unique( $user_ids ) );
}

	/**
	 * Recurse through all activity comments and collect the IDs of the users who wrote them.
	 *
	 * @since 1.7.0
	 *
	 * @param array $comments Array of {@link BP_Activity_Activity} items.
	 * @return array Array of user IDs.
	 */
	function bp_activity_recurse_comments_user_ids( array $comments = array() ) {

		// Default user ID's array.
		$user_ids = array();

		// Loop through comments and try to get user ID's.
		if ( ! empty( $comments ) ) {
			foreach ( $comments as $comment ) {

				// If a user is a spammer, their activity items will have been
				// automatically marked as spam. Skip these.
				if ( ! empty( $comment->is_spam ) ) {
					continue;
				}

				// Add user ID to array.
				$user_ids[] = $comment->user_id;

				// Check for commentception.
				if ( ! empty( $comment->children ) ) {
					$user_ids = array_merge( $user_ids, bp_activity_recurse_comments_user_ids( $comment->children ) );
				}
			}
		}

		/**
		 * Filters the list of user IDs for the current activity comment item.
		 *
		 * @since 2.1.0
		 *
		 * @param array $user_ids Array of user IDs for the current activity comment item.
		 * @param array $comments Array of comments being checked for user IDs.
		 */
		return apply_filters( 'bp_activity_recurse_comments_user_ids', $user_ids, $comments );
	}

/**
 * Output the mentionname for the displayed user.
 *
 * @since 1.9.0
 */
function bp_displayed_user_mentionname() {
	echo bp_get_displayed_user_mentionname();
}
	/**
	 * Get the mentionname for the displayed user.
	 *
	 * @since 1.9.0
	 *
	 * @return string Mentionname for the displayed user, if available.
	 */
	function bp_get_displayed_user_mentionname() {

		/**
		 * Filters the mentionname for the displayed user.
		 *
		 * @since 1.9.0
		 *
		 * @param string $value The mentionanme for the displayed user.
		 */
		return apply_filters( 'bp_get_displayed_user_mentionname', bp_activity_get_user_mentionname( bp_displayed_user_id() ) );
	}

/**
 * Echo a list of all registered activity types for use in dropdowns or checkbox lists.
 *
 * @since 1.7.0
 *
 * @param string       $output Optional. Either 'select' or 'checkbox'. Default: 'select'.
 * @param array|string $args {
 *     Optional extra arguments.
 *     @type string       $checkbox_name When returning checkboxes, sets the 'name'
 *                                       attribute.
 *     @type array|string $selected      A list of types that should be checked/
 *                                       selected.
 * }
 */
function bp_activity_types_list( $output = 'select', $args = '' ) {

	$args = bp_parse_args( $args, array(
		'checkbox_name' => 'bp_activity_types',
		'selected'      => array(),
	) );

	$activities = bp_activity_get_types();
	natsort( $activities );

	// Loop through the activity types and output markup.
	foreach ( $activities as $type => $description ) {

		// See if we need to preselect the current type.
		$checked  = checked(  true, in_array( $type, (array) $args['selected'] ), false );
		$selected = selected( true, in_array( $type, (array) $args['selected'] ), false );

		// Switch output based on the element.
		switch ( $output ) {
			case 'select' :
				printf( '<option value="%1$s" %2$s>%3$s</option>', esc_attr( $type ), $selected, esc_html( $description ) );
				break;
			case 'checkbox' :
				printf( '<label style="" for="%1$s[]">%2$s<input type="checkbox" id="%1$s[]" name="%1$s[]" value="%3$s" %4$s/></label>', esc_attr( $args['checkbox_name'] ), esc_html( $description ), esc_attr( $args['checkbox_name'] ), esc_attr( $args['checkbox_name'] ), esc_attr( $type ), $checked );
				break;
		}

		/**
		 * Fires at the end of the listing of activity types.
		 *
		 * This is a variable action hook. The actual hook to use will depend on the output type specified.
		 * Two default hooks are bp_activity_types_list_select and bp_activity_types_list_checkbox.
		 *
		 * @since 1.7.0
		 *
		 * @param array  $args        Array of arguments passed into function.
		 * @param string $type        Activity type being rendered in the output.
		 * @param string $description Description of the activity type being rendered.
		 */
		do_action( 'bp_activity_types_list_' . $output, $args, $type, $description );
	}

	// Backpat with BP-Default for dropdown boxes only.
	if ( 'select' === $output ) {
		do_action( 'bp_activity_filter_options' );
	}
}


/* RSS Feed Template Tags ****************************************************/

/**
 * Output the sitewide activity feed link.
 *
 * @since 1.0.0
 *
 * @uses bp_get_sitewide_activity_feed_link()
 */
function bp_sitewide_activity_feed_link() {
	echo bp_get_sitewide_activity_feed_link();
}

	/**
	 * Returns the sitewide activity feed link.
	 *
	 * @since 1.0.0
	 *
	 * @uses home_url()
	 * @uses bp_get_activity_root_slug()
	 * @uses apply_filters() To call the 'bp_get_sitewide_activity_feed_link' hook.
	 *
	 * @return string The sitewide activity feed link.
	 */
	function bp_get_sitewide_activity_feed_link() {

		/**
		 * Filters the sidewide activity feed link.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value The feed link for sitewide activity.
		 */
		return apply_filters( 'bp_get_sitewide_activity_feed_link', bp_get_root_domain() . '/' . bp_get_activity_root_slug() . '/feed/' );
	}

/**
 * Output the member activity feed link.
 *
 * @since 1.2.0
 *
 * @uses bp_get_member_activity_feed_link()
 */
function bp_member_activity_feed_link() {
	echo bp_get_member_activity_feed_link();
}

/**
 * Output the member activity feed link.
 *
 * @since 1.0.0
 * @deprecated 1.2.0
 *
 * @todo properly deprecate in favor of bp_member_activity_feed_link().
 *
 * @uses bp_get_member_activity_feed_link()
 */
function bp_activities_member_rss_link() { echo bp_get_member_activity_feed_link(); }

	/**
	 * Return the member activity feed link.
	 *
	 * @since 1.2.0
	 *
	 * @uses bp_is_profile_component()
	 * @uses bp_is_current_action()
	 * @uses bp_displayed_user_domain()
	 * @uses bp_get_activity_slug()
	 * @uses bp_is_active()
	 * @uses bp_get_friends_slug()
	 * @uses bp_get_groups_slug()
	 * @uses apply_filters() To call the 'bp_get_activities_member_rss_link' hook.
	 *
	 * @return string $link The member activity feed link.
	 */
	function bp_get_member_activity_feed_link() {

		// Single member activity feed link.
		if ( bp_is_profile_component() || bp_is_current_action( 'just-me' ) ) {
			$link = bp_displayed_user_domain() . bp_get_activity_slug() . '/feed/';

		// Friend feed link.
		} elseif ( bp_is_active( 'friends' ) && bp_is_current_action( bp_get_friends_slug() ) ) {
			$link = bp_displayed_user_domain() . bp_get_activity_slug() . '/' . bp_get_friends_slug() . '/feed/';

		// Group feed link.
		} elseif ( bp_is_active( 'groups'  ) && bp_is_current_action( bp_get_groups_slug()  ) ) {
			$link = bp_displayed_user_domain() . bp_get_activity_slug() . '/' . bp_get_groups_slug() . '/feed/';

		// Favorites activity feed link.
		} elseif ( 'favorites' === bp_current_action() ) {
			$link = bp_displayed_user_domain() . bp_get_activity_slug() . '/favorites/feed/';

		// Mentions activity feed link.
		} elseif ( ( 'mentions' === bp_current_action() ) && bp_activity_do_mentions() ) {
			$link = bp_displayed_user_domain() . bp_get_activity_slug() . '/mentions/feed/';

		// No feed link.
		} else {
			$link = '';
		}

		/**
		 * Filters the member activity feed link.
		 *
		 * @since 1.0.0
		 *
		 * @param string $link URL for the member activity feed.
		 */
		return apply_filters( 'bp_get_activities_member_rss_link', $link );
	}

	/**
	 * Return the member activity feed link.
	 *
	 * @since 1.0.0
	 * @deprecated 1.2.0
	 *
	 * @todo properly deprecate in favor of bp_get_member_activity_feed_link().
	 *
	 * @uses bp_get_member_activity_feed_link()
	 *
	 * @return string The member activity feed link.
	 */
	function bp_get_activities_member_rss_link() { return bp_get_member_activity_feed_link(); }


/** Template tags for RSS feed output ****************************************/

/**
 * Outputs the activity feed item guid.
 *
 * @since 1.0.0
 *
 * @uses bp_activity_feed_item_guid()
 */
function bp_activity_feed_item_guid() {
	echo bp_get_activity_feed_item_guid();
}

	/**
	 * Returns the activity feed item guid.
	 *
	 * @since 1.2.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_feed_item_guid' hook.
	 *
	 * @return string The activity feed item guid.
	 */
	function bp_get_activity_feed_item_guid() {
		global $activities_template;

		/**
		 * Filters the activity feed item guid.
		 *
		 * @since 1.1.3
		 *
		 * @param string $value Calculated md5 value for the activity feed item.
		 */
		return apply_filters( 'bp_get_activity_feed_item_guid', md5( $activities_template->activity->date_recorded . '-' . $activities_template->activity->content ) );
	}

/**
 * Output the activity feed item title.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_feed_item_title()
 */
function bp_activity_feed_item_title() {
	echo bp_get_activity_feed_item_title();
}

	/**
	 * Return the activity feed item title.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses ent2ncr()
	 * @uses convert_chars()
	 * @uses bp_create_excerpt()
	 * @uses apply_filters() To call the 'bp_get_activity_feed_item_title' hook.
	 *
	 * @return string $title The activity feed item title.
	 */
	function bp_get_activity_feed_item_title() {
		global $activities_template;

		if ( !empty( $activities_template->activity->action ) ) {
			$content = $activities_template->activity->action;
		} else {
			$content = $activities_template->activity->content;
		}

		$content = explode( '<span', $content );
		$title   = strip_tags( ent2ncr( trim( convert_chars( $content[0] ) ) ) );

		if ( ':' === substr( $title, -1 ) ) {
			$title = substr( $title, 0, -1 );
		}

		if ( 'activity_update' === $activities_template->activity->type ) {
			$title .= ': ' . strip_tags( ent2ncr( trim( convert_chars( bp_create_excerpt( $activities_template->activity->content, 70, array( 'ending' => " [&#133;]" ) ) ) ) ) );
		}

		/**
		 * Filters the activity feed item title.
		 *
		 * @since 1.0.0
		 *
		 * @param string $title The title for the activity feed item.
		 */
		return apply_filters( 'bp_get_activity_feed_item_title', $title );
	}

/**
 * Output the activity feed item link.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_feed_item_link()
 */
function bp_activity_feed_item_link() {
	echo bp_get_activity_feed_item_link();
}

	/**
	 * Return the activity feed item link.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_feed_item_link' hook.
	 *
	 * @return string The activity feed item link.
	 */
	function bp_get_activity_feed_item_link() {
		global $activities_template;

		$retval = ! empty( $activities_template->activity->primary_link )
			? $activities_template->activity->primary_link
			: '';

		/**
		 * Filters the activity feed item link.
		 *
		 * @since 1.0.0
		 *
		 * @param string $retval The URL for the activity feed item.
		 */
		return apply_filters( 'bp_get_activity_feed_item_link', $retval );
	}

/**
 * Output the activity feed item date.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_feed_item_date()
 */
function bp_activity_feed_item_date() {
	echo bp_get_activity_feed_item_date();
}

	/**
	 * Return the activity feed item date.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses apply_filters() To call the 'bp_get_activity_feed_item_date' hook.
	 *
	 * @return string The activity feed item date.
	 */
	function bp_get_activity_feed_item_date() {
		global $activities_template;

		$retval = ! empty( $activities_template->activity->date_recorded )
			? $activities_template->activity->date_recorded
			: '';

		/**
		 * Filters the activity feed item date.
		 *
		 * @since 1.0.0
		 *
		 * @param string $retval The date for the activity feed item.
		 */
		return apply_filters( 'bp_get_activity_feed_item_date', $retval );
	}

/**
 * Output the activity feed item description.
 *
 * @since 1.0.0
 *
 * @uses bp_get_activity_feed_item_description()
 */
function bp_activity_feed_item_description() {
	echo bp_get_activity_feed_item_description();
}

	/**
	 * Return the activity feed item description.
	 *
	 * @since 1.0.0
	 *
	 * @global object $activities_template {@link BP_Activity_Template}
	 * @uses ent2ncr()
	 * @uses convert_chars()
	 * @uses apply_filters() To call the 'bp_get_activity_feed_item_description' hook.
	 *
	 * @return string The activity feed item description.
	 */
	function bp_get_activity_feed_item_description() {
		global $activities_template;

		// Get the content, if exists.
		$content = ! empty( $activities_template->activity->content )
			? $activities_template->activity->content
			: '';

		// Perform a few string conversions on the content, if it's not empty.
		if ( ! empty( $content ) ) {
			$content = ent2ncr( convert_chars( str_replace( '%s', '', $content ) ) );
		}

		/**
		 * Filters the activity feed item description.
		 *
		 * @since 1.0.0
		 *
		 * @param string $content The description for the activity feed item.
		 */
		return apply_filters( 'bp_get_activity_feed_item_description', $content );
	}

/**
 * Template tag so we can hook activity feed to <head>.
 *
 * @since 1.5.0
 *
 * @uses bloginfo()
 * @uses bp_sitewide_activity_feed_link()
 */
function bp_activity_sitewide_feed() {
?>

	<link rel="alternate" type="application/rss+xml" title="<?php bloginfo( 'name' ) ?> | <?php _e( 'Site Wide Activity RSS Feed', 'buddypress' ) ?>" href="<?php bp_sitewide_activity_feed_link() ?>" />

<?php
}
add_action( 'bp_head', 'bp_activity_sitewide_feed' );

/**
 * Display available filters depending on the scope.
 *
 * @since 2.1.0
 *
 * @param string $context The current context. 'activity', 'member',
 *                        'member_groups', 'group'.
 * @uses bp_get_activity_show_filters()
 */
function bp_activity_show_filters( $context = '' ) {
	echo bp_get_activity_show_filters( $context );
}
	/**
	 * Get available filters depending on the scope.
	 *
	 * @since 2.1.0
	 *
	 * @param string $context The current context. 'activity', 'member',
	 *                        'member_groups', 'group'.
	 *
	 * @return string HTML for <option> values.
	 */
	function bp_get_activity_show_filters( $context = '' ) {
		// Set default context based on current page.
		if ( empty( $context ) ) {

			// On member pages, default to 'member', unless this
			// is a user's Groups activity.
			if ( bp_is_user() ) {
				if ( bp_is_active( 'groups' ) && bp_is_current_action( bp_get_groups_slug() ) ) {
					$context = 'member_groups';
				} else {
					$context = 'member';
				}

			// On individual group pages, default to 'group'.
			} elseif ( bp_is_active( 'groups' ) && bp_is_group() ) {
				$context = 'group';

			// 'activity' everywhere else.
			} else {
				$context = 'activity';
			}
		}

		$filters = array();

		// Walk through the registered actions, and prepare an the
		// select box options.
		foreach ( bp_activity_get_actions() as $actions ) {
			foreach ( $actions as $action ) {
				if ( ! in_array( $context, (array) $action['context'] ) ) {
					continue;
				}

				// Friends activity collapses two filters into one.
				if ( in_array( $action['key'], array( 'friendship_accepted', 'friendship_created' ) ) ) {
					$action['key'] = 'friendship_accepted,friendship_created';
				}

				$filters[ $action['key'] ] = $action['label'];
			}
		}

		/**
		 * Filters the options available in the activity filter dropdown.
		 *
		 * @since 2.2.0
		 *
		 * @param array  $filters Array of filter options for the given context, in the following format: $option_value => $option_name.
		 * @param string $context Context for the filter. 'activity', 'member', 'member_groups', 'group'.
		 */
		$filters = apply_filters( 'bp_get_activity_show_filters_options', $filters, $context );

		// Build the options output.
		$output = '';

		if ( ! empty( $filters ) ) {
			foreach ( $filters as $value => $filter ) {
				$output .= '<option value="' . esc_attr( $value ) . '">' . esc_html( $filter ) . '</option>' . "\n";
			}
		}

		/**
		 * Filters the HTML markup result for the activity filter dropdown.
		 *
		 * @since 2.1.0
		 *
		 * @param string $output  HTML output for the activity filter dropdown.
		 * @param array  $filters Array of filter options for the given context, in the following format: $option_value => $option_name.
		 * @param string $context Context for the filter. 'activity', 'member', 'member_groups', 'group'.
		 */
		return apply_filters( 'bp_get_activity_show_filters', $output, $filters, $context );
	}
