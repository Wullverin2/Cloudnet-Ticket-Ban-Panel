package de.speed.ticketconsolecloudban.http;

import de.speed.ticketconsolecloudban.appeal.AppealMultipartForm;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class MultipartFormParser {

  private MultipartFormParser() {
  }

  public static AppealMultipartForm parse(String contentType, byte[] body) {
    var boundary = boundary(contentType);
    if (boundary == null) {
      throw new IllegalArgumentException("Multipart boundary fehlt.");
    }

    var payload = new String(body, StandardCharsets.ISO_8859_1);
    var marker = "--" + boundary;
    var fields = new LinkedHashMap<String, String>();
    var files = new ArrayList<AppealMultipartForm.UploadFile>();

    for (var rawPart : payload.split(java.util.regex.Pattern.quote(marker))) {
      if (rawPart.isBlank() || rawPart.startsWith("--")) {
        continue;
      }

      var part = rawPart;
      if (part.startsWith("\r\n")) {
        part = part.substring(2);
      }
      var separator = part.indexOf("\r\n\r\n");
      if (separator < 0) {
        continue;
      }

      var headerBlock = part.substring(0, separator);
      var content = part.substring(separator + 4);
      if (content.endsWith("\r\n")) {
        content = content.substring(0, content.length() - 2);
      }

      var disposition = header(headerBlock, "content-disposition");
      var name = dispositionParameter(disposition, "name");
      if (name == null || name.isBlank()) {
        continue;
      }

      var fileName = dispositionParameter(disposition, "filename");
      if (fileName == null || fileName.isBlank()) {
        fields.put(name, new String(content.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
        continue;
      }

      var fileBytes = content.getBytes(StandardCharsets.ISO_8859_1);
      if (fileBytes.length == 0) {
        continue;
      }
      files.add(new AppealMultipartForm.UploadFile(
        name,
        fileName,
        header(headerBlock, "content-type"),
        fileBytes));
    }

    return new AppealMultipartForm(fields, List.copyOf(files));
  }

  private static String boundary(String contentType) {
    if (contentType == null) {
      return null;
    }
    for (var part : contentType.split(";")) {
      var trimmed = part.trim();
      if (trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
        return trimmed.substring("boundary=".length()).replace("\"", "");
      }
    }
    return null;
  }

  private static String header(String headers, String name) {
    for (var line : headers.split("\r\n")) {
      var separator = line.indexOf(':');
      if (separator <= 0) {
        continue;
      }
      var headerName = line.substring(0, separator).trim();
      if (headerName.equalsIgnoreCase(name)) {
        return line.substring(separator + 1).trim();
      }
    }
    return "";
  }

  private static String dispositionParameter(String disposition, String name) {
    for (var part : disposition.split(";")) {
      var separator = part.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      var key = part.substring(0, separator).trim();
      if (key.equalsIgnoreCase(name)) {
        return part.substring(separator + 1).trim().replaceAll("^\"|\"$", "");
      }
    }
    return null;
  }
}
