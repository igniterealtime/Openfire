/* jshint undef: false, unused:false */
// AJAX Functions
var jq = jQuery;

// Global variable to prevent multiple AJAX requests
var bp_ajax_request = null;

// Global variables to temporarily store newest activities
var newest_activities = '';
var activity_last_recorded  = 0;

jq(document).ready( function() {
	/**** Page Load Actions *******************************************************/

	/* Hide Forums Post Form */
	if ( '-1' === window.location.search.indexOf('new') && jq('div.forums').length ) {
		jq('#new-topic-post').hide();
	} else {
		jq('#new-topic-post').show();
	}

	/* Activity filter and scope set */
	bp_init_activity();

	var objects  = [ 'members', 'groups', 'blogs', 'forums', 'group_members' ],
		$whats_new = jq('#whats-new');

	/* Object filter and scope set. */
	bp_init_objects( objects );

	/* @mention Compose Scrolling */
	if ( $whats_new.length && bp_get_querystring('r') ) {
		var $member_nicename = $whats_new.val();

		jq('#whats-new-options').slideDown();

		$whats_new.animate({
			height:'3.8em'
		});

		jq.scrollTo( $whats_new, 500, {
			offset:-125,
			easing:'swing'
		} );

		$whats_new.val('').focus().val( $member_nicename );
	} else {
		jq('#whats-new-options').hide();
	}

	/**** Activity Posting ********************************************************/

	/* Textarea focus */
	$whats_new.focus( function(){
		jq( '#whats-new-options' ).slideDown();

		jq( this ).animate({
			height:'3.8em'
		});

		jq('#aw-whats-new-submit').prop('disabled', false);

		jq( this ).parent().addClass( 'active' );
		jq( '#whats-new-content' ).addClass( 'active' );

		var $whats_new_form = jq('form#whats-new-form'),
			$activity_all = jq( '#activity-all' );

		if ( $whats_new_form.hasClass('submitted') ) {
			$whats_new_form.removeClass('submitted');
		}

		// Return to the 'All Members' tab and 'Everything' filter,
		// to avoid inconsistencies with the heartbeat integration
		if ( $activity_all.length  ) {
			if ( ! $activity_all.hasClass( 'selected' ) ) {
				// reset to everything
				jq( '#activity-filter-select select' ).val( '-1' );
				$activity_all.children( 'a' ).trigger( 'click' );
			} else if ( '-1' !== jq( '#activity-filter-select select' ).val() ) {
				jq( '#activity-filter-select select' ).val( '-1' );
				jq( '#activity-filter-select select' ).trigger( 'change' );
			}
		}
	});

	/* For the "What's New" form, do the following on focusout. */
	jq( '#whats-new-form' ).on( 'focusout', function( e ) {
		var elem = jq( this );

		// Let child hover actions passthrough.
		// This allows click events to go through without focusout.
		setTimeout( function () {
			if ( ! elem.find(':hover').length ) {
				// Do not slide up if textarea has content.
				if ( '' !== $whats_new.val() ) {
					return;
				}

				$whats_new.animate({
					height:'2.2em'
				});

				jq( '#whats-new-options' ).slideUp();

				jq('#aw-whats-new-submit').prop( 'disabled', true );

				jq( '#whats-new-content' ).removeClass( 'active' );
				$whats_new.parent().removeClass( 'active' );
			}
		}, 0 );
	} );

	/* New posts */
	jq('#aw-whats-new-submit').on( 'click', function() {
		var last_date_recorded = 0,
			button = jq(this),
			form   = button.closest('form#whats-new-form'),
			inputs = {}, post_data;

		// Get all inputs and organize them into an object {name: value}
		jq.each( form.serializeArray(), function( key, input ) {
			// Only include public extra data
			if ( '_' !== input.name.substr( 0, 1 ) && 'whats-new' !== input.name.substr( 0, 9 ) ) {
				if ( ! inputs[ input.name ] ) {
					inputs[ input.name ] = input.value;
				} else {
					// Checkboxes/dropdown list can have multiple selected value
					if ( ! jq.isArray( inputs[ input.name ] ) ) {
						inputs[ input.name ] = new Array( inputs[ input.name ], input.value );
					} else {
						inputs[ input.name ].push( input.value );
					}
				}
			}
		} );

		form.find( '*' ).each( function() {
			if ( jq.nodeName( this, 'textarea' ) || jq.nodeName( this, 'input' ) ) {
				jq(this).prop( 'disabled', true );
			}
		} );

		/* Remove any errors */
		jq('div.error').remove();
		button.addClass('loading');
		button.prop('disabled', true);
		form.addClass('submitted');

		/* Default POST values */
		object = '';
		item_id = jq('#whats-new-post-in').val();
		content = jq('#whats-new').val();
		firstrow = jq( '#buddypress ul.activity-list li' ).first();
		activity_row = firstrow;
		timestamp = null;

		// Checks if at least one activity exists
		if ( firstrow.length ) {

			if ( activity_row.hasClass( 'load-newest' ) ) {
				activity_row = firstrow.next();
			}

			timestamp = activity_row.prop( 'class' ).match( /date-recorded-([0-9]+)/ );
		}

		if ( timestamp ) {
			last_date_recorded = timestamp[1];
		}

		/* Set object for non-profile posts */
		if ( item_id > 0 ) {
			object = jq('#whats-new-post-object').val();
		}

		post_data = jq.extend( {
			action: 'post_update',
			'cookie': bp_get_cookies(),
			'_wpnonce_post_update': jq('#_wpnonce_post_update').val(),
			'content': content,
			'object': object,
			'item_id': item_id,
			'since': last_date_recorded,
			'_bp_as_nonce': jq('#_bp_as_nonce').val() || ''
		}, inputs );

		jq.post( ajaxurl, post_data, function( response ) {
			form.find( '*' ).each( function() {
				if ( jq.nodeName( this, 'textarea' ) || jq.nodeName( this, 'input' ) ) {
					jq(this).prop( 'disabled', false );
				}
			});

			/* Check for errors and append if found. */
			if ( response[0] + response[1] === '-1' ) {
				form.prepend( response.substr( 2, response.length ) );
				jq( '#' + form.attr('id') + ' div.error').hide().fadeIn( 200 );
			} else {
				if ( 0 === jq('ul.activity-list').length ) {
					jq('div.error').slideUp(100).remove();
					jq('#message').slideUp(100).remove();
					jq('div.activity').append( '<ul id="activity-stream" class="activity-list item-list">' );
				}

				if ( firstrow.hasClass( 'load-newest' ) ) {
					firstrow.remove();
				}

				jq('#activity-stream').prepend(response);

				if ( ! last_date_recorded ) {
					jq('#activity-stream li:first').addClass('new-update just-posted');
				}

				if ( 0 !== jq('#latest-update').length ) {
					var l   = jq('#activity-stream li.new-update .activity-content .activity-inner p').html(),
						v     = jq('#activity-stream li.new-update .activity-content .activity-header p a.view').attr('href'),
						ltext = jq('#activity-stream li.new-update .activity-content .activity-inner p').text(),
						u     = '';

					if ( ltext !== '' ) {
						u = l + ' ';
					}

					u += '<a href="' + v + '" rel="nofollow">' + BP_DTheme.view + '</a>';

					jq('#latest-update').slideUp(300,function(){
						jq('#latest-update').html( u );
						jq('#latest-update').slideDown(300);
					});
				}

				jq('li.new-update').hide().slideDown( 300 );
				jq('li.new-update').removeClass( 'new-update' );
				jq('#whats-new').val('');
				form.get(0).reset();

				// reset vars to get newest activities
				newest_activities = '';
				activity_last_recorded  = 0;
			}

			jq('#whats-new-options').slideUp();
			jq('#whats-new-form textarea').animate({
				height:'2.2em'
			});
			jq('#aw-whats-new-submit').prop('disabled', true).removeClass('loading');
			jq( '#whats-new-content' ).removeClass( 'active' );
		});

		return false;
	});

	/* List tabs event delegation */
	jq('div.activity-type-tabs').on( 'click', function(event) {
		var target = jq(event.target).parent(),
			scope, filter;

		if ( event.target.nodeName === 'STRONG' || event.target.nodeName === 'SPAN' ) {
			target = target.parent();
		} else if ( event.target.nodeName !== 'A' ) {
			return false;
		}

		/* Reset the page */
		jq.cookie( 'bp-activity-oldestpage', 1, {
			path: '/'
		} );

		/* Activity Stream Tabs */
		scope  = target.attr('id').substr( 9, target.attr('id').length );
		filter = jq('#activity-filter-select select').val();

		if ( scope === 'mentions' ) {
			jq( '#' + target.attr('id') + ' a strong' ).remove();
		}

		bp_activity_request(scope, filter);

		return false;
	});

	/* Activity filter select */
	jq('#activity-filter-select select').change( function() {
		var selected_tab = jq( 'div.activity-type-tabs li.selected' ),
			filter = jq(this).val(),
			scope;

		if ( !selected_tab.length ) {
			scope = null;
		} else {
			scope = selected_tab.attr('id').substr( 9, selected_tab.attr('id').length );
		}

		bp_activity_request(scope, filter);

		return false;
	});

	/* Stream event delegation */
	jq('div.activity').on( 'click', function(event) {
		var target = jq(event.target),
			type, parent, parent_id,
			li, id, link_href, nonce, timestamp,
			oldest_page, just_posted;

		/* Favoriting activity stream items */
		if ( target.hasClass('fav') || target.hasClass('unfav') ) {
			type      = target.hasClass('fav') ? 'fav' : 'unfav';
			parent    = target.closest('.activity-item');
			parent_id = parent.attr('id').substr( 9, parent.attr('id').length );

			target.addClass('loading');

			jq.post( ajaxurl, {
				action: 'activity_mark_' + type,
				'cookie': bp_get_cookies(),
				'id': parent_id
			},
			function(response) {
				target.removeClass('loading');

				target.fadeOut( 200, function() {
					jq(this).html(response);
					jq(this).attr('title', 'fav' === type ? BP_DTheme.remove_fav : BP_DTheme.mark_as_fav);
					jq(this).fadeIn(200);
				});

				if ( 'fav' === type ) {
					if ( !jq('.item-list-tabs #activity-favs-personal-li').length ) {
						if ( !jq('.item-list-tabs #activity-favorites').length ) {
							jq('.item-list-tabs ul #activity-mentions').before( '<li id="activity-favorites"><a href="#">' + BP_DTheme.my_favs + ' <span>0</span></a></li>');
						}

						jq('.item-list-tabs ul #activity-favorites span').html( Number( jq('.item-list-tabs ul #activity-favorites span').html() ) + 1 );
					}

					target.removeClass('fav');
					target.addClass('unfav');

				} else {
					target.removeClass('unfav');
					target.addClass('fav');

					jq('.item-list-tabs ul #activity-favorites span').html( Number( jq('.item-list-tabs ul #activity-favorites span').html() ) - 1 );

					if ( !Number( jq('.item-list-tabs ul #activity-favorites span').html() ) ) {
						if ( jq('.item-list-tabs ul #activity-favorites').hasClass('selected') ) {
							bp_activity_request( null, null );
						}

						jq('.item-list-tabs ul #activity-favorites').remove();
					}
				}

				if ( 'activity-favorites' === jq( '.item-list-tabs li.selected').attr('id') ) {
					target.closest( '.activity-item' ).slideUp( 100 );
				}
			});

			return false;
		}

		/* Delete activity stream items */
		if ( target.hasClass('delete-activity') ) {
			li        = target.parents('div.activity ul li');
			id        = li.attr('id').substr( 9, li.attr('id').length );
			link_href = target.attr('href');
			nonce     = link_href.split('_wpnonce=');
			timestamp = li.prop( 'class' ).match( /date-recorded-([0-9]+)/ );
			nonce     = nonce[1];

			target.addClass('loading');

			jq.post( ajaxurl, {
				action: 'delete_activity',
				'cookie': bp_get_cookies(),
				'id': id,
				'_wpnonce': nonce
			},
			function(response) {

				if ( response[0] + response[1] === '-1' ) {
					li.prepend( response.substr( 2, response.length ) );
					li.children('#message').hide().fadeIn(300);
				} else {
					li.slideUp(300);

					// reset vars to get newest activities
					if ( timestamp && activity_last_recorded === timestamp[1] ) {
						newest_activities = '';
						activity_last_recorded  = 0;
					}
				}
			});

			return false;
		}

		// Spam activity stream items
		if ( target.hasClass( 'spam-activity' ) ) {
			li        = target.parents( 'div.activity ul li' );
			timestamp = li.prop( 'class' ).match( /date-recorded-([0-9]+)/ );
			target.addClass( 'loading' );

			jq.post( ajaxurl, {
				action: 'bp_spam_activity',
				'cookie': encodeURIComponent( document.cookie ),
				'id': li.attr( 'id' ).substr( 9, li.attr( 'id' ).length ),
				'_wpnonce': target.attr( 'href' ).split( '_wpnonce=' )[1]
			},

			function(response) {
				if ( response[0] + response[1] === '-1' ) {
					li.prepend( response.substr( 2, response.length ) );
					li.children( '#message' ).hide().fadeIn(300);
				} else {
					li.slideUp( 300 );
					// reset vars to get newest activities
					if ( timestamp && activity_last_recorded === timestamp[1] ) {
						newest_activities = '';
						activity_last_recorded  = 0;
					}
				}
			});

			return false;
		}

		/* Load more updates at the end of the page */
		if ( target.parent().hasClass('load-more') ) {
			if ( bp_ajax_request ) {
				bp_ajax_request.abort();
			}

			jq('#buddypress li.load-more').addClass('loading');

			if ( null === jq.cookie('bp-activity-oldestpage') ) {
				jq.cookie('bp-activity-oldestpage', 1, {
					path: '/'
				} );
			}

			oldest_page = ( jq.cookie('bp-activity-oldestpage') * 1 ) + 1;
			just_posted = [];

			jq('.activity-list li.just-posted').each( function(){
				just_posted.push( jq(this).attr('id').replace( 'activity-','' ) );
			});

			load_more_args = {
				action: 'activity_get_older_updates',
				'cookie': bp_get_cookies(),
				'page': oldest_page,
				'exclude_just_posted': just_posted.join(',')
			};

			load_more_search = bp_get_querystring('s');

			if ( load_more_search ) {
				load_more_args.search_terms = load_more_search;
			}

			bp_ajax_request = jq.post( ajaxurl, load_more_args,
			function(response)
			{
				jq('#buddypress li.load-more').removeClass('loading');
				jq.cookie( 'bp-activity-oldestpage', oldest_page, {
					path: '/'
				} );
				jq('#buddypress ul.activity-list').append(response.contents);

				target.parent().hide();
			}, 'json' );

			return false;
		}

		/* Load newest updates at the top of the list */
		if ( target.parent().hasClass('load-newest') ) {

			event.preventDefault();

			target.parent().hide();

			/**
			 * If a plugin is updating the recorded_date of an activity
			 * it will be loaded as a new one. We need to look in the
			 * stream and eventually remove similar ids to avoid "double".
			 */
			activity_html = jq.parseHTML( newest_activities );

			jq.each( activity_html, function( i, el ){
				if( 'LI' === el.nodeName && jq(el).hasClass( 'just-posted' ) ) {
					if( jq( '#' + jq(el).attr( 'id' ) ).length ) {
						jq( '#' + jq(el).attr( 'id' ) ).remove();
					}
				}
			} );

			// Now the stream is cleaned, prepend newest
			jq( '#buddypress ul.activity-list' ).prepend( newest_activities );

			// reset the newest activities now they're displayed
			newest_activities = '';
		}
	});

	// Activity "Read More" links
	jq('div.activity').on('click', '.activity-read-more a', function(event) {
		var target = jq(event.target),
			link_id = target.parent().attr('id').split('-'),
			a_id    = link_id[3],
			type    = link_id[0], /* activity or acomment */
			inner_class, a_inner;

		inner_class = type === 'acomment' ? 'acomment-content' : 'activity-inner';
		a_inner = jq('#' + type + '-' + a_id + ' .' + inner_class + ':first' );
		jq(target).addClass('loading');

		jq.post( ajaxurl, {
			action: 'get_single_activity_content',
			'activity_id': a_id
		},
		function(response) {
			jq(a_inner).slideUp(300).html(response).slideDown(300);
		});

		return false;
	});

	/**** Activity Comments *******************************************************/

	/* Hide all activity comment forms */
	jq('form.ac-form').hide();

	/* Hide excess comments */
	if ( jq('.activity-comments').length ) {
		bp_legacy_theme_hide_comments();
	}

	/* Activity list event delegation */
	jq('div.activity').on( 'click', function(event) {
		var target = jq(event.target),
			id, ids, a_id, c_id, form,
			form_parent, form_id,
			tmp_id, comment_id, comment,
			ajaxdata,
			ak_nonce,
			show_all_a, new_count,
			link_href, comment_li, nonce;

		/* Comment / comment reply links */
		if ( target.hasClass('acomment-reply') || target.parent().hasClass('acomment-reply') ) {
			if ( target.parent().hasClass('acomment-reply') ) {
				target = target.parent();
			}

			id  = target.attr('id');
			ids = id.split('-');

			a_id = ids[2];
			c_id = target.attr('href').substr( 10, target.attr('href').length );
			form = jq( '#ac-form-' + a_id );

			form.css( 'display', 'none' );
			form.removeClass('root');
			jq('.ac-form').hide();

			/* Hide any error messages */
			form.children('div').each( function() {
				if ( jq(this).hasClass( 'error' ) ) {
					jq(this).hide();
				}
			});

			if ( ids[1] !== 'comment' ) {
				jq('#acomment-' + c_id).append( form );
			} else {
				jq('#activity-' + a_id + ' .activity-comments').append( form );
			}

			if ( form.parent().hasClass( 'activity-comments' ) ) {
				form.addClass('root');
			}

			form.slideDown( 200 );
			jq.scrollTo( form, 500, {
				offset:-100,
				easing:'swing'
			} );
			jq('#ac-form-' + ids[2] + ' textarea').focus();

			return false;
		}

		/* Activity comment posting */
		if ( target.attr('name') === 'ac_form_submit' ) {
			form = target.parents( 'form' );
			form_parent = form.parent();
			form_id = form.attr('id').split('-');

			if ( !form_parent.hasClass('activity-comments') ) {
				tmp_id = form_parent.attr('id').split('-');
				comment_id = tmp_id[1];
			} else {
				comment_id = form_id[2];
			}

			content = jq( '#' + form.attr('id') + ' textarea' );

			/* Hide any error messages */
			jq( '#' + form.attr('id') + ' div.error').hide();
			target.addClass('loading').prop('disabled', true);
			content.addClass('loading').prop('disabled', true);

			ajaxdata = {
				action: 'new_activity_comment',
				'cookie': bp_get_cookies(),
				'_wpnonce_new_activity_comment': jq('#_wpnonce_new_activity_comment').val(),
				'comment_id': comment_id,
				'form_id': form_id[2],
				'content': content.val()
			};

			// Akismet
			ak_nonce = jq('#_bp_as_nonce_' + comment_id).val();
			if ( ak_nonce ) {
				ajaxdata['_bp_as_nonce_' + comment_id] = ak_nonce;
			}

			jq.post( ajaxurl, ajaxdata, function(response) {
				target.removeClass('loading');
				content.removeClass('loading');

				/* Check for errors and append if found. */
				if ( response[0] + response[1] === '-1' ) {
					form.append( jq( response.substr( 2, response.length ) ).hide().fadeIn( 200 ) );
				} else {
					var activity_comments = form.parent();
					form.fadeOut( 200, function() {
						if ( 0 === activity_comments.children('ul').length ) {
							if ( activity_comments.hasClass('activity-comments') ) {
								activity_comments.prepend('<ul></ul>');
							} else {
								activity_comments.append('<ul></ul>');
							}
						}

						/* Preceding whitespace breaks output with jQuery 1.9.0 */
						var the_comment = jq.trim( response );

						activity_comments.children('ul').append( jq( the_comment ).hide().fadeIn( 200 ) );
						form.children('textarea').val('');
						activity_comments.parent().addClass('has-comments');
					} );
					jq( '#' + form.attr('id') + ' textarea').val('');

					/* Increase the "Reply (X)" button count */
					jq('#activity-' + form_id[2] + ' a.acomment-reply span').html( Number( jq('#activity-' + form_id[2] + ' a.acomment-reply span').html() ) + 1 );

					// Increment the 'Show all x comments' string, if present
					show_all_a = activity_comments.find('.show-all').find('a');
					if ( show_all_a ) {
						new_count = jq('li#activity-' + form_id[2] + ' a.acomment-reply span').html();
						show_all_a.html( BP_DTheme.show_x_comments.replace( '%d', new_count ) );
					}
				}

				jq(target).prop('disabled', false);
				jq(content).prop('disabled', false);
			});

			return false;
		}

		/* Deleting an activity comment */
		if ( target.hasClass('acomment-delete') ) {
			link_href = target.attr('href');
			comment_li = target.parent().parent();
			form = comment_li.parents('div.activity-comments').children('form');

			nonce = link_href.split('_wpnonce=');
			nonce = nonce[1];

			comment_id = link_href.split('cid=');
			comment_id = comment_id[1].split('&');
			comment_id = comment_id[0];

			target.addClass('loading');

			/* Remove any error messages */
			jq('.activity-comments ul .error').remove();

			/* Reset the form position */
			comment_li.parents('.activity-comments').append(form);

			jq.post( ajaxurl, {
				action: 'delete_activity_comment',
				'cookie': bp_get_cookies(),
				'_wpnonce': nonce,
				'id': comment_id
			},
			function(response) {
				/* Check for errors and append if found. */
				if ( response[0] + response[1] === '-1' ) {
					comment_li.prepend( jq( response.substr( 2, response.length ) ).hide().fadeIn( 200 ) );
				} else {
					var children  = jq( '#' + comment_li.attr('id') + ' ul' ).children('li'),
						child_count = 0,
						count_span, new_count, show_all_a;

					jq(children).each( function() {
						if ( !jq(this).is(':hidden') ) {
							child_count++;
						}
					});
					comment_li.fadeOut(200, function() {
						comment_li.remove();
					});

					/* Decrease the "Reply (X)" button count */
					count_span = jq('#' + comment_li.parents('#activity-stream > li').attr('id') + ' a.acomment-reply span');
					new_count = count_span.html() - ( 1 + child_count );
					count_span.html(new_count);

					// Change the 'Show all x comments' text
					show_all_a = comment_li.siblings('.show-all').find('a');
					if ( show_all_a ) {
						show_all_a.html( BP_DTheme.show_x_comments.replace( '%d', new_count ) );
					}

					/* If that was the last comment for the item, remove the has-comments class to clean up the styling */
					if ( 0 === new_count ) {
						jq(comment_li.parents('#activity-stream > li')).removeClass('has-comments');
					}
				}
			});

			return false;
		}

		// Spam an activity stream comment
		if ( target.hasClass( 'spam-activity-comment' ) ) {
			link_href  = target.attr( 'href' );
			comment_li = target.parent().parent();

			target.addClass('loading');

			// Remove any error messages
			jq( '.activity-comments ul div.error' ).remove();

			// Reset the form position
			comment_li.parents( '.activity-comments' ).append( comment_li.parents( '.activity-comments' ).children( 'form' ) );

			jq.post( ajaxurl, {
				action: 'bp_spam_activity_comment',
				'cookie': encodeURIComponent( document.cookie ),
				'_wpnonce': link_href.split( '_wpnonce=' )[1],
				'id': link_href.split( 'cid=' )[1].split( '&' )[0]
			},

			function ( response ) {
				// Check for errors and append if found.
				if ( response[0] + response[1] === '-1' ) {
					comment_li.prepend( jq( response.substr( 2, response.length ) ).hide().fadeIn( 200 ) );

				} else {
					var children  = jq( '#' + comment_li.attr( 'id' ) + ' ul' ).children( 'li' ),
						child_count = 0,
						parent_li;

					jq(children).each( function() {
						if ( !jq( this ).is( ':hidden' ) ) {
							child_count++;
						}
					});
					comment_li.fadeOut( 200 );

					// Decrease the "Reply (X)" button count
					parent_li = comment_li.parents( '#activity-stream > li' );
					jq( '#' + parent_li.attr( 'id' ) + ' a.acomment-reply span' ).html( jq( '#' + parent_li.attr( 'id' ) + ' a.acomment-reply span' ).html() - ( 1 + child_count ) );
				}
			});

			return false;
		}

		/* Showing hidden comments - pause for half a second */
		if ( target.parent().hasClass('show-all') ) {
			target.parent().addClass('loading');

			setTimeout( function() {
				target.parent().parent().children('li').fadeIn(200, function() {
					target.parent().remove();
				});
			}, 600 );

			return false;
		}

		// Canceling an activity comment
		if ( target.hasClass( 'ac-reply-cancel' ) ) {
			jq(target).closest('.ac-form').slideUp( 200 );
			return false;
		}
	});

	/* Escape Key Press for cancelling comment forms */
	jq(document).keydown( function(e) {
		e = e || window.event;
		if (e.target) {
			element = e.target;
		} else if (e.srcElement) {
			element = e.srcElement;
		}

		if( element.nodeType === 3) {
			element = element.parentNode;
		}

		if( e.ctrlKey === true || e.altKey === true || e.metaKey === true ) {
			return;
		}

		var keyCode = (e.keyCode) ? e.keyCode : e.which;

		if ( keyCode === 27 ) {
			if (element.tagName === 'TEXTAREA') {
				if ( jq(element).hasClass('ac-input') ) {
					jq(element).parent().parent().parent().slideUp( 200 );
				}
			}
		}
	});

	/**** Directory Search ****************************************************/

	/* The search form on all directory pages */
	jq( '.dir-search, .groups-members-search' ).on( 'click', function(event) {
		if ( jq(this).hasClass('no-ajax') ) {
			return;
		}

		var target = jq(event.target),
			css_id, object, template;

		if ( target.attr('type') === 'submit' ) {
			css_id = jq('.item-list-tabs li.selected').attr('id').split( '-' );
			object = css_id[0];
			template = null;

			// The Group Members page specifies its own template
			if ( event.currentTarget.className === 'groups-members-search' ) {
				object = 'group_members';
				template = 'groups/single/members';
			}

			bp_filter_request( object, jq.cookie('bp-' + object + '-filter'), jq.cookie('bp-' + object + '-scope') , 'div.' + object, target.parent().children('label').children('input').val(), 1, jq.cookie('bp-' + object + '-extras'), null, template );

			return false;
		}
	});

	/**** Tabs and Filters ****************************************************/

	/* When a navigation tab is clicked - e.g. | All Groups | My Groups | */
	jq('div.item-list-tabs').on( 'click', function(event) {
		if ( jq(this).hasClass('no-ajax')  || jq( event.target ).hasClass('no-ajax') )  {
			return;
		}

		var targetElem = ( event.target.nodeName === 'SPAN' ) ? event.target.parentNode : event.target,
			target       = jq( targetElem ).parent(),
			css_id, object, scope, filter, search_terms;

		if ( 'LI' === target[0].nodeName && !target.hasClass( 'last' ) ) {
			css_id = target.attr('id').split( '-' );
			object = css_id[0];

			if ( 'activity' === object ) {
				return false;
			}

			scope = css_id[1];
			filter = jq('#' + object + '-order-select select').val();
			search_terms = jq('#' + object + '_search').val();

			bp_filter_request( object, filter, scope, 'div.' + object, search_terms, 1, jq.cookie('bp-' + object + '-extras') );

			return false;
		}
	});

	/* When the filter select box is changed re-query */
	jq('li.filter select').change( function() {
		var el,
			css_id, object, scope, filter, search_terms, template,
			$gm_search;

		if ( jq('.item-list-tabs li.selected').length ) {
			el = jq('.item-list-tabs li.selected');
		} else {
			el = jq(this);
		}

		css_id = el.attr('id').split('-');
		object = css_id[0];
		scope = css_id[1];
		filter = jq(this).val();
		search_terms = false;
		template = null;

		if ( jq('.dir-search input').length ) {
			search_terms = jq('.dir-search input').val();
		}

		// The Group Members page has a different selector for its
		// search terms box
		$gm_search = jq( '.groups-members-search input' );
		if ( $gm_search.length ) {
			search_terms = $gm_search.val();
			object = 'members';
			scope = 'groups';
		}

		// On the Groups Members page, we specify a template
		if ( 'members' === object && 'groups' === scope ) {
			object = 'group_members';
			template = 'groups/single/members';
		}

		if ( 'friends' === object ) {
			object = 'members';
		}

		bp_filter_request( object, filter, scope, 'div.' + object, search_terms, 1, jq.cookie('bp-' + object + '-extras'), null, template );

		return false;
	});

	/* All pagination links run through this function */
	jq('#buddypress').on( 'click', function(event) {
		var target = jq(event.target),
			el,
			css_id, object, search_terms, pagination_id, template,
			page_number,
			$gm_search,
			caller;

		if ( target.hasClass('button') ) {
			return true;
		}

		if ( target.parent().parent().hasClass('pagination') && !target.parent().parent().hasClass('no-ajax') ) {
			if ( target.hasClass('dots') || target.hasClass('current') ) {
				return false;
			}

			if ( jq('.item-list-tabs li.selected').length ) {
				el = jq('.item-list-tabs li.selected');
			} else {
				el = jq('li.filter select');
			}

			css_id = el.attr('id').split( '-' );
			object = css_id[0];
			search_terms = false;
			pagination_id = jq(target).closest('.pagination-links').attr('id');
			template = null;

			// Search terms
			if ( jq('div.dir-search input').length ) {
				search_terms =  jq('.dir-search input');

				if ( ! search_terms.val() && bp_get_querystring( search_terms.attr( 'name' ) ) ) {
					search_terms = jq('.dir-search input').prop('placeholder');
				} else {
					search_terms = search_terms.val();
				}
			}

			// Page number
			if ( jq(target).hasClass('next') || jq(target).hasClass('prev') ) {
				page_number = jq('.pagination span.current').html();
			} else {
				page_number = jq(target).html();
			}

			// Remove any non-numeric characters from page number text (commas, etc.)
			page_number = Number( page_number.replace(/\D/g,'') );

			if ( jq(target).hasClass('next') ) {
				page_number++;
			} else if ( jq(target).hasClass('prev') ) {
				page_number--;
			}

			// The Group Members page has a different selector for
			// its search terms box
			$gm_search = jq( '.groups-members-search input' );
			if ( $gm_search.length ) {
				search_terms = $gm_search.val();
				object = 'members';
			}

			// On the Groups Members page, we specify a template
			if ( 'members' === object && 'groups' === css_id[1] ) {
				object = 'group_members';
				template = 'groups/single/members';
			}

			// On the Admin > Requests page, we need to reset the object,
			// since "admin" isn't specific enough
			if ( 'admin' === object && jq( 'body' ).hasClass( 'membership-requests' ) ) {
				object = 'requests';
			}

			if ( pagination_id.indexOf( 'pag-bottom' ) !== -1 ) {
				caller = 'pag-bottom';
			} else {
				caller = null;
			}

			bp_filter_request( object, jq.cookie('bp-' + object + '-filter'), jq.cookie('bp-' + object + '-scope'), 'div.' + object, search_terms, page_number, jq.cookie('bp-' + object + '-extras'), caller, template );

			return false;
		}

	});

	/**** New Forum Directory Post **************************************/

	/* Hit the "New Topic" button on the forums directory page */
	jq('a.show-hide-new').on( 'click', function() {
		if ( !jq('#new-topic-post').length ) {
			return false;
		}

		if ( jq('#new-topic-post').is(':visible') ) {
			jq('#new-topic-post').slideUp(200);
		} else {
			jq('#new-topic-post').slideDown(200, function() {
				jq('#topic_title').focus();
			} );
		}

		return false;
	});

	/* Cancel the posting of a new forum topic */
	jq('#submit_topic_cancel').on( 'click', function() {
		if ( !jq('#new-topic-post').length ) {
			return false;
		}

		jq('#new-topic-post').slideUp(200);
		return false;
	});

	/* Clicking a forum tag */
	jq('#forum-directory-tags a').on( 'click', function() {
		bp_filter_request( 'forums', 'tags', jq.cookie('bp-forums-scope'), 'div.forums', jq(this).html().replace( /&nbsp;/g, '-' ), 1, jq.cookie('bp-forums-extras') );
		return false;
	});

	/** Invite Friends Interface ****************************************/

	/* Select a user from the list of friends and add them to the invite list */
	jq('#send-invite-form').on( 'click', '#invite-list input', function() {
		// invites-loop template contains a div with the .invite class
		// We use the existence of this div to check for old- vs new-
		// style templates.
		var invites_new_template = jq( '#send-invite-form > .invite' ).length,
			friend_id, friend_action;

		jq('.ajax-loader').toggle();

		// Dim the form until the response arrives
		if ( invites_new_template ) {
			jq( this ).parents( 'ul' ).find( 'input' ).prop( 'disabled', true );
		}

		friend_id = jq(this).val();

		if ( jq(this).prop('checked') === true ) {
			friend_action = 'invite';
		} else {
			friend_action = 'uninvite';
		}

		if ( ! invites_new_template ) {
			jq( '.item-list-tabs li.selected' ).addClass( 'loading' );
		}

		jq.post( ajaxurl, {
			action: 'groups_invite_user',
			'friend_action': friend_action,
			'cookie': bp_get_cookies(),
			'_wpnonce': jq('#_wpnonce_invite_uninvite_user').val(),
			'friend_id': friend_id,
			'group_id': jq('#group_id').val()
		},
		function(response)
		{
			if ( jq('#message') ) {
				jq('#message').hide();
			}

			if ( invites_new_template ) {
				// With new-style templates, we refresh the
				// entire list
				bp_filter_request( 'invite', 'bp-invite-filter', 'bp-invite-scope', 'div.invite', false, 1, '', '', '' );
			} else {
				// Old-style templates manipulate only the
				// single invitation element
				jq('.ajax-loader').toggle();

				if ( friend_action === 'invite' ) {
					jq('#friend-list').append(response);
				} else if ( friend_action === 'uninvite' ) {
					jq('#friend-list li#uid-' + friend_id).remove();
				}

				jq('.item-list-tabs li.selected').removeClass('loading');
			}
		});
	});

	/* Remove a user from the list of users to invite to a group */
	jq('#send-invite-form').on('click', 'a.remove', function() {
		// invites-loop template contains a div with the .invite class
		// We use the existence of this div to check for old- vs new-
		// style templates.
		var invites_new_template = jq('#send-invite-form > .invite').length,
			friend_id = jq(this).attr('id');

		jq('.ajax-loader').toggle();

		friend_id = friend_id.split('-');
		friend_id = friend_id[1];

		jq.post( ajaxurl, {
			action: 'groups_invite_user',
			'friend_action': 'uninvite',
			'cookie': bp_get_cookies(),
			'_wpnonce': jq('#_wpnonce_invite_uninvite_user').val(),
			'friend_id': friend_id,
			'group_id': jq('#group_id').val()
		},
		function(response)
		{
			if ( invites_new_template ) {
				// With new-style templates, we refresh the
				// entire list
				bp_filter_request( 'invite', 'bp-invite-filter', 'bp-invite-scope', 'div.invite', false, 1, '', '', '' );
			} else {
				// Old-style templates manipulate only the
				// single invitation element
				jq('.ajax-loader').toggle();
				jq('#friend-list #uid-' + friend_id).remove();
				jq('#invite-list #f-' + friend_id).prop('checked', false);
			}
		});

		return false;
	});

	/** Profile Visibility Settings *********************************/
	jq( '.visibility-toggle-link' ).on( 'click', function( event ) {
		event.preventDefault();

		jq( this ).parent().hide().addClass( 'field-visibility-settings-hide' )
			.siblings( '.field-visibility-settings' ).show().addClass( 'field-visibility-settings-open' );
	} );

	jq( '.field-visibility-settings-close' ).on( 'click', function( event ) {
		event.preventDefault();

		var settings_div = jq( this ).parent(),
			vis_setting_text = settings_div.find( 'input:checked' ).parent().text();

		settings_div.hide().removeClass( 'field-visibility-settings-open' )
			.siblings( '.field-visibility-settings-toggle' )
				.children( '.current-visibility-level' ).text( vis_setting_text ).end()
			.show().removeClass( 'field-visibility-settings-hide' );
	} );

	jq('#profile-edit-form input:not(:submit), #profile-edit-form textarea, #profile-edit-form select, #signup_form input:not(:submit), #signup_form textarea, #signup_form select').change( function() {
		var shouldconfirm = true;

		jq('#profile-edit-form input:submit, #signup_form input:submit').on( 'click', function() {
			shouldconfirm = false;
		});

		window.onbeforeunload = function(e) {
			if ( shouldconfirm ) {
				return BP_DTheme.unsaved_changes;
			}
		};
	});

	/** Friendship Requests **************************************/

	/* Accept and Reject friendship request buttons */
	jq('#friend-list a.accept, #friend-list a.reject').on( 'click', function() {
		var button   = jq(this),
			li         = jq(this).parents('#friend-list li'),
			action_div = jq(this).parents('li div.action'),
			id         = li.attr('id').substr( 11, li.attr('id').length ),
			link_href  = button.attr('href'),
			nonce      = link_href.split('_wpnonce=')[1],
			action;

		if ( jq(this).hasClass('accepted') || jq(this).hasClass('rejected') ) {
			return false;
		}

		if ( jq(this).hasClass('accept') ) {
			action = 'accept_friendship';
			action_div.children('a.reject').css( 'visibility', 'hidden' );
		} else {
			action = 'reject_friendship';
			action_div.children('a.accept').css( 'visibility', 'hidden' );
		}

		button.addClass('loading');

		jq.post( ajaxurl, {
			action: action,
			'cookie': bp_get_cookies(),
			'id': id,
			'_wpnonce': nonce
		},
		function(response) {
			button.removeClass('loading');

			if ( response[0] + response[1] === '-1' ) {
				li.prepend( response.substr( 2, response.length ) );
				li.children('#message').hide().fadeIn(200);
			} else {
				button.fadeOut( 100, function() {
					if ( jq(this).hasClass('accept') ) {
						action_div.children('a.reject').hide();
						jq(this).html( BP_DTheme.accepted ).contents().unwrap();
					} else {
						action_div.children('a.accept').hide();
						jq(this).html( BP_DTheme.rejected ).contents().unwrap();
					}
				});
			}
		});

		return false;
	});

	/* Add / Remove friendship buttons */
	jq( '#members-dir-list, #members-group-list, #item-header' ).on('click', '.friendship-button a', function() {
		jq(this).parent().addClass('loading');
		var fid   = jq(this).attr('id'),
			nonce   = jq(this).attr('href'),
			thelink = jq(this);

		fid = fid.split('-');
		fid = fid[1];

		nonce = nonce.split('?_wpnonce=');
		nonce = nonce[1].split('&');
		nonce = nonce[0];

		jq.post( ajaxurl, {
			action: 'addremove_friend',
			'cookie': bp_get_cookies(),
			'fid': fid,
			'_wpnonce': nonce
		},
		function(response)
		{
			var action  = thelink.attr('rel');
				parentdiv = thelink.parent();

			if ( action === 'add' ) {
				jq(parentdiv).fadeOut(200,
					function() {
						parentdiv.removeClass('add_friend');
						parentdiv.removeClass('loading');
						parentdiv.addClass('pending_friend');
						parentdiv.fadeIn(200).html(response);
					}
					);

			} else if ( action === 'remove' ) {
				jq(parentdiv).fadeOut(200,
					function() {
						parentdiv.removeClass('remove_friend');
						parentdiv.removeClass('loading');
						parentdiv.addClass('add');
						parentdiv.fadeIn(200).html(response);
					}
					);
			}
		});
		return false;
	} );

	/** Group Join / Leave Buttons **************************************/

	// Confirmation when clicking Leave Group in group headers
	jq('#buddypress').on('click', '.group-button .leave-group', function() {
		if ( false === confirm( BP_DTheme.leave_group_confirm ) ) {
			return false;
		}
	});

	jq('#groups-dir-list').on('click', '.group-button a', function() {
		var gid   = jq(this).parent().attr('id'),
			nonce   = jq(this).attr('href'),
			thelink = jq(this);

		gid = gid.split('-');
		gid = gid[1];

		nonce = nonce.split('?_wpnonce=');
		nonce = nonce[1].split('&');
		nonce = nonce[0];

		// Leave Group confirmation within directories - must intercept
		// AJAX request
		if ( thelink.hasClass( 'leave-group' ) && false === confirm( BP_DTheme.leave_group_confirm ) ) {
			return false;
		}

		jq.post( ajaxurl, {
			action: 'joinleave_group',
			'cookie': bp_get_cookies(),
			'gid': gid,
			'_wpnonce': nonce
		},
		function(response)
		{
			var parentdiv = thelink.parent();

			// user groups page
			if ( ! jq('body.directory').length ) {
				window.location.reload();

			// groups directory
			} else {
				jq(parentdiv).fadeOut(200,
					function() {
						parentdiv.fadeIn(200).html(response);

						var mygroups = jq('#groups-personal span'),
							add        = 1;

						if( thelink.hasClass( 'leave-group' ) ) {
							// hidden groups slide up
							if ( parentdiv.hasClass( 'hidden' ) ) {
								parentdiv.closest('li').slideUp( 200 );
							}

							add = 0;
						} else if ( thelink.hasClass( 'request-membership' ) ) {
							add = false;
						}

						// change the "My Groups" value
						if ( mygroups.length && add !== false ) {
							if ( add ) {
								mygroups.text( ( mygroups.text() >> 0 ) + 1 );
							} else {
								mygroups.text( ( mygroups.text() >> 0 ) - 1 );
							}
						}

					}
				);
			}
		});
		return false;
	} );

	/** Button disabling ************************************************/

	jq('#buddypress').on( 'click', '.pending', function() {
		return false;
	});

	/** Registration ***********************************************/

	if ( jq('body').hasClass('register') ) {
		var blog_checked = jq('#signup_with_blog');

		// hide "Blog Details" block if not checked by default
		if ( ! blog_checked.prop('checked') ) {
			jq('#blog-details').toggle();
		}

		// toggle "Blog Details" block whenever checkbox is checked
		blog_checked.change(function() {
			jq('#blog-details').toggle();
		});
	}

	/** Private Messaging ******************************************/

	/** Message search */
	jq('.message-search').on( 'click', function(event) {
		if ( jq(this).hasClass('no-ajax') ) {
			return;
		}

		var target = jq(event.target),
			object;

		if ( target.attr('type') === 'submit' || target.attr('type') === 'button' ) {
			object = 'messages';

			bp_filter_request(
				object,
				jq.cookie('bp-' + object + '-filter'),
				jq.cookie('bp-' + object + '-scope'),
				'div.' + object, jq('#messages_search').val(),
				1,
				jq.cookie('bp-' + object + '-extras')
			);

			return false;
		}
	});

	/* AJAX send reply functionality */
	jq('#send_reply_button').click(
		function() {
			var order = jq('#messages_order').val() || 'ASC',
				offset  = jq('#message-recipients').offset(),
				button  = jq('#send_reply_button');

			jq(button).addClass('loading');

			jq.post( ajaxurl, {
				action: 'messages_send_reply',
				'cookie': bp_get_cookies(),
				'_wpnonce': jq('#send_message_nonce').val(),

				'content': jq('#message_content').val(),
				'send_to': jq('#send_to').val(),
				'subject': jq('#subject').val(),
				'thread_id': jq('#thread_id').val()
			},
			function(response)
			{
				if ( response[0] + response[1] === '-1' ) {
					jq('#send-reply').prepend( response.substr( 2, response.length ) );
				} else {
					jq('#send-reply #message').remove();
					jq('#message_content').val('');

					if ( 'ASC' === order ) {
						jq('#send-reply').before( response );
					} else {
						jq('#message-recipients').after( response );
						jq(window).scrollTop(offset.top);
					}

					jq('.new-message').hide().slideDown( 200, function() {
						jq('.new-message').removeClass('new-message');
					});
				}
				jq(button).removeClass('loading');
			});

			return false;
		}
	);

	/* Marking private messages as read and unread */
	jq('#mark_as_read, #mark_as_unread').click(function() {
		var checkboxes_tosend = '',
			checkboxes = jq('#message-threads tr td input[type="checkbox"]'),
			currentClass, newClass, unreadCount, inboxCount, unreadCountDisplay, action,
			inboxcount, thread_count;

		if ( 'mark_as_unread' === jq(this).attr('id') ) {
			currentClass = 'read';
			newClass = 'unread';
			unreadCount = 1;
			inboxCount = 0;
			unreadCountDisplay = 'inline';
			action = 'messages_markunread';
		} else {
			currentClass = 'unread';
			newClass = 'read';
			unreadCount = 0;
			inboxCount = 1;
			unreadCountDisplay = 'none';
			action = 'messages_markread';
		}

		checkboxes.each( function(i) {
			if(jq(this).is(':checked')) {
				if ( jq('#m-' + jq(this).attr('value')).hasClass(currentClass) ) {
					checkboxes_tosend += jq(this).attr('value');
					jq('#m-' + jq(this).attr('value')).removeClass(currentClass);
					jq('#m-' + jq(this).attr('value')).addClass(newClass);
					thread_count = jq('#m-' + jq(this).attr('value') + ' td span.unread-count').html();

					jq('#m-' + jq(this).attr('value') + ' td span.unread-count').html(unreadCount);
					jq('#m-' + jq(this).attr('value') + ' td span.unread-count').css('display', unreadCountDisplay);

					inboxcount = jq('tr.unread').length;

					jq('#user-messages span').html( inboxcount );

					if ( i !== checkboxes.length - 1 ) {
						checkboxes_tosend += ',';
					}
				}
			}
		});
		jq.post( ajaxurl, {
			action: action,
			'thread_ids': checkboxes_tosend
		});
		return false;
	});

	/* Selecting unread and read messages in inbox */
	jq( 'body.messages #item-body div.messages' ).on( 'change', '#message-type-select', function() {
		var selection   = this.value,
			checkboxes    = jq( 'td input[type="checkbox"]' ),
			checked_value = 'checked';

		checkboxes.each( function(i) {
			checkboxes[i].checked = '';
		});

		switch ( selection ) {
			case 'unread':
				checkboxes = jq('tr.unread td input[type="checkbox"]');
				break;
			case 'read':
				checkboxes = jq('tr.read td input[type="checkbox"]');
				break;
			case '':
				checked_value = '';
				break;
		}

		checkboxes.each( function(i) {
			checkboxes[i].checked = checked_value;
		});
	});

	/* Bulk delete messages */
	jq( 'body.messages #item-body div.messages' ).on( 'click', '.messages-options-nav a', function() {
		if ( -1 === jq.inArray( this.id, Array( 'delete_sentbox_messages', 'delete_inbox_messages' ) ) ) {
			return;
		}

		checkboxes_tosend = '';
		checkboxes = jq('#message-threads tr td input[type="checkbox"]');

		jq('#message').remove();
		jq(this).addClass('loading');

		jq(checkboxes).each( function(i) {
			if( jq(this).is(':checked') ) {
				checkboxes_tosend += jq(this).attr('value') + ',';
			}
		});

		if ( '' === checkboxes_tosend ) {
			jq(this).removeClass('loading');
			return false;
		}

		jq.post( ajaxurl, {
			action: 'messages_delete',
			'thread_ids': checkboxes_tosend
		}, function(response) {
			if ( response[0] + response[1] === '-1' ) {
				jq('#message-threads').prepend( response.substr( 2, response.length ) );
			} else {
				jq('#message-threads').before( '<div id="message" class="updated"><p>' + response + '</p></div>' );

				jq(checkboxes).each( function(i) {
					if( jq(this).is(':checked') ) {
						// We need to uncheck because message is only hidden
						// Otherwise, AJAX will be fired again with same data
						jq(this).attr( 'checked', false );
						jq(this).parent().parent().fadeOut(150);
					}
				});
			}

			jq('#message').hide().slideDown(150);
			jq('#delete_inbox_messages, #delete_sentbox_messages').removeClass('loading');
		});

		return false;
	});

	/* Selecting/Deselecting all messages */
	jq('#select-all-messages').click(function(event) {
		if( this.checked ) {
			jq('.message-check').each(function() {
				this.checked = true;
			});
		} else {
			jq('.message-check').each(function() {
				this.checked = false;
			});
		}
	});

	/* Make sure a 'Bulk Action' is selected before submitting the messages bulk action form */
	jq('#messages-bulk-manage').attr('disabled', 'disabled');

	/* Remove the disabled attribute from the messages form submit button when bulk action has a value */
	jq('#messages-select').on('change', function(){
		jq('#messages-bulk-manage').attr('disabled', jq(this).val().length <= 0);
	});

	/* Star action function */
	starAction = function() {
		var link = jq(this);

		jq.post( ajaxurl, {
			action: 'messages_star',
			'message_id': link.data('message-id'),
			'star_status': link.data('star-status'),
			'nonce': link.data('star-nonce'),
			'bulk': link.data('star-bulk')
		},
		function(response) {
			if ( 1 === parseInt( response, 10 ) ) {
				if ( 'unstar' === link.data('star-status') ) {
					link.data('star-status', 'star');
					link.removeClass('message-action-unstar').addClass('message-action-star');
					link.find('.bp-screen-reader-text').text( BP_PM_Star.strings.text_star );

					if ( 1 === BP_PM_Star.is_single_thread ) {
						link.prop('title', BP_PM_Star.strings.title_star );
					} else {
						link.prop('title', BP_PM_Star.strings.title_star_thread );
					}

				} else {
					link.data('star-status', 'unstar');
					link.removeClass('message-action-star').addClass('message-action-unstar');
					link.find('.bp-screen-reader-text').text(BP_PM_Star.strings.text_unstar);

					if ( 1 === BP_PM_Star.is_single_thread ) {
						link.prop('title', BP_PM_Star.strings.title_unstar );
					} else {
						link.prop('title', BP_PM_Star.strings.title_unstar_thread );
					}
				}
			}
		});
		return false;
	};

	/* Star actions */
	jq('#message-threads').on('click', 'td.thread-star a', starAction );
	jq('#message-thread').on('click', '.message-star-actions a', starAction );

	/* Star bulk manage - Show only the valid action based on the starred item. */
	jq('#message-threads td.bulk-select-check :checkbox').on('change', function() {
		var box = jq(this),
			star = box.closest('tr').find('.thread-star a');

		if ( box.prop('checked') ) {
			if( 'unstar' === star.data('star-status') ) {
				BP_PM_Star.star_counter++;
			} else {
				BP_PM_Star.unstar_counter++;
			}
		} else {
			if( 'unstar' === star.data('star-status') ) {
				BP_PM_Star.star_counter--;
			} else {
				BP_PM_Star.unstar_counter--;
			}
		}

		if ( BP_PM_Star.star_counter > 0 && parseInt( BP_PM_Star.unstar_counter, 10 ) === 0 ) {
			jq('option[value="star"]').hide();
		} else {
			jq('option[value="star"]').show();
		}

		if ( BP_PM_Star.unstar_counter > 0 && parseInt( BP_PM_Star.star_counter, 10 ) === 0 ) {
			jq('option[value="unstar"]').hide();
		} else {
			jq('option[value="unstar"]').show();
		}
	});

	/** Notifications **********************************************/

	/* Selecting/Deselecting all notifications */
	jq('#select-all-notifications').click(function(event) {
		if( this.checked ) {
			jq('.notification-check').each(function() {
				this.checked = true;
			});
		} else {
			jq('.notification-check').each(function() {
				this.checked = false;
			});
		}
	});

	/* Make sure a 'Bulk Action' is selected before submitting the form */
	jq('#notification-bulk-manage').attr('disabled', 'disabled');

	/* Remove the disabled attribute from the form submit button when bulk action has a value */
	jq('#notification-select').on('change', function(){
		jq('#notification-bulk-manage').attr('disabled', jq(this).val().length <= 0);
	});

	/* Close site wide notices in the sidebar */
	jq('#close-notice').on( 'click', function() {
		jq(this).addClass('loading');
		jq('#sidebar div.error').remove();

		jq.post( ajaxurl, {
			action: 'messages_close_notice',
			'notice_id': jq('.notice').attr('rel').substr( 2, jq('.notice').attr('rel').length )
		},
		function(response) {
			jq('#close-notice').removeClass('loading');

			if ( response[0] + response[1] === '-1' ) {
				jq('.notice').prepend( response.substr( 2, response.length ) );
				jq( '#sidebar div.error').hide().fadeIn( 200 );
			} else {
				jq('.notice').slideUp( 100 );
			}
		});
		return false;
	});

	/* Toolbar & wp_list_pages JavaScript IE6 hover class */
	jq('#wp-admin-bar ul.main-nav li, #nav li').mouseover( function() {
		jq(this).addClass('sfhover');
	});

	jq('#wp-admin-bar ul.main-nav li, #nav li').mouseout( function() {
		jq(this).removeClass('sfhover');
	});

	/* Clear BP cookies on logout */
	jq('#wp-admin-bar-logout, a.logout').on( 'click', function() {
		jq.removeCookie('bp-activity-scope', {
			path: '/'
		});
		jq.removeCookie('bp-activity-filter', {
			path: '/'
		});
		jq.removeCookie('bp-activity-oldestpage', {
			path: '/'
		});

		var objects = [ 'members', 'groups', 'blogs', 'forums' ];
		jq(objects).each( function(i) {
			jq.removeCookie('bp-' + objects[i] + '-scope', {
				path: '/'
			} );
			jq.removeCookie('bp-' + objects[i] + '-filter', {
				path: '/'
			} );
			jq.removeCookie('bp-' + objects[i] + '-extras', {
				path: '/'
			} );
		});
	});

	/* if js is enabled then replace the no-js class by a js one */
	if( jq('body').hasClass('no-js') ) {
		jq('body').attr('class', jq('body').attr('class').replace( /no-js/,'js' ) );
	}

	/** Activity HeartBeat ************************************************/

	// Set the interval and the namespace event
	if ( typeof wp !== 'undefined' && typeof wp.heartbeat !== 'undefined' && typeof BP_DTheme.pulse !== 'undefined' ) {

		wp.heartbeat.interval( Number( BP_DTheme.pulse ) );

		jq.fn.extend({
			'heartbeat-send': function() {
				return this.bind( 'heartbeat-send.buddypress' );
			}
		});
	}

	// Set the last id to request after
	var first_item_recorded = 0;
	jq( document ).on( 'heartbeat-send.buddypress', function( e, data ) {

		first_item_recorded = 0;

		// First row is default latest activity id
		if ( jq( '#buddypress ul.activity-list li' ).first().prop( 'id' ) ) {
			// getting the timestamp
			timestamp = jq( '#buddypress ul.activity-list li' ).first().prop( 'class' ).match( /date-recorded-([0-9]+)/ );

			if ( timestamp ) {
				first_item_recorded = timestamp[1];
			}
		}

		if ( 0 === activity_last_recorded || Number( first_item_recorded ) > activity_last_recorded ) {
			activity_last_recorded = Number( first_item_recorded );
		}

		data.bp_activity_last_recorded = activity_last_recorded;

		last_recorded_search = bp_get_querystring('s');

		if ( last_recorded_search ) {
			data.bp_activity_last_recorded_search_terms = last_recorded_search;
		}
	});

	// Increment newest_activities and activity_last_recorded if data has been returned
	jq( document ).on( 'heartbeat-tick', function( e, data ) {

		// Only proceed if we have newest activities
		if ( ! data.bp_activity_newest_activities ) {
			return;
		}

		newest_activities = data.bp_activity_newest_activities.activities + newest_activities;
		activity_last_recorded  = Number( data.bp_activity_newest_activities.last_recorded );

		if ( jq( '#buddypress ul.activity-list li' ).first().hasClass( 'load-newest' ) ) {
			return;
		}

		jq( '#buddypress ul.activity-list' ).prepend( '<li class="load-newest"><a href="#newest">' + BP_DTheme.newest + '</a></li>' );
	});
});

