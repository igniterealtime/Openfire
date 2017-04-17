
<?php echo piklist_form::template_tag_fetch('field_wrapper', $wrapper, 'end'); ?>

<h3><?php echo $meta_box['config']['name']; ?></h3>
  
<?php if (!empty($meta_box['config']['description'])): ?>
  
  <?php echo wpautop($meta_box['config']['description']); ?>
  
<?php endif; ?>
  
<?php echo piklist_form::template_tag_fetch('field_wrapper', $wrapper, 'start'); ?>
