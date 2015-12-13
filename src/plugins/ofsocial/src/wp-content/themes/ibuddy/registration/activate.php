<?php get_header(); ?>
		<?php do_action( 'bp_before_activation_page' ); ?>

		<div class="page" id="activate-page">
		<div id="intro-mem-home">
			<h2><?php if ( bp_account_was_activated() ) :
				_e( 'Account Activated', 'buddypress' );
			else :
				_e( 'Activate your Account', 'buddypress' );
			endif; ?></h2>

			<?php do_action( 'template_notices' ); ?>

			<?php do_action( 'bp_before_activate_content' ); ?>

			<?php if ( bp_account_was_activated() ) : ?>

				<?php if ( isset( $_GET['e'] ) ) : ?>
					<p><?php _e( 'Your account was activated successfully! Your account details have been sent to you in a separate email.', 'buddypress' ); ?></p>
				<?php else : ?>
					<p><?php _e( 'Your account was activated successfully! You can now log in with the username and password you provided when you signed up.', 'buddypress' ); ?></p>
				<?php endif; ?>

			<?php else : ?>

				<p><?php _e( 'Please provide a valid activation key.', 'buddypress' ); ?></p>

				<form action="" method="get" class="standard-form" id="activation-form">

					<label for="key"><?php _e( 'Activation Key:', 'buddypress' ); ?></label>
					<input type="text" name="key" id="key" value="" />

					<p class="submit">
						<input type="submit" name="submit" value="<?php _e( 'Activate', 'buddypress' ); ?>" />
					</p>

				</form>

			<?php endif; ?>

			<?php do_action( 'bp_after_activate_content' ); ?>
			
			</div><!-- #intro-mem-home -->

			<!-- Login Form -->
		<div id="home-login">
			<h2><?php _e('Login', 'ibuddy') ?></h2>
					<?php if((!is_user_logged_in()) && ($_GET['login'] === 'failed')) : ?>
					<div class="error">
						<p><?php _e('Invalid User Name or Password, Please try again','ibuddy'); ?></p>
					</div>
					<?php endif; ?>
				<?php wp_login_form(); ?>
			</div><!-- #home-login -->
		</div><!-- .page -->

		<?php do_action( 'bp_after_activation_page' ); ?>

<?php get_footer(); ?>