<?php

class BB_Query {
	var $type;
	var $query;
	var $query_id;

	var $query_vars = array();
	var $not_set = array();
	var $request;
	var $match_query = false;

	var $results;
	var $errors;
	var $count = 0;
	var $found_rows = false;

	// Can optionally pass unique id string to help out filters
	function BB_Query( $type = 'topic', $query = '', $id = '' ) {
		$this->init( $type, $query, $id );

		if ( !empty($this->query) )
			$this->query();
	}

	function init( $type = null, $query = null, $id = null ) {
		if ( !is_null($type) || !isset($this->type) )
			$this->type = is_null($type) ? 'topic' : $type;
		if ( !is_null($query) || !isset($this->query) )
			$this->query = $query;
		if ( !is_null($id) || !isset($this->query_id) )
			$this->query_id = $id;

		$this->query_vars = array();
		$this->not_set = array();
		unset($this->request);
		$this->match_query = false;

		unset($this->results, $this->errors);
		$this->count = 0;
		$this->found_rows = false;
	}

	function &query() {
		global $bbdb;

		if ( $args = func_get_args() )
			call_user_func_array( array(&$this, 'init'), $args );

		if ( !$this->generate_query() )
			return;

		do_action_ref_array( 'bb_query', array(&$this) );

		$key = md5( $this->request );
		if ( false === $cached_ids = wp_cache_get( $key, 'bb_query' ) ) {
			if ( 'post' == $this->type ) {
				$this->results = bb_cache_posts( $this->request, $this->query_vars['post_id_only'] ); // This always appends meta
				$_the_id = 'post_id';
				$this->query_vars['append_meta'] = false;
			} else {
				$this->results = $bbdb->get_results( $this->request );
				$_the_id = 'topic_id';
			}
			$cached_ids = array();
			if ( is_array($this->results) )
				foreach ( $this->results as $object )
					$cached_ids[] = $object->$_the_id;
			wp_cache_set( $key, $cached_ids, 'bb_query' );
		} else {
			if ( 'post' == $this->type ) {
				$_query_ids = array();
				$_cached_posts = array();
				foreach ( $cached_ids as $_cached_id ) {
					if ( false !== $_post = wp_cache_get( $_cached_id, 'bb_post' ) ) {
						$_cached_posts[$_post->post_id] = $_post;
					} else {
						$_query_ids[] = $_cached_id;
					}
				}
				if ( count( $_query_ids ) ) {
					$_query_ids = join( ',', array_map( 'intval', $_query_ids ) );
					$results = $bbdb->get_results( "SELECT * FROM $bbdb->posts WHERE post_id IN($_query_ids)" );
					$results = array_merge( $results, $_cached_posts );
				} else {
					$results = $_cached_posts;
				}
				$_the_id = 'post_id';
			} else {
				$_query_ids = array();
				$_cached_topics = array();
				foreach ( $cached_ids as $_cached_id ) {
					if ( false !== $_topic = wp_cache_get( $_cached_id, 'bb_topic' ) ) {
						$_cached_topics[$_topic->topic_id] = $_topic;
					} else {
						$_query_ids[] = $_cached_id;
					}
				}
				if ( count( $_query_ids ) ) {
					$_query_ids = join( ',', array_map( 'intval', $_query_ids ) );
					$results = $bbdb->get_results( "SELECT * FROM $bbdb->topics WHERE topic_id IN($_query_ids)" );
					$results = array_merge( $results, $_cached_topics );
				} else {
					$results = $_cached_topics;
				}
				$_the_id = 'topic_id';
			}

			$this->results = array();
			$trans = array();

			foreach ( $results as $object )
				$trans[$object->$_the_id] = $object;
			foreach ( $cached_ids as $cached_id )
				$this->results[] = $trans[$cached_id];
		}

		$this->count = count( $this->results );

		if ( false === $this->found_rows && $this->query_vars['count'] ) // handles FOUND_ROWS() or COUNT(*)
			$this->found_rows = bb_count_last_query( $this->request );
		if ( 'post' == $this->type ) {
			if ( $this->query_vars['append_meta'] )
				$this->results = bb_append_meta( $this->results, 'post' );
			if ( $this->query_vars['cache_users'] )
				bb_post_author_cache( $this->results );
			if ( $this->query_vars['cache_topics'] )
				bb_cache_post_topics( $this->results );
		} else {
			if ( $this->query_vars['append_meta'] )
				$this->results = bb_append_meta( $this->results, 'topic' );
		}

		return $this->results;
	}

	function generate_query() {
		if ( $args = func_get_args() )
			call_user_func_array( array(&$this, 'init'), $args );

		$this->parse_query();

		// Allow filter to abort query
		if ( false === $this->query_vars )
			return false;

		if ( 'post' == $this->type )
			$this->generate_post_sql();
		else
			$this->generate_topic_sql();

		return $this->request;
	}

