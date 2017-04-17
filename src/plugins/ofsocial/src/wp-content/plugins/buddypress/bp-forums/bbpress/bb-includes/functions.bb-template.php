<?php

function bb_load_template( $files, $globals = false, $action_arg = null )
{
	global $bb, $bbdb, $bb_current_user, $page, $bb_cache,
		$posts, $bb_post, $post_id, $topics, $topic, $topic_id,
		$forums, $forum, $forum_id, $tags, $tag, $tag_name, $user, $user_id, $view,
		$del_class, $bb_alt;

	if ( $globals ) {
		foreach ( $globals as $global => $v ) {
			if ( !is_numeric($global) ) {
				$$global = $v;
			} else {
				global $$v;
			}
		}
	}

	$files = (array) $files;
	$template = false;
	$default_template = false;
	$file_used = false;
	$default_file_used = false;

	foreach ( $files as $file ) {
		do_action( 'bb_' . $file, $action_arg );
		if ( false !== $template = bb_get_template( $file, false ) ) {
			$file_used = $file;
			break;
		}
		if ( !$default_template ) {
			if ( false !== $default_template = bb_get_default_template( $file ) ) {
				$default_file_used = $file;
			}
		}
	}

	if ( !$template && $default_template ) {
		$template = $default_template;
		$file_used = $default_file_used;
	}

	$template = apply_filters( 'bb_template', $template, $file_used );
	include( $template );

	do_action( 'bb_after_' . $file_used, $action_arg );
}

function bb_get_template( $file, $default = true )
{
	global $bb;
	// Skip theme loading in "safe" mode
	if ( !isset( $bb->safemode ) || $bb->safemode !== true ) {
		if ( file_exists( bb_get_active_theme_directory() .  $file ) ) {
			return bb_get_active_theme_directory() .  $file;
		}
	}

	if ( !$default ) {
		return false;
	}

	return bb_get_default_template( $file );
}

function bb_get_default_template( $file )
{
	if ( file_exists( BB_DEFAULT_THEME_DIR . $file ) ) {
		return BB_DEFAULT_THEME_DIR . $file;
	}
}

function bb_get_header()
{
	bb_load_template( 'header.php' );
}

function bb_language_attributes( $xhtml = 0 )
{
	$output = '';
	if ( $dir = bb_get_option( 'text_direction' ) ) {
		$output = 'dir="' . $dir . '" ';
	}
	if ( $lang = bb_get_option( 'language' ) ) {
		$output .= 'xml:lang="' . $lang . '" ';
		if ( $xhtml < '1.1' ) {
			$output .= 'lang="' . $lang . '"';
		}
	}

	echo ' ' . rtrim( $output );
}

function bb_generator( $type = 'xhtml' )
{
	if ( !$type ) {
		$type = 'xhtml';
	}
	echo apply_filters( 'bb_generator', bb_get_generator( $type ) . "\n", $type );
}

function bb_get_generator( $type = 'xhtml' )
{
	if ( !$type ) {
		$type = 'xhtml';
	}
	switch ( $type ) {
		case 'html':
			$gen = '<meta name="generator" content="bbPress ' . bb_get_option( 'version' ) . '">';
			break;
		case 'xhtml':
			$gen = '<meta name="generator" content="bbPress ' . bb_get_option( 'version' ) . '" />';
			break;
		case 'atom':
			$gen = '<generator uri="http://bbpress.org/" version="' . bb_get_option( 'version' ) . '">bbPress</generator>';
			break;
		case 'rss2':
			$gen = '<generator>http://bbpress.org/?v=' . bb_get_option( 'version' ) . '</generator>';
			break;
		case 'rdf':
			$gen = '<admin:generatorAgent rdf:resource="http://bbpress.org/?v=' . bb_get_option( 'version' ) . '" />';
			break;
		case 'comment':
			$gen = '<!-- generator="bbPress/' . bb_get_option( 'version' ) . '" -->';
			break;
		case 'export':
			$gen = '<!-- generator="bbPress/' . bb_get_option( 'version' ) . '" created="'. date( 'Y-m-d H:i' ) . '"-->';
			break;
	}
	return apply_filters( 'bb_get_generator', $gen, $type );
}

function bb_stylesheet_uri( $stylesheet = '' )
{
	echo esc_html( bb_get_stylesheet_uri( $stylesheet ) );
}

function bb_get_stylesheet_uri( $stylesheet = '' )
{
	if ( 'rtl' == $stylesheet ) {
		$css_file = 'style-rtl.css';
	} else {
		$css_file = 'style.css';
	}

	$active_theme = bb_get_active_theme_directory();

	if ( file_exists( $active_theme . $css_file ) ) {
		$r = bb_get_active_theme_uri() . $css_file;
	} else {
		$r = BB_DEFAULT_THEME_URL . $css_file;
	}

	return apply_filters( 'bb_get_stylesheet_uri', $r, $stylesheet );
}

function bb_active_theme_uri()
{
	echo bb_get_active_theme_uri();
}

function bb_get_active_theme_uri()
{
	global $bb;
	// Skip theme loading in "safe" mode
	if ( isset( $bb->safemode ) && $bb->safemode === true ) {
		$active_theme_uri = BB_DEFAULT_THEME_URL;
	} elseif ( !$active_theme = bb_get_option( 'bb_active_theme' ) ) {
		$active_theme_uri = BB_DEFAULT_THEME_URL;
	} else {
		$active_theme_uri = bb_get_theme_uri( $active_theme );
	}
	return apply_filters( 'bb_get_active_theme_uri', $active_theme_uri );
}

function bb_get_theme_uri( $theme = false )
{
	global $bb;
	if ( preg_match( '/^([a-z0-9_-]+)#([a-z0-9_-]+)$/i', $theme, $_matches ) ) {
		$theme_uri = $bb->theme_locations[$_matches[1]]['url'] . $_matches[2] . '/';
	} else {
		$theme_uri = $bb->theme_locations['core']['url'];
	}
	return apply_filters( 'bb_get_theme_uri', $theme_uri, $theme );
}

function bb_get_footer()
{
	bb_load_template( 'footer.php' );
}

function bb_head()
{
	do_action('bb_head');
}

/**
 * Display the link to the Really Simple Discovery service endpoint.
 *
 * @link http://archipelago.phrasewise.com/rsd
 * @since 1.0
 */
function bb_rsd_link() {
	if (bb_get_option('enable_xmlrpc'))
		echo '<link rel="EditURI" type="application/rsd+xml" title="RSD" href="' . bb_get_uri('xmlrpc.php', 'rsd', BB_URI_CONTEXT_LINK_OTHER + BB_URI_CONTEXT_BB_XMLRPC) . '" />' . "\n";
}

/**
 * Display the link to the pingback service endpoint.
 *
 * @since 1.0
 */
function bb_pingback_link() {
	if (bb_get_option('enable_pingback'))
		echo '<link rel="pingback" href="' . bb_get_uri('xmlrpc.php', null, BB_URI_CONTEXT_LINK_OTHER + BB_URI_CONTEXT_BB_XMLRPC) . '" />' . "\n";
}

function profile_menu() {
	global $user_id, $profile_menu, $self, $profile_page_title;
	$list  = "<ul id='profile-menu'>";
	$list .= "\n\t<li" . ( ( $self ) ? '' : ' class="current"' ) . '><a href="' . esc_attr( get_user_profile_link( $user_id ) ) . '">' . __('Profile') . '</a></li>';
	$id = bb_get_current_user_info( 'id' );
	foreach ($profile_menu as $item) {
		// 0 = name, 1 = users cap, 2 = others cap, 3 = file
		$class = '';
		if ( $item[3] == $self ) {
			$class = ' class="current"';
			$profile_page_title = $item[0];
		}
		if ( bb_can_access_tab( $item, $id, $user_id ) )
			if ( file_exists($item[3]) || is_callable($item[3]) )
				$list .= "\n\t<li$class><a href='" . esc_attr( get_profile_tab_link($user_id, $item[4]) ) . "'>{$item[0]}</a></li>";
	}
	$list .= "\n</ul>";
	echo $list;
}

function login_form() {
	if ( bb_is_user_logged_in() )
		bb_load_template( 'logged-in.php' );
	else
		bb_load_template( 'login-form.php', array('user_login', 'remember_checked', 'redirect_to', 're') );
}

function search_form( $q = '' ) {
	bb_load_template( 'search-form.php', array('q' => $q) );
}

function bb_post_template() {
	bb_load_template( 'post.php' );
}

function post_form( $args = array() ) {
	global $page, $topic, $forum;

	$defaults = array(
		'h2' => '',
		'last_page_only' => true
	);
	if ( is_string( $args ) ) {
		$defaults['h2'] = $args;
	}
	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( isset( $forum->forum_is_category ) && $forum->forum_is_category ) {
		return;
	}

	$add = topic_pages_add();
	if ( empty( $h2 ) && false !== $h2 ) {
		if ( bb_is_topic() ) {
			$h2 = __( 'Reply' );
		} elseif ( bb_is_forum() ) {
			$h2 = __( 'New Topic in this Forum' );
		} elseif ( bb_is_tag() || bb_is_front() ) {
			$h2 = __( 'Add New Topic' );
		}
	}

	$last_page = bb_get_page_number( ( isset( $topic->topic_posts ) ? $topic->topic_posts : 0 ) + $add );

	if ( !empty( $h2 ) ) {
		if ( bb_is_topic() && ( $page != $last_page && $last_page_only ) ) {
			$h2 = '<a href="' . esc_attr( get_topic_link( 0, $last_page ) . '#postform' ) . '">' . $h2 . ' &raquo;</a>';
		}
		echo '<h2 class="post-form">' . $h2 . '</h2>' . "\n";
	}

	do_action( 'pre_post_form' );

	if (
		( false === bb_is_login_required() ) ||
		( bb_is_topic() && bb_current_user_can( 'write_post', $topic->topic_id ) && ( $page == $last_page || !$last_page_only ) ) ||
		( !bb_is_topic() && bb_current_user_can( 'write_topic', isset( $forum->forum_id ) ? $forum->forum_id : 0 ) )
	) {
		echo '<form class="postform post-form" id="postform" method="post" action="' . bb_get_uri( 'bb-post.php', null, BB_URI_CONTEXT_FORM_ACTION ) . '">' . "\n";
		echo '<fieldset>' . "\n";
		bb_load_template( 'post-form.php', array('h2' => $h2) );
		bb_nonce_field( bb_is_topic() ? 'create-post_' . $topic->topic_id : 'create-topic' );
		if ( bb_is_forum() ) {
			echo '<input type="hidden" name="forum_id" value="' . $forum->forum_id . '" />' . "\n";
		} elseif ( bb_is_topic() ) {
			echo '<input type="hidden" name="topic_id" value="' . $topic->topic_id . '" />' . "\n";
		}
		do_action( 'post_form' );
		echo "\n</fieldset>\n</form>\n";
	} elseif ( !bb_is_user_logged_in() ) {
		echo '<p>';
		printf(
			__('You must <a href="%s">log in</a> to post.'),
			esc_attr( bb_get_uri( 'bb-login.php', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_USER_FORMS ) )
		);
		echo '</p>';
	}

	do_action( 'post_post_form' );
}

function edit_form() {
	global $bb_post;
	do_action('pre_edit_form');
	echo '<form class="postform edit-form" method="post" action="' . bb_get_uri('bb-edit.php', null, BB_URI_CONTEXT_FORM_ACTION)  . '">' . "\n";
	echo '<fieldset>' . "\n";
	bb_load_template( 'edit-form.php', array('topic_title') );
	bb_nonce_field( 'edit-post_' . $bb_post->post_id );
	do_action('edit_form');
	if ($_REQUEST['view'] === 'all')
		echo "\n" . '<input type="hidden" name="view" value="all" />';
	echo "\n" . '</fieldset>' . "\n" . '</form>' . "\n";
	do_action('post_edit_form');
}

function bb_anonymous_post_form() {
	if ( !bb_is_user_logged_in() && !bb_is_login_required() )
		bb_load_template( 'post-form-anonymous.php' );
}

function alt_class( $key, $others = '' ) {
	echo get_alt_class( $key, $others );
}

function get_alt_class( $key, $others = '' ) {
	global $bb_alt;
	$class = '';
	if ( !isset( $bb_alt[$key] ) ) $bb_alt[$key] = -1;
	++$bb_alt[$key];
	$others = trim($others);
	if ( $others xor $bb_alt[$key] % 2 )
		$class = ' class="' . ( ($others) ? $others : 'alt' ) . '"';
	elseif ( $others && $bb_alt[$key] % 2 )
		$class = ' class="' . $others . ' alt"';
	return $class;
}

function bb_location() {
	echo apply_filters( 'bb_location', bb_get_location() );
}

function bb_get_location() { // Not for display.  Do not internationalize.
	static $file;
	static $filename;

	if ( !isset( $file ) ) {
		$path = '';
		foreach ( array( $_SERVER['SCRIPT_NAME'], $_SERVER['SCRIPT_FILENAME'], $_SERVER['PHP_SELF'] ) as $_path ) {
			if ( false !== strpos( $_path, '.php' ) ) {
				$path = $_path;
				break;
			}
		}

		$filename = bb_find_filename( $path );
		// Make $file relative to bbPress root directory
		$file = str_replace( bb_get_option( 'path' ), '', $path );
	}

	switch ( $filename ) {
		case 'index.php':
		case 'page.php':
			$location = 'front-page';
			break;
		case 'forum.php':
			$location = 'forum-page';
			break;
		case 'tags.php':
			$location = 'tag-page';
			break;
		case 'edit.php':
			$location = 'topic-edit-page';
			break;
		case 'topic.php':
			$location = 'topic-page';
			break;
		case 'rss.php':
			$location = 'feed-page';
			break;
		case 'search.php':
			$location = 'search-page';
			break;
		case 'profile.php':
			$location = 'profile-page';
			break;
		case 'favorites.php':
			$location = 'favorites-page';
			break;
		case 'view.php':
			$location = 'view-page';
			break;
		case 'statistics.php':
			$location = 'stats-page';
			break;
		case 'bb-login.php':
			$location = 'login-page';
			break;
		case 'register.php':
			$location = 'register-page';
			break;
		default:
			$location = apply_filters( 'bb_get_location', '', $file );
			break;
	}

	return $location;
}

function bb_is_front() {
	return 'front-page' == bb_get_location();
}

function bb_is_forum() {
	return 'forum-page' == bb_get_location();
}

/**
 * Whether a user is required to log in in order to create posts and forums.
 * @return bool Whether a user must be logged in.
 */
function bb_is_login_required() {
	return ! (bool) bb_get_option('enable_loginless');
}

function bb_is_tags() {
	return 'tag-page' == bb_get_location();
}

function bb_is_tag() {
	global $tag, $tag_name;
	return $tag && $tag_name && bb_is_tags();
}

function bb_is_topic_edit() {
	return 'topic-edit-page' == bb_get_location();
}

function bb_is_topic() {
	return 'topic-page' == bb_get_location();
}

function bb_is_feed() {
	return 'feed-page' == bb_get_location();
}

function bb_is_search() {
	return 'search-page' == bb_get_location();
}

function bb_is_profile() {
	return 'profile-page' == bb_get_location();
}

function bb_is_favorites() {
	return 'favorites-page' == bb_get_location();
}

function bb_is_view() {
	return 'view-page' == bb_get_location();
}

function bb_is_statistics() {
	return 'stats-page' == bb_get_location();
}

function bb_is_admin() {
	if ( defined('BB_IS_ADMIN') )
		return BB_IS_ADMIN;
	return false;
}

function bb_title( $args = '' ) {
	echo apply_filters( 'bb_title', bb_get_title( $args ), $args );
}

