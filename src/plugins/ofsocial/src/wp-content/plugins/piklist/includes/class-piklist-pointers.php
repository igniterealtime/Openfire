<?php

if (!defined('ABSPATH'))
{
  exit;
}


class PikList_Pointers
{
  private static $pointers = array();

  public static function _construct()
  {
    add_action('current_screen', array('piklist_pointers', 'register_pointers'));
    add_action('admin_enqueue_scripts', array('piklist_pointers', 'admin_enqueue_scripts'));
    add_action('admin_print_footer_scripts', array('piklist_pointers', 'print_pointers'));
  }

  public static function admin_enqueue_scripts()
  {
   if (!empty(self::$pointers))
   {
      wp_enqueue_script('wp-pointer');
      wp_enqueue_style('wp-pointer');
   }
  }

  public static function register_pointers()
  {
    piklist::process_views('pointers', array('piklist_pointers', 'register_pointers_callback'));
  }

  public static function register_pointers_callback($arguments)
  {
    $screen = get_current_screen();

    extract($arguments);
    
    $file = $path . '/parts/' . $folder . '/' . $part;
    
    $data = get_file_data($file, apply_filters('piklist_get_file_data', array(
              'title' => 'Title'
              ,'pointer_id' => 'Pointer ID'
              ,'capability' => 'Capability'
              ,'role' => 'Role'
              ,'page' => 'Page'
              ,'anchor' => 'Anchor ID'
              ,'edge' => 'Edge'
              ,'align' => 'Align'
            ), 'pointer'));

    $data = apply_filters('piklist_add_part', $data, 'pointer');

    $dismissed = explode( ',', (string) get_user_meta(get_current_user_id(), 'dismissed_wp_pointers', true ));

    if (!empty($dismissed[0]) && in_array($data['pointer_id'], $dismissed))
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
        
        $data = array_merge($data, $content);

        self::$pointers[] = $data;
      }
    }
  }

  public static function print_pointers()
  {
    foreach (self::$pointers as $pointer => $value)
    { 
      piklist('shared/pointer', array(
        'anchor' => $value['anchor']
        ,'page' => $value['page']
        ,'content' => '<h3>' . $value['title'] . '</h3>' . $value['content']
        ,'edge' => $value['edge']
        ,'align' => $value['align']
        ,'pointer_id' => $value['pointer_id']
      ));
    }
  }
}