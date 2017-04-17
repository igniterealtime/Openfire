<?php echo piklist_form::template_tag_fetch('field_wrapper', $wrapper, 'start'); ?>

<h3><?php echo isset($meta_box['config']['name']) ? $meta_box['config']['name'] : ''; ?></h3>
  
<?php if (!empty($meta_box['config']['description'])): ?>
  
  <?php echo wpautop($meta_box['config']['description']); ?>
  
<?php endif; ?>