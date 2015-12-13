<?php
// Last sync [WP11537]

/**
 * Taxonomy API
 *
 * @package WordPress
 * @subpackage Taxonomy
 * @since 2.3.0
 */

function get_object_taxonomies($object_type) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_object_taxonomies($object_type);
}

function get_taxonomy( $taxonomy ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_taxonomy( $taxonomy );
}

function is_taxonomy( $taxonomy ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->is_taxonomy( $taxonomy );
}

function is_taxonomy_hierarchical($taxonomy) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->is_taxonomy_hierarchical($taxonomy);
}

function register_taxonomy( $taxonomy, $object_type, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->register_taxonomy( $taxonomy, $object_type, $args );
}

function get_objects_in_term( $terms, $taxonomies, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_objects_in_term( $terms, $taxonomies, $args );
}

function &get_term($term, $taxonomy, $output = OBJECT, $filter = 'raw') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_term($term, $taxonomy, $output, $filter);
}

function get_term_by($field, $value, $taxonomy, $output = OBJECT, $filter = 'raw') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_term_by($field, $value, $taxonomy, $output, $filter);
}

function get_term_children( $term, $taxonomy ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_term_children( $term, $taxonomy );
}

function get_term_field( $field, $term, $taxonomy, $context = 'display' ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_term_field( $field, $term, $taxonomy, $context );
}

function get_term_to_edit( $id, $taxonomy ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_term_to_edit( $id, $taxonomy );
}

function &get_terms($taxonomies, $args = '') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_terms($taxonomies, $args);
}

function is_term($term, $taxonomy = '') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->is_term($term, $taxonomy );
}

function sanitize_term($term, $taxonomy, $context = 'display') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->sanitize_term($term, $taxonomy, $context );
}

function sanitize_term_field($field, $value, $term_id, $taxonomy, $context) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->sanitize_term_field($field, $value, $term_id, $taxonomy, $context);
}

function wp_count_terms( $taxonomy, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->count_terms( $taxonomy, $args );
}

function wp_delete_object_term_relationships( $object_id, $taxonomies ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->delete_object_term_relationships( $object_id, $taxonomies );
}

function wp_delete_term( $term, $taxonomy, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->delete_term( $term, $taxonomy, $args );
}

function wp_get_object_terms($object_ids, $taxonomies, $args = array()) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_object_terms($object_ids, $taxonomies, $args );
}

function wp_insert_term( $term, $taxonomy, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->insert_term( $term, $taxonomy, $args );
}

function wp_set_object_terms($object_id, $terms, $taxonomy, $append = false) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->set_object_terms($object_id, $terms, $taxonomy, $append);
}

function wp_unique_term_slug($slug, $term) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->unique_term_slug($slug, $term);
}

function wp_update_term( $term, $taxonomy, $args = array() ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->update_term( $term, $taxonomy, $args );
}

function wp_defer_term_counting($defer=NULL) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->defer_term_counting($defer);
}

function wp_update_term_count( $terms, $taxonomy, $do_deferred=false ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->update_term_count( $terms, $taxonomy, $do_deferred );
}

function wp_update_term_count_now( $terms, $taxonomy ) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->update_term_count_now( $terms, $taxonomy );
}

function clean_object_term_cache($object_ids, $object_type) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->clean_object_term_cache($object_ids, $object_type);
}

function clean_term_cache($ids, $taxonomy = '') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->clean_term_cache($ids, $taxonomy);
}

function &get_object_term_cache($id, $taxonomy) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->get_object_term_cache($id, $taxonomy);
}

function update_object_term_cache($object_ids, $object_type) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->update_object_term_cache($object_ids, $object_type);
}

function update_term_cache($terms, $taxonomy = '') {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->update_term_cache($terms, $taxonomy);
}

function _get_term_hierarchy($taxonomy) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->_get_term_hierarchy($taxonomy);
}

function &_get_term_children($term_id, $terms, $taxonomy) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->_get_term_children($term_id, $terms, $taxonomy);
}

function _pad_term_counts(&$terms, $taxonomy) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->_pad_term_counts(&$terms, $taxonomy);
}

function is_object_in_term($object_id, $taxonomy, $terms = null) {
	global $wp_taxonomy_object;
	return $wp_taxonomy_object->is_object_in_term($object_id, $taxonomy, $terms);
}
