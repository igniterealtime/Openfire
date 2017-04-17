<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Admin
{
  private static $admin_pages;
  
  private static $admin_page_sections = array();
  
  private static $admin_page_tabs = array();
  
  private static $admin_page_default_tabs = array();
  
  private static $locked_plugins = array();
  
  private static $redirect_post_location_allowed = array(
    'admin_hide_ui'
  );
  
  public static $piklist_dependent = false;
  
  public static $page_icon = false;
  
  public static function _construct()
  {
    add_action('admin_init', array('piklist_admin', 'admin_init'));
    add_action('admin_head', array('piklist_admin', 'admin_head'));
    add_action('wp_head', array('piklist_admin', 'admin_head'));
    add_action('admin_footer', array('piklist_admin', 'admin_footer'));
    add_action('admin_menu', array('piklist_admin', 'admin_menu'), -1);
    add_action('redirect_post_location', array('piklist_admin', 'redirect_post_location'), 10, 2);
    
    add_filter('admin_footer_text', array('piklist_admin', 'admin_footer_text'));
    add_filter('admin_body_class', array('piklist_admin', 'admin_body_class'));

    add_filter('plugin_action_links_piklist/piklist.php', array('piklist_admin', 'plugin_action_links'));
    
    if (is_admin())
    {
      add_filter('piklist_assets', array('piklist_admin', 'assets'));
    }
  }

  public static function admin_init()
  {
    $path = piklist::$paths['piklist'] . '/piklist.php';
    $data = get_file_data($path, array(
              'version' => 'Version'
            ));

    if ($data['version'])
    {
      self::check_update('piklist/piklist.php', $data['version']);
    }
 
    self::$locked_plugins = apply_filters('piklist_locked_plugins', array('piklist/piklist.php'));

    add_action('in_plugin_update_message-piklist/piklist.php', array('piklist_admin', 'update_available'), null, 2);
  }

  public static function admin_menu()
  {
    self::add_admin_pages();
  }

  public static function admin_head()
  {
    if (is_admin() && self::hide_ui())
    {
      piklist::render('shared/admin-hide-ui');
    }
  }

  public static function admin_footer()
  {   
    if (self::$page_icon)
    {
      piklist('shared/page-icon-style', array(
        'page_icon_id' => self::$page_icon['page_id']
        ,'icon_url' => self::$page_icon['icon_url']
      ));
    }
  }
  
  public static function assets($assets)
  {
    global $pagenow;
    
    $scripts = array(
      '/parts/js/admin.js' => piklist::$version
    );
    
    foreach ($scripts as $path => $version)
    {
      array_push($assets['scripts'], array(
        'handle' => str_replace(array('.js', '/'), '', substr($path, strrpos($path, '/')))
        ,'src' => piklist::$urls['piklist'] . $path
        ,'ver' => $version
        ,'deps' => 'jquery'
        ,'enqueue' => true
        ,'in_footer' => true
        ,'admin' => true
      ));
    }
    
    $styles = array(
      '/parts/css/admin.css' => piklist::$version
      ,'/parts/fonts/dashicons.css' => '20140105'
    );
    
    foreach ($styles as $path => $version)
    {
      array_push($assets['styles'], array(
        'handle' => str_replace(array('.css', '/'), '', substr($path, strrpos($path, '/')))
        ,'src' => piklist::$urls['piklist'] . $path
        ,'ver' => $version
        ,'enqueue' => true
        ,'in_footer' => true
        ,'admin' => true
        ,'media' => 'screen, projection'
      ));
    }

    return $assets;
  }

  public static function update_available($plugin_data, $new_plugin_data)
  {
    require_once(ABSPATH . 'wp-admin/includes/plugin-install.php');

    $plugin = plugins_api('plugin_information', array('slug' => $new_plugin_data->slug));

    if (!$plugin || is_wp_error($plugin) || empty($plugin->sections['changelog']))
    {
      return;
    }

    $changes = $plugin->sections['changelog'];

    $pos = strpos($changes, '<h4>' . preg_replace('/[^\d\.]/', '', $plugin_data['Version']));
    
    if ($pos !== false)
    {
      $changes = trim(substr($changes, 0, $pos));
    }

    piklist::render('shared/update-available');
    
    $changes = preg_replace('/<h4>(.*)<\/h4>.*/iU', '', $changes);
    $changes = strip_tags($changes, '<li>');

    echo '<ul class="update-available">' . $changes . '</ul>';
  }
  
  public static function admin_footer_text($footer_text)
  {
    return str_replace('</a>.', sprintf(__('%1$s and %2$sPiklist%1$s.', 'piklist'), '</a>', '<a href="http://piklist.com">'), $footer_text);
  }
  
  public static function hide_ui()
  {
    return isset($_REQUEST[piklist::$prefix]['admin_hide_ui']) && $_REQUEST[piklist::$prefix]['admin_hide_ui'] == 'true';
  }
  
  public static function redirect_post_location($location, $post_id)
  {
    if (isset($_REQUEST[piklist::$prefix]))
    {
      $variables = array(
        'piklist' => array()
      );
      
      foreach ($_REQUEST[piklist::$prefix] as $key => $value)
      {
        if (in_array($key, self::$redirect_post_location_allowed))
        {
          $variables['piklist'][$key] = $value;
        }
      }
      
      $location .= '&' . http_build_query($variables);
    }
    
    return $location;
  }
  
  public static function add_admin_pages() 
  {
    self::$admin_pages = apply_filters('piklist_admin_pages', array());
    
    foreach (self::$admin_pages as $page)
    {
      if (isset($page['sub_menu']))
      {
        add_submenu_page($page['sub_menu'], $page['page_title'], $page['menu_title'], $page['capability'], $page['menu_slug'], array('piklist_admin', 'admin_page'));
      }
      else
      {
        $menu_icon = isset($page['menu_icon']) ? $page['menu_icon'] : (isset($page['icon_url']) ? $page['icon_url'] : null);

        add_menu_page($page['page_title'], $page['menu_title'], $page['capability'], $page['menu_slug'], array('piklist_admin', 'admin_page'), $menu_icon, isset($page['position']) ? $page['position'] : null);
        add_submenu_page($page['menu_slug'], $page['page_title'], $page['page_title'], $page['capability'], $page['menu_slug'], array('piklist_admin', 'admin_page'));
      }
      
      if (isset($page['default_tab']))
      {
        self::$admin_page_default_tabs[isset($page['setting']) ? $page['setting'] : $page['menu_slug']] = $page['default_tab'];
      }
      
      self::$admin_page_tabs[$page['menu_slug']] = array(
        'default' => array(
          'title' => isset($page['default_tab']) ? $page['default_tab'] : __('General', 'piklist')
          ,'page' => null
        )
      );
    }
    
    piklist::process_views('admin-pages', array('piklist_admin', 'admin_pages_callback'));
  }
  
  public static function admin_pages_callback($arguments)
  {
    extract($arguments);
    
    $data = get_file_data($path . '/parts/' . $folder . '/' . $part, apply_filters('piklist_get_file_data', array(
              'title' => 'Title'
              ,'page' => 'Page'
              ,'flow' => 'Flow'
              ,'flow_page' => 'Flow Page'
              ,'tab' => 'Tab'
              ,'order' => 'Order'
              ,'position' => 'Position'
            ), 'admin-pages'));

    $data = apply_filters('piklist_add_part', $data, 'admin-pages');

    if (!empty($data['page']) && (!isset($_REQUEST['flow_page']) || (isset($_REQUEST['flow_page']) && $_REQUEST['flow_page'] === $data['flow_page'])))
    {
      if (!isset(self::$admin_page_sections[$data['page']]))
      {
        self::$admin_page_sections[$data['page']] = array();
      }
    
      array_push(self::$admin_page_sections[$data['page']]
        ,array_merge($arguments
          ,array_merge($data
            ,array(
              'slug' => piklist::dashes("{$add_on} {$part}")
              ,'page' => piklist::dashes($add_on)
              ,'part' => $path . '/parts/admin-pages/' . $part
            )
          )
        )
      );
      
      $tab = !empty($data['tab']) ? piklist::dashes($data['tab']) : 'default';
      if (!isset(self::$admin_page_tabs[$data['page']][$tab]) && $tab)
      {
        self::$admin_page_tabs[$data['page']][$tab] = array(
          'title' => $data['tab']
          ,'page' => $tab
        );
      }
    
      uasort(self::$admin_page_sections[$data['page']], array('piklist', 'sort_by_order'));
    }
  }
  
  public static function admin_page() 
  {
    $page = false;

    foreach (self::$admin_pages as $admin_page)
    {
      if ($_REQUEST['page'] === $admin_page['menu_slug'])
      {
        $page = $admin_page;
    
        break;
      }
    }

    if ($page)
    {
      $setting_tabs = piklist_setting::get('setting_tabs');

      piklist::render('shared/admin-page', array(
        'section' => $page['menu_slug']
        ,'notice' => isset($page['sub_menu']) ? !in_array($page['sub_menu'], array('options-general.php')) : false
        ,'icon' => isset($page['icon']) ? $page['icon'] : false
        ,'page_icon' => isset($page['page_icon']) ? $page['page_icon'] : (isset($page['icon']) ? $page['icon'] : null)         
        ,'single_line' => isset($page['single_line']) ? $page['single_line'] : false
        ,'title' => ($page['page_title'])
        ,'setting' => isset($page['setting']) ? $page['setting'] : false
        ,'tabs' => isset($page['setting']) && isset($setting_tabs[$page['setting']]) ? $setting_tabs[$page['setting']] : self::$admin_page_tabs[$page['menu_slug']]
        ,'page_sections' => isset(self::$admin_page_sections[$page['menu_slug']]) ? self::$admin_page_sections[$page['menu_slug']] : array()
        ,'save' => isset($page['save']) ? $page['save'] : true
        ,'save_text' => isset($page['save_text']) ? $page['save_text'] : __('Save Changes','piklist')
        ,'page' => isset($admin_page['page']) ? $admin_page['page'] : false
      ));
    }
  }
  
  public static function admin_body_class($classes = '')
  {
    global $typenow;

    $classes .= $classes;

    if (piklist_admin::$piklist_dependent == true)
    {
      $classes .= 'piklist-dependent' . ' ';
    }

    if (piklist_admin::responsive_admin() == true)
    {
      $classes .= 'responsive-admin' . ' ';
    }

    if (isset($_GET['taxonomy']))
    {
      $classes .= 'taxonomy-' . $_GET['taxonomy'] . ' '; 
    }

    if ($typenow)
    { 
      $classes .= 'post_type-' . $typenow;
    }

    return $classes;
  }
  
  public static function get($variable)
  {
    return isset(self::$$variable) ? self::$$variable : false;
  }

  public static function deactivation_link()
  {
    add_filter('plugin_action_links_piklist/piklist.php', array('piklist_admin', 'replace_deactivation_link'));
    add_filter('network_admin_plugin_action_links_piklist/piklist.php', array('piklist_admin', 'replace_deactivation_link'));    
    
    $classes = 'piklist-dependent';

    add_filter('admin_body_class', array('piklist_admin', 'admin_body_class'));

  }

  public static function replace_deactivation_link($actions)
  {
    unset($actions['deactivate']);
    
    array_unshift($actions, '<p>' . sprintf(__('Dependent plugins or theme are active.', 'piklist'),'<br>') . (is_network_admin() ? sprintf(__('%1$s Network Deactivate', 'piklist'), '</p>') : sprintf(__('%1$sDeactivate', 'piklist'), '</p>'))); 

    return $actions;
  }

  public static function plugin_action_links($links)
  {
    $links[] = '<a href="' . get_admin_url(null, 'admin.php?page=piklist-core-settings') . '">' . __('Settings','piklist') . '</a>';
    $links[] = '<a href="' . get_admin_url(null, 'admin.php?page=piklist-core-addons') . '">' . __('Demo','piklist') . '</a>';
   
    return $links;
  }

  public static function check_update($file, $version)
  {
    global $pagenow;

    if (!in_array($pagenow, array('plugins.php', 'update-core.php', 'update.php', 'index.php')) || !is_admin() || !current_user_can('manage_options'))
    {
      return;
    }
     
    $plugin = plugin_basename($file);

    if (is_plugin_active_for_network($plugin))
    {
      $versions = get_site_option('piklist_active_plugin_versions', array());
      $network_wide = true;
    }
    elseif (is_plugin_active($plugin))
    {
      $versions = get_option('piklist_active_plugin_versions', array());
      $network_wide = false;
    }
    else
    {
      return;
    }

    if (!isset($versions[$plugin]))
    {
      $versions[$plugin] = array('0');
    }
    
    if (!is_array($versions[$plugin]))
    {
      $versions[$plugin] = array($versions[$plugin]);
    }
    
    $current_version = is_array($versions[$plugin]) ? current($versions[$plugin]) : $versions[$plugin];


    if (version_compare($version, $current_version, '>'))
    {
      self::get_update($file, $version, $current_version);
      
      array_unshift($versions[$plugin], $version);
    }

    if ($network_wide)
    { 
      update_site_option('piklist_active_plugin_versions', $versions);
    }
    else
    { 
      update_option('piklist_active_plugin_versions', $versions);
    }
  }

  public static function get_update($file, $version, $current_version)
  {
    $updates_url = WP_PLUGIN_DIR . '/' . dirname($file) . '/parts/updates/';
    $updates = piklist::get_directory_list($updates_url);

    if ($updates)
    {
      array_multisort($updates);
    }
    else
    {
      return;
    }

    $operator = $current_version ? '=' : '>='; // Upgrade : Install
    $valid_updates = array();
    foreach ($updates as $update)
    {
      $update_version_number = rtrim($update, '.php');

      if (version_compare($version, $update_version_number, $operator))
      {
        $update_code = file_get_contents($updates_url . $update);      
        $stripped_update_code = str_ireplace(array('<?php', '<?', '?>'), '', $update_code);
        $update_function = create_function('', $stripped_update_code);
        $valid_updates[$update] = $update_function;
      }
    }

    if ($valid_updates)
    {
      piklist::check_network_propagate(array('piklist_admin', 'run_update'), $valid_updates);
    }
  }

  public static function run_update($valid_updates)
  {
    piklist::performance();
    
    foreach ($valid_updates as $valid_update)
    {
      $function = $valid_update;
      $function();
    }
  }

  public static function responsive_admin()
  {
    if (version_compare($GLOBALS['wp_version'], '3.8', '>=' ))
    {
      return true;
    }
    else
    {
      return false;
    }
  }
}