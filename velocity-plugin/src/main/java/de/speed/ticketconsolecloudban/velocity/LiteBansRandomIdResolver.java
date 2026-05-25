package de.speed.ticketconsolecloudban.velocity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

final class LiteBansRandomIdResolver {

  private static final List<String> RANDOM_ID_CLASS_NAMES = List.of(
    "litebans.api.RandomID",
    "litebans.api.util.RandomID",
    "litebans.api.common.RandomID",
    "litebans.api.database.RandomID");

  private static final List<String> API_CLASS_NAMES = List.of(
    "litebans.api.Database",
    "litebans.api.LiteBans",
    "litebans.api.Api",
    "litebans.api.API");

  private static final List<String> FALLBACK_SCOPE_CANDIDATES = Arrays.asList("*", "", null, "global", "proxy");

  private final Logger logger;

  LiteBansRandomIdResolver(Logger logger) {
    this.logger = logger;
  }

  Optional<String> fromDatabaseId(long databaseId, String playerUuid, String serverScope, String serverOrigin) {
    var direct = this.invokeRandomIdClass(databaseId);
    if (direct.isPresent()) {
      return direct;
    }

    return this.tryApiBasedLookup(databaseId, playerUuid, serverScope, serverOrigin);
  }

  private Optional<String> invokeRandomIdClass(long databaseId) {
    for (var className : RANDOM_ID_CLASS_NAMES) {
      try {
        var clazz = Class.forName(className);
        for (var target : this.resolvePossibleTargets(clazz)) {
          var result = this.tryRandomIdMethodsOnTarget(clazz, target, databaseId);
          if (result.isPresent()) {
            return result;
          }
        }
      } catch (ClassNotFoundException ignored) {
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans RandomID-Klasse {} konnte nicht verwendet werden: {}", className, throwable.toString());
      }
    }
    return Optional.empty();
  }

  private List<Object> resolvePossibleTargets(Class<?> clazz) {
    var targets = new ArrayList<>();
    targets.add(null);

    var instance = instantiate(clazz);
    if (instance != null) {
      targets.add(instance);
    }

    for (var field : clazz.getDeclaredFields()) {
      try {
        if (!Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        field.setAccessible(true);
        var value = field.get(null);
        if (value != null && clazz.isAssignableFrom(value.getClass())) {
          targets.add(value);
        }
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans RandomID-Feld {}#{} konnte nicht gelesen werden: {}",
          clazz.getName(), field.getName(), throwable.toString());
      }
    }

    return targets;
  }

