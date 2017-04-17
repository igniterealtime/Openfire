<?php
/*
Title: Featured Image
Setting: piklist_wp_helpers
Tab: Writing
Order: 220
Tab Order: 20
Flow: WP Helpers Settings Flow
*/

$thumbnail_post_types = array();

$registered_post_types = piklist(
   get_post_types(
     array()
     ,'objects'
   )
   ,array(
     'name'
     ,'label'
   )
  );

foreach ($registered_post_types as $post_type => $value)
{
  if(post_type_supports($post_type, 'thumbnail'))
  {
    $thumbnail_post_types[$post_type] = $value;
  }
}

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'require_featured_image'
    ,'label' => 'Require Featured Image'
    ,'description' => 'Require Featured Image to Publish.'
    ,'choices' => $thumbnail_post_types
  ));