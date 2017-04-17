<?php
		// Setup $current_poster varaible on post edit
		if ( bb_is_topic_edit() ) :
			foreach( array( 'post_author', 'post_email', 'post_url' ) as $post_author_meta )
				$current_poster[$post_author_meta] = bb_get_post_meta( $post_author_meta, $post_id );

		// Shift $current_poster values from cookie
		else :
			$current_poster               = bb_get_current_poster();
			$current_poster['post_email'] = $current_poster['post_author_email'];
			$current_poster['post_url']   = $current_poster['post_author_url'];
		endif;
?>

	<p id="post-form-author-container">
		<label for="author"><?php _e( 'Author' ); ?>
			<input type="text" name="author" id="author" size="50" tabindex="30" aria-required="true" value="<?php echo esc_attr( $current_poster['post_author'] ); ?>" />
		</label>
	</p>

	<p id="post-form-email-container">
		<label for="email"><?php _e( 'Email' ); ?>
			<input type="text" name="email" id="email" size="50" tabindex="31" aria-required="true" value="<?php echo esc_attr( $current_poster['post_email'] ); ?>" />
		</label>
	</p>

	<p id="post-form-url-container">
		<label for="url"><?php _e( 'Website' ); ?>
			<input type="text" name="url" id="url" size="50" tabindex="32" value="<?php echo esc_attr( $current_poster['post_url'] ); ?>" />
		</label>
	</p>
