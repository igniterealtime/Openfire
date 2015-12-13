<?php /* Added for Backwards compatibility with Piklist < 0.9.9 */ ?>

<div id="<?php echo $id; ?>" class="notice piklist-notice <?php echo (isset($dismiss) && $dismiss == true) || (isset($notice_id) && !empty($notice_id)) ? 'is-dismissible' : null; ?> <?php echo esc_attr((is_admin() ? (isset($notice_type) && $notice_type == 'update' ? 'updated ' : null) : 'piklist-notice-') . (isset($notice_type) ? $notice_type : 'updated')); ?>">

  <?php echo wpautop($content); ?>

</div>
