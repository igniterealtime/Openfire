<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Notices
{
  private static $notices = array();

  public static function _construct()
  {
    add_action('admin_init', array('piklist_notices', 'update_dismissed_piklist_notices'));
    add_action('current_screen', array('piklist_notices', 'register_notices'));
    add_action('admin_notices', array('piklist_notices', 'admin_notices'));
  }
   
  public static function register_notices()
  {
    piklist::process_views('notices', array('piklist_notices', 'register_notices_callback'));
  }

  public static function register_notices_callback($arguments)
  {
    $screen = get_current_screen();

    extract($arguments);
    
    $file = $path . '/parts/' . $folder . '/' . $part;
    
    $data = get_file_data($file, apply_filters('piklist_get_file_data', array(
              'notice_id' => 'Notice ID'
              ,'notice_type' => 'Notice Type' // error, updated, update-nag
              ,'capability' => 'Capability'
              ,'role' => 'Role'
              ,'page' => 'Page'
            ), 'notice'));

    $data = apply_filters('piklist_add_part', $data, 'notice');

    $dismissed = explode( ',', (string) get_user_meta(get_current_user_id(), 'dismissed_piklist_notices', true));

    if (!empty($dismissed[0]) && in_array($data['notice_id'], $dismissed))
    {
      return;
    }

    $page = str_replace(' ', '', $data['page']);

    $pages = $page ? explode(',', $page) : false;
   
    if (((($screen->id == $page) || (empty($page)) 
      || (in_array($screen->id, $pages))))
    )
    {
      if (($data['capability'] && !current_user_can($data['capability']))
        || ($data['role']) && !piklist_user::current_user_role($data['role'])
      )
      {
        return false;
      }
      else
      {
        $content = array(
            'content' => trim(piklist::render($file, null, true))
          );

        if(isset($data))
        {
          $data = array_merge($data, $content);

          self::$notices[] = $data;
        }
      }
    }
  }
  
  public static function admin_notices()
  {
    foreach (self::$notices as $notices => $value)
    {
      piklist('shared/admin-notice', array(
        'type' => $value['notice_type']
        ,'notices' => $value['content']
        ,'notice_id' => $value['notice_id']
      ));
    }
  }

  public static function update_dismissed_piklist_notices()
  {
    if(isset($_GET['piklist-dismiss']))
    {
      $dismissed = array_filter(explode( ',', (string) get_user_meta(get_current_user_id(), 'dismissed_piklist_notices', true)));

      $dismissed[] = $_GET['piklist-dismiss'];
      $dismissed = implode( ',', $dismissed);

      update_user_meta(get_current_user_id(), 'dismissed_piklist_notices', $dismissed);
    }
  }
}