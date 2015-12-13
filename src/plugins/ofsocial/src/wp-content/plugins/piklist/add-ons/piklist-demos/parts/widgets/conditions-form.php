<?php
/*
Width: 720
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
     
  ));

  piklist('field', array(
    'type' => 'checkbox'
    ,'field' => 'show_hide_checkbox'
    ,'label' => 'Checkbox: toggle a field'
    ,'choices' => array(
      'show' => 'Show'
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

  piklist('field', array(
    'type' => 'html'
    ,'field' => '_message_meal'
    ,'template' => 'admin_notice_error'
    ,'value' => __('We only serve steaks rare.', 'piklist-demo')
    ,'conditions' => array(
      'relation' => 'or'
      ,array(
        'field' => 'guest_meal'
        ,'value' => 'steak'
      )
      ,array(
        'field' => 'guest_one_meal'
        ,'value' => 'steak'
      )
      ,array(
        'field' => 'guest_two_meal'
        ,'value' => 'steak'
      )
    )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'attending'
    ,'label' => 'Are you coming to the party?'
    ,'choices' => array(
      '' => ''
      ,'yes' => 'Yes'
      ,'no' => 'No'
      ,'maybe' => 'Maybe'
    )
    ,'conditions' => array(
      array(
        'field' => 'guests'
        ,'value' => array('yes', 'maybe')
        ,'update' => 'yes'
        ,'type' => 'update'
      )
    )
  ));

  piklist('field', array(
    'type' => 'radio'
    ,'field' => 'guest_meal'
    ,'label' => 'Choose meal type'
    ,'choices' => array(
      'chicken' => 'Chicken'
      ,'steak' => 'Steak'
      ,'vegetarian' => 'Vegetarian'
    )
    ,'conditions' => array(
      array(
        'field' => 'attending'
        ,'value' => array('', 'no')
        ,'compare' => '!='
      )
    )
  ));

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'guests'
    ,'label' => 'Are you bringing guests'
    ,'description' => 'Coming to party != (No or empty)'
    ,'choices' => array(
      'yes' => 'Yes'
      ,'no' => 'No'
    )
    ,'conditions' => array(
      array(
        'field' => 'attending'
        ,'value' => array('', 'no')
        ,'compare' => '!='
      )
    )
  ));

  piklist('field', array(
    'type' => 'html'
    ,'field' => '_message_guests'
    ,'template' => 'admin_notice'
    ,'value' => __('Sorry, only two guests are allowed.', 'piklist-demo')
    ,'conditions' => array(
      array(
        'field' => 'guests_number'
        ,'value' => '3'
      )
    )
  ));

  piklist('field', array(
    'type' => 'number'
    ,'field' => 'guests_number'
    ,'label' => 'How many guests?'
    ,'description' => 'Coming to party != (No or empty) AND Guests = Yes'
    ,'value' => 1
    ,'attributes' => array(
      'class' => 'small-text'
      ,'step' => 1
      ,'min' => 1
      ,'max' => 3
    )
    ,'conditions' => array(
      array(
        'field' => 'attending'
        ,'value' => array('', 'no')
        ,'compare' => '!='
      )
      ,array(
        'field' => 'guests'
        ,'value' => 'yes'
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => 'Guest One'
    ,'description' => 'Number of guests != empty'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'guest_one'
        ,'label' => 'Name'
      )
      ,array(
        'type' => 'radio'
        ,'field' => 'guest_one_meal'
        ,'label' => 'Meal choice'
        ,'choices' => array(
          'chicken' => 'Chicken'
          ,'steak' => 'Steak'
          ,'vegetarian' => 'Vegetarian'
        )
      )
    )
    ,'conditions' => array(
      array(
        'field' => 'guests_number'
        ,'value' => array('', '0')
        ,'compare' => '!='
      )
      ,array(
        'field' => 'guests'
        ,'value' => 'yes'
      )
      ,array(
        'field' => 'attending'
        ,'value' => array('', 'no')
        ,'compare' => '!='
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => 'Guest Two'
    ,'description' => 'Number of guests != (empty or 1)'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'guest_two'
        ,'label' => 'Name'
      )
      ,array(
        'type' => 'radio'
        ,'field' => 'guest_two_meal'
        ,'label' => 'Meal choice'
        ,'choices' => array(
          'chicken' => 'Chicken'
          ,'steak' => 'Steak'
          ,'vegetarian' => 'Vegetarian'
        )
      )
    )
    ,'conditions' => array(
      array(
        'field' => 'guests_number'
        ,'value' => array('', '0', '1')
        ,'compare' => '!='
      )
      ,array(
        'field' => 'attending'
        ,'value' => array('', 'no')
        ,'compare' => '!='
      )
    )
  )); 


?>