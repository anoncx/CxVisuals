package me.cxiy.cxvisuals.gui.map;

import java.util.List;

public record MapPlayer(
    int entityId,
    String name,
    String uuid,
    String mode,
    int ping,
    float health,
    float maxHealth,
    float absorption,
    int armor,
    int food,
    int level,
    String held,
    String team,
    double x,
    double y,
    double z,
    List<String> states) {}
