// Handles the password row of each certificate store on the certificate store management page. Each store's
// new-password field starts hidden and disabled, so it is not submitted and the current password is kept. "Change"
// (or "Set") reveals and enables it; "Cancel" clears, disables and hides it again. Once a password is typed, the row is
// marked as changed until it is saved or cancelled. Each password cell is handled on its own, finding its elements
// within the cell, so that every store's links act on that store's field.
(() => {
    document.querySelectorAll('td.store-password').forEach((cell) => {
        const row = cell.closest('tr');
        const status = cell.querySelector('.store-password-status');
        const edit = cell.querySelector('.store-password-edit');
        const field = edit.querySelector('input[type="password"]');
        const change = status.querySelector('.store-password-change');
        const cancel = edit.querySelector('.store-password-cancel');

        const markChanged = () => {
            row.classList.toggle('store-password-changed', field.value !== '');
        };

        const show = (editing) => {
            field.value = '';
            field.disabled = !editing;
            status.hidden = editing;
            edit.hidden = !editing;
            change.setAttribute('aria-expanded', editing ? 'true' : 'false');
            markChanged();
            (editing ? field : change).focus();
        };

        field.addEventListener('input', markChanged);

        // These links act as buttons: Enter activates a link natively, Space needs handling.
        [[change, true], [cancel, false]].forEach(([link, editing]) => {
            link.addEventListener('click', (event) => {
                event.preventDefault();
                show(editing);
            });
            link.addEventListener('keydown', (event) => {
                if (event.key === ' ') {
                    event.preventDefault();
                    show(editing);
                }
            });
        });
    });
})();
