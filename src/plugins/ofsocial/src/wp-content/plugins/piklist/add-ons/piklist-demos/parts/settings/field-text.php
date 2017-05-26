<?php
/*
Title: Text Fields
Setting: piklist_demo_fields
Tab Order: 1
Order: 30
*/

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_class_regular'
    ,'label' => 'Text'
    ,'description' => 'class="regular-text"'
    ,'help' => 'You can easily add tooltips to your fields with the help parameter.'
    ,'attributes' => array(
      'class' => 'regular-text'
      ,'placeholder' => 'Enter some text'
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