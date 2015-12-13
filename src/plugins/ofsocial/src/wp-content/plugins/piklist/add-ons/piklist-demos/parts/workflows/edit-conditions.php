<?php
/*
Title: Conditions
Order: 80
Flow: Edit Demo
*/

  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_conditions'
    ,'piklist_meta_field_conditions_party_invite'
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