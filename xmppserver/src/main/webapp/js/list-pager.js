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

/**
 * Common logic for ListPager navigation.
 */
const ListPager = (function() {
    const PAGINATION_FORM_ID = 'paginationForm';
    const REQUEST_PARAMETER_KEY_PAGE_SIZE = 'listPagerPageSize';
    const REQUEST_PARAMETER_KEY_CURRENT_PAGE = 'listPagerCurrentPage';

    function getForm() {
        return document.getElementById(PAGINATION_FORM_ID);
    }

    function getAdditionalFormFields() {
        const form = getForm();
        if (!form) return [];
        const fieldsJson = form.getAttribute('data-additional-form-fields');
        try {
            return fieldsJson ? JSON.parse(fieldsJson) : [];
        } catch (e) {
            console.error("Error parsing additionalFormFields", e);
            return [];
        }
    }

    function jumpToPage(pageNumber) {
        const formObject = getForm();
        if (formObject) {
            formObject[REQUEST_PARAMETER_KEY_CURRENT_PAGE].value = pageNumber;
            submitForm();
        }
        return false;
    }

    function setPageSize(pageSize) {
        const formObject = getForm();
        if (formObject) {
            formObject[REQUEST_PARAMETER_KEY_PAGE_SIZE].value = pageSize;
            submitForm();
        }
        return false;
    }

    function submitForm() {
        const formObject = getForm();
        if (!formObject) return false;

        const additionalFormFields = getAdditionalFormFields();
        for (let i = 0; i < additionalFormFields.length; i++) {
            const field = document.getElementById(additionalFormFields[i]);
            if (field !== null) {
                const formField = formObject[additionalFormFields[i]];
                if (!formField) {
                    console.warn("Hidden form field missing for:", additionalFormFields[i]);
                    continue;
                }
                if (typeof field !== 'object' || field.value === '') {
                    formField.disabled = true;
                } else {
                    formField.disabled = false;
                    formField.value = field.value;
                }
            }
        }

        // Disable unchanged page size or default page number to keep URL clean
        const initialPageSize = formObject.getAttribute('data-page-size');
        if (formObject[REQUEST_PARAMETER_KEY_PAGE_SIZE].value === initialPageSize) {
            formObject[REQUEST_PARAMETER_KEY_PAGE_SIZE].disabled = true;
        }
        if (formObject[REQUEST_PARAMETER_KEY_CURRENT_PAGE].value === '1') {
            formObject[REQUEST_PARAMETER_KEY_CURRENT_PAGE].disabled = true;
        }

        formObject.submit();
        return false;
    }

    function inputFieldOnKeyDownEventListener(e) {
        if (e.keyCode === 13) {
            submitForm();
            return false;
        }
    }

    function inputFieldOnInputEventListener() {
        if (this.value === '') {
            submitForm();
            return false;
        }
    }

    function init() {
        const form = getForm();
        if (!form) return;

        const additionalFormFields = getAdditionalFormFields();
        for (let i = 0; i < additionalFormFields.length; i++) {
            const field = document.getElementById(additionalFormFields[i]);
            if (field !== null && typeof field === 'object') {
                field.onkeydown = inputFieldOnKeyDownEventListener;
                field.addEventListener('input', inputFieldOnInputEventListener);
            }
        }

        // Event delegation for pagination links
        document.addEventListener('click', function(e) {
            const anchor = e.target.closest('a');
            if (anchor && !anchor.onclick && anchor.href.includes(REQUEST_PARAMETER_KEY_CURRENT_PAGE)) {
                try {
                    const url = new URL(anchor.href, window.location.origin);
                    const page = url.searchParams.get(REQUEST_PARAMETER_KEY_CURRENT_PAGE);
                    if (page) {
                        e.preventDefault();
                        jumpToPage(page);
                    }
                } catch (err) {
                    // Ignore URL parsing errors for relative links if URL constructor fails
                }
            }
        });

        // Event delegation for page size selector
        document.addEventListener('change', function(e) {
            if (e.target.name === REQUEST_PARAMETER_KEY_PAGE_SIZE && !e.target.onchange) {
                setPageSize(e.target.value);
            }
        });
    }

    return {
        init: init,
        jumpToPage: jumpToPage,
        setPageSize: setPageSize,
        submitForm: submitForm
    };
})();

document.addEventListener('DOMContentLoaded', ListPager.init);
