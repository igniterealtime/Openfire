/* @author: Prabuddha Chakraborty */

module.exports = {
  tags: ['others', 'menu','admin'],
  'Step One : Enable Admin bar menu integration from rtmedia settings ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.OTHERSETTINGS.OTHERSETTINGS)
          .pause(500)
          //Disable Admin bar integration checkbox switch
          .getAttribute(data.SELECTORS.OTHERSETTINGS.SHOW_ADMIN_MENU, "checked", function(result) {
                if(result.value)
                  {
                    browser.click(data.SELECTORS.OTHERSETTINGS.SHOW_ADMIN_MENU);
                    browser.click(data.SELECTORS.SUBMIT);
                  }
                  else
                  {
                    console.log('Admin bar menu integration was already OFF');
                  } })
            .pause(1000)
    },
    'step two: Checking on Frontend ' : function (browser) {
        browser
            .assert.elementNotPresent("#wp-admin-bar-rtMedia > a")
            .wplogout()
            .end();
      }
};
