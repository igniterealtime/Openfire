/*
 @author: Prabuddha Chakraborty
 TestCase: Photo Dimension Test
*/

module.exports = {
  tags: ['mediasize', 'photo','upload'],
  'Step One : Enable from rtmedia settings  ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.MEDIASIZES.MEDIASIZES)
          .pause(2000)
          .clearValue(data.SELECTORS.MEDIASIZES.PHOTO_MEDIUM_WIDTH)
          .setValue(data.SELECTORS.MEDIASIZES.PHOTO_MEDIUM_WIDTH,'100') //set width size:100
          .clearValue(data.SELECTORS.MEDIASIZES.PHOTO_MEDIUM_HEIGHT)
          .setValue(data.SELECTORS.MEDIASIZES.PHOTO_MEDIUM_HEIGHT,'100') //set height size:100
          .click(data.SELECTORS.SUBMIT)
          .pause(1000)
  },
    'step two: Check on Frontend ' : function (browser) {
    var data = browser.globals;
        browser
          .goToActivity()
          .assert.elementPresent("#rtmedia-add-media-button-post-update")
          .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
          .setValue('#whats-new','testing for image size in activity')
          .click('#aw-whats-new-submit')
          .refresh()
          .pause(1000)
          .getElementSize(".rtmedia-item-thumbnail img", function(result) {
                this.assert.equal(result.value.height, 100)
                this.assert.equal(result.value.width, 100);
            })
           .wplogout()
           .end();
        }
};
