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
 */

package devs.msg.mutability;

import devs.Port;
import devs.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ImmutablePortTypedCollectionTest {

  @Test
  public void supportsTypedListAndMapPorts() {
    ImmutablePort<List<String>> typedListPort =
        new ImmutablePort<>("listIn", new TypeReference<List<String>>() {
        });
    List<String> listValue = List.of("a", "b");
    assert typedListPort.getValue(typedListPort.createPortValue(listValue)).equals(listValue);

    ImmutablePort<Map<String, Integer>> typedMapPort =
        new ImmutablePort<>("mapIn", new TypeReference<Map<String, Integer>>() {
        });
    Map<String, Integer> mapValue = Map.of("k", 1);
    assert typedMapPort.getValue(typedMapPort.createPortValue(mapValue)).equals(mapValue);

    ImmutablePort<List<String>> typedListPortFromFactory =
        new ImmutablePort<>("listFromFactory", TypeReference.listOf(String.class));
    assert typedListPortFromFactory.getValue(typedListPortFromFactory.createPortValue(listValue))
        .equals(listValue);

    ImmutablePort<Map<String, Integer>> typedMapPortFromFactory =
        new ImmutablePort<>("mapFromFactory", TypeReference.mapOf(String.class, Integer.class));
    assert typedMapPortFromFactory.getValue(typedMapPortFromFactory.createPortValue(mapValue))
        .equals(mapValue);
  }

  @Test
  public void keepsExistingClassConstructorsWorking() {
    Port<String> port = new Port<>("out", String.class);
    assert port.getValue(port.createPortValue("ok")).equals("ok");

    ImmutablePort<String> immutablePort = new ImmutablePort<>("immutableOut", String.class);
    assert immutablePort.getValue(immutablePort.createPortValue("ok")).equals("ok");
  }

  @Test
  public void rejectsMutableTypeArgumentsInTypedCollections() {
    boolean threw = false;
    try {
      new ImmutablePort<List<MutableValue>>("badList", new TypeReference<List<MutableValue>>() {
      });
    } catch (IllegalArgumentException expected) {
      threw = true;
    }
    assert threw;
  }

  static class MutableValue {
    private int value;
  }
}