  private Optional<String> tryRandomIdMethodsOnTarget(Class<?> clazz, Object target, long databaseId) {
    for (var method : clazz.getMethods()) {
      if (method.getParameterCount() != 1 || (target == null && !Modifier.isStatic(method.getModifiers()))) {
        continue;
      }

      var parameterType = method.getParameterTypes()[0];
      if (!isNumericOrString(parameterType)) {
        continue;
      }

      try {
        method.setAccessible(true);
        var result = invokeNumericMethod(method, target, databaseId);
        if (result instanceof String value && isResolvedRandomId(value, databaseId)) {
          return Optional.of(value);
        }
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans RandomID-Methode {}#{} konnte nicht verwendet werden: {}",
          clazz.getName(), method.getName(), throwable.toString());
      }
    }
    return Optional.empty();
  }

  private Optional<String> tryApiBasedLookup(long databaseId, String playerUuid, String serverScope, String serverOrigin) {
    for (var apiClassName : API_CLASS_NAMES) {
      try {
        var apiClass = Class.forName(apiClassName);
        var apiObject = this.resolveApiObject(apiClass);
        if (apiObject == null) {
          continue;
        }

        var result = this.tryAllEntryLookups(apiObject, databaseId, playerUuid, serverScope, serverOrigin);
        if (result.isPresent()) {
          return result;
        }
      } catch (ClassNotFoundException ignored) {
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans API-Lookup {} konnte nicht verwendet werden: {}", apiClassName, throwable.toString());
      }
    }
    return Optional.empty();
  }

  private Object resolveApiObject(Class<?> apiClass) {
    for (var getterName : List.of("get", "getInstance", "instance", "getDatabase", "getAPI", "getApi")) {
      try {
        var getter = apiClass.getMethod(getterName);
        if (Modifier.isStatic(getter.getModifiers()) && getter.getParameterCount() == 0) {
          getter.setAccessible(true);
          var result = getter.invoke(null);
          if (result != null) {
            return result;
          }
        }
      } catch (NoSuchMethodException ignored) {
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans API-Getter {}#{} konnte nicht verwendet werden: {}",
          apiClass.getName(), getterName, throwable.toString());
      }
    }

    return instantiate(apiClass);
  }

  private Optional<String> tryAllEntryLookups(Object apiObject,
                                              long databaseId,
                                              String playerUuid,
                                              String serverScope,
                                              String serverOrigin) {
    for (var method : apiObject.getClass().getMethods()) {
      var methodName = method.getName().toLowerCase(Locale.ROOT);
      if ("getban".equals(methodName) && method.getParameterCount() == 2) {
        var result = this.tryGetBanWithDatabaseId(apiObject, method, databaseId, serverScope);
        if (result.isPresent()) {
          return result;
        }
      }

      if ("getban".equals(methodName) && method.getParameterCount() == 3) {
        var result = this.tryGetBanWithUuid(apiObject, method, databaseId, playerUuid, serverScope, serverOrigin);
        if (result.isPresent()) {
          return result;
        }
      }
    }
    return Optional.empty();
  }

  private Optional<String> tryGetBanWithDatabaseId(Object apiObject, Method method, long databaseId, String serverScope) {
    var parameterTypes = method.getParameterTypes();
    if (parameterTypes.length != 2 || !isNumeric(parameterTypes[0]) || !parameterTypes[1].equals(String.class)) {
      return Optional.empty();
    }

    try {
      method.setAccessible(true);
      var firstArg = parameterTypes[0].equals(int.class) || parameterTypes[0].equals(Integer.class)
        ? (int) databaseId
        : databaseId;
      var entry = method.invoke(apiObject, firstArg, serverScope);
      return this.extractRandomIdFromObject(entry, databaseId);
    } catch (Throwable throwable) {
      this.logger.debug("LiteBans getBan(DB-ID, Scope) konnte nicht verwendet werden: {}", throwable.toString());
      return Optional.empty();
    }
  }

  private Optional<String> tryGetBanWithUuid(Object apiObject,
                                             Method method,
                                             long databaseId,
                                             String playerUuid,
                                             String serverScope,
                                             String serverOrigin) {
    var uuid = parseUuid(playerUuid);
    var parameterTypes = method.getParameterTypes();
    if (uuid == null
      || parameterTypes.length != 3
      || !parameterTypes[0].equals(UUID.class)
      || !parameterTypes[1].equals(String.class)
      || !parameterTypes[2].equals(String.class)) {
      return Optional.empty();
    }

    for (var first : this.buildStringCandidates(serverScope, serverOrigin)) {
      for (var second : this.buildStringCandidates(serverOrigin, serverScope)) {
        try {
          method.setAccessible(true);
          var entry = method.invoke(apiObject, uuid, first, second);
          var extracted = this.extractRandomIdFromObject(entry, databaseId);
          if (extracted.isPresent()) {
            return extracted;
          }
        } catch (Throwable throwable) {
          this.logger.debug("LiteBans getBan(UUID, Scope, Origin) konnte nicht verwendet werden: {}", throwable.toString());
        }
      }
    }
    return Optional.empty();
  }

  private List<String> buildStringCandidates(String preferredA, String preferredB) {
    var candidates = new ArrayList<String>();
    addIfMissing(candidates, preferredA);
    addIfMissing(candidates, preferredB);
    for (var fallback : FALLBACK_SCOPE_CANDIDATES) {
      addIfMissing(candidates, fallback);
    }
    return candidates;
  }

  private Optional<String> extractRandomIdFromObject(Object object, long databaseId) {
    if (object == null) {
      return Optional.empty();
    }

    var clazz = object.getClass();
    for (var method : clazz.getMethods()) {
      if (method.getParameterCount() != 0 || !isInteresting(method.getName())) {
        continue;
      }

      try {
        method.setAccessible(true);
        var result = method.invoke(object);
        var extracted = this.extractStringOrNested(result, databaseId);
        if (extracted.isPresent()) {
          return extracted;
        }
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans Entry-Methode {}#{} konnte nicht verwendet werden: {}",
          clazz.getName(), method.getName(), throwable.toString());
      }
    }

    for (var field : clazz.getDeclaredFields()) {
      if (!isInteresting(field.getName())) {
        continue;
      }

      try {
        field.setAccessible(true);
        var result = field.get(object);
        var extracted = this.extractStringOrNested(result, databaseId);
        if (extracted.isPresent()) {
          return extracted;
        }
      } catch (Throwable throwable) {
        this.logger.debug("LiteBans Entry-Feld {}#{} konnte nicht verwendet werden: {}",
          clazz.getName(), field.getName(), throwable.toString());
      }
    }

    return Optional.empty();
  }

  private Optional<String> extractStringOrNested(Object result, long databaseId) {
    if (result instanceof String value && isResolvedRandomId(value, databaseId)) {
      return Optional.of(value);
    }

    if (result != null
      && !(result instanceof Number)
      && !(result instanceof Boolean)
      && !result.getClass().getName().startsWith("java.")) {
      return this.extractRandomIdFromObject(result, databaseId);
    }

    return Optional.empty();
  }

  private static Object instantiate(Class<?> clazz) {
    try {
      Constructor<?> constructor = clazz.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (Throwable throwable) {
      return null;
    }
  }

  private static Object invokeNumericMethod(Method method, Object target, long databaseId) throws Exception {
    var parameterType = method.getParameterTypes()[0];
    if (parameterType.equals(long.class) || parameterType.equals(Long.class)) {
      return method.invoke(target, databaseId);
    }
    if (parameterType.equals(int.class) || parameterType.equals(Integer.class)) {
      return method.invoke(target, (int) databaseId);
    }
    if (parameterType.equals(String.class)) {
      return method.invoke(target, String.valueOf(databaseId));
    }
    return null;
  }

  private static boolean isInteresting(String name) {
    var lowerName = name.toLowerCase(Locale.ROOT);
    return lowerName.contains("random")
      || lowerName.contains("punishment")
      || (lowerName.contains("id") && !"hashcode".equals(lowerName))
      || lowerName.contains("string");
  }

  private static boolean isNumericOrString(Class<?> type) {
    return isNumeric(type) || type.equals(String.class);
  }

  private static boolean isNumeric(Class<?> type) {
    return type.equals(long.class)
      || type.equals(Long.class)
      || type.equals(int.class)
      || type.equals(Integer.class);
  }

  private static boolean isResolvedRandomId(String value, long databaseId) {
    if (value == null || value.isBlank()) {
      return false;
    }
    var trimmed = value.trim();
    return !trimmed.equalsIgnoreCase(String.valueOf(databaseId)) && !trimmed.matches("\\d+");
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    try {
      if (raw.contains("-")) {
        return UUID.fromString(raw);
      }

      if (raw.length() == 32) {
        var hyphenated = raw.substring(0, 8) + "-"
          + raw.substring(8, 12) + "-"
          + raw.substring(12, 16) + "-"
          + raw.substring(16, 20) + "-"
          + raw.substring(20, 32);
        return UUID.fromString(hyphenated);
      }
    } catch (IllegalArgumentException ignored) {
    }

    return null;
  }

  private static void addIfMissing(List<String> values, String value) {
    if (!values.contains(value)) {
      values.add(value);
    }
  }
}
