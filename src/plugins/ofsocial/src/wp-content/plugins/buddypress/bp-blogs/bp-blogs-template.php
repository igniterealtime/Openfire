<?php
/**
 * BuddyPress Blogs Template Tags.
 *
 * @package BuddyPress
 * @subpackage BlogsTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the blogs component slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_blogs_slug()
 */
function bp_blogs_slug() {
	echo bp_get_blogs_slug();
}
	/**
	 * Return the blogs component slug.
	 *
	 * @since 1.5.0
	 *
	 * @return string The 'blogs' slug.
	 */
	function bp_get_blogs_slug() {

		/**
		 * Filters the blogs component slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $slug Slug for the blogs component.
		 */
		return apply_filters( 'bp_get_blogs_slug', buddypress()->blogs->slug );
	}

/**
 * Output the blogs component root slug.
 *
 * @since 1.5.0
 *
 * @uses bp_get_blogs_root_slug()
 */
function bp_blogs_root_slug() {
	echo bp_get_blogs_root_slug();
}
	/**
	 * Return the blogs component root slug.
	 *
	 * @since 1.5.0
	 *
	 * @return string The 'blogs' root slug.
	 */
	function bp_get_blogs_root_slug() {

		/**
		 * Filters the blogs component root slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $root_slug Root slug for the blogs component.
		 */
		return apply_filters( 'bp_get_blogs_root_slug', buddypress()->blogs->root_slug );
	}

/**
 * Output blog directory permalink.
 *
 * @since 1.5.0
 *
 * @uses bp_get_blogs_directory_permalink()
 */
function bp_blogs_directory_permalink() {
	echo esc_url( bp_get_blogs_directory_permalink() );
}
	/**
	 * Return blog directory permalink.
	 *
	 * @since 1.5.0
	 *
	 * @uses apply_filters()
	 * @uses trailingslashit()
	 * @uses bp_get_root_domain()
	 * @uses bp_get_blogs_root_slug()
	 *
	 * @return string The URL of the Blogs directory.
	 */
	function bp_get_blogs_directory_permalink() {

		/**
		 * Filters the blog directory permalink.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Permalink URL for the blog directory.
		 */
		return apply_filters( 'bp_get_blogs_directory_permalink', trailingslashit( bp_get_root_domain() . '/' . bp_get_blogs_root_slug() ) );
	}

/**
 * The main blog template loop class.
 *
 * Responsible for loading a group of blogs into a loop for display.
 */
class BP_Blogs_Template {

	/**
	 * The loop iterator.
	 *
	 * @var int
	 */
	public $current_blog = -1;

	/**
	 * The number of blogs returned by the paged query.
	 *
	 * @var int
	 */
	public $blog_count = 0;

	/**
	 * Array of blogs located by the query..
	 *
	 * @var array
	 */
	public $blogs = array();

	/**
	 * The blog object currently being iterated on.
	 *
	 * @var object
	 */
	public $blog;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @var bool
	 */
	public $in_the_loop = false;

	/**
	 * The page number being requested.
	 *
	 * @var int
	 */
	public $pag_page = 1;

	/**
	 * The number of items being requested per page.
	 *
	 * @var int
	 */
	public $pag_num = 20;

	/**
	 * An HTML string containing pagination links.
	 *
	 * @var string
	 */
	public $pag_links = '';

	/**
	 * The total number of blogs matching the query parameters.
	 *
	 * @var int
	 */
	public $total_blog_count = 0;

	/**
	 * Constructor method.
	 *
	 * @see BP_Blogs_Blog::get() for a description of parameters.
	 *
	 * @param string     $type              See {@link BP_Blogs_Blog::get()}.
	 * @param string     $page              See {@link BP_Blogs_Blog::get()}.
	 * @param string     $per_page          See {@link BP_Blogs_Blog::get()}.
	 * @param string     $max               See {@link BP_Blogs_Blog::get()}.
	 * @param string     $user_id           See {@link BP_Blogs_Blog::get()}.
	 * @param string     $search_terms      See {@link BP_Blogs_Blog::get()}.
	 * @param string     $page_arg          The string used as a query parameter in
	 *                                      pagination links. Default: 'bpage'.
	 * @param bool       $update_meta_cache Whether to pre-fetch metadata for
	 *                                      queried blogs.
	 * @param array|bool $include_blog_ids  Array of blog IDs to include.
	 */
	public function __construct( $type, $page, $per_page, $max, $user_id, $search_terms, $page_arg = 'bpage', $update_meta_cache = true, $include_blog_ids = false ) {

		$this->pag_arg  = sanitize_key( $page_arg );
		$this->pag_page = bp_sanitize_pagination_arg( $this->pag_arg, $page     );
		$this->pag_num  = bp_sanitize_pagination_arg( 'num',          $per_page );

		// Backwards compatibility support for blogs by first letter
		if ( ! empty( $_REQUEST['letter'] ) ) {
			$this->blogs = BP_Blogs_Blog::get_by_letter( $_REQUEST['letter'], $this->pag_num, $this->pag_page );

		// Typical blogs query
		} else {
			$this->blogs = bp_blogs_get_blogs( array(
				'type'              => $type,
				'per_page'          => $this->pag_num,
				'page'              => $this->pag_page,
				'user_id'           => $user_id,
				'search_terms'      => $search_terms,
				'update_meta_cache' => $update_meta_cache,
				'include_blog_ids'  => $include_blog_ids,
			) );
		}

		// Set the total blog count
		if ( empty( $max ) || ( $max >= (int) $this->blogs['total'] ) ) {
			$this->total_blog_count = (int) $this->blogs['total'];
		} else {
			$this->total_blog_count = (int) $max;
		}

		// Set the blogs array (to loop through later
		$this->blogs = $this->blogs['blogs'];

		// Get the current blog count to compare maximum against
		$blog_count = count( $this->blogs );

		// Set the current blog count
		if ( empty( $max ) || ( $max >= (int) $blog_count ) ) {
			$this->blog_count = (int) $blog_count;
		} else {
			$this->blog_count = (int) $max;
		}

		// Build pagination links based on total blogs and current page number
		if ( ! empty( $this->total_blog_count ) && ! empty( $this->pag_num ) ) {
			$this->pag_links = paginate_links( array(
				'base'      => add_query_arg( $this->pag_arg, '%#%' ),
				'format'    => '',
				'total'     => ceil( (int) $this->total_blog_count / (int) $this->pag_num ),
				'current'   => (int) $this->pag_page,
				'prev_text' => _x( '&larr;', 'Blog pagination previous text', 'buddypress' ),
				'next_text' => _x( '&rarr;', 'Blog pagination next text',     'buddypress' ),
				'mid_size'  => 1,
				'add_args'  => array(),
			) );
		}
	}

