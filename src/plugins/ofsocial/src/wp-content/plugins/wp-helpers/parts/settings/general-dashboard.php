<?php
/*
Title: Dashboard Widgets
Setting: piklist_wp_helpers
Order: 110
Flow: WP Helpers Settings Flow
*/


  piklist('field', array(
    'type' => 'group'
    ,'field' => 'remove_dashboard_widgets_new'
    ,'label' => 'Remove Dashboard Widgets'
    ,'fields' => array(
      array(
        'type' => 'checkbox'
        ,'field' => 'dashboard_widgets'
        ,'choices' => piklist_wordpress_helpers::get_dashboard_widget_list()
        ,'columns' => 4
      )
    )
  ));