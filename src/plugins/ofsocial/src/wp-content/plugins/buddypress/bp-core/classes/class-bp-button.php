<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * API to create BuddyPress buttons.
 *
 * @since 1.2.6
 *
 * @param array $args {
 *     Array of arguments.
 *
 *     @type string      $id                String describing the button type.
 *     @type string      $component         The name of the component the button belongs to.
 *                                          Default: 'core'.
 *     @type bool        $must_be_logged_in Optional. Does the user need to be logged
 *                                          in to see this button? Default: true.
 *     @type bool        $block_self        Optional. True if the button should be hidden
 *                                          when a user is viewing his own profile.
 *                                          Default: true.
 *     @type string|bool $wrapper           Optional. HTML element type that should wrap
 *                                          the button: 'div', 'span', 'p', or 'li'.
 *                                          False for no wrapper at all. Default: 'div'.
 *     @type string      $wrapper_id        Optional. DOM ID of the button wrapper element.
 *                                          Default: ''.
 *     @type string      $wrapper_class     Optional. DOM class of the button wrapper
 *                                          element. Default: ''.
 *     @type string      $link_href         Optional. Destination link of the button.
 *                                          Default: ''.
 *     @type string      $link_class        Optional. DOM class of the button. Default: ''.
 *     @type string      $link_id           Optional. DOM ID of the button. Default: ''.
 *     @type string      $link_rel          Optional. DOM 'rel' attribute of the button.
 *                                          Default: ''.
 *     @type string      $link_title        Optional. Title attribute of the button.
 *                                          Default: ''.
 *     @type string      $link_text         Optional. Text to appear on the button.
 *                                          Default: ''.
 * }
 */
class BP_Button {

	/** Button properties *****************************************************/

	/**
	 * The button ID.
	 *
	 * @var string
	 */
	public $id = '';

	/**
	 * The name of the component that the button belongs to.
	 *
	 * @var string
	 */
	public $component = 'core';

	/**
	 * Does the user need to be logged in to see this button?
	 *
	 * @var bool
	 */
	public $must_be_logged_in = true;

	/**
	 * Whether the button should be hidden when viewing your own profile.
	 *
	 * @var bool
	 */
	public $block_self = true;

	/** Wrapper ***************************************************************/

	/**
	 * The type of DOM element to use for a wrapper.
	 *
	 * @var string|bool 'div', 'span', 'p', 'li', or false for no wrapper.
	 */
	public $wrapper = 'div';

	/**
	 * The DOM class of the button wrapper.
	 *
	 * @var string
	 */
	public $wrapper_class = '';

	/**
	 * The DOM ID of the button wrapper.
	 *
	 * @var string
	 */
	public $wrapper_id = '';

	/** Button ****************************************************************/

	/**
	 * The destination link of the button.
	 *
	 * @var string
	 */
	public $link_href = '';

	/**
	 * The DOM class of the button link.
	 *
	 * @var string
	 */
	public $link_class = '';

	/**
	 * The DOM ID of the button link.
	 *
	 * @var string
	 */
	public $link_id = '';

	/**
	 * The DOM rel value of the button link.
	 *
	 * @var string
	 */
	public $link_rel = '';

	/**
	 * Title of the button link.
	 *
	 * @var string
	 */
	public $link_title = '';

	/**
	 * The contents of the button link.
	 *
	 * @var string
	 */
	public $link_text = '';

	/** HTML result ***********************************************************/

	public $contents = '';

	/** Methods ***************************************************************/

	/**
	 * Builds the button based on class parameters.
	 *
	 * @since 1.2.6
	 *
	 * @param array|string $args See {@BP_Button}.
	 */
	public function __construct( $args = '' ) {

		$r = wp_parse_args( $args, get_class_vars( __CLASS__ ) );

		// Required button properties
		$this->id                = $r['id'];
		$this->component         = $r['component'];
		$this->must_be_logged_in = (bool) $r['must_be_logged_in'];
		$this->block_self        = (bool) $r['block_self'];
		$this->wrapper           = $r['wrapper'];

		// $id and $component are required
		if ( empty( $r['id'] ) || empty( $r['component'] ) )
			return false;

		// No button if component is not active
		if ( ! bp_is_active( $this->component ) )
			return false;

		// No button for guests if must be logged in
		if ( true == $this->must_be_logged_in && ! is_user_logged_in() )
			return false;

		// block_self
		if ( true == $this->block_self ) {
			// No button if you are the current user in a members loop
			// This condition takes precedence, because members loops
			// can be found on user profiles
			if ( bp_get_member_user_id() ) {
				if ( is_user_logged_in() && bp_loggedin_user_id() == bp_get_member_user_id() ) {
					return false;
				}

			// No button if viewing your own profile (and not in
			// a members loop)
			} elseif ( bp_is_my_profile() ) {
				return false;
			}
		}

		// Wrapper properties
		if ( false !== $this->wrapper ) {

			// Wrapper ID
			if ( !empty( $r['wrapper_id'] ) ) {
				$this->wrapper_id    = ' id="' . $r['wrapper_id'] . '"';
			}

			// Wrapper class
			if ( !empty( $r['wrapper_class'] ) ) {
				$this->wrapper_class = ' class="generic-button ' . $r['wrapper_class'] . '"';
			} else {
				$this->wrapper_class = ' class="generic-button"';
			}

			// Set before and after
			$before = '<' . $r['wrapper'] . $this->wrapper_class . $this->wrapper_id . '>';
			$after  = '</' . $r['wrapper'] . '>';

		// No wrapper
		} else {
			$before = $after = '';
		}

		// Link properties
		if ( !empty( $r['link_id']    ) ) $this->link_id    = ' id="' .    $r['link_id']    . '"';
		if ( !empty( $r['link_href']  ) ) $this->link_href  = ' href="' .  $r['link_href']  . '"';
		if ( !empty( $r['link_title'] ) ) $this->link_title = ' title="' . $r['link_title'] . '"';
		if ( !empty( $r['link_rel']   ) ) $this->link_rel   = ' rel="' .   $r['link_rel']   . '"';
		if ( !empty( $r['link_class'] ) ) $this->link_class = ' class="' . $r['link_class'] . '"';
		if ( !empty( $r['link_text']  ) ) $this->link_text  =              $r['link_text'];

		// Build the button
		$this->contents = $before . '<a'. $this->link_href . $this->link_title . $this->link_id . $this->link_rel . $this->link_class . '>' . $this->link_text . '</a>' . $after;

		/**
		 * Filters the button based on class parameters.
		 *
		 * This filter is a dynamic filter based on component and component ID and
		 * allows button to be manipulated externally.
		 *
		 * @since 1.2.6
		 *
		 * @param string    $contents HTML being used for the button.
		 * @param BP_Button $this     Current BP_Button instance.
		 * @param string    $before   HTML appended before the actual button.
		 * @param string    $after    HTML appended after the actual button.
		 */
		$this->contents = apply_filters( 'bp_button_' . $this->component . '_' . $this->id, $this->contents, $this, $before, $after );
	}

	/**
	 * Return the markup for the generated button.
	 *
	 * @since 1.2.6
	 *
	 * @return string Button markup.
	 */
	public function contents() {
		return $this->contents;
	}

	/**
	 * Output the markup of button.
	 *
	 * @since 1.2.6
	 */
	public function display() {
		if ( !empty( $this->contents ) )
			echo $this->contents;
	}
}
