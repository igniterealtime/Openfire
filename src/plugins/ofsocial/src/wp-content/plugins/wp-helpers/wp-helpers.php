<?php
/*
Plugin Name: WordPress Helpers
Plugin URI: http://piklist.com
Description: Enhanced settings for WordPress. Located under <a href="tools.php?page=piklist_wp_helpers">Tools > Helpers</a>
Version: 1.9.3
Author: Piklist
Author URI: http://piklist.com/
Plugin Type: Piklist
Text Domain: wp-helpers
Domain Path: /languages
License: GPLv2
*/

/*  
  Copyright (c) 2012-2015 Piklist, LLC.
  All rights reserved.

  This software is distributed under the GNU General Public License, Version 2,
  June 1991. Copyright (C) 1989, 1991 Free Software Foundation, Inc., 51 Franklin
  St, Fifth Floor, Boston, MA 02110, USA

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

  *******************************************************************************
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  *******************************************************************************
*/

add_action('init', array('piklist_wordpress_helpers', 'init'), -1);
add_action('admin_init', array('piklist_wordpress_helpers', 'admin_init'), -1);

class Piklist_WordPress_Helpers
{
  public static $prefix = '_pik_wph_';

  private static $options = null;

  public static $registered_widgets = array();

  private static $dashboard_widgets = null;

  private static $list_table_columns = array();
  
  private static $filter_priority = 9999;

  public static function admin_init()
  {
    add_action('in_plugin_update_message-' . plugin_basename(__FILE__), array('piklist_admin', 'update_available'), null, 2);
    add_filter('plugin_row_meta', array('piklist_wordpress_helpers', 'plugin_row_meta'), 10, 2);
  }

  public static function init()
  {
    if (is_admin())
    {
      include_once('includes/class-piklist-checker.php');
   
      if (!piklist_checker::check(__FILE__))
      {
        return;
      }

      add_filter('piklist_part_process-settings', array('piklist_wordpress_helpers', 'assign_workflows'),1); 
      
      add_filter('piklist_admin_pages', array('piklist_wordpress_helpers', 'admin_pages'));   
    }
    
    self::helpers();
  }

  public static function admin_pages($pages) 
  {    
    $pages[] = array(
      'page_title' => 'WordPress Helpers'
      ,'menu_title' =>  'Helpers'
      ,'sub_menu' => 'tools.php'
      ,'capability' => 'manage_options'
      ,'menu_slug' => 'piklist_wp_helpers'
      ,'setting' => 'piklist_wp_helpers'
      ,'icon_url' => plugins_url('piklist/parts/img/piklist-icon.png') 
      ,'icon' => 'piklist-page'
      ,'single_line' => false
      ,'default_tab' => 'General'
    );
 
    return $pages;
  }

  public static function system_admin_page($pages)
  {
    $pages[] = array(
      'page_title' => __('System Information')
      ,'menu_title' => __('System', 'piklist')
      ,'sub_menu' => 'tools.php'
      ,'capability' => 'manage_options'
      ,'menu_slug' => 'piklist_wp_helpers_system_information'
      ,'icon_url' => plugins_url('piklist/parts/img/piklist-icon.png') 
      ,'icon' => 'piklist-page'
    );

    return $pages;
  }

  public static function plugin_row_meta($links, $file)
  {
    if ($file == 'wp-helpers/wp-helpers.php')
    {
      $links[] = '<a href="https://piklist.com/support/forum/wordpress-helpers/" target="_blank">' . __('Support', 'piklist') . '</a>';
    }

    return $links;
  }

  /**
   * Forward compatiblity with Piklist v0.9.9
   */
  public static function assign_workflows($_part)
  {
    if($_part['data']['flow'][0] == 'wp_helpers_settings_flow')
    {
      if(empty($_part['data']['tab'][0]))
      {

        $_part['data']['tab'][0] = 'general';
      }
    }

    return $_part;
  }

