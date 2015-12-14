<?php
/**
 * BP-Default theme functions and definitions
 *
 * Sets up the theme and provides some helper functions. Some helper functions
 * are used in the theme as custom template tags. Others are attached to action and
 * filter hooks in WordPress and BuddyPress to change core functionality.
 *
 * The first function, status_setup(), sets up the theme by registering support
 * for various features in WordPress and BuddyPress, such as post formats, theme
 * compatibility, and enqueues this theme's JavaScripts.
 *
 * Functions that are not pluggable (not wrapped in function_exists()) are instead attached
 * to a filter or action hook. The hook can be removed by using remove_action() or
 * remove_filter() and you can attach your own function to the hook.
 *
 * For more information on hooks, actions, and filters, see http://codex.wordpress.org/Plugin_API.
 *
 * @package Status
 * @since 1.0
 */
 // Exit if accessed directly
 if ( ! defined( 'ABSPATH' ) ) exit;

 // If BuddyPress is not activated, switch back to the default WP theme
 if ( ! defined( 'BP_VERSION' ) )
 	switch_theme( WP_DEFAULT_THEME, WP_DEFAULT_THEME );

 // Bail out if enough of BuddyPress isn't loaded
 if ( ! function_exists( 'bp_is_active' ) )
 	return;

if ( !function_exists( 'status_setup' ) ) :
/**
 * Sets up theme defaults and registers support for various WordPress and BuddyPress features.
 *
 * Note that this function is hooked into the after_setup_theme hook, which runs
 * before the init hook. The init hook is too late for some features, such as indicating
 * support post thumbnails.
 *
 * @since 1.0
 */
function status_setup() {
	// We're not using BP-Default's primary nav menu, or custom header images.
		unregister_nav_menu( 'primary' );

		remove_theme_support( 'custom-header');

		// Enqueue javascript
		add_action( 'wp_enqueue_scripts', 'status_enqueue_scripts' );

		// Enqueue scripts
		add_action( 'wp_enqueue_scripts', 'bp_dtheme_enqueue_styles' );

		// This theme uses wp_nav_menu() in one location.
		register_nav_menus( array(
			'admin_bar_nav' => __( 'Admin Bar Custom Navigation Menu', 'status' ),
		) );

		// Add theme support for post formats
		add_theme_support( 'post-formats', array( 'aside', 'gallery', 'link', 'video', 'image', 'quote', 'status', 'chat' ) );

		// This theme comes with all the BuddyPress goodies
		add_theme_support( 'buddypress' );
}
add_action( 'after_setup_theme', 'status_setup' );
endif;

if ( ! function_exists( 'bp_dtheme_enqueue_styles()' ) ) :
/**
 * Enqueue theme CSS safely
 *
 * @see http://codex.wordpress.org/Function_Reference/wp_enqueue_style
 * @since 1.0
 */
function bp_dtheme_enqueue_styles(){
	if (!is_admin()){
	$version = '20120303';
	wp_enqueue_style( 'status',  get_stylesheet_directory_uri() . '/_inc/css/status.css', array(), $version );
	}
}
endif;

if ( ! function_exists( 'status_enqueue_scripts' ) ) :
/**
 * Enqueue theme javascript safely
 *
 * @see http://codex.wordpress.org/Function_Reference/wp_enqueue_script
 * @since 1.0
 */
function status_enqueue_scripts() {
	if ( is_admin() )
			return;

		$version = '20120303'; 
		wp_enqueue_script( 'modernizr',      get_stylesheet_directory_uri() . '/_inc/scripts/modernizr.js',      array( 'jquery' ), $version );
		wp_enqueue_script( 'status-scripts', get_stylesheet_directory_uri() . '/_inc/scripts/status-scripts.js', array( 'jquery' ), $version );

		// Only load comment-reply javascript if we're on a single post and threaded comments has been enabled
		if ( is_singular() && get_option( 'thread_comments' ) && comments_open() )
			wp_enqueue_script( 'comment-reply' );
}
endif;

