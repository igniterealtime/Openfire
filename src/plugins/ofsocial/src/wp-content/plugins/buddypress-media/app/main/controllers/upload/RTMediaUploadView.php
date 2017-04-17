<?php

/**
 * Description of RTMediaUploadView
 *
 * @author joshua
 */
class RTMediaUploadView {

	private $attributes;

	/**
	 *
	 * @param type $attr
	 */
	function __construct( $attr ) {
		$this->attributes = $attr;
	}

	static function upload_nonce_generator( $echo = true, $only_nonce = false ) {

		if ( $echo ) {
			wp_nonce_field( 'rtmedia_upload_nonce', 'rtmedia_upload_nonce' );
		} else {
			if ( $only_nonce )
				return wp_create_nonce( 'rtmedia_upload_nonce' );
			$token = array(
				'action' => 'rtmedia_upload_nonce',
				'nonce' => wp_create_nonce( 'rtmedia_upload_nonce' )
			);

			return json_encode( $token );
		}
	}

	/**
	 * Render the uploader shortcode and attach the uploader panel
	 *
	 * @param type $template_name
	 */
	public function render( $template_name ) {

		global $rtmedia_query;
		$album = '';
		if ( apply_filters( 'rtmedia_render_select_album_upload', true ) ) {
			if ( $rtmedia_query && isset( $rtmedia_query->media_query ) && isset( $rtmedia_query->media_query[ 'album_id' ] ) &&  is_rtmedia_album( $rtmedia_query->media_query[ 'album_id' ] ) ) {
				$album = '<input class="rtmedia-current-album" type="hidden" name="rtmedia-current-album" value="' . $rtmedia_query->media_query[ 'album_id' ] . '" />';
			} elseif ( is_rtmedia_album_enable() && $rtmedia_query && is_rtmedia_gallery() ) {

				if ( isset( $rtmedia_query->query[ 'context' ] ) && $rtmedia_query->query[ 'context' ] == 'profile' ) {
					$album = '<span> <label> <i class="dashicons dashicons-format-gallery rtmicon"></i>' . __( 'Album', 'buddypress-media' ) . ': </label><select name="album" class="rtmedia-user-album-list">' . rtmedia_user_album_list() . '</select></span>';
				}
				if ( isset( $rtmedia_query->query[ 'context' ] ) && $rtmedia_query->query[ 'context' ] == 'group' ) {
					$album = '<span> <label> <i class="dashicons dashicons-format-gallery rtmicon"></i>' . __( 'Album', 'buddypress-media' ) . ': </label><select name="album" class="rtmedia-user-album-list">' . rtmedia_group_album_list() . '</select></span>';
				}
			}
		}
        $up_privacy = $privacy = ""; //uploader privacy dropdown for uploader under rtMedia Media tab.
		if ( is_rtmedia_privacy_enable()
			&& ( ( ! isset( $rtmedia_query->is_upload_shortcode ) || $rtmedia_query->is_upload_shortcode === false) )
				|| ( isset( $rtmedia_query->is_upload_shortcode ) && ! isset( $this->attributes['privacy'] ) )
		) {
			if ( ( isset( $rtmedia_query->query[ 'context' ] ) && $rtmedia_query->query[ 'context' ] == 'group' ) || ( function_exists( 'bp_is_groups_component' ) && bp_is_groups_component() ) ) {
                // if the context is group, then set the media privacy to public
                $privacy = "<input type='hidden' name='privacy' value='0'/>";
			} else {
                $up_privacy = new RTMediaPrivacy();
				$up_privacy = $up_privacy->select_privacy_ui( false, 'rtSelectPrivacy' );
				if ( $up_privacy ) {
					$privacy = "<span> <label for='privacy'> <i class='dashicons dashicons-visibility rtmicon'></i> " . __( 'Privacy: ', 'buddypress-media' ) . "</label>" . $up_privacy . "</span>";
                }
            }
        }

		$upload_tabs = array(
			'file_upload' => array(
				'title' => __( 'File Upload', 'buddypress-media' ),
				'class' => array( 'rtm-upload-tab', 'active' ),
				'content' => '<div class="rtm-upload-tab-content" data-id="rtm-upload-tab">'
					. apply_filters( 'rtmedia_uploader_before_select_files', "" )
					. '<div class="rtm-select-files"><input id="rtMedia-upload-button" value="' . __( "Select your files", 'buddypress-media' ) . '" type="button" class="rtmedia-upload-input rtmedia-file" />'
					. '<span class="rtm-seperator">' . __('or','buddypress-media') .'</span><span class="drag-drop-info">' . __('Drop your files here', 'buddypress-media') . '</span> <i class="rtm-file-size-limit rtmicon-info-circle rtmicon-fw"></i></div>'
					. apply_filters( 'rtmedia_uploader_after_select_files', "" )
					. '</div>',
			),
		);

		$upload_tabs = apply_filters( 'rtmedia_uploader_tabs', $upload_tabs );

		if( is_array( $upload_tabs ) && ! empty( $upload_tabs ) ){
			if( sizeof( $upload_tabs ) == 1 && isset( $upload_tabs['file_upload'] ) ){
				$upload_tab_html = $upload_tabs['file_upload']['content'];
			} else {
				$upload_tab_html = '<div class="rtm-uploader-main-wrapper"><div class="rtm-uploader-tabs"><ul>';
				foreach( $upload_tabs as $single_tab ){
					$upload_tab_html .= '<li class="'. implode( ' ', $single_tab['class'] ) .'">' . $single_tab['title'] . '</li>';
				}
				$upload_tab_html .= '</ul></div>';
				foreach( $upload_tabs as $single_tab ){
					$upload_tab_html .= $single_tab['content'];
				}
				$upload_tab_html .= '</div>';
			}
		} else {
			$upload_tab_html = '';
		}
		global $rtmedia;
		//Render UPLOAD button only if direct upload is disabled
		$upload_button = ( ! ( isset( $rtmedia->options[ "general_direct_upload" ] )&& $rtmedia->options[ "general_direct_upload" ] == 1 ) ? '<input type="button" class="start-media-upload" value="' . __( 'Start upload', 'buddypress-media' ) . '"/>'  : '' );
        $tabs = array(
            'file_upload' => array(
                'default' => array(
	                'title' => __( 'File Upload', 'buddypress-media' ),
                    'content' =>
	                    '<div id="rtmedia-upload-container" >'
	                        . '<div id="drag-drop-area" class="drag-drop clearfix">'
	                                . apply_filters( 'rtmedia_uploader_before_album_privacy', "" )
	                                . "<div class='rtm-album-privacy'>" . $album . $privacy . "</div>"
	                                . $upload_tab_html
									. apply_filters( 'rtmedia_uploader_before_start_upload_button', "" )
						. $upload_button
									. apply_filters( 'rtmedia_uploader_after_start_upload_button', "" )
	                        . '</div>'
						. '<div class="clearfix">'
						. '<ul class="plupload_filelist_content ui-sortable rtm-plupload-list clearfix" id="rtmedia_uploader_filelist"></ul></div>'
	                    . '</div>'
                ),
				'activity' => array(
					'title' => __( 'File Upload', 'buddypress-media' ),
					'content' =>
						'<div class="rtmedia-plupload-container rtmedia-container clearfix">'
							.'<div id="rtmedia-action-update" class="clearfix">'
								.'<div class="rtm-upload-button-wrapper">'
									.'<div id="rtmedia-whts-new-upload-container">'
									.'</div>'
									.'<button type="button" class="rtmedia-add-media-button" id="rtmedia-add-media-button-post-update" title="' . apply_filters( 'rtmedia_attach_media_button_title', __( 'Attach Media', 'buddypress-media' ) ) . '">'
										.'<span class="dashicons dashicons-admin-media"></span>'
										. apply_filters( 'rtmedia_attach_file_message', '' )
									. '</button>'
								.'</div>'
								. $up_privacy
							. '</div>'
						.'</div>'
						.'<div class="rtmedia-plupload-notice">'
							.'<ul class="plupload_filelist_content ui-sortable rtm-plupload-list clearfix" id="rtmedia_uploader_filelist">'
							.'</ul>'
						.'</div>'
				)
            ),
//			'file_upload' => array( 'title' => __('File Upload','buddypress-media'), 'content' => '<div id="rtmedia-uploader"><p>Your browser does not have HTML5 support.</p></div>'),
			'link_input' => array( 'title' => __( 'Insert from URL', 'buddypress-media' ), 'content' => '<input type="url" name="bp-media-url" class="rtmedia-upload-input rtmedia-url" />' ),
		);
		$tabs = apply_filters( 'rtmedia_upload_tabs', $tabs );

		$attr = $this->attributes;
		$mode = (isset( $_GET[ 'mode' ] ) && array_key_exists( $_GET[ 'mode' ], $tabs )) ? $_GET[ 'mode' ] : 'file_upload';
		if ( $attr && is_array( $attr ) ) {
			foreach ( $attr as $key => $val ) {
				?>
				<input type='hidden' id="rt_upload_hf_<?php echo sanitize_key( $key ); ?>" value='<?php echo $val; ?>' name ='<?php echo $key; ?>' />
				<?php
			}
		}
		$upload_type = 'default';
		if ( isset( $attr[ 'activity' ] ) && $attr[ 'activity' ] )
			$upload_type = 'activity';

		$uploadHelper = new RTMediaUploadHelper();
		include $this->locate_template( $template_name );
	}

	/**
	 * Template Locator
	 *
	 * @param type $template
	 * @return string
	 */
	protected function locate_template( $template ) {
		$located = '';

		$template_name = $template . '.php';

		if ( ! $template_name )
			$located = false;
		if ( file_exists( STYLESHEETPATH . '/rtmedia/upload/' . $template_name ) ) {
			$located = STYLESHEETPATH . '/rtmedia/upload/' . $template_name;
		} else if ( file_exists( TEMPLATEPATH . '/rtmedia/upload/' . $template_name ) ) {
			$located = TEMPLATEPATH . '/rtmedia/upload/' . $template_name;
		} else {
			$located = RTMEDIA_PATH . 'templates/upload/' . $template_name;
		}

		return $located

		;
	}

}
