/* @author: Prabuddha Chakraborty */


module.exports = {
  tags: ['privacy', 'publicuser'],
  'Step One : Enable Privacy from rtmedia settings ' : function (browser){

    var data = browser.globals;                                                     //fetch variables from constants.js

      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.PRIVACY.PRIVACY)                                                 //open privacy tab from rtmedia
          .pause(2000)
          .getAttribute(data.SELECTORS.PRIVACY.ENABLE_PRIVACY, "checked", function(result) {         //select Enable privacy feature

                  if(result.value)
                  {

                      browser.verify.ok(result.value, 'Checkbox is already selected');     //check if privacy is already enabled

                  }
                  else
                  {
                      browser.click(data.SELECTORS.PRIVACY.ENABLE_PRIVACY);                            //Select Enable privacy if already not selected
                      console.log('Privacy is enabled');
                  }
                })

                .getAttribute(data.SELECTORS.PRIVACY.PRIVACY_OVERRIDE, "checked", function(result) {         //select Enable privacy feature

                        if(result.value)
                        {

                            browser.verify.ok(result.value, 'Checkbox is already selected');     //check if privacy is already enabled

                        }
                        else
                        {
                            browser.click(data.SELECTORS.PRIVACY.PRIVACY_OVERRIDE);                            //Select Enable privacy if already not selected
                            console.log('Privacy override is enabled');
                        }
                      })

        .click(data.SELECTORS.PRIVACY.PUBLIC)                                                        //set privacy as public.
        .click(data.SELECTORS.SUBMIT)                                     //Submit to save
        .pause(1000)


        },



  'step two: Upload Media/Post in public privacy ' : function (browser) {
        browser
            .goToActivity()
            .setValue('#whats-new','test privacy for public')
            .click("#rtSelectPrivacy option[value='0']")
            .click('#aw-whats-new-submit')
            .pause(2000)

      /* assert for if post submited in both logged-in & logged-out mode */

            .getText("#activity-stream.activity-list.item-list > li.activity.activity_update.activity-item > div.activity-content > div.activity-inner p", function(result) {
                this.assert.equal(result.value, "test privacy for public");
                browser.wplogout();

                this.assert.equal(result.value, "test privacy for public");
                  })

            .end();

          }


    };