/**
 * Register the admin bar menu if the Toolbar is enabled and current user
 * is a bona fide super admin (with cape).
 *
 * @since 1.0
 */
add_action('admin_bar_init', 'status_adminbar_menu_init');
function status_adminbar_menu_init() {
	if (!is_admin_bar_showing() )
		return;
 	add_action( 'admin_bar_menu', 'status_admin_bar_menu', 1000 );
}

/**
 * Register our admin bar extension menu
 *
 * @global obj $wp_admin_bar
 * @since 1.0
 */
function status_admin_bar_menu() {
	global $wp_admin_bar;

		$menu_name = 'admin_bar_nav';
		$locations = get_nav_menu_locations();

		// Bail out if this nav menu hasn't been set up
		if ( ! isset( $locations[ $menu_name ] ) )
			return;

		$menu       = wp_get_nav_menu_object( $locations[ $menu_name ] );
		$menu_items = wp_get_nav_menu_items( $menu->term_id );

		// If the menu has no items, don't do anything
		if ( ! $menu_items )
			return;

		// Add menu
		$wp_admin_bar->add_menu( array(
			'href'  => '#',
			'id'    => 'status-admin-menu-0',
			'title' => 'Navigation',
		) );

		// Add menu items
		foreach ( $menu_items as $menu_item ) {
			$wp_admin_bar->add_menu( array(
				'href'   => $menu_item->url,
				'id'     => 'status-admin-menu-' . $menu_item->ID,
				'parent' => 'status-admin-menu-' . $menu_item->menu_item_parent,
				'meta'   => array(
					'title'  => $menu_item->attr_title,
					'target' => $menu_item->target,
					'class'  => implode( ' ', $menu_item->classes ),
				),
				'title'  => $menu_item->title,
			) );
		}
}

/**
 * Register widget areas.
 *
 * @since 1.0
 */
function status_widgets_init() {
	register_sidebar( array(
			'name'          => __( 'Blog sidebar', 'status' ),
			'id'            => 'sidebar',
			'description'   => __( 'The sidebar widget area', 'status' ),
			'before_widget' => '<aside id="%1$s" class="widget %2$s">',
			'after_widget'  => '</aside>',
			'before_title'  => '<h3 class="widgettitle">',
			'after_title'   => '</h3>',
		) );

	register_sidebar( array(
			'name'          => __( 'BuddyPress sidebar', 'status' ),
			'id'            => 'sidebar-buddypress',
			'description'   => __( 'The sidebar widget area', 'status' ),
			'before_widget' => '<aside id="%1$s" class="widget %2$s">',
			'after_widget'  => '</aside>',
			'before_title'  => '<h3 class="widgettitle">',
			'after_title'   => '</h3>',
	) );
}
add_action( 'widgets_init', 'status_widgets_init' );

/**
 * Template for comments and pingbacks.
 *
 * Used as a callback by wp_list_comments() for displaying the comments.
 *
 * @param mixed $comment Comment record from database
 * @param array $args Arguments from wp_list_comments() call
 * @param int $depth Comment nesting level
 * @see wp_list_comments()
 * @since 1.0
 */
