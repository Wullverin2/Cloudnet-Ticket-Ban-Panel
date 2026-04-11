package de.speed.ticketconsolecloudban.appeal;

import java.util.List;
import java.util.Map;

public record AppealMultipartForm(
  Map<String, String> fields,
  List<UploadFile> files
) {

  public String field(String name) {
    var value = this.fields.get(name);
    return value == null ? "" : value.trim();
  }

  public record UploadFile(
    String fieldName,
    String fileName,
    String contentType,
    byte[] content
  ) {
  }
}
