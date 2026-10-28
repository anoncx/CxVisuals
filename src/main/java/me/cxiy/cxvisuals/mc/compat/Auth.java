package me.cxiy.cxvisuals.mc.compat;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import java.net.Proxy;

public final class Auth {
  private Auth() {}

  public record Services(UserApiService api, FriendsService friends) {}

  public static Services create(Proxy proxy, String token, boolean online) {
    MinecraftServicesDiscoveryService discovery =
        MinecraftServicesDiscoveryService.create(proxy, online);
    UserApiService api = online ? discovery.createUserApiService(token) : UserApiService.OFFLINE;
    return new Services(api, discovery.createFriendsService(token));
  }
}