	// $defaults = vars to use if not set in GET, POST or allowed
	// $allowed = array( key_name => value, key_name, key_name, key_name => value );
	// 	key_name => value pairs override anything from defaults, GET, POST
	//	Lone key_names are a whitelist.  Only those can be set by defaults, GET, POST
	//	If there are no lone key_names, allow everything but still override with key_name => value pairs
	//	Ex: $allowed = array( 'topic_status' => 0, 'post_status' => 0, 'topic_author', 'started' );
	//		Will only take topic_author and started values from defaults, GET, POST and will query with topic_status = 0 and post_status = 0
	function &query_from_env( $type = 'topic', $defaults = null, $allowed = null, $id = '' ) {
		$this->init_from_env( $type, $defaults, $allowed, $id );
		return $this->query();
	}

	function init_from_env( $type = 'topic', $defaults = null, $allowed = null, $id = '' ) {
		$vars = $this->fill_query_vars( array() );

		$defaults  = wp_parse_args($defaults);
		$get_vars  = stripslashes_deep( $_GET );
		$post_vars = stripslashes_deep( $_POST );
		$allowed   = wp_parse_args($allowed);

		$_allowed = array();
		foreach ( array_keys($allowed) as $k ) {
			if ( is_numeric($k) ) {
				$_allowed[] = $allowed[$k];
				unset($allowed[$k]);
			} elseif ( !isset($$k) ) {
				$$k = $allowed[$k];
			}
		}

		extract($post_vars, EXTR_SKIP);
		extract($get_vars, EXTR_SKIP);
		extract($defaults, EXTR_SKIP);

		$vars = $_allowed ? compact($_allowed, array_keys($allowed)) : compact(array_keys($vars));

		$this->init( $type, $vars, $id );
	}

	function fill_query_vars( $array ) {
		// Should use 0, '' for empty values
		// Function should return false iff not set

		// parameters commented out are handled farther down

		$ints = array(
//			'page',		 // Defaults to global or number in URI
//			'per_page',	 // Defaults to page_topics
			'tag_id',	 // one tag ID
			'favorites', // one user ID,
			'offset',	 // first item to query
			'number'	 // number of items to retrieve
		);

		$parse_ints = array(
			// Both
			'post_id',
			'topic_id',
			'forum_id',

			// Topics
			'topic_author_id',
			'post_count',
			'tag_count',

			// Posts
			'post_author_id',
			'position'
		);

		$dates = array(
			'started',	// topic
			'updated',	// topic
			'posted'	// post
		);

		$others = array(
			// Both
			'topic',	// one topic name
			'forum',	// one forum name
			'tag',		// one tag name

			// Topics
			'topic_author',	// one username
			'topic_status',	// *normal, deleted, all, parse_int ( and - )
			'open',			// *all, yes = open, no = closed, parse_int ( and - )
			'sticky',		// *all, no = normal, forum, super = front, parse_int ( and - )
			'meta_key',		// one meta_key ( and - )
			'meta_value',	// range
			'topic_title',	// LIKE search.  Understands "doublequoted strings"
			'search',		// generic search: topic_title OR post_text
							// Can ONLY be used in a topic query
							// Returns additional search_score and (concatenated) post_text columns

			// Posts
			'post_author',	// one username
			'post_status',	// *noraml, deleted, all, parse_int ( and - )
			'post_text',	// FULLTEXT search
							// Returns additional search_score column (and (concatenated) post_text column if topic query)
			'poster_ip',	// one IPv4 address

			// SQL
			'index_hint',	// A full index hint using valid index hint syntax, can be multiple hints an array
			'order_by',		// fieldname
			'order',		// *DESC, ASC
			'count',		// *false = none, true = COUNT(*), found_rows = FOUND_ROWS()
			'_join_type',	// not implemented: For benchmarking only.  Will disappear. join (1 query), in (2 queries)

			// Utility
//			'append_meta',	// *true, false: topics only
//			'cache_users',	// *true, false
//			'cache_topics,	// *true, false: posts only
//			'post_id_only', // true, *false: this query is only returning post IDs
			'cache_posts'	 // not implemented: none, first, last
		);

		foreach ( $ints as $key )
			if ( ( false === $array[$key] = isset($array[$key]) ? (int) $array[$key] : false ) && isset($this) )
				$this->not_set[] = $key;

		foreach ( $parse_ints as $key )
			if ( ( false === $array[$key] = isset($array[$key]) ? preg_replace( '/[^<=>0-9,-]/', '', $array[$key] ) : false ) && isset($this) )
				$this->not_set[] = $key;

		foreach ( $dates as $key )
			if ( ( false === $array[$key] = isset($array[$key]) ? preg_replace( '/[^<>0-9-]/', '', $array[$key] ) : false ) && isset($this) )
				$this->not_set[] = $key;

		foreach ( $others as $key ) {
			if ( !isset($array[$key]) )
				$array[$key] = false;
			if ( isset($this) && false === $array[$key] )
				$this->not_set[] = $key;
		}

		// Both
		if ( isset($array['page']) )
			$array['page'] = (int) $array['page'];
		elseif ( isset($GLOBALS['page']) )
			$array['page'] = (int) $GLOBALS['page'];
		else
			$array['page'] = bb_get_uri_page();

		if ( $array['page'] < 1 )
			$array['page'] = 1;

		$array['per_page'] = isset($array['per_page']) ? (int) $array['per_page'] : 0;
		if ( $array['per_page'] < -1 )
			$array['per_page'] = 1;

		// Posts
		if ( ( !$array['poster_ip'] = isset($array['poster_ip']) ? preg_replace("@[^0-9a-f:.]@i", '', $array['poster_ip']) : false ) && isset($this) ) {
			$this->not_set[] = 'poster_ip';
			$array['poster_ip'] = false;
		}

		// Utility
		$array['append_meta']  = isset($array['append_meta'])  ? (int) (bool) $array['append_meta']  : 1;
		$array['cache_users']  = isset($array['cache_users'])  ? (int) (bool) $array['cache_users']  : 1;
		$array['cache_topics'] = isset($array['cache_topics']) ? (int) (bool) $array['cache_topics'] : 1;
		$array['post_id_only'] = isset($array['post_id_only']) ? (int) (bool) $array['post_id_only'] : 1;

		// Only one FULLTEXT search per query please
		if ( $array['search'] )
			$array['post_text'] = false;

		return $array;
	}

