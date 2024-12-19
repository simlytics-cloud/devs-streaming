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

package devs.msg.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.immutables.value.Value;

/**
 * Annotation used to define the style for generating immutable objects and their related builder
 * patterns. This style configuration is often used in conjunction with the Immutables library to
 * create immutable classes with specific naming conventions and builder configurations.
 * <p>
 * The style specifies: - Abstract type names should start with "Abstract*". - Immutable concrete
 * type names should match the abstract types, with "Abstract" removed. - A staged builder pattern
 * is used for object construction.
 * <p>
 * This annotation can be applied at the package or type level.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Value.Style(typeAbstract = "Abstract*", typeImmutable = "*", stagedBuilder = true)
public @interface MutableStyle {

}
