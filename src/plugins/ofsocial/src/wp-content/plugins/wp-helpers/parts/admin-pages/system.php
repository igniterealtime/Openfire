<?php
/*
Title: WordPress
Page: piklist_wp_helpers_system_information
Save: false
*/
?>

<style type="text/css">
  .widefat td, .widefat th {
    border-bottom-color: #DFDFDF;
    border-top-color: #DFDFDF;
  }

  .widefat td.e {
    width: 10%;
  }

  .widefat td.v {
    width: 90%;
  }

</style>

<?php

piklist_site_inventory();
clean_phpinfo();



function clean_phpinfo()
{
  ob_start();
  phpinfo();
  $phpinfo = ob_get_contents();
  ob_end_clean();

  $phpinfo = preg_replace( '%^.*<body>(.*)</body>.*$%ms','$1',$phpinfo);
  $phpinfo = str_ireplace('width="600"','class="form-table widefat"',$phpinfo);

  echo $phpinfo;
}


function piklist_site_inventory()
{
  global $wp_version, $wpdb;

  $theme_data = wp_get_theme();
  $theme = $theme_data->Name . ' ' . $theme_data->Version  . '( ' . strip_tags($theme_data->author) . ' )';

  $page_on_front_id = get_option('page_on_front'); 
  $page_on_front = get_the_title($page_on_front_id) . ' (#' . $page_on_front_id . ')';

  $page_for_posts_id = get_option('page_for_posts'); 
  $page_for_posts = get_the_title($page_for_posts_id) . ' (#' . $page_for_posts_id . ')';

  $table_prefix_length = strlen($wpdb->prefix);
  if (strlen( $wpdb->prefix )>16 )
  {
    $table_prefix_status = sprintf(__('%1$sERRO: Too long%2$s', 'piklist-toolbox'), ' (', ')');
  }
  else
  {
    $table_prefix_status = sprintf(__('%1$sAcceptable%2$s', 'piklist-toolbox'), ' (', ')');
  };

  $wp_debug = defined('WP_DEBUG') ? WP_DEBUG ? __('Enabled', 'piklist-toolbox') . "\n" : __('Disabled', 'piklist-toolbox') . "\n" : __('Not set', 'piklist-toolbox');

  $php_safe_mode = ini_get('safe_mode') ? __('Yes', 'piklist-toolbox') : __('No', 'piklist-toolbox');
  $allow_url_fopen = ini_get('allow_url_fopen') ? __('Yes', 'piklist-toolbox') : __('No', 'piklist-toolbox'); 

  $plugins_active = array();
  $plugins = get_plugins();
  $active_plugins = get_option( 'active_plugins', array() );

  foreach ($plugins as $plugin_path => $plugin)
  {
    if (in_array($plugin_path, $active_plugins))
    {
      $plugins_active[] = $plugin['Name'] . ': ' . $plugin['Version'] . ' (' . strip_tags($plugin['Author']) . ')';
    }
  }

  // Widgets
  $all_widgets = '';
  $sidebar_widgets = '';
  $current_sidebar = '';
  $active_widgets = get_option('sidebars_widgets');
  if (is_array($active_widgets) && count($active_widgets))
  {
    foreach ($active_widgets as $sidebar => $widgets)
    {
      if (is_array($widgets))
      {
        if ($sidebar != $current_sidebar)
        {
          $sidebar_widgets .= $sidebar . ': ';
          $current_sidebar = $sidebar;
        }
        if (count($widgets))
        {
          $sidebar_widgets .= implode(', ', $widgets);
          $all_widgets[] = $sidebar_widgets;
        }
        else
        {
          $sidebar_widgets .= __('(none)', 'piklist');
          $all_widgets[] = $sidebar_widgets;
        }
        
        $sidebar_widgets = '';
      }
    }
  }


    piklist::render('shared/system-info', array(
      'theme' => $theme
      ,'wordpress_version' => get_bloginfo('version')
      ,'multisite' => is_multisite() ? __('WordPress Multisite', 'piklist') : __('WordPress (single user)', 'piklist')
      ,'permalinks' => get_option('permalink_structure') == '' ? $permalinks = __('Query String (index.php?p=123)', 'piklist') : $permalinks = __('Pretty Permalinks', 'piklist')
      ,'page_on_front' => $page_on_front
      ,'page_for_posts' => $page_for_posts
      ,'table_prefix_status' => $table_prefix_status
      ,'table_prefix_length' => $table_prefix_length
      ,'wp_debug' => $wp_debug
      ,'users_can_register' => get_option('users_can_register') == '1' ?  __('Yes', 'piklist') : __('No', 'piklist')
      ,'enable_xmlrpc' => get_option('enable_xmlrpc') == '1' ?  __('Yes', 'piklist') : __('No', 'piklist')
      ,'enable_app' => get_option('enable_app') == '1' ? __('Yes', 'piklist') : __('No', 'piklist')
      ,'blog_public' => get_option('blog_public') == '1' ? __('Public', 'piklist') : __('Private', 'piklist')
      ,'rss_use_excerpt' => get_option('rss_use_excerpt') == '1' ? __('Summaries', 'piklist') : __('Full Content', 'piklist')
      ,'php_safe_mode' => $php_safe_mode
      ,'allow_url_fopen' => $allow_url_fopen
      ,'plugins_active' => $plugins_active
      ,'sidebar_widgets' => $all_widgets
  ));
}