package me.cxiy.cxvisuals.modules.avatar.format;

import java.io.IOException;

public interface SideFiles {
  byte[] read(String relative) throws IOException;
}
