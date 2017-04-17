/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Comment On Uploaded Media
*/
module.exports = {
  tags: ['display', 'comment'],
  'Step One : Enable Comment on Media ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.DISPLAY.DISPLAY)
          .pause(1000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.DISPLAY.ENABLE_COMMENT, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('Comment on uploaded media is already on');
                  }else{
                    browser.click(data.SELECTORS.DISPLAY.ENABLE_COMMENT);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
    },
    'step two: Check on Frontend ' : function (browser) {
            browser
            .goToActivity()
            .waitForElementVisible('body', 1500)
            .click('.acomment-reply')
            .pause(1000)
            .setValue('.ac-input','nice')
            .click('input[name="ac_form_submit"]')
            .pause(2000)
            .assert.containsText("#item-body .activity", "nice")
            .wplogout()
            .end();
        }
  };