function bb_get_title( $args = '' ) {
	$defaults = array(
		'separator' => ' &laquo; ',
		'order' => 'normal',
		'front' => ''
	);
	$args = wp_parse_args( $args, $defaults );
	$title = array();
	
	switch ( bb_get_location() ) {
		case 'search-page':
			if ( !$q = trim( @$_GET['search'] ) )
				if ( !$q = trim( @$_GET['q'] ) )
					break;
			$title[] = sprintf( __( 'Search for %s' ), esc_html( $q ) );
			break;
		case 'front-page':
			if ( !empty( $args['front'] ) )
				$title[] = $args['front'];
			break;
		
		case 'topic-edit-page':
		case 'topic-page':
			$title[] = get_topic_title();
			break;
		
		case 'forum-page':
			$title[] = get_forum_name();
			break;
		
		case 'tag-page':
			if ( bb_is_tag() )
				$title[] = esc_html( bb_get_tag_name() );
			
			$title[] = __( 'Tags' );
			break;
		
		case 'profile-page':
			$title[] = get_user_display_name();
			break;
		
		case 'view-page':
			$title[] = get_view_name();
			break;
	}
	
	if ( $st = bb_get_option( 'static_title' ) )
		$title = array( $st );
	
	$title[] = bb_get_option( 'name' );
	
	if ( 'reversed' == $args['order'] )
		$title = array_reverse( $title );
	
	return apply_filters( 'bb_get_title', implode( $args['separator'], $title ), $args, $title );
}

function bb_feed_head() {
	
	$feeds = array();
	
	switch (bb_get_location()) {
		case 'profile-page':
			if ( $tab = isset($_GET['tab']) ? $_GET['tab'] : bb_get_path(2) )
				if ($tab != 'favorites')
					break;
			
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; User Favorites: %2$s'), bb_get_option( 'name' ), get_user_name()),
				'href'  => get_favorites_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			break;
		
		case 'topic-page':
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; Topic: %2$s'), bb_get_option( 'name' ), get_topic_title()),
				'href'  => get_topic_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			break;
		
		case 'tag-page':
			if (bb_is_tag()) {
				$feeds[] = array(
					'title' => sprintf(__('%1$s &raquo; Tag: %2$s - Recent Posts'), bb_get_option( 'name' ), bb_get_tag_name()),
					'href'  => bb_get_tag_posts_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
				);
				$feeds[] = array(
					'title' => sprintf(__('%1$s &raquo; Tag: %2$s - Recent Topics'), bb_get_option( 'name' ), bb_get_tag_name()),
					'href'  => bb_get_tag_topics_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
				);
			}
			break;
		
		case 'forum-page':
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; Forum: %2$s - Recent Posts'), bb_get_option( 'name' ), get_forum_name()),
				'href'  => bb_get_forum_posts_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; Forum: %2$s - Recent Topics'), bb_get_option( 'name' ), get_forum_name()),
				'href'  => bb_get_forum_topics_rss_link(0, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			break;
		
		case 'front-page':
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; Recent Posts'), bb_get_option( 'name' )),
				'href'  => bb_get_posts_rss_link(BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			$feeds[] = array(
				'title' => sprintf(__('%1$s &raquo; Recent Topics'), bb_get_option( 'name' )),
				'href'  => bb_get_topics_rss_link(BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
			);
			break;
		
		case 'view-page':
			global $bb_views, $view;
			if ($bb_views[$view]['feed']) {
				$feeds[] = array(
					'title' => sprintf(__('%1$s &raquo; View: %2$s'), bb_get_option( 'name' ), get_view_name()),
					'href'  => bb_get_view_rss_link(null, BB_URI_CONTEXT_LINK_ALTERNATE_HREF + BB_URI_CONTEXT_BB_FEED)
				);
			}
			break;
	}
	
	if (count($feeds)) {
		$feed_links = array();
		foreach ($feeds as $feed) {
			$link = '<link rel="alternate" type="application/rss+xml" ';
			$link .= 'title="' . esc_attr($feed['title']) . '" ';
			$link .= 'href="' . esc_attr($feed['href']) . '" />';
			$feed_links[] = $link;
		}
		$feed_links = join("\n", $feed_links);
	} else {
		$feed_links = '';
	}
	
	echo apply_filters('bb_feed_head', $feed_links);
}

function bb_get_posts_rss_link($context = 0) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	if ( bb_get_option( 'mod_rewrite' ) )
		$link = bb_get_uri('rss/', null, $context);
	else
		$link = bb_get_uri('rss.php', null, $context);
	return apply_filters( 'bb_get_posts_rss_link', $link, $context );
}

function bb_get_topics_rss_link($context = 0) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	if ( bb_get_option( 'mod_rewrite' ) )
		$link = bb_get_uri('rss/topics', null, $context);
	else
		$link = bb_get_uri('rss.php', array('topics' => 1), $context);
	return apply_filters( 'bb_get_topics_rss_link', $link, $context );
}

function bb_view_rss_link($view = null, $context = 0) {
	echo apply_filters( 'bb_view_rss_link', bb_get_view_rss_link($view, $context), $context);
}

function bb_get_view_rss_link($view = null, $context = 0) {
	if (!$view) {
		global $view;
	}
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	if ( bb_get_option( 'mod_rewrite' ) )
		$link = bb_get_uri('rss/view/' . $view, null, $context);
	else
		$link = bb_get_uri('rss.php', array('view' => $view), $context);
	return apply_filters( 'bb_get_view_rss_link', $link, $context );
}

function bb_latest_topics_pages( $args = null )
{
	$defaults = array( 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page;
	static $bb_latest_topics_count;
	if ( !$bb_latest_topics_count) {
		global $bbdb;
		$bb_latest_topics_count = $bbdb->get_var('SELECT COUNT(`topic_id`) FROM `' . $bbdb->topics . '` WHERE `topic_status` = 0 AND `topic_sticky` != 2;');
	}
	if ( $pages = apply_filters( 'bb_latest_topics_pages', get_page_number_links( $page, $bb_latest_topics_count ), $bb_latest_topics_count ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

// FORUMS

function forum_id( $forum_id = 0 ) {
	echo apply_filters( 'forum_id', get_forum_id( $forum_id ) );
}

function get_forum_id( $forum_id = 0 ) {
	global $forum;
	$forum_id = (int) $forum_id;
	if ( $forum_id )
		$_forum = bb_get_forum( $forum_id );
	else
		$_forum =& $forum;
	return $_forum->forum_id;
}

function forum_link( $forum_id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}
	echo apply_filters('forum_link', get_forum_link( $forum_id, $page, $context ), $forum_id, $context );
}

function get_forum_link( $forum_id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}
	
	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'forum_slug';
		} else {
			$column = 'forum_id';
		}
		$page = (1 < $page) ? '/page/' . $page : '';
		$link = bb_get_uri('forum/' . $forum->$column . $page, null, $context);
	} else {
		$query = array(
			'id' => $forum->forum_id,
			'page' => (1 < $page) ? $page : false
		);
		$link = bb_get_uri('forum.php', $query, $context);
	}

	return apply_filters( 'get_forum_link', $link, $forum->forum_id, $context );
}

function forum_name( $forum_id = 0 ) {
	echo apply_filters( 'forum_name', get_forum_name( $forum_id ), $forum_id );
}

function get_forum_name( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_name', $forum->forum_name, $forum->forum_id );
}

function forum_description( $args = null ) {
	if ( is_numeric($args) )
		$args = array( 'id' => $args );
	elseif ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'before' => $args );
	$defaults = array( 'id' => 0, 'before' => ' &#8211; ', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	if ( $desc = apply_filters( 'forum_description', get_forum_description( $args['id'] ), $args['id'], $args ) )
		echo $args['before'] . $desc . $args['after'];
}

function get_forum_description( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_description', $forum->forum_desc, $forum->forum_id );
}

function get_forum_parent( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_parent', $forum->forum_parent, $forum->forum_id );
}

function get_forum_position( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_position', $forum->forum_order, $forum->forum_id );
}

function bb_get_forum_is_category( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'bb_get_forum_is_category', isset($forum->forum_is_category) ? $forum->forum_is_category : false, $forum->forum_id );
}

function forum_topics( $forum_id = 0 ) {
	echo apply_filters( 'forum_topics', get_forum_topics( $forum_id ), $forum_id );
}

function get_forum_topics( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_topics', $forum->topics, $forum->forum_id );
}

function forum_posts( $forum_id = 0 ) {
	echo apply_filters( 'forum_posts', get_forum_posts( $forum_id ), $forum_id );
}

function get_forum_posts( $forum_id = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	return apply_filters( 'get_forum_posts', $forum->posts, $forum->forum_id );
}

function forum_pages( $args = null )
{
	// Compatibility
	if ( $args && is_numeric( $args ) ) {
		$args = array( 'id' => $args );
	}
	$defaults = array( 'id' => 0, 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page;
	$forum = bb_get_forum( get_forum_id( $args['id'] ) );
	if ( $pages = apply_filters( 'forum_pages', get_page_number_links( $page, $forum->topics ), $forum->topics ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

function bb_forum_posts_rss_link( $forum_id = 0, $context = 0 ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	echo apply_filters('bb_forum_posts_rss_link', bb_get_forum_posts_rss_link( $forum_id, $context ), $context );
}

function bb_get_forum_posts_rss_link( $forum_id = 0, $context = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	
	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'forum_slug';
		} else {
			$column = 'forum_id';
		}
		$link = bb_get_uri('rss/forum/' . $forum->$column, null, $context);
	} else {
		$link = bb_get_uri('rss.php', array('forum' => $forum->forum_id), $context);
	}
	return apply_filters( 'bb_get_forum_posts_rss_link', $link, $forum->forum_id, $context );
}

function bb_forum_topics_rss_link( $forum_id = 0, $context = 0 ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	echo apply_filters('bb_forum_topics_rss_link', bb_get_forum_topics_rss_link( $forum_id, $context ), $context );
}

function bb_get_forum_topics_rss_link( $forum_id = 0, $context = 0 ) {
	$forum = bb_get_forum( get_forum_id( $forum_id ) );
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	
	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'forum_slug';
		} else {
			$column = 'forum_id';
		}
		$link = bb_get_uri('rss/forum/' . $forum->$column . '/topics', null, $context);
	} else {
		$link = bb_get_uri('rss.php', array('forum' => $forum->forum_id, 'topics' => 1), $context);
	}
	return apply_filters( 'bb_get_forum_topics_rss_link', $link, $forum->forum_id, $context );
}

function bb_get_forum_bread_crumb($args = '') {
	$defaults = array(
		'forum_id' => 0,
		'separator' => ' &raquo; ',
		'class' => null
	);
	$args = wp_parse_args($args, $defaults);
	extract($args, EXTR_SKIP);

	$trail = '';
	$trail_forum = bb_get_forum(get_forum_id($forum_id));
	if ($class) {
		$class = ' class="' . $class . '"';
	}
	$current_trail_forum_id = $trail_forum->forum_id;
	while ( $trail_forum && $trail_forum->forum_id > 0 ) {
		$crumb = $separator;
		if ($current_trail_forum_id != $trail_forum->forum_id || !bb_is_forum()) {
			$crumb .= '<a' . $class . ' href="' . get_forum_link($trail_forum->forum_id) . '">';
		} elseif ($class) {
			$crumb .= '<span' . $class . '>';
		}
		$crumb .= get_forum_name($trail_forum->forum_id);
		if ($current_trail_forum_id != $trail_forum->forum_id || !bb_is_forum()) {
			$crumb .= '</a>';
		} elseif ($class) {
			$crumb .= '</span>';
		}
		$trail = $crumb . $trail;
		$trail_forum = bb_get_forum($trail_forum->forum_parent);
	}

	return apply_filters('bb_get_forum_bread_crumb', $trail, $forum_id );
}

function bb_forum_bread_crumb( $args = '' ) {
	echo apply_filters('bb_forum_bread_crumb', bb_get_forum_bread_crumb( $args ) );
}

// Forum Loop //

function &bb_forums( $args = '' ) {
	global $bb_forums_loop;

	$default_type = 'flat';

	if ( is_numeric($args) ) {
		$args = array( 'child_of' => $args );
	} elseif ( func_num_args() > 1 ) { // bb_forums( 'ul', $args ); Deprecated
		$default_type = $args;
		$args = func_get_arg(1);
	} elseif ( $args && is_string($args) && false === strpos($args, '=') ) {
		$args = array( 'type' => $args );
	}

	// hierarchical not used here.  Sent to bb_get_forums for proper ordering.
	$args = wp_parse_args( $args, array('hierarchical' => true, 'type' => $default_type, 'walker' => 'BB_Walker_Blank') );

	$levels = array( '', '' );

	if ( in_array($args['type'], array('list', 'ul')) )
		$levels = array( '<ul>', '</ul>' );

	$forums = bb_get_forums( $args );

	if ( !class_exists($args['walker']) )
		$args['walker'] = 'BB_Walker_Blank';

	if ( $bb_forums_loop = BB_Loop::start( $forums, $args['walker'] ) ) {
		$bb_forums_loop->preserve( array('forum', 'forum_id') );
		$bb_forums_loop->walker->db_fields = array( 'id' => 'forum_id', 'parent' => 'forum_parent' );
		list($bb_forums_loop->walker->start_lvl, $bb_forums_loop->walker->end_lvl) = $levels;
		return $bb_forums_loop->elements;
	}
	$false = false;
	return $false;
}

function bb_forum() { // Returns current depth
	global $bb_forums_loop;
	if ( !is_object($bb_forums_loop) || !is_a($bb_forums_loop, 'BB_Loop') )
		return false;
	if ( !is_array($bb_forums_loop->elements) )
		return false;

	if ( $bb_forums_loop->step() ) {
		$GLOBALS['forum'] =& $bb_forums_loop->elements[key($bb_forums_loop->elements)]; // Globalize the current forum object
	} else {
		$bb_forums_loop->reinstate();
		return $bb_forums_loop = null; // All done?  Kill the object and exit the loop.
	}

	return $bb_forums_loop->walker->depth;
}

function bb_forum_pad( $pad, $offset = 0 ) {
	global $bb_forums_loop;
	if ( !is_object($bb_forums_loop) || !is_a($bb_forums_loop, 'BB_Loop') )
		return false;

	echo $bb_forums_loop->pad( $pad, $offset );
}

function bb_forum_class( $args = null ) {
	echo apply_filters( 'bb_forum_class', get_alt_class( 'forum', bb_get_forum_class_names( $args ) ), $args );
}

function bb_get_forum_class_names( $args = null ) {
	if ( is_numeric( $args ) ) { // Not used
		$args = array( 'id' => $args );
	} elseif ( $args && is_string( $args ) && false === strpos( $args, '=' ) ) {
		$args = array( 'class' => $args );
	}
	$defaults = array( 'id' => 0, 'key' => 'forum', 'class' => '', 'output' => 'string' );
	$args = wp_parse_args( $args, $defaults );

	$classes = array();
	if ( $args['class'] ) {
		$classes[] = $args['class'];
	}

	global $bb_forums_loop;
	if ( is_object( $bb_forums_loop ) && is_a( $bb_forums_loop, 'BB_Loop' ) ) {
		$classes = array_merge( $classes, $bb_forums_loop->classes( 'array' ) );
	}

	if ( $args['output'] === 'string' ) {
		$classes = join( ' ', $classes );
	}

	return apply_filters( 'bb_get_forum_class', $classes, $args );
}

// TOPICS
function topic_id( $id = 0 ) {
	echo apply_filters( 'topic_id', get_topic_id( $id ) );
}

function get_topic_id( $id = 0 ) {
	global $topic;
	$id = (int) $id;
	if ( $id )
		$_topic = get_topic( $id );
	else
		$_topic =& $topic;

	if ( empty($_topic->topic_id) )
		return 0;

	return (int) $_topic->topic_id;
}

function topic_link( $id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	echo apply_filters( 'topic_link', get_topic_link( $id, $page, $context ), $id, $page, $context );
}

