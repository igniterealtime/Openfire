<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Extracts media from text. Use {@link extract()}.
 *
 * @since 2.3.0
 *
 * The supported types are links, mentions, images, shortcodes, embeds, audio, video, and "all".
 * This is what each type extracts:
 *
 * Links:      <a href="http://example.com">
 * Mentions:   @name
 *             If the Activity component is enabled, we use it to parse out any at-names. A consequence
 *             to note is that the "name" mentioned must match a real user account. If it's a made-up
 *             at-name, then it isn't extracted.
 *             If the Activity component is disabled, any at-name is extracted (both those matching
 *             real accounts, and those made-up).
 * Images:     <img src="image.gif">, [gallery], [gallery ids="2,3"], featured images (Post thumbnails).
 *             If an extracted image is in the Media Library, then its resolution will be included.
 * Shortcodes: Extract information about any (registered) shortcodes.
 *             This includes any shortcodes indirectly covered by any of the other media extraction types.
 *             For example, [gallery].
 * Embeds:     Extract any URL matching a registered oEmbed handler.
 * Audio:      <a href="*.mp3"">, [audio]
 *             See wp_get_audio_extensions() for supported audio formats.
 * Video:      [video]
 *             See wp_get_video_extensions() for supported video formats.
 *
 * @see BP_Media_Extractor::extract() Use this to extract media.
 */
class BP_Media_Extractor {
	/**
	 * Media type.
	 *
	 * @since 2.3.0
	 * @var int
	 */
	const ALL        = 255;
	const LINKS      = 1;
	const MENTIONS   = 2;
	const IMAGES     = 4;
	const SHORTCODES = 8;
	const EMBEDS     = 16;
	const AUDIO      = 32;
	const VIDEOS     = 64;


	/**
	 * Extract media from text.
	 *
	 * @since 2.3.0
	 *
	 * @param string|WP_Post $richtext        Content to parse.
	 * @param int            $what_to_extract Media type to extract (optional).
	 * @param array          $extra_args      Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $audio
	 *         @type int $embeds
	 *         @type int $images
	 *         @type int $links
	 *         @type int $mentions
	 *         @type int $shortcodes
	 *         @type int $video
	 *     }
	 *     @type array $audio Extracted audio. {
	 *         Array of extracted media.
	 *
	 *         @type string $source Media source. Either "html" or "shortcodes".
	 *         @type string $url    Link to audio.
	 *     }
	 *     @type array $embeds Extracted oEmbeds. {
	 *         Array of extracted media.
	 *
	 *         @type string $url oEmbed link.
	 *     }
	 *     @type array $images Extracted images. {
	 *         Array of extracted media.
	 *
	 *         @type int    $gallery_id Gallery ID. Optional, not always set.
	 *         @type int    $height     Width of image. If unknown, set to 0.
	 *         @type string $source     Media source. Either "html" or "galleries".
	 *         @type string $url        Link to image.
	 *         @type int    $width      Width of image. If unknown, set to 0.
	 *     }
	 *     @type array $links Extracted URLs. {
	 *         Array of extracted media.
	 *
	 *         @type string $url Link.
	 *     }
	 *     @type array $mentions Extracted mentions. {
	 *         Array of extracted media.
	 *
	 *         @type string $name    @mention.
	 *         @type string $user_id User ID. Optional, only set if Activity component enabled.
	 *     }
	 *     @type array $shortcodes Extracted shortcodes. {
	 *         Array of extracted media.
	 *
	 *         @type array  $attributes Key/value pairs of the shortcodes attributes (if any).
	 *         @type string $content    Text wrapped by the shortcode.
	 *         @type string $type       Shortcode type.
	 *         @type string $original   The entire shortcode.
	 *     }
	 *     @type array $videos Extracted video. {
	 *         Array of extracted media.
	 *
	 *         @type string $source Media source. Currently only "shortcodes".
	 *         @type string $url    Link to audio.
	 *     }
	 * }
	 */
	public function extract( $richtext, $what_to_extract = self::ALL, $extra_args = array() ) {
		$media = array();

		// Support passing a WordPress Post for the $richtext parameter.
		if ( is_a( $richtext, 'WP_Post' ) ) {
			$extra_args['post'] = $richtext;
			$richtext           = $extra_args['post']->post_content;
		}

		$plaintext = $this->strip_markup( $richtext );


		// Extract links.
		if ( self::LINKS & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_links( $richtext, $plaintext, $extra_args ) );
		}

