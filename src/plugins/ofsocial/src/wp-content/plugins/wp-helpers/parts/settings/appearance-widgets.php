<?php
/*
Title: Widgets
Setting: piklist_wp_helpers
Tab: Appearance
Order: 510
Flow: WP Helpers Settings Flow
*/

if(empty($GLOBALS['wp_registered_sidebars'])) :

  piklist('field', array(
    'type' => 'html'
    ,'value' => __('This theme does not support widgets', 'wp-helpers')
    ,'label' => __('Widget Support', 'wp-helpers')
  ));

else :

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'remove_widgets_new'
    ,'label' => 'Remove Widgets'
    ,'fields' => array(
      array(
        'type' => 'checkbox'
        ,'field' => 'widgets'
        ,'choices' => array(
          'WP_Widget_Archives' => 'Archives'
          ,'WP_Widget_Calendar' => 'Calendar'
          ,'WP_Widget_Categories' => 'Categories'
          ,'WP_Widget_Links' => 'Links'
          ,'WP_Nav_Menu_Widget' => 'Menu'
          ,'WP_Widget_Meta' => 'Meta'
          ,'WP_Widget_Pages' => 'Page'
          ,'WP_Widget_Recent_Comments' => 'Recent Comments'
          ,'WP_Widget_Recent_Posts' => 'Recent Posts'
          ,'WP_Widget_RSS' => 'RSS'
          ,'WP_Widget_Search' => 'Search'
          ,'WP_Widget_Tag_Cloud' => 'Tag Cloud'
          ,'WP_Widget_Text' => 'Text'
        )
        ,'columns' => 4
      )
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'shortcodes_in_widgets'
    ,'label' => 'Allow Shortcodes in Widgets'
    ,'choices' => array(
      'true' => 'Run any shortcode in a widget'
    )
  ));

endif;