<?php
/*
Title: ColorPicker Fields
Post Type: piklist_demo
Order: 60
Collapse: true
*/
?>

<h3 class="demo-highlight">
  <?php _e('WordPress ColorPicker fields are super simple to create. Piklist handles all the Javascript.','piklist-demo');?>
</h3>

<?php
    
  piklist('field', array(
    'type' => 'colorpicker'
    ,'field' => 'color'
    ,'label' => 'Color Picker'
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));

  piklist('field', array(
    'type' => 'colorpicker'
    ,'add_more' => true
    ,'field' => 'color_add_more'
    ,'label' => 'Color Picker'
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>