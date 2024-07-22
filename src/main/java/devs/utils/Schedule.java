/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.utils;

import devs.msg.time.SimTime;

import java.util.*;

public class Schedule<T extends SimTime> extends TreeMap<T, ArrayList<Object>> {
    public Schedule() {
    }

    public Schedule(Map<? extends T, ArrayList<Object>> m) {
        super(m);
    }

    public Schedule(SortedMap<T, ArrayList<Object>> m) {
        super(m);
    }
    
    public void add(T time, Object event) {
    	if (this.containsKey(time)) {
    		this.get(time).add(event);
    	} else {
    		ArrayList<Object> events = new ArrayList<Object>();
    		events.add(event);
    		this.put(time, events);
    	}
    }
    
    @Override
    public String toString() {
    	StringBuilder stringBuilder = new StringBuilder();
    	stringBuilder.append("Scheduled Events: \n");
    	for (T key: keySet()) {
    		stringBuilder.append("  Time:" + key + "\n");
    		for (Object event: get(key)) {
    			stringBuilder.append("    Event:" + event + "\n");
    		}
    	}
    	return stringBuilder.toString();
    }
}
