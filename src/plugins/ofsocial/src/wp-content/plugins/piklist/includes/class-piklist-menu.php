<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Menu
{
  public static function _construct()
  {    
    add_action('init', array('piklist_menu', 'init'));

    add_filter('wp_nav_menu', array('piklist_menu', 'wp_nav_menu_updates'));
  }
 
  public static function init()
  {    
    self::register_nav_menus();
  }
  
  public static function register_nav_menus()
  {
    $menus = apply_filters('piklist_menus', array());
    
    foreach ($menus as $menu)
    {
      register_nav_menus($menu);
    }
  }
  
  public static function wp_nav_menu_updates($output) 
  {
    // NOTE: Replace with custom walker and use descriptions instead of titles
    $permalink_structure = get_option('permalink_structure');
    
    if (!empty($permalink_structure))
    {
      $parse = preg_match_all('/<li id="menu-item-(\d+)/', $output, $matches);
      
      for ($i = 0; $i < count($matches[1]); $i++)
      {
        $menu_id = $matches[1][$i];
        
        $id = get_post_meta($menu_id, '_menu_item_object_id', true);
  
        $class = '';
        if ($i == 0)
        {
          $class = 'first-menu-item';
        }
        elseif ($i + 1 == count($matches[1]))
        {
          $class = 'last-menu-item';
        }
  
        $output = preg_replace('/menu-item-' . $menu_id . '">/', 'menu-item-' . $menu_id . ' menu-item-' . basename(get_permalink($id)) . ' ' . $class . '">', $output, 1);
      }
    }

    if (strstr($output, 'main-menu'))
    {
      preg_match_all(
        '#<a\s
          (?:(?= [^>]* href="(?P<href> [^"]*) ")|)
          (?:(?= [^>]* title="(?P<title> [^"]*) ")|)
          [^>]*>
          (?P<text>[^<]*)
          </a>
        #xi'
        ,$output
        ,$links
        ,PREG_SET_ORDER
      );

      foreach ($links as $link)
      {
        if (!empty($link['title']))
        {
          $output = str_replace('>' . $link['text'] . '<', '>' . $link['text'] . '<span>' . $link['title'] . '</span><', $output);
        }
      }
    }

    return $output;
  }
}