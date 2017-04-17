<?php
/*
Title: Radio Fields
Capability: manage_options
Order: 50
Collapse: false
*/

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'radio'
    ,'label' => 'Radio'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'radio_add_more'
    ,'label' => 'Radio Add More'
    ,'add_more' => true
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'second'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'radio_inline'
    ,'label' => 'Single Line'
    ,'value' => 'no'
    ,'list' => false
    ,'choices' => array(
      'yes' => 'Yes'
      ,'no' => 'No'
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'radio_list'
    ,'label' => 'Group Lists'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'fields' => array(
      array(
        'type' => 'radio'
        ,'field' => 'radio_list_1'
        ,'label' => 'List #1'
        ,'label_position' => 'before'
        ,'value' => 'second'
        ,'choices' => array(
          'first' => '1-1 Choice'
          ,'second' => '1-2 Choice'
        )
        ,'columns' => 6
      )
      ,array(
        'type' => 'radio'
        ,'field' => 'radio_list_2'
        ,'label' => 'List #2'
        ,'label_position' => 'before'
        ,'value' => 'second'
        ,'choices' => array(
          'first' => '2-1 Choice'
          ,'second' => '2-2 Choice'
          ,'third' => '2-3 Choice'
        )
        ,'columns' => 6
      )
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'radio_nested'
    ,'label' => 'Nested Field'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice with a nested [field=radio_nested_text] input.'
      ,'third' => 'Third Choice'
    )
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'radio_nested_text'
        ,'value' => '123'
        ,'embed' => true
        ,'attributes' => array(
          'class' => 'small-text'
        )
      )
    )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));

?>