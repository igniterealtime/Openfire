<?php

class WP_Object_Cache
{
	// WordPress would need to be initialised with this:
	// wp_cache_add_global_groups( array( 'users', 'userlogins', 'usermeta', 'site-options', 'site-lookup', 'blog-lookup', 'blog-details', 'rss' ) );
	var $global_groups = array (
		'_cache_keys'
	);

	// WordPress would need to be initialised with this:
	// wp_cache_add_non_persistent_groups( array( 'comment', 'counts' ) );
	var $no_mc_groups = array();

	var $cache = array();
	var $mc = array();
	var $stats = array();
	var $group_ops = array();

	var $default_expiration = 0;

	function WP_Object_Cache()
	{
		global $memcached_servers;

		if ( isset( $memcached_servers ) ) {
			$buckets = $memcached_servers;
		} else {
			$buckets = array('default' => array('127.0.0.1:11211'));
		}

		foreach ( $buckets as $bucket => $servers ) {
			$this->mc[$bucket] = new Memcache();
			foreach ( $servers as $server ) {
				list ( $node, $port ) = explode( ':', $server );
				$this->mc[$bucket]->addServer( $node, $port, true, 1, 1, 15, true, array( $this, 'failure_callback' ) );
				$this->mc[$bucket]->setCompressThreshold( 20000, 0.2 );
			}
		}
	}

	function &get_mc( $group )
	{
		if ( isset( $this->mc[$group] ) ) {
			return $this->mc[$group];
		}
		return $this->mc['default'];
	}

	function failure_callback( $host, $port )
	{
		//error_log( "Connection failure for $host:$port\n", 3, '/tmp/memcached.txt' );
	}

	function close()
	{
		foreach ( $this->mc as $bucket => $mc ) {
			$mc->close();
		}
	}

	function add_global_groups( $groups )
	{
		if ( !is_array( $groups ) ) {
			$groups = (array) $groups;
		}

		$this->global_groups = array_merge( $this->global_groups, $groups );
		$this->global_groups = array_unique( $this->global_groups );
	}

	function add_non_persistent_groups( $groups )
	{
		if ( !is_array( $groups ) ) {
			$groups = (array) $groups;
		}

		$this->no_mc_groups = array_merge( $this->no_mc_groups, $groups );
		$this->no_mc_groups = array_unique( $this->no_mc_groups );
	}

	function key( $key, $group )
	{
		if ( empty( $group ) ) {
			$group = 'default';
		}

		if ( false !== array_search( $group, $this->global_groups ) ) {
			$prefix = '';
		} else {
			$prefix = backpress_get_option( 'application_id' ) . ':';
		}

		return preg_replace( '/\s+/', '', $prefix . $group . ':' . $key );
	}

	function get( $id, $group = 'default' )
	{
		$key = $this->key( $id, $group );
		$mc =& $this->get_mc( $group );

		if ( isset( $this->cache[$key] ) ) {
			$value = $this->cache[$key];
		} elseif ( in_array( $group, $this->no_mc_groups ) ) {
			$value = false;
		} else {
			$value = $mc->get($key);
		}

		@ ++$this->stats['get'];
		$this->group_ops[$group][] = "get $id";

		if ( NULL === $value ) {
			$value = false;
		}

		$this->cache[$key] = $value;

		if ( 'checkthedatabaseplease' === $value ) {
			$value = false;
		}

		return $value;
	}

	/*
	format: $get['group-name'] = array( 'key1', 'key2' );
	*/
	function get_multi( $groups )
	{
		$return = array();
		foreach ( $groups as $group => $ids ) {
			$mc =& $this->get_mc( $group );
			foreach ( $ids as $id ) {
				$key = $this->key( $id, $group );
				if ( isset( $this->cache[$key] ) ) {
					$return[$key] = $this->cache[$key];
					continue;
				} elseif ( in_array( $group, $this->no_mc_groups ) ) {
					$return[$key] = false;
					continue;
				} else {
					$return[$key] = $mc->get( $key );
				}
			}
			if ( $to_get ) {
				$vals = $mc->get_multi( $to_get );
				$return = array_merge( $return, $vals );
			}
		}

		@ ++$this->stats['get_multi'];
		$this->group_ops[$group][] = "get_multi $id";

		$this->cache = array_merge( $this->cache, $return );

		return $return;
	}

