/*
 @author: Prabuddha Chakraborty
 TestCase: Music Player in Activity Test
*/
module.exports = {
  tags: ['mediasize', 'activity','music'],
  'Step One : Set Music Activity Player Dimensions ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .pause(5000)
          .click(data.SELECTORS.MEDIASIZES.MEDIASIZES)
          .pause(2000)
          .clearValue(data.SELECTORS.MEDIASIZES.MUSIC_ACTIVITY_PLAYER_WIDTH)
          .setValue(data.SELECTORS.MEDIASIZES.MUSIC_ACTIVITY_PLAYER_WIDTH,'200') //set size:200
          .click(data.SELECTORS.SUBMIT)
    },
  'step two: Check on Frontend ' : function (browser) {
  var data = browser.globals;
        browser
            .goToActivity()
            .pause(5000)
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_MUSIC)) //enter only music file
            .setValue('#whats-new','testing for Music Player size in activity')
            .click('#aw-whats-new-submit')
            .pause(15000)
            .refresh()
            .getElementSize(".mejs-container", function(result) {
                  this.assert.equal(result.value.width, 200);
  })
            .wplogout()
            .end();
        }
};
