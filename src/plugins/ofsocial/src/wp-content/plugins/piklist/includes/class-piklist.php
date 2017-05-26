<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList
{
  public static $version;
      
  public static $urls = array();

  public static $paths = array();

  public static $domains = array();
  
  public static $plurals = array(
    'plural' => array(
      '/(quiz)$/i' => "$1zes"
      ,'/^(ox)$/i' => "$1en"
      ,'/([m|l])ouse$/i' => "$1ice"
      ,'/(matr|vert|ind)ix|ex$/i' => "$1ices"
      ,'/(x|ch|ss|sh)$/i' => "$1es"
      ,'/([^aeiouy]|qu)y$/i' => "$1ies"
      ,'/(hive)$/i' => "$1s"
      ,'/(?:([^f])fe|([lr])f)$/i' => "$1$2ves"
      ,'/(shea|lea|loa|thie)f$/i' => "$1ves"
      ,'/sis$/i' => "ses"
      ,'/([ti])um$/i' => "$1a"
      ,'/(tomat|potat|ech|her|vet)o$/i' => "$1oes"
      ,'/(bu)s$/i' => "$1ses"
      ,'/(alias)$/i' => "$1es"
      ,'/(octop)us$/i' => "$1i"
      ,'/(ax|test)is$/i' => "$1es"
      ,'/(us)$/i' => "$1es"
      ,'/s$/i' => "s"
      ,'/$/' => "s"
    )
    ,'singular' => array(
      '/(quiz)zes$/i' => "$1"
      ,'/(matr)ices$/i' => "$1ix"
      ,'/(vert|ind)ices$/i'  => "$1ex"
      ,'/^(ox)en$/i' => "$1"
      ,'/(alias)es$/i' => "$1"
      ,'/(octop|vir)i$/i' => "$1us"
      ,'/(cris|ax|test)es$/i' => "$1is"
      ,'/(shoe)s$/i' => "$1"
      ,'/(o)es$/i' => "$1"
      ,'/(bus)es$/i' => "$1"
      ,'/([m|l])ice$/i' => "$1ouse"
      ,'/(x|ch|ss|sh)es$/i' => "$1"
      ,'/(m)ovies$/i' => "$1ovie"
      ,'/(s)eries$/i' => "$1eries"
      ,'/([^aeiouy]|qu)ies$/i' => "$1y"
      ,'/([lr])ves$/i' => "$1f"
      ,'/(tive)s$/i' => "$1"
      ,'/(hive)s$/i' => "$1"
      ,'/(li|wi|kni)ves$/i' => "$1fe"
      ,'/(shea|loa|lea|thie)ves$/i' => "$1f"
      ,'/(^analy)ses$/i' => "$1sis"
      ,'/((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$/i' => "$1$2sis"
      ,'/([ti])a$/i' => "$1um"
      ,'/(n)ews$/i' => "$1ews"
      ,'/(h|bl)ouses$/i' => "$1ouse"
      ,'/(corpse)s$/i' => "$1"
      ,'/(us)es$/i' => "$1"
      ,'/s$/i' => ""
    )
    ,'irregular' => array(
      'move' => 'moves'
      ,'foot' => 'feet'
      ,'goose' => 'geese'
      ,'sex' => 'sexes'
      ,'child' => 'children'
      ,'man' => 'men'
      ,'tooth' => 'teeth'
      ,'person' => 'people'
    )
    ,'ignore' => array(
      'sheep'
      ,'fish'
      ,'deer'
      ,'series'
      ,'species'
      ,'money'
      ,'rice'
      ,'information'
      ,'equipment'
      ,'media'
      ,'documentation'
    )
  );
  
  public static $prefix = '_';
  
  public static function load()
  {
    self::add_plugin('piklist', dirname(dirname(__FILE__)));

    self::$version = current(get_file_data(self::$paths['piklist'] . '/piklist.php', array('version' => 'Version')));

    load_plugin_textdomain('piklist', false, 'piklist/languages/');

    register_activation_hook('piklist/piklist.php', array('piklist', 'activate'));
   
    self::auto_load();
  }
  
  public static function auto_load()
  {
    $includes = self::get_directory_list(self::$paths['piklist'] . '/includes');
    foreach ($includes as $include)
    {
      $class_name = str_replace(array('.php', 'class_'), array('', ''), self::slug($include));
      if ($include != __FILE__)
      {
        include_once self::$paths['piklist'] . '/includes/' . $include;
     
        if (class_exists($class_name) && method_exists($class_name, '_construct') && !is_subclass_of($class_name, 'WP_Widget'))
        {
          call_user_func(array($class_name, '_construct'));
        }
      }
    }
  }

  public static function activate()
  {
    piklist::check_network_propagate('do_action', 'piklist_activate');
  }

  public static function plugin_dir()
  {
    $dir = plugin_dir_path(__FILE__);
    $dir = substr($dir, 0, -18);  

    return apply_filters('piklist_plugin_dir', $dir);
  }
  
  public static function add_plugin($type, $path)
  {
    self::$paths[$type] = stristr($path, ':\\') || stristr($path, ':/') ? str_ireplace('/', '\\', $path) : $path;   

    $path = str_replace(chr(92), '/', $path);

    self::$urls[$type] = plugins_url() . substr($path, strrpos($path, '/'));
  }
  
  public static function render($view, $arguments = array(), $return = false, $loop = null) 
  {
    global $post, $posts, $post_id, $current_user, $wpdb, $wp_query, $pagenow, $typenow, $hook_suffix, $current_screen, $wp_version, $wp_did_header, $wp_rewrite, $wp, $wp_post_statuses, $comment, $user_ID;

    $_windows_os = strtoupper(substr(PHP_OS, 0, 3)) === 'WIN';
    $_path_seperator = '/';

    $_backtrace = debug_backtrace();

    if (isset($_backtrace[1]['file']))
    {
      $_origin = $_backtrace[1]['file'];
      
      if (is_string($_origin) && ((!$_windows_os && substr($_origin, 0, 1) == $_path_seperator) || ($_windows_os && substr($_origin, 1, 1) == ':')))
      { 
        $view .= strstr($view, '.php') ? '' : '.php';
        $_path = substr($_origin, 0, strrpos($_origin, $_path_seperator));     
        foreach (array(STYLESHEETPATH, TEMPLATEPATH) as $_theme_path)
        {
          if ($_path == $_theme_path && file_exists($_theme_path . $_path_seperator . $view))
          {
            $_file = path_is_absolute($view) ? $view : $_theme_path . $_path_seperator . $view;
          }
        }
      }
    }
    
    if (!isset($_file))
    {
      foreach (self::$paths as $_display => $_path)
      {
        $_file = (path_is_absolute($view) ? $view : self::$paths[$_display] . $_path_seperator . 'parts' . $_path_seperator . $view) . (strstr($view, '.php') ? '' : '.php');

        // Check for theme overrides
        if (stristr($_file, '/parts/'))
        {
          $_part = '';
          $_directories = explode($_path_seperator, $_file);
          for ($i = count($_directories); $i >= 0; $i--)
          {
            if (isset($_directories[$i]))
            {
              $_part = $_directories[$i] . (empty($_part) ? '' : $_path_seperator) . $_part;  
              if ($_directories[$i] == 'parts')
              {
                $_part = $_path_seperator . $_directories[$i - 1] . $_path_seperator . $_part;  
                break;
              }
            }
          }
        }
        
        if (!path_is_absolute($_file))
        {
          foreach (array('theme', 'parent-theme') as $_theme)
          {
            $_path = isset(self::$paths[$_theme]) ? self::$paths[$_theme] : null;

            if (isset(self::$paths[$_theme]) && isset($_path)) 
            {          
              $_path = substr($_path, 0, strlen($_path) - 8);
              
              if (file_exists($_path . $_part))
              {
                $_file = $_path . $_part;
                break;
              }
            }
          }
        }

        if (file_exists($_file))
        {
          break;
        }
      }   
    }

    if ($return)
    {
      ob_start();
    }

    $_arguments = array($wp_query->query_vars);
    
    if (isset($arguments) && !empty($arguments))
    {
      array_push($_arguments, $arguments);
    }

    foreach ($_arguments as $_object)
    {
      foreach ($_object as $_key => $_value)
      {
        $$_key = $_value;
      }
    }
    
    $_file = apply_filters('piklist_render', $_file, $view, $arguments);

    if ($_file)
    {
      if ($loop && self::is_associative_array($arguments[$loop]))
      {
        $_depth = 1;
        
        foreach ($arguments[$loop] as $_key => $_value)
        {
          $_depth = is_array($_value) ? (count($_value) > $_depth ? count($_value) : $_depth) : 1;
        }
        
        for ($i = 0; $i < $_depth; $i++)
        {  
          $_loop = array();
          foreach ($arguments[$loop] as $_key => $_value)
          {  
            $_loop[$_key] = isset($_value[$i]) ? $_value[$i] : null;
          }
          $$loop = $_loop;
          
          include $_file;
        }
      }
      elseif ($loop)
      {
        for ($i = 0; $i < count($arguments[$loop]); $i++)
        {    
          $$loop = $arguments[$loop][$i];
              
          include $_file;
        }
      }
      elseif (file_exists($_file))
      {
        include $_file;
      }
    }
    
    if ($return)
    {
      $output = ob_get_contents();
      
      ob_end_clean();

      return $output;
    }
  }

  public static function view_exists($view, $paths = array())
  {
    if (empty($paths))
    {
      return false;
    }
    
    foreach ($paths as $type => $path)
    {
      if (file_exists($path . '/parts/' . $view . '.php'))
      {
        return $type;
      }
    }
    
    return false;
  }
  
  public static function process_views($folder, $callback, $path = false, $prefix = '', $suffix = '.php')
  { 
    $paths = $path ? $path : self::$paths;

    foreach ($paths as $display => $path)
    {  
      $files = self::get_directory_list($path . '/parts/' . $folder);

      if (empty($files) && in_array($display, array('theme', 'parent-theme')))
      {
        $files = self::get_directory_list($path . '/' . $folder);
      }
      
      foreach ($files as $part)
      {
        if (strtolower($part) != 'index.php')
        {
          $file_prefix = substr($part, 0, strlen($prefix));
          $file_suffix = substr($part, strlen($part) - strlen($suffix));
          
          if ($file_prefix == $prefix && $file_suffix == $suffix)
          {
            call_user_func_array($callback, array(array(
              'folder' => $folder
              ,'part' => $part
              ,'prefix' => $prefix
              ,'add_on' => $display
              ,'path' => $path
            )));
          }
        }
      }
    }
  }
  
  public static function pre($output, $source = false)
  {
    if ($output === '-')
    {
      $output = '--------------------------------------------------';
    }
    
    echo "<pre " . ($source ? 'style="display: none !important;"' : null) . ">\r\n";
  
    print_r($output);
  
    echo "</pre>\r\n";

    $output = ob_get_contents();
 
    if (!empty($output))
    {
      @ob_flush();
      @flush();
    }
  }
  
  public static function get_prefixed_post_types($prefix)
  {
    $post_types = get_post_types('', 'names');
    
    foreach ($post_types as $key => $post_type) 
    {
      if (substr($post_type, 0, strlen($prefix)) != $prefix)
      {
        unset($post_types[$key]);
      }
    }
    
    return $post_types;
  }
  
  public static function get_directory_list($start = '.', $path = false, $extension = false) 
  {
    $files = array();

    if (is_dir($start)) 
    {
      $file_handle = opendir($start);

      while (($file = readdir($file_handle)) !== false) 
      {
        if ($file != '.' && $file != '..' && strlen($file) > 2) 
        {
          if (strcmp($file, '.') == 0 || strcmp($file, '..') == 0) 
          {
            continue;
          }

          if ($file[0] != '.' && $file[0] != '_')
          {
            $file_parts = explode('.', $file);
            $_file = $extension ? $file : $file_parts[0];
            $file_path = $path ? $start . '/' . $_file : $_file;

            if (is_dir($file_path)) 
            {
              $files = array_merge($files, self::get_directory_list($file_path));
            } 
            else 
            {
              array_push($files, $file);
            }
          }
        }
      }

      closedir($file_handle);
    } 
    else 
    {
      $files = array();
    }

    return $files;
  }
   
  public static function dashes($string)
  {  
    return str_replace(array('_', ' '), '-', preg_replace('/[^a-z0-9]+/i', '-', str_replace('.php', '', strtolower($string))));
  }
  
  public static function slug($string)
  {
    return str_replace('.php', '', str_replace(array('-', ' '), '_', strtolower($string)));
  }

  public static function check_network_propagate($callback, $arguments)
  {
    global $wpdb;

    if (function_exists('is_multisite') && is_multisite()) 
    {
      if (is_network_admin())
      {
        $core = $wpdb->blogid;
        $ids = $wpdb->get_col("SELECT blog_id FROM $wpdb->blogs");
        foreach ($ids as $id) 
        {
          switch_to_blog($id);
          
          call_user_func($callback, $arguments);
        }
        switch_to_blog($core);
      }
      else
      {
        call_user_func($callback, $arguments);
      }  
    } 
    else
    {
      call_user_func($callback, $arguments);
    }
  }
  
  public static function create_table($table_name, $columns) 
  {
    global $wpdb;
    
    $settings = $wpdb->has_cap('collation') ? (!empty($wpdb->charset) ? 'DEFAULT CHARACTER SET ' . $wpdb->charset : null) . (!empty($wpdb->collate) ? ' COLLATE ' . $wpdb->collate : null) : null;

    $wpdb->query('CREATE TABLE IF NOT EXISTS ' . $wpdb->prefix . $table_name . ' (' . $columns . ') ' . $settings . ';');
  }

  public static function delete_table($table_name) 
  {
    global $wpdb;
    
    $wpdb->query('DROP TABLE IF EXISTS ' . $wpdb->prefix . $table_name);
  }

  public static function post_type_labels($label)
  {
    return array(
      'name' => __(self::singularize($label), 'piklist')
      ,'singular_name' => __(self::singularize($label), 'piklist')
      ,'all_items' => __('All ' . self::pluralize($label), 'piklist')
      ,'add_new' => __('Add New', 'piklist')
      ,'add_new_item' => __('Add New ' . self::singularize($label), 'piklist')
      ,'edit_item' => __('Edit ' . self::singularize($label), 'piklist')
      ,'new_item' => __('Add New ' . self::singularize($label), 'piklist')
      ,'view_item' => __('View ' . self::singularize($label), 'piklist')
      ,'search_items' => __('Search ' . self::pluralize($label), 'piklist')
      ,'not_found' => __('No ' . self::pluralize($label) . ' found', 'piklist')
      ,'not_found_in_trash' => __('No ' . self::pluralize($label) . ' found in trash', 'piklist')
      ,'parent_item_colon' => __('Parent ' . self::pluralize($label) . ':', 'piklist')
      ,'menu_name' => __(self::pluralize($label), 'piklist')
    );
  }
  
  public static function taxonomy_labels($label)
  {
    return array(
      'name' => __(self::singularize($label), 'piklist')
      ,'singular_name' => __(self::singularize($label), 'piklist')
      ,'search_items' =>  __('Search ' . self::pluralize($label), 'piklist')
      ,'all_items' => __('All ' . self::pluralize($label), 'piklist')
      ,'parent_item' => __('Parent '  . self::pluralize($label), 'piklist')
      ,'parent_item_colon' => __('Parent ' . self::pluralize($label) . ':', 'piklist')
      ,'edit_item' => __('Edit ' . self::singularize($label), 'piklist')
      ,'update_item' => __('Update ' . self::singularize($label), 'piklist')
      ,'add_new_item' => __('Add New ' . self::singularize($label), 'piklist')
      ,'view_item' => __('View ' . self::singularize($label), 'piklist')
      ,'popular_items' => __('Popular ' . self::pluralize($label), 'piklist')
      ,'new_item_name' => __('New ' . self::singularize($label) . ' Name', 'piklist')
      ,'separate_items_with_commas' => __('Separate ' . self::pluralize($label) . ' with commas', 'piklist')
      ,'add_or_remove_items' => __('Add or remove ' . self::pluralize($label), 'piklist')
      ,'choose_from_most_used' => __('Choose from the most used ' . self::pluralize($label), 'piklist')
      ,'not_found' => __('No ' . self::pluralize($label) . ' found.', 'piklist')
      ,'menu_name' => __(self::pluralize($label), 'piklist')
      ,'name_admin_bar' => $label
    );
  }
  
  public static function pluralize($string)
  {
    if ((in_array(strtolower($string), self::$plurals['ignore'])) || (strrpos($string, ' ') && in_array(strtolower(substr($string, strrpos($string, ' ') + 1, strlen($string) - strrpos($string, ' ') + 1)), self::$plurals['ignore'])))
    {
      return $string;
    }
    
    foreach (self::$plurals['irregular'] as $pattern => $result)
    {
      $pattern = '/' . $pattern . '$/i';
      if (preg_match($pattern, $string))
      {
        return preg_replace($pattern, $result, $string);
      }
    }

    foreach (self::$plurals['plural'] as $pattern => $result)
    {
      if (preg_match($pattern, $string))
      {
        return preg_replace($pattern, $result, $string);
      }
    }

    return $string;
  }
  
  public static function singularize($string)
  {
    if (in_array(strtolower($string), self::$plurals['ignore']))
    {
      return $string;
    }
    
    foreach (self::$plurals['irregular'] as $pattern => $result)
    {
      $pattern = '/' . $pattern . '$/i';
      if (preg_match($pattern, $string))
      {
        return preg_replace($pattern, $result, $string);
      }
    }
    
    foreach (self::$plurals['singular'] as $pattern => $result)
    {
      if (preg_match($pattern, $string))
      {
        return preg_replace($pattern, $result, $string);
      }
    }

    return $string;
  }
  
  public static function add_admin_menu_separator($position) 
  {
    global $menu;
  
    if (isset($menu) && !empty($menu))
    {
      $index = 0;
    
      foreach ($menu as $offset => $section) 
      {
        if (substr($section[2], 0, 9) == 'separator')
        {
          $index++;
        }
      
        if ($offset >= $position) 
        {
          $menu[$position] = array(
            ''
            ,'read'
            ,'separator' . $index
            ,''
            ,'wp-menu-separator'
          );
        
          ksort($menu);
        
          break;
        }
      }
    }
  }
  
  public static function array_path($array, $find, $map = null)
  {
    $path = array();
    
    if (array_key_exists($find, $array))
    {
      return $map ? array("{$map[count($path)]}" => $find) : array($find);
    }
    else
    {
      foreach ($array as $key => $data)
      {
        if (is_array($data))
        {
          if ($path = self::array_path($data, $find, $map))
          {
            $path[($map ? $map[count($path)] : null)] = $key;
            
            return $path;
          }
        }
      }
    }

    return null;
  }
  
  public static function array_path_get($array, $path)
  {
    if (!$path)
    {  
      return false;
    }
    
    $map = is_array($path) ? $path : explode('/', $path);
    $found =& $array;
    
    foreach ($map as $part) 
    {
      if (!isset($found[$part]))
      {
        return null;
      }
      
      $found = $found[$part];
    }

    return $found;
  }

  public static function array_path_set(&$array, $path, $value)
  {
    if (is_array($path) && empty($path))
    {
      $array = $value;
      
      return null;
    }
    elseif (!$path)
    {
      return null;
    }
    
    $map = is_array($path) ? $path : explode('/', $path);
    $found =& $array;
    
    foreach ($map as $part)
    {
      if (!isset($found[$part]))
      {
        $found[$part] = array();
      }
      
      $found =& $found[$part];
    }

    $found = $value;
  }
  
  public static function array_values_cast(&$value, $key)
  {
    if (is_numeric($value))
    {
      $value = $value + 0;
    }
    elseif (in_array(strtolower($value), array('true', 'false')))
    {
      $value = strtolower($value) == 'true' ? true : false;
    }
  }
  
  public static function xml_to_array($xml) 
  {
    libxml_use_internal_errors(true);

    $xml_document = new DOMDocument();
    $xml_document->loadXML($xml);

    return self::dom_node_to_array($xml_document->documentElement);
  }

  public static function dom_node_to_array($node) 
  {
    $output = array();
    switch ($node->nodeType) 
    {
      case XML_CDATA_SECTION_NODE:
      case XML_TEXT_NODE:
        $output = trim($node->textContent);
      break;

      case XML_ELEMENT_NODE:
      for ($x = 0, $y = $node->childNodes->length; $x < $y; $x++) 
      {
        $child = $node->childNodes->item($x);

        $value = self::dom_node_to_array($child);

        if (isset($child->tagName)) 
        {
          $tag = $child->tagName;
          if (!isset($output[$tag])) 
          {
            $output[$tag] = array();
          }
          $output[$tag][] = $value;
        }
        elseif ($value) 
        {
          $output = (string) $value;
        }
      }

      if (is_array($output)) 
      {
        if ($node->attributes->length) 
        {
          $attributes = array();
          foreach($node->attributes as $key => $attribute_node) 
          {
            $attributes[$key] = (string) $attribute_node->value;
          }
          $output['@attributes'] = $attributes;
        }

        foreach ($output as $key => $value) 
        {
          if (is_array($value) && count($value) == 1 && $key != '@attributes') 
          {
            $output[$key] = $value[0];
          }
        }
      }

      break;
    }

    return $output;
  }
  
  public static function directory_empty($path)
  {
    if (is_dir($path))
    {
      $files = @scandir($path);
      return count($files) > 2 ? false : true;
    }
    
    return true;
  }
  
  public static function unique_id($object = null)
  {
    return substr(md5(is_object($object) || is_array($object) ? serialize($object) : rand()), 0, 7);
  }

  public static function object_id($object)
  {
    if (!is_object($object)) 
    {
      return null;
    }
    
    if (!isset($object->__unique)) 
    {
      $object->__unique = microtime(true);
      usleep(1);
    }
    
    return spl_object_hash($object) . $object->__unique;
  }
  
  public static function object_to_array($object) 
  {
    if (!is_array($object) && !is_object($object))
    {
      return $object;
    }

    if (is_object($object))
    {
      $object = get_object_vars($object);
    }
    
    return array_map(array('piklist', 'object_to_array'), $object);
  }
  
  public static function is_associative_array($array)
  {
    return array_keys($array) !== range(0, count($array) - 1);
  }
  
  public static function get_settings($option, $setting)
  {
    $options = get_option($option);

    return isset($options[$setting]) ? $options[$setting] : array();
  }
  
  public static function check_in($needle, $haystack)
  {
    return (is_array($needle) && in_array($haystack, $needle)) || (is_string($needle) && $needle == $haystack);
  }
  
  public static function sort_by_order($a, $b) 
  {
    return $a['order'] - $b['order'];
  }

  public static function sort_by_name_order($a, $b) 
  {
    return $a['name'] - $b['name'];
  }
  
  public static function sort_by_tab_order($a, $b) 
  {
    return $a['tab_order'] - $b['tab_order'];
  }
  
  public static function sort_by_args_order($a, $b) 
  {
    if (!isset($a['args']['order']) && !isset($b['args']['order']))
    {
      return 1;
    }

    return $a['args']['order'] - $b['args']['order'];
  }
  
  public static function sort_by_config_order($a, $b) 
  {
    return $a['config']['order'] - $b['config']['order'];
  }
  
  public static function array_next($array, $needle)
  {
    $keys = array_keys($array);
    $position = array_search($needle, $keys);

    if (isset($keys[$position + 1])) 
    {
      return $keys[$position + 1];
    }
    
    return $needle;
  }
  
  public static function array_filter($value)
  {
    return $value !== false && $value !== null && $value !== '';
  }
    
  public static function object($type, $id)
  {
    $data = $type == 'option' ? get_option($id) : get_metadata($type, $id);

    if (!empty($data))
    {
      foreach ($data as $key => $value)
      {
        $data[$key] = self::object_value(maybe_unserialize($value));
      }
    }

    return $data;
  }
  
  public static function object_value($object)
  {
    if (is_array($object) && count($object) == 1 && self::is_flat($object))
    {
      return maybe_unserialize(current($object));
    }
    elseif (is_array($object))
    {
      foreach ($object as $key => $value)
      {
        $value = maybe_unserialize($value);
        
        if (is_array($value) && is_numeric($key) && count($value) == 1 && self::is_flat($object))
        {
          $object = current($value);
        }
        elseif (is_array($value))
        {
          $object[$key] = self::object_value($value);
        }
      }
    }
    
    return maybe_unserialize($object);
  }
  
  public static function is_flat($object)
  {
    return count($object) == count($object, COUNT_RECURSIVE);
  }
  
  public static function explode($delimiter, $string)
  {
    return array_map('trim', explode($delimiter, $string));
  }
  
  public static function include_meta_boxes($keep, $post_type = null)
  {
    global $wp_meta_boxes, $typenow;
    
    $post_type = $post_type ? $post_type : $typenow;
    
    array_push($keep, 'submitdiv');
    
    $post_type = $post_type ? $post_type : $typenow;
    
    foreach ($wp_meta_boxes[$post_type] as $meta_boxes)
    {
      foreach (array('normal', 'advanced', 'side') as $context)
      {
        foreach (array('high', 'core', 'default', 'low') as $priority)
        {
          if (isset($meta_boxes[$priority]))
          {
            foreach ($meta_boxes[$priority] as $id => $config)
            {
              if (!in_array($id, $keep))
              {
                remove_meta_box($id, $post_type, $context);
              }
            }
          }
        }
      }
    }
  }
  
  public static function include_actions($tags)
  {
    global $wp_filter;
    
    // TODO: Redo with below...
    foreach ($tags as $tag => $keep)
    {
      foreach ($wp_filter[$tag] as $priority => $callback)
      {
        foreach ($callback as $id => $config)
        {
          if (!in_array($id, $keep))
          {
            unset($wp_filter[$tag][$priority][$id]);
          }
        }
      }
    }    
  }
  
  public function get_ip_address() 
  {
    if (!empty($_SERVER['HTTP_CLIENT_IP'])) 
    {
      $ip_address = $_SERVER['HTTP_CLIENT_IP'];
    } 
    elseif (!empty($_SERVER['HTTP_X_FORWARDED_FOR'])) 
    {
      $ip_address = $_SERVER['HTTP_X_FORWARDED_FOR'];
    }
    else
    {
      $ip_address = $_SERVER['REMOTE_ADDR'];
    }

    return $ip_address;
  }
  
  public static function performance()
  {
    if (!ini_get('safe_mode'))
    { 
      ini_set('max_execution_time', -1);
      ini_set('memory_limit', -1);
    }
  }
}

