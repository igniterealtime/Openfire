/**
 * $RCSfile$
 * $Revision: 23996 $
 * $Date: 2005-11-21 13:50:46 -0800 (Mon, 21 Nov 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup;

import java.util.*;
import java.text.DateFormatSymbols;

public class Schedule {

    private long id;
    private boolean[] week = new boolean[7];
    private final String[] dayNames = new DateFormatSymbols().getShortWeekdays();
    private final List dayNamesList = Arrays.asList(dayNames);
    private TreeSet events = new TreeSet();
    private final String[] weekdays = new DateFormatSymbols().getShortWeekdays();

    public Schedule(long id) {
        this.id = id;
        Arrays.fill(week, false);
    }

    public Schedule(long id, String settings) {
        this(id);
        parse(settings);
    }

    public void clear() {
        events = new TreeSet();
        Arrays.fill(week, false);
    }

    public long getID() {
        return id;
    }

    public boolean[] getWeek() {
        return week;
    }

    public SortedSet getEvents() {
        return events;
    }

    public void parse(String data) {
        events.clear();
        Arrays.fill(week, false);
        if (data != null) {
            data = data.trim();
            int dayIndex = data.indexOf('#');
            String days;
            String events = null;
            if (dayIndex == -1) {
                // Just days
                days = data;
            }
            else {
                days = data.substring(0, dayIndex);
                data = data.substring(dayIndex + 1).trim();
                int eventIndex = data.indexOf('#');
                if (eventIndex == -1) {
                    // Just events
                    events = data;
                }
                else {
                    events = data.substring(0, eventIndex).trim();
                }
            }
            if (days.length() > 0) {
                StringTokenizer tokenizer = new StringTokenizer(days, ",", false);
                while (tokenizer.hasMoreTokens()) {
                    String day = tokenizer.nextToken().trim();
                    for (int i = 0; i < dayNames.length; i++) {
                        if (dayNames[i].equalsIgnoreCase(day)) {
                            week[i] = true;
                        }
                    }
                }
            }
            if (events != null && events.length() > 0) {
                StringTokenizer tokenizer = new StringTokenizer(events, ",", false);
                while (tokenizer.hasMoreTokens()) {
                    String event = tokenizer.nextToken();
                    StringTokenizer eventTokenizer = new StringTokenizer(event, "-", false);
                    int hour = Integer.parseInt(eventTokenizer.nextToken());
                    int min = Integer.parseInt(eventTokenizer.nextToken());
                    boolean on = "1".equals(eventTokenizer.nextToken());
                    this.events.add(new Event(hour, min, on));
                }
            }
        }
    }

    public String toString() {
        StringBuilder schedule = new StringBuilder();
        boolean needsComma = false;
        for (int i = 0; i < week.length; i++) {
            if (week[i]) {
                if (needsComma) {
                    schedule.append(',');
                }
                schedule.append(dayNames[i]);
                needsComma = true;
            }
        }
        schedule.append('#');
        needsComma = false;
        Iterator eventIter = events.iterator();
        while (eventIter.hasNext()) {
            Event event = (Schedule.Event)eventIter.next();
            if (needsComma) {
                schedule.append(',');
            }
            schedule.append(event.toString());
            needsComma = true;
        }
        return schedule.toString();
    }

    public boolean isOpened(Calendar now) {
        int index = 0;
        boolean isOpened;
        try {
            index = dayNamesList.indexOf(weekdays[now.get(Calendar.DAY_OF_WEEK)]);
            isOpened = getWeek()[index];
        }
        catch (Exception e) {
            return false;
        }

        return isOpened;
    }

    /**
        * <p>An event that will occur in the schedule.</p>
        * <p>Event times are:</p>
        * <ul>
        * <li>hour - any number between 0 and 23 with 0 being midnight.</li>
        * <li>minute - any minute between 0 and 59.</li>
        * </ul>
        */
      public static class Event implements Comparable{
           private int hour;
           private int minute;
           private boolean on;
           /**
            * <p>Create an event.</p>
            *
            * @param hour The hour this event occurs
            * @param minute The minute this event occurs
            * @param on True if the event turns on the associated resource
            */
           public Event(int hour, int minute, boolean on){
               setHour(hour);
               setMinute(minute);
               setOn(on);
           }

           /**
            * <p>Obtain the hour for this event.</p>
            *
            * @return The hour of this event
            */
           public int getHour() {
               return hour;
           }

           /**
            * <p>Set the hour for this event.</p>
            *
            * @param hour The hour of this event
            */
           public void setHour(int hour) {
               this.hour = hour;
           }

           /**
            * <p>Obtain the minute for this event.</p>
            *
            * @return The minute of this event
            */
           public int getMinute() {
               return minute;
           }

           /**
            *
            * @param minute
            */
           public void setMinute(int minute) {
               this.minute = minute;
           }

           /**
            * <p>Get the flag indicating this event is to trigger the resource to turn on or off.</p>
            *
            * @return True if this event turns the resource on
            */
           public boolean isOn() {
               return on;
           }

           /**
            * <p>Set a flag indicating this event is to trigger the resource to turn on or off.</p>
            *
            * @param on True if this event turns the resource on
            */
           public void setOn(boolean on) {
               this.on = on;
           }

           /**
            * <p>Compares events based on time.</p>
            *
            * <p>Comparisons against non Event objects will result in a class cast exception.</p>
            *
            * @param o The object to compare with this one
            * @return negative, zero, or positive int if the object is less than, equal to, or greater than the specified object
            */
           public int compareTo(Object o) {
               Event event = (Event)o;
               int val = hour - event.hour;
               if (val == 0){
                   val = minute - event.minute;
               }
               return val;
           }

           /**
            * <p>Generate the event string in proper format for parsing.</p>
            *
            * @return The event as a standard schedule string
            */
           public String toString(){
               StringBuilder event = new StringBuilder(Integer.toString(hour));
               event.append('-');
               event.append(Integer.toString(minute));
               event.append('-');
               event.append(on ? '1' : '0');
               return event.toString();
           }

           public boolean equals(Object o){
               boolean eq = false;
               if (o instanceof Event){
                   Event event = (Event)o;
                   eq = event.hour == hour && event.minute == minute && event.on == on;
               }
               return eq;
           }
       }
   }