function get_topic_link( $id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	$topic = get_topic( get_topic_id( $id ) );

	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}

	$args = array();

	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'topic_slug';
		} else {
			$column = 'topic_id';
		}
		$page = (1 < $page) ? '/page/' . $page : '';
		$link = bb_get_uri('topic/' . $topic->$column . $page, null, $context);
	} else {
		$page = (1 < $page) ? $page : false;
		$link = bb_get_uri('topic.php', array('id' => $topic->topic_id, 'page' => $page), $context);
	}

	return apply_filters( 'get_topic_link', $link, $topic->topic_id, $context );
}

function topic_rss_link( $id = 0, $context = 0 ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	echo apply_filters('topic_rss_link', get_topic_rss_link($id, $context), $id, $context );
}

function get_topic_rss_link( $id = 0, $context = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );

	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}

	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'topic_slug';
		} else {
			$column = 'topic_id';
		}
		$link = bb_get_uri('rss/topic/' . $topic->$column, null, $context);
	} else {
		$link = bb_get_uri('rss.php', array('topic' => $topic->topic_id), $context);
	}
	return apply_filters( 'get_topic_rss_link', $link, $topic->topic_id, $context );
}

function bb_topic_labels() {
	echo apply_filters( 'bb_topic_labels', null );
}

function topic_title( $id = 0 ) {
	echo apply_filters( 'topic_title', get_topic_title( $id ), get_topic_id( $id ) );
}

function get_topic_title( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	return apply_filters( 'get_topic_title', $topic->topic_title, $topic->topic_id );
}

function topic_page_links( $id = 0, $args = null ) {
	echo apply_filters( 'topic_page_links', get_topic_page_links( $id, $args ), get_topic_id( $id ) );
}

function get_topic_page_links( $id = 0, $args = null ) {

	$defaults = array(
		'show_all' => false,
		'end_size' => 3,
		'before' => ' - ',
		'after' => null
	);

	$args = wp_parse_args( $args, $defaults );

	$topic = get_topic( get_topic_id( $id ) );

	$uri = get_topic_link();
	if ( bb_get_option('mod_rewrite') ) {
		if ( false === $pos = strpos( $uri, '?' ) ) {
			$uri = $uri . '%_%';
		} else {
			$uri = substr_replace( $uri, '%_%', $pos, 0 );
		}
	} else {
		$uri = add_query_arg( 'page', '%_%', $uri );
	}

	$posts = $topic->topic_posts + topic_pages_add( $topic->topic_id );

	$per_page = apply_filters( 'get_topic_page_links_per_page', bb_get_option('page_topics') );

	$_links = bb_paginate_links(
		array(
			'base' => $uri,
			'format' => bb_get_option('mod_rewrite') ? '/page/%#%' : '%#%',
			'total' => ceil($posts/$per_page),
			'current' => 0,
			'show_all' => $args['show_all'],
			'end_size' => $args['end_size'],
			'type' => 'array'
		)
	);

	$links = $_links;

	$r = '';

	if ( $links ) {
		if ( !$show_first ) {
			unset( $links[0] );
		}

		if ( $args['before'] ) {
			$r .= $args['before'];
		}
		$r .= join('', $links);
		if ( $args['after'] ) {
			$r .= $args['after'];
		}
	}

	return apply_filters( 'get_topic_page_links', $r, $_links, $topic->topic_id );
}

function bb_topic_voices( $id = 0 ) {
	echo apply_filters( 'bb_topic_voices', bb_get_topic_voices( $id ), get_topic_id( $id ) );
}

function bb_get_topic_voices( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );

	if ( empty( $topic->voices_count ) ) {
		global $bbdb;

		if ( $voices = $bbdb->get_col( $bbdb->prepare( "SELECT DISTINCT poster_id FROM $bbdb->posts WHERE topic_id = %s AND post_status = '0';", $topic->topic_id ) ) ) {
			$voices = count( $voices );
			bb_update_topicmeta( $topic->topic_id, 'voices_count', $voices );
		}
	} else {
		$voices = $topic->voices_count;
	}

	return apply_filters( 'bb_get_topic_voices', $voices, $topic->topic_id );
}

function topic_posts( $id = 0 ) {
	echo apply_filters( 'topic_posts', get_topic_posts( $id ), get_topic_id( $id ) );
}

function get_topic_posts( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	return apply_filters( 'get_topic_posts', $topic->topic_posts, $topic->topic_id );
}

function get_topic_deleted_posts( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	return apply_filters( 'get_topic_deleted_posts', isset($topic->deleted_posts) ? $topic->deleted_posts : 0, $topic->topic_id );
}

function topic_noreply( $title ) {
	if ( 1 == get_topic_posts() && ( bb_is_front() || bb_is_forum() ) )
		$title = "<strong>$title</strong>";
	return $title;
}

function topic_last_poster( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	echo apply_filters( 'topic_last_poster', get_topic_last_poster( $id ), $topic->topic_last_poster, $topic->topic_id ); // $topic->topic_last_poster = user ID
}

function get_topic_last_poster( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	if ( isset( $topic->topic_last_post_id ) && ( 1 == $topic->topic_last_post_id ) ) {
		$user_display_name = $topic->topic_poster_name;
	} else {
		$user_display_name = get_post_author( $topic->topic_last_post_id );
	}
	return apply_filters( 'get_topic_last_poster', $user_display_name, $topic->topic_last_poster, $topic->topic_id ); // $topic->topic_last_poster = user ID
}

function topic_author( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	echo apply_filters( 'topic_author', get_topic_author( $id ), $topic->topic_poster, $topic->topic_id ); // $topic->topic_poster = user ID
}

function get_topic_author( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	$first_post = bb_get_first_post( $topic );
	if ( !empty( $first_post ) ) {
		$user_display_name = get_post_author( $first_post->post_id );
	} else {
		$user_display_name = $topic->topic_poster_name;
	}
	return apply_filters( 'get_topic_author', $user_display_name, $topic->topic_poster, $topic->topic_id ); // $topic->topic_poster = user ID
}

// Filters expect the format to by mysql on both topic_time and get_topic_time
function topic_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );
	$time = apply_filters( 'topic_time', get_topic_time( array('format' => 'mysql') + $args), $args );
	echo _bb_time_function_return( $time, $args );
}

function get_topic_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );

	$topic = get_topic( get_topic_id( $args['id'] ) );

	$time = apply_filters( 'get_topic_time', $topic->topic_time, $args );

	return _bb_time_function_return( $time, $args );
}

function topic_start_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );
	$time = apply_filters( 'topic_start_time', get_topic_start_time( array('format' => 'mysql') + $args), $args );
	echo _bb_time_function_return( $time, $args );
}

function get_topic_start_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );

	$topic = get_topic( get_topic_id( $args['id'] ) );

	$time = apply_filters( 'get_topic_start_time', $topic->topic_start_time, $args, $topic->topic_id );

	return _bb_time_function_return( $time, $args );
}

function topic_last_post_link( $id = 0 ) {
	echo apply_filters( 'topic_last_post_link', get_topic_last_post_link( $id ), $id);
}

function get_topic_last_post_link( $id = 0 ){
	$topic = get_topic( get_topic_id( $id ) );
	$page = bb_get_page_number( $topic->topic_posts );
	return apply_filters( 'get_post_link', get_topic_link( $topic->topic_id, $page ) . "#post-$topic->topic_last_post_id", $topic->topic_last_post_id, $topic->topic_id );
}

