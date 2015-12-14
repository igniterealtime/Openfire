/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Organise Media In album Negative Case
 */
module.exports = {
  tags: ['buddypress', 'album'],
  'Step One : Enable media in profile  ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_ALBUM, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_ALBUM);
                    browser.click(data.SELECTORS.SUBMIT);
                  }else{
                    console.log('check box is already enabled');
                } })
            .pause(1000)
          },
      'step two: Check if Album Exist ' : function (browser) {
            browser
            .goToProfile()
            .click('#user-media')
            .assert.elementNotPresent("#rtmedia-nav-item-albums")
            .wplogout()
            .end();
        }
};
