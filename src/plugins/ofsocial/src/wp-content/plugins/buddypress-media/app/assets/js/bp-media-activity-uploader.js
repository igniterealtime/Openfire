/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

jQuery(document).ready(function(){
    if ( jQuery('#bp-media-activity-upload-ui').length > 0 ) {
                
        jQuery('#whats-new').off('focus');
        jQuery('#whats-new').on('focus', function(){
            jQuery("#whats-new-options").css('height','auto');
            jQuery("form#whats-new-form textarea").animate({
                height:'50px'
            });
            jQuery("#aw-whats-new-submit").prop("disabled", false);
        });
                
        jQuery("input#aw-whats-new-submit").off('click');
        jQuery("input#aw-whats-new-submit").on('click',function() {
            var button = jQuery(this);
            var form = button.parent().parent().parent().parent();

            form.children().each( function() {
                if ( jQuery.nodeName(this, "textarea") || jQuery.nodeName(this, "input") )
                    jQuery(this).prop( 'disabled', true );
            });

            /* Remove any errors */
            jQuery('div.error').remove();
            button.addClass('loading');
            button.prop('disabled', true);

            /* Default POST values */
            var object = '';
            var item_id = jQuery("#whats-new-post-in").val();
            var content = jQuery("#bp-media-dummy-update").val();

            /* Set object for non-profile posts */
            if ( item_id > 0 ) {
                object = jQuery("#whats-new-post-object").val();
            }

            jQuery.post( ajaxurl, {
                action: 'post_update',
                'cookie': encodeURIComponent(document.cookie),
                '_wpnonce_post_update': jQuery("input#_wpnonce_post_update").val(),
                'content': content,
                'object': object,
                'item_id': item_id,
                '_bp_as_nonce': jQuery('#_bp_as_nonce').val() || ''
            },
            function(response) {

                form.children().each( function() {
                    if ( jQuery.nodeName(this, "textarea") || jQuery.nodeName(this, "input") ) {
                        jQuery(this).prop( 'disabled', false );
                    }
                });

                /* Check for errors and append if found. */
                if ( response[0] + response[1] == '-1' ) {
                    form.prepend( response.substr( 2, response.length ) );
                    jQuery( 'form#' + form.attr('id') + ' div.error').hide().fadeIn( 200 );
                } else {
                    if ( 0 == jQuery("ul.activity-list").length ) {
                        jQuery("div.error").slideUp(100).remove();
                        jQuery("div#message").slideUp(100).remove();
                        jQuery("div.activity").append( '<ul id="activity-stream" class="activity-list item-list">' );
                    }

                    jQuery("ul#activity-stream").prepend(response);
                    jQuery("ul#activity-stream li:first").addClass('new-update');

                    if ( 0 != jQuery("#latest-update").length ) {
                        var l = jQuery("ul#activity-stream li.new-update .activity-content .activity-inner p").html();
                        var v = jQuery("ul#activity-stream li.new-update .activity-content .activity-header p a.view").attr('href');

                        var ltext = jQuery("ul#activity-stream li.new-update .activity-content .activity-inner p").text();

                        var u = '';
                        if ( ltext != '' )
                            u = l + ' ';

                        u += '<a href="' + v + '" rel="nofollow">' + BP_DTheme.view + '</a>';

                        jQuery("#latest-update").slideUp(300,function(){
                            jQuery("#latest-update").html( u );
                            jQuery("#latest-update").slideDown(300);
                        });
                    }

                    jQuery("li.new-update").hide().slideDown( 300 );
                    jQuery("li.new-update").removeClass( 'new-update' );
                    jQuery("textarea#whats-new").val('');
                }

                jQuery("#whats-new-options").animate({
                    height:'0px'
                });
                jQuery("form#whats-new-form textarea").animate({
                    height:'20px'
                });
                jQuery("#aw-whats-new-submit").prop("disabled", true).removeClass('loading');
            });

            return false;
        });
        
        $dummy_update_box = jQuery('<input id="bp-media-dummy-update" type="hidden" name="whats-new" />');
        $update_container = jQuery('#whats-new-textarea');
        $update_container.append($dummy_update_box);

        jQuery('#whats-new').on('keyup',function(){
            $this = jQuery(this);
            $that = jQuery('#bp-media-update-text');
            $that.val($this.val()).change();
        });
        jQuery('#bp-media-update-text').on('change',function(){
            bp_media_overwrite();
        });
        jQuery('#bp-media-update-json').on('change',function(){
            bp_media_overwrite();
        });

        $bp_media_activity_is_multiple_upload = false;
        $bp_media_activity_uploader=new plupload.Uploader(bp_media_uploader_params);
        $bp_media_activity_album_selected = false;
        $bp_media_activity_uploader.init();

        $bp_media_activity_uploader.bind('FilesAdded', function(up, files) {
            //bp_media_is_multiple_upload = files.length==1&&jQuery('.bp-media-progressbar').length==0?false:true;
            $bp_media_activity_is_multiple_upload = files.length>1;
            jQuery.each(files, function(i, file) {
                $bp_media_activity_extension = file.name.substr( (file.name.lastIndexOf('.') +1) );
                jQuery('#bp-media-activity-uploaded-files').append('<div id="bp-media-activity-progress-'+file.id+'" class="bp-media-progressbar"><div class="bp-media-progress-text">' + file.name + ' (' + plupload.formatSize(file.size) + ')(<b>0%</b>)</div><div class="bp-media-progress-completed"></div></div>');
            });
            //                bp_media_activity_album_selected = jQuery('#bp-media-activity-selected-album').val();
            $bp_media_activity_album_selected = default_album;
            $bp_media_activity_uploader.start();
            up.refresh(); // Reposition Flash/Silverlight
        });
        $bp_media_activity_uploader.bind('UploadProgress', function(up, file) {
            jQuery('input#aw-whats-new-submit').prop('disabled',true).addClass('loading');
            jQuery('#bp-media-activity-progress-'+file.id+' .bp-media-progress-completed').width(file.percent+'%');
            jQuery('#bp-media-activity-progress-'+file.id+' .bp-media-progress-text b').html(file.percent+'%');
        });

        $bp_media_activity_uploader.bind('Error', function(up, err) {
            jQuery('#bp-media-activity-uploaded-files').html('<div class="error"><p>Error: ' + err.code +
                ', Message: ' + err.message +
                (err.file ? ', File: ' + err.file.name : '') +
                '</p></div>'
                );
            up.refresh();
        });
        $bp_media_activity_uploader.bind('FileUploaded', function(up, file,response) {
            jQuery('#bp-media-activity-progress-'+file.id+' .bp-media-progress-text b').html("100%");
            $album_arr = [];
            $val = jQuery('#bp-media-update-json').val();
            if($val!=''){
                $album_arr= JSON.parse($val);
            }
            $album_arr.push(parseInt(response.response));
            $album_json =JSON.stringify($album_arr);
            jQuery('#bp-media-update-json').val($album_json).change();
            jQuery('#aw-whats-new-submit').prop('disabled',false).removeClass('loading');

        });
        $bp_media_activity_uploader.bind('BeforeUpload',function(up){
            up.settings.multipart_params.is_multiple_upload = $bp_media_activity_is_multiple_upload;
            up.settings.multipart_params.bp_media_album_id = $bp_media_activity_album_selected;
            up.settings.multipart_params.is_activity = true;
        });
        //jQuery("#aw-whats-new-submit").off( 'click');

        jQuery("#aw-whats-new-submit").on( 'click', function() {
            $latest = '';
            $val = bp_media_stringify();
            jQuery("#bp-media-dummy-update").val('');
            jQuery("#bp-media-update-json").val('');
            jQuery("#bp-media-update-txt").val('');
            jQuery("#bp-media-activity-uploaded-files").empty();
            setTimeout(function(){
                if($val!=''){
                    $album_arr= JSON.parse($val);
                    $lastid = parseInt($album_arr.length) - 1;
                    $media_id = $album_arr[parseInt($lastid)];
                    $activity = (jQuery('#activity-stream').find('li').first().attr('id')).split('-');
                    $activity_id = $activity[1];
                    var data = {
                        action: 'bp_media_get_latest_activity',
                        content : $val,
                        id: $activity_id
                    };
                    jQuery.get(ajaxurl,data,function(response){
                        $latest = response;
                        jQuery('#latest-update').html($latest);
                    });
                }
            },1000);
        });

        $bp_media_activity_uploader.bind('UploadComplete',function(response){

            });
    }


    function bp_media_stringify(){
        $album_json = jQuery('#bp-media-update-json').val();
        $update_txt = jQuery('#bp-media-update-text').val();
        $activity = {
            'media':$album_json,
            'update_txt':encodeURIComponent($update_txt)
        };
        return JSON.stringify($activity);
    }

    function bp_media_overwrite(){
        jQuery('#bp-media-dummy-update').val(bp_media_stringify());
    }
});