	/**
	 * Whether there are blogs available in the loop.
	 *
	 * @see bp_has_blogs()
	 *
	 * @return bool True if there are items in the loop, otherwise false.
	 */
	public function has_blogs() {
		return (bool) ! empty( $this->blog_count );
	}

	/**
	 * Set up the next blog and iterate index.
	 *
	 * @return object The next blog to iterate over.
	 */
	public function next_blog() {
		$this->current_blog++;
		$this->blog = $this->blogs[ $this->current_blog ];

		return $this->blog;
	}

	/**
	 * Rewind the blogs and reset blog index.
	 */
	public function rewind_blogs() {
		$this->current_blog = -1;
		if ( $this->blog_count > 0 ) {
			$this->blog = $this->blogs[0];
		}
	}

	/**
	 * Whether there are blogs left in the loop to iterate over.
	 *
	 * This method is used by {@link bp_blogs()} as part of the while loop
	 * that controls iteration inside the blogs loop, eg:
	 *     while ( bp_blogs() ) { ...
	 *
	 * @see bp_blogs()
	 *
	 * @return bool True if there are more blogs to show, otherwise false.
	 */
	public function blogs() {
		if ( ( $this->current_blog + 1 ) < $this->blog_count ) {
			return true;
		} elseif ( ( $this->current_blog + 1 ) === $this->blog_count ) {

			/**
			 * Fires right before the rewinding of blogs listing after all are shown.
			 *
			 * @since 1.5.0
			 */
			do_action( 'blog_loop_end' );
			// Do some cleaning up after the loop
			$this->rewind_blogs();
		}

		$this->in_the_loop = false;
		return false;
	}

	/**
	 * Set up the current blog inside the loop.
	 *
	 * Used by {@link bp_the_blog()} to set up the current blog data while
	 * looping, so that template tags used during that iteration make
	 * reference to the current blog.
	 *
	 * @see bp_the_blog()
	 */
	public function the_blog() {

		$this->in_the_loop = true;
		$this->blog        = $this->next_blog();

		// loop has just started
		if ( 0 === $this->current_blog ) {

			/**
			 * Fires if on the first blog in the loop.
			 *
			 * @since 1.5.0
			 */
			do_action( 'blog_loop_start' );
		}
	}
}

/**
 * Rewind the blogs and reset blog index.
 */
function bp_rewind_blogs() {
	global $blogs_template;

	$blogs_template->rewind_blogs();
}

/**
 * Initialize the blogs loop.
 *
 * Based on the $args passed, bp_has_blogs() populates the $blogs_template
 * global, enabling the use of BuddyPress templates and template functions to
 * display a list of activity items.
 *
 * @global object $blogs_template {@link BP_Blogs_Template}
 *
 * @param array|string $args {
 *     Arguments for limiting the contents of the blogs loop. Most arguments
 *     are in the same format as {@link BP_Blogs_Blog::get()}. However, because
 *     the format of the arguments accepted here differs in a number of ways,
 *     and because bp_has_blogs() determines some default arguments in a
 *     dynamic fashion, we list all accepted arguments here as well.
 *
 *     Arguments can be passed as an associative array, or as a URL query
 *     string (eg, 'user_id=4&per_page=3').
 *
 *     @type int      $page             Which page of results to fetch. Using page=1 without
 *                                      per_page will result in no pagination. Default: 1.
 *     @type int|bool $per_page         Number of results per page. Default: 20.
 *     @type string   $page_arg         The string used as a query parameter in
 *                                      pagination links. Default: 'bpage'.
 *     @type int|bool $max              Maximum number of results to return.
 *                                      Default: false (unlimited).
 *     @type string   $type             The order in which results should be fetched.
 *                                      'active', 'alphabetical', 'newest', or 'random'.
 *     @type array    $include_blog_ids Array of blog IDs to limit results to.
 *     @type string   $sort             'ASC' or 'DESC'. Default: 'DESC'.
 *     @type string   $search_terms     Limit results by a search term. Default: the value of `$_REQUEST['s']` or
 *                                      `$_REQUEST['sites_search']`, if present.
 *     @type int      $user_id          The ID of the user whose blogs should be retrieved.
 *                                      When viewing a user profile page, 'user_id' defaults to the
 *                                      ID of the displayed user. Otherwise the default is false.
 * }
 * @return bool Returns true when blogs are found, otherwise false.
 */
function bp_has_blogs( $args = '' ) {
	global $blogs_template;

	// Check for and use search terms.
	$search_terms_default = false;
	$search_query_arg = bp_core_get_component_search_query_arg( 'blogs' );
	if ( ! empty( $_REQUEST[ $search_query_arg ] ) ) {
		$search_terms_default = stripslashes( $_REQUEST[ $search_query_arg ] );
	} elseif ( ! empty( $_REQUEST['s'] ) ) {
		$search_terms_default = stripslashes( $_REQUEST['s'] );
	}

	// Parse arguments
	$r = bp_parse_args( $args, array(
		'type'              => 'active',
		'page_arg'          => 'bpage',                // See https://buddypress.trac.wordpress.org/ticket/3679
		'page'              => 1,
		'per_page'          => 20,
		'max'               => false,
		'user_id'           => bp_displayed_user_id(), // Pass a user_id to limit to only blogs this user is a member of
		'include_blog_ids'  => false,
		'search_terms'      => $search_terms_default,
		'update_meta_cache' => true
	), 'has_blogs' );

	// Set per_page to maximum if max is enforced
	if ( ! empty( $r['max'] ) && ( (int) $r['per_page'] > (int) $r['max'] ) ) {
		$r['per_page'] = (int) $r['max'];
	}

	// Get the blogs
	$blogs_template = new BP_Blogs_Template( $r['type'], $r['page'], $r['per_page'], $r['max'], $r['user_id'], $r['search_terms'], $r['page_arg'], $r['update_meta_cache'], $r['include_blog_ids'] );

	/**
	 * Filters whether or not there are blogs to list.
	 *
	 * @since 1.1.0
	 *
	 * @param bool              $value          Whether or not there are blogs to list.
	 * @param BP_Blogs_Template $blogs_template Current blogs template object.
	 * @param array             $r              Parsed arguments used in blogs template query.
	 */
	return apply_filters( 'bp_has_blogs', $blogs_template->has_blogs(), $blogs_template, $r );
}

/**
 * Determine if there are still blogs left in the loop.
 *
 * @global object $blogs_template {@link BP_Blogs_Template}
 *
 * @return bool Returns true when blogs are found.
 */
function bp_blogs() {
	global $blogs_template;

	return $blogs_template->blogs();
}

/**
 * Get the current blog object in the loop.
 *
 * @global object $blogs_template {@link BP_Blogs_Template}
 *
 * @return object The current blog within the loop.
 */
