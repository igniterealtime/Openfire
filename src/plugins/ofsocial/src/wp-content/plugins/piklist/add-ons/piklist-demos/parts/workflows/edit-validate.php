<?php
/*
Title: Validation
Order: 70
Flow: Edit Demo
*/

  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_validate'
    ,'piklist_meta_field_taxonomies'
    ,'piklist_test_typediv'
    ,'piklist_meta_field_featured_image'
    ,'piklist_meta_field_relate'
    ,'piklist_meta_field_comments'
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Workflow Tab'
  ));

?>