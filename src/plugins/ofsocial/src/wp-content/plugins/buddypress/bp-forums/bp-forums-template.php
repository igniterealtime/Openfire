<?php
/**
 * BuddyPress Forums Template Tags.
 *
 * @package BuddyPress
 * @subpackage ForumsTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the forums component slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_forums_slug()
 */
function bp_forums_slug() {
	echo bp_get_forums_slug();
}
	/**
	 * Return the forums component slug.
	 *
	 * @since 1.5.0
	 *
	 * @return string Slug for the forums component.
	 */
	function bp_get_forums_slug() {
		/**
		 * Filters the forums component slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $slug Forums component slug.
		 */
		return apply_filters( 'bp_get_forums_slug', buddypress()->forums->slug );
	}

/**
 * Output the forums component root slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_forums_root_slug()
 */
function bp_forums_root_slug() {
	echo bp_get_forums_root_slug();
}
	/**
	 * Return the forums component root slug.
	 *
	 * @since 1.5.0
	 *
	 * @return string Root slug for the forums component.
	 */
	function bp_get_forums_root_slug() {
		/**
		 * Filters the forums component root slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $root_slug Forums component root slug.
		 */
		return apply_filters( 'bp_get_forums_root_slug', buddypress()->forums->root_slug );
	}

/**
 * Output permalink for the forum directory.
 *
 * @since 1.5.0
 *
 * @uses bp_get_forums_directory_permalink()
 */
function bp_forums_directory_permalink() {
	echo esc_url( bp_get_forums_directory_permalink() );
}
	/**
	 * Return permalink for the forum directory.
	 *
	 * @since 1.5.0
	 *
	 * @uses apply_filters()
	 * @uses trailingslashit()
	 * @uses bp_get_root_domain()
	 * @uses bp_get_forums_root_slug()
	 *
	 * @return string The permalink for the forums component directory.
	 */
	function bp_get_forums_directory_permalink() {

		/**
		 * Filters the permalink for the forum directory.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Permalink for the forum directory.
		 */
		return apply_filters( 'bp_get_forums_directory_permalink', trailingslashit( bp_get_root_domain() . '/' . bp_get_forums_root_slug() ) );
	}

/**
 * The main forums template loop class.
 *
 * Responsible for loading a group of forum topics into a loop for display.
 */
class BP_Forums_Template_Forum {
	/**
	 * The loop iterator.
	 *
	 * @var int
	 */
	public $current_topic = -1;

	/**
	 * The number of topics returned by the paged query.
	 *
	 * @var int
	 */
	public $topic_count;

	/**
	 * Array of topics located by the query.
	 *
	 * @var array
	 */
	public $topics;

	/**
	 * The topic object currently being iterated on.
	 *
	 * @var object
	 */
	public $topic;

	/**
	 * The ID of the forum whose topics are being queried.
	 *
	 * @var int
	 */
	public $forum_id;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @var bool
	 */
	public $in_the_loop;

	/**
	 * The page number being requested.
	 *
	 * @var int
	 */
	public $pag_page;

	/**
	 * The number of items being requested per page.
	 *
	 * @var int
	 */
	public $pag_num;

	/**
	 * An HTML string containing pagination links.
	 *
	 * @var string
	 */
	public $pag_links;

	/**
	 * The total number of topics matching the query parameters.
	 *
	 * @var int
	 */
	public $total_topic_count;

	/**
	 * Whether requesting a single topic. Not currently used.
	 *
	 * @var bool
	 */
	public $single_topic = false;

	/**
	 * Term to sort by. Not currently used.
	 *
	 * @var string
	 */
	public $sort_by;

	/**
	 * Sort order. Not currently used.
	 *
	 * @var string
	 */
	public $order;

