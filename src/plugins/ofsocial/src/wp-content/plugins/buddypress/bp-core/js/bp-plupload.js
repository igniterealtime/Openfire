/* global bp, plupload, BP_Uploader, _, JSON, Backbone */

window.wp = window.wp || {};
window.bp = window.bp || {};

( function( exports, $ ) {

	// Bail if not set
	if ( typeof BP_Uploader === 'undefined' ) {
		return;
	}

	/**
	 * Extend the bp global with what we need from the wp one.
	 * and make sure previously defined BuddyPress attributes
	 * are not removed (eg: bp.mentions)
	 */
	_.extend( bp, _.pick( wp, 'Backbone', 'ajax', 'template' ) );

	// Init Models, Collections, Views and the BuddyPress Uploader
	bp.Models      = bp.Models || {};
	bp.Collections = bp.Collections || {};
	bp.Views       = bp.Views || {};
	bp.Uploader    = {};

	/**
	 * BuddyPress Uploader.
	 *
	 * This is an adapted version of wp.Uploader
	 */
	bp.Uploader.uploader = function() {
		var self = this,
			isIE = navigator.userAgent.indexOf('Trident/') !== -1 || navigator.userAgent.indexOf('MSIE ') !== -1;

		this.params  = BP_Uploader.settings;
		this.strings = BP_Uploader.strings;

		this.supports = {
			upload: this.params.browser.supported
		};

		this.supported = this.supports.upload;

		if ( ! this.supported ) {
			/*jshint -W020 */
			BP_Uploader = undefined;
			return;
		}

		// Make sure flash sends cookies (seems in IE it does without switching to urlstream mode)
		if ( ! isIE && 'flash' === plupload.predictRuntime( this.params.defaults ) &&
			( ! this.params.defaults.required_features || ! this.params.defaults.required_features.hasOwnProperty( 'send_binary_string' ) ) ) {

			this.params.defaults.required_features = this.params.defaults.required_features || {};
			this.params.defaults.required_features.send_binary_string = true;
		}

		this.uploader = new plupload.Uploader( this.params.defaults );

		/**
		 * After the Uploader has been initialized, initialize some behaviors for the dropzone.
		 *
		 * @event Init
		 * @param {plupload.Uploader} uploader Uploader instance.
		 */
		this.uploader.bind( 'Init', function( uploader ) {
			var container    = $( '#' + self.params.defaults.container ),
			    drop_element = $( '#' + self.params.defaults.drop_element );

			if ( 'html4' === uploader.runtime ) {
				uploader.settings.multipart_params.html4 = true;
			}

			/**
			 * Avatars need to be cropped, by default we are using an original
			 * max width of 450px, but there can be cases when this max width
			 * is larger than the one of the Avatar UI (eg: on mobile). To avoid any
			 * difficulties, we're adding a ui_available_width argument to the bp_params
			 * object and set it according to the container width. This value will be
			 * checked during the upload process to eventually adapt the resized avatar.
			 */
			if ( 'bp_avatar_upload' ===  uploader.settings.multipart_params.action ) {
				 uploader.settings.multipart_params.bp_params.ui_available_width = container.width();
			}

			if ( uploader.features.dragdrop && ! self.params.browser.mobile ) {
				container.addClass( 'drag-drop' );
				drop_element.bind( 'dragover.wp-uploader', function() {
					container.addClass( 'drag-over' );
				} ).bind( 'dragleave.wp-uploader, drop.wp-uploader', function() {
					container.removeClass( 'drag-over' );
				} );
			} else {
				container.removeClass( 'drag-drop' );
				drop_element.unbind( '.wp-uploader' );
			}

		} );

		// Init BuddyPress Uploader
		this.uploader.init();

		/**
		 * Feedback callback.
		 *
		 * Add a new message to the errors collection, so it's possible
		 * to give some feedback to the user
		 *
		 * @param  {string}        message
		 * @param  {object}        data
		 * @param  {plupload.File} file     File that was uploaded.
		 */
		this.feedback = function( message, data, file ) {
			if ( ! _.isNull( file ) && file.item ) {
				file.item.clear();
			}

			bp.Uploader.filesError.unshift( {
				message: message,
				data:    data,
				file:    file
			} );
		};

		/**
		 * After files were filtered and added to the queue, create a model for each.
		 *
		 * @event FilesAdded
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {Array}             files    Array of file objects that were added to queue by the user.
		 */
		this.uploader.bind( 'FilesAdded', function( uploader, files ) {
			var hundredmb = 100 * 1024 * 1024, max = parseInt( uploader.settings.max_file_size, 10 ),
			    _this = this;

			/**
			 * In case the multiple selection is false (eg: avatar) stop the process and send
			 * and event containing a warning
			 */
			if ( ! uploader.settings.multi_selection && files.length > 1 ) {
				for ( var i in files ) {
					uploader.removeFile( files[i] );
				}

				$( self ).trigger( 'bp-uploader-warning', self.strings.unique_file_warning );
				return;
			}

			_.each( files, function( file ) {
				var attributes;

				// Ignore failed uploads.
				if ( plupload.FAILED === file.status ) {
					return;
				}

				if ( max > hundredmb && file.size > hundredmb && uploader.runtime !== 'html5' ) {
					_this.uploadSizeError( uploader, file, true );
				} else {
					attributes = _.extend( {
						id:        file.id,
						file:      file,
						uploading: true,
						date:      new Date(),
						filename:  file.name
					}, _.pick( file, 'loaded', 'size', 'percent' ) );

					file.item = new bp.Models.File( attributes );
					bp.Uploader.filesQueue.add( file.item );
				}

			} );

			uploader.refresh();
			uploader.start();
		} );

		/**
		 * Update each file item on progress
		 *
		 * @event UploadProgress
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {Object}            file
		 */
		this.uploader.bind( 'UploadProgress', function( uploader, file ) {
			file.item.set( _.pick( file, 'loaded', 'percent' ) );
		} );

		/**
		 * After a file is successfully uploaded, update its model.
		 *
		 * @event FileUploaded
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {plupload.File}     file     File that was uploaded.
		 * @param {Object}            response Object with response properties.
		 * @return {mixed}
		 */
		this.uploader.bind( 'FileUploaded', function( uploader, file, response ) {
			var message = self.strings.default_error;

			try {
				response = JSON.parse( response.response );
			} catch ( e ) {
				return self.feedback( message, e, file );
			}

			if ( ! _.isObject( response ) || _.isUndefined( response.success ) ) {
				return self.feedback( message, null, file );
			} else if ( ! response.success ) {
				if ( response.data && response.data.message ) {
					message = response.data.message;
				}

				return self.feedback( message, response.data, file );
			}

			_.each(['file','loaded','size','percent'], function( key ) {
				file.item.unset( key );
			} );

			file.item.set( _.extend( response.data, { uploading: false } ) );

			//  Add the file to the Uploaded ones
			bp.Uploader.filesUploaded.add( file.item );

		} );

		/**
		 * Trigger an event to inform a new upload is being processed
		 *
		 * Mainly used to remove an eventual warning
		 *
		 * @event BeforeUpload
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {Array}             files    Array of file objects that were added to queue by the user.
		 */
		this.uploader.bind( 'BeforeUpload', function( uploader, files ) {
			$( self ).trigger( 'bp-uploader-new-upload', uploader, files );
		} );

		/**
		 * Reset the filesQueue once the upload is complete
		 *
		 * @event BeforeUpload
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {Array}             files    Array of file objects that were added to queue by the user.
		 */
		this.uploader.bind( 'UploadComplete', function( uploader, files ) {
			$( self ).trigger( 'bp-uploader-upload-complete', uploader, files );
			bp.Uploader.filesQueue.reset();
		} );

		/**
		 * Map Plupload errors & Create a warning when plupload failed
		 *
		 * @event Error
		 * @param {plupload.Uploader} uploader Uploader instance.
		 * @param {Object}            pluploadError Plupload error
		 */
		this.uploader.bind( 'Error', function( uploader, pluploadError ) {
			var message = self.strings.default_error,
				key,
				errors = {
					'FAILED':                 self.strings.upload_failed,
					'FILE_EXTENSION_ERROR':   self.strings.invalid_filetype,
					'IMAGE_FORMAT_ERROR':     self.strings.not_an_image,
					'IMAGE_MEMORY_ERROR':     self.strings.image_memory_exceeded,
					'IMAGE_DIMENSIONS_ERROR': self.strings.image_dimensions_exceeded,
					'GENERIC_ERROR':          self.strings.upload_failed,
					'IO_ERROR':               self.strings.io_error,
					'HTTP_ERROR':             self.strings.http_error,
					'SECURITY_ERROR':         self.strings.security_error,
					'FILE_SIZE_ERROR':        self.strings.file_exceeds_size_limit.replace( '%s' , pluploadError.file.name )
				};

			// Check for plupload errors.
			for ( key in errors ) {
				if ( pluploadError.code === plupload[ key ] ) {
					message = errors[ key ];
					break;
				}
			}

			$( self ).trigger( 'bp-uploader-warning', message );
			uploader.refresh();
		} );
	};

	// Create a very generic Model for files
	bp.Models.File = Backbone.Model.extend( {
		file: {}
	} );

	// Add Collections to store queue, uploaded files and errors
	$.extend( bp.Uploader, {
		filesQueue    : new Backbone.Collection(),
		filesUploaded : new Backbone.Collection(),
		filesError    : new Backbone.Collection()
	} );

	// Extend wp.Backbone.View with .prepare() and .inject()
	bp.View = bp.Backbone.View.extend( {
		inject: function( selector ) {
			this.render();
			$(selector).html( this.el );
			this.views.ready();
		},

		prepare: function() {
			if ( ! _.isUndefined( this.model ) && _.isFunction( this.model.toJSON ) ) {
				return this.model.toJSON();
			} else {
				return {};
			}
		}
	} );

	// BuddyPress Uploader main view
	bp.Views.Uploader = bp.View.extend( {
		className: 'bp-uploader-window',
		template: bp.template( 'upload-window' ),

		defaults: _.pick( BP_Uploader.settings.defaults, 'container', 'drop_element', 'browse_button' ),

		initialize: function() {
			this.warnings = [];
			this.model    = new Backbone.Model( this.defaults );
			this.on( 'ready', this.initUploader );
		},

		initUploader: function() {
			this.uploader = new bp.Uploader.uploader();
			$( this.uploader ).on( 'bp-uploader-warning', _.bind( this.setWarning, this ) );
			$( this.uploader ).on( 'bp-uploader-new-upload', _.bind( this.resetWarning, this ) );
		},

		setWarning: function( event, message ) {
			if ( _.isUndefined( message ) ) {
				return;
			}

			var warning = new bp.Views.uploaderWarning( {
				value: message
			} ).render();

			this.warnings.push( warning );

			this.$el.after( warning.el );
		},

		resetWarning: function() {
			if ( 0 === this.warnings.length ) {
				return;
			}

			// Remove all warning views
			_.each( this.warnings, function( view ) {
				view.remove();
			} );

			// Reset Warnings
			this.warnings = [];
		}
	} );

	// BuddyPress Uploader warning view
	bp.Views.uploaderWarning = bp.View.extend( {
		tagName: 'p',
		className: 'warning',

		initialize: function() {
			this.value = this.options.value;
		},

		render: function() {
			this.$el.html( this.value );
			return this;
		}
	} );

	// BuddyPress Uploader Files view
	bp.Views.uploaderStatus = bp.View.extend( {
		className: 'files',

		initialize: function() {
			_.each( this.collection.models, this.addFile, this );
			this.collection.on( 'change:percent', this.progress, this );
			bp.Uploader.filesError.on( 'add', this.feedback, this );
		},

		addFile: function( file ) {
			this.views.add( new bp.Views.uploaderProgress( { model: file } ) );
		},

		progress:function( model ) {
			if ( ! _.isUndefined( model.get( 'percent' ) ) ) {
				$( '#' + model.get('id') + ' .bp-progress .bp-bar' ).css( 'width', model.get('percent') + '%' );
			}
		},

		feedback: function( model ) {
			if ( ! _.isUndefined( model.get( 'message' ) ) && ! _.isUndefined( model.get( 'file' ) ) ) {
				$( '#' + model.get( 'file' ).id ).html( model.get( 'message' ) ).addClass( 'error' );
			}
		}
	} );

	// BuddyPress Uploader File progress view
	bp.Views.uploaderProgress = bp.View.extend( {
		className: 'bp-uploader-progress',
		template: bp.template( 'progress-window' )
	} );

})( bp, jQuery );