/*
 * Helper Function
 */
function piklist($option, $arguments = array())
{
  if (!is_array($arguments) && strstr($arguments, '='))
  {
    parse_str($arguments, $arguments);
  }

  if (is_array($option) || is_object($option))
  {
    $list = array();
    $arguments = is_array($arguments) ? $arguments : array($arguments);
    foreach ($option as $key => $value) 
    {
      if (count($arguments) > 1)
      {
        if (in_array('_key', $arguments))
        {
          $_value = $arguments[1];
          $list[$key] = is_object($value) ? $value->$_value : $value[$_value];
        }
        else
        {
          $__key = $arguments[0];
          $_key = is_object($value) ? $value->$__key : (isset($value[$__key]) ? $value[$__key] : null);

          $_value = $arguments[1];
          $list[$_key] = is_object($value) ? $value->$_value : (isset($value[$_value]) ? $value[$_value] : null);
        }
      }
      else
      {
        $_value = $arguments[0];
        array_push($list, is_object($value) && isset($value->$_value) ? $value->$_value : (isset($value[$_value]) ? $value[$_value] : null));
      }
    }

    return $list;
  }
  else
  {
    switch ($option)
    {
      case 'field':
        
        if (piklist_setting::get('active_section'))
        {
          piklist_setting::register_setting($arguments);
        }
        else
        {
          piklist_form::render_field($arguments, isset($arguments['return']) ? $arguments['return'] : false);
        }
        
      break;
      
      case 'list_table':
        
        piklist_list_table::render($arguments);
      
      break;
      
      case 'post_type_labels':

        return piklist::post_type_labels($arguments);
        
      break;
      
      case 'taxonomy_labels':
      
        return piklist::taxonomy_labels($arguments);
        
      break;
      
      case 'option':
      case 'post_custom':
      case 'post_meta':
      case 'get_post_custom':
      case 'user_custom':
      case 'user_meta':
      case 'get_user_custom':
      case 'term_custom':
      case 'term_meta':
      case 'get_term_custom':
        
        switch ($option)
        {
          case 'user_custom':
          case 'user_meta':
          case 'get_user_custom':
          
            $type = 'user';
            
          break;
          
          case 'term_custom':
          case 'term_meta':
          case 'get_term_custom':
          
            $type = 'term';
            
          break;
          
          case 'post_custom':
          case 'post_meta':
          case 'get_post_custom':
          
            $type = 'post';
            
          break;

          default: 
          
            $type = 'option';
            
          break;
        }
        
        return piklist::object($type, $arguments);
        
      break;
      
      case 'dashes':
      
        return piklist::dashes($arguments);
        
      break;
      
      case 'slug':
      
        return piklist::slug($arguments);
        
      break;
      
      case 'performance':
        
        piklist::performance();
        
      break;
      
      case 'include_meta_boxes':
      
        // TODO: Improve
        if (isset($arguments['post_type']))
        {
          $post_type = $arguments['post_type'];
          unset($arguments['post_type']);
        }
        else
        {
          $post_type = null;
        }

        piklist::include_meta_boxes($arguments, $post_type);

      break;
      
      case 'include_actions':
        
        // TODO: Improve
        if (isset($arguments['action']))
        {
          $post_type = $arguments['action'];
          unset($arguments['action']);
        }
        
        piklist::include_actions($action, $arguments);
      
      break;
      
      case 'include_user_profile_fields':
      
        piklist_user::include_user_profile_fields($arguments);
        
      break;
      
      case 'comments_template':

        $file = isset($arguments[0]) ? $arguments[0] : '/comments.php';
        $seperate_comments = isset($arguments[1]) ? $arguments[1] : false;

        piklist_comments::comments_template($file, $seperate_comments);

      break;
      
      default:
        
        $return = isset($arguments['return']) ? $arguments['return'] : false;
        $loop = isset($arguments['loop']) ? $arguments['loop'] : null;
        
        unset($arguments['return']);
        unset($arguments['loop']);

        return piklist::render($option, $arguments, $return, $loop); 
        
      break;
    }
  }
}