<?php

class BP_Docs_Attachments {
	protected $doc_id;
	protected $override_doc_id;

	protected $is_private;
	protected $htaccess_path;

	function __construct() {
		if ( ! bp_docs_enable_attachments() ) {
			return;
		}

		add_action( 'template_redirect', array( $this, 'catch_attachment_request' ), 20 );
		add_filter( 'redirect_canonical', array( $this, 'redirect_canonical' ), 10, 2 );
		add_filter( 'upload_dir', array( $this, 'filter_upload_dir' ) );
		add_action( 'bp_docs_doc_saved', array( $this, 'check_privacy' ) );
		add_filter( 'wp_handle_upload_prefilter', array( $this, 'maybe_create_rewrites' ) );
		add_action( 'wp_enqueue_scripts', array( $this, 'enqueue_scripts' ), 20 );

		add_action( 'pre_get_posts', array( $this, 'filter_gallery_posts' ) );
		add_action( 'pre_get_posts', array( $this, 'filter_directory_posts' ) );

		// Add the tags filter markup
		add_filter( 'bp_docs_filter_types', array( $this, 'filter_type' ) );
		add_filter( 'bp_docs_filter_sections', array( $this, 'filter_markup' ) );

		// Icon display
		add_filter( 'icon_dir', 'BP_Docs_Attachments::icon_dir' );
		add_filter( 'icon_dir_uri', 'BP_Docs_Attachments::icon_dir_uri' );

		// Catch delete request
		add_action( 'bp_actions', array( $this, 'catch_delete_request' ) );

		// Ensure that all logged-in users have the 'upload_files' cap
		add_filter( 'map_meta_cap', array( __CLASS__, 'map_meta_cap' ), 10, 4 );

		// Admin notice about directory accessibility
		add_action( 'admin_init', array( $this, 'admin_notice_init' ) );

		require( dirname( __FILE__ ) . '/attachments-ajax.php' );
	}

	/**
	 * @todo
	 *
	 * - Must have script for recreating .htaccess files when:
	 *	- top-level Docs slug changes
	 *	- single Doc slug changes
	 */

	/**
	 * Catches bp-attachment requests and serves attachmens if appropriate
	 *
	 * @since 1.4
	 */
	function catch_attachment_request() {
		if ( ! empty( $_GET['bp-attachment'] ) ) {

			$fn = $_GET['bp-attachment'];

			// Sanity check - don't do anything if this is not a Doc
			if ( ! bp_docs_is_existing_doc() ) {
				return;
			}

			if ( ! $this->filename_is_safe( $fn ) ) {
				wp_die( __( 'File not found.', 'bp-docs' ) );
			}

			$uploads = wp_upload_dir();
			$filepath = $uploads['path'] . DIRECTORY_SEPARATOR . $fn;

			if ( ! file_exists( $filepath ) ) {
				wp_die( __( 'File not found.', 'bp-docs' ) );
			}

			error_reporting( 0 );
			ob_end_clean();

			$headers = $this->generate_headers( $filepath );

			// @todo Support xsendfile?
			// @todo Better to send header('Location') instead?
			//       Generate symlinks like Drupal. Needs FollowSymLinks
			foreach( $headers as $name => $field_value ) {
				@header("{$name}: {$field_value}");
			}

			readfile( $filepath );
			exit();
		}
	}

	/**
	 * If redirecting from a 'p' URL to a rewritten URL, retain 'bp-attachment' param
	 *
	 * @since 1.6.0
	 *
	 * @param string $redirect_url URL as calculated by redirect_canonical
	 * @param string $requested_url Originally requested URL.
	 * @return string
	 */
	public function redirect_canonical( $redirect_url, $requested_url ) {
		if ( isset( $_GET['p'] ) && isset( $_GET['bp-attachment'] ) && false !== strpos( $requested_url, 'bp-attachments' ) ) {
			$redirect_url = add_query_arg( 'bp-attachment', $_GET['bp-attachment'], $redirect_url );
		}

		return $redirect_url;
	}

