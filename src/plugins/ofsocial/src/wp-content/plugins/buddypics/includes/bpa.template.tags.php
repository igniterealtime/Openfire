<?php

/**
 * BP-ALBUM TEMPLATE TAGS CLASS
 *
 * @since 0.1.8.0
 * @package BP-Album
 * @subpackage Template Tags
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */

/**
 * Example use in the template file:
 *
 * 	<?php if ( bp_album_has_pictures() ) : ?>
 *
 *		<?php while ( bp_album_has_pictures() ) : bp_album_the_picture(); ?>
 *
 *			<a href="<?php bp_album_picture_url() ?>">
 *				<img src='<?php bp_album_picture_thumb_url() ?>' />
 *			</a>
 *
 *		<?php endwhile; ?>
 *
 *	<?php else : ?>
 *
 *		<p class="error">No Pics!</p>
 *
 *	<?php endif; ?>
 */

class BP_Album_Template {

	var $current_picture = -1;
	var $picture_count = 0;
	var $pictures;
	var $picture;

	var $in_the_loop;

	var $pag_page;
	var $pag_per_page;
	var $pag_links;
	var $pag_links_global;

	function BP_Album_Template( $args = '' ) {
		$this->__construct( $args);
	}

	function __construct( $args = '' ) {
		global $bp;


		$defaults = bp_album_default_query_args();

		$r = apply_filters('bp_album_template_args',wp_parse_args( $args, $defaults ));
		extract( $r , EXTR_SKIP);

		$this->pag_page = $page;
		$this->pag_per_page = $per_page;
		$this->owner_id = $owner_id;
		$this->privacy= $privacy;

		$total = bp_album_get_picture_count($r);
		$this->pictures = bp_album_get_pictures($r);

		if ( !$max || $max >= $total )
			$this->total_picture_count = $total;
		else
			$this->total_picture_count = $max;

		if ( !$max || $max >= count($this->pictures))
			$this->picture_count = count($this->pictures);
		else
			$this->picture_count = $max;


		$this->pag_links_global = paginate_links( array(
			'base' => get_permalink() . '%#%',
			'format' => '?page=%#%',
			'total' => @ceil( (int) $this->total_picture_count / (int) $this->pag_per_page ),
			'current' => (int) $this->pag_page,
			'prev_text' => '&larr;',
			'next_text' => '&rarr;',
			'mid_size' => 1

		));

		//$this->pag_links = paginate_links( array(
			//'base' => $bp->displayed_user->domain . $bp->album->slug .'/'. $bp->album->pictures_slug .'/%_%',
			//'format' => '%#%',
			//'total' => ceil( (int) $this->total_picture_count / (int) $this->pag_per_page ),
			//'current' => (int) $this->pag_page,
			//'prev_text' => '&larr;',
			//'next_text' => '&rarr;',
			//'mid_size' => 1
		//));

		if ($this->picture_count)
			$this->picture = $this->pictures[0];

	}

	function has_pictures() {
		if ( $this->current_picture + 1 < $this->picture_count ) {
			return true;
		} elseif ( $this->current_picture + 1 == $this->picture_count && $this->picture_count > 0) {
			do_action('bp_album_loop_end');

			$this->rewind_pictures();
		}

		$this->in_the_loop = false;
		return false;
	}

	function next_picture() {
		$this->current_picture++;
		$this->picture = $this->pictures[$this->current_picture];

		return $this->picture;
	}

	function rewind_pictures() {
		$this->current_picture = -1;
		if ( $this->picture_count > 0 ) {
			$this->picture = $this->pictures[0];
		}
	}

	function the_picture() {
		global $picture, $bp;

		$this->in_the_loop = true;
		$this->picture = $this->next_picture();

		if ( 0 == $this->current_picture )
			do_action('bp_album_loop_start');
	}

	function has_next_pic(){
		if (!isset($this->picture->next_pic))
			$this->picture->next_pic = bp_album_get_next_picture();
		if (isset($this->picture->next_pic) && $this->picture->next_pic !== false)
			return true;
		if (isset($this->picture->next_pic) && $this->picture->next_pic === false)
			return false;

	}
	function has_prev_pic(){
		if (!isset($this->picture->prev_pic))
			$this->picture->prev_pic = bp_album_get_prev_picture();
		if (isset($this->picture->prev_pic) && $this->picture->prev_pic !== false)
			return true;
		if (isset($this->picture->prev_pic) && $this->picture->prev_pic === false)
			return false;
	}
}

function bp_album_query_pictures( $args = '' ) {

	global $pictures_template;

	$pictures_template = new BP_Album_Template( $args );

	return $pictures_template->has_pictures();
}

function bp_album_the_picture() {

	global $pictures_template;
	return $pictures_template->the_picture();
}