/* Setup activity scope and filter based on the current cookie settings. */
function bp_init_activity() {
	/* Reset the page */
	jq.cookie( 'bp-activity-oldestpage', 1, {
		path: '/'
	} );

	if ( undefined !== jq.cookie('bp-activity-filter') && jq('#activity-filter-select').length ) {
		jq('#activity-filter-select select option[value="' + jq.cookie('bp-activity-filter') + '"]').prop( 'selected', true );
	}

	/* Activity Tab Set */
	if ( undefined !== jq.cookie('bp-activity-scope') && jq('.activity-type-tabs').length ) {
		jq('.activity-type-tabs li').each( function() {
			jq(this).removeClass('selected');
		});
		jq('#activity-' + jq.cookie('bp-activity-scope') + ', .item-list-tabs li.current').addClass('selected');
	}
}

/* Setup object scope and filter based on the current cookie settings for the object. */
function bp_init_objects(objects) {
	jq(objects).each( function(i) {
		if ( undefined !== jq.cookie('bp-' + objects[i] + '-filter') && jq('#' + objects[i] + '-order-select select').length ) {
			jq('#' + objects[i] + '-order-select select option[value="' + jq.cookie('bp-' + objects[i] + '-filter') + '"]').prop( 'selected', true );
		}

		if ( undefined !== jq.cookie('bp-' + objects[i] + '-scope') && jq('div.' + objects[i]).length ) {
			jq('.item-list-tabs li').each( function() {
				jq(this).removeClass('selected');
			});
			jq('#' + objects[i] + '-' + jq.cookie('bp-' + objects[i] + '-scope') + ', #object-nav li.current').addClass('selected');
		}
	});
}

