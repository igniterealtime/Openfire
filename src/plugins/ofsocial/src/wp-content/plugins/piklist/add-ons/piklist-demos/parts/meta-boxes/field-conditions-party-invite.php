<?php
/*
Title: Party Invite
Post Type: piklist_demo
Order: 100
Collapse: false
*/
  
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