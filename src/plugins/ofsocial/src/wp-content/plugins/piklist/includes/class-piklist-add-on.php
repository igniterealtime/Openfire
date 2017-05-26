<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Add_On
{
  public static $available_add_ons = array();
  
  public static function _construct()
  {    
    add_action('init', array('piklist_add_on', 'init'), 0);
  }

  public static function init()
  {    
    self::include_add_ons();
  }
  
  public static function include_add_ons()
  { 
    require_once ABSPATH . 'wp-admin/includes/plugin.php';
    
    $site_wide_plugins = get_site_option('active_sitewide_plugins');
    if (!empty($site_wide_plugins))
    {
      $plugins = array_merge(get_option('active_plugins'), array_keys($site_wide_plugins));
    }
    else
    {
      $plugins = get_option('active_plugins');      
    }

    foreach ($plugins as $plugin)
    {
      $path = WP_PLUGIN_DIR . '/' . $plugin;
      
      if (file_exists($path))
      {
        $data = get_file_data($path, array(
                  'type' => 'Plugin Type'
                  ,'version' => 'Version'
                ));
                
        if ($data['type'] && strtolower($data['type']) == 'piklist')
        {
          piklist::add_plugin(basename(dirname($plugin)), dirname($path));

          add_action('load-plugins.php', array('piklist_admin', 'deactivation_link'));

          piklist_admin::$piklist_dependent = true;

          if ($data['version'])
          {
            $file = $plugin;
            $version = $data['version'];

            piklist_admin::check_update($file, $version);
          }
        }
      }
    }
    
    $paths = array();
    foreach (piklist::$paths as $from => $path)
    {
      if ($from != 'theme')
      {
        array_push($paths, $path  . '/add-ons');
        if ($from != 'piklist')
        {
          array_push($paths, $path);
        }
      }
    }

    foreach ($paths as $path)
    {
      if (is_dir($path))
      {
        if (strstr($path, 'add-ons'))
        {
          $add_ons = piklist::get_directory_list($path);
          foreach ($add_ons as $add_on)
          {
            $file = file_exists($path . '/' . $add_on . '/' . $add_on . '.php') ? $path . '/' . $add_on . '/' . $add_on . '.php' : $path . '/' . $add_on . '/plugin.php';
            self::register_add_on($add_on, $file, $path);
          }
        }
        else
        {
          $add_on = basename($path);
          $file = file_exists($path . '/' . $add_on . '.php') ? $path . '/' . $add_on . '.php' : $path . '/plugin.php';
          self::register_add_on($add_on, $file, $path, true);
        }
      }
    }

    do_action('piklist_activate_add_on');
  }

  private static function register_add_on($add_on, $file, $path, $plugin = false)
  {
    if (file_exists($file))
    {
      $active_add_ons = piklist::get_settings('piklist_core_addons', 'add-ons');
      
      $data = get_plugin_data($file);
      $data['plugin'] = $plugin;
      
      self::$available_add_ons[$add_on] = $data;

      if (in_array($add_on, is_array($active_add_ons) ? $active_add_ons : array($active_add_ons)))
      {
        include_once $file;

        $class_name = str_replace(piklist::$prefix, 'piklist_', piklist::slug($add_on));

        if (class_exists($class_name) && method_exists($class_name, '_construct') && !is_subclass_of($class_name, 'WP_Widget'))
        {
          call_user_func(array($class_name, '_construct'));
        }
  
        piklist::$paths[$add_on] = $path . (!$plugin ? '/' . $add_on : '');
      }
    }
  }

  public static function is_active($add_on = '')
  {
    $piklist_core_addons = get_option('piklist_core_addons');

    if ((!empty($piklist_core_addons)) && in_array($add_on, is_array($piklist_core_addons['add-ons']) ? $piklist_core_addons['add-ons'] : array($piklist_core_addons['add-ons'])))
    {
      return true;
    }
    else
    {
      return false;
    }
  }

}