/* Filter the current content list (groups/members/blogs/topics) */
function bp_filter_request( object, filter, scope, target, search_terms, page, extras, caller, template ) {
	if ( 'activity' === object ) {
		return false;
	}

	if ( null === scope ) {
		scope = 'all';
	}

	/* Save the settings we want to remain persistent to a cookie */
	jq.cookie( 'bp-' + object + '-scope', scope, {
		path: '/'
	} );
	jq.cookie( 'bp-' + object + '-filter', filter, {
		path: '/'
	} );
	jq.cookie( 'bp-' + object + '-extras', extras, {
		path: '/'
	} );

	/* Set the correct selected nav and filter */
	jq('.item-list-tabs li').each( function() {
		jq(this).removeClass('selected');
	});
	jq('#' + object + '-' + scope + ', #object-nav li.current').addClass('selected');
	jq('.item-list-tabs li.selected').addClass('loading');
	jq('.item-list-tabs select option[value="' + filter + '"]').prop( 'selected', true );

	if ( 'friends' === object || 'group_members' === object ) {
		object = 'members';
	}

	if ( bp_ajax_request ) {
		bp_ajax_request.abort();
	}

	bp_ajax_request = jq.post( ajaxurl, {
		action: object + '_filter',
		'cookie': bp_get_cookies(),
		'object': object,
		'filter': filter,
		'search_terms': search_terms,
		'scope': scope,
		'page': page,
		'extras': extras,
		'template': template
	},
	function(response)
	{
		/* animate to top if called from bottom pagination */
		if ( caller === 'pag-bottom' && jq('#subnav').length ) {
			var top = jq('#subnav').parent();
			jq('html,body').animate({scrollTop: top.offset().top}, 'slow', function() {
				jq(target).fadeOut( 100, function() {
					jq(this).html(response);
					jq(this).fadeIn(100);
				});
			});

		} else {
			jq(target).fadeOut( 100, function() {
				jq(this).html(response);
				jq(this).fadeIn(100);
			});
		}

		jq('.item-list-tabs li.selected').removeClass('loading');
	});
}

