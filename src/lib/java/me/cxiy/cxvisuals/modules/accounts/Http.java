package me.cxiy.cxvisuals.modules.accounts;

import java.io.IOException;
import java.util.Map;

public interface Http {
  record Reply(int code, String body) {
    public boolean ok() {
      return code >= 200 && code < 300;
    }
  }

  Reply postForm(String url, Map<String, String> form) throws IOException;

  Reply postJson(String url, String body, String bearer) throws IOException;

  Reply get(String url, String bearer) throws IOException;

  byte[] bytes(String url, long limit) throws IOException;
}
