/*
 @author: Prabuddha Chakraborty
 TestCase: Enable Media in Group Negative Case
 */
module.exports = {
  tags: ['buddypress', 'groups','upload'],
  'Step one :Disable media in group from settings' :function(browser){
    var data = browser.globals;
    browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_GROUP, "checked", function(result) {
                  if(result.value){
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_GROUP);
                    browser.click(data.SELECTORS.SUBMIT);
                  }else{
                    console.log('check box is already disabled');
                } })
            .pause(1000)
          },
  'step two: Check frontend ' : function (browser) {
      var data = browser.globals;
            browser
            .goToGroups()
            .click('#groups-list .is-member .item .item-title a')       //select a group
            .assert.elementNotPresent('#rtmedia-media-nav')
            .wplogout()
            .end();
  }
};