function topic_pages( $args = null )
{
	// Compatibility
	if ( $args && is_numeric( $args ) ) {
		$args = array( 'id' => $args );
	}
	$defaults = array( 'id' => 0, 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page;
	$topic = get_topic( get_topic_id( $args['id'] ) );
	$add = topic_pages_add( $topic->topic_id );
	if ( $pages = apply_filters( 'topic_pages', get_page_number_links( $page, $topic->topic_posts + $add ), $topic->topic_id ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

function topic_pages_add( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	if ( isset($_GET['view']) && 'all' == $_GET['view'] && bb_current_user_can('browse_deleted') && isset( $topic->deleted_posts ) )
		$add = $topic->deleted_posts;
	else
		$add = 0;
	return apply_filters( 'topic_pages_add', $add, isset($topic->topic_id) ? $topic->topic_id : 0 );
}

function get_page_number_links( $args ) {
	if ( 1 < func_num_args() ) {
		$_args = func_get_args();
		$args = array(
			'page' => $_args[0],
			'total' => $_args[1],
			'per_page' => isset( $_args[2] ) ? $_args[2] : '',
			'mod_rewrite' => isset( $_args[3] ) ? $_args[3] : 'use_option'
		);
	}
	$defaults = array(
		'page' => 1,
		'total' => false,
		'per_page' => '',
		'mod_rewrite' => 'use_option',
		'prev_text' => __( '&laquo; Previous' ),
		'next_text' => __( 'Next &raquo;' )
	);
	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	$add_args = array();
	$uri = rtrim( $_SERVER['REQUEST_URI'], '?&' );

	if ( $mod_rewrite === 'use_option' ) {
		$mod_rewrite = bb_get_option( 'mod_rewrite' );
	}

	if ( $mod_rewrite ) {
		$format = '/page/%#%';
		if ( 1 == $page ) {
			if ( false === $pos = strpos($uri, '?') )
				$uri = $uri . '%_%';
			else
				$uri = substr_replace($uri, '%_%', $pos, 0);
		} else {
			$uri = preg_replace('|/page/[0-9]+|', '%_%', $uri);
		}
		$uri = str_replace( '/%_%', '%_%', $uri );
	} else {
		if ( 1 == $page ) {
			if ( false === $pos = strpos($uri, '?') ) {
				$uri = $uri . '%_%';
				$format = '?page=%#%';
			} else {
				$uri = substr_replace($uri, '?%_%', $pos, 1);
				$format = 'page=%#%&';
			}
		} else {
			if ( false === strpos($uri, '?page=') ) {
				$uri = preg_replace('!&page=[0-9]+!', '%_%', $uri );
				$uri = str_replace( '&page=', '', $uri );
				$format = '&page=%#%';
			} else {
				$uri = preg_replace('!\?page=[0-9]+!', '%_%', $uri );
				$uri = str_replace( '?page=', '', $uri );
				$format = '?page=%#%';
			}
		}
	}

	if ( isset($_GET['view']) && in_array($_GET['view'], bb_get_views()) )
		$add_args['view'] = $_GET['view'];

	if ( empty( $per_page ) ) {
		$per_page = bb_get_option( 'page_topics' );
	}

	$links = bb_paginate_links( array(
		'base' => $uri,
		'format' => $format,
		'total' => ceil( $total/$per_page ),
		'current' => $page,
		'add_args' => $add_args,
		'type' => 'array',
		'mid_size' => 1,
		'prev_text' => $prev_text,
		'next_text' => $next_text
	) );

	if ($links) {
		$links = join('', $links);
	}
	return $links;
}

function bb_topic_admin( $args = '' ) {
	$parts = array(
		'delete' => bb_get_topic_delete_link( $args ),
		'close'  => bb_get_topic_close_link( $args ),
		'sticky' => bb_get_topic_sticky_link( $args ),
		'move'   => bb_get_topic_move_dropdown( $args )
	);

	echo join( "\n", apply_filters( 'bb_topic_admin', $parts, $args ) );
}

function topic_delete_link( $args = '' ) {
	echo bb_get_topic_delete_link( $args );
}

function bb_get_topic_delete_link( $args = '' ) {
	$defaults = array( 'id' => 0, 'before' => '[', 'after' => ']', 'delete_text' => false, 'undelete_text' => false, 'redirect' => true );
	extract(wp_parse_args( $args, $defaults ), EXTR_SKIP);
	$id = (int) $id;

	$topic = get_topic( get_topic_id( $id ) );

	if ( !$topic || !bb_current_user_can( 'delete_topic', $topic->topic_id ) )
		return;

	if ( 0 == $topic->topic_status ) {
		if ( true === $redirect )
			$redirect = add_query_arg( bb_is_admin() ? array() : array( 'view' => 'all'  ) );

		$query   = array( 'id' => $topic->topic_id, '_wp_http_referer' => $redirect ? rawurlencode( $redirect ) : false );
		$confirm = __('Are you sure you want to delete that?');
		$display = esc_html( $delete_text ? $delete_text : __('Delete entire topic') );
	} else {
		if ( true === $redirect )
			$redirect = remove_query_arg( bb_is_admin() ? array() : 'view' );

		$query   = array('id' => $topic->topic_id, 'view' => 'all', '_wp_http_referer' => $redirect ? rawurlencode( $redirect ) : false );
		$confirm = __('Are you sure you want to undelete that?');
		$display = esc_html( $undelete_text ? $undelete_text : __('Undelete entire topic') );
	}
	$uri = bb_get_uri('bb-admin/delete-topic.php', $query, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri = esc_url( bb_nonce_url( $uri, 'delete-topic_' . $topic->topic_id ) );
	
	return $before . '<a href="' . $uri . '" onclick="return confirm(\'' . esc_js( $confirm ) . '\');">' . $display . '</a>' . $after;
}

function topic_close_link( $args = '' ) {
	echo bb_get_topic_close_link( $args );
}

function bb_get_topic_close_link( $args = '' ) {
	$defaults = array( 'id' => 0, 'before' => '[', 'after' => ']', 'close_text' => false, 'open_text' => false, 'redirect' => true );
	extract(wp_parse_args( $args, $defaults ), EXTR_SKIP);
	$id = (int) $id;

	$topic = get_topic( get_topic_id( $id ) );

	if ( !$topic || !bb_current_user_can( 'close_topic', $topic->topic_id ) )
		return;

	if ( topic_is_open( $topic->topic_id ) )
		$display = esc_html( $close_text ? $close_text : __( 'Close topic' ) );
	else
		$display = esc_html( $open_text ? $open_text : __( 'Open topic' ) );

	if ( true === $redirect )
		$redirect = $_SERVER['REQUEST_URI'];

	$uri = bb_get_uri('bb-admin/topic-toggle.php', array( 'id' => $topic->topic_id, '_wp_http_referer' => $redirect ? rawurlencode( $redirect ) : false ), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri = esc_url( bb_nonce_url( $uri, 'close-topic_' . $topic->topic_id ) );
	
	return $before . '<a href="' . $uri . '">' . $display . '</a>' . $after;
}

function topic_sticky_link( $args = '' ) {
	echo bb_get_topic_sticky_link( $args );
}

function bb_get_topic_sticky_link( $args = '' ) {
	$defaults = array( 'id' => 0, 'before' => '[', 'after' => ']' );
	extract(wp_parse_args( $args, $defaults ), EXTR_SKIP);
	$id = (int) $id;

	$topic = get_topic( get_topic_id( $id ) );

	if ( !$topic || !bb_current_user_can( 'stick_topic', $topic->topic_id ) )
		return;

	$uri_stick = bb_get_uri('bb-admin/sticky.php', array('id' => $topic->topic_id), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri_stick = esc_url( bb_nonce_url( $uri_stick, 'stick-topic_' . $topic->topic_id ) );

	$uri_super = bb_get_uri('bb-admin/sticky.php', array('id' => $topic->topic_id, 'super' => 1), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri_super = esc_url( bb_nonce_url( $uri_super, 'stick-topic_' . $topic->topic_id ) );

	if ( topic_is_sticky( $topic->topic_id ) )
		return "$before<a href='" . $uri_stick . "'>". __('Unstick topic') ."</a>$after";
	else
		return "$before<a href='" . $uri_stick . "'>". __('Stick topic') . "</a> (<a href='" . $uri_super . "'>" . __('to front') . "</a>)$after";
}

function topic_show_all_link( $id = 0 ) {
	if ( !bb_current_user_can( 'browse_deleted' ) )
		return;
	if ( 'all' == @$_GET['view'] )
		echo "<a href='" . esc_attr( get_topic_link( $id ) ) . "'>". __('View normal posts') ."</a>";
	else
		echo "<a href='" . esc_attr( add_query_arg( 'view', 'all', get_topic_link( $id ) ) ) . "'>". __('View all posts') ."</a>";
}

function topic_posts_link( $id = 0 ) {
	echo get_topic_posts_link( $id );
}

function get_topic_posts_link( $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	$post_num = get_topic_posts( $id );
	$posts = sprintf(__ngettext( '%s post', '%s posts', $post_num ), $post_num);
	$r = '';

	if ( ( 'all' == @$_GET['view'] || bb_is_admin() ) && bb_current_user_can('browse_deleted') )
		$r .= "<a href='" . esc_attr( get_topic_link( $id ) ) . "'>$posts</a>";
	else
		$r .= $posts;

	if ( bb_current_user_can( 'browse_deleted' ) ) {
		$user_id = bb_get_current_user_info( 'id' );
		if ( isset($topic->bozos[$user_id]) && 'all' != @$_GET['view'] )
			add_filter('get_topic_deleted_posts', create_function('$a', "\$a -= {$topic->bozos[$user_id]}; return \$a;") );
		if ( $deleted = get_topic_deleted_posts( $id ) ) {
			$extra = sprintf(__('+%d more'), $deleted);
			if ( 'all' == @$_GET['view'] )
				$r .= " $extra";
			else
				$r .= " <a href='" . esc_attr( add_query_arg( 'view', 'all', get_topic_link( $id ) ) ) . "'>$extra</a>";
		}
	}

	return $r;
}

function topic_move_dropdown( $args = '' )
{
	echo bb_get_topic_move_dropdown( $args );
}

function bb_get_topic_move_dropdown( $args = '' )
{
	if ( $args && is_numeric( $args ) ) {
		$args = array( 'id' => (integer) $args );
	}

	$defaults = array( 'id' => 0, 'before' => '[', 'after' => ']' );
	extract(wp_parse_args( $args, $defaults ), EXTR_SKIP);
	$id = (int) $id;

	$topic = get_topic( get_topic_id( $id ) );
	if ( !bb_current_user_can( 'move_topic', $topic->topic_id ) )
		return;

	$dropdown = bb_get_forum_dropdown( array(
		'callback' => 'bb_current_user_can',
		'callback_args' => array('move_topic', $topic->topic_id),
		'selected' => $topic->forum_id,
		'tab' => false
	) );

	if ( !$dropdown )
		return;

	$r = $before . '<form id="topic-move" method="post" action="' . bb_get_uri( 'bb-admin/topic-move.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ) . '"><fieldset><div>' . "\n";
	$r .= '<input type="hidden" name="topic_id" value="' . $topic->topic_id . '" />' . "\n";
	$r .= '<label for="forum-id">'. __( 'Move to' ) . '</label>' . "\n";
	$r .= $dropdown . "\n";
	$r .= bb_nonce_field( 'move-topic_' . $topic->topic_id, '_wpnonce', true , false );
	$r .= '<input type="submit" name="Submit" value="' . __( 'Move' ) . '" />' . "\n";
	$r .= '</div></fieldset></form>' . $after;

	return $r;
}

function topic_class( $class = '', $key = 'topic', $id = 0 ) {
	$topic = get_topic( get_topic_id( $id ) );
	$class = $class ? explode(' ', $class ) : array();
	if ( '1' === $topic->topic_status && bb_current_user_can( 'browse_deleted' ) )
		$class[] = 'deleted';
	elseif ( 1 < $topic->topic_status && bb_current_user_can( 'browse_deleted' ) )
		$class[] = 'bozo';
	if ( '0' === $topic->topic_open )
		$class[] = 'closed';
	if ( 1 == $topic->topic_sticky && ( bb_is_forum() || bb_is_view() ) )
		$class[] = 'sticky';
	elseif ( 2 == $topic->topic_sticky && ( bb_is_front() || bb_is_forum() || bb_is_view() ) )
		$class[] = 'sticky super-sticky';
	$class = apply_filters( 'topic_class', $class, $topic->topic_id );
	$class = join(' ', $class);
	alt_class( $key, $class );
}

/**
 * bb_get_new_topic_link() - Get the link to the form for a new topic
 *
 * @since 1.0
 * @param mixed The arguments for this function.
 * @return string The link to the new topic form
 */
function bb_get_new_topic_link( $args = null ) {
	$defaults = array( 'text' => __('Add New &raquo;'), 'forum' => 0, 'tag' => '' );
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'text' => $args );

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( $forum && $forum = bb_get_forum( $forum ) )
		$url = get_forum_link( $forum->forum_id ) . '#postform';
	elseif ( $tag && $tag = bb_get_tag( $tag ) )
		$url = bb_get_tag_link( $tag->tag ) . '#postform';
	elseif ( bb_is_forum() ) {
		global $forum;
		$url = get_forum_link( $forum->forum_id ) . '#postform';
	} elseif ( bb_is_tag() ) {
		global $tag;
		$url = bb_get_tag_link( $tag ) . '#postform';
	} elseif ( bb_is_topic() )
		$url = get_forum_link() . '#postform';
	elseif ( bb_is_front() )
		$url = bb_get_uri(null, array('new' => 1));

	if ( !bb_is_user_logged_in() && bb_is_login_required() )
		$url = bb_get_uri('bb-login.php', array('redirect_to' => $url), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_USER_FORMS);
	elseif ( bb_is_forum() || bb_is_topic() ) {
		if ( !bb_current_user_can( 'write_topic', get_forum_id() ) )
			return;
	} else {
		if ( !bb_current_user_can( 'write_topics' ) )
			return;
	}

	if ( $url = esc_attr( apply_filters( 'new_topic_url', $url, $args ) ) )
		return '<a href="' . $url . '" class="new-topic">' . $text . '</a>' . "\n";
}

function bb_new_topic_link( $args = null ) {
	echo bb_get_new_topic_link($args);
}

function bb_new_topic_forum_dropdown( $args = '' ) {
	if ( !is_array( $args ) ) {
		$args = array(
			'callback' => 'bb_current_user_can',
			'callback_args' => array( 'write_topic' )
		);
	}
	if ( !isset( $args['callback'] ) && !isset( $args['callback_args'] ) ) {
		$args['callback']      = 'bb_current_user_can';
		$args['callback_args'] = array( 'write_topic' );
	}
	bb_forum_dropdown( $args );
}

function bb_topic_search_form( $args = null, $query_obj = null ) {
	global $bb_query_form;

	if ( $query_obj && is_a($query_obj, 'BB_Query_Form') ); // [sic]
	else
		$query_obj =& $bb_query_form;

	$query_obj->form( $args );
}

function bb_search_pages( $args = null ) {
	global $page, $search_count, $per_page;
	
	$defaults = array( 'before' => '', 'after' => '' );
 	$args = wp_parse_args( $args, $defaults );
	
	if ( $pages = apply_filters( 'bb_search_pages', get_page_number_links( array( 'page' => $page, 'total' => $search_count, 'per_page' => $per_page, 'mod_rewrite' => false ) ) ) )
		echo $args['before'] . $pages . $args['after'];
}

/**
 * bb_topic_pagecount() - Print the total page count for a topic
 *
 * @since 0.9
 * @param int $topic_id The topic id of the topic being queried
 * @return void
 */
function bb_topic_pagecount( $topic_id = 0 ) {
	echo bb_get_topic_pagecount( $topic_id );
}

/**
 * bb_get_topic_pagecount() - Get the total page count for a topic
 *
 * @since 0.9
 * @param int $topic_id The topic id of the topic being queried
 * @return int The total number of pages in the topic
 */
function bb_get_topic_pagecount( $topic_id = 0 ) {
	$topic = get_topic( get_topic_id( $topic_id ) );
	return bb_get_page_number( $topic->topic_posts + topic_pages_add() );
}

/**
 * bb_is_topic_lastpage() - Report whether the current page is the last page of a given topic
 *
 * @since 0.9
 * @param int $topic_id The topic id of the topic being queried
 * @return boolean True if called on the last page of a topic, otherwise false
 */
function bb_is_topic_lastpage( $topic_id = 0 ) {
	global $page;
	return ( $page == bb_get_topic_pagecount( $topic_id ) );
}

// POSTS

function post_id( $post_id = 0 ) {
	echo get_post_id( $post_id );
}

function get_post_id( $post_id = 0 ) {
	global $bb_post;
	$post_id = (int) $post_id;
	if ( $post_id )
		$post = bb_get_post( $post_id );
	else
		$post =& $bb_post;
	return $post->post_id;
}

function post_link( $post_id = 0 ) {
	echo apply_filters( 'post_link', get_post_link( $post_id ), get_post_id( $post_id ) );
}

function get_post_link( $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	$page = bb_get_page_number( $bb_post->post_position );
	return apply_filters( 'get_post_link', get_topic_link( $bb_post->topic_id, $page ) . "#post-$bb_post->post_id", $bb_post->post_id );
}

function post_anchor_link( $force_full = false ) {
	if ( defined('DOING_AJAX') || $force_full )
		post_link();
	else
		echo '#post-' . get_post_id();
}

function post_position( $post_id = 0 ) {
	echo apply_filters( 'post_position', get_post_position( $post_id ), get_post_id( $post_id ) );
}

function get_post_position( $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	return apply_filters( 'get_post_position', $bb_post->post_position, $bb_post->post_id );
}

function post_position_link( $topic_id = 0, $position = 1 ) {
	echo apply_filters( 'post_position_link', get_post_position_link( $topic_id, $position ), get_topic_id( $topic_id ), (integer) $position );
}

function get_post_position_link( $topic_id = 0, $position = 1 ) {
	$position = (integer) $position;
	$bb_topic = get_topic( get_topic_id( $topic_id ) );
	if ( $bb_topic->topic_posts < $position ) {
		return;
	}
	$page = bb_get_page_number( $position );
	return apply_filters( 'get_post_position_link', get_topic_link( $bb_post->topic_id, $page ) . "#position-$position", $bb_topic->topic_id, $position );
}

function bb_post_meta( $key, $post_id = 0 ) {
	echo bb_get_post_meta( $key, $post_id );
}

function bb_get_post_meta( $key, $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	if ( isset($bb_post->$key) )
		return $bb_post->$key;
}


function post_author( $post_id = 0 ) {
	echo apply_filters('post_author', get_post_author( $post_id ), $post_id );
}

function get_post_author( $post_id = 0 ) {
	if ( $user = bb_get_user( get_post_author_id( $post_id ) ) )
		return apply_filters( 'get_post_author', $user->display_name, $user->ID, $post_id );
	elseif ( $title = bb_get_post_meta( 'pingback_title', $post_id ) )
		return apply_filters( 'bb_get_pingback_title', $title, $post_id );
	elseif ( $title = bb_get_post_meta( 'post_author', $post_id ) )
		return apply_filters( 'get_post_author', $title, 0, $post_id );
	else
		return apply_filters( 'get_post_author', __('Anonymous'), 0, $post_id );
}

function post_author_link( $post_id = 0 ) {
	if ( $link = ( bb_get_option( 'name_link_profile' ) ? get_user_profile_link( get_post_author_id( $post_id ) ) : get_user_link( get_post_author_id( $post_id ) ) ) ) {
		echo '<a href="' . esc_attr( $link ) . '">' . get_post_author( $post_id ) . '</a>';
	} elseif ( $link = bb_get_post_meta( 'pingback_uri' )) {
		echo '<a href="' . esc_attr( $link ) . '">' . get_post_author( $post_id ) . '</a>';
	} elseif ( $link = bb_get_post_meta( 'post_url' ) ) {
		echo '<a href="' . esc_attr( $link ) . '">' . get_post_author( $post_id ) . '</a>';
	} else {
		post_author( $post_id );
	}
}

function post_author_avatar( $size = '48', $default = '', $post_id = 0 ) {
	if ( ! bb_get_option('avatars_show') )
		return false;
	
	$author_id = get_post_author_id( $post_id );
	echo bb_get_avatar( $author_id, $size, $default );
}

function post_author_avatar_link( $size = '48', $default = '', $post_id = 0 ) {
	if ( ! bb_get_option('avatars_show') )
		return false;
	
	$author_id = get_post_author_id( $post_id );
	if ( $link = get_user_link( $author_id ) ) {
		echo '<a href="' . esc_attr( $link ) . '">' . bb_get_avatar( $author_id, $size, $default ) . '</a>';
	} else {
		echo bb_get_avatar( $author_id, $size, $default );
	}
}

function post_text( $post_id = 0 ) {
	echo apply_filters( 'post_text', get_post_text( $post_id ), get_post_id( $post_id ) );
}

function get_post_text( $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	return apply_filters( 'get_post_text', $bb_post->post_text, $bb_post->post_id );
}

function bb_post_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );
	$time = apply_filters( 'bb_post_time', bb_get_post_time( array('format' => 'mysql') + $args ), $args );
	echo _bb_time_function_return( $time, $args );
}

function bb_get_post_time( $args = '' ) {
	$args = _bb_parse_time_function_args( $args );

	$bb_post = bb_get_post( get_post_id( $args['id'] ) );

	$time = apply_filters( 'bb_get_post_time', $bb_post->post_time, $args );

	return _bb_time_function_return( $time, $args );
}

function post_ip( $post_id = 0 ) {
	if ( bb_current_user_can( 'view_by_ip' ) )
		echo apply_filters( 'post_ip', get_post_ip( $post_id ), get_post_id( $post_id ) );
}

function get_post_ip( $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	return apply_filters( 'get_post_ip', $bb_post->poster_ip, $bb_post->post_id );
}

function bb_post_admin( $args = null )
{
	$defaults = array(
		'post_id' => 0,
		'before' => '',
		'after' => '',
		'before_each' => '',
		'after_each' => "\n",
		'each' => array(
			'ip' => array(
				'post_id' => 0
			),
			'edit' => array(
				'post_id' => 0
			),
			'delete' => array(
				'post_id' => 0
			),
			'undelete' => array(
				'post_id' => 0
			)
		)
	);
	if ( isset( $args['each'] ) ) {
		$each_args = $args['each'];
		$_each_args = $defaults['each'];
		foreach ( $each_args as $_part_name => $_part_args ) {
			if ( !isset( $defaults['each'][$_part_name] ) ) {
				continue;
			}
			$_each_args[$_part_name] = wp_parse_args( $_part_args, $defaults['each'][$_part_name] );
		}
	}
	$args = wp_parse_args( $args, $defaults );
	if ( isset( $_each_args ) ) {
		$args['each'] = $_each_args;
	}

	$parts = array();
	if ( is_array( $args['each'] ) && count( $args['each'] ) ) {
		foreach ( $args['each'] as $_part_name => $_part_args ) {
			if ( $args['post_id'] && !$_part_args['post_id'] ) {
				$_part_args['post_id'] = $args['post_id'];
			}
			if ( $args['before_each'] && !$_part_args['before'] ) {
				$_part_args['before'] = $args['before_each'];
			}
			if ( $args['after_each'] && !$_part_args['after'] ) {
				$_part_args['after'] = $args['after_each'];
			}
			$_part_function = 'bb_get_post_' . $_part_name . '_link';
			$parts[$_part_name] = $_part_function( $_part_args );
		}

		// For the benefit of filters, mark the final part
		if ( !isset( $args['last_each'] ) ) {
			$args['last_each'] = $_part_args;
		}
	}

	$parts = apply_filters( 'bb_post_admin', $parts, $args );

	if ( !count( $parts ) ) {
		return;
	}

	echo $args['before'] . join( '', $parts ) . $args['after'];
}

function post_ip_link( $args = null )
{
	echo bb_get_post_ip_link( $args );
}

function bb_get_post_ip_link( $args = null )
{
	if ( !bb_current_user_can( 'view_by_ip' ) ) {
		return;
	}

	$defaults = array(
		'post_id' => 0,
		'before' => '',
		'after' => '',
		'text' => '%s'
	);
	if ( is_numeric( $args ) ) {
		$args = array( 'post_id' => $args );
	}
	$args = wp_parse_args( $args, $defaults );

	$bb_post = bb_get_post( get_post_id( $args['post_id'] ) );

	$uri = bb_get_uri( 'bb-admin/posts.php', array( 'poster_ip' => get_post_ip( $bb_post->post_id ) ), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN );

	// Make sure that the last tag in $before gets a class (if it's there)
	if ( preg_match( '/.*(<[^>]+>)[^<]*/', $args['before'], $_node ) ) {
		if ( preg_match( '/class=(\'|")(.*)\1/U', $_node[1], $_class ) ) {
			$args['before'] = str_replace( $_class[0], 'class=' . $_class[1] . 'before-post-ip-link ' . $_class[2] . $_class[1], $args['before'] );
		} else {
			$args['before'] = preg_replace( '/(.*)<([a-z0-9_-]+)(\s?)([^>]*)>([^<]*)/i', '$1<$2 class="before-post-ip-link"$3$4>$5', $args['before'], 1 );
		}
	}

	$link = $args['before'] . '<a class="post-ip-link" href="' . esc_attr( $uri ) . '">' . esc_html( sprintf( $args['text'], get_post_ip( $bb_post->post_id ) ) ) . '</a>' . $args['after'];
	return apply_filters( 'post_ip_link', $link, $bb_post->post_id, $args );
}

function post_edit_link( $args = null )
{
	echo bb_get_post_edit_link( $args );
}

function bb_get_post_edit_link( $args = null )
{
	$defaults = array(
		'post_id' => 0,
		'before' => '',
		'after' => '',
		'text' => __( 'Edit' )
	);
	if ( is_numeric( $args ) ) {
		$args = array( 'post_id' => $args );
	}
	$args = wp_parse_args( $args, $defaults );

	$bb_post = bb_get_post( get_post_id( $args['post_id'] ) );

	if ( bb_current_user_can( 'edit_post', $bb_post->post_id ) ) {
		$uri = bb_get_uri( 'edit.php', array( 'id' => $bb_post->post_id ) );

		// Make sure that the last tag in $before gets a class (if it's there)
		if ( preg_match( '/.*(<[^>]+>)[^<]*/', $args['before'], $_node ) ) {
			if ( preg_match( '/class=(\'|")(.*)\1/U', $_node[1], $_class ) ) {
				$args['before'] = str_replace( $_class[0], 'class=' . $_class[1] . 'before-post-edit-link ' . $_class[2] . $_class[1], $args['before'] );
			} else {
				$args['before'] = preg_replace( '/(.*)<([a-z0-9_-]+)(\s?)([^>]*)>([^<]*)/i', '$1<$2 class="before-post-edit-link"$3$4>$5', $args['before'], 1 );
			}
		}

		$r = $args['before'] . '<a class="post-edit-link" href="' . esc_attr( apply_filters( 'post_edit_uri', $uri, $bb_post->post_id, $args ) ) . '">' . esc_html( $args['text'] ) . '</a>' . $args['after'];
		return apply_filters( 'bb_get_post_edit_link', $r, $bb_post->post_id, $args );
	}
}

function post_del_class( $post_id = 0 )
{
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	$classes = array();
	if ( bb_get_post_meta( 'pingback_uri', $post_id ) ) {
		$classes[] = 'pingback';
	}
	if ( $bb_post->post_status == 1 ) {
		$classes[] = 'deleted';
	} elseif ( $bb_post->post_status != 0 ) {
		$classes[] = 'post-status-' . $bb_post->post_status;
	}
	if ( count( $classes ) ) {
		$classes = join( ' ', $classes );
	} else {
		$classes = '';
	}
	return apply_filters( 'post_del_class', $classes, $bb_post->post_id, $bb_post );
}

function post_delete_link( $args = null )
{
	echo bb_get_post_delete_link( $args );
}

function bb_get_post_delete_link( $args = null )
{
	$defaults = array(
		'post_id' => 0,
		'before' => '',
		'after' => '',
		'text' => __( 'Delete' ),
		'redirect' => true
	);
	if ( is_numeric( $args ) || is_object( $args ) ) {
		$args = array( 'post_id' => $args );
	}
	if ( isset( $args['delete_text'] ) && ( !isset( $args['text'] ) || !$args['text'] ) ) {
		$args['text'] = $args['delete_text'];
	}

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	$bb_post = bb_get_post( get_post_id( $post_id ) );
	//if ( bb_is_first( $bb_post->post_id ) ) {
	//	$topic = get_topic( $bb_post->topic_id );
	//	if ( 2 > $topic->topic_posts ) {
			// Should delete the whole topic
	//		return;
	//	}
	//}
	
	if ( !bb_current_user_can( 'delete_post', $bb_post->post_id ) ) {
		return;
	}

	if ( true === $redirect ) {
		$redirect = $_SERVER['REQUEST_URI'];
	}

	$uri = bb_get_uri('bb-admin/delete-post.php', array(
		'id' => $bb_post->post_id,
		'status' => 1,
		'_wp_http_referer' => $redirect ? rawurlencode( $redirect ) : false
	), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri = esc_url( bb_nonce_url( $uri, 'delete-post_' . $bb_post->post_id ) );

	if ( ( bb_is_admin() || isset( $_GET['view'] ) && 'all' == $_GET['view'] ) ) {
		$ajax_class = 'dim:thread:post-' . $bb_post->post_id . ':deleted:FF3333:FFFF33:action=delete-post&amp;status=1';
	} else {
		$ajax_class = 'delete:thread:post-' . $bb_post->post_id . '::status=1';
	}

	$text = esc_html( $text );

	// Make sure that the last tag in $before gets a class (if it's there)
	if ( preg_match( '/.*(<[^>]+>)[^<]*/', $before, $_node ) ) {
		if ( preg_match( '/class=(\'|")(.*)\1/U', $_node[1], $_class ) ) {
			$before = str_replace( $_class[0], 'class=' . $_class[1] . 'before-post-delete-link ' . $_class[2] . $_class[1], $before );
		} else {
			$before = preg_replace( '/(.*)<([a-z0-9_-]+)(\s?)([^>]*)>([^<]*)/i', '$1<$2 class="before-post-delete-link"$3$4>$5', $before, 1 );
		}
	}

	$r = $before . '<a href="' . $uri . '" class="' . $ajax_class . ' post-delete-link">' . $text . '</a>' . $after;
	$r = apply_filters( 'post_delete_link', $r, $bb_post->post_status, $bb_post->post_id, $args );
	return $r;
}

function bb_post_undelete_link( $args = null )
{
	echo bb_get_post_undelete_link( $args );
}

function bb_get_post_undelete_link( $args = null )
{
	$defaults = array(
		'post_id' => 0,
		'before' => '',
		'after' => '',
		'text' => __( 'Undelete' ),
		'redirect' => true
	);
	if ( is_numeric( $args ) || is_object( $args ) ) {
		$args = array( 'post_id' => $args );
	}

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	$bb_post = bb_get_post( get_post_id( $post_id ) );

	if ( !bb_current_user_can( 'delete_post', $bb_post->post_id ) ) {
		return;
	}

	if ( true === $redirect ) {
		$redirect = $_SERVER['REQUEST_URI'];
	}

	$uri = bb_get_uri('bb-admin/delete-post.php', array(
		'id' => $bb_post->post_id,
		'status' => 0,
		'view' => 'all',
		'_wp_http_referer' => $redirect ? rawurlencode( $redirect ) : false
	), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$uri = esc_url( bb_nonce_url( $uri, 'delete-post_' . $bb_post->post_id ) );

	$ajax_class = 'dim:thread:post-' . $bb_post->post_id . ':deleted:FF3333:FFFF33:action=delete-post&amp;status=0';

	$text = esc_html( $text );

	// Make sure that the last tag in $before gets a class (if it's there)
	if ( preg_match( '/.*(<[^>]+>)[^<]*/', $before, $_node ) ) {
		if ( preg_match( '/class=(\'|")(.*)\1/U', $_node[1], $_class ) ) {
			$before = str_replace( $_class[0], 'class=' . $_class[1] . 'before-post-undelete-link ' . $_class[2] . $_class[1], $before );
		} else {
			$before = preg_replace( '/(.*)<([a-z0-9_-]+)(\s?)([^>]*)>([^<]*)/i', '$1<$2 class="before-post-undelete-link"$3$4>$5', $before, 1 );
		}
	}

	$r = $before . '<a href="' . $uri . '" class="' . $ajax_class . ' post-undelete-link">' . $text . '</a>' . $after;
	$r = apply_filters( 'post_undelete_link', $r, $bb_post->post_status, $bb_post->post_id, $args );
	return $r;
}

function post_author_id( $post_id = 0 ) {
	echo apply_filters( 'post_author_id', get_post_author_id( $post_id ), get_post_id( $post_id ) );
}

function get_post_author_id( $post_id = 0 ) {
	$bb_post = bb_get_post( get_post_id( $post_id ) );
	return apply_filters( 'get_post_author_id', (int) $bb_post->poster_id, get_post_id( $post_id ) );
}

function post_author_title( $post_id = 0 ) {
	echo apply_filters( 'post_author_title', get_post_author_title( $post_id ), get_post_id( $post_id ) );
}

function get_post_author_title( $post_id = 0 ) {
	return get_user_title( get_post_author_id( $post_id ) );
}

function post_author_title_link( $post_id = 0 ) {
	echo apply_filters( 'post_author_title_link', get_post_author_title_link( $post_id ), get_post_id( $post_id ) );
}

function get_post_author_title_link( $post_id = 0 ) {
	$title = get_post_author_title( $post_id );
	if ( false === $title ) {
		if ( bb_get_post_meta( 'pingback_uri', $post_id ) )
			$r = __('PingBack');
		else
			$r = __('Unregistered'); // This should never happen
	} else {
		if ( $link = bb_get_option( 'name_link_profile' ) ? get_user_link( get_post_author_id( $post_id ) ) : get_user_profile_link( get_post_author_id( $post_id ) ) )
			$r = '<a href="' . esc_attr( $link ) . '">' . $title . '</a>';
		else
			$r = $title;
	}

	return apply_filters( 'get_post_author_title_link', $r, get_post_id( $post_id ) );
}

function post_author_type( $post_id = 0 ) {
	$id = get_post_author_id( $post_id );
	$type = get_user_type( $id );
	if ( false === $type ) {
		if ( bb_get_post_meta( 'pingback_uri', $post_id ) )
			$r = __('PingBack');
		else
			$r = __('Unregistered'); // This should never happen
	} else
		$r = '<a href="' . esc_attr( get_user_profile_link( $id ) ) . '">' . $type . '</a>';

	echo apply_filters( 'post_author_type', $r, $post_id );
}

function allowed_markup( $args = '' ) {
	echo apply_filters( 'allowed_markup', get_allowed_markup( $args ) );
}

// format=list or array( 'format' => 'list' )
function get_allowed_markup( $args = '' ) {
	$args = wp_parse_args( $args, array('format' => 'flat') );
	extract($args, EXTR_SKIP);

	$tags = bb_allowed_tags();
	unset($tags['pre'], $tags['br']);
	$tags = array_keys($tags);

	switch ( $format ) :
	case 'array' :
		$r = $tags;
		break;
	case 'list' :
		$r = "<ul class='allowed-markup'>\n\t<li>";
		$r .= join("</li>\n\t<li>", $tags);
		$r .= "</li>\n</ul>\n";
		break;
	default :
		$r = join(' ', $tags);
		break;
	endswitch;
	return apply_filters( 'get_allowed_markup', $r, $format );
}

// USERS
function bb_get_user_id( $id = 0 ) {
	global $user;
	if ( is_object($id) && isset($id->ID) )
		return (int) $id->ID;
	elseif ( !$id )
		return $user->ID;

	$_user = bb_get_user( (int) $id );
	return isset($_user->ID) ? $_user->ID : 0;
}

function user_profile_link( $id = 0 , $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}
	echo apply_filters( 'user_profile_link', get_user_profile_link( $id ), bb_get_user_id( $id ), $context );
}

function get_user_profile_link( $id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	$user = bb_get_user( bb_get_user_id( $id ) );
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}
	
	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'user_nicename';
		} else {
			$column = 'ID';
		}
		$page = (1 < $page) ? '/page/' . $page : '';
		$r = bb_get_uri('profile/' . $user->$column . $page, null, $context);
	} else {
		$query = array(
			'id' => $user->ID,
			'page' => (1 < $page) ? $page : false
		);
		$r = bb_get_uri('profile.php', $query, $context);
	}
	return apply_filters( 'get_user_profile_link', $r, $user->ID, $context );
}

function user_delete_button() {
	global $user;
	
	$user_obj = new BP_User( $user->ID );
	if ( !bb_current_user_can( 'keep_gate' ) && 'keymaster' == $user_obj->roles[0] )
		return;
	
	if ( bb_current_user_can( 'edit_users' ) && bb_get_current_user_info( 'id' ) != (int) $user->ID )
		echo apply_filters( 'user_delete_button', get_user_delete_button() );
}

function get_user_delete_button() {
	$r  = '<input type="submit" class="delete" name="delete-user" value="' . __('Delete User &raquo;') . '" ';
	$r .= 'onclick="return confirm(\'' . esc_js(__('Are you sure you want to delete this user?')) . '\')" />';
	return apply_filters( 'get_user_delete_button', $r);
}

function profile_tab_link( $id = 0, $tab, $page = 1 ) {
	echo apply_filters( 'profile_tab_link', get_profile_tab_link( $id, $tab ) );
}

function get_profile_tab_link( $id = 0, $tab, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	$user = bb_get_user( bb_get_user_id( $id ) );

	$tab = bb_sanitize_with_dashes($tab);

	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}

	if ( $tab === 'edit' && !( $context & BB_URI_CONTEXT_BB_USER_FORMS ) ) {
		$context += BB_URI_CONTEXT_BB_USER_FORMS;
	}

	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'user_nicename';
		} else {
			$column = 'ID';
		}
		$page = (1 < $page) ? '/page/' . $page : '';
		$r = bb_get_uri('profile/' . $user->$column . '/' . $tab . $page, null, $context);
	} else {
		$query = array(
			'id' => $user->ID,
			'tab' => $tab,
			'page' => (1 < $page) ? $page : false
		);
		$r = bb_get_uri('profile.php', $query, $context);
	}
	return apply_filters( 'get_profile_tab_link', $r, $user->ID, $context );
}

function user_link( $id = 0 ) {
	echo apply_filters( 'user_link', get_user_link( $id ), $id );
}

function get_user_link( $id = 0 ) {
	if ( $user = bb_get_user( bb_get_user_id( $id ) ) )
		return apply_filters( 'get_user_link', $user->user_url, $user->ID );
}

function full_user_link( $id = 0 ) {
	echo get_full_user_link( $id );
}

function get_full_user_link( $id = 0 ) {
	if ( get_user_link( $id ) )
		$r = '<a href="' . esc_attr( get_user_link( $id ) ) . '">' . get_user_display_name( $id ) . '</a>';
	else
		$r = get_user_display_name( $id );
	return $r;
}

function user_type_label( $type ) {
	echo apply_filters( 'user_type_label', get_user_type_label( $type ), $type );
}

function get_user_type_label( $type ) {
	global $wp_roles;
	if ( $wp_roles->is_role( $type ) )
		return apply_filters( 'get_user_type_label', $wp_roles->role_names[$type], $type );
}

function user_type( $id = 0 ) {
	echo apply_filters( 'user_type', get_user_type( $id ), $id );
}

function get_user_type( $id = 0 ) {
	if ( $user = bb_get_user( bb_get_user_id( $id ) ) ) :
		@$caps = array_keys($user->capabilities);
		if ( !$caps )
			$caps[] = 'inactive';

		$type = get_user_type_label( $caps[0] ); //Just support one role for now.
	else :
		$type = false;
	endif;
	return apply_filters( 'get_user_type', $type, $user->ID );
}

function get_user_name( $id = 0 ) {
	$user = bb_get_user( bb_get_user_id( $id ) );
	return apply_filters( 'get_user_name', $user->user_login, $user->ID );
}

function get_user_display_name( $id = 0 ) {
	$user = bb_get_user( bb_get_user_id( $id ) );
	return apply_filters( 'get_user_display_name', $user->display_name, $user->ID );
}

function user_title( $id = 0 ) {
	echo apply_filters( 'user_title', get_user_title( $id ), bb_get_user_id( $id ) );
}

function get_user_title( $id = 0 ) {
	$user = bb_get_user( bb_get_user_id( $id ) );
	return empty( $user->title ) ? get_user_type( $id ) : apply_filters( 'get_user_title', $user->title, $user->ID );
}

function profile_pages( $args = null )
{
	$defaults = array( 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page, $user;
	$add = apply_filters( 'profile_pages_add', $add );
	if ( $pages = apply_filters( 'profile_pages', get_page_number_links( $page, $user->topics_replied + $add ), $user->user_id ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

function bb_profile_data( $id = 0 ) {
	if ( !$user = bb_get_user( bb_get_user_id( $id ) ) )
		return;

	$reg_time = bb_gmtstrtotime( $user->user_registered );
	$profile_info_keys = bb_get_profile_info_keys();
	echo "<dl id='userinfo'>\n";
	echo "\t<dt>" . __('Member Since') . "</dt>\n";
	echo "\t<dd>" . bb_datetime_format_i18n($reg_time, 'date') . ' (' . bb_since($reg_time) . ")</dd>\n";
	if ( is_array( $profile_info_keys ) ) {
		foreach ( $profile_info_keys as $key => $label ) {
			if ( in_array($key, array('first_name', 'last_name', 'display_name')) || !isset($user->$key) )
				continue;
			$val = 'user_url' == $key ? get_user_link( $user->ID ) : $user->$key;
			if (
				( 'user_email' != $key || ( 'user_email' == $key && bb_current_user_can( 'edit_users' ) ) )
				&& $val
				&& 'http://' != $val
			) {
				echo "\t<dt>{$label[1]}</dt>\n";
				$val = make_clickable( $val );
				$attributes = array();
				if (isset($label[2]) && !empty($label[2]))
					if (preg_match("#^<a#i", $val))
						$val = preg_replace("#^<a#i", '<a class="' . esc_attr($label[2]) . '"', $val);
					else
						$val = '<span class="' . esc_attr($label[2]) . '">' . $val . '</span>';
				
				echo "\t<dd>" . $val . "</dd>\n";
			}
		}
	}
	echo "</dl>\n";
}

function bb_profile_base_content() {
	global $self;
	if ( !is_callable( $self ) )
		return; // should never happen
	call_user_func( $self );
}

function bb_profile_data_form( $id = 0 ) {
	global $errors;
	if ( !$user = bb_get_user( bb_get_user_id( $id ) ) )
		return;

	if ( !bb_current_user_can( 'edit_user', $user->ID ) )
		return;

	$error_codes = $errors->get_error_codes();
	$profile_info_keys = bb_get_profile_info_keys();
	$required = false;
	if ( in_array( 'delete', $error_codes ) )
		echo '<div class="form-invalid error">' . $errors->get_error_message( 'delete' ) . '</div>';
?>
<table id="userinfo">
<?php
	if ( is_array($profile_info_keys) ) :
		$bb_current_id = bb_get_current_user_info( 'id' );
		foreach ( $profile_info_keys as $key => $label ) :
			if ( $label[0] ) {
				$class = 'form-field form-required required';
				$required = true;
			} else {
				$class = 'form-field';
			}
			$title = esc_attr( $label[1] );

			$name = esc_attr( $key );
			$type = isset($label[2]) ? esc_attr( $label[2] ) : 'text';
			if ( !in_array( $type, array( 'checkbox', 'file', 'hidden', 'image', 'password', 'radio', 'text' ) ) ) {
				$type = 'text';
			}

			$checked = false;
			if ( in_array( $key, $error_codes ) ) {
				$class .= ' form-invalid error';
				$data = $errors->get_error_data( $key );
				if ( 'checkbox' == $type ) {
					if ( isset($data['data']) )
						$checked = $data['data'];
					else
						$checked = $_POST[$key];
					$value = $label[3];
					$checked = $checked == $value;
				} else {
					if ( isset($data['data']) )
						$value = $data['data'];
					else
						$value = $_POST[$key];
				}

				$message = esc_html( $errors->get_error_message( $key ) );
				$message = "<em>$message</em>";
			} else {
				if ( 'checkbox' == $type ) {
					$checked = $user->$key == $label[3] || $label[4] == $label[3];
					$value = $label[3];
				} else {
					$value = isset($user->$key) ? $user->$key : '';
				}
				$message = '';
			}

			$checked = $checked ? ' checked="checked"' : '';
			$value = esc_attr( $value );

?>

<tr class="<?php echo $class; ?>">
	<th scope="row">
		<label for="<?php echo $name; ?>"><?php echo $title; ?></label>
		<?php echo $message; ?>
	</th>
	<td>
<?php
			if ($key == 'display_name') {
?>
		<select name="display_name" id="display_name">
<?php
				$public_display = array();
				$public_display['display_displayname'] = $user->display_name;
				//$public_display['display_nickname'] = $user->nickname;
				$public_display['display_username'] = $user->user_login;
				if ( isset($user->first_name) ) {
					$public_display['display_firstname'] = $user->first_name;
					if ( isset($user->last_name) ) {
						$public_display['display_firstlast'] = $user->first_name.' '.$user->last_name;
						$public_display['display_lastfirst'] = $user->last_name.' '.$user->first_name;
					}
				}
				if ( isset($user->last_name) )
					$public_display['display_lastname'] = $user->last_name;
				
				$public_display = array_unique(array_filter(array_map('trim', $public_display)));
				
				foreach($public_display as $id => $item) {
?>
			<option id="<?php echo esc_attr( $id ); ?>" value="<?php echo esc_attr( $item ); ?>"><?php echo esc_html( $item ); ?></option>
<?php
				}
?>
		</select>
<?php
			} else {
?>
		<?php if ( 'checkbox' == $type && isset($label[5]) ) echo '<label for="' . $name . '">'; ?>
		<input name="<?php echo $name; ?>" id="<?php echo $name; ?>" type="<?php echo $type; ?>"<?php echo $checked; ?> value="<?php echo $value; ?>" />
		<?php if ( 'checkbox' == $type && isset($label[5]) ) echo esc_html( $label[5] ) . '</label>'; ?>
<?php
			}
?>
	</td>
</tr>

<?php endforeach; endif; // $profile_info_keys; $profile_info_keys ?>

</table>

<?php bb_nonce_field( 'edit-profile_' . $user->ID ); if ( $required ) : ?>

<p class="required-message"><?php _e('These items are <span class="required">required</span>.') ?></p>

<?php
	endif;
	do_action( 'extra_profile_info', $user->ID );
}

function bb_profile_admin_form( $id = 0 ) {
	global $wp_roles, $errors;
	if ( !$user = bb_get_user( bb_get_user_id( $id ) ) )
		return;

	if ( !bb_current_user_can( 'edit_user', $user->ID ) )
		return;

	$error_codes = $errors->get_error_codes();
	$bb_current_id = bb_get_current_user_info( 'id' );

	$profile_admin_keys = bb_get_profile_admin_keys();
	$assignable_caps = bb_get_assignable_caps();
	$required = false;

	$roles = $wp_roles->role_names;
	$can_keep_gate = bb_current_user_can( 'keep_gate' );

	// Keymasters can't demote themselves
	if ( ( $bb_current_id == $user->ID && $can_keep_gate ) || ( isset( $user->capabilities ) && is_array( $user->capabilities ) && array_key_exists( 'keymaster', $user->capabilities ) && !$can_keep_gate ) ) {
		$roles = array( 'keymaster' => $roles['keymaster'] );
	} elseif ( !$can_keep_gate ) { // only keymasters can promote others to keymaster status
		unset($roles['keymaster']);
	}

	$selected = array( 'inactive' => ' selected="selected"' );
?>
<table id="admininfo">
<tr class='form-field<?php if ( in_array( 'role', $error_codes ) ) echo ' form-invalid error'; ?>'>
	<th scope="row">
		<label for="admininfo_role"><?php _e('User Type'); ?></label>
		<?php if ( in_array( 'role', $error_codes ) ) echo '<em>' . $errors->get_error_message( 'role' ) . '</em>'; ?>
	</th>
	<td>
		<select id="admininfo_role" name="role">
<?php
	foreach( $roles as $r => $n ) {
		if ( isset( $user->capabilities ) && is_array( $user->capabilities ) && array_key_exists( $r, $user->capabilities ) ) {
			$selected['inactive'] = '';
			$selected[$r] = ' selected="selected"';
		} elseif ( $r !== 'inactive' ) {
			$selected[$r] = '';
		}
?>
			<option value="<?php echo $r; ?>"<?php echo $selected[$r]; ?>><?php echo $n; ?></option>
<?php
	}
?>
		</select>
	</td>
</tr>
<?php
	if (count($assignable_caps)) :
?>
<tr class="extra-caps-row">
	<th scope="row"><?php _e('Allow this user to'); ?></th>
	<td>
<?php
	foreach( $assignable_caps as $cap => $label ) :
		$name = esc_attr( $cap );
		$checked = '';
		if ( isset( $user->capabilities ) && is_array( $user->capabilities ) && array_key_exists( $cap, $user->capabilities ) ) {
			$checked = ' checked="checked"';
		}
		$label = esc_html( $label );
?>

		<label><input name="<?php echo $name; ?>" value="1" type="checkbox"<?php echo $checked; ?> /> <?php echo $label; ?></label><br />

<?php endforeach; ?>

	</td>
</tr>

<?php
	endif;
	
	if ( is_array($profile_admin_keys) ) :
		foreach ( $profile_admin_keys as $key => $label ) :
			if ( $label[0] ) {
				$class = 'form-field form-required required';
				$required = true;
			} else {
				$class = 'form-field';
			}
			$title = esc_attr( $label[1] );

			$name = esc_attr( $key );
			$type = isset($label[2]) ? esc_attr( $label[2] ) : 'text';

			$checked = false;
			if ( in_array( $key, $error_codes ) ) {
				$class .= ' form-invalid error';
				$data = $errors->get_error_data( $key );
				if ( 'checkbox' == $type ) {
					if ( isset($data['data']) )
						$checked = $data['data'];
					else
						$checked = $_POST[$key];
					$value = $label[3];
					$checked = $checked == $value;
				} else {
					if ( isset($data['data']) )
						$value = $data['data'];
					else
						$value = $_POST[$key];
				}

				$message = esc_html( $errors->get_error_message( $key ) );
				$message = "<em>$message</em>";
			} else {
				if ( 'checkbox' == $type ) {
					$checked = $user->$key == $label[3] || $label[4] == $label[3];
					$value = $label[3];
				} else {
					$value = isset($user->$key) ? $user->$key : '';
				}
				$message = '';
			}

			$checked = $checked ? ' checked="checked"' : '';
			$value = esc_attr( $value );

?>

<tr class="<?php echo $class; ?>">
	<th scope="row">
		<label for="<?php echo $name; ?>"><?php echo $title ?></label>
		<?php echo $message; ?>
	</th>
	<td>
		<?php if ( 'checkbox' == $type && isset($label[5]) ) echo "<label for='$name'>"; ?>
		<input name="<?php echo $name; ?>" id="<?php echo $name; ?>" type="<?php echo $type; ?>"<?php echo $checked; ?> value="<?php echo $value; ?>" />
		<?php if ( 'checkbox' == $type && isset($label[5]) ) echo esc_html( $label[5] ) . "</label>"; ?>
	</td>
</tr>

<?php endforeach; endif; // $profile_admin_keys; $profile_admin_keys ?>

</table>

<?php if ( $required ) : ?>
<p class="required-message"><?php _e('These items are <span class="required">required</span>.') ?></p>

<?php endif; ?>
<p><?php _e('Inactive users can login and look around but not do anything. Blocked users just see a simple error message when they visit the site.'); ?></p>
<p><?php _e('<strong>Note</strong>: Blocking a user does <em>not</em> block any IP addresses.'); ?></p>
<?php
}

function bb_profile_password_form( $id = 0 ) {
	global $errors;
	if ( !$user = bb_get_user( bb_get_user_id( $id ) ) )
		return;

	if ( !bb_current_user_can( 'change_user_password', $user->ID ) )
		return;

	$class = 'form-field';

	if ( $message = $errors->get_error_message( 'pass' ) ) {
		$class .= ' form-invalid error';
		$message = '<em>' . esc_html( $message ) . '</em>';
	}
?>

<table>
<tr class="<?php echo $class; ?>">
	<th scope="row" rowspan="2">
		<label for="pass1"><?php _e('New password'); ?></label>
		<?php echo $message; ?>
	</th>
	<td>
		<input name="pass1" type="password" id="pass1" autocomplete="off" />
	</td>
</tr>
<tr class="<?php echo $class; ?>">
	<td>
		<input name="pass2" type="password" id="pass2" autocomplete="off" />
	</td>
</tr>
<tr class="pass-strength">
	<th scope="row"><?php _e('Password Strength'); ?></th>
	<td>
		<input type="hidden" name="user_login" id="user_login" value="<?php echo $user->user_login; ?>" />
		<noscript>
			<?php _e('Disabled (requires JavaScript)'); ?>
		</noscript>
		<script type="text/javascript" charset="utf-8">
			if (typeof jQuery != 'undefined') {
				document.writeln('<div id="pass-strength-result">' + pwsL10n.short + '</div>');
			} else {
				document.writeln('<?php echo str_replace("'", "\'", __('Disabled.')); ?>')
			}
		</script>
	</td>
</tr>
</table>

<p><?php _e('Hint: Use upper and lower case characters, numbers and symbols like !"?$%^&amp;( in your password.'); ?></p>

<?php

}

function bb_logout_link( $args = '' ) {
	echo apply_filters( 'bb_logout_link', bb_get_logout_link( $args ), $args );
}

function bb_get_logout_link( $args = '' ) {
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'text' => $args );

	$defaults = array('text' => __('Log Out'), 'before' => '', 'after' => '', 'redirect' => '');
	$args = wp_parse_args( $args, $defaults );
	extract($args, EXTR_SKIP);

	$query = array( 'action' => 'logout' );
	if ( $redirect ) {
		$query['redirect_to'] = $redirect;
	}

	$uri = esc_attr( bb_get_uri('bb-login.php', $query, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_USER_FORMS) );

	return apply_filters( 'bb_get_logout_link', $before . '<a href="' . $uri . '">' . $text . '</a>' . $after, $args );
}

function bb_admin_link( $args = '' ) {
	if ( !bb_current_user_can( 'moderate' ) )
		return;
	echo apply_filters( 'bb_admin_link', bb_get_admin_link( $args ), $args );
}

function bb_get_admin_link( $args = '' ) {
	if ( !bb_current_user_can( 'moderate' ) )
		return;
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'text' => $args );

	$defaults = array('text' => __('Admin'), 'before' => '', 'after' => '');
	$args = wp_parse_args( $args, $defaults );
	extract($args, EXTR_SKIP);

	$uri = esc_attr( bb_get_uri('bb-admin/', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN) );

	return apply_filters( 'bb_get_admin_link', $before . '<a href="' . $uri . '">' . $text . '</a>' . $after, $args );
}

function bb_get_user_admin_link( $user_id = null ) { 
	if( !$user_id || !bb_current_user_can( 'edit_user', $user_id ) )
		return;

	if( !bb_get_user_id( $user_id ) )
		return;

	$uri = bb_get_uri( 'bb-admin/users.php', array( 'action' => 'edit', 'user_id' => $user_id ) );

	return apply_filters( 'bb_get_user_admin_link', $uri, $user_id );
}

function bb_profile_link( $args = '' ) {
	echo apply_filters( 'bb_profile_link', bb_get_profile_link( $args ), $args );
}

function bb_get_profile_link( $args = '' ) {
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'text' => $args );
	elseif ( is_numeric($args) )
		$args = array( 'id' => $args );

	$defaults = array( 'text' => __('View your profile'), 'before' => '', 'after' => '', 'id' => false );
	$args = wp_parse_args( $args, $defaults );
	extract($args, EXTR_SKIP);

	$id = (int) $id;
	if ( !$id )
		$id = bb_get_current_user_info( 'id' );

	return apply_filters( 'bb_get_profile_link', "$before<a href='" . esc_attr( get_user_profile_link( $id ) ) . "'>$text</a>$after", $args );
}

function bb_current_user_info( $key = '' ) {
	if ( !$key )
		return;
	echo apply_filters( 'bb_current_user_info', bb_get_current_user_info( $key ), $key );
}
	

function bb_get_current_user_info( $key = '' ) {
	if ( !is_string($key) )
		return;
	if ( !$user = bb_get_current_user() ) // Not globalized
		return false;

	switch ( $key ) :
	case '' :
		return $user;
		break;
	case 'id' :
	case 'ID' :
		return (int) $user->ID;
		break;
	case 'name' :
		return get_user_display_name( $user->ID );
		break;
	case 'login' :
	case 'user_login' :
		return get_user_name( $user->ID );
		break;
	case 'email' :
	case 'user_email' :
		return bb_get_user_email( $user->ID );
		break;
	case 'url' :
	case 'uri' :
	case 'user_url' :
		return get_user_link( $user->ID );
		break;
	endswitch;
}

function bb_get_user_email( $id ) {
	if ( !$user = bb_get_user( bb_get_user_id( $id ) ) )
		return false;

	return apply_filters( 'bb_get_user_email', $user->user_email, $id );
}

//TAGS
function topic_tags()
{
	global $tags, $tag, $topic_tag_cache, $user_tags, $other_tags, $topic;
	if ( is_array( $tags ) || bb_current_user_can( 'edit_tag_by_on', bb_get_current_user_info( 'id' ), $topic->topic_id ) ) {
		bb_load_template( 'topic-tags.php', array('user_tags', 'other_tags', 'public_tags') );
	}
}

function bb_tag_page_link()
{
	echo bb_get_tag_page_link();
}

function bb_get_tag_page_link( $context = BB_URI_CONTEXT_A_HREF )
{
	if ( bb_get_option( 'mod_rewrite' ) ) {
		$r = bb_get_uri( 'tags/', null, $context );
	} else {
		$r = bb_get_uri( 'tags.php', null, $context );
	}
	return apply_filters( 'bb_get_tag_page_link', $r, $context );
}

function bb_tag_link( $tag_id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF )
{
	echo apply_filters( 'bb_tag_link', bb_get_tag_link( $tag_id, $page, $context ), $tag_id, $page, $context );
}

function bb_get_tag_link( $tag_id = 0, $page = 1, $context = BB_URI_CONTEXT_A_HREF )
{
	global $tag;

	if ( $tag_id ) {
		if ( is_object( $tag_id ) ) {
			$_tag = $tag_id;
		} else {
			$_tag = bb_get_tag( $tag_id );
		}
	} else {
		$_tag =& $tag;
	}

	if ( !is_object( $_tag ) ) {
		return '';
	}

	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF;
	}

	if ( bb_get_option( 'mod_rewrite' ) ) {
		$page = (1 < $page) ? '/page/' . $page : '';
		$r = bb_get_uri( 'tags/' . $_tag->tag . $page, null, $context );
	} else {
		$query = array(
			'tag' => $_tag->tag,
			'page' => ( 1 < $page ) ? $page : false
		);
		$r = bb_get_uri( 'tags.php', $query, $context );
	}

	return apply_filters( 'bb_get_tag_link', $r, $_tag->tag, $page, $context );
}

function bb_tag_link_base()
{
	echo bb_get_tag_link_base();
}

function bb_get_tag_link_base()
{
	return bb_get_tag_page_link() . ( bb_get_option( 'mod_rewrite' ) ? '' : '?tag=' );
}

function bb_tag_name( $tag_id = 0 )
{
	echo esc_html( bb_get_tag_name( $tag_id ) );
}

function bb_get_tag_name( $tag_id = 0 ) {
	global $tag;

	if ( $tag_id ) {
		if ( is_object( $tag_id ) ) {
			$_tag = $tag_id;
		} else {
			$_tag = bb_get_tag( $tag_id );
		}
	} else {
		$_tag =& $tag;
	}

	if ( !is_object( $_tag ) ) {
		return '';
	}

	return $_tag->raw_tag;
}

function bb_tag_posts_rss_link( $tag_id = 0, $context = 0 )
{
	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}

	echo apply_filters( 'tag_posts_rss_link', bb_get_tag_posts_rss_link( $tagid, $context ), $tag_id, $context );
}

