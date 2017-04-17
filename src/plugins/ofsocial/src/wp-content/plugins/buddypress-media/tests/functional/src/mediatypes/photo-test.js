/*
 @author: Prabuddha Chakraborty
 TestCase: Photo Media Type Test
*/
module.exports = {
  tags: ['mediatypes', 'photo','upload'],
  'Step One : Enable Photo Types From Settings ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click('#tab-rtmedia-bp')
          .pause(2000)
          //select checkbox switch
          .getAttribute("#rtmedia-bp-enable-activity", "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('check box is already enabled');
                  }else{
                    browser.click('#rtmedia-bp-enable-activity');
                    browser.click(data.SELECTORS.SUBMIT);
                } })
    /*
       'Allow Upload From Activity Stream' is switched  on
        code here ..
          */
          .pause(1000)
          .click(data.SELECTORS.MEDIATYPES.MEDIATYPES)
          .getAttribute(data.SELECTORS.MEDIATYPES.ENABLE_PHOTO, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('Photo check box is already enabled');
                  }else{
                    browser.click(data.SELECTORS.MEDIATYPES.ENABLE_PHOTO);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
          },
    'step two: Check on Frontend ' : function (browser) {
      var data = browser.globals;
        browser
            .goToActivity()
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
            .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
            .click('#aw-whats-new-submit')
            .assert.containsText("#buddypress", "test")
            .wplogout()
            .end();
        }
  };
