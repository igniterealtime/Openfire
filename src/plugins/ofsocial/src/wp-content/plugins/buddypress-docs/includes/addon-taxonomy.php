<?php

/**
 * This file contains the functions used to enable tags and categories for docs.
 * Separated into this file so that the feature can be turned off.
 *
 * @package BuddyPress Docs
 */

/**
 * This class sets up the interface and back-end functions needed to use post tags with BP Docs.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
class BP_Docs_Taxonomy {
	var $docs_tag_tax_name;

	var $taxonomies;
	var $current_filters;

	/**
	 * PHP 4 constructor
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function bp_docs_query() {
		$this->__construct();
	}

	/**
	 * PHP 5 constructor
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function __construct() {
		// Register our custom taxonomy
		add_filter( 'bp_docs_init', array( &$this, 'register_taxonomy' ), 11 );

		// Make sure that the bp_docs post type supports our post taxonomies
		add_filter( 'bp_docs_init', array( $this, 'register_with_post_type' ), 12 );

		// Hook into post saves to save any taxonomy terms.
		add_action( 'bp_docs_doc_saved', 	array( $this, 'save_post' ) );

		// Display a doc's terms on its single doc page
		add_action( 'bp_docs_single_doc_meta', 	array( $this, 'show_terms' ) );

		// Modify the main tax_query in the doc loop
		add_filter( 'bp_docs_tax_query', 	array( $this, 'modify_tax_query' ) );

		// Add the Tags column to the docs loop
		add_filter( 'bp_docs_loop_additional_th', array( $this, 'tags_th' ) );
		add_filter( 'bp_docs_loop_additional_td', array( $this, 'tags_td' ) );

		// Filter the message in the docs info header
		add_filter( 'bp_docs_info_header_message', array( $this, 'info_header_message' ), 10, 2 );

		// Add the tags filter markup
		add_filter( 'bp_docs_filter_types', array( $this, 'filter_type' ) );
		add_filter( 'bp_docs_filter_sections', array( $this, 'filter_markup' ) );

		// Adds filter arguments to a URL
		add_filter( 'bp_docs_handle_filters',	array( $this, 'handle_filters' ) );
	}

	/**
	 * Registers the custom taxonomy for BP doc tags
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 */
	function register_taxonomy() {
		global $bp;

		$this->docs_tag_tax_name = apply_filters( 'bp_docs_docs_tag_tax_name', 'bp_docs_tag' );
		$bp->bp_docs->docs_tag_tax_name = $this->docs_tag_tax_name;

		// Define the labels to be used by the taxonomy bp_docs_tag
		$doc_tags_labels = array(
			'name' 		=> __( 'Docs Tags', 'bp-docs' ),
			'singular_name' => __( 'Docs Tag', 'bp-docs' )
		);

		// Register the bp_docs_associated_item taxonomy
		register_taxonomy( $this->docs_tag_tax_name, array( $bp->bp_docs->post_type_name ), array(
			'labels' 	=> $doc_tags_labels,
			'hierarchical' 	=> false,
			'show_ui' 	=> true,
			'query_var' 	=> true,
			'rewrite' 	=> array( 'slug' => 'item' ),
		) );
	}

	/**
	 * Registers the post taxonomies with the bp_docs post type
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param array The $bp_docs_post_type_args array created in BP_Docs::register_post_type()
	 * @return array $args The modified parameters
	 */
	function register_with_post_type() {
		$this->taxonomies = array( /* 'category', */ $this->docs_tag_tax_name );

		foreach ( $this->taxonomies as $tax ) {
			register_taxonomy_for_object_type( $tax, bp_docs_get_post_type_name() );
		}
	}

	/**
	 * Saves post taxonomy terms to a doc when saved from the front end
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param object $query The query object created by BP_Docs_Query
	 * @return int $post_id Returns the doc's post_id on success
	 */
	function save_post( $query ) {
		foreach ( $this->taxonomies as $tax_name ) {

			if ( $tax_name == 'category' )
				$tax_name = 'post_category';

			// Separate out the terms
			$terms = !empty( $_POST[$tax_name] ) ? explode( ',', $_POST[$tax_name] ) : array();

			// Strip whitespace from the terms
			foreach ( $terms as $key => $term ) {
				$terms[$key] = trim( $term );
			}

			$tax = get_taxonomy( $tax_name );

			// Hierarchical terms like categories have to be handled differently, with
			// term IDs rather than the term names themselves
			if ( !empty( $tax->hierarchical ) ) {
				$term_ids = array();
				foreach( $terms as $term ) {
					$parent = 0;
					$term_ids[] = term_exists( $term, $tax_id, $parent );
				}
			}

			wp_set_post_terms( $query->doc_id, $terms, $tax_name );
		}

		do_action( 'bp_docs_taxonomy_saved', $query );
	}

	/**
	 * Handles taxonomy cleanup when a post is deleted
	 *
	 * No longer needed, because item taxonomies are generated dynamically
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param int $doc_id
	 */
	function delete_post( $new_status, $old_status, $post ) {
		return;
	}

	/**
	 * Shows a doc's taxonomy terms
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function show_terms() {
		foreach ( $this->taxonomies as $tax_name ) {
			$html    = '';
			$tagtext = array();
			$tags 	 = wp_get_post_terms( get_the_ID(), $tax_name );

			foreach ( $tags as $tag ) {
				$tagtext[] = bp_docs_get_tag_link( array( 'tag' => $tag->name ) );
			}

			if ( ! empty( $tagtext ) ) {
				$html = '<p>' . sprintf( __( 'Tags: %s', 'bp-docs' ), implode( ', ', $tagtext ) ) . '</p>';
			}

			echo apply_filters( 'bp_docs_taxonomy_show_terms', $html, $tagtext );
		}
	}

	/**
	 * Gets the list of terms used by an item's docs
	 *
	 * This is a dummy function that allows specific item types to hook in their own methods
	 * for retrieving metadata (groups_update_groupmeta(), get_user_meta(), etc)
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @return array $terms The item's terms
	 */
	function get_item_terms() {
		$terms = array();

		return apply_filters( 'bp_docs_taxonomy_get_item_terms', $terms );
	}

	/**
	 * Save list of terms used by an item's docs
	 *
	 * Just a dummy hook for the moment, for the integration modules to hook into
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @return array $terms The item's terms
	 */
	function save_item_terms( $terms ) {
		do_action( 'bp_docs_taxonomy_save_item_terms', $terms );
	}

	/**
	 * Modifies the tax_query on the doc loop to account for doc tags
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @return array $terms The item's terms
	 */
	function modify_tax_query( $tax_query ) {

		// Check for the existence tag filters in the request URL
		if ( ! empty( $_REQUEST['bpd_tag'] ) ) {
			// The bpd_tag argument may be comma-separated
			$tags = explode( ',', urldecode( $_REQUEST['bpd_tag'] ) );

			// Clean up the tag input
			foreach ( $tags as $key => $value ) {
				$tags[$key] = esc_attr( $value );
			}

			$tax_query[] = array(
				'taxonomy'	=> $this->docs_tag_tax_name,
				'terms'		=> $tags,
				'field'		=> 'slug',
			);

			if ( !empty( $_REQUEST['bool'] ) && $_REQUEST['bool'] == 'and' ) {
				$tax_query['operator'] = 'AND';
			}
		}

		return apply_filters( 'bp_docs_modify_tax_query_for_tax', $tax_query );
	}

	/**
	 * Markup for the Tags <th> on the docs loop
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */

	function tags_th() {
		?>

		<th scope="column" class="tags-cell"><?php _e( 'Tags', 'bp-docs' ); ?></th>

		<?php
	}

	/**
	 * Markup for the Tags <td> on the docs loop
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function tags_td() {

		$tags    = get_the_terms( get_the_ID(), $this->docs_tag_tax_name );
		$tagtext = array();

		foreach ( (array) $tags as $tag ) {
			if ( ! empty( $tag->name ) ) {
				$tagtext[] = bp_docs_get_tag_link( array( 'tag' => $tag->name ) );
			}
		}

		?>

		<td class="tags-cell">
			<?php echo implode( ', ', $tagtext ) ?>
		</td>

		<?php
	}

	/**
	 * Modifies the info header message to account for current tags
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 *
	 * @param array $message An array of the messages explaining the current view
	 * @param array $filters The filters pulled out of the $_REQUEST global
	 *
	 * @return array $message The maybe modified message array
	 */
	function info_header_message( $message, $filters ) {
		$this->current_filters = $filters;

		if ( ! empty( $filters['tags'] ) ) {
			$tagtext = array();

			foreach ( $filters['tags'] as $tag ) {
				$tagtext[] = bp_docs_get_tag_link( array( 'tag' => $tag ) );
			}

			$message[] = sprintf( __( 'You are viewing docs with the following tags: %s', 'bp-docs' ), implode( ', ', $tagtext ) );
		}

		return $message;
	}

	public function filter_type( $types ) {
		$types[] = array(
			'slug' => 'tags',
			'title' => __( 'Tag', 'bp-docs' ),
			'query_arg' => 'bpd_tag',
		);
		return $types;
	}

	/**
	 * Creates the markup for the tags filter checkboxes on the docs loop
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function filter_markup() {
		$existing_terms = $this->get_item_terms();

		$tag_filter = ! empty( $_GET['bpd_tag'] );

		?>

		<div id="docs-filter-section-tags" class="docs-filter-section<?php if ( $tag_filter ) : ?> docs-filter-section-open<?php endif ?>">
			<ul id="tags-list">
			<?php if ( ! empty( $existing_terms ) ) : ?>
				<?php foreach ( $existing_terms as $term => $posts ) : ?>
					<?php
					if ( isset( $posts['count'] ) ) {
						$term_count = $posts['count'];
					} else if ( is_int( $posts ) ) {
						$term_count = $posts;
					} else {
						$term_count = count( $posts );
					}

					if ( isset( $posts['name'] ) ) {
						$term_name = $posts['name'];
					} else {
						$term_name = $term;
					}

					?>
					<li>
					<a href="<?php echo bp_docs_get_tag_link( array( 'tag' => $term, 'type' => 'url' ) ) ?>" title="<?php echo esc_html( $term_name ) ?>"><?php echo esc_html( $term_name ) ?> <?php printf( __( '(%d)', 'bp-docs' ), $term_count ) ?></a>
					</li>

				<?php endforeach ?>
			<?php else: ?>
				<li><?php _e( 'No tags to show.', 'bp-docs' )  ?></li>
			<?php endif; ?>
			</ul>
		</div>

		<?php
	}

	/**
	 * Handles doc filters from a form post and translates to $_GET arguments before redirect
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta
	 */
	function handle_filters( $redirect_url ) {
		if ( ! empty( $_POST['filter_terms'] ) ) {
			$tags = array();

			foreach ( $_POST['filter_terms'] as $term => $value ) {
				$tags[] = urlencode( $term );
			}

			$tags = implode( ',', $tags );

			$redirect_url = add_query_arg( 'bpd_tag', $tags, $redirect_url );
		}

		return $redirect_url;
	}
}

