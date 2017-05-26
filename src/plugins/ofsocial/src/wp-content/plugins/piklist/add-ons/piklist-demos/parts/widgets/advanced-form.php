<?php
/*
Width: 720
*/

piklist('field', array(
    'type' => 'colorpicker'
    ,'field' => 'color'
    ,'label' => 'Color Picker'
 
  ));

  piklist('field', array(
    'type' => 'colorpicker'
    ,'add_more' => true
    ,'field' => 'color_add_more'
    ,'label' => 'Color Picker Add More'
 
  ));


piklist('field', array(
    'type' => 'datepicker'
    ,'field' => 'date'
    ,'label' => 'Date'
    ,'description' => 'Choose a date'
    ,'options' => array(
      'dateFormat' => 'M d, yy'
    )
    ,'attributes' => array(
      'size' => 12
    )
    ,'value' => date('M d, Y', time() + 604800)
 
  ));

  piklist('field', array(
    'type' => 'datepicker'
    ,'field' => 'date_add_more'
    ,'add_more' => true
    ,'label' => 'Date Add More'
    ,'description' => 'Choose a date'
    ,'options' => array(
      'dateFormat' => 'M d, yy'
    )
    ,'attributes' => array(
      'size' => 12
    )
    ,'value' => date('M d, Y', time() + 604800)
 
  ));


