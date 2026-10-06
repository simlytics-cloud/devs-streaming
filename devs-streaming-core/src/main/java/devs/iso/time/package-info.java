/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
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

/**
 * Simulation time representations and JSON bindings for timed DEVS messages.
 *
 * <p>Use {@link devs.iso.time.LongSimTime} for integer ticks and
 * {@link devs.iso.time.DoubleSimTime} for binary floating-point native time. Choose
 * {@link devs.iso.time.DecimalSimTime} when finite decimal timestamps and intervals must remain
 * exact, or {@link devs.iso.time.QuantizedSimTime} for rational canonical scheduling across models
 * with different units and time quanta. Time mappings, not the value classes, enforce unit
 * conversion and lattice constraints.
 *
 * <p>{@link devs.iso.time.ExactTimeJsonModule} provides standalone exact-time JSON binding.
 * Message transport separately requires an appropriate wire profile and endpoint policy.
 */
package devs.iso.time;
