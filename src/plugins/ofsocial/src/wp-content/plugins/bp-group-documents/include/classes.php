<?php

// Exit if accessed directly
if (!defined('ABSPATH'))
    exit;

class BP_Group_Documents {

    public $id; //int
    public $user_id; //int
    public $group_id; //int
    public $created_ts; //unix timestamp
    public $modified_ts; //unix timestamp
    public $file; //varchar
    public $name; //varchar
    public $description; //text
    public $featured; //bool
    public $download_count; //int

    /**
     * __construct()
     *
     * The constructor will either create a new empty object if no ID is set, or fill the object
     * with a row from the table, or the passed parameters, if an ID is provided.
     */

    public function __construct($id = null, $params = false) {
        global $wpdb;

        if ($id && ctype_digit($id)) {
            $this->id = $id;
            if ($params) {
                $this->populate_passed($params);
            } else {
                $this->populate($this->id);
            }
        }
    }

    /**
     * populate()
     *
     * This method will populate the object with a row from the database, based on the
     * ID passed to the constructor.
     */
    private function populate() {
        global $wpdb;

        if ($row = $wpdb->get_row($wpdb->prepare("SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE id = %d", $this->id))) {
            foreach ($this as $field => $value) {
                $this->$field = $row->$field;
            }
        }
    }

    /**
     * populate_passed()
     *
     * This method will populate the object with the passed parameters,
     * saving a call to the database
     */
    private function populate_passed($params) {

        //if checkbox in unchecked, nothing will be present
        //turn absense of "true" into a "false"
        if ( ! isset( $params['featured'] ) ) {
	    $params['featured'] = false;
	}
	foreach ($this as $key => $value) {
            if ( isset( $params[$key] ) ) {
		$this->$key = $params[$key];
	    }
	}
    }

    /**
     * populate_by_file()
     *
     * this will populate the object's properties based
     * on the passed file name.  it will return false if
     * the name is not found
     */
    public function populate_by_file($file) {
        global $wpdb;
	if ($row = $wpdb->get_row($wpdb->prepare("SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE file LIKE '%s'", $file))) {
            foreach ($this as $field => $value) {
                $this->$field = $row->$field;
            }
            return true;
        }
        return false;
    }

    /**
     * save()
     *
     * This method will save an object to the database. It will dynamically switch between
     * INSERT and UPDATE depending on whether or not the object already exists in the database.
     */
    public function save($check_file_upload = true) {
        global $wpdb;

	do_action('bp_group_documents_data_before_save', $this);

        if ($this->id) {
            // Update
            $result = $wpdb->query($wpdb->prepare(
                            "UPDATE " . BP_GROUP_DOCUMENTS_TABLE . " SET
						modified_ts = %d,
						name = %s,
						description = %s,
						featured = %d
					WHERE id = %d", time(), $this->name, $this->description, $this->featured, $this->id
            ));
        } else {
            // Save
            if ($check_file_upload) {
                if (!$this->UploadFile()) {
                    return false;
                }
            }

            $result = $wpdb->query($wpdb->prepare(
                            "INSERT INTO " . BP_GROUP_DOCUMENTS_TABLE . " (
					user_id,
					group_id,
					created_ts,
					modified_ts,
					file,
					name,
					description,
					featured
				) VALUES (
					%d, %d, %d, %d, %s, %s, %s, %d
				)", $this->user_id, $this->group_id, time(), time(), $this->file, $this->name, $this->description, $this->featured
            ));
        }

        if (!$result)
            return false;

        if (!$this->id) {
            $this->id = $wpdb->insert_id;
        }

        do_action('bp_group_documents_data_after_save', $this);

