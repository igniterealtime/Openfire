<?php

if (!defined('ABSPATH'))
{
  exit;
}

class PikList_Universal_Widget extends WP_Widget 
{
  public $widgets = array();

  public $instance = array();

  public $widget_core_name = 'piklist_universal_widget';
  
  public $widget_name = '';

  public $widgets_path = '';
  
  public function PikList_Universal_Widget($name, $title, $description, $path = array(), $control_options = array()) 
  {
    global $pagenow;
    
    $this->widget_name = $name;
    $this->widgets_path = $path;
    
    if ($pagenow == 'customize.php')
    {
      $control_options['width'] = 300;
      $control_options['height'] = 200;
    }
      
    parent::__construct(
      ucwords(piklist::dashes($this->widget_name))
      ,__($title)
      ,array(
        'classname' => piklist::dashes($this->widget_core_name)
        ,'description' => __($description)
      )
      ,$control_options
    );
    
    add_action('wp_ajax_' . $name, array(&$this, 'ajax'));
  }
  
  public function form($instance) 
  {
    $this->register_widgets();
    
    $this->instance = $instance;

    if (isset($this->instance['widget']))
    {
      $widget = maybe_unserialize($this->instance['widget']);
      $widget = is_array($widget) ? current($widget) : null;      
    }
    
    piklist_widget::$current_widget = $this->widget_name;
    
    piklist::render('shared/widget-select', array(
      'instance' => $instance
      ,'widgets' => $this->widgets
      ,'name' => $this->widget_core_name
      ,'widget_name' => $this->widget_name
      ,'class_name' => piklist::dashes($this->widget_core_name)
      ,'widget' => isset($widget) ? $widget : null 
    ));
    
    return $instance;
  }
  
  public function ajax()
  {
    global $wp_widget_factory;
    
    $widget = isset($_REQUEST['widget']) ? $_REQUEST['widget'] : null;
    
    if ($widget)
    {
      $this->register_widgets();
      
      piklist_widget::$current_widget = $this->widget_name;
      
      if (isset($_REQUEST['number']))
      {
        $instances = get_option('widget_' . piklist::dashes($this->widget_name));
      
        piklist_widget::widget()->_set($_REQUEST['number']);
        
        if (isset($instances[$_REQUEST['number']]))
        {
          piklist_widget::widget()->instance = $instances[$_REQUEST['number']];
        }
      }

      if (isset($this->widgets[$widget]))
      {
        ob_start();
        
        do_action('piklist_widget_notices');
      
        piklist::render($this->widgets[$widget]['form'], null);

        piklist_form::save_fields();

        $output = ob_get_contents();
  
        ob_end_clean();
        
        echo json_encode(array(
          'form' => $output
          ,'widget' => $this->widgets[$widget]
          ,'tiny_mce' => piklist_form::$field_editor_settings['tiny_mce']
          ,'quicktags' => piklist_form::$field_editor_settings['quicktags']  
        ));
      }
    }
    
    die;
  }

  public function update($new_instance, $old_instance)
  {
    if (false !== ($fields = piklist_validate::check($new_instance)))
    { 
      $instance = array();
    
      foreach ($new_instance as $key => $value)
      {
        if (!empty($value))
        {
          $instance[$key] = is_array($value) ? maybe_serialize($value) : stripslashes($value);
        }
      }
      
      return $instance;
    }
    elseif (count($old_instance) <= 1)
    {
      return array(
        'widget' => $new_instance['widget']
      );
    }
    
    $old_instance['widget'] = $new_instance['widget'];
    
    return $old_instance;
  }

  public function widget($arguments, $instance) 
  {
    extract($arguments);
    
    $this->register_widgets();
    
    $instance = piklist::object_value($instance);
    $widget = $instance['widget'];
    
    if (!empty($widget))
    {
      unset($instance['widget']);
    
      $this->widgets[$widget]['instance'] = $instance;

      piklist_widget::$current_widget = $this->widget_name;
    
      do_action('piklist_pre_render_widget', $this->widgets[$widget]);
      
      piklist::render($this->widgets[$widget]['path'], array(
        'instance' => $instance
        ,'settings' => $instance
        ,'before_widget' => str_replace('class="', 'class="' . piklist::dashes($this->widgets[$widget]['add_on'] . ' ' . $this->widgets[$widget]['name']) . ' ' . $this->widgets[$widget]['data']['class'] . ' ', $before_widget)
        ,'after_widget' => $after_widget
        ,'before_title' => $before_title
        ,'after_title' => $after_title
      ));
    
      do_action('piklist_post_render_widget', $this->widgets[$widget]);
    }
  }
  
  public function register_widgets()
  {
    if (empty($this->widgets))
    {
      piklist::process_views('widgets', array(&$this, 'register_widgets_callback'), $this->widgets_path);
    }
  }

  public function register_widgets_callback($arguments)
  {
    extract($arguments);
    
    if (!strstr($part, '-form.php'))
    {
      $path .= '/parts/' . $folder . '/';
      $name = piklist::dashes(strtolower(str_replace('.php', '', $part)));
      $form = file_exists($path . $name . '-form.php') ? $path . $name . '-form' : false;
      
      if ($form)
      {
        $data = get_file_data($form . '.php', apply_filters('piklist_get_file_data', array(
          'height' => 'Height'
          ,'width' => 'Width'
        ), 'widgets'));
        
        $data = apply_filters('piklist_add_part', $data, 'widgets');
      }
      else
      {
        $data = null;
      }
      
      $this->widgets[$name] = array(
        'name' => $name
        ,'add_on' => $add_on
        ,'path' => $path . $name
        ,'form' => $form
        ,'form_data' => $data
        ,'data' => get_file_data($path . $part, array(
          'title' => 'Title'
          ,'description' => 'Description'
          ,'tags' => 'Tags'
          ,'class' => 'Class'
        ))
      );
    }
  }
}