	// Parse a query string and set query flag booleans.
	function parse_query() {
		if ( $args = func_get_args() )
			call_user_func_array( array(&$this, 'init'), $args );

		if ( is_array($this->query) )
			$this->query_vars = $this->query;
		else
			wp_parse_str($this->query, $this->query_vars);

		do_action_ref_array('bb_parse_query', array(&$this));

		if ( false === $this->query_vars )
			return;

		$this->query_vars = $this->fill_query_vars($this->query_vars);
	}

	function get($query_var) {
		return isset($this->query_vars[$query_var]) ? $this->query_vars[$query_var] : null;
	}

	function set($query_var, $value) {
		$this->query_vars[$query_var] = $value;
	}

	function generate_topic_sql( $_part_of_post_query = false ) {
		global $bbdb;

		$q =& $this->query_vars;
		$distinct = '';
		$sql_calc_found_rows = 'found_rows' === $q['count'] ? 'SQL_CALC_FOUND_ROWS' : ''; // unfiltered
		$fields = 't.*';
		$index_hint = '';
		$join = '';
		$where = '';
		$group_by = '';
		$having = '';
		$order_by = '';

		$post_where = '';
		$post_queries = array('post_author_id', 'post_author', 'posted', 'post_status', 'position', 'post_text', 'poster_ip');

		if ( !$_part_of_post_query && ( $q['search'] || array_diff($post_queries, $this->not_set) ) ) :
			$join .= " JOIN $bbdb->posts as p ON ( t.topic_id = p.topic_id )";
			$post_where = $this->generate_post_sql( true );
			if ( $q['search'] ) {
				$post_where .= ' AND ( ';
				$post_where .= $this->generate_topic_title_sql( $q['search'] );
				$post_where .= ' OR ';
				$post_where .= $this->generate_post_text_sql( $q['search'] );
				$post_where .= ' )';
			}

			$group_by = 't.topic_id';

			$fields .= ", MIN(p.post_id) as post_id";

			if ( $bbdb->has_cap( 'GROUP_CONCAT', $bbdb->posts ) )
				$fields .= ", GROUP_CONCAT(p.post_text SEPARATOR ' ') AS post_text";
			else
				$fields .= ", p.post_text";

			if ( $this->match_query ) {
				$fields .= ", AVG($this->match_query) AS search_score";
				if ( !$q['order_by'] )
					$q['order_by'] = 'search_score';
			} elseif ( $q['search'] || $q['post_text'] ) {
				$fields .= ", 0 AS search_score";
			}
		endif;

		if ( !$_part_of_post_query ) :
			if ( $q['post_id'] ) :
				$post_topics = $post_topics_no = array();
				$op = substr($q['post_id'], 0, 1);
				if ( in_array($op, array('>','<')) ) :
					$post_topics = $bbdb->get_col( "SELECT DISTINCT topic_id FROM $bbdb->posts WHERE post_id $op '" . (int) substr($q['post_id'], 1) . "'" );
				else :
					$posts = explode(',', $q['post_id']);
					$get_posts = array();
					foreach ( $posts as $post_id ) {
						$post_id = (int) $post_id;
						$_post_id = abs($post_id);
						$get_posts[] = $_post_id;
					}
					bb_cache_posts( $get_posts );

					foreach ( $posts as $post_id ) :
						$post = bb_get_post( abs($post_id) );
						if ( $post_id < 0 )
							$post_topics_no[] = $post->topic_id;
						else
							$post_topics[] = $post->topic_id;
					endforeach;
				endif;
				if ( $post_topics )
					$where .= " AND t.topic_id IN (" . join(',', $post_topics) . ")";
				if ( $post_topics_no )
					$where .= " AND t.topic_id NOT IN (" . join(',', $post_topics_no) . ")";
			endif;

			if ( $q['topic_id'] ) :
				$where .= $this->parse_value( 't.topic_id', $q['topic_id'] );
			elseif ( $q['topic'] ) :
				$q['topic'] = bb_slug_sanitize( $q['topic'] );
				$where .= " AND t.topic_slug = '$q[topic]'";
			endif;

			if ( $q['forum_id'] ) :
				$where .= $this->parse_value( 't.forum_id', $q['forum_id'] );
			elseif ( $q['forum'] ) :
				if ( !$q['forum_id'] = bb_get_id_from_slug( 'forum', $q['forum'] ) )
					$this->error( 'query_var:forum', 'No forum by that name' );
				$where .= " AND t.forum_id = $q[forum_id]";
			endif;

			if ( $q['tag'] && !is_int($q['tag_id']) )
				$q['tag_id'] = (int) bb_get_tag_id( $q['tag'] );

			if ( is_numeric($q['tag_id']) ) :
				$join .= " JOIN `$bbdb->term_relationships` AS tr ON ( t.`topic_id` = tr.`object_id` AND tr.`term_taxonomy_id` = $q[tag_id] )";
			endif;

			if ( is_numeric($q['favorites']) && $f_user = bb_get_user( $q['favorites'] ) )
				$where .= $this->parse_value( 't.topic_id', $f_user->favorites );
		endif; // !_part_of_post_query

		if ( $q['topic_title'] )
			$where .= ' AND ' . $this->generate_topic_title_sql( $q['topic_title'] );

		if ( $q['started'] )
			$where .= $this->date( 't.topic_start_time', $q['started'] );

		if ( $q['updated'] )
			$where .= $this->date( 't.topic_time', $q['updated'] );

		if ( $q['topic_author_id'] ) :
			$where .= $this->parse_value( 't.topic_poster', $q['topic_author_id'] );
		elseif ( $q['topic_author'] ) :
			$user = bb_get_user( $q['topic_author'], array( 'by' => 'login' ) );
			if ( !$q['topic_author_id'] = (int) $user->ID )
				$this->error( 'query_var:user', 'No user by that name' );
			$where .= " AND t.topic_poster = $q[topic_author_id]";
		endif;

		if ( !$q['topic_status'] ) :
			$where .= " AND t.topic_status = '0'";
		elseif ( false === strpos($q['topic_status'], 'all') ) :
			$stati = array( 'normal' => 0, 'deleted' => 1 );
			$q['topic_status'] = str_replace(array_keys($stati), array_values($stati), $q['topic_status']);
			$where .= $this->parse_value( 't.topic_status', $q['topic_status'] );
		endif;

		if ( false !== $q['open'] && false === strpos($q['open'], 'all') ) :
			$stati = array( 'no' => 0, 'closed' => 0, 'yes' => 1, 'open' => 1 );
			$q['open'] = str_replace(array_keys($stati), array_values($stati), $q['open']);
			$where .= $this->parse_value( 't.topic_open', $q['open'] );
		endif;

		if ( false !== $q['sticky'] && false === strpos($q['sticky'], 'all') ) :
			$stickies = array( 'no' => 0, 'normal' => 0, 'forum' => 1, 'super' => 2, 'front' => 2, 'sticky' => '-0' );
			$q['sticky'] = str_replace(array_keys($stickies), array_values($stickies), $q['sticky']);
			$where .= $this->parse_value( 't.topic_sticky', $q['sticky'] );
		endif;

		if ( false !== $q['post_count'] )
			$where .= $this->parse_value( 't.topic_posts', $q['post_count'] );

		if ( false !== $q['tag_count'] )
			$where .= $this->parse_value( 't.tag_count', $q['tag_count'] );

		if ( $q['meta_key'] && $q['meta_key'] = preg_replace('|[^a-z0-9_-]|i', '', $q['meta_key']) ) :
			if ( '-' == substr($q['meta_key'], 0, 1) ) :
				$join  .= " LEFT JOIN $bbdb->meta AS tm ON ( tm.object_type = 'bb_topic' AND t.topic_id = tm.object_id AND tm.meta_key = '" . substr( $q['meta_key'], 1 ) . "' )";
				$where .= " AND tm.meta_key IS NULL";
			else :
				$join  .= " JOIN $bbdb->meta AS tm ON ( tm.object_type = 'bb_topic' AND t.topic_id = tm.object_id AND tm.meta_key = '$q[meta_key]' )";

				if ( $q['meta_value'] ) :
					$q['meta_value'] = maybe_serialize( $q['meta_value'] );
					if ( strpos( $q['meta_value'], 'NULL' ) !== false )
						$join = ' LEFT' . $join;
					$where .= $this->parse_value( 'tm.meta_value', $q['meta_value'] );
				endif;
			endif;
		endif;

		// Just getting topic part for inclusion in post query
		if ( $_part_of_post_query )
			return $where;

		$where .= $post_where;

		if ( $where ) // Get rid of initial " AND " (this is pre-filters)
			$where = substr($where, 5);

		if ( $q['index_hint'] )
			$index_hint = $q['index_hint'];

		if ( $q['order_by'] )
			$order_by = $q['order_by'];
		else
			$order_by = 't.topic_time';

		$bits = compact( array('distinct', 'sql_calc_found_rows', 'fields', 'index_hint', 'join', 'where', 'group_by', 'having', 'order_by') );
		$this->request = $this->_filter_sql( $bits, "$bbdb->topics AS t" );
		return $this->request;
	}

