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

package devs;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Captures a full generic type at runtime.
 *
 * <p>Usage example:
 * <pre>
 * new TypeReference&lt;List&lt;MyValue&gt;&gt;() {}
 * </pre>
 *
 * @param <T> captured type
 */
public class TypeReference<T> {

  private final Type type;
  private final Class<T> rawClass;

  protected TypeReference() {
    Type superClass = getClass().getGenericSuperclass();
    if (!(superClass instanceof ParameterizedType parameterizedType)) {
      throw new IllegalArgumentException("TypeReference must capture a parameterized type");
    }

    this.type = parameterizedType.getActualTypeArguments()[0];
    this.rawClass = extractRawClass(type);
  }

  private TypeReference(Type type) {
    this.type = type;
    this.rawClass = extractRawClass(type);
  }

  public static <E> TypeReference<List<E>> listOf(Class<E> elementClass) {
    return new TypeReference<>(new SimpleParameterizedType(List.class, elementClass));
  }

  public static <K, V> TypeReference<Map<K, V>> mapOf(Class<K> keyClass, Class<V> valueClass) {
    return new TypeReference<>(new SimpleParameterizedType(Map.class, keyClass, valueClass));
  }

  public Type getType() {
    return type;
  }

  public Class<T> getRawClass() {
    return rawClass;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> extractRawClass(Type type) {
    if (type instanceof Class<?> clazz) {
      return (Class<T>) clazz;
    }

    if (type instanceof ParameterizedType parameterizedType
        && parameterizedType.getRawType() instanceof Class<?> clazz) {
      return (Class<T>) clazz;
    }

    throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
  }

  private static final class SimpleParameterizedType implements ParameterizedType {

    private final Type rawType;
    private final Type[] typeArguments;

    private SimpleParameterizedType(Type rawType, Type... typeArguments) {
      this.rawType = rawType;
      this.typeArguments = typeArguments.clone();
    }

    @Override
    public Type[] getActualTypeArguments() {
      return typeArguments.clone();
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }
  }
}