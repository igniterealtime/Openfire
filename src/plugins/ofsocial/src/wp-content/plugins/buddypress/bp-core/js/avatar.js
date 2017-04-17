/* global bp, BP_Uploader, _, Backbone */

window.bp = window.bp || {};

( function( exports, $ ) {

	// Bail if not set
	if ( typeof BP_Uploader === 'undefined' ) {
		return;
	}

	bp.Models      = bp.Models || {};
	bp.Collections = bp.Collections || {};
	bp.Views       = bp.Views || {};

	bp.Avatar = {
		start: function() {
			/**
			 * Remove the bp-legacy UI
			 *
			 * bp.Avatar successfully loaded, we can now
			 * safely remove the Legacy UI.
			 */
			this.removeLegacyUI();

			// Init some vars
			this.views    = new Backbone.Collection();
			this.jcropapi = {};
			this.warning = null;

			// Set up nav
			this.setupNav();

			// Avatars are uploaded files
			this.avatars = bp.Uploader.filesUploaded;

			// The Avatar Attachment object.
			this.Attachment = new Backbone.Model();

			// Wait till the queue is reset
			bp.Uploader.filesQueue.on( 'reset', this.cropView, this );

			/**
			 * In Administration screens we're using Thickbox
			 * We need to make sure to reset the views if it's closed
			 */
			$( 'body.wp-admin' ).on( 'tb_unload', '#TB_window', function() {
				// Reset to the uploader view
				bp.Avatar.nav.trigger( 'bp-avatar-view:changed', 'upload' );

				// Reset to the uploader nav
				_.each( bp.Avatar.navItems.models, function( model ) {
					if ( model.id === 'upload' ) {
						model.set( { active: 1 } );
					} else {
						model.set( { active: 0 } );
					}
				} );
			} );
		},

		removeLegacyUI: function() {
			// User
			if ( $( '#avatar-upload-form' ).length ) {
				$( '#avatar-upload' ).remove();
				$( '#avatar-upload-form p' ).remove();

			// Group Manage
			} else if ( $( '#group-settings-form' ).length ) {
				$( '#group-settings-form p' ).each( function( i ) {
					if ( 0 !== i ) {
						$( this ).remove();
					}
				} );

				if ( $( '#delete-group-avatar-button' ).length ) {
					$( '#delete-group-avatar-button' ).remove();
				}

			// Group Create
			} else if ( $( '#group-create-body' ).length ) {
				$( '.main-column p #file' ).remove();
				$( '.main-column p #upload' ).remove();

			// Admin Extended Profile
			} else if ( $( '#bp_xprofile_user_admin_avatar a.bp-xprofile-avatar-user-admin' ).length ) {
				$( '#bp_xprofile_user_admin_avatar a.bp-xprofile-avatar-user-admin' ).remove();
			}
		},

		setView: function( view ) {
			// Clear views
			if ( ! _.isUndefined( this.views.models ) ) {
				_.each( this.views.models, function( model ) {
					model.get( 'view' ).remove();
				}, this );
			}

			// Reset Views
			this.views.reset();

			// Reset Avatars (file uploaded)
			if ( ! _.isUndefined( this.avatars ) ) {
				this.avatars.reset();
			}

			// Reset the Jcrop API
			if ( ! _.isEmpty( this.jcropapi ) ) {
				this.jcropapi.destroy();
				this.jcropapi = {};
			}

			// Load the required view
			switch ( view ) {
				case 'upload':
					this.uploaderView();
					break;

				case 'delete':
					this.deleteView();
					break;
			}
		},

		setupNav: function() {
			var self = this,
			    initView, activeView;

			this.navItems = new Backbone.Collection();

			_.each( BP_Uploader.settings.nav, function( item, index ) {
				if ( ! _.isObject( item ) ) {
					return;
				}

				// Reset active View
				activeView = 0;

				if ( 0 === index ) {
					initView = item.id;
					activeView = 1;
				}

				self.navItems.add( {
					id     : item.id,
					name   : item.caption,
					href   : '#',
					active : activeView,
					hide   : _.isUndefined( item.hide ) ? 0 : item.hide
				} );
			} );

			this.nav = new bp.Views.Nav( { collection: this.navItems } );
			this.nav.inject( '.bp-avatar-nav' );

			// Activate the initial view (uploader)
			this.setView( initView );

			// Listen to nav changes (it's like a do_action!)
			this.nav.on( 'bp-avatar-view:changed', _.bind( this.setView, this ) );
		},

		uploaderView: function() {
			// Listen to the Queued uploads
			bp.Uploader.filesQueue.on( 'add', this.uploadProgress, this );

			// Create the BuddyPress Uploader
			var uploader = new bp.Views.Uploader();

			// Add it to views
			this.views.add( { id: 'upload', view: uploader } );

			// Display it
			uploader.inject( '.bp-avatar' );
		},

		uploadProgress: function() {
			// Create the Uploader status view
			var avatarStatus = new bp.Views.uploaderStatus( { collection: bp.Uploader.filesQueue } );

			if ( ! _.isUndefined( this.views.get( 'status' ) ) ) {
				this.views.set( { id: 'status', view: avatarStatus } );
			} else {
				this.views.add( { id: 'status', view: avatarStatus } );
			}

			// Display it
			avatarStatus.inject( '.bp-avatar-status' );
		},

		cropView: function() {
			var status;

			// Bail there was an error during the Upload
			if ( _.isEmpty( this.avatars.models ) ) {
				return;
			}

			// Make sure to remove the uploads status
			if ( ! _.isUndefined( this.views.get( 'status' ) ) ) {
				status = this.views.get( 'status' );
				status.get( 'view' ).remove();
				this.views.remove( { id: 'status', view: status } );
			}

			// Create the Avatars view
			var avatar = new bp.Views.Avatars( { collection: this.avatars } );
			this.views.add( { id: 'crop', view: avatar } );

			avatar.inject( '.bp-avatar' );
		},

		setAvatar: function( avatar ) {
			var self = this,
				crop;

			// Remove the crop view
			if ( ! _.isUndefined( this.views.get( 'crop' ) ) ) {
				// Remove the JCrop API
				if ( ! _.isEmpty( this.jcropapi ) ) {
					this.jcropapi.destroy();
					this.jcropapi = {};
				}
				crop = this.views.get( 'crop' );
				crop.get( 'view' ).remove();
				this.views.remove( { id: 'crop', view: crop } );
			}

			// Set the avatar !
			bp.ajax.post( 'bp_avatar_set', {
				json:          true,
				original_file: avatar.get( 'url' ),
				crop_w:        avatar.get( 'w' ),
				crop_h:        avatar.get( 'h' ),
				crop_x:        avatar.get( 'x' ),
				crop_y:        avatar.get( 'y' ),
				item_id:       avatar.get( 'item_id' ),
				object:        avatar.get( 'object' ),
				type:          _.isUndefined( avatar.get( 'type' ) ) ? 'crop' : avatar.get( 'type' ),
				nonce:         avatar.get( 'nonces' ).set
			} ).done( function( response ) {
				var avatarStatus = new bp.Views.AvatarStatus( {
					value : BP_Uploader.strings.feedback_messages[ response.feedback_code ],
					type : 'success'
				} );

				self.views.add( {
					id   : 'status',
					view : avatarStatus
				} );

				avatarStatus.inject( '.bp-avatar-status' );

				// Update each avatars of the page
				$( '.' + avatar.get( 'object' ) + '-' + response.item_id + '-avatar' ).each( function() {
					$(this).prop( 'src', response.avatar );
				} );

				// Inject the Delete nav
				bp.Avatar.navItems.get( 'delete' ).set( { hide: 0 } );

				/**
				 * Set the Attachment object
				 *
				 * You can run extra actions once the avatar is set using:
				 * bp.Avatar.Attachment.on( 'change:url', function( data ) { your code } );
				 *
				 * In this case data.attributes will include the url to the newly
				 * uploaded avatar, the object and the item_id concerned.
				 */
				self.Attachment.set( _.extend(
					_.pick( avatar.attributes, ['object', 'item_id'] ),
					{ url: response.avatar, action: 'uploaded' }
				) );

			} ).fail( function( response ) {
				var feedback = BP_Uploader.strings.default_error;
				if ( ! _.isUndefined( response ) ) {
					feedback = BP_Uploader.strings.feedback_messages[ response.feedback_code ];
				}

				var avatarStatus = new bp.Views.AvatarStatus( {
					value : feedback,
					type : 'error'
				} );

				self.views.add( {
					id   : 'status',
					view : avatarStatus
				} );

				avatarStatus.inject( '.bp-avatar-status' );
			} );
		},

		deleteView:function() {
			// Create the delete model
			var delete_model = new Backbone.Model( _.pick( BP_Uploader.settings.defaults.multipart_params.bp_params,
				'object',
				'item_id',
				'nonces'
			) );

			// Create the delete view
			var deleteView = new bp.Views.DeleteAvatar( { model: delete_model } );

			// Add it to views
			this.views.add( { id: 'delete', view: deleteView } );

			// Display it
			deleteView.inject( '.bp-avatar' );
		},

		deleteAvatar: function( model ) {
			var self = this,
				deleteView;

			// Remove the delete view
			if ( ! _.isUndefined( this.views.get( 'delete' ) ) ) {
				deleteView = this.views.get( 'delete' );
				deleteView.get( 'view' ).remove();
				this.views.remove( { id: 'delete', view: deleteView } );
			}

			// Remove the avatar !
			bp.ajax.post( 'bp_avatar_delete', {
				json:          true,
				item_id:       model.get( 'item_id' ),
				object:        model.get( 'object' ),
				nonce:         model.get( 'nonces' ).remove
			} ).done( function( response ) {
				var avatarStatus = new bp.Views.AvatarStatus( {
					value : BP_Uploader.strings.feedback_messages[ response.feedback_code ],
					type : 'success'
				} );

				self.views.add( {
					id   : 'status',
					view : avatarStatus
				} );

				avatarStatus.inject( '.bp-avatar-status' );

				// Update each avatars of the page
				$( '.' + model.get( 'object' ) + '-' + response.item_id + '-avatar').each( function() {
					$( this ).prop( 'src', response.avatar );
				} );

				// Remove the Delete nav
				bp.Avatar.navItems.get( 'delete' ).set( { active: 0, hide: 1 } );

				/**
				 * Reset the Attachment object
				 *
				 * You can run extra actions once the avatar is set using:
				 * bp.Avatar.Attachment.on( 'change:url', function( data ) { your code } );
				 *
				 * In this case data.attributes will include the url to the gravatar,
				 * the object and the item_id concerned.
				 */
				self.Attachment.set( _.extend(
					_.pick( model.attributes, ['object', 'item_id'] ),
					{ url: response.avatar, action: 'deleted' }
				) );

			} ).fail( function( response ) {
				var feedback = BP_Uploader.strings.default_error;
				if ( ! _.isUndefined( response ) ) {
					feedback = BP_Uploader.strings.feedback_messages[ response.feedback_code ];
				}

				var avatarStatus = new bp.Views.AvatarStatus( {
					value : feedback,
					type : 'error'
				} );

				self.views.add( {
					id   : 'status',
					view : avatarStatus
				} );

				avatarStatus.inject( '.bp-avatar-status' );
			} );
		},

		removeWarning: function() {
			if ( ! _.isNull( this.warning ) ) {
				this.warning.remove();
			}
		},

		displayWarning: function( message ) {
			this.removeWarning();

			this.warning = new bp.Views.uploaderWarning( {
				value: message
			} );

			this.warning.inject( '.bp-avatar-status' );
		}
	};

	// Main Nav view
	bp.Views.Nav = bp.View.extend( {
		tagName:    'ul',
		className:  'avatar-nav-items',

		events: {
			'click .bp-avatar-nav-item' : 'toggleView'
		},

		initialize: function() {
			var hasAvatar = _.findWhere( this.collection.models, { id: 'delete' } );

			// Display a message to inform about the delete tab
			if ( 1 !== hasAvatar.get( 'hide' ) ) {
				bp.Avatar.displayWarning( BP_Uploader.strings.has_avatar_warning );
			}

			_.each( this.collection.models, this.addNavItem, this );
			this.collection.on( 'change:hide', this.showHideNavItem, this );
		},

		addNavItem: function( item ) {
			/**
			 * The delete nav is not added if no avatar
			 * is set for the object
			 */
			if ( 1 === item.get( 'hide' ) ) {
				return;
			}

			this.views.add( new bp.Views.NavItem( { model: item } ) );
		},

		showHideNavItem: function( item ) {
			var isRendered = null;

			/**
			 * Loop in views to show/hide the nav item
			 * BuddyPress is only using this for the delete nav
			 */
			_.each( this.views._views[''], function( view ) {
				if ( 1 === view.model.get( 'hide' ) ) {
					view.remove();
				}

				// Check to see if the nav is not already rendered
				if ( item.get( 'id' ) === view.model.get( 'id' ) ) {
					isRendered = true;
				}
			} );

			// Add the Delete nav if not rendered
			if ( ! _.isBoolean( isRendered ) ) {
				this.addNavItem( item );
			}
		},

		toggleView: function( event ) {
			event.preventDefault();

			// First make sure to remove all warnings
			bp.Avatar.removeWarning();

			var active = $( event.target ).data( 'nav' );

			_.each( this.collection.models, function( model ) {
				if ( model.id === active ) {
					model.set( { active: 1 } );
					this.trigger( 'bp-avatar-view:changed', model.id );
				} else {
					model.set( { active: 0 } );
				}
			}, this );
		}
	} );

	// Nav item view
	bp.Views.NavItem = bp.View.extend( {
		tagName:    'li',
		className:  'avatar-nav-item',
		template: bp.template( 'bp-avatar-nav' ),

		initialize: function() {
			if ( 1 === this.model.get( 'active' ) ) {
				this.el.className += ' current';
			}
			this.el.id += 'bp-avatar-' + this.model.get( 'id' );

			this.model.on( 'change:active', this.setCurrentNav, this );
		},

		setCurrentNav: function( model ) {
			if ( 1 === model.get( 'active' ) ) {
				this.$el.addClass( 'current' );
			} else {
				this.$el.removeClass( 'current' );
			}
		}
	} );

	// Avatars view
	bp.Views.Avatars = bp.View.extend( {
		className: 'items',

		initialize: function() {
			_.each( this.collection.models, this.addItemView, this );
		},

		addItemView: function( item ) {
			// Defaults to 150
			var full_d = { full_h: 150, full_w: 150 };

			// Make sure to take in account bp_core_avatar_full_height or bp_core_avatar_full_width php filters
			if ( ! _.isUndefined( BP_Uploader.settings.crop.full_h ) && ! _.isUndefined( BP_Uploader.settings.crop.full_w ) ) {
				full_d.full_h = BP_Uploader.settings.crop.full_h;
				full_d.full_w = BP_Uploader.settings.crop.full_w;
			}

			// Set the avatar model
			item.set( _.extend( _.pick( BP_Uploader.settings.defaults.multipart_params.bp_params,
				'object',
				'item_id',
				'nonces'
			), full_d ) );

			// Add the view
			this.views.add( new bp.Views.Avatar( { model: item } ) );
		}
	} );

	// Avatar view
	bp.Views.Avatar = bp.View.extend( {
		className: 'item',
		template: bp.template( 'bp-avatar-item' ),

		events: {
			'click .avatar-crop-submit': 'cropAvatar'
		},

		initialize: function() {
			_.defaults( this.options, {
				full_h:  BP_Uploader.settings.crop.full_h,
				full_w:  BP_Uploader.settings.crop.full_w,
				aspectRatio : 1
			} );

			// Display a warning if the image is smaller than minimum advised
			if ( false !== this.model.get( 'feedback' ) ) {
				bp.Avatar.displayWarning( this.model.get( 'feedback' ) );
			}

			this.on( 'ready', this.initCropper );
		},

		initCropper: function() {
			var self = this,
				tocrop = this.$el.find( '#avatar-to-crop img' ),
				availableWidth = this.$el.width(),
				selection = {}, crop_top, crop_bottom, crop_left, crop_right, nh, nw;

			if ( ! _.isUndefined( this.options.full_h ) && ! _.isUndefined( this.options.full_w ) ) {
				this.options.aspectRatio = this.options.full_w / this.options.full_h;
			}

			selection.w = this.model.get( 'width' );
			selection.h = this.model.get( 'height' );

			/**
			 * Make sure the crop preview is at the right of the avatar
			 * if the available width allowes it.
			 */
			if ( this.options.full_w + selection.w + 20 < availableWidth ) {
				$( '#avatar-to-crop' ).addClass( 'adjust' );
				this.$el.find( '.avatar-crop-management' ).addClass( 'adjust' );
			}

			if ( selection.h <= selection.w ) {
				crop_top    = Math.round( selection.h / 4 );
				nh = nw     = Math.round( selection.h / 2 );
				crop_bottom = nh + crop_top;
				crop_left   = ( selection.w - nw ) / 2;
				crop_right  = nw + crop_left;
			} else {
				crop_left   = Math.round( selection.w / 4 );
				nh = nw     = Math.round( selection.w / 2 );
				crop_right  = nw + crop_left;
				crop_top    = ( selection.h - nh ) / 2;
				crop_bottom = nh + crop_top;
			}

			// Add the cropping interface
			tocrop.Jcrop( {
				onChange: _.bind( self.showPreview, self ),
				onSelect: _.bind( self.showPreview, self ),
				aspectRatio: self.options.aspectRatio,
				setSelect: [ crop_left, crop_top, crop_right, crop_bottom ]
			}, function() {
				// Get the Jcrop API
				bp.Avatar.jcropapi = this;
			} );
		},

		cropAvatar: function( event ) {
			event.preventDefault();

			bp.Avatar.setAvatar( this.model );
		},

		showPreview: function( coords ) {
			if ( ! coords.w || ! coords.h ) {
				return;
			}

			if ( parseInt( coords.w, 10 ) > 0 ) {
				var fw = this.options.full_w;
				var fh = this.options.full_h;
				var rx = fw / coords.w;
				var ry = fh / coords.h;

				// Update the model
				this.model.set( { x: coords.x, y: coords.y, w: coords.w, h: coords.h } );

				$( '#avatar-crop-preview' ).css( {
					maxWidth:'none',
					width: Math.round( rx *  this.model.get( 'width' ) )+ 'px',
					height: Math.round( ry * this.model.get( 'height' ) )+ 'px',
					marginLeft: '-' + Math.round( rx * this.model.get( 'x' ) ) + 'px',
					marginTop: '-' + Math.round( ry * this.model.get( 'y' ) ) + 'px'
				} );
			}
		}
	} );

	// BuddyPress Avatar Feedback view
	bp.Views.AvatarStatus = bp.View.extend( {
		tagName: 'p',
		className: 'updated',
		id: 'bp-avatar-feedback',

		initialize: function() {
			this.el.className += ' ' + this.options.type;
			this.value = this.options.value;
		},

		render: function() {
			this.$el.html( this.value );
			return this;
		}
	} );

	// BuddyPress Avatar Delete view
	bp.Views.DeleteAvatar = bp.View.extend( {
		tagName: 'div',
		id: 'bp-delete-avatar-container',
		template: bp.template( 'bp-avatar-delete' ),

		events: {
			'click #bp-delete-avatar': 'deleteAvatar'
		},

		deleteAvatar: function( event ) {
			event.preventDefault();

			bp.Avatar.deleteAvatar( this.model );
		}
	} );

	bp.Avatar.start();

})( bp, jQuery );
