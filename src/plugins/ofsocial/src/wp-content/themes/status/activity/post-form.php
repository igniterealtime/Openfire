<?php
/**
* BuddyPress - Activity Post Form
 *
 * @package Status
 * @since 1.0
 */
?>

<div id="whats-new-declare" class="clearfix">
<form action="<?php bp_activity_post_form_action(); ?>" method="post" id="whats-new-form" name="whats-new-form" role="complementary">
	<?php do_action( 'bp_before_activity_post_form' ); ?>
	<section id="whats-new-about">
		<div id="whats-new-avatar">
			<a href="<?php echo bp_loggedin_user_domain(); ?>">
				<?php bp_loggedin_user_avatar( 'width=' . bp_core_avatar_thumb_width() . '&height=' . bp_core_avatar_thumb_height() ); ?>
			</a>
		</div>
		<div id="whats-new-wrapper">
			<div id="whats-new-tail"></div>
			<div id="whats-new-textarea">
				<textarea name="whats-new" id="whats-new" cols="50" rows="10"><?php if ( isset( $_GET['r'] ) ) : ?>@<?php echo esc_attr( $_GET['r'] ); ?> <?php endif; ?></textarea>
			</div>
		</div>
	</section>
	<section id="whats-new-content">
		<div id="whats-new-options">
			<div id="whats-new-submit">
				<input type="submit" name="aw-whats-new-submit" id="aw-whats-new-submit" value="<?php _e( 'Post Update', 'status' ); ?>" class="submitbutton"/>
			</div>
			<?php if ( bp_is_active( 'groups' ) && !bp_is_my_profile() && !bp_is_group() ) : ?>
				<div id="whats-new-post-in-box">
					<?php _e( 'Post in', 'status' ) ?>:
					<select id="whats-new-post-in" name="whats-new-post-in">
						<option selected="selected" value="0"><?php _e( 'My Profile', 'status' ); ?></option>
						<?php if ( bp_has_groups( 'user_id=' . bp_loggedin_user_id() . '&type=alphabetical&max=100&per_page=100&populate_extras=0' ) ) :
							while ( bp_groups() ) : bp_the_group(); ?>
								<option value="<?php bp_group_id(); ?>"><?php bp_group_name(); ?></option>
							<?php endwhile;
						endif; ?>
					</select>
				</div>
				<input type="hidden" id="whats-new-post-object" name="whats-new-post-object" value="groups" />
			<?php elseif ( bp_is_group_home() ) : ?>
				<input type="hidden" id="whats-new-post-object" name="whats-new-post-object" value="groups" />
				<input type="hidden" id="whats-new-post-in" name="whats-new-post-in" value="<?php bp_group_id(); ?>" />
			<?php endif; ?>
			<?php do_action( 'bp_activity_post_form_options' ); ?>
		</div><!-- #whats-new-options -->
	</section><!-- #whats-new-content -->
	<?php wp_nonce_field( 'post_update', '_wpnonce_post_update' ); ?>
	<?php do_action( 'bp_after_activity_post_form' ); ?>
</form><!-- #whats-new-form -->
</div>