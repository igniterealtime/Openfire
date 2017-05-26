<?php get_header(); ?>
	<?php do_action( 'bp_before_register_page' ) ?>
	<div id="content-register" class="primary" role="main">

		<form action="" name="signup_form" id="signup_form" class="standard-form" method="post" enctype="multipart/form-data">

			<?php if ( 'registration-disabled' == bp_get_current_signup_step() ) : ?>
			<section id="signup-disabled" class="status-signup-disbled">
				<?php do_action( 'template_notices' ) ?>
				<?php do_action( 'bp_before_registration_disabled' ) ?>

					<p><?php _e( 'User registration is currently not allowed.', 'status' ); ?></p>

				<?php do_action( 'bp_after_registration_disabled' ); ?>
			</section>
			<?php endif; // registration-disabled signup setp ?>

			<?php if ( 'request-details' == bp_get_current_signup_step() ) : ?>
			<section id="do-signup" class="status-signup-steps">
				<h2><?php _e( 'Create an Account', 'status' ) ?></h2>

				<?php do_action( 'template_notices' ) ?>

				<p><?php _e( 'Registering for this site is easy, just fill in the fields below and we\'ll get a new account set up for you in no time.', 'status' ) ?></p>

				<?php do_action( 'bp_before_account_details_fields' ) ?>

				<fieldset class="register-section" id="basic-details-section">

					<?php /***** Basic Account Details ******/ ?>

					<legend><?php _e( 'Account Details', 'status' ) ?></legend>

					<div class="control-pairs">
						<label for="signup_username">
							<?php _e( 'Username', 'status' ) ?> <span class="label-required"><?php _e( '(required)', 'status' ) ?></span>
						</label>
						<?php do_action( 'bp_signup_username_errors' ) ?>
						<input type="text" name="signup_username" id="signup_username" value="<?php bp_signup_username_value() ?>" required placeholder="Your User Name" />
					</div>

					<div class="control-pairs">
					<label for="signup_email"><?php _e( 'Email Address', 'status' ) ?> <span class="label-required"><?php _e( '(required)', 'status' ) ?></span></label>
					<?php do_action( 'bp_signup_email_errors' ) ?>
					<input type="email" name="signup_email" id="signup_email" value="<?php bp_signup_email_value() ?>" required placeholder="myemail@example.com" />
					</div>

					<div class="control-pairs">
					<label for="signup_password"><?php _e( 'Choose a Password', 'status' ) ?> <span class="label-required"><?php _e( '(required)', 'status' ) ?></span></label>
					<?php do_action( 'bp_signup_password_errors' ) ?>
					<input type="password" name="signup_password" id="signup_password" value="" required placeholder="Min 6 mixed alpha/numeric characters" />
					</div>

					<div class="control-pairs">
					<label for="signup_password_confirm"><?php _e( 'Confirm Password', 'status' ) ?> <span class="label-required"><?php _e( '(required)', 'status' ) ?></span></label>
					<?php do_action( 'bp_signup_password_confirm_errors' ) ?>
					<input type="password" name="signup_password_confirm" id="signup_password_confirm" value="" required placeholder="please repeat password" />
					</div>

				</fieldset><!-- #basic-details-section -->

				<?php do_action( 'bp_after_account_details_fields' ) ?>

				<?php /***** Extra Profile Details ******/ ?>

				<?php if ( bp_is_active( 'xprofile' ) && bp_has_profile( 'profile_group_id=1' ) ) :?>

					<?php do_action( 'bp_before_signup_profile_fields' ) ?>

					<fieldset class="register-section" id="profile-details-section">

						<legend><?php _e( 'Profile Details', 'status' ) ?></legend>

						<?php /* Use the profile field loop to render input fields for the 'base' profile field group */ ?>
						<?php if ( bp_is_active( 'xprofile' ) ) : if ( bp_has_profile( 'profile_group_id=1' ) ) : while ( bp_profile_groups() ) : bp_the_profile_group(); ?>

						<?php while ( bp_profile_fields() ) : bp_the_profile_field(); ?>

							<div class="editfield">

								<?php if ( 'textbox' == bp_get_the_profile_field_type() ) : ?>
									<div class="control-pairs">
										<label for="<?php bp_the_profile_field_input_name() ?>"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></label>
										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
										<input type="text" name="<?php bp_the_profile_field_input_name() ?>" id="<?php bp_the_profile_field_input_name() ?>" value="<?php bp_the_profile_field_edit_value() ?>" />
									</div>
								<?php endif; ?>

								<?php if ( 'textarea' == bp_get_the_profile_field_type() ) : ?>
									<div class="control-pairs">
										<label for="<?php bp_the_profile_field_input_name() ?>"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?><?php endif; ?></span></label>
										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
										<textarea rows="5" cols="40" name="<?php bp_the_profile_field_input_name() ?>" id="<?php bp_the_profile_field_input_name() ?>"><?php bp_the_profile_field_edit_value() ?></textarea>
									</div>
								<?php endif; ?>

								<?php if ( 'selectbox' == bp_get_the_profile_field_type() ) : ?>
									<div class="control-pairs">
										<label for="<?php bp_the_profile_field_input_name() ?>"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></label>
										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
										<select name="<?php bp_the_profile_field_input_name() ?>" id="<?php bp_the_profile_field_input_name() ?>">
										<?php bp_the_profile_field_options() ?>
										</select>
									</div>
								<?php endif; ?>

								<?php if ( 'multiselectbox' == bp_get_the_profile_field_type() ) : ?>
									<div class="control-pairs">
										<label for="<?php bp_the_profile_field_input_name() ?>"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></label>
										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
										<select name="<?php bp_the_profile_field_input_name() ?>" id="<?php bp_the_profile_field_input_name() ?>" multiple="multiple">
										<?php bp_the_profile_field_options() ?>
										</select>
									</div>
								<?php endif; ?>

								<?php if ( 'radio' == bp_get_the_profile_field_type() ) : ?>

										<div class="radio control-pairs">
											<p class="label"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></p>

											<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
											<?php  bp_the_profile_field_options() ?>

											<?php if ( !bp_get_the_profile_field_is_required() ) : ?>
											<a class="clear-value" href="javascript:clear( '<?php bp_the_profile_field_input_name() ?>' );"><?php _e( 'Clear', 'status' ) ?></a>
											<?php endif; ?>
										</div>


								<?php endif; ?>

								<?php if ( 'checkbox' == bp_get_the_profile_field_type() ) : ?>

									<div class="checkbox control-pairs">
										<p class="label"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></p>

										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>
										<?php bp_the_profile_field_options() ?>
									</div>

								<?php endif; ?>

								<?php if ( 'datebox' == bp_get_the_profile_field_type() ) : ?>

									<div class="datebox control-pairs">

										<label for="<?php bp_the_profile_field_input_name() ?>_day"><?php bp_the_profile_field_name() ?> <?php if ( bp_get_the_profile_field_is_required() ) : ?><span class="label-required"><?php _e( '(required)', 'status' ) ?></span><?php endif; ?></label>
										<?php do_action( 'bp_' . bp_get_the_profile_field_input_name() . '_errors' ) ?>

										<select name="<?php bp_the_profile_field_input_name() ?>_day" id="<?php bp_the_profile_field_input_name() ?>_day">
											<?php bp_the_profile_field_options( 'type=day' ) ?>
										</select>

										<select name="<?php bp_the_profile_field_input_name() ?>_month" id="<?php bp_the_profile_field_input_name() ?>_month">
											<?php bp_the_profile_field_options( 'type=month' ) ?>
										</select>

										<select name="<?php bp_the_profile_field_input_name() ?>_year" id="<?php bp_the_profile_field_input_name() ?>_year">
											<?php bp_the_profile_field_options( 'type=year' ) ?>
										</select>

									</div>

								<?php endif; ?>

								<?php do_action( 'bp_custom_profile_edit_fields' ) ?>

								<?php if( bp_get_the_profile_field_description() ) : ?>
								<p class="description xprofile-desc"><?php bp_the_profile_field_description() ?></p>
								<?php endif; ?>

							</div><!-- / .editfield -->

						<?php endwhile; // bp_profile_fields() ?>

						<input type="hidden" name="signup_profile_field_ids" id="signup_profile_field_ids" value="<?php bp_the_profile_group_field_ids() ?>" />

						<?php endwhile; endif; endif; ?>

					</fieldset><!-- #profile-details-section -->
					<?php /***** End Extra Profile Details ******/ ?>

					<?php do_action( 'bp_after_signup_profile_fields' ) ?>

				<?php endif; ?>


				<?php do_action( 'bp_before_registration_submit_buttons' ) ?>

				<div class="submit">
					<input type="submit" name="signup_submit" id="signup_submit" value="<?php _e( 'Complete Sign Up', 'status' ) ?> &rarr;" />
				</div>

				<?php do_action( 'bp_after_registration_submit_buttons' ) ?>

				<?php wp_nonce_field( 'bp_new_signup' ) ?>
			</section>
			<?php endif; // request-details signup step ?>

			<?php if ( 'completed-confirmation' == bp_get_current_signup_step() ) : ?>

			<section id="sign-up-confirm" class="sign-up-complete">
					<h2><?php _e( 'Sign Up Complete!', 'status' ) ?></h2>

					<?php do_action( 'template_notices' ) ?>
					<?php do_action( 'bp_before_registration_confirmed' ) ?>

					<?php if ( bp_registration_needs_activation() ) : ?>
						<p><?php _e( 'You have successfully created your account! To begin using this site you will need to activate your account via the email we have just sent to your address.', 'status' ) ?></p>
					<?php else : ?>
						<p><?php _e( 'You have successfully created your account! Please log in using the username and password you have just created.', 'status' ) ?></p>
					<?php endif; ?>

					<?php do_action( 'bp_after_registration_confirmed' ) ?>
			</section>
			<?php endif; // completed-confirmation signup step ?>

			<?php do_action( 'bp_custom_signup_steps' ) ?>

			</form>

	</div><!-- / #content -->
	<?php do_action( 'bp_after_register_page' ); ?>
<?php get_footer(); ?>