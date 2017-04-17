<?php

function bb_recount_topic_posts()
{
	global $bbdb;

	$statement = __( 'Counting the number of posts in each topic&hellip; %s' );
	$result = __( 'Failed!' );

	$sql = "INSERT INTO `$bbdb->topics` (`topic_id`, `topic_posts`) (SELECT `topic_id`, COUNT(`post_status`) as `topic_posts` FROM `$bbdb->posts` WHERE `post_status` = '0' GROUP BY `topic_id`) ON DUPLICATE KEY UPDATE `topic_posts` = VALUES(`topic_posts`);";
	if ( is_wp_error( $bbdb->query( $sql ) ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_topic_voices()
{
	global $bbdb;

	$statement = __( 'Counting the number of voices in each topic&hellip; %s' );
	$result = __( 'Failed!' );

	$sql_delete = "DELETE FROM `$bbdb->meta` WHERE `object_type` = 'bb_topic' AND `meta_key` = 'voices_count';";
	if ( is_wp_error( $bbdb->query( $sql_delete ) ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$sql = "INSERT INTO `$bbdb->meta` (`object_type`, `object_id`, `meta_key`, `meta_value`) (SELECT 'bb_topic', `topic_id`, 'voices_count', COUNT(DISTINCT `poster_id`) as `meta_value` FROM `$bbdb->posts` WHERE `post_status` = '0' GROUP BY `topic_id`);";
	if ( is_wp_error( $bbdb->query( $sql ) ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_topic_deleted_posts()
{
	global $bbdb;

	$statement = __( 'Counting the number of deleted posts in each topic&hellip; %s' );
	$result = __( 'Failed!' );

	$sql_delete = "DELETE FROM `$bbdb->meta` WHERE `object_type` = 'bb_topic' AND `meta_key` = 'deleted_posts';";
	if ( is_wp_error( $bbdb->query( $sql_delete ) ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$sql = "INSERT INTO `$bbdb->meta` (`object_type`, `object_id`, `meta_key`, `meta_value`) (SELECT 'bb_topic', `topic_id`, 'deleted_posts', COUNT(`post_status`) as `meta_value` FROM `$bbdb->posts` WHERE `post_status` != '0' GROUP BY `topic_id`);";
	if ( is_wp_error( $bbdb->query( $sql ) ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_forum_topics()
{
	global $bbdb;

	$statement = __( 'Counting the number of topics in each forum&hellip; %s' );
	$result = __( 'Failed!' );

	$sql = "INSERT INTO `$bbdb->forums` (`forum_id`, `topics`) (SELECT `forum_id`, COUNT(`topic_status`) as `topics` FROM `$bbdb->topics` WHERE `topic_status` = '0' GROUP BY `forum_id`) ON DUPLICATE KEY UPDATE `topics` = VALUES(`topics`);";
	if ( is_wp_error( $bbdb->query( $sql ) ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_forum_posts()
{
	global $bbdb;

	$statement = __( 'Counting the number of posts in each forum&hellip; %s' );
	$result = __( 'Failed!' );

	$sql = "INSERT INTO `$bbdb->forums` (`forum_id`, `posts`) (SELECT `forum_id`, COUNT(`post_status`) as `posts` FROM `$bbdb->posts` WHERE `post_status` = '0' GROUP BY `forum_id`) ON DUPLICATE KEY UPDATE `posts` = VALUES(`posts`);";
	if ( is_wp_error( $bbdb->query( $sql ) ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_user_topics_replied()
{
	global $bbdb;

	$statement = __( 'Counting the number of topics to which each user has replied&hellip; %s' );
	$result = __( 'Failed!' );

	$sql_select = "SELECT `poster_id`, COUNT(DISTINCT `topic_id`) as `_count` FROM `$bbdb->posts` WHERE `post_status` = '0' GROUP BY `poster_id`;";
	$insert_rows = $bbdb->get_results( $sql_select );

	if ( is_wp_error( $insert_rows ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$meta_key = $bbdb->prefix . 'topics_replied';

	$insert_values = array();
	foreach ( $insert_rows as $insert_row ) {
		$insert_values[] = "('$insert_row->poster_id', '$meta_key', '$insert_row->_count')";
	}

	if ( !count( $insert_values ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}

	$sql_delete = "DELETE FROM `$bbdb->usermeta` WHERE `meta_key` = '$meta_key';";
	if ( is_wp_error( $bbdb->query( $sql_delete ) ) ) {
		return array( 3, sprintf( $statement, $result ) );
	}

	$insert_values = array_chunk( $insert_values, 10000 );
	foreach ( $insert_values as $chunk ) {
		$chunk = "\n" . join( ",\n", $chunk );
		$sql_insert = "INSERT INTO `$bbdb->usermeta` (`user_id`, `meta_key`, `meta_value`) VALUES $chunk;";

		if ( is_wp_error( $bbdb->query( $sql_insert ) ) ) {
			return array( 4, sprintf( $statement, $result ) );
		}
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

// This function bypasses the taxonomy API
function bb_recount_topic_tags()
{
	global $bbdb;

	$statement = __( 'Counting the number of topic tags in each topic&hellip; %s' );
	$result = __( 'Failed!' );

	// Delete empty tags
	$delete = bb_recount_tag_delete_empty();
	if ( $delete[0] > 0 ) {
		$result = __( 'Could not delete empty tags.' );
		return array( 1, sprintf( $statement, $result ) );
	}

	// Get all tags
	$sql_terms = "SELECT
		`$bbdb->term_relationships`.`object_id`,
		`$bbdb->term_taxonomy`.`term_id`
	FROM `$bbdb->term_relationships`
	JOIN `$bbdb->term_taxonomy`
		ON `$bbdb->term_taxonomy`.`term_taxonomy_id` = `$bbdb->term_relationships`.`term_taxonomy_id`
	WHERE
		`$bbdb->term_taxonomy`.`taxonomy` = 'bb_topic_tag'
	ORDER BY
		`$bbdb->term_relationships`.`object_id`,
		`$bbdb->term_taxonomy`.`term_id`;";

	$terms = $bbdb->get_results( $sql_terms );
	if ( is_wp_error( $terms ) || !is_array( $terms ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}
	if ( empty( $terms ) ) {
		$result = __( 'No topic tags found.' );
		return array( 3, sprintf( $statement, $result ) );
	}

	// Count the tags in each topic
	$topics = array();
	foreach ( $terms as $term ) {
		if ( !isset( $topics[$term->object_id] ) ) {
			$topics[$term->object_id] = 1;
		} else {
			$topics[$term->object_id]++;
		}
	}
	if ( empty( $topics ) ) {
		return array( 4, sprintf( $statement, $result ) );
	}

	// Build the values to insert into the SQL statement
	$values = array();
	foreach ($topics as $topic_id => $tag_count) {
		$values[] = '(' . $topic_id . ', ' . $tag_count . ')';
	}
	if ( empty( $values ) ) {
		return array( 5, sprintf( $statement, $result ) );
	}

	// Update the topics with the new tag counts
	$values = array_chunk( $values, 10000 );
	foreach ($values as $chunk) {
		$sql = "INSERT INTO `$bbdb->topics` (`topic_id`, `tag_count`) VALUES " . implode(", ", $chunk) . " ON DUPLICATE KEY UPDATE `tag_count` = VALUES(`tag_count`);";
		if ( is_wp_error( $bbdb->query( $sql ) ) ) {
			return array( 6, sprintf( $statement, $result ) );
		}
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

// This function bypasses the taxonomy API
function bb_recount_tag_topics()
{
	global $bbdb;

	$statement = __( 'Counting the number of topics in each topic tag&hellip; %s' );
	$result = __( 'Failed!' );

	// Delete empty tags
	$delete = bb_recount_tag_delete_empty();
	if ( $delete[0] > 0 ) {
		$result = __( 'Could not delete empty tags.' );
		return array( 1, sprintf( $statement, $result ) );
	}

	// Get all tags
	$sql_terms = "SELECT
		`$bbdb->term_taxonomy`.`term_taxonomy_id`,
		`$bbdb->term_relationships`.`object_id`
	FROM `$bbdb->term_relationships`
	JOIN `$bbdb->term_taxonomy`
		ON `$bbdb->term_taxonomy`.`term_taxonomy_id` = `$bbdb->term_relationships`.`term_taxonomy_id`
	WHERE
		`$bbdb->term_taxonomy`.`taxonomy` = 'bb_topic_tag'
	ORDER BY
		`$bbdb->term_taxonomy`.`term_taxonomy_id`,
		`$bbdb->term_relationships`.`object_id`;";

	$terms = $bbdb->get_results( $sql_terms );
	if ( is_wp_error( $terms ) || !is_array( $terms ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}
	if ( empty( $terms ) ) {
		$result = __( 'No topic tags found.' );
		return array( 3, sprintf( $statement, $result ) );
	}
	
	// Count the topics in each tag
	$tags = array();
	foreach ( $terms as $term ) {
		if ( !isset( $tags[$term->term_taxonomy_id] ) ) {
			$tags[$term->term_taxonomy_id] = 1;
		} else {
			$tags[$term->term_taxonomy_id]++;
		}
	}
	if ( empty( $tags ) ) {
		return array( 4, sprintf( $statement, $result ) );
	}
	
	// Build the values to insert into the SQL statement
	$values = array();
	foreach ($tags as $term_taxonomy_id => $count) {
		$values[] = '(' . $term_taxonomy_id . ', ' . $count . ')';
	}
	if ( empty( $values ) ) {
		return array( 5, sprintf( $statement, $result ) );
	}
	
	// Update the terms with the new tag counts
	$values = array_chunk( $values, 10000 );
	foreach ($values as $chunk) {
		$sql = "INSERT INTO `$bbdb->term_taxonomy` (`term_taxonomy_id`, `count`) VALUES " . implode(", ", $chunk) . " ON DUPLICATE KEY UPDATE `count` = VALUES(`count`);";
		if ( is_wp_error( $bbdb->query( $sql ) ) ) {
			return array( 6, sprintf( $statement, $result ) );
		}
	}

	if ($return_boolean) {
		return true;
	}
	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}

// This function bypasses the taxonomy API
function bb_recount_tag_delete_empty()
{
	global $bbdb;

	$statement = __( 'Deleting topic tags with no topics&hellip; %s' );
	$result = __( 'Failed!' );

	static $run_once;
	if ( isset( $run_once ) ) {
		if ($run_once > 0) {
			$exit = sprintf( __( 'failure (returned code %s)' ), $run_once );
		} else {
			$exit = __( 'success' );
		}
		$result = sprintf( __( 'Already run with %s.' ), $exit );
		return array( $run_once, sprintf( $statement, $result ) );
	}

	// Get all topic ids
	$sql_topics = "SELECT `topic_id` FROM $bbdb->topics ORDER BY `topic_id`;";
	$topics = $bbdb->get_results( $sql_topics );
	if ( is_wp_error( $topics ) ) {
		$result = __('No topics found.');
		$run_once = 1;
		return array( 1, sprintf( $statement, $result ) );
	}
	$topic_ids = array();
	foreach ($topics as $topic) {
		$topic_ids[] = $topic->topic_id;
	}

	// Get all topic tag term relationships without a valid topic id
	$in_topic_ids = implode(', ', $topic_ids);
	$sql_bad_term_relationships = "SELECT
		`$bbdb->term_taxonomy`.`term_taxonomy_id`,
		`$bbdb->term_taxonomy`.`term_id`,
		`$bbdb->term_relationships`.`object_id`
	FROM `$bbdb->term_relationships`
	JOIN `$bbdb->term_taxonomy`
		ON `$bbdb->term_taxonomy`.`term_taxonomy_id` = `$bbdb->term_relationships`.`term_taxonomy_id`
	WHERE
		`$bbdb->term_taxonomy`.`taxonomy` = 'bb_topic_tag' AND
		`$bbdb->term_relationships`.`object_id` NOT IN ($in_topic_ids)
	ORDER BY
		`$bbdb->term_relationships`.`object_id`,
		`$bbdb->term_taxonomy`.`term_id`,
		`$bbdb->term_taxonomy`.`term_taxonomy_id`;";

	$bad_term_relationships = $bbdb->get_results( $sql_bad_term_relationships );
	if ( is_wp_error( $bad_term_relationships ) || !is_array( $bad_term_relationships ) ) {
		$run_once = 2;
		return array( 2, sprintf( $statement, $result ) );
	}

	// Delete those bad term relationships
	if ( !empty( $bad_term_relationships ) ) {
		$values = array();
		foreach ( $bad_term_relationships as $bad_term_relationship ) {
			$values[] = '(`object_id` = ' . $bad_term_relationship->object_id . ' AND `term_taxonomy_id` = ' . $bad_term_relationship->term_taxonomy_id . ')';
		}
		if ( !empty( $values ) ) {
			$values = join(' OR ', $values);
			$sql_bad_term_relationships_delete = "DELETE
			FROM `$bbdb->term_relationships`
			WHERE $values;";
			if ( is_wp_error( $bbdb->query( $sql_bad_term_relationships_delete ) ) ) {
				$run_once = 3;
				return array( 3, sprintf( $statement, $result ) );
			}
		}
	}

	// Now get all term taxonomy ids with term relationships
	$sql_term_relationships = "SELECT `term_taxonomy_id` FROM $bbdb->term_relationships ORDER BY `term_taxonomy_id`;";
	$term_taxonomy_ids = $bbdb->get_col($sql_term_relationships);
	if ( is_wp_error( $term_taxonomy_ids ) ) {
		$run_once = 4;
		return array( 4, sprintf( $statement, $result ) );
	}
	$term_taxonomy_ids = array_unique( $term_taxonomy_ids );

	// Delete topic tags that don't have any term relationships
	if ( !empty( $term_taxonomy_ids ) ) {
		$in_term_taxonomy_ids = implode(', ', $term_taxonomy_ids);
		$sql_delete_term_relationships = "DELETE
		FROM $bbdb->term_taxonomy
		WHERE
			`taxonomy` = 'bb_topic_tag' AND
			`term_taxonomy_id` NOT IN ($in_term_taxonomy_ids);";
		if ( is_wp_error( $bbdb->query( $sql_delete_term_relationships ) ) ) {
			$run_once = 5;
			return array( 5, sprintf( $statement, $result ) );
		}
	}

	// Get all valid term ids
	$sql_terms = "SELECT `term_id` FROM $bbdb->term_taxonomy ORDER BY `term_id`;";
	$term_ids = $bbdb->get_col($sql_terms);
	if ( is_wp_error( $term_ids ) ) {
		$run_once = 6;
		return array( 6, sprintf( $statement, $result ) );
	}
	$term_ids = array_unique( $term_ids );

	// Delete terms that don't have any associated term taxonomies
	if ( !empty( $term_ids ) ) {
		$in_term_ids = implode(', ', $term_ids);
		$sql_delete_terms = "DELETE
		FROM $bbdb->terms
		WHERE
			`term_id` NOT IN ($in_term_ids);";
		if ( is_wp_error( $bbdb->query( $sql_delete_terms ) ) ) {
			$run_once = 7;
			return array( 7, sprintf( $statement, $result ) );
		}
	}

	$result = __( 'Complete!' );
	$run_once = 0;
	return array( 0, sprintf( $statement, $result ) );
}

function bb_recount_clean_favorites()
{
	global $bbdb;

	$statement = __( 'Removing deleted topics from user favorites&hellip; %s' );
	$result = __( 'Failed!' );

	$meta_key = $bbdb->prefix . 'favorites';

	$users = $bbdb->get_results( "SELECT `user_id`, `meta_value` AS `favorites` FROM `$bbdb->usermeta` WHERE `meta_key` = '$meta_key';" );
	if ( is_wp_error( $users ) ) {
		return array( 1, sprintf( $statement, $result ) );
	}

	$topics = $bbdb->get_col( "SELECT `topic_id` FROM `$bbdb->topics` WHERE `topic_status` = '0';" );

	if ( is_wp_error( $topics ) ) {
		return array( 2, sprintf( $statement, $result ) );
	}

	$values = array();
	foreach ( $users as $user ) {
		if ( empty( $user->favorites ) || !is_string( $user->favorites ) ) {
			continue;
		}
		$favorites = explode( ',', $user->favorites );
		if ( empty( $favorites ) || !is_array( $favorites ) ) {
			continue;
		}
		$favorites = join( ',', array_intersect( $topics, $favorites ) );
		$values[] = "('$user->user_id', '$meta_key', '$favorites')";
	}

	if ( !count( $values ) ) {
		$result = __( 'Nothing to remove!' );
		return array( 0, sprintf( $statement, $result ) );
	}

	$sql_delete = "DELETE FROM `$bbdb->usermeta` WHERE `meta_key` = '$meta_key';";
	if ( is_wp_error( $bbdb->query( $sql_delete ) ) ) {
		return array( 4, sprintf( $statement, $result ) );
	}

	$values = array_chunk( $values, 10000 );
	foreach ( $values as $chunk ) {
		$chunk = "\n" . join( ",\n", $chunk );
		$sql_insert = "INSERT INTO `$bbdb->usermeta` (`user_id`, `meta_key`, `meta_value`) VALUES $chunk;";
		if ( is_wp_error( $bbdb->query( $sql_insert ) ) ) {
			return array( 5, sprintf( $statement, $result ) );
		}
	}

	$result = __( 'Complete!' );
	return array( 0, sprintf( $statement, $result ) );
}
