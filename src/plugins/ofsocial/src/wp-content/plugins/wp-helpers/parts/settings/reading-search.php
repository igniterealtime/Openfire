<?php
/*
Title: Search
Setting: piklist_wp_helpers
Tab: Reading
Order: 320
Flow: WP Helpers Settings Flow
*/

$valid_post_types = piklist(
   get_post_types(
     array(
      'exclude_from_search' => false
     )
     ,'objects'
   )
   ,array(
     'name'
     ,'label'
   )
  );


piklist('field', array(
   'type' => 'checkbox'
   ,'field' => 'search_post_types'
   ,'label' => 'Include in Search'
   ,'description' => 'Uncheck all to use defaults'
   ,'list' => count($valid_post_types) < 5 ? true : false // If 5 or more post types than show in columns
   ,'attributes' => array(
     'class' => 'text'
    ,'columns' => 3
   )
   ,'choices' => $valid_post_types
));