package com.brunnen.vp.usecase;

import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;

/**
 * Use Case Plugin for Visual Paradigm. Provides functionality for creating and managing use cases.
 */
public final class VPUseCasePlugin implements VPPlugin {

  @Override
  public void loaded(final VPPluginInfo info) {
    // Plugin loaded - initialization code here
    String pluginId = (info != null) ? info.getPluginId() : "unknown";
    System.out.println("Use Case Plugin loaded: " + pluginId);
  }

  @Override
  public void unloaded() {
    // Plugin unloaded - cleanup code here
    System.out.println("Use Case Plugin unloaded");
  }
}
