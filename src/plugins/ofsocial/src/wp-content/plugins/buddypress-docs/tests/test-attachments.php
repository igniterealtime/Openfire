<?php

class BP_Docs_Attachments_Tests extends BP_Docs_TestCase {
	function test_filename_is_safe() {
		$this->assertTrue( BP_Docs_Attachments::filename_is_safe( 'foo.jpg' ) );

		// No traversing
		$this->assertFalse( BP_Docs_Attachments::filename_is_safe( '../foo.jpg' ) );

		// No leading dots
		$this->assertFalse( BP_Docs_Attachments::filename_is_safe( '.foo.jpg' ) );

		// No slashes
		$this->assertFalse( BP_Docs_Attachments::filename_is_safe( 'foo/bar.jpg' ) );

		// No forbidden extensions
		$this->assertFalse( BP_Docs_Attachments::filename_is_safe( 'foo.php' ) );

	}

	/**
	 * There's no great unit test way to do this
	 */
	function test_htaccess_creation() {
		$doc_id = $this->factory->doc->create();

		$uploads = wp_upload_dir();
		$subdir = DIRECTORY_SEPARATOR . 'bp-attachments' . DIRECTORY_SEPARATOR . $doc_id;
		$dir = $uploads['basedir'] . $subdir;
		$htaccess_path = $dir . DIRECTORY_SEPARATOR . '.htaccess';

		// for cleanup later
		$dir_exists = file_exists( $dir );
		$htaccess_exists = file_exists( $htaccess_path );

		if ( $dir_exists ) {
			rename( $dir, $dir . '.bu' );
		} else if ( $htaccess_exists ) {
			rename( $htaccess_path, $htaccess_path . '.bu' );
		}

		$settings = bp_docs_get_doc_settings();

		// Test private first
		$settings['read'] = 'loggedin';
		update_post_meta( $doc_id, 'bp_docs_settings', $settings );
		bp_docs_update_doc_access( $doc_id, 'loggedin' );

		$query = new BP_Docs_Query;
		$query->doc_id = $doc_id;

		do_action( 'bp_docs_doc_saved', $query );

		$this->assertTrue( file_exists( $htaccess_path ) );

		// Clean up and test with public
		unlink( $htaccess_path );
		rmdir( $dir );

		$settings['read'] = 'anyone';
		update_post_meta( $doc_id, 'bp_docs_settings', $settings );
		bp_docs_update_doc_access( $doc_id, 'anyone' );

		$query2 = new BP_Docs_Query;
		$query2->doc_id = $doc_id;

		do_action( 'bp_docs_doc_saved', $query2 );

		$this->assertFalse( file_exists( $htaccess_path ) );

		// Clean up
		@unlink( $htaccess_path );
		@rmdir( $dir );

		if ( $dir_exists ) {
			rename( $dir . '.bu', $dir );
		} else if ( $htaccess_exists ) {
			rename( $htaccess_path . '.bu', $htaccess_path );
		}
	}
}
