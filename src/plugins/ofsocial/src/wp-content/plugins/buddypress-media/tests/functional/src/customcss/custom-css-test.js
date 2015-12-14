/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Custom CSS Settings
*/

module.exports = {
  tags: ['customcss'],
  'Step: Add custom css in rtmedia settings and Verify on FRONTEND' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS)
          .getAttribute(data.SELECTORS.CUSTOMCSS.DEFAULT_ENABLE, "checked", function(result) {
                if(result.value)
                  {
                    browser.click(data.SELECTORS.CUSTOMCSS.DEFAULT_ENABLE);
                    console.log('CUSTOM CSS Checkbox is disabled');
                  }
                  else
                  {
                    console.log('CUSTOM CSS is already disabled');
                } })
          .pause(1000)
          .click(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS_TEXTAREA)
          .clearValue(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS_TEXTAREA)
          .setValue(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS_TEXTAREA,"#buddypress #whats-new { height: 500px !important; overflow: hidden;")
          .pause(200)
          .click(data.SELECTORS.SUBMIT)
          /* move to Activity page ..and verify changes */
          .moveToElement('#wp-admin-bar-my-account > a.ab-item',5,5)
          .pause(500)
          .click('#wp-admin-bar-my-account-activity a.ab-item')
          .getElementSize("#buddypress #whats-new", function(result) {
                  this.assert.equal(result.value.height, 500);
                  console.log(result.value.height);
          })
        /*Restore to the old settings below*/
          .openrtMediaSettings()
          .click(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS)
          .click(data.SELECTORS.CUSTOMCSS.DEFAULT_ENABLE)
          .clearValue(data.SELECTORS.CUSTOMCSS.CUSTOM_CSS_TEXTAREA)
          .click(data.SELECTORS.SUBMIT,function(){
                      console.log("Restored to the old settings");
                          })
          .wplogout()
          .end();


  }




};