        return $result;
    }

    /**
     * increment_download_count()
     *
     * Adds one to the download count for the current document
     */
    public function increment_download_count() {
        global $wpdb;
	return $wpdb->query($wpdb->prepare("UPDATE " . BP_GROUP_DOCUMENTS_TABLE . " SET download_count = (download_count + 1) WHERE id = %d", $this->id));
    }

    /**
     * delete()
     *
     * This method will delete the corresponding row for an object from the database.
     */
    public function delete() {
        global $wpdb;

        if ($this->current_user_can('delete')) {
            if ($this->file && file_exists($this->get_path(1)))
                @unlink($this->get_path(1));

            return $wpdb->query($wpdb->prepare("DELETE FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE id = %d", $this->id));
        }
    }

    private function UploadFile() {

        //check that file exists
        if (!$_FILES['bp_group_documents_file']['name']) {
            bp_core_add_message(__('Whoops!  There was no file selected for upload.', 'bp-group-documents'), 'error');
            return false;
        }
        //check that file has an allowed extension
        if (!bp_group_documents_check_ext($_FILES['bp_group_documents_file']['name'])) {
            bp_core_add_message(__('The type of document submitted is not allowed', 'bp-group-documents'), 'error');
            return false;
        }

        //if there was any upload errors, spit them out
        if ($_FILES['bp_group_documents_file']['error']) {
            switch ($_FILES['bp_group_documents_file']['error']) {
                case UPLOAD_ERR_INI_SIZE:
                    bp_core_add_message(__('There was a problem; your file is larger than is allowed by the site administrator.', 'bp-group-documents'), 'error');
                    break;
                case UPLOAD_ERR_PARTIAL:
                    bp_core_add_message(__('There was a problem; the file was only partially uploaded.', 'bp-group-documents'), 'error');
                    break;
                case UPLOAD_ERR_NO_FILE:
                    bp_core_add_message(__('There was a problem; no file was found for the upload.', 'bp-group-documents'), 'error');
                    break;
                case UPLOAD_ERR_NO_TMP_DIR:
                    bp_core_add_message(__('There was a problem; the temporary folder for the file is missing.', 'bp-group-documents'), 'error');
                    break;
                case UPLOAD_ERR_CANT_WRITE:
                    bp_core_add_message(__('There was a problem; the file could not be saved.', 'bp-group-documents'), 'error');
                    break;
            }
            return false;
        }

        //if the user didn't specify a display name, use the file name (before the timestamp)
        if (!$this->name)
            if (get_magic_quotes_gpc()) {
                $this->name = stripslashes(basename($_FILES['bp_group_documents_file']['name']));
            } else {
                $this->name = basename($_FILES['bp_group_documents_file']['name']);
            }

        $this->file = apply_filters('bp_group_documents_filename_in', basename($_FILES['bp_group_documents_file']['name']));

        $file_path = $this->get_path(0, 1);

        if (move_uploaded_file($_FILES['bp_group_documents_file']['tmp_name'], $file_path)) {
            return true;
        } else {
            bp_core_add_message(__('There was a problem saving your file, please try again.', 'bp-group-documents'), 'error');
            return false;
        }
    }

    /*
     * current_user_can()
     *
     * When passed an action, it returns true if the user has the privilages
     * to perfrom that action and false if they do not
     * @version 1.2.2
     * v1.2.1, stergatu add group_id variable in order to make it work for every group and not only current group
     * @param type $action (add, edit, delete)
     * @param type $group_id
     * @return boolean
     */

    public function current_user_can($action, $group_id = false) {
        $bp = buddypress();

	if (!$group_id) {
            $group_id = $bp->groups->current_group->id;
        }
        $user_id = get_current_user_id();

        if (is_super_admin($user_id)) {
            return true;
        }

        if (groups_is_user_admin($user_id, $group_id)) {
            return true;
        }
        $user_is_owner = ($this->user_id == $user_id );
        switch ($action) {
            case 'add':
                switch (get_option('bp_group_documents_upload_permission')) {
                    case 'mods_decide':
                        switch (groups_get_groupmeta($group_id, 'bp_group_documents_upload_permission')) {
                            case 'mods_only':
                                if (groups_is_user_mod($user_id, $group_id))
                                    return true;
                                break;
                            case 'members':
                            default:
                                if (groups_is_user_member($user_id, $group_id))
                                    return true;
                                break;
                        }
                        break;
                    case 'mods_only':
                        if (groups_is_user_mod($user_id, $group_id))
                            return true;
                        break;
                    case 'members':
                    default:
                        if (groups_is_user_member($user_id, $group_id))
                            return true;
                        break;
                }
                break;
            case 'edit':
                if ((groups_is_user_mod($user_id, $group_id)) ||
                        ((groups_is_user_member($user_id, $group_id)) && $user_is_owner)) {
                    return true;
                }
                break;
            case 'delete':
                if ((groups_is_user_mod($user_id, $group_id)) ||
                        (groups_is_user_member($user_id, $group_id) && $user_is_owner)) {
                    return true;
                }
                break;
        }
        return false;
    }

