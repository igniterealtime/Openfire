<?php

/**
 * BP-ALBUM IMAGE TRANSCODER CLASS
 * Transcodes various image formats to JPG format at a specified size
 *
 * @version 0.1.8.12
 * @since 0.1.8.12
 * @package BP-Album
 * @subpackage Image Transcoder
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */

class BPA_image {
    
    
       /**
	* Create a thumbnail from an Image given a maximum side size.
	*
	* This function can handle most image file formats which PHP supports. If PHP
	* does not have the functionality to save in a file of the same format, the
	* thumbnail will be created as a jpeg.
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param mixed $file Filename of the original image, Or attachment id.
	* @param int $max_side Maximum length of a single side for the thumbnail.
	* @return string Thumbnail path on success, Error string on failure.
	*/
    
	public function create_thumbnail( $file, $max_side) {
	    
		$thumbpath = self::image_resize( $file, $max_side, $max_side );
		
		return $thumbpath;
		
	}


       /**
	* Calculates the new dimensions for a downsampled image.
	*
	* If either width or height are empty, no constraint is applied on
	* that dimension.
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param int $current_width Current width of the image.
	* @param int $current_height Current height of the image.
	* @param int $max_width Optional. Maximum wanted width.
	* @param int $max_height Optional. Maximum wanted height.
	* @return array First item is the width, the second item is the height.
	*/
	public function constrain_dimensions( $current_width, $current_height, $max_width=0, $max_height=0 ) {
	    
		if ( !$max_width and !$max_height )
			return array( $current_width, $current_height );

		$width_ratio = $height_ratio = 1.0;
		$did_width = $did_height = false;

		if ( $max_width > 0 && $current_width > 0 && $current_width > $max_width ) {
			$width_ratio = $max_width / $current_width;
			$did_width = true;
		}

		if ( $max_height > 0 && $current_height > 0 && $current_height > $max_height ) {
			$height_ratio = $max_height / $current_height;
			$did_height = true;
		}

		// Calculate the larger/smaller ratios
		$smaller_ratio = min( $width_ratio, $height_ratio );
		$larger_ratio  = max( $width_ratio, $height_ratio );

		if ( intval( $current_width * $larger_ratio ) > $max_width || intval( $current_height * $larger_ratio ) > $max_height )
			// The larger ratio is too big. It would result in an overflow.
			$ratio = $smaller_ratio;
		else
			// The larger ratio fits, and is likely to be a more "snug" fit.
			$ratio = $larger_ratio;

		$w = intval( $current_width  * $ratio );
		$h = intval( $current_height * $ratio );

		// Sometimes, due to rounding, we'll end up with a result like this: 465x700 in a 177x177 box is 117x176... a pixel short
		// We also have issues with recursive calls resulting in an ever-changing result. Constraining to the result of a constraint should yield the original result.
		// Thus we look for dimensions that are one pixel shy of the max value and bump them up
		if ( $did_width && $w == $max_width - 1 )
			$w = $max_width; // Round it up
		if ( $did_height && $h == $max_height - 1 )
			$h = $max_height; // Round it up

		return array( $w, $h );
		
	}


       /**
	* Retrieve calculated resized dimensions for use in imagecopyresampled().
	*
	* Calculate dimensions and coordinates for a resized image that fits within a
	* specified width and height. If $crop is true, the largest matching central
	* portion of the image will be cropped out and resized to the required size.
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param int $orig_w Original width.
	* @param int $orig_h Original height.
	* @param int $dest_w New width.
	* @param int $dest_h New height.
	* @param bool $crop Optional, default is false. Whether to crop image or resize.
	* @return bool|array False on failure. Returned array matches parameters for imagecopyresampled() PHP function.
	*/
	public function image_resize_dimensions($orig_w, $orig_h, $dest_w, $dest_h, $crop = false) {

		if ($orig_w <= 0 || $orig_h <= 0)
			return false;
		// at least one of dest_w or dest_h must be specific
		if ($dest_w <= 0 && $dest_h <= 0)
			return false;

		if ( $crop ) {
			// crop the largest possible portion of the original image that we can size to $dest_w x $dest_h
			$aspect_ratio = $orig_w / $orig_h;
			$new_w = min($dest_w, $orig_w);
			$new_h = min($dest_h, $orig_h);

			if ( !$new_w ) {
				$new_w = intval($new_h * $aspect_ratio);
			}

			if ( !$new_h ) {
				$new_h = intval($new_w / $aspect_ratio);
			}

			$size_ratio = max($new_w / $orig_w, $new_h / $orig_h);

			$crop_w = round($new_w / $size_ratio);
			$crop_h = round($new_h / $size_ratio);

			$s_x = floor( ($orig_w - $crop_w) / 2 );
			$s_y = floor( ($orig_h - $crop_h) / 2 );
		} else {
			// don't crop, just resize using $dest_w x $dest_h as a maximum bounding box
			$crop_w = $orig_w;
			$crop_h = $orig_h;

			$s_x = 0;
			$s_y = 0;

			list( $new_w, $new_h ) = self::constrain_dimensions( $orig_w, $orig_h, $dest_w, $dest_h );
		}

		// if the resulting image would be the same size or larger we don't want to resize it
		if ( $new_w >= $orig_w && $new_h >= $orig_h )
			return false;

		// the return array matches the parameters to imagecopyresampled()
		// int dst_x, int dst_y, int src_x, int src_y, int dst_w, int dst_h, int src_w, int src_h
		return array( 0, 0, (int) $s_x, (int) $s_y, (int) $new_w, (int) $new_h, (int) $crop_w, (int) $crop_h );

	}


