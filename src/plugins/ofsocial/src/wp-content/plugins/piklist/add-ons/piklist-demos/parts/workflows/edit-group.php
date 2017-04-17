<?php
/*
Title: Groups
Order: 30
Flow: Edit Demo
*/
?>

<h3 class="demo-highlight">
  <?php _e('Grouping fields saves all their data in one field array for convenience. If you need to search the individual field data then don\'t group the fields. Don\'t worry you can acheive the same layout either way.','piklist-demo');?>
</h3>

<?php
  
  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_group'
    ,'piklist_meta_field_group_test'
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