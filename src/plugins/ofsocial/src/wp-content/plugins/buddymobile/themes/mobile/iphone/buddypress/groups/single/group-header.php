<?php

do_action( 'bp_before_group_header' );

?>


<div id="item-header-avatar">
	<a href="<?php bp_group_permalink(); ?>" title="<?php bp_group_name(); ?>">

		<?php bp_group_avatar( 'type=thumb' ); ?>

	</a>
</div><!-- #item-header-avatar -->

<div id="item-header-content">
	<h2><a href="<?php bp_group_permalink(); ?>" title="<?php bp_group_name(); ?>"><?php bp_group_name(); ?></a> <span class="highlight">(<?php bp_group_type(); ?>)</span></h2>
	<span class="activity"><?php printf( __( 'active %s', 'buddypress' ), bp_get_group_last_active() ); ?></span>

	<?php do_action( 'bp_before_group_header_meta' ); ?>

	<div id="item-meta">

		<?php bp_group_description(); ?>

		<div id="item-buttons">

			<?php do_action( 'bp_group_header_actions' ); ?>

		</div><!-- #item-buttons -->

		<?php do_action( 'bp_group_header_meta' ); ?>

	</div>
</div><!-- #item-header-content -->

<?php
do_action( 'bp_after_group_header' );
do_action( 'template_notices' );
?>