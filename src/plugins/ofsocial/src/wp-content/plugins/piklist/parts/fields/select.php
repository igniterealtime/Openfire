
<select 
  id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
  name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix, $multiple); ?>"
  <?php echo piklist_form::attributes_to_string($attributes); ?>
>
  <?php foreach ($choices as $choice_value => $choice): ?>
    <option value="<?php echo esc_attr($choice_value); ?>" <?php echo (is_array($value) ? in_array($choice_value, $value) : $value == $choice_value) ? 'selected="selected"' : ''; ?>><?php echo $choice; ?></option>
  <?php endforeach; ?>
</select>