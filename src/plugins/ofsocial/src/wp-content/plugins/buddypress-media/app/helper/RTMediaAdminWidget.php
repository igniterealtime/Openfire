<?php
/**
 * Description of RTMediaWidget
 *
 * @author Gagandeep Singh <gagandeep.singh@rtcamp.com>
 * @author Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
if ( ! class_exists( 'RTMediaAdminWidget' ) ){

	class RTMediaAdminWidget {

		/**
		 * Constructs the RTMediaAdminWidget.
		 *
		 * @global type  $rtmedia
		 * @param  type  $id
		 * @param  type  $title
		 * @param  type  $content
		 */
		public function __construct( $id = null, $title = null, $content = null ) {
			global $rtmedia;
			if ( $id ){
				?>
				<div class="postbox" id="<?php echo $id; ?>">
				<?php if ( $title ){ ?>
						<h3 class="hndle"><span><?php echo $title; ?></span></h3>
				<?php }
				?>
				    <div class="inside"><?php echo $content; ?></div>
				</div><?php
			} else {
				trigger_error( __( 'Argument missing. id is required.', 'buddypress-media' ) );
			}
		}

	}

}