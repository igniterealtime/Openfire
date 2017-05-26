<?php
/**
 * Home page
 *
 * @package Status
 * @since 1.0
 */
?>

<?php get_header(); ?>
		<?php locate_template( array( 'activity/index.php' ), true ); ?>
<?php get_footer( ); ?>