function bp_the_blog() {
	global $blogs_template;

	return $blogs_template->the_blog();
}

/**
 * Output the blogs pagination count.
 *
 * @global object $blogs_template {@link BP_Blogs_Template}
 */
function bp_blogs_pagination_count() {
	global $blogs_template;

	$start_num = intval( ( $blogs_template->pag_page - 1 ) * $blogs_template->pag_num ) + 1;
	$from_num  = bp_core_number_format( $start_num );
	$to_num    = bp_core_number_format( ( $start_num + ( $blogs_template->pag_num - 1 ) > $blogs_template->total_blog_count ) ? $blogs_template->total_blog_count : $start_num + ( $blogs_template->pag_num - 1 ) );
	$total     = bp_core_number_format( $blogs_template->total_blog_count );

	if ( 1 == $blogs_template->total_blog_count ) {
		$message = __( 'Viewing 1 site', 'buddypress' );
	} else {
		$message = sprintf( _n( 'Viewing %1$s - %2$s of %3$s site', 'Viewing %1$s - %2$s of %3$s sites', $blogs_template->total_blog_count, 'buddypress' ), $from_num, $to_num, $total );
	}

	echo $message;
}

/**
 * Output the blogs pagination links.
 */
function bp_blogs_pagination_links() {
	echo bp_get_blogs_pagination_links();
}
	/**
	 * Return the blogs pagination links.
	 *
	 * @global object $blogs_template {@link BP_Blogs_Template}
	 *
	 * @return string HTML pagination links.
	 */
	function bp_get_blogs_pagination_links() {
		global $blogs_template;

		/**
		 * Filters the blogs pagination links.
		 *
		 * @since 1.0.0
		 *
		 * @param string $pag_links HTML pagination links.
		 */
		return apply_filters( 'bp_get_blogs_pagination_links', $blogs_template->pag_links );
	}

/**
 * Output a blog's avatar.
 *
 * @see bp_get_blog_avatar() for description of arguments.
 *
 * @param array|string $args See {@link bp_get_blog_avatar()}.
 */
function bp_blog_avatar( $args = '' ) {
	echo bp_get_blog_avatar( $args );
}
	/**
	 * Get a blog's avatar.
	 *
	 * At the moment, blog avatars are simply the user avatars of the blog
	 * admin. Filter 'bp_get_blog_avatar_' . $blog_id to customize.
	 *
	 * @since 2.4.0 Introduced `$title` argument.
	 *
	 * @see bp_core_fetch_avatar() For a description of arguments and
	 *      return values.
	 *
	 * @param array|string $args  {
	 *     Arguments are listed here with an explanation of their defaults.
	 *     For more information about the arguments, see
	 *     {@link bp_core_fetch_avatar()}.
	 *     @type string   $alt     Default: 'Profile picture of site author [user name]'.
	 *     @type string   $class   Default: 'avatar'.
	 *     @type string   $title   Default: 'Profile picture of site author [user name]'.
	 *     @type string   $type    Default: 'full'.
	 *     @type int|bool $width   Default: false.
	 *     @type int|bool $height  Default: false.
	 *     @type bool     $id      Currently unused.
	 *     @type bool     $no_grav Default: true.
	 * }
	 * @return string User avatar string.
	 */
	function bp_get_blog_avatar( $args = '' ) {
		global $blogs_template;

		// Bail if avatars are turned off
		// @todo Should we maybe still filter this?
		if ( ! buddypress()->avatar->show_avatars ) {
			return false;
		}

		$author_displayname = bp_core_get_user_displayname( $blogs_template->blog->admin_user_id );

		// Parse the arguments
		$r = bp_parse_args( $args, array(
			'type'    => 'full',
			'width'   => false,
			'height'  => false,
			'class'   => 'avatar',
			'title'   => sprintf( __( 'Profile picture of site author %s', 'buddypress' ), esc_attr( $author_displayname ) ),
			'id'      => false,
			'alt'     => sprintf( __( 'Profile picture of site author %s', 'buddypress' ), esc_attr( $author_displayname ) ),
			'no_grav' => true,
		) );

		// Fetch the avatar
		$avatar = bp_core_fetch_avatar( array(
			'item_id'    => $blogs_template->blog->admin_user_id,
			'title'      => $r['title'],
			//'avatar_dir' => 'blog-avatars',
			//'object'     => 'blog',
			'type'       => $r['type'],
			'alt'        => $r['alt'],
			'css_id'     => $r['id'],
			'class'      => $r['class'],
			'width'      => $r['width'],
			'height'     => $r['height']
		) );

		/***
		 * In future BuddyPress versions you will be able to set the avatar for a blog.
		 * Right now you can use a filter with the ID of the blog to change it if you wish.
		 * By default it will return the avatar for the primary blog admin.
		 *
		 * This filter is deprecated as of BuddyPress 1.5 and may be removed in a future version.
		 * Use the 'bp_get_blog_avatar' filter instead.
		 */
		$avatar = apply_filters( 'bp_get_blog_avatar_' . $blogs_template->blog->blog_id, $avatar );

		/**
		 * Filters a blog's avatar.
		 *
		 * @since 1.5.0
		 *
		 * @param string $avatar  Formatted HTML <img> element, or raw avatar
		 *                        URL based on $html arg.
		 * @param int    $blog_id ID of the blog whose avatar is being displayed.
		 * @param array  $r       Array of arguments used when fetching avatar.
		 */
		return apply_filters( 'bp_get_blog_avatar', $avatar, $blogs_template->blog->blog_id, $r );
	}

function bp_blog_permalink() {
	echo bp_get_blog_permalink();
}
	function bp_get_blog_permalink() {
		global $blogs_template;

		if ( empty( $blogs_template->blog->domain ) )
			$permalink = bp_get_root_domain() . $blogs_template->blog->path;
		else {
			$protocol = 'http://';
			if ( is_ssl() )
				$protocol = 'https://';

			$permalink = $protocol . $blogs_template->blog->domain . $blogs_template->blog->path;
		}

		/**
		 * Filters the blog permalink.
		 *
		 * @since 1.0.0
		 *
		 * @param string $permalink Permalink URL for the blog.
		 */
		return apply_filters( 'bp_get_blog_permalink', $permalink );
	}

/**
 * Output the name of the current blog in the loop.
 */
function bp_blog_name() {
	echo bp_get_blog_name();
}
	/**
	 * Return the name of the current blog in the loop.
	 *
	 * @return string The name of the current blog in the loop.
	 */
	function bp_get_blog_name() {
		global $blogs_template;

		/**
		 * Filters the name of the current blog in the loop.
		 *
		 * @since 1.2.0
		 *
		 * @param string $name Name of the current blog in the loop.
		 */
		return apply_filters( 'bp_get_blog_name', $blogs_template->blog->name );
	}

