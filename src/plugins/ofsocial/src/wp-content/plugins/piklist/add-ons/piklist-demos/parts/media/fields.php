<?php
/*
Title: Piklist Demo Fields
Description: This is an example of some fields built with Piklist. Only displays for users with the capability of manage_options.
Capability: manage_options
*/

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_class_small'
    ,'label' => 'Small'
    ,'description' => 'class="small-text"'
    ,'value' => 'Lorem'
    ,'attributes' => array(
      'class' => 'small-text'
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_required'
    ,'label' => 'Text Required'
    ,'description' => "required' => true"
    ,'attributes' => array(
      'class' => 'small-text'
    )
    ,'required' => true
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_null'
    ,'label' => 'Text Null'
    ,'value' => 'null'
    ,'description' => "required' => true"
    ,'attributes' => array(
      'class' => 'small-text'
    )
    ,'required' => true
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_false'
    ,'label' => 'Text False'
    ,'value' => 'false'
    ,'description' => "required' => true"
    ,'attributes' => array(
      'class' => 'small-text'
    )
    ,'required' => true
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_columns_element'
    ,'label' => 'Columns Element'
    ,'description' => 'columns="6"'
    ,'value' => 'Lorem'
    ,'attributes' => array(
      'columns' => 6
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'text_add_more'
    ,'add_more' => true
    ,'label' => 'Add More'
    ,'description' => 'add_more="true" columns="8"'
    ,'value' => 'Lorem'
    ,'attributes' => array(
      'columns' => 8
    )
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'number'
    ,'label' => 'Number'
    ,'description' => 'ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 5
    ,'attributes' => array(
      'class' => 'small-text'
      ,'step' => 5
      ,'min' => 5
    )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'select'
    ,'label' => 'Select'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
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
        ,'value' => 'third'
        ,'choices' => array(
          'first' => 'First Choice'
          ,'second' => 'Second Choice'
          ,'third' => 'Third Choice'
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
          'forth' => 'Forth Choice'
          ,'fifth' => 'Fifth Choice'
        )
        ,'columns' => 6
      )
    )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox_nested'
    ,'label' => 'Nested Field'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => array(
      'first'
      ,'third'
    )
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice with a nested [field=checkbox_nested_text] input.'
      ,'third' => 'Third Choice'
    )
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'checkbox_nested_text'
        ,'value' => '12345'
        ,'embed' => true
        ,'attributes' => array(
          'class' => 'text'
        )
      )
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
  

  piklist('field', array(
    'type' => 'colorpicker'
    ,'field' => 'color'
    ,'label' => 'Color Picker'
    ,'value' => '#03ADEF'
  ));

  piklist('field', array(
    'type' => 'colorpicker'
    ,'field' => 'color_add_more'
    ,'add_more' => true
    ,'label' => 'Add More'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'on_post_status' => array(
      'value' => 'lock'
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'show_hide'
    ,'label' => 'Toggle a field'
    ,'choices' => array(
      'show' => 'Show'
      ,'hide' => 'Hide'
    )
    ,'value' => 'hide'
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'show_hide_field'
    ,'label' => 'Show/Hide Field'
    ,'description' => 'This field is toggled by the field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide'
        ,'value' => 'show'
      )
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'change'
    ,'label' => 'Update a field'
    ,'choices' => array(
      'hello-world' => 'Hello World'
      ,'clear' => 'Clear'
    )
    ,'value' => 'hello-world'
    ,'conditions' => array(
      array(
        'field' => 'update_field'
        ,'value' => 'hello-world' 
        ,'update' => 'Hello World!' 
        ,'type' => 'update'
      )
      ,array(
        'field' => 'update_field'
        ,'value' => 'clear' 
        ,'update' => '' 
        ,'type' => 'update'
      )
    )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'update_field'
    ,'value' => 'Hello World!'
    ,'label' => 'Update This Field'
    ,'description' => 'This field is updated by the field above'
  ));

  piklist('shared/meta-field-welcome', array(
    'location' => __FILE__
  ));

?>