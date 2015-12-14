<?php
/*
Plugin Name: Piklist Demos
Plugin URI: http://piklist.com
Description: Creates a Demo post type, Taxonomy, Settings Page, User fields, Dashbaord widget, Help tabs and Widget, with Field Examples.
Version: 0.3
Author: Piklist
Author URI: http://piklist.com/
*/

  if (!defined('ABSPATH'))
  {
    exit;
  }

  add_filter('piklist_post_types', 'piklist_demo_post_types');
  function piklist_demo_post_types($post_types)
  {
    $post_types['piklist_demo'] = array(
      'labels' => piklist('post_type_labels', 'Piklist Demos')
      ,'title' => __('Enter a new Demo Title')
      ,'menu_icon' => piklist_admin::responsive_admin() == true ? plugins_url('piklist/parts/img/piklist-menu-icon.svg') : plugins_url('piklist/parts/img/piklist-icon.png') 
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
      ,'supports' => array(
        'title'
        ,'revisions'
      )
      ,'public' => true
      ,'admin_body_class' => array (
        'piklist-demonstration'
        ,'piklist-sample'
      )
      ,'has_archive' => true
      ,'rewrite' => array(
        'slug' => 'piklist-demo'
      )
      ,'capability_type' => 'post'
      ,'edit_columns' => array(
        'title' => __('Demo')
        ,'author' => __('Assigned to')
      )
      ,'hide_meta_box' => array(
        'slug'
        ,'author'
      )
      ,'status' => array(
        'draft' => array(
          'label' => 'New'
          ,'public' => true
        )
        ,'demo' => array(
          'label' => 'Demo'
          ,'public' => true
          ,'exclude_from_search' => true
          ,'show_in_admin_all_list' => true
          ,'show_in_admin_status_list' => true
       )
        ,'lock' => array(
          'label' => 'Lock'
          ,'public' => true
        )
      )
    );
    
    $post_types['piklist_lite_demo'] = array(
      'labels' => piklist('post_type_labels', 'Lite Demo')
      ,'title' => __('Enter a new Demo Title')
      ,'menu_icon' => piklist_admin::responsive_admin() == true ? plugins_url('piklist/parts/img/piklist-menu-icon.svg') : plugins_url('piklist/parts/img/piklist-menu-icon.png')
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
      ,'show_in_menu' => 'edit.php?post_type=piklist_demo'
      ,'supports' => array(
        'title'
        ,'revisions'
      )
      ,'public' => true
      ,'has_archive' => true
      ,'capability_type' => 'post'
      ,'edit_columns' => array(
        'title' => __('Title')
      )
      ,'hide_meta_box' => array(
        'slug'
        ,'author'
      )
    );
    
    return $post_types;
  }
  

  add_filter('piklist_taxonomies', 'piklist_demo_taxonomies');
  function piklist_demo_taxonomies($taxonomies)
  {
    $taxonomies[] = array(
      'post_type' => 'piklist_demo'
      ,'name' => 'piklist_demo_type'
      ,'configuration' => array(
        'hierarchical' => true
        ,'labels' => piklist('taxonomy_labels', 'Demo Taxonomy')
        ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
        ,'show_ui' => true
        ,'query_var' => true
        ,'rewrite' => array( 
          'slug' => 'demo-type' 
        )
        ,'show_admin_column' => true
        ,'comments' => true
      )
    );
    
    $taxonomies[] = array(
      'object_type' => 'user'
      ,'name' => 'piklist_demo_user_type'
      ,'configuration' => array(
        'hierarchical' => true
        ,'labels' => piklist('taxonomy_labels', 'Demo User Type')
        ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
        ,'show_ui' => true
        ,'query_var' => true
        ,'rewrite' => array( 
          'slug' => 'demo-user-type' 
        )
        ,'show_admin_column' => true
      )
    );
  
    return $taxonomies;
  }


  add_filter('piklist_admin_pages', 'piklist_demo_admin_pages');
  function piklist_demo_admin_pages($pages) 
  {
    $pages[] = array(
      'page_title' => __('Demo Settings')
      ,'menu_title' => __('Demo Settings', 'piklist-demo')
      ,'sub_menu' => 'edit.php?post_type=piklist_demo'
      ,'capability' => 'manage_options'
      ,'menu_slug' => 'piklist_demo_fields'
      ,'setting' => 'piklist_demo_fields'
      ,'menu_icon' => plugins_url('piklist/parts/img/piklist-icon.png')
      ,'page_icon' => plugins_url('piklist/parts/img/piklist-page-icon-32.png')
      ,'single_line' => true
      ,'default_tab' => 'Basic'
      ,'save_text' => 'Save Demo Settings'
    );
  
    return $pages;
  }

  
  add_filter('piklist_field_templates', 'piklist_demo_field_templates');
  function piklist_demo_field_templates($templates)
  {
    $templates['piklist_demo'] = array(
                                'name' => __('User', 'piklist')
                                ,'description' => __('Default layout for User fields from Piklist Demos.', 'piklist')
                                ,'template' => '[field_wrapper]
                                                  <div id="%1$s" class="%2$s">
                                                    [field_label]
                                                    [field]
                                                    [field_description_wrapper]
                                                      <small>[field_description]</small>
                                                    [/field_description_wrapper]
                                                  </div>
                                                [/field_wrapper]'
                              );
                                    
    $templates['theme_tight'] = array(
                                  'name' => __('Theme - Tight', 'piklist')
                                  ,'description' => __('A front end form wrapper example from Piklist Demos.', 'piklist')
                                  ,'template' => '[field_wrapper]
                                                    <div id="%1$s" class="%2$s piklist-field-container">
                                                      [field_label]
                                                      <div class="piklist-field">
                                                        [field]
                                                        [field_description_wrapper]
                                                          <span class="piklist-field-description">[field_description]</span>
                                                        [/field_description_wrapper]
                                                      </div>
                                                    </div>
                                                  [/field_wrapper]'
                                );

    return $templates;
  }


  add_filter('piklist_post_submit_meta_box_title', 'piklist_demo_post_submit_meta_box_title', 10, 2);
  function piklist_demo_post_submit_meta_box_title($title, $post)
  {
    switch ($post->post_type)
    {
      case 'piklist_demo':
        $title = __('Create Demo');
      break;
    }
    
    return $title;
  }
  
  add_filter('piklist_post_submit_meta_box', 'piklist_demo_post_submit_meta_box', 10, 3);
  function piklist_demo_post_submit_meta_box($show, $section, $post)
  {
    switch ($post->post_type)
    {   
      case 'piklist_demo':
        
        switch ($section)
        {
          case 'minor-publishing-actions':
          //case 'misc-publishing-actions':
          //case 'misc-publishing-actions-status':
          case 'misc-publishing-actions-visibility':
          case 'misc-publishing-actions-published':
          
            $show = false;
          
          break;
        }
        
      break;
    }
    
    return $show;
  }

  add_action('the_content', 'piklist_demo_meta_field_insert');
  function piklist_demo_meta_field_insert($content)
  {
    if (get_post_type() == 'piklist_demo')
    {
      global $post;
      
      $meta = piklist('post_custom', $post->ID);

      foreach ($meta as $key => $value)
      {
        if (!empty($value) && substr($key, 0, 1) != '_')
        {
          $content .= '<br /><strong>' . $key . ':</strong> ' . (is_array($value) ? var_export($value, true) : $value);
        }
      }
    }
    
    return $content;
  }

  add_filter('piklist_assets', 'piklist_demo_assets');
  function piklist_demo_assets($assets)
  {    
    array_push($assets['styles'], array(
      'handle' => 'piklist-demos'
      ,'src' => piklist::$urls['piklist'] . '/add-ons/piklist-demos/parts/css/piklist-demo.css'
      ,'media' => 'screen, projection'
      ,'enqueue' => true
      ,'admin' => true
    ));
    
    return $assets;
  }

?>