/*
 @author: Prabuddha Chakraborty
 TestCase: Photo Dimension Test
*/

module.exports = {
  tags: ['mediasize', 'photo','upload'],
  'Step One : Enable thumbnail settings from backened ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.MEDIASIZES.MEDIASIZES)
          .pause(2000)
          .clearValue(data.SELECTORS.MEDIASIZES.PHOTO_THUMBNAIL_WIDTH)
          .setValue(data.SELECTORS.MEDIASIZES.PHOTO_THUMBNAIL_WIDTH,'150') //set width size:150
          .clearValue(data.SELECTORS.MEDIASIZES.PHOTO_THUMBNAIL_HEIGHT)
          .setValue(data.SELECTORS.MEDIASIZES.PHOTO_THUMBNAIL_HEIGHT,'150') //set height size:150
          .click(data.SELECTORS.SUBMIT)
          .pause(1000)
    },

          'step two: Check on Frontend ' : function (browser) {
            var data = browser.globals;
            browser
            .goToMedia()
            .click('#rtmedia-nav-item-photo')
            .waitForElementVisible('body', 1500)
            .click('#rtm_show_upload_ui')
            .click('.rtm-select-files')
            .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
            .click('.start-media-upload')
            .pause(7000)
            .refresh()
    //get the height of uploaded photo thumbnail.
            .getElementSize(".rtmedia-item-thumbnail img", function(result) {
                this.assert.equal(result.value.height, 150)
                this.assert.equal(result.value.width, 150);
          })
            .wplogout()
            .end();
          }
  };
