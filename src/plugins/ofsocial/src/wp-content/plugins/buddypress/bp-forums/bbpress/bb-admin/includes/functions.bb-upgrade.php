<?php
function bb_install() {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	require_once( BB_PATH . 'bb-admin/includes/defaults.bb-schema.php' );
	$alterations = BP_SQL_Schema_Parser::delta( $bbdb, $bb_queries, $bb_schema_ignore );

	bb_update_db_version();

	return array_filter($alterations);
}

function bb_upgrade_all()
{
	if ( !ini_get( 'safe_mode' ) ) {
		set_time_limit(600);
	}

	$_do_user_operations = true;
	if ( bb_get_option( 'wp_table_prefix' ) || ( defined( 'BB_SCHEMA_IGNORE_WP_USERS_TABLES' ) && BB_SCHEMA_IGNORE_WP_USERS_TABLES ) ) {
		$_do_user_operations = false;
	}

	$bb_upgrade = array();

	// Pre DB Delta
	if ( $_do_user_operations ) {
		$bb_upgrade['messages'][] = bb_upgrade_160(); // Break blocked users
		$bb_upgrade['messages'][] = bb_upgrade_170(); // Escaping in usermeta
		$bb_upgrade['messages'][] = bb_upgrade_180(); // Delete users for real
	}
	$bb_upgrade['messages'][] = bb_upgrade_190(); // Move topic_resolved to topicmeta
	$bb_upgrade['messages'][] = bb_upgrade_200(); // Indices
	$bb_upgrade['messages'][] = bb_upgrade_210(); // Convert text slugs to varchar slugs
	$bb_upgrade['messages'][] = bb_upgrade_220(); // remove bb_tagged primary key, add new column and primary key

	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	require_once( BB_PATH . 'bb-admin/includes/defaults.bb-schema.php' );
	$delta = BP_SQL_Schema_Parser::delta( $bbdb, $bb_queries, $bb_schema_ignore );
	if ( is_array( $delta ) ) {
		$bb_upgrade['messages'] = array_merge($bb_upgrade['messages'], $delta['messages']);
		$bb_upgrade['errors'] = $delta['errors'];
	} else {
		$bb_upgrade['errors'] = array();
	}

	// Post DB Delta
	$bb_upgrade['messages'][] = bb_upgrade_1000(); // Make forum and topic slugs
	$bb_upgrade['messages'][] = bb_upgrade_1010(); // Make sure all forums have a valid parent
	if ( $_do_user_operations ) {
		$bb_upgrade['messages'][] = bb_upgrade_1020(); // Add a user_nicename to existing users
	}
	$bb_upgrade['messages'][] = bb_upgrade_1030(); // Move admin_email option to from_email
	$bb_upgrade['messages'][] = bb_upgrade_1040(); // Activate Akismet and bozo plugins and convert active plugins to new convention on upgrade only
	$bb_upgrade['messages'][] = bb_upgrade_1050(); // Update active theme if present
	$bb_upgrade['messages'][] = bb_upgrade_1070(); // trim whitespace from raw_tag
	$bb_upgrade['messages'][] = bb_upgrade_1080(); // Convert tags to taxonomy
	if ( $_do_user_operations ) {
		$bb_upgrade['messages'][] = bb_upgrade_1090(); // Add display names
	}
	$bb_upgrade['messages'][] = bb_upgrade_1100(); // Replace forum_stickies index with stickies (#876)
	$bb_upgrade['messages'][] = bb_upgrade_1110(); // Create plugin directory (#1083)
	$bb_upgrade['messages'][] = bb_upgrade_1120(); // Create theme directory (#1083)
	$bb_upgrade['messages'][] = bb_upgrade_1130(); // Add subscriptions option and set it to true (#1268)

	bb_update_db_version();
	wp_cache_flush();

	$bb_upgrade['messages'] = array_filter($bb_upgrade['messages']);
	$bb_upgrade['errors'] = array_filter($bb_upgrade['errors']);

	return $bb_upgrade;
}

