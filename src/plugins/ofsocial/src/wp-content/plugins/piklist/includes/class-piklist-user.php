<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_User
{
  private static $meta_boxes = array();
  
  private static $meta_box_nonce = false;
  
  public static function _construct()
  {    
    add_action('init', array('piklist_user', 'init'));
    add_action('show_user_profile', array('piklist_user', 'meta_box'));
    add_action('edit_user_profile', array('piklist_user', 'meta_box'));
    add_action('personal_options_update', array('piklist_user', 'process_form'));
    add_action('edit_user_profile_update', array('piklist_user', 'process_form'));
  }
  
  public static function init()
  {   
    self::register_meta_boxes();

    $use_multiple_user_roles = piklist::get_settings('piklist_core', 'multiple_user_roles');

    if ($use_multiple_user_roles && (!is_multisite() || ($pagenow == 'user-edit.php' && is_multisite())))
    {
      add_action('profile_update', array('piklist_user', 'multiple_roles'));
      add_action('user_register', array('piklist_user', 'multiple_roles'), 9);
      add_action('admin_footer', array('piklist_user', 'multiple_roles_field'));
      
      add_filter('additional_capabilities_display', array('piklist_user', 'additional_capabilities_display'));
    }
  }

  public static function register_meta_boxes()
  {
    piklist::process_views('users', array('piklist_user', 'register_meta_boxes_callback'));
  }

  public static function register_meta_boxes_callback($arguments)
  {
    extract($arguments);
    
    $current_user = wp_get_current_user();
    
    $data = get_file_data($path . '/parts/' . $folder . '/' . $part, apply_filters('piklist_get_file_data', array(
              'name' => 'Title'
              ,'description' => 'Description'
              ,'capability' => 'Capability'
              ,'order' => 'Order'
              ,'role' => 'Role'
              ,'new' => 'New'
            ), 'users'));
    
    $data = apply_filters('piklist_add_part', $data, 'users');
            
    $data = array_filter($data);
   
    $meta_box = array(
      'id' => piklist::slug($data['name'])
      ,'config' => $data
      ,'part' => $path . '/parts/' . $folder . '/' . $part
    );
    
    if ((!isset($data['capability']) || ($data['capability'] && current_user_can(strtolower($data['capability']))))
      && (!isset($data['role']) || piklist_user::current_user_role($data['role']))
      && (!isset($data['new']) || ($data['new'] && (isset($pagenow) && $pagenow != 'user-new.php')))
    )
    {
      if (isset($order))
      {
        self::$meta_boxes[$order] = $meta_box;
      }
      else
      {
        array_push(self::$meta_boxes, $meta_box);
      }
    }    
  }

  public static function meta_box($user_id)
  {
    if (!empty(self::$meta_boxes))
    {
      if (!self::$meta_box_nonce)
      {
        piklist_form::render_field(array(
          'type' => 'hidden'
          ,'field' => 'nonce'
          ,'value' => wp_create_nonce(plugin_basename(piklist::$paths['piklist'] . '/piklist.php'))
          ,'scope' => piklist::$prefix
        ));
      
        self::$meta_box_nonce = true;
      }
      
      $user = get_userdata($user_id);

      foreach (self::$meta_boxes as $meta_box)
      {
        piklist::render('shared/meta-box-start', array(
          'meta_box' => $meta_box
          ,'wrapper' => 'user_meta'
        ), false);

        do_action('piklist_pre_render_user_meta_box', $user, $meta_box);
  
        piklist::render($meta_box['part'], array(
          'user_id' => $user_id
          ,'prefix' => 'piklist'
          ,'plugin' => 'piklist'
        ), false);

        do_action('piklist_post_render_user_meta_box', $user, $meta_box);
        
        piklist::render('shared/meta-box-end', array(
          'meta_box' => $meta_box
          ,'wrapper' => 'user_meta'
        ), false);
      }
    }
  }
  
  public static function process_form($user_id)
  {
    piklist_form::process_form(array(
      'user' => $user_id
    ));
  }
  
  public static function additional_capabilities_display($user_id)
  {
    return false;
  }
  
  public static function multiple_roles($user_id, $roles = false)
  {
    global $wpdb, $wp_roles, $current_user, $pagenow;
    
    $roles = $roles ? $roles : (isset($_POST['roles']) && isset($_POST['roles'][0]) ? $_POST['roles'][0] : false);

    if ($roles && current_user_can('edit_user', $current_user->ID))
    {      
      $editable_roles = get_editable_roles();
      $user = new WP_User($user_id);
      $user_roles = array_intersect(array_values($user->roles), array_keys($editable_roles));
      
      $_user_role_log = get_user_meta($user_id, $wpdb->prefix . 'capabilities_log', true);
      $user_role_log = $_user_role_log ? $_user_role_log : array();
      
      $roles = is_array($roles) ? $roles : array($roles);
      foreach ($roles as $role)
      {
        if (!in_array($role, $user_roles) && $wp_roles->is_role($role))
        {
          $user->add_role($role);
          
          array_push($user_role_log, array(
            'action' => 'add'
            ,'role' => $role
            ,'timestamp' => time()
          ));
        }
      }
      
      foreach ($user_roles as $role)
      {
        if (!in_array($role, $roles) && $wp_roles->is_role($role))
        {
          $user->remove_role($role);
          
          array_push($user_role_log, array(
            'action' => 'remove'
            ,'role' => $role
            ,'timestamp' => time()
          ));
        }
      }
      
      update_user_meta($user_id, $wpdb->prefix . 'capabilities_log', $user_role_log);
    }
  }
  
  public static function multiple_roles_field($user)
  {
    global $pagenow, $user_id;
    
    if (in_array($pagenow, array('user-edit.php', 'user-new.php')))
    {
      $editable_roles = get_editable_roles();

      if ($user_id)
      {
        $user = get_user_to_edit($user_id);
        $user_roles = array_intersect(array_values($user->roles), array_keys($editable_roles));
      }
      else
      {
        $user_roles = null;
      }

      $roles = array();
      foreach ($editable_roles as $role => $details) 
      {
        $roles[$role] = translate_user_role($details['name']); 
      }
    
      piklist::render('shared/field-user-role', array(
        'user_roles' => $user_roles
        ,'roles' => $roles
      ), false);
    }
  }

  public static function available_capabilities()
  {
    global $wp_roles;

    $roles = array();
    $capabilities = array();

    $roles = $wp_roles->role_objects;

    $caps = piklist($roles, array('capabilities'));

    foreach ($caps as $section => $items)
    {
      foreach ($items as $key => $value)
      {
        $capabilities[$key] = ucwords(str_replace('_', ' ', $key));
      }
    }

    $capabilities = array_unique($capabilities);
    natcasesort($capabilities);

    return $capabilities;
  }

  public static function available_roles()
  {
    global $wp_roles;
    
    $roles = $wp_roles->get_names();

    return $roles;
  }

  public static function current_user_role($roles)
  {
    $current_user = wp_get_current_user();

    $roles = is_array($roles) ? $roles : explode(',', $roles);

    $roles_array = array_filter(array_map('trim', $roles));
    $roles_array = array_filter(array_map('strtolower', $roles_array));

    foreach ($current_user->roles as $user_role)
    {
      if(in_array(strtolower($user_role), $roles_array))
      {
        return true;
      }
    }

    return false;
  }
  
  public static function include_user_profile_fields($arguments)
  {
    global $wp_filter;
    
    $tags = array(
      'show_user_profile'
      ,'edit_user_profile'
      ,'personal_options_update'
      ,'edit_user_profile_update'
    );
    
    if (isset($arguments['actions']))
    {
      foreach ($tags as $tag)
      {
        foreach ($wp_filter[$tag] as $priority => $callback)
        {
          foreach ($callback as $id => $config)
          {
            foreach ($arguments['actions'] as $action)
            {
              if (strstr($id, $action))
              {
                $idx = _wp_filter_build_unique_id($action, $id, $priority);
                if ($idx == $id)
                {
                  array_push($arguments['actions'], $id);
                }
              }
            }

            if (!in_array($id, $arguments['actions']) && ($wp_filter[$tag][$priority][$id]['function'][0] != 'piklist_form' && $wp_filter[$tag][$priority][$id]['function'][0] != 'save_fields'))
            {
              unset($wp_filter[$tag][$priority][$id]);
            }
          }
        
          if (empty($wp_filter[$tag][$priority]))
          {
            unset($wp_filter[$tag][$priority]);
          }
        }
      
        if (empty($wp_filter[$tag]))
        {
          unset($wp_filter[$tag]);
        }
      }
    }
    
    if (isset($arguments['meta_boxes']))
    {
      $count = count(self::$meta_boxes);
      for ($i = 0; $i < $count; $i++)
      {
        if (!in_array(self::$meta_boxes[$i]['config']['name'], $arguments['meta_boxes']))
        {
          unset(self::$meta_boxes[$i]);
        }
      }
    }

    piklist('shared/admin-user-profile-fields', array(
      'meta_boxes' => isset($arguments['meta_boxes']) ? $arguments['meta_boxes'] : array()
    ));
  }
}