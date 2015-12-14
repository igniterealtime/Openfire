/*
 @author: Prabuddha Chakraborty
 TestCase: Enable Media in Profile Negative Case
 */

module.exports = {
  tags: ['buddypress', 'profile','upload'],
  'Step One : Enable media in profile  ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE);
                    browser.click(data.SELECTORS.SUBMIT);
                  }else{
                    console.log('check box is already OFF');
                } })
            .pause(1000)
          },
  'step two: Upload and Check Media ' : function (browser) {
            browser
            .assert.elementNotPresent('#wp-admin-bar-my-account-media.menupop a.ab-item')
            .goToProfile()
            .assert.elementNotPresent("#user-media")
            .wplogout()
            .end();
        }
  };