function status_blog_comments( $comment, $args, $depth ) {
	$GLOBALS['comment'] = $comment;

	if ( 'pingback' == $comment->comment_type )
		return false;

	if ( 1 == $depth )
		// Use smaller avatars the deeper the comment depth
		$avatar_size = 50;
	else
		$avatar_size = 25;
	?>

	<li <?php comment_class() ?> id="comment-<?php comment_ID() ?>">
		<article id="comment-<?php comment_ID(); ?>" class="comment">
			<section class="comment-meta">
				<div class="comment-avatar-box">
					<div class="avb">
						<a href="<?php echo get_comment_author_url() ?>" rel="nofollow">
							<?php if ( $comment->user_id ) : ?>
								<?php echo bp_core_fetch_avatar( array( 'item_id' => $comment->user_id, 'width' => $avatar_size, 'height' => $avatar_size, 'email' => $comment->comment_author_email ) ) ?>
							<?php else : ?>
								<?php echo get_avatar( $comment, $avatar_size ) ?>
							<?php endif; ?>
						</a>
					</div>
				</div>
			</section>

			<section class="comment-content">
				<div class="comment-meta">
					<p>
						<?php
							/* translators: 1: comment author url, 2: comment author name, 3: comment permalink, 4: comment date/timestamp*/
							printf( __( '<a href="%1$s" rel="nofollow">%2$s</a> said on <a href="%3$s"><span class="time-since">%4$s</span></a>', 'status' ), get_comment_author_url(), get_comment_author(), get_comment_link(), get_comment_date() );
						?>
					</p>
				</div>

				<div class="comment-entry">
					<?php if ( $comment->comment_approved == '0' ) : ?>
					 	<em class="moderate"><?php _e( 'Your comment is awaiting moderation.', 'status' ); ?></em>
					<?php endif; ?>

					<?php comment_text() ?>
				</div>

				<div class="comment-options">
						<?php if ( comments_open() ) : ?>
							<?php comment_reply_link( array( 'depth' => $depth, 'max_depth' => $args['max_depth'] ) ); ?>
						<?php endif; ?>

						<?php if ( current_user_can( 'edit_comment', $comment->comment_ID ) ) : ?>
							<?php printf( '<a class="button comment-edit-link bp-secondary-action" href="%1$s" title="%2$s">%3$s</a> ', get_edit_comment_link( $comment->comment_ID ), esc_attr__( 'Edit comment', 'status' ), __( 'Edit', 'status' ) ) ?>
						<?php endif; ?>

				</div>
			</section>

		</article>
<?php
}

/**
 * Shows friends in sidebar
 *
 * @since 1.0
 */
function status_showfriends() {
	$user = bp_loggedin_user_id();
	if( is_user_logged_in() ) : 
		if( bp_has_members('user_id=' . $user . '&search_terms=') && $user !== 0 ) : ?>

						<ul id="friends-list" class="looplist">
						<?php	while ( bp_members() ) : bp_the_member(); ?>
							<li>
								<div class="item-avatar"><a href="<?php bp_member_permalink() ?>"><?php  bp_member_avatar('type=full&width=30&height=30') ?></a></div>
							</li>				 

						<?php endwhile; ?>
						</ul>

	<?php else: ?>

		<p><?php _e( 'Once you friend someone it will show up here.', 'status' ) ?></p>

	<?php endif;

	endif;
}


/**
 * User specific functions - sets background image
 *
 * @since 1.0
 */
function status_get_background_image () {
	return (array)get_user_meta(bp_displayed_user_id(), 'status_design_bg_image', true);
}

/**
 * User specific functions - sets user settings
 *
 * @since 1.0
 */
function status_get_user_options () {
	$options = (array)get_user_meta(bp_displayed_user_id(), 'status_design_options', true);
	$bg_image = status_get_background_image();

	return wp_parse_args($options, array(
		'bg-position' => 'left',
		'bg-repeat' => 'repeat',
		'bg-attachment' => 'fixed',
		'bg-color' => false,
		'bg-image' => $bg_image,
		'link-color' => false,
	));
}

/**
 * User specific functions - saves user settings
 *
 * @since 1.0
 */
