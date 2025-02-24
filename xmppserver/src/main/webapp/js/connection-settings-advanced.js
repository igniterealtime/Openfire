// Displays or hides the configuration blocks, based on the status of selected settings.
function applyDisplayable()
{
    let tlsConfigs, connectionConfigs, tlsDisplayValue, connectionDisplayValue, connectionEnabled, i, len;

    connectionEnabled = document.getElementById( "enabled" ).checked;

    connectionDisplayValue = (connectionEnabled ? "block" : "none");
    tlsDisplayValue = ( !connectionEnabled || document.getElementById( "tlspolicy-disabled" ).checked ? "none" : "block" );

    // Select the right configuration block and enable or disable it as defined by the corresponding checkbox.
    connectionConfigs = document.getElementsByClassName( "connectionConfig" );
    for ( i = 0, len = connectionConfigs.length; i < len; i++ )
    {
        // Hide or show the info block (as well as it's title, which is the previous sibling element)
        connectionConfigs[ i ].parentElement.style.display = connectionDisplayValue;
        connectionConfigs[ i ].parentElement.previousSibling.style.display = connectionDisplayValue;
    }

    // TLS configs depend on both 'connectionEnabled' and 'tlsDisplayValue'.
    tlsConfigs = document.getElementsByClassName( "tlsconfig" );
    for ( i = 0, len = tlsConfigs.length; i < len; i++ )
    {
        // Hide or show the info block (as well as it's title, which is the previous sibling element)
        tlsConfigs[ i ].parentElement.style.display = tlsDisplayValue;
        tlsConfigs[ i ].parentElement.previousSibling.style.display = tlsDisplayValue;
    }
}

// Marks all options in a select element as 'selected' (useful prior to form submission)
function selectAllOptions( selectedId )
{
    let select, i, len;

    select = document.getElementById( selectedId );

    for ( i = 0, len = select.options.length; i < len; i++ )
    {
        select.options[ i ].selected = true;
    }
}

// Moves selected option values from one select element to another.
function moveSelectedFromTo( from, to )
{
    let selected, i, len;

    selected = getSelectValues( document.getElementById( from ) );

    for ( i = 0, len = selected.length; i < len; i++ )
    {
        document.getElementById( to ).appendChild( selected[ i ] );
    }
}

// Return an array of the selected options. argument is an HTML select element
function getSelectValues( select )
{
    let i, len, result;

    result = [];

    for ( i = 0, len = select.options.length; i < len; i++ )
    {
        if ( select.options[ i ].selected )
        {
            result.push( select.options[ i ] );
        }
    }
    return result;
}

// Ensure that the various elements are set properly when the page is loaded.
window.onload = function()
{
    document.getElementById("enabled").addEventListener('click', applyDisplayable, false);
    document.getElementById("tlspolicy-disabled").addEventListener('click', applyDisplayable, false);
    document.getElementById("tlspolicy-optional").addEventListener('click', applyDisplayable, false);
    document.getElementById("tlspolicy-required").addEventListener('click', applyDisplayable, false);
    document.getElementById("moveToSupported").addEventListener('click', () => { moveSelectedFromTo('cipherSuitesEnabled','cipherSuitesSupported'); }, false);
    document.getElementById("moveToEnabled").addEventListener('click', () => { moveSelectedFromTo('cipherSuitesSupported','cipherSuitesEnabled'); }, false);
    document.getElementById("settingsForm").addEventListener('submit', () => { selectAllOptions('cipherSuitesEnabled'); }, false);

    applyDisplayable();
};