function bb_get_tag_posts_rss_link( $tag_id = 0, $context = 0 )
{
	global $tag;

	if ( $tag_id ) {
		if ( is_object( $tag_id ) ) {
			$_tag = $tag_id;
		} else {
			$_tag = bb_get_tag( $tag_id );
		}
	} else {
		$_tag =& $tag;
	}

	if ( !is_object( $_tag ) ) {
		return '';
	}

	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}

	if ( bb_get_option( 'mod_rewrite' ) ) {
		$link = bb_get_uri( 'rss/tags/' . $_tag->tag, null, $context );
	} else {
		$link = bb_get_uri( 'rss.php', array( 'tag' => $_tag->tag ), $context );
	}

	return apply_filters( 'get_tag_posts_rss_link', $link, $tag_id, $context );
}

function bb_tag_topics_rss_link( $tag_id = 0, $context = 0 )
{
	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}

	echo apply_filters( 'tag_topics_rss_link', bb_get_tag_topics_rss_link( $tag_id, $context ), $tag_id, $context );
}

function bb_get_tag_topics_rss_link( $tag_id = 0, $context = 0 )
{
	global $tag;

	if ( $tag_id ) {
		if ( is_object( $tag_id ) ) {
			$_tag = $tag_id;
		} else {
			$_tag = bb_get_tag( $tag_id );
		}
	} else {
		$_tag =& $tag;
	}

	if ( !is_object( $_tag ) ) {
		return '';
	}

	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}

	if ( bb_get_option( 'mod_rewrite' ) ) {
		$link = bb_get_uri( 'rss/tags/' . $_tag->tag . '/topics', null, $context );
	} else {
		$link = bb_get_uri( 'rss.php', array('tag' => $_tag->tag, 'topics' => 1 ), $context );
	}

	return apply_filters( 'get_tag_topics_rss_link', $link, $tag_id, $context );
}

