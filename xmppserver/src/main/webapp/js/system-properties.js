/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Page-specific JavaScript for system-properties.jsp.
 * Replaces inline onclick/onchange handlers with event delegation
 * for CSP compliance.
 */
(function() {
    function getKey(imgObject) {
        var row = imgObject.closest('tr');
        if (row) {
            var span = row.querySelector('.nameColumn span');
            if (span) {
                return span.textContent;
            }
        }
        return "";
    }

    function doEdit(imgObject, hidden, encrypted, nullValue) {
        document.getElementById("editPropertyName").value = getKey(imgObject);
        var valueField = document.getElementById("editPropertyValue");

        if (encrypted || hidden || nullValue) {
            valueField.value = "";
        } else {
            var row = imgObject.closest('tr');
            if (row) {
                var valueSpan = row.querySelector('.valueColumn span');
                if (valueSpan) {
                    valueField.value = valueSpan.textContent;
                }
            }
        }

        var defaultValueField = document.getElementById("defaultPropertyValue");
        var editRow = imgObject.closest('tr');
        if (editRow) {
            var defaultValueCell = editRow.querySelectorAll('.valueColumn')[1];
            if (defaultValueCell) {
                defaultValueField.innerText = defaultValueCell.textContent.trim();
            }
        }

        document.getElementById(encrypted ? "editPropertyEncryptTrue" : "editPropertyEncryptFalse").checked = true;
        document.getElementById("newPropertyTitle").style.display = "none";
        document.getElementById("editPropertyTitle").style.display = "";
        valueField.focus();
        valueField.setSelectionRange(0, 0);
        window.scrollTo(0, document.body.scrollHeight);
    }

    function doEncrypt(imgObject) {
        var confirmMessage = document.getElementById('actionForm').getAttribute('data-encrypt-confirm');
        if (confirm(confirmMessage)) {
            submitActionForm("encrypt", getKey(imgObject));
        }
    }

    function doDelete(imgObject) {
        var confirmMessage = document.getElementById('actionForm').getAttribute('data-delete-confirm');
        if (confirm(confirmMessage)) {
            submitActionForm("delete", getKey(imgObject));
        }
    }

    function submitEditForm(save) {
        var action = save ? "save" : "cancel";
        var key = document.getElementById("editPropertyName").value;
        if (key.trim() === "") {
            return;
        }
        if (save) {
            var value = document.getElementById("editPropertyValue").value;
            var encrypt = document.getElementById("editPropertyEncryptTrue").checked;
            submitActionForm(action, key, value, encrypt);
        } else {
            submitActionForm(action, key);
        }
    }

    function submitActionForm(action, key, value, encrypt) {
        var form = document.getElementById("actionForm");
        form["action"].value = action;
        form["key"].value = key;
        if (typeof value !== "undefined") {
            form["value"].value = value;
            form["value"].disabled = false;
        } else {
            form["value"].disabled = true;
        }
        if (typeof encrypt !== "undefined") {
            form["encrypt"].value = encrypt;
            form["encrypt"].disabled = false;
        } else {
            form["encrypt"].disabled = true;
        }
        form.submit();
    }

    document.addEventListener('DOMContentLoaded', function() {
        // Delegate click events for action icons in the property table
        document.addEventListener('click', function(e) {
            var target = e.target.closest('.clickable');
            if (!target) return;

            var src = target.getAttribute('src') || '';
            if (src.indexOf('edit-16x16.gif') > -1) {
                var hidden = target.getAttribute('data-hidden') === 'true';
                var encrypted = target.getAttribute('data-encrypted') === 'true';
                var nullValue = target.getAttribute('data-null-value') === 'true';
                doEdit(target, hidden, encrypted, nullValue);
            } else if (src.indexOf('add-16x16.gif') > -1) {
                doEncrypt(target);
            } else if (src.indexOf('delete-16x16.gif') > -1) {
                doDelete(target);
            } else if (target.getAttribute('alt') === 'Search') {
                if (typeof ListPager !== 'undefined') {
                    ListPager.submitForm();
                }
            }
        });

        // Save/Cancel buttons for the edit form
        var saveBtn = document.getElementById('savePropertyBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', function() { submitEditForm(true); });
        }
        var cancelBtn = document.getElementById('cancelPropertyBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function() { submitEditForm(false); });
        }
    });
})();
