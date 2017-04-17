<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Setting
{
  private static $setting_tabs = array();

  private static $settings;
  
  private static $active_section = null;
  
  private static $setting_section_callback_args = array();
  
  public static function _construct()
  {    
    add_action('admin_init', array('piklist_setting', 'register_settings'));
    add_filter('piklist_admin_pages', array('piklist_setting', 'admin_pages'));
  }

  public static function admin_pages($pages) 
  {
    $pages[] = array(
      'page_title' => __('About', 'piklist')
      ,'menu_title' => 'Piklist'
      ,'capability' => defined('PIKLIST_SETTINGS_CAP') ? PIKLIST_SETTINGS_CAP : 'manage_options'
      ,'menu_slug' => 'piklist'
      ,'single_line' => false
      ,'menu_icon' => piklist_admin::responsive_admin() == true ? plugins_url('piklist/parts/img/piklist-menu-icon.svg') : plugins_url('piklist/parts/img/piklist-icon.png')
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
    );
    
    $pages[] = array(
      'page_title' => __('Settings', 'piklist')
      ,'menu_title' => __('Settings', 'piklist')
      ,'capability' => defined('PIKLIST_SETTINGS_CAP') ? PIKLIST_SETTINGS_CAP : 'manage_options'
      ,'sub_menu' => 'piklist'
      ,'menu_slug' => 'piklist-core-settings'
      ,'setting' => 'piklist_core'
      ,'menu_icon' => piklist_admin::responsive_admin() == true ? plugins_url('piklist/parts/img/piklist-menu-icon.svg') : plugins_url('piklist/parts/img/piklist-icon.png') 
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
      ,'default_tab' => 'General'
      ,'single_line' => true
    );

    $pages[] = array(
      'page_title' => __('Add-ons', 'piklist')
      ,'menu_title' => __('Add-ons', 'piklist')
      ,'capability' => defined('PIKLIST_SETTINGS_CAP') ? PIKLIST_SETTINGS_CAP : 'manage_options'
      ,'sub_menu' => 'piklist'
      ,'menu_slug' => 'piklist-core-addons'
      ,'setting' => 'piklist_core_addons'
      ,'menu_icon' => piklist_admin::responsive_admin() == true ? plugins_url('piklist/parts/img/piklist-menu-icon.svg') : plugins_url('piklist/parts/img/piklist-icon.png') 
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
      ,'default_tab' => 'Activate'
      ,'single_line' => true
    );

    return $pages;
  }
  
  public static function get($variable)
  {
    return isset(self::$$variable) ? self::$$variable : false;
  }

  public static function register_settings()
  { 
    piklist::process_views('settings', array('piklist_setting', 'register_settings_callback'));
    
    $default_tabs = piklist_admin::get('admin_page_default_tabs');

    foreach (self::$settings as $setting => $sections)
    {
      add_filter('pre_update_option_' . $setting, array('piklist_setting', 'pre_update_option'), 10, 2);
      register_setting($setting, $setting);
      
      uasort($sections, array('piklist', 'sort_by_order'));
      
      self::$setting_tabs[$setting] = array(
        'default' => array(
          'title' => isset($default_tabs[$setting]) ? __($default_tabs[$setting]) : __('General', 'piklist')
          ,'page' => null
          ,'tab_order' => 10
        )
      );
      
      foreach ($sections as $section) 
      {
        $tab = !empty($section['tab']) ? piklist::dashes($section['tab']) : 'default';
        if (!isset(self::$setting_tabs[$setting][$tab]) && $tab)
        {
          self::$setting_tabs[$setting][$tab] = array(
            'title' => $section['tab']
            ,'page' => $tab
            ,'tab_order' => $section['tab_order']
          );
        }
        elseif ($tab && !empty($section['tab_order']) && empty(self::$setting_tabs[$setting][$tab]['tab_order']))
        {
          self::$setting_tabs[$setting][$tab]['tab_order'] = $section['tab_order'];
        }

        if ((isset($_REQUEST['tab']) && isset($section['tab']) && $_REQUEST['tab'] == $tab) || (!isset($_REQUEST['tab']) && empty($section['tab'])))
        {
          self::$setting_section_callback_args[$section['slug']] = $section;
          
          add_settings_section($section['slug'], $section['title'], array('piklist_setting', 'register_settings_section_callback'), $setting);
        }
      }
      
      uasort(self::$setting_tabs[$setting], array('piklist', 'sort_by_tab_order'));
    }
  }

  public static function register_setting($field)
  {
    add_settings_field(
      isset($field['field']) ? $field['field'] : null
      ,isset($field['label']) ? piklist_form::field_label($field) : null
      ,array('piklist_setting', 'render_setting')
      ,self::$active_section['setting']
      ,self::$active_section['slug']
      ,array(
        'field' => $field
        ,'section' => self::$active_section
      ) 
    );
  }

  public static function register_settings_callback($arguments)
  {
    extract($arguments);
    
    $data = get_file_data($path . '/parts/' . $folder . '/' . $part, apply_filters('piklist_get_file_data', array(
              'title' => 'Title'
              ,'setting' => 'Setting'
              ,'tab' => 'Tab'
              ,'tab_order' => 'Tab Order'
              ,'order' => 'Order'
            ), 'settings'));
            
    $data = apply_filters('piklist_add_part', $data, 'settings');
    
    if (!isset(self::$settings[$data['setting']]))
    {
      self::$settings[$data['setting']] = array();
    }
    
    array_push(self::$settings[$data['setting']]
      ,array_merge($arguments
        ,array_merge($data
          ,array(
            'slug' => piklist::dashes("{$add_on} {$part}")
            ,'page' => piklist::dashes($add_on)
          )
        )
      )
    );
  }
  
  public static function register_settings_section_callback($arguments)
  {
    extract($arguments);

    $section = self::$setting_section_callback_args[$id];
    
    self::$active_section = $section;
    
    do_action('piklist_pre_render_setting_section', $section);
    
    piklist::render($section['path'] . '/parts/' . $section['folder'] . '/' . $section['part']);
  
    do_action('piklist_post_render_setting_section', $section);
  
    self::$active_section = null;
  }

  public static function pre_update_option($new, $old = false)
  {
    if (false !== ($field_data = piklist_validate::check($new)))
    {
      $setting = $_REQUEST['option_page'];
      $_old = $old;
      
      foreach ($field_data[$setting] as $field => &$data)
      {
        if (!isset($data['display']) || (isset($data['display']) && !$data['display']))
        {
          if (!isset($new[$field]) && isset($_old[$field]))
          {
            unset($_old[$field]);
          }
        
          if (((isset($data['add_more']) && !$data['add_more']) || !isset($data['add_more'])) && (isset($new[$field]) && isset($new[$field][0]) && count($new[$field]) == 1))
          {
            $new[$field] = is_array($new[$field][0]) && count($new[$field][0]) == 1 ? $new[$field][0][0] : $new[$field][0];
          }
        
          if (isset($new[$field]) && is_array($new[$field]) && count($new[$field]) > 1 && empty($new[$field][0]) && isset($new[$field][0]))
          {
            unset($new[$field][0]);
            $new[$field] = array_values($new[$field]);
          }
          
          if (isset($data['field']))
          {
            $path = array_merge(array(
                $setting
                ,'name'
              ), strstr($data['field'], ':') ? explode(':', $data['field']) : array($data['field']));
             
            if (piklist::array_path_get($_FILES, $path) && $data['type'] == 'file')
            {
              $data['request_value'] = piklist_form::save_upload($path, $data['request_value'], true);

              $path = explode(':', $data['field']);
              $parent_field = $path[0];

              unset($path[0]);
              
              piklist::array_path_set($new[$parent_field], $path, $data['request_value']);
            }
          }
        }        
      }
      
      $settings = wp_parse_args($new, $_old);
      
      $settings = apply_filters('piklist_pre_update_option', $settings, $setting, $new, $old);
      $settings = apply_filters('piklist_pre_update_option_' . $setting, $settings, $new, $old);
    }
    else
    {
      $settings = $old;
    }

    return $settings;
  }

  public static function render_setting($setting)
  { 
    piklist_form::render_field(wp_parse_args(
      array(
        'scope' => $setting['section']['setting']
        ,'prefix' => false
        ,'disable_label' => true
        ,'position' => false
        ,'value' => piklist_form::get_field_value($setting['section']['setting'], $setting['field'], 'option')
      )
      ,$setting['field']
    ));
  }
}