	/**
	 * Attempts to customize upload_dir with our attachment paths
	 *
	 * @todo There's a quirk in wp_upload_dir() that will create the
	 *   attachment directory if it doesn't already exist. This normally
	 *   isn't a problem, because wp_upload_dir() is generally only called
	 *   when a credentialed user is logged in and viewing the admin side.
	 *   But the current code will force a folder to be created the first
	 *   time the doc is viewed at all. Not sure whether this merits fixing
	 *
	 * @since 1.4
	 */
	function filter_upload_dir( $uploads ) {
		if ( ! $this->get_doc_id() ) {
			return $uploads;
		}

		$uploads = $this->mod_upload_dir( $uploads );

		return $uploads;
	}

	/**
	 * After a Doc is saved, check to see whether any action is necessary
	 *
	 * @since 1.4
	 */
	public function check_privacy( $query ) {
		if ( empty( $query->doc_id ) ) {
			return;
		}

		$this->set_doc_id( $query->doc_id );

		if ( $this->get_is_private() ) {
			$this->create_htaccess();
		} else {
			$this->delete_htaccess();
		}
	}

	/**
	 * Delete an htaccess file for the current Doc
	 *
	 * @since 1.4
	 * @return bool
	 */
	public function delete_htaccess() {
		if ( ! $this->get_doc_id() ) {
			return false;
		}

		$path = $this->get_htaccess_path();
		if ( file_exists( $path ) ) {
			unlink( $path );
		}

		return true;
	}

	/**
	 * Create rewrite rules for upload directory, if appropriate.
	 *
	 * As a hack, we've hooked to wp_handle_upload_prefilter. We don't
	 * actually do anything with the passed value; we just need a place
	 * to hook in reliably before the file is written.
	 *
	 * @since 1.6.0
	 *
	 * @param $file
	 * @return $file
	 */
	public function maybe_create_rewrites( $file ) {
		global $is_apache;

		if ( ! $this->get_doc_id() ) {
			return $file;
		}

		if ( ! $this->get_is_private() ) {
			return $file;
		}

		if ( $is_apache ) {
			$this->create_htaccess();
		}

		return $file;
	}

	/**
	 * Creates an .htaccess file in the appropriate upload dir, if appropriate
	 *
	 * @since 1.4
	 * @param $file
	 * @return $file
	 */
	public function maybe_create_htaccess( $file ) {
		if ( ! $this->get_doc_id() ) {
			return $file;
		}

		if ( ! $this->get_is_private() ) {
			return $file;
		}

		$this->create_htaccess();

		return $file;
	}

	/**
	 * Creates an .htaccess for a Doc upload directory
	 *
	 * No check happens here to see whether an .htaccess is necessary. Make
	 * sure you check $this->get_is_private() before running.
	 *
	 * @since 1.4
	 */
	public function create_htaccess() {
		$htaccess_path = $this->get_htaccess_path();

		$rules = $this->generate_rewrite_rules();

		if ( ! empty( $rules ) ) {
			if ( ! function_exists( 'insert_with_markers' ) ) {
				require_once( ABSPATH . 'wp-admin/includes/misc.php' );
			}
			insert_with_markers( $htaccess_path, 'BuddyPress Docs', $rules );
		}
	}

	/**
	 * Generates a path to htaccess for the Doc in question
	 *
	 * @since 1.4
	 * @return string
	 */
	public function get_htaccess_path() {
		if ( empty( $this->htaccess_path ) ) {
			$upload_dir = wp_upload_dir();
			$this->htaccess_path = $upload_dir['path'] . DIRECTORY_SEPARATOR . '.htaccess';
		}

		return $this->htaccess_path;
	}

	/**
	 * Check whether the current Doc is private ('read' != 'anyone')
	 *
	 * @since 1.4
	 * @return bool
	 */
	public function get_is_private() {
//		if ( is_null( $this->is_private ) ) {
			$doc_id = $this->get_doc_id();
			$doc_settings = bp_docs_get_doc_settings( $doc_id );
			$this->is_private = isset( $doc_settings['read'] ) && 'anyone' !== $doc_settings['read'];
//		}

		return $this->is_private;
	}

	/**
	 * Set a doc id for this object
	 *
	 * This is a hack that lets you manually bypass the doc sniffing in
	 * get_doc_id()
	 */
	public function set_doc_id( $doc_id ) {
		$this->override_doc_id = intval( $doc_id );
	}

