package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.IOException;
import java.util.Collection;

public interface Assets extends AutoCloseable {
  Collection<String> paths();

  boolean has(String path);

  byte[] read(String path) throws IOException;

  default byte[] icon() {
    return null;
  }

  @Override
  default void close() {}
}