	/**
	 * Constructor method.
	 *
	 * @param string   $type         The 'type' is the sort order/kind. 'newest',
	 *                               'popular', 'unreplied', 'tags'.
	 * @param int      $forum_id     The ID of the forum for which topics are being
	 *                               queried.
	 * @param int      $user_id      The ID of the user to whom topics should be
	 *                               limited. Pass false to remove this filter.
	 * @param int      $page         The number of the page being requested.
	 * @param int      $per_page     The number of items being requested per page.
	 * @param int      $max          Max amount to retrieve.
	 * @param string   $no_stickies  Requested sticky format.
	 * @param string   $search_terms Filter results by a string.
	 * @param int|bool $offset       Optional. Offset results by a given numeric value.
	 * @param int|bool $number       Optional. Total number of items to retrieve.
	 */
	function __construct( $type, $forum_id, $user_id, $page, $per_page, $max, $no_stickies, $search_terms, $offset = false, $number = false ) {
		$bp = buddypress();

		$this->pag_page     = $page;
		$this->pag_num      = $per_page;
		$this->type         = $type;
		$this->search_terms = $search_terms;
		$this->forum_id     = $forum_id;
		$this->offset	    = $offset;
		$this->number	    = $number;

		switch ( $type ) {
			case 'newest': default:
				$this->topics = bp_forums_get_forum_topics( array( 'user_id' => $user_id, 'forum_id' => $forum_id, 'filter' => $search_terms, 'page' => $this->pag_page, 'per_page' => $this->pag_num, 'show_stickies' => $no_stickies, 'offset' => $offset, 'number' => $number ) );
				break;

			case 'popular':
				$this->topics = bp_forums_get_forum_topics( array( 'user_id' => $user_id, 'type' => 'popular', 'filter' => $search_terms, 'forum_id' => $forum_id, 'page' => $this->pag_page, 'per_page' => $this->pag_num, 'show_stickies' => $no_stickies, 'offset' => $offset, 'number' => $number ) );
				break;

			case 'unreplied':
				$this->topics = bp_forums_get_forum_topics( array( 'user_id' => $user_id, 'type' => 'unreplied', 'filter' => $search_terms, 'forum_id' => $forum_id, 'page' => $this->pag_page, 'per_page' => $this->pag_num, 'show_stickies' => $no_stickies, 'offset' => $offset, 'number' => $number ) );
				break;

			case 'tags':
				$this->topics = bp_forums_get_forum_topics( array( 'user_id' => $user_id, 'type' => 'tags', 'filter' => $search_terms, 'forum_id' => $forum_id, 'page' => $this->pag_page, 'per_page' => $this->pag_num, 'show_stickies' => $no_stickies, 'offset' => $offset, 'number' => $number ) );
				break;
		}

		/**
		 * Filters the forums template topics.
		 *
		 * @since 1.1.0
		 *
		 * @param array  $topics      Array of topics being set for display.
		 * @param string $type        The sort order/kind. 'newest',
		 *                            'popular', 'unreplied', 'tags'.
		 * @param int    $forum_id    ID for the current forum.
		 * @param int    $per_page    The number of the page being requested.
		 * @param int    $max         The max number of posts to show.
		 * @param string $no_stickies Requested sticky format.
		 */
		$this->topics = apply_filters( 'bp_forums_template_topics', $this->topics, $type, $forum_id, $per_page, $max, $no_stickies );

		if ( !(int) $this->topics ) {
			$this->topic_count       = 0;
			$this->total_topic_count = 0;
		} else {
			// Get a total topic count, for use in pagination. This value will differ
			// depending on scope.
			if ( !empty( $forum_id ) ) {
				// Group forums.
				$topic_count = bp_forums_get_forum( $forum_id );
				$topic_count = (int) $topic_count->topics;
			} else if ( !empty( $bp->groups->current_group ) ) {
				$topic_count = (int)groups_total_public_forum_topic_count( $type );
			} else if ( bp_is_user_forums_started() || ( bp_is_directory() && $user_id ) ) {
				// This covers the case of Profile > Forums > Topics Started, as
				// well as Forum Directory > My Topics.
				$topic_count = bp_forums_total_topic_count_for_user( bp_displayed_user_id(), $type );
			} else if ( bp_is_user_forums_replied_to() ) {
				// Profile > Forums > Replied To.
				$topic_count = bp_forums_total_replied_count_for_user( bp_displayed_user_id(), $type );
			} else if ( 'tags' == $type ) {
				$tag         = bb_get_tag( $search_terms );
				$topic_count = $tag->count;
			} else {
				// For forum directories (All Topics), get a true count
				$status = bp_current_user_can( 'bp_moderate' ) ? 'all' : 'public'; // @todo: member-of.
				$topic_count = (int)groups_total_forum_topic_count( $status, $search_terms );
			}

			if ( !$max || $max >= $topic_count ) {
				$this->total_topic_count = $topic_count;
			} else {
				$this->total_topic_count = (int) $max;
			}

			if ( $max ) {
				if ( $max >= count($this->topics) ) {
					$this->topic_count = count( $this->topics );
				} else {
					$this->topic_count = (int) $max;
				}
			} else {
				$this->topic_count = count( $this->topics );
			}
		}

		/**
		 * Filters the topic count for the forum being displayed.
		 *
		 * @since 1.1.0
		 *
		 * @param int    $topic_count Topic count to be displayed.
		 * @param array  $topics      Array of topics to be displayed.
		 * @param string $type        The sort order/kind. 'newest',
		 *                            'popular', 'unreplied', 'tags'.
		 * @param int    $forum_id    ID for the current forum.
		 * @param int    $per_page    The number of the page being requested.
		 * @param int    $max         The max number of posts to show.
		 * @param string $no_stickies Requested sticky format.
		 */
		$this->topic_count = apply_filters_ref_array( 'bp_forums_template_topic_count', array( $this->topic_count, &$this->topics, $type, $forum_id, $per_page, $max, $no_stickies ) );

		/**
		 * Filters the total topic count for the forum being displayed.
		 *
		 * @since 1.1.0
		 *
		 * @param int    $total_topic_count Total topic count found.
		 * @param int    $topic_count       Topic count to be displayed.
		 * @param array  $topics            Array of topics to be displayed.
		 * @param string $type              The sort order/kind. 'newest',
		 *                                  'popular', 'unreplied', 'tags'.
		 * @param int    $forum_id          ID for the current forum.
		 * @param int    $per_page          The number of the page being requested.
		 * @param int    $max               The max number of posts to show.
		 * @param string $no_stickies       Requested sticky format.
		 */
		$this->total_topic_count = apply_filters_ref_array( 'bp_forums_template_total_topic_count', array( $this->total_topic_count, $this->topic_count, &$this->topics, $type, $forum_id, $per_page, $max, $no_stickies ) );

		// Fetch extra information for topics, so we don't have to query inside the loop.
		$this->topics = bp_forums_get_topic_extras( $this->topics );

		if ( (int) $this->total_topic_count && (int) $this->pag_num ) {
			$this->pag_links = paginate_links( array(
				'base'      => add_query_arg( array( 'p' => '%#%', 'n' => $this->pag_num ) ),
				'format'    => '',
				'total'     => ceil( (int) $this->total_topic_count / (int) $this->pag_num),
				'current'   => $this->pag_page,
				'prev_text' => _x( '&larr;', 'Forum topic pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Forum topic pagination next text', 'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => array(),
			) );
		}
	}

	/**
	 * Whether there are topics available in the loop.
	 *
	 * @see bp_has_forum_topics()
	 *
	 * @return bool True if there are items in the loop, otherwise false.
	 */
	function has_topics() {
		if ( $this->topic_count ) {
			return true;
		}

		return false;
	}

	/**
	 * Set up the next topic and iterate index.
	 *
	 * @return object The next topic to iterate over.
	 */
	function next_topic() {
		$this->current_topic++;
		$this->topic = $this->topics[$this->current_topic];

		return $this->topic;
	}

	/**
	 * Rewind the topics and reset topic index.
	 */
	function rewind_topics() {
		$this->current_topic = -1;
		if ( $this->topic_count > 0 ) {
			$this->topic = $this->topics[0];
		}
	}

	/**
	 * Whether there are blogs left in the loop to iterate over.
	 *
	 * This method is used by {@link bp_forum_topics()} as part of the while loop
	 * that controls iteration inside the blogs loop, eg:
	 *     while ( bp_forum_topics() ) { ...
	 *
	 * @see bp_forum_topics()
	 *
	 * @return bool True if there are more topics to show, otherwise false.
	 */
	function user_topics() {
		if ( $this->current_topic + 1 < $this->topic_count ) {
			return true;
		} elseif ( $this->current_topic + 1 == $this->topic_count ) {

			/**
			 * Fires right before the rewinding of user topics.
			 *
			 * @since 1.5.0
			 */
			do_action('forum_loop_end');
			// Do some cleaning up after the loop.
			$this->rewind_topics();
		}

		$this->in_the_loop = false;
		return false;
	}

	/**
	 * Set up the current topic in the loop.
	 *
	 * @see bp_the_forum_topic()
	 */
	function the_topic() {
		global $topic;

		$this->in_the_loop = true;
		$this->topic = $this->next_topic();
		$this->topic = (object)$this->topic;

		if ( $this->current_topic == 0 ) {

			/**
			 * Fires if the current topic item is the first in the topic loop.
			 *
			 * @since 1.5.0
			 */
			do_action( 'forum_loop_start' );
		}
	}
}

/**
 * Initiate the forum topics loop.
 *
 * Like other BuddyPress custom loops, the default arguments for this function
 * are determined dynamically, depending on your current page. All of these
 * $defaults can be overridden in the $args parameter.
 *
 * @uses apply_filters() Filter 'bp_has_topics' to manipulate the
 *       $forums_template global before it's rendered, or to modify the value
 *       of has_topics().
 *
 * @param array|string $args {
 *     Arguments for limiting the contents of the forum topics loop.
 *
 *     @type string      $type         The 'type' is the sort order/kind. 'newest',
 *                                     'popular', 'unreplied', 'tags'. Default: 'newest'.
 *     @type int         $forum_id     The ID of the forum for which topics are being
 *                                     queried. Default: the ID of the forum belonging to the current
 *                                     group, if available.
 *     @type int         $user_id      The ID of a user to whom to limit results. If viewing
 *                                     a member's profile, defaults to that member's ID; otherwise
 *                                     defaults to 0.
 *     @type int         $page         The number of the page being requested. Default: 1, or
 *                                     the value of $_GET['p'].
 *     @type int         $per_pag      The number items to return per page. Default: 20, or
 *                                     the value of $_GET['n'].
 *     @type int         $max          Optional. Max records to return. Default: false (no max).
 *     @type int         $number       Optional. Number of records to return. Default: false.
 *     @type int         $offset       Optional. Offset results by a given value.
 *                                     Default: false.
 *     @type string      $search_terms Optional. A string to which results should be
 *                                     limited. Default: false, or the value of $_GET['fs'].
 *     @type string|bool $do_stickies  Whether to move stickies to the top of
 *                                     the sort order. Default: true if looking at a group forum,
 *                                     otherwise false.
 * }
 * @return bool True when forum topics are found corresponding to the args,
 *         false otherwise.
 */
function bp_has_forum_topics( $args = '' ) {
	global $forum_template;

	$bp = buddypress();

	/*
	 * Set the defaults based on the current page. Any of these will be overridden
	 * if arguments are directly passed into the loop. Custom plugins should always
	 * pass their parameters directly to the loop.
	 */
	$type         = 'newest';
	$user_id      = 0;
	$forum_id     = false;
	$search_terms = false;
	$do_stickies  = false;

	// User filtering.
	if ( bp_displayed_user_id() )
		$user_id = bp_displayed_user_id();

	// "Replied" query must be manually modified
	if ( 'replies' == bp_current_action() ) {
		$user_id = 0; // User id must be handled manually by the filter, not by BB_Query.

		add_filter( 'get_topics_distinct',   'bp_forums_add_replied_distinct_sql', 20 );
		add_filter( 'get_topics_join', 	     'bp_forums_add_replied_join_sql', 20 );
		add_filter( 'get_topics_where',      'bp_forums_add_replied_where_sql', 20  );
	}

	// If we're in a single group, set this group's forum_id.
	if ( !$forum_id && !empty( $bp->groups->current_group ) ) {
		$bp->groups->current_group->forum_id = groups_get_groupmeta( $bp->groups->current_group->id, 'forum_id' );

		// If it turns out there is no forum for this group, return false so
		// we don't fetch all global topics.
		if ( empty( $bp->groups->current_group->forum_id ) )
			return false;

		$forum_id = $bp->groups->current_group->forum_id;
	}

	// If $_GET['fs'] is set, let's auto populate the search_terms var.
	if ( bp_is_directory() && !empty( $_GET['fs'] ) )
		$search_terms = $_GET['fs'];

	// Get the pagination arguments from $_REQUEST.
	$page     = isset( $_REQUEST['p'] ) ? intval( $_REQUEST['p'] ) : 1;
	$per_page = isset( $_REQUEST['n'] ) ? intval( $_REQUEST['n'] ) : 20;

	// By default, stickies are only pushed to the top of the order on individual group forums.
	if ( bp_is_group_forum() )
		$do_stickies = true;

	$defaults = array(
		'type'         => $type,
		'forum_id'     => $forum_id,
		'user_id'      => $user_id,
		'page'         => $page,
		'per_page'     => $per_page,
		'max'          => false,
		'number'       => false,
		'offset'       => false,
		'search_terms' => $search_terms,
		'do_stickies'  => $do_stickies
	);

	$r = bp_parse_args( $args, $defaults, 'has_forum_topics' );
	extract( $r );

	// If we're viewing a tag URL in the directory, let's override the type and
	// set it to tags and the filter to the tag name.
	if ( bp_is_current_action( 'tag' ) && $search_terms = bp_action_variable( 0 ) ) {
		$type = 'tags';
	}

	/** Sticky logic ******************************************************************/

	if ( $do_stickies ) {
		// Fetch the stickies.
		$stickies_template = new BP_Forums_Template_Forum( $type, $forum_id, $user_id, 0, 0, $max, 'sticky', $search_terms );

		// If stickies are found, try merging them.
		if ( $stickies_template->has_topics() ) {

			// If stickies are for current $page.
			$page_start_num = ( ( $page - 1 ) * $per_page ) + 1;
			$page_end_num 	= $page * $per_page <= $stickies_template->total_topic_count ? $page * $per_page : $stickies_template->total_topic_count;

			// Calculate the number of sticky topics that will be shown on this page.
			if ( $stickies_template->topic_count < $page_start_num ) {
				$this_page_stickies = 0;
			} else {
				$this_page_stickies = $stickies_template->topic_count - $per_page * floor( $stickies_template->topic_count / $per_page ) * ( $page - 1 ); // Total stickies minus sticky count through this page.

				// $this_page_stickies cannot be more than $per_page or less than 0
				if ( $this_page_stickies > $per_page )
					$this_page_stickies = $per_page;
				else if ( $this_page_stickies < 0 )
					$this_page_stickies = 0;
			}

			// Calculate the total number of topics that will be shown on this page.
			$this_page_topics = $stickies_template->total_topic_count >= ( $page * $per_page ) ? $per_page : $page_end_num - ( $page_start_num - 1 );

			// If the number of stickies to be shown is less than $per_page, fetch some
			// non-stickies to fill in the rest.
			if ( $this_page_stickies < $this_page_topics ) {
				// How many non-stickies do we need?
				$non_sticky_number = $this_page_topics - $this_page_stickies;

				// Calculate the non-sticky offset
				// How many non-stickies on all pages up to this point?
				$non_sticky_total = $page_end_num - $stickies_template->topic_count;

				// The offset is the number of total non-stickies, less the number
				// to be shown on this page.
				$non_sticky_offset = $non_sticky_total - $non_sticky_number;

				// Fetch the non-stickies.
				$forum_template = new BP_Forums_Template_Forum( $type, $forum_id, $user_id, 1, $per_page, $max, 'no', $search_terms, $non_sticky_offset, $non_sticky_number );

				// If there are stickies to merge on this page, do it now.
				if ( $this_page_stickies ) {
					// Correct the topic_count.
					$forum_template->topic_count += (int) $this_page_stickies;

					// Figure out which stickies need to be included.
					$this_page_sticky_topics = array_slice( $stickies_template->topics, 0 - $this_page_stickies );

					// Merge these topics into the forum template.
					$forum_template->topics = array_merge( $this_page_sticky_topics, (array) $forum_template->topics );
				}
			} else {
				// This page has no non-stickies.
				$forum_template = $stickies_template;

				// Adjust the topic count and trim the topics.
				$forum_template->topic_count = $this_page_stickies;
				$forum_template->topics      = array_slice( $forum_template->topics, $page - 1 );
			}

			// Because we're using a manual offset and number for the topic query, we
			// must set the page number manually, and recalculate the pagination links.
			$forum_template->pag_num     = $per_page;
			$forum_template->pag_page    = $page;

			$forum_template->pag_links = paginate_links( array(
				'base'      => add_query_arg( array( 'p' => '%#%', 'n' => $forum_template->pag_num ) ),
				'format'    => '',
				'total'     => ceil( (int) $forum_template->total_topic_count / (int) $forum_template->pag_num ),
				'current'   => $forum_template->pag_page,
				'prev_text' => _x( '&larr;', 'Forum topic pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Forum topic pagination next text', 'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => array(),
			) );

		} else {
			// Fetch the non-sticky topics if no stickies were found.
			$forum_template = new BP_Forums_Template_Forum( $type, $forum_id, $user_id, $page, $per_page, $max, 'all', $search_terms );
		}
	} else {
		// When skipping the sticky logic, just pull up the forum topics like usual.
		$forum_template = new BP_Forums_Template_Forum( $type, $forum_id, $user_id, $page, $per_page, $max, 'all', $search_terms );
	}

	/**
	 * Filters whether or not there are topics to display.
	 *
	 * @since 1.1.0
	 *
	 * @param bool   $value          Whether or not there are topics to display.
	 * @param object $forum_template Global $forum_template object.
	 */
	return apply_filters( 'bp_has_topics', $forum_template->has_topics(), $forum_template );
}

/**
 * Determine whether there are still topics left in the loop.
 *
 * @global BP_Forums_Template_Forum $forum_template Template global.
 *
 * @return bool Returns true when topics are found.
 */
function bp_forum_topics() {
	global $forum_template;
	return $forum_template->user_topics();
}

/**
 * Get the current topic object in the loop.
 *
 * @global BP_Forums_Template_Forum $forum_template Template global.
 *
 * @return object The current topic object.
 */
function bp_the_forum_topic() {
	global $forum_template;
	return $forum_template->the_topic();
}

/**
 * Output the ID of the current topic in the loop.
 */
function bp_the_topic_id() {
	echo bp_get_the_topic_id();
}
	/**
	 * Return the ID of the current topic in the loop.
	 *
	 * @return int ID of the current topic in the loop.
	 */
	function bp_get_the_topic_id() {
		global $forum_template;

		/**
		 * Filters the ID of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $topic_id ID for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_id', $forum_template->topic->topic_id );
	}

/**
 * Output the title of the current topic in the loop.
 */
function bp_the_topic_title() {
	echo bp_get_the_topic_title();
}
	/**
	 * Return the title of the current topic in the loop.
	 *
	 * @return string Title of the current topic in the loop.
	 */
	function bp_get_the_topic_title() {
		global $forum_template;

		/**
		 * Filters the title of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_title Title for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_title', stripslashes( $forum_template->topic->topic_title ) );
	}

/**
 * Output the slug of the current topic in the loop.
 */
function bp_the_topic_slug() {
	echo bp_get_the_topic_slug();
}
	/**
	 * Return the slug of the current topic in the loop.
	 *
	 * @return string Slug of the current topic in the loop.
	 */
	function bp_get_the_topic_slug() {
		global $forum_template;

		/**
		 * Filters the slug of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_slug Slug for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_slug', $forum_template->topic->topic_slug );
	}

/**
 * Output the text of the first post in the current topic in the loop.
 */
function bp_the_topic_text() {
	echo bp_get_the_topic_text();
}
	/**
	 * Return the text of the first post in the current topic in the loop.
	 *
	 * @return string Text of the first post in the current topic.
	 */
	function bp_get_the_topic_text() {
		global $forum_template;

		$post = bb_get_first_post( (int) $forum_template->topic->topic_id, false );

		/**
		 * Filters the text of the first post in the current topic in the loop.
		 *
		 * @since 1.1.0
		 *
		 * @param string $post_text Text for the first post in the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_text', esc_attr( $post->post_text ) );
	}

/**
 * Output the ID of the user who posted the current topic in the loop.
 */
function bp_the_topic_poster_id() {
	echo bp_get_the_topic_poster_id();
}
	/**
	 * Return the ID of the user who posted the current topic in the loop.
	 *
	 * @return int ID of the user who posted the current topic.
	 */
	function bp_get_the_topic_poster_id() {
		global $forum_template;

		/**
		 * Filters the ID of the user who posted the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $topic_poster ID for the user.
		 */
		return apply_filters( 'bp_get_the_topic_poster_id', $forum_template->topic->topic_poster );
	}

/**
 * Output the avatar of the user who posted the current topic in the loop.
 *
 * @see bp_get_the_topic_poster_avatar() for a description of arguments.
 *
 * @param array|string $args See {@link bp_get_the_topic_poster_avatar()}.
 */
function bp_the_topic_poster_avatar( $args = '' ) {
	echo bp_get_the_topic_poster_avatar( $args );
}
	/**
	 * Return the avatar of the user who posted the current topic in the loop.
	 *
	 * @param array|string $args {
	 *     Arguments for building the avatar.
	 *     @type string $type   Avatar type. 'thumb' or 'full'. Default:
	 *                          'thumb'.
	 *     @type int    $width  Width of the avatar, in pixels. Default: the
	 *                          width corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type int    $height Height of the avatar, in pixels. Default: the
	 *                          height corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type string $alt    The text of the image's 'alt' attribute.
	 *                          Default: 'Profile picture of [user name]'.
	 * }
	 * @return string HTML of user avatar.
	 */
	function bp_get_the_topic_poster_avatar( $args = '' ) {
		global $forum_template;

		$defaults = array(
			'type'   => 'thumb',
			'width'  => false,
			'height' => false,
			'alt'    => sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_core_get_user_displayname( $forum_template->topic->topic_poster ) )
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		/**
		 * Filters the avatar for the user who posted the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value HTML for the display of the user's avatar.
		 */
		return apply_filters( 'bp_get_the_topic_poster_avatar', bp_core_fetch_avatar( array( 'item_id' => $forum_template->topic->topic_poster, 'type' => $type, 'width' => $width, 'height' => $height, 'alt' => $alt ) ) );
	}

/**
 * Output the name of the user who posted the current topic in the loop.
 */
function bp_the_topic_poster_name() {
	echo bp_get_the_topic_poster_name();
}
	/**
	 * Return the name of the user who posted the current topic in the loop.
	 *
	 * @return string Name of the user who posted the current topic.
	 */
	function bp_get_the_topic_poster_name() {
		global $forum_template;

		$poster_id = ( empty( $forum_template->topic->poster_id ) ) ? $forum_template->topic->topic_poster : $forum_template->topic->poster_id;

		if ( !$name = bp_core_get_userlink( $poster_id ) )
			return __( 'Deleted User', 'buddypress' );

		/**
		 * Filters the name of the user who posted the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $name Name for the user who posted the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_poster_name', $name );
	}

/**
 * Output the ID of the object associated with the current topic in the loop.
 */
function bp_the_topic_object_id() {
	echo bp_get_the_topic_object_id();
}
	/**
	 * Return the ID of the object associated with the current topic in the loop.
	 *
	 * Objects are things like associated groups.
	 *
	 * @return int ID of the associated object.
	 */
	function bp_get_the_topic_object_id() {
		global $forum_template;

		/**
		 * Filters the ID of the object associated with the current topic in the loop.
		 *
		 * Objects are things like associated groups.
		 *
		 * @since 1.1.0
		 *
		 * @param int $object_id ID for the object associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_object_id', $forum_template->topic->object_id );
	}

/**
 * Output the name of the object associated with the current topic in the loop.
 */
function bp_the_topic_object_name() {
	echo bp_get_the_topic_object_name();
}
	/**
	 * Return the name of the object associated with the current topic in the loop.
	 *
	 * Objects are things like groups. So this function would return the
	 * name of the group associated with the forum topic, if it exists.
	 *
	 * @return string Object name.
	 */
	function bp_get_the_topic_object_name() {
		global $forum_template;

		if ( isset( $forum_template->topic->object_name ) )
			$retval = $forum_template->topic->object_name;
		else
			$retval = '';

		/**
		 * Filters the name of the object associated with the current topic in the loop.
		 *
		 * Objects are things like groups. So this filter would return the
		 * name of the group associated with the forum topic, if it exists.
		 *
		 * @since 1.1.0
		 *
		 * @param string $retval Name for the object associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_object_name', $retval );
	}

/**
 * Output the slug of the object associated with the current topic in the loop.
 */
function bp_the_topic_object_slug() {
	echo bp_get_the_topic_object_slug();
}
	/**
	 * Return the slug of the object associated with the current topic in the loop.
	 *
	 * Objects are things like groups. So this function would return the
	 * slug of the group associated with the forum topic, if it exists.
	 *
	 * @return string Object slug.
	 */
	function bp_get_the_topic_object_slug() {
		global $forum_template;

		/**
		 * Filters the slug of the object associated with the current topic in the loop.
		 *
		 * Objects are things like groups. So this filter would return the
		 * slug of the group associated with the forum topic, if it exists.
		 *
		 * @since 1.1.0
		 *
		 * @param string $object_slug Slug for the object associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_object_slug', $forum_template->topic->object_slug );
	}

/**
 * Output the permalink of the object associated with the current topic in the loop.
 */
function bp_the_topic_object_permalink() {
	echo bp_get_the_topic_object_permalink();
}
	/**
	 * Return the permalink of the object associated with the current topic in the loop.
	 *
	 * Objects are things like groups. So this function would return the
	 * permalink of the group associated with the forum topic, if it exists.
	 *
	 * @return string Object permalink.
	 */
	function bp_get_the_topic_object_permalink() {

		// Currently this will only work with group forums, extended support in the future.
		if ( bp_is_active( 'groups' ) ) {
			$permalink = trailingslashit( bp_get_groups_directory_permalink() . bp_get_the_topic_object_slug() . '/forum' );
		} else {
			$permalink = '';
		}

		/**
		 * Filters the permalink of the object associated with the current topic in the loop.
		 *
		 * Objects are things like groups. So this filter would return the
		 * slug of the group associated with the forum topic, if it exists.
		 *
		 * @since 1.1.0
		 *
		 * @param string $permalink Permalink for the object associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_object_permalink', $permalink );
	}

/**
 * Output the linked name of the user who last posted to the current topic in the loop.
 */
function bp_the_topic_last_poster_name() {
	echo bp_get_the_topic_last_poster_name();
}
	/**
	 * Return the linked name of the user who last posted to the current topic in the loop.
	 *
	 * @return string HTML link to the profile of the user who last posted
	 *                to the current topic.
	 */
	function bp_get_the_topic_last_poster_name() {
		global $forum_template;

		$domain = bp_core_get_user_domain( $forum_template->topic->topic_last_poster, $forum_template->topic->topic_last_poster_nicename, $forum_template->topic->topic_last_poster_login );

		// In the case where no user is found, bp_core_get_user_domain() may return the URL
		// of the Members directory.
		if ( empty( $domain ) || ( bp_get_members_directory_permalink() === $domain ) ) {
			return __( 'Deleted User', 'buddypress' );
		}

		/**
		 * Filters the linked name of the user who last posted to the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value HTML link to the profile of the user who last posted.
		 */
		return apply_filters( 'bp_get_the_topic_last_poster_name', '<a href="' . $domain . '">' . $forum_template->topic->topic_last_poster_displayname . '</a>' );
	}

/**
 * Output the permalink of the object associated with the current topic in the loop.
 *
 * @see bp_get_the_topic_object_avatar() for description of arguments.
 *
 * @param array|string $args See {@bp_get_the_topic_object_avatar()}.
 */
function bp_the_topic_object_avatar( $args = '' ) {
	echo bp_get_the_topic_object_avatar( $args );
}
	/**
	 * Return the avatar of the object associated with the current topic in the loop.
	 *
	 * Objects are things like groups. So this function would return the
	 * avatar of the group associated with the forum topic, if it exists.
	 *
	 * @param array|string $args {
	 *     Arguments for building the avatar.
	 *     @type string $type   Avatar type. 'thumb' or 'full'. Default:
	 *                         'thumb'.
	 *     @type int    $width  Width of the avatar, in pixels. Default: the
	 *                          width corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type int    $height Height of the avatar, in pixels. Default:
	 *                          the height corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type string $alt    The text of the image's 'alt' attribute.
	 *                          Default: 'Group logo for [group name]'.
	 * }
	 * @return string Object avatar.
	 */
	function bp_get_the_topic_object_avatar( $args = '' ) {
		global $forum_template;

		if ( !isset( $forum_template->topic->object_id ) )
			return false;

		$defaults = array(
			'type'   => 'thumb',
			'width'  => false,
			'height' => false,
			'alt'    => __( 'Group logo for %s', 'buddypress' )
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		/**
		 * Filters the avatar of the object associated with the current topic in the loop.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value HTML output for the object associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_object_avatar', bp_core_fetch_avatar( array( 'item_id' => $forum_template->topic->object_id, 'type' => $type, 'object' => 'group', 'width' => $width, 'height' => $height, 'alt' => $alt ) ) );
	}

/**
 * Output the avatar for the user who last posted to the current topic in the loop.
 *
 * @see bp_get_the_topic_last_poster_avatar() for description of arguments.
 *
 * @param array|string $args See {@bp_get_the_topic_last_poster_avatar()}.
 */
function bp_the_topic_last_poster_avatar( $args = '' ) {
	echo bp_get_the_topic_last_poster_avatar( $args );
}
	/**
	 * Return the avatar for the user who last posted to the current topic in the loop.
	 *
	 * @param array|string $args {
	 *     Arguments for building the avatar.
	 *     @type string $type   Avatar type. 'thumb' or 'full'. Default:
	 *                          'thumb'.
	 *     @type int    $width  Width of the avatar, in pixels. Default: the
	 *                          width corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type int    $height Height of the avatar, in pixels. Default:
	 *                          the height corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type string $alt    The text of the image's 'alt' attribute.
	 *                          Default: 'Profile picture of [group name]'.
	 * }
	 * @return string User avatar.
	 */
	function bp_get_the_topic_last_poster_avatar( $args = '' ) {
		global $forum_template;

		$defaults = array(
			'type'   => 'thumb',
			'width'  => false,
			'height' => false,
			'alt'    => __( 'Profile picture of %s', 'buddypress' )
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		/**
		 * Filters the avatar for the user who last posted to the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value HTML output for the avatar for the user who last posted.
		 */
		return apply_filters( 'bp_get_the_topic_last_poster_avatar', bp_core_fetch_avatar( array( 'email' => $forum_template->topic->topic_last_poster_email, 'item_id' => $forum_template->topic->topic_last_poster, 'type' => $type, 'width' => $width, 'height' => $height, 'alt' => $alt ) ) );
	}

/**
 * Output the start time of the current topic in the loop.
 */
function bp_the_topic_start_time() {
	echo bp_get_the_topic_start_time();
}
	/**
	 * Return the start time of the current topic in the loop.
	 *
	 * @return string Start time of the current topic.
	 */
	function bp_get_the_topic_start_time() {
		global $forum_template;

		/**
		 * Filters the start time of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_start_time Start time for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_start_time', $forum_template->topic->topic_start_time );
	}

/**
 * Output the topic time of the current topic in the loop.
 */
function bp_the_topic_time() {
	echo bp_get_the_topic_time();
}
	/**
	 * Return the topic time of the current topic in the loop.
	 *
	 * @return string Topic time of the current topic.
	 */
	function bp_get_the_topic_time() {
		global $forum_template;

		/**
		 * Filters the topic time of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_time Topic time for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_time', $forum_template->topic->topic_time );
	}

/**
 * Output the ID of the forum associated with the current topic in the loop.
 */
function bp_the_topic_forum_id() {
	echo bp_get_the_topic_forum_id();
}
	/**
	 * Return the ID of the forum associated with the current topic in the loop.
	 *
	 * @return int ID of the forum associated with the current topic.
	 */
	function bp_get_the_topic_forum_id() {
		global $forum_template;

		/**
		 * Filters the ID of the forum associated with the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $forum_id ID of the forum associated with the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_forum_id', $forum_template->topic->forum_id );
	}

/**
 * Output the status of the current topic in the loop.
 */
function bp_the_topic_status() {
	echo bp_get_the_topic_status();
}
	/**
	 * Return the status of the current topic in the loop.
	 *
	 * @return string Status of the current topic.
	 */
	function bp_get_the_topic_status() {
		global $forum_template;

		/**
		 * Filters the status of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_status Status of the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_status', $forum_template->topic->topic_status );
	}

/**
 * Output whether the current topic in the loop is open.
 */
function bp_the_topic_is_topic_open() {
	echo bp_get_the_topic_is_topic_open();
}
	/**
	 * Return whether the current topic in the loop is open.
	 *
	 * @return string
	 */
	function bp_get_the_topic_is_topic_open() {
		global $forum_template;

		/**
		 * Filters whether or not the current topic in the loop is open.
		 *
		 * @since 1.0.0
		 *
		 * @param string $topic_open Whether or not the current topic is open.
		 */
		return apply_filters( 'bp_get_the_topic_is_topic_open', $forum_template->topic->topic_open );
	}

/**
 * Output the ID of the last post in the current topic in the loop.
 */
function bp_the_topic_last_post_id() {
	echo bp_get_the_topic_last_post_id();
}
	/**
	 * Return the ID of the last post in the current topic in the loop.
	 *
	 * @return int ID of the last post in the current topic.
	 */
	function bp_get_the_topic_last_post_id() {
		global $forum_template;

		/**
		 * Filters the ID of the last post in the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $topic_last_post_id ID for the last post in the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_last_post_id', $forum_template->topic->topic_last_post_id );
	}

/**
 * Output whether the current topic in the loop is sticky.
 */
function bp_the_topic_is_sticky() {
	echo bp_get_the_topic_is_sticky();
}
	/**
	 * Return whether the current topic in the loop is sticky.
	 *
	 * @return bool
	 */
	function bp_get_the_topic_is_sticky() {
		global $forum_template;

		/**
		 * Filters whether or not the current topic in the loop is sticky.
		 *
		 * @since 1.0.0
		 *
		 * @param bool $topic_sticky Whether or not the current topic is sticky.
		 */
		return apply_filters( 'bp_get_the_topic_is_sticky', $forum_template->topic->topic_sticky );
	}

/**
 * Output a 'x posts' string with the number of posts in the current topic.
 */
function bp_the_topic_total_post_count() {
	echo bp_get_the_topic_total_post_count();
}
	/**
	 * Return a 'x posts' string with the number of posts in the current topic.
	 *
	 * @return string String of the form 'x posts'.
	 */
	function bp_get_the_topic_total_post_count() {
		global $forum_template;

		if ( $forum_template->topic->topic_posts == 1 ) {

			/**
			 * Filters a 'x posts' string with the number of posts in the current topic.
			 *
			 * @since 1.0.0
			 *
			 * @param string $value 'X posts' string value for the current topic.
			 */
			return apply_filters( 'bp_get_the_topic_total_post_count', sprintf( __( '%d post', 'buddypress' ), $forum_template->topic->topic_posts ) );
		} else {

			/** This filter is documented in bp-forums/bp-forums-template.php */
			return apply_filters( 'bp_get_the_topic_total_post_count', sprintf( __( '%d posts', 'buddypress' ), $forum_template->topic->topic_posts ) );
		}
	}

/**
 * Output the total number of posts in the current topic in the loop.
 */
function bp_the_topic_total_posts() {
	echo bp_get_the_topic_total_posts();
}
	/**
	 * Return the total number of posts in the current topic in the loop.
	 *
	 * @return int Total number of posts in the current topic.
	 */
	function bp_get_the_topic_total_posts() {
		global $forum_template;

		/**
		 * Filters the total number of posts in the current topic in the loop.
		 *
		 * @since 1.2.4
		 *
		 * @param int $topic_posts Total number of posts in the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_total_posts', $forum_template->topic->topic_posts );
	}

/**
 * Output the tag count for the current topic in the loop.
 */
function bp_the_topic_tag_count() {
	echo bp_get_the_topic_tag_count();
}
	/**
	 * Return the tag count for the current topic in the loop.
	 *
	 * @return int Tag count for the current topic.
	 */
	function bp_get_the_topic_tag_count() {
		global $forum_template;

		/**
		 * Filters the tag count for the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $tag_count Tag count for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_tag_count', $forum_template->topic->tag_count );
	}

/**
 * Output the permalink of the current topic in the loop.
 */
function bp_the_topic_permalink() {
	echo bp_get_the_topic_permalink();
}
	/**
	 * Return the permalink for the current topic in the loop.
	 *
	 * @return string Permalink for the current topic.
	 */
	function bp_get_the_topic_permalink() {
		global $forum_template;

		// The topic is in a loop where its parent object is loaded.
		if ( bp_get_the_topic_object_slug() ) {
			$permalink = trailingslashit( bp_get_groups_directory_permalink() . bp_get_the_topic_object_slug() . '/forum' );

		// We are viewing a single group topic, so use the current item.
		} elseif ( bp_is_group_forum_topic() ) {
			$permalink = trailingslashit( bp_get_groups_directory_permalink() . bp_current_item() . '/forum' );

		// We are unsure what the context is, so fallback to forum root slug.
		} elseif ( bp_is_single_item() ) {
			$permalink = trailingslashit( bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/' . bp_current_item() );

		// This is some kind of error situation, so use forum root.
		} else {
			$permalink = trailingslashit( bp_get_root_domain() . '/' . bp_get_forums_root_slug() );
		}

		/**
		 * Filters the permalink for the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Permalink for the current topic in the loop.
		 */
		return apply_filters( 'bp_get_the_topic_permalink', trailingslashit( $permalink . 'topic/' . $forum_template->topic->topic_slug ) );
	}

/**
 * Output a 'since' string describing when the current topic was created.
 */
function bp_the_topic_time_since_created() {
	echo bp_get_the_topic_time_since_created();
}
	/**
	 * Return a 'since' string describing when the current topic was created.
	 *
	 * @see bp_core_time_since() for a description of return value.
	 *
	 * @return string
	 */
	function bp_get_the_topic_time_since_created() {
		global $forum_template;

		/**
		 * Filters a 'time since' string describing when the current topic was created.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value 'Time since' value for the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_time_since_created', bp_core_time_since( strtotime( $forum_template->topic->topic_start_time ) ) );
	}

/**
 * Output an excerpt from the latest post of the current topic in the loop.
 *
 * @param array|string $args See {@bp_get_the_topic_latest_post_excerpt()}.
 */
function bp_the_topic_latest_post_excerpt( $args = '' ) {
	echo bp_get_the_topic_latest_post_excerpt( $args );
}
	/**
	 * Return an excerpt from the latest post of the current topic in the loop.
	 *
	 * @param array|string $args {
	 *     @type int $length The length of the excerpted text. Default: 225.
	 * }
	 * @return string Post excerpt.
	 */
	function bp_get_the_topic_latest_post_excerpt( $args = '' ) {
		global $forum_template;

		$defaults = array(
			'length' => 225
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		$post = bp_forums_get_post( $forum_template->topic->topic_last_post_id );
		$post = bp_create_excerpt( $post->post_text, $length );

		/**
		 * Filters an excerpt from the latest post of the current topic in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $post   Post exceprt for the current topic.
		 * @param string $length String length to trim excerpt down to.
		 */
		return apply_filters( 'bp_get_the_topic_latest_post_excerpt', $post, $length );
	}

/**
 * Output a 'since' string describing when the last post in the current topic was created.
 */
function bp_the_topic_time_since_last_post() {
	echo bp_get_the_topic_time_since_last_post();
}
	/**
	 * Return a 'since' string describing when the last post in the current topic was created.
	 *
	 * @see bp_core_time_since() for a description of return value.
	 *
	 * @return string
	 */
	function bp_get_the_topic_time_since_last_post() {
		global $forum_template;

		/**
		 * Filters a 'time since' string describing when the last post in the current topic was created.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value The 'time since' string for the last post in the current topic.
		 */
		return apply_filters( 'bp_get_the_topic_time_since_last_post', bp_core_time_since( strtotime( $forum_template->topic->topic_time ) ) );
	}

/**
 * Output whether the current topic in the loop belongs to the logged-in user.
 */
function bp_the_topic_is_mine() {
	echo bp_get_the_topic_is_mine();
}
	/**
	 * Does the current topic belong to the logged-in user?
	 *
	 * @return bool True if the current topic in the loop was created by
	 *              the logged-in user, otherwise false.
	 */
	function bp_get_the_topic_is_mine() {
		global $forum_template;

		return bp_loggedin_user_id() == $forum_template->topic->topic_poster;
	}

/**
 * Output the admin links for the current topic in the loop.
 *
 * @see bp_get_the_topic_admin_links() for a description of arguments.
 *
 * @param array|string $args See {@link bp_get_the_topic_admin_links()}.
 */
function bp_the_topic_admin_links( $args = '' ) {
	echo bp_get_the_topic_admin_links( $args );
}
	/**
	 * Return the admin links for the current topic in the loop.
	 *
	 * @param array|string $args {
	 *     @type string $separator The character to use when separating links. Default: '|'.
	 * }
	 * @return string HTML string containing the admin links for the current topic.
	 */
	function bp_get_the_topic_admin_links( $args = '' ) {
		global $forum_template;

		$defaults = array(
			'separator' => '|'
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		$links[] = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'edit', 'bp_forums_edit_topic' ) . '">' . __( 'Edit Topic', 'buddypress' ) . '</a>';

		if ( bp_is_item_admin() || bp_is_item_mod() || bp_current_user_can( 'bp_moderate' ) ) {
			if ( 0 == (int) $forum_template->topic->topic_sticky )
				$links[] = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'stick', 'bp_forums_stick_topic' ) . '">' . __( 'Sticky Topic', 'buddypress' ) . '</a>';
			else
				$links[] = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'unstick', 'bp_forums_unstick_topic' ) . '">' . __( 'Un-stick Topic', 'buddypress' ) . '</a>';

			if ( 0 == (int) $forum_template->topic->topic_open )
				$links[] = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'open', 'bp_forums_open_topic' ) . '">' . __( 'Open Topic', 'buddypress' ) . '</a>';
			else
				$links[] = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'close', 'bp_forums_close_topic' ) . '">' . __( 'Close Topic', 'buddypress' ) . '</a>';

			$links[] = '<a class="confirm" id="topic-delete-link" href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'delete', 'bp_forums_delete_topic' ) . '">' . __( 'Delete Topic', 'buddypress' ) . '</a>';
		}

		return implode( ' ' . $separator . ' ', (array) $links );
	}

/**
 * Output the CSS class for the current topic in the loop.
 */
function bp_the_topic_css_class() {
	echo bp_get_the_topic_css_class();
}
	/**
	 * Return the CSS class for the current topic in the loop.
	 *
	 * This class may contain keywords like 'alt', 'sticky', or 'closed',
	 * based on context.
	 *
	 * @return string Contents of the 'class' attribute.
	 */
	function bp_get_the_topic_css_class() {
		global $forum_template;

		$class = false;

		if ( $forum_template->current_topic % 2 == 1 )
			$class .= 'alt';

		if ( isset( $forum_template->topic->topic_sticky ) && 1 == (int) $forum_template->topic->topic_sticky )
			$class .= ' sticky';

		if ( !isset( $forum_template->topic->topic_open ) || 0 == (int) $forum_template->topic->topic_open )
			$class .= ' closed';

		/**
		 * Filters the CSS class for the current topic in the loop.
		 *
		 * This class may contain keywords like 'alt', 'sticky', or 'closed',
		 * based on context.
		 *
		 * @since 1.2.4
		 *
		 * @param string $value Concatenated classes for the current topic in the loop.
		 */
		return apply_filters( 'bp_get_the_topic_css_class', trim( $class ) );
	}

/**
 * Output the permalink to the 'personal' topics tab.
 */
function bp_my_forum_topics_link() {
	echo bp_get_my_forum_topics_link();
}
	/**
	 * Return the permalink to the 'personal' topics tab.
	 *
	 * @return string Link to the 'personal' topics tab.
	 */
	function bp_get_my_forum_topics_link() {
		/**
		 * Filters the permalink to the 'personal' topics tab.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Permalink to the 'personal' topics tab.
		 */
		return apply_filters( 'bp_get_my_forum_topics_link', bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/personal/' );
	}

/**
 * Output the permalink to the 'unreplied' topics tab.
 */
function bp_unreplied_forum_topics_link() {
	echo bp_get_unreplied_forum_topics_link();
}
	/**
	 * Return the permalink to the 'unreplied' topics tab.
	 *
	 * @return string Link to the 'unreplied' topics tab.
	 */
	function bp_get_unreplied_forum_topics_link() {
		/**
		 * Filters the permalink to the 'unreplied' topics tab.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Permalink to the 'unreplied' topics tab.
		 */
		return apply_filters( 'bp_get_unreplied_forum_topics_link', bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/unreplied/' );
	}

/**
 * Output the permalink to the 'popular' topics tab.
 */
function bp_popular_forum_topics_link() {
	echo bp_get_popular_forum_topics_link();
}
	/**
	 * Return the permalink to the 'popular' topics tab.
	 *
	 * @return string Link to the 'popular' topics tab.
	 */
	function bp_get_popular_forum_topics_link() {
		/**
		 * Filters the permalink to the 'popular' topics tab.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Permalink to the 'popular' topics tab.
		 */
		return apply_filters( 'bp_get_popular_forum_topics_link', bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/popular/' );
	}

/**
 * Output the link to the forums directory.
 */
function bp_newest_forum_topics_link() {
	echo bp_get_newest_forum_topics_link();
}
	/**
	 * Return the link to the forums directory.
	 *
	 * @return string Link to the forums directory.
	 */
	function bp_get_newest_forum_topics_link() {
		/**
		 * Filters the link to the forums directory.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Link to the forums directory.
		 */
		return apply_filters( 'bp_get_newest_forum_topics_link', bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/' );
	}

/**
 * Output the currently viewed topic list type.
 */
function bp_forum_topic_type() {
	echo bp_get_forum_topic_type();
}
	/**
	 * Return the currently viewed topic list type.
	 *
	 * Eg, 'newest', 'popular', etc.
	 *
	 * @return string Type of the currently viewed topic list.
	 */
	function bp_get_forum_topic_type() {

		if ( !bp_is_directory() || !bp_current_action() )
			return 'newest';

		/**
		 * Filters the currently viewed topic list type.
		 *
		 * Eg, 'newest', 'popular', etc.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Currently viewed topic list type.
		 */
		return apply_filters( 'bp_get_forum_topic_type', bp_current_action() );
	}

/**
 * Output the value of bp_get_forum_topic_new_reply_link().
 *
 * @since 1.5.0
 */
function bp_forum_topic_new_reply_link() {
	echo esc_url( bp_get_forum_topic_new_reply_link() );
}
	/**
	 * Return the permalink for the New Reply button at the top of forum topics.
	 *
	 * @since 1.5.0
	 *
	 * @uses apply_filters() Filter bp_get_forum_topic_new_reply_link to
	 *       modify.
	 *
	 * @return string The URL for the New Reply link.
	 */
	function bp_get_forum_topic_new_reply_link() {
		global $topic_template;

		if ( $topic_template->pag->total_pages == $topic_template->pag_page ) {
			// If we are on the last page, no need for a URL base.
			$link = '';
		} else {
			// Create a link to the last page for the topic.
			$link = add_query_arg( array(
				'topic_page' =>	$topic_template->pag->total_pages,
				'num'        => $topic_template->pag_num
			), bp_get_the_topic_permalink() );
		}

		/**
		 * Filters the permalink for the New Reply button at the top of forum topics.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Permalink for the New Reply button.
		 */
		return apply_filters( 'bp_get_forum_topic_new_reply_link', $link . '#post-topic-reply', $link );
	}

/**
 * Output the currently viewed tag name.
 *
 * @todo Deprecate?
 */
function bp_forums_tag_name() {
	echo bp_get_forums_tag_name();
}
	/**
	 * Return the currently viewed tag name.
	 *
	 * @todo Deprecate? Seems unused
	 */
	function bp_get_forums_tag_name() {
		$tag_name = bp_is_directory() && bp_is_forums_component() ? bp_action_variable( 0 ) : false;

		return apply_filters( 'bp_get_forums_tag_name', $tag_name );
	}

/**
 * Output the pagination links for the current topic list.
 */
function bp_forum_pagination() {
	echo bp_get_forum_pagination();
}
	/**
	 * Return the pagination links for the current topic list.
	 *
	 * @return string HTML pagination links.
	 */
	function bp_get_forum_pagination() {
		global $forum_template;

		/**
		 * Filters the pagination links for the current topic list.
		 *
		 * @since 1.0.0
		 *
		 * @param string $pag_links HTML pagination links.
		 */
		return apply_filters( 'bp_get_forum_pagination', $forum_template->pag_links );
	}

/**
 * Output the pagination count for the current topic list.
 */
function bp_forum_pagination_count() {
	echo bp_get_forum_pagination_count();
}
	/**
	 * Return the pagination count for the current topic list.
	 *
	 * The "count" is a string of the form "Viewing x of y topics".
	 *
	 * @return string
	 */
	function bp_get_forum_pagination_count() {
		global $forum_template;

		$start_num  = intval( ( $forum_template->pag_page - 1 ) * $forum_template->pag_num ) + 1;
		$from_num   = bp_core_number_format( $start_num );
		$to_num     = bp_core_number_format( ( $start_num + ( $forum_template->pag_num - 1  ) > $forum_template->total_topic_count ) ? $forum_template->total_topic_count : $start_num + ( $forum_template->pag_num - 1 ) );
		$total      = bp_core_number_format( $forum_template->total_topic_count );
		$pag_filter = false;

		if ( 'tags' == $forum_template->type && !empty( $forum_template->search_terms ) )
			$pag_filter = sprintf( __( ' matching tag "%s"', 'buddypress' ), $forum_template->search_terms );

		if ( 1 == $forum_template->total_topic_count ) {
			$message = __( 'Viewing 1 topic', 'buddypress' );
		} else {
			$message = sprintf( _n( 'Viewing %1$s - %2$s of %3$s topic', 'Viewing %1$s - %2$s of %3$s topics', (int) $forum_template->total_topic_count, 'buddypress' ), $from_num, $to_num, $total, $pag_filter );
		}

		/**
		 * Filters the pagination count for the current topic list.
		 *
		 * @since 1.5.0
		 *
		 * @param string $message  Pagination count for the current topic list.
		 * @param string $from_num Low end count in the view.
		 * @param string $to_num   High end count in the view.
		 * @param string $total    Total count of topics found.
		 */
		return apply_filters( 'bp_get_forum_pagination_count', $message, $from_num, $to_num, $total );
	}

/**
 * Are we currently on an Edit Topic screen?
 *
 * @return bool True if currently editing a topic, otherwise false.
 */
function bp_is_edit_topic() {

	if ( bp_is_action_variable( 'post' ) && bp_is_action_variable( 'edit' ) )
		return false;

	return true;
}

/**
 * The single forum topic template loop class.
 *
 * Responsible for loading a topic's posts into a loop for display.
 */
class BP_Forums_Template_Topic {
	/**
	 * The loop iterator.
	 *
	 * @var int
	 */
	public $current_post = -1;

	/**
	 * The number of posts returned by the paged query.
	 *
	 * @var int
	 */
	public $post_count;

	/**
	 * Array of posts located by the query.
	 *
	 * @var array
	 */
	public $posts;

	/**
	 * The post object currently being iterated on.
	 *
	 * @var object
	 */
	public $post;

	/**
	 * The ID of the forum whose topic is being queried.
	 *
	 * @var int
	 */
	public $forum_id;

	/**
	 * The ID of the topic whose posts are being queried.
	 *
	 * @var int
	 */
	public $topic_id;

	/**
	 * The topic object to which the posts belong.
	 *
	 * @var object
	 */
	public $topic;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @var bool
	 */
	public $in_the_loop;

	/**
	 * Contains a 'total_pages' property holding total number of pages in
	 * this loop.
	 *
	 * @since 1.2.0
	 * @var stdClass
	 */
	public $pag;

	/**
	 * The page number being requested.
	 *
	 * @var int
	 */
	public $pag_page;

	/**
	 * The number of items being requested per page.
	 *
	 * @var int
	 */
	public $pag_num;

	/**
	 * An HTML string containing pagination links.
	 *
	 * @var string
	 */
	public $pag_links;

	/**
	 * The total number of posts matching the query parameters.
	 *
	 * @var int
	 */
	public $total_post_count;

	/**
	 * Whether requesting a single topic. Not currently used.
	 *
	 * @var bool
	 */
	public $single_post = false;

	/**
	 * Term to sort by.
	 *
	 * @var string
	 */
	public $sort_by;

	/**
	 * Sort order.
	 *
	 * @var string
	 */
	public $order;

	/**
	 * Constructor method.
	 *
	 * @param int    $topic_id ID of the topic whose posts are being requested.
	 * @param int    $per_page Number of items to return per page.
	 * @param int    $max Max records to return.
	 * @param string $order Direction to order results.
	 */
	function __construct( $topic_id, $per_page, $max, $order ) {
		global $forum_template;

		if ( !isset( $forum_template ) ) {
			$forum_template = new stdClass;
		}

		$this->pag_page        = isset( $_REQUEST['topic_page'] ) ? intval( $_REQUEST['topic_page'] ) : 1;
		$this->pag_num         = isset( $_REQUEST['num'] ) ? intval( $_REQUEST['num'] ) : $per_page;

		$this->order           = $order;
		$this->topic_id        = $topic_id;
		$forum_template->topic = (object) bp_forums_get_topic_details( $this->topic_id );
		$this->forum_id        = $forum_template->topic->forum_id;

		$this->posts           = bp_forums_get_topic_posts( array( 'topic_id' => $this->topic_id, 'page' => $this->pag_page, 'per_page' => $this->pag_num, 'order' => $this->order ) );

		if ( !$this->posts ) {
			$this->post_count       = 0;
			$this->total_post_count = 0;
		} else {
			if ( !$max || $max >= (int) $forum_template->topic->topic_posts ) {
				$this->total_post_count = (int) $forum_template->topic->topic_posts;
			} else {
				$this->total_post_count = (int) $max;
			}

			if ( $max ) {
				if ( $max >= count( $this->posts ) ) {
					$this->post_count = count( $this->posts );
				} else {
					$this->post_count = (int) $max;
				}
			} else {
				$this->post_count = count( $this->posts );
			}
		}

		// Load topic tags.
		$this->topic_tags = bb_get_topic_tags( $this->topic_id );

		$this->pag = new stdClass;

		if ( (int) $this->total_post_count && (int) $this->pag_num ) {
			$this->pag_links = paginate_links( array(
				'base'      => add_query_arg( array( 'topic_page' => '%#%', 'num' => (int) $this->pag_num ) ),
				'format'    => '',
				'total'     => ceil( (int) $this->total_post_count / (int) $this->pag_num ),
				'current'   => $this->pag_page,
				'prev_text' => _x( '&larr;', 'Forum thread pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Forum thread pagination next text', 'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => array(),
			) );

			$this->pag->total_pages = ceil( (int) $this->total_post_count / (int) $this->pag_num );
		} else {
			$this->pag->total_pages = 1;
		}
	}

	/**
	 * Whether there are posts available in the loop.
	 *
	 * @see bp_has_forum_topic_posts()
	 *
	 * @return bool True if there are items in the loop, otherwise false.
	 */
	function has_posts() {
		if ( $this->post_count )
			return true;

		return false;
	}

	/**
	 * Set up the next post and iterate index.
	 *
	 * @return object The next post to iterate over.
	 */
	function next_post() {
		$this->current_post++;
		$this->post = $this->posts[$this->current_post];

		return $this->post;
	}

	/**
	 * Rewind the posts and reset post index.
	 */
	function rewind_posts() {
		$this->current_post = -1;
		if ( $this->post_count > 0 ) {
			$this->post = $this->posts[0];
		}
	}

	/**
	 * Whether there are posts left in the loop to iterate over.
	 *
	 * This method is used by {@link bp_forum_topic_posts()} as part of
	 * the while loop that controls iteration inside the blogs loop, eg:
	 *     while ( bp_forum_topic_posts() ) { ...
	 *
	 * @see bp_forum_topic_posts()
	 *
	 * @return bool True if there are more posts to show, otherwise false.
	 */
	function user_posts() {
		if ( $this->current_post + 1 < $this->post_count ) {
			return true;
		} elseif ( $this->current_post + 1 == $this->post_count ) {

			/**
			 * Fires right before the rewinding of user posts.
			 *
			 * @since 1.5.0
			 */
			do_action('topic_loop_end');
			// Do some cleaning up after the loop.
			$this->rewind_posts();
		}

		$this->in_the_loop = false;
		return false;
	}

	/**
	 * Set up the current topic in the loop.
	 *
	 * @see bp_the_forum_topic_post()
	 */
	function the_post() {

		$this->in_the_loop = true;
		$this->post = $this->next_post();
		$this->post = (object)$this->post;

		if ( $this->current_post == 0 ) {

			/**
			 * Fires if the current post item is the first in the topic loop.
			 *
			 * @since 1.5.0
			 */
			do_action( 'topic_loop_start' );
		}
	}
}

/**
 * Initiate the loop for a single topic's posts.
 *
 * @param array|string $args {
 *     Arguments for limiting the contents of the topic posts loop.
 *     @type int    $topic_id ID of the topic to which the posts belong.
 *     @type int    $per_page Number of items to return per page. Default: 15.
 *     @type int    $max      Max items to return. Default: false.
 *     @type string $order   'ASC' or 'DESC'.
 * }
 * @return bool True when posts are found corresponding to the args,
 *              otherwise false.
 */
function bp_has_forum_topic_posts( $args = '' ) {
	global $topic_template;

	$defaults = array(
		'topic_id' => false,
		'per_page' => 15,
		'max'      => false,
		'order'    => 'ASC'
	);

	$r = bp_parse_args( $args, $defaults, 'has_forum_topic_posts' );
	extract( $r, EXTR_SKIP );

	if ( empty( $topic_id ) && bp_is_groups_component() && bp_is_current_action( 'forum' ) && bp_is_action_variable( 'topic', 0 ) && bp_action_variable( 1 ) )
		$topic_id = bp_forums_get_topic_id_from_slug( bp_action_variable( 1 ) );
	elseif ( empty( $topic_id ) && bp_is_forums_component() && bp_is_current_action( 'topic' ) && bp_action_variable( 0 ) )
		$topic_id = bp_forums_get_topic_id_from_slug( bp_action_variable( 0 ) );

	if ( empty( $topic_id ) ) {
		return false;

	} else {
		$topic_template = new BP_Forums_Template_Topic( (int) $topic_id, $per_page, $max, $order );

		// Current topic forum_id needs to match current_group forum_id.
		if ( bp_is_groups_component() && $topic_template->forum_id != groups_get_groupmeta( bp_get_current_group_id(), 'forum_id' ) )
			return false;
	}

	/**
	 * Filters whether or not there are topics to display.
	 *
	 * @since 1.1.0
	 *
	 * @param bool                     $value          Whether or not there are topics.
	 * @param BP_Forums_Template_Topic $topic_template Topic template global to use when rendering.
	 */
	return apply_filters( 'bp_has_topic_posts', $topic_template->has_posts(), $topic_template );
}

/**
 * Determine whether there are posts left in the loop.
 *
 * @return bool True when posts are found.
 */
function bp_forum_topic_posts() {
	global $topic_template;
	return $topic_template->user_posts();
}

/**
 * Set up the current post in the loop.
 *
 * @return object
 */
function bp_the_forum_topic_post() {
	global $topic_template;
	return $topic_template->the_post();
}

/**
 * Output the ID of the current post in the loop.
 */
function bp_the_topic_post_id() {
	echo bp_get_the_topic_post_id();
}
	/**
	 * Return the ID of the current post in the loop.
	 *
	 * @return int ID of the current post in the loop.
	 */
	function bp_get_the_topic_post_id() {
		global $topic_template;

		/**
		 * Filters the ID of the current post in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param int $post_id ID of the current post.
		 */
		return apply_filters( 'bp_get_the_topic_post_id', $topic_template->post->post_id );
	}

/**
 * Output the content of the current post in the loop.
 */
function bp_the_topic_post_content() {
	echo bp_get_the_topic_post_content();
}
	/**
	 * Return the content of the current post in the loop.
	 *
	 * @return string Content of the current post.
	 */
	function bp_get_the_topic_post_content() {
		global $topic_template;

		/**
		 * Filters the content of the current post in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $post_text The content of the current post.
		 */
		return apply_filters( 'bp_get_the_topic_post_content', stripslashes( $topic_template->post->post_text ) );
	}

/**
 * Output the CSS class of the current post in the loop.
 */
function bp_the_topic_post_css_class() {
	echo bp_get_the_topic_post_css_class();
}
	/**
	 * Return the CSS class of the current post in the loop.
	 *
	 * May contain strings 'alt', 'deleted', or 'open', depending on
	 * context.
	 *
	 * @return string String to put in the 'class' attribute of the current
	 *                post.
	 */
	function bp_get_the_topic_post_css_class() {
		global $topic_template;

		$class = false;

		if ( $topic_template->current_post % 2 == 1 )
			$class .= 'alt';

		if ( 1 == (int) $topic_template->post->post_status )
			$class .= ' deleted';

		if ( 0 == (int) $topic_template->post->post_status )
			$class .= ' open';

		/**
		 * Filters the CSS class for the current post in the loop.
		 *
		 * This class may contain keywords like 'alt', 'deleted', or 'open',
		 * based on context.
		 *
		 * @since 1.2.4
		 *
		 * @param string $value Concatenated classes for the current post in the loop.
		 */
		return apply_filters( 'bp_get_the_topic_post_css_class', trim( $class ) );
	}

/**
 * Output the avatar of the user who posted the current post in the loop.
 *
 * @see bp_get_the_topic_post_poster_avatar() for a description of arguments.
 *
 * @param array|string $args See {@link bp_get_the_topic_post_poster_avatar()}.
 */
function bp_the_topic_post_poster_avatar( $args = '' ) {
	echo bp_get_the_topic_post_poster_avatar( $args );
}
	/**
	 * Return the avatar of the user who posted the current post in the loop.
	 *
	 * @param array|string $args {
	 *     Arguments for building the avatar.
	 *     @type string $type   Avatar type. 'thumb' or 'full'. Default:
	 *                          'thumb'.
	 *     @type int    $width  Width of the avatar, in pixels. Default: the
	 *                          width corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type int    $height Height of the avatar, in pixels. Default: the
	 *                          height corresponding to $type.
	 *                          See {@link bp_core_fetch_avatar()}.
	 *     @type string $alt    The text of the image's 'alt' attribute.
	 *                          Default: 'Profile picture of [user name]'.
	 * }
	 * @return string HTML of user avatar.
	 */
	function bp_get_the_topic_post_poster_avatar( $args = '' ) {
		global $topic_template;

		$defaults = array(
			'type' => 'thumb',
			'width' => 20,
			'height' => 20,
			'alt' => __( 'Profile picture of %s', 'buddypress' )
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		/**
		 * Filters the avatar of the user who posted the current post in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Avatar of the user who posted the current post.
		 */
		return apply_filters( 'bp_get_the_topic_post_poster_avatar', bp_core_fetch_avatar( array( 'item_id' => $topic_template->post->poster_id, 'type' => $type, 'width' => $width, 'height' => $height, 'alt' => $alt ) ) );
	}

/**
 * Output the name of the user who posted the current post in the loop.
 */
function bp_the_topic_post_poster_name() {
	echo bp_get_the_topic_post_poster_name();
}
	/**
	 * Return the name of the user who posted the current post in the loop.
	 *
	 * @return string Name of the user who posted the current post.
	 */
	function bp_get_the_topic_post_poster_name() {
		global $topic_template;

		if ( empty( $topic_template->post->poster_name ) || ( !$link = bp_core_get_user_domain( $topic_template->post->poster_id ) ) )
			return __( 'Deleted User', 'buddypress' );

		/**
		 * Filters the name of the user who posted the current post in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value HTML link for the poster's name.
		 */
		return apply_filters( 'bp_get_the_topic_post_poster_name', '<a href="' . $link . '" title="' . $topic_template->post->poster_name . '">' . $topic_template->post->poster_name . '</a>' );
	}

/**
 * Output a link to the profile of the user who posted the current post.
 */
function bp_the_topic_post_poster_link() {
	echo bp_get_the_topic_post_poster_link();
}
	/**
	 * Return a link to the profile of the user who posted the current post.
	 *
	 * @return string Link to the profile of the user who posted the
	 *                current post.
	 */
	function bp_get_the_topic_post_poster_link() {
		global $topic_template;

		/**
		 * Filters a link to the profile of the user who posted the current post.
		 *
		 * @since 1.2.1
		 *
		 * @param string $value Link for the current poster's profile.
		 */
		return apply_filters( 'bp_the_topic_post_poster_link', bp_core_get_user_domain( $topic_template->post->poster_id ) );
	}

/**
 * Output a 'since' string describing when the current post in the loop was posted.
 */
function bp_the_topic_post_time_since() {
	echo bp_get_the_topic_post_time_since();
}
	/**
	 * Return a 'since' string describing when the current post in the loop was posted.
	 *
	 * @see bp_core_time_since() for a description of return value.
	 *
	 * @return string
	 */
	function bp_get_the_topic_post_time_since() {
		global $topic_template;

		/**
		 * Filters the 'since' string describing when the current post in the loop was posted.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value The 'since' string.
		 */
		return apply_filters( 'bp_get_the_topic_post_time_since', bp_core_time_since( strtotime( $topic_template->post->post_time ) ) );
	}

/**
 * Output whether the current post in the loop belongs to the logged-in user.
 */
function bp_the_topic_post_is_mine() {
	echo bp_get_the_topic_post_is_mine();
}
	/**
	 * Does the current post belong to the logged-in user?
	 *
	 * @return bool True if the current post in the loop was created by
	 *              the logged-in user, otherwise false.
	 */
	function bp_get_the_topic_post_is_mine() {
		global $topic_template;

		return bp_loggedin_user_id() == $topic_template->post->poster_id;
	}

/**
 * Output the admin links for the current post in the loop.
 *
 * @see bp_get_the_post_admin_links() for a description of arguments.
 *
 * @param array|string $args See {@link bp_get_the_post_admin_links()}.
 */
function bp_the_topic_post_admin_links( $args = '' ) {
	echo bp_get_the_topic_post_admin_links( $args );
}
	/**
	 * Return the admin links for the current post in the loop.
	 *
	 * @param array|string $args {
	 *     @type string $separator The character to use when separating
	 *                             links. Default: '|'.
	 * }
	 * @return string HTML string containing the admin links for the current post.
	 */
	function bp_get_the_topic_post_admin_links( $args = '' ) {
		global $topic_template;

		// Never show for the first post in a topic.
		if ( 0 == $topic_template->current_post && 1 == $topic_template->pag_page )
			return;

		$defaults = array(
			'separator' => ' | '
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		$query_vars = '';
		if ( $_SERVER['QUERY_STRING'] )
			$query_vars = '?' . $_SERVER['QUERY_STRING'];

		$links    = array();
		$links[]  = '<a href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'edit/post/' . $topic_template->post->post_id . '/' . $query_vars, 'bp_forums_edit_post' ) . '">' . __( 'Edit', 'buddypress' ) . '</a>';
		$links[] .= '<a class="confirm" id="post-delete-link" href="' . wp_nonce_url( bp_get_the_topic_permalink() . 'delete/post/' . $topic_template->post->post_id, 'bp_forums_delete_post' ) . '">' . __( 'Delete', 'buddypress' ) . '</a>';

		/**
		 * Filters the admin links for the current post in the loop.
		 *
		 * @since 1.2.7
		 *
		 * @param string $value HTML string containing the admin links for the current post.
		 */
		return apply_filters( 'bp_get_the_topic_post_admin_links', implode( $separator, $links ), $links, $r );
	}

/**
 * Output the text to edit when editing a post.
 */
function bp_the_topic_post_edit_text() {
	echo bp_get_the_topic_post_edit_text();
}
	/**
	 * Return the text to edit when editing a post.
	 *
	 * @return string Editable text.
	 */
	function bp_get_the_topic_post_edit_text() {
		$post = bp_forums_get_post( bp_action_variable( 4 ) );

		/**
		 * Filters the text to edit when editing a post.
		 *
		 * @since 1.2.4
		 *
		 * @param string $value The text to edit when editing a post.
		 */
		return apply_filters( 'bp_get_the_topic_post_edit_text', esc_attr( $post->post_text ) );
	}

/**
 * Output the pagination links for the current topic.
 */
function bp_the_topic_pagination() {
	echo bp_get_the_topic_pagination();
}
	/**
	 * Return the pagination links for the current topic page.
	 *
	 * @return string HTML pagination links.
	 */
	function bp_get_the_topic_pagination() {
		global $topic_template;


		/**
		 * Filters the pagination links for the current topic page.
		 *
		 * @since 1.0.0
		 *
		 * @param string $pag_links HTML pagination links.
		 */
		return apply_filters( 'bp_get_the_topic_pagination', $topic_template->pag_links );
	}

/**
 * Return the pagination count for the current topic page.
 *
 * The "count" is a string of the form "Viewing x of y posts".
 */
function bp_the_topic_pagination_count() {
	global $topic_template;

	$start_num = intval( ( $topic_template->pag_page - 1 ) * $topic_template->pag_num ) + 1;
	$from_num = bp_core_number_format( $start_num );
	$to_num = bp_core_number_format( ( $start_num + ( $topic_template->pag_num - 1  ) > $topic_template->total_post_count ) ? $topic_template->total_post_count : $start_num + ( $topic_template->pag_num - 1 ) );
	$total = bp_core_number_format( $topic_template->total_post_count );

	if ( 1 == $topic_template->total_post_count ) {
		$message = __( 'Viewing 1 post', 'buddypress' );
	} else {
		$message = sprintf( _n( 'Viewing %1$s - %2$s of %3$s post', 'Viewing %1$s - %2$s of %3$s posts', (int) $topic_template->total_post_count, 'buddypress' ), $from_num, $to_num, $total );
	}

	/**
	 * Filters the pagination count for the current topic page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $message  Pagination count for the current topic page.
	 * @param string $from_num Low end count in the view.
	 * @param string $to_num   High end count in the view.
	 * @param string $total    Total count of topics found.
	 */
	echo apply_filters( 'bp_the_topic_pagination_count', $message, $from_num, $to_num, $total );
}

/**
 * Output whether this is the last page in the current topic.
 */
function bp_the_topic_is_last_page() {
	echo bp_get_the_topic_is_last_page();
}
	/**
	 * Is this the last page in the current topic?
	 *
	 * @return bool True if this is the last page of posts for the current
	 *              topic, otherwise false.
	 */
	function bp_get_the_topic_is_last_page() {
		global $topic_template;

		/**
		 * Filters whether or not a user is on the last page in the current topic.
		 *
		 * @since 1.2.0
		 *
		 * @param bool $value Whether or not user is on last page.
		 */
		return apply_filters( 'bp_get_the_topic_is_last_page', $topic_template->pag_page == $topic_template->pag->total_pages );
	}

/**
 * Output the forums directory search form.
 */
function bp_directory_forums_search_form() {
	$default_search_value = bp_get_search_default_text( 'forums' );
	$search_value = !empty( $_REQUEST['fs'] ) ? stripslashes( $_REQUEST['fs'] ) : $default_search_value;

	$search_form_html = '<form action="" method="get" id="search-forums-form">
		<label for="forums_search"><input type="text" name="s" id="forums_search" placeholder="'. esc_attr( $search_value ) .'" /></label>
		<input type="submit" id="forums_search_submit" name="forums_search_submit" value="' . __( 'Search', 'buddypress' ) . '" />
	</form>';

	/**
	 * Filters the forums directory search form.
	 *
	 * @since 1.9.0
	 *
	 * @param string $search_form_html HTML search form for the forums directory.
	 */
	echo apply_filters( 'bp_directory_forums_search_form', $search_form_html );
}

/**
 * Output the link to a given forum.
 *
 * @see bp_get_forum_permalink() for a description of arguments.
 *
 * @param int $forum_id See {@link bp_get_forum_permalink()}.
 */
function bp_forum_permalink( $forum_id = 0 ) {
	echo bp_get_forum_permalink( $forum_id );
}
	/**
	 * Return the permalink to a given forum.
	 *
	 * @param int $forum_id Optional. Defaults to the current forum, if
	 *                      there is one.
	 * @return string|bool False on failure, a URL on success.
	 */
	function bp_get_forum_permalink( $forum_id = 0 ) {

		if ( bp_is_groups_component() ) {
			$permalink = trailingslashit( bp_get_groups_directory_permalink() . bp_current_item() . '/forum' );
		} else {
			if ( empty( $forum_id ) ) {
				global $topic_template;
				if ( isset( $topic_template->forum_id ) )
					$forum_id = $topic_template->forum_id;
			}

			if ( $forum = bp_forums_get_forum( $forum_id ) )
				$permalink = trailingslashit( bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/forum/' . $forum->forum_slug );
			else
				return false;
		}

		/**
		 * Filters the permalink to a given forum.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Peramlink to the given forum.
		 */
		return apply_filters( 'bp_get_forum_permalink', trailingslashit( $permalink ) );
	}

/**
 * Output the name of a given forum.
 *
 * @see bp_get_forum_name() for a description of parameters.
 *
 * @param int $forum_id See {@link bp_get_forum_name()}.
 */
function bp_forum_name( $forum_id = 0 ) {
	echo bp_get_forum_name( $forum_id );
}
	/**
	 * Return the name of a given forum.
	 *
	 * @param int $forum_id Optional. Defaults to the current forum, if
	 *                      there is one.
	 * @return string|bool False on failure, a name on success.
	 */
	function bp_get_forum_name( $forum_id = 0 ) {

		if ( empty( $forum_id ) ) {
			global $topic_template;
			if ( isset( $topic_template->forum_id ) )
				$forum_id = $topic_template->forum_id;
		}

		if ( $forum = bp_forums_get_forum( $forum_id ) ) {

			/**
			 * Filters the name of a given forum.
			 *
			 * @since 1.5.0
			 *
			 * @param string $forum_name Name of the given forum.
			 * @param string $forum_id   ID of the given forum.
			 */
			return apply_filters( 'bp_get_forum_name', $forum->forum_name, $forum->forum_id );
		} else {
			return false;
		}
	}

/**
 * Get a heatmap of forum tags for the installation.
 *
 * A wrapper for {@link bb_tag_heat_map}, which provides it with BP-friendly
 * defaults.
 *
 * @param array|string $args {
 *     An array of optional arguments.
 *     @type int    $smallest Size of the smallest link. Default: 10.
 *     @type int    $largest  Size of the largest link. Default: 42.
 *     @type string $sizing   Unit for $largest and $smallest. Default: 'px'.
 *     @type int    $limit    Max number of tags to display. Default: 50.
 * }
 */
function bp_forums_tag_heat_map( $args = '' ) {
	$defaults = array(
		'smallest' => '10',
		'largest'  => '42',
		'sizing'   => 'px',
		'limit'    => '50'
	);

	$r = wp_parse_args( $args, $defaults );
	extract( $r, EXTR_SKIP );

	bb_tag_heat_map( $smallest, $largest, $sizing, $limit );
}

/**
 * Output the current topic's tag list, comma-separated.
 *
 * @since 1.5.0
 */
function bp_forum_topic_tag_list() {
	echo bp_get_forum_topic_tag_list();
}
	/**
	 * Get the current topic's tag list.
	 *
	 * @since 1.5.0
	 *
	 * @param string $format 'string' returns comma-separated string;
	 *                       otherwise returns array.
	 * @return mixed $tags
	 */
	function bp_get_forum_topic_tag_list( $format = 'string' ) {
		global $topic_template;

		$tags_data = !empty( $topic_template->topic_tags ) ? $topic_template->topic_tags : false;

		$tags = array();

		if ( $tags_data ) {
			foreach( $tags_data as $tag_data ) {
				$tags[] = $tag_data->name;
			}
		}

		if ( 'string' == $format )
			$tags = implode( ', ', $tags );

		/**
		 * Filters the current topic's tag list.
		 *
		 * @since 1.5.0
		 *
		 * @param string|array $tags   List or array of tags for the current topic.
		 * @param string       $format Requested format for the tags.
		 */
		return apply_filters( 'bp_forum_topic_tag_list', $tags, $format );
	}

/**
 * Does the current topic have any tags?
 *
 * @since 1.5.0
 *
 * @return bool True if the current topic has tags, otherwise false.
 */
function bp_forum_topic_has_tags() {
	global $topic_template;

	$has_tags = false;

	if ( !empty( $topic_template->topic_tags ) )
		$has_tags = true;

	/**
	 * Filters whether or not a forum topic has any tags.
	 *
	 * @since 1.5.0
	 *
	 * @param bool $has_tags Whether or not there are any tags.
	 */
	return apply_filters( 'bp_forum_topic_has_tags', $has_tags );
}

/**
 * Output a URL to use in as a forum form 'action'.
 */
function bp_forum_action() {
	echo bp_get_forum_action();
}
	/**
	 * Get a URL to use in as a forum form 'action'.
	 *
	 * @return string URL of the current page, minus query args.
	 */
	function bp_get_forum_action() {
		global $topic_template;

		/**
		 * Filters the url to use in a forum form 'action'.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value URL to use in the forum form 'action'.
		 */
		return apply_filters( 'bp_get_forum_action', bp_get_root_domain() . esc_attr( $_SERVER['REQUEST_URI'] ) );
	}

/**
 * Output a URL to use in as a forum topic form 'action'.
 */
function bp_forum_topic_action() {
	echo bp_get_forum_topic_action();
}
	/**
	 * Get a URL to use in as a forum topic form 'action'.
	 *
	 * @return string URL of the current page, minus query args.
	 */
	function bp_get_forum_topic_action() {

		/**
		 * Filters the url to use in a forum topic form 'action'.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value URL to use in the forum topic form 'action'.
		 */
		return apply_filters( 'bp_get_forum_topic_action', $_SERVER['REQUEST_URI'] );
	}

/**
 * Output the total topic count for a given user.
 *
 * @see bp_get_forum_topic_count_for_user() for description of parameters.
 *
 * @param int $user_id See {@link bp_get_forum_topic_count_for_user()}.
 */
function bp_forum_topic_count_for_user( $user_id = 0 ) {
	echo bp_get_forum_topic_count_for_user( $user_id );
}
	/**
	 * Return the total topic count for a given user.
	 *
	 * @param int $user_id See {@link bp_forums_total_topic_count_for_user}.
	 *
	 * @return int
	 */
	function bp_get_forum_topic_count_for_user( $user_id = 0 ) {

		/**
		 * Filters the total topic count for a given user.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Total topic count for the given user.
		 */
		return apply_filters( 'bp_get_forum_topic_count_for_user', bp_forums_total_topic_count_for_user( $user_id ) );
	}

/**
 * Output the total topic count for a given user.
 *
 * @see bp_get_forum_topic_count() for description of parameters.
 *
 * @param int $user_id See {@link bp_get_forum_topic_count()}.
 */
function bp_forum_topic_count( $user_id = 0 ) {
	echo bp_get_forum_topic_count( $user_id );
}
	/**
	 * Return the total topic count for a given user.
	 *
	 * @param int $user_id See {@link bp_forums_total_topic_count()}.
	 *
	 * @return int
	 */
	function bp_get_forum_topic_count( $user_id = 0 ) {

		/**
		 * Filters the total topic count for a given user.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Total topic count for the given user.
		 */
		return apply_filters( 'bp_get_forum_topic_count', bp_forums_total_topic_count( $user_id ) );
	}