	function generate_post_sql( $_part_of_topic_query = false ) {
		global $bbdb;

		$q =& $this->query_vars;
		$distinct = '';
		$sql_calc_found_rows = 'found_rows' === $q['count'] ? 'SQL_CALC_FOUND_ROWS' : ''; // unfiltered
		$fields = 'p.*';
		$index_hint = '';
		$join = '';
		$where = '';
		$group_by = '';
		$having = '';
		$order_by = '';

		$topic_where = '';
		$topic_queries = array( 'topic_author_id', 'topic_author', 'topic_status', 'post_count', 'tag_count', 'started', 'updated', 'open', 'sticky', 'meta_key', 'meta_value', 'topic_title' );
		if ( !$_part_of_topic_query && array_diff($topic_queries, $this->not_set) ) :
			$join .= " JOIN $bbdb->topics as t ON ( t.topic_id = p.topic_id )";
			$topic_where = $this->generate_topic_sql( true );
		endif;
		
		if ( !$_part_of_topic_query ) :
			if ( $q['post_id'] )
				$where .= $this->parse_value( 'p.post_id', $q['post_id'] );

			if ( $q['topic_id'] ) :
				$where .= $this->parse_value( 'p.topic_id', $q['topic_id'] );
			elseif ( $q['topic'] ) :
				if ( !$q['topic_id'] = bb_get_id_from_slug( 'topic', $q['topic'] ) )
					$this->error( 'query_var:topic', 'No topic by that name' );
				$where .= " AND p.topic_id = " . $q['topic_id'];
			endif;

			if ( $q['forum_id'] ) :
				$where .= $this->parse_value( 'p.forum_id', $q['forum_id'] );
			elseif ( $q['forum'] ) :
				if ( !$q['forum_id'] = bb_get_id_from_slug( 'forum', $q['forum'] ) )
					$this->error( 'query_var:forum', 'No forum by that name' );
				$where .= " AND p.forum_id = " . $q['forum_id'];
			endif;

			if ( $q['tag'] && !is_int($q['tag_id']) )
				$q['tag_id'] = (int) bb_get_tag_id( $q['tag'] );

			if ( is_numeric($q['tag_id']) ) :
				$join .= " JOIN `$bbdb->term_relationships` AS tr ON ( p.`topic_id` = tr.`object_id` AND tr.`term_taxonomy_id` = $q[tag_id] )";
			endif;

			if ( is_numeric($q['favorites']) && $f_user = bb_get_user( $q['favorites'] ) )
				$where .= $this->parse_value( 'p.topic_id', $f_user->favorites );
		endif; // !_part_of_topic_query

		if ( $q['post_text'] ) :
			$where  .= ' AND ' . $this->generate_post_text_sql( $q['post_text'] );
			if ( $this->match_query ) {
				$fields .= ", $this->match_query AS search_score";
				if ( !$q['order_by'] )
					$q['order_by'] = 'search_score';
			} else {
				$fields .= ', 0 AS search_score';
			}
		endif;

		if ( $q['posted'] )
			$where .= $this->date( 'p.post_time', $q['posted'] );

		if ( $q['post_author_id'] ) :
			$where .= $this->parse_value( 'p.poster_id', $q['post_author_id'] );
		elseif ( $q['post_author'] ) :
			$user = bb_get_user( $q['post_author'], array( 'by' => 'login' ) );
			if ( !$q['post_author_id'] = (int) $user->ID )
				$this->error( 'query_var:user', 'No user by that name' );
			$where .= " AND p.poster_id = $q[post_author_id]";
		endif;

		if ( !$q['post_status'] ) :
			$where .= " AND p.post_status = '0'";
		elseif ( false === strpos($q['post_status'], 'all') ) :
			$stati = array( 'normal' => 0, 'deleted' => 1 );
			$q['post_status'] = str_replace(array_keys($stati), array_values($stati), $q['post_status']);
			$where .= $this->parse_value( 'p.post_status', $q['post_status'] );
		endif;

		if ( false !== $q['position'] )
			$where .= $this->parse_value( 'p.post_position', $q['position'] );

		if ( false !== $q['poster_ip'] )
			$where .= " AND poster_ip = '" . $q['poster_ip'] . "'";

		// Just getting post part for inclusion in topic query
		if ( $_part_of_topic_query )
			return $where;

		$where .= $topic_where;

		if ( $where ) // Get rid of initial " AND " (this is pre-filters)
			$where = substr($where, 5);

		if ( $q['index_hint'] )
			$index_hint = $q['index_hint'];

		if ( $q['order_by'] )
			$order_by = $q['order_by'];
		else
			$order_by = 'p.post_time';

		$bits = compact( array('distinct', 'sql_calc_found_rows', 'fields', 'index_hint', 'join', 'where', 'group_by', 'having', 'order_by') );
		$this->request = $this->_filter_sql( $bits, "$bbdb->posts AS p" );

		return $this->request;
	}

