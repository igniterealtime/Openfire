<?php

// Globalise as this file is included within the functions bb_install() and bb_upgrade_all()
global $bb_queries, $bbdb, $bb_schema_ignore;

// Die if no database class is loaded
if ( !isset($bbdb) || ( !is_a( $bbdb, 'BPDB' ) && !is_a( $bbdb, 'db' ) ) )
	die( __('Database class not loaded.') );

// Initialise the query array
$bb_queries = array();

// forums
$bb_queries['forums'] = "CREATE TABLE IF NOT EXISTS `$bbdb->forums` (
	`forum_id` int(10) NOT NULL auto_increment,
	`forum_name` varchar(150) NOT NULL default '',
	`forum_slug` varchar(255) NOT NULL default '',
	`forum_desc` text NOT NULL,
	`forum_parent` int(10) NOT NULL default 0,
	`forum_order` int(10) NOT NULL default 0,
	`topics` bigint(20) NOT NULL default 0,
	`posts` bigint(20) NOT NULL default 0,
	PRIMARY KEY (`forum_id`),
	KEY `forum_slug` (`forum_slug`)
);";

// meta
$bb_queries['meta'] = "CREATE TABLE IF NOT EXISTS `$bbdb->meta` (
	`meta_id` bigint(20) NOT NULL auto_increment,
	`object_type` varchar(16) NOT NULL default 'bb_option',
	`object_id` bigint(20) NOT NULL default 0,
	`meta_key` varchar(255) default NULL,
	`meta_value` longtext default NULL,
	PRIMARY KEY (`meta_id`),
	KEY `object_type__meta_key` (`object_type`, `meta_key`),
	KEY `object_type__object_id__meta_key` (`object_type`, `object_id`, `meta_key`)
);";

// posts
$bb_queries['posts'] = "CREATE TABLE IF NOT EXISTS `$bbdb->posts` (
	`post_id` bigint(20) NOT NULL auto_increment,
	`forum_id` int(10) NOT NULL default 1,
	`topic_id` bigint(20) NOT NULL default 1,
	`poster_id` int(10) NOT NULL default 0,
	`post_text` text NOT NULL,
	`post_time` datetime NOT NULL default '0000-00-00 00:00:00',
	`poster_ip` varchar(15) NOT NULL default '',
	`post_status` tinyint(1) NOT NULL default 0,
	`post_position` bigint(20) NOT NULL default 0,
	PRIMARY KEY (`post_id`),
	KEY `topic_time` (`topic_id`, `post_time`),
	KEY `poster_time` (`poster_id`, `post_time`),
	KEY `post_time` (`post_time`),
	FULLTEXT KEY `post_text` (`post_text`)
) ENGINE = MYISAM;";

// terms
$bb_queries['terms'] = "CREATE TABLE IF NOT EXISTS `$bbdb->terms` (
	`term_id` bigint(20) NOT NULL auto_increment,
	`name` varchar(55) NOT NULL default '',
	`slug` varchar(200) NOT NULL default '',
	`term_group` bigint(10) NOT NULL default 0,
	PRIMARY KEY (`term_id`),
	UNIQUE KEY `slug` (`slug`),
	KEY name (name)
);";

// term_relationships
$bb_queries['term_relationships'] = "CREATE TABLE IF NOT EXISTS `$bbdb->term_relationships` (
	`object_id` bigint(20) NOT NULL default 0,
	`term_taxonomy_id` bigint(20) NOT NULL default 0,
	`user_id` bigint(20) NOT NULL default 0,
	`term_order` int(11) NOT NULL default 0,
	PRIMARY KEY (`object_id`, `term_taxonomy_id`),
	KEY `term_taxonomy_id` (`term_taxonomy_id`)
);";

// term_taxonomy
$bb_queries['term_taxonomy'] = "CREATE TABLE IF NOT EXISTS `$bbdb->term_taxonomy` (
	`term_taxonomy_id` bigint(20) NOT NULL auto_increment,
	`term_id` bigint(20) NOT NULL default 0,
	`taxonomy` varchar(32) NOT NULL default '',
	`description` longtext NOT NULL,
	`parent` bigint(20) NOT NULL default 0,
	`count` bigint(20) NOT NULL default 0,
	PRIMARY KEY (`term_taxonomy_id`),
	UNIQUE KEY `term_id_taxonomy` (`term_id`, `taxonomy`),
	KEY `taxonomy` (`taxonomy`)
);";

// topics
$bb_queries['topics'] = "CREATE TABLE IF NOT EXISTS `$bbdb->topics` (
	`topic_id` bigint(20) NOT NULL auto_increment,
	`topic_title` varchar(100) NOT NULL default '',
	`topic_slug` varchar(255) NOT NULL default '',
	`topic_poster` bigint(20) NOT NULL default 0,
	`topic_poster_name` varchar(40) NOT NULL default 'Anonymous',
	`topic_last_poster` bigint(20) NOT NULL default 0,
	`topic_last_poster_name` varchar(40) NOT NULL default '',
	`topic_start_time` datetime NOT NULL default '0000-00-00 00:00:00',
	`topic_time` datetime NOT NULL default '0000-00-00 00:00:00',
	`forum_id` int(10) NOT NULL default 1,
	`topic_status` tinyint(1) NOT NULL default 0,
	`topic_open` tinyint(1) NOT NULL default 1,
	`topic_last_post_id` bigint(20) NOT NULL default 1,
	`topic_sticky` tinyint(1) NOT NULL default 0,
	`topic_posts` bigint(20) NOT NULL default 0,
	`tag_count` bigint(20) NOT NULL default 0,
	PRIMARY KEY (`topic_id`),
	KEY `topic_slug` (`topic_slug`),
	KEY `forum_time` (`forum_id`, `topic_time`),
	KEY `user_start_time` (`topic_poster`, `topic_start_time`),
	KEY `stickies` (`topic_status`, `topic_sticky`, `topic_time`)
);";

