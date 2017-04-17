<?php
/*
Title: Text Fields
Capability: manage_options
Order: 10
*/


  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_class_small'
    ,'label' => 'Text'
    ,'value' => 'Lorem'
    ,'help' => 'You can easily add tooltips to your fields with the help parameter.'
    ,'attributes' => array(
      'class' => 'regular-text'
    )
  ));


  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_add_more'
    ,'add_more' => true
    ,'label' => 'Text Add More'
    ,'description' => 'add_more="true"'
    ,'value' => 'Lorem'
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'number'
    ,'label' => 'Number'
    ,'description' => 'ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 5
    ,'attributes' => array(
      'class' => 'small-text'
      ,'step' => 1
      ,'min' => 0
      ,'max' => 10
    )
  ));

  piklist('field', array(
    'type' => 'textarea'
    ,'field' => 'textarea_large'
    ,'label' => 'Large Code'
    ,'description' => 'class="large-text code" rows="10" columns="50"'
    ,'value' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'attributes' => array(
      'rows' => 10
      ,'cols' => 50
      ,'class' => 'large-text code'
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));

?>