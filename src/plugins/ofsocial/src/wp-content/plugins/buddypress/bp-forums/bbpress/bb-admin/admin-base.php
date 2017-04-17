<?php
require_once('admin.php');

do_action( $bb_admin_page . '_pre_head' );

bb_get_admin_header(); 
?>

<div class="wrap">

<?php if ( is_callable($bb_admin_page) ) : call_user_func( $bb_admin_page ); else : ?>

<h2><?php _e( 'Page not found' ); ?></h2>
<?php
bb_admin_notice( __( 'There is no administration page at the requested address. Please check the address you entered and try again.' ), 'error' );
do_action( 'bb_admin_notices' );
?>

<?php endif; ?>

</div>

<?php bb_get_admin_footer(); ?>
