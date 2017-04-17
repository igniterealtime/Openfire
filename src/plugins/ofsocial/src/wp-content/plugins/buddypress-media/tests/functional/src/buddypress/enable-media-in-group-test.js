/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Enable Media in Group
 */

module.exports = {
  tags: ['buddypress', 'profile','upload'],
  'Step one :Enable media in group from settings' :function(browser){
    var data = browser.globals;
    browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_GROUP, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                          browser.verify.ok(result.value, 'Checkbox is selected');
                          console.log('check box is already enabled');
                  }else{
                          browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_GROUP);
                          browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
          },
    'step two: Check frontend ' : function (browser) {
            var data = browser.globals;
            browser
            .goToGroups()
            .click('#groups-list .is-member .item .item-title a')
            .assert.elementPresent('#rtmedia-media-nav')
            .click('#rtmedia-media-nav')
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
}
