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

package devs.utils;

import com.typesafe.config.Config;
import java.util.Properties;

public class ConfigUtils {

  public static Properties toProperties(Config config) {
    Properties properties = new Properties();
    config.entrySet()
        .forEach(entry -> properties.put(entry.getKey(), entry.getValue().unwrapped()));
    return properties;
  }


  public static Properties copyProperties(Properties original) {
    Properties copy = new Properties();
    copy.putAll(original);
    return copy;
  }

}
