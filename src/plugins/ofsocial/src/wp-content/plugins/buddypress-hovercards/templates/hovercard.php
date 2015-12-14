<?php if ( bp_has_members( "include={$_POST['userid']}&max=1") ) : ?>

    <?php while ( bp_members() ) : bp_the_member();
        global $members_template; ?>

        <?php /* The loop for the member you're showing a hovercard for is set up. Place hovercard code here */ ?>

        <div class="tipsy-avatar">
            <img src="<?php echo bp_core_fetch_avatar( array( 'item_id' => bp_get_member_user_id(), 'type' => 'full', 'width' => 100, 'height' => 100, 'html' => false ) ); ?>">
        </div>

        <div class='tipsy-content'>

            <div class="user">

                <h3>
                    <?php // iexpert_skype_status( xprofile_get_field_data( 'skype', bp_get_member_user_id() ) ); ?>

                    <a href="<?php bp_member_link(); ?>"><?php bp_member_name(); ?></a>
                </h3>

                <div><strong><?php bp_member_last_active(); ?></strong></div>
                @<?php bp_member_user_nicename(); ?>
            </div>

            <div class="update"><strong><?php _e("Last update", 'bp-hovercards'); ?></strong><br><?php bp_member_latest_update( 'length=68' ) ?></div>

            <a href="<?php bp_member_link() ?>"><?php _e("View profile", 'bp-hovercards'); ?></a>

        </div>

        <div class="clear">

    <?php endwhile; ?>

<?php endif; ?>