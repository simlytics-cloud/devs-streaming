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

package devs.utils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

/**
 * Custom type ID resolver that handles adding package prefixes during deserialization
 * and removing them during serialization for DEVS message types.
 */
public class DevsTypeIdResolver extends TypeIdResolverBase {

  private static final String PACKAGE_PREFIX = "devs.iso.";
  private JavaType baseType;

  @Override
  public void init(JavaType baseType) {
    this.baseType = baseType;
  }

  @Override
  public String idFromValue(Object obj) {
    // During serialization, remove the package prefix
    String className = obj.getClass().getName();
    if (className.startsWith(PACKAGE_PREFIX)) {
      return className.substring(PACKAGE_PREFIX.length());
    }
    return className;
  }

  @Override
  public String idFromValueAndType(Object obj, Class<?> clazz) {
    return idFromValue(obj);
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) {
    // During deserialization, add the package prefix if it's missing
    if (!id.contains(".")) {
      id = PACKAGE_PREFIX + id;
    }
    try {
      return context.constructType(Class.forName(id));
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Cannot find class: " + id, e);
    }
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CLASS;
  }
}
