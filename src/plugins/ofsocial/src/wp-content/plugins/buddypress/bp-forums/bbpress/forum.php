<?php

require_once('./bb-load.php');

$forum_id = 0;

bb_repermalink();

if ( !$forum )
	bb_die(__('Forum not found.'));

$bb_db_override = false;
do_action( 'bb_forum.php_pre_db', $forum_id );

if ( !$bb_db_override ) :
	if ( $topics = get_latest_topics( $forum_id, $page ) ) {
		bb_cache_last_posts( $topics );
	}
	if ( $stickies = get_sticky_topics( $forum_id, $page ) ) {
		bb_cache_last_posts( $stickies );
	}
endif;

bb_load_template( 'forum.php', array('bb_db_override', 'stickies'), $forum_id );

?>
