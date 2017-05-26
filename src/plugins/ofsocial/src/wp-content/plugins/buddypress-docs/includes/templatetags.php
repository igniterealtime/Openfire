<?php

if ( !function_exists( 'bp_is_root_blog' ) ) :
	/**
	 * Is this BP_ROOT_BLOG?
	 *
	 * Provides backward compatibility with pre-1.5 BP installs
	 *
	 * @package BuddyPress Docs
	 * @since 1.0.4
	 *
	 * @return bool $is_root_blog Returns true if this is BP_ROOT_BLOG. Always true on non-MS
	 */
	function bp_is_root_blog() {
		global $wpdb;

		$is_root_blog = true;

		if ( is_multisite() && $wpdb->blogid != BP_ROOT_BLOG )
			$is_root_blog = false;

		return apply_filters( 'bp_is_root_blog', $is_root_blog );
	}
endif;

/**
 * Initiates a BuddyPress Docs query
 *
 * @since 1.2
 */
function bp_docs_has_docs( $args = array() ) {
	global $bp, $wp_query;

	// The if-empty is because, like with WP itself, we use bp_docs_has_docs() both for the
	// initial 'if' of the loop, as well as for the 'while' iterator. Don't want infinite
	// queries
	if ( empty( $bp->bp_docs->doc_query ) ) {
		// Build some intelligent defaults

		// Default to current group id, if available
		if ( bp_is_group() ) {
			$d_group_id = bp_get_current_group_id();
		} else if ( bp_docs_is_mygroups_directory() ) {
			$my_groups = groups_get_user_groups( bp_loggedin_user_id() );
			$d_group_id = ! empty( $my_groups['total'] ) ? $my_groups['groups'] : array( 0 );
		} else {
			$d_group_id = array();
		}

		// If this is a Started By tab, set the author ID
		$d_author_id = bp_docs_is_started_by() ? bp_displayed_user_id() : array();

		// If this is an Edited By tab, set the edited_by id
		$d_edited_by_id = bp_docs_is_edited_by() ? bp_displayed_user_id() : array();

		// Default to the tags in the URL string, if available
		$d_tags = isset( $_REQUEST['bpd_tag'] ) ? explode( ',', urldecode( $_REQUEST['bpd_tag'] ) ) : array();

		// Order and orderby arguments
		$d_orderby = !empty( $_GET['orderby'] ) ? urldecode( $_GET['orderby'] ) : apply_filters( 'bp_docs_default_sort_order', 'modified' );

		if ( empty( $_GET['order'] ) ) {
			// If no order is explicitly stated, we must provide one.
			// It'll be different for date fields (should be DESC)
			if ( 'modified' == $d_orderby || 'date' == $d_orderby )
				$d_order = 'DESC';
			else
				$d_order = 'ASC';
		} else {
			$d_order = $_GET['order'];
		}

		// Search
		$d_search_terms = !empty( $_GET['s'] ) ? urldecode( $_GET['s'] ) : '';

		// Parent id
		$d_parent_id = !empty( $_REQUEST['parent_doc'] ) ? (int)$_REQUEST['parent_doc'] : '';

		// Page number, posts per page
		$d_paged = 1;
		if ( ! empty( $_GET['paged'] ) ) {
			$d_paged = absint( $_GET['paged'] );
		} else if ( bp_docs_is_global_directory() && is_a( $wp_query, 'WP_Query' ) && 1 < $wp_query->get( 'paged' ) ) {
			$d_paged = absint( $wp_query->get( 'paged' ) );
		} else {
			$d_paged = absint( $wp_query->get( 'paged', 1 ) );
		}

		// Use the calculated posts_per_page number from $wp_query->query_vars.
		// If that value isn't set, we assume 10 posts per page.
		$d_posts_per_page = absint( $wp_query->get( 'posts_per_page', 10 ) );

		// doc_slug
		$d_doc_slug = !empty( $bp->bp_docs->query->doc_slug ) ? $bp->bp_docs->query->doc_slug : '';

		$defaults = array(
			'doc_id'         => array(),      // Array or comma-separated string
			'doc_slug'       => $d_doc_slug,  // String (post_name/slug)
			'group_id'       => $d_group_id,  // Array or comma-separated string
			'parent_id'      => $d_parent_id, // int
			'author_id'      => $d_author_id, // Array or comma-separated string
			'edited_by_id'   => $d_edited_by_id, // Array or comma-separated string
			'tags'           => $d_tags,      // Array or comma-separated string
			'order'          => $d_order,        // ASC or DESC
			'orderby'        => $d_orderby,   // 'modified', 'title', 'author', 'created'
			'paged'	         => $d_paged,
			'posts_per_page' => $d_posts_per_page,
			'search_terms'   => $d_search_terms,
		);
		$r = wp_parse_args( $args, $defaults );

		$doc_query_builder      = new BP_Docs_Query( $r );
		$bp->bp_docs->doc_query = $doc_query_builder->get_wp_query();
	}

	return $bp->bp_docs->doc_query->have_posts();
}

/**
 * Part of the bp_docs_has_docs() loop
 *
 * @since 1.2
 */
function bp_docs_the_doc() {
	global $bp;

	return $bp->bp_docs->doc_query->the_post();
}

/**
 * Determine whether you are viewing a BuddyPress Docs page
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return bool
 */
function bp_docs_is_bp_docs_page() {
	global $bp, $post;

	$is_bp_docs_page = false;

	// This is intentionally ambiguous and generous, to account for BP Docs is different
	// components. Probably should be cleaned up at some point
	if ( isset( $bp->bp_docs->slug ) && $bp->bp_docs->slug == bp_current_component()
	     ||
	     isset( $bp->bp_docs->slug ) && $bp->bp_docs->slug == bp_current_action()
	     ||
	     isset( $post->post_type ) && bp_docs_get_post_type_name() == $post->post_type
	     ||
	     is_post_type_archive( bp_docs_get_post_type_name() )
	   )
		$is_bp_docs_page = true;

	return apply_filters( 'bp_docs_is_bp_docs_page', $is_bp_docs_page );
}


/**
 * Returns true if the current page is a BP Docs edit or create page (used to load JS)
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @returns bool
 */
function bp_docs_is_wiki_edit_page() {
	global $bp;

	$item_type = BP_Docs_Query::get_item_type();
	$current_view = BP_Docs_Query::get_current_view( $item_type );

	return apply_filters( 'bp_docs_is_wiki_edit_page', $is_wiki_edit_page );
}


/**
 * Echoes the output of bp_docs_get_info_header()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_info_header() {
	echo bp_docs_get_info_header();
}
	/**
	 * Get the info header for a list of docs
	 *
	 * Contains things like tag filters
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param int $doc_id optional The post_id of the doc
	 * @return str Permalink for the group doc
	 */
	function bp_docs_get_info_header() {
		$filters = bp_docs_get_current_filters();

		// Set the message based on the current filters
		if ( empty( $filters ) ) {
			$message = __( 'You are viewing <strong>all</strong> docs.', 'bp-docs' );
		} else {
			$message = array();

			$message = apply_filters( 'bp_docs_info_header_message', $message, $filters );

			$message = implode( "\n", $message );

			// We are viewing a subset of docs, so we'll add a link to clear filters
			// Figure out what the possible filter query args are.
			$filter_args = apply_filters( 'bp_docs_filter_types', array() );
			$filter_args = wp_list_pluck( $filter_args, 'query_arg' );
			$filter_args = array_merge( $filter_args, array( 'search_submit', 'folder' ) );

			$message .= ' - ' . sprintf( __( '<strong><a href="%s" title="View All Docs">View All Docs</a></strong>', 'bp-docs' ), remove_query_arg( $filter_args ) );
		}

		?>

		<p class="currently-viewing"><?php echo $message ?></p>

		<?php if ( $filter_titles = bp_docs_filter_titles() ) : ?>
			<div class="docs-filters">
				<p id="docs-filter-meta">
					<?php printf( __( 'Filter by: %s', 'bp-docs' ), $filter_titles ) ?>
				</p>

				<div id="docs-filter-sections">
					<?php do_action( 'bp_docs_filter_sections' ) ?>
				</div>
			</div>

			<div class="clear"> </div>
		<?php endif ?>

		<?php
	}

/**
 * Links/Titles for the filter types
 *
 * @since 1.4
 */
