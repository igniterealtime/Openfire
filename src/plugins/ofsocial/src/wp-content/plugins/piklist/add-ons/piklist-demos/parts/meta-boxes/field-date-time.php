<?php
/*
Title: DatePicker Fields
Post Type: piklist_demo
Order: 50
Collapse: true
*/
?>

<h3 class="demo-highlight">
  <?php _e('DatePicker fields are easy to create and format.','piklist-demo');?>
</h3>

<?php
  
  piklist('field', array(
    'type' => 'datepicker'
    ,'field' => 'date'
    ,'label' => 'Date'
    ,'description' => 'Choose a date'
    ,'options' => array(
      'dateFormat' => 'M d, yy'
    )
    ,'attributes' => array(
      'size' => 12
    )
    ,'value' => date('M d, Y', time() + 604800)
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('field', array(
    'type' => 'datepicker'
    ,'field' => 'date_add_more'
    ,'add_more' => true
    ,'label' => 'Add More'
    ,'description' => 'Choose a date'
    ,'options' => array(
      'dateFormat' => 'M d, yy'
    )
    ,'attributes' => array(
      'size' => 12
    )
    ,'value' => date('M d, Y', time() + 604800)
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
    
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>