/**
 * Output the ID of the current blog in the loop.
 *
 * @since 1.7.0
 */
function bp_blog_id() {
	echo bp_get_blog_id();
}
	/**
	 * Return the ID of the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @return int ID of the current blog in the loop.
	 */
	function bp_get_blog_id() {
		global $blogs_template;

		/**
		 * Filters the ID of the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param int $blog_id ID of the current blog in the loop.
		 */
		return apply_filters( 'bp_get_blog_id', $blogs_template->blog->blog_id );
	}

/**
 * Output the description of the current blog in the loop.
 */
function bp_blog_description() {

	/**
	 * Filters the description of the current blog in the loop.
	 *
	 * @since 1.2.0
	 *
	 * @param string $value Description of the current blog in the loop.
	 */
	echo apply_filters( 'bp_blog_description', bp_get_blog_description() );
}
	/**
	 * Return the description of the current blog in the loop.
	 *
	 * @return string Description of the current blog in the loop.
	 */
	function bp_get_blog_description() {
		global $blogs_template;

		/**
		 * Filters the description of the current blog in the loop.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Description of the current blog in the loop.
		 */
		return apply_filters( 'bp_get_blog_description', $blogs_template->blog->description );
	}

/**
 * Output the row class of the current blog in the loop.
 *
 * @since 1.7.0
 *
 * @param array $classes Array of custom classes
 */
function bp_blog_class( $classes = array() ) {
	echo bp_get_blog_class( $classes );
}
	/**
	 * Return the row class of the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Blogs_Template $blogs_template
	 * @param array $classes Array of custom classes
	 *
	 * @return string Row class of the site.
	 */
	function bp_get_blog_class( $classes = array() ) {
		global $blogs_template;

		// Add even/odd classes, but only if there's more than 1 group
		if ( $blogs_template->blog_count > 1 ) {
			$pos_in_loop = (int) $blogs_template->current_blog;
			$classes[]   = ( $pos_in_loop % 2 ) ? 'even' : 'odd';

		// If we've only one site in the loop, don't bother with odd and even
		} else {
			$classes[] = 'bp-single-blog';
		}

		/**
		 * Filters the row class of the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param array $classes Array of classes to be applied to row.
		 */
		$classes = apply_filters( 'bp_get_blog_class', $classes );
		$classes = array_merge( $classes, array() );
		$retval  = 'class="' . join( ' ', $classes ) . '"';

		return $retval;
	}

/**
 * Output the last active date of the current blog in the loop.
 *
 * @param array $args See {@link bp_get_blog_last_active()}.
 */
function bp_blog_last_active( $args = array() ) {
	echo bp_get_blog_last_active( $args );
}
	/**
	 * Return the last active date of the current blog in the loop.
	 *
	 * @param array $args {
	 *     Array of optional arguments.
	 *     @type bool $active_format If true, formatted "Active 5 minutes ago".
	 *                               If false, formatted "5 minutes ago".
	 *                               Default: true.
	 * }
	 * @return string Last active date.
	 */
	function bp_get_blog_last_active( $args = array() ) {
		global $blogs_template;

		// Parse the activity format
		$r = bp_parse_args( $args, array(
			'active_format' => true
		) );

		// Backwards compatibility for anyone forcing a 'true' active_format
		if ( true === $r['active_format'] ) {
			$r['active_format'] = __( 'active %s', 'buddypress' );
		}

		// Blog has been posted to at least once
		if ( isset( $blogs_template->blog->last_activity ) ) {

			// Backwards compatibility for pre 1.5 'ago' strings
			$last_activity = ! empty( $r['active_format'] )
				? bp_core_get_last_activity( $blogs_template->blog->last_activity, $r['active_format'] )
				: bp_core_time_since( $blogs_template->blog->last_activity );

		// Blog has never been posted to
		} else {
			$last_activity = __( 'Never active', 'buddypress' );
		}

		/**
		 * Filters the last active date of the current blog in the loop.
		 *
		 * @since
		 *
		 * @param string $last_activity Last active date.
		 * @param array  $r             Array of parsed args used to determine formatting.
		 */
		return apply_filters( 'bp_blog_last_active', $last_activity, $r );
	}

/**
 * Output the latest post from the current blog in the loop.
 *
 * @param array $args See {@link bp_get_blog_latest_post()}.
 */
function bp_blog_latest_post( $args = array() ) {
	echo bp_get_blog_latest_post( $args );
}
	/**
	 * Return the latest post from the current blog in the loop.
	 *
	 * @param array $args {
	 *     Array of optional arguments.
	 *     @type bool $latest_format If true, formatted "Latest post: [link to post]".
	 *                               If false, formatted "[link to post]".
	 *                               Default: true.
	 * }
	 * @return string $retval String of the form 'Latest Post: [link to post]'.
	 */
	function bp_get_blog_latest_post( $args = array() ) {
		global $blogs_template;

		$r = wp_parse_args( $args, array(
			'latest_format' => true,
		) );

		$retval = bp_get_blog_latest_post_title();

		if ( ! empty( $retval ) ) {
			if ( ! empty( $r['latest_format'] ) ) {

				/**
				 * Filters the title text of the latest post for the current blog in loop.
				 *
				 * @since 1.0.0
				 *
				 * @param string $retval Title of the latest post.
				 */
				$retval = sprintf( __( 'Latest Post: %s', 'buddypress' ), '<a href="' . $blogs_template->blog->latest_post->guid . '">' . apply_filters( 'the_title', $retval ) . '</a>' );
			} else {

				/** This filter is documented in bp-blogs/bp-blogs-template.php */
				$retval = '<a href="' . $blogs_template->blog->latest_post->guid . '">' . apply_filters( 'the_title', $retval ) . '</a>';
			}
		}

		/**
		 * Filters the HTML markup result for the latest blog post in loop.
		 *
		 * @since 1.2.0
		 *
		 * @param string $retval HTML markup for the latest post.
		 */
		return apply_filters( 'bp_get_blog_latest_post', $retval );
	}

/**
 * Output the title of the latest post on the current blog in the loop.
 *
 * @since 1.7.0
 *
 * @see bp_get_blog_latest_post_title()
 */
function bp_blog_latest_post_title() {
	echo bp_get_blog_latest_post_title();
}
	/**
	 * Return the title of the latest post on the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Blogs_Template
	 *
	 * @return string Post title.
	 */
	function bp_get_blog_latest_post_title() {
		global $blogs_template;

		$retval = '';

		if ( ! empty( $blogs_template->blog->latest_post ) && ! empty( $blogs_template->blog->latest_post->post_title ) )
			$retval = $blogs_template->blog->latest_post->post_title;

		/**
		 * Filters the title text of the latest post on the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param string $retval Title text for the latest post.
		 */
		return apply_filters( 'bp_get_blog_latest_post_title', $retval );
	}

