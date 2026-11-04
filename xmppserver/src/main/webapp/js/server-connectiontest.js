// Shows the spinner on the server-to-server connection test page while the test runs, as the page only returns once
// the test has finished, and stops a second test from being started meanwhile.
(() => {
    const form = document.getElementById('s2s-test-form');
    const button = document.getElementById('s2s-test');
    const spinner = document.getElementById('spinner');
    if (!form || !button || !spinner) {
        return;
    }

    let submitted = false;
    form.addEventListener('submit', (event) => {
        if (submitted) {
            event.preventDefault();
            return;
        }
        submitted = true;
        spinner.classList.add('s2s-running');
        // Disable the button only once this submission is under way: a button that is disabled any earlier is left
        // out of the submitted form.
        setTimeout(() => { button.disabled = true; }, 0);
    }, false);

    // A page restored from the back-forward cache keeps the state above; make the form usable again.
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) {
            submitted = false;
            button.disabled = false;
            spinner.classList.remove('s2s-running');
        }
    });
})();