function bb_upgrade_process_all_slugs() {
	global $bbdb;
	// Forums

	$forums = (array) $bbdb->get_results("SELECT forum_id, forum_name FROM $bbdb->forums ORDER BY forum_order ASC" );

	$slugs = array();
	foreach ( $forums as $forum ) :
		$slug = bb_slug_sanitize( wp_specialchars_decode( $forum->forum_name, ENT_QUOTES ) );
		$slugs[$slug][] = $forum->forum_id;
	endforeach;

	foreach ( $slugs as $slug => $forum_ids ) :
		foreach ( $forum_ids as $count => $forum_id ) :
			$_slug = $slug;
			$count = - $count; // madness
			if ( is_numeric($slug) || $count )
				$_slug = bb_slug_increment( $slug, $count );
			$bbdb->query("UPDATE $bbdb->forums SET forum_slug = '$_slug' WHERE forum_id = '$forum_id';");
		endforeach;
	endforeach;
	unset($forums, $forum, $slugs, $slug, $_slug, $forum_ids, $forum_id, $count);

	// Topics

	$topics = (array) $bbdb->get_results("SELECT topic_id, topic_title FROM $bbdb->topics ORDER BY topic_start_time ASC" );

	$slugs = array();
	foreach ( $topics as $topic) :
		$slug = bb_slug_sanitize( wp_specialchars_decode( $topic->topic_title, ENT_QUOTES ) );
		$slugs[$slug][] = $topic->topic_id;
	endforeach;

	foreach ( $slugs as $slug => $topic_ids ) :
		foreach ( $topic_ids as $count => $topic_id ) :
			$_slug = $slug;
			$count = - $count;
			if ( is_numeric($slug) || $count )
				$_slug = bb_slug_increment( $slug, $count );
			$bbdb->query("UPDATE $bbdb->topics SET topic_slug = '$_slug' WHERE topic_id = '$topic_id';");
		endforeach;
	endforeach;
	unset($topics, $topic, $slugs, $slug, $_slug, $topic_ids, $topic_id, $count);
}

// Reversibly break passwords of blocked users.
function bb_upgrade_160() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 535 )
		return;

	require_once( BB_PATH . 'bb-admin/includes/functions.bb-admin.php' );
	$blocked = bb_get_ids_by_role( 'blocked' );
	foreach ( $blocked as $b )
		bb_break_password( $b );
	return 'Done reversibly breaking passwords: ' . __FUNCTION__;
}

function bb_upgrade_170() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 536 )
		return;

	global $bbdb;
	foreach ( (array) $bbdb->get_results("SELECT * FROM $bbdb->usermeta WHERE meta_value LIKE '%&quot;%' OR meta_value LIKE '%&#039;%'") as $meta ) {
		$value = str_replace(array('&quot;', '&#039;'), array('"', "'"), $meta->meta_value);
		$value = stripslashes($value);
		bb_update_usermeta( $meta->user_id, $meta->meta_key, $value);
	}
	bb_update_option( 'bb_db_version', 536 );
	return 'Done updating usermeta: ' . __FUNCTION__;
}

function bb_upgrade_180() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 559 )
		return;

	global $bbdb;

	foreach ( (array) $bbdb->get_col("SELECT ID FROM $bbdb->users WHERE user_status = 1") as $user_id )
		bb_delete_user( $user_id );
	bb_update_option( 'bb_db_version', 559 );
	return 'Done clearing deleted users: ' . __FUNCTION__;
}

function bb_upgrade_190() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 630 )
		return;

	global $bbdb;

	$exists = false;
	foreach ( (array) $bbdb->get_col("DESC $bbdb->topics") as $col )
		if ( 'topic_resolved' == $col )
			$exists = true;
	if ( !$exists )
		return;

	$topics = (array) $bbdb->get_results("SELECT topic_id, topic_resolved FROM $bbdb->topics" );
	foreach ( $topics  as $topic )
		bb_update_topicmeta( $topic->topic_id, 'topic_resolved', $topic->topic_resolved );
	unset($topics,$topic);

	$bbdb->query("ALTER TABLE $bbdb->topics DROP topic_resolved");

	bb_update_option( 'bb_db_version', 630 );

	return 'Done converting topic_resolved: ' . __FUNCTION__;
}