function status_save_changes () {
	if (empty($_POST)) return false;
	if (!isset($_POST['status_design'])) return false;

	$nonce = isset($_POST['_nonce']) ? $_POST['_nonce'] : false;
	if (!$nonce) return false;
	if (!wp_verify_nonce($nonce, 'status-design_form')) return false;

	global $bp;
	$user_id = ( current_user_can('administrator') && ! bp_is_my_profile() ) ? bp_displayed_user_id() : bp_current_user_id();
	if (!$user_id) return false;
	
	// Resetting options to default
	if (isset($_POST['status-reset_to_default'])) {
		update_user_meta($user_id, 'status_design_options', false);
		$old = status_get_background_image();
		if ($old['file'] && file_exists($old['file'])) @unlink($old['file']);
		update_user_meta($user_id, 'status_design_bg_image', array());
		wp_redirect(bp_core_get_user_domain($user_id) . $bp->profile->slug . '/design'); die;
	}

	$update = array();
	foreach ($_POST['status_design'] as $key => $value) {
		$update[$key] = sanitize_text_field($value); // Potential fix for issue #119
	}
	if (!function_exists('sanitize_hex_color_no_hash') && file_exists(ABSPATH . WPINC . '/class-wp-customize-manager.php')) {
		require_once(ABSPATH . WPINC . '/class-wp-customize-manager.php');
	}
	if (function_exists('sanitize_hex_color_no_hash')) {
		$sane_bg = sanitize_hex_color_no_hash(ltrim($update['bg-color'], '#'));
		$update['bg-color'] = $sane_bg ? "#{$sane_bg}" : '';
		$sane_link = sanitize_hex_color_no_hash(ltrim($update['link-color'], '#'));
		$update['link-color'] = $sane_link ? "#{$sane_link}" : '';
	} else {
		$update['bg-color'] = preg_match('/^([0-9a-f]{3}){1,2}$/i', ltrim($update['bg-color'], '#')) 
			? '#' . ltrim($update['bg-color'], '#') 
			: ''
		;
		$update['link-color'] = preg_match('/^([0-9a-f]{3}){1,2}$/i', ltrim($update['link-color'], '#')) 
			? '#' . ltrim($update['link-color'], '#') 
			: ''
		;
	}
	if (isset($_POST['_status_design-remove_background'])) {
		$old = status_get_background_image();
		if ($old['file'] && file_exists($old['file'])) @unlink($old['file']);
		update_user_meta($user_id, 'status_design_bg_image', array());
	}
	status_handle_image_upload();
	if ($update) {
		update_user_meta($user_id, 'status_design_options', $update);
		wp_redirect(bp_core_get_user_domain($user_id) . $bp->profile->slug . '/design'); die;
	}
}

/**
 * User specific functions - uploads background image
 *
 * @since 1.0
 */
function status_handle_image_upload () {
	$user_id = bp_current_user_id();
	if (!$user_id) return false;

	$old = status_get_background_image();

	// Determine if we have an upload
	if (!isset($_FILES['status_design-bg_image'])) return false;
	if (
		(!empty( $_FILES['status_design-bg_image']['type'] ) && !preg_match('/(jpe?g|gif|png)$/i', $_FILES['status_design-bg_image']['type'])) 
		|| 
		!preg_match( '/(jpe?g|gif|png)$/i', $_FILES['status_design-bg_image']['name']) 
	) {
		//xprofile_delete_field_data($_present['status-background_image'], $user_id);
		return false;
	}

	if (!function_exists('wp_handle_upload')) @require_once(ABSPATH . 'wp-admin/includes/file.php');
	$result = wp_handle_upload($_FILES['status_design-bg_image'], array('test_form'=>false));
	if (isset($result['error'])) return false;
	$result['file'] = str_replace('\\', '/', $result['file']);

	update_user_meta($user_id, 'status_design_bg_image', $result);

	if ($old['file'] && $result['file'] != $old['file'] && file_exists($old['file'])) {
		@unlink($old['file']);
	}
}


/**
 * User specific functions - sets user customisation
 *
 * @since 1.0
 */
