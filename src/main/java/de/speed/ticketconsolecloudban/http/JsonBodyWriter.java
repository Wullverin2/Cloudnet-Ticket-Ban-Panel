package de.speed.ticketconsolecloudban.http;

import eu.cloudnetservice.driver.document.Document;
import eu.cloudnetservice.driver.document.StandardSerialisationStyle;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.Iterator;
import java.util.Map;

final class JsonBodyWriter {

  private JsonBodyWriter() {
  }

  static String toJson(Object value) {
    var builder = new StringBuilder();
    appendValue(builder, value);
    return builder.toString();
  }

  private static void appendValue(StringBuilder builder, Object value) {
    if (value == null) {
      builder.append("null");
      return;
    }

    if (value instanceof Document document) {
      builder.append(document.serializeToString(StandardSerialisationStyle.COMPACT));
      return;
    }

    if (value instanceof String text) {
      appendString(builder, text);
      return;
    }

    if (value instanceof Number || value instanceof Boolean) {
      builder.append(value);
      return;
    }

    if (value instanceof Enum<?> enumValue) {
      appendString(builder, enumValue.name());
      return;
    }

    if (value instanceof TemporalAccessor temporalAccessor) {
      appendString(builder, temporalAccessor.toString());
      return;
    }

    if (value instanceof Map<?, ?> mapValue) {
      appendMap(builder, mapValue);
      return;
    }

    if (value instanceof Iterable<?> iterable) {
      appendIterable(builder, iterable.iterator());
      return;
    }

    if (value.getClass().isArray()) {
      appendArray(builder, value);
      return;
    }

    if (value.getClass().isRecord()) {
      appendRecord(builder, value);
      return;
    }

    appendString(builder, value.toString());
  }

  private static void appendRecord(StringBuilder builder, Object value) {
    builder.append('{');
    var first = true;
    for (RecordComponent component : value.getClass().getRecordComponents()) {
      try {
        var componentValue = component.getAccessor().invoke(value);
        if (!first) {
          builder.append(',');
        }
        appendString(builder, component.getName());
        builder.append(':');
        appendValue(builder, componentValue);
        first = false;
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Record konnte nicht als JSON serialisiert werden.", exception);
      }
    }
    builder.append('}');
  }

  private static void appendMap(StringBuilder builder, Map<?, ?> value) {
    builder.append('{');
    var iterator = value.entrySet().iterator();
    var first = true;
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (!first) {
        builder.append(',');
      }
      appendString(builder, String.valueOf(entry.getKey()));
      builder.append(':');
      appendValue(builder, entry.getValue());
      first = false;
    }
    builder.append('}');
  }

  private static void appendIterable(StringBuilder builder, Iterator<?> iterator) {
    builder.append('[');
    var first = true;
    while (iterator.hasNext()) {
      if (!first) {
        builder.append(',');
      }
      appendValue(builder, iterator.next());
      first = false;
    }
    builder.append(']');
  }

  private static void appendArray(StringBuilder builder, Object array) {
    builder.append('[');
    var length = Array.getLength(array);
    for (int index = 0; index < length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      appendValue(builder, Array.get(array, index));
    }
    builder.append(']');
  }

  private static void appendString(StringBuilder builder, String value) {
    builder.append('"');
    for (int index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      switch (character) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (character < 0x20) {
            builder.append(String.format("\\u%04x", (int) character));
          } else {
            builder.append(character);
          }
        }
      }
    }
    builder.append('"');
  }
}