	function generate_topic_title_sql( $string ) {
		global $bbdb;
		$string = trim($string);

		if ( !preg_match_all('/".*?("|$)|((?<=[\s",+])|^)[^\s",+]+/', $string, $matches) ) {
			$string = $bbdb->escape($string);
			return "(t.topic_title LIKE '%$string%')";
		}

		$where = '';

		foreach ( $matches[0] as $match ) {
			$term = trim($match, "\"\n\r ");
			$term = $bbdb->escape($term);
			$where .= " AND t.topic_title LIKE '%$term%'";
		}

		if ( count($matches[0]) > 1 && $string != $matches[0][0] ) {
			$string = $bbdb->escape($string);
			$where .= " OR t.topic_title LIKE '%$string%'";
		}

		return '(' . substr($where, 5) . ')';
	}

	function generate_post_text_sql( $string ) {
		global $bbdb;
		$string = trim($string);
		$_string = $bbdb->escape( $string );
		if ( strlen($string) < 5 )
			return "p.post_text LIKE '%$_string%'";

		return $this->match_query = "MATCH(p.post_text) AGAINST('$_string')";
	}

	function _filter_sql( $bits, $from ) {
		global $bbdb;

		$q =& $this->query_vars;

		// MySQL 5.1 allows multiple index hints per query - earlier versions only get the first hint
		if ( $bits['index_hint'] ) {
			if ( !is_array( $bits['index_hint'] ) ) {
				$bits['index_hint'] = array( (string) $bits['index_hint'] );
			}
			if ( $bbdb->has_cap( 'index_hint_for_any' ) ) {
				// 5.1 <= MySQL
				$_regex = '/\s*(USE|IGNORE|FORCE)\s+(INDEX|KEY)\s+(FOR\s+(JOIN|ORDER\s+BY|GROUP\s+BY)\s+)?\(\s*`?[a-z0-9_]+`?(\s*,\s*`?[a-z0-9_]+`?)*\s*\)\s*/i';
			} elseif ( $bbdb->has_cap( 'index_hint_for_join' ) ) {
				// 5.0 <= MySQL < 5.1
				$_regex = '/\s*(USE|IGNORE|FORCE)\s+(INDEX|KEY)\s+(FOR\s+JOIN\s+)?\(\s*`?[a-z0-9_]+`?(\s*,\s*`?[a-z0-9_]+`?)*\s*\)\s*/i';
			} else {
				// MySQL < 5.0
				$_regex = '/\s*(USE|IGNORE|FORCE)\s+(INDEX|KEY)\s+\(\s*`?[a-z0-9_]+`?(\s*,\s*`?[a-z0-9_]+`?)*\s*\)\s*/i';
			}
			$_index_hint = array();
			foreach ( $bits['index_hint'] as $_hint ) {
				if ( preg_match( $_regex, $_hint ) ) {
					$_index_hint[] = trim( $_hint );
				}
			}
			unset( $_regex, $_hint );
			if ( $bbdb->has_cap( 'index_hint_lists' ) ) {
				// 5.1 <= MySQL
				$bits['index_hint'] = join( ' ', $_index_hint );
			} else {
				// MySQL < 5.1
				$bits['index_hint'] = isset( $_index_hint[0] ) ? $_index_hint[0] : '';
			}
			unset( $_index_hint );
		}

		$q['order'] = strtoupper($q['order']);
		if ( $q['order'] && in_array($q['order'], array('ASC', 'DESC')) )
			$bits['order_by'] .= " $q[order]";
		else
			$bits['order_by'] .= " DESC";

		$bits['limit'] = '';

		// When offset and number are provided, skip per_page and limit checks
		if ( !empty( $q['offset'] ) && !empty( $q['number'] ) ) {
			$bits['limit'] .= $q['offset'] . ", " . $q['number'];

		// Else proceed as normal
		} else {
			if ( !$q['per_page'] ) {
				$q['per_page'] = (int) bb_get_option( 'page_topics' );
			}
	
			if ( $q['per_page'] > 0 ) {
				if ( $q['page'] > 1 ) {
					$bits['limit'] .= $q['per_page'] * ( $q['page'] - 1 ) . ", ";
				}
				$bits['limit'] .= $q['per_page'];
			}
		}

		$name = "get_{$this->type}s_";

		// Unfiltered
		$sql_calc_found_rows = $bits['sql_calc_found_rows'];
		unset($bits['sql_calc_found_rows']);

		foreach ( $bits as $bit => $value ) {
			if ( $this->query_id )
				$value = apply_filters( "{$this->query_id}_$bit", $value );
			$$bit = apply_filters( "$name$bit", $value );
		}

		if ( $where )
			$where = "WHERE $where";
		if ( $group_by )
			$group_by = "GROUP BY $group_by";
		if ( $having )
			$having = "HAVING $having";
		if ( $order_by )
			$order_by = "ORDER BY $order_by";
		if ( $limit )
			$limit = "LIMIT $limit";

		return "SELECT $distinct $sql_calc_found_rows $fields FROM $from $index_hint $join $where $group_by $having $order_by $limit";
	}

