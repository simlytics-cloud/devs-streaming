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

package example.generator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import devs.PDEVSModel;

public class GeneratorModel extends PDEVSModel<LongSimTime, Integer> {

    public static String identifier = "generator";

    public static Port<Integer> generatorOutputPort = new Port<Integer>("OUTPUT");
    public GeneratorModel(Integer initialState) {
        super(initialState, identifier);
    }

    @Override
    protected void internalStateTransitionFunction(LongSimTime currentTime) {
        if (modelState == 0) {
            this.modelState = 1;
        } else {
            this.modelState = 0;
        }

    }

    @Override
    protected void externalSateTransitionFunction(LongSimTime currentTime, Bag inputs) {

    }

    @Override
    protected void confluentStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

    }

    @Override
    protected LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
        if (modelState == 1) {
            return currentTime;
        } else {
            return LongSimTime.builder().t(currentTime.getT() + 1).build();
        }
    }

    @Override
    protected Bag outputFunction() {
        return Bag.builder().addPortValueList(generatorOutputPort.createPortValue(modelState)).build();
    }
}
