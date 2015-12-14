<?php

/**
 * Validation callbacks
 *
 * @since 1.2
 */

function bp_docs_val_refresh_access_settings() {
	$doc_id   = isset( $_POST['doc_id'] ) ? intval( $_POST['doc_id'] ) : 0;
	$group_id = isset( $_POST['group_id'] ) ? intval( $_POST['group_id'] ) : 0;

	bp_docs_doc_settings_markup( $doc_id, $group_id );

	die();
}
add_action( 'wp_ajax_refresh_access_settings', 'bp_docs_val_refresh_access_settings' );

function bp_docs_val_refresh_associated_group() {
	$group_id = isset( $_POST['group_id'] ) ? intval( $_POST['group_id'] ) : 0;

	bp_docs_associated_group_summary( $group_id );

	die();
}
add_action( 'wp_ajax_refresh_associated_group', 'bp_docs_val_refresh_associated_group' );
