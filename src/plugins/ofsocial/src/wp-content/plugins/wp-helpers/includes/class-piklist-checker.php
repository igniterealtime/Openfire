<?php
/*
 * Piklist Checker
 * Version: 0.6.2
 *
 * Verifies that Piklist is installed and activated.
 * If not:
 ** Your plugin will be deactivated and user will be notifed.
 ** Themes will show a notice.
 *
 * Developers:
 ** Instructions on how to use this file in your plugin can be found here:
 ** http://piklist.com/user-guide/docs/piklist-checker/
 *
 * Most recent version of this file can be found here:
 * http://s-plugins.wordpress.org/piklist/assets/class-piklist-checker.php
 */

if (!defined('ABSPATH')) exit; // Exit if accessed directly

if (!class_exists('Piklist_Checker'))
{
  class Piklist_Checker
  {
    private static $plugins = array();

    private static $type = null;

    private static $theme = false;

    private static $plugin_list = '';

    public static function admin_notices()
    {
      add_action('network_admin_notices', array('piklist_checker', 'show_message'));
      add_action('admin_notices', array('piklist_checker', 'show_message'));
    }

    public static function check($this_plugin, $checking = 'plugin')
    {
      global $pagenow;

      if ($pagenow == 'update.php' || $pagenow == 'update-core.php' || class_exists('Piklist'))
      {
        return true;
      }

      if($checking == 'plugin')
      {
        require_once(ABSPATH . '/wp-admin/includes/plugin.php');

        if (is_multisite())
        {
          if (is_plugin_active_for_network(plugin_basename($this_plugin)))
          {
            piklist_checker::deactivate_plugins($this_plugin, 'network');
          }
          else
          {
            piklist_checker::deactivate_plugins($this_plugin, 'single-network');
          }
        }
        else
        {
          piklist_checker::deactivate_plugins($this_plugin, 'single');
        }
      }
      else
      {
        piklist_checker::$theme = true;

        if (!empty(self::$type))
        {
          self::$type = 'single';
        }
      }
    }

    public static function deactivate_plugins($this_plugin, $type)
    {
      self::$type = $type;
      
      if (self::$type == 'single' || self::$type == 'single-network')
      {
        $plugins = get_option('active_plugins', array()); 
      }
      else
      {
        $plugins = array_flip(get_site_option('active_sitewide_plugins', array()));
      }

      if (!empty(self::$type))
      {
        self::$type = $type;
      }

      $this_plugin = str_replace('\\', '/',$this_plugin);

      foreach ($plugins as $plugin)
      {
        if (strstr($this_plugin, $plugin))
        {
          array_push(piklist_checker::$plugins, $this_plugin);
          
          deactivate_plugins($plugin);
          
          return false;
        }
      }
    }

    public static function message()
    {
      $piklist_file = 'piklist/piklist.php';
      $piklist_installed = false;

      if (array_key_exists($piklist_file, get_plugins()))
      {
        $piklist_installed = true;
      }

      $url_proper_dashboard = (self::$type == 'network' ? network_admin_url() : admin_url()) . 'plugins.php'; ?>

      <?php ob_start(); ?>

        <?php if(piklist_checker::$theme == true) : ?>

          <p><strong><?php _e('Your theme requires PIKLIST to work properly.', 'piklist'); ?></strong></p>

        <?php endif; ?>

        <?php if(!empty(piklist_checker::$plugins)) : ?>

          <p>

            <strong>

              <?php _e('The following plugin(s) require PIKLIST, and have been deactivated:', 'piklist'); ?>
            
              <?php foreach(piklist_checker::$plugins as $plugin): $data = get_plugin_data($plugin); ?>
              
                  <?php piklist_checker::$plugin_list = piklist_checker::$plugin_list . $data['Title'] . ', '; ?>
               
              <?php endforeach; ?>

              <?php echo rtrim(piklist_checker::$plugin_list, ", "); ?>
  
            </strong>

          </p>

        <?php endif; ?>
     
        <h4><?php _e('You can:', 'piklist'); ?></h4>

        <ol>

          <?php

            if ($piklist_installed):

              global $s;
              $context = 'all';

              if (self::$type == 'single' || self::$type == 'single-network'):

                $activate = '<a href="' . wp_nonce_url(admin_url() . 'plugins.php?action=activate&amp;plugin=' . $piklist_file . '&amp;plugin_status=' . $context . '&amp;s=' . $s, 'activate-plugin_' . $piklist_file) . '" title="' . esc_attr__('Activate Piklist for this site', 'piklist') . '" class="edit">' . __('Activate Piklist for this site', 'piklist') . '</a>';
                echo '<li>' . $activate . '</li>';

              endif;
              
              if ((self::$type == 'network' || self::$type == 'single-network') && is_multisite() && is_super_admin()):

                $activate = '<a href="' . wp_nonce_url(network_admin_url() . 'plugins.php?action=activate&amp;plugin=' . $piklist_file . '&amp;plugin_status=' . $context . '&amp;s=' . $s, 'activate-plugin_' . $piklist_file) . '" title="' . esc_attr__('Network Activate Piklist for all sites.', 'piklist') . '" class="edit">' . __('Network Activate Piklist for all sites.', 'piklist') . '</a>';
                echo '<li>' . $activate . '</li>';

              endif;

            else:

              $install = '<a href="' . wp_nonce_url(network_admin_url() . 'update.php?action=install-plugin&amp;plugin=piklist', 'install-plugin_' . 'piklist') . '"title="' . esc_attr__('Install Piklist', 'piklist') . '" class="edit">' . __('Install Piklist', 'piklist') . '</a>';
              echo '<li>' . $install . '</li>';

            endif;

            if(!empty(piklist_checker::$plugins)) :

              printf(__('%1$s %2$sDismiss this message.', 'piklist'),'<li>', '<a href="' . $url_proper_dashboard . '">','</a>', '</li>');

            else :

              printf(__('%1$s %2$sChange your theme.', 'piklist'),'<li>', '<a href="' . admin_url() . 'themes.php' . '">','</a>', '</li>');
              

            endif;
            
          ?>

        </ol>


        <?php

          $message = ob_get_contents();

          ob_end_clean();
    
          return $message;
    }
    
    public static function show_message($message, $errormsg = true)
    {
      if (!empty(piklist_checker::$plugins) || piklist_checker::$theme == true) : ?>

        <div class="error">

            <p>
              <?php echo piklist_checker::message(); ?>
            </p>

        </div>


      <?php endif;
    }
  }
  
  piklist_checker::admin_notices();

}

/*
 * Changelog
 *
 *
 
  = 0.6.2 =
 * Windows Server support

 = 0.6.1 =
 * Code refactor

 = 0.6.0 =
 * Faster checks.
 * Works with themes that support Piklist.

 = 0.5.1 =
 * Check if class Piklist exists instead of is_plugin_active.

 = 0.5.0 =
 * Now runs in the WordPress admin for a better user experience.

 = 0.4.2 =
 * Check if is_plugin_active_for_network function exists
 * Updated to Text Domain: Piklist

 = 0.4.1 =
 * Fixed Unterminated Comment Notice

 = 0.4.0 =
 * Multisite support

 = 0.3.0 =
 * Bugfix: deactivated plugin after Piklist was upgraded.

 = 0.2.0 =
 * Better messages when plugin is uninstalled

 = 0.1.0 =
 * Initial release

 */