	function parse_value( $field, $value = '' ) {
		if ( !$value && !is_numeric($value) )
			return '';

		global $bbdb;

		$op = substr($value, 0, 1);

		// #, =whatever, <#, >#.  Cannot do < and > at same time
		if ( in_array($op, array('<', '=', '>')) ) :
			$value = substr($value, 1);
			$value = is_numeric($value) ? (float) $value : $bbdb->escape( $value );
			return " AND $field $op '$value'";
		elseif ( false === strpos($value, ',') && 'NULL' !== $value && '-NULL' !== $value ) :
			$value = is_numeric($value) ? (float) $value : $bbdb->escape( $value );
			return '-' == $op ? " AND $field != '" . substr($value, 1) . "'" : " AND $field = '$value'";
		endif;

		$y = $n = array();
		foreach ( explode(',', $value) as $v ) {
			$v = is_numeric($v) ? (int) $v : $bbdb->escape( $v );
			if ( '-' == substr($v, 0, 1) )
				if ( $v === '-NULL' )
					$not_null_flag = true;
				else
					$n[] = substr($v, 1);
			else
				if ( $v === 'NULL' )
					$null_flag = true;
				else
					$y[] = $v;
		}

		$r = '';
		if ( $y ) {
			$r .= " AND ";
			if ( $null_flag )
				$r .= "(";
			$r .= "$field IN ('" . join("','", $y) . "')";
			if ( $null_flag )
				$r .= " OR $field IS NULL)";
		} elseif ( $null_flag ) {
			$r .= " AND $field IS NULL";
		}
		
		if ( $n ) {
			$r .= " AND ";
			if ( $not_null_flag )
				$r .= "(";
			$r .= "$field NOT IN ('" . join("','", $n) . "')";
			if ( $not_null_flag )
				$r .= " AND $field IS NOT NULL)";
		} elseif ( $not_null_flag ) {
			$r .= " AND $field IS NOT NULL";
		}

		return $r;
	}

