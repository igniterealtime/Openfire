<?php bb_get_header(); ?>

<div class="bbcrumb"><a href="<?php bb_uri(); ?>"><?php bb_option('name'); ?></a> &raquo; <a href="<?php user_profile_link( $user_id ); ?>"><?php echo get_user_display_name( $user_id ); ?></a> &raquo; <?php _e('Favorites'); ?></div>

<h2 id="userlogin" role="main"><?php echo get_user_display_name( $user->ID ); ?> <small>(<?php echo get_user_name( $user->ID ); ?>)</small> <?php _e( 'favorites' ); ?><?php if ( $topics ) printf( __( ' - %d' ), $favorites_total ); ?></h2>

<p><?php _e( 'Favorites allow members to create a custom <abbr title="Really Simple Syndication">RSS</abbr> feed which pulls recent replies to the topics they specify.' ); ?></p>
<?php if ( bb_current_user_can( 'edit_favorites_of', $user_id ) ) : ?>
<p><?php _e( 'To add topics to your list of favorites, just click the "Add to Favorites" link found on that topic&#8217;s page.' ); ?></p>
<?php endif; ?>

<?php if ( $topics ) : ?>

<table id="favorites">
<tr>
	<th><?php _e('Topic'); ?></th>
	<th><?php _e('Posts'); ?></th>
	<!-- <th><?php _e('Voices'); ?></th> -->
	<th><?php _e('Last Poster'); ?></th>
	<th><?php _e('Freshness'); ?></th>
<?php if ( bb_current_user_can( 'edit_favorites_of', $user_id ) ) : ?>
	<th><?php _e('Remove'); ?></th>
<?php endif; ?>
</tr>

<?php foreach ( $topics as $topic ) : ?>
<tr<?php topic_class(); ?>>
	<td><?php bb_topic_labels(); ?> <a href="<?php topic_link(); ?>"><?php topic_title(); ?></a></td>
	<td class="num"><?php topic_posts(); ?></td>
	<!-- <td class="num"><?php bb_topic_voices(); ?></td> -->
	<td class="num"><?php topic_last_poster(); ?></td>
	<td class="num"><a href="<?php topic_last_post_link(); ?>" title="<?php topic_time(array('format'=>'datetime')); ?>"><?php topic_time(); ?></a></td>
<?php if ( bb_current_user_can( 'edit_favorites_of', $user_id ) ) : ?>
	<td class="num">[<?php user_favorites_link('', array('mid'=>'&times;'), $user_id); ?>]</td>
<?php endif; ?>
</tr>
<?php endforeach; ?>
</table>

<p class="rss-link"><a href="<?php favorites_rss_link( $user_id ); ?>" class="rss-link"><?php _e('<abbr title="Really Simple Syndication">RSS</abbr> feed for these favorites'); ?></a></p>

<?php favorites_pages( array( 'before' => '<div class="nav">', 'after' => '</div>' ) ); ?>

<?php else: if ( $user_id == bb_get_current_user_info( 'id' ) ) : ?>

<p><?php _e('You currently have no favorites.'); ?></p>

<?php else : ?>

<p><?php echo get_user_name( $user_id ); ?> <?php _e('currently has no favorites.'); ?></p>

<?php endif; endif; ?>

<?php bb_get_footer(); ?>
