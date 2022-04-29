/*
togglePanel function
This is for showing and hiding the advanced options panel.
This toggles an individual panel (slides up and down).
*/

function togglePanel(panel)
{
    const activeLink = document.getElementById(panel.id+"Link");
    if (panel.style.display === 'block') {
        panel.style.display = 'none';
        activeLink.className = 'jiveAdvancedButtonOn';
    } else {
        panel.style.display = 'block';
        activeLink.className = '';
    }
}
