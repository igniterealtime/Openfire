			</div>
			<div class="clear"></div>
			<!-- If you like showing off the fact that your server rocks -->
			<!-- <p id="bbShowOff">
<?php
global $bbdb;
printf(
__( 'This page generated in %s seconds, using %d queries.' ),
bb_number_format_i18n( bb_timer_stop(), 2 ),
bb_number_format_i18n( $bbdb->num_queries )
);
?>
			</p> -->
			<div class="clear"></div>
		</div>
	</div>
	<div id="bbFoot">
		<p id="bbThanks">
<?php
printf(
	__( 'Thank you for using <a href="%s">bbPress</a>. | <a href="%s">Documentation</a> | <a href="%s">Feedback</a>' ),
	'http://bbpress.org/',
	'http://bbpress.org/documentation/',
	'http://bbpress.org/forums/forum/requests-and-feedback'
);
?>
		</p>
		<p id="bbVersion"><?php printf( __( 'Version %s' ), bb_get_option( 'version' ) ); ?></p>
	</div>

	<?php do_action( 'bb_admin_footer' ); ?>
</body>
</html>
