<?php

/**
 * Created by PhpStorm.
 * User: udit
 * Date: 18/02/14
 * Time: 4:30 PM
 */
class RT_WP_TestCase extends WP_UnitTestCase {
	/**
	 * Ensure that the plugin has been installed and activated.
	 */
	function test_plugin_activated() {
		$this->assertTrue( is_plugin_active( 'rtMedia/index.php' ) );
	}

}
