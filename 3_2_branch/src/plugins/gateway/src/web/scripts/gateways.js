

/* 
togglePanel function
This is for showing and hiding the gateway options and permissions panels. 
This toggles toggles an individual panel (slides up and down), or switches 
between the two if one's already open.
*/

var lastID = "";

function togglePanel(thisID) {

    var activeLink = thisID.id+"Link";
    if (lastID != "" && lastID != thisID) {
        var oldLink = lastID.id+"Link";
        if ($(thisID).style.display != 'none' && $(lastID).style.display == 'none') {
            Effect.toggle($(thisID),'slide', {duration: .4});
            $(activeLink).className = "";
        } else if ($(thisID).style.display == 'none' && $(lastID).style.display != 'none') {
            Effect.toggle($(lastID),'slide', {duration: .4});
            $(oldLink).className = "";            
            Effect.toggle($(thisID),'slide', {duration: .4, delay: .5});
            $(activeLink).className = "jive-gatewayButtonOn";
        } else {
            Effect.toggle($(thisID),'slide', {duration: .4});
            $(activeLink).className = "jive-gatewayButtonOn";
        }
    }
    else {
        if ($(thisID).style.display != 'none') {
            Effect.toggle($(thisID),'slide', {duration: .4});
            $(activeLink).className = "";
        } else {
            Effect.toggle($(thisID),'slide', {duration: .4});
            $(activeLink).className = "jive-gatewayButtonOn";
        }
    }

    lastID = thisID;
}



/*
checkToggle function
this toggles the appearance and options for the gateways. When a user
unchecks a gateway the box goes grey panels aren't accessible.
*/
function checkToggle(theID) {

    var theCheckbox = theID.id+"checkbox";
    var testLink = theID.id+"testsLink";
    var optsLink = theID.id+"optionsLink";
    var permLink = theID.id+"permsLink";
    var testPanel = theID.id+"tests";
    var optsPanel = theID.id+"options";
    var permPanel = theID.id+"perms";

	if ($(theCheckbox).checked) {
		$(theID).className = "jive-gateway";
        $(testLink).style.display = 'block';
        $(optsLink).style.display = 'block';
		$(permLink).style.display = 'block';
	} else {
		$(theID).className = "jive-gateway jive-gatewayDisabled";
        $(testLink).style.display = 'none';
        $(optsLink).style.display = 'none';
		$(permLink).style.display = 'none';
		/* fix the panels so they roll up and the buttons go back to default states */
        $(testLink).className = "";
        $(optsLink).className = "";
		$(permLink).className = "";
		if ($(optsPanel).style.display != 'none') {
			Effect.toggle($(optsPanel), 'slide', {duration: .1});
		} else if ($(permPanel).style.display != 'none') {
			Effect.toggle($(permPanel), 'slide', {duration: .1});
        } else if ($(testPanel).style.display != 'none') {
            Effect.toggle($(testPanel), 'slide', {duration: .1});
        }

    }

}



/* 
toggleAdd function
This is the function that shows / hides the add registration form
*/
function toggleAdd(theID) {
    var jiveAddRegPanel = document.getElementById("jiveAddRegPanel");
    var jiveAddRegButton = document.getElementById("jiveAddRegButton");
    var jiveAddRegLink = document.getElementById("jiveAddRegLink");
    if ($(jiveAddRegPanel).style.display != 'none') {
		Effect.SlideUp($(jiveAddRegPanel), {duration: .2})
		$(jiveAddRegButton).className = "jive-gateway-addregBtn";
		$(jiveAddRegLink).innerHTML = "Add a new registration";
	} else if ($(jiveAddRegPanel).style.display == 'none') {
		Effect.SlideDown($(jiveAddRegPanel), {duration: .2})
		$(jiveAddRegButton).className = "jive-gateway-addregBtn jive-gateway-cancelAdd";
		$(jiveAddRegLink).innerHTML = "Cancel adding new registration";
	}
}



/* 
toggleEdit function
This is the function that shows / hides the edit fields for an existing registration
*/
function toggleEdit(theNum) {
    var normalRow = "jiveRegistration"+theNum;
    var editRow = "jiveRegistrationEdit"+theNum;
	if ($(editRow).style.display != 'none') {
		$(editRow).className = "jive-registrations-edit";
		$(editRow).style.display = 'none';
		$(normalRow).className = "jive-registrations-normal";
	} else if ($(editRow).style.display == 'none') {
		$(normalRow).className = "jive-registrations-normalHidden";
		$(editRow).className = "jive-registrations-editVisible";
		Effect.Appear($(editRow), {duration: .2});
	}
}
