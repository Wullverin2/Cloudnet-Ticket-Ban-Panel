package de.speed.ticketconsolecloudban.shop;

public record ServerShopServerView(
  String id,
  String name,
  String host,
  int port,
  boolean enabled,
  String basePath,
  String baseUrl,
  int connectTimeoutMillis,
  int readTimeoutMillis,
  boolean tokenConfigured
) {
}
