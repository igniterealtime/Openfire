<?php
/**
 * The template for displaying the footer.
 *
 * Contains the closing of the id=main div and all content after
 *
 * @package iBuddy
 */
?>

	</div><!-- #main -->

	<footer id="colophon" class="site-footer" role="contentinfo">
		<div class="site-info">
			<?php if (of_get_option('copyright')) : ?>
				<?php echo of_get_option('copyright'); ?>
			<?php else: ?>
			<?php do_action( 'ibuddy_credits' ); ?>
			<a href="http://wordpress.org/" title="<?php esc_attr_e( 'A Semantic Personal Publishing Platform', 'ibuddy' ); ?>" rel="generator"><?php printf( __( 'Proudly powered by %s', 'ibuddy' ), 'WordPress' ); ?></a>
			<span class="sep"> | </span>
			<?php printf( __( 'Theme: %1$s by %2$s.', 'ibuddy' ), 'iBuddy', '<a href="http://profiles.wordpress.org/aymanalzarrad/" rel="designer">Ayman Al Zarrad</a>' ); ?>
			<?php endif; ?>
		</div><!-- .site-info -->
	</footer><!-- #colophon -->
</div><!-- #page -->

<?php wp_footer(); ?>
</body>
</html>
