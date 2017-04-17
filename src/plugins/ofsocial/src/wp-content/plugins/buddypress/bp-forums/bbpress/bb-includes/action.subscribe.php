<?php

if ( !isset( $_GET['doit'] ) || 'bb-subscribe' != $_GET['doit'] ) // sanity check
	bb_die( __( 'What are you trying to do, exactly?' ) );

if ( !bb_is_subscriptions_active() )
	bb_die( __( 'You can not subscribe to topics.' ) );

if ( !isset( $_GET['topic_id'] ) )
	bb_die( __( 'Missing topic ID!' ) );

bb_auth( 'logged_in' );

$topic_id = (int) $_GET['topic_id'];

$topic = get_topic( $topic_id );
if ( !$topic )
	bb_die( __( 'Topic not found! What are you subscribing to?' ) );

bb_check_admin_referer( 'toggle-subscribe_' . $topic_id );

// Okay, we should be covered now

if ( in_array( $_GET['and'], array( 'add', 'remove' ) ) )
	bb_subscription_management( $topic->topic_id, $_GET['and'] );

wp_redirect( get_topic_link( $topic_id, 1 ) );

exit;
