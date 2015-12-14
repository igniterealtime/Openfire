<?php

require('./bb-load.php');

bb_repermalink();

$bb_db_override = false;
do_action( 'bb_index.php_pre_db' );

if ( isset($_GET['new']) && '1' == $_GET['new'] ) :
	$forums = false;
elseif ( !$bb_db_override ) :
	$forums = bb_get_forums(); // Comment to hide forums
	if ( $topics = get_latest_topics( false, $page ) ) {
		bb_cache_last_posts( $topics );
	}
	if ( $super_stickies = get_sticky_topics() ) {
		bb_cache_last_posts( $super_stickies );
	}
endif;

bb_load_template( 'front-page.php', array('bb_db_override', 'super_stickies') );

?>
