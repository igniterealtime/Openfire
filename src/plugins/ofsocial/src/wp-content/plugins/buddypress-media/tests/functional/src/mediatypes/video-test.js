/*
 @author: Prabuddha Chakraborty
 TestCase: Video Media Type Test

*/
module.exports = {
  tags: ['mediatypes', 'video','upload'],
  'Step One : Enable Video Types ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click('#tab-rtmedia-bp')
          .pause(2000)
          /*
          'Allow Upload From Activity Stream' is switched  on
          code here ..
          */
          //select checkbox switch
          .getAttribute('#rtmedia-bp-enable-activity', "checked", function(result) {
                if(result.value)
                  {
                    browser.verify.ok(result.value, 'Activity Checkbox is already selected');
                  }
                  else
                  {
                    console.log('enabling activity checkbox')
                    browser.click('#rtmedia-bp-enable-activity');
                    browser.click(data.SELECTORS.SUBMIT);
                } })
          .pause(1000)
          .click(data.SELECTORS.MEDIATYPES.MEDIATYPES)
          .getAttribute(data.SELECTORS.MEDIATYPES.ENABLE_VIDEO, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('Photo check box is already enabled');
                  }else{
                    browser.click(data.SELECTORS.MEDIATYPES.ENABLE_VIDEO);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
    },
    'step two: Check on Frontend ' : function (browser) {
    var data = browser.globals;
        browser
            .goToActivity()
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_VIDEO))
            .setValue('#whats-new','Check Videos ')
            .click('#aw-whats-new-submit')
            .assert.containsText("#buddypress", "testmpfour")
            .wplogout()
            .end();
    }
};
