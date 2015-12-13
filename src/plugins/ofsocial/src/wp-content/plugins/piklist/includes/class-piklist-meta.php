<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Meta
{
  private static $reset_meta = array(
    'post.php' => array(
      'id' => 'post'
      ,'group' => 'post_meta'
    )
    ,'user-edit.php' => array(
      'id' => 'user_id'
      ,'group' => 'user_meta'
    )
    ,'comment.php' => array(
      'id' => 'c'
      ,'group' => 'comment_meta'
    )
  );
  
  public static $reserved_meta_keys = array(
    'post' => array(
      '_edit_lock'
      ,'_edit_last'
      ,'_wp_trash_meta_status'
      ,'_wp_page_template'
      ,'_menu_item_target'
      ,'_menu_item_object'
      ,'_menu_item_object_id'
      ,'_menu_item_type'
      ,'_menu_item_xfn'
      ,'_menu_item_menu_item_parent'
      ,'_menu_item_classes'
      ,'_menu_item_url'
      ,'_menu_item_orphaned'
      ,'_invalid'
    )
    ,'user' => array(
      'wp_capabilities'
      ,'wp_user_level'
      ,'wp_use_ssl'
      ,'use_ssl'
      ,'wp_default_password_nag'
      ,'default_password_nag'
      ,'wp_rich_editing'
      ,'rich_editing'
      ,'wp_comment_shortcuts'
      ,'comment_shortcuts'
      ,'wp_admin_color'
      ,'admin_color'
      ,'dismissed_wp_pointers'
      ,'wp_user-settings'
      ,'dismissed_piklist_notices'
    )
  );
  
  public static function _construct()
  {    
    add_action('init', array('piklist_meta', 'meta_reset'));
    add_action('query', array('piklist_meta', 'meta_sort'));
    
    add_filter('get_post_metadata', array('piklist_meta', 'get_post_meta'), 100, 4);
    add_filter('get_user_metadata', array('piklist_meta', 'get_user_meta'), 100, 4);
    add_filter('get_term_metadata', array('piklist_meta', 'get_term_meta'), 100, 4);
  }
  
  public static function meta_reset()
  {
    global $pagenow;

    self::$reset_meta = apply_filters('piklist_reset_meta_admin_pages', self::$reset_meta);
    
    if (in_array($pagenow, self::$reset_meta))
    {
      foreach (self::$reset_meta as $page => $data)
      {
        if (isset($_REQUEST[$data['id']]))
        {
          wp_cache_replace($_REQUEST[$data['id']], false, $data['group']);
          
          break;
        }
      }
    }
  }
  
  public static function meta_sort($query) 
  {
    global $wpdb;
    
    if (stristr($query, ', meta_key, meta_value FROM'))
    {
      $meta_tables = apply_filters('piklist_meta_tables', array(
        'post_id' => $wpdb->postmeta
        ,'comment_id' => $wpdb->commentmeta
      ));

      foreach ($meta_tables as $id => $meta_table)
      {
        if (stristr($query, "SELECT {$id}, meta_key, meta_value FROM {$meta_table} WHERE {$id} IN") && !stristr($query, ' ORDER BY '))
        {
          return $query . ' ORDER BY meta_id ASC';
        }
      }
    }
    
    return $query;
  }
  
  public static function get_post_meta($value, $object_id, $meta_key, $single)
  {
    return self::get_metadata($value, 'post', $object_id, $meta_key, $single);
  }
  
  public static function get_user_meta($value, $object_id, $meta_key, $single)
  {
    return self::get_metadata($value, 'user', $object_id, $meta_key, $single);
  }
  
  public static function get_term_meta($value, $object_id, $meta_key, $single)
  {
    return self::get_metadata($value, 'term', $object_id, $meta_key, $single);
  }
  
  public static function get_metadata($value, $meta_type, $object_id, $meta_key, $single)
  {
    global $wpdb;
    
    self::$reserved_meta_keys = apply_filters('piklist_reserved_meta_keys',self::$reserved_meta_keys);
    
    if ((isset(self::$reserved_meta_keys[$meta_type]) && in_array($meta_key, self::$reserved_meta_keys[$meta_type])) || !$meta_key)
    {
      return $value;
    }

    $meta_key = '_' . piklist::$prefix . $meta_key;
    
    switch ($meta_type)
    {
      case 'post':
      
        $meta_table = $wpdb->postmeta;
        $meta_id_field = 'meta_id';
        $meta_id = 'post_id';
      
      break;

      case 'term': 
      
        $meta_table = $wpdb->termmeta;
        $meta_id_field = 'meta_id';
        $meta_id = 'term_id';
      
      break;

      case 'user':
      
        $meta_table = $wpdb->usermeta;
        $meta_id_field = 'umeta_id';
        $meta_id = 'user_id';
      
      break;
    }
    
    $is_group = $wpdb->get_var($wpdb->prepare("SELECT $meta_id_field FROM $meta_table WHERE meta_key = %s AND $meta_id = %d", $meta_key, $object_id));
    
    if ($is_group)
    {
      if ($meta_ids = get_metadata($meta_type, $object_id, $meta_key))
      {
        foreach ($meta_ids as &$group)
        {
          foreach ($group as &$meta_id)
          {
            $meta_id = $wpdb->get_var($wpdb->prepare("SELECT meta_value FROM $meta_table WHERE $meta_id_field = %d", $meta_id));
          }
        }
        $value = $meta_ids;
      }
    }

    return $value;
  }
}