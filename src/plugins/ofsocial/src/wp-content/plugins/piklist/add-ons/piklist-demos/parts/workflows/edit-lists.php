<?php
/*
Title: Lists
Order: 25
Flow: Edit Demo
*/
?>

<h3 class="demo-highlight">
  <?php _e('Select, Radio and Checkbox fields have multiple configuration options. You can display them stacked, single line, as grouped lists and even use nested fields. ','piklist-demo');?>
</h3>

<?php
  piklist('include_meta_boxes', array(
    'piklist_meta_help'
    ,'piklist_meta_field_select'
    ,'piklist_meta_field_multiselect'
    ,'piklist_meta_field_radio'
    ,'piklist_meta_field_checkbox'
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