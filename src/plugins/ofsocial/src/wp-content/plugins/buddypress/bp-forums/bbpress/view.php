<?php
require_once('./bb-load.php');

bb_repermalink();

$view = bb_slug_sanitize($view);

$sticky_count = $topic_count = 0;
$stickies = $topics = $view_count = false;

if ( isset($bb_views[$view]) ) {
	if ( $bb_views[$view]['sticky'] ) {
		$sticky_query = bb_view_query( $view, array('sticky' => '-no') ); // -no = yes
		$stickies     = $sticky_query->results;
		$sticky_count = $sticky_query->found_rows;
	}
	$topic_query = bb_view_query( $view, array('count' => true) );
	$topics      = $topic_query->results;
	$topic_count = $topic_query->found_rows;

	$view_count = max($sticky_count, $topic_count);
}

do_action( 'bb_custom_view', $view, $page );

bb_load_template( 'view.php', array('view_count', 'stickies'), $view );

?>
