<?php
require('./bb-load.php');

// Determine the type of feed and the id of the object
if ( isset($_GET['view']) || bb_get_path() == 'view' ) {
	
	// View
	$feed = 'view';
	$feed_id = isset($_GET['view']) ? $_GET['view'] : bb_get_path(2);
	
} elseif ( isset($_GET['topic']) || bb_get_path() == 'topic' ) {
	
	// Topic
	$feed = 'topic';
	$topic = get_topic(isset($_GET['topic']) ? $_GET['topic'] : bb_get_path(2));
	$feed_id = $topic->topic_id;
	
} elseif ( isset($_GET['profile']) || bb_get_path() == 'profile' ) {
	
	// Profile
	$feed = 'profile';
	$feed_id = isset($_GET['profile']) ? $_GET['profile'] : bb_get_path(2);
	
} elseif ( isset($_GET['tag']) || bb_get_path() == 'tags' ) {
	
	if ( isset($_GET['topics']) || bb_get_path(3) == 'topics' ) {
		// Tag recent topics
		$feed = 'tag-topics';
	} else {
		// Tag recent posts
		$feed = 'tag-posts';
	}
	$feed_id = isset($_GET['tag']) ? $_GET['tag'] : bb_get_path(2);
	
} elseif ( isset($_GET['forum']) || bb_get_path() == 'forum' ) {
	
	if ( isset($_GET['topics']) || bb_get_path(3) == 'topics' ) {
		// Forum recent topics
		$feed = 'forum-topics';
	} else {
		// Forum recent posts
		$feed = 'forum-posts';
	}
	$forum = bb_get_forum(isset($_GET['forum']) ? $_GET['forum'] : bb_get_path(2));
	$feed_id = $forum->forum_id;
	
} elseif ( isset($_GET['topics']) || bb_get_path() == 'topics' ) {
	
	// Recent topics
	$feed = 'all-topics';
	
} else {
	
	// Recent posts
	$feed = 'all-posts';
	
}

// Initialise the override variable
$bb_db_override = false;
do_action( 'bb_rss.php_pre_db' );

