<?php
/*
Title: Text Fields
Description: this is the description
Post Type: piklist_demo
Order: 10
Collapse: false
*/
?>

<h3 class="demo-highlight">
  <?php _e('Text fields are at the core of most forms, and easily created with Piklist. Here a text field is marked REQUIRED, which will stop the entire form from saving unless filled in. Tooltip help can be added to any field with one line of code, and HTML fields can output your markup in the same format as other fields.','piklist-demo');?>
</h3>

<?php

  piklist('field', array(
    'type' => 'password'
    ,'field' => 'text_class_regular'
    ,'label' => 'color'
    ,'description' => 'class="regular-text"'
    ,'help' => 'You can easily add tooltips to your fields with the help parameter.'
    ,'attributes' => array(
      'class' => 'regular-text'
      ,'placeholder' => 'Enter some text'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_required'
    ,'label' => 'Text Required'
    ,'description' => "required => true"
    ,'attributes' => array(
      'class' => 'regular-text'
      ,'placeholder' => 'Enter text or this page won\'t save.'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
    ,'required' => true
  ));
  
  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_add_more'
    ,'add_more' => true
    ,'label' => 'Add More'
    ,'description' => 'add_more="true" columns="8"'
    ,'attributes' => array(
      'columns' => 8
      ,'placeholder' => 'Enter some text'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));
  
  piklist('field', array(
    'type' => 'textarea'
    ,'field' => 'demo_textarea_large'
    ,'label' => 'Large Code'
    ,'description' => 'class="large-text code" rows="10" columns="50"'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'attributes' => array(
      'rows' => 10
      ,'cols' => 50
      ,'class' => 'large-text code'
    )
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));

  piklist('field', array(
    'type' => 'html'
    ,'label' => 'HTML Field'
    ,'description' => 'Allows you to output any HTML in the proper format.'
    ,'value' => '<ul><li>First Item</li><li>Second Item</li></ul>'
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));
  
?>