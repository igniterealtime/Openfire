/*
togglePanel function
This is for showing and hiding the advanced options panel.
This toggles toggles an individual panel (slides up and down).
*/

function togglePanel(thisID) {

let activeLink = thisID.id+"Link";

    if ($(thisID).style.display != 'none') {
        Effect.toggle($(thisID),'slide', {duration: .4});
        $(activeLink).className = "";
    } else {
        Effect.toggle($(thisID),'slide', {duration: .4});
        $(activeLink).className = "jiveAdvancedButtonOn";
    }
}
