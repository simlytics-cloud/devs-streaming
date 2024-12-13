/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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

package example.generator;

import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;

public class GeneratorModel extends PDEVSModel<LongSimTime, Integer> {

  public static String identifier = "generator";

  public static final Port<Integer> generatorOutputPort = new Port<>("OUTPUT", Integer.class);

  public GeneratorModel(Integer initialState) {
    super(initialState, identifier);
  }

  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    if (modelState == 0) {
      this.modelState = 1;
    } else {
      this.modelState = 0;
    }

  }

  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    if (modelState == 1) {
      return currentTime;
    } else {
      return LongSimTime.builder().t(currentTime.getT() + 1).build();
    }
  }

  @Override
  public Bag outputFunction() {
    return Bag.builder().addPortValueList(generatorOutputPort.createPortValue(modelState)).build();
  }
}
