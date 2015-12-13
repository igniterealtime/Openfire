/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Enable Media in Profile
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
          .pause(2000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                          browser.verify.ok(result.value, 'Checkbox is selected');
                          console.log('check box is already enabled');
                  }else{
                          browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE);
                          browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
    },
      'step two: Upload and Check Media ' : function (browser) {
            var data = browser.globals;
            browser
            .goToProfile()
            .click('#user-media')
            .click('#rtm_show_upload_ui')
            .click('.rtm-select-files')
            .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
            .click('.start-media-upload')
            .pause(6000)
            .refresh()
            .getText('.rtmedia-list-item a.rtmedia-list-item-a .rtmedia-item-title h4',function(result){
                browser.assert.equal(result.value, 'test', 'image uploaded successfully');
               })
            .wplogout()
            .end();
  }
};