	/**
	 * Attempt to auto-detect a doc ID
	 */
	public function get_doc_id() {

		if ( ! empty( $this->override_doc_id ) ) {

			$this->doc_id = $this->override_doc_id;

		} else {

			if ( ! defined( 'DOING_AJAX' ) || ! DOING_AJAX ) {
				if ( bp_docs_is_existing_doc() ) {
					$this->doc_id = get_queried_object_id();
				}
			} else {
				// AJAX
				if ( isset( $_REQUEST['query']['auto_draft_id'] ) ) {

					$this->doc_id = (int) $_REQUEST['query']['auto_draft_id'];

				} else if ( isset( $_REQUEST['action'] ) && 'upload-attachment' == $_REQUEST['action'] && isset( $_REQUEST['post_id'] ) ) {

					$maybe_doc = get_post( $_REQUEST['post_id'] );
					if ( bp_docs_get_post_type_name() == $maybe_doc->post_type ) {
						$this->doc_id = $maybe_doc->ID;
					}

				} else {
					// In order to check if this is a doc, must check ajax referer
					$this->doc_id = $this->get_doc_id_from_url( wp_get_referer() );
				}
			}
		}

		return $this->doc_id;
	}

	public function catch_delete_request() {
		if ( ! bp_docs_is_existing_doc() ) {
			return;
		}

		if ( ! isset( $_GET['delete_attachment'] ) ) {
			return;
		}

		if ( ! current_user_can( 'bp_docs_edit' ) ) {
			return;
		}

		$attachment_id = intval( $_GET['delete_attachment'] );

		check_admin_referer( 'bp_docs_delete_attachment_' . $attachment_id );

		if ( wp_delete_attachment( $attachment_id ) ) {
			bp_core_add_message( __( 'Attachment deleted', 'bp-docs' ) );
		} else {
			bp_core_add_message( __( 'Could not delete attachment', 'bp-docs' ), 'error' );
		}

		wp_redirect( wp_get_referer() );
	}

	/**
	 * Get a Doc's ID from its URL
	 *
	 * Note that this only works for existing Docs, because (duh) new Docs
	 * don't yet have a URL
	 *
	 * @since 1.4
	 * @param string $url
	 * @return int $doc_id
	 */
	public static function get_doc_id_from_url( $url ) {
		$doc_id = null;
		$url = untrailingslashit( $url );
		$edit_location = strrpos( $url, BP_DOCS_EDIT_SLUG );
		if ( false !== $edit_location && BP_DOCS_EDIT_SLUG == substr( $url, $edit_location ) ) {
			$doc_id = url_to_postid( substr( $url, 0, $edit_location - 1 ) );
		}
		return $doc_id;
	}

	/**
	 * Filter upload_dir to customize Doc upload locations
	 *
	 * @since 1.4
	 * @return array $uploads
	 */
	function mod_upload_dir( $uploads ) {
		$subdir = '/bp-attachments/' . $this->doc_id;

		$uploads['subdir'] = $subdir;
		$uploads['path'] = $uploads['basedir'] . $subdir;
		$uploads['url'] = $uploads['baseurl'] . '/bp-attachments/' . $this->doc_id;

		return $uploads;
	}

	function enqueue_scripts() {
		if ( bp_docs_is_doc_edit() || bp_docs_is_doc_create() ) {
			wp_enqueue_script( 'bp-docs-attachments', plugins_url( BP_DOCS_PLUGIN_SLUG . '/includes/js/attachments.js' ), array( 'media-editor', 'media-views', 'bp-docs-js' ), false, true );

			wp_localize_script( 'bp-docs-attachments', 'bp_docs_attachments', array(
				'upload_title'  => __( 'Upload File', 'bp-docs' ),
				'upload_button' => __( 'OK', 'bp-docs' ),
			) );
		}
	}