/* Activity Loop Requesting */
function bp_activity_request(scope, filter) {
	/* Save the type and filter to a session cookie */
	if ( null !== scope ) {
		jq.cookie( 'bp-activity-scope', scope, {
			path: '/'
		} );
	}
	if ( null !== filter ) {
		jq.cookie( 'bp-activity-filter', filter, {
			path: '/'
		} );
	}
	jq.cookie( 'bp-activity-oldestpage', 1, {
		path: '/'
	} );

	/* Remove selected and loading classes from tabs */
	jq('.item-list-tabs li').each( function() {
		jq(this).removeClass('selected loading');
	});
	/* Set the correct selected nav and filter */
	jq('#activity-' + scope + ', .item-list-tabs li.current').addClass('selected');
	jq('#object-nav.item-list-tabs li.selected, div.activity-type-tabs li.selected').addClass('loading');
	jq('#activity-filter-select select option[value="' + filter + '"]').prop( 'selected', true );

	/* Reload the activity stream based on the selection */
	jq('.widget_bp_activity_widget h2 span.ajax-loader').show();

	if ( bp_ajax_request ) {
		bp_ajax_request.abort();
	}

	bp_ajax_request = jq.post( ajaxurl, {
		action: 'activity_widget_filter',
		'cookie': bp_get_cookies(),
		'_wpnonce_activity_filter': jq('#_wpnonce_activity_filter').val(),
		'scope': scope,
		'filter': filter
	},
	function(response)
	{
		jq('.widget_bp_activity_widget h2 span.ajax-loader').hide();

		jq('div.activity').fadeOut( 100, function() {
			jq(this).html(response.contents);
			jq(this).fadeIn(100);

			/* Selectively hide comments */
			bp_legacy_theme_hide_comments();
		});

		/* Update the feed link */
		if ( undefined !== response.feed_url ) {
			jq('.directory #subnav li.feed a, .home-page #subnav li.feed a').attr('href', response.feed_url);
		}

		jq('.item-list-tabs li.selected').removeClass('loading');

	}, 'json' );
}