function bb_upgrade_200() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 845 )
		return;

	global $bbdb;

	$bbdb->hide_errors();
	$bbdb->query( "DROP INDEX tag_id_index ON $bbdb->tagged" );
	$bbdb->query( "DROP INDEX user_id ON $bbdb->topicmeta" );
	$bbdb->query( "DROP INDEX forum_id ON $bbdb->topics" );
	$bbdb->query( "DROP INDEX topic_time ON $bbdb->topics" );
	$bbdb->query( "DROP INDEX topic_start_time ON $bbdb->topics" );
	$bbdb->query( "DROP INDEX tag_id_index ON $bbdb->tagged" );
	$bbdb->query( "DROP INDEX topic_id ON $bbdb->posts" );
	$bbdb->query( "DROP INDEX poster_id ON $bbdb->posts" );
	$bbdb->show_errors();

	bb_update_option( 'bb_db_version', 845 );

	return 'Done removing old indices: ' . __FUNCTION__;
}

// 210 converts text slugs to varchar(255) width slugs (upgrading from alpha version - fires before dbDelta)
// 1000 Gives new slugs (upgrading from previous release - fires after dbDelta)
function bb_upgrade_210() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 846 )
		return;

	global $bbdb;

	$bbdb->hide_errors();
	if ( !$bbdb->get_var("SELECT forum_slug FROM $bbdb->forums ORDER BY forum_order ASC LIMIT 1" ) )
		return; // Wait till after dbDelta
	$bbdb->show_errors();

	bb_upgrade_process_all_slugs();

	bb_update_option( 'bb_db_version', 846 );
	
	return 'Done adding slugs: ' . __FUNCTION__;
}

function bb_upgrade_220() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1051 )
		return;

	global $bbdb;

	$bbdb->query( "ALTER TABLE $bbdb->tagged DROP PRIMARY KEY" );
	$bbdb->query( "ALTER TABLE $bbdb->tagged ADD tagged_id bigint(20) unsigned NOT NULL auto_increment PRIMARY KEY FIRST" );

	return "Done removing key from $bbdb->tagged: " . __FUNCTION__;
}

function bb_upgrade_1000() { // Give all topics and forums slugs
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 846 )
		return;

	bb_upgrade_process_all_slugs();

	bb_update_option( 'bb_db_version', 846 );
	
	return 'Done adding slugs: ' . __FUNCTION__;;
}

// Make sure all forums have a valid parent
function bb_upgrade_1010() {
	global $bbdb;
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 952 )
		return;

	$forums = (array) $bbdb->get_results( "SELECT forum_id, forum_parent FROM $bbdb->forums" );
	$forum_ids = (array) $bbdb->get_col( '', 0 );

	foreach ( $forums as $forum ) {
		if ( $forum->forum_parent && !in_array( $forum->forum_parent, $forum_ids ) )
			$bbdb->query( "UPDATE $bbdb->forums SET forum_parent = 0 WHERE forum_id = '$forum->forum_id'" );
	}

	bb_update_option( 'bb_db_version', 952 );
	
	return 'Done re-parenting orphaned forums: ' . __FUNCTION__;
}

// Add a nicename for existing users if they don't have one already
function bb_upgrade_1020() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 977 )
		return;
	
	global $bbdb;
	
	$users = $bbdb->get_results( "SELECT ID, user_login, user_nicename FROM $bbdb->users WHERE user_nicename IS NULL OR user_nicename = ''" );
	
	if ( $users ) {
		foreach ( $users as $user ) {
			$user_nicename = $_user_nicename = bb_user_nicename_sanitize( $user->user_login );
			while ( is_numeric($user_nicename) || $existing_user = bb_get_user_by_nicename( $user_nicename ) )
				$user_nicename = bb_slug_increment($_user_nicename, $existing_user->user_nicename, 50);
			
			$bbdb->query( "UPDATE $bbdb->users SET user_nicename = '$user_nicename' WHERE ID = $user->ID;" );
		}
	}
	
	bb_update_option( 'bb_db_version', 977 );
	
	return 'Done adding nice-names to existing users: ' . __FUNCTION__;
}