	/**
	 * Filter the posts query on attachment pages, to ensure that only the
	 * specific Doc's attachments show up in the Gallery
	 *
	 * Hooked to 'pre_get_posts'. Bail out if we're not in a Gallery
	 * request for a Doc
	 *
	 * @since 1.4
	 */
	public function filter_gallery_posts( $query ) {
		// This is also a surrogate check for DOING_AJAX
		if ( ! isset( $_POST['action'] ) || 'query-attachments' !== $_POST['action'] ) {
			return;
		}

		$post_id = isset( $_POST['post_id'] ) ? intval( $_POST['post_id'] ) : 0;

		if ( ! $post_id ) {
			return;
		}

		$post = get_post( $post_id );

		if ( empty( $post ) || is_wp_error( $post ) || bp_docs_get_post_type_name() !== $post->post_type ) {
			return;
		}

		// Phew
		$query->set( 'post_parent', $_REQUEST['post_id'] );
	}

	public function filter_directory_posts( $query ) {
		if ( bp_docs_get_post_type_name() !== $query->get( 'post_type' ) ) {
			return;
		}

		remove_action( 'pre_get_posts', array( $this, 'filter_directory_posts' ) );

		$has_attachment = isset( $_REQUEST['has-attachment'] ) && in_array( $_REQUEST['has-attachment'], array( 'yes', 'no' ) ) ? $_REQUEST['has-attachment'] : '';

		if ( $has_attachment ) {
			$post__in = $query->get( 'post__in' );
			$att_posts = $this->get_docs_with_attachments();
			$query_arg = 'yes' === $has_attachment ? 'post__in' : 'post__not_in';
			$query->set( $query_arg, array_merge( (array) $post__in, (array) $att_posts ) );
		}

		add_action( 'pre_get_posts', array( $this, 'filter_directory_posts' ) );
	}

	public function get_docs_with_attachments() {
		$atts = get_posts( array(
			'update_meta_cache' => false,
			'update_term_cache' => false,
			'post_type' => 'attachment',
			'post_parent__in' => bp_docs_get_doc_ids_accessible_to_current_user(),
			'posts_per_page' => -1,
		) );

		return array_unique( wp_list_pluck( $atts, 'post_parent' ) );
	}

	/**
	 * Generates the rewrite rules to be put in .htaccess of the upload dir
	 *
	 * @since 1.4
	 * @return array $rules One per line, to be put together by insert_with_markers()
	 */
	public function generate_rewrite_rules() {
		$rules = array();
		$doc_id = $this->get_doc_id();

		if ( ! $doc_id ) {
			return $rules;
		}

		$url = bp_docs_get_doc_link( $doc_id );
		$url_parts = parse_url( $url );

		if ( ! empty( $url_parts['path'] ) ) {
			$rules = array(
				'RewriteEngine On',
				'RewriteBase ' . $url_parts['path'],
				'RewriteRule (.+) ?bp-attachment=$1 [R=302,NC]',
			);
		}

		return apply_filters( 'bp_docs_attachments_generate_rewrite_rules', $rules, $this );
	}

	/**
	 * Check to see whether a filename is safe
	 *
	 * This is used to sanitize file paths passed via $_GET params
	 *
	 * @since 1.4
	 * @param string $filename Filename to validate
	 * @return bool
	 */
	public static function filename_is_safe( $filename ) {
		// WP's core function handles most sanitization
		if ( $filename !== sanitize_file_name( $filename ) ) {
			return false;
		}

		// No leading dots
		if ( 0 === strpos( $filename, '.' ) ) {
			return false;
		}

		// No directory walking means no slashes
		$filename_parts = pathinfo( $filename );
		if ( $filename_parts['basename'] !== $filename ) {
			return false;
		}

		// Check filetype
		$ft = wp_check_filetype( $filename );
		if ( empty( $ft['ext'] ) ) {
			return false;
		}

		return true;
	}

	/**
	 * Generate download headers
	 *
	 * @since 1.4
	 * @param string $filename Full path to file
	 * @return array Headers in key=>value format
	 */
	public static function generate_headers( $filename ) {
		// Disable compression
		if ( function_exists( 'apache_setenv' ) ) {
			@apache_setenv( 'no-gzip', 1 );
		}
		@ini_set( 'zlib.output_compression', 'Off' );

		// @todo Make this more configurable
		$headers = wp_get_nocache_headers();

		// Content-Disposition
		$filename_parts = pathinfo( $filename );
		$headers['Content-Disposition'] = 'attachment; filename="' . $filename_parts['basename'] . '"';

		// Content-Type
		$filetype = wp_check_filetype( $filename );
		$headers['Content-Type'] = $filetype['type'];

		// Content-Length
		$filesize = filesize( $filename );
		$headers['Content-Length'] = $filesize;

		return $headers;
	}