/**************************
 * TEMPLATE TAGS
 **************************/

/**
 * Get an archive link for a given tag
 *
 * Optional arguments:
 *  - 'tag' 	The tag linked to. This one is required
 *  - 'type' 	'html' returns a link; anything else returns a URL
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param array $args Optional arguments
 * @return array $filters
 */
function bp_docs_get_tag_link( $args = array() ) {
	global $bp;

	$defaults = array(
		'tag' 	=> false,
		'type' 	=> 'html',
	);

	$r = wp_parse_args( $args, $defaults );
	extract( $r, EXTR_SKIP );

	if ( bp_is_user() ) {
		$current_action = bp_current_action();
		if ( empty( $current_action ) || BP_DOCS_STARTED_SLUG == $current_action ) {
			$item_docs_url = bp_docs_get_displayed_user_docs_started_link();
		} elseif ( BP_DOCS_EDITED_SLUG == $current_action ) {
			$item_docs_url = bp_docs_get_displayed_user_docs_edited_link();
		}
	} elseif ( bp_is_group() ){
		$item_docs_url = trailingslashit( bp_get_group_permalink() . bp_docs_get_docs_slug() );
	} else {
		$item_docs_url = bp_docs_get_archive_link();
	}

	$url = apply_filters( 'bp_docs_get_tag_link_url', add_query_arg( 'bpd_tag', urlencode( $tag ), $item_docs_url ), $args, $item_docs_url );

	if ( $type != 'html' )
		return apply_filters( 'bp_docs_get_tag_link_url', $url, $tag, $type );

	$html = '<a href="' . $url . '" title="' . sprintf( __( 'Docs tagged %s', 'bp-docs' ), esc_attr( $tag ) ) . '">' . esc_html( $tag ) . '</a>';

	return apply_filters( 'bp_docs_get_tag_link', $html, $url, $tag, $type );
}