/**
 * Output the permalink of the latest post on the current blog in the loop.
 *
 * @since 1.7.0
 *
 * @see bp_get_blog_latest_post_title()
 */
function bp_blog_latest_post_permalink() {
	echo esc_url( bp_get_blog_latest_post_permalink() );
}
	/**
	 * Return the permalink of the latest post on the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Blogs_Template
	 *
	 * @return string URL of the blog's latest post.
	 */
	function bp_get_blog_latest_post_permalink() {
		global $blogs_template;

		$retval = '';

		if ( ! empty( $blogs_template->blog->latest_post ) && ! empty( $blogs_template->blog->latest_post->ID ) )
			$retval = add_query_arg( 'p', $blogs_template->blog->latest_post->ID, bp_get_blog_permalink() );

		/**
		 * Filters the permalink of the latest post on the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param string $retval Permalink URL of the latest post.
		 */
		return apply_filters( 'bp_get_blog_latest_post_permalink', $retval );
	}

/**
 * Output the content of the latest post on the current blog in the loop.
 *
 * @since 1.7.0
 *
 * @uses bp_get_blog_latest_post_content()
 */
function bp_blog_latest_post_content() {
	echo bp_get_blog_latest_post_content();
}
	/**
	 * Return the content of the latest post on the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Blogs_Template
	 *
	 * @return string Content of the blog's latest post.
	 */
	function bp_get_blog_latest_post_content() {
		global $blogs_template;

		$retval = '';

		if ( ! empty( $blogs_template->blog->latest_post ) && ! empty( $blogs_template->blog->latest_post->post_content ) )
			$retval = $blogs_template->blog->latest_post->post_content;

		/**
		 * Filters the content of the latest post on the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param string $retval Content of the latest post on the current blog in the loop.
		 */
		return apply_filters( 'bp_get_blog_latest_post_content', $retval );
	}

/**
 * Output the featured image of the latest post on the current blog in the loop.
 *
 * @since 1.7.0
 *
 * @see bp_get_blog_latest_post_content() For description of parameters.
 *
 * @param string $size See {@link bp_get_blog_latest_post_featured_image()}.
 */
function bp_blog_latest_post_featured_image( $size = 'thumbnail' ) {
	echo bp_get_blog_latest_post_featured_image( $size );
}
	/**
	 * Return the featured image of the latest post on the current blog in the loop.
	 *
	 * @since 1.7.0
	 *
	 * @global BP_Blogs_Template
	 *
	 * @param string $size Image version to return. 'thumbnail', 'medium',
	 *                     'large', or 'post-thumbnail'. Default: 'thumbnail'.
	 *
	 * @return string URL of the image.
	 */
	function bp_get_blog_latest_post_featured_image( $size = 'thumbnail' ) {
		global $blogs_template;

		$retval = '';

		if ( ! empty( $blogs_template->blog->latest_post ) && ! empty( $blogs_template->blog->latest_post->images[$size] ) )
			$retval = $blogs_template->blog->latest_post->images[$size];

		/**
		 * Filters the featured image of the latest post on the current blog in the loop.
		 *
		 * @since 1.7.0
		 *
		 * @param string $retval The featured image of the latest post on the current blog in the loop.
		 */
		return apply_filters( 'bp_get_blog_latest_post_featured_image', $retval );
	}

/**
 * Does the latest blog post have a featured image?
 *
 * @since 1.7.0
 *
 * @param string $thumbnail Image version to return. 'thumbnail', 'medium', 'large',
 *                          or 'post-thumbnail'. Default: 'thumbnail'.
 *
 * @return bool True if the latest blog post from the current blog has a
 *              featured image of the given size.
 */
function bp_blog_latest_post_has_featured_image( $thumbnail = 'thumbnail' ) {
	$image  = bp_get_blog_latest_post_featured_image( $thumbnail );

	/**
	 * Filters whether or not the latest blog post has a featured image.
	 *
	 * @since 1.7.0
	 *
	 * @param bool   $value     Whether or not the latest blog post has a featured image.
	 * @param string $thumbnail Image version to return.
	 * @param string $image     Returned value from bp_get_blog_latest_post_featured_image.
	 */
	return apply_filters( 'bp_blog_latest_post_has_featured_image', ! empty( $image ), $thumbnail, $image );
}

/**
 * Output hidden fields to help with form submissions in Sites directory.
 *
 * This function detects whether 's', 'letter', or 'blogs_search' requests are
 * currently being made (as in a URL parameter), and creates corresponding
 * hidden fields.
 */
function bp_blog_hidden_fields() {
	if ( isset( $_REQUEST['s'] ) )
		echo '<input type="hidden" id="search_terms" value="' . esc_attr( $_REQUEST['s'] ). '" name="search_terms" />';

	if ( isset( $_REQUEST['letter'] ) )
		echo '<input type="hidden" id="selected_letter" value="' . esc_attr( $_REQUEST['letter'] ) . '" name="selected_letter" />';

	if ( isset( $_REQUEST['blogs_search'] ) )
		echo '<input type="hidden" id="search_terms" value="' . esc_attr( $_REQUEST['blogs_search'] ) . '" name="search_terms" />';
}

/**
 * Output the total number of blogs on the site.
 */
function bp_total_blog_count() {
	echo bp_get_total_blog_count();
}
	/**
	 * Return the total number of blogs on the site.
	 *
	 * @return int Total number of blogs.
	 */
	function bp_get_total_blog_count() {

		/**
		 * Filters the total number of blogs on the site.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Total number of blogs on the site.
		 */
		return apply_filters( 'bp_get_total_blog_count', bp_blogs_total_blogs() );
	}
	add_filter( 'bp_get_total_blog_count', 'bp_core_number_format' );

/**
 * Output the total number of blogs for a given user.
 *
 * @param int $user_id ID of the user.
 */
function bp_total_blog_count_for_user( $user_id = 0 ) {
	echo bp_get_total_blog_count_for_user( $user_id );
}
	/**
	 * Return the total number of blogs for a given user.
	 *
	 * @param int $user_id ID of the user.
	 *
	 * @return int Total number of blogs for the user.
	 */
	function bp_get_total_blog_count_for_user( $user_id = 0 ) {

		/**
		 * Filters the total number of blogs for a given user.
		 *
		 * @since 1.2.0
		 *
		 * @param int $value Total number of blogs for a given user.
		 */
		return apply_filters( 'bp_get_total_blog_count_for_user', bp_blogs_total_blogs_for_user( $user_id ) );
	}
	add_filter( 'bp_get_total_blog_count_for_user', 'bp_core_number_format' );


/** Blog Registration ********************************************************/

