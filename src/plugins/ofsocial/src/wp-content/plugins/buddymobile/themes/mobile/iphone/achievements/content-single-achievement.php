<?php
/**
 * Single Achievement content part
 *
 * @package Achievements
 * @subpackage ThemeCompatibility
 */

// Exit if accessed directly
if ( ! defined( 'ABSPATH' ) ) exit;
?>

<div id="dpa-achievements">

	<?php do_action( 'dpa_template_before_single_achievement' ); ?>

	<div id="dpa-achievement-<?php dpa_achievement_id(); ?>" <?php dpa_achievement_class(); ?>>

		<?php do_action( 'dpa_template_before_achievement_content' ); ?>

		<?php dpa_achievement_content(); ?>

		<?php do_action( 'dpa_template_after_achievement_content' ); ?>


		<?php if ( dpa_has_progress() ) : ?>

			<?php dpa_get_template_part( 'loop-progresses' ); ?>

		<?php endif; ?>

	</div><!-- #dpa-achievement-<?php dpa_achievement_id(); ?> -->

	<?php do_action( 'dpa_template_after_single_achievement' ); ?>

</div>
