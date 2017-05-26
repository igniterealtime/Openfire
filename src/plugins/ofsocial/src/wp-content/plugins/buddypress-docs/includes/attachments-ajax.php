<?php

/**
 * AJAX handlers for attachments
 *
 * @since 1.4
 */

function bp_docs_attachment_item_markup_cb() {
	$attachment_id = isset( $_POST['attachment_id'] ) ? intval( $_POST['attachment_id'] ) : 0;
	$markup = bp_docs_attachment_item_markup( $attachment_id );
	wp_send_json_success( $markup );
}
add_action( 'wp_ajax_doc_attachment_item_markup', 'bp_docs_attachment_item_markup_cb' );

/**
 * Ajax handler to create dummy doc on creation
 *
 * @since 1.4
 */
function bp_docs_create_dummy_doc() {
	add_filter( 'wp_insert_post_empty_content', '__return_false' );
	$doc_id = wp_insert_post( array(
		'post_type' => bp_docs_get_post_type_name(),
		'post_status' => 'auto-draft',
	) );
	remove_filter( 'wp_insert_post_empty_content', '__return_false' );
	wp_send_json_success( array( 'doc_id' => $doc_id ) );
}
add_action( 'wp_ajax_bp_docs_create_dummy_doc', 'bp_docs_create_dummy_doc' );
