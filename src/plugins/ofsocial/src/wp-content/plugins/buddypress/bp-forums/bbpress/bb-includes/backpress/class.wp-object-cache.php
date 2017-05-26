<?php
// Last sync [WP11537]

/**
 * WordPress Object Cache
 *
 * The WordPress Object Cache is used to save on trips to the database. The
 * Object Cache stores all of the cache data to memory and makes the cache
 * contents available by using a key, which is used to name and later retrieve
 * the cache contents.
 *
 * The Object Cache can be replaced by other caching mechanisms by placing files
 * in the wp-content folder which is looked at in wp-settings. If that file
 * exists, then this file will not be included.
 *
 * @package WordPress
 * @subpackage Cache
 * @since 2.0
 */
class WP_Object_Cache {

	/**
	 * Holds the cached objects
	 *
	 * @var array
	 * @access private
	 * @since 2.0.0
	 */
	var $cache = array ();

	/**
	 * Cache objects that do not exist in the cache
	 *
	 * @var array
	 * @access private
	 * @since 2.0.0
	 */
	var $non_existant_objects = array ();

	/**
	 * The amount of times the cache data was already stored in the cache.
	 *
	 * @since 2.5.0
	 * @access private
	 * @var int
	 */
	var $cache_hits = 0;

	/**
	 * Amount of times the cache did not have the request in cache
	 *
	 * @var int
	 * @access public
	 * @since 2.0.0
	 */
	var $cache_misses = 0;

	/**
	 * Adds data to the cache if it doesn't already exist.
	 *
	 * @uses WP_Object_Cache::get Checks to see if the cache already has data.
	 * @uses WP_Object_Cache::set Sets the data after the checking the cache
	 *		contents existance.
	 *
	 * @since 2.0.0
	 *
	 * @param int|string $id What to call the contents in the cache
	 * @param mixed $data The contents to store in the cache
	 * @param string $group Where to group the cache contents
	 * @param int $expire When to expire the cache contents
	 * @return bool False if cache ID and group already exists, true on success
	 */
	function add($id, $data, $group = 'default', $expire = '') {
		if (empty ($group))
			$group = 'default';

		if (false !== $this->get($id, $group, false))
			return false;

		return $this->set($id, $data, $group, $expire);
	}

	/**
	 * Remove the contents of the cache ID in the group
	 *
	 * If the cache ID does not exist in the group and $force parameter is set
	 * to false, then nothing will happen. The $force parameter is set to false
	 * by default.
	 *
	 * On success the group and the id will be added to the
	 * $non_existant_objects property in the class.
	 *
	 * @since 2.0.0
	 *
	 * @param int|string $id What the contents in the cache are called
	 * @param string $group Where the cache contents are grouped
	 * @param bool $force Optional. Whether to force the unsetting of the cache
	 *		ID in the group
	 * @return bool False if the contents weren't deleted and true on success
	 */
	function delete($id, $group = 'default', $force = false) {
		if (empty ($group))
			$group = 'default';

		if (!$force && false === $this->get($id, $group, false))
			return false;

		unset ($this->cache[$group][$id]);
		$this->non_existant_objects[$group][$id] = true;
		return true;
	}

	/**
	 * Clears the object cache of all data
	 *
	 * @since 2.0.0
	 *
	 * @return bool Always returns true
	 */
	function flush( $group = null ) {
		// WP does not support group flushing
		if ( is_null($group) ) {
			$this->cache = array();
			$this->non_existant_objects = array();
			return true;
		}

		if ( isset($this->cache[$group]) )
			$this->cache[$group] = array();
		if ( isset($this->non_existant_objects[$group]) )
			$this->non_existant_objects[$group] = array();
	}