function bp_docs_filter_titles() {
	$filter_types = apply_filters( 'bp_docs_filter_types', array() );
	$links = array();
	foreach ( $filter_types as $filter_type ) {
		$current = isset( $_GET[ $filter_type['query_arg'] ] ) ? ' current' : '';
		$links[] = sprintf(
			'<a href="#" class="docs-filter-title%s" id="docs-filter-title-%s">%s</a>',
			apply_filters( 'bp_docs_filter_title_class', $current, $filter_type ),
			$filter_type['slug'],
			$filter_type['title']
		);
	}

	return implode( '', $links );
}

/**
 * Echoes the content of a Doc
 *
 * @since 1.3
 */
function bp_docs_the_content() {
	echo bp_docs_get_the_content();
}
	/**
	 * Returns the content of a Doc
	 *
	 * We need to use this special function, because the BP theme compat
	 * layer messes with the filters on the_content, and we can't rely on
	 * using that theme function within the context of a Doc
	 *
	 * @since 1.3
	 *
	 * @return string $content
	 */
	function bp_docs_get_the_content() {
		if ( function_exists( 'bp_restore_all_filters' ) ) {
			bp_restore_all_filters( 'the_content' );
		}

		$content = apply_filters( 'the_content', get_queried_object()->post_content );

		if ( function_exists( 'bp_remove_all_filters' ) ) {
			bp_remove_all_filters( 'the_content' );
		}

		return apply_filters( 'bp_docs_get_the_content', $content );
	}
/**
 * Filters the output of the doc list header for search terms
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return array $filters
 */
function bp_docs_search_term_filter_text( $message, $filters ) {
	if ( !empty( $filters['search_terms'] ) ) {
		$message[] = sprintf( __( 'You are searching for docs containing the term <em>%s</em>', 'bp-docs' ), esc_html( $filters['search_terms'] ) );
	}

	return $message;
}
add_filter( 'bp_docs_info_header_message', 'bp_docs_search_term_filter_text', 10, 2 );

/**
 * Get the filters currently being applied to the doc list
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return array $filters
 */
function bp_docs_get_current_filters() {
	$filters = array();

	// First check for tag filters
	if ( !empty( $_REQUEST['bpd_tag'] ) ) {
		// The bpd_tag argument may be comma-separated
		$tags = explode( ',', urldecode( $_REQUEST['bpd_tag'] ) );

		foreach ( $tags as $tag ) {
			$filters['tags'][] = $tag;
		}
	}

	// Now, check for search terms
	if ( !empty( $_REQUEST['s'] ) ) {
		$filters['search_terms'] = urldecode( $_REQUEST['s'] );
	}

	return apply_filters( 'bp_docs_get_current_filters', $filters );
}

/**
 * Echoes the output of bp_docs_get_doc_link()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_doc_link( $doc_id = false ) {
	echo bp_docs_get_doc_link( $doc_id );
}
	/**
	 * Get the doc's permalink
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param int $doc_id
	 * @return str URL of the doc
	 */
	function bp_docs_get_doc_link( $doc_id = false ) {
		if ( false === $doc_id ) {
			if ( is_singular( bp_docs_get_post_type_name() ) && $q = get_queried_object() ) {
				$doc_id = isset( $q->ID ) ? $q->ID : 0;
			} else if ( get_the_ID() ) {
				$doc_id = get_the_ID();
			}
		}

		return apply_filters( 'bp_docs_get_doc_link', trailingslashit( get_permalink( $doc_id ) ), $doc_id );
	}