function status_apply_custom_user_settings () {
	$_positions = array(
		'left',
		'right', 
		'center',
	);
	$_repeats = array(
		'no-repeat',
		'repeat',
		'repeat-x',
		'repeat-y',
	);
	$_attachments = array(
		'scroll',
		'fixed',
	);

	$options = status_get_user_options();

	$color = $options['link-color'] ? $options['link-color'] : false;
	$bg_image = $options['bg-image'] && isset($options['bg-image']['url']) ? $options['bg-image'] : array("url" => get_background_image()); // Fix for issue #104
	$bg_color = $options['bg-color'] ? $options['bg-color'] : "#" . get_theme_mod("background_color", (defined('BACKGROUND') ? BACKGROUND : 'ffffff'));


	$position = $options['bg-position'];
	$position = ($position && in_array($position, $_positions)) ? $position : get_theme_mod('background_position_x', 'left');

	$repeat = $options['bg-repeat'];
	$repeat = ($repeat && in_array($repeat, $_repeats)) ? $repeat : get_theme_mod('background_repeat', 'repeat');

	$attachment = $options['bg-attachment'];
	$attachment = ($attachment && in_array($attachment, $_attachments)) ? strtolower($attachment) : get_theme_mod('background_attachment', 'scroll');

	$image = ($bg_image && isset($bg_image['url']) && $bg_image['url']) 
		? "background-image: url({$bg_image['url']});" 
		: '' // Use whatever is in the stylesheet
	;
	$background_color = ($bg_color && '#' != $bg_color) ? "background-color: {$bg_color};" : '';
	if ($image) {
		$position = ($position) ? "background-position: top {$position};" : '';
		$repeat = ($repeat) ? "background-repeat: {$repeat};" : '';
		$attachment = ($attachment) ? "background-attachment: {$attachment};" : '';
	} else {
		if ($background_color) $image = 'background-image: none;';
		$position = $repeat = $attachment = '';
	}

	echo '<style type="text/css">' .
		($color ? "a, a:link, a:visited, a:hover { color: {$color}; } " : '') .
		"body {	{$image} {$position} {$repeat} {$attachment} {$background_color} }" .
	'</style>';
}

/**
 * User specific functions - sets background image handling
 *
 * @since 1.0
 */
function status_setup_user_theme () {
	//add_custom_background('status_apply_custom_user_settings', '');
	if (bp_get_major_wp_version() >= 3.4) {
		// Not sure if "wp-head-callback" doesn't work what it says on the tin,
		// or if there's some other thing wrong...
		add_theme_support('custom-background', array(
			'wp-head-callback' => 'status_apply_custom_user_settings',
		));
		// Either way, it's not crucial
		add_action('wp_head', 'status_apply_custom_user_settings', 999); // Callback parameter doesn't seem to be called, for some reason...
	} else {
		add_custom_background('status_apply_custom_user_settings', '');
	}
}
add_action('after_setup_theme', 'status_setup_user_theme', 20);

/**
 * User specific functions - gets design group name
 *
 * @since 1.0
 */
function status_get_design_group_name () {
	return __('Design', 'status');
}

/**
 * User specific functions - puts design link in menu
 *
 * @since 1.0
 */
function status_design_group_url_setup () {
	global $bp;
	if ( !current_user_can('administrator') && ! bp_is_my_profile() ) return false;

	$name = status_get_design_group_name();
	$slug = 'design';
	bp_core_new_subnav_item(array(
		'name' => $name,
		'slug' => $slug,
		'parent_url' => bp_loggedin_user_domain() . $bp->profile->slug . '/',
		'parent_slug' => $bp->profile->slug,
		'screen_function' => 'status_dispatch_page_handlers',
		'position' => 40 
	));
}
add_action('bp_init', 'status_design_group_url_setup');

/**
 * User specific functions - customisation handlers
 *
 * @since 1.0
 */
function status_dispatch_page_handlers () {
	status_save_changes();
	add_action('bp_template_title', 'status_show_design_title');
	add_action('bp_template_content',  'status_show_design_body');
	add_action('bp_head', 'status_design_dependencies');
	bp_core_load_template(apply_filters('bp_core_template_plugin', 'members/single/plugins'));
}

