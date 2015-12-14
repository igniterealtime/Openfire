<?php
/*
Title: Comments
Setting: piklist_wp_helpers
Tab: Discussion
Order: 400
Tab Order: 50
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'comments_open_pages'
    ,'label' => 'Pages'
    ,'choices' => array(
      'true' => 'Do not allow comments on Pages'
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'make_clickable'
    ,'label' => 'Automatic linking'
    ,'choices' => array(
      'true' => 'Turn off automatic linking of urls in comments'
    )
  ));