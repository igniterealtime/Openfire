<?php
// Exit if accessed directly
if (!defined('ABSPATH'))
    exit;

/**
 * bp_group_documents_front_cssjs()
 *
 * This function will enqueue the components css and javascript files
 * only when the front group documents page is displayed
 * @version 2 7/3/2013 stergatu, replaced hardcoded plugin directory with BP_GROUP_DOCUMENTS_DIR
 */
function bp_group_documents_front_cssjs() {
    $bp = buddypress();

    //if we're on a group page
    if ($bp->current_component == $bp->groups->slug) {
        wp_enqueue_script('bp-group-documents', WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/js/general.js', array('jquery'), BP_GROUP_DOCUMENTS_VERSION);
        wp_localize_script('bp-group-documents', 'l10nBpGrDocuments', array(
            'new_category' => __('New Category...!', 'bp-group-documents'),
            'no_file_selected' => __('You must select a file to upload!', 'bp-group-documents'),
            'sure_to_delete_document' => __('Are you sure you wish to permanently delete this document?', 'bp-group-documents'),
            'add'=>__('Add','bp-group-documents'),
        ));


        wp_register_style('bp-group-documents', WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/css/style.css', false, BP_GROUP_DOCUMENTS_VERSION);
        wp_enqueue_style('bp-group-documents');

        switch (BP_GROUP_DOCUMENTS_THEME_VERSION) {
            case '1.1':
                wp_enqueue_style('bp-group-documents-1.1', WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/css/11.css');
                break;
            case '1.2':
                //	wp_enqueue_style('bp-group-documents-1.2', WP_PLUGIN_URL . '/buddypress-group-documents/css/12.css');
                break;
        }
    }
}

//changed with chriskeeble suggestion
add_action('wp_enqueue_scripts', 'bp_group_documents_front_cssjs');

/**
 * bp_group_documents_admin_cssjs()
 *
 * This function will enqueue the css and js files for the admin back-end
 * @version 1.2.2, remove admin.js call
 * @deprecated since 1.5
 */
function bp_group_documents_admin_cssjs() {
    wp_enqueue_style('bp-group-documents-admin', WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/css/admin.css');
}

//changed with chriskeeble suggestion
add_action('admin_head', 'bp_group_documents_admin_cssjs');
