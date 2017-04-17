<?php
/**
 * @package WordPress
 * @subpackage onetone
 */

	
	if ( post_password_required() ) { ?>
		<p class="nocomments"><?php _e('This post is password protected. Enter the password to view comments.', 'onetone'); ?></p> 
	<?php
		return;
	}
?>

<!-- You can start editing here. -->

<?php if ( have_comments() ) : ?>
	<h3 id="comments"><?php comments_number(__('No comment', 'onetone'), __('Has one comment', 'onetone'), __('% comments', 'onetone'));?> <?php printf(__('to &#8220;%s&#8221;', 'onetone'), the_title('', '', false)); ?></h3>
<div class="upcomment"><?php _e('You can ','onetone'); ?><a id="leaverepond" href="#comments"><?php _e('leave a reply','onetone'); ?></a>  <?php _e(' or ','onetone'); ?> <a href="<?php trackback_url(true); ?>" rel="trackback"><?php _e('Trackback','onetone'); ?></a> <?php _e('this post.','onetone'); ?></div>
	<ol id="thecomments" class="commentlist">
	<?php wp_list_comments('type=comment&callback=onetone_comment');?>
	</ol>

<!-- comments pagenavi Start. -->
	<?php
	if (get_option('page_comments')) {
		$comment_pages = paginate_comments_links('echo=0');
		if ($comment_pages) {
?>
		<div id="commentnavi">
			<span class="pages"><?php _e('Comment pages', 'onetone'); ?></span>
			<div id="commentpager">
				<?php echo $comment_pages; ?>
				
			</div>
			<div class="fixed"></div>
		</div>
<?php
		}
	}
?>

 <?php else : // this is displayed if there are no comments so far ?>

	<?php if ( comments_open() ) : ?>
		<!-- If comments are open, but there are no comments. -->

	 <?php else : // comments are closed ?>
		<!-- If comments are closed. -->
		<p class="nocomments"><?php //_e('Comments are closed.', 'onetone'); ?></p>

	<?php endif; ?>
<?php endif; ?>


<?php if ( comments_open() ) : ?>

<div id="respond" class="respondbg">

<?php if ( get_option('comment_registration') && !is_user_logged_in() ) : ?>
<p><?php printf(__('You must be <a href="%s">logged in</a> to post a comment.', 'onetone'), wp_login_url( get_permalink() )); ?></p>
<?php else : ?>
<?php 
$commenter = wp_get_current_commenter();
$req = get_option( 'require_name_email' );
$aria_req = ( $req ? " aria-required='true'" : '' );
global $required_text;
$commenter['comment_author']        = ($commenter['comment_author'] == "")?"Name":$commenter['comment_author'];
$commenter['comment_author_email']  = ($commenter['comment_author_email'] == "")?"Email":$commenter['comment_author_email'];
$commenter['comment_author_url']    = ($commenter['comment_author_url'] == "")?"Website":$commenter['comment_author_url'];
$comments_args = array(
         'comment_notes_before' => '<p class="comment-notes">' .
    __( 'Your email address will not be published.', 'onetone' ) . ( $req ? $required_text : '' ) .
    '</p>',
        // change the title of the reply section
        'title_reply'=>__('Write a Reply or Comment', 'onetone'),
        // remove "Text or HTML to be displayed after the set of comment fields"
        'comment_notes_after' => '',
        // redefine your own textarea (the comment body)
        'comment_field' => '<div class="clear"></div>
<div id="comment-textarea"><textarea id="comment" name="comment" onFocus="if(this.value==\'Message\'){this.value=\'\'}" onBlur="if(this.value==\'\'){this.value=\'Message\'}" class="textarea-comment" aria-required="true">Message</textarea></div>',
		'fields' => apply_filters( 'comment_form_default_fields', array(

    'author' =>
      '<p><input id="author" class="input-name" name="author" onFocus="if(this.value==\'Name\'){this.value=\'\'}" onBlur="if(this.value==\'\'){this.value=\'Name\'}" type="text" value="' . esc_attr( $commenter['comment_author'] ) .
      '" size="22"' . $aria_req . ' /></p>',

    'email' =>
      '<p><input id="email" class="input-name" name="email" onFocus="if(this.value==\'Email\'){this.value=\'\'}" onBlur="if(this.value==\'\'){this.value=\'Email\'}" type="text" value="' . esc_attr(  $commenter['comment_author_email'] ) .
      '" size="22"' . $aria_req . ' /></p>',

    'url' =>
      '<p><input id="url" class="input-name" name="url" onFocus="if(this.value==\'Website\'){this.value=\'\'}" onBlur="if(this.value==\'\'){this.value=\'Website\'}" type="text" value="' . esc_attr( $commenter['comment_author_url'] ) .
      '" size="22" /></p>'
    ))
);
?>
<?php comment_form($comments_args);?>

<?php endif; // If registration required and not logged in ?>
</div>

<?php endif; // if you delete this the sky will fall on your head ?>
