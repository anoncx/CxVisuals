package me.cxiy.cxvisuals.mc.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.fabricmc.loader.api.FabricLoader;

public final class IrisCompat {
  private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
  private static final String PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";
  private static final String SHADOW_PROGRAM = "net.irisshaders.iris.api.v0.IrisShadowProgram";
  private static final float HAND_SCALE = 0.125F;
  private static final float HAND_OFFSET = 0.4375F;
  private static final float MARGIN = 0.002F;

  private static final Object INSTANCE;
  private static final MethodHandle IN_USE;
  private static final MethodHandle IN_SHADOW;
  private static final MethodHandle SHADOW_STATE;

  private static boolean cleanBroken;
  private static java.lang.reflect.Method cleanManager;
  private static java.lang.reflect.Method cleanPipeline;
  private static java.lang.reflect.Method cleanDepth;
  private static java.lang.reflect.Field cleanTargets;
  private static Class<?> cleanPipelineClass;

  static {
    Object instance = null;
    MethodHandle inUse = null;
    MethodHandle inShadow = null;
    MethodHandle shadowState = null;
    if (FabricLoader.getInstance().isModLoaded("iris")) {
      try {
        Class<?> api = Class.forName(API);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
        inUse =
            lookup
                .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                .asType(MethodType.methodType(boolean.class, Object.class));
        try {
          inShadow =
              lookup
                  .findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                  .asType(MethodType.methodType(boolean.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          inShadow = null;
        }
      } catch (Throwable e) {
        instance = null;
        inUse = null;
        inShadow = null;
        CxVisualsClient.LOG.warn(
            "Iris is present but its API could not be reached, the hand may blur under shader"
                + " packs",
            e);
      }
      if (inShadow == null) {
        try {
          Class<?> state = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState");
          shadowState =
              MethodHandles.publicLookup()
                  .findStatic(
                      state,
                      "areShadowsCurrentlyBeingRendered",
                      MethodType.methodType(boolean.class));
        } catch (Throwable ignored) {
          shadowState = null;
        }
      }
    }
    INSTANCE = instance;
    IN_USE = inUse;
    IN_SHADOW = inShadow;
    SHADOW_STATE = shadowState;
  }

  private IrisCompat() {}

  public static boolean handInScene() {
    if (IN_USE == null) {
      return false;
    }
    try {
      return (boolean) IN_USE.invokeExact(INSTANCE);
    } catch (Throwable e) {
      return false;
    }
  }

  public static Object cleanDepth() {
    if (cleanBroken || INSTANCE == null || !handInScene()) {
      return null;
    }
    try {
      if (cleanTargets == null) {
        ClassLoader loader = INSTANCE.getClass().getClassLoader();
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris", true, loader);
        Class<?> manager = Class.forName("net.irisshaders.iris.pipeline.PipelineManager", true, loader);
        cleanPipelineClass =
            Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", true, loader);
        Class<?> targets = Class.forName("net.irisshaders.iris.targets.RenderTargets", true, loader);
        cleanManager = iris.getMethod("getPipelineManager");
        cleanPipeline = manager.getMethod("getPipelineNullable");
        cleanTargets = cleanPipelineClass.getDeclaredField("renderTargets");
        cleanTargets.setAccessible(true);
        cleanDepth = targets.getMethod("getDepthTextureNoTranslucents");
      }
      Object pipeline = cleanPipeline.invoke(cleanManager.invoke(null));
      if (!cleanPipelineClass.isInstance(pipeline)) {
        return null;
      }
      Object targets = cleanTargets.get(pipeline);
      return targets == null ? null : cleanDepth.invoke(targets);
    } catch (ReflectiveOperationException | LinkageError e) {
      cleanBroken = true;
      CxVisualsClient.LOG.warn(
          "Iris depth without translucents could not be reached, effects hide behind glass and"
              + " water under shader packs",
          e);
      return null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static boolean isRenderingShadowPass() {
    if (IN_SHADOW != null) {
      try {
        return (boolean) IN_SHADOW.invokeExact(INSTANCE);
      } catch (Throwable e) {
        return false;
      }
    }
    if (SHADOW_STATE != null) {
      try {
        return (boolean) SHADOW_STATE.invokeExact();
      } catch (Throwable e) {
        return false;
      }
    }
    return false;
  }

  public static void entityPipeline(RenderPipeline pipeline, boolean translucent) {
    if (INSTANCE == null) {
      return;
    }
    try {
      ClassLoader loader = INSTANCE.getClass().getClassLoader();
      Class<?> api = Class.forName(API, true, loader);
      Class<?> program = Class.forName(PROGRAM, true, loader);
      Class<?> shadow = Class.forName(SHADOW_PROGRAM, true, loader);
      Object programValue = enumValue(program, translucent ? "ENTITIES_TRANSLUCENT" : "ENTITIES");
      Object shadowValue = enumValue(shadow, "SHADOW_ENTITIES");
      api.getMethod("assignPipeline", RenderPipeline.class, program)
          .invoke(INSTANCE, pipeline, programValue);
      api.getMethod("assignPipelineShadow", RenderPipeline.class, shadow)
          .invoke(INSTANCE, pipeline, shadowValue);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      CxVisualsClient.LOG.warn(
          "Iris could not adopt the avatar pipeline, avatars may look flat under shader packs", e);
    }
  }

  private static Object enumValue(Class<?> type, String name) {
    for (Object constant : type.getEnumConstants()) {
      if (((Enum<?>) constant).name().equals(name)) {
        return constant;
      }
    }
    throw new IllegalArgumentException(name);
  }

  public static float handDepthLow(boolean zeroToOne) {
    float low = zeroToOne ? HAND_OFFSET : HAND_OFFSET - HAND_SCALE;
    return (zeroToOne ? low : low * 0.5F + 0.5F) - MARGIN;
  }

  public static float handDepthHigh(boolean zeroToOne) {
    float high = HAND_OFFSET + HAND_SCALE;
    return (zeroToOne ? high : high * 0.5F + 0.5F) + MARGIN;
  }
}