/* Hide long lists of activity comments, only show the latest five root comments. */
function bp_legacy_theme_hide_comments() {
	var comments_divs = jq('div.activity-comments'),
		parent_li, comment_lis, comment_count;

	if ( !comments_divs.length ) {
		return false;
	}

	comments_divs.each( function() {
		if ( jq(this).children('ul').children('li').length < 5 ) {
			return;
		}

		comments_div = jq(this);
		parent_li = comments_div.parents('#activity-stream > li');
		comment_lis = jq(this).children('ul').children('li');
		comment_count = ' ';

		if ( jq('#' + parent_li.attr('id') + ' a.acomment-reply span').length ) {
			comment_count = jq('#' + parent_li.attr('id') + ' a.acomment-reply span').html();
		}

		comment_lis.each( function(i) {
			/* Show the latest 5 root comments */
			if ( i < comment_lis.length - 5 ) {
				jq(this).addClass('hidden');
				jq(this).toggle();

				if ( !i ) {
					jq(this).before( '<li class="show-all"><a href="#' + parent_li.attr('id') + '/show-all/" title="' + BP_DTheme.show_all_comments + '">' + BP_DTheme.show_x_comments.replace( '%d', comment_count ) + '</a></li>' );
				}
			}
		});

	});
}

/* Helper Functions */

