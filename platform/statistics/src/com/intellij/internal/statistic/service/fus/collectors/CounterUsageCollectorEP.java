// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * EP to register counter collector in plugin.xml
 * <br/><br/>
 *
 * To use new style API define "implementationClass" field using {@code com.intellij.statistics.counterUsagesCollector} extension point.
 * See fus-collectors.md for more information.<br/>
 * To use old API define "groupId" and "version" and log events with {@link com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger}
 */
public final class CounterUsageCollectorEP {
  @Attribute("groupId")
  public String groupID;

  @Attribute("version")
  public int version = 1;

  @Attribute("implementationClass")
  public String implementationClass;

  @Nullable
  public String getGroupId() {
    return groupID;
  }
}