function bb_list_tags( $args = null )
{
	$defaults = array(
		'tags' => false,
		'format' => 'list',
		'topic' => 0,
		'list_id' => 'tags-list'
	);

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( !$topic = get_topic( get_topic_id( $topic ) ) ) {
		return false;
	}

	if ( !is_array( $tags ) ) {
		$tags = bb_get_topic_tags( $topic->topic_id );
	}

	if ( !$tags ) {
		return false;
	}

	$list_id = esc_attr( $list_id );

	$r = '';
	switch ( strtolower( $format ) ) {
		case 'table' :
			break;

		case 'list' :
		default :
			$args['format'] = 'list';
			$r .= '<ul id="' . $list_id . '" class="tags-list list:tag">' . "\n";
			foreach ( $tags as $tag ) {
				$r .= _bb_list_tag_item( $tag, $args );
			}
			$r .= '</ul>';
			break;
	}

	echo $r;
}

function _bb_list_tag_item( $tag, $args )
{
	$url = esc_url( bb_get_tag_link( $tag ) );
	$name = esc_html( bb_get_tag_name( $tag ) );
	if ( 'list' == $args['format'] ) {
		$id = 'tag-' . $tag->tag_id . '_' . $tag->user_id;
		return "\t" . '<li id="' . $id . '"' . get_alt_class( 'topic-tags' ) . '><a href="' . $url . '" rel="tag">' . $name . '</a> ' . bb_get_tag_remove_link( array( 'tag' => $tag, 'list_id' => $args['list_id'] ) ) . '</li>' . "\n";
	}
}
	
