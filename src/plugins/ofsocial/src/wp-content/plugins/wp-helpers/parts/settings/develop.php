<?php
/*
Title: Developer Tools
Setting: piklist_wp_helpers
Tab: Develop
Order: 10
Tab Order: 999
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_system_information'
    ,'label' => 'Show System Information'
    ,'choices' => array(
      'screen-information' => 'Under Tools menu'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'add_to_help'
    ,'label' => 'Show Screen Information'
    ,'choices' => array(
      'screen-information' => 'In HELP Tabs'
    )
  ));