// Move admin_email option to from_email
function bb_upgrade_1030() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1058 )
		return;
	
	$admin_email = bb_get_option('admin_email');
	if ($admin_email) {
		bb_update_option('from_email', $admin_email);
	}
	bb_delete_option('admin_email');
	
	bb_update_option( 'bb_db_version', 1058 );
	
	return 'Done moving admin_email to from_email: ' . __FUNCTION__;
}

// Activate Akismet and bozo plugins and convert active plugins to new convention on upgrade only
function bb_upgrade_1040() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1230 )
		return;
	
	// Only do this when upgrading
	if ( defined( 'BB_UPGRADING' ) && BB_UPGRADING ) {
		$plugins = bb_get_option('active_plugins');
		if ( bb_get_option('akismet_key') && !in_array('core#akismet.php', $plugins) ) {
			$plugins[] = 'core#akismet.php';
		}
		if ( !in_array('core#bozo.php', $plugins) ) {
			$plugins[] = 'core#bozo.php';
		}
		
		$new_plugins = array();
		foreach ($plugins as $plugin) {
			if (substr($plugin, 0, 5) != 'core#') {
				if ($plugin != 'akismet.php' && $plugin != 'bozo.php') {
					$new_plugins[] = 'user#' . $plugin;
				}
			} else {
				$new_plugins[] = $plugin;
			}
		}
		
		bb_update_option( 'active_plugins', $new_plugins );
	}
	
	bb_update_option( 'bb_db_version', 1230 );
	
	return 'Done activating Akismet and Bozo plugins and converting active plugins to new convention on upgrade only: ' . __FUNCTION__;
}

// Update active theme if present
function bb_upgrade_1050() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1234 )
		return;
	
	// Only do this when upgrading
	if ( defined( 'BB_UPGRADING' ) && BB_UPGRADING ) {
		if ( $theme = bb_get_option( 'bb_active_theme' ) ) {
			bb_update_option( 'bb_active_theme', bb_theme_basename( $theme ) );
		}
	}
	
	bb_update_option( 'bb_db_version', 1234 );
	
	return 'Done updating active theme if present: ' . __FUNCTION__;
}

function bb_upgrade_1070() {
	global $bbdb;
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1467 )
		return;

	$bbdb->query( "UPDATE `$bbdb->tags` SET `raw_tag` = TRIM(`raw_tag`)" );

	bb_update_option( 'bb_db_version', 1467 );

	return 'Whitespace trimmed from raw_tag: ' . __FUNCTION__;
}

function bb_upgrade_1080() {
	global $bbdb, $wp_taxonomy_object;
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1526 )
		return;

	$offset = 0;
	while ( $tags = (array) $bbdb->get_results( "SELECT * FROM $bbdb->tags LIMIT $offset, 100" ) ) {
		if ( !ini_get('safe_mode') ) set_time_limit(600);
		$wp_taxonomy_object->defer_term_counting(true);
		for ( $i = 0; isset($tags[$i]); $i++ ) {
			$bbdb->insert( $bbdb->terms, array( 
				'name' => $tags[$i]->raw_tag,
				'slug' => $tags[$i]->tag
			) );
			$term_id = $bbdb->insert_id;
			$bbdb->insert( $bbdb->term_taxonomy, array(
				'term_id' => $term_id,
				'taxonomy' => 'bb_topic_tag',
				'description' => ''
			) );
			$term_taxonomy_id = $bbdb->insert_id;
			$topics = (array) $bbdb->get_results( $bbdb->prepare( "SELECT user_id, topic_id FROM $bbdb->tagged WHERE tag_id = %d", $tags[$i]->tag_id ) );
			for ( $j = 0; isset($topics[$j]); $j++ ) {
				$bbdb->insert( $bbdb->term_relationships, array(
					'object_id' => $topics[$j]->topic_id,
					'term_taxonomy_id' => $term_taxonomy_id,
					'user_id' => $topics[$j]->user_id
				) );
			}
			$wp_taxonomy_object->update_term_count( array( $term_taxonomy_id ), 'bb_topic_tag' );
		}
		$wp_taxonomy_object->defer_term_counting(false);
		$offset += 100;
	}

	bb_update_option( 'bb_db_version', 1526 );

	return 'Tags copied to taxonomy tables: ' . __FUNCTION__;
}

