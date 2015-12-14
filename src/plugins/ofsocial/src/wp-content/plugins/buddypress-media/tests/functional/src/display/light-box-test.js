/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Lightbox Feature
*/
module.exports = {
  tags: ['display', 'lightbox'],
  'Step One : Enable Allow upload from activity stream ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.DISPLAY.DISPLAY)
          .pause(1000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX, "checked", function(result) {
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('Light box is already on');
                  }else{
                    browser.click(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
      },
    'step two: Check on Frontend ' : function (browser) {
            browser
            .goToMedia()
            .click('div.rtmedia-item-thumbnail img')
            .pause(1000)
            .assert.elementPresent('.rtmedia-media')
            .wplogout()
            .end();
        }
};
