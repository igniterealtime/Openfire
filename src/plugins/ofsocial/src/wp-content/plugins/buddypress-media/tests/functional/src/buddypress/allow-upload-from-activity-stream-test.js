/*
 @author: Prabuddha Chakraborty
 TestCase: Check Allow upload from activity stream
 */
module.exports = {
  tags: ['buddypress', 'activity','upload'],
  'Step One : Enable Allow upload from activity stream ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY, "checked", function(result) {
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('check box is already enabled');
                  }else{
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
          //.pause(1000)
      },
  'Step two: Check on Activity Page ' : function (browser) {
            var data = browser.globals;
            browser
            .goToActivity()
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
            .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
            .click('#aw-whats-new-submit')
            .assert.containsText("#buddypress", "test")
            .end();
        }
  };
