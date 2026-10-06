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

package devs;

import devs.couplings.CouplingTarget;
import devs.couplings.DynamicCouplingResolver;
import devs.iso.PortValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PDevsCouplingsRoutingTest {

  @Test
  void explicitSourceOrderPreservesBagTargetsDuplicatesAndExternalRoutes() {
    PDevsCouplings couplings = PDevsCouplings.builder("parent")
        .addConnection("first", "out", "sink", "in")
        .addConnection("first", "out", "sink", "in")
        .addConnection("first", "out", "parent", "external")
        .addConnection("second", "other", "sink", "secondIn")
        .build();
    Object firstValue = new Object();
    Object nextValue = new Object();
    Map<String, Optional<List<PortValue<?>>>> reversed = new LinkedHashMap<>();
    reversed.put("second", Optional.of(List.of(message("other", "second"))));
    reversed.put("first", Optional.of(List.of(message("out", firstValue),
        message("unused", "ignored"), message("out", nextValue))));
    reversed.put("silent", Optional.empty());

    OutputCouplingMessages result = couplings.handleOutputBag(reversed,
        List.of("first", "silent", "absent", "second"));
    List<RoutedDelivery> routes = result.getRoutedDeliveries();
    assertEquals(List.of("first", "first", "first", "first", "first", "first", "second"),
        routes.stream().map(RoutedDelivery::sourceModel).toList());
    assertEquals(List.of("out", "out", "out", "out", "out", "out", "other"),
        routes.stream().map(RoutedDelivery::sourcePort).toList());
    assertEquals(List.of("sink", "sink", "parent", "sink", "sink", "parent", "sink"),
        routes.stream().map(RoutedDelivery::targetModel).toList());
    assertEquals(List.of("in", "in", "external", "in", "in", "external", "secondIn"),
        routes.stream().map(RoutedDelivery::targetPort).toList());
    assertSame(firstValue, routes.get(0).portValue().getValue());
    assertSame(nextValue, routes.get(3).portValue().getValue());
    assertSame(routes.get(0).portValue(), result.getInternalMessages().get("sink").get(0));
    assertSame(routes.get(1).portValue(), result.getInternalMessages().get("sink").get(1));
    assertSame(routes.get(2).portValue(), result.getExternalOutputMessages().get(0));
    assertEquals(5, result.getInternalMessages().get("sink").size());
    assertEquals(2, result.getExternalOutputMessages().size());

    Map<String, Optional<List<PortValue<?>>>> forward = new LinkedHashMap<>();
    forward.put("first", reversed.get("first"));
    forward.put("silent", Optional.empty());
    forward.put("second", reversed.get("second"));
    assertEquals(routes, couplings.handleOutputBag(forward,
        List.of("first", "silent", "absent", "second")).getRoutedDeliveries());
    assertThrows(UnsupportedOperationException.class, () -> routes.clear());
  }

  @Test
  void dynamicSetAndPatternResolversKeepTransformedObjectIdentityAndOrder() {
    AtomicInteger calls = new AtomicInteger();
    PortValue<?> transformed = message("changed", new Object());
    DynamicCouplingResolver resolver = new DynamicCouplingResolver() {
      @Override
      public List<CouplingTarget> resolve(String sender, PortValue<?> portValue) {
        calls.incrementAndGet();
        assertEquals("producer", sender);
        assertEquals("original", portValue.getValue());
        return List.of(CouplingTarget.of("sink", transformed),
            CouplingTarget.of("parent", transformed));
      }
    };
    PDevsCouplings couplings = PDevsCouplings.builder("parent")
        .addConnection("producer", "out", "sink", "static")
        .addResolver("producer", "out", resolver)
        .addResolver(Set.of("producer", "other"), "out", resolver)
        .addPatternResolver("prod.*", "out", resolver)
        .build();

    OutputCouplingMessages result = couplings.handleOutputBag(
        Map.of("producer", Optional.of(List.of(message("out", "original")))),
        List.of("producer"));
    assertEquals(3, calls.get());
    assertEquals(7, result.getRoutedDeliveries().size());
    assertEquals("static", result.getRoutedDeliveries().get(0).targetPort());
    for (RoutedDelivery route : result.getRoutedDeliveries().subList(1, 7)) {
      assertEquals("producer", route.sourceModel());
      assertEquals("out", route.sourcePort());
      assertEquals("changed", route.targetPort());
      assertSame(transformed, route.portValue());
    }
    assertSame(transformed, result.getInternalMessages().get("sink").get(1));
    assertSame(transformed, result.getExternalOutputMessages().get(0));
  }

  @Test
  void parentInputsUseCoupledModelAsSourceAndRetainLegacyInputBags() {
    PortValue<?> transformed = message("renamed", new Object());
    PDevsCouplings couplings = PDevsCouplings.builder("parent")
        .addConnection("parent", "input", "sink", "in")
        .addConnection("parent", "input", "sink", "in")
        .addResolver("parent", "input", new DynamicCouplingResolver() {
          @Override
          public List<CouplingTarget> resolve(String sender, PortValue<?> portValue) {
            assertEquals("parent", sender);
            return List.of(CouplingTarget.of("other", transformed));
          }
        })
        .build();
    List<PortValue<?>> input = List.of(message("input", "first"),
        message("unused", "ignored"), message("input", "second"));

    OutputCouplingMessages result = couplings.handleInputBag(input);
    assertEquals(couplings.handleInputMessage(input), result.getInternalMessages());
    assertTrue(result.getExternalOutputMessages().isEmpty());
    assertEquals(6, result.getRoutedDeliveries().size());
    assertEquals(List.of("sink", "sink", "other", "sink", "sink", "other"),
        result.getRoutedDeliveries().stream().map(RoutedDelivery::targetModel).toList());
    for (RoutedDelivery route : result.getRoutedDeliveries()) {
      assertEquals("parent", route.sourceModel());
      assertEquals("input", route.sourcePort());
    }
    assertSame(transformed, result.getRoutedDeliveries().get(2).portValue());
    assertSame(transformed, result.getInternalMessages().get("other").get(0));
  }

  @Test
  void legacyOutputBagsAndLowLevelMethodsRemainCompatible() {
    PDevsCouplings couplings = PDevsCouplings.builder("parent")
        .addConnection("child", "out", "sink", "in")
        .addConnection("child", "out", "parent", "external")
        .addConnection("parent", "input", "sink", "in")
        .build();
    Map<String, Optional<List<PortValue<?>>>> outputs = Map.of("child",
        Optional.of(List.of(message("out", "value"))));
    Map<String, List<PortValue<?>>> internal = new LinkedHashMap<>();
    List<PortValue<?>> external = new ArrayList<>();
    couplings.handleOutputs(outputs, internal, external);
    OutputCouplingMessages result = couplings.handleOutputBag(outputs);
    assertEquals(internal, result.getInternalMessages());
    assertEquals(external, result.getExternalOutputMessages());
    assertEquals(2, result.getRoutedDeliveries().size());
    assertTrue(new OutputCouplingMessages(external, internal).getRoutedDeliveries().isEmpty());

    List<PortValue<?>> inputs = List.of(message("input", "value"));
    Map<String, List<PortValue<?>>> inputMap = new LinkedHashMap<>();
    couplings.handleInputs(inputs, inputMap);
    assertEquals(inputMap, couplings.handleInputMessage(inputs));
    assertEquals(inputMap, couplings.handleInputBag(inputs).getInternalMessages());
  }

  @Test
  void emptyUnmatchedAndInvalidSourceOrdersDoNotProduceDeliveries() {
    PDevsCouplings couplings = PDevsCouplings.builder("parent").build();
    assertTrue(couplings.handleOutputBag(null).getRoutedDeliveries().isEmpty());
    assertTrue(couplings.handleOutputBag(Map.of(), List.of()).getRoutedDeliveries().isEmpty());
    assertTrue(couplings.handleInputBag(null).getRoutedDeliveries().isEmpty());
    assertTrue(couplings.handleInputBag(List.of()).getInternalMessages().isEmpty());
    Map<String, Optional<List<PortValue<?>>>> outputs = Map.of("child",
        Optional.of(List.of(message("out", "ignored"))));
    assertTrue(couplings.handleOutputBag(outputs, List.of("child")).getRoutedDeliveries().isEmpty());
    assertThrows(IllegalArgumentException.class,
        () -> couplings.handleOutputBag(outputs, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> couplings.handleOutputBag(outputs, List.of("child", "child")));
  }

  private static PortValue<?> message(String port, Object value) {
    return PortValue.builder().value(value).portName(port).build();
  }
}