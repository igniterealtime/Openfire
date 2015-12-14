<?php

/**
 * BuddyMobile - Comment template
 *
 * This template displays a list of comments.
 *
 * @package BuddyMobile
 *
 */

?>
	<?php
		if ( post_password_required() ) :
			echo '<h3 class="comments-header">' . __('Password Protected', 'buddymobile') . '</h3>';
			echo '<p class="alert password-protected">' . __('Enter the password to view comments.', 'buddymobile') . '</p>';
			return;
		endif;

		if ( is_page() && !have_comments() && !comments_open() && !pings_open() )
			return;
	?>

	<?php if ( have_comments() ) : ?>

		<div id="comments">

			<?php
			$numTrackBacks = 0; $numComments = 0;
			foreach ( (array)$comments as $comment ) if ( get_comment_type() != "comment") $numTrackBacks++; else $numComments++;
			?>


			<ul class="pageitem">
				<li class="textbox">

			<ul class="commentlist">
			 <?php wp_list_comments(); ?>

			</ul><!-- .comment-list -->

			<?php if ( get_option( 'page_comments' ) ) : ?>

				<div class="comment-navigation paged-navigation">

					<?php paginate_comments_links(); ?>

				</div>

			<?php endif; ?>

		</div><!-- #comments -->

		</li>
		</ul>

	<?php else : ?>

		<?php if ( pings_open() && !comments_open() && is_single() ) : ?>

			<p class="comments-closed pings-open">
				<?php printf( __('Comments are closed, but <a href="%1$s" title="Trackback URL for this post">trackbacks</a> and pingbacks are open.', 'buddymobile'), trackback_url( '0' ) ); ?>
			</p>

		<?php elseif ( !comments_open() && is_single() ) : ?>

			<p class="comments-closed">
				<?php _e('Comments are closed.', 'buddymobile'); ?>
			</p>

		<?php endif; ?>

	<?php endif; ?>

		<?php if ( comments_open() ) : ?>

		<div id="respond">

		<ul class="pageitem">
			<li class="textbox">



			<div class="comment-content">

				<h3 id="reply" class="comments-header">
					<?php comment_form_title( __( 'Leave a Reply', 'buddymobile' ), __( 'Leave a Reply to %s', 'buddymobile' ), true ); ?>
				</h3>

				<p id="cancel-comment-reply">
					<?php cancel_comment_reply_link( __( 'Click here to cancel reply.', 'buddymobile' ) ); ?>
				</p>

				<?php if ( get_option( 'comment_registration' ) && !$user_ID ) : ?>

					<p class="alert">
						<?php printf( __('You must be <a href="%1$s" title="Log in">logged in</a> to post a comment.', 'buddymobile'), wp_login_url( get_permalink() ) ); ?>
					</p>

				<?php else : ?>

					<form action="<?php echo get_option( 'siteurl' ); ?>/wp-comments-post.php" method="post" id="commentform" class="standard-form">

						<?php if ( $user_ID ) : ?>

							<p class="log-in-out">
								<?php
								 if ( function_exists( 'bp_is_active' ) ) :
								printf( __('Logged in as <a href="%1$s" title="%2$s">%2$s</a>.', 'buddymobile'), bp_loggedin_user_domain(), $user_identity );

								endif;

								?> <a href="<?php echo wp_logout_url( get_permalink() ); ?>" title="<?php _e('Log out of this account', 'buddymobile'); ?>"><?php _e('Log out &rarr;', 'buddymobile'); ?></a>
							</p>

						<?php else : ?>

							<?php $req = get_option( 'require_name_email' ); ?>

							<p class="form-author">
								<label for="author"><?php _e('Name', 'buddymobile'); ?> <?php if ( $req ) : ?><span class="required"><?php _e('*', 'buddymobile'); ?></span><?php endif; ?></label>
								<input type="text" class="text-input" name="author" id="author" value="<?php echo $comment_author; ?>" size="40" tabindex="1" />
							</p>

							<p class="form-email">
								<label for="email"><?php _e('Email', 'buddymobile'); ?>  <?php if ( $req ) : ?><span class="required"><?php _e('*', 'buddymobile'); ?></span><?php endif; ?></label>
								<input type="text" class="text-input" name="email" id="email" value="<?php echo $comment_author_email; ?>" size="40" tabindex="2" />
							</p>

							<p class="form-url">
								<label for="url"><?php _e('Website', 'buddymobile'); ?></label>
								<input type="text" class="text-input" name="url" id="url" value="<?php echo $comment_author_url; ?>" size="40" tabindex="3" />
							</p>

						<?php endif; ?>

						<p class="form-textarea">
							<label for="comment"><?php _e('Comment', 'buddymobile'); ?></label>
							<textarea name="comment" id="comment" rows="4" tabindex="4"></textarea>
						</p>


						</li>


						<p class="form-submit">
							<input class="submit-comment button white" name="submit" type="submit" id="submit" tabindex="5" value="<?php _e('Submit', 'buddymobile'); ?>" />
							<?php comment_id_fields(); ?>
						</p>

						</ul>



						<div class="comment-action">
							<?php do_action( 'comment_form', $post->ID ); ?>
						</div>

					</form>

				<?php endif; ?>

			</div><!-- .comment-content -->
		</div><!-- #respond -->

		<?php endif; ?>

		<?php if ( $numTrackBacks ) : ?>
			<div id="trackbacks">

				<span class="title"><?php the_title() ?></span>

				<?php if ( 1 == $numTrackBacks ) : ?>
					<h3><?php printf( __( '%d Trackback', 'buddymobile' ), $numTrackBacks ) ?></h3>
				<?php else : ?>
					<h3><?php printf( __( '%d Trackbacks', 'buddymobile' ), $numTrackBacks ) ?></h3>
				<?php endif; ?>

				<ul id="trackbacklist">
					<?php foreach ( (array)$comments as $comment ) : ?>

						<?php if ( get_comment_type() != 'comment' ) : ?>
							<li><h5><?php comment_author_link() ?></h5><em>on <?php comment_date() ?></em></li>
	  					<?php endif; ?>
					<?php endforeach; ?>
				</ul>
			</div>
		<?php endif; ?>