/**
 * Checks whether blog creation is enabled.
 *
 * Returns true when blog creation is enabled for logged-in users only, or
 * when it's enabled for new registrations.
 *
 * @return bool True if blog registration is enabled.
 */
function bp_blog_signup_enabled() {
	$bp = buddypress();

	$active_signup = isset( $bp->site_options['registration'] )
		? $bp->site_options['registration']
		: 'all';

	/**
	 * Filters whether or not blog creation is enabled.
	 *
	 * @since 1.0.0
	 *
	 * @param string $active_signup Value of the registration site option creation status.
	 */
	$active_signup = apply_filters( 'wpmu_active_signup', $active_signup ); // return "all", "none", "blog" or "user"

	if ( 'none' == $active_signup || 'user' == $active_signup )
		return false;

	return true;
}

/**
 * Output the wrapper markup for the blog signup form.
 *
 * @param string          $blogname   Optional. The default blog name (path or domain).
 * @param string          $blog_title Optional. The default blog title.
 * @param string|WP_Error $errors     Optional. The WP_Error object returned by a previous
 *                                    submission attempt.
 */
function bp_show_blog_signup_form($blogname = '', $blog_title = '', $errors = '') {
	global $current_user;

	if ( isset($_POST['submit']) ) {
		bp_blogs_validate_blog_signup();
	} else {
		if ( ! is_wp_error($errors) ) {
			$errors = new WP_Error();
		}

		/**
		 * Filters the default values for Blog name, title, and any current errors.
		 *
		 * @since 1.0.0
		 *
		 * @param array $value {
		 *      string   $blogname   Default blog name provided.
		 *      string   $blog_title Default blog title provided.
		 *      WP_Error $errors     WP_Error object.
		 * }
		 */
		$filtered_results = apply_filters('signup_another_blog_init', array('blogname' => $blogname, 'blog_title' => $blog_title, 'errors' => $errors ));
		$blogname = $filtered_results['blogname'];
		$blog_title = $filtered_results['blog_title'];
		$errors = $filtered_results['errors'];

		if ( $errors->get_error_code() ) {
			echo "<p>" . __('There was a problem; please correct the form below and try again.', 'buddypress') . "</p>";
		}
		?>
		<p><?php printf(__("By filling out the form below, you can <strong>add a site to your account</strong>. There is no limit to the number of sites that you can have, so create to your heart's content, but blog responsibly!", 'buddypress'), $current_user->display_name) ?></p>

		<p><?php _e("If you&#8217;re not going to use a great domain, leave it for a new user. Now have at it!", 'buddypress') ?></p>

		<form class="standard-form" id="setupform" method="post" action="">

			<input type="hidden" name="stage" value="gimmeanotherblog" />
			<?php

			/**
			 * Fires after the default hidden fields in blog signup form markup.
			 *
			 * @since 1.0.0
			 */
			do_action( 'signup_hidden_fields' ); ?>

			<?php bp_blogs_signup_blog($blogname, $blog_title, $errors); ?>
			<p>
				<input id="submit" type="submit" name="submit" class="submit" value="<?php esc_attr_e('Create Site', 'buddypress') ?>" />
			</p>

			<?php wp_nonce_field( 'bp_blog_signup_form' ) ?>
		</form>
		<?php
	}
}

/**
 * Output the input fields for the blog creation form.
 *
 * @param string          $blogname   Optional. The default blog name (path or domain).
 * @param string          $blog_title Optional. The default blog title.
 * @param string|WP_Error $errors     Optional. The WP_Error object returned by a previous
 *                                    submission attempt.
 */
function bp_blogs_signup_blog( $blogname = '', $blog_title = '', $errors = '' ) {
	global $current_site;

	// Blog name
	if( !is_subdomain_install() )
		echo '<label for="blogname">' . __('Site Name:', 'buddypress') . '</label>';
	else
		echo '<label for="blogname">' . __('Site Domain:', 'buddypress') . '</label>';

	if ( $errmsg = $errors->get_error_message('blogname') ) { ?>

		<p class="error"><?php echo $errmsg ?></p>

	<?php }

	if ( !is_subdomain_install() )
		echo '<span class="prefix_address">' . $current_site->domain . $current_site->path . '</span> <input name="blogname" type="text" id="blogname" value="'.$blogname.'" maxlength="63" /><br />';
	else
		echo '<input name="blogname" type="text" id="blogname" value="'.$blogname.'" maxlength="63" ' . bp_get_form_field_attributes( 'blogname' ) . '/> <span class="suffix_address">.' . bp_blogs_get_subdomain_base() . '</span><br />';

	if ( !is_user_logged_in() ) {
		print '(<strong>' . __( 'Your address will be ' , 'buddypress');

		if ( !is_subdomain_install() ) {
			print $current_site->domain . $current_site->path . __( 'blogname' , 'buddypress');
		} else {
			print __( 'domain.' , 'buddypress') . $current_site->domain . $current_site->path;
		}

		echo '.</strong> ' . __( 'Must be at least 4 characters, letters and numbers only. It cannot be changed so choose carefully!)' , 'buddypress') . '</p>';
	}

	// Blog Title
	?>

	<label for="blog_title"><?php _e('Site Title:', 'buddypress') ?></label>

	<?php if ( $errmsg = $errors->get_error_message('blog_title') ) { ?>

		<p class="error"><?php echo $errmsg ?></p>

	<?php }
	echo '<input name="blog_title" type="text" id="blog_title" value="'.esc_html($blog_title, 1).'" /></p>';
	?>

	<p>
		<label for="blog_public_on"><?php _e('Privacy:', 'buddypress') ?></label>
		<?php _e( 'I would like my site to appear in search engines, and in public listings around this network.', 'buddypress' ); ?>

		<label class="checkbox" for="blog_public_on">
			<input type="radio" id="blog_public_on" name="blog_public" value="1" <?php if( !isset( $_POST['blog_public'] ) || '1' == $_POST['blog_public'] ) { ?>checked="checked"<?php } ?> />
			<strong><?php _e( 'Yes' , 'buddypress'); ?></strong>
		</label>
		<label class="checkbox" for="blog_public_off">
			<input type="radio" id="blog_public_off" name="blog_public" value="0" <?php if( isset( $_POST['blog_public'] ) && '0' == $_POST['blog_public'] ) { ?>checked="checked"<?php } ?> />
			<strong><?php _e( 'No' , 'buddypress'); ?></strong>
		</label>
	</p>

	<?php

	/**
	 * Fires at the end of all of the default input fields for blog creation form.
	 *
	 * @since 1.0.0
	 *
	 * @param WP_Error $errors WP_Error object if any present.
	 */
	do_action('signup_blogform', $errors);
}

/**
 * Process a blog registration submission.
 *
 * Passes submitted values to {@link wpmu_create_blog()}.
 *
 * @return bool True on success, false on failure.
 */
