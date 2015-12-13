
<?php 
  
  $values = array_keys($choices);
  
  foreach ($choices as $_add_on => $_name):
    if (piklist_add_on::$available_add_ons[$_add_on]['plugin']):
      unset($choices[$_add_on]);
    endif;
  endforeach;
  
  $settings = piklist_setting::get('settings');
  $values = array_keys($choices);
  $attributes['style'] = 'display: none !important;';
  
  for ($index = 0; $index < count($choices); $index++):
    $active = (!is_array($value) && $value == $values[$index]) || (is_array($value) && in_array($values[$index], $value));
?>
    
    <div class="piklist-field-add-on">  
  
      <h3>
        <?php echo piklist_add_on::$available_add_ons[$values[$index]]['Name']; ?>
      </h3>
    
      <p>
        <?php echo piklist_add_on::$available_add_ons[$values[$index]]['Description']; ?>
      </p>
    
      <input 
        type="checkbox"
        id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
        name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix); ?>"
        value="<?php echo esc_attr($values[$index]); ?>"
        <?php echo $active ? 'checked="checked"' : ''; ?>
        <?php echo piklist_form::attributes_to_string($attributes); ?>
      />
    
      <a href="#<?php echo piklist::dashes(piklist_add_on::$available_add_ons[$values[$index]]['Name']); ?>" class="button<?php echo $active ? '' : '-primary'; ?> piklist-field-add-on-button">
        <?php $active ? _e('Disable','piklist') : _e('Activate','piklist');?>
      </a>

      <?php if (isset($settings[$values[$index]])): ?>
      
        <a href="<?php echo admin_url('admin.php?page=' . $values[$index]); ?>" class="button piklist-field-add-on-button-settings">
          <?php _e('Settings','piklist'); ?>
        </a>
      
      <?php endif; ?>
    
    </div>

<?php endfor; ?>

<script type="text/javascript">

  (function($)
  {
    $(document).ready(function()
    {
      $('.piklist-field-add-on-button').click(function(event)
      {
        event.preventDefault();
        
        if ($(this).hasClass('button-primary'))
        {
          $(this).removeClass('button-primary').addClass('button').text('Disable').prev(':checkbox').attr('checked', true);
          $(this).next('a').show();
        }
        else
        {
          $(this).removeClass('button').addClass('button-primary').text('Activate').prev(':checkbox').attr('checked', false);
          $(this).next('a').hide();
        }
      });
    });
  })(jQuery);

</script>