<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Help
{
  public static function _construct()
  {
    add_action('admin_head', array('piklist_help', 'register_help_tabs'));
  }

  public static function register_help_tabs()
  {
    piklist::process_views('help', array('piklist_help', 'register_help_tabs_callback'));
  }

  public static function register_help_tabs_callback($arguments)
  {
    $screen = get_current_screen();

    extract($arguments);
    
    $file = $path . '/parts/' . $folder . '/' . $part;
    
    $data = get_file_data($file, apply_filters('piklist_get_file_data', array(
              'title' => 'Title'
              ,'capability' => 'Capability'
              ,'role' => 'Role'
              ,'page' => 'Page'
              ,'sidebar' => 'Sidebar'
            ), 'help'));

    $data = apply_filters('piklist_add_part', $data, 'help');

    $pages = isset($data['page']) ? explode(',', $data['page']) : false;
    
    if (((($screen->id == $data['page']) || empty($data['page']) || in_array($screen->id, $pages)))
      && (
          (isset($data['capability']) && current_user_can($data['capability']))
          || (isset($data['role']) && piklist_user::current_user_role($data['role']))
      )
    )
    {
      if ($data['sidebar'] == 'true')
      {
        get_current_screen()->set_help_sidebar(piklist::render($file, null, true));
      }
      else
      {
        get_current_screen()->add_help_tab(array(
          'id' => piklist::dashes($add_on . '-' . $part)
          ,'title' => $data['title']
          ,'content' => piklist::render($file, null, true)
        ));
      }    
    }
  }
}