function bp_blogs_validate_blog_signup() {
	global $wpdb, $current_user, $blogname, $blog_title, $errors, $domain, $path, $current_site;

	if ( !check_admin_referer( 'bp_blog_signup_form' ) )
		return false;

	$current_user = wp_get_current_user();

	if( !is_user_logged_in() )
		die();

	$result = bp_blogs_validate_blog_form();
	extract($result);

	if ( $errors->get_error_code() ) {
		unset($_POST['submit']);
		bp_show_blog_signup_form( $blogname, $blog_title, $errors );
		return false;
	}

	$public = (int) $_POST['blog_public'];

	$meta = apply_filters( 'signup_create_blog_meta', array( 'lang_id' => 1, 'public' => $public ) ); // depreciated

	/**
	 * Filters the default values for Blog meta.
	 *
	 * @since 1.0.0
	 *
	 * @param array $meta {
	 *      string $value  Default blog language ID.
	 *      string $public Default public status.
	 * }
	 */
	$meta = apply_filters( 'add_signup_meta', $meta );

	// If this is a subdomain install, set up the site inside the root domain.
	if ( is_subdomain_install() )
		$domain = $blogname . '.' . preg_replace( '|^www\.|', '', $current_site->domain );

	wpmu_create_blog( $domain, $path, $blog_title, $current_user->ID, $meta, $wpdb->siteid );
	bp_blogs_confirm_blog_signup($domain, $path, $blog_title, $current_user->user_login, $current_user->user_email, $meta);
	return true;
}

/**
 * Validate a blog creation submission.
 *
 * Essentially, a wrapper for {@link wpmu_validate_blog_signup()}.
 *
 * @return array Contains the new site data and error messages.
 */
function bp_blogs_validate_blog_form() {
	$user = '';
	if ( is_user_logged_in() )
		$user = wp_get_current_user();

	return wpmu_validate_blog_signup($_POST['blogname'], $_POST['blog_title'], $user);
}

/**
 * Display a message after successful blog registration.
 *
 * @param string       $domain     The new blog's domain.
 * @param string       $path       The new blog's path.
 * @param string       $blog_title The new blog's title.
 * @param string       $user_name  The user name of the user who created the blog. Unused.
 * @param string       $user_email The email of the user who created the blog. Unused.
 * @param string|array $meta       Meta values associated with the new blog. Unused.
 */
function bp_blogs_confirm_blog_signup( $domain, $path, $blog_title, $user_name, $user_email = '', $meta = '' ) {
	$protocol = is_ssl() ? 'https://' : 'http://';
	$blog_url = $protocol . $domain . $path; ?>

	<p><?php _e( 'Congratulations! You have successfully registered a new site.', 'buddypress' ) ?></p>
	<p>
		<?php printf(__( '<a href="%1$s">%2$s</a> is your new site.  <a href="%3$s">Login</a> as "%4$s" using your existing password.', 'buddypress' ), $blog_url, $blog_url, $blog_url . "wp-login.php", $user_name ); ?>
	</p>

<?php

	/**
	 * Fires after the default successful blog registration message markup.
	 *
	 * @since 1.0.0
	 */
	do_action('signup_finished');
}

/**
 * Output a "Create a Site" link for users viewing their own profiles.
 *
 * This function is not used by BuddyPress as of 1.2, but is kept here for older
 * themes that may still be using it.
 */
function bp_create_blog_link() {

	// Don't show this link when not on your own profile
	if ( ! bp_is_my_profile() ) {
		return;
	}

	/**
	 * Filters "Create a Site" links for users viewing their own profiles.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value HTML link for creating a site.
	 */
	echo apply_filters( 'bp_create_blog_link', '<a href="' . trailingslashit( bp_get_blogs_directory_permalink() . 'create' ) . '">' . __( 'Create a Site', 'buddypress' ) . '</a>' );
}

/**
 * Output navigation tabs for a user Blogs page.
 *
 * Currently unused by BuddyPress.
 */
function bp_blogs_blog_tabs() {

	// Don't show these tabs on a user's own profile
	if ( bp_is_my_profile() ) {
		return false;
	} ?>

	<ul class="content-header-nav">
		<li<?php if ( bp_is_current_action( 'my-blogs'        ) || !bp_current_action() ) : ?> class="current"<?php endif; ?>><a href="<?php echo trailingslashit( bp_displayed_user_domain() . bp_get_blogs_slug() . '/my-blogs'        ); ?>"><?php printf( __( "%s's Sites", 'buddypress' ),           bp_get_displayed_user_fullname() ); ?></a></li>
		<li<?php if ( bp_is_current_action( 'recent-posts'    )                         ) : ?> class="current"<?php endif; ?>><a href="<?php echo trailingslashit( bp_displayed_user_domain() . bp_get_blogs_slug() . '/recent-posts'    ); ?>"><?php printf( __( "%s's Recent Posts", 'buddypress' ),    bp_get_displayed_user_fullname() ); ?></a></li>
		<li<?php if ( bp_is_current_action( 'recent-comments' )                         ) : ?> class="current"<?php endif; ?>><a href="<?php echo trailingslashit( bp_displayed_user_domain() . bp_get_blogs_slug() . '/recent-comments' ); ?>"><?php printf( __( "%s's Recent Comments", 'buddypress' ), bp_get_displayed_user_fullname() ); ?></a></li>
	</ul>

<?php

	/**
	 * Fires after the markup for the navigation tabs for a user Blogs page.
	 *
	 * @since 1.0.0
	 */
	do_action( 'bp_blogs_blog_tabs' );
}

/**
 * Output the blog directory search form.
 */
function bp_directory_blogs_search_form() {

	$query_arg = bp_core_get_component_search_query_arg( 'blogs' );

	if ( ! empty( $_REQUEST[ $query_arg ] ) ) {
		$search_value = stripslashes( $_REQUEST[ $query_arg ] );
	} else {
		$search_value = bp_get_search_default_text( 'blogs' );
	}

	$search_form_html = '<form action="" method="get" id="search-blogs-form">
		<label for="blogs_search"><input type="text" name="' . esc_attr( $query_arg ) . '" id="blogs_search" placeholder="'. esc_attr( $search_value ) .'" /></label>
		<input type="submit" id="blogs_search_submit" name="blogs_search_submit" value="' . __( 'Search', 'buddypress' ) . '" />
	</form>';

	/**
	 * Filters the output for the blog directory search form.
	 *
	 * @since 1.9.0
	 *
	 * @param string $search_form_html HTML markup for blog directory search form.
	 */
	echo apply_filters( 'bp_directory_blogs_search_form', $search_form_html );
}

/**
 * Output the Create a Site button.
 *
 * @since 2.0.0
 */
