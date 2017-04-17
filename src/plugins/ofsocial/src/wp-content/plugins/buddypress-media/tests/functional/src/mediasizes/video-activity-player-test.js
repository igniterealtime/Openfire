/*
 @author: Prabuddha Chakraborty
 TestCase: Video Activity Player Test
*/
module.exports = {
  tags: ['mediasize', 'videos','upload'],
  'Step One : Set dimensions from Backened ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.MEDIASIZES.MEDIASIZES)
          .pause(2000)
          .clearValue(data.SELECTORS.MEDIASIZES.VIDEO_ACTIVITY_PLAYER_WIDTH)
          .setValue(data.SELECTORS.MEDIASIZES.VIDEO_ACTIVITY_PLAYER_WIDTH,'300') //set width size:300
          .clearValue(data.SELECTORS.MEDIASIZES.VIDEO_ACTIVITY_PLAYER_HEIGHT)
          .setValue(data.SELECTORS.MEDIASIZES.VIDEO_ACTIVITY_PLAYER_HEIGHT,'250')
          .click(data.SELECTORS.SUBMIT)
          .pause(1000)
          .click(data.SELECTORS.DISPLAY.DISPLAY)
          .getAttribute(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.click(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX);
                    browser.click(data.SELECTORS.SUBMIT);
                }else{
                  console.log('Light box is already disabled');
              } })
            .pause(1000)
          },
    'step two: Check on Frontend ' : function (browser) {
        var data = browser.globals;
        browser
            .goToActivity()
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_VIDEO))
            .setValue('#whats-new','Test video upload')
            .click('#aw-whats-new-submit')
            .pause(20000)
            .refresh()
            .getElementSize(".rtmedia-item-thumbnail", function(result) {
                  this.assert.equal(result.value.width, 300);
                  this.assert.equal(result.value.height, 250);
        })
           .wplogout()
           .end();
        }
};
