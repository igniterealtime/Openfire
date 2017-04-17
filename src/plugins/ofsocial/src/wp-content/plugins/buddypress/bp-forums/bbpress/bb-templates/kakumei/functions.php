<?php
/* Functions for Kakumei Theme */

/* The below actions are removed because we add our own checkbox (check post-form.php and edit-form.php) */
remove_action( 'post_form', 'bb_user_subscribe_checkbox' );
remove_action( 'edit_form', 'bb_user_subscribe_checkbox' );
