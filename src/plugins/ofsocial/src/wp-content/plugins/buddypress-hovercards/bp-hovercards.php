<?php
/*
Plugin Name: BuddyPress Hovercards
Plugin URI: https://github.com/mgmartel/BuddyPress-Hovercards/
Author: Mike Martel
Author URI: http://trenvo.nl
Description: Adds hovercard to Buddypress avatars
Version: 1.1.3
Revision Date: January 24th, 2013
*/

/**
 * "BuddyPress Hovercards"
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * @package bp-hovercards
 */

// Exit if accessed directly
if ( !defined( 'ABSPATH' ) )
	exit;

/**
 * Version number
 *
 * @since 0.9
 */
define ( 'BPHOVERCARDS_VERSION', '1.1.3' );

/**
 * PATHs and URLs
 *
 * @since 0.9
 */
define ( 'BPHOVERCARDS_DIR', plugin_dir_path(__FILE__) );
define ( 'BPHOVERCARDS_URL', plugin_dir_url(__FILE__) );
define ( 'BPHOVERCARDS_TEMPLATES_DIR', BPHOVERCARDS_DIR . 'templates/' );
define ( 'BPHOVERCARDS_INC_URL', BPHOVERCARDS_URL . 'templates/_inc' );

if ( ! class_exists( 'BuddyPress_Hovercards' ) ) :

    class BuddyPress_Hovercards
    {

        /**
         * In lieu of an options screen, some options fixed as classvars
         */
        protected $parent_filter = "#item-header-avatar, .profile_badge";
        protected $element_filter = ".nohc";

        /**
         * Creates an instance of the BuddyPress_Hovercards class, and loads i18n.
         *
         * Thanks to the Jetpack and BP Labs plugins for the idea with this function.
         *
         * @return BuddyPress_Hovercards object
         * @since 0.9
         * @static
         */
        public static function &init() {
            static $instance = false;

            if ( !$instance ) {
                load_plugin_textdomain('bp-hovercards', false, BPHOVERCARDS_DIR . 'languages/' );
                $instance = new BuddyPress_Hovercards;
            }

            return $instance;
        }


        /**
         * Simple constructor
         *
         * @since 0.9
         */
        public function __construct() {
            add_action('wp_enqueue_scripts',array ( &$this, 'load_scripts' ) );
            add_action('wp_ajax_buddypress_hovercard', array( &$this, 'ajax_hovercard' ) );
	    add_action('wp_ajax_nopriv_buddypress_hovercard', array( &$this, 'ajax_hovercard' ) );
        }
            /**
             * PHP4
             *
             * @since 0.9
             */
            public function BuddyPress_Hovercards() {
                $this->_construct();
            }

        /**
         * Load stylesheets and scripts
         *
         * Loads Tipsy JS and CSS, as well as BP Hovercard JS and CSS
         *
         * @since 0.9
         */
        public function load_scripts() {
            if( ! is_admin() ) {
                wp_enqueue_script( 'jquery' );
                wp_enqueue_script( 'tipsy', BPHOVERCARDS_INC_URL . '/js/jquery.tipsy.js', array ( 'jquery' ), '1.0.0a', true );
                wp_enqueue_script( 'tipsy-hovercard', BPHOVERCARDS_INC_URL . '/js/jquery.tipsy.buddypress.hovercard.js', array ( 'jquery','tipsy' ), BPHOVERCARDS_VERSION, true );

                wp_localize_script( 'tipsy-hovercard', 'bphc', array(
                    'parent_filter'     => apply_filters('bphc_parent_filter', $this->parent_filter ),
                    'element_filter'    => apply_filters('bphc_element_filter', $this->element_filter )
                ));

                wp_enqueue_style( 'tipsy-css', BPHOVERCARDS_INC_URL . '/css/tipsy.css' );
                wp_enqueue_style( 'tipsy-hovercard-css', BPHOVERCARDS_INC_URL . '/css/tipsy.hovercard.css' );
            }
        }

        /**
         * AJAX Callback for the hovercard itself
         *
         * Loads the hovercard template ('hovercard.php') from (1) the child theme dir, (2) the theme dir and lastly (3) the plugin.
         *
         * @since 0.9
         */
        public function ajax_hovercard() {
            $this->locate_template( 'hovercard.php', true );
            exit;
        }

        /**
         * Loads a template file
         *
         * @param str $template_file
         * @param boolean $load
         * @param boolean $require_once
         * @return boolean|string Path to template file or false if not found.
         */
        protected function locate_template ( $template_file, $load = false, $require_once = true ) {

            $located = '';
            if ( file_exists( STYLESHEETPATH . '/' . $template_file ) ) {
                $located = STYLESHEETPATH . '/' . $template_file;
            } else if ( file_exists( TEMPLATEPATH . '/' . $template_file ) ) {
                $located = TEMPLATEPATH . '/' . $template_file;
            } else if ( file_exists( BPHOVERCARDS_TEMPLATES_DIR . $template_file ) ) {
                $located = BPHOVERCARDS_TEMPLATES_DIR . $template_file;
            }

            if ( '' == $located )
                return false;
            elseif ( ! $load ) {
                return $located;
            }

            load_template( $located, $require_once );
        }
    }
    add_action( 'bp_include', array( 'BuddyPress_Hovercards', 'init' ) );
endif;