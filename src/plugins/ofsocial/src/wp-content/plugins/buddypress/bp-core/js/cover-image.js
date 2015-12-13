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

	bp.CoverImage = {
		start: function() {

			// Init some vars
			this.views   = new Backbone.Collection();
			this.warning = null;

			// The Cover Image Attachment object.
			this.Attachment = new Backbone.Model();

			// Set up views
			this.uploaderView();

			// Inform about the needed dimensions
			this.displayWarning( BP_Uploader.strings.cover_image_warnings.dimensions );

			// Set up the delete view if needed
			if ( true === BP_Uploader.settings.defaults.multipart_params.bp_params.has_cover_image ) {
				this.deleteView();
			}
		},

		uploaderView: function() {
			// Listen to the Queued uploads
			bp.Uploader.filesQueue.on( 'add', this.uploadProgress, this );

			// Create the BuddyPress Uploader
			var uploader = new bp.Views.Uploader();

			// Add it to views
			this.views.add( { id: 'upload', view: uploader } );

			// Display it
			uploader.inject( '.bp-cover-image' );
		},

		uploadProgress: function() {
			// Create the Uploader status view
			var coverImageUploadProgress = new bp.Views.coverImageUploadProgress( { collection: bp.Uploader.filesQueue } );

			if ( ! _.isUndefined( this.views.get( 'status' ) ) ) {
				this.views.set( { id: 'status', view: coverImageUploadProgress } );
			} else {
				this.views.add( { id: 'status', view: coverImageUploadProgress } );
			}

			// Display it
			coverImageUploadProgress.inject( '.bp-cover-image-status' );
		},

		deleteView: function() {
			// Create the delete model
			var delete_model = new Backbone.Model( _.pick( BP_Uploader.settings.defaults.multipart_params.bp_params,
				['object', 'item_id', 'nonces']
			) );

			// Do not add it if already there!
			if ( ! _.isUndefined( this.views.get( 'delete' ) ) ) {
				return;
			}

			// Create the delete view
			var deleteView = new bp.Views.DeleteCoverImage( { model: delete_model } );

			// Add it to views
			this.views.add( { id: 'delete', view: deleteView } );

			// Display it
			deleteView.inject( '.bp-cover-image-manage' );
		},

		deleteCoverImage: function( model ) {
			var self = this,
				deleteView;

			// Remove the delete view
			if ( ! _.isUndefined( this.views.get( 'delete' ) ) ) {
				deleteView = this.views.get( 'delete' );
				deleteView.get( 'view' ).remove();
				this.views.remove( { id: 'delete', view: deleteView } );
			}

			// Remove the cover image !
			bp.ajax.post( 'bp_cover_image_delete', {
				json:          true,
				item_id:       model.get( 'item_id' ),
				object:        model.get( 'object' ),
				nonce:         model.get( 'nonces' ).remove
			} ).done( function( response ) {
				var coverImageStatus = new bp.Views.CoverImageStatus( {
					value : BP_Uploader.strings.feedback_messages[ response.feedback_code ],
					type : 'success'
				} );

				self.views.add( {
					id   : 'status',
					view : coverImageStatus
				} );

				coverImageStatus.inject( '.bp-cover-image-status' );

				// Reset the header of the page
				if ( '' === response.reset_url ) {
					$( '#header-cover-image' ).css( {
						'background-image': 'none'
					} );
				} else {
					$( '#header-cover-image' ).css( {
						'background-image': 'url( ' + response.reset_url + ' )'
					} );
				}

				// Reset the has_cover_image bp_param
				BP_Uploader.settings.defaults.multipart_params.bp_params.has_cover_image = false;

				/**
				 * Reset the Attachment object
				 *
				 * You can run extra actions once the cover image is set using:
				 * bp.CoverImage.Attachment.on( 'change:url', function( data ) { your code } );
				 *
				 * In this case data.attributes will include the default url for the
				 * cover image (most of the time: ''), the object and the item_id concerned.
				 */
				self.Attachment.set( _.extend(
					_.pick( model.attributes, ['object', 'item_id'] ),
					{ url: response.reset_url, action: 'deleted' }
				) );

			} ).fail( function( response ) {
				var feedback = BP_Uploader.strings.default_error;
				if ( ! _.isUndefined( response ) ) {
					feedback = BP_Uploader.strings.feedback_messages[ response.feedback_code ];
				}

				var coverImageStatus = new bp.Views.CoverImageStatus( {
					value : feedback,
					type : 'error'
				} );

				self.views.add( {
					id   : 'status',
					view : coverImageStatus
				} );

				coverImageStatus.inject( '.bp-cover-image-status' );

				// Put back the delete view
				bp.CoverImage.deleteView();
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

			this.warning.inject( '.bp-cover-image-status' );
		}
	};

	// Custom Uploader Files view
	bp.Views.coverImageUploadProgress = bp.Views.uploaderStatus.extend( {
		className: 'files',

		initialize: function() {
			bp.Views.uploaderStatus.prototype.initialize.apply( this, arguments );

			this.collection.on( 'change:url', this.uploadResult, this );
		},

		uploadResult: function( model ) {
			var message, type;

			if ( ! _.isUndefined( model.get( 'url' ) ) ) {

				// Image is too small
				if ( 0 === model.get( 'feedback_code' ) ) {
					message = BP_Uploader.strings.cover_image_warnings.dimensions;
					type    = 'warning';

				// Success, Rock n roll!
				} else {
					message = BP_Uploader.strings.feedback_messages[ model.get( 'feedback_code' ) ];
					type = 'success';
				}

				this.views.set( '.bp-uploader-progress', new bp.Views.CoverImageStatus( {
					value : message,
					type  : type
				} ) );

				// Update the header of the page
				$( '#header-cover-image' ).css( {
					'background-image': 'url( ' + model.get( 'url' ) + ' )'
				} );

				// Add the delete view
				bp.CoverImage.deleteView();

				/**
				 * Set the Attachment object
				 *
				 * You can run extra actions once the cover image is set using:
				 * bp.CoverImage.Attachment.on( 'change:url', function( data ) { your code } );
				 *
				 * In this case data.attributes will include the url to the newly
				 * uploaded cover image, the object and the item_id concerned.
				 */
				bp.CoverImage.Attachment.set( _.extend(
					_.pick( BP_Uploader.settings.defaults.multipart_params.bp_params, ['object', 'item_id'] ),
					{ url: model.get( 'url' ), action: 'uploaded' }
				) );
			}
		}
	} );

	// BuddyPress Cover Image Feedback view
	bp.Views.CoverImageStatus = bp.View.extend( {
		tagName: 'p',
		className: 'updated',
		id: 'bp-cover-image-feedback',

		initialize: function() {
			this.el.className += ' ' + this.options.type;
			this.value = this.options.value;
		},

		render: function() {
			this.$el.html( this.value );
			return this;
		}
	} );

	// BuddyPress Cover Image Delete view
	bp.Views.DeleteCoverImage = bp.View.extend( {
		tagName: 'div',
		id: 'bp-delete-cover-image-container',
		template: bp.template( 'bp-cover-image-delete' ),

		events: {
			'click #bp-delete-cover-image': 'deleteCoverImage'
		},

		deleteCoverImage: function( event ) {
			event.preventDefault();

			bp.CoverImage.deleteCoverImage( this.model );
		}
	} );

	bp.CoverImage.start();

})( bp, jQuery );
