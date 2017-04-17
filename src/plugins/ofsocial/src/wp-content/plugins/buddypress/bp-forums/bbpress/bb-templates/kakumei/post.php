		<div id="position-<?php post_position(); ?>">
			<div class="threadauthor">
				<?php post_author_avatar_link(); ?>
				<p>
					<strong><?php post_author_link(); ?></strong><br />
					<small><?php post_author_title_link(); ?></small>
				</p>
			</div>
			<div class="threadpost">
				<div class="post"><?php post_text(); ?></div>
				<div class="poststuff"><?php printf( __('Posted %s ago'), bb_get_post_time() ); ?> <a href="<?php post_anchor_link(); ?>">#</a> <?php bb_post_admin(); ?></div>
			</div>
		</div>