<?php
/**
 * Content - attachment
 *
 * @package Status
 * @since 1.0
 */
?>

<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
			<div class="author-box">		<?php echo get_avatar( get_the_author_meta( 'user_email' ), '50' ); ?>		<p><?php printf( _x( 'by %s', 'Post written by...', 'status' ), str_replace( '<a href=', '<a rel="author" href=', bp_core_get_userlink( $post->post_author ) ) ); ?></p>	</div>
	<header class="post-header">
			<h1 class="post-title"><?php the_title(); ?></h1>
		<div class="post-info">
			<?php printf( __( '%1$s <span>in %2$s</span>', 'status' ), get_the_date(), get_the_category_list( ', ' ) ); ?>
			<span class="post-utility alignright"><?php edit_post_link( __( 'Edit this entry', 'status' ) ); ?></span>
			<span class="post-action alignright">
				<a href="<?php the_permalink(); ?>" title="<?php printf( esc_attr__( 'Permalink to %s', 'status' ), the_title_attribute( 'echo=0' ) ); ?>" rel="bookmark"><?php _e( 'Permalink', 'status'); ?></a>
			</span>

		</div>
	</header>
	<div class="post-content">
		<?php echo wp_get_attachment_image( $post->ID, 'large', false, array( 'class' => 'size-large aligncenter' ) ); ?>

		<div class="entry-caption"><?php if ( !empty( $post->post_excerpt ) ) the_excerpt(); ?></div>
		<?php the_content(); ?>
	</div>

		<div class="post-info">
				<span class="post-tags">
					<?php
					if ( wp_attachment_is_image() ) :
						$metadata = wp_get_attachment_metadata();
						printf( __( 'Full size is %s pixels', 'buddypress' ),
							sprintf( '<a href="%1$s" title="%2$s">%3$s &times; %4$s</a>',
								wp_get_attachment_url(),
								esc_attr( __( 'Link to full size image', 'buddypress' ) ),
								$metadata['width'],
								$metadata['height']
							)
						);
					endif;
				?>
				</span>
		</div>
</article>