  public static function helpers() 
  {
    if (self::$options = get_option('piklist_wp_helpers'))
    {
      foreach (self::$options as $option => $value)
      {
        $value = (is_array($value)) && (count($value) == 1) && (array_key_exists('0', $value)) ? $value[0] : $value;
        
        if ($value == 'true')
        {
          switch ($option)
          {
            case 'shortcodes_in_widgets':
              add_filter('widget_text', 'do_shortcode', self::$filter_priority);
            break;
          
            case 'private_title_format':
              add_filter('private_title_format', array('piklist_wordpress_helpers', 'title_format'), self::$filter_priority);  
            break;

            case 'protected_title_format':
              add_filter('protected_title_format', array('piklist_wordpress_helpers', 'title_format'), self::$filter_priority);                
            break;

            case 'disable_autosave':
              add_action('admin_enqueue_scripts', array('piklist_wordpress_helpers', 'disable_autosave'), self::$filter_priority);
            break;

            case 'admin_color_scheme':
              self::remove_filter('admin_color_scheme_picker', 'admin_color_scheme_picker');
            break;

            case 'disable_feeds':
              add_filter('wp', array('piklist_wordpress_helpers', 'disable_feed'), self::$filter_priority);
            break;

            case 'featured_image_in_feed':
              add_filter('the_excerpt_rss', array('piklist_wordpress_helpers', 'featured_image'), self::$filter_priority);
              add_filter('the_content_feed', array('piklist_wordpress_helpers', 'featured_image'), self::$filter_priority);
            break;

            case 'disable_visual_editor':
              add_filter('user_can_richedit', '__return_false', self::$filter_priority);
            break;

            case 'excerpt_wysiwyg':
              add_action('init', array('piklist_wordpress_helpers', 'remove_post_excerpt_box'), self::$filter_priority);
              add_filter('piklist_add_part', array('piklist_wordpress_helpers', 'replace_post_excerpt_box'), self::$filter_priority, 2); // Piklist v0.9.4.x
              add_filter('piklist_part_add', array('piklist_wordpress_helpers', 'replace_post_excerpt_box'), self::$filter_priority, 2); // Piklist v0.9.5.x
            break;

            case 'show_additional_image_sizes' :
              add_filter('piklist_add_part', array('piklist_wordpress_helpers', 'show_additional_image_sizes'), self::$filter_priority, 2); // Piklist v0.9.4.x
              add_filter('piklist_part_add', array('piklist_wordpress_helpers', 'show_additional_image_sizes'), self::$filter_priority, 2); // Piklist v0.9.5.x
            break;

            case 'show_exif_data' :
              add_filter('piklist_add_part', array('piklist_wordpress_helpers', 'show_exif_data'), self::$filter_priority, 2); // Piklist v0.9.4.x
              add_filter('piklist_part_add', array('piklist_wordpress_helpers', 'show_exif_data'), self::$filter_priority, 2); // Piklist v0.9.5.x
            break;

            case 'hide_admin_bar':
              add_filter('show_admin_bar', '__return_false');
              add_action('piklist_wordpress_helpers_admin_css', array('piklist_wordpress_helpers', 'hide_admin_bar_profile_option'), self::$filter_priority);
            break;

            case 'show_ids':
              Piklist_WordPress_Helpers::$list_table_columns[] = 'show_ids';

              add_filter('request', array('piklist_wordpress_helpers', 'column_orderby_id'));
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_post_types'), self::$filter_priority);
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_multisite'), self::$filter_priority);
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_users'), self::$filter_priority);
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_link_manager'), self::$filter_priority);
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_taxonomies'), self::$filter_priority);
              add_action('piklist_wordpress_helpers_admin_css', array('piklist_wordpress_helpers', 'column_id_width'), self::$filter_priority);
            break;

            case 'show_featured_image':
              Piklist_WordPress_Helpers::$list_table_columns[] = 'show_featured_image';
              add_action('admin_init', array('piklist_wordpress_helpers', 'list_table_post_types'), self::$filter_priority);
            break;

            case 'all_options':
              add_action('admin_menu', array('piklist_wordpress_helpers', 'all_options_menu'), self::$filter_priority);
            break;

            case 'disable_emojis':
              // @credit https://wordpress.org/plugins/disable-emojis/
              remove_action('wp_head', 'print_emoji_detection_script', 7);
              remove_action('admin_print_scripts', 'print_emoji_detection_script');
              remove_action('wp_print_styles', 'print_emoji_styles');
              remove_action('admin_print_styles', 'print_emoji_styles');  
              remove_filter('the_content_feed', 'wp_staticize_emoji');
              remove_filter('comment_text_rss', 'wp_staticize_emoji');  
              remove_filter('wp_mail', 'wp_staticize_emoji_for_email');
              add_filter('tiny_mce_plugins', array('piklist_wordpress_helpers', 'disable_emojis_tinymce'), self::$filter_priority);
              add_action('piklist_wordpress_helpers_admin_css', array('piklist_wordpress_helpers', 'hide_option_use_smilies'), self::$filter_priority);
            break;

            case 'make_clickable':
              self::remove_filter('comment_text', 'make_clickable');
            break;

            case 'theme_switcher':
              if (is_multisite() && !is_super_admin())
              {
                add_action('admin_init', array('piklist_wordpress_helpers', 'theme_switcher'), self::$filter_priority); 
              }
            break;

            case 'remove_screen_options':
              add_filter('screen_options_show_screen', '__return_false', self::$filter_priority);
            break;

            case 'comments_open_pages':
              add_filter('comments_open', array('piklist_wordpress_helpers', 'comments_open_pages'), self::$filter_priority, 2);
            break;

            case 'enhanced_classes':
              add_filter('body_class', array('piklist_wordpress_helpers', 'body_class'));
              add_filter('post_class', array('piklist_wordpress_helpers', 'post_class'));
              add_action('wp_footer', array('piklist_wordpress_helpers', 'no_js'));
            break;

            case 'xml_rpc':
              // Only triggers when xml_rpc is set to true
              add_filter('xmlrpc_enabled', '__return_false', self::$filter_priority);
            break;

            case 'disable_visual_editor':
              add_filter('user_can_richedit', '__return_false', self::$filter_priority);
            break;

            case 'maintenance_mode':
              add_action('get_header', array('piklist_wordpress_helpers', 'maintenance_mode'));          
              add_action('admin_notices', array('piklist_wordpress_helpers', 'maintenance_mode_warning'));
              add_action('admin_bar_menu', array('piklist_wordpress_helpers', 'maintenance_mode_warning_toolbar'), 999);
              add_filter('login_message', array('piklist_wordpress_helpers', 'maintenance_mode_message'));
            break;

            case 'private_site':
              add_action('wp', array('piklist_wordpress_helpers', 'redirect_to_login'));
              add_action('login_form', array('piklist_wordpress_helpers', 'private_login_form'));
            break;

            case 'redirect_to_home':
              add_action('login_redirect', array('piklist_wordpress_helpers', 'go_home'), 10, 3);
            break;

            case 'notice_admin':
              add_action('admin_notices', array('piklist_wordpress_helpers', 'notice_admin'));
            break;

            case 'notice_front':
              add_action('wp_enqueue_scripts', array('piklist_wordpress_helpers', 'helpers_css'));
              add_action('wp_head', array('piklist_wordpress_helpers', 'check_notice_front'),self::$filter_priority);
            break;

            case 'link_manager':
              add_filter( 'pre_option_link_manager_enabled', '__return_true', self::$filter_priority);
            break;
          }
        }
        else if (!empty($value))
        {
          switch ($option)
          {
            case 'xml_rpc':
              // Only triggers when xml_rpc is set to false or some
              add_filter('xmlrpc_methods', array('piklist_wordpress_helpers', 'unset_xmlrpc_methods'), self::$filter_priority);
            break;

            case 'default_editor':
              add_filter('wp_default_editor', array('piklist_wordpress_helpers', 'wp_default_editor'), self::$filter_priority);
            break;

            case 'edit_posts_per_page':
              add_filter('edit_posts_per_page', array('piklist_wordpress_helpers', 'edit_posts_per_page'), self::$filter_priority);
            break;

            case 'excerpt_box_height':
              add_action('piklist_wordpress_helpers_admin_css', array('piklist_wordpress_helpers', 'excerpt_box_height'), self::$filter_priority);
            break;

            case 'remove_widgets_new':
              add_action('widgets_init', array('piklist_wordpress_helpers', 'remove_widgets'), 99);
            break;

            case 'remove_dashboard_widgets_new':
              add_action('wp_dashboard_setup', array('piklist_wordpress_helpers', 'remove_dashboard_widgets'), self::$filter_priority);
            break;

            case 'clean_header':

              $value = is_array($value) ? $value : array($value);
              
              foreach ($value as $tag)
              {
                self::remove_filter('wp_head', $tag);
              }

            break;
            
            case 'excerpt_length_type':
          
              switch ($value)
              {
                case 'words':
                  add_filter('excerpt_length', array('piklist_wordpress_helpers', 'excerpt_length'), self::$filter_priority);
                break;
          
                case 'characters':
                  add_filter('get_the_excerpt', array('piklist_wordpress_helpers', 'excerpt_length'), self::$filter_priority);
                break;
              }
            
            break;
          
            case 'profile_fields':
              add_filter('user_contactmethods', array('piklist_wordpress_helpers', 'unset_profile_fields'), self::$filter_priority, 1);
            break;
          
            case 'change_howdy':
              add_filter('admin_bar_menu', array('piklist_wordpress_helpers', 'change_howdy'), self::$filter_priority);
            break;

            case 'login_image':
              add_action('login_head', array('piklist_wordpress_helpers', 'login_css'), self::$filter_priority);
              add_filter('login_headerurl', array('piklist_wordpress_helpers', 'login_headerurl'), self::$filter_priority);
              add_filter('login_headertitle', array('piklist_wordpress_helpers', 'login_headertitle'), self::$filter_priority);

            break;

            case 'login_background':
              add_action('login_head', array('piklist_wordpress_helpers', 'login_background'), self::$filter_priority);
            break;

            case 'mail_from':
              add_filter('wp_mail_from', array('piklist_wordpress_helpers', 'mail_from'), self::$filter_priority);
            break;

            case 'mail_from_name':
              add_filter('wp_mail_from_name', array('piklist_wordpress_helpers', 'mail_from_name'), self::$filter_priority);
            break;
          
            case 'show_admin_bar_components':
              add_action('wp_before_admin_bar_render', array('piklist_wordpress_helpers', 'remove_admin_bar_components'), self::$filter_priority);
            break;

            case 'show_system_information':
              add_filter('piklist_admin_pages', array('piklist_wordpress_helpers', 'system_admin_page'));
            break;

            case 'delete_orphaned_meta':
              add_action('wp_scheduled_delete', array('piklist_wordpress_helpers', 'delete_orphaned_meta'));
            break;

            case 'add_to_help':
              add_action('contextual_help', array('piklist_wordpress_helpers', 'add_help_tab'), 10, 3);
            break;

            case 'screen_layout_columns_post':

              if ($value != 'default')
              {
                add_filter('get_user_option_screen_layout_post', array('piklist_wordpress_helpers', 'get_user_option_screen_layout_post'), self::$filter_priority);
                add_filter('get_user_option_screen_layout_page', array('piklist_wordpress_helpers', 'get_user_option_screen_layout_post'), self::$filter_priority);
                add_action('piklist_wordpress_helpers_admin_css', array('piklist_wordpress_helpers', 'screen_layout_prefs'), self::$filter_priority);
              }
              
            break;

            case 'disable_uprade_notifications':

              if (current_user_can('manage_options')) break;

               $value = is_array($value) ? $value : array($value);
              
                if (in_array('wordpress', $value))
                {
                  add_filter('pre_site_transient_update_core', '__return_null');
                }

                if (in_array('plugins', $value))
                {
                  add_filter('pre_site_transient_update_plugins', '__return_null');
                }

                if (in_array('themes', $value))
                {
                  add_filter('pre_site_transient_update_themes', '__return_null');
                }
            
            break;

            case 'search_post_types':

              add_filter('pre_get_posts', array('piklist_wordpress_helpers', 'set_search'));          
            
            break;

            case 'delay_feed':
                        
              if (!empty(self::$options['delay_feed']) && isset(self::$options['delay_feed'][0]['delay_feed_num']))
              {
                add_filter('posts_where', array('piklist_wordpress_helpers', 'delay_feed'), self::$filter_priority);
              }
            
            break;

            case 'require_featured_image':

              add_action('save_post', array('piklist_wordpress_helpers', 'require_featured_image'), self::$filter_priority);
              add_action('admin_notices', array('piklist_wordpress_helpers', 'notice_admin_require_featured_image'), self::$filter_priority);
            
            break;
          }
        }
      }
    }
    
    add_action('admin_footer', array('piklist_wordpress_helpers', 'admin_css'), self::$filter_priority);
  }

