// Displays or hides the configuration block for a particular connection type, based on the status of the
// 'enable' checkbox for that connection type.
function applyDisplayable( connectionType )
{
    let configBlock, enabled;

    // Select the right configuration block and enable or disable it as defined by the corresponding checkbox.
    configBlock = document.getElementById( connectionType + "-config" );
    enabled     = document.getElementById( connectionType + "-enabled" ).checked;

    if ( ( configBlock != null ) && ( enabled != null ) )
    {
        if ( enabled )
        {
            configBlock.style.display = "block";
        }
        else
        {
            configBlock.style.display = "none";
        }
    }
}

// Ensure that the various elements are set properly when the page is loaded.
window.onload = function()
{
    document.getElementById("plaintext-enabled").addEventListener('click', () => { applyDisplayable( "plaintext" ); }, false);
    document.getElementById("directtls-enabled").addEventListener('click', () => { applyDisplayable( "directtls" ); }, false);
    applyDisplayable( "plaintext" );
    applyDisplayable( "directtls" );
};
