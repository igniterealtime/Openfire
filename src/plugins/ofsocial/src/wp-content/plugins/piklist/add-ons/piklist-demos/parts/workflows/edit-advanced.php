<?php
/*
Title: Advanced
Order: 60
Flow: Edit Demo
*/
  
  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_date_time'
    ,'piklist_meta_field_colorpicker'
    ,'piklist_meta_field_conditions'
    ,'piklist_meta_field_add_more'
    ,'piklist_meta_field_taxonomies'
    ,'piklist_meta_field_featured_image'
    ,'piklist_meta_field_relate'
    ,'piklist_meta_field_comments'
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));
  
?>