/**
 * Display post tags form fields. Based on WP core's post_tags_meta_box()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param object $post
 */
function bp_docs_post_tags_meta_box() {
	global $bp;

	require_once(ABSPATH . '/wp-admin/includes/taxonomy.php');

	$defaults = array(
		'taxonomy' => $bp->bp_docs->docs_tag_tax_name,
	);

	if ( ! isset( $box['args'] ) || !is_array( $box['args'] ) )
		$args = array();
	else
		$args = $box['args'];

	extract( wp_parse_args( $args, $defaults ), EXTR_SKIP );

	$tax_name = esc_attr( $taxonomy );
	$taxonomy = get_taxonomy( $taxonomy );

	// If this is a failed submission, use the value from the POST cookie
	if ( ! empty( buddypress()->bp_docs->submitted_data->{$tax_name} ) ) {
		$terms = buddypress()->bp_docs->submitted_data->{$tax_name};

	// If it's an existing Doc, look up the terms
	} else if ( bp_docs_is_existing_doc() ) {
		$terms = get_terms_to_edit( get_the_ID(), $bp->bp_docs->docs_tag_tax_name );

	// Otherwise nothing to show
	} else {
		$terms = '';
	}
?>
	<textarea name="<?php echo "$tax_name"; ?>" class="the-tags" id="tax-input-<?php echo $tax_name; ?>"><?php echo esc_textarea( $terms ); ?></textarea>
<?php
}


