<?php
/*
Title: Conditional Fields <span class="piklist-title-right">Order 90</span>
Post Type: piklist_demo
Order: 90
Collapse: false
*/

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'show_hide_select'
    ,'label' => 'Select: toggle a field'
    ,'choices' => array(
      'show1' => 'Show first set'
      ,'show2' => 'Show second set'
      ,'hide' => 'Hide all'
    )
    ,'value' => 'hide'
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'show_hide_field_select_1'
    ,'label' => 'Show/Hide Field (Set 1)'
    ,'description' => 'This field is toggled by the Select field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show1'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'another_show_hide_field_select_1'
    ,'label' => 'Another Show/Hide Field (Set 1)'
    ,'description' => 'This field is also toggled by the Select field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show1'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'show_hide_field_select_set_2'
    ,'label' => 'Show/Hide Field (Set 2)'
    ,'description' => 'This field is toggled by the Select field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show2'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'another_show_hide_field_select_set_2'
    ,'label' => 'Another Show/Hide Field (Set 2)'
    ,'description' => 'This field is also toggled by the Select field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show2'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'select_show_hide_field_select_set_2'
    ,'label' => 'Select Show/Hide Field (Set 2)'
    ,'description' => 'This field is also toggled by the Select field above'
    ,'choices' => array(
      'a' => 'Choice A'
      ,'b' => 'Choice B'
      ,'c' => 'Choice C'
    )
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show2'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox_show_hide_field_select_set_2'
    ,'label' => 'Checkbox Show/Hide Field (Set 2)'
    ,'description' => 'This field is also toggled by the Select field above'
    ,'choices' => array(
      'a' => 'Choice A'
      ,'b' => 'Choice B'
      ,'c' => 'Choice C'
    )
    ,'conditions' => array(
      array(
        'field' => 'show_hide_select'
        ,'value' => 'show2'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));


  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'show_hide'
    ,'label' => 'Radio: toggle a field'
    ,'choices' => array(
      'show' => 'Show'
      ,'hide' => 'Hide'
    )
    ,'value' => 'hide'
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'show_hide_field'
    ,'label' => 'Show/Hide Field'
    ,'description' => 'This field is toggled by the Radio field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide'
        ,'value' => 'show'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_hide_checkbox'
    ,'label' => 'Checkbox: toggle a field'
    ,'choices' => array(
      'show' => 'Show'
    )
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'show_hide_field_checkbox'
    ,'label' => 'Show/Hide Field'
    ,'description' => 'This field is toggled by the Checkbox field above'
    ,'conditions' => array(
      array(
        'field' => 'show_hide_checkbox'
        ,'value' => 'show'
      )
    )
    ,'on_post_status' => array(
        'value' => 'lock'
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
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('field', array(
    'type' => 'text'
    ,'field' => 'update_field'
    ,'value' => 'Hello World!'
    ,'label' => 'Update This Field'
    ,'description' => 'This field is updated by the field above'
    ,'on_post_status' => array(
        'value' => 'lock'
      )
  ));

  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));

?>