/**
 * User specific functions - sets design title
 * @since 1.0
 */
function status_show_design_title () {
	echo  status_get_design_group_name() ;
}

/**
 * User specific functions - shows design options
 *
 * @since 1.0
 */
function status_show_design_body () {
	$options = status_get_user_options();

	echo '<form action="" method="POST" enctype="multipart/form-data" >';

	echo '<input type="hidden" name="_nonce" value="' . esc_attr(wp_create_nonce('status-design_form')) . '" />';

	echo '<p>' .
		'<label for="status-design-link_color">' . _e('Link color', 'status') . '</label>' .
		'<input type="text" size="7" id="status-design-link_color" name="status_design[link-color]" value="' . $options['link-color'] . '" />' .
	'</p>';

	if (current_user_can('upload_files')) { // Fix for issue #85
		echo '<p id="status-design-background_image-wrapper">' .
			'<label for="status_design-bg_image">' . _e('Background image', 'status') . '</label>' .
			'<input type="file" id="status_design-bg_image" name="status_design-bg_image" />' .
			(
				! empty( $options['bg-image']['url'] ) ? '<div><img src="' . $options['bg-image']['url'] . '" />' .
					'<br /><a href="#remove-background" id="status-design-remove_background">' . __('Remove background image', 'status') . '</a></div>'
				: ''
			) .
		'</p>';

		echo '<p>' .
			'<label for="status-design-position-left">' . _e('Background position', 'status') . '</label>' .
				'<label for="status-design-position-left"><input type="radio" id="status-design-position-left" name="status_design[bg-position]" ' . (("left" == $options['bg-position']) ? 'checked="checked"' : '') . ' value="left" /> Left</label> ' .
				'<label for="status-design-position-right"><input type="radio" id="status-design-position-right" name="status_design[bg-position]" ' . (("right" == $options['bg-position']) ? 'checked="checked"' : '') . ' value="right" /> Right</label> ' .
				'<label for="status-design-position-center"><input type="radio" id="status-design-position-center" name="status_design[bg-position]" ' . (("center" == $options['bg-position']) ? 'checked="checked"' : '') . ' value="center" /> Center</label> ' .
		'</p>';

		echo '<p>' .
			'<label for="status-design-repeat-no">' . _e('Background repeat', 'status') . '</label>' .
				'<label for="status-design-repeat-no"><input type="radio" id="status-design-repeat-no" name="status_design[bg-repeat]" ' . (("no-repeat" == $options['bg-repeat']) ? 'checked="checked"' : '') . ' value="no-repeat" /> None</label> ' .
				'<label for="status-design-repeat-repeat"><input type="radio" id="status-design-repeat-repeat" name="status_design[bg-repeat]" ' . (("repeat" == $options['bg-repeat']) ? 'checked="checked"' : '') . ' value="repeat" /> Tile</label> ' .
				'<label for="status-design-repeat-x"><input type="radio" id="status-design-repeat-x" name="status_design[bg-repeat]" ' . (("repeat-x" == $options['bg-repeat']) ? 'checked="checked"' : '') . ' value="repeat-x" /> Tile horizontally</label> ' .
				'<label for="status-design-repeat-y"><input type="radio" id="status-design-repeat-y" name="status_design[bg-repeat]" ' . (("repeat-y" == $options['bg-repeat']) ? 'checked="checked"' : '') . ' value="repeat-y" /> Tile vertically</label> ' .
		'</p>';

		echo '<p>' .
			'<label for="status-design-attachment-fixed">' . _e('Background attachment', 'status') . '</label>' .
				'<label for="status-design-attachment-scroll"><input type="radio" id="status-design-attachment-scroll" name="status_design[bg-attachment]" ' . (("scroll" == $options['bg-attachment']) ? 'checked="checked"' : '') . ' value="scroll" /> Scroll</label> ' .
				'<label for="status-design-attachment-fixed"><input type="radio" id="status-design-attachment-fixed" name="status_design[bg-attachment]" ' . (("fixed" == $options['bg-attachment']) ? 'checked="checked"' : '') . ' value="fixed" /> Fixed</label> ' .
		'</p>';
	}

	echo '<p>' .
		'<label for="status-design-background_color">' . _e('Background color', 'status') . '</label>' .
		'<input type="text" size="7" id="status-design-background_color" name="status_design[bg-color]" value="' . $options['bg-color'] . '" />' .
	'</p>';

	echo '<p>' .
		'<input type="submit" class="button button-primary" value="' . esc_attr(__('Save', 'status')) . '" />' .
		' <input type="submit" class="button" name="status-reset_to_default" value="' . esc_attr(__('Reset to default', 'status')) . '" />' .
	'</p>';

	echo '</form>';

	add_action('wp_footer', 'status_custom_javascript');
}