  public static function remove_filter($filter, $tag)
  {
    global $wp_filter;

    if ($arguments = piklist_wordpress_helpers::array_path($wp_filter, $tag, array('tag', 'priority', 'filter')))
    {
      extract($arguments);
    }
        
    if (isset($wp_filter[$filter]) && $filter == $tag)
    {
      foreach ($wp_filter[$filter] as $priority => $data)
      {
        remove_action($filter, $tag, $priority, $wp_filter[$filter][$priority][$tag]['accepted_args']);
      }
    }
    else
    {
      if(!empty($filter))
      {
        remove_action($filter, $tag, $priority, $wp_filter[$filter][$priority][$tag]['accepted_args']);
      }
      
    }
  }
  
  public static function title_format($format)
  {
    return '%s';
  }

  public static function remove_post_excerpt_box()
  {
    remove_post_type_support('post', 'excerpt');
  }

  public static function replace_post_excerpt_box($part_data, $folder)
  {
    if($folder == 'meta-boxes')
    {
     if(!empty($part_data['type']) && $part_data['type'] == 'piklist_wp_helpers_post')
     {
        $part_data['type'] = 'post';
     }
    }

    return $part_data;
  }

  public static function require_featured_image($post_id)
  {
    if(!isset($_GET['_wpnonce']))
    {
      $thumbnail_post_types = is_array(self::$options['require_featured_image']) ?  self::$options['require_featured_image'] : array(self::$options['require_featured_image']);

      if(post_type_supports(get_post_type($post_id), 'thumbnail') && in_array(get_post_type($post_id), $thumbnail_post_types) && !has_post_thumbnail($post_id))
      {
        remove_action('save_post', array('piklist_wordpress_helpers', 'require_featured_image'), self::$filter_priority); // Stops infinite loop

        wp_update_post(array(
          'ID' => $post_id
          ,'post_status' => 'draft'
        ));

        update_post_meta($post_id, self::$prefix . 'no_featured_image', true);

        add_action('save_post', array('piklist_wordpress_helpers', 'require_featured_image'), self::$filter_priority);
      }
      else
      {
        delete_post_meta($post_id,  self::$prefix . 'no_featured_image');
      }
    }
  }

