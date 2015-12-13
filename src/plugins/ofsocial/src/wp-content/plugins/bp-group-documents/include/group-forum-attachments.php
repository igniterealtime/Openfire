<?php
// Exit if accessed directly
if (!defined('ABSPATH'))
    exit;

// Add group document uploading to new forum posts
add_action('bp_after_group_forum_post_new', 'bp_group_documents_forum_attachments_upload_attachment');
add_action('groups_forum_new_reply_after', 'bp_group_documents_forum_attachments_upload_attachment');

function bp_group_documents_forum_attachments_upload_attachment() {
    ?>
    <div>
        <a id="bp_group_documents_forum_upload_toggle" href="#"><?php _e('Upload document (+)', 'bp-group-documents'); ?></a>
    </div>
    <div id="bp_group_documents_forum_upload">
        <label><?php _e('Choose File:', 'bp-group-documents'); ?></label>
        <input type="file" name="bp_group_documents_file" class="bp-group-documents-file" />

        <div id="document-detail-clear" class="clear"></div>
        <div class="document-info">
            <label><?php _e('Display Name:', 'bp-group-documents'); ?></label>
            <input type="text" name="bp_group_documents_name" id="bp-group-documents-name" />

    <?php if (BP_GROUP_DOCUMENTS_SHOW_DESCRIPTIONS) { ?>
                <label><?php _e('Description:', 'bp-group-documents'); ?></label>
                <textarea name="bp_group_documents_description" id="bp-group-documents-description"></textarea>
    <?php } ?>
        </div>
    </div>
    <?php
}

// Save group documents and append link to forum topic text
add_filter('group_forum_topic_text_before_save', 'bp_group_documents_forum_attachments_topic_text', 10, 1);
add_filter('group_forum_post_text_before_save', 'bp_group_documents_forum_attachments_topic_text', 10, 1);


/**
 *
 * @param type $topic_text
 * @return type
 * @version 1.2.2, stergatu 3/10/2013, sanitize_text_field
 * @since
 */
function bp_group_documents_forum_attachments_topic_text($topic_text) {
    $bp = buddypress();

    if (!empty($_FILES)) {
        $document = new BP_Group_Documents();
        $document->user_id = get_current_user_id();
        $document->group_id = $bp->groups->current_group->id;
        /* Never trust an input box */
//        $document->name =  $_POST['bp_group_documents_name'];
//        $document->description = $_POST['bp_group_documents_description'];
        $document->name = sanitize_text_field($_POST['bp_group_documents_name']);
        $document->description = sanitize_text_field($_POST['bp_group_documents_description']);
        if ($document->save()) {
            do_action('bp_group_documents_add_success', $document);
            bp_core_add_message(__('Document successfully uploaded', 'bp-group-documents'));
            return $topic_text . bp_group_documents_forum_attachments_document_link($document);
        }
    }
    return $topic_text;
}

/* Returns html that links to a group document
 */
function bp_group_documents_forum_attachments_document_link($document) {
    $html = "<br /><a class='group-documents-title' id='group-document-link-{$document->id}' href='{$document->get_url()}' target='_blank'>{$document->name}";
    if ( get_option( 'bp_group_documents_display_file_size' ) ) {
	$html .= " <span class='group-documents-filesize'>(" . get_file_size( $document ) . ")</span>";
    }
    $html .= "</a>";

    if (BP_GROUP_DOCUMENTS_SHOW_DESCRIPTIONS && $document->description) {
        $html .= "<br /><span class='group-documents-description'>" . nl2br( stripslashes_deep( $document->description ) ) . "</span>";
    }

    return apply_filters('bp_group_documents_forum_document_link', $html, $document);
}

// Allow the id attribute in <a> tags so download counting works.
add_filter('bp_forums_allowed_tags', 'bp_group_documents_forum_attachments_allowed_tags', 10, 1);

function bp_group_documents_forum_attachments_allowed_tags($forums_allowedtags) {
    $forums_allowedtags['a']['id'] = array();

    return $forums_allowedtags;
}
