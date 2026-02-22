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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the collection of coupling resolvers for a coupled model.
 */
public class Couplings {

    /**
     * A rule that links a source (matcher and port) to a resolver.
     */
    public record CouplingRule(SourceMatcher matcher, String sourcePort, CouplingResolver resolver) {
        public boolean matches(String sender, String port) {
            return sourcePort.equals(port) && matcher.matches(sender);
        }
    }

    private final List<CouplingRule> rules = new ArrayList<>();

    /**
     * Adds a static connection. If a static resolver already exists for the exact source model and port, 
     * it adds the target to the existing static resolver.
     */
    public void addConnection(String sourceModel, String sourcePort, String targetModel, String targetPort) {
        CouplingRule existing = rules.stream()
                .filter(r -> r.matcher() instanceof SourceMatchers.Literal literal 
                        && literal.name().equals(sourceModel) 
                        && r.sourcePort().equals(sourcePort)
                        && r.resolver() instanceof StaticCouplingResolver)
                .findFirst()
                .orElse(null);

        List<CouplingTarget> targets;
        if (existing != null && existing.resolver() instanceof StaticCouplingResolver staticResolver) {
            targets = new ArrayList<>(staticResolver.getTargets());
            rules.remove(existing);
        } else {
            targets = new ArrayList<>();
        }

        targets.add(CouplingTarget.of(targetModel, targetPort));
        rules.add(new CouplingRule(new SourceMatchers.Literal(sourceModel), sourcePort, new StaticCouplingResolver(targets)));
    }

    /**
     * Adds a custom resolver (e.g., dynamic) for a specific source model and port.
     */
    public void addResolver(String sourceModel, String sourcePort, CouplingResolver resolver) {
        rules.add(new CouplingRule(new SourceMatchers.Literal(sourceModel), sourcePort, resolver));
    }

    /**
     * Adds a resolver for multiple source models.
     */
    public void addResolver(Set<String> sourceModels, String sourcePort, CouplingResolver resolver) {
        rules.add(new CouplingRule(new SourceMatchers.ListMatcher(sourceModels), sourcePort, resolver));
    }

    /**
     * Adds a resolver for source models matching a pattern.
     */
    public void addPatternResolver(String regex, String sourcePort, CouplingResolver resolver) {
        rules.add(new CouplingRule(new SourceMatchers.RegexMatcher(regex), sourcePort, resolver));
    }

    /**
     * Returns all resolvers that match the given sender and port.
     */
    public List<CouplingResolver> getResolvers(String sender, String port) {
        return rules.stream()
                .filter(rule -> rule.matches(sender, port))
                .map(CouplingRule::resolver)
                .collect(Collectors.toList());
    }

    public List<CouplingRule> getRules() {
        return rules;
    }
}
