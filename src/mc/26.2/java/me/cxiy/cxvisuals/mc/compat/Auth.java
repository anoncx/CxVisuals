package me.cxiy.cxvisuals.mc.compat;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.net.Proxy;

public final class Auth {
  private Auth() {}

  public record Services(UserApiService api, FriendsService friends) {}

  public static Services create(Proxy proxy, String token, boolean online) {
    YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(proxy);
    UserApiService api = online ? auth.createUserApiService(token) : UserApiService.OFFLINE;
    return new Services(api, auth.createFriendsService(token));
  }
}