	function add( $id, $data, $group = 'default', $expire = 0 )
	{
		$key = $this->key( $id, $group );

		if ( in_array( $group, $this->no_mc_groups ) ) {
			$this->cache[$key] = $data;
			return true;
		}

		$mc =& $this->get_mc( $group );
		$expire = ( $expire == 0 ) ? $this->default_expiration : $expire;
		$result = $mc->add( $key, $data, false, $expire );

		@ ++$this->stats['add'];
		$this->group_ops[$group][] = "add $id";

		if ( false !== $result ) {
			$this->cache[$key] = $data;
			$this->add_key_to_group_keys_cache( $key, $group );
		}

		return $result;
	}

	function set( $id, $data, $group = 'default', $expire = 0 )
	{
		$key = $this->key($id, $group);

		if ( isset( $this->cache[$key] ) && 'checkthedatabaseplease' === $this->cache[$key] ) {
			return false;
		}
		$this->cache[$key] = $data;

		if ( in_array( $group, $this->no_mc_groups ) ) {
			return true;
		}

		$expire = ( $expire == 0 ) ? $this->default_expiration : $expire;
		$mc =& $this->get_mc( $group );
		$result = $mc->set( $key, $data, false, $expire );

		if ( false !== $result ) {
			$this->add_key_to_group_keys_cache($key, $group);
		}

		return $result;
	}

	function replace($id, $data, $group = 'default', $expire = 0) {
		$key = $this->key($id, $group);
		if ( in_array( $group, $this->no_mc_groups ) ) {
			$this->cache[$key] = $data;
			return true;
		}
		$expire = ($expire == 0) ? $this->default_expiration : $expire;
		$mc =& $this->get_mc($group);
		$result = $mc->replace($key, $data, false, $expire);
		if ( false !== $result ) {
			$this->cache[$key] = $data;
			$this->add_key_to_group_keys_cache( $key, $group );
		}
		return $result;
	}

	function delete( $id, $group = 'default' )
	{
		$key = $this->key( $id, $group );

		if ( in_array( $group, $this->no_mc_groups ) ) {
			unset( $this->cache[$key] );
			return true;
		}

		$mc =& $this->get_mc( $group );

		$result = $mc->delete( $key );

		@ ++$this->stats['delete'];
		$this->group_ops[$group][] = "delete $id";

		if ( false !== $result ) {
			unset( $this->cache[$key] );
			$this->remove_key_from_group_keys_cache( $key, $group );
		}

		return $result; 
	}

	function flush( $group = null )
	{
		// Get all the group keys
		if ( !$_groups = $this->get( 1, '_group_keys' ) ) {
			return true;
		}

		if ( !is_array( $_groups ) || !count( $_groups ) ) {
			return $this->delete( 1, '_group_keys' );
		}

		if ( is_null( $group ) ) {
			$results = array();
			foreach ( $_groups as $_group => $_keys ) {
				$results[] = $this->delete_all_keys_in_group_key_cache( $_group );
			}
			if ( in_array( false, $results ) ) {
				return false;
			}
			return true;
		}

		return $this->delete_all_keys_in_group_key_cache( $group );
	}

	// Update the cache of group keys or add a new cache if it isn't there
	function add_key_to_group_keys_cache( $key, $group )
	{
		if ( '_group_keys' === $group ) {
			return;
		}

		//error_log( 'Adding key ' . $key . ' to group ' . $group );

		// Get all the group keys
		if ( !$_groups = $this->get( 1, '_group_keys' ) ) {
			$_groups = array( $group => array( $key ) );
			return $this->add( 1, $_groups, '_group_keys' );
		}

		// Don't blow up if it isn't an array
		if ( !is_array( $_groups ) ) {
			$_groups = array();
		}

		// If this group isn't in there, then insert it
		if ( !isset( $_groups[$group] ) || !is_array( $_groups[$group] ) ) {
			$_groups[$group] = array();
		}

		// If it's already there then do nothing
		if ( in_array( $key, $_groups[$group] ) ) {
			return true;
		}

		$_groups[$group][] = $key;

		// Remove duplicates
		$_groups[$group] = array_unique( $_groups[$group] );

		return $this->replace( 1, $_groups, '_group_keys' );
	}