	/**
	 * Retrieves the cache contents, if it exists
	 *
	 * The contents will be first attempted to be retrieved by searching by the
	 * ID in the cache group. If the cache is hit (success) then the contents
	 * are returned.
	 *
	 * On failure, the $non_existant_objects property is checked and if the
	 * cache group and ID exist in there the cache misses will not be
	 * incremented. If not in the nonexistant objects property, then the cache
	 * misses will be incremented and the cache group and ID will be added to
	 * the nonexistant objects.
	 *
	 * @since 2.0.0
	 *
	 * @param int|string $id What the contents in the cache are called
	 * @param string $group Where the cache contents are grouped
	 * @return bool|mixed False on failure to retrieve contents or the cache
	 *		contents on success
	 */
	function get($id, $group = 'default') {
		if (empty ($group))
			$group = 'default';

		if (isset ($this->cache[$group][$id])) {
			$this->cache_hits += 1;
			if ( is_object($this->cache[$group][$id]) )
				return wp_clone($this->cache[$group][$id]);
			else
				return $this->cache[$group][$id];
		}

		if ( isset ($this->non_existant_objects[$group][$id]) )
			return false;

		$this->non_existant_objects[$group][$id] = true;
		$this->cache_misses += 1;
		return false;
	}

	/**
	 * Replace the contents in the cache, if contents already exist
	 *
	 * @since 2.0.0
	 * @see WP_Object_Cache::set()
	 *
	 * @param int|string $id What to call the contents in the cache
	 * @param mixed $data The contents to store in the cache
	 * @param string $group Where to group the cache contents
	 * @param int $expire When to expire the cache contents
	 * @return bool False if not exists, true if contents were replaced
	 */
	function replace($id, $data, $group = 'default', $expire = '') {
		if (empty ($group))
			$group = 'default';

		if (false === $this->get($id, $group, false))
			return false;

		return $this->set($id, $data, $group, $expire);
	}

	/**
	 * Sets the data contents into the cache
	 *
	 * The cache contents is grouped by the $group parameter followed by the
	 * $id. This allows for duplicate ids in unique groups. Therefore, naming of
	 * the group should be used with care and should follow normal function
	 * naming guidelines outside of core WordPress usage.
	 *
	 * The $expire parameter is not used, because the cache will automatically
	 * expire for each time a page is accessed and PHP finishes. The method is
	 * more for cache plugins which use files.
	 *
	 * @since 2.0.0
	 *
	 * @param int|string $id What to call the contents in the cache
	 * @param mixed $data The contents to store in the cache
	 * @param string $group Where to group the cache contents
	 * @param int $expire Not Used
	 * @return bool Always returns true
	 */
	function set($id, $data, $group = 'default', $expire = '') {
		if (empty ($group))
			$group = 'default';

		if (NULL === $data)
			$data = '';

		if ( is_object($data) )
			$data = wp_clone($data);

		$this->cache[$group][$id] = $data;

		if(isset($this->non_existant_objects[$group][$id]))
			unset ($this->non_existant_objects[$group][$id]);

		return true;
	}

	/**
	 * Echos the stats of the caching.
	 *
	 * Gives the cache hits, and cache misses. Also prints every cached group,
	 * key and the data.
	 *
	 * @since 2.0.0
	 */
	function stats() {
		echo "<p>";
		echo "<strong>Cache Hits:</strong> {$this->cache_hits}<br />";
		echo "<strong>Cache Misses:</strong> {$this->cache_misses}<br />";
		echo "</p>";

		foreach ($this->cache as $group => $cache) {
			echo "<p>";
			echo "<strong>Group:</strong> $group<br />";
			echo "<strong>Cache:</strong>";
			echo "<pre>";
			print_r($cache);
			echo "</pre>";
		}
	}

	function add_global_groups( $groups )
	{
		return true;
	}

	function add_non_persistent_groups( $groups )
	{
		return true;
	}

	/**
	 * PHP4 constructor; Calls PHP 5 style constructor
	 *
	 * @since 2.0.0
	 *
	 * @return WP_Object_Cache
	 */
	function WP_Object_Cache() {
		return $this->__construct();
	}

	/**
	 * Sets up object properties; PHP 5 style constructor
	 *
	 * @since 2.0.8
	 * @return null|WP_Object_Cache If cache is disabled, returns null.
	 */
	function __construct() {
		/**
		 * @todo This should be moved to the PHP4 style constructor, PHP5
		 * already calls __destruct()
		 */
		register_shutdown_function(array(&$this, "__destruct"));
	}

	/**
	 * Will save the object cache before object is completely destroyed.
	 *
	 * Called upon object destruction, which should be when PHP ends.
	 *
	 * @since  2.0.8
	 *
	 * @return bool True value. Won't be used by PHP
	 */
	function __destruct() {
		return true;
	}
}
