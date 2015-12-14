<?php
require_once('./bb-load.php');

bb_repermalink();

if ( $tag_name && $tag ) :

	if ( $topics = get_tagged_topics($tag->tag_id, $page) ) {
		bb_cache_last_posts( $topics );
	}

	bb_load_template( 'tag-single.php', array('tag', 'tag_name', 'topics'), $tag->tag_id );
else :

	bb_load_template( 'tags.php' );
endif;
?>