	public static function icon_dir( $dir ) {
		if ( bp_docs_is_docs_component() ) {
			$dir = BP_DOCS_INSTALL_PATH . 'lib/nuvola';
		}
		return $dir;
	}

	public static function icon_dir_uri( $url ) {
		if ( bp_docs_is_docs_component() ) {
			$url = plugins_url( BP_DOCS_PLUGIN_SLUG . '/lib/nuvola' );
		}
		return $url;
	}

	public static function filter_type( $types ) {
		$types[] = array(
			'slug' => 'attachments',
			'title' => __( 'Attachments', 'bp-docs' ),
			'query_arg' => 'has-attachment',
		);
		return $types;
	}

	public static function filter_markup() {
		$has_attachment = isset( $_REQUEST['has-attachment'] ) && in_array( $_REQUEST['has-attachment'], array( 'yes', 'no' ) ) ? $_REQUEST['has-attachment'] : '';
		$form_action = ( is_ssl() ? 'https://' : 'http://' ) . $_SERVER['HTTP_HOST'] . $_SERVER['REQUEST_URI'];
		foreach ( $_GET as $k => $v ) {
			$form_action = remove_query_arg( $k, $form_action );
		}
		?>

		<div id="docs-filter-section-attachments" class="docs-filter-section<?php if ( $has_attachment ) : ?> docs-filter-section-open<?php endif ?>">
			<form method="get" action="<?php echo $form_action ?>">
				<label for="has-attachment"><?php _e( 'Has attachment?', 'bp-docs' ) ?></label>
				<select id="has-attachment" name="has-attachment">
					<option value="yes"<?php selected( $has_attachment, 'yes' ) ?>><?php _e( 'Yes', 'bp-docs' ) ?></option>
					<option value="no"<?php selected( $has_attachment, 'no' ) ?>><?php _e( 'No', 'bp-docs' ) ?></option>
					<option value=""<?php selected( $has_attachment, '' ) ?>><?php _e( 'Doesn&#8217;t matter', 'bp-docs' ) ?></option>
				</select>
				<input type="submit" value="<?php _e( 'Filter', 'bp-docs' ) ?>" />
			</form>
		</div>

		<?php
	}

	/**
	 * Give users the 'edit_post' and 'upload_files' cap, when appropriate
	 *
	 * @since 1.4
	 *
	 * @param array $caps The mapped caps
	 * @param string $cap The cap being mapped
	 * @param int $user_id The user id in question
	 * @param $args
	 * @return array $caps
	 */
	public static function map_meta_cap( $caps, $cap, $user_id, $args ) {
		if ( 'upload_files' !== $cap && 'edit_post' !== $cap ) {
			return $caps;
		}

		$maybe_user = new WP_User( $user_id );
		if ( ! is_a( $maybe_user, 'WP_User' ) || empty( $maybe_user->ID ) ) {
			return $caps;
		}

		$is_doc = false;

		// DOING_AJAX is not set yet, so we cheat
		$is_ajax = isset( $_SERVER['REQUEST_METHOD'] ) && 'POST' === $_SERVER['REQUEST_METHOD'] && 'async-upload.php' === substr( $_SERVER['REQUEST_URI'], strrpos( $_SERVER['REQUEST_URI'], '/' ) + 1 );

		if ( $is_ajax ) {
			// WordPress sends the 'media-form' nonce, which we use
			// as an initial screen
			$nonce   = isset( $_REQUEST['_wpnonce'] ) ? stripslashes( $_REQUEST['_wpnonce'] ) : '';
			$post_id = isset( $_REQUEST['post_id'] ) ? intval( $_REQUEST['post_id'] ) : '';

			if ( wp_verify_nonce( $nonce, 'media-form' ) && $post_id ) {
				$post   = get_post( $post_id );

				// The dummy Doc created during the Create
				// process should pass this test, in addition to
				// existing Docs
				$is_doc = isset( $post->post_type ) && bp_docs_get_post_type_name() === $post->post_type;
			}
		} else {
			$is_doc = bp_docs_is_existing_doc() || bp_docs_is_doc_create();
		}

		if ( $is_doc ) {
			$caps = array( 'exist' );

			// Since we've already done the permissions check,
			// we can filter future current_user_can() checks on
			// this pageload
			add_filter( 'map_meta_cap', array( __CLASS__, 'map_meta_cap_supp' ), 10, 4 );
		}

		return $caps;
	}

