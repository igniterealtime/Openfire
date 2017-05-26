<?php
/*
Width: 720
*/

piklist('field', array(
    'type' => 'select'
    ,'field' => 'multiselect'
    ,'label' => 'Multiselect'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
    ,'attributes' => array(
      'multiple' => 'multiple'
    )
     
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'multiselect_add_more'
    ,'label' => 'Multiselect Add More'
    ,'add_more' => true
    ,'description' => 'A grouped field. Data is not searchable, since it is saved in an array.'
    ,'fields' => array(
      array(
        'type' => 'select'
        ,'field' => 'multiselect_add_more_field'
        ,'choices' => array(
          'first' => 'First Choice'
          ,'second' => 'Second Choice'
          ,'third' => 'Third Choice'
        )
        ,'attributes' => array(
          'multiple' => 'multiple'
        )
      )
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
    'type' => 'select'
    ,'field' => 'select_add_more'
    ,'label' => 'Select Add More'
    ,'add_more' => true
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
     
  ));

piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox'
    ,'label' => 'Checkbox'
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
     
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox_add_more'
    ,'label' => 'Checkbox Add More'
    ,'add_more' => true
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'value' => 'third'
    ,'choices' => array(
      'first' => 'First Choice'
      ,'second' => 'Second Choice'
      ,'third' => 'Third Choice'
    )
     
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'checkbox_inline'
    ,'label' => 'Single Line'
    ,'value' => 'that'
    ,'list' => false
    ,'choices' => array(
      'this' => 'This'
      ,'that' => 'That'
    )
     
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'checkbox_list'
    ,'label' => 'Group Lists'
    ,'list' => false
    ,'description' => 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
    ,'fields' => array(
      array(
        'type' => 'checkbox'
        ,'field' => 'checkbox_list_1'
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
        'type' => 'checkbox'
        ,'field' => 'checkbox_list_2'
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
        ,'value' => '123'
        ,'embed' => true
        ,'attributes' => array(
          'class' => 'small-text'
        )
      )
    )
     
  ));


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


?>