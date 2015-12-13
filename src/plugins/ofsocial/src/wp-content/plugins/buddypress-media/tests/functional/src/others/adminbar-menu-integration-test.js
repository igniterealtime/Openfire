/* @author: Prabuddha Chakraborty */

module.exports = {
  tags: ['others'],
  'Step One : Enable Admin bar menu integration from rtmedia settings ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.OTHERSETTINGS.OTHERSETTINGS)
          .pause(2000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.OTHERSETTINGS.SHOW_ADMIN_MENU, "checked", function(result) {
              if(result.value)
              {
                    browser.verify.ok(result.value, 'Admin bar menu integration Checkbox is already selected');
              }
              else
              {
                    browser.click(data.SELECTORS.OTHERSETTINGS.SHOW_ADMIN_MENU);
                    browser.click(data.SELECTORS.SUBMIT);
                    console.log('Admin bar menu integration is enabled')
              } })
            .pause(1000)
    },
    'step two: Checking on frontend ' : function (browser) {
            browser
            .assert.elementPresent("#wp-admin-bar-rtMedia > a")
            .wplogout()
            .end();
      }
};
