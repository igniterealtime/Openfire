<div id="buddypress">
	<?php if ( ! did_action( 'template_notices' ) ) : ?>
		<?php do_action( 'template_notices' ) ?>
	<?php endif ?>

	<?php include( apply_filters( 'bp_docs_header_template', bp_docs_locate_template( 'docs-header.php' ) ) ) ?>

	<?php if ( bp_docs_is_doc_edit_locked() && current_user_can( 'bp_docs_edit' ) ) : ?>
		<div class="toggleable doc-is-locked">
			<span class="toggle-switch" id="toggle-doc-is-locked"><?php _e( 'Locked', 'bp-docs' ) ?> <span class="hide-if-no-js description"><?php _e( '(click for more info)', 'bp-docs' ) ?></span></span>
			<div class="toggle-content">
				<p><?php printf( __( 'This doc is currently being edited by %1$s. In order to prevent edit conflicts, only one user can edit a doc at a time.', 'bp-docs' ), bp_docs_get_current_doc_locker_name() ) ?></p>

				<?php if ( is_super_admin() || bp_group_is_admin() ) : ?>
					<p><?php printf( __( 'Please try again in a few minutes. Or, as an admin, you can <a href="%s">force cancel</a> the edit lock.', 'bp-docs' ), bp_docs_get_force_cancel_edit_lock_link() ) ?></p>
				<?php else : ?>
					<p><?php _e( 'Please try again in a few minutes.', 'bp-docs' ) ?></p>
				<?php endif ?>
			</div>
		</div>
	<?php endif ?>

	<?php do_action( 'bp_docs_before_doc_content' ) ?>

	<div class="doc-content">
		<?php bp_docs_the_content() ?>
	</div>

	<?php do_action( 'bp_docs_after_doc_content' ) ?>

	<?php if ( bp_docs_enable_attachments() && bp_docs_doc_has_attachments() ) : ?>
		<div class="doc-attachments">
			<h3><?php _e( 'Attachments', 'bp-docs' ) ?></h3>
			<?php include ( bp_docs_locate_template( 'single/attachments.php' ) ) ?>
		</div>
	<?php endif ?>

	<div class="doc-meta">
		<?php do_action( 'bp_docs_single_doc_meta' ) ?>
	</div>

	<?php if ( apply_filters( 'bp_docs_allow_comment_section', true ) ) : ?>
		<?php comments_template( '/docs/single/comments.php' ) ?>
	<?php endif ?>
</div>

