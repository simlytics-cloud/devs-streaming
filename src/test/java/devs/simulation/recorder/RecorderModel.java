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

package devs.simulation.recorder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import example.storage.StorageStateEnum;

public class RecorderModel extends PDEVSModel<LongSimTime, Void> {

    public static Port<StorageStateEnum> storageOutput = new Port<>("STORAGE_OUTPUT");
    public static Port<Integer> generatorOutput = new Port<>("GENERATOR_OUTPUT");

    public RecorderModel(String sender) {
        super(null, sender);
    }

    @Override
    protected void internalStateTransitionFunction(LongSimTime currentTime) {

    }

    @Override
    protected void externalSateTransitionFunction(LongSimTime currentTime, Bag input) {

    }

    @Override
    protected void confluentStateTransitionFunction(LongSimTime currentTime, Bag input) {

    }

    @Override
    protected LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
        return LongSimTime.builder().t(Long.MAX_VALUE).build();
    }

    @Override
    protected Bag outputFunction() {
        return Bag.builder().build();
    }
}
