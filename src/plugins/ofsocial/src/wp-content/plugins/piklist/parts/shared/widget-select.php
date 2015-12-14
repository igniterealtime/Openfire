
<?php if (!empty($widgets)): ?>
  
  <div class="piklist-universal-widget-select-container">
    
    <?php
      $choices = array(
        '' => __('Select a Widget', 'piklist')
      );

      foreach ($widgets as $w)
      {
        if ($widget == $w['name'])
        {
          $widget_data = $w;
        }
      
        $choices[$w['name']] = $w['data']['title'];
      }
    
      $default_widget = current($widgets);
      
      piklist('field', array(
        'type' => 'select'
        ,'field' => 'widget'
        ,'label' => __('Select a Widget', 'piklist')
        ,'editable' => false
        ,'attributes' => array(
          'class' => array(
            'widefat'
            ,$class_name . '-select'
          )
          ,'data-piklist-addon' => $default_widget['add_on']
        )
        ,'position' => 'wrap'
        ,'choices' => $choices
      ));
    
      $data_attributes = '';
      if (isset($widget_data))
      {
        $data_attributes .= 'data-widget-title="' . $widget_data['data']['title'] . '" data-widget-height="' . $widget_data['form_data']['height'] . '" data-widget-width="' . $widget_data['form_data']['width'] . '"';
      }
    ?>
    
    <p>
      <?php if (isset($widgets[$widget])) echo $widgets[$widget]['data']['description']; ?>
    </p>
      
  </div>

  <div class="piklist-universal-widget-form-container" <?php echo $data_attributes; ?>>
    
    <?php 
      if ($widget):
        
        do_action('piklist_widget_notices');
        
        piklist::render($widgets[$widget]['form'], array(
          'instance' => $instance
        ));
        
        piklist_form::save_fields();
      
      endif;
    ?>
    
  </div>

<?php else: ?>
  
  <p>
    <em><?php _e('There are currently no Widgets available.', 'piklist'); ?></em>
  </p>
  
  <h4><?php _e('Learn to make Widgets', 'piklist'); ?></h4>
  
  <p>
    <?php _e('Check out the documentation for how to easily build your own custom widgets!', 'piklist')?>
  </p>  
  
<?php endif; ?>