function checkAll() {
	var checkboxes = document.getElementsByTagName('input'),
		i;

	for(i=0; i<checkboxes.length; i++) {
		if(checkboxes[i].type === 'checkbox') {
			if($('check_all').checked === '') {
				checkboxes[i].checked = '';
			}
			else {
				checkboxes[i].checked = 'checked';
			}
		}
	}
}

/**
 * Deselects any select options or input options for the specified field element.
 *
 * @param {String} container HTML ID of the field
 * @since 1.2.0
 */
function clear( container ) {
	container = document.getElementById( container );
	if ( ! container ) {
		return;
	}

	var radioButtons = container.getElementsByTagName( 'INPUT' ),
		options = container.getElementsByTagName( 'OPTION' ),
		i       = 0;

	if ( radioButtons ) {
		for ( i = 0; i < radioButtons.length; i++ ) {
			radioButtons[i].checked = '';
		}
	}

	if ( options ) {
		for ( i = 0; i < options.length; i++ ) {
			options[i].selected = false;
		}
	}
}

/* Returns a querystring of BP cookies (cookies beginning with 'bp-') */
function bp_get_cookies() {
	var allCookies = document.cookie.split(';'),  // get all cookies and split into an array
		bpCookies      = {},
		cookiePrefix   = 'bp-',
		i, cookie, delimiter, name, value;

	// loop through cookies
	for (i = 0; i < allCookies.length; i++) {
		cookie    = allCookies[i];
		delimiter = cookie.indexOf('=');
		name      = jq.trim( unescape( cookie.slice(0, delimiter) ) );
		value     = unescape( cookie.slice(delimiter + 1) );

		// if BP cookie, store it
		if ( name.indexOf(cookiePrefix) === 0 ) {
			bpCookies[name] = value;
		}
	}

	// returns BP cookies as querystring
	return encodeURIComponent( jq.param(bpCookies) );
}
