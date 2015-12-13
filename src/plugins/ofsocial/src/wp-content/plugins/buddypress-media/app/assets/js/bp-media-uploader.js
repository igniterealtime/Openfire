/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

jQuery(document).ready(function(){

    var selected = jQuery('#bp-media-album-prompt select').val();
    var in_list = 0;
    if(jQuery('#'+bp_media_uploader_params.container).length==0)
        return false;
    if ( jQuery('#bp-media-album-prompt p').css('display') == 'none' )
        in_list = 1;
    jQuery('#bp-media-album-prompt select').change(function() {
        if ( jQuery(this).val() == 'create_new' ) {
            jQuery('#bp-media-album-prompt select').hide();
            jQuery('#bp-media-album-prompt p').hide();
            jQuery('#bp-media-album-prompt div.hide').show();
        } else
            selected = jQuery(this).val();
    });
    var new_album_flag = 0;
    jQuery('#btn-create-new').click(function(){
        if ( new_album_flag == 1 ) {
            return false;
        }
        var new_album_name = jQuery('#bp_media_album_new').val();
        if(new_album_name.length==0){
            alert(bp_media_uploader_strings.no_name);
            return false;
        } else {
            new_album_flag = 1;
            jQuery(this).val('Wait');
            var data = {
                action: 'bp_media_add_album',
                bp_media_album_name : new_album_name,
                bp_media_group_id : bp_media_uploader_params.multipart_params.bp_media_group_id
            };
            jQuery.post(bp_media_vars.ajaxurl,data,function(response){
                var album = parseInt(response);
                if(album == 0){
                    alert(bp_media_uploader_strings.cant_upload_group_album);
                } else {
                    jQuery('#bp-media-album-prompt select option').removeAttr('selected');
                    jQuery('#bp-media-selected-album').prepend('<option value='+album+' selected="selected">'+new_album_name+'</option>');
                    jQuery('#bp-media-album-prompt div.hide').hide();
                    jQuery('#bp-media-album-prompt select').show();
                    if ( in_list == 0 )
                        jQuery('#bp-media-album-prompt p').show();
                }
            });
        }
    });
    jQuery('#btn-create-cancel').click(function(){
        jQuery('#bp-media-album-prompt div.hide').hide();
        jQuery('#bp-media-album-prompt select option').removeAttr('selected');
        jQuery('#bp-media-album-prompt select option[value=' + selected + ']').attr('selected', 'selected');
        jQuery('#bp-media-album-prompt select').show();
        if ( in_list == 0 )
                jQuery('#bp-media-album-prompt p').show();
    });

    //Normal Uplaoder
    var bp_media_is_multiple_upload = false;
    var bp_media_uploader=new plupload.Uploader(bp_media_uploader_params);
    var bp_media_album_selected = false;
    bp_media_uploader.init();

    bp_media_uploader.bind('FilesAdded', function(up, files) {
        if ( jQuery('#bp-media-selected-album').val() == 'create_new' ) {
            alert(bp_media_uploader_strings.select_album);
            return false;
        }
        //bp_media_is_multiple_upload = files.length==1&&jQuery('.bp-media-progressbar').length==0?false:true;
        bp_media_is_multiple_upload = files.length>1;
        jQuery.each(files, function(i, file) {
            var extension = file.name.substr( (file.name.lastIndexOf('.') +1) );
            jQuery('#bp-media-uploaded-files').append('<div id="bp-media-progress-'+file.id+'" class="bp-media-progressbar"><div class="bp-media-progress-text">' + file.name + ' (' + plupload.formatSize(file.size) + ')(<b>0%</b>)</div><div class="bp-media-progress-completed"></div></div>');
        });
        bp_media_album_selected = jQuery('#bp-media-selected-album').val();
        bp_media_uploader.start();
        up.refresh(); // Reposition Flash/Silverlight
    });
    bp_media_uploader.bind('UploadProgress', function(up, file) {
        jQuery('#bp-media-progress-'+file.id+' .bp-media-progress-completed').width(file.percent+'%');
        jQuery('#bp-media-progress-'+file.id+' .bp-media-progress-text b').html(file.percent+'%');
    });

    bp_media_uploader.bind('Error', function(up, err) {
        jQuery('#bp-media-uploaded-files').html('<div class="error"><p>Error: ' + err.code +
            ', Message: ' + err.message +
            (err.file ? ', File: ' + err.file.name : '') +
            '</p></div>'
            );
        up.refresh();
    });

    bp_media_uploader.bind('FileUploaded', function(up, file) {
        jQuery('#bp-media-progress-'+file.id+' .bp-media-progress-text b').html("100%");
    });
    bp_media_uploader.bind('BeforeUpload',function(up){
        up.settings.multipart_params.is_multiple_upload = bp_media_is_multiple_upload;
        up.settings.multipart_params.bp_media_album_id = bp_media_album_selected;
    });
    bp_media_uploader.bind('UploadComplete',function(){
        var new_location = window.location.href;
        if(new_location.search('/upload/')>0){
            new_location = new_location.replace('/upload/','/albums/');
            if(bp_media_album_selected>0)
                new_location = new_location.concat(bp_media_album_selected);
            else
                new_location = new_location.concat('0/');
            window.location.replace(new_location);
        } else            
            location.reload(true);
    });


});