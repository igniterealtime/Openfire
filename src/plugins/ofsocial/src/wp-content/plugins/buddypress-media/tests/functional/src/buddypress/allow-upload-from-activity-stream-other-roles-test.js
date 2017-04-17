/*
 @author: Prabuddha Chakraborty
 TestCase: Check Allow upload from activity stream for other users
*/
module.exports = {
  tags: ['buddypress', 'activity','upload'],
  'Step One : Enable Allow upload from activity stream from Admin' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(2000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY, "checked", function(result) {
                  if(result.value){
                    browser.verify.ok(result.value, 'Checkbox is selected');
                    console.log('check box is already enabled');
                  }else{
                    browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY);
                    browser.click(data.SELECTORS.SUBMIT);
                } })
          .pause(1000)
          .wplogout()


      },
  'Step two: Login from TestEditor' : function (browser) {
    var data = browser.globals;
          browser
          .wplogin(data.URLS.LOGIN,data.TESTEDITORUSERNAME,data.TESTEDITORPASSWORD)
          .goToActivity()
          .assert.elementPresent("#rtmedia-add-media-button-post-update")
          .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
          .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
          .click('#aw-whats-new-submit')
          .assert.containsText("#buddypress", "test")
          .wplogout()
      },
  'Step three: Login from Author' : function (browser) {
    var data = browser.globals;
        browser
        .wplogin(data.URLS.LOGIN,data.TESTAUTHORUSERNAME,data.TESTAUTHORPASSWORD)
        .goToActivity()
        .assert.elementPresent("#rtmedia-add-media-button-post-update")
        .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
        .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
        .click('#aw-whats-new-submit')
        .assert.containsText("#buddypress", "test")
        .wplogout()
    },
  'Step four: Login from Subscriber' : function (browser) {
    var data = browser.globals;
      browser
      .wplogin(data.URLS.LOGIN,data.TESTSUBSCRIBERUSERNAME,data.TESTSUBSCRIBERPASSWORD)
      .goToActivity()
      .assert.elementPresent("#rtmedia-add-media-button-post-update")
      .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
      .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
      .click('#aw-whats-new-submit')
      .assert.containsText("#buddypress", "test")
     .wplogout()
  },
 'Step five: Login from Contributor' : function (browser) {
    var data = browser.globals;
    browser
    .wplogin(data.URLS.LOGIN,data.TESTCONTRIBUTORUSERNAME,data.TESTCONTRIBUTORPASSWORD)
    .goToActivity()
    .assert.elementPresent("#rtmedia-add-media-button-post-update")
    .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
    .setValue('#whats-new','Check  Media Type : Photos (jpg, jpeg, png, gif) ')
    .click('#aw-whats-new-submit')
    .assert.containsText("#buddypress", "test")
    .wplogout()
    .end();
}
};
