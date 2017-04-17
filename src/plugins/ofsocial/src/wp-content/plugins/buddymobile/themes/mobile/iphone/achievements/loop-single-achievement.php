<?php
/**
 * Achievements loop - single
 *
 * @package Achievements
 * @subpackage ThemeCompatibility
 */

// Exit if accessed directly
if ( ! defined( 'ABSPATH' ) ) exit;
?>

<tr id="dpa-achievement-<?php dpa_achievement_id(); ?>" <?php dpa_achievement_class(); ?>>

	<?php do_action( 'dpa_template_in_achievements_loop_early' ); ?>


	<td headers="dpa-archive-achievements-name">
		<?php do_action( 'dpa_template_before_achievement_name' ); ?>

		<a href="<?php dpa_achievement_permalink(); ?>"><?php dpa_achievement_title(); ?></a>

		<?php do_action( 'dpa_template_after_achievement_name' ); ?>
	</td>


	<td headers="dpa-archive-achievements-karma">
		<?php do_action( 'dpa_template_before_achievement_karma' ); ?>

		<?php dpa_achievement_points(); ?>

		<?php do_action( 'dpa_template_after_achievement_karma' ); ?>
	</td>


	<td headers="dpa-archive-achievements-excerpt">
		<?php do_action( 'dpa_template_before_achievement_excerpt' ); ?>

		<?php dpa_achievement_excerpt(); ?>

		<?php do_action( 'dpa_template_after_achievement_excerpt' ); ?>
	</td>


	<?php do_action( 'dpa_template_in_achievements_loop_late' ); ?>

</tr>
