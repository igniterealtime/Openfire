/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

(function() {
    function getKey(imgObject) {
        // Find the first span in the first cell (nameColumn) of this row
        const row = imgObject.closest('tr');
        if (row) {
            const span = row.querySelector('.nameColumn span');
            if (span) {
                return span.textContent;
            }
        }
        return "";
    }

    function doEdit(imgObject, hidden, encrypted, nullValue) {
        document.getElementById("editPropertyName").value = getKey(imgObject);
        const valueField = document.getElementById("editPropertyValue");
        
        if (encrypted || hidden || nullValue) {
            valueField.value = "";
        } else {
            const row = imgObject.closest('tr');
            if (row) {
                const valueSpan = row.querySelector('.valueColumn span');
                if (valueSpan) {
                    valueField.value = valueSpan.textContent;
                }
            }
        }

        const defaultValueField = document.getElementById("defaultPropertyValue");
        const row = imgObject.closest('tr');
        if (row) {
            // Default value is in the 3rd column (index 2)
            const defaultValueCell = row.querySelectorAll('.valueColumn')[1];
            if (defaultValueCell) {
                defaultValueField.innerText = defaultValueCell.textContent;
            }
        }

        document.getElementById(encrypted ? "editPropertyEncryptTrue" : "editPropertyEncryptFalse").checked = true;
        document.getElementById("newPropertyTitle").style.display = "none";
        document.getElementById("editPropertyTitle").style.display = "";
        valueField.focus();
        // Modern approach to move cursor to start/end
        valueField.setSelectionRange(0, 0);
        window.scrollTo(0, document.body.scrollHeight);
    }

    function doEncrypt(imgObject) {
        const confirmMessage = document.getElementById('actionForm').getAttribute('data-encrypt-confirm');
        if (confirm(confirmMessage)) {
            submitActionForm("encrypt", getKey(imgObject));
        }
    }

    function doDelete(imgObject) {
        const confirmMessage = document.getElementById('actionForm').getAttribute('data-delete-confirm');
        if (confirm(confirmMessage)) {
            submitActionForm("delete", getKey(imgObject));
        }
    }

    function submitEditForm(save) {
        const action = save ? "save" : "cancel";
        const key = document.getElementById("editPropertyName").value;
        if (key.trim() === "") {
            return;
        }
        if (save) {
            const value = document.getElementById("editPropertyValue").value;
            const encrypt = document.getElementById("editPropertyEncryptTrue").checked;
            submitActionForm(action, key, value, encrypt);
        } else {
            submitActionForm(action, key);
        }
    }

    function submitActionForm(action, key, value, encrypt) {
        const form = document.getElementById("actionForm");
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
        // Action icons in the table
        document.addEventListener('click', function(e) {
            const target = e.target.closest('.clickable');
            if (!target) return;

            if (target.getAttribute('src').indexOf('edit-16x16.gif') > -1) {
                const hidden = target.getAttribute('data-hidden') === 'true';
                const encrypted = target.getAttribute('data-encrypted') === 'true';
                const nullValue = target.getAttribute('data-null-value') === 'true';
                doEdit(target, hidden, encrypted, nullValue);
            } else if (target.getAttribute('src').indexOf('add-16x16.gif') > -1) {
                doEncrypt(target);
            } else if (target.getAttribute('src').indexOf('delete-16x16.gif') > -1) {
                doDelete(target);
            } else if (target.getAttribute('alt') === 'Search') {
                if (typeof ListPager !== 'undefined') {
                    ListPager.submitForm();
                }
            }
        });

        // Save/Cancel buttons
        const saveBtn = document.getElementById('savePropertyBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => submitEditForm(true));
        }
        const cancelBtn = document.getElementById('cancelPropertyBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => submitEditForm(false));
        }

        // Global keydown for Enter on search inputs
        const searchInputs = ["searchName", "searchValue", "searchDescription"];
        searchInputs.forEach(id => {
            const input = document.getElementById(id);
            if (input) {
                input.addEventListener('keydown', function(e) {
                    if (e.keyCode === 13) {
                        if (typeof ListPager !== 'undefined') {
                            ListPager.submitForm();
                        }
                    }
                });
            }
        });
    });
})();
