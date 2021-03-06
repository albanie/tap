/*
 * Licensed to Think Big Analytics, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Think Big Analytics, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Copyright 2010 Think Big Analytics. All Rights Reserved.
 */
package tap.core;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.joda.time.*;
import org.junit.Before;
import org.junit.Test;

import tap.CommandOptions;
import tap.Tap;



public class BaseOptionsTests {

    private Tap pipe;
    private CommandOptions options;
    
    @Before
    public void setup() {
    	           
    }
    
    @Test
    public void invalid() {
        PrintStream oldErr = System.err;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        System.setErr(ps);
        try {
        	String[] args = {"myProgram", "-t=2010-09-07T13:00:00Z/2010-09-08T13:30:00Z"};
            options = new CommandOptions(args);
            pipe = new Tap(options);
            
            ps.flush();
            String errText = new String(bos.toByteArray());
            //Error message is localized so check it carefully.
            assertTrue(errText.contains("\"-t=2010-09-07T13:00:00Z/2010-09-08T13:30:00Z\""));
            assertTrue(errText.contains("Usage: hadoop jar"));
            assertTrue(errText.contains("-time"));
            assertTrue(errText.contains("Example: hadoop jar"));
        } finally {
            System.setErr(oldErr);
        }
    }
    
    @Test
    public void interval1() {
/*
 * this test fails because args4j has a bug - change opt to h in the below method for CmdLine:
   private Map<String,OptionHandler> filter(List<OptionHandler> opt, String keyFilter) {
        Map<String,OptionHandler> rv = new TreeMap<String,OptionHandler>();
        for (OptionHandler h : opt) {
            if (opt.toString().startsWith(keyFilter)) rv.put(opt.toString(), h);
        }
        return rv;
    }
        
 */
        PrintStream oldErr = System.err;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        System.setErr(ps);
        try {
        	String[] args = {"myProgram", "-time=2010-09-07T13:00:00Z/2010-09-08T13:30:00Z"};
            options = new CommandOptions(args);
            pipe = new Tap(options);
     
        } finally {
            System.setErr(oldErr);
        }
    }
    
    @Test
    public void interval2() {
    	String[] args = {"myProgram", "-time","2010-09-07T13:00:00Z/2010-09-08T13:30:00Z"};
        options = new CommandOptions(args);
        pipe = new Tap(options);
        assertNotNull(options.time);
        
        Interval interval = options.getInterval();
        DateTime expectedStart = new DateTime(2010, 9, 7, 13, 0, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
        DateTime expectedEnd = new DateTime(2010, 9, 8, 13, 30, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
        assertEquals(expectedStart, interval.getStart());
        assertEquals(expectedEnd, interval.getEnd());
    }
    
    @Test
    public void interval3() {
    	String[] args = {"myProgram", "-s", "2010-09-07T13:00:00Z", "-e", "2010-09-08T133000Z"};
        options = new CommandOptions(args);
        pipe = new Tap(options);
        
        Interval interval = options.getInterval();
        DateTime expectedStart = new DateTime(2010, 9, 7, 13, 0, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
        DateTime expectedEnd = new DateTime(2010, 9, 8, 13, 30, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
        assertEquals(expectedStart, interval.getStart());
        assertEquals(expectedEnd, interval.getEnd());
    }
    
    @Test
    public void interval4() {
        String[] args = {"myProgram", "-s", "2010-09-07T13:00:00Z", "-d", "1 hour"};
        options = new CommandOptions(args);
        pipe = new Tap(options);
        
        Interval interval = options.getInterval();
        DateTime expectedStart = new DateTime(2010, 9, 7, 13, 0, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
        DateTime expectedEnd = expectedStart.plusHours(1);
        assertEquals(expectedStart, interval.getStart());
        assertEquals(expectedEnd, interval.getEnd());
    }

    @Test
    public void intervalBad() {
        String[] args = {"myProgram", "--start", "2010-09-07T13:00:00Z", "-d", "1 ziphoon"};
        options = new CommandOptions(args);
        pipe = new Tap(options);
        
        try {
            options.getInterval();
            fail("invalid duration");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can't parse: 1 ziphoon"));
        }
    }

    @Test
    public void intervalBad2() {
    	String[] args = {"myProgram", "--start", "2010-09-07T13:00:00Z", "-d", "1hour"};
        options = new CommandOptions(args);
        pipe = new Tap(options);
        
        try {
            options.getInterval();
            fail("invalid duration");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can't parse: 1hour"));
        }
    }
    // this will require a custom formatter
//    @Test
//    public void intervalShouldWork() {
//        int rc = options.parse(pipe, "-s", "2010-09-07T13:00:00Z", "--duration", "1h");
//        assertEquals(0, rc);
//        
//        Interval interval = options.getInterval();
//        DateTime expectedStart = new DateTime(2010, 9, 7, 13, 0, 0, 0).withZoneRetainFields(DateTimeZone.UTC); 
//        DateTime expectedEnd = expectedStart.plusHours(1);
//        assertEquals(expectedStart, interval.getStart());
//        assertEquals(expectedEnd, interval.getEnd());
//    }

}
