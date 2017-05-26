<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <a href="<?php user_profile_link( $user_id ); ?>"><?php echo get_user_display_name( $user_id ); ?></a> &raquo; <?php echo $profile_page_title; ?></div>
<h2 role="main"><?php echo get_user_name( $user->ID ); ?></h2>

<?php bb_profile_base_content(); ?>

<?php bb_get_footer(); ?>
