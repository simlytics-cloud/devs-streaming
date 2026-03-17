/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and 
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except 
 * in compliance with the License. You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software distributed under the License 
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express 
 * or implied. See the License for the specific language governing permissions and limitations under 
 * the License.
 *  
 */

package devs.couplings;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Common implementations of SourceMatcher.
 */
public class SourceMatchers {

    /**
     * Matches a specific sender name exactly.
     */
    public record Literal(String name) implements SourceMatcher {
        @Override
        public boolean matches(String sender) {
            return Objects.equals(name, sender);
        }
    }

    /**
     * Matches any sender name in a provided list.
     */
    public record ListMatcher(Set<String> names) implements SourceMatcher {
        @Override
        public boolean matches(String sender) {
            return names.contains(sender);
        }
    }

    /**
     * Matches sender names based on a Regular Expression.
     */
    public static class RegexMatcher implements SourceMatcher {
        private final Pattern pattern;

        public RegexMatcher(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        @Override
        public boolean matches(String sender) {
            return pattern.matcher(sender).matches();
        }
    }
}