	// Remove the key from the cache of group keys, delete the cache if it is emptied
	function remove_key_from_group_keys_cache( $key, $group )
	{
		if ( '_group_keys' === $group ) {
			return;
		}

		//error_log( 'Removing key ' . $key . ' from group ' . $group );

		// Get all the group keys
		if ( !$_groups = $this->get( 1, '_group_keys' ) ) {
			return true;
		}

		// If group keys are somehow borked delete it all
		if ( !is_array( $_groups ) ) {
			return $this->delete( 1, '_group_keys' );
		}

		// If it's not there, we're good
		if (
			!isset( $_groups[$group] ) ||
			!is_array( $_groups[$group] ) ||
			!in_array( $key, $_groups[$group] )
		) {
			return true;
		}

		// Remove duplicates
		$_groups[$group] = array_unique( $_groups[$group] );

		// If there is only one key or no keys in the group then delete the group
		if ( 2 > count( $_groups[$group] ) ) {
			unset( $_groups[$group] );
			return $this->replace( 1, $_groups, '_group_keys' );
		}

		// array_unique() made sure there is only one
		if ( $_key = array_search( $key, $_groups[$group] ) ) {
			unset( $_groups[$group][$_key] );
		}

		return $this->replace( 1, $_groups, '_group_keys' );
	}

	function delete_all_keys_in_group_key_cache( $group )
	{
		if ( '_group_keys' === $group ) {
			return;
		}

		//error_log( 'Deleting all keys in group ' . $group );

		// Get all the group keys
		if ( !$_groups = $this->get( 1, '_group_keys' ) ) {
			//error_log( '--> !!!! No groups' );
			return true;
		}

		// Check that what we want to loop over is there
		if ( !is_array( $_groups ) ) {
			//error_log( '--> !!!! Groups is not an array, delete whole key' );
			return $this->delete( 1, '_group_keys' );
		}

		// Check that what we want to loop over is there
		if (
			!isset( $_groups[$group] ) ||
			!is_array( $_groups[$group] )
		) {
			//error_log( '--> !!!! No specific group' );
			return true;
		}

		$_groups[$group] = array_unique( $_groups[$group] );

		$_remaining_keys = array();
		$mc =& $this->get_mc($group);
		foreach ( $_groups[$group] as $_key ) {
			//error_log( '--> Deleting key ' . $_key );
			if ( false !== $mc->delete( $_key ) ) {
				//error_log( '--> Deleted key ' . $_key );
				unset( $this->cache[$_key] );
			}
		}

		unset( $_groups[$group] );
		if ( count( $_groups ) ) {
			//error_log( '--> Remove single group' );
			return $this->replace( 1, $_groups, '_group_keys' );
		}

		//error_log( '--> No groups left, delete whole key' );
		return $this->delete( 1, '_group_keys' );
	}

	function incr( $id, $n, $group )
	{
		$key = $this->key( $id, $group );
		$mc =& $this->get_mc( $group );

		return $mc->increment( $key, $n );
	}

	function decr( $id, $n, $group )
	{
		$key = $this->key( $id, $group );
		$mc =& $this->get_mc( $group );

		return $mc->decrement( $key, $n );
	}

	function colorize_debug_line( $line )
	{
		$colors = array(
			'get' => 'green',
			'set' => 'purple',
			'add' => 'blue',
			'delete' => 'red'
		);

		$cmd = substr( $line, 0, strpos( $line, ' ' ) );

		$cmd2 = "<span style='color:{$colors[$cmd]}'>$cmd</span>";

		return $cmd2 . substr( $line, strlen( $cmd ) ) . "\n";
	}

	function stats()
	{
		echo "<p>\n";
		foreach ( $this->stats as $stat => $n ) {
			echo "<strong>$stat</strong> $n";
			echo "<br/>\n";
		}
		echo "</p>\n";
		echo "<h3>Memcached:</h3>";
		foreach ( $this->group_ops as $group => $ops ) {
			if ( !isset( $_GET['debug_queries'] ) && 500 < count( $ops ) ) { 
				$ops = array_slice( $ops, 0, 500 ); 
				echo "<big>Too many to show! <a href='" . add_query_arg( 'debug_queries', 'true' ) . "'>Show them anyway</a>.</big>\n";
			} 
			echo "<h4>$group commands</h4>";
			echo "<pre>\n";
			$lines = array();
			foreach ( $ops as $op ) {
				$lines[] = $this->colorize_debug_line( $op ); 
			}
			print_r( $lines );
			echo "</pre>\n";
		}

		if ( $this->debug ) {
			var_dump( $this->memcache_debug );
		}
	}
}