	/**
	 * Make sure the current user has the 'edit_post' and 'upload_files' caps, when appropriate
	 *
	 * We do the necessary permissions checks in self::map_meta_cap(). If
	 * the checks pass, then we can blindly hook this filter without doing
	 * the permissions logic again. *Do not call this filter directly.* I'd
	 * mark it private but it's not possible given the way that WP's filter
	 * system works.
	 *
	 * @since 1.4
	 */
	public static function map_meta_cap_supp( $caps, $cap, $user_id, $args ) {
		if ( 'upload_files' !== $cap && 'edit_post' !== $cap ) {
			return $caps;
		}

		return array( 'exist' );
	}

	public function admin_notice_init() {
		if ( defined( 'DOING_AJAX' ) && DOING_AJAX ) {
			return;
		}

		if ( ! current_user_can( 'delete_users' ) ) {
			return;
		}

		// If the notice is being disabled, mark it as such and bail
		if ( isset( $_GET['bpdocs-disable-attachment-notice'] ) ) {
			check_admin_referer( 'bpdocs-disable-attachment-notice' );
			bp_update_option( 'bp_docs_disable_attachment_notice', 1 );
			return;
		}

		// If the notice has already been disabled, bail
		if ( 1 == bp_get_option( 'bp_docs_disable_attachment_notice' ) ) {
			return;
		}

		// Nothing to see here
		if ( $this->check_is_protected() ) {
			return;
		}

		add_action( 'admin_notices', array( $this, 'admin_notice' ) );
		add_action( 'network_admin_notices', array( $this, 'admin_notice' ) );
	}

	public function admin_notice() {
		global $is_apache, $is_nginx, $is_IIS, $is_iis7;

		$dismiss_url = add_query_arg( 'bpdocs-disable-attachment-notice', '1', $_SERVER['REQUEST_URI'] );
		$dismiss_url = wp_nonce_url( $dismiss_url, 'bpdocs-disable-attachment-notice' );

		if ( ! bp_is_root_blog() ) {
			switch_to_blog( bp_get_root_blog_id() );
		}

		$upload_dir = $this->mod_upload_dir( wp_upload_dir() );
		$att_url = str_replace( get_option( 'home' ), '', $upload_dir['url'] );

		restore_current_blog();

		if ( $is_nginx ) {
			$help_url = 'https://github.com/boonebgorges/buddypress-docs/wiki/Attachment-Privacy#nginx';

			$help_p  = __( 'It looks like you are running <strong>nginx</strong>. We recommend the following setting in your site configuration file:', 'bp-docs' );
			$help_p .= '<pre><code>location ' . $att_url . ' {
    rewrite ^.*' . str_replace( '/wp-content/', '', $att_url ) . '([0-9]+)/(.*) /?p=$1&bp-attachment=$2 permanent;
}
</code></pre>';
		}

		if ( $is_iis7 ) {
			$help_url = 'https://github.com/boonebgorges/buddypress-docs/wiki/Attachment-Privacy#iis7';

			$help_p  = __( 'It looks like you are running <strong>IIS 7</strong>. We recommend the following setting in your Web.config file:', 'bp-docs' );
			$help_p .= '<pre><code>&lt;rule name="buddypress-docs-attachments">
    &lt;match url="^' . $att_url . '([0-9]+)/(.*)$"/>
        &lt;conditions>
	    &lt;add input="{REQUEST_FILENAME}" matchType="IsFile" negate="false"/>
	&lt;/conditions>
    &lt;action type="Redirect" url="?p={R:1}&amp;bp-attachment={R:2}"/>
&lt;/rule> </code></pre>';
		}

