<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a><?php bb_forum_bread_crumb(); ?></div>
<div class="infobox" role="main">

<div id="topic-info">
<span id="topic_labels"><?php bb_topic_labels(); ?></span>
<h2<?php topic_class( 'topictitle' ); ?>><?php topic_title(); ?></h2>
<span id="topic_posts">(<?php topic_posts_link(); ?>)</span>
<span id="topic_voices">(<?php printf( _n( '%s voice', '%s voices', bb_get_topic_voices() ), bb_get_topic_voices() ); ?>)</span>

<ul class="topicmeta">
	<li><?php printf(__('Started %1$s ago by %2$s'), get_topic_start_time(), get_topic_author()) ?></li>
<?php if ( 1 < get_topic_posts() ) : ?>
	<li><?php printf(__('<a href="%1$s">Latest reply</a> from %2$s'), esc_attr( get_topic_last_post_link() ), get_topic_last_poster()) ?></li>
<?php endif; ?>
<?php if ( bb_is_user_logged_in() ) : ?>
	<li<?php echo $class;?> id="favorite-toggle"><?php user_favorites_link(); ?></li>
<?php endif; do_action('topicmeta'); ?>
</ul>
</div>

<?php topic_tags(); ?>

<div style="clear:both;"></div>
</div>
<?php do_action('under_title'); ?>
<?php if ($posts) : ?>
<?php topic_pages( array( 'before' => '<div class="nav">', 'after' => '</div>' ) ); ?>
<div id="ajax-response"></div>
<ol id="thread" class="list:post">

<?php foreach ($posts as $bb_post) : $del_class = post_del_class(); ?>
	<li id="post-<?php post_id(); ?>"<?php alt_class('post', $del_class); ?>>
<?php bb_post_template(); ?>
	</li>
<?php endforeach; ?>

</ol>
<div class="clearit"><br style=" clear: both;" /></div>
<p class="rss-link"><a href="<?php topic_rss_link(); ?>" class="rss-link"><?php _e('<abbr title="Really Simple Syndication">RSS</abbr> feed for this topic') ?></a></p>
<?php topic_pages( array( 'before' => '<div class="nav">', 'after' => '</div>' ) ); ?>
<?php endif; ?>
<?php if ( topic_is_open( $bb_post->topic_id ) ) : ?>
<?php post_form(); ?>
<?php else : ?>
<h2><?php _e('Topic Closed') ?></h2>
<p><?php _e('This topic has been closed to new replies.') ?></p>
<?php endif; ?>
<?php if ( bb_current_user_can( 'delete_topic', get_topic_id() ) || bb_current_user_can( 'close_topic', get_topic_id() ) || bb_current_user_can( 'stick_topic', get_topic_id() ) || bb_current_user_can( 'move_topic', get_topic_id() ) ) : ?>

<div class="admin">
<?php bb_topic_admin(); ?>
</div>

<?php endif; ?>
<?php bb_get_footer(); ?>
