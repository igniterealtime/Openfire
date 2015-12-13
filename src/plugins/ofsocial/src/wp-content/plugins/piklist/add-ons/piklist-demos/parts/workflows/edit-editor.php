<?php
/*
Title: Editor
Order: 10
Flow: Edit Demo
Default: true
*/
?>

<h3 class="demo-highlight">
  <?php _e('The WordPress post editor can be placed anywhere you like, since Piklist treats it like any other field. You can also use multiple editors per page.','piklist-demo');?>
</h3>

<?php
  
  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_editor'
    ,'piklist_meta_field_editor_draggable'
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