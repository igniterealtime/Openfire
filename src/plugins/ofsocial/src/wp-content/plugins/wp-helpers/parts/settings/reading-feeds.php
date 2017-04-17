<?php
/*
Title: Feeds
Setting: piklist_wp_helpers
Tab: Reading
Order: 310
Flow: WP Helpers Settings Flow
*/

  piklist('field', array(
    'type' => 'select'
    ,'field' => 'disable_feeds'
    ,'label' => 'All Feeds'
    ,'value' => 'false'
    ,'choices' => array(
      'true' => 'Disable'
      ,'false' => 'Enable'
    )
  ));

  piklist('field', array(
    'type' => 'group'
    ,'field'=> 'delay_feed'
    ,'label' => 'Delay Feed Publishing'
    ,'fields' => array(
      array(
        'type' => 'number'
        ,'field' => 'delay_feed_num'
        ,'columns' => 1
      )
      ,array(
        'type' => 'select'
        ,'field' => 'delay_feed_time'
        ,'value' => 'min'
        ,'choices' => array(
          'minute' => 'Minute(s)'
          ,'hour' => 'Hour(s)'
          ,'day' => 'Day(s)'
          ,'week' => 'Week(s)'
          ,'month' => 'Month(s)'
          ,'year' => 'Year(s)'
        )
        ,'columns' => 2
      )
    )
    ,'conditions' => array(
      array(
        'field' => 'disable_feeds'
        ,'value' => 'false'
      )
    )
  ));

  if (current_theme_supports('post-thumbnails'))
  {
    piklist('field', array(
      'type' => 'checkbox'
      ,'field' => 'featured_image_in_feed'
      ,'label' => 'Featured Image'
      ,'choices' => array(
        'true' => 'Add Featured Images to feed.'
      )
      ,'conditions' => array(
        array(
          'field' => 'disable_feeds'
          ,'value' => 'false'
        )
      )
    ));
  }