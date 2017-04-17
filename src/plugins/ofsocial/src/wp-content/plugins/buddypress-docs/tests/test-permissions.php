<?php

/**
 * @group permissions
 */
class BP_Docs_Tests_Permissions extends BP_Docs_TestCase {
	public $old_current_user;

	public function setUp() {
		$this->old_current_user = get_current_user_id();
		parent::setUp();
	}

	public function tearDown() {
		$this->set_current_user( $this->old_current_user );
		parent::tearDown();
	}

	/**
	 * @group bp_docs_user_can
	 */
	function test_loggedout_user_cannot_create() {
		$this->assertFalse( bp_docs_user_can( 'create', 0 ) );
	}

	/**
	 * @group bp_docs_user_can
	 */
	function test_loggedin_user_can_create() {
		$this->assertTrue( bp_docs_user_can( 'create', 4 ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_loggedout_user_cannot_bp_docs_create() {
		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_create' ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_loggedin_user_can_bp_docs_create() {
		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_create' ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read
	 */
	public function test_user_can_read_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_user_can_read_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_user_can_read_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_user_can_read_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );
	}

	/**
	 * @group map_meta_cap
	 */
	public function test_user_can_read_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_read', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_read', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group edit
	 */
	public function test_user_can_edit_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['edit'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group edit
	 */
	public function test_user_can_edit_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['edit'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group edit
	 */
	public function test_user_can_edit_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['edit'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group edit
	 */
	public function test_user_can_edit_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['edit'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group edit
	 */
	public function test_user_can_edit_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['edit'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_edit', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_edit', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 */
	public function test_user_can_manage_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['manage'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 */
	public function test_user_can_manage_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['manage'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 */
	public function test_user_can_manage_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['manage'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 */
	public function test_user_can_manage_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['manage'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 */
	public function test_user_can_manage_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['manage'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_manage', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group manage
	 * @ticket 485
	 */
	public function test_anyone_can_manage_doc_with_ID_0() {
		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_manage', 0 ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_manage', 0 ) );
	}

	/**
	 * @group map_meta_cap
	 * @group view_history
	 */
	public function test_user_can_view_history_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['view_history'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group view_history
	 */
	public function test_user_can_view_history_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['view_history'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group view_history
	 */
	public function test_user_can_view_history_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['view_history'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group view_history
	 */
	public function test_user_can_view_history_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['view_history'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group view_history
	 */
	public function test_user_can_view_history_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['view_history'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_view_history', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_view_history', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read_comments
	 */
	public function test_user_can_read_comments_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read_comments'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read_comments
	 */
	public function test_user_can_read_comments_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read_comments'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read_comments
	 */
	public function test_user_can_read_comments_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read_comments'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read_comments
	 */
	public function test_user_can_read_comments_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read_comments'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group read_comments
	 */
	public function test_user_can_read_comments_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['read_comments'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_read_comments', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_read_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group post_comments
	 */
	public function test_user_can_post_comments_anyone() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['post_comments'] = 'anyone';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group post_comments
	 */
	public function test_user_can_post_comments_loggedin() {
		$d = $this->factory->doc->create();
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['post_comments'] = 'loggedin';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group post_comments
	 */
	public function test_user_can_post_comments_creator() {
		$c = $this->factory->user->create();
		$d = $this->factory->doc->create( array(
			'post_author' => $c,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['post_comments'] = 'creator';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u = $this->factory->user->create();
		$this->set_current_user( $u );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$this->set_current_user( $c );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group post_comments
	 */
	public function test_user_can_post_comments_group_members() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['post_comments'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group post_comments
	 */
	public function test_user_can_post_comments_admins_mods() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );
		$doc_settings = bp_docs_get_doc_settings( $d );
		$doc_settings['post_comments'] = 'group-members';
		update_post_meta( $d, 'bp_docs_settings', $doc_settings );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_post_comments', $d ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_post_comments', $d ) );
	}

	/**
	 * @group map_meta_cap
	 * @group associate_with_group
	 */
	public function test_user_can_associate_with_group_admin() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'admin',
		) );

		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u3 = $this->factory->user->create();
		$this->add_user_to_group( $u3, $g );
		$gm3 = new BP_Groups_Member( $u3, $g );
		$gm3->promote( 'admin' );
		$this->set_current_user( $u3 );
		$this->asserttrue( current_user_can( 'bp_docs_associate_with_group', $g ) );
	}

	/**
	 * @group map_meta_cap
	 * @group associate_with_group
	 */
	public function test_user_can_associate_with_group_mod() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'mod',
		) );

		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u3 = $this->factory->user->create();
		$this->add_user_to_group( $u3, $g );
		$gm3 = new BP_Groups_Member( $u3, $g );
		$gm3->promote( 'mod' );
		$this->set_current_user( $u3 );
		$this->assertTrue( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u4 = $this->factory->user->create();
		$this->add_user_to_group( $u4, $g );
		$gm4 = new BP_Groups_Member( $u4, $g );
		$gm4->promote( 'mod' );
		$this->set_current_user( $u4 );
		$this->assertTrue( current_user_can( 'bp_docs_associate_with_group', $g ) );
	}

	/**
	 * @group map_meta_cap
	 * @group associate_with_group
	 */
	public function test_user_can_associate_with_group_member() {
		if ( ! bp_is_active( 'groups' ) ) {
			return;
		}

		$g = $this->factory->group->create();
		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'member',
		) );

		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u1 = $this->factory->user->create();
		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_associate_with_group', $g ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$this->set_current_user( $u2 );
		$this->assertTrue( current_user_can( 'bp_docs_associate_with_group', $g ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_no_group() {
		$u = $this->factory->user->create();
		$this->assertFalse( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u, 0 ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_non_group_member() {
		$u = $this->factory->user->create();
		$g = $this->factory->group->create();
		$this->assertFalse( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u, $g ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_default_can_create_value() {
		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		groups_delete_groupmeta( $g, 'bp-docs' );

		$u = $this->factory->user->create();
		$this->add_user_to_group( $u, $g );
		$this->assertTrue( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u, $g ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_member() {
		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'member',
		) );

		$u = $this->factory->user->create();
		$this->add_user_to_group( $u, $g );
		$this->assertTrue( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u, $g ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_mod() {
		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'mod',
		) );

		$u1 = $this->factory->user->create();
		$this->add_user_to_group( $u1, $g );
		$this->assertFalse( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u1, $g ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$gm2 = new BP_Groups_Member( $u2, $g );
		$gm2->promote( 'mod' );
		$this->assertTrue( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u2, $g ) );

		$u3 = $this->factory->user->create();
		$this->add_user_to_group( $u3, $g );
		$gm3 = new BP_Groups_Member( $u3, $g );
		$gm3->promote( 'admin' );
		$this->assertTrue( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u3, $g ) );
	}

	/**
	 * @group user_can_associate_doc_with_group
	 * @expectedDeprecated user_can_associate_doc_with_group
	 */
	public function test_associate_with_group_admin() {
		$g = $this->factory->group->create();
		$d = $this->factory->doc->create( array(
			'group' => $g,
		) );

		groups_update_groupmeta( $g, 'bp-docs', array(
			'can-create' => 'admin',
		) );

		$u1 = $this->factory->user->create();
		$this->add_user_to_group( $u1, $g );
		$this->assertFalse( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u1, $g ) );

		$u2 = $this->factory->user->create();
		$this->add_user_to_group( $u2, $g );
		$gm2 = new BP_Groups_Member( $u2, $g );
		$gm2->promote( 'mod' );
		$this->assertFalse( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u2, $g ) );

		$u3 = $this->factory->user->create();
		$this->add_user_to_group( $u3, $g );
		$gm3 = new BP_Groups_Member( $u3, $g );
		$gm3->promote( 'admin' );
		$this->assertTrue( BP_Docs_Groups_Integration::user_can_associate_doc_with_group( $u3, $g ) );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_no_group() {
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group', 0 ) );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_no_user() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();

		$this->set_current_user( 0 );
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_logged_in() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();

		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_group_member() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();
		$this->add_user_to_group( $u1, $g );

		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_group_mod() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();
		$this->add_user_to_group( $u1, $g );
		$gm1 = new BP_Groups_Member( $u1, $g );
		$gm1->promote( 'mod' );

		$this->set_current_user( $u1 );
		$this->assertTrue( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_group_admin() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();
		$this->add_user_to_group( $u1, $g );
		$gm1 = new BP_Groups_Member( $u1, $g );
		$gm1->promote( 'admin' );

		$this->set_current_user( $u1 );
		$this->assertTrue( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_site_admin() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();
		$u_site_admin = new WP_user( $u1 );
		$u_site_admin->add_role( 'administrator' );

		$this->set_current_user( $u1 );
		$this->assertTrue( current_user_can( 'bp_docs_dissociate_from_group', $g ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_no_group_specified() {
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();

		$this->set_current_user( $u1 );
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group' ) );

		$this->set_current_user( $old_current_user );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_within_group_logged_in() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();

		$this->set_current_user( $u1 );
		$this->go_to( bp_get_group_permalink( groups_get_group( array( 'group_id' => $g ) ) ) );
		// $this->go_to( bp_docs_get_doc_link( $post_id ) );
		$this->assertFalse( current_user_can( 'bp_docs_dissociate_from_group' ) );
	}

	/**
	 * @group map_meta_cap
	 * @group dissociate_from_group
	 */
	public function test_user_can_dissociate_from_group_within_group_mod() {
		$g = $this->factory->group->create();
		$old_current_user = get_current_user_id();
		$u1 = $this->factory->user->create();
		$gm1 = new BP_Groups_Member( $u1, $g );
		$gm1->promote( 'admin' );

		$this->set_current_user( $u1 );
		$this->go_to( bp_get_group_permalink( groups_get_group( array( 'group_id' => $g ) ) ) );
		// $this->go_to( bp_docs_get_doc_link( $post_id ) );
		$this->assertTrue( current_user_can( 'bp_docs_dissociate_from_group' ) );
	}
}