/**
 * User specific functions - includes scripts for customisation
 *
 * @since 1.0
 */
function status_design_dependencies () {
	wp_enqueue_style("farbtastic");
	wp_enqueue_script("jquery");
	wp_enqueue_script("farbtastic", admin_url("js/farbtastic.js"), array("jquery"));
	add_thickbox();
}

/**
 * User specific functions - includes custom profile javascript
 *
 * @since 1.0
 */
function status_custom_javascript () {
	include_once( dirname( __FILE__ ) . '/_inc/scripts/status-custom-profile.js' );
}

/**
 * User statistics function - gets activity count
 *
 * @since 1.0
 */
function status_user_activity_count($action) {
	$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_current_user_id();
	$args = array('user_id'=>$user_id,'action'=>$action);
	$user_activity = bp_activity_get (array( 'filter' => $args ));

	return $user_activity['total'];
}

/**
 * User statistics function - creates stats
 *
 * @since 1.0
 */
function status_member_profile_stats_member_status() {
	echo status_member_profile_stats_get_member_status();
}

/**
 * User statistics function - gets member status
 *
 * @since 1.0
 */
function status_member_profile_stats_get_member_status() {
		if ( !bp_is_active( 'activity' ) )
		return;

		$total_count = status_user_activity_count($action = 'activity_update' );

		if ( $total_count == 0 ) {
			$content = '<li>' . __( ' No updates', 'status' ) . '</li>';
		} else if ( $total_count == 1 ) {
			$content = '<li><span class="status-count">'. $total_count .'</span>' . __( ' update', 'status' ) . '</li>';
		} else {
			$content = '<li><span class="status-count">'. $total_count .'</span>' . __( ' updates', 'status' ) . '</li>';
		}

		return apply_filters( 'status_member_profile_stats_get_member_status', $content, $total_count );
}

/**
 * User statistics function - gets member topics
 *
 * @since 1.0
 */
function status_member_profile_stats_member_topics() {
	echo status_member_profile_stats_get_member_topics();
}

/**
 * User statistics function - gets member topics
 *
 * @since 1.0
 */
function status_member_profile_stats_get_member_topics() {
		if ( !bp_is_active( 'forums' ) )
			return;

		$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();
		$total_count = bp_forums_total_topic_count_for_user($user_id);

		if ( $total_count == 0 ) {
			$content = '<li>' . __( ' No forum topics', 'status' ) . '</li>';
		} else if ( $total_count == 1 ) {
			$content = '<li><span class="topic-count">'. $total_count .'</span>' . __( ' forum topic', 'status' ) . '</li>';
		} else {
			$content = '<li><span class="topic-count">'. $total_count .'</span>' . __( ' forum topics', 'status' ) . '</li>';
		}

		return apply_filters( 'status_member_profile_stats_get_member_posts', $content, $total_count );
}

/**
 * User statistics function - gets member replies
 *
 * @since 1.0
 */
function status_member_profile_stats_member_replies() {
	echo status_member_profile_stats_get_member_replies();
}

