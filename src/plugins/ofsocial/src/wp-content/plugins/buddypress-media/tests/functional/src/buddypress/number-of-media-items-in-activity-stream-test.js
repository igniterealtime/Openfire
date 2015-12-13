module.exports = {
  tags: ['buddypress', 'activity','upload'],
  'Step One : backened ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(500)
          .clearValue('input[name="rtmedia-options[buddypress_limitOnActivity]"]')
					.setValue('input[name="rtmedia-options[buddypress_limitOnActivity]"]',1)
          //disable checkbox switch
          .getAttribute('input[name="rtmedia-options[buddypress_limitOnActivity]"]', "checked", function(result) {
                if(result.value){
									console.log('check box is already enabled');
                  }else{
									browser.click('input[name="rtmedia-options[buddypress_limitOnActivity]"]');
            }
            })
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY, "checked", function(result) {
                    if(result.value){
                      browser.verify.ok(result.value, 'Checkbox is selected');
                      console.log('check box is already enabled');
                    }else{
                      browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_UPLOAD_ACTIVITY);
                  } })
            .click(data.SELECTORS.SUBMIT)
            .pause(1000)
},
    'step two: Check on ACTIVITY For Post upload button ' : function (browser) {
						var data = browser.globals;
            browser
            .goToActivity()
            .assert.elementPresent("#rtmedia-add-media-button-post-update")
            .setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
            .setValue('#whats-new','Uploading 2 media files but only one should be visible ')
						.setValue('#rtmedia-whts-new-upload-container input[type="file"]', require('path').resolve(data.PATH.TEST_IMAGE))
						.click('#aw-whats-new-submit')
            .refresh()
            .assert.elementNotPresent('img[alt="test0"]')
            .assert.elementPresent('img[alt="test"]')
            .end();
				}
};