       /**
	* Create new GD image resource with transparency support
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param int $width Image width
	* @param int $height Image height
	* @return image resource
	*/
	public function imagecreatetruecolor($width, $height) {
	    
		$img = imagecreatetruecolor($width, $height);
		
		if ( is_resource($img) && function_exists('imagealphablending') && function_exists('imagesavealpha') ) {
			imagealphablending($img, false);
			imagesavealpha($img, true);
		}
		
		return $img;
		
	}

       /**
	* Load an image from a string, if PHP supports it.
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param string $file Filename of the image to load.
	* @return resource The resulting image resource on success, Error string on failure.
	*/
	
	public function load_image( $file ) {
	    
		if ( is_numeric( $file ) )
			$file = get_attached_file( $file );

		if ( ! file_exists( $file ) )
			return sprintf(__('File &#8220;%s&#8221; doesn&#8217;t exist?'), $file);

		if ( ! function_exists('imagecreatefromstring') )
			return __('The GD image library is not installed.');

		// Set artificially high because GD uses uncompressed images in memory
		@ini_set( 'memory_limit', apply_filters( 'image_memory_limit', WP_MAX_MEMORY_LIMIT ) );
		
		
		$image = imagecreatefromstring( file_get_contents( $file ) );

		
		if ( !is_resource( $image ) )
			return sprintf(__('File &#8220;%s&#8221; is not an image.'), $file);
			
		return $image;
	}
	
	
       /**
	* Scale down an image to fit a particular size and save a new copy of the image.
	*
	* The PNG transparency will be preserved using the function, as well as the
	* image type. If the file going in is PNG, then the resized image is going to
	* be PNG. The only supported image types are PNG, GIF, and JPEG.
	*
	* @version 0.1.8.12
	* @since 0.1.8.12
	*
	* @param string $file Image file path.
	* @param int $max_w Maximum width to resize to.
	* @param int $max_h Maximum height to resize to.
	* @param bool $crop Optional. Whether to crop image or resize.
	* @param string $suffix Optional. File suffix.
	* @param string $dest_path Optional. New image file path.
	* @param int $jpeg_quality Optional, default is 90. Image quality percentage.
	* @return mixed WP_Error on failure. String with new destination path.
	*/
	public function image_resize( $file, $max_w, $max_h, $crop = false, $suffix = null, $dest_path = null, $jpeg_quality = 100 ) {

		$image = self::load_image( $file );
		if ( !is_resource( $image ) )
			return new WP_Error( 'error_loading_image', $image, $file );
			

		$size = @getimagesize( $file );
		if ( !$size )
			return new WP_Error('invalid_image', __('Could not read image size'), $file);
		list($orig_w, $orig_h, $orig_type) = $size;

		$dims = self::image_resize_dimensions($orig_w, $orig_h, $max_w, $max_h, $crop);
		if ( !$dims )
			return new WP_Error( 'error_getting_dimensions', __('Could not calculate resized image dimensions') );
		list($dst_x, $dst_y, $src_x, $src_y, $dst_w, $dst_h, $src_w, $src_h) = $dims;

		$newimage = self::imagecreatetruecolor( $dst_w, $dst_h );

		imagecopyresampled( $newimage, $image, $dst_x, $dst_y, $src_x, $src_y, $dst_w, $dst_h, $src_w, $src_h);

		// convert from full colors to index colors, like original PNG.
		if ( IMAGETYPE_PNG == $orig_type && function_exists('imageistruecolor') && !imageistruecolor( $image ) )
			imagetruecolortopalette( $newimage, false, imagecolorstotal( $image ) );

		// we don't need the original in memory anymore
		imagedestroy( $image );
		
		//read image data
		$exif = exif_read_data( $file );
		
		//rotate images to display oreintation
		if( !empty( $exif['Orientation'] ) ) {
		    switch( $exif['Orientation'] ) {
		        case 8:
		            $newimage = imagerotate( $newimage,90,0 );
		            break;
		        case 3:
		            $newimage = imagerotate( $newimage,180,0 );
		            break;
		        case 6:
		            $newimage = imagerotate( $newimage,-90,0 );
		            break;
		    }
		}


		// $suffix will be appended to the destination filename, just before the extension
		if ( !$suffix )
			$suffix = "{$dst_w}x{$dst_h}";

		$info = pathinfo($file);
		$dir = $info['dirname'];
		$ext = $info['extension'];
		$name = wp_basename($file, ".$ext");

		if ( !is_null($dest_path) and $_dest_path = realpath($dest_path) )
			$dir = $_dest_path;
		$destfilename = "{$dir}/{$name}-{$suffix}.{$ext}";

		if ( IMAGETYPE_GIF == $orig_type ) {
			if ( !imagegif( $newimage, $destfilename ) )
				return new WP_Error('resize_path_invalid', __( 'Resize path invalid' ));
		} elseif ( IMAGETYPE_PNG == $orig_type ) {
			if ( !imagepng( $newimage, $destfilename ) )
				return new WP_Error('resize_path_invalid', __( 'Resize path invalid' ));
		} else {
			// all other formats are converted to jpg
			$destfilename = "{$dir}/{$name}-{$suffix}.jpg";
			if ( !imagejpeg( $newimage, $destfilename, apply_filters( 'jpeg_quality', $jpeg_quality, 'image_resize' ) ) )
				return new WP_Error('resize_path_invalid', __( 'Resize path invalid' ));
		}

		imagedestroy( $newimage );

		// Set correct file permissions
		$stat = stat( dirname( $destfilename ));
		$perms = $stat['mode'] & 0000666; //same permissions as parent folder, strip off the executable bits
		@ chmod( $destfilename, $perms );

		return $destfilename;
	}
    
    
}

?>