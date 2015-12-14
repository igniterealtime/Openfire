<?php

if ( ! defined( 'BP_TESTS_DIR' ) ) {
	define( 'BP_TESTS_DIR', dirname( __FILE__ ) . '/../../buddypress/tests/phpunit' );
}

if ( file_exists( BP_TESTS_DIR . '/bootstrap.php' ) ) :

	require_once getenv( 'WP_DEVELOP_DIR' ) . '/tests/phpunit/includes/functions.php';

	function _bootstrap_bpdocs() {
		// Make sure BP is installed and loaded first
		require BP_TESTS_DIR . '/includes/loader.php';

		// Then load BP Docs
		require dirname( __FILE__ ) . '/../loader.php';
	}
	tests_add_filter( 'muplugins_loaded', '_bootstrap_bpdocs' );

	// We need pretty permalinks for some tests
	function _set_permalinks() {
		update_option( 'permalink_structure', '/%year%/%monthnum%/%day%/%postname%/' );
	}
	tests_add_filter( 'init', '_set_permalinks', 1 );

	// We need pretty permalinks for some tests
	function _flush() {
		flush_rewrite_rules();
	}
	tests_add_filter( 'init', '_flush', 1000 );

	require getenv( 'WP_DEVELOP_DIR' ) . '/tests/phpunit/includes/bootstrap.php';

	// Load the BP test files
	require BP_TESTS_DIR . '/includes/testcase.php';

	// include our testcase
	require( dirname(__FILE__) . '/bp-docs-testcase.php' );

endif;
