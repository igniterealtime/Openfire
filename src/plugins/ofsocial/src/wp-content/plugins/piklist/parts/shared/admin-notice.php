
<div class="<?php echo esc_attr((is_admin() ? null : 'piklist-notice-') . $type); ?>">

  <?php if (is_array($notices)): ?>
    
    <?php foreach ($notices as $notice): ?>

      <p><?php echo $notice; ?></p>

    <?php endforeach; ?>
  
  <?php else: ?>
    
    <p>
      <?php echo $notices; ?>
    </p>

  <?php endif; ?>

  <?php if(!empty($notice_id)) : ?>

 		<a class="piklist-dismiss-notice" href="<?php echo esc_url(add_query_arg('piklist-dismiss', $notice_id)); ?>" target="_parent"><?php _e('Dismiss', 'piklist'); ?></a>
	
	<?php endif; ?>

</div>