	function date( $field, $date ) {
		if ( !$date && !is_int($date) )
			return '';

		if ( $is_range = false !== strpos( $date, '--' ) )
			$dates = explode( '--', $date, 2 );
		else
			$dates = array( $date );

		$op = false;
		$r = '';
		foreach ( $dates as $date ) {
			if ( $is_range ) {
				$op = $op ? '<' : '>';
				$date = (int) substr($date, 0, 14);
			} else {
				$op = substr($date, 0, 1);
				if ( !in_array($op, array('>', '<')) )
					break;
				$date = (int) substr($date, 1, 14);
			}
			if ( strlen($date) < 14 )
				$date .= str_repeat('0', 14 - strlen($date));
			$r .= " AND $field $op $date";
		}
		if ( $r )
			return $r;

		$date = (int) $date;
		$r = " AND YEAR($field) = " . substr($date, 0, 4);
		if ( strlen($date) > 5 )
			$r .= " AND MONTH($field) = " . substr($date, 4, 2);
		if ( strlen($date) > 7 )
			$r .= " AND DAYOFMONTH($field) = " . substr($date, 6, 2);
		if ( strlen($date) > 9 )
			$r .= " AND HOUR($field) = " . substr($date, 8, 2);
		if ( strlen($date) > 11 )
			$r .= " AND MINUTE($field) = " . substr($date, 10, 2);
		if ( strlen($date) > 13 )
			$r .= " AND SECOND($field) = " . substr($date, 12, 2);
		return $r;
	}

	function error( $code, $message ) {
		if ( is_wp_error($this->errors) )
			$this->errors->add( $code, $message );
		else
			$this->errors = new WP_Error( $code, $message );
	}
}

class BB_Query_Form extends BB_Query {
	var $defaults;
	var $allowed;

	// Can optionally pass unique id string to help out filters
	function BB_Query_Form( $type = 'topic', $defaults = '', $allowed = '', $id = '' ) {
		$this->defaults = wp_parse_args( $defaults );
		$this->allowed  = wp_parse_args( $allowed );
		if ( !empty($defaults) || !empty($allowed) )
			$this->query_from_env($type, $defaults, $allowed, $id);
	}

