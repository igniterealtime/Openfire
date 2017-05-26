<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Workflow
{
  private static $workflows;
  
  private static $after_positions = array(
    'header' => 'in_admin_header'
    ,'body' => 'all_admin_notices'
    ,'title' => 'edit_form_after_title'
    ,'editor' => 'edit_form_after_editor'
    ,'profile' => 'profile_personal_options'
  );
  
  public static function _construct()
  {    
    add_action('init', array('piklist_workflow', 'init'));
  }
  
  public static function init()
  { 
    foreach (self::$after_positions as $position => $filter)
    {
      add_action($filter, array('piklist_workflow', 'workflows'));   
    }
    
    add_filter('redirect_post_location', array('piklist_workflow', 'redirect'), 10, 2);
    add_filter('wp_redirect', array('piklist_workflow', 'redirect'), 10, 2);
    
    self::register_workflows();
  }
  
  public static function register_workflows()
  {
    piklist::process_views('workflows', array('piklist_workflow', 'register_workflows_callback'));
  }

  public static function redirect($location, $post_id)
  {
    if (isset($_REQUEST['_wp_http_referer']))
    {
      $url = parse_url($_REQUEST['_wp_http_referer']);
    
      if (isset($url['query']))
      {
        parse_str($url['query'], $url_defaults);
    
        if ((isset($url_defaults['flow']) && !stristr($location, 'flow=')) && (isset($url_defaults['flow_page']) && !stristr($location, 'flow_page=')))
        {
          $location .= (stristr($location, '?') ? '&' : null) . 'flow=' . urlencode($url_defaults['flow']) . '&flow_page=' . urlencode($url_defaults['flow_page']);
        }
      }
    }

    return $location;
  }

  public static function is_active($data)
  {
    $is_active = false;

    if (isset($_REQUEST['flow_page']))
    {
      $is_active = $_REQUEST['flow_page'] == $data['page_slug'];
    }
    elseif (!empty($data))
    {
      global $post, $current_user, $pagenow;
      
      $post = !$post ? (isset($_REQUEST['post']) ? get_post($_REQUEST['post']) : false) : $post;

      foreach ($data as $key => $value)
      {
        $value = is_array($value) ? array_filter($value) : $value;
        
        if (!empty($value))
        {
          switch ($key)
          {
            case 'post_type':

              $is_active = ($post ? $post->post_type : (isset($_REQUEST['post_type']) && post_type_exists($_REQUEST['post_type']) ? $_REQUEST['post_type'] : null)) == $value;
              
            break;
          
            case 'page':
                          
              $is_active = (is_array($value) && in_array($pagenow, $value)) || (is_string($value) && $pagenow == $value);
          
            break;
          }
        }
      }
    }
    
    if ($is_active)
    {
      $data['active'] = true;
    }

    return $data;
  }

  public static function register_workflows_callback($arguments)
  {
    global $pagenow;
    
    extract($arguments);
    
    $data = get_file_data($path . '/parts/' . $folder . '/' . $part, apply_filters('piklist_get_file_data', array(
              'name' => 'Title'
              ,'description' => 'Description'
              ,'capability' => 'Capability'
              ,'order' => 'Order'
              ,'flow' => 'Flow'
              ,'page' => 'Page'
              ,'post_type' => 'Post Type'
              ,'taxonomy' => 'Taxonomy'
              ,'role' => 'Role'
              ,'redirect' => 'Redirect'
              ,'header' => 'Header'
              ,'disable' => 'Disable'
              ,'position' => 'Position'
              ,'default' => 'Default'
            ), 'workflows'));

    $data = apply_filters('piklist_add_part', $data, 'workflows');

    if (!isset($data['flow']))
    {
      return null;
    }

    if (((!isset($data['capability']) || empty($data['capability'])) || ($data['capability'] && current_user_can(strtolower($data['capability']))))
      && ((!isset($data['role']) || empty($data['role'])) || piklist_user::current_user_role($data['role']))
    )
    {
      if (!empty($data['page']))
      {
        $data['page'] = strstr($data['page'], ',') ? piklist::explode(',', $data['page']) : array($data['page']);
      }

      $data['page_slug'] = piklist::slug($data['name']);
      $data['flow_slug'] = piklist::slug($data['flow']);

      if (!$data['header'])
      {
        if ($data['page'] && !is_int(array_search($pagenow, $data['page'])))
        {
          return null;
        }
        
        $data = self::is_active($data);
      }
      
      if (in_array($pagenow, array('admin.php', 'users.php', 'plugins.php', 'options-general.php')) && $data['position'] == 'title')
      {
        $data['position'] = 'header';
      }
      
      $workflow = array(
        'config' => $data
        ,'part' => $path . '/parts/' . $folder . '/' . $part
      );
      
      if (!isset(self::$workflows[$data['flow']]))
      {
        self::$workflows[$data['flow']] = array();
      }
      
      if ($data['header'] == 'true')
      {
        array_unshift(self::$workflows[$data['flow']], $workflow);
      }
      elseif (!empty($data['order']))
      {
        self::$workflows[$data['flow']][$data['order']] = $workflow;
      }
      else
      {
        array_push(self::$workflows[$data['flow']], $workflow);
      }
    }
  }
  
  public static function workflows()
  {
    if (empty(self::$workflows))
    {
      return false;
    }

    global $pagenow, $typenow, $post;
    
    reset(self::$after_positions);
    $current_position = key(self::$after_positions);

    foreach (self::$workflows as $workflow => $path)
    {
      uasort($path, array('piklist', 'sort_by_config_order'));

      $path = array_values($path);
      $first = current($path);
      $position = isset($first['config']['position']) ? $first['config']['position'] : false;

      if ($position)
      {
        $pages = $first['config']['page'];
        $post_type = isset($first['config']['post_type']) ? $first['config']['post_type'] : false;

        if (
            ((in_array($pagenow, $pages) || (in_array($pagenow, array('admin.php', 'edit.php', 'users.php', 'plugins.php', 'options-general.php')) && (isset($_REQUEST['page']) && in_array($_REQUEST['page'], $pages))))
              && (!$post_type || ($post_type && (($post && $typenow == $post_type) || (!$post && isset($_REQUEST['post_type']) == $post_type))))
            )
            ||
            (isset($_REQUEST['flow']) && piklist::slug($workflow) == $_REQUEST['flow'])
        )
        {
          $default = null;
          $has_active = false;
          foreach ($path as &$tab)
          {
            if (isset($tab['config']['default']) && $tab['config']['default'] == true)
            {
              $default = &$tab;
            }
            if (isset($tab['config']['active']))
            {
              $has_active = true;
            }
          }
          
          if (!$has_active && $default)
          {
            $default['config']['active'] = true;
          }
          
          if ($position == $current_position)
          {
            piklist::render('shared/admin-workflow', array(
              'workflow' => $workflow
              ,'path' => $path
              ,'pages' => $pages
              ,'position' => $position
            ));
          }
        }
      }
    }
    
    array_shift(self::$after_positions);
  }
}