
<input 
  type="hidden"
  id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
  name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix); ?>"
  value="<?php echo esc_attr($value); ?>" 
  <?php echo piklist_form::attributes_to_string($attributes); ?>
/>