/**
 * Echoes the output of bp_docs_get_doc_edit_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_doc_edit_link( $doc_id = false ) {
	echo bp_docs_get_doc_edit_link( $doc_id );
}
	/**
	 * Get the edit link for a doc
	 *
	 * @package BuddyPress_Docs
	 * @since 1.2
	 *
	 * @param int $doc_id
	 * @return str URL of the edit page for the doc
	 */
	function bp_docs_get_doc_edit_link( $doc_id = false ) {
		return apply_filters( 'bp_docs_get_doc_edit_link', trailingslashit( bp_docs_get_doc_link( $doc_id ) . BP_DOCS_EDIT_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_archive_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_archive_link() {
        echo bp_docs_get_archive_link();
}
	/**
         * Get the link to the main site Docs archive
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_archive_link() {
		return apply_filters( 'bp_docs_get_archive_link', trailingslashit( get_post_type_archive_link( bp_docs_get_post_type_name() ) ) );
	}

/**
 * Echoes the output of bp_docs_get_mygroups_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_mygroups_link() {
        echo bp_docs_get_mygroups_link();
}
	/**
         * Get the link the My Groups tab of the Docs archive
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_mygroups_link() {
		return apply_filters( 'bp_docs_get_mygroups_link', trailingslashit( bp_docs_get_archive_link() . BP_DOCS_MY_GROUPS_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_mydocs_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_mydocs_link() {
        echo bp_docs_get_mydocs_link();
}
	/**
         * Get the link to the My Docs tab of the logged in user
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_mydocs_link() {
		return apply_filters( 'bp_docs_get_mydocs_link', trailingslashit( bp_loggedin_user_domain() . bp_docs_get_docs_slug() ) );
	}

/**
 * Echoes the output of bp_docs_get_mydocs_started_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_mydocs_started_link() {
        echo bp_docs_get_mydocs_started_link();
}
	/**
         * Get the link to the Started By Me tab of the logged in user
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_mydocs_started_link() {
		return apply_filters( 'bp_docs_get_mydocs_started_link', trailingslashit( bp_docs_get_mydocs_link() . BP_DOCS_STARTED_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_mydocs_edited_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_mydocs_edited_link() {
        echo bp_docs_get_mydocs_edited_link();
}
	/**
         * Get the link to the Edited By Me tab of the logged in user
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_mydocs_edited_link() {
		return apply_filters( 'bp_docs_get_mydocs_edited_link', trailingslashit( bp_docs_get_mydocs_link() . BP_DOCS_EDITED_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_displayed_user_docs_started_link()
 *
 * @package BuddyPress_Docs
 * @since 1.9
 */
function bp_docs_displayed_user_docs_started_link() {
        echo bp_docs_get_displayed_user_docs_started_link();
}
	/**
     * Get the link to the Started By tab of the displayed user
     *
     * @package BuddyPress_Docs
     * @since 1.9
     */
	function bp_docs_get_displayed_user_docs_started_link() {
		return apply_filters( 'bp_docs_get_displayed_user_docs_started_link', user_trailingslashit( trailingslashit( bp_displayed_user_domain() . bp_docs_get_docs_slug() ) . BP_DOCS_STARTED_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_displayed_user_docs_edited_link()
 *
 * @package BuddyPress_Docs
 * @since 1.9
 */
function bp_docs_displayed_user_docs_edited_link() {
        echo bp_docs_get_displayed_user_docs_edited_link();
}
	/**
     * Get the link to the Edited By tab of the displayed user
     *
     * @package BuddyPress_Docs
     * @since 1.9
     */
	function bp_docs_get_displayed_user_docs_edited_link() {
		return apply_filters( 'bp_docs_get_displayed_user_docs_edited_link', user_trailingslashit( trailingslashit( bp_displayed_user_domain() . bp_docs_get_docs_slug() ) . BP_DOCS_EDITED_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_create_link()
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_create_link() {
        echo bp_docs_get_create_link();
}
	/**
         * Get the link to create a Doc
         *
         * @package BuddyPress_Docs
         * @since 1.2
         */
	function bp_docs_get_create_link() {
		return apply_filters( 'bp_docs_get_create_link', trailingslashit( bp_docs_get_archive_link() . BP_DOCS_CREATE_SLUG ) );
	}

/**
 * Echoes the output of bp_docs_get_item_docs_link()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_item_docs_link() {
	echo bp_docs_get_item_docs_link();
}
	/**
	 * Get the link to the docs section of an item
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @return array $filters
	 */
	function bp_docs_get_item_docs_link( $args = array() ) {
		global $bp;

		// @todo Disabling for now!!
		return;

		$d_item_type = '';
		if ( bp_is_user() ) {
			$d_item_type = 'user';
		} else if ( bp_is_active( 'groups' ) && bp_is_group() ) {
			$d_item_type = 'group';
		}

		switch ( $d_item_type ) {
			case 'user' :
				$d_item_id = bp_displayed_user_id();
				break;
			case 'group' :
				$d_item_id = bp_get_current_group_id();
				break;
		}

		$defaults = array(
			'item_id'   => $d_item_id,
			'item_type' => $d_item_type,
		);

		$r = wp_parse_args( $args, $defaults );
		extract( $r, EXTR_SKIP );

		if ( !$item_id || !$item_type )
			return false;

		switch ( $item_type ) {
			case 'group' :
				if ( !$group = $bp->groups->current_group )
					$group = groups_get_group( array( 'group_id' => $item_id ) );

				$base_url = bp_get_group_permalink( $group );
				break;

			case 'user' :
				$base_url = bp_core_get_user_domain( $item_id );
				break;
		}

		return apply_filters( 'bp_docs_get_item_docs_link', $base_url . $bp->bp_docs->slug . '/', $base_url, $r );
	}

/**
 * Get the sort order for sortable column links
 *
 * Detects the current sort order and returns the opposite
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return str $new_order Either desc or asc
 */
function bp_docs_get_sort_order( $orderby = 'modified' ) {

	$new_order	= false;

	// We only want a non-default order if we are currently ordered by this $orderby
	// The default order is Last Edited, so we must account for that
	$current_orderby	= !empty( $_GET['orderby'] ) ? $_GET['orderby'] : apply_filters( 'bp_docs_default_sort_order', 'modified' );

	if ( $orderby == $current_orderby ) {
		// Default sort orders are different for different fields
		if ( empty( $_GET['order'] ) ) {
			// If no order is explicitly stated, we must provide one.
			// It'll be different for date fields (should be DESC)
			if ( 'modified' == $current_orderby || 'date' == $current_orderby )
				$current_order = 'DESC';
			else
				$current_order = 'ASC';
		} else {
			$current_order = $_GET['order'];
		}

		$new_order = 'ASC' == $current_order ? 'DESC' : 'ASC';
	}

	return apply_filters( 'bp_docs_get_sort_order', $new_order );
}

/**
 * Echoes the output of bp_docs_get_order_by_link()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param str $orderby The order_by item: title, author, created, edited, etc
 */
function bp_docs_order_by_link( $orderby = 'modified' ) {
	echo bp_docs_get_order_by_link( $orderby );
}
	/**
	 * Get the URL for the sortable column header links
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param str $orderby The order_by item: title, author, created, modified, etc
	 * @return str The URL with args attached
	 */
	function bp_docs_get_order_by_link( $orderby = 'modified' ) {
		$args = array(
			'orderby' 	=> $orderby,
			'order'		=> bp_docs_get_sort_order( $orderby )
		);

		return apply_filters( 'bp_docs_get_order_by_link', add_query_arg( $args ), $orderby, $args );
	}

/**
 * Echoes current-orderby and order classes for the column currently being ordered by
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param str $orderby The order_by item: title, author, created, modified, etc
 */
function bp_docs_is_current_orderby_class( $orderby = 'modified' ) {
	// Get the current orderby column
	$current_orderby = !empty( $_GET['orderby'] ) ? $_GET['orderby'] : apply_filters( 'bp_docs_default_sort_order', 'modified' );

	// Does the current orderby match the $orderby parameter?
	$is_current_orderby = $current_orderby == $orderby ? true : false;

	$class = '';

	// If this is indeed the current orderby, we need to get the asc/desc class as well
	if ( $is_current_orderby ) {
		$class = ' current-orderby';

		if ( empty( $_GET['order'] ) ) {
			// If no order is explicitly stated, we must provide one.
			// It'll be different for date fields (should be DESC)
			if ( 'modified' == $current_orderby || 'date' == $current_orderby )
				$class .= ' desc';
			else
				$class .= ' asc';
		} else {
			$class .= 'DESC' == $_GET['order'] ? ' desc' : ' asc';
		}
	}

	echo apply_filters( 'bp_docs_is_current_orderby', $class, $is_current_orderby, $current_orderby );
}

/**
 * Prints the inline toggle setup script
 *
 * Ideally, I would put this into an external document; but the fact that it is supposed to hide
 * content immediately on pageload means that I didn't want to wait for an external script to
 * load, much less for document.ready. Sorry.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_inline_toggle_js() {
	?>
	<script type="text/javascript">
		/* Swap toggle text with a dummy link and hide toggleable content on load */
		var togs = jQuery('.toggleable');

		jQuery(togs).each(function(){
			var ts = jQuery(this).children('.toggle-switch');

			/* Get a unique identifier for the toggle */
			var tsid = jQuery(ts).attr('id').split('-');
			var type = tsid[0];

			/* Append the static toggle text with a '+' sign and linkify */
			var toggleid = type + '-toggle-link';
			var plus = '<span class="show-pane plus-or-minus"></span>';

			jQuery(ts).html('<a href="#" id="' + toggleid + '" class="toggle-link">' + plus + jQuery(ts).html() + '</a>');
		});

	</script>
	<?php
}

/**
 * Outputs the markup for the Associated Group settings section
 *
 * @since 1.2
 */
function bp_docs_doc_associated_group_markup() {
	// First, try to set the preselected group by looking at the URL params
	$selected_group_slug = isset( $_GET['group'] ) ? $_GET['group'] : '';

	// Support for BP Group Hierarchy
	if ( false !== $slash = strrpos( $selected_group_slug, '/' ) ) {
		$selected_group_slug = substr( $selected_group_slug, $slash + 1 );
	}

	$selected_group = BP_Groups_Group::get_id_from_slug( $selected_group_slug );
	if ( $selected_group && ! current_user_can( 'bp_docs_associate_with_group', $selected_group ) ) {
		$selected_group = 0;
	}

	// If the selected group is still 0, see if there's something in the db
	if ( ! $selected_group && is_singular() ) {
		$selected_group = bp_docs_get_associated_group_id( get_the_ID() );
	}

	// Last check: if this is a second attempt at a newly created Doc,
	// there may be a previously submitted value
	if ( empty( $selected_group ) && ! empty( buddypress()->bp_docs->submitted_data->associated_group_id ) ) {
		$selected_group = intval( buddypress()->bp_docs->submitted_data->associated_group_id );
	}

	$selected_group = intval( $selected_group );

	$groups_args = array(
		'per_page' => false,
		'populate_extras' => false,
		'type' => 'alphabetical',
	);

	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		$groups_args['user_id'] = bp_loggedin_user_id();
	}

	// Populate the $groups_template global
	global $groups_template;
	$old_gt = $groups_template;

	bp_has_groups( $groups_args );

	// Filter out the groups where associate_with permissions forbid
	$removed = 0;
	foreach ( $groups_template->groups as $gtg_key => $gtg ) {
		if ( ! current_user_can( 'bp_docs_associate_with_group', $gtg->id ) ) {
			unset( $groups_template->groups[ $gtg_key ] );
			$removed++;
		}
	}

	// cleanup, if necessary from filter above
	if ( $removed ) {
		$groups_template->groups = array_values( $groups_template->groups );
		$groups_template->group_count = $groups_template->group_count - $removed;
		$groups_template->total_group_count = $groups_template->total_group_count - $removed;
	}

	?>
	<tr>
		<td class="desc-column">
			<label for="associated_group_id"><?php _e( 'Which group should this Doc be associated with?', 'bp-docs' ) ?></label>
			<span class="description"><?php _e( '(Optional) Note that the Access settings available for this Doc may be limited by the privacy settings of the group you choose.', 'bp-docs' ) ?></span>
		</td>

		<td class="content-column">
			<select name="associated_group_id" id="associated_group_id">
				<option value=""><?php _e( 'None', 'bp-docs' ) ?></option>
				<?php foreach ( $groups_template->groups as $g ) : ?>
					<option value="<?php echo esc_attr( $g->id ) ?>" <?php selected( $selected_group, $g->id ) ?>><?php echo esc_html( $g->name ) ?></option>
				<?php endforeach ?>
			</select>

			<div id="associated_group_summary">
				<?php bp_docs_associated_group_summary() ?>
			</div>
		</td>
	</tr>
	<?php

	$groups_template = $old_gt;
}

/**
 * Display a summary of the associated group
 *
 * @since 1.2
 *
 * @param int $group_id
 */
function bp_docs_associated_group_summary( $group_id = 0 ) {
	$html = '';

	if ( ! $group_id ) {
		if ( isset( $_GET['group'] ) ) {
			$group_slug = $_GET['group'];
			$group_id   = BP_Groups_Group::get_id_from_slug( $group_slug );
		} else {
			$doc_id = is_singular() ? get_the_ID() : 0;
			$group_id = bp_docs_get_associated_group_id( $doc_id );
		}
	}

	$group_id = intval( $group_id );
	if ( $group_id ) {
		$group = groups_get_group( 'group_id=' . $group_id );

		if ( ! empty( $group->name ) ) {
			$group_link = esc_url( bp_get_group_permalink( $group ) );
			$group_avatar = bp_core_fetch_avatar( array(
				'item_id' => $group_id,
				'object' => 'group',
				'type' => 'thumb',
				'width' => '40',
				'height' => '40',
			) );
			$group_member_count = sprintf( 1 == $group->total_member_count ? __( '%s member', 'bp-docs' ) : __( '%s members', 'bp-docs' ), intval( $group->total_member_count ) );

			switch ( $group->status ) {
				case 'public' :
					$group_type_string = __( 'Public Group', 'bp-docs' );
					break;

				case 'private' :
					$group_type_string = __( 'Private Group', 'bp-docs' );
					break;

				case 'hidden' :
					$group_type_string = __( 'Hidden Group', 'bp-docs' );
					break;

				default :
					$group_type_string = '';
					break;
			}

			$html .= '<a href="' . $group_link . '">' . $group_avatar . '</a>';

			$html .= '<div class="item">';
			$html .= '<a href="' . $group_link . '">' . esc_html( $group->name ) . '</a>';
			$html .= '<div class="meta">' . $group_type_string . ' / ' . $group_member_count . '</div>';
			$html .= '</div>';
		}

	}

	echo $html;
}

/**
 * A hook for intergration pieces to insert their settings markup
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_doc_settings_markup( $doc_id = 0, $group_id = 0 ) {
	global $bp;

	if ( ! $doc_id ) {
		$doc_id = is_singular() ? get_the_ID() : 0;
	}

	$doc_settings = bp_docs_get_doc_settings( $doc_id, 'default', $group_id );

	$settings_fields = array(
		'read' => array(
			'name'  => 'read',
			'label' => __( 'Who can read this doc?', 'bp-docs' )
		),
		'edit' => array(
			'name'  => 'edit',
			'label' => __( 'Who can edit this doc?', 'bp-docs' )
		),
		'read_comments' => array(
			'name'  => 'read_comments',
			'label' => __( 'Who can read comments on this doc?', 'bp-docs' )
		),
		'post_comments' => array(
			'name'  => 'post_comments',
			'label' => __( 'Who can post comments on this doc?', 'bp-docs' )
		),
		'view_history' => array(
			'name'  => 'view_history',
			'label' => __( 'Who can view the history of this doc?', 'bp-docs' )
		)
	);

	foreach ( $settings_fields as $settings_field ) {
		bp_docs_access_options_helper( $settings_field, $doc_id, $group_id );
	}

	// Hand off the creation of additional settings to individual integration pieces
	do_action( 'bp_docs_doc_settings_markup', $doc_settings );
}

function bp_docs_access_options_helper( $settings_field, $doc_id = 0, $group_id = 0 ) {
	if ( $group_id ) {
		$settings_type = 'raw';
	} else {
		$settings_type = 'default';
	}

	$doc_settings = bp_docs_get_doc_settings( $doc_id, $settings_type, $group_id );

	// If this is a failed form submission, check the submitted values first
	if ( ! empty( buddypress()->bp_docs->submitted_data->settings->{$settings_field['name']} ) ) {
		$setting = buddypress()->bp_docs->submitted_data->settings->{$settings_field['name']};
	} else {
		$setting = isset( $doc_settings[ $settings_field['name'] ] ) ? $doc_settings[ $settings_field['name'] ] : '';
	}

	?>
	<tr class="bp-docs-access-row bp-docs-access-row-<?php echo esc_attr( $settings_field['name'] ) ?>">
		<td class="desc-column">
			<label for="settings-<?php echo esc_attr( $settings_field['name'] ) ?>"><?php echo esc_html( $settings_field['label'] ) ?></label>
		</td>

		<td class="content-column">
			<select name="settings[<?php echo esc_attr( $settings_field['name'] ) ?>]" id="settings-<?php echo esc_attr( $settings_field['name'] ) ?>">
				<?php $access_options = bp_docs_get_access_options( $settings_field['name'], $doc_id, $group_id ) ?>
				<?php foreach ( $access_options as $key => $option ) : ?>
					<?php
					$selected = selected( $setting, $option['name'], false );
					if ( empty( $setting ) && ! empty( $option['default'] ) ) {
						$selected = selected( 1, 1, false );
					}
					?>
					<option value="<?php echo esc_attr( $option['name'] ) ?>" <?php echo $selected ?>><?php echo esc_attr( $option['label'] ) ?></option>
				<?php endforeach ?>
			</select>
		</td>
	</tr>

	<?php
}

/**
 * Outputs the links that appear under each Doc in the Doc listing
 *
 * @package BuddyPress Docs
 */
function bp_docs_doc_action_links() {
	$links = array();

	$links[] = '<a href="' . bp_docs_get_doc_link() . '">' . __( 'Read', 'bp-docs' ) . '</a>';

	if ( current_user_can( 'bp_docs_edit', get_the_ID() ) ) {
		$links[] = '<a href="' . bp_docs_get_doc_edit_link() . '">' . __( 'Edit', 'bp-docs' ) . '</a>';
	}

	if ( current_user_can( 'bp_docs_view_history', get_the_ID() ) && defined( 'WP_POST_REVISIONS' ) && WP_POST_REVISIONS ) {
		$links[] = '<a href="' . bp_docs_get_doc_link() . BP_DOCS_HISTORY_SLUG . '">' . __( 'History', 'bp-docs' ) . '</a>';
	}

	if ( current_user_can( 'manage', get_the_ID() ) && bp_docs_is_doc_trashed( get_the_ID() ) ) {
		$links[] = '<a href="' . bp_docs_get_remove_from_trash_link( get_the_ID() ) . '" class="delete confirm">' . __( 'Untrash', 'bp-docs' ) . '</a>';
	}

	$links = apply_filters( 'bp_docs_doc_action_links', $links, get_the_ID() );

	echo implode( ' &#124; ', $links );
}

function bp_docs_current_group_is_public() {
	global $bp;

	if ( !empty( $bp->groups->current_group->status ) && 'public' == $bp->groups->current_group->status )
		return true;

	return false;
}

/**
 * Echoes the output of bp_docs_get_delete_doc_link()
 *
 * @package BuddyPress Docs
 * @since 1.0.1
 */
function bp_docs_delete_doc_link() {
	echo bp_docs_get_delete_doc_link();
}
	/**
	 * Get the URL to delete the current doc
	 *
	 * @package BuddyPress Docs
	 * @since 1.0.1
	 *
	 * @return string $delete_link href for the delete doc link
	 */
	function bp_docs_get_delete_doc_link() {
		$doc_permalink = bp_docs_get_doc_link();

		$delete_link = wp_nonce_url( add_query_arg( BP_DOCS_DELETE_SLUG, '1', $doc_permalink ), 'bp_docs_delete' );

		return apply_filters( 'bp_docs_get_delete_doc_link', $delete_link, $doc_permalink );
	}


/**
 * Echo the URL to remove a Doc from the Trash.
 *
 * @since 1.5.5
 */
function bp_docs_remove_from_trash_link( $doc_id = false ) {
	echo bp_docs_get_remove_from_trash_link( $doc_id );
}
	/**
	 * Get the URL for removing a Doc from the Trash.
	 *
	 * @since 1.5.5
	 *
	 * @param $doc_id ID of the Doc.
	 * @return string URL for Doc untrashing.
	 */
	function bp_docs_get_remove_from_trash_link( $doc_id ) {
		$doc_permalink = bp_docs_get_doc_link( $doc_id );

		$untrash_link = wp_nonce_url( add_query_arg( array(
			BP_DOCS_UNTRASH_SLUG => '1',
			'doc_id' => intval( $doc_id ),
		), $doc_permalink ), 'bp_docs_untrash' );

		return apply_filters( 'bp_docs_get_remove_from_trash_link', $untrash_link, $doc_permalink );
	}

/**
 * Echo the Delete/Untrash link for use on single Doc pages.
 *
 * @since 1.5.5
 *
 * @param int $doc_id Optional. Default: current Doc.
 */
function bp_docs_delete_doc_button( $doc_id = false ) {
	echo bp_docs_get_delete_doc_button( $doc_id );
}
	/**
	 * Get HTML for the Delete/Untrash link used on single Doc pages.
	 *
	 * @since 1.5.5
	 *
	 * @param int $doc_id Optional. Default: ID of current Doc.
	 * @return string HTML of Delete/Remove from Trash link.
	 */
	function bp_docs_get_delete_doc_button( $doc_id = false ) {
		if ( ! $doc_id ) {
			$doc_id = bp_docs_is_existing_doc() ? get_queried_object_id() : get_the_ID();
		}

		if ( bp_docs_is_doc_trashed( $doc_id ) ) {
			$button = '<a class="delete-doc-button untrash-doc-button confirm" href="' . bp_docs_get_remove_from_trash_link( $doc_id ) . '">' . __( 'Remove from Trash', 'bp-docs' ) . '</a>';
		} else {
			$button = '<a class="delete-doc-button confirm" href="' . bp_docs_get_delete_doc_link() . '">' . __( 'Delete', 'bp-docs' ) . '</a>';
		}

		return $button;
	}

/**
 * Echo the pagination links for the doc list view
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 */
function bp_docs_paginate_links() {
	global $bp, $wp_query, $wp_rewrite;

    $page_links_total = $bp->bp_docs->doc_query->max_num_pages;

	$pagination_args = array(
		'base' 		=> add_query_arg( 'paged', '%#%' ),
		'format' 	=> '',
		'prev_text' 	=> __('&laquo;'),
		'next_text' 	=> __('&raquo;'),
		'total' 	=> $page_links_total,
		'end_size'  => 2,
	);

	if ( $wp_rewrite->using_permalinks() ) {
		$pagination_args['base'] = apply_filters( 'bp_docs_page_links_base_url', user_trailingslashit( trailingslashit( bp_docs_get_archive_link() ) . $wp_rewrite->pagination_base . '/%#%/', 'bp-docs-directory' ), $wp_rewrite->pagination_base );
	}

    $page_links = paginate_links( $pagination_args );

    echo apply_filters( 'bp_docs_paginate_links', $page_links );
}

/**
 * Get the start number for the current docs view (ie "Viewing *5* - 8 of 12")
 *
 * Here's the math: Subtract one from the current page number; multiply times posts_per_page to get
 * the last post on the previous page; add one to get the start for this page.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 *
 * @return int $start The start number
 */
function bp_docs_get_current_docs_start() {
	global $bp;

	$paged = !empty( $bp->bp_docs->doc_query->query_vars['paged'] ) ? $bp->bp_docs->doc_query->query_vars['paged'] : 1;

	$posts_per_page = !empty( $bp->bp_docs->doc_query->query_vars['posts_per_page'] ) ? $bp->bp_docs->doc_query->query_vars['posts_per_page'] : 10;

	$start = ( ( $paged - 1 ) * $posts_per_page ) + 1;

	return apply_filters( 'bp_docs_get_current_docs_start', $start );
}

/**
 * Get the end number for the current docs view (ie "Viewing 5 - *8* of 12")
 *
 * Here's the math: Multiply the posts_per_page by the current page number. If it's the last page
 * (ie if the result is greater than the total number of docs), just use the total doc count
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 *
 * @return int $end The start number
 */
function bp_docs_get_current_docs_end() {
	global $bp;

	$paged = !empty( $bp->bp_docs->doc_query->query_vars['paged'] ) ? $bp->bp_docs->doc_query->query_vars['paged'] : 1;

	$posts_per_page = !empty( $bp->bp_docs->doc_query->query_vars['posts_per_page'] ) ? $bp->bp_docs->doc_query->query_vars['posts_per_page'] : 10;

	$end = $paged * $posts_per_page;

	if ( $end > bp_docs_get_total_docs_num() )
		$end = bp_docs_get_total_docs_num();

	return apply_filters( 'bp_docs_get_current_docs_end', $end );
}

/**
 * Get the total number of found docs out of $wp_query
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 *
 * @return int $total_doc_count The start number
 */
function bp_docs_get_total_docs_num() {
	global $bp;

	$total_doc_count = !empty( $bp->bp_docs->doc_query->found_posts ) ? $bp->bp_docs->doc_query->found_posts : 0;

	return apply_filters( 'bp_docs_get_total_docs_num', $total_doc_count );
}

/**
 * Display a Doc's comments
 *
 * This function was introduced to make sure that the comment display callback function can be
 * filtered by site admins. Originally, wp_list_comments() was called directly from the template
 * with the callback bp_dtheme_blog_comments, but this caused problems for sites not running a
 * child theme of bp-default.
 *
 * Filter bp_docs_list_comments_args to provide your own comment-formatting function.
 *
 * @package BuddyPress Docs
 * @since 1.0.5
 */
function bp_docs_list_comments() {
	$args = array();

	if ( function_exists( 'bp_dtheme_blog_comments' ) )
		$args['callback'] = 'bp_dtheme_blog_comments';

	$args = apply_filters( 'bp_docs_list_comments_args', $args );

	wp_list_comments( $args );
}

/**
 * Are we looking at an existing doc?
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return bool True if it's an existing doc
 */
function bp_docs_is_existing_doc() {
	global $wp_query;

	$is_existing_doc = false;

	if ( isset( $wp_query ) && is_a( $wp_query, 'WP_Query' ) ) {
		$post_obj = get_queried_object();
		if ( isset( $post_obj->post_type ) && is_singular( bp_docs_get_post_type_name() ) ) {
			$is_existing_doc = true;
		}
	}

	return apply_filters( 'bp_docs_is_existing_doc', $is_existing_doc );
}

/**
 * What's the current view?
 *
 * @package BuddyPress Docs
 * @since 1.1
 *
 * @return str $current_view The current view
 */
function bp_docs_current_view() {
	global $bp;

	$view = !empty( $bp->bp_docs->current_view ) ? $bp->bp_docs->current_view : false;

	return apply_filters( 'bp_docs_current_view', $view );
}

/**
 * Todo: Make less hackish
 */
function bp_docs_doc_permalink() {
	if ( bp_is_group() ) {
		bp_docs_group_doc_permalink();
	} else {
		the_permalink();
	}
}

function bp_docs_slug() {
	echo bp_docs_get_slug();
}
	function bp_docs_get_slug() {
		global $bp;
		return apply_filters( 'bp_docs_get_slug', $bp->bp_docs->slug );
	}

function bp_docs_get_docs_slug() {
	global $bp;

	if ( defined( 'BP_DOCS_SLUG' ) ) {
		$slug = BP_DOCS_SLUG;
		$is_in_wp_config = true;
	} else {
		$slug = bp_get_option( 'bp-docs-slug' );
		if ( empty( $slug ) ) {
			$slug = 'docs';
		}

		// for backward compatibility
		define( 'BP_DOCS_SLUG', $slug );
		$is_in_wp_config = false;
	}

	// For the settings page
	if ( ! isset( $bp->bp_docs->slug_defined_in_wp_config['slug'] ) ) {
		$bp->bp_docs->slug_defined_in_wp_config['slug'] = (int) $is_in_wp_config;
	}

	return apply_filters( 'bp_docs_get_docs_slug', $slug );
}

/**
 * Outputs the tabs at the top of the Docs view (All Docs, New Doc, etc)
 *
 * At the moment, the group-specific stuff is hard coded in here.
 * @todo Get the group stuff out
 */
function bp_docs_tabs( $show_create_button = true ) {
	$current_view = '';

	?>

	<ul id="bp-docs-all-docs">
		<li<?php if ( bp_docs_is_global_directory() ) : ?> class="current"<?php endif; ?>><a href="<?php bp_docs_archive_link() ?>"><?php _e( 'All Docs', 'bp-docs' ) ?></a></li>

		<?php if ( is_user_logged_in() ) : ?>
			<?php if ( function_exists( 'bp_is_group' ) && bp_is_group() ) : ?>
				<li<?php if ( bp_is_current_action( 'docs' ) ) : ?> class="current"<?php endif ?>><a href="<?php bp_group_permalink( groups_get_current_group() ) ?><?php bp_docs_slug() ?>"><?php printf( __( "%s's Docs", 'bp-docs' ), bp_get_current_group_name() ) ?></a></li>
			<?php else : ?>
				<li><a href="<?php bp_docs_mydocs_started_link() ?>"><?php _e( 'Started By Me', 'bp-docs' ) ?></a></li>
				<li><a href="<?php bp_docs_mydocs_edited_link() ?>"><?php _e( 'Edited By Me', 'bp-docs' ) ?></a></li>

				<?php if ( bp_is_active( 'groups' ) ) : ?>
					<li<?php if ( bp_docs_is_mygroups_docs() ) : ?> class="current"<?php endif; ?>><a href="<?php bp_docs_mygroups_link() ?>"><?php _e( 'My Groups', 'bp-docs' ) ?></a></li>
				<?php endif ?>
			<?php endif ?>

		<?php endif ?>

		<?php if ( $show_create_button ) : ?>
			<?php bp_docs_create_button() ?>
		<?php endif ?>

	</ul>
	<?php
}

/**
 * Echoes the Create A Doc button
 *
 * @since 1.2
 */
function bp_docs_create_button() {
	if ( ! bp_docs_is_doc_create() && current_user_can( 'bp_docs_create' ) ) {
		echo apply_filters( 'bp_docs_create_button', '<a class="button" id="bp-create-doc-button" href="' . bp_docs_get_create_link() . '">' . __( "Create New Doc", 'bp-docs' ) . '</a>' );
	}
}

/**
 * Puts a Create A Doc button on the members nav of member doc lists
 *
 * @since 1.2.1
 */
function bp_docs_member_create_button() {
	if ( bp_docs_is_docs_component() ) { ?>
		<?php bp_docs_create_button(); ?>
	<?php
	}
}
add_action( 'bp_member_plugin_options_nav', 'bp_docs_member_create_button' );

/**
 * Markup for the Doc Permissions snapshot
 *
 * Markup is built inline. Someday I may abstract it. In the meantime, suck a lemon
 *
 * @since 1.2
 */
function bp_docs_doc_permissions_snapshot( $args = array() ) {
	$html = '';

	$defaults = array(
		'summary_before_content' => '',
		'summary_after_content' => ''
	);

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( bp_is_active( 'groups' ) ) {
		$doc_group_ids = bp_docs_get_associated_group_id( get_the_ID(), false, true );
		$doc_groups = array();
		foreach( $doc_group_ids as $dgid ) {
			$maybe_group = groups_get_group( 'group_id=' . $dgid );

			// Don't show hidden groups if the
			// current user is not a member
			if ( isset( $maybe_group->status ) && 'hidden' === $maybe_group->status ) {
				// @todo this is slow
				if ( ! current_user_can( 'bp_moderate' ) && ! groups_is_user_member( bp_loggedin_user_id(), $dgid ) ) {
					continue;
				}
			}

			if ( !empty( $maybe_group->name ) ) {
				$doc_groups[] = $maybe_group;
			}
		}

		// First set up the Group snapshot, if there is one
		if ( ! empty( $doc_groups ) ) {
			$group_link = bp_get_group_permalink( $doc_groups[0] );
			$html .= '<div id="doc-group-summary">';

			$html .= $summary_before_content ;
			$html .= '<span>' . __('Group: ', 'bp-docs') . '</span>';

			$html .= sprintf( __( ' %s', 'bp-docs' ), '<a href="' . $group_link . '">' . bp_core_fetch_avatar( 'item_id=' . $doc_groups[0]->id . '&object=group&type=thumb&width=25&height=25' ) . '</a> ' . '<a href="' . $group_link . '">' . esc_html( $doc_groups[0]->name ) . '</a>' );

			$html .= $summary_after_content;

			$html .= '</div>';
		}

		// we'll need a list of comma-separated group names
		$group_names = implode( ', ', wp_list_pluck( $doc_groups, 'name' ) );
	}

	$levels = array(
		'anyone'        => __( 'Anyone', 'bp-docs' ),
		'loggedin'      => __( 'Logged-in Users', 'bp-docs' ),
		'friends'       => __( 'My Friends', 'bp-docs' ),
		'creator'       => __( 'The Doc author only', 'bp-docs' ),
		'no-one'        => __( 'Just Me', 'bp-docs' )
	);

	if ( bp_is_active( 'groups' ) ) {
		$levels['group-members'] = sprintf( __( 'Members of: %s', 'bp-docs' ), $group_names );
		$levels['admins-mods'] = sprintf( __( 'Admins and mods of the group %s', 'bp-docs' ), $group_names );
	}

	if ( get_the_author_meta( 'ID' ) == bp_loggedin_user_id() ) {
		$levels['creator'] = __( 'The Doc author only (that\'s you!)', 'bp-docs' );
	}

	$settings = bp_docs_get_doc_settings();

	// Read
	$read_class = bp_docs_get_permissions_css_class( $settings['read'] );
	$read_text  = sprintf( __( 'This Doc can be read by: <strong>%s</strong>', 'bp-docs' ), $levels[ $settings['read'] ] );

	// Edit
	$edit_class = bp_docs_get_permissions_css_class( $settings['edit'] );
	$edit_text  = sprintf( __( 'This Doc can be edited by: <strong>%s</strong>', 'bp-docs' ), $levels[ $settings['edit'] ] );

	// Read Comments
	$read_comments_class = bp_docs_get_permissions_css_class( $settings['read_comments'] );
	$read_comments_text  = sprintf( __( 'Comments are visible to: <strong>%s</strong>', 'bp-docs' ), $levels[ $settings['read_comments'] ] );

	// Post Comments
	$post_comments_class = bp_docs_get_permissions_css_class( $settings['post_comments'] );
	$post_comments_text  = sprintf( __( 'Comments can be posted by: <strong>%s</strong>', 'bp-docs' ), $levels[ $settings['post_comments'] ] );

	// View History
	$view_history_class = bp_docs_get_permissions_css_class( $settings['view_history'] );
	$view_history_text  = sprintf( __( 'History can be viewed by: <strong>%s</strong>', 'bp-docs' ), $levels[ $settings['view_history'] ] );

	// Calculate summary
	// Summary works like this:
	//  'public'  - all read_ items set to 'anyone', all others to 'anyone' or 'loggedin'
	//  'private' - everything set to 'admins-mods', 'creator', 'no-one', 'friends', or 'group-members' where the associated group is non-public
	//  'limited' - everything else
	$anyone_count  = 0;
	$private_count = 0;
	$public_settings = array(
		'read'          => 'anyone',
		'edit'          => 'loggedin',
		'read_comments' => 'anyone',
		'post_comments' => 'loggedin',
		'view_history'  => 'anyone'
	);

	foreach ( $settings as $l => $v ) {
		if ( 'anyone' == $v || ( isset( $public_settings[ $l ] ) && $public_settings[ $l ] == $v ) ) {

			$anyone_count++;

		} else if ( in_array( $v, array( 'admins-mods', 'creator', 'no-one', 'friends', 'group-members' ) ) ) {

			if ( 'group-members' == $v ) {
				if ( ! isset( $group_status ) ) {
					$group_status = 'foo'; // todo
				}

				if ( 'public' != $group_status ) {
					$private_count++;
				}
			} else {
				$private_count++;
			}

		}
	}

	$settings_count = count( $public_settings );
	if ( $settings_count == $private_count ) {
		$summary       = 'private';
		$summary_label = __( 'Private', 'bp-docs' );
	} else if ( $settings_count == $anyone_count ) {
		$summary       = 'public';
		$summary_label = __( 'Public', 'bp-docs' );
	} else {
		$summary       = 'limited';
		$summary_label = __( 'Limited', 'bp-docs' );
	}

	$html .= '<div id="doc-permissions-summary" class="doc-' . $summary . '">';
	$html .= $summary_before_content;
 $html .=   sprintf( __( 'Access: <strong>%s</strong>', 'bp-docs' ), $summary_label );
	$html .=   '<a href="#" class="doc-permissions-toggle" id="doc-permissions-more">' . __( 'Show Details', 'bp-docs' ) . '</a>';
	$html .= $summary_after_content;
 $html .= '</div>';

	$html .= '<div id="doc-permissions-details">';
	$html .=   '<ul>';
	$html .=     '<li class="bp-docs-can-read ' . $read_class . '"><span class="bp-docs-level-icon"></span>' . '<span class="perms-text">' . $read_text . '</span></li>';
	$html .=     '<li class="bp-docs-can-edit ' . $edit_class . '"><span class="bp-docs-level-icon"></span>' . '<span class="perms-text">' . $edit_text . '</span></li>';
	$html .=     '<li class="bp-docs-can-read_comments ' . $read_comments_class . '"><span class="bp-docs-level-icon"></span>' . '<span class="perms-text">' . $read_comments_text . '</span></li>';
	$html .=     '<li class="bp-docs-can-post_comments ' . $post_comments_class . '"><span class="bp-docs-level-icon"></span>' . '<span class="perms-text">' . $post_comments_text . '</span></li>';
	$html .=     '<li class="bp-docs-can-view_history ' . $view_history_class . '"><span class="bp-docs-level-icon"></span>' . '<span class="perms-text">' . $view_history_text . '</span></li>';
	$html .=   '</ul>';

	if ( current_user_can( 'bp_docs_manage' ) )
		$html .=   '<a href="' . bp_docs_get_doc_edit_link() . '#doc-settings" id="doc-permissions-edit">' . __( 'Edit', 'bp-docs' ) . '</a>';

	$html .=   '<a href="#" class="doc-permissions-toggle" id="doc-permissions-less">' . __( 'Hide Details', 'bp-docs' ) . '</a>';
	$html .= '</div>';

	echo $html;
}

function bp_docs_get_permissions_css_class( $level ) {
	return apply_filters( 'bp_docs_get_permissions_css_class', 'bp-docs-level-' . $level );
}

/**
 * Blasts any previous queries stashed in the BP global
 *
 * @since 1.2
 */
function bp_docs_reset_query() {
	global $bp;

	if ( isset( $bp->bp_docs->doc_query ) ) {
		unset( $bp->bp_docs->doc_query );
	}
}

/**
 * Get a total doc count, for a user, a group, or the whole site
 *
 * @since 1.2
 * @todo Total sitewide doc count
 *
 * @param int $item_id The id of the item (user or group)
 * @param str $item_type 'user' or 'group'
 * @return int
 */
function bp_docs_get_doc_count( $item_id = 0, $item_type = '' ) {
	$doc_count = 0;

	switch ( $item_type ) {
		case 'user' :
			$doc_count = get_user_meta( $item_id, 'bp_docs_count', true );

			if ( '' === $doc_count ) {
				$doc_count = bp_docs_update_doc_count( $item_id, 'user' );
			}

			break;
		case 'group' :
			$doc_count = groups_get_groupmeta( $item_id, 'bp-docs-count' );

			if ( '' === $doc_count ) {
				$doc_count = bp_docs_update_doc_count( $item_id, 'group' );
			}
			break;
	}

	return apply_filters( 'bp_docs_get_doc_count', (int)$doc_count, $item_id, $item_type );
}

/**
 * Is the current page a single Doc?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_single_doc() {
	global $wp_query;

	$is_single_doc = false;

	// There's an odd bug in WP_Query that causes errors when attempting to access
	// get_queried_object() too early. The check for $wp_query->post is a workaround
	if ( is_singular() && ! empty( $wp_query->post ) ) {
		$post = get_queried_object();

		if ( isset( $post->post_type ) && bp_docs_get_post_type_name() == $post->post_type ) {
			$is_single_doc = true;
		}
	}

	return apply_filters( 'bp_docs_is_single_doc', $is_single_doc );
}

/**
 * Is the current page a single Doc 'read' view?
 *
 * By process of elimination.
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_doc_read() {
	$is_doc_read = false;

	if ( bp_docs_is_single_doc() &&
	     ! bp_docs_is_doc_edit() &&
	     ( !function_exists( 'bp_docs_is_doc_history' ) || !bp_docs_is_doc_history() )
	   ) {
	 	$is_doc_read = true;
	}

	return apply_filters( 'bp_docs_is_doc_read', $is_doc_read );
}


/**
 * Is the current page a doc edit?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_doc_edit() {
	$is_doc_edit = false;

	if ( bp_docs_is_single_doc() && 1 == get_query_var( BP_DOCS_EDIT_SLUG ) ) {
		$is_doc_edit = true;
	}

	return apply_filters( 'bp_docs_is_doc_edit', $is_doc_edit );
}

/**
 * Is this the Docs create screen?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_doc_create() {
	$is_doc_create = false;

	if ( is_post_type_archive( bp_docs_get_post_type_name() ) && 1 == get_query_var( BP_DOCS_CREATE_SLUG ) ) {
		$is_doc_create = true;
	}

	return apply_filters( 'bp_docs_is_doc_create', $is_doc_create );
}

/**
 * Is this the My Groups tab of the Docs archive?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_mygroups_docs() {
	$is_mygroups_docs = false;

	if ( is_post_type_archive( bp_docs_get_post_type_name() ) && 1 == get_query_var( BP_DOCS_MY_GROUPS_SLUG ) ) {
		$is_mygroups_docs = true;
	}

	return apply_filters( 'bp_docs_is_mygroups_docs', $is_mygroups_docs );
}

/**
 * Is this the History tab?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_doc_history() {
	$is_doc_history = false;

	if ( bp_docs_is_single_doc() && 1 == get_query_var( BP_DOCS_HISTORY_SLUG ) ) {
		$is_doc_history = true;
	}

	return apply_filters( 'bp_docs_is_doc_history', $is_doc_history );
}

/**
 * Is this the Docs tab of a user profile?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_user_docs() {
	$is_user_docs = false;

	if ( bp_is_user() && bp_docs_is_docs_component() ) {
		$is_user_docs = true;
	}

	return apply_filters( 'bp_docs_is_user_docs', $is_user_docs );
}

/**
 * Is this the Started By tab of a user profile?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_started_by() {
	$is_started_by = false;

	if ( bp_docs_is_user_docs() && bp_is_current_action( BP_DOCS_STARTED_SLUG ) ) {
		$is_started_by = true;
	}

	return apply_filters( 'bp_docs_is_started_by', $is_started_by );
}

/**
 * Is this the Edited By tab of a user profile?
 *
 * @since 1.2
 * @return bool
 */
function bp_docs_is_edited_by() {
	$is_edited_by = false;

	if ( bp_docs_is_user_docs() && bp_is_current_action( BP_DOCS_EDITED_SLUG ) ) {
		$is_edited_by = true;
	}

	return apply_filters( 'bp_docs_is_edited_by', $is_edited_by );
}

/**
 * Is this the global Docs directory?
 */
function bp_docs_is_global_directory() {
	$is_global_directory = false;

	if ( is_post_type_archive( bp_docs_get_post_type_name() ) && ! get_query_var( BP_DOCS_MY_GROUPS_SLUG ) && ! get_query_var( BP_DOCS_CREATE_SLUG ) ) {
		$is_global_directory = true;
	}

	return apply_filters( 'bp_docs_is_global_directory', $is_global_directory );
}

/**
 * Is this the My Groups directory?
 *
 * @since 1.5
 * @return bool
 */
function bp_docs_is_mygroups_directory() {
	$is_mygroups_directory = false;

	if ( is_post_type_archive( bp_docs_get_post_type_name() ) && get_query_var( BP_DOCS_MY_GROUPS_SLUG ) && ! get_query_var( BP_DOCS_CREATE_SLUG ) ) {
		$is_mygroups_directory = true;
	}

	return apply_filters( 'bp_docs_is_mygroups_directory', $is_mygroups_directory );
}

function bp_docs_get_sidebar() {
	if ( $template = apply_filters( 'bp_docs_sidebar_template', '' ) ) {
		load_template( $template );
	} else {
		get_sidebar( 'buddypress' );
	}
}

/**
 * Renders the Permissions Snapshot
 *
 * @since 1.3
 */
function bp_docs_render_permissions_snapshot() {
	$show_snapshot = is_user_logged_in();

	if ( apply_filters( 'bp_docs_allow_access_settings', $show_snapshot ) )  {
		?>
		<div class="doc-permissions">
			<?php bp_docs_doc_permissions_snapshot() ?>
		</div>
		<?php
	}
}
add_action( 'bp_docs_single_doc_header_fields', 'bp_docs_render_permissions_snapshot' );

/**
 * Renders the Add Files button area
 *
 * @since 1.4
 */
function bp_docs_media_buttons( $editor_id ) {
	if ( bp_docs_is_existing_doc() && ! current_user_can( 'bp_docs_edit' ) ) {
		return;
	}

	$post = get_post();
	if ( ! $post && ! empty( $GLOBALS['post_ID'] ) )
		$post = $GLOBALS['post_ID'];

	wp_enqueue_media( array(
		'post' => $post
	) );

	$img = '<span class="wp-media-buttons-icon"></span> ';

	echo '<a href="#" id="insert-media-button" class="button add-attachment add_media" data-editor="' . esc_attr( $editor_id ) . '" title="' . esc_attr__( 'Add Files', 'bp-docs' ) . '">' . $img . __( 'Add Files', 'bp-docs' ) . '</a>';
}

/**
 * Fetch the attachments for a Doc
 *
 * @since 1.4
 * @return array
 */
function bp_docs_get_doc_attachments( $doc_id = null ) {

	if ( is_null( $doc_id ) ) {
		$doc = get_post();
		if ( ! empty( $doc->ID ) ) {
			$doc_id = $doc->ID;
		}
	}

	if ( empty( $doc_id ) ) {
		return array();
	}

	$atts_args = apply_filters( 'bp_docs_get_doc_attachments_args', array(
		'post_type' => 'attachment',
		'post_parent' => $doc_id,
		'update_post_meta_cache' => false,
		'update_post_term_cache' => false,
		'posts_per_page' => -1,
	), $doc_id );

	$atts = get_posts( $atts_args );

	return apply_filters( 'bp_docs_get_doc_attachments', $atts, $doc_id );
}

/**
 * Get the URL for an attachment download.
 *
 * Is sensitive to whether Docs can be directly downloaded.
 *
 * @param int $attachment_id
 */
function bp_docs_attachment_url( $attachment_id ) {
	echo bp_docs_get_attachment_url( $attachment_id );
}
	/**
	 * Get the URL for an attachment download.
	 *
	 * Is sensitive to whether Docs can be directly downloaded.
	 *
	 * @param int $attachment_id
	 */
	function bp_docs_get_attachment_url( $attachment_id ) {
		$attachment = get_post( $attachment_id );

		if ( bp_docs_attachment_protection() ) {
			$attachment = get_post( $attachment_id );
			$att_base   = basename( get_attached_file( $attachment_id ) );
			$doc_url    = bp_docs_get_doc_link( $attachment->post_parent );
			$att_url    = add_query_arg( 'bp-attachment', $att_base, $doc_url );
		} else {
			$att_url = wp_get_attachment_url( $attachment_id );
		}

		// Backward compatibility: fix IIS URLs that were broken by a
		// previous implementation
		$att_url = preg_replace( '|bp\-attachments([0-9])|', 'bp-attachments/$1', $att_url );

		return apply_filters( 'bp_docs_attachment_url_base', $att_url, $attachment );
	}


// @todo make <li> optional?
function bp_docs_attachment_item_markup( $attachment_id, $format = 'full' ) {
	$markup = '';

	$att_url    = bp_docs_get_attachment_url( $attachment_id );

	$attachment = get_post( $attachment_id );
	$att_base   = basename( get_attached_file( $attachment_id ) );
	$doc_url    = bp_docs_get_doc_link( $attachment->post_parent );

	$attachment_ext = preg_replace( '/^.+?\.([^.]+)$/', '$1', $att_url );

	if ( 'full' === $format ) {
		$attachment_delete_html = '';
		if ( current_user_can( 'bp_docs_edit' ) && ( bp_docs_is_doc_edit() || bp_docs_is_doc_create() ) ) {
			$attachment_delete_url = wp_nonce_url( $doc_url, 'bp_docs_delete_attachment_' . $attachment_id );
			$attachment_delete_url = add_query_arg( array(
				'delete_attachment' => $attachment_id,
			), $attachment_delete_url );
			$attachment_delete_html = sprintf(
				'<a href="%s" class="doc-attachment-delete confirm button">%s</a> ',
				$attachment_delete_url,
				__( 'Delete', 'buddypress' )
			);
		}

		$markup = sprintf(
			'<li id="doc-attachment-%d"><span class="doc-attachment-mime-icon doc-attachment-mime-%s"></span><a href="%s" title="%s">%s</a>%s</li>',
			$attachment_id,
			$attachment_ext,
			$att_url,
			esc_attr( $att_base ),
			esc_html( $att_base ),
			$attachment_delete_html
		);
	} else {
		$markup = sprintf(
			'<li id="doc-attachment-%d"><span class="doc-attachment-mime-icon doc-attachment-mime-%s"></span><a href="%s" title="%s">%s</a></li>',
			$attachment_id,
			$attachment_ext,
			$att_url,
			esc_attr( $att_base ),
			esc_html( $att_base )
		);
	}

	return $markup;
}

/**
 * Does this doc have attachments?
 *
 * @since 1.4
 * @return bool
 */
function bp_docs_doc_has_attachments( $doc_id = null ) {
	if ( is_null( $doc_id ) ) {
		$doc_id = get_the_ID();
	}

	$atts = bp_docs_get_doc_attachments( $doc_id );

	return ! empty( $atts );
}

/**
 * Gets the markup for the paperclip icon in directories
 *
 * @since 1.4
 */
function bp_docs_attachment_icon() {
	$atts = bp_docs_get_doc_attachments( get_the_ID() );

	if ( empty( $atts ) ) {
		return;
	}

	// $pc = plugins_url( BP_DOCS_PLUGIN_SLUG . '/includes/images/paperclip.png' );

	$html = '<a class="bp-docs-attachment-clip paperclip-jaunty" id="bp-docs-attachment-clip-' . get_the_ID() . '"></a>';

	echo $html;
}

/**
 * Builds the markup for the attachment drawer in directories
 *
 * @since 1.4
 */
function bp_docs_doc_attachment_drawer() {
	$atts = bp_docs_get_doc_attachments( get_the_ID() );
	$html = '';

	if ( ! empty( $atts ) ) {
		$html .= '<ul>';
		$html .= '<h4>' . __( 'Attachments', 'bp-docs' ) . '</h4>';

		foreach ( $atts as $att ) {
			$html .= bp_docs_attachment_item_markup( $att->ID, 'simple' );
		}

		$html .= '</ul>';
	}

	echo $html;
}

/**
 * Add classes to a row in the document list table.
 *
 * Currently supports: bp-doc-trashed-doc
 *
 * @since 1.5.5
 */
function bp_docs_doc_row_classes() {
	$classes = array();

	if ( get_post_status( get_the_ID() ) == 'trash' ) {
		$classes[] = 'bp-doc-trashed-doc';
	}

	// Pass the classes out as an array for easy unsetting or adding new elements
	$classes = apply_filters( 'bp_docs_doc_row_classes', $classes );

	if ( ! empty( $classes ) ) {
		$classes = implode( ' ', $classes );
		echo ' class="' . esc_attr( $classes ) . '"';
	}
}

/**
 * Add "Trash" notice next to deleted Docs.
 *
 * @since 1.5.5
 */
function bp_docs_doc_trash_notice() {
	if ( get_post_status( get_the_ID() ) == 'trash' ) {
		echo ' <span title="' . __( 'This Doc is in the Trash', 'bp-docs' ) . '" class="bp-docs-trashed-doc-notice">' . __( 'Trash', 'bp-docs' ) . '</span>';
	}
}

/**
 * Is the given Doc trashed?
 *
 * @since 1.5.5
 *
 * @param int $doc_id Optional. ID of the doc. Default: current doc.
 * @return bool True if doc is trashed, otherwise false.
 */
function bp_docs_is_doc_trashed( $doc_id = false ) {
	if ( ! $doc_id ) {
		$doc = get_queried_object();
	} else {
		$doc = get_post( $doc_id );
	}

	return isset( $doc->post_status ) && 'trash' == $doc->post_status;
}

/**
 * Output 'toggle-open' or 'toggle-closed' class for toggleable div.
 *
 * @since 1.8
 */
function bp_docs_toggleable_open_or_closed_class() {
	if ( bp_docs_is_doc_create() ) {
		echo 'toggle-open';
	} else {
		echo 'toggle-closed';
	}
}
