/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class ListPagerTest {

    private static final List<Integer> LIST_OF_25 = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25);

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpSession session;

    @BeforeEach
    public void setUp() {
        doReturn(session).when(request).getSession();
    }

    @Test
    public void aDefaultRequestWillHaveAPageSizeOf25() {
        doReturn("5").when(request).getParameter("listPagerPageSize");

        final ListPager<Integer> listPager = new ListPager<>(request, response, LIST_OF_25);

        assertThat(listPager.getCurrentPageNumber(), is(1));
        assertThat(listPager.getTotalPages(), is(5));
        assertThat(listPager.getFilteredItemCount(), is(25));
        assertThat(listPager.isFiltered(), is(false));
        assertThat(listPager.getFirstItemNumberOnPage(), is(1));
        assertThat(listPager.getLastItemNumberOnPage(), is(5));
    }

    @Test
    public void anEmptyListWillStartOnPage1Of1() {
        doReturn("5").when(request).getParameter("listPagerPageSize");

        final ListPager<Integer> listPager = new ListPager<>(request, response, Collections.emptyList());

        assertThat(listPager.getCurrentPageNumber(), is(1));
        assertThat(listPager.getTotalPages(), is(1));
        assertThat(listPager.getFilteredItemCount(), is(0));
        assertThat(listPager.isFiltered(), is(false));
        assertThat(listPager.getFirstItemNumberOnPage(), is(0));
        assertThat(listPager.getLastItemNumberOnPage(), is(0));
    }

    @Test
    public void theLowerBoundCurrentPageIs1() {
        doReturn("5").when(request).getParameter("listPagerPageSize");
        doReturn("-1").when(request).getParameter("listPagerCurrentPage");

        final ListPager<Integer> listPager = new ListPager<>(request, response, LIST_OF_25);

        assertThat(listPager.getCurrentPageNumber(), is(1));
        assertThat(listPager.getFirstItemNumberOnPage(), is(1));
        assertThat(listPager.getLastItemNumberOnPage(), is(5));
    }

    @Test
    public void theUpperBoundCurrentPageIsTotalPages() {
        doReturn("5").when(request).getParameter("listPagerPageSize");
        doReturn(String.valueOf(Integer.MAX_VALUE)).when(request).getParameter("listPagerCurrentPage");

        final ListPager<Integer> listPager = new ListPager<>(request, response, LIST_OF_25);

        assertThat(listPager.getCurrentPageNumber(), is(5));
        assertThat(listPager.getFirstItemNumberOnPage(), is(21));
        assertThat(listPager.getLastItemNumberOnPage(), is(25));
    }

    @Test
    public void willFilterTheList() {
        doReturn("5").when(request).getParameter("listPagerPageSize");

        // Filter on even numbers only
        final ListPager<Integer> listPager = new ListPager<>(request, response, LIST_OF_25, value -> value % 2 == 0);

        assertThat(listPager.isFiltered(), is(true));
        assertThat(listPager.getFilteredItemCount(), is(12));
        assertThat(listPager.getTotalPages(), is(3));
        assertThat(listPager.getTotalItemCount(), is(25));
        assertThat(listPager.getItemsOnCurrentPage(), contains(2, 4, 6, 8, 10));
        assertThat(listPager.getFirstItemNumberOnPage(), is(1));
        assertThat(listPager.getLastItemNumberOnPage(), is(5));
    }

    @Test
    public void willRoundUp() {

        doReturn("24").when(request).getParameter("listPagerPageSize");

        final ListPager<Integer> listPager = new ListPager<>(request, response, LIST_OF_25);

        assertThat(listPager.getTotalPages(), is(2));
    }
}
