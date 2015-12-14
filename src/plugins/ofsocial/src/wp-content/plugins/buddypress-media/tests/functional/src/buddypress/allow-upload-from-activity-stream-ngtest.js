/*
 @author: Prabuddha Chakraborty
 TestCase: Allow upload from activity stream Negative Case
*/
module.exports = {
  tags: ['buddypress', 'activity','upload'],
  'Step One : Disable Allow upload from activity stream ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY, "checked", function(result) {
                if(result.value){
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY);
                    browser.click(data.SELECTORS.SUBMIT);
                  }else{
                  console.log('check box is already disabled');
            } })
          .pause(1000)
    },
    'Step two: Check on ACTIVITY For Post upload button ' : function (browser) {
            browser
            .goToActivity()
            .assert.elementNotPresent("#rtmedia-add-media-button-post-update")
            .wplogout()
            .end();
      }
};
