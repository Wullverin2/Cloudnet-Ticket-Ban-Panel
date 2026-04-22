package de.speed.ticketconsolecloudban.quest;

public record QuestEditorServerView(
  String id,
  String name,
  String host,
  int port,
  boolean enabled,
  String basePath,
  String baseUrl,
  int connectTimeoutMillis,
  int readTimeoutMillis,
  boolean tokenConfigured,
  String pluginServerName
) {
}