//    /*
//     * current_user_can()
//     *
//     * When passed an action, it returns true if the user has the privilages
//     * to perfrom that action and false if they do not
//     * @version 2, stergatu add group_id variable in order to make it work for every group and not only current group
//     */
//
//    public function current_user_can($action, $group_id = false) {
//        global $bp;
//
//        if (!group_id) {
//            if (bp_group_is_admin()) {
//                return true;
//            }
//        }
//
//        $user_is_owner = ($this->user_id == get_current_user_id() );
//
//        switch ($action) {
//            case 'add':
//                switch (get_option('bp_group_documents_upload_permission')) {
//                    case 'mods_decide':
//                        //version 2
//                        //      switch (groups_get_groupmeta($bp->groups->current_group->id, 'bp_group_documents_upload_permission')) {
//                        switch (groups_get_groupmeta($bp->groups->current_group->id, 'bp_group_documents_upload_permission')) {
//                            case 'mods_only':
//                                if (bp_group_is_mod($bp->groups->current_group))
//                                    return true;
//                                break;
//                            case 'members':
//                            default:
//                                if (bp_group_is_member($bp->groups->current_group))
//                                    return true;
//                                break;
//                        }
//                        break;
//                    case 'mods_only':
//                        if (bp_group_is_mod($bp->groups->current_group))
//                            return true;
//                        break;
//                    case 'members':
//                    default:
//                        if (bp_group_is_member($bp->groups->current_group))
//                            return true;
//                        break;
//                }
//                break;
//            case 'edit':
//                if (bp_group_is_mod($bp->groups->current_group) ||
//                        (bp_group_is_member($bp->groups->current_group) && $user_is_owner)) {
//                    return true;
//                }
//                break;
//            case 'delete':
//                if (bp_group_is_mod($bp->groups->current_group) ||
//                        (bp_group_is_member($bp->groups->current_group) && $user_is_owner)) {
//                    return true;
//                }
//                break;
//        }
//        return false;
//    }

    /*
     * url()
     *
     * returns the full url of the document
     * if $legacy_check is true (default) the function
     * will check past locations if the file is not found
     */

    public function url($legacy_check = 1) {
        echo $this->get_url($legacy_check);
    }

    public function get_url($legacy_check = 1) {

        //preferred place for documents - in the upload folder, sorted by group
        if (function_exists('bp_core_avatar_upload_path')) {
            $document_url = str_replace(WP_CONTENT_DIR, WP_CONTENT_URL, bp_core_avatar_upload_path()) . '/group-documents/' . $this->group_id . '/' . $this->file;
        } else {
            $path = get_blog_option(BP_ROOT_BLOG, 'upload_path'); //wp-content/blogs.dir/1/files
            $document_url = WP_CONTENT_URL . str_replace('wp-content', '', $path);
            $document_url .= '/group-documents/' . $this->group_id . '/' . $this->file;
        }

        //I've  noticed a couple cases on wp-single installs that the using the avatar path (above) returns
        //only a relative link.  Check for that, and append the base domain on if that's the case.
        if ('wp-content' == substr($document_url, 0, 10))
            $document_url = get_bloginfo('home') . '/' . $document_url;

        if ($legacy_check) {

            //this is the server path of the $document_url above
            $document_path = $this->get_path();
            if (!file_exists($document_path)) {

                //check legacy override
                if (defined('BP_GROUP_DOCUMENTS_FILE_URL')) {
                    $document_url = BP_GROUP_DOCUMENTS_FILE_URL . $this->file;
                } else { //if not there, check legacy location
                    $document_url = WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/documents/' . $this->file;
                }
            }
        }
        return apply_filters('bp_group_documents_file_url', $document_url, $this->group_id, $this->file);
    }

    /*     * *
     * path()
     *
     * returns the full server path of the document
     *
     * If $legacy check is true, the function will attempt to check
     * past locations if existing doc is not found (used for retrieval).
     *
     * If $create_folders is true, it will recursively create the path
     * to the new file (used for assignment).
     */

    public function path($legacy_check = 0, $create_folders = 0) {
        echo $this->get_path($legacy_check, $create_folders);
    }

    /**
     *
     * @param type $legacy_check
     * @param type $create_folders
     * @return type
     * @version 2, 30/4/2013, stergatu in order to hardening the security
     */
    public function get_path($legacy_check = 0, $create_folders = 0) {

        /*         * *
         * place 'group-documents' on the same level as 'group-avatars'
         * organize docs within group sub-folders
         */
        if (function_exists('bp_core_avatar_upload_path')) { //bp 1.2 and later
            $document_dir = bp_core_avatar_upload_path() . '/group-documents/' . $this->group_id;
        } else { //bp 1.1
            $path = get_blog_option(BP_ROOT_BLOG, 'upload_path'); //wp-content/blogs.dir/1/files
            $document_dir = WP_CONTENT_DIR . str_replace('wp-content', '', $path);
            $document_dir .= '/group-documents/' . $this->group_id;
        }

        $this->create_dir_or_htaccess($document_dir);

        /* ideal location - use this if possible */
        $document_path = $document_dir . '/' . $this->file;

        /*         * *
         * if we're getting the existing file to display, it may not be there
         * if file is not there, check in legacy locations
         */
        if ($legacy_check && !file_exists($document_path)) {

            /* check legacy override */
            if (defined('BP_GROUP_DOCUMENTS_FILE_PATH'))
                $document_path = BP_GROUP_DOCUMENTS_FILE_PATH . $this->file;

            /* if not there, check legacy default */
            else
                $document_path = WP_PLUGIN_DIR . '/' . BP_GROUP_DOCUMENTS_DIR . '/documents/' . $this->file;
        }

        return apply_filters('bp_group_documents_file_path', $document_path, $this->group_id, $this->file);
    }

    /**
     * Prints documents categories
     * * @since version 0.5.4
     * @version 3, 8/12/2014 fix category link
     * v2, 12/11/2014, category link added
     * v1, 21/5/2013, stergatu
     */
    public function categories() {
	$bp = buddypress();
	$toprint = '';
        $categories_of_document = $this->get_document_categories();
	$group = groups_get_group( array( 'group_id' => $this->group_id ) );

	if (!empty($categories_of_document)) {
            if (!is_wp_error($categories_of_document)) {
                _e('In category:', 'bp-group-documents');
                foreach ($categories_of_document as $term) {
                    $toprint.=' <b><a href="' . bp_get_group_permalink( $group ) . BP_GROUP_DOCUMENTS_SLUG . '?category=' . $term->term_id . '">' . wp_kses( stripslashes( $term->name ), wp_kses_allowed_html( 'post' ) ) . '</a></b>,';
		}
                echo substr($toprint, 0, -1) . '. <br/>';
            }
        }
    }

    /**
     * Fetches documents categories
     * * @since version 0.5.4
     * @version 1, 21/5/2013, stergatu
     */
    public function get_document_categories() {
        return wp_get_object_terms($this->id, 'group-documents-category');
    }

    /*
     * icon()
     *
     * gets the file extension from the documents, and displays the applicable
     * icon inside of a link to the actual document
     */

    public function icon() {
        if ($icon_url = $this->get_icon()) {
            echo '<a class="bp-group-documents-icon" id="group-document-icon-' . $this->id . '" href="' . $this->get_url() . '" target="_blank"><img class="bp-group-documents-icon" src="' . $icon_url . '" alt="" /></a>';
        }
    }

    /**
     *
     * @return boolean
     * @todo, 1/5/2013, make it search in a relative folder for icon images
     */
    public function get_icon() {

        $icons = array(
            'adp' => 'page_white_database.png',
            'as' => 'page_white_actionscript.png',
            'avi' => 'film.png',
            'bash' => 'script.png',
            'bz' => 'package.png',
            'bz2' => 'package.png',
            'c' => 'page_white_c.png',
            'cf' => 'page_white_coldfusion.png',
            'cpp' => 'page_white_cplusplus.png',
            'cs' => 'page_white_csharp.png',
            'css' => 'page_white_code.png',
            'deb' => 'package.png',
            'doc' => 'page_white_word.png',
            'docx' => 'page_white_word.png',
            'eps' => 'page_white_vector.png',
            'exe' => 'application_xp_terminal.png',
            'fh' => 'page_white_freehand.png',
            'fl' => 'page_white_flash.png',
            'gif' => 'picture.png',
            'gz' => 'package.png',
            'htm' => 'page_white_code.png',
            'html' => 'page_white_code.png',
            'iso' => 'cd.png',
            'java' => 'page_white_cup.png',
            'jpeg' => 'picture.png',
            'jpg' => 'picture.png',
            'json' => 'page_white_code.png',
            'm4a' => 'music.png',
            'mov' => 'film.png',
            'mdb' => 'page_white_database.png',
            'mp3' => 'music.png',
			'mp4' => 'mp4.png',
            'mpeg' => 'film.png',
            'msp' => 'page_white_paintbrush',
            'ods' => 'application_view_columns.png',
            'odt' => 'page_white_text.png',
            'ogg' => 'music.png',
            'perl' => 'script.png',
            'pdf' => 'page_white_acrobat.png',
            'php' => 'page_white_php.png',
            'png' => 'picture.png',
            'ppt' => 'page_white_powerpoint.png',
            'pps' => 'page_white_powerpoint.png',
            'pptx' => 'page_white_powerpoint.png',
			'ppsx' => 'ppsx.gif',
            'ps' => 'page_white_paintbrush.png',
            'rb' => 'page_white_ruby.png',
            'rtf' => 'page_white_text.png',
            'sh' => 'script.png',
            'sql' => 'database.png',
            'swf' => 'page_white_flash.png',
            'tar' => 'package.png',
            'txt' => 'page_white_text.png',
            'wav' => 'music.png',
            'xls' => 'page_white_excel.png',
            'xlsx' => 'page_white_excel.png',
            'xml' => 'page_white_code.png',
            'zip' => 'page_white_zip.png',
        );


        $extension = substr($this->file, (strrpos($this->file, ".") + 1));
        $extension = strtolower($extension);

        if (!isset($icons[$extension]))
            return false;

        $img_folder = WP_PLUGIN_URL . '/' . BP_GROUP_DOCUMENTS_DIR . '/images/icons/';

        $img_url = $img_folder . $icons[$extension];

        return apply_filters('bp_group_documents_get_icon', $img_url);
    }

    /* Static Functions */

    /**
     * Get documents ids for the current group
     * @global type $wpdb
     * @return type
     * @version 2, 12/11/2014
     */
    public static function get_ids_in_current_group() {
        global $wpdb;
	$bp = buddypress();

	$group_id = $bp->groups->current_group->id;

        return $wpdb->get_col($wpdb->prepare("SELECT id FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE group_id = %d", $group_id));
    }

    /**
     *
     * @global type $wpdb
     * @param type $group_id
     * @param type $category
     * @return type
     * @version 1.2.2.
     */
    public static function get_total($group_id, $category = false) {
        global $wpdb;
	$sql = "SELECT COUNT(*) FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE group_id = %d ";
        if ($category) {
            //grab all object id's in the passed category
            $category_ids = get_objects_in_term($category, 'group-documents-category');

            if (!empty($category_ids)) {
                $in_clause = '(' . implode(',', array_map('absint', $category_ids)) . ') ';
                $sql .= "AND id IN " . $in_clause;
            }
        }

        $result = $wpdb->get_var($wpdb->prepare($sql, $group_id));
        return $result;
    }

    /**
     *
     * @global type $wpdb
         * @param type $group_id
     * @param type $category
     * @param type $sort
     * @param type $order
     * @param type $start
     * @param type $items
     * @return type
     */
    public static function get_list_by_group($group_id, $category = 0, $sort = 0, $order = 0, $start = 0, $items = 0) {
        global $wpdb;

        // if these parameters aren't passed, grab the entire list
        if (!$category && !$sort) {

            $result = $wpdb->get_results($wpdb->prepare("SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE group_id = %d ORDER BY name ASC", $group_id), ARRAY_A);

            return $result;
        }

        //convert from 1-based paging to 0-based SQL limit
        --$start;

        //grab all object id's in the passed category
        $category_ids = get_objects_in_term($category, 'group-documents-category');

        $sql = "SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE group_id = %d ";
        if (!empty($category_ids)) {
            $in_clause = '(' . implode(',', $category_ids) . ') ';
            $sql .= "AND id IN " . $in_clause;
        }
        $sql .= "ORDER BY $sort $order LIMIT %d, %d";

        $result = $wpdb->get_results($wpdb->prepare($sql, $group_id, $start, $items), ARRAY_A);

        return $result;
    }

    /**
     *
     * @global type $wpdb
     * @param type $num
     * @param type $group_filter
     * @param type $featured
     * @return type
     * @version 1.2.2
     */
    public static function get_list_for_newest_widget($num, $group_filter = 0, $featured = 0) {
        global $wpdb;
	$bp = buddypress();

	if ($group_filter || $featured) {
            $sql = "SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE 1=1 ";
            if ( $group_filter ) {
		$sql .= $wpdb->prepare( "AND group_id = %d ", $group_filter );
	    }
	    if ( $featured && BP_GROUP_DOCUMENTS_FEATURED ) {
		$sql .= "AND featured = 1 ";
	    }
	    $sql .= "ORDER BY created_ts DESC LIMIT %d";
            $result = $wpdb->get_results($wpdb->prepare($sql, $num), ARRAY_A);
        } else {
            $result = $wpdb->get_results($wpdb->prepare("SELECT d.* FROM " . BP_GROUP_DOCUMENTS_TABLE . " d INNER JOIN {$bp->groups->table_name} g ON d.group_id = g.id WHERE g.status = 'public' ORDER BY created_ts DESC LIMIT %d", $num), ARRAY_A);
        }

        return $result;
    }

    /**
     *
     * @global type $wpdb
     * @param type $num
     * @param type $group_filter
     * @param type $featured
     * @return type
     * @version 1.2.2
     */
    public static function get_list_for_popular_widget($num, $group_filter = 0, $featured = 0) {
        global $wpdb;
	$bp = buddypress();

	if ($group_filter || $featured) {
            $sql = "SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE 1=1 ";
            if ($group_filter)
                $sql .= $wpdb->prepare("AND group_id = %d ", $group_filter);
            if ($featured)
                $sql .= "AND featured = 1 ";
            $sql .= "ORDER BY download_count DESC LIMIT %d";
            $result = $wpdb->get_results($wpdb->prepare($sql, $num), ARRAY_A);
        } else {
            $result = $wpdb->get_results($wpdb->prepare("SELECT d.* FROM " . BP_GROUP_DOCUMENTS_TABLE . " d INNER JOIN {$bp->groups->table_name} g ON d.group_id = g.id WHERE g.status = 'public' ORDER BY download_count DESC LIMIT %d", $num), ARRAY_A);
        }

        return $result;
    }

    /**
     *
     * @global type $wpdp
     * @param type $num
     * @param type $featured
     * @return type

     * @version 1.2.2
     * v1.2.1, 17/9/2013, stergatu fix the http://wordpress.org/support/topic/widget-functionality bug
     * v1, 1/5/2013
     * @author stergatu
     * @since 0.5
     */
    public static function get_list_for_usergroups_widget($num, $featured = 0) {
        global $wpdb;
	if ( bp_has_groups( 'user_id=' . get_current_user_id() ) ) {
	    while (bp_groups()) : bp_the_group();
                $group_array[] = bp_get_group_id();
            endwhile;
        }

        $sql = "SELECT * FROM " . BP_GROUP_DOCUMENTS_TABLE . " WHERE group_id in ("
                . implode(',', array_map('absint', $group_array)) .
                " ) ";
        if ($featured && BP_GROUP_DOCUMENTS_FEATURED) {
            $sql .= "AND featured = 1 ";
        }
        $sql .= "ORDER BY created_ts DESC LIMIT %d";

        $result = $wpdb->get_results($wpdb->prepare($sql, $num), ARRAY_A);
        return $result;
    }

    /**
     * Creates the upload dir or and htaccess file
     *
     * @uses wp_mkdir_p() to create the dir
     * @uses insert_with_markers() to create the htaccess file
     * @since version 0.5
     * @author stergatu
     * @todo uncomment the insert_with_markers when ready
     * @version 2, 13/5/2013
     */
    private function create_dir_or_htaccess($dir) {
        global $wp;
        if (!file_exists($dir)) {
            // we first create the initial dir
            wp_mkdir_p($dir);
        }
        // then we need to check for .htaccess and eventually create it
        if (!file_exists($dir . '/.htaccess')) {
            // Defining the rule, we need to make it unreachable and use php to reach it
            $rules = array('Order Allow,Deny', 'Deny from all');
            // creating the .htaccess file
            insert_with_markers($dir . '/.htaccess', 'Buddypress Group Documents plugin', $rules);
        }
    }

}
