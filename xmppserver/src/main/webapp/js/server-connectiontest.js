// Shows the spinner on the server-to-server connection test page while the test runs, as the page only returns once
// the test has finished.
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('s2s-test-form');
    const spinner = document.getElementById('spinner');
    if (form && spinner) {
        form.addEventListener('submit', () => { spinner.style.display = 'inline-block'; }, false);
    }
});