/**
 * bp_album_has_pictures()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_has_pictures() {

	global $pictures_template;
	return $pictures_template->has_pictures();
}

/**
 * bp_album_picture_title()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_title() {
	echo bp_album_get_picture_title();
}
	function bp_album_get_picture_title() {

		global $pictures_template;
		return apply_filters( 'bp_album_get_picture_title', $pictures_template->picture->title);
	}

/**
 * bp_album_picture_title_truncate()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_title_truncate($length = 11) {
	echo bp_album_get_picture_title_truncate($length);
}
	function bp_album_get_picture_title_truncate($length) {

		global $pictures_template;

		$title = $pictures_template->picture->title;

		$title = apply_filters( 'bp_album_get_picture_title_truncate', $title);

		$r = wp_specialchars_decode($title, ENT_QUOTES);


		if ( function_exists('mb_strlen') && strlen($r) > mb_strlen($r) ) {

			$length = round($length / 2);
		}

		if ( function_exists( 'mb_substr' ) ) {


			$r = mb_substr($r, 0, $length);
		}
		else {
			$r = substr($r, 0, $length);
		}

		$result = _wp_specialchars($r) . '&#8230;';

		return $result;

	}

/**
 * bp_album_picture_desc()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_desc() {
	echo bp_album_get_picture_desc();
}
	function bp_album_get_picture_desc() {

		global $pictures_template;

		return apply_filters( 'bp_album_get_picture_desc', $pictures_template->picture->description );
	}

/**
 * bp_album_picture_desc_truncate()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_desc_truncate($words=55) {
	echo bp_album_get_picture_desc_truncate($words);
}
	function bp_album_get_picture_desc_truncate($words=55) {

		global $pictures_template;

		$exc = bp_create_excerpt($pictures_template->picture->description, $words, true) ;

		return apply_filters( 'bp_album_get_picture_desc_truncate', $exc, $pictures_template->picture->description, $words );
	}

/**
 * bp_album_picture_id()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_id() {
	echo bp_album_get_picture_id();
}
	function bp_album_get_picture_id() {

		global $pictures_template;

		return apply_filters( 'bp_album_get_picture_id', $pictures_template->picture->id );
	}

/**
 * bp_album_picture_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_url() {
	echo bp_album_get_picture_url();
}
	function bp_album_get_picture_url() {

		global $bp, $pictures_template;

		$owner_domain = bp_core_get_user_domain($pictures_template->picture->owner_id);
		return apply_filters( 'bp_album_get_picture_url', $owner_domain . $bp->album->slug . '/'.$bp->album->single_slug.'/'.$pictures_template->picture->id  . '/');
	}

/**
 * bp_album_picture_edit_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_edit_link() {
	if (bp_is_my_profile() || is_super_admin())
		echo '<a href="'.bp_album_get_picture_edit_url().'" class="picture-edit">'.__('Edit picture','bp-album').'</a>';
}

/**
 * bp_album_picture_edit_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_edit_url() {
	echo bp_album_get_picture_edit_url();
}
	function bp_album_get_picture_edit_url() {

		global $bp, $pictures_template;

		if (bp_is_my_profile() || is_super_admin())
			return wp_nonce_url(apply_filters( 'bp_album_get_picture_edit_url', $bp->displayed_user->domain . $bp->album->slug .'/'.$bp->album->single_slug.'/'.$pictures_template->picture->id.'/'.$bp->album->edit_slug),'bp-album-edit-pic');
	}

/**
 * bp_album_picture_delete_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_delete_link() {
	if (bp_is_my_profile() || is_super_admin())
		echo '<a href="'.bp_album_get_picture_delete_url().'" class="picture-delete">'.__('Delete picture','bp-album').'</a>';
}

/**
 * bp_album_picture_delete_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_delete_url() {
	echo bp_album_get_picture_delete_url();
}
	function bp_album_get_picture_delete_url() {

		global $bp, $pictures_template;

		if (bp_is_my_profile() || is_super_admin())
			return wp_nonce_url(apply_filters( 'bp_album_get_picture_delete_url', $bp->displayed_user->domain . $bp->album->slug .'/'.$bp->album->single_slug.'/'.$pictures_template->picture->id.'/'.$bp->album->delete_slug ),'bp-album-delete-pic');
	}

/**
 * bp_album_picture_original_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_original_url() {
	echo bp_album_get_picture_original_url();
}
	function bp_album_get_picture_original_url() {

		global $bp, $pictures_template;

		if($bp->album->bp_album_url_remap == true){

		    $filename = substr( $pictures_template->picture->pic_org_url, strrpos($pictures_template->picture->pic_org_url, '/') + 1 );
		    $owner_id = $pictures_template->picture->owner_id;
		    $result = $bp->album->bp_album_base_url . '/' . $owner_id . '/' . $filename;

		    return $result;
		}
		else {
		    return apply_filters( 'bp_album_get_picture_original_url', bp_get_root_domain().$pictures_template->picture->pic_org_url );
		}

	}

/**
 * bp_album_picture_middle_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_middle_url() {
	echo bp_album_get_picture_middle_url();
}
	function bp_album_get_picture_middle_url() {

		global $bp, $pictures_template;

		if($bp->album->bp_album_url_remap == true){

		    $filename = substr( $pictures_template->picture->pic_mid_url, strrpos($pictures_template->picture->pic_mid_url, '/') + 1 );
		    $owner_id = $pictures_template->picture->owner_id;
		    $result = $bp->album->bp_album_base_url . '/' . $owner_id . '/' . $filename;

		    return $result;
		}
		else {
		    return apply_filters( 'bp_album_get_picture_middle_url', bp_get_root_domain().$pictures_template->picture->pic_mid_url );
		}
	}

/**
 * bp_album_picture_thumb_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_thumb_url() {
	echo bp_album_get_picture_thumb_url();
}
	function bp_album_get_picture_thumb_url() {

		global $bp, $pictures_template;

		if($bp->album->bp_album_url_remap == true){

		    $filename = substr( $pictures_template->picture->pic_thumb_url, strrpos($pictures_template->picture->pic_thumb_url, '/') + 1 );
		    $owner_id = $pictures_template->picture->owner_id;
		    $result = $bp->album->bp_album_base_url . '/' . $owner_id . '/' . $filename;

		    return $result;
		}
		else {
		    return apply_filters( 'bp_album_get_picture_thumb_url', bp_get_root_domain().$pictures_template->picture->pic_thumb_url );
		}
	}

/**
 * bp_album_total_picture_count()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_total_picture_count() {
	echo bp_album_get_total_picture_count();
}
	function bp_album_get_total_picture_count() {

		global $pictures_template;

		return apply_filters( 'bp_album_get_total_picture_count', $pictures_template->total_picture_count );
	}

/**
 * bp_album_picture_pagination()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_pagination($always_show = false) {
	echo bp_album_get_picture_pagination($always_show);
}
	function bp_album_get_picture_pagination($always_show = false) {

		global $pictures_template;

		if ($always_show || $pictures_template->total_picture_count > $pictures_template->pag_per_page)
		return apply_filters( 'bp_album_get_picture_pagination', $pictures_template->pag_links );
	}

/**
 * bp_album_picture_pagination_global()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_pagination_global($always_show = false) {
	echo bp_album_get_picture_pagination_global($always_show);
}
	function bp_album_get_picture_pagination_global($always_show = false) {

		global $pictures_template;

		if ($always_show || $pictures_template->total_picture_count > $pictures_template->pag_per_page)
		return apply_filters( 'bp_album_get_picture_pagination_global', $pictures_template->pag_links_global );
	}

/**
 * bp_album_adjacent_links()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_adjacent_links() {
	echo bp_album_get_adjacent_links();
}
	function bp_album_get_adjacent_links() {

		global $pictures_template;

		if ($pictures_template->has_prev_pic() || $pictures_template->has_next_pic())
			return bp_album_get_prev_picture_or_album_link().' '.bp_album_get_next_picture_or_album_link();
		else
			return '<a href="'.bp_album_get_pictures_url().'" class="picture-album-link picture-no-adjacent-link"><span>'.bp_word_or_name( __( "My Albums", 'bp-album' ), __( "%s album", 'bp-album' ) ,false,false ).'</span> </a>';
	}

/**
 * bp_album_next_picture_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_next_picture_link($text = ' &raquo;', $title = true) {
	echo bp_album_get_next_picture_link($text, $title);
}
	function bp_album_get_next_picture_link($text = ' &raquo;', $title = true) {

		global $pictures_template;

		if ($pictures_template->has_next_pic()){
			$text = ( ($title)?bp_album_get_next_picture_title():'' ).$text;
			return '<a href="'.bp_album_get_next_picture_url().'" class="picture-next-link"> <span>'.$text.'</span></a>';
		}
		else
			return null;
	}

/**
 * bp_album_next_picture_or_album_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_next_picture_or_album_link($text = ' &raquo;', $title = true) {
	echo bp_album_get_next_picture_or_album_link($text, $title);
}
	function bp_album_get_next_picture_or_album_link($text = ' &raquo;', $title = true) {

		global $pictures_template;

		if ($pictures_template->has_next_pic()){
			$text = ( ($title)?bp_album_get_next_picture_title():'' ).$text;
			return '<a href="'.bp_album_get_next_picture_url().'" class="picture-next-link"> <span>'.$text.'</span></a>';
		}
		else
			return '<a href="'.bp_album_get_pictures_url().'" class="picture-album-link picture-next-link"> <span> '.bp_word_or_name( __( "My Album", 'bp-album' ), __( "%s album", 'bp-album' ) ,false,false ).'</span></a>';
	}

/**
 * bp_album_next_picture_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_next_picture_url() {
	echo bp_album_get_next_picture_url();
}
	function bp_album_get_next_picture_url() {

		global $bp, $pictures_template;

		if ($pictures_template->has_next_pic())
			return apply_filters( 'bp_album_get_next_picture_url', $bp->displayed_user->domain . $bp->album->slug . '/'.$bp->album->single_slug.'/'.$pictures_template->picture->next_pic->id  . '/');
	}

/**
 * bp_album_next_picture_title()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_next_picture_title() {
	echo bp_album_get_next_picture_title();
}
	function bp_album_get_next_picture_title() {

		global $pictures_template;

		if ($pictures_template->has_next_pic())
			return apply_filters( 'bp_album_get_picture_title', $pictures_template->picture->next_pic->title );
	}

/**
 * bp_album_has_next_picture()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_has_next_picture() {

	global $bp, $pictures_template;

	return $pictures_template->has_next_pic();
}

/**
 * bp_album_prev_picture_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_prev_picture_link($text = '&laquo; ', $title = true) {
	echo bp_album_get_prev_picture_link($text, $title);
}
	function bp_album_get_prev_picture_link($text = '&laquo; ', $title = true) {

		global $pictures_template;

		if ($pictures_template->has_prev_pic()){
			$text .= ($title)?bp_album_get_prev_picture_title():'';
			return '<a href="'.bp_album_get_prev_picture_url().'" class="picture-prev-link"><span>'.$text.'</span> </a>';
		}
		else
			return null;
	}

/**
 * bp_album_prev_picture_or_album_link()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_prev_picture_or_album_link($text = '&laquo; ', $title = true) {
	echo bp_album_get_prev_picture_or_album_link($text, $title);
}
	function bp_album_get_prev_picture_or_album_link($text = '&laquo; ', $title = true) {

		global $pictures_template;

		if ($pictures_template->has_prev_pic()){
			$text .= ($title)?bp_album_get_prev_picture_title():'';
			return '<a href="'.bp_album_get_prev_picture_url().'" class="picture-prev-link"><span>'.$text.'</span> </a>';
		}
		else
			return '<a href="'.bp_album_get_pictures_url().'" class="picture-album-link picture-prev-link"><span> '.bp_word_or_name( __( "My album", 'bp-album' ), __( "%s album", 'bp-album' ) ,false,false ).'</span> </a>';
	}

/**
 * bp_album_prev_picture_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_prev_picture_url() {
	echo bp_album_get_prev_picture_url();
}
	function bp_album_get_prev_picture_url() {

		global $bp, $pictures_template;

		if ($pictures_template->has_prev_pic())
			return apply_filters( 'bp_album_get_prev_picture_url', $bp->displayed_user->domain . $bp->album->slug . '/'.$bp->album->single_slug.'/'.$pictures_template->picture->prev_pic->id . '/');
	}

/**
 * bp_album_prev_picture_title()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_prev_picture_title() {
	echo bp_album_get_prev_picture_title();
}
	function bp_album_get_prev_picture_title() {

		global $pictures_template;

		if ($pictures_template->has_prev_pic())
			return apply_filters( 'bp_album_get_picture_title', $pictures_template->picture->prev_pic->title );
	}

/**
 * bp_album_has_prev_picture()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_has_prev_picture() {

	global $pictures_template;

	return $pictures_template->has_prev_pic();
}

/**
 * bp_album_pictures_url()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_pictures_url() {

	echo bp_album_get_pictures_url();

}
	function bp_album_get_pictures_url() {

		global $bp;
			return apply_filters( 'bp_album_get_pictures_url', $bp->displayed_user->domain . $bp->album->slug . '/'.$bp->album->pictures_slug . '/');
	}

/**
 * bp_album_picture_has_activity()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_picture_has_activity(){

	global $bp, $pictures_template;

	// Handle users that try to run the function when the activity stream is disabled
	// ------------------------------------------------------------------------------
	if ( !function_exists( 'bp_activity_add' ) || !$bp->album->bp_album_enable_wire) {
		return false;
	}

	return bp_has_activities( array('object'=> $bp->album->id,'primary_id'=>$pictures_template->picture->id , 'show_hidden' => true) );
}

/**
 * bp_album_comments_enabled()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_comments_enabled() {

        global $bp;

        return $bp->album->bp_album_enable_comments;

}

?>