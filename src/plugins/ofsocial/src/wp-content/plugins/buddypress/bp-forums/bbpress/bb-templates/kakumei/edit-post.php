<?php bb_get_header(); ?>
<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <?php _e('Edit Post'); ?></div>

<?php edit_form(); ?>

<?php bb_get_footer(); ?>
