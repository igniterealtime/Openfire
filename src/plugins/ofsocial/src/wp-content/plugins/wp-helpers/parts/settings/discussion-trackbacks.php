<?php
/*
Title: Trackbacks / Pingbacks
Setting: piklist_wp_helpers
Tab: Discussion
Order: 410
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'disable_self_ping'
    ,'label' => 'Self Pings'
    ,'choices' => array(
      'true' => 'Disable'
    )
  ));