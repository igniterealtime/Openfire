<?php
/*
Title: Featured Image(s)
Post Type: piklist_demo,piklist_lite_demo
Order: 40
Priority: default
Context: side
Collapse: true
*/
?>

<h3 class="demo-highlight">
  <?php _e('With Piklist you can easily replicate the WordPress Featured Image field, with an added bonus. Piklist allows you to use multiple featured images.','piklist-demo');?>
</h3>

<?php
  
  piklist('field', array(
    'type' => 'file'
    ,'field' => '_thumbnail_id' // Use these field to match WordPress featured images.
    ,'scope' => 'post_meta'
    ,'options' => array(
      'title' => 'Set featured image(s)'
      ,'button' => 'Set featured image(s)'
    )
  ));
  
  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));