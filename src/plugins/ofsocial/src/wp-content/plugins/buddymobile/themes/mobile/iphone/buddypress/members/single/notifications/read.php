<?php if ( bp_has_notifications() ) : ?>

	<?php bp_get_template_part( 'members/single/notifications/notifications-loop' ); ?>

<?php else : ?>

	<?php bp_get_template_part( 'members/single/notifications/feedback-no-notifications' ); ?>

<?php endif;