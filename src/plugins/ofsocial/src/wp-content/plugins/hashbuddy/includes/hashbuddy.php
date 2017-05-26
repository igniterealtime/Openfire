<?php

if ( !defined( 'ABSPATH' ) ) exit;

function hashbuddy_activity_hashtags_filter( $content ) {
	global $bp;

	$pattern = '/[#]([\p{L}_0-9a-zA-Z-]+)/iu';

	$activity_url = trailingslashit( get_bloginfo('url') ) . BP_ACTIVITY_SLUG;

	preg_match_all( $pattern, $content, $hashtags );

	if ( $hashtags ) {
		/* Make sure there's only one instance of each tag */
		if ( !$hashtags = array_unique( $hashtags[1] ) )
			return $content;

		foreach( (array)$hashtags as $hashtag ) {

			$pattern = "/(^|\s|\b)#". $hashtag ."($|\b)/";

			$content = preg_replace( $pattern, ' <a href="' . $activity_url . '/?s=%23' . htmlspecialchars( $hashtag ) . '" rel="nofollow" class="hashtag" id="' . htmlspecialchars( $hashtag ) . '">#'. htmlspecialchars( $hashtag ) .'</a>', $content );
		}
	}

	return $content;
}


function hashbuddy_activity_hashtags_querystring( $query_string, $object ) {
	global $bp;

	if ( isset( $_GET['hash'] ) ) {

		$hash = $_GET['hash'];

	/* Now pass the querystring to override default values. */
	$query_string .= '&display_comments=true&search_terms=#' . $hash;

	}

	return $query_string;
}
add_filter( 'bp_ajax_querystring', 'hashbuddy_activity_hashtags_querystring', 11, 2 );
add_filter( 'bp_dtheme_ajax_querystring', 'hashbuddy_activity_hashtags_querystring', 11, 2 );


function hashbuddy_bbpress_hashtags_filter( $content ) {
	global $bp;

	$forum_slug = get_option('_bbp_root_slug');
	$search_slug = get_option('_bbp_search_slug');


	$pattern = '/[#]([\p{L}_0-9a-zA-Z-]+)/iu';

	$site_url = trailingslashit( get_bloginfo('url') );

	preg_match_all( $pattern, $content, $hashtags );
	if ( $hashtags ) {
		/* Make sure there's only one instance of each tag */
		if ( !$hashtags = array_unique( $hashtags[1] ) )
			return $content;

		//but we need to watch for edits and if something was already wrapped in html link - thus check for space or word boundary prior
		foreach( (array)$hashtags as $hashtag ) {
			$pattern = "/(^|\s|\b)#". $hashtag ."($|\b)/";
			$content = preg_replace( $pattern, ' <a href="'. $site_url . $forum_slug . '/' . $search_slug . '/?bbp_search=%23' . htmlspecialchars( $hashtag ) . '" rel="nofollow" class="hashtag" id="' . htmlspecialchars( $hashtag ) . '">#'. htmlspecialchars( $hashtag ) .'</a>', $content );
		}
	}

	return $content;
}