function bb_upgrade_1090() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1589 )
		return;

	global $bbdb;

	$users = (array) $bbdb->get_results( "SELECT `ID`, `user_login` FROM $bbdb->users WHERE `display_name` = '' OR `display_name` IS NULL;" );

	if ($users) {
		foreach ($users as $user) {
			$bbdb->query( "UPDATE $bbdb->users SET `display_name` = '" . $user->user_login . "' WHERE ID = " . $user->ID . ";" );
		}
		unset($user, $users);
	}

	bb_update_option( 'bb_db_version', 1589 );

	return 'Display names populated: ' . __FUNCTION__;
}

function bb_upgrade_1100() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 1638 )
		return;

	global $bbdb;

	$bbdb->query( "DROP INDEX forum_stickies ON $bbdb->topics" );

	bb_update_option( 'bb_db_version', 1638 );

	return 'Index forum_stickies dropped: ' . __FUNCTION__;
}

function bb_upgrade_1110() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 2077 )
		return;

	// No matter what happens, update the db version
	bb_update_option( 'bb_db_version', 2077 );

	if ( !defined( 'BB_PLUGIN_DIR' ) ) {
		return;
	}

	if ( !BB_PLUGIN_DIR ) {
		return;
	}

	if ( file_exists( BB_PLUGIN_DIR ) ) {
		return;
	}

	// Just suppress errors as this is not critical
	if ( @mkdir( BB_PLUGIN_DIR, 0755 ) ) {
		return 'Making plugin directory at ' . BB_PLUGIN_DIR . ': ' . __FUNCTION__;
	}

	return;
}

function bb_upgrade_1120() {
	if ( ( $dbv = bb_get_option_from_db( 'bb_db_version' ) ) && $dbv >= 2078 ) {
		return;
	}

	// No matter what happens, update the db version
	bb_update_option( 'bb_db_version', 2078 );

	if ( !defined( 'BB_THEME_DIR' ) ) {
		return;
	}

	if ( !BB_THEME_DIR ) {
		return;
	}

	if ( file_exists( BB_THEME_DIR ) ) {
		return;
	}

	// Just suppress errors as this is not critical
	if ( @mkdir( BB_THEME_DIR, 0755 ) ) {
		return 'Making theme directory at ' . BB_THEME_DIR . ': ' . __FUNCTION__;
	}

	return;
}

// Subscription Option
function bb_upgrade_1130() {
	if ( $dbv = bb_get_option_from_db( 'bb_db_version' ) && $dbv >= 2471 )
		return;
	
	// If the option is already there, then return
	if ( bb_get_option( 'enable_subscriptions' ) )
		return;
	
	bb_update_option( 'enable_subscriptions', 1 );
	
	bb_update_option( 'bb_db_version', 2471 );
	
	return 'Added subscriptions option and set its value to true: ' . __FUNCTION__;
}

function bb_deslash($content) {
    // Note: \\\ inside a regex denotes a single backslash.

    // Replace one or more backslashes followed by a single quote with
    // a single quote.
    $content = preg_replace("/\\\+'/", "'", $content);

    // Replace one or more backslashes followed by a double quote with
    // a double quote.
    $content = preg_replace('/\\\+"/', '"', $content);

    // Replace one or more backslashes with one backslash.
    $content = preg_replace("/\\\+/", "\\", $content);

    return $content;
}

function bb_update_db_version() {
	bb_update_option( 'bb_db_version', bb_get_option( 'bb_db_version' ) );
}
?>
