<?php
/**
 * bbPress class to handle pinging
 *
 * @package bbPress
 */
class BB_Pingbacks
{
	/**
	 * Gets the Pingback endpoint URI provided by a web page specified by URL
	 *
	 * @return string|boolean Returns the Pingback endpoint URI if found or false
	 */
	function get_endpoint_uri( $url )
	{
		// First check for an X-pingback header
		if ( !$response = wp_remote_head( $url ) ) {
			return false;
		}
		if ( !$content_type = wp_remote_retrieve_header( $response, 'content-type' ) ) {
			return false;
		}
		if ( preg_match( '#(image|audio|video|model)/#is', $content_type ) ) {
			return false;
		}
		if ( $x_pingback = wp_remote_retrieve_header( $response, 'x-pingback' ) ) {
			return trim( $x_pingback );
		}

		// Fall back to extracting it from the HTML link
		if ( !$response = wp_remote_get( $url ) ) {
			return false;
		}
		if ( 200 !== wp_remote_retrieve_response_code( $response ) ) {
			return false;
		}
		if ( '' === $response_body = wp_remote_retrieve_body( $response ) ) {
			return false;
		}
		if ( !preg_match_all( '@<link([^>]+)>@im', $response_body, $response_links ) ) {
			return false;
		}

		foreach ( $response_links[1] as $response_link_attributes ) {
			$_link = array( 'rel' => false, 'href' => false );
			$response_link_attributes = preg_split( '@\s+@im', $response_link_attributes, -1, PREG_SPLIT_NO_EMPTY );
			foreach ( $response_link_attributes as $response_link_attribute ) {
				if ( $_link['rel'] == 'pingback' && $_link['href'] ) {
					return $_link['href'];
				}
				if ( strpos( $response_link_attribute, '=', 1 ) !== false ) {
					list( $_key, $_value ) = explode( '=', $response_link_attribute, 2 );
					$_link[strtolower( $_key )] = trim( $_value, "'\"" );
				}
			}
		}

		// Fail
		return false;
	}

	/**
	 * Sends all pingbacks and other ping like requests
	 *
	 * At the moment only sends normal pingbacks, but may be
	 * expanded in the future.
	 *
	 * @return integer The number of pings sent
	 */
	function send_all()
	{
		$pings = BB_Pingbacks::send_all_pingbacks();

		return $pings;
	}

	/**
	 * Sends all pingbacks
	 *
	 * @return integer The number of pings sent
	 */
	function send_all_pingbacks()
	{
		global $bbdb;

		$posts = $bbdb->get_results(
			"SELECT
				{$bbdb->posts}.post_id,
				{$bbdb->posts}.topic_id,
				{$bbdb->posts}.post_text
			FROM {$bbdb->posts}, {$bbdb->meta}
			WHERE {$bbdb->posts}.post_id = {$bbdb->meta}.object_id
				AND {$bbdb->meta}.object_type = 'bb_post'
				AND {$bbdb->meta}.meta_key = 'pingback_queued';"
		);

		$pings = 0;
		foreach ( $posts as $post ) {
			if ( $sent = BB_Pingbacks::send_pingback( $post->topic_id, $post->post_text ) ) {
				$pings += $sent;
				bb_delete_postmeta( $post->post_id, 'pingback_queued' );
			}
		}

		return $pings;
	}

	/**
	 * Sends a single pingback if a link is found
	 *
	 * @return integer The number of pingbacks sent
	 */
	function send_pingback( $topic_id, $post_text )
	{
		if ( !$topic_id || !$post_text ) {
			return 0;
		}

		// Get all links in the text and add them to an array
		if ( !preg_match_all( '@<a ([^>]+)>@im', make_clickable( $post_text ), $post_links ) ) {
			return 0;
		}

		$_links = array();
		foreach ( $post_links[1] as $post_link_attributes ) {
			$post_link_attributes = preg_split( '@\s+@im', $post_link_attributes, -1, PREG_SPLIT_NO_EMPTY );
			foreach ( $post_link_attributes as $post_link_attribute ) {
				if ( strpos( $post_link_attribute, '=', 1 ) !== false ) {
					list( $_key, $_value ) = explode( '=', $post_link_attribute, 2 );
					if ( strtolower( $_key ) === 'href' ) {
						$_links[] = trim( $_value, "'\"" );
					}
				}
			}
		}

		// Get pingbacks which have already been performed from this topic
		$past_pingbacks = bb_get_topicmeta( $topic_id, 'pingback_performed' );
		$new_pingbacks = array();

		foreach ( $_links as $_link ) {
			// If it's already been pingbacked, then skip it
			if ( $past_pingbacks && in_array( $_link, $past_pingbacks ) ) {
				continue;
			}

			// If it's trying to ping itself, then skip it
			if ( $topic = bb_get_topic_from_uri( $_link ) ) {
				if ( $topic->topic_id === $topic_id ) {
					continue;
				}
			}

			// Make sure it's a page on a site and not the root
			if ( !$_url = parse_url( $_link ) ) {
				continue;
			}
			if ( !isset( $_url['query'] ) ) {
				if ( $_url['path'] == '' || $_url['path'] == '/' ) {
					continue;
				}
			}

			// Add the URL to the array of those to be pingbacked
			$new_pingbacks[] = $_link;
		}

		include_once( BACKPRESS_PATH . '/class.ixr.php' );

		$count = 0;
		foreach ( $new_pingbacks as $pingback_to_url ) {
			if ( !$pingback_endpoint_uri = BB_Pingbacks::get_endpoint_uri( $pingback_to_url ) ) {
				continue;
			}

			// Stop this nonsense after 60 seconds
			@set_time_limit( 60 );

			// Get the URL to pingback from
			$pingback_from_url = get_topic_link( $topic_id );

			// Using a timeout of 3 seconds should be enough to cover slow servers
			$client = new IXR_Client( $pingback_endpoint_uri );
			$client->timeout = 3;
			$client->useragent .= ' -- bbPress/' . bb_get_option( 'version' );

			// When set to true, this outputs debug messages by itself
			$client->debug = false;

			// If successful or the ping already exists then add to the pingbacked list
			if (
				$client->query( 'pingback.ping', $pingback_from_url, $pingback_to_url ) ||
				( isset( $client->error->code ) && 48 == $client->error->code )
			) {
				$count++;
				$past_pingbacks[] = $pingback_to_url;
			}
		}

		bb_update_topicmeta( $topic_id, 'pingback_performed', $past_pingbacks );

		return $count;
	}
} // END class BB_Pingbacks
