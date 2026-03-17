/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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

package devs.iso;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.immutables.value.Value;

/**
 * Meta-annotation for configuring the style and naming conventions of immutable value classes
 * within the DEVS Streaming Framework.
 * <p>
 * This annotation leverages Immutables framework's {@link Value.Style} to define specific
 * conventions for class and builder generation: - Abstract types are prefixed with "Abstract". -
 * Generated immutable implementations match the abstract type names without the "Abstract" prefix.
 * - Staged builders are enabled for fine-grained control over instance creation.
 * <p>
 * This annotation can be applied at a package or type level to maintain consistency across related
 * classes. It simplifies the creation and management of immutable objects essential for the
 * framework's operation.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Value.Style(typeAbstract = "Abstract*", typeImmutable = "*", stagedBuilder = true)
public @interface DevsStyle {

}
