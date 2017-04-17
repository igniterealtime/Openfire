<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <?php _e('Log in'); ?></div>

<h2 role="main"><?php _e('Password Reset'); ?></h2>

<?php if ( $error ) : ?>
<p class="notice error"><?php echo $error; ?></p>
<?php else : ?>
<?php switch ( $action ) : ?>
<?php case ( 'send_key' ) : ?>
<p class="notice"><?php _e('An email has been sent to the address we have on file for you. If you don&#8217;t get anything within a few minutes, or your email has changed, you may want to get in touch with the webmaster or forum administrator here.'); ?></p>
<?php break; ?>
<?php case ( 'reset_password' ) : ?>
<p class="notice"><?php _e('Your password has been reset and a new one has been mailed to you.'); ?></p>
<?php break; ?>
<?php endswitch; ?>
<?php endif; ?>

<?php bb_get_footer(); ?>