/**
 * Display post categories form fields. Borrowed from WP. Not currently used.
 *
 * @since 1.0-beta
 *
 * @param object $post
 */
function bp_docs_post_categories_meta_box( $post ) {
	global $bp;

	require_once(ABSPATH . '/wp-admin/includes/template.php');

	$defaults = array('taxonomy' => 'category');
	if ( !isset($box['args']) || !is_array($box['args']) )
		$args = array();
	else
		$args = $box['args'];
	extract( wp_parse_args($args, $defaults), EXTR_SKIP );
	$tax = get_taxonomy($taxonomy);

	?>
	<div id="taxonomy-<?php echo $taxonomy; ?>" class="categorydiv">
		<ul id="<?php echo $taxonomy; ?>-tabs" class="category-tabs">
			<li class="tabs"><a href="#<?php echo $taxonomy; ?>-all" tabindex="3"><?php echo $tax->labels->all_items; ?></a></li>
			<li class="hide-if-no-js"><a href="#<?php echo $taxonomy; ?>-pop" tabindex="3"><?php _e( 'Most Used', 'bp-docs' ); ?></a></li>
		</ul>

		<div id="<?php echo $taxonomy; ?>-pop" class="tabs-panel" style="display: none;">
			<ul id="<?php echo $taxonomy; ?>checklist-pop" class="categorychecklist form-no-clear" >
				<?php $popular_ids = wp_popular_terms_checklist($taxonomy); ?>
			</ul>
		</div>

		<div id="<?php echo $taxonomy; ?>-all" class="tabs-panel">
			<?php
            $name = ( $taxonomy == 'category' ) ? 'post_category' : 'tax_input[' . $taxonomy . ']';
            echo "<input type='hidden' name='{$name}[]' value='0' />"; // Allows for an empty term set to be sent. 0 is an invalid Term ID and will be ignored by empty() checks.
            ?>
			<ul id="<?php echo $taxonomy; ?>checklist" class="list:<?php echo $taxonomy?> categorychecklist form-no-clear">
				<?php wp_terms_checklist($bp->bp_docs->current_post->ID, array( 'taxonomy' => $taxonomy, 'popular_cats' => $popular_ids ) ) ?>
			</ul>
		</div>
	<?php if ( current_user_can($tax->cap->edit_terms) ) : ?>
			<div id="<?php echo $taxonomy; ?>-adder" class="wp-hidden-children">
				<h4>
					<a id="<?php echo $taxonomy; ?>-add-toggle" href="#<?php echo $taxonomy; ?>-add" class="hide-if-no-js" tabindex="3">
						<?php
							/* translators: %s: add new taxonomy label */
							printf( __( '+ %s' ), $tax->labels->add_new_item );
						?>
					</a>
				</h4>
				<p id="<?php echo $taxonomy; ?>-add" class="category-add wp-hidden-child">
					<label class="screen-reader-text" for="new<?php echo $taxonomy; ?>"><?php echo $tax->labels->add_new_item; ?></label>
					<input type="text" name="new<?php echo $taxonomy; ?>" id="new<?php echo $taxonomy; ?>" class="form-required form-input-tip" value="<?php echo esc_attr( $tax->labels->new_item_name ); ?>" tabindex="3" aria-required="true"/>
					<label class="screen-reader-text" for="new<?php echo $taxonomy; ?>_parent">
						<?php echo $tax->labels->parent_item_colon; ?>
					</label>
					<?php wp_dropdown_categories( array( 'taxonomy' => $taxonomy, 'hide_empty' => 0, 'name' => 'new'.$taxonomy.'_parent', 'orderby' => 'name', 'hierarchical' => 1, 'show_option_none' => '&mdash; ' . $tax->labels->parent_item . ' &mdash;', 'tab_index' => 3 ) ); ?>
					<input type="button" id="<?php echo $taxonomy; ?>-add-submit" class="add:<?php echo $taxonomy ?>checklist:<?php echo $taxonomy ?>-add button category-add-sumbit" value="<?php echo esc_attr( $tax->labels->add_new_item ); ?>" tabindex="3" />
					<?php wp_nonce_field( 'add-'.$taxonomy, '_ajax_nonce-add-'.$taxonomy, false ); ?>
					<span id="<?php echo $taxonomy; ?>-ajax-response"></span>
				</p>
			</div>
		<?php endif; ?>
	</div>
	<?php
}

?>
