<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option( 'name' ); ?></a> &raquo; <?php _e( 'Statistics' ); ?></div>

<dl role="main" class="left">
	<dt><?php _e( 'Registered Users' ); ?></dt>
	<dd><strong><?php bb_total_users(); ?></strong></dd>
	<dt><?php _e( 'Posts' ); ?></dt>
	<dd><strong><?php total_posts(); ?></strong></dd>
<?php do_action( 'bb_stats_left' ); ?>
</dl>

<div class="right">
<?php if ( $popular ) : ?>
	<h3><?php _e( 'Most Popular Topics' ); ?></h3>
	<ol>
<?php foreach ( $popular as $topic ) : ?>
		<li><?php bb_topic_labels(); ?> <a href="<?php topic_link(); ?>"><?php topic_title(); ?></a> &#8212; <?php printf( _n( '%s post', '%s posts', get_topic_posts() ), bb_number_format_i18n( get_topic_posts() ) ); ?></li>
<?php endforeach; ?>
	</ol>
<?php endif; ?>

<?php do_action( 'bb_stats_right' ); ?>
</div>

<?php bb_get_footer(); ?>
