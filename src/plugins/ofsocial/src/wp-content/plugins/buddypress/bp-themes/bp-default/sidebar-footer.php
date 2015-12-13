<?php
	/**
	 * The footer widget area is triggered if any of the areas
	 * have widgets.
	 *
	 * If none of the sidebars have widgets, bail early.
	 */
	if (   ! is_active_sidebar( 'first-footer-widget-area'  )
		&& ! is_active_sidebar( 'second-footer-widget-area' )
		&& ! is_active_sidebar( 'third-footer-widget-area'  )
		&& ! is_active_sidebar( 'fourth-footer-widget-area' )
	)
	return; ?>

			<div id="footer-widget-area" role="complementary">

				<?php if ( is_active_sidebar( 'first-footer-widget-area' ) ) : ?>

					<div id="first" class="widget-area">
						<ul class="xoxo">
							<?php dynamic_sidebar( 'first-footer-widget-area' ); ?>
						</ul>
					</div><!-- #first .widget-area -->

				<?php endif; ?>

				<?php if ( is_active_sidebar( 'second-footer-widget-area' ) ) : ?>

					<div id="second" class="widget-area">
						<ul class="xoxo">
							<?php dynamic_sidebar( 'second-footer-widget-area' ); ?>
						</ul>
					</div><!-- #second .widget-area -->

				<?php endif; ?>

				<?php if ( is_active_sidebar( 'third-footer-widget-area' ) ) : ?>

					<div id="third" class="widget-area">
						<ul class="xoxo">
							<?php dynamic_sidebar( 'third-footer-widget-area' ); ?>
						</ul>
					</div><!-- #third .widget-area -->

				<?php endif; ?>

				<?php if ( is_active_sidebar( 'fourth-footer-widget-area' ) ) : ?>

					<div id="fourth" class="widget-area">
						<ul class="xoxo">
							<?php dynamic_sidebar( 'fourth-footer-widget-area' ); ?>
						</ul>
					</div><!-- #fourth .widget-area -->

				<?php endif; ?>

			</div><!-- #footer-widget-area -->
