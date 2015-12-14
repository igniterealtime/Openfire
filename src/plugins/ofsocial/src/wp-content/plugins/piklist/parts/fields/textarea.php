
<?php

  $attributes['class'] = array_filter($attributes['class']) ? array_filter($attributes['class'],'trim') : array('large-text', 'code');

?>

<textarea
  id="<?php echo piklist_form::get_field_id($field, $scope, $index, $prefix); ?>" 
  name="<?php echo piklist_form::get_field_name($field, $scope, $index, $prefix); ?>"
  <?php echo piklist_form::attributes_to_string($attributes); ?>
><?php echo esc_textarea($value); ?></textarea>