  public static function notice_admin_require_featured_image()
  {
    global $post, $pagenow;

    if($pagenow == 'post.php')
    {
      $no_featured_image = get_post_meta($post->ID, self::$prefix . 'no_featured_image');

      if ($no_featured_image)
      {
        $post_type = get_post_type_object($post->post_type);
        self::admin_notice(sprintf(__('You must assign a Featured Image before Publishing. This %1$s has been saved and set to Draft.', 'wp-helpers'), $post_type->labels->singular_name),'error');
      }
    }
  }

  public static function show_additional_image_sizes($part_data, $folder)
  {
    if($folder == 'media')
    {
      if((isset($part_data['part']) && $part_data['part'] == 'image-sizes.php') || (isset($part_data['name']) && $part_data['name'] == 'Image Sizes'))
      {
        $part_data['capability'] = null;
        return $part_data;
      }
    }

    return $part_data;
  }

  public static function show_exif_data($part_data, $folder)
  {
    if($folder == 'media')
    {
      if((isset($part_data['part']) && $part_data['part'] == 'image-exif.php') || (isset($part_data['name']) && $part_data['name'] == 'Exif Data'))
      {
        $part_data['capability'] = null;
        return $part_data;
      }
    }

    return $part_data;
  }

  public static function excerpt_length($length)
  {
    if (is_numeric($length) && self::$options['excerpt_length_type'] == 'words')
    {
      return !empty(self::$options['excerpt_length']) ? self::$options['excerpt_length'] : $length;
    }
    else if (!is_numeric($length) && self::$options['excerpt_length_type'] == 'characters')
    {
      return !empty(self::$options['excerpt_length']) ? substr($length, 0, self::$options['excerpt_length']) : $length;
    }

    return $length;
  }

  public static function disable_autosave()
  {
    wp_dequeue_script('autosave');
  }

  public static function wp_default_editor($default)
  {
    return self::$options['default_editor']; 
  }

  public static function edit_posts_per_page($posts_per_page)
  {
    return self::$options['edit_posts_per_page'];
  }

  public static function excerpt_box_height()
  {
    echo '#excerpt { height:' . self::$options['excerpt_box_height'] . 'px; }' . PHP_EOL;
  }
  
  public static function get_dashboard_widgets()
  {
    global $typenow, $wp_meta_boxes;
  
    if (empty(self::$dashboard_widgets))
    {
      remove_action('wp_dashboard_setup', array('piklist_wordpress_helpers', 'remove_dashboard_widgets'), self::$filter_priority);
      
      if (!function_exists('wp_dashboard_setup'))
      {
        include_once(ABSPATH . '/wp-admin/includes/dashboard.php');
      }
    
      wp_dashboard_setup();

      $meta_boxes = current($wp_meta_boxes);

      foreach (array('normal', 'advanced', 'side') as $context)
      {
        foreach (array('high', 'core', 'default', 'low') as $priority)
        {
          if (isset($meta_boxes[$context][$priority]))
          {
            foreach ($meta_boxes[$context][$priority] as $meta_box => $data)
            {
              self::$dashboard_widgets[$meta_box]['title'] = $data['title'];
              self::$dashboard_widgets[$meta_box]['context'] = $context;
              self::$dashboard_widgets[$meta_box]['priority'] = $priority;
            }
          }
        }
      }
    }
    
    return self::$dashboard_widgets;
  }

  public static function get_dashboard_widget_list()
  {
    $dashboards_widgets = piklist_wordpress_helpers::get_dashboard_widgets();

    foreach ($dashboards_widgets as $dashboards_widget => $value)
    {
      $all_dashboards_widgets[$dashboards_widget] = $value['title'];
    }

    return $all_dashboards_widgets;
  }
  
  public static function remove_widgets()
  {
    if(!empty(self::$options['remove_widgets_new']['widgets']))
    {
      $widgets = self::$options['remove_widgets_new']['widgets'];
      $widgets = is_array($widgets) ? $widgets : array($widgets);

      // Backwards compatibility with Piklist < 0.9.9
      $widgets = is_array($widgets[0]) ? $widgets[0] : $widgets;

      foreach ($widgets as $widget)
      {
        unregister_widget($widget);          
      }
    }
  }

  public static function remove_dashboard_widgets()
  {
    $dashboard_widgets = self::get_dashboard_widgets();
    
    $widgets = self::$options['remove_dashboard_widgets_new']['dashboard_widgets'];

    if(!empty($widgets[0]))
    {
      $widgets = is_array($widgets) ? $widgets : array($widgets);
      
      if(empty($widgets[1]))
      {
        $widgets = $widgets[0]; // backwards compatibility before Piklist v.9.9.x
      }

      $widgets = is_array($widgets) ? $widgets : array($widgets);  // backwards compatibility before Piklist v.9.9.x

      foreach ($dashboard_widgets as $dashboard_widget => $value)
      {
        if(in_array($dashboard_widget, $widgets))
        {
          remove_meta_box($dashboard_widget, 'dashboard', $value['context']);
        }
        
      }
    }
  }


  public static function unset_xmlrpc_methods($methods)
  {
    $orig_methods = $methods;

    $options = self::$options['xml_rpc_methods']; 
    $options = is_array($options) ? $options : array($options);

    if(!empty($options[0]))
    {
      foreach ($options as $method)
      {
        unset($methods[$method]);
      }
    }
    else
    {
      $methods = $orig_methods;
    }

    return $methods;
  }

  public static function unset_profile_fields($contactmethods)
  {
    $value = self::$options['profile_fields'];
    $value = is_array($value) ? $value : array($value);

    foreach ($value as $tag) 
    {
      if (isset($contactmethods[$tag]))
      {
        unset($contactmethods[$tag]);
      }
    }

    return $contactmethods;
  }

  public static function featured_image($content)
  {
    global $post;
      
    if (has_post_thumbnail($post->ID))
    {
      $content = '<p class="entry-featured-image entry-featured-image-' . $post->ID . '">' . get_the_post_thumbnail($post->ID) . '</p>' . $content;
    }
      
    return $content;
  }

