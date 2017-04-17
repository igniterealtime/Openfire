<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <?php _e('Tags'); ?></div>

<p role="main"><?php _e('This is a collection of tags that are currently popular on the forums.'); ?></p>

<div id="hottags">
<?php bb_tag_heat_map( 9, 38, 'pt', 80 ); ?>
</div>

<?php bb_get_footer(); ?>
