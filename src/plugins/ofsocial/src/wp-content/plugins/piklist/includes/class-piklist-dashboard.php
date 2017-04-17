<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Dashboard
{
  private static $widgets = array();
  
  public static function _construct()
  {
    add_action('wp_dashboard_setup', array('piklist_dashboard', 'wp_dashboard_setup'));
    add_action('wp_network_dashboard_setup', array('piklist_dashboard', 'register_dashboard_widgets'));
  }

  public static function wp_dashboard_setup()
  {
    self::unregister_dashboard_widgets();
    self::register_dashboard_widgets();
  }

  public static function unregister_dashboard_widgets()
  {
    global $wp_meta_boxes;
    
    unset($wp_meta_boxes['dashboard']['normal']['core']['dashboard_right_now']);
  }

  public static function register_dashboard_widgets()
  {
    piklist::process_views('dashboard', array('piklist_dashboard', 'register_dashboard_widgets_callback'));
  }

  public static function register_dashboard_widgets_callback($arguments)
  {
    global $current_screen;

    extract($arguments);
    
    $file = $path . '/parts/' . $folder . '/' . $part;
    
    $data = get_file_data($file, apply_filters('piklist_get_file_data', array(
              'title' => 'Title'
              ,'capability' => 'Capability'
              ,'role' => 'Role'
              ,'id' => 'ID'
              ,'network' => 'Network'
            ), 'dashboard'));

    $data = apply_filters('piklist_add_part', $data, 'dashboard');

    if (($data['network'] == 'only') && ($current_screen->id != 'dashboard-network'))
    {
      return;
    }
    
    if ((empty($data['network']) || $data['network'] == 'false') && (isset($current_screen) && $current_screen->id == 'dashboard-network'))
    {
      return;
    }

    if ((isset($data['capability']) && current_user_can($data['capability']))
        || (isset($data['role']) && piklist_user::current_user_role($data['role']))
    )
    {
      $id = empty($data['id']) ? piklist::dashes($add_on . '-' . $part) : $data['id'];    

      self::$widgets[$id] = array(
        'id' => $id
        ,'file' => $file
        ,'data' => $data
      );

      wp_add_dashboard_widget(
        $id
        ,self::$widgets[$id]['data']['title']
        ,array('piklist_dashboard', 'render_widget')
      );
    }
  }

  public static function render_widget($null, $data)
  {
    piklist::render(self::$widgets[$data['id']]['file']);
  }
}