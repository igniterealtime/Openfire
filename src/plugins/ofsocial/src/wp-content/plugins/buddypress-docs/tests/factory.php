<?php

class BP_Docs_UnitTest_Factory_For_Doc extends WP_UnitTest_Factory_For_Thing {

	function __construct( $factory = null ) {
		parent::__construct( $factory );
		$this->default_generation_definitions = array(
			'post_status' => 'publish',
			'post_title' => new WP_UnitTest_Generator_Sequence( 'Doc title %s' ),
			'post_content' => new WP_UnitTest_Generator_Sequence( 'Doc content %s' ),
			'post_type' => 'bp_doc'
		);
	}

	function create_object( $args ) {
		$post_id = wp_insert_post( $args );

		if ( isset( $args['group'] ) ) {
			bp_docs_set_associated_group_id( $post_id, $args['group'] );
		}

		return $post_id;
	}

	/**
	 * @todo
	 */
	function update_object( $object, $fields ) {}

	function get_object_by_id( $post_id ) {
		return get_post( $post_id );
	}
}
