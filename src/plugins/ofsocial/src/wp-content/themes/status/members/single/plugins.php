<?php
/**
* BuddyPress - Users Plugins
*
* This is a fallback file that external plugins can use if the template they
* need is not installed in the current theme. Use the actions in this template
* to output everything your plugin needs.
 *
 * @package Status
 * @since 1.0
 */
?>

<?php get_header( 'status' ); ?>

	<div id="content-profile-headerfull">
			<?php do_action( 'bp_before_member_home_content' ); ?>

			<div id="item-header" role="complementary">

				<?php locate_template( array( 'members/single/member-header.php' ), true ); ?>

			</div><!-- #item-header -->
		</div>
		<div id="item-nav">
			<div class="item-list-tabs no-ajax" id="object-nav" role="navigation">
				<ul>

					<?php bp_get_displayed_user_nav(); ?>

					<?php do_action( 'bp_member_options_nav' ); ?>

				</ul>
			</div>
		</div><!-- #item-nav -->
	<div id="content-profile">
			<div id="item-body" role="main">

				<?php do_action( 'bp_before_member_body' ); ?>

				<div class="item-list-tabs no-ajax" id="subnav">
					<ul>

						<?php bp_get_options_nav(); ?>

						<?php do_action( 'bp_member_plugin_options_nav' ); ?>

					</ul>
				</div><!-- .item-list-tabs -->

				<h3><?php do_action( 'bp_template_title' ); ?></h3>

				<?php do_action( 'bp_template_content' ); ?>

				<?php do_action( 'bp_after_member_body' ); ?>

			</div><!-- #item-body -->

			<?php do_action( 'bp_after_member_plugin_template' ); ?>

	</div><!-- #content -->

<?php get_sidebar( 'status' ); ?>
<?php get_footer( 'status' ); ?>