piklist('field', array(
    'type' => 'group'
    ,'label' => __('Newsletter Signup (Grouped)')
    ,'description' => __('Add email addresses with topic selectivity')
    ,'field' => 'newsletter_signup'
    ,'add_more' => true
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'first_name'
        ,'label' => 'First Name'
        ,'columns' => 4
      )
      ,array(
        'type' => 'text'
        ,'field' => 'last_name'
        ,'label' => 'Last Name'
        ,'columns' => 4
      )
      ,array(
        'type' => 'text'
        ,'field' => 'email'
        ,'label' => 'Email Address'
        ,'columns' => 4
      )
      ,array(
        'type' => 'group'
        ,'field' => 'newsletters'
        ,'fields' => array(
          array(
            'type' => 'checkbox'
            ,'field' => 'newsletter_a'
            ,'label' => 'Newsletter A'
            ,'columns' => 4
            ,'value' => 'first'
            ,'choices' => array(
              'first' => 'A-1'
              ,'second' => 'A-2'
              ,'third' => 'A-3'
            )
          )
          ,array(
            'type' => 'checkbox'
            ,'field' => 'newsletter_b'
            ,'columns' => 4
            ,'label' => 'Newsletter B'
            ,'value' => 'second'
            ,'choices' => array(
              'first' => 'B-1'
              ,'second' => 'B-2'
              ,'third' => 'B-3'
            )
          )
          ,array(
            'type' => 'checkbox'
            ,'field' => 'newsletter_c'
            ,'columns' => 4
            ,'label' => 'Newsletter C'
            ,'value' => 'third'
            ,'choices' => array(
              'first' => 'C-1'
              ,'second' => 'C-2'
              ,'third' => 'C-3'
            )
          )
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field' => 'work_order_repair'
    ,'add_more' => true
    ,'label' => 'REPAIR'
    ,'description' => 'Enter TYPE of Work, PRICE and DUE DATE'
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'work'
        ,'columns' => 6
        ,'attributes' => array(
          'placeholder' => 'Type of work'
        )
      )
      ,array(
        'type' => 'number'
        ,'field' => 'price'
        ,'columns' => 2
        ,'attributes' => array(
          'placeholder' => '$'
        )
      )
      ,array(
        'type' => 'datepicker'
        ,'field' => 'due'
        ,'columns' => 4
        ,'options' => array(
          'dateFormat' => 'M d, yy'
        )
        ,'attributes' => array(
          'placeholder' => 'Due date'
        )
      )
    )
  ));


  piklist('field', array(
    'type' => 'group'
    ,'field' => 'demo_add_more_group_todo'
    ,'label' => __('Todo\'s (Grouped)')
    ,'add_more' => true
    ,'fields' => array(
      array(
        'type' => 'select'
        ,'field' => 'user'
        ,'label' => 'Assigned to'
        ,'columns' => 4
        ,'choices' => array (
          'adam' => 'Adam'
          ,'bill' => 'Bill'
          ,'carol' => 'Carol'
          )
        )
        ,array(
          'type' => 'text'
          ,'field' => 'task'
          ,'label' => 'Task'
          ,'columns' => 8
        )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => __('Todo\'s (Un-Grouped)')
    ,'add_more' => true
    ,'fields' => array(
      array(
        'type' => 'select'
        ,'field' => 'demo_add_more_todo_user'
        ,'label' => 'Assigned to'
        ,'columns' => 4
        ,'choices' => array (
          'adam' => 'Adam'
          ,'bill' => 'Bill'
          ,'carol' => 'Carol'
          )
        )
        ,array(
          'type' => 'text'
          ,'field' => 'demo_add_more_todo_task'
          ,'label' => 'Task'
          ,'columns' => 8
        )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => __('Content Section (Grouped)')
    ,'description' => __('When an add-more field is nested it should be grouped to maintain the data relationships.')
    ,'field' => 'demo_content'
    ,'add_more' => true
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'csg_title'
        ,'label' => 'Title'
        ,'columns' => 12
        ,'attributes' => array(
          'class' => 'large-text'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'csg_section'
        ,'label' => 'Section'
        ,'columns' => 12
        ,'attributes' => array(
          'class' => 'large-text'
        )
      )
      ,array(
        'type' => 'group'
        ,'field' => 'content'
        ,'add_more' => true
        ,'fields' => array(
          array(
            'type' => 'select'
            ,'field' => 'post_id'
            ,'label' => 'Grade'
            ,'columns' => 12
            ,'choices' => array (
              'a' => 'A'
              ,'b' => 'B'
              ,'c' => 'C'
            )
          )
        )
      )
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'label' => __('Content Section with Siblings (Grouped)')
    ,'decription' => __('When an add-more field is nested it should be grouped to maintain the data relationships.')
    ,'field' => 'demo_content_sibling'
    ,'add_more' => true
    ,'fields' => array(
      array(
        'type' => 'text'
        ,'field' => 'title'
        ,'label' => 'Section Title'
        ,'columns' => 12
        ,'attributes' => array(
          'class' => 'large-text'
        )
      )
      ,array(
        'type' => 'text'
        ,'field' => 'tagline'
        ,'label' => 'Section Tagline'
        ,'columns' => 12
        ,'attributes' => array(
          'class' => 'large-text'
        )
      )
      ,array(
        'type' => 'group'
        ,'field' => 'sibling_content_1'
        ,'add_more' => true
        ,'fields' => array(
          array(
            'type' => 'select'
            ,'field' => 'post_id_sibling_1'
            ,'label' => 'Content One Title'
            ,'columns' => 12
            ,'choices' => piklist(
              get_posts(
                 array(
                  'post_type' => 'post'
                  ,'orderby' => 'post_date'
                 )
                 ,'objects'
               )
               ,array(
                 'ID'
                 ,'post_title'
               )
            )
          )
        )
      )
      ,array(
        'type' => 'group'
        ,'field' => 'sibling_content_2'
        ,'add_more' => true
        ,'fields' => array(
          array(
            'type' => 'select'
            ,'field' => 'post_id_sibling_2'
            ,'label' => 'Content Two Title'
            ,'columns' => 12
            ,'choices' => piklist(
              get_posts(
                 array(
                  'post_type' => 'post'
                  ,'orderby' => 'post_date'
                 )
                 ,'objects'
               )
               ,array(
                 'ID'
                 ,'post_title'
               )
            )
          )
        )
      )
    )
  ));


  piklist('shared/code-locater', array(
    'location' => __FILE__
    ,'type' => 'Meta Box'
  ));

?>