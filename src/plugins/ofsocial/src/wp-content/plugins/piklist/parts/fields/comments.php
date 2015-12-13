<?php

  piklist('field', array(
    'type' => 'textarea'
    ,'scope' => 'comment'
    ,'field' => 'comment_content'
    ,'template' => 'field'
    ,'attributes' => $attributes
    ,'value' => $value
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'comment'
    ,'field' => 'user_id'
    ,'value' => $current_user->ID
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'comment'
    ,'field' => 'comment_author'
    ,'value' => $current_user->display_name
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'comment'
    ,'field' => 'comment_author_email'
    ,'value' => $current_user->user_email
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'comment'
    ,'field' => 'comment_author_url'
    ,'value' => $current_user->user_url
  ));

  piklist('field', array(
    'type' => 'hidden'
    ,'scope' => 'comment'
    ,'field' => 'comment_post_ID'
    ,'value' => isset($post->ID) ? $post->ID : null
  ));
