<?php
/**
 * Comments
 *
 * @package Status
 * @since 1.0
 */
?>

<?php
	if ( post_password_required() ) {
		echo '<h3 class="comments-header">' . __( 'Password Protected', 'status' ) . '</h3>';
		echo '<p class="alert password-protected">' . __( 'Enter the password to view comments.', 'status' ) . '</p>';
		return;
	}

	if ( is_page() && !have_comments() && !comments_open() && !pings_open() )
		return;

	if ( have_comments() ) :
		$num_comments = 0;
		$num_trackbacks = 0;
		foreach ( (array)$comments as $comment ) {
			if ( 'comment' != get_comment_type() )
				$num_trackbacks++;
			else
				$num_comments++;
		}
?>
	<div id="comments">

		<h3>
			<?php printf( _n( '1 response to %2$s', '%1$s responses to %2$s', $num_comments, 'status' ), number_format_i18n( $num_comments ), '<em>' . get_the_title() . '</em>' ) ?>
		</h3>

		<?php do_action( 'bp_before_blog_comment_list' ) ?>

		<ol class="commentlist">
			<?php wp_list_comments( array( 'callback' => 'status_blog_comments', 'type' => 'comment' ) ) ?>
		</ol><!-- .comment-list -->

		<?php do_action( 'bp_after_blog_comment_list' ) ?>

		<?php if ( get_option( 'page_comments' ) ) : ?>
			<div class="comment-navigation paged-navigation">
				<?php paginate_comments_links() ?>
			</div>
		<?php endif; ?>

	</div><!-- #comments -->
<?php else : ?>

	<?php if ( pings_open() && !comments_open() && ( is_single() || !is_page() ) ) : ?>
		<p class="comments-closed pings-open">
			<?php printf( __( 'Comments are closed, but <a href="%1$s" title="Trackback URL for this post">trackbacks</a> and pingbacks are open.', 'status' ), trackback_url( '0' ) ) ?>
		</p>
	<?php elseif ( !comments_open() && ( is_single() || !is_page() ) ) : ?>
		<p class="comments-closed">
			<?php _e( 'Comments are closed.', 'status' ) ?>
		</p>
	<?php endif; ?>

<?php endif; ?>

<?php if ( comments_open() ) : ?>
	<?php comment_form() ?>
<?php endif; ?>

<?php if ( !empty( $num_trackbacks ) ) : ?>
	<div id="trackbacks">
		<h3><?php printf( _n( '1 trackback', '%d trackbacks', $num_trackbacks, 'status' ), number_format_i18n( $num_trackbacks ) ) ?></h3>

		<ul id="trackbacklist">
			<?php foreach ( (array)$comments as $comment ) : ?>

				<?php if ( 'comment' != get_comment_type() ) : ?>
					<li>
						<h5><?php comment_author_link() ?></h5>
						<em>on <?php comment_date() ?></em>
					</li>
 				<?php endif; ?>

			<?php endforeach; ?>
		</ul>

	</div>
<?php endif; ?>