/**
 * User statistics function - gets member replies
 *
 * @since 1.0
 */
function status_member_profile_stats_get_member_replies() {
		if ( !bp_is_active( 'forums' ) )
			return;
		$total_count = status_user_activity_count($action = 'new_forum_post' );

		if ( $total_count == 0 ) {
			$content = '<li>' . __( ' No forum replies', 'status' ) . '</li>';
		} else if ( $total_count == 1 ) {
			$content = '<li><span class="topic-replies">'. $total_count .'</span>' . __( ' forum reply', 'status' ) . '</li>';
		} else {
			$content = '<li><span class="topic-replies">'. $total_count .'</span>' . __( ' forum replies', 'status' ) . '</li>';
		}

		return apply_filters( 'status_member_profile_stats_get_member_topics', $content, $total_count );
}

/**
 * User statistics function - gets new blog posts
 *
 * @since 1.0
 */
function status_member_profile_stats_new_blog_post() {
	echo status_member_profile_stats_get_new_blog_post();
}

/**
 * User statistics function - gets new blog posts
 *
 * @since 1.0
 */
function status_member_profile_stats_get_new_blog_post() {

		$total_count = status_user_activity_count($action = 'new_blog_post' );

		if ( $total_count == 0 ) {
			return;
		} else if ( $total_count == 1 ) {
			$content = '<li><span class="blog-count">'. $total_count .'</span>' . __( ' blog post', 'status' ) . '</li>';
		} else {
			$content = '<li><span class="blog-count">'. $total_count .'</span>' . __( ' blog posts', 'status' ) . '</li>';
		}

		return apply_filters( 'status_member_profile_stats_get_new_blog_post', $content, $total_count );
}

/**
 * User statistics function - gets comment count
 *
 * @since 1.0
 */
function status_member_profile_stats_member_comments() {
	echo status_member_profile_stats_get_member_comments();
}

/**
 * User statistics function - gets comment count
 *
 * @since 1.0
 */
function status_member_profile_stats_get_member_comments() {

		$total_count = status_member_profile_stats_get_member_comment_count();

		if ( $total_count == 0 ) {
			$content = '<li>' . __( ' No blog comments', 'status' ) . '</li>';
		} else if ( $total_count == 1 ) {
			$content = '<li><span class="comment-count">'. $total_count .'</span>' . __( ' blog comment', 'status' ) . '</li>';
		} else {
			$content = '<li><span class="comment-count">'. $total_count .'</span>' . __( ' blog comments', 'status' ) . '</li>';
		}

		return apply_filters( 'status_member_profile_stats_get_member_comments', $content, $total_count );
}

/**
 * User statistics function - gets comment count
 *
 * @since 1.0
 */
function status_member_profile_stats_get_member_comment_count( $user_id = false ) {

	if ( !$user_id )
		$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();

		$args = array('status' => 'approve', 'user_id' => $user_id, 'count' => true);
		$total_count =  get_comments( $args );

	if ( !$total_count )
		$total_count == 0;

	return $total_count;
}

/**
 * User statistics function - displays stats
 *
 * @since 1.0
 */
function status_display_member_stats() {?>
	<div id="member-stats">
		<ul>
		<?php  status_member_profile_stats_member_status();   ?>
		<?php  status_member_profile_stats_member_topics();   ?>
		<?php	 status_member_profile_stats_member_replies();  ?>
		<?php  status_member_profile_stats_new_blog_post();   ?>
		<?php  status_member_profile_stats_member_comments(); ?>
		</ul>
	</div>
<?php
}
add_action('status_stats', 'status_display_member_stats');

/**
 * Adds link to go along with permlink being clickable
 *
 * @since 1.0
 */
function status_format_content($content) {
    global $post;
    if ( (is_home() || is_archive() ) && ( has_post_format( 'aside' ) ) )
{ 
        $content .= '   &#8734;';
    }
    return $content;
}

add_filter('the_content','status_format_content',1);
?>