function tag_form( $args = null )
{
	$defaults = array( 'topic' => 0, 'submit' => __('Add &raquo;'), 'list_id' => 'tags-list' );
	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( !$topic = get_topic( get_topic_id( $topic ) ) ) {
		return false;
	}

	if ( !bb_current_user_can( 'edit_tag_by_on', bb_get_current_user_info( 'id' ), $topic->topic_id ) ) {
		return false;
	}

	global $page;
?>

<form id="tag-form" method="post" action="<?php bb_uri('tag-add.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>" class="add:<?php echo esc_attr( $list_id ); ?>:">
	<p>
		<input name="tag" type="text" id="tag" />
		<input type="hidden" name="id" value="<?php echo $topic->topic_id; ?>" />
		<input type="hidden" name="page" value="<?php echo $page; ?>" />
		<?php bb_nonce_field( 'add-tag_' . $topic->topic_id ); ?>
		<input type="submit" name="submit" id="tagformsub" value="<?php echo esc_attr( $submit ); ?>" />
	</p>
</form>

<?php
}

function manage_tags_forms()
{
	global $tag;
	if ( !bb_current_user_can( 'manage_tags' ) ) {
		return false;
	}

	$form  = '<ul id="manage-tags">' . "\n";
	$form .= '<li id="tag-rename">' . __('Rename tag:') . "\n\t";
	$form .= '<form method="post" action="' . bb_get_uri( 'bb-admin/tag-rename.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ) . '"><div>' . "\n\t";
	$form .= '<input type="text" name="tag" size="10" maxlength="30" />' . "\n\t";
	$form .= '<input type="hidden" name="id" value="' . $tag->tag_id . '" />' . "\n\t";
	$form .= "<input type='submit' name='Submit' value='" . __('Rename') . "' />\n\t";
	echo $form;
	bb_nonce_field( 'rename-tag_' . $tag->tag_id );
	echo "\n\t</div></form>\n  </li>\n ";
	$form  = "<li id='tag-merge'>" . __('Merge this tag into:') . "\n\t";
	$form .= "<form method='post' action='" . bb_get_uri('bb-admin/tag-merge.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN) . "'><div>\n\t";
	$form .= "<input type='text' name='tag' size='10' maxlength='30' />\n\t";
	$form .= "<input type='hidden' name='id' value='$tag->tag_id' />\n\t";
	$form .= "<input type='submit' name='Submit' value='" . __('Merge') . "' ";
	$form .= 'onclick="return confirm(\'' . esc_js( sprintf(__('Are you sure you want to merge the "%s" tag into the tag you specified? This is permanent and cannot be undone.'), $tag->raw_tag) ) . "');\" />\n\t";
	echo $form;
	bb_nonce_field( 'merge-tag_' . $tag->tag_id );
	echo "\n\t</div></form>\n  </li>\n ";
	$form  = "<li id='tag-destroy'>" . __('Destroy tag:') . "\n\t";
	$form .= "<form method='post' action='" . bb_get_uri('bb-admin/tag-destroy.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN) . "'><div>\n\t";
	$form .= "<input type='hidden' name='id' value='$tag->tag_id' />\n\t";
	$form .= "<input type='submit' name='Submit' value='" . __('Destroy') . "' ";
	$form .= 'onclick="return confirm(\'' . esc_js( sprintf(__('Are you sure you want to destroy the "%s" tag? This is permanent and cannot be undone.'), $tag->raw_tag) ) . "');\" />\n\t";
	echo $form;
	bb_nonce_field( 'destroy-tag_' . $tag->tag_id );
	echo "\n\t</div></form>\n  </li>\n</ul>";
}

function bb_tag_remove_link( $args = null ) {
	echo bb_get_tag_remove_link( $args );
}

function bb_get_tag_remove_link( $args = null ) {
	if ( is_scalar($args) || is_object( $args ) )
		$args = array( 'tag' => $args );
	$defaults = array( 'tag' => 0, 'topic' => 0, 'list_id' => 'tags-list' );
	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( is_object( $tag ) && isset( $tag->tag_id ) ); // [sic]
	elseif ( !$tag = bb_get_tag( bb_get_tag_id( $tag ) ) )
		return false;
	if ( !$topic = get_topic( get_topic_id( $topic ) ) )
		return false;
	if ( !bb_current_user_can( 'edit_tag_by_on', $tag->user_id, $topic->topic_id ) )
		return false;
	$url = bb_get_uri('tag-remove.php', array('tag' => $tag->tag_id, 'user' => $tag->user_id, 'topic' => $topic->topic_id) );
	$url = esc_url( bb_nonce_url( $url, 'remove-tag_' . $tag->tag_id . '|' . $topic->topic_id) );
	$title = esc_attr__( 'Remove this tag' );
	$list_id = esc_attr( $list_id );
	return "[<a href='$url' class='delete:$list_id:tag-{$tag->tag_id}_{$tag->user_id}' title='$title'>&times;</a>]";
}

function bb_tag_heat_map( $args = '' ) {
	$defaults = array( 'smallest' => 8, 'largest' => 22, 'unit' => 'pt', 'limit' => 40, 'format' => 'flat' );
	$args = wp_parse_args( $args, $defaults );

	if ( 1 < $fn = func_num_args() ) : // For back compat
		$args['smallest'] = func_get_arg(0);
		$args['largest']  = func_get_arg(1);
		$args['unit']     = 2 < $fn ? func_get_arg(2) : $unit;
		$args['limit']    = 3 < $fn ? func_get_arg(3) : $limit;
	endif;

	extract($args, EXTR_SKIP);

	$tags = bb_get_top_tags( array( 'number' => $limit ) );

	if ( empty($tags) )
		return;

	$r = bb_get_tag_heat_map( $tags, $args );
	echo apply_filters( 'tag_heat_map', $r, $args );
}

function bb_get_tag_heat_map( $tags, $args = '' ) {
	$defaults = array( 'smallest' => 8, 'largest' => 22, 'unit' => 'pt', 'limit' => 45, 'format' => 'flat' );
	$args = wp_parse_args( $args, $defaults );
	extract($args, EXTR_SKIP);

	if ( !$tags )
		return;

	foreach ( (array) $tags as $tag ) {
		$counts{$tag->raw_tag} = $tag->tag_count;
		$taglinks{$tag->raw_tag} = bb_get_tag_link( $tag );
	}

	$min_count = min($counts);
	$spread = max($counts) - $min_count;
	if ( $spread <= 0 )
		$spread = 1;
	$fontspread = $largest - $smallest;
	if ( $fontspread <= 0 )
		$fontspread = 1;
	$fontstep = $fontspread / $spread;

	do_action_ref_array( 'sort_tag_heat_map', array(&$counts) );

	$a = array();

	foreach ( $counts as $tag => $count ) {
		$taglink = esc_attr($taglinks{$tag});
		$tag = str_replace(' ', '&nbsp;', esc_html( $tag ));
		$fontsize = round( $smallest + ( ( $count - $min_count ) * $fontstep ), 1 );
		$a[] = "<a href='$taglink' title='" . esc_attr( sprintf( __('%d topics'), $count ) ) . "' rel='tag' style='font-size:$fontsize$unit;'>$tag</a>";
	}

	switch ( $format ) :
	case 'array' :
		$r =& $a;
		break;
	case 'list' :
		$r = "<ul class='bb-tag-heat-map'>\n\t<li>";
		$r .= join("</li>\n\t<li>", $a);
		$r .= "</li>\n</ul>\n";
		break;
	default :
		$r = join("\n", $a);
		break;
	endswitch;

	return apply_filters( 'bb_get_tag_heat_map', $r, $tags, $args );
}

function bb_sort_tag_heat_map( &$tag_counts ) {
	uksort($tag_counts, 'strnatcasecmp');
}

function tag_pages( $args = null )
{
	$defaults = array( 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page, $tagged_topic_count;
	if ( $pages = apply_filters( 'tag_pages', get_page_number_links( $page, $tagged_topic_count ) ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

function bb_forum_dropdown( $args = '' ) {
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'callback' => $args );
	if ( 1 < func_num_args() )
		$args['callback_args'] = func_get_arg(1);
	echo bb_get_forum_dropdown( $args );
}

function bb_get_forum_dropdown( $args = '' ) {
	$defaults = array( 'callback' => false, 'callback_args' => false, 'id' => 'forum_id', 'none' => false, 'selected' => false, 'tab' => false, 'hierarchical' => 1, 'depth' => 0, 'child_of' => 0, 'disable_categories' => 1, 'options_only' => false );
	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'callback' => $args );
	if ( 1 < func_num_args() )
		$args['callback_args'] = func_get_arg(1);

	$args = wp_parse_args( $args, $defaults );

	extract($args, EXTR_SKIP);

	if ( !bb_forums( $args ) )
		return;

	global $forum_id, $forum;
	$old_global = $forum;

	$name = esc_attr( $id );
	$id = str_replace( '_', '-', $name );
	$tab = (int) $tab;

	if ( $none && 1 == $none )
		$none = __('- None -');

	$r = '';
	if ( !$options_only ) {
		if ( $tab ) {
			$tab = ' tabindex="' . $tab . '"';
		} else {
			$tab = '';
		}
		$r .= '<select name="' . $name . '" id="' . $id . '"' . $tab . '>' . "\n";
	}
	if ( $none )
		$r .= "\n" . '<option value="0">' . $none . '</option>' . "\n";

	$no_option_selected = true;
	$options = array();
	while ( $depth = bb_forum() ) :
		global $forum; // Globals + References = Pain
		$pad_left = str_repeat( '&nbsp;&nbsp;&nbsp;', $depth - 1 );
		if ( $disable_categories && isset($forum->forum_is_category) && $forum->forum_is_category ) {
			$options[] = array(
				'value' => 0,
				'display' => $pad_left . $forum->forum_name,
				'disabled' => true,
				'selected' => false
			);
			continue;
		}
		$_selected = false;
		if ( (!$selected && $forum_id == $forum->forum_id) || $selected == $forum->forum_id ) {
			$_selected = true;
			$no_option_selected = false;
		}
		$options[] = array(
			'value' => $forum->forum_id,
			'display' => $pad_left . $forum->forum_name,
			'disabled' => false,
			'selected' => $_selected
		);
	endwhile;

	if ( 1 === count( $options ) && !$none ) {
		foreach ( $options as $option_index => $option_value ) {
			if ( $option_value['disabled'] ) {
				return;
			}
			return '<input type="hidden" name="' . $name . '" id="' . $id . '" value="' . esc_attr( $option_value['value'] ) . '" /><span>' . esc_html( $option_value['display'] ) . '</span>';
		}
	}

	foreach ($options as $option_index => $option_value) {
		if (!$none && !$selected && $no_option_selected && !$option_value['disabled']) {
			$option_value['selected'] = true;
			$no_option_selected = false;
		}
		$option_disabled = $option_value['disabled'] ? ' disabled="disabled"' : '';
		$option_selected = $option_value['selected'] ? ' selected="selected"' : '';
		$r .= "\n" . '<option value="' . esc_attr( $option_value['value'] ) . '"' . $option_disabled . $option_selected . '>' . esc_html( $option_value['display'] ) . '</option>' . "\n";
	}
	
	$forum = $old_global;
	if ( !$options_only )
		$r .= '</select>' . "\n";

	return $r;
}

//FAVORITES
function favorites_link( $user_id = 0 ) {
	echo apply_filters( 'favorites_link', get_favorites_link( $user_id ) );
}

function get_favorites_link( $user_id = 0 ) {
	if ( !$user_id )
		$user_id = bb_get_current_user_info( 'id' );
	return apply_filters( 'get_favorites_link', get_profile_tab_link($user_id, 'favorites'), $user_id );
}

function user_favorites_link($add = array(), $rem = array(), $user_id = 0) {
	global $topic, $bb_current_user;
	if ( empty($add) || !is_array($add) )
		$add = array('mid' => __('Add this topic to your favorites'), 'post' => __(' (%?%)'));
	if ( empty($rem) || !is_array($rem) )
		$rem = array( 'pre' => __('This topic is one of your %favorites% ['), 'mid' => __('&times;'), 'post' => __(']'));
	if ( $user_id ) :
		if ( !bb_current_user_can( 'edit_favorites_of', (int) $user_id ) )
			return false;
		if ( !$user = bb_get_user( bb_get_user_id( $user_id ) ) ) :
			return false;
		endif;
	else :
		if ( !bb_current_user_can('edit_favorites') )
			return false;
		$user =& $bb_current_user->data;
	endif;

        $url = esc_url( get_favorites_link( $user_id ) );
	if ( $is_fav = is_user_favorite( $user->ID, $topic->topic_id ) ) :
		$rem = preg_replace('|%(.+)%|', "<a href='$url'>$1</a>", $rem);
		$favs = array('fav' => '0', 'topic_id' => $topic->topic_id);
		$pre  = ( is_array($rem) && isset($rem['pre'])  ) ? $rem['pre']  : '';
		$mid  = ( is_array($rem) && isset($rem['mid'])  ) ? $rem['mid']  : ( is_string($rem) ? $rem : '' );
		$post = ( is_array($rem) && isset($rem['post']) ) ? $rem['post'] : '';
	elseif ( false === $is_fav ) :
		$add = preg_replace('|%(.+)%|', "<a href='$url'>$1</a>", $add);
		$favs = array('fav' => '1', 'topic_id' => $topic->topic_id);
		$pre  = ( is_array($add) && isset($add['pre'])  ) ? $add['pre']  : '';
		$mid  = ( is_array($add) && isset($add['mid'])  ) ? $add['mid']  : ( is_string($add) ? $add : '' );
		$post = ( is_array($add) && isset($add['post']) ) ? $add['post'] : '';
	endif;

	$url = esc_url(  bb_nonce_url( add_query_arg( $favs, get_favorites_link( $user_id ) ), 'toggle-favorite_' . $topic->topic_id ) );

	if (  !is_null($is_fav) )
		echo "<span id='favorite-$topic->topic_id'>$pre<a href='$url' class='dim:favorite-toggle:favorite-$topic->topic_id:is-favorite'>$mid</a>$post</span>";
}

function favorites_rss_link( $id = 0, $context = 0 ) {
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	echo apply_filters('favorites_rss_link', get_favorites_rss_link( $id, $context ), $context, $id);
}

function get_favorites_rss_link( $id = 0, $context = 0 ) {
	$user = bb_get_user( bb_get_user_id( $id ) );
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_FEED;
	}
	
	$rewrite = bb_get_option( 'mod_rewrite' );
	if ( $rewrite ) {
		if ( $rewrite === 'slugs' ) {
			$column = 'user_nicename';
		} else {
			$column = 'ID';
		}
		$link = bb_get_uri('rss/profile/' . $user->$column, null, $context);
	} else {
		$link = bb_get_uri('rss.php', array('profile' => $user->ID), $context);
	}
	return apply_filters( 'get_favorites_rss_link', $link, $user->ID, $context );
}

function favorites_pages( $args = null )
{
	$defaults = array( 'before' => '', 'after' => '' );
	$args = wp_parse_args( $args, $defaults );

	global $page, $user, $favorites_total;
	if ( $pages = apply_filters( 'favorites_pages', get_page_number_links( $page, $favorites_total ), $user->user_id ) ) {
		echo $args['before'] . $pages . $args['after'];
	}
}

//SUBSCRIPTION

/** 
 * Checks if subscription is enabled.
 * 
 * @since 1.1 
 *  
 * @return bool is subscription enabled or not 
 */
function bb_is_subscriptions_active() { 
	return (bool) bb_get_option( 'enable_subscriptions' ); 
}

/**
 * Checks if user is subscribed to current topic.
 *
 * @since 1.1
 *
 * @return bool is user subscribed or not
 */
function bb_is_user_subscribed( $args = null ) {
	global $bbdb;
	
	$defaults = array(
		'user_id'  => bb_is_topic_edit() ? bb_get_user_id( get_post_author_id() ) : bb_get_current_user_info( 'id' ),
		'topic_id' => get_topic_id()
	);
	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );
	
	$there = $bbdb->get_var( $bbdb->prepare( "SELECT `$bbdb->term_relationships`.`object_id`
				FROM $bbdb->term_relationships, $bbdb->term_taxonomy, $bbdb->terms
				WHERE `$bbdb->term_relationships`.`object_id` = %d
				AND `$bbdb->term_relationships`.`term_taxonomy_id` = `$bbdb->term_taxonomy`.`term_taxonomy_id`
				AND `$bbdb->term_taxonomy`.`term_id` = `$bbdb->terms`.`term_id`
				AND `$bbdb->term_taxonomy`.`taxonomy` = 'bb_subscribe'
				AND `$bbdb->terms`.`slug` = 'topic-%d'",
				$user_id, $topic_id ) );
	
	$there = apply_filters( 'bb_is_user_subscribed', $there, $args );
	
	if ( $there )
		return true;
	
	return false;
}

/**
 * Outputs the subscribe/unsubscibe link.
 *
 * Checks if user is subscribed and outputs link based on status.
 *
 * @since 1.1
 */
function bb_user_subscribe_link() {
	$topic_id = get_topic_id();

	if ( !bb_is_user_logged_in() )
		return false;

	if ( bb_is_user_subscribed() )
		echo '<li id="subscription-toggle"><a href="'. bb_nonce_url( bb_get_uri( null, array( 'doit' => 'bb-subscribe', 'topic_id' => $topic_id, 'and' => 'remove' ) ), 'toggle-subscribe_' . $topic_id ) .'">' . apply_filters( 'bb_user_subscribe_link_unsubscribe', __( 'Unsubscribe from Topic' ) ) . '</a></li>';
	else
		echo '<li id="subscription-toggle"><a href="'. bb_nonce_url( bb_get_uri( null, array( 'doit' => 'bb-subscribe', 'topic_id' => $topic_id, 'and' => 'add' ) ), 'toggle-subscribe_' . $topic_id ) .'">' . apply_filters( 'bb_user_subscribe_link_subscribe', __( 'Subscribe to Topic' ) ) . '</a></li>';

}

/**
 * Outputs the post form subscription checkbox.
 *
 * Checks if user is subscribed and outputs checkbox based on status.
 *
 * @since 1.1
 */
function bb_user_subscribe_checkbox( $args = null ) {
	
	if ( !bb_is_user_logged_in() )
		return false;

	$is_current = false;
	$defaults   = array( 'tab' => false );
	$args       = wp_parse_args( $args, $defaults );
	$tab        = $args['tab'] !== false ? ' tabindex="' . $args['tab'] . '"' : '';
	$is_current = bb_get_user_id( get_post_author_id() ) == bb_get_current_user_info( 'id' );

	// Change subscription checkbox message if current or moderating
	if ( bb_is_topic_edit() && !$is_current )
		$text = __( 'This user should be notified of follow-up posts via email' );
	else
		$text = __( 'Notify me of follow-up posts via email' );

	echo '
	<label for="subscription_checkbox">
		<input name="subscription_checkbox" id="subscription_checkbox" type="checkbox" value="subscribe" ' . checked( true, bb_is_user_subscribed(), false ) . $tab . ' />
		' .  apply_filters( 'bb_user_subscribe_checkbox_label', $text, (bool) $is_current ) . '
	</label>';

}

//VIEWS
function view_name( $view = '' ) { // Filtration should be done at bb_register_view()
	echo get_view_name( $view );
}

function get_view_name( $_view = '' ) {
	global $view, $bb_views;
	if ( $_view )
		$v = bb_slug_sanitize($_view);
	else
		$v =& $view;

	if ( isset($bb_views[$v]) )
		return $bb_views[$v]['title'];
}

function view_pages() {
	global $page, $view_count;
	echo apply_filters( 'view_pages', get_page_number_links( $page, $view_count ) );
}

function view_link( $_view = false, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	echo get_view_link( $_view, $page, $context );
}

function get_view_link( $_view = false, $page = 1, $context = BB_URI_CONTEXT_A_HREF ) {
	global $view, $bb_views;
	if ( $_view )
		$v = bb_slug_sanitize($_view);
	else
		$v =& $view;
	
	if (!$context || !is_integer($context)) {
		$context = BB_URI_CONTEXT_A_HREF;
	}
	
	if ( !array_key_exists($v, $bb_views) )
		return bb_get_uri(null, null, $context);
	if ( bb_get_option('mod_rewrite') ) {
		$page = ( 1 < $page ) ? '/page/' . $page : '';
		$link = bb_get_uri('view/' . $v . $page, null, $context);
	} else {
		$query = array(
			'view' => $v,
			'page' => ( 1 < $page ) ? $page : false,
		);
		$link = bb_get_uri('view.php', $query, $context);
	}

	return apply_filters( 'get_view_link', $link, $v, $page, $context );
}

function _bb_parse_time_function_args( $args ) {
	if ( is_numeric($args) )
		$args = array('id' => $args);
	elseif ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array('format' => $args);

	$defaults = array( 'id' => 0, 'format' => 'since', 'more' => 0, 'localize' => true );
	return wp_parse_args( $args, $defaults );
}

function _bb_time_function_return( $time, $args ) {
	$time = bb_gmtstrtotime( $time );

	switch ( $format = $args['format'] ) :
	case 'since' :
		return bb_since( $time, $args['more'] );
		break;
	case 'timestamp' :
		$format = 'U';
		break;
	case 'mysql' :
		$format = 'Y-m-d H:i:s';
		break;
	case 'datetime' :
		$format = bb_get_option( 'datetime_format' );
		break;
	endswitch;

	return $args['localize'] ? bb_gmdate_i18n( $format, $time ) : gmdate( $format, $time );
}

function bb_template_scripts() {
	if ( bb_is_topic() && bb_is_user_logged_in() )
		wp_enqueue_script( 'topic' );
	elseif ( bb_is_profile() && bb_is_user_logged_in() ) {
		global $self;
		if ($self == 'profile-edit.php') {
			wp_enqueue_script( 'profile-edit' );
		}
	}
}

if ( !function_exists( 'checked' ) ) :
/**
 * Outputs the html checked attribute.
 *
 * Compares the first two arguments and if identical marks as checked
 *
 * @since 1.1
 *
 * @param mixed $checked One of the values to compare
 * @param mixed $current (true) The other value to compare if not just true
 * @param bool $echo Whether to echo or just return the string
 * @return string html attribute or empty string
 */
function checked( $checked, $current = true, $echo = true ) {
	return __checked_selected_helper( $checked, $current, $echo, 'checked' );
}
endif;

if ( !function_exists( 'selected' ) ) :
/**
 * Outputs the html selected attribute.
 *
 * Compares the first two arguments and if identical marks as selected
 *
 * @since 1.1
 *
 * @param mixed selected One of the values to compare
 * @param mixed $current (true) The other value to compare if not just true
 * @param bool $echo Whether to echo or just return the string
 * @return string html attribute or empty string
 */
function selected( $selected, $current = true, $echo = true ) {
	return __checked_selected_helper( $selected, $current, $echo, 'selected' );
}
endif;

if ( !function_exists( 'disabled' ) ) :
/**
 * Outputs the html disabled attribute.
 *
 * Compares the first two arguments and if identical marks as disabled
 *
 * @since 1.1
 *
 * @param mixed $disabled One of the values to compare
 * @param mixed $current (true) The other value to compare if not just true
 * @param bool $echo Whether to echo or just return the string
 * @return string html attribute or empty string
 */
function disabled( $disabled, $current = true, $echo = true ) {
	return __checked_selected_helper( $disabled, $current, $echo, 'disabled' );
}
endif;

if ( !function_exists( '__checked_selected_helper' ) ) :
/**
 * Private helper function for checked, selected, and disabled.
 *
 * Compares the first two arguments and if identical marks as $type
 *
 * @since 1.1
 * @access private
 *
 * @param any $helper One of the values to compare
 * @param any $current (true) The other value to compare if not just true
 * @param bool $echo Whether to echo or just return the string
 * @param string $type The type of checked|selected|disabled we are doing
 * @return string html attribute or empty string
 */
function __checked_selected_helper( $helper, $current, $echo, $type ) {
	if ( (string) $helper === (string) $current )
		$result = " $type='$type'";
	else
		$result = '';

	if ( $echo )
		echo $result;

	return $result;
}
endif;
