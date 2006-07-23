

/* 
togglePanel function
This is for showing and hiding the gateway options and permissions panels. 
This toggles toggles an individual panel (slides up and down), or switches 
between the two if one's already open.
*/

function togglePanel(thisID,thatID) {

activeLink = thisID.id+"Link";
oldLink = thatID.id+"Link";

	if ($(thisID).style.display != 'none' && $(thatID).style.display == 'none') {
		Effect.toggle($(thisID),'slide', {duration: .4});
		$(activeLink).className = "";
	} else if ($(thisID).style.display == 'none' && $(thatID).style.display != 'none') {
		$(thatID).style.display = 'none';
		$(thisID).style.display = 'block';
		$(oldLink).className = "";
		$(activeLink).className = "jive-gatewayButtonOn";
	} else {
		Effect.toggle($(thisID),'slide', {duration: .4});
		$(activeLink).className = "jive-gatewayButtonOn";
	}
}



/*
checkToggle function
this toggles the appearance and options for the gateways. When a user
unchecks a gateway the box goes grey panels aren't accessible.
*/
function checkToggle(theID) {

theCheckbox = theID.id+"checkbox";
optsLink = theID.id+"optionsLink";
permLink = theID.id+"permsLink";
optsPanel = theID.id+"options";
permPanel = theID.id+"perms";

	if ($(theCheckbox).checked) {
		$(theID).className = "jive-gateway";
		$(optsLink).style.display = 'block';
		$(permLink).style.display = 'block';
		/* the below doesn't work right in IE, work on later */
		//$(optsLink).setAttribute('onclick',"togglePanel($(optsPanel),$(permPanel))");
		//$(permLink).setAttribute('onclick',"togglePanel($(permPanel),$(optsPanel))");
	} else {
		$(theID).className = "jive-gateway jive-gatewayDisabled";
		/* the below doesn't work right in IE, work on later */
		//$(optsLink).removeAttribute('onclick');
		//$(permLink).removeAttribute('onclick');
		$(optsLink).style.display = 'none';
		$(permLink).style.display = 'none';
		/* fix the panels so they roll up and the buttons go back to default states */
		$(optsLink).className = "";
		$(permLink).className = "";
		if ($(optsPanel).style.display != 'none') {
			Effect.toggle($(optsPanel), 'slide', {duration: .1});
		} else if ($(permPanel).style.display != 'none') {
			Effect.toggle($(permPanel), 'slide', {duration: .1});
		}
	
	}

}



/* 
toggleAdd function
This is the function that shows / hides the add registration form
*/
function toggleAdd(theID) {
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
normalRow = "jiveRegistration"+theNum;
editRow = "jiveRegistrationEdit"+theNum;
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



/* 
toggleFilters function
this is for a future feature, to replace the row of filter options with a
dynamic pulldown menu.
*/

function toggleFilters() {
	if ($(jiveFilterDrop).style.display != 'none') {
		Effect.toggle($(jiveFilterDrop),'slide', {duration: .4});
	} else {
		Effect.toggle($(jiveFilterDrop),'slide', {duration: .4});
	}
}


/*
toggleGW function
this performs the actual work for enabling or disabling the gateway in
question.
*/
function toggleGW(gwType,gwSetting) {
	if (document.getElementById(gwSetting)) {
		var url = 'gateway-setting-handler.jsp?gwType=' + gwType + '&gwEnabled=';
		if (document.getElementById(gwSetting).checked) {
			url = url + "true";
		}
		else {
			url = url + "false";
		}
		if (window.XMLHttpRequest) {
			req = new XMLHttpRequest();
		} else if (window.ActiveXObject) {
			req = new ActiveXObject("Microsoft.XMLHTTP");
		}
		req.open("GET", url, true);
		req.send(null);
	}
}
