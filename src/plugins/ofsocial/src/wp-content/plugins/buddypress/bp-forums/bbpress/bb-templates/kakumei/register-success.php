<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <?php _e('Register'); ?></div>

<h2 id="register" role="main"><?php _e('Great!'); ?></h2>

<p><?php printf(__('Your registration as <strong>%s</strong> was successful. Within a few minutes you should receive an email with your password.'), $user_login) ?></p>

<?php bb_get_footer(); ?>