function bp_blog_create_button() {
	echo bp_get_blog_create_button();
}
	/**
	 * Get the Create a Site button.
	 *
	 * @since 2.0.0
	 *
	 * @return string
	 */
	function bp_get_blog_create_button() {
		if ( ! is_user_logged_in() ) {
			return false;
		}

		if ( ! bp_blog_signup_enabled() ) {
			return false;
		}

		$button_args = array(
			'id'         => 'create_blog',
			'component'  => 'blogs',
			'link_text'  => __( 'Create a Site', 'buddypress' ),
			'link_title' => __( 'Create a Site', 'buddypress' ),
			'link_class' => 'blog-create no-ajax',
			'link_href'  => trailingslashit( bp_get_blogs_directory_permalink() . 'create' ),
			'wrapper'    => false,
			'block_self' => false,
		);

		/**
		 * Filters the Create a Site button.
		 *
		 * @since 2.0.0
		 *
		 * @param array $button_args Array of arguments to be used for the Create a Site button.
		 */
		return bp_get_button( apply_filters( 'bp_get_blog_create_button', $button_args ) );
	}

/**
 * Output the Create a Site nav item.
 *
 * @since 2.2.0
 */
function bp_blog_create_nav_item() {
	echo bp_get_blog_create_nav_item();
}

	/**
	 * Get the Create a Site nav item.
	 *
	 * @since 2.2.0
	 *
	 * @return string
	 */
	function bp_get_blog_create_nav_item() {
		// Get the create a site button
		$create_blog_button = bp_get_blog_create_button();

		// Make sure the button is available
		if ( empty( $create_blog_button ) ) {
			return;
		}

		$output = '<li id="blog-create-nav">' . $create_blog_button . '</li>';

		return apply_filters( 'bp_get_blog_create_nav_item', $output );
	}

/**
 * Checks if a specific theme is still filtering the Blogs directory title
 * if so, transform the title button into a Blogs directory nav item.
 *
 * @since 2.2.0
 *
 * @uses bp_blog_create_nav_item() to output the Create a Site nav item.
 *
 * @return string HTML Output
 */
function bp_blog_backcompat_create_nav_item() {
	// Bail if Blogs nav item is already used by bp-legacy
	if ( has_action( 'bp_blogs_directory_blog_types', 'bp_legacy_theme_blog_create_nav', 999 ) ) {
		return;
	}

	// Bail if the theme is not filtering the Blogs directory title
	if ( ! has_filter( 'bp_blogs_directory_header' ) ) {
		return;
	}

	bp_blog_create_nav_item();
}
add_action( 'bp_blogs_directory_blog_types', 'bp_blog_backcompat_create_nav_item', 1000 );

/**
 * Output button for visiting a blog in a loop.
 *
 * @see bp_get_blogs_visit_blog_button() for description of arguments.
 *
 * @param array|string $args See {@link bp_get_blogs_visit_blog_button()}.
 */
function bp_blogs_visit_blog_button( $args = '' ) {
	echo bp_get_blogs_visit_blog_button( $args );
}
	/**
	 * Return button for visiting a blog in a loop.
	 *
	 * @see BP_Button for a complete description of arguments and return
	 *      value.
	 *
	 * @param array|string $args {
	 *     Arguments are listed below, with their default values. For a
	 *     complete description of arguments, see {@link BP_Button}.
	 *     @type string $id                Default: 'visit_blog'.
	 *     @type string $component         Default: 'blogs'.
	 *     @type bool   $must_be_logged_in Default: false.
	 *     @type bool   $block_self        Default: false.
	 *     @type string $wrapper_class     Default: 'blog-button visit'.
	 *     @type string $link_href         Permalink of the current blog in the loop.
	 *     @type string $link_class        Default: 'blog-button visit'.
	 *     @type string $link_text         Default: 'Visit Site'.
	 *     @type string $link_title        Default: 'Visit Site'.
	 * }
	 * @return string The HTML for the Visit button.
	 */
	function bp_get_blogs_visit_blog_button( $args = '' ) {
		$defaults = array(
			'id'                => 'visit_blog',
			'component'         => 'blogs',
			'must_be_logged_in' => false,
			'block_self'        => false,
			'wrapper_class'     => 'blog-button visit',
			'link_href'         => bp_get_blog_permalink(),
			'link_class'        => 'blog-button visit',
			'link_text'         => __( 'Visit Site', 'buddypress' ),
			'link_title'        => __( 'Visit Site', 'buddypress' ),
		);

		$button = wp_parse_args( $args, $defaults );

		/**
		 * Filters the button for visiting a blog in a loop.
		 *
		 * @since 1.2.10
		 *
		 * @param array $button Array of arguments to be used for the button to visit a blog.
		 */
		return bp_get_button( apply_filters( 'bp_get_blogs_visit_blog_button', $button ) );
	}

/** Stats **********************************************************************/

/**
 * Display the number of blogs in user's profile.
 *
 * @since 2.0.0
 *
 * @uses bp_blogs_admin_get_profile_stats() to get the stats.
 *
 * @param array|string $args before|after|user_id
 */
function bp_blogs_profile_stats( $args = '' ) {
	echo bp_blogs_get_profile_stats( $args );
}
add_action( 'bp_members_admin_user_stats', 'bp_blogs_profile_stats', 9, 1 );

/**
 * Return the number of blogs in user's profile.
 *
 * @since 2.0.0
 *
 * @param array|string $args before|after|user_id
 *
 * @return string HTML for stats output.
 */
function bp_blogs_get_profile_stats( $args = '' ) {

	// Parse the args
	$r = bp_parse_args( $args, array(
		'before'  => '<li class="bp-blogs-profile-stats">',
		'after'   => '</li>',
		'user_id' => bp_displayed_user_id(),
		'blogs'   => 0,
		'output'  => ''
	), 'blogs_get_profile_stats' );

	// Allow completely overloaded output
	if ( is_multisite() && empty( $r['output'] ) ) {

		// Only proceed if a user ID was passed
		if ( ! empty( $r['user_id'] ) ) {

			// Get the user's blogs
			if ( empty( $r['blogs'] ) ) {
				$r['blogs'] = absint( bp_blogs_total_blogs_for_user( $r['user_id'] ) );
			}

			// If blogs exist, show some formatted output
			$r['output'] = $r['before'] . sprintf( _n( '%s site', '%s sites', $r['blogs'], 'buddypress' ), '<strong>' . $r['blogs'] . '</strong>' ) . $r['after'];
		}
	}

	/**
	 * Filters the number of blogs in user's profile.
	 *
	 * @since 2.0.0
	 *
	 * @param string $value Output determined for the profile stats.
	 * @param array  $r     Array of arguments used for default output if none provided.
	 */
	return apply_filters( 'bp_blogs_get_profile_stats', $r['output'], $r );
}