  // @credit http://botcrawl.com/how-to-change-or-remove-the-howdy-greeting-message-on-the-wordpress-user-menu-bar/
  public static function change_howdy($wp_admin_bar)
  {
    $my_account = $wp_admin_bar->get_node('my-account');
    
    $wp_admin_bar->add_node(array(
      'id' => 'my-account'
      ,'title' => str_replace('Howdy, ', self::$options['change_howdy'] . ' ', $my_account->title)
    ));
  }


  public static function login_css()
  {
    $image_id = is_array(self::$options['login_image']) ? self::$options['login_image'] : array(self::$options['login_image']);

    shuffle($image_id);

    $image = wp_get_attachment_image_src($image_id[0],'full');
    
    piklist('shared/login-css', array(
      'image' => $image
    ));
  }

  public static function login_headerurl()
  {
    return home_url();
  }

  public static function login_headertitle()
  {
    return get_bloginfo('name');
  }


  public static function login_background()
  {
    $color = self::$options['login_background'];
?>
    <!-- WP Helpers -->   
    <style type="text/css">
      html {
        background: none repeat scroll 0 0 <?php echo $color; ?>;
      }
      body.login {
        background: none repeat scroll 0 0 <?php echo $color; ?>;
      }
    </style>
<?php
  }

  public static function remove_admin_bar_components()
  {
    global $wp_admin_bar;
    
    $value = self::$options['show_admin_bar_components'];
    $value = is_array($value) ? $value : array($value);

    foreach ($value as $tag)
    {
      $wp_admin_bar->remove_node($tag);
    }
  }

  public static function add_help_tab($contextual_help, $screen_id, $screen)
  {
    $value = self::$options['add_to_help'];
    $value = is_array($value) ? $value : array($value);

    foreach ($value as $help)
    {
      $function_suffix = str_replace("-", "_", $help);

      $title = __('Screen Information', 'wp-helpers');

      $help_function = 'add_help_tab_' . $function_suffix;

      $help_content = self::$help_function();

      $screen->add_help_tab(array(
        'id' => 'wp-helpers-' . $help
        ,'title' => $title
        ,'content' => $help_content
      ));
  
      return $contextual_help;
    }
  }

  // @Credit: http://wp.tutsplus.com/articles/tips-articles/quick-tip-get-the-current-screens-hooks/
  public static function add_help_tab_screen_information()
  {
    global $hook_suffix, $current_screen;
 
    $variables = '<ul style="width:50%;float:left;"> <strong>' . __('Screen variables', 'wp-helpers') . '</strong>'
        . sprintf(__('%1$s Screen id %2$s', 'wp-helpers'),'<li><strong>', '</strong>' . $current_screen->id . '</li>')

        . sprintf(__('%1$s Screen base %2$s', 'wp-helpers'),'<li><strong>', '</strong>' . $current_screen->base . '</li>')
        . sprintf(__('%1$s Parent base %2$s', 'wp-helpers'),'<li><strong>', '</strong>' . $current_screen->parent_base . '</li>')
        . sprintf(__('%1$s Parent file %2$s', 'wp-helpers'),'<li><strong>', '</strong>' . $current_screen->parent_file . '</li>')
        . sprintf(__('%1$s Hook suffix %2$s', 'wp-helpers'),'<li><strong>', '</strong>' . $hook_suffix . '</li>')
        . '</ul>';
 
    $hooks = array(
        "load-$hook_suffix"
        ,"admin_print_styles-$hook_suffix"
        ,"admin_print_scripts-$hook_suffix"
        ,"admin_head-$hook_suffix"
        ,"admin_footer-$hook_suffix"
    );
 
    if (did_action('add_meta_boxes_' . $current_screen->id))
    {
      $hooks[] = 'add_meta_boxes_' . $current_screen->id;
    }
        
    if (did_action('add_meta_boxes'))
    {
      $hooks[] = 'add_meta_boxes';
    }
 
    $hooks = '<ul style="width:50%;float:left;"> <strong>' . __('Hooks', 'wp-helpers') .  '</strong> <li>' . implode('</li><li>', $hooks) . '</li></ul>';

    $help_content = $variables . $hooks;
 
    return $help_content;
  }

  public static function delete_orphaned_meta()
  {
    global $wpdb;
    
    $tables = array(
      array(
        'type' => 'post'
        ,'object' => $wpdb->posts
        ,'meta' => $wpdb->postmeta
      )
      ,array(
        'type' => 'user'
        ,'object' => $wpdb->users
        ,'meta' => $wpdb->usermeta
      )
      ,array(
        'type' => 'term'
        ,'object' => $wpdb->terms
        ,'meta' => $wpdb->termmeta
      )
    );

    foreach ($tables as $table)
    {
      $wpdb->query($wpdb->prepare("DELETE meta FROM %s meta LEFT JOIN %s object ON object.ID = %s WHERE object.ID IS NULL", $table['meta'], $table['object'], 'meta.' . $table['type'] . '_id'));
    }
  }

  public static function hide_option_use_smilies()
  {
    echo 'label[for="use_smilies"]{ display: none !important }' . PHP_EOL;
    echo 'label[for="use_smilies"] + br { display: none !important }' . PHP_EOL;
  }

  public static function hide_admin_bar_profile_option()
  {
    echo '.show-admin-bar { display: none; }' . PHP_EOL;
  }

  public static function screen_layout_prefs()
  { 
    echo '.screen-layout, .columns-prefs { display: none; }' . PHP_EOL;
  }

  public static function comments_open_pages( $open, $post_id )
  {
    if ('page' == get_post_type())
    {
      return false;
    }
    
    return $open;
  }

  public static function list_table_post_types()
  {
    $post_types = array(
      'posts' => 'post'
      ,'pages' => 'page'
      ,'media' => 'media'
    );

    foreach ($post_types as $post_type => $value)
    {
      add_action('manage_' . $post_type . '_custom_column', array('piklist_wordpress_helpers', 'edit_column_echo'), self::$filter_priority, 2);
      add_filter('manage_' . $post_type . '_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
      add_filter('manage_edit-' .  $value . '_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'), self::$filter_priority);
    }
    
    if ($custom_post_types = get_post_types(array('_builtin' => false)))
    {
      foreach ($custom_post_types  as $custom_post_type)
      {
        add_action('manage_' . $custom_post_type . '_custom_column', array('piklist_wordpress_helpers', 'edit_column_echo'), self::$filter_priority, 2);
        add_filter('manage_' . $custom_post_type . '_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
        add_filter('manage_edit-' .  $custom_post_type . '_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'), self::$filter_priority);
      }
    }
  }

