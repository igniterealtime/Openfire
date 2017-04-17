<?php
/**
 * BuddyPress Blogs Classes.
 *
 * @package BuddyPress
 * @subpackage BlogsClasses
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * The main BuddyPress blog class.
 *
 * A BP_Blogs_Object represents a link between a specific WordPress blog on a
 * network and a specific user on that blog.
 *
 * @since 1.0.0
 */
class BP_Blogs_Blog {
	public $id;
	public $user_id;
	public $blog_id;

	/**
	 * Constructor method.
	 *
	 * @param int $id Optional. The ID of the blog.
	 */
	public function __construct( $id = null ) {
		if ( !empty( $id ) ) {
			$this->id = $id;
			$this->populate();
		}
	}

	/**
	 * Populate the object with data about the specific activity item.
	 */
	public function populate() {
		global $wpdb;

		$bp = buddypress();

		$blog = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->blogs->table_name} WHERE id = %d", $this->id ) );

		$this->user_id = $blog->user_id;
		$this->blog_id = $blog->blog_id;
	}

	/**
	 * Save the BP blog data to the database.
	 *
	 * @return bool True on success, false on failure.
	 */
	public function save() {
		global $wpdb;

		$this->user_id = apply_filters( 'bp_blogs_blog_user_id_before_save', $this->user_id, $this->id );
		$this->blog_id = apply_filters( 'bp_blogs_blog_id_before_save', $this->blog_id, $this->id );

		/**
		 * Fires before the current blog item gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Blogs_Blog $this Current instance of the blog item being saved. Passed by reference.
		 */
		do_action_ref_array( 'bp_blogs_blog_before_save', array( &$this ) );

		// Don't try and save if there is no user ID or blog ID set.
		if ( !$this->user_id || !$this->blog_id )
			return false;

		// Don't save if this blog has already been recorded for the user.
		if ( !$this->id && $this->exists() )
			return false;

		$bp = buddypress();

		if ( $this->id ) {
			// Update
			$sql = $wpdb->prepare( "UPDATE {$bp->blogs->table_name} SET user_id = %d, blog_id = %d WHERE id = %d", $this->user_id, $this->blog_id, $this->id );
		} else {
			// Save
			$sql = $wpdb->prepare( "INSERT INTO {$bp->blogs->table_name} ( user_id, blog_id ) VALUES ( %d, %d )", $this->user_id, $this->blog_id );
		}

		if ( !$wpdb->query($sql) )
			return false;

		/**
		 * Fires after the current blog item gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Blogs_Blog $this Current instance of the blog item being saved. Passed by reference.
		 */
		do_action_ref_array( 'bp_blogs_blog_after_save', array( &$this ) );

		if ( $this->id )
			return $this->id;
		else
			return $wpdb->insert_id;
	}

	/**
	 * Check whether an association between this user and this blog exists.
	 *
	 * @return int $value The number of associations between the user and blog
	 *                    saved in the blog component tables.
	 */
	public function exists() {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->blogs->table_name} WHERE user_id = %d AND blog_id = %d", $this->user_id, $this->blog_id ) );
	}

	/** Static Methods ***************************************************/

	/**
	 * Retrieve a set of blog-user associations.
	 *
	 * @param string      $type              The order in which results should be returned.
	 *                                       'active', 'alphabetical', 'newest', or 'random'.
	 * @param int|bool    $limit             Optional. The maximum records to return.
	 *                                       Default: false.
	 * @param int|bool    $page              Optional. The page of records to return.
	 *                                       Default: false (unlimited results).
	 * @param int         $user_id           Optional. ID of the user whose blogs are being
	 *                                       retrieved. Default: 0.
	 * @param string|bool $search_terms      Optional. Search by text stored in
	 *                                       blogmeta (such as the blog name). Default: false.
	 * @param bool        $update_meta_cache Whether to pre-fetch metadata for
	 *                                       blogs. Default: true.
	 * @param array|bool  $include_blog_ids  Array of blog IDs to include.
	 *
	 * @return array Multidimensional results array, structured as follows:
	 *               'blogs' - Array of located blog objects
	 *               'total' - A count of the total blogs matching the filter params
	 */
	public static function get( $type, $limit = false, $page = false, $user_id = 0, $search_terms = false, $update_meta_cache = true, $include_blog_ids = false ) {
		global $wpdb;

		$bp = buddypress();

		if ( !is_user_logged_in() || ( !bp_current_user_can( 'bp_moderate' ) && ( $user_id != bp_loggedin_user_id() ) ) )
			$hidden_sql = "AND wb.public = 1";
		else
			$hidden_sql = '';

		$pag_sql = ( $limit && $page ) ? $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) ) : '';

		$user_sql = !empty( $user_id ) ? $wpdb->prepare( " AND b.user_id = %d", $user_id ) : '';

		switch ( $type ) {
			case 'active': default:
				$order_sql = "ORDER BY bm.meta_value DESC";
				break;
			case 'alphabetical':
				$order_sql = "ORDER BY bm_name.meta_value ASC";
				break;
			case 'newest':
				$order_sql = "ORDER BY wb.registered DESC";
				break;
			case 'random':
				$order_sql = "ORDER BY RAND()";
				break;
		}

		$include_sql = '';
		$include_blog_ids = array_filter( wp_parse_id_list( $include_blog_ids ) );
		if ( ! empty( $include_blog_ids ) ) {
			$blog_ids_sql = implode( ',', $include_blog_ids );
			$include_sql  = " AND b.blog_id IN ({$blog_ids_sql})";
		}

		if ( ! empty( $search_terms ) ) {
			$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
			$search_terms_sql  = $wpdb->prepare( 'AND (bm_name.meta_value LIKE %s OR bm_description.meta_value LIKE %s)', $search_terms_like, $search_terms_like );
		} else {
			$search_terms_sql = '';
		}

		$paged_blogs = $wpdb->get_results( "
			SELECT b.blog_id, b.user_id as admin_user_id, u.user_email as admin_user_email, wb.domain, wb.path, bm.meta_value as last_activity, bm_name.meta_value as name
			FROM
			  {$bp->blogs->table_name} b
			  LEFT JOIN {$bp->blogs->table_name_blogmeta} bm ON (b.blog_id = bm.blog_id)
			  LEFT JOIN {$bp->blogs->table_name_blogmeta} bm_name ON (b.blog_id = bm_name.blog_id)
			  LEFT JOIN {$bp->blogs->table_name_blogmeta} bm_description ON (b.blog_id = bm_description.blog_id)
			  LEFT JOIN {$wpdb->base_prefix}blogs wb ON (b.blog_id = wb.blog_id)
			  LEFT JOIN {$wpdb->users} u ON (b.user_id = u.ID)
			WHERE
			  wb.archived = '0' AND wb.spam = 0 AND wb.mature = 0 AND wb.deleted = 0 {$hidden_sql}
			  AND bm.meta_key = 'last_activity' AND bm_name.meta_key = 'name' AND bm_description.meta_key = 'description'
			  {$search_terms_sql} {$user_sql} {$include_sql}
			GROUP BY b.blog_id {$order_sql} {$pag_sql}
		" );

		$total_blogs = $wpdb->get_var( "
			SELECT COUNT(DISTINCT b.blog_id)
			FROM
			  {$bp->blogs->table_name} b
			  LEFT JOIN {$wpdb->base_prefix}blogs wb ON (b.blog_id = wb.blog_id)
			  LEFT JOIN {$bp->blogs->table_name_blogmeta} bm_name ON (b.blog_id = bm_name.blog_id)
			  LEFT JOIN {$bp->blogs->table_name_blogmeta} bm_description ON (b.blog_id = bm_description.blog_id)
			WHERE
			  wb.archived = '0' AND wb.spam = 0 AND wb.mature = 0 AND wb.deleted = 0 {$hidden_sql}
			  AND
			  bm_name.meta_key = 'name' AND bm_description.meta_key = 'description'
			  {$search_terms_sql} {$user_sql} {$include_sql}
		" );

		$blog_ids = array();
		foreach ( (array) $paged_blogs as $blog ) {
			$blog_ids[] = (int) $blog->blog_id;
		}

		$paged_blogs = BP_Blogs_Blog::get_blog_extras( $paged_blogs, $blog_ids, $type );

		if ( $update_meta_cache ) {
			bp_blogs_update_meta_cache( $blog_ids );
		}

		return array( 'blogs' => $paged_blogs, 'total' => $total_blogs );
	}

	/**
	 * Delete the record of a given blog for all users.
	 *
	 * @param int $blog_id The blog being removed from all users.
	 * @return int|bool Number of rows deleted on success, false on failure.
	 */
	public static function delete_blog_for_all( $blog_id ) {
		global $wpdb;

		bp_blogs_delete_blogmeta( $blog_id );

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->blogs->table_name} WHERE blog_id = %d", $blog_id ) );
	}

	/**
	 * Delete the record of a given blog for a specific user.
	 *
	 * @param int $blog_id The blog being removed.
	 * @param int $user_id Optional. The ID of the user from whom the blog is
	 *                     being removed. If absent, defaults to the logged-in user ID.
	 *
	 * @return int|bool Number of rows deleted on success, false on failure.
	 */
	public static function delete_blog_for_user( $blog_id, $user_id = null ) {
		global $wpdb;

		if ( !$user_id )
			$user_id = bp_loggedin_user_id();

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->blogs->table_name} WHERE user_id = %d AND blog_id = %d", $user_id, $blog_id ) );
	}

	/**
	 * Delete all of a user's blog associations in the BP tables.
	 *
	 * @param int $user_id Optional. The ID of the user whose blog associations
	 *                     are being deleted. If absent, defaults to logged-in user ID.
	 *
	 * @return int|bool Number of rows deleted on success, false on failure.
	 */
	public static function delete_blogs_for_user( $user_id = null ) {
		global $wpdb;

		if ( !$user_id )
			$user_id = bp_loggedin_user_id();

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->blogs->table_name} WHERE user_id = %d", $user_id ) );
	}

	/**
	 * Get all of a user's blogs, as tracked by BuddyPress.
	 *
	 * Note that this is different from the WordPress function
	 * {@link get_blogs_of_user()}; the current method returns only those
	 * blogs that have been recorded by BuddyPress, while the WP function
	 * does a true query of a user's blog capabilities.
	 *
	 * @param int  $user_id     Optional. ID of the user whose blogs are being
	 *                          queried. Defaults to logged-in user.
	 * @param bool $show_hidden Optional. Whether to include blogs that are not marked
	 *                          public. Defaults to true when viewing one's own profile.
	 *
	 * @return array Multidimensional results array, structured as follows:
	 *               'blogs' - Array of located blog objects.
	 *               'total' - A count of the total blogs for the user.
	 */
	public static function get_blogs_for_user( $user_id = 0, $show_hidden = false ) {
		global $wpdb;

		$bp = buddypress();

		if ( !$user_id )
			$user_id = bp_displayed_user_id();

		// Show logged in users their hidden blogs.
		if ( !bp_is_my_profile() && !$show_hidden )
			$blogs = $wpdb->get_results( $wpdb->prepare( "SELECT DISTINCT b.blog_id, b.id, bm1.meta_value as name, wb.domain, wb.path FROM {$bp->blogs->table_name} b, {$wpdb->base_prefix}blogs wb, {$bp->blogs->table_name_blogmeta} bm1 WHERE b.blog_id = wb.blog_id AND b.blog_id = bm1.blog_id AND bm1.meta_key = 'name' AND wb.public = 1 AND wb.deleted = 0 AND wb.spam = 0 AND wb.mature = 0 AND wb.archived = '0' AND b.user_id = %d ORDER BY b.blog_id", $user_id ) );
		else
			$blogs = $wpdb->get_results( $wpdb->prepare( "SELECT DISTINCT b.blog_id, b.id, bm1.meta_value as name, wb.domain, wb.path FROM {$bp->blogs->table_name} b, {$wpdb->base_prefix}blogs wb, {$bp->blogs->table_name_blogmeta} bm1 WHERE b.blog_id = wb.blog_id AND b.blog_id = bm1.blog_id AND bm1.meta_key = 'name' AND wb.deleted = 0 AND wb.spam = 0 AND wb.mature = 0 AND wb.archived = '0' AND b.user_id = %d ORDER BY b.blog_id", $user_id ) );

		$total_blog_count = BP_Blogs_Blog::total_blog_count_for_user( $user_id );

		$user_blogs = array();
		foreach ( (array) $blogs as $blog ) {
			$user_blogs[$blog->blog_id] = new stdClass;
			$user_blogs[$blog->blog_id]->id = $blog->id;
			$user_blogs[$blog->blog_id]->blog_id = $blog->blog_id;
			$user_blogs[$blog->blog_id]->siteurl = ( is_ssl() ) ? 'https://' . $blog->domain . $blog->path : 'http://' . $blog->domain . $blog->path;
			$user_blogs[$blog->blog_id]->name = $blog->name;
		}

		return array( 'blogs' => $user_blogs, 'count' => $total_blog_count );
	}

	/**
	 * Get IDs of all of a user's blogs, as tracked by BuddyPress.
	 *
	 * This method always includes hidden blogs.
	 *
	 * @param int $user_id Optional. ID of the user whose blogs are being
	 *                     queried. Defaults to logged-in user.
	 *
	 * @return int The number of blogs associated with the user.
	 */
	public static function get_blog_ids_for_user( $user_id = 0 ) {
		global $wpdb;

		$bp = buddypress();

		if ( !$user_id )
			$user_id = bp_displayed_user_id();

		return $wpdb->get_col( $wpdb->prepare( "SELECT blog_id FROM {$bp->blogs->table_name} WHERE user_id = %d", $user_id ) );
	}

	/**
	 * Check whether a blog has been recorded by BuddyPress.
	 *
	 * @param int $blog_id ID of the blog being queried.
	 *
	 * @return int|null The ID of the first located entry in the BP table
	 *                  on success, otherwise null.
	 */
	public static function is_recorded( $blog_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$bp->blogs->table_name} WHERE blog_id = %d", $blog_id ) );
	}

	/**
	 * Return a count of associated blogs for a given user.
	 *
	 * Includes hidden blogs when the logged-in user is the same as the
	 * $user_id parameter, or when the logged-in user has the bp_moderate
	 * cap.
	 *
	 * @param int $user_id Optional. ID of the user whose blogs are being
	 *                     queried. Defaults to logged-in user.
	 *
	 * @return int Blog count for the user.
	 */
	public static function total_blog_count_for_user( $user_id = null ) {
		global $wpdb;

		$bp = buddypress();

		if ( !$user_id )
			$user_id = bp_displayed_user_id();

		// If the user is logged in return the blog count including their hidden blogs.
		if ( ( is_user_logged_in() && $user_id == bp_loggedin_user_id() ) || bp_current_user_can( 'bp_moderate' ) ) {
			return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(DISTINCT b.blog_id) FROM {$bp->blogs->table_name} b LEFT JOIN {$wpdb->base_prefix}blogs wb ON b.blog_id = wb.blog_id WHERE wb.deleted = 0 AND wb.spam = 0 AND wb.mature = 0 AND wb.archived = '0' AND user_id = %d", $user_id ) );
		} else {
			return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(DISTINCT b.blog_id) FROM {$bp->blogs->table_name} b LEFT JOIN {$wpdb->base_prefix}blogs wb ON b.blog_id = wb.blog_id WHERE wb.public = 1 AND wb.deleted = 0 AND wb.spam = 0 AND wb.mature = 0 AND wb.archived = '0' AND user_id = %d", $user_id ) );
		}
	}

	/**
	 * Return a list of blogs matching a search term.
	 *
	 * Matches against blog names and descriptions, as stored in the BP
	 * blogmeta table.
	 *
	 * @param string $filter The search term.
	 * @param int    $limit  Optional. The maximum number of items to return.
	 *                       Default: null (no limit).
	 * @param int    $page   Optional. The page of results to return. Default:
	 *                       null (no limit).
	 *
	 * @return array Multidimensional results array, structured as follows:
	 *               'blogs' - Array of located blog objects.
	 *               'total' - A count of the total blogs matching the query.
	 */
	public static function search_blogs( $filter, $limit = null, $page = null ) {
		global $wpdb;

		$search_terms_like = '%' . bp_esc_like( $filter ) . '%';
		$search_terms_sql  = $wpdb->prepare( 'bm.meta_value LIKE %s', $search_terms_like );

		$hidden_sql = '';
		if ( !bp_current_user_can( 'bp_moderate' ) )
			$hidden_sql = "AND wb.public = 1";

		$pag_sql = '';
		if ( $limit && $page ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		$bp = buddypress();

		$paged_blogs = $wpdb->get_results( "SELECT DISTINCT bm.blog_id FROM {$bp->blogs->table_name_blogmeta} bm LEFT JOIN {$wpdb->base_prefix}blogs wb ON bm.blog_id = wb.blog_id WHERE ( ( bm.meta_key = 'name' OR bm.meta_key = 'description' ) AND {$search_terms_sql} ) {$hidden_sql} AND wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 ORDER BY meta_value ASC{$pag_sql}" );
		$total_blogs = $wpdb->get_var( "SELECT COUNT(DISTINCT bm.blog_id) FROM {$bp->blogs->table_name_blogmeta} bm LEFT JOIN {$wpdb->base_prefix}blogs wb ON bm.blog_id = wb.blog_id WHERE ( ( bm.meta_key = 'name' OR bm.meta_key = 'description' ) AND {$search_terms_sql} ) {$hidden_sql} AND wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 ORDER BY meta_value ASC" );

		return array( 'blogs' => $paged_blogs, 'total' => $total_blogs );
	}

	/**
	 * Retrieve a list of all blogs.
	 *
	 * Query will include hidden blogs if the logged-in user has the
	 * 'bp_moderate' cap.
	 *
	 * @param int $limit Optional. The maximum number of items to return.
	 *                   Default: null (no limit).
	 * @param int $page  Optional. The page of results to return. Default:
	 *                   null (no limit).
	 *
	 * @return array Multidimensional results array, structured as follows:
	 *               'blogs' - Array of located blog objects.
	 *               'total' - A count of the total blogs.
	 */
	public static function get_all( $limit = null, $page = null ) {
		global $wpdb;

		$bp = buddypress();

		$hidden_sql = !bp_current_user_can( 'bp_moderate' ) ? "AND wb.public = 1" : '';
		$pag_sql    = ( $limit && $page ) ? $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) ) : '';

		$paged_blogs = $wpdb->get_results( "SELECT DISTINCT b.blog_id FROM {$bp->blogs->table_name} b LEFT JOIN {$wpdb->base_prefix}blogs wb ON b.blog_id = wb.blog_id WHERE wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 {$hidden_sql} {$pag_sql}" );
		$total_blogs = $wpdb->get_var( "SELECT COUNT(DISTINCT b.blog_id) FROM {$bp->blogs->table_name} b LEFT JOIN {$wpdb->base_prefix}blogs wb ON b.blog_id = wb.blog_id WHERE wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 {$hidden_sql}" );

		return array( 'blogs' => $paged_blogs, 'total' => $total_blogs );
	}

	/**
	 * Retrieve a list of blogs whose names start with a given letter.
	 *
	 * Query will include hidden blogs if the logged-in user has the
	 * 'bp_moderate' cap.
	 *
	 * @param string $letter. The letter you're looking for.
	 * @param int    $limit   Optional. The maximum number of items to return.
	 *                        Default: null (no limit).
	 * @param int    $page    Optional. The page of results to return. Default:
	 *                        null (no limit).
	 *
	 * @return array Multidimensional results array, structured as follows:
	 *               'blogs' - Array of located blog objects.
	 *               'total' - A count of the total blogs matching the query.
	 */
	public static function get_by_letter( $letter, $limit = null, $page = null ) {
		global $wpdb;

		$bp = buddypress();

		$letter_like = '%' . bp_esc_like( $letter ) . '%';
		$letter_sql  = $wpdb->prepare( 'bm.meta_value LIKE %s', $letter_like );

		$hidden_sql = '';
		if ( !bp_current_user_can( 'bp_moderate' ) )
			$hidden_sql = "AND wb.public = 1";

		$pag_sql = '';
		if ( $limit && $page )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		$paged_blogs = $wpdb->get_results( "SELECT DISTINCT bm.blog_id FROM {$bp->blogs->table_name_blogmeta} bm LEFT JOIN {$wpdb->base_prefix}blogs wb ON bm.blog_id = wb.blog_id WHERE bm.meta_key = 'name' AND {$letter_sql} {$hidden_sql} AND wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 ORDER BY bm.meta_value ASC{$pag_sql}" );
		$total_blogs = $wpdb->get_var( "SELECT COUNT(DISTINCT bm.blog_id) FROM {$bp->blogs->table_name_blogmeta} bm LEFT JOIN {$wpdb->base_prefix}blogs wb ON bm.blog_id = wb.blog_id WHERE bm.meta_key = 'name' AND {$letter_sql} {$hidden_sql} AND wb.mature = 0 AND wb.spam = 0 AND wb.archived = '0' AND wb.deleted = 0 ORDER BY bm.meta_value ASC" );

		return array( 'blogs' => $paged_blogs, 'total' => $total_blogs );
	}

	/**
	 * Fetch blog data not caught in the main query and append it to results array.
	 *
	 * Gets the following information, which is either unavailable at the
	 * time of the original query, or is more efficient to look up in one
	 * fell swoop:
	 *   - The latest post for each blog, include Featured Image data
	 *   - The blog description
	 *
	 * @param array       $paged_blogs Array of results from the original query.
	 * @param array       $blog_ids    Array of IDs returned from the original query.
	 * @param string|bool $type        Not currently used. Default: false.
	 *
	 * @return array $paged_blogs The located blogs array, with the extras added.
	 */
	public static function get_blog_extras( &$paged_blogs, &$blog_ids, $type = false ) {
		global $wpdb;

		$bp = buddypress();

		if ( empty( $blog_ids ) )
			return $paged_blogs;

		$blog_ids = implode( ',', wp_parse_id_list( $blog_ids ) );

		for ( $i = 0, $count = count( $paged_blogs ); $i < $count; ++$i ) {
			$blog_prefix = $wpdb->get_blog_prefix( $paged_blogs[$i]->blog_id );
			$paged_blogs[$i]->latest_post = $wpdb->get_row( "SELECT ID, post_content, post_title, post_excerpt, guid FROM {$blog_prefix}posts WHERE post_status = 'publish' AND post_type = 'post' AND id != 1 ORDER BY id DESC LIMIT 1" );
			$images = array();

			// Add URLs to any Featured Image this post might have
			if ( ! empty( $paged_blogs[$i]->latest_post ) && has_post_thumbnail( $paged_blogs[$i]->latest_post->ID ) ) {

				// Grab 4 sizes of the image. Thumbnail.
				$image = wp_get_attachment_image_src( get_post_thumbnail_id( $paged_blogs[$i]->latest_post->ID ), 'thumbnail', false );
				if ( ! empty( $image ) )
					$images['thumbnail'] = $image[0];

				// Medium
				$image = wp_get_attachment_image_src( get_post_thumbnail_id( $paged_blogs[$i]->latest_post->ID ), 'medium', false );
				if ( ! empty( $image ) )
					$images['medium'] = $image[0];

				// Large
				$image = wp_get_attachment_image_src( get_post_thumbnail_id( $paged_blogs[$i]->latest_post->ID ), 'large', false );
				if ( ! empty( $image ) )
					$images['large'] = $image[0];

				// Post thumbnail
				$image = wp_get_attachment_image_src( get_post_thumbnail_id( $paged_blogs[$i]->latest_post->ID ), 'post-thumbnail', false );
				if ( ! empty( $image ) )
					$images['post-thumbnail'] = $image[0];

				// Add the images to the latest_post object
				$paged_blogs[$i]->latest_post->images = $images;
			}
		}

		/* Fetch the blog description for each blog (as it may be empty we can't fetch it in the main query). */
		$blog_descs = $wpdb->get_results( "SELECT blog_id, meta_value as description FROM {$bp->blogs->table_name_blogmeta} WHERE meta_key = 'description' AND blog_id IN ( {$blog_ids} )" );

		for ( $i = 0, $count = count( $paged_blogs ); $i < $count; ++$i ) {
			foreach ( (array) $blog_descs as $desc ) {
				if ( $desc->blog_id == $paged_blogs[$i]->blog_id )
					$paged_blogs[$i]->description = $desc->description;
			}
		}

		return $paged_blogs;
	}

	/**
	 * Check whether a given blog is hidden.
	 *
	 * Checks the 'public' column in the wp_blogs table.
	 *
	 * @param int $blog_id The ID of the blog being checked.
	 *
	 * @return bool True if hidden (public = 0), false otherwise.
	 */
	public static function is_hidden( $blog_id ) {
		global $wpdb;

		if ( !(int) $wpdb->get_var( $wpdb->prepare( "SELECT public FROM {$wpdb->base_prefix}blogs WHERE blog_id = %d", $blog_id ) ) ) {
			return true;
		}

		return false;
	}

	/**
	 * Get ID of user-blog link.
	 *
	 * @param int $user_id ID of user.
	 * @param int $blog_id ID of blog.
	 *
	 * @return int|bool ID of user-blog link, or false if not found.
	 */
	public static function get_user_blog( $user_id, $blog_id ) {
		global $wpdb;

		$bp = buddypress();

		$user_blog = $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$bp->blogs->table_name} WHERE user_id = %d AND blog_id = %d", $user_id, $blog_id ) );

		if ( empty( $user_blog ) ) {
			$user_blog = false;
		} else {
			$user_blog = intval( $user_blog );
		}

		return $user_blog;
	}
}
