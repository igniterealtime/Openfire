<?php
/**
 * Author: Ritesh <ritesh.patel@rtcamp.com>
 *
 * edit/delete links on gallery items.
 */

class RTMediaGalleryItemAction {

	function __construct(){
		// add edit/delete buttons in media gallery besides thumbnails
		add_action( 'rtmedia_before_item', array( $this, 'action_buttons_before_media_thumbnail' ), 11 );
		// In load more of media all the data render through backbone template and so we need to avail it in backbone variable
		add_filter( 'rtmedia_media_array_backbone', array( $this, 'rtmedia_media_actions_backbone' ), 10, 1 );
		// add a custom class to media gallery item if the user on his profile which will be used to show the action buttons on the media gallery item
		add_filter( 'rtmedia_gallery_class_filter', array( $this, 'add_class_to_rtmedia_gallery' ), 11, 1 );
		// remove rtMedia Pro actions
		add_action( 'rtmedia_before_media_gallery', array( $this, 'remove_rtmedia_pro_hooks' ) );
	}

	function remove_rtmedia_pro_hooks(){
		remove_action( 'rtmedia_before_item', 'add_action_buttons_before_media_thumbnail' , 11 );
	}

	function add_class_to_rtmedia_gallery( $classes ) {
		global $rtmedia_query;
		$user_id = get_current_user_id();
		if ( is_rt_admin() || ( isset( $rtmedia_query->query[ 'context' ] ) && $rtmedia_query->query[ 'context' ] == 'profile' && isset( $rtmedia_query->query[ 'context_id' ] ) && $rtmedia_query->query[ 'context_id' ] == $user_id ) ){
			$classes .= " rtm-pro-allow-action";
		}
		if ( isset( $rtmedia_query->query[ 'context' ] ) && $rtmedia_query->query[ 'context' ] == 'group' ){
			$group_id = $rtmedia_query->query[ 'context_id' ];
			if ( groups_is_user_mod( $user_id, $group_id ) || groups_is_user_admin( $user_id, $group_id ) ){
				$classes .= " rtm-pro-allow-action";
			}
		}

		return $classes;

	}


	function action_buttons_before_media_thumbnail(){
		// add edit and delete links on single media
		global $rtmedia_media, $rtmedia_backbone;
		?>
		<?php
		if ( is_user_logged_in() ){
			if ( $rtmedia_backbone[ 'backbone' ] ){
				echo "<%= media_actions %>";
			} else {
				$context_id = $rtmedia_media->context_id;
				$user_id    = get_current_user_id();

				if( is_rt_admin()
				    || ( function_exists( 'groups_is_user_mod' ) && groups_is_user_mod( $user_id, $context_id ) )
				        || ( isset( $rtmedia_media ) && isset( $rtmedia_media->media_author ) && $rtmedia_media->media_author == get_current_user_id() ) ) {
					?>
					<div class='rtmedia-gallery-item-actions'>
						<a href="<?php rtmedia_permalink(); ?>edit" class='no-popup' target='_blank' title='<?php _e( 'Edit this media', 'buddypress-media' ); ?>'>
							<i class='dashicons dashicons-edit rtmicon'></i><?php _e( 'Edit', 'buddypress-media' ); ?>
						</a>
						<a href="#" class="no-popup rtm-delete-media" title='<?php _e( 'Delete this media', 'buddypress-media' ); ?>'>
							<i class='dashicons dashicons-trash rtmicon'></i><?php _e( 'Delete', 'buddypress-media' ); ?>
						</a>
					</div>
				<?php
				}
			}
		}
	}

	function rtmedia_media_actions_backbone( $media_array ){
		$context_id = $media_array->context_id;
		$user_id    = get_current_user_id();

		if( is_rt_admin()
			|| ( function_exists( 'groups_is_user_mod' ) && groups_is_user_mod( $user_id, $context_id ) )
		        || $media_array->media_author == get_current_user_id() ) {
			$media_array->media_actions = "<div class='rtmedia-gallery-item-actions'><a href='" . $media_array->rt_permalink . "edit' class='no-popup' target='_blank' title='" . __( 'Edit this media', 'buddypress-media' ) ."'><i class='dashicons dashicons-edit rtmicon'></i>" . __( 'Edit', 'buddypress-media' ) ."</a><a href='#' class='no-popup rtm-delete-media' title='" . __( 'Delete this media', 'buddypress-media' ) . "' ><i class='dashicons dashicons-trash rtmicon'></i>" . __( 'Delete', 'buddypress-media' ) ."</a></div>";
		} else {
			$media_array->media_actions = "";
		}
		return $media_array;
	}
} 