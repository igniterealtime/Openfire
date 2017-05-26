<?php

class BP_Docs_TestCase extends BP_UnitTestCase {

	function setUp() {
		parent::setUp();

		require_once( dirname(__FILE__) . '/factory.php' );
		$this->factory->doc = new BP_Docs_UnitTest_Factory_For_Doc( $this->factory );

		$this->old_current_user = get_current_user_id();
	}

	public function tearDown() {
		parent::tearDown();
		$this->set_current_user( $this->old_current_user );
	}
}