if ( bb_get_option( 'wp_table_prefix' ) || ( defined( 'BB_SCHEMA_IGNORE_WP_USERS_TABLES' ) && BB_SCHEMA_IGNORE_WP_USERS_TABLES ) ) {
	// Don't add user tables
} else {
	// users - 'user_login', 'user_nicename' and 'user_registered' indices are inconsistent with WordPress
	$bb_queries['users'] = "CREATE TABLE IF NOT EXISTS `$bbdb->users` (
		`ID` bigint(20) unsigned NOT NULL auto_increment,
		`user_login` varchar(60) NOT NULL default '',
		`user_pass` varchar(64) NOT NULL default '',
		`user_nicename` varchar(50) NOT NULL default '',
		`user_email` varchar(100) NOT NULL default '',
		`user_url` varchar(100) NOT NULL default '',
		`user_registered` datetime NOT NULL default '0000-00-00 00:00:00',
		`user_status` int(11) NOT NULL default 0,
		`display_name` varchar(250) NOT NULL default '',
		PRIMARY KEY (`ID`),
		UNIQUE KEY `user_login` (`user_login`),
		UNIQUE KEY `user_nicename` (`user_nicename`),
		KEY `user_registered` (`user_registered`)
	);";

	// usermeta
	$bb_queries['usermeta'] = "CREATE TABLE IF NOT EXISTS `$bbdb->usermeta` (
		`umeta_id` bigint(20) NOT NULL auto_increment,
		`user_id` bigint(20) NOT NULL default 0,
		`meta_key` varchar(255),
		`meta_value` longtext,
		PRIMARY KEY (`umeta_id`),
		KEY `user_id` (`user_id`),
		KEY `meta_key` (`meta_key`)
	);";
}

$bb_queries = apply_filters( 'bb_schema_pre_charset', $bb_queries );

// Set the charset and collation on each table
foreach ($bb_queries as $_table_name => $_sql) {
	// Skip SQL that isn't creating a table
	if (!preg_match('@^\s*CREATE\s+TABLE\s+@im', $_sql)) {
		continue;
	}
	
	// Skip if the table's database doesn't support collation
	if (!$bbdb->has_cap('collation', $bbdb->$_table_name)) {
		continue;
	}
	
	// Find out if the table has a custom database set
	if (
		isset($bbdb->db_tables) &&
		is_array($bbdb->db_tables) &&
		isset($bbdb->db_tables[$bbdb->$_table_name])
	) {
		// Set the database for this table
		$_database = $bbdb->db_tables[$bbdb->$_table_name];
	} else {
		// Set the default global database
		$_database = 'dbh_global';
	}
	
	// Make sure the database exists
	if (
		isset($bbdb->db_servers) &&
		is_array($bbdb->db_servers) &&
		isset($bbdb->db_servers[$_database]) &&
		is_array($bbdb->db_servers[$_database])
	) {
		$_charset_collate = '';
		if (isset($bbdb->db_servers[$_database]['charset']) && !empty($bbdb->db_servers[$_database]['charset'])) {
			// Add a charset if set
			$_charset_collate .= ' DEFAULT CHARACTER SET \'' . $bbdb->db_servers[$_database]['charset'] . '\'';
		}
		if (isset($bbdb->db_servers[$_database]['collate']) && !empty($bbdb->db_servers[$_database]['collate'])) {
			// Add a collation if set
			$_charset_collate .= ' COLLATE \'' . $bbdb->db_servers[$_database]['collate'] . '\'';
		}
		if ($_charset_collate) {
			// Modify the SQL
			$bb_queries[$_table_name] = str_replace(';', $_charset_collate . ';', $_sql);
		}
	}
	unset($_database, $_charset_collate);
}
unset($_table_name, $_sql);

$bb_queries = apply_filters( 'bb_schema', $bb_queries );

// These elements in the schema may need to be ignored when doing comparisons due to inconsistencies with WordPress
if ( bb_get_option('wp_table_prefix') || ( defined( 'BB_SCHEMA_IGNORE_WP_USERS_KEYS' ) && BB_SCHEMA_IGNORE_WP_USERS_KEYS ) ) {
	$bb_schema_ignore = array(
		'tables' => array(),
		'columns' => array(),
		'indices' => array(
			$bbdb->users => array(
				'user_login',
				'user_nicename',
				'user_registered'
			)
		)
	);
} else {
	$bb_schema_ignore = false;
}

if ( bb_get_option('wp_table_prefix') || ( defined( 'BB_SCHEMA_IGNORE_WP_USERS_TABLES' ) && BB_SCHEMA_IGNORE_WP_USERS_TABLES ) ) {
	if ( $bb_schema_ignore ) {
		$bb_schema_ignore['tables'] = array( $bbdb->users, $bbdb->usermeta );
	} else {
		$bb_schema_ignore = array(
			'tables' => array( $bbdb->users, $bbdb->usermeta ),
			'columns' => array(),
			'indices' => array()
		);
	}
}

$bb_schema_ignore = apply_filters( 'bb_schema_ignore', $bb_schema_ignore );

do_action( 'bb_schema_defined' );

?>