if ( !$bb_db_override ) {
	
	// Get the posts and the title for the given feed
	switch ($feed) {
		case 'view':
			if ( !isset($bb_views[$feed_id]) )
				die();
			if ( !$bb_views[$feed_id]['feed'] )
				die();
			if ( !$topics_object = new BB_Query( 'topic', $bb_views[$feed_id]['query'], "bb_view_$feed_id" ) )
				die();
			
			$topics = $topics_object->results;
			
			$posts = array();
			foreach ( (array) $topics as $topic ) {
				$posts[] = bb_get_first_post($topic->topic_id);
			}
			
			$title = sprintf( __( '%1$s &raquo; View: %2$s' ), bb_get_option( 'name' ), $bb_views[$feed_id]['title'] );
			$link = get_view_link($feed_id);
			$link_self = bb_get_view_rss_link($feed_id);
			break;
		
		case 'topic':
			if ( !$topic = get_topic ( $feed_id ) )
				die();
			if ( !$posts = get_thread( $feed_id, 0, 1 ) )
				die(); /* Should die here, as the topic posts aren't there, so the topic is most probably deleted/empty */
			$title = sprintf( __( '%1$s &raquo; Topic: %2$s' ), bb_get_option( 'name' ), get_topic_title() );
			$link = get_topic_link($feed_id);
			$link_self = get_topic_rss_link($feed_id);
			break;
		
		case 'profile':
			if ( bb_get_option( 'mod_rewrite' ) === 'slugs') {
				if ( !$user = bb_get_user_by_nicename( $feed_id ) )
					$user = bb_get_user( $feed_id );
			} else {
 	                        if ( !$user = bb_get_user( $feed_id ) )
				        $user = bb_get_user_by_nicename( $feed_id ); 
			}
			if ( !$user ) {
				die();
			}
			$posts = get_user_favorites( $user->ID );
			
			$title = sprintf( __( '%1$s &raquo; User Favorites: %2$s' ), bb_get_option( 'name' ), $user->user_nicename );
			$link = get_user_profile_link($feed_id);
			$link_self = get_favorites_rss_link($feed_id);
			break;
		
		case 'tag-topics':
			if ( !$tag = bb_get_tag( $feed_id ) )
				die();
			$topics = get_tagged_topics( array( 'tag_id' => $tag->tag_id, 'page' => 0 ) );
			
			$posts = array();
			foreach ( (array) $topics as $topic ) {
				$posts[] = bb_get_first_post($topic->topic_id);
			}
			
			$title = sprintf( __( '%1$s &raquo; Tag: %2$s - Recent Topics' ), bb_get_option( 'name' ), bb_get_tag_name() );
			$link = bb_get_tag_link($feed_id);
			$link_self = bb_get_tag_topics_rss_link($feed_id);
			break;
		
		case 'tag-posts':
			if ( !$tag = bb_get_tag( $feed_id ) )
				die();
			$posts = get_tagged_topic_posts( array( 'tag_id' => $tag->tag_id, 'page' => 0 ) );
			$title = sprintf( __( '%1$s &raquo; Tag: %2$s - Recent Posts' ), bb_get_option( 'name' ), bb_get_tag_name() );
			$link = bb_get_tag_link($feed_id);
			$link_self = bb_get_tag_posts_rss_link($feed_id);
			break;
		
		case 'forum-topics':
			$topics = get_latest_topics( $feed_id );
			
			$posts = array();
			foreach ( (array) $topics as $topic) {
				$posts[] = bb_get_first_post($topic->topic_id);
			}
			
			$title = sprintf( __( '%1$s &raquo; Forum: %2$s - Recent Topics' ), bb_get_option( 'name' ), get_forum_name( $feed_id ) );
			$link = get_forum_link($feed_id);
			$link_self = bb_get_forum_topics_rss_link($feed_id);
			break;
		
		case 'forum-posts':
			$posts = bb_get_latest_forum_posts( $feed_id );
			$title = sprintf( __( '%1$s &raquo; Forum: %2$s - Recent Posts' ), bb_get_option( 'name' ), get_forum_name( $feed_id ) );
			$link = get_forum_link($feed_id);
			$link_self = bb_get_forum_posts_rss_link($feed_id);
			break;
		
		// Get just the first post from the latest topics
		case 'all-topics':
			$topics = get_latest_topics();
			
			$posts = array();
			foreach ( (array) $topics as $topic ) {
				$posts[] = bb_get_first_post($topic->topic_id);
			}
			
			$title = sprintf( __( '%1$s &raquo; Recent Topics' ), bb_get_option( 'name' ) );
			$link = bb_get_uri();
			$link_self = bb_get_topics_rss_link();
			break;
		
		// Get latest posts by default
		case 'all-posts':
		default:
			$posts = bb_get_latest_posts( 35 );
			$title = sprintf( __( '%1$s &raquo; Recent Posts' ), bb_get_option( 'name' ) );
			$link = bb_get_uri();
			$link_self = bb_get_posts_rss_link();
			break;
	}
}

if ( !$posts ) /* We do typecasting in the template, but all themes don't have that! */
	$posts = array();
else /* Only send 304 if there are posts */
	bb_send_304( gmdate('D, d M Y H:i:s \G\M\T', strtotime( $posts[0]->post_time ) ) );

if (!$description = bb_get_option( 'description' ) ) {
	$description = $title;
}
$title = apply_filters( 'bb_title_rss', $title, $feed );
$description = apply_filters( 'bb_description_rss', $description, $feed );
$posts = apply_filters( 'bb_posts_rss', $posts, $feed );
$link_self = apply_filters( 'bb_link_self_rss', $link_self, $feed );

bb_load_template( 'rss2.php', array('bb_db_override', 'title', 'description', 'link', 'link_self'), $feed );