		if ( $is_apache ) {
			$help_url = 'https://github.com/boonebgorges/buddypress-docs/wiki/Attachment-Privacy#apache';
			$help_p = __( 'It looks like you are running <strong>Apache</strong>. The most likely cause of your problem is that the <code>AllowOverride</code> directive has been disabled, either globally (<code>httpd.conf</code>) or in a <code>VirtualHost</code> definition. Contact your host for assistance.', 'bp-docs' );
		}

		?>

		<div class="message error">
			<p><?php _e( '<strong>Your BuddyPress Docs attachments directory is publicly accessible.</strong> Doc attachments will not be properly protected from direct viewing, even if the parent Docs are non-public.', 'bp-docs' ) ?></p>

			<?php if ( $help_p ) : ?>
				<p><?php echo $help_p ?></p>
			<?php endif ?>

			<?php if ( $help_url ) : ?>
				<p><?php printf( __( 'See <a href="%s">this wiki page</a> for more information.', 'bp-docs' ), $help_url ) ?></p>
			<?php endif ?>

			<p><a href="<?php echo $dismiss_url ?>"><?php _e( 'Dismiss this message', 'bp-docs' ) ?></a></p>
		</div>
		<?php
	}

	/**
	 * Test whether the attachment upload directory is protected.
	 *
	 * We create a dummy file in the directory, and then test to see
	 * whether we can fetch a copy of the file with a remote request.
	 *
	 * @since 1.6.0
	 *
	 * @return True if protected, false if not.
	 */
	public function check_is_protected( $force_check = true ) {
		global $is_apache;

		// Fall back on cached value if it exists
		if ( ! $force_check ) {
			$is_protected = bp_get_option( 'bp_docs_attachment_protection' );
			if ( '' !== $is_protected ) {
				return (bool) $is_protected;
			}
		}

		$uploads = wp_upload_dir();

		$test_dir = $uploads['basedir'] . DIRECTORY_SEPARATOR . 'bp-attachments' . DIRECTORY_SEPARATOR . '0';
		$test_file_dir = $test_dir . DIRECTORY_SEPARATOR . 'test.html';
		$test_text = 'This is a test of the Protected Attachment feature of BuddyPress Docs. Please do not remove.';

		if ( ! file_exists( $test_file_dir ) ) {
			if ( ! file_exists( $test_dir ) ) {
				wp_mkdir_p( $test_dir );
			}

			// Create an .htaccess, if we can
			if ( $is_apache ) {

				// Fake the doc ID
				$this->doc_id = 0;

				$rules = array(
					'RewriteEngine On',
					'RewriteBase /',
					'RewriteRule (.+) ?bp-attachment=$1 [R=302,NC]',
				);

				if ( ! empty( $rules ) ) {
					if ( ! file_exists( 'insert_with_markers' ) ) {
						require_once( ABSPATH . 'wp-admin/includes/misc.php' );
					}
					insert_with_markers( $test_dir . DIRECTORY_SEPARATOR . '.htaccess', 'BuddyPress Docs', $rules );
				}
			}

			// Make a dummy file
			file_put_contents( $test_dir . DIRECTORY_SEPARATOR . 'test.html', $test_text );
		}

		$test_url = $uploads['baseurl'] . '/bp-attachments/0/test.html';
		$r = wp_remote_get( $test_url );

		// If the response body includes our test text, we have a problem
		$is_protected = true;
		if ( ! is_wp_error( $r ) && $r['body'] === $test_text ) {
			$is_protected = false;
		}

		// Cache
		$cache = $is_protected ? '1' : '0';
		bp_update_option( 'bp_docs_attachment_protection', $cache );

		return $is_protected;
	}
}

/**
 * Are attachments enabled?
 *
 * @since 1.5
 * @return bool
 */
function bp_docs_enable_attachments() {
	$enabled = get_option( 'bp-docs-enable-attachments', 'yes' );
	return apply_filters( 'bp_docs_enable_attachments', 'yes' === $enabled );
}

/**
 * Are attachment downloads protected?
 */
function bp_docs_attachment_protection( $force_check = false ) {
	return buddypress()->bp_docs->attachments->check_is_protected( $force_check );
}