  public static function list_table_multisite()
  {
    if (is_multisite())
    {
      add_action('manage_sites_custom_column', array('piklist_wordpress_helpers', 'edit_column_echo'), self::$filter_priority, 2);
      add_action('manage_blogs_custom_column', array('piklist_wordpress_helpers', 'edit_column_echo'), self::$filter_priority, 2);
      add_filter('wpmu_blogs_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
    }
  }

  public static function list_table_users()
  {
    add_action('manage_users_custom_column', array('piklist_wordpress_helpers', 'edit_column_return'), self::$filter_priority, 3);
    add_filter('manage_users_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
    add_filter('manage_users_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'));
  }

  public static function list_table_link_manager()
  {
    add_action('manage_link_custom_column', array('piklist_wordpress_helpers', 'edit_column_echo'), self::$filter_priority, 2);
    add_filter('manage_link-manager_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
    add_filter('manage_link-manager_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'));
  }

  public static function list_table_taxonomies()
  {
    $taxonomies_builtin = array(
      'category'
      , 'post_tag'
    );

    foreach ($taxonomies_builtin as $taxonomy_builtin)
    {
      add_action('manage_' . $taxonomy_builtin . '_custom_column', array('piklist_wordpress_helpers', 'edit_column_return'), self::$filter_priority, 3);
      add_filter('manage_edit-' . $taxonomy_builtin . '_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
      add_filter('manage_edit-' . $taxonomy_builtin . '_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'), self::$filter_priority);
    }
    
    if ($custom_taxonomies = get_taxonomies(array('_builtin' => false)))
    {
      foreach ($custom_taxonomies  as $custom_taxonomy)
      {
        add_action('manage_' . $custom_taxonomy . '_custom_column', array('piklist_wordpress_helpers', 'edit_column_return'), self::$filter_priority, 3);
        add_filter('manage_edit-' . $custom_taxonomy . '_columns', array('piklist_wordpress_helpers', 'edit_column_header'), self::$filter_priority, 2);
        add_filter('manage_edit-' . $custom_taxonomy . '_sortable_columns', array('piklist_wordpress_helpers', 'register_sortable_column'), self::$filter_priority);
      }
    }
  }

  public static function column_orderby_id($vars)
  {
    if (is_admin())
    {
      if (isset($vars['orderby']) && 'piklist_id' == $vars['orderby'])
      {
        $vars = array_merge($vars, array(
                            'meta_key' => 'piklist_id',
                            'orderby' => 'meta_value_num'
                            ));
      }

    }
  return $vars;
  }

  public static function register_sortable_column($columns)
  {
    $columns['piklist_id'] =  __('ID', 'wp-helpers');
   
    return $columns;
  }


  public static function edit_column_header($columns)
  {
    global $post;

    if(in_array('show_ids', Piklist_WordPress_Helpers::$list_table_columns))
    {
      $column_id = array('piklist_id' =>  __('ID', 'wp-helpers'));

      $columns = array_slice( $columns, 0, 1, true ) + $column_id + array_slice( $columns, 1, NULL, true );
    }

    if(post_type_supports(get_post_type($post), 'thumbnail'))
    {
      if(in_array('show_featured_image', Piklist_WordPress_Helpers::$list_table_columns))
      {
        $columns['piklist_featured_image'] =  __('Featured Image', 'wp-helpers');
      }
    }

    return $columns;
  }

  public static function edit_column_echo($column, $value)
  {
    switch ($column)
    {
      case 'piklist_id' :

        echo $value;

        break;

      case 'piklist_featured_image' :

        $image_attributes = wp_get_attachment_image_src(get_post_thumbnail_id(), 'large');
        $title = get_the_title(get_post_thumbnail_id());

        piklist::render('shared/list-table-image', array(
          'image_attributes' => $image_attributes
          ,'title' => $title
        ));

      break;
    }
  }

  public static function edit_column_return($value, $column, $id)
  {
    switch ($column)
    {
      case 'piklist_id':
        
        $value = (int) $id;
      
      break;

      default:

        $value = '';

      break;
    }
    
    return $value;
  }

  public static function column_id_width()
  {
    echo '.column-piklist_id { text-align: left; width: 5em; }' . PHP_EOL;
    echo '.column-piklist_featured_image { text-align: left; width: 133px; }' . PHP_EOL;
  }

  public static function get_user_option_screen_layout_post()
  {
    return self::$options['screen_layout_columns_post'];
  }

  public static function theme_switcher()
  {
    global $submenu;
    
    unset($submenu['themes.php'][5]);
    unset($submenu['themes.php'][15]);
  }

  // @credit http://blogwaffe.com/2006/10/04/wordpress-plugin-no-self-pings/
  public static function disable_self_ping(&$links)
  {
    foreach ($links as $link => $link_data)
    {
      if (0 === strpos($link_data, get_option('home')))
      {
        unset($links[$link]);
      }
    }
  }

  public static function all_options_menu()
  {
    if (current_user_can('manage_options'))
    {
      global $submenu;
      
      $all_options_menu = array('All','manage_options','options.php');
      array_unshift($submenu['options-general.php'], $all_options_menu);
    }
  }

  // @credit https://wordpress.org/plugins/disable-emojis/
  public static function disable_emojis_tinymce($plugins)
  {
    if (is_array($plugins))
    {
      return array_diff($plugins, array('wpemoji'));
    }
    else
    {
      return array();
    }
  }

  public static function mail_from($old)
  {
    return self::$options['mail_from'];
  }

  public static function mail_from_name($old)
  {
    return self::$options['mail_from_name'];
  }

  // @credit http://skyje.com/wordpress-code-snippets/
  public static function maintenance_mode()
  {
    if (!current_user_can('manage_options') || !is_user_logged_in())
    {
      if (!empty(self::$options['private_site']))
      {
        self::redirect_to_login();
      }
      else
      {
        wp_die(self::$options['maintenance_mode_message'], self::$options['maintenance_mode_message'], array('response' => '503'));
      }           
    }
  }

  public static function private_login_form()
  {
    printf(__('%1$sYou must be logged in to view this Website%2$s', 'wp-helpers'), '<p>', '</p><br/>');
  }

  public static function maintenance_mode_warning()
  {
    self::admin_notice(sprintf(__('This site is in Maintenance Mode. %1$sDeactivate when finished%2$s', 'wp-helpers'), '<a href="' . admin_url() . 'tools.php?page=piklist_wp_helpers&tab=users#piklist_wordpress_helpers_maintenance_mode_0">', '</a>'), false);
  }

  public static function maintenance_mode_warning_toolbar($wp_admin_bar)
  {
    $args = array(
      'id' => 'wp-helpers-maintenance_mode_warning'
      ,'title' => __('This site is in Maintenance Mode.', 'wp-helpers')
      ,'href' => admin_url() . 'tools.php?page=piklist_wp_helpers&tab=users#piklist_wordpress_helpers_maintenance_mode_0'
      ,'meta'  => array( 'class' => 'my-toolbar-page' )
    );

    $wp_admin_bar->add_node($args);
  }

  public static function maintenance_mode_message()
  {
    self::login_notice(self::$options['maintenance_mode_message']);
  }

  public static function redirect_to_login()
  {
    if(!is_user_logged_in())
    {
      wp_redirect(home_url() . '/wp-login.php', 302);
      exit;
    }
  }

  public static function notice_admin()
  {
    self::admin_notice(self::$options['admin_message'], false); 
  }

  public static function check_notice_front()
  {
    $user_type = self::$options['notice_user_type'];
    $browser_type = self::$options['notice_browser_type'];
    $message = self::$options['logged_in_front_message'];
    $type = self::$options['notice_color'];

    if ($user_type == 'logged_in')
    {
      if (!is_user_logged_in())
      {
        return;
      } 
    }

    if ($browser_type != 'all')
    {
      global ${$browser_type};
      {
        if (!${$browser_type})
        {
          return;
        }
      } 
    }
    self::front_notice($message, $type);
  }

  // @credit http://themehybrid.com/
  public static function body_class($classes)
  {
    global $post, $wp_roles, $blog_id;

    $extended_classes = array();
    
    $extended_classes[] = 'no-js';


    if (is_singular())
    {
      $extended_classes = array_merge($extended_classes, self::taxonomy_class($classes));
      $extended_classes = array_merge($extended_classes, self::date_class($classes));

      if (has_post_thumbnail())
      {
        $extended_classes[] = 'has-post-thumbnail';
      }

      if (is_multi_author())
      {
        $extended_classes = array_merge($extended_classes, self::author_class($classes));
      }
    }
    
    if (is_archive())
    {
      if (is_year())
      {
        $extended_classes[] = 'year';
      }

      if (is_month())
      {
        $extended_classes[] = 'month';
      }

      if (is_day())
      {
        $extended_classes[] = 'day';
      }
    }

    if (is_user_logged_in())
    {
      $current_user = wp_get_current_user();
      $roles = $current_user->roles;
      $role = array_shift($roles);
      $extended_classes[] = ($wp_roles->role_names[$role]) ? translate_user_role($wp_roles->role_names[$role]) : false;
    }

    if (is_multisite())
    {       
       $sitename = str_replace(' ', '-', strtolower(get_bloginfo('name')));
       $extended_classes[] = 'multisite';
       $extended_classes[] = 'site-' . $blog_id;
       $extended_classes[] = 'site-' . $sitename;
    }

    if (function_exists('bp_get_the_body_class'))
    {
      $extended_classes = array_merge($extended_classes, self::buddypress_class($classes));
    }

    $extended_classes = array_merge($extended_classes, self::browser_class($classes));

    $classes = array_merge((array) $classes, (array) $extended_classes);

    $classes = array_map('strtolower', $classes);
    $classes = sanitize_html_class($classes);
    $classes = array_unique($classes);

    return $classes;
  }


  public static function post_class($classes = '', $post_id = null)
  {
    global $post, $wp_query;

    $extended_classes = array();

    if(!is_singular())
    {
      $extended_classes[] = (($wp_query->current_post + 1) % 2) ? 'odd' : 'even';
    }

    $extended_classes = array_merge($extended_classes, self::date_class($classes));
    $extended_classes = array_merge($extended_classes, self::taxonomy_class($classes));

    if (has_post_thumbnail())
    {
      $extended_classes[] = 'has-post-thumbnail';
    }

    if (is_multi_author())
    {
      $extended_classes = array_merge($extended_classes, self::author_class($classes));
    }

    if (post_type_supports($post->post_type, 'excerpt') && has_excerpt())
    {
      $extended_classes[] = 'has-excerpt';
    }

    $classes = array_merge((array) $classes, (array) $extended_classes);
    $classes = array_map('strtolower', $classes);
    $classes = sanitize_html_class($classes);
    $classes = array_unique($classes);

    return $classes;
  }

  // @credit: http://wordpress.org/extend/plugins/genesis-js-no-js/
  // @credit: http://core.svn.wordpress.org/trunk/wp-admin/admin-header.php
  public static function no_js()
  { ?>
    <script type="text/javascript">
      document.body.className = document.body.className.replace('no-js','js');
    </script>
  <?php
  }

  // @credit http://www.mattvarone.com/wordpress/browser-body-classes-function/
  public static function browser_class()
  {
    global $is_lynx, $is_gecko, $is_IE, $is_opera, $is_NS4, $is_safari, $is_chrome, $is_iphone;

    $classes = array();

    if ($is_lynx)
    {
      $classes[] = 'lynx';
    }
    elseif ($is_gecko)
    {
      $classes[] = 'gecko';
    }
    elseif ($is_opera)
    {
      $classes[] = 'opera';
    }
    elseif ($is_NS4)
    {
      $classes[] = 'ns4';
    }
    elseif ($is_safari)
    {
      $classes[] = 'safari';
    }
    elseif ($is_chrome)
    {
      $classes[] = 'chrome';
    }
    elseif ($is_IE)
    {
      $classes[] = 'ie';
      if (preg_match('/MSIE ([0-9]+)([a-zA-Z0-9.]+)/', $_SERVER['HTTP_USER_AGENT'], $browser_version))
      {
        $classes[] = 'ie-' . $browser_version[1];
      }
    }
    else
    {
      $classes[] = 'browser-unknown';
    }

    if ($is_iphone)
    {
      $classes[] = 'iphone';
    }
    
    return $classes;
  }

  public static function date_class()
  {
    $classes = array();
    $classes[] = get_the_date('F');
    $classes[] = 'day-' . get_the_date('j');
    $classes[] = get_the_date('Y');
    $classes[] = 'time-' . get_the_date('a');

    return $classes;
  }

  public static function author_class()
  {
    global $post;

    $classes = array();

    $author_id = $post->post_author;
    $classes[] = 'author-' . get_the_author_meta('user_nicename', $author_id);

    return $classes;
  }

  public static function taxonomy_class()
  {
    global $post, $post_id;

    $classes = array();
        
    $post = get_post($post->ID);
    $post_type = $post->post_type;

    $taxonomies = get_object_taxonomies($post_type);
    foreach ($taxonomies as $taxonomy)
    {
      $terms = get_the_terms($post->ID, $taxonomy);
      if (!empty($terms))
      {
        $output = array();
        foreach ($terms as $term)
        {
          $classes[] .= $term->taxonomy . '-' . $term->name ;
          if (is_taxonomy_hierarchical($term->taxonomy))
          {
            $counter = 1;
            while (!is_wp_error(get_term($term->parent, $term->taxonomy)))
            {
              $term_parent = get_term($term->parent, $term->taxonomy);
              $classes[] .= $term->taxonomy . '-' . 'level-' . $counter . '-' . $term_parent->name;
              $classes[] .= $term->taxonomy . '-hierarchical-' . $term_parent->name;
              $term = $term_parent;
              $counter++;
            }
          }
        }              
      }
    }

    return $classes;
  }

  public static function buddypress_class()
  {
    $classes = array();
    $classes = bp_get_the_body_class();

    return $classes;
  }

  public static function set_search($query)
  {
    if (is_admin())
    {
      return;
    }
    elseif ($query->is_search)
    {
      $value = self::$options['search_post_types'];
      $value = is_array($value) ? $value : array($value);
      $query->set('post_type', $value);
    }
    return $query;
  }

  public static function disable_feed()
  {
    global $wp_query;

    if($wp_query->is_feed == 1)
    {
      self::wp_die();
    }

    self::remove_filter('wp_head', 'feed_links');
    self::remove_filter('wp_head', 'feed_links_extra');
  }

  public static function delay_feed($where)
  {
    global $wpdb;

    if (is_feed())
    {
      $delay_feed_num = self::$options['delay_feed'][0]['delay_feed_num'];
      $delay_feed_time = self::$options['delay_feed'][0]['delay_feed_time'];

      $now = gmdate('Y-m-d H:i:s');

      $where .= " AND TIMESTAMPDIFF($delay_feed_time, $wpdb->posts.post_date_gmt, '$now') > $delay_feed_num ";
    }
    return $where;
  }

  public static function admin_notice($message, $error = false)
  {
    piklist('shared/admin-notice', array(
      'type' => $error ? 'error' : 'updated'
      ,'notices' => $message
    ));
  }

  public static function login_notice($message, $error = false)
  {
    piklist('shared/admin-login-message', array(
      'type' => $error ? 'error' : 'updated'
      ,'notices' => $message
    ));
  }

  public static function front_notice($message, $type = 'info')
  {
    piklist('shared/front-notice', array(
      'type' => $type
      ,'message' => $message
    ));
  }

  public static function login_message($message)
  {
    $login_message = '<p class="message">' . $message . '</p><br />';
    echo $login_message;
  }

  public static function website_message($message)
  {
    $login_message = '<p class="message">' . $message . '</p><br />';
    echo $login_message;
  }

  public static function go_home($redirect_to, $request, $user)
  {
    return home_url();
  } 

  public static function helpers_css()
  {
    wp_register_style('wp-helpers', plugins_url('/parts/css/wp-helpers.css', __FILE__) );
    wp_enqueue_style('wp-helpers');
  }

  public static function wp_die()
  {
    wp_die(__('Disabled'), 'wp-helpers');
  }

  public static function admin_css()
  { 
?>
    <!-- Piklist WP-Helpers -->   
    <style type="text/css">  
      <?php do_action('piklist_wordpress_helpers_admin_css'); ?>
    </style>
<?php
  }

  public static function deactivate()
  {
    update_option('avatar_default', 'mystery');
  }
  
  public static function clean_phpinfo()
  {
    ob_start();
    
      phpinfo();
    
      $phpinfo = ob_get_contents();
    
    ob_end_clean();

    $phpinfo = preg_replace( '%^.*<body>(.*)</body>.*$%ms','$1',$phpinfo);
    $phpinfo = str_ireplace('width="600"','class="wp-list-table widefat"', $phpinfo);

    echo $phpinfo;
  }


  public static function piklist_site_inventory()
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
      $table_prefix_status = sprintf(__('%1$sERRO: Too long%2$s', 'wp-helpers'), ' (', ')');
    }
    else
    {
      $table_prefix_status = sprintf(__('%1$sAcceptable%2$s', 'wp-helpers'), ' (', ')');
    };

    $wp_debug = defined('WP_DEBUG') ? WP_DEBUG ? __('Enabled', 'wp-helpers') . "\n" : __('Disabled', 'wp-helpers') . "\n" : __('Not set', 'wp-helpers');

    $php_safe_mode = ini_get('safe_mode') ? __('Yes', 'wp-helpers') : __('No', 'wp-helpers');
    $allow_url_fopen = ini_get('allow_url_fopen') ? __('Yes', 'wp-helpers') : __('No', 'wp-helpers'); 

    $plugins_active = array();
    $plugins = get_plugins();
    $active_plugins = get_option('active_plugins', array());

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

  public static function array_path($array, $find, $map = null)
  {
    $path = array();

    if ($find && array_key_exists($find, $array))
    {
      return $map ? array("{$map[count($path)]}" => $find) : array($find);
    }
    else
    {
      foreach ($array as $key => $data)
      {
        if (is_array($data))
        {
          if ($path = self::array_path($data, $find, $map))
          {
            $path[($map ? $map[count($path)] : null)] = $key;

            return $path;
          }
        }
      }
    }

    return null;
  }
}