<?php
/**
 * Footer
 *
 * @package Status
 * @since 1.0
 */
?>

		</div>
		</div>
		<?php do_action( 'bp_after_container' ) ?>
		<?php do_action( 'bp_before_footer' ) ?>

		<div id="footer-wrapper">
			<footer class="bottom">
			<?php if(has_nav_menu('footer')): ?>
				<nav id="secondary-nav">
					<?php wp_nav_menu(array('sort_column' => 'menu_order', 'container' => '', 'fallback_cb' => '', 'theme_location' => 'footer')) ?>
				</nav><!-- / #footer-nav -->
			<?php endif; ?>

			<div id="site-generator" role="contentinfo">
				<?php do_action( 'bp_dtheme_credits' ) ?>
				<p><?php printf( __( 'Proudly powered by <a href="%1$s">WordPress</a> and <a href="%2$s">BuddyPress</a>.', 'status' ), 'http://wordpress.org', 'http://buddypress.org' ) ?></p>
			</div>

			<?php do_action( 'bp_footer' ) ?>

			</footer><!-- #footer -->
			<?php do_action( 'bp_after_footer' ) ?>
		</div><!-- /#footer-wrapper -->

	<?php wp_footer(); ?>

	</body>

</html>