	function form( $args = null ) {
		$_post = 'post' == $this->type;

		$defaults = array(
			'search' => true,
			'forum'  => true,
			'tag'    => false,
			'open'   => false,
			'topic_author' => false,
			'post_author'  => false,
			'topic_status' => false,
			'post_status'  => false,
			'topic_title'  => false,
			'poster_ip'  => false,

			'method' => 'get',
			'submit' => __('Search &#187;'),
			'action' => ''
		);
		$defaults['id'] = $_post ? 'post-search-form' : 'topic-search-form';

		$args = wp_parse_args( $args, $defaults );
		extract( $args, EXTR_SKIP );

		$id = esc_attr( $id );
		$method = 'get' == strtolower($method) ? 'get' : 'post';
		$submit = esc_attr( $submit );
		if ( !$action = esc_url( $action ) )
			$action = '';

		if ( $this->query_vars )
			$query_vars =& $this->query_vars;
		else
			$query_vars = $this->fill_query_vars( $this->defaults );

		extract($query_vars, EXTR_PREFIX_ALL, 'q');

		$r  = "<form action='$action' method='$method' id='$id' class='search-form'>\n";

		$r .= "\t<fieldset>\n";

		if ( $search ) {
			if ( $_post ) {
				$s_value = esc_attr( $q_post_text );
				$s_name = 'post_text';
				$s_id = 'post-text';
			} else {
				$s_value = esc_attr( $q_search );
				$s_name = $s_id = 'search';
			}
			$r .= "\t<div><label for=\"$s_id\">" . __('Search term') . "</label>\n";
			$r .= "\t\t<div><input name='$s_name' id='$s_id' type='text' class='text-input' value='$s_value' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $forum ) {
			$r .= "\t<div><label for=\"forum-id\">" . __('Forum')  . "</label>\n";
			$r .= "\t\t<div>" . bb_get_forum_dropdown( array( 'selected' => $q_forum_id, 'none' => __('Any') ) ) . "</div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $tag ) {
			$q_tag = esc_attr( $q_tag );
			$r .= "\t<div><label for=\"topic-tag\">" .  __('Tag') . "</label>\n";
			$r .= "\t\t<div><input name='tag' id='topic-tag' type='text' class='text-input' value='$q_tag' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $topic_author ) {
			$q_topic_author = esc_attr( $q_topic_author );
			$r .= "\t<div><label for=\"topic-author\">" . __('Topic author') . "</label>\n";
			$r .= "\t\t<div><input name='topic_author' id='topic-author' type='text' class='text-input' value='$q_topic_author' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $post_author ) {
			$q_post_author = esc_attr( $q_post_author );
			$r .= "\t<div><label for=\"post-author\">" . __('Post author') . "</label>\n";
			$r .= "\t\t<div><input name='post_author' id='post-author' type='text' class='text-input' value='$q_post_author' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		$stati = apply_filters( 'bb_query_form_post_status', array( 'all' => _x( 'All', 'post status' ), '0' => __('Normal'), '1' => __('Deleted') ), $this->type );

		if ( $topic_status ) {
			$r .= "\t<div><label for=\"topic-status\">" . __('Topic status') . "</label>\n";
			$r .= "\t\t<div><select name='topic_status' id='topic-status'>\n";
			foreach ( $stati as $status => $label ) {
				$selected = (string) $status == (string) $q_topic_status ? " selected='selected'" : '';
				$r .= "\t\t\t<option value='$status'$selected>$label</option>\n";
			}
			$r .= "\t\t</select></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $post_status ) {
			$r .= "\t<div><label for=\"post-status\">" . __('Post status') . "</label>\n";
			$r .= "\t\t<div><select name='post_status' id='post-status'>\n";
			foreach ( $stati as $status => $label ) {
				$selected = (string) $status == (string) $q_post_status ? " selected='selected'" : '';
				$r .= "\t\t\t<option value='$status'$selected>$label</option>\n";
			}
			$r .= "\t\t</select></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $poster_ip ) {
			$r .= "\t<div><label for=\"poster-ip\">" . __('Poster IP address') . "</label>\n";
			$r .= "\t\t<div><input name='poster_ip' id='poster-ip' type='text' class='text-input' value='$q_poster_ip' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $open ) {
			$r .= "\t<div><label for=\"topic-open\">" . __('Open?') . "</label>\n";
			$r .= "\t\t<div><select name='open' id='topic-open'>\n";
			foreach ( array( 'all' => _x( 'All', 'posting status' ), '1' => _x( 'Open', 'posting status' ), '0' => __('Closed') ) as $status => $label ) {
				$label = esc_html( $label );
				$selected = (string) $status == (string) $q_open ? " selected='selected'" : '';
				$r .= "\t\t\t<option value='$status'$selected>$label</option>\n";
			}
			$r .= "\t\t</select></div>\n";
			$r .= "\t</div>\n\n";
		}

		if ( $topic_title ) {
			$q_topic_title = esc_attr( $q_topic_title );
			$r .= "\t<div><label for=\"topic-title\">" . __('Title') . "</label>\n";
			$r .= "\t\t<div><input name='topic_title' id='topic-title' type='text' class='text-input' value='$q_topic_title' /></div>\n";
			$r .= "\t</div>\n\n";
		}

		$r .= apply_filters( 'bb_query_form_inputs', '', $args, $query_vars );

		$r .= "\t<div class=\"submit\"><label for=\"$id-submit\">" . __('Search') . "</label>\n";
		$r .= "\t\t<div><input type='submit' class='button submit-input' value='$submit' id='$id-submit' /></div>\n";
		$r .= "\t</div>\n";

		$r .= "\t</fieldset>\n\n";

		do_action( 'bb_query_form', $args, $query_vars );

		$r .= "</form>\n\n";

		echo $r;
	}
}
