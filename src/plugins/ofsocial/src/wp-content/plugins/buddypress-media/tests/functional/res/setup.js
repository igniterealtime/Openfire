/*
This is setup scripts.. creating wordpress users for newly created site
to uploading initial images this scripts does it all
This scripts runs only for first time
*/
module.exports = {
  'Step One : initializing setup file ... ' : function (browser){
    var data = browser.globals;
    var urll = data.URLS.LOGIN;
    var settings = urll + '/wp-admin/options-general.php?page=bp-components';
    var adduser = urll + "/wp-admin/user-new.php";
      browser
          .maximizeWindow()
          .wplogin(urll,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .getTitle(function(title) {
              console.log(title);
		        })
          .url(adduser)
          .waitForElementVisible('body', 2500)
          .getTitle(function(title) {
            console.log(title);
            console.log("creating users for various roles...");
		         })

             //test editor
          .url(adduser)
          .waitForElementVisible('input[id="user_login"]', 2500)
          .setValue('input[id="user_login"]',data.TESTEDITORUSERNAME)
          .setValue('input[id="email"]',"abc1@cc.in")
          .setValue('input[id="first_name"]',data.TESTEDITORUSERNAME)
          .setValue('input[id="pass1"]',data.TESTEDITORPASSWORD)
          .setValue('input[id="pass2"]',data.TESTEDITORPASSWORD)
          .click("#role option[value='editor']")
          .click('#createusersub')
          .waitForElementVisible('body', 2500)
          //test author
          .url(adduser)
          .waitForElementVisible('input[id="user_login"]', 2500)
          .setValue('input[id="user_login"]',data.TESTAUTHORUSERNAME)
          .setValue('input[id="email"]',"abc2@cc.in")
          .setValue('input[id="first_name"]',data.TESTAUTHORUSERNAME)
          .setValue('input[id="pass1"]',data.TESTAUTHORPASSWORD)
          .setValue('input[id="pass2"]',data.TESTAUTHORPASSWORD)
          .click("#role option[value='author']")
          .click('#createusersub')
          .pause(200)
          //test subscriber
          .url(adduser)
          .waitForElementVisible('input[id="user_login"]', 2500)
          .setValue('input[id="user_login"]',data.TESTSUBSCRIBERUSERNAME)
          .setValue('input[id="email"]',"abc3@cc.in")
          .setValue('input[id="first_name"]',data.TESTSUBSCRIBERUSERNAME)
          .setValue('input[id="pass1"]',data.TESTSUBSCRIBERPASSWORD)
          .setValue('input[id="pass2"]',data.TESTSUBSCRIBERPASSWORD)
          .click("#role option[value='subscriber']")
          .click('#createusersub')
          .pause(200)
        //test contributor
          .url(adduser)
          .waitForElementVisible('input[id="user_login"]', 2500)
          .setValue('input[id="user_login"]',data.TESTCONTRIBUTORUSERNAME)
          .setValue('input[id="email"]',"abc4@cc.in")
          .setValue('input[id="first_name"]',data.TESTCONTRIBUTORUSERNAME)
          .setValue('input[id="pass1"]',data.TESTCONTRIBUTORPASSWORD)
          .setValue('input[id="pass2"]',data.TESTCONTRIBUTORPASSWORD)
          .click("#role option[value='contributor']")
          .click('#createusersub')
          .pause(200)
        //upload media to medias
          .goToMedia()
          .getTitle(function(title) {
            console.log(title);
            console.log("uploading media photos for initial setup..")
            console.log("total of 13 test photos will be uploaded in media galery")
                for( var i = 0; i < 12 ; i++ ) {
                    console.log("uploaded !");
                    browser.click('#rtmedia-nav-item-music')
                          .waitForElementVisible('body', 1500)
                          .click('#rtm_show_upload_ui')
                          .click('.rtm-select-files')
                          .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
                          .click('.start-media-upload')
                          .pause(4000);
        };
})

  //Activate groups for varios roles
          .url(settings)
          .click('input[name="bp_components[groups]"]')
          .click('#bp-admin-component-submit')

    //creating demo groups
          .url(urll + '/groups/create/step/group-details/')
          .pause(200)
          .setValue('#group-name','test')
          .setValue('#group-desc','creating test')
          .click('#group-creation-create')
          .pause(200)
          .click('#group-creation-next')
          .click('#bbp-create-group-forum')
          .click('#group-creation-next')
          .pause(200)
          .click('#group-creation-finish')
          .pause(200)
          .wplogout()

   //login for others
          .wplogin(urll,data.TESTAUTHORUSERNAME,data.TESTAUTHORPASSWORD)
          .url(urll + '/groups/test/')
          .getTitle(function(title) {
              console.log("TestAuthor is joining group..")
            })
          .click('a[title="Join Group"]')
          .wplogout()

  //login for others
          .wplogin(urll,data.TESTEDITORUSERNAME,data.TESTEDITORPASSWORD)
          .getTitle(function(title) {
            console.log("TestEditor is joining group..")
          })
          .url(urll + '/groups/test/')
          .click('a[title="Join Group"]')
          .wplogout()

 //login for others
          .wplogin(urll,data.TESTCONTRIBUTORUSERNAME,data.TESTCONTRIBUTORPASSWORD)
          .getTitle(function(title) {
                console.log("TestContributor is joining group..")
              })
          .url(urll + '/groups/test/')
          .click('a[title="Join Group"]')
          .wplogout()

  //login for others
          .wplogin(urll,data.TESTSUBSCRIBERUSERNAME,data.TESTSUBSCRIBERPASSWORD)
          .getTitle(function(title) {
              console.log("TestEditor is joining group..")
            })
          .url(urll + '/groups/test/')
          .click('a[title="Join Group"]')
          .wplogout()
          .end();
      }
};
