<form class="search-form" role="search" action="<?php bb_uri('search.php', null, BB_URI_CONTEXT_FORM_ACTION); ?>" method="get">
	<p>
		<label class="hidden" for="q"><?php _e('Search:'); ?></label>
		<input class="text" type="text" size="14" maxlength="100" name="q" id="q" />
		<input class="submit" type="submit" value="<?php echo esc_attr__( 'Search &raquo;' ); ?>" />
	</p>
</form>