		// Extract mentions.
		if ( self::MENTIONS & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_mentions( $richtext, $plaintext, $extra_args ) );
		}

		// Extract images.
		if ( self::IMAGES & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_images( $richtext, $plaintext, $extra_args ) );
		}

		// Extract shortcodes.
		if ( self::SHORTCODES & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_shortcodes( $richtext, $plaintext, $extra_args ) );
		}

		// Extract oEmbeds.
		if ( self::EMBEDS & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_embeds( $richtext, $plaintext, $extra_args ) );
		}

		// Extract audio.
		if ( self::AUDIO & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_audio( $richtext, $plaintext, $extra_args ) );
		}

		// Extract video.
		if ( self::VIDEOS & $what_to_extract ) {
			$media = array_merge_recursive( $media, $this->extract_video( $richtext, $plaintext, $extra_args ) );
		}

		/**
		 * Filters media extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $media           Extracted media. See {@link BP_Media_Extractor::extract()} for format.
		 * @param string $richtext        Content to parse.
		 * @param int    $what_to_extract Media type to extract.
		 * @param array  $extra_args      Bespoke data for a particular extractor.
		 * @param string $plaintext       Copy of $richtext without any markup.
		 */
		return apply_filters( 'bp_media_extractor_extract', $media, $richtext, $what_to_extract, $extra_args, $plaintext );
	}


	/**
	 * Content type specific extraction methods.
	 *
	 * You shouldn't need to use these directly; just use {@link BP_Media_Extractor::extract()}.
	 */

	/**
	 * Extract `<a href>` tags from text.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $links
	 *     }
	 *     @type array $links Extracted URLs. {
	 *         Array of extracted media.
	 *
	 *         @type string $url Link.
	 *     }
	 * }
	 */
	protected function extract_links( $richtext, $plaintext, $extra_args = array() ) {
		$data = array( 'has' => array( 'links' => 0 ), 'links' => array() );

		// Matches: href="text" and href='text'
		if ( stripos( $richtext, 'href=' ) !== false ) {
			preg_match_all( '#href=(["\'])([^"\']+)\1#i', $richtext, $matches );

			if ( ! empty( $matches[2] ) ) {
				$matches[2] = array_unique( $matches[2] );

				foreach ( $matches[2] as $link_src ) {
					$link_src = esc_url_raw( $link_src );

					if ( $link_src ) {
						$data['links'][] = array( 'url' => $link_src );
					}
				}
			}
		}

		$data['has']['links'] = count( $data['links'] );

		/**
		 * Filters links extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted links. See {@link BP_Media_Extractor::extract_links()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_links', $data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract @mentions tags from text.
	 *
	 * If the Activity component is enabled, it is used to parse @mentions.
	 * The mentioned "name" must match a user account, otherwise it is discarded.
	 *
	 * If the Activity component is disabled, any @mentions are extracted.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor.
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $mentions
	 *     }
	 *     @type array $mentions Extracted mentions. {
	 *         Array of extracted media.
	 *
	 *         @type string $name    @mention.
	 *         @type string $user_id User ID. Optional, only set if Activity component enabled.
	 *     }
	 * }
	 */
	protected function extract_mentions( $richtext, $plaintext, $extra_args = array() ) {
		$data     = array( 'has' => array( 'mentions' => 0 ), 'mentions' => array() );
		$mentions = array();

		// If the Activity component is active, use it to parse @mentions.
		if ( bp_is_active( 'activity' ) ) {
			$mentions = bp_activity_find_mentions( $plaintext );
			if ( ! $mentions ) {
				$mentions = array();
			}

		// If the Activity component is disabled, instead do a basic parse.
		} else {
			if ( strpos( $plaintext, '@' ) !== false ) {
				preg_match_all( '/[@]+([A-Za-z0-9-_\.@]+)\b/', $plaintext, $matches );

				if ( ! empty( $matches[1] ) ) {
					$mentions = array_unique( array_map( 'strtolower', $matches[1] ) );
				}
			}
		}

		// Build results
		foreach ( $mentions as $user_id => $mention_name ) {
			$mention = array( 'name' => strtolower( $mention_name ) );

			// If the Activity component is active, store the User ID, too.
			if ( bp_is_active( 'activity' ) ) {
				$mention['user_id'] = (int) $user_id;
			}

			$data['mentions'][] = $mention;
		}

		$data['has']['mentions'] = count( $data['mentions'] );

		/**
		 * Filters @mentions extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted @mentions. See {@link BP_Media_Extractor::extract_mentions()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor (optional).
		 */
		return apply_filters( 'bp_media_extractor_mentions', $data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract images from `<img src>` tags, [galleries], and featured images from a Post.
	 *
	 * If an image is in the Media Library, then its resolution is included in the results.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $images
	 *     }
	 *     @type array $images Extracted images. {
	 *         Array of extracted media.
	 *
	 *         @type int    $gallery_id Gallery ID. Optional, not always set.
	 *         @type int    $height     Width of image. If unknown, set to 0.
	 *         @type string $source     Media source. Either "html" or "galleries".
	 *         @type string $url        Link to image.
	 *         @type int    $width      Width of image. If unknown, set to 0.
	 *     }
	 * }
	 */
	protected function extract_images( $richtext, $plaintext, $extra_args = array() ) {
		$media = array( 'has' => array( 'images' => 0 ), 'images' => array() );

		$featured_image = $this->extract_images_from_featured_images( $richtext, $plaintext, $extra_args );
		$galleries      = $this->extract_images_from_galleries( $richtext, $plaintext, $extra_args );


		// `<img src>` tags.
		if ( stripos( $richtext, 'src=' ) !== false ) {
			preg_match_all( '#src=(["\'])([^"\']+)\1#i', $richtext, $img_srcs );  // matches src="text" and src='text'

			// <img>.
			if ( ! empty( $img_srcs[2] ) ) {
				$img_srcs[2] = array_unique( $img_srcs[2] );

				foreach ( $img_srcs[2] as $image_src ) {
					// Skip data URIs.
					if ( strtolower( substr( $image_src, 0, 5 ) ) === 'data:' ) {
						continue;
					}

					$image_src = esc_url_raw( $image_src );
					if ( ! $image_src ) {
						continue;
					}

					$media['images'][] = array(
						'source' => 'html',
						'url'    => $image_src,

						// The image resolution isn't available, but we need to set the keys anyway.
						'height' => 0,
						'width'  => 0,
					);
				}
			}
		}

		// Galleries.
		if ( ! empty( $galleries ) ) {
			foreach ( $galleries as $gallery ) {
				foreach ( $gallery as $image ) {
					$image_url = esc_url_raw( $image['url'] );
					if ( ! $image_url ) {
						continue;
					}

					$media['images'][] = array(
						'gallery_id' => $image['gallery_id'],
						'source'     => 'galleries',
						'url'        => $image_url,
						'width'      => $image['width'],
						'height'     => $image['height'],
					);
				}
			}

			$media['has']['galleries'] = count( $galleries );
		}

		// Featured images (aka thumbnails).
		if ( ! empty( $featured_image ) ) {
			$image_url = esc_url_raw( $featured_image[0] );

			if ( $image_url ) {
				$media['images'][] = array(
					'source' => 'featured_images',
					'url'    => $image_url,
					'width'  => $featured_image[1],
					'height' => $featured_image[2],
				);

				$media['has']['featured_images'] = 1;
			}
		}

		// Update image count.
		$media['has']['images'] = count( $media['images'] );


		/**
		 * Filters images extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $media      Extracted images. See {@link BP_Media_Extractor::extract_images()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_images', $media, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract shortcodes from text.
	 *
	 * This includes any shortcodes indirectly used by other media extraction types.
	 * For example, [gallery] and [audio].
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $shortcodes
	 *     }
	 *     @type array $shortcodes Extracted shortcodes. {
	 *         Array of extracted media.
	 *
	 *         @type array  $attributes Key/value pairs of the shortcodes attributes (if any).
	 *         @type string $content    Text wrapped by the shortcode.
	 *         @type string $type       Shortcode type.
	 *         @type string $original   The entire shortcode.
	 *     }
	 * }
	 */
	protected function extract_shortcodes( $richtext, $plaintext, $extra_args = array() ) {
		$data = array( 'has' => array( 'shortcodes' => 0 ), 'shortcodes' => array() );

		// Match any registered WordPress shortcodes.
		if ( strpos( $richtext, '[' ) !== false ) {
			preg_match_all( '/' . get_shortcode_regex() . '/s', $richtext, $matches );

			if ( ! empty( $matches[2] ) ) {
				foreach ( $matches[2] as $i => $shortcode_name ) {
					$attrs = shortcode_parse_atts( $matches[3][ $i ] );
					$attrs = ( ! $attrs ) ? array() : $attrs;

					$shortcode               = array();
					$shortcode['attributes'] = $attrs;             // Attributes
					$shortcode['content']    = $matches[5][ $i ];  // Content
					$shortcode['type']       = $shortcode_name;    // Shortcode
					$shortcode['original']   = $matches[0][ $i ];  // Entire shortcode

					$data['shortcodes'][] = $shortcode;
				}
			}
		}

		$data['has']['shortcodes'] = count( $data['shortcodes'] );

		/**
		 * Filters shortcodes extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted shortcodes.
		 *                           See {@link BP_Media_Extractor::extract_shortcodes()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_shortcodes', $data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract any URL, matching a registered oEmbed endpoint, from text.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $embeds
	 *     }
	 *     @type array $embeds Extracted oEmbeds. {
	 *         Array of extracted media.
	 *
	 *         @type string $url oEmbed link.
	 *     }
	 * }
	 */
	protected function extract_embeds( $richtext, $plaintext, $extra_args = array() ) {
		$data   = array( 'has' => array( 'embeds' => 0 ), 'embeds' => array() );
		$embeds = array();

		if ( ! function_exists( '_wp_oembed_get_object' ) ) {
			require( ABSPATH . WPINC . '/class-oembed.php' );
		}


		// Matches any links on their own lines. They may be oEmbeds.
		if ( stripos( $richtext, 'http' ) !== false ) {
			preg_match_all( '#^\s*(https?://[^\s"]+)\s*$#im', $richtext, $matches );

			if ( ! empty( $matches[1] ) ) {
				$matches[1] = array_unique( $matches[1] );
				$oembed     = _wp_oembed_get_object();

				foreach ( $matches[1] as $link ) {
					// Skip data URIs.
					if ( strtolower( substr( $link, 0, 5 ) ) === 'data:' ) {
						continue;
					}

					foreach ( $oembed->providers as $matchmask => $oembed_data ) {
						list( , $is_regex ) = $oembed_data;

						// Turn asterisk-type provider URLs into regexs.
						if ( ! $is_regex ) {
							$matchmask = '#' . str_replace( '___wildcard___', '(.+)', preg_quote( str_replace( '*', '___wildcard___', $matchmask ), '#' ) ) . '#i';
							$matchmask = preg_replace( '|^#http\\\://|', '#https?\://', $matchmask );
						}

						// Check whether this "link" is really an oEmbed.
						if ( preg_match( $matchmask, $link ) ) {
							$data['embeds'][] = array( 'url' => $link );

							break;
						}
					}
				}
			}
		}

		$data['has']['embeds'] = count( $data['embeds'] );

		/**
		 * Filters embeds extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted embeds. See {@link BP_Media_Extractor::extract_embeds()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_embeds', $data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract [audio] shortcodes and `<a href="*.mp3">` tags, from text.
	 *
	 * @since 2.3.0
	 *
	 * @see wp_get_audio_extensions() for supported audio formats.
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $audio
	 *     }
	 *     @type array $audio Extracted audio. {
	 *         Array of extracted media.
	 *
	 *         @type string $original The entire shortcode.
	 *         @type string $source   Media source. Either "html" or "shortcodes".
	 *         @type string $url      Link to audio.
	 *     }
	 * }
	 */
	protected function extract_audio( $richtext, $plaintext, $extra_args = array() ) {
		$data   = array( 'has' => array( 'audio' => 0 ), 'audio' => array() );
		$audios = $this->extract_shortcodes( $richtext, $plaintext, $extra_args );
		$links  = $this->extract_links( $richtext, $plaintext, $extra_args );

		$audio_types = wp_get_audio_extensions();


		// [audio]
		$audios = wp_list_filter( $audios['shortcodes'], array( 'type' => 'audio' ) );
		foreach ( $audios as $audio ) {

			// Media URL can appear as the first parameter inside the shortcode brackets.
			if ( isset( $audio['attributes']['src'] ) ) {
				$src_param = 'src';
			} elseif ( isset( $audio['attributes'][0] ) ) {
				$src_param = 0;
			} else {
				continue;
			}

			$path = untrailingslashit( parse_url( $audio['attributes'][ $src_param ], PHP_URL_PATH ) );

			foreach ( $audio_types as $extension ) {
				$extension = '.' . $extension;

				// Check this URL's file extension matches that of an accepted audio format.
				if ( ! $path || substr( $path, -4 ) !== $extension ) {
					continue;
				}

				$data['audio'][] = array(
					'original' => '[audio src="' . esc_url_raw( $audio['attributes'][ $src_param ] ) . '"]',
					'source'   => 'shortcodes',
					'url'      => esc_url_raw( $audio['attributes'][ $src_param ] ),
				);
			}
		}

		// <a href="*.mp3"> tags
		foreach ( $audio_types as $extension ) {
			$extension = '.' . $extension;

			foreach ( $links['links'] as $link ) {
				$path = untrailingslashit( parse_url( $link['url'], PHP_URL_PATH ) );

				// Check this URL's file extension matches that of an accepted audio format.
				if ( ! $path || substr( $path, -4 ) !== $extension ) {
					continue;
				}

				$data['audio'][] = array(
					'original' => '[audio src="' . esc_url_raw( $link['url'] ) . '"]',  // Build an audio shortcode.
					'source'   => 'html',
					'url'      => esc_url_raw( $link['url'] ),
				);
			}
		}

		$data['has']['audio'] = count( $data['audio'] );

		/**
		 * Filters audio extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted audio. See {@link BP_Media_Extractor::extract_audio()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_audio', $data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract [video] shortcodes from text.
	 *
	 * @since 2.3.0
	 *
	 * @see wp_get_video_extensions() for supported video formats.
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array {
	 *     @type array $has Extracted media counts. {
	 *         @type int $video
	 *     }
	 *     @type array $videos Extracted video. {
	 *         Array of extracted media.
	 *
	 *         @type string $source Media source. Currently only "shortcodes".
	 *         @type string $url    Link to audio.
	 *     }
	 * }
	 */
	protected function extract_video( $richtext, $plaintext, $extra_args = array() ) {
		$data   = array( 'has' => array( 'videos' => 0 ), 'videos' => array() );
		$videos = $this->extract_shortcodes( $richtext, $plaintext, $extra_args );

		$video_types = wp_get_video_extensions();


		// [video]
		$videos = wp_list_filter( $videos['shortcodes'], array( 'type' => 'video' ) );
		foreach ( $videos as $video ) {

			// Media URL can appear as the first parameter inside the shortcode brackets.
			if ( isset( $video['attributes']['src'] ) ) {
				$src_param = 'src';
			} elseif ( isset( $video['attributes'][0] ) ) {
				$src_param = 0;
			} else {
				continue;
			}

			$path = untrailingslashit( parse_url( $video['attributes'][ $src_param ], PHP_URL_PATH ) );

			foreach ( $video_types as $extension ) {
				$extension = '.' . $extension;

				// Check this URL's file extension matches that of an accepted video format (-5 for webm).
				if ( ! $path || ( substr( $path, -4 ) !== $extension && substr( $path, -5 ) !== $extension ) ) {
					continue;
				}

				$data['videos'][] = array(
					'original' => $video['original'],  // Entire shortcode.
					'source'   => 'shortcodes',
					'url'      => esc_url_raw( $video['attributes'][ $src_param ] ),
				);
			}
		}

		$data['has']['videos'] = count( $data['videos'] );

		/**
		 * Filters videos extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $data       Extracted videos. See {@link BP_Media_Extractor::extract_videos()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_videos', $data, $richtext, $plaintext, $extra_args );
	}


	/**
	 * Helpers and utility methods.
	 */

	/**
	 * Extract images in [galleries] shortcodes from text.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Bespoke data for a particular extractor (optional).
	 *
	 * @return array
	 */
	protected function extract_images_from_galleries( $richtext, $plaintext, $extra_args = array() ) {
		if ( ! isset( $extra_args['post'] ) || ! is_a( $extra_args['post'], 'WP_Post' ) ) {
			$post = new WP_Post( (object) array( 'post_content' => $richtext ) );
		} else {
			$post = $extra_args['post'];
		}

		// We're not using get_post_galleries_images() because it returns thumbnails; we want the original image.
		$galleries      = get_post_galleries( $post, false );
		$galleries_data = array();

		if ( ! empty( $galleries ) ) {
			// Validate the size of the images requested.
			if ( isset( $extra_args['width'] ) ) {

				// A width was specified but not a height, so calculate it assuming a 4:3 ratio.
				if ( ! isset( $extra_args['height'] ) && ctype_digit( $extra_args['width'] ) ) {
					$extra_args['height'] = round( ( $extra_args['width'] / 4 ) * 3 );
				}

				if ( ctype_digit( $extra_args['width'] ) ) {
					$image_size = array( $extra_args['width'], $extra_args['height'] );
				} else {
					$image_size = $extra_args['width'];  // e.g. "thumb", "medium".
				}

			} else {
				$image_size = 'full';
			}

			/**
			 * There are two variants of gallery shortcode.
			 *
			 * One kind specifies the image (post) IDs via an `ids` parameter.
			 * The other gets the image IDs from post_type=attachment and post_parent=get_the_ID().
			 */

			foreach ( $galleries as $gallery_id => $gallery ) {
				$data   = array();
				$images = array();

				// Gallery ids= variant.
				if ( isset( $gallery['ids'] ) ) {
					$images = wp_parse_id_list( $gallery['ids'] );

				// Gallery post_parent variant.
				} elseif ( isset( $extra_args['post'] ) ) {
					$images = wp_parse_id_list(
						get_children( array(
							'fields'         => 'ids',
							'order'          => 'ASC',
							'orderby'        => 'menu_order ID',
							'post_mime_type' => 'image',
							'post_parent'    => $extra_args['post']->ID,
							'post_status'    => 'inherit',
							'post_type'      => 'attachment',
						) )
					);
				}

				// Extract the data we need from each image in this gallery.
				foreach ( $images as $image_id ) {
					$image  = wp_get_attachment_image_src( $image_id, $image_size );
					$data[] = array(
						'url'    => $image[0],
						'width'  => $image[1],
						'height' => $image[2],

						'gallery_id' => 1 + $gallery_id,
					);
				}

				$galleries_data[] = $data;
			}
		}

		/**
		 * Filters image galleries extracted from text.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $galleries_data Galleries. See {@link BP_Media_Extractor::extract_images_from_galleries()}.
		 * @param string $richtext       Content to parse.
		 * @param string $plaintext      Copy of $richtext without any markup.
		 * @param array  $extra_args     Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_galleries', $galleries_data, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Extract the featured image from a Post.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext   Content to parse.
	 * @param string $plaintext  Sanitized version of the content.
	 * @param array  $extra_args Contains data that an implementation might need beyond the defaults.
	 *
	 * @return array
	 */
	protected function extract_images_from_featured_images( $richtext, $plaintext, $extra_args ) {
		$image = array();
		$thumb = 0;

		if ( isset( $extra_args['post'] ) ) {
			$thumb = (int) get_post_thumbnail_id( $extra_args['post']->ID );
		}

		if ( $thumb ) {
			// Validate the size of the images requested.
			if ( isset( $extra_args['width'] ) ) {
				if ( ! isset( $extra_args['height'] ) && ctype_digit( $extra_args['width'] ) ) {
					// A width was specified but not a height, so calculate it assuming a 4:3 ratio.
					$extra_args['height'] = round( ( $extra_args['width'] / 4 ) * 3 );
				}

				if ( ctype_digit( $extra_args['width'] ) ) {
					$image_size = array( $extra_args['width'], $extra_args['height'] );
				} else {
					$image_size = $extra_args['width'];  // e.g. "thumb", "medium".
				}
			} else {
				$image_size = 'full';
			}

			$image = wp_get_attachment_image_src( $thumb, $image_size );
		}

		/**
		 * Filters featured images extracted from a WordPress Post.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $image      Extracted images. See {@link BP_Media_Extractor_Post::extract_images()} for format.
		 * @param string $richtext   Content to parse.
		 * @param string $plaintext  Copy of $richtext without any markup.
		 * @param array  $extra_args Bespoke data for a particular extractor.
		 */
		return apply_filters( 'bp_media_extractor_featured_images', $image, $richtext, $plaintext, $extra_args );
	}

	/**
	 * Sanitize and format raw content to prepare for content extraction.
	 *
	 * HTML tags and shortcodes are removed, and HTML entities are decoded.
	 *
	 * @since 2.3.0
	 *
	 * @param string $richtext
	 *
	 * @return string
	 */
	protected function strip_markup( $richtext ) {
		$plaintext = strip_shortcodes( html_entity_decode( strip_tags( $richtext ) ) );

		/**
		 * Filters the generated plain text version of the content passed to the extractor.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $plaintext Generated plain text.
		 * @param string $richtext  Original content.
		 */
		return apply_filters( 'bp_media_extractor_strip_markup', $plaintext, $richtext );
	}
}
