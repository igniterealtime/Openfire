<?php
/**
 * Content - aside
 *
 * @package Status
 * @since 1.0
 */
?>

<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
	<div class="author-box">	
		<?php echo get_avatar( get_the_author_meta( 'user_email' ), '50' ); ?>		
		<p><?php printf( _x( 'by %s', 'Post written by...', 'status' ), str_replace( '<a href=', '<a rel="author" href=', bp_core_get_userlink( $post->post_author ) ) ); ?></p>	
	</div>
	<div class="post-format"><?php _e( 'Aside', 'status' ); ?></div> 
	<div class="post-content">
		<?php the_content( __( 'View the pictures &rarr;', 'status' ) ); ?>
		<?php wp_link_pages( array( 'before' => '<div class="page-link">' . __( '<span>Pages:</span>', 'status' ), 'after' => '</div>' ) ); ?>
		<a href="<?php the_permalink(); ?>" title="<?php printf( esc_attr__( 'Permalink to %s', 'status' ), the_title_attribute( 'echo=0' ) ); ?>" rel="bookmark"><?php _e( 'Permalink', 'status'); ?></a>
	</div>
</article>