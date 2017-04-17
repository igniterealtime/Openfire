/*
@author : Prabuddha Chakraborty
This is a standalone scripts for centralized management of all selectors and all other constant.
*/

module.exports ={
  //username and password for each wordpress roles
    TESTADMINUSERNAME: 'ADMINUSER',
    TESTADMINPASSWORD: 'ADMINPASS',

    TESTEDITORUSERNAME: 'EDITORUSER',
    TESTEDITORPASSWORD: 'EDITORPASS',

    TESTAUTHORUSERNAME: 'AUTHORUSER',
    TESTAUTHORPASSWORD: 'AUTHORPASS',

    TESTCONTRIBUTORUSERNAME: 'CONTRIBUTORUSER',
    TESTCONTRIBUTORPASSWORD: 'CONTRIBUTORPASS',

    TESTSUBSCRIBERUSERNAME: 'SUBSCRIBERUSER',
    TESTSUBSCRIBERPASSWORD: 'SUBSCRIBERPASS',
  //Home site url enter here
   URLS: {
        LOGIN: 'http://wp.localtest.me'
   },
  //local path of all test_data resources
   PATH: {
          TEST_IMAGE: './test-data/images/test.jpg',
          TEST_IMAGE2: './test-data/images/test0.jpg',
          TEST_MUSIC: './test-data/music/mpthreetest.mp3',
          TEST_VIDEO: './test-data/videos/testmpfour.mp4',
          TEST_DOC:  './test-data/document.doc',
          TEST_ZIP:  './test-data/testzip.zip',
   },
/*SELECTORS of rtMedia settings here .. */
  SELECTORS: {
          SUBMIT: '#bp_media_settings_form .bottom .rtmedia-settings-submit',           //data.SELECTORS.SUBMIT
          BUDDYPRESS: {
            BUDDYPRESS: '#tab-rtmedia-bp',
            ENABLE_MEDIA_PROFILE: 'input[name="rtmedia-options[buddypress_enableOnProfile]"]',
            ENABLE_MEDIA_GROUP:    'input[name="rtmedia-options[buddypress_enableOnGroup]"]',
            ENABLE_UPLOAD_ACTIVITY: 'input[name="rtmedia-options[buddypress_enableOnActivity]"]',
            ENABLE_MEDIA_ALBUM: 'input[name="rtmedia-options[general_enableAlbums]"]'
          },

          CUSTOMCSS: {
            CUSTOM_CSS: "#tab-rtmedia-custom-css-settings",        //'input[name="rtmedia-options[styles_custom]"]'
            DEFAULT_ENABLE: 'input[name="rtmedia-options[styles_enabled]"]',
            CUSTOM_CSS_TEXTAREA: "#rtmedia-custom-css"             //rtmedia-options[styles_custom]
          },

          DISPLAY: {
            DISPLAY: '#tab-rtmedia-display',
            ENABLE_COMMENT: 'input[name="rtmedia-options[general_enableComments]"]',
            ENABLE_LIGHTBOX: 'input[name="rtmedia-options[general_enableLightbox]"]',
            MEDIA_PER_PAGE: 'input[name="rtmedia-options[general_perPageMedia]"]',
            SELECT_LOADMORE: 'input[value="load_more"]',
            SELECT_PAGINATION: 'input[value="pagination"]'
          },

          MEDIASIZES: {
            MEDIASIZES: '#tab-rtmedia-sizes',
            MUSIC_ACTIVITY_PLAYER_WIDTH: 'input[name="rtmedia-options[defaultSizes_music_activityPlayer_width]"]',
            MUSIC_SINGLEPLAYER_WIDTH: 'input[name="rtmedia-options[defaultSizes_music_singlePlayer_width]"]',
            PHOTO_LARGE_WIDTH: 'input[name="rtmedia-options[defaultSizes_photo_large_width]"]',
            PHOTO_LARGE_HEIGHT: 'input[name="rtmedia-options[defaultSizes_photo_large_height]"]',
            PHOTO_MEDIUM_WIDTH: 'input[name="rtmedia-options[defaultSizes_photo_medium_width]"]',
            PHOTO_MEDIUM_HEIGHT: 'input[name="rtmedia-options[defaultSizes_photo_medium_height]"]',
            PHOTO_THUMBNAIL_HEIGHT: 'input[name="rtmedia-options[defaultSizes_photo_thumbnail_height]"]' ,
            PHOTO_THUMBNAIL_WIDTH: 'input[name="rtmedia-options[defaultSizes_photo_thumbnail_width]"]',
            VIDEO_ACTIVITY_PLAYER_WIDTH: 'input[name="rtmedia-options[defaultSizes_video_activityPlayer_width]"]',
            VIDEO_ACTIVITY_PLAYER_HEIGHT: 'input[name="rtmedia-options[defaultSizes_video_activityPlayer_height]"]',
            VIDEO_SINGLE_PLAYER_WIDTH: 'input[name="rtmedia-options[defaultSizes_video_singlePlayer_width]"]',
            VIDEO_SINGLE_PLAYER_HEIGHT: 'input[name="rtmedia-options[defaultSizes_video_singlePlayer_height]"]'
          },

          MEDIATYPES: {
            MEDIATYPES: '#tab-rtmedia-types',
            ENABLE_PHOTO: 'input[name="rtmedia-options[allowedTypes_photo_enabled]"]',
            ENABLE_MUSIC: 'input[name="rtmedia-options[allowedTypes_music_enabled]"]',
            ENABLE_VIDEO: 'input[name="rtmedia-options[allowedTypes_video_enabled]"]'
          },

          OTHERSETTINGS: {
            OTHERSETTINGS: '#tab-rtmedia-general',
            SHOW_ADMIN_MENU: 'input[name="rtmedia-options[general_showAdminMenu]"]',
            ADD_FOOTER_LINK: 'input[name="rtmedia-options[rtmedia_add_linkback]"]'
          },

          PRIVACY: {
            PRIVACY: "#tab-rtmedia-privacy",
            ENABLE_PRIVACY: 'input[name="rtmedia-options[privacy_enabled]"]',
            PRIVATE: 'input[value="60"]',
            LOGGEDIN: 'input[value="20"]',
            PUBLIC: 'input[value="0"]',
            PRIVACY_OVERRIDE: 'input[name="rtmedia-options[privacy